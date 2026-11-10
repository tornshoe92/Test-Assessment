package com.example.notes.web.dto;

import com.example.notes.domain.AccessLevel;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record NoteResponse(
        Long id,
        String title,
        String content,
        Set<String> tags,
        UserSummary owner,
        AccessLevel access,
        List<ShareResponse> shares,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
}
