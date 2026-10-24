#!/usr/bin/env python3
"""Summarize callback delivery, not optical pen-to-ink latency, from an Ink Probe export."""
import argparse
import json
import math
from pathlib import Path


def percentile(values, percent):
    if not values:
        return None
    values = sorted(values)
    return values[math.ceil(len(values) * percent / 100) - 1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("export", type=Path)
    args = parser.parse_args()
    data = json.loads(args.export.read_text())
    print(f"Ink Probe {data.get('appVersion', '?')} / mode {data.get('mode', '?')}")
    print("Times below describe software callback delivery, not visible ink latency.")
    print("Stroke  Source          Points  Event span  Arrival span  Delay p50/p95/max (ms)")
    timed = 0
    all_delays = []
    for number, stroke in enumerate(data.get("strokes", []), 1):
        points = stroke.get("points_x_y_time_pressure", [])
        received = stroke.get("callbackArrivalUptimeMs", [])
        if not received:
            continue
        if len(points) != len(received):
            raise SystemExit(f"Stroke {number}: point/receipt counts differ")
        valid = [(p, r) for p, r in zip(points, received) if p[2] > 0 and r >= p[2]]
        delays = [r - p[2] for p, r in valid]
        all_delays.extend(delays)
        timed += len(received)
        event_span = points[-1][2] - points[0][2]
        arrival_span = received[-1] - received[0]
        stats = "/".join(str(percentile(delays, q)) for q in (50, 95, 100))
        print(f"{number:6d}  {stroke.get('source', '?'):14}  {len(points):6d}"
              f"  {event_span:10d}  {arrival_span:12d}  {stats}")
        if len(valid) != len(points):
            print(f"        {len(points) - len(valid)} samples excluded: invalid/mismatched event clock")
    if not timed:
        print("No receipt timestamps: perform a physical test and Save log in version 0.3 or newer.")
    else:
        print(f"Aggregate {timed} received samples; valid delay p50/p95/max: "
              + "/".join(str(percentile(all_delays, q)) for q in (50, 95, 100)) + " ms")
    native = data.get("firmwareSurface", {}).get("tracks_x_y_time_pressure")
    if native is not None:
        print(f"Native widget retained {len(native)} tracks independently of the timing observer.")


if __name__ == "__main__":
    main()
