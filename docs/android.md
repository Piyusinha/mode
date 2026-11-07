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

## Developing

Debug builds have an adb probe, so the engine can be driven without the app's screens:

```sh
# Turn phone control on without the Settings pages (debug and emulators only):
adb shell appops set com.piyush.mode ACCESS_RESTRICTED_SETTINGS allow
adb shell settings put secure enabled_accessibility_services com.piyush.mode/com.piyush.mode.android.ModeAccessibilityService
adb shell settings put secure accessibility_enabled 1

# Then (results in logcat under ModeProbe):
adb shell am broadcast -n com.piyush.mode/com.piyush.mode.app.DevProbe --es op look
adb shell am broadcast -n com.piyush.mode/com.piyush.mode.app.DevProbe --es op tap --ef x 150 --ef y 233
adb shell am broadcast -n com.piyush.mode/com.piyush.mode.app.DevProbe --es op checks
```

`am broadcast` waits until the probe finishes; run it with `&` to look at the phone
while it works (for example during `hold`). Logs: `adb shell run-as com.piyush.mode
cat files/logs/events.log`.

## Background mode (planned, Phase 5)

Mode pairs with the phone's own Wireless debugging, starts a helper as the shell user
and works on a hidden display while the user keeps the phone. Built on proven open
source rather than our own: [libadb-android](https://github.com/MuntashirAkon/libadb-android)
(Apache-2.0 option) for pairing and the connection, a trimmed fork of the
[scrcpy](https://github.com/Genymobile/scrcpy) server (Apache-2.0) for the virtual
display, input and starting apps on it, following [JemRec](https://github.com/jemcik/JemRec)
(Apache-2.0), which does both on-device, and its notes on libadb's pitfalls.
