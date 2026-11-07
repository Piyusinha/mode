#!/bin/sh
# Android's version of check-ui-literals.sh: fails when a screen hard-codes what belongs to
# the design system or the string resources: colours, fonts, sizes, corner radii, icons, or
# visible text. Screens use Mode.colors, ModeTextStyle, ModeSpacing/ModeSize/ModeRadius,
# ModeIcon and stringResource instead. Runs in `check` (checkUiLiterals) and in CI.
# The design system itself (app/design) defines the tokens, so it is not checked.
set -u
root="$(cd "$(dirname "$0")/.." && pwd)"
dirs="$root/app/android/src/main/kotlin $root/app/android/src/full/kotlin $root/app/android/src/play/kotlin"
patterns='Color\(0x|Color\.(Red|Blue|Green|Black|White|Gray|Yellow|Cyan|Magenta|DarkGray|LightGray)\b|[^A-Za-z_.][0-9]+(\.[0-9]+)?\.(dp|sp)\b|FontWeight\.|FontFamily\(|fontSize *=|RoundedCornerShape\([0-9]|CircleShape|painterResource\(R\.drawable|Icons\.(Default|Filled|Outlined|Rounded)|(Text|ModeText|BasicText)\(\s*"[^"]|text *= *"[^"]'
found=0
for d in $dirs; do
    [ -d "$d" ] || continue
    if grep -rnE "$patterns" "$d" --include='*.kt' | grep -v '// literal-ok'; then found=1; fi
done
if [ "$found" -ne 0 ]; then
    echo "error: screens must use the design system (Mode.colors, ModeTextStyle, ModeSpacing, ModeSize, ModeRadius, ModeIcon) and string resources, not literals (see docs/android.md)." >&2
    exit 1
fi
echo "UI literal check passed (Android)"
