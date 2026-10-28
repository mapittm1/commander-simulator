"""Companion selection and evidence boundaries cannot bypass Commander legality."""

import unittest
from datetime import datetime, timezone
from unittest.mock import patch

from commander.catalog.database import connect, store_card
from commander.decks.deck_store import get_revision
from commander.decks.decks import prepare_deck, save_deck
from commander.engine.companion_admission import validate_receipt
from commander.engine.forge_decks import serialize_deck
from tests.unit.test_legality import card

# Test names describe behavior.
# pylint: disable=missing-function-docstring


class CompanionSelectionTests(unittest.TestCase):
    """Native condition admission is required after all ordinary construction checks."""

    def setUp(self):
        self.db = connect(":memory:")
        self.addCleanup(self.db.close)
        with self.db:
            for value in (
                card("Leader", colors=["G"]),
                card("Forest", "Basic Land — Forest", colors=["G"]),
                card("Companion Fixture", text="Companion — Condition.", colors=["G"]),
            ):
                store_card(self.db, value)
            self.db.execute(
                "INSERT INTO imports(source,source_updated_at,records) VALUES (?,?,3)",
                ("oracle_cards", datetime.now(timezone.utc).isoformat()),
            )
        self.base = "Commander\n1 Leader\nMainboard\n99 Forest\n"
        self.text = self.base + "Companion\n1 Companion Fixture\n"

    def test_positive_condition_is_outside_100_and_retained_in_snapshot_and_export(
        self,
    ):
        with patch(
            "commander.rules.companions.check_companion_condition",
            return_value={"eligible": True, "run_id": "fixture-only"},
        ) as native:
            prepared = prepare_deck(self.db, self.text)
            self.assertEqual(prepared["legality"]["status"], "legal", prepared)
            self.assertEqual(prepared["total"], 100)
            self.assertEqual(prepared["supplementary_total"], 1)
            self.assertEqual(
                sum(entry["quantity"] for entry in native.call_args.args[0]), 100
            )
            self.assertTrue(
                any(entry["zone"] == "commander" for entry in native.call_args.args[0])
            )
            deck_id = save_deck(self.db, "Selected companion", 3, prepared)
            revision = get_revision(self.db, deck_id, 1)
            index = {
                entry["card"]["name"]: entry["card"]["name"]
                for entry in revision["cards"]
            }
            self.assertIn(
                "[Sideboard]\n1 Companion Fixture", serialize_deck(revision, index, 1)
            )
            self.assertEqual(
                self.db.execute("SELECT card_id FROM deck_companions").fetchone()[0],
                "Companion Fixture",
            )
            self.assertEqual(
                self.db.execute("SELECT SUM(quantity) FROM deck_cards").fetchone()[0],
                100,
            )

    def test_false_missing_and_failed_native_admission_cannot_be_saved(self):
        for outcome in (
            {"eligible": False},
            ValueError("Missing native proof"),
            OSError("Worker failed"),
        ):
            options = (
                {"side_effect": outcome}
                if isinstance(outcome, Exception)
                else {"return_value": outcome}
            )
            with (
                self.subTest(outcome=outcome),
                patch(
                    "commander.rules.companions.check_companion_condition", **options
                ),
            ):
                prepared = prepare_deck(self.db, self.text)
                self.assertNotEqual(prepared["legality"]["status"], "legal")
                with self.assertRaises(ValueError):
                    save_deck(self.db, "Must fail", 3, prepared)

    def test_invalid_counts_duplicates_and_noncompanion_never_start_native_worker(self):
        for text in (
            self.text.replace("1 Companion Fixture", "2 Companion Fixture"),
            self.text.replace("99 Forest", "98 Forest\n1 Companion Fixture"),
            self.base + "Companion\n1 Forest\n",
            self.text.replace("99 Forest", "98 Forest"),
        ):
            with (
                self.subTest(text=text),
                patch("commander.rules.companions.check_companion_condition") as native,
            ):
                self.assertEqual(
                    prepare_deck(self.db, text)["legality"]["status"], "illegal"
                )
                native.assert_not_called()

    def test_banned_and_off_identity_companions_fail_before_native_analysis(self):
        for colors, legality in ((["U"], "legal"), (["G"], "banned")):
            value = card(
                "Companion Fixture", text="Companion — Condition.", colors=colors
            )
            value["legalities"]["commander"] = legality
            with self.db:
                store_card(self.db, value)
            with patch(
                "commander.rules.companions.check_companion_condition"
            ) as native:
                self.assertEqual(
                    prepare_deck(self.db, self.text)["legality"]["status"], "illegal"
                )
                native.assert_not_called()


class CompanionReceiptTests(unittest.TestCase):
    """A native result must be complete, typed and tied to current Oracle wording."""

    def setUp(self):
        self.plan = {
            "engine": {"jar_sha256": "fixed"},
            "companion": {"engine_name": "Companion Fixture"},
            "condition_text": "Condition.",
        }
        self.receipt = {
            "schema_version": 1,
            "engine": self.plan["engine"],
            "plan_sha256": "frozen",
            "starting_cards": 100,
            "companion_name": "Companion Fixture",
            "eligible": True,
            "condition": "Companion:Special:UniqueNames:Condition.",
            "java_version": "17.0.20",
        }
        self.process = {"status": "finished", "returncode": 0}

    def test_complete_evidence_is_required_for_either_eligibility_result(self):
        for eligible in (True, False):
            validate_receipt(
                {**self.receipt, "eligible": eligible},
                self.plan,
                "frozen",
                self.process,
            )
        for key, value in (
            ("schema_version", True),
            ("starting_cards", 99),
            ("starting_cards", True),
            ("engine", {}),
            ("plan_sha256", "changed"),
            ("eligible", 1),
            ("companion_name", "Wrong Card"),
            ("java_version", "16"),
            ("condition", "Companion:Special:UniqueNames:Changed condition."),
        ):
            with self.subTest(field=key, value=value), self.assertRaises(ValueError):
                validate_receipt(
                    {**self.receipt, key: value}, self.plan, "frozen", self.process
                )
        with self.assertRaises(ValueError):
            validate_receipt(
                self.receipt,
                self.plan,
                "frozen",
                {"status": "timeout", "returncode": 0},
            )
