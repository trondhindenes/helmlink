# HelmLink

Control an Orca Core autopilot from a Garmin Forerunner 165 watch, using an Android phone as a bridge.

**Architecture:** Watch (Garmin CIQ) &harr; Phone (Android companion app via BLE) &harr; Autopilot (Orca Core HTTP/WebSocket)

## Watch App

Garmin Connect IQ widget written in Monkey C. Targets Forerunner 165.

### Controls

| Input | Action |
|-------|--------|
| START/ENTER | Engage/disengage autopilot |
| UP | Adjust heading + (by selected increment) |
| DOWN | Adjust heading - (by selected increment) |
| Tap +/-1 or +/-10 | Select heading increment |
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
