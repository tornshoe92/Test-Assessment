package com.example.notes.exception;

/** Maps to HTTP 409: the request clashes with the current state of the resource. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
