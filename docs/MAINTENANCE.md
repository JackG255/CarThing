# Maintenance and deadlines

The code lives in `data/maintenance/`, `data/deadlines/`, `MaintenanceRepository` and `DeadlineRepository`.

## Components and schedules
A `MaintenanceItem` can have up to two independent `Schedule`s:
- **Inspection:** check it (brake pads, tires, tire pressure, battery).
- **Replacement:** replace it (oil, filters, fluids, belt).

Each schedule has `intervalKm` and/or `intervalMonths`, and it's due when **either** runs out. A null
interval means that limit isn't tracked. Replacing a component also counts as inspecting it.
`Schedule.of` returns null when both intervals are null.

New vehicles get `DefaultSchedule.templates` (12 generic components). The owner's service book is
the real source of intervals, so they're meant to be edited.

Schedules restart when:
- the user marks one done (`MaintenanceRepository.markDone`, which uses `MaintenanceItem.recorded`), or
- a service entry linked to the item is saved and is at least as recent as the last replacement. This restarts **both** schedules.

## Driving rate (`Usage`)
`Usage.estimate` takes every odometer reading: fuel entries, service entries and odometer entries.
- The last reading is the highest odometer value.
- The rate in km/day comes from readings in the **90 days** before the last one. It falls back to all history when that window is too short, and needs readings at least **7 days** apart. Negative rates are ignored.
- `estimatedOdometerKm(now)` projects the last reading forward at that rate, and never goes below it.

## Due status (`MaintenanceStatus.of`)
- `kmRemaining` = last done km + interval − max(recorded odometer, estimated odometer).
- `daysRemaining` = last done date + interval months − today.
- `predictedDaysByKm` = `kmRemaining` divided by the km/day rate.
- `fractionRemaining` = the share of the interval left, by whichever limit is closer.

| Level | When |
|---|---|
| `UNKNOWN` | No "last done" is known for any tracked limit |
| `OVERDUE` | Either limit is ≤ 0 |
| `DUE_SOON` | Within **1,000 km** or **30 days**, or the km limit is predicted within 30 days. Both thresholds shrink for short intervals: km/10 and months×30/4 days, so a monthly check isn't always "soon" |
| `OK` | Otherwise |

`ComponentStatus` picks the more urgent schedule as `primary`, ordered by level and then by days
until due. On a tie it prefers replacement.

`UrgencyBand` sets the chip colour: `FRESH` (> ½ of the interval left), `MIDWAY` (¼–½), `SOON` (< ¼ or due soon), `OVERDUE`, `UNKNOWN`.

## Deadlines (`DeadlineStatus`)
Deadlines depend only on the date: overdue after the due day, due soon within **30 days**.
Renewing moves the due date by `repeatMonths` (`DeadlineStatus.nextDue`) and resets `notifiedLevel`.
Quick-add templates: technical inspection (STK, 24 months), emissions check (24), motorway
vignette (12), insurance (12).

## Reminders (`ReminderPolicy`)
The daily `MaintenanceCheckWorker` evaluates every schedule and deadline inside a transaction:
- It notifies once on reaching **due soon** and once more on **overdue**, and never repeats a level.
- It stores the new level in `inspectNotifiedLevel`, `replaceNotifiedLevel` or `notifiedLevel`.
- Disabled items store 0. A lower level (for example after the interval was extended) is stored as well, so a later rise can notify again.

Notifications are removed when an item is done, deleted or renewed (`ReminderNotifications`).
To test on a debug build, see [ARCHITECTURE.md](ARCHITECTURE.md#background-work-notifications).
