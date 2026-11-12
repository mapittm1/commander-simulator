# Changelog

Changes to user-visible behavior, backend contracts, and engineering safeguards
are recorded here. Work remains unreleased until an explicit release is made.
Historical entries below summarize verified Git milestones, not release versions.

## Unreleased

- Port the reviewed Commander engine changes to Forge's Star Trek release.
- Match All Good Things... by Oracle ID and verify its final chapter against
  empty exile, a single card, a common type, and overlapping pairs with no
  type shared by every card.
- Preserve legal Altar payments when the planner must reserve other
  permanents for later sacrifice or tap costs.

- Allow Scryfall bulk refresh to reassign a token printing when both identities
  are ineligible for Commander decks and its printing details still match.
  Preserve other printings and the prior Oracle identity.

- Restore eligible permanents and tokens sacrificed during failed AI payments.
  Preserve their prior battlefield state and suppress triggers from undone
  sacrifices and equipment detachment.
- Preserve independent exile events and a Blightsteel Colossus shuffle during a
  mixed payment. Recheck grouped payments when a selected permanent disappears.
- Add 14 native and packaged sacrifice cases covering ownership, tokens,
  equipment, zone replacements, and grouped triggers.

- Require an exact legal counter selection for Tayam and similar costs.
  Use other legal counter types when the preferred types cannot pay.
- Recheck the current permanent, controller, type, and counter availability
  before payment. Restore counters and their payment records if a later
  cost fails.
- Add 10 native and packaged checks for card-counter payments, including
  protected stun counters and Chandra's loyalty removal trigger.


- Restore energy and remove the corresponding counter-removal record when a
  later payment fails. Preserve independent counter loss.
- Reverse supported discard payments from the graveyard or exile, including
  Madness and Rest in Peace, and restore the prior hand order.
- Hold discard log entries and triggers until payment succeeds. In the
  mixed Darksteel Colossus case, preserve the library move and the events
  for the card that remains discarded.
- Add 11 native and packaged tests for energy and discard recovery.


- Reserve permanents for later tap costs when choosing mana sources, helper
  taps, sacrifices, and inputs to mana filters.
- Reselect a tap group if an earlier mana payment uses one of its original
  members and a legal replacement is available.
- Reverse the tap cost of a failed nested mana ability and release the tap
  reservation created by that attempt.
- Add 13 native and packaged regressions for combined mana and tap costs.


- Choose a legal shared-type pair for Weight of Conscience and honor
  effective power or toughness when crewing a Vehicle.
- Check that all creatures chosen for a grouped tap cost still qualify. If
  payment fails, restore direct and grouped taps and their payment records,
  and discard the associated tap triggers.
- Add 14 native and packaged tests for direct taps, grouped costs, trigger
  recovery, shared creature types, and Crew.


- Refund direct and Phyrexian life payments when a later cost fails. Restore
  life-loss history and discard triggers from the abandoned payment.
- Refund mana after failed AI activations. Restore previously floating mana
  and undo reversible activations. Preserve damage and mana from Overabundance
  when the mana activation is classified as non-undoable.
- Add 11 native and packaged regression checks for payment, rollback, and
  unrelated triggers.


- Assign distinct creatures to simultaneous sacrifice requirements, including
  Demon's Herald's three overlapping color requirements.
- Reserve creatures for a spell's sacrifice cost during mana planning and
  payment. Altar's Reap can use Blood Pet or Ashnod's Altar with a second
  creature, and it can use Orzhov Signet when the nested mana payment leaves
  a creature available for the spell.
- Verify nine new cases in the focused native suite and against the packaged
  JAR. The previous runtime failed seven of those cases.


- Restore failed casts to their original zone and position. Refund spent mana,
  undo reversible activations, and keep effects from activations that Forge
  marks non-undoable.
- Hold the cast's zone-change trigger until payment succeeds. The checks cover
  an earlier Phyrexian Arena trigger and Syr Konrad on successful and aborted
  graveyard casts.
- Share chosen mana replacement logic between the engine and payment
  forecasts. Hall of Gemstone changes colored mana to the chosen color and
  leaves colorless mana intact.
- Verify 11 additional cases in the native and packaged suites. The full live
  gate passed 42 tests with 764 subtests.

- Forecast Caged Sun's bonus from mana actually added by the land ability.
  Count Kinnan's bonus only for mana abilities with a tap cost.
- Preserve the type and snow status of each mana source during payment
  forecasting. Respect Ritual of Subdual's replacement before checking mana
  triggers. The regressions cover Frostwalk Bastion, River of Tears,
  Blood Pet, Icehide Troll, and typed colorless payment.
- Verify 16 additional mana-event, snow and replacement cases through the
  native and packaged suites. The live gate passed 42 tests with 753 subtests.

- Resolve Overabundance's bonus mana before damage. Choose reflected mana
  against the outstanding cost during automatic payment and restore the
  previous choice policy when the activation ends.
- Forecast repeated same-name land bonuses for Sasaya's Essence. Apply
  Snowfall's ordinary and snow Island amounts only to cumulative upkeep.
- Verify 53 reflected and triggered mana cases in the native and packaged
  suites. The combined gate passed 813 focused native, 1,361 broader gameplay
  and 256 serialization tests, plus 42 live tests with 737 subtests and 403
  offline tests with 508 subtests.

- Run limited Commander conformance across fixed seeds 42, 0 and 4096, retaining
  all three games and their hashes. Each rule still needs a positive witness;
  contradictions fail the suite and incomplete games remain inconclusive.
  The prior single game did not exercise Goreclaw; the fixed matrix witnesses
  five unblocked damage without dropping the commander-tax or damage checks.

- Clear expired Main Phase 2 mana reservations before checking the reserve
  chance, including zero-chance profiles. Preserve first-main and next-spell
  reservations. Hybrid safety checks use their own RNG scope on the AI
  evaluation thread and combine margins at seeds 0 and 4096. Thirteen native
  cases change from seven failures / six controls to thirteen passes. The
  packaged rules group passes all 36 cases; all 15 exchange/pile cases pass,
  including actual paid Whims choices and unchanged live RNG accounting in
  three AI modes. The bounded forecasts retain the previous permissive
  behavior for an unavailable native score and do not certify optimal strategy.
- Activated the combined runtime with the Divine Intervention correction after
  760 focused native tests, 1,308 broader gameplay checks, 256 serialization
  checks, 42 live tests / 684 subtests, the Python quality gate, and 32,109
  matched catalog constructions. The previous candidate's failed full gate
  remains in the historical evidence. Two production Java files changed;
  the card archive is byte-identical to that draw candidate, with Divine
  Intervention the only rules script changed from the earlier active runtime.
  Oracle text and strategic AI exclusions are unchanged. Retain earlier
  runtimes and exact alias/companion reviews.
- Divine Intervention now queues a separate, counterable draw trigger when
  its current controller removes the final intervention counter. Upkeep at
  zero counters no longer draws by itself. The unchanged 23-case rules
  fixture passes, including source removal, control changes, copied games,
  and Celestial Convergence controls. Full-card and strategic casting
  verification remain ongoing.

- Added paid native planning for Cultural Exchange and Whims of the Fates.
  Validate both creature groups before transferring either side, including
  canceled, unequal and out-of-group replies. Choose paired groups from the
  decision beneficiary's perspective and preserve separation order for seeded
  pile replay. The same 26-case fixture changes from eleven failures / fifteen
  controls to 26 passes; five additional selection contracts pass. Fifteen
  packaged cases change from ten failures / five controls to fifteen passes.
  Activated the pinned runtime after 724 focused native, 1,272 broader gameplay,
  256 serialization and 41 live tests / 648 subtests, the Python quality gate
  and 32,109 matched catalog constructions. Compared all 34,132 archive entries;
  only the two intended AI exclusion flags changed. Retained earlier runtimes
  and exact alias/companion reviews. The pile policy remains all-in-one-pile;
  forecasts and creature ranking are bounded, not optimal or full-card proof.

