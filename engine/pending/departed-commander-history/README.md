# Departed commander history

These 24 named regressions are registered for the next source build. Twelve use
controlled native commander combat followed by living, concession, life-loss,
poison-loss, command-zone or exile changes. Twelve also require a legal paid
command-zone cast, its cast count, normal summoning sickness, a real next untap,
and copied cast/mana-source provenance whenever that state still exists.

Both corrected baselines pass six and fail six; the historical-map prototype
passes all 24. The combined retained/new graph suite passes 285. All 1,384 mixed
source cases pass in one original 2-GiB/600-second JVM, retaining 1,360 and adding
24. Exact hashes, original failed phases and fixture corrections are retained in
[the source evidence](../../departed_commander_history_evidence.json).

The production change uses one existing historical graph for commander references
and paid ability/callback state. A source owner may be eliminated while surviving
players still retain commander damage. Copy that history into its own world,
without resurrecting the commander or silently discarding the counters. Strict
ordinary lookup remains strict. Current objects after actual zone changes retain
their native reset state instead of being forced to keep an earlier cast object.

A fresh sealed native 2,609 build, independent packaged 1,452/65/194/146 checks,
broader 4,083/256 tests and the unchanged full-live gates remain pending. The
failed aea5 candidate is not activated. These fixtures do not certify all card
rules, inactive-participant statistics or AI strategy.
