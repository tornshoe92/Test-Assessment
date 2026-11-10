package com.example.notes.repository;

import com.example.notes.domain.TeamMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface TeamMembershipRepository extends JpaRepository<TeamMembership, Long> {

    Optional<TeamMembership> findByTeamIdAndUserId(Long teamId, Long userId);

    List<TeamMembership> findByUserId(Long userId);

    List<TeamMembership> findByTeamId(Long teamId);

    long countByTeamId(Long teamId);

    @Query("select m.team.id from TeamMembership m where m.user.id = :userId")
    Set<Long> findTeamIdsByUserId(@Param("userId") Long userId);
}
