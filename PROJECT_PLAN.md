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

Source investigation is incomplete. EDHREC's public commander ranking exposes
overall deck counts, but its inspected ranking page did not expose a bracket
filter. Archidekt exposes bracket labels and deck searches, but a stable import
interface and bracket-level commander aggregation still need to be verified.
No benchmark lists have been downloaded or ranked yet.

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
2. Deck import: local text-file CLI and preliminary checks implemented. GUI/file
   upload, provider links, commander eligibility/pairing, complete copy limits,
   and bracket validation remain to be built.
3. Opponent acquisition: verify source/ranking method, import and audit 50 lists.
4. Forge integration: install a pinned version, validate deck exports, run a real
   four-player match, and verify result parsing against logs.
5. Batch runner: opponent/seat rotation, persisted runs, per-match results,
   uncertainty, coverage, timeout and failure accounting.
6. User interface: deck upload, bracket selection, opponent preview, simulation
   progress, downloadable results, and replay/log inspection.

Current CLI imports are stored locally in SQLite and are not uploaded to a server.
