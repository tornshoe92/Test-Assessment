package com.example.notes;

import com.example.notes.domain.Note;
import com.example.notes.domain.NoteShare;
import com.example.notes.domain.Permission;
import com.example.notes.domain.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NoteApiTest extends TestFixtures {

    @Autowired
    private ObjectMapper json;

    @BeforeEach
    void setUp() {
        resetData();
    }

    // --- authentication ----------------------------------------------------

    @Test
    @DisplayName("anonymous requests are rejected with a problem+json 401")
    void anonymousIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/notes"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    @DisplayName("wrong password is rejected")
    void badCredentialsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/notes")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.httpBasic("alice", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    // --- create / read -----------------------------------------------------

    @Test
    @DisplayName("creating a note returns 201 with a Location header and OWNER access")
    void createNote() throws Exception {
        mvc.perform(post("/api/v1/notes")
                        .with(as(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Release runbook","content":"Freeze main, cut branch","tags":["Ops","ops","release"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Release runbook"))
                .andExpect(jsonPath("$.owner.username").value("alice"))
                .andExpect(jsonPath("$.access").value("OWNER"))
                .andExpect(jsonPath("$.version").value(0))
                // tags are lower-cased and de-duplicated
                .andExpect(jsonPath("$.tags.length()").value(2));
    }

    @Test
    @DisplayName("a blank title is a 400 with per-field errors")
    void blankTitleIsRejected() throws Exception {
        mvc.perform(post("/api/v1/notes")
                        .with(as(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"   ","content":"body"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.title").exists());
    }

    @Test
    @DisplayName("malformed JSON is a 400, not a 500")
    void malformedJsonIsRejected() throws Exception {
        mvc.perform(post("/api/v1/notes")
                        .with(as(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":"))
                .andExpect(status().isBadRequest());
    }

    // --- access control ----------------------------------------------------

    @Test
    @DisplayName("a note that is not shared is invisible to other users (404, not 403)")
    void unsharedNoteIsHiddenFromOthers() throws Exception {
        Note note = givenNote(alice, "Private thoughts", "nobody else sees this");

        mvc.perform(get("/api/v1/notes/{id}", note.getId()).with(as(bob)))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/notes").with(as(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("a READ share lets the recipient read but not write")
    void readShareIsReadOnly() throws Exception {
        Note note = givenNote(alice, "Sprint retro", "went well");
        shares.save(NoteShare.toUser(note, bob, Permission.READ));

        mvc.perform(get("/api/v1/notes/{id}", note.getId()).with(as(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value("READ"));

        mvc.perform(put("/api/v1/notes/{id}", note.getId())
                        .with(as(bob))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Hijacked","content":"nope"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    @DisplayName("a WRITE share lets the recipient edit the note")
    void writeShareAllowsEditing() throws Exception {
        Note note = givenNote(alice, "Sprint retro", "went well");
        shares.save(NoteShare.toUser(note, bob, Permission.WRITE));

        mvc.perform(put("/api/v1/notes/{id}", note.getId())
                        .with(as(bob))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Sprint retro (edited)","content":"went well, fix CI","tags":["retro"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Sprint retro (edited)"))
                .andExpect(jsonPath("$.access").value("WRITE"))
                .andExpect(jsonPath("$.owner.username").value("alice"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("a team share reaches every team member and nobody else")
    void teamShareReachesMembers() throws Exception {
        Note note = givenNote(alice, "Release runbook", "steps");
        shares.save(NoteShare.toTeam(note, platform, Permission.WRITE));

        // bob is in platform
        mvc.perform(get("/api/v1/notes/{id}", note.getId()).with(as(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value("WRITE"));

        // carol is not
        mvc.perform(get("/api/v1/notes/{id}", note.getId()).with(as(carol)))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/notes").with(as(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].access").value("WRITE"));
    }

    @Test
    @DisplayName("the strongest applicable share wins when several apply")
    void strongestShareWins() throws Exception {
        Note note = givenNote(alice, "Runbook", "steps");
        shares.save(NoteShare.toTeam(note, platform, Permission.READ));
        shares.save(NoteShare.toUser(note, bob, Permission.WRITE));

        mvc.perform(get("/api/v1/notes/{id}", note.getId()).with(as(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value("WRITE"));
    }

    @Test
    @DisplayName("only the owner can delete, even with WRITE access")
    void onlyOwnerCanDelete() throws Exception {
        Note note = givenNote(alice, "Runbook", "steps");
        shares.save(NoteShare.toUser(note, bob, Permission.WRITE));

        mvc.perform(delete("/api/v1/notes/{id}", note.getId()).with(as(bob)))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/v1/notes/{id}", note.getId()).with(as(alice)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/notes/{id}", note.getId()).with(as(alice)))
                .andExpect(status().isNotFound());
    }

    // --- sharing endpoints -------------------------------------------------

    @Test
    @DisplayName("sharing requires exactly one target")
    void shareRequiresExactlyOneTarget() throws Exception {
        Note note = givenNote(alice, "Runbook", "steps");

        mvc.perform(post("/api/v1/notes/{id}/shares", note.getId())
                        .with(as(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"bob","teamName":"platform","permission":"READ"}
                                """))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/notes/{id}/shares", note.getId())
                        .with(as(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"permission":"READ"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("re-sharing with the same target upgrades the existing grant")
    void resharingIsIdempotent() throws Exception {
        Note note = givenNote(alice, "Runbook", "steps");

        mvc.perform(post("/api/v1/notes/{id}/shares", note.getId())
                        .with(as(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"bob","permission":"READ"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permission").value("READ"))
                .andExpect(jsonPath("$.targetType").value("USER"));

        mvc.perform(post("/api/v1/notes/{id}/shares", note.getId())
                        .with(as(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"bob","permission":"WRITE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permission").value("WRITE"));

        mvc.perform(get("/api/v1/notes/{id}/shares", note.getId()).with(as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("a non-owner cannot share, and revoking removes access")
    void shareAndRevoke() throws Exception {
        Note note = givenNote(alice, "Runbook", "steps");
        NoteShare share = shares.save(NoteShare.toUser(note, bob, Permission.WRITE));

        mvc.perform(post("/api/v1/notes/{id}/shares", note.getId())
                        .with(as(bob))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"carol","permission":"READ"}
                                """))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/v1/notes/{noteId}/shares/{shareId}", note.getId(), share.getId())
                        .with(as(alice)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/notes/{id}", note.getId()).with(as(bob)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("sharing with an unknown user is a 404")
    void shareWithUnknownUser() throws Exception {
        Note note = givenNote(alice, "Runbook", "steps");

        mvc.perform(post("/api/v1/notes/{id}/shares", note.getId())
                        .with(as(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"nobody","permission":"READ"}
                                """))
                .andExpect(status().isNotFound());
    }

    // --- concurrency -------------------------------------------------------

    @Test
    @DisplayName("updating with a stale version is a 409")
    void staleVersionIsRejected() throws Exception {
        Note note = givenNote(alice, "Runbook", "v1");

        mvc.perform(put("/api/v1/notes/{id}", note.getId())
                        .with(as(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Runbook","content":"v2","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(put("/api/v1/notes/{id}", note.getId())
                        .with(as(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Runbook","content":"v3 from a stale client","version":0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    // --- listing -----------------------------------------------------------

    @Test
    @DisplayName("list supports scope, full-text search, tag filter and paging")
    void listFiltering() throws Exception {
        Note ownedA = givenNote(alice, "Release runbook", "deploy steps for the platform", Set.of("ops"));
        givenNote(alice, "Grocery list", "milk and oats", Set.of("personal"));
        Note bobsNote = givenNote(bob, "Bob design notes", "spacing and type scale", Set.of("design"));
        shares.save(NoteShare.toUser(bobsNote, alice, Permission.READ));

        mvc.perform(get("/api/v1/notes").with(as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.last").value(true));

        mvc.perform(get("/api/v1/notes").param("scope", "OWNED").with(as(alice)))
                .andExpect(jsonPath("$.totalElements").value(2));

        mvc.perform(get("/api/v1/notes").param("scope", "SHARED").with(as(alice)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].owner.username").value("bob"));

        // search covers content as well as title, case-insensitively
        mvc.perform(get("/api/v1/notes").param("q", "DEPLOY").with(as(alice)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(ownedA.getId()));

        mvc.perform(get("/api/v1/notes").param("tag", "ops").with(as(alice)))
                .andExpect(jsonPath("$.totalElements").value(1));

        MvcResult paged = mvc.perform(get("/api/v1/notes")
                        .param("size", "2")
                        .param("page", "0")
                        .with(as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(false))
                .andReturn();

        JsonNode body = json.readTree(paged.getResponse().getContentAsString());
        assertThat(body.get("content").get(0).get("excerpt").asText()).isNotEmpty();
    }

    // --- framework-level errors keep their status codes --------------------

    @Test
    @DisplayName("an unknown path is a 404, not a 500")
    void unknownPathIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/nonexistent").with(as(alice)))
                .andExpect(status().isNotFound());

        // empty path variable: /api/v1/notes/ must not fall through to the catch-all
        mvc.perform(get("/api/v1/notes/").with(as(alice)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a non-numeric id is a 400 and an unsupported method is a 405")
    void badRequestsKeepTheirStatus() throws Exception {
        mvc.perform(get("/api/v1/notes/not-a-number").with(as(alice)))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/v1/users/me").with(as(alice)))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("a missing note is a 404")
    void missingNoteIsNotFound() throws Exception {
        mvc.perform(get("/api/v1/notes/{id}", 999_999).with(as(alice)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not found"));
    }

    @Test
    @DisplayName("the caller identity endpoint reports the authenticated user")
    void whoAmI() throws Exception {
        mvc.perform(get("/api/v1/users/me").with(as(carol)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("carol"))
                .andExpect(jsonPath("$.displayName").value("Carol Santos"));
    }

    // --- helpers -----------------------------------------------------------

    private Note givenNote(User owner, String title, String content) {
        return givenNote(owner, title, content, Set.of());
    }

    private Note givenNote(User owner, String title, String content, Set<String> tags) {
        Note note = new Note(title, content, owner);
        note.replaceTags(tags);
        return notes.save(note);
    }
}
