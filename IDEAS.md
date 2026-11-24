# ClockShift Pro – feature ideas

ClockShift stays free, with no ads, account or analytics. A Pro subscription pays for development. Rules:

- Everything that is free today stays free. Pro adds power-user extras on top.
- Pro features must keep the privacy promise: no server, no account, data stays on the device.
- Purchases go through Google Play Billing, which talks to the Play Store app on the phone, so the app needs no Internet permission.

## Candidates

| # | Feature | Notes |
|---|---------|-------|
| 1 | **Automatic check-in/out** | Office Wi‑Fi or geofence around the workplace. *Done.* |
| 2 | **Smart check-in/out reminders** | Work start reminder learns your usual start (the basic one, an hour after the planned start, is free); "daily hours reached" check-out reminder. *Done.* |
| 3 | **Export and reports** | CSV and PDF timesheets for a week, month or custom range, shared through the share sheet. *Done.* |
| 4 | **Backup and restore to a file** | JSON/SQLite export and import through the system file picker; daily backup to a folder or file (Pro). Manual backup and restore are free. *Done.* |
| 5 | **Home-screen widget and Quick Settings tile** | One-tap check in/out and the balance at a glance (Glance + `TileService`). |
| 6 | **Time-off balances** | Day-off types (vacation, holiday, sick, comp time), yearly vacation allowance with days left, taking overtime as time off. |
| 7 | **Multiple jobs or projects** | Separate schedules and balances per employer, or project tags on blocks with per-project totals. |
| 8 | **Working-time limit warnings** | Break reminder after N hours; warning when daily or weekly hours pass a legal limit. |
| 9 | **Statistics** | Average start/finish times, monthly overtime trend, weekday heatmap, year-over-year comparison. |
| 10 | **Small extras** | Notes per day or block, rounding rules (5/15 min), accent colours and themes, app lock (fingerprint/PIN). |
| 11 | **Tip jar** | One-off "support development" purchase alongside Pro. |

## More candidates

These build on what the app has now (automatic check-in, export, daily backup, smart reminders). "Builds on" names the existing piece each one extends.

### Automatic check-in

| # | Feature | Notes | Builds on |
|---|---------|-------|-----------|
| 12 | **Several workplaces** | More than one geofence, and Wi‑Fi and location active together. Today there is one `GeoPoint` and one mode. Each place gets a name ("Office", "Client site", "Home office"). | `AutoTrackSettings`, `AutoTracker` |
| 13 | **Work location on blocks** | Blocks checked in automatically carry the place name. Gives home-office vs. office day counts per month and year (useful for tax returns and hybrid-work rules) and a column in the export. Needs #12. | `EntryEntity`, `Timesheet` |
| 14 | **Automatic breaks** | Leaving and coming back the same day is logged as a break instead of two work blocks, when the gap is short enough (configurable). | `AutoTrackRules` |
| 15 | **More triggers** | NFC tag on the desk (tap to check in/out), a Bluetooth device (car, dock, headset), or "on charger at the workplace Wi‑Fi". No location permission needed for NFC and Bluetooth. | `Probes`, `PresenceSignal` |
| 16 | **Automation intents** | Check in, check out and start/end break as exported intents for Tasker, MacroDroid, Samsung Routines and Bixby/Assistant shortcuts. Guarded by a setting so other apps cannot write entries unasked. | `ClockActions`, `Receivers` |

### Schedule and balance

