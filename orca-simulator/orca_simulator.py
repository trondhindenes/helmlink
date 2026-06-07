#!/usr/bin/env python3
# /// script
# requires-python = ">=3.10"
# dependencies = ["aiohttp>=3.9"]
# ///
"""
Orca Core simulator for testing the Autopilot Companion app.

Speaks the same protocol the app expects:
  HTTP  :8088  GET  /v1/autopilots                     -> {"results": [0]}
  HTTP  :8088  POST /v1/autopilots/{id}/mode           <- {"value": 0|1|2|7}
  HTTP  :8088  POST /v1/autopilots/{id}/course-change  <- {"value": deg, "wind_value": deg?}
  WS    :8089  /v1/sensors/full?interval=500           -> sensor frames

Every HTTP response is delayed by a random amount (default 0.5s-20s) to
exercise the app's pending/ack/timeout handling. Sensor frames are pushed
on the configured interval and reflect state changes as they are applied.

Run:  uv run orca_simulator.py
"""

import argparse
import asyncio
import atexit
import json
import math
import random
import shutil
import subprocess
import sys
import time

from aiohttp import web, WSMsgType

MODE_NAMES = {0: "STANDBY", 1: "AUTO", 2: "NO_DRIFT", 7: "WIND"}


def log(msg: str) -> None:
    print(f"[{time.strftime('%H:%M:%S')}] {msg}", flush=True)


class OrcaState:
    def __init__(self, autopilot_id: int, heading: int):
        self.autopilot_id = autopilot_id
        self.mode = 0  # STANDBY
        self.heading = heading % 360  # degrees
        self.wind_hold_angle = 0  # degrees

    def frame(self) -> dict:
        ap = self.autopilot_id
        return {
            "values": {
                f"steering.headingControl.{ap}.headingToSteer": math.radians(self.heading),
                f"autopilot.{ap}.mode": self.mode,
                f"autopilot.{ap}.windHoldAngle": math.radians(self.wind_hold_angle),
            }
        }


class Simulator:
    def __init__(self, args):
        self.args = args
        self.state = OrcaState(args.autopilot_id, args.initial_heading)

    async def delay(self, what: str) -> float:
        d = random.uniform(self.args.min_delay, self.args.max_delay)
        log(f"{what}: delaying response {d:.1f}s")
        await asyncio.sleep(d)
        return d

    # --- HTTP handlers (port 8088) ---

    async def get_autopilots(self, request: web.Request) -> web.Response:
        await self.delay("GET /v1/autopilots")
        return web.json_response({"results": [self.state.autopilot_id]})

    async def post_mode(self, request: web.Request) -> web.Response:
        body = await request.json()
        value = int(body.get("value", 0))
        await self.delay(f"POST mode value={value} ({MODE_NAMES.get(value, '?')})")
        self.state.mode = value
        log(f"  -> mode is now {MODE_NAMES.get(value, value)}")
        return web.json_response({})

    async def post_course_change(self, request: web.Request) -> web.Response:
        body = await request.json()
        value = int(body.get("value", 0))
        wind_value = body.get("wind_value")
        await self.delay(f"POST course-change value={value} wind_value={wind_value}")
        self.state.heading = (self.state.heading + value) % 360
        if wind_value is not None:
            self.state.wind_hold_angle += int(wind_value)
        log(f"  -> heading is now {self.state.heading:03d}")
        return web.json_response({})

    # --- WebSocket handler (port 8089) ---

    async def ws_sensors(self, request: web.Request) -> web.WebSocketResponse:
        interval_ms = int(request.query.get("interval", "500"))
        ws = web.WebSocketResponse()
        await ws.prepare(request)
        peer = request.remote
        log(f"WS client connected from {peer} (interval={interval_ms}ms)")

        async def reader():
            # Drain incoming messages so close/ping are handled.
            async for msg in ws:
                if msg.type == WSMsgType.ERROR:
                    break

        reader_task = asyncio.create_task(reader())
        frames = 0
        try:
            while not ws.closed:
                await ws.send_str(json.dumps(self.state.frame()))
                frames += 1
                if self.args.verbose:
                    log(f"WS frame #{frames}: heading={self.state.heading:03d} "
                        f"mode={MODE_NAMES.get(self.state.mode, '?')}")
                await asyncio.sleep(interval_ms / 1000)
        except (ConnectionResetError, asyncio.CancelledError):
            pass
        finally:
            reader_task.cancel()
            log(f"WS client {peer} disconnected after {frames} frames")
        return ws


def start_mdns(http_port: int) -> subprocess.Popen | None:
    """Advertise as Orca Core via macOS dns-sd so app auto-discovery finds us."""
    if shutil.which("dns-sd") is None:
        log("dns-sd not found - skipping mDNS advertisement (use manual host in app)")
        return None
    name = "orca-sim001 ORCA"  # matches the app's ^orca-[a-zA-Z0-9]{6}(-\d)? ORCA$ pattern
    proc = subprocess.Popen(
        ["dns-sd", "-R", name, "_http._tcp", ".", str(http_port)],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    atexit.register(proc.terminate)
    log(f'mDNS: advertising "{name}" on _http._tcp port {http_port}')
    return proc


async def main(args) -> None:
    sim = Simulator(args)

    http_app = web.Application()
    http_app.router.add_get("/v1/autopilots", sim.get_autopilots)
    http_app.router.add_post("/v1/autopilots/{id}/mode", sim.post_mode)
    http_app.router.add_post("/v1/autopilots/{id}/course-change", sim.post_course_change)

    ws_app = web.Application()
    ws_app.router.add_get("/v1/sensors/full", sim.ws_sensors)

    http_runner = web.AppRunner(http_app)
    ws_runner = web.AppRunner(ws_app)
    await http_runner.setup()
    await ws_runner.setup()
    await web.TCPSite(http_runner, "0.0.0.0", args.http_port).start()
    await web.TCPSite(ws_runner, "0.0.0.0", args.ws_port).start()

    log(f"Orca Core simulator running: HTTP :{args.http_port}  WS :{args.ws_port}")
    log(f"Response delay: {args.min_delay}s - {args.max_delay}s")
    log(f"Autopilot id={args.autopilot_id} heading={sim.state.heading:03d} mode=STANDBY")

    if not args.no_mdns:
        start_mdns(args.http_port)

    await asyncio.Event().wait()  # run until Ctrl-C


if __name__ == "__main__":
    p = argparse.ArgumentParser(description="Orca Core simulator")
    p.add_argument("--http-port", type=int, default=8088)
    p.add_argument("--ws-port", type=int, default=8089)
    p.add_argument("--min-delay", type=float, default=0.5, help="min response delay in seconds")
    p.add_argument("--max-delay", type=float, default=20.0, help="max response delay in seconds")
    p.add_argument("--autopilot-id", type=int, default=0)
    p.add_argument("--initial-heading", type=int, default=270)
    p.add_argument("--no-mdns", action="store_true", help="don't advertise via dns-sd")
    p.add_argument("--verbose", action="store_true", help="log every websocket frame")
    args = p.parse_args()

    if args.min_delay > args.max_delay:
        sys.exit("--min-delay must be <= --max-delay")

    try:
        asyncio.run(main(args))
    except KeyboardInterrupt:
        log("Simulator stopped")
