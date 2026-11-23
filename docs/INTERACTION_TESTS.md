# Controlled card interaction checks

These tests inspect engine state at defined checkpoints, using the selected
reviewed Forge runtime's damage, trigger admission, stack resolution, zone-change,
and state-based action APIs. They are separate from full-game AI smoke tests and user simulations.

```powershell
.\.venv\Scripts\python.exe -m pip install -r requirements-interactions.txt
.\.venv\Scripts\python.exe commander_simulator.py engine-interactions
```

The command requires the pinned engine, a fresh catalog, the exact optional
JPype version, and a trusted installed JDK 17+ compiler. `javac` is discovered on
PATH; set `COMMANDER_JAVAC` to its executable to select another local compiler.
The compiler is used only for the fixed test controllers; the configured pinned
JRE still runs Forge. The existing Windows Java runtime exposes its JVM library; standard
Linux/macOS library paths are recognized, but the live checks were verified on
Windows. Ordinary catalog, deck, and simulation commands do not need this bridge.

## Battle combat and Siege defeat

The expanded 146-case family passes against the combat-role candidate, including
twelve dual-role cases. See the [source evidence](../engine/dual_combat_role_native_evidence.json)
and [packaged validation](../engine/dual_combat_role_packaged_evidence.json).
The selected runtime remains unchanged pending full live validation/activation
and still reproduces four role-loss failures; this command will expose those
failures until the corrected candidate is selected.

Run the fixed Battle pytest family with the selected reviewed engine,
optional native bridge, pinned local TestNG dependency and trusted JDK compiler:

```powershell
$env:COMMANDER_ENGINE_INTEGRATION = "1"
.\.venv\Scripts\python.exe -m pytest -q -s tests/integration/test_battle_combat_integration.py
```

Set `COMMANDER_JAVAC` when `javac` is not on PATH, as described above. Ordinary
quality checks skip this family unless the opt-in environment flag is enabled.
It is separate from the `engine-interactions` CLI suite.

All 146 native cases pass against the combat-role candidate. The family retains
134 witnesses and adds twelve dual-role contracts. The prior 134 retained 110
and added 24 paid restoration contracts, including copied
and nested pending Disenchant/Counterspell stacks. The prior 110 include 32
type-change, blocked and pending/nested spell-copy contracts. Native paid Aura
casting, flash-timing checks and Counterspell responses complement strike
history, assigned damage and Siege defeat. See the [resolved type-loss gap](../engine/defender_typechange_gap.json).

