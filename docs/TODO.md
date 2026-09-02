# Open questions

Things measured and not yet decided. Each one says what was found, how much it moves, and what a fix would
have to answer — so that picking it up later starts from evidence rather than from the memory of a
conversation.

## The outcome spread costs accuracy that refitting the steepness does not fully return

Giving each rank its own outcome spread makes the board fit the record worse, and the fix that looked like
it would absorb that only partly does. `PRICE_STEEPNESS` has since been refitted from the record — see
[how steeply the league bids](fuad/PROJECTION.md#how-steeply-the-league-bids-which-is-fitted-and-not-chosen)
— which was worth more accuracy than the spread change cost, so the two together leave the board better than
it was. But they are separable, and separated they do not both point the same way.

Mean absolute error against what the league actually paid, over 2022-2025, each model given its own fitted
steepness:

| | old spread | new spread |
| --- | --- | --- |
| steepness as it was committed | 7.66 | 8.05 |
| steepness refitted from the record | **7.50** | 7.80 |

Refitting helps both. **The spread change costs about 0.3 either way**, and the best board on this measure
is the old spread with the new steepness, which is not what is committed.

### Why it is not simply reverted

The spread change is a correctness fix to `VALUE`, measured directly from the outcome record: a rank's
seasons genuinely scatter two to three times as widely at the back of a position as at the front, and
pooling them was two errors that happened to cancel at the top of the board. That claim is not in doubt and
does not rest on the auction agreeing with it. `VALUE` is also what `EDGE` and the roster reports consume,
and both are better for it.

What the measurement says is that the market does not price the way the corrected value column says it
should — which is a finding about the market, or about which parts of value a bidder actually responds to,
rather than a refutation of the spread. The board carries `VALUE` and `PRICE` as separate numbers precisely
because they are allowed to disagree; this is the largest disagreement anything has yet measured between
them.

### What is not settled

- **Whether the fitted seasons are enough to tell.** Four seasons, 312 signings, and the one season held out
  of the calibration is also the season the league had not adjusted to superflex in. On that season the two
  models are within a tenth of each other and the ordering of the four boards above reverses.
- **Whether the tag censors the fit as badly as the minimum bid does.** The steepness fit handles a dollar
  signing as the bound it is, and has nothing to say about the eight players a year held out of the auction
  entirely. Those are the top of every position — exactly where the spread change moved value most — so the
  part of the board this disagreement is about is the part the record is quietest on.
- **Whether value over replacement is what a bidder responds to at all.** Gamma exists because it is not,
  quite. A spread change that makes value more correct and prices less accurate is evidence about the gap
  between the two, and the model has no account of that gap beyond a single exponent per position.

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
