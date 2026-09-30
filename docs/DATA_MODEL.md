# Data model

Room database `CarThingDatabase`, currently **version 8**. Distances are stored in km (`Double`). Dates are stored as epoch millis, and deadline due dates as local midnight.

## Entities
```
vehicles ─┬─< fuel_entries ─────< attachments (fuelEntryId)
          ├─< service_entries ──< attachments (serviceEntryId)
          │        └── maintenanceItemId ──> maintenance_items (SET NULL)
          ├─< maintenance_items
          ├─< deadlines
          ├─< odometer_entries
          └─< attachments (vehicleId: service book photos)
```
Every child table uses `ON DELETE CASCADE`, so deleting a vehicle removes everything that belongs to it.

| Entity | Purpose | Notes |
|---|---|---|
| `Vehicle` | A car | `initialOdometerKm` is the odometer reading when the car was added |
| `FuelEntry` | A fill-up | `isFullTank` and `missedPrevious` drive the economy chain (`FuelEconomy`) |
| `ServiceEntry` | A service or repair | Optional `maintenanceItemId`. Saving it restarts that item's schedules |
| `MaintenanceItem` | A tracked component | Separate inspection and replacement schedules (`inspect*` / `replace*`), `*NotifiedLevel` (0/1/2), and an optional `icon` key |
| `Deadline` | A date-based obligation | `repeatMonths` is null for one-off deadlines. `notifiedLevel` resets when the deadline is renewed |
| `OdometerEntry` | A plain odometer reading | Can't be lower than the highest reading already known |
| `Attachment` | A photo | Exactly one owner: a fuel entry, a service entry or a vehicle. `fileName` is unique |

Odometer readings come from three places: fuel entries, service entries and odometer entries.
`Usage` and the current-odometer queries use all three.

## Migrations
Room exports each schema version to `app/schemas/com.carthing.data.CarThingDatabase/<n>.json`. The migrations are in `data/Migrations.kt`:

| Version | Change |
|---|---|
| 1→2 | `maintenance_items` added (seeded with a frozen copy of the v2 defaults); `service_entries.maintenanceItemId` added |
| 2→3 | Split each item's single interval into inspection and replacement schedules |
| 3→4 | `deadlines` |
| 4→5 | `odometer_entries` |
| 5→6 | `attachments` on fuel and service entries |
| 6→7 | `attachments.vehicleId` (service book photos) |
| 7→8 | `maintenance_items.icon` |

SQLite can't add a foreign key to an existing table. When a migration needs one, it rebuilds the table: create `_new`, copy the rows, drop the old table and rename. If dropping a table would null out `SET NULL` links, keep a copy of them first (see 2→3).

## Checklist: changing the schema
1. Change the entity and bump `version` in `CarThingDatabase`.
2. Add `MIGRATION_<n>_<n+1>` to `Migrations.kt` and register it in `CarThingDatabase.get` **and** in `MigrationTest.openRoom()`.
3. Build once so KSP exports `schemas/<n+1>.json`, then commit it. Migration tests read these files from the debug assets.
4. Extend `MigrationTest`: insert data at the old version, then run `runMigrationsAndValidate`.
5. Don't use current constants (such as `DefaultSchedule`) inside a migration. Freeze a copy so the migration always gives the same result.
6. Update the backup format when the new data should be backed up. See [BACKUP.md](BACKUP.md#changing-the-format).
