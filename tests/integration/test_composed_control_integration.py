"""Opt-in composed spell control with native costs, outcomes and cast origin."""

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
    "Requires reviewed composed-control runtime",
)
class ComposedControlIntegrationTests(unittest.TestCase):
    """Verify real choices and optional decisions without forcing their outcomes."""

    def assert_case(self, row):
        """Check one fixed cost, branch, ownership and provenance witness."""
        kind = row["kind"]
        if kind == "chimera":
            self.assertEqual(row["decision_path"], "native_optional_trigger")
            self.assertEqual(row["offered_trigger"]["api"], "ControlSpell")
            self.assertFalse(row["offered_trigger"]["is_spell"])
            self.assertEqual(row["setup_paid"], 5)
            self.assertEqual(row["paid_mana"], 0)
            self.assertEqual(row["host_owner"], 0)
            self.assertEqual(row["host_controller"], 1)
        else:
            self.assertTrue(row["world_unchanged"])
            self.assertTrue(row["rng_restored"])
            self.assertEqual(row["before"], row["after_choice"])
            action = row["selected"]
            self.assertIsNotNone(action)
            self.assertEqual(action["name"], row["source"])
            self.assertEqual(action["host_id"], row["host_id"])
            self.assertEqual(action["actor"], 0)
            self.assertEqual(action["spells"], [row["threat_id"]])
            self.assertEqual(
                action["api"], "FlipCoin" if kind == "polarity" else "Pump"
            )
            self.assertTrue(action["in_world"])
            self.assertEqual(row["paid_mana"], 3 if kind == "polarity" else 4)
            if kind == "substitution":
                self.assertEqual(row["sub_target_ids"], [row["offered_id"]])
                self.assertEqual(row["offered_owner"], 0)
                self.assertEqual(row["offered_controller"], 1)
        countered = kind == "polarity" and row["physical_seed"] == 4096
        self.assertEqual(len(row["controlled_stack"]), 0 if countered else 1)
        for action in row["controlled_stack"]:
            self.assertEqual(action["actor"], 0)
            self.assertEqual(action["host_id"], row["threat_id"])
            self.assertNotEqual(action["players"], [0])
            self.assertTrue(action["in_world"])
        self.assertEqual(row["cast_counts"], [1, 1, 0, 0])
        self.assertEqual(len(row["history"]), 2)
        threat = next(
            action for action in row["history"] if action["host_id"] == row["threat_id"]
        )
        self.assertEqual(threat["actor"], 1)
        self.assertEqual(threat["players"], [0])
        self.assertEqual(row["life"][0], 2)
        creature_target = kind == "substitution"
        self.assertEqual(sum(row["life"]), 122 if countered or creature_target else 120)
        self.assertEqual(row["damage_to_creatures"], 2 if creature_target else 0)
        if creature_target:
            self.assertEqual(row["controlled_stack"][0]["cards"], [row["offered_id"]])
            self.assertEqual(row["offered_final_zone"], "Graveyard")
        self.assertFalse(row["actor_lost"])

    def assert_provenance(self, row):
        """A controller who did not cast Zacama must not receive the land untap."""
        stolen = row["kind"] == "zacama_stolen"
        self.assertEqual(row["paid_source"], 9)
        self.assertEqual(row["paid_response"], 6 if stolen else 0)
        self.assertEqual(row["owner"], 1)
        self.assertEqual(row["controller"], 0 if stolen else 1)
        self.assertEqual(row["actor_tapped"], 6 if stolen else 0)
        self.assertEqual(row["caster_tapped"], 9 if stolen else 0)
        self.assertEqual(row["cast_caster"], 1)
        self.assertEqual(row["cast_counts"], [1, 1, 0, 0] if stolen else [0, 1, 0, 0])
        self.assertEqual(row["life"], [40, 40, 40, 40])

    def checked_part(self, run, part, name, count):
        """Validate a fixed receipt name, contents and digest before using its rows."""
        self.assertEqual(part["name"], name)
        path = run / name
        rows = read_bounded_json(path)["cases"]
        self.assertEqual(
            (part["sha256"], part["cases"], len(rows)),
            (file_hash(path), count, count),
        )
        return rows

    def test_composed_choices_and_cast_provenance(self):
        """Exercise two coin branches and both exchange sources in each AI mode."""
        settings = read_settings()
        run = create_run()
        worker = PROJECT_ROOT / "tests/support/composed_control_worker.py"
        process, _ = run_test_worker(settings, run, worker, "composed-control.log")
        self.assertEqual(process["returncode"], 0, process)
        manifest = read_bounded_json(run / "composed-control.json")
        self.assertEqual(
            (manifest["engine"], manifest["default_profile_sha256"]),
            (settings.verify(), file_hash(settings.home / "res/ai/Default.ai")),
        )
        self.assertEqual(len(manifest["parts"]), 4)
        for mode, part in zip(("heuristic", "hybrid", "full"), manifest["parts"]):
            rows = self.checked_part(run, part, "composed-control-" + mode + ".json", 4)
            self.assertEqual(
                [(row["kind"], row["mode"], row["physical_seed"]) for row in rows],
                [
                    ("polarity", mode, 0),
                    ("polarity", mode, 4096),
                    ("substitution", mode, None),
                    ("chimera", mode, None),
                ],
            )
            for row in rows:
                with self.subTest(
                    kind=row["kind"], mode=mode, seed=row["physical_seed"]
                ):
                    self.assert_case(row)
        rows = self.checked_part(
            run, manifest["parts"][3], "composed-control-provenance.json", 2
        )
        self.assertEqual(
            [row["kind"] for row in rows], ["zacama_ordinary", "zacama_stolen"]
        )
        for row in rows:
            with self.subTest(kind=row["kind"]):
                self.assert_provenance(row)
        print(json.dumps({"composed_control_run": run.name}), flush=True)
