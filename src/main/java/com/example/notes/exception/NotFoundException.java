package com.example.notes.exception;

/** Maps to HTTP 404. Also used when a resource exists but the caller may not know it. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
