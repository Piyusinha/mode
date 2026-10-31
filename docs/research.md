# Research: finding an alternative to XCUITest

The question: how can an app drive other apps on an iPhone without iOS's
"Automation Running" overlay? A screen-recording indicator was acceptable.

## What the overlay is

On iOS 15 and later, real devices show "Automation Running" while an XCUITest or
WebDriverAgent session drives them. No supported setting hides it; it comes
from the test session itself.
([Appium XCUITest troubleshooting](https://appium.github.io/appium-xcuitest-driver/12.8/troubleshooting/),
[Appium Discuss](https://discuss.appium.io/t/ios-15-automation-running/34766))

## Approaches considered

### 1. Public APIs: deep links, Shortcuts, App Intents
- Deep links open an app at a given screen, and the old engine already had
  `url.open`. Shortcuts can run App Intents that apps expose.
- In iOS 27, apps still cannot call each other's intents; Siri and Shortcuts
  are the go-between ([WWDC26 session 8011](https://developer.apple.com/videos/play/wwdc2026/8011/)).
- **Verdict:** clean and allowed on the App Store, but limited to what each app
  exposes. Not general enough to be the main path.

### 2. External hardware: Bluetooth/USB mouse plus screen capture
- [onehumanbeing/esp32-iphone-agent](https://github.com/onehumanbeing/esp32-iphone-agent):
  a $7 ESP32-S3 acts as a USB mouse and keyboard with AssistiveTouch, and as an
  AirPlay receiver for the screen. The pointer is relative and has to be found
  on screen and calibrated. The screen arrives at 5 fps, 480×1040. ASCII only,
  portrait only.
- Similar: [dovydaskel/Iphone-control-for-social-media](https://github.com/dovydaskel/Iphone-control-for-social-media),
  [ghbhiee/iphone-hil](https://github.com/ghbhiee/iphone-hil).
- [sakx7/iOSBleDigitizer](https://github.com/sakx7/iOSBleDigitizer) claims
  absolute multitouch over BLE (with Zoom turned on), but publishes no test
  results.
- ReplayKit as the eyes: capped at 15 fps, with about 50 MB of memory in the
  extension ([Twilio example](https://github.com/twilio/video-quickstart-ios/blob/master/ReplayKitExample/README.md)).
- **Verdict:** works, but clumsy. Kept as the runner-up.

### 3. Other XCTest-based tools
WebDriverAgent, Appium, [devicekit-ios](https://github.com/M-Shahab-YT/devicekit-ios)
and [leeguooooo/iphone-use](https://github.com/leeguooooo/iphone-use) all run
on XCTest, so they show the same overlay. [mobile-use](https://github.com/minitap-ai/mobile-use)
does not support physical iPhones. **Rejected.**

### 4. CoreDevice remote control (chosen)
Xcode 27's Device Hub can view and control an iPhone. It uses CoreDevice
services on the phone, not XCTest:

| Service | Use |
|---|---|
| `com.apple.coredevice.feature.remote.hid.digitizer` / `universalhid` | Touch, 0..65535 normalised |
| `…remote.hid.keyboard`, `…remote.hid.button` | Keys, hardware buttons |
| `com.apple.coredevice.screencaptureservice` | Screenshots |
| `com.apple.coredevice.displayservice` | Screen stream; also unlocks touch on iOS 27 |
| pasteboard service | Clipboard |
| app service | Launch, app list, processes |

([getsentry/MobileBuildMCP#519](https://github.com/getsentry/MobileBuildMCP/issues/519),
[pymobiledevice3 CLI recipes](https://github.com/doronz88/pymobiledevice3/blob/master/docs/guides/cli-recipes.md))

Key findings:
- **Touch needs a stream.** Touch and keyboard reports are dropped silently
  unless the same client has a media stream open. iOS 26.3 refuses with
  "Remote control requires iOS 27.0" ([go-ios PR #843](https://github.com/danielpaulus/go-ios/pull/843)).
- **Running it on the phone itself already works.**
  [EliotAndres/iphone-use-app](https://github.com/EliotAndres/iphone-use-app)
  (AGPL; read, not copied) drives the phone it runs on through a loopback VPN
  and [idevice](https://github.com/jkcoxson/idevice) (MIT). Mode follows the
  same idea with its own code.
- **The phone shows a notice.** iOS posts "Device was viewed remotely from Mac"
  after sessions, and lists them in Safety Check
  ([Apple: Manage Device Hub access](https://support.apple.com/guide/personal-safety/manage-device-hub-access-on-your-iphone-ips37dbab2a1/web)).

## Why the loopback VPN, and why mobile data is a problem

- The phone's pairing service is meant for a computer on the network. A
  loopback VPN reflects packets for `10.7.0.1` back, so the phone sees a peer.
  (LocalDevVPN, StikDebug and SideStore all work this way.)
- **Mobile data.** "Lockdown rejects connections from the cellular interface.
  It assumes that if there's no WiFi but there's a cellular connection, the
  connection is from that interface." (jkcoxson, [StikDebug#259](https://github.com/StikDebug/StikDebug/issues/259))
  Users work around it by turning mobile data off while connecting
  ([LocalDevVPN#31](https://github.com/jkcoxson/LocalDevVPN/issues/31),
  [StikDebug#418](https://github.com/StikDebug/StikDebug/issues/418)).
- **The 127.0.0.1 idea.** LocalDevVPN#31 showed TCP to `127.0.0.1:49152`
  accepted on cellular, so loopback without a VPN looked possible. **We tested
  it on the phone: TCP is accepted but pair-verify is dropped**, for
  127.0.0.1 and for the phone's own addresses alike (see
  [decisions.md D12](decisions.md#d12-connect-through-a-loopback-vpn-the-phone-refuses-local-clients)).

## Measured from the Mac (Phase 1, pymobiledevice3)

No overlay. Home, screenshot, tap, ASCII typing and taps over Uber's live map
all worked. Hindi failed through the keyboard and worked through the
clipboard. Details are in [coredevice.md](coredevice.md).
