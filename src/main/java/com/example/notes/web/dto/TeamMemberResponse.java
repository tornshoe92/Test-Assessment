package com.example.notes.web.dto;

import com.example.notes.domain.TeamMembership;
import com.example.notes.domain.TeamRole;

import java.time.Instant;

public record TeamMemberResponse(
        Long userId,
        String username,
        String displayName,
        TeamRole role,
        Instant joinedAt
) {

    public static TeamMemberResponse from(TeamMembership membership) {
        return new TeamMemberResponse(
                membership.getUser().getId(),
                membership.getUser().getUsername(),
                membership.getUser().getDisplayName(),
                membership.getRole(),
                membership.getJoinedAt());
    }
}
