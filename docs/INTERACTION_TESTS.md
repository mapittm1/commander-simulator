# Controlled card interaction checks

These tests inspect engine state at defined checkpoints, using Forge 2.0.15's
actual damage, trigger admission, stack resolution, zone-change, and state-based
action APIs. They are separate from full-game AI smoke tests and user simulations.

```powershell
.\.venv\Scripts\python.exe -m pip install -r requirements-interactions.txt
.\.venv\Scripts\python.exe commander_simulator.py engine-interactions
```

The command requires the pinned engine, a fresh catalog, the exact optional
JPype version, and a trusted installed JDK 17+ compiler. `javac` is discovered on
PATH; set `COMMANDER_JAVAC` to its executable to select another local compiler.
The compiler is used only for the fixed test controller; the configured pinned
JRE still runs Forge. The existing Windows Java runtime exposes its JVM library; standard
Linux/macOS library paths are recognized, but the live checks were verified on
Windows. Ordinary catalog, deck, and simulation commands do not need this bridge.

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

All 28 cases passed against the unmodified pinned archive. The suite does not
turn a successful subset into a claim of complete card support. Existing export
coverage and identity reviews remain distinct from interaction correctness.

The [Aether Revolt release notes](https://magic.wizards.com/en/news/feature/aether-revolt-release-notes-2017-01-06)
document Disallow's ability targeting; the
[War of the Spark release notes](https://magic.wizards.com/en/news/feature/war-spark-release-notes-2019-04-19)
document The Wanderer's noncombat prevention. Current local Oracle records for
Furnace of Rath, Solitary Confinement, Disallow, and The Wanderer are frozen and
fingerprinted with the other fixture inputs. The suite is versioned as
`raton_equipment_v7`; previously saved v1-v6 reports remain historical artifacts.
Ground Seal, Tel-Jilad Defiance, Ranger's Guile, and Alexi's Cloak are pinned
with the other inputs. Urza's Armor is the thirteenth reviewed fixture identity.

## Evidence and boundaries

`config/interaction_suite.json` is a schema-2 manifest holding reviewed card
identities, fingerprints of rules-relevant Oracle characteristics, source links,
and the fixed family inventory. Expectations live in six individually bounded
files under `config/interactions/`:

| Family | Definition file | Cases |
| --- | --- | ---: |
| Equipment | `equipment.json` | 8 |
| Damage | `damage.json` | 6 |
| Countering | `countering.json` | 2 |
| Targeting | `targeting.json` | 4 |
| Protection | `protection.json` | 4 |
| Replacement ordering | `replacement_order.json` | 4 |

The original 24 expectations and 12 pinned identities remain unchanged; the
replacement family adds four independent cases and Urza's Armor. Expectations were specified from the rules, not copied from engine
output. Changed Oracle characteristics require reviewing the fixture. The command
accepts no uploaded state, script, case file, arbitrary file path, or card input.
Code fixes family paths, order, and exact case IDs. Missing/duplicate/reordered
families or cases fail admission.

Each case constructs a fresh four-player partial state. It deals damage through
the engine, inspects the first trigger before resolution, inspects the reflexive
trigger after token creation/target selection, applies the specified between-trigger
zone change, and resolves the next ability. Observations include life, stack
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
six `observations-<family>.json` receipts, `interaction-report.json`, and
`interactions.log`. The worker uses one bounded JVM process for all six families.
Each definition, input document, and receipt is limited to 128 KiB and 32 nesting
levels; duplicate fields are rejected. Generated inputs/receipts are size-checked
before exclusive creation. Receipts use compact JSON; the combined report
retains indented state evidence for inspection.

Report schema 2 retains all 28 `checks` and adds `partition_results`, containing
family IDs, statuses, Java versions, and receipt `observations_sha256` hashes.
There is no combined `observations.json` in new runs. Consumers of earlier
reports must use the named receipts and per-family hashes for new reports.
A missing/misidentified receipt makes the combined result inconclusive; any
witnessed rules mismatch fails it. Nonzero/unfinished processes or conflicting
Java versions cannot pass. CLI success requires every fixed family to pass.

Reports include Oracle/catalog evidence, source/bootstrap hashes, engine pins,
bridge versions, process outcome, and artifact hashes. `definition_hashes` binds
the exact parsed bytes of the manifest and all six definitions; `suite_hash` is
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

Next interaction work should cover cost/priority handling, simultaneous effects
and APNAP choices, and representative combos. Future families must retain the existing
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
