# Architecture

Single-module Android app (`app/`), one Activity, Compose UI, Room storage. Nothing leaves the
device except the backups the user exports.

## Layers
```
entity (Room @Entity)  →  dao  →  repository  →  ViewModel  →  Compose screen
                          pure logic: maintenance/, deadlines/, ocr/, FuelEconomy, VehicleStats
```

- **`data/entity/`**: Room entities. See [DATA_MODEL.md](DATA_MODEL.md).
- **`data/dao/`**: queries. Reads return `Flow` for live UI, and writes are `suspend`.
- **`data/repository/`**: business rules around writes, such as:
  - `FuelRepository.save` validates odometer order and duplicate full tanks (`FuelValidation`). It returns `SaveResult.Rejected` with errors or with warnings the user can accept.
  - `VehicleRepository.save` seeds a new vehicle with `DefaultSchedule`.
  - `ServiceRepository.save` resets the linked maintenance item's schedules.
  - `MaintenanceRepository` / `DeadlineRepository` combine rows with computed status and collect reminders.
- **Pure logic**: plain Kotlin objects with no Android dependencies, so they're easy to unit-test. `Usage`, `MaintenanceStatus`, `ReminderPolicy`, `UrgencyBand`, `DeadlineStatus`, `FuelEconomy`, `VehicleStats`, `BackupPolicy` and the OCR parsers all work this way.
- **`ui/`**: Compose screens grouped by feature (`vehicles/`, `fuel/`, `service/`, `maintenance/`, `deadlines/`, `attachments/`, `backup/`, `common/`).

## Dependency wiring
There is no DI framework. `data/AppContainer.kt` builds every repository and service once. The
`CarThingApp` Application class owns it (`val container by lazy`).

ViewModels get their dependencies through a `viewModelFactory` in their companion object
(`Factory` / `factory(id)`), which reads `CarThingApp.container`. Tests build repositories directly
over an in-memory database instead.

## Navigation
`ui/CarThingNavHost.kt` uses type-safe Navigation Compose routes (`@Serializable` classes):
`VehicleListRoute`, `VehicleFormRoute`, `VehicleDetailRoute(vehicleId, tab)`, and one form route per
entry type (`FuelFormRoute`, `ServiceFormRoute`, `MaintenanceItemFormRoute`, `DeadlineFormRoute`).
An id of `0` means "new". Tapping a notification opens `MainActivity` with
`MaintenanceNotifier.EXTRA_VEHICLE_ID`.

## Background work (`notifications/`)
| Worker | Schedule | Does |
|---|---|---|
| `MaintenanceCheckWorker` | daily; `MainActivity` schedules it with `KEEP` | Posts maintenance and deadline reminders, a stale-backup reminder, and sweeps orphaned photos |
| `AutoBackupWorker` | weekly when a folder is set; `runOnce` for "Back up now" | Writes a zip into the chosen folder and prunes old automatic backups |

`MaintenanceNotifier` posts on one channel (`maintenance`). Notification ids are stable per
component and schedule, and negative for deadlines. `ReminderNotifications` cancels them when an
item is done, deleted or renewed.

Debug builds include `RunMaintenanceCheckReceiver`, which triggers the check immediately:
```
adb shell am broadcast -n com.carthing.debug/com.carthing.debug.RunMaintenanceCheckReceiver
```

## Storage
- Room database `carthing.db`.
- Photos: `filesDir/attachments/<uuid>.jpg`, see [BACKUP.md](BACKUP.md#photos).
- SharedPreferences: `backup_settings` (backup bookkeeping and folder URI) and `ui_settings` (wallpaper colours).
- Android's own backup (`res/xml/data_extraction_rules.xml`, `backup_rules.xml`) includes the database and preferences. It leaves out `backup_settings.xml`, because a folder permission only works on the same device, and it doesn't include photos.

## Build variants
- `debug`: application id `com.carthing.debug`, so it installs next to the release app.
- `release`: R8 with resource shrinking, and split per ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`) because the bundled ML Kit model ships native code.