- Added complete paid mass-counter forecasts for Aether Snap, Dust of Moments,
  Oblivion Stone and Pestilent Haze, including native modal selection and attached
  cleanup. Share the bounded outcome evaluator with composed spell-control
  planning. An untargeted action has one empty target combination; required
  targets still need legal candidates. The identical 22-case source fixture
  changes from eight failures / fourteen controls to 22 passes. Five additional
  contracts cover untargeted actions, missing required targets despite payable
  mana, unaffordable spells, controlled-player perspective and resolving-parent
  guards. Fourteen packaged cases change from eight failures / six controls to
  fourteen passes. Activated the pinned runtime after 693 focused native,
  1,241 broader gameplay, 256 serialization and 40 live tests / 633 subtests,
  the Python quality gate and 32,109 matched catalog constructions. Compared all
  34,132 archive entries; only three AI exclusion flags changed. Retained prior
  runtimes and carried exact unchanged alias/companion reviews forward. All
  native fixture copies are included in the reviewed build; the exact-count
  guard rejected an earlier incomplete build. Forecasts retain two fixed seeds,
  128 target combinations and the shared deadline. Full-card certification and
  exhaustive strategy remain open.

- Added reviewed composed-control planning with paired native response forecasts,
  compound target selection and exact optional-trigger checkpoint continuations.
  Reuse native body/completion code, including resolution events, control cleanup
  and stack bookkeeping. The identical sixteen-case source fixture changes from
  seven failures / nine controls to sixteen passes. Four additional contracts
  cover live-world/random isolation, accepted/declined suffixes, foreign/live
  checkpoint rejection and controlled-player perspective.
  These contracts exposed cast-provenance aliasing. Five dedicated cases change
  from four failures / one control to five passes after separating cast records
  from live spells; include repeated theft, announced targets and Zacama's
  conditional land untap. Activated the pinned runtime after 666 focused native,
  1,214 broader gameplay, 256 serialization and 39 live tests / 619 subtests,
  the Python quality gate (399 offline tests / 508 subtests) and 32,109 matched
  catalog constructions. Fourteen packaged cases change from twelve failures /
  two controls to fourteen passes. They include both coin outcomes, all three
  AI modes, original cast counts, creature/player retargeting and ordinary/stolen
  Zacama. Compared all 34,132 card entries; only two AI exclusion flags changed.
  Retained prior runtime/reviews and carried exact unchanged aliases/companion
  predicates forward. Forecasts sample two fixed seeds and at most 128 target
  combinations; full-card rules and exhaustive strategy certification remain open.

- Added paid native spell-control decisions for Aethersnatch, Commandeer and
  Chef's Kiss, comparing paid outcomes with letting the pending stack resolve.
  Retarget in a copied world, preserve the native spell controller, decision
  beneficiary, announced target count, X and damage division, and validate
  mapped replacements before changing live targets. Native effects still handle
  control changes and target events. Defined nontargeting copy subeffects are
  assessed with their parent while retaining Chain of Smog/Acid policies.
  The identical 28-case source fixture changes from 18 failures / 10 passing
  controls to 28 passes. Twelve packaged choice/payment/outcome checks change
  from 11 failures / one passing control to twelve passes. Both native Commandeer
  cost paths, insufficient alternate payment, Counterspell targets, ownership,
  no-benefit decisions and random-provider restoration have fixed witnesses.
  Activated the pinned runtime after 641 focused native, 1,189 broader gameplay,
  256 serialization and 38 live tests / 605 subtests, the Python quality gate
  and 32,109 matched catalog constructions. Compared all 34,132 card entries;
  only two AI exclusion flags changed. Retained old exact alias/companion reviews
  and carried them forward after script/predicate checks. Composed-source AI,
  exhaustive retarget strategy and full-card certification remain open.
  Refreshed the active catalog from the real November 4 Scryfall export after
  preserving a database backup. All 32,116 Commander Oracle/identity records
  remain unchanged; three keyword metadata and two other-format legality
  differences are recorded explicitly. Freshness checks remain enforced.

- Added a separately validated primary-control candidate for Worst Fears,
  Mindslaver, Secret of Bloodbending, Sorin Markov and The Dominion Bracelet.
  Compare paid native future windows against no-action windows; preserve native
  control timing, source costs, fixed beneficiary and original-world state.
  Added explicit terminal outcomes, thread-local preview randomness, native
  original-equipment exile payment and completed-plan recovery at deadlines.
  The identical 23-case source fixture changes from 23 failures to 23 passes;
  Fourteen additional outcome, randomness, recovery and resolver-boundary
  contracts pass. Exclude unfinished parent continuations from future forecasts
  while retaining incremental free-cast safety, including lethal Ruric Thar
  cast triggers. Activated the final pinned runtime after 613 focused native,
  1,161 broader gameplay, 256 serialization and 37 live tests / 593 subtests,
  the Python quality gate and 32,109 matched catalog constructions. Forecasts sample
  at most two legal actions per main phase; full search can retain redundant
  legal equip prefixes. General strategy and full-card certification remain open.
  Compared all 34,132 card-archive entries before carrying one exact alias and
  two companion wording reviews to the new archive hash; only two AI eligibility
  flags changed. Retained prior reviews and native positive/negative controls.


- Added controlled-attack planning that compares native combat outcomes for the
  fixed decision controller while retaining the actor's ownership and resources.
  Candidates must satisfy native restrictions and attack requirements; mandatory
  attacks against the controller remain legal when required, and optional attack
  taxes cannot be forced. The identical 26-case native fixture changes from
  19 failures / 7 passes to 26 passes, including real damage, first strike,
  lifelink, actor-paid taxes, group restrictions and combat-only control expiry.
  Twenty-six packaged cases verify actual choices, payments and damage in two
  bounded hash-bound receipt parts. Activated the pinned runtime after 576 focused,
  1,124 broader gameplay, 256 serialization and 36 live tests / 570 subtests, the
  Python quality gate and 32,109 matched catalog constructions. Primary heuristic
  control actions, controlled blocking and arbitrary mandatory choices remain open.

- Corrected controlled-player planning to retain the decision controller's scoring
  perspective without changing the acting player's costs or resources. Preserve
  pending stack entries, resolve an independent no-response baseline, and enumerate
  legal spell/ability targets. Preserve incremental free-cast safety without
  replaying an already resolving parent. The identical final native fixture changes from
  16 failures / 10 positive controls to 26 passes; sixteen packaged cases exercise
  actual Shock, Counterspell, Stifle and uncounterable-threat outcomes. Ordinary
  heuristic response controls use the unchanged shipped Cautious profile.
  Separate source-bound combat research retains two controlled attack failures
  and two ordinary positive controls. Cyclic/custom control, combat policy and
  arbitrary mandatory choices remain open.
- Clarified the earlier ControlPlayer support finding: the three unsupported
  classifications belong to heuristic AI. Full-simulation candidate enumeration
  already admits those legal actions; neither admission nor snapshot fidelity
  establishes complete strategic support.

- Preserved timestamped player-control layers and created independent copied-world
  agents. Mapped scheduled turn/combat acquisition and release without replaying
  control events during copying. Thirty native cases and thirteen paid packaged
  witnesses cover Worst Fears, Mindslaver, Secret of Bloodbending and Emrakul's
  following uncontrolled extra turn, with explicit policy and cost-choice limits.
  The heuristic selector rejects the three tested primary ControlPlayer
  actions as unsupported; the source-bound research failures remain documented.

- Moved the remaining loose legacy simulator script and original project ZIP from
  Downloads into ignored `data/local-archive/`, with collision checks, byte hashes
  and a migration receipt. Active project files remain in the Desktop checkout.

- Preserved Detain membership, player-bound deadlines and independent live/LKI
  state in game snapshots. Preserved raw control age so a newly controlled
  creature cannot use a tap ability after losing haste. Thirty native cases
  reproduce 26 failures and four positive controls on the previous engine;
  twelve paid packaged witnesses check expiry, multiple detainers, blink,
  control changes, extra turns and haste loss. Pinned the patch and evidence.

