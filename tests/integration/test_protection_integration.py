"""Opt-in paid temporary protection expiration witnesses."""

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
    "Requires reviewed protection expiration runtime",
)
class ProtectionIntegrationTests(unittest.TestCase):
    """Paid card, group and player protection deadlines stay in their own worlds."""

    def test_paid_protection_expires_independently(self):
        """Run packaged card scripts and native turn hooks without replaying casts."""
        settings = read_settings()
        expected = settings.verify()
        self.assertIn("protection_expiration", expected["capabilities"])
        run = create_run()
        process, _ = run_test_worker(
            settings,
            run,
            PROJECT_ROOT / "tests/support/protection_worker.py",
            "protection.log",
        )
        self.assertEqual(
            (process["status"], process["returncode"]), ("finished", 0), str(run)
        )
        receipt = read_bounded_json(run / "protection-state.json")
        self.assertEqual(receipt["engine"], expected)
        self.assertEqual(
            [row["card"] for row in receipt["cases"]],
            ["Shelter", "Brave the Elements", "Seht's Tiger"],
        )
        for row, paid in zip(receipt["cases"], (2, 1, 4)):
            with self.subTest(card=row["card"]):
                player_effect = row["card"] == "Seht's Tiger"
                self.assertEqual(row["paid"], paid)
                self.assertEqual(
                    row["before"],
                    {
                        "creature": not player_effect,
                        "player": player_effect,
                        "intrinsic": True,
                    },
                )
                self.assertEqual(row["before"], row["copied_before"])
                self.assertEqual(row["source"], row["before"])
                self.assertEqual(
                    row["after"],
                    {
                        "creature": False,
                        "player": False,
                        "intrinsic": True,
                    },
                )
                self.assertEqual(row["source_after"], row["after"])
                self.assertEqual(row["unmapped"], [])
