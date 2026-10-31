"""Opt-in temporary-copy deadlines and token-keyword expiration checks."""

import os
import unittest

import pytest

from commander.engine.forge_config import read_settings
from commander.engine.interactions.suite import read_bounded_json
from commander.engine.native_runtime import create_run
from commander.paths import PROJECT_ROOT
from tests.support.native_worker import run_test_worker

pytestmark = pytest.mark.integration


@unittest.skipUnless(
    os.environ.get("COMMANDER_ENGINE_INTEGRATION") == "1",
    "Requires reviewed copy-frame expiration runtime",
)
class CloneDurationIntegrationTests(unittest.TestCase):
    """Resolve real copy effects, then expire only the copied world's frames."""

    def _assert_artifact_restored(self, row, name):
        self.assertEqual(row["source"], row["before"])
        self.assertEqual(row["after"]["name"], name)
        self.assertTrue(row["after"]["artifact"])
        self.assertFalse(row["after"]["creature"])
        self.assertFalse(row["after"]["land"])

    def _assert_tokens(self, row):
        self.assertEqual(row["paid"], 4)
        self.assertEqual(row["before"], row["source"])
        self.assertEqual(len(row["after"]), 5)
        self.assertEqual(
            [t["id"] for t in row["before"]], [t["id"] for t in row["after"]]
        )
        self.assertEqual(sum(t["owner"] == 0 for t in row["after"]), 2)
        self.assertEqual(sorted(t["owner"] for t in row["after"]), [0, 0, 1, 2, 3])
        for token in row["before"]:
            self.assertEqual(token["haste"], token["owner"] == 0)
            self.assertEqual(token["menace"], token["owner"] == 0)
        for token in row["after"]:
            self.assertFalse(token["haste"])
            self.assertFalse(token["menace"])
            self.assertTrue(token["vigilance"])
            self.assertEqual(token["power"], 2)

    def _assert_copy_deadline(self, row):
        if row["case"] == "mirrorweave":
            self.assertEqual(row["paid"], 4)
            self.assertEqual(
                [c["name"] for c in row["after"]], ["Grizzly Bears", "Runeclaw Bear"]
            )
            self.assertEqual([c["name"] for c in row["source"]], ["Serra Angel"] * 2)
            self.assertEqual(row["target"]["name"], "Serra Angel")
        elif row["case"] == "shapesharer":
            self.assertEqual(row["paid"], [2, 3])
            self.assertEqual(
                [c["name"] for c in row["turns"]], ["Serra Angel"] * 3 + ["Shapesharer"]
            )
            self.assertEqual(row["turns"][-1]["power"], 1)
            self.assertFalse(row["turns"][-1]["flying"])
            self.assertEqual(row["source"]["name"], "Serra Angel")
        else:
            self.assertEqual(row["paid"], 5)
            self.assertTrue(row["face_down"])
            self.assertEqual(row["after_frames"], 0)
            self.assertEqual(row["after"]["power"], 2)
            self.assertEqual(row["source_frames"], 1)
            self.assertEqual(row["source"]["name"], "Serra Angel")

    def _assert_special_mirror(self, row):
        if row["case"] == "overlap":
            self.assertEqual(row["paid"], [3, 2, 2])
            self.assertEqual(row["before_frames"], 2)
            self.assertEqual(row["after_frames"], 0)
            self.assertEqual(row["source_frames"], 2)
            self.assertEqual(row["after"]["name"], "Mirage Mirror")
            self.assertEqual(row["source"]["name"], "Serra Angel")
        elif row["case"] == "blink":
            self.assertEqual(row["paid"], [3, 2, 1])
            self.assertTrue(row["retained"])
            self.assertTrue(row["source_retained"])
            self.assertEqual(row["after"]["name"], "Mirage Mirror")
            self.assertEqual(row["source"]["name"], "Mirage Mirror")
        else:
            self.assertEqual(row["paid"], 3)
            self._assert_artifact_restored(row, "Cursed Mirror")
            self.assertTrue(row["before"]["haste"])
            self.assertFalse(row["after"]["haste"])

    def test_paid_temporary_copy_and_keyword_deadlines(self):
        """Observe copied outcomes without replaying casts or mutating original frames."""
        settings = read_settings()
        expected = settings.verify()
        self.assertIn("copy_frame_expiration", expected["capabilities"])
        run = create_run()
        process, _ = run_test_worker(
            settings, run, PROJECT_ROOT / "tests/support/clone_worker.py", "clone.log"
        )
        self.assertEqual(
            (process["status"], process["returncode"]), ("finished", 0), str(run)
        )
        receipt = read_bounded_json(run / "clone-state.json")
        self.assertEqual(receipt["engine"], expected)
        self.assertEqual(
            [
                (r["case"], r.get("target") if r["case"] == "mirage" else None)
                for r in receipt["cases"]
            ],
            [
                ("mirage", name)
                for name in ("Serra Angel", "Forest", "Sol Ring", "Glorious Anthem")
            ]
            + [
                (case, None)
                for case in (
                    "mirrorweave",
                    "shapesharer",
                    "vesuvan",
                    "eiganjo",
                    "overlap",
                    "blink",
                    "cursed",
                )
            ],
        )
        for row in receipt["cases"]:
            with self.subTest(case=row["case"], target=row.get("target")):
                self.assertEqual(row["unmapped"], [])
                if row["case"] == "mirage":
                    self.assertEqual(row["paid"], [3, 2])
                    self.assertEqual(row["before"]["name"], row["target"])
                    self._assert_artifact_restored(row, "Mirage Mirror")
                elif row["case"] == "eiganjo":
                    self._assert_tokens(row)
                elif row["case"] in {"mirrorweave", "shapesharer", "vesuvan"}:
                    self._assert_copy_deadline(row)
                else:
                    self._assert_special_mirror(row)
