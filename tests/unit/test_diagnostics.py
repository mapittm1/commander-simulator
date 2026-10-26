"""Observed metrics must retain evidence and never invent hidden-state conclusions."""

import json
import unittest
from contextlib import ExitStack
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch

from commander.catalog.database import connect
from commander.diagnostics.batch_diagnostics import batch_diagnostics
from commander.diagnostics.diagnostic_artifacts import record_diagnostics
from commander.diagnostics.game_diagnostics import summarize_events
from commander.diagnostics.game_events import extract_events
from commander.engine.forge_engine import _read_game_output
from commander.simulation.batch_store import encode_spec, ensure_schema

# Behavior is expressed in test names.
# pylint: disable=missing-function-docstring

LOG = "\n".join(
    [
        "Ignored startup message",
        "Turn: Turn 1 (Ai(1)-seat-1)",
        "Phase: Ai(1)-seat-1's Main phase, precombat",
        "Land: Ai(1)-seat-1 played Forest (1)",
        "Add To Stack: Ai(1)-seat-1 cast Card with punctuation, here!",
        "Combat: Ai(1)-seat-1 assigned Attacker (7) to attack Ai(3)-seat-3.",
        "Phase: Ai(1)-seat-1's Cleanup step",
        "Turn: Turn 2 (Ai(2)-seat-2)",
        "Phase: Ai(2)-seat-2's Main phase, precombat",
        "Phase: Ai(2)-seat-2's Cleanup step",
        "Game Outcome: Ai(1)-seat-1 has won because all opponents have lost",
        "Game Result: Game 1 ended in 123 ms. Ai(1)-seat-1 has won!",
    ]
)


def report(text=LOG, status="completed"):
    """Build a pure diagnostic report from a synthetic log fixture."""
    return summarize_events(extract_events(text), status)


class EventTests(unittest.TestCase):
    """Bounded parsing with exact seats, card data, and original source lines."""

    def test_event_types_and_line_evidence(self):
        parsed = extract_events(LOG)
        self.assertEqual(
            parsed["events"][0], {"kind": "turn", "line": 2, "turn": 1, "seat": 1}
        )
        attack = next(event for event in parsed["events"] if event["kind"] == "attack")
        self.assertEqual((attack["seat"], attack["target"], attack["line"]), (1, 3, 6))
        self.assertEqual(parsed["unparsed_candidates"], {})

    def test_malformed_players_and_unsupported_targets_are_not_guessed(self):
        text = "\n".join(
            [
                "Land: Ai(1)-seat-2 played Forest (1)",
                "Combat: Ai(1)-seat-1 assigned Attacker (7) to attack Planeswalker (8).",
                "Combat: Ai(1)-seat-1 assigned Attacker (7) to attack Ai(1)-seat-1.",
                "Add To Stack: Ai(9)-seat-9 cast Unknown",
            ]
        )
        parsed = extract_events(text)
        self.assertEqual(parsed["events"], [])
        self.assertEqual(
            parsed["unparsed_candidates"], {"land": 1, "attack": 2, "cast": 1}
        )
        self.assertEqual(report(text)["status"], "partial")

    def test_unrelated_blocks_mana_and_triggers_are_outside_metric_scope(self):
        parsed = extract_events(
            "\n".join(
                [
                    "Combat: Ai(2)-seat-2 didn't block Attacker (7).",
                    "Mana: Forest (1) - {T}: Add {G}.",
                    "Add To Stack: Ai(1)-seat-1 triggered Card",
                ]
            )
        )
        self.assertEqual(parsed["events"], [])
        self.assertEqual(parsed["unparsed_candidates"], {})

    def test_crlf_and_embedded_control_characters_preserve_physical_line_numbers(self):
        self.assertEqual(
            extract_events(LOG.replace("\n", "\r\n"))["events"][0]["line"], 2
        )
        parsed = extract_events(
            "Warning: ignored\vLand: Ai(1)-seat-1 played Forest (1)\n"
            "Turn: Turn 1 (Ai(1)-seat-1)"
        )
        self.assertEqual(
            parsed["events"], [{"kind": "turn", "line": 2, "turn": 1, "seat": 1}]
        )

    def test_input_event_line_and_issue_sample_limits(self):
        with patch("commander.diagnostics.game_events.MAX_LOG_BYTES", 100):
            with self.assertRaises(ValueError):
                extract_events("x" * 101)
        with self.assertRaises(ValueError):
            extract_events(None)
        with patch("commander.diagnostics.game_events.MAX_EVENTS", 2):
            parsed = extract_events(LOG)
        self.assertEqual(len(parsed["events"]), 2)
        self.assertTrue(parsed["event_limit_reached"])
        self.assertFalse(
            summarize_events(parsed, "completed")["eligible_for_batch_aggregate"]
        )
        parsed = extract_events(("Land: " + "x" * 5000 + "\n") * 30)
        self.assertEqual(len(parsed["issue_samples"]), 20)
        self.assertEqual(parsed["unparsed_candidates"]["land"], 30)

    def test_multiple_attackers_and_multiline_defenders_keep_individual_card_names(
        self,
    ):
        parsed = extract_events(
            "\n".join(
                [
                    "Combat: Ai(1)-seat-1 assigned Ghalta, Primal Hunger (7), Creature B (8), and Creature C (9) to attack Ai(2)-seat-2.",
                    "Ai(1)-seat-1 assigned Creature D (10) to attack Ai(3)-seat-3.",
                ]
            )
        )
        self.assertEqual(parsed["unparsed_candidates"], {})
        self.assertEqual(
            [card["card"] for card in parsed["events"][0]["attackers"]],
            ["Ghalta, Primal Hunger", "Creature B", "Creature C"],
        )
        diagnostics = summarize_events(parsed, "completed")
        self.assertEqual(diagnostics["attack_assignment_matrix"][0], [0, 3, 1, 0])

    def test_targeted_casts_do_not_treat_targets_as_part_of_the_card_name(self):
        parsed = extract_events(
            "Add To Stack: Ai(1)-seat-1 cast Removal Spell targeting Creature (8)"
        )
        self.assertEqual(parsed["events"][0]["card"], "Removal Spell")
        self.assertEqual(parsed["events"][0]["targets_text"], "Creature (8)")
        malformed = extract_events(
            "Combat: Ai(1)-seat-1 assigned One (1) garbage Two (2) to attack Ai(2)-seat-2."
        )
        self.assertEqual(malformed["events"], [])
        self.assertEqual(malformed["unparsed_candidates"], {"attack": 1})


