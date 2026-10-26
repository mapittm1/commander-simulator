"""Legality regression tests use controlled card records and catalog timestamps."""

import json
import unittest
from datetime import datetime, timedelta, timezone

from database import connect, store_card
from decks import list_decks, prepare_deck, save_deck
from legality import compatible_commanders, eligible_commander, require_legal_deck

# Test names describe the expected behavior.
# pylint: disable=missing-function-docstring


def card(name, types="Legendary Creature — Human", text="", colors=None):
    """Create a small, Commander-legal Scryfall-shaped fixture."""
    return {
        "id": name,
        "oracle_id": name,
        "name": name,
        "type_line": types,
        "oracle_text": text,
        "color_identity": colors or [],
        "legalities": {"commander": "legal"},
    }


class LegalityTests(unittest.TestCase):
    """Exercise construction rules, fail-closed decisions, and gate revalidation."""

    def setUp(self):
        self.db = connect(":memory:")
        self.addCleanup(self.db.close)
        self._put(card("Leader", colors=["G", "R", "B"]))
        self._put(card("Forest", "Basic Land — Forest", colors=["G"]))
        with self.db:
            self.db.execute(
                "INSERT INTO imports(source, source_updated_at, records) VALUES (?, ?, ?)",
                ("oracle_cards", datetime.now(timezone.utc).isoformat(), 2),
            )

    def _put(self, value):
        with self.db:
            store_card(self.db, value)

    def _prepare(self, main="99 Forest", commanders=("Leader",)):
        return prepare_deck(self.db, main, commanders)

    def test_ordinary_deck_is_legal(self):
        self.assertEqual(self._prepare()["legality"]["status"], "legal")

    def test_deck_size_is_enforced(self):
        self.assertEqual(self._prepare("98 Forest")["legality"]["status"], "illegal")

    def test_nonlegendary_and_ordinary_planeswalkers_cannot_command(self):
        for types in ("Creature — Human", "Legendary Planeswalker — Test"):
            self._put(card("Bad Leader", types, colors=["G"]))
            self.assertEqual(
                self._prepare(commanders=["Bad Leader"])["legality"]["status"],
                "illegal",
            )

    def test_vehicle_and_spacecraft_eligibility(self):
        self.assertTrue(
            eligible_commander(card("Vehicle", "Legendary Artifact — Vehicle"))
        )
        spacecraft = card("Ship", "Legendary Artifact — Spacecraft")
        self.assertFalse(eligible_commander(spacecraft))
        spacecraft.update(power="4", toughness="4")
        self.assertTrue(eligible_commander(spacecraft))

    def test_explicit_commander_permission(self):
        self.assertTrue(
            eligible_commander(
                card(
                    "Walker",
                    "Legendary Planeswalker — Test",
                    "Walker can be your commander.",
                )
            )
        )

    def test_reverse_face_does_not_grant_eligibility(self):
        value = card("Front // Back")
        value["card_faces"] = [card("Front", "Artifact"), card("Back")]
        self.assertFalse(eligible_commander(value))

    def test_unrelated_commanders_cannot_pair(self):
        self._put(card("Other", colors=["G"]))
        self.assertEqual(
            self._prepare("98 Forest", ["Leader", "Other"])["legality"]["status"],
            "illegal",
        )

    def test_partner_variants_cannot_mix(self):
        first = card("A", text="Partner")
        second = card("B", text="Friends forever")
        self.assertFalse(compatible_commanders(first, second))
        second["oracle_text"] = "Partner"
        self.assertTrue(compatible_commanders(first, second))

    def test_named_partner_is_reciprocal(self):
        first = card("A", text="Partner with B (Reminder.)")
        second = card("B", text="Partner with A (Reminder.)")
        self.assertTrue(compatible_commanders(first, second))
        second["oracle_text"] = "Partner with C"
        self.assertFalse(compatible_commanders(first, second))

    def test_background_requires_correct_primary(self):
        primary = card("A", text="Choose a Background")
        background = card("B", "Legendary Enchantment — Background")
        self.assertTrue(compatible_commanders(primary, background))
        self.assertFalse(eligible_commander(background))
        self.assertFalse(compatible_commanders(card("Other"), background))

    def test_doctor_cannot_have_extra_creature_types(self):
        companion = card("Companion", text="Doctor's companion")
        doctor = card("Doctor", "Legendary Creature — Time Lord Doctor")
        self.assertTrue(compatible_commanders(companion, doctor))
        doctor["type_line"] += " Human"
        self.assertFalse(compatible_commanders(companion, doctor))

    def test_finite_and_unlimited_copy_exceptions(self):
        self._put(
            card(
                "Seven",
                "Creature — Dwarf",
                "A deck can have up to seven cards named Seven.",
            )
        )
        self.assertEqual(
            self._prepare("92 Forest\n7 Seven")["legality"]["status"], "legal"
        )
        self.assertEqual(
            self._prepare("91 Forest\n8 Seven")["legality"]["status"], "illegal"
        )
        self._put(
            card(
                "Rats",
                "Creature — Rat",
                "A deck can have any number of cards named Rats.",
            )
        )
        self.assertEqual(self._prepare("99 Rats")["legality"]["status"], "legal")

    def test_basic_land_type_restriction_without_colored_identity(self):
        self._put(card("Island Variant", "Land — Island"))
        result = self._prepare("98 Forest\n1 Island Variant")
        self.assertTrue(
            any("Basic land type" in message for message in result["issues"])
        )

    def test_banned_cards_cannot_pass(self):
        banned = card("Banned", "Artifact")
        banned["legalities"]["commander"] = "banned"
        self._put(banned)
        self.assertEqual(
            self._prepare("98 Forest\n1 Banned")["legality"]["status"], "illegal"
        )

    def test_stale_catalog_blocks_approval(self):
        with self.db:
            self.db.execute(
                "UPDATE imports SET source_updated_at = ?",
                ((datetime.now(timezone.utc) - timedelta(days=8)).isoformat(),),
            )
        prepared = self._prepare()
        self.assertEqual(prepared["legality"]["status"], "needs_review")
        with self.assertRaises(ValueError):
            save_deck(self.db, "Stale", 3, prepared)

    def test_unknown_color_choice_blocks_approval(self):
        self._put(
            card(
                "Choice",
                text="If Choice is your commander, choose a color before the game begins.",
            )
        )
        self.assertNotEqual(
            self._prepare(commanders=["Choice"])["legality"]["status"], "legal"
        )

    def test_saved_status_cannot_bypass_revalidation(self):
        deck_id = save_deck(self.db, "Legal deck", 3, self._prepare())
        self.assertEqual(require_legal_deck(self.db, deck_id)["status"], "legal")
        banned = card("Leader", colors=["G", "R", "B"])
        banned["legalities"]["commander"] = "banned"
        self._put(banned)
        self.assertEqual(
            json.loads(list_decks(self.db)[0]["validation_json"])["status"], "legal"
        )
        with self.assertRaises(ValueError):
            require_legal_deck(self.db, deck_id)

    def test_forged_prepared_card_fields_cannot_bypass_gate(self):
        prepared = self._prepare("98 Forest")
        prepared["issues"] = []
        prepared["legality"]["status"] = "legal"
        with self.assertRaises(ValueError):
            save_deck(self.db, "Forged", 3, prepared)


