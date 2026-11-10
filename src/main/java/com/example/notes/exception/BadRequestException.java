package com.example.notes.exception;

/** Maps to HTTP 400 for semantic problems that bean validation cannot express. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
