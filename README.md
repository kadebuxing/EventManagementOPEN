# Event Management and Staffing System

A console application built from the Jira backlog `OPEN-1` to `OPEN-10`
("Event Management and Staffing"). The whole system is written in plain Java
with **no third-party dependencies**: the JSON store, the console UI and the
test runner all ship with the project.

- Java 17+ (developed and verified with JDK 25 using `--release 17`)
- JSON persistence with atomic file replacement
- 99 tests, all passing, run by a small zero-dependency test runner

Repository: <https://github.com/kadebuxing/EventManagementOPEN>

## Jira status

Jira is the source of truth for sprint status:

| Sprint | Issues | Status |
| --- | --- | --- |
| Sprint 1 (active) | OPEN-1, OPEN-2, OPEN-4, OPEN-8, OPEN-10 | Done |
| Sprint 2 (future) | OPEN-3, OPEN-5, OPEN-6, OPEN-7, OPEN-9, OPEN-11, OPEN-12 | To Do |

Four Sprint 2 stories are code-complete ahead of the sprint because they sit on
the same records as Sprint 1: OPEN-3 (registration) feeds the capacity rule in
OPEN-4 and the cancelled-event rule in OPEN-8, OPEN-6 (cancel registration)
frees a place again, OPEN-5 (attendance) works from the registration list, and
OPEN-9 (persistence) saves every Sprint 1 change. OPEN-7 (reports) has an early
implementation. All five remain **To Do** in Jira until Sprint 2 starts.
OPEN-11 (JUnit regression suite) and OPEN-12 (file locking) are not
implemented.

## Requirements

| Tool | Version |
| --- | --- |
| JDK | 17 or newer (`javac` and `java` on the `PATH`) |
| PowerShell | 5.1+ on Windows for the helper scripts (optional) |

## Quick start

```powershell
.\scripts\compile.ps1   # compile into out/main
.\scripts\run.ps1       # compile and start the console
.\scripts\test.ps1      # compile everything and run all 99 tests
```

Use a different data file:

```powershell
.\scripts\run.ps1 --data data\my-events.json
```

Manual equivalent on any platform:

```bash
javac --release 17 -encoding UTF-8 -d out/main $(find src/main/java -name '*.java')
java -cp out/main com.eventmanagement.Main
```

```bash
javac --release 17 -encoding UTF-8 -cp out/main -d out/test $(find src/test/java -name '*.java')
java -cp "out/main:out/test" com.eventmanagement.tests.TestRunner
```

## Menu

| Option | Action |
| --- | --- |
| 1 | Create event |
| 2 | List / search events (active and cancelled sections) |
| 3 | Edit event |
| 4 | Cancel event |
| 5 | Undo event cancellation |
| 6 | Update event capacity |
| 7 | Register attendee |
| 8 | Cancel registration (asks for confirmation) |
| 9 | Track attendance |
| 10 | Event summary |
| 11 | Generate / export report |
| 0 | Exit |

Entering `cancel` at any prompt of the create/edit/registration flows aborts the
action without saving anything. Invalid input is reported and the prompt is
repeated, so a rejected field never consumes an identifier.

## Jira traceability

### Sprint 1 — Done

| Issue | User story | Implementation |
| --- | --- | --- |
| OPEN-1 | Create Event | `EventService.createEvent`, `Validators` |
| OPEN-2 | List Events | `EventService.listEvents`, `EventListing`, `Formats.eventTable` |
| OPEN-4 | Manage Event Capacity | `EventService.setCapacity`, `Event.isFull` |
| OPEN-8 | Cancel Event | `EventService.cancelEvent`, `undoCancelEvent` |
| OPEN-10 | Edit Event | `EventService.updateEvent` |

### Sprint 2 — Code-complete early (strong Sprint 1 coupling)

| Issue | User story | Implementation | Acceptance coverage |
| --- | --- | --- | --- |
| OPEN-3 | Register Attendee | `EventService.registerAttendee`, `Registration` | unique reference, capacity block, cancelled-event block, live counts |
| OPEN-5 | Track Attendance | `EventService.markAttendance`, `Event.attendedCount` | day-of-event marking, registered vs attended list, unknown references rejected |
| OPEN-6 | Cancel Registration | `EventService.cancelRegistration` | confirmation, freed place, cancelled entry hidden from attendance |
| OPEN-9 | Data Persistence | `JsonStorage`, `Json`, `Storage` | restart round trips, attendance flags, counters, atomic writes |

### Sprint 2 — Early code

