# Commander Simulator

A local Commander simulator CLI with a Scryfall card catalog, legality checks,
reviewed deck revisions, a pinned Forge adapter, and configurable simulation batches.
See [PROJECT_PLAN.md](docs/PROJECT_PLAN.md) for the confirmed product direction.
See [CHANGELOG.md](CHANGELOG.md) for ongoing changes and historical milestones.
Use one active repository checkout; local development scratch work and preserved
older copies belong under ignored `data/`, following [CONTRIBUTING.md](CONTRIBUTING.md#workspace-and-file-placement).
The remaining loose legacy simulator source and original ZIP were moved from
Downloads to `data/local-archive/downloads-loose-20261031/` with verified hashes.

The core CLI requires Python 3.10 or newer and no third-party runtime packages.
Companion condition validation, explicit Attraction printing checks and controlled
interaction tests require the
optional native bridge and a trusted Java 17+ runtime. Controlled interaction
tests also require a JDK 17+ compiler (`javac` on PATH, or an executable selected
by `COMMANDER_JAVAC`).
Development/testing uses Python 3.12 and the pinned requirements-dev.txt tools.

## Card coverage

The AI checks the full cost when removing counters from one or more cards.
If a later cost fails, it restores the counters used for that payment and
preserves independent counter removals. See
[card-counter costs](docs/CARD_COVERAGE.md#card-counter-costs).

When a later cost fails, the AI restores paid energy and reversible discards.
The engine returns cards from the graveyard or exile in the tested cases and
keeps a card moved into a library by a replacement effect. See
[energy and discard recovery](docs/CARD_COVERAGE.md#energy-and-discard-recovery).

Mana planning now accounts for permanents needed for later tap costs,
including when a mana ability taps or sacrifices another card or needs mana
from a second source. See
[combined mana and tap costs](docs/CARD_COVERAGE.md#combined-mana-and-tap-costs).

The AI can activate Weight of Conscience with creatures that share a type,
including a Changeling. It also counts the power or toughness the rules
assign to a crew payment and restores eligible taps when a later cost fails.
See [tap costs and group selection](docs/CARD_COVERAGE.md#tap-costs-and-group-selection).

A failed AI activation now returns refundable mana and direct life payments,
including life paid for Phyrexian mana. The checks cover Yawgmoth, Zopandrel,
and Vampiric Rites. See
[failed activation recovery](docs/CARD_COVERAGE.md#failed-activation-recovery).

The native AI now checks that a creature is available for only one sacrifice
when a spell also needs a sacrifice to produce its mana. Tests cover Blood
Pet, Ashnod's Altar, and Orzhov Signet funding Altar's Reap. A separate test
checks that Demon's Herald needs three distinct creatures. See
[sacrifice allocation](docs/CARD_COVERAGE.md#sacrifice-allocation).

Failed casts return the card to its original zone, including when mana was
already spent. The engine drops triggers caused by the abandoned move to the
stack and preserves earlier triggers. Hall of Gemstone's chosen color now
reaches the mana planner. Validation passed 840 focused native tests, 1,388
broader gameplay checks, 256 serialization checks, and 42 live tests with 764
subtests. See [cast recovery and chosen mana](docs/CARD_COVERAGE.md#cast-recovery-and-chosen-mana).

Automatic payment now distinguishes mana being added from a source being
tapped. It counts Caged Sun's bonus, excludes Kinnan's bonus for sacrifice-only
mana, and preserves both the mana type and its source when checking snow costs.
That milestone passed 829 focused native tests, 1,377 broader gameplay
checks, 256 serialization checks, and 42 live tests with 753 subtests. See
[mana events and snow payments](docs/CARD_COVERAGE.md#mana-events-and-snow-payments).

The reflected-mana milestone added support for Overabundance's bonus on automatic payments,
including mixed-color and typed colorless costs. Sasaya's Essence counts and
chooses each repeated bonus separately. Snowfall's mana is available only for
cumulative upkeep. Validation passed 813 focused native tests, 1,361 broader
gameplay checks, 256 serialization checks, and 42 live tests with 737 subtests.
See [reflected and triggered mana](docs/CARD_COVERAGE.md#reflected-and-triggered-mana).

The earlier mana-reservation milestone cleared expired mana reservations before rolling and gave
hybrid safety forecasts their own RNG scope on the evaluation thread. It
combined two fixed samples so a single bad pile outcome does not dominate the
safety check. The runtime also integrates the Divine Intervention rules fix:
its controller's last-counter removal queues a separate draw trigger.

That milestone passed 760 focused native tests, 1,308 broader gameplay checks,
256 serialization checks, 42 live tests / 684 subtests, and the Python quality
gate (403 offline tests / 508 subtests). See
[mana reservations and hybrid previews](docs/CARD_COVERAGE.md#mana-reservations-and-hybrid-previews)
and [draw-ending rules](docs/CARD_COVERAGE.md#draw-ending-rules). Strategic AI
exclusions on Divine Intervention remain; full-card correctness is ongoing.

The earlier exchange/pile milestone added paid Cultural Exchange and Whims of the Fates
plays, rejects invalid exchange replies before either transfer, and preserves
player order for seeded pile rolls. That milestone passed 724 focused native,
1,272 broader gameplay, 256 serialization and 41 live tests / 648 subtests,
plus the Python quality gate (399 offline tests / 508 subtests). The pile AI
retains a legal all-in-one-pile policy; optimal partitions and full-card
certification remain open. See
[exchange and pile coverage](docs/CARD_COVERAGE.md#creature-exchanges-and-random-piles).

The earlier mass-counter milestone added complete paid outcomes for
Aether Snap, Dust of Moments, Oblivion Stone and Pestilent Haze. Modal choices,
attached counter cleanup and source-decline controls have native and packaged
witnesses. That milestone passed 693 focused native, 1,241 broader gameplay,
256 serialization and 40 live tests / 633 subtests, plus the Python quality gate
(399 offline tests / 508 subtests). See
[mass counter removal and paid outcomes](docs/CARD_COVERAGE.md#mass-counter-removal-and-paid-outcomes)
for exact scope and remaining full-card limits.

The earlier composed-control milestone added native planning for Invert Polarity,
Sudden Substitution and Perplexing Chimera. Cast provenance now preserves the original
caster, paid costs and announced targets when a live spell changes control.
That milestone fixed the stolen-Zacama land-untap outcome and passed 666 focused
native regressions, 1,214 broader gameplay checks, 256 serialization checks,
39 live tests / 619 subtests and the Python quality gate (399 offline tests /
508 subtests). See [composed control and cast provenance](docs/CARD_COVERAGE.md#composed-control-and-cast-provenance)
for the bounded forecasts, exact fixtures and remaining limits.

Paid spell control and retargeting now have native choice/outcome witnesses for
Aethersnatch, Commandeer and Chef's Kiss, including Commandeer's alternate exile
cost. That earlier reviewed build passed 641 focused native regressions,
1,189 broader gameplay checks, 256 serialization checks and 38 live tests /
605 subtests, plus the Python quality gate. See
[spell-control coverage](docs/CARD_COVERAGE.md#paid-spell-control-and-retargeting)
for its exact historical scope. Full-card rules and strategy
coverage remain in progress.

The recorded catalog refresh uses Scryfall’s November 4 export (39,287 records).
The previous database is preserved under `data/local-archive/`; real provider
timestamps and normal freshness checks remain in effect.

Primary-control planning now evaluates paid native future windows for Worst Fears,
Mindslaver, Secret of Bloodbending, Sorin Markov and The Dominion Bracelet.
That earlier milestone passed 613 focused native regressions, 1,161 broader gameplay
checks, 256 serialization checks and 37 live tests / 593 subtests, plus the Python
quality gate. See [primary-control coverage](docs/CARD_COVERAGE.md#primary-control-future-planning)
for exact scope and limits. Forecasts sample at most two legal actions per future
main phase. Full-card rules and strategy coverage remain in progress.

Usage-history work and its remaining validation limits are described in
[usage histories and trait identities](docs/CARD_COVERAGE.md#usage-histories-and-trait-identities).
See also [scheduled effects and temporal state](docs/CARD_COVERAGE.md#scheduled-effects-and-temporal-state)
for expiration, control-layer and player-keyword validation.
[Delayed-trigger snapshots](docs/CARD_COVERAGE.md#delayed-trigger-snapshots) cover
future sacrifices, returns, upkeep debts, registrations and held events.
[Turn and phase schedules](docs/CARD_COVERAGE.md#turn-and-phase-schedules) cover
selected extra turns, extra combats, attached obligations and phase counters.
[Conditional control and cleanup](docs/CARD_COVERAGE.md#conditional-control-and-cleanup)
cover selected power/enchantment conditions, two-turn deadlines and cleanup queues.
[Animation snapshots](docs/CARD_COVERAGE.md#animation-snapshots) cover timed/mass
animation and copied reversal paths for all 13 retained Licids.
[Temporary-copy expiry](docs/CARD_COVERAGE.md#temporary-copy-expiry) covers selected
copy frames, restored references and temporary token keywords.
[Protection deadlines](docs/CARD_COVERAGE.md#protection-deadlines) cover temporary
protection on individual creatures, groups and players in copied games.
[Keyword restoration and power exchange](docs/CARD_COVERAGE.md#keyword-restoration-and-power-exchange)
cover temporary keyword removal and Serene Master combat cleanup.
[Forced blocking and prevention shields](docs/CARD_COVERAGE.md#forced-blocking-and-prevention-shields)
cover copied block requirements and finite shield cleanup.
[Ring designations and text changes](docs/CARD_COVERAGE.md#ring-designations-and-text-changes)
cover Ring progression, bearer loss, word edits and text-box exchange.
[Reconfigure and mana-granted keywords](docs/CARD_COVERAGE.md#reconfigure-and-mana-granted-keywords)
cover attachment-effect cleanup and temporary haste after actual mana payment.
[Host-bound exile returns](docs/CARD_COVERAGE.md#host-bound-exile-returns) cover
linked exiled cards, battlefield/hand returns and copied return abilities.
[Temporary effect cleanup and regeneration](docs/CARD_COVERAGE.md#temporary-effect-cleanup-and-regeneration)
cover effect-card expiration, regeneration quotas/history and Spiny Starfish tokens.
[Detain and control age](docs/CARD_COVERAGE.md#detain-and-control-age) cover independent
restrictions/deadlines and tap admission after newly controlled creatures lose haste.
[Player-control snapshots](docs/CARD_COVERAGE.md#player-control-snapshots) cover
turn/combat deadlines, layered controllers and Emrakul's following extra turn.
[Decision beneficiaries and pending responses](docs/CARD_COVERAGE.md#decision-beneficiaries-and-pending-responses)
cover actual AI choices, fixed scoring perspective and spell/ability stack targets.
[Controlled attack planning](docs/CARD_COVERAGE.md#controlled-attack-planning) describes
native attack requirements, actor-paid costs, controller-benefit decisions and
real combat outcomes.

The default reviewed Commander Forge build constructs all **32,109 matched
native card identities** in the frozen local Commander-format catalog without errors.
Seven additional records are non-starting meld results. The build passes 641
focused native tests for stickers, chosen commander colors, companion pregame
behavior, supplementary AI zones, historical card state, damage provenance,
participant state, starting-life baselines, paid stack state, cast forms, usage
histories, scheduled effects, delayed triggers, turn/phase schedules and
conditional control/cleanup, animation reversal, temporary-copy expiry, protection
deadlines, keyword restoration, combat power exchange, forced-block requirements,
prevention shields, Ring state, text changes, Reconfigure cleanup, mana-granted
haste, host-bound exile returns, temporary effects, regeneration history, Detain,
raw control age, player-control snapshots, decision/response planning and
controlled attack outcomes, primary-control forecasts, resolving-parent safety and paid spell control.
Broader native runs pass 1,189 AI/gameplay tests (including those 641) and 256
card/deck serialization tests, with no failures or skips.
The `engine-card-probe` command preserves bounded, identity-bound construction evidence.
Five primary player-control sources now have paid native choice/outcome witnesses
in heuristic, hybrid and full modes. Source and packaged fixtures each change
from 23 failed criteria to 23 passes. Control timing, costs, source transitions,
original-state independence and survival are observed. Controlled players plan
for their decision controller's benefit while paying with their own resources. Verified outcomes
include paid Shock, Counterspell, Stifle and selected controlled combat. Native
mandatory attacks remain required even when harmful to the controller; broader
combat and mandatory-choice policy still need work.
Other callbacks, arbitrary suspended continuations and general AI policy still
need separate validation.
See [card support and evidence](docs/CARD_COVERAGE.md) and the
[reviewed engine build](engine/README.md) for the exact source, patches and limits.

This construction check does not certify all interactions or AI decisions.
Sticker and Attraction sections now retain validated supplementary selections
through saved revisions and native pregame loading. Explicit companion selection
now includes native condition validation against all 100 starting cards. Explicit
commander color choices are retained through legality checks, saved revisions,
native pregame loading and AI game copies. All 87 retained legal Attraction
printings across 22 identities preserve their selected light combinations through
import, snapshots, native registration and AI copies. Applied stickers now retain
physical records and independent sheet references
through AI copies, with LKI records retained separately from ordinary Clone effects.
Control changes cannot make an already applied sticker available for reuse.
Packaged-runtime checks cover all 480 stickers across 48 legal sheets in three
public zones, comparing live, AI-copy and LKI state. These are state-preservation
checks, separate from individual ability resolution.
The 41 controlled interaction scenarios remain separate checks. A further 49 native meld cases verify all seven catalog
results forming, separating after graveyard/exile moves and rejecting invalid
parts. These meld fixtures directly resolve native effects; they do not certify
trigger admission, payment or timing.

Historical-state work additionally checks departed objects, distinct incarnations,
zone-entry records and paid morbid spell outcomes. See the
[history validation scope](docs/CARD_COVERAGE.md#historical-state-validation).

Damage-state validation additionally covers filtered provenance, prior-turn
receipts and paid freerunning/bloodthirst controls. See the
[damage validation scope](docs/CARD_COVERAGE.md#damage-provenance-and-alternative-costs).

Participant-state validation covers eliminated seats, loss outcomes, turn order,
historical player references and independent later eliminations. A resolved Blood
Tyrant loss trigger retains its five counters without firing again during copying.
See the [participant validation scope](docs/CARD_COVERAGE.md#eliminated-participants).

Starting-life validation checks that Commander snapshots retain the 40-life
baseline separately from current life. Righteous Valkyrie's bonus requires 47
life, including after actual paid life-gain spells. See the
[starting-life validation scope](docs/CARD_COVERAGE.md#starting-life-baselines).

Paid-stack validation preserves actual mana/X/kicker payments, selected modes,
divided targets, activated abilities, admitted triggers, cast history and floating
mana through snapshots. It checks resolution in the copied game without changing
the source game or replaying cast events. See the
[paid-stack validation scope](docs/CARD_COVERAGE.md#paid-stack-snapshots).

Cast-form validation additionally covers paid Adventure, split, modal back-face,
morph/disguise, prototype and bestow casts, repeated Adventure snapshots followed
by casting from exile, and real disguise turn-up/ward outcomes. Copied card views
agree with the restored form. See the
[cast-form validation scope](docs/CARD_COVERAGE.md#cast-form-snapshots).

## Run it now

From the repository folder in PowerShell:

```powershell
.\.venv\Scripts\python.exe commander_simulator.py doctor
.\.venv\Scripts\python.exe commander_simulator.py demo --runs 1
```

The current local installation has the catalog and engine installed. `doctor`
reports missing or stale prerequisites on other checkouts. The demo runs real
Forge games using clearly labeled diagnostic decks in an isolated saved database;
it does not add fixture decks or reviews to your main library. It prints progress,
results, and paths to a JSON report and its database. It is not a competitive
benchmark or a realistic deck recommendation.

Use `play` to import your deck and three supplied opponents, or `simulate` to
reuse reviewed deck revisions. Start with one game before choosing 100, 1,000,
10,000, or up to 100,000 attempts. See [the CLI quick start](docs/QUICKSTART.md)
for deck file format, explicit bracket reviews, resume, cancellation, and reports.

## Commands

For direct-state card interaction checks, install the optional pinned tools and run:

```powershell
.\.venv\Scripts\python.exe -m pip install -r requirements-interactions.txt
.\.venv\Scripts\python.exe commander_simulator.py engine-interactions
```

The current suite checks 41 Ratonhnhaké꞉ton/Equipment scenarios against direct
Forge state: separate trigger/target timing, return and attachment, missing or
opponent-owned Equipment, removed targets, removed tokens/sources, incompatible
attachments, noncombat damage, damage prevention/doubling, and countering the
initial or reflexive trigger with Disallow. Ground Seal cases check target legality
before selection and at resolution; a zone-roundtrip case checks that the returned
card is a new object. Native response spells also check protection from artifacts,
hexproof, and shroud, including each player's target eligibility and the difference
between targeting the Equipment and attaching it to the token. A late-protection
case witnesses attachment first, then verifies native state-based detachment
without changing the Equipment's zone or object identity. Four replacement-order
cases record both native choices for Furnace of Rath and Urza's Armor: doubling
then preventing one deals 5 damage; preventing one then doubling deals 4.
Combat and noncombat controls check trigger behavior for both orders. Eight
casting cases now exercise Forge's paid-casting entry point with real lands,
colored/generic costs, unaffordable/off-color rejection, and controlled timing
and priority admission checks. Three additional cases drive Forge’s native
four-player priority loop, including a paid counterspell response and an
unaffordable response. Two counterspell-chain cases now verify a third player
countering the counterspell, a fresh pass cycle before the original spell resolves,
and the outcome when that third player cannot afford the response.
Results retain checkpoints, identities and zone timestamps,
Oracle/engine/source hashes, and raw worker logs. All 41 currently pass on
the pinned engine. This does not certify every card, complete priority handling,
or default AI response strategy.

The same command runs nine bounded fixture families: Equipment, damage,
countering, targeting, protection, replacement ordering, casting, priority, and
counterspell chains.
`config/interaction_suite.json` is the
manifest; reviewed expectations live in `config/interactions/`. Each family
writes a separate `observations-<family>.json` receipt. The combined schema-2
report retains all 41 checks and adds `partition_results` with per-family status
and receipt hashes. The fixed test controllers are compiled into the generated
run directory; reports preserve compiler/source/class hashes and compilation logs.
Production card scripts and AI choices are unchanged. Every family must pass for CLI success. See
[controlled interaction tests](docs/INTERACTION_TESTS.md).

Run these from the project folder:

```powershell
python commander_simulator.py init
python commander_simulator.py sync
python commander_simulator.py lookup "Sol Ring"
python commander_simulator.py stats
python -m pytest -v
```

If `python` is not available, use an installed interpreter's full path. In
PowerShell, prefix a quoted executable path with `&`.

`sync` downloads Scryfall's Oracle Cards bulk export, which represents unique
card identities with representative printings. It does not include every
printing or translation. Use `sync --kind default_cards` for the default printing
export or `sync --kind all_cards` for every printing/language; these are larger.
Compressed JSON Lines and JSON-array exports are both supported.
An existing export can be imported with `python commander_simulator.py import file.json`.

The database lives at `data/cards.sqlite3`, alongside cached exports. That directory
is excluded from Git. Override the database path with `--database PATH` before
the subcommand. No scheduled updates run; rerun `sync` to refresh the catalog.
Imports update existing records and add new records; they do not delete records
that disappear from a later export. Failed imports roll back their database changes.

## Structure

```text
commander_simulator.py     Small, stable CLI entry point
commander/
  catalog/                Scryfall import and SQLite card catalog
  decks/                  Parsing, revisions, and four-file pod import
  rules/                  Commander legality and bracket review
  engine/                 Pinned Forge execution, coverage, and regressions
  simulation/             Durable batches and complete-run workflow
  diagnostics/            Observed events and aggregate metrics
  opponents/              Attributed representative collections
  salt/                   EDHREC opinion snapshots and advisory analysis
  cli/                    Argument parsing and terminal presentation
  paths.py                Stable configuration/data locations
config/                   Versioned policy, engine pins, alias reviews
examples/                 Local input and review templates
tests/unit/               Offline regression tests
tests/integration/        Opt-in real-engine tests
tests/support/            Shared isolated test fixtures
docs/                     Quick start, product plan, backend contracts
tools/                    Shared quality gate
data/                     Ignored local catalog, engines, runs, and reports
```

Private engine settings now live at `config/engine_config.json`; relative paths
are resolved from that file. The local configuration was migrated while preserving
the original engine locations. The database remains `data/cards.sqlite3` and raw
games remain under `data/runs/`. README, changelog, contributing guide, dependency
pins, and tool configuration stay at the repository root.

Card identities and printings are separate. Faces belong to printings, preserving
face-specific artwork and Oracle IDs. Rare reversible records without a root
Oracle ID use their Scryfall printing ID as the catalog key. Complete source JSON
is retained for each printing, including fields not yet exposed as SQL columns.

Card data and rules text do not execute card abilities; game mechanics will be
implemented separately as the project grows.

Sources: [Scryfall bulk data](https://scryfall.com/docs/api/bulk-data) and
[official card field definitions](https://github.com/scryfall/api-types/blob/main/src/objects/Card/CardFields.ts).

## Initial validation

Eight offline tests pass, including reprint identity, face queries, repeat imports,
rollback, Unicode, malformed input, and compressed JSON Lines. The first live
Oracle import on October 25, 2026 loaded 38,997 catalog records and 6,659 faces.
These are Scryfall catalog records, not a count of Commander-legal cards.

## Deck import

```powershell
python commander_simulator.py deck-add my-deck.txt --name "My deck" --bracket 3 --commander "Teysa Karlov"
python commander_simulator.py decks
```

Text files accept `1 Card Name` lines (or `1x Card Name`). A Commander section
can replace `--commander`; repeat the flag for a commander pair. Sideboards and
maybeboards are excluded. Flat lists may include the specified commander; one
copy is moved to the command zone. Brackets are user declarations, from 1 to 5.
The legality gate checks deck size, commander eligibility and pairing, color
identity, basic land types, ban/format status, and duplicate-card limits. Checks
use the pinned rules and recorded Scryfall catalog snapshot. Invalid decks and
unverified conditions cannot be saved as validated decks. Bracket review and
engine compatibility remain separate requirements.

Check a list without saving it:

```powershell
python commander_simulator.py validate my-deck.txt --commander "Teysa Karlov"
```

This prints a structured legality report and exits nonzero for `illegal` or
`needs_review`. Freshness policy requires a verified Scryfall bulk timestamp no
more than seven days old. Run `sync` to refresh the catalog. Companion, Stickers
and Attractions sections retain explicit selections outside the 100 starting
cards. For The Prismatic Piper or Faceless One, use a commander line such as
`1 The Prismatic Piper [color=G]`, choosing one of W, U, B, R or G. A missing
required choice blocks validation. These features require the reviewed engine
capabilities before any simulation can start. The future UI follows the backend; see
[BACKEND_REQUIREMENTS.md](docs/BACKEND_REQUIREMENTS.md) for scope and processing order.

## Code quality and security

[CONTRIBUTING.md](CONTRIBUTING.md) defines readability, design, testing, and secure
coding requirements. Decklists are limited to 1 MiB and 2,000 lines; card names
and quantities are bounded before resolving or expanding them. Deck names are
validated, database writes use SQL parameters, and imports run in transactions.
Future remote uploads and engine execution require additional controls before
being exposed. This project has not undergone an independent security audit.

## Enforced Python standards

Use Python 3.12 for development tools. Install them with
`python -m pip install -r requirements-dev.txt` in a virtual environment.
Run `python tools/check.py` to check Black formatting, isort imports, Pylint,
Bandit security rules, and pytest unit tests. `python tools/check.py --fix` applies
formatting first. Install the commit hook with `python -m pre_commit install`.
See [CONTRIBUTING.md](CONTRIBUTING.md) for Windows commands and check details.
GitHub Actions runs the same gate for pushes and pull requests. Server-side merge
blocking still requires branch protection to mark the quality check as required.

## Versioned deck library

```powershell
python commander_simulator.py deck-add my-deck.txt --name "My deck" --bracket 3 --commander "Teysa Karlov"
python commander_simulator.py deck-revise 1 revised-deck.txt --commander "Teysa Karlov"
python commander_simulator.py deck-history 1
python commander_simulator.py deck-show 1 --version 1
python commander_simulator.py deck-check 1 --version 1
```

Replace `1` with the deck ID returned by `deck-add`. `deck-revise` returns the new
revision ID; each deck also has sequential version numbers. `--bracket` on a
revision is optional and otherwise retains the latest declaration. Source URLs
are retained as attribution metadata and are not fetched.

Each revision freezes card identities, names, quantities, zones, Oracle data,
bracket, source URL, and validation evidence. Database triggers reject revision
updates and deletes. Content hashes verify snapshot integrity on reads. The
same list may be saved again as a new revision to capture a new catalog snapshot.

`deck-show` displays the saved snapshot and historical validation. `deck-check`
revalidates that list against the current catalog; it can fail after a ban or
when card data becomes stale. Backend callers must recheck the selected version
before matching or running it. These read/validation commands emit a single
JSON document. There is still no simulation command or UI.

Existing unversioned decks migrate once, retaining their deck IDs. Their first
Oracle snapshot is captured at migration time; it cannot reconstruct historical
Oracle text that was never stored. Storage service calls own their transactions
and reject initialization inside an already-active caller transaction.

## Bracket screening and human review

```powershell
python commander_simulator.py bracket-check 1 --version 1
python commander_simulator.py bracket-review 1 my-review.json --version 1
python commander_simulator.py bracket-history 1 --version 1
```

`bracket-check` is diagnostic: it rechecks Commander legality first, counts Game
Changers in every zone using the current Scryfall catalog, and reports conflicts
or `needs_review`. It intentionally does not approve gameplay intent. Game
Changer caps are 0 for brackets 1–2, 3 for bracket 3, and unlimited for 4–5, per
[Wizards' Commander guidance](https://magic.wizards.com/en/formats/commander).
Policy, checklist, and source links live in `config/bracket_policy.json`.

Copy `examples/bracket_review.example.json` to a local file, provide a reviewer name and
rationale, and set a checklist item to true only after reviewing that aspect of
the specific deck and bracket. Every item must be confirmed; a human claim cannot
override a count conflict, missing catalog flags, or failed Commander legality.
The sample deliberately starts with every check false.

Saved reviews are immutable and tied to a deck revision, policy fingerprint, and
catalog import. The backend gate `require_bracket_review` rechecks current data;
a new revision, policy change, or catalog refresh requires another review. A
`reviewed` result means automated screening plus a recorded human attestation,
not an automated proof of deck power. Reviewer names are local labels, not
verified identities or user authentication. No arbitrary deck URL is fetched.
JSON review files are limited to 32 KiB and have a strict schema.

## Opponent collection snapshots

```powershell
python commander_simulator.py opponents-import my-opponents.json
python commander_simulator.py opponents
python commander_simulator.py opponents-show 1
python commander_simulator.py opponents-check 1
```

Start from `examples/opponent_manifest.example.json` and replace every placeholder with
observed source evidence and existing, reviewed deck IDs/versions. The importer
accepts local JSON only (maximum 256 KiB); it does not fetch attribution URLs.
Manifest schema version 1 records source, sample size, observation time/window,
source bracket provenance, author, list link, and observed commander deck counts.
The source counts and attribution are operator-supplied claims, not verified by
this importer. Commander pairs count as one configuration.

Each collection freezes its representative revision IDs, review evidence, and
hashes. Ranks order only the submitted representatives by reported counts, with
deterministic ties; they are not certified global or source-wide top-ten ranks.
Duplicate commander configurations, mismatched brackets, invalid decks, and
missing/stale bracket reviews are rejected.

Partial collections can be preserved and inspected. The readiness gate requires
ten distinct representatives, valid current reviews, and source observations no
more than 30 days old. Thirty days is a product freshness policy. Catalog/policy
updates require refreshed review evidence and a new collection. Earlier collections
remain intact when decks get new revisions.

Automatic source collection and the requested 50 real benchmark lists are not
implemented yet. EDHREC's individual commander pages do expose user-set bracket
counts, but a complete bracket ranking and stable decklist import interface have
not been verified. See docs/PROJECT_PLAN.md for the source investigation status.

## Pytest

Install the development dependencies into `.venv`, then run:

```powershell
.\.venv\Scripts\python.exe -m pytest -q
.\.venv\Scripts\python.exe -m pytest tests/unit/test_opponents.py -v
```

The existing unittest-based suites run directly under pytest. The full quality
runner also uses pytest, including when invoked by pre-commit and GitHub Actions.

## Single-game engine adapter

Install the assets referenced by `config/engine_lock.json` from their official release
URLs into an operator-controlled directory. Verify archive SHA-256 values before
extraction. Keep the complete Forge distribution together; the adapter verifies
its JAR and card-script archive before each game. Install trusted Java 17+.
Java and the remaining resources are part of the trusted installation and must
not be writable by upload users. Binaries are excluded from Git.

Copy `config/engine_config.example.json` to ignored `config/engine_config.json`, adjusting
`home` and `java` relative to that file. These are administrator settings;
the future web API must never accept executable paths from uploads.

```powershell
python commander_simulator.py engine-check
python commander_simulator.py simulate-game --seat 1:1 --seat 2:1 --seat 3:1 --seat 4:1 --seed 42 --game-clock 120
```

Each seat is an explicit `deck_id:version`. All four must pass current Commander
legality and recorded bracket review in the same bracket. Missing card scripts
block execution. Script availability does not establish correctness of every
card interaction or AI strategy. Automated opponent selection remains pending.

Runs preserve `inputs.json`, generated decks, `forge.log`, and `result.json`
under generated identifiers in `data/runs`. Wins/draws, engine errors,
unrecognized output, log limits, and timeouts have distinct statuses. Failed
simulations exit nonzero. Game clocks accept 1–300 seconds; an outer timeout
allows 90 seconds for startup/shutdown. Logs are capped at 8 MiB per game; Java
heap is capped at 2 GiB. A heap cap is not a total process memory limit.
The minimal child environment excludes inherited tokens and Java option variables.

Only one game may access the shared engine profile at a time. Its original
contents are restored on handled failures. A machine/process crash can leave
`.commander-simulator.lock` and a changed profile: an operator must confirm no
engine process is active and restore the profile before removing a stale lock.
These controls are not an OS sandbox. Worker isolation, hard quotas,
authorization, retention limits, and upload defenses remain deployment gates.

The optional live smoke test requires the installed engine and a fresh catalog:

```powershell
$env:COMMANDER_ENGINE_INTEGRATION = '1'
.\.venv\Scripts\python.exe -m pytest tests/integration/test_engine_integration.py -q -s
Remove-Item Env:COMMANDER_ENGINE_INTEGRATION
```

It uses disposable fixture decks/reviews, never production attestations or
benchmark popularity claims. Ordinary tests skip it. Forge AI results do not
establish how humans will react to a deck.

References: [pinned Forge release](https://github.com/Card-Forge/forge/releases/tag/forge-2.0.15)
and [Forge simulation/AI documentation](https://github.com/Card-Forge/forge/wiki/AI).

## Persistent batches

Create a batch from four explicitly selected, reviewed deck versions. Run counts
are validated by the backend from 1 through **100,000**; 100, 1,000, and 10,000
are ordinary supported counts. Creation validates legality, bracket, engine
integrity, and script coverage, then freezes inputs and evidence in SQLite.
It does not start games or allocate 100,000 attempt records.

```powershell
python commander_simulator.py batch-create --seat 1:1 --seat 2:1 --seat 3:1 --seat 4:1 --runs 100 --seed 42
python commander_simulator.py batch-run JOB_ID --max-games 10
python commander_simulator.py batch-show JOB_ID
python commander_simulator.py batch-results JOB_ID --offset 0 --limit 100
python commander_simulator.py batch-cancel JOB_ID
```

Use the generated `job_id` from creation. A worker defaults to **one game** per
invocation. Increase `--max-games` explicitly, up to 100,000, to run larger chunks.
Repeat `batch-run` to resume paused work. Execution is sequential and foreground;
this is not yet a daemon or web queue. These commands use the same trusted local
engine configuration as single-game commands.

The pod is fixed. Seats rotate every game, and seeds are the base seed plus the
zero-based game index. Original pod index 0 is the first supplied deck; summary
wins map back to the original pod order despite rotation. Counts not divisible
by four have a seat imbalance of at most one. No opponent popularity selection
is implied by an explicit pod.

Requested runs count reserved attempts, not guaranteed successful games. Summaries
separate attempted, completed, failed, interrupted, running, and unattempted
games. Win rates use completed games (including genuine draws) as the denominator;
failure counts remain visible. These are descriptive AI results for this pod,
without calibrated uncertainty or human-play predictions.

A failed game stops the chunk. An operator can resume a blocked batch if the
underlying problem is resolved; that failed slot is retained, never silently
retried. Changed catalog, policy, review, or engine evidence requires a new batch.
Cancellation is cooperative: a game already reserved may finish within its
configured limits, and no following game is reserved. Read/cancel commands do not require
an installed engine. A worker command exits nonzero for blocked/cancelled jobs
or any failed/interrupted attempts, even if all scheduled slots are finished.

Only one worker may claim a batch. Handled interruption releases its claim and
records the reserved attempt as interrupted. A hard process/machine crash leaves
the claim reserved; automatic crash recovery is deliberately not implemented.
Do not edit claims while an engine may still be active. Crash-recovery tooling,
storage budgets/retention, isolated workers, authentication, and hard host quotas
remain required before offering this as a web service. Total log storage grows
with attempts; the per-game log cap is not a batch disk quota.

## Observed gameplay diagnostics

New games save `events.jsonl` and `diagnostics.json` beside `forge.log` and
`result.json`. The report includes the original log hash, parser version, coverage,
and physical source line references for supported observations. A diagnostic
write failure is reported separately and does not change the game's outcome.

```powershell
python commander_simulator.py batch-diagnostics JOB_ID
python commander_simulator.py batch-results JOB_ID --offset 0 --limit 10
```

Per-game reports currently measure:

- Logged land plays and casts, including casts by card name.
- Turns with observed main phase and cleanup but no logged land play. Incomplete
  turns and turns without a main phase are excluded from that denominator.
- Creature attack assignments between player seats, with sampled source lines.
  These count assignments rather than damage, combat phases, or removal targets.

Batch reports map seat rotation back to the original pod order. They sum only
completed games with supported diagnostic coverage and explicitly count excluded
attempts. Missing legacy reports, partial/truncated parsing, failed games, and
running/interrupted attempts do not become zeros. If no game is eligible, deck
metrics are `null`; when coverage is mixed the aggregate status is `partial`.
Zero eligible main turns produces a `null` no-land-observation fraction.

The parser accepts at most 8 MiB of text and 10,000 normalized events. Candidate
lines are limited to 4,096 characters; card names to 300. Unknown supported-event
formats, malformed players, and event truncation make coverage partial. Evidence
samples are bounded; full source context remains in the raw log. Other event kinds
are outside this parser's scope. Parser version 1 targets the installed Forge
2.0.15 dialect; script presence and these observations do not prove all rules or
AI decisions correct.

The parser follows the [pinned Forge log formatter](https://github.com/Card-Forge/forge/blob/forge-2.0.15/forge-game/src/main/java/forge/game/GameLogFormatter.java).
It separates targeted-cast descriptions from card names and supports grouped
attackers and continuation lines for multiple defenders. Each attack line is
limited to 100 creatures. Non-player defender formats are currently partial
coverage; target descriptions are retained as text rather than classified as removal.

These are observations, not diagnoses of hidden game state. No logged land play
does not prove mana screw, flood, or a missed legal opportunity. The parser does
not establish color access, uncastable cards, targeted removal, why a player was
focused, or whether a card is "unfun." Those need further instrumentation and
explicitly attributed subjective data. Historical results are not rewritten.

## Catalog-wide engine coverage

Audit the current local catalog against the pinned engine without starting Java
or running games:

```powershell
python commander_simulator.py engine-audit
python commander_simulator.py engine-audit-show AUDIT_ID
python commander_simulator.py engine-audit-cards AUDIT_ID --limit 100
python commander_simulator.py engine-audit-cards AUDIT_ID --status script_present --scope commander_legal
python commander_simulator.py engine-audit-cards AUDIT_ID --scope all --status all --offset 100 --limit 100
```

Creation requires current catalog provenance and verified engine files. It freezes
one row per retained catalog identity, exact export matches, aggregate counts,
catalog evidence, engine hashes, and content fingerprints in SQLite. The audit
is atomic: input limits or changed evidence roll back the entire snapshot.
Sealed audits and their card rows cannot be changed or extended through ordinary
database writes. Inspection needs no installed engine and never upgrades old
evidence into a current gameplay approval.

Scopes distinguish `commander_legal`, `other_legality`, and `unknown_legality`.
These refer to catalog fields, not starting-deck eligibility. Statuses are:

- `script_present`: an exact canonical or explicitly declared engine alias has
  a safe starting-deck export name. This does not establish rules/AI correctness.
- `missing_script`: no export alias was found in this index. It can include
  in-game-only faces, so this is not a count of unsupported playable cards.
- `unsafe_export`: an alias matched but its export name failed the shared
  serialization checks; it cannot proceed to gameplay.

The index recognizes explicit `UniversesWithin:FlavorName` variant metadata,
including paired faces. It exports the corresponding original script name and
preserves both names in the audit record. No fuzzy name matching or Oracle-text
substitution is used. Back-face-only names are not promoted to starting cards.

The latest local audit matches 32,015 of 32,116 records marked Commander-legal,
with 101 unmatched records. Explicit `CopyFaceFrom` references recover 20 matches
previously missed by the index; a reviewed identity mapping adds one more. Those
counts describe the October 25, 2026 local
catalog and Forge 2.0.15, not every published card or every game interaction. Imports
retain older identities; the audited set is the local catalog, not a guaranteed
exact inventory of the latest bulk export. Rerun after catalog/engine changes.

Audits accept at most 100,000 catalog identities. Result pages contain 1–1,000
rows; offsets are bounded. Catalog identifiers, names, layouts, and legality
labels have bounded lengths. Audit creation briefly serializes catalog writes;
it is a trusted local operator command. Hard disk quotas and retention remain
web-deployment requirements.

Library-command and lookup JSON uses native Unicode on compatible terminals and
lossless JSON escapes on older encodings. Non-ASCII card names remain intact
when a JSON reader decodes the output, including on Windows redirected streams.

## Meld starting-card validation

Validator version 2 rejects combined meld results in either the main deck or
command zone. It uses the record's own printing ID and related-card component
metadata to distinguish `meld_part` from `meld_result`. Unknown roles require
review rather than being inferred from a name. Ordinary meld fronts remain
eligible under the other Commander construction rules. This follows
[CR 712.4 and 712.8a](https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.pdf).

Bracket rechecks now carry current validator/rules versions. Existing deck
history remains intact and is revalidated; older batch evidence that differs
from the new validation fields requires creating a fresh batch.

## Prioritized engine gaps and rules regressions

```powershell
python commander_simulator.py engine-audit
python commander_simulator.py engine-audit-gaps AUDIT_ID --limit 100
python commander_simulator.py engine-conformance
```

New audits freeze gap classifications alongside the per-card evidence. Older
version-1 audits remain readable but require a fresh audit for this backlog.
`engine-audit-gaps` pages actionable records in engineering priority order:
metadata/name/serialization review first, ordinary script investigation next,
then lexical hints for supplementary mechanics. Priority is not deck popularity
or proof of support. Spelling candidates remain blocked pending explicit review.
The local snapshot separates 48 sticker sheets and six confirmed meld results
from 47 investigation items: one unresolved meld role and 46 cards mentioning
supplementary mechanics. These are triage categories,
not a count of confirmed unsupported starting cards.

The index reads Forge's explicit `CopyFaceFrom` declarations and requires their
referenced names to exist. Canonical standalone names take precedence over
optional front aliases; specialized/back faces are not independently promoted.
This follows the pinned engine's [card parser](https://github.com/Card-Forge/forge/blob/forge-2.0.15/forge-core/src/main/java/forge/card/CardRules.java).
Index matches still do not validate individual card interactions.

Validator version 3 also rejects sticker sheets as starting cards; their
supplementary role is described in the [official Unfinity mechanics guide](https://magic.wizards.com/en/news/feature/unfinity-mechanics-2022-09-20).
Uncertain meld roles continue to require review. Existing revisions are retained
and revalidated; changed frozen batch evidence requires a new batch.

`engine-conformance` runs a fixed, legal four-player fixture at seeds 42, 0 and
4096, with one commander and 99 Forests per seat. It uses current catalog
metadata in a disposable database, normal legality and fixture-only bracket
reviews. Each game has a 60-second engine limit. All three are executed and
retained, including games that do not exercise a particular rule. The primary
run's `conformance.json` links each game's inputs, log hashes and source lines.
It adds no fixture decks or reviews to the main library.

The limited checks require witnessed commander tax on a Ruxa recast, Goreclaw's
resolved attack trigger followed by five unblocked combat damage, and a loss
from one commander's accumulated combat damage while life remains positive.
Commander tax and the 21-combat-damage rule are described in the
[official Commander rules](https://magic.wizards.com/en/formats/commander).
Each rule needs an actual witness in at least one fixed game. A contradiction
in any game fails the suite; missing evidence or an incomplete game yields
`inconclusive`. Only `passed` returns CLI exit code zero.

This is a limited log regression, not full rules conformance or AI validation.
It does not prove the exact 21-point boundary, stack priority, targeted removal,
replacement effects, zone-change interactions, or combos. Those need additional
controlled fixtures. Offline tests mutate witnesses to verify rejection;
opt-in integration tests run the real engine using the command documented above.

## Reviewed identity exports

The repository-owned `config/engine_alias_reviews.json` records explicit identity
reviews separately from gameplay code. Ratonhnhaké꞉ton has a catalog Unicode
colon, while Forge and the [official release notes](https://magic.wizards.com/en/news/feature/assassins-creed-release-notes)
use Ratonhnhaké:ton. This mapping requires its exact Oracle ID, reviewed archive
SHA-256, individual script SHA-256, and an existing exact engine target.
Other similar names are never normalized or approved automatically.

The registry is bounded to 64 KiB and 64 reviews and rejects duplicate fields,
names, identities, conflicting aliases, unsafe export names, and invalid hashes.
An archive mismatch leaves that alias unavailable; a matching archive with
incorrect script evidence fails closed. Registry source URLs are attribution
only and are never fetched at runtime. This is trusted repository configuration,
not an upload or a user-extensible remote mapping service.

Coverage evidence freezes every used review into audit records and game inputs,
including source references and the review's limited scope. Changing a used
review invalidates incompatible frozen batch evidence. A name without the
reviewed Oracle identity cannot be exported through the mapping.

The original live export test includes a legal multicolor basic-land pod
that verifies this export, casting, combat-damage trigger resolution, and later
Assassin-token participation. That game test does not verify Equipment recursion,
hexproof, or every interaction of this card. The separate controlled suite now
covers 41 specific interaction cases; identity approval remains distinct from
rules verification.

Gap classifier version 3 retains printing-ID evidence for uncertain meld roles.
An exact, deny-only review now excludes Ragnarok's result printing using both
authoritative printing records and their shared Oracle identity. Names alone
cannot approve an unknown meld front. Audits freeze this review and its source
references; changed metadata requires a new review. Historical audits retain
their original classification and version.

## EDHREC salt reference

[EDHREC's salt page](https://edhrec.com/top/salt) is the reference for community
salt ratings. EDHREC describes an annual community vote from 0 (least salty)
to 4 (most salty). The backend preserves those ratings with the voting year,
observation time, source link, and immutable snapshot hash. This is an opinion
signal for potential social friction, not evidence that a specific opponent
will attack or remove a card.

```powershell
python commander_simulator.py salt-import examples/salt_manifest.example.json
python commander_simulator.py salt-show SNAPSHOT_ID
python commander_simulator.py deck-salt DECK_ID --version 1 --salt-snapshot SNAPSHOT_ID
```

`salt-import` accepts an attributed local JSON sample. The example contains three
observed 2026 ratings, not a complete dataset. Source URL, year, timezone-aware
observation time, exact card name, source rank, and rating are required. Input is
limited to 1 MiB and 100 entries. Ratings must be finite 0–4 values with at most
two decimal places; internal storage uses integer hundredths. Duplicate JSON
keys, names, ranks, conflicting identity mappings, and inconsistent rank order
are rejected. Numeric token lengths and JSON nesting are bounded or rejected.

Exact catalog or face-name lookup binds ratings to Oracle identities. Unknown
or ambiguous source names remain unresolved; imports never guess a replacement.
Resolution shares the deck import candidate rules, so art-only/name-display
records do not mask a game-card match; banned candidates remain identifiable
and still fail the separate legality gate when used in a deck.
Snapshots are immutable and retain the catalog used for resolution. Source
attribution is operator-provided and is not an authenticated EDHREC data feed.
The current implementation performs no website requests, scheduled scraping,
or automatic updates. A supported access path needs to be established before
adding automatic synchronization; see [EDHREC's terms](https://edhrec.com/terms).
The full local reference sample and database are excluded from Git.

`deck-salt` requires an explicit saved revision and current Commander legality.
It reports each matched rating and rank, unrated cards as `null`, rated/unrated
quantities, and a quantity-weighted subtotal/mean over known ratings only.
Those aggregates are project-defined, not an official EDHREC deck score. Missing
sample entries are unknown rather than zero-salt, and an all-unrated deck has
an unknown mean. Commander cards are included in the same coverage accounting.

Observations older than 30 days are labeled stale; this is a product freshness
policy, separate from the annual voting year. Historical source snapshots remain
inspectable. Reports retain both current legality evidence and the catalog used
for the rating resolution. Existing source snapshots and game history are not rewritten.

Salt analysis does not change Forge targeting, game rules, brackets, or measured
attack statistics. `aggression_probability` remains `null`. A future optional
salt-informed opponent policy would need explicit modeling assumptions and
validation; the published rating itself supplies no attack-probability mapping.


## Original printed card names

Exact English `printed_name` and `flavor_name` fields from retained Scryfall
printings resolve to their existing canonical Oracle identity, including face
aliases. For example, Ryu, World Warrior resolves to Vikya, Scorching Stalwart
when that original printing is present. Original and canonical names share the
same singleton limit and canonical native export. Ambiguous aliases remain
unresolved; an exact canonical game-card name takes priority. No fuzzy matching
or engine installation is needed for this lookup.

The indexed projection is backfilled for existing printings and refreshed on
printing updates. Oracle bulk data supplies one representative printing per card,
so it cannot supply every original name. `sync --kind default_cards` imports the
English printing dataset when a complete printing-name inventory is needed.
Printing-only enrichment through the backend `store_printing` API preserves
current Oracle rules and does not refresh catalog freshness evidence. Historical
deck revisions retain their canonical identities.


Exact source-printing proofs now establish Fang and Vanille as meld starting
parts when their retained catalog records reference another printing of the same
Oracle identity. Legality reports freeze the used proofs; audit version 4
retains them in catalog evidence. Both complete relationship
graphs and both printing/Oracle identities must match. Changed metadata requires
review. These proofs establish card structure; ordinary Commander legality and
native engine gates still apply. The separate meld-result registry remains
deny-only. See [card support and evidence](docs/CARD_COVERAGE.md).


## Exact Attraction printing choices

After refreshing the Oracle catalog when needed, fetch the English Attraction
printing metadata separately:

```powershell
python commander_simulator.py sync-attractions
```

This updates printing records and records the observed source/hash separately.
It does not replace Oracle rules or refresh the catalog's legality timestamp.
The local run retained all 87 legal printings across 22 Oracle identities.

Append a section such as this to an otherwise legal 100-card decklist:

```text
Attractions
1 Bumper Cars (UNF) 202c
1 Balloon Stand (UNF) 200a
1 Bounce Chamber (UNF) 201a
1 Clown Extruder (UNF) 204a
1 Concession Stand (UNF) 205a
1 Costume Shop (UNF) 206a
1 Drop Tower (UNF) 209a
1 Ferris Wheel (UNF) 210
1 Foam Weapons Kiosk (UNF) 211a
1 Fortune Teller (UNF) 212a
```

The `(SET) collector` suffix selects an exact retained English printing. Bumper
Cars `202c`, for example, has lights 2, 5 and 6. The normal import, review, play
and resume commands preserve that choice. Different printings of one Attraction
still share its singleton limit; they cannot fill multiple supplementary slots.
At least ten distinct Attractions are required when selecting an Attraction deck.
Name-only selections continue to use the native default printing.

Missing, ambiguous, changed or nonlegal source printings block approval. At game
admission a bounded native preflight verifies the actual collector identity and
light set; a native fallback is rejected. The selected facts are frozen into deck
revisions and batch evidence, including seat rotations. Printing/source/edition
changes invalidate incompatible checks and frozen jobs. Direct engine syntax in
uploaded card names remains rejected; only validated selectors are generated.

The reviewed runtime fixes AI snapshots that omitted supplementary zones.
Attraction decks, junkyards and sticker sheets now survive those copies. Older
engines lacking that capability cannot start simulations containing these
selections. The lock also pins the complete 686-file edition inventory alongside
the JAR, card ZIP, Java executable and JVM library. See the detailed coverage
document for tested behavior and remaining interaction work.