class ObservationTests(unittest.TestCase):
    """Describe land/cast/attack observations without claiming mana issues or motives."""

    def test_land_observation_denominator_and_casts(self):
        diagnostics = report()
        self.assertEqual(diagnostics["status"], "observed")
        first, second = diagnostics["seats"][:2]
        self.assertEqual(
            (first["land_plays"], first["casts"], first["complete_main_turns"]),
            (1, 1, 1),
        )
        self.assertEqual(second["turns_without_logged_land_play"], 1)
        self.assertEqual(
            second["no_land_turn_samples"], [{"turn_marker": 2, "line": 8}]
        )
        self.assertIn("mana_screw_or_flood", diagnostics["unsupported_metrics"])
        self.assertIn("human_targeting_motives", diagnostics["unsupported_metrics"])

    def test_attacks_are_counted_as_assignments_not_motives_or_damage(self):
        diagnostics = report()
        self.assertEqual(diagnostics["attack_assignment_matrix"][0][2], 1)
        self.assertEqual(diagnostics["seats"][2]["attacks_received"], 1)
        self.assertEqual(
            diagnostics["seats"][2]["incoming_attack_samples"][0]["line"], 6
        )

    def test_incomplete_and_skipped_main_turns_are_outside_denominator(self):
        incomplete = report(
            "Turn: Turn 1 (Ai(1)-seat-1)\nPhase: Ai(1)-seat-1's Main phase, precombat"
        )
        stats = incomplete["seats"][0]
        self.assertEqual(
            (stats["complete_main_turns"], stats["incomplete_turns"]), (0, 1)
        )
        skipped = report(
            "Turn: Turn 1 (Ai(1)-seat-1)\nPhase: Ai(1)-seat-1's Cleanup step"
        )
        self.assertEqual(skipped["seats"][0]["complete_main_turns"], 0)

    def test_extra_land_plays_and_repeated_cleanup_do_not_duplicate_turns(self):
        extra = (
            "Land: Ai(1)-seat-1 played Forest (2)\nPhase: Ai(1)-seat-1's Cleanup step\n"
        )
        diagnostics = report(LOG.replace("Turn: Turn 2", extra + "Turn: Turn 2"))
        stats = diagnostics["seats"][0]
        self.assertEqual((stats["complete_main_turns"], stats["land_plays"]), (1, 2))

    def test_off_turn_events_do_not_change_active_players_land_observation(self):
        text = LOG.replace(
            "Phase: Ai(2)-seat-2's Cleanup step",
            "\n".join(
                [
                    "Land: Ai(3)-seat-3 played Forest (9)",
                    "Add To Stack: Ai(3)-seat-3 cast Instant",
                    "Phase: Ai(2)-seat-2's Cleanup step",
                ]
            ),
        )
        diagnostics = report(text)
        self.assertEqual(diagnostics["seats"][1]["turns_without_logged_land_play"], 1)
        third = diagnostics["seats"][2]
        self.assertEqual(
            (
                third["land_plays"],
                third["off_turn_or_unscoped_land_plays"],
                third["casts"],
            ),
            (1, 1, 1),
        )

    def test_partial_failed_and_unrecognized_contexts_cannot_enter_aggregates(self):
        for diagnostics in (
            report(status="engine_timeout"),
            report(""),
            report(LOG + "\nTurn: Turn 1 (Ai(3)-seat-3)"),
            report(LOG + "\nPhase: Ai(4)-seat-4's Cleanup step"),
        ):
            self.assertFalse(diagnostics["eligible_for_batch_aggregate"])

    def test_evidence_samples_are_capped(self):
        turns = []
        for number in range(1, 25):
            turns.extend(
                [
                    f"Turn: Turn {number} (Ai(1)-seat-1)",
                    "Phase: Ai(1)-seat-1's Main phase, precombat",
                    "Phase: Ai(1)-seat-1's Cleanup step",
                ]
            )
        stats = report("\n".join(turns))["seats"][0]
        self.assertEqual(stats["turns_without_logged_land_play"], 24)
        self.assertEqual(len(stats["no_land_turn_samples"]), 10)


