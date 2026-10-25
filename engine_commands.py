"""Administrative CLI commands for the pinned engine and single-game adapter."""

import argparse
from pathlib import Path

from forge_config import DEFAULT_CONFIG, read_settings
from forge_engine import engine_health, run_game

ENGINE_COMMANDS = {"engine-check", "simulate-game"}


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


def run_engine_command(connection, args) -> dict:
    """Keep command rendering separate from engine services."""
    settings = read_settings(args.engine_config)
    if args.command == "engine-check":
        return engine_health(settings)
    return run_game(connection, settings, args.seat, args.seed, args.game_clock)
