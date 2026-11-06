#!/bin/sh
# Generates iosApp/Mode.xcodeproj for a paid or a free Apple account.
#
#   tools/gen-project.sh paid   # Mode ships its own loopback VPN extension
#   tools/gen-project.sh free   # no extension; turn on the LocalDevVPN app
set -eu
cd "$(dirname "$0")/../iosApp"
# Your Apple team and app ID, kept out of git: copy Signing.example.env to Signing.env and fill it in.
if [ -f Signing.env ]; then
    set -a; . ./Signing.env; set +a
else
    echo "note: no iosApp/Signing.env; building unsigned with com.example.mode (copy Signing.example.env to set your team)" >&2
fi
export MODE_TEAM="${MODE_TEAM:-}"
export MODE_BUNDLE_PREFIX="${MODE_BUNDLE_PREFIX:-com.example.mode}"
case "${1:-}" in
    paid) xcodegen generate -q --spec project.paid.yml ;;
    free) xcodegen generate -q --spec project.yml ;;
    *) echo "usage: $0 paid|free" >&2; exit 2 ;;
esac
echo "generated Mode.xcodeproj ($1)"
