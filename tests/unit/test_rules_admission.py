"""Known incorrect card interactions must stop before storage or engine execution."""

import copy
import hashlib
import json
import tempfile
import unittest
from contextlib import ExitStack, nullcontext
from pathlib import Path
from unittest.mock import Mock, patch

from commander.decks.pod_import import import_reviewed_pod
from commander.engine.forge_engine import _execute_game, game_evidence, run_game
from commander.engine.rules_admission import (
    check_rules_admission,
    verify_rules_admission,
)
from commander.paths import CONFIG_ROOT, PROJECT_ROOT

# Test names describe the behavior being asserted.
# pylint: disable=missing-function-docstring

CARD_ID = "c905227d-b5fe-42e6-a11d-5008b7a7922f"
NAME = "B'Elanna Torres, Scrapper"
ENGINE = {"jar_sha256": "a" * 64, "cards_sha256": "b" * 64, "forge_version": "fixture"}
REVISION = {"id": 1, "cards": [{"card": {"card_id": CARD_ID, "name": NAME}}]}


class RulesAdmissionTests(unittest.TestCase):
    """Bound policy parsing and preserve exact reviews through launch admission."""

    def setUp(self):
        resources = ExitStack()
        self.addCleanup(resources.close)
        self.root = Path(resources.enter_context(tempfile.TemporaryDirectory()))
        self.path = self.root / "issues.json"
        resources.enter_context(
            patch("commander.engine.rules_admission.ISSUES_PATH", self.path)
        )
        self.policy = {
            "schema_version": 1,
            "issues": [
                {
                    "issue_id": "fixture_draw_replacement",
                    "card_id": CARD_ID,
                    "card_name": NAME,
                    "reason": "Putting a card into hand is not drawing it.",
                    "evidence": "engine/fixture.json",
                    "reviewed_builds": [
                        {
                            "jar_sha256": ENGINE["jar_sha256"],
                            "cards_sha256": ENGINE["cards_sha256"],
                            "evidence": "engine/fixture.json",
                            "evidence_sha256": "c" * 64,
                        }
                    ],
                }
            ],
        }
        self.write_policy()

    def write_policy(self):
        self.path.write_text(json.dumps(self.policy), encoding="utf-8")

    def test_exact_engine_resource_pair_carries_a_bounded_policy_receipt(self):
        result = check_rules_admission(ENGINE, [REVISION])
        self.assertEqual(
            result["policy_sha256"], hashlib.sha256(self.path.read_bytes()).hexdigest()
        )
        self.assertEqual(result["reviewed_issues"][0]["card_id"], CARD_ID)
        self.assertEqual(result["reviewed_issues"][0]["evidence_sha256"], "c" * 64)
        verify_rules_admission(ENGINE, [REVISION], result)

    def test_either_fingerprint_change_rejects_without_trusting_capability_claims(self):
        for key in ("jar_sha256", "cards_sha256"):
            engine = {
                **ENGINE,
                key: "d" * 64,
                "capabilities": ["all_cards"],
                "native_tests": {"passed": 999999},
            }
            with (
                self.subTest(key=key),
                self.assertRaisesRegex(ValueError, "Known engine rules issue"),
            ):
                check_rules_admission(engine, [REVISION])

    def test_card_identity_cannot_be_bypassed_by_a_different_display_name(self):
        revision = copy.deepcopy(REVISION)
        revision["cards"][0]["card"]["name"] = "Different upload display"
        with self.assertRaisesRegex(ValueError, "Known engine rules issue"):
            check_rules_admission({}, [revision])
        revision["cards"][0]["card"]["card_id"] = "00000000-0000-0000-0000-000000000000"
        revision["cards"][0]["card"]["name"] = NAME
        self.assertIsNone(check_rules_admission({}, [revision]))

    def test_a_matching_pair_from_another_issue_does_not_approve_this_card(self):
        other = copy.deepcopy(self.policy["issues"][0])
        other.update(
            issue_id="other_issue", card_id="00000000-0000-0000-0000-000000000000"
        )
        self.policy["issues"][0]["reviewed_builds"] = []
        self.policy["issues"].append(other)
        self.write_policy()
        with self.assertRaisesRegex(ValueError, "Known engine rules issue"):
            check_rules_admission(ENGINE, [REVISION])

    def test_policy_changes_invalidate_reviewed_receipts(self):
        evidence = check_rules_admission(ENGINE, [REVISION])
        self.path.write_bytes(self.path.read_bytes() + b"\n")
        with self.assertRaisesRegex(ValueError, "policy changed"):
            verify_rules_admission(ENGINE, [REVISION], evidence)

    def test_newly_applicable_issue_is_rechecked_even_without_an_earlier_receipt(self):
        self.policy["issues"] = []
        self.write_policy()
        self.assertIsNone(check_rules_admission({}, [REVISION]))
        self.policy["issues"] = [
            {
                "issue_id": "new_issue",
                "card_id": CARD_ID,
                "card_name": NAME,
                "reason": "Newly confirmed error",
                "evidence": "engine/new.json",
                "reviewed_builds": [],
            }
        ]
        self.write_policy()
        with self.assertRaisesRegex(ValueError, "Known engine rules issue"):
            verify_rules_admission({}, [REVISION], None)

    def test_duplicate_json_fields_and_structural_limits_are_rejected(self):
        for raw in (
            b'{"schema_version":1,"schema_version":1,"issues":[]}',
            b" " * (64 * 1024 + 1),
            b'{"schema_version":1,"issues":' + b"[" * 40 + b"]" * 40 + b"}",
        ):
            with self.subTest(size=len(raw)):
                self.path.write_bytes(raw)
                with self.assertRaises(ValueError):
                    check_rules_admission(ENGINE, [REVISION])

    def test_malformed_policy_reviews_fail_closed(self):
        original = copy.deepcopy(self.policy)
        changes = [
            lambda p: p.update(schema_version=True),
            lambda p: p.update(extra=True),
            lambda p: p.update(issues=p["issues"] * 65),
            lambda p: p["issues"].append(copy.deepcopy(p["issues"][0])),
            lambda p: p["issues"][0].update(card_id="not-an-id"),
            lambda p: p["issues"][0].update(card_name=""),
            lambda p: p["issues"][0].update(reason="x" * 1001),
            lambda p: p["issues"][0].update(issue_id="bad/id"),
            lambda p: p["issues"][0].update(unknown=True),
            lambda p: p["issues"][0].update(reviewed_builds=[{}]),
            lambda p: p["issues"][0].update(
                reviewed_builds=p["issues"][0]["reviewed_builds"] * 2
            ),
            lambda p: p["issues"][0]["reviewed_builds"][0].update(jar_sha256="x" * 64),
        ]
        for number, mutate in enumerate(changes):
            self.policy = copy.deepcopy(original)
            mutate(self.policy)
            self.write_policy()
            with self.subTest(number=number), self.assertRaises(ValueError):
                check_rules_admission(ENGINE, [REVISION])

    def test_known_issue_blocks_game_before_exports_or_process_creation(self):
        settings = Mock()
        settings.verify.return_value = {}
        settings.home = self.root
        with (
            patch(
                "commander.engine.forge_engine.prepare_seats",
                return_value=[{"revision": REVISION}],
            ),
            patch("commander.engine.forge_engine.card_index", return_value={}),
            patch(
                "commander.engine.forge_engine.coverage", return_value={"missing": []}
            ),
            patch("commander.engine.forge_engine.serialize_deck") as export,
            patch("commander.engine.forge_engine.run_bounded") as process,
        ):
            with self.assertRaisesRegex(ValueError, "Known engine rules issue"):
                run_game(None, settings, [(1, 1)] * 4)
            export.assert_not_called()
            process.assert_not_called()

    def test_known_issue_blocks_pod_import_before_any_decks_are_saved(self):
        settings = Mock()
        settings.verify.return_value = {}
        settings.home = self.root
        deck = {**REVISION, "legality": {"status": "legal"}}
        with (
            patch("commander.decks.pod_import.require_review_attestation"),
            patch("commander.decks.pod_import.read_decklist", return_value="deck"),
            patch("commander.decks.pod_import.prepare_deck", return_value=deck),
            patch("commander.decks.pod_import.card_index", return_value={}),
            patch(
                "commander.decks.pod_import.coverage",
                return_value={"status": "supported_scripts"},
            ),
            patch("commander.decks.pod_import.save_deck") as save,
        ):
            with self.assertRaisesRegex(ValueError, "Known engine rules issue"):
                import_reviewed_pod(None, settings, [self.root / "deck.txt"] * 4, 2, {})
            save.assert_not_called()

    def test_launch_rechecks_policy_and_never_turns_rejection_into_a_result(self):
        evidence = check_rules_admission(ENGINE, [REVISION])
        self.policy["issues"][0]["reviewed_builds"] = []
        self.write_policy()
        inputs = {
            "engine": ENGINE,
            "seats": [{"revision": REVISION}],
            "decks": [],
            "rules_admission": evidence,
        }
        with (
            patch("commander.engine.forge_engine._new_run", return_value=self.root),
            patch(
                "commander.engine.forge_engine._engine_profile",
                return_value=nullcontext(),
            ),
            patch("commander.engine.forge_engine.run_bounded") as process,
        ):
            settings = Mock()
            settings.verify.return_value = ENGINE
            result = _execute_game(settings, inputs, 42, 60)
        self.assertEqual(result["status"], "engine_error")
        self.assertIsNone(result["winner_seat"])
        self.assertIn("Known engine rules issue", result["error"])
        process.assert_not_called()

    def test_changed_engine_cannot_reuse_an_earlier_rules_approval(self):
        inputs = {
            "engine": ENGINE,
            "seats": [{"revision": REVISION}],
            "decks": [],
            "rules_admission": check_rules_admission(ENGINE, [REVISION]),
        }
        settings = Mock()
        settings.verify.return_value = {**ENGINE, "jar_sha256": "d" * 64}
        with (
            patch("commander.engine.forge_engine._new_run", return_value=self.root),
            patch(
                "commander.engine.forge_engine._engine_profile",
                return_value=nullcontext(),
            ),
            patch("commander.engine.forge_engine.run_bounded") as process,
        ):
            result = _execute_game(settings, inputs, 42, 60)
        self.assertEqual(result["status"], "engine_error")
        self.assertIsNone(result["winner_seat"])
        self.assertIn("Pinned engine changed", result["error"])
        process.assert_not_called()

    def test_game_evidence_retains_review_only_when_an_issue_applies(self):
        inputs = {
            "engine": ENGINE,
            "coverage": [],
            "seats": [{"revision": REVISION, "review": {}}],
        }
        self.assertNotIn("rules_admission", game_evidence(inputs))
        inputs["rules_admission"] = check_rules_admission(ENGINE, [REVISION])
        self.assertEqual(
            game_evidence(inputs)["rules_admission"], inputs["rules_admission"]
        )


class CheckedInRulesPolicyTests(unittest.TestCase):
    """Keep checked-in approvals tied to the preserved validation artifacts."""

    def test_approved_pairs_match_their_preserved_build_evidence(self):
        policy = json.loads((CONFIG_ROOT / "engine_rules_issues.json").read_bytes())
        for issue in policy["issues"]:
            for review in issue["reviewed_builds"]:
                with self.subTest(issue=issue["issue_id"]):
                    path = (PROJECT_ROOT / review["evidence"]).resolve()
                    self.assertTrue(path.is_relative_to(PROJECT_ROOT / "engine"))
                    raw = path.read_bytes()
                    self.assertEqual(
                        hashlib.sha256(raw).hexdigest(), review["evidence_sha256"]
                    )
                    build = json.loads(raw)["build"]
                    for key in ("jar_sha256", "cards_sha256"):
                        self.assertEqual(review[key], build[key])
