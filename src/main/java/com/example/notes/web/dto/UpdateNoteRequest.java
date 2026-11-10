package com.example.notes.web.dto;

import com.example.notes.domain.Note;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Full replacement of a note. {@code version} is optional: when supplied it must
 * match the stored version or the update is rejected with 409.
 */
public record UpdateNoteRequest(

        @NotBlank(message = "title must not be blank")
        @Size(max = Note.MAX_TITLE_LENGTH, message = "title must be at most 200 characters")
        String title,

        @Size(max = Note.MAX_CONTENT_LENGTH, message = "content must be at most 20000 characters")
        String content,

        @Size(max = 20, message = "a note may have at most 20 tags")
        Set<@Size(max = 64, message = "a tag must be at most 64 characters") String> tags,

        Long version
) {
}
