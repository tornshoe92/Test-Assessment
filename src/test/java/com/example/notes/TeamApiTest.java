package com.example.notes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeamApiTest extends TestFixtures {

    @BeforeEach
    void setUp() {
        resetData();
    }

    @Test
    @DisplayName("listing teams returns only the caller's own teams, with their role")
    void listMyTeams() throws Exception {
        mvc.perform(get("/api/v1/teams").with(as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("platform"))
                .andExpect(jsonPath("$[0].myRole").value("ADMIN"))
                .andExpect(jsonPath("$[0].memberCount").value(2));

        mvc.perform(get("/api/v1/teams").with(as(carol)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("creating a team makes the creator its admin; duplicate names are a 409")
    void createTeam() throws Exception {
        mvc.perform(post("/api/v1/teams")
                        .with(as(carol))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"design"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("design"))
                .andExpect(jsonPath("$.myRole").value("ADMIN"))
                .andExpect(jsonPath("$.memberCount").value(1));

        mvc.perform(post("/api/v1/teams")
                        .with(as(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"design"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a team is invisible to non-members")
    void nonMembersCannotSeeTeam() throws Exception {
        mvc.perform(get("/api/v1/teams/{id}/members", platform.getId()).with(as(carol)))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/teams/{id}/members", platform.getId()).with(as(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("only an admin can change membership")
    void onlyAdminsManageMembership() throws Exception {
        // bob is a plain member of platform
        mvc.perform(post("/api/v1/teams/{id}/members", platform.getId())
                        .with(as(bob))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"carol"}
                                """))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/teams/{id}/members", platform.getId())
                        .with(as(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"carol"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("carol"))
                .andExpect(jsonPath("$.role").value("MEMBER"));

        // adding the same person twice is a conflict
        mvc.perform(post("/api/v1/teams/{id}/members", platform.getId())
                        .with(as(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"carol"}
                                """))
                .andExpect(status().isConflict());

        mvc.perform(delete("/api/v1/teams/{id}/members/{userId}", platform.getId(), carol.getId())
                        .with(as(alice)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("the last admin cannot be removed")
    void lastAdminIsProtected() throws Exception {
        mvc.perform(delete("/api/v1/teams/{id}/members/{userId}", platform.getId(), alice.getId())
                        .with(as(alice)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A team must keep at least one admin"));
    }

    @Test
    @DisplayName("a team share makes a note visible to a member added later")
    void newMemberGainsAccessToTeamNotes() throws Exception {
        mvc.perform(post("/api/v1/notes")
                        .with(as(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Team runbook","content":"shared with the whole team"}
                                """))
                .andExpect(status().isCreated());

        Long noteId = notes.findAll().get(0).getId();

        mvc.perform(post("/api/v1/notes/{id}/shares", noteId)
                        .with(as(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"teamName":"platform","permission":"READ"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetType").value("TEAM"));

        mvc.perform(get("/api/v1/notes/{id}", noteId).with(as(carol)))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/teams/{id}/members", platform.getId())
                        .with(as(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"carol","role":"MEMBER"}
                                """))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/notes/{id}", noteId).with(as(carol)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access").value("READ"));
    }
}
