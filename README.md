# HelmLink

Control an Orca Core autopilot from a Garmin Forerunner 165 watch, using an Android phone as a bridge.

**Architecture:** Watch (Garmin CIQ) &harr; Phone (Android companion app via BLE) &harr; Autopilot (Orca Core HTTP/WebSocket)

## Installing from releases

Pre-built binaries are available on the [releases page](https://github.com/trondhindenes/helmlink/releases) as an alternative to building from source:

- **helmlink-companion-debug.apk** — the Android companion app. Sideload it onto the phone (e.g. `adb install -r helmlink-companion-debug.apk`, or transfer the file and open it — requires allowing installs from unknown sources).
- **helmlink-watchapp.prg** — the Garmin watch app, built for the **Forerunner 165**. Copy it to the watch's `GARMIN/Apps/` folder over USB (see [Deploying to watch](#deploying-to-watch)). For other watch models, build from source for your device.

## Watch App

Garmin Connect IQ widget written in Monkey C.

### Supported watches

Developed and tested on the **Forerunner 165**. The manifest also includes other 5-button watches with Connect IQ 4.2.0+ — these are expected to work but are untested, and the layout is scaled rather than tuned per device:

- Forerunner 165 / 165 Music (tested), 255 / 255S (incl. Music), 265 / 265S, 955, 965
- fenix 7 / 7S / 7X (incl. Pro)
- epix (Gen 2) / epix Pro (42/47/51mm)

Note that the pre-built `.prg` on the releases page is compiled for the FR165 specifically — for any other watch you currently need to build from source with `-d <device>` (see below).

### Controls

| Input | Action |
|-------|--------|
| START/ENTER | Engage/disengage autopilot |
| UP | Adjust heading + (by selected increment) |
| DOWN | Adjust heading - (by selected increment) |
| Tap +/-1 or +/-10 | Select heading increment |
| Hold UP (menu) | Toggle heading increment (for non-touch watches) |
| Swipe | Cycle mode (AUTO / WIND / NO DRIFT) |
| ESC | Exit app |

### Building

Requires [Connect IQ SDK 9.1.0+](https://developer.garmin.com/connect-iq/sdk/) and a developer key.

Build from VS Code with the Monkey C extension, or from command line:

```sh
cd watch-app
monkeyc -f monkey.jungle -o bin/AutopilotWidget.prg -y /path/to/developer_key.der -d fr165
```

### Deploying to watch

Connect the watch via USB (requires an MTP app on macOS such as "OpenMTP") and copy the `.prg` file to `GARMIN/Apps/`.

## Android Companion App

Android app that bridges watch commands to the Orca Core API.

### Prerequisites

- JDK 17 (the project is pinned to Corretto 17 in `gradle.properties`)
- Android SDK (compileSdk 36)

### Building

```sh
cd android-app
./gradlew assembleDebug
```

The APK is output to `app/build/outputs/apk/debug/app-debug.apk`.

### Deploying to phone

With USB debugging enabled and the phone connected:

```sh
cd android-app
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

One-liner build + deploy:

```sh
cd android-app
./gradlew assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Features

- **Test mode**: Simulates autopilot without Orca hardware connected
- **mDNS autodiscovery**: Finds Orca Core on the local network automatically
- **Manual host**: Configure host address when autodiscovery isn't available
- **Autopilot ID selection**: Supports multiple autopilot instances

## Orca Core API

The companion app communicates with Orca Core over:
- **HTTP** (port 8088): Commands (engage, disengage, mode change, course adjust)
- **WebSocket** (port 8089): Real-time sensor/state feed

See `docs/orca-autopilot-api.md` for the full API reference.

## Supported Autopilot Modes

- **AUTO**: Holds a compass heading
- **WIND**: Holds a wind angle
- **NO DRIFT**: Holds position using GPS

NAV mode is not supported.
