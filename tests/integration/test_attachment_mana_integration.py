"""Opt-in paid Reconfigure and mana-granted keyword cleanup checks."""

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
    "Requires reviewed attachment/mana runtime",
)
class AttachmentManaIntegrationTests(unittest.TestCase):
    """Require actual payment, independent deadlines and effect-card cleanup."""

    def _assert_attachment(self, row):
        mode = row["case"].split(":")[1]
        before = row["before"]
        self.assertFalse(before["creature"])
        self.assertIsNotNone(before["attached_to"])
        self.assertEqual(before["zone"], "Battlefield")
        self.assertEqual(before["effects"], 1)
        self.assertEqual(row["copied_before"], before)
        self.assertEqual(row["source"], before)
        expected_cost = {"detach": 2, "bearer": 1, "source": 2, "reattach": 4}[mode]
        self.assertEqual(row["paid"], [2, 2, expected_cost, 2])
        expected = {
            "creature": True,
            "attached_to": None,
            "zone": "Hand" if mode == "source" else "Battlefield",
            "effects": 0,
        }
        self.assertEqual(row["after"], expected)
        self.assertEqual(row["source_after"], {**expected, "zone": "Battlefield"})
        if mode == "reattach":
            self.assertEqual(
                row["transferred"], {**before, "attached_to": row["second"]}
            )
        else:
            self.assertIsNone(row["transferred"])

    def _assert_mana(self, row):
        name = row["case"]
        permanent = name == "Hall of the Bandit Lord"
        self.assertEqual(
            row["paid"],
            [
                {"Carnelian Orb of Dragonkind": 3, "Generator Servant": 2}.get(name, 0),
                4,
            ],
        )
        self.assertIn(row["source_id"], row["mana_sources"])
        self.assertTrue(row["before"])
        self.assertTrue(row["copied_before"])
        self.assertTrue(row["source"])
        self.assertEqual(row["after"], permanent)
        self.assertEqual(row["source_after"], permanent)
        self.assertTrue(row["source_tapped"])
        self.assertEqual(
            row["source_zone"],
            "Graveyard" if name == "Generator Servant" else "Battlefield",
        )
        self.assertEqual(row["exerted"], 1 if name == "Arena of Glory" else 0)
        self.assertEqual(row["life"], 37 if permanent else 40)

    def _assert_case(self, row):
        if ":" in row["case"]:
            self._assert_attachment(row)
        else:
            self._assert_mana(row)
        self.assertEqual(row["unmapped"], [])

    def test_attachment_and_mana_cleanup(self):
        """Resolve native actions independently in copied and original games."""
        settings = read_settings()
        run = create_run()
        process, _ = run_test_worker(
            settings,
            run,
            PROJECT_ROOT / "tests/support/attachment_mana_worker.py",
            "attachment-mana.log",
        )
        self.assertEqual(process["returncode"], 0, process)
        receipt = read_bounded_json(run / "attachment-mana.json")
        self.assertEqual(receipt["engine"], settings.verify())
        expected = [
            name + ":" + mode
            for name in ("Lion Sash", "Lizard Blades")
            for mode in ("detach", "bearer", "source", "reattach")
        ]
        expected.extend(
            (
                "Carnelian Orb of Dragonkind",
                "Generator Servant",
                "Arena of Glory",
                "Hall of the Bandit Lord",
            )
        )
        self.assertEqual([row["case"] for row in receipt["cases"]], expected)
        for row in receipt["cases"]:
            with self.subTest(case=row["case"]):
                self._assert_case(row)
