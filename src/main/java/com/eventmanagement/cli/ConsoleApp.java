package com.eventmanagement.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import com.eventmanagement.model.Event;
import com.eventmanagement.model.Registration;
import com.eventmanagement.report.EventReport;
import com.eventmanagement.report.ReportExporter;
import com.eventmanagement.service.EventListing;
import com.eventmanagement.service.EventService;
import com.eventmanagement.service.EventServiceException;
import com.eventmanagement.service.NotFoundException;
import com.eventmanagement.service.ValidationException;
import com.eventmanagement.storage.StorageException;
import com.eventmanagement.util.Formats;
import com.eventmanagement.util.Validators;

/** Interactive console front end for the Event Management system. */
public final class ConsoleApp {
    private static final String MENU = """
            Event Management and Staffing System
            ====================================
             1. Create event
             2. List / search events
             3. Edit event
             4. Cancel event
             5. Undo event cancellation
             6. Update event capacity
             7. Register attendee
             8. Cancel registration
             9. Track attendance
            10. Event summary
            11. Generate / export report
             0. Exit""";

    private final EventService service;
    private final BufferedReader reader;
    private final PrintStream out;

    public ConsoleApp(EventService service, InputStream input, PrintStream out) {
        this.service = service;
        this.reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        this.out = out;
    }

