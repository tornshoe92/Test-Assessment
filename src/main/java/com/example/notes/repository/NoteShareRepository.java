package com.example.notes.repository;

import com.example.notes.domain.NoteShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NoteShareRepository extends JpaRepository<NoteShare, Long> {

    List<NoteShare> findByNoteId(Long noteId);

    Optional<NoteShare> findByNoteIdAndSharedWithUserId(Long noteId, Long userId);

    Optional<NoteShare> findByNoteIdAndSharedWithTeamId(Long noteId, Long teamId);

    @Query("""
            select new com.example.notes.repository.ShareAccessRow(
                       s.note.id, s.sharedWithUser.id, s.sharedWithTeam.id, s.permission)
            from NoteShare s
            where s.note.id in :noteIds
            """)
    List<ShareAccessRow> findAccessRowsByNoteIds(@Param("noteIds") Collection<Long> noteIds);
}
