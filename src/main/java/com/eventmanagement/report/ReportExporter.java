package com.eventmanagement.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes an event report to disk without touching the stored data. */
public final class ReportExporter {
    private ReportExporter() {
    }

    public static Path write(EventReport report, Path target) {
        try {
            Path absolute = target.toAbsolutePath().normalize();
            Path parent = absolute.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(absolute, report.toText(), StandardCharsets.UTF_8);
            return absolute;
        } catch (IOException ex) {
            throw new ReportException("Could not export report to " + target + ": " + ex.getMessage(), ex);
        }
    }

    /** Raised when a report cannot be written. */
    public static final class ReportException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public ReportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
