# Changelog

Changes to user-visible behavior, backend contracts, and engineering safeguards
are recorded here. Work remains unreleased until an explicit release is made.
Historical entries below summarize verified Git milestones, not release versions.

## Unreleased

- Register the seven previously passing B'Elanna lifecycle controls without
  changing their tested source bytes. Include them in fresh-source/native builds
  and a fixed seven-case packaged partition. The expanded registry expects 4,970
  native and 3,813 main packaged cases; retain the original 1,271/90 unchanged.
  Fresh build/package validation is pending. Card definitions, production Java
  behavior, selected runtime and known-issue build approvals are unchanged.
  The full Python quality gate passes 504 tests and 865 subtests, with 45 opt-in skips.

- Verify actual legal-pod admission in both directions: the selected older engine
  rejects the confirmed issue before launch; the repaired candidate completes the
  same B'Elanna/basic-land pod at seed 42 with a 60-second clock. Preserve the
  matching saved review receipt, exact decks, native output and selected-engine
  rejection in engine/rules_admission_fullgame_evidence.json. Catalog, selected
  configuration and frozen source remain unchanged. This is a launch/receipt
  witness, not an Abundance full-game test, broad card certification or activation.

- Reject confirmed rules errors separately from script presence. Add a bounded
  identity-based policy requiring exact reviewed JAR/card-archive pairs for
  B'Elanna Torres; version labels, capability claims and names cannot approve it.
- Check known issues after legality and before pod storage or game export, freeze
  applicable policy/review hashes, and recheck immediately before launch. Changed
  or revoked reviews stop execution; unaffected frozen evidence stays unchanged.
  Reverify actual engine fingerprints under the engine profile lock before launch
  and reject an engine that changed after admission.
- Pass seven new native lifecycle controls alongside all ten retained Oracle
  cases: paid ownership/control changes, copied activations with/without Abundance,
  and later activation after a draw. Preserve source, exact membership and reports
  in engine/belanna_lifecycle_evidence.json. New controls await global registration;
  selected runtime and the original 1,271/90 gate remain unchanged.
- Add 14 offline policy/admission tests. The full quality gate passes 504 tests
  and 864 subtests with 45 opt-in skips. Verify the actual selected engine rejects
  the confirmed issue and the reviewed candidate carries a matching receipt.

- Build the B'Elanna Torres repair from fresh patched source: all 4,963 native
  invocations pass, retaining exactly all prior 4,953 method/parameter identities
  plus ten new cases. All 18,068 packaged classfiles remain byte-identical and
  only B'Elanna's script changes among 34,466 card-resource members.
- Pass the ten registered cases through the normal packaged worker, verify 188
  production origins and restore configuration exactly. Preserve complete native
  reports, package receipts and audits in engine/belanna_runtime_evidence.json.
  Full 3,806 packaged, original 1,271/90, broader games and activation stay open.

- Reproduce a B'Elanna Torres rules error: Abundance puts the revealed artifact
  into hand without drawing, but the original definition awards zero counters.
  Preserve the 9/10 baseline and a rejected memory-clearing proposal that fails
  the doubled-draw control. The final supplied-definition proposal passes 10/10
  identical scenarios, including public-top reveal choices and draw replacements.
- Repair the card definition using existing StoreSVar/Count mechanics to compare
  the activating player's actual draw count before and after the conditional draw.
  Update display wording and register ten regressions for a fresh build: 4,963
  native and 3,806 main packaged cases. Retain original 1,271/90 unchanged.
  Preserve raw sources/results in engine/belanna_oracle_evidence.json. Fresh build,
  packaged validation and activation remain pending; selected runtime is unchanged.

- Bound catalog metadata, downloads, decompression, record size, nesting, record
  count and cooperative operation duration. Stream JSON arrays and JSON Lines
  without rewinding decompression; keep shared lexical depth state across chunks.
- Download through private temporary files with bounded transfer validation and
  atomic cache replacement. Reject imports inside a caller transaction; roll back
  catalog updates and freshness receipts on malformed or excessive input.
- Add 26 offline tests for exact limits, overflow, malformed/truncated input,
  transaction preservation, rollback, approved endpoints and shared deadlines.
  The full quality gate passes 490 tests and 845 subtests, with 45 opt-in skips.
- Refresh the local Oracle export to 39,412 records and audit both runtimes:
  32,636 Commander-legal starting identities match scripts; seven separately
  classified meld results lack standalone scripts. Two Oracle text changes were
  reviewed against scripts and existing controls: Hit It! has retained reveal/
  zone-movement evidence; B'Elanna Torres needs focused sequence validation.
  Matching definitions does not establish rules correctness.
  Preserve refresh scope and receipt identifiers in docs/CATALOG_INGESTION.md.

- Preserve two independent unpromoted query guards. Native Self-first restriction
  controls pass 24 cases per variant and 297 retained cases. The default-zone
  query passes 21 per variant, 237 static/trait/filter and 297 retained cases;
  its observation confirms one irrelevant condition read becomes zero.
- Both unchanged seed-42 identity tests still fail the 60-second completion
  assertion. Preserve exact sources, controls, class-origin maps, failed games
  and inputs in engine/query_guard_evidence.json. Do not claim a speedup, change
  search/clock limits, activate either proposal or retry original 1,271/90.
- Condense README coverage status while retaining detailed historical evidence
  in the coverage document and changelog. Full card/rules coverage remains open.

- Validate three further complete workflow games, variant-name export, and
  two-game batch pause/resume with unchanged first-attempt bytes, frozen inputs,
  seat rotation and winner mapping. Preserve the failing 60-second identity test;
  timed-out games remain partial and excluded from completed-game aggregation.
- Profile that failure once with unchanged clocks and preserve its raw reports.
  An isolated exact-property dispatch passes 21 controls on both variants and
  297 retained controls, but the unchanged identity scenario still times out.
  Keep the proposal unregistered/unpromoted and preserve class-origin evidence.
  Catalog, default configuration, native limits and original 1,271/90 stay unchanged.

- Validate three complete legal basic-land Commander games on the 4,953-case
  candidate using unchanged seeds 42, 0 and 4096. Recompute explicit native-log
  witnesses for commander-tax recasts, Goreclaw attack triggers and commander-
  damage loss with positive life; all pass. Preserve exact decks, admission data,
  logs, results and checker evidence. Catalog and selected configuration remain
  byte-identical. Rich-deck gameplay, original 1,271/90 and activation remain open.

- Preserve the unpromoted single-source native-filter experiment and 24 new
  controlled-state regressions. Both variants pass all 63 filter controls; the
  candidate retains 297 semantic controls and all six 85-case trials pass.
  Median test/worker reductions of 8.0%/5.5% miss the predeclared 10% criterion.
  Keep production, fixture registration, selected runtime and original 1,271/90
  gate unchanged; retain all proposal inputs, reports and comparison evidence.

- Profile 85 unchanged native diagnostic cases with bounded JFR allocation/CPU
  sampling; all pass with exact case identities and verified class origins.
  Preserve the initial preflight failure and corrected run. Collection/view
  allocation and repeated color-mask enum-array copies remain investigation
  targets; no optimization, timed-gate pass or runtime activation is claimed.

- Validate the elimination evaluator in all 3,753 fresh-source and 4,953 native
  cases, retaining every prior method/parameter identity. Verify only evaluator
  production classes change and 173 resolution/helper classfiles stay identical.
- Pass 2,525 main packaged cases outside resolution and 405 separate packaged
  cases with exact native membership and restored configuration. Pass fresh
  broader suites of 6,276 gameplay and 320 serialization/loading cases, retaining
  all prior cases and restoring reviewed test policies. Preserve reports and
  receipts in engine/player_elimination_runtime_evidence.json. Original 1,271/90,
  full 3,796 packaged, full legal games, activation and universal rules stay open.

- Fix multiplayer AI valuation that could decline a legal opponent elimination
  because average surviving-opponent life remained unchanged. Reward native
  registered-opponent losses and penalize allied losses by 200 heuristic points;
  preserve terminal scoring. Focused autonomous/payment controls improve from
  5/11 to 11/11, and 112 unchanged control/perspective/snapshot cases pass.
- Register 21 candidate/payment/autonomous regressions. The new recipe expects
  3,753 source, 4,953 native and 3,796 main packaged cases. Subsequent validation
  is recorded above; the full packaged gate remains open. Preserve raw baselines,
  corrected fixtures and the evaluator overlay
  in engine/elimination_score_evidence.json. Keep selected runtime and original
  1,271-case / 90-second gate unchanged. Full rules coverage remains open.

- Preserve Java parameter text in native test receipts: Boolean and floating
  special values no longer use Python bridge formatting, and null arguments
  use the explicit native null marker. Keep literal text and nonpassing results
  intact. Four regression tests fail on the prior collector and pass after the
  fix. Verify identical native invocations before/after: 15 formatting cases now
  match TestNG, fixing six wrong identities; 10 ignore-effect candidate/payment
  controls retain all cases and fix two Boolean receipt identities. Revalidate
  14 regular packaged phase cases with 188 production origins and exact config
  restoration. Record engine/native_parameter_format_evidence.json, including
  the corrected independent enum fixture. Engine behavior remains unchanged;
  full rules coverage and the original 1,271/90 gate remain open.

- Pass all 3,732 fresh-source and 4,932 native cases with exact retained method
  and parameter identities, adding 14 registered phase-replacement controls.
  Pass all 14 through the regular packaged worker with 188 production origins
  verified and byte-identical configuration restoration. Verify unchanged
  production class bytes and 173 protected resolution/helper classfiles.
- Preserve raw reports, inputs and class fingerprints in
  engine/phase_regression_runtime_evidence.json. The rejected shortcut remains
  excluded and the selected runtime unchanged. Original 1,271/90, full 3,775
  packaged, full legal games, activation and universal rules support remain open.
  Prior broad/catalog evidence is historical rather than rerun for this build.

- Register 14 native phase-replacement regressions for future source builds and
  packaged checks. Cover Eon Hub, Solitary Confinement, live traits, copied games,
  unknown callbacks and shield/stun/finality replacements. The phase shortcut
  remains excluded. Expected totals become 4,932 native and 3,775 main packaged
  cases; the original 1,271-case resolution group and 90-second deadline remain
  unchanged. Subsequent source/native and phase-group validation is recorded above;
  the full packaged gate remains open. The runtime stays selected
  at the previously configured build.

- Validate 14 paired native phase replacement controls: Eon Hub, Solitary
  Confinement, live trait changes, repeated game copies and unknown callbacks.
  Retain all 297 existing replacement/keyword/damage cases.
- Reject the phase-only precheck for promotion after all 510 fixed diagnostic
  invocations pass: median runner/worker reductions 0.42% / -0.73%
  miss the predeclared 10% criterion. Preserve the source audit, unused patch and
  every trial in engine/phase_precheck_evidence.json. Runtime, recipe and original
  1,271/90 remain unchanged; full-game and universal rules support remain open.

- Reject a narrowly guarded damage/destroy precheck after its fixed six-run
  diagnostic protocol: all 510 invocations pass, but median runner time rises
  0.94% and worker time falls only 0.38%, below the predeclared 10% criterion.
  Both variants pass 16 native controls; the prototype retains 297 existing cases.
  Preserve the unused patch and fixture/analysis corrections without dropping
  assertions or repeating gameplay for the analysis repair.
- Preserve baseline guard/event counters and existing CPU-recording attribution.
  Damage/destroy cause only 3.03% of collection fetches in the diagnostic selection;
  BeginPhase accounts for about 48%. These counts do not measure CPU or allocation.
  Record engine/damage_precheck_evidence.json; leave runtime, recipe and original
  1,271/90 unchanged. Full-card rules correctness and activation remain open.

- Reject the scalar-lookup no-op prototype for promotion after its fixed six-run
  diagnostic protocol. All 510 cases pass; median runner/worker reductions of
  9.0% / 8.9% miss the predeclared 10% criterion. Both variants also pass all nine
  callback/freeze controls. Preserve every trial and the unused patch under
  engine/lookup_dispatch_evidence.json; leave the original 1,271/90 gate untouched.
- Preserve a bounded CPU/GC recording of the unchanged baseline's 85 diagnostic
  cases and its decoded summary. Sampling indicates static/replacement trait
  work merits investigation. No allocation, retained-memory, reliable production
  speedup or full-card correctness claim; no shipped runtime or recipe change.

- Validate all 3,718 source and 4,918 native cases with exact retained case and
  parameter counters, reviewed source/fixture bytes and unchanged 173 protected
  resolution/helper classfiles. Bind baseline reports to their published bytes.
- Pass 2,490 main packaged cases outside resolution and 405 separate cases, with
  receipt arguments matched to the sealed native report. Pass all 6,241 broader
  gameplay and 320 card/deck-loading cases, restoring temporary test policies.
  Candidate health and frozen-catalog construction pass: 32,636 constructed,
  seven nonstarting meld results explicitly unavailable as standalone scripts.
  Preserve raw reports/receipts in engine/remembered_history_runtime_evidence.json.
  Original 1,271/90, full 3,761 packaged, full games and activation remain open.
- Replace the README's long coverage history with current status and direct links;
  preserve its previous notes under docs/history/README_CARD_COVERAGE.md. Detailed
  coverage evidence and earlier failure records remain available.

- Preserve remembered detached cards and last-known-information objects through
  the shared snapshot identity graph. Seven of nine baseline controls fail; all
  nine corrected controls and 87 retained regressions pass, including actual
  source-versus-copy native life gain, repeated copies and sibling isolation.
  Retain the initial incorrect test expectation and wrapper collision artifacts.
- Register nine remembered-history cases with reviewed patch/fixture hashes.
  Expanded 3,718 source / 4,918 native / 3,761 main packaged validation remains
  pending. Original 1,271/90, full-game checks and activation remain open; selected
  runtime is unchanged. See engine/remembered_history_evidence.json.

- Validate the repaired parameter-aware collector through a real 22-case native
  invocation, all 2,481 non-resolution main packaged cases and 405 separate
  casting/untap/Battle cases. Audit exact native method/argument membership and
  packaged production origins; retain the earlier failed validator evidence.
- Pass all 6,232 broader gameplay and 320 card/deck-loading cases with no skips,
  restoring reviewed temporary test policies and preserving production inputs.
  Candidate health is ready; catalog construction passes for 32,636 identities,
  with seven nonstarting meld results explicitly unavailable as standalone scripts.
  Preserve raw reports, receipts and catalog rows in
  engine/parameter_runtime_evidence.json. These finite checks do not establish
  universal card correctness. Original 1,271/90, full 3,752 packaged, full games
  and candidate activation remain open; the selected runtime stays unchanged.

- Verify all 3,709 source and 4,909 native cases with exact retained methods and
  parameters, matching 2,168 production / 180 selected-test / 350 card inputs,
  291 registered plus nine priority fixtures, and unchanged 173 protected
  resolution/helper classfiles. Preserve the pre-JVM UTF-8 comment mismatch and
  repair; all three prototype classfiles remain byte-identical.
- Preserve the failed packaged integration gate separately from successful native
  executions: 912 cases pass their complete audit; another 1,569 native cases
  pass, but the validator incorrectly counts 14 method names as 22 cases.
- Retain native result parameters and validate unique method-and-argument pairs,
  rejecting exact duplicate cases and malformed parameter lists. Update all four
  packaged integration validators and derive the main total from the exhaustive
  registry. Add parameterized pass/fail/skip, duplicate and legacy-row regressions.
  Corrected native integration, broader/live checks and activation remain pending;
  original 1,271/90 and selected runtime stay unchanged. See
  engine/ignore_effect_runtime_evidence.json.

- Validate the copier-lifecycle recipe with all 3,687 source and 4,887 native cases,
  exact prior methods/parameters and reviewed input bytes. Verify 289 registered
  plus nine priority fixtures and unchanged 173 resolution/helper classfiles;
  production differences are confined to the GameCopier family. Keep independent
  packaged/live gates open and runtime unselected.
- Map ignore-effect special actions and shared cleanup callbacks into each copied
  world. Correct nine of 22 baseline failures across Leonin Arbiter, Damping
  Engine, Volrath's Curse and Lost in Thought. All 22 controls pass with native
  mana, sacrifice/exile costs, expiry, unaffordable payment and branch isolation;
  all 13 retained interaction groups pass. Preserve the repaired fixture's missing
  static-restriction predicate and initial callback return-type compile failure.
- Register 22 ignore-effect cases and reviewed patch/fixture hashes, keeping every
  prior case and original 1,271/90. Fresh 3,709 source / 4,909 native / 3,752 main
  packaged validation remains pending; selected runtime is unchanged. No complete
  legal-history, default-AI or full rules claim. See engine/ignore_effect_evidence.json
  and engine/copier_release_runtime_evidence.json.

- Release retained source ability/combat-band graphs from completed game copiers,
  stop mapping through released contexts, and retain immutable unsupported-callback
  diagnostics. Four of eight baseline controls fail; all eight corrected controls
  pass, including native paid mana activation after release, repeated copies and
  idempotent cleanup. All 57 payload controls and 13 retained semantic groups pass.
- Register eight copier lifecycle cases with fixed patch/fixture checksums and
  reviewed builder admission. Preserve every earlier case and original 1,271/90.
  Fresh 3,687 source / 4,887 native / 3,730 packaged checks remain pending; selected
  runtime stays unchanged. Preserve CPU diagnostics, failed preflight checks and
  paired evidence in engine/copier_release_evidence.json. No speedup, collectability
  or comprehensive card/rules correctness claim.

- Validate the unselected payload-graph candidate: 4,879 native, 2,451 main
  non-resolution packaged, 405 other packaged, 6,202 broader gameplay and 320
  card/deck loading cases pass with retained methods/parameters. Health is ready;
  catalog construction reports 32,636 successes and seven non-starting meld
  results. Preserve raw reports and input/class audits; no complete rules claim.
- Preserve the isolated empty-event-map experiment: 57 payload controls, 16
  supplier controls, 13 semantic groups and all 3,679 source cases pass. Fixed
  probes save about 1,200/600 bytes per snapshot with two/one empty maps; no
  reliable speedup claim. Preserve the incomplete 3,677-case source attempt and
  restore two omitted original method selectors without altering code/assertions.
  The unchanged original 1,271/90 resolution workload times out at 90.015s without
  a completed report. Keep the optimization unpromoted and runtime unchanged.
  See engine/payload_graph_runtime_evidence.json for bound artifacts.

- Verify all 3,679 source cases with exact retained methods/parameters, no skips
  or configuration failures, and frozen production/test/card inputs.
- Add the reviewed payload patch to the native builder's fixed path allowlist.
  Preserve the pre-JVM rejection and add unit tests for current-recipe admission,
  unknown paths rejected before file access, and changed reviewed hashes. Keep
  every existing allowlist and content-hash check; fresh native validation remains
  pending.

- Remove two unused Java imports caught by the expanded source build and refresh
  its registered fixture checksum. Preserve the failed Checkstyle log and exact
  import-only repair; no desktop source cases ran in the failed attempt. The
  corrected 3,679-case source suite subsequently passes. All test names/assertions
  and production code stay unchanged.

- Preserve shared mutable event-payload references and supported container cycles
  in AI snapshots; retain independent field-owned containers and original/sibling
  worlds. Remove newly memoized payload containers after copy failures without
  claiming full native snapshot transactions.
- Correct 22/26 baseline graph failures and 4/5 native prevention-event failures;
  all 57 self-contained registered cases, retained 16 supplier controls, 13 semantic
  groups and a fresh source build's 320 card/deck loading cases pass. Preserve raw
  failures and unchanged assertions; supplied events are not full paid histories.
- Register 57 `payload_graph` cases and their fixed checksums, keeping every prior
  group and original 1,271/90. Source 3,679 passes; fresh 4,879 native / 3,722 main
  packaged validation, final live checks, activation and complete card/rules coverage remain
  pending. Original 1,271 / 90 seconds still times out at 90.016s without a
  completed report.

- Preserve an unpromoted ability-snapshot supplier optimization and sixteen
  baseline/prototype contracts for evaluation order, failures, mutable containers,
  card mapping and native rollback. Fixed probes save about 120 bytes per snapshot
  in three workloads; no reliable elapsed or retained-memory claim is established.
- Validate its seventeen-source combination with 78 focused checks, all 13 retained
  interaction groups and a fresh source build's 320 card/deck loading cases, with
  exact methods/parameters and no skips. Original 1,271/90 still times out at 90.016s
  without a completed report; selected runtime remains unchanged. Preserve the
  failed pre-JVM Git lookup alongside the corrected trial. These 320 cases cover
  loading/parsing, mocks and generation, not ability serialization or full rules.

- Preserve an unpromoted one-source EnumSet allocation hint and seventeen
  native query controls for both baseline and prototype. Fixed escaping-query
  measurements improve 8/80-card allocation, increase the empty sample, and stay
  close at 400 cards; general memory, speed, rules correctness and activation
  remain unverified.

- Verify fresh 3,622 source and 4,822 native cases with exact retained methods
  and parameters, plus all 294 reviewed fixture bytes. Only Player/GameCopier
  production classes change from the previous candidate.
- Audit all 2,394 non-resolution main and 405 separate packaged cases. Broader
  6,145 gameplay and 320 card/deck loading cases pass without skips, with exact
  membership and source policies restored. Full 3,665 main validation remains
  open at original 1,271/90; final live checks, activation and complete card/rules
  coverage remain unfinished. Preserve raw receipts and failed wrapper preflight.

- Preserve per-player sacrificed-card history in AI game copies through the
  existing LKI mapper, without replaying events or sharing original history cards.
  Sixteen baseline failures lose records or Dargo's prior-sacrifice discounts;
  all twenty-six native-payment, ownership, isolation, cleanup and negative movement
  controls pass after correction, plus retained semantic regressions.