They cover legal defender enumeration, copied combat, damage to defense counters,
lethal Siege defeat, paid Fog, Hexmage sacrifice, transformed casting, Stifle on
the defeat trigger and Beast Within before trigger resolution. Actions, targets,
legal board setup and unblocked attacks are supplied; native resolution executes.
The family does not establish autonomous combat strategy or every Battle
interaction. Fixed source/dependency checks, compilation integrity, 30 production
class origins and hash-bound method receipts use the existing 90-second worker
budget. See [detailed cases and limits](CARD_COVERAGE.md#battle-combat-and-siege-defeat)
and [current packaged evidence](../engine/dual_combat_role_packaged_evidence.json).
Both supplemental fixtures are included in the 1,938-method candidate. The prior
[full runtime validation](../engine/defender_typechange_evidence.json) remains a
separate milestone; full live validation and activation of this candidate are
still pending.

## Current scope: Equipment, damage effects, countering, and target legality

The [official Assassin's Creed release notes](https://magic.wizards.com/en/news/feature/assassins-creed-release-notes)
explain the separate reflexive trigger and its target timing. They also explain
that Equipment can return even when it cannot attach. Broader rules are pinned
to the project's [September 25, 2026 Comprehensive Rules](https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.pdf).

| Case | Required behavior |
| --- | --- |
| Normal return | Combat damage puts the initial untargeted trigger on the stack; its resolution creates a black 1/1 Assassin with menace, then a separately targeted ability returns Bonesplitter and attaches it. The token becomes 3/1. |
| No Equipment | The token is still created; no Equipment is returned. |
| Opponent-owned Equipment | Equipment in the opponent's graveyard is not returned by this ability. |
| Target exiled | Equipment exiled after target selection stays exiled. |
| Token removed | The Equipment still returns, but remains unattached. |
| Source removed | Removing Ratonhnhaké꞉ton after its trigger is stacked does not stop that ability. |
| Creature Equipment | March of the Machines makes returned Bonesplitter a creature; it returns unattached. |
| Noncombat damage | No token-creation trigger is generated. |
| Full prevention | Solitary Confinement prevents the combat damage, so no damage trigger or token is created. |
| Noncombat prevention | The Wanderer prevents noncombat damage to its controller. |
| Combat control | The Wanderer does not prevent the same combat damage; the normal token/Equipment sequence proceeds. |
| Combat doubled | Furnace of Rath changes three combat damage to six, but creates only one damage trigger and one token. |
| Noncombat doubled | Six noncombat damage is dealt without creating a combat-damage trigger. |
| Doubling plus full prevention | All damage remains prevented; there is no damage trigger. |
| Initial trigger countered | Disallow resolves against the first trigger; no token or reflexive trigger is created. |
| Reflexive trigger countered | Disallow resolves against the second trigger; the token remains and the Equipment stays in the graveyard. |
| Graveyard targeting blocked | Ground Seal is already active. The untargeted initial trigger still creates a token, but no reflexive ability can remain on the stack with a legal target. |
| Targeting blocked in response | Ground Seal becomes active after the Equipment was targeted. The reflexive ability does not resolve; the token remains 1/1 and the Equipment stays in the graveyard. |
| Targeting restored before selection | Ground Seal is removed while the initial trigger is pending. The later reflexive ability can target, return, and attach the Equipment normally. |
| Target zone roundtrip | The targeted Equipment is exiled and returned to the graveyard. It is a new object, so the pending ability cannot return it to the battlefield. |
| Protection from artifacts | Tel-Jilad Defiance resolves on the token before the reflexive ability. The Equipment returns to the battlefield but cannot attach; the token remains 1/1. |
| Hexproof | Ranger's Guile makes the token 2/2 with hexproof. An opposing nonartifact creature-targeting spell cannot target it, while its controller's can. The Equipment still attaches and the token becomes 4/2. |
| Shroud | Alexi's Cloak resolves attached to the token, granting shroud. Neither player's creature-targeting spell can target it. The reflexive ability targets only the Equipment, which returns and attaches; the token becomes 3/1. |
| Later protection detaches Equipment | The normal return/attachment completes first, making the token 3/1. Tel-Jilad Defiance then resolves. Native state-based actions detach the Equipment while both objects remain on the battlefield; the same token becomes 1/1. |
| Double then prevent, combat | The affected player's native callback chooses Furnace of Rath then Urza's Armor. Three incoming damage becomes 5; life is 35. One initial trigger and token are created, and Equipment returns/attaches. |
| Prevent then double, combat | The callback chooses Urza's Armor then Furnace of Rath. Three incoming damage becomes 4; life is 36. One initial trigger and token are created, and Equipment returns/attaches. |
| Double then prevent, noncombat | The same chosen order deals 5, but no combat-damage trigger or token is created. |
| Prevent then double, noncombat | The same chosen order deals 4, but no combat-damage trigger or token is created. |

All 41 cases passed against the unmodified pinned archive. The suite does not
turn a successful subset into a claim of complete card support. Existing export
coverage and identity reviews remain distinct from interaction correctness.

The [Aether Revolt release notes](https://magic.wizards.com/en/news/feature/aether-revolt-release-notes-2017-01-06)
document Disallow's ability targeting; the
[War of the Spark release notes](https://magic.wizards.com/en/news/feature/war-spark-release-notes-2019-04-19)
document The Wanderer's noncombat prevention. Current local Oracle records for
Furnace of Rath, Solitary Confinement, Disallow, and The Wanderer are frozen and
fingerprinted with the other fixture inputs. The suite is versioned as
`raton_equipment_v10`; previously saved v1-v9 reports remain historical artifacts.
Ground Seal, Tel-Jilad Defiance, Ranger's Guile, and Alexi's Cloak are pinned
with the other inputs. Urza's Armor, Plains and Island bring the total to 15
reviewed fixture identities, including an off-color mana control.

## Evidence and boundaries

`config/interaction_suite.json` is a schema-2 manifest holding reviewed card
identities, fingerprints of rules-relevant Oracle characteristics, source links,
and the fixed family inventory. Expectations live in nine individually bounded
files under `config/interactions/`:

| Family | Definition file | Cases |
| --- | --- | ---: |
| Equipment | `equipment.json` | 8 |
| Damage | `damage.json` | 6 |
| Countering | `countering.json` | 2 |
| Targeting | `targeting.json` | 4 |
| Protection | `protection.json` | 4 |
| Replacement ordering | `replacement_order.json` | 4 |
| Casting admission/payment | `casting.json` | 8 |
| Native priority/response loop | `priority.json` | 3 |
| Counterspell chains | `counter_chain.json` | 2 |

The original 28 expectations and 13 pinned identities remain unchanged; the
casting family adds eight independent cases and Plains, and the priority family
adds three cases and Island. The chain family adds two cases using the same
reviewed card identities. Expectations were specified from the rules,
not copied from engine output. Changed Oracle characteristics require reviewing the fixture. The command
accepts no uploaded state, script, case file, arbitrary file path, or card input.
Code fixes family paths, order, and exact case IDs. Missing/duplicate/reordered
families or cases fail admission.

Each case constructs a fresh four-player partial state. The original interaction
families deal damage through
the engine, inspect the first trigger before resolution, inspect the reflexive
trigger after token creation/target selection, apply the specified between-trigger
zone change, and resolve the next ability. Casting and priority families use the
distinct checkpoints described below. Observations include life, stack
size, target IDs/counts, token identity/properties, equipment ownership/zone,
source zone, and attachment state. Initial setup mismatches and absent checkpoints
are inconclusive. A witnessed mismatch fails. CLI exit code zero requires every
case to pass; a completed process alone is insufficient.

Counterspell checkpoints require two stack entries and a spell-ability target.
The verifier independently compares the raw target ID, the underlying stack
entry's ID, and the trigger ID captured at the preceding checkpoint. Missing
IDs are inconclusive; contradictory bindings fail even if summary flags say
the counterspell targeted correctly. Initial snapshots also verify that each
prevention/doubling permanent is active under the intended controller.

The new target-legality cases use rules 603.3d (no legal initial target), 608.2b
(recheck targets on resolution), and 400.7 (a zone change creates a new object).
The [Wilds of Eldraine release notes](https://media.wizards.com/2023/downloads/WOE_Release_Notes_9S5bvPQTjRwh/EN_MTGWOE_ReleaseNotes_20230518.pdf)
confirm Ground Seal's battlefield-only restriction on graveyard targets. Its
enter-the-battlefield draw trigger is outside these controlled static-effect tests.

Injected cards receive a native game timestamp before placement. The verifier
binds raw target IDs **and** zone timestamps across selection and response. The
roundtrip also requires direct Exile/Graveyard transition records, increasing
timestamps, and the original pending target. Missing data is inconclusive;
contradictory identities fail. `equipment_targeted` is only a same-card-ID summary,
not a statement of current target legality; matching IDs alone cannot pass these
new target-object checks.

The defense cases resolve the actual pinned spell/Aura through Forge's stack,
then inspect the granted keyword, token statistics, remaining trigger, and
Equipment outcome. Rules 702.16d, 702.11b, and 702.18a distinguish protection's
Equipment restriction from hexproof/shroud target restrictions. The existing
Assassin's Creed release notes explain returning Equipment even when attachment
is impossible. The early-protection case checks attachment refusal. The new
late-protection case checks subsequent detachment under rules 702.16d and 704.5n.
Only that case requires an additional `after_detachment` checkpoint, following
the witnessed normal attachment in `after_second`. The actual protection spell
is resolved and Forge performs state-based actions; the fixture never directly
unattaches the Equipment. Raw attachment IDs, stable Equipment/token IDs and zone
timestamps, and the response's target/stack/graveyard evidence are required.
Missing or unexpected checkpoints cannot pass. An unattached final state alone
is insufficient.

Response evidence independently binds its raw target ID/timestamp to the original
token, requires the same reflexive trigger below it and still pending afterward,
and checks the resolved spell's zone. The Equipment target remains separately
bound by ID/timestamp and distinct from that token. Missing data is inconclusive;
contradictory evidence fails even if keyword summary flags match.

Target-eligibility probes create unplayed Tel-Jilad Defiance spell abilities for
each player and call the native targeting predicate. Its nonartifact source and
unrestricted creature target isolate hexproof's opponent-only restriction and
shroud's restriction on both players. Probes never enter a zone or resolve.
They do not prove mana payment, legal cast timing, or AI decisions.

The generated directory under `data/runs/` retains `interaction-inputs.json`,
nine `observations-<family>.json` receipts, `interaction-report.json`, and
`interactions.log`. The worker uses one bounded JVM process for all nine families.
Each definition, input document, and receipt is limited to 128 KiB and 32 nesting
levels; duplicate fields are rejected. Generated inputs/receipts are size-checked
before exclusive creation. Receipts use compact JSON; the combined report
retains indented state evidence for inspection.

Report schema 2 retains all 41 `checks` and adds `partition_results`, containing
family IDs, statuses, Java versions, and receipt `observations_sha256` hashes.
There is no combined `observations.json` in new runs. Consumers of earlier
reports must use the named receipts and per-family hashes for new reports.
A missing/misidentified receipt makes the combined result inconclusive; any
witnessed rules mismatch fails it. Nonzero/unfinished processes or conflicting
Java versions cannot pass. CLI success requires every fixed family to pass.

Reports include Oracle/catalog evidence, source/bootstrap hashes, engine pins,
bridge versions, process outcome, and artifact hashes. `definition_hashes` binds
the exact parsed bytes of the manifest and all nine definitions; `suite_hash` is
a fingerprint of that fixed path/hash map. Formatting changes also alter it.
Definition preflight, worker admission, and post-run checks prevent changed
definitions from producing a valid conclusion. A source or pinned-engine change
during execution likewise invalidates the conclusion.

The worker uses isolated Python imports and a minimal child environment, the
existing engine profile lock/restoration, a 90-second outer timeout, an 8 MiB log
cap, and a 2 GiB JVM heap cap. This is not an OS sandbox or a total memory quota.
The optional bridge is trusted local tooling; its use does not expose a server.

`native/ReplacementChoiceController.java` is compiled from the repository's
fixed source with `--release 17`, UTF-8, and `-proc:none`. No deck-provided code or
annotation processor is compiled. The compiler has a 30-second timeout and
64 KiB log cap; the generated class is capped at 64 KiB. Generated class files
and `compiler.log` remain in the run directory, outside Git. Reports record
compiler executable hash/version, compilation outcome/log hash, source hashes,
and class hash. The worker checks the class before loading it, every receipt
binds its hash, and post-run checks reject changed class/compiler evidence.
The compiler is trusted local tooling, not a new sandbox or a pinned toolchain.

The fixture deliberately bypasses shuffling/opening hands and injects controlled
partial states. Ground Seal placement/removal and between-trigger zone changes
model resolved effects; they do not
prove that the AI casts a response legally or passes priority correctly. These
tests also do not establish deck-construction legality, full Commander zone rules,
all replacement effects, every protection/hexproof interaction, every targeting
condition, or broader combo correctness.
Normal user simulations retain their existing construction/review/execution gates.

The Wanderer's starting loyalty is initialized through the engine counter API
from its pinned Oracle value; this does not test its enter-the-battlefield setup.
Disallow is put on the stack with a chosen legal ability target and resolved
by the engine. Its mana payment, legal casting window, and AI decision to cast
it are not tested. The same casting/priority boundary applies to the three new
response spells, including the Aura. Their native effects are resolved rather than
assigning keywords directly. The prevention/doubling combination has an order-independent
zero-damage result. The separate replacement-order family now exercises the
5-versus-4 outcomes for the fixed single-source, single-recipient damage event;
it does not certify all replacement/prevention effects or simultaneous recipients.

## Paid casting and controlled admission

The casting family has three fixed checkpoints: `before`, `after_attempt`, and
`after_resolution`. A rejected proposal does not resolve anything, including an
existing pending spell in the nonempty-stack control. Unknown/extra/missing
checkpoints cannot pass.

| Case | Required behavior |
| --- | --- |
| Paid instant | Ranger's Guile pays G from a real Forest, appears on the stack targeting its controller's creature, then resolves to the graveyard and grants +1/+1. |
| No mana | Native `playStack` returns false; the card stays in hand with no payment or new stack entry. |
| Wrong color | A Plains cannot pay G. Native rejection preserves the untapped land, hand, and stack. |
| Paid permanent | A Forest's G pays Bonesplitter's generic cost; the artifact appears on the stack and then resolves to the battlefield. |
| Noninstant in combat | Native `canPlay` is false despite affordable mana; the fixture does not invoke the payment entry point. |
| No priority | Native phase data shows another player has priority. The fixture's priority admission gate declines the proposal without invoking payment. |
| Nonempty stack | A separate instant is first cast with native payment. Native legality then blocks the noninstant; the pending spell and remaining Forest are preserved. |
| Instant in combat | With priority and green mana, Ranger's Guile still casts, pays, and resolves during the controlled combat phase. |

Rules 117.1a and 601.2g-h motivate these expectations. The pinned
[SpellAbilityRestriction](https://github.com/Card-Forge/forge/blob/forge-2.0.15/forge-game/src/main/java/forge/game/spellability/SpellAbilityRestriction.java)
checks native timing through `canPlay`;
[ComputerUtil.playStack](https://github.com/Card-Forge/forge/blob/forge-2.0.15/forge-ai/src/main/java/forge/ai/ComputerUtil.java)
checks payment and pays through the native cost machinery. Its low-level payment
entry point expects eligibility to have been checked first. The fixture calls
it for eligible proposals even when `canPayCost` predicts failure, to witness
native unaffordable/off-color rejection.

The eligibility values are frozen pre-cast observations, repeated in subsequent
checkpoints for auditing. Phase and priority ownership are initialized explicitly.
The priority gate belongs to this harness; these cases do not exercise native
priority-pass rotation, opponent responses, or prove that the payment entry point
rejects calls made without priority. Targets are prepared directly after a native
`canTarget` check; target choice is not an AI-strategy test.

Accepted casts require a native payment receipt tied to the original untapped
Forest, its timestamp, green color, and controller. Raw resource/payment/stack
counts must agree with summaries. Stack ability/card IDs bind the proposed spell,
and stack target IDs bind the prepared target. Spell timestamps are recorded, but
are not assumed to advance when entering the stack. Rejected cases must preserve
tracked resources, pool, card identity/timestamp/zone, target state, and pending
stack identities. Matching acceptance or payment-count flags alone cannot pass.
Additional/variable costs and rollback after an already-started partial payment
remain outside this family.

## Repeatable validation

Offline tests mutate expected observations to ensure early target selection,
incorrect return/attachment, wrong ownership, noncombat triggers, missing evidence,
false integer values, process failure, and provenance changes cannot pass.
They test the verifier, not the engine. The actual-engine test is explicitly opt-in:

```powershell
$env:COMMANDER_INTERACTION_INTEGRATION = "1"
.\.venv\Scripts\python.exe -m pytest tests/integration/test_interactions_integration.py -q -s
Remove-Item Env:COMMANDER_INTERACTION_INTEGRATION
```

The priority family below exercises selected native passes and opponent
responses. Additional/variable costs, simultaneous effects/APNAP choices,
longer response chains and representative combos remain future work. Future
families must retain the existing
per-document 128 KiB limits and fixed, reviewed admission rules.

For replacement ordering, the pinned engine's
[ReplacementHandler](https://github.com/Card-Forge/forge/blob/forge-2.0.15/forge-game/src/main/java/forge/game/replacement/ReplacementHandler.java)
calls the affected player's controller through `chooseSingleReplacementEffect`;
[PlayerControllerAi](https://github.com/Card-Forge/forge/blob/forge-2.0.15/forge-ai/src/main/java/forge/ai/PlayerControllerAi.java)
delegates that choice to its AI. Rule 616.1 gives the relevant player the choice.
The fixed test controller now overrides only that native choice method for
Furnace of Rath/Urza's Armor, without assigning damage or editing scripts. It
records actual candidate effect/card IDs, zone timestamps, controller IDs,
chosen IDs, and the deciding player. The verifier independently binds them to
the pre-damage permanents and affected player, requires two candidates then the
remaining one, and checks both selected orders at every subsequent checkpoint.
Missing/malformed choice evidence is inconclusive; contradictory evidence fails
even when summary counts and life totals match. These are controlled player
choices, not evidence that the default AI chooses optimally.

Every new case needs an independent rules expectation and a negative test before
its result contributes to any future card-support claim.


## Native priority and paid opponent responses

Suite v9 adds three independent cases without changing the original 36 expectations.
Island is the fifteenth pinned Oracle identity. Rules 117.3b–d, 117.4, 117.7 and
118.3 govern retained priority, turn-order passes, successive-pass resolution,
response order and affordability. Forge's pinned
[PhaseHandler source](https://github.com/Card-Forge/forge/blob/forge-2.0.15/forge-game/src/main/java/forge/game/phase/PhaseHandler.java)
provides the native `mainLoopStep()` used here.

| Case | Required behavior |
| --- | --- |
| All pass | Seat 0 pays for Bonesplitter with a Forest, retains priority, then seats 0–3 pass. Seat 1 has an affordable Disallow but deliberately passes. Bonesplitter enters the battlefield. |
| Paid counterspell | Seat 1 pays {1}{U}{U} using three Islands to counter the pending Bonesplitter. Seat 1 retains priority, then seats 1, 2, 3, 0 pass before native resolution. Both spells finish in their owners' graveyards. |
| Unaffordable response | Seat 1 has Disallow but only two Islands. Native cost checks reject affordability; the scripted controller passes without attempting payment. Disallow remains in hand, Islands remain untapped, and Bonesplitter resolves. |

Each case has `before` and `after_sequence` checkpoints. The latter includes raw
callbacks and snapshots after each native loop step. The verifier independently
checks ability IDs, selected spell targets, callback order, retention after casting,
the restarted pass cycle, stack order, zones, source identities/colors/payment,
tapping, life totals and stable phase/active player. Missing or malformed evidence
is inconclusive; a witnessed disagreement fails. Unit counterexamples cover early
resolution, skipped passes, wrong targets, forged payments and altered resources.

The fixed `PriorityTestController.java` only scripts choices returned to Forge's
normal controller API. Its inherited casting path performs payment; the Python
observer never moves priority or directly resolves a pending spell. The fixture
initializes a partial main phase and calls `onStackResolved()` once to enable the
priority loop before any cast. This bootstrap does not represent game startup.
Execution is limited to 16 steps and 32 callbacks per case, within the existing
90-second worker/128 KiB receipt limits. Both fixed Java classes are separately
hashed at compilation, before startup, in receipts and after execution.

These cases validate selected priority sequences, not all priority rules or the
default AI's strategic choices. The chain family below now covers two selected
counter-counterspell sequences. Additional/variable costs, interrupted casting
rollback and broader card combinations remain future work. Production deck
legality and bracket review gates are unchanged.


## Counter-counterspell chains

Suite v10 adds a ninth bounded family, preserving all 39 previous expectations and
all 15 pinned card identities. It uses the same native loop and fixed controller,
with a third player who may propose a distinct Disallow targeting the first
Disallow. The controller still returns only fixed action choices; Forge owns mana
payment, stack changes, pass rotation and resolution.

The pinned [Comprehensive Rules](https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.pdf)
117.3b–d, 117.4 and 117.7 require priority after casting/resolution, consecutive
passes and response order. Rule 118.3 governs affordability; rules 701.6a–b send
a countered spell to its owner's graveyard without refunding its paid costs.

| Case | Required behavior |
| --- | --- |
| Paid second counterspell | Seat 0 pays for Bonesplitter with one Forest. Seat 1 pays for Disallow using three Islands. Seat 2 pays for a second Disallow using three different Islands, targeting seat 1's pending Disallow. Seats 2, 3, 0, 1 pass. The second Disallow resolves, both counterspells enter their owners' graveyards, and Bonesplitter remains on the stack. The active player receives priority; seats 0–3 must pass again before Bonesplitter enters the battlefield. All seven used lands remain tapped and both counterspells remain paid. |
| Unaffordable second counterspell | Seat 2 has only two Islands. Native affordability is false, so no second counterspell is cast or paid. Seats 2, 3, 0 finish passing, then seat 1's Disallow counters Bonesplitter. Seat 2's Disallow remains in hand and its Islands remain untapped. |

In addition to the two fixed priority checkpoints, raw native step snapshots bind
the actual post-payment `stack_targets` to pending ability IDs and record every
player's remaining mana. The verifier checks all intermediate zones, source
identities/controllers, tapping, payments, stack order, the second full pass cycle,
and priority returning to the active player between resolutions. Two different
Disallow objects cannot be substituted for one another because their card and
ability IDs are independently bound at setup and every native step.

Negative tests reject a second counterspell targeting Bonesplitter, early original
spell resolution, skipped passes, wrong priority ownership after resolution,
refunded costs, use of another player's lands, malformed target IDs, missing
mana/target evidence and an unaffordable cast. Existing process, heap, JSON and
callback limits apply unchanged. These selected three-player sequences do not
certify longer stacks, all cards, additional/variable costs or default AI strategy.


Primary-control integration uses the fixed `primary_control_worker.py` with four
fixed-name, hash-bound receipt parts under the unchanged 128 KiB per-document
limit. Eighteen positive scenarios exercise five sources across heuristic, hybrid
and full modes, including both Secret payment paths. Five affordable no-benefit
controls require a supported decline. Check actual targets, payer-only costs,
loyalty, sacrifice/exile, original-world independence, control timing and survival.
A completed full plan may contain up to three actual paid equip prefixes.

Separate native tests cover discounted terminal outcomes, nested/parallel random
scopes, exception restoration, completed-plan deadline recovery and resolving-parent
boundaries with lethal free-cast triggers. They are not complete legal-deck games
or exhaustive rules/strategy certification. See `engine/primary_control_evidence.json`
for exact sources, before/after outcomes, gates and limitations.


## Paid spell-control witnesses

`tests/support/spell_control_worker.py` exercises twelve real native choices in
three fixed receipt parts: each contains Aethersnatch, Commandeer and Chef's Kiss
with mana payments, plus Commandeer's two-other-blue-card alternate payment.
`tests/integration/test_spell_control_integration.py` checks the untouched original
world, restored random provider, real source/stack identities, native payments,
changed controllers/targets and final damage/survival. Receipt names and order
are fixed, each part uses the existing bounded JSON reader/writer, and the
manifest binds the verified runtime and unchanged Default profile.
The previous runtime baseline is retained separately; no uploaded deck can
select a worker program. The reviewed runtime is active after the full validation gate; all twelve
packaged cases pass. See [bound evidence](../engine/spell_control_evidence.json).


## Composed-control and cast provenance

The fixed `composed_control_worker.py` records both Invert Polarity branches,
Sudden Substitution and Perplexing Chimera in each of three AI modes, plus ordinary
and stolen Zacama in a separate provenance part. Four fixed receipt names are
hash-bound in the manifest and use the existing bounded JSON I/O. Native mana
payments, source/target identities, ownership, controllers, original cast counts,
announced targets, damage and land untaps are checked. Optional trigger decisions
are exercised through native resolution; no script is made mandatory. The three
native boundary fixtures document explicit manual choices separately. All fourteen
packaged cases pass; the identical previous-runtime fixture has twelve failures
and two controls. Sudden Substitution records both the creature target and its
graveyard outcome, rather than assuming the stolen Shock damages a player.
The ordinary-Zacama control also checks trigger processing after the final
resolution. Final validation passes 39 live tests / 619 subtests. See
[bound evidence](../engine/composed_control_evidence.json).


## Mass counter removal

The fixed worker requests Aether Snap, Dust of Moments, Oblivion Stone and
Pestilent Haze in heuristic, hybrid and full simulation modes, plus two Aether
Snap source-decline controls. Four fixed receipt names bind native outcomes to
the engine, unchanged Default profile and file digests through bounded JSON I/O.
The cast path is `playChosenSpellAbility`, including native modal selection;
actual paid mana, original card identities, counter values and resulting zones
are checked. Oblivion Stone is first cast for three mana, then its activation
pays five mana and sacrifices it. No selected action is forced when AI declines.
The previous-runtime baseline has eight failing criteria and six passing controls.
All fourteen candidate cases pass; the complete live suite passes 40 tests /
633 subtests. See [bound evidence](../engine/mass_counter_evidence.json).
