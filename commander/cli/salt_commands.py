"""Local source-import and advisory salt-profile commands."""

from pathlib import Path

from commander.salt.salt_analysis import deck_salt
from commander.salt.salt_data import read_salt_manifest
from commander.salt.salt_store import save_salt_snapshot, show_salt_snapshot

SALT_COMMANDS = {"salt-import", "salt-show", "deck-salt"}


def add_salt_commands(commands) -> None:
    """Require explicit source snapshots and immutable deck revisions for analysis."""
    source = commands.add_parser(
        "salt-import", help="Import an attributed EDHREC salt sample"
    )
    source.add_argument("file", type=Path)
    show = commands.add_parser(
        "salt-show", help="Inspect an immutable salt source snapshot"
    )
    show.add_argument("snapshot_id")
    deck = commands.add_parser(
        "deck-salt", help="Report matched community salt ratings for a legal deck"
    )
    deck.add_argument("deck_id", type=int)
    deck.add_argument("--version", type=int, required=True)
    deck.add_argument("--salt-snapshot", required=True)


def run_salt_command(connection, args) -> dict:
    """Keep source data and social opinion signals separate from engine execution."""
    if args.command == "salt-import":
        return save_salt_snapshot(connection, read_salt_manifest(args.file))
    if args.command == "salt-show":
        return show_salt_snapshot(connection, args.snapshot_id)
    return deck_salt(connection, args.deck_id, args.version, args.salt_snapshot)