- Mapped temporary effect-card cleanup for Fog, regeneration, death replacement,
  Unearth, Amass and replayed spells. Preserved regeneration quotas/history in live
  snapshots and LKI, correcting negative quotas and missing Spiny Starfish tokens.
  Twenty-eight native checks and twelve packaged witnesses distinguish gameplay,
  state, lifecycle and diagnostic coverage; scheme-only helper checks stay separate.

- Fixed host-bound exile returns in snapshots by mapping captured return commands,
  zone records and live/historical linked-card lists. Twenty-two native and ten
  packaged cases cover Banishing Light, Banisher Priest, Grasp of Fate, Kitesail
  Freebooter, Lagrella, early exile exits, tokens and already-paid pending spells.

- Fixed Reconfigure cleanup and temporary mana-granted keywords in copied games.
  Explicit mapped callbacks remove attachment effect cards and expire only their
  granted keyword layer. Paid regressions cover Lion Sash, Lizard Blades, all
  three retained temporary-haste mana sources and permanent Hall haste as a control.

- Fixed Ring progression/designation snapshots, stale former-owner bearer cleanup,
  copied word/type layers and stale word-derived keyword caches. Registered legal
  mandatory text-box exchange targeting and mapped text restoration. Forty native
  and nineteen packaged cases cover original-game defects, actual Ring combat,
  Clone/LKI distinctions, word expiry and exchanged abilities with explicit limits.

- Fixed lost forced-block requirements and lingering finite prevention shields in
  copied games. The snapshot retains requirement timestamps and mapped attackers;
  cleanup maps blockers, effect cards, target views and player-stat events.
  Sixteen native and eight packaged witnesses check paid actions, canonical
  references, expiry, partial/stacked quotas and actual damage outcomes.

- Fixed copied-world cleanup of temporary keyword removal and combat power
  exchange. Sixteen native regressions first reproduced the failures; eight
  packaged witnesses now check paid costs, independent deadlines, protection
  splitting, opponent activation and Serene Master's native block trigger.
  Pinned the patch and fixtures and retained before/after evidence and limits.

- Fixed copied-game expiration of temporary creature, group and player protection.
  Six native regressions first reproduced the defect; paid Shelter, Brave the
  Elements and Seht's Tiger witnesses now preserve independent deadlines and
  intrinsic protection. Pinned the patch and regression hashes, rebuilt the
  runtime, and retained broader validation and coverage limits.

### Added

- Map temporary-copy expiration targets/timestamps and saved card/player references
  into snapshots. Restore saved memory using copied-world owner membership and
  preserve the native non-restored-payload policy. Map temporary token-keyword
  cleanup without removing intrinsic keywords or retaining original closures.
- Add 13 native cases and 11 packaged witnesses for Mirage Mirror's four target
  types, Mirrorweave, Shapesharer, Vesuvan's controlled face-down event, Cursed
  Mirror, Eiganjo keywords, overlapping frames, incarnation guards and synthetic
  reference restoration. Preserve the already-correct blink outcome as a control.
- Verification: all Python quality tools and 399 offline tests / 508 subtests;
  312 focused native tests; 860 broader gameplay tests including those 312;
  256 serialization tests; all 25 live integrations / 431 subtests; and all
  32,109 matched native constructions pass. Activate the pinned copy-expiration
  runtime while retaining previous builds, locks and exact before/after evidence.

- Map animation expiration cards/timestamps and explicit Licid end-effect payloads
  into snapshots. Reverting a copy no longer executes the original game's closure.
  Add a trusted native subclass-field hook and map changed-trait collections and
  supported animation removal predicates through the canonical identity graph.
- Add 33 native cases: both stack-copy modes for all 13 retained Licids, selected
  timed/mass and permanent/temporary animation layers, and one synthetic alias/
  collection contract. Eighteen packaged witnesses cover all 13 reversal paths and
  five other animation outcomes, with Oracle-checked payment expectations.
- Verification: all Python quality tools and 399 offline tests / 508 subtests;
  299 focused native tests; 847 broader gameplay tests including those 299;
  256 serialization tests; all 24 live integrations / 420 subtests; and all
  32,109 matched native constructions pass. Activate the pinned animation runtime
  while retaining previous builds, locks and exact before/after evidence.

- Preserve conditional-control arrays with mapped targets and shared cleanup
  identities; reject unknown internal schemas. Map EffectEffect expiration,
  two-turn re-registration wrappers and the state-based cleanup queue without
  retaining original-game closures. Report unsupported nested callbacks as well
  as unsupported conditional/state-based commands.
- Add fifteen native cases and nine paid runtime witnesses for Old Man of the Sea,
  Rootwater Matriarch, Peace Talks and Silence, with real growth/shrink, aura/blink,
  expiry, cast-denial and paid Bolt outcomes. Six native contracts separately
  check queue ordering, aliases, independent arrays and unknown-callback handling.
  Preserve three already-correct primary outcomes as controls.
- Verification: all Python quality tools and 399 offline tests / 508 subtests;
  266 focused native tests; 814 broader gameplay tests including those 266;
  256 serialization tests; all 23 live integrations / 402 subtests; and all
  32,109 matched native constructions pass. Activate the pinned conditional-cleanup
  runtime and retain previous builds, locks and exact before/after evidence.

- Preserve extra-turn and extra-phase stacks, their order and normal-turn
  continuation through snapshots. Map attached triggers and skip-untap/scheme
  references without replaying their registration; retain current extra-turn
  flags/counts, previous-turn player and scheduler counters/control state.
- Add ten native cases and nine paid runtime witnesses for Time Warp, Time Stretch,
  Savor the Moment, Final Fortune, Relentless Assault and Moraug. Verify LIFO
  scheduling, repeated combats, native landfall, phase-entry untapping, end-step
  loss and independent schedule consumption. Six initial native cases reproduce
  the previous build's missing behavior.
- Verification: all Python quality tools and 399 offline tests / 508 subtests;
  251 focused native tests; 799 broader gameplay tests including those 251;
  256 serialization tests; all 22 live integrations / 393 subtests; and all
  32,109 matched native constructions pass. Activate the pinned turn-schedule
  runtime while retaining previous builds, locks and exact before/after receipts.

- Preserve delayed, turn-limited, player-defined and waiting trigger queues through
  snapshots without replaying source events. Map remembered incarnations, spawned
  abilities, captured controllers, extra-trigger counts and suppression flags.
- Replace deferred delayed-trigger registration closures with explicitly mapped
  callbacks. Retain cost-payment receipts, individual parts and global stack
  aliases, including independent conversion/restriction matrices and paid history.
- Add 21 native cases and nine paid runtime witnesses for Kiki-Jiki, Ghostway,
  Pact debt, Sneak Attack, rebound, slowtrips and Suppress. Separate synthetic
  contracts cover held-event metadata, registration lifetimes, suppression and
  receipt graphs. Unknown event payload types still fail rather than being shared.
- Fix the delayed-trigger fixture resolver to admit triggers after each resolution,
  with a fixed safety bound; rebound now checks the resulting Visionary draw.
- Verification: all Python quality tools and 399 offline tests / 508 subtests;
  241 focused native tests; 789 broader gameplay tests including those 241;
  256 serialization tests; all 21 live integrations / 384 subtests; and all
  32,109 matched native constructions pass. Activate the pinned delayed-trigger
  runtime and retain the previous runtime, locks and exact before/after receipts.

- Rebind explicitly supported native expiration callbacks through the snapshot
  identity map. Preserve ordered phase queues, player-bound deadlines, deferred
  registrations and shared callback identity without sharing original closures.
- Preserve temporary controller layers separately from baseline control; map
  source/target links so theft and Sower effects can return control correctly.
  Restore independent player keyword layers and chosen-number rules values while
  keeping each card's existing public/secret number presentation.
- Add eighteen native cases and eight fixed paid runtime witnesses for phase
  progression, pumps, keywords, theft, leave-play effects, untapping, combat
  boundaries, separate Kraj grants, Prelate cast restrictions and scheduler
  contracts. Unconverted callback kinds remain omitted and are exposed by the
  native copier's diagnostic API; this is not complete callback/card coverage.