| # | Feature | Notes | Builds on |
|---|---------|-------|-----------|
| 17 | **Rotating schedules** | Two- to four-week cycles (e.g. every other Friday off, shift rotas) instead of one repeating week. | `ScheduleVersion`, `ScheduleEditor` |
| 18 | **Future-dated schedule changes** | "From 1 January I work 80 %". Today a change always applies from today. Also editing or deleting a past version. | `ScheduleVersion.effectiveFrom` |
| 19 | **Public holidays** | Holiday calendars per country and region, shipped inside the app (no Internet), marked as days off automatically. Yearly data update comes with app updates. | `DayOffEntity` |
| 20 | **Half days off** | A day off currently covers the whole day. Half-day vacation or a doctor's visit should reduce the target by a part. Fits with time-off types (#6). | `DayOffEntity`, `OvertimeCalculator` |
| 21 | **Balance adjustments** | Dated corrections: overtime paid out, hours written off at year end, a manual fix. Today the only lever is the opening balance. Shown as their own rows in History and the export. | `openingBalanceMinutes`, `OvertimeCalculator` |
| 22 | **Balance rules** | Cap ("at most 40 h carried over"), yearly reset, or a threshold above which overtime counts at 125 %/150 % (also for weekend or night work). | `OvertimeCalculator` |
| 23 | **"When can I leave?"** | On Today: the time at which the daily target is reached, and the time at which the balance hits zero or a goal you set ("+10 h by the end of the month"). | `TodayScreen`, `SmartReminderPlanner` |
| 24 | **Earnings** | Hourly rate (per schedule version), so the balance and export also show money; overtime rate separately. | `Timesheet`, `OverviewScreen` |

### Export and backup

| # | Feature | Notes | Builds on |
|---|---------|-------|-----------|
| 25 | **Scheduled timesheets** | On the 1st of each month, last month's PDF or CSV is written to the backup folder, with a notification to share it. | `DailyBackupWorker`, `TimesheetExport` |
| 26 | **Timesheet header and sign-off** | Name, employer, employee number and a logo on the PDF; signature lines for employee and manager. | `TimesheetPdf` |
| 27 | **Excel export** | Real `.xlsx` with typed time cells and sum formulas. HR departments ask for this more often than CSV. | `ExportFormat` |
| 28 | **Import from other trackers** | CSV import with column mapping, for people switching apps. Lowers the barrier to trying ClockShift (could also be free). | `BackupCodec`, `EntryOps` |
| 29 | **Encrypted backups** | Passphrase-protected backup files, which matters most when the folder is a cloud storage app. | `BackupCodec` |
| 30 | **Backup history** | Choose how many daily backups to keep (now fixed at 7), plus one per month kept for a year. Restore picks from a list with date and entry count. | `BackupManager.KEEP` |
| 31 | **Calendar export** | Work blocks and days off as an `.ics` file, or written to a local device calendar so they show next to appointments. | `export/` |

### On the phone and beyond

| # | Feature | Notes | Builds on |
|---|---------|-------|-----------|
| 32 | **Live notification** | While checked in: an ongoing notification with a running timer, today's remaining time and break/check-out buttons (a Live Update on Android 16). Extends #5. | `Notifications` |
| 33 | **App shortcuts** | Long-press the icon for Check in/out, Start break, Add block. Cheap to build; a good free candidate. | `ClockActions` |
| 34 | **Wear OS** | Tile and complication with the check-in state and balance; check in/out from the watch. Syncs over the Wearable Data Layer, so still no Internet permission. | `ClockActions` |
| 35 | **Days off from the calendar** | Reads the device calendar (optional permission) and suggests marking days with an all-day "Vacation"/"Out of office" event as off. | `DayOffEntity` |
| 36 | **Weekly and monthly summary** | A Monday-morning or month-end notification: hours worked, delta, balance, and days that still have an open check-out. | `ReminderScheduler`, `OvertimeCalculator` |
| 37 | **Year in review** | A shareable card: hours worked, longest day, usual start time, overtime earned and taken. Uses the same numbers as Statistics (#9). | `OverviewScreen`, `DeltaBarChart` |

## Suggested order

1. Automatic check-in/out + smart reminders (first Pro release). *Done.*
2. Export (CSV + PDF) and file backup *(done)*, widget/tile.
3. Time-off balances, together with half days (#20) and public holidays (#19), since all three touch the day-off model.
4. Small additions to finished features: automatic breaks (#14), "When can I leave?" (#23), scheduled timesheets (#25), timesheet header (#26), backup history (#30).
5. Balance adjustments (#21) and future-dated schedule changes (#18). Both fill gaps users will hit after a year of use.
6. Several workplaces with location tags (#12, #13).
7. The rest, by user demand.
