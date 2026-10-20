# Attendance Log (Android)

A tiny (~42 KB), fully offline Android app for logging **leave**, **late coming** and **overtime**, with a home-screen **calendar widget**.

## Features
- Month calendar with colour-coded days (leave / late / OT; days with several share the box)
- Configurable shifts (add, edit, delete, default shift); overnight shifts supported
- Configurable weekly offs and holidays
- Daily reminder (working days only) with quick Late / OT / Leave actions
- Weekly summary notification (last 7 days + month to date)
- Home-screen widget: month grid, stats, ‹ › month navigation, quick-add buttons, follows dark mode
- Backup export / import (JSON)
- Light and dark theme

## Structure
- `assets/index.html` – the UI (HTML/CSS/JS in a WebView)
- `src/.../Main.java` – WebView host + JS bridge (sync, notifications permission, backup)
- `src/.../Alarm.java` – reminders, weekly summary, midnight widget refresh, reboot rescheduling
- `src/.../CalWidget.java` – home-screen calendar widget (bitmap-rendered grid)
- `src/.../Store.java` – shared data helpers

## Build
No Gradle. Needs JDK 17, Android `android.jar` (API 34), aapt2, d8, zipalign, apksigner.
Edit the `T=` path in `build.sh`, then:

```bash
bash build.sh   # outputs Attendance.apk
```
min SDK 24 (Android 7.0), target SDK 34.
