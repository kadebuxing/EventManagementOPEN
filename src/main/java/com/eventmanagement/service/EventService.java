package com.eventmanagement.service;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.eventmanagement.model.Event;
import com.eventmanagement.model.EventStatus;
import com.eventmanagement.model.Registration;
import com.eventmanagement.model.Store;
import com.eventmanagement.report.AttendeeLine;
import com.eventmanagement.report.EventReport;
import com.eventmanagement.report.ReportExporter;
import com.eventmanagement.storage.Storage;
import com.eventmanagement.storage.StorageException;
import com.eventmanagement.util.Formats;
import com.eventmanagement.util.Validators;

/**
 * Business rules for events, registrations, attendance and reports.
 *
 * <p>The service is the only place that enforces the user story acceptance
 * criteria. It is independent from the console UI, which keeps every rule
 * directly testable.</p>
 */
public final class EventService {
    private static final Pattern EVENT_ID_PATTERN =
            Pattern.compile("e-\\d+", Pattern.CASE_INSENSITIVE);
    private static final Comparator<Event> EVENT_ORDER = Comparator
            .comparing(Event::getDate)
            .thenComparing(Event::getTime)
            .thenComparing(Event::getEventId);

    private final Storage storage;
    private final Supplier<LocalDate> clock;
    private Store store;

    public EventService(Storage storage) {
        this(storage, LocalDate::now);
    }

