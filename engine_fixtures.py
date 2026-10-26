"""Disposable legal engine fixtures; never production decks or benchmark attestations."""

import json
import tempfile
from collections.abc import Iterator
from contextlib import closing, contextmanager
from pathlib import Path
from sqlite3 import Connection

from brackets import load_policy, save_bracket_review
from database import connect, store_card
from deck_store import save_deck
from decks import prepare_deck
from legality import require_current_catalog

FOREST_POD = {
    "Ghalta, Primal Hunger": "Forest",
    "Goreclaw, Terror of Qal Sisma": "Forest",
    "Surrak, the Hunt Caller": "Forest",
    "Ruxa, Patient Professor": "Forest",
}


@contextmanager
def legal_fixture_pod(
    source: Connection, leaders: dict[str, str]
) -> Iterator[tuple[Connection, list[tuple[int, int]]]]:
    """Copy only real fixture records, then run normal legality and bracket gates."""
    if source.in_transaction:
        raise ValueError("Finish the active transaction before preparing a fixture")
    with (
        tempfile.TemporaryDirectory() as folder,
        closing(connect(Path(folder) / "fixture.sqlite3")) as fixture,
    ):
        names = list(dict.fromkeys([*leaders, *leaders.values()]))
        with source, fixture:
            source.execute("BEGIN")
            catalog = require_current_catalog(source)
            for name in names:
                row = source.execute(
                    "SELECT raw_json FROM card_details JOIN cards USING(card_id) WHERE name=?",
                    (name,),
                ).fetchone()
                if row is None:
                    raise ValueError("Required engine fixture card is absent: " + name)
                store_card(fixture, json.loads(row[0]))
            fixture.execute(
                "INSERT INTO imports(source,source_updated_at,records) VALUES (?,?,?)",
                (catalog["source"], catalog["source_updated_at"], len(names)),
            )
        seats = []
        for leader, basic in leaders.items():
            deck_id = save_deck(
                fixture,
                "Disposable engine fixture",
                2,
                prepare_deck(fixture, f"99 {basic}", [leader]),
            )
            save_bracket_review(
                fixture,
                deck_id,
                {
                    "reviewer": "Automated software test fixture only",
                    "rationale": "99 basic lands and one creature; not a production attestation.",
                    "checks": dict.fromkeys(load_policy()["review_checks"], True),
                },
            )
            seats.append((deck_id, 1))
        yield fixture, seats