- Register twenty-six sacrifice_history cases, retaining every earlier case and the
  original 1,271/90 workload. Fresh 3,622 source / 4,822 native / 3,665 main
  packaged validation and runtime activation remain pending. Supplied boards
  do not establish full preceding legal histories or comprehensive rules support.

- Preserve per-player discarded-card history in AI copies through the existing
  LKI mapper, without replaying discard events or sharing original history cards.
  Twelve baseline failures lose discard records or Hollow One discounts; all
  eighteen paid cycling/casting, provenance, isolation, cleanup and negative
  movement controls pass with the correction, plus retained semantic regressions.
- Register eighteen discard_history cases separately; retain every earlier group
  and the original 1,271/90 workload. Fresh 3,596 source / 4,796 native / 3,639 main
  packaged validation is pending. Supplied boards are not full legal histories,
  turn-scheduler or experimental restore proof; selected runtime is unchanged.

- Preserve per-player foretell counts without replaying special actions or
  triggers. Six baseline failures wrongly restore first-foretell discounts or
  lose action history; all twelve native-payment/exile/trigger/permission controls
  pass after correction, plus all 1,926 retained semantic-family cases.
- Register twelve foretell_history cases separately; retain every earlier group
  and original 1,271/90 workload. Fresh 3,578 source / 4,778 native cases pass with
  exact retained methods/parameters and all 292 copied-fixture bytes verified.
  Broader 6,101 gameplay / 320 serialization cases pass with exact membership,
  no skips and policies restored. Supplied boards are not full legal histories,
  turn-scheduler or experimental restore proof.
- Audit all 2,350 non-resolution main and 405 separate packaged cases on that
  candidate. Full 3,621 remains incomplete: original resolution times out at
  90.032 seconds without a report. Final live checks and activation remain
  pending; selected runtime is unchanged.

- Preserve an unpromoted live-membership keyword-static cache: fourteen new and
  fifteen prior boundary contracts pass in baseline/prototype runs, plus 495
  existing trait/collection and all 1,926 retained semantic-family cases. Mutable
  APIs and opaque callback order are preserved; counts are workload evidence.
- Diagnose keyword guards in three unchanged heavy methods, then observe 763,977
  baseline versus 428 post-cache rebuilds. Original 1,271/90 still times out without
  a report; no reliable speedup, full gate, retained-memory or full rules claim.

- Preserve game-long spell counts after the existing turn-local snapshot copy.
  Five baseline failures incorrectly grant a free first-spell alternative or lose
  prior casts. All twelve paid-spell, turn-boundary, later-cast and isolation
  controls pass after correction; all retained semantic regression groups pass.
- Register twelve spell_game_history cases separately; preserve all earlier groups
  and original 1,271/90 workload. Fresh 3,566 source / 4,766 native cases pass with
  exact retained methods/parameters, reviewed manifests and all 291 copied-fixture
  bytes verified. Broader 6,089 gameplay / 320 serialization cases pass with exact
  membership, no skips and policies restored. Supplied boards are not full legal
  histories, turn scheduling or experimental restore proof.
- Audit all 2,338 non-resolution main and 405 separate packaged cases on that
  candidate. Full 3,609 remains incomplete: original resolution times out at
  90.015 seconds without a report. Final live checks and activation remain
  pending; selected runtime is unchanged.

- Preserve an unpromoted live static-category experiment: fifteen baseline and
  corrected mutation/callback contracts pass, including side-effect ordering; all
  1,914 retained semantic cases pass. The single original 1,271/90 trial times out without a completed report.
  Keep the proposal uninstalled pending full validation; no full rules or reliable
  speedup claim. Preserve the earlier generic-type compile failure and its correction.

- Preserve per-player scry counts through the shared event-history helper. Three
  baseline failures lose paid-scry qualification or another player's count. All six
  controls pass after correction, including paid return, finality and enters-surveil
  effects. Retain casting/combat/untap/dungeon/expend/event-history/upstream controls.
- Register six scry_history cases separately; preserve all prior groups and original
  1,271/90 workload. Fresh 3,554 source / 4,754 native cases pass with exact retained
  methods/parameters, reviewed manifests and all 290 copied-fixture bytes verified.
  Broader 6,077 gameplay / 320 serialization cases pass with exact membership, no
  skips and temporary policies restored. Supplied boards are not full legal histories
  or experimental restore proof.
- Audit all non-resolution packaged groups on that candidate, including six scry
  cases and separate free-casting/untap/Battle families. Full 3,597 remains incomplete:
  original resolution times out at 90.016 seconds without a completed report.
  Final live checks and activation remain pending; selected runtime is unchanged.

- Copy native regression fixtures from the shared hash registry instead of a
  second filename list. Preserve all 280 registered sources and nine priority
  fixtures at their prior Java destinations. An offline check verifies declared
  packages, exact file bytes and complete membership; no Pylint limit is raised.

- Preserve life-gain, surveil and investigate event counts in ordinary AI world
  copies through a shared Player helper. Six baseline failures repeat Cleric
  counters, Whispering Snitch drains or Erdwal Illuminator investigations. The
  correction passes twelve native-event/paid-follow-up controls and retains 1,404
  casting / 273 combat / 194 untap / ten dungeon / six expend / nine upstream cases.
  Supplied boards do not establish full histories or experimental restore support.
- Register twelve first_events cases separately; retain all earlier groups/counts
  and original 1,271-case / 90-second workload. Fresh 3,548 source / 4,748 native
  cases pass with exact retained methods/parameters, reviewed input manifests and
  all 289 copied-fixture bytes verified. Broader 6,071 gameplay / 320 serialization
  cases pass with exact membership, no skips and temporary policies restored.
- Audit every non-resolution packaged group on that candidate, including twelve
  history cases. Full 3,591 validation remains incomplete: original resolution
  times out at 90.015 seconds without a completed report. Final live checks and
  activation remain pending; selected runtime stays unchanged.
- Preserve neutral standalone mask-removal and positive compact-version allocation
  measurements: 800,000 bytes saved per 100,000 fixed escaping objects in each mode.
  Combined semantic controls pass, but original 1,271/90 still times out without a
  report. Keep both proposals unpromoted. Late-window sampling again points toward
  trait gathering; sampled allocation volume is not retained memory or a speedup.

- Preserve cumulative expend history in shared AI world copies without firing
  triggers again. Four baseline failures include missed fourth-mana life gain and
  repeated fourth-mana gain in place of the eighth-mana exile effect. The correction
  passes six paid-spell/isolation controls and retains 1,404 casting / 273 combat /
  194 untap / ten dungeon / nine upstream checks. Supplied boards are not full histories.
- Register the six-case expended_mana family separately, preserving all old groups,
  counts and original 1,271/90 workload. Fresh 3,536 source / 4,736 native cases now
  pass with exact retained methods/parameters and reviewed 2,168 production / 168
  test / 332 card inputs; class changes stay within GameCopier and nested classes.
- Preserve the failed full 3,579-case packaged attempt: original resolution times
  out at 90.000 seconds without a completed report. The preceding 301 information /
  273 combat / 338 text-cost cases pass receipt audits. Remaining packaged groups,
  broader/live checks and activation remain pending; selected runtime is unchanged.
- Preserve one bounded CPU/allocation profile: setup takes about 1.6 seconds and
  execution remains inside TestNG until timeout. Samples point toward trait-list
  and collection work; recorded GC pauses total about 0.42 seconds. These are
  diagnostics, not normal timing or complete rules proof. No workload/collector
  change is promoted, and allocation weights are not retained-memory measurements.

- Correct generic dungeon snapshots: retain noncopiable game-piece kind and room
  markers, mapped completed-dungeon records and venture counters without replaying
  effects. Preserve the 1/10 baseline and 10/10 corrected controls, including real
  Dungeon Map costs, first entry, final-room draw/completion, Nadaar bonus, repeated
  worlds, multiple players and isolated history. Retain 1,404 casting / 273 combat /
  194 untap / nine upstream checks. Supplied mid-room states are not full histories.
- Register ten new dungeon cases separately; retain every earlier group/count and
  original 1,271-case / 90-second gate. Expand expected source/native/main packaged
  totals to 3,530/4,730/3,573 and required production origins to 180. Fresh expanded
  source/native validation now passes; full packaged validation and activation remain
  incomplete, as recorded below. The selected runtime stays unchanged.
- Validate fresh 3,530 source / 4,730 native cases with exact retained methods and
  parameters, all 2,168 production / 167 test / 324 card inputs, and changed class
  bytes confined to the four dungeon correction families. Broader 6,053 gameplay /
  320 serialization cases pass, with reviewed test-only policies restored.
- Audit packaged 301 information / 273 combat / 338 text-cost / 1,380 casting /
  ten dungeon cases and separate 65 free-casting / 194 untap / 146 Battle cases.
  The full 3,573-case gate remains incomplete: original 1,271-case resolution times
  out at 90.016 seconds at its unchanged 90-second limit without a completed report. Preserve
  this failure; no unchanged retry or activation. Fresh final live checks and full
  paid-history/card-rules coverage remain required.
- Preserve a current corrected-baseline Parallel GC diagnostic and prior failed
  trials. Semantic controls pass, but original resolution still times out90.016s
  without a completed report. No collector setting or allocation patch is promoted;
  individual timings/JMX counters do not establish a reliable speedup or full rules.

- Validate the unchanged corrected candidate against the current frozen catalog:
  32,636 identities construct and seven non-starting meld results remain unavailable;
  no actionable starting-card construction/availability gap. Health passes. Fixed
  full-game seeds 42/0/4096 pass commander-tax, Goreclaw-trigger and commander-damage
  loss witnesses, with raw logs and frozen inputs revalidated. These checks do not
  establish every card's ability behavior or complete rules coverage.
- Preserve the unpromoted 14-source allocation combination: paired 1,404 casting
  controls and 273 combat / 194 untap / nine upstream checks pass, but original
  1,271-case / 90-second resolution still times out without a complete report.
  Keep the workload/budget and failed receipts. Static Intensify API triage matches
  21 non-Commander-legal catalog cards, so no Commander blocker is inferred.
  Correct an inherited health annotation without rewriting original evidence:
  java -version makes no heap/processor measurement claim. Selected runtime,
  production patches and all remaining activation/full-rules gates stay unchanged.

- Correct generic pre-target cost forecasting, restoring replicate choices and
  Abbot follow-up plans while retaining additional costs, known extra-mana budgets
  and completed-target native trigger handling. Preserve selected 9/9, earlier
  candidate 4/9 and corrected 9/9 upstream comparisons, diagnostic failures and a
  corrected card/mana fixture setup error. Add nine paid ward/tax/sacrifice cases
  across copied board states; all nine fail on baseline and pass with the fix.
- Fresh source/native suites pass 3,520/4,720 cases with every earlier method and
  parameter retained. Verify 2,168 production, 166 test and 318 card inputs; only
  the casting-cost production class changes. Broader suites pass 6,043 gameplay /
  320 serialization cases with exact membership/no skips and restored test policies.
  Preserve a pre-Java newline-only source preflight failure and its verified repair.
- Validate packaged groups: 1,380 cast, 301 information, 273 combat, 338 text/cost,
  65 free-casting, 194 untap and 146 Battle cases with exact native case/parameter
  and origin audits. The full 3,563-case attempt still fails at the original
  1,271-case / 90-second resolution gate (90.015s); no completed report or unchanged
  retry for luck. Preserve the workload/budget and failure receipts. Selected
  runtime stays unchanged; full-game, catalog, health, activation and complete
  card/rules coverage remain open. Supplied board states do not prove complete
  preceding legal histories.

- Add 43 regular native controls for live split/clone trait visibility and table
  isolation, version tokens, serialization and concurrent use. The complete
  3,511-source / 4,711-native suites pass with all earlier methods and parameters
  retained. Verify all 2,168 production, 165 test and 316 card inputs; production
  class bytes match the preceding candidate. The expanded 1,371-case packaged
  group passes with 179 production origins and prior compiled assertions retained.
- Preserve unpromoted guard/allocation experiments, paired diagnostics and a
  corrected post-test catalog diagnostic. Original 1,271-case / 90-second runs
  still time out: one finishes every assertion, while an instrumented run remains
  inside TestNG. Do not infer completed timing or whole-game speedup from bounded
  allocation measurements. The full 3,554-case packaged gate remains incomplete;
  no unchanged resolution retry was made on identical production code. Broader,
  full-game, catalog, health and activation gates stay open. Production patches
  and selected runtime remain unchanged; complete card/rules support is unproven.

- Correct generic replacement eligibility: performance mode now respects explicit
  active zones instead of dropping tap, untap and mana replacements outside the
  battlefield/command zone. Preserve the failing 36/72 baseline, corrected 72/72
  controls and retained 685 interaction / 194 untap checks. Fresh source/native
  builds pass all 3,468/4,668 cases with earlier cases and parameters retained.
  Verify 2,168 production, 163 test and 316 card inputs; incremental production
  changes are confined to the reviewed replacement handler.
- Validate normal packaged groups: 1,328 cast/trait, 301 information, 273 combat,
  338 text/cost, 65 free-casting, 194 untap and 146 Battle cases. The full
  3,511-case attempt still times out at the original 1,271-case / 90-second
  resolution gate (90.031s). Preserve the failed launcher preflight and count
  correction before any test ran. Selected runtime remains unchanged; remaining
  full-game, catalog, health and activation gates stay open. Controlled definitions
  and these regression suites do not establish complete card/rules coverage.

- Preserve an unpromoted native snapshot-construction experiment: eligible
  runtime types start with original-state properties before the existing mapped
  trait graph installation. All 685 retained contracts and 194 untap cases pass,
  including printing/random-state comparisons, but the unchanged 1,271-case /
  90-second resolution workload still times out. Add bounded CPU/allocation
  diagnostics with explicit limits; partial counts do not prove completion or a
  speedup. Production patches, selected runtime and remaining full-rules gates
  remain unchanged.

- Validate the corrected sealed 4,596-case native build and normal packaged
  1,256 cast / 273 combat groups with 179 production origins. Retain every prior
  native case/parameter, verify 2,168 production sources, 162 test files and 316
  card files, and confirm production class bytes match the prior candidate.
  Preserve an owned edition lookup experiment: paired baseline/prototype checks
  pass 685 contracts with 661 prior cases and 648 printing vectors retained; the
  changed overlay passes 194 untap cases. Its original 1,271-case / 90-second
  resolution check still times out, so it remains unpromoted. Preserve the helper
  interface compile failure and correction without changing assertion bodies.
  The full packaged and remaining runtime/rules gates stay open; update evidence.

- Correct the native sealing count from 4,563 to 4,596 after all 4,596 tests
  passed but the stale expected count stopped packaging. Add a cross-registry
  unit guard, verified failing before the correction and passing afterward;
  retain the strict native validator and failed attempt. All 3,396 source cases
  pass with prior methods/parameters retained. Audit 2,168 production sources,
  162 test files and 316 card files; production class bytes match the preceding
  candidate. A fresh corrected sealed build and remaining packaged/runtime gates
  are still required. The selected runtime remains unchanged.

- Register 33 native attack controls for unavailable creatures, haste, goad,
  mandatory and group attacks, and protected defenders across copy depths 0/1/2.
  Paired baseline/prototype runs pass 661 cases, retaining 628 prior contracts and
  all 648 printing comparisons; the changed overlay also passes 194 untap cases.
  Its empty-declaration shortcut still times out on the unchanged 1,271-case /
  90-second resolution workload and remains unpromoted. Pin the fixture and add
  it to source/native/packaged registries, retaining all prior assertions. Expanded
  3,396 / 4,596 / 3,439 full validation remains pending, with 273 combat cases and
  179 required production origins. Preserve evidence and the selected runtime.

- Validate six pending packaged families: 301 information, 240 combat/snapshot,
  338 casting-cost, 65 free-casting, 194 untap and 146 Battle cases pass. Native
  methods/parameters, packaged origins and configuration restoration are verified.
  Preserve a bounded replacement-hot-path profile and an event-specific implicit
  precheck experiment: paired baseline/prototype suites pass 616 and 194 cases,
  but resolution still times out at the original 1,271-case / 90-second limit.
  The optimization remains unpromoted; the full packaged gate, broader/live checks
  and comprehensive rules correctness remain incomplete. Update runtime evidence.

- Validate the expanded printing/membership suite: 3,363 source and 4,563 native
  cases pass with prior cases and parameters retained; 2,168 production sources,
  161 test files and 316 card files match reviewed inputs. Production class bytes
  match the prior candidate. The normal packaged cast worker passes 1,256 cases
  in 45.547 seconds with 178 verified production origins and matching native
  methods/parameters. Preserve the failed Checkstyle attempt; the full packaged
  gate, resolution timeout and remaining runtime/rules validation stay open.
  The selected runtime is unchanged.

- Correct one unused import in the printing regression fixture after the full
  source build stopped at Checkstyle before executing native cases. Preserve the
  failed attempt, align canonical fixture bytes and update the recipe digest;
  test bodies, all 30 named cases and all 648 reference vectors are unchanged.
  Full expanded source/native/packaged validation remains pending.

- Register 81 native printing/membership contracts and a pinned 648-vector data
  fixture. Paired baseline/corrected-prototype checks pass all 616 focused cases
  with identical printing identities and scoped random state. Preserve three
  retained-redundancy-view failures in the earlier membership experiment and its
  corrected native fallback. Preserve bounded combined CPU/allocation profiling
  and distinct original-workload timeouts (90.016 diagnostic / 90.016 printing /
  90.015 membership seconds). No trait-absence cache, speedup or complete rules
  claim; experiments remain unpromoted. Full source3363/native4563/packaged3406
  validation is pending, with 178 registered production origins. README and
  coverage notes distinguish metadata/live-trait contracts from card histories.

- Validate the expanded contracts with all 3,282 source and 4,482 native cases
  passing; retain prior cases/parameters and match 2,168 production sources, 158
  test files and 316 card files. Production class bytes match the previous counter
  candidate; actual two-processor / 2 GiB JVM assertions pass. Independently audit
  all 1,175 packaged cast cases, native parameter counters, 177 production origins
  and 399 earlier compiled class bytes (43.984 worker seconds). Preserve a wrapper-
  only 1,177-count typo and verify completed receipts without rerunning the worker.
  Preserve a combined metadata/gather/filter experiment: all 535 focused cases
  pass, but the unchanged 1,271-case / 90-second gate times out at 90.015 seconds.
  Keep it unpromoted and the candidate uninstalled. Hash-check seven historical
  catalog gaps as non-starting meld results; no script/rules approval claim.
  Full packaged/broader/full-game/catalog/health/activation and full coverage remain
  pending. Record exact artifacts in engine/trait_contracts_runtime_evidence.json.

- Register 87 native contracts for live trigger/devotion gathering and generic
  filtering; paired baseline/corrected-prototype checks pass all 535 focused cases.
  Preserve three custom-spliterator callback failures from the unrestricted filter
  experiment, its corrected fallback, and the earlier missing-phase fixture/log-
  limit failure. Preserve bounded CPU/allocation profiling and distinct unchanged-
  workload resolution timeouts (90.016 / 90.031 diagnostic / 90.015 / 90.016s).
  Production prototypes remain unpromoted; source3282/native4482/packaged3325 full
  validation is pending, with 177 registered class origins. No speedup, complete
  rules support or installed-engine regression claim. README and coverage limits
  distinguish native registry/devotion/filter contracts from full card histories.

- Validate the counter-zone correction with all 3,195 source and 4,395 native
  cases passing, preserving prior cases and parameters. Match all 2,168 production
  sources, 156 test files and 316 card files; verify actual two-processor / 2 GiB
  JVM limits. The normal packaged cast subset passes 1,088 cases with matching
  native parameter counters and 175 production origins in 42.891 worker seconds.
  Preserve the fresh packaged 1,271-case / 90-second resolution timeout (90.016s),
  exact inputs/logs and the CRLF/LF preparation correction. Full packaged/broader/
  full-game/catalog/health/activation and complete rules support remain pending;
  restore the original configuration and keep the candidate uninstalled.

- Add a pending generic world-copy correction for counters in every card zone,
  using independent storage without placement events, new timestamps or rebuilding
  counter statics. Paired 448-case checks improve from 430 pass / 18 fail to all
  448 passing. Register 48 additional counter-zone/static-gather/opaque-metadata
  controls in normal workflows (3,195 source / 4,395 native / 3,238 packaged;
  full validation pending). Preserve the original 1,271-case / 90-second resolution
  timeout at 90.016 seconds with the changed correction. Direct-state fixture
  limits and unpromoted metadata/collector experiments remain explicit; the
  installed engine is unchanged. Preserve exact focused inputs, outcomes and logs
  in engine/counter_zone_snapshot_evidence.json and its bound artifacts.

- Validate the registered contracts with all 3,147 source and 4,347 native cases
  passing, retaining earlier cases/parameters and matching all 2,168 production
  sources. Production class bytes match the prior candidate; cards/profiles are
  unchanged. Actual two-processor / 2 GiB JVM assertions pass. The normal packaged
  cast subset passes all 1,040 cases with 175 production origins verified in
  44.016 worker seconds. Preserve an import-alignment audit failure and a wrong-
  engine packaged driver attempt, their corrections, and exact evidence bytes.
  Restore the original configuration and leave the candidate uninstalled. Do not
  retry the unchanged failed 1,271-case / 90-second resolution gate; full packaged,
  broader/full-game/catalog/health/activation and complete rules support remain
  pending. Consolidate the README status while retaining historical evidence.

- Register 96 additional verified native contracts in the normal source/build/
  packaged workflows: 24 keyword, 21 untap and 51 all-event replacement eligibility
  cases. Paired baseline/prototype checks pass all 400 focused cases. Preserve
  invalid fixture attempts separately and document their corrected preconditions;
  do not classify harness failures as engine defects. Full 3,147-source /
  4,347-native / 3,190-packaged runs remain pending. Preserve the unchanged
  1,271-case resolution timeout at 90.016 seconds; keep production patches and
  selected runtime unchanged.

