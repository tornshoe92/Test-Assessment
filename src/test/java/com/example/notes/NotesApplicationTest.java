package com.example.notes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "app.seed.enabled=false")
class NotesApplicationTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    @DisplayName("the application context loads")
    void contextLoads() {
        assertThat(context.getBeanNamesForType(com.example.notes.web.NoteController.class)).hasSize(1);
    }
}
