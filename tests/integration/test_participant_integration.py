"""Opt-in checks for participant state in the packaged Commander runtime."""

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
    "Requires reviewed participant-state native runtime",
)
class ParticipantIntegrationTests(unittest.TestCase):
    """Keep eliminated seats historical and independently mapped in snapshots."""

    def test_participant_state_and_historical_references(self):
        """Exercise three loss reasons at each inactive seat in a four-player pod."""
        settings = read_settings()
        expected = settings.verify()
        self.assertIn("participant_snapshot_state", expected["capabilities"])
        run = create_run()
        process, _ = run_test_worker(
            settings,
            run,
            PROJECT_ROOT / "tests/support/participant_worker.py",
            "participant.log",
        )
        self.assertEqual(
            (process["status"], process["returncode"]), ("finished", 0), str(run)
        )
        receipt = read_bounded_json(run / "participant-state.json")
        self.assertEqual(receipt["engine"], expected)
        cases = [
            (seat, reason)
            for reason in ("concession", "life", "poison")
            for seat in (1, 2, 3)
        ]
        self.assertEqual(len(receipt["seats"]), len(cases))
        for row, (seat, reason) in zip(receipt["seats"], cases):
            with self.subTest(seat=seat, reason=reason):
                self.assertEqual((row["seat"], row["reason"]), (seat, reason))
                self.assertEqual(row["before"], row["after"])
                self.assertEqual(len(row["after"]["active"]), 3)
                self.assertEqual(len(row["after"]["lost"]), 1)
                self.assertEqual(row["after"]["registered"], 4)
                self.assertTrue(row["mapped_lost"])
                self.assertTrue(row["mapped_game"])
                self.assertTrue(row["not_active"])
        with self.subTest(case="historical_player"):
            self.assertEqual(
                receipt["history"],
                {
                    "received": 3,
                    "remembered_canonical": True,
                    "mapped_game": True,
                    "active": 3,
                },
            )
        with self.subTest(case="independent_loss"):
            self.assertEqual(
                receipt["independent"],
                {
                    "original_active": 3,
                    "original_lost": 1,
                    "original_second_lost": False,
                    "copy_active": 2,
                    "copy_lost": 2,
                    "recopy_equal": True,
                    "lost_order": True,
                },
            )
        self.assertEqual(settings.verify(), expected)
