package com.eventmanagement.tests;

import java.nio.file.Files;
import java.nio.file.Path;

import com.eventmanagement.model.Event;
import com.eventmanagement.model.Registration;
import com.eventmanagement.report.EventReport;

/** OPEN-7: attendance reports and report export. */
public final class ReportTest extends ServiceFixture {

    @Test
    public void cancelledRegistrationsAreExcludedFromTheReport() {
        Event event = createSampleEvent();
        Registration alice = service.registerAttendee(event.getEventId(), "Alice");
        service.registerAttendee(event.getEventId(), "Bob");
        service.cancelRegistration(event.getEventId(), alice.getReference(), true);

        EventReport report = service.generateReport(event.getEventId());
        Assert.assertEquals(1, report.registered(), "Only active registrations count");
        Assert.assertEquals(1, report.attendees().size(), "Only active attendees are listed");
        Assert.assertEquals("Bob", report.attendees().get(0).name(), "Bob remains");
    }

    @Test
    public void reportContainsTheAttendanceNumbers() {
        Event event = service.createEvent("Today", "20/05/2026", "09:00", "Hall A", "10");
        Registration alice = service.registerAttendee(event.getEventId(), "Alice");
        Registration bob = service.registerAttendee(event.getEventId(), "Bob");
        service.markAttendance(event.getEventId(), alice.getReference(), true);
        service.markAttendance(event.getEventId(), bob.getReference(), false);

        EventReport report = service.generateReport(event.getEventId());
        Assert.assertEquals(2, report.registered(), "Registered count");
        Assert.assertEquals(1, report.attended(), "Attended count");
        Assert.assertEquals(10, report.capacity(), "Capacity is reported");
        Assert.assertEquals(8, report.availablePlaces(), "Available places are reported");
        Assert.assertTrue(report.attendees().get(0).attended(), "Alice attended");
        Assert.assertFalse(report.attendees().get(1).attended(), "Bob did not attend");
    }

    @Test
    public void reportForACancelledEventKeepsTheStatus() {
        Event event = createSampleEvent();
        service.cancelEvent(event.getEventId());
        EventReport report = service.generateReport(event.getEventId());
        Assert.assertEquals("Cancelled", report.status().displayName(), "Status is preserved");
        String text = report.toText();
        Assert.assertContains(text, "Cancelled", "The text shows the status");
    }

    @Test
    public void reportTextListsEveryActiveAttendee() {
        Event event = createSampleEvent();
        service.registerAttendee(event.getEventId(), "Alice");
        service.registerAttendee(event.getEventId(), "Bob");
        String text = service.generateReport(event.getEventId()).toText();
        Assert.assertContains(text, "R-0001", "First reference");
        Assert.assertContains(text, "Alice", "First attendee");
        Assert.assertContains(text, "R-0002", "Second reference");
        Assert.assertContains(text, "Bob", "Second attendee");
        Assert.assertContains(text, "Registered", "Registered line");
        Assert.assertContains(text, "2", "Registered count");
    }

    @Test
    public void reportTextHandlesAnEmptyEvent() {
        Event event = createSampleEvent();
        String text = service.generateReport(event.getEventId()).toText();
        Assert.assertContains(text, "Registered      : 0", "Zero registrations");
        Assert.assertContains(text, "No registered attendees.", "Empty state is explicit");
    }

    @Test
    public void exportWritesTheReportToDisk() throws Exception {
        Event event = createSampleEvent();
        service.registerAttendee(event.getEventId(), "Alice");
        Path target = TempFiles.newReportPath("E-001_report.txt");

        Path exported = service.exportReport(event.getEventId(), target);

        Assert.assertTrue(Files.exists(exported), "The report file exists");
        String text = Files.readString(exported);
        Assert.assertContains(text, "Event Report", "Report header");
        Assert.assertContains(text, "Java Meetup", "Event name");
        Assert.assertContains(text, "Alice", "Attendee");
    }

    @Test
    public void exportCreatesMissingDirectories() throws Exception {
        Event event = createSampleEvent();
        Path target = TempFiles.newReportPath("nested").resolve("reports").resolve("report.txt");
        service.exportReport(event.getEventId(), target);
        Assert.assertTrue(Files.exists(target), "Directories are created on demand");
    }

    @Test
    public void exportDoesNotModifyStoredData() throws Exception {
        Event event = createSampleEvent();
        service.registerAttendee(event.getEventId(), "Alice");
        String before = Files.readString(storePath);
        service.exportReport(event.getEventId(), TempFiles.newReportPath("report.txt"));
        String after = Files.readString(storePath);
        Assert.assertEquals(before, after, "Exporting is read only");
    }

    @Test
    public void reportSnapshotDoesNotChangeAfterLaterEdits() {
        Event event = createSampleEvent();
        service.registerAttendee(event.getEventId(), "Alice");
        EventReport report = service.generateReport(event.getEventId());
        service.registerAttendee(event.getEventId(), "Bob");
        Assert.assertEquals(1, report.registered(), "The report is an immutable snapshot");
        Assert.assertEquals(1, report.attendees().size(), "The attendee list is copied");
    }
}
