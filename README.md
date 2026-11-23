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
The quality gate skips generated and archived folders before walking their files.

Companion wording reviews use an exact archive checksum to select a bounded
file under `config/companion_condition_reviews/`. The policy file and selected
review file both contribute to the approval digest. Other engine archives do not
need to be loaded, and a missing or malformed review cannot approve a wording
difference.


## Card coverage

The selected runtime correctly marks an attacker blocked when a native effect
assigns a blocker. That state persists if the blocker leaves before damage.
The Block API evaluates the complete paid parent, so classic/hybrid AI can judge
removing a blocker and assigning it to another attacker together.

All 1,136 focused source and 2,361 native tests pass, retaining all 2,333 prior
native methods and adding 28. Independent/repeated packaged groups pass
1204/65/194/146; broader gameplay/serialization pass 3,835/256. All 45 live tests
and 2,257 subtests pass. Post-move health is ready; construction passes for 32,636
starting identities with seven nonstarting meld exclusions. See the
[preserved reblocking failures](engine/combat_reblocking_gap.json),
[source validation](engine/combat_reblocking_native_evidence.json),
[packaged checks](engine/combat_reblocking_packaged_evidence.json), and
[activation evidence](engine/combat_reblocking_evidence.json).

The 28 fixed contracts retain identical baseline bytes. Four ordinary damage
controls passed before correction; four paid Shock/departure cases exposed an
incorrect defending-player loss. The state-only prototype fixed rules/damage
but retained eight AI declines until the Block policy was added. Original/copied
cases cover paid explicit choices, optional decline, no-response lethal and all
three AI modes. Two existing production files/classes change and one BlockAi
source/class is added. Card scripts, profiles and test assertions are unchanged.
Other blocker histories, combat restrictions, trigger policies and complete
Commander rules or strategy remain unproven.

The earlier combat-removal milestone evaluated paid combat-removal actions and their legal targets
against the remaining combat. Classic/hybrid AI can reach that policy even when a
host carries an old deck-exclusion hint. It can choose the lethal attacker among
multiple threats and pays the real activation cost before removing it.

That milestone passed 1,108 focused source and 2,333 native tests, retaining every prior 2,291
native method and adding 42. Independent/repeated packaged groups pass
1176/65/194/146; broader gameplay/serialization pass 3,807/256. All 45 live tests
and 2,229 subtests pass. Post-move health is ready; construction passes for 32,636
starting identities with seven nonstarting meld exclusions. See the
[preserved combat failures](engine/combat_removal_gap.json),
[source validation](engine/combat_removal_native_evidence.json),
[packaged checks](engine/combat_removal_packaged_evidence.json), and
[activation evidence](engine/combat_removal_evidence.json).

The 42 fixed contracts retain identical baseline fixture bytes. Paid/Fog and
no-response lethal controls separate resolution from willingness. Original/copied
states cover single/multiple attackers, indestructible creatures, hexproof,
tapped sources and unavailable mana. Two production files and three existing
classes change; no classes, card scripts or profile changes are added.

Large native receipts use 500-case parts with hash-bound indexes while preserving
the 128 KiB document and 90-second worker limits. The first packaged run passed
all 1,176 methods but failed writing the receipt; it remains preserved. Corrected
independent/repeated runs pass. Nine offline regressions verify bounded round
trips, legacy receipts, tampering, fixed names/counts and oversized cases.
Triggered removal, optional/mandatory reblocking choices and complete Commander
rules or strategy remain unproven.

The earlier draw-step milestone applied first-extra replacements to the first eligible draw,
while exempting the first card drawn during each own draw step. Draws during
another player's draw step count as extra draws. Global draw-step history is
preserved in original games and active/common snapshots.

That milestone passed 1,066 focused source checks and 2,291 native tests, retaining 2,267 native
cases and adding 24. Independent/repeated packaged families pass 1134/65/194/146;
broader gameplay/serialization pass 3,765/256. All 45 live tests and 2,187 subtests
pass. Post-move health is ready; construction passes for 32,636 starting identities
with seven nonstarting meld exclusions. See the [preserved per-event failures](engine/draw_step_replacement_gap.json),
[source validation](engine/draw_step_replacement_native_evidence.json),
[packaged checks](engine/draw_step_replacement_packaged_evidence.json), and
[activation evidence](engine/draw_step_replacement_evidence.json).

The 24 fixed contracts retain identical baseline fixture bytes and cover own-step
first/second/third draws, equal three-card batch totals, foreign-step and MAIN2
histories, and original/active/common worlds. Card scripts and AI profiles are
unchanged. Other replacement/prevention combinations, denied or replaced draw
history, strategy and complete Commander correctness remain unproven.

The earlier Recruit/draw-provenance milestone evaluated paid Recruit actions and preserves copied draw
history, drawn-card flags and last-drawn provenance. Native cost admission and
AI payment share a current-turn, current-hand and card-timestamp check, rejecting
previous-turn cards and cards that left and returned to the hand.

That milestone passed 1,042 focused source checks and 2,267 native tests, retaining all 2,212
prior native methods and adding 55. Independent and repeated packaged families
pass 1110/65/194/146; broader gameplay/serialization pass 3,741/256. All 45 live
tests and 2,163 subtests pass. Post-move health is ready, and construction passes
for 32,636 starting identities with seven nonstarting meld exclusions.
See the [preserved last-drawn failures](engine/last_drawn_payment_gap.json),
[source validation](engine/last_drawn_payment_native_evidence.json),
[packaged checks](engine/last_drawn_payment_packaged_evidence.json), and
[activation evidence](engine/last_drawn_payment_evidence.json).

The 21 supplied last-drawn cases cover actual mana/tap/exact-discard/draw payment
and original/active-copy/common-snapshot independence. Recruit tests isolate
supported composition under a willing counter policy. The default probabilistic
counter policy and optional Recruit decisions still need work. Card construction
and selected passing tests do not establish complete Commander rules or strategy.
Card scripts and Default/Cautious profile files are unchanged.

The earlier delayed-reveal milestone routed the active delayed-reveal wrapper through shared
per-card rights. Only current matching views in declared/current zones resolve;
stale agents and foreign/copied view aliases cannot redirect fresh information.
Audience and zone/owner display groups are captured before callbacks, preserving
outside-game restrictions, original/current-agent memories and single/multi-zone
prefixes. No private histories are copied or card-name exceptions added.

