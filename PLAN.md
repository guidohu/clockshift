# ClockShift – Product & Implementation Plan

A minimal work-time tracker. Core promise: *check in, check out, and always know your overtime balance.*

## 1. Principles

1. **Zero-effort default.** If the user logs nothing, the configured standard day is assumed (overtime delta = 0). Logging only matters when a day deviates.
2. **One primary action per screen.** Today = check in/out. Overview = read the balance. Day = fix a day.
3. **Everything is correctable.** Any past day can be edited, marked as day off, or reset to standard.
4. **Honest maths.** Past schedule changes never rewrite history. Today is not counted while the user is still checked in.
5. **Offline, private, no accounts.** All data stays on the device.

## 2. Domain model

| Concept | Description |
|---|---|
| **Schedule version** | Seven `DaySchedule`s (Mon–Sun) valid from an effective date. Each: `working` flag, `start`, `end`, `break` → `target = end − start − break` (0 if not working). First version is effective from the epoch so back-filling always works; saving settings later creates a version effective *today*. |
| **Entry** | One work session: start & end (local date-time), break minutes, optional note. Multiple entries per day are allowed (e.g. morning/afternoon). An entry with no end is the *active* session (checked in); it also stores `breakStartedAt` while on a break. |
| **Day off** | Marks a date as target = 0 (vacation, holiday, sick). Work logged on such a day is pure overtime. |
| **Settings** | Setup done flag, tracking start date, opening balance (minutes, may be negative), reminder on/off, reminder time. |

### Overtime rules (per day `d`, from `trackingStart` to `today`)

* `target(d)` = 0 if day off or non-working weekday, else schedule target valid on `d`.
* `worked(d)` = sum of completed entries' net time (end − start − break, never negative).
* Past day, no entries, not day off → **assumed standard day**: `worked = target` (delta 0), flagged *assumed*.
* Today: counted only when it has completed entries (or is a day off) and no active session. While checked in, today is excluded and shown separately as "in progress".
* `delta(d) = worked(d) − target(d)`; **balance(range) = Σ delta**; *All time* additionally includes the opening balance.
* `trackingStart = min(settings.startDate, earliest entry/day-off date)`.
* Future days never count.

## 3. Navigation

Bottom navigation bar (4 destinations) – **Today · History · Overview · Settings**.
Full-screen destinations pushed on top: **Setup wizard** (first launch only, replaces everything), **Edit day**.
Entry editing happens in a modal bottom sheet from the Edit-day screen.

## 4. Views

### 4.1 Setup wizard (first launch)
Four steps, progress indicator, Back/Next, final button "Start tracking". Nothing is mandatory – sensible defaults (Mon–Fri, 09:00–17:30, 30 min break = 8 h) let the user tap through in 4 taps.

1. **Welcome** – what the app does in 3 bullets (check in/out, default days, overtime), "Get started".
2. **Your work week** – the shared *Schedule editor*:
   * Working-day chips Mon…Sun (toggle).
   * "Usual day" block: start, end, break → applies to all selected working days; shows daily target and weekly total live.
   * Expandable "Customise individual days" – per-day start/end/break rows with computed target.
3. **Starting point** – "Count overtime from" date (default today) and "Existing overtime balance" (default 0, sign + h + m). Explained in one line each; both optional.
4. **Daily reminder** – switch (default on), reminder time (default = latest end + 30 min), explanation of the two notification actions; requests notification permission on Android 13+ when the switch is on and the user continues; graceful handling if denied.

### 4.2 Today (home)
* Top app bar: date ("Thursday, 8 Oct").
* **Status card** (state machine):
  * *Idle / nothing logged*: target and expected hours for today, big filled **Check in** button; secondary text buttons **Log standard day** and **Add entry manually**.
  * *Working*: large live timer (net of break), "Since 09:02" (tappable to correct the start time), progress bar vs today's target, "Expected finish 17:32" / "+0:25 overtime so far"; buttons **Start break** and **Check out**.
  * *On break*: timer paused, break timer, **End break** (primary) and **Check out**.
  * *Done*: summary "Worked 8h 12m · +12m", **Check in again** (new session), **Edit day**.
  * *Day off*: card says "Day off – enjoy", **Check in anyway** option.
  * *Forgot to check out* (active session from an earlier date): warning card "You're still checked in since Wed 09:00" with **Set end time** → opens Edit day for that date.
* **Balance tile**: current overall overtime balance, tap → Overview.
* **Today's entries** list (tap → edit sheet) with an **Edit day** link.
* Snackbars confirm actions with Undo (check-out, logged standard day).

### 4.3 History
* Chronological list (newest first), grouped under month headers, from tracking start to today.
* Row: weekday + date, time span or "Standard day (assumed)" / "Day off" / "Not a working day", worked time, signed delta chip (green/neutral/red-ish tone).
* Today highlighted; tap any row → Edit day. Extended FAB "Add entry" opens a date picker → Edit day.

