#!/bin/sh
# Makes a draft GitHub release of Mode Setup, the desktop app that installs Mode: tag and title
# Mode-Desktop-App-<version>. The Mac .dmg is built, signed and notarized here; the tag starts GitHub
# Actions (release.yml), which adds Windows (.exe, .msi) and Linux (.AppImage, .deb).
#
#   tools/release-setup.sh 0.1.3      # → draft release Mode-Desktop-App-0.1.3
#
# Before: set the version in desktop/setup-app/tauri.conf.json and desktop/Cargo.toml, and write
# its section in desktop/CHANGELOG.md. Mode itself is released on its own (tools/release-app.sh).
#
# Every Mode Setup carries the newest published Mode inside it (tools/latest-app-release.sh), so
# it can install offline; online, it installs any newer Mode that has come out since.
#
# Signing the Mac app: this Mac's "Developer ID Application" certificate and the notarytool
# profile "mode-notary" are used by default. Override with:
#   MODE_SIGN_IDENTITY="Developer ID Application: Your Name (TEAMID)"   the identity to sign with
#   MODE_NOTARY_PROFILE=mode-notary   a notarytool keychain profile, made once with
#       xcrun notarytool store-credentials mode-notary --apple-id <you> --team-id <TEAMID>
# Without them the .dmg is unsigned and macOS asks people to right-click → Open.
#
# Publish it without making it GitHub's "Latest" (that stays the newest Mode release):
#   gh release edit Mode-Desktop-App-<version> --draft=false --latest=false
set -eu
. "$(dirname "$0")/release-common.sh"
CARGO=${CARGO:-"$HOME/.cargo/bin/cargo"}

V=${1:?"usage: tools/release-setup.sh <version>, e.g. 0.1.3"}
TAG="Mode-Desktop-App-$V"
check_main "$TAG"

SETUP_V=$(sed -n 's/^ *"version": *"\(.*\)",*$/\1/p' desktop/setup-app/tauri.conf.json | head -1)
[ "$SETUP_V" = "$V" ] || die "desktop/setup-app/tauri.conf.json says $SETUP_V; set it to $V"
CARGO_V=$(sed -n '/^\[workspace.package\]/,/^\[/ s/^version = "\(.*\)"$/\1/p' desktop/Cargo.toml | head -1)
[ "$CARGO_V" = "$V" ] || die "desktop/Cargo.toml says $CARGO_V; set it to $V"
tools/release-notes.sh setup "$V" >/dev/null || die "write the $V section of desktop/CHANGELOG.md first"

echo "== The newest Mode, to carry inside"
MODE_V=$(tools/latest-app-release.sh)
echo "   Mode $MODE_V"

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
# tauri.release.conf.json puts both .ipa files and Mode.version inside the app.
(cd desktop/setup-app && CI=true "$CARGO" tauri build --bundles dmg --target universal-apple-darwin --config tauri.release.conf.json)
DMG=$(ls desktop/target/universal-apple-darwin/release/bundle/dmg/*_"$V"_*.dmg | head -1)
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
# The same file under a fixed name, which heymode.app/download/mac serves from the newest Mode Setup release.
cp "$DMG" build/release/Mode-Setup-mac.dmg

echo "== Draft release $TAG"
NOTES=$(mktemp)
tools/release-notes.sh setup "$V" > "$NOTES"
printf '\nCarries Mode %s inside, and installs any newer Mode that has come out since.\n' "$MODE_V" >> "$NOTES"
gh release create "$TAG" --draft --target main --title "$TAG" --notes-file "$NOTES" \
    "$MAC_DMG" build/release/Mode-Setup-mac.dmg
rm -f "$NOTES"
push_tag "$TAG" "$TAG"
echo "Draft $TAG made. Windows and Linux builds: gh run list --workflow release.yml"
echo "When they are attached, check the files and publish:"
echo "  gh release edit $TAG --draft=false --latest=false"