That milestone passed 987 focused source checks and 2,212 native build tests, retaining all
2,181 prior native methods and adding 31. Independent and repeated packaged
families pass 1055/65/194/146; broader gameplay/serialization pass 3,686/256.
Its 45 live tests and 2,108 subtests passed. Post-move health is ready; construction
passes for 32,636 starting identities with seven nonstarting meld exclusions.
See the [source evidence](engine/delayed_private_reveal_native_evidence.json),
[packaged milestone](engine/delayed_private_reveal_packaged_evidence.json), and
[validation and activation](engine/delayed_private_reveal_evidence.json).

The eighteen-case paid Ancient Stirrings/Impulse baseline passes ten controls
and fails eight master/post-control assertions; all eighteen methods now pass.
Thirteen instrumented privacy/view/callback/metadata boundaries include a
preserved intermediate prefix failure. These delayed-reveal phases have no
fixture compilation or Checkstyle failure. Direct view paths bypassing this
wrapper, all UI/history/temporary-controller/nested contexts and autonomous
strategy still need meaningful validation. Scripts/default AI are unchanged.
Complete Commander card/rules/AI correctness remains unproven.

The earlier direct-reveal runtime routes the RevealHand Look and PeekAndReveal private
disclosure paths through shared per-card rights. Private library audiences are
captured before callbacks;
public library audiences also include dormant agents and agents removed during
reveal callbacks. Declared positions follow those captured audiences without
copying private histories or adding card-name exceptions.

All 956 focused source checks and 2,181 native build tests pass, retaining all
2,159 prior native methods and adding 22. Independent and repeated packaged
families pass 1024/65/194/146; broader gameplay/serialization pass 3,655/256.
All 45 live tests and 2,077 subtests pass. Post-move health is ready; construction
passes for 32,636 starting identities with seven nonstarting meld exclusions.
See the [source evidence](engine/direct_private_reveal_native_evidence.json),
[packaged milestone](engine/direct_private_reveal_packaged_evidence.json), and
[validation and activation](engine/direct_private_reveal_evidence.json).

The original eighteen-case paid baseline passes ten controls and fails eight
master/post-control knowledge assertions; all eighteen methods now pass. Four
callback/public-position boundaries include two preserved intermediate failures.
The initial unused-import Checkstyle stop also remains preserved. Recording-agent
fixtures explicitly construct controller transitions; they do not establish all
printed nested control-card rules. Other direct/delayed effect callbacks,
agent/history unification, temporary controllers, all nested/rollback contexts
and autonomous strategy still need meaningful validation. Scripts/default AI
are unchanged. Complete Commander card/rules/AI correctness remains unproven.

The earlier controlled-information runtime's shared reveal and inspected-library
paths preserve fresh in-game observations for the controlled player, their controller and the acting
agent. Library audiences are captured
at inspection time, so later controllers do not inherit earlier private peeks.
Private reveals distinguish in-game and outside-game cards. Explicit receiving
agents retain their own memories, and foreign views cannot select local cards
through matching IDs. Public reveal/return facts update original/current/dormant
agents independently of optional owner display; blind draw history stays intact.

All 934 focused source checks and 2,159 native build tests pass, retaining all
2,117 prior native methods and adding 42. Independent and repeated packaged
families pass 1002/65/194/146; broader gameplay/serialization pass 3,633/256.
All 45 live tests and 2,055 subtests pass. Post-move health is ready; construction
passes for 32,636 starting identities with seven nonstarting meld exclusions.
See the [source evidence](engine/controlled_information_native_evidence.json),
[packaged milestone](engine/controlled_information_packaged_evidence.json), and
[validation and activation](engine/controlled_information_evidence.json).

The original fourteen-case baseline passes six and fails eight; all fourteen
methods remain and pass after correction. Eighteen paid cases include real
control, rearrange, private inspection, counters, failed payment and copied states.
Nine audience and fifteen reveal/return boundaries also use explicitly constructed
control graphs and recording agents; they do not certify all nested control-card
resolutions. Failed private/public reveal phases remain preserved. This correction copies no private
histories at control changes and adds no card-name exceptions. Scripts/default AI are unchanged. All agent-identity/history,
temporary-controller, nested/rollback and strategy contexts remain broader
validation work. Direct private callbacks in other effect APIs still need native
validation. Complete card/rules/AI correctness is unproven.

The earlier [autonomous rearrange milestone](engine/autonomous_rearrange_evidence.json)
introduced generic paid primary rearrange choices, X preparation, legal target
search, private knowledge and deadline cleanup. Those contracts remain retained.

The earlier [library-transition milestone](engine/library_knowledge_transition_evidence.json)
introduced bounded exact-object observations and declared index transforms for
previously known positions outside a library inspection. Those contracts remain retained.

The earlier [library-choice milestone](engine/library_choice_knowledge_evidence.json)
introduced shared actual-inspection and announced-placement callbacks, distinguishing
seen identities from known shuffled positions. Its native contracts remain retained.

The earlier [information milestone](engine/information_choice_evidence.json)
introduced generic paid information choices, bounded utility, explicit forgetting
and resolving-zone boundaries. Its native and packaged contracts remain retained.

The earlier [inspection milestone](engine/inspection_ai_evidence.json) introduced
generic face-down inspection choices, privacy and knowledge-sensitive target
pruning. Its original checks remain in the current native and packaged families.

Earlier combat-role validation preserved independent blocking and attacked-planeswalker
roles when a permanent loses one card type. All 712 focused source checks pass;
the 146-case Battle family includes twelve paid dual-role controls. Independent
and repeated packaged checks, broader regressions, all 45 live tests and 1,834
subtests pass. See the [full validation](engine/dual_combat_role_evidence.json)
and [resolved gap](engine/dual_combat_role_gap.json). The original baseline failures
and corrected harness-origin lookup remain preserved. Full card/rules/AI
correctness remains open.

The earlier test-only Battle extension passed 134 cases, retaining 110 and adding 24
paid type-restoration and countered-restoration checks. Song of the Dryads,
Disenchant and Counterspell exercise original, copied and nested pending stacks;
regaining a type does not restore the old attack. See the
[restoration evidence](engine/defender_restoration_evidence.json). These supplemental
tests left production code and the then-current 1,902-method build unchanged.