- Add 21 pending native untap controls for live callbacks, intrinsic/keyword/merged
  replacements, and actual stun-counter consumption across copy depths 0/1/2.
  Baseline and generic untap prototype both pass all 349 focused cases, retaining
  all 328 prior controls. Preserve guard-order and untap resolution timeouts
  (90.031 / 90.016 seconds under the unchanged 1,271-case / 90-second gate) and
  the existing profile follow-up. Production recipe/runtime unchanged; no speedup
  or full card/rules support claim.

- Preserve a pending keyword no-op prototype and 24 additional callback/read-ahead
  controls: baseline and prototype both pass all 328 focused cases. The unchanged
  1,271-case resolution workload still times out at 90.016 seconds. Preserve the
  separate bounded CPU profile and exact artifact hashes; make no speedup or
  complete rules-support claim. Production recipe and selected runtime unchanged.

- Validate the opaque-keyword callback correction with all 3,051 source and 4,251
  native cases passing, retaining all prior cases/parameters and all 2,168
  production sources. Actual two-processor / 2 GiB JVM assertions pass. Preserve
  the independent packaged resolution timeout (1,271 expected cases, 90.015
  seconds), six completed groups with 1,284 passing subtests, and the 944-case
  cast group not reached. Restore the original configuration, keep the candidate
  uninstalled, and update coverage evidence/docs. Broader/full-game/catalog/
  health/activation and complete rules support remain pending.

- Honor opaque keyword-collection callbacks even when the underlying collection
  is empty. Six native callback failures become passing, and all 298 prior focused
  controls remain passing (304 total). Bind compiled test bytecode before and
  after the combined runs. Register all six additional cases without changing
  the original 1,271-case / 90-second resolution gate. Full 3,051-source /
  4,251-native / 3,094-packaged validation remains pending; selected runtime unchanged.

- Complete intrinsic-cache source/native validation: 3,045 source and 4,245 native
  cases pass, all prior parameters and 2,168 production sources are retained, and
  actual two-processor / 2 GiB JVM assertions pass. Preserve the independent
  packaged resolution timeout (90.016 seconds), 1,284 passing subtests in six
  other groups, and the 938-case cast group not reached. Restore configuration
  and keep the candidate uninstalled. Clarify that the four serialization checks
  cover table snapshots; broader/full-game/catalog/health/activation remain pending.

- Simplify guarded intrinsic ability caches by removing 66 lines of redundant
  source/version bookkeeping and repeated split-state checks. Preserve live
  mutable-source guards, unknown-table fallback, list stamps, native type/counter
  flags and independent returned collections. Baseline/prototype both pass all
  298 focused controls and retain identical table-snapshot serialization bytes. The
  original 1,271-case overlay still times out at 90 seconds; preserve that result
  without claiming a runtime improvement. Full source/native/packaged and later
  validation remain pending for the new recipe; selected runtime unchanged.

- Validate the live-trait correction with 3,045 passing source and 4,245 passing
  native cases, retaining all prior cases/parameters and verifying two actual JVM
  processors and a 2 GiB maximum heap. Audit all 2,168 production sources and
  preserve the failed independent packaged gate: original resolution (1,271
  cases) times out at 90.031 seconds; six groups pass 1,284 subtests and the
  938-case cast-event group is not reached. Restore the original configuration,
  keep the candidate uninstalled, preserve a separate bounded sampling profile,
  and consolidate README coverage status while retaining historical evidence.
  Broader/full-game/catalog/health/activation and full rules coverage remain open.

- Remove eight unused imports from the new live-trait fixture after the full
  source attempt stopped at Checkstyle before executing tests. Keep every test
  body and production change unchanged, update the pinned fixture hash and
  preserve the failed build log. Full source/native/runtime validation pending.

- Correct static/replacement cache reuse for mutable keyword and trait entries,
  changing removal predicates and custom callbacks. Preserve 30 failing baseline
  regressions and their corrected passes across original and copied games, plus
  all 232 existing focused controls (262 pass). Register all 30 additional cases
  in native and packaged suites without changing the original 1,271-case
  resolution group or its 90-second limit. Full source/native/packaged and later
  validation remain pending; selected runtime unchanged.

- Preserve the bounded 3,015-source / 4,215-native pass with actual two JVM
  processors and a 2 GiB maximum Java heap, and full 2,168-source
  parity audit, retaining all prior native cases. Record the failed independent
  packaged gate: resolution (1,271 expected cases) times out at its unchanged
  90-second limit; six completed groups pass 1,284 subtests. The 908-case
  cast-event group is not reached. Restore the original configuration and keep
  the candidate uninstalled. Broader/full-game/catalog/activation remain pending;
  preserve raw results and update README and hashed evidence.

- Correct the Maven launcher to explicitly use two JVM processors. Inspection of
  the fresh 4,214-case functional build showed 32 active processors, so prior
  Maven source/native passes do not prove the advertised two-processor scope.
  Preserve the functional pass and parameter-aware parity audit, mark that
  build ineligible for bounded validation, and retain raw historical evidence.
  Add a native resource assertion: it fails at the default 32 and passes at 2
  with the same 2 GiB maximum Java heap. Register 4,215 native / 3,058 packaged
  targets. Bounded source/build revalidation and the unresolved resolution gate
  remain required; the selected runtime is unchanged.

- Fix writable inner maps escaping read-only snapshot row/column views; 48
  mutation cases fail before correction and all 56 controls pass afterward.
  Cache immutable table cell counts and preserve serialization bytes. Add
  64 Saga predicate equivalence cases and nine callback/implicit-rule controls,
  retaining all previous cases: 231 focused and 3,014 source checks pass.
  Add conservative live trait prechecks with subclass safeguards and retain
  the Vengeance cache flag. Register 4,214 native / 3,057 packaged targets and
  175 origin checks. Preserve timed-out resolution diagnostics and corrected
  fixture/audit failures. Resolution remains over its unchanged 90-second limit;
  fresh runtime gates remain required and the selected engine stays unchanged.

- Preserve the successful fresh 4,085-case native build and full 2,168-source
  parity audit, retaining all prior native cases. Record the failed independent
  packaged gate: resolution (1,271 expected cases) times out at its unchanged
  90-second limit; six completed groups pass 1,284 subtests. The 778-case
  cast-event group is not reached. Restore the original configuration and keep
  the candidate uninstalled. Broader/full-game/catalog/activation remain pending;
  preserve raw results and update README and hashed evidence.

- Register the cache/planning patch and 150 additional fixed native cases,
  targeting 4,085 native and 2,928 packaged checks. Keep every existing partition
  member in order and retain the original 1,271-case resolution deadline. Copy
  collection fixtures into their original Java package. Verify 174 production
  origins, resolving private binary classes directly and rejecting wrong
  identities or missing code sources. Add three boundary tests. Fresh build and
  runtime gates remain required; the selected runtime is unchanged.

- Reproduce a replacement-cache gap for Vengeance type changes: six of 36 native
  cache-equivalence controls fail before the fix; all 36 pass after adding the
  missing cache-key flag. Preserve baseline, corrected and failed diagnostic
  attempts. All 2,885 source cases pass with previous cases retained. Preserve
  114 preceding live-trait, empty-attack and core-collection controls and the
  candidate-budget-preserving planning changes. Keep these changes pending
  registration/runtime gates; the original packaged resolution timeout remains
  unresolved and the selected runtime stays unchanged. Update README and evidence.

- Preserve the fresh 3,935-case native build and source/binary parity audit,
  retaining all prior cases. Record the failed independent packaged gate:
  resolution (1,271 expected cases) reached its unchanged 90-second limit;
  three earlier groups and three other families passed, totaling 1,272 subtests.
  The 640-case cast-event group was not reached. Preserve an instrumented timing
  and stack-sampling diagnostic that also timed out after 1,076 passing methods.
  Keep these failures explicit; no unchanged retry, increased budget or omitted
  cases. The selected runtime is restored and unchanged, candidate uninstalled;
  performance investigation and remaining runtime gates are still required.

- Preserve the 3,899-case fresh build and audit, plus the independent packaged
  probe that found a mana-ability admission regression (3/4 pass). Keep that
  runtime uninstalled. Add a generic non-stack action guard while preserving
  cast-trigger checks for mana-producing spells. Add 36 native/preview controls
  across copy depths 0/1/2; all 850 focused and 2,735 source cases pass with every
  earlier case retained. Pin the incremental patch and expanded fixture; target
  3,935 native and 2,778 packaged cases. Preserve the failed fixture compilation
  and corrected run. Fresh build/runtime gates remain pending; selected runtime,
  card scripts and AI profiles are unchanged. Update README and hashed evidence.

- Preserve the first registered event build's 3,872/3,877 result and its five
  regressions. Track native stack-entry IDs through activation copying and
  preserve continuation timing with guarded cost-only snapshots. Default copies
  retain state checks; deferred copies reject invalid windows, phase advancement,
  experimental restoration and stale reuse. Add 22 native controls and retain all
  optional-X/cleanup cases: 814 focused and 2,699 source cases pass. Register the
  incremental correction and fixture, targeting 3,899 native and 2,742 packaged
  cases under unchanged limits. README and hashed evidence updated. Fresh retry
  and runtime gates remain pending; the selected runtime is unchanged.

- Register the validated cast-event/X-cost aggregate and five fixed native
  regression fixtures. Pin their hashes in the recipe and build/source registries;
  target 3,877 native cases while retaining all 3,295 prior cases. Add a separate
  582-case independent packaged group, bringing its expected total to 2,720 under
  unchanged worker limits. Update README and registration evidence. Fresh build
  and runtime gates remain pending; the selected runtime is unchanged.

- Select native pre-goal forecasting from live cast/copy/target event eligibility,
  including suppression and delayed triggers, instead of ward keywords. Preserve
  native registration behavior and remap independent per-turn targeting histories
  in copied cards. Cover Frost Titan, Kira, Chalice and suppressed tax estimates.
  Fix rejection of wins from cast triggers, including wins after a countered goal,
  while preserving older stack entries. Retain the failed Bontu airbend source
  regression and all earlier cases: 768 focused and 2,653 source cases now pass.
  Preserve the failed 2,611-source run, intermediate regressions, native outcome
  controls and corrected fixture assumptions. Document the 64-resolution limit
  with 63/64/65 native probes. Eight generic sources differ from the installed
  baseline; no card/profile changes. README and evidence updated; fresh runtime
  gates remain pending and complete card/rules correctness is still unproven.
  See engine/target_tax_evidence.json.

- Replace manual ward forecasting with bounded native stack-segment execution in
  a copied game, stopping before the forecast action resolves. Preserve native
  costs, choices, cast/target/cost triggers and state-based actions. Fix reuse of
  creatures after blight kills them, missed payer elimination from poison/life
  loss, and duplicate cast-tax estimates after actual trigger payment. Cover
  collect evidence and tokens generated to pay later ward costs. All 588 focused
  and 2,473 source cases pass; the previous prototype passes 579/588 on identical
  fixtures. Preserve failed prototypes and the invalid-target fixture diagnosis.
  Update source-isolation checks, README, and hashed patch evidence. Card scripts,
  profiles and the installed runtime are unchanged. Broader trigger admission and
  fresh runtime validation remain pending; full rules correctness is unproven.
  See engine/ward_transition_evidence.json.

- Extend the uninstalled X-cost prototype with complete native ward-cost contexts
  and correct payer resources for waterbend/convoke. Cover mixed mana/life,
  variable life, discard alternatives, sacrifice replacement, doubled triggers,
  repeated targets, mana-derived uncounterability and preview isolation. All 498
  focused and 2,383 retained source cases pass. The installed runtime passes
  360/498 controls; 33 failures concern new APIs and 105 concern behavior/contracts.
  Preserve intermediate failures and verify the aggregate patch against its source
  baseline. Update README with remaining cost-trigger/state-action, cost-family,
  payer and runtime-validation gaps. Six generic sources change, with no card or
  profile edits. The runtime remains unchanged; full rules correctness is unproven.
  See engine/typed_ward_evidence.json.

- Extend the uninstalled X-cost prototype to 363 focused and 2,248 source cases,
  retaining every prior case. Compare the selected runtime's 267/363 result and
  classify 18 absent-API checks separately. Separate proposed X from follow-up
  mana, require completed minimum-X choices, and validate generic payment before
  undo cleanup. Native copied payment contexts respect cast-only restrictions,
  explicit costs, source reservations and lethal-tap policy while preserving
  original state and RNG/cache isolation. Preserve earlier unannounced-X,
  ward-budget, restricted-mana and fixture-compile failures. Five generic sources
  change; no card/profile changes. Full typed waterbend/mana-life ward controls
  remain required before registration; fresh runtime gates remain pending and
  the installed runtime is unchanged. See engine/waterbend_x_evidence.json.

- Preserve an uninstalled generic variable-X waterbend/minimum-X prototype
  passing 300 focused and 2,185 source cases, retaining every previous case.
  Compare the selected runtime's 252/300 result. Native paid effects cover all
  four variable-X waterbend cards; ordinary mana/exile minimums and legal zero-X
  casts add controls. Copy the proposed-X forecast and enforce already announced
  minimums at native cost boundaries, without card-name checks. Retain earlier
  invalid-target and missing-refund fixture failures and corrected native
  payment/refund controls. Unannounced-X completion and forecasting-budget
  semantics need more controls before registration. Fresh runtime gates remain
  pending; the installed runtime is unchanged. See engine/waterbend_x_evidence.json.

- Select the validated chained-mana runtime after all 186 focused, 2,071 source
  and 3,295 native cases pass, retaining every prior case. Independent packaged
  checks pass 2,138/65/194/146 with 168 origins; broader suites pass 4,769 gameplay
  and 256 serialization cases. Full integration passes 45 tests/3,191 subtests
  in 1061.484 seconds at unchanged seeds and limits. Catalog construction passes
  32,636 identities plus seven reviewed nonstarting meld results. Verify all
  21,859 copied runtime files and installed health; preserve the previous runtime,
  sealed build, rollback metadata and earlier failed prototypes. Two generic
  sources change four existing classes and add five helper records; card scripts
  and AI profiles remain unchanged. This bounded plain-source fallback does not
  complete full card/rules/AI support. See engine/mana_chain_activation_evidence.json.

- Register the source-validated chained-mana patch and 48-case fixture in the
  engine recipe, native selector and independent packaged partitions. Require
  3,295 native cases and 2,138/65/194/146 packaged cases with 168 production
  origins, retaining every prior case. Fresh runtime gates remain pending; the
  installed runtime is unchanged.

- Preserve an uninstalled generic chained-mana fallback passing all 186 focused
  and 2,071 source cases, retaining every previous 138/2,023 case. Compare the
  installed engine's 168/186 result and classify payment, preview and absent-API
  failures separately. Native costs/stack execution cover chained filters,
  floating-color seeds and scarce-color allocation. Preserve preview state,
  RNG/cache isolation, payment metadata and existing reserved-source/lethal-tap
  policy. Retain the failed compiler, search, preview and policy prototypes and
  corrected fixtures. Resource limits are unchanged; the bounded search does
  not prove impossible payments. Fresh registered/runtime gates and complete
  card/rules/AI support remain pending. See engine/mana_chain_evidence.json.

- Select the validated shared mana-activation runtime after all 138 focused,
  2,023 source and 3,247 native cases pass, retaining every prior 3,142 native
  case. Independent 2,090/65/194/146 packaged checks verify 163 origins; broader
  4,721 gameplay/256 serialization and full 45 tests/3,143 subtests pass at
  unchanged seeds and limits. Catalog construction passes 32,636 identities
  plus seven reviewed nonstarting meld results. Verify all copied runtime bytes
  and installed health, retaining the prior runtime and sealed build. Two source
  files/seven existing classes change; scripts and AI profiles are unchanged.
  Preserve both validation epochs, the verifier-only input comparison, the
  failed stale-origin assertion and all earlier prototype/fixture failures.
  Chained/colored allocations, variable-X waterbend, cost modifiers, dynamic
  limits, diagnostics and complete card/rules/AI support remain unfinished.
  See engine/mana_activation_budget_activation_evidence.json.

- Verify the fresh candidate's 3,247 native cases, retaining all previous 3,142,
  complete production-source parity and changes limited to the two reviewed
  families. Preserve the first packaged attempt: 706 method results pass, then
  an overlooked Python assertion expects 161 origins instead of the new 163.
  Correct that assertion; the complete independent packaged and later gates
  remain pending. The installed runtime is unchanged.

- Register generic repeated-mana activation-budget and entry-turn snapshot
  corrections after all 138 focused and 2,023 source cases pass. Preserve all
  1,933 immediate cases and the earlier prototype's 48/138 failures. Native
  controls cover explicit and keyword/static budgets, life/sacrifice sources,
  copied games and entry metadata across six zones. Keep the Power-Up setup
  failures and copy-state discovery. Fresh native/packaged/broader/full/catalog
  gates remain pending; the installed runtime is unchanged. See
  engine/mana_activation_budget_evidence.json.

- Reproduce automatic refusal of repeated Prismite conversion with independent
  native legal witnesses. Preserve a generic, uninstalled prototype passing all
  48 focused and 1,933 source cases, including insufficient-resource and native
  replacement controls. Retain all earlier cases and failed fixture versions.
  Activation-limit coverage and fresh runtime gates remain pending; the selected
  engine is unchanged. See engine/repeated_mana_filter_evidence.json.

- Select the validated mana-filter runtime: all 33 focused/1,918 source,
  3,142 native (previous 3,109 retained), independent 1,985/65/194/146 packaged
  with 161 origins, broader 4,616 gameplay/256 serialization and full45tests/
  3,038 subtests pass at unchanged seeds and limits. Catalog construction passes
  32,636 identities plus seven reviewed nonstarting meld results. Verify copied
  bytes and installed health; preserve the previous runtime and sealed build.
  One production file changes and the assessment enum is added; card scripts
  and AI profiles remain unchanged. Preserve all failures and corrected test
  assumptions. Normal strategy preference remains enabled; scoped assessment
  results do not certify exhaustive legal affordability. CLI/UI integration,
  repeated/chained conversions, variable-X and full card/rules/AI support remain
  unfinished. See engine/waterbend_filter_activation_evidence.json.

- Register the generic mana-filter correction and scoped assessment API after
  all 33 focused/1,918 source cases pass, retaining 1,885 original and 1,906
  intermediate cases. Preserve reservations during nested funding, pending tap
  sources and direct-source priority for simple any-color/non-increasing filters.
  Distinguish plans found with the current or relaxed sacrifice-value preference
  from unsuccessful searches. Preserve normal strategy policy, native ordered
  legal witnesses, all previous failures and preview-memory purity. Register
  3,142 native/1,985 packaged targets and 161 origins together. Fresh runtime
  validation and activation remain pending; user-facing diagnostics and full
  card/rules/AI support remain unfinished.

- Confirm remaining nested mana-filter gaps: Signet funding may sacrifice an
  artifact already tapped for waterbend; simple any-color/non-increasing filters
  are excluded by automatic admission. Preserve every native baseline, partial
  prototype and setup/fixture correction. The v4 prototype passes 21 controls
  and 1,906 source cases, retaining all 1,885. The later 27-case/v6 prototype
  passes 24; three automatic refusals come from sacrifice-value policy while
  ordered native witnesses prove legal payments. Correct rollback expectations
  under CR734.1: retained legal mana abilities keep their costs and mana. No
  new correction is registered or selected; full card/rules/AI coverage remains
  unfinished. See engine/waterbend_filter_gaps.json.

- Select the validated waterbend-payment runtime: all 99 focused/1,885 source,
  3,109 native (previous 3,010 retained), independent 1,952/65/194/146 packaged
  with 157 origins, broader 4,583 gameplay/256 serialization, and full 45 tests/
  3,005 subtests pass at unchanged seeds and limits. Catalog construction passes
  32,636 identities plus seven reviewed nonstarting meld results. Verify copied
  bytes and installed health while preserving the previous runtime and both
  sealed builds. Preserve the first candidate's broader combo failure and its
  generic transform guard correction. Five production files change; card scripts
  and AI profiles stay unchanged. Archive immutable source/resource/failure
  evidence. Complete card/rules/AI correctness remains unproven.

- Preserve the first waterbend candidate's 3,103 native/2,351 packaged passes
  and 4,576 pass/1 fail broader gameplay result. Newly legal transforms exposed
  an AI fallback bypassing the existing combo guard. Share that guard with
  transform heuristics without rejecting legal payment. All 99 focused and
  1,885 source cases pass, including the exact failed upstream combo scenario;
  retain prior 1,878 cases and all diagnostic/setup failures. Register fresh
  3,109 native/1,952 packaged targets and 157 origins. Broader, full, catalog
  gates and activation remain pending; the installed runtime is unchanged.

- Extend the waterbend correction through actual mana execution and payment
  rollback. Reproduce sacrifice resource reuse and leaked taps after rejection;
  all 93 focused/1,878 source cases now pass, retaining all previous 1,869 cases.
  Preserve legal sacrifice of previously tapped artifacts and verify a fresh
  payment after injected post-payment rejection. Baseline48 pass/45 fail;
  all diagnostic/setup failures remain recorded. Register 3,103 native/1,946
  packaged targets. The installed runtime remains unchanged and affected;
  fresh runtime validation and activation remain pending.

- Register the generic two-file waterbend correction: all 84 focused and 1,869
  combined source cases pass, retaining 1,785 and the earlier 1,866-case phase.
  Enforce the six-tap component cap, legal creature/mana-capable taps and joint
  normal mana allocation without reusing tap/sacrifice resources. Preserve
  preview memory, existing creature reservation when independently affordable
  and native-mana-first alternatives. Latest baseline48 pass/36 fail. Retain all
  partial prototypes, compiler setup failure and later regression controls.
  Register 3,094 native/1,937 packaged targets and 154 production origins with
  matching integration assertions. No card scripts/AI profiles change; fresh
  runtime/full/catalog gates and activation remain pending. The installed
  runtime remains affected; see engine/waterbend_payment_evidence.json.

