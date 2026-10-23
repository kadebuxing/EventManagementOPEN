package com.eventmanagement.util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

import com.eventmanagement.service.ValidationException;

/** Field level validation with the user facing messages from the stories. */
public final class Validators {
    public static final int MAX_TEXT_LENGTH = 50;
    public static final String DATE_HINT = "DD/MM/YYYY";
    public static final String TIME_HINT = "HH:MM";

    private static final DateTimeFormatter DATE_INPUT =
            DateTimeFormatter.ofPattern("d/M/uuuu", Locale.ROOT)
                    .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter TIME_INPUT =
            DateTimeFormatter.ofPattern("H:mm", Locale.ROOT)
                    .withResolverStyle(ResolverStyle.STRICT);

    private Validators() {
    }

    public static String validateEventName(String value) {
        String text = trimToEmpty(value);
        if (text.isEmpty()) {
            throw new ValidationException("Event name is required");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new ValidationException(
                    "Event name must be " + MAX_TEXT_LENGTH + " characters or fewer");
        }
        return text;
    }

    public static String validateVenue(String value) {
        String text = trimToEmpty(value);
        if (text.isEmpty()) {
            throw new ValidationException("Venue is required");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new ValidationException(
                    "Venue must be " + MAX_TEXT_LENGTH + " characters or fewer");
        }
        return text;
    }

    public static String validateAttendeeName(String value) {
        String text = trimToEmpty(value);
        if (text.isEmpty()) {
            throw new ValidationException("Attendee name is required");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new ValidationException(
                    "Attendee name must be " + MAX_TEXT_LENGTH + " characters or fewer");
        }
        return text;
    }

    public static LocalDate parseEventDate(String value, LocalDate today) {
        String text = trimToEmpty(value);
        LocalDate parsed;
        try {
            parsed = LocalDate.parse(text, DATE_INPUT);
        } catch (DateTimeParseException ex) {
            throw new ValidationException(
                    "Invalid date format (expected " + DATE_HINT + ")");
        }
        LocalDate reference = today == null ? LocalDate.now() : today;
        if (parsed.isBefore(reference)) {
            throw new ValidationException("Event date must be today or in the future");
        }
        return parsed;
    }

    public static LocalTime parseEventTime(String value) {
        String text = trimToEmpty(value);
        try {
            return LocalTime.parse(text, TIME_INPUT);
        } catch (DateTimeParseException ex) {
            throw new ValidationException(
                    "Invalid time format (expected " + TIME_HINT + ")");
        }
    }

    public static int parseCapacity(String value) {
        String text = trimToEmpty(value);
        int number;
        try {
            number = Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            throw new ValidationException(
                    "Capacity must be a whole number greater than 0");
        }
        if (number <= 0) {
            throw new ValidationException(
                    "Capacity must be a whole number greater than 0");
        }
        return number;
    }

    public static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
