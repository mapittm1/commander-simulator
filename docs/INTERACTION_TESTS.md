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

## Initial scope: Ratonhnhaké꞉ton and Equipment

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

All eight cases passed against the unmodified pinned archive. The suite does not
turn a successful subset into a claim of complete card support. Existing export
coverage and identity reviews remain distinct from interaction correctness.

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

The generated directory under `data/runs/` retains `interaction-inputs.json`,
`observations.json`, `interaction-report.json`, and `interactions.log`. Reports
include Oracle/catalog evidence, suite/source/bootstrap hashes, engine pins,
bridge/Java versions, process outcome, and artifact hashes. JSON documents are
limited to 128 KiB and 32 nesting levels; duplicate fields are rejected. A source,
suite, or pinned-engine change during execution invalidates the conclusion.

The worker uses isolated Python imports and a minimal child environment, the
existing engine profile lock/restoration, a 90-second outer timeout, an 8 MiB log
cap, and a 2 GiB JVM heap cap. This is not an OS sandbox or a total memory quota.
The optional bridge is trusted local tooling; its use does not expose a server.

The fixture deliberately bypasses shuffling/opening hands and injects controlled
partial states. Between-trigger removals model resolved effects; they do not
prove that the AI casts a response legally or passes priority correctly. These
tests also do not establish deck-construction legality, full Commander zone rules,
all replacement effects, every targeting condition, or broader combo correctness.
Normal user simulations retain their existing construction/review/execution gates.

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

Next interaction work should add controlled cases for prevention/replacement,
countering the reflexive trigger, protection/target restrictions, and combos.
Every new case needs an independent rules expectation and a negative test before
its result contributes to any future card-support claim.
