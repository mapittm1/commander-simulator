"""Commander Simulator: local Magic card catalog commands."""

import argparse
import json
import sqlite3
from contextlib import closing
from pathlib import Path

from batch_commands import BATCH_COMMANDS, add_batch_commands, run_batch_command
from bracket_commands import BRACKET_COMMANDS, add_bracket_commands, run_bracket_command
from database import DEFAULT_DATABASE, catalog_stats, connect, find_cards
from deck_commands import REVISION_COMMANDS, add_revision_commands, run_revision_command
from decks import BRACKETS, list_decks, prepare_deck, read_decklist, save_deck
from engine_commands import ENGINE_COMMANDS, add_engine_commands, run_engine_command
from json_output import write_json
from opponent_commands import (
    OPPONENT_COMMANDS,
    add_opponent_commands,
    run_opponent_command,
)
from salt_commands import SALT_COMMANDS, add_salt_commands, run_salt_command
from scryfall import download_bulk, import_bulk


def hello_world():
    """Print the original project greeting."""
    print("Hello, world!")


def build_parser() -> argparse.ArgumentParser:
    """Define the available commands without performing database operations."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", type=Path, default=DEFAULT_DATABASE)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("init", help="Create an empty local database")
    sync = commands.add_parser("sync", help="Download and import Scryfall bulk data")
    sync.add_argument(
        "--kind",
        choices=("oracle_cards", "default_cards", "all_cards"),
        default="oracle_cards",
    )
    local = commands.add_parser(
        "import", help="Import an existing Scryfall JSON export"
    )
    local.add_argument("file", type=Path)
    lookup = commands.add_parser("lookup", help="Look up a card or face by exact name")
    lookup.add_argument("name")
    commands.add_parser("stats", help="Show catalog counts")
    deck_import = commands.add_parser(
        "deck-add", help="Import a text Commander decklist"
    )
    deck_import.add_argument("file", type=Path)
    deck_import.add_argument("--name", required=True)
    deck_import.add_argument("--bracket", type=int, choices=BRACKETS, required=True)
    deck_import.add_argument("--commander", action="append", default=[])
    deck_import.add_argument("--source-url")
    validate = commands.add_parser(
        "validate", help="Check Commander legality without saving"
    )
    validate.add_argument("file", type=Path)
    validate.add_argument("--commander", action="append", default=[])
    commands.add_parser("decks", help="List imported decks and declared brackets")
    add_revision_commands(commands)
    add_bracket_commands(commands)
    add_opponent_commands(commands)
    add_engine_commands(commands)
    add_batch_commands(commands)
    add_salt_commands(commands)
    return parser


def run_library_command(connection, args) -> bool:
    """Render one backend response and report whether a diagnostic gate passed."""
    handlers = dict.fromkeys(BRACKET_COMMANDS, run_bracket_command)
    handlers.update(dict.fromkeys(REVISION_COMMANDS, run_revision_command))
    handlers.update(dict.fromkeys(OPPONENT_COMMANDS, run_opponent_command))
    handlers.update(dict.fromkeys(ENGINE_COMMANDS, run_engine_command))
    handlers.update(dict.fromkeys(BATCH_COMMANDS, run_batch_command))
    handlers.update(dict.fromkeys(SALT_COMMANDS, run_salt_command))
    result = handlers[args.command](connection, args)
    write_json(result)
    if args.command == "batch-run":
        return result["status"] in {"paused", "completed"} and not (
            result["failed"] or result["interrupted"]
        )
    expected = {
        "bracket-check": "reviewed",
        "deck-check": "legal",
        "engine-check": "ready",
        "simulate-game": "completed",
    }.get(args.command)
    return expected is None or result["status"] == expected


def main(argv=None):
    """Parse CLI arguments and dispatch a catalog or deck command."""
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        with closing(connect(args.database)) as connection:
            if (
                args.command
                in BRACKET_COMMANDS
                | REVISION_COMMANDS
                | OPPONENT_COMMANDS
                | ENGINE_COMMANDS
                | BATCH_COMMANDS
                | SALT_COMMANDS
            ):
                if not run_library_command(connection, args):
                    parser.exit(1)
            elif args.command == "validate":
                prepared = prepare_deck(
                    connection, read_decklist(args.file), args.commander
                )
                print(json.dumps(prepared["legality"], indent=2))
                if prepared["legality"]["status"] != "legal":
                    parser.exit(1)
            elif args.command == "deck-add":
                prepared = prepare_deck(
                    connection, read_decklist(args.file), args.commander
                )
                deck_id = save_deck(
                    connection, args.name, args.bracket, prepared, args.source_url
                )
                print(
                    json.dumps(
                        {
                            "deck_id": deck_id,
                            "total": prepared["total"],
                            "declared_bracket": args.bracket,
                            "warnings": prepared["warnings"],
                            "legality": prepared["legality"],
                        },
                        indent=2,
                    )
                )
            elif args.command == "decks":
                print(json.dumps(list_decks(connection), indent=2))
            elif args.command == "sync":
                path = args.database.parent / f"{args.kind}.bulk"
                metadata = download_bulk(path, args.kind)
                count = import_bulk(connection, path, args.kind, metadata["updated_at"])
                print(f"Imported {count} records.")
            elif args.command == "import":
                print(f"Imported {import_bulk(connection, args.file)} records.")
            elif args.command == "lookup":
                write_json(find_cards(connection, args.name))
            elif args.command in {"init", "stats"}:
                print(json.dumps(catalog_stats(connection)))
    except (OSError, ValueError, KeyError, StopIteration, sqlite3.Error) as error:
        parser.exit(1, f"Error: {error}\n")


if __name__ == "__main__":
    main()
