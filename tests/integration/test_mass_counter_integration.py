"""Opt-in paid mass-counter decisions, modal handoff, and native post-resolution state."""

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
    "Requires reviewed mass-counter-removal runtime",
)
class MassCounterIntegrationTests(unittest.TestCase):
    """Require selected sources, paid costs and rule outcomes in every AI mode."""

    def assert_row(self, row):
        """Check one fixed outcome or source-decline control."""
        if not assert_paid_outcome(self, row):
            return
        final = row["final"]
        selected = row["selected"]
        self.assertEqual(selected["name"], row["source"])
        kind = row["kind"]
        self.assertEqual(row["setup_paid"], 3 if kind == "stone" else 0)
        self.assertEqual(row["paid_mana"], 5 if kind in ("aether", "stone") else 3)
        if kind in ("dust", "haze"):
            self.assertEqual(row["paid_action"]["api"], "Charm")
            self.assertEqual(row["mode_api"], "RemoveCounterAll")
        if kind == "aether":
            self.assertEqual(row["paid_action"]["api"], "RemoveCounterAll")
            self.assertEqual(final["bear"]["counters"]["P1P1"], 0)
            self.assertEqual(final["bear"]["zone"], "Battlefield")
            self.assertEqual(final["walker"]["zone"], "Graveyard")
        elif kind == "dust":
            self.assertEqual(final["victim"]["zone"], "Graveyard")
            self.assertEqual(final["victim"]["counters"]["TIME"], 0)
        elif kind == "haze":
            self.assertEqual(final["walker"]["zone"], "Graveyard")
            self.assertEqual(final["walker"]["counters"]["LOYALTY"], 0)
        else:
            self.assertEqual(row["paid_action"]["api"], "DestroyAll")
            self.assertEqual(final["protected"]["zone"], "Battlefield")
            self.assertEqual(final["protected"]["counters"]["FATE"], 0)
            for index in range(3):
                self.assertEqual(final[f"enemy_{index}"]["zone"], "Graveyard")

    def test_paid_mass_counter_choices(self):
        """Exercise the production modal cast path and preserve decline controls."""
        settings = read_settings()
        run = create_run()
        worker = PROJECT_ROOT / "tests/support/mass_counter_worker.py"
        process, _ = run_test_worker(settings, run, worker, "mass-counters.log")
        self.assertEqual(process["returncode"], 0, process)
        expected = {
            f"mass-counters-{mode}.json": 4 for mode in ("heuristic", "hybrid", "full")
        }
        expected["mass-counters-controls.json"] = 2
        rows = read_test_receipts(settings, run, "mass-counters.json", expected)
        self.assertEqual(
            [(row["kind"], row["mode"], row["control"]) for row in rows],
            [
                (kind, mode, "positive")
                for mode in ("heuristic", "hybrid", "full")
                for kind in ("aether", "dust", "stone", "haze")
            ]
            + [("aether", "heuristic", control) for control in ("blank", "own_harm")],
        )
        for row in rows:
            with self.subTest(
                kind=row["kind"], mode=row["mode"], control=row["control"]
            ):
                self.assert_row(row)
        print(json.dumps({"mass_counter_run": run.name}), flush=True)
