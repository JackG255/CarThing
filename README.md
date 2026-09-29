# CarThing

Android app for tracking vehicle data: odometer, fueling history, fuel economy, and service history.

## Stack
Kotlin 2.0 · Jetpack Compose (Material 3) · Room · KSP · minSdk 26 / targetSdk 35

## Structure
```
app/src/main/java/com/carthing/
├── MainActivity.kt
├── data/
│   ├── CarThingDatabase.kt
│   ├── FuelEconomy.kt        # full-to-full L/100km calculation
│   ├── entity/               # Vehicle, FuelEntry, ServiceEntry
│   └── dao/
└── ui/
```

## Build
Open the folder in Android Studio and let Gradle sync.

- **Debug** (`./gradlew assembleDebug`) installs as `com.carthing.debug` ("CarThing Debug"), next to
  the release app, so testing never touches real data.
- **Release** (`./gradlew assembleRelease`) is shrunk with R8 and split per CPU type; phones use
  `app/build/outputs/apk/release/app-arm64-v8a-release.apk`.
- CI (GitHub Actions) runs the unit tests and builds both on every push to `main` and every PR.

### Release signing
Release builds are signed when `keystore.properties` exists in the project root (git-ignored):

```properties
storeFile=C:/Users/<you>/.carthing/carthing-release.jks
storePassword=...
keyAlias=carthing
keyPassword=...
```

or when the `CARTHING_KEYSTORE`, `CARTHING_KEYSTORE_PASSWORD`, `CARTHING_KEY_ALIAS` and
`CARTHING_KEY_PASSWORD` environment variables are set. Without them the release APK is unsigned.

**Keep the keystore and its password backed up.** Updates must be signed with the same key;
losing it means uninstalling the app (restore data from a CarThing backup) to install a new build.

### Publishing a release
Tag a commit on `main` and push the tag:

```
git tag v0.2.0
git push origin v0.2.0
```

The Release workflow runs the tests, builds a signed APK (version name and code come from the tag:
`v1.2.3` → `1.2.3` / `10203`, so each tag must be higher than the last) and publishes it on
[Releases](https://github.com/JackG255/CarThing/releases/latest). Share that link: it always points
to the newest build. Signing uses the `CARTHING_*` repository secrets.

Trigger the daily maintenance check on a debug build:
`adb shell am broadcast -n com.carthing.debug/com.carthing.debug.RunMaintenanceCheckReceiver`
