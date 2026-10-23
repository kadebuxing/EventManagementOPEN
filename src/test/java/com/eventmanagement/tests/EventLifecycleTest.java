package com.eventmanagement.tests;

import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalTime;

import com.eventmanagement.model.Event;
import com.eventmanagement.service.EventListing;
import com.eventmanagement.service.EventService;
import com.eventmanagement.service.EventStateException;
import com.eventmanagement.service.NotFoundException;
import com.eventmanagement.service.ValidationException;

/** OPEN-1, OPEN-2, OPEN-4, OPEN-8 and OPEN-10: event lifecycle. */
public final class EventLifecycleTest extends ServiceFixture {

    @Test
    public void cancelEventMovesItToTheCancelledSection() {
        Event event = createSampleEvent();
        service.cancelEvent(event.getEventId());

        EventListing listing = service.listEvents(null);
        Assert.assertEquals(0, listing.active().size(), "No active events remain");
        Assert.assertEquals(1, listing.cancelled().size(), "The event is listed as cancelled");
        Assert.assertEquals("E-001", listing.cancelled().get(0).getEventId(), "Same event");
    }

    @Test
    public void cancellingTwiceIsRejected() {
        Event event = createSampleEvent();
        service.cancelEvent(event.getEventId());
        EventStateException error = Assert.assertThrows(
                EventStateException.class,
                () -> service.cancelEvent(event.getEventId()),
                "A cancelled event cannot be cancelled again");
        Assert.assertEquals("Event is already cancelled", error.getMessage(), "Unexpected message");
    }

    @Test
    public void cancelledEventCannotBeEdited() {
        Event event = createSampleEvent();
        service.cancelEvent(event.getEventId());
        Assert.assertThrows(
                EventStateException.class,
                () -> service.updateEvent(event.getEventId(), "Renamed", null, null, null, null),
                "Cancelled events are locked");
    }

    @Test
    public void cancelledEventCannotBeRestoredTwice() {
        Event event = createSampleEvent();
        service.cancelEvent(event.getEventId());
        service.undoCancelEvent(event.getEventId());
        Assert.assertThrows(
                EventStateException.class,
                () -> service.undoCancelEvent(event.getEventId()),
                "Nothing to undo on an active event");
    }

    @Test
    public void createEventAssignsSequentialIdentifiers() {
        Event first = createSampleEvent();
        Event second = service.createEvent("Team Sync", "01/11/2026", "10:00", "Room B", "5");
        Assert.assertEquals("E-001", first.getEventId(), "First identifier");
        Assert.assertEquals("E-002", second.getEventId(), "Second identifier");
    }

    @Test
    public void createEventPersistsImmediately() throws Exception {
        Event event = createSampleEvent();
        String json = Files.readString(storePath);
        Assert.assertContains(json, "\"eventId\": \"E-001\"", "The event is written to disk");
        Assert.assertContains(json, event.getName(), "The name is written to disk");
        Assert.assertEquals(1, serviceOn(TODAY).listEvents(null).active().size(), "Reload sees it");
    }

    @Test
    public void createEventStoresAllFields() {
        Event event = service.createEvent(
                "  Java Meetup  ", "31/12/2026", "09:30", "  Hall A  ", "120");
        Assert.assertEquals("Java Meetup", event.getName(), "Name is trimmed");
        Assert.assertEquals(LocalDate.of(2026, 12, 31), event.getDate(), "Date is parsed");
        Assert.assertEquals(LocalTime.of(9, 30), event.getTime(), "Time is parsed");
        Assert.assertEquals("Hall A", event.getVenue(), "Venue is trimmed");
        Assert.assertEquals(120, event.getCapacity(), "Capacity is parsed");
        Assert.assertFalse(event.isCancelled(), "A new event is active");
        Assert.assertEquals(0, event.registeredCount(), "A new event has no registrations");
    }

    @Test
    public void editEventUpdatesEveryField() {
        Event event = createSampleEvent();
        Event updated = service.updateEvent(
                event.getEventId(), "Advanced Java", "15/01/2027", "14:45", "Hall C", "80");
        Assert.assertEquals("Advanced Java", updated.getName(), "Name updated");
        Assert.assertEquals(LocalDate.of(2027, 1, 15), updated.getDate(), "Date updated");
        Assert.assertEquals(LocalTime.of(14, 45), updated.getTime(), "Time updated");
        Assert.assertEquals("Hall C", updated.getVenue(), "Venue updated");
        Assert.assertEquals(80, updated.getCapacity(), "Capacity updated");
    }

