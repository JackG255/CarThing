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
