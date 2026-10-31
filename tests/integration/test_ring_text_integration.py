"""Opt-in Ring designations, word edits and text-box exchange regressions."""

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
    "Requires reviewed Ring/text runtime",
)
class RingTextIntegrationTests(unittest.TestCase):
    """Check native paid choices, copied progression and observable rule outcomes."""

    def _assert_words(self, row):
        active = {"words": {"White": "Blue"}, "power": [2, 3]}
        restored = {"words": {}, "power": [3, 2]}
        expected = active if row["case"] == "words_permanent" else restored
        self.assertEqual(row["paid"], [2, 1])
        self.assertEqual(row["before"], active)
        self.assertEqual(row["copied_before"], active)
        self.assertEqual(row["source"], active)
        self.assertEqual(row["after"], expected)
        self.assertEqual(row["source_after"], expected)

    def _assert_exchange(self, row):
        case = row["case"]
        self.assertEqual(row["paid"][:2], [3, 2])
        self.assertEqual(row["before"], row["copied_before"])
        self.assertEqual(row["source"], row["before"])
        first, second = row["after"]
        if case in {"exchange_keywords", "exchange_blink"}:
            self.assertFalse(row["before"][0]["flying"])
            self.assertTrue(row["before"][0]["destroy"])
            self.assertTrue(first["flying"])
            self.assertTrue(first["vigilance"])
            self.assertFalse(first["destroy"])
            self.assertFalse(second["flying"])
            self.assertTrue(second["destroy"])
            if case == "exchange_blink":
                self.assertTrue(second["memory"])
                self.assertTrue(row["before"][1]["memory"])
            else:
                self.assertTrue(row["before"][1]["flying"])
        elif case == "exchange_static":
            self.assertEqual(row["merfolk_copy"], [2, 1])
            self.assertEqual(row["merfolk_source"], [1, 2])
        elif case == "exchange_trigger":
            self.assertEqual([box["triggers"] for box in row["before"]], [0, 1])
            self.assertEqual([first["triggers"], second["triggers"]], [1, 0])
            self.assertEqual(
                row["library_after_blink"], row["library_before_blink"] - 1
            )
            self.assertEqual(row["paid"], [3, 2, 1])
        else:
            self.assertEqual(row["copy_damage"]["zone"], "Battlefield")
            self.assertEqual(row["copy_damage"]["damage"], 0)
            self.assertEqual(row["source_damage"]["zone"], "Graveyard")
            self.assertEqual(row["paid"], [3, 2, 1, 1])

    def _assert_active_ring(self, state, count):
        self.assertEqual(state["count"], count)
        self.assertGreater(state["ring_id"], 0)
        self.assertEqual(state["ring_triggers"], min(count, 4) - 1)
        self.assertEqual(state["ring_cards"], 1)
        self.assertEqual(state["bearer_id"], state["physical_id"])
        self.assertTrue(state["flag"])
        self.assertTrue(state["legendary"])
        self.assertEqual(state["zone"], "Battlefield")

    def _assert_ring(self, row):
        self._assert_active_ring(row["before"], row["level"])
        self.assertEqual(row["copied_before"], row["before"])
        self.assertEqual(row["source"], row["before"])
        self._assert_active_ring(row["after"], row["level"] + 1)
        self.assertEqual(row["after"]["ring_id"], row["before"]["ring_id"])
        self.assertEqual(row["after"]["physical_id"], row["before"]["physical_id"])
        self.assertEqual(row["paid"], [1] * (row["level"] + 1))

    def _assert_loss(self, row):
        self._assert_active_ring(row["before"], 1)
        self.assertEqual(row["copied_before"], row["before"])
        self.assertEqual(row["source"], row["before"])
        after = row["after"]
        self.assertIsNone(after["bearer_id"])
        self.assertFalse(after["flag"])
        self.assertFalse(after["legendary"])
        self.assertEqual(after["count"], 1)
        self.assertEqual(after["ring_id"], row["before"]["ring_id"])
        self.assertEqual(after["ring_cards"], 1)
        control = row["case"] == "bearer_control"
        self.assertEqual(row["paid"], [1, 3 if control else 1])
        self.assertEqual(after["zone"], "Battlefield" if control else "Hand")

    def _assert_former(self, row):
        self.assertEqual(row["paid"], [1, 4, 1, 0, 1, 1])
        self._assert_active_ring(row["source_before"][0], 2)
        self._assert_active_ring(row["source_before"][1], 1)
        self.assertEqual(row["before"], row["source_before"])
        self.assertEqual(row["after"][0], row["before"][0])
        self.assertIsNone(row["after"][1]["bearer_id"])
        self.assertFalse(row["after"][1]["flag"])
        self.assertEqual(
            row["source"],
            row["source_before"] if row["case"] == "former_copy" else row["after"],
        )

    def _assert_contract(self, row):
        if row["case"] == "designation":
            self.assertEqual(row["paid"], [1, 4])
            self.assertFalse(row["clone_flag"])
            self.assertFalse(row["clone_legendary"])
            self.assertTrue(row["lki_flag"])
        else:
            self.assertEqual(row["paid"], [3])
            self.assertTrue(row["angel_flying"])
            self.assertFalse(row["bogle_flying"])

    def _assert_combat(self, row):
        blocked = row["case"] == "combat_block"
        self.assertEqual(row["paid"], [1] * 4)
        self.assertEqual(row["after"]["library"], row["before"]["library"] - 1)
        self.assertEqual(row["after"]["hand"], row["before"]["hand"])
        self.assertEqual(row["source"]["library"], row["before"]["library"])
        self.assertEqual(row["source"]["hand"], row["before"]["hand"])
        self.assertEqual(row["source"]["life"], [40] * 4)
        self.assertEqual(
            row["after"]["life"], [40] * 4 if blocked else [40, 35, 37, 37]
        )
        if blocked:
            self.assertEqual(row["after"]["wall_zone"], "Graveyard")
            self.assertEqual(row["source"]["wall_zone"], "Battlefield")

    def _assert_case(self, row):
        case = row["case"]
        if case.startswith("words_"):
            self._assert_words(row)
        elif case.startswith("exchange_"):
            self._assert_exchange(row)
        elif case == "ring_level":
            self._assert_ring(row)
        elif case.startswith("bearer_"):
            self._assert_loss(row)
        elif case.startswith("former_"):
            self._assert_former(row)
        elif case.startswith("combat_"):
            self._assert_combat(row)
        else:
            self._assert_contract(row)
        self.assertEqual(row["unmapped"], [])

    def test_paid_ring_and_text_rules(self):
        """Use native payment, mandatory targeting, combat and independent copied-world state."""
        settings = read_settings()
        expected = settings.verify()
        self.assertIn("ring_text_snapshot", expected["capabilities"])
        run = create_run()
        process, _ = run_test_worker(
            settings,
            run,
            PROJECT_ROOT / "tests/support/ring_text_worker.py",
            "ring-text.log",
        )
        self.assertEqual(
            (process["status"], process["returncode"]), ("finished", 0), str(run)
        )
        receipt = read_bounded_json(run / "ring-text.json")
        self.assertEqual(receipt["engine"], expected)
        self.assertEqual(
            [row["case"] for row in receipt["cases"]],
            (
                "words_temporary words_permanent exchange_keywords exchange_static exchange_trigger "
                "exchange_replacement exchange_blink ring_level ring_level ring_level ring_level "
                "bearer_leave bearer_control former_copy former_original designation illegal_pair "
                "combat_hit combat_block"
            ).split(),
        )
        self.assertEqual(
            [r["level"] for r in receipt["cases"] if r["case"] == "ring_level"],
            [1, 2, 3, 4],
        )
        for index, row in enumerate(receipt["cases"]):
            with self.subTest(case=row["case"], index=index):
                self._assert_case(row)
