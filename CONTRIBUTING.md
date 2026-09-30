# Contributing

## Setup
- Android Studio (recent stable) or JDK 17 with the Android SDK (compileSdk 35).
- Clone, open the folder and let Gradle sync. No keys are needed for debug builds.

```
./gradlew testDebugUnitTest   # run before every push
./gradlew assembleDebug       # installs as "CarThing Debug" (com.carthing.debug)
```

The debug build installs next to the release app, so testing on your own phone never touches real data.

## Workflow
1. Branch from `main` with a type prefix: `feat/…`, `fix/…`, `ui/…`, `build/…`, `ci/…`, `chore/…`.
2. Write commit subjects in the imperative, saying what changed for the user: "Add a service book photo gallery per vehicle".
3. Open a PR to `main`. CI (`.github/workflows/ci.yml`) runs the unit tests and builds the debug and unsigned release APKs. It must pass.
4. Merge with a merge commit. Releases are cut from tags; see [docs/RELEASING.md](docs/RELEASING.md).

## Conventions
- **Layering:** keep business rules in repositories or pure-logic objects, not in composables. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).
- **Dependencies:** add new services to `AppContainer` and give ViewModels a `viewModelFactory`.
- **Time:** pass `nowMillis` or a `clock` in; don't call `System.currentTimeMillis()` in logic.
- **Units:** store km and epoch millis, and format only in `ui/common/Format.kt`.
- **Schema changes:** follow the checklist in [docs/DATA_MODEL.md](docs/DATA_MODEL.md#checklist-changing-the-schema), and update the backup format too ([docs/BACKUP.md](docs/BACKUP.md#changing-the-format)).
- **Comments:** KDoc explains *why* and what a value means (units, null meaning). Keep them short.
- **Tests:** new logic gets unit tests; see [docs/TESTING.md](docs/TESTING.md).
- **Privacy:** never log receipt text, backup contents or other user data in release builds (`BuildConfig.DEBUG` guard).
- **Dependencies:** versions live in `gradle/libs.versions.toml`.
