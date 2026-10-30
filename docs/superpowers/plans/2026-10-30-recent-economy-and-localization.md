# Recent Economy and Czech/Slovak Translations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the spiky "Last fill-up" economy tile with a 3-segment "Recent" figure, and ship the app in English, Czech and Slovak, including relabelling default names already stored in the owner's data.

**Architecture:** Part 1 is a change to `VehicleStats` plus one label. Part 2 has three parts: a pure-logic `DefaultName` enum (stable keys with en/cs/sk names) that every default list reads from; a `DefaultNameRelabeler` that rewrites stored names that still exactly match a default; and moving all other UI text into `res/values{,-cs,-sk}/strings.xml`. Code that formats text outside Compose takes `Resources`, and ViewModels hand the UI `UiText` (a resource id plus args) instead of strings.

**Tech Stack:** Kotlin 2.0, Jetpack Compose, Room, WorkManager, AGP 8.13 (`generateLocaleConfig`), JUnit 4 + Robolectric 4.14.

**Spec:** `docs/superpowers/specs/2026-10-30-recent-economy-and-localization-design.md`

## Global Constraints

- Languages: English (default `values/`, fallback for every other language), Czech (`values-cs`), Slovak (`values-sk`).
- No schema change, no backup format change.
- A stored name is relabelled only if `trim(name)` exactly equals (case-sensitive) a default in one of the three languages. Free-text notes are never touched.
- Pure-logic objects (`data/maintenance`, `data/deadlines`, `data/ocr`, `VehicleStats`, `FuelEconomy`) stay free of Android `Context`/`Resources`. The language is passed in, never read from `Locale.getDefault()` inside logic.
- Persist codes/keys, never translated sentences.
- Plurals use `<plurals>`. Czech and Slovak define `one`, `few`, `many` and `other` for every plural.
- Wire new services in `AppContainer`. Match the existing compact style and short KDoc.
- Every commit message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Test command: `./gradlew testDebugUnitTest` (run from the repo root).

## Review Focus

1. **A hand-edited name that differs only in case** ("brake fluid") must not be relabelled. Test in Task 4.
2. **Switching EN → CS → EN** must give back exactly the original English names, and a second run with no language change must write nothing. Test in Task 4.
3. **Restoring a backup made in Czech on an English phone** must relabel even though the stored "last language" didn't change. Test in Task 4.
4. **Czech/Slovak plurals:** cs 1 den / 2 dny / 5 dní, sk 1 deň / 2 dni / 5 dní. Test in Task 5.
5. **The first launch after upgrading from 0.3.0** must not rewrite Czech/Slovak names the owner typed by hand ("Oprava", "Pojištění"), because 0.3.0 only generated English defaults. Test in Task 4.
6. **The English auto-backup error sentence already saved on the owner's phone** must show as the generic localized message, not as English text and not as a crash. Test in Task 6.

---

### Task 1: "Recent" economy tile

**Files:**
- Modify: `app/src/main/java/com/carthing/data/VehicleStats.kt`
- Modify: `app/src/main/java/com/carthing/ui/vehicles/VehicleDetailScreen.kt:183`
- Modify: `CHANGELOG.md`
- Test: `app/src/test/java/com/carthing/data/VehicleStatsTest.kt`

**Interfaces:**
- Produces: `VehicleStats.recentLitersPer100Km: Double?` (replaces `lastLitersPer100Km`), `VehicleStats.RECENT_SEGMENTS = 3`.

- [ ] **Step 1: Write the failing tests** in `VehicleStatsTest`. Rename the existing `lastLitersPer100Km` assertions to `recentLitersPer100Km`: `emptyHistory` stays null, and in `averageIsDistanceWeighted` both segments fall in the window, so the expected value becomes `46.0 / 700.0 * 100.0`. Add:

