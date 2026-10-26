"""Opt-in real-engine smoke and limited rules-witness tests with disposable decks."""

import json
import os
import re
import unittest
from contextlib import closing
from pathlib import Path

import pytest

from commander.catalog.database import DEFAULT_DATABASE, connect
from commander.diagnostics.batch_diagnostics import batch_diagnostics
from commander.engine.engine_conformance import run_rules_conformance
from commander.engine.engine_fixtures import legal_fixture_pod
from commander.engine.forge_config import read_settings
from commander.engine.forge_engine import run_game
from commander.simulation.batches import BatchOptions, create_batch, run_batch

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

    def test_reviewed_identity_exports_and_casts(self):
        """The reviewed alias reaches the pinned engine with the real Oracle identity."""
        leaders = {
            "Ghalta, Primal Hunger": "Forest",
            "Goreclaw, Terror of Qal Sisma": "Forest",
            "Surrak, the Hunt Caller": "Forest",
            "Ratonhnhaké꞉ton": {"Plains": 33, "Island": 33, "Swamp": 33},
        }
        with (
            closing(connect(DEFAULT_DATABASE)) as source,
            legal_fixture_pod(source, leaders) as (fixture, seats),
        ):
            result = run_game(fixture, read_settings(), seats, seed=42, game_clock=60)
        self.assertEqual(result["status"], "completed", result)
        run = Path(result["directory"])
        self.assertIn(
            "1 Ratonhnhaké:ton", (run / "seat-4.dck").read_text(encoding="utf-8")
        )
        inputs = json.loads((run / "inputs.json").read_text(encoding="utf-8"))
        self.assertEqual(
            inputs["coverage"][3]["alias_reviews"][0]["oracle_id"],
            "a37f8381-83a4-4b6b-8176-c90f6a61da74",
        )
        log = (run / "forge.log").read_text(encoding="utf-8", errors="replace")
        self.assertIn("Add To Stack: Ai(4)-seat-4 cast Ratonhnhaké:ton", log)
        hit = re.search(
            r"Damage: Ratonhnhaké:ton \([0-9]+\) deals 3 combat damage to Ai", log
        )
        self.assertIsNotNone(hit)
        position = hit.end()
        for marker in (
            "Add To Stack: Ai(4)-seat-4 triggered Ratonhnhaké:ton",
            "Resolve Stack: Whenever Ratonhnhaké:ton deals combat damage",
            "create a 1/1 black Assassin creature token with menace.",
            "Assassin Token (",
        ):
            position = log.find(marker, position)
            self.assertGreaterEqual(position, 0, marker)
            position += len(marker)
        print(
            json.dumps(
                {"identity_smoke_run": result["run_id"], "status": result["status"]}
            )
        )
