package com.example.notes.web.dto;

/** Which slice of the accessible notes a list request wants. */
public enum NoteScope {
    /** Everything the caller can see. */
    ALL,
    /** Only notes the caller owns. */
    OWNED,
    /** Only notes shared with the caller by someone else. */
    SHARED
}
