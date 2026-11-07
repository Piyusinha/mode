# Mode on Android

How Mode drives an Android phone, and what Android asks of it. The shared Kotlin
(agent, memory, tasks, presenters) is the same as on the iPhone; this page is about
what differs. Status per phase is in [status.md](status.md); decisions are D41–D45 in
[decisions.md](decisions.md).

- Android 13 (API 33) and newer, built against Android 17 (API 37).
- Two builds: **full** (GitHub: everything) and **play** (Play Store: no phone control,
  since Play does not allow an accessibility service to drive apps on its own).

## The pieces

| Module | What |
|---|---|
| `app:android` | The app: one activity with Compose screens, the receivers and services Android needs in the app itself, the `full` and `play` flavours |
| `app:graph` (androidMain) | `androidModeGraph`: the Android implementations behind `PlatformServices` |
| `engine:phone-android` | The engine: `AccessibilityBackend` (common code, tested over a fake tree), `ModeAccessibilityService`, `ServiceRegistry`, `AndroidImageTools` |
| `feature:connection` (androidMain) | `AccessibilityPhoneLink`, `AccessibilityPhoneChecks` |
| `core:*` (androidMain) | Keystore secrets, SharedPreferences, files, OkHttp, the SQLite driver, lifecycle, permissions, the keyguard, notifications, `WorkService` |

## Phone control: the accessibility service ("On your screen")

The user turns Mode on once in Settings › Accessibility. Android then runs
`ModeAccessibilityService`, and the backend drives the phone through it while a task
runs; between tasks Mode reads nothing.

