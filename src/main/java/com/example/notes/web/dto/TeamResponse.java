package com.example.notes.web.dto;

import com.example.notes.domain.TeamRole;

import java.time.Instant;

public record TeamResponse(
        Long id,
        String name,
        TeamRole myRole,
        long memberCount,
        Instant createdAt
) {
}
