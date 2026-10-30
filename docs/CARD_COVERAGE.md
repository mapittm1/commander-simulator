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

The reviewed patched source also passes **750 native tests in 68 selected AI and
gameplay classes**, with zero failures, errors or skips. This includes the 202 focused
sticker, chosen-color, companion, supplementary-copy, history, damage, participant,
starting-life, paid-stack, cast-form and usage-history tests; these counts must not be added together.
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
during an effect's execution. Arbitrary event container graphs, delayed callbacks,
unlisted casting forms, all once-per-turn ability identities and all
mana-spending restrictions still require separate validation. No claim of complete
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

Other callback kinds, merged/mutated object groups, specialized or perpetual
states, every alternate-cost mechanic, all hidden-view identifiers and AI policy
choices still require separate validation. These tests do not certify every
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

This does not certify every restriction or generated grant. The counter/mode
reset tests call native reset primitives; they do not exercise a full turn
transition. Cross-controller limits, multiple identical grants from different
sources, grant removal/reacquisition, combat-mode rollover, every keyword cost
subclass and arbitrary suspended continuations remain unvalidated. Crew/saddle
member provenance is distinct from proving every attack-trigger outcome.


Usage-history verification: **399 offline tests / 508 subtests**, all Python
quality tools, **202 focused native tests**, **750 broader gameplay tests**
including those 202, **256 serialization tests**, and **19 live integrations /
367 subtests** pass. Construction passes for all **32,109 matched native
identities**, with seven non-starting meld-result exclusions. Exact source,
before/after witnesses, rule/catalog references and runtime hashes are retained
in [usage-history evidence](../engine/usage_state_evidence.json).
