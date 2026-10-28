"""Every catalog meld result must form and separate through the native engine."""

import hashlib
import json
import os
import unittest
from contextlib import closing

import pytest

from commander.catalog.database import (
    DEFAULT_DATABASE,
    connect,
    find_deck_candidates,
    get_card_details,
)
from commander.engine.catalog_probe_io import source_hashes
from commander.engine.forge_config import file_hash, read_settings
from commander.engine.forge_decks import card_index, script_match
from commander.engine.interactions.suite import read_bounded_json, write_bounded_json
from commander.engine.native_runtime import create_run
from commander.paths import PROJECT_ROOT
from commander.rules.card_structure import meld_role
from commander.rules.legality import require_current_catalog
from tests.support.native_worker import run_test_worker

pytestmark = pytest.mark.integration

PAIRS = (
    (
        "Gisela, the Broken Blade",
        "Bruna, the Fading Light",
        "Brisela, Voice of Nightmares",
    ),
    ("Graf Rats", "Midnight Scavengers", "Chittering Host"),
    ("Hanweir Battlements", "Hanweir Garrison", "Hanweir, the Writhing Township"),
    ("Urza, Lord Protector", "The Mightstone and Weakstone", "Urza, Planeswalker"),
    ("Mishra, Claimed by Gix", "Phyrexian Dragon Engine", "Mishra, Lost to Phyrexia"),
    ("Titania, Voice of Gaea", "Argoth, Sanctum of Nature", "Titania, Gaea Incarnate"),
    ("Vanille, Cheerful l'Cie", "Fang, Fearless l'Cie", "Ragnarok, Divine Deliverance"),
)
VARIANTS = (
    "form_graveyard",
    "form_exile",
    "missing",
    "not_owned",
    "not_controlled",
    "clone",
    "graveyard",
)


@unittest.skipUnless(
    os.environ.get("COMMANDER_ENGINE_INTEGRATION") == "1",
    "Requires current catalog and reviewed native engine",
)
class MeldIntegrationTests(unittest.TestCase):
    """Partial-state resolution checks; trigger admission, timing and payment are separate."""

    def test_all_catalog_meld_results_form_separate_and_reject_invalid_parts(self):
        """Bind current identities and exercise actual native effects without replacing scripts."""
        settings = read_settings()
        run = create_run()
        with closing(connect(DEFAULT_DATABASE)) as db:
            catalog = require_current_catalog(db)
            index = card_index(settings.home / "res/cardsfolder/cardsfolder.zip")
            pairs = [_pair(db, index, definition) for definition in PAIRS]
            observed_results = {
                json.loads(row[0])["name"]
                for row in db.execute(
                    "SELECT raw_json FROM cards JOIN card_details USING(card_id) WHERE layout='meld' AND commander_legality='legal'"
                )
                if meld_role(json.loads(row[0])) == "meld_result"
            }
        self.assertEqual(observed_results, {definition[2] for definition in PAIRS})
        inputs = {
            "catalog": catalog,
            "pairs": pairs,
            "variants": list(VARIANTS),
            "source_hashes": source_hashes(),
        }
        path = run / "meld-inputs.json"
        write_bounded_json(path, inputs)
        process, _ = run_test_worker(
            settings, run, PROJECT_ROOT / "tests/support/meld_worker.py", "meld.log"
        )
        self.assertEqual(
            (process["status"], process["returncode"]), ("finished", 0), str(run)
        )
        receipt = read_bounded_json(run / "meld-receipt.json")
        self.assertEqual(receipt["engine"], settings.verify())
        self.assertEqual(receipt["inputs_sha256"], file_hash(path))
        self.assertEqual(inputs["source_hashes"], source_hashes())
        self.assertEqual(
            [(row["result"], row["variant"]) for row in receipt["cases"]],
            [(pair[2], variant) for pair in PAIRS for variant in VARIANTS],
        )
        for row, definition in zip(
            receipt["cases"], (pair for pair in PAIRS for _ in VARIANTS)
        ):
            with self.subTest(result=row["result"], variant=row["variant"]):
                self._assert_case(row, definition)
        print(
            json.dumps(
                {
                    "meld_run": run.name,
                    "results": len(PAIRS),
                    "cases": len(receipt["cases"]),
                }
            )
        )

    def _assert_case(self, row, definition):
        """Check independent formation and destination expectations."""
        expected = row["variant"].startswith("form_")
        self.assertIs(row["formed"], expected)
        if expected:
            self.assertEqual(row["linked_name"], definition[1])
            self.assertEqual(row["state"], "Meld")
            self.assertEqual(row["after"]["Battlefield"], [definition[2]])
            destination = "Graveyard" if row["variant"] == "form_graveyard" else "Exile"
            self.assertEqual(
                row["after_departure"][destination], sorted(definition[:2])
            )
            self.assertEqual(row["after_departure"]["Battlefield"], [])
        else:
            self.assertNotIn(definition[2], row["after"]["Battlefield"])


def _pair(db, index, definition):
    records = {}
    for role, name in zip(("primary", "secondary", "result"), definition):
        cards = find_deck_candidates(db, name)
        if len(cards) != 1:
            raise ValueError("Missing exact meld identity: " + name)
        card = cards[0]
        oracle = get_card_details(db, card["card_id"])
        expected_role = "meld_result" if role == "result" else "meld_part"
        if meld_role(oracle) != expected_role:
            raise ValueError("Unverified meld role: " + name)
        records[role] = {
            "name": name,
            "card_id": card["card_id"],
            "oracle_sha256": hashlib.sha256(
                json.dumps(oracle, sort_keys=True).encode()
            ).hexdigest(),
            "printing_id": oracle["id"],
        }
        if role != "result":
            match = script_match(name, index, card["card_id"])
            if match["script_status"] != "script_present":
                raise ValueError("Missing native meld front: " + name)
            records[role]["engine_name"] = match["engine_name"]
    return records
