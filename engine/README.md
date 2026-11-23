# Reviewed native engine build

Forge is GPL-licensed. See [LICENSE.Forge](LICENSE.Forge). This directory retains
the source modifications and regression source used by the reviewed Commander
engine. The unmodified source is pinned by commit and archive SHA-256 in
[`sticker_recipe.json`](sticker_recipe.json). The Star Trek recipe applies a port
of the reviewed changes to Forge 2.0.16, followed by the Saga common-card-type
and the Dilemma cast, combat-entry, and public-reveal fixes. The earlier patches
and engine locks preserve the history of those
changes. See the [official Forge Star Trek release](https://github.com/Card-Forge/forge/releases/tag/forge-2.0.16).

The reviewed combat-role correction separates creature and attacked-permanent
removal in [Combat.java](patches/commander-dual-combat-role.patch). Paid native
Gideon/Luxior and March-removal controls preserve whichever role remains valid.
All 1,938 selected native, 712 focused source, 781/65/194/146 packaged,
3,412 broader gameplay, 256 serialization and 45 live/1,834 subtest checks pass.
See the [activation evidence](dual_combat_role_evidence.json); selected contracts
and catalog construction do not certify every card or AI strategy.

In the earlier recipe, the Wicker Picker patch was pinned
to an immutable upstream pull-request head. Local regression fixes are a second
patch, applied after the upstream patch. Two further patches preserve chosen
commander colors through native/AI state and refresh companion pregame types.
A fifth patch preserves supplementary zones in AI game copies. A sixth preserves
applied sticker state in AI and last-known-information copies, maps sheets to
the copied game and tracks used slots across control changes. A seventh patch
preserves historical LKI and zone-entry records, independent damage history and
shared mapped combat bands. An eighth patch preserves damage provenance and
receipt histories. A ninth preserves registered participant mappings and active/lost
lists without replaying elimination, restoring life before loss outcomes.
A tenth patch preserves registered and live starting-life baselines. An eleventh
restores paid spell/ability graphs, stack entries, cast history and mapped mana
without replaying cast events. A twelfth preserves active card forms, prototype clone frames, bestow timestamps,
exile/effect provenance and the scheduled face-down-state reset. It also refreshes
disguise keyword caches. A thirteenth preserves canonical trait graphs,
activation/resolution and mode histories, mapped crew/saddle members and exact
saddle counts. A fourteenth maps explicit scheduled-effect callbacks and preserves control,
player-keyword and numeric-choice state. A fifteenth preserves delayed/waiting
trigger queues, deferred registrations and mapped cost-payment receipts.
A sixteenth preserves extra-turn/phase stacks, attached obligations and scheduler
state. A seventeenth preserves conditional control checks, explicit effect
expiration, two-turn cleanup wrappers and the state-based cleanup queue.
An eighteenth maps animation expiration, Licid reversal payloads and changed-trait
collections. A nineteenth maps temporary-copy expiration and token-keyword
cleanup payloads.
A twentieth maps card and player protection expiration to their own copied worlds.
A twenty-first maps keyword restoration and temporary power-exchange expiration.
A twenty-second preserves timestamped blocking requirements and maps their
expiration plus finite prevention-shield cleanup.
A twenty-third preserves Ring progression/designations and word/type layers,
corrects stale bearer and word-keyword cleanup, and enables mandatory text-box
exchange targeting with mapped restoration.
A twenty-fourth maps Reconfigure effect removal and mana-granted keyword expiry.
A twenty-fifth maps captured zone-return state and live/historical linked exile.
A twenty-sixth maps temporary effect cleanup and restores regeneration quotas/history.
A twenty-seventh preserves independent Detain membership, maps player-bound expiry
and retains raw control age in live/LKI copies.
A twenty-eighth maps player-control start/end commands and retains timestamped
controller layers with new game-bound agent instances.
A twenty-ninth preserves the fixed decision beneficiary, pending-stack planning
and legal spell/ability target enumeration.
A thirtieth adds bounded controlled-attack outcome planning under native attack
constraints and the existing shared decision deadline.
Every patch is pinned. No remote branch is used at build time.

Download the two archives from the exact URLs in the recipe, install a trusted
JDK 17+ and Java runtime, then run from the repository root:

```powershell
.\.venv\Scripts\python.exe tools/build_sticker_engine.py `
  --source-archive PATH_TO_FORGE_SOURCE_ZIP `
  --maven-archive PATH_TO_MAVEN_ZIP `
  --jdk PATH_TO_INSTALLED_JDK `
  --runtime PATH_TO_TRUSTED_JAVA_EXECUTABLE
```

The tool checks source, Maven, patch and regression-source hashes before building.
It extracts into a fresh generated directory under `data/engine-builds/`, rejects
archive traversal/symlinks and bounds extracted member counts and sizes. It runs
996 native tests covering public reveal, card characteristics, combat entry,
Beam me up, Dilemma, Assimilate,
stickers, chosen color, companions, supplementary-copy, history,
damage, participant, starting-life, paid-stack, cast-form, usage-history,
temporal-state, delayed-trigger, turn/phase-schedule and conditional-cleanup, animation, temporary-copy, protection, keyword-restoration, power-exchange,
forced-blocking, prevention-shield, Ring, text-edit, Reconfigure and mana-keyword, host-return, temporary-effect, regeneration, Detain, control-age, player-control, decision/response, controlled-attack, primary-control, resolver-boundary paid spell-control/retargeting, and tap selection, payment, and refund tests.
It packages a standalone runtime only when all pass without skips. It disables only the optional Windows GUI launcher packaging;
the native engine is unchanged by that build setting. Maven needs network access
to resolve the pinned source project's dependencies; transitive plugin and
dependency artifacts are not independently locked by this Python recipe.

The build preserves the complete extracted source, logs, test reports, runtime,
license, exact `engine-lock.json` and `engine-config.json`. Maven runs in the
bounded build JVM with test forking disabled. Administrator-selected source and
plugins execute trusted code; this is not a web/upload feature or an OS sandbox.

Before selecting a newly built runtime, run:

```powershell
python commander_simulator.py engine-check --engine-config BUILD_DIRECTORY/engine-config.json
python commander_simulator.py engine-card-probe --engine-config BUILD_DIRECTORY/engine-config.json
python commander_simulator.py engine-interactions --engine-config BUILD_DIRECTORY/engine-config.json
```

For broader native validation, apply
[the Commandeer test adaptation](patches/star-trek-commandeer-validation.patch)
to a copy of the extracted source. It checks that Commandeer remains eligible
for AI decks after the reviewed spell-control changes. The patch changes two upstream test methods to enforce this policy.

Also run the opt-in engine/CLI integration tests with that candidate selected in
the local `config/engine_config.json`. The build output config uses paths relative
to its own directory; moving that config requires updating those paths. JARs can
differ across builds because of embedded metadata. Never substitute a checksum
from a different build. The deterministic card ZIP has no loose-script overrides;
engine verification rejects extra entries in `res/cardsfolder/`.

See [card support and evidence](../docs/CARD_COVERAGE.md) for the distinction
between constructor coverage, controlled rules checks and full gameplay support.


The recipe pins forty regression source files and exactly twelve card-script
overrides, listed explicitly in the recipe. The initial recipe
is saved with the build; inputs are rechecked after compilation and packaging.
Locks pin both the Java executable and the JVM library, in addition to the JAR
and card ZIP, plus the bounded complete edition metadata inventory. Edition
filenames and bytes use a portable ordered fingerprint; the default lock pins
686 files verified against immutable source. The broader 1,241 gameplay and 256
serialization checks are recorded
in `native_test_evidence.json`; the 693 focused tests are included in the 1,241.


The supplementary-zone regression checks real independent AI copies rather than
infer support from loaded card names. Explicit Attraction preflight and all 87
printing/export/AI-copy integration cases also pass. Runtime promotion preserves
historical locks. The current build's edition pin was independently verified
against the immutable source archive, retaining the original generated lock and
review receipt locally; new builds include this pin directly in their lock.

Applied-sticker regressions directly place stickers to isolate state-copy behavior
from legal placement, ticket payment and target selection. Normal Clone effects
must not acquire physical stickers; AI game snapshots and LKI records preserve
different information. The focused tests distinguish those cases and verify a
real paid spell through stack copy and resolution. Snapshot fidelity does not
certify every ability's effect, historical zone-change LKI or AI decision.

The packaged runtime also passes state-fidelity checks for all 480 stickers on
48 Commander-legal sheets in three public zones. Fixed workers preserve bounded
receipts, physical-record hashes and exact Java object identity. The aggregate
[`sticker_state_evidence.json`](sticker_state_evidence.json) records these checks
and the corrected fixture rerun without treating state fidelity as ability proof.

The history patch additionally passes fourteen native cases and packaged-runtime
witnesses for four historical zone transitions and two actual paid Tragic Slip
outcomes. It copies recorded old characteristics and turn-zone entry records,
retains distinct incarnations, isolates mutable damage history, preserves mapped
combat-band identity and resets caches before reusing a copier. Copy failures
propagate rather than dropping history silently. See
[`history_state_evidence.json`](history_state_evidence.json) for the bound evidence
and explicit limits; other historical registries, arbitrary linked abilities,
damage triggers and post-elimination states still require further validation.

The damage patch preserves immutable packet identity, historical source/target
records, live/global source-history sharing and independent current/previous-turn
receipts. Nineteen native regressions include paid freerunning casts with seven
eligibility controls and paid bloodthirst regression controls. Filtered queries
reject missing provenance rather than counting it without its predicates.

The packaged damage runtime passes all 14 live integrations and the complete
catalog construction check. Its
[`damage_state_evidence.json`](damage_state_evidence.json) records exact runtime,
source, before/after, Oracle and receipt hashes. The before probe demonstrates a
real lost freerunning window in the previous runtime; the new paid witnesses
verify normal admission, two-mana payment and drawing three cards. They cast
after the copy and do not certify arbitrary paid spell state already on the stack.

The participant patch preserves eliminated players as canonical historical
references while excluding them from active players and turn order. Fourteen
native cases include three loss reasons at every non-active Commander seat,
target rejection, damage/memory references, independent subsequent losses,
removed owned cards and a resolved Blood Tyrant trigger without replay. The
packaged witness records both the original resurrection/history failures and
the corrected state. See
[participant_state_evidence.json](participant_state_evidence.json) for exact
hashes and limits; active-turn departures and pending loss triggers are outside
these fixtures.

The starting-life patch preserves registration and live player baselines
separately, before restoring current life. Sixteen native regressions include
Righteous Valkyrie boundary cases and real paid Angel's Mercy/Chaplain's Blessing
casts, plus configured baselines, eliminated players and a live override.
[starting_life_evidence.json](starting_life_evidence.json) binds the before-fix
incorrect bonus, exact build, source hashes and packaged-runtime witnesses.
It does not certify every starting-life-dependent card or every registration
field.

Paid-stack evidence is retained in [stack_state_evidence.json](stack_state_evidence.json).
The 30 StackGameCopyTest cases distinguish paid ability snapshots from ordinary
in-game spell-copy effects and verify source independence. The runtime is checked
through five fixed Python/native witnesses as well as the broader native suites.
See the coverage document for unvalidated stack/AI behavior.

Cast-form validation is retained in [cast_form_evidence.json](cast_form_evidence.json).
FaceGameCopyTest adds sixteen paid-action scenarios. Six packaged-runtime cases
record all live-zone instances of each resolved card, so a leftover stack object
cannot be hidden by selecting a convenient match. Oracle witnesses and the
versioned Wizards rules reference are recorded separately from engine behavior.
The cast-form patch initially reconstructed the marked face-down reset.
The scheduled-effect patch below now maps it through the explicit command
protocol. Arbitrary callbacks and complete alternate-cost coverage remain open.


Usage-history validation is retained in [usage_state_evidence.json](usage_state_evidence.json).
UsageGameCopyTest adds sixteen cases; the packaged runtime has six paid
witnesses with before-fix receipts. Printed, keyword and generated grant traits
retain canonical keys while mutable history/cost collections remain independent.
The synthetic null-override case checks a native replacement contract only.
The scheduled-effect cases below extend turn-transition and grant checks; see the coverage document for remaining gaps.


Temporal-state validation is retained in [duration_state_evidence.json](duration_state_evidence.json).
TurnUsageGameCopyTest adds eighteen cases and the packaged runtime has eight
paid witnesses. Explicit callback implementations cover single/mass Pump,
player-keyword expiration, ControlGain return/keyword removal and the face-down
reset. Unconverted commands in scanned queues remain omitted and reported by
the native copier API. This diagnostic is not yet aggregated into CLI simulation
reports. See the coverage document for the exact queue and phase-advancement limits.


Delayed-trigger validation is retained in
[delayed_state_evidence.json](delayed_state_evidence.json). DelayedGameCopyTest
adds 21 cases, including synthetic queue/receipt contracts; nine packaged witnesses
check paid actions and future outcomes. Trigger registration callbacks use the
explicit command protocol. Cost objects are historical receipts, not resumed
payment decisions. See the coverage document for unvalidated conditions,
APNAP/replacement behavior, callback kinds and unvalidated turn/phase variants.


Turn/phase-schedule validation is retained in
[turn_schedule_evidence.json](turn_schedule_evidence.json). Ten native cases and
nine paid packaged witnesses check extra-turn recipients, LIFO order, normal-turn
resumption, Savor's skipped untap, Final Fortune's loss, extra combats and Moraug's
phase-entry untap. Scheduler entries and attached abilities/triggers use the shared
identity map without replaying registration. Current extra-turn flags, counts and
selected phase counters survive snapshots. Variant rules and priority/cleanup
continuations still need separate validation.


Conditional-control/cleanup evidence is retained in
[conditional_state_evidence.json](conditional_state_evidence.json). Fifteen native
cases include nine paid card witnesses and six synthetic queue/mapping contracts;
nine packaged witnesses compare the exact previous and new runtimes. Tests retain
existing Old Man untap and blink outcomes as controls, including the distinction
between Cloudshift's new control and Ephemerate's return under the owner's control.
EffectEffect expiration and the two-turn registration wrapper now implement the
explicit command protocol. Conditional card arrays and the game state-based cleanup
queue are scanned for unconverted callbacks; normal CLI reports still do not
aggregate these diagnostics. Other effect callbacks and continuations remain open.


Animation evidence is retained in [animation_state_evidence.json](animation_state_evidence.json).
Thirty-three native cases include both stack-copy modes for all 13 Licids,
selected timed/mass animation, permanent/temporary layering and a changed-trait
collection contract. Eighteen packaged witnesses retain exact before/after state.
The previous runtime executed the original Licid's reversal closure when reversing
a copy. Explicit expiration/reversal classes, a native subclass-payload hook and
canonical changed-trait collections remove that source-world reference for these
paths. A supported animation removal predicate also maps its ability reference.
Other subclasses and predicates require separate conversion/validation.


Temporary-copy evidence is retained in [clone_state_evidence.json](clone_state_evidence.json).
Thirteen native cases and eleven packaged witnesses check four Mirage Mirror
copy categories, mass and overlapping frames, player-bound and face-down deadlines,
Cursed Mirror's haste, restored reference contracts and Eiganjo token keywords.
The blinked artifact's new-incarnation memory already passed on the previous build
and remains a control. The copy-expiration command explicitly maps saved Card and
Player references; non-restored scalar values retain the existing native policy.
Other copy/keyword variants, priority choices and hidden views remain unverified.

Protection expiry evidence is retained in `protection_state_evidence.json`. Six native
regressions cover three paid card paths with and without stack copying; three
packaged witnesses also check original-game independence and intrinsic protection.

Keyword-restoration and Serene Master combat evidence is retained in
`keyword_expiration_evidence.json`. Sixteen native cases cover both stack-copy
modes; eight packaged witnesses record independent source/copy deadlines.

Forced-blocking requirements and finite prevention-shield evidence is retained in
`blocking_prevention_evidence.json`, with sixteen native and eight packaged cases.

Ring progression, text edits, mandatory text-exchange targeting and cleanup evidence
is retained in `ring_text_evidence.json`, with forty native and nineteen packaged
cases. This includes original-game regressions, not only snapshot cleanup.

Reconfigure and mana-keyword expiry evidence is retained in
`attachment_mana_evidence.json`, with twenty-four native and twelve packaged cases.
Attachment changes remove the mapped effect card; mana expiry retains the
existing timestamp-specific removal policy. General attachment and mana choices
still require separate validation.

Host-bound return evidence is retained in `host_return_evidence.json`. Twenty-two
native cases and ten packaged witnesses check captured return payloads, canonical
live/historical links, native paid removals, return destinations and ownership.
Lagrella's friendly return counters use fixed legal native controller choices;
packaged observations retain the ordinary AI's opposing-target selections.

Temporary effect cleanup and regeneration evidence is retained in
`effect_cleanup_evidence.json`. Twenty-eight native cases cover six Commander-card
paths, two scheme-helper contracts and regeneration history/quota contracts.
Twelve packaged witnesses exercise legal cards only. The shared command maps one
effect card; raw regeneration-state restoration does not perform regeneration.


Detain state and expiry now map the detained card and detaining participants into
an independent game. Its activation restrictions include mana abilities; native
attack/block predicates remain distinct from tap and sickness constraints. The
raw first-turn-controlled flag is copied before haste masks its effect. LKI also
preserves this flag without applying new control changes or modifying ordinary
copiable characteristics. The exact final native fixture fails 26 cases on the
prior reviewed production source and passes all 30 on this build. The four other
cases are positive controls. Twelve paid packaged witnesses and the retained
[`detain_control_age_evidence.json`](detain_control_age_evidence.json) document
actual admission, original/copy independence, deadline checkpoints and limits.


Player-control copies now preserve the timestamp layers and the registered agent
selected when each effect began. Fresh controllers bind to copied players/game;
the original game-bound controller objects are never reused. An unregistered
custom controller factory is rejected explicitly rather than silently discarded.
Explicit start/end commands map the controlling player, target, combat flag and
release timestamp. The original departed-controller guard and native deadlines
are retained. The same final native fixture changes from 28 failures / 2 passes
to 30 passes. Thirteen paid packaged witnesses retain source/copy observations;
[`player_control_evidence.json`](player_control_evidence.json) records the exact
inputs, baseline, compiled source and coverage limits. Three separate research
heuristic probes classify legal primary actions as `CantPlayAi`. Full-simulation
candidate enumeration admits those actions; execution, admission and snapshot
fidelity do not establish complete hostile AI policy.


Decision planning now separates the acting player from the decision beneficiary.
Controlled heuristic play uses the legal-action simulator, whose objective stays
fixed even when the controlling player dies and control ends during a rollout.
Planning copies paid pending stack state; a separate copier computes the resolved
no-response score. Stack spell/ability targets join ordinary entity targets,
including abilities whose sacrificed source is already in the graveyard.
The 26-case native fixture reproduces 16 failures and ten positive controls on
previous production source. Sixteen packaged witnesses check actual selection,
costs, unchanged original state while planning, canonical targets and outcomes.
Ordinary heuristic damage-counter controls explicitly use shipped Cautious AI;
no profile is edited. Exact inputs, baseline and limits are retained in
[`decision_perspective_evidence.json`](decision_perspective_evidence.json).


Controlled attack planning now evaluates native legal declarations for the fixed
decision beneficiary. It retains actor ownership and costs, respects mandatory
attacks and optional tax payments, and resolves native blocking/damage through
the second main phase. Search shares the existing deadline, compares at most
128 legal declarations and restores the original random provider. Incomplete
nonterminal rollouts are excluded. Twenty-six native and twenty-six packaged
cases cover actual decisions, costs, damage, control expiry and timeout recovery.
The identical previous-source fixture has 19 failures / 7 passes; all 26 pass
with this patch. [`controlled_attack_evidence.json`](controlled_attack_evidence.json)
retains exact provenance.
This is selected bounded combat planning, not exhaustive strategy or full card
certification; the primary-control extension below covers selected additional decisions.


When a candidate changes the card archive, exact alias and companion wording
reviews remain bound to the previous archive hash. Before catalog and interaction
checks, compare all archive entries and carry a review forward only after checking
its exact script, Oracle identity and native condition. Keep the previous archive
reviews for reproducibility. The primary-control candidate changes only two
AI eligibility flags; the Ratonhnhaké꞉ton alias script and Gyruda/Obosh parity
predicates remain unchanged. Missing reviews correctly block admission.


Primary-control planning now uses paired paid/no-action native future windows.
Five primary sources are witnessed in three AI modes; both Secret cost paths,
Sorin loyalty, Mindslaver sacrifice and original-equipment exile costs are paid
natively. Forecasts sample at most two one-ply actions per main phase under the
shared deadline. Synchronous previews isolate random providers; native terminal
outcomes retain their win/loss flags. Only completed improving full-search plans
can survive a deadline, and the normal picker still validates their identity,
score and targets. Legal redundant equip prefixes remain a strategy gap.

Do not advance unfinished resolving-parent continuations. The three-case
resolver-boundary fixture changes from two failures / one positive control to
three passes, retaining incremental lethal free-cast safety. The 23-case primary
native fixture changes from 23 failures to 23 passes, as does the packaged
witness. Fourteen additional native contracts cover scoring, randomness,
recovery and resolver safety. Full verification passes 613 focused, 1,161 broader
gameplay, 256 serialization and 37 live tests / 593 subtests. Exact provenance and
limits are in `primary_control_evidence.json`; this is not full-card certification.


Paid spell-control planning compares native paid outcomes with a separate
no-response baseline. AI retargeting uses native effect heuristics on a copied
world, maps and validates replacements, retains announced target count and
per-slot damage division, and leaves native target-event dispatch intact.
The identical 28-case fixture changes from 18 failures / 10 positive controls to
28 passes; twelve packaged witnesses change from 11 failures / one positive
control to twelve passes. Final verification passes 641 focused, 1,189 broader
gameplay, 256 serialization and 38 live tests / 605 subtests, plus the Python
quality gate and catalog checks. See `spell_control_evidence.json` for frozen
inputs, binary identities, native payments, baseline failures and remaining
composed-source/strategy limits. This is not full-card certification.


The thirty-third patch adds a bounded paired native response planner,
optional-trigger suffix checkpoints and detached cast provenance. The fresh build
passes 666 focused tests: sixteen composed scenarios, four checkpoint contracts
and five provenance cases supplement the previous 641. Final verification passes
1,214 broader gameplay, 256 serialization and 39 live tests / 619 subtests,
plus the Python quality gate. The provenance tests include actual Zacama mana
outcomes. Fourteen packaged witnesses change from twelve failures / two controls
to fourteen passes. Exact source, build, report and receipt identities are in
[`composed_control_evidence.json`](composed_control_evidence.json). The native
boundary choices and two-seed/128-target forecast bounds are documented there;
full-card rules and strategy certification remain open.


The thirty-fourth patch factors paid outcome scoring into a shared planner,
retains the stack-response adapter and implements the mass-counter AI entry point
and complete modal-cast assessment. An untargeted action gets one candidate;
a targeted action without legal candidates is rejected. The fresh build passes
all 693 focused tests, including 22 mass-counter witnesses and five additional
planner contracts. Final validation also passes 1,241 broader gameplay,
256 serialization and 40 live tests / 633 subtests, plus the Python quality gate.
Fourteen packaged cases change from eight failures / six controls to fourteen
passes. See [`mass_counter_evidence.json`](mass_counter_evidence.json) for exact
source, build, baseline, report, profile and archive identities and material limits.
The initial fresh build omitted the two new fixture copies; its 666-test result
was rejected by the exact-count guard before packaging or activation.
