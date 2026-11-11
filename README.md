# ClockShift

<img src="play-store-assets/icon-rounded.png" alt="ClockShift icon" width="96" align="right">

A minimal Android work time tracker. Check in, check out, and always know your overtime balance. Everything stays on your device: no account, no ads, no analytics, and no Internet permission.

## Features

- **Check in / out** — one tap to start and finish, with live break tracking. A forgotten check-out is flagged and easy to fix.
- **Standard days** — days you don't log count as your usual hours, so you only record the days that were different.
- **Manual entries** — add, edit or delete work blocks and breaks on any past day, and mark days off (vacation, holiday, sick).
- **Overtime overview** — balance for all time (default), this week, this month, last month, this year or a custom range, with a bar chart and per-period breakdown.
- **History** — every day since tracking started, grouped by month, with the daily delta.
- **Schedule versions** — changing your work week applies from today, so past days keep their old targets.
- **Reminders** — a work start reminder an hour after your planned start if you haven't checked in, and an evening (work end) reminder offering to log the standard day or customise it, without opening the app. Each can be switched on or off under Settings › Notifications.
- **Backup and restore** — save all data to a JSON file anywhere (including storage apps) and restore it, also from the setup wizard on a new phone.
- **Setup wizard** — work week, starting overtime balance, reminders and automatic check-in in five steps.

### ClockShift Pro (subscription)

- **Automatic check-in/out** — by office Wi‑Fi (checks in when the phone joins one of your networks) or by location (a geofence around your workplace). Either acts on its own with undo, or asks first. Departures only count after a configurable time away, and you're checked out at the time you left.
- **Timesheet export** — PDF (printable, with summary and weekly separators) or CSV (decimal hours for spreadsheets) for any period, optionally with every block; shared or saved to a file. Open it from the Overview's share icon or Settings.
- **Daily backup** — a WorkManager job writes a dated backup to a folder you pick (on the phone or in a storage app such as Nextcloud/ownCloud) and keeps the last 7, or replaces a single file for apps without folder access such as Google Drive. Failures show in Settings and as a notification.
- **Smarter reminders** — the work start reminder learns when you usually start and waits as long as you choose, and a "daily hours reached" reminder offers to check you out.

Debug builds have an "Unlock Pro for testing" switch in Settings. Release builds need a subscription product with id `clockshift_pro` (monthly and/or yearly base plans) in the Play Console. See [IDEAS.md](IDEAS.md) for further Pro ideas.

The full product spec and overtime rules are in [PLAN.md](PLAN.md).

## Stack

- Kotlin, Jetpack Compose, Material 3 (dynamic colour on Android 12+, light and dark)
- Room (KSP) for local persistence, Navigation Compose, manual dependency injection
- Google Play Billing (Pro subscription) and Google Play services location (geofencing); the Internet permission that Billing's telemetry would add is removed in the manifest
- Pure-Kotlin `OvertimeCalculator`, `AutoTrackRules`, `SmartReminderPlanner`, `TimesheetBuilder`, `TimesheetCsv` and `BackupCodec` with JVM unit tests
- WorkManager for the daily backup; PDFs are drawn with the platform's `PdfDocument`
- minSdk 26, compile/target SDK 36
- Warnings are errors (`allWarningsAsErrors`, lint `warningsAsErrors`)

## Building

Requires JDK 17 and the Android SDK (platform 36). Point `local.properties` at the SDK:

```
sdk.dir=/path/to/android/sdk
```

Then:

```sh
./gradlew :app:assembleDebug      # APK in app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest  # unit tests
./gradlew :app:lintDebug          # lint
```

A prebuilt debug APK is kept in [dist/](dist/).

### Release bundle for the Play Store

```sh
./gradlew :app:bundleRelease      # app/build/outputs/bundle/release/app-release.aab
```

Release builds are minified and shrunk. They are signed with an upload key read from `keystore/keystore.properties` (keys `storeFile`, `storePassword`, `keyAlias`, `keyPassword`). The `keystore/` folder is gitignored and must be backed up separately. Without it the bundle is built unsigned. Raise `versionCode` in `app/build.gradle.kts` for every upload.

## Project layout

```
app/src/main/java/com/github/guidohu/clockshift/
  data/      Room entities, DAOs, migrations, repository, settings store
  domain/    Models, OvertimeCalculator, entry operations, formatting
  autotrack/ Automatic check-in: Wi-Fi/geofence registration, presence state, receivers
  backup/    Backup format (JSON), restore, daily backup job
  export/    Timesheet PDF and CSV writers, sharing
  notify/    Reminder planning and scheduling, notifications, boot/time-change receivers
  pro/       Pro subscription (Google Play Billing)
  ui/        Today, History, Overview, Day editor, Settings, Notifications, Setup wizard, theme
docs/                 Privacy policy (publish with GitHub Pages)
play-store-assets/    Icon source, 512 px icon and feature graphic for the Play Store
```

## Privacy

ClockShift stores your schedule and entries in a local database and never sends them anywhere. Automatic check-in (Pro) uses location permission to read the Wi‑Fi name or for the geofence; that stays on the device too. See the [privacy policy](docs/privacy-policy.html). Android's own backup may copy the database to your Google account if you have device backup turned on; this is explained in the policy.

## Contributing

Issues and pull requests are welcome at [github.com/guidohu/clockshift](https://github.com/guidohu/clockshift/issues).
