#!/bin/sh
# Makes a draft GitHub release of Mode, the phone app: tag and title
# Mode-Mobile-Build-<version>, with Mode for iPhone
# (and for Android, once it ships). Mode Setup is released on its own (tools/release-setup.sh) and
# installs the newest published Mode.
#
#   tools/release-app.sh 0.2.0      # → draft release Mode-Mobile-Build-0.2.0
#
# Before: set MARKETING_VERSION in iosApp/project.yml to the version, and write its section in
# CHANGELOG.md (move the Unreleased items into "## 0.2.0 · <date>").
#
# The release holds, each checked by Mode Setup against its .sha256:
#   Mode.ipa, Mode.ipa.sha256             iPhone, the free build (LocalDevVPN)
#   Mode-paid.ipa, Mode-paid.ipa.sha256   iPhone, with Mode's own VPN, for paid Apple accounts
#   Mode.apk, Mode.apk.sha256             Android, when build/release/Mode.apk is there
#
# Publishing makes it GitHub's "Latest" release (Mode Setups up to 0.1.2 look for Mode there).
set -eu
. "$(dirname "$0")/release-common.sh"

V=${1:?"usage: tools/release-app.sh <version>, e.g. 0.2.0"}
TAG="Mode-Mobile-Build-$V"
check_main "$TAG"

APP_V=$(sed -n 's/^ *MARKETING_VERSION: *"\(.*\)"$/\1/p' iosApp/project.yml | head -1)
case "$V" in "$APP_V" | "$APP_V".0) ;; *) die "iosApp/project.yml MARKETING_VERSION is $APP_V; set it to $V";; esac
tools/release-notes.sh app "$V" >/dev/null || die "write the $V section of CHANGELOG.md first"

echo "== Mode for iPhone (.ipa, free and paid)"
tools/build-ipa.sh both
FILES="build/release/Mode.ipa build/release/Mode.ipa.sha256 build/release/Mode-paid.ipa build/release/Mode-paid.ipa.sha256"
if [ -f build/release/Mode.apk ]; then
    echo "== Mode for Android (.apk)"
    (cd build/release && shasum -a 256 Mode.apk > Mode.apk.sha256)
    FILES="$FILES build/release/Mode.apk build/release/Mode.apk.sha256"
fi

echo "== Draft release $TAG"
NOTES=$(mktemp)
tools/release-notes.sh app "$V" > "$NOTES"
# shellcheck disable=SC2086
gh release create "$TAG" --draft --target main --title "$TAG" --notes-file "$NOTES" $FILES
rm -f "$NOTES"
push_tag "$TAG" "$TAG"
echo "Draft $TAG made. Check it, then publish:"
echo "  gh release edit $TAG --draft=false --latest"
echo "Mode Setup installs it from then on; no new Mode Setup is needed."