class CharacteristicTests(unittest.TestCase):
    """Cover deck-zone characteristics that differ from the printed type line."""

    def test_off_battlefield_creature_can_command(self):
        value = card(
            "Grist, the Hunger Tide",
            "Legendary Planeswalker — Grist",
            "As long as Grist isn't on the battlefield, it's a 1/1 Insect creature "
            "in addition to its other types.",
        )
        self.assertTrue(eligible_commander(value))


class StartingStructureTests(unittest.TestCase):
    """Distinguish physical starting cards from combined faces and supplementary pieces."""

    def setUp(self):
        self.connection = connect(":memory:")
        self.addCleanup(self.connection.close)
        with self.connection:
            store_card(self.connection, card("Leader", colors=["G"]))
            store_card(
                self.connection, card("Forest", "Basic Land — Forest", colors=["G"])
            )
            self.connection.execute(
                "INSERT INTO imports(source,source_updated_at,records) VALUES (?,?,2)",
                ("oracle_cards", datetime.now(timezone.utc).isoformat()),
            )

    def _meld(self, role):
        value = card("Meld Fixture", colors=["G"])
        value["layout"] = "meld"
        value["all_parts"] = [{"id": value["id"], "component": role}]
        with self.connection:
            store_card(self.connection, value)

    def test_meld_result_is_illegal_in_main_deck_or_command_zone(self):
        self._meld("meld_result")
        for text, commanders in (
            ("98 Forest\n1 Meld Fixture", ["Leader"]),
            ("99 Forest", ["Meld Fixture"]),
        ):
            result = prepare_deck(self.connection, text, commanders)["legality"]
            self.assertEqual(result["status"], "illegal")
            self.assertTrue(any("Meld result" in error for error in result["errors"]))

    def test_meld_front_is_startable_and_unknown_role_blocks(self):
        self._meld("meld_part")
        self.assertEqual(
            prepare_deck(self.connection, "99 Forest", ["Meld Fixture"])["legality"][
                "status"
            ],
            "legal",
        )
        self._meld("combo_piece")
        self.assertEqual(
            prepare_deck(self.connection, "99 Forest", ["Meld Fixture"])["legality"][
                "status"
            ],
            "needs_review",
        )

    def test_meld_role_is_not_inferred_from_a_name_or_another_printing(self):
        value = card("Meld Fixture", colors=["G"])
        value.update(
            layout="meld",
            all_parts=[{"id": "another-printing", "component": "meld_part"}],
        )
        with self.connection:
            store_card(self.connection, value)
        self.assertEqual(
            prepare_deck(self.connection, "99 Forest", ["Meld Fixture"])["legality"][
                "status"
            ],
            "needs_review",
        )

    def test_sticker_sheet_cannot_fill_a_starting_deck_slot(self):
        with self.connection:
            store_card(self.connection, card("Sheet", "Stickers"))
        report = prepare_deck(self.connection, "98 Forest\n1 Sheet", ["Leader"])[
            "legality"
        ]
        self.assertEqual(report["status"], "illegal")
        self.assertTrue(any("Sticker sheet" in error for error in report["errors"]))


if __name__ == "__main__":
    unittest.main()
