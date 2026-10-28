"""Opt-in native condition matrix for all nine Commander-legal companion identities."""

import hashlib
import json
import os
import unittest
from contextlib import closing

import pytest

from commander.catalog.database import (
    DEFAULT_DATABASE,
    connect,
    find_deck_candidates,
    get_card_details,
)
from commander.engine.catalog_probe_io import source_hashes
from commander.engine.companion_reviews import condition_matches, condition_review
from commander.engine.forge_config import file_hash, read_settings
from commander.engine.forge_decks import card_index, script_match
from commander.engine.interactions.suite import read_bounded_json, write_bounded_json
from commander.engine.native_runtime import create_run
from commander.paths import PROJECT_ROOT
from commander.rules.legality import require_current_catalog
from tests.support.native_worker import run_test_worker

pytestmark = pytest.mark.integration

# Case expectations are independent of the native condition implementation.
CASES = (
    ("gyruda_valid", "Gyruda, Doom of Depths", "Etrata, the Silencer", None, True),
    (
        "gyruda_odd_spell",
        "Gyruda, Doom of Depths",
        "Etrata, the Silencer",
        "Consider",
        False,
    ),
    ("jegantha_valid", "Jegantha, the Wellspring", "Ramos, Dragon Engine", None, True),
    (
        "jegantha_repeated_green",
        "Jegantha, the Wellspring",
        "Ramos, Dragon Engine",
        "Ghalta, Primal Hunger",
        False,
    ),
    (
        "kaheera_valid",
        "Kaheera, the Orphanguard",
        "Arahbo, Roar of the World",
        None,
        True,
    ),
    (
        "kaheera_wrong_creature_type",
        "Kaheera, the Orphanguard",
        "Arahbo, Roar of the World",
        "Llanowar Elves",
        False,
    ),
    (
        "kaheera_changeling",
        "Kaheera, the Orphanguard",
        "Arahbo, Roar of the World",
        "Woodland Changeling",
        True,
    ),
    ("keruga_valid", "Keruga, the Macrosage", "Koma, Cosmos Serpent", None, True),
    (
        "keruga_small_nonland",
        "Keruga, the Macrosage",
        "Koma, Cosmos Serpent",
        "Llanowar Elves",
        False,
    ),
    (
        "lurrus_valid",
        "Lurrus of the Dream-Den",
        "Elas il-Kor, Sadistic Pilgrim",
        None,
        True,
    ),
    (
        "lurrus_commander_too_large",
        "Lurrus of the Dream-Den",
        "Queen Marchesa",
        None,
        False,
    ),
    (
        "lurrus_large_permanent",
        "Lurrus of the Dream-Den",
        "Elas il-Kor, Sadistic Pilgrim",
        "Phyrexian Arena",
        False,
    ),
    ("obosh_valid", "Obosh, the Preypiercer", "Judith, the Scourge Diva", None, True),
    (
        "obosh_even_permanent",
        "Obosh, the Preypiercer",
        "Judith, the Scourge Diva",
        "Arcane Signet",
        False,
    ),
    (
        "umori_hidden_zone_creature",
        "Umori, the Collector",
        "Grist, the Hunger Tide",
        None,
        True,
    ),
    (
        "umori_shared_creature_with_cda",
        "Umori, the Collector",
        "Grist, the Hunger Tide",
        "Llanowar Elves",
        True,
    ),
    (
        "umori_no_shared_type",
        "Umori, the Collector",
        "Grist, the Hunger Tide",
        "Cultivate",
        False,
    ),
    (
        "zirda_valid_basic_land_abilities",
        "Zirda, the Dawnwaker",
        "Kenrith, the Returned King",
        None,
        True,
    ),
    (
        "zirda_static_permanent",
        "Zirda, the Dawnwaker",
        "Kenrith, the Returned King",
        "Glorious Anthem",
        False,
    ),
    (
        "zirda_colon_in_reminder_not_activation",
        "Zirda, the Dawnwaker",
        "Kenrith, the Returned King",
        "Rings of Brighthearth",
        False,
    ),
    (
        "zirda_activated_nonland",
        "Zirda, the Dawnwaker",
        "Kenrith, the Returned King",
        "Ashnod's Altar",
        True,
    ),
    (
        "yorion_cannot_exceed_commander_100",
        "Yorion, Sky Nomad",
        "Brago, King Eternal",
        None,
        False,
    ),
)


