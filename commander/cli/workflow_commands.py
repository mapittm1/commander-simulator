"""Runnable local workflows above the existing legality, review, and batch services."""

import sqlite3
import sys
import uuid
from contextlib import closing
from pathlib import Path

from commander.cli.engine_commands import parse_seat
from commander.cli.json_output import write_json, write_text
from commander.cli.project_health import readiness_report
from commander.decks.decks import BRACKETS
from commander.decks.pod_import import import_reviewed_pod
from commander.engine.engine_fixtures import FOREST_POD, legal_fixture_pod
from commander.engine.forge_config import DEFAULT_CONFIG, read_settings
from commander.paths import DATA_ROOT
from commander.rules.brackets import read_review
from commander.simulation.batches import BatchOptions, create_batch
from commander.simulation.workflow import finish_batch, save_report

WORKFLOW_COMMANDS = {"doctor", "demo", "play", "simulate"}


def add_workflow_commands(commands) -> None:
    """Expose readiness, a diagnostic demo, four-file import, and saved-pod execution."""
    doctor = commands.add_parser(
        "doctor", help="Check catalog and pinned runtime readiness"
    )
    doctor.add_argument("--engine-config", type=Path, default=DEFAULT_CONFIG)
    doctor.add_argument("--json", action="store_true")
    for name, help_text in (
        ("demo", "Run diagnostic decks in an isolated database; not a benchmark"),
        ("play", "Import four decklists, apply your explicit review, and run a batch"),
        ("simulate", "Finish a reviewed saved pod or resume an existing batch"),
    ):
        command = commands.add_parser(name, help=help_text)
        command.add_argument("--engine-config", type=Path, default=DEFAULT_CONFIG)
        command.add_argument("--runs", type=int)
        command.add_argument("--seed", type=int)
        command.add_argument("--game-clock", type=int)
        command.add_argument("--output", type=Path)
        command.add_argument(
            "--json",
            action="store_true",
            help="Final JSON on stdout; progress on stderr",
        )
        if name == "play":
            command.add_argument(
                "--deck",
                action="append",
                type=Path,
                required=True,
                help="Exactly four files with Commander sections; yours first",
            )
            command.add_argument("--bracket", type=int, choices=BRACKETS, required=True)
            command.add_argument(
                "--review-file",
                type=Path,
                required=True,
                help="Your completed human attestation applies to each of the four decks",
            )
        if name == "simulate":
            selection = command.add_mutually_exclusive_group(required=True)
            selection.add_argument("--seat", action="append", type=parse_seat)
            selection.add_argument("--resume", metavar="JOB_ID")


def run_workflow_command(connection: sqlite3.Connection, args) -> int:
    """Return a process exit code; leave old machine-readable commands unchanged."""
    if args.command == "doctor":
        report = readiness_report(connection, args.engine_config)
        _show_doctor(report, args.json)
        return 0 if report["status"] == "ready" else 1
    if args.output is not None and args.output.exists():
        raise FileExistsError("Report already exists; choose a new --output path")
    settings = read_settings(args.engine_config)
    if args.command == "demo":
        report, destination = _demo(connection, settings, args)
    else:
        report, destination = _simulate(connection, settings, args)
    save_report(report, destination)
    if args.json:
        write_json(report)
    else:
        _show_summary(report, destination)
    if report["status"] == "completed":
        return 0
    if report["status"] == "interrupted":
        return 130
    return 1


def _options(args) -> BatchOptions:
    default_runs = 1 if args.command == "demo" else 100
    default_clock = 60 if args.command == "demo" else 120
    options = BatchOptions(
        args.runs if args.runs is not None else default_runs,
        args.seed if args.seed is not None else 42,
        args.game_clock if args.game_clock is not None else default_clock,
    )
    options.validate()
    return options


def _progress(args):
    stream = sys.stderr if args.json else sys.stdout

    def emit(index, result):
        winner = result.get("winner_pod_index")
        suffix = f", winner: pod deck {winner + 1}" if winner is not None else ""
        write_text(f"Game {index + 1}: {result['status']}{suffix}", stream)

    return emit


