# Changelog

Changes to user-visible behavior, backend contracts, and engineering safeguards
are recorded here. Work remains unreleased until an explicit release is made.
Historical entries below summarize verified Git milestones, not release versions.

## Unreleased

### Added

- Partition the existing 24 controlled cases into five fixed families in
  `raton_equipment_v6`: Equipment (8), damage (6), countering (2), targeting (4),
  and protection (4). Preserve every reviewed expectation and all 12 pinned
  identities. Keep the CLI command and the single bounded Python/JVM process.
- Use a schema-2 manifest plus independently bounded files in
  `config/interactions/`, with code-owned paths, order, and exact case inventory.
  Hash each document's exact parsed bytes and bind the complete path/hash map
  into the suite fingerprint; verify definitions before, within, and after execution.
- Write one bounded `observations-<family>.json` receipt per family. Report
  schema 2 retains the combined checks and adds `partition_results` with receipt
  hashes/statuses. New runs no longer emit a combined `observations.json`.
  Missing/misidentified evidence, failed processes, and conflicting runtime
  metadata cannot pass; a witnessed mismatch fails the combined result.
- Retain the 128 KiB/32-level per-document limits. Size-check generated worker
  inputs before exclusive creation; keep readable combined reports. Organize
  definition, runner, verifier tests, and synthetic helpers into focused files.
- Validation: 261 offline tests and 211 subtests pass. Native CLI integration
  passes all 24 unchanged cases, checks every receipt's hash/size, and verifies
  profile restoration. Black, isort, Pylint, and Bandit pass. Outcome-changing
  replacement-order choices remain the next interaction milestone.

- Expand the controlled suite from 23 to 24 cases in `raton_equipment_v5`.
  Witness normal Equipment attachment, then resolve Tel-Jilad Defiance and let
  native state-based actions detach it. The Equipment remains on the battlefield
  and the same Assassin token loses the +2/+0 bonus.
- Require a case-specific `after_detachment` checkpoint. Independently verify raw
  attachment, target, stack, and stable object ID/zone-timestamp evidence; an
  unattached final state alone cannot pass. Missing/extra checkpoints or malformed
  evidence are inconclusive; witnessed contradictions fail. The original 23 cases
  retain their six-checkpoint contracts and all 12 Oracle identities stay pinned.
- Document bounded suite partitioning and explicit affected-player choice
  recording as prerequisites for the next replacement-order test family.
  Outcome-changing order choices, full casting/priority, and combos remain pending.
- Validation: 246 offline tests and 195 subtests pass, including detachment
  counterexamples, raw identity mutations, and checkpoint boundary checks.
  Native CLI integration passes all 24 cases; Black, isort, Pylint, and Bandit
  pass. No production Forge script correction was needed for these cases.

- Expand the controlled interaction suite from 20 to 23 cases in
  `raton_equipment_v4`. Resolve Tel-Jilad Defiance, Ranger's Guile, and Alexi's
  Cloak against the Assassin token before the Equipment-return ability. Cover
  protection from artifacts preventing attachment, and hexproof/shroud allowing
  an attachment that does not target the creature. Pin all 12 fixture identities.
- Compare native target eligibility for both players using an unplayed nonartifact
  creature-targeting spell. Freeze response target IDs/zone timestamps and stack
  identities, independently binding the original token and remaining reflexive
  trigger while preserving the separate Equipment target. Missing evidence is
  inconclusive; contradictory bindings fail even with matching keyword flags.
- Preserve isolated execution, the unchanged 128 KiB input/receipt cap, source
  hashes (including the new response verifier), and readable state reports.
  Add focused response-verifier tests and explicit casting/priority boundaries.
- Validation: 242 offline tests and 177 subtests pass; the native CLI integration
  test passes all 23 cases. Black, isort, Pylint, and Bandit pass without new
  suppressions. No production Forge script correction was needed for these cases.
  Protection-driven detachment and outcome-changing replacement order remain pending.

- Expand the controlled interaction suite from 16 to 20 cases in
  `raton_equipment_v3`. Add Ground Seal before target selection, activation of
  its static restriction after selection, removal before selection, and a
  targeted Equipment leaving and returning to the graveyard as a new object.
- Pin Ground Seal as a ninth Oracle identity. Record target-card zone timestamps
  and both native roundtrip transitions; independently bind the original target
  across the response. Missing evidence is inconclusive and conflicting raw
  identities fail even with matching summary flags.
