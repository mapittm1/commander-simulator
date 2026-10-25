"""Command-line adapters for bracket checks and human review records."""

from pathlib import Path

from brackets import check_bracket, read_review, review_history, save_bracket_review

BRACKET_COMMANDS = {"bracket-check", "bracket-review", "bracket-history"}


def add_bracket_commands(commands) -> None:
    """Register commands without database or network activity."""
    for name in sorted(BRACKET_COMMANDS):
        command = commands.add_parser(name, help=name.replace("-", " "))
        command.add_argument("deck_id", type=int)
        command.add_argument("--version", type=int)
        if name == "bracket-review":
            command.add_argument("review_file", type=Path)


def run_bracket_command(connection, args) -> dict:
    """Return one JSON-ready result from the selected backend operation."""
    if args.command == "bracket-check":
        return check_bracket(connection, args.deck_id, args.version)
    if args.command == "bracket-history":
        return {"reviews": review_history(connection, args.deck_id, args.version)}
    review_id = save_bracket_review(
        connection, args.deck_id, read_review(args.review_file), args.version
    )
    return {"review_id": review_id, "deck_id": args.deck_id}
