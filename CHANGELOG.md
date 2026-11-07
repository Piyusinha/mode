# Changelog

What changed in each release of Mode (the iPhone app) and Mode Setup (the app that installs it).
Each release on GitHub shows its section from here: `tools/release.sh` won't make a release
without one.

## Unreleased

### Mode Setup
- Tells you when a newer Mode Setup is out: a banner on the first page and the phone page, with
  a Download link for your computer. Settings › About shows the version and has Check for
  updates.

## 0.1.2 · 7 Nov 2026

### Mode Setup
- Always installs the newest Mode. On a first install, a reinstall or a renewal, it checks for a
  newer Mode release and installs that, even from an older Mode Setup. Without internet it
  installs the copy inside it.
- Shows "Getting the latest Mode" while it downloads.

## 0.1.1 · 7 Nov 2026

### Mode Setup
- Has Mode's icon, Mo, on Mac, Windows and Linux, instead of a placeholder.

## 0.1.0 · 7 Nov 2026

The first release.

### Mode
- Say a task, by voice or typing, and Mode does it in your apps: it opens them, taps and types
  on your screen where you can watch, and tells you how it went.
- "Hey Siri, start Mode" for a spoken conversation, as many tasks as you like.
- Every step on the Lock Screen and in the Dynamic Island, with a Stop button.
- Learns you: your people, places, apps and usual orders. See and forget anything it knows.
- Your own AI: sign in with ChatGPT or Claude, or use an OpenRouter key.
- Repeats: "every morning at 8, tell me what's on my calendar".
- Backup and restore: one file with your chats, memory, tasks and settings, locked with a
  password when it holds your sign-ins.

### Mode Setup (Mac, Windows, Linux)
- Installs Mode on your iPhone with your own Apple account, free or paid.
- Checks your computer first: iTunes on Windows and usbmuxd on Linux, with a download link or the
  command to run. Xcode is not needed.
- Mode is inside Mode Setup, so setup works offline.
- The Mac app is signed and notarized by Apple.
