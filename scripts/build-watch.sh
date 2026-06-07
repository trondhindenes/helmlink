#!/usr/bin/env bash
# Build a sideloadable .prg for every device listed in the watch app manifest.
# Output: dist/helmlink-watchapp-<device>.prg
#
# Devices whose device files aren't installed (via the Connect IQ SDK Manager)
# are skipped with a warning.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CIQ_HOME="$HOME/Library/Application Support/Garmin/ConnectIQ"
MANIFEST="$ROOT/watch-app/manifest.xml"
DIST="$ROOT/dist"

SDK_CFG="$CIQ_HOME/current-sdk.cfg"
if [[ ! -f "$SDK_CFG" ]]; then
    echo "error: $SDK_CFG not found - is the Connect IQ SDK installed?" >&2
    exit 1
fi
MONKEYC="$(cat "$SDK_CFG")bin/monkeyc"

DEVELOPER_KEY="${DEVELOPER_KEY:-$CIQ_HOME/developer_key.der}"
if [[ ! -f "$DEVELOPER_KEY" ]]; then
    echo "error: developer key not found at $DEVELOPER_KEY (override with DEVELOPER_KEY=...)" >&2
    exit 1
fi

devices=$(sed -n 's/.*<iq:product id="\([^"]*\)".*/\1/p' "$MANIFEST")
if [[ -z "$devices" ]]; then
    echo "error: no products found in $MANIFEST" >&2
    exit 1
fi

mkdir -p "$DIST"
built=()
skipped=()

cd "$ROOT/watch-app"
for device in $devices; do
    if [[ ! -d "$CIQ_HOME/Devices/$device" ]]; then
        skipped+=("$device")
        continue
    fi
    echo "==> building $device"
    "$MONKEYC" -f monkey.jungle -o "$DIST/helmlink-watchapp-$device.prg" \
        -y "$DEVELOPER_KEY" -d "$device"
    built+=("$device")
done

echo
echo "built ${#built[@]} device(s): ${built[*]:-none}"
if [[ ${#skipped[@]} -gt 0 ]]; then
    echo "skipped ${#skipped[@]} device(s) with no device files installed: ${skipped[*]}" >&2
    echo "install them via the Connect IQ SDK Manager, then re-run" >&2
fi
