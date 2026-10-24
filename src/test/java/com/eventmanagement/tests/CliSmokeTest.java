package com.eventmanagement.tests;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import com.eventmanagement.cli.ConsoleApp;
import com.eventmanagement.model.Event;
import com.eventmanagement.service.EventService;

/** Console level smoke tests: the menu drives the same service the tests use. */
public final class CliSmokeTest extends ServiceFixture {

    @Test
    public void cancellingRegistrationThroughTheMenuFreesThePlace() {
        Event event = createSampleEvent();
        service.registerAttendee(event.getEventId(), "Alice");
        String output = runConsole(String.join("\n",
                "8", "E-001", "R-0001", "y", "0"));
        Assert.assertContains(output, "Registration R-0001 cancelled", "Confirmation message");
        Assert.assertContains(output, "Remaining places: 2.", "Remaining places shown");
        Assert.assertEquals(0, event.registeredCount(), "The registration was cancelled");
    }

    @Test
    public void creatingAnEventThroughTheMenu() {
        String output = runConsole(String.join("\n",
                "1", "Java Meetup", "31/12/2026", "09:30", "Hall A", "50", "0"));
        Assert.assertContains(output, "Event created successfully.", "Success message");
        Assert.assertContains(output, "E-001", "Identifier shown");
        Assert.assertContains(output, "Goodbye", "Clean exit");
    }

    @Test
    public void invalidCapacityIsRetriedBeforeSaving() {
        String output = runConsole(String.join("\n",
                "1", "Java Meetup", "31/12/2026", "09:30", "Hall A", "0", "50", "0"));
        Assert.assertContains(
                output, "Capacity must be a whole number greater than 0", "Validation message");
        Assert.assertContains(output, "Event created successfully.", "The retry succeeds");
        Assert.assertEquals(1, service.listEvents(null).active().size(), "One event exists");
    }

    @Test
    public void listShowsScheduledAndCancelledSections() {
        Event event = createSampleEvent();
        service.cancelEvent(event.getEventId());
        String output = runConsole(String.join("\n", "2", "", "0"));
        Assert.assertContains(output, "Scheduled events", "Active section");
        Assert.assertContains(output, "Cancelled", "Cancelled section");
        Assert.assertContains(output, "E-001", "The cancelled event is listed");
    }

    @Test
    public void registeringAttendeeThroughTheMenu() {
        createSampleEvent();
        String output = runConsole(String.join("\n", "7", "E-001", "Alice", "0"));
        Assert.assertContains(output, "Registration confirmed. Unique reference: R-0001",
                "Reference shown");
        Assert.assertContains(output, "1/2", "Live count shown");
        Assert.assertContains(output, "Remaining places: 1.", "Remaining places shown");
    }

    @Test
    public void registeringForACancelledEventIsBlocked() {
        Event event = createSampleEvent();
        service.cancelEvent(event.getEventId());
        String output = runConsole(String.join("\n", "7", "E-001", "0"));
        Assert.assertContains(output, "Cancelled events cannot accept registrations", "Blocked");
        Assert.assertEquals(0, event.registeredCount(), "Nothing was registered");
    }

    @Test
    public void eventListShowsLiveRegistrationAndAttendanceCounts() {
        Event event = service.createEvent("Today", "20/05/2026", "09:00", "Hall A", "10");
        service.registerAttendee(event.getEventId(), "Alice");
        service.registerAttendee(event.getEventId(), "Bob");
        service.registerAttendee(event.getEventId(), "Carol");
        service.markAttendance(event.getEventId(), "R-0001", true);
        service.markAttendance(event.getEventId(), "R-0002", true);

        String output = runConsole(String.join("\n", "2", "", "0"));

        Assert.assertContains(output, "Registered", "Registered column");
        Assert.assertContains(output, "Attended", "Attended column");
        Assert.assertTrue(
                output.lines().anyMatch(line -> line.startsWith("E-001")
                        && line.contains("3/10") && line.contains(" 2 ")),
                "The row shows live registered and attended counts");
    }

    @Test
    public void eventSummaryListsRegisteredAndAttendedPeople() {
        Event event = service.createEvent("Today", "20/05/2026", "09:00", "Hall A", "10");
        service.registerAttendee(event.getEventId(), "Alice");
        service.registerAttendee(event.getEventId(), "Bob");
        service.markAttendance(event.getEventId(), "R-0001", true);

        String output = runConsole(String.join("\n", "10", "E-001", "0"));

        Assert.assertContains(output, "Registrations:", "Registration list");
        Assert.assertContains(output, "R-0001  Alice  [Attended]", "Attended attendee");
        Assert.assertContains(output, "R-0002  Bob  [Registered]", "Registered attendee");
        Assert.assertContains(output, "Attended  : 1 of 2", "Live attended count");
        Assert.assertContains(output, "Remaining : 8", "Live remaining places");
    }

    @Test
    public void cancelledRegistrationIsRemovedFromTheAttendanceList() {
        Event event = createSampleEvent();
        service.registerAttendee(event.getEventId(), "Alice");
        service.registerAttendee(event.getEventId(), "Bob");
        service.cancelRegistration(event.getEventId(), "R-0001", true);

        String output = runConsole(String.join("\n", "10", "E-001", "0"));

        Assert.assertNotContains(output, "R-0001", "Cancelled reference is hidden");
        Assert.assertNotContains(output, "Alice", "Cancelled attendee is hidden");
        Assert.assertContains(output, "R-0002", "Active attendee remains");
        Assert.assertContains(output, "Attended  : 0 of 1", "Only active registrations count");
    }

