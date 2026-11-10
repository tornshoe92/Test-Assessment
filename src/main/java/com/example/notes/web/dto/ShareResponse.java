package com.example.notes.web.dto;

import com.example.notes.domain.NoteShare;
import com.example.notes.domain.Permission;

import java.time.Instant;

public record ShareResponse(
        Long id,
        ShareTargetType targetType,
        Long targetId,
        String targetName,
        Permission permission,
        Instant createdAt
) {

    public enum ShareTargetType {
        USER,
        TEAM
    }

    public static ShareResponse from(NoteShare share) {
        if (share.isTeamShare()) {
            return new ShareResponse(
                    share.getId(),
                    ShareTargetType.TEAM,
                    share.getSharedWithTeam().getId(),
                    share.getSharedWithTeam().getName(),
                    share.getPermission(),
                    share.getCreatedAt());
        }
        return new ShareResponse(
                share.getId(),
                ShareTargetType.USER,
                share.getSharedWithUser().getId(),
                share.getSharedWithUser().getUsername(),
                share.getPermission(),
                share.getCreatedAt());
    }
}
