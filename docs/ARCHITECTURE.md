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
| `MaintenanceCheckWorker` | daily; `MainActivity` schedules it with `KEEP` | Relabels default names if the language changed, posts maintenance and deadline reminders, a stale-backup reminder, and sweeps orphaned photos |
| `AutoBackupWorker` | weekly when a folder is set; `runOnce` for "Back up now" | Writes a zip into the chosen folder and prunes old automatic backups |

`MaintenanceNotifier` posts on one channel (`maintenance`). Notification ids are stable per
component and schedule, and negative for deadlines. `ReminderNotifications` cancels them when an
item is done, deleted or renewed.

Debug builds include `RunMaintenanceCheckReceiver`, which triggers the check immediately:
```
adb shell am broadcast -n com.carthing.debug/com.carthing.debug.RunMaintenanceCheckReceiver
```

## Languages
The app is in English (default, and the fallback for every other language), Czech and Slovak.
`AppContainer.language` follows the per-app locale. Pure-logic objects never read the locale; the
language is passed in.

Names the app supplies (default components, service quick picks, deadline presets) are `DefaultName`
entries with a label per language. They are stored as plain text, so `DefaultNameRelabeler` rewrites
them when the language changes:
- A stored name is relabelled only if its **trimmed** text **exactly** equals (case-sensitive) a default in any of the three languages. Anything else, and all free-text notes, is left alone.
- It covers component names, service entry types and deadline titles, in one transaction.
- The last language it relabelled to is kept in the `language` preferences. Nothing is written to the database when it is unchanged.
- On the first run, with no language stored, only the English labels count as defaults: data from 0.3.0 can only hold English defaults, so a Czech or Slovak one was typed by hand.
- Triggers: `MainActivity.onCreate` (a per-app language change recreates the activity), the daily `MaintenanceCheckWorker`, and right after a backup is restored (always a full relabel).

Everything else the app shows is a string resource:
- `res/values/strings.xml` is English, `values-cs/` Czech and `values-sk/` Slovak. `res/resources.properties` marks `values/` as English, and `generateLocaleConfig` in `app/build.gradle.kts` turns the folders into the per-app language list in Android 13+ settings.
- Text helpers (`formatKm`, `formatDays`, `MaintenanceStatus.describe`, `DeadlineStatus.describeExpiry`, …) take `Resources` first. Each has a `@Composable` overload without it for screens; workers and notifications pass `context.resources`.
- A ViewModel that picks a message returns a `UiText` (resource id plus args), and the screen calls `resolve()`.
- Sentences are whole format strings with numbered args (`%1$s`, `%2$s`), never glued fragments, so a translation can reorder words. Counts use `<plurals>`. Czech and Slovak need `one` (1), `few` (2–4), `many` (fractions only, so never hit by whole numbers) and `other` (0, 5+). When a phrase puts the count in another case, it gets its own plurals, e.g. `deadline_expired_days_ago` ("před 5 dny") next to `days` ("5 dní").
- Units: English uses `L` and `L/100km`; Czech and Slovak use `l` and `l/100 km`.

To add a string, put it in all three `strings.xml` files; `TranslationsTest` fails when a name is missing or extra in `cs` or `sk`, or a Czech or Slovak plural lacks a quantity. Mark brand names `translatable="false"`. To add a language, add a `values-xx/strings.xml` with every string, a `Language` entry and its `DefaultName` labels, and add the folder to `TranslationsTest`.

## Storage
- Room database `carthing.db`.
- Photos: `filesDir/attachments/<uuid>.jpg`, see [BACKUP.md](BACKUP.md#photos).
- SharedPreferences: `backup_settings` (backup bookkeeping and folder URI), `ui_settings` (wallpaper colours) and `language` (the language default names were last relabelled to).
- Android's own backup (`res/xml/data_extraction_rules.xml`, `backup_rules.xml`) includes the database and preferences. It leaves out `backup_settings.xml`, because a folder permission only works on the same device, and it doesn't include photos.

## Build variants
- `debug`: application id `com.carthing.debug`, so it installs next to the release app.
- `release`: R8 with resource shrinking, and split per ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`) because the bundled ML Kit model ships native code.
