# PillPup — Medication Reminder & Tracker

Small Android app (package `com.ideonate.pillpup`) that nags you to take your
meds and keeps a per-day record of whether you took them, skipped them, or
missed them.

The whole point is "loose punctuality with reliable history": it doesn't
ping at the millisecond, but it won't let a dose silently disappear, and it
survives reboots / app kills / battery saver / DND.

## Features

- **Per-med daily reminders.** Set a name and a time. Reminders fire in a
  ±5-minute window after the scheduled time (uses
  `AlarmManager.setWindow`, so no `SCHEDULE_EXACT_ALARM` permission).
- **Snooze 15 min.** Notification has Snooze and Open actions.
- **Multiple meds, single notification.** If A is snoozed and B becomes due
  in the meantime, B breaks through the snooze. A alone won't refire while
  the snooze is active.
- **DND respected.** Single notification channel at `IMPORTANCE_HIGH` with
  no `setBypassDnd`.
- **Survives reboots.** Boot, package-replace, and time/timezone receivers
  re-arm everything.
- **Quiet when you're already in the app.** No notification fires while the
  activity is in the foreground.
- **Today view.** Slide-to-take thumb + Skip button (with a confirm modal),
  per untaken med. Status rows show ✓ Taken / ⊘ Skipped with the time it
  was recorded; tap to undo (with a confirm modal).
- **History.** Step ◀ / ▶ through past days. No record on a past day = ✗
  Missed (skipped is *explicit*, distinct from missed). New meds don't
  retroactively appear on prior days.
- **Edit/delete meds via cog.** The day view itself is read-only apart from
  take/skip/undo — adding and editing happens on a separate Medications
  screen.
- **Landscape.** Title + date stepper share one row.

## Prerequisites (one-time, macOS)

```sh
brew install openjdk@17 gradle
brew install --cask android-commandlinetools
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

Env vars (in `~/.zshrc`):

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
```

The gradle wrapper jar is committed; `./gradlew` works out of the box.

## Build

Debug (unsigned, for fast iteration):

```sh
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

Release (signed if `local.properties` has `pillpup*` keystore properties,
otherwise unsigned):

```sh
./gradlew :app:assembleRelease
# → app/build/outputs/apk/release/app-release[-unsigned].apk
```

## Install on a tethered Pixel

Phone needs **Settings → System → Developer options → USB debugging** on,
and must show as `device` in `adb devices`.

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`-r` reinstalls over an existing install without wiping data. Switching
between debug and release builds requires `adb uninstall com.ideonate.pillpup`
first (different signing identities).

## Release signing (optional)

If you want to ship a signed release APK from this machine, generate a
keystore and add it to `local.properties` (gitignored):

```sh
PW=$(LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 32)
keytool -genkeypair \
  -keystore ~/.android/pillpup-release.jks \
  -alias pillpup -keyalg RSA -keysize 2048 -validity 36500 \
  -storepass "$PW" -keypass "$PW" \
  -dname "CN=Dan, OU=pillpup, O=pillpup, L=, ST=, C=GB"
```

```
sdk.dir=/opt/homebrew/share/android-commandlinetools
pillpupStoreFile=/Users/dan/.android/pillpup-release.jks
pillpupStorePassword=<PW>
pillpupKeyAlias=pillpup
pillpupKeyPassword=<PW>
```

Back up the keystore — losing it means future updates require an
uninstall-first.

## Usage

1. Tap the **cog** (top-right) → **Add new** → name + time → Save.
2. The med appears on the Today view. When the scheduled time arrives (give
   or take 5 min), a notification fires unless the app is open / DND is on.
3. Drag the slide-to-take thumb on the row to record it as taken, or tap
   **Skip** for an explicit skip. Tap a recorded row to undo.
4. ◀ / ▶ steps through previous days. Anything untaken from a finished day
   shows as ✗ Missed.

## CI

`.github/workflows/build.yml` builds a release APK on push (unsigned, since
the keystore is local-only) and uploads it as an artifact.
