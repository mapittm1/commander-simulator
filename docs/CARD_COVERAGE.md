# Card support and evidence

The earlier sticker-capable runtime used the official Forge source snapshot
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

## Announced choices and copied context

The reviewed runtime preserves announced choices in every copied zone and native
last-known information. A shared routine copies type/type2, commander color,
colors, names, number, player, parity, direction, mode, sector, selected cards and
player-keyed card groups. Player and card references map into the copied world;
historical and self-referencing selections retain their correct identities.
Mutable choice containers are independent of the original game.

Rules values and public presentation are copied separately. A secret choice can
retain an older public value until revealed. Revealing a type or player in the
copy does not reveal it in the original. Color/name view aliases are retained
inside a card when the original used them, without sharing mutable containers
between games. Native last-known information uses the same copying contract.

The previous reviewed runtime passed the supplied paid Persecute outcome control
but failed four copy checks, including losing its chosen color at the native
mid-resolution reveal checkpoint. Expanded baseline tests passed five controls
and failed 24 contracts. These are failed contracts, not 24 distinct card bugs.
The reviewed correction passes all 32 new contracts and retains the earlier 470
source checks. Real paid Persecute resolution complements structural tests for
all-zone choices, eliminated players, secret/reveal lifecycles and reference
mapping. Supplied choices do not establish autonomous strategic selection.

The fresh build passes 1,728 selected native cases. Separate packaged families
of 781, 65 and 130 cases pass twice, including the complete live integration run
of 44 tests with 1,624 subtests. Broader gameplay and serialization runs pass
3,202 and 256 cases respectively. The audit confines changes to three production
sources and five packaged classes, with no added or removed classes. Card data,
AI hints and the default profile remain unchanged. All 482 implementation inputs
remain frozen through validation. Catalog construction still covers 32,636
starting identities with seven nonstarting meld exclusions; this is not proof of
every interaction. See the [source comparison](../engine/choice_context_native_evidence.json),
[activation evidence](../engine/choice_context_evidence.json), and
[resolved gap with original reproductions](../engine/spell_choice_copy_gap.json).
Full card rules, all compositions and multiplayer strategy remain unverified.

## Mass tap and untap choices

The reviewed shared mass tap/untap policy replaces an outright AI rejection with
paid native outcome planning. Turnabout can choose a legal type and direction,
recover its caster's lands after payment and enable Rorix Bladewing. A full-pilot
witness selects and pays for both spells. Separate shared-API controls test
admission; they do not establish autonomous heuristic/hybrid discovery. Existing
card exclusion hints remain unchanged.

Resolution-time type and direction choices use the existing ordered replay
stream. Namespaced tokens are checked against current legal callback options,
with actor and ordered player-target roles bound to the stream. A changed actor,
changed player target, invalid type token or swapped player roles cannot reuse
the old choice. Copies consume their own stream without changing the original.
The two-node target-role case is a structural contract, not additional Turnabout
Oracle text.

Chosen type and secondary type now survive native copies of cards in every zone.
An independent reviewed-runtime failure demonstrates a resolving Turnabout losing
its chosen Land type before the correction. Six separate zone contracts also
check both fields and independent mutation in the copy. Secondary choices in
those fixtures are explicit raw state, not new printed card abilities.

Actual native controls cover paid Turnabout, countered costs, unaffordable setup
rejection, already available follow-ups, one stun replacement without suppressing
other untaps, and player hexproof gained after casting. An artifact creature
untaps once, and Wake Thrasher receives the corresponding native events.
Mycosynth Lattice added before resolution changes which permanents match the
chosen type. Faces of the Past controls retain the dead creature's shared-type
resolution and distinguish supplied choices from the ordinary AI direction.

Unplanned mass-direction decisions project the not-yet-applied native effect and
resulting triggers in isolated copied games, without replaying the resolving
parent. They compare paired seeds 0 and 4096. A public untapped-permanent resource
tie-break applies only when native value and availability scores agree. This is a
local policy; it does not certify every unfinished parent suffix or multi-turn
combo. Nested mass choices retain native fallback. The two samples are not an
exhaustive probability model.

Paid variants cover explicit nonrandom Card-type domains up to 32 options and
128 complete combinations. Oversized domains keep native policy rather than
searching only a prefix. Existing paid outcome, immediate-alternative, deadline,
heap and worker limits are unchanged. Thirty-two new contracts plus 438 retained
source cases pass. The mass-tap milestone expanded the packaged untap family to
98 cases, retaining all earlier single-target, discard-payment and preview-cache
checks. The choice-context correction above expands it to 130. Run it with
`COMMANDER_ENGINE_INTEGRATION=1` and a configured JDK compiler using
`.venv/Scripts/python.exe -m pytest -q -s tests/integration/test_untap_integration.py`.
See the [source comparison](../engine/mass_tap_native_evidence.json) and
[activation evidence](../engine/mass_tap_evidence.json). These selected controlled
states do not establish complete card compositions, arbitrary choice optimization,
multiplayer strategy or full rules coverage.

Five follow-up probes against the mass-tap runtime had one passing supplied
Persecute outcome control and four copy failures: colors, names and chosen-player
state were lost from stack cards, including the color at an actual Persecute
resolution checkpoint. The shared choice-context correction above resolves
these failures. The [resolved gap](../engine/spell_choice_copy_gap.json) preserves
exact original reproduction sources and distinguishes those failures from the
32 passing mass-tap contracts.

## Activated discard payments

The reviewed planner can choose which card pays an activated ability's single
unrestricted card-discard cost. Mind Over Matter can discard a spare Forest,
untap Gilded Lotus and enable Hellrider. Trickster Mage also pays its blue mana
and tap costs through native payment. Full-pilot witnesses select the setup,
spend the intended card and cast the follow-up; shared API controls test admission
and actual payment independently. Fatestitcher and Puppet Strings retain native
tap and mana cost behavior.

Private planning metadata binds the actor, source and ordered targets, including
card timestamps. Before payment, copied or replayed choices are checked against
current objects and native cost legality. Changed actors, costs, targets, departed
cards and cards returning as new objects cannot silently substitute a payment.
Native payment consumes the plan. Costs can move their source or target after
activation legality has been checked; that does not create an extra targeting
check during cost payment. Metadata is separate from editable card rules text.

The search covers at most 32 eligible discard cards under the existing shared
preview budget. Larger hands preserve the complete native payment policy rather
than searching only a prefix. A printed Squee beyond the boundary verifies that
fallback. Multi-card, random, constrained-type and full-hand discard costs continue
to use native behavior and are outside this new optimization. The bounded planner
still compares setup plus one follow-up with all immediate alternatives within
its existing limit and defers an incomplete baseline.

The positive Mind Over Matter and Trickster Mage states have already used their
land drop. Preserved exploratory expectations with a land drop available were
incorrect: the evaluator preferred playing the spare land. Separate negative
controls retain that decision. Sacrificing an ability's target as a mana source
is an existing AI policy exclusion, not a replay or rules-engine defect. Native
unplanned discard heuristics are unchanged.

Eight Hidden Strings resolution controls separately verify partial target loss,
both targets leaving, new object identities on return, and stun counters on either
target. Together with 32 activated-payment cases, they add 40 focused contracts
and retain 398 source checks. The packaged untap family now has 66 cases. Run it
with `COMMANDER_ENGINE_INTEGRATION=1` and a configured JDK compiler using
`.venv/Scripts/python.exe -m pytest -q -s tests/integration/test_untap_integration.py`.
See the [source comparison](../engine/discard_payment_native_evidence.json) and
[activation evidence](../engine/discard_payment_evidence.json). These selected
states do not establish arbitrary card compositions, complete multiplayer
strategy or full rules coverage. Autonomous heuristic/hybrid discovery remains
separate from shared API acceptance; card exclusion hints have not been changed.

## Tap and untap decisions

The reviewed untap correction honors the native default for each target: untap
a permanent controlled by the choosing player and tap one controlled by another.
Supplied-choice controls separately check actual untapping, stun counters, Stasis,
Wake Thrasher's controller, and a target that leaves and returns as a new object.

Paid planning compares an untap plus one immediate follow-up against the plays
already available. Full-pilot witnesses cast Twiddle before Hellrider and use two
distinct Hidden Strings targets before Rorix Bladewing. Separate shared-API controls
pay and resolve the setup and follow-up under heuristic and hybrid profiles;
those controls supply the source action rather than proving autonomous selection.
Unchanged card exclusion hints can still affect the broader pilot.