- Verification: all Python quality tools and 399 offline tests / 508 subtests;
  220 focused native tests; 768 broader gameplay tests including those 220;
  256 serialization tests; all 20 live integrations / 375 subtests; and all
  32,109 matched native constructions pass. Activate the pinned temporal-state
  runtime, retaining previous builds and locks and exact before/after evidence.


- Preserve canonical printed, keyword and granted ability identities in game
  snapshots, including independent costs, static references and optional
  replacement overrides. Retain activation/resolution counts, selected mode
  histories and exact saddle counts; map crew/saddle members to the copied world.
- Add sixteen native usage-history cases and six fixed packaged-runtime witnesses
  with real payment. Cover once-per-turn/lifetime restrictions, countered and
  pending activations, Vampire draw limits, Longbow grants, Omnath resolution
  ordinals, Galadriel modes, repeated saddle and independent reset primitives.
  Document unvalidated full-turn transitions, combat rollover and grant changes.

- Verification: all Python quality tools and 399 offline tests / 508 subtests;
  202 focused native tests; 750 broader gameplay tests including those 202;
  256 serialization tests; all 19 live integrations / 367 subtests; and all
  32,109 matched native constructions pass. Activate the separately pinned
  usage-history runtime, preserve previous locks/builds and bind before/after
  receipts to exact compiled sources and test hashes.


- Preserve active cast faces and clone frames through snapshots: Adventure, split,
  modal back-face, morph/disguise, prototype and bestow forms retain their actual
  characteristics. Keep clone provenance, bestow/prototype timestamps and copied
  card views independent and consistent with the paid form.
- Map exile/effect provenance so a resolved Adventure remains playable after
  repeated snapshots. Refresh disguise's keyword cache and reconstruct only its
  marked face-down reset using a fresh callback bound to the copied card.
- Add sixteen native paid-action cases, including normal front-face controls,
  invalidated bestow targets, repeated Adventure copies and later casting, paid
  disguise turn-up, independent clone state, and ward countering a Bolt after a
  paid response consumes the opponent's remaining mana. Add six packaged-runtime
  witnesses that retain every live-zone instance and compare resolved outcomes.
- Preserve before-fix receipts; check witness costs/text against retained Scryfall
  identities and selected versioned Wizards rules. Document unverified callback,
  hidden-view, merged/perpetual-state and alternate-cost behavior.


- Verification: all Python quality tools and 399 offline tests / 508 subtests;
  186 focused native tests; 734 broader gameplay tests including those 186;
  256 serialization tests; all 18 live integrations / 361 subtests; and all
  32,109 matched native constructions pass. Pin cast-form evidence and activate
  the reviewed runtime while retaining earlier builds and locks.

- Restore existing paid spells, activated abilities and admitted triggers in AI
  snapshots instead of recasting printed spell definitions. Preserve mana/X,
  kicker/buyback, modal graphs, target incarnations/divided allocations, historical
  costs, cast history and floating mana without replaying cast events.
- Add 30 native stack regressions and five packaged-runtime witnesses with actual
  payment and copied resolution. Retain a before-fix receipt showing lost X/kicker
  and dropped activated/triggered entries. Reject unknown mutable event payload
  types rather than sharing them across simulation worlds.
- Document remaining stack limits, including alternate faces, arbitrary payload
  graphs and mid-resolution continuations. Full card/AI certification remains
  unfinished. Bind stack evidence to the fresh runtime, source and test hashes.


- Verification: all Python quality tools and 399 offline tests / 508 subtests;
  170 focused native tests; 718 broader gameplay tests including those 170;
  256 serialization tests; and all 17 live integrations / 355 subtests pass.
  Pin the reviewed stack build and retain earlier runtimes and locks.

- Preserve both registered starting life and each live player's starting-life
  baseline in AI copies. Restore the baseline before current life so their
  different values remain intact. This fixes Righteous Valkyrie granting +2/+2
  at 40 life in a copied Commander game instead of requiring 47 life.
- Add sixteen native starting-life regressions: five configured baselines;
  seven Righteous Valkyrie threshold controls; actual mana-paid Angel's Mercy
  and Chaplain's Blessing casts that cross or miss the threshold; eliminated
  players across repeated copies; and a live baseline override that leaves
  registration unchanged. Check the three witness cards against independent
  retained Scryfall Oracle facts and preserve a before-fix packaged receipt.
- Verification: 399 offline tests / 508 subtests and all Python quality tools;
  140 focused native tests; 688 broader gameplay tests including those 140;
  256 serialization tests; all 16 live integrations / 350 subtests without skips;
  and all 32,109 matched native constructions pass. Pin aggregate evidence in
  engine/starting_life_evidence.json and activate the reviewed starting-life
  runtime while preserving earlier builds and locks.
- Fix eliminated-player state in AI snapshots. Map all registered participants,
  restore life and counters before immutable loss outcomes, restore active/lost
  lists without replaying player-loss actions, and preserve eliminated-player references in damage and zone history.
  Copy commanders/effects only for surviving players so departed owned cards are
  not restored to play.
- Add fourteen native participant regressions: concession, life and poison losses
  at all three non-active seats; target rejection and turn order; historical
  damage and remembered players; independent later losses and preserved loss
  order; removed owned cards; and Blood Tyrant's resolved loss trigger without
  duplicate counters. Check the trigger expectation against the separate retained
  Scryfall Oracle record. Add bounded packaged-runtime participation witnesses and
  retain a before-fix receipt showing both the resurrected seat and history error.
- Verification: 399 offline tests / 508 subtests and all Python quality tools;
  124 focused native tests; 672 broader gameplay tests including those 124;
  256 serialization tests; all 15 distinct live integrations / 334 subtests
  in the final complete run; and all 32,109 matched native constructions pass.
  Preserve before-fix receipts for resurrected players, unmapped historical targets
  and incorrectly restored life totals. Activate the
  reviewed participant runtime and pin engine/participant_state_evidence.json;
  preserve prior builds/locks and keep all scratch work in the Desktop checkout.
- Preserve global and per-card damage histories, historical source/target records,
  equal-valued packet identities and independent current/previous-turn receipt
  lists in AI snapshots. Reuse each copied live-source history in the global
  ledger so later damage is counted once. Keep one previous-turn provenance
  ledger for explicit prior-turn source queries and expire it at the next cleanup.
  Surface missing provenance in filtered queries; safely clean up vanished targets.
- Add 19 native damage regressions, including seven normal-admission, mana-paid
  Eagle Vision freerunning controls. Verify source types and control at damage
  time, noncombat and creature-target exclusions, independent copies, vanished
  tokens and cleanup. Keep paid Lightning Bolt/Gorehorn Minotaurs bloodthirst
  controls for opponent damage versus self-damage. Independently check the witness
  facts against retained Scryfall Oracle identities/text hashes. Record a packaged
  before-fix freerunning failure rather than inferring it from script presence.
- Verification: 399 offline tests / 508 subtests; all Python quality tools;
  110 focused native tests; 658 broader gameplay tests including those 110;
  256 serialization tests; all 14 live integrations / 323 subtests; and all
  32,109 matched native constructions pass. Freeze the bound before/after evidence
  in engine/damage_state_evidence.json, preserve exact receipt/source bytes and
  activate the reviewed damage runtime with earlier builds and locks retained.
  Align the versioned engine-config example with the selected reviewed runtime.

- Consolidate the old Downloads checkout, backups and scratch scripts under the
  active repository's ignored data/local-archive/. Verify all 1,359 moved files
  by SHA-256 and preserve the migration manifest; leave the old Downloads
  workspace empty. Document one active source tree and consistent file placement
  in AGENTS.md and CONTRIBUTING.md. Keep future scratch work under data/development/.
- Add a seventh native patch for historical card state in AI copies. Copy the
  recorded departed object instead of its current zone incarnation; preserve
  current/previous-turn zone-entry records rather than inventing entries during
  snapshot construction. Map owners, controllers, zones, sticker sheets, linked
  memories and permissions into the copied game. Keep distinct remembered
  incarnations and independent mutable damage-history containers. Preserve shared
  combat-band identity within a snapshot and reset copier caches between copies.
  Propagate history-copy failures instead of silently dropping records.
