package com.example.notes.service;

import com.example.notes.domain.AccessLevel;
import com.example.notes.domain.Note;
import com.example.notes.domain.NoteShare;
import com.example.notes.domain.Team;
import com.example.notes.domain.User;
import com.example.notes.exception.BadRequestException;
import com.example.notes.exception.ConflictException;
import com.example.notes.exception.ForbiddenException;
import com.example.notes.exception.NotFoundException;
import com.example.notes.repository.NoteRepository;
import com.example.notes.repository.NoteShareRepository;
import com.example.notes.repository.NoteSpecifications;
import com.example.notes.repository.TeamRepository;
import com.example.notes.repository.UserRepository;
import com.example.notes.web.dto.CreateNoteRequest;
import com.example.notes.web.dto.CreateShareRequest;
import com.example.notes.web.dto.NoteResponse;
import com.example.notes.web.dto.NoteScope;
import com.example.notes.web.dto.NoteSummaryResponse;
import com.example.notes.web.dto.PageResponse;
import com.example.notes.web.dto.ShareResponse;
import com.example.notes.web.dto.UpdateNoteRequest;
import com.example.notes.web.dto.UserSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class NoteService {

    private static final int EXCERPT_LENGTH = 160;

    private final NoteRepository notes;
    private final NoteShareRepository shares;
    private final UserRepository users;
    private final TeamRepository teams;
    private final NoteAccessPolicy access;

    public NoteService(NoteRepository notes,
                       NoteShareRepository shares,
                       UserRepository users,
                       TeamRepository teams,
                       NoteAccessPolicy access) {
        this.notes = notes;
        this.shares = shares;
        this.users = users;
        this.teams = teams;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public PageResponse<NoteSummaryResponse> list(Long userId,
                                                  NoteScope scope,
                                                  String query,
                                                  String tag,
                                                  Pageable pageable) {
        Set<Long> teamIds = access.teamIdsOf(userId);

        Specification<Note> spec = NoteSpecifications.accessibleBy(userId, teamIds);
        if (scope == NoteScope.OWNED) {
            spec = spec.and(NoteSpecifications.ownedBy(userId));
        } else if (scope == NoteScope.SHARED) {
            spec = spec.and(NoteSpecifications.notOwnedBy(userId));
        }
        if (StringUtils.hasText(query)) {
            spec = spec.and(NoteSpecifications.matchesText(query.trim()));
        }
        if (StringUtils.hasText(tag)) {
            spec = spec.and(NoteSpecifications.hasTag(tag.trim()));
        }

        Page<Note> page = notes.findAll(spec, pageable);
        Map<Long, AccessLevel> levels = access.levelsFor(userId, teamIds, page.getContent());
        return PageResponse.of(page, note -> toSummary(note, levels.getOrDefault(note.getId(), AccessLevel.READ)));
    }

    @Transactional
    public NoteResponse create(Long userId, CreateNoteRequest request) {
        User owner = requireUser(userId);
        Note note = new Note(request.title().trim(), nullSafeContent(request.content()), owner);
        note.replaceTags(normaliseTags(request.tags()));
        notes.save(note);
        return toResponse(note, AccessLevel.OWNER);
    }

    @Transactional(readOnly = true)
    public NoteResponse get(Long userId, Long noteId) {
        Note note = requireNote(noteId);
        AccessLevel level = requireAccess(userId, note);
        return toResponse(note, level);
    }

    @Transactional
    public NoteResponse update(Long userId, Long noteId, UpdateNoteRequest request) {
        Note note = requireNote(noteId);
        AccessLevel level = requireAccess(userId, note);
        if (!level.canWrite()) {
            throw new ForbiddenException("You have read-only access to this note");
        }
        if (request.version() != null && !request.version().equals(note.getVersion())) {
            throw new ConflictException("Note was modified by someone else; reload and retry (current version "
                    + note.getVersion() + ")");
        }

        note.setTitle(request.title().trim());
        note.setContent(nullSafeContent(request.content()));
        note.replaceTags(normaliseTags(request.tags()));
        notes.saveAndFlush(note);
        return toResponse(note, level);
    }

    @Transactional
    public void delete(Long userId, Long noteId) {
        Note note = requireNote(noteId);
        AccessLevel level = requireAccess(userId, note);
        if (!level.isOwner()) {
            throw new ForbiddenException("Only the owner can delete a note");
        }
        notes.delete(note);
    }

    // --- sharing -----------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ShareResponse> listShares(Long userId, Long noteId) {
        Note note = requireNote(noteId);
        requireAccess(userId, note);
        return shares.findByNoteId(noteId).stream()
                .map(ShareResponse::from)
                .sorted(Comparator.comparing(ShareResponse::id))
                .toList();
    }

    /**
     * Shares a note with a user or a team. Re-sharing with the same target
     * updates the existing grant rather than failing, so the call is idempotent.
     */
    @Transactional
    public ShareResponse share(Long userId, Long noteId, CreateShareRequest request) {
        boolean hasUser = StringUtils.hasText(request.username());
        boolean hasTeam = StringUtils.hasText(request.teamName());
        if (hasUser == hasTeam) {
            throw new BadRequestException("Provide exactly one of username or teamName");
        }

        Note note = requireNote(noteId);
        AccessLevel level = requireAccess(userId, note);
        if (!level.isOwner()) {
            throw new ForbiddenException("Only the owner can share a note");
        }

        if (hasUser) {
            User target = users.findByUsername(request.username().trim())
                    .orElseThrow(() -> new NotFoundException("No user named " + request.username()));
            if (target.getId().equals(userId)) {
                throw new BadRequestException("A note is already available to its owner");
            }
            NoteShare share = shares.findByNoteIdAndSharedWithUserId(noteId, target.getId())
                    .orElseGet(() -> NoteShare.toUser(note, target, request.permission()));
            share.setPermission(request.permission());
            note.addShare(share);
            return ShareResponse.from(shares.save(share));
        }

        Team target = teams.findByName(request.teamName().trim())
                .orElseThrow(() -> new NotFoundException("No team named " + request.teamName()));
        NoteShare share = shares.findByNoteIdAndSharedWithTeamId(noteId, target.getId())
                .orElseGet(() -> NoteShare.toTeam(note, target, request.permission()));
        share.setPermission(request.permission());
        note.addShare(share);
        return ShareResponse.from(shares.save(share));
    }

    @Transactional
    public void revokeShare(Long userId, Long noteId, Long shareId) {
        Note note = requireNote(noteId);
        AccessLevel level = requireAccess(userId, note);
        if (!level.isOwner()) {
            throw new ForbiddenException("Only the owner can change sharing on a note");
        }
        NoteShare share = shares.findById(shareId)
                .filter(candidate -> candidate.getNote().getId().equals(noteId))
                .orElseThrow(() -> new NotFoundException("Share " + shareId + " not found on this note"));
        note.removeShare(share);
        shares.delete(share);
    }

    // --- helpers -----------------------------------------------------------

    private Note requireNote(Long noteId) {
        return notes.findById(noteId)
                .orElseThrow(() -> new NotFoundException("Note " + noteId + " not found"));
    }

    private User requireUser(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId + " not found"));
    }

    /**
     * Returns the caller's access, or 404 when they have none: a note they cannot
     * see should not be distinguishable from one that does not exist.
     */
    private AccessLevel requireAccess(Long userId, Note note) {
        AccessLevel level = access.levelFor(userId, note, access.teamIdsOf(userId));
        if (!level.canRead()) {
            throw new NotFoundException("Note " + note.getId() + " not found");
        }
        return level;
    }

    private static String nullSafeContent(String content) {
        return content == null ? "" : content;
    }

    /** Tags are trimmed, lower-cased and de-duplicated so filtering is predictable. */
    private static Set<String> normaliseTags(Set<String> tags) {
        if (tags == null) {
            return Set.of();
        }
        return tags.stream()
                .filter(StringUtils::hasText)
                .map(tag -> tag.trim().toLowerCase())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private NoteResponse toResponse(Note note, AccessLevel level) {
        List<ShareResponse> shareResponses = note.getShares().stream()
                .map(ShareResponse::from)
                .sorted(Comparator.comparing(ShareResponse::id))
                .toList();
        return new NoteResponse(
                note.getId(),
                note.getTitle(),
                note.getContent(),
                Set.copyOf(note.getTags()),
                UserSummary.from(note.getOwner()),
                level,
                shareResponses,
                note.getVersion(),
                note.getCreatedAt(),
                note.getUpdatedAt());
    }

    private NoteSummaryResponse toSummary(Note note, AccessLevel level) {
        return new NoteSummaryResponse(
                note.getId(),
                note.getTitle(),
                excerpt(note.getContent()),
                Set.copyOf(note.getTags()),
                UserSummary.from(note.getOwner()),
                level,
                note.getVersion(),
                note.getCreatedAt(),
                note.getUpdatedAt());
    }

    private static String excerpt(String content) {
        if (content == null) {
            return "";
        }
        String collapsed = content.strip();
        if (collapsed.length() <= EXCERPT_LENGTH) {
            return collapsed;
        }
        return collapsed.substring(0, EXCERPT_LENGTH).stripTrailing() + "...";
    }
}
