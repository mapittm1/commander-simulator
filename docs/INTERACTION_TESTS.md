# Controlled card interaction checks

These tests inspect engine state at defined checkpoints, using Forge 2.0.15's
actual damage, trigger admission, stack resolution, zone-change, and state-based
action APIs. They are separate from full-game AI smoke tests and user simulations.

```powershell
.\.venv\Scripts\python.exe -m pip install -r requirements-interactions.txt
.\.venv\Scripts\python.exe commander_simulator.py engine-interactions
```

The command requires the pinned engine, a fresh catalog, and the exact optional
JPype version. The existing Windows Java runtime exposes its JVM library; standard
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

All 20 cases passed against the unmodified pinned archive. The suite does not
turn a successful subset into a claim of complete card support. Existing export
coverage and identity reviews remain distinct from interaction correctness.

The [Aether Revolt release notes](https://magic.wizards.com/en/news/feature/aether-revolt-release-notes-2017-01-06)
document Disallow's ability targeting; the
[War of the Spark release notes](https://magic.wizards.com/en/news/feature/war-spark-release-notes-2019-04-19)
document The Wanderer's noncombat prevention. Current local Oracle records for
Furnace of Rath, Solitary Confinement, Disallow, and The Wanderer are frozen and
fingerprinted with the other fixture inputs. The suite is versioned as
`raton_equipment_v3`; previously saved v1/v2 reports remain historical artifacts.
Ground Seal's Oracle characteristics are pinned as the ninth fixture identity.

## Evidence and boundaries

`config/interaction_suite.json` holds reviewed card identities, fingerprints of
rules-relevant Oracle characteristics, case intentions, expected checkpoints,
and source links. Expectations were specified from the rules, not copied from
engine output. Changed Oracle characteristics require reviewing the fixture.
The command accepts no uploaded state, script, case file, or arbitrary card input.

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

The generated directory under `data/runs/` retains `interaction-inputs.json`,
`observations.json`, `interaction-report.json`, and `interactions.log`. Reports
include Oracle/catalog evidence, suite/source/bootstrap hashes, engine pins,
bridge/Java versions, process outcome, and artifact hashes. Suite/input/worker
receipt JSON is limited to 128 KiB and 32 nesting levels; duplicate fields are
rejected. Worker receipts use compact JSON within the unchanged cap; generated
reports retain indented state evidence for inspection. A source,
suite, or pinned-engine change during execution invalidates the conclusion.

The worker uses isolated Python imports and a minimal child environment, the
existing engine profile lock/restoration, a 90-second outer timeout, an 8 MiB log
cap, and a 2 GiB JVM heap cap. This is not an OS sandbox or a total memory quota.
The optional bridge is trusted local tooling; its use does not expose a server.

The fixture deliberately bypasses shuffling/opening hands and injects controlled
partial states. Ground Seal placement/removal and between-trigger zone changes
model resolved effects; they do not
prove that the AI casts a response legally or passes priority correctly. These
tests also do not establish deck-construction legality, full Commander zone rules,
all replacement effects, protection/hexproof interactions, every targeting
condition, or broader combo correctness.
Normal user simulations retain their existing construction/review/execution gates.

The Wanderer's starting loyalty is initialized through the engine counter API
from its pinned Oracle value; this does not test its enter-the-battlefield setup.
Disallow is put on the stack with a chosen legal ability target and resolved
by the engine. Its mana payment, legal casting window, and AI decision to cast
it are not tested. The prevention/doubling combination has an order-independent
zero-damage result; affected-player choice between outcome-changing replacement
orders remains unverified.

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

Next interaction work should cover outcome-changing replacement ordering,
protection/hexproof restrictions, cost/priority handling, and combos.
Every new case needs an independent rules expectation and a negative test before
its result contributes to any future card-support claim.
