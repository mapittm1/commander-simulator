"""Reject native printing fallbacks, changed resources and unstable batch evidence."""

import copy
import hashlib
import json
import tempfile
import unittest
from contextlib import ExitStack
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
from uuid import NAMESPACE_URL, uuid5

from commander.engine.forge_config import require_engine_capabilities
from commander.engine.forge_engine import game_evidence, run_game
from commander.engine.printing_admission import (
    EVIDENCE_FIELDS,
    check_printing_choices,
    validate_receipt,
    verify_printing_evidence,
)
from commander.engine.printing_resources import edition_fingerprint

# Test names carry the behavior specification.
# pylint: disable=missing-function-docstring


def _choice():
    return {
        "id": str(uuid5(NAMESPACE_URL, "printing")),
        "oracle_id": str(uuid5(NAMESPACE_URL, "oracle")),
        "name": "Attraction",
        "set": "unf",
        "collector_number": "200a",
        "lang": "en",
        "attraction_lights": [2, 6],
    }


class PrintingAdmissionTests(unittest.TestCase):
    """A successful native process must still prove every requested functional printing."""

    def setUp(self):
        choice = _choice()
        self.plan = {
            "engine": {
                "jar_sha256": "a" * 64,
                "capabilities": ["supplementary_ai_zones"],
            },
            "editions": {"files": 1, "sha256": "b" * 64},
            "printings": [{**choice, "engine_name": "Attraction"}],
        }
        self.receipt = {
            "schema_version": 1,
            "plan_sha256": "c" * 64,
            "engine": self.plan["engine"],
            "editions": self.plan["editions"],
            "java_version": "17.0.1",
            "printings": [
                {
                    "id": choice["id"],
                    "name": "Attraction",
                    "set": "unf",
                    "collector_number": "200a",
                    "attraction_lights": [2, 6],
                    "is_attraction": True,
                }
            ],
        }

    def test_matching_collector_identity_and_lights_are_required(self):
        validate_receipt(self.receipt, self.plan, "c" * 64)
        for field, value in (
            ("collector_number", "200b"),
            ("name", "Other"),
            ("set", "other"),
            ("attraction_lights", [3, 6]),
            ("is_attraction", 1),
        ):
            receipt = copy.deepcopy(self.receipt)
            receipt["printings"][0][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                validate_receipt(receipt, self.plan, "c" * 64)

    def test_missing_extra_or_reordered_rows_and_provenance_cannot_pass(self):
        for field, value in (
            ("schema_version", True),
            ("plan_sha256", "changed"),
            ("editions", {}),
            ("engine", {}),
            ("java_version", ""),
            ("printings", []),
            ("printings", self.receipt["printings"] * 2),
        ):
            receipt = {**self.receipt, field: value}
            with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                validate_receipt(receipt, self.plan, "c" * 64)

    def test_boolean_lights_cannot_masquerade_as_integers(self):
        plan, receipt = copy.deepcopy(self.plan), copy.deepcopy(self.receipt)
        plan["printings"][0]["attraction_lights"] = [1, 6]
        receipt["printings"][0]["attraction_lights"] = [True, 6]
        with self.assertRaises(ValueError):
            validate_receipt(receipt, plan, "c" * 64)

    def test_no_selection_requires_no_native_bridge(self):
        with patch("commander.engine.printing_admission.require_bridge") as bridge:
            self.assertIsNone(check_printing_choices(None, [{"cards": []}], {}))
        bridge.assert_not_called()

    def test_cache_inputs_bind_resources_and_deduplicate_seats(self):
        choice = _choice()
        revision = {
            "cards": [
                {
                    "card": {"card_id": choice["oracle_id"], "name": choice["name"]},
                    "zone": "attractions",
                    "printing": choice,
                }
            ]
        }
        identity = {
            "home": "engine",
            "java": "java",
            "lock": None,
            "engine": self.plan["engine"],
        }
        with ExitStack() as resources:
            for name in ("require_bridge", "source_hashes"):
                resources.enter_context(
                    patch(
                        "commander.engine.printing_admission." + name, return_value={}
                    )
                )
            resources.enter_context(
                patch(
                    "commander.engine.printing_admission.native_plan_identity",
                    return_value=identity,
                )
            )
            editions = resources.enter_context(
                patch(
                    "commander.engine.printing_admission.edition_fingerprint",
                    side_effect=[
                        self.plan["editions"],
                        {"files": 2, "sha256": "d" * 64},
                    ],
                )
            )
            worker = resources.enter_context(
                patch(
                    "commander.engine.printing_admission._run_printing_check",
                    return_value='{"verified": true}',
                )
            )
            settings = SimpleNamespace(home=Path("engine"))
            result = check_printing_choices(
                settings, [revision] * 4, {"Attraction": "Attraction"}
            )
            result["verified"] = False
            self.assertTrue(
                check_printing_choices(
                    settings, [revision], {"Attraction": "Attraction"}
                )["verified"]
            )
        first, second = [json.loads(call.args[0]) for call in worker.call_args_list]
        self.assertEqual(len(first["printings"]), 1)
        self.assertNotEqual(first["editions"], second["editions"])
        self.assertEqual(editions.call_count, 2)

    def test_failed_preflight_cannot_create_a_game_run(self):
        seats = [{"revision": {"cards": []}}] * 4
        settings = SimpleNamespace(home=Path("engine"), verify=lambda: {})
        with ExitStack() as resources:
            resources.enter_context(
                patch("commander.engine.forge_engine.prepare_seats", return_value=seats)
            )
            resources.enter_context(
                patch("commander.engine.forge_engine.card_index", return_value={})
            )
            resources.enter_context(
                patch(
                    "commander.engine.forge_engine.coverage",
                    return_value={"missing": []},
                )
            )
            resources.enter_context(
                patch(
                    "commander.engine.forge_engine.serialize_deck", return_value="deck"
                )
            )
            resources.enter_context(
                patch(
                    "commander.engine.forge_engine.check_printing_choices",
                    side_effect=ValueError("Native printing differs"),
                )
            )
            execute = resources.enter_context(
                patch("commander.engine.forge_engine._execute_game")
            )
            with self.assertRaisesRegex(ValueError, "Native printing differs"):
                run_game(None, settings, [])
        execute.assert_not_called()

    def test_batch_evidence_ignores_run_ids_but_binds_native_facts(self):
        report = {
            **{field: {} for field in EVIDENCE_FIELDS},
            "run_id": "first",
            "process": {"elapsed_seconds": 1},
        }
        inputs = {
            "engine": {},
            "seats": [],
            "coverage": [],
            "printing_admission": report,
        }
        before = game_evidence(inputs)
        report.update(run_id="second", process={"elapsed_seconds": 99})
        self.assertEqual(game_evidence(inputs), before)
        report["editions"] = {"sha256": "changed"}
        self.assertNotEqual(game_evidence(inputs), before)

    def test_edition_inventory_changes_and_size_bounds_invalidate_resources(self):
        with tempfile.TemporaryDirectory() as folder:
            home = Path(folder)
            edition = home / "res/editions"
            edition.mkdir(parents=True)
            with self.assertRaises(ValueError):
                edition_fingerprint(home)
            first = edition / "unfinity.txt"
            first.write_text("first", encoding="utf-8")
            baseline = edition_fingerprint(home)
            first.write_text("second", encoding="utf-8")
            self.assertNotEqual(edition_fingerprint(home), baseline)
            second = edition / "added.txt"
            second.write_text("first", encoding="utf-8")
            self.assertNotEqual(edition_fingerprint(home)["files"], baseline["files"])
            second.write_bytes(b"x" * (512 * 1024 + 1))
            with self.assertRaises(ValueError):
                edition_fingerprint(home)

    def test_old_engines_cannot_simulate_dropped_supplementary_zones(self):
        for zone in ("attractions", "stickers"):
            with self.subTest(zone=zone):
                with self.assertRaisesRegex(ValueError, "drops supplementary zones"):
                    require_engine_capabilities({}, [{"zone": zone}])
                require_engine_capabilities(
                    {
                        "capabilities": [
                            "supplementary_ai_zones",
                            "sticker_snapshot_state",
                        ]
                    },
                    [{"zone": zone}],
                )

    def test_sticker_state_capability_is_required_only_for_sticker_games(self):
        lock = {"capabilities": ["supplementary_ai_zones"]}
        require_engine_capabilities(lock, [{"zone": "attractions"}])
        require_engine_capabilities(lock, [{"zone": "main"}])
        with self.assertRaisesRegex(ValueError, "loses applied sticker state"):
            require_engine_capabilities(lock, [{"zone": "stickers"}])

    def test_resource_changes_are_rechecked_immediately_before_native_launch(self):
        report = {
            "engine": self.plan["engine"],
            "editions": self.plan["editions"],
            "source_hashes": {"worker": "a"},
        }
        settings = SimpleNamespace(
            home=Path("engine"), verify=lambda: self.plan["engine"]
        )
        with (
            patch(
                "commander.engine.printing_admission.source_hashes",
                return_value={"worker": "a"},
            ),
            patch(
                "commander.engine.printing_admission.edition_fingerprint",
                return_value={"files": 1, "sha256": "changed"},
            ),
        ):
            with self.assertRaisesRegex(ValueError, "execution evidence changed"):
                verify_printing_evidence(settings, report)

    def test_edition_manifest_uses_portable_filename_order(self):
        with tempfile.TemporaryDirectory() as folder:
            home = Path(folder)
            editions = home / "res/editions"
            editions.mkdir(parents=True)
            for name in ("a.txt", "B.txt"):
                (editions / name).write_bytes(b"metadata")
            content_hash = hashlib.sha256(b"metadata").hexdigest()
            expected = hashlib.sha256(
                "".join(
                    name + "\0" + content_hash + "\n" for name in ("B.txt", "a.txt")
                ).encode()
            ).hexdigest()
            self.assertEqual(
                edition_fingerprint(home), {"files": 2, "sha256": expected}
            )