- Add 14 native history regressions, including actual mana-paid Tragic Slip
  resolution and state-based actions with positive creature-death and negative
  noncreature-death controls. Add bounded packaged-runtime witnesses for four
  zone-history cases and both spell outcomes. Full rules coverage remains open.
- Verification: 399 offline tests / 508 subtests; all Python quality tools;
  91 focused native tests; 639 broader gameplay tests including those 91;
  256 serialization tests; all 13 live integrations / 312 subtests; and all
  32,109 matched catalog constructions pass. Pin the aggregate history, paid-spell,
  sticker and catalog receipts in engine/history_state_evidence.json. Preserve
  raw evidence bytes and portable LF witness source with Git attributes. Activate
  the reviewed history runtime while retaining previous runtimes and locks.

- Preserve applied sticker records and sheet/slot identities in native AI
  snapshots; rebind source sheets to the independent copied game. Retain LKI
  records without reapplying effective characteristics. Check used stickers
  across all public zones so changing a card's controller cannot free a slot.
  Preserve ordinary Clone behavior and hidden-zone sticker removal. Require
  the `sticker_snapshot_state` capability for games with sticker selections.
- Add 22 native regressions for four sticker kinds in three public zones,
  independent LKI lists, ticket counters, control changes, zone transitions,
  ordinary copies and a paid spell copied on the stack and resolved.
- Verify all 480 physical stickers across 48 Commander-legal sheets and 144
  public-zone fixtures in the packaged runtime. Compare physical record hashes,
  effective characteristics, LKI records and exact independent sheet identity.
  Settle continuous effects before observations. Preserve hashed raw evidence in
  `engine/sticker_state_evidence.json`; direct placement does not certify every
  ability's resolution, historical LKI or strategic AI decisions.
- Validation: 399 offline tests / 508 subtests; all quality tools; 77 focused
  native tests; 625 broader gameplay tests including those 77; 256 serialization
  tests; all 12 live integrations / 306 distinct subtests across the suite and
  targeted fixture rerun; and all 32,109 matched catalog constructions pass.
  Retain the earlier reviewed runtime and its historical lock.

- Add exact `(SET) collector` Attraction selection with validator version 9.
  Preserve printing IDs and functional lights in immutable revisions and the
  latest projection; hash explicit choices and reject ambiguous, conflicting,
  changed, incorrectly typed or nonlegal source printings. Keep name-only and
  unselected legacy behavior. Generate native selectors from validated fields.
- Add `sync-attractions`: bounded, attributed, atomic printing-only Scryfall
  enrichment that preserves Oracle records and bulk freshness. Require the
  complete fixed-query inventory, safe pagination, unique identities, bounded
  strict JSON and legal English source printings. Retain all 87 current variants.
- Add fail-closed native collector/light preflight with bounded workers/receipts
  and a 64-entry engine/source/edition-bound cache. Recheck printing facts during
  admission and native resource/source identity under the launch profile lock.
  Preserve semantic printing evidence through batch seat rotations and resume.
- Fix native AI copies omitting Attraction decks, junkyards, sticker sheets and
  other real supplementary zones. Pin the fifth source patch and seven native
  copy regressions. Block incompatible older engines for supplementary games.
  Pin all 686 edition metadata files alongside executable/card/runtime hashes,
  with portable filename ordering and byte bounds. Preserve historical locks.
- Verification: 398 offline tests and 508 subtests; Black, isort, Pylint and
  Bandit; 55 focused native tests; 603 broader gameplay tests (including those
  55); 256 serialization tests; all 11 live integrations/162 subtests; and all
  32,109 matched catalog constructions pass. All 87 Attraction printings retain
  their lights through native registration and AI copies. The CLI plays and
  resumes an isolated pod with a selected printing. These checks do not certify
  every Visit ability, sticker effect or AI decision.


- Verify all seven current Commander meld results with 49 native partial-state
  cases: formation, graveyard/exile separation, missing/wrong-zone parts,
  ownership/control rejection and a physical Clone reverting to its printed
  identity in exile. Use real native Meld effects, bounded workers and receipts,
  exact engine/input/source hashes and an independently checked catalog inventory.
  Trigger admission, payment, timing and result abilities remain separate work.
- Resolve Fang/Vanille's ambiguous self-printing references through exact source
  proofs pinned to both printing/Oracle identities and complete relationship
  graphs. Reject malformed, conflicting, duplicate or changed evidence. Keep the
  existing result registry deny-only. Validator version 8 and audit version 4
  freeze used front proofs without bypassing ordinary deck checks or changing
  historical evidence.
- Validation: 367 offline tests and 458 subtests pass; Black, isort, Pylint and
  Bandit pass. All ten live integrations pass with 75 subtests. Keep native patch
  context spaces intact while excluding their expected end-of-line spaces from
  Git's patch-file whitespace check; reviewed patch bytes remain unchanged.


- Add explicit pregame commander color choices with validator version 7,
  immutable storage, color identity/companion checks and safe native export.
  Reject missing/invalid/unauthorized choices and incompatible engine capabilities
  before simulation creation. Preserve the designation across native deck artwork
  selection, linked abilities, logical/LKI copies and AI simulation copies.
- Fix native companion pregame type evaluation for Grist/Umori. Verify Lurrus's
  paid special action, insufficient-mana rejection and combat timing admission.
  Preserve the backend's independent full 100-card companion condition gate.
- Promote the reviewed runtime after 48 focused native tests, 596 broader native
  gameplay tests (including those 48), 256 serialization tests, nine live
  integrations/26 subtests and 32,109 catalog constructions pass. Retain exact
  source, recipe, executable, JVM and report hashes plus historical engine locks.
  Freeze build recipe bytes and recheck inputs after compilation and packaging.
- Validation: 360 offline tests and 439 subtests pass; the nine native opt-in
  tests run separately. Black, isort, Pylint and Bandit pass. Local printing
  enrichment retains 59 verified printings with 32 aliases without changing
  Oracle rules or freshness.
- Resolve exact English printed/flavor names and face aliases through retained
  printing metadata. Backfill an indexed projection, remove stale aliases on
  updates, retain ambiguity and prioritize canonical names. Original/canonical
  aliases merge before singleton validation. Printing enrichment leaves current
  Oracle rules and catalog freshness unchanged. No fuzzy matching is introduced.


- Add explicit `Companion` imports outside the 100 starting cards. Require one
  legal companion with a valid ability, permitted color identity and no matching
  starting-card name. Validator version 6 checks its condition against the full
  starting deck, including the commander, through the pinned native engine.
  Preserve choices and admission evidence in immutable revisions and export to
  Forge's Sideboard section. Existing Sideboard/Maybeboard imports remain ignored.
- Bound native admission plans/receipts, process time, heap and logs; verify
  engine/source/input identity, native Java and exact condition wording. Missing,
  failed, changed or malformed evidence blocks approval. Use an identity-bound
  64-entry process cache. Extract shared process-profile/JVM configuration into
  a runtime module independent of deck/rules services, preserving existing APIs.
- Add exact Gyruda/Obosh display-wording reviews pinned to Oracle identity,
  native predicate text and card archive. Bind used registry bytes; changed
  identities, predicates or Oracle wording do not inherit approval.
- Validation: 339 offline tests and 418 subtests pass with eight native opt-in
  tests excluded from the offline gate. Black, isort, Pylint and Bandit pass.
  The native companion matrix passes 22 cases for all nine legal identities;
  All eight live integrations pass, including the original 41 controlled cases.
  Lurrus integration admits Elas il-Kor and rejects a commander-only violation.
  This verifies condition admission, not companion AI strategy or all special
  action/payment interactions. Bind the extracted runtime source into controlled
  interaction evidence as well. Full live regression results remain local.


- Verify 581 native regressions in 57 selected Forge AI/gameplay classes without
  failures, errors or skips, including the previously verified 33 sticker tests.
  Record selected classes, process result and raw TestNG/log hashes. Retain raw
  reports locally; exclude GUI/download tests from this headless run. This does
  not certify every catalog card or interaction. Preserve native CRLF bytes while
  identifying them as line terminators for Git whitespace checks.


