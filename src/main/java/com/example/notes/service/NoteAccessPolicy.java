package com.example.notes.service;

import com.example.notes.domain.AccessLevel;
import com.example.notes.domain.Note;
import com.example.notes.domain.NoteShare;
import com.example.notes.domain.Permission;
import com.example.notes.repository.NoteShareRepository;
import com.example.notes.repository.ShareAccessRow;
import com.example.notes.repository.TeamMembershipRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Single place where "who may do what to this note" is decided.
 *
 * <p>Access comes from three sources, strongest first: ownership, then a direct
 * user share, then a share with a team the user belongs to. Where several apply,
 * the most permissive wins.
 */
@Component
public class NoteAccessPolicy {

    private final TeamMembershipRepository memberships;
    private final NoteShareRepository shares;

    public NoteAccessPolicy(TeamMembershipRepository memberships, NoteShareRepository shares) {
        this.memberships = memberships;
        this.shares = shares;
    }

    /** Ids of every team the user belongs to. */
    public Set<Long> teamIdsOf(Long userId) {
        return memberships.findTeamIdsByUserId(userId);
    }

    /** Resolves access for one note from its already-loaded share collection. */
    public AccessLevel levelFor(Long userId, Note note, Set<Long> teamIds) {
        if (note.getOwner().getId().equals(userId)) {
            return AccessLevel.OWNER;
        }
        AccessLevel best = AccessLevel.NONE;
        for (NoteShare share : note.getShares()) {
            if (!appliesTo(share, userId, teamIds)) {
                continue;
            }
            if (share.getPermission() == Permission.WRITE) {
                return AccessLevel.WRITE;
            }
            best = AccessLevel.READ;
        }
        return best;
    }

    /**
     * Resolves access for a page of notes using a single share query, so listing
     * does not issue one query per note.
     */
    public Map<Long, AccessLevel> levelsFor(Long userId, Set<Long> teamIds, Collection<Note> notes) {
        Map<Long, AccessLevel> levels = new HashMap<>();
        if (notes.isEmpty()) {
            return levels;
        }

        List<Long> notOwned = notes.stream()
                .filter(note -> {
                    boolean owned = note.getOwner().getId().equals(userId);
                    if (owned) {
                        levels.put(note.getId(), AccessLevel.OWNER);
                    }
                    return !owned;
                })
                .map(Note::getId)
                .toList();

        if (notOwned.isEmpty()) {
            return levels;
        }

        for (ShareAccessRow row : shares.findAccessRowsByNoteIds(notOwned)) {
            if (!appliesTo(row, userId, teamIds)) {
                continue;
            }
            AccessLevel candidate = row.permission() == Permission.WRITE ? AccessLevel.WRITE : AccessLevel.READ;
            levels.merge(row.noteId(), candidate, NoteAccessPolicy::strongest);
        }

        // A note reached the page via the access filter, so anything still
        // unresolved is a defensive fallback rather than an expected case.
        notOwned.forEach(id -> levels.putIfAbsent(id, AccessLevel.READ));
        return levels;
    }

    private static boolean appliesTo(NoteShare share, Long userId, Set<Long> teamIds) {
        if (share.getSharedWithUser() != null && share.getSharedWithUser().getId().equals(userId)) {
            return true;
        }
        return share.getSharedWithTeam() != null && teamIds.contains(share.getSharedWithTeam().getId());
    }

    private static boolean appliesTo(ShareAccessRow row, Long userId, Set<Long> teamIds) {
        if (row.userId() != null && row.userId().equals(userId)) {
            return true;
        }
        return row.teamId() != null && teamIds.contains(row.teamId());
    }

    private static AccessLevel strongest(AccessLevel a, AccessLevel b) {
        return a.compareTo(b) >= 0 ? a : b;
    }
}
