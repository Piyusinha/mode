#!/bin/sh
# Prints the GitHub release notes for a Mode or Mode Setup version: its changelog section
# ("What's new"), then how to install. Fails when the changelog has no section for it.
#
#   tools/release-notes.sh app 0.2.0      # Mode: CHANGELOG.md
#   tools/release-notes.sh setup 0.1.3    # Mode Setup: desktop/CHANGELOG.md
set -eu

KIND=${1:?"usage: tools/release-notes.sh app|setup <version>"}
V=${2:?"usage: tools/release-notes.sh app|setup <version>"}
ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
case "$KIND" in
    app) LOG="$ROOT/CHANGELOG.md" ;;
    setup) LOG="$ROOT/desktop/CHANGELOG.md" ;;
    *) echo "release-notes: app or setup, not $KIND" >&2; exit 2 ;;
esac

# The lines after "## <V>" (or "## <V> · date") up to the next "## " heading, without blank edges.
NEW=$(awk -v v="$V" '
    /^## / { if (on) exit; on = ($2 == v); next }
    on { print }
' "$LOG" | sed -e '/./,$!d' | sed -e ':a' -e '/^\n*$/{$d;N;ba' -e '}')
[ -n "$NEW" ] || { echo "release-notes: $(basename "$(dirname "$LOG")")/$(basename "$LOG") has no \"## $V\" section; add one (move the Unreleased items into it)" >&2; exit 1; }

printf '## What’s new\n\n%s\n\n' "$(printf '%s\n' "$NEW" | sed 's/^### /#### /')"
if [ "$KIND" = app ]; then cat <<'NOTES'
## Install or update
Mode is installed with **Mode Setup** on a Mac, Windows or Linux computer: get it from
[heymode.app](https://heymode.app). It installs this version of Mode for you.

Already have Mode? Open Mode Setup and press **Check for updates**.

Mode needs an iPhone on iOS 27 or later. Android is next. See the
[README](https://github.com/heymode/mode#readme) and the [changelog](https://github.com/heymode/mode/blob/main/CHANGELOG.md).

## Files
- `Mode.ipa`: Mode for iPhone, unsigned. Mode Setup signs it with your Apple account.
- `Mode-paid.ipa`: the same, with Mode's own VPN, for paid Apple developer accounts.
- `*.sha256`: their checksums.
NOTES
else cat <<'NOTES'
## Install
1. Download **Mode Setup** for your computer from [heymode.app](https://heymode.app), or below:
   - Mac: `Mode-Setup-mac.dmg` (Apple silicon and Intel, signed and notarized by Apple)
   - Windows: `Mode-Setup-windows.exe` (or the .msi)
   - Linux: `Mode-Setup-linux.AppImage` (or the .deb)
2. Open it. It checks your computer first: Windows needs Apple's iTunes from apple.com, Linux
   needs usbmuxd, and it tells you if either is missing. Xcode is not needed.
   On Windows, if it says "Windows protected your PC", click **More info**, then **Run anyway**:
   the installer isn't signed with a paid Windows certificate.
3. Connect your iPhone with a cable and follow the steps. Mode Setup installs the newest Mode.

See the [changelog](https://github.com/heymode/mode/blob/main/desktop/CHANGELOG.md).
NOTES
fi
