# Open questions

Things measured and not yet decided. Each one says what was found, how much it moves, and what a fix would
have to answer — so that picking it up later starts from evidence rather than from the memory of a
conversation.

## The flex allocation is decided on season totals, replacement is then taken at a weekly rate

`ExpectedValue.startersOf` hands `PointsCurve.seasonPoints` to the allocator, so how many of each position
the league starts is settled on season totals — a rank's rate times its expected games. Replacement is then
taken per week, at a rate, on the explicit grounds that
[season totals hide what a bye week does](fuad/PROJECTION.md#3-replacement-level-week-by-week). The count and
the level are on different bases, and the weekly one is the better argued: a lineup is filled fourteen times,
and a player who misses four games vacates a slot entirely in four weeks rather than costing his position a
thin slice of one all year.

**Checked, and it binds nothing.** Allocating on the rate instead returns the same count at every position —
QB 20, RB 26, WR 31, TE 13, PK 10 — and leaves every value over replacement unchanged. Quarterback fills to
its cap of two a team and kicker's minimum is its maximum, so only running back, receiver and tight end
compete; and expected games run 10.4 to 11.0 across every rank that does compete, so multiplying by them
preserves the ordering. Availability diverges sharply only at the back of quarterback, where the cap has
already decided the question.

This is a documentation gap rather than a modelling error, and it is now recorded in `startersOf` itself. It
is here so that the two bases are known to disagree, and so the check is not redone from scratch the next
time somebody notices. What would change the answer is a curve whose expected games differ materially across
the contested ranks — worth re-running then, and not before.
