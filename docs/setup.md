# Setup: build, pair, install, test

## Requirements

| | |
|---|---|
| Phone | iPhone on **iOS 27+**, Developer Mode on (Settings › Privacy & Security › Developer Mode) |
| Mac | Xcode 27.1+, XcodeGen (`brew install xcodegen`), Rust via rustup with `aarch64-apple-ios` and `aarch64-apple-ios-sim` |
| Apple account | Paid: Mode's own VPN, re-sign about yearly. Free: LocalDevVPN app, re-sign every 7 days |
| Optional | pymobiledevice3 (`uv tool install pymobiledevice3`) for poking at the phone from the Mac |

```sh
curl https://sh.rustup.rs -sSf | sh -s -- -y --profile minimal
~/.cargo/bin/rustup target add aarch64-apple-ios aarch64-apple-ios-sim
```

## 1. Build

First, your signing: copy `iosApp/Signing.example.env` to `iosApp/Signing.env` (git-ignored) and set
`MODE_TEAM` (your Apple team ID) and `MODE_BUNDLE_PREFIX` (an app ID prefix of your own).
`tools/gen-project.sh` reads it.


```sh
tools/build-native.sh            # libmodecore.a for iPhone + simulator (Gradle also runs it)
tools/gen-project.sh paid        # or: free
cd iosApp
xcodebuild -project Mode.xcodeproj -scheme Mode \
  -destination "id=<UDID>" -derivedDataPath ../build/xcode \
  -allowProvisioningUpdates build
xcrun devicectl device install app --device <UDID> \
  ../build/xcode/Build/Products/Debug-iphoneos/Mode.app
```

`xcrun devicectl list devices` shows the UDID. The Xcode build runs Gradle
(`embedAndSignAppleFrameworkForXcode`), which builds the Kotlin framework and,
through cinterop, the Rust library.

### Updating a copy installed by Mode Setup

Preserve its existing bundle ID, team, Keychain access groups, and app groups.
Mode Setup adds the team suffix to the bundle ID and signs with the provisioning
profile's groups. Xcode automatic signing can omit those explicit groups even
when the bundle ID matches: preferences remain, but saved provider credentials
can become inaccessible. Use the Mode Setup signer for updates, or preserve and
verify the installed signer's entitlements before installing an Xcode build.
Release versus Debug does not determine which preference container is used.

## 2. Pair (once, phone on USB, Mode installed)

```sh
tools/make-pairing.sh
```

Tap **Trust** on the phone if it asks. The file is saved to
`build/pairing/mode.rppairing` (git-ignored; treat it as a credential) and copied
into Mode's `Documents`. You can also drop it into Mode's folder in Finder or
the Files app.

## 3. Connect from the phone

- **Paid build:** open Mode and tap **Connect**. The first time, iOS asks to
  **Add VPN Configurations**: Allow.
- **Free build:** turn on **LocalDevVPN** first, then tap Connect.
- **Use Wi-Fi.** On mobile data alone, turn Mobile Data off for a moment while
  connecting.

The status line shows `Connected via loopback VPN (10.7.0.1) in … ms`.

## 4. Hands check

In Mode: **Open app** `Settings`, **Then tap text containing** `Wi-Fi`, then
**Run in that app, then come back**. The log shows each step with timings, plus
both screenshots and the text read from them.

## Debug logs

Every run is recorded in Mode's `Documents/logs`:
- `runs/<time>/run.json`, `steps.jsonl` and `step-N.jpg`.
- App events in `events.log`.

See them in the **Logs** tab, in Files › On My iPhone › Mode › logs, or pull
them to the Mac:

```sh
tools/pull-logs.sh                                   # → build/logs/<time>/
pymobiledevice3 syslog live | grep "Mode "           # watch app events live
```

## Tests

```sh
./gradlew :shared:core-ops:jvmTest :shared:phone-coredevice:jvmTest
(cd native/modecore && ~/.cargo/bin/cargo test --lib)
```

## Driving the phone from the Mac (`mc`)

`native/modecore/examples/mc.rs` runs the same session code as the app.

```sh
cd native/modecore
cargo build --features usb --example mc
./target/debug/examples/mc home sleep 800 shot /tmp/a.png tap 32768 32768 type "Uber" apps
# through remote pairing instead of the USB tunnel (the app's path):
MC_CONNECT=<phone-ip>:49152 MC_PAIRING=../../build/pairing/mode.rppairing ./target/debug/examples/mc apps
./target/debug/examples/mc pair Mode /tmp/new.rppairing     # what make-pairing.sh runs
./target/debug/examples/mc ddi-remove                        # unmount the disk image (to test remounting)
MC_DDI=/Library/Developer/DeveloperDiskImages/iOS_DDI/Restore ./target/debug/examples/mc home   # mount if missing
```

Commands: `home`, `shot <file>`, `tap x y`, `drag x1 y1 x2 y2 ms`,
`type <ascii>`, `paste <text>`, `key <name>`, `launch <bundle id>`, `apps`,
`clock mach|wall`, `sleep ms`. Coordinates are 0..65535.

Avoid running `mc` while Mode holds its own session: each opens its own screen
stream.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `pair-verify: device socket io failed` | Connecting without the VPN; the phone refuses local clients. Use the VPN route. |
| `connect 10.7.0.1:49152: timed out` | VPN not on. Paid: allow the VPN configuration. Free: turn on LocalDevVPN. |
| Refused only on mobile data | Turn Mobile Data off while connecting, then back on |
| Services missing after a reboot | Mode mounts the bundled image itself at Connect (needs internet). From the Mac: `pymobiledevice3 mounter auto-mount`. |
| `device socket io failed` mid-task | The connection dropped (Mode was paused). Mode reconnects by itself; check that it plays silent audio during tasks. |
| Mode no longer paired after tapping the Dynamic Island | **Stop Session** unpairs. Run `tools/make-pairing.sh` again. |
| Taps do nothing, no error | The session's screen stream is not running, or an app is using the camera or mic |
| "Allow Paste?" prompt | Expected for non-ASCII text; tap Allow, or allow pasting for that app in Settings |
| `xcodebuild` cannot find the device | Phone locked or unplugged (`devicectl` shows "unavailable") |
