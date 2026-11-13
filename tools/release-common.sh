# Shared by tools/release-app.sh and tools/release-setup.sh (sourced, not run).
ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
cd "$ROOT"
die() { echo "release: $*" >&2; exit 1; }

# Versions count 0.1.8, 0.1.9, 0.2.0: three parts, each a single digit, so a part never reaches 10
# (0.2.0 follows 0.1.9, never 0.1.10).
check_version() {  # $1 = version
    case "$1" in
        [0-9].[0-9].[0-9]) return 0 ;;
        *) die "version $1: use three single digits, 0.2.0 after 0.1.9 (never 0.1.10)" ;;
    esac
}

# Releases come from main as pushed, so the tag and the files match.
check_main() {  # $1 = tag
    check_version "${1##*-}"
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
