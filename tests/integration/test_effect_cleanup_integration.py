"""Opt-in temporary effects, regeneration quotas and native cleanup regressions."""

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
    "Requires reviewed temporary-effect cleanup runtime",
)
class EffectCleanupIntegrationTests(unittest.TestCase):
    """Observe actual costs and outcomes, including historical regeneration state."""

    def _assert_state(self, state):
        for card in state["cards"]:
            self.assertTrue(card["in_world"])
            self.assertGreaterEqual(card["regeneration_shields"], 0)
            self.assertEqual(
                card["lki_regeneration_shields"], card["regeneration_shields"]
            )
            self.assertEqual(
                card["lki_regenerated_this_turn"], card["regenerated_this_turn"]
            )

    def _assert_before(self, row):
        kind = row["case"]
        before = row["before"]
        self._assert_state(before)
        self.assertEqual(len(before["effects"]), 0 if kind == "starfish" else 1)
        self.assertEqual(before["tokens"], 0)
        self.assertEqual(before["life"], [40] * 4)
        self.assertEqual(len(before["cards"]), 2 if kind == "regen_partial" else 1)
        card = before["cards"][0]
        self.assertEqual(
            card["zone"], "Stack" if kind.startswith("replay") else "Battlefield"
        )
        self.assertEqual(
            card["regeneration_shields"],
            1 if kind in {"regen_use", "regen_expiry"} else 0,
        )
        self.assertEqual(
            card["regenerated_this_turn"],
            1 if kind in {"regen_partial", "starfish"} else 0,
        )
        if kind == "regen_partial":
            self.assertEqual(before["cards"][1]["regeneration_shields"], 1)
        if kind.startswith("dying"):
            self.assertEqual(card["damage"], 3)
        if kind.startswith("unearth"):
            self.assertTrue(card["haste"])

    def _assert_after(self, row):
        kind = row["case"]
        state = row["after"]
        self._assert_state(state)
        self.assertEqual(state["effects"], [])
        self.assertEqual(state["life"], [38, 40, 40, 40] if kind == "fog" else [40] * 4)
        self.assertEqual(state["tokens"], 1 if kind == "starfish" else 0)
        destination = (
            "Graveyard"
            if kind in {"regen_expiry", "regen_partial", "dying_expiry"}
            else (
                "Exile"
                if kind.startswith(("unearth", "replay")) or kind == "dying_active"
                else "Battlefield"
            )
        )
        card = state["cards"][-1]
        self.assertEqual(card["zone"], destination)
        self.assertEqual(card["regeneration_shields"], 0)
        self.assertEqual(card["regenerated_this_turn"], 1 if kind == "regen_use" else 0)
        if kind == "regen_use":
            self.assertTrue(card["tapped"])
        if kind == "regen_partial":
            self.assertEqual(state["cards"][0]["zone"], "Battlefield")
            self.assertEqual(state["cards"][0]["regenerated_this_turn"], 0)
        if "after_cleanup" in row:
            self._assert_state(row["after_cleanup"])
            self.assertEqual(row["after_cleanup"]["effects"], [])
            for cleaned in row["after_cleanup"]["cards"]:
                self.assertEqual(cleaned["regenerated_this_turn"], 0)
        if kind.startswith("replay"):
            self.assertEqual(
                row["library_change"], 0 if kind == "replay_counter" else 1
            )

    def _assert_case(self, row):
        costs = {
            "fog": [2, 1],
            "regen_expiry": [2, 3, 3],
            "regen_partial": [2, 3, 3, 3],
            "regen_use": [2, 3, 3],
            "dying_expiry": [2, 3, 3],
            "dying_active": [2, 3, 3],
            "unearth_bounce": [2, 1, 1],
            "unearth_end": [2, 0, 0],
            "starfish": [3, 1, 3, 0, 0],
            "amass": [2, 3, 1, 1],
            "replay_resolve": [6, 0],
            "replay_counter": [6, 2],
        }
        self.assertEqual(row["paid"], costs[row["case"]])
        self.assertEqual(row["copied_before"], row["before"])
        self.assertEqual(row["source"], row["before"])
        self.assertEqual(row["unmapped"], [])
        if row["case"] == "amass":
            self.assertEqual(len(row["before"]["effects"]), 1)
            self.assertEqual(len(row["before"]["armies"]), 1)
            army = row["before"]["armies"][0]
            self.assertTrue(army["orc"] and army["zombie"] and army["in_world"])
            self.assertEqual(army["counters"], 3)
            self.assertEqual(row["after"], {"armies": [], "effects": []})
        else:
            self._assert_before(row)
            self._assert_after(row)
        self.assertEqual(row["source_after"], row["after"])

    def test_temporary_effect_cleanup(self):
        """Capture twelve legal-card paths using the exact selected engine."""
        run = create_run()
        settings = read_settings()
        worker = PROJECT_ROOT / "tests/support/effect_cleanup_worker.py"
        process, _ = run_test_worker(settings, run, worker, "effect-cleanup.log")
        self.assertEqual(process["returncode"], 0, process)
        receipt = read_bounded_json(run / "effect-cleanup.json")
        self.assertEqual(receipt["engine"], settings.verify())
        expected = (
            "fog regen_expiry regen_partial regen_use dying_expiry dying_active"
            " unearth_bounce unearth_end starfish amass replay_resolve replay_counter"
        ).split()
        self.assertEqual([row["case"] for row in receipt["cases"]], expected)
        for row in receipt["cases"]:
            with self.subTest(case=row["case"]):
                self._assert_case(row)
