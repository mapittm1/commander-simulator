"""Thin CLI adapters for the deck library's reusable backend operations."""

from pathlib import Path

from commander.decks.deck_store import get_revision, list_revisions, save_revision
from commander.decks.decks import BRACKETS, prepare_deck, read_decklist
from commander.rules.legality import validate_entries

REVISION_COMMANDS = {"deck-revise", "deck-show", "deck-history", "deck-check"}


def add_revision_commands(commands) -> None:
    """Register revision commands without reading files or opening a database."""
    revise = commands.add_parser("deck-revise", help="Append a legal deck revision")
    revise.add_argument("deck_id", type=int)
    revise.add_argument("file", type=Path)
    revise.add_argument("--bracket", type=int, choices=BRACKETS)
    revise.add_argument("--commander", action="append", default=[])
    for name, help_text in (
        ("deck-show", "Inspect a saved deck revision"),
        ("deck-check", "Recheck a saved revision against the current catalog"),
    ):
        command = commands.add_parser(name, help=help_text)
        command.add_argument("deck_id", type=int)
        command.add_argument("--version", type=int)
    history = commands.add_parser("deck-history", help="List immutable deck revisions")
    history.add_argument("deck_id", type=int)


def run_revision_command(connection, args) -> dict:
    """Return one structured response; command-line rendering stays in the entry point."""
    if args.command == "deck-revise":
        prepared = prepare_deck(connection, read_decklist(args.file), args.commander)
        revision_id = save_revision(connection, args.deck_id, prepared, args.bracket)
        return {"deck_id": args.deck_id, "revision_id": revision_id}
    if args.command == "deck-history":
        return {
            "deck_id": args.deck_id,
            "revisions": list_revisions(connection, args.deck_id),
        }
    revision = get_revision(connection, args.deck_id, args.version)
    if args.command == "deck-check":
        report = validate_entries(connection, revision["cards"])
        return {
            **report,
            "deck_id": args.deck_id,
            "revision_id": revision["id"],
            "version": revision["version"],
        }
    # A compact view preserves snapshot names; full Oracle data remains in backend storage.
    revision["cards"] = [
        {
            "name": entry["card"]["name"],
            "card_id": entry["card"]["card_id"],
            "quantity": entry["quantity"],
            "zone": entry["zone"],
        }
        for entry in revision["cards"]
    ]
    return revision
