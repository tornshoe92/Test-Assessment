package com.example.notes.config;

import com.example.notes.domain.Note;
import com.example.notes.domain.NoteShare;
import com.example.notes.domain.Permission;
import com.example.notes.domain.Team;
import com.example.notes.domain.TeamMembership;
import com.example.notes.domain.TeamRole;
import com.example.notes.domain.User;
import com.example.notes.repository.NoteRepository;
import com.example.notes.repository.NoteShareRepository;
import com.example.notes.repository.TeamMembershipRepository;
import com.example.notes.repository.TeamRepository;
import com.example.notes.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Seeds a small, recognisable data set so the API can be exercised immediately.
 * Controlled by {@code app.seed.enabled}; tests run with it off.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String DEMO_PASSWORD = "password";

    private final UserRepository users;
    private final TeamRepository teams;
    private final TeamMembershipRepository memberships;
    private final NoteRepository notes;
    private final NoteShareRepository shares;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(UserRepository users,
                          TeamRepository teams,
                          TeamMembershipRepository memberships,
                          NoteRepository notes,
                          NoteShareRepository shares,
                          PasswordEncoder passwordEncoder) {
        this.users = users;
        this.teams = teams;
        this.memberships = memberships;
        this.notes = notes;
        this.shares = shares;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            log.info("Demo data already present; skipping seed");
            return;
        }

        String hash = passwordEncoder.encode(DEMO_PASSWORD);
        User alice = users.save(new User("alice", "Alice Nguyen", "alice@example.com", hash));
        User bob = users.save(new User("bob", "Bob Okafor", "bob@example.com", hash));
        User carol = users.save(new User("carol", "Carol Santos", "carol@example.com", hash));
        User dave = users.save(new User("dave", "Dave Lindqvist", "dave@example.com", hash));

        Team platform = teams.save(new Team("platform"));
        Team design = teams.save(new Team("design"));

        memberships.save(new TeamMembership(platform, alice, TeamRole.ADMIN));
        memberships.save(new TeamMembership(platform, bob, TeamRole.MEMBER));
        memberships.save(new TeamMembership(design, carol, TeamRole.ADMIN));
        memberships.save(new TeamMembership(design, dave, TeamRole.MEMBER));
        // Alice spans both teams, which makes cross-team sharing visible in the demo data.
        memberships.save(new TeamMembership(design, alice, TeamRole.MEMBER));

        Note runbook = newNote("Release runbook",
                "1. Freeze main. 2. Cut the release branch. 3. Run smoke tests. 4. Tag and deploy.",
                alice, Set.of("ops", "release"));
        Note retro = newNote("Sprint 14 retro",
                "Went well: fewer rollbacks. To improve: flaky integration tests on CI.",
                alice, Set.of("retro"));
        Note scratch = newNote("Scratch pad",
                "Private thinking, not shared with anyone yet.",
                bob, Set.of());
        Note brand = newNote("Brand voice notes",
                "Short sentences. Concrete nouns. No exclamation marks.",
                carol, Set.of("brand", "writing"));

        shares.save(NoteShare.toTeam(runbook, platform, Permission.WRITE));
        shares.save(NoteShare.toUser(retro, bob, Permission.READ));
        shares.save(NoteShare.toTeam(brand, design, Permission.READ));
        shares.save(NoteShare.toUser(brand, alice, Permission.WRITE));

        log.info("Seeded {} users, {} teams and {} notes (password for every demo user: {})",
                users.count(), teams.count(), notes.count(), DEMO_PASSWORD);
        log.debug("Seeded note ids: {}, {}, {}, {}",
                runbook.getId(), retro.getId(), scratch.getId(), brand.getId());
    }

    private Note newNote(String title, String content, User owner, Set<String> tags) {
        Note note = new Note(title, content, owner);
        note.replaceTags(tags);
        return notes.save(note);
    }
}