- Accept distinct `Stickers`/`Sticker Sheets` and `Attractions` import sections.
  Enforce authoritative types, current Commander legality, at least ten unique
  selections and one copy each; keep them outside the 100 starting cards and
  reject Attractions in mainboard/commander slots. Validator version 5 hashes
  supplementary choices into immutable revisions and maintains a separate latest
  projection without changing the legacy starting-card table.
- Export supplementary sections to Forge. Native pregame loading preserves all
  48 catalog sticker sheets and all 22 legal Attractions and reveals exactly
  three distinct selected sheets. Empty controls load neither. Explicit Attraction
  printing/light selection, companions and commander color choices remain work.
  Share trusted JVM initialization across fixed native workers.
- Preserve exact patch, Java regression and recipe bytes in Git attributes so
  Windows line-ending conversion cannot invalidate reviewed build checksums.
  Verify staged patch/test blobs against the recipe. Validation: 331 offline
  tests and 395 subtests pass; all six live integration tests also pass. Black,
  isort, Pylint and Bandit pass. The final catalog probe again constructs all
  32,109 matched identities without errors. Native results remain in local run
  artifacts, separate from assertions of complete gameplay support.


- Add a separately pinned sticker-capable Forge build from immutable official
  source, a reviewed upstream Wicker Picker patch and local multiple-kicker fixes.
  Retain patches, GPL license, regression source, exact source/Maven checksums and
  a bounded build tool. A fresh build passes all 33 native sticker tests without
  skips, including 10 local affordability, timing and printed-kicker regressions.
- Add `engine-card-probe`: freeze every Commander-format audit identity and build
  each matched native card through Forge's factory. Bound streaming evidence,
  reject duplicate/reordered identities and invalid receipts, and bind engine,
  source, catalog, count and observation hashes. The fresh build constructs 32,109
  identities with zero construction failures; seven non-starting meld-result
  objects are explicit exclusions. This is constructor coverage, not certification
  of all rules interactions or AI decisions.
- Support explicit, separately pinned candidate engine locks. Reject duplicate
  configuration fields, unsafe JAR names and loose-script overrides. Scope exact
  alias approvals independently to each card archive. Add a deny-only Ragnarok
  meld-result review pinned to complete related printing metadata. Advance the
  legality validator to version 4 and audit/gap classification to version 3;
  preserve historical catalog and audit evidence.
- Validation: Black, isort, Pylint and Bandit pass; 326 offline tests and 389
  subtests pass, with five native opt-in tests skipped in the offline gate.
  All five live integration tests pass, including CLI games, batch resume, rules
  witnesses and all 41 controlled interactions. Select the reviewed build as the
  default and preserve the previous 2.0.15 lock for historical verification. See
  `docs/CARD_COVERAGE.md` for remaining pregame setup and gameplay coverage work.


- Expand the controlled suite from 39 to 41 scenarios in `raton_equipment_v10`,
  adding a ninth fixed counterspell-chain family. Preserve all previous 39
  expectations and all 15 reviewed card identities. Exercise a paid third-player
  Disallow targeting a pending Disallow and a two-Island affordability control.
- Verify native last-in-first-out resolution, distinct counterspell identities,
  post-payment stack targets, priority returning to the active player, another
  complete pass cycle before the surviving original spell resolves, and no cost
  refund for a countered spell. Record remaining mana and intermediate zones.
  Share setup validation and serialization while keeping scenario expectations
  independent. Add counterexamples for early resolution, skipped passes, wrong
  targets, forged/refunded payments and missing or malformed evidence.
- Validation: Black, isort, Pylint and Bandit pass; 298 offline tests and 298
  subtests pass (5 opt-in tests skipped). The native integration passes all 41
  scenarios. Full card/rules coverage, default AI strategy, longer chains and
  additional/variable costs remain separate work.


- Expand the controlled suite from 36 to 39 scenarios in `raton_equipment_v9`,
  adding the eighth priority family and Island as the fifteenth reviewed identity.
  Preserve all original 36 case expectations. Drive Forge's native four-player
  priority loop through all-pass resolution, a paid Disallow response, and a
  response that cannot be afforded with two Islands.
- Preserve native callback order, stack/target identities, retained priority,
  restarted pass cycles, mana payment/source colors, tapping and final zones.
  Add independent negative verifier tests; compile only fixed Java controllers
  and bind both generated classes to startup, receipts and post-run checks.
  Initial partial states and choices are controlled; full priority rules,
  default AI strategy and counter-counterspell chains remain separate work.
- Validation: Black, isort, Pylint and Bandit pass; 290 offline tests and
  283 subtests pass (5 opt-in tests skipped). The explicit native integration
  passes all 39 scenarios and verifies both class hashes, receipt bounds/hashes
  and engine profile restoration. All previous 36 expectations are unchanged.

- Expand the controlled suite from 28 to 36 cases in `raton_equipment_v8`. Add a
  seventh casting family and Plains as the fourteenth pinned Oracle identity.
  Exercise paid Ranger's Guile/Bonesplitter casts, no-mana/off-color rejection,
  noninstant timing controls, no-priority fixture admission, and a paid instant
  during combat. Original 28 case expectations remain unchanged.
- Use Forge's native legality/payment predicates and `ComputerUtil.playStack`
  cost machinery with real lands; record payment source/color/tapping, proposed
  spell/target IDs, stack entries, and controlled phase/priority ownership. Bind
  raw evidence independently and cross-check summary counts. Rejected proposals
  preserve resources, card/target state, and pending-stack identities.
- Require three fixed casting checkpoints and document the scope boundary:
  timing/priority admission is checked before payment; priority ownership is
  initialized by the fixture and the full native pass loop is not certified.
  Additional/variable costs, partially started rollback, and response chains remain
  pending. No production AI or card-script correction was needed for these cases.
- Validation: 281 offline tests and 265 subtests pass, including forged payments,
  wrong source colors/controllers, raw identity/count conflicts, rejected-state
  mutations, missing evidence, and checkpoint controls. Native CLI integration
  passes all 36 scenarios and verifies receipt/class hashes and profile restoration.
  Black, isort, Pylint, and Bandit pass; existing execution/JSON limits remain.

- Expand the controlled suite from 24 to 28 cases in `raton_equipment_v7`, adding
  a sixth replacement-order family and Urza's Armor as the thirteenth pinned
  Oracle identity. Exercise Furnace of Rath then prevention (5 damage), and
  prevention then doubling (4 damage), with combat/noncombat trigger controls.
- Add a fixed, readable test-only Java controller that returns native replacement
  effects and records candidate/selected effect IDs, card IDs/timestamps,
  controller identities, and the affected player. Independently verify both
  callback inventories and chosen order; matching life totals alone cannot pass.
- Compile only that repository source using trusted local JDK 17+ `javac` from
  PATH or `COMMANDER_JAVAC`, targeting Java 17 with processors disabled. Bound
  compilation to 30 seconds/64 KiB logs and the class to 64 KiB. Preserve compiler,
  source, class, log, and process evidence; check class/tool integrity before
  accepting native results. Ordinary simulations/offline tests need no compiler.
- Keep the original 24 expectations, production Forge scripts/AI, receipt/input
  limits, profile restoration, and six-family aggregate success requirements.
- Validation: 273 offline tests and 237 subtests pass, including forged/missing
  choices, wrong controllers/candidates, trigger/damage counterexamples, compiler
  failures, and changed/oversized artifacts. Native CLI integration passes all
  28 cases and verifies receipt/class hashes and profile restoration. Black,
  isort, Pylint, and Bandit pass. Cost/priority and broader simultaneous effects
  remain pending; these controlled choices do not certify default AI strategy.

- Partition the existing 24 controlled cases into five fixed families in
  `raton_equipment_v6`: Equipment (8), damage (6), countering (2), targeting (4),
  and protection (4). Preserve every reviewed expectation and all 12 pinned
  identities. Keep the CLI command and the single bounded Python/JVM process.
