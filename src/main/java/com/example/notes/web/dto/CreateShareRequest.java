package com.example.notes.web.dto;

import com.example.notes.domain.Permission;
import jakarta.validation.constraints.NotNull;

/**
 * Share a note with exactly one target: either a user (by username) or a team
 * (by name). Supplying both, or neither, is a 400.
 */
public record CreateShareRequest(

        String username,

        String teamName,

        @NotNull(message = "permission is required (READ or WRITE)")
        Permission permission
) {
}
