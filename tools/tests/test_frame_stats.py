import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from analyze_frame_stats import parse_frames, summarize


class FrameStatsTest(unittest.TestCase):
    def test_ignores_metadata_invalid_and_flagged_frames(self):
        capture = """Stats since: 10
---PROFILEDATA---
Flags,IntendedVsync,FrameCompleted
0,1000000,7000000
1,1000000,30000000
0,1000000,9223372036854775807
0,0,7000000
0,7000000,1000000
invalid,row
---PROFILEDATA---
"""
        self.assertEqual(1, len(parse_frames(capture)))

    def test_detects_120hz_budget_misses(self):
        frames = [
            {"IntendedVsync": 1_000_000, "FrameCompleted": 8_000_000},
            {"IntendedVsync": 2_000_000, "FrameCompleted": 14_000_000},
        ]
        result = summarize(frames, 120, min_frames=2)
        self.assertEqual(8.333, result["frame_budget_ms"])
        self.assertEqual(12.0, result["p95_ms"])
        self.assertEqual(50, result["missed_deadline_percent"])
        self.assertFalse(result["within_p95_budget"])

    def test_uses_reported_frame_deadline_when_present(self):
        frames = [{"IntendedVsync": 1_000_000, "FrameCompleted": 8_000_000, "FrameDeadline": 6_000_000}]
        self.assertEqual(100, summarize(frames, 120, 1)["missed_deadline_percent"])

    def test_does_not_report_success_for_empty_or_short_capture(self):
        with self.assertRaises(ValueError):
            summarize([], 120)
        with self.assertRaises(ValueError):
            summarize([{"IntendedVsync": 1, "FrameCompleted": 2}], 120)
        for rate in (0, -1, float("inf")):
            with self.assertRaises(ValueError):
                summarize([], rate, 0)


if __name__ == "__main__":
    unittest.main()
