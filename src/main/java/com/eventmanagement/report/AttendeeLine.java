package com.eventmanagement.report;

/** One row of the attendance part of a report. */
public record AttendeeLine(String reference, String name, boolean attended) {
}