Already available follow-ups and insufficient or unavailable mana cannot justify
wasting an untap. The comparison evaluates at most 32 legal immediate alternatives
and 128 setup target outcomes under the existing AI deadline. An oversized baseline
defers the comparison; it does not declare the untap beneficial from partial data.
That bound control uses distinctly named copies of Opt to avoid hand-name
deduplication. It tests search completeness, not new Oracle card identities.

Preview caches have independent nested lifetimes and restore live values after
success, failure, clearing and simultaneous work on different threads. Scoped
paid outcomes release copied-game references instead of adding them to the live
AI cache. The failed pre-correction heap-exhaustion run is preserved separately;
heap and ordinary worker time limits were not increased.

All 26 focused contracts, 372 retained source cases and the independent packaged
and broader suites pass. Run the focused family with the reviewed engine and
`COMMANDER_ENGINE_INTEGRATION=1` using
`.venv/Scripts/python.exe -m pytest -q -s tests/integration/test_untap_integration.py`.
A configured JDK compiler is required. See the
[source comparison](../engine/untap_native_evidence.json) and
[activation evidence](../engine/untap_evidence.json). These controlled partial
states do not certify all card compositions, arbitrary binary-choice optimization,
complete multiplayer strategy or full rules coverage.

## Sacrifice payments

