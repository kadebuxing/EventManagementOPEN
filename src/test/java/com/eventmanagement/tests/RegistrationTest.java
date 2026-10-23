package com.eventmanagement.tests;

import java.time.LocalDate;

import com.eventmanagement.model.Event;
import com.eventmanagement.model.Registration;
import com.eventmanagement.service.ConfirmationRequiredException;
import com.eventmanagement.service.EventService;
import com.eventmanagement.service.EventStateException;
import com.eventmanagement.service.NotFoundException;
import com.eventmanagement.service.ValidationException;

/** OPEN-3, OPEN-4, OPEN-5 and OPEN-6: registrations, capacity and attendance. */
public final class RegistrationTest extends ServiceFixture {

    @Test
    public void attendanceCanBeMarkedAfterTheEventDate() {
        Event event = service.createEvent("Today", "20/05/2026", "09:00", "Hall A", "10");
        Registration registration = service.registerAttendee(event.getEventId(), "Alice");

        EventService later = serviceOn(TODAY.plusDays(5));
        later.markAttendance(event.getEventId(), registration.getReference(), true);

        Assert.assertEquals(1, later.getEvent(event.getEventId()).attendedCount(), "Attendance recorded");
    }

    @Test
    public void attendanceCanBeMarkedOnTheEventDate() {
        Event event = service.createEvent("Today", "20/05/2026", "09:00", "Hall A", "10");
        Registration registration = service.registerAttendee(event.getEventId(), "Alice");
        service.markAttendance(event.getEventId(), registration.getReference(), true);
        Assert.assertTrue(registration.isAttended(), "Same day attendance is allowed");
    }

    @Test
    public void attendanceCannotBeMarkedBeforeTheEventDate() {
        Event event = createSampleEvent();
        Registration registration = service.registerAttendee(event.getEventId(), "Alice");
        EventStateException error = Assert.assertThrows(
                EventStateException.class,
                () -> service.markAttendance(event.getEventId(), registration.getReference(), true),
                "Future events cannot track attendance yet");
        Assert.assertContains(error.getMessage(), "on or after the event date", "Explains the rule");
    }

    @Test
    public void attendanceCanBeToggledBackToNotAttended() {
        Event event = service.createEvent("Today", "20/05/2026", "09:00", "Hall A", "10");
        Registration registration = service.registerAttendee(event.getEventId(), "Alice");
        service.markAttendance(event.getEventId(), registration.getReference(), true);
        service.markAttendance(event.getEventId(), registration.getReference(), false);
        Assert.assertFalse(registration.isAttended(), "Attendance can be corrected");
        Assert.assertEquals(0, event.attendedCount(), "The counter follows the flag");
    }

    @Test
    public void attendanceIsOnlyForRegisteredAttendees() {
        Event event = service.createEvent("Today", "20/05/2026", "09:00", "Hall A", "10");
        Assert.assertThrows(
                NotFoundException.class,
                () -> service.markAttendance(event.getEventId(), "R-9999", true),
                "Unknown references are rejected");
    }

    @Test
    public void cancelledEventCannotAcceptRegistrations() {
        Event event = createSampleEvent();
        service.cancelEvent(event.getEventId());
        EventStateException error = Assert.assertThrows(
                EventStateException.class,
                () -> service.registerAttendee(event.getEventId(), "Alice"),
                "Cancelled events are closed");
        Assert.assertContains(error.getMessage(), "Cancelled", "Explains the rule");
    }

    @Test
    public void cancelledEventCannotTrackAttendance() {
        Event event = service.createEvent("Today", "20/05/2026", "09:00", "Hall A", "10");
        Registration registration = service.registerAttendee(event.getEventId(), "Alice");
        service.cancelEvent(event.getEventId());
        Assert.assertThrows(
                EventStateException.class,
                () -> service.markAttendance(event.getEventId(), registration.getReference(), true),
                "Cancelled events are closed");
    }

    @Test
    public void cancelledRegistrationClearsAttendance() {
        Event event = service.createEvent("Today", "20/05/2026", "09:00", "Hall A", "10");
        Registration registration = service.registerAttendee(event.getEventId(), "Alice");
        service.markAttendance(event.getEventId(), registration.getReference(), true);
        service.cancelRegistration(event.getEventId(), registration.getReference(), true);
        Assert.assertFalse(registration.isAttended(), "A cancelled registration cannot be attended");
        Assert.assertEquals(0, event.attendedCount(), "The attended count drops");
    }

    @Test
    public void cancelledRegistrationFreesAPlace() {
        Event event = createSampleEvent();
        Registration first = service.registerAttendee(event.getEventId(), "Alice");
        service.registerAttendee(event.getEventId(), "Bob");
        Assert.assertTrue(event.isFull(), "The event is full");

        service.cancelRegistration(event.getEventId(), first.getReference(), true);

        Assert.assertEquals(1, event.registeredCount(), "One active registration remains");
        Assert.assertEquals(1, event.availablePlaces(), "The place is available again");
        Registration replacement = service.registerAttendee(event.getEventId(), "Carol");
        Assert.assertEquals("R-0003", replacement.getReference(), "The freed place can be used");
    }

    @Test
    public void cancelledRegistrationCannotBeCancelledAgain() {
        Event event = createSampleEvent();
        Registration registration = service.registerAttendee(event.getEventId(), "Alice");
        service.cancelRegistration(event.getEventId(), registration.getReference(), true);
        Assert.assertThrows(
                NotFoundException.class,
                () -> service.cancelRegistration(
                        event.getEventId(), registration.getReference(), true),
                "An already cancelled registration is not active");
    }

