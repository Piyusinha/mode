#!/bin/sh
# Builds modecore (native/modecore) as static libraries for the iPhone and the
# Apple-silicon simulator, for the Kotlin cinterop in shared/phone-coredevice.
#
#   tools/build-native.sh            # release (what the app links)
#   PROFILE=debug tools/build-native.sh
#
# Output: native/out/<target>/libmodecore.a, header in native/modecore/include.
set -eu

ROOT=$(cd "$(dirname "$0")/.." && pwd)
CARGO=${CARGO:-$HOME/.cargo/bin/cargo}
PROFILE=${PROFILE:-release}
# Matches iosApp/project.yml's deploymentTarget.
export IPHONEOS_DEPLOYMENT_TARGET=26.0
# Bitcode would bloat the static library by ~140 MB for no gain. Never `strip -S`
# the result: it corrupts ring's assembly objects and the link fails.
export RUSTFLAGS="-C embed-bitcode=no"

cd "$ROOT/native/modecore"
for target in aarch64-apple-ios aarch64-apple-ios-sim; do
    case $target in
        aarch64-apple-ios) sdk=iphoneos; out=ios-arm64 ;;
        aarch64-apple-ios-sim) sdk=iphonesimulator; out=ios-simulator-arm64 ;;
    esac
    SDKROOT=$(xcrun --sdk $sdk --show-sdk-path) \
        "$CARGO" build --target $target $( [ "$PROFILE" = release ] && echo --release ) --lib
    mkdir -p "$ROOT/native/out/$out"
    cp "target/$target/$PROFILE/libmodecore.a" "$ROOT/native/out/$out/"
    echo "built native/out/$out/libmodecore.a ($(du -h "$ROOT/native/out/$out/libmodecore.a" | cut -f1))"
done
