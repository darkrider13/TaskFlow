<div align="center">

# TaskFlow

**A beautiful, offline-first task & habit tracker for Android — built natively with Kotlin and Jetpack Compose.**

[![Build TaskFlow APK](https://github.com/darkrider13/TaskFlow-v2/actions/workflows/build.yml/badge.svg)](https://github.com/darkrider13/TaskFlow-v2/actions/workflows/build.yml)
![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)
![Languages](https://img.shields.io/badge/language-বাংলা%20%7C%20English-blue)

[**⬇ Download the latest APK**](https://github.com/darkrider13/TaskFlow-v2/releases/tag/latest)

</div>

---

## Overview

TaskFlow keeps your day in one place: one-off **tasks**, daily **habits**, a **calendar** with Bangladesh holidays, **reports**, a **focus timer**, and smart **reminders**. Everything is stored on your phone, works without internet, and is available in **Bangla and English**, in **dark and day themes**.

It started as an HTML/Capacitor app and was rebuilt from scratch as a fully native app — same design, same features, much smoother.

## Features

### Tasks & habits
- Create **tasks** with date, time, repeat (none / daily / weekly / monthly), category and a quick sub-task list.
- Create **habits** with a daily check-in time, monthly and total goals, fixed weekdays or every day, and your own colour.
- **Swipe to delete**, tap to edit, categories you can add and edit yourself.
- Habit answers: ✔ done or ✘ missed, tracked day by day with streak-friendly progress.

### Alerts: Notification or Reminder
Every task and habit has an **Alert** option:

| Option | What happens |
|---|---|
| 🔔 **Notification** | A normal notification at the set time with **Yes / No / Snooze** buttons. |
| ⏰ **Reminder** | An **alarm-style** experience: the phone rings and a full-screen reminder opens (even on the lock screen) with the time and the name. **Swipe the screen sideways to turn it off.** The background matches the app — night photo in dark mode, soft glow in day mode. |

### Screens
- **Home** — today's tasks and habits at a glance, with progress.
- **Calendar** — month view with marks, plus **Bangladesh government and other holidays** (fixed dates, gazette list, Hijri, Hindu/Buddhist festivals). The holiday list updates itself online and also works offline.
- **Upcoming** — what's next, and what is overdue.
- **Reports** — completion rates for habits and tasks.
- **More** — focus timer (with full-screen mode), interval reminders, settings, backup.

### Extras
- 🎯 **Focus timer** with focus/break sessions that keep running on every screen and ring when finished.
- 🔒 **App lock** — PIN, pattern or fingerprint.
- 💾 **Backup & restore** — export/import all your data as a JSON file (old backups from the HTML version still work).
- 🔄 **In-app update** — "Check for update" downloads the newest build from this repository's Releases.
- 🌙 Dark theme with a night-forest background and a bright day theme.
- 🌐 Full **Bangla / English** interface with Bangla digits.

## Install

1. Open the [**latest release**](https://github.com/darkrider13/TaskFlow-v2/releases/tag/latest).
2. Download `TaskFlow.apk`.
3. Open it on your phone and allow *Install from this source* if Android asks.
4. New builds install **over** the old version and keep your data.

> Requires **Android 7.0 (API 24)** or newer. On Android 13+ allow notifications; on Android 14 allow *Full screen notifications* so the reminder screen can open over the lock screen.

## Build it yourself (no computer needed)

The project builds automatically with **GitHub Actions** — you do not need Android Studio.

1. Fork or upload this repository to your GitHub account (keep it **public** if you want in-app updates).
2. Push to the `main` branch, or open **Actions → Build TaskFlow APK → Run workflow**.
3. After a few minutes the APK appears under **Releases → latest** as `TaskFlow.apk`.

### Optional: your own signing key
By default builds are signed with the `debug.keystore` stored in the project, so every update installs over the previous one. To use your own key, add these **repository secrets**:

`KEYSTORE_BASE64` · `KEYSTORE_PASSWORD` · `KEY_ALIAS` · `KEY_PASSWORD`

### Build locally (optional)
```bash
# JDK 17 and Gradle 8.9
gradle assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Tech stack

| | |
|---|---|
| Language | Kotlin 100% |
| UI | Jetpack Compose (custom "glass" design system) |
| Storage | Single local JSON file (`hb.json`) — no account, no cloud |
| Alerts | `AlarmManager` exact alarms, notifications, full-screen intent |
| Security | Biometric API, PIN and pattern lock |
| CI/CD | GitHub Actions → GitHub Releases |
| Min / target SDK | 24 / 34 |

## Project structure

```
app/src/main/java/com/habit/tracker/
├── MainActivity.kt      # app shell, top bar, bottom navigation
├── Home.kt  Calendar.kt  Upcoming.kt  Reports.kt  More.kt   # screens
├── Forms.kt             # task / habit form and day popup
├── Notifs.kt            # alarms, notifications, reminder scheduling
├── ReminderActivity.kt  # full-screen alarm-style reminder
├── Focus.kt  Fullscreen.kt   # focus timer
├── Lock.kt              # PIN / pattern / fingerprint lock
├── Backup.kt  Store.kt  Logic.kt   # data, backup, business logic
├── Hol.kt  HolData.kt  HolRemote.kt  # holiday engine + online feed
├── Updater.kt           # in-app update from GitHub Releases
└── Theme.kt  Ui.kt  Strings.kt  StrData.kt  IconData.kt   # design system & texts
```

## Permissions

| Permission | Why |
|---|---|
| Notifications | Task and habit alerts |
| Exact alarms | Ring at the exact time you set |
| Full-screen notifications | Open the reminder screen over the lock screen |
| Boot completed | Restore alarms after the phone restarts |
| Biometric | Fingerprint app lock |
| Vibrate / Wake lock | Reminder ringing |
| Internet | Only to refresh the holiday list and check for updates |
| Install packages | Install updates downloaded by the app |

## Privacy

Your tasks, habits and notes never leave your phone. There are no accounts, no ads and no analytics. The only network calls are the holiday feed and the update check.

## Not included yet

- Diary screen and badges (their data is still kept inside backups).

## Credits

Designed and developed by **MD. INSANUL ISLAM ABIR**.

© Copyright MD. Insanul Islam Abir. All rights reserved.