- Use a schema-2 manifest plus independently bounded files in
  `config/interactions/`, with code-owned paths, order, and exact case inventory.
  Hash each document's exact parsed bytes and bind the complete path/hash map
  into the suite fingerprint; verify definitions before, within, and after execution.
- Write one bounded `observations-<family>.json` receipt per family. Report
  schema 2 retains the combined checks and adds `partition_results` with receipt
  hashes/statuses. New runs no longer emit a combined `observations.json`.
  Missing/misidentified evidence, failed processes, and conflicting runtime
  metadata cannot pass; a witnessed mismatch fails the combined result.
- Retain the 128 KiB/32-level per-document limits. Size-check generated worker
  inputs before exclusive creation; keep readable combined reports. Organize
  definition, runner, verifier tests, and synthetic helpers into focused files.
- Validation: 261 offline tests and 211 subtests pass. Native CLI integration
  passes all 24 unchanged cases, checks every receipt's hash/size, and verifies
  profile restoration. Black, isort, Pylint, and Bandit pass. Outcome-changing
  replacement-order choices remain the next interaction milestone.

- Expand the controlled suite from 23 to 24 cases in `raton_equipment_v5`.
  Witness normal Equipment attachment, then resolve Tel-Jilad Defiance and let
  native state-based actions detach it. The Equipment remains on the battlefield
  and the same Assassin token loses the +2/+0 bonus.
- Require a case-specific `after_detachment` checkpoint. Independently verify raw
  attachment, target, stack, and stable object ID/zone-timestamp evidence; an
  unattached final state alone cannot pass. Missing/extra checkpoints or malformed
  evidence are inconclusive; witnessed contradictions fail. The original 23 cases
  retain their six-checkpoint contracts and all 12 Oracle identities stay pinned.
- Document bounded suite partitioning and explicit affected-player choice
  recording as prerequisites for the next replacement-order test family.
  Outcome-changing order choices, full casting/priority, and combos remain pending.
- Validation: 246 offline tests and 195 subtests pass, including detachment
  counterexamples, raw identity mutations, and checkpoint boundary checks.
  Native CLI integration passes all 24 cases; Black, isort, Pylint, and Bandit
  pass. No production Forge script correction was needed for these cases.

- Expand the controlled interaction suite from 20 to 23 cases in
  `raton_equipment_v4`. Resolve Tel-Jilad Defiance, Ranger's Guile, and Alexi's
  Cloak against the Assassin token before the Equipment-return ability. Cover
  protection from artifacts preventing attachment, and hexproof/shroud allowing
  an attachment that does not target the creature. Pin all 12 fixture identities.
- Compare native target eligibility for both players using an unplayed nonartifact
  creature-targeting spell. Freeze response target IDs/zone timestamps and stack
  identities, independently binding the original token and remaining reflexive
  trigger while preserving the separate Equipment target. Missing evidence is
  inconclusive; contradictory bindings fail even with matching keyword flags.
- Preserve isolated execution, the unchanged 128 KiB input/receipt cap, source
  hashes (including the new response verifier), and readable state reports.
  Add focused response-verifier tests and explicit casting/priority boundaries.
- Validation: 242 offline tests and 177 subtests pass; the native CLI integration
  test passes all 23 cases. Black, isort, Pylint, and Bandit pass without new
  suppressions. No production Forge script correction was needed for these cases.
  Protection-driven detachment and outcome-changing replacement order remain pending.

- Expand the controlled interaction suite from 16 to 20 cases in
  `raton_equipment_v3`. Add Ground Seal before target selection, activation of
  its static restriction after selection, removal before selection, and a
  targeted Equipment leaving and returning to the graveyard as a new object.
- Pin Ground Seal as a ninth Oracle identity. Record target-card zone timestamps
  and both native roundtrip transitions; independently bind the original target
  across the response. Missing evidence is inconclusive and conflicting raw
  identities fail even with matching summary flags.
- Initialize injected fixture cards with native game timestamps. Keep the worker
  receipt cap at 128 KiB by using compact JSON; generated reports retain
  indented checkpoints. Split verifier tests into focused behavior/boundary groups.
- Validation: 239 offline tests and 153 subtests pass; the native CLI integration
  test passes all 20 cases. Black, isort, Pylint, and Bandit pass. No production
  Forge script correction was needed for these tested cases. Full casting,
  priority, protection, and outcome-changing replacement ordering remain pending.

- Expand the controlled interaction suite from eight to 16 cases in
  `raton_equipment_v2`. Add full combat prevention, noncombat-only prevention with
  a combat control, combat/noncombat damage doubling, combined prevention and
  doubling, and Disallow countering either the initial or reflexive trigger.
- Counter tests place the actual counterspell ability on Forge's stack and resolve
  its counter effect. Record raw stack/target ability IDs and independently bind
  them to the previously observed pending trigger; summary flags alone cannot pass.
- Pin eight fixture card identities and include loyalty in Oracle fingerprints.
  Record active prevention/doubling effects, The Wanderer's starting loyalty,
  and the resolved counterspell's graveyard state. Existing v1 report files remain
  historical; the current command requires the complete v2 suite.
- Validation: 235 offline tests and 129 subtests pass, including new damage and
  counterspell counterexamples and raw-identity failure checks. The native CLI
  integration test passes all 16 cases. Formatting, lint, and security checks pass;
  no production Forge script correction was needed for these tested cases.

- `engine-interactions`: controlled partial-state tests using the actual pinned
  Forge damage, trigger, stack, zone-change, and state-based-action APIs. Eight
  initial Ratonhnhaké꞉ton/Equipment cases cover return/attach, separate target
  timing, unavailable/opponent-owned Equipment, exiled targets, removed tokens
  and sources, creature Equipment, and noncombat damage. All eight pass; no
  production engine-script correction was needed for these tested cases.
- Independent versioned expectations, current Oracle-characteristic fingerprints,
  direct state checkpoints, input/receipt/log/source hashes, and explicit failed
  or inconclusive outcomes. Missing evidence, setup/process failure, and changed
  provenance cannot pass. JSON is bounded to 128 KiB and 32 nesting levels with
  duplicate-field rejection.
- A pinned optional JPype bridge in `requirements-interactions.txt`, invoked in
  an isolated Python child with the existing engine profile lock/restoration,
  stripped environment, wall/log limits, and a JVM heap cap. Ordinary simulation
  commands remain standard-library-only. Partial-state fixtures never become
  production decks, bracket attestations, benchmark lists, or win-rate samples.
- Validation: 231 offline tests and 107 subtests pass, including mutation and
  provenance/failure controls; the new native integration test passes all eight
  direct-state cases. Black, isort, Pylint, and Bandit pass. Salt and interaction
  JSON readers share a structural scanner while retaining their separate limits.

- Runnable CLI workflows: `doctor` checks catalog/runtime readiness, `demo` runs
  an isolated diagnostic pod, `play` imports four explicit reviewed decklists,
  and `simulate` completes or resumes a saved pod with progress and JSON reports.
  Existing legality, bracket, frozen-evidence, execution, and 100,000-attempt
  limits still gate simulation. Reports distinguish failures and interrupted slots.
- A short CLI quick start and a complete diagnostic deck-format example. Demo
  databases/reports remain available for inspection and resume without adding
  fixtures or test reviews to the main deck library. Existing report files are
  never overwritten; JSON stdout is separated from stderr progress.
- Validation: 211 offline tests and 90 subtests pass; all four opt-in live
  integration tests pass. A two-game CLI demo also completed and its saved
  database was reopened successfully without rerunning completed attempts.

### Repository organization

- Group backend modules under `commander/` by catalog, decks, rules, engine,
  simulation, diagnostics, opponents, salt, and CLI. Keep the root Python entry
  point and command names stable. Unit/live tests and shared fixtures now live
  under `tests/`; detailed docs, examples, and versioned configuration have their
  own folders. Update imports, mock targets, discovery, and documentation paths.
- Centralize checkout data/config paths. Preserve the existing catalog, engine
  assets, game history, and private engine settings while moving the latter into
  ignored `config/engine_config.json`. No new runtime dependencies are required.

