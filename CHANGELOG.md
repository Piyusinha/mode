# Changelog: Mode

What changed in each release of Mode, the phone app (iPhone now, Android next). Mode releases are
tagged `Mode-Mobile-Build-<version>`, and `tools/release-app.sh` won't make one without its section here.
Mode Setup, the desktop app that installs Mode, has its own: [desktop/CHANGELOG.md](desktop/CHANGELOG.md).

## Unreleased

## 0.1.7 · 9 Nov 2026

- End, any time: the button at the top left of a conversation now ends everything at once
  (listening, speaking and the task Mode is doing), and while Mode is telling you how it went
  there's an End button too. Before, you could get stuck waiting, and iOS's own Stop Session,
  which disconnects Mode until it's paired again, was the only quick way out.

## 0.1.6 · 8 Nov 2026

- Settings no longer shows Developer (runs, logs, model comparison): it's for building Mode, so
  only builds made in Xcode have it.

## 0.1.5 · 8 Nov 2026

- Fixed: "Connect your AI" showed "ChatGPT · Connected" whichever AI you connected. It now shows
  the one you connected (Claude, OpenRouter or ChatGPT).
- Fixed: after deleting Mode and installing it again, sign-ins from the earlier install (kept by
  iOS in the Keychain) came back, so an AI you hadn't connected showed as connected. A fresh
  install now starts signed out; updating keeps your sign-ins.

## 0.1.4 · 7 Nov 2026

- Tells you when a newer Mode is out: Settings shows "Mode 0.1.5 is available", how to update
  it with Mode Setup, and What's new. It checks at most once a day.
- Settings shows this Mode's version at the bottom.

## 0.1.3 · 7 Nov 2026

Mode's first release of its own: Mode and Mode Setup are now released separately, each with its
own version and changelog. Mode Setup installs this release, and any newer Mode, for you.

- Mode itself is the same as in 0.1.2: voice and typed tasks in your apps, the Lock Screen and
  Dynamic Island, memory, your own AI, repeats, and backup and restore.

## 0.1.2 · 7 Nov 2026

Released together with Mode Setup 0.1.2. Mode is the same as in 0.1.0.

## 0.1.1 · 7 Nov 2026

Released together with Mode Setup 0.1.1. Mode is the same as in 0.1.0.

## 0.1.0 · 7 Nov 2026

The first release.

- Say a task, by voice or typing, and Mode does it in your apps: it opens them, taps and types
  on your screen where you can watch, and tells you how it went.
- "Hey Siri, start Mode" for a spoken conversation, as many tasks as you like.
- Every step on the Lock Screen and in the Dynamic Island, with a Stop button.
- Learns you: your people, places, apps and usual orders. See and forget anything it knows.
- Your own AI: sign in with ChatGPT or Claude, or use an OpenRouter key.
- Repeats: "every morning at 8, tell me what's on my calendar".
- Backup and restore: one file with your chats, memory, tasks and settings, locked with a
  password when it holds your sign-ins.