```kotlin
@Test fun recentUsesLastThreeSegmentsDistanceWeighted() {
    // Segments: 50/500 (10.0), 30/500 (6.0), 20/200 (10.0), 70/1000 (7.0); the first falls out of the window.
    val fuel = listOf(fill(1, 0.0, 40.0, null), fill(2, 500.0, 50.0, null), fill(3, 1000.0, 30.0, null),
        fill(4, 1200.0, 20.0, null), fill(5, 2200.0, 70.0, null))
    assertEquals(120.0 / 1700.0 * 100.0, VehicleStats.from(fuel, emptyList()).recentLitersPer100Km!!, 1e-9)
}

@Test fun recentWithOneSegmentEqualsThatSegment() {
    val fuel = listOf(fill(1, 0.0, 40.0, null), fill(2, 500.0, 35.0, null))
    assertEquals(7.0, VehicleStats.from(fuel, emptyList()).recentLitersPer100Km!!, 1e-9)
}
```

- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests com.carthing.data.VehicleStatsTest`. Expected: FAIL (unresolved `recentLitersPer100Km`).
- [ ] **Step 3: Implement.** Replace `lastLitersPer100Km` with `recentLitersPer100Km`, computed as `segments.takeLast(RECENT_SEGMENTS)` summed liters / summed distance × 100, or null when there are no segments. KDoc: "Distance-weighted over the last [RECENT_SEGMENTS] economy segments (fewer when there aren't that many); null until two full fills exist." Add `const val RECENT_SEGMENTS = 3` to the companion. In `VehicleDetailScreen`, the second `Stat` becomes `"Recent"` with `stats.recentLitersPer100Km`.
- [ ] **Step 4: Run** the same test command. Expected: PASS. Then run `grep -rn lastLitersPer100Km app docs`. Expected: no hits.
- [ ] **Step 5:** Under Unreleased in CHANGELOG, add: "Fuel economy: the "Last fill-up" figure is now "Recent", averaged over the last 3 full-to-full fills, so one over- or under-filled tank no longer causes a spike. The fuel list still shows each fill's own figure." Commit: `Recent fuel economy over the last 3 fills`.

### Task 2: `Language` and the `DefaultName` table

**Files:**
- Create: `app/src/main/java/com/carthing/data/Language.kt`
- Create: `app/src/main/java/com/carthing/data/maintenance/DefaultName.kt`
- Test: `app/src/test/java/com/carthing/data/maintenance/DefaultNameTest.kt`

**Interfaces:**
- Produces:
  - `enum class Language { EN, CS, SK; companion object { fun of(locale: java.util.Locale): Language } }` — `cs`→CS, `sk`→SK, anything else→EN.
  - `enum class DefaultName { … ; fun label(language: Language): String; val all: List<String> /* en, cs, sk, distinct */; companion object { fun find(name: String): DefaultName? /* matches trim(name) exactly against any language */ } }`

- [ ] **Step 1: Write the failing tests**: `Language.of(Locale("cs","CZ")) == CS`, `of(Locale("sk")) == SK`, `of(Locale.GERMAN) == EN`, `of(Locale.US) == EN`; `DefaultName.find("Brzdová kapalina") == BRAKE_FLUID`; `find("  Brake fluid ") == BRAKE_FLUID`; `find("brake fluid") == null`; `find("Brake fluid!") == null`; `BRAKE_FLUID.label(Language.SK) == "Brzdová kvapalina"`. Uniqueness: build a map from every `entry.all` name to its entry and assert that no name maps to two entries, and that within each language the labels are distinct.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests com.carthing.data.maintenance.DefaultNameTest`. Expected: FAIL (unresolved).
- [ ] **Step 3: Implement** both files. The enum takes `(en, cs, sk)` constructor params, and the entry names are the stable keys. Use exactly these values. They are the shipped defaults, and the owner proofreads them in review:

| Entry | en | cs | sk |
|---|---|---|---|
| ENGINE_OIL | Engine oil & filter | Motorový olej a filtr | Motorový olej a filter |
| AIR_FILTER | Air filter | Vzduchový filtr | Vzduchový filter |
| CABIN_FILTER | Cabin filter | Kabinový filtr | Kabínový filter |
| BRAKE_FLUID | Brake fluid | Brzdová kapalina | Brzdová kvapalina |
| BRAKE_PADS | Brake pads | Brzdové destičky | Brzdové platničky |
| COOLANT | Coolant | Chladicí kapalina | Chladiaca kvapalina |
| SPARK_PLUGS | Spark plugs | Zapalovací svíčky | Zapaľovacie sviečky |
| TIMING_BELT | Timing belt | Rozvodový řemen | Rozvodový remeň |
| TIRES | Tires | Pneumatiky | Pneumatiky |
| TIRE_PRESSURE | Tire pressure | Tlak v pneumatikách | Tlak v pneumatikách |
| BATTERY | Battery | Akumulátor | Akumulátor |
| WIPER_BLADES | Wiper blades | Lišty stěračů | Lišty stieračov |
| OIL_CHANGE | Oil change | Výměna oleje | Výmena oleja |
| BRAKES | Brakes | Brzdy | Brzdy |
| INSPECTION | Inspection | Prohlídka | Prehliadka |
| FILTERS | Filters | Filtry | Filtre |
| WIPERS | Wipers | Stěrače | Stierače |
| REPAIR | Repair | Oprava | Oprava |
| TECHNICAL_INSPECTION | Technical inspection (STK) | Technická kontrola (STK) | Technická kontrola (STK) |
| EMISSIONS | Emissions check | Měření emisí | Emisná kontrola |
| VIGNETTE | Motorway vignette | Dálniční známka | Diaľničná známka |
| INSURANCE | Insurance | Pojištění | Poistenie |

  KDoc on the enum: "Names the app supplies (default components, service quick picks, deadline presets) in every language. Entry names are stable keys; a stored name equal to any of an entry's labels is still a default and follows the app's language (see `DefaultNameRelabeler`)."
- [ ] **Step 4: Run** the tests. Expected: PASS.
- [ ] **Step 5: Commit**: `Default names in English, Czech and Slovak`.

### Task 3: Default lists, OCR and icons read from `DefaultName`

**Files:**
- Modify: `app/src/main/java/com/carthing/data/maintenance/DefaultSchedule.kt`, `data/deadlines/DeadlineStatus.kt` (`DeadlineTemplates`), `data/ocr/ServiceInvoiceParser.kt`, `data/repository/VehicleRepository.kt`, `data/AppContainer.kt`
- Modify: `ui/service/ServiceFormScreen.kt` (`COMMON_TYPES`, OCR fill), `ui/deadlines/DeadlineFormScreen.kt` / `DeadlinesSection.kt` (wherever `DeadlineTemplates.all` is shown), `ui/maintenance/ComponentIcons.kt`, `ui/vehicles/VehicleDetailScreen.kt` (`serviceIcon`)
- Create: `ui/common/Language.kt` (the composable helper)
- Modify: `docs/MAINTENANCE.md`, `docs/OCR.md`
- Test: `DefaultScheduleTest` (new, `app/src/test/java/com/carthing/data/maintenance/`), `ServiceInvoiceParserTest`, `ComponentIconsTest`, `RepositoryTest`

**Interfaces:**
- Consumes: `Language`, `DefaultName` (Task 2).
- Produces:
  - `DefaultSchedule.Template(val name: DefaultName, …)` and `DefaultSchedule.itemsFor(vehicleId: Long, language: Language): List<MaintenanceItem>`
  - `DeadlineTemplates.Template(val name: DefaultName, val repeatMonths: Int?)`
  - `ServiceInvoiceParser` result field `services: List<DefaultName>`
  - `VehicleRepository(db, language: () -> Language = { Language.EN })`
  - `@Composable fun currentLanguage(): Language` in `ui/common/Language.kt` = `Language.of(LocalConfiguration.current.locales[0])`
  - `val SERVICE_TYPES: List<DefaultName>` in `ServiceFormScreen.kt` = OIL_CHANGE, TIRES, BRAKES, INSPECTION, BATTERY, FILTERS, WIPERS, REPAIR (replaces `COMMON_TYPES`)

