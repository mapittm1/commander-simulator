"""Regression coverage for immutable snapshots, migration, and atomic revisions."""

import json
import sqlite3
import unittest
from datetime import datetime, timezone
from unittest.mock import patch

from commander.catalog.database import connect, store_card
from commander.decks.deck_store import (
    ensure_deck_schema,
    get_revision,
    list_decks,
    list_revisions,
    save_deck,
    save_revision,
)
from commander.decks.decks import prepare_deck
from commander.rules.legality import require_legal_deck

# Behavior is documented by test names.
# pylint: disable=missing-function-docstring


class DeckStoreTests(unittest.TestCase):
    """Verify durable versions rather than a mutable deck masquerading as history."""

    def setUp(self):
        self.db = connect(":memory:")
        self.addCleanup(self.db.close)
        with self.db:
            for name, types in (
                ("Leader", "Legendary Creature — Human"),
                ("Forest", "Basic Land — Forest"),
                ("Spell", "Sorcery"),
            ):
                record = {
                    "oracle_id": name,
                    "id": name,
                    "type_line": types,
                    "name": name,
                    "color_identity": ["G"],
                    "legalities": {"commander": "legal"},
                    "oracle_text": "Original text",
                }
                store_card(self.db, record)
            self.db.execute(
                "INSERT INTO imports(source, source_updated_at, records) VALUES (?, ?, 3)",
                ("oracle_cards", datetime.now(timezone.utc).isoformat()),
            )

    def _prepared(self, changed=False):
        text = "98 Forest\n1 Spell" if changed else "99 Forest"
        return prepare_deck(self.db, text, ["Leader"])

    def test_revisions_preserve_cards_and_brackets(self):
        deck_id = save_deck(self.db, "My deck", 2, self._prepared())
        first = get_revision(self.db, deck_id, 1)
        save_revision(self.db, deck_id, self._prepared(True), bracket=3)
        self.assertEqual(get_revision(self.db, deck_id, 1), first)
        second = get_revision(self.db, deck_id)
        self.assertEqual(second["version"], 2)
        self.assertEqual(first["declared_bracket"], 2)
        self.assertEqual(second["declared_bracket"], 3)
        self.assertNotEqual(first["deck_hash"], second["deck_hash"])
        self.assertEqual(list_decks(self.db)[0]["revision_id"], second["id"])

    def test_database_rejects_revision_updates_and_deletes(self):
        deck_id = save_deck(self.db, "Immutable", 3, self._prepared())
        for sql in (
            "UPDATE deck_revisions SET name = 'Changed'",
            "DELETE FROM deck_revisions",
        ):
            with self.assertRaises(sqlite3.IntegrityError):
                self.db.execute(sql)
            self.db.rollback()
        self.assertEqual(get_revision(self.db, deck_id)["name"], "Immutable")

    def test_catalog_updates_do_not_rewrite_snapshots(self):
        deck_id = save_deck(self.db, "Snapshot", 3, self._prepared())
        first = get_revision(self.db, deck_id)
        with self.db:
            changed = json.loads(
                self.db.execute(
                    "SELECT raw_json FROM card_details WHERE card_id = 'Leader'"
                ).fetchone()[0]
            )
            changed["oracle_text"] = "Updated text"
            changed["legalities"]["commander"] = "banned"
            store_card(self.db, changed)
        self.assertEqual(get_revision(self.db, deck_id), first)
        with self.assertRaises(ValueError):
            require_legal_deck(self.db, deck_id, version=1)

    def test_invalid_revision_does_not_advance_history(self):
        deck_id = save_deck(self.db, "Valid", 3, self._prepared())
        invalid = prepare_deck(self.db, "98 Forest", ["Leader"])
        with self.assertRaises(ValueError):
            save_revision(self.db, deck_id, invalid)
        self.assertEqual(len(list_revisions(self.db, deck_id)), 1)

    def test_failed_write_rolls_back_revision_and_projection(self):
        deck_id = save_deck(self.db, "Atomic", 3, self._prepared())
        with patch(
            "commander.decks.deck_store._update_projection",
            side_effect=sqlite3.OperationalError("disk failure"),
        ):
            with self.assertRaises(sqlite3.OperationalError):
                save_revision(self.db, deck_id, self._prepared(True))
        self.assertEqual(len(list_revisions(self.db, deck_id)), 1)
        self.assertEqual(get_revision(self.db, deck_id)["version"], 1)

    def test_revision_gate_ignores_mutable_legacy_projection(self):
        deck_id = save_deck(self.db, "Authoritative", 3, self._prepared())
        with self.db:
            self.db.execute(
                "UPDATE deck_cards SET quantity = 1 WHERE card_id = 'Forest'"
            )
        self.assertEqual(require_legal_deck(self.db, deck_id)["status"], "legal")

    def test_unknown_revision_is_not_replaced_by_latest(self):
        deck_id = save_deck(self.db, "Known", 3, self._prepared())
        with self.assertRaises(ValueError):
            get_revision(self.db, deck_id, 99)
        with self.assertRaises(ValueError):
            require_legal_deck(self.db, deck_id, version=99)

    def test_invalid_bracket_types_are_rejected(self):
        for bracket in (True, 3.0, "3", 0, 6):
            with self.subTest(bracket=bracket), self.assertRaises(ValueError):
                save_deck(self.db, "Invalid metadata", bracket, self._prepared())

    def test_legacy_migration_is_idempotent(self):
        self.db.executescript("""
            CREATE TABLE decks (
                id INTEGER PRIMARY KEY, name TEXT, declared_bracket INTEGER,
                source_url TEXT, imported_at TEXT DEFAULT CURRENT_TIMESTAMP,
                validation_json TEXT
            );
            CREATE TABLE deck_cards (
                deck_id INTEGER, card_id TEXT, zone TEXT, quantity INTEGER
            );
        """)
        with self.db:
            self.db.execute(
                "INSERT INTO decks(id, name, declared_bracket, validation_json) "
                "VALUES (42, 'Legacy', 3, ?)",
                ('{"status":"preliminary"}',),
            )
            self.db.executemany(
                "INSERT INTO deck_cards VALUES (42, ?, ?, ?)",
                [("Leader", "commander", 1), ("Forest", "mainboard", 99)],
            )
        ensure_deck_schema(self.db)
        ensure_deck_schema(self.db)
        revision = get_revision(self.db, 42)
        self.assertEqual(revision["version"], 1)
        self.assertIn("migration_note", revision["validation"])
        self.assertEqual(len(list_revisions(self.db, 42)), 1)


if __name__ == "__main__":
    unittest.main()