| Op | How |
|---|---|
| `screen.capture` | `takeScreenshot` (about three a second at most; a throttled shot waits once). A screen an app keeps secure answers `{"private": true}` and the agent hands that step to the user |
| `screen.text` | The accessibility tree, not OCR: visible nodes' text, else description, else hint; reading order; one box where a container repeats its child; 80 boxes from the page; passwords never; the keyboard's keys marked `window: "ime"` |
| `input.tap`, `.press`, `.drag`, `.scroll` | `dispatchGesture` with the display id; scrolls are slow drags so lists stop where the finger stops |
| `input.text` | `ACTION_SET_TEXT` on the focused field, after what it holds (its hint is not text it holds); paste when that fails. Any language |
| `input.keys` | `return` presses the keyboard's action key (Search, Send, Go); `delete` removes the last character |
| `nav.home`, `nav.back`, `nav.recents`, `notifications.open`, `quicksettings.open` | Global actions |
| `apps.launch` | The launcher intent (`fresh` clears the app's task); names resolve against the launcher's list (`<queries>`, no `QUERY_ALL_PACKAGES`) |
| `apps.current`, `url.open` | The focused app window's package; `ACTION_VIEW` |

Points are dp. `screen.bounds` also gives `top_inset`, the status bar's height, which
the agent uses in place of the iPhone's fixed 60 points.

Measured on the API 37 emulator (2026-11-07): a look (a 462×1000 JPEG plus 20 texts)
in 131–229 ms; a tap in about 50 ms; open an app in about 40 ms.

### Screens phone control can't read

When an app's window has no readable text (games, maps, canvases, some web views), the backend reads the
screenshot with Google's on-device text recognition (`MlKitTextReader`: ML Kit through Google Play services,
Latin script, plus Devanagari when the phone's language is written in it); boxes come back with source "ocr".
About 0.4 s on the emulator.

### Keeping Mode running

While a task runs, `AndroidBackgroundWork` starts `WorkService`, a special-use
foreground service with the ongoing "Mode is working" notification and Stop, and
holds a partial wake lock and a screen-on wake lock (taps need the screen on). Android
allows the start from the background because the accessibility service is bound.
Silent audio, as on the iPhone, is not used: Android 17 restricts background audio.

Android never says when it ends a process, so `AppLifecycle.onTerminate` does
nothing on Android; Mode goes to the background on every step of a task.

### What the checks look at (Diagnose, Health)

1. **Restricted settings.** Android 13+ locks the accessibility switch of an app that
   didn't come from an app store until the user taps ⋮ › Allow restricted settings in
   Mode's App info. Skipped once the switch is on.
2. **Phone control.** The switch is on, and Android is running the service (after an
   update or a force stop it can be on but not running: off and on again fixes it).
3. **Sees the screen** (one screenshot) and **taps** (on Mode's own title area).
4. **Battery.** Unrestricted, so Android doesn't stop a task halfway. A warning only.

## Setup on Android

The first run has one more step than the iPhone's: Welcome, Connect your AI, **Let Mode use your phone**, then the first job. Phone control is a switch the user turns on in Settings, so it is set up rather than asked for (`FirstRunStep.Phone`, shown when the phone's checks include `Engine`).

- `PhoneSetupPresenter` (shared) lists Phone control (required), Notifications, the assistant button, Battery and the Microphone (asked later), each with its state from `PhoneChecks.status()` (a quick read of Android's settings, no screenshot or tap) and `Permissions`. A setting this build can't offer is left out.
- Phone control, the assistant and battery each open a guide page (the S02–S05 boards). The guide's button opens Android's page; when Mode comes back to the front everything is read again, and a guide whose setting is now on closes itself.
- An app not from a store has its accessibility switch locked by Android 13+: Phone control then opens the "Unlock Phone control" guide first (App info › ⋮ › Allow restricted settings).
- Android pages and their fallbacks live in `SystemPages`: Mode's own accessibility switch is not openable directly on stock Android, so the Accessibility list opens; battery asks Android's one-tap question in the full build.
- ChatGPT sign-in: `ChatGptSignIn` listens on 127.0.0.1:1455 (`LoopbackCallback`) before opening OpenAI's page in a browser tab; coming back to Mode without an answer is a cancel.
- Strings both apps share are generated from the iPhone's `L10n*.swift` by `tools/gen-android-strings.py` (iPhone words swapped for Android's); Android-only ones are in `app/android/src/main/res/values/strings.xml`.

To walk setup past Connect your AI on an emulator without an account: `adb shell am broadcast -n app.heymode.mode/.app.DevProbe --es op key` (debug builds only).

Known gap: the Play build has no phone control, so its setup will need its own path (Phase 6).

## Hands-free

- **Notifications** (`AndroidNotifier`): results open their chat; Mode's questions go on a high-importance channel with buttons from `QuestionActions` (shared, tested): Yes / No, Always, up to three choices (more choices end in a typed "something else"), a typed reply (RemoteInput), or "I've entered it". `AnswerReceiver` answers the task without opening Mode and withdraws the notification.
- **The working notice** follows the task (`WorkNotice.progress`, fed from the shared `LiveActivityPresenter`): what Mode is doing, the plan as a progress bar, a running clock, or waiting for the phone to unlock; Stop ends the task.
- **Repeats** remind with an exact alarm (`SCHEDULE_EXACT_ALARM`; inexact when the user hasn't allowed it). `ScheduleReceiver` shows the reminder; `BootReceiver` re-arms every repeat after a restart, an update, or a clock or time zone change (`Scheduler.rearmAll`).
- **Voice** (`AndroidDeviceVoice` behind the shared `DeviceVoice`): text-to-speech with the phone's voices (named "English (India) · Voice 2", the user's accent first); listening live with the speech recognizer, on the device when its speech pack is there, otherwise the phone's regular recognizer while Android downloads the pack; a 16 kHz WAV with echo cancelling for a cloud listener (Deepgram, Soniox). `VoiceLevel` feeds talking mode's orb and bars and its "can't hear you".
- **Talking mode** (`talk/Talk.kt`, the Talking mode boards) over the shared `ListenPresenter`: from Ask's mic, a chat's mic, `mode://talk`, `mode://listen`, the assistant, and a spoken question in a running task.
- **The assistant** (`assistant/ModeAssistant.kt`): holding power (or the assistant gesture) opens talking mode. Android requires an assistant to bring a speech recognizer and may make it the default, so `ModeRecognitionService` forwards to the phone's own one, and Mode's listening names the phone's recognizer directly. The user picks Mode in Settings › Default apps › Digital assistant (`ROLE_ASSISTANT` can't be requested by an app).
- **The widget** (`widget/ModeWidget.kt`, board "Home screen widget"): the mic opens talking mode; two suggestions run at once through `mode://run/<task>` and open their chat. Redrawn after each task.

To try these on an emulator (debug builds): `--es op ask --es kind Choice --es options 'Home|Work|Gym'` posts a question, `--es op widget` asks the launcher to pin the widget, and `adb shell cmd role add-role-holder android.app.role.ASSISTANT app.heymode.mode` then `adb shell input keyevent KEYCODE_ASSIST` starts Mode as the assistant.

## Releases

- `tools/build-apk.sh [full|play] [release|debug]` builds into `dist/` (`Mode.apk` + `.sha256`). The version is
  the iPhone's `MARKETING_VERSION` (`iosApp/project.yml`): one version everywhere; the version code is
  major·10000 + minor·100 + patch.
- Release signing comes from `MODE_ANDROID_KEYSTORE`, `MODE_ANDROID_KEYSTORE_PASSWORD`, `MODE_ANDROID_KEY_ALIAS`
  (and `MODE_ANDROID_KEY_PASSWORD`); without them the APK is unsigned. Keep one key forever: Android only updates
  an app signed with the key it was installed with. Register that key for Android developer verification before
  the first public release.
