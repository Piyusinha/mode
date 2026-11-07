#!/bin/sh
# Prints the GitHub release notes for a version: its section of CHANGELOG.md ("What's new"), then
# how to install. Fails when CHANGELOG.md has no section for it.
#
#   tools/release-notes.sh 0.1.2
#   gh release edit v0.1.2 --notes-file <(tools/release-notes.sh 0.1.2)   # fix an existing release
set -eu

V=${1:?"usage: tools/release-notes.sh <version>, e.g. 0.1.2"}
ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)

# The lines after "## <V>" (or "## <V> · date") up to the next "## " heading.
NEW=$(awk -v v="$V" '
    /^## / { if (on) exit; on = ($2 == v) ; next }
    on { print }
' "$ROOT/CHANGELOG.md" | sed -e '/./,$!d' | sed -e ':a' -e '/^\n*$/{$d;N;ba' -e '}')
[ -n "$NEW" ] || { echo "release-notes: CHANGELOG.md has no \"## $V\" section; add one (move the Unreleased items into it)" >&2; exit 1; }

# The changelog's "### Mode" headings read as smaller headings under "What's new".
printf '## What’s new\n\n%s\n\n' "$(printf '%s\n' "$NEW" | sed 's/^### /#### /')"
cat <<'NOTES'
## Install
1. Download **Mode Setup** for your computer from [heymode.app](https://heymode.app), or below:
   - Mac: `Mode-Setup-mac.dmg` (Apple silicon and Intel, signed and notarized by Apple)
   - Windows: `Mode-Setup-windows.exe` (or the .msi)
   - Linux: `Mode-Setup-linux.AppImage` (or the .deb)
2. Open it. It checks your computer first: Windows needs Apple's iTunes from apple.com, Linux
   needs usbmuxd, and it tells you if either is missing. Xcode is not needed.
3. Connect your iPhone with a cable and follow the steps.

Already have Mode? Open Mode Setup and press **Check for updates**.

Mode needs an iPhone on iOS 27 or later. See the [README](https://github.com/Piyusinha/mode#readme)
and the full [changelog](https://github.com/Piyusinha/mode/blob/main/CHANGELOG.md).

## Files
- `Mode.ipa`, `Mode-paid.ipa`: Mode, unsigned. Mode Setup signs it with your Apple account
  (`Mode-paid.ipa` has Mode's own VPN, for paid developer accounts).
- `*.sha256`: their checksums.
NOTES
