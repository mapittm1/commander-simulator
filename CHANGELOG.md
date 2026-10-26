# Changelog

Changes to user-visible behavior, backend contracts, and engineering safeguards
are recorded here. Work remains unreleased until an explicit release is made.
Historical entries below summarize verified Git milestones, not release versions.

## Unreleased

### Added

- Repository-owned identity review registry with exact Oracle-ID guards, archive
  and script hashes, bounded input, duplicate/conflict rejection, and preserved
  provenance in audits and game/batch evidence. Ratonhnhaké꞉ton now exports to
  the reviewed Forge spelling, Ratonhnhaké:ton; no fuzzy substitution is used.
- Multicolor basic-land fixtures and a live regression for that reviewed export,
  casting, combat-damage trigger resolution, and later Assassin-token participation.
  Equipment recursion, hexproof, and full card conformance remain unverified.
- Meld-gap diagnostics expose the representative and related printing IDs while
  preserving the review block. Classifier version 2 is frozen into new records;
  historical backlog reports keep the stored classifier version.
- Local audit now matches 32,015 of 32,116 Commander-format records, leaving
  101 gaps: 54 confirmed supplementary/meld-result objects and 47 investigation
  items, including the still-unresolved Ragnarok printing-role discrepancy.
- Validation: 199 offline tests and 90 subtests pass, plus all three opt-in
  live-engine integration tests. Black, isort, Pylint, and Bandit pass.

- `engine-audit-gaps`: bounded, immutable engineering triage for new version-2
  audits, separating supplementary/meld-result records from investigation items.
  Historical classifications do not change with later catalog edits. Spelling
  candidates are diagnostic only and remain blocked from export.
- `engine-conformance`: a disposable legal all-Forest pod and three limited
  real-engine log regressions for commander tax, a resolved combat trigger, and
  commander-damage loss with positive life. Missing exercise is inconclusive;
  failures and inconclusive results return nonzero. Reports preserve hashes and
  source lines. Full rules conformance and AI proficiency remain unverified.
- Counterexample tests for witnesses, bounded backlog pages, frozen history,
  sticker-sheet construction, and copied/split/specialized face indexing.
- Validation: 185 offline tests and 78 subtests pass; both opt-in live-engine
  integration tests pass separately. Black, isort, Pylint, and Bandit pass.

### Fixed

- Index explicit Forge copied-face references, reject missing targets, preserve
  standalone canonical names over optional face aliases, and avoid promoting
  specialized faces. The local audit gains 20 matches: 32,014 of 32,116
  Commander-format records. Of 102 gaps, 54 are confirmed supplementary/meld
  results and 48 need investigation, including an unresolved meld role.
- Validator version 3 rejects sticker sheets from starting decks; format legality
  alone does not make these supplementary objects valid deck slots. Malformed
  meld component metadata remains blocked for review without crashing.

### Earlier unreleased additions

- EDHREC salt reference support through bounded local sample import, immutable
  source snapshots, exact catalog/face matching, and `deck-salt` advisory reports.
  Ratings retain the 0–4 scale, voting year, observation date, source attribution,
  resolution catalog, and explicit unknowns. New commands: `salt-import`,
  `salt-show`, and `deck-salt`; a three-card example manifest is included.
- Validation for finite/precise scores, rank order, duplicate fields/names/ranks,
  timestamps, source URL, file/card/numeric limits, and ambiguous identities.
  Legal-deck checks precede analysis; immutable snapshots keep prior ratings intact.
- Salt and deck import share the existing game-card candidate rules, avoiding
  false ambiguity from art-only records while retaining banned-card identification.
- Rated/unrated quantities, known-rating subtotal and mean, and stale-source
  labeling. These descriptive aggregates are not an official EDHREC deck score
  or an aggression probability. Forge behavior and observed targeting are unchanged.
- A 100-card reference-page sample was imported locally for verification; full
  source data remains outside Git. Automatic website synchronization is not
  implemented pending a supported access path.

- Catalog-wide engine script/export audits with immutable per-card snapshots,
  current catalog provenance, pinned engine hashes, bounded historical pages,
  and separate Commander-legal/other/unknown counts. New commands: `engine-audit`,
  `engine-audit-show`, and `engine-audit-cards`. Audits do not run Java or certify
  gameplay correctness. Missing export matches include non-startable faces.
