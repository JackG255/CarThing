# CarThing

Android app for keeping a car's service book on your phone: odometer, fill-ups and fuel economy,
service history, maintenance reminders, deadlines (STK, vignette, insurance), receipt photos and
backups. Everything stays on the device; receipts are read offline.

**Download:** [latest release](https://github.com/JackG255/CarThing/releases/latest)

## Features
- **Vehicles** with odometer readings from fill-ups, services or plain updates, including one read from a dashboard photo.
- **Fuel:** fill-ups with full-to-full L/100km economy, cost totals and cost per km.
- **Service history** linked to tracked components, so logging a service restarts that component's schedule.
- **Maintenance:** each component has its own inspection and replacement intervals (km and/or months). The app predicts when each is due from how much you drive and sends reminders when one is due soon or overdue.
- **Deadlines:** date-based obligations (technical inspection, vignette, insurance) with renewal.
- **Photos:** receipts and invoices on entries, plus a service book gallery per vehicle.
- **Receipt reading:** on-device OCR fills in the fuel and service forms from a receipt or invoice photo.
- **Backups:** zip export/restore, weekly automatic backups to a folder you choose, and a reminder when the last backup is stale.

## Stack
Kotlin 2.0 · Jetpack Compose (Material 3) · Room · KSP · WorkManager · ML Kit text recognition ·
Coil · kotlinx.serialization · minSdk 26 / targetSdk 35

## Structure
```
app/src/main/java/com/carthing/
├── CarThingApp.kt / MainActivity.kt
├── data/
│   ├── AppContainer.kt       # manual dependency container
│   ├── CarThingDatabase.kt   # Room database (schema v8), Migrations.kt
│   ├── FuelEconomy.kt        # full-to-full L/100km calculation
│   ├── entity/  dao/  repository/
│   ├── maintenance/          # schedules, due status, reminder rules
│   ├── deadlines/            # deadline status and templates
│   ├── attachments/          # photo storage
│   ├── ocr/                  # receipt, invoice and odometer parsing
│   └── backup/               # backup format, archive, automatic backups
├── notifications/            # daily check and weekly backup workers
└── ui/                       # Compose screens by feature
```

## Docs
| | |
|---|---|
| [Architecture](docs/ARCHITECTURE.md) | Layers, dependency wiring, navigation, background work |
| [Data model](docs/DATA_MODEL.md) | Entities, relations, migrations, schema-bump checklist |
| [Maintenance](docs/MAINTENANCE.md) | How due status, predictions and reminders work |
| [OCR](docs/OCR.md) | Receipt, invoice and odometer reading |
| [Backup](docs/BACKUP.md) | Backup file format, automatic backups, restore |
| [Testing](docs/TESTING.md) | Test setup and patterns |
| [Releasing](docs/RELEASING.md) | Signing and publishing releases |
| [Contributing](CONTRIBUTING.md) | Build, branches, PRs, conventions |
| [Changelog](CHANGELOG.md) | What changed in each release |

## Build
Open the folder in Android Studio and let Gradle sync, or use the command line (JDK 17):

```
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # debug APK, installs as com.carthing.debug next to the release app
./gradlew assembleRelease     # R8-shrunk release APKs, one per CPU type
```

See [RELEASING.md](docs/RELEASING.md) for signing and publishing.
