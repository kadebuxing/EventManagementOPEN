package com.eventmanagement.service;

import java.util.List;

import com.eventmanagement.model.Event;

/** Result of listing events, split into the scheduled and cancelled sections. */
public record EventListing(List<Event> active, List<Event> cancelled) {
    public EventListing {
        active = List.copyOf(active);
        cancelled = List.copyOf(cancelled);
    }

    public boolean isEmpty() {
        return active.isEmpty() && cancelled.isEmpty();
    }
}
