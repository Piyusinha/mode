#!/bin/sh
# Builds Mode and starts the engine on a connected iPhone, hosted inside
# Mode.app (one app, no separate runner), reachable from this Mac over the
# USB tunnel. Phase 2 replaces this with a launch from the phone itself.
#
#   MODE_ENGINE_TOKEN=... tools/run-engine.sh            # first USB iPhone
#   DEVICE=<udid> MODE_ENGINE_TOKEN=... tools/run-engine.sh
#
# Prints MODE_ENGINE_URL for tools/engine.py once the engine is up. The log
# goes to build/engine.log; `tools/engine.py stop` ends the engine.
set -eu

ROOT=$(cd "$(dirname "$0")/.." && pwd)
: "${MODE_ENGINE_TOKEN:?set MODE_ENGINE_TOKEN to the bearer token clients will send}"
DEVICE=${DEVICE:-$(xcrun devicectl list devices 2>/dev/null | awk '/physical/ && /iPhone/ && /connected/ {for (i=1;i<=NF;i++) if ($i ~ /^[0-9A-F]{8}-[0-9A-F]{16}$/) {print $i; exit}}')}
[ -n "$DEVICE" ] || { echo "no connected iPhone; plug one in or set DEVICE" >&2; exit 1; }

cd "$ROOT/iosApp"
xcodegen generate -q
xcodebuild -project Mode.xcodeproj -scheme Mode -destination "id=$DEVICE" \
    -derivedDataPath ../build/xcode -allowProvisioningUpdates build-for-testing -quiet

# Xcode records ModeEngineHosted as an app-hosted unit test. Marked as a UI
# test (with a target app), testmanagerd authorizes it to drive other apps,
# which is all the separate runner app was for.
PRODUCTS="$ROOT/build/xcode/Build/Products"
XCTESTRUN=$(ls -t "$PRODUCTS"/*.xctestrun | head -1)
python3 - "$XCTESTRUN" "$PRODUCTS/engine.xctestrun" <<'PY'
import plistlib, sys
src, dst = sys.argv[1], sys.argv[2]
run = plistlib.load(open(src, "rb"))
hosted = run["ModeEngineHosted"]
hosted["IsUITestBundle"] = True
hosted["UITargetAppPath"] = "__TESTROOT__/Debug-iphoneos/Mode.app"
hosted["OnlyTestIdentifiers"] = ["EngineTests/testServe"]
plistlib.dump({"__xctestrun_metadata__": run["__xctestrun_metadata__"], "ModeEngineHosted": hosted}, open(dst, "wb"))
PY

LOG="$ROOT/build/engine.log"
TEST_RUNNER_MODE_ENGINE_TOKEN=$MODE_ENGINE_TOKEN \
TEST_RUNNER_MODE_ENGINE_BIND_ALL=1 \
TEST_RUNNER_MODE_ENGINE_IDLE_SECONDS=${MODE_ENGINE_IDLE_SECONDS:-600} \
    nohup xcodebuild test-without-building -xctestrun "$PRODUCTS/engine.xctestrun" \
    -destination "id=$DEVICE" > "$LOG" 2>&1 &

for _ in $(seq 1 90); do
    grep -qE 'MODE_ENGINE ready|TEST (SUCCEEDED|FAILED)' "$LOG" && break
    sleep 1
done
grep -q 'MODE_ENGINE ready' "$LOG" || { echo "engine did not start; see $LOG" >&2; exit 1; }

TUNNEL=$(xcrun devicectl device info details --device "$DEVICE" 2>/dev/null | awk '/Tunnel IP Address/ {print $NF}')
echo "engine ready: export MODE_ENGINE_URL=http://[$TUNNEL]:47610"