    public int run() {
        out.println("Event Management and Staffing System");
        while (true) {
            out.println(MENU);
            String choice = ask("Select an option: ");
            if (choice == null) {
                out.println();
                out.println("Input closed; exiting.");
                return 0;
            }
            if (choice.equals("0")
                    || choice.equalsIgnoreCase("q")
                    || choice.equalsIgnoreCase("quit")
                    || choice.equalsIgnoreCase("exit")) {
                out.println("Goodbye. All changes are already saved.");
                return 0;
            }
            try {
                switch (choice) {
                    case "1" -> createEvent();
                    case "2" -> listEvents();
                    case "3" -> editEvent();
                    case "4" -> cancelEvent();
                    case "5" -> undoCancelEvent();
                    case "6" -> updateCapacity();
                    case "7" -> registerAttendee();
                    case "8" -> cancelRegistration();
                    case "9" -> trackAttendance();
                    case "10" -> showEventSummary();
                    case "11" -> report();
                    default -> out.println("Unknown option; please choose a number between 0 and 11.");
                }
            } catch (ValidationException | EventServiceException ex) {
                out.println("Action failed: " + ex.getMessage());
            } catch (StorageException ex) {
                out.println("Storage error: " + ex.getMessage());
            } catch (ReportExporter.ReportException ex) {
                out.println("Report error: " + ex.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------
    // Input helpers
    // ------------------------------------------------------------------
    private String ask(String prompt) {
        out.print(prompt);
        out.flush();
        try {
            String line = reader.readLine();
            return line == null ? null : line.trim();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read input: " + ex.getMessage(), ex);
        }
    }

    private String askUntilValid(String prompt, Consumer<String> check, String retryNote) {
        while (true) {
            String raw = ask(prompt);
            if (raw == null || raw.equalsIgnoreCase("cancel")) {
                return null;
            }
            try {
                check.accept(raw);
                return raw;
            } catch (ValidationException ex) {
                out.println("  " + ex.getMessage());
                out.println("  " + retryNote);
            }
        }
    }

    private Prompt askOptional(String label, String current, Consumer<String> check) {
        while (true) {
            String raw = ask(label + " [" + current + "]: ");
            if (raw == null || raw.isEmpty()) {
                return new Prompt(null, false);
            }
            if (raw.equalsIgnoreCase("cancel")) {
                return new Prompt(null, true);
            }
            try {
                check.accept(raw);
                return new Prompt(raw, false);
            } catch (ValidationException ex) {
                out.println("  " + ex.getMessage());
            }
        }
    }

    private Optional<Event> selectEvent(String title) {
        out.println();
        out.println("-- " + title + " --");
        if (!service.hasEvents()) {
            out.println("No events");
            return Optional.empty();
        }
        String eventId = ask("Event ID (blank to cancel): ");
        if (eventId == null || eventId.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(service.getEvent(eventId));
        } catch (NotFoundException ex) {
            out.println("  " + ex.getMessage());
            return Optional.empty();
        }
    }

    private static boolean isYes(String answer) {
        return answer != null
                && (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes"));
    }

    private void abortAction(String action) {
        out.println(action + " cancelled; nothing was saved.");
    }

    // ------------------------------------------------------------------
    // Output helpers
    // ------------------------------------------------------------------
    private void printEventDetail(Event event) {
        out.println();
        out.println("Event " + event.getEventId());
        out.printf(Locale.ROOT, "  Name      : %s%n", event.getName());
        out.printf(
                Locale.ROOT,
                "  Date      : %s %s%n",
                Formats.date(event.getDate()),
                Formats.time(event.getTime()));
        out.printf(Locale.ROOT, "  Venue     : %s%n", event.getVenue());
        out.printf(Locale.ROOT, "  Capacity  : %d%n", event.getCapacity());
        out.printf(Locale.ROOT, "  Status    : %s%n", event.getStatus().displayName());
        out.printf(
                Locale.ROOT,
                "  Registered: %d/%d%n",
                event.registeredCount(),
                event.getCapacity());
        out.printf(Locale.ROOT, "  Attended  : %d%n", event.attendedCount());
    }

    private void printRegistrations(Event event) {
        List<Registration> registrations = event.activeRegistrations();
        if (registrations.isEmpty()) {
            out.println("  No registered attendees yet.");
            return;
        }
        out.println("  Registrations:");
        int index = 1;
        for (Registration registration : registrations) {
            String state = registration.isAttended() ? "Attended" : "Registered";
            out.printf(
                    Locale.ROOT,
                    "    %d. %s  %s  [%s]%n",
                    index++,
                    registration.getReference(),
                    registration.getAttendeeName(),
                    state);
        }
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------
    private void createEvent() {
        out.println();
        out.println("-- Create event (OPEN-1) --");
        out.println("Type 'cancel' at any prompt to abort.");
        String retry = "Event not saved. Please re-enter the details.";

        String name = askUntilValid("Event name: ", Validators::validateEventName, retry);
        if (name == null) {
            abortAction("Event creation");
            return;
        }
        String dateText = askUntilValid(
                "Event date (DD/MM/YYYY): ",
                value -> Validators.parseEventDate(value, service.today()),
                retry);
        if (dateText == null) {
            abortAction("Event creation");
            return;
        }
        String timeText = askUntilValid("Event time (HH:MM): ", Validators::parseEventTime, retry);
        if (timeText == null) {
            abortAction("Event creation");
            return;
        }
        String venue = askUntilValid("Venue: ", Validators::validateVenue, retry);
        if (venue == null) {
            abortAction("Event creation");
            return;
        }
        String capacity = askUntilValid("Capacity: ", Validators::parseCapacity, retry);
        if (capacity == null) {
            abortAction("Event creation");
            return;
        }

        Event event = service.createEvent(name, dateText, timeText, venue, capacity);
        out.println();
        out.println("Event created successfully.");
        printEventDetail(event);
    }

    private void listEvents() {
        out.println();
        out.println("-- List / search events (OPEN-2) --");
        if (!service.hasEvents()) {
            out.println("No events");
            return;
        }
        String query = ask("Filter by name, ID, date (DD/MM/YYYY) or venue, blank for all: ");
        EventListing listing = service.listEvents(query == null || query.isEmpty() ? null : query);
        if (listing.isEmpty()) {
            out.println("No matching events found");
            return;
        }

        out.println();
        out.println("Scheduled events");
        out.println(listing.active().isEmpty()
                ? "  No active events match."
                : Formats.eventTable(listing.active()));
        out.println();
        out.println("Cancelled");
        out.println(listing.cancelled().isEmpty()
                ? "  No cancelled events match."
                : Formats.eventTable(listing.cancelled()));
    }

    private void editEvent() {
        Optional<Event> selected = selectEvent("Edit event (OPEN-10)");
        if (selected.isEmpty()) {
            return;
        }
        Event event = selected.get();
        printEventDetail(event);
        if (event.isCancelled()) {
            out.println();
            out.println("This event is cancelled and cannot be edited; undo the cancellation first.");
            return;
        }
        if (event.isPast(service.today())) {
            out.println();
            out.println("This event has passed and can no longer be edited.");
            return;
        }

        out.println();
        out.println("Press Enter to keep the current value, or type 'cancel' to abort.");
        Prompt name = askOptional("Event name", event.getName(), Validators::validateEventName);
        if (name.aborted()) {
            abortAction("Edit");
            return;
        }
        Prompt dateText = askOptional(
                "Event date (DD/MM/YYYY)",
                Formats.date(event.getDate()),
                value -> Validators.parseEventDate(value, service.today()));
        if (dateText.aborted()) {
            abortAction("Edit");
            return;
        }
        Prompt timeText = askOptional(
                "Event time (HH:MM)", Formats.time(event.getTime()), Validators::parseEventTime);
        if (timeText.aborted()) {
            abortAction("Edit");
            return;
        }
        Prompt venue = askOptional("Venue", event.getVenue(), Validators::validateVenue);
        if (venue.aborted()) {
            abortAction("Edit");
            return;
        }
        Prompt capacity = askOptional(
                "Capacity", String.valueOf(event.getCapacity()), Validators::parseCapacity);
        if (capacity.aborted()) {
            abortAction("Edit");
            return;
        }

        Event updated = service.updateEvent(
                event.getEventId(),
                name.value(),
                dateText.value(),
                timeText.value(),
                venue.value(),
                capacity.value());
        out.println();
        out.println("Event updated successfully.");
        printEventDetail(updated);
    }

    private void cancelEvent() {
        Optional<Event> selected = selectEvent("Cancel event (OPEN-8)");
        if (selected.isEmpty()) {
            return;
        }
        Event event = selected.get();
        printEventDetail(event);
        if (event.isCancelled()) {
            out.println();
            out.println("Event is already cancelled.");
            return;
        }
        String answer = ask("Cancel " + event.getEventId() + " (" + event.getName()
                + ")? Registrations will stop. (y/N): ");
        if (!isYes(answer)) {
            out.println("Cancellation aborted; the event is unchanged.");
            return;
        }
        service.cancelEvent(event.getEventId());
        out.println();
        out.println("Event cancelled. It is now listed in the Cancelled section and "
                + "no longer accepts registrations.");
        out.println("Use option 5 if you need to undo the cancellation.");
    }

    private void undoCancelEvent() {
        Optional<Event> selected = selectEvent("Undo event cancellation (OPEN-8)");
        if (selected.isEmpty()) {
            return;
        }
        Event event = selected.get();
        if (!event.isCancelled()) {
            out.println();
            out.println("Event is not cancelled, so there is nothing to undo.");
            return;
        }
        service.undoCancelEvent(event.getEventId());
        out.println();
        out.println("Cancellation undone. " + event.getEventId() + " is active again.");
        printEventDetail(event);
    }

    private void updateCapacity() {
        Optional<Event> selected = selectEvent("Update event capacity (OPEN-4)");
        if (selected.isEmpty()) {
            return;
        }
        Event event = selected.get();
        out.printf(
                Locale.ROOT,
                "%nCurrent capacity: %d; registered attendees: %d%n",
                event.getCapacity(),
                event.registeredCount());
        String capacity = askUntilValid(
                "New capacity: ",
                Validators::parseCapacity,
                "Capacity not updated. Please re-enter a valid number.");
        if (capacity == null) {
            abortAction("Capacity update");
            return;
        }
        Event updated;
        try {
            updated = service.setCapacity(event.getEventId(), capacity);
        } catch (ValidationException ex) {
            out.println();
            out.println("Capacity not updated: " + ex.getMessage());
            return;
        }
        out.println();
        out.println("Capacity updated to " + updated.getCapacity() + ".");
        printEventDetail(updated);
    }

    private void registerAttendee() {
        Optional<Event> selected = selectEvent("Register attendee (OPEN-3)");
        if (selected.isEmpty()) {
            return;
        }
        Event event = selected.get();
        printEventDetail(event);
        if (event.isCancelled()) {
            out.println();
            out.println("Cancelled events cannot accept registrations.");
            return;
        }
        if (event.isFull()) {
            out.println();
            out.println("Event capacity has been reached (" + event.getCapacity()
                    + "); further registrations are blocked.");
            return;
        }
        String name = askUntilValid(
                "Attendee name: ",
                Validators::validateAttendeeName,
                "Registration not saved. Please re-enter a valid name.");
        if (name == null) {
            abortAction("Registration");
            return;
        }
        Registration registration = service.registerAttendee(event.getEventId(), name);
        out.println();
        out.println("Registration confirmed. Unique reference: " + registration.getReference());
        out.printf(
                Locale.ROOT,
                "Registered attendees: %d/%d%n",
                event.registeredCount(),
                event.getCapacity());
        out.println("Remaining places: " + event.availablePlaces() + ".");
        if (event.isFull()) {
            out.println("Event is now full; further registrations are blocked.");
        }
    }

    private void cancelRegistration() {
        Optional<Event> selected = selectEvent("Cancel registration (OPEN-6)");
        if (selected.isEmpty()) {
            return;
        }
        Event event = selected.get();
        printEventDetail(event);
        printRegistrations(event);
        if (event.activeRegistrations().isEmpty()) {
            return;
        }
        String reference = ask("Registration reference to cancel: ");
        if (reference == null || reference.isEmpty()) {
            return;
        }
        Optional<Registration> found = event.findActiveRegistration(reference);
        if (found.isEmpty()) {
            out.println();
            out.println("Registration " + reference + " not found for " + event.getEventId() + ".");
            return;
        }
        Registration registration = found.get();
        String answer = ask("Cancel the registration for " + registration.getAttendeeName()
                + " (" + registration.getReference() + ")? (y/N): ");
        if (!isYes(answer)) {
            out.println("Cancellation aborted; the registration is unchanged.");
            return;
        }
        service.cancelRegistration(event.getEventId(), registration.getReference(), true);
        out.println();
        out.printf(
                Locale.ROOT,
                "Registration %s cancelled. Registered attendees: %d/%d; "
                        + "the place is now available.%n",
                registration.getReference(),
                event.registeredCount(),
                event.getCapacity());
        out.println("Remaining places: " + event.availablePlaces() + ".");
    }

    private void trackAttendance() {
        Optional<Event> selected = selectEvent("Track attendance (OPEN-5)");
        if (selected.isEmpty()) {
            return;
        }
        Event event = selected.get();
        printEventDetail(event);
        if (event.isCancelled()) {
            out.println();
            out.println("Cancelled events cannot track attendance.");
            return;
        }
        printRegistrations(event);
        if (event.activeRegistrations().isEmpty()) {
            return;
        }
        String reference = ask("Registration reference: ");
        if (reference == null || reference.isEmpty()) {
            return;
        }
        String state = ask("Mark as attended (a) or not attended (n)? [a]: ");
        boolean attended = !(state != null
                && (state.equalsIgnoreCase("n") || state.equalsIgnoreCase("no")));
        Registration registration = service.markAttendance(
                event.getEventId(), reference, attended);
        out.println();
        out.printf(
                Locale.ROOT,
                "%s (%s) marked as %s.%n",
                registration.getReference(),
                registration.getAttendeeName(),
                attended ? "attended" : "not attended");
        out.printf(
                Locale.ROOT,
                "Registered: %d, Attended: %d (live counts).%n",
                event.registeredCount(),
                event.attendedCount());
    }

    private void showEventSummary() {
        Optional<Event> selected = selectEvent("Event summary");
        if (selected.isEmpty()) {
            return;
        }
        Event event = selected.get();
        printEventDetail(event);
        printRegistrations(event);
    }

    private void report() {
        Optional<Event> selected = selectEvent("Generate / export report (OPEN-7)");
        if (selected.isEmpty()) {
            return;
        }
        Event event = selected.get();
        EventReport report = service.generateReport(event.getEventId());
        out.println();
        out.print(report.toText());
        out.println();
        String answer = ask("Export this report to a file? (y/N): ");
        if (!isYes(answer)) {
            return;
        }
        Path defaultPath = Path.of("reports", event.getEventId() + "_report.txt");
        String rawPath = ask("File path [" + defaultPath + "]: ");
        Path target;
        try {
            target = rawPath == null || rawPath.isEmpty() ? defaultPath : Path.of(rawPath);
        } catch (InvalidPathException ex) {
            out.println("Invalid file path: " + ex.getMessage());
            return;
        }
        Path exported = service.exportReport(event.getEventId(), target);
        out.println("Report exported to " + exported);
    }

    private record Prompt(String value, boolean aborted) {
    }
}
