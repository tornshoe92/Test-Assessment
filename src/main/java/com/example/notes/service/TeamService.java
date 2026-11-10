package com.example.notes.service;

import com.example.notes.domain.Team;
import com.example.notes.domain.TeamMembership;
import com.example.notes.domain.TeamRole;
import com.example.notes.domain.User;
import com.example.notes.exception.BadRequestException;
import com.example.notes.exception.ConflictException;
import com.example.notes.exception.ForbiddenException;
import com.example.notes.exception.NotFoundException;
import com.example.notes.repository.TeamMembershipRepository;
import com.example.notes.repository.TeamRepository;
import com.example.notes.repository.UserRepository;
import com.example.notes.web.dto.AddMemberRequest;
import com.example.notes.web.dto.CreateTeamRequest;
import com.example.notes.web.dto.TeamMemberResponse;
import com.example.notes.web.dto.TeamResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class TeamService {

    private final TeamRepository teams;
    private final TeamMembershipRepository memberships;
    private final UserRepository users;

    public TeamService(TeamRepository teams, TeamMembershipRepository memberships, UserRepository users) {
        this.teams = teams;
        this.memberships = memberships;
        this.users = users;
    }

    /** Teams the caller belongs to. Teams are not publicly listable. */
    @Transactional(readOnly = true)
    public List<TeamResponse> listMyTeams(Long userId) {
        return memberships.findByUserId(userId).stream()
                .map(membership -> toResponse(membership.getTeam(), membership.getRole()))
                .sorted(Comparator.comparing(TeamResponse::name))
                .toList();
    }

    @Transactional
    public TeamResponse create(Long userId, CreateTeamRequest request) {
        String name = request.name().trim();
        if (teams.existsByName(name)) {
            throw new ConflictException("A team named " + name + " already exists");
        }
        User creator = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId + " not found"));

        Team team = teams.save(new Team(name));
        memberships.save(new TeamMembership(team, creator, TeamRole.ADMIN));
        return new TeamResponse(team.getId(), team.getName(), TeamRole.ADMIN, 1, team.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<TeamMemberResponse> listMembers(Long userId, Long teamId) {
        requireMembership(userId, teamId);
        return memberships.findByTeamId(teamId).stream()
                .map(TeamMemberResponse::from)
                .sorted(Comparator.comparing(TeamMemberResponse::username))
                .toList();
    }

    @Transactional
    public TeamMemberResponse addMember(Long userId, Long teamId, AddMemberRequest request) {
        TeamMembership callerMembership = requireMembership(userId, teamId);
        if (callerMembership.getRole() != TeamRole.ADMIN) {
            throw new ForbiddenException("Only a team admin can add members");
        }

        User target = users.findByUsername(request.username().trim())
                .orElseThrow(() -> new NotFoundException("No user named " + request.username()));
        if (memberships.findByTeamIdAndUserId(teamId, target.getId()).isPresent()) {
            throw new ConflictException(request.username() + " is already a member of this team");
        }

        TeamRole role = request.role() == null ? TeamRole.MEMBER : request.role();
        TeamMembership membership =
                memberships.save(new TeamMembership(callerMembership.getTeam(), target, role));
        return TeamMemberResponse.from(membership);
    }

    @Transactional
    public void removeMember(Long userId, Long teamId, Long memberUserId) {
        TeamMembership callerMembership = requireMembership(userId, teamId);
        if (callerMembership.getRole() != TeamRole.ADMIN) {
            throw new ForbiddenException("Only a team admin can remove members");
        }
        TeamMembership target = memberships.findByTeamIdAndUserId(teamId, memberUserId)
                .orElseThrow(() -> new NotFoundException("User " + memberUserId + " is not a member of this team"));

        boolean removingLastAdmin = target.getRole() == TeamRole.ADMIN
                && memberships.findByTeamId(teamId).stream()
                        .filter(m -> m.getRole() == TeamRole.ADMIN)
                        .count() == 1;
        if (removingLastAdmin) {
            throw new BadRequestException("A team must keep at least one admin");
        }
        memberships.delete(target);
    }

    /** A team is only visible to its members; non-members get a 404. */
    private TeamMembership requireMembership(Long userId, Long teamId) {
        return memberships.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new NotFoundException("Team " + teamId + " not found"));
    }

    private TeamResponse toResponse(Team team, TeamRole myRole) {
        return new TeamResponse(
                team.getId(),
                team.getName(),
                myRole,
                memberships.countByTeamId(team.getId()),
                team.getCreatedAt());
    }
}
