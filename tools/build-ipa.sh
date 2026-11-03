#!/bin/sh
# Builds Mode as unsigned .ipa files, for Mode Setup (desktop/) and iLoader to sign and install
# with the person's own Apple account.
#   free: Mode + its widget, no special entitlements (iosApp/project.yml); any team can sign it,
#         and it uses the LocalDevVPN app.
#   paid: also Mode's own VPN extension (project.paid.yml); a paid team signs it once Network
#         Extensions is on for Mode's app IDs, which Mode Setup switches on.
#
#   tools/build-ipa.sh [free|paid|both]   # → build/release/Mode.ipa, Mode-paid.ipa (+ .sha256)
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

build() {  # $1 = free|paid
    if [ "$1" = paid ]; then SPEC=project.paid.yml; NAME=Mode-paid.ipa; else SPEC=project.yml; NAME=Mode.ipa; fi
    cd "$ROOT/iosApp"
    xcodegen generate -q --spec "$SPEC"
    xcodebuild -project Mode.xcodeproj -scheme Mode -configuration Release \
        -sdk iphoneos -destination 'generic/platform=iOS' -derivedDataPath "$DD-$1" \
        CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY="" DEVELOPMENT_TEAM="" \
        build -quiet
    APP="$DD-$1/Build/Products/Release-iphoneos/Mode.app"
    [ -d "$APP" ] || { echo "no Mode.app at $APP" >&2; exit 1; }
    STAGE=$(mktemp -d)
    mkdir -p "$STAGE/Payload"
    cp -R "$APP" "$STAGE/Payload/"
    rm -f "$OUT/$NAME"
    (cd "$STAGE" && zip -qry "$OUT/$NAME" Payload)
    rm -rf "$STAGE"
    (cd "$OUT" && shasum -a 256 "$NAME" > "$NAME.sha256")
    VERSION=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$APP/Info.plist")
    echo "built $OUT/$NAME (Mode $VERSION, $1, $(du -h "$OUT/$NAME" | cut -f1)) · $(ls "$APP/PlugIns" | tr '\n' ' ')"
}

case "${1:-both}" in
    free) build free ;;
    paid) build paid ;;
    both) build free; build paid ;;
    *) echo "usage: $0 [free|paid|both]" >&2; exit 2 ;;
esac
