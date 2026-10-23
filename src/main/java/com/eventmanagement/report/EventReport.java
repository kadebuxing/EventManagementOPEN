package com.eventmanagement.report;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

import com.eventmanagement.model.EventStatus;
import com.eventmanagement.util.Formats;

/** An immutable snapshot of the numbers requested by OPEN-7. */
public record EventReport(
        String eventId,
        String name,
        LocalDate eventDate,
        LocalTime eventTime,
        String venue,
        int capacity,
        EventStatus status,
        int registered,
        int attended,
        List<AttendeeLine> attendees) {

    public EventReport {
        attendees = List.copyOf(attendees);
    }

    public int availablePlaces() {
        return Math.max(capacity - registered, 0);
    }

    public String toText() {
        StringBuilder builder = new StringBuilder();
        builder.append("Event Report").append(System.lineSeparator());
        builder.append("============").append(System.lineSeparator());
        builder.append(String.format(Locale.ROOT, "Event ID        : %s%n", eventId));
        builder.append(String.format(Locale.ROOT, "Name            : %s%n", name));
        builder.append(String.format(
                Locale.ROOT,
                "Date            : %s %s%n",
                Formats.date(eventDate),
                Formats.time(eventTime)));
        builder.append(String.format(Locale.ROOT, "Venue           : %s%n", venue));
        builder.append(String.format(Locale.ROOT, "Status          : %s%n", status.displayName()));
        builder.append(String.format(Locale.ROOT, "Capacity        : %d%n", capacity));
        builder.append(String.format(Locale.ROOT, "Registered      : %d%n", registered));
        builder.append(String.format(Locale.ROOT, "Attended        : %d%n", attended));
        builder.append(String.format(Locale.ROOT, "Available places: %d%n", availablePlaces()));
        builder.append(System.lineSeparator());
        builder.append("Attendees").append(System.lineSeparator());
        builder.append("---------").append(System.lineSeparator());
        if (attendees.isEmpty()) {
            builder.append("No registered attendees.").append(System.lineSeparator());
        } else {
            int index = 1;
            for (AttendeeLine attendee : attendees) {
                String state = attendee.attended() ? "Attended" : "Registered";
                builder.append(String.format(
                        Locale.ROOT,
                        "%d. %-7s %-24s [%s]%n",
                        index++,
                        attendee.reference(),
                        attendee.name(),
                        state));
            }
        }
        return builder.toString();
    }
}
