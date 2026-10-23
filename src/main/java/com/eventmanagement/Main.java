package com.eventmanagement;

import java.io.PrintStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import com.eventmanagement.cli.ConsoleApp;
import com.eventmanagement.service.EventService;
import com.eventmanagement.storage.JsonStorage;
import com.eventmanagement.storage.StorageException;

/**
 * Entry point of the Event Management and Staffing System.
 *
 * <p>Usage: {@code java com.eventmanagement.Main [--data <file>]}. The default
 * data file is {@code data/events.json} relative to the working directory.</p>
 */
public final class Main {
    private static final String DEFAULT_DATA_FILE = "data/events.json";

    private Main() {
    }

    public static void main(String[] args) {
        Path dataFile;
        try {
            dataFile = resolveDataFile(args);
        } catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            printUsage(System.err);
            System.exit(2);
            return;
        }
        if (dataFile == null) {
            printUsage(System.out);
            return;
        }

        JsonStorage storage = new JsonStorage(dataFile);
        EventService service;
        try {
            service = new EventService(storage);
        } catch (StorageException ex) {
            System.err.println("Storage error: " + ex.getMessage());
            System.err.println("The existing data file was not modified.");
            System.exit(1);
            return;
        }
        System.out.println("Data file: " + storage.getPath());
        System.exit(new ConsoleApp(service, System.in, System.out).run());
    }

    /**
     * Returns the data file to use, or {@code null} when help was requested.
     *
     * @throws IllegalArgumentException when the arguments are malformed
     */
    private static Path resolveDataFile(String[] args) {
        String value = DEFAULT_DATA_FILE;
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            switch (argument) {
                case "--data" -> {
                    if (index + 1 >= args.length) {
                        throw new IllegalArgumentException("Missing value for --data");
                    }
                    value = args[++index];
                }
                case "--help", "-h" -> {
                    return null;
                }
                default -> throw new IllegalArgumentException("Unknown argument: " + argument);
            }
        }
        try {
            return Path.of(value);
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException("Invalid data file path: " + value);
        }
    }

    private static void printUsage(PrintStream out) {
        out.println("Event Management and Staffing System");
        out.println("Usage: java com.eventmanagement.Main [--data <file>]");
        out.println("  --data <file>  JSON file used for persistence");
        out.println("                 (default: " + DEFAULT_DATA_FILE + ")");
        out.println("  --help         show this help");
    }
}
