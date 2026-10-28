# Backend foundation requirements

The backend comes first. A UI will be built after these operations have stable
interfaces and tests. CLI commands are the current interface; an HTTP server is
not required to establish the core services.

## Processing order

The runnable CLI provides readiness checks (`doctor`), a labeled isolated demo,
four-file import/review execution (`play`), and complete/resumable saved-pod
execution (`simulate`). These use the existing services and gates; they do not
replace explicit human bracket review or silently substitute unsupported cards.
Each attempt is persisted before progress is emitted. Report files are exclusive
and bounded to a preview of 20 attempts plus fixed-size aggregate diagnostics;
the database keeps all attempts. Interruptions remain counted and resumed runs
do not retry their reserved slots. See [QUICKSTART.md](QUICKSTART.md).

Code is grouped in the `commander` package. Configuration comes from `config/`,
while checkout data remains under `data/`; the root script is a thin CLI entry
point. The source layout is independent of the shell's working directory for
default config/data lookup, though operator-supplied relative input paths use
the shell's working directory.

1. Parse and resolve the supplied decklist within input limits.
2. Validate Commander deck construction against the catalog and pinned rules.
3. Persist a validated deck with its validation evidence.
4. Evaluate declared bracket separately from format legality.
5. Select validated opponents from a versioned benchmark library.
6. Check engine card coverage, then create and execute a simulation job.
7. Persist results, failures, engine settings, deck snapshots, and raw logs.

Steps 4–7 must not run for a deck that has failed or incomplete legality checks.
The single-game adapter uses the saved-deck legality and bracket gates.
Persistent local batches use those same gates; automated matchmaking remains pending.

## Required services and acceptance criteria

| Service | Required behavior | Current state |
| --- | --- | --- |
| Card catalog | Stable identities, faces, current source data, atomic refresh | Implemented; manual refresh |
| Legality | Explain failures, record rules/catalog versions, block unverified decks | Implemented for documented scope |
| Deck library | Save validated decks and reload them without trusting stored approval | Implemented; immutable revision snapshots |
| Bracket review | Separate user declaration from verified bracket findings | Game Changer screening and human-attested reviews implemented |
| Opponent library | Ten popular commander configurations per bracket with reviewed lists and source evidence | Immutable source-sample storage implemented; automated acquisition pending |
| Engine adapter | Real Commander rules execution, card-coverage checks, bounded processes | Pinned Forge single-game adapter; broad card correctness not yet validated |
| Simulation jobs | Revalidation, seat/opponent balance, cancellation, progress, resumable results | Fixed-pod batches, rotating seats, bounded resumable chunks; crash recovery/opponent scheduling pending |
| Results | Wins, losses, draws, failures, uncertainty, traceable logs | Per-game logs, durable batch counts/rates, covered land/cast/attack observations; uncertainty and causal diagnostics pending |

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

Explicit commander color choices, companions and supplementary sticker/Attraction
sections now have versioned legality checks, immutable storage and native export.
Missing required colors and unverified companion conditions block approval;
selected features also require verified native engine capabilities. Explicit
Attraction printing/light variants remain work. Sideboard and
maybeboard exports are excluded from the starting deck. This is standard
multiplayer Commander, not Commander Draft, Brawl, Duel Commander, or Rule 0.
New construction mechanics or unknown copy caps require validator work rather
than an override. Bracket compliance and engine compatibility are separate gates.

The pinned rules are the September 25, 2026 Comprehensive Rules. Updating card
data does not automatically update the validator's interpretation of rules.

Validator version 2 distinguishes a meld front from the combined meld result
using the exact printing ID in `all_parts`. A combined result cannot occupy a
starting slot or the command zone. Missing or conflicting role metadata blocks
approval. Current validator/rules versions are carried into bracket rechecks;
immutable prior validation history is preserved.

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

## Engine and batch contracts

