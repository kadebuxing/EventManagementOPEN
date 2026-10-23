package com.eventmanagement.service;

/** Raised when an event or registration cannot be found. */
public class NotFoundException extends EventServiceException {
    private static final long serialVersionUID = 1L;

    public NotFoundException(String message) {
        super(message);
    }
}