- Confirm native waterbend payment gaps in the selected runtime. Preserve all
  24/48/51/60/66-case baselines: the latest passes 33/fails 33. Actual casts can
  exceed the six-tap cap and underpay normal/tax costs; legal creature-assisted
  and mana-capable-permanent payments are also rejected. The generic one-file
  prototype passes 51/51 initial cases and 54/66 expanded cases, with 12 legal
  payments still failing. It remains partial, unregistered and unactivated.
  Retain original/one/two-copy outcomes, every version and all native reports
  under engine/pending/waterbend-payment and engine/waterbend_payment_gaps.json.
  Joint waterbend/mana allocation and full runtime gates remain required.

- Select the validated test-only earthbend-return milestone: 1,785 source,
  3,010 native (all 2,934 previous methods retained), independent packaged
  1,853/65/194/146 in 315.031s with all 151 origins verified, broader 4,484
  gameplay/256 serialization and all 45 full tests/2,906 subtests in 1256.891s
  at unchanged seeds/limits. Catalog construction passes 32,636 identities plus
  seven reviewed nonstarting meld results. Verify installed bytes and health;
  preserve the previous runtime and sealed build. All 76 supplied earthbend
  cases pass without a production correction; every production class, card
  script and AI profile is unchanged. Preserve the initial three incorrect
  Swords life assertions, corrected phases and immutable validation inputs.
  See engine/earthbend_return_activation_evidence.json. Full card/rules/AI
  support remains unproven.

- Register 76 paid/native earthbend return controls after all pass in the selected
  runtime and all 1,785 combined source cases pass, retaining 1,709. Cover death/
  exile, source movement, ownership/control, pending spells/triggers, turn reset,
  counter prevention/removal, competing and old/new-object return triggers,
  Tracker Clues, Cremate/Stifle responses and Ephemerate blink. Preserve all
  12/40/58/76-case phases and the corrected three Swords life-beneficiary
  assertions. No production/card/AI changes. Register 3,010 native/1,853 packaged
  targets and 151 production origins, including the matching opt-in integration
  assertions. Fresh runtime/full/catalog gates and selection remain pending;
  see engine/earthbend_return_evidence.json.

- Select the validated elemental-history milestone: 1,709 source, 2,934 native
  (all 2,882 previous methods retained), independent packaged 1,777/65/194/146
  in 307.875s, broader 4,408 gameplay/256 serialization and all 45 full tests/
  2,830 subtests in 1259.875s at unchanged seeds/limits. Catalog construction
  passes 32,636 identities plus seven reviewed nonstarting meld results.
  Verify installed bytes/health and preserve the previous runtime and both
  candidate builds. Resolve the selected elemental-history gap; register all
  25 elemental, 21 Airbend and six supplementary departed-counter cases.
  Only Player/GameCopier production families change; scripts and AI profiles
  are unchanged. Preserve every earlier failed/setup phase, including the stale
  integration assertions. See engine/elemental_bend_activation_evidence.json.
  Full card/rules/AI support remains unproven.

- Correct stale opt-in integration expectations after expanding the registry:
  require all 1,777 cases and all 145 production origins. Preserve the first
  packaged run: its 301 first-partition cases passed, but its old 143-class
  assertion stopped the suite. The three companion groups passed. No gameplay
  failure or timeout; fresh validation remains required under the corrected
  test epoch.

- Register generic elemental-action history restoration and the supplementary
  counter/Airbend tests. Combined source validation passes 1,709/1,709, retaining
  all 1,657 prior methods plus six departed-counter, 21 Airbend permission and
  25 elemental cases. Register 2,934 native/1,777 packaged targets and 145 origins.
  Preserve source manifests and reports in engine/elemental_bend_evidence.json.
  Fresh build, packaged/broader/full/catalog validation and activation remain
  pending; installed runtime remains affected. No scripts or AI profiles change.

- Confirm copied elemental-action history is lost in the selected runtime:
  25 cases pass 11/fail 14; generic Player/GameCopier prototype passes 25/25
  and 408/408 related contracts. Preserve the failed original combat setup,
  corrected 12 controls, expanded pending-spell/cleanup/isolation checks and
  prototype patch in engine/pending/elemental-bend and engine/elemental_bend_gaps.json.
  Production activation and fresh build/full gates remain pending.
- Preserve 21 passing supplementary Airbend permission/cost cases, retaining
  the original 12, including owner-only casting, timing, mandatory additional
  costs, cost increases/reductions and pending-spell copies. No production
  change; suite registration remains pending and sealed counts stay unchanged.
  See engine/airbend_permission_evidence.json.

- Verify supplementary departed-player counter receipts: all 59 cases pass in
  the selected runtime, retaining the 53 existing bodies and adding six original/
  one/two-copy cases. The prior runtime passes 19 controls/fails 40 copies. Check
  private historical cards after their owner leaves, mapped departed payer keys,
  reference independence and actual Hornbeetle tokens. No production changes;
  the added six cases await regular suite registration and sealed counts stay
  unchanged. Preserve fixtures/evidence in engine/pending/counter-history/departed
  and engine/counter_history_departed_evidence.json.

- Select the validated counter-history milestone: 1,657 source, 2,882 native
  (all 2,829 previous methods retained), independent packaged 1,725/65/194/146
  in 305.906s, broader 4,356 gameplay/256 serialization and 45 full
  tests/2,778 subtests in 1250.359s at unchanged seeds/limits. Catalog
  construction passes 32,636 identities plus seven reviewed nonstarting meld
  results. Preserve prior runtime and sealed build; verify installed bytes and
  health, archive the lock and bind receipts in engine/counter_history_activation_evidence.json.
  Resolve the selected counter-history gap while retaining all original failures
  and intermediate/setup-error phases. Only two production families change;
  scripts and AI profiles are unchanged. Complete card/rules/AI support remains unproven.

- Register the generic two-file counter-history correction: independent mapped
  placement/removal receipts preserve actual token/draw/cost/activation outcomes
  without repeating counter events. Baseline 53 passes 17 original controls and
  fails 36 copies; correction passes all 53, all 408 related cases and all 1,657
  mixed source cases, retaining 1,604. Preserve original nine/41-case phases,
  partial placement-only prototype and setup failure. Register 2,882 native/
  1,725 packaged targets and 143 production origins. No scripts or AI profiles
  change. Fresh runtime/full/catalog gates remain pending and the installed
  Aura runtime remains affected; see engine/counter_history_evidence.json.

- Confirm a turn-counter history gap in the selected Aura-entry runtime: three
  paid original controls pass and six one/two-snapshot cases fail, changing
  history queries, Hornbeetle token counts and Danny Pink first-time draws.
  Preserve the nine-case baseline, its earlier insufficient-green setup failure,
  corrected setup and all native reports. No production correction yet; see
  engine/counter_history_gaps.json and engine/pending/counter-history.

- Select the validated Aura-entry coverage milestone: 1,604 mixed source,
  2,829 native (all 2,781 previous methods retained), normal packaged
  1,672/65/194/146 in 307.187s, broader 4,303 gameplay/256 serialization,
  and 45 full tests/2,725 subtests in 1260.859s at unchanged seeds and limits.
  Catalog construction passes 32,636 identities plus seven reviewed nonstarting
  meld exclusions. Preserve prior runtime and sealed candidate; verify runtime
  resource bytes and post-install health, archive the lock and bind receipts in
  engine/simultaneous_aura_activation_evidence.json. All 48 paid Aura cases pass
  without production changes; compiled production classes, cards and AI profiles
  match the prior exert runtime. Complete card/rules/AI support remains unproven.

- Register all 48 paid Aura-entry witnesses in the build and packaged suite;
  combined source validation passes 1,604 cases with all 1,556 previous methods
  retained. Set future candidate targets to 2,829 native/1,672 packaged cases,
  including 805 in the existing resolution partition and 141 production origins.
  No production, card-script or AI-profile changes. Fresh sealed runtime and
  independent packaged/broader/full/catalog gates remain pending; keep the
  installed exert runtime and prior evidence unchanged. See
  engine/simultaneous_aura_registration_evidence.json.

- Verify 48 supplementary paid Replenish Aura-entry cases against the selected
  packaged runtime, retaining all 18 initial controls. Cover both graveyard
  orders, original/one/two copies, on-stack copies, preexisting enchantment
  requirements, simultaneous creature entry, hexproof and protection. All pass
  within the unchanged 1536 MiB/two CPU/90-second diagnostic budget. No defect
  demonstrated or production change made. Preserve fixtures and hash-bound
  evidence in engine/pending/simultaneous-aura and
  engine/simultaneous_aura_evidence.json; standard suite registration is pending.

- Select the validated exert snapshot milestone: 1,556 mixed source, 2,781
  native (all 2,747 previous methods retained), normal packaged
  1,624/65/194/146 in 300.844s, broader 4,255 gameplay/256 serialization,
  and 45 full tests/2,677 subtests in 1248.547s at unchanged seeds and limits.
  Catalog construction passes 32,636 identities plus seven reviewed nonstarting
  meld exclusions. Preserve the prior runtime and sealed build, verify installed
  resource bytes and health, archive the lock and bind receipts in
  engine/exert_snapshot_activation_evidence.json. Resolve the selected exert-gap
  status while retaining all original failures and setup-error artifacts. No
  card-script or AI-profile changes. Supplied attack/exert choices do not prove
  default AI strategy; complete card/rules/AI support remains unproven.

- Preserve exert counts and fresh player-mapped restrictions in simulation and
  historical snapshots without repeating costs or firing exert triggers. Retain
  new-object/ordinary-copy resets, repeated exertion expiring at one untap,
  untap effects, control changes and independent expiry for two exerting players.
  Paid creature-attack cases supply legal declarations/exert choices and verify
  native costs, printed damage/life gain, combat damage and no duplicate triggers.
  The 34-case baseline passes 9/fails 25; the prototype passes all 34, all 408
  related cases and all 1,556 mixed source cases, retaining 1,522. Preserve original
  eight bodies, intermediate 26/31 cases and research setup failures. Register
  the three-file patch, exact fixtures, 2,781 native/1,624 packaged targets and
  140 production origins. No card-script or AI-profile changes. Fresh runtime,
  full-game and catalog gates remain pending; the installed 2,747-case World
  runtime remains unchanged and affected. See engine/exert_snapshot_evidence.json.
  Complete card/rules/AI support and default AI exert choices remain unproven.

- Select the validated World snapshot milestone: 1,522 mixed source, 2,747
  native (all 2,716 previous methods retained), normal packaged
  1,590/65/194/146 in 285.188s, broader 4,221 gameplay/256 serialization,
  and 45 full tests/2,643 subtests in 1241.469s at unchanged seeds and limits.
  Catalog construction passes 32,636 identities plus seven reviewed nonstarting
  meld exclusions. Preserve the prior runtime and sealed build, verify installed
  resource bytes and post-install health, archive the lock and bind receipts in
  engine/world_snapshot_activation_evidence.json. Resolve the selected World's
  snapshot-gap status while preserving all original failures.
- Preserve a separate confirmed exert snapshot gap: both paid original Arena of
  Glory controls pass, while six copied state/untap cases fail. Use actual exert
  mana spent on Furnace Whelp and first/second owner untap outcomes. Record exact
  fixtures, methods and receipts in engine/exert_snapshot_gaps.json. No exert
  correction is selected; full card/rules/AI correctness remains unproven.

- Preserve World age in generic simulation and historical snapshots. Keep new
  age for ordinary copies and paid re-entry, age retention through control and
  ability changes, simultaneous-entry ties, and independent temporary-copy
  expiration. The expanded baseline passes 14/fails 17; the three-file prototype
  passes all 31 paid/history/transition cases, all 374 related cases, and all
  1,522 mixed source cases, retaining 1,491. Preserve the original eight cases,
  intermediate 26 cases, all failed results, and the initial unused-import
  Checkstyle stop. Register the exact patch/fixtures, 2,747 native target,
  1,590 packaged cases and 137 production origins. No card-script or AI-profile
  changes. Fresh runtime/full-game/catalog gates remain pending; the installed
  2,716-case runtime remains unchanged and still has the World gap. See
  engine/world_snapshot_evidence.json. Full rules/card/AI correctness is unproven.

- Select the validated Harnessed/Class snapshot milestone. Pass 1,491 mixed
  source, 2,716 native (all 2,652 previous methods retained), normal packaged
  1,559/65/194/146 in 326.031s, broader 4,190 gameplay/256 serialization, and
  45 full integration tests/2,612 subtests in 1,343.313s at unchanged seeds and
  resource limits. Catalog construction passes 32,636 identities plus seven
  reviewed nonstarting meld exclusions. Preserve the prior runtime and sealed
  build, archive the lock, verify post-install health, and bind receipts in
  engine/snapshot_designation_activation_evidence.json. Resolve the selected
  runtime's Harnessed/Class gap status while retaining original failures.
- Preserve a newly confirmed paid World snapshot gap: two original controls
  pass while six copied timestamp/survivor cases fail. The Abyss/Teferi's
  Protection/Nether Void witness pays real costs and checks independent worlds.
  Record exact fixture, methods, failures and local receipts in
  engine/world_snapshot_gaps.json. No World correction is selected, and complete
  card/rules/AI correctness remains unproven.

- Preserve generic Harnessed designations and Class levels in simulated and
  historical states. Keep ordinary in-game copy/new-object resets, control-change
  retention and level/designation retention under type changes. Paid Mind/Soul
  Stone and Wizard Class witnesses include actual costs, phase/draw consequences,
  private history, independent worlds and paid Song of the Dryads/Naturalize
  restoration. Preserve all original 12 cases and the 52-case intermediate suite.
  The final baseline passes 20/fails 44; the two-file correction passes all 64,
  all 343 related and all 1,491 mixed source cases, retaining 1,427. No card-name
  production conditions, card-script changes or AI-profile changes. Register the
  fresh 2,716 native/1,559 packaged targets and 135 production-origin checks;
  independent runtime/full-game/catalog gates remain pending. See
  engine/snapshot_designation_evidence.json. The selected 2,652-case runtime
  remains unchanged and still has the confirmed copied-state gaps.

- Select the reviewed prepared-state milestone after all required gates pass:
  1,427 mixed source, 2,652 native (all 2,361 previously selected methods retained),
  packaged 1,495/65/194/146 in 315.782s, broader 4,126 gameplay/256 serialization,
  and all 45 full tests/2,548 subtests at unchanged seeds 42/0/4096. Catalog
  construction passes 32,636 identities with seven reviewed nonstarting meld
  exclusions. Copy and verify the installed resources, preserve the prior
  runtime/sealed build, and verify post-install health. Archive the lock and bind
  activation evidence in engine/prepared_state_activation_evidence.json.
- Preserve two newly confirmed paid snapshot gaps: harnessed Mind/Soul Stone
  designations and Wizard Class levels. Each baseline passes two original
  controls/fails four one/two-copy cases. Record exact fixtures and native results
  in engine/snapshot_designation_gaps.json and pending/snapshot-designations.
  No correction is claimed yet; full rules/card/AI coverage remains incomplete.

- Keep every packaged draw/rules case while splitting the oversized worker into
  four fixed mechanic groups (301/228/338/628). Reject missing, repeated and
  unregistered classes, retain all method/summary/origin assertions, and keep
  worker 90s/2048 MiB/two CPU and whole-smoke 420s limits. Keep the combined
  2,652-case native JVM gate. Twelve draft offline tests pass; diagnostic groups
  pass all 1,495 in 193.375s. Registered harness runtime validation remains
  pending, and the original single-worker timeout remains failed. The prepared
  artifact also passes all 2,652 native and independent 4,126 gameplay/256
  serialization cases, with 65/194/146 other packaged cases passed. Record
  engine/prepared_state_runtime_evidence.json; full games/catalog/activation
  remain pending and complete card/rules coverage is unproven.

- Preserve generic prepared state across copied games: map the creature/effect/
  spell links after remembered objects, retain the exiled prepared face, and map
  leaves-play cleanup. Keep historical prepared memories private and rebind
  historical cast-origin zones to the target player. Paid Infirmary Healer and
  Stream of Life cases verify actual payment/resolution, consumption, normal
  re-entry and independent one/two-copy cleanup/history. The baseline passes
  four/fails 14; the final prototype passes all 18. Retain the strengthened
  cast-origin probe's 16-pass/two-fail intermediate result. All 279 related and
  1,427 mixed source cases pass (1,409 retained plus 18). Preserve setup/API and
  compile-source-list corrections. Register fresh native 2,652 and packaged
  1,495 targets; all full-runtime gates remain pending. See
  engine/prepared_state_evidence.json.

- Verify the deferred-repeat artifact with all 2,634 native and independent
  4,108 gameplay/256 serialization cases. The 1,477-case packaged family times
  out at the original 90-second limit on both Java 17 and a separately pinned
  Java 21 comparison; all 65/194/146 other cases pass on each. Preserve both
  failures, keep Java 21 unadopted, and perform no full-game/catalog phase or
  activation. See engine/deferred_repeat_runtime_evidence.json.

- Preserve generic deferred per-player repeats in snapshots by explicitly mapping
  source, player and repeated subability. Paid Azor/Sphinx's Decree witnesses
  require next-turn instant/sorcery restrictions for all three opponents, allow
  creatures, and verify expiry on following turns in independently advanced
  original/one/two-copy worlds. Two original controls pass while eight copied
  cases fail before correction; all ten pass afterward. All 155 related and
  1,409 mixed source cases pass, retaining 1,399 and adding ten. Preserve the
  initial fixture's incomplete permission query and its full-native-API correction.
  Register fresh sealed 2,634 and packaged 1,477 targets; runtime gates remain
  pending. See engine/deferred_repeat_evidence.json.

- Verify the skipped-phase artifact with 2,624 native, 4,098 gameplay and 256
  serialization cases, retaining all 2,609 earlier native methods. Packaged
  65/194/146 cases pass; preserve the 1,467-case family's 90-second timeout as a
  failed gate. No full-game/catalog phase or activation was performed. See
  engine/skipped_phase_snapshot_runtime_evidence.json.

- Correct copied skipped-phase callbacks generically: deferred effect entry and
  expiry now map their effect/source references into the owning snapshot world.
  Three normally paid original-game controls pass while 12 copied cases fail
  before correction; all 15 pass afterward. Retain both copy depths and world
  advancement orders. Related scheduling/cleanup/goad/control checks pass 145,
  and all 1,399 mixed source cases pass (1,384 retained plus 15 new). Register
  the patch and fixed fixture for fresh sealed 2,624 and packaged 1,467; keep
  all full-runtime gates pending and the prior 90s packaged timeout intact.
  See engine/skipped_phase_snapshot_evidence.json.

- Verify the commander-history candidate with all 2,609 sealed native tests,
  retaining every previous 2,585 method, and independent 4,083 gameplay/256
  serialization checks. Three packaged families pass 65/194/146 cases; preserve
  the 1,452-case family's 90-second timeout as a failed gate. Timing diagnostics
  implicate nested spell planning and historical copying; a per-zone identity
  memo also times out and remains an isolated, unadopted prototype. Preserve the
  initial timing-listener setup failure separately. No full-game/catalog phase
  or activation was performed for this candidate. See
  engine/departed_commander_history_runtime_evidence.json.

- Fix historical commander damage copying after the source owner leaves. Reuse
  the existing historical graph for commander references and ability/callback
  state; preserve live-zone identity, historical sources, independent counters
  and paid cast/mana provenance without dropping records or weakening strict
  lookup. Add 24 named combat/paid-cast cases: both baselines pass six/fail six,
  while all 285 related and 1,384 mixed source cases pass. Preserve fixture API,
  command-zone permission and zone-reset premise corrections. A fresh sealed
  2,609-native build and complete runtime gates remain pending. See
  engine/departed_commander_history_evidence.json.

- Preserve the multiplayer-horizon candidate's full live failure: 42 tests and
  all 2,481 subtests pass, but three tests fail when actual games encounter a
  departed commander-history mapping exception. Goreclaw's attack witness now
  passes in seed 0; retain the unchanged three seeds and incomplete-game checks.
  Independent catalog construction passes 32,636 starting identities with seven
  nonstarting meld exclusions. Packaged 1,833 and broader 4,059/256 checks passed;
  keep that candidate unactivated. See
  engine/multiplayer_attack_horizon_live_evidence.json.

- Build and audit the sealed multiplayer-horizon candidate: all 2,585 native
  tests pass, retaining 2,463 and adding 122. All 2,168 production sources match
  the reviewed source after line-ending normalization; changed/added classes
  stay within the six reviewed families, with cards and AI profiles unchanged.
  Preserve the first independent packaged run: all 1,833 native subtests pass,
  while the wrapper retains a stale 129-origin expectation for the reviewed
  132-class manifest. Correct only that count and retain exact/duplicate-free
  origin checks. Rerun, broader/full-live checks and activation remain pending.
  See engine/multiplayer_attack_horizon_native_evidence.json.

- Register generic multiplayer attack-horizon source and 122 uniquely named
  regressions. Carry native combat state through successive opponent turns,
  preserve live RNG/undo state, and avoid nested whole-round searches inside
  single-combat forecasts. Copy goad instances and their mapped expiration
  callbacks; verify independently expiring paid Disrupt Decorum effects.
  All 1,360 mixed source cases pass in one existing-budget JVM. Isolated overlays
  pass all 122 focused and 1,306 existing packaged cases. Preserve earlier unsafe,
  RNG, goad-copy/expiry, timeout and fixture-setup failures. Keep the selected
  runtime unchanged; sealed native 2,585, packaged 1,428, broader and full-live
  gates remain pending. See engine/multiplayer_attack_horizon_evidence.json.

