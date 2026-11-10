package com.example.notes.exception;

/** Maps to HTTP 403: the caller can see the resource but not perform this action on it. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