- [ ] **Step 1: Write the failing tests.**
  - `DefaultScheduleTest.itemsAreNamedInTheGivenLanguage`: `itemsFor(1, Language.CS).map { it.name }` starts with `"Motorový olej a filtr"` and has 12 items. Intervals are unchanged versus EN: zip the EN and CS lists and compare `replaceKm`/`replaceMonths`/`inspectKm`/`inspectMonths`.
  - `ServiceInvoiceParserTest`: change expected services from strings to `DefaultName` entries (e.g. `listOf(DefaultName.OIL_CHANGE, DefaultName.FILTERS)`).
  - `ComponentIconsTest.defaultNamesGetTheSameIconInEveryLanguage`: for each of the 12 component entries and each `Language`, `ComponentIcons.guess(entry.label(lang)).key == ComponentIcons.guess(entry.label(Language.EN)).key`. Also keep the existing English map test.
  - `RepositoryTest.newVehicleGetsDefaultsInItsLanguage`: `VehicleRepository(db) { Language.SK }.save(Vehicle(name = "Car"))`, then the maintenance items include `"Brzdová kvapalina"`.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest`. Expected: these tests FAIL (compile errors first).
- [ ] **Step 3: Implement.**
  - Templates hold a `DefaultName`, and `itemsFor` labels them in the given language.
  - `VehicleRepository` passes `language()` to `itemsFor`. `AppContainer` constructs it with `{ Language.of(context.resources.configuration.locales[0]) }`. Use the application context, which follows the per-app locale on Android 13+.
  - `ServiceInvoiceParser.SERVICES` keys become `DefaultName` entries; keep the keyword lists unchanged.
  - The service form shows `SERVICE_TYPES.map { it.label(currentLanguage()) }` and fills OCR services the same way. The "Work: " prefix stays literal until Task 8.
  - Deadline preset chips show `template.name.label(currentLanguage())` and save that label as the title.
  - In `ComponentIcons.RULES`, add the Slovak stems the new test needs (at least `"kvapalin"` for fluid, `"chladiac"` for coolant, `"kabín"` for air, `"sviečk"`/`"zapaľ"` for spark, `"remeň"` for belt, `"stierač"` for wipers). Update the KDoc to "English, Czech and Slovak stems".
  - `serviceIcon(type)` first maps `DefaultName.find(type)` to an icon (OIL_CHANGE/ENGINE_OIL→oil, TIRES→tire, INSPECTION/TECHNICAL_INSPECTION/EMISSIONS→fact check, BATTERY→battery), then falls back to the existing word rules.
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Docs, then commit.** `docs/MAINTENANCE.md`: default components are created in the app's language from `DefaultName`. `docs/OCR.md`: the parser returns `DefaultName` entries, and what it recognises is unchanged. Commit: `Default components, service types and deadline presets follow the app language`.

### Task 4: Relabel stored default names

**Files:**
- Modify: `data/dao/MaintenanceItemDao.kt`, `data/dao/ServiceEntryDao.kt`, `data/dao/DeadlineDao.kt`
- Create: `app/src/main/java/com/carthing/data/repository/DefaultNameRelabeler.kt`
- Modify: `data/AppContainer.kt`, `MainActivity.kt`, `notifications/MaintenanceCheckWorker.kt`, `ui/backup/BackupViewModel.kt` (after `archive.restore`)
- Modify: `docs/ARCHITECTURE.md`, `docs/BACKUP.md`
- Test: `app/src/test/java/com/carthing/data/repository/DefaultNameRelabelerTest.kt` (Robolectric, in-memory DB as in `RepositoryTest`)

**Interfaces:**
- Consumes: `DefaultName.all`, `DefaultName.label`, `Language`.
- Produces:
  - DAO: `@Query("UPDATE maintenance_items SET name = :to WHERE trim(name) IN (:from) AND name != :to") suspend fun relabel(from: List<String>, to: String): Int`. Add the same as `ServiceEntryDao.relabelType` on `service_entries.type` and `DeadlineDao.relabelTitle` on `deadlines.title`.
  - `class DefaultNameRelabeler(db: CarThingDatabase, prefs: SharedPreferences, language: () -> Language)` with:
    - `suspend fun relabel(): Int`: for every `DefaultName`, relabel all three tables from `entry.all` to `entry.label(language())` in one `withTransaction`, store `language().name` under `KEY_LANGUAGE`, and return the number of rows changed.
    - `suspend fun relabelIfLanguageChanged(): Int`: return 0 without touching the DB when the stored language equals `language()`; otherwise call `relabel()`.
    - **First run (nothing stored under `KEY_LANGUAGE`):** relabel from `listOf(entry.label(Language.EN))` only, never `entry.all`. Data from 0.3.0 can only contain app-generated defaults in English, so a Czech/Slovak default there (e.g. "Oprava", "Pojištění") was typed by hand. Every later run, and the forced `relabel()` after a restore, uses `entry.all`.
  - `AppContainer.relabeler`, using prefs `context.getSharedPreferences("language", MODE_PRIVATE)`.

- [ ] **Step 1: Write the failing tests** in `DefaultNameRelabelerTest`. Use a `var lang = Language.EN` captured by the lambda and seed one vehicle with:
  - items "Brake fluid", "  Coolant ", "brake fluid", "Brake fluid (rear)" and "Moje brzdy";
  - service entries typed "Oil change" and "Engine oil & filter";
  - a deadline titled "Motorway vignette" and one titled "Vignette AT".

  Tests:
  - `defaultsFollowTheLanguage`: with `lang = CS`, `relabel()` gives "Brzdová kapalina", "Chladicí kapalina", "Výměna oleje", "Motorový olej a filtr" and "Dálniční známka". "brake fluid", "Brake fluid (rear)", "Moje brzdy" and "Vignette AT" are unchanged.
  - `roundTripRestoresEnglish`: CS, then EN gives exactly "Brake fluid", "Coolant", "Oil change", "Engine oil & filter" and "Motorway vignette". The trimmed one comes back without its spaces, which is accepted.
  - `secondRunWithSameLanguageWritesNothing`: `relabelIfLanguageChanged()` with CS returns > 0, then returns 0 when called again.
  - `slovakFromCzech`: CS then SK gives "Brzdová kvapalina".
  - `firstRunOnlyTouchesEnglishDefaults`: with fresh prefs, seed a service type "Oprava", a deadline "Pojištění" and an item "Brake fluid".
    - `relabelIfLanguageChanged()` under EN leaves "Oprava" and "Pojištění" unchanged.
    - On another set of fresh prefs, the first run under CS turns "Brake fluid" into "Brzdová kapalina" and leaves "Oprava" unchanged.
  - `forcedRelabelAfterRestore`: run `relabel()` under EN, insert a Czech-named item directly via the DAO (simulating `replaceAll`), then call `relabel()` under EN. Expected: the item is English. This is the restore path.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests com.carthing.data.repository.DefaultNameRelabelerTest`. Expected: FAIL.
