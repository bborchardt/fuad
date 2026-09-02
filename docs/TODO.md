# Open questions

Things measured and not yet decided. Each one says what was found, how much it moves, and what a fix would
have to answer — so that picking it up later starts from evidence rather than from the memory of a
conversation.

## The outcome spread is pooled across ranks, and the record says it should not be

`PointsCurve.outcomeSeasons` returns one pool of outcomes per position, and
`ExpectedValue.expectedValueOverReplacement` replays every rank through it. The best quarterback on the board
and the 34th are handed the same distribution of rate multipliers and the same distribution of games.

[PROJECTION.md](fuad/PROJECTION.md#3b-outcome-spread-which-is-what-a-bench-is-worth) defends this as the
spread belonging to the position rather than to the player, on the grounds that realised variation cannot
tell a genuinely erratic player from one the consensus misjudged. That argument is about **players** and it
is sound. It does not cover a systematic pattern by **rank**, which is a different claim and a measurable
one.

Measured over the nine seasons the curve is built from, splitting each position into rank bands and taking
the coefficient of variation of the rate multiplier:

| Position | top band | deepest priced band | ratio |
| --- | --- | --- | --- |
| QB | 0.17 (ranks 1-5) | 0.48 (ranks 29-36) | 2.8x |
| WR | 0.21 (ranks 1-6) | 0.54 (ranks 71-89) | 2.6x |

Availability moves with it: quarterbacks ranked 1-5 played 11.8 games a season, ranks 29-36 played 7.6.

**The level is not what is wrong.** Mean realised rate against the rank's expectation is flat across every
band — 0.937 to 0.950 at quarterback — so the curve's central estimate is unbiased by rank. Only the width
around it varies.

**Some of the width is real and some is arithmetic.** A quarterback ranked past about 26 is a backup, so his
season is close to bimodal: he takes a starting job through somebody's injury, or he never plays. That is a
genuine feature of the position. But a ratio taken against a smaller expected number is also mechanically
noisier, which is the same effect `RELEVANT_FRACTION` exists to bound, showing up again inside the ranks that
survive the cut.

### How much it moves

Rebuilding the outcome pool from a rank's own band, normalised on that band's own played mean, and repricing:

| Player | pooled (what the model does) | own band | difference |
| --- | --- | --- | --- |
| Lamar Jackson, QB2 | 78.79 | 78.72 | -0.1% |
| Amon-Ra St. Brown, WR4 | 63.57 | 62.56 | -1.6% |

Almost nothing at the top of the board, and **that is a coincidence of two errors cancelling** rather than a
sign the pooling is harmless. Pooling hands an elite player more volatility than his band carries, which
raises value through the per-week floor at zero, and more missed games than his band carries, which lowers
it. At the top of quarterback those two happen to be the same size. They do not cancel in the middle: rank
bands 13-20 and 21-28 land +7.3% and -5.8% against the pooled figure.

### What a fix would have to answer

- **Sample size.** A band deep enough to be different is a band of 45 to 70 seasons. Whether that supports
  its own distribution, or wants smoothing across bands the way the rate and availability levels already
  have, is the first question and probably decides the rest.
- **Where the boundaries go.** Bands drawn to make a point are not bands a model can use. Tiers already
  exist and already group ranks the curve cannot separate.
- **Whether it is one change or two.** Rate spread and games are pooled separately and could be fixed
  separately. The games half is already known — PROJECTION.md calls the mismatch between pooled games and
  the rank's own `PTS` "a real inconsistency" and reports that repairing it shifts no quarterback by more
  than a dollar, because the ranks where availability genuinely differs are already at the minimum bid. The
  rate half has not been measured against the board.
- **Whether the board would notice.** The middle bands are where the error lives and also where most of the
  money is. A repricing that moves mid-ranked players 5 to 7 per cent of their value over replacement is not
  obviously small once the positional shares renormalise around it.

Nothing here is urgent: the players the plan actually turns on are the ones the pooling happens to get
right. It is on this list because the reason it gets them right is luck.

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

## A tagged player's price and everyone else's are computed in different worlds

`AuctionValuation.price` prices an untagged player at the board's clearing rate, `(pot − slots) /
biddingShare`, where the tagged have already left the pool and their tag prices have already left the pot. A
tagged player cannot be priced that way — [it would overstate him by a quarter](fuad/PROJECTION.md#6-franchise-tags-iterated-to-a-fixed-point)
— so he gets a counterfactual instead: the clearing rate of the world in which his own team did not tag him,
with him back in the pool, his tag price back in the pot, and one more slot to fill.

```
tagged:    (pot + franchiseSalary − (slots + 1)) / (biddingShare + share)
everyone:  (pot − slots) / biddingShare
```

**Each rate is right for its own world, and the `PRICE` column reports both side by side.** That is the
whole of the issue: the two are not on one scale, and nothing on the board says which basis a price is on.

**The counterfactual is systematically the lower of the two, and always in the same direction.** It adds the
player's own share to the denominator and only the tag price to the numerator, and a player worth tagging is
by definition worth more than his tag costs. In the limit where the tag were free the depression is exactly
`share / (biddingShare + share)`.

### How much it moves

The 2026 board, at the round the tags settled on — nine tags, `pot` 1444, `slots` 69, `biddingShare` 1532:

| Player | tag | counterfactual | at the board's own rate | gap | tag/price |
| --- | --- | --- | --- | --- | --- |
| Ja'Marr Chase, WR1 | 61 | 96 | 98 | -2 | 0.64 |
| Lamar Jackson, QB2 | 66 | 89 | 90 | -1 | 0.74 |
| Jahmyr Gibbs, RB1 | 60 | 74 | 75 | -1 | 0.81 |
| CeeDee Lamb, WR5 | 61 | 79 | 80 | -1 | 0.77 |
| Jonathan Taylor, RB4 | 60 | 72 | 72 | 0 | 0.83 |
| Saquon Barkley, RB7 | 60 | 65 | 65 | 0 | 0.92 |
| Dalton Kincaid, TE11 | 24 | 30 | 30 | 0 | 0.80 |

**Nought to two dollars, and it is bounded well below what the mechanism allows.** Holding Chase's share and
varying only the tag price he is put back with, the gap widens from -2 at his real tag of 61 to -6 at a tag
of 1, and no further: one player's share against a `biddingShare` of 1532 is 7%, so 7% is the whole of the
effect available on a board this size. The depression is a property of how concentrated the board is, not of
the tag price.

**Where it bites is a board whose adjacent prices are closer together than that.** `AuctionPricingSpec`
prices 45 quarterbacks and nothing else, so one player's share is a much larger fraction of the total and
consecutive ranks are 3% apart. There the top player is tagged and prices at 132 against the second's 139:
the ordering inverts, and the test asserting that a better player never costs less to prise loose has to
exclude tagged players to pass. That exclusion is honest — a tagged price is a different measurement — but
it is the property being given up rather than checked.

**The blast radius is not nothing, which is what separates this from the item above it.** `tagSurplus` is
`marketSalary − franchiseSalary` and it is what `predictTags` iterates on, so a depressed market price
understates what a tag saves and could in principle change which player a team tags. It does not in 2026:
the smallest surplus among the tagged is Barkley's 5 and Kincaid's 6, against a depression of nought to two.

### What a fix would have to answer

- **Whether the two bases should be reconciled or merely labelled.** They answer different questions and
  both answers are wanted — one says what a tag saves, the other what the auction pays. Reporting which
  basis each price is on may be the whole of the fix, in which case this belongs in the printers and not in
  the pricing.
- **Whether the depression is an error at all.** In the counterfactual world the best player really is back
  in the pool against barely more money, so prices really would be lower. Calling that wrong requires saying
  what the right comparison is, and "what he would fetch in the world where nobody was tagged" is a third
  world, not either of the two on the board.
- **What it would take to make `tagSurplus` like-for-like.** This is the half with a decision hanging on it.
  Both terms would have to come from one world, and the tag price is fixed by rule in all of them, so it is
  the market half that would have to move.
- **Whether a smaller board would expose it.** Everything above is measured on a 105-player board where one
  share is 7% of the total. The pathology is real at 45 players and one position; whether any board the
  model is actually asked to price gets near that is unknown, and is the cheapest of these to answer.
