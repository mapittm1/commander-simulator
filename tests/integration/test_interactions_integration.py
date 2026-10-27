"""Opt-in direct-state tests using the real pinned Java engine and native bridge."""

import io
import json
import os
import unittest
from contextlib import redirect_stdout
from pathlib import Path

import pytest

from commander.cli.app import main
from commander.engine.forge_config import file_hash, read_settings
from commander.engine.interactions.native_bridge import (
    CLASS_RELATIVE,
    PRIORITY_CLASS_RELATIVE,
)
from commander.engine.interactions.suite import MAX_RECEIPT_BYTES, PARTITIONS

pytestmark = pytest.mark.integration


@unittest.skipUnless(
    os.environ.get("COMMANDER_INTERACTION_INTEGRATION") == "1",
    "Requires pinned engine, current catalog, and optional interaction dependencies",
)
class ControlledInteractionTests(unittest.TestCase):
    """Check direct states rather than inferring interaction correctness from a game log."""

    def test_equipment_damage_countering_targeting_and_defense_scenarios(self):
        """Require every controlled case and preserve original engine profile bytes."""
        settings = read_settings()
        profile = settings.home / "forge.profile.properties"
        original = profile.read_bytes() if profile.exists() else None
        output = io.StringIO()
        with redirect_stdout(output):
            main(["engine-interactions"])
        report = json.loads(output.getvalue())
        self.assertEqual(report["status"], "passed", report)
        self.assertEqual(len(report["checks"]), 41)
        self.assertTrue(all(case["status"] == "passed" for case in report["checks"]))
        run = Path(report["directory"])
        self.assertEqual(
            file_hash(run / CLASS_RELATIVE), report["native_bridge"]["class_sha256"]
        )
        self.assertEqual(
            file_hash(run / PRIORITY_CLASS_RELATIVE),
            report["native_bridge"]["priority_class_sha256"],
        )
        self.assertEqual(report["native_bridge"]["process"]["returncode"], 0)
        self.assertEqual(
            [part["partition_id"] for part in report["partition_results"]],
            list(PARTITIONS),
        )
        for part in report["partition_results"]:
            path = run / f"observations-{part['partition_id']}.json"
            self.assertLessEqual(path.stat().st_size, MAX_RECEIPT_BYTES)
            self.assertEqual(file_hash(path), part["observations_sha256"])
            self.assertEqual(part["status"], "passed")
        self.assertLessEqual(
            (run / "interaction-inputs.json").stat().st_size, MAX_RECEIPT_BYTES
        )
        self.assertEqual(profile.read_bytes() if profile.exists() else None, original)
        self.assertFalse((settings.home / ".commander-simulator.lock").exists())
        print(
            json.dumps(
                {
                    "controlled_interaction_run": report["run_id"],
                    "directory": report["directory"],
                    "cases": 41,
                    "status": "passed",
                }
            )
        )
