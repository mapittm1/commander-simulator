"""Opt-in real CLI import/review/run/report flow, restricted to isolated test fixtures."""

import io
import json
import os
import sqlite3
import tempfile
import unittest
from contextlib import closing, redirect_stderr, redirect_stdout
from pathlib import Path

import pytest

from commander.catalog.database import DEFAULT_DATABASE, connect, store_printing
from commander.cli.app import main
from commander.engine.engine_fixtures import FOREST_POD, legal_fixture_pod
from commander.rules.brackets import load_policy
from tests.support.catalog_fixture import copy_catalog_records

pytestmark = pytest.mark.integration


@unittest.skipUnless(
    os.environ.get("COMMANDER_ENGINE_INTEGRATION") == "1",
    "Requires an operator-installed engine and current local catalog",
)
class LiveCliTests(unittest.TestCase):
    """Fixture attestations never enter the real deck library or a ranked benchmark."""

    def test_import_play_and_resume_completed_pod(self):
        """Run the same four-file command a local user runs, with the pinned engine."""
        with (
            closing(connect(DEFAULT_DATABASE)) as source,
            legal_fixture_pod(source, FOREST_POD) as (fixture, _),
            tempfile.TemporaryDirectory() as directory,
        ):
            directory = Path(directory)
            selected_attractions = _selected_attractions(source, fixture)
            database = directory / "cli.sqlite3"
            with closing(sqlite3.connect(database)) as destination:
                fixture.backup(destination)
            review = directory / "review.json"
            review.write_text(
                json.dumps(
                    {
                        "reviewer": "Automated isolated CLI integration fixture",
                        "rationale": "99 basics, one commander and an optional selected Attraction deck; software regression only, not a production human attestation.",
                        "checks": dict.fromkeys(load_policy()["review_checks"], True),
                    }
                ),
                encoding="utf-8",
            )
            decks = _write_cli_decks(directory, selected_attractions)
            output, progress = io.StringIO(), io.StringIO()
            with redirect_stdout(output), redirect_stderr(progress):
                main(
                    [
                        "--database",
                        str(database),
                        "play",
                        *decks,
                        "--bracket",
                        "2",
                        "--review-file",
                        str(review),
                        "--runs",
                        "1",
                        "--game-clock",
                        "60",
                        "--output",
                        str(directory / "report.json"),
                        "--json",
                    ]
                )
            report = json.loads(output.getvalue())
            self.assertEqual(
                (report["status"], report["batch"]["completed"]), ("completed", 1)
            )
            self.assertIn("Game 1: completed", progress.getvalue())
            self.assertEqual(report["diagnostics"]["coverage"]["games_included"], 1)
            self.assertEqual(
                json.loads((directory / "report.json").read_text())["status"],
                "completed",
            )
            evidence = _frozen_printing(database, report["batch"]["job_id"])
            self.assertEqual(evidence["printings"][0]["collector_number"], "202c")
            self.assertEqual(evidence["printings"][0]["attraction_lights"], [2, 5, 6])
            output = io.StringIO()
            with redirect_stdout(output), redirect_stderr(io.StringIO()):
                main(
                    [
                        "--database",
                        str(database),
                        "simulate",
                        "--resume",
                        report["batch"]["job_id"],
                        "--output",
                        str(directory / "resumed.json"),
                        "--json",
                    ]
                )
            resumed = json.loads(output.getvalue())
            self.assertEqual(resumed["batch"]["attempted"], 1)
            self.assertEqual(resumed["attempt_preview"], report["attempt_preview"])
            print(
                json.dumps(
                    {
                        "live_cli_job": report["batch"]["job_id"],
                        "status": report["status"],
                        "attempts": 1,
                    }
                )
            )


def _selected_attractions(source, fixture):
    """Enrich only the disposable CLI catalog with current attributed source records."""
    names = [
        row[0]
        for row in source.execute(
            "SELECT name FROM cards WHERE commander_legality='legal' AND type_line LIKE '%Attraction%' AND name != 'Bumper Cars' ORDER BY name LIMIT 9"
        )
    ]
    if len(names) != 9:
        raise ValueError("CLI fixture requires ten legal Attraction identities")
    copy_catalog_records(source, fixture, ["Bumper Cars", *names])
    row = source.execute(
        "SELECT raw_json FROM printings WHERE set_code='unf' AND collector_number='202c' AND language='en'"
    ).fetchone()
    if row is None:
        raise ValueError(
            "CLI fixture requires Bumper Cars printing 202c; run sync-attractions"
        )
    with fixture:
        store_printing(fixture, json.loads(row[0]))
    return "Attractions\n1 Bumper Cars (UNF) 202c\n" + "".join(
        f"1 {name}\n" for name in names
    )


def _write_cli_decks(directory, selected_attractions):
    """Write four real imports with an explicit Attraction printing in the first seat."""
    arguments = []
    for number, leader in enumerate(FOREST_POD):
        path = directory / f"diagnostic-{number}.txt"
        path.write_text(
            f"Commander\n1 {leader}\nMainboard\n99 Forest\n"
            + (selected_attractions if number == 0 else ""),
            encoding="utf-8",
        )
        arguments.extend(("--deck", str(path)))
    return arguments


def _frozen_printing(database, job_id):
    """Inspect the persisted batch contract rather than infer success from deck text."""
    with closing(connect(database)) as retained:
        raw = retained.execute(
            "SELECT spec_json FROM simulation_jobs WHERE id=?", (job_id,)
        ).fetchone()[0]
    return json.loads(raw)["inputs"]["printing_admission"]
