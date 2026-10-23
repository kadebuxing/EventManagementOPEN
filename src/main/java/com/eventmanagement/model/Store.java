package com.eventmanagement.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Everything that is persisted to storage. */
public final class Store {
    private final List<Event> events = new ArrayList<>();
    private int nextEventNumber = 1;
    private int nextRegistrationNumber = 1;

    public List<Event> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public void addEvent(Event event) {
        events.add(event);
    }

    public int getNextEventNumber() {
        return nextEventNumber;
    }

    public void setNextEventNumber(int nextEventNumber) {
        this.nextEventNumber = Math.max(nextEventNumber, 1);
    }

    public int getNextRegistrationNumber() {
        return nextRegistrationNumber;
    }

    public void setNextRegistrationNumber(int nextRegistrationNumber) {
        this.nextRegistrationNumber = Math.max(nextRegistrationNumber, 1);
    }
}
