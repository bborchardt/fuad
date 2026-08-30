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