- The tag's release workflow (`.github/workflows/release.yml`, job `android`) builds and signs the APK from the
  repository secrets `MODE_ANDROID_KEYSTORE_BASE64`, `MODE_ANDROID_KEYSTORE_PASSWORD`, `MODE_ANDROID_KEY_ALIAS`
  and attaches it to the draft release. Without the secrets it builds unsigned and attaches nothing.
- Not shrunk (no R8) yet: the helper is started by class name from the APK, and the link and storage use
  reflection. The APK is about 55 MB; keep rules and per-ABI APKs would bring it down.
- **The Play build** (`play` flavour, `app.heymode.mode.play`) has nothing that drives the phone: no accessibility
  service, no special-use working service, no background mode (checked in its merged manifest). Its setup skips
  "Let Mode use your phone". Still to do for Play: first jobs that don't need phone control, and the store's
  declarations.

## Developing

Debug builds have an adb probe, so the engine can be driven without the app's screens:

```sh
# Turn phone control on without the Settings pages (debug and emulators only):
adb shell appops set app.heymode.mode ACCESS_RESTRICTED_SETTINGS allow
adb shell settings put secure enabled_accessibility_services app.heymode.mode/com.piyush.mode.android.ModeAccessibilityService
adb shell settings put secure accessibility_enabled 1

# Then (results in logcat under ModeProbe):
adb shell am broadcast -n app.heymode.mode/com.piyush.mode.app.DevProbe --es op look
adb shell am broadcast -n app.heymode.mode/com.piyush.mode.app.DevProbe --es op tap --ef x 150 --ef y 233
adb shell am broadcast -n app.heymode.mode/com.piyush.mode.app.DevProbe --es op checks
```

`am broadcast` waits until the probe finishes; run it with `&` to look at the phone
while it works (for example during `hold`). Logs: `adb shell run-as app.heymode.mode
cat files/logs/events.log`.

## Background mode ("In the background")

Optional, set up once from Settings › Permissions › Work in the background (boards B01–B06). Mode then does a
task on a hidden display while the user keeps using the phone, and falls back to their screen whenever the hidden
display isn't ready, so a task always runs.

- **The link** (`engine:adb-link`): Mode pairs with its own phone's Wireless debugging and runs commands as the
  shell user. Built on [libadb-android](https://github.com/MuntashirAkon/libadb-android) 3.1.1 (JitPack, only that
  group) with Conscrypt 2.8.0 for TLS 1.3 (2.5.3's native library is 4 KB-aligned and won't load on 16 KB-page
  phones) and BouncyCastle for Mode's own key (`AdbKeys`, in Mode's files). No mDNS: on Android 17 discovering
  local services brings up a picker, which closes Settings' pairing window. The debugging port is found on
  127.0.0.1 instead (`LoopbackPorts`, the ephemeral range, about a second), and the last one is tried first.
- **Pairing** (`BackgroundSetup`): while the "Pair with device" window is open, Mode reads its code and port with
  phone control (`PairingWindow`, digits and an address only, so any language) and pairs by itself; the board's
  notification takes a typed code when it can't. Then Mode grants itself `WRITE_SECURE_SETTINGS` over the link, so
  after a restart it switches Wireless debugging back on itself (`BackgroundMode.wirelessDebuggingOn`).
- **The helper** (`engine:adb-helper`, see its `PATCHES.md`): started with `app_process` from Mode's APK over one
  `exec:` stream (no PTY, no socket). Following the scrcpy server (Apache-2.0), it makes a trusted, always-unlocked
  virtual display with its own focus, presses Back and Home there, and moves the hidden display's app to the user's
  screen and back (`cmd activity display move-stack`). It ends with its input, taking the display with it.
- **Tasks** (`AccessibilityPhoneLink` + `BackgroundMode`): the same accessibility backend with the hidden display's
  id. Screenshots, the text tree, taps and typing all take a display id; Mode opens apps there itself. Back and Home
  go through the helper, because the accessibility service's own actions always hit the user's screen; the
  notification shade and quick settings are refused on the hidden display. Coming back to Mode after a task is
  skipped there. The display ends with the task's session.
- **The user's turn** (a PIN, a password, a private screen): the app comes to the user's screen and goes back once
  Mode carries on. The live run shows "Live · in the background", a "Hidden screen" tag, and Take over / Give back
  to Mode (boards R01, R03). Leaving Mode while a task runs there shrinks it to a small floating window
  (picture-in-picture, board R02): the hidden screen as Mode last saw it, Mo and the task, with Stop.

Measured on the API 37 emulator: pairing about 1.2 s after the window opens; connecting, starting the helper and
making the display 1.1–1.6 s (including switching Wireless debugging back on); a look on the hidden display
130–170 ms, Back 117 ms. Debug probes: `--es op adb` (pair with `--es code … --ei port …`, or connect),
`helper`, `on --ei display N`, `background --ez on true|false` / `--ez forget true`, `route`, `bring`, `giveback`.
