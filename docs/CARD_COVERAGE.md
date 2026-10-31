# Card support and evidence

The sticker-capable runtime uses the official Forge source snapshot
[`3e6c036e71b05b4d9d035df08b7a0c0dfa30cc76`](https://github.com/Card-Forge/forge/commit/3e6c036e71b05b4d9d035df08b7a0c0dfa30cc76),
the reviewed Wicker Picker implementation from
[upstream PR 12129](https://github.com/Card-Forge/forge/pull/12129) at immutable
head `470d983bea1dd40b06ea81c6e061b4b14c31f933`, and two local corrections.
The corrections account for already-added costs when multiple Wicker Pickers
grant kicker, and count all generic kicker payments without satisfying an
unpaid, linked printed kicker ability.

The repository includes the exact patches, regression source, source/archive
checksums, build instructions and Forge's GPL license under [engine/](../engine/).
Compiled engines and the card database remain local, ignored data. The build
tool preserves a separate runtime, its exact lock, logs and test results. It
does not silently replace the selected engine. Rebuilding may change the JAR
hash because upstream packaging embeds build metadata; every build needs its
own lock and validation.

## Coverage levels

These checks answer different questions:

| Evidence | What it establishes | What it does not establish |
| --- | --- | --- |
| Versioned Commander legality | The selected list satisfies implemented construction rules against the current local catalog | Bracket appropriateness, strategy or every pregame option |
| Engine audit | Every selected identity has an exact script or an explicitly reviewed mapping | Successful native parsing or gameplay |
| `engine-card-probe` | The native card factory can construct every matched identity and enumerate its states and abilities | Every ability resolves correctly, all rules interactions or competent AI play |
| Controlled interactions | Named, independently checked native state transitions pass | Untested combinations or general AI decisions |
| CLI/game integration | Import, review, four-player games, diagnostics and resumable batches work together | Competitive realism or statistical accuracy |

On the frozen local catalog tested on October 29, 2026, the new runtime constructs
**32,109 identities out of 32,116 Commander-format records**, with zero native
construction failures. The remaining seven are meld-result objects that cannot
occupy starting-deck slots. This is not seven unsupported starting cards. Their
gameplay depends on their native front-card scripts and requires separate meld
interaction evidence. The inventory includes supplementary objects, so the
constructed count is not a count of legal starting-deck cards.

Ragnarok's representative printing points to a different printing of its own
meld result. A deny-only review pins both complete printing records and their
Oracle identity. It excludes only this exact result from starting cards;
matching names cannot approve an unknown meld front. Validator version 4,
auditor version 3 and gap-classifier version 3 preserve the changed interpretation
without rewriting historical audits or catalog import timestamps.

## Run the catalog construction probe

Install `requirements-interactions.txt`, configure a trusted Java 17+ runtime,
then run:

```powershell
.\.venv\Scripts\python.exe commander_simulator.py engine-card-probe
# A separate candidate can be evaluated without changing the default:
.\.venv\Scripts\python.exe commander_simulator.py engine-card-probe --engine-config PATH_TO_BUILD/engine-config.json
```

Each run preserves an immutable audit, frozen input identities, native observations,
a receipt, a log and the final report under `data/runs/`. Identities, order, count,
engine/source hashes and the native Java version are checked. Missing, changed,
duplicate or malformed evidence cannot pass; neither can a timed-out or failed
worker. Seven explicitly classified meld results are acceptable exclusions;
unavailable starting cards produce `gaps_remain` and a failing CLI exit status.

Plans and receipts remain bounded JSON. Catalog rows use a separate protocol:
4 KiB per row, at most 100,000 rows and 32 MiB per stream. Native construction
runs in one process with a 600-second deadline, a 2 GiB JVM heap and an 8 MiB log
limit. These are application limits, not an operating-system security sandbox or
a total process memory quota. Card scripts and build plugins are trusted engine
code; users cannot upload them through a decklist.

## Gameplay work still required

The controlled suite currently covers 41 four-player interaction scenarios.
The patched build additionally passes all 23 upstream sticker tests and 10 local
Wicker Picker tests, with no skips. Those ten tests cover paid/declined kicker,
ticket and sticker timing before resolution, no selected sheets, affordability,
multiple grantors and interaction with printed kicker.

The CLI now supports supplementary sticker and Attraction sections through
validated immutable revisions. Native pregame integration verifies all 48 catalog
sticker sheets and all 22 Commander-legal Attractions, retaining every selection
and revealing exactly three distinct selected sheets. Empty selections produce
no supplementary objects. Name-only Attraction selections use the native default printing. Explicit
set/collector choices now preserve their exact source-backed light variants.
Explicit companions now have native condition admission and immutable revisions.
Commander color choices now pass backend and native pregame checks, including
AI game copies. Explicit Attraction printing/light choices now have source and native identity
checks. Construction counts remain separate from interaction correctness. Further work includes
meld trigger admission/payment/timing, catalog-wide characteristic comparisons
and broader native rules regressions. An implementation claim should name its tested behavior;
script presence and successful games alone do not certify full card coverage.


## Supplementary decklist sections

Add `Stickers` (or `Sticker Sheets`) and `Attractions` headings to the same plain
text import used for starting cards. Each following line uses `1 Exact Name`.
Neither section is part of the 100 starting cards. If selected, either section
requires at least ten unique selections, one each. There is no rules-imposed
maximum; normal upload/line limits still apply. Authoritative catalog types and
Commander legality are checked before saving or simulation. Attractions and
sticker sheets cannot replace starting cards.

These rules follow CR 123.2a and 717.2a in the project's pinned
[Comprehensive Rules](https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.pdf).
Forge performs the random three-sheet selection and Attraction shuffle during
native pregame setup. Saved revisions hash all supplementary selections and
retain their Oracle records. Changes require a new revision/review. The latest
legacy 100-card projection stays separate from the supplementary projection;
immutable revision snapshots remain authoritative. Validator version 5 adds
these construction checks.

The legacy two-counter `parse_decklist` API rejects supplementary selections
instead of silently discarding them. Use `prepare_deck` for resolved, validated
imports or `parse_deck_sections` when only parsing is needed. Ordinary Sideboard
and Maybeboard sections retain their existing ignored behavior and do not select
a companion.


## Broader native regressions

The reviewed patched source also passes **882 native tests in 76 selected AI and
gameplay classes**, with zero failures, errors or skips. This includes the 334 focused
sticker, chosen-color, companion, supplementary-copy, history, damage, participant,
starting-life, paid-stack, cast-form, usage-history, scheduled-state, delayed-trigger,
turn/phase-schedule, conditional-cleanup, animation and temporary-copy tests; these counts must not be added
together.
A separate card/deck serialization run passes 256 tests without failures or skips. GUI/download tests were
outside this run. The selected class inventory, process result and raw-report
hashes are recorded in [native test evidence](../engine/native_test_evidence.json).
Raw TestNG reports and compiler/test logs remain local under the recorded run ID.
This is broader regression evidence, not a test for every catalog card or every
possible interaction.


## Explicit companions

Use a `Companion` heading and one `1 Exact Card Name` line. The companion is
outside the 100 starting cards. It must be Commander-legal, have a companion
ability, fit the commander's color identity and have a different name from every
starting card. At most one companion can be selected. Ordinary `Sideboard` and
`Maybeboard` sections remain ignored and do not select a companion.

Validator version 6 checks the companion's condition against all 100 starting
cards, including the commander, using the configured default pinned native
engine. Native characteristics handle changeling, hidden-zone creature types,
mana symbols, mana values and activated abilities. The admission worker builds
a controlled partial state and never starts or plays a simulation. All ordinary
construction checks and catalog freshness must pass before this native analysis
can run. Optional `requirements-interactions.txt` tools and the configured trusted
engine are required; missing/failed analysis blocks admission.

Plans, receipts and reports are bounded to 128 KiB. The worker has a 90-second
deadline, 2 GiB JVM heap, filtered environment and 8 MiB log limit. Engine, source,
input and native Java evidence are checked. Missing or changed evidence cannot
approve a deck. A bounded 64-entry process cache keys exact identities/quantities,
Oracle data, engine and source; results are copied before returning to callers.
Cold companion validation starts a JVM and can take several seconds. Saved
revisions are revalidated rather than trusting a prior approval. Alternate
simulation engines still require their own gameplay/conformance validation.

Exact condition wording is checked against current Oracle text. Two explicit
[display reviews](../config/companion_condition_reviews.json) cover Gyruda and
Obosh's singular/plural wording and Gyruda's repeated reminder text. Each review
pins the Oracle identity, complete native condition and exact script archive;
used reviews retain the registry hash. Changed predicates or meanings are not
approximately matched.

The native condition matrix has 22 cases covering all nine legal companion
identities, including positive/negative controls, Grist's off-battlefield creature
type, changeling, basic-land mana abilities and Rings of Brighthearth's reminder
text. Yorion cannot satisfy its larger-deck condition in a 100-card Commander
deck. Separate integration admits Lurrus with Elas il-Kor, rejects Queen Marchesa
as a commander-only violation and verifies stable evidence across save/reload.
Lutri remains excluded by the ordinary current catalog ban gate.

Validated companions are retained in revision snapshots, hashes and a separate
latest projection, then exported to Forge's native Sideboard section for its
companion selection and special action. The condition checks do not by themselves
verify companion AI strategy, special-action payment or every gameplay interaction.
They implement admission rules from CR 103.2b, 702.139 and 903.11a in the pinned
[Comprehensive Rules](https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.pdf).


## Chosen commander colors and native pregame behavior

Validator version 7 accepts one explicit `[color=W/U/B/R/G]` choice on an eligible
commander line (replace the slash-separated notation with one letter). It requires
the actual Oracle pregame permission. The chosen color affects deck and companion
color identity, is hashed into immutable revisions and is exported as generated
native metadata. Arbitrary uploaded pipes/engine flags remain rejected. Pending
choices block approval. Engine capability checks run before simulation creation.

The native patches preserve the separate pregame designation through linked
abilities, partner/background setups, deck artwork selection, logical/LKI copies
and AI GameCopier snapshots. Later generic color choices cannot rewrite it, and
ordinary copying of characteristics does not grant the designation. The real
integration suite verifies four legal mono/multicolor setups and Command Tower's
mana options in original and AI states.

Companion pregame selection now refreshes static types before testing Umori, so
Grist counts as a creature outside the battlefield. Five native tests verify this
and Lurrus's paid three-mana special action, rejection with insufficient mana and
combat timing admission. Backend admission still supplies the full 100-card
condition gate. This does not certify companion AI strategy or every interaction.

The reviewed runtime passes the current live integration suite, including all
41 unchanged controlled interaction cases; current totals are recorded below. A new full factory probe constructs
all 32,109 matched identities. Source/report hashes bind the actual tested builds.
The Java executable and JVM library are both checked when pinned by the lock.
Build recipe bytes are frozen before execution and rechecked after compilation
and packaging. Previous runtime locks remain available for historical evidence.


## Meld formation and separation

A separate bounded native fixture runs **49 cases across all seven meld results**
currently retained in the Commander-legal catalog. Each pair forms the expected
linked result and separates into both original fronts on graveyard and exile
moves. Negative controls cover a missing part, a part in the graveyard, wrong
ownership, wrong control and a physical Clone copying a part. The latter must
revert to its true printed identity in exile and cannot form the result.

The fixture resolves actual printed native Meld effects in controlled partial
states. It does not pay activation costs, schedule the original triggers, prove
their intervening conditions, test commander replacement choices or certify all
meld-result abilities. Those remain separate work. Input/worker/engine/source
hashes and bounded receipts are retained in local run artifacts. The test also
asserts that its seven-result inventory matches the current catalog.

This test exposed ambiguous self-printing links on Fang and Vanille. The separate
`config/meld_part_reviews.json` stores exact source-backed proofs linking each
retained printing to a meld-front printing with the same Oracle identity, name
and complete three-object relationship graph. Duplicate fields, malformed UUIDs,
changed fingerprints, wrong roles, mismatched sources and changed graphs are
rejected. The 64 KiB/32-entry manifest is trusted repository data; its source URLs
are attribution and are never fetched during validation. The existing result
review registry cannot approve fronts and remains deny-only.

Validator version 8 freezes used front proofs into legality reports. A proof
only establishes starting-card structure; size, eligibility, color identity,
singleton, current format legality and engine checks still run. Audit version 4
also freezes the used proof. Historical reports are not rewritten.

The meld matrix remains part of the current live integration suite, alongside
the original 41 controlled cases. Current aggregate counts are recorded below;
the source and raw-report evidence for each milestone remain available.


## Exact Attraction printings and supplementary AI state

Validator version 9 accepts Arena-style `(SET) collector` suffixes in the
Attractions section. Each choice must resolve to one retained English printing
of the canonical Oracle identity, with valid ordered integer die lights, a legal
Commander printing and the correct object type. Names without this suffix keep
their previous native-default behavior. A printing choice cannot make an
Attraction a starting card or bypass the ten-unique-name supplementary rule.

Immutable revisions preserve printing/Oracle IDs, set, collector, language and
light facts. Explicit choices affect semantic hashes; unselected legacy hashes
remain unchanged. The separate latest projection points to the retained printing.
Changing source metadata invalidates incompatible approval without rewriting
historical snapshots. Missing or conflicting identities are never guessed.

`sync-attractions` uses one fixed HTTPS Scryfall search, bounded to four pages,
175 records per page and 2 MiB per response. It verifies pagination host/path/query,
complete inventory, unique printing IDs, source types/legality, JSON depth, unique
keys and finite numbers. Printings and the source receipt commit atomically only
when all canonical identities already exist. It never updates Oracle records or
the bulk-import freshness receipt. Current source facts comprise 87 printings of
22 legal Attraction identities. This is metadata synchronization, not deck approval.

The private native preflight uses the actual variant-card database and rejects
collector fallbacks, changed light sets, missing/reordered rows and malformed
receipt facts. Its plans bind engine, Java/JVM, backend/worker source and edition
inventory. The worker is bounded to 90 seconds, a 2 GiB JVM heap, existing log and
128 KiB document limits, and 256 distinct selections per process. These are
resource bounds, not additional Commander deck-construction rules.

A 64-entry in-process cache binds the complete frozen plan and returns fresh
decoded results. Edition files are bounded individually and in aggregate; their
portable filename/content fingerprint detects additions, removals and edits.
The reviewed engine lock pins all 686 edition files against immutable source.
Printing facts are rechecked after native preflight and in transactional job
admission; native resources/source are checked under the profile lock immediately
before game launch. Frozen batch evidence retains printing facts through seat
rotation and resume, excluding volatile run IDs and process timings.

Native GameCopier previously omitted supplementary zones. The patch retains
AttractionDeck, Junkyard, StickerSheets, Sideboard, PlanarDeck, SchemeDeck and
ContraptionDeck, with seven new regressions for object identity, order, functional
lights and independent copies. This does not enable additional application game
formats; the backend remains standard multiplayer Commander. Missing capability
metadata blocks simulations containing sticker or Attraction selections.

The end-to-end printing test passes all 87 variants through legal import, saved
revisions, generated exports, actual native registration and independent AI
copies. Supplementary pregame tests also retain their randomly chosen three
sheets and Attraction deck in AI copies. The real CLI test plays and resumes a
completed pod containing Bumper Cars `202c`, retaining its exact light facts in
the persisted batch contract. These tests do not certify every Visit ability,
dice outcome, sticker effect or AI decision.

Final verification: **398 offline tests / 508 subtests**, all quality tools, all
**11 live integrations / 162 subtests**, and the fresh catalog factory probe pass.
The probe constructs all 32,109 matched identities; seven separate meld results
remain correctly excluded from starting-card construction. The unchanged-card
archive and newly reviewed engine/edition hashes are retained in their locks.

## Applied sticker snapshot state

The sixth reviewed patch preserves applied sticker records, timestamps, name
positions and physical sheet/slot identities in AI game copies. Source-sheet
references are mapped to the independent copied game. LKI copies retain records
without reapplying characteristics; ordinary Clone copies retain their separate
copy rules. Used-slot discovery scans all public zones so a controller change
cannot free a sticker still attached to an owned object.

Twenty-two focused native regressions check four sticker kinds in battlefield,
graveyard and exile, independent LKI lists, ticket counters, public/hidden zone
transitions, control changes, Clone behavior and an actually paid spell copied
on the stack and resolved. Sticker games require `sticker_snapshot_state` in
addition to supplementary-zone support. Attraction-only games retain their
existing capability requirements.

These fixtures place stickers directly to isolate preservation of state. They
do not certify placement admission, every granted ability, historical zone-change
LKI or strategic AI decisions. Those remain separate rules-validation work.

Packaged-runtime validation additionally covers all **480 physical stickers on
48 Commander-legal sheets**, applied in 144 battlefield/graveyard/exile fixtures.
It compares effective characteristics, record hashes, physical-slot availability,
LKI state and Java object identity after settling continuous effects. Eight-sheet
workers retain bounded, engine-bound receipts. See
[`sticker_state_evidence.json`](../engine/sticker_state_evidence.json) for source,
input, runtime and raw receipt hashes. The original broad run exposed a stale
continuous-effect fixture; a targeted rerun of the corrected sticker fixture and
the separately enabled controlled interaction suite passed. Unique passing live
coverage totals 12 integration tests and 306 subtests. All 399 offline tests and
508 subtests pass with Black, isort, Pylint and Bandit.

## Historical state validation

The seventh source patch preserves recorded battlefield/graveyard departures and
current/previous-turn zone-entry records in independent AI snapshots. Historical
objects keep their old characteristics even after a hidden-zone move or face
change. Distinct remembered incarnations sharing a physical card ID remain
separate. Mutable damage-history containers are copied; combat-band references
retain the shared identity required by the copied combat. History-copy exceptions
are surfaced instead of treating missing records as harmless refinements.

Fourteen isolated native cases check counter-modified power, transformed faces,
name and power/toughness stickers, mapped owner/controller/zone/memory references,
repeated departures, remembered incarnations, vanished tokens, damage-history
independence, combat-band identity and copier reuse. Two cases actually pay for
Tragic Slip, copy the stack, resolve the spell and process state-based actions.
A departed Reflection of Kiki-Jiki enables the -13/-13 morbid effect; a departed
noncreature Fable does not, leaving a Colossal Dreadmaw at 5/5 after -1/-1.

The fixtures distinguish historical state from a card's current incarnation under
CR 608.2h/i in the
[pinned September 25, 2026 Comprehensive Rules](https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.pdf).
They do not certify every historical registry, linked ability, damage trigger,
combat interaction or post-elimination game state. Those require further tests.
The current reviewed runtime passed fresh-build and packaged-runtime checks;
previous reviewed runtimes and their locks remain separate.

Historical-state milestone verification: **399 offline tests / 508 subtests**, all Python quality
checks, **91 focused native tests**, **639 broader gameplay tests** including
those 91, **256 serialization tests**, and **13 live integrations / 312 subtests**
pass. All **32,109 matched native identities** construct; the seven non-starting
meld results remain excluded. The aggregate
[`history_state_evidence.json`](../engine/history_state_evidence.json) binds the
history witnesses, paid spell controls, all-sticker regression receipts, complete
live suite, native evidence and catalog probe to the exact runtime and sources.
Exact raw evidence bytes and LF witness source are preserved by Git attributes.

## Damage provenance and alternative costs

The eighth source patch preserves global damage histories and immutable packet
identity while mapping historical source/target objects into an independent AI
snapshot. Equal-valued damage packets must still refer to distinct targets.
Live per-card histories and their global ledger share the same copied history
object so subsequent damage is counted once. Current and previous-turn receipt
lists remain independent; the previous-turn provenance ledger expires on the
next cleanup. Filtered queries fail explicitly if provenance is unavailable.
Vanished token references retain historical metadata and permit safe cleanup.

Nineteen focused native cases cover filters, amounts, maximum damage, matching
sources, equal packets, later damage in both worlds, previous-turn receipts,
vanished sources/targets, missing provenance and paid cast controls. Seven
freerunning cases use native damage processing, normal cast admission and actual
payment for Eagle Vision: eligible Assassin/commander hits pay two mana and draw
three cards; noncombat hits, creature targets, ordinary creatures and control
at the wrong time do not authorize that alternative cost. Static effects are
settled before recording characteristic-defining creature types. These are
controlled damage-event fixtures, not proof of attack declaration or damage
assignment legality for every board.

Two regression controls actually pay for Lightning Bolt and Gorehorn Minotaurs.
Opponent damage enables bloodthirst 2, giving a 5/5; self-damage leaves a 3/3.
These already passed before this patch and are retained as controls, not claimed
as newly fixed behavior. The independent local Scryfall Oracle records agree
on bloodthirst 2, Eagle Vision's freerunning cost and its three-card draw.
Mechanic expectations follow CR 702.54 and 702.173 in the
[pinned rules](https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.pdf).

A separate worker on the previous packaged runtime observed a valid original
freerunning window become unavailable in its AI copy. The reviewed damage build
passes the nineteen isolated cases and the packaged-runtime witnesses;
this does not certify every damage replacement, prevention interaction, delayed
history query or post-elimination state.

The freerunning fixtures make the AI snapshot before casting Eagle Vision.
They do not certify copying every already-cast alternative-cost spell or its
paid choices on the stack. Active-turn elimination, pending loss triggers and
other damage prevention, replacement and delayed-history cases remain separate
validation work; settled non-active-player snapshots are covered below.

Current damage-build verification: **399 offline tests / 508 subtests**, all
Python quality tools, **110 focused native tests**, **658 broader gameplay tests**
including those 110, **256 serialization tests**, **14 live integrations / 323
subtests**, and all **32,109 matched native constructions** pass. The seven
non-starting meld results remain excluded. See
[`damage_state_evidence.json`](../engine/damage_state_evidence.json) for exact
runtime/source hashes, before-fix observations, Oracle witness facts, raw receipt
hashes and the native/live/catalog checks. Earlier reviewed runtimes are retained.

## Eliminated participants

The participant patch maps all four registered Commander participants, including
those who have lost. It copies active and lost lists in their recorded order,
along with independent life/counter state and each immutable outcome. It restores
life before setting the outcome, because an eliminated player cannot gain or
lose life through the engine's ordinary setters. Restoring a snapshot does not
call the player-loss action again. Surviving commanders and
effect cards remain wired to the copied game; departed owned cards stay out of
play. Historical damage targets, remembered players and zone records resolve to
the copied registered participant, rather than a player from the source game.

Fourteen native regressions cover concession, zero life and ten poison counters
at each of the three non-active seats in a four-player pod. They compare life,
counters, turn order and outcomes, reject targeting an eliminated player,
preserve historical damage and remembered-player references, and check that another elimination in
the copy cannot change the original. A second copy preserves loss order. A
resolved Blood Tyrant loss trigger grants five counters before the snapshot;
copying retains those counters and does not add another trigger. The expected
five-counter effect is checked against the independent local Scryfall Oracle
record, rather than inferred from the native script alone.

The packaged-runtime witness repeats nine seat/loss combinations and historical
reference/independence checks. Its before-fix receipt records three active players
becoming four, losing the original loss outcome, and a separate history-copy
exception for the eliminated damage target. A second before-fix witness records
a zero-life eliminated player incorrectly retaining the copied game's starting
life; the corrected snapshot retains zero. The aggregate
[participant_state_evidence.json](../engine/participant_state_evidence.json)
binds source/runtime identities, retained receipts and regression results.

This verifies snapshots after settled non-active-player elimination. It does
not certify every leave-game control transfer, pending loss trigger, departure
during the active player's turn, completed-game outcome, or arbitrary paid spell
state already on the stack. Those require further interaction tests.

Participant-build verification: **399 offline tests / 508 subtests**, all Python
quality tools, **124 focused native tests**, **672 broader gameplay tests**
including those 124, **256 serialization tests**, and all **32,109 matched native
constructions** pass. All **15 distinct live integrations / 334 subtests** have
passing results in the final complete run, with both engine and controlled
interaction opt-ins enabled. Earlier debug receipts are retained locally. The
seven non-starting meld results remain excluded from construction.
Previous reviewed runtimes and locks remain available.

## Starting-life baselines

A copied Commander game must preserve its starting-life baseline separately
from its current life total. Native registrations default to 20 life; recreating
them without the original baseline made a 40-life Commander player count as
having started at 20. The packaged before-fix witness records Righteous Valkyrie
and Grizzly Bears changing from 2 power to 4 merely because the game was copied.

The tenth patch copies registered starting life when constructing new player
registrations, then restores each live player's baseline before current life.
These two baselines can differ for a game override and remain distinct.
Eliminated players retain their baseline and zero current life across repeated
copies.

Sixteen native regressions and matching packaged witnesses check five configured
baselines, seven threshold controls and two actual paid spells. For a 40-life
baseline, Righteous Valkyrie's bonus remains off at 27, 40 and 46 life and turns
on at 47. With a 20-life baseline, it turns on at 27. In the copied game, paying
four mana for Angel's Mercy raises life from 40 to 47 and enables the bonus;
paying one mana for Chaplain's Blessing raises life to 45 and leaves it off.
Both spells use normal native cast admission and payment, resolve through the
stack, move to the graveyard and leave the original game unchanged. Independent
local Scryfall Oracle facts verify the threshold, bonus, mana costs and life gain.

[starting_life_evidence.json](../engine/starting_life_evidence.json) records the
exact runtime, source, before/after receipts, Oracle identities and test results.
The new fixtures initialize Commander registrations explicitly rather than
substituting current life for starting life. They verify this baseline invariant
and these static-effect/paid-spell outcomes; Righteous Valkyrie's entry trigger,
other starting-life-dependent cards, other registration metadata and arbitrary
paid spell state already on the stack remain separate validation work.

Starting-life build verification: **399 offline tests / 508 subtests**, all Python
quality tools, **140 focused native tests**, **688 broader gameplay tests**
including those 140, **256 serialization tests**, **16 live integrations / 350
subtests**, and all **32,109 matched constructions** pass. Native and live runs
have no failures or skips. The seven non-starting meld results retain their
separate construction exclusion and existing meld-interaction checks.

## Paid stack snapshots

The reviewed paid-stack patch restores existing entries instead of casting their
printed abilities again. It preserves paid mana and its mapped sources, declared
X, optional costs, selected modal/subability graphs, target incarnations and
divided allocations. Activated and admitted triggered abilities remain pending.
Cast histories remain available even when a caller intentionally omits visible
stack entries. Floating mana, payment records and lazy cost metadata are isolated
from the original game. Unknown mutable event payload types fail explicitly.

Thirty native cases exercise paid Lightning Bolt, Fireball, kicked Burst Lightning,
Counterspell targeting another pending spell, Prodigal Pyromancer, Soul Warden,
Boros Charm, buyback Capsize, Pentad Prism sunburst, Fling with sacrificed last-known
power, invalidated targets, departed sources, Young Pyromancer, Weather the Storm,
Omnath's floating mana, Stonecoil Serpent and Arc Lightning. Boros Charm's printed
mode is selected explicitly before normal admission/payment; this does not test
the AI's mode-selection policy. Five additional packaged-runtime witnesses use
normal casting/payment, a tap activation and an admitted enter trigger.

A retained before-fix receipt demonstrates missing paid mana/X/kicker, zero
Fireball damage, incorrect kicked damage and dropped activated/triggered entries.
The patch is independently bound to its source/test hashes, a fresh runtime build,
broader native regressions and packaged integration evidence in
[stack_state_evidence.json](../engine/stack_state_evidence.json).

These are snapshots at settled action points, not suspended Java continuations
during an effect's execution. Subsequent sections cover selected cast forms,
usage identities and scheduled effects. Arbitrary event graphs, unconverted
callbacks, remaining forms/identities and all mana-spending restrictions still
require separate validation. No claim of complete
card or AI correctness follows from these tests or the catalog construction count.

Paid-stack build verification: **399 offline tests / 508 subtests**, all Python
quality tools, **170 focused native tests**, **718 broader gameplay tests**
including those 170, **256 serialization tests**, and **17 live integrations /
355 subtests** pass. The live/native suites have no failures or skips.

## Cast-form snapshots

Snapshots preserve the form actually being cast, its clone frames and timestamps,
and mapped clone/exile/effect provenance. Card views reflect the restored name
and characteristics. Ordinary printed-face reconstruction previously lost these
forms even when the paid ability itself survived.

Sixteen native cases cover:

| Form or action | Paid witnesses and observations |
| --- | --- |
| Adventure | Petty Theft resolves into exile; repeated copies retain permission for a later paid Brazen Borrower cast |
| Modal double-faced | The Prismatic Bridge remains an enchantment and enters on its back face; Esika's normal front face remains usable |
| Split | Ice taps and draws; Fire deals its declared divided damage |
| Morph/disguise | Face-down 2/2 characteristics remain intact, including Dog Walker's ward |
| Prototype | Phyrexian Fleshgorger retains its three-mana black 3/3 form through resolution and later snapshots; clone state is independent |
| Bestow | Boon Satyr remains an Aura, attaches and grants its bonus; an invalidated target makes it resolve as a creature instead |
| Disguise turn-up | A real paid turn-up clears only the copied temporary face-down state |
| Ward response | A paid Lightning Strike consumes remaining mana before copied ward counters the earlier Lightning Bolt |

The source disguise keyword cache is refreshed when its temporary intrinsic
keywords change. The face-down reset is represented by scheduling metadata and
a new callback bound to the copied card, rather than sharing a callback that
captures the original. Exile provenance retains historical Adventure
characteristics and permission source references in the copied world.

Six Python/native witnesses additionally compare pending and resolved forms,
paid mana, colors, target outcomes, source independence and unique live-zone
instances in the packaged runtime. A retained before-fix receipt shows reverted
faces, a seven-mana colorless prototype body and unresolved stack-zone objects.

Expectations were checked against retained Scryfall Oracle identities and the
[Wizards rules text linked from the official rules page](https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt),
effective September 25, 2026: 702.21a, 702.103e, 702.168b/d, 709.3b,
712.8f/11b/13, 715.3b/d and 718.3b. The exact downloaded bytes and Oracle record
hashes are recorded in [cast-form evidence](../engine/cast_form_evidence.json).
This is a check of these rules and witnesses, not a complete current-rules audit.

The scheduled-effect work below additionally maps selected expiration callbacks.
Remaining callback kinds, merged/mutated groups, specialized/perpetual states,
every alternate-cost mechanic, all hidden-view identifiers and AI policy choices
still require separate validation. These tests do not certify every
card or interaction.

Cast-form build verification: **399 offline tests / 508 subtests**, all Python
quality tools, **186 focused native tests**, **734 broader gameplay tests**
including those 186, **256 serialization tests**, **18 live integrations / 361
subtests**, and **32,109 matched constructions** pass. Native/live runs have no
failures or skips; the seven non-starting meld results remain excluded from
standalone construction and retain their separate interaction checks.

## Usage histories and trait identities

The usage snapshot patch preserves the actual printed, keyword-generated and
statically granted trait graphs through one identity map. Activation counts,
resolution counts and mode histories use those canonical ability/grantor keys.
Mutable costs, mode lists, static references and keyword trait collections are
copied independently. Optional replacement overrides may remain null without
invoking normal setters that alter intrinsic flags.

Sixteen native cases cover paid once-per-turn activation, actual Stifle
countering without refunding usage, an only-once activation with mana still
available, Welcoming Vampire's one-draw limit, pending activations with the
visible stack included or omitted, independent native counter reset, repeated
snapshots, crew members that remain or leave the battlefield, an equipped
Viridian Longbow grant, successive Omnath landfall resolutions, Galadriel's
three distinct alliance choices, two paid saddle costs and normal riot resolution.
One case is an explicitly synthetic native contract fixture for a replacement
with no child ability; it is not a printed-card interaction claim.

Six packaged-runtime witnesses use ordinary native admission and payment.
Before-fix receipts reproduce lost usage limits, a duplicate Vampire draw,
repeated Galadriel modes and lost saddle count/member provenance. Source and
copied worlds are observed independently. Fixture names, costs and restrictions
are checked against the retained local Scryfall catalog.

This does not certify every restriction or generated grant. These initial reset
cases use native primitives. The scheduled-effect cases below add actual phase
transitions, one same-turn controller change and separate Rootwalla grants.
Grant removal/reacquisition, combat-mode history rollover, every keyword cost
subclass and arbitrary suspended continuations remain unvalidated. Crew/saddle
member provenance is distinct from proving every attack-trigger outcome.


Usage-history verification: **399 offline tests / 508 subtests**, all Python
quality tools, **202 focused native tests**, **750 broader gameplay tests**
including those 202, **256 serialization tests**, and **19 live integrations /
367 subtests** pass. Construction passes for all **32,109 matched native
identities**, with seven non-starting meld-result exclusions. Exact source,
before/after witnesses, rule/catalog references and runtime hashes are retained
in [usage-history evidence](../engine/usage_state_evidence.json).

## Scheduled effects and temporal state

Resolved effects have future obligations as well as visible current state. The
scheduled-effect snapshot patch uses an explicit SnapshotCommand protocol:
supported callbacks create new objects with references rebound through the same
identity map used for cards, players and abilities. It uses no reflective cloning,
deserialization or execution of original callbacks while copying.

Single and mass Pump expirations, player-keyword expirations, ControlGain return
and keyword-removal callbacks, and the marked face-down reset implement this
protocol. Phase queues preserve order, aliases, player deadlines and deferred
registration. Card event queues preserve the supported untap, unattach,
face-up/down, controller-change, phase-out and leave-play callbacks. Existing
baseline controller/timestamp and temporary control layers stay separate.
Player keyword layers and their effect-card traits are independent. Chosen-number
rules state and its existing card-view string are restored separately.

Eighteen native cases include real mana payment and actual turn/cleanup,
untap, leave-play and combat boundaries. They cover Rootwalla, Overrun, Jump,
Giant Growth, Take Up the Shield, Act of Treason, Sower of Temptation, Gilded
Light, Endoskeleton/Dramatic Reversal, Battering Ram, Galadriel and separate
Rootwalla abilities gained by Experiment Kraj. A paid Sanctum Prelate uses a
fixed legal player choice of two, rejects Rampant Growth through actual casting,
and admits and resolves Ponder and Grizzly Bears. Synthetic contracts separately
check scheduler ordering/aliases, unsupported-callback reporting and secret
number-view preservation; they are not printed-card behavior claims.

Eight packaged witnesses use ordinary native payment. The four-turn activation
case already passed on the previous build and remains a positive control.
Phase advancement runs
native cleanup/untap/upkeep/draw/turn-order hooks, with a bounded resolver; it does
not offer response priority. The packaged Prelate case observes the native AI's
legal numeric choice, while the native Java case controls choice two and checks
the resulting cast restrictions.

Unconverted callbacks in these queues are deliberately never shared with the source.
They remain omitted, as in the earlier copier, and are listed by
GameCopier.getUnmappedSnapshotCommands(). The normal simulation report does
not yet aggregate this API. Empty diagnostics in these fixed witnesses do not
certify all callbacks. Other effect implementations and unconverted wrappers,
unconverted callbacks in state-based and conditional queues,
unvalidated turn/phase variants, merged/perpetual objects and arbitrary
mid-resolution continuations require separate implementation and validation.
The gameplay AI's choice quality and complete hidden-information views also
remain outside this milestone.


Scheduled-state verification: **399 offline tests / 508 subtests**, all Python
quality tools, **220 focused native tests**, **768 broader gameplay tests**
including those 220, **256 serialization tests**, **20 live integrations /
375 subtests**, and **32,109 matched native constructions** pass. Seven
non-starting meld-result identities retain their separate interaction checks.
[Temporal-state evidence](../engine/duration_state_evidence.json) binds the exact
runtime, source, before/after witnesses, test hashes and rule/catalog references.


## Delayed-trigger snapshots

Future triggers are restored through the same identity map as paid abilities and
scheduled commands. The handler retains ordinary delayed triggers, turn-limited
aliases, player-defined registrations, waiting events and suppression flags.
Waiting events preserve their collection-time controller and extra-trigger count;
later control changes or removal of Panharmonicon do not rewrite those facts.
Active printed-card triggers are still rebuilt from the copied board.

Deferred registration at cleanup uses an explicit mapped command, so a next-turn
slowtrip registers in the copied game. Ordinary future registration and next-turn
windows retain their different lifetimes. Payment receipts in held events are
copied with canonical cost/ability/part references, historical paid-card state,
matrix flags and independent conversion/restriction arrays. The copied global
cost stack is bound to event references; receipts are not resumed payment dialogs.

Twenty-one native cases cover real paid Kiki-Jiki token creation with the source
alive or dead, Ghostway returns, payable/unpayable Pact debt and one-shot payment,
Sneak Attack with blink and late-end-step controls, Infuse, Ephemerate and
Visionary, and Suppress before/after its player's registration turn. Synthetic
contracts separately check held-event controllers/multipliers, nested payload
independence, suppression, registration lifetimes and cost-receipt aliases/matrices.
These contracts do not represent custom printed cards or suspended payment execution.

Nine packaged witnesses compare resolved future behavior and source independence.
Before-fix receipts retain tokens, strand exiled cards, lose debts/draws and omit
deferred registration. Rebound is checked through a real second blink/draw,
graveyard destination and untapped lands after the free cast. Phase advancement
runs native hooks with a bounded resolver and no response priority.

These are selected delayed-trigger checks. Unknown mutable event payload types
remain rejected by the mapper. This does not certify all trigger conditions,
APNAP ordering, replacement/doubling effects, hidden views, multiplayer loss
interactions, countering delayed triggers, all cost subclasses or payment
continuations. Other callback kinds, unvalidated conditional/state-based payloads,
unvalidated turn/phase variants and merged/perpetual state remain open.
The later conditional-cleanup work also scans conditional card checks and the
game state-based cleanup queue. Normal CLI reports still do not aggregate these
diagnostics.


Delayed-trigger verification: **399 offline tests / 508 subtests**, all Python
quality tools, **241 focused native tests**, **789 broader gameplay tests**
including those 241, **256 serialization tests**, **21 live integrations /
384 subtests**, and **32,109 matched native constructions** pass.
[Delayed-trigger evidence](../engine/delayed_state_evidence.json) binds the exact
runtime, source, corrected bounded trigger-drain helper, before/after witnesses,
test hashes and selected rule/catalog references. Generic payload map/list copies
do not establish arbitrary container alias or cycle preservation.


## Turn and phase schedules

Snapshots retain the native extra-turn stack, its normal-turn continuation and
each per-phase stack in their original order. Scheduler entries map their players,
delayed triggers and skip-untap/scheme ability references through the shared
identity map. Copying neither schedules a new turn nor admits its future triggers.
Current player extra-turn flags/counts, previous-turn player, phase counters and
scheduler control flags are restored separately from combat state.

Ten native cases use paid Time Warp, Time Stretch, Savor the Moment, Final Fortune,
Relentless Assault and Moraug casts; Moraug also uses admitted native land play.
They check targeted seats, multiple queued turns, most-recent-first ordering,
normal-turn resumption, skipping only the scheduled untap, the attached end-step
loss, inserted combat/main chains and Moraug's combat-entry untap. Separate copies
consume independent schedules. A mid-extra-turn copy retains the current turn flag,
upkeep count and previous player. Six initial native cases fail on the previous
build. Nine packaged witnesses retain exact before/after outcomes.

These fixtures advance real phase hooks without response priority. They do not
certify every extra turn/step/phase effect, simultaneous APNAP grants, eliminated
queued recipients, nested turn-ending effects, phase reversal, skip replacements,
scheme variants, all counter boundaries, priority restoration or repeated cleanup.
Other unmapped callbacks, unvalidated conditional/state-based payloads,
merged/perpetual state and arbitrary continuations remain open.

Verification: **399 offline tests / 508 subtests**, all Python quality tools,
**251 focused native tests**, **799 broader gameplay tests** including those 251,
**256 serialization tests**, **22 live integrations / 393 subtests**, and
**32,109 matched native constructions** pass. Seven non-starting meld-result
identities remain separate. [Turn-schedule evidence](../engine/turn_schedule_evidence.json)
binds the exact runtime, source, worker and test hashes, before/after receipts and
selected versioned rules/catalog references.


## Conditional control and cleanup

Snapshots retain each native conditional-control record in an independent array:
its two immutable expressions, mapped target and mapped expiration command.
Recognized records use the native four-field schema. Unsupported schemas fail
rather than sharing an unknown mutable payload. The same cleanup command keeps
its mapped identity when both an event queue and a condition refer to it.

EffectEffect expiration is an explicit mapped command, and the ThisTurnAndNextTurn
wrapper re-registers its mapped cleanup in the copied game's following end-of-turn
queue. An unsupported nested callback causes the wrapper to be omitted and the
nested kind to be reported. The game state-based cleanup queue also retains order
and duplicate command entries through the shared identity map. Copy construction's
existing native state-based check can execute those mapped commands; the copy
operation itself never executes original-game closures.

Nine paid card cases check Old Man of the Sea after target growth, source shrink
or untapping; Rootwater Matriarch after Disenchant, Ephemerate or Cloudshift;
Peace Talks copied before/after the first end-of-turn deadline; and Silence
expiring before an admitted, paid Lightning Bolt. Three existing primary outcomes
remain controls: Old Man untapping, Ephemerate's owner-controlled return and
Cloudshift's newly granted caster control. Control-condition targets are created
after a real turn cycle so automatic fixture combat cannot remove them first.
The untap transition directly invokes the native untap event.
Silence uses the native cast-restriction check and casting path, rather than
inferring admission from canPlay alone.

Six synthetic native contracts separately test state-based cleanup transfer,
queue order/duplicates, independent conditional arrays and shared mapped callback
identity, and omission/reporting of unknown state-based, conditional and nested
callbacks. Their numeric markers are test contracts, not printed card behavior.
Nine packaged witnesses retain exact before/after receipts. The stable native
baseline has 12 failures and three passing controls; these are separate from the
earlier corrected fixture assumptions and unstable setup runs.

These are selected conditions and cleanup implementations. Other callback classes
remain omitted and reported where scanned. The normal CLI simulation report does
not yet aggregate the diagnostic. The fixtures do not certify every EffectEffect
variant, every duration, source/target transition, overlapping control layer,
state-based-action rule, arbitrary conditional schema, hidden view, multiplayer
loss interaction, mid-resolution continuation or AI choice. Merged/perpetual state
and other unexplored native fields remain open.

Verification: **399 offline tests / 508 subtests**, all Python quality tools,
**266 focused native tests**, **814 broader gameplay tests** including those 266,
**256 serialization tests**, **23 live integrations / 402 subtests**, and
**32,109 matched native constructions** pass. Seven non-starting meld-result
identities remain separate. [Conditional-state evidence](../engine/conditional_state_evidence.json)
binds the exact runtime, source, stable baseline, worker/test hashes, outcomes and
selected versioned rule/catalog references.


## Animation snapshots

Animation expiration now stores a mapped card and the original effect timestamp.
It removes only that timestamp's power/toughness, type, keyword, color, trait,
name and related changes, using the native unanimation operation. Timed Animate
and AnimateAll paths share this explicit command. Permanent effects do not gain
an expiration merely because a snapshot is made.

Licid end-effect actions use an explicit native ability with a mapped expiration
payload. The standard snapshot copier invokes a protected native hook for supported
subclass fields after mapping the base ability state. Changed CardTraitChanges
records retain independent mutable collections and canonical mapped abilities,
triggers, replacements and static abilities. The animation's RemoveThisAbility
predicate explicitly maps its ability reference. Other record predicates retain
the previous native behavior and need separate conversion where they hold mutable
or game-bound data; this is not a universal predicate copier.

All 13 retained scripts with RevertCost are Licids. Twenty-six native cases use
both stack-copy modes to pay for casting, animation and reversal, checking that
reversing the copy leaves the original Aura intact. Six further paid-action cases
(including both Factory stack-copy modes) cover Mishra's Factory, Animate Land,
Sudden Spoiling, Natural Affinity and Awakening of Vitu-Ghazi under temporary
Factory animation. The permanent name, haste and 9/9 state survive expiration of
the temporary layer. One synthetic native contract checks changed-trait collection
independence and canonical ability/host/player aliases.

Eighteen packaged witnesses include each Licid and five other animation paths.
The previous build mutates the original game in all 13 Licid reversal witnesses,
leaves temporary animations active and fails to restore Spoiling's power/flying.
The stable native baseline fails all 33 cases. The earlier layering payment fixture
was corrected to supply a spare Forest before recording that baseline.

These are partial games with Commander registrations, controlled initial boards,
admitted payments and bounded native phase hooks without response priority. Licid
fixtures advance a turn cycle before creating their friendly target; timed/mass
fixtures start in postcombat main to avoid incidental AI combat. Licid reversion
coverage does not certify every separate enchanted-creature ability, hostile-target
control interaction, target/source transition or every possible animation overlap.
All remaining subtype payloads, removal predicates, face-up animation durations,
perpetual/merged states and arbitrary continuations need further work. The native
unmapped-command API still is not aggregated into normal CLI simulation reports.

Verification: **399 offline tests / 508 subtests**, all Python quality tools,
**299 focused native tests**, **847 broader gameplay tests** including those 299,
**256 serialization tests**, **24 live integrations / 420 subtests**, and
**32,109 matched native constructions** pass. Seven non-starting meld-result
identities remain separate. [Animation-state evidence](../engine/animation_state_evidence.json)
binds the exact runtime, source, all 13 identity/payment expectations, stable
before/after receipts, tests and selected versioned rule/catalog references.


## Temporary-copy expiry

The native temporary-copy cleanup now retains a mapped target, effect timestamp,
saved imprinted cards and remembered card/player references. It removes the
matching copy frame before restoring those saved values. References gained while
copied are cleared only when that frame is removed. The existing native restoration
contract retains remembered players and cards whose owners remain in the game;
other remembered payload kinds are not restored. A copied player's later loss is
evaluated in the copied world, without consulting the original game's membership.

Temporary token keywords also use an explicit mapped card/timestamp command.
Expiration removes that keyword layer while retaining intrinsic token keywords.
The original native scheduling branch still selects end of turn or the activating
player's next turn. This is separate from creating a new in-game copy or token.

Thirteen native cases comprise ten paid card paths, one controlled face-down event
and two synthetic memory contracts. They cover Mirage Mirror copying a creature,
land, artifact or
enchantment; Mirrorweave restoring multiple creatures; Shapesharer through the
controller's next turn; a paid Vesuvan Shapeshifter followed by a controlled native
face-down event; Eiganjo Uprising's two temporary keywords versus intrinsic
vigilance; two same-deadline overlapping frames; a blinked new incarnation; and
Cursed Mirror's copied characteristics/haste. The two pre-copy memory
contracts verify independent mapped references, card filtering after
elimination, retained player references and the native scalar-omission policy.
These memory fixtures are contracts, not claims that Grizzly Bears has imprint.

Eleven packaged witnesses cover ten paid card paths and the controlled face-down
event. The stable native baseline has 12 failures and one passing blink control.
The blink's primary outcome already passed; the earlier build still omitted its
unconverted old-incarnation callback. New witnesses report no unmapped commands.
Token identities, ownership, intrinsic vigilance and source-game state remain
independent across the tested expiry operations.

Fixtures use partial Commander registrations, controlled postcombat main states,
ordinary payment and bounded native hooks without response priority. The face-down
event isolates the end condition; it does not certify optional upkeep choice or
special-action timing. These cases do not establish every copy exception,
room/merged/perpetual layout, source/target transition, overlapping-memory lifetime,
keyword duration, multiplayer-loss interaction, hidden view or AI policy. Other
callbacks remain omitted/reported where scanned, and normal CLI reports still do
not aggregate that diagnostic.

Verification: **399 offline tests / 508 subtests**, all Python quality tools,
**312 focused native tests**, **860 broader gameplay tests** including those 312,
**256 serialization tests**, **25 live integrations / 431 subtests**, and
**32,109 matched native constructions** pass. Seven non-starting meld-result
identities remain separate. [Copy-expiration evidence](../engine/clone_state_evidence.json)
binds the exact runtime, source, stable before/after receipts, tests and selected
versioned rule/catalog references.


## Protection deadlines

The reviewed runtime maps temporary protection cleanup for both individual and
mass protection effects. Card cleanup retains the affected incarnation and its
keyword timestamp; player cleanup retains the mapped player and timestamp.
The existing duration scheduler and card-in-play guard remain in use. Copying
these payloads does not execute their cleanup or retain original-game closures.

Six native regressions cover paid Shelter, Brave the Elements and Seht's Tiger
paths with stack copying enabled and disabled. All six first reproduced lingering
protection after the copied world's native turn-end hooks. The fixed cases check
active protection before expiry, independent original-game deadlines, and White
Knight's intrinsic protection surviving cleanup. Three packaged-runtime witnesses
repeat these paid paths and record both worlds before and after their own turns.

The runtime passes **318 focused native tests**, **866 broader gameplay tests**
including those 318, and **256 serialization tests**. The opt-in live suite passes
**26 tests / 434 subtests**; the offline quality gate passes **399 tests / 508
subtests**. Frozen source, patch, fixture, receipt and log hashes are recorded in
[`protection_state_evidence.json`](../engine/protection_state_evidence.json).

These controlled four-player boards are partial registrations, not complete legal
decks. Native turn advancement runs bounded phase hooks without offering response
priority. These cases test expiration and copied-world ownership, not every
protection quality, prevention, blocking, attachment, radiance, blink, overlapping
duration, player-loss interaction or AI decision. Other effect callbacks and
arbitrary continuation payloads still require separate verification. This is
additional tested coverage, not complete Magic rules certification.


## Keyword restoration and power exchange

The reviewed runtime maps the cleanup payload for temporary keyword removal and
for temporary power exchanges. Keyword cleanup retains the affected card and the
layer timestamp; power-exchange cleanup maps both affected cards and its timestamp.
These commands remove the same layers at the same native deadlines as before,
without retaining cards or event targets from the original game.

Sixteen native regressions cover eight scenarios with stack copying enabled and
disabled. All sixteen first reproduced failed cleanup in the previous runtime:

- Canopy Claws removes flying, then restores it at turn end.
- Hammerheim pays its tap cost to remove River Boa's landwalk.
- Canopy Dragon gains flying and loses trample through separate effect paths;
  cleanup removes the flying grant and restores trample.
- An opponent pays to activate Zerapa Minotaur's first-strike removal.
- Cephalid Snitch pays its sacrifice cost against White Knight, Iridescent Angel,
  and a creature enchanted with Spectra Ward. The latter two first use a paid
  Moonlace to make the blue Snitch colorless, preserving target legality. Cleanup
  restores the original protection layer and removes the temporary split entries.
- A paid Serene Master cast is followed by a controlled attacker declaration.
  Native AI blocker selection, the block trigger, combat damage and end-of-combat
  hooks run normally. The copied Master returns to zero power while the source
  world's exchange remains active until that world's own combat ends.

Eight packaged-runtime witnesses repeat these paths, recording paid mana, tap or
sacrifice costs, the acting player, copied active state, independent source/copy
cleanup and unmapped-command diagnostics. The native fixtures also assert each
removed keyword existed before removal. Before/after receipts and frozen source
hashes are retained in
[`keyword_expiration_evidence.json`](../engine/keyword_expiration_evidence.json).
The retained September 25, 2026 rules reference covers duration, ability/power
layers, protection targeting and end-of-combat expiration; it is versioned evidence,
not a claim to have audited every rule or subsequent rules revision.

Verification: **334 focused native tests**, **882 broader gameplay tests** including
those 334, **256 serialization tests**, **27 live integrations / 442 subtests**,
and **399 offline tests / 508 subtests**, with the full Python quality gate.
The frozen catalog construction probe still passes **32,109 identities**, excluding
the same seven non-starting meld records. Construction is not gameplay proof.

These are controlled four-player boards with partial registrations, not complete
legal decks or full pregame runs. Attacker declaration is installed as a fixture;
this does not certify attacker admission, summoning-sickness handling or every
combat choice. Native phase advancement omits response priority. This work does
not certify every removed keyword, flashback path, overlapping layer, lost-player
or blink interaction, permanent/perpetual power exchange, or AI decision. Other
callbacks and arbitrary continuations still require separate verification.