- Initialize injected fixture cards with native game timestamps. Keep the worker
  receipt cap at 128 KiB by using compact JSON; generated reports retain
  indented checkpoints. Split verifier tests into focused behavior/boundary groups.
- Validation: 239 offline tests and 153 subtests pass; the native CLI integration
  test passes all 20 cases. Black, isort, Pylint, and Bandit pass. No production
  Forge script correction was needed for these tested cases. Full casting,
  priority, protection, and outcome-changing replacement ordering remain pending.

- Expand the controlled interaction suite from eight to 16 cases in
  `raton_equipment_v2`. Add full combat prevention, noncombat-only prevention with
  a combat control, combat/noncombat damage doubling, combined prevention and
  doubling, and Disallow countering either the initial or reflexive trigger.
- Counter tests place the actual counterspell ability on Forge's stack and resolve
  its counter effect. Record raw stack/target ability IDs and independently bind
  them to the previously observed pending trigger; summary flags alone cannot pass.
- Pin eight fixture card identities and include loyalty in Oracle fingerprints.
  Record active prevention/doubling effects, The Wanderer's starting loyalty,
  and the resolved counterspell's graveyard state. Existing v1 report files remain
  historical; the current command requires the complete v2 suite.
- Validation: 235 offline tests and 129 subtests pass, including new damage and
  counterspell counterexamples and raw-identity failure checks. The native CLI
  integration test passes all 16 cases. Formatting, lint, and security checks pass;
  no production Forge script correction was needed for these tested cases.

- `engine-interactions`: controlled partial-state tests using the actual pinned
  Forge damage, trigger, stack, zone-change, and state-based-action APIs. Eight
  initial Ratonhnhaké꞉ton/Equipment cases cover return/attach, separate target
  timing, unavailable/opponent-owned Equipment, exiled targets, removed tokens
  and sources, creature Equipment, and noncombat damage. All eight pass; no
  production engine-script correction was needed for these tested cases.
- Independent versioned expectations, current Oracle-characteristic fingerprints,
  direct state checkpoints, input/receipt/log/source hashes, and explicit failed
  or inconclusive outcomes. Missing evidence, setup/process failure, and changed
  provenance cannot pass. JSON is bounded to 128 KiB and 32 nesting levels with
  duplicate-field rejection.
- A pinned optional JPype bridge in `requirements-interactions.txt`, invoked in
  an isolated Python child with the existing engine profile lock/restoration,
  stripped environment, wall/log limits, and a JVM heap cap. Ordinary simulation
  commands remain standard-library-only. Partial-state fixtures never become
  production decks, bracket attestations, benchmark lists, or win-rate samples.
- Validation: 231 offline tests and 107 subtests pass, including mutation and
  provenance/failure controls; the new native integration test passes all eight
  direct-state cases. Black, isort, Pylint, and Bandit pass. Salt and interaction
  JSON readers share a structural scanner while retaining their separate limits.

- Runnable CLI workflows: `doctor` checks catalog/runtime readiness, `demo` runs
  an isolated diagnostic pod, `play` imports four explicit reviewed decklists,
  and `simulate` completes or resumes a saved pod with progress and JSON reports.
  Existing legality, bracket, frozen-evidence, execution, and 100,000-attempt
  limits still gate simulation. Reports distinguish failures and interrupted slots.
- A short CLI quick start and a complete diagnostic deck-format example. Demo
  databases/reports remain available for inspection and resume without adding
  fixtures or test reviews to the main deck library. Existing report files are
  never overwritten; JSON stdout is separated from stderr progress.
- Validation: 211 offline tests and 90 subtests pass; all four opt-in live
  integration tests pass. A two-game CLI demo also completed and its saved
  database was reopened successfully without rerunning completed attempts.

### Repository organization

- Group backend modules under `commander/` by catalog, decks, rules, engine,
  simulation, diagnostics, opponents, salt, and CLI. Keep the root Python entry
  point and command names stable. Unit/live tests and shared fixtures now live
  under `tests/`; detailed docs, examples, and versioned configuration have their
  own folders. Update imports, mock targets, discovery, and documentation paths.
- Centralize checkout data/config paths. Preserve the existing catalog, engine
  assets, game history, and private engine settings while moving the latter into
  ignored `config/engine_config.json`. No new runtime dependencies are required.

### Earlier unreleased additions

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
