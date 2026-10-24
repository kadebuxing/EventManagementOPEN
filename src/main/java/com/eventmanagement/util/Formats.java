package com.eventmanagement.util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.eventmanagement.model.Event;

/** Presentation helpers shared by the console UI and the report renderer. */
public final class Formats {
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT);
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private Formats() {
    }

    public static String date(LocalDate value) {
        return DATE.format(value);
    }

    public static String time(LocalTime value) {
        return TIME.format(value);
    }

    public static String eventSummary(Event event) {
        return event.getEventId()
                + " | " + event.getName()
                + " | " + date(event.getDate()) + " " + time(event.getTime())
                + " | " + event.getVenue()
                + " | registered " + event.registeredCount() + "/" + event.getCapacity()
                + " | attended " + event.attendedCount();
    }

    public static String eventTable(List<Event> events) {
        if (events.isEmpty()) {
            return "";
        }
        String header = String.format(
                Locale.ROOT,
                "%-6s %-24s %-10s %-5s %-18s %-10s %-8s %s",
                "ID", "Name", "Date", "Time", "Venue", "Registered", "Attended", "Status");
        StringBuilder builder = new StringBuilder(header);
        builder.append(System.lineSeparator());
        builder.append("-".repeat(header.length()));
        for (Event event : events) {
            String registered = event.registeredCount() + "/" + event.getCapacity();
            builder.append(System.lineSeparator());
            builder.append(String.format(
                    Locale.ROOT,
                    "%-6s %-24s %-10s %-5s %-18s %-10s %-8s %s",
                    event.getEventId(),
                    truncate(event.getName(), 24),
                    date(event.getDate()),
                    time(event.getTime()),
                    truncate(event.getVenue(), 18),
                    registered,
                    event.attendedCount(),
                    event.getStatus().displayName()));
        }
        return builder.toString();
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