- [ ] **Step 3: Implement** the DAOs and the relabeler.
- [ ] **Step 4: Wire it up.**
  - `MainActivity.onCreate`: `lifecycleScope.launch { container.relabeler.relabelIfLanguageChanged() }`. A per-app language change recreates the activity, so `onCreate` sees it.
  - `MaintenanceCheckWorker.doWork`: call `relabelIfLanguageChanged()` before collecting reminders.
  - `BackupViewModel`: call `relabeler.relabel()` right after `archive.restore(...)`, on the IO dispatcher. Pass the relabeler through its existing factory.
- [ ] **Step 5: Run** `./gradlew testDebugUnitTest`. Expected: all PASS, including `BackupRepositoryTest` and `MigrationTest`, which are unchanged.
- [ ] **Step 6: Docs, then commit.**
  - `docs/ARCHITECTURE.md`: add a "Languages" section covering the relabel rule (exact match after trimming, case-sensitive, three tables, triggers: activity start, daily check, restore).
  - `docs/BACKUP.md`: a restore relabels default names to the current language, and the format is unchanged.
  - Commit: `Relabel stored default names when the app language changes`.

### Task 5: String resources, locale config, plurals, text helpers and notifications

**Files:**
- Create: `app/src/main/res/resources.properties` (`unqualifiedResLocale=en`), `app/src/main/res/values-cs/strings.xml`, `app/src/main/res/values-sk/strings.xml`
- Modify: `app/build.gradle.kts` (`androidResources { generateLocaleConfig = true }`), `app/src/main/res/values/strings.xml`
- Create: `app/src/main/java/com/carthing/ui/common/UiText.kt`
- Modify: `ui/common/Format.kt`, `ui/maintenance/StatusText.kt`, `ui/deadlines/DeadlineText.kt`, `notifications/MaintenanceNotifier.kt`, all callers of the changed helpers
- Modify: `docs/ARCHITECTURE.md` (Languages section: where text lives, how to add a string or a language), `CHANGELOG.md`
- Test: `app/src/test/java/com/carthing/ui/TranslationsTest.kt`, `app/src/test/java/com/carthing/ui/maintenance/StatusTextTest.kt`

