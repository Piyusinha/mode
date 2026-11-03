#!/bin/sh
# Build the desktop app; use a stable local signing identity across debug rebuilds on macOS.
# MODE_SETUP_SIGN_IDENTITY overrides auto-detection. No certificate or Keychain ACL is changed.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
CARGO=${CARGO:-"$HOME/.cargo/bin/cargo"}
"$CARGO" build --manifest-path "$ROOT/desktop/Cargo.toml" -p mode-setup-app
if [ "$(uname -s)" = Darwin ]; then
    IDENTITY=${MODE_SETUP_SIGN_IDENTITY:-}
    if [ -z "$IDENTITY" ]; then
        IDENTITIES=$(/usr/bin/security find-identity -v -p codesigning | awk '/"Apple Development:/ {print $2}')
        COUNT=$(printf '%s\n' "$IDENTITIES" | awk 'NF {n++} END {print n+0}')
        if [ "$COUNT" = 1 ]; then IDENTITY=$IDENTITIES; fi
    fi
    if [ -n "$IDENTITY" ]; then
        /usr/bin/codesign --force --sign "$IDENTITY" --identifier app.mode.setup "$ROOT/desktop/target/debug/mode-setup-app"
        /usr/bin/codesign --verify --strict "$ROOT/desktop/target/debug/mode-setup-app"
    else
        echo 'No unique Apple Development identity found; set MODE_SETUP_SIGN_IDENTITY to sign debug rebuilds consistently.'
    fi
fi