    @Test
    public void editEventKeepsUnchangedFields() {
        Event event = createSampleEvent();
        service.updateEvent(event.getEventId(), null, null, null, "Hall D", null);
        Event reloaded = serviceOn(TODAY).getEvent(event.getEventId());
        Assert.assertEquals("Java Meetup", reloaded.getName(), "Name kept");
        Assert.assertEquals(LocalDate.of(2026, 12, 31), reloaded.getDate(), "Date kept");
        Assert.assertEquals(LocalTime.of(9, 30), reloaded.getTime(), "Time kept");
        Assert.assertEquals("Hall D", reloaded.getVenue(), "Venue changed");
        Assert.assertEquals(2, reloaded.getCapacity(), "Capacity kept");
    }

    @Test
    public void editRejectsCapacityBelowRegisteredCount() {
        Event event = createSampleEvent();
        service.registerAttendee(event.getEventId(), "Alice");
        service.registerAttendee(event.getEventId(), "Bob");
        ValidationException error = Assert.assertThrows(
                ValidationException.class,
                () -> service.updateEvent(event.getEventId(), null, null, null, null, "1"),
                "Capacity below the registered count must be rejected");
        Assert.assertContains(
                error.getMessage(), "2", "The message reports the registered count");
        Assert.assertEquals(2, service.getEvent(event.getEventId()).getCapacity(), "Capacity unchanged");
    }

    @Test
    public void failedCreationDoesNotConsumeAnIdentifier() {
        createSampleEvent();
        Assert.assertThrows(
                ValidationException.class,
                () -> service.createEvent("", "31/12/2026", "09:30", "Hall A", "10"),
                "Invalid input must be rejected");
        Event next = service.createEvent("Team Sync", "01/11/2026", "10:00", "Room B", "5");
        Assert.assertEquals("E-002", next.getEventId(), "Identifiers are not wasted");
    }

    @Test
    public void identifiersAreNotReusedAfterCancellation() {
        Event first = createSampleEvent();
        service.cancelEvent(first.getEventId());
        Event second = service.createEvent("Team Sync", "01/11/2026", "10:00", "Room B", "5");
        Assert.assertEquals("E-002", second.getEventId(), "Cancelled identifiers are never reused");
    }

    @Test
    public void listFiltersByDate() {
        createSampleEvent();
        service.createEvent("Team Sync", "01/11/2026", "10:00", "Room B", "5");
        EventListing listing = service.listEvents("01/11/2026");
        Assert.assertEquals(1, listing.active().size(), "Only one event matches the date");
        Assert.assertEquals("E-002", listing.active().get(0).getEventId(), "Matched event");
    }

    @Test
    public void listFiltersById() {
        createSampleEvent();
        service.createEvent("Team Sync", "01/11/2026", "10:00", "Room B", "5");
        Assert.assertEquals(1, service.listEvents("e-002").active().size(), "Id search is exact");
        Assert.assertEquals(0, service.listEvents("E-00").active().size(), "Partial ids do not match");
    }

    @Test
    public void listFiltersByNameCaseInsensitively() {
        createSampleEvent();
        Assert.assertEquals(1, service.listEvents("java").active().size(), "Name search is partial");
        Assert.assertEquals(0, service.listEvents("python").active().size(), "No false positives");
    }

    @Test
    public void listFiltersByVenue() {
        createSampleEvent();
        Assert.assertEquals(1, service.listEvents("hall a").active().size(), "Venue search works");
    }

    @Test
    public void listSortsByDateThenTime() {
        service.createEvent("Later", "31/12/2026", "18:00", "Hall A", "10");
        service.createEvent("Earlier", "01/06/2026", "08:00", "Hall A", "10");
        service.createEvent("Middle", "01/06/2026", "12:00", "Hall A", "10");
        EventListing listing = service.listEvents(null);
        Assert.assertEquals("E-002", listing.active().get(0).getEventId(), "Earliest first");
        Assert.assertEquals("E-003", listing.active().get(1).getEventId(), "Then by time");
        Assert.assertEquals("E-001", listing.active().get(2).getEventId(), "Latest last");
    }

    @Test
    public void pastEventCannotBeEdited() {
        Event event = createSampleEvent();
        EventService later = serviceOn(LocalDate.of(2027, 1, 1));
        Assert.assertThrows(
                EventStateException.class,
                () -> later.updateEvent(event.getEventId(), "Too late", null, null, null, null),
                "Past events are read only");
    }

    @Test
    public void undoRestoresACancelledEvent() {
        Event event = createSampleEvent();
        service.cancelEvent(event.getEventId());
        service.undoCancelEvent(event.getEventId());
        EventListing listing = service.listEvents(null);
        Assert.assertEquals(1, listing.active().size(), "The event is active again");
        Assert.assertEquals(0, listing.cancelled().size(), "Nothing remains cancelled");
    }

    @Test
    public void unknownEventIsReported() {
        NotFoundException error = Assert.assertThrows(
                NotFoundException.class,
                () -> service.getEvent("E-999"),
                "Unknown ids must fail");
        Assert.assertContains(error.getMessage(), "E-999", "The message contains the identifier");
    }
}