    public EventService(Storage storage, Supplier<LocalDate> clock) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.store = storage.load();
    }

    // ------------------------------------------------------------------
    // Infrastructure
    // ------------------------------------------------------------------
    public LocalDate today() {
        return clock.get();
    }

    public boolean hasEvents() {
        return !store.getEvents().isEmpty();
    }

    public void reload() {
        this.store = storage.load();
    }

    private void persist() {
        try {
            storage.save(store);
        } catch (StorageException ex) {
            try {
                this.store = storage.load();
            } catch (StorageException reloadFailure) {
                ex.addSuppressed(reloadFailure);
            }
            throw ex;
        }
    }

    // ------------------------------------------------------------------
    // Lookup helpers
    // ------------------------------------------------------------------
    public Event getEvent(String eventId) {
        return findEvent(eventId).orElseThrow(() -> new NotFoundException(
                "Event " + Validators.trimToEmpty(eventId) + " not found"));
    }

    public Optional<Event> findEvent(String eventId) {
        String key = Validators.trimToEmpty(eventId);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        return store.getEvents().stream().filter(event -> event.hasId(key)).findFirst();
    }

    private String nextEventId() {
        int number = Math.max(store.getNextEventNumber(), 1);
        Set<String> used = new HashSet<>();
        for (Event event : store.getEvents()) {
            used.add(event.getEventId().toUpperCase(Locale.ROOT));
        }
        while (used.contains(String.format(Locale.ROOT, "E-%03d", number).toUpperCase(Locale.ROOT))) {
            number++;
        }
        store.setNextEventNumber(number + 1);
        return String.format(Locale.ROOT, "E-%03d", number);
    }

    private String nextRegistrationReference() {
        int number = Math.max(store.getNextRegistrationNumber(), 1);
        Set<String> used = new HashSet<>();
        for (Event event : store.getEvents()) {
            for (Registration registration : event.getRegistrations()) {
                used.add(registration.getReference().toUpperCase(Locale.ROOT));
            }
        }
        while (used.contains(String.format(Locale.ROOT, "R-%04d", number).toUpperCase(Locale.ROOT))) {
            number++;
        }
        store.setNextRegistrationNumber(number + 1);
        return String.format(Locale.ROOT, "R-%04d", number);
    }

    // ------------------------------------------------------------------
    // OPEN-1 / OPEN-10: create and edit events
    // ------------------------------------------------------------------
    public Event createEvent(
            String name,
            String dateText,
            String timeText,
            String venue,
            String capacityText) {
        String validName = Validators.validateEventName(name);
        LocalDate validDate = Validators.parseEventDate(dateText, today());
        LocalTime validTime = Validators.parseEventTime(timeText);
        String validVenue = Validators.validateVenue(venue);
        int validCapacity = Validators.parseCapacity(capacityText);

        Event event = new Event(
                nextEventId(),
                validName,
                validDate,
                validTime,
                validVenue,
                validCapacity,
                EventStatus.ACTIVE);
        store.addEvent(event);
        persist();
        return event;
    }

    public Event updateEvent(
            String eventId,
            String name,
            String dateText,
            String timeText,
            String venue,
            String capacityText) {
        Event event = getEvent(eventId);
        if (event.isCancelled()) {
            throw new EventStateException(
                    "This event is cancelled and cannot be edited; undo the cancellation first");
        }
        if (event.isPast(today())) {
            throw new EventStateException("This event has passed and can no longer be edited");
        }

        String validName = name == null ? null : Validators.validateEventName(name);
        LocalDate validDate = dateText == null
                ? null
                : Validators.parseEventDate(dateText, today());
        LocalTime validTime = timeText == null ? null : Validators.parseEventTime(timeText);
        String validVenue = venue == null ? null : Validators.validateVenue(venue);
        Integer validCapacity = capacityText == null
                ? null
                : Validators.parseCapacity(capacityText);
        if (validCapacity != null && validCapacity < event.registeredCount()) {
            throw new ValidationException(
                    "Capacity cannot be lower than the number of registered attendees ("
                            + event.registeredCount() + ")");
        }

        boolean changed = false;
        if (validName != null) {
            event.rename(validName);
            changed = true;
        }
        if (validDate != null || validTime != null) {
            event.reschedule(
                    validDate != null ? validDate : event.getDate(),
                    validTime != null ? validTime : event.getTime());
            changed = true;
        }
        if (validVenue != null) {
            event.relocate(validVenue);
            changed = true;
        }
        if (validCapacity != null) {
            event.changeCapacity(validCapacity);
            changed = true;
        }
        if (changed) {
            persist();
        }
        return event;
    }

    public Event setCapacity(String eventId, String capacityText) {
        return updateEvent(eventId, null, null, null, null, capacityText);
    }

    // ------------------------------------------------------------------
    // OPEN-2: list, filter and search
    // ------------------------------------------------------------------
    public EventListing listEvents(String query) {
        List<Event> matches = store.getEvents().stream()
                .filter(event -> matches(event, query))
                .sorted(EVENT_ORDER)
                .toList();
        List<Event> active = matches.stream().filter(event -> !event.isCancelled()).toList();
        List<Event> cancelled = matches.stream().filter(Event::isCancelled).toList();
        return new EventListing(active, cancelled);
    }

    private static boolean matches(Event event, String query) {
        if (query == null) {
            return true;
        }
        String text = query.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return true;
        }
        if (EVENT_ID_PATTERN.matcher(text).matches()) {
            return event.getEventId().equalsIgnoreCase(text);
        }
        if (event.getName().toLowerCase(Locale.ROOT).contains(text)) {
            return true;
        }
        if (event.getVenue().toLowerCase(Locale.ROOT).contains(text)) {
            return true;
        }
        return Formats.date(event.getDate()).equalsIgnoreCase(text);
    }

    // ------------------------------------------------------------------
    // OPEN-8: cancel and restore events
    // ------------------------------------------------------------------
    public Event cancelEvent(String eventId) {
        Event event = getEvent(eventId);
        if (event.isCancelled()) {
            throw new EventStateException("Event is already cancelled");
        }
        event.cancel();
        persist();
        return event;
    }

    public Event undoCancelEvent(String eventId) {
        Event event = getEvent(eventId);
        if (!event.isCancelled()) {
            throw new EventStateException("Event is not cancelled");
        }
        event.restore();
        persist();
        return event;
    }

    // ------------------------------------------------------------------
    // OPEN-3 / OPEN-6: registrations
    // ------------------------------------------------------------------
    public Registration registerAttendee(String eventId, String attendeeName) {
        Event event = getEvent(eventId);
        if (event.isCancelled()) {
            throw new EventStateException("Cancelled events cannot accept registrations");
        }
        String validName = Validators.validateAttendeeName(attendeeName);
        if (event.isFull()) {
            throw new EventStateException(
                    "Event capacity has been reached (" + event.getCapacity()
                            + "); further registrations are blocked");
        }
        Registration registration =
                new Registration(nextRegistrationReference(), validName);
        event.addRegistration(registration);
        persist();
        return registration;
    }

    public Event cancelRegistration(String eventId, String reference, boolean confirmed) {
        Event event = getEvent(eventId);
        Registration registration = event.findActiveRegistration(reference)
                .orElseThrow(() -> new NotFoundException(
                        "Registration " + Validators.trimToEmpty(reference)
                                + " not found for " + event.getEventId()));
        if (!confirmed) {
            throw new ConfirmationRequiredException(
                    "Registration cancellation must be confirmed before it is final");
        }
        registration.cancel();
        persist();
        return event;
    }

    // ------------------------------------------------------------------
    // OPEN-5: attendance
    // ------------------------------------------------------------------
    public Registration markAttendance(String eventId, String reference, boolean attended) {
        Event event = getEvent(eventId);
        if (event.isCancelled()) {
            throw new EventStateException("Cancelled events cannot track attendance");
        }
        Registration registration = event.findActiveRegistration(reference)
                .orElseThrow(() -> new NotFoundException(
                        "Only registered attendees can be marked as attended"));
        if (event.getDate().isAfter(today())) {
            throw new EventStateException(
                    "Attendance can only be marked on or after the event date");
        }
        registration.markAttended(attended);
        persist();
        return registration;
    }

    // ------------------------------------------------------------------
    // OPEN-7: reports
    // ------------------------------------------------------------------
    public EventReport generateReport(String eventId) {
        Event event = getEvent(eventId);
        List<AttendeeLine> attendees = event.activeRegistrations().stream()
                .map(registration -> new AttendeeLine(
                        registration.getReference(),
                        registration.getAttendeeName(),
                        registration.isAttended()))
                .toList();
        return new EventReport(
                event.getEventId(),
                event.getName(),
                event.getDate(),
                event.getTime(),
                event.getVenue(),
                event.getCapacity(),
                event.getStatus(),
                event.registeredCount(),
                event.attendedCount(),
                attendees);
    }

    public Path exportReport(String eventId, Path target) {
        return ReportExporter.write(generateReport(eventId), target);
    }
}
