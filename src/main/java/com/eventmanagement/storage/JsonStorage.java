package com.eventmanagement.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.eventmanagement.model.Event;
import com.eventmanagement.model.EventStatus;
import com.eventmanagement.model.Registration;
import com.eventmanagement.model.Store;
import com.eventmanagement.util.Formats;

/**
 * Stores the whole system state in a single JSON file.
 *
 * <p>Writes go to a temporary file in the same directory and are then moved
 * into place with {@link Files#move}. On every supported platform the final
 * move is atomic, so a crash half way through a save leaves the previous file
 * completely intact.</p>
 */
public final class JsonStorage implements Storage {
    private static final int SCHEMA_VERSION = 1;

    private final Path path;

    public JsonStorage(Path path) {
        this.path = path.toAbsolutePath().normalize();
    }

    public Path getPath() {
        return path;
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------
    @Override
    public Store load() {
        if (!Files.exists(path)) {
            return new Store();
        }
        String text;
        try {
            text = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new StorageException("Could not read data file " + path + ": " + ex.getMessage(), ex);
        }

        Object root;
        try {
            root = Json.parse(text);
        } catch (RuntimeException ex) {
            throw new StorageException(
                    "Data file " + path + " is not valid JSON and was left untouched: "
                            + ex.getMessage(),
                    ex);
        }
        if (!(root instanceof Map<?, ?> map)) {
            throw new StorageException("Data file " + path + " has an unexpected format");
        }
        try {
            return readStore(map);
        } catch (StorageException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new StorageException(
                    "Data file " + path + " contains invalid event data: " + ex.getMessage(),
                    ex);
        }
    }

    private Store readStore(Map<?, ?> map) {
        Store store = new Store();
        Object eventsValue = map.get("events");
        if (eventsValue != null) {
            if (!(eventsValue instanceof List<?> items)) {
                throw new IllegalArgumentException("'events' must be an array");
            }
            for (Object item : items) {
                if (!(item instanceof Map<?, ?> eventMap)) {
                    throw new IllegalArgumentException("an event entry must be an object");
                }
                store.addEvent(readEvent(eventMap));
            }
        }
        store.setNextEventNumber(intValue(map.get("nextEventNumber"), 1));
        store.setNextRegistrationNumber(intValue(map.get("nextRegistrationNumber"), 1));
        return store;
    }

    private Event readEvent(Map<?, ?> map) {
        String eventId = requiredString(map, "eventId");
        String name = requiredString(map, "name");
        LocalDate date = LocalDate.parse(requiredString(map, "date"));
        LocalTime time = LocalTime.parse(requiredString(map, "time"));
        String venue = requiredString(map, "venue");
        int capacity = intValue(map.get("capacity"), -1);
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be greater than 0");
        }
        EventStatus status = EventStatus.fromDisplay(optionalString(map.get("status"), "Active"));
        Event event = new Event(eventId, name, date, time, venue, capacity, status);

        Object registrationsValue = map.get("registrations");
        if (registrationsValue != null) {
            if (!(registrationsValue instanceof List<?> items)) {
                throw new IllegalArgumentException("'registrations' must be an array");
            }
            for (Object item : items) {
                if (!(item instanceof Map<?, ?> registrationMap)) {
                    throw new IllegalArgumentException("a registration entry must be an object");
                }
                event.addRegistration(new Registration(
                        requiredString(registrationMap, "reference"),
                        requiredString(registrationMap, "attendeeName"),
                        booleanValue(registrationMap.get("attended"), false),
                        booleanValue(registrationMap.get("cancelled"), false)));
            }
        }
        return event;
    }

    private static String requiredString(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("missing '" + key + "'");
        }
        return text;
    }

    private static String optionalString(Object value, String fallback) {
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ex) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        return value instanceof Boolean bool ? bool : fallback;
    }

    // ------------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------------
    @Override
    public void save(Store store) {
        String text = Json.stringify(toMap(store)) + System.lineSeparator();
        writeAtomically(text);
    }

    private Map<String, Object> toMap(Store store) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", SCHEMA_VERSION);
        root.put("nextEventNumber", store.getNextEventNumber());
        root.put("nextRegistrationNumber", store.getNextRegistrationNumber());
        List<Object> events = new ArrayList<>();
        for (Event event : store.getEvents()) {
            events.add(eventToMap(event));
        }
        root.put("events", events);
        return root;
    }

    private Map<String, Object> eventToMap(Event event) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("eventId", event.getEventId());
        map.put("name", event.getName());
        map.put("date", event.getDate().toString());
        map.put("time", Formats.time(event.getTime()));
        map.put("venue", event.getVenue());
        map.put("capacity", event.getCapacity());
        map.put("status", event.getStatus().displayName());
        List<Object> registrations = new ArrayList<>();
        for (Registration registration : event.getRegistrations()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("reference", registration.getReference());
            item.put("attendeeName", registration.getAttendeeName());
            item.put("attended", registration.isAttended());
            item.put("cancelled", registration.isCancelled());
            registrations.add(item);
        }
        map.put("registrations", registrations);
        return map;
    }

    private void writeAtomically(String text) {
        Path parent = path.getParent();
        Path temp = null;
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            temp = Files.createTempFile(parent, path.getFileName().toString() + ".", ".tmp");
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(temp, path,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            temp = null;
        } catch (IOException ex) {
            throw new StorageException(
                    "Could not save data file " + path + ": " + ex.getMessage(), ex);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // Best effort cleanup; the original file is still intact.
                }
            }
        }
    }
}