**Interfaces:**
- Produces:
  - `data class UiText(@StringRes val id: Int, val args: List<Any> = emptyList())`, `fun UiText.resolve(res: Resources): String`, `@Composable fun UiText.resolve(): String`
  - Every text helper takes `res: Resources` as its first parameter and has a `@Composable` overload without it that uses `LocalContext.current.resources`. That covers `formatKm`, `formatLiters`, `formatEconomy`, `formatCostPerKm`, `formatDays`, `MaintenanceStatus.describe`, `Schedule.describe(kind)`, `ScheduleKind.verb`/`noun`, `ComponentStatus.describe` and `DeadlineStatus.describeExpiry`. `formatMoney`, `formatDate`, `parseDecimal` and `editableNumber` are unchanged.
  - Plurals `days`, `weeks`, `months`, `years` (each `%d …`).

- [ ] **Step 1: Write the failing tests.**
  - `TranslationsTest` is a plain JVM test that reads `src/main/res/values{,-cs,-sk}/strings.xml` relative to the module dir. It asserts that the set of `<string>` and `<plurals>` names in `cs` and in `sk` equals the default set, and that every `<plurals>` in `cs`/`sk` has items `one`, `few`, `many` and `other`. Skip `translatable="false"` strings.
  - `StatusTextTest` is Robolectric, using `ApplicationProvider.getApplicationContext<Context>().resources`:
    - `@Config(qualifiers = "cs")`: `formatDays(res, 1) == "1 den"`, `formatDays(res, 2) == "2 dny"`, `formatDays(res, 5) == "5 dní"`.
    - `@Config(qualifiers = "sk")`: `"1 deň"`, `"2 dni"`, `"5 dní"`.
    - Default: `"1 day"`, `"5 days"`, `formatDays(res, 21) == "3 weeks"`.
    - `@Config(qualifiers = "de")`: falls back to `"5 days"`.
    - `@Config(qualifiers = "cs")`: `DeadlineStatus(DueLevel.DUE_SOON, 12).describeExpiry(res)` contains `"12 dní"`.
    - `@Config(qualifiers = "cs")`: `MaintenanceNotifier.reminderTitle(res, "Brzdy", ScheduleKind.REPLACEMENT, DueLevel.OVERDUE)` contains `"Brzdy"` and no English words ("is overdue", "replacement"). Extract this pure function from `post` so the notification text is testable.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests 'com.carthing.ui.*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
  - Move every string in `Format.kt`, `StatusText.kt`, `DeadlineText.kt` and `MaintenanceNotifier.kt` to resources: the channel name and description, the overdue/due-soon titles, the backup reminder, and the "Last backup" text. Keep `formatDays`' thresholds (<14 days, <60 weeks, <730 months, else years).
  - Write sentences as whole format strings (`%1$s`, `%2$s`), never concatenated fragments, so cs/sk can reorder words. When a quantity follows a preposition that changes its case in Czech/Slovak (e.g. "ago" → "před 5 dny"), give that phrase its own plurals resource instead of reusing `formatDays`.
  - Units: en `km`, `L`, `L/100km`; cs/sk `km`, `l`, `l/100 km`.
  - The notifier uses `context.resources`.
  - Add the Czech and Slovak translations for everything moved. Then fix the compile errors at call sites, using the composable overload in Compose and `res` elsewhere.
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest`. Expected: PASS. Run `./gradlew assembleDebug`. Expected: BUILD SUCCESSFUL. Check that the merged manifest of `app/build/intermediates/merged_manifests/debug/…/AndroidManifest.xml` has an `android:localeConfig` attribute.
- [ ] **Step 5: Docs, then commit.** Add to CHANGELOG under Unreleased: "Czech and Slovak. The app follows the phone's language, or the per-app language in Android 13+ settings. Default component names, service types and deadline presets you haven't renamed switch language too." Commit: `String resources, plurals and Czech/Slovak for status text and notifications`.

### Task 6: Backup errors as codes

**Files:**
- Modify: `data/backup/BackupRepository.kt` (`InvalidBackupException`), `data/backup/BackupArchive.kt`, `data/backup/BackupSettings.kt`, `notifications/AutoBackupWorker.kt`, `ui/backup/BackupViewModel.kt`, `ui/backup/BackupMenu.kt`
- Test: `BackupArchiveTest`, `BackupRepositoryTest`, new `app/src/test/java/com/carthing/data/backup/BackupSettingsTest.kt`

**Interfaces:**
- Produces:
  - `class InvalidBackupException(val reason: Reason, detail: String? = null, cause: Throwable? = null) : Exception(detail ?: reason.name, cause)` with `enum class Reason { NOT_A_BACKUP, DAMAGED, TOO_LARGE, NEWER_VERSION, PHOTOS_MISSING }`. Keep the `fail(what)` detail as the exception message, for logs only.
  - `enum class AutoBackupError { FOLDER_UNAVAILABLE, OTHER }`. `BackupSettings.State.autoError: AutoBackupError?`. `recordAutoError(error: AutoBackupError)` stores `error.name`. On load, an unrecognised stored value (e.g. an old English sentence) → `OTHER`, and `null` → null.
  - `BackupViewModel.message: StateFlow<UiText?>`.

- [ ] **Step 1: Write the failing tests.**
  - Existing backup tests that expect `InvalidBackupException` also assert `.reason`: a truncated zip → DAMAGED, a JSON without a version → NOT_A_BACKUP, a newer version → NEWER_VERSION.
  - `BackupSettingsTest` (Robolectric):
    - Write `"auto_error" = "Can't write to the folder"` directly into the `backup_settings` prefs, then construct `BackupSettings`. Expected: `autoError == AutoBackupError.OTHER`.
    - `recordAutoError(FOLDER_UNAVAILABLE)` round-trips through a new instance.
- [ ] **Step 2: Run** `./gradlew testDebugUnitTest --tests 'com.carthing.data.backup.*'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
  - `AutoBackupWorker` maps `IOException`/`SecurityException` → FOLDER_UNAVAILABLE and anything else → OTHER.
  - `BackupViewModel` emits `UiText` for every message. "Couldn't save the backup" and "Couldn't read the file" no longer append `e.message`; log it under `BuildConfig.DEBUG` instead.
  - `BackupMenu` resolves the messages and moves all of its own text to resources, plus cs/sk. `PHOTOS_MISSING` uses a plural.
