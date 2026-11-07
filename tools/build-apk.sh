#!/bin/sh
# Builds Mode for Android into dist/.
#   tools/build-apk.sh                  # full release → dist/Mode.apk (+ .sha256)
#   tools/build-apk.sh play release     # the Play build → dist/Mode-play.apk
#   tools/build-apk.sh full debug       # a debug build
#
# Release signing comes from the environment; without it the APK is unsigned (fine for trying it, not for
# sharing):
#   MODE_ANDROID_KEYSTORE=/path/to/mode.jks MODE_ANDROID_KEYSTORE_PASSWORD=… MODE_ANDROID_KEY_ALIAS=mode
#   [MODE_ANDROID_KEY_PASSWORD=…]  (defaults to the keystore password)
# Keep the same key for every release: Android only updates an app signed with the key it was installed with.
set -eu

FLAVOR=${1:-full}
TYPE=${2:-release}
case "$FLAVOR" in full|play) ;; *) echo "build-apk: flavour is full or play" >&2; exit 2 ;; esac
case "$TYPE" in release|debug) ;; *) echo "build-apk: type is release or debug" >&2; exit 2 ;; esac

ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
cd "$ROOT"
CAP=$(printf '%s' "$FLAVOR" | cut -c1 | tr a-z A-Z)$(printf '%s' "$FLAVOR" | cut -c2-)
TCAP=$(printf '%s' "$TYPE" | cut -c1 | tr a-z A-Z)$(printf '%s' "$TYPE" | cut -c2-)
./gradlew -q ":app:android:assemble$CAP$TCAP"

OUT="$ROOT/dist"
mkdir -p "$OUT"
NAME=Mode
[ "$FLAVOR" = play ] && NAME=Mode-play
[ "$TYPE" = debug ] && NAME="$NAME-debug"
APK=$(find "app/android/build/outputs/apk/$FLAVOR/$TYPE" -name '*.apk' | head -1)
[ -n "$APK" ] || { echo "build-apk: no APK was built" >&2; exit 1; }
cp "$APK" "$OUT/$NAME.apk"
(cd "$OUT" && shasum -a 256 "$NAME.apk" > "$NAME.apk.sha256")
case "$APK" in *unsigned*) echo "build-apk: unsigned (set MODE_ANDROID_KEYSTORE to sign)" ;; esac
echo "$OUT/$NAME.apk"
