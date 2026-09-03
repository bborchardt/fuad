# Open questions

Things measured and not yet decided. Each one says what was found, how much it moves, and what a fix would
have to answer — so that picking it up later starts from evidence rather than from the memory of a
conversation.

## The board is at the floor of what this market can be predicted to, and the levers are spent

Two changes landed together — each rank given its own outcome spread, and `PRICE_STEEPNESS` refitted from the
record — and the question was whether the pair was worth shipping when the second was measured to cost
accuracy against the first.

**It is, and the reason is that the question was the wrong size.** Mean absolute error against what the
league actually paid, over 2022-2025, each model given its own fitted steepness:

| | old spread | new spread |
| --- | --- | --- |
| steepness as it was committed | 7.66 | 8.05 |
| steepness refitted from the record | 7.52 | **7.77** |

The spread change costs about a quarter of a dollar whichever steepness either model is given. Set against
the paired 95% interval on a difference of this size, which is ±0.27, that is not a measurable regression —
and set against what any remaining lever could return, it is inside the noise.

### Every lever, measured

| lever | worth | how it was measured |
| --- | --- | --- |
| the pot, and `SPEND_RATE` behind it | nothing | best single multiplier on every price is 1.00 |
| `MARKET_SHARE` | 0.40 | hand the board each season's actual positional spend |
| `PRICE_STEEPNESS` | 0.54 | best gamma per season **and** position, fitted on the answer |
| the priced depth | negative | trim the deep end, give its money to the top |
| the franchise tag | nothing | tagged signings are 9% of the error at a below-average 7.35 |

The two that return anything are ceilings, not gains: they are what perfect hindsight on a season would buy,
and no constant can be fitted to a season before it happens. **Nothing in the chain is left to win.**

And the board already beats the obvious alternative to having a model at all. `NAIVE` on
`accuracy.tsv` predicts each signing from what this league paid at the same rank in its other seasons; the
board is ahead of it in all four. What remains is the market's own scatter — `SIGMA` on `steepness.tsv`,
0.88 to 1.29 in log dollars, so two players of identical worth go for prices a factor of two apart.

### Three things this leaves open

- **It is still scored on the seasons it was fitted on.** Broken out, the old spread wins by half a dollar
  over 2023-25 and loses by eight tenths on 2022, which is the only season held out of everything. That
  ordering is the reverse of the pooled figure and rests on 66 signings from the year the league had not
  adjusted to superflex. Another season of the record settles more than any further analysis of these four.
- **The tag censors the top of the board the way the minimum bid censors the bottom.** `PriceSteepness`
  handles the second and has nothing to say about the first: eight players a year are held out of the
  auction entirely, and they are the players the spread change moved most. The part of the board this
  argument is about is the part the record is quietest on.
- **`PLAYERSABOVE1` against the league's 70 is not the comparison it looks like.** The model's count is over
  the whole priced pool and the league's is over signings. Among players who actually signed, the board is
  only slightly more generous than the league — 74 against 67, 74 against 65, 79 against 70. It was read as
  an accuracy signal here and in [PROJECTION.md](fuad/PROJECTION.md) and it is a weaker one than that.

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
