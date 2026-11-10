package com.example.notes.web.dto;

import com.example.notes.domain.AccessLevel;

import java.time.Instant;
import java.util.Set;

/** List projection: content is trimmed to a short excerpt. */
public record NoteSummaryResponse(
        Long id,
        String title,
        String excerpt,
        Set<String> tags,
        UserSummary owner,
        AccessLevel access,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
}
