"""Commander Simulator: local Magic card catalog commands."""

import argparse
import json
import sqlite3
from contextlib import closing
from pathlib import Path

from database import DEFAULT_DATABASE, catalog_stats, connect, find_cards
from deck_commands import REVISION_COMMANDS, add_revision_commands, run_revision_command
from decks import BRACKETS, list_decks, prepare_deck, read_decklist, save_deck
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
    return parser


def main(argv=None):
    """Parse CLI arguments and dispatch a catalog or deck command."""
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        with closing(connect(args.database)) as connection:
            if args.command in REVISION_COMMANDS:
                result = run_revision_command(connection, args)
                print(json.dumps(result, indent=2, ensure_ascii=False))
                if args.command == "deck-check" and result["status"] != "legal":
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
                print(
                    json.dumps(
                        find_cards(connection, args.name), indent=2, ensure_ascii=False
                    )
                )
            elif args.command in {"init", "stats"}:
                print(json.dumps(catalog_stats(connection)))
    except (OSError, ValueError, KeyError, StopIteration, sqlite3.Error) as error:
        parser.exit(1, f"Error: {error}\n")


if __name__ == "__main__":
    main()
