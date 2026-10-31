#!/bin/sh
# Copies Mode's debug logs (runs, steps, screenshots, app events) from the
# phone to build/logs/<time>/ on this Mac, over USB.
#
#   tools/pull-logs.sh               # first iPhone on USB
#   DEVICE=<udid> tools/pull-logs.sh
#
# Live app events instead: pymobiledevice3 syslog live | grep "Mode "
set -eu
ROOT=$(cd "$(dirname "$0")/.." && pwd)
DEVICE=${DEVICE:-$(xcrun devicectl list devices 2>/dev/null | awk '/physical/ && /iPhone/ && /connected/ {for (i=1;i<=NF;i++) if ($i ~ /^[0-9A-F]{8}-[0-9A-F]{16}$/) {print $i; exit}}')}
[ -n "$DEVICE" ] || { echo "no connected iPhone; plug one in or set DEVICE" >&2; exit 1; }
OUT="$ROOT/build/logs/$(date +%Y-%m-%dT%H-%M-%S)"
mkdir -p "$OUT"
xcrun devicectl device copy from --device "$DEVICE" --domain-type appDataContainer \
    --domain-identifier com.piyush.mode --source Documents/logs --destination "$OUT" >/dev/null
echo "logs in $OUT"
ls "$OUT"