| Issue | User story | Implementation |
| --- | --- | --- |
| OPEN-7 | Generate Reports | `EventService.generateReport`, `ReportExporter` |

### Sprint 2 — Not implemented

| Issue | User story | Planned work |
| --- | --- | --- |
| OPEN-11 | Add JUnit regression suite | Migrate the business-rule tests to JUnit 5 and run them in CI |
| OPEN-12 | Add file locking for concurrent access | Add file locking or optimistic concurrency with a clear failure message |

## Business rules

- Event identifiers are `E-001`, `E-002`, ... and registration references are
  `R-0001`, `R-0002`, ... Numbers are never reused, even after cancellation.
- Event names, venues and attendee names are trimmed and limited to 50
  characters; blank values are rejected.
- Dates are entered as `DD/MM/YYYY` (single digit day/month allowed) and must be
  today or later. Times use `HH:MM` (single digit hour allowed).
- Capacity must be a whole number greater than 0 and can never be set below the
  number of active registrations.
- Registrations are refused when the event is cancelled or full. Cancelling a
  registration requires explicit confirmation and immediately frees the place.
- Attendance can only be marked for active registrations on or after the event
  date. Cancelling a registration clears its attendance flag.
- Cancelled events stay visible in the `Cancelled` section, reject edits,
  registrations and attendance tracking, and can be restored.
- Reports are immutable snapshots: generating or exporting one never changes
  stored data. Exported reports are UTF-8 text files.
- Passed events are read-only for editing.

## Data storage

State lives in a single JSON file (`data/events.json` by default):

```json
{
  "version": 1,
  "nextEventNumber": 2,
  "nextRegistrationNumber": 3,
  "events": [
    {
      "eventId": "E-001",
      "name": "Java Meetup",
      "date": "2026-12-31",
      "time": "09:30",
      "venue": "Hall A",
      "capacity": 50,
      "status": "Active",
      "registrations": [
        {
          "reference": "R-0001",
          "attendeeName": "Alice",
          "attended": false,
          "cancelled": false
        }
      ]
    }
  ]
}
```

Saves are written to a temporary file in the same directory, flushed to disk and
then moved over the data file, so an interrupted save leaves the previous file
intact. A corrupted file is reported with a `StorageException` and is never
overwritten.

## Project layout

```
src/main/java/com/eventmanagement/
  Main.java                 entry point and --data/--help handling
  model/                    Event, Registration, Store, EventStatus
  service/                  EventService and its exceptions
  storage/                  Storage, JsonStorage, Json, StorageException
  report/                   EventReport, AttendeeLine, ReportExporter
  util/                     Validators, Formats
  cli/                      ConsoleApp
src/test/java/com/eventmanagement/tests/
  Test.java, Assert.java, TempFiles.java, TestRunner.java, ServiceFixture.java
  ValidationTest, EventLifecycleTest, RegistrationTest,
  ReportTest, PersistenceTest, CliSmokeTest
scripts/                    compile.ps1, run.ps1, test.ps1
.github/workflows/build.yml GitHub Actions: compile and run the suite
```

## Tests

`.\scripts\test.ps1` runs the suite through `com.eventmanagement.tests.TestRunner`
and prints one line per test plus a summary:

```
Tests run: 99, passed: 99, failed: 0
OK
```

Coverage highlights:

- field validation: required values, 50 character limit, strict date and time
  parsing, past dates, non-positive capacity
- event lifecycle: sequential identifiers without reuse, search by name, venue,
  date and id, active/cancelled sections, edit rules, cancel and restore
- registrations: capacity limits, unique references, confirmation before
  cancellation, freed places, persistence of cancelled entries
- attendance: only on/after the event date, only for active registrations,
  clearing attendance when a registration is cancelled
- reports: counts, immutable snapshots, exclusion of cancelled registrations,
  export without modifying stored data
- persistence: restart round trips, counter recovery, corrupted and malformed
  JSON rejected without touching the file, no temporary files left behind
- console: scripted end-to-end runs of create, list, register, cancel and export
- Sprint 1 console acceptance: empty list message, past-date retry, filtered
  no-match, capacity rejection below registrations, passed-event edit block,
  blank edit keeps the current values
- Sprint 2 early acceptance: registered/attended counts in the event list,
  attendee list with attendance state, cancellation confirmation and abort,
  cancelled entries hidden from attendance, attendance surviving a restart

## Continuous integration

`.github/workflows/build.yml` compiles the main and test sources on Java 17 and
runs the same test runner on every push to `main` and every pull request.
