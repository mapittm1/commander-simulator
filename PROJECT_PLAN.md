# Commander Simulator product plan

## Confirmed scope

A user imports a Commander decklist, declares a bracket, and tests it in
four-player pods against opponents in the same bracket. The benchmark library
will contain ten popular commanders per bracket, each with a representative
decklist. Commander pairs count as a single commander configuration.

The target is a full rules engine, not a score-based heuristic substitute.

## Brackets

1. Exhibition
2. Core
3. Upgraded
4. Optimized
5. cEDH

Bracket declaration, source-site bracket labels, and validation findings must
remain separate. A commander can appear in multiple brackets with different
lists. Brackets describe intended gameplay as well as card choices; card counts
alone do not certify bracket compliance. Version rules and Game Changers data.

Source: https://magic.wizards.com/en/formats/commander

## Benchmark acquisition

The requested output is 50 representative lists, not ten globally popular
commanders copied into every bracket. Rank commander configurations using deck
counts within each bracket from a specified source and time window. Record
source URL, collection date, bracket provenance, population/sample size, count,
rank, and representative list hash. Deduplicate copied lists where possible.
Do not label a sampled ranking as a global popularity ranking.

Select an actual complete public list for each ranked commander configuration.
Validate card resolution and deck construction, then audit its declared bracket.
Retain author attribution and the original link. Do not substitute an average
deck without labeling it as a generated composite.

Source investigation is incomplete. Verified EDHREC commander pages expose
bracket-specific counts and say that their filtered data includes user-set
brackets rather than auto-detected brackets. For example:
https://edhrec.com/commanders/teysa-karlov/upgraded

A complete per-bracket commander ranking and reliable provider export interface
still need verification. The current opponent importer accepts operator-supplied
local manifests referencing already legal, reviewed deck revisions. It does not
claim global rankings or automatically download lists. No production benchmark
lists have been downloaded or ranked yet.

References:
- https://edhrec.com/commanders
- https://archidekt.com/search/decks

## Simulation engine

Evaluate Forge as an external process behind a Python adapter. Forge documents
headless multiplayer Commander simulation, broad card support, and known AI
weaknesses, especially for combo/control strategies. It is not installed or
integrated yet. Verify the selected release's command options, Java requirements,
card coverage, deck validation, logs, and timeout behavior before using it.

Do not silently substitute unsupported cards. Report failed games and unsupported
cards separately from wins/losses. Keep engine version and deck snapshots with
every run. Preserve raw logs. Rotate seats and opponent combinations, record
draws/timeouts, and report uncertainty and strategy coverage with win rates.
AI match results measure performance against that engine's pilots; they are not
a calibrated estimate of performance against human pods.

References:
- https://github.com/Card-Forge/forge/wiki/AI
- https://github.com/Card-Forge/forge

## Implementation milestones

1. Card catalog: implemented, populated, tested.
2. Backend legality and deck import: CLI validation and a mandatory save gate
   implemented, including commander eligibility/pairing and duplicate limits.
   See BACKEND_REQUIREMENTS.md for supported scope, freshness policy, and remaining
   mechanics. Establish backend services before building a UI.
3. Opponent acquisition: verify source/ranking method, import and audit 50 lists.
4. Forge integration: install a pinned version, validate deck exports, run a real
   four-player match, and verify result parsing against logs.
5. Batch runner: opponent/seat rotation, persisted runs, per-match results,
   uncertainty, coverage, timeout and failure accounting.
6. User interface: deck upload, bracket selection, opponent preview, simulation
   progress, downloadable results, and replay/log inspection.

Current CLI imports are stored locally in SQLite and are not uploaded to a server.

## Backend-first sequencing

Before opponents or simulation, every starting deck must pass legality validation.
Only the `legal` state proceeds; `illegal` and `needs_review` stop processing.
See BACKEND_REQUIREMENTS.md for the backend contracts and acceptance criteria.

## Deck library milestone

Validated decks now have immutable numbered revisions, including frozen Oracle
records, bracket declarations, attribution, hashes, and validation evidence.
Backend and CLI operations can inspect history and revalidate a selected version.
Versioned opponent data and simulation jobs remain separate upcoming milestones.

## Bracket review milestone

Added a separate bracket-review service: automatic Game Changer cap checks,
explicit human review of gameplay expectations, immutable review history, and a
freshness gate bound to the selected revision and current catalog/policy.
Representative opponent selection remains the next data-source milestone.

## Opponent-library storage milestone

Implemented immutable source-sample collection snapshots, attributed representative
revisions, deterministic ordering of reported commander counts, and a readiness
gate requiring ten distinct reviewed representatives and current evidence. Partial
collections remain inspectable. Network adapters and collection of the actual 50
benchmark lists remain pending; fixtures are used only in isolated tests.
