"""Opt-in paid controlled-attack decisions, native costs and combat outcomes."""

import json
import os
import unittest

import pytest

from commander.engine.forge_config import file_hash, read_settings
from commander.engine.interactions.suite import read_bounded_json
from commander.engine.native_runtime import create_run
from commander.paths import PROJECT_ROOT
from tests.support.native_worker import run_test_worker

pytestmark = pytest.mark.integration


@unittest.skipUnless(
    os.environ.get("COMMANDER_ENGINE_INTEGRATION") == "1",
    "Requires reviewed controlled-attack runtime",
)
class AttackIntegrationTests(unittest.TestCase):
    """Separate legal declarations, original-state independence, payment and damage."""

    def _assert_world(self, state):
        participants = state["players"]
        self.assertEqual([row["seat"] for row in participants], list(range(4)))
        self.assertTrue(all(row["in_world"] for row in participants))
        self.assertEqual(
            [row["view_master"] for row in participants],
            [row["master"] for row in participants],
        )
        for card in state["cards"]:
            self.assertEqual(card["in_world"], True if card["present"] else None)
        self.assertEqual(state["stack"], [])
        self.assertEqual(state["stack_cards"], 0)

    def _assert_declaration(self, row):
        kind = row["kind"]
        attacks = row["selected"]
        self.assertTrue(row["legal"])
        self.assertTrue(row["rng_restored"])
        self.assertEqual(
            row["timeout_fallback_legal"], True if kind == "timeout" else None
        )
        self.assertEqual(
            attacks, row["declared"], "Native payment must preserve this declaration"
        )
        self.assertTrue(all(attack["in_world"] for attack in attacks))
        self.assertTrue(all(attack["defender_card"] is None for attack in attacks))
        self.assertTrue(
            all(attack["attacker"] in row["attackers"] for attack in attacks)
        )
        defenders = [attack["defender_player"] for attack in attacks]
        if kind in {"ordinary", "self", "only_master"}:
            self.assertEqual(defenders, [0])
        else:
            self.assertNotIn(row["beneficiary"], defenders)
        if kind == "optional_tax":
            self.assertEqual(
                attacks, [], "A requirement cannot force an optional payment"
            )
        if kind in {"forced", "blocked", "paid_tax"}:
            self.assertEqual(len(attacks), 1)
        if kind == "cap":
            self.assertLessEqual(len(attacks), 1)
        if kind == "group":
            self.assertEqual(len(attacks), 2, "Neither Flunkies can attack alone")
        if kind in {"blocked", "paid_tax"}:
            self.assertEqual(defenders, [2])

    def _assert_costs(self, row):
        expected_tax = 2 if row["kind"] == "paid_tax" else 0
        for seat, (before, paid) in enumerate(
            zip(row["before"]["players"], row["after_declaration"]["players"])
        ):
            self.assertEqual(before["life"], paid["life"])
            self.assertEqual(
                paid["tapped_lands"],
                before["tapped_lands"] + (expected_tax if seat == 1 else 0),
                "Only the acting player's mana can pay for its attack",
            )
        attacking = {attack["attacker"] for attack in row["selected"]}
        for card in row["after_declaration"]["cards"]:
            if card["id"] in row["attackers"]:
                self.assertEqual(card["tapped"], card["id"] in attacking)
                self.assertEqual(card["controller"], 1)
                self.assertEqual(card["zone"], "Battlefield")

    def _assert_outcome(self, row):
        kind = row["kind"]
        expected_lives = [
            participant["life"] for participant in row["before"]["players"]
        ]
        if kind == "blocked":
            expected_lives[2] += 5
            attacker = next(
                card
                for card in row["after"]["cards"]
                if card["id"] == row["attackers"][0]
            )
            self.assertEqual(
                attacker["zone"], "Graveyard", "Native first strike kills the Dragon"
            )
        else:
            for attack in row["selected"]:
                expected_lives[attack["defender_player"]] -= 3 if kind == "group" else 5
        self.assertEqual(
            [participant["life"] for participant in row["after"]["players"]],
            expected_lives,
        )
        self.assertEqual(
            [participant["active"] for participant in row["after"]["players"]],
            [life > 0 for life in expected_lives],
        )
        if kind != "only_master":
            self.assertTrue(row["after"]["players"][row["beneficiary"]]["active"])
        if kind == "combat_only":
            self.assertIsNone(row["after"]["players"][1]["master"])

    def _assert_case(self, row):
        kind = row["kind"]
        controlled = kind not in {"ordinary", "self"}
        self.assertEqual(
            row["setup_paid"], [4 if kind == "combat_only" else 8] if controlled else []
        )
        self.assertEqual(
            row["before"]["players"][1]["master"],
            0 if controlled else 1 if kind == "self" else None,
        )
        if kind == "chain":
            self.assertEqual(row["beneficiary"], 2)
            self.assertEqual(row["before"]["players"][0]["master"], 2)
        self.assertEqual(
            row["before"],
            row["after_choice"],
            "Planning must not pay, tap or resolve original actions",
        )
        for label in ("before", "after_choice", "after_declaration", "after"):
            self._assert_world(row[label])
        self._assert_declaration(row)
        self._assert_costs(row)
        self._assert_outcome(row)

    def _read_cases(self, run, manifest):
        parts = manifest["parts"]
        self.assertEqual(len(parts), 2)
        cases = []
        kinds = "ordinary controlled forced only_master optional_tax cap chain self blocked paid_tax group combat_only timeout".split()
        for mode, part in zip(("heuristic", "full"), parts):
            name = "attack-" + mode + ".json"
            self.assertEqual(part["name"], name)
            path = run / name
            self.assertEqual(file_hash(path), part["sha256"])
            receipt = read_bounded_json(path)
            expected = [kind + "_" + mode for kind in kinds]
            self.assertEqual([row["case"] for row in receipt["cases"]], expected)
            self.assertEqual(part["cases"], 13)
            cases.extend(receipt["cases"])
        return cases

    def test_paid_native_combat_outcomes(self):
        """Retain twenty-six choices and native payment/damage outcomes in bounded parts."""
        settings = read_settings()
        run = create_run()
        worker = PROJECT_ROOT / "tests/support/attack_worker.py"
        process, _ = run_test_worker(settings, run, worker, "attack.log")
        self.assertEqual(process["returncode"], 0, process)
        manifest = read_bounded_json(run / "attack.json")
        self.assertEqual(manifest["engine"], settings.verify())
        self.assertEqual(
            manifest["default_profile_sha256"],
            file_hash(settings.home / "res/ai/Default.ai"),
        )
        for observation in self._read_cases(run, manifest):
            with self.subTest(scenario=observation["case"]):
                self._assert_case(observation)
        print(json.dumps({"controlled_attack_run": run.name, "cases": 26}))