- Exact Universes Within aliases declared by engine scripts, including paired
  faces. These export the original script name and reject ambiguous mappings;
  no fuzzy matching or inferred card replacements are introduced. The local
  audit gained 29 matches, leaving 122 unmatched Commander-format records.
- Audit regression tests cover count/page bounds, provenance, race detection,
  rollback, immutability, integrity checks, historical retention, unsafe exports,
  and inspection without an installed engine.

- Bounded parsing of the pinned Forge log dialect into `events.jsonl`, retaining
  original physical log line numbers, card names, seats, and object identifiers.
  Grouped attackers, multiple-defender continuation lines, and targeted-cast
  descriptions follow the pinned formatter; non-player attack targets remain partial.
- Per-game `diagnostics.json` with the raw log's SHA-256, parser version,
  coverage, and descriptive land-play, cast, and attack-assignment observations.
- `batch-diagnostics JOB_ID` aggregates covered completed games, maps rotated
  seats back to original deck order, and reports included/excluded game counts.
  Missing diagnostics remain unavailable rather than becoming zero observations.
- Parser limits: existing 8 MiB log cap, 10,000 normalized events, 4,096-character
  candidate lines, 20 malformed-line references, and bounded evidence samples.
  Individual attack lists are capped at 100 creatures.
- Regression coverage for incomplete turns, off-turn events, malformed seats,
  unknown targets, parser limits, physical line references, output failures,
  legacy data, and rotated batch aggregation. The opt-in real-engine smoke test
  now verifies diagnostic artifacts and two-game batch aggregation.
- Validated with the shared Black/isort/Pylint/Bandit/pytest gate and an opt-in
  real single-game run plus a two-game pause/resume batch.

### Changed

- Commander validator version 2 rejects meld results as starting cards or
  commanders, while unverifiable meld roles block approval. Meld fronts remain
  supported subject to the other construction rules. Bracket rechecks record
  current rules and validator versions, so changed frozen batch evidence must
  be recreated. The verified rules reference now links to the pinned PDF.
- The live engine smoke fixture includes an explicit variant alias and checks
  its actual generated export name before validating batch diagnostics.
- Library and lookup JSON output now falls back to lossless escapes on legacy
  terminal encodings, preventing valid Unicode card names from breaking pages.

- New engine results include diagnostic coverage; failure to write diagnostics
  does not replace the actual game's win/draw/error outcome.
- README documents diagnostic definitions, commands, artifacts, and limitations.
  CONTRIBUTING requires keeping README and CHANGELOG current with project changes.

### Scope and remaining work

- A turn without a logged land play does not prove a missed land drop or mana
  problem. Attack assignments do not establish threat assessment or human motives.
- Color access, uncastable cards, targeted removal, subjective hate/unfun labels,
  and causal optimization recommendations are not implemented by these metrics.
- Existing results are retained; no historical game results are rewritten.
- Self-hosted web UI, isolated workers, hard host quotas, automatic crash recovery,
  automatic benchmark acquisition, and comprehensive card/AI validation remain pending.

## Earlier milestones

- `9e2d757`: Persistent fixed-pod batches with a backend cap of 100,000 attempts,
  frozen inputs, deterministic seeds/seat rotation, resumable foreground chunks,
  cooperative cancellation, worker ownership, and separate outcome/failure counts.
- `07e76f7`: Pinned Forge single-game adapter with legality/bracket gates,
  script-coverage checks, bounded shell-free execution, minimal child environment,
  profile locking/restoration, preserved logs, and security regression tests.
- `2d444c8`: Pinned pytest discovery and execution in the shared local, commit-hook,
  and CI quality gate alongside Black, isort, Pylint, and Bandit.
- `3aa31c5`: Immutable, attributed source-sample opponent collections with readiness
  checks. Automatic acquisition of the requested 50 real representatives remains pending.
- `f61dc81`: Game Changer screening, auditable human bracket reviews, and current
  catalog/policy evidence checks for immutable deck revisions.
- Earlier foundation: local Scryfall catalog, bounded deck parsing, Commander
  construction validation, immutable deck history, and backend-first requirements.
