package com.eventmanagement.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** An event that can hold registrations. */
public final class Event {
    private final String eventId;
    private String name;
    private LocalDate date;
    private LocalTime time;
    private String venue;
    private int capacity;
    private EventStatus status;
    private final List<Registration> registrations = new ArrayList<>();

    public Event(
            String eventId,
            String name,
            LocalDate date,
            LocalTime time,
            String venue,
            int capacity,
            EventStatus status) {
        this.eventId = eventId;
        this.name = name;
        this.date = date;
        this.time = time;
        this.venue = venue;
        this.capacity = capacity;
        this.status = status;
    }

    public String getEventId() {
        return eventId;
    }

    public String getName() {
        return name;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalTime getTime() {
        return time;
    }

    public String getVenue() {
        return venue;
    }

    public int getCapacity() {
        return capacity;
    }

    public EventStatus getStatus() {
        return status;
    }

    public boolean isCancelled() {
        return status == EventStatus.CANCELLED;
    }

    public boolean isPast(LocalDate today) {
        return date.isBefore(today);
    }

    public void rename(String value) {
        this.name = value;
    }

    public void reschedule(LocalDate newDate, LocalTime newTime) {
        this.date = newDate;
        this.time = newTime;
    }

    public void relocate(String newVenue) {
        this.venue = newVenue;
    }

    public void changeCapacity(int newCapacity) {
        this.capacity = newCapacity;
    }

    public void cancel() {
        this.status = EventStatus.CANCELLED;
    }

    public void restore() {
        this.status = EventStatus.ACTIVE;
    }

    public void addRegistration(Registration registration) {
        registrations.add(registration);
    }

    public List<Registration> getRegistrations() {
        return Collections.unmodifiableList(registrations);
    }

    public List<Registration> activeRegistrations() {
        return registrations.stream().filter(item -> !item.isCancelled()).toList();
    }

    public int registeredCount() {
        return (int) registrations.stream().filter(item -> !item.isCancelled()).count();
    }

    public int attendedCount() {
        return (int) registrations.stream()
                .filter(item -> !item.isCancelled() && item.isAttended())
                .count();
    }

    public int availablePlaces() {
        return Math.max(capacity - registeredCount(), 0);
    }

    public boolean isFull() {
        return registeredCount() >= capacity;
    }

    public Optional<Registration> findRegistration(String reference) {
        String key = reference == null ? "" : reference.trim();
        return registrations.stream()
                .filter(item -> item.getReference().equalsIgnoreCase(key))
                .findFirst();
    }

    public Optional<Registration> findActiveRegistration(String reference) {
        return findRegistration(reference).filter(item -> !item.isCancelled());
    }

    /** Case-insensitive helper used by the CLI to resolve an event by id. */
    public boolean hasId(String candidate) {
        return eventId.toLowerCase(Locale.ROOT)
                .equals(candidate == null ? "" : candidate.trim().toLowerCase(Locale.ROOT));
    }
}
