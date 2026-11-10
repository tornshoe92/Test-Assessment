package com.example.notes.web;

import com.example.notes.security.AppUserDetails;
import com.example.notes.service.NoteService;
import com.example.notes.web.dto.CreateNoteRequest;
import com.example.notes.web.dto.CreateShareRequest;
import com.example.notes.web.dto.NoteResponse;
import com.example.notes.web.dto.NoteScope;
import com.example.notes.web.dto.NoteSummaryResponse;
import com.example.notes.web.dto.PageResponse;
import com.example.notes.web.dto.ShareResponse;
import com.example.notes.web.dto.UpdateNoteRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/notes")
@Tag(name = "Notes", description = "Capture, find, edit and share notes")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping
    @Operation(summary = "List notes the caller can see (owned plus shared)")
    public PageResponse<NoteSummaryResponse> list(
            @AuthenticationPrincipal AppUserDetails principal,
            @RequestParam(defaultValue = "ALL") NoteScope scope,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) String tag,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return noteService.list(principal.getId(), scope, query, tag, pageable);
    }

    @PostMapping
    @Operation(summary = "Create a note owned by the caller")
    public ResponseEntity<NoteResponse> create(
            @AuthenticationPrincipal AppUserDetails principal,
            @Valid @RequestBody CreateNoteRequest request) {
        NoteResponse created = noteService.create(principal.getId(), request);
        return ResponseEntity
                .created(URI.create("/api/v1/notes/" + created.id()))
                .body(created);
    }

    @GetMapping("/{noteId}")
    @Operation(summary = "Fetch a single note")
    public NoteResponse get(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable Long noteId) {
        return noteService.get(principal.getId(), noteId);
    }

    @PutMapping("/{noteId}")
    @Operation(summary = "Replace a note; requires WRITE access")
    public NoteResponse update(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable Long noteId,
            @Valid @RequestBody UpdateNoteRequest request) {
        return noteService.update(principal.getId(), noteId, request);
    }

    @DeleteMapping("/{noteId}")
    @Operation(summary = "Delete a note; owner only")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable Long noteId) {
        noteService.delete(principal.getId(), noteId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{noteId}/shares")
    @Operation(summary = "List who a note is shared with")
    public List<ShareResponse> listShares(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable Long noteId) {
        return noteService.listShares(principal.getId(), noteId);
    }

    @PostMapping("/{noteId}/shares")
    @Operation(summary = "Share a note with a user or a team; owner only, idempotent per target")
    public ShareResponse share(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable Long noteId,
            @Valid @RequestBody CreateShareRequest request) {
        return noteService.share(principal.getId(), noteId, request);
    }

    @DeleteMapping("/{noteId}/shares/{shareId}")
    @Operation(summary = "Revoke a share; owner only")
    public ResponseEntity<Void> revokeShare(
            @AuthenticationPrincipal AppUserDetails principal,
            @PathVariable Long noteId,
            @PathVariable Long shareId) {
        noteService.revokeShare(principal.getId(), noteId, shareId);
        return ResponseEntity.noContent().build();
    }
}
