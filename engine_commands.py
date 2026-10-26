"""Administrative CLI commands for the pinned engine and single-game adapter."""

import argparse
from pathlib import Path

from engine_audit import create_engine_audit
from engine_audit_store import SCOPES, SCRIPT_STATUSES, audit_cards, show_audit
from forge_config import DEFAULT_CONFIG, read_settings
from forge_engine import engine_health, run_game

ENGINE_COMMANDS = {
    "engine-check",
    "simulate-game",
    "engine-audit",
    "engine-audit-show",
    "engine-audit-cards",
}


def parse_seat(value: str) -> tuple[int, int]:
    """Require explicit deck-id:version references instead of moving latest pointers."""
    parts = value.split(":")
    if len(parts) != 2 or any(
        len(part) > 19 or not part.isascii() or not part.isdigit() for part in parts
    ):
        raise argparse.ArgumentTypeError("Seat must be deck_id:version")
    pair = tuple(int(part) for part in parts)
    if any(not 1 <= number < 2**63 for number in pair):
        raise argparse.ArgumentTypeError(
            "Seat IDs and versions must be positive 64-bit integers"
        )
    return pair


def add_engine_commands(commands) -> None:
    """Register trusted local engine configuration and bounded game parameters."""
    health = commands.add_parser(
        "engine-check", help="Check the installed pinned engine"
    )
    health.add_argument("--engine-config", type=Path, default=DEFAULT_CONFIG)
    game = commands.add_parser(
        "simulate-game", help="Run one reviewed four-player game"
    )
    game.add_argument("--engine-config", type=Path, default=DEFAULT_CONFIG)
    game.add_argument("--seat", action="append", type=parse_seat, required=True)
    game.add_argument("--seed", type=int, default=42)
    game.add_argument("--game-clock", type=int, default=120)
    audit = commands.add_parser(
        "engine-audit", help="Freeze catalog-wide script coverage; no games are run"
    )
    audit.add_argument("--engine-config", type=Path, default=DEFAULT_CONFIG)
    show = commands.add_parser(
        "engine-audit-show", help="Inspect historical engine coverage"
    )
    show.add_argument("audit_id")
    cards = commands.add_parser(
        "engine-audit-cards", help="Page historical coverage gaps and matches"
    )
    cards.add_argument("audit_id")
    cards.add_argument("--scope", choices=(*SCOPES, "all"), default="commander_legal")
    cards.add_argument(
        "--status", choices=(*SCRIPT_STATUSES, "all"), default="missing_script"
    )
    cards.add_argument("--offset", type=int, default=0)
    cards.add_argument("--limit", type=int, default=100)


def run_engine_command(connection, args) -> dict:
    """Keep command rendering separate from engine services."""
    if args.command == "engine-audit-show":
        return show_audit(connection, args.audit_id)
    if args.command == "engine-audit-cards":
        return audit_cards(
            connection,
            args.audit_id,
            {key: getattr(args, key) for key in ("scope", "status", "offset", "limit")},
        )
    settings = read_settings(args.engine_config)
    if args.command == "engine-check":
        return engine_health(settings)
    if args.command == "engine-audit":
        return create_engine_audit(connection, settings)
    return run_game(connection, settings, args.seat, args.seed, args.game_clock)
