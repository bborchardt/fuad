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
