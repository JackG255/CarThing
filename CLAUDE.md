# CarThing: notes for Claude

Android app (Kotlin, Compose, Room) that works as a car's service book. Start with `README.md` and
`docs/ARCHITECTURE.md`.

## Commands
- Tests: `./gradlew testDebugUnitTest` (JVM + Robolectric; there are no device tests)
- Build: `./gradlew assembleDebug` / `./gradlew assembleRelease` (unsigned without keys)
- Run the daily reminder check on a debug build: `adb shell am broadcast -n com.carthing.debug/com.carthing.debug.RunMaintenanceCheckReceiver`

## Rules
- Layers: entity → dao → repository → ViewModel → screen. Put business rules in repositories or the pure-logic objects (`data/maintenance`, `data/deadlines`, `data/ocr`, `FuelEconomy`, `VehicleStats`, `BackupPolicy`).
- No DI framework: wire new services in `data/AppContainer.kt`, and give ViewModels a `viewModelFactory`.
- Pass the time in (`nowMillis`, `clock`); logic must stay deterministic in tests.
- **Schema change:** bump the version, add a migration in `Migrations.kt`, and register it in both `CarThingDatabase.get` and `MigrationTest.openRoom()`. Commit the exported `app/schemas/*.json` and extend `MigrationTest`. Don't reference live constants inside a migration. See `docs/DATA_MODEL.md`.
- **Backed-up data change:** update the DTOs, `export`/`replaceAll`/`validate` in `BackupRepository`, and the format notes in `docs/BACKUP.md`.
- Never log user data (receipt text, backups) outside `BuildConfig.DEBUG`.
- Match the existing style: compact Kotlin and short KDoc that states units and what null means.
- Keep the docs in `docs/` up to date when behaviour they describe changes. Add user-facing changes to `CHANGELOG.md` under Unreleased.
