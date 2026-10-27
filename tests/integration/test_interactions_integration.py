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
        self.assertEqual(len(report["checks"]), 24)
        self.assertTrue(all(case["status"] == "passed" for case in report["checks"]))
        run = Path(report["directory"])
        self.assertEqual(
            file_hash(run / "observations.json"), report["observations_sha256"]
        )
        self.assertEqual(profile.read_bytes() if profile.exists() else None, original)
        self.assertFalse((settings.home / ".commander-simulator.lock").exists())
        print(
            json.dumps(
                {
                    "controlled_interaction_run": report["run_id"],
                    "directory": report["directory"],
                    "cases": 24,
                    "status": "passed",
                }
            )
        )
