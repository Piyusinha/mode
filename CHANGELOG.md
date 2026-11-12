# Changelog: Mode

What changed in each release of Mode, the phone app (iPhone now, Android next). Mode releases are
tagged `Mode-Mobile-Build-<version>`, and `tools/release-app.sh` won't make one without its section here.
Mode Setup, the desktop app that installs Mode, has its own: [desktop/CHANGELOG.md](desktop/CHANGELOG.md).

## 0.2.0 · 12 Nov 2026

The launch release. If you're new here: Mode is an AI agent on your iPhone. You tell it what to do, by
voice or by typing, and it does it in your apps, looking at the screen and tapping like you would. It
runs with your own AI account (ChatGPT, Claude or OpenRouter), is installed from your Mac or PC with
Mode Setup, and is open source.

- Everything from 0.1.10 is here: the greeting that speaks at once, voice sessions that stay open and
  minimise into a session bar, Speechify as a voice, instant Stop, chats named by the AI.
- Version numbers now go 0.1.9 → 0.2.0 (three single digits), so this follows 0.1.10 directly;
  nothing in the app changed between the two.

## 0.1.10 · 10 Nov 2026

- Mode speaks the moment you call it. Its own hello ("Hey Piyush, good evening.") comes at once and the
  mic opens with it, while the AI writes what comes next: an offer from your day, or how it asks what
  you need. That line follows when it arrives, as one greeting; say your task at any point and Mode
  takes it straight away. Before, nothing was said or heard until the AI answered, 5–12 seconds some
  evenings. The AI gets 6 seconds, then Mode asks in its own words.
- The next greeting is ready before you ask: after each one, Mode writes the next for the same hour
  and keeps it for 20 minutes, so a session opened soon after greets instantly, still in the AI's words.
- A task that leaves you in another app (a cart to look at, a video playing) no longer ends the voice
  session: Mode carries on from behind, and you tap the bar to bring it forward.
- Fixed: after a task, a listen that came back at once could hold Mode's thread for 20 seconds, with the
  screen standing still.

## 0.1.9 · 9 Nov 2026

- Speechify is a new voice for Mode, first in Settings → Voice and in Choose a voice: natural voices
  in 48 languages, Hindi included, on a free plan of about 4 hours of speech a month with no card.
  Paste a key from platform.speechify.ai; Mode checks it with Speechify, then keeps it in the
  iPhone's Keychain. It speaks in the language and at the speed you set, and in a conversation it
  streams: Mode starts talking as the first sound arrives instead of waiting for the whole line.
- Yes to the greeting's offer does what was offered. When Mode opens with "Want me to set an
  alarm?", "sure, please do this" now runs that task (the offer shows Yes and Not now to tap, too).
  Before, the words themselves became the task and Mode had to ask what you meant. More ways of
  saying yes are understood: "do this", "go for it", "sounds good".
- A new voice session starts fresh. Mode only offers to carry on a stopped task when the session
  is opened from that chat's mic; from Siri or a new chat it no longer opens with "Last time…",
  and its greeting no longer brings up the last task of the day.
- Fixed: Mode's voice could stop in the middle of a line and its own words show up as yours. The
  microphone heard Mode, took that for you speaking and then going quiet, and closed, which
  switched the sound off. While Mode speaks, the microphone now never decides you have finished.
- Talking over Mode takes a little more: half a second of speech with three new words among the
  last five heard. Words close to Mode's own ("harbor" for "Harper") count as its voice heard back.
- Chat names come from what Mode did as well as what you asked, so a chat that began with "sure,
  please do this" is no longer called "Unspecified Request".
- ElevenLabs is no longer tagged "Most human", and setup no longer suggests it under Apple's voice.

## 0.1.8 · 9 Nov 2026

- The voice session minimises instead of closing: ⌄ at the top left slides the voice screen down
  into a session bar above the tab bar (or above a chat's message box). The mic, Mode and any task
  carry on; tap the bar to open the same screen again. The bar always says what's happening:
  listening, Mode speaking, working (with Stop), needs you (with Answer), or mic paused. Its mic
  pauses or resumes listening, and × ends the session. It follows light and dark mode.
- A session stays open until you end it (End, ×, or "that's all"), from Siri, the Ask mic or a
  chat's mic. Going quiet after a task pauses the mic instead; a session left paused with nothing
  running ends by itself after 15 minutes.
- Stop answers at once: Mode stops talking, the task's card shows Stopped, and Mode asks what's
  next with the mic already open, while the task lets go of the phone behind. Before, Stop could
  stay stuck on "working", or take 5–10 seconds and still say the reply meant for the task.
- A spoken session started from a chat continues that chat: it shows the chat's last messages, and
  Mode picks up from its last task instead of a fresh greeting.
- One Live Activity at a time: in a spoken session, each task's activity replaces the last one's
  answer, and "Mode is connected" waits until it's gone, instead of stacking one per task.
  Answers you already heard out loud no longer also arrive as a notification.
- Carry on where you stopped: when a spoken session starts and the last task of that chat (or,
  from Siri, of your latest session) stopped or was cut off in the last 12 hours, Mode first asks
  whether to carry on. Yes runs it again in the same chat, so the session continues.
- Fixed: iOS could close Mode in the middle of a task (seen on a payment page) for using too much
  CPU in the background. Screens no longer rebuild their logic every time something on screen
  changes, the encrypted link to the phone uses the iPhone's AES hardware, and Mode slows down
  between steps if it gets close to iOS's limit.

## 0.1.7 · 9 Nov 2026

- Fixed: the first task after setup could fail with "Could not reach this iPhone's developer
  services". Setup's connection check switched Mode's VPN off a moment late, after the first
  task had already connected. Mode now keeps the VPN when a new task connects in that moment.

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
