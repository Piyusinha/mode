#!/bin/sh
# Makes a draft GitHub release of Mode: everything that needs a Mac and Xcode is built here, and the
# tag starts GitHub Actions, which adds Mode Setup for Windows and Linux to the same release.
#
#   tools/release.sh 0.1.0          # → draft release v0.1.0
#
# The release holds (Mode Setup carries both .ipa files inside it, and downloads newer ones from here
# for updates, checking each against its .sha256):
#   Mode.ipa, Mode.ipa.sha256             the free build (LocalDevVPN)
#   Mode-paid.ipa, Mode-paid.ipa.sha256   with Mode's own VPN, for paid Apple accounts
#   Mode Setup for Mac (.dmg), then Windows (.msi, .exe) and Linux (.deb, .AppImage) from CI
#
# Signing the Mac app: this Mac's "Developer ID Application" certificate and the notarytool
# profile "mode-notary" are used by default. Override with:
#   MODE_SIGN_IDENTITY="Developer ID Application: Your Name (TEAMID)"   the identity to sign with
#   MODE_NOTARY_PROFILE=mode-notary   a notarytool keychain profile, made once with
#       xcrun notarytool store-credentials mode-notary --apple-id <you> --team-id <TEAMID>
# Without them the .dmg is unsigned and macOS asks people to right-click → Open.
#
# Nothing is published: the release stays a draft until you publish it on GitHub.
set -eu

V=${1:?"usage: tools/release.sh <version>, e.g. 0.1.0"}
TAG="v$V"
ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
CARGO=${CARGO:-"$HOME/.cargo/bin/cargo"}
cd "$ROOT"

die() { echo "release: $*" >&2; exit 1; }

# Releases come from main as pushed, so the tag and the files match.
[ "$(git branch --show-current)" = main ] || die "switch to main first"
git diff --quiet && git diff --cached --quiet || die "commit or stash your changes first"
git fetch -q origin main
[ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] || die "push main first (or pull): HEAD differs from origin/main"
git rev-parse -q --verify "refs/tags/$TAG" >/dev/null && die "tag $TAG already exists"

# One version everywhere: Mode Setup's and Mode's.
SETUP_V=$(sed -n 's/^ *"version": *"\(.*\)",*$/\1/p' desktop/setup-app/tauri.conf.json | head -1)
[ "$SETUP_V" = "$V" ] || die "desktop/setup-app/tauri.conf.json says $SETUP_V; set it to $V"
APP_V=$(sed -n 's/^ *MARKETING_VERSION: *"\(.*\)"$/\1/p' iosApp/project.yml | head -1)
case "$V" in "$APP_V" | "$APP_V".0) ;; *) die "iosApp/project.yml MARKETING_VERSION is $APP_V; set it to $V";; esac

echo "== Mode (.ipa, free and paid)"
tools/build-ipa.sh both

echo "== Mode Setup for Mac (.dmg)"
# This Mac's Developer ID and the "mode-notary" password are used unless others are given.
if [ -z "${MODE_SIGN_IDENTITY:-}" ]; then
    MODE_SIGN_IDENTITY=$(security find-identity -v -p codesigning | sed -n 's/.*"\(Developer ID Application: [^"]*\)".*/\1/p' | head -1)
fi
if [ -n "$MODE_SIGN_IDENTITY" ]; then
    export APPLE_SIGNING_IDENTITY="$MODE_SIGN_IDENTITY"
    MODE_NOTARY_PROFILE=${MODE_NOTARY_PROFILE:-mode-notary}
    echo "   signing as $MODE_SIGN_IDENTITY, notarizing with profile $MODE_NOTARY_PROFILE"
fi
# Universal: Apple silicon and Intel. CI=true skips Finder's window styling, which needs automation access.
"$HOME/.cargo/bin/rustup" target add aarch64-apple-darwin x86_64-apple-darwin >/dev/null
# tauri.release.conf.json puts both .ipa files inside the app, so setup works offline and needs
# nothing from GitHub (updates still come from the latest release).
(cd desktop/setup-app && CI=true "$CARGO" tauri build --bundles dmg --target universal-apple-darwin --config tauri.release.conf.json)
DMG=$(ls desktop/target/universal-apple-darwin/release/bundle/dmg/*.dmg | head -1)
[ -f "$DMG" ] || die "no .dmg was built"
if [ -n "${MODE_NOTARY_PROFILE:-}" ]; then
    echo "== Notarizing $DMG"
    xcrun notarytool submit "$DMG" --keychain-profile "$MODE_NOTARY_PROFILE" --wait
    xcrun stapler staple "$DMG"
    spctl -a -t open --context context:primary-signature -v "$DMG"
else
    echo "   (unsigned: set MODE_SIGN_IDENTITY and MODE_NOTARY_PROFILE to sign and notarize)"
fi
MAC_DMG="build/release/Mode-Setup-$V-mac.dmg"
cp "$DMG" "$MAC_DMG"
# The same file under a fixed name: the README's Download button links to releases/latest/download/Mode-Setup-mac.dmg.
cp "$DMG" build/release/Mode-Setup-mac.dmg

echo "== Draft release $TAG"
NOTES=$(mktemp)
cat > "$NOTES" <<EOF
## Install
1. Download **Mode Setup** for your computer:
   - Mac: \`Mode-Setup-mac.dmg\` (Apple silicon and Intel, signed and notarized)
   - Windows: \`Mode-Setup-windows.exe\` (or the .msi)
   - Linux: \`Mode-Setup-linux.AppImage\` (or the .deb)
2. Open it. It checks your computer first: on Windows it needs Apple's iTunes from apple.com, on
   Linux usbmuxd, and it tells you if either is missing. Xcode is not needed.
3. Connect your iPhone with a cable and follow the steps. Mode is inside Mode Setup, so nothing
   else is downloaded.

Mode needs an iPhone on iOS 27 or later. See the README.

## Files
- \`Mode.ipa\`, \`Mode-paid.ipa\`: Mode, unsigned. Mode Setup signs it with your Apple account.
- \`*.sha256\`: their checksums.
EOF
gh release create "$TAG" --draft --target main --title "Mode $V" --notes-file "$NOTES" \
    build/release/Mode.ipa build/release/Mode.ipa.sha256 \
    build/release/Mode-paid.ipa build/release/Mode-paid.ipa.sha256 \
    "$MAC_DMG" build/release/Mode-Setup-mac.dmg
rm -f "$NOTES"

# GitHub Actions (release.yml) builds Windows and Linux for this tag and adds them to the draft.
git tag -a "$TAG" -m "Mode $V"
git push -q origin "$TAG"
echo "Draft $TAG made. Windows and Linux builds: gh run list --workflow release.yml"
echo "When they are attached, check the files and publish it on GitHub."
