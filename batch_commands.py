"""Local operator commands for durable simulation batches."""

from pathlib import Path

from batch_diagnostics import batch_diagnostics
from batch_store import attempt_results, cancel_job, job_summary
from batches import BatchOptions, create_batch, run_batch
from engine_commands import parse_seat
from forge_config import DEFAULT_CONFIG, read_settings

BATCH_COMMANDS = {
    "batch-create",
    "batch-run",
    "batch-show",
    "batch-cancel",
    "batch-results",
    "batch-diagnostics",
}


def add_batch_commands(commands) -> None:
    """Register explicit creation, bounded work, inspection, and cancellation."""
    create = commands.add_parser(
        "batch-create", help="Validate and queue a fixed four-player pod"
    )
    create.add_argument("--seat", action="append", type=parse_seat, required=True)
    create.add_argument("--runs", type=int, required=True)
    create.add_argument("--seed", type=int, default=42)
    create.add_argument("--game-clock", type=int, default=120)
    create.add_argument("--engine-config", type=Path, default=DEFAULT_CONFIG)
    run = commands.add_parser(
        "batch-run", help="Run a bounded chunk of a queued/paused batch"
    )
    run.add_argument("job_id")
    run.add_argument("--max-games", type=int, default=1)
    run.add_argument("--engine-config", type=Path, default=DEFAULT_CONFIG)
    for command in ("batch-show", "batch-cancel", "batch-diagnostics"):
        parser = commands.add_parser(command)
        parser.add_argument("job_id")
    results = commands.add_parser("batch-results", help="Page individual game results")
    results.add_argument("job_id")
    results.add_argument("--offset", type=int, default=0)
    results.add_argument("--limit", type=int, default=100)


def run_batch_command(connection, args) -> dict:
    """Dispatch without loading an executable configuration for read/cancel operations."""
    if args.command == "batch-show":
        return job_summary(connection, args.job_id)
    if args.command == "batch-cancel":
        return cancel_job(connection, args.job_id)
    if args.command == "batch-diagnostics":
        return batch_diagnostics(connection, args.job_id)
    if args.command == "batch-results":
        return attempt_results(connection, args.job_id, args.offset, args.limit)
    settings = read_settings(args.engine_config)
    if args.command == "batch-create":
        return create_batch(
            connection,
            settings,
            args.seat,
            BatchOptions(args.runs, args.seed, args.game_clock),
        )
    return run_batch(connection, settings, args.job_id, args.max_games)
