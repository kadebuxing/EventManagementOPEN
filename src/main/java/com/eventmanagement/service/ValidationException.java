package com.eventmanagement.service;

/** Raised when user supplied data fails a business validation rule. */
public class ValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ValidationException(String message) {
        super(message);
    }
}
