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
- **Evening reminder** — a notification offering to log the standard day or customise it, without opening the app.
- **Setup wizard** — work week, starting overtime balance and reminder in four steps.

The full product spec and overtime rules are in [PLAN.md](PLAN.md).

## Stack

- Kotlin, Jetpack Compose, Material 3 (dynamic colour on Android 12+, light and dark)
- Room (KSP) for local persistence, Navigation Compose, manual dependency injection
- Pure-Kotlin `OvertimeCalculator` with JVM unit tests
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
  notify/    Reminder scheduling, notification, boot/time-change receivers
  ui/        Today, History, Overview, Day editor, Settings, Setup wizard, theme
docs/                 Privacy policy (publish with GitHub Pages)
play-store-assets/    Icon source, 512 px icon and feature graphic for the Play Store
```

## Privacy

ClockShift stores your schedule and entries in a local database and never sends them anywhere. See the [privacy policy](docs/privacy-policy.html). Android's own backup may copy the database to your Google account if you have device backup turned on; this is explained in the policy.

## Contributing

Issues and pull requests are welcome at [github.com/guidohu/clockshift](https://github.com/guidohu/clockshift/issues).
