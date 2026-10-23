package com.eventmanagement.tests;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Creates throw-away data files and removes them when the suite finishes. */
final class TempFiles {
    private static final List<Path> DIRECTORIES = new ArrayList<>();

    private TempFiles() {
    }

    static Path newStorePath() {
        return newDirectory().resolve("events.json");
    }

    static Path newReportPath(String fileName) {
        return newDirectory().resolve(fileName);
    }

    private static synchronized Path newDirectory() {
        try {
            Path directory = Files.createTempDirectory("event-management-test-");
            DIRECTORIES.add(directory);
            return directory;
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not create a temporary directory", ex);
        }
    }

    static synchronized void cleanup() {
        for (Path directory : DIRECTORIES) {
            try (var paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // Best effort: temporary files never affect the outcome of a test.
                    }
                });
            } catch (IOException ignored) {
                // The directory may already be gone; nothing else to do.
            }
        }
        DIRECTORIES.clear();
    }
}