Combat now remembers a defender's legal type when each attacking group forms,
keeps independent copies of that history, and retires it when the defender leaves
combat or combat ends. An attacked Battle becoming a Forest no longer redirects
trample-over-planeswalkers damage to the attacking player. All 676 focused source
checks pass; the 110-case Battle family retains 78 methods and adds 32 type-change,
blocked, pending-spell and nested-copy controls. See the
[source evidence](engine/defender_typechange_native_evidence.json),
[full validation](engine/defender_typechange_evidence.json), and
[resolved gap](engine/defender_typechange_gap.json).
Supplied legal state and selected cases do not certify all card, rules or AI choices.

Internal combat snapshots now preserve valid assigned damage after an attacked
permanent leaves combat. Absent-defender stand-ins no longer enter the damage
table; real blocker, permanent and player damage remain intact. All 644 focused
source checks pass. The 78-case Battle family retains its prior 62 methods and
adds sixteen internal snapshot and paid-equip trigger controls. See the
[source evidence](engine/damage_assignment_snapshot_native_evidence.json),
[full validation](engine/damage_assignment_snapshot_evidence.json), and
[resolved gap](engine/damage_assignment_snapshot_gap.json).
Internal snapshots are not player priority windows. Autonomous invocation
frequency and full card, rules and AI correctness remain unverified.

The reviewed runtime corrects damage assignment after an attacked planeswalker
leaves combat: trample over planeswalkers no longer reserves a damage point for
the absent target. Live loyalty and real blocker requirements remain intact.
All 628 focused source checks pass; the 62-case Battle family retains its prior
34 methods and adds 28 removed/live target, removal-response and paid-equip Jitte
controls. See the [source evidence](engine/removed_defender_native_evidence.json),
[packaged/full validation](engine/removed_defender_evidence.json), and
[resolved gap with original reproductions](engine/removed_defender_gap.json).
These selected cases do not certify all card, combat or autonomous AI behavior.

First-strike assignment history now survives copied combat, including live and
historical departed combatants in independent collections. All 600 source checks
pass. The 34-case Battle family retains ten cases and adds 24 boundaries covering
blocking, trample, pending defeat responses, first/double strike, paid keyword
changes, multiple creatures and nested worlds. The original one and expanded six
copy failures are resolved in the reviewed runtime. See the
[source comparison](engine/combat_strike_native_evidence.json),
[packaged validation](engine/combat_strike_evidence.json), and
[resolved gap with original reproductions](engine/combat_strike_gap.json).
These selected cases do not certify all combat or autonomous AI choices.

Shared Battle-protector snapshots preserve live and historical protector references
and public views in copied games and native LKI. All 566 source tests pass, with
17 Battle contracts. The original three and expanded nine old-runtime failures
are resolved, including Portent Tracker adding a counter where it should remove
one when given historical target metadata. Native paid activations, Stifle and
copied pending entry complement structural history controls. See the
[source comparison](engine/battle_protector_native_evidence.json),
[packaged validation](engine/battle_protector_evidence.json), and
[resolved gap with original reproductions](engine/battle_protector_gap.json).
Supplied actions and metadata do not certify autonomous strategy or complete combat.
Full card, rules and AI correctness remain open.

Generic phasing/imprint snapshots preserve phased-out physical cards and their
attachments, mapped phase owners, direct/suppressed phase-in flags and imprinted
references/public views. All 549 source checks pass with 25 new contracts,
including native untap, Out of Time/Disenchant, tokens, static abilities and copied
Chrome Mox mana. Intermediate failures isolate missing imprints after lookup was
corrected. See the [source comparison](engine/phasing_context_native_evidence.json),
[packaged validation](engine/phasing_context_evidence.json), and
[resolved phasing gap](engine/phasing_context_gap.json). Full card and AI
correctness remain open.

Generic Gift-context copying maps recipients into copied games and historical
state through the shared snapshot routine. All 524 source checks pass with 22 new
contracts, including paid Scrapshooter and Into the Flood Maw resolution, countered
spells, departed recipients and independent nested copies. The original eight and
expanded fourteen failed contracts now pass in the reviewed runtime. Their exact
reproductions and a corrected stackless-fixture setup remain preserved separately.
See the [source comparison](engine/gift_context_native_evidence.json),
[packaged validation](engine/gift_context_evidence.json), and
[resolved Gift gap](engine/gift_context_gap.json). Full card and AI correctness
remain open.

Shared choice-context copying preserves announced values, copied-world
references and public/secret presentation across zones and native last-known
information. The original four copy failures and the expanded 24 failed contracts
pass in the reviewed runtime. All 502 source checks pass with 32 new contracts,
retaining the earlier 470. Independent containers, historical and self-referencing
choices, secret reveal lifecycles and native Persecute resolution are covered.
See the [source evidence](engine/choice_context_native_evidence.json),
[packaged validation](engine/choice_context_evidence.json), and
[resolved gap](engine/spell_choice_copy_gap.json). Full card and AI correctness
remain open.