The implemented single-game operation requires four explicit legal, reviewed
revisions in the same bracket and catalog/policy evidence, then checks pinned
engine assets and card-script presence before launch. Inputs, seeds, reviews,
engine hashes, deck exports, bounded logs, and final outcomes are preserved in
generated directories. Profile access is serialized. Engine failures, truncated
logs, timeouts (including Forge's forced timeout draws), and unrecognized output
are not completed wins or genuine draws. Script presence does not prove card
correctness or AI competence. Java and other engine assets remain trusted local
dependencies; execution is not sandboxed by the Python adapter.

Batch admission enforces an integer count from 1 through 100,000 (rejecting
booleans), including 100/1,000/10,000 and custom counts. Four explicit reviewed
versions and semantic engine/review evidence are frozen with a hash; changing
recheck timestamps do not change semantic evidence. A write transaction checks
current catalog, policy, and review identities before admitting the batch.
The worker repeats evidence checks, full validation, integrity, and script
coverage before execution, refusing to rebind changed inputs.

Jobs persist state and attempts, rotate seats deterministically, and support
cooperative cancellation and chunk resume. Attempt reservation and worker claim
use write transactions; ownership is checked before recording results. Terminal
attempts and job specifications are immutable. Requested runs cap reserved
attempts; failed/interrupted slots are not automatically retried. Summaries retain
failed/interrupted counts separately from completed-game win/draw rates.
Cancellation may finish the current bounded game. A hard crash leaves ownership
claimed rather than permitting duplicate execution; recovery tooling is pending.
The current opponent mode is an explicit fixed pod, not automatic matchmaking.

Upcoming diagnostics must define mana issues, gameplay bottlenecks, and targeting
events with supporting evidence and coverage. Subjective "hate"/"unfun" labels
require provenance and must not be presented as measured human intent. The
self-hosted UI will expose these findings after backend contracts are stable.

The initial implemented observation contract includes versioned normalized events
and source line references, raw-log hashes, bounded samples, land/cast counts,
complete-main-turn no-land observations, and attack-assignment matrices. Partial
or missing coverage is explicit, never substituted as zero. Only completed,
covered games enter batch diagnostic totals; seat mappings use original pod order.
Diagnostic output failure does not replace a valid game outcome. These counts
do not provide hidden-state mana diagnoses or human targeting explanations.

## Catalog engine audit contract

Audit creation requires fresh catalog provenance and pinned engine integrity,
then freezes bounded per-identity export matches and fixed-size scope counts in
one transaction. A catalog refresh during preparation or engine change during
creation aborts the snapshot. Sealed headers/cards cannot be updated, deleted,
or extended. Historical reads verify content digests and require no Java.

Scope is every retained local identity, separated by its Commander legality
field; this does not infer starting-card eligibility. Exact canonical and
explicit Universes Within aliases are permitted; ambiguous aliases fail closed.
Missing export aliases include in-game-only meld faces and cannot be described
as an exact count of unsupported playable cards. Audit results must never replace
current construction, bracket, or launch-time script checks. Script availability
does not verify Oracle semantics, interactions, or AI behavior.

## Engine gap and regression contracts

The `engine-interactions` service runs only fixed repository-owned partial-state
fixtures in a bounded isolated Python/JVM child. Fresh catalog identities and
Oracle-characteristic hashes, pinned engine/script evidence, and reviewed case
expectations are required. Six common direct-state checkpoints per case capture actual
targeting, stack, zone, token, ownership, and attachment outcomes. Missing setup
or evidence is inconclusive, never a pass. Any witnessed expected-state mismatch
fails; changed suite/source/engine provenance invalidates the result.
Its diagnostic fixtures bypass ordinary deck startup only and never enter user
deck libraries, bracket attestations, benchmark rankings, or batch win rates.
The regular simulation legality/review gates remain mandatory.

Suite v2 adds prevention/replacement and countered-trigger controls, with eight
reviewed card identities and loyalty in their Oracle fingerprints. Counterspell
targets must bind by raw ability ID to the prior checkpoint's pending trigger;
missing identities are inconclusive and conflicting bindings fail. Counter
resolution is exercised through the native stack API; mana payment, casting
windows, AI response selection, and outcome-dependent replacement ordering are
not certified by these partial-state fixtures.

Suite v3 adds four target-legality cases and a ninth pinned identity, Ground Seal.
Target-card IDs and engine zone timestamps must bind the selected object before
and after a response. A graveyard roundtrip must show two ordered native zone
changes and preserve the old target identity while the returned card has a new
timestamp. Missing/malformed identities are inconclusive; contradictory evidence
fails. Injected cards receive native game timestamps. Worker receipts remain
bounded to 128 KiB using compact JSON; reports retain indented state evidence.

Suite v4 adds three defense/attachment cases, with 12 pinned card identities.
Actual response spell/Aura resolution must grant the expected ability and preserve
the selected token's ID/timestamp and the pending reflexive trigger's ID. The
reflexive target must remain the Equipment, distinct from the token. Native
target-eligibility probes compare both players' permissions using a nonartifact
creature-targeting spell. They are predicates, not paid or legally timed casts.
These cases distinguish protection's attachment restriction from hexproof/shroud
targeting restrictions; full casting and priority validation remain separate.

Suite v5 adds a late-protection detachment case. Only this fixed case accepts an
additional `after_detachment` checkpoint; missing or unexpected checkpoints are
inconclusive. The prior state must witness attachment and a 3/1 token. After actual
Tel-Jilad Defiance resolution and native state-based actions, the same Equipment
and token must retain their IDs/zone timestamps, while attachment ends and power
returns to 1. Raw attachment and response target identities are independently
checked; pre/post stacks must be empty and the response must be in the graveyard.
The 12 pinned identities and existing process/JSON limits remain unchanged.

Suite v6 partitions the same 24 cases into Equipment, damage, countering, targeting,
and protection families. A schema-2 manifest lists exactly those fixed IDs; paths,
family order, and case inventory come from code. Each definition and worker
receipt remains limited to 128 KiB/32 levels, with duplicate-field rejection.
Generated worker inputs use the same bound and exclusive file creation. A single
bounded Python/JVM child runs every fixed family and writes one receipt per family.

Report schema 2 keeps the combined `checks` list and adds `partition_results`
with each receipt's status, Java version, and SHA-256. Missing, misidentified,
wrong-suite, or malformed receipts cannot pass. A witnessed mismatch fails the
combined result; process failure or conflicting Java versions is inconclusive.
The suite fingerprint binds hashes of the exact parsed bytes of the manifest
and all family definitions. Worker admission and parent pre/post checks verify
that evidence; changed definitions invalidate the whole conclusion. Historical
v1-v6 reports remain artifacts, not interchangeable current receipts.

Suite v7 adds four replacement-order cases in a sixth bounded family and pins
Urza's Armor as the thirteenth Oracle identity. The affected player's test-only
controller records candidate effect/card IDs, timestamps, controller identities,
and chosen effect IDs. The verifier independently requires both candidates at
the first callback, the remaining effect at the second, and the intended order
under the correct affected player. Missing evidence is inconclusive; contradictory
choices fail even with matching life totals. Combat/noncombat branches separately
check trigger/token creation and the 5-versus-4 damage outcomes.

Only fixed repository Java source is compiled. A trusted local JDK 17+ `javac`
is selected from `COMMANDER_JAVAC` or PATH; generated bytecode targets Java 17
and annotation processors are disabled. Compilation has a 30-second/64 KiB-log
bound, and the fixed class has a 64 KiB size bound. Record compiler binary/version,
compilation log/process, source hashes, and compiled class hash. Worker admission
and post-run checks reject changed class/tool evidence; every receipt binds the
class hash. The pinned Forge archive/JRE and production AI/scripts stay unchanged.
Ordinary simulations and offline unit tests require no Java compiler.

Suite v8 adds eight cases in a seventh bounded casting family and pins Plains
as the fourteenth identity for an off-color control. Its fixed checkpoints are
`before`, `after_attempt`, and `after_resolution`. Only accepted spells resolve;
rejected cases retain their existing stack. Record native `canPlay`/`canPayCost`
results, actual phase/priority IDs, the fixture's admission decision, payment API
invocation/result, source tapping, mana color/source identities, and spell/ability
stack IDs. Payment/resource/stack summary counts must agree with raw evidence.

Ranger's Guile and Bonesplitter pay via `ComputerUtil.playStack`, including native
land activation. The same API rejects no-mana and off-color proposals. Native
legality blocks noninstant timing during combat or with a nonempty stack; the
fixture does not invoke payment for those cases. A separate fixture gate requires
priority ownership. This gate is explicit harness admission, not proof that a
low-level payment call enforces priority. Phase/priority are initialized directly;
full pass rotation, cast proposal rollback after partial payment, variable and
additional costs, and response chains remain unverified. Preserve source/card/
stack identities for rejected proposals, and bind each accepted payment to its
previously untapped source and intended spell/target.

Auditor version 2 freezes gap classifications at audit creation. Backlog reads
verify stored evidence and page only actionable records, with counts for excluded
supplementary objects. Version-1 audits require a fresh snapshot for classification.
Name candidates never authorize export. Copied-face aliases require explicit
engine declarations and existing targets; ambiguous exports fail closed.
Validator version 3 excludes sticker sheets from starting decks and preserves
the review state for uncertain meld roles.

Explicit repository-owned identity reviews may bridge verified spelling
differences only for their exact Oracle identity and pinned archive/script
hashes. Review provenance is retained in audit and game coverage evidence;
incompatible batch evidence must not resume. Missing identities, changed script
evidence, unsafe names, conflicts, and ambiguity cannot authorize an export.
Identity/export approval does not certify card rules behavior. Meld diagnostics
show conflicting printing IDs without inferring an approved role from names.

The built-in regression pod uses a disposable database populated from current
catalog records and passes normal deck/engine gates. Fixture bracket reviews are
test-only, never production attestations. Reports preserve hashes, witness lines,
engine version, and seed. A completed game alone is insufficient: every required
case must be exercised and pass. Absent evidence is inconclusive, never success.
These limited log witnesses do not certify every card, exact rules boundaries,
stack priority, replacement effects, targeted removal, or combo behavior.

## Salt evidence contract

EDHREC's top salt page is the configured community-opinion reference. Local
manifests preserve voting year, observation time, source attribution, rank, and
0–4 rating, with strict size/count/schema/precision checks. Exact catalog/face
resolution records IDs or explicit unresolved/ambiguous states. Immutable hashed
snapshots are admitted atomically against current catalog evidence. Operator
attribution is not authenticated provider verification; no network client or
scheduled source synchronization is implemented.

Salt analysis requires a legal explicit deck revision and reads ratings inside
the same catalog read transaction. Unrated cards remain unknown. Known-score
subtotals/means report their limited denominator; these are project-defined
descriptions rather than an official source deck score. The 30-day observation
freshness label is independent of annual voting year. Historical snapshots remain
unchanged. Salt opinions must remain distinct from engine attack observations,
causal explanations, bracket approval, and hypothetical targeting policies.
