# Backend foundation requirements

The backend comes first. A UI will be built after these operations have stable
interfaces and tests. CLI commands are the current interface; an HTTP server is
not required to establish the core services.

## Processing order

1. Parse and resolve the supplied decklist within input limits.
2. Validate Commander deck construction against the catalog and pinned rules.
3. Persist a validated deck with its validation evidence.
4. Evaluate declared bracket separately from format legality.
5. Select validated opponents from a versioned benchmark library.
6. Check engine card coverage, then create and execute a simulation job.
7. Persist results, failures, engine settings, deck snapshots, and raw logs.

Steps 4–7 must not run for a deck that has failed or incomplete legality checks.
No simulation or matchmaking service is implemented yet; future callers must use
the saved-deck legality gate before proceeding.

## Required services and acceptance criteria

| Service | Required behavior | Current state |
| --- | --- | --- |
| Card catalog | Stable identities, faces, current source data, atomic refresh | Implemented; manual refresh |
| Legality | Explain failures, record rules/catalog versions, block unverified decks | Implemented for documented scope |
| Deck library | Save validated decks and reload them without trusting stored approval | Implemented; immutable revision snapshots |
| Bracket review | Separate user declaration from verified bracket findings | Game Changer screening and human-attested reviews implemented |
| Opponent library | Ten popular commander configurations per bracket with reviewed lists and source evidence | Immutable source-sample storage implemented; automated acquisition pending |
| Engine adapter | Real Commander rules execution, card-coverage checks, bounded processes | Pending Forge evaluation |
| Simulation jobs | Revalidation, seat/opponent balance, cancellation, progress, resumable results | Pending |
| Results | Wins, losses, draws, failures, uncertainty, traceable logs | Pending |

## Legality contract

`legal` means the represented 100-card starting deck passes this validator's
supported construction rules against the recorded catalog snapshot. `illegal`
means a rule violation was found. `needs_review` means required data or a
construction choice cannot be verified. Only `legal` can pass the gate.

`validate` checks without saving a deck and returns a nonzero exit status if it
does not pass. `deck-add` repeats validation before writing. The backend function
`require_legal_deck(connection, deck_id, version=None)` reloads authoritative saved cards and
revalidates before any future downstream operation. An earlier stored approval
cannot override a later catalog change, ban, or stale-data decision.

Supported checks include deck size, commander eligibility, permitted commander
pairs, color identity, basic land types, Commander legality from Scryfall, and
ordinary/finite/unlimited duplicate-card limits. Card faces use front-face
characteristics for commander eligibility and the catalog's combined identity.

Catalog source timestamps must be timezone-aware, no more than seven days old,
and from an identified Scryfall bulk export. Seven days is a product freshness
policy, not an official Magic rule. Locally imported files with no verified bulk
timestamp cannot certify legality. Old catalogs missing current card details
must be reimported before approval.

Chosen commander colors, selected companions, and supplementary decks are not
implemented. Companion/supplementary sections cannot be silently treated as an
approved configuration. Commander color choices block approval. Sideboard and
maybeboard exports are excluded from the starting deck. This is standard
multiplayer Commander, not Commander Draft, Brawl, Duel Commander, or Rule 0.
New construction mechanics or unknown copy caps require validator work rather
than an override. Bracket compliance and engine compatibility are separate gates.

The pinned rules are the September 25, 2026 Comprehensive Rules. Updating card
data does not automatically update the validator's interpretation of rules.

## Engineering requirements

- Reusable services stay independent of a future UI and transport framework.
- Network adapters, parsers, validation, persistence, and engine processes remain
  separate modules with documented inputs and outputs.
- SQL values are parameterized. Imports and deck writes are atomic.
- Untrusted content is never executable. Input and process limits are explicit.
- Test rules and failure cases offline with controlled card fixtures; perform
  separate integration checks against real catalogs and the selected engine.
- Black, isort, Pylint, Bandit, and tests must pass before each commit.

References:
- https://magic.wizards.com/en/rules
- https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt

## Deck revision contract

A deck ID groups immutable revisions; a revision ID identifies one snapshot and
`version` is its sequence within that deck. Every successful edit appends a
revision. Updates and deletes of revision rows are prohibited by SQLite triggers.
Legacy latest-deck tables remain a compatibility projection and are not the
source used by revision legality checks.

Snapshots retain the original card data and validation evidence. Current legality
is reevaluated from the saved card identities and current authoritative catalog,
so a historical approval never overrides new bans or stale data. Future jobs must
store the selected revision IDs explicitly rather than referencing a moving
latest deck. All revision writes are atomic and acquire a write lock before
validation so a concurrent catalog refresh cannot split the approval/snapshot.

## Bracket review contract

A valid Commander deck can still conflict with a declared bracket. Bracket
screening cannot waive format legality, including for Exhibition/Bracket 1;
Rule 0 agreements are outside this application's current scope.

The automatic check uses the catalog's current boolean Game Changer flags and
the versioned caps in bracket_policy.json. Missing flags require review/data
refresh instead of being treated as false. Passing the cap does not infer deck
intent, combo timing, extra-turn behavior, land denial, or competitive purpose.

A local reviewer can attest to those dimensions with an identity label, rationale,
and explicit boolean checklist. Successful reviews are append-only records tied
to revision ID, snapshot hash, policy hash, and catalog import. The reviewer label
is not authenticated identity. This backend currently assumes a trusted local
operator; future remote access needs authorization before review submission.

`require_bracket_review` requires a recorded human review and reruns legality and
count screening. It rejects stale catalog/policy evidence and never silently
carries approval to a different revision. The diagnostic bracket-check command
always requests human review; the saved attestation appears in bracket-history.
This is a product matchmaking gate, not a Wizards-certified power-level rating.

## Opponent collection contract

Manifests use schema version 1 and explicitly distinguish source-user-declared
brackets from source estimates. The current import path only supports
`source_sample`, with operator-supplied counts and source/author attribution.
It must not be displayed as a verified source-wide or global top-ten ranking.
Ordering is computed only over submitted representatives. Counts must be positive
and their sum cannot exceed the claimed deduplicated sample size.

Collections pin immutable deck revision IDs, commander configurations (including
pairs), snapshot hashes, review IDs, catalog evidence, and policy hash. All members
must be legal, currently reviewed, and in the declared collection bracket. A
refresh race during import fails rather than mixing catalog/policy versions.

`require_collection` rejects partial collections, source evidence older than 30
days, and changed review/catalog/policy evidence. It is a readiness check, not a
simulation submission transaction. Future job creation must bind those exact
collection and revision IDs and recheck evidence in its own transaction.

No production opponents or popularity statistics are fabricated. Source adapters
must establish collection coverage, bracket provenance, deduplication, and actual
list retrieval before automated population of 50 representatives can be claimed.