- [ ] **Step 4: Run** `./gradlew testDebugUnitTest`. Expected: PASS.
- [ ] **Step 5: Commit**: `Backup errors as codes, translated in the UI`.

### Tasks 7–9: Move screen text to resources

Each of these three tasks has the same shape and touches its own files only. In each one:
- move every user-visible literal to `values/strings.xml`, with cs and sk translations: `Text(...)`, labels, placeholders, `contentDescription`, snackbar and dialog text;
- replace ViewModel strings with `UiText`;
- don't translate log tags, routes or pref keys.

The tests are `TranslationsTest` (Task 5) plus the build.

- **Task 7, vehicles and shell:** `ui/vehicles/VehicleListScreen.kt`, `VehicleFormScreen.kt`, `VehicleDetailScreen.kt` (including the "Recent"/"Average" tiles and empty states), `VehicleDetailViewModel.kt`, `ui/common/Scaffolds.kt`, `ui/common/FormFields.kt`, `ui/CarThingNavHost.kt`. Commit: `Translate vehicle screens`.
- **Task 8, fuel, service, attachments:** `ui/fuel/FuelFormScreen.kt`, `ui/service/ServiceFormScreen.kt` (including the "Work: %s" note prefix), `ui/attachments/AttachmentsSection.kt`, `OdometerInput.kt`, `ReceiptPrompts.kt`, `EntryPhotos.kt`. Commit: `Translate fuel, service and photo screens`.
- **Task 9, maintenance and deadlines:** `ui/maintenance/MaintenanceTab.kt`, `MaintenanceItemFormScreen.kt`, `ComponentIcons.kt` (`ComponentIcon.label` becomes `@StringRes val label: Int`; keys unchanged), `ui/deadlines/DeadlinesSection.kt`, `DeadlineFormScreen.kt`. Commit: `Translate maintenance and deadline screens`.

