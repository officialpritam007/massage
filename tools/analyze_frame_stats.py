"""Analyze an adb dumpsys gfxinfo framestats capture without device dependencies.

Use an explicit refresh rate; this measures captured frames, not display capability.
"""
import argparse
import csv
import json
import math
from pathlib import Path


def parse_frames(raw):
    frames = []
    header = None
    for row in csv.reader(raw.splitlines()):
        names = [value.strip() for value in row]
        if {"Flags", "IntendedVsync", "FrameCompleted"}.issubset(names):
            header = names
            continue
        if header is None or len(names) != len(header):
            continue
        try:
            values = dict(zip(header, map(int, names)))
        except ValueError:
            continue
        intended = values["IntendedVsync"]
        completed = values["FrameCompleted"]
        if values["Flags"] != 0 or intended <= 0 or completed <= intended:
            continue
        # Android uses Long.MAX_VALUE for unavailable timings.
        if completed >= 2**63 - 1:
            continue
        frames.append(values)
    return frames


def percentile(values, percent):
    if not values:
        raise ValueError("No valid rendered frames in capture")
    ordered = sorted(values)
    return ordered[max(0, math.ceil(len(ordered) * percent / 100) - 1)]


def summarize(frames, refresh_rate, min_frames=120):
    if not math.isfinite(refresh_rate) or refresh_rate <= 0:
        raise ValueError("Refresh rate must be positive and finite")
    if len(frames) < min_frames:
        raise ValueError(
            f"Capture has {len(frames)} valid frames; at least {min_frames} required"
        )
    budget_ns = 1_000_000_000 / refresh_rate
    durations = [frame["FrameCompleted"] - frame["IntendedVsync"] for frame in frames]
    missed = 0
    for frame, duration in zip(frames, durations):
        deadline = frame.get("FrameDeadline", 0)
        if deadline > frame["IntendedVsync"]:
            missed += frame["FrameCompleted"] > deadline
        else:
            missed += duration > budget_ns
    return {
        "frames": len(frames),
        "requested_refresh_hz": refresh_rate,
        "frame_budget_ms": round(budget_ns / 1_000_000, 3),
        "median_ms": round(percentile(durations, 50) / 1_000_000, 3),
        "p95_ms": round(percentile(durations, 95) / 1_000_000, 3),
        "p99_ms": round(percentile(durations, 99) / 1_000_000, 3),
        "missed_deadline_percent": round(missed * 100 / len(frames), 2),
        "within_p95_budget": percentile(durations, 95) <= budget_ns,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("capture", type=Path)
    parser.add_argument("--refresh-rate", type=float, required=True)
    parser.add_argument("--min-frames", type=int, default=120)
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--max-missed-percent", type=float, default=5)
    args = parser.parse_args()
    if args.min_frames < 1:
        parser.error("--min-frames must be at least 1")
    try:
        result = summarize(parse_frames(args.capture.read_text()), args.refresh_rate, args.min_frames)
    except (OSError, ValueError) as error:
        parser.error(str(error))
    print(json.dumps(result, indent=2))
    if args.check and (
        not result["within_p95_budget"]
        or result["missed_deadline_percent"] > args.max_missed_percent
    ):
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
