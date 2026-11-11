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

## Suggested order

1. Automatic check-in/out + smart reminders (first Pro release). *Done.*
2. Export (CSV + PDF) and file backup *(done)*, widget/tile.
3. Time-off balances.
4. The rest, by user demand.
