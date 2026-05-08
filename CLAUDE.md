# CLAUDE.md

## What this project is

A small Android app — **PillPup**, package `com.ideonate.pillpup` — that
reminds you to take your daily meds and tracks whether you took, skipped,
or missed each dose. Built around a single user on a Pixel; no accounts,
no cloud sync.

Design priority is **reliability over punctuality**: it's fine for a
reminder to fire 5 min late, it's not fine for a missed dose to silently
disappear, and the engine has to survive reboots, app kills, Doze, and DND
without manual intervention.

## Key design choices (don't undo without thinking)

- **`AlarmManager.setWindow` with a 5-min flex window.** No
  `SCHEDULE_EXACT_ALARM` permission needed. The user explicitly chose
  loose punctuality (up to 5 min late is fine, 15 min for snooze). If you
  ever switch to exact alarms, that's a permission ramp the user has to
  approve in system settings on Android 14+.
- **Single notification channel at `IMPORTANCE_HIGH`, no
  `setBypassDnd(true)`.** Default channel behavior already respects DND
  per the user's spec. Don't add bypass-DND without asking.
- **Snooze gate is a single global pair (`snoozeStartedAt`,
  `snoozeUntil`), not per-med.** A *new* med becoming due during a snooze
  breaks through; a med that was already due before the snooze does not
  refire on its own (which would be noise). The check is
  `dueSince[medId] > snoozeStartedAt`. This matches the user's A/B example
  in the original spec.
- **Foreground tracking via `Application.ActivityLifecycleCallbacks`.**
  When the app is in the foreground, alarms fire but suppress the
  notification and schedule a 5-min re-poll. The user said: "if app is
  open, no notification — wait to see if still open on next alarm".
- **Boot / time / timezone re-arms.** `BootReceiver` listens for
  `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`,
  `TIME_SET`, `TIMEZONE_CHANGED` and calls `ReminderScheduler.rescheduleAll`
  + `Engine.checkAndNotify`. Don't drop these.
- **History is a JSON blob in SharedPreferences**
  (`{ day: { medId: { at, status } } }`). Same pattern as bttame's
  `DeviceStore`. Skipped is recorded explicitly. "Missed" = absence of a
  record on a past day, computed live; **treated as backlog, not a
  terminal display state**. Past-day untaken rows are interactive (slide +
  Skip) so the user can resolve them retroactively. `MainActivity` shows
  a red banner above the list with the unresolved count
  (`HistoryStore.computeBacklog`); tap → jump to the most-recent
  unresolved day. Don't store missed.
- **Timezone policy: literal local "named day".** `Days.today()` is
  `yyyy-MM-dd` in the system's current `TimeZone.getDefault()`. We don't
  pin a "home tz" or expose a setting. Crossing timezones can cause the
  same calendar day to appear twice (long westward flight) or for a slot
  to be skipped over (eastward), but both edges are absorbed by the
  backlog workflow — the user just clears whatever's unresolved.
  `BootReceiver` re-arms on `TIMEZONE_CHANGED` and additionally posts a
  one-off **"review your meds"** notification if backlog is non-zero
  after re-evaluation — so a traveler whose backlog spiked because the
  date moved gets a single ping pointing them at the banner. Don't
  introduce per-app tz state without a strong reason.
- **`createdDay` per med.** Filters meds out of past-day views so newly-
  added meds don't appear retroactively. Today's view shows them
  immediately even if their scheduled time was earlier today.
- **Plain views + ViewBinding, no Compose / Hilt / Room.** Same posture as
  bttame — small enough that the build environment is the hard part, not
  the code.
- **`minSdk = 31`.** `POST_NOTIFICATIONS` runtime permission was
  introduced in Android 13 (API 33), but we still target Pixel-class
  devices. Don't lower minSdk without re-checking the permission story.
- **Cog → Medications screen for add/edit; the day view is read-only**
  for editing. Only take/skip/undo are interactive on the day view. The
  user explicitly asked for this separation — don't fold add/edit back
  into the FAB.
