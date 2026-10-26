"""Engine coverage is immutable source evidence, never a gameplay approval."""

import io
import json
import sqlite3
import tempfile
import unittest
from contextlib import ExitStack, redirect_stdout
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import patch

from commander_simulator import main
from database import connect, store_card
from engine_audit import create_engine_audit
from engine_audit_store import audit_cards, show_audit
from forge_config import EngineSettings
from forge_decks import safe_engine_name, script_match, serialize_deck

# Behavior is expressed in test names.
# pylint: disable=missing-function-docstring


class EngineAuditTests(unittest.TestCase):
    """Exercise real catalog persistence and provenance with a deterministic script index."""

    def setUp(self):
        stack = ExitStack()
        self.addCleanup(stack.close)
        self.path = (
            Path(stack.enter_context(tempfile.TemporaryDirectory())) / "audit.sqlite3"
        )
        self.db = connect(self.path)
        self.addCleanup(self.db.close)
        self.settings = EngineSettings(self.path.parent, self.path.parent / "java.exe")
        self.engine = {
            "forge_version": "fixture",
            "jar_sha256": "fixture-jar",
            "cards_sha256": "fixture-cards",
        }
        self.index = {
            "Known": "Known",
            "Fire // Ice": "Fire // Ice",
            "Front // Back": "Front",
            "Unknown legality": "Unknown legality",
            "Unsafe": "Unsafe|SET",
        }
        stack.enter_context(
            patch.object(EngineSettings, "verify", return_value=self.engine)
        )
        stack.enter_context(patch("engine_audit.card_index", return_value=self.index))
        with self.db:
            for number, (name, legality, layout) in enumerate(
                [
                    ("Known", "legal", "normal"),
                    ("Missing A", "legal", "normal"),
                    ("Missing B", "legal", "normal"),
                    ("Fire // Ice", "legal", "split"),
                    ("Front // Back", "legal", "transform"),
                    ("Banned Card", "banned", "normal"),
                    ("Unknown legality", None, None),
                    ("Unsafe", "legal", "normal"),
                ]
            ):
                record = {
                    "name": name,
                    "oracle_id": f"oracle-{number}",
                    "id": f"printing-{number}",
                    "layout": layout,
                    "legalities": {"commander": legality},
                }
                store_card(self.db, record)
            self.db.execute(
                "INSERT INTO imports(source,source_updated_at,records) VALUES (?,?,8)",
                ("oracle_cards", datetime.now(timezone.utc).isoformat()),
            )

    def _create(self):
        return create_engine_audit(self.db, self.settings)

    def test_counts_separate_format_legality_and_script_availability(self):
        stored = self._create()
        report = stored["report"]
        self.assertEqual(report["total_identities"], 8)
        self.assertEqual(report["commander_legal_total"], 6)
        self.assertEqual(report["commander_script_match_fraction"], 0.5)
        self.assertEqual(
            report["counts"]["commander_legal"],
            {"script_present": 3, "missing_script": 2, "unsafe_export": 1},
        )
        self.assertEqual(report["counts"]["other_legality"]["missing_script"], 1)
        self.assertEqual(report["counts"]["unknown_legality"]["script_present"], 1)
        self.assertEqual(report["rules_correctness"], "not_verified")
        self.assertEqual(report["ai_competence"], "not_verified")
        self.assertTrue(stored["historical_snapshot_only"])

    def test_bounded_pages_are_stable_and_filtered_by_scope(self):
        stored = self._create()
        first = audit_cards(self.db, stored["audit_id"], {"limit": 1})
        second = audit_cards(self.db, stored["audit_id"], {"offset": 1, "limit": 1})
        self.assertEqual(first["total_matching"], 2)
        self.assertEqual(
            [first["cards"][0]["name"], second["cards"][0]["name"]],
            ["Missing A", "Missing B"],
        )
        everything = audit_cards(
            self.db, stored["audit_id"], {"scope": "all", "status": "all"}
        )
        self.assertEqual(
            (everything["total_matching"], len(everything["cards"])), (8, 8)
        )
        page = audit_cards(self.db, stored["audit_id"], {"scope": "other_legality"})
        self.assertEqual(page["cards"][0]["name"], "Banned Card")

    def test_split_and_multiface_export_names_are_preserved(self):
        stored = self._create()
        cards = audit_cards(self.db, stored["audit_id"], {"status": "script_present"})[
            "cards"
        ]
        exports = {card["name"]: card["engine_name"] for card in cards}
        self.assertEqual(exports["Fire // Ice"], "Fire // Ice")
        self.assertEqual(exports["Front // Back"], "Front")

    def test_audit_creation_never_starts_a_game_or_java_process(self):
        with patch("engine_process.subprocess.Popen") as process:
            self._create()
            process.assert_not_called()

    def test_current_provenance_is_required_before_engine_work(self):
        for source, updated in (
            ("manual", datetime.now(timezone.utc).isoformat()),
            (
                "oracle_cards",
                (datetime.now(timezone.utc) - timedelta(days=8)).isoformat(),
            ),
            ("oracle_cards", "no timestamp"),
        ):
            with self.db:
                self.db.execute(
                    "UPDATE imports SET source=?,source_updated_at=?", (source, updated)
                )
            with patch.object(EngineSettings, "verify") as verify:
                with self.assertRaisesRegex(ValueError, "current"):
                    self._create()
                verify.assert_not_called()

    def test_catalog_refresh_during_preparation_is_rejected(self):
        def refresh(_):
            with self.db:
                self.db.execute(
                    "INSERT INTO imports(source,source_updated_at,records) VALUES (?,?,8)",
                    ("oracle_cards", datetime.now(timezone.utc).isoformat()),
                )
            return self.index

        with patch("engine_audit.card_index", side_effect=refresh):
            with self.assertRaisesRegex(ValueError, "changed"):
                self._create()
        self.assertEqual(
            self.db.execute("SELECT count(*) FROM engine_audits").fetchone()[0], 0
        )

    def test_engine_changes_and_card_limit_failures_roll_back_everything(self):
        with patch.object(
            EngineSettings,
            "verify",
            side_effect=[self.engine, {**self.engine, "jar_sha256": "changed"}],
        ):
            with self.assertRaisesRegex(ValueError, "Engine evidence changed"):
                self._create()
        with patch("engine_audit.MAX_AUDIT_CARDS", 2):
            with self.assertRaisesRegex(ValueError, "limit"):
                self._create()
        self.assertEqual(
            self.db.execute("SELECT count(*) FROM engine_audits").fetchone()[0], 0
        )
        self.assertEqual(
            self.db.execute("SELECT count(*) FROM engine_audit_cards").fetchone()[0], 0
        )

    def test_empty_catalog_and_oversized_fields_do_not_make_misleading_reports(self):
        with self.db:
            self.db.execute(
                "UPDATE cards SET name=? WHERE card_id='oracle-0'", ("x" * 301,)
            )
        with self.assertRaisesRegex(ValueError, "field"):
            self._create()
        empty = connect(":memory:")
        self.addCleanup(empty.close)
        with empty:
            empty.execute(
                "INSERT INTO imports(source,source_updated_at,records) VALUES (?,?,0)",
                ("oracle_cards", datetime.now(timezone.utc).isoformat()),
            )
        with self.assertRaisesRegex(ValueError, "No catalog"):
            create_engine_audit(empty, self.settings)

    def test_historical_snapshot_survives_later_card_and_engine_changes(self):
        stored = self._create()
        original_page = audit_cards(self.db, stored["audit_id"])
        with self.db:
            self.db.execute(
                "UPDATE cards SET name='Changed later' WHERE card_id='oracle-1'"
            )
        self.index["Missing A"] = "Missing A"
        self.assertEqual(show_audit(self.db, stored["audit_id"]), stored)
        self.assertEqual(audit_cards(self.db, stored["audit_id"]), original_page)

    def test_snapshot_content_fingerprints_are_repeatable(self):
        first = self._create()
        second = self._create()
        self.assertNotEqual(first["audit_id"], second["audit_id"])
        self.assertEqual(first["report_hash"], second["report_hash"])
        self.assertEqual(first["report"]["cards_hash"], second["report"]["cards_hash"])

    def test_sealed_audits_cannot_be_modified_deleted_or_extended(self):
        stored = self._create()
        for statement in (
            "UPDATE engine_audits SET report_json='{}' WHERE id=?",
            "DELETE FROM engine_audits WHERE id=?",
            "UPDATE engine_audit_cards SET name='Changed' WHERE audit_id=?",
            "DELETE FROM engine_audit_cards WHERE audit_id=?",
        ):
            with self.assertRaises(sqlite3.IntegrityError), self.db:
                self.db.execute(statement, (stored["audit_id"],))
        with self.assertRaises(sqlite3.IntegrityError), self.db:
            self.db.execute(
                "INSERT INTO engine_audit_cards VALUES (?,'new','New','commander_legal',"
                "'missing_script','{}','hash')",
                (stored["audit_id"],),
            )

    def test_page_bounds_and_sql_fragments_are_rejected_without_dynamic_sql(self):
        stored = self._create()
        for filters in (
            False,
            [],
            {"extra": 1},
            {"limit": True},
            {"offset": True},
            {"limit": 1001},
            {"offset": -1},
            {"scope": "all' OR 1=1--"},
            {"status": "unknown"},
        ):
            with self.subTest(filters=filters), self.assertRaises(ValueError):
                audit_cards(self.db, stored["audit_id"], filters)
        self.assertEqual(
            show_audit(self.db, stored["audit_id"])["audit_id"], stored["audit_id"]
        )

    def test_integrity_hashes_detect_corrupted_header_and_card_snapshots(self):
        stored = self._create()
        self.db.executescript(
            "DROP TRIGGER engine_audit_cards_no_update; "
            "UPDATE engine_audit_cards SET snapshot_json='{}';"
        )
        with self.assertRaisesRegex(ValueError, "integrity"):
            audit_cards(self.db, stored["audit_id"])
        self.db.executescript(
            "DROP TRIGGER engine_audit_sealed; UPDATE engine_audits SET report_json='{}';"
        )
        with self.assertRaisesRegex(ValueError, "integrity"):
            show_audit(self.db, stored["audit_id"])

    def test_inspection_needs_no_engine_and_does_not_commit_unrelated_work(self):
        stored = self._create()
        output = io.StringIO()
        with (
            patch(
                "engine_commands.read_settings",
                side_effect=RuntimeError("Must not load an engine"),
            ),
            redirect_stdout(output),
        ):
            main(
                [
                    "--database",
                    str(self.path),
                    "engine-audit-cards",
                    stored["audit_id"],
                    "--limit",
                    "1",
                ]
            )
        self.assertEqual(json.loads(output.getvalue())["total_matching"], 2)
        self.db.execute("BEGIN")
        with self.assertRaisesRegex(ValueError, "transaction"):
            show_audit(self.db, stored["audit_id"])
        self.assertTrue(self.db.in_transaction)
        self.db.rollback()


class ScriptMatchTests(unittest.TestCase):
    """Script lookup and deck export share bounded, injection-safe name validation."""

    def test_missing_unsafe_and_exact_alias_matches(self):
        self.assertEqual(script_match("Absent", {})["script_status"], "missing_script")
        self.assertEqual(
            script_match("Card", {"Card": "Card|SET"})["script_status"], "unsafe_export"
        )
        self.assertEqual(
            script_match("Front // Back", {"Front // Back": "Front"})["engine_name"],
            "Front",
        )
        for value in ("", "x" * 301, "Card\n[Commander]", "Card|SET", None):
            self.assertFalse(safe_engine_name(value))
        revision = {
            "id": 1,
            "cards": [{"card": {"name": "Card"}, "zone": "mainboard", "quantity": 1}],
        }
        with self.assertRaisesRegex(ValueError, "Unsafe"):
            serialize_deck(revision, {"Card": "x" * 301}, 1)
