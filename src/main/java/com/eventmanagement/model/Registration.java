package com.eventmanagement.model;

/** A single attendee registration for one event. */
public final class Registration {
    private final String reference;
    private final String attendeeName;
    private boolean attended;
    private boolean cancelled;

    public Registration(String reference, String attendeeName) {
        this(reference, attendeeName, false, false);
    }

    public Registration(String reference, String attendeeName, boolean attended, boolean cancelled) {
        this.reference = reference;
        this.attendeeName = attendeeName;
        this.attended = attended;
        this.cancelled = cancelled;
    }

    public String getReference() {
        return reference;
    }

    public String getAttendeeName() {
        return attendeeName;
    }

    public boolean isAttended() {
        return attended;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void markAttended(boolean value) {
        this.attended = value;
    }

    public void cancel() {
        this.cancelled = true;
        this.attended = false;
    }
}
