"""Isolated legal/reviewed pod fixtures shared by batch and CLI regression tests."""

import tempfile
import unittest
from contextlib import ExitStack
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import patch

from commander.catalog.database import connect, store_card
from commander.decks.deck_store import save_deck
from commander.decks.decks import prepare_deck
from commander.engine.forge_config import EngineSettings
from commander.rules.brackets import load_policy, save_bracket_review
from commander.simulation.batches import BatchOptions, create_batch, run_batch


class BatchFixture(unittest.TestCase):
    """Provide a current synthetic catalog and immutable reviewed test-only decks."""

    def setUp(self):
        """Prepare a legal synthetic pod without executing an external engine."""
        stack = ExitStack()
        self.addCleanup(stack.close)
        self.path = (
            Path(stack.enter_context(tempfile.TemporaryDirectory())) / "fixture.sqlite3"
        )
        self.db = connect(self.path)
        self.addCleanup(self.db.close)
        self.settings = EngineSettings(Path.cwd(), Path.cwd() / "java.exe")
        names = ["Leader A", "Leader B", "Leader C", "Leader D", "Forest"]
        self.lock = {
            "forge_version": "test",
            "jar_sha256": "unit",
            "cards_sha256": "unit",
        }
        stack.enter_context(
            patch.object(EngineSettings, "verify", return_value=self.lock)
        )
        stack.enter_context(
            patch(
                "commander.engine.forge_engine.card_index",
                return_value={name: name for name in names},
            )
        )
        with self.db:
            for name in names:
                record = {
                    "id": name,
                    "oracle_id": name,
                    "name": name,
                    "game_changer": False,
                    "color_identity": ["G"],
                    "legalities": {"commander": "legal"},
                    "type_line": (
                        "Basic Land â€” Forest"
                        if name == "Forest"
                        else "Legendary Creature â€” Human"
                    ),
                }
                store_card(self.db, record)
            self.db.execute(
                "INSERT INTO imports(source,source_updated_at,records) VALUES (?,?,5)",
                ("oracle_cards", datetime.now(timezone.utc).isoformat()),
            )
        self.review = {
            "reviewer": "Unit test fixture",
            "rationale": "Fixture only, not production.",
            "checks": dict.fromkeys(load_policy()["review_checks"], True),
        }
        self.seats = []
        for name in names[:4]:
            deck_id = save_deck(
                self.db, "Batch fixture", 2, prepare_deck(self.db, "99 Forest", [name])
            )
            save_bracket_review(self.db, deck_id, self.review)
            self.seats.append((deck_id, 1))

    def _create(self, runs=4):
        return create_batch(self.db, self.settings, self.seats, BatchOptions(runs))

    def _worker(self, job_id, games=1, result=None):
        if result is None:
            result = {"status": "completed", "outcome": "win", "winner_seat": 1}
        with patch(
            "commander.engine.forge_engine._execute_game",
            side_effect=lambda *_: dict(result),
        ):
            return run_batch(self.db, self.settings, job_id, games)
