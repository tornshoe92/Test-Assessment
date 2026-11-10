package com.example.notes.web.dto;

import com.example.notes.domain.TeamRole;
import jakarta.validation.constraints.NotBlank;

public record AddMemberRequest(

        @NotBlank(message = "username must not be blank")
        String username,

        /** Defaults to MEMBER when omitted. */
        TeamRole role
) {
}