@unittest.skipUnless(
    os.environ.get("COMMANDER_ENGINE_INTEGRATION") == "1",
    "Requires catalog, pinned engine and native bridge",
)
class CompanionConditionMatrixTests(unittest.TestCase):
    """Condition evidence is distinct from separate color/ban/uniqueness admission checks."""

    def test_all_legal_companion_conditions_have_positive_and_negative_controls(self):
        """Check parity, costs, type changes, changeling, activated abilities and deck size."""
        settings = read_settings()
        run = create_run()
        with closing(connect(DEFAULT_DATABASE)) as connection:
            catalog = require_current_catalog(connection)
            index = card_index(settings.home / "res/cardsfolder/cardsfolder.zip")
            cases = [_case(connection, index, definition) for definition in CASES]
        inputs = {"catalog": catalog, "source_hashes": source_hashes(), "cases": cases}
        path = run / "matrix-inputs.json"
        write_bounded_json(path, inputs)
        worker = PROJECT_ROOT / "tests/support/companion_matrix_worker.py"
        process, worker_hash = run_test_worker(settings, run, worker, "matrix.log")
        self.assertEqual(
            (process["status"], process["returncode"]), ("finished", 0), str(run)
        )
        observed = read_bounded_json(run / "matrix-receipt.json")
        self.assertEqual(observed["engine"], settings.verify())
        self.assertEqual(observed["inputs_sha256"], file_hash(path))
        self.assertEqual(inputs["source_hashes"], source_hashes())
        self.assertEqual(worker_hash, file_hash(worker))
        self.assertEqual(
            [case["id"] for case in observed["cases"]], [case[0] for case in CASES]
        )
        for definition, row in zip(CASES, observed["cases"]):
            with self.subTest(case=definition[0]):
                self.assertIs(row["eligible"], definition[4])
                self.assertEqual(row["starting_cards"], 100)
                self.assertEqual(row["companion_name"], definition[1])
                self._assert_condition(row, cases, observed["engine"])

        print(
            json.dumps(
                {
                    "companion_matrix_run": run.name,
                    "cases": len(CASES),
                    "companions": len({case[1] for case in CASES}),
                }
            )
        )

    def _assert_condition(self, row, cases, engine):
        plan = next(case["plan"] for case in cases if case["id"] == row["id"])
        plan = {**plan, "engine": engine}
        plan["condition_review"] = condition_review(plan)
        self.assertTrue(condition_matches(plan, row["condition"]))


def _identity(connection, index, name, quantity):
    matches = find_deck_candidates(connection, name)
    if len(matches) != 1:
        raise ValueError("Missing/ambiguous companion matrix identity: " + name)
    card_id = matches[0]["card_id"]
    match = script_match(name, index, card_id)
    if match["script_status"] != "script_present":
        raise ValueError("Missing native matrix card: " + name)
    return {
        "name": name,
        "card_id": card_id,
        "engine_name": match["engine_name"],
        "quantity": quantity,
    }


def _case(connection, index, definition):
    case_id, companion, commander, extra, _ = definition
    starting = [
        _identity(connection, index, "Forest", 98 if extra else 99),
        _identity(connection, index, commander, 1),
    ]
    if extra:
        starting.append(_identity(connection, index, extra, 1))
    selected = _identity(connection, index, companion, 1)
    oracle = get_card_details(connection, selected["card_id"])
    return {
        "id": case_id,
        "plan": {
            "starting": starting,
            "companion": selected,
            "condition_text": next(
                line.removeprefix("Companion — ").split(" (", 1)[0]
                for line in oracle["oracle_text"].splitlines()
                if line.startswith("Companion — ")
            ),
            "oracle_sha256": hashlib.sha256(
                json.dumps(oracle, sort_keys=True).encode()
            ).hexdigest(),
        },
    }
