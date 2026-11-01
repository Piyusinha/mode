#!/bin/sh
# Fails when a screen hard-codes what belongs to the design system or the string
# catalog: colours, fonts, sizes, corner radii, SF Symbol names, or visible text.
# Screens use ModeColor, ModeTextStyle, ModeSpacing/ModeSize/ModeRadius, ModeIcon
# and L10n instead. Runs as an Xcode build phase and in CI.
set -u
root="$(cd "$(dirname "$0")/.." && pwd)"
dirs="$root/iosApp/Mode/Features $root/iosApp/Mode/App"
patterns='Color\(|UIColor\(|Color\.(red|blue|green|black|white|gray|orange|purple|pink|yellow|primary|secondary)\b|\.font\(\.(system|custom|largeTitle|title|headline|body|callout|caption|footnote|subheadline)|Font\.(system|custom)|\.padding\([0-9]|\.padding\(\.[a-zA-Z]+, *[0-9]|(width|height|minWidth|minHeight|maxWidth|maxHeight): *[0-9]|cornerRadius: *[0-9]|spacing: *[0-9]|Image\(systemName:|Text\("[^"]|LocalizedStringResource\("[^"]'
found=0
for d in $dirs; do
    [ -d "$d" ] || continue
    if grep -rnE "$patterns" "$d" --include='*.swift' | grep -v '// literal-ok'; then found=1; fi
done
if [ "$found" -ne 0 ]; then
    echo "error: screens must use the design system (ModeColor, ModeTextStyle, ModeSpacing, ModeSize, ModeRadius, ModeIcon) and L10n strings, not literals (see docs/architecture.md)." >&2
    exit 1
fi
echo "UI literal check passed"
