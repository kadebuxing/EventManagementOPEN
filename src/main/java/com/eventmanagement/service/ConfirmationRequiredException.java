package com.eventmanagement.service;

/** Raised when an action needs an explicit confirmation first. */
public class ConfirmationRequiredException extends EventServiceException {
    private static final long serialVersionUID = 1L;

    public ConfirmationRequiredException(String message) {
        super(message);
    }
}
