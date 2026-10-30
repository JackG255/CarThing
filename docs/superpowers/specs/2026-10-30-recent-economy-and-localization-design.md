# Recent fuel economy and Czech/Slovak translations

Two changes from outside review of the 0.3.0 app. They're independent; ship them in one release.

## Part 1: "Recent" fuel economy instead of "Last fill-up"

### Why
The vehicle screen shows two economy figures. **Average** is lifetime and distance-weighted over every
full-to-full segment (`VehicleStats.averageLitersPer100Km`), so a single bad fill barely moves it.
**Last fill-up** (`lastLitersPer100Km`) is one segment, so stopping at a different pump click (overfilling
or underfilling the closing full fill) shows up as a spike. The owner wants a short-term figure that
doesn't spike but still reacts to real changes (winter, new commute).

### What
- Replace `lastLitersPer100Km` with `recentLitersPer100Km`: distance-weighted economy over the **last 3**
  segments from `FuelEconomy.segments` (sum of liters / sum of distance × 100), i.e. the same formula as
  the lifetime average applied to the newest 3 segments. Fewer than 3 segments → use all there are; no
  segments → null. "Last" = last in `segments()` order (odometer order).
- The window size is a named constant (3), not a setting.
- The tile on `VehicleDetailScreen` is relabelled **"Recent"** (keeps the History icon).
- **Average stays lifetime.** **The per-entry numbers in the fuel list stay raw** (one segment each):
  that list is where the owner goes to spot the odd fill-up, so smoothing it would hide it. (Owner's
  decisions.)
- Tests in `VehicleStatsTest`; KDoc on the new field states the window and what null means; CHANGELOG.

### Non-goals
No change to how segments are formed (`FuelEconomy`), to cost per km, or to the fuel list.

## Part 2: Czech and Slovak translations

### Why
The app is English-only and every piece of text is hard-coded in Kotlin (`strings.xml` only holds
`app_name`). The owner uses it in Czech/Slovak context. The 0.3.0 English build is already installed on
the owner's phone with real data, so existing data must come along.

### Goals
1. **Languages: English (default/fallback), Czech, Slovak.** All user-facing text is translated: every
   screen, dialog, button, empty state, snackbar/error, the notification channel name/description and
   reminder notifications, formatting units (e.g. "L/100km", "km"), the default maintenance components
   and the service-type quick picks.
2. **Language choice follows Android:** the phone's language, plus the Android 13+ per-app language
   setting (system Settings → Apps → CarThing → Language), which needs a locale config declaring en/cs/sk.
   Any other language falls back to English. No in-app language picker. *(My call.)*