class DiagnosticArtifactTests(unittest.TestCase):
    """Preserve traceable files and keep diagnostic errors out of gameplay outcomes."""

    def setUp(self):
        stack = ExitStack()
        self.addCleanup(stack.close)
        self.run = Path(stack.enter_context(TemporaryDirectory()))
        (self.run / "forge.log").write_text(LOG, encoding="utf-8")

    def test_reports_and_event_files_preserve_log_line_references(self):
        diagnostics = record_diagnostics(self.run, LOG, "completed")
        saved = json.loads((self.run / "diagnostics.json").read_text(encoding="utf-8"))
        self.assertEqual(saved, diagnostics)
        events = [
            json.loads(line)
            for line in (self.run / "events.jsonl")
            .read_text(encoding="utf-8")
            .splitlines()
        ]
        self.assertEqual(events[0]["line"], 2)
        self.assertEqual(len(diagnostics["log_sha256"]), 64)
        with self.assertRaises(FileExistsError):
            record_diagnostics(self.run, LOG, "completed")

    def test_diagnostic_write_failure_preserves_the_real_outcome(self):
        with patch(
            "commander.engine.forge_engine.record_diagnostics",
            side_effect=OSError("fixture failure"),
        ):
            result = _read_game_output(
                self.run, {"status": "finished", "returncode": 0}
            )
        self.assertEqual((result["status"], result["winner_seat"]), ("completed", 1))
        self.assertEqual(result["diagnostics"]["status"], "unavailable")


class BatchDiagnosticTests(unittest.TestCase):
    """Keep missing data distinct from zero and undo each game's seat rotation."""

    def setUp(self):
        self.db = connect(":memory:")
        self.addCleanup(self.db.close)
        ensure_schema(self.db)
        self.job_id = "d" * 32
        raw, digest = encode_spec({"runs": 4})
        with self.db:
            self.db.execute(
                "INSERT INTO simulation_jobs(id,spec_json,spec_hash,state) VALUES (?,?,?,'queued')",
                (self.job_id, raw, digest),
            )

    def _attempt(self, index, result, state="completed"):
        with self.db:
            self.db.execute(
                "INSERT INTO simulation_attempts(job_id,game_index,state,result_json) VALUES (?,?,?,?)",
                (self.job_id, index, state, json.dumps(result)),
            )

    def test_rotated_seats_and_attack_matrix_map_to_original_pod(self):
        self._attempt(
            0,
            {
                "status": "completed",
                "seat_order": [2, 3, 0, 1],
                "diagnostics": report(),
            },
        )
        summary = batch_diagnostics(self.db, self.job_id)
        decks = summary.get("decks") or []
        matrix = summary.get("attack_assignment_matrix") or []
        self.assertEqual(len(decks), 4)
        self.assertEqual(len(matrix), 4)
        self.assertEqual(decks[2]["land_plays"], 1)
        self.assertEqual(decks[3]["turns_without_logged_land_play_fraction"], 1.0)
        self.assertEqual(matrix[2][0], 1)
        self.assertEqual(decks[0]["attacks_received"], 1)

    def test_legacy_failed_and_partial_reports_are_excluded_not_zero(self):
        self._attempt(0, {"status": "completed"})
        self._attempt(
            1, {"status": "engine_timeout", "diagnostics": report()}, "failed"
        )
        self._attempt(
            2, {"status": "completed", "diagnostics": report(status="engine_error")}
        )
        self._attempt(3, {"status": "completed", "diagnostics": report()}, "failed")
        summary = batch_diagnostics(self.db, self.job_id)
        self.assertEqual(
            summary["coverage"],
            {"attempts_seen": 4, "games_included": 0, "games_excluded": 4},
        )
        self.assertIsNone(summary["decks"])
        self.assertIsNone(summary["attack_assignment_matrix"])

    def test_invalid_seat_mapping_is_rejected(self):
        self._attempt(
            0,
            {
                "status": "completed",
                "seat_order": [0, 0, 1, 2],
                "diagnostics": report(),
            },
        )
        with self.assertRaises(ValueError):
            batch_diagnostics(self.db, self.job_id)

    def test_mixed_coverage_reports_only_covered_game_totals(self):
        self._attempt(
            0,
            {
                "status": "completed",
                "seat_order": [0, 1, 2, 3],
                "diagnostics": report(),
            },
        )
        self._attempt(1, {"status": "completed"})
        summary = batch_diagnostics(self.db, self.job_id)
        self.assertEqual(summary["status"], "partial")
        self.assertEqual(summary["coverage"]["games_included"], 1)
        self.assertEqual(summary["coverage"]["games_excluded"], 1)
