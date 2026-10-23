package com.eventmanagement.tests;

import java.time.LocalDate;
import java.time.LocalTime;

import com.eventmanagement.service.ValidationException;
import com.eventmanagement.util.Validators;

/** OPEN-1: field level validation rules and their user facing messages. */
public final class ValidationTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 20);

    @Test
    public void attendeeNameIsRequired() {
        ValidationException error = Assert.assertThrows(
                ValidationException.class,
                () -> Validators.validateAttendeeName("   "),
                "A blank attendee name must be rejected");
        Assert.assertEquals("Attendee name is required", error.getMessage(), "Unexpected message");
    }

    @Test
    public void capacityMustBeNumeric() {
        Assert.assertThrows(
                ValidationException.class,
                () -> Validators.parseCapacity("ten"),
                "Text is not a valid capacity");
    }

    @Test
    public void capacityMustBePositive() {
        Assert.assertThrows(
                ValidationException.class,
                () -> Validators.parseCapacity("0"),
                "Zero capacity must be rejected");
        Assert.assertThrows(
                ValidationException.class,
                () -> Validators.parseCapacity("-4"),
                "Negative capacity must be rejected");
    }

    @Test
    public void capacityWhitespaceIsTrimmed() {
        Assert.assertEquals(25, Validators.parseCapacity("  25  "), "Capacity should be trimmed");
    }

    @Test
    public void dateMustUseDayMonthYearFormat() {
        ValidationException error = Assert.assertThrows(
                ValidationException.class,
                () -> Validators.parseEventDate("2026-12-31", TODAY),
                "ISO dates must be rejected");
        Assert.assertEquals(
                "Invalid date format (expected DD/MM/YYYY)",
                error.getMessage(),
                "Unexpected message");
    }

    @Test
    public void eventNameIsRequired() {
        Assert.assertThrows(
                ValidationException.class,
                () -> Validators.validateEventName(""),
                "An empty event name must be rejected");
        Assert.assertThrows(
                ValidationException.class,
                () -> Validators.validateEventName(null),
                "A null event name must be rejected");
    }

    @Test
    public void eventNameOfExactlyFiftyCharactersIsAccepted() {
        String name = "a".repeat(Validators.MAX_TEXT_LENGTH);
        Assert.assertEquals(name, Validators.validateEventName(name), "50 characters are allowed");
    }

    @Test
    public void eventNameLongerThanFiftyCharactersIsRejected() {
        String name = "a".repeat(Validators.MAX_TEXT_LENGTH + 1);
        ValidationException error = Assert.assertThrows(
                ValidationException.class,
                () -> Validators.validateEventName(name),
                "51 characters must be rejected");
        Assert.assertEquals(
                "Event name must be 50 characters or fewer", error.getMessage(), "Unexpected message");
    }

    @Test
    public void impossibleDateIsRejected() {
        Assert.assertThrows(
                ValidationException.class,
                () -> Validators.parseEventDate("31/02/2026", TODAY),
                "31 February must be rejected");
        Assert.assertThrows(
                ValidationException.class,
                () -> Validators.parseEventDate("01/13/2026", TODAY),
                "Month 13 must be rejected");
    }

    @Test
    public void invalidTimeIsRejected() {
        Assert.assertThrows(
                ValidationException.class,
                () -> Validators.parseEventTime("9:75"),
                "Minute 75 must be rejected");
        Assert.assertThrows(
                ValidationException.class,
                () -> Validators.parseEventTime("24:00"),
                "Hour 24 must be rejected");
        Assert.assertThrows(
                ValidationException.class,
                () -> Validators.parseEventTime("09.30"),
                "Dotted times must be rejected");
    }

    @Test
    public void pastDateIsRejected() {
        ValidationException error = Assert.assertThrows(
                ValidationException.class,
                () -> Validators.parseEventDate("19/05/2026", TODAY),
                "Yesterday must be rejected");
        Assert.assertEquals(
                "Event date must be today or in the future", error.getMessage(), "Unexpected message");
    }

    @Test
    public void singleDigitDateAndTimeAreAccepted() {
        Assert.assertEquals(
                LocalDate.of(2026, 6, 5),
                Validators.parseEventDate("5/6/2026", TODAY),
                "Single digit day and month should parse");
        Assert.assertEquals(
                LocalTime.of(9, 30),
                Validators.parseEventTime("9:30"),
                "Single digit hour should parse");
    }

    @Test
    public void todayIsAccepted() {
        Assert.assertEquals(
                TODAY, Validators.parseEventDate("20/05/2026", TODAY), "Today is a valid date");
    }

    @Test
    public void venueIsRequired() {
        Assert.assertThrows(
                ValidationException.class,
                () -> Validators.validateVenue("   "),
                "A blank venue must be rejected");
    }
}
