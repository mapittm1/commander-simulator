"""Import a local four-deck pod using explicit human attestations and existing gates."""

from pathlib import Path
from sqlite3 import Connection

from commander.decks.deck_store import save_deck
from commander.decks.decks import prepare_deck, read_decklist
from commander.engine.forge_config import EngineSettings
from commander.engine.forge_decks import card_index, coverage
from commander.rules.brackets import require_review_attestation, save_bracket_review


def import_reviewed_pod(
    connection: Connection,
    settings: EngineSettings,
    files: list[Path],
    bracket: int,
    review: dict,
) -> list[tuple[int, int]]:
    """Preflight all deck files before imports; never infer or fabricate human approval."""
    if len(files) != 4:
        raise ValueError("Provide exactly four deck files, with your deck first")
    require_review_attestation(review)
    prepared = []
    for path in files:
        deck = prepare_deck(connection, read_decklist(path), [])
        report = deck["legality"]
        if report["status"] != "legal":
            reasons = report.get("errors", []) + report.get("blockers", [])
            raise ValueError(f"{path.name}: {report['status']}: " + "; ".join(reasons))
        prepared.append(deck)
    settings.verify()
    index = card_index(settings.home / "res/cardsfolder/cardsfolder.zip")
    for number, deck in enumerate(prepared):
        report = coverage({"id": number, "cards": deck["cards"]}, index)
        if report["status"] != "supported_scripts":
            raise ValueError(
                f"{files[number].name}: unsupported engine cards: "
                + ", ".join(report["missing"])
            )
    seats = []
    for path, deck in zip(files, prepared):
        deck_id = save_deck(connection, path.stem, bracket, deck)
        save_bracket_review(connection, deck_id, review, 1)
        seats.append((deck_id, 1))
    return seats
