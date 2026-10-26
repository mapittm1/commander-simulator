"""Opt-in real-engine smoke test using disposable, explicitly labeled fixtures."""

import json
import os
import sqlite3
import tempfile
import unittest
from contextlib import closing
from pathlib import Path

import pytest

from batch_diagnostics import batch_diagnostics
from batches import BatchOptions, create_batch, run_batch
from brackets import load_policy, save_bracket_review
from database import DEFAULT_DATABASE, connect, store_card
from deck_store import save_deck
from decks import prepare_deck
from forge_config import read_settings
from forge_engine import run_game

pytestmark = pytest.mark.integration


@unittest.skipUnless(
    os.environ.get("COMMANDER_ENGINE_INTEGRATION") == "1",
    "Requires an operator-installed engine and current local catalog",
)
class LiveEngineTests(unittest.TestCase):
    """Verify four legal fixtures through the real adapter, without production reviews."""

    def test_four_player_game(self):
        """Preserve the run logs but keep all fixture decks and reviews disposable."""
        leaders = [
            "Ghalta, Primal Hunger",
            "Goreclaw, Terror of Qal Sisma",
            "Surrak, the Hunt Caller",
            "Ruxa, Patient Professor",
        ]
        with (
            tempfile.TemporaryDirectory() as folder,
            closing(
                sqlite3.connect(f"file:{DEFAULT_DATABASE.as_posix()}?mode=ro", uri=True)
            ) as catalog,
            closing(connect(Path(folder) / "fixture.sqlite3")) as fixture,
        ):
            latest = catalog.execute(
                "SELECT source, source_updated_at FROM imports "
                "ORDER BY id DESC LIMIT 1"
            ).fetchone()
            with fixture:
                for name in [*leaders, "Forest"]:
                    raw = catalog.execute(
                        "SELECT raw_json FROM card_details JOIN cards "
                        "USING(card_id) WHERE name = ?",
                        (name,),
                    ).fetchone()
                    store_card(fixture, json.loads(raw[0]))
                fixture.execute(
                    "INSERT INTO imports(source,source_updated_at,records) "
                    "VALUES (?,?,5)",
                    latest,
                )
            seats = []
            for leader in leaders:
                deck_id = save_deck(
                    fixture,
                    "Disposable engine fixture",
                    2,
                    prepare_deck(fixture, "99 Forest", [leader]),
                )
                with self.assertRaisesRegex(ValueError, "No bracket review"):
                    run_game(fixture, read_settings(), [(deck_id, 1)] * 4)
                save_bracket_review(
                    fixture,
                    deck_id,
                    {
                        "reviewer": "Automated test fixture only",
                        "rationale": "99 Forest and one creature; not a production attestation.",
                        "checks": dict.fromkeys(load_policy()["review_checks"], True),
                    },
                )
                seats.append((deck_id, 1))
            result = run_game(fixture, read_settings(), seats, seed=42, game_clock=60)
            self.assertEqual(result["status"], "completed", result)
            run = Path(result["directory"])
            self.assertTrue((run / "inputs.json").is_file())
            self.assertTrue((run / "result.json").is_file())
            self.assertGreater((run / "forge.log").stat().st_size, 0)
            self.assertEqual(result["diagnostics"]["status"], "observed")
            self.assertTrue((run / "diagnostics.json").is_file())
            self.assertTrue((run / "events.jsonl").is_file())
            print(json.dumps(result, indent=2))
            batch = create_batch(
                fixture, read_settings(), seats, BatchOptions(2, seed=43, game_clock=60)
            )
            result = run_batch(fixture, read_settings(), batch["job_id"])
            self.assertEqual(
                (result["status"], result["completed"]), ("paused", 1), result
            )
            final = run_batch(fixture, read_settings(), batch["job_id"])
            self.assertEqual(
                (final["status"], final["completed"]), ("completed", 2), final
            )
            self.assertEqual(final["failed"], 0)
            self.assertEqual(
                batch_diagnostics(fixture, batch["job_id"])["coverage"][
                    "games_included"
                ],
                2,
            )
            print(
                json.dumps(
                    {key: value for key, value in final.items() if key != "spec"},
                    indent=2,
                )
            )