def _simulate(connection, settings, args):
    if args.command == "simulate" and args.resume:
        if any(
            getattr(args, key) is not None for key in ("runs", "seed", "game_clock")
        ):
            raise ValueError(
                "A resumed batch keeps its frozen runs, seed, and clock; omit those options"
            )
        job_id = args.resume
    else:
        options = _options(args)
        seats = (
            args.seat
            if args.command == "simulate"
            else import_reviewed_pod(
                connection,
                settings,
                args.deck,
                args.bracket,
                read_review(args.review_file),
            )
        )
        job_id = create_batch(connection, settings, seats, options)["job_id"]
    stream = sys.stderr if args.json else sys.stdout
    write_text(
        f"Batch: {job_id}. Ctrl+C stops safely; remaining games can be resumed.", stream
    )
    report = finish_batch(connection, settings, job_id, args.database, _progress(args))
    destination = (
        args.output or DATA_ROOT / "reports" / f"{job_id}-{uuid.uuid4().hex[:8]}.json"
    )
    report["report_path"] = str(destination.resolve())
    report["purpose"] = "explicit_fixed_pod_not_a_popularity_benchmark"
    return report, destination


def _demo(source, settings, args):
    options = _options(args)
    folder = DATA_ROOT / "demos" / uuid.uuid4().hex
    folder.mkdir(parents=True, exist_ok=False)
    database = folder / "demo.sqlite3"
    stream = sys.stderr if args.json else sys.stdout
    write_text(
        f"Diagnostic demo: {options.runs} attempt(s). Database: {database}", stream
    )
    with legal_fixture_pod(source, FOREST_POD) as (fixture, seats):
        try:
            job = create_batch(fixture, settings, seats, options)
            write_text(
                f"Batch: {job['job_id']}. Ctrl+C preserves interrupted work.", stream
            )
            report = finish_batch(
                fixture, settings, job["job_id"], database, _progress(args)
            )
        finally:
            with closing(sqlite3.connect(database)) as saved:
                fixture.backup(saved)
    destination = args.output or folder / "report.json"
    report.update(
        purpose="diagnostic_fixture_not_a_benchmark",
        report_path=str(destination.resolve()),
    )
    return report, destination


def _show_doctor(report, as_json):
    if as_json:
        write_json(report)
        return
    for check in report["checks"]:
        write_text(f"{check['name']}: {check['status']}")
        if check["status"] != "ready":
            write_text(f"  {check['error']}\n  {check['next_step']}")
    write_text(f"Overall: {report['status']}")
    if report["status"] == "ready":
        write_text("Try: " + report["next_command"])


def _show_summary(report, destination):
    batch = report["batch"]
    write_text(
        f"\n{report['status'].upper()}: {batch['completed']}/{batch['requested']} completed; "
        f"{batch['failed']} failed, {batch['interrupted']} interrupted."
    )
    for index, deck in enumerate(report["pod"]):
        wins = batch["outcomes"]["wins_by_original_pod_index"][index]
        rate = batch["outcomes"]["win_rates"][index]
        percentage = f"{rate:.1%}" if rate is not None else "unknown"
        write_text(
            f"  {index + 1}. {deck['name']} [{deck['deck_id']}:{deck['version']}]: {wins} wins ({percentage})"
        )
    coverage = report["diagnostics"]["coverage"]
    write_text(
        f"Observed diagnostics: {coverage['games_included']} games; {coverage['games_excluded']} excluded."
    )
    write_text(
        "These are AI fixed-pod results; mana problems and reasons for human aggression are not established."
    )
    write_text(
        f"Report: {destination.resolve()}\nDatabase: {report['database']}\nBatch: {batch['job_id']}"
    )
    if batch.get("error"):
        write_text("Stopped: " + batch["error"])
    if batch["unattempted"] and batch["status"] in {"paused", "blocked"}:
        write_text(
            f'Resume: python commander_simulator.py --database "{report["database"]}" simulate --resume {batch["job_id"]}'
        )
