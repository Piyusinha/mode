#!/bin/sh
# Downloads the newest published Mode release's .ipa files into build/release, checks them against
# their .sha256, and writes build/release/Mode.version, for Mode Setup to carry inside it.
# Used by tools/release-setup.sh and by CI (.github/workflows/release.yml). Prints the version.
#
# Mode releases are tagged "Mode-Mobile-Build-<version>"; the first releases ("v0.1.0" to "v0.1.2")
# held Mode and Mode Setup together and count too. The highest version wins: GitHub's list order
# isn't by version (a release made as a draft and published later can sit below older ones).
set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
REPO=${GITHUB_REPOSITORY:-heymode/mode}
OUT="$ROOT/build/release"
mkdir -p "$OUT"

TAG=$(gh api "repos/$REPO/releases?per_page=50" --jq '
  [.[] | select(.draft == false and .prerelease == false)
       | select(.tag_name | test("^(Mode-Mobile-Build-|v)[0-9]+(\\.[0-9]+)*$"))
       | select([.assets[].name] | index("Mode.ipa"))
       | {tag: .tag_name, v: (.tag_name | sub("^(Mode-Mobile-Build-|v)"; "") | split(".") | map(tonumber))}]
  | sort_by(.v) | last | .tag // empty')
[ -n "$TAG" ] || { echo "latest-app-release: no published Mode release with Mode.ipa" >&2; exit 1; }
V=${TAG#Mode-Mobile-Build-}; V=${V#v}

for f in Mode.ipa Mode.ipa.sha256 Mode-paid.ipa Mode-paid.ipa.sha256; do rm -f "$OUT/$f"; done
gh release download "$TAG" --repo "$REPO" --dir "$OUT" \
    --pattern Mode.ipa --pattern Mode.ipa.sha256 --pattern Mode-paid.ipa --pattern Mode-paid.ipa.sha256 >&2
cd "$OUT"
for ipa in Mode.ipa Mode-paid.ipa; do
    want=$(cut -d' ' -f1 "$ipa.sha256")
    got=$( (command -v sha256sum >/dev/null && sha256sum "$ipa" || shasum -a 256 "$ipa") | cut -d' ' -f1)
    [ "$want" = "$got" ] || { echo "latest-app-release: $ipa doesn't match its .sha256" >&2; exit 1; }
done
printf '%s\n' "$V" > Mode.version
echo "$V"
