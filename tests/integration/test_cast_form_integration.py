"""Opt-in paid cast-form checks against the pinned packaged runtime."""

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
    "Requires reviewed cast-form runtime",
)
class CastFormIntegrationTests(unittest.TestCase):
    """Preserve chosen forms through paid casts, copies and native resolution."""

    def _assert_common(self, row, expected):
        """Require equal pending forms and exactly one current live-zone instance."""
        case, paid, face, pending_name, resolved_name, zone = expected
        self.assertEqual(row["case"], case)
        self.assertEqual(row["paid"], paid)
        self.assertEqual(row["before"]["face"], face)
        self.assertEqual(row["before"]["name"], pending_name)
        self.assertEqual(row["copied_pending"], row["before"])
        self.assertEqual(row["source_pending"], row["before"])
        self.assertEqual(row["source_entries"], 1)
        self.assertEqual(row["remaining_stack"], 0)
        self.assertEqual(len(row["resolved_instances"]), 1)
        resolved = row["resolved_instances"][0]
        self.assertTrue(resolved["world"])
        self.assertEqual(resolved["zone"], zone)
        self.assertEqual(resolved["form"]["name"], resolved_name)

    def _assert_targets(self, row):
        """Check real bounce, tap and Aura-bonus outcomes with source independence."""
        if "target_instances" not in row:
            return
        self.assertEqual(len(row["target_instances"]), 1)
        self.assertEqual(len(row["source_target"]), 1)
        target = row["target_instances"][0]
        source = row["source_target"][0]
        self.assertTrue(target["world"])
        self.assertEqual(source["zone"], "Battlefield")
        self.assertFalse(source["tapped"])
        if row["case"] == "adventure":
            self.assertEqual(target["zone"], "Hand")
            self.assertEqual(target["form"]["power"], 2)
        elif row["case"] == "split":
            self.assertEqual(target["zone"], "Battlefield")
            self.assertTrue(target["tapped"])
        elif row["case"] == "bestow":
            self.assertEqual(target["form"]["power"], 6)
            self.assertEqual(source["form"]["power"], 2)

    def _assert_characteristics(self, row):
        """Check prototype size/color, hidden morph and the selected permanent type."""
        form = row["resolved_instances"][0]["form"]
        if row["case"] == "prototype":
            self.assertEqual((form["power"], form["toughness"]), (3, 3))
            self.assertEqual(form["mana_value"], 3)
            self.assertTrue(form["black"])
            self.assertFalse(form["colorless"])
            self.assertEqual(
                form["prototype_timestamp"], row["before"]["prototype_timestamp"]
            )
        elif row["case"] == "morph":
            self.assertTrue(form["facedown"])
            self.assertTrue(form["colorless"])
            self.assertEqual(form["mana_value"], 0)
            self.assertEqual((form["power"], form["toughness"]), (2, 2))
        elif row["case"] == "modal":
            self.assertTrue(form["backside"])
            self.assertFalse(form["creature"])
            self.assertIn("Enchantment", form["types"])
        elif row["case"] == "bestow":
            self.assertTrue(form["aura"])
            self.assertTrue(form["bestowed"])
            self.assertFalse(form["creature"])
            self.assertTrue(row["resolved_instances"][0]["attached"])
        elif row["case"] == "adventure":
            self.assertEqual(form["face"], "Original")
            self.assertTrue(form["creature"])

    def test_paid_alternate_cast_forms(self):
        """Exercise six forms using real native cost payment and copied resolution."""
        settings = read_settings()
        expected_engine = settings.verify()
        self.assertIn("cast_form_snapshot", expected_engine["capabilities"])
        run = create_run()
        process, _ = run_test_worker(
            settings,
            run,
            PROJECT_ROOT / "tests/support/cast_form_worker.py",
            "cast-forms.log",
        )
        self.assertEqual(
            (process["status"], process["returncode"]), ("finished", 0), str(run)
        )
        receipt = read_bounded_json(run / "cast-forms.json")
        self.assertEqual(receipt["engine"], expected_engine)
        cases = (
            ("adventure", 2, "Secondary", "Petty Theft", "Brazen Borrower", "Exile"),
            (
                "modal",
                5,
                "Backside",
                "The Prismatic Bridge",
                "The Prismatic Bridge",
                "Battlefield",
            ),
            ("split", 2, "RightSplit", "Ice", "Fire // Ice", "Graveyard"),
            ("morph", 3, "FaceDown", "", "", "Battlefield"),
            (
                "prototype",
                3,
                "Original",
                "Phyrexian Fleshgorger",
                "Phyrexian Fleshgorger",
                "Battlefield",
            ),
            ("bestow", 5, "Original", "Boon Satyr", "Boon Satyr", "Battlefield"),
        )
        self.assertEqual(len(receipt["cases"]), len(cases))
        for row, expected in zip(receipt["cases"], cases):
            with self.subTest(case=expected[0]):
                self._assert_common(row, expected)
                self._assert_targets(row)
                self._assert_characteristics(row)
        self.assertEqual(settings.verify(), expected_engine)
