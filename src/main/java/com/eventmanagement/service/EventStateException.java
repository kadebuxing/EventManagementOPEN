package com.eventmanagement.service;

/** Raised when an action is not allowed in the current event state. */
public class EventStateException extends EventServiceException {
    private static final long serialVersionUID = 1L;

    public EventStateException(String message) {
        super(message);
    }
}