- Add pending multiplayer attack regression witnesses: the corrected 50-case
  candidate baseline passes 38 and misses 12 ground-attack opportunities; the
  corrected 40-case suite passes 34 and misses six group attacks. All eight
  controlled trigger/damage outcomes and four sequential counterattack outcomes
  pass. Preserve and reject two generic fallback prototypes that recover attacks
  but fail six defensive declarations against cumulative counterattacks. Record
  fixture corrections and all failed phases; keep recipes, selected runtime,
  fixed full-game seeds and conformance checks unchanged. See
  engine/multiplayer_attack_opportunity_gap.json.

- Preserve the cache-lifetime candidate's incomplete live gate: 44 tests and
  all 2,359 subtests pass, while the limited full-game conformance check remains
  inconclusive because no fixed-seed game exercises Goreclaw's required attack
  trigger witness. Keep seeds and absent/contradictory-witness checks unchanged.
  Twelve native declaration probes reproduce the same open-opponent opportunity
  gap across classic/hybrid/full modes and original/copied states; these probes
  assert legality and record choices, not strategic optimality. Record the
  successful four-group packaged rerun, broader 3,937/256 checks, and independent
  catalog construction of 32,636 starting identities with seven nonstarting meld
  exclusions. Keep the previous runtime selected. See
  engine/ai_cache_lifetime_live_evidence.json.

- Verify the fresh cache-lifetime candidate with all 2,463 native tests passing,
  retaining 2,361 and adding 102 across combat history and cache lifetime.
  Audit the six production source families, eight changed classes, unchanged
  card/archive/profile data and absence of added or removed production classes.
  Preserve the first independent packaged run: all 1,711 native subtests pass,
  while the wrapper fails its stale 127-origin expectation. Update that guard to
  require the exact, duplicate-free reviewed 129-class manifest. Packaged rerun,
  broader/full live checks, catalog validation and activation remain pending.

- Correct the AI cache lifetime in source: remove the process-wide fallback
  that retained completed games, recompute unscoped queries from current state,
  and scope native spell decisions with exception-safe nested cleanup. All
  1,238 mixed source tests pass in one JVM with the existing 2 GiB heap and
  timeout, retaining 1,222 cases and adding 16. Preserve six selected-runtime
  cache failures, including stale original/copied board evaluations, and the
  earlier native build's 14 heap failures. Recorded strong-reference paths name
  AiCache as the owner. Exclude the snapshot-allocation prototype that still
  exhausted heap. Give four existing preview tests explicit live-decision setup
  while preserving their complete method bodies and assertions; verify the
  setup against old and new implementations. Replay the two-file cache patch,
  pin fixtures, and keep card/profile inputs unchanged. The selected runtime
  remains at 2,361 native tests; candidate packaging and live validation remain
  pending. See engine/ai_cache_lifetime_gap.json and
  engine/ai_cache_lifetime_native_evidence.json. Full rules coverage is unproven.

- Correct combat-local blocker history in isolated source: record confirmed
  declarations and resolving effects, retain departed zone objects, exclude
  cancelled selections, reset per combat, and deep-copy history between game
  branches. All 1,222 distinct focused contracts pass across disjoint 1,192/30
  JVM runs with identical production inputs, retaining 1,136 and adding 86.
  Preserve the 86-case selected-runtime baseline (42 passed, 44 failed), earlier
  baselines, the prototype's six cancelled-selection regressions, a corrected
  token-name fixture premise, and the combined source run's heap exhaustion.
  Test actual paid token entry and both printed blocker switches as well as
  Shock, Cloudshift, independent forks and direct declaration commit boundaries.
  Replay the four-file generic patch with explicit line-ending normalization.
  Keep heap/time budgets and card/profile bytes unchanged. The selected runtime
  remains at 2,361 native tests; packaging, broader and full live validation of
  this candidate are pending. Full rules coverage remains unproven. See
  engine/blocking_history_gap.json and engine/blocking_history_native_evidence.json.

- Activate generic reblocking state and paid-parent AI planning after all 1,136
  source and 2,361 native tests pass, retaining 2,333 and adding 28. Independent
  and repeated packaged groups pass 1204/65/194/146; broader gameplay and
  serialization pass 3,835/256; all 45 live tests and 2,257 subtests pass.
  Verify post-move readiness and construction of 32,636 starting identities with
  seven nonstarting meld exclusions. Preserve 20/28-case baselines, exact fixed
  fixtures and the state-only prototype's eight AI failures. Two existing files
  and classes change, with one new BlockAi class; scripts/profiles and limits
  are unchanged. Other blocker histories, restrictions, trigger policies and full
  Commander correctness remain unproven. See engine/combat_reblocking_evidence.json.

- Correct generic native reblocking state and paid-parent AI planning in isolated
  source. All 1,136 focused tests pass, retaining 1,108 and adding 28. Preserve
  baselines of 20 cases (4 passed, 16 failed) and 28 cases (8 passed, 20 failed),
  with identical fixed fixture bytes. Four basic damage controls pass before correction; four real paid Shock
  departure cases expose an incorrect defending-player loss. The state-only
  prototype passes all rules/damage/full-simulation controls but leaves eight
  classic/hybrid declines with runtime missing-Block-policy diagnostics. Add a
  generic Block AI policy that evaluates the complete paid parent. Two existing
  production files and one new AI class change; scripts/profiles are unchanged.
  Register future 2,361-native/1,204-packaged targets; the selected 2,333-test
  runtime remains unchanged. Wider blocking history, restrictions and triggers
  remain further work. See engine/combat_reblocking_native_evidence.json.

- Activate generic paid combat-removal planning after all 1,108 source and 2,333
  native tests pass, retaining 2,291 and adding 42. Independent/repeated packaged
  groups pass 1176/65/194/146; broader gameplay/serialization pass 3,807/256;
  all 45 live tests and 2,229 subtests pass. Verify post-move readiness and
  construction of 32,636 starting identities with seven nonstarting meld results.
  Preserve both four-failure baselines, the handler-only prototype failures and
  the first packaged receipt-size failure. Keep identical fixed fixtures, card
  scripts/profiles and 128 KiB/90-second limits. Two production files and three
  existing classes change; no classes are added. Triggered/composed reblocking
  and complete Commander rules/strategy remain unproven.
  See engine/combat_removal_evidence.json.

- Keep native evidence documents at 128 KiB by splitting large method results
  into bounded 500-case parts with a hash-bound canonical index. Preserve legacy
  single-file reads; derive physical names from fixed caller-selected logical
  names and reject altered names/counts before reading case files. Verify parsed
  bytes and hashes together. Nine offline regressions cover round trips,
  tampering, counts, path selection and oversized individual cases. Preserve the
  first packaged run: all 1,176 native methods pass, but receipt writing fails;
  the other three families pass. Fresh build passes 2,333 native tests. Packaged
  rerun, broader/live validation and combat activation remain pending.

- Correct generic paid combat-removal planning and action discovery in isolated
  source. All 1,108 focused tests pass, retaining 1,066 and adding 42. Preserve
  selected-runtime 18-case14pass/4fail and 24-case20pass/4fail reports, identical
  regression bytes and the first prototype's four failures before discovery was
  corrected. Native paid/Fog/full-simulation controls distinguish resolution from
  classic/hybrid willingness. Cover multiple attackers, indestructible, hexproof,
  payment limits and tapped sources in original/copied games. Register a future
  2,333-native/1,176-packaged build; these are targets, not completed checks.
  Selected runtime remains unchanged; triggers, reblocking and broader strategy
  need further verification. See engine/combat_removal_native_evidence.json.

- Activate first-extra draw timing and foreign-step exclusion after all 1,066
  source and 2,291 native checks pass, retaining 2,267 and adding 24. Independent
  and repeated packaged families pass 1134/65/194/146; broader gameplay and
  serialization pass 3,765/256; all 45 live tests and 2,187 subtests pass.
  Verify post-move readiness and construction of 32,636 starting identities
  with seven nonstarting meld exclusions. Preserve native 18-case12pass/6fail
  and 24-case15pass/9fail baselines and identical fixed-fixture bytes. Per-event
  checks expose timing errors hidden by equal batch totals. Two existing classes
  change; scripts/profiles are unchanged. Wider replacement/prevention/history
  and full Commander correctness remain open. See engine/draw_step_replacement_evidence.json.

- Correct first-extra draw timing and foreign-step exclusion in isolated source.
  All 1,066 focused checks pass, retaining 1,042 and adding 24 per-event/history
  contracts across original/active/common games. Preserve native18-case12pass/6fail
  and24-case15pass/9fail baselines; equal three-draw totals remain passing controls
  rather than timing proof. Two production files change; scripts/profiles are
  unchanged. Register future 2,291 native/1,134 packaged checks without claiming
  a rebuilt or active correction. See engine/draw_step_replacement_native_evidence.json;
  wider replacement/prevention/history and complete Commander correctness remain open.

- Activate generic Recruit evaluation and last-drawn payment provenance after
  all 1,042 source and 2,267 native checks pass, retaining 2,212 and adding 55.
  Independent/repeated packaged families pass 1110/65/194/146; broader gameplay
  and serialization pass 3,741/256; all 45 live tests and 2,163 subtests pass.
  Verify post-move readiness and construction of 32,636 starting identities
  with seven nonstarting meld exclusions. Preserve original failures, copied
  history, current-turn/hand/flag/timestamp eligibility and actual paid outcomes.
  Card scripts and Default/Cautious profile files are unchanged. Default counter
  policy, optional Recruit and full Commander rules/strategy remain open. See
  engine/last_drawn_payment_evidence.json.

- Validate generic last-drawn payment/provenance with all 1,042 focused source
  checks, retaining 1,021 and adding 21 original/active-copy/common-snapshot
  contracts. Share current-turn/current-hand/drawn-flag/timestamp eligibility
  between native cost admission and AI payment; rebind historical card references
  and preserve draw flags in copied games. Baselines retain 12-case8pass/4fail
  and 21-case13pass/8fail results plus a fixture syntax compilation failure.
  Register this and the prior Recruit correction for fresh builds: 2,267 native
  and 1,110 packaged checks are expected, not yet passed. Selected runtime remains
  unchanged. See engine/last_drawn_payment_native_evidence.json; broader rules,
  optional Recruit, default-policy strategy and full Commander support remain open.

- Validate the isolated Recruit/draw-history prototype with all 1,021 focused
  source checks, retaining 987 and adding 34. Compare complete paid root actions
  for Recruit; preserve four draw counters in active and common copy paths.
  Paid safety cases cover empty-library loss, conditional skips, draw prohibition,
  Laboratory Maniac and Platinum Angel; printed Notion Thief/Reed cases verify
  copied history and independent outcomes. Preserve failed phases, fixture compile
  and Checkstyle errors, controlled-policy refinement and raw source hashes.
  Patch replay matches normalized text; record line-ending byte differences
  explicitly. Selected 2,212-test runtime is unchanged; packaging, optional
  Recruit triggers, last-drawn references and wider correctness remain pending.
  See engine/recruit_planning_native_evidence.json.

- Preserve Recruit planning baselines: original16-case11pass/5fail and a
  controlled-admission16-case12pass/4fail diagnostic. Hybrid misses supplied
  Sound the Trumpets responses while full-mode choice, ordinary-counter controls
  and paid conditional Recruit resolution pass. Keep the original stochastic
  raw ordinary-counter decline separate; only admission checks select an existing
  willing profile, with all states/assertions and resource hashes preserved.
  No source correction or complete card coverage is claimed. See
  engine/recruit_planning_gap.json and both pending fixtures.

- Activate generic delayed disclosure and captured display metadata after all
  45 live tests and 2,108 subtests pass, including repeated packaged families
  of 1055/65/194/146. All 987 focused source, 2,212 native, 3,686 gameplay and
  256 serialization checks pass. Verify 533 frozen inputs, retained methods,
  original native and prefix failures, corrected auditor provenance, post-move
  readiness and construction of 32,636 starting identities with seven nonstarting
  meld exclusions. Three files and nine existing classes change; no production
  classes are added. Card scripts/default AI are unchanged. Other view/UI/history
  contexts and complete Commander rules/AI/strategy remain unproven. See
  engine/delayed_private_reveal_evidence.json.

- Validate the delayed-reveal candidate with all 2,212 native tests, retaining
  2,181 and adding 31. All 987 focused source, independent packaged families
  of 1055/65/194/146 and broader gameplay/serialization of 3,686/256 pass.
  Audit 2,165 production sources, three changed files, nine existing classes,
  no added production classes and 533 frozen implementation inputs. Card
  scripts/default AI are unchanged. Full live validation and activation remain
  pending; selected checks do not certify all Commander rules or strategy. See
  engine/delayed_private_reveal_packaged_evidence.json.

- Add a source-validated generic delayed-disclosure correction: all 987 focused
  checks pass, retaining 956 and adding 31. Resolve only current matching views
  and declared/current zones; enforce active-agent and per-card disclosure rights.
  Capture audience and zone/owner groups before callbacks, preserve outside-game
  restrictions, and retain single/multi-zone display prefixes. Validate paid
  printed inspection, counters, privacy, copied states and thirteen explicit
  identity/callback/metadata boundaries. Preserve original native and prefix
  failures. Packaging and broader/full live gates remain pending; selected
  runtime/scripts/default AI are unchanged. See
  engine/delayed_private_reveal_native_evidence.json. Full correctness is unproven.

- Preserve an18-case paid delayed-reveal baseline: ten controls pass and eight
  master/post-control assertions fail for Ancient Stirrings with no valid choice
  and Impulse with an actual choice, in original/copied states. Witness only
  unselected inspected identities. Investigate shared delayed disclosure,
  current-view identity, outside-game rights and callback boundaries separately;
  no source correction or full coverage is claimed yet. See
  engine/delayed_private_reveal_gap.json and the pending native fixture.

- Activate direct private-reveal and captured public-library observation routing
  after all 45 live tests and 2,077 subtests pass, including repeated packaged
  families of 1024/65/194/146. All 956 focused source, 2,181 native, 3,655 gameplay
  and 256 serialization checks pass. Verify 530 frozen inputs, retained methods,
  preserved native/Checkstyle failures, post-move readiness and construction of
  32,636 starting identities with seven nonstarting meld exclusions. Three source
  files and five existing classes change; no production classes are added. Card
  scripts/default AI are unchanged. Other direct/delayed callbacks and full
  Commander rules/AI/strategy remain unproven. See
  engine/direct_private_reveal_evidence.json.

- Validate the direct-reveal candidate with all 2,181 native tests, retaining
  2,159 and adding 22. All 956 focused source, independent packaged families
  of 1024/65/194/146 and broader gameplay/serialization of 3,655/256 pass.
  Audit 2,165 production sources, three changed files, five existing classes,
  no added production classes and 530 frozen implementation inputs. Card
  scripts/default AI are unchanged. Full live validation and activation remain
  pending; selected checks do not certify all Commander rules or strategy. See
  engine/direct_private_reveal_packaged_evidence.json.

- Add a source-validated generic direct-reveal correction: all 956 focused
  checks pass, retaining 934 and adding 22. Route private hand/peek disclosures
  through shared rights and capture public library audiences before callbacks.
  Validate dormant/removed agents, private callback changes, paid/countered
  printed cards and copied states. Preserve native failures and the initial
  unused-import Checkstyle stop. Packaging and broader/full live gates remain
  pending; selected runtime/scripts/default AI are unchanged. See
  engine/direct_private_reveal_native_evidence.json. Full correctness is unproven.

- Preserve an 18-case paid direct-private-reveal baseline against the selected
  runtime: ten controls pass and eight master/post-control knowledge assertions
  fail for printed Peek and Brutal Deceiver in original/copied states. Generic
  RevealHand Look/PeekAndReveal routing is in isolated source validation. Other
  callbacks and complete Commander correctness remain unproven. See
  engine/direct_private_reveal_gap.json and the preserved pending fixture.

- Activate generic controlled-player observation routing after all 45 live tests
  and 2,055 subtests pass, including repeated 1002/65/194/146 packaged families.
  All 934 focused source, 2,159 native, 3,633 gameplay and 256 serialization checks
  pass. Audit 527 frozen inputs, retained baseline methods, preserved failed phases,
  post-move readiness and construction of 32,636 starting identities with seven
  nonstarting meld exclusions. Six source families and ten existing classes change;
  one nested observer-capture class is added. Capture private audiences, enforce
  in-game/outside-game rights, preserve per-agent memories and separate public
  knowledge from owner display. No private-history copying or production card-name
  exceptions. Scripts/default AI remain unchanged. Structural graph fixtures and
  selected native checks do not certify all card/rules/AI or calibrated strategy.
  See engine/controlled_information_evidence.json.

- Validate the controlled-information candidate with 2,159 native tests, retaining
  all 2,117 and adding 42. All 934 focused source, independent packaged families
  of 1002/65/194/146, and broader gameplay/serialization of 3,633/256 pass. Audit
  2,165 production sources, six changed production source families and one added nested class,
  and 527 frozen implementation inputs. Scripts/default AI remain unchanged.
  Full live validation and activation remain pending; selected contracts do not
  certify every Commander interaction or calibrated strategy.
  See engine/controlled_information_packaged_evidence.json.

- Add source-validated controlled-information observation routing: all 934
  focused checks pass, retaining 892 and adding 42. Capture fresh inspection
  audiences before control changes; preserve per-agent memories and distinguish
  in-game from outside-game disclosures. Retain public reveal/return knowledge
  for original/current/dormant agents independently of optional owner display.
  Validate real paid control/inspection, copies, counters, failed payment, actor
  resources, stale/foreign contexts, current-view identity and structural cycles.
  Preserve native baseline and failed private/public reveal phases. Packaging and
  broader/full live gates remain pending; selected engine/scripts/default AI
  remain unchanged. Complete rules and calibrated strategy remain unproven.
  See engine/controlled_information_native_evidence.json.

- Record fourteen native controlled-information contracts: six proxy/payment/
  counter controls pass and eight rightful-observer/autonomous-choice checks fail.
  Real paid Worst Fears establishes control, paid Soothsaying resolves and Stifle
  prevents disclosure. The acting proxy learns three positions; the master and
  post-control original player retain zero. Preserve exact fixture, driver/input
  snapshots, native origins and reports. Require generic current in-game
  observation rights and captured recipients, without copying private histories
  or granting later controllers earlier hidden observations. Selected 2,117-method
  runtime remains active; complete rules and calibrated strategy remain unproven.
  See engine/controlled_information_gap.json and official rules 724.4/724.5a.

- Activate generic autonomous rearrange choices after all 45 live tests and
  2,013 subtests pass, including repeated 960/65/194/146 packaged families.
  All 892 focused source, 2,117 native, 3,591 gameplay and 256 serialization checks
  pass. Audit 523 frozen inputs, retained baseline methods, preserved native and
  fixture failures, post-move health and construction of 32,636 starting identities
  with seven nonstarting meld exclusions. Two production files/classes change;
  no classes are added. Native paid outcomes handle hybrid admission, X, targets,
  payment, privacy and deadline cleanup; a trusted native composition retains
  shared X. Scripts/default AI remain unchanged. Controlled/nested/mandatory
  contexts and calibrated strategy remain open; selected contracts and construction
  do not certify complete Commander correctness.
  See engine/autonomous_rearrange_evidence.json.

- Validate the autonomous rearrange candidate with 2,117 native tests, retaining
  all 2,090 and adding 27. All 892 focused source, independent packaged families
  of 960/65/194/146, and broader gameplay/serialization of 3,591/256 pass. Audit
  2,165 production sources, two changed production files/classes, no added classes,
  and 523 frozen implementation inputs. Scripts/default AI remain unchanged.
  Full live validation and activation remain pending; selected contracts do not
  certify every Commander interaction or calibrated strategy.
  See engine/autonomous_rearrange_packaged_evidence.json.

- Add source-validated generic autonomous rearrange admission and paid native
  outcome assessment. All 892 focused checks pass, retaining 865 and adding 27.
  Validate actual hybrid/full original/copied X selection, payment, known/empty/
  timing/budget controls, native color and target restrictions, private knowledge,
  X cleanup and recovery after expired previews. Preserve shared X in a trusted
  native composed life-gain effect. Restrict the library-size optimization to
  standalone own-library rearrangements. Preserve the two hybrid baseline failures
  and all three fixture compilation failures. Packaging and broader/full live
  gates remain pending; selected engine/scripts/default AI are unchanged.
  Complete Commander correctness remains unproven.
  See engine/autonomous_rearrange_native_evidence.json.

- Record a fourteen-case autonomous rearrange baseline: twelve pass and two
  original/copied hybrid choices fail. Full simulation announces and pays X=4;
  supplied paid-resolution and known/empty/timing/unaffordable decline controls
  pass. Preserve the original fixture compile failure and corrected API fixture.
  The hybrid admission filter excludes the activated information ability before
  planning. Generic admission, X and paid-outcome handling remain pending;
  selected 2,090-method runtime remains active. Complete rules/strategy unproven.
  See engine/autonomous_rearrange_gap.json.

- Activate deducible library-position retention after all 45 live tests and
  1,986 subtests pass, including repeated 933/65/194/146 packaged families.
  All 865 focused source, 2,090 native, 3,564 gameplay and 256 serialization checks
  pass. Audit 519 frozen implementation inputs, retained baseline methods,
  preserved failed phases, post-move readiness and construction of 32,636 starting
  identities with seven nonstarting meld exclusions. Transform prior knowledge
  through declared indices without hidden identity searches. Seven production
  files and ten existing classes change; two nested classes are added.
  Scripts/default AI remain unchanged. Other observers, all nested/rollback
  contexts, other library APIs and calibrated strategy remain open. Selected
  checks and construction do not certify complete card/rules/AI correctness.
  See engine/library_knowledge_transition_evidence.json.

