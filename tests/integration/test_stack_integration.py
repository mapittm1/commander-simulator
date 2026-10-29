"""Opt-in paid stack checks against the hash-verified packaged runtime."""

import os
import unittest

import pytest

from commander.engine.forge_config import read_settings
from commander.engine.interactions.suite import read_bounded_json
from commander.engine.native_runtime import create_run
from commander.paths import PROJECT_ROOT
from tests.support.native_worker import run_test_worker

pytestmark = pytest.mark.integration


@unittest.skipUnless(
    os.environ.get("COMMANDER_ENGINE_INTEGRATION") == "1",
    "Requires reviewed paid stack runtime",
)
class StackIntegrationTests(unittest.TestCase):
    """Require paid costs, independent worlds and real ability resolution."""

    def test_paid_stack_snapshot(self):
        """Exercise damage spells, a tap activation and an admitted enter trigger."""
        settings = read_settings()
        expected = settings.verify()
        self.assertIn("stack_snapshot_state", expected["capabilities"])
        run = create_run()
        process, _ = run_test_worker(
            settings,
            run,
            PROJECT_ROOT / "tests/support/stack_worker.py",
            "stack-state.log",
        )
        self.assertEqual(
            (process["status"], process["returncode"]), ("finished", 0), str(run)
        )
        receipt = read_bounded_json(run / "stack-state.json")
        self.assertEqual(receipt["engine"], expected)
        cases = receipt["cases"]
        self.assertEqual(set(cases), {"bolt", "x", "kicker", "activated", "trigger"})
        for name, paid, life, x, kicked in (
            ("bolt", 1, 37, None, False),
            ("x", 4, 37, 3, False),
            ("kicker", 5, 36, None, True),
            ("activated", 0, 39, None, False),
            ("trigger", 0, 40, None, False),
        ):
            with self.subTest(case=name):
                row = cases[name]
                self.assertEqual(row["entries"], 1)
                self.assertEqual(row["paid"], paid)
                self.assertEqual(row["x"], x)
                self.assertEqual(row["kicked"], kicked)
                self.assertTrue(row["caster_world"])
                self.assertTrue(row["mana_world"])
                self.assertEqual(row["target_life"], life)
                self.assertEqual(row["owner_life"], 41 if name == "trigger" else 40)
                self.assertEqual(row["source_entries"], 1)
                self.assertEqual(row["source_target_life"], 40)
                self.assertEqual(row["source_owner_life"], 40)
        self.assertTrue(cases["activated"]["tap_paid"])
        self.assertEqual(settings.verify(), expected)
