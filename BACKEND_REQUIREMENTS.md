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
| Deck library | Save validated decks and reload them without trusting stored approval | Implemented; immutable revisions pending |
| Bracket review | Separate user declaration from verified bracket findings | Declaration stored; review pending |
| Opponent library | Ten popular commander configurations per bracket with reviewed lists and source evidence | Source verification pending |
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
`require_legal_deck(connection, deck_id)` reloads authoritative saved cards and
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
