# Commander Simulator

A new Python project for a Commander simulator. The first component is a local
SQLite card catalog sourced from Scryfall. No gameplay engine exists yet. See [PROJECT_PLAN.md](PROJECT_PLAN.md) for the confirmed product direction.

The application requires Python 3.10 or newer and no third-party runtime packages.
Development/testing uses Python 3.12 and the pinned requirements-dev.txt tools.

## Commands

Run these from the project folder:

```powershell
python commander_simulator.py init
python commander_simulator.py sync
python commander_simulator.py lookup "Sol Ring"
python commander_simulator.py stats
python -m pytest -v
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
The legality gate checks deck size, commander eligibility and pairing, color
identity, basic land types, ban/format status, and duplicate-card limits. Checks
use the pinned rules and recorded Scryfall catalog snapshot. Invalid decks and
unverified conditions cannot be saved as validated decks. Bracket review and
engine compatibility remain separate requirements.

Check a list without saving it:

```powershell
python commander_simulator.py validate my-deck.txt --commander "Teysa Karlov"
```

This prints a structured legality report and exits nonzero for `illegal` or
`needs_review`. Freshness policy requires a verified Scryfall bulk timestamp no
more than seven days old. Run `sync` to refresh the catalog. Commander color
choices, selected companions, and supplementary-deck configuration are not yet
supported. The future UI follows the backend; see
[BACKEND_REQUIREMENTS.md](BACKEND_REQUIREMENTS.md) for scope and processing order.

## Code quality and security

[CONTRIBUTING.md](CONTRIBUTING.md) defines readability, design, testing, and secure
coding requirements. Decklists are limited to 1 MiB and 2,000 lines; card names
and quantities are bounded before resolving or expanding them. Deck names are
validated, database writes use SQL parameters, and imports run in transactions.
Future remote uploads and engine execution require additional controls before
being exposed. This project has not undergone an independent security audit.

## Enforced Python standards

Use Python 3.12 for development tools. Install them with
`python -m pip install -r requirements-dev.txt` in a virtual environment.
Run `python tools/check.py` to check Black formatting, isort imports, Pylint,
Bandit security rules, and pytest unit tests. `python tools/check.py --fix` applies
formatting first. Install the commit hook with `python -m pre_commit install`.
See [CONTRIBUTING.md](CONTRIBUTING.md) for Windows commands and check details.
GitHub Actions runs the same gate for pushes and pull requests. Server-side merge
blocking still requires branch protection to mark the quality check as required.

## Versioned deck library

```powershell
python commander_simulator.py deck-add my-deck.txt --name "My deck" --bracket 3 --commander "Teysa Karlov"
python commander_simulator.py deck-revise 1 revised-deck.txt --commander "Teysa Karlov"
python commander_simulator.py deck-history 1
python commander_simulator.py deck-show 1 --version 1
python commander_simulator.py deck-check 1 --version 1
```

Replace `1` with the deck ID returned by `deck-add`. `deck-revise` returns the new
revision ID; each deck also has sequential version numbers. `--bracket` on a
revision is optional and otherwise retains the latest declaration. Source URLs
are retained as attribution metadata and are not fetched.

Each revision freezes card identities, names, quantities, zones, Oracle data,
bracket, source URL, and validation evidence. Database triggers reject revision
updates and deletes. Content hashes verify snapshot integrity on reads. The
same list may be saved again as a new revision to capture a new catalog snapshot.

`deck-show` displays the saved snapshot and historical validation. `deck-check`
revalidates that list against the current catalog; it can fail after a ban or
when card data becomes stale. Backend callers must recheck the selected version
before matching or running it. These read/validation commands emit a single
JSON document. There is still no simulation command or UI.

Existing unversioned decks migrate once, retaining their deck IDs. Their first
Oracle snapshot is captured at migration time; it cannot reconstruct historical
Oracle text that was never stored. Storage service calls own their transactions
and reject initialization inside an already-active caller transaction.

## Bracket screening and human review

```powershell
python commander_simulator.py bracket-check 1 --version 1
python commander_simulator.py bracket-review 1 my-review.json --version 1
python commander_simulator.py bracket-history 1 --version 1
```

`bracket-check` is diagnostic: it rechecks Commander legality first, counts Game
Changers in every zone using the current Scryfall catalog, and reports conflicts
or `needs_review`. It intentionally does not approve gameplay intent. Game
Changer caps are 0 for brackets 1–2, 3 for bracket 3, and unlimited for 4–5, per
[Wizards' Commander guidance](https://magic.wizards.com/en/formats/commander).
Policy, checklist, and source links live in `bracket_policy.json`.

Copy `bracket_review.example.json` to a local file, provide a reviewer name and
rationale, and set a checklist item to true only after reviewing that aspect of
the specific deck and bracket. Every item must be confirmed; a human claim cannot
override a count conflict, missing catalog flags, or failed Commander legality.
The sample deliberately starts with every check false.

Saved reviews are immutable and tied to a deck revision, policy fingerprint, and
catalog import. The backend gate `require_bracket_review` rechecks current data;
a new revision, policy change, or catalog refresh requires another review. A
`reviewed` result means automated screening plus a recorded human attestation,
not an automated proof of deck power. Reviewer names are local labels, not
verified identities or user authentication. No arbitrary deck URL is fetched.
JSON review files are limited to 32 KiB and have a strict schema.

## Opponent collection snapshots

```powershell
python commander_simulator.py opponents-import my-opponents.json
python commander_simulator.py opponents
python commander_simulator.py opponents-show 1
python commander_simulator.py opponents-check 1
```

Start from `opponent_manifest.example.json` and replace every placeholder with
observed source evidence and existing, reviewed deck IDs/versions. The importer
accepts local JSON only (maximum 256 KiB); it does not fetch attribution URLs.
Manifest schema version 1 records source, sample size, observation time/window,
source bracket provenance, author, list link, and observed commander deck counts.
The source counts and attribution are operator-supplied claims, not verified by
this importer. Commander pairs count as one configuration.

Each collection freezes its representative revision IDs, review evidence, and
hashes. Ranks order only the submitted representatives by reported counts, with
deterministic ties; they are not certified global or source-wide top-ten ranks.
Duplicate commander configurations, mismatched brackets, invalid decks, and
missing/stale bracket reviews are rejected.

Partial collections can be preserved and inspected. The readiness gate requires
ten distinct representatives, valid current reviews, and source observations no
more than 30 days old. Thirty days is a product freshness policy. Catalog/policy
updates require refreshed review evidence and a new collection. Earlier collections
remain intact when decks get new revisions.

Automatic source collection and the requested 50 real benchmark lists are not
implemented yet. EDHREC's individual commander pages do expose user-set bracket
counts, but a complete bracket ranking and stable decklist import interface have
not been verified. See PROJECT_PLAN.md for the source investigation status.

## Pytest

Install the development dependencies into `.venv`, then run:

```powershell
.\.venv\Scripts\python.exe -m pytest -q
.\.venv\Scripts\python.exe -m pytest test_opponents.py -v
```

The existing unittest-based suites run directly under pytest. The full quality
runner also uses pytest, including when invoked by pre-commit and GitHub Actions.