    @Test
    public void cancellingRegistrationCanBeAborted() {
        Event event = createSampleEvent();
        service.registerAttendee(event.getEventId(), "Alice");

        String output = runConsole(String.join("\n", "8", "E-001", "R-0001", "n", "0"));

        Assert.assertContains(output, "Cancellation aborted", "The abort is reported");
        Assert.assertEquals(1, event.registeredCount(), "The registration is unchanged");
    }

    @Test
    public void attendanceIsTrackedThroughTheMenu() {
        Event event = service.createEvent("Today", "20/05/2026", "09:00", "Hall A", "10");
        service.registerAttendee(event.getEventId(), "Alice");

        String output = runConsole(String.join("\n", "9", "E-001", "R-0001", "a", "0"));

        Assert.assertContains(output, "marked as attended", "Attendance confirmation");
        Assert.assertContains(
                output,
                "Registered: 1, Attended: 1 (live counts).",
                "Live counts are shown");
        Assert.assertTrue(
                event.activeRegistrations().get(0).isAttended(),
                "The registration is marked");
    }

    @Test
    public void onlyRegisteredAttendeesCanBeMarkedThroughTheMenu() {
        Event event = service.createEvent("Today", "20/05/2026", "09:00", "Hall A", "10");
        service.registerAttendee(event.getEventId(), "Alice");

        String output = runConsole(String.join("\n", "9", "E-001", "R-9999", "0"));

        Assert.assertContains(
                output,
                "Only registered attendees can be marked as attended",
                "Unknown references are rejected");
        Assert.assertEquals(0, event.attendedCount(), "Nothing was marked");
    }

    @Test
    public void reportingThroughTheMenuWritesAFile() throws Exception {
        Event event = createSampleEvent();
        service.registerAttendee(event.getEventId(), "Alice");
        Path target = TempFiles.newReportPath("cli-report.txt");
        String output = runConsole(String.join("\n",
                "11", "E-001", "y", target.toString(), "0"));
        Assert.assertContains(output, "Event Report", "Report preview");
        Assert.assertContains(output, "Report exported to", "Export confirmation");
        Assert.assertTrue(Files.exists(target), "The file was written");
    }

    @Test
    public void unknownMenuOptionIsReported() {
        String output = runConsole("99\n0");
        Assert.assertContains(output, "Unknown option", "Unknown options are handled");
    }

    @Test
    public void unknownEventIdIsReported() {
        createSampleEvent();
        String output = runConsole(String.join("\n", "7", "E-404", "0"));
        Assert.assertContains(output, "Event E-404 not found", "Missing events are reported");
    }

    @Test
    public void emptyListShowsNoEvents() {
        String output = runConsole(String.join("\n", "2", "0"));
        Assert.assertContains(output, "No events", "Empty state is shown");
    }

    @Test
    public void pastDateIsRetriedBeforeSaving() {
        String output = runConsole(String.join("\n",
                "1", "Java Meetup", "01/01/2020", "31/12/2026", "09:30", "Hall A", "50", "0"));
        Assert.assertContains(output, "Event not saved", "The past date is rejected");
        Assert.assertContains(output, "Event created successfully.", "The retry succeeds");
        Assert.assertEquals(1, service.listEvents(null).active().size(), "One event exists");
    }

    @Test
    public void filterWithNoMatchReportsNoResults() {
        createSampleEvent();
        String output = runConsole(String.join("\n", "2", "zzz", "0"));
        Assert.assertContains(output, "No matching events found", "No results are shown");
    }

    @Test
    public void capacityBelowRegisteredCountIsRejectedThroughTheMenu() {
        Event event = createSampleEvent();
        service.registerAttendee(event.getEventId(), "Alice");
        service.registerAttendee(event.getEventId(), "Bob");
        String output = runConsole(String.join("\n", "6", "E-001", "1", "0"));
        Assert.assertContains(output, "Capacity not updated", "The update is rejected");
        Assert.assertEquals(2, event.getCapacity(), "Capacity is unchanged");
        Assert.assertEquals(2, event.registeredCount(), "Registrations are unchanged");
    }

    @Test
    public void fullEventBlocksFurtherRegistrationsThroughTheMenu() {
        Event event = createSampleEvent();
        service.registerAttendee(event.getEventId(), "Alice");
        service.registerAttendee(event.getEventId(), "Bob");
        String output = runConsole(String.join("\n", "7", "E-001", "0"));
        Assert.assertContains(output, "Event capacity has been reached", "Full event blocked");
        Assert.assertEquals(2, event.registeredCount(), "Count stays at capacity");
    }

    @Test
    public void passedEventCannotBeEditedThroughTheMenu() {
        createSampleEvent();
        EventService future = serviceOn(LocalDate.of(2027, 1, 1));
        String output = runConsole(future, String.join("\n", "3", "E-001", "0"));
        Assert.assertContains(
                output,
                "This event has passed and can no longer be edited",
                "Passed events are blocked");
    }

    @Test
    public void editKeepsCurrentValuesWhenBlank() {
        createSampleEvent();
        String output = runConsole(String.join("\n",
                "3", "E-001", "", "", "", "", "", "0"));
        Assert.assertContains(output, "Event updated successfully.", "The update succeeds");
        Assert.assertEquals("Java Meetup", service.getEvent("E-001").getName(),
                "The name is unchanged");
    }

    private String runConsole(String input) {
        return runConsole(service, input);
    }

    private String runConsole(EventService target, String input) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        ConsoleApp app = new ConsoleApp(
                target,
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                out);
        int exitCode = app.run();
        Assert.assertEquals(0, exitCode, "The console exits cleanly");
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
