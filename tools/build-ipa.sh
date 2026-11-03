#!/bin/sh
# Builds Mode's free build as an unsigned .ipa, for Mode Setup (desktop/) and iLoader to sign and
# install with the person's own Apple account. Free build: Mode + its widget, no special
# entitlements (iosApp/project.yml), so a free Personal Team can sign it.
#
#   tools/build-ipa.sh            # → build/release/Mode.ipa and Mode.ipa.sha256
#
# It generates the free Xcode project to build, then puts back the one you had (paid or free) and
# the Info.plist files xcodegen rewrites.
set -eu

ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT="$ROOT/build/release"
DD="$ROOT/build/ipa-dd"
SAVE=$(mktemp -d)
mkdir -p "$OUT"

cd "$ROOT/iosApp"
WAS=free
grep -q ModeTunnel Mode.xcodeproj/project.pbxproj 2>/dev/null && WAS=paid
for f in Mode/Info.plist ModeWidgets/Info.plist ModeTunnel/Info.plist; do
    [ -f "$f" ] && mkdir -p "$SAVE/$(dirname "$f")" && cp "$f" "$SAVE/$f"
done
restore() {
    cd "$ROOT/iosApp"
    "$ROOT/tools/gen-project.sh" "$WAS" >/dev/null
    (cd "$SAVE" && find . -name Info.plist) | while read -r f; do cp "$SAVE/$f" "$f"; done
    rm -rf "$SAVE"
}
trap restore EXIT

xcodegen generate -q --spec project.yml

xcodebuild -project Mode.xcodeproj -scheme Mode -configuration Release \
    -sdk iphoneos -destination 'generic/platform=iOS' -derivedDataPath "$DD" \
    CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY="" DEVELOPMENT_TEAM="" \
    build -quiet

APP="$DD/Build/Products/Release-iphoneos/Mode.app"
[ -d "$APP" ] || { echo "no Mode.app at $APP" >&2; exit 1; }

STAGE=$(mktemp -d)
mkdir -p "$STAGE/Payload"
cp -R "$APP" "$STAGE/Payload/"
rm -f "$OUT/Mode.ipa"
(cd "$STAGE" && zip -qry "$OUT/Mode.ipa" Payload)
rm -rf "$STAGE"
(cd "$OUT" && shasum -a 256 Mode.ipa > Mode.ipa.sha256)

VERSION=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$APP/Info.plist")
echo "built $OUT/Mode.ipa (Mode $VERSION, $(du -h "$OUT/Mode.ipa" | cut -f1))"
cat "$OUT/Mode.ipa.sha256"
