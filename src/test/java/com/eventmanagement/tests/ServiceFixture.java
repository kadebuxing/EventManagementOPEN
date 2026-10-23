package com.eventmanagement.tests;

import java.nio.file.Path;
import java.time.LocalDate;

import com.eventmanagement.model.Event;
import com.eventmanagement.service.EventService;
import com.eventmanagement.storage.JsonStorage;

/** Shared fixture: a temporary data file plus a service pinned to a fixed day. */
abstract class ServiceFixture {
    static final LocalDate TODAY = LocalDate.of(2026, 5, 20);

    final Path storePath = TempFiles.newStorePath();
    final EventService service = serviceOn(TODAY);

    EventService serviceOn(LocalDate day) {
        return new EventService(new JsonStorage(storePath), () -> day);
    }

    Event createSampleEvent() {
        return service.createEvent("Java Meetup", "31/12/2026", "09:30", "Hall A", "2");
    }
}
