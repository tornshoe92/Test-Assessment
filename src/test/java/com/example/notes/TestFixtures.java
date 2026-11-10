package com.example.notes;

import com.example.notes.domain.Team;
import com.example.notes.domain.TeamMembership;
import com.example.notes.domain.TeamRole;
import com.example.notes.domain.User;
import com.example.notes.repository.NoteRepository;
import com.example.notes.repository.NoteShareRepository;
import com.example.notes.repository.TeamMembershipRepository;
import com.example.notes.repository.TeamRepository;
import com.example.notes.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

/**
 * Shared base for the API tests: boots the real application context with the
 * demo seeder disabled, and rebuilds a small known data set before each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.seed.enabled=false")
abstract class TestFixtures {

    protected static final String PASSWORD = "s3cret-test";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected UserRepository users;

    @Autowired
    protected TeamRepository teams;

    @Autowired
    protected TeamMembershipRepository memberships;

    @Autowired
    protected NoteRepository notes;

    @Autowired
    protected NoteShareRepository shares;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected User alice;
    protected User bob;
    protected User carol;
    protected Team platform;

    /**
     * Each test starts from an empty schema. Deleting in dependency order keeps
     * this honest about the real foreign keys rather than relying on cascades.
     */
    protected void resetData() {
        shares.deleteAll();
        notes.deleteAll();
        memberships.deleteAll();
        teams.deleteAll();
        users.deleteAll();

        String hash = passwordEncoder.encode(PASSWORD);
        alice = users.save(new User("alice", "Alice Nguyen", "alice@example.com", hash));
        bob = users.save(new User("bob", "Bob Okafor", "bob@example.com", hash));
        carol = users.save(new User("carol", "Carol Santos", "carol@example.com", hash));

        platform = teams.save(new Team("platform"));
        memberships.save(new TeamMembership(platform, alice, TeamRole.ADMIN));
        memberships.save(new TeamMembership(platform, bob, TeamRole.MEMBER));
    }

    protected RequestPostProcessor as(User user) {
        return httpBasic(user.getUsername(), PASSWORD);
    }
}
