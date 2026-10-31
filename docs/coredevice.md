# CoreDevice remote control

Mode sees and taps other apps through iOS 27's CoreDevice remote-control
services: the ones Xcode's Device Hub uses. No XCTest is involved, so iOS shows
no "Automation Running" overlay.

| Need | Service |
|---|---|
| Touch (tap, drag, multi-finger) | `com.apple.coredevice.hid.universalhidservice` (digitizer, 0..65535 normalised) |
| Keyboard (ASCII key codes) | same, host-registered virtual keyboard |
| Hardware buttons | `com.apple.coredevice.hid.indigo` |
| Screenshot | `com.apple.coredevice.screencaptureservice` |
| Media stream (gates touch) | `com.apple.coredevice.displayservice` |
| Clipboard | CoreDevice pasteboard copy/paste |
| Launch, app list, processes | CoreDevice app service |

## Phase 1 check (2026-10-31)

Driven **from the Mac** over USB with pymobiledevice3 11.49.1 (`--userspace`
tunnel, no sudo). iPhone 17 Pro, iOS 27.2 (24B119), disk image already mounted.

| Check | Result |
|---|---|
| Home button | works, ~1 s per CLI call |
| Screenshot | works, ~1 s, 1206×2622 PNG (402×874 pt at 3×) |
| Tap | works: Spotlight from the Home Screen Search pill, Uber from its icon |
| Type ASCII | works ("Uber" into Spotlight) |
| Taps over Uber's live map | work first try |
| Type Unicode ("इंडिया गेट") | **not via the keyboard**: it sends key codes, ASCII only |
| Unicode via clipboard | works: CoreDevice `copy`, then tap iOS's Paste menu; no "Allow Paste" prompt |
| "Automation Running" overlay | **none** (confirmed by watching the phone) |
| Notification | "Device was viewed remotely from Mac" after the session |

The ~1 s per call is the CLI opening a fresh tunnel and stream each time; Mode
keeps one session open.

## Phase 2: modecore, our own session (2026-10-31)

`native/modecore` (Rust, on idevice) holds one tunnel and one screen stream for
the whole session. Driven from the Mac over USB with `examples/mc`:

| Op | Time |
|---|---|
| Open session (tunnel + stream) | 0.5–0.7 s, once |
| Tap / drag | ~52 ms / hold + travel |
| Home | ~81 ms |
| Type "Uber" | ~55 ms |
| Paste (clipboard + Cmd+V) | ~55 ms |
| Launch by bundle id | ~113 ms |
| App list (238 apps) | 42 ms |
| Screenshot, PNG 1206×2622 | 0.24–1.8 s |

Found along the way:
- iOS 27.2 rejects idevice's `jpeg` screenshot format; only PNG works.
  Shrink on the phone, or later take frames from the open stream.
- Cmd+V paste makes iOS ask "<App> would like to paste from dtpasteboardd".
  Tapping Allow Paste works; so does Settings › <App> › Paste from Other Apps ›
  Allow. Tapping iOS's own Paste menu never asks.
- The plain app-list call never returns on iOS 27; the streamed one does.
- Uber's bundle id is `com.ubercab.UberClient`.

## Gotchas

- **Touch needs an open media stream from the same client.** Without one, iOS
  accepts touch reports and silently drops them. Open one stream per session;
  negotiating per gesture costs ~1 s and has wedged the media daemon until reboot.
- Stamp HID reports with `mach_absolute_time()`; wall-clock stamps drop taps.
- Buttons: hold ≥ 50 ms, wait ~100 ms before closing the connection.
- A foreground app using the camera or microphone blocks the media stream.
- Non-ASCII text goes through the clipboard, then Paste.
- No accessibility tree: screen text comes from on-device OCR.
- The phone reports every session as "viewed remotely from Mac", even when the
  client is Mode itself (the phone cannot tell). Sessions are listed in
  Settings › Privacy & Security › Safety Check.
- Requires Developer Mode and a remote-pairing file made once over USB.
- The phone refuses pairing from clients on the phone itself; connect through the loopback VPN.
- A reboot unmounts the developer disk image; until it is mounted again the services are gone.

## Phase 4: on the phone itself (2026-10-31)

Mode connects to its own phone with the pairing file, through a loopback VPN.

| Route | Result |
|---|---|
| `127.0.0.1:49152` | TCP accepted, **pair-verify dropped** |
| Own Wi-Fi address `:49152` | TCP accepted, **pair-verify dropped** |
| Own USB-link address `:49152` | TCP accepted, **pair-verify dropped** |
| `10.7.0.1:49152` through Mode's loopback VPN | ✅ connected |
| Mac → phone's USB-link address, same file and code | ✅ connected (control test) |

So the phone refuses pairing on connections that start on the phone itself, and
the VPN is required. On mobile data with Wi-Fi off, the phone refuses the VPN
route as well (it treats it as cellular), so turn mobile data off for a moment
while connecting.

| Measured on the phone | Time |
|---|---|
| Connect (first time, including the VPN permission prompt) | 5976 ms |
| Connect (afterwards) | 962 ms |
| Open Settings by name | 149 ms |
| Look: picture + text (first) | 1413 ms (694 + 719) |
| Look (next) | 825 ms (417 + 408) |
| Look before the speed fix | about 7–8 s |
| Touch after the session sat idle for 45 s | works |

See [decisions.md](decisions.md) D12–D15 and [flows.md](flows.md).
