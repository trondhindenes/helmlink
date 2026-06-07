# Contributing

## Building

### Garmin watch app

Build from VS Code with the Monkey C extension ("Monkey C: Build Current Project"), or via CLI. Output lands in `watch-app/bin/watchapp.prg`.

The manifest lists multiple supported devices, but a sideloadable `.prg` is built for one device at a time (`monkeyc -d <device>`). Building for a device other than `fr165` requires downloading its device files first via the Connect IQ SDK Manager. The screen layout is scaled from the FR165's 390x390 reference (see `watch-app/source/Layout.mc`) — when adding a new device, verify it in the simulator with that device profile.

### Android companion app

```bash
cd android-app
./gradlew assembleDebug
```

Output: `android-app/app/build/outputs/apk/debug/app-debug.apk`

## Releasing

Releases are published as GitHub releases with the debug binaries attached. Versioning is handled by [autoversion](https://github.com/trondhindenes/autoversion), which derives the version from the commit count on `main` — so always commit and push *before* computing the version, and make sure both binaries are freshly built from that commit.

```bash
# 1. Make sure everything is committed and pushed
git push origin main

# 2. Compute the version
VERSION=$(autoversion 2>/dev/null | jq -r .semver)

# 3. Stage the binaries with friendly names
cp android-app/app/build/outputs/apk/debug/app-debug.apk /tmp/helmlink-companion-debug.apk
cp watch-app/bin/watchapp.prg /tmp/helmlink-watchapp.prg

# 4. Create the release (tags the current main HEAD)
gh release create "$VERSION" \
  /tmp/helmlink-companion-debug.apk \
  /tmp/helmlink-watchapp.prg \
  --title "HelmLink $VERSION" \
  --prerelease \
  --notes "Debug builds:
- **helmlink-companion-debug.apk** — Android companion app (debug-signed, install via sideload)
- **helmlink-watchapp.prg** — Garmin watch app (sideload to the watch's /GARMIN/APPS folder)"
```

Notes:

- `--prerelease` is used while we ship debug builds; drop it for proper release builds.
- The watch `.prg` is signed with the local Connect IQ developer key (configured in VS Code). The `.iq` store package is only needed for Connect IQ store submission.
