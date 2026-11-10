package com.example.notes.web;

import com.example.notes.security.AppUserDetails;
import com.example.notes.service.TeamService;
import com.example.notes.web.dto.AddMemberRequest;
import com.example.notes.web.dto.CreateTeamRequest;
import com.example.notes.web.dto.TeamMemberResponse;
import com.example.notes.web.dto.TeamResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/teams")
@Tag(name = "Teams", description = "Teams are the unit notes are shared with")
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    @Operation(summary = "List the teams the caller belongs to")
    public List<TeamResponse> listMyTeams(@AuthenticationPrincipal AppUserDetails principal) {
        return teamService.listMyTeams(principal.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a team; the caller becomes its first admin")
    public TeamResponse create(
            @AuthenticationPrincipal AppUserDetails principal,
            @Valid @RequestBody CreateTeamRequest request) {
        return teamService.create(principal.getId(), request);
    }

    @GetMapping("/{teamId}/members")
    @Operation(summary = "List team members; members only")
    public List<TeamMemberResponse> listMembers(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable Long teamId) {
        return teamService.listMembers(principal.getId(), teamId);
    }

    @PostMapping("/{teamId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a member; team admins only")
    public TeamMemberResponse addMember(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable Long teamId,
            @Valid @RequestBody AddMemberRequest request) {
        return teamService.addMember(principal.getId(), teamId, request);
    }

    @DeleteMapping("/{teamId}/members/{memberUserId}")
    @Operation(summary = "Remove a member; team admins only")
    public ResponseEntity<Void> removeMember(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable Long teamId,
            @PathVariable Long memberUserId) {
        teamService.removeMember(principal.getId(), teamId, memberUserId);
        return ResponseEntity.noContent().build();
    }
}