- Verify library-transition knowledge with 2,090 native tests, retaining all
  prior 2,057 and adding 33. All 865 focused source checks, independent packaged
  families of 933/65/194/146, and broader gameplay/serialization of 3,564/256 pass.
  Audit 2,165 production sources, seven changed source files, two added nested
  classes and 519 frozen implementation inputs. Scripts/default AI stay unchanged.
  Preserve prior failures and exact-object refinement. Full live validation and
  activation remain pending; selected contracts do not certify complete rules
  or calibrated strategy. See engine/library_knowledge_transition_packaged_evidence.json.

- Add a source-validated observer-authorized library transition model: all 865
  focused checks pass, retaining 832 and adding 33. Preserve known outside
  positions through declared index transforms without scanning hidden final
  identities. Bounded exact-object scopes reject opaque resets, outside-card
  mutations, stale/foreign/closed/overflow contexts and inconsistent shapes.
  Validate copied-state independence, paid Soothsaying and actual shuffle damage.
  Preserve the failed timestamp-based prototype and exact-object refinement.
  Packaging and broader/full live validation remain pending; selected engine,
  scripts and default AI remain unchanged. Complete rules/strategy remain open.
  See engine/library_knowledge_transition_native_evidence.json.

- Record twelve native no-shuffle knowledge-transition contracts: eight
  original/copied Opt/Consider retention checks fail, while four direct-draw and
  fully-reannounced-order controls pass. Native card resolution succeeds; the AI
  loses deducible positions of previously inspected cards outside the new window.
  Preserve exact fixtures, driver/source snapshots, production origins and report
  digests. Require observer-authorized transitions rather than hidden-state scans
  or revision-count heuristics. The selected reviewed engine remains active;
  complete card/rules/AI coverage remains unproven.
  See engine/library_knowledge_transition_gap.json.

- Activate shared library inspection/order knowledge after all 45 live tests and
  1,953 subtests pass, including repeated 900/65/194/146 packaged families.
  All 832 focused source, 2,057 native, 3,531 broader gameplay and 256 serialization
  checks pass. Verify 516 frozen implementation inputs, retained baseline methods,
  preserved fixture corrections, post-move readiness and construction of 32,636
  starting identities with seven nonstarting meld exclusions. Preserve seen
  identities and private placements across known draws, copies and native
  replacement/trigger shuffles. Scripts/default AI remain unchanged. Further
  public deductions and other library-choice APIs remain open; selected tests
  and catalog construction do not certify complete rules or calibrated strategy.
  See engine/library_choice_knowledge_evidence.json.

- Validate the library-choice candidate with 2,057 native methods, retaining
  all 2,029 and adding 28. Independent packaged families pass 900/65/194/146 cases;
  broader gameplay/serialization pass 3,531/256. Verify all 2,165 production
  sources, 516 frozen implementation inputs and unchanged card scripts/default
  AI. Preserve initial seen identities, final private placements, public reveals,
  known draws, independent copied memory and actual replacement/trigger shuffle
  witnesses. Full live validation and activation remain pending; complete
  rules and strategy correctness remains unproven.
  See engine/library_choice_knowledge_packaged_evidence.json.

- Add source-validated shared initial-inspection and announced-library-order
  callbacks for scry, surveil and rearrange. All 832 focused checks pass, retaining 804 and adding
  28. Preserve original/copied paid top/bottom/mixed choices, countering, privacy
  and unavailable mana. Validate known draws, independent copied memory, exile
  replacements and immediate/later hidden shuffles. Production uses announced
  placements and current object identity, without card-name exceptions.
  Packaging and broader/full live validation remain pending; the selected
  information runtime remains active. Complete rules and strategy are unproven.
  See engine/library_choice_knowledge_native_evidence.json.

- Record 16 paid native scry/surveil contracts against the selected information
  runtime: six countering/privacy/unavailable-mana controls pass and ten original
  or pending-copy position-retention checks fail. Preserve exact compiled
  fixtures, driver snapshots, native production origins and report hashes.
  Native movement succeeds; resulting private position knowledge is missing.
  Link the official rules review and keep complete rules/strategy coverage open.
  See engine/library_choice_knowledge_gap.json.

- Activate reviewed generic information choices and resolving-zone scoring after
  all 45 live tests and 1,925 subtests pass, including repeated 872/65/194/146
  packaged families. All 804 focused source, 2,029 native, 3,503 broader gameplay
  and 256 serialization checks pass. Verify 512 frozen implementation inputs,
  source/baseline snapshots, original failure retention, post-move readiness and
  construction of 32,636 starting identities with seven nonstarting meld exclusions.
  Preserve private paid knowledge and resulting library order without counting
  public stack cards as private-zone information. Card scripts/default AI remain
  unchanged. Complete rules and strategy coverage remains open; scry/surveil
  knowledge retention is an unverified audit lead requiring native baselines.
  See engine/information_choice_evidence.json.

- Validate the corrected information candidate with 2,029 native methods,
  retaining all 1,971 and adding 58. Independent packaged families pass
  872/65/194/146 cases; broader gameplay/serialization pass 3,503/256, including
  the unchanged upstream safety test that failed in the first candidate.
  Verify 2,165 production sources, 512 frozen implementation inputs and unchanged
  scripts/default AI. Full live validation and activation remain pending;
  complete card/rules/AI correctness remains open.
  See engine/information_choice_packaged_evidence.json.

- Correct information scoring during resolving-stack transitions: public stack
  visibility cannot earn hand or library-position credit from a stale zone
  collection. Preserve authorized paid knowledge across copies. All 804 focused
  source checks pass, retaining 799 plus four regressions and the original
  unchanged upstream safety test. Preserve the unactivated 2,025-test candidate,
  its 3,498/3,499 broader result and four failing isolated baselines. A fresh
  2,029-test build, packaged and broader/full live validation remain pending.
  See engine/information_choice_native_evidence.json; full correctness remains open.

- Add a source-validated generic information correction: 799 checks retain 745 and
  add 54. Preserve chosen library order and retire matching position knowledge
  when a card is explicitly forgotten. Value only authorized hands, positions and
  faces; use bounded native paid outcomes so cast-trigger draws, granted mana and
  harmful cast damage remain part of decisions. Preserve the original 26/4-case
  baselines and later memory/material-context failures without weakening their
  expectations. Packaging and full runtime validation remain pending; selected
  engine, scripts and default AI are unchanged. Full-card correctness remains open.
  See engine/information_choice_native_evidence.json.

- Record two packaged information baselines against the selected inspection
  runtime: 26 cases pass 20/fail 6, and four own-library-order cases pass 2/fail 2.
  Preserve exact fixtures, driver/source snapshots, production origins and report
  digests. Distinguish working paid/private rule resolution from choice behavior;
  full simulation already casts a useful first Lens but also a redundant copy.
  Original and copied Spy Network resolution lose the chosen own-library order.
  Preserve an initial fixture compilation stop separately. An isolated correction
  is under validation; selected engine and full-card limits remain unchanged.
  See engine/information_choice_gap.json.

- Activate the reviewed inspection AI correction after 45 live tests and 1,867
  subtests pass, including repeated 814/65/194/146 packaged families. Retain every
  prior native method in the 1,971-test build; all 745 focused source, 3,445 broader
  gameplay and 256 serialization checks pass. Verify 505 frozen implementation
  inputs, exact preserved source phases, original baseline fixtures, post-move
  readiness and construction of 32,636 starting identities with seven nonstarting
  meld exclusions. Preserve disclosure, payment, countering and copied-memory
  controls. Card scripts and default AI remain unchanged. Information utility is
  a bounded heuristic; hand/library inspection strategy and full card/rules/AI
  correctness remain open. See engine/inspection_ai_evidence.json.

- Validate the inspection AI candidate with 1,971 native methods, retaining all
  1,938 and adding 33. Independent packaged families pass 814/65/194/146 cases;
  broader gameplay/serialization pass 3,445/256. Verify 2,164 production sources
  match the source-tested candidate, 505 frozen implementation inputs, preserved
  prior methods and unchanged card scripts/default AI. Extract the fixed fixture
  digest registry from build orchestration to meet the module-size standard.
  Full live validation and activation remain pending; the selected runtime stays
  unchanged. See engine/inspection_ai_packaged_evidence.json.

- Add a source-validated generic inspection AI correction: 745 focused checks
  retain 712 and add 33 paid activation, privacy, copied-memory, free-access and
  target-selection controls. Preserve the packaged 8-pass/12-fail baseline and
  failed source phases. Avoid redundant inspection, give free and remembered
  face access equal bounded value, and retain knowledge-distinct targets during
  pruning. Packaging and full runtime validation remain pending; selected engine,
  scripts and default AI profile are unchanged. Full rules/AI correctness remains
  open. See engine/inspection_ai_native_evidence.json.

- Activate the reviewed combat-role correction after all 45 live tests and 1,834
  subtests pass, with independent/repeated 781/65/194/146 packaged families.
  Retain every prior 1,902 native method in the 1,938-test build; all 712 focused
  source checks, 3,412 broader gameplay and 256 serialization tests pass. Preserve
  both frozen phases and the corrected origin-check failure before gameplay.
  Verify post-move health and construction of 32,636 starting identities with
  seven nonstarting meld exclusions. Only Combat.java/Combat.class change;
  scripts and default AI remain unchanged. Full card/rules/AI correctness stays open.

- Validate the separate combat-role build with 1,938 native methods, retaining
  all 1,902 and adding 36. Packaged families pass 781/65/194/146; broader gameplay
  and serialization pass 3,412/256. Only Combat.class changes. Preserve the initial
  Battle origin-lookup failure before gameplay, correct ControlGainEffect's class
  name and rerun the affected 146-case family successfully. Bind both 500-file
  phase manifests and the single worker-only correction. Scripts, default AI,
  production and Java expectations do not change during that correction.
  Full live validation and activation remain pending; the selected engine is unchanged.

- Correct independent combat-role removal in source. Losing planeswalker type
  preserves a valid block and its assignment order; losing creature type preserves
  a valid attacked planeswalker. Retain 676 focused tests and add 24 restoration
  plus 12 dual-role contracts: all 712 pass. Preserve six/twelve-case baselines
  with two/four failures and passing native-cost/countering controls. Extend the
  reviewed recipe to 1,938 native methods and the packaged family to 146. Fresh
  packaged/full validation remains pending; the selected runtime is unchanged.

- Add 24 paid defender-type restoration regressions to the packaged Battle family;
  all 134 methods pass. Cover Disenchant and Counterspell after native flash-enabled
  Song of the Dryads, including copied and nested pending stacks. Check unchanged
  defense/loyalty, correct player damage and unresolved original stacks. Preserve
  12-, 122- and 134-case phase evidence. Add fail-closed checks for missing or
  changed supplemental source reviews. Production and the 1,902-method build stay
  unchanged; all-card rules and strategy correctness remain open.

- Activate the reviewed defender-type correction after all 45 live tests and
  1,798 subtests pass, with repeated 781/65/194/110 packaged families. Preserve
  all 1,870 prior native methods in the 1,902-test build and retain 644 focused
  checks in the 676-test suite. Broader gameplay/serialization pass 3,376/256;
  only the completed recovery phase supplies broader success claims. Preserve
  the interrupted phase and restore temporary policy bytes. Verify 497 frozen
  inputs, health readiness and construction of 32,636 starting identities with
  seven nonstarting meld exclusions. Only Combat.java and Combat.class change;
  scripts and default AI remain unchanged. Full rules/AI correctness stays open.

- Validate the defender-type candidate with 1,902 selected native methods,
  retaining all 1,870 and adding 32. Independent packaged families pass
  781/65/194/110 cases; broader gameplay/serialization pass 3,376/256. Only
  Combat.class changes. Card scripts and default AI remain unchanged. Verify
  497 frozen inputs and 28 Battle-family production class origins. Full live
  validation and runtime activation remain pending.
  Preserve an interrupted broader phase without a terminal receipt; a separate
  completed recovery run supplies the broader validation evidence.

- Correct defender type history in source: remember legal type per attacking
  group, clone it across game copies, and retire it on departure or combat end.
  Use active combat groups for removal. Pass 676 focused source checks, retaining
  644 plus 32 new contracts; the 110-case Battle family retains 78 prior methods.
  Preserve 12/24/32-case baselines and the compiler-only fixture correction.
  Fresh packaged/full validation remains pending; selected runtime is unchanged.

- Record a defender type-loss damage gap: ten of twelve native probes pass;
  two wrongly damage the attacking player after an attacked Battle becomes a
  Forest. Use native flash-enabled Song payment/resolution and Counterspell
  controls. Preserve fixtures, drivers, exact failures and report/process hashes.
  Retain the initial compiler-only fixture stop and its API-receiver correction;
  no gameplay expectation changed. No further production fix is claimed.

- Activate the reviewed assigned-damage correction after all 45 live integration
  tests and 1,766 subtests pass, with repeated 781/65/194/78 packaged families.
  Preserve 1,854 prior native methods in the 1,870-test build and retain 628
  focused checks in the 644-test source suite. Broader gameplay/serialization
  pass 3,344/256; 496 implementation inputs remain frozen. Health and catalog
  checks pass with 32,636 starting identities and seven nonstarting meld results.
  Only Combat.java and Combat.class change; scripts and default AI are unchanged.
  Internal assignment snapshots are not player priority windows. Full card,
  rules and strategy correctness remain open.

- Validate the assigned-damage candidate with 1,870 selected native tests,
  retaining all 1,854 prior methods and adding 16. Independent packaged families
  pass 781/65/194/78 cases; broader gameplay/serialization pass 3,344/256.
  Only Combat.class changes; card scripts and default AI remain unchanged.
  Verify 496 frozen inputs and 25 Battle-family production class origins.
  Full live validation and runtime activation remain pending.

- Correct internal assigned-damage snapshots in source by excluding absent
  defender stand-ins from damage recipients. Pass 644 focused checks, retaining 628
  plus 16 new contracts; the 78-case Battle family retains 62 prior methods. The
  initial 636-case pass and expanded 44-case baseline remain preserved. Paid Jitte
  controls verify absent/live targets, real blockers and grouped damage events.
  Fresh packaged/full validation remains pending; selected runtime is unchanged.

- Record an internal post-assignment combat snapshot gap: 32 of 36 probes pass;
  four copied absent-defender recipients fail strict mapping. Preserve exact
  fixtures, driver, native report hashes, stack trace and finished process receipt.
  Existing 28 removed-defender contracts and four new valid-recipient controls
  pass. This checkpoint is internal, not a player priority window; autonomous
  gameplay impact remains unmeasured. No new production correction is claimed.

- Activate the reviewed removed-defender correction after all 45 live integration
  tests and 1,750 subtests pass, including repeated 781/65/194/62 packaged
  families. Preserve the prior 1,826 native methods in the 1,854-test build,
  all 600 focused source methods in the 628-test suite, and 495 frozen inputs.
  Broader gameplay/serialization pass 3,328/256. Health and catalog checks pass;
  32,636 starting identities construct with seven nonstarting meld exclusions.
  Only Combat.java and Combat.class change in production. Scripts and default
  AI remain unchanged; full card, rules and strategy correctness remain open.

- Validate the removed-defender candidate with 1,854 selected native tests,
  retaining every prior 1,826 method and adding 28. Independent packaged families
  pass 781/65/194/62 cases; broader gameplay and serialization pass 3,328/256.
  Only Combat.class changes; card scripts and default AI are unchanged. Verify
  495 frozen inputs and restore temporary test-policy bytes. Full live validation
  and runtime activation remain pending.

- Correct removed-planeswalker assignment in source: preserve departed defender
  type/protector context and exclude the missing planeswalker from lethal damage
  recipients. Pass 628 focused source checks, retaining 600 plus 28 new contracts.
  Keep all 34 prior Battle methods in the expanded 62-case family. Fresh packaged
  and full validation remain pending; the selected engine is unchanged.

- Confirm a removed-planeswalker trample-assignment gap: the 28-case baseline
  passes 24 and fails four native/copied blocked/unblocked player-damage
  contracts. Preserve exact fixtures, drivers, report hashes and finished process
  receipts. Eight paid-equip Jitte controls pass; no extra damage-trigger defect
  is claimed. The selected runtime remains unchanged pending correction gates.

- Correct first-strike assignment history in combat snapshots: map live
  combatants into copied physical cards and departed incarnations through the
  native historical-card copier, with independent containers. Pass 600 source
  tests, retaining 566 and adding the 34-case Battle family (ten retained external
  cases plus 24 new boundaries). Resolve the original one and expanded six copy
  failures while retaining legitimate double strike and paid keyword changes.
  Preserve the initial Checkstyle stop and its two-import-only fixture correction.
- Activate the reviewed strike-history build after 1,826 selected native tests,
  independent 781/65/194/34 packaged families, 3,300 broader gameplay tests, 256
  serialization tests and 45 live tests with 1,722 subtests pass. Verify 494 frozen
  inputs and construction of 32,636 starting identities with seven nonstarting
  meld exclusions. Only Combat.java and Combat.class change in production; no
  classes are added or removed. Card scripts, AI hints, default profile and damage
  assignment policy remain unchanged. Preserve broad-test policy originals and
  restore them byte-for-byte after independent compilation. Full card, combat,
  rules and strategy correctness remain open.

- Record a first-strike combat-copy gap: an initial probe passes nine controls
  and fails copied strike history; expanded probes pass 18 and fail six contracts.
  Copies can assign normal damage a second time, kill a native-surviving creature,
  or incorrectly defeat a Battle. Preserve original and expanded exact fixtures,
  drivers, failed expectations and report hashes. Native controls, double strike,
  paid gained-double-strike and departed-incarnation controls pass. A separate
  source candidate is under validation; no corrected packaged engine is claimed.

- Add a separate ten-case packaged Battle combat/Siege-defeat pytest family:
  defender selection, mapped combat, defense damage, copied-world isolation,
  lethal combat, paid Fog, Hexmage sacrifice/free transformed casting, Stifle and
  removal in response to defeat. All ten pass against the unchanged reviewed
  engine with 24 production class origins verified and the normal 90-second worker
  limit. Preserve an initial four-case fixture failure caused by ending combat
  during phase advancement, plus the corrected ten-case pass; no engine defect or
  production correction is inferred from that fixture mistake.
- Pin the new Java fixture and existing TestNG dependency, bind review manifests
  to worker receipts, and add six offline tests rejecting changed or redirected
  inputs. Pass 426 offline tests with 520 subtests. All prior 488 implementation
  inputs remain unchanged; 493 inputs include the five new harness files. The
  existing 1,792 native build regressions and prior 44-test full integration run
  remain separate evidence; this test-only extension runs the new pytest family.
  Full card, combat and AI correctness remain open.

- Correct generic Battle-protector restoration in copied games and native LKI,
  replacing the battlefield-only path with shared mapped references/public views.
  Pass 566 source tests with 17 Battle contracts. Preserve the original three and
  expanded nine old-runtime failures, including native Portent Tracker adding
  rather than removing a defense counter with historical target metadata.
  Cover paid activations, Stifle, copied pending entry, departed protectors,
  nested history and mutation isolation. Supplied actions and metadata do not
  certify autonomous strategy or complete combat.
- Activate the reviewed Battle-protector build after 1,792 selected native tests,
  independent 781/65/194 packaged families, 3,266 broader gameplay tests, 256
  serialization tests and 44 live tests with 1,688 subtests pass. Verify all 488
  frozen inputs and construction of 32,636 starting identities with seven
  nonstarting meld exclusions. Three production sources and six packaged classes
  change; none are added or removed. Card scripts, AI hints and the default
  profile are unchanged. Full card, rules and strategy correctness remains open.

- Record a historical Battle-protector gap after real paid Invasion of Gobakhan:
  native LKI and copied historical records lose the protector, and the native
  OppProtect predicate changes. Preserve three failed contracts, three passing
  controls, exact reproductions and raw report hashes. This does not establish
  complete combat or defeat-trigger behavior; no production correction is included yet.

- Correct shared phasing and imprint snapshots: include phased-out physical
  cards/attachments, map phase owners and direct/suppression flags, and restore
  imprinted references/public views without sharing containers. Pass 549 source
  cases with 25 new contracts. Preserve the initial 532-case success, expanded Out of
  Time failure and six isolated imprint failures before their generic correction.
  Include paid native phasing, untap, Out of Time/Disenchant, token/static-source
  and Chrome Mox mana witnesses; document explicit metadata/setup limits.
- Activate the reviewed phasing/imprint build after 1,775 selected native tests,
  separate 781/65/177 packaged families, 3,249 broader gameplay tests, 256 serialization
  tests and 44 live tests with 1,671 subtests pass. Verify all 486 frozen inputs and
  construction of 32,636 starting identities with seven nonstarting meld exclusions.
  Three production sources and six packaged classes change; no classes are added
  or removed. Card scripts, AI hints and the default profile remain unchanged.
  Preserve original failed expectations and reproductions in the resolved gap.
  Full card, rules and strategy correctness remains unproven.

- Record a copied-phasing lookup gap after real paid Slip Out the Back: five
  contracts cannot map the phased-out creature. Preserve three passing controls,
  exact reproduction sources, raw report hashes and an initial native-default
  fixture correction. Later phase-owner/view/nested/phase-in assertions are not
  reached; no phasing production correction is included yet.

- Correct generic Gift-recipient copying through the shared card snapshot after
  entity maps exist, including native last-known information. Pass 524 source cases
  with 22 new contracts and real paid permanent/instant Gift witnesses. Preserve
  the original eight and expanded fourteen failed contracts, eight expanded
  controls and the explicit-stack fixture correction separately. Clarify that
  Long River Lurker baseline cases are raw metadata, not printed Gift abilities.
- Activate the reviewed Gift-context build after 1,750 selected native tests,
  separate 781/65/152 packaged families, 3,224 broader gameplay tests, 256 serialization
  tests and 44 live tests with 1,646 subtests pass. Verify all 484 frozen inputs and
  construction of 32,636 starting identities with seven nonstarting meld exclusions.
  Exactly two production sources/classes change, with no added or removed classes.
  Card scripts, AI hints and the default profile remain unchanged. Preserve the
  original failed expectations and reproductions in the resolved Gift gap report.
  Full card, rules and strategy correctness remains unproven.

