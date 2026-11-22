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

The Python adapter uses a locally reviewed Forge 2.0.16 build with pinned JAR,
card resources, source patches and runtime hashes. Legality and bracket gates,
explicit deck revisions, bounded execution, raw logs and conservative result
parsing precede reported results. Real four-player fixtures and selected native
rules checks run separately from offline Python tests. See [README.md](../README.md)
for the currently selected build and its validation evidence; a staged candidate
is not selected permanently until its required runtime checks pass. Selected
checks do not establish complete card correctness or AI proficiency for every
strategy.

Do not silently substitute unsupported cards. Report failed games and unsupported
cards separately from wins/losses. Keep engine version and deck snapshots with
every run. Preserve raw logs. Rotate seats and opponent combinations, record
draws/timeouts, and report uncertainty and strategy coverage with win rates.
AI match results measure performance against that engine's pilots; they are not
a calibrated estimate of performance against human pods.

Catalog-wide coverage audits quantify exact script/export matches against a
frozen format-legality snapshot. The reviewed construction probe loads 32,636
starting identities; seven other catalog identities are nonstarting meld results.
Oracle-ID and script-hash reviews bind the supported spelling aliases to each
exact card archive. Construction proves loading and ability parsing, not rules
correctness. Continue testing native interactions and AI decisions before claiming
comprehensive support; distinguish the two when reporting evidence.

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

The earlier 101-unmatched-record audit and meld-role investigation are historical.
The current reviewed catalog probe and its nonstarting meld exclusions are recorded
in the runtime evidence linked from [README.md](../README.md). Keep the earlier
findings and their resolutions as provenance rather than treating those counts
as the current backlog.

Native regressions now cover selected stack, payment and restoration paths,
replacement effects, zone changes, copied games, private information and text
changes. Continue closing concrete failures with reproducible before/after cases.
Remaining work includes unverified card compositions, cipher and information-use
policies, representative combos and wider rules/AI coverage. A script match or a
completed game does not resolve those requirements.

1. Card catalog: implemented, populated, tested.
2. Backend legality and deck import: CLI validation and a mandatory save gate
   implemented, including commander eligibility/pairing and duplicate limits.
   See BACKEND_REQUIREMENTS.md for supported scope, freshness policy, and remaining
   mechanics. Establish backend services before building a UI.
3. Opponent acquisition: verify source/ranking method, import and audit 50 lists.
4. Forge integration: pinned runtime installation, validated exports, real
   four-player execution and conservative result parsing are implemented. Continue
   interaction and AI validation against each exact candidate build.
5. Batch runner: fixed-pod execution, rotating seats, bounded resumable chunks and
   persisted attempt/results accounting are implemented, with a 100,000-game cap.
   Opponent scheduling, crash recovery, uncertainty and causal diagnostics remain
   pending; see [BACKEND_REQUIREMENTS.md](BACKEND_REQUIREMENTS.md).
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
