"""Copy named authoritative cards into isolated native test catalogs."""

import json

from commander.catalog.database import store_card
from commander.rules.legality import require_current_catalog


def copy_catalog_records(source, fixture, names):
    """Retain the real import timestamp and card records; never forge freshness."""
    catalog = require_current_catalog(source)
    with fixture:
        for name in names:
            row = source.execute(
                "SELECT raw_json FROM cards JOIN card_details USING(card_id) WHERE name=?",
                (name,),
            ).fetchone()
            if row is None:
                raise ValueError("Missing native fixture card: " + name)
            store_card(fixture, json.loads(row[0]))
        fixture.execute(
            "INSERT INTO imports(source,source_updated_at,records) VALUES (?,?,?)",
            (catalog["source"], catalog["source_updated_at"], len(names)),
        )
    return catalog
