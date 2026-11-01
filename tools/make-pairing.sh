#!/bin/sh
# One-time setup: pairs this Mac's USB connection with the iPhone and gives the
# remote-pairing file to Mode, which then reaches its own phone's developer
# services with it. Run again only if the phone stops trusting it (Developer
# Mode turned off and on, or Location & Privacy reset).
#
#   tools/make-pairing.sh                  # first iPhone on USB, Mode already installed
#   DEVICE=<udid> tools/make-pairing.sh
#
# The phone may ask to Trust this computer: tap Trust. The file is a credential
# for the phone; it stays in build/ (git-ignored) and in Mode's own container.
set -eu

ROOT=$(cd "$(dirname "$0")/.." && pwd)
CARGO=${CARGO:-$HOME/.cargo/bin/cargo}
# Must match PhoneConnector's hostname (engine/phone-ios).
HOSTNAME_FOR_PAIRING=Mode
BUNDLE_ID=com.piyush.mode
OUT="$ROOT/build/pairing/mode.rppairing"

mkdir -p "$(dirname "$OUT")"
# Pairs over plain USB (usbmuxd), so it works even when Xcode's own pairing
# with the phone is gone (Stop Session on the phone removes both).
(cd "$ROOT/native/modecore" && "$CARGO" run -q --features usb --example mc -- pair "$HOSTNAME_FOR_PAIRING" "$OUT")
chmod 600 "$OUT"

# Into Mode's Documents: through the file-sharing service if pymobiledevice3
# is installed (USB only), else devicectl (needs Xcode's pairing).
PMD=${PMD:-$(command -v pymobiledevice3 || echo "$HOME/.local/bin/pymobiledevice3")}
if [ -x "$PMD" ]; then
    "$PMD" apps push --documents "$BUNDLE_ID" "$OUT" /Documents/mode.rppairing >/dev/null
else
    DEVICE=${DEVICE:-$(xcrun devicectl list devices 2>/dev/null | awk '/physical/ && /iPhone/ && /connected/ {for (i=1;i<=NF;i++) if ($i ~ /^[0-9A-F]{8}-[0-9A-F]{16}$/) {print $i; exit}}')}
    [ -n "$DEVICE" ] || { echo "no connected iPhone for devicectl; install pymobiledevice3 or set DEVICE" >&2; exit 1; }
    xcrun devicectl device copy to --device "$DEVICE" \
        --domain-type appDataContainer --domain-identifier "$BUNDLE_ID" \
        --source "$OUT" --destination Documents/mode.rppairing >/dev/null
fi
echo "pairing file installed in Mode (Documents/mode.rppairing)"
