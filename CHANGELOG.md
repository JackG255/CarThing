# Changelog

Each release is published on [GitHub Releases](https://github.com/JackG255/CarThing/releases).

## [Unreleased]
- Documentation: architecture, data model, maintenance, OCR, backup, testing and release guides.
- Fuel economy: the "Last fill-up" figure is now "Recent", averaged over the last 3 full-to-full fills, so one over- or under-filled tank no longer causes a spike. The fuel list still shows each fill's own figure.
- Czech and Slovak. The app follows the phone's language, or the per-app language in Android 13+ settings. Default component names, service types and deadline presets you haven't renamed switch language too, including after a backup is restored. A name you typed yourself is left alone unless it exactly matches a built-in name.
- Language can be picked inside the app (⋮ menu → Language): System default, English, Čeština or Slovenčina. On Android 13+ it is the same setting as the system's per-app language.

## [0.3.0] - 2026-09-29
- Icons for maintenance components, picked from the name or chosen by hand (#17). Database v8, backup format 4.

## [0.2.0] - 2026-09-29
First public release.
- Vehicles, fill-ups with full-to-full fuel economy, and service history (#3, #4). Fixes to economy and cost per km (#2).
- Maintenance tab with separate inspection and replacement schedules, driving-rate predictions and daily reminders (#5).
- Deadlines (STK, vignette, insurance), backup export/restore, automatic folder backups and backup reminders (#6).
- Odometer updates, missing-km hints, and clearing of stale reminders (#7).
- Status chips coloured by how much of the interval is left (#8).
- Receipt and invoice photos on entries (#9).
- On-device reading of fuel receipts (#10), service invoices (#11) and the dashboard odometer (#12).
- Service book photo gallery per vehicle (#13).
- Release setup: R8, signing, per-ABI APKs and CI (#14). App icon, teal Material You theme, splash screen and wallpaper-colours switch (#15).
- Signed APKs published as GitHub Releases on version tags (#16).
- Build upgrade and first tests (#1).

[Unreleased]: https://github.com/JackG255/CarThing/compare/v0.3.0...HEAD
[0.3.0]: https://github.com/JackG255/CarThing/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/JackG255/CarThing/releases/tag/v0.2.0
