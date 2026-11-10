package com.example.notes.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTeamRequest(

        @NotBlank(message = "name must not be blank")
        @Size(max = 128, message = "name must be at most 128 characters")
        String name
) {
}
