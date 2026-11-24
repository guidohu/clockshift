# Play Store listing (en)

Paste each block into Play Console → Grow users → Store presence → Main store listing.
Limits: app name 30, short description 80, full description 4000 characters.

## App name

```
ClockShift: Overtime Tracker
```

Alternatives: `ClockShift: Work Hours Tracker`, `ClockShift – Flexitime Tracker`

## Short description

```
Track work hours and always know your overtime balance. No ads, no account.
```

## Full description

```
ClockShift is a work hours tracker that keeps a running overtime balance, so you always know how many hours you are ahead or behind. No account, no ads, and your data never leaves your phone.

Check in when you start, check out when you finish, and ClockShift does the maths. Forget to log a day? Your usual hours are assumed, so you only record the days that were different.

<b>Check in and out with one tap</b>
• A live timer shows today's progress and when you'll reach your hours
• Start and end breaks as you go
• Forgot to check out? ClockShift notices and lets you set the right end time in seconds

<b>Your overtime balance, always up to date</b>
• See your balance the moment you open the app
• Overview for all time, this week, this month, last month, this year or any custom range
• Bar chart of overtime per day, week or month, with worked time against target for each period
• Already have flexitime hours? Enter your starting balance and start date

<b>Standard days, no daily effort</b>
• Set your working days and usual hours once
• Days you don't log count as a standard day, so your balance stays right
• Different hours on some weekdays? Set each day on its own
• Change your schedule later and past days keep the hours they had

<b>Fix any day</b>
• Add, edit or delete work blocks and breaks on any past day
• Mark vacation, holidays or sick days as days off
• Work on a weekend or a day off counts entirely as overtime
• Swipe actions, multi-select and undo make corrections painless

<b>Reminders that save you work</b>
• A nudge if you haven't checked in an hour after your usual start
• An evening reminder lets you log a standard day straight from the notification, or adjust it
• Choose the times, or turn each reminder off

<b>Backup and restore</b>
• Save all your data to a file, on your phone or in a storage app
• Restore it on a new phone, right from the setup

<b>ClockShift Pro (optional subscription)</b>
• Automatic check-in and check-out when your phone joins the office Wi‑Fi or arrives at work. It acts on its own with undo, or asks first
• PDF and CSV timesheets for any period, ready to share with your employer
• Daily automatic backups to a folder or a cloud storage app
• A start reminder that learns when you usually begin, and a reminder to check out once you've worked your hours
Everything else is free, without limits.

<b>Private by design</b>
• Everything stays on your device
• No account, no sign-in
• No ads, no analytics, no tracking
• ClockShift doesn't even have Internet access
• Location is only used for automatic check-in, on your phone, and never shared
• Open source: github.com/guidohu/clockshift

<b>Made for Android</b>
• Material You design with dynamic colour on Android 12 and newer
• Light and dark theme
• Follows your 12- or 24-hour clock and your first day of the week

Whether you have flexible hours, a fixed schedule or something in between, ClockShift keeps your balance honest and stays out of your way.
```

## Screenshots (phone)

Upload in this order from `listing/`. Most people only see the first two or three.

1. `screenshot-1-today.png`: Always know your overtime
2. `screenshot-2-overview.png`: Ahead or behind? See it.
3. `screenshot-3-history.png`: Log only the days that were different
4. `screenshot-4-auto.png`: Checks you in automatically (Pro)
5. `screenshot-5-timesheet.png`: Timesheets in one tap (Pro)
6. `screenshot-6-day.png`: Fix any day in seconds
7. `screenshot-7-private.png`: Private by design (dark theme)

Feature graphic: `listing/feature-graphic.png` (1024 × 500). App icon: `icon-512.png`.

## Regenerating the graphics

The screens are rendered by `StoreAssetsTest` (Paparazzi) with demo data and a fixed clock, then
framed and captioned by `make_listing_graphics.py` (needs Pillow and macOS's Avenir Next font).

```sh
export ANDROID_HOME=<sdk root> ANDROID_SDK_ROOT=<sdk root>   # Paparazzi reads these, not local.properties
./gradlew :app:recordPaparazziDebug -PstoreAssets --tests '*StoreAssetsTest*'
python3 play-store-assets/make_listing_graphics.py
```

`-PstoreAssets` compiles against SDK 35 (Paparazzi 1.3.4 can't render SDK 36 yet; the platform
must be installed) and is the only way StoreAssetsTest runs, so normal test runs skip it. The
renders land in `app/src/test/snapshots/images/`. Captions and order live in `SHOTS` at the top of
the script.
