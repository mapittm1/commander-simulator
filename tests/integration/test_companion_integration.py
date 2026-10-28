"""Opt-in companion admission includes the commander and survives normal persistence."""

import json
import os
import unittest
from contextlib import closing

import pytest

from commander.catalog.database import DEFAULT_DATABASE, connect
from commander.decks.deck_store import get_revision
from commander.decks.decks import prepare_deck, save_deck
from commander.engine.forge_config import read_settings
from commander.engine.forge_decks import card_index, serialize_deck
from commander.rules.legality import require_legal_deck
from tests.support.catalog_fixture import copy_catalog_records

pytestmark = pytest.mark.integration


@unittest.skipUnless(
    os.environ.get("COMMANDER_ENGINE_INTEGRATION") == "1",
    "Requires catalog, pinned engine and native bridge",
)
class NativeCompanionTests(unittest.TestCase):
    """A commander-only condition violation must fail despite a valid 99-card library."""

    def test_lurrus_condition_includes_commander_and_cached_evidence_survives_revalidation(
        self,
    ):
        """Admit Elas, reject Queen Marchesa and retain the explicit outside-game card."""
        with (
            closing(connect(DEFAULT_DATABASE)) as source,
            closing(connect(":memory:")) as fixture,
        ):
            _copy_records(source, fixture)
            positive = _deck("Elas il-Kor, Sadistic Pilgrim")
            prepared = prepare_deck(fixture, positive)
            self.assertEqual(prepared["legality"]["status"], "legal", prepared)
            evidence = dict(prepared["legality"]["companion_admission"] or {})
            self.assertIs(evidence["eligible"], True)
            self.assertEqual(evidence["starting_cards"], 100)
            deck_id = save_deck(fixture, "Lurrus companion fixture", 3, prepared)
            revision = get_revision(fixture, deck_id, 1)
            refreshed = require_legal_deck(fixture, deck_id, 1)
            self.assertEqual(
                dict(refreshed["companion_admission"] or {})["run_id"],
                evidence["run_id"],
            )
            settings = read_settings()
            exported = serialize_deck(
                revision,
                card_index(settings.home / "res/cardsfolder/cardsfolder.zip"),
                1,
            )
            self.assertIn("[Sideboard]\n1 Lurrus of the Dream-Den", exported)
            negative = prepare_deck(fixture, _deck("Queen Marchesa"))
            self.assertEqual(negative["legality"]["status"], "illegal", negative)
            failed = dict(negative["legality"]["companion_admission"] or {})
            self.assertIs(failed["eligible"], False)
            self.assertNotEqual(failed["run_id"], evidence["run_id"])
            self.assertTrue(
                any("companion condition" in error for error in negative["issues"])
            )
            with self.assertRaises(ValueError):
                save_deck(fixture, "Must not save", 3, negative)
            print(
                json.dumps(
                    {
                        "companion_positive_run": evidence["run_id"],
                        "commander_only_rejection_run": failed["run_id"],
                    }
                )
            )


def _deck(commander):
    return f"Commander\n1 {commander}\nMainboard\n99 Plains\nCompanion\n1 Lurrus of the Dream-Den\n"


def _copy_records(source, fixture):
    return copy_catalog_records(
        source,
        fixture,
        (
            "Elas il-Kor, Sadistic Pilgrim",
            "Queen Marchesa",
            "Plains",
            "Lurrus of the Dream-Den",
        ),
    )
