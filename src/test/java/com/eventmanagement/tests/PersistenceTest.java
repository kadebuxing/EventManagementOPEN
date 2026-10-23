package com.eventmanagement.tests;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.eventmanagement.model.Event;
import com.eventmanagement.service.EventListing;
import com.eventmanagement.service.EventService;
import com.eventmanagement.storage.JsonStorage;
import com.eventmanagement.storage.StorageException;

/** OPEN-9: JSON persistence, schema checks and crash-safe writes. */
public final class PersistenceTest extends ServiceFixture {

    @Test
    public void cancelledStatusSurvivesARestart() {
        Event event = createSampleEvent();
        service.cancelEvent(event.getEventId());

        EventService restarted = serviceOn(TODAY);
        Assert.assertTrue(restarted.getEvent(event.getEventId()).isCancelled(), "Status is stored");
        EventListing listing = restarted.listEvents(null);
        Assert.assertEquals(1, listing.cancelled().size(), "Cancelled section after restart");
    }

    @Test
    public void corruptedFileIsRejectedAndLeftUntouched() throws Exception {
        String original = "{ this is not valid json";
        Files.writeString(storePath, original);

        StorageException error = Assert.assertThrows(
                StorageException.class,
                () -> new EventService(new JsonStorage(storePath), () -> TODAY),
                "A corrupted file must fail loudly");
        Assert.assertContains(error.getMessage(), "not valid JSON", "Explains the problem");
        Assert.assertEquals(original, Files.readString(storePath), "The data file is untouched");
    }

    @Test
    public void editIsPersistedAcrossRestart() {
        Event event = createSampleEvent();
        service.updateEvent(event.getEventId(), "Renamed", "15/01/2027", "14:45", "Hall C", "80");

        Event reloaded = serviceOn(TODAY).getEvent(event.getEventId());
        Assert.assertEquals("Renamed", reloaded.getName(), "Name");
        Assert.assertEquals("Hall C", reloaded.getVenue(), "Venue");
        Assert.assertEquals(80, reloaded.getCapacity(), "Capacity");
    }

    @Test
    public void missingFileIsTreatedAsAnEmptyStore() {
        Assert.assertFalse(service.hasEvents(), "A missing file starts with no events");
    }

    @Test
    public void identifierCountersArePersisted() {
        Event first = createSampleEvent();
        service.registerAttendee(first.getEventId(), "Alice");
        EventService restarted = serviceOn(TODAY);
        Event second = restarted.createEvent("Team Sync", "01/11/2026", "10:00", "Room B", "5");
        Assert.assertEquals("E-002", second.getEventId(), "Event numbering continues");
    }

    @Test
    public void jsonArrayRootIsRejected() throws Exception {
        Files.writeString(storePath, "[]");
        Assert.assertThrows(
                StorageException.class,
                () -> new EventService(new JsonStorage(storePath), () -> TODAY),
                "The root must be an object");
    }

    @Test
    public void missingEventFieldsAreRejected() throws Exception {
        Files.writeString(storePath, """
                {
                  "events": [
                    {"eventId": "E-001", "name": "Broken"}
                  ]
                }
                """);
        StorageException error = Assert.assertThrows(
                StorageException.class,
                () -> new EventService(new JsonStorage(storePath), () -> TODAY),
                "Incomplete events must not load");
        Assert.assertContains(error.getMessage(), "invalid event data", "Explains the problem");
    }

    @Test
    public void invalidCapacityInTheDataFileIsRejected() throws Exception {
        Files.writeString(storePath, """
                {
                  "events": [
                    {
                      "eventId": "E-001",
                      "name": "Broken",
                      "date": "2026-12-31",
                      "time": "09:30",
                      "venue": "Hall A",
                      "capacity": 0
                    }
                  ]
                }
                """);
        StorageException error = Assert.assertThrows(
                StorageException.class,
                () -> new EventService(new JsonStorage(storePath), () -> TODAY),
                "Non-positive capacity must not load");
        Assert.assertContains(error.getMessage(), "capacity", "Mentions the bad field");
    }

    @Test
    public void saveLeavesNoTemporaryFilesBehind() throws Exception {
        createSampleEvent();
        Path directory = storePath.getParent();
        try (Stream<Path> entries = Files.list(directory)) {
            List<String> names = entries.map(path -> path.getFileName().toString()).toList();
            Assert.assertEquals(List.of("events.json"), names, "Only the data file remains");
        }
    }

    @Test
    public void saveIsAtomicAcrossManyEdits() throws Exception {
        Event event = createSampleEvent();
        for (int index = 0; index < 20; index++) {
            service.registerAttendee(event.getEventId(), "Attendee " + index);
            service.setCapacity(event.getEventId(), String.valueOf(100 + index));
        }
        String json = Files.readString(storePath);
        Assert.assertContains(json, "\"capacity\": 119", "The last write won");
        Assert.assertTrue(json.trim().endsWith("}"), "The JSON document is complete");
    }

    @Test
    public void reloadDiscardsUnsavedInMemoryChanges() {
        Event event = createSampleEvent();
        event.rename("Changed only in memory");
        service.reload();
        Assert.assertEquals("Java Meetup", service.getEvent(event.getEventId()).getName(), "Reload wins");
    }
}
