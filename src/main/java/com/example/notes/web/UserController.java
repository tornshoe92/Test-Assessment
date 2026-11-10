package com.example.notes.web;

import com.example.notes.exception.NotFoundException;
import com.example.notes.repository.UserRepository;
import com.example.notes.security.AppUserDetails;
import com.example.notes.web.dto.UserSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
public class UserController {

    private final UserRepository users;

    public UserController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    @Operation(summary = "Identity of the authenticated caller")
    public UserSummary me(@AuthenticationPrincipal AppUserDetails principal) {
        return users.findById(principal.getId())
                .map(UserSummary::from)
                .orElseThrow(() -> new NotFoundException("User " + principal.getId() + " not found"));
    }
}
