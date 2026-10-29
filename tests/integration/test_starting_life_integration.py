"""Opt-in packaged-runtime starting-life and paid threshold behavior."""

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
    "Requires reviewed starting-life snapshot runtime",
)
class StartingLifeIntegrationTests(unittest.TestCase):
    """Preserve baseline-dependent card effects through real native snapshots."""

    def _assert_baselines(self, rows):
        """Keep the registered baseline independent from changing current life."""
        self.assertEqual(len(rows), 5)
        for row, baseline in zip(rows, (20, 25, 30, 40, 50)):
            with self.subTest(baseline=baseline):
                self.assertEqual(row["baseline"], baseline)
                self.assertEqual(
                    row["before"],
                    [
                        {
                            "starting": baseline,
                            "registered": baseline,
                            "current": baseline - 3,
                        }
                    ]
                    + [
                        {
                            "starting": baseline,
                            "registered": baseline,
                            "current": baseline,
                        }
                    ]
                    * 3,
                )
                self.assertEqual(row["after"], row["before"])

    def _assert_thresholds(self, rows):
        """Check both sides of the seven-life boundary for two baselines."""
        cases = (
            (20, 26, False),
            (20, 27, True),
            (40, 27, False),
            (40, 40, False),
            (40, 46, False),
            (40, 47, True),
            (40, 48, True),
        )
        self.assertEqual(len(rows), len(cases))
        for row, (baseline, life, buffed) in zip(rows, cases):
            with self.subTest(baseline=baseline, life=life):
                powers = [4, 4] if buffed else [2, 2]
                self.assertEqual(
                    row,
                    {
                        "baseline": baseline,
                        "life": life,
                        "before": powers,
                        "after": powers,
                        "copied_life": {
                            "starting": baseline,
                            "registered": baseline,
                            "current": life,
                        },
                    },
                )

    def _assert_paid(self, rows):
        """Require normal casting, real payment and the expected resulting effect."""
        self.assertEqual(len(rows), 2)
        for row, name, mana, gain, power in zip(
            rows, ("Angel's Mercy", "Chaplain's Blessing"), (4, 1), (7, 5), (4, 2)
        ):
            with self.subTest(spell=name):
                self.assertEqual(
                    row,
                    {
                        "spell": name,
                        "mana_paid": mana,
                        "before_power": 2,
                        "after_power": power,
                        "bear_power": power,
                        "life": {
                            "starting": 40,
                            "registered": 40,
                            "current": 40 + gain,
                        },
                        "in_graveyard": True,
                        "source_life": {
                            "starting": 40,
                            "registered": 40,
                            "current": 40,
                        },
                        "source_bear_power": 2,
                    },
                )

    def _assert_historical_and_overridden_baselines(self, receipt):
        """Retain eliminated state and separate game overrides from registration."""
        with self.subTest(case="eliminated_baseline"):
            self.assertEqual(
                receipt["eliminated"],
                {
                    "life": {"starting": 40, "registered": 40, "current": 0},
                    "lost": True,
                    "active": 3,
                },
            )
        with self.subTest(case="live_baseline_override"):
            state = {"starting": 30, "registered": 40, "current": 35}
            self.assertEqual(receipt["override"], {"before": state, "after": state})

    def test_starting_life_thresholds_and_paid_life_gain(self):
        """Keep registered/current baselines distinct and verify real life-gain casts."""
        settings = read_settings()
        expected = settings.verify()
        self.assertIn("starting_life_snapshot", expected["capabilities"])
        run = create_run()
        process, _ = run_test_worker(
            settings,
            run,
            PROJECT_ROOT / "tests/support/starting_life_worker.py",
            "starting-life.log",
        )
        self.assertEqual(
            (process["status"], process["returncode"]), ("finished", 0), str(run)
        )
        receipt = read_bounded_json(run / "starting-life.json")
        self.assertEqual(receipt["engine"], expected)
        self._assert_baselines(receipt["baselines"])
        self._assert_thresholds(receipt["thresholds"])
        self._assert_paid(receipt["paid"])
        self._assert_historical_and_overridden_baselines(receipt)
        self.assertEqual(settings.verify(), expected)
