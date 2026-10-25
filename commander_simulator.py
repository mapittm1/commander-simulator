"""Commander Simulator: local Magic card catalog commands."""

import argparse
import json
from contextlib import closing
from pathlib import Path

from database import DEFAULT_DATABASE, catalog_stats, connect, find_cards
from scryfall import download_bulk, import_bulk


def hello_world():
    print("Hello, world!")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", type=Path, default=DEFAULT_DATABASE)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("init", help="Create an empty local database")
    sync = commands.add_parser("sync", help="Download and import Scryfall bulk data")
    sync.add_argument("--kind", choices=("oracle_cards", "default_cards", "all_cards"),
                      default="oracle_cards")
    local = commands.add_parser("import", help="Import an existing Scryfall JSON export")
    local.add_argument("file", type=Path)
    lookup = commands.add_parser("lookup", help="Look up a card or face by exact name")
    lookup.add_argument("name")
    commands.add_parser("stats", help="Show catalog counts")
    args = parser.parse_args(argv)
    try:
        with closing(connect(args.database)) as connection:
            if args.command == "sync":
                path = args.database.parent / f"{args.kind}.bulk"
                metadata = download_bulk(path, args.kind)
                count = import_bulk(connection, path, args.kind, metadata["updated_at"])
                print(f"Imported {count} records.")
            elif args.command == "import":
                print(f"Imported {import_bulk(connection, args.file)} records.")
            elif args.command == "lookup":
                print(json.dumps(find_cards(connection, args.name), indent=2, ensure_ascii=False))
            print(json.dumps(catalog_stats(connection)))
    except (OSError, ValueError, KeyError, StopIteration) as error:
        parser.exit(1, f"Error: {error}\n")


if __name__ == "__main__":
    main()
