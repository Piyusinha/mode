#!/bin/sh
# Generates iosApp/Mode.xcodeproj for a paid or a free Apple account.
#
#   tools/gen-project.sh paid   # Mode ships its own loopback VPN extension
#   tools/gen-project.sh free   # no extension; turn on the LocalDevVPN app
set -eu
cd "$(dirname "$0")/../iosApp"
case "${1:-}" in
    paid) xcodegen generate -q --spec project.paid.yml ;;
    free) xcodegen generate -q --spec project.yml ;;
    *) echo "usage: $0 paid|free" >&2; exit 2 ;;
esac
echo "generated Mode.xcodeproj ($1)"
