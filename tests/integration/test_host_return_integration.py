"""Opt-in host-bound exile returns and linked-card identity regressions."""

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
    "Requires reviewed host-return runtime",
)
class HostReturnIntegrationTests(unittest.TestCase):
    """Require native paid returns and independent copied-world state."""

    def _assert_returned(self, row, state):
        kind = row["case"]
        self.assertTrue(state["linked_world"])
        self.assertEqual(state["linked"], [])
        expected_host = (
            "Hand" if kind in {"bounce", "priest", "hand", "lagrella"} else "Graveyard"
        )
        self.assertEqual(state["host_zone"], expected_host)
        self.assertEqual(state["tokens"], 1 if kind == "token" else 0)
        self.assertEqual(
            [card["id"] for card in state["cards"]],
            [card["id"] for card in row["before"]["cards"]],
        )
        destination = (
            "Hand"
            if kind == "hand"
            else "Graveyard" if kind == "early" else "Battlefield"
        )
        for before, card in zip(row["before"]["cards"], state["cards"]):
            self.assertTrue(card["in_world"])
            self.assertEqual(card["zone"], destination)
            self.assertEqual(card["owner"], before["owner"])
            self.assertEqual(
                card["counters"],
                2 if kind == "lagrella" and card["owner"] == row["acting_id"] else 0,
            )
            if destination == "Battlefield":
                self.assertEqual(card["controller"], card["owner"])
            if kind in {"early", "host_gone"}:
                self.assertEqual(card, before)
            else:
                self.assertGreater(card["timestamp"], before["timestamp"])

    def _assert_case(self, row):
        kind = row["case"]
        before = row["before"]
        self.assertEqual(row["copied_before"], before)
        self.assertEqual(row["source"], before)
        self.assertTrue(before["linked_world"])
        self.assertEqual(
            before["host_zone"], "Graveyard" if kind == "host_gone" else "Battlefield"
        )
        count = len(before["cards"])
        if kind == "lagrella":
            self.assertIn(count, (3, 4))
            self.assertEqual(len({card["owner"] for card in before["cards"]}), count)
        else:
            self.assertEqual(
                count, 3 if kind == "grasp" else 0 if kind == "token" else 1
            )
        expected_links = (
            []
            if kind in {"early", "token", "host_gone"}
            else sorted(card["id"] for card in before["cards"])
        )
        self.assertEqual(before["linked"], expected_links)
        for card in before["cards"]:
            self.assertTrue(card["in_world"])
            self.assertEqual(card["counters"], 0)
            self.assertEqual(
                card["zone"],
                (
                    "Graveyard"
                    if kind == "early"
                    else "Battlefield" if kind == "host_gone" else "Exile"
                ),
            )
        self.assertEqual(row["paid"]["host"], 2 if kind == "hand" else 3)
        self.assertEqual(
            row["paid"]["preparation"],
            1 if kind == "early" else 2 if kind == "token" else 0,
        )
        if kind in {"pending_removal", "host_gone"}:
            self.assertEqual(row["source_stack"], 1)
            self.assertEqual(row["copied_stack"], 1)
            self.assertEqual(row["pending_paid"], 0 if kind == "host_gone" else 2)
            self.assertEqual(row["paid"]["removal"], 2)
        else:
            cost = 1 if kind in {"priest", "hand", "lagrella"} else 2
            self.assertEqual(row["paid"]["copied_removal"], cost)
            self.assertEqual(row["paid"]["source_removal"], cost)
        self._assert_returned(row, row["after"])
        self._assert_returned(row, row["source_after"])
        self.assertEqual(row["unmapped"], [])

    def test_host_bound_returns(self):
        """Use the selected runtime and preserve bounded evidence for ten cases."""
        run = create_run()
        settings = read_settings()
        worker = PROJECT_ROOT / "tests/support/host_return_worker.py"
        process, _ = run_test_worker(settings, run, worker, "host-return.log")
        self.assertEqual(process["returncode"], 0, process)
        receipt = read_bounded_json(run / "host-return.json")
        self.assertEqual(receipt["engine"], settings.verify())
        expected_cases = (
            "destroy bounce priest grasp hand lagrella early token"
            " pending_removal host_gone"
        ).split()
        self.assertEqual([row["case"] for row in receipt["cases"]], expected_cases)
        for row in receipt["cases"]:
            with self.subTest(case=row["case"]):
                self._assert_case(row)
