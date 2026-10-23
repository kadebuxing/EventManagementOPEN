package com.eventmanagement.service;

/** Base class for failures that are not field validation errors. */
public class EventServiceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public EventServiceException(String message) {
        super(message);
    }
}
