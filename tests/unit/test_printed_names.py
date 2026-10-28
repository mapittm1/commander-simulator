"""Original printing names must resolve safely to current canonical game cards."""

import unittest

from commander.catalog.database import (
    connect,
    find_deck_candidates,
    get_card_details,
    store_card,
    store_printing,
)
from commander.catalog.printed_names import initialize_aliases
from commander.decks.decks import _resolve_entries, parse_deck_sections

# Test names describe behavior.
# pylint: disable=missing-function-docstring


class PrintedNameTests(unittest.TestCase):
    """Print aliases do not create new Oracle identities or rewrite Oracle rules."""

    def setUp(self):
        self.db = connect(":memory:")
        self.addCleanup(self.db.close)
        self.card = {
            "id": "canonical-print",
            "oracle_id": "identity",
            "name": "Canonical Name",
            "oracle_text": "Current rules",
            "legalities": {"commander": "legal"},
            "lang": "en",
        }
        store_card(self.db, self.card)

    def _printing(self, **fields):
        return dict(
            self.card,
            **dict({"id": "original-print", "printed_name": "Original Name"}, **fields),
        )

    def test_printed_and_flavor_names_share_canonical_identity(self):
        store_printing(self.db, self._printing(flavor_name="Flavor Name"))
        for name in ("Original Name", "original name", "Flavor Name"):
            with self.subTest(name=name):
                result = find_deck_candidates(self.db, name)
                self.assertEqual([row["card_id"] for row in result], ["identity"])
                self.assertEqual(result[0]["name"], "Canonical Name")
        self.assertEqual(find_deck_candidates(self.db, "Original Nam"), [])

    def test_aliases_merge_before_singleton_validation(self):
        store_printing(self.db, self._printing())
        entries = _resolve_entries(
            self.db, parse_deck_sections("1 Original Name\n1 Canonical Name")
        )
        self.assertEqual(len(entries), 1)
        self.assertEqual(entries[0]["quantity"], 2)

    def test_print_enrichment_preserves_current_oracle_and_freshness(self):
        before = get_card_details(self.db, "identity")
        store_printing(self.db, self._printing(oracle_text="Obsolete printed wording"))
        self.assertEqual(get_card_details(self.db, "identity"), before)
        self.assertEqual(
            self.db.execute("SELECT COUNT(*) FROM imports").fetchone()[0], 0
        )
        with self.assertRaises(ValueError):
            store_printing(self.db, self._printing(oracle_id="different"))
        with self.assertRaises(ValueError):
            store_printing(self.db, self._printing(name="Different"))

    def test_foreign_printed_names_are_not_english_card_aliases(self):
        store_printing(self.db, self._printing(lang="fr"))
        self.assertEqual(find_deck_candidates(self.db, "Original Name"), [])

    def test_updated_printing_removes_stale_aliases(self):
        store_printing(self.db, self._printing())
        store_printing(self.db, self._printing(printed_name="Corrected Name"))
        self.assertEqual(find_deck_candidates(self.db, "Original Name"), [])
        self.assertEqual(len(find_deck_candidates(self.db, "Corrected Name")), 1)

    def test_ambiguous_aliases_remain_ambiguous_and_exact_names_win(self):
        store_printing(self.db, self._printing())
        store_card(
            self.db,
            dict(
                self.card,
                id="other",
                oracle_id="other",
                name="Other",
                printed_name="Original Name",
            ),
        )
        self.assertEqual(len(find_deck_candidates(self.db, "Original Name")), 2)
        store_card(
            self.db,
            dict(self.card, id="exact", oracle_id="exact", name="Original Name"),
        )
        self.assertEqual(
            [row["card_id"] for row in find_deck_candidates(self.db, "Original Name")],
            ["exact"],
        )

    def test_face_aliases_and_parameterized_sql(self):
        malicious = "x' OR 1=1 --"
        store_printing(
            self.db,
            self._printing(card_faces=[{"name": "Front", "printed_name": malicious}]),
        )
        self.assertEqual(len(find_deck_candidates(self.db, malicious)), 1)
        self.assertEqual(find_deck_candidates(self.db, "' OR 1=1 --"), [])

    def test_legacy_printings_are_backfilled_once(self):
        store_printing(self.db, self._printing())
        self.db.execute("DELETE FROM printing_aliases")
        self.db.execute("DELETE FROM catalog_alias_state")
        initialize_aliases(self.db)
        self.assertEqual(len(find_deck_candidates(self.db, "Original Name")), 1)
        initialize_aliases(self.db)
        self.assertEqual(
            self.db.execute("SELECT COUNT(*) FROM printing_aliases").fetchone()[0], 1
        )

    def test_existing_printing_cannot_be_reassigned_to_another_card(self):
        store_printing(self.db, self._printing())
        store_card(
            self.db, dict(self.card, id="other", oracle_id="other", name="Other")
        )
        with self.assertRaises(ValueError):
            store_printing(self.db, self._printing(oracle_id="other", name="Other"))
        self.assertEqual(
            find_deck_candidates(self.db, "Original Name")[0]["card_id"], "identity"
        )