    @Test
    public void cancellingRegistrationRequiresConfirmation() {
        Event event = createSampleEvent();
        Registration registration = service.registerAttendee(event.getEventId(), "Alice");
        ConfirmationRequiredException error = Assert.assertThrows(
                ConfirmationRequiredException.class,
                () -> service.cancelRegistration(
                        event.getEventId(), registration.getReference(), false),
                "Unconfirmed cancellations must be refused");
        Assert.assertContains(error.getMessage(), "confirmed", "Explains the rule");
        Assert.assertEquals(1, event.registeredCount(), "The registration is untouched");
    }

    @Test
    public void cancellingUnknownRegistrationIsReported() {
        Event event = createSampleEvent();
        NotFoundException error = Assert.assertThrows(
                NotFoundException.class,
                () -> service.cancelRegistration(event.getEventId(), "R-0404", true),
                "Unknown references are rejected");
        Assert.assertContains(error.getMessage(), "R-0404", "The message contains the reference");
    }

    @Test
    public void capacityLimitsRegistrations() {
        Event event = createSampleEvent();
        service.registerAttendee(event.getEventId(), "Alice");
        service.registerAttendee(event.getEventId(), "Bob");
        EventStateException error = Assert.assertThrows(
                EventStateException.class,
                () -> service.registerAttendee(event.getEventId(), "Carol"),
                "A full event blocks further registrations");
        Assert.assertContains(error.getMessage(), "capacity has been reached", "Explains the rule");
        Assert.assertEquals(2, event.registeredCount(), "The count stays at the capacity");
    }

    @Test
    public void capacityCanBeLoweredToTheRegisteredCount() {
        Event event = createSampleEvent();
        service.registerAttendee(event.getEventId(), "Alice");
        service.setCapacity(event.getEventId(), "1");
        Assert.assertEquals(1, event.getCapacity(), "Capacity matches the registrations");
        Assert.assertTrue(event.isFull(), "The event is full");
    }

    @Test
    public void capacityCanBeRaisedAgain() {
        Event event = createSampleEvent();
        service.registerAttendee(event.getEventId(), "Alice");
        service.registerAttendee(event.getEventId(), "Bob");
        service.setCapacity(event.getEventId(), "5");
        Assert.assertEquals(3, event.availablePlaces(), "Seats become available");
        service.registerAttendee(event.getEventId(), "Carol");
        Assert.assertEquals(3, event.registeredCount(), "Registration succeeds again");
    }

    @Test
    public void capacityMustBePositiveWhenUpdated() {
        Event event = createSampleEvent();
        Assert.assertThrows(
                ValidationException.class,
                () -> service.setCapacity(event.getEventId(), "0"),
                "Zero capacity must be rejected");
        Assert.assertEquals(2, event.getCapacity(), "The capacity is unchanged");
    }

    @Test
    public void duplicateAttendeeNamesAreAllowed() {
        Event event = createSampleEvent();
        Registration first = service.registerAttendee(event.getEventId(), "Alice");
        Registration second = service.registerAttendee(event.getEventId(), "Alice");
        Assert.assertFalse(
                first.getReference().equals(second.getReference()),
                "Each registration gets its own reference");
        Assert.assertEquals(2, event.registeredCount(), "Both registrations count");
    }

    @Test
    public void failedRegistrationDoesNotConsumeAReference() {
        Event event = service.createEvent("Small", "31/12/2026", "09:30", "Hall A", "1");
        service.registerAttendee(event.getEventId(), "Alice");
        Assert.assertThrows(
                EventStateException.class,
                () -> service.registerAttendee(event.getEventId(), "Bob"),
                "The event is full");
        service.cancelRegistration(event.getEventId(), "R-0001", true);
        Registration next = service.registerAttendee(event.getEventId(), "Carol");
        Assert.assertEquals("R-0002", next.getReference(), "References are not wasted");
    }

    @Test
    public void registrationNamesAreValidated() {
        Event event = createSampleEvent();
        Assert.assertThrows(
                ValidationException.class,
                () -> service.registerAttendee(event.getEventId(), "   "),
                "A blank attendee name must be rejected");
        Assert.assertEquals(0, event.registeredCount(), "Nothing was registered");
    }

    @Test
    public void registrationReferencesAreSequentialAcrossEvents() {
        Event first = createSampleEvent();
        Event second = service.createEvent("Team Sync", "01/11/2026", "10:00", "Room B", "5");
        Assert.assertEquals(
                "R-0001",
                service.registerAttendee(first.getEventId(), "Alice").getReference(),
                "First reference");
        Assert.assertEquals(
                "R-0002",
                service.registerAttendee(second.getEventId(), "Bob").getReference(),
                "References are unique across events");
    }

    @Test
    public void registrationsArePersisted() {
        Event event = createSampleEvent();
        Registration registration = service.registerAttendee(event.getEventId(), "Alice");
        service.cancelRegistration(event.getEventId(), registration.getReference(), true);
        service.registerAttendee(event.getEventId(), "Bob");

        Event reloaded = serviceOn(TODAY).getEvent(event.getEventId());
        Assert.assertEquals(1, reloaded.registeredCount(), "One active registration after reload");
        Assert.assertEquals(2, reloaded.getRegistrations().size(), "The cancelled entry is kept");
    }

    @Test
    public void registrationsUseTheStoredCounterAfterReload() {
        Event event = createSampleEvent();
        service.registerAttendee(event.getEventId(), "Alice");
        EventService reloaded = serviceOn(LocalDate.of(2026, 5, 21));
        Registration next = reloaded.registerAttendee(event.getEventId(), "Bob");
        Assert.assertEquals("R-0002", next.getReference(), "The counter survives a restart");
    }
}