Demon's Herald restores the original creature after a later sacrifice becomes
unavailable. The tests check tokens, counters, an opponent-owned creature,
Grafted Wargear, and a sacrifice exiled by Rest in Peace. They also check that
Soulherder receives the independent exile trigger and that Blightsteel Colossus
stays in the library when an earlier sacrifice is reversed. See rule 734.1 in
the [Comprehensive Rules](https://magic.wizards.com/en/rules).

Feasting Troll King rejects an interrupted three-Food payment and restores the
first Food. A successful payment consumes all three, makes one Squirrel through
Camellia, and returns the Troll King from the graveyard.

The fixtures exercise the AI activation path. Sacrifices involving combat,
merged or face-down permanents, linked effects, and attachments with additional
unattach actions still need verification. Human payments and effect costs
retain their existing paths.

## Dice history and rerolls

Ancient Copper Dragon's retained d20 determines its Treasures after
Barbarian Class ignores the lower roll. Chira, All In and The Space Family
Goblinson each trigger once in that scenario. The ignored die cannot grant
Goblinson trample. Iron Mastiff keeps only the highest roll when different
opponents are attacked and applies its 10–19 result to its defending player.

A controlled Monitor Monitor decision rerolls two matching dice from Circuits
Act. The fixture pays the actual mana cost, applies Barbarian Class again,
creates two Clown Robot tokens from two distinct final results, and verifies
one third-roll trigger from Resolute Veggiesaur. The default AI currently
declines Monitor Monitor's optional reroll. Diviner's Portent verifies that
both forms of game copy retain the modified result and can change their dice
histories independently.

## November 11 catalog refresh

The refreshed Scryfall snapshot contains 32,643 identities marked legal for
Commander. The previous package matched 32,324 and lacked 312 Star Trek
starting-card identities. The Star Trek package constructed all 32,636 starting
cards. The seven remaining catalog identities are nonstarting meld results.

The Forge 2.0.16 Star Trek source contains 311 of those starting-card names.
The Saga uses a different spelling of the ellipsis. The engine adapter binds
that alias to the exact Oracle ID and packaged script. The build carries forward
the reviewed Wicker Picker script and the existing Ratonhnhaké꞉ton identity review.
See the [Forge release](https://github.com/Card-Forge/forge/releases/tag/forge-2.0.16).

All Good Things... checks whether the cards it exiled share a single card type
when chapter IV resolves. Native fixtures cover zero and one linked card, a
shared artifact type, and three cards whose overlapping types never occur on
all three. See the [Star Trek release notes](https://magic.wizards.com/en/news/feature/star-trek-release-notes).

An isolated CLI game imported and validated Forge's copies of the four Star
Trek Commander precons. It ran to completion at seed 42 and produced a
diagnostics report with 39 turn markers and 40 recorded casts. See the
[Star Trek Commander decklists](https://magic.wizards.com/en/news/announcements/star-trek-commander-decklists).

Forge 2.0.16 also adds bounded Flat Monte Carlo settings to the Cautious AI
profile. The live suite exercises selected decisions under the packaged engine.
Dilemma regression cases cover an unpaid cast, target-selection cancellation,
preservation of a previous dilemma, and single settlement through spell-ability
copies. A completed entwine counts once. A modal enters trigger and random
upkeep modes produce the expected counters, including Chira's tapped Treasure.
The Cascade fixture supplies an accepted legal card choice, mode and target.
Forge's normal AI declined that cast in the same position. See
[Wizards' Star Trek mechanics](https://magic.wizards.com/en/news/feature/star-trek-mechanics).

Assimilate cases cover a Dryad Arbor that retains Forest and returns to its
owner when The Best of Both Worlds reaches chapter III, a graveyard creature
entering as an artifact in time for Reckless Fireweaver, and Grafdigger's Cage
preventing the entry. Assimilate the Wounded returns three creatures from
opposing graveyards in one artifact-entry event.

Kira Nerys's replacement applies to her controller's creature while that player
controls an attacker. Momentary Blink checks the defender choice and absence
of a declared-attack trigger. Kaalia and ninjutsu keep their specified defender.
If that defender has left the game, their entrant stays tapped and out of combat.
Open Communications pays the full Beam me up cost, returns its creature, and
goes to exile on resolution, after Counterspell, and after Reprieve.

Captain Benjamin Sisko counts a completed cast that was countered and ignores
an unpaid cast. Kobayashi Maru retains a spent Hail mode through a failed
resolution and a change of controller, and creates no Dilemma after all
modes are exhausted. See the
[Star Trek release notes](https://magic.wizards.com/en/news/feature/star-trek-release-notes).

Hit It! reveals its top five cards to every opponent, even if none are lands.
Its chosen land enters tapped, and the other cards move to the graveyard
without triggering Zellix. The fix follows the
[official Hit It! Oracle clarification](https://magic.wizards.com/en/news/feature/star-trek-release-notes).

Additional native cases cover Station using the tapped creature's current or
last known power, Federation excluding Borg and duplicate types, and Battlefront
counting an attacked opponent who later lost. A script-characteristic comparison
prompted actual paid cases for Lukka, Lost Order of Jarkeld, Sasaya and Arlinn.
Those fixtures exercise selected characteristics and gameplay transitions.

Full rules and AI coverage remain open.

A landless Vintage replay showed that Flat Monte Carlo can offer and score
Mishra's Bauble, then pass because the improvement falls below its configured
threshold.

## Card-counter costs

Axiom Engraver restores the oil counter used for a failed activation and
preserves a separate counter removal that occurred during payment. The
engine also rejects the activation if the required oil counter disappears
before the counter cost is paid.

Tayam, Luminous Enigma can pay with multiple creatures and with legal
counters outside the AI's preferred type. The fixtures verify the exact
three-counter payment, rejection after a creature changes controllers or
loses its selected counter, and protection of stun counters under Fear of
Sleep Paralysis. Chandra, Fire Artisan's successful loyalty payment still
produces a seven-damage trigger. The fixture supplies the target.

The tests cover selected AI payments from card counters. Other counter
replacement effects and costs paid while resolving an effect still need
verification. See [card-counter evidence](../engine/card_counter_evidence.json).

## Energy and discard recovery

Era of Innovation returns six paid energy and removes its payment history
if the later sacrifice fails. A separate one-energy loss and its history
survive. A successful activation spends six and draws three cards.

Insolent Neonate returns an ordinary discard from the graveyard and a Madness
discard from exile. The checks cover prior payment records, discard history,
descend history, and another player's discard trigger.

Plague Reaver exercises payments that discard two cards. Reversible cards
return in their prior hand order. The checks cover ordinary and Madness
discards in the same payment and cards exiled by Rest in Peace. When
Darksteel Colossus is shuffled into its library, the engine retains that
move and only records the card that remains discarded. The rule limits
reversal of library moves and shuffles.
[Comprehensive Rules, section 734](https://magic.wizards.com/en/rules).

The tests cover selected discard replacements and non-effect AI payments.
Other nonmana costs, replacements that perform additional actions, and human
payment need separate checks. See
[energy and discard evidence](../engine/energy_discard_evidence.json).

## Combined mana and tap costs

Dune Diviner needs distinct Deserts when one supplies the mana and the other
pays the tap cost. Grove of the Guardian reserves the creatures it needs
while selecting mana from Avacyn's Pilgrim, Springleaf Drum, or Phyrexian
Altar. The fixtures exercise both insufficient and sufficient resources.

Eladamri, Korvecdal keeps its own tap cost separate from the creatures that
fund mana. The filter fixture uses Ancient Tomb to pay for Selesnya Signet
and leaves the two creatures available to tap. If Springleaf Drum loses its
selected helper during payment, the failed activation reverses the Drum's
tap, drops its reservation, and preserves the helper's separate exile.

These checks cover selected AI costs and filters. General search across all
multi-source and multi-cost payment orders and recovery of other nonmana
costs still need verification. See
[combined reservation evidence](../engine/combined_reservation_evidence.json).

## Tap costs and group selection

Weight of Conscience can activate with two Bears or a Bear and a Changeling.
The regression fixtures check that the paid ability exiles the enchanted
creature and that a pair with no common creature type is rejected.
Mossbridge Troll checks a total-power cost at and below its threshold.
Giant Ox can crew Renegade Freighter using toughness, and two one-power
creatures can crew it using their combined power.

The Wight of the Reliquary fixtures exercise recovery of the direct tap and
the associated single-card and group tap triggers. A separate tap by another
player and its trigger survive. Grove of the Guardian rejects a selected
creature that leaves the battlefield or changes controllers during payment.
If the Grove becomes unavailable after both creatures tap, the engine
restores both taps and their prior payment records. See
[tap payment evidence](../engine/tap_payment_evidence.json).

The recovery cases cover AI nonmana tap costs. Human payment, costs paid
during an effect, and reversal of tap costs inside mana abilities require
separate validation.

## Failed activation recovery

The Yawgmoth fixtures make a creature unavailable after life is paid. An
aborted activation restores the life total and life-loss history and discards
the associated Lich's Mastery, Font of Agonies, and Ob Nixilis triggers.
A successful activation still produces the triggers. The tests also preserve
a second player's separate life loss and verify recovery of the actual amount
lost under Bloodletter of Aclazotz.

Zopandrel pays four life across two Phyrexian mana symbols. The failed
activation restores both payments and clears the amount paid with Phyrexian
mana. The successful activation keeps both life-loss events.

The Vampiric Rites fixtures exercise undoable Swamp and Wastes activations,
black mana floated before the attempt, and a Swamp activation under
Overabundance. The engine refunds spent mana and keeps the consequences of
mana abilities it classifies as non-undoable. See
[Comprehensive Rules, section 734](https://magic.wizards.com/en/rules) and
[activation payment evidence](../engine/activation_payment_evidence.json).

Recovery for other nonmana costs, human payment, and costs paid while resolving
an effect requires additional rules checks. The test fault makes an otherwise
available resource disappear during payment and checks the resulting state.

## Sacrifice allocation

The native AI assigns distinct creatures to Demon's Herald's three color
requirements. A multicolored creature can satisfy any one eligible slot. The
engine checks the actual activated ability with two and three creatures.

For Altar's Reap, mana planning reserves the spell's sacrifice while considering
Blood Pet and Ashnod's Altar. The checks also exercise an Orzhov Signet
activation funded by Ashnod's Altar. With a second creature available, the
engine pays the Signet activation, uses its mana for the spell, and pays the
spell's sacrifice. With only one creature, it rejects the attempt.

The packaged test suite passed 125 cases and checked that the named production
classes came from the pinned JAR. The broader native suite passed 1,397
gameplay and 256 serialization checks. See
[sacrifice allocation evidence](../engine/sacrifice_allocation_evidence.json).

## Cast recovery and chosen mana

When payment fails, the AI returns the card to its original zone and position.
It holds the attempt's move-to-stack event until payment succeeds. A successful
Gravecrawler cast still queues Syr Konrad. Aborted Gravecrawler and Haakon
casts leave the card in the graveyard and suppress that trigger. A preexisting
Phyrexian Arena trigger survives a failed cast.

The partial-payment fixtures make a second mana source unavailable after the
first produces mana. The engine refunds mana spent on the failed cast and
undoes a reversible Island activation. It preserves the mana and damage from
the activation that Forge marks non-undoable under Overabundance. The rules
allow players to reverse legal mana abilities subject to specific exceptions.
[Magic Comprehensive Rules, section 734](https://magic.wizards.com/en/rules).

The Hall of Gemstone fixture resolves the actual upkeep trigger, then verifies
a paid Opt from a Forest. Wastes remains colorless. The 11 additional cases run
against both the pinned build and the packaged JAR. See
[cast recovery evidence](../engine/cast_recovery_evidence.json).

## Mana events and snow payments

Caged Sun's bonus is available when the land ability actually adds the chosen
color. The payment tests cover an opponent's copy, a nonland source,
multiple green mana from one activation, River of Tears before and after a land
play, and composition with Overabundance. Blood Pet verifies that a
sacrifice-only activation gives no Kinnan bonus.

Payment forecasting retains the mana type and source separately. A snow Island
and Caged Sun can pay Frostwalk Bastion's activation. Overabundance's nonsnow
bonus cannot pay a second snow cost. Mana Reflection can double the snow
Island's own mana to pay both snow costs. Ritual of Subdual supplies typed
colorless mana and prevents the replaced blue mana from triggering Caged Sun.

The 16 new cases run against both the pinned build and packaged JAR. The
catalog probe is construction evidence. Full failed-payment rollback and other
replacement interactions still require separate validation. See
[mana event evidence](../engine/mana_event_evidence.json).

## Reflected and triggered mana

Overabundance adds mana before dealing damage. Tests cover typed colorless
payment when an opponent controls the enchantment and observe two red mana in
the pool at the damage event during direct activation. A test with Izzet
Boilerworks verifies that the payer chooses blue for Thassa's Oracle even when
its hand favors red. Manual activation after the payment uses its usual choice
policy. An unaffordable spell stays in hand and leaves mana, life and the land
untouched.

Sasaya's Essence counts other same-name lands its controller controls and can
choose a different produced type for each bonus. Snowfall's bonus pays real
cumulative upkeep with either an ordinary or snow Island. Its mana is excluded
from previews for ordinary spells.

The 53 cases also cover Reflecting Pool, Exotic Orchard, Fellwar Stone, Mox
Amber and restricted mana from Plaza of Heroes. Some cases set the required
battlefield state directly. The archived run covers these interactions and
catalog construction. Strategy, other trigger combinations, and full
failed-payment rollback still need separate evidence. See
[reflected mana evidence](../engine/reflected_mana_evidence.json).

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

The reviewed patched source also passes **1,241 native tests in 96 selected AI and
gameplay classes**, with zero failures, errors or skips. This includes the 693 focused
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


## Forced blocking and prevention shields

Game snapshots now retain each card's timestamped forced-block map and map the
required attacker references into the new world. The collection is copied without
replaying the ability that established the requirement. Explicit cleanup payloads
map the affected blockers for duration-bound requirements, and map both the effect
card and affected card/player for finite prevention-shield expiry. Cleanup updates
the copied world's prevention view and player-stat event targets.

Sixteen native cases first reproduced eight missing copied block requirements and
eight lingering copied shields, covering both stack-copy modes. The candidate
passes all sixteen. Eight packaged witnesses repeat the paid paths:

- Hunt Down and Monstrous Step retain both chosen targets and the block requirement.
- Auriok Siege Sled pays its cast and activation costs against an artifact creature.
- A paid Avalanche Tusker uses a controlled attacker declaration and the native
  declared-attacker trigger. Wall of Stone remains alive through combat, so the
  requirement must actually expire at combat end rather than disappear on death.
- Mending Hands shields a player or creature. Other cases consume three of its
  four points before copying, or stack two shields for eight total points.
  After the copied turn ends, a paid Lightning Bolt deals three damage. The
  original world's shield still prevents damage until its own deadline.

The native fixtures assert canonical copied attacker identity, and both native
and packaged checks observe independent source/copy requirements or shield quotas.
The shield checks use actual damage and life totals, not shield descriptions.
The frozen local Oracle identities, valid failing baseline, compiled source hashes,
receipts and logs are retained in
[`blocking_prevention_evidence.json`](../engine/blocking_prevention_evidence.json).

Verification: **350 focused native tests**, **898 broader gameplay tests** including
those 350, **256 serialization tests**, **28 live integrations / 450 subtests**, and
**399 offline tests / 508 subtests**, with all Python quality tools. The frozen
catalog construction probe still passes **32,109 identities**, with the same seven
non-starting meld records excluded. Construction is not full gameplay evidence.

The fixtures use partial four-player registrations and controlled boards. Attacker
declaration is installed directly; the native attack trigger, blocker selection,
damage and deadlines run afterward. Phase advancement omits response priority.
Multiple concurrent forced-block timestamps, multi-attacker BlockAllDefined paths,
prevention subabilities, radiance/divided shields, already-consumed effect lifecycle,
blink, player loss, hidden views and arbitrary AI policies remain unverified.
Other callbacks and general continuations still need separate verification.


## Ring designations and text changes

The reviewed runtime preserves the temptation count, existing Ring effect card,
Ring-bearer reference and designation flag in game snapshots. It reuses the copied
emblem without tempting again or rebuilding its acquired abilities. Historical
card copies retain the designation; an ordinary paid Clone still does not copy it.
A stale loss callback now checks the player's exact current bearer, so an old
bearer belonging to another player cannot clear the former player's newer bearer.
That sequence first failed in the original game as well as copied games.

Color/type word maps, timestamped type-line layers and word-derived keyword layers
are restored with canonical copied traits. State-changed type records receive an
independent type value. Expiring a word edit refreshes the active keyword cache;
the original game previously kept islandwalk after the edit map was cleared.
Native fixtures cover temporary and permanent color edits, controlled legal type
choices, forestwalk/islandwalk restoration, and Forest/Island type and mana changes.
The two type-choice scenarios and original word-cache check are native-only;
they install a fixed controller choice rather than change the printed ability.

Exchange of Words previously had no AI handler, so its mandatory ETB effect was
skipped. A registered handler now selects legal targets, rejects an opposing
hexproof second target, uses the existing native creature evaluator with power/
toughness and mana-value contributions disabled for basic preferences, and supplies
a legal fallback for mandatory choices. This is a heuristic, not strategic
certification. Duration cleanup maps both creatures, retains incarnation guards,
and restores their original text without replaying entry triggers.

Forty native regressions cover both stack-copy modes plus original-game contracts.
The valid baseline had **39 failures and one existing positive outcome**: the
opposing-hexproof pair already remained unchanged while the entire exchange API
was unsupported. That positive outcome does not prove the old targeting policy
worked. The candidate passes all forty. Nineteen packaged witnesses cover:

- Temporary/permanent color edits with actual anthem power changes.
- Exchanged keywords/activated abilities, a controller-dependent Merfolk bonus,
  restored ETB drawing, real damage prevention, and a blinked incarnation guard.
- Ring levels one through four, the next temptation without duplicate emblems,
  leaving-play/control-change cleanup, the stale former-owner sequence in both
  original and copied games, and Clone versus historical designation behavior.
- Actual copied Ring looting and combat-damage life loss. A forced block by a
  surviving Wall of Omens also exercises the native end-of-combat sacrifice.
- The existing opposing-hexproof no-exchange outcome as a negative targeting case.

Frozen source hashes, the valid failing baseline, actual observations and limits
are retained in [`ring_text_evidence.json`](../engine/ring_text_evidence.json).
The local Oracle witnesses select unique legal non-token identities; Ornithopter
also has a same-name token record that is not a starting-deck card.

Verification: **390 focused native tests**, **938 broader gameplay tests** including
those 390, **256 serialization tests**, **29 live integrations / 469 subtests**,
and **399 offline tests / 508 subtests**, with all Python quality tools. The frozen
catalog construction probe still passes **32,109 identities**, excluding the same
seven non-starting meld records. Construction does not establish full gameplay.

Fixtures use partial four-player registrations and controlled boards. Attacker
declaration is installed directly; native attack/block triggers, blocker choice,
damage, looting and deadlines run afterward without response priority. This does
not certify all Ring choices, controllers, ward costs, control/reselection patterns,
spell-on-stack word edits, cipher/buyback/flashback choices, overlapping word/text
layers, all Deadpool entry paths, lost-player or hidden-view behavior, arbitrary
subclass payloads, general continuations or AI policy. The retained rules reference
is versioned evidence, not a claim to have audited every rule or newer revisions.


## Reconfigure and mana-granted keywords

Copied games now retain the explicit cleanup shared by a Reconfigure equipment's
leave-play and unattach queues. The callback maps the effect card into the new
world, so detaching, bouncing the equipped creature, bouncing the equipment, or
moving it to another creature removes the correct type-changing effect. It never
executes an original-world closure. A new attachment creates its own effect and
cleanup as usual; the old one does not accumulate after moving the equipment.

Temporary keywords granted by mana now use a mapped card/timestamp callback.
Expiry removes only that changed-keyword layer and refreshes native views. The
mana source can already have been sacrificed, as with Generator Servant. The
normal paid-casting path still determines which mana was spent; these checks do
not manually grant haste or inject a payment result.

Twenty-four native cases run both stack-copy modes: sixteen exercise Lion Sash
and Lizard Blades through the four attachment paths, six exercise Carnelian Orb
of Dragonkind, Generator Servant and Arena of Glory, and two retain Hall of the
Bandit Lord's permanent haste. The identical final fixture fails twenty-two cases
against the prior runtime while both permanent controls pass. Twelve packaged
witnesses also retain paid costs, attachment IDs, effect-card counts, actual mana
source IDs, sacrifice/exert/life costs and independent source/copy outcomes.
An earlier fixture incorrectly tried Unsummon on noncreature attached equipment;
the retained valid baseline uses Disperse and checks native target admission.

Verification: **414 focused native tests**, **962 broader gameplay tests** including
those 414, **256 serialization tests**, **30 live integrations / 481 subtests**,
and **399 offline tests / 508 subtests**, with the Python quality gate. Evidence,
source hashes and actual observations are retained in
[`attachment_mana_evidence.json`](../engine/attachment_mana_evidence.json).

Fixtures use controlled partial four-player boards and bounded phase hooks
without response priority. Land mana sources are installed on the battlefield;
spell casts and activation costs are paid natively. Generator Servant waits a
full turn cycle before tapping. The cases check haste presence and expiry, not
all attack admission, multiple-spell allocation, copied mana abilities, overlapping
grant layers or every Reconfigure card's other abilities. Ordinary equipment,
living weapon, modified attachment restrictions, hidden/merged/perpetual state,
other callbacks and arbitrary continuations still need separate validation.
Catalog construction remains availability evidence rather than full rules proof.

At the attachment/mana milestone, a separate retained native research probe
reproduced a host-bound exile return defect with Banishing Light in both snapshot modes: the original game's
paid Disenchant returns the exiled creature, while copied Disenchant leaves it in
exile. The return callback and linked-card list required their own fix and tests.
That historical failure is addressed by the following milestone.


## Host-bound exile returns

Copied games now retain the explicit return command created by duration-based
zone changes. The command maps its host, original/destination zone table, ability
graph and historical return-ability host through the snapshot context. Copying
only restores state; it does not exile or return cards, replay a trigger, or share
an original-world closure. The return itself uses the existing native zone-change
and trigger paths in the copied game.

The host's linked-card list is restored as a separate collection of mapped cards.
Cross-game historical copies also retain mapped links; ordinary in-game card
copy characteristics are unchanged. The captured table retains game timestamps,
and existing token filtering and early-zone-exit cleanup still apply. Selected
cards return to their prior zones and, on battlefield return, their owners'
control. A host that already left before its triggered exile resolves continues
to leave the target untouched.

The identical final native fixture has twenty-two cases. Against the previous
runtime, thirteen return checks and four live/historical link checks fail. Four
more early-move/token cases already have the right visible outcome but report
unsupported callbacks; the host-already-gone control passes. All twenty-two pass
with the mapped return implementation. The fixture includes both stack-copy
modes for eight card/cleanup scenarios and two reference contracts, plus a paid
pending Disenchant and a pending exile trigger whose host has already left.

Ten packaged witnesses retain actual paid costs, target IDs, zone timestamps,
owners/controllers, link membership, world identity and original/copy outcomes:
Banishing Light destroyed or bounced, Banisher Priest, three opponents' Grasp of
Fate targets, Kitesail Freebooter's hand return, Lagrella's selected targets, Pull
from Eternity before host removal, tokens, and the two pending-stack cases.
The natural packaged AI selects opposing Lagrella targets. The native positive
counter case fixes legal targets at the controller boundary, restores the original
controller before copying, and checks that only the returned friendly creature
gets two counters. These are distinct coverage claims.

Verification: **436 focused native tests**, **984 broader gameplay tests** including
those 436, **256 serialization tests**, **31 live integrations / 491 subtests**,
and **399 offline tests / 508 subtests**, with the Python quality gate. The catalog
probe still constructs **32,109 identities**, excluding seven nonstarting meld
results. Exact source/worker hashes, the failing baseline, paid observations and
limits are retained in [`host_return_evidence.json`](../engine/host_return_evidence.json).

The fixtures use controlled partial four-player games and bounded settling without
response priority, except for the two explicitly staged paid stack points. They
do not certify every duration, untap/control deadline, simultaneous host departure,
blink/re-exile incarnation pattern, ownership replacement, full hidden-information
policy, or unrelated card ability. Arbitrary suspended mid-resolution state,
including partially accumulated global return-zone tables, needs separate work.
Other callbacks, merged/perpetual state and AI decisions remain unvalidated.
Script construction and versioned rule references do not prove complete gameplay
or a complete current-rules audit.

At the host-return milestone, a separate native research probe reproduced Fog's
effect surviving end of turn in both snapshot modes. A paid Grizzly Bears waits a turn cycle; original
following-turn combat deals two damage after Fog expires, while copied combat
still deals none. Its attacker declaration is controlled, followed by native
combat processing. That failure was outside the passing suites selected for the host-return
milestone and is addressed below.


## Temporary effect cleanup and regeneration

A shared explicit snapshot command now maps a temporary effect card into the new
world before removing it. Seven native helper paths use it: Fog, regeneration,
death-zone replacement, Unearth, Amass subtype grants, replayed-spell replacement,
and the scheme-only extra-turn helper. The last is a native utility contract;
Schemes are not admitted as normal Commander starting cards. The existing native
deadlines, zone events and removal policy are retained rather than replayed while
copying the game.

Regeneration state needed a separate repair. Live snapshots lost the regeneration
shield quota and the number of times a creature regenerated that turn. Consuming
a copied shield could produce a negative quota, and Spiny Starfish could fail to
create its end-step token. Original-game LKI copies also lost both values. Raw
state restoration now preserves them in live and LKI copies, updates the native
view and leaves ordinary copiable characteristics unchanged. These regeneration
shields are distinct from shield counters, whose mechanics are not changed here.

The identical final native fixture has **28 cases**. All fail against the previous
reviewed source and all pass with the candidate. The failures are not equivalent:
eight are gameplay outcomes, eight are quota/history contracts, six are dangling
effect lifecycle checks, four are diagnostic-only controls whose visible outcomes
already work, and two are scheme-helper contracts. An intermediate candidate that
only mapped cleanup passed eighteen; the remaining ten demonstrated the need for
regeneration-state restoration. Earlier invalid counterspell targeting and use of
the common-card lookup for a Scheme are excluded from the retained final baseline.

**Twelve packaged witnesses** pay real costs and retain independent source/copy
state: Fog expires before following-turn combat; Regenerate expires or is consumed;
Wrap in Vigor is partially consumed; Incendiary Flow's death replacement is checked
before and after expiry; Unearth exits via bounce or its native end-step trigger;
an existing Zombie Army gains Orc through Amass and then leaves; Spiny Starfish
creates its token; and an Opt replayed by Torrential Gearhulk resolves or is
countered before end-of-turn cleanup. The replay and unexpired death-replacement
controls already have correct visible outcomes on the old runtime; their omitted
callback diagnostics are tracked separately from actual rules failures.

Verification: **464 focused native tests**, **1,012 broader gameplay tests** including
those 464, **256 serialization tests**, **32 live integrations / 503 subtests**,
and **399 offline tests / 508 subtests**, with the Python quality gate. The catalog
probe still constructs **32,109 identities**, excluding seven nonstarting meld
results. Exact source/worker hashes, valid failing baselines, actual outcomes and
limits are retained in [`effect_cleanup_evidence.json`](../engine/effect_cleanup_evidence.json).

The fixtures use controlled partial four-player boards. Fog's paid attacker waits
a turn cycle; its declaration is installed directly before native triggers,
blocking and combat damage run. Replayed spells use explicit paid stack points.
The remaining scenarios use bounded native settling without response priority.
They do not certify every replacement ordering, combat-regeneration case, shield
counter interaction, token subtype combination, alternate controller, rollback,
replay permission or scheme rule. Other callbacks, hidden/merged/perpetual states,
arbitrary suspended continuations and general AI policy still require work.
Script presence, construction and versioned references do not prove complete
card/rules support or a complete current-rules audit.

At the effect-cleanup milestone, a separate native research probe reproduced lost
Detain restrictions in both snapshot modes. Azure Mage is cast and waits a turn cycle, then paid Inaction
Injunction blocks its native activation admission in the original game. The copy
loses the detained flag, incorrectly admits that activation and reports an omitted
player-bound cleanup callback. That defect was outside this milestone's passing
suites. Its future deadline checks were not reached in that baseline; the repair
is described below.


## Detain and control age

Detain now retains an independent collection of detaining participants in live
snapshots and last-known information. Explicit expiration commands map both the
card and the detaining player; they run at the original native deadline. Clearing
one participant's Detain does not clear another's. Blink creates a new incarnation
without Detain while the old expiration still points to its historical object.

A separate defect appeared when a snapshot copied the effective sickness value
of a creature with haste. That value masked its raw first-turn-controlled flag.
After Naturalize removed Fervor, a copied newly controlled Prodigal Pyromancer
could incorrectly activate its tap ability. The copy now preserves raw control
age. LKI also preserves this state for both new and established permanents;
ordinary copiable characteristics and physical zone-change copies are unchanged.

The **same final 30-case native fixture** produces **26 failures / 4 passes** on
an audited prior source and **30 passes** with the repair. Fourteen failures lose
live Detain restrictions, three lose Detain history, four lose raw control age in
a hasted snapshot, two admit an illegal tap activation after haste loss, one
loses established control age in LKI, and two report omitted blink-expiration
commands despite already correct visible outcomes. The four positive controls
cover expired Detain and fresh historical control age. An intermediate Detain-only
candidate passes 23 cases; its remaining seven failures demonstrate the separate
control-age repair. The earlier 24-case fixture was corrected to respect tapped
and sick creatures when checking post-expiry combat eligibility; those fixture
expectation failures are excluded from the final baseline.

**Twelve packaged witnesses** pay actual costs for Azure Mage, Icy Manipulator,
Llanowar Elves, Inaction Injunction, Lyev Decree, Lyev Skyknight, Cloudshift, Act of
Treason, Time Warp, Fervor, Prodigal Pyromancer and Naturalize. They retain native
activation and attack/block predicates, view/LKI values, raw/effective control age,
controller/world identity and deadline checkpoints. Cases distinguish repeated
Detain by one participant, two participants' separate deadlines, a new blink
incarnation, temporary control, a queued extra turn, already expired restrictions
and loss of haste. Source/copy equality and independence are checked separately.

Verification: **494 focused native tests**, **1,042 broader gameplay tests** including
those 494 in **82 classes**, **256 serialization tests**, **33 live integrations /
515 subtests**, and **399 offline tests / 508 subtests**, with the Python quality
gate. The catalog probe still constructs **32,109 identities**, excluding seven
nonstarting meld results. Exact source/worker hashes, frozen final fixtures,
valid failing baselines, actual observations and limits are retained in
[`detain_control_age_evidence.json`](../engine/detain_control_age_evidence.json).

These are controlled partial four-player boards with bounded native settling,
not complete legal-deck games or an AI strategy certification. Combat checks are
native eligibility predicates at fixed main-phase checkpoints, not attacker or
blocker declaration policy. The fixtures do not certify every target/controller
combination, player departure, response-priority ordering, untap-symbol cost,
control-change history or historical-object consumer. Native cases cover both
stack-copy modes; packaged witnesses use stack copying. Other callbacks, hidden,
merged or perpetual states and arbitrary suspended continuations remain open.
Unmapped callback diagnostics are asserted in these fixtures and are not yet
aggregated into regular CLI reports. Catalog construction and retained versioned
rules references do not establish full card/rules support or a current-rules audit.


At the Detain milestone, a separate native research probe reproduced lost turn control from paid Worst
Fears, both before the controlled player's next turn and while that turn is
active. All four cases fail on production source audited against this candidate:
the original target is controlled, its copy is not, and an unmapped start/expiry
callback is reported. That defect was outside the passing reviewed suites; its later expiry and
decision-policy checks were not reached. The retained Detain evidence records
that research baseline. The snapshot repair is described below.


## Player-control snapshots

Pending player-control effects now map their controller and target into the
copied world, along with the flag selecting a turn or combat deadline. Release
commands retain the target and exact timestamp. Already active effects preserve
all timestamped control layers and rebuild game-bound decision controllers using
the new game's registered factories, without firing new control events. The
stored agent is preserved even when its master has since gained or lost control
by another player. Custom factories outside registered participants are rejected
explicitly. This mechanism does not copy arbitrary custom agent or strategy state.

The **identical final 30-case native fixture** produces **28 failures / 2 passes**
on the audited previous source and **30 passes** after the repair. Twenty-two
failures cover paid control lifecycles, including legal self-targeting; four are
independent layer/factory-history contracts using native controller APIs; two are
omitted-command diagnostics after the controller concedes even though visible
outcomes are already correct. The two already-expired snapshots are positive
controls. All 1,032 gameplay/AI production Java paths and text were compared for
the baseline; raw file hashes and exact fixture bytes were retained.

**Thirteen packaged witnesses** pay real costs and check independent source/copy
worlds: pending and active Worst Fears, Mindslaver, Secret of Bloodbending combat
control, two different players' overlapping Worst Fears effects, already expired
control, self-control, concession before acquisition, and pending/active Emrakul
cast-trigger control. Mindslaver is actually cast, tapped and sacrificed through
its native activation cost. Secret uses its normal four-blue cost without paying
the optional waterbend cost. Emrakul is cast for thirteen mana with no graveyard
cost reduction, gets a natural mandatory trigger target on the fixed board, and
preserves the promised following extra turn without retaining control of it.
Receipts retain costs, active/lost participants, master/view/agent identities,
game-bound controller checks, phases, turns, extra-turn flags and source-card
zones or absence after an owner's concession.

Verification: **524 focused native tests**, **1,072 broader gameplay tests** including
those 524 in **83 classes**, **256 serialization tests**, **34 live integrations /
528 subtests**, and **399 offline tests / 508 subtests**, with the Python quality
gate. The catalog probe constructs **32,109 identities**, excluding seven
nonstarting meld results. Exact source/worker hashes, failing baselines, frozen
final fixtures, actual observations and limits are retained in
[`player_control_evidence.json`](../engine/player_control_evidence.json).

The fixtures use partial four-player boards and fixed legal action choices,
with native payment, resolution and phase hooks but no response priority. They
cover both native stack-copy modes at resolved checkpoints, not arbitrary paid
pending spells or mid-resolution continuations. Other control cards, untap/skip
interactions, targets leaving, active-master departure, human-controller conversion,
custom agents, search-only control, optional waterbend payment, Emrakul cost
reductions and strategic target/action choices remain unverified. Catalog
construction and versioned rules references do not establish complete rules or
AI coverage. Unmapped diagnostics still need regular CLI report aggregation.

A separate native research fixture checks three legal, affordable primary
ControlPlayer actions: Worst Fears, Mindslaver's activation and Secret of
Bloodbending. In heuristic mode each is assigned to `CannotPlayAi` and returns `CantPlayAi` with
rating zero. All three research checks fail the criterion that an implemented
strategy should evaluate the action instead of classifying it as unsupported;
they do not require choosing every legal action. This is an open heuristic AI support gap
outside the passing snapshot suites. Full-simulation candidate enumeration admits
these legal actions, as verified separately below. Emrakul's mandatory trigger fallback in the
paid witnesses is separate from automated selection of those primary actions.
Full hostile decision policy and arbitrary custom AI state still need work.


## Decision beneficiaries and pending responses

The planner distinguishes the acting player from the player whose decisions it
implements. A controlled player's legal spell/ability choices are evaluated for
its current decision controller, following native controller links. The actor
still owns and pays for its actions; teams, opponents and object control are not
rewritten. Self-control uses the ordinary objective. Cross-game references and
cycles without a unique beneficiary are rejected explicitly; broader cyclic and
custom-agent behavior is not certified.

A rollout captures the beneficiary before copying or resolving anything. All
three simulator constructors, initial scores and resulting scores use that same
participant, even after a lethal action removes its control effect. Cached plans
are discarded when their beneficiary changes. Controlled heuristic selection now
uses legal-action simulation; ordinary uncontrolled heuristic selection is retained.

Pending spells and abilities are copied before response planning. The no-response
baseline resolves them using a separate copier, keeping the action-world mapping
intact. Incremental free-cast checks during an already resolving parent keep
their board-only baseline and do not replay that parent. The broader suite caught
this regression in an intermediate candidate; all fourteen existing one-play
safety checks pass after the guard. Response enumeration includes legal stack
spell/ability targets as well
as card/player entities. It sees Barbarian Ring's activated damage ability after
the source has been sacrificed, when no card occupies the stack zone. These
changes make the tested Counterspell and Stifle responses selectable.

The **identical final 26-case native fixture** has **16 failures / 10 passes** on
audited previous production source and **26 passes** with this patch. Failures
comprise four harmful controlled/chain choices, seven initial/rollout score
contracts, three spell-response decisions and two activated-response decisions.
Positive controls cover ordinary/self choices, full-mode candidate admission,
ordinary heuristic damage-counter behavior and uncounterable threats. All 1,032
prior AI/gameplay production paths/text were compared; raw hashes and exact
fixture bytes are retained. The candidate adds one production Java class.

**Sixteen packaged cases** observe actual AI-selected actions and native payment
and resolution. Paid Worst Fears sets up control; Shock choices protect the
beneficiary. Ordinary and controlled responses use Counterspell against pending
Shock. Stifle counters Barbarian Ring's native threshold activation with its real
red, tap and sacrifice costs; the Ring cannot fund its own tap cost. Volcanic
Fallout remains uncounterable and causes the expected unavoidable loss. Chain and
self contexts additionally exercise native controller APIs; they are not claimed
as complete card-generated control chains. Planning must leave the original
game unchanged; receipts retain identities, targets, mana owners, zones and lives.

Both requested heuristic and full modes are tested. The ordinary heuristic
response positive uses the unchanged shipped **Cautious** profile's damage-counter
preference; its normal cheap-counter policy is probabilistic, so arbitrary
political or always-counter expectations are excluded. Default and Cautious
profile hashes and selected native profile names are retained. Draft fixtures
that expected precombat full-mode casting, read cast history from a graveyard
card, or required arbitrary/default random counter behavior were corrected and
excluded from the final identical baseline. No production fix is attributed to
those fixture errors. An intermediate 548-test build was never selected; two
activated-response regressions were added before the final 550-test build.

Verification for this milestone is recorded in
[`decision_perspective_evidence.json`](../engine/decision_perspective_evidence.json):
**550 focused native tests**, **1,098 broader gameplay tests** including those 550
in **84 classes**, **256 serialization tests**, **35 live integrations / 544
subtests**, and **399 offline tests / 508 subtests**, with the Python quality gate.
The catalog probe covers **32,109 constructed identities** and seven nonstarting
meld results. Construction remains distinct from full gameplay verification.

This is bounded planning on controlled partial four-player boards, not complete
Commander game/strategy certification. Primary ControlPlayer heuristic support
still needs work; full-mode admission is verified separately from valuable
selection. Combat/blocks, mandatory choices, search-only control, arbitrary
responses, custom/cyclic controllers, stochastic future effects and suspended
continuations remain unverified. Callback diagnostics still need normal CLI
aggregation. Versioned rules references are not a complete current-rules audit.


At the decision-perspective milestone, a separate source-bound combat research
fixture reproduced a policy gap:
a Shivan Dragon controlled through paid Worst Fears is declared against its
2-life decision controller despite three legal defenders and an empty opposing
creature board. Both requested heuristic/full cases failed the protection criterion;
both ordinary-player attack controls passed. All 1,033 AI/gameplay production paths
matched that build. This was a fixed declaration checkpoint after native control
acquisition, not full combat-damage resolution or forced-attack certification.
The earlier automatic-declaration checkpoint error is excluded. Those two
research failures were separate from the passing native and live suites above;
their source/fixture/observation hashes are retained in the decision evidence.


## Controlled attack planning

The reviewed controlled-attack build evaluates legal attack declarations for the controlling decision
beneficiary without changing the actor's ownership, teams or resources. It reuses
native attack constraints, preserves mandatory assignments, and considers the
heuristic declaration, a native legal seed, passing, whole groups and individual
changes across native player/card defenders. Candidates that violate restrictions
or satisfy fewer requirements than the native legal result are rejected.
This includes keeping a mandatory attack on the controller when it is the only
legal defender; an attack tax remains an optional payment under native rules.

Each evaluated candidate uses an independent game copy, mapped attackers and
defenders, native declaration/payment, attack triggers, blocking and combat damage
through the second main phase. The beneficiary is captured before copying and
remains fixed through loss or combat-control expiration. Incomplete nonterminal
rollouts are excluded. Equal seeded samples restore the original random provider.
The existing shared decision deadline applies; at most 128 legal declarations
are evaluated, retaining the best fully evaluated one if time runs out. A
reentrancy guard prevents nested predictions from recursively starting this search.
These limits describe bounded strategy search, not exhaustive optimization.

The identical final **26-case native fixture** has **19 failures / 7 passes** on
previous production source audited across all **1,033 AI/gameplay Java paths and
text**, and **26 passes** after the patch. Eighteen failures declare unforced
attacks against the decision beneficiary. One full-mode tax witness fails to
select its reachable defender; that is a selection expectation, not an illegal
attack. The seven positives comprise ordinary/self attacks, mandatory harmful
attacks with no alternative, and the heuristic actor-paid tax witness. These
baselines preserve exact fixture bytes and raw source hashes; only text comparison
normalizes line endings. The candidate adds one production Java class.

Cases use paid Worst Fears or the normal four-blue Secret of Bloodbending cost,
without optional waterbend payment. Native outcomes cover Shivan Dragon,
Juggernaut's required attacks, Blazing Archon restrictions, Propaganda payments,
Silent Arbiter's limit, paired Mogg Flunkies, Baneslayer Angel blocking with first
strike/protection/lifelink, chain/self API contexts, and recovery after an expired
deadline. Planning must leave original life, lands and attackers unchanged.
Combat-only control expires before the scored main phase. Chain/self contexts are
native API contracts, separate from card-generated control-chain certification.

The packaged witness worker writes two fixed hash-bound receipt parts within the
existing 128 KiB document bound. An initial oversized receipt, an incorrect draft
group-card name, unused imports and an automatic-attack checkpoint mistake were
fixture errors and are excluded from gameplay failure claims. Packaged/broader
validation passed before that runtime promotion. At the controlled-attack milestone, the runtime passed **576
focused native tests**, **1,124 broader gameplay tests** including those 576 in
**85 selected classes**, **256 serialization tests**, **36 live integrations /
570 subtests**, and **399 offline tests / 508 subtests**, with all Python quality
checks. The catalog probe constructs **32,109 identities** and retains seven
nonstarting meld results. Exact source/fixture/worker hashes, bounded receipt parts,
baselines, outcomes and limits are retained in
[`controlled_attack_evidence.json`](../engine/controlled_attack_evidence.json).

Controlled blocking, arbitrary mandatory choices, tax-choice variants, hidden or
stochastic future effects, different declaring players, cyclic/custom controllers,
all attack combinations and complete Commander games remain unverified. Script
construction and these selected scenarios do not establish complete card support.


## Primary-control future planning

A separate candidate adds native future-window assessment for Worst Fears,
Mindslaver, Secret of Bloodbending, Sorin Markov and The Dominion Bracelet.
The identical 23-case source fixture fails all 23 cases on the previous reviewed
source and passes all 23 on the candidate. Fourteen additional native contracts
cover discounted terminal outcomes, scoped random-provider isolation and recovery
of completed plans after interrupted search, plus resolving-parent boundaries.
All 37 candidate-native cases pass. The final reviewed archive-based build passes
613 focused native regressions, 1,161 broader gameplay tests in 90 selected classes,
256 serialization tests, 37 live tests / 593 subtests and the Python quality gate
(399 offline tests / 508 subtests). It constructs all 32,109 matched catalog
identities; seven nonstarting meld results remain separate. Provenance is retained
in [`primary_control_evidence.json`](../engine/primary_control_evidence.json).

Each branch pays real native costs, retains the original decision beneficiary,
and compares the selected future window with an independent no-action branch.
The bound is two legal one-ply actions per main phase through the selected second
main phase, under the existing shared deadline. Both Secret cost paths retain
cast targets and native control timing; Bracelet payment exiles the granting
equipment rather than the creature host. Sorin's fixed eight-loyalty fixture
represents previously accumulated counters, not a fabricated activation history.

Full search may retain legal redundant equip prefixes before Bracelet activation;
these are actually paid and executed by the witness. That remains a strategy
optimization gap. Incomplete future windows are excluded. Current coverage does
not establish arbitrary turn/combat schedules, response priority, optimal choices,
composed-control strategy, or all-card rules correctness. Synchronous scoped
previews restore their random provider after exceptions; this is not a global
concurrency certification for every remaining native AI path.

A future forecast cannot advance an unfinished resolving parent. The paid-child
regression reproduced that incorrect forecast, and a separate native Ruric Thar
case exposed a free-cast safety bypass. The guarded fixture changes from two
failures and one positive control to three passes. Existing incremental free-cast
safety remains active; arbitrary parent continuations are not forecast.

## Paid spell control and retargeting

The reviewed extension covers primary ControlSpell actions: Aethersnatch, Commandeer and
Chef's Kiss. It compares native paid stack outcomes with a separate no-response
baseline. Each branch uses the same isolated preview seed; previews preserve
original state and the live random provider. Native casting still controls
legality, costs, spell ownership, targeting and resolution.

A generic AI retarget callback delegates to native effect heuristics in a copied
world, maps selected objects back, validates changed targets and retains original
per-slot damage allocations. It preserves the announced number of targets and
X value. The callback mutates the live spell's choices only after validation;
the native ChangeTargets effect remains responsible for target events.
A defined, nontargeting copy subeffect is assessed with its parent instead of
being rejected as a separate copy spell. Chain of Smog/Acid policies remain first.

The 28-case fixture includes nine primary AI choices, three alternate-cost AI
choices, paid manual resolutions, ownership, the creature-target prohibition,
Counterspell retargeting, controlled-player perspective, Arc Lightning divisions,
Fireball X, insufficient alternate payment, no-benefit choices and an additional
restriction/random-provider contract. The identical previous-source fixture
fails 18 cases and passes ten controls. The fresh build passes all 28, and all twelve packaged choice/payment/outcome
witnesses pass. That earlier runtime passed 641 focused native, 1,189 broader
gameplay, 256 serialization and 38 live tests / 605 subtests, plus the Python
quality gate. Exact provenance is retained in
[`spell_control_evidence.json`](../engine/spell_control_evidence.json).

That fixture does not cover Invert Polarity, Perplexing Chimera and Sudden
Substitution; their later composed-control witnesses are described below.
The primary heuristic declines resolving-parent contexts, trigger/subability
entry points and reentrant assessment. Retargeting keeps the original choices
if native heuristics cannot propose a complete valid replacement; it does not
exhaustively search every legal target combination. Seeded examples establish
selected outcomes, not stochastic fairness or full-card certification.

Rules reference: [Wizards Comprehensive Rules, retained 2026-09-25 edition](https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt),
115.7d–f and 601.2c–d. This is a versioned reference, not a current-rules audit.

## Composed control and cast provenance

The reviewed runtime evaluates complete native stack responses for Invert Polarity and
Sudden Substitution, with up to 128 target combinations and paired seeds 0/4096.
These two deterministic preview samples are not an exhaustive probability model.
Native payment, targeting, coin outcomes, exchange rules and resolution remain
responsible for gameplay. Structural target/remember nodes are validated with
their complete response rather than being mistaken for independent stat bonuses.

Perplexing Chimera asks its optional question inside an already resolving
trigger. The planner captures that exact wrapper checkpoint and previews the
accepted and declined native suffixes, preserving the normal completion event,
control cleanup, stack removal and phase bookkeeping. It does not replay the
checked prefix or advance arbitrary unfinished parents. Existing resolver guards
remain in place. Live-world and random-provider invariants have native contracts.

Cast history is recorded after payment as separate native LKI-backed abilities.
The live spell's controller and targets can change while the record retains its
original caster, costs and announced choices. Zacama is a concrete gameplay
witness: gaining control of its spell does not make the thief its caster and
must not grant that thief its conditional land untap. Ordinary casting, repeated
theft and current/recorded target separation are tested separately.

The sixteen-case identical-source baseline has seven failures and nine controls.
Three boundary controls use explicit affirmative choices for the original spell
and real Rapid Hybridization, March of Swirling Mist and Counterspell responses;
these are rules checks, not AI-admission witnesses. Four checkpoint contracts
and five cast-provenance cases also pass. The latter's pre-provenance-fix prototype
has four failures / one control, recorded separately from the previous-runtime
sixteen-case baseline. The fresh archive-based build passes 666 focused native,
1,214 broader gameplay and 256 serialization checks. Fourteen packaged witnesses
change from twelve failures / two controls to fourteen passes. All 39 live tests /
619 subtests and the Python quality gate (399 offline tests / 508 subtests) pass.
The Sudden Substitution witness records Shock targeting the exchanged Ornithopter,
two creature damage and its resulting graveyard zone. Ordinary/stolen Zacama
controls flush triggers created by the final resolution before checking untaps.

All 34,132 card-archive entries were compared; only the AI exclusion flags for
Perplexing Chimera and Sudden Substitution changed. Exact unchanged alias scripts
and companion predicates were reviewed before carrying their receipts forward.
The catalog probe matches 32,109 constructions; seven meld results remain
nonstarting objects. See [bound evidence](../engine/composed_control_evidence.json).
These are selected gameplay witnesses, not full-card certification.

Reference: [Wizards' Commander Masters release notes](https://media.wizards.com/2023/downloads/CMM_Release_Notes/EN_MTGCMM_ReleaseNotes_20230616.pdf)
and the retained Comprehensive Rules 112.2, 601 and 109.5; references are versioned
and do not constitute a complete current-rules audit.


## Mass counter removal and paid outcomes

Before this change, `RemoveCounterAll` was mapped to `CannotPlayAi`; it now uses
`CountersRemoveAllAi`. Fourteen exact-name Commander-legal scripts reference that
API; this inventory is not a count of unsupported cards. The Heartmender
mandatory-upkeep control already passes through native rules resolution. Six
initial AI-choice failures for Aether Snap, Dust of Moments and Oblivion Stone
expand to eight when Pestilent Haze is included.

The reviewed planner compares complete paid native outcomes, including token exile,
vanishing/suspend triggers, fate-counter cleanup and modal loyalty removal.
The target enumerator distinguishes an untargeted action from a required target
with no legal candidate. The shared planner retains two fixed seeds, at most
128 target combinations, the native shared deadline and resolving-parent/reentry
guards. Native costs and resolution remain responsible for gameplay.

The final 22-case previous-source baseline has eight failures / fourteen passing
controls. The fresh archive-based build passes all 22 plus five shared-planner
contracts, and all 693 focused native regressions. The fourteen packaged cases
change from eight failures / six controls to fourteen passes. Modal witnesses use
the production AI cast path, which selects and chains native modes before native
payment. Explicit manual modal choices remain limited to rules controls.

Final validation passes 1,241 broader gameplay checks in 96 selected classes,
256 serialization checks, 40 live tests / 633 subtests and the Python quality gate
(399 offline tests / 508 subtests). The 32,109 catalog constructions match; seven
meld results remain nonstarting objects. All 34,132 archive entries were compared;
only AI exclusion metadata for Aether Snap, Dust of Moments and Oblivion Stone
changed. Exact unchanged alias/companion predicates were reviewed before carrying
their receipts forward. See [bound evidence](../engine/mass_counter_evidence.json).
This is selected rules/choice coverage; full-card certification remains open.

Mass-counter references: the retained [Wizards Comprehensive Rules, 2026-09-25 edition](https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt),
122.2, 400.7, 702.62 and 702.63. These are versioned references, not a complete
current-rules audit. Exiled suspended-card states, counter amounts, token
identity and controller layers are explicit fixture preconditions; they do not
claim to exercise every prior suspension, token-creation or control-acquisition path.

## Creature exchanges and random piles

The reviewed runtime covers selected paid decisions for Cultural Exchange and
Whims of the Fates. A shared bounded
paid-outcome forecast evaluates actual costs and native resolution. Creature
selection ranks both equal-sized groups together from the decision beneficiary's
perspective, including a controlled player's decisions. The exchange effect
validates current eligible objects, unique identities, cardinality and destination
control permission before changing either side; a canceled choice ends safely.
Seeded pile rolls are assigned in the same player order as pile separation.

The fixed native rules cases cover zero/equal groups, shroud, phasing, illegal
player targets, empty piles and Assault Suit's sacrifice restriction. Separate
AI cases require actual source admission and payment. The pile AI retains its
legal all-in-one-pile policy; this is not an optimal partition strategy. Paired
forecasts sample two fixed seeds under the existing deadline, not every possible
roll or creature subset. Controlled-player fixtures establish a controller layer;
they do not claim to test a preceding paid player-control spell. The runtime passed
724 focused native tests, 1,272 broader gameplay checks (99 classes),
256 serialization checks (16 classes), 41 live tests / 648 subtests and the
Python quality gate (399 offline tests / 508 subtests). Catalog probing
constructed 32,109 matched cards; seven unavailable entries were verified as
nonstarting meld results. These counts are not full rules certification.

The identical 26-case native fixture changes from eleven failures / fifteen
passing controls to 26 passes, with five additional paired-selection contracts.
Fifteen packaged cases change from ten failures / five passing controls to
fifteen passes. The failing native baseline includes one canceled-choice
exception and one seeded replay contract. Shroud, phasing, protection and
Assault Suit were passing controls before this work. See
[exact evidence](../engine/exchange_pile_evidence.json) and the
[official Whims release notes](https://magic.wizards.com/en/news/feature/release-notes-2014-01-22)
for the recorded scope and card-specific reference.


## Draw-ending rules

Reviewed rules work corrects Divine Intervention's separate last-counter
trigger. It checks the removal event, intervention counter type, zero remaining
counters and the current controller as remover. Its draw effect can be countered
after removal and survives the source leaving the battlefield or gaining another
counter. Entering without counters does not cause a draw on a later upkeep.
Celestial Convergence retains its distinct upkeep-only check.

The identical 23-case source fixture changes from eleven failures / twelve
passing controls to 23 passes. Controls cover actual paid entry counters,
Solemnity, another player removing counters, countering upkeep removal, unique
and tied highest life totals, and different counter types. Added witnesses cover
control changes and independent copied-game resolution. Fixed control layers and
some counter totals are explicit setup preconditions, not preceding gameplay
histories. The runtime retains Divine Intervention's AI exclusions. These are
rules witnesses, not evidence of strategic casting or all-card correctness.

Packaged tests compile the same reviewed fixture and its exact native AITest
base against the packaged JAR with pinned TestNG 7.10.2. Class-origin checks
require production classes to come from that JAR. Compilation uses an
operator-selected JDK, disables annotation processors and writes only into a
generated run directory. Deck uploads cannot invoke this fixed test harness.
All 23 rules cases pass against both the draw candidate and the combined runtime. The fresh build passes
747 focused native, 1,295 broader gameplay (100 classes) and 256 serialization
checks, plus the Python quality gate (399 offline tests / 508 subtests).
All 2,114 production Java files are unchanged, and exactly one of 34,132 card
archive entries changes. Oracle text and AI exclusions are unchanged.

The initial packaged candidate's full gate had one Whims full-mode selection
failure, also reproduced on the preceding runtime. Its historical failure is
preserved in the [draw candidate evidence](../engine/draw_ending_evidence.json).
The combined runtime integrates the rules correction after the mana reservation
and hybrid preview fixes below. Divine Intervention's AI exclusions remain.

## Mana reservations and hybrid previews

The reviewed runtime resolves two defects found while validating the draw-rule runtime.
The named Default profile reserves mana for Main Phase 2 with a 100% chance. The
old feasibility check rolled that chance even after the deadline, advancing the
RNG before a full planner selected its forecast seed. Zero-chance profiles could
also retain stale reservation memory. Expired Main Phase 2 and cleanup holds now
clear before the roll; first-main and next-spell reservations remain effective.

Hybrid OnePlaySafetyChecker runs on a separate Game AI Eval thread. A caller's
thread-local RNG scope does not propagate there. Its simulated Whims pile rolls
therefore consumed live randomness and advanced the undo counter. Each hybrid
safety sample now owns a worker-local RNG scope. Margins are combined over fixed
seeds 0 and 4096, retaining the existing card-disadvantage tolerance and permissive
handling of a native minimum/unavailable score. This is bounded sampling, not an
exhaustive expected-value or optimal-strategy model.

The identical thirteen-case native fixture changes from seven failures / six
controls to thirteen passes, including actual Default-profile full and hybrid
choices, paid costs, live RNG accounting, reservation deadlines and beneficial
versus harmful stochastic positions. The combined fresh build passes 760 focused
native tests. Its unchanged card archive includes the Divine Intervention
correction. Packaged checks pass all 36 draw/reservation cases and all 15 exchange
cases. The activated runtime passed 1,308 broader gameplay checks (101 classes),
256 serialization checks (16 classes), 42 live tests / 684 subtests, and the
Python quality gate (403 offline tests / 508 subtests). The catalog probe built
32,109 matched cards; seven unavailable entries were verified as nonstarting
meld results. See [validation evidence](../engine/mana_reservation_evidence.json)
for the baseline, diagnostics, package receipts and activation hashes.
Native and packaged tests use explicit partial-state preconditions. They do not
certify every preceding setup action, all profiles or all card interactions.
