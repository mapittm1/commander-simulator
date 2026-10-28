"""Opt-in native pregame colors from the real deck import/snapshot/export pipeline."""

import json
import os
import unittest
from contextlib import closing

import pytest

from commander.catalog.database import DEFAULT_DATABASE, connect
from commander.decks.deck_store import get_revision
from commander.decks.decks import prepare_deck, save_deck
from commander.engine.catalog_probe_io import source_hashes
from commander.engine.forge_config import (
    file_hash,
    read_settings,
    require_engine_capabilities,
)
from commander.engine.forge_decks import card_index, serialize_deck
from commander.engine.interactions.suite import read_bounded_json, write_bounded_json
from commander.engine.native_runtime import create_run
from commander.paths import PROJECT_ROOT
from tests.support.catalog_fixture import copy_catalog_records
from tests.support.native_worker import run_test_worker

pytestmark = pytest.mark.integration

CASES = (
    (
        "piper_green",
        "1 The Prismatic Piper [color=G]",
        "99 Forest",
        {"The Prismatic Piper": (16, 16)},
        16,
        "G",
    ),
    (
        "faceless_blue",
        "1 Faceless One [color=U]",
        "99 Island",
        {"Faceless One": (2, 2)},
        2,
        "U",
    ),
    (
        "partner_green_red",
        "1 The Prismatic Piper [color=G]\n1 Rograkh, Son of Rohgahh",
        "98 Forest",
        {"The Prismatic Piper": (16, 16), "Rograkh, Son of Rohgahh": (0, 8)},
        24,
        "R G",
    ),
    (
        "background_blue_green",
        "1 Wilson, Refined Grizzly\n1 Faceless One [color=U]",
        "98 Forest",
        {"Wilson, Refined Grizzly": (0, 16), "Faceless One": (2, 2)},
        18,
        "U G",
    ),
)


@unittest.skipUnless(
    os.environ.get("COMMANDER_ENGINE_INTEGRATION") == "1",
    "Requires current catalog and reviewed color-capable native engine",
)
class CommanderColorIntegrationTests(unittest.TestCase):
    """Selected colors must survive every backend and native pregame boundary."""

    def test_single_partner_and_background_choices_reach_native_identity_and_command_tower(
        self,
    ):
        """Observe actual colors and mana options; do not infer success from parsed strings."""
        settings = read_settings()
        run = create_run()
        inputs = _exports(run, settings)
        write_bounded_json(run / "color-inputs.json", inputs)
        process, _ = run_test_worker(
            settings,
            run,
            PROJECT_ROOT / "tests/support/commander_color_worker.py",
            "colors.log",
        )
        self.assertEqual(
            (process["status"], process["returncode"]), ("finished", 0), str(run)
        )
        observed = read_bounded_json(run / "color-receipt.json")
        self.assertEqual(observed["engine"], settings.verify())
        self.assertEqual(
            observed["inputs_sha256"], file_hash(run / "color-inputs.json")
        )
        self.assertEqual(inputs["source_hashes"], source_hashes())
        self.assertEqual(
            [case["id"] for case in observed["cases"]], [case[0] for case in CASES]
        )
        for number, (expected, row) in enumerate(zip(CASES, observed["cases"])):
            with self.subTest(case=expected[0]):
                self.assertEqual(
                    row["deck_sha256"], file_hash(run / f"color-{number}.dck")
                )
                self.assertEqual(row["identity_mask"], expected[4])
                self.assertIs(row["snapshot_is_independent"], True)
                self.assertEqual(row["snapshot_identity_mask"], expected[4])
                self.assertEqual(
                    row["snapshot_choices"],
                    {name: values[0] for name, values in expected[3].items()},
                )
                self.assertEqual(row["tower_colors"], expected[5])
                self.assertEqual(row["main_count"], 100 - len(expected[3]))
                self.assertEqual(
                    {
                        card["name"]: (card["pregame_color"], card["color_mask"])
                        for card in row["commanders"]
                    },
                    expected[3],
                )
                self.assertTrue(all(card["is_commander"] for card in row["commanders"]))
        print(json.dumps({"commander_color_run": run.name, "cases": len(CASES)}))


def _exports(run, settings):
    index = card_index(settings.home / "res/cardsfolder/cardsfolder.zip")
    with (
        closing(connect(DEFAULT_DATABASE)) as source,
        closing(connect(":memory:")) as fixture,
    ):
        catalog = _copy_records(source, fixture)
        for number, definition in enumerate(CASES):
            text = f"Commander\n{definition[1]}\nMainboard\n{definition[2]}\n"
            deck_id = save_deck(
                fixture, "Color pregame fixture", 3, prepare_deck(fixture, text)
            )
            revision = get_revision(fixture, deck_id, 1)
            require_engine_capabilities(settings.verify(), revision["cards"])
            (run / f"color-{number}.dck").write_text(
                serialize_deck(revision, index, 1), encoding="utf-8"
            )
    return {
        "catalog": catalog,
        "source_hashes": source_hashes(),
        "cases": [{"id": case[0]} for case in CASES],
    }


def _copy_records(source, fixture):
    return copy_catalog_records(
        source,
        fixture,
        (
            "The Prismatic Piper",
            "Faceless One",
            "Wilson, Refined Grizzly",
            "Rograkh, Son of Rohgahh",
            "Forest",
            "Island",
        ),
    )