Steps for each:
- [ ] **Step 1:** Run `grep -nE '"[A-Z][a-z][^"]*"' <the task's files>` and list the hits.
- [ ] **Step 2:** Move each user-visible hit to resources with cs and sk translations, and resolve it at its call site.
- [ ] **Step 3:** Rerun the grep. Expected: only non-user-facing literals remain (log tags, keys, routes, brand names). Then run `./gradlew testDebugUnitTest assembleDebug`. Expected: PASS / BUILD SUCCESSFUL.
- [ ] **Step 4:** Commit with the message above.

### Task 10: Final sweep

**Files:** whatever the sweep finds. `docs/superpowers/plans/2026-10-30-recent-economy-and-localization.md` (tick the boxes).

- [ ] **Step 1:** Run `grep -rnE '"[A-Z][a-z][^"]*"' app/src/main/java/com/carthing/ui app/src/main/java/com/carthing/notifications`. Expected: no user-visible English left. Fix any stragglers, with translations.
- [ ] **Step 2:** Run `./gradlew lintDebug`. Expected: no `MissingTranslation`, `ExtraTranslation` or `StringFormatInvalid` errors. Fix them if they appear; leave other pre-existing lint findings alone.
- [ ] **Step 3:** Run `./gradlew testDebugUnitTest assembleDebug`. Expected: all tests PASS, BUILD SUCCESSFUL.
- [ ] **Step 4:** Commit: `Translation sweep`. Report to the owner the list of cs/sk strings worth proofreading first: notification titles, status lines and the `DefaultName` table.
