package com.example.notes.web.dto;

import com.example.notes.domain.User;

public record UserSummary(Long id, String username, String displayName) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getUsername(), user.getDisplayName());
    }
}
