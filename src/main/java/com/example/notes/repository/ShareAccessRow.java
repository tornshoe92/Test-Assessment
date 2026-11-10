package com.example.notes.repository;

import com.example.notes.domain.Permission;

/**
 * Flat projection of a share, used to resolve access for a whole page of notes
 * in one query instead of walking each note's share collection.
 */
public record ShareAccessRow(Long noteId, Long userId, Long teamId, Permission permission) {
}