- Record a separate Gift-recipient copy gap against the reviewed choice-context
  runtime: six copied zones lose the recipient, a historical reference retains an
  original-world player, and a copied native Gift effect creates no Fish for its
  copied recipient. Preserve all eight failed contracts, three passing controls,
  exact reproduction sources and raw report hashes. The supplied native payment
  and effect control does not establish full casting or autonomous AI choices.
  No Gift-context production correction is included yet.

- Correct shared choice-context copying across all zones and native last-known
  information. Preserve mapped players/cards/groups, independent containers and
  public presentation separately from secret rules values. Pass 502 source cases
  with 32 new contracts after preserving the original four failures and expanded
  24 failing contracts with five controls. Include real paid Persecute resolution,
  hidden/revealed choices, eliminated players and historical/self-reference cases.
  Full card/rules/strategy correctness remains open.
- Activate the reviewed choice-context build after 1,728 selected native tests,
  separate 781/65/130 packaged families, 3,202 broader gameplay tests, 256 serialization
  tests and 44 live tests with 1,624 subtests pass. Verify all 482 frozen inputs and
  catalog construction of 32,636 starting identities with seven nonstarting meld
  exclusions. Three production sources and five packaged classes change; no classes
  are added or removed. Card scripts, AI hints and the default profile are unchanged.
  Preserve original failed expectations and reproduction sources in the resolved
  choice-copy gap report. Full card, rules and strategy correctness remain unproven.

- Record an adjacent open choice-copy gap: four focused checks fail for stack
  colors, named cards and chosen-player state, including a native Persecute
  resolution checkpoint. Its supplied native outcome control passes. Preserve
  exact reproduction sources and keep these failures separate from the passing
  mass-tap regression suites.

- Add generic paid mass tap/untap type and direction planning, ordered
  player-role replay and local native branch choices. Preserve chosen type/type2
  state across all copied zones, including resolving spells. Pass 470 source
  cases with 32 new contracts, including countered/unaffordable costs, changed
  types, copied streams, six-zone state isolation and repeated discard payments.
  Preserve six reviewed-runtime failures, nine controls and fixture corrections
  separately. Fix the introduced player-role binding defect.
- Activate the reviewed mass-tap build after 1,696 selected native tests, separate
  781/65/98 packaged families, 3,170 broader gameplay tests, 256 serialization tests
  and 44 live tests with 1,592 subtests pass. Verify all 480 frozen inputs and
  catalog construction of 32,636 starting identities with seven nonstarting meld
  exclusions. Six production sources change and one helper is added; card scripts,
  AI hints, identity reviews and the default profile are unchanged. Full card,
  rules and strategy correctness remain unproven.

- Add generic activated single-card discard planning and full simulation
  choice replay. Validate the chosen card through native cost approval and payment,
  copy identities independently and reject stale actors, targets, timestamps and
  changed cost shapes. Pass 438 source tests with 40 new contracts, including real
  combined-cost activations and oversized native-payment fallback. Preserve fixture
  corrections, unchanged policy observations and a fixed candidate boundary
  regression separately.
- Activate the reviewed discard-payment build after 1,664 selected native tests,
  separate 781/65/66 packaged families, 3,138 broader gameplay tests, 256 serialization
  tests and 44 live tests with 1,560 subtests pass. Verify all 476 frozen inputs and
  catalog construction of 32,636 starting identities with seven nonstarting meld
  exclusions. Eight production sources change and one helper is added; card scripts,
  AI hints, identity reviews and the default profile are unchanged. Full card,
  rules and strategy correctness remain unproven.

- Correct generic tap/untap choices and paid one-follow-up planning, preserving
  unique chained targets and rejecting wasted or unaffordable untaps. Defer
  incomplete alternative searches instead of claiming an improvement. Isolate
  preview cache lifetimes to release projected games and preserve live decisions.
  Pass 398 source tests with 26 new interaction, payment, profile, search-bound and
  cache contracts. Preserve the reproduced heap exhaustion and partial-comparison
  failure separately. Centralize shared packaged receipt assertions.
- Activate the reviewed untap build after 1,624 selected native tests, separate
  781/65/26 packaged families, 3,098 broader gameplay tests, 256 serialization tests
  and 44 live tests with 1,520 subtests pass. Verify all 473 frozen inputs and
  catalog construction of 32,636 starting identities with seven nonstarting meld
  exclusions. Exactly four production sources/classes change; card scripts,
  AI hints, identity reviews and the default profile are unchanged. Full card,
  rules and strategy correctness remain unproven.

- Preserve runtime cost modifiers and nested expressions across ability copies,
  previews and pre-grant hand-spell assessments. Share native play-effect modifier
  setup and apply casting-cost rules during AI admission. Classify generated hand
  copies consistently for simulation and hybrid safety, retaining their originals
  without hand-card loss compensation. Pass 372 source checks with 27 new cases
  after reproducing 14 engine failures and 13 controls. Cover Kefnet first-draw
  admission across three pilot profiles and supplied-target Ogre branch rules.
  Preserve fixture and Checkstyle corrections separately from engine failures.
- Activate the reviewed cost-modifier build after 1,598 selected native tests,
  separate 781/65 packaged families, 3,072 broader gameplay tests, 256 serialization
  tests and 43 live tests with 1,494 subtests pass. Verify all 467 frozen inputs
  and catalog construction of 32,636 starting identities with seven nonstarting
  meld exclusions. Exactly seven production sources/classes change; card scripts,
  AI hints, identity reviews and the default AI profile are unchanged. Complete
  card, rules and strategy correctness remain unproven.

- Preserve effect-granted casting costs during AI preview reconstruction after
  reproducing seven engine failures across twelve focused contracts. Pass all
  345 source checks, covering replacement-cost affordability, life, discard,
  mandatory sacrifice and buyback, with native Wizard's Spellbook resolution as
  a retained control. Preserve original fixture setup/compiler and harness-path
  failures separately; none count as engine failures.
- Activate the reviewed granted-cost build after 1,571 selected native tests,
  separate 781/38 packaged families, 3,045 broader gameplay tests, 256 serialization
  tests and 43 live tests with 1,467 subtests pass. Verify all 460 frozen inputs
  and catalog construction of 32,636 starting identities with seven nonstarting
  meld exclusions. Exactly one production source and class change; card scripts,
  archive identity reviews and the default AI profile are unchanged. Complete
  card, rules and strategy correctness remain unproven.

- Resolve generic free-casting behavior for preserved mana-waiver modifiers, native
  effect permissions, simulated copies that retain their original card, and
  target pruning that distinguishes effective text. Pass 333 selected source
  checks, including 26 new cases; remove only Trait Doctoring's All-AI exclusion,
  retaining its Random hint and Oracle instructions. Activate only after 1,559
  selected native tests, separate 781/26 packaged families, 3,033 broader gameplay
  tests, 256 serialization tests and 43 live tests with 1,455 subtests pass.
  Preserve all 457 frozen inputs and catalog construction of 32,636 starting
  identities with seven nonstarting meld exclusions. Retain the old identity
  reviews while binding unchanged aliases and companion conditions to the new
  archive. Complete card/AI/rules correctness remains unproven.
- Add a separate bounded packaged-native family and a shared runner, preserving
  the existing 781 cases and 90-second worker limit. Detect changes to shared
  Python execution sources as well as the worker entry point.

- Add nine controlled cipher-casting regressions for real copy resolution,
  countering, decline followed by later casting, repeated hits, changed
  controllers and temporary noncreature encoders, with copied-game controls.
  Pass all nine against packaged production classes after 34 research cases.
  Preserve initial failures at an unscripted AI card-selection boundary.
- Activate the test-expanded reviewed runtime after 1,533 selected native tests,
  781 packaged interaction cases, 3,007 broader gameplay tests, 256 serialization
  tests and 42 live tests with 1,429 subtests pass. Verify all 444 frozen inputs
  and catalog construction of 32,636 starting identities with seven nonstarting
  meld exclusions. All 2,160 production sources, packaged classes, card resources
  and AI hints are unchanged. Default AI admission, wider compositions and full
  card correctness remain unproven.

- Copy both ends of encoded relationships before recalculating static abilities.
  The generic GameCopier correction preserves cipher combat triggers for one or
  multiple encoded cards without changing card scripts or AI restrictions.
- Pass 298 selected source-native checks, including 25 paid cipher/timing/lifetime
  cases. Their identical packaged baseline passes 20 controls and fails five
  copied-state cases. Cover zone roundtrips and copied mutation independence.
  Preserve the passing 296-case proof before the stronger independence controls.
- Activate the reviewed cipher-link build only after 1,524 selected native tests,
  772 packaged interaction cases, 2,998 broader gameplay tests, 256 serialization
  tests and 42 live tests with 1,420 subtests pass. Preserve all 443 frozen inputs
  and the 32,636-identity construction probe with seven nonstarting meld exclusions.
  Cipher-copy casting, further compositions, AI admission and full card correctness
  remain unproven.

- Reconcile the project plan with the pinned Forge 2.0.16 runtime, current
  construction evidence and implemented fixed-pod batches. Keep historical gap
  counts separate from pending card/AI coverage, scheduling and diagnostics.

- Resolve modal text changes against parent card or spell targets through a
  shared lookup used by native effects and AI word variants/signatures. Review
  only Crystal Spray's All-AI hint; preserve Oracle text and all other AI flags.
- Pass 273 selected source-native checks including 29 paid real-card scenarios.
  Their identical packaged baseline passes 15 controls and fails 14 cases. Cover
  both word modes on permanents and stack spells, draws and draw restrictions,
  invalid targets, cast triggers, expiration and copied-game independence.
- Preserve the 269/273 draft and native traces that exposed an incorrect test
  expectation: the AI destroys four opposing Mountains instead of one Swamp.
  Corrected tests assert the word map and actual victims. Activate the reviewed
  build only after 1,499 selected native tests, 747 packaged interaction cases,
  2,973 broader gameplay tests, 256 serialization tests and 42 live tests with
  1,395 subtests pass. Preserve all 441 frozen inputs and the 32,636-identity
  construction probe with seven nonstarting meld exclusions. Full card and AI
  correctness remain unproven.

- Track explicitly disclosed absolute library positions through the generic
  LookAt handler. Restrict positional disclosure to exact top/bottom qualifiers;
  preserve private recipients and avoid inferences from identity-only, filtered
  or mixed selections. Keep remembered positions through verified interleaved
  draws and invalidate them after hidden reorders.
- Pass 244 source-native checks including nineteen expanded inspection cases.
  Their identical packaged baseline passes nine controls and fails ten cases.
  Preserve the passing 238-case draft and the 243/244 draft caught by the stronger
  one-card-library conjunction case. The corrected source passes all cases.
- Move the pending Spy Network fixture into engine/tests and bind its old inputs
  to preserved local snapshots. Card scripts and AI flags are unchanged. The
  reviewed runtime passes 1,470 selected native tests, 718 packaged interaction
  cases, 2,944 broader gameplay tests, 256 serialization tests and 42 live tests
  with 1,366 subtests. Preserve all 436 frozen inputs and the construction probe
  of 32,636 starting identities with seven nonstarting meld exclusions. Complete
  card correctness and information-spending AI policy remain unproven.

- Record a preexisting Spy Network inspected-draw knowledge gap with five identical
  packaged witnesses on both prior and corrected modal runtimes: three controls
  pass and two inspected-draw cases fail. Verify unchanged inspection/memory
  classes and card script; keep the fixture pending and outside passing totals.

- Add generic mode-before-target paid-outcome planning and child text-word variants.
  Replay paid choices through mapped copies and full-simulation resolution modes.
  Evaluate supported normal paid casts before a wait-for-buyback preference while
  retaining no-benefit and payment checks.
- Pass 225 selected source-native checks, including 26 expanded mode cases and a
  forced-mode budget witness. The identical 26-case packaged baseline passes eight
  controls and fails eighteen cases.
  Preserve the 76/78, 89/91 and corrected 91/91 drafts and all raw reports.
- Remove only the All-AI hints from Whim of Volrath, Mind Bend and Spectral Shift
  after the source witnesses pass; preserve their Oracle text. Move the original
  pending fixture into engine/tests with its old baseline inputs frozen locally.
  The fresh 1,450-test build and source/resource/class audit pass, but activation
  is held after a stronger native API witness times out while preparing 32 legal
  forced modes. Preserve all 432 frozen inputs and the five-second native /
  thirty-second worker timeout receipts. The initial no-target fixture is not an
  engine finding. The branch-feasibility correction passes all 225 source checks;
  the same forced-mode witness takes 6 ms in that retained run. Activate only the
  corrected build after 1,451 selected native, 699 packaged, 2,925 broader gameplay,
  256 serialization and 42 live tests with 1,347 subtests pass. Preserve all 433
  frozen inputs and the 32,636-identity construction probe with seven nonstarting
  meld exclusions. Complete card correctness remains unproven.

- Correct the held private-knowledge candidate with explicit disclosed library
  positions, structural-order revisions, tracked top/bottom draws and mapped
  per-agent position state. Hidden reorders invalidate positions; generic identity
  reveals do not disclose positions. Keep durable knowledge across turn boundaries
  while clearing per-turn planning reservations.
- Pass 189 source-native checks, including sixteen expanded Bauble/knowledge cases
  and three retained opening replays. The earlier runtime passes nine of sixteen
  cases and the held candidate eight; preserve both per-case baselines.
- Remove only Mishra's Bauble's All-AI exclusion after native paid peek, delayed
  draw, copied resolution and actual heuristic/hybrid/full casts pass. Oracle text
  is unchanged. Activate after 1,424 selected native, 672 packaged, 2,898 broader
  gameplay, 256 serialization and 42 live tests with 1,320 subtests pass. Bind all
  428 reviewed inputs and the 32,636-identity catalog probe with seven nonstarting
  meld exclusions. Keep the earlier failed candidate and reports preserved.
- Migrate Bauble's test-only AI metadata expectations to normal AI eligibility
  and broad permanent discovery, retaining narrow discovery checks for excluded
  cards. Fifteen paired packaged checks pass on the candidate and fail the two
  expected old-archive assertions. Preserve the first broader failure and policy
  prototype; no production Java changes are introduced by this test migration.

- Prepare generic private-knowledge lifetime and agent-snapshot handling. Preserve
  public hand-return identities, filter old incarnations without mutating source
  memory during reads, and map base and stacked control-agent knowledge separately.
- Pass 170 source-native checks, including eighteen new knowledge witnesses. Their
  identical packaged baseline passes ten controls and fails eight cases. Preserve
  two held drafts caught by additional public-return and controller-layer controls.
  The fresh 1,405-test build and source/resource audit pass, with 424 frozen inputs.
  Hold activation after the broader run passes 2,878 of 2,879 gameplay cases and
  further paired checks expose a library-knowledge regression. Preserve the failed
  build and exact frozen inputs; the reviewed text-choice runtime remains active.
- Reproduce the landless Bauble opening at three seeds on both baseline and
  candidate. A reversible All-AI-hint removal makes the three source replays act;
  no permanent override or smoke-test waiver has been applied.
- Preserve paired library-knowledge results: both engines pass six of nine cases,
  but the candidate fixes shuffled-draw identification and loses a legitimately
  tracked unshuffled draw. Further position and shuffle handling is required.
- Record a separate composed-text policy gap: five full-mode controls pass and six
  heuristic/hybrid casts decline. Keep its fixture pending, outside passing totals.
  A reversible three-card hint-only diagnostic preserves the same five-pass/six-fail
  result with unchanged production Java; removing exclusions alone is insufficient.

- Cap retained sick-creature value during response previews at its original value.
  Protect an established creature without treating an unusable temporary pump as
  immediately valuable. Use private LKI snapshots for reference valuation.
- Pass fifty-four source-native cases. The identical sixteen-case earlier packaged
  baseline passes six controls and fails ten cases. Preserve the intermediate
  1,386-test build and audit as held evidence after its additional timing failure.
  Activate the corrected build after 1,387 selected native tests, its source/resource
  audit, 635 packaged cases, 2,861 broader gameplay tests, 256 serialization tests,
  and 42 live tests with 1,283 subtests pass. All 422 frozen inputs remain unchanged;
  catalog construction covers 32,636 starting identities and seven nonstarting meld
  results. The earlier candidate builds remain held and retain their evidence.

- Correct generic word-choice filtering for creature/land plurals, negated words
  and current text after an irregular-plural rewrite. Preserve whole compound types.
- Retain established-permanent value during response previews, including protection
  of a summoning-sick creature. Keep ordinary and new-creature timing controls and
  one evaluator context across nested previews.
- Share full-simulation time across independent root candidates, preserving scored
  completed branches and the policy against spending mana on unavailable-only gains.
- Pass fifty-three source-native text-change cases, including fifteen new lexical,
  three-mode play and timing witnesses. Their identical packaged baseline passes
  five controls and fails ten cases. Keep the prior 38-case comparison separate.
- Hold the earlier predecessor despite its passing packaged/live/catalog gates.
  The intermediate 1,386-test revision passed its selected tests and audit, then
  remained held after the additional temporary-pump timing regression failed.

- Add a text-choice candidate using generic legal word/target variants, bounded
  paid native forecasts and immutable choice replay across ability/game copies.
  Keep plans separate from rules text and bind them to the activator and targets.
- Preserve full-mode words through runtime child choices, modal spells and copied
  pending stacks. Rebuild deterministic entwined mode chains before replaying
  targets and preserve announced entwined targets at native cast time.
- Verify thirty-eight source-native cases: thirty-three root witnesses and five
  composition cases, including four native prompts and four mana paid for a
  supplied entwined cast. Strengthen actor/target checks with successful replay
  controls inside resolution. Preserve decline and instant-deferral controls.
- Reject the earlier 1,366-test candidate after three composition failures before
  activation. The revised trusted build passes 1,371 selected native tests and its
  source/resource audit, 619 packaged cases, 2,845 broader gameplay cases,
  256 serialization tests, 42 live tests with 1,267 subtests and catalog validation.
  Keep that earlier revision held after a native control confirms a plural-word AI
  pruning gap; the corrected revision is validated above. Its identical source-native
  baseline passed fourteen controls and failed twenty-four cases. Supplied entwine
  costs do not prove optimal cost selection.
- Five root-card scripts remove only obsolete All-AI hints. Preserve Oracle text,
  random-deck hints. Preserve the original twelve-case gap as historical evidence,
  with explicit corrected test expectations and limits on the resolved scope.

- Preserve the initial twelve-case text-change finding: six supplied-word controls
  passed and six admission/picker expectations failed. The expanded tested fixtures
  now live under engine/tests, with the original finding and corrected expectations
  retained in the resolved gap record.

- Implement a generic Empower AI handler using bounded paid native outcomes.
  Correct its generated description to name loyalty counters. Verify twenty-four
  supplied-state cases spanning all five root-ability cards: baseline fifteen
  controls pass and nine cases fail; the correction passes all twenty-four.
  Native costs, replacements and harmful-action refusals are included.
- Activate the reviewed Empower engine after 1,333 selected native tests, 581
  packaged cases, 2,807 broader gameplay tests, 256 serialization tests, 42 live
  tests / 1,229 subtests and the refreshed catalog probe pass. The Python quality
  gate passes 416 offline tests / 520 subtests. Audit two modified Java sources
  and one new handler; card resources and the default AI profile are unchanged.
  Optional-trigger, complete card and optimal-AI behavior remain unverified.

- Implement a shared `SwitchBlock` AI handler using bounded paid native forecasts.
  General Jarkeld and Sorrow's Path can select beneficial swaps and reject harmful,
  self-lethal or ineffective actions in the tested scenarios. Remove only their
  obsolete All-AI exclusion hints; preserve random-deck exclusions and Oracle text.
- Preserve live-card block histories through the existing last-known-state mapper
  when copying games. Sorrow's Path previously paid its simulated tap trigger but
  its copied effect could not find the attackers, so its forecast lost the benefit.
  Test independent copied records and pending tap-trigger/swap snapshots.
- Verify twenty-two combat cases: baseline nine failures and thirteen controls,
  corrected twenty-two passes. Cover native costs/damage, actual choices and
  refusals in three modes, illegal flying cross-blocks and original-state isolation.
  General Jarkeld's full-mode choice already worked in the baseline.
- Promote ten unchanged sacrifice-cost witnesses into native and packaged tests.
  Activate after 1,309 selected native tests, 557 packaged cases, 2,783 broader
  gameplay tests, 256 serialization tests, 42 live tests / 1,205 subtests, the
  refreshed catalog construction probe, and the Python quality gate (416 offline
  tests / 520 subtests). Rebind alias/companion reviews only after unchanged-script
  verification. These checks do not certify full card, combat or optimal-AI support.

- Record a directly verified packaged `SwitchBlock` AI registry gap: the root
  ability used by General Jarkeld and Sorrow's Path selects `CannotPlayAi` and
  returns `CantPlayAi`. Separate this handler finding from unverified combat and
  simulation-planner behavior; native combat witnesses are the next step.
- Preserve ten passing source-only discover sacrifice witnesses for payment
  timing, draw timing, casting prohibitions, land versus nonland sacrifice under
  Yasharn, and exile replacement. Keep them outside selected and packaged counts.
- Promote eight unchanged discover tax witnesses into trusted native and Java 17
  packaged regressions. Activate after 1,277 selected native tests, 525 packaged
  cases and the Python quality gate (416 offline tests / 520 subtests). All 2,155
  production sources, 18,033 packaged classes, card resources and default AI
  configuration remain verified unchanged. Previous broad/live/catalog receipts
  retain their original scope and were not rerun for this test-only promotion.

- Preserve eight further passing source-only discover cost witnesses for payable
  and unpayable Thalia, Trinisphere and Damping Sphere costs, actual mana payment
  and combined cost ordering. Exclude the initial Checkstyle-stopped run and stale
  unrelated reports. These cases remain outside selected and packaged totals.

