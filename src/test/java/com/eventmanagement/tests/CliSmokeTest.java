package com.eventmanagement.tests;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.eventmanagement.cli.ConsoleApp;
import com.eventmanagement.model.Event;

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

    private String runConsole(String input) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        ConsoleApp app = new ConsoleApp(
                service,
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                out);
        int exitCode = app.run();
        Assert.assertEquals(0, exitCode, "The console exits cleanly");
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