3. **Default names get translated in existing data, unless the owner renamed them.** (Owner's decision.)
   - A maintenance component whose `name` is still exactly one of the default component names (in any
     of the three languages) is renamed to that default's name in the current language.
   - Same for service records: a `ServiceEntry.type` that is exactly one of the quick-pick types (in any
     of the three languages) is rewritten to the current language. Owner chose this explicitly for past
     records too.
   - Service records whose type is a **default component name** are relabelled too: `markDone` and the
     component picker in `ServiceFormScreen` copy the component name into `type`, so these are
     app-generated, not typed by hand. *(My call, following the owner's "translate unless I renamed it".)*
   - **Deadline titles** that are exactly one of the `DeadlineTemplates` presets ("Technical inspection
     (STK)", "Emissions check", "Motorway vignette", "Insurance") get the same treatment, and the preset
     chips are translated. *(My call, same reasoning; otherwise Czech reminders would mix languages.)*
   - Anything else is user text and is never touched. Free-text notes are never touched.
   - It works in every direction: switching back to English turns them back into English.
4. **Translations are written by the builder** (Czech and Slovak); the owner proofreads in review.
   Use the natural terms a Czech/Slovak car owner and a service book would use (e.g. "STK", "olej a
   filtr"), not literal translations.

### Non-goals
- What receipt/invoice OCR *recognises* stays as it is (it already targets Czech receipts). Slovak
  receipt support is a separate piece of work. Its *output* is in scope: `ServiceInvoiceParser.SERVICES`
  returns `DefaultNames` keys instead of English strings, so the form fills in the current language's
  name, and the "Work: …" note prefix is a localized string. *(My call.)*
- No in-app language picker; no languages beyond the three.
- No schema change. No backup format change.
- Number/date formatting already follows the device locale (`ui/common/Format.kt`); leave it.

### Anti-goals (would be a failure even if it "works")
- A component or service record the owner renamed/typed by hand gets rewritten.
- Translated text is persisted somewhere it can't be re-translated later (e.g. an error stored as a
  sentence in prefs that then shows in the old language forever).
- Czech or Slovak screens with leftover English strings, or plural forms that read wrong
  ("1 dní", "5 dny"); Czech and Slovak need one/few/many/other plurals.
- Component icons stop matching after names are translated.

### How it's decided

**Default-name table (pure logic).** Add a pure-logic object (e.g. `data/maintenance/DefaultNames.kt`)
that holds, per default component, service quick-pick type and deadline preset, a stable key and its
name in en/cs/sk. `DefaultSchedule` templates, the `COMMON_TYPES` quick picks in `ServiceFormScreen`,
`DeadlineTemplates` and the OCR service list read their names from it for the current language. One
table, not several: names that appear in more than one list ("Tires", "Battery") are a single entry
with one translation per language, so a service type can never match two different translations.
Every name must be unique within a language (test this). Keeping these in Kotlin rather than `strings.xml` makes "recognise a
name in any language" a deterministic, JVM-testable lookup with no need to load other locales'
resources. *(My call.)* All other UI text goes into `res/values{,-cs,-sk}/strings.xml`.

**Relabelling existing data.** A repository operation, run in one DB transaction, that for a given
language rewrites every `MaintenanceItem.name` / `ServiceEntry.type` / `Deadline.title` that exactly
matches a known default (any language) to the current language's version. Matching is exact after trimming
surrounding whitespace, case-sensitive. *(My call.)* It runs:
- on app start / activity creation when the current app language differs from the last language it
  was applied for (store that language in prefs, so normal starts are a no-op); a per-app-language
  change recreates the activity without restarting the process, so checking only in
  `Application.onCreate` is not enough;
- at the start of the daily maintenance check worker, with the same language-changed check, so a
  reminder never uses the old language's name;
- after a backup restore (`replaceAll`), so a backup made in one language restores correctly in
  another. This is how backups stay language-neutral without a format change.
Wire it in `AppContainer`; pass the language in rather than reading the locale inside logic, so tests
stay deterministic.

**Text produced outside the UI.** Several data-layer and pure-logic places build English sentences:
`InvalidBackupException` messages (`BackupArchive`, `BackupRepository`), `IOException` messages in
`PhotoStore`/`FolderBackup` that reach the user, `describe()`/`noun()`/`describeExpiry()` used by
`MaintenanceNotifier`, and the auto-backup error stored under `BackupSettings.KEY_ERROR`. Turn these into
typed reasons/enums (or resource ids) that the UI/notifier maps to strings; persist a code, not a
sentence. The pure-logic objects must stay free of Android `Context`.

**Component icons.** `ComponentIcons` already matches English and Czech words; add Slovak words so
every default name in every language maps to the same icon as its English name (test this). The same
applies to the service-type icon matching in `VehicleDetailScreen`.

**Plurals.** Use `<plurals>` for anything with a count (days, months, km, photos missing, …).

### What's left to the builder
- String resource naming, file split, and how ViewModels/screens get strings (`stringResource` in
  Compose, `Context.getString` in the notifier/worker).
- The exact typed-error design for the data layer.
- How the locale config is produced (hand-written `res/xml/locales_config.xml` or AGP's
  `generateLocaleConfig`).
- Where the "last applied language" lives (existing prefs vs a new small store).

### Docs, changelog, tests
- `docs/ARCHITECTURE.md`: a short section on where text lives, how to add a string/language, and the
  relabelling rule. `docs/MAINTENANCE.md`: default component names are localized and relabelled.
  `docs/BACKUP.md`: restore relabels default names to the current language (format unchanged).
- `CHANGELOG.md` under Unreleased: Recent economy tile; Czech and Slovak.
- Tests: `DefaultNames` lookups and uniqueness; relabelling (defaults in each language are rewritten in
  components, service types and deadline titles; custom names and near-misses are not; idempotent); restore then relabel; icons for every default name in every language;
  a Robolectric check that `cs` and `sk` define every string/plural that the default `values` does
  (lint's MissingTranslation also catches this); notifier text in `cs`.
