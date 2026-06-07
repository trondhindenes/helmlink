# Orca Core Autopilot HTTP API

Base URL: `http://10.11.12.1:8088/v1/autopilots/{autopilotId}`

All commands are HTTP POST with `Content-Type: application/json`. The `autopilotId` is a numeric ID from the sensor state.

## Modes

| Name         | Value | Description            |
| ------------ | ----- | ---------------------- |
| `STANDBY`    | 0     | Disengaged             |
| `AUTO`       | 1     | Compass / heading hold |
| `NO_DRIFT`   | 2     | No-drift mode          |
| `NAVIGATION` | 4     | Route/waypoint follow  |
| `WIND`       | 7     | Wind vane hold         |

## Engage / Disengage

```
POST /v1/autopilots/{id}/mode
```

```json
{"value": "STANDBY"}      // disengage
{"value": "AUTO"}         // engage heading hold
{"value": "WIND"}         // engage wind mode
{"value": "NAVIGATION"}   // engage route mode
```

Setting `NAVIGATION` mode requires an active route. The app fetches the current route from `GET /v1/navigation/route`, optionally prepends the vessel's current position as an initial waypoint, and broadcasts it as an `AUTOPILOT_ROUTE` hub sync event before setting the mode.

## Set Mode

Same endpoint as engage — just POST the desired mode value.

## Adjust Course

```
POST /v1/autopilots/{id}/course-change
```

```json
{"value": 1}     // +1°
{"value": -1}    // -1°
{"value": 10}    // +10°
{"value": -10}   // -10°
```

Only `1`, `-1`, `10`, `-10` are accepted.

In **WIND** mode, the payload includes an inverted `wind_value`:

```json
{"value": 10, "wind_value": -10}
```

## Tack

```
POST /v1/autopilots/{id}/tack
```

```json
{"value": "PORT"}
{"value": "STARBOARD"}
```

Available on SeaTalk 1, SeaTalk NG, and Yacht Devices autopilots. Not available on Garmin.

## Autopilot Types

| Name          | Value |
| ------------- | ----- |
| `SIMNET`      | 0     |
| `SEATALK_1`   | 1     |
| `SEATALK_NG`  | 2     |
| `GARMIN`      | 3     |
| `YATCH_DEVICES` | 4  |

### Feature support by type

| Type           | Modes                      | Tack |
| -------------- | -------------------------- | ---- |
| SEATALK_1      | AUTO, WIND, NAVIGATION     | Yes  |
| SEATALK_NG     | AUTO, WIND, NAVIGATION     | Yes  |
| GARMIN         | AUTO, WIND, NAVIGATION     | No   |
| YATCH_DEVICES  | AUTO, WIND, NAVIGATION     | Yes  |

## Hub Ports

| Port | Service                |
| ---- | ---------------------- |
| 8088 | HTTP API (commands)    |
| 8089 | WebSocket (sensor data)|
| 8080 | Info                   |
| 9001 | IMU                    |
| 9081 | Radar HTTP             |
| 9089 | Radar WebSocket        |

## Sensor Data (read-only, via WebSocket)

Connect to `ws://10.11.12.1:8089/v1/sensors/full?interval=200&ns=...` to receive autopilot state:

- `autopilot_mode` — current mode
- `autopilot_state` — engaged / standby / error
- `autopilot_courseToSteer` — target course heading
- `autopilot_headingToSteer` — target heading
- `autopilot_headingReference` — true vs magnetic
- `autopilot_windHoldAngle` — wind vane hold angle
- `autopilot_XTE` — cross-track error
- `autopilot_type` — autopilot hardware type
- `autopilot_age` — data staleness
