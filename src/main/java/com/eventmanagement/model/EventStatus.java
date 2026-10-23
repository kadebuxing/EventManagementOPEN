package com.eventmanagement.model;

/** Lifecycle state of an event. */
public enum EventStatus {
    ACTIVE("Active"),
    CANCELLED("Cancelled");

    private final String displayName;

    EventStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static EventStatus fromDisplay(String value) {
        for (EventStatus status : values()) {
            if (status.displayName.equalsIgnoreCase(value)) {
                return status;
            }
        }
        return ACTIVE;
    }
}
