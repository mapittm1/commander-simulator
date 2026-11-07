"""Opt-in complete paid creature-exchange and random-pile outcomes."""

import json
import os
import unittest

import pytest

from commander.engine.forge_config import read_settings
from commander.engine.native_runtime import create_run
from commander.paths import PROJECT_ROOT
from tests.support.native_receipts import assert_paid_outcome, read_test_receipts
from tests.support.native_worker import run_test_worker

pytestmark = pytest.mark.integration


@unittest.skipUnless(
    os.environ.get("COMMANDER_ENGINE_INTEGRATION") == "1",
    "Requires reviewed exchange/pile runtime",
)
class ExchangePileIntegrationTests(unittest.TestCase):
    """Require real selections, native payment, valid groups and stable physical rolls."""

    def assert_row(self, row):
        """Check a fixed causal outcome without forcing AI to activate the source."""
        if row["kind"] == "whims":
            self.assertEqual(row["decision_seed"], 0)
            self.assertTrue(row["decision_rng_restored"])
            self.assertTrue(row["decision_rng_calls_unchanged"])
        if not assert_paid_outcome(self, row):
            return
        final = row["final"]
        self.assertEqual(row["paid_mana"], 6)
        if row["kind"] == "whims":
            self.assertEqual(row["paid_action"]["api"], "MultiplePiles")
            self.assertTrue(row["physical_rng_restored"])
            rolls = row["rolls_in_separation_order"]
            self.assertEqual(len(rolls), 4)
            self.assertTrue(all(0 <= roll < 3 for roll in rolls))
            self.assertEqual(
                row["after_permanents"],
                [
                    0 if roll == 0 else count
                    for roll, count in zip(rolls, row["before_permanents"])
                ],
            )
        else:
            giver = 2 if row["kind"] == "controlled" else 0
            self.assertEqual(row["paid_action"]["api"], "ExchangeControlVariant")
            self.assertEqual(sorted(row["paid_action"]["players"]), sorted([giver, 1]))
            self.assertEqual(final["weak"]["controller"], 1)
            self.assertEqual(final["best"]["controller"], giver)
            self.assertEqual(final["weak"]["zone"], "Battlefield")
            self.assertEqual(final["best"]["zone"], "Battlefield")
            if row["kind"] in ("selective", "controlled"):
                self.assertEqual(final["keep"]["controller"], giver)
                self.assertEqual(final["other"]["controller"], 1)

    def test_paid_exchanges_and_random_piles(self):
        """Exercise three modes plus real physical pile rolls and source-decline controls."""
        settings = read_settings()
        run = create_run()
        worker = PROJECT_ROOT / "tests/support/exchange_piles_worker.py"
        process, _ = run_test_worker(settings, run, worker, "exchange-piles.log")
        self.assertEqual(process["returncode"], 0, process)
        expected = {
            f"exchange-piles-{mode}.json": 4 for mode in ("heuristic", "hybrid", "full")
        }
        expected["exchange-piles-controls.json"] = 3
        rows = read_test_receipts(settings, run, "exchange-piles.json", expected)
        self.assertEqual(
            [(row["kind"], row["mode"], row["control"]) for row in rows],
            [
                (kind, mode, "positive")
                for mode in ("heuristic", "hybrid", "full")
                for kind in ("simple", "selective", "controlled", "whims")
            ]
            + [
                ("simple", "heuristic", "harmful"),
                ("simple", "heuristic", "empty"),
                ("whims", "heuristic", "own_risk"),
            ],
        )
        for row in rows:
            with self.subTest(
                kind=row["kind"], mode=row["mode"], control=row["control"]
            ):
                self.assert_row(row)
        print(json.dumps({"exchange_pile_run": run.name}), flush=True)
