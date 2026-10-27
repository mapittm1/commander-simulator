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
weaknesses, especially for combo/control strategies. Release 2.0.15 is installed
locally behind a single-game adapter with pinned JAR/script hashes, current
legality/bracket gates, explicit revisions, script coverage checks, bounded
execution, raw logs, and conservative result parsing. A real four-player diagnostic
using simple legal fixtures completed successfully. This does not validate the
entire card catalog or establish AI proficiency for every strategy.

Do not silently substitute unsupported cards. Report failed games and unsupported
cards separately from wins/losses. Keep engine version and deck snapshots with
every run. Preserve raw logs. Rotate seats and opponent combinations, record
draws/timeouts, and report uncertainty and strategy coverage with win rates.
AI match results measure performance against that engine's pilots; they are not
a calibrated estimate of performance against human pods.

Catalog-wide immutable coverage audits now quantify exact script/export matches
and gaps by format-legality scope. Explicit engine-declared Universes Within
aliases are supported. Missing matches still include in-game-only faces and do
not establish the count of unsupported playable cards. Oracle conformance and
AI competence remain unverified; source acquisition and card-interaction testing
are still needed before comprehensive support can be claimed.

References:
- https://github.com/Card-Forge/forge/wiki/AI
- https://github.com/Card-Forge/forge

## Implementation milestones

A controlled direct-state Ratonhnhaké꞉ton/Equipment suite now verifies 41
specific trigger, target, return/attachment, removal, prevention, damage-doubling,
counterspell, graveyard-target restrictions, target zone-roundtrip, and
protection/hexproof/shroud attachment, later detachment, and both orders of
a damage-doubling/prevention pair, with combat/noncombat controls, plus
selected paid-casting and admission checks. See
[INTERACTION_TESTS.md](INTERACTION_TESTS.md). These fixtures model resolved
between-trigger effects; complete legal casting and priority handling remain
separate work. The suite does not certify the rest of the card catalog.

The 41 interaction cases now run as nine fixed families with individually bounded
definitions and receipts. Manifest/schema-2 reporting preserves the 128 KiB
per-document input/receipt limit, all existing expectations, and a combined
all-families result. A fixed test-only Java controller now exercises and records
both affected-player choices for Furnace of Rath/Urza's Armor, bound to raw
replacement identities and final damage/trigger observations. This proves those
controlled branches without certifying AI strategy or general replacement rules.
The casting family now records native legality/payment predicates and calls the
paid-casting API for eligible proposals, including native unaffordable/off-color
rejection. Phase and priority ownership are controlled snapshots. Next work should
extend native priority coverage to longer chains, additional/variable costs,
simultaneous effects/APNAP choices, and representative combos.

The native priority family now verifies retained priority, four-player pass cycles,
paid Disallow responses and unaffordable-response passes. Fixed scripted choices
exercise Forge’s actual loop; default AI strategy remains a separate concern.
The chain family additionally verifies a paid third-player counterspell, a second
complete pass cycle before the surviving original spell resolves, and an
unaffordable second-counterspell control. Actual pending targets and unspent
mana are bound to every intermediate checkpoint; countered spells stay paid.

The CLI is now directly runnable through `doctor`, an isolated real-engine
`demo`, four-file `play`, and reviewed-pod `simulate` with progress, resume, and
saved reports. See [QUICKSTART.md](QUICKSTART.md). Opponents are explicitly
provided; the benchmark acquisition and web UI milestones remain pending.
Backend code, tests, config, examples, and docs are grouped into folders.

The current rules-testing milestone adds frozen support-gap triage and a limited
live all-Forest regression pod. Copied-face indexing removes 20 false gaps; the
latest local audit has 101 unmatched Commander-format records, with 47 requiring
investigation after excluding 54 confirmed supplementary/meld-result objects.
One meld role remains uncertain; the spelling discrepancy now has an explicit
Oracle-ID/hash-bound review and a live export/cast/trigger/token regression.
Three log witnesses cover commander tax, an attack trigger's combat effect, and
positive-life commander-damage loss. These do not establish full rules correctness.
Next engine work: resolve the meld printing-role discrepancy, then build controlled
fixtures for stack priority, targeted removal, replacement effects, zone changes,
and combos before expanding strategy-level diagnostics or claiming broad support.

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
Opponent collections and local simulation batches now bind explicit revisions.
Automatic representative acquisition and web job scheduling remain pending.

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

## Self-hosted product and diagnostics

The intended product is a self-hosted web application with an attractive UI.
Backend correctness comes first. Users upload a deck, select a bracket and run
count, and receive actionable optimization findings with supporting game events.
Support counts such as 100, 1,000, and 10,000, with a hard maximum of 100,000 per
batch enforced by the backend. Persistent local batches now support explicit
four-deck pods, frozen evidence, deterministic seeds/seat rotation, bounded
foreground worker chunks, cooperative cancellation, and durable outcome counts.
Automatic opponent selection, crash recovery, and web scheduling remain pending.

Diagnostics should cover mana/color access, missed land drops, uncastable cards,
and gameplay bottlenecks with explicit definitions and coverage. Targeting
analysis should identify observed attacks/removal and preceding events.
Human dislike and "unfun" are subjective: distinguish attributed card tags,
observed AI actions, and hypotheses about why a player was focused. Never present
correlation or AI targeting as proof of human intent.

EDHREC's annual community salt ratings from https://edhrec.com/top/salt are the
chosen social-opinion reference. Attributed local snapshots and legal-deck salt
profiles now preserve ratings, voting year, source time/link, and partial coverage.
These can flag potential social friction alongside gameplay observations.
They do not establish causal aggression or supply calibrated targeting weights.
Automatic source synchronization and an optional modeled opponent policy remain
separate future work requiring a supported access path and explicit assumptions.

The initial observation layer now normalizes supported log events with source
lines and reports land plays, casts, complete-main-turn land-play observations,
and attack assignments. Batch aggregation remaps seats and excludes partial or
missing diagnostic coverage. These descriptive metrics do not yet establish mana
issues, targeted-removal patterns, causal gameplay bottlenecks, or social reactions.

Before exposing the service: authenticated ownership checks for decks/jobs,
bounded text uploads, escaped rendering, CSRF protection where cookies are used,
private generated storage, isolated workers without credentials or network,
hard CPU/memory/disk quotas, cancellation, retention, rate limits, and atomic job
admission tied to immutable deck/opponent evidence. Store actual attempted,
completed, and failed counts, seeds, seat rotation, engine version, and metric
coverage. Failed games must not silently disappear from reported rates.
