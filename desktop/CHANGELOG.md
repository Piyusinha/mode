# Changelog: Mode Setup

What changed in each release of Mode Setup, the app for Mac, Windows and Linux that installs Mode
on your phone. Mode Setup releases are tagged `Mode-Desktop-App-<version>`, and `tools/release-setup.sh`
won't make one without its section here. Mode itself has its own: [CHANGELOG.md](../CHANGELOG.md).

## Unreleased

## 0.1.4 · 7 Nov 2026

- A saved Apple password that no longer works (changed since) is removed from this computer the
  first time Apple turns it down, and the sign-in asks for the new one, instead of trying the
  old one again each time.
- Stops an iPhone on iOS 26 or earlier with "Mode needs iOS 27", instead of installing a Mode
  that can't see or tap the screen, and guides you through updating: Software Update, finding
  "Upgrade to iOS 27", and what to do if it isn't offered or there isn't enough space. Check
  again goes on after the update.
- The steps on your iPhone include trusting your Apple account (Settings › General › VPN &
  Device Management), for when iOS says "Untrusted Developer".

## 0.1.3 · 7 Nov 2026

- Tells you when a newer Mode Setup is out: a banner on the first page and the phone page, with
  a Download link for your computer. Settings › About shows the version and has Check for
  updates.
- Finds Mode and Mode Setup releases separately, now that each has its own version, and installs
  the newest Mode release.

## 0.1.2 · 7 Nov 2026

- Always installs the newest Mode. On a first install, a reinstall or a renewal, it checks for a
  newer Mode release and installs that, even from an older Mode Setup. Without internet it
  installs the copy inside it.
- Shows "Getting the latest Mode" while it downloads.

## 0.1.1 · 7 Nov 2026

- Has Mode's icon, Mo, on Mac, Windows and Linux, instead of a placeholder.

## 0.1.0 · 7 Nov 2026

The first release.

- Installs Mode on your iPhone with your own Apple account, free or paid.
- Checks your computer first: iTunes on Windows and usbmuxd on Linux, with a download link or the
  command to run. Xcode is not needed.
- Mode is inside Mode Setup, so setup works offline.
- The Mac app is signed and notarized by Apple.
