# Shared by tools/release-app.sh and tools/release-setup.sh (sourced, not run).
ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
cd "$ROOT"
die() { echo "release: $*" >&2; exit 1; }

# Releases come from main as pushed, so the tag and the files match.
check_main() {  # $1 = tag
    [ "$(git branch --show-current)" = main ] || die "switch to main first"
    git diff --quiet && git diff --cached --quiet || die "commit or stash your changes first"
    git fetch -q origin main --tags
    [ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] || die "push main first (or pull): HEAD differs from origin/main"
    git rev-parse -q --verify "refs/tags/$1" >/dev/null && die "tag $1 already exists"
    return 0
}

# Tags the release's commit; for Mode Setup this also starts CI (Windows and Linux).
push_tag() {  # $1 = tag, $2 = message
    git tag -a "$1" -m "$2"
    git push -q origin "$1"
}
