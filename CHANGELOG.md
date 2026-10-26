# Changelog

Changes to user-visible behavior, backend contracts, and engineering safeguards
are recorded here. Work remains unreleased until an explicit release is made.
Historical entries below summarize verified Git milestones, not release versions.

## Unreleased

### Added

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
