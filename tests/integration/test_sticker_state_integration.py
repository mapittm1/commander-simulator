"""Verify applied sticker snapshot fidelity using the packaged native runtime."""

import json
import os
import unittest
from contextlib import closing

import pytest

from commander.catalog.database import DEFAULT_DATABASE, connect
from commander.engine.forge_config import read_settings
from commander.engine.interactions.suite import read_bounded_json, write_bounded_json
from commander.engine.native_runtime import create_run
from commander.paths import PROJECT_ROOT
from tests.support.native_worker import run_test_worker

pytestmark = pytest.mark.integration


@unittest.skipUnless(
    os.environ.get("COMMANDER_ENGINE_INTEGRATION") == "1",
    "Requires current sticker catalog and reviewed native engine",
)
class StickerStateIntegrationTests(unittest.TestCase):
    """Direct placement isolates copy fidelity from ticket/payment/target admission."""

    def test_all_catalog_stickers_survive_public_zone_and_lki_copies(self):
        """Check every sheet in bounded eight-sheet native worker batches."""
        settings = read_settings()
        expected = settings.verify()
        with closing(connect(DEFAULT_DATABASE)) as catalog:
            names = [
                row[0]
                for row in catalog.execute(
                    "SELECT name FROM cards WHERE type_line='Stickers' AND commander_legality='legal' ORDER BY name"
                )
            ]
        self.assertEqual(len(names), 48)
        runs = []
        for offset in range(0, len(names), 8):
            run = create_run()
            runs.append(run.name)
            selected = names[offset : offset + 8]
            write_bounded_json(run / "sticker-inputs.json", {"sheets": selected})
            process, _ = run_test_worker(
                settings,
                run,
                PROJECT_ROOT / "tests/support/sticker_state_worker.py",
                "sticker-state.log",
            )
            self.assertEqual(
                (process["status"], process["returncode"]), ("finished", 0), str(run)
            )
            observed = read_bounded_json(run / "sticker-state.json")
            self.assertEqual(observed["engine"], expected)
            self.assertEqual(
                [(row["sheet"], row["zone"]) for row in observed["cases"]],
                [
                    (name, zone)
                    for name in selected
                    for zone in ("Battlefield", "Graveyard", "Exile")
                ],
            )
            for row in observed["cases"]:
                with self.subTest(sheet=row["sheet"], zone=row["zone"]):
                    self.assertEqual(row["original"]["stickers"], 10)
                    self.assertEqual(row["copied"], row["original"])
                    self.assertEqual(row["lki"], row["original"])
                    self.assertEqual(row["available"], 0)
                    self.assertIs(row["independent_sheet"], True)
        self.assertEqual(settings.verify(), expected)
        print(
            json.dumps(
                {
                    "sticker_state_runs": runs,
                    "sheets": len(names),
                    "physical_stickers": len(names) * 10,
                    "zone_cases": len(names) * 3,
                }
            )
        )