- Promote eight unchanged discover casting-form witnesses into trusted native and
  packaged Java 17 tests. Cover exile restrictions, modal/adventure spell limits,
  split mana values, adventure resolution, and excluded fuse/aftermath forms.
- Activate the test-only casting build after 1,269 selected native tests, 517
  packaged cases and the Python quality gate (416 offline tests / 520 subtests).
  Verify all 2,155 production sources, 18,033 packaged classes, card resources and
  default AI configuration unchanged. Earlier broader/live/catalog receipts retain
  their original scope and were not rerun for this test-only promotion.

- Preserve eight further passing source-only discover casting witnesses for
  casting restrictions, modal/adventure limits, split-card values, adventure exile,
  and excluded fuse/aftermath forms. Use native paid entry and a pending Lightning
  Bolt for the Failure half. Keep these outside selected and packaged totals.

- Promote the eight unchanged Imposter Mech / Pantlaza vehicle witnesses from
  pending source validation into the trusted native build and packaged Java 17
  matrix. Cover crew, uncrewed zero toughness, departure, copied-game independence
  and native crew expiry after Time Stop.
- Activate the test-only vehicle build after 1,261 selected native tests, 509
  packaged cases and the Python quality gate (416 offline tests / 520 subtests).
  Verify all 2,155 production sources, 18,033 packaged classes, card resources and
  default AI configuration unchanged. Preserve previous broader/live/catalog
  receipts with their original scope rather than relabeling them as rerun checks.

- Preserve eight additional passing source-only Imposter Mech / Pantlaza witnesses
  for crew, zero toughness, departure, copied-game independence and native expiry.
  They found no failure in that scope and remain outside selected, packaged and
  live counts.
- Promote six unchanged Pantlaza last-known-toughness witnesses from pending source
  validation into the trusted native build and packaged Java 17 regression suite.
  Preserve earlier source-only evidence and its historical path. The test-only
  promotion verified all 18,033 packaged classes and 2,155 production sources
  unchanged before the separately reviewed gameplay correction.
- Normalize a negative discover action result to zero under CR 107.1b without
  changing negative toughness for other calculations. Native paid Grasp of Darkness
  and Dismember distinguish zero controls from the two reproduced negative-value
  failures, including copied games. The correction passes all four witnesses.
  No card script or AI configuration changes are required.
- Activate the discover correction after 1,253 selected native tests, 501 packaged
  rules cases, 2,727 broader gameplay tests, 256 serialization tests, 42 live tests
  with 1,149 subtests, and the refreshed catalog construction probe. The Python
  quality gate passes 416 offline tests with 520 subtests. These checks do not
  certify full discover, casting, card or AI correctness.

- Preserve six additional source-only Pantlaza toughness/discover witnesses, including
  source departure, later responses, copied games and Time Stop in the original
  world. All six pass with no failure found in that scope. Keep their pending
  fixture and evidence separate from selected/packaged/live release counts until
  packaged regression integration. No new production change is inferred from them.

- Track declared optional cast actions after native casting succeeds, using existing
  per-ability turn history. Prohibited, declined and failed casts preserve the
  opportunity; countering an already cast spell does not refund it. Emet-Selch's
  script declares the marker; Oracle text is unchanged.
- Roll back effects prepared by PlayEffect when its cast callback declines or fails.
  The earlier prototype left an exile replacement after cancellation; the corrected
  sixteen-case suite passes cancellation cleanup and copying afterward. Cover native
  mana and sacrifice payments, restrictions, counterspells, copied pending casts,
  independent history, source movement and real cleanup/extra turns. The preceding
  runtime fails seven cases and passes nine controls; the correction passes all sixteen.
- Activate the cast-history runtime after 1,243 selected native tests, 491 packaged
  cases, 2,717 broader gameplay tests, 256 serialization tests, 42 live tests / 1,139
  subtests and the Python quality gate (416 offline tests / 520 subtests).
  The catalog constructs 32,636 starting identities and separately classifies seven
  nonstarting meld results. Rebind unchanged alias/companion scripts to the new
  archive, preserve the original failure/prototype evidence and retire byte-verified
  pending copies. Complete rules and AI coverage remain open.

- Record Emet-Selch's separate prohibited/declined cast-history failure, with two
  failing cases and two passing controls. Preserve an explicit actual-cast marker
  prototype that passes four focused and 1,231 selected native cases. Keep it
  excluded from the active recipe pending wider casting, payment, replacement
  and release validation. The candidate script flag retains Oracle text.

- Expose Priority Boarding's reflexive exile trigger separately so players can
  respond or counter it. Preserve its reveal allowance when no card can be
  revealed. Add a generic guarded reveal-admission query with an explicit primary
  reveal marker for compound effects. Oracle text is unchanged.
- Retain the revealed object's last-known identity instead of following its current
  state. The earlier prototype failed draw-away and library-return cases; both
  pass after correcting its remembered-object selectors. Expand coverage to
  eighteen cases, including native library responses, source removal, copied games,
  queued/copied triggers, declined choices, mana-value equality and native cleanup,
  extra turns and temporary play permission expiry. The preceding runtime fails
  eight cases and passes ten controls; the corrected candidate passes all eighteen.
- Activate the reveal runtime after 1,227 selected native tests, 475 packaged cases,
  2,701 broader gameplay tests, 256 serialization tests, 42 live tests / 1,123
  subtests and the Python quality gate (416 offline tests / 520 subtests).
  The catalog constructs 32,636 starting identities and separately classifies seven
  nonstarting meld results. Rebind alias and companion reviews only after verifying
  their unchanged scripts. Preserve the original failure/prototype evidence and
  retire byte-verified pending copies. Complete rules and AI coverage remain open.

- Record separate Priority Boarding reflexive-timing and empty-library reveal
  failures. Three of six native cases fail while three controls pass. Preserve a
  source-only prototype that passes all six and 1,215 selected native cases,
  excluded from this runtime pending wider reveal and release validation. Its
  script change retains Oracle text and uses existing reflexive-trigger support.

- Preserve simple optional X-changing allowances when the referenced spell or
  ability is no longer on the stack. Skip mutation of absent stack abilities while
  retaining mandatory changes and same-value choices. No card scripts change.
- Expand coverage to sixteen cases: four fail in the preceding engine and twelve
  controls pass; all sixteen pass with the correction. Cover native paid spells
  and activations, counterspells, source removal/return, copied triggers, remanded
  and recast spell identity, independent game copies, native cleanup/extra turns,
  and mandatory Unbound Flourishing doubling.
- Activate the X-interaction runtime after 1,209 selected native tests,
  457 packaged cases, 2,683 broader gameplay tests, 256 serialization tests,
  42 live tests / 1,105 subtests, and the Python quality gate
  (416 offline tests / 520 subtests). The unchanged card archive constructs 32,636
  starting identities and separately classifies seven nonstarting meld results.
  Preserve the original failure/prototype record and retire byte-verified pending
  copies. Complete rules and AI correctness remain unproven.

- Record the separate Glava absent-spell failure: one failing baseline and three
  passing controls, with native paid spells and actual stack targets. A source-only
  generic prototype passes four focused and 1,197 selected native cases. Keep it
  excluded from the active recipe pending wider X-interaction and release checks.

- Preserve simple optional counter-placement allowances when every recipient is
  prohibited from receiving counters. Keep mixed groups, replacement effects and
  independent counter-removal costs on their native resolution paths. No card
  scripts or alias/companion reviews change.
- Expand counter coverage to sixteen controlled cases: five fail in the preceding
  runtime and eleven controls pass; all sixteen pass with the correction. Cover
  Nykthos Paragon, Leonardo and Guiding Hydra, native response timing, copied
  triggers, game copies, extra-turn cleanup and source death during cost payment.
  The empty-group expectation is inferred from impossible optional action rules.
- Activate the counter-placement runtime after 1,193 selected native tests,
  441 packaged cases, 2,667 broader gameplay tests, 256 serialization tests,
  42 live tests / 1,089 subtests, and the Python quality gate
  (416 offline tests / 520 subtests). The unchanged archive constructs 32,636
  starting identities and separately classifies seven nonstarting meld results.
  Preserve the original failure/prototype record and retire byte-verified pending
  copies. Complete rules and AI correctness remain unproven.

- Track positive native mana additions for explicitly flagged abilities, using
  existing per-ability turn history instead of counting parent/child resolutions.
  Carpet of Flowers retains its later trigger after adding zero, records a legal
  replacement addition, and stays used after the pool is drained. Its script flag
  changes; Oracle text is unchanged. Other abilities retain their normal counters.
- Expand mana history coverage to twelve cases: three fail in the preceding
  engine while nine controls pass. The corrected candidate passes all twelve,
  including native paid counterspells, copied triggers, False Dawn, independent
  game copies, and native cleanup/extra-turn/replacement-expiry hooks.
- Activate the reviewed mana runtime after 1,177 selected native tests,
  425 packaged cases, 2,651 broader gameplay tests, 256 serialization tests,
  42 live tests / 1,073 subtests, and the Python quality gate
  (416 offline tests / 520 subtests). The catalog constructs 32,636 starting
  identities and classifies seven nonstarting meld results separately. Rebind
  alias and companion reviews only after verifying their scripts are unchanged.
- Preserve the separate Nykthos Paragon/Solemnity counter-placement failure and
  source-only prototype (four focused and 1,181 selected native cases pass),
  excluded from this runtime pending wider validation.
- Keep AGENTS.md local and ignored while continuing to follow its rules. Its
  tracking removal was committed separately; the local bytes were preserved.

- Preserve optional battlefield-copy allowances when no eligible choice remains.
  Calix can make a later legal copy after an impossible earlier choice. The generic
  effect query checks the same native eligibility filter used by resolution and
  avoids guessing about defined, random or compound copy forms. No card scripts change.
- Expand copy coverage to twelve controlled cases: four fail in the preceding
  engine and eight controls pass. All twelve pass with the correction, along with
  103 focused/neighboring tests. Controls include native paid removal/counterspells,
  token doubling/redirection and last-known copies after the source leaves play.
- Activate the reviewed optional-copy runtime after 1,165 selected native tests,
  413 packaged cases, 2,639 broader gameplay tests, 256 serialization tests,
  42 live tests / 1,061 subtests, and the Python quality gate
  (416 offline tests / 520 subtests). The unchanged card archive constructs 32,636
  starting identities; seven nonstarting meld results remain separately classified.
  Normalize the active patch to LF for reproducible application and preserve the
  original CRLF research patch in history. Retire byte-verified pending copies.
  Complete rules and default-AI correctness remain unproven.
- Record the separate Carpet of Flowers zero-mana history failure with two passing
  controls. A native paid Harrow changes the target opponent from zero to one Island
  between supplied main-phase events. Preserve a source-only prototype that passes
  three focused and 1,168 selected native checks, excluded from this runtime
  pending wider mana-history and release validation.

- Correct optional draws under Narset and Spirit of the Labyrinth: preserve the
  once-per-turn action when drawing is prohibited, including an optional two-card
  draw when only one card could be drawn. Keep independent trigger cost choices
  available, including Tymna's life payment when its draw is prohibited. Ordinary,
  replaced, empty-library, declined and countered draw controls retain their behavior.
- Expand the draw regression suite to twelve cases. The preceding engine fails
  four and passes eight controls; the corrected candidate passes all twelve and
  all 91 focused/neighboring tests. The original five-case prototype failed three
  expanded cases, so its partial-draw and trigger-cost handling were revised.
- Activate the reviewed optional-draw runtime after 1,153 selected native tests,
  401 packaged cases, 2,627 broader gameplay tests, 256 serialization tests,
  42 live tests / 1,049 subtests, and the Python quality gate
  (416 offline tests / 520 subtests). The unchanged card archive constructs 32,636
  starting identities with seven nonstarting meld results classified separately.
  Preserve the original pending failure record and retire its byte-verified copies.
  These checks establish selected behavior, not complete card or AI correctness.
- Record the separate Calix optional-copy gap: one failing baseline and two
  passing controls. A source-only prototype passes three focused and 91 neighboring
  cases; keep it excluded from the active recipe pending wider copy validation.

- Preserve optional life-gain allowances under prohibitions while retaining legal
  replacement effects and independent reveal choices. Identify Skyfisher Spider's
  optional primary gain before its conditional exile. Keep Uro's attack gain/draw
  mandatory while allowing the land to be declined. Oracle text is unchanged.
- Capture held sacrifice-cost zone changes using the pre-move trigger snapshot,
  then restore the live registry. A sacrificed creature's own death trigger is
  retained even when the paid spell is countered. Eighteen expanded cases pass;
  the preceding engine fails seven and passes eleven controls. All 79 focused,
  neighboring and rollback checks pass.
- Record the separate Chekov and McCoy draw-prohibition gap. The baseline fails
  both prohibition cases and passes ordinary, replacement and empty-library draw
  controls. Preserve a source-only candidate that passes all five cases and 79
  neighbors, excluded from the active recipe pending wider validation.
- Activate the reviewed optional-life runtime after 1,141 selected native tests,
  389 packaged cases, 2,615 broader gameplay tests, 256 serialization tests,
  42 live tests / 1,037 subtests, and the Python quality gate
  (416 offline tests / 520 subtests). The catalog probe constructed 32,636
  starting-card identities and classified seven nonstarting meld results.
  Retire the byte-verified pending copies while preserving their original failure
  record and source commit in the resolution evidence.

- Check simple targeted optional moves through a shared, side-effect-free effect
  query. Riveteers Ascendancy preserves its once-per-turn return when movement is
  prohibited. Fourteen native cases also cover replacement controls, declining,
  countered triggers, lost targets, zone round-trips, hand returns, a mixed
  multi-target return, and a library search whose later move is prohibited.
  Card scripts and the card archive are unchanged. Searches and compound actions
  retain their existing resolution; other effect families remain separate work.
- Record the separate Ancient Cornucopia life-gain prohibition gap. The baseline
  fails both Erebos/Tibalt cases and passes ordinary-gain and Rain of Gore controls.
  Preserve a source-only candidate that passes all four cases and 41 neighbors;
  it remains excluded from the active recipe pending wider validation.
- Activate the reviewed targeted-return runtime after 1,123 selected native tests,
  371 packaged cases, 2,597 broader gameplay tests, 256 serialization tests,
  42 live tests / 1,019 subtests, and the Python quality gate
  (416 offline tests / 520 subtests). The catalog probe constructed 32,636
  starting-card identities and classified seven nonstarting meld results.
  Preserve the baseline's four failures and ten passing controls in the
  resolution evidence; all 41 targeted and neighboring native checks passed.

- Activate the reviewed return-prohibition runtime after 1,109 selected native
  tests, 357 packaged rules cases, 2,583 broader gameplay tests, 256 serialization
  tests, 42 live tests / 1,005 subtests, and the Python quality gate
  (416 offline tests / 520 subtests). The catalog probe constructed 32,636
  starting-card identities and classified seven nonstarting meld results.
  Preserve the original pending failure record inside its resolved evidence;
  promote the reviewed patch and expanded regression into the active build.
- Check shared optional-return availability against native movement prohibitions.
  Preserve the once-per-turn allowance when the move is forbidden, filter mixed
  legal/forbidden return choices, and retain Containment Priest replacement
  behavior. Sixteen controlled cases cover five prohibition cards, two return
  abilities, and mixed creature/land deaths.
- Store companion wording reviews by exact engine card-archive checksum. Preserve
  all 34 existing reviews across 17 archive files, retain the 32 KiB / 32-entry
  read bounds for each file, reject redirected paths and mismatched archives, and
  bind cached approvals to both policy and selected-file bytes. Legacy bounded
  registries remain readable.


- Activate the reviewed Rinoa runtime after 1,093 selected native tests,
  341 packaged rules cases, 2,567 broader gameplay tests, 256 serialization
  tests, 42 live tests / 989 subtests, and the Python quality gate
  (407 offline tests / 511 subtests). The catalog probe constructed 32,636
  starting-card identities and classified seven nonstarting meld results.
- Identify native test methods by class and method name. Add an offline
  regression that preserves same-named methods across classes and retains
  passing, failing, and skipped outcomes without reducing expected totals.
- Previously recorded the separate Grafdigger's Cage return-prohibition gap for Rinoa and
  The Reaper. Preserve its pending correction: the preceding source fails
  both Cage cases and passes both Containment Priest controls; the prototype
  passes all fifteen selected cases. Mixed grouped returns and the wider
  native/packaged/live checks were pending at that milestone and are now
  validated by the activation above.


- Correct Rinoa, Angel Wing’s queued return to retain the graveyard objects
  from the death event, including simultaneous deaths and cards that leave and
  later return to the graveyard.
- Copy resident ability definitions and usage records through the snapshot
  identity map so restoring a game preserves both unused and spent returns.

- Activate the reviewed Reaper runtime after 1,084 selected native tests,
  332 packaged rules cases, 2,558 broader gameplay tests, 256 serialization
  tests, 42 live integration tests / 980 subtests, and the full Python quality
  gate (406 offline tests / 511 subtests). The catalog probe constructed 32,636
  starting-card identities and classified seven nonstarting meld results.
  Preserve the then-known Rinoa failure, resolved by the Rinoa update above;
  these totals do not certify all cards.


- Record a confirmed Rinoa, Angel Wing rules gap: its return effect can move
  an attacker from exile after Ghost Vacuum removes it from the graveyard.
  Preserve the failing native witness and passing decline control separately
  from the passing engine recipe.


- Refresh the local catalog from the November 20 Scryfall Oracle export after
  the prior snapshot failed the freshness gate. Preserve the failed validation
  attempt and a database backup; rerun admission and gameplay validation.

- Preserve The Reaper, King No More's once-per-turn return when a dead creature
  is exiled before the trigger resolves. Verify that declining the first
  return preserves the allowance and a successful return prevents another.
- Verify that Ulalek can copy a separate trigger from the same permanent
  when Roaming Throne makes its cast ability trigger twice.


- Prune generated and archived folders before discovering Python source for the
  quality gate, keeping engine builds out of the traversal.

- Let Ulalek’s AI consider eligible abilities after the Eldrazi spell is
  countered, and verify that it declines when only an uncopyable spell remains.

- Let the AI pay for Ulalek’s copy trigger and exclude the resolving trigger
  from the abilities it copies.

- Correct priority when resolving spells, copying spells and ending the turn.
- Preserve Tempt with Mayhem’s spell target and copy order.
- Fix mana payment with Gemstone Caverns, Cavern of Souls, and Delighted Halfling.


- Exclude Vault 13: Dweller's Journey from its own first chapter's targets.
- Count Wizards controlled by all players when resolving Ixidor's Will.
- Preserve queued abilities and paid spell targets when restoring a snapshot.

- Preserve Incubator token faces and counters when copying game state.
- Preserve transformation history in AI previews and restored snapshots.

- Resume normal play from the last normal player after Aeon Engine reverses
  turn order during an extra turn. Keep pending extra turns in order.
- Verify that ending a turn clears earlier triggers and allows exile triggers
  created during the process to resolve in cleanup.

- Resolve Vault 11's no-vote draw when there are no eligible creatures.
- Make Círdan's permanent placement optional for each player with no votes.
- Restore the saved turn direction when applying a snapshot to an existing game.

- Apply Commander's outside-the-game rule to Learn while preserving its
  discard-and-draw option and Retriever Phoenix replacement.
- Preserve Aeon Engine's reversed turn order in AI previews and snapshots.
- Hide prior secret ballots from voting controllers until all votes are cast.
- Add tests for time travel, including the last time counter on a suspended
  card and a game with no eligible objects.

- Resolve each villainous choice before prompting for the next one, including
  choices added by The Valeyard. Use active-player-first turn order.
- Verify 1,021 focused native tests, 269 packaged checks, 2,495 gameplay
  tests, and 256 serialization tests.

- Place the explore counter before asking whether to keep a revealed nonland
  card on top of the library or put it into the graveyard.
- Put a discovered card into hand when its cast cannot be completed. Reject
  effect-driven AI casts missing required targets and restore the card before
  applying Discover's fallback.

- Finish resolving dice after a declined Monitor Monitor or Night Shift of the
  Living Dead payment. A successful Night Shift payment changes the result and
  can trigger its Zombie ability.
- Verify Jadelight Ranger exploring twice and Captain Kathryn Janeway's explore
  trigger after the creature leaves the battlefield.
- Verify 2,487 gameplay tests, 256 serialization tests, and 42 live integration
  tests with 909 subtests.

- Count dice retained after replacements and rerolls. Record modified results
  and preserve independent dice history in AI previews and native snapshots.
- Exercise a paid Monitor Monitor reroll during Circuits Act, Iron Mastiff's
  highest roll in multiplayer combat, and the dice triggers for Ancient
  Copper Dragon, Chira, Goblinson, and Resolute Veggiesaur. The Monitor Monitor
  fixture supplies the optional choice that Forge's default AI declines.
- Add native and packaged checks for Smell Fear, Hard Evidence, Kai Winn,
  Adami, and Geist of Saint Traft with Holo-Engineer.
- Verify 2,481 gameplay tests, 256 serialization tests, and 42 live integrations
  with 903 subtests. A four-player CLI game with the Star Trek precons completed.

- Reveal all five cards for Hit It! and keep its graveyard movement separate
  from mill. Exercise Station power, Federation mana, Battlefront discounts,
  hybrid Phyrexian mana, and selected flip and transform characteristics.

- Preserve the original defender when Kira Nerys modifies an entry that already
  specifies one. Ninjutsu still enters tapped if that defender has left the game.
- Verify actual Beam me up resolution and interruption, simultaneous Assimilate
  entries, Sisko's Dilemma payoff, and Kobayashi Maru mode history.

- Restore Dilemma history after canceled modal casts and count a completed
  entwine or free modal cast once. Verify modal triggers, random-mode Treasure
  creation, and selected Assimilate control and zone changes.
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