### 4.4 Overview (stats)
* **Range chips**: *All time* (default), This week, This month, Last month, This year, Custom (date range picker).
* **Hero**: overtime balance for the range, signed, large; subtitle describes the range; negative shown with a distinct tone and wording ("behind").
* **Summary tiles**: Worked, Target, Logged vs assumed days (only counted days).
* **Bar chart** of delta per period (days ≤ 14 d range, weeks ≤ ~4 months, otherwise months); positive up / negative down, accessible description.
* **Breakdown list** per period: label, worked / target, delta.
* Empty state before the first day has passed.
* All-time shows "incl. opening balance" line when ≠ 0.

### 4.5 Edit day (full screen)
* Header: date (tap to change date picker not needed – navigated from outside), computed target and balance of this day.
* **Day off** switch.
* If no entries and working day: card "Standard day assumed: 09:00–17:30 · 30 min break" with **Use standard day** (creates an editable entry) and **Add entry**.
* Entry cards (start–end, break, net duration, note) → tap opens **Entry sheet**; delete with Undo.
* **Entry sheet**: start & end time buttons (Material time picker), break stepper (−/+ 5 min plus presets 0/15/30/45/60), optional note, validation (end after start, break shorter than duration, no overlap with other entries), Save / Delete. For the active session the end field is "Still running".
* "Add entry" defaults to the standard hours of that weekday.

### 4.6 Settings
* **Work schedule** – the same Schedule editor as the wizard; "Changes apply from today – past days keep their old targets."
* **Overtime tracking** – start date and opening balance (editable).
* **Daily reminder** – switch, time; permission status row with "Allow" action when blocked.
* **About** – version, "Data stays on your device".
* Edits are saved immediately (no save button); schedule edits are debounced into one new version for today.

## 5. Notifications

* One channel "Daily reminder". Alarm (`setAndAllowWhileIdle`, no special permission) at the reminder time; re-armed after each fire, on boot, on app update, timezone/time change.
* Fires only when: today is a working day and not marked off **and** nothing completed has been logged; or a session is still active.
* Normal text: "How was your day? Log the standard 8h 00m or customise." Actions: **Standard day** (logs the day silently, confirmation replaces the notification), **Customise** (opens Edit day for today). Tapping the body opens the app on Today.
* If still checked in: "Still working? You're checked in since 09:02." Actions: **Check out now**, **Customise**.

## 6. Edge cases

* Session spanning midnight is attributed to its start date.
* Check-out earlier than check-in (clock changes) → forced into the editor.
* Break entered longer than the session → blocked in the editor; on check-out the break is capped at the session length.
* Changing the schedule never changes past targets (versions).
* Locale: 24h/12h follows the system, week start follows the locale.
* Process death while checked in: the active entry lives in the database, timer resumes.

## 7. Technical

Kotlin · Jetpack Compose · Material 3 (dynamic colour on Android 12+, fallback palette, light/dark, edge-to-edge) · Room (KSP) · Navigation Compose · manual DI. minSdk 26, compile/target 36. Pure-Kotlin `OvertimeCalculator` with JVM unit tests. Build must be warning-free (`allWarningsAsErrors`, lint clean).

---

## 8. Plan review – round 1 (gaps found → changes folded into the text above)

| # | Gap | Resolution |
|---|---|---|
| 1 | What if the user forgets to check out? | "Forgot to check out" card + forced end-time in editor |
| 2 | Changing the weekly schedule would rewrite old overtime | Schedule versions with effective date |
| 3 | Mid-day the balance would look negative | Today excluded while checked in, progress shown separately |
| 4 | Users arriving with an existing flex balance | Opening balance + start date in wizard & settings |
| 5 | Unlogged days: user must understand they count as standard | "assumed" labelling in History and Edit day, explained in wizard step 4 |
| 6 | Notification "Standard day" needs to work without opening the app | Broadcast receiver writing directly to the DB |
| 7 | Breaks must be trackable live and manually | Start/End break on Today, break minutes in manual entries |
| 8 | Work on days off / weekends | Target 0 → whole time is overtime |
| 9 | Android 13 notification permission | Requested in context in wizard / settings, with fallback |
| 10 | Flexible people may only know "8 h" | Start/end/break derive target; any combination is possible (e.g. 08:00–16:00 + 0 break) |

## 9. Plan review – round 2

* Covers all requested functions: main check-in/out + manual entries (4.2, 4.5), overview with range default *All time* (4.4), weekday defaults (4.1/4.6), post-business-hours notification with standard/customise (5), first-start wizard (4.1). ✔
* User journeys verified on paper: *first day* (wizard → Today → check in → break → check out → balance), *lazy week* (notification → Standard day ×5 → Overview = 0), *long day* (notification → Customise → sheet → Save → Overview +), *vacation* (History → day → Day off), *forgot* (banner → set end). ✔
* Navigation depth ≤ 2 everywhere. ✔

Plan accepted → implementation.
