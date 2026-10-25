"""CLI surface for source-scoped opponent snapshots; no arbitrary URL downloads."""

from pathlib import Path

from opponent_manifest import read_manifest
from opponents import (
    get_collection,
    list_collections,
    require_collection,
    save_collection,
)

OPPONENT_COMMANDS = {
    "opponents-import",
    "opponents",
    "opponents-show",
    "opponents-check",
}


def add_opponent_commands(commands) -> None:
    """Define local import, inspection, and readiness commands."""
    importer = commands.add_parser(
        "opponents-import", help="Import a local source-sample manifest"
    )
    importer.add_argument("manifest", type=Path)
    commands.add_parser("opponents", help="List opponent collection snapshots")
    for name in ("opponents-show", "opponents-check"):
        command = commands.add_parser(name, help=name.replace("-", " "))
        command.add_argument("collection_id", type=int)


def run_opponent_command(connection, args) -> dict:
    """Dispatch to backend services and return one JSON-ready result."""
    if args.command == "opponents-import":
        return {
            "collection_id": save_collection(connection, read_manifest(args.manifest))
        }
    if args.command == "opponents":
        return {"collections": list_collections(connection)}
    if args.command == "opponents-check":
        return {"status": "ready", **require_collection(connection, args.collection_id)}
    return get_collection(connection, args.collection_id)
