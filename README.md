# Commander Simulator

A new Python project for a Commander simulator. The first component is a local
SQLite card catalog sourced from Scryfall. No gameplay engine exists yet. See [PROJECT_PLAN.md](PROJECT_PLAN.md) for the confirmed product direction.

Requires Python 3.10 or newer. No third-party Python packages are required.

## Commands

Run these from the project folder:

```powershell
python commander_simulator.py init
python commander_simulator.py sync
python commander_simulator.py lookup "Sol Ring"
python commander_simulator.py stats
python -m unittest -v
```

If `python` is not available, use an installed interpreter's full path. In
PowerShell, prefix a quoted executable path with `&`.

`sync` downloads Scryfall's Oracle Cards bulk export, which represents unique
card identities with representative printings. It does not include every
printing or translation. Use `sync --kind default_cards` for the default printing
export or `sync --kind all_cards` for every printing/language; these are larger.
Compressed JSON Lines and JSON-array exports are both supported.
An existing export can be imported with `python commander_simulator.py import file.json`.

The database lives at `data/cards.sqlite3`, alongside cached exports. That directory
is excluded from Git. Override the database path with `--database PATH` before
the subcommand. No scheduled updates run; rerun `sync` to refresh the catalog.
Imports update existing records and add new records; they do not delete records
that disappear from a later export. Failed imports roll back their database changes.

## Structure

- `commander_simulator.py`: CLI entry point.
- `database.py`: schema, storage, and exact card/face-name queries.
- `scryfall.py`: bulk download, streaming JSON parsing, and transactional import.
- `test_database.py`: offline database/import tests.

Card identities and printings are separate. Faces belong to printings, preserving
face-specific artwork and Oracle IDs. Rare reversible records without a root
Oracle ID use their Scryfall printing ID as the catalog key. Complete source JSON
is retained for each printing, including fields not yet exposed as SQL columns.

Card data and rules text do not execute card abilities; game mechanics will be
implemented separately as the project grows.

Sources: [Scryfall bulk data](https://scryfall.com/docs/api/bulk-data) and
[official card field definitions](https://github.com/scryfall/api-types/blob/main/src/objects/Card/CardFields.ts).

## Initial validation

Eight offline tests pass, including reprint identity, face queries, repeat imports,
rollback, Unicode, malformed input, and compressed JSON Lines. The first live
Oracle import on October 25, 2026 loaded 38,997 catalog records and 6,659 faces.
These are Scryfall catalog records, not a count of Commander-legal cards.

## Deck import

```powershell
python commander_simulator.py deck-add my-deck.txt --name "My deck" --bracket 3 --commander "Teysa Karlov"
python commander_simulator.py decks
```

Text files accept `1 Card Name` lines (or `1x Card Name`). A Commander section
can replace `--commander`; repeat the flag for a commander pair. Sideboards and
maybeboards are excluded. Flat lists may include the specified commander; one
copy is moved to the command zone. Brackets are user declarations, from 1 to 5.
Basic checks cover card resolution, 100-card count, Commander legality, color
identity, and ordinary singleton duplicates. These checks do not certify full
Commander legality or bracket compliance. Commander eligibility, pairing, and
special copy limits remain pending engine validation. There is no simulation
command or GUI upload yet.
