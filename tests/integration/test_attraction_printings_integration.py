"""Every retained legal Attraction printing must retain its exact native light variant."""

import json
import os
import unittest
from contextlib import closing

import pytest

from commander.catalog.database import DEFAULT_DATABASE, connect, store_printing
from commander.catalog.printing_choices import PRINTING_FIELDS
from commander.decks.deck_store import get_revision
from commander.decks.decks import prepare_deck, save_deck
from commander.engine.catalog_probe_io import source_hashes
from commander.engine.forge_config import file_hash, read_settings
from commander.engine.forge_decks import card_index, serialize_deck
from commander.engine.interactions.suite import read_bounded_json, write_bounded_json
from commander.engine.native_runtime import create_run
from commander.engine.printing_admission import check_printing_choices
from commander.engine.printing_resources import edition_fingerprint
from commander.paths import PROJECT_ROOT
from tests.support.catalog_fixture import copy_catalog_records
from tests.support.native_worker import run_test_worker

pytestmark = pytest.mark.integration


@unittest.skipUnless(
    os.environ.get("COMMANDER_ENGINE_INTEGRATION") == "1",
    "Requires current catalog, English Attraction printings and reviewed native engine",
)
class AttractionPrintingIntegrationTests(unittest.TestCase):
    """Source printings must survive backend revisions, native registration and AI copies."""

    def test_all_retained_attraction_printings_preserve_lights_and_collector_identity(
        self,
    ):
        """Use actual legality/export services and native preflight without changing scripts."""
        settings = read_settings()
        run = create_run()
        inputs, revisions, index = _exports(run, settings)
        admission = check_printing_choices(settings, revisions, index)
        self.assertEqual(len(admission["printings"]), len(inputs["choices"]))
        self.assertEqual(admission["editions"], edition_fingerprint(settings.home))
        write_bounded_json(run / "attraction-inputs.json", inputs)
        process, _ = run_test_worker(
            settings,
            run,
            PROJECT_ROOT / "tests/support/attraction_printing_worker.py",
            "attraction-printings.log",
        )
        self.assertEqual(
            (process["status"], process["returncode"]), ("finished", 0), str(run)
        )
        observed = read_bounded_json(run / "attraction-receipt.json")
        self.assertEqual(observed["engine"], settings.verify())
        self.assertEqual(
            observed["inputs_sha256"], file_hash(run / "attraction-inputs.json")
        )
        self.assertEqual(inputs["source_hashes"], source_hashes())
        self.assertEqual(
            [row["id"] for row in observed["cases"]],
            [choice["id"] for choice in inputs["choices"]],
        )
        for number, (choice, row) in enumerate(
            zip(inputs["choices"], observed["cases"])
        ):
            with self.subTest(
                card=choice["name"], collector=choice["collector_number"]
            ):
                expected = {
                    key: choice[key]
                    for key in ("name", "set", "collector_number", "attraction_lights")
                }
                expected.update(zone_size=10, is_attraction=True)
                self.assertEqual(row["original"], expected)
                self.assertEqual(row["copied"], expected)
                self.assertIs(row["independent_copy"], True)
                self.assertEqual(
                    row["deck_sha256"], file_hash(run / f"attraction-{number}.dck")
                )
        print(
            json.dumps(
                {
                    "attraction_printing_run": run.name,
                    "printings": len(inputs["choices"]),
                    "identities": len(
                        {choice["oracle_id"] for choice in inputs["choices"]}
                    ),
                }
            )
        )


def _exports(run, settings):
    index = card_index(settings.home / "res/cardsfolder/cardsfolder.zip")
    with (
        closing(connect(DEFAULT_DATABASE)) as source,
        closing(connect(":memory:")) as fixture,
    ):
        printings = [
            json.loads(row[0])
            for row in source.execute(
                "SELECT printings.raw_json FROM printings JOIN cards USING(card_id) WHERE commander_legality='legal' AND type_line LIKE '%Attraction%' AND printings.language='en' ORDER BY scryfall_id"
            )
        ]
        names = sorted({printing["name"] for printing in printings})
        catalog = copy_catalog_records(
            source, fixture, ["Wilson, Refined Grizzly", "Forest", *names]
        )
        if len(names) < 10 or len(printings) > 256:
            raise ValueError(
                "Attraction fixture requires at least ten identities and at most 256 printings"
            )
        with fixture:
            for printing in printings:
                store_printing(fixture, printing)
        revisions = [
            _export_case(
                fixture, index, run / f"attraction-{number}.dck", printing, names
            )
            for number, printing in enumerate(printings)
        ]
    return (
        {
            "catalog": catalog,
            "source_hashes": source_hashes(),
            "choices": [
                {key: printing[key] for key in PRINTING_FIELDS}
                for printing in printings
            ],
        },
        revisions,
        index,
    )


def _export_case(fixture, index, path, printing, names):
    remaining = [name for name in names if name != printing["name"]][:9]
    text = "Commander\n1 Wilson, Refined Grizzly\nMainboard\n99 Forest\nAttractions\n"
    text += f"1 {printing['name']} ({printing['set']}) {printing['collector_number']}\n"
    text += "".join(f"1 {name}\n" for name in remaining)
    prepared = prepare_deck(fixture, text)
    deck_id = save_deck(fixture, "Attraction printing " + printing["id"], 3, prepared)
    revision = get_revision(fixture, deck_id, 1)
    path.write_text(serialize_deck(revision, index, 1), encoding="utf-8")
    return revision