### Earlier unreleased additions

- Repository-owned identity review registry with exact Oracle-ID guards, archive
  and script hashes, bounded input, duplicate/conflict rejection, and preserved
  provenance in audits and game/batch evidence. Ratonhnhaké꞉ton now exports to
  the reviewed Forge spelling, Ratonhnhaké:ton; no fuzzy substitution is used.
- Multicolor basic-land fixtures and a live regression for that reviewed export,
  casting, combat-damage trigger resolution, and later Assassin-token participation.
  Equipment recursion, hexproof, and full card conformance remain unverified.
- Meld-gap diagnostics expose the representative and related printing IDs while
  preserving the review block. Classifier version 2 is frozen into new records;
  historical backlog reports keep the stored classifier version.
- Local audit now matches 32,015 of 32,116 Commander-format records, leaving
  101 gaps: 54 confirmed supplementary/meld-result objects and 47 investigation
  items, including the still-unresolved Ragnarok printing-role discrepancy.
- Validation: 199 offline tests and 90 subtests pass, plus all three opt-in
  live-engine integration tests. Black, isort, Pylint, and Bandit pass.

- `engine-audit-gaps`: bounded, immutable engineering triage for new version-2
  audits, separating supplementary/meld-result records from investigation items.
  Historical classifications do not change with later catalog edits. Spelling
  candidates are diagnostic only and remain blocked from export.
- `engine-conformance`: a disposable legal all-Forest pod and three limited
  real-engine log regressions for commander tax, a resolved combat trigger, and
  commander-damage loss with positive life. Missing exercise is inconclusive;
  failures and inconclusive results return nonzero. Reports preserve hashes and
  source lines. Full rules conformance and AI proficiency remain unverified.
- Counterexample tests for witnesses, bounded backlog pages, frozen history,
  sticker-sheet construction, and copied/split/specialized face indexing.
- Validation: 185 offline tests and 78 subtests pass; both opt-in live-engine
  integration tests pass separately. Black, isort, Pylint, and Bandit pass.

### Fixed

- Index explicit Forge copied-face references, reject missing targets, preserve
  standalone canonical names over optional face aliases, and avoid promoting
  specialized faces. The local audit gains 20 matches: 32,014 of 32,116
  Commander-format records. Of 102 gaps, 54 are confirmed supplementary/meld
  results and 48 need investigation, including an unresolved meld role.
- Validator version 3 rejects sticker sheets from starting decks; format legality
  alone does not make these supplementary objects valid deck slots. Malformed
  meld component metadata remains blocked for review without crashing.

### Earlier unreleased additions

- EDHREC salt reference support through bounded local sample import, immutable
  source snapshots, exact catalog/face matching, and `deck-salt` advisory reports.
  Ratings retain the 0–4 scale, voting year, observation date, source attribution,
  resolution catalog, and explicit unknowns. New commands: `salt-import`,
  `salt-show`, and `deck-salt`; a three-card example manifest is included.
- Validation for finite/precise scores, rank order, duplicate fields/names/ranks,
  timestamps, source URL, file/card/numeric limits, and ambiguous identities.
  Legal-deck checks precede analysis; immutable snapshots keep prior ratings intact.
- Salt and deck import share the existing game-card candidate rules, avoiding
  false ambiguity from art-only records while retaining banned-card identification.
- Rated/unrated quantities, known-rating subtotal and mean, and stale-source
  labeling. These descriptive aggregates are not an official EDHREC deck score
  or an aggression probability. Forge behavior and observed targeting are unchanged.
- A 100-card reference-page sample was imported locally for verification; full
  source data remains outside Git. Automatic website synchronization is not
  implemented pending a supported access path.

- Catalog-wide engine script/export audits with immutable per-card snapshots,
  current catalog provenance, pinned engine hashes, bounded historical pages,
  and separate Commander-legal/other/unknown counts. New commands: `engine-audit`,
  `engine-audit-show`, and `engine-audit-cards`. Audits do not run Java or certify
  gameplay correctness. Missing export matches include non-startable faces.
- Exact Universes Within aliases declared by engine scripts, including paired
  faces. These export the original script name and reject ambiguous mappings;
  no fuzzy matching or inferred card replacements are introduced. The local
  audit gained 29 matches, leaving 122 unmatched Commander-format records.
- Audit regression tests cover count/page bounds, provenance, race detection,
  rollback, immutability, integrity checks, historical retention, unsafe exports,
  and inspection without an installed engine.

- Bounded parsing of the pinned Forge log dialect into `events.jsonl`, retaining
  original physical log line numbers, card names, seats, and object identifiers.
  Grouped attackers, multiple-defender continuation lines, and targeted-cast
  descriptions follow the pinned formatter; non-player attack targets remain partial.
- Per-game `diagnostics.json` with the raw log's SHA-256, parser version,
  coverage, and descriptive land-play, cast, and attack-assignment observations.
- `batch-diagnostics JOB_ID` aggregates covered completed games, maps rotated
  seats back to original deck order, and reports included/excluded game counts.
  Missing diagnostics remain unavailable rather than becoming zero observations.
- Parser limits: existing 8 MiB log cap, 10,000 normalized events, 4,096-character
  candidate lines, 20 malformed-line references, and bounded evidence samples.
  Individual attack lists are capped at 100 creatures.
- Regression coverage for incomplete turns, off-turn events, malformed seats,
  unknown targets, parser limits, physical line references, output failures,
  legacy data, and rotated batch aggregation. The opt-in real-engine smoke test
  now verifies diagnostic artifacts and two-game batch aggregation.
- Validated with the shared Black/isort/Pylint/Bandit/pytest gate and an opt-in
  real single-game run plus a two-game pause/resume batch.

### Changed

- Commander validator version 2 rejects meld results as starting cards or
  commanders, while unverifiable meld roles block approval. Meld fronts remain
  supported subject to the other construction rules. Bracket rechecks record
  current rules and validator versions, so changed frozen batch evidence must
  be recreated. The verified rules reference now links to the pinned PDF.
- The live engine smoke fixture includes an explicit variant alias and checks
  its actual generated export name before validating batch diagnostics.
- Library and lookup JSON output now falls back to lossless escapes on legacy
  terminal encodings, preventing valid Unicode card names from breaking pages.

- New engine results include diagnostic coverage; failure to write diagnostics
  does not replace the actual game's win/draw/error outcome.
- README documents diagnostic definitions, commands, artifacts, and limitations.
  CONTRIBUTING requires keeping README and CHANGELOG current with project changes.

### Scope and remaining work

- A turn without a logged land play does not prove a missed land drop or mana
  problem. Attack assignments do not establish threat assessment or human motives.
- Color access, uncastable cards, targeted removal, subjective hate/unfun labels,
  and causal optimization recommendations are not implemented by these metrics.
- Existing results are retained; no historical game results are rewritten.
- Self-hosted web UI, isolated workers, hard host quotas, automatic crash recovery,
  automatic benchmark acquisition, and comprehensive card/AI validation remain pending.

## Earlier milestones

- `9e2d757`: Persistent fixed-pod batches with a backend cap of 100,000 attempts,
  frozen inputs, deterministic seeds/seat rotation, resumable foreground chunks,
  cooperative cancellation, worker ownership, and separate outcome/failure counts.
- `07e76f7`: Pinned Forge single-game adapter with legality/bracket gates,
  script-coverage checks, bounded shell-free execution, minimal child environment,
  profile locking/restoration, preserved logs, and security regression tests.
- `2d444c8`: Pinned pytest discovery and execution in the shared local, commit-hook,
  and CI quality gate alongside Black, isort, Pylint, and Bandit.
- `3aa31c5`: Immutable, attributed source-sample opponent collections with readiness
  checks. Automatic acquisition of the requested 50 real representatives remains pending.
- `f61dc81`: Game Changer screening, auditable human bracket reviews, and current
  catalog/policy evidence checks for immutable deck revisions.
- Earlier foundation: local Scryfall catalog, bounded deck parsing, Commander
  construction validation, immutable deck history, and backend-first requirements.