Generic mass tap/untap planning compares paid type and direction choices and
replays ordered player-target roles. Unplanned mass effects use isolated native
local projections. Chosen type/type2 state survives copies in every zone,
including a resolving Turnabout. All 470 source checks pass with 32 new contracts,
retaining the earlier 438. Paid and triggered controls cover Turnabout, Faces of
the Past, countered costs, stun, Wake Thrasher and changed battlefield types.
Card scripts and AI hints are unchanged. See the
[source evidence](engine/mass_tap_native_evidence.json),
[packaged validation](engine/mass_tap_evidence.json), and
[coverage limits](docs/CARD_COVERAGE.md#mass-tap-and-untap-choices).
Four additional spell-choice copy failures were subsequently resolved by the
shared choice-context correction above; their original reproductions remain in
the [resolved copy-state gap](engine/spell_choice_copy_gap.json). Full card and AI
correctness remain open.

Generic activated-discard planning compares legal single-card payments and
carries its choice through native cost approval, copied previews and full-pilot
replay. Actual Mind Over Matter and Trickster Mage payments enable the native
follow-up; separate Fatestitcher and Puppet Strings controls retain tap/mana
behavior. All 438 source checks pass with 40 new contracts. Larger discard-choice
sets retain native payment policy rather than searching a partial hand. Card
scripts and AI hints are unchanged. See the
[source evidence](engine/discard_payment_native_evidence.json),
[packaged validation](engine/discard_payment_evidence.json), and
[coverage limits](docs/CARD_COVERAGE.md#activated-discard-payments).
Full card and AI correctness remain open.

Tap/untap AI choices now honor native per-target controller defaults. Paid untaps
can enable an immediate follow-up, including two unique Hidden Strings targets.
The planner compares against plays already available and defers when its bounded
comparison is incomplete. Preview caches release projected games and preserve
live cache values across nested, failed and concurrent previews. All 398 source
checks pass, including 26 new interaction and cache contracts. Heuristic/hybrid
paid-cast controls supply the source action; full-pilot witnesses select both
the untap and its follow-up. Card scripts and AI hints are unchanged. See the
[source evidence](engine/untap_native_evidence.json),
[packaged validation](engine/untap_evidence.json), and
[coverage limits](docs/CARD_COVERAGE.md#tap-and-untap-decisions).
Full card and AI correctness remain open.

Runtime cost modifiers and nested expressions now survive ability copies and
previews, including pre-grant hand-spell assessments and colored reductions.
Native play-effect resolution and AI admission share modifier setup and use spell
casting costs. Generated hand copies retain their originals and receive no
hand-card loss compensation in hybrid safety. An independent control rejects paying
life when the resulting draw is prohibited.
All 372 source checks pass, including 27 new cases. Native Kefnet first-draw
witnesses pass under heuristic, hybrid and full simulation. Ogre Battlecaster
branch controls supply targets and do not establish autonomous attack selection.
Card scripts and AI hints are unchanged. See the
[source comparison](engine/cost_modifier_native_evidence.json) and
[packaged validation](engine/cost_modifier_evidence.json). Full card and AI
correctness remain open.

The earlier granted-cost correction preserves a casting cost already granted by
an effect, including
zero or higher mana costs, life and discard payments, mandatory sacrifices and
buyback. Twelve focused contracts plus 333 retained source checks pass. Native
Wizard's Spellbook branch resolution is retained as a control. The correction
changes one production class; card scripts and the default AI profile are unchanged.
See the [source comparison](engine/granted_cost_native_evidence.json) and
[packaged validation](engine/granted_cost_evidence.json). Full card and AI
correctness remain open.

Optional life-gain actions now preserve their once-per-turn allowance while life
gain is prohibited. Controls distinguish ordinary gains and gains replaced by life
loss. A legal reveal remains available even when its resulting gain is prohibited.
Skyfisher Spider's conditional exile requires a legal optional gain; Uro's attack
still draws a card when life gain is prohibited and the optional land is declined.

Held sacrifice-cost events capture leaving creatures' triggers from the event's
pre-move snapshot. Their death triggers survive even if the paid spell is later
countered. Eighteen controlled cases cover these behaviors; 79 focused and
neighboring checks also cover existing cost rollback and grouped costs.
See the [resolved failure record](engine/optional_life_gain_gap.json).
These are partial-state rules witnesses with supplied decisions and preconditions;
full card, cost, and AI coverage remain open.

Simple optional single- and multi-card draws now preserve their once-per-turn allowance
when a prohibition makes the entire optional draw illegal. Replacement draws,
declined and countered triggers, and empty-library loss retain their native behavior.
Choosing to pay a trigger cost remains independent of the resulting draw: Tymna
can still pay life when Narset prohibits drawing. Twelve controlled draw cases
and 91 focused/neighboring cases pass. See the
[resolved failure record](engine/optional_draw_gap.json).
These tests use supplied states and decisions. Compound draw effects need separate
coverage; full rules and AI coverage remain open.

Simple optional battlefield-copy choices preserve their once-per-turn allowance
when no eligible object remains at resolution. Calix can copy an eligible
enchantment after an earlier impossible choice. Opponent-controlled and nonenchantment
objects do not qualify. Twelve controlled cases also cover removal before
resolution, declined and countered triggers, noncombat damage, token doubling,
Crafty Cutpurse redirection, and legal last-known copies from departed sources.
All 103 focused and neighboring cases pass. See the
[resolved failure record](engine/optional_copy_gap.json).
These are partial-state witnesses with supplied decisions and native damage/casts;
complete copy, token, combat and AI coverage remain open.

Carpet of Flowers now records a use only after mana is actually added, rather than
when its parent or child ability resolves. Adding zero leaves the later main-phase
trigger available; draining mana after a positive addition does not reopen it.
Twelve controlled cases cover native paid Harrow, countered and copied triggers,
False Dawn's replacement, independent copied-game history, and native cleanup,
extra turns and replacement expiry. See the
[resolved failure record](engine/carpet_mana_gap.json).
The generic marker reuses existing snapshot-aware per-ability history. Only the
Carpet script flag changes; its Oracle text is unchanged. Initial phase events and
decisions are supplied; complete mana, combat and AI coverage remain open.

Simple optional counter placements now preserve their once-per-turn allowance
when no eligible recipient can receive counters. Nykthos Paragon and Leonardo can
place counters after Solemnity leaves. Mixed groups still receive the counters
that can be placed, and a legal placement replaced with zero uses the allowance.
Sixteen controlled cases also cover native responses, copied triggers, independent
game copies, extra-turn cleanup, and Guiding Hydra's independent counter-removal
payment, including a payment that causes its source to die. Five cases fail in the
previous engine; all sixteen pass with the correction. See the
[resolved failure record](engine/optional_counters_gap.json).
These are partial-state witnesses with supplied decisions and native paid spells;
compound counter effects, complete card behavior and default-AI correctness remain open.

Simple optional X changes now preserve their once-per-turn allowance when the
referenced spell or activated ability has left the stack. Glava can choose X=5
for a later spell after an earlier one is countered. A copied old trigger cannot
follow a remanded card into a new casting. Sixteen controlled cases also cover
same-value choices, source removal and return, copied triggers, independent game
copies, native extra-turn cleanup, and mandatory Unbound Flourishing doubling.
Four cases fail in the previous engine; all sixteen pass with the correction.
See the [resolved failure record](engine/optional_x_gap.json).
These tests supply initial states and decisions; complete X, card and default-AI
correctness remain open. No card scripts change.

Priority Boarding now exposes a separate, counterable reflexive exile trigger.
An impossible empty-library reveal preserves the once-per-turn allowance. The
trigger remembers the revealed object's last-known identity: it can still exile
that card after another card moves on top, but cannot follow it through a draw
or a trip out of and back into the library. Eighteen controlled cases cover
native paid responses, declined choices, equal mana value, queued rolls, copied
triggers, independent game copies and real cleanup/extra-turn permission expiry.
Eight cases fail in the preceding runtime; all eighteen pass with the correction.
The earlier prototype failed two added identity cases and was revised.
See the [resolved failure record](engine/priority_boarding_gap.json).
Only Priority Boarding's script changes; Oracle text is unchanged. Alias and
companion reviews were rebound after verifying their scripts are unchanged.
These are supplied-state rules witnesses; complete reveal, dice, card and default-AI
correctness remain open.

Declared optional casts now use their once-per-turn allowance only after native
casting succeeds. Emet-Selch retains a later opportunity after a casting restriction,
a declined cast dialog or failed payment. Countering a spell after it was cast
does not refund that use. PlayEffect rolls back effects prepared for a canceled
or failed cast, including the previously retained graveyard-exile replacement.
Sixteen controlled cases cover native mana and sacrifice costs, prohibitions,
counterspells, source movement, independent copied games and native extra-turn
cleanup. The preceding runtime fails seven cases and passes nine controls; all
sixteen pass with the correction. The earlier prototype failed the added
cancellation-cleanup check and was revised. See the
[resolved failure record](engine/optional_play_gap.json).
Only Emet-Selch's script flag changes; Oracle text is unchanged. Existing alias
and companion scripts were verified unchanged before review rebinding.
These are supplied-state witnesses; complete casting, alternative-payment,
card and default-AI correctness remain open.

Six Pantlaza discover witnesses now run in the trusted native and packaged suites:
resolution-time and last battlefield toughness, later responses, copied games and
original-turn ending. Their source bytes are unchanged from the earlier
[source-only validation](engine/discover_lki_evidence.json). See the
[packaged promotion evidence](engine/discover_lki_packaged_evidence.json).

Discover now uses zero when its calculated action number would be negative.
This preserves negative creature toughness for other rules calculations. Native
Pantlaza entry, Grasp of Darkness and Dismember reproduce two passing zero-value
controls and two failing negative-value cases in the preceding runtime; all four
pass with the correction, including independent copied games. Only the generic
DiscoverEffect implementation changes; card scripts and AI configuration are
unchanged. See the [resolved failure record](engine/discover_scalar_gap.json).
Complete discover, casting, card and default-AI correctness remain open.

Eight Imposter Mech / Pantlaza tests now run in the trusted native and packaged
suites: uncrewed zero toughness, crew before resolution, last known values after
Disenchant, independent copied worlds and native crew expiry after Time Stop.
The fixture moved from pending to active tests without byte changes. The earlier
[source-only vehicle evidence](engine/discover_vehicle_evidence.json) retains its
historical path; see the [packaged promotion evidence](engine/discover_vehicle_packaged_evidence.json)
for the current test location and Java 17 results.

Eight discover casting tests now run in the trusted native and packaged suites:
Drannith and Cage, modal and adventure limits, chosen split-half mana value,
adventure exile, combined split-card value, and excluded fuse/aftermath forms.
A native Lightning Bolt and a flashed Appraiser exercise Failure with an actual
pending spell. Their unchanged source moved into active tests; the earlier
[source-only casting evidence](engine/discover_cast_forms_evidence.json) retains
its historical path. See the [packaged casting evidence](engine/discover_cast_forms_packaged_evidence.json)
for current Java 17 results.

Eight discover cost tests now run in the trusted native and packaged suites:
Thalia, Trinisphere and Damping Sphere, payable and unpayable costs, actual mana
consumption, and combined cost increases before the minimum-cost check. The
unchanged fixture moved into active tests. Earlier [source-only tax evidence](engine/discover_tax_evidence.json)
retains its historical path; see the [packaged tax evidence](engine/discover_tax_packaged_evidence.json)
for current Java 17 results.

Ten sacrifice tests now run in the trusted native and packaged suites for Village
Rites and Shard Volley: payment before draw, prohibited casts, land versus nonland
costs under Yasharn, and graveyard movement replaced by exile. The unchanged
fixture moved into active tests. Earlier [source-only evidence](engine/discover_sacrifice_evidence.json)
retains its historical path; current packaged outcomes are in the
[combat AI release evidence](engine/switch_block_ai_evidence.json).

`SwitchBlock` now has a generic AI handler using bounded paid native forecasts.
Copies retain block records as independent last-known-state graphs, so Sorrow's
Path's forecast includes both its tap-trigger damage and its actual reblocking.
The two scripts remove only obsolete All-AI exclusion hints; random-deck exclusions,
Oracle text and the default AI profile are unchanged. Unchanged alias and companion
scripts were verified before binding their reviews to the new card archive.

Twenty-two supplied-state combat cases cover native paid swaps and damage,
actual choices and harmful-action refusals in heuristic/hybrid/full modes,
independent copied histories, pending tap triggers and illegal flying cross-blocks.
The baseline fails nine cases and passes thirteen controls; the correction passes
all twenty-two. General Jarkeld's full-mode case already passes in the baseline.
This resolves the [recorded handler and snapshot gap](engine/switch_block_ai_gap.json)
in the tested scope; full combat, card and optimal-AI correctness remain open.

Empower now has a generic paid-outcome AI handler; its native stack description
correctly names loyalty counters. Twenty-four native and packaged cases cover the
five root-ability cards, real mana/exile/tap/loyalty payments, token creation and
reuse, replacement effects, harmful-action refusals, copied pending resolution and
generated descriptions. The identical baseline passes fifteen controls and fails
nine cases; the correction passes all twenty-four. The full-mode beneficial choice
already passes in the baseline. Card scripts and the default AI profile are unchanged.
See the [native comparison](engine/empower_ai_native_evidence.json) and
[packaged validation and activation](engine/empower_ai_evidence.json).
Complete Empower, optional-trigger, card and optimal-AI correctness remain unverified.

Generic text-choice planning evaluates legal targets and replacement words through
paid native outcomes. Immutable AI choices remain separate from rules text and are
scoped to their activator and target incarnation. Full simulation records word
choices separately from card choices and resets multi-choice prefixes between targets.
Five root-card scripts remove only All-AI exclusion hints; Oracle text and random-deck
hints are unchanged.

The root fixture has thirty-three cases spanning six cards, paid combat survival,
three-mode choices/refusals and counterspell responses, copied pending resolution,
forbidden types, random isolation and useful cast triggers. Five composition cases
add Whim of Volrath, Mind Bend and Spectral Shift in full simulation, copied pending
modal resolution, and an explicitly supplied entwined cast with four native word
prompts and four mana paid. All thirty-eight source-native cases pass; the identical
baseline passes fourteen controls and fails twenty-four cases. Scope checks first prove replay works during
resolution, then reject changed actors and targets; outside-resolution prompts retain
ordinary choices. Earlier weak-action and premature-casting expectations retain
paid decline and instant-deferral controls.

The earlier 1,366-test candidate was rejected before activation: three composed
spells cast without replaying their useful words. The revision carries bounded word
transcripts across runtime child selection and copies, rebuilds deterministic entwined
mode chains before replaying their targets, and preserves those announced targets
at cast time. A target-ordering experiment was removed after diagnostics identified
the actual cast-chain mismatch.

The follow-up source-native suite passes fifty-four cases. Sixteen additional
cases cover creature/land plurals, negated color words, current irregular-plural
text, actual three-mode responses and timing controls. Against the earlier held
packaged build, six controls pass and ten cases fail. Response previews retain
established permanent value, capped at the original valuation so an unusable
temporary pump creates no immediate benefit. Reference values use private LKI
snapshots. Candidate-local thinking budgets preserve completed scored results.

A later 1,386-test build passed its source/resource audit, then an additional
packaged timing probe passed fifteen cases and failed the temporary-pump control.
It remains held. The corrected runtime passes 1,387 selected native tests and its
source/resource audit, 635 packaged rules cases, 2,861 broader gameplay tests,
256 serialization tests, and 42 live tests with 1,283 subtests. All 422 frozen inputs
remained unchanged. Catalog construction covers 32,636 starting identities and
excludes seven nonstarting meld results. See the
[native comparison and held predecessors](engine/text_choice_native_evidence.json)
and [packaged validation and activation](engine/text_choices_evidence.json).
The earlier [twelve-case finding](engine/change_text_ai_gap.json) remains historical.
Supplied entwine costs do not prove optimal optional-cost selection. A follow-up
composed-spell probe passes five full-mode controls and fails six heuristic/hybrid
casts across Whim of Volrath, Mind Bend and Spectral Shift. A reversible hint-only
diagnostic keeps those five controls passing and the six casts failing: removing
AI exclusions alone does not resolve the mode-planning gap. All three card scripts
were restored and all 2,159 production sources verified unchanged. See the
[pending mode finding](engine/composite_text_ai_gap.json). Full text-change/card
correctness, hidden-information behavior and optimal AI remain unverified.

A private-knowledge candidate retains current-object information without sharing
memory between games, players or control agents. Publicly identified returns to
hand retain their identity; unseen library moves and face-down exile returns do
not create observer knowledge. Reads filter old incarnations without changing the
original player's store during a preview. Fresh inspection replaces old references.

All 170 selected source-native checks pass, including eighteen knowledge cases and
named-discard, copy, text-change, control, decision and plan-recovery neighbors.
The identical eighteen-case packaged baseline passes ten controls and fails eight
cases. Two intermediate drafts remain held after stronger public-return and
control-layer tests found regressions. See the
[native comparison](engine/private_knowledge_native_evidence.json).
The earlier 1,405-test build and audit passed, but it remains held. Its broader
run passed 2,878 of 2,879 cases. Follow-up checks found that timestamp-only filtering
lost an inspected top card when it was drawn. The original failed build, reports
and 424 frozen inputs are preserved.

The corrected source tracks only explicitly disclosed library positions and carries
them through verified draws. Shuffles and hidden reorders invalidate that tracking;
physical card ids alone cannot identify a draw. Knowledge survives turn boundaries,
while per-turn planning reservations still expire. Copied agents receive mapped,
independent knowledge and library revisions.

All 189 selected source checks pass, including sixteen Bauble/knowledge cases and
three fixed opening replays. The prior runtime passes nine of the sixteen cases;
the held candidate passes eight, with different failures. Native payment, copied
slowtrips, bottom draws, selective reveals and all three ordinary AI modes are
covered. Only Mishra's Bauble's obsolete All-AI hint is removed; Oracle text is preserved.
See the [knowledge finding](engine/private_library_knowledge_gap.json),
[classification finding](engine/bauble_ai_gap.json) and
[source evidence](engine/private_knowledge_native_evidence.json).
The reviewed runtime passes 1,424 selected native tests and its source/resource
audit, 672 packaged rules cases, 2,898 broader gameplay tests, 256 serialization
tests, and 42 live tests with 1,320 subtests. All 428 reviewed inputs remained
unchanged. Catalog construction covers 32,636 starting identities and excludes
seven nonstarting meld results. A test-only metadata migration requires Bauble's
normal AI eligibility and broad permanent discovery; the other excluded cantrips
retain their narrow fallback checks. Its paired packaged checks pass all fifteen
cases on this runtime and reject the previous archive in two expected assertions.
The first broader failure and initial policy prototype remain preserved.
See the [packaged validation and activation](engine/private_knowledge_evidence.json).
LookAt AI policy, optimal information valuation and complete
hidden-information correctness remain open.

Generic modal-text planning passes 225 selected source-native checks,
including 26 composed-spell scenarios covering actual casts, land-word choices,
copied pending choices, buyback reservation and no-benefit controls, plus a forced-
mode budget witness. The identical 26-case packaged baseline passes eight controls
and fails eighteen cases. Modes are prepared before
targets; replacement words are planned on selected child abilities. Full simulation
records resolution-time generic mode tokens in order with its word choices.
Supported normal paid casts are evaluated before applying a wait-for-buyback
preference. Existing preservation policy remains for other buyback spells.

The first draft passes 76 of 78 tests; the next passes 89 of 91. Stronger land-word
cases expose a full-simulation mode-selection gap, fixed in the retained 91-case
draft and the expanded 224-case run. See the
[source comparison](engine/modal_text_native_evidence.json). Only the three
reviewed All-AI exclusion hints are removed, with Oracle text unchanged. The fresh
1,450-test build passes its selected regressions and source/resource/class audit,
but remains held after a stronger native API budget probe: preparing 32 legal
forced modes exceeds a five-second test budget and reaches the worker's thirty-
second bound. This is not a claim about a printed card having 32 modes. All 432
frozen inputs and the failed probe remain preserved. The isolated correction
prunes branches that cannot reach the required mode count; all 225 source-native
checks pass, with the same forced-mode witness taking 6 ms in the retained run.
See the [budget finding](engine/modal_budget_gap.json). The corrected reviewed
runtime passes 1,451 selected native tests, 699 packaged interaction cases,
2,925 broader gameplay tests, 256 serialization tests and 42 live tests with
1,347 subtests. All 433 frozen inputs remain unchanged. Catalog construction
covers 32,636 starting identities and excludes seven nonstarting meld results;
construction alone does not establish rules correctness. The earlier 1,450-test
build remains held. See the
[packaged validation and activation](engine/modal_text_evidence.json).
Search is bounded;
random choices, repeat modes, multiple generic choosers, optimal optional-cost
selection and complete card correctness remain unverified.

A separate five-case paid Spy Network probe passes three controls and fails two
inspected-draw cases on both the prior reviewed and corrected modal runtimes.
The relevant inspection/memory classes and its card script are byte-identical.
An explicitly inspected top card loses its remembered identity after an unshuffled
draw, including copied pending resolution. Payment/private disclosure, unseen
and shuffled-draw controls pass. See the
[historical knowledge finding](engine/spy_network_knowledge_gap.json). The fixed
fixture now runs in the reviewed matrix below. AI admission policy is not tested
by this direct paid-cast probe.

The explicit-position source candidate now passes all 244 selected native checks,
including nineteen paid-card/API inspection scenarios. Their identical packaged
baseline passes nine controls and fails ten cases. Exact top/bottom `ValidAll`
qualifiers grant position knowledge only to the authorized observer. Identity-only,
filtered and mixed selections do not infer hidden positions. Interleaved draws,
hidden reorders, fresh inspection and copied pending resolution are covered.
A stronger one-card-library test catches a top-and-bottom conjunction gap in the
243/244 draft; the corrected source passes that case too. The change is confined
to the generic LookAt handler; card scripts and AI flags are unchanged. See the
[source comparison](engine/look_positions_native_evidence.json). The reviewed
runtime passes 1,470 selected native tests, 718 packaged interaction cases,
2,944 broader gameplay tests, 256 serialization tests and 42 live tests with
1,366 subtests. All 436 frozen inputs remain unchanged. The catalog probe
constructs 32,636 starting identities and excludes seven nonstarting meld results;
construction does not establish rules correctness. See the
[validation and activation](engine/look_positions_evidence.json). AI admission,
information-spending policy and complete card correctness remain unproven.

Shared parent-text handling passes 273 selected source-native checks, including
29 real Crystal Spray scenarios. Their identical packaged baseline passes fifteen
controls and fails fourteen cases. Modal child effects now use the same parent
card/spell target lookup during resolution and AI word planning. Tests cover both
word modes on permanents and pending spells, paid card draws, invalid targets,
draw restrictions, cast triggers, expiration and independent copied games.
Only Crystal Spray's All-AI hint is removed; Oracle text, its Random hint and
Default.ai are unchanged. A retained 269/273 draft used an incorrect opponent-land
expectation: native traces show the AI destroys four Mountains, preserving its
own three Islands. Corrected checks assert the actual word map and all four victims.
See the [source comparison](engine/parent_text_native_evidence.json). The reviewed
runtime passes 1,499 selected native tests, 747 packaged interaction cases,
2,973 broader gameplay tests, 256 serialization tests and 42 live tests with
1,395 subtests. All 441 frozen inputs remain unchanged. The construction probe
loads 32,636 starting identities and excludes seven nonstarting meld results;
construction is not rules certification. See the
[validation and activation](engine/parent_text_evidence.json). Full card and AI
correctness remain unproven.

Generic cipher-link copying passes 298 selected source-native checks,
including 25 paid encoding, timing, lifetime and combat scenarios. Their identical
packaged baseline passes twenty controls and fails five copied-state cases.
Both ends of each encoded relationship now refer to the copied game before static
abilities recalculate. Tests cover one and multiple encoded cards, actual combat
triggers, zone roundtrips and independent removal in copied/original games.
Card scripts and all AI flags are unchanged. See the
[source comparison](engine/cipher_links_native_evidence.json). The reviewed runtime
passes 1,524 selected native tests, 772 packaged interaction cases, 2,998 broader
gameplay tests, 256 serialization tests and 42 live tests with 1,420 subtests.
All 443 frozen inputs remain unchanged. Catalog construction loads 32,636 starting
identities and excludes seven nonstarting meld results; construction does not
certify rules correctness. See the
[validation and activation](engine/cipher_links_evidence.json). Actual cipher copy
casting, additional compositions and AI admission remain required coverage.
Full card correctness remains unproven.

Nine additional controlled cipher-casting regressions verify resolving,
countering and declining copies, later acceptance, repeated hits, changed
controllers and temporary noncreature encoders, including copied games. See the
[research and selection controls](engine/cipher_casting_native_evidence.json).
The reviewed build passes 1,533 selected native tests, 781 packaged interaction
cases, 3,007 broader gameplay tests, 256 serialization tests and 42 live tests
with 1,429 subtests. All 444 frozen inputs are unchanged. All 2,160 production
source files, packaged classes, card resources and AI hints match the prior
reviewed runtime. See the [validation and activation](engine/cipher_casting_evidence.json).
Scripted card selection isolates these rules paths from normal AI admission;
wider compositions and complete card correctness remain unproven.

Generic free-casting corrections pass 333 selected source-native checks,
including 26 new cases for normal pilots, cost-waiver preservation, distinct text
states, copy/original identity, taxes and casting restrictions. Copies offered
before they are registered in a game zone are covered separately. See the
[source comparison](engine/free_cast_native_evidence.json). The reviewed build
passes 1,559 selected native tests, separate packaged families of 781 and 26 cases,
3,033 broader gameplay tests, 256 serialization tests and 43 live tests with
1,455 subtests. All 457 frozen inputs remain unchanged. The card archive changes
only Trait Doctoring's All-AI exclusion; its Random hint and Oracle instructions
remain unchanged. Identity reviews retain both old and new archive bindings.
The new family keeps the normal 90-second worker limit. Shared execution sources
are checked for changes and retained in bounded process receipts. See the
[validation and activation](engine/free_cast_evidence.json). These selected checks
do not establish full card compositions, AI strategy coverage or rules correctness.

Rinoa's queued return retains the graveyard objects from the death event.
It cannot follow a card through exile and back. Tests cover simultaneous deaths,
tapped entry, a flying counter, and used and unused return allowances in copied
and restored games. The [initial failure record](engine/rinoa_return_gap.json)
now links to the expanded regression and its resolution.

Optional return choices now check movement prohibitions before using a
once-per-turn allowance. Tests cover Grafdigger's Cage, Kunoros, Soulless Jailer,
Weathered Runestone, and Worms of the Earth, including mixed creature/land choices.
Containment Priest still replaces an otherwise legal return and uses its allowance.
The shared rules checks are generic; card scripts declare the required destination.
See the [resolved failure record](engine/prevented_return_gap.json).
Full card and AI coverage remain open.

The current reviewed runtime passed 2,212 selected native tests, separate packaged
families of 1055, 65, 194 and 146 rules cases, 3,686 broader gameplay tests, 256
serialization tests, and 45 live integration tests with 2,108 subtests. All 533
implementation inputs stayed frozen through candidate validation. Earlier failed
source phases and baseline fixtures remain preserved. The Python quality gate passed 430 offline
tests with 520 subtests; 45 opt-in integration tests are skipped by default.
The refreshed catalog probe constructed 32,636 starting-card identities; its seven
other identities are nonstarting meld results. Construction parses native abilities
and is not proof of every interaction. Native receipts use class-qualified method
names. See the [validation and activation evidence](engine/delayed_private_reveal_evidence.json).
Complete card and AI correctness remain unverified.

The Reaper, King No More keeps its once-per-turn return available if the first
return is declined or the dead creature is exiled before the trigger resolves.
A successful return uses the allowance for that turn. This follows
[Comprehensive Rules 603.2h](https://media.wizards.com/2026/downloads/MagicCompRules%2020261106.pdf).
Native checks also cover Roaming Throne creating distinct Ulalek triggers:
one resolving trigger may copy another trigger from the same permanent.


Ulalek can pay two colorless mana to copy its controller’s spells and other
abilities. Tests verify copy order and exclude the resolving trigger itself.
If an opponent counters the Eldrazi spell, Ulalek can still copy an eligible
ability on the stack.

Spells and copies created during resolution preserve priority and stack order.
Tempt with Mayhem copies the selected spell in turn order. Mana planning uses
Gemstone Caverns’ luck counter and can spend Cavern of Souls’ colorless mana
when it is required. Delighted Halfling can protect a legendary spell when its
mana pays a generic cost.


Vault 13: Dweller's Journey excludes itself from chapter I targets. Ixidor's
Will counts Wizards controlled by every player when determining its payment.
Forge's experimental snapshot preserves queued abilities and the paid spell
and target references in a Counterspell response.

Incubator tokens retain their two faces and +1/+1 counters in copied games.
AI previews preserve transformation history so Homicidal Brute can return to
Civilized Scholar when its queued end-step trigger resolves. Restoring a
game snapshot restores the saved counters and transformation history.

Aeon Engine now resumes the normal turn sequence in the new direction after
pending extra turns. Tests also check Day's Undoing, Time Stop, and The War
Doctor's exile trigger during cleanup.

Vault 11 draws for each player when no creature receives a vote. Players who
receive no votes from Círdan may decline to put a permanent onto the
battlefield. Restoring a snapshot also restores Aeon Engine's turn direction.

Learn offers discard-and-draw in Commander and honors Retriever Phoenix's
replacement effect. Aeon Engine's reversed turn order survives AI previews
and game snapshots. Secret votes stay hidden from later voters until the
reveal. Time travel checks cover eligible objects and suspend after the last
time counter is removed.

Villainous choices resolve in turn order. With The Valeyard in play, each
choice resolves before the same player chooses again. The native tests use
Ensnared by the Mara to check that damage is visible at the next prompt and
that each choice exiles the expected cards.

Explore places the +1/+1 counter before the player decides where to leave a
revealed nonland card. Discover puts an uncastable card into hand. Native
checks cover Jadelight Ranger, Captain Kathryn Janeway, and Geological Appraiser,
including free casts with legal targets and cards with unpayable costs.

Declining an optional dice payment lets the original result resolve and leaves
the ability available for later rolls. Tests cover Monitor Monitor and Night
Shift of the Living Dead. Explore tests cover Jadelight Ranger and Captain
Kathryn Janeway, including an empty library and a creature that left the
battlefield before its explore effect resolved. That earlier build passed 1,013
focused native tests and 261 checks against the packaged JAR.

Dice interactions count retained rolls, carry the final results into AI
previews and game snapshots, and keep the copied history independent. Native
tests cover Ancient Copper Dragon, Circuits Act, Iron Mastiff, and Diviner's
Portent. The Monitor Monitor test supplies the optional reroll decision and
uses the engine to pay its mana cost. Forge's default AI still declines that
reroll. That earlier build passed 1,007 focused native tests and 255 packaged
checks. See [dice history and rerolls](docs/CARD_COVERAGE.md#dice-history-and-rerolls).

The Star Trek engine includes the reviewed alias for All Good Things... and
checks that the Saga's exiled cards share one common card type. The reviewed
Dilemma change preserves prior turn history when a cast is canceled and counts
entwine and accepted free casts once. Kira Nerys and ninjutsu preserve a
specified defender when a creature enters attacking. Hit It! reveals all five
cards to each player before moving them. See the
[Star Trek catalog and rules checks](docs/CARD_COVERAGE.md#november-11-catalog-refresh).

A failed AI payment restores supported sacrifices to the original battlefield
object, including its counters, controller, and ordinary equipment. The engine
preserves independent moves and sacrifices that were redirected into a library.
See [sacrifice payments](docs/CARD_COVERAGE.md#sacrifice-payments).

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
A Scryfall bulk refresh can apply a corrected Oracle ID to an ineligible token
printing while preserving its previous identity and other printings.
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
