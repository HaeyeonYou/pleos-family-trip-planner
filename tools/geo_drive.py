"""Simulate driving on the Pleos emulator by streaming GPS fixes.

Positions sent with `adb emu geo fix` reach the Pleos FusedLocation service,
so the app's location tracking and arrival (proximity) logic can be tested.
The built-in navigation's own position does not follow (see issue #4).

usage:
  python3 tools/geo_drive.py <lat1> <lon1> <lat2> <lon2> [speed_kmh]
  python3 tools/geo_drive.py --fix <lat> <lon>

Uses ANDROID_SERIAL when several devices are connected.
"""
import math
import subprocess
import sys
import time

METERS_PER_DEG_LAT = 111_000


def fix(lat: float, lon: float) -> None:
    # geo fix takes longitude first
    subprocess.run(["adb", "emu", "geo", "fix", f"{lon:.6f}", f"{lat:.6f}"], check=True, capture_output=True)


def drive(lat1: float, lon1: float, lat2: float, lon2: float, speed_kmh: float = 40.0) -> None:
    meters_per_deg_lon = METERS_PER_DEG_LAT * math.cos(math.radians(lat1))
    meters = math.hypot((lat2 - lat1) * METERS_PER_DEG_LAT, (lon2 - lon1) * meters_per_deg_lon)
    steps = max(1, round(meters / (speed_kmh / 3.6)))
    print(f"{meters:.0f} m, {steps} s at {speed_kmh:.0f} km/h")
    for i in range(steps + 1):
        f = i / steps
        fix(lat1 + (lat2 - lat1) * f, lon1 + (lon2 - lon1) * f)
        time.sleep(1)


if __name__ == "__main__":
    args = sys.argv[1:]
    if args and args[0] == "--fix":
        fix(float(args[1]), float(args[2]))
    else:
        drive(*[float(a) for a in args])