- **Confirm modals on Skip and Undo.** The user wants both to require an
  explicit second tap. Don't silently auto-record.

## Files of note

- `MainActivity.kt` — date stepper + day list. Toolbar menu (portrait) /
  inline cog button (landscape) opens the Medications screen.
- `MedsListActivity.kt` — flat list of all meds, "Add new" FAB, tap a row
  to edit.
- `AddEditMedActivity.kt` — name + 24h `TimePicker`, Save / Delete-with-
  confirm.
- `Engine.kt` — `checkAndNotify`, `onMedTakenOrSkipped`, `onMedUndone`,
  `onMedRemoved`. The decision logic for "fire / suppress / re-poll".
- `ReminderScheduler.kt` — per-med one-shot alarms, snooze-check alarm,
  midnight roll alarm. Per-med `PendingIntent` request code is
  `medId.hashCode()` plus a unique `Uri` so `FLAG_UPDATE_CURRENT` swaps
  cleanly.
- `ReminderReceiver.kt` — handles `MED_DUE`, `CHECK`, `SNOOZE`,
  `MIDNIGHT`. Each `MED_DUE` re-arms the next day's slot.
- `BootReceiver.kt` — re-schedule everything after boot / time changes.
- `Notifications.kt` — channel + post/cancel. Snooze + Open actions.
- `MedStore.kt` / `HistoryStore.kt` / `ReminderState.kt` — JSON-blob
  prefs. State store holds `snoozeStartedAt`, `snoozeUntil`, `dueSince`
  per med, and `dueSinceDay` for rollover.
- `SlideToTakeView.kt` — custom view, ~140 lines. Drag thumb past 75% →
  fires `onTaken` and locks. No external library.
- `MedDayAdapter.kt` — two row states: actions (no record yet, on today
  *or* past) or status text (taken/skipped, tap to undo). No "missed"
  row state — that's surfaced via the global banner instead.
- `res/layout/activity_main.xml` + `res/layout-land/activity_main.xml` —
  portrait keeps a Toolbar + separate stepper row; landscape collapses
  title + stepper + cog into one row (no Toolbar widget; cog is a regular
  IconButton). `binding.toolbar` and `binding.manageBtn` are nullable —
  check both.
- `AndroidManifest.xml` — only `POST_NOTIFICATIONS` and
  `RECEIVE_BOOT_COMPLETED` declared. No exact-alarm, no foreground-service.
- `.github/workflows/build.yml` — CI builds release APK (unsigned),
  regenerates wrapper jar each run.

## Build environment

- JDK 17 at `/opt/homebrew/opt/openjdk@17`.
- Android SDK at `/opt/homebrew/share/android-commandlinetools`,
  platform 34, build-tools 34.0.0.
- Gradle wrapper pinned to 8.7.

Build: `./gradlew :app:assembleDebug` →
`app/build/outputs/apk/debug/app-debug.apk`.
Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

## Release signing

Optional and local-only. `local.properties` (gitignored) holds four
`pillpup*` keys; if absent, the release `signingConfig` is empty and
`assembleRelease` produces an unsigned APK (which is what CI ships).
Keystore lives outside the repo at `~/.android/pillpup-release.jks` if
you set it up.

## Things that are out of scope (considered and rejected)

- **Exact alarms.** User accepted ±5-min slack; not worth the
  permission ramp.
- **Per-med snooze.** Single global gate plus the "new med breaks
  through" rule covers the user's stated cases without UI bloat.
- **Foreground service for reminders.** Receivers do all the work
  inline; no long-running task warrants a service, and Doze handles
  short broadcast wakeups fine.
- **Cloud sync / multi-device.** Not asked for. JSON in prefs is
  enough.
- **Storing "missed" explicitly.** Computed from "no record on a past
  day". Cheaper, avoids backfill bugs, and lets us treat missed as a
  resolvable backlog rather than a terminal state.
- **Pinning a "home" timezone or per-app tz setting.** Considered for
  travel; rejected because the backlog workflow already absorbs the
  edge cases without extra state or UI.
- **Adding meds from the day view.** User specifically wanted the
  cog-gated separation.
