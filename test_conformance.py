"""Mutation checks for limited rules witnesses; a smoke-game success is insufficient."""

import copy
import unittest
from unittest.mock import patch

from conformance_checks import check_rules_fixture
from engine_fixtures import FOREST_POD

# Behavior is expressed in test names.
# pylint: disable=missing-function-docstring


def fixture_inputs() -> dict:
    """Synthetic input metadata for offline verifier tests, not an engine result."""
    return {
        "seats": [
            {
                "revision": {
                    "cards": [
                        {
                            "zone": "commander",
                            "quantity": 1,
                            "card": {"name": name},
                            "oracle": {
                                "mana_cost": "{2}{G}{G}",
                                "power": "4",
                                "oracle_text": "gets +1/+1",
                            },
                        },
                        {
                            "zone": "mainboard",
                            "quantity": 99,
                            "card": {"name": "Forest"},
                            "oracle": {},
                        },
                    ]
                }
            }
            for name in FOREST_POD
        ]
    }


def fixture_log() -> str:
    """Synthetic witnesses exercise the checker; live tests verify actual Forge behavior."""
    lines = [f"Land: Ai(4)-seat-4 played Forest ({index})" for index in range(1, 7)]
    for count in (4, 6):
        lines.extend(
            f"Mana: Forest ({index}) - {{T}}: Add {{G}}."
            for index in range(1, count + 1)
        )
        lines.append("Add To Stack: Ai(4)-seat-4 cast Ruxa, Patient Professor")
    lines.extend(
        [
            "Add To Stack: Ai(2)-seat-2 triggered Goreclaw, Terror of Qal Sisma",
            "Resolve Stack: Whenever Goreclaw, Terror of Qal Sisma attacks, each creature gets +1/+1",
            "Combat: Ai(1)-seat-1 didn't block Goreclaw, Terror of Qal Sisma (201).",
            "Damage: Goreclaw, Terror of Qal Sisma (201) deals 5 combat damage to Ai(1)-seat-1.",
            "Damage: Ghalta, Primal Hunger (100) deals 12 combat damage to Ai(2)-seat-2.",
            "Life: Life: Ai(2)-seat-2 40 > 28",
            "Damage: Ghalta, Primal Hunger (100) deals 9 combat damage to Ai(2)-seat-2.",
            "Life: Life: Ai(2)-seat-2 28 > 19",
            "Game Outcome: Ai(2)-seat-2 has lost due to accumulation of 21 damage from generals",
            "Game Outcome: Ai(1)-seat-1 has won because all opponents have lost",
            "Game Result: Game 1 ended in 100 ms. Ai(1)-seat-1 has won!",
        ]
    )
    return "\n".join(lines)


class ConformanceWitnessTests(unittest.TestCase):
    """Counterexamples must fail and absent exercise must remain inconclusive."""

    def _check(self, text=None, inputs=None, status="completed"):
        return check_rules_fixture(
            fixture_log() if text is None else text,
            fixture_inputs() if inputs is None else inputs,
            {"status": status, "process": {"status": "finished", "returncode": 0}},
        )

    def test_all_required_witnesses_are_present(self):
        result = self._check()
        self.assertEqual(result["status"], "passed")
        self.assertEqual([case["status"] for case in result["checks"]], ["passed"] * 3)
        self.assertEqual(result["checks"][0]["evidence"][1]["expected_cost"], 6)

    def test_recast_without_additional_payment_fails(self):
        text = fixture_log().replace("Mana: Forest (6) - {T}: Add {G}.\n", "")
        self.assertEqual(self._check(text)["status"], "failed")

    def test_unattributable_payment_is_not_a_rules_pass(self):
        text = fixture_log().replace(
            "Land: Ai(4)-seat-4 played Forest (6)",
            "Land: Ai(3)-seat-3 played Forest (6)",
        )
        self.assertEqual(self._check(text)["status"], "inconclusive")

    def test_missing_recast_is_not_exercised(self):
        marker = "Add To Stack: Ai(4)-seat-4 cast Ruxa, Patient Professor"
        before, after = fixture_log().rsplit(marker, 1)
        text = before + "Ignored" + after
        self.assertEqual(self._check(text)["status"], "inconclusive")

    def test_unresolved_or_blocked_combat_is_not_evidence_of_a_failed_trigger(self):
        for original in (
            "Resolve Stack: Whenever Goreclaw, Terror of Qal Sisma attacks, each creature gets +1/+1",
            "Combat: Ai(1)-seat-1 didn't block Goreclaw, Terror of Qal Sisma (201).",
        ):
            with self.subTest(original=original):
                self.assertEqual(
                    self._check(fixture_log().replace(original, "Ignored"))["status"],
                    "inconclusive",
                )

    def test_wrong_unblocked_trigger_damage_fails(self):
        text = fixture_log().replace("deals 5 combat damage", "deals 4 combat damage")
        self.assertEqual(self._check(text)["status"], "failed")

    def test_premature_commander_loss_fails(self):
        text = fixture_log().replace("deals 9 combat damage", "deals 8 combat damage")
        self.assertEqual(self._check(text)["status"], "failed")

    def test_damage_from_different_commanders_cannot_be_pooled(self):
        text = fixture_log().replace(
            "Ghalta, Primal Hunger (100) deals 9",
            "Surrak, the Hunt Caller (300) deals 9",
        )
        self.assertEqual(self._check(text)["status"], "failed")

    def test_life_total_loss_does_not_prove_positive_life_commander_loss(self):
        text = fixture_log().replace("28 > 19", "28 > 0")
        self.assertEqual(self._check(text)["status"], "inconclusive")

    def test_changed_fixture_or_incomplete_game_is_inconclusive(self):
        changed = copy.deepcopy(fixture_inputs())
        changed["seats"][0]["revision"]["cards"][1]["quantity"] = 98
        self.assertEqual(self._check(inputs=changed)["status"], "inconclusive")
        self.assertEqual(self._check(status="engine_timeout")["status"], "inconclusive")
        self.assertEqual(self._check("No result")["status"], "inconclusive")

    def test_bounded_log_input(self):
        with patch("conformance_checks.MAX_LOG_BYTES", 100):
            with self.assertRaises(ValueError):
                self._check()
        with self.assertRaises(ValueError):
            self._check("\n" * 100001)
