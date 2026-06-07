# Orca Core Simulator

Simulates an Orca Core unit so the HelmLink companion app can be tested
without being on the boat. Implements the HTTP command API, the sensor
WebSocket stream, and mDNS advertisement for auto-discovery.

## Run

```bash
uv run orca_simulator.py
```

Requires [uv](https://docs.astral.sh/uv/) (installed via Homebrew); it pulls
`aiohttp` automatically on first run.

## What it does

- **HTTP :8088** — `GET /v1/autopilots`, `POST /v1/autopilots/{id}/mode`,
  `POST /v1/autopilots/{id}/course-change`. State changes apply when the
  (delayed) response is sent.
- **WS :8089** — `/v1/sensors/full?interval=500` pushes sensor frames
  (headingToSteer in radians, mode, windHoldAngle) on the requested interval.
- **Random response delays** of 0.5–20 s per HTTP request, to exercise the
  app's ack/pending/timeout handling.
- **mDNS** — advertises `orca-sim001 ORCA` on `_http._tcp` via macOS `dns-sd`,
  matching the app's auto-discovery pattern. The phone must be on the same
  Wi-Fi network as this machine.

## Options

| Flag | Default | Description |
|---|---|---|
| `--min-delay` / `--max-delay` | 0.5 / 20.0 | response delay range (seconds) |
| `--http-port` / `--ws-port` | 8088 / 8089 | listen ports |
| `--autopilot-id` | 0 | autopilot id reported and used in sensor keys |
| `--initial-heading` | 270 | starting heading (degrees) |
| `--no-mdns` | off | skip dns-sd advertisement |
| `--verbose` | off | log every websocket frame |

Examples:

```bash
# fast responses for functional testing
uv run orca_simulator.py --min-delay 0.2 --max-delay 1

# worst-case latency only
uv run orca_simulator.py --min-delay 15 --max-delay 20
```

If auto-discovery doesn't work (different network segment), turn off
auto-discover in the app settings and enter this machine's LAN IP as the host.
