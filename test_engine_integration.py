"""Opt-in real-engine smoke and limited rules-witness tests with disposable decks."""

import json
import os
import unittest
from contextlib import closing
from pathlib import Path

import pytest

from batch_diagnostics import batch_diagnostics
from batches import BatchOptions, create_batch, run_batch
from database import DEFAULT_DATABASE, connect
from engine_conformance import run_rules_conformance
from engine_fixtures import legal_fixture_pod
from forge_config import read_settings
from forge_engine import run_game

pytestmark = pytest.mark.integration


@unittest.skipUnless(
    os.environ.get("COMMANDER_ENGINE_INTEGRATION") == "1",
    "Requires an operator-installed engine and current local catalog",
)
class LiveEngineTests(unittest.TestCase):
    """Use the real pinned runtime without producing production decks or reviews."""

    def test_four_player_game(self):
        """Verify variant exports, saved diagnostics, and two-game chunk resume."""
        leaders = {
            "Ghalta, Primal Hunger": "Forest",
            "Goreclaw, Terror of Qal Sisma": "Forest",
            "Surrak, the Hunt Caller": "Forest",
            "Vikya, Scorching Stalwart": "Plains",
        }
        with (
            closing(connect(DEFAULT_DATABASE)) as source,
            legal_fixture_pod(source, leaders) as (fixture, seats),
        ):
            result = run_game(fixture, read_settings(), seats, seed=42, game_clock=60)
            self.assertEqual(result["status"], "completed", result)
            run = Path(result["directory"])
            for name in (
                "inputs.json",
                "result.json",
                "forge.log",
                "diagnostics.json",
                "events.jsonl",
            ):
                self.assertTrue((run / name).is_file())
            self.assertEqual(result["diagnostics"]["status"], "observed")
            self.assertIn(
                "1 Ryu, World Warrior", (run / "seat-4.dck").read_text(encoding="utf-8")
            )
            batch = create_batch(
                fixture, read_settings(), seats, BatchOptions(2, seed=43, game_clock=60)
            )
            result = run_batch(fixture, read_settings(), batch["job_id"])
            self.assertEqual(
                (result["status"], result["completed"]), ("paused", 1), result
            )
            final = run_batch(fixture, read_settings(), batch["job_id"])
            self.assertEqual(
                (final["status"], final["completed"], final["failed"]),
                ("completed", 2, 0),
                final,
            )
            self.assertEqual(
                batch_diagnostics(fixture, batch["job_id"])["coverage"][
                    "games_included"
                ],
                2,
            )

    def test_limited_rules_witnesses(self):
        """No assertion passes merely because a game completed or a case was absent."""
        with closing(connect(DEFAULT_DATABASE)) as source:
            report = run_rules_conformance(source, read_settings())
        self.assertEqual(report["status"], "passed", report)
        self.assertEqual(len(report["checks"]), 3)
        self.assertTrue(all(check["status"] == "passed" for check in report["checks"]))
        print(json.dumps(report, indent=2))
