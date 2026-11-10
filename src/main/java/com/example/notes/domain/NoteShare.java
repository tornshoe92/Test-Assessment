package com.example.notes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;

/**
 * Grants access to a note, to either a single user or a whole team.
 * Exactly one of {@code sharedWithUser} / {@code sharedWithTeam} is set.
 */
@Entity
@Table(
        name = "note_shares",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_share_note_user", columnNames = {"note_id", "shared_with_user_id"}),
                @UniqueConstraint(name = "uk_share_note_team", columnNames = {"note_id", "shared_with_team_id"})
        }
)
public class NoteShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "note_id", nullable = false)
    private Note note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_with_user_id")
    private User sharedWithUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_with_team_id")
    private Team sharedWithTeam;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Permission permission;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected NoteShare() {
        // required by JPA
    }

    public static NoteShare toUser(Note note, User user, Permission permission) {
        NoteShare share = new NoteShare();
        share.note = note;
        share.sharedWithUser = user;
        share.permission = permission;
        return share;
    }

    public static NoteShare toTeam(Note note, Team team, Permission permission) {
        NoteShare share = new NoteShare();
        share.note = note;
        share.sharedWithTeam = team;
        share.permission = permission;
        return share;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Note getNote() {
        return note;
    }

    public User getSharedWithUser() {
        return sharedWithUser;
    }

    public Team getSharedWithTeam() {
        return sharedWithTeam;
    }

    public Permission getPermission() {
        return permission;
    }

    public void setPermission(Permission permission) {
        this.permission = permission;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isTeamShare() {
        return sharedWithTeam != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NoteShare other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
