"""Engineering gap priorities preserve historical evidence and never approve aliases."""

import json
import sqlite3
import unittest
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import patch

from database import connect, store_card
from engine_audit import create_engine_audit
from engine_gaps import audit_gap_backlog, classify_gap
from forge_config import EngineSettings

# Behavior is expressed in test names.
# pylint: disable=missing-function-docstring


class GapPriorityTests(unittest.TestCase):
    """Triage priorities are distinct from playability, popularity, and engine support."""

    def test_supplementary_objects_and_meld_results_are_not_starting_script_work(self):
        sticker = classify_gap(
            {"name": "Sheet", "type_line": "Stickers"}, {}, "missing_script"
        )
        meld = classify_gap(
            {
                "name": "Result",
                "layout": "meld",
                "id": "result",
                "all_parts": [{"id": "result", "component": "meld_result"}],
            },
            {},
            "missing_script",
        )
        self.assertEqual((sticker["priority"], meld["priority"]), (0, 0))

    def test_punctuation_candidate_is_explicitly_unapproved(self):
        result = classify_gap(
            {"name": "Name꞉part"}, {"Name:part": "Name:part"}, "missing_script"
        )
        self.assertEqual(result["category"], "name_review")
        self.assertFalse(result["candidate_is_approved"])
        self.assertEqual(result["candidate_engine_name"], "Name:part")

    def test_mechanic_words_are_only_hints(self):
        result = classify_gap(
            {"name": "Card", "oracle_text": "You may put a sticker on a creature."},
            {},
            "missing_script",
        )
        self.assertEqual(result["category"], "supplementary_mechanic_hint")
        self.assertEqual(result["oracle_terms"], ["sticker"])
        result = classify_gap(
            {"name": "Other", "oracle_text": "Draw a card."}, {}, "missing_script"
        )
        self.assertEqual(result["priority"], 2)

    def test_unknown_or_malformed_meld_roles_need_review(self):
        result = classify_gap(
            {
                "name": "Unknown",
                "layout": "meld",
                "id": "u",
                "all_parts": [{"id": "u", "component": []}],
            },
            {},
            "missing_script",
        )
        self.assertEqual(result["category"], "metadata_review")
        self.assertFalse(result["metadata_evidence"]["role_approved"])

    def test_a_related_meld_result_name_does_not_prove_own_printing_role(self):
        result = classify_gap(
            {
                "name": "Meld Result",
                "oracle_id": "oracle",
                "id": "own-printing",
                "layout": "meld",
                "all_parts": [
                    {
                        "id": "other-printing",
                        "name": "Meld Result",
                        "component": "meld_result",
                    }
                ],
            },
            {},
            "missing_script",
        )
        evidence = result["metadata_evidence"]
        self.assertEqual(result["category"], "metadata_review")
        self.assertEqual(evidence["own_printing_id"], "own-printing")
        self.assertEqual(evidence["exact_self_reference_count"], 0)
        self.assertEqual(evidence["related_printings"][0]["id"], "other-printing")
        self.assertFalse(evidence["role_approved"])


class FrozenGapBacklogTests(unittest.TestCase):
    """Audit triage survives metadata updates and is unavailable in legacy audits."""

    def setUp(self):
        self.db = connect(":memory:")
        self.addCleanup(self.db.close)
        with self.db:
            for name, types in (("Sheet", "Stickers"), ("Missing", "Artifact")):
                store_card(
                    self.db,
                    {
                        "id": name,
                        "name": name,
                        "oracle_id": name,
                        "type_line": types,
                        "legalities": {"commander": "legal"},
                    },
                )
            self.db.execute(
                "INSERT INTO imports(source,source_updated_at,records) VALUES (?,?,2)",
                ("oracle_cards", datetime.now(timezone.utc).isoformat()),
            )
        settings = EngineSettings(Path.cwd(), Path.cwd() / "java.exe")
        with (
            patch.object(
                EngineSettings,
                "verify",
                return_value={
                    "forge_version": "test",
                    "jar_sha256": "jar",
                    "cards_sha256": "cards",
                },
            ),
            patch("engine_audit.card_index", return_value={}),
        ):
            self.stored = create_engine_audit(self.db, settings)

    def test_non_starting_records_are_counted_but_not_in_actionable_page(self):
        report = audit_gap_backlog(self.db, self.stored["audit_id"])
        self.assertEqual(
            (
                report["total_gap_records"],
                report["actionable_records"],
                report["non_starting_records"],
            ),
            (2, 1, 1),
        )
        self.assertEqual(report["cards"][0]["name"], "Missing")

    def test_later_source_metadata_does_not_reclassify_old_audit(self):
        before = audit_gap_backlog(self.db, self.stored["audit_id"])
        with self.db:
            self.db.execute(
                "UPDATE card_details SET raw_json=? WHERE card_id='Sheet'",
                (
                    json.dumps(
                        {"id": "Sheet", "name": "Sheet", "type_line": "Creature"}
                    ),
                ),
            )
        self.assertEqual(audit_gap_backlog(self.db, self.stored["audit_id"]), before)

    def test_legacy_snapshot_cannot_be_presented_as_fresh_triage(self):
        with patch(
            "engine_gaps.read_audit", return_value={"report": {"auditor_version": 1}}
        ):
            with self.assertRaisesRegex(ValueError, "fresh audit"):
                audit_gap_backlog(self.db, self.stored["audit_id"])

    def test_report_keeps_the_stored_classifier_version(self):
        baseline = audit_gap_backlog(self.db, self.stored["audit_id"])
        with patch("engine_gaps.CLASSIFIER_VERSION", 999):
            self.assertEqual(
                audit_gap_backlog(self.db, self.stored["audit_id"]), baseline
            )

    def test_pagination_and_immutability(self):
        self.assertEqual(
            audit_gap_backlog(self.db, self.stored["audit_id"], offset=1)["cards"], []
        )
        for offset, limit in ((-1, 1), (True, 1), (0, 1001)):
            with self.assertRaises(ValueError):
                audit_gap_backlog(self.db, self.stored["audit_id"], offset, limit)
        with self.assertRaises(sqlite3.IntegrityError), self.db:
            self.db.execute("UPDATE engine_audit_cards SET snapshot_json='{}'")
