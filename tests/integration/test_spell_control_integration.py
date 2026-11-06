"""Opt-in spell theft with native selection, payment and retargeted damage."""

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
    "Requires reviewed spell-control runtime",
)
class SpellControlIntegrationTests(unittest.TestCase):
    """Check paid gameplay rather than treating API dispatch as rules coverage."""

    def assert_case(self, row):
        """Validate one fixed source/mode/cost witness."""
        self.assertTrue(row["world_unchanged"])
        self.assertTrue(row["rng_restored"])
        self.assertEqual(row["before"], row["after_choice"])
        selected = row["selected"]
        self.assertIsNotNone(selected)
        self.assertEqual(selected["name"], row["source"])
        self.assertEqual(selected["api"], "ControlSpell")
        self.assertEqual(selected["host_id"], row["host_id"])
        self.assertEqual(selected["actor"], 0)
        self.assertEqual(selected["spells"], [row["incoming_id"]])
        self.assertTrue(selected["in_world"])
        self.assertTrue(row["paid_successfully"])
        self.assertEqual(
            row["paid_mana"],
            (
                0
                if row["alternate"]
                else {
                    "Aethersnatch": 6,
                    "Commandeer": 7,
                    "Chef's Kiss": 3,
                }[row["source"]]
            ),
        )
        self.assertEqual(
            row["fodder_zones"], ["Exile", "Exile"] if row["alternate"] else []
        )
        spells = 2 if row["source"] == "Chef's Kiss" else 1
        self.assertEqual(len(row["controlled_stack"]), spells)
        for action in row["controlled_stack"]:
            self.assertEqual(action["actor"], 0)
            self.assertTrue(action["in_world"])
            self.assertEqual(len(action["players"]), 1)
            self.assertNotEqual(action["players"], [0])
        self.assertEqual(row["life"][0], 2)
        self.assertEqual(sum(row["life"]), 122 - spells * 2)
        self.assertFalse(row["actor_lost"])

    def test_spell_control_choices_and_paid_outcomes(self):
        """Exercise three sources in three modes and three alternate-cost choices."""
        settings = read_settings()
        run = create_run()
        worker = PROJECT_ROOT / "tests/support/spell_control_worker.py"
        process, _ = run_test_worker(settings, run, worker, "spell-control.log")
        self.assertEqual(process["returncode"], 0, process)
        manifest = read_bounded_json(run / "spell-control.json")
        self.assertEqual(manifest["engine"], settings.verify())
        self.assertEqual(
            manifest["default_profile_sha256"],
            file_hash(settings.home / "res/ai/Default.ai"),
        )
        self.assertEqual(len(manifest["parts"]), 3)
        for mode, part in zip(("heuristic", "hybrid", "full"), manifest["parts"]):
            self.assertEqual(part["name"], "spell-control-" + mode + ".json")
            path = run / part["name"]
            self.assertEqual(part["sha256"], file_hash(path))
            rows = read_bounded_json(path)["cases"]
            self.assertEqual(part["cases"], 4)
            self.assertEqual(
                [(row["source"], row["mode"], row["alternate"]) for row in rows],
                [
                    ("Aethersnatch", mode, False),
                    ("Commandeer", mode, False),
                    ("Chef's Kiss", mode, False),
                    ("Commandeer", mode, True),
                ],
            )
            for row in rows:
                with self.subTest(
                    source=row["source"], mode=mode, alternate=row["alternate"]
                ):
                    self.assert_case(row)
        print(json.dumps({"spell_control_run": run.name}), flush=True)
