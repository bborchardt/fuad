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

## The restricted free agent premium has no behavioural ceiling

`AuctionValuation.price` sets a held player's acquisition price at `max(market, value + 1)`: an outside
bidder has to clear what the player is worth to the team holding him, because that team may match. The rule
is right in principle — right of first refusal is what makes positive edge on somebody else's restricted
free agent [arithmetically unavailable](fuad/PROJECTION.md#restricted-free-agency-and-why-bargains-are-unavailable) rather than merely rare — but it
assumes an incumbent who matches all the way up to the model's own valuation, and nothing bounds that by
what the league has ever actually paid.

It breaks wherever `value` sits far above the market price, which is to say at kicker. The highest kicker
salary in nine seasons of this league:

| 2017 | 2018 | 2019 | 2020 | 2021 | 2022 | 2023 | 2024 | 2025 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 2 | 3 | 4 | 2 | 5 | 5 | 5 | 5 |

Nobody has ever paid more than five. The 2026 board asks 16 for Cameron Dicker, whose market price is 3 and
whose value is 15, and 14 for Ka'imi Fairbairn against a market price of 3. Both are more than three times
the league record, and the number is not a prediction of anything a team here would do.

**It is the ratio and not the premium that is wrong.** Mean restriction premium runs +3.0 at running back
and +1.9 at quarterback, on market prices of fifteen to thirty, which is a sensible few per cent. At kicker
the mean is +2.8 on market prices of one to three — the same dollars against a fifth of the base, so the
price multiplies rather than nudges. Receiver and tight end are +0.1 and +0.5 and are not affected at all.

**What it costs is a real buy.** Kicker was the largest single hole on franchise 0001's 2026 roster, with no
kicker under contract at all: a kicker bought for a plausible 6 adds 105 points to that lineup, which is what
Amon-Ra St. Brown adds for 84. Pricing the two best kickers at 14 and 16 routes a plan away from the cheapest
points on its board.

**The blast radius is small, which is why this is a note and not a defect.** `acquisitionSalary` is reported
and never priced — `FuadSalaryProjectionPrinter` and `FuadRosterFitPrinter` read it, and nothing feeds it
back into the board — so no other figure is wrong because of it.

### What a fix would have to answer

- **Where the ceiling comes from.** The obvious source is what the league has paid at that position, but the
  historical maximum is one observation and a ceiling set from it would be as arbitrary as no ceiling. The
  top few salaries at a position are already computed for the franchise tag and are the natural candidate.
- **Whether it is a position's problem or a ratio's.** A rule keyed on position singles kicker out by name.
  A rule keyed on how far `value` sits above `market` would catch the next position where the curve and the
  market disagree that hard, and would leave running back and quarterback alone on their own numbers.
- **Whether the model should instead be doubting its kicker valuations.** The premium is only absurd because
  `value` says a kicker is worth 15. Rank does predict kicker scoring — the correlation is -0.33 against
  -0.57 to -0.61 at the other positions, and the top five ranks have realised 25 points a season more than
  ranks 9 to 13, which is a wider gap than running back's — so the valuation is not obviously wrong. But it
  is the assumption this whole item rests on and it should be stated rather than assumed.
