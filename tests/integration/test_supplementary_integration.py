"""Opt-in native loading for every catalog sticker sheet and legal Attraction."""

import json
import os
import unittest
from contextlib import closing

import pytest

from commander.catalog.database import DEFAULT_DATABASE, connect, store_card
from commander.decks.deck_store import get_revision
from commander.decks.decks import prepare_deck, save_deck
from commander.engine.forge_config import file_hash, read_settings
from commander.engine.forge_decks import card_index, serialize_deck
from commander.engine.forge_engine import _new_run
from commander.engine.interactions.suite import read_bounded_json
from commander.paths import PROJECT_ROOT
from commander.rules.legality import require_current_catalog
from tests.support.native_worker import run_test_worker

pytestmark = pytest.mark.integration


@unittest.skipUnless(
    os.environ.get("COMMANDER_ENGINE_INTEGRATION") == "1",
    "Requires current catalog, pinned engine and optional native bridge",
)
class NativeSupplementaryTests(unittest.TestCase):
    """Native observations must match the validated, exported card selections."""

    def test_all_sheets_and_legal_attractions_load_without_entering_library(self):
        """Verify native parsing, all selections and exactly three revealed sheets."""
        settings = read_settings()
        run = _new_run()
        expected = settings.verify()
        index = card_index(settings.home / "res/cardsfolder/cardsfolder.zip")
        sheets, attractions = _export_fixture(run, index)
        worker = PROJECT_ROOT / "tests/support/supplementary_worker.py"
        process, worker_hash = run_test_worker(
            settings, run, worker, "supplementary.log"
        )
        self.assertEqual(
            (process["status"], process["returncode"]), ("finished", 0), str(run)
        )
        observed = read_bounded_json(run / "supplementary-observations.json")
        self.assertEqual(observed["engine"], expected)
        self.assertEqual(settings.verify(), expected)
        self.assertEqual(file_hash(worker), worker_hash)
        for filename in ("selected", "empty"):
            self.assertEqual(
                observed[filename + "_sha256"], file_hash(run / (filename + ".dck"))
            )
        self.assertEqual(observed["selected"]["registered_sheets"], sheets)
        self.assertEqual(observed["selected"]["registered_attractions"], attractions)
        self.assertEqual(observed["selected"]["attraction_deck"], attractions)
        chosen = observed["selected"]["chosen_sheets"]
        self.assertEqual(len(set(chosen)), 3)
        self.assertEqual(len(chosen), 3)
        self.assertTrue(set(chosen).issubset(sheets))
        for name in ("selected", "empty"):
            self.assertEqual(observed[name]["main_count"], 99)
            self.assertEqual(observed[name]["library"], 0)
            self.assertEqual(observed[name]["battlefield"], 0)
        for field in (
            "registered_sheets",
            "registered_attractions",
            "chosen_sheets",
            "attraction_deck",
        ):
            self.assertEqual(observed["empty"][field], [])
        print(
            json.dumps(
                {
                    "supplementary_run": run.name,
                    "sheets": len(sheets),
                    "attractions": len(attractions),
                    "chosen_sheets": chosen,
                }
            )
        )


def _copy_records(source, fixture):
    catalog = require_current_catalog(source)
    sheets, attractions = [], []
    rows = source.execute(
        "SELECT cards.name,type_line,raw_json FROM cards JOIN card_details USING(card_id) "
        "WHERE name IN ('Ghalta, Primal Hunger','Forest') OR "
        "(commander_legality='legal' AND (type_line='Stickers' OR type_line LIKE '%Attraction%')) ORDER BY cards.name"
    ).fetchall()
    with fixture:
        for row in rows:
            store_card(fixture, json.loads(row["raw_json"]))
            if row["type_line"] == "Stickers":
                sheets.append(row["name"])
            elif "Attraction" in row["type_line"]:
                attractions.append(row["name"])
        fixture.execute(
            "INSERT INTO imports(source,source_updated_at,records) VALUES (?,?,?)",
            (catalog["source"], catalog["source_updated_at"], len(rows)),
        )
    if len(sheets) < 10 or len(attractions) < 10:
        raise ValueError(
            "Native pregame fixture requires complete supplementary catalog records"
        )
    return sorted(sheets), sorted(attractions)


def _export_fixture(run, index):
    """Use normal validation and snapshots before native fixture export."""
    with (
        closing(connect(DEFAULT_DATABASE)) as source,
        closing(connect(":memory:")) as fixture,
    ):
        sheets, attractions = _copy_records(source, fixture)
        base = "Commander\n1 Ghalta, Primal Hunger\nMainboard\n99 Forest\n"
        selected = base + "Stickers\n" + "".join(f"1 {name}\n" for name in sheets)
        selected += "Attractions\n" + "".join(f"1 {name}\n" for name in attractions)
        for filename, text in (("selected", selected), ("empty", base)):
            prepared = prepare_deck(fixture, text)
            deck_id = save_deck(fixture, "Native pregame fixture", 2, prepared)
            exported = serialize_deck(get_revision(fixture, deck_id, 1), index, 1)
            (run / (filename + ".dck")).write_text(exported, encoding="utf-8")
    return sheets, attractions
