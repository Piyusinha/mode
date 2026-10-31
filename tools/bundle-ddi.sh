#!/bin/sh
# Copies the iOS developer disk image (Cryptex1 variant, what iOS 27 mounts)
# from this Mac's Xcode into the app bundle, so Mode can remount it itself
# after the phone restarts. Only the files the manifest's cryptex identity
# names are copied (~17 MB), from Apple's own copy, not a download.
#
#   tools/bundle-ddi.sh <destination folder>
#   MODE_DDI_SOURCE=<Restore folder> tools/bundle-ddi.sh <dest>   # another Xcode's DDI
#
# Run by the Mode target's build phase. If no DDI is installed it warns and
# exits 0: the app still builds, it just cannot remount after a restart.
set -eu
DEST=${1:?usage: bundle-ddi.sh <destination folder>}
SRC=${MODE_DDI_SOURCE:-/Library/Developer/DeveloperDiskImages/iOS_DDI/Restore}

if [ ! -f "$SRC/BuildManifest.plist" ]; then
    echo "warning: no iOS developer disk image at $SRC; Mode will not be able to remount it after a restart" >&2
    exit 0
fi

/usr/bin/python3 - "$SRC" "$DEST" <<'PY'
import os, plistlib, shutil, sys
src, dest = sys.argv[1], sys.argv[2]
manifest = plistlib.load(open(os.path.join(src, "BuildManifest.plist"), "rb"))
keys = ["Cryptex1,GenericDmg", "Cryptex1,GenericTrustCache", "Cryptex1,CryptexInfoPlist", "Cryptex1,GenericVolume"]
identity = next((b for b in manifest["BuildIdentities"]
                 if all(k in b.get("Manifest", {}) for k in keys)), None)
if identity is None:
    sys.exit(f"error: {src}/BuildManifest.plist has no Cryptex1 developer disk image identity")
files = ["BuildManifest.plist"] + [identity["Manifest"][k]["Info"]["Path"] for k in keys]
for rel in files:
    target = os.path.join(dest, rel)
    os.makedirs(os.path.dirname(target), exist_ok=True)
    source = os.path.join(src, rel)
    if not os.path.exists(target) or os.path.getmtime(target) < os.path.getmtime(source):
        shutil.copy2(source, target)
print(f"bundled developer disk image ({len(files)} files) into {dest}")
PY
