package com.example.notes.domain;

/**
 * The effective access a user has to a note, resolved from ownership plus any
 * direct or team shares. Ordered from least to most privileged.
 */
public enum AccessLevel {
    NONE,
    READ,
    WRITE,
    OWNER;

    public boolean canRead() {
        return this != NONE;
    }

    public boolean canWrite() {
        return this == WRITE || this == OWNER;
    }

    public boolean isOwner() {
        return this == OWNER;
    }
}
