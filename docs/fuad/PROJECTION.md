# Salary projection

How `-t salaries` decides what a player will cost at auction. Run it with:

```
./fuad_report.sh -t salaries -y 2026
```

Built for the season being auctioned, and not meant to run on past ones.

**Every report that quotes a dollar quotes this one.** `rankings` and `franchise_projections` used to carry
their own column, from a curve fitted straight from positional rank to dollars, so the same player could be
worth one number on one sheet and another on the next with nothing to say which was meant. That curve is
gone. `rankings` shows `PRICE`, being the sheet a bid is made from; `franchise_projections` shows `COST`,
being read a roster at a time. Both are the board's, unchanged.

Fitting dollars to rank directly is the obvious alternative, and three things rule it out here. It pools a
$250 and a $300 cap, and one-quarterback and superflex lineups, as if they were one league. It reads the
**46 franchise tags** as bids, when their prices are set by rule and land on the most expensive players in
the data. And it prices each player alone, so nothing makes the answers add up to the money that exists.
None of it can price 2026's tight end premium at all, since no season under that rule has been played.

Two consequences of there being one model rather than two. A player the board declines to price — a rookie,
or a rank past the depth the curve still makes a claim at — is now **blank** on those sheets rather than
carrying a number, which took 148 priced rows on 2026's `rankings` down to the board's 106. And the
earliest season any of them can run for is **2018**: the franchise tag is the average of the previous
season's top five salaries at a position, and 2017 has no 2016 behind it.

## The chain

### 1. Order from consensus, level from history

`PointsCurve` takes the **order** from the FantasyPros consensus and the **level** from what those ranks
have historically been worth. The player consensus ranks WR1 is valued at what preseason WR1s have actually
scored, restated under the rules being priced.

The split is deliberate, and one half of it is a judgement while the other is a fact. A ranking is a
judgement about who is better, which is what expert consensus is for and all it gives you. What that
judgement has been worth is a question only finished seasons can answer. **No projection of any particular
player enters anywhere.**

Levelling on projections instead is the alternative, and it makes the level of every rank one source's
opinion of this year's specific players. MFL projects Patrick Mahomes at 318 points where consensus ranks
him QB14, so the whole rank goes with him; history levels QB14 at 196, which is what QB14s have actually
done.

The one thing about a particular player the curve is willing to know is his bye week. That is a fact of the
schedule rather than an opinion about him, and it is carried for the whole ranked pool, because replacement
level in a week six teams are off depends on who else is playing.

### 2. Nine seasons, pooled flat, absences included

The record runs 2017-2025, from raw nflverse statistics rather than anyone's fantasy points, because the
league has scored four different ways since 2017 and a season scored under its own rules says nothing about
what a rank is worth today. `ScoringRules` restates every season under the rules being priced.

Pooled flat rather than weighted towards recent seasons. Restating 2017-19 and 2022-24 under one rule set
brings the eras to within a few per cent at every position — QB 1.07, RB 1.00, WR 0.99, TE 1.00 — so there
is no era effect left to correct for, and the quarterback figure is noise. Nine seasons gives a rank about
**45 observations against 15** from three, which is what the smoothing has to work with: two ranks either
side are averaged in, and a rank with fewer than six observations behind it is not levelled at all.

**Ranked seasons that never happened are in the sample as zeros.** Andrew Luck's 2017 shoulder, Le'Veon
Bell's 2018 holdout, Gus Edwards' 2021 knee, Joe Mixon's 2025 foot. They are exactly the seasons that busted
hardest, so dropping them biases every curve upward and cuts off the left tail a bench is priced against.
How many of each there are, per position:

<!-- figures: fuad/positions -->

| POS | SEASONS | LOST |
| --- | --- | --- |
| QB | 324 | 5 |
| RB | 584 | 13 |
| WR | 801 | 11 |
| TE | 405 | 10 |

Which is why the name matching between the rankings and the statistics has to be careful before it gives
up: **an unmatched name and a season lost to injury are indistinguishable, and both score zero.** See
[DATA.md](../DATA.md#player-names) for how names are matched.

What that produces at the top of each position, in points over the fourteen week regular season:

<!-- figures: fuad/curve across=POS field=PTS -->

| Rank | QB | RB | WR | TE |
| --- | --- | --- | --- | --- |
| 1 | 245.3 | 190.0 | 183.1 | 161.5 |
| 6 | 220.5 | 178.0 | 160.1 | 116.4 |
| 24 | 153.4 | 109.5 | 121.3 | 75.0 |

and per game played:

<!-- figures: fuad/curve across=POS field=PPG -->

| Rank | QB | RB | WR | TE |
| --- | --- | --- | --- | --- |
| 1 | 21.39 | 17.08 | 15.98 | 15.06 |
| 6 | 19.26 | 16.45 | 14.11 | 10.90 |
| 24 | 15.17 | 10.50 | 11.09 | 7.22 |

### 2b. A season is a rate times an availability

Those are two numbers because a season is two things, and they are differently caused: how good a player is
when he plays, and how much football he plays. `RATECV` and `GAMESCV` are how widely each scatters, as a
coefficient of variation:

<!-- figures: fuad/positions -->

| POS | RATECV | GAMESCV |
| --- | --- | --- |
| QB | 0.32 | 0.34 |
| RB | 0.52 | 0.33 |
| WR | 0.46 | 0.30 |
| TE | 0.50 | 0.32 |

For ranked quarterback seasons the two are level, so **about half the variation in a season total is
availability rather than production.** At the other three the rate scatters half again as widely as
availability does, which is a real difference between positions and one a single season total hides.

**How much the two halves have to say about rank is not the same, and it differs by position.** Over the
same span of ranks, receiver loses almost all of its level to the rate while quarterback loses nearly as
much of its own to availability as to the rate:

<!-- figures: fuad/curve across=POS field=PPG -->

| Rank | QB | WR |
| --- | --- | --- |
| 1 | 21.39 | 15.98 |
| 34 | 11.22 | 9.88 |

<!-- figures: fuad/curve across=POS field=G -->

| Rank | QB | WR |
| --- | --- | --- |
| 1 | 11.46 | 11.45 |
| 34 | 6.90 | 10.71 |

A receiver 34 ranks down plays essentially as much football as the best one and simply scores less when he
does. A quarterback 34 ranks down is a different kind of player: the league has 32 starting jobs, so past
about rank 26 he is a backup who plays when somebody gets hurt. A single levelled season total puts both a
long way under the best and says nothing about which of them is worse and which is simply absent. The two
tables above do say: the receiver keeps his availability and gives up the rate, while the quarterback gives
up both.

**Levelling the product directly carries that noise into the level, and the measurement says by how much.**
Where rank *r+1* levels above rank *r*, the step is noise the smoothing failed to remove; summed over the
priced ranks and taken against the curve's range, it is how far a curve travels backwards. Levelling the
season totals against splitting them, over a rank window common to all four positions:

<!-- figures: fuad/positions -->

| POS | BACKWARDTOTALS | BACKWARD |
| --- | --- | --- |
| QB | 24.5 | 0.6 |
| RB | 54.5 | 4.4 |
| WR | 61.2 | 2.0 |
| TE | 20.3 | 1.3 |

Better at every position, and by a long way — but the two columns no longer measure the same thing, and
the gap between them is not all the split's doing.

**`BACKWARD` is now held down by a constraint as well as by the split.** The rate is pooled by adjacent
violators before anything is built on it, so a curve that ran backwards because forty five observations
failed to separate two ranks no longer can; what backward movement survives comes from availability, which
is deliberately left alone. `BACKWARDTOTALS` has no such constraint on it, being the counterfactual of
levelling the season totals directly.

So read the two columns as what they are: one curve that is constrained and one that is not. The split's own
case was made before the constraint existed, when the same measurement ran 6.6 at quarterback, 12.4 at
running back, 21.9 at receiver and 11.6 at tight end against the totals figures above — better by a factor
of two to four, on the same footing. That is the comparison the split rests on, and it is recorded here
because the figures can no longer reproduce it.

> **Superseded.** This section used to argue the split from a single case — that levelling season totals
> made the consensus best running back appear to be outplayed by RB5 — and that case no longer exists,
> which is the change working rather than an argument still standing. RB1 now levels above RB5 on the
> season, on the rate and on games played alike. The measurement above replaced it because a curve-wide
> number can be regenerated and an anecdote cannot.

**Availability is smoothed five times harder than the rate**, over ranks ±10 rather than ±2. How much
football a player misses is only weakly related to where he was ranked, `GAMESCORR` being the correlation
between rank and games played over every ranked season that carries money:

<!-- figures: fuad/positions -->

| POS | GAMESCORR |
| --- | --- |
| QB | -0.41 |
| RB | -0.16 |
| WR | -0.22 |
| TE | -0.20 |

Outside quarterback that is a few per cent of the variance, so a narrow window fits mostly noise and
multiplies it straight back into the level. **The decisive evidence is not this, though, and it is worth
saying which is which:** at the rate's own radius the curve came out *less* monotone than the season totals
it replaced, and monotonicity is measured directly as `BACKWARD`. The correlation says the signal is weak;
`BACKWARD` says what the window actually did to the curve.

> This paragraph used to put the correlation at −0.04 at running back, −0.09 at receiver and −0.14 at tight
> end, which is two to four times weaker than the figures above and is not reproducible under any definition
> the model now supports. It was prose that nothing recomputed. The conclusion is unchanged, because it never
> rested on this measurement — but "nearly unrelated" was a stronger claim than the record supports, and the
> figure is generated now so it cannot drift again.

<!-- The model this was measured against is no longer in the repository: the history was rewritten and the
     sha it named went with it. The blockquote below stands as a record of what was measured, not as
     something a reader can go and check. -->

> Measured when the radius was chosen, against a model since superseded, and not reproducible: the
> alternative is a constant the curve no longer accepts. Backward movement at ±2 ran 24% at quarterback,
> 44% at running back, 83% at receiver and 35% at tight end, against the ±10 figures in the table above.

Smoothed, though, and not held flat, which was the first attempt. Quarterback is why:

<!-- figures: fuad/curve across=POS field=G -->

| Rank | QB |
| --- | --- |
| 1 | 11.46 |
| 20 | 10.61 |
| 24 | 10.11 |
| 26 | 9.54 |
| 30 | 8.31 |
| 34 | 6.90 |

A flat figure would have used one number for all of them, around 10.6. That overstated the back of the range
by half while understating the elite, and left a cliff wherever the flat region stopped. A wide window keeps
what flattening was for and gives all of that back: it is more monotone at every position, and continuous
everywhere. Receiver, where availability really is flat well past rank 40, is unaffected either way.

A season lost entirely is `games = 0`. It carries no rate — a year that never happened is no evidence about
form — and counts in the availability half, which is how the left tail a bench is priced against survives
the split.

**Indexing by rank, not by player, is what keeps this honest.** See [Provenance](#provenance).

### 3. Replacement level, week by week

`StarterRequirements` works out how many players at each position the league actually starts. That is not a
setting to read off: the lineup is ten of QB 1-2, RB 1-3, WR 2-5, TE 1-3, PK 1, so six slots are fixed by
the minimums and four are flex. Allocating the flex greedily across the league gives, for 2026:

<!-- figures: fuad/positions -->

| POS | STARTED | REPLRANK |
| --- | --- | --- |
| QB | 20 | 21 |
| RB | 26 | 27 |
| WR | 31 | 32 |
| TE | 13 | 14 |
| PK | 10 | 11 |

Superflex means 20 of about 50 usable quarterbacks start, which pushes quarterback replacement very high
and compresses what the best ones are worth over it. That much is decisive and stable.

**The last few flex spots are not.** The comparison that decides them is between a league's 32nd to 34th
receiver and its 11th to 13th tight end, and those are the same players:

<!-- figures: fuad/curve across=POS field=PTS -->

| Rank | TE |
| --- | --- |
| 11 | 109.6 |
| 12 | 109.5 |
| 13 | 107.2 |

<!-- figures: fuad/curve across=POS field=PTS -->

| Rank | WR |
| --- | --- |
| 32 | 106.8 |
| 33 | 106.5 |
| 34 | 105.8 |

A handful of points apart, against a standard error near six. Small changes to the curve have moved these
slots between the two positions more than once, and
the honest reading is that the league starts about ten tight ends and about thirty-two receivers with the
last few slots undetermined. It matters because replacement level follows from it — which is a caution
about the tight end and deep receiver markets rather than about the top of the board.

Replacement is then taken **per week**, as the best player at that position who would not be started *that
week*. A team fields a lineup every week, so on a week when six teams are on bye its alternative is worse
than the season table suggests. Season totals hide this; pricing weekly moves about 1.6 points of the
league's total value onto quarterbacks and running backs.

### 3b. Outcome spread, which is what a bench is worth

Value over replacement at a player's expected points is `max(0, E[X] - replacement)`. What a roster spot is
actually worth is `E[max(0, X - replacement)]`, because a player only has to be started in the weeks he is
good. The second is never smaller, and the gap is widest at replacement level — exactly where a bench sits:

`VOREXP` is the first, carried only so the two can be compared; `VOR` is the second, which is what the board
prices with. Read down a position at a time:

<!-- figures: fuad/curve across=POS field=VOR -->

| Rank | QB | WR | RB |
| --- | --- | --- | --- |
| 10 | 42.6 | | |
| 30 | | 14.4 | 16.3 |
| 38 | | 9.7 | |
| 48 | | | 7.6 |

<!-- figures: fuad/curve across=POS field=VOREXP -->

| Rank | QB | WR | RB |
| --- | --- | --- | --- |
| 10 | 34.0 | | |
| 30 | | 2.5 | 0.0 |
| 38 | | 0.0 | |
| 48 | | | 0.0 |

Every one of those gaps is positive, and it is widest where a player sits near replacement rather than well
clear of it — RB48 is worth nothing at all at his expectation and nearly eight points once the season is
allowed to go several ways.

The spread is real: realised points at a given preseason rank vary with a coefficient of variation of 0.5 to
0.6 by position, and players nominally below replacement still clear it often.

So a season is replayed against **every season the ranks around this one have actually produced**, and value
over replacement averaged across them.

**The pool is a stretch of the board and not the whole position, because the record says the two are not the
same thing.** One pool a position replayed the best quarterback on the board and the 34th through the same
distribution of rate multipliers and the same distribution of games. `SPREAD` is how widely a rank's own
seasons scatter in the years they happen:

<!-- figures: fuad/curve across=POS field=SPREAD -->

| Rank | QB | WR | RB | TE |
| --- | --- | --- | --- | --- |
| 1 | 0.17 | 0.21 | 0.28 | 0.28 |
| 12 | 0.25 | 0.25 | 0.25 | 0.31 |
| 24 | 0.29 | 0.25 | 0.33 | 0.40 |
| 36 | 0.47 | 0.33 | 0.44 | 0.49 |

and `G` is how many of them happen at all:

<!-- figures: fuad/curve across=POS field=G -->

| Rank | QB | WR | RB | TE |
| --- | --- | --- | --- | --- |
| 1 | 11.46 | 11.45 | 11.12 | 10.73 |
| 12 | 11.39 | 11.44 | 10.91 | 10.72 |
| 24 | 10.11 | 10.94 | 10.43 | 10.40 |
| 36 | 6.24 | 10.62 | 10.26 | 9.37 |

**The level was never what was wrong.** Mean realised rate against a rank's own expectation is flat across
the board — 0.937 to 0.950 at quarterback — so the curve's central estimate was unbiased by rank and only
the width around it varied. What the window changes is the width, and it is normalised on its own seasons
so that carrying it still moves no expected points.

**Some of the widening is the board and some of it is arithmetic, and the arithmetic is the smaller half.**
There are 32 starting jobs at quarterback, so a rank past about 26 is a backup whose season is close to
bimodal: he takes a job through somebody's injury, or he never plays. That is a real feature of the
position. But a ratio taken against a smaller expected number is also mechanically noisier, which is what
`RELEVANT_FRACTION` exists to bound and it does not stop at the cut. How much of it that accounts for is
readable rather than arguable: `SE` against `PTS` bounds the noise in that denominator at 3.8% of the level
at QB1 and 12.8% at QB36, and taking the second out in quadrature leaves 0.45 of the 0.47 measured there.
About a twenty-fifth of the width at the very deepest rank, and less everywhere else, is the ratio rather
than the football.

**Why this was invisible while it was wrong.** Pooling handed an elite player more volatility than his
neighbours carry, which raises value through the per-week floor at zero, and more missed games than they
carry, which lowers it. At the top of quarterback the two happened to be the same size, so the players the
board actually turns on were priced almost exactly right — by two errors cancelling rather than by either
being small. They do not cancel in the middle, and the middle is where most of the money is.

**A sliding window rather than bands, because a band has edges and an edge near replacement is a cliff.**
Tiers were the obvious candidate, being a grouping the curve already believes in. They are the wrong shape:
a tier boundary is a claim about two levels being separable, the spread moves smoothly and continuously with
rank, and two ranks a tier apart would price tens of dollars apart on nothing but which side of the line they
fell on. The rookie board learned that with real bands and real dollars — see
[the spread that priced rookies above the league](#the-spread-that-priced-rookies-above-the-league) — and
this is the same lesson applied to the veterans.

Five ranks either side is 99 seasons where the window is full, against the 45 a level is a mean of, and the
count is carried as `SEASONS` on the same row so a reader can see which ranks are speaking from a full
window and which from a one-sided edge. Measured at three, five and eight ranks either side the widening by
rank is the same shape, so the radius is chosen for sample rather than to make the pattern appear.

**Rate and availability are one change and not two.** They were pooled separately and could have been fixed
separately, and there is no reason to: what was wrong is the pooling unit, not either half. A window is a set
of realised seasons, and each of them carries its rate and its games together, so both halves come out of one
pass and cannot disagree about which seasons they describe.

**What it moved.** The top of the board barely notices: Lamar Jackson's value over replacement at QB2 moves
by one point in 79 and Ja'Marr Chase's at WR1 by none, which is the cancellation above showing up as it
should. The middle moves by five to twenty-five per cent — running backs 11 through 27 and receivers 22
through 41 downward, the back of every position upward — and the positional shares of worth renormalise
around it: quarterback's `VORSHARE` rises from 24.8 to 25.6 and receiver's falls from 23.8 to 22.9. The one
count that looks worst against the record is the number of players above the minimum bid, which rose from 76
to 86 and then to 97 as the steepness was refitted — against the 70 the league signs. **That comparison is
weaker than it looks**: the model's count is over the whole priced pool and the league's is over signings,
so they have different denominators. Among the players who actually signed, the board is only slightly more
generous than the league was — 74 against 67 in 2023, 74 against 65 in 2024, 79 against 70 in 2025. What
both changes really did is weighed player by player in
[how close the board comes to what was paid](#how-close-the-board-comes-to-what-was-paid), which is the
comparison that shares a denominator.

Each replayed season is a rate and a number of games, kept paired as one player's year rather than drawn
apart. That matters most for the seasons that ended early. Smearing an injured starter's total across the
whole calendar made him look like a player who was bad every week instead of good and then absent:

<!-- model: b5e0874 -->

> **Superseded, and not reproducible**: the second row is what an implementation the model no longer has
> produced. A top-24 quarterback season of seven games or fewer scored **12.22** points per week in the games
> he actually played, against the **4.77** levelling the season total gave him, and **17.46** for a healthy
> season. At 12.22 he clears replacement in every week he plays and banks value; at 4.77 he clears it in none
> and banks nothing. Twenty-four of the 215 top-24 quarterback seasons on record are that shape, so the old
> reading systematically understated the injury tail — which overstated injury risk and undervalued the
> players with the highest rates.

Which weeks are missed is left as an expectation rather than drawn, since nothing here knows when an injury
lands: a season of `g` games out of `W` playable weeks earns `g/W` of what a full season at that rate would
have earned.

**Replacement is taken at its rate**, not discounted by availability. It is the best of whoever is left
rather than one player's season, and a replacement by definition turns up — the waiver wire always has a
healthy body, so pricing against one who might himself be hurt would be pricing against a player nobody has
to accept.

### 3c. Why a missed week costs nothing, which is not the same as being free

A missed week earns no value over replacement, and that is a claim: it says the week a player is out, his
team fields somebody at replacement level. It plainly does not, in season. Replacement here is the best
player *not started* — QB21, RB27, WR33, TE13 — and the week 1 rosters hold 46 quarterbacks, 71 running
backs, 110 receivers and 44 tight ends, so every one of those is on somebody's bench. The first genuinely
free quarterback is about QB47.

Charging for it was tried, against two bars: covered by the deepest rank the curve still prices, and
covered by nobody. Both wreck the board.

Measured on `COST`, which is what a team actually pays and so what the record can be compared against — the
tag holds the very best players well below their `PRICE`.

As priced, which is generated:

<!-- figures: fuad/board -->

| FIGURE | VALUE | Actual 2025 |
| --- | --- | --- |
| Players above $1 | 97 | 70 |
| Top cost | 73 | $100 |
| Top 40 cost | 81.4 | 87% |

<!-- model: 59b4f91 -->

> **Superseded, and not reproducible**: each of the three alternatives below is an implementation the model
> no longer has. Charging missed weeks against the deepest rank the curve still prices left **27** players
> above the minimum bid with a top `COST` of **$236** and 96.5% of the money in the top 40; charging them
> against nobody left **23**, **$268** and the same 96.5%. Giving each rank its own availability instead —
> the mildest of the three — left **70**, **$81** and 88.3%, which is the only one that did not wreck the
> board.

Twenty-seven players above the minimum bid against the seventy the league signs, and tight end priced out
of existence altogether. The reason is structural rather than a matter of picking a gentler bar: those three
were measured while the games a season loses came from the position's pooled distribution, so the charge was
**the same number for every player at a position**. Subtract a constant from everyone and let the positional
shares renormalise, and the money tilts to whoever had most of it already — the top tenth of the board goes
from 34.7% to 60.2%. A rank-invariant penalty cannot correct a rank-invariant assumption; it only steepens
the curve.

The rank-invariant assumption is itself gone now. Games come from the rank's own window along with the rate
— see [3b](#3b-outcome-spread-which-is-what-a-bench-is-worth) — so the mildest of the three alternatives, a
rank's own availability, is no longer an alternative at all but what the model does. It shifted no
quarterback by more than a dollar when it was measured against the pooled version, for the reason it was
always going to: the ranks where availability genuinely differs — the back of quarterback plays barely six
games against the best one's eleven and a half — are already at the minimum bid. That is a repair to what
value over replacement draws from, and it is not the same as charging a player for the weeks he misses,
which is the question this section is about and which the paragraph below settles.

**What settles it is that the cost is charged elsewhere, once.** At the auction a team buys its whole
roster in one sitting, so what covers an injured starter is another player it also bought. Charging the
starter for his own absence *and* making the team buy a backup pays for the same week twice. `-t roster`
prices that backup and finds him expensive — a third quarterback is worth 57 points to Brett, nearly all of
it bye and injury cover. So absence is charged in the report that can see a roster, and a league-wide
clearing price, which cannot see one, leaves it alone. See [STRATEGY.md](../STRATEGY.md#the-roster-reports).

**The distribution is used as observed, not fitted.** It is badly lopsided. Almost all the variance is a left
tail of seasons lost to injury, reaching zero at every position, while the upside is far more compressed —
nine seasons in ten land under about one and three quarters times expectation:

<!-- figures: fuad/positions -->

| POS | P10 | P90 |
| --- | --- | --- |
| QB | 0.36 | 1.51 |
| RB | 0.23 | 1.71 |
| WR | 0.36 | 1.66 |
| TE | 0.31 | 1.75 |

Note how much further the tenth percentile falls below one than the ninetieth rises above it. Fitting a
lognormal to that variance mirrors the left tail into a right one, pricing the bench as if every deep player
might become a star, which flattens the whole board. The empirical distribution does carry a handful of
multipliers above three; what a fit does is make them ordinary rather than rare.

**Only ranks levelled above a quarter of the position's best contribute to it.** Not because the deep ranks
are uninteresting but because a ratio taken against a very small number is not a ratio of the same thing.
The consensus ranks receivers 140 deep, and by the bottom of that list a rank is levelled at three or four
points a season, so one player who turns out to be a starter comes back as sixteen times expectation. Those
are not seasons that beat their expectation, they are seasons the consensus was not really making a claim
about. Letting them in invents exactly the multipliers above three the distribution is kept empirical to
avoid.

### 4. Dollars from a known pot

Teams spend a fairly steady share of the cap they have free — 70% to 87% across the measurable record,
**83%** across the four superflex seasons — so the pot is knowable before the auction:

<!-- figures: fuad/board -->

| FIGURE | VALUE |
| --- | --- |
| Free cap | 2438 |
| Expected spend | 2024 |

The sixth that goes unspent is deliberate, and it is why a model that assumed teams bid to the cap would
price everything too high. Cap is what absorbs in-season signings, and it is what a team releasing a bad
contract pays the penalty out of — charged to the current year and cleared at the end of it, so cap left
over is also the mechanism by which a mistake is prevented from reaching next season. See
[LEAGUE_RULES.md](LEAGUE_RULES.md#the-cut-penalty).

Most of that reserve is never drawn on, which is what makes it insurance rather than a budget. In-season
salary added between the week 1 and end-of-year rosters runs **0.3% to 2.3% of the league's cap**, a mean
of about 1.2%, though 4 to 8 of the 8 to 10 teams add something every single season. Teams hold far more
room than they use, every year, and go on holding it.

**That 83% counts every player the auction paid for**, which is expiring contracts that came back and also
the veterans signed from outside the pre-auction rosters. The second group used to be left out, so the pot
was measured without money the board was nevertheless dividing among those same players — between 0.8% and
7.2% of an auction, depending on the year. They are told from an in-season waiver pickup by the transaction
log rather than by their price: the auction arrives as one roster load, a waiver claim does not, and the
two look nothing alike — a pickup is almost always the minimum bid.

It is also counted over **distinct players**. The week 1 snapshots repeat a handful of roster rows verbatim,
same franchise and same salary, so summing rows rather than players double counts those contracts. That
mistake happens to land near 83% as well, and has nothing to do with this figure — two different errors
reaching one number, which is worth saying so the agreement is not read as confirmation.

`AuctionValuationSpec` recomputes this and the market shares from the committed seasons, so neither constant
can drift from the data it came from.

**2021 is not among those seasons, and a spec is what put it out.** The league contracted from ten teams to
eight that year and the pre-draft snapshot was taken afterwards, so the 47 contracts the departing owners
released sit on no pre-draft roster and every one of them reads as a player somebody bid on. Measured that
way the season spends 1.09 of the cap it had free — which is not a league bidding hard, it is a measurement
that has stopped measuring. Nothing in the data separates those contracts from real signings, so the season
is excluded from the measurement and used everywhere else as normal.

Each player who must be signed is reserved a dollar and the rest is shared in proportion to value over
replacement, so **prices sum to the money available**.

### 5. Pulled towards how this league actually bids

Value over replacement puts far more of the pot on running backs than this league spends, and far less on
wide receivers, so prices are scaled to the shares the league actually pays.

**Calibrated on 2023-2025, not on 2022.** Superflex arrived in 2022 and the league had not adjusted to it:
wide receivers took **56.0%** of that auction against 30.6% to 36.5% in every season since, and
quarterbacks 13.8% against 17.6% to 29.4%. Averaging that year in drags wide receiver up and holds
quarterback down, which showed up as top receivers priced above anything the league has ever paid.

<!-- figures: fuad/spend across=POS field=SHAREXPK -->

| Share of auction spend | QB | RB | WR | TE | |
| --- | --- | --- | --- | --- | --- |
| 2022 | 13.8% | 22.3% | **56.0%** | 7.9% | excluded |
| 2023 | 23.9% | 27.3% | 36.5% | 12.2% | |
| 2024 | 17.6% | 41.8% | 30.6% | 10.0% | |
| 2025 | 29.4% | 29.4% | 34.4% | 6.8% | |
| 2023-2025 | 24.0% | 32.7% | 33.9% | 9.5% | what the model calibrates to |

Those rows are what the league paid, generated from the committed seasons into
`docs/figures/fuad/<year>/spend.tsv` and checked cell by cell, so the case for dropping 2022 is evidence a
reader can check rather than a claim they have to take. The last row is the pooled span the calibration is
fitted over, and it is `TARGETSHARE`:

<!-- figures: fuad/positions -->

| POS | TARGETSHARE |
| --- | --- |
| QB | 23.7 |
| RB | 32.3 |
| WR | 33.5 |
| TE | 9.4 |
| PK | 1.0 |

**The table above is on the four-position basis; `TARGETSHARE` is on the whole-auction one.** Kickers take
0.6% to 1.7% a season, and leaving them in or out of the denominator moves every other position by a few
tenths — enough to be mistaken for rounding and enough to matter to a constant compared against them.
`spend.tsv` carries both: `SHAREXPK` is the table's basis, `SHARE` is the share of every dollar, and it is
`SHARE` the model calibrates to now that kickers are priced along with everyone else.

The repricing is real, and it is not a stock of old contracts running off. Money already committed says the
same thing from the other side. `COMMITTEDSHARE` is what each position holds of the salary already on the
books before a bid is made, so it moves only as contracts are signed and expire rather than with one
auction:

<!-- figures: fuad/spend key=SEASON+POS -->

| SEASON | POS | COMMITTEDSHARE |
| --- | --- | --- |
| 2022 | QB | 16.1 |
| 2025 | QB | 33.0 |
| 2022 | RB | 42.6 |
| 2025 | RB | 17.9 |
| 2022 | WR | 36.5 |
| 2025 | WR | 35.3 |

Quarterback doubles its share of committed money over the same span the auction turns towards it, running
back more than halves, and **wide receiver ends where it started**. Nothing is expiring away; the league is
simply buying different positions.

### 6. Franchise tags, iterated to a fixed point

Tags take 12-37% of the pot out of open bidding, so they cannot be ignored. Each team tags the expiring
player it saves most on, so long as the saving is positive; those players leave the pool and their tag
price leaves the pot; everything reprices; repeat until the set of tags stops changing.

**Tag surplus is measured against the market price, not against what the player ends up costing.** A tagged
player costs the tag price by definition, and comparing that against itself makes every tag look pointless
— which sends the loop oscillating forever rather than settling. The board therefore reports both a
`MARKET` price and a `SALARY`.

#### The world a saving is read in

A tag is a choice between two boards: the one where the team uses it, and the one where it uses none — the
player back in the bidding, his tag price back in the pot, and one more roster spot to fill. What the tag
saves is the difference between them, so the market half has to be read on the second. A tagged player's
`MARKET` is accordingly the price he would have fetched **had his own team not tagged him, with every other
team's tag still standing**.

**And so is every rival on the same roster.** A team is not deciding whether to tag one named player, it is
deciding which of its expiring players to tag, and those comparisons have to be on one basis. Priced player
by player they were not: the player actually tagged was measured in the world where his tag is lifted while
his own team-mates were measured in the world where it still stands — against a smaller pool and a smaller
pot, which prices them higher. A systematic discount on the incumbent and a systematic premium on the
challenger, running the same way every time. `AuctionValuation` now takes **one clearing rate per team**,
the rate of that team's own no-tag world, and reads every one of its expiring players off it.

<!-- model: b0f526d -->

> **Superseded, and not reproducible**: 2026 was the case this used to trip over. One franchise held Lamar
> Jackson and Amon-Ra St. Brown, and their savings came out at **23** and **23** — a tie, settled on worth.
> Read in one world they were **23** and **22**: the tie was never there, and St. Brown's extra dollar was
> the world in which Jackson had already been tagged. Fifteen of the sixty-eight held players nobody tags
> came out a dollar apart on the two bases and none by more than a dollar. Those figures are from a model
> that priced quarterback at a steepness of 1.44; refitting it from the record took quarterback to 1.01 —
> see [how steeply the league bids](#how-steeply-the-league-bids-which-is-fitted-and-not-chosen) — and the
> board no longer contains that example. The two-world reading it is compared against is itself gone, so
> neither half can be regenerated.

The same franchise still holds both, and on the current board the choice is not close: St. Brown saves 31
against Jackson's 6, so the tag goes to the receiver on any reading. What the argument needed was a board
where a team's two candidates land within a dollar of each other, and this one has none — the narrowest
choice any team is actually asked to make is now `TAGMARGIN`:

<!-- figures: fuad/board -->

| FIGURE | VALUE |
| --- | --- |
| TAGS | 8 |
| TEAMSTAGGING | 8 |
| TAGMARGIN | 7 |

Seven dollars is comfortably wider than the phantom the two-world reading used to invent, which is why
nothing on the 2026 board now turns on it. **That is a fact about this year's board and not about the
rule**: the asymmetry ran the same way every time it arose, and a season whose margins are tighter would
feel it again. The fix stands on the argument rather than on the size of the example, and
`AuctionPricingSpec` pins each of a team's candidates to that team's own no-tag world to the dollar.

#### The two bases are not one scale, and cannot be made into one

`PRICE` for anyone the auction can bid on is the rate the board clears at with every predicted tag standing.
`PRICE` for a tagged player is his own team's no-tag world. Those are different worlds, no scale puts them
side by side, and the `FRANCHISED` column is what says which basis a row is on.

**The counterfactual is always the lower of the two, and that is not a defect — it is the same inequality as
the tag being worth using.** Write a player's share of the bidding as `b` and what his tag returns to the pot
as a fraction of it as `a`. The counterfactual rate is below the board's own rate by `(b − a) / (1 + b)`, so
it is lower exactly when `b > a`; and `b > a` rearranges to `market > tag`, which is the condition for the
tag to save anything at all. A player worth tagging cannot fail to be discounted by his own counterfactual.
The size of it is bounded by `b / (1 + b)`, which is a fact about how concentrated the board is and not
about him: the largest share on the 2026 board is 7% of the bidding, so no gap on it can exceed about six
dollars. The largest actually is two, the tag prices returning most of the difference through `a`.

**What would make it bite is concentration, not a small board.** A board of one position leaves its best
player at 7% of the bidding however deep it runs, and there the gap exceeds the step between adjacent ranks,
so a tagged player prices below the rank beneath him and the ordering inverts. Adding a second position
halves the top share and it stops. Every board this model is asked to price carries five, and the real board
reaches the same 7% without inverting, because its tag prices are large enough to return most of it.

**Pricing him at the board's own rate instead is two per cent out, not a quarter.** That figure was measured
on a board this repository no longer produces and it is worth saying what replaced it, because the two halves
of the counterfactual pull opposite ways and very nearly cancel. Taking Ja'Marr Chase at his real tag of 61:
putting him back in the bidding on its own would price him at 92 against his 96, and putting his tag back in
the pot on its own at 102. Doing neither — charging him the rate the untagged pay — gives 98. The naive
figure looks nearly harmless because it makes two errors of opposite sign, which is a reason to distrust it
rather than to keep it.

#### It settles, and where it cannot it says so

The loop used not to reach a fixed point, and the reason was the comparison across two worlds rather than
anything about the football: two expiring players on one roster could each be the better tag once the other
was tagged, for ever. Reading a team's candidates off one rate closes that, and closes it by construction
rather than by tie-breaking. A second route closed with it — the counterfactual counts the roster spots of
the world it describes by the same rule the board counts its own, where it used to add one to the board's,
which invents a spot on any board holding more tags than spots left and reports the saving a dollar short.
Each mechanism was reproducible on demand beforehand; with both closed, some 28,000 synthetic boards
produced no tag set that cycles at all.

**A tie is still broken on the more valuable player**, and still has to be: surpluses are whole dollars off
levels carrying a standard error of seven points or so, so two of them tying says only that the model cannot
separate what the tag *saves*. It can still separate what the two players are *worth* — `VALUE` is worth
priced against the cap, before any adjustment for how this league bids — so the tie goes to the better
contract, and the same way every time.

**The bounded loop and its warning stay.** Neither closure is a proof: prices are whole dollars, and a dollar
of truncation is not something either argument rules out. What survives in the record is slower rather than
circular — tagging is self-reinforcing, since a tag returns less to the pot than the share it takes out of
the bidding was earning, so each one lifts the rate and pulls the next team over the line. Where teams are
finely enough separated they come in one at a time, and a synthetic board of forty of them takes fourteen
rounds against a budget of ten. **That budget is roughly twice what a league this size can want**: the queue
advances in blocks rather than a team at a time, so over six thousand synthetic ten-franchise boards the
slowest settles in five rounds, and the 2026 board settles in three.

What matters when it does stop short is that the set the board **reports** is the set it was **priced with**.
Those came apart once: the loop ran out of rounds part way round and the tags were re-read from the round
after the last pricing, so a team was told to tag one player while all 106 prices, his own included, assumed
it had tagged another. The prices were never wrong — only the column naming the tag was. `AuctionValuation`
stops on the set it priced and prints a warning naming the teams it had not finished deciding about, rather
than letting half a turn read as an answer. `FranchiseTagSettlementSpec` holds it to that, on a board built
to overrun the budget.

## What it produces for 2026

The highest `PRICE` is Ja'Marr Chase, which no one pays because he is tagged well below it.

<!-- figures: fuad/board -->

| FIGURE | VALUE | Actual 2025 |
| --- | --- | --- |
| Players | 105 | |
| Total cost | 1936 | |
| Top price | 105 | $100 |
| Players above $1 | 97 | 70* |
| Top 40 price | 82.9 | 87% |
| Teams tagging | 8 | 7 |

\* Not the same denominator: the model's count is over the priced pool and the league's over signings. Among
players who actually signed the two are within a handful of each other every season.

Position shares are the ones the league actually spends, since `MARKET_WEIGHT` is 1.0. `SHARE` is what the
board came out at and `TARGETSHARE` what the calibration aimed for:

<!-- figures: fuad/positions -->

| POS | PLAYERS | RESERVE | SHARE | TARGETSHARE |
| --- | --- | --- | --- | --- |
| QB | 19 | 0.9 | 23.8 | 23.7 |
| RB | 22 | 1.0 | 32.0 | 32.3 |
| WR | 24 | 1.1 | 33.1 | 33.5 |
| TE | 22 | 1.0 | 9.6 | 9.4 |
| PK | 18 | 0.9 | 1.5 | 1.0 |

**Those two columns do not match, and the reason is not a failure of the calibration.** It hits the target
exactly — the shares of value it hands on are `TARGETSHARE` to the decimal, and bending each position's
curve to its own steepness leaves those totals alone. What moves them afterwards is the dollar reserved for
every roster spot still to be filled, which comes to about 5% of the pot and is **handed out by headcount
rather than by worth**. `RESERVE` is each position's share of it.

So a position carrying many cheap players finishes above its target and one carrying fewer dearer players
below it. Add `RESERVE` to `TARGETSHARE` scaled down by the reserved fraction and every position lands
within a third of a point of `SHARE`. Kicker is the plain case, and its row in the table above says it: a
sixth of the board's players against a `SHARE` almost the whole of which is minimum bids.

Eight tags are predicted, one apiece to eight of the ten teams. It was nine before the steepness was
refitted from the record: quarterback bid at 1.44 made Lamar Jackson worth tagging and at 1.01 it does not,
which is the single largest thing that correction did to this board.

The concentration is the number to watch, and it has now been pushed both ways. Splitting rate from
availability raised the top of the board, the best players losing less to the injury smear than the deep
ones did, and taking each rank's outcome spread from its own neighbourhood has since given some of that back
at the other end: a deep rank's seasons run wider than the position's, and value over replacement is convex,
so the bench is worth more than one pooled distribution said. Refitting the steepness from the record pushed
the same way again. The top forty now hold 82.9% of what open bidding would pay against the 87% the league
actually spent in 2025, having been a little over it before both changes.
That is a point of drift toward a flatter board than the record rather than a steeper one, and it is small
either way, but it is in the direction the model is least able to check: the tag keeps the very top from
ever being priced in the open.

## Team context

`-t teams` reports what each team brings to the auction: roster and contract counts, positions already
signed, free cap, and **exposure** — what keeping every one of its expiring players would cost, against the
cap it has to do it with.

It also reports how many players a team must actually buy. `MINSIGN` and `MAXSIGN` are the roster bylaws
(23 and 30) less what is already signed and less the five the rookie draft supplies, so a team with two
spots to fill and one with nine are not read as being in the same auction on identical cap space. The
rookie figure is rounds times one, as the model assumes league-wide; a team that has traded picks will hold
more or fewer.

`NEEDS` names any position a team holds fewer players at than the lineup requires, as `PK:1`. It is the one
piece of roster legality no price can carry, since the position it almost always names is the one with no
curve behind it.

None of this moves a price, deliberately. An auction clears at the highest bidder rather than the average
one, so the outliers come from one team being thin at a position with the money to fix it. Lamar Jackson's
$100 in 2025 went to a team holding one quarterback under contract with $251 free, which spent $183 on the
position and finished with five.

It is reported rather than priced because it does not predict well enough to price. Team states differ
enormously, and the relation between being stretched and letting players go is nevertheless nothing:

<!-- figures: fuad/stretch key=SEASON+FRANCHISE -->

| SEASON | FRANCHISE | TEAMSEASONS | MINSTRETCH | MAXSTRETCH | STRETCHCORR |
| --- | --- | --- | --- | --- | --- |
| 2023-2025 | ALL | 29 | 0.30 | 1.92 | 0.13 |

A team may go into an auction owing six times what it has to spend or a third of it, and knowing which
tells you almost nothing about how much of its roster it will hold. The extreme case is on the same table:

<!-- figures: fuad/stretch key=SEASON+FRANCHISE -->

| SEASON | FRANCHISE | EXPIRING | KEPT | EXPOSURE | FREECAP |
| --- | --- | --- | --- | --- | --- |
| 2025 | 0003 | 20 | 3 | 355 | 289 |

Twenty players up, worth more than the whole budget, and three kept. Real one at a time, invisible on
average. Turning that into a price adjustment would be fitting noise; handing it to a reader who knows the
league is not.

## Known limits

- **The rookie allowance inside the auction is still flat.** Rookies themselves are now priced over their
  contracts in [Rookies](#rookies), but that board does not feed back into this one: the pot still loses a
  fixed 3.3% and a fixed five spots a team. Both could be computed per season from the rule and the picks
  actually held, and both are left as they are because moving them moves every price here.
- **Nothing prices team need or budget, so top-end prices are ceilings rather than clearing prices.** Value
  over replacement is the most a rational team would pay, but an auction clears at what the *second* bidder
  will go to, and that gap is widest at the very top. A desperate buyer with cap to spend is averaged into
  a league-wide rate rather than identified. Treat the top of the board as a walk-away number, and `-t
  teams` as the stopgap; pricing at the highest bidder needs an auction simulation.
- **Prices are still per player, so nothing on this board sees a roster.** Two players are priced
  identically whether they cover each other's byes or share one, and a spare at a position a team already
  starts two of is priced as though it were his first. `-t roster` answers that separately, in points and
  for one named team; see [STRATEGY.md](../STRATEGY.md#the-roster-reports). It is deliberately not fed back
  into price, which is a league-wide clearing rate and would stop being one. That division is also what
  keeps the cost of a missed week from being charged twice; see [3c](#3c-why-a-missed-week-costs-nothing-which-is-not-the-same-as-being-free).
- **The spread cannot tell volatility from disagreement.** It is realised variation, so a genuinely erratic
  player and one the consensus simply misjudged look identical. For pricing that is the right total, but it
  means the model has no notion of a safe pick versus a risky one — which is why `PTSLOW` and `PTSHIGH` are
  the range of the ranks around his, scaled to him, and must not be read as his own. That the range now
  varies down a position says where on the board a player sits, never which player he is.
- **Contract length is not modelled.** A salary buys one season, and length is chosen jointly with price
  rather than being an input. The record shows the shape the cut penalty implies: 87% of signings above $40
  are one-year deals, against 70% at $1-2, and the dynasty-minus-redraft gap tracks length four times more
  strongly at the cheap end (correlation 0.290 against 0.12-0.13 higher up). `DYNRANK` is carried on the
  board so a plan can weigh this; nothing prices it. See
  [LEAGUE_RULES.md](LEAGUE_RULES.md#contract-length).
- **Nine of ten teams are predicted to tag**, at the high end of the observed 5-to-9. 2026's tag prices are
  low against the market because they are computed from 2025 salaries. The set settles in three rounds, and
  the closest decision on the board is separated by a dollar: see
  [the tag loop](#6-franchise-tags-iterated-to-a-fixed-point).
- **Tag surplus asks what a tag saves, never whether the player is worth it.** It is what he would have
  fetched had his team tagged nobody, less the tag price, so the tag a team is told to use is the one it
  saves most on even where that player is priced above what he is worth. **Value enters only to break a
  tie**, which is a deliberately small role: it decides between two tags the model cannot otherwise
  separate, and never overrides a surplus that is genuinely larger. A team whose best saving is on a player
  it should not want is still told to tag him.
- **The calibration is fitted on three seasons.** Positional shares swing hard year to year, and dropping
  2022 as a transition year buys accuracy at the cost of a thinner sample.
- **The market price of a tagged player is never tested against anything the league did.** It is a
  counterfactual for a player who will not reach the auction, and no observed price can confirm or refute
  it. What is checked is that it is the price of a world the model can actually price — the same board with
  that team's tag lifted, to the dollar — and not that the world is the right one to ask about.
- **Kicker value rests on a replacement nobody has to accept.** See [Kickers](#kickers). The position is
  priced like any other now, and it is the one place where value and price disagree by a factor rather than
  a margin — which is either an inefficiency or a limit of what value over replacement can say about a
  position whose starters can be replaced from the waiver wire in a week.
- **The curve is only as good as the ranking it is indexed by.** A season is attributed to whatever rank the
  consensus gave that player, so a year the consensus was collectively wrong about is levelled into the
  rank, not identified as an error. That is the right total for pricing and no help at all in spotting one.
- **Two ranked seasons a year or so go missing to source quirks rather than to injury.** nflverse takes a
  player's position from the roster, so Travis Hunter is a `CB` in 2025 and his receiving never enters. A
  player the extract does not carry is indistinguishable from one who never played, and scores zero either
  way. See [DATA.md](../DATA.md).

## Value and price are separate numbers

They answer different questions and blending them answered neither. The board reports both.

- `VALUE` — worth: value over replacement priced against the cap, with no adjustment for this league.
- `PRICE` — what open bidding here is expected to settle at: value calibrated to the positional shares the
  league actually spends (`MARKET_WEIGHT` is now 1.0) and to how steeply it bids **within** a position. For
  a franchised player, who reaches no open bidding, it is the price in the world where his own team tagged
  nobody — a different basis, flagged by `FRANCHISED`. See
  [the tag loop](#6-franchise-tags-iterated-to-a-fixed-point).
- `COST` — what the holding team actually pays, which is the tag price for a franchised player.
- `ACQUIRE` — what it takes to prise him off the team that holds him.
- `EDGE` / `BAND` — `VALUE − PRICE`, banded rather than given to the dollar, because it is the difference
  of two noisy estimates and a precise figure would overstate the resolution.

Alongside them the board carries what a plan needs in order to reason without going behind it:

- `PTS` — expected points: what this rank has historically been worth, under the rules being priced.
- `PPG` / `G` — the two halves that season is the product of: what he scores in a game he plays, and how
  many games he plays. They multiply back out to `PTS`, to the one decimal place the board prints them at,
  and that costs something to arrange — the level is anchored back to the mean season the position actually
  had, which sits above the product of the two separate means, so `PPG` is the rate that level implies
  rather than the raw mean behind it. The factor is `ANCHOR`, and it differs by position:

  <!-- figures: fuad/positions -->

  | POS | ANCHOR |
  | --- | --- |
  | WR | 1.025 |
  | QB | 1.063 |

  They are carried because the two halves are differently caused and the total hides which one a player is
  made of. Availability is the one that moves with rank, and only at quarterback: past about rank 26 a
  quarterback is a backup who plays when somebody gets hurt, while a receiver that far down plays nearly as
  much football as the best one and simply scores less doing it. See
  [2b](#2b-a-season-is-a-rate-times-an-availability) for the figures.

  **`G` does not break a tie inside a tier.** Availability is smoothed across ten ranks either side, because
  rank predicts it weakly — see `GAMESCORR` in [2b](#2b-a-season-is-a-rate-times-an-availability) — and a
  tier is a band of neighbouring levels, so the ranks in one share nearly the same figure. Across a whole
  tier of 2026 the widest spread is 0.7 games and the usual one is 0.3. Reading an order into that is the
  same false precision `TIER` exists to remove.

  **`G` is not `AVAIL`.** `G` is how much football he plays; `AVAIL` is the chance he ever reaches another
  team, which is a fact about the right of first refusal and nothing to do with his health.
- `PTSLOW` / `PTSHIGH` — the same rank in a bad season and a good one, at the 10th and 90th percentile of
  the seasons the ranks around his have produced. **The spread belongs to the board, not to the player.**
  Two players at one rank get the same proportional range, because realised variation cannot tell an erratic
  player from one the consensus misjudged. A wide range means this stretch of the board is wide — and the
  deep end of every position is much wider than the top, which is a fact about where the ranking stops
  making a confident claim; it never means this player is the risky one. See
  [3b](#3b-outcome-spread-which-is-what-a-bench-is-worth).
- `BYE` — the week he is off. A fact of the schedule rather than a judgement about him, and the one thing
  about a particular player the model is willing to know.
- `TIER` — the band of ranks at this position the curve **cannot tell apart**, 1 being the best. Levels are
  means of about 45 realised seasons and carry a standard error of seven to nine points at quarterback, so the
  curve resolves QB2 from QB17 and has no business resolving QB10 from QB14. Players sharing a tier are
  ties: choose between them on price, bye or roster fit, never on the order they sit in. Compare only
  within a position, and see [What a tier is for](#what-a-tier-is-for).
- `RANK` / `DYNRANK` — his consensus rank for this season, and for the long run. Everything above is built
  on `RANK` alone, because a salary buys one season. `DYNRANK` is carried and never priced, for the second
  decision taken at the same moment as the price: how many years to sign him for. Dynasty rank is the
  better predictor of what a signing goes on to score over the following two seasons, at every price band —
  but the top of the board cannot act on it, since a long deal there is unwritable under the cut penalty.

Within-position steepness is fitted per position as `price ~ value^gamma` over the same seasons' signings,
with the minimum-bid ones treated as the censored observations they are — see
[how steeply the league bids](#how-steeply-the-league-bids-which-is-fitted-and-not-chosen), where the
estimator and what it rests on are set out:

<!-- figures: fuad/positions -->

| POS | GAMMA |
| --- | --- |
| QB | 1.01 |
| RB | 1.19 |
| WR | 1.08 |
| TE | 0.87 |

Tight end is the fragile one — thirty-three signings, a quarter of them at the minimum bid, and it has moved
across most of this range as seasons have come in and out of the fit. **Running back is the steepest position
this league bids and kicker much the flattest**, with quarterback almost exactly in line with value.

That last is a correction. Quarterback was carried at 1.44 for as long as this constant existed, and read as
the league paying an elite-starter premium that value over replacement will not produce on its own, since
superflex starts twenty quarterbacks and so sets a high replacement. The premium is not in the record: it was
an artefact of fitting the line over the signings above the minimum bid alone, which reads every market as
flatter than it is and the flattest ones as flatter still. This belongs to price and never to value: it
describes behaviour, not worth.

The split immediately shows what the blend was hiding. This league **overpays for receivers and underpays
for running backs**, and the size of it is on a committed figure rather than in a sentence: `TARGETSHARE`
is the share of the pot a position takes and `VORSHARE` the share of the board's value it holds, before any
calibration touches it.

<!-- figures: fuad/positions -->

| POS | TARGETSHARE | VORSHARE |
| --- | --- | --- |
| WR | 33.5 | 22.9 |
| RB | 32.3 | 36.9 |

Receiver is bought nearly half again above what it is worth and running back at about seven eighths of it. Named by
position rather than by player for the reason [STRATEGY.md](../STRATEGY.md#what-the-board-carries) gives about
ranks: a position is on a figure that is committed and checked, where the two players this paragraph used
to name were on a board that is regenerated and never kept — and both of their numbers had gone stale.

## What a tier is for

The board reports prices to the dollar off levels that are good to seven points or so. Across the flat middle
of a position that is a false precision, and it shows up as an ordering the evidence does not support.

Quarterback is the clearest case. The curve is nearly flat across the middle of the position, and each
level carries a standard error of seven to nine points:

<!-- figures: fuad/curve across=POS field=PTS -->

| Rank | QB |
| --- | --- |
| 8 | 210.6 |
| 10 | 204.3 |
| 11 | 204.0 |
| 12 | 203.5 |
| 14 | 195.6 |

<!-- figures: fuad/curve across=POS field=SE -->

| Rank | QB |
| --- | --- |
| 8 | 7.7 |
| 10 | 7.4 |
| 11 | 7.3 |
| 12 | 7.8 |
| 14 | 8.8 |

<!-- figures: fuad/curve across=POS field=TIER -->

| Rank | QB |
| --- | --- |
| 8 | 4 |
| 10 | 4 |
| 11 | 4 |
| 12 | 4 |
| 14 | 5 |

QB10 and QB11 are half a point apart on estimates carrying seven. That is not a claim about which is
better; it is the curve saying it cannot separate them.

What then amplifies a point of noise into dollars is worst at quarterback: superflex starts 20 of them, so
replacement sits at QB20 and value is a small difference against a large number. `PRICE_STEEPNESS` used to
be blamed for a second helping of it, quarterback being carried at 1.44 and read as the steepest position on
the board. It is not — refitted from the record it comes out at 1.01, which stretches nothing — so the
amplification here is replacement alone, and it is enough on its own.

`TIER` says so directly. Lawrence, Prescott and Mahomes all come out tier 5, priced $34, $34 and $30, and
the spread between them should be read as nothing at all.

Splitting rate from availability shrank the errors — from ten to twelve points down to seven or nine — but
did not remove them, and it was never going to: the flatness across the middle of the position is a real
feature of quarterback scoring, not an artefact. Fewer ranks now sit in one tier than before, and the ones
that remain together genuinely belong together.

Tiers are built by walking the ranks **in order of level, not of rank**, grouping while a rank stays within
one standard error of the best in its tier. Ordering by rank would put a boundary wherever the curve dips
and recovers — it split Lawrence from Mahomes on a one-point difference, which is the fault this exists to
remove. A tier is therefore a set of ranks rather than a range, and need not be contiguous.

The flatness itself is real, and it is the most useful thing the model says about quarterbacks: it is why
the mid tier costs half as much for a fifth fewer points. Trust the flat region; distrust the order inside
it.

## Who is in the pool, and who is not

Three groups, treated differently.

**Expiring contracts** are restricted free agents. Their team may match.

**Unrostered veterans** are unrestricted, and go to the highest bid. Historically 7 to 22 a year are signed
from outside the pre-auction rosters. **They are the only players on whom a positive edge is available**,
for the reason in the next section.

**Both are cut off at the same depth**, which is the point below which the curve stops making a claim — the
last rank levelling above a quarter of the position's best:

<!-- figures: fuad/positions -->

| POS | PRICEDDEPTH |
| --- | --- |
| QB | 36 |
| RB | 65 |
| WR | 89 |
| TE | 45 |
| PK | 25 |

Kicker's 25 is set by hand, being the one position that rule cannot bound. The test is a level under a
quarter of the position's best, and it never fires at kicker because the curve there is nearly flat — the
42nd ranked kicker still levels around three fifths of the first, so the whole ranked pool would come onto
the board. Flat is not the same as valuable: only ten kickers start, so everything past about the eleventh
is below replacement and worth nothing to anybody. Left uncapped the board carried 29 kickers, 27 of them
at the minimum bid, and the dollar reserved for each came to more than the position's whole budget.

**25 is not a round number.** It is exactly the deepest rank the league has ever paid for at the position,
and it covers all but a handful of the kickers anybody rosters:

<!-- figures: fuad/depth -->

| POS | PRICEDDEPTH | SIGNINGS | DEEPEST | MEDIANRANK | P90RANK | WITHINDEPTH |
| --- | --- | --- | --- | --- | --- | --- |
| PK | 25 | 40 | 25 | 6 | 18 | 96.3 |

`MEDIANRANK` and `P90RANK` are the other half of the point: teams do not sign kickers in rank order, so the
depth has to cover a spread rather than a top slice.

**Set by hand, but set in the same place as the others.** It is a cap on `PRICEDDEPTH` itself rather than a
filter applied where the auction pool is assembled, which is what it used to be — and while it was, kicker
had two depths at once. The board priced 25 ranks while the curve took the position's spread, its outcomes,
its census and its anchor over all 42. Those seventeen ranks are where kicker's lost seasons sit: the
consensus ranks more kickers each year than ever record a stat line, so the position reported a tenth
percentile of **0.00** — one ranked season in ten worth nothing at all — against 0.23 to 0.36 everywhere
else. That was true of ranks 26 to 42 and never true of a kicker anybody would bid on. One depth doing both
jobs puts kicker's spread back among the other four; the figures are below.

One depth for both, because an expiring contract does not have to be re-signed: if nobody bids, the player
goes back into the pool like anybody else. A rank too deep to be worth bidding on is too deep whoever holds
it. Pricing every expiring contract regardless of rank had put 43 players on the board past the cutoff that
applied to free agents — holding 1.9% of all value between them, 36 of them at the minimum bid — while
excluding unrostered players like Dontayvion Wicks at WR67 who would be signed.

The depth is taken from the curve rather than set by hand. The four numbers it replaced — QB 30, RB 45, WR
50, TE 25 — were shallower than the deepest rank the league has actually paid for at **every one** of those
positions, which is the whole argument for reading a depth off the curve instead of writing it down:

<!-- figures: fuad/depth -->

| POS | PRICEDDEPTH | DEEPEST | MEDIANRANK | P90RANK |
| --- | --- | --- | --- | --- |
| QB | 36 | 45 | 19 | 32 |
| RB | 65 | 102 | 30 | 63 |
| WR | 89 | 112 | 40 | 85 |
| TE | 45 | 57 | 15 | 34 |

`DEEPEST` is one signing and a depth should not chase it, which is why `PRICEDDEPTH` sits below it at every
position; `P90RANK` is the rank nine signings in ten come at or above, and the curve's own cutoff lands
above that everywhere.

**Rookies are excluded entirely from the auction pool.** They are drafted separately afterwards and cannot
be bid on. Two simple allowances stand in for them here, both checked against the record by
`AuctionValuationSpec` — and they remain allowances even now that rookies are priced properly in
[Rookies](#rookies), because changing them would move every price on this board:

- **Roster spots**: five rounds times teams. Nearly every pick is kept — 38, 46, 52 and 49 rookies rostered
  at week 1 across 2022-2025, against 40, 45, 50 and 50 picks.
- **Budget**: 3.3% of the pot, taken off the top. Rookie prices are set by rule off the previous year's
  positional prices and come out very low: $42, $52, $69 and $76 for the whole league, which is 2.9% to
  3.9% of the auction pot.

That leaves the spots the auction has to fill, as `teams x 30 - under contract - 5 x teams`. It predicts
what the league actually signs closely: 65 against 71 in 2022, 90 against 93 in 2023, 103 against 96 in
2024, and 92 against 92 in 2025. A dollar is reserved per **spot**, not per player on the board, since the
pool holds everyone who could be bid on and only the spots get filled.

## Rookies

A rookie is the one player in this league nobody can bid on. The auction runs first and rookies are held out
of it (bylaw 5.1), then they are drafted, and what they cost is set by formula rather than by anybody's
willingness to pay. So none of the board above applies to them: there is no market to clear, no restricted
free agent to prise loose, and no price to be a bargain against.

**What replaces it is a contract rather than a price.** A pick buys one to five seasons at a salary fixed
the moment he is taken, and for all but the first few picks of a strong class that salary is a dollar. A
rookie class has never cost as much as 3% of the cap. See
[LEAGUE_RULES.md](LEAGUE_RULES.md#rookie-salaries) for the rule and for the check that it reproduces every
salary the league has charged.

That makes the question a different one. Not *what is he worth this year against what he will cost*, which
is what a salary buys and what the board answers, but *what is he worth across five years against a dollar*.

### Five curves, from one levelling

The method is the same one everything else here rests on, run five times against a shifted ranking.
[RealisedSeasons](#the-chain) scores whichever ranking it is handed against a season's statistics, so asking
what a rookie is worth in his third year is asking it to score the class of three years ago against this
season. Nothing about the curve, the rate and availability split, the smoothing or the tiering needs to know
that the rank being levelled is a rookie's.

Rookies are levelled at their **positional** rookie rank. Rookie RB1 is a thing that happens once a year and
can be pooled across classes; "the fourth rookie taken" is a different animal in a strong class and a weak
one.

**The later years rest on fewer classes, and the figure says so.** Nine classes have a first season in the
collected record and five have a fifth:

<!-- figures: fuad/rookiecurve key=POS+YEAR -->

| POS | YEAR | CLASSES | PTS1 |
| --- | --- | --- | --- |
| QB | 1 | 9 | 90 |
| QB | 2 | 8 | 155 |
| QB | 3 | 7 | 112 |
| QB | 4 | 6 | 100 |
| QB | 5 | 5 | 87 |
| RB | 1 | 9 | 131 |
| RB | 2 | 8 | 127 |
| RB | 3 | 7 | 127 |
| RB | 4 | 6 | 116 |
| RB | 5 | 5 | 121 |
| WR | 1 | 9 | 99 |
| WR | 2 | 8 | 122 |
| WR | 3 | 7 | 119 |
| WR | 4 | 6 | 99 |
| WR | 5 | 5 | 96 |
| TE | 1 | 9 | 72 |
| TE | 2 | 8 | 74 |
| TE | 3 | 7 | 71 |
| TE | 4 | 6 | 76 |
| TE | 5 | 5 | 53 |

**This table is the argument for the whole thing, and it is not the same argument at every position.** The
best quarterback of a rookie class levels at 90 points in the year he is drafted and 155 in the year after;
the best receiver at 99 and 122. The best running back levels at 131 and then 127 — he arrives finished.

So a rookie running back is a win-now pick in a way a rookie quarterback is not, and a board that priced
either of them on his first season would be wrong about both: it would overrate the back relative to the
quarterback by roughly the whole of the difference. `RookieSeasonsSpec` asserts this shape rather than
describing it, because if it ever stopped holding, a pick would be an auction lot with a fixed price and
most of this could be deleted.

### Two indices, because one of them cannot see the class

**The rookie ranking orders a class and says nothing about how good the class is.** "Rookie RB1" is the best
back of that year whether that is a generational prospect or a committee back in a bare one, and nine of them
are levelled as a single object. That is an incomplete index by construction, and class quality is both large
and visible before the draft:

<!-- figures: fuad/rookieclass key=SEASON -->

| SEASON | TOP1 | TOP2 | TOP3 | TOP4 | TOP5 | MEAN | FIRSTQB |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 2018 | RB4 | RB19 | RB16 | RB20 | RB22 | 16.2 | 43 |
| 2019 | RB16 | RB19 | RB20 | WR27 | WR37 | 23.8 | 29 |
| 2022 | RB6 | WR20 | RB17 | WR28 | WR31 | 20.4 | 31 |
| 2024 | WR5 | WR9 | WR17 | TE3 | WR30 | 12.8 | 21 |
| 2025 | RB3 | QB20 | RB6 | WR15 | WR16 | 12.0 | 22 |
| 2026 | RB4 | QB17 | WR15 | RB21 | WR25 | 16.4 | 1 |

Those are where each class's five best rookies sat among their **position's** dynasty assets — the same
consensus, making the cross-class comparison the rookie ranking refuses to make. 2019's best five were the
sixteenth to thirty-seventh at their positions; 2024's were the fifth, ninth and seventeenth receivers and
the third tight end. **2026 is a below-average class**, close to 2018 and well clear of 2019 and 2022, and
nothing in a within-class rank could tell you any of that.

**Positional rank and not overall, because the source changed format.** `FIRSTQB` is where the dynasty
ranking puts the best quarterback in football, and it sits between 21st and 43rd in every season from 2017
to 2025 and **first** in 2026: the export went superflex. Measured on overall ranks that break inflates every
quarterback and pushes every other position down, which made 2026 read as the weakest class since 2019 when
it is nothing of the kind. The column is on the table so that the break stays visible instead of being
something a reader has to already know.

The valuation is unaffected, because it blends on positional rank — the same players in the same order
whichever format the ranking is in. This figure was not, and said so for one commit.

So the dynasty index is used to **adjust** a rookie's level rather than to supply one. Two more obvious
things were tried first and both failed, for reasons worth keeping.

Reading a rookie's dynasty rank off the **veteran** curve mixes populations: a veteran ranked 28th at
quarterback who plays is a backup in relief at 13 points a game, and a rookie ranked 28th who plays has won a
job. Averaging a job-winner's rate with a backup's is not an estimate of anything, and it needed a fitted
calibration to stand up at all. Building a rookie curve **indexed** by dynasty rank ran out of data instead:
nine classes spread over forty dynasty ranks left 27 of 117 rookies with any level, and every rookie worth
drafting fell through.

**What the record supports is an ordering claim, so it is used as one.** Holding rookie rank fixed, the
rookies the dynasty ranking rates above their peers go on to score more — and that needs no per-rank sample,
because every rookie contributes to one pooled relationship. A rookie is compared against the dynasty rank
that rookies at his rank usually hold, and his level moves with the gap.

**Tapered, because the claim is only true at the top of a class.** Measured in a sliding window down the
rookie ranks, the signal runs:

| rookie rank | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| signal | 0.42 | 0.42 | 0.43 | 0.33 | 0.23 | 0.16 | 0.09 | 0.05 | −0.01 |

A plateau over the top four and an exponential decay of three ranks fits that closely, and it is continuous
everywhere — there is no rank at which a rookie's treatment jumps. A cutoff would put a cliff on the board,
which this project has already paid for once.

**One sided, because only one side of it is there.** Binned by residual, the rookies the dynasty ranking
rates *below* their peers score what the ones it agrees about score — mean rates of 8.73 and 8.70 — while
the ones it rates well above score 11.62. No penalty is applied, because none is in the record. What is
left is a narrow claim and the one the evidence makes: **the dynasty ranking picks out the exceptional
prospects and says nothing useful about the rest.**

**And capped at the ninetieth percentile of its own fitting sample.** The slope is 0.258 with a correlation
of 0.242 over 57 seasons — a rookie placed twice as high as his rookie rank implies scores about 20% more per
game. Uncapped, this year's best back sits past the 95th percentile of everything that slope was fitted on,
and reading a weak fit off the end of its own data lifted his rate two fifths and his contract from $233 to
$491. Value over replacement is convex, so a rate moved by two fifths moves a price by rather more.

Class quality enters here and nowhere else, and per position, which is what a year thin at back and deep at
receiver requires.

### How widely a rookie's seasons actually run

The board's rule is that a spread belongs to a position and never to a player. Rookies were given the
**veteran** position's spread, which is a distribution of established players, and that is close to right at
the top of a class and badly wrong at the bottom:

<!-- figures: fuad/rookiespread key=POS+RANK -->

| POS | RANK | SEASONS | MISSING | P50 | P90 | MAX |
| --- | --- | --- | --- | --- | --- | --- |
| RB | 3 | 64 | 8 | 0.76 | 1.26 | 2.03 |
| RB | 8 | 88 | 17 | 0.72 | 1.63 | 3.18 |
| RB | 15 | 89 | 36 | 0.51 | 1.91 | 3.18 |
| RB | 25 | 53 | 64 | 0.55 | 2.48 | 2.53 |
| WR | 3 | 64 | 0 | 0.91 | 1.36 | 1.74 |
| WR | 8 | 88 | 7 | 0.84 | 1.45 | 1.91 |
| WR | 15 | 87 | 22 | 0.79 | 1.71 | 2.49 |
| WR | 25 | 88 | 44 | 0.61 | 2.19 | 2.65 |

A rookie in his position's top few is a **narrower** proposition than a deep one — 1.26 and 1.36 at the
ninetieth percentile against 2.48 and 2.19 by rank 25 — and almost all of his seasons happen. By rank 25
more than half never happen at all.

`MISSING` is the column that matters, and it is not the spread: it is the share of seasons with no games in
them. **That is where a deep rookie's value lives**, because value over replacement is convex. Five seasons
of fifty points are worth nothing five times over; one season of two hundred is worth seventy-eight. A rank
whose outcomes are bimodal is worth real money at a mean that looks worthless, and a spread that cannot
reach two hundred cannot see it — which is why every fourth round pick used to price at zero, and the sheet
gave a reader no ordering at all where he most needed one.

So the spread is measured on rookies, **at every rank rather than only the deep ones**. Applied at the top
of a class it barely moves anything, which is the point: the transition comes from the data rather than from
a boundary somebody chose.

**A sliding window of neighbouring ranks, and never fixed bands.** That distinction cost real dollars before
it was made. Banding ranks 1-5, 6-10, 11-20 and 21 up put an edge between the first two bands, and Omar
Cooper at WR5 and Denzel Boston at WR6 — whose levels are within one per cent of each other — were priced
$52 and $85 because of which side of it they fell. Near replacement that is not a rounding difference: a
receiver worth nothing at his mean draws all his value from the right tail, and a tenth more tail is most of
a doubling.

**And each season is a ratio against the level of the rank it came from, not against the window's mean.**
That is the second thing this had wrong, and it was the more expensive of the two. A multiplier is applied
to the level of the rank being valued, so it has to be normalised against the level of the rank it came out
of — otherwise the arithmetic does not reproduce the season it was built from. Normalised on the window
instead, every rank sitting above its neighbours was overstated and every rank below understated: rookie
QB1's rate is 25.6 against a window mean of 15.3, so each of his realised seasons arrived **68% too large**,
and his second year priced at 96 points over replacement where it belongs at 41.

`WIDE` on that table marks a rank whose own neighbours were too few and whose window had to be widened —
quarterback and tight end mostly, nine classes not ranking enough of them.

### From points to dollars

A rookie's points over replacement are converted to dollars **by equivalence**: a rookie worth eleven points
over replacement is worth what a veteran worth eleven points over replacement costs, interpolated along the
board's own value-to-price pairs at his position. A single dollars-per-point rate would be the same claim
with the board's shape discarded, and the shape is most of what separates the top of a position from its
middle — the auction is deliberately steepened.

Above the most valuable player on the board the price is extended at the rate of the top pair rather than
held flat. A rookie can be worth more than anyone *available*, since the best players in the league are
under contract or franchised and never reach the auction at all.

Three assumptions, none of them small:

- **A future season prices like this one.** Year four's points are converted through the board being priced
  now, since nothing can know that year's cap, pool or ranking. What this assumes is that the price of a
  given amount of value holds, not that any player or any rank does.
- **Replacement is this season's**, for the same reason.
- **No discount is applied to a later year.** A dollar of surplus in year four counts as a dollar, because
  the league's currency does not carry interest: cap space cannot be saved between seasons, so a dollar next
  year is not a dollar this year invested. What the horizon costs is that later years are levelled off fewer
  classes, which `CLASSES` reports rather than discounts.

### The spread that priced rookies above the league

Worth recording, because for an afternoon it looked like a finding.

Levelling a rookie rank off **its own** outcome spread priced rookie quarterbacks at $151 in their third
year, against a board whose most expensive player is $89, off a level of 112 points against a veteran QB1's
245. A rookie worth nearly twice the best quarterback in football, and it was arithmetic rather than
insight.

The cause is that an outcome multiplier is a ratio of **season totals** and it is applied to a **rate**. That
is sound while a rank's expected games are close to the games behind the seasons the ratios came from, which
is true of every veteran rank and false of a rookie one: a rookie quarterback rank pools a class that mostly
never played with one that started, so its level carries five or six games where its best seasons carry
thirteen. The ratio of the two arrives as a rate multiplier of three or four, which is then *also* scaled by
the games that produced it. Availability is counted twice, in the direction that inflates.

**The first fix was to hand rookies the veteran position's spread.** It removed the double count and threw
the bimodality away with it, which is what left every fourth round pick priced at zero. What replaced it is
above: multipliers are ratios of **rate**, so a rookie's own distribution can be used without the
normalization that broke it, and availability travels in the games of the same realised season rather than as
a separate expectation applied on top.

`RookieValuationSpec` holds every rookie inside half again the most expensive player on the board at his
position, which is the guard that would have caught the original.

### When they actually go

Value says who to take; the drafts say who will still be there. Each pick of each draft is joined to the
rookie rank the player held that preseason, and the board walked down as they go:

<!-- figures: fuad/rookiedemand key=PICK -->

| PICK | BESTQB | BESTRB | BESTWR | BESTTE |
| --- | --- | --- | --- | --- |
| 1 | 1 | 1 | 1 | 1 |
| 5 | 1 | 3 | 3 | 1 |
| 10 | 2 | 3 | 5 | 2 |
| 11 | 3 | 3 | 6 | 2 |
| 15 | 4 | 5 | 8 | 3 |
| 20 | 4 | 6 | 9 | 3 |
| 25 | 4 | 7 | 12 | 3 |
| 30 | 5 | 9 | 14 | 4 |
| 40 | 6 | 10 | 15 | 6 |
| 50 | 6 | 12 | 17 | 7 |

**Measured over the four superflex drafts and no others, which is a correction rather than a preference.**
A curve can pool nine seasons because a level is points and every season is restated under the rules being
priced. Availability is *behaviour*, and behaviour has no restatement: what the room did in 2019 was done by
teams starting one quarterback, and nothing converts that into what a team starting two would have done.

The difference is the largest figure this measurement produces. Pooled across all nine drafts, the best
rookie quarterback appears to last until pick 15 — which reads as a standing inefficiency in the room, and
is not one. Split at 2022:

| Best QB available at pick | 5 | 8 | 10 | 12 | 15 |
| --- | --- | --- | --- | --- | --- |
| 2017-2021, one quarterback started | 1 | 1 | 1 | 1 | 1 |
| 2022-2025, superflex | 1 | 2 | 2 | 3 | 4 |

**The room adjusted.** Before superflex the best rookie quarterback sat there past the end of round one;
since superflex he is gone by pick 8. Receivers moved the same way and less sharply — the best available at
pick 15 was the fifth and is now the eighth — and running backs did not move at all, which is what a real
lineup effect should look like rather than noise.

Four drafts is thin, and that is the price of measuring the era being drafted in. The floor of three
sightings then bites hard at the deep picks, which report nothing rather than an average over whichever
years happened to run long. The auction board makes the same trade for the same reason; see
`AuctionSpend.SUPERFLEX_SEASONS`.

**And the seasons behind it agree, which is worth checking rather than assuming.** A pooled ladder is only
worth reading if the drafts it pools drafted alike, and the obvious way that could fail is the source: a year
ranking 138 rookies where another ranked 80 might be putting a worse player at rank thirty, in which case the
room would take him later and the pooled figure would average two different things.

<!-- figures: fuad/rookiepace key=SEASON -->

| SEASON | RANKED | PICK1_10 | PICK11_20 | PICK21_30 |
| --- | --- | --- | --- | --- |
| 2022 | 84 | 6 | 16 | 28 |
| 2023 | 95 | 6 | 19 | 23 |
| 2024 | 80 | 6 | 18 | 27 |
| 2025 | 93 | 7 | 16 | 31 |

**It holds.** Ranking length varies by a fifth across those four years and the mapping does not move with it —
2024 is the shortest list and 2023 the longest, and they place a rookie at nearly the same pick.

The reason is that **rankings extend at the tail rather than in the middle**. 2026 ranks 138 rookies against
2024's 80, and the extra names are almost all at the bottom: the share the NFL never drafted runs 3% over
ranks 1-30, 23% over 31-60, 63% over 61-90 and 79% past 91, against 2024's 0%, 10% and 45%. The top of the
list is the same kind of player it has always been, so the ranks a team is actually choosing among are
calibrated on like with like. Only the deep end is stretched, and there the ladder is blank or the draft has
already ended.

The ladder is held to only ever emptying. A median over unequal drafts does not do that on its own: a pick
past 40 exists in three of the four drafts and one past 50 in one of them, so a long year that happened to
leave a good receiver on the board can put pick 41 ahead of pick 40. That is an artefact of which drafts
reached which pick, not a claim that waiting improves the board.

### What it produces for 2026

<!-- figures: fuad/rookieboard -->

| FIGURE | VALUE |
| --- | --- |
| RANKED | 117 |
| TOPTEN | 9 |
| SURPLUS | 1327 |
| DEFERRED | 1163 |
| SALARY | 30 |

Nine rookies expected to go inside the first ten picks, costing $30 between them, holding $1,327 of
surplus over their contracts — of which **$1,163, or 88%, arrives after the season the pick is spent
in**.

That figure is the whole case for the rookie board. It is the part of a pick that no auction dollar can buy
at any price, and pricing a rookie on his first season would report a tenth of it.

Read it against [the class it comes from](#two-indices-because-one-of-them-cannot-see-the-class): 2026 is a
below-average year, so these are smaller numbers than a strong class would produce, and the board says so
rather than levelling every class alike.

### What is worth reading, and what is not

Two columns exist to stop the rest being over-read.

**`VALUE` is the column the sheet sorts on, and it carries no price at all.** What a rookie is worth is a
fact about him; what he costs is a fact about the pick he goes at, and the two do not belong in one number.
At quarterback this year the price runs from $20 at the first pick to $1 by the fifteenth, so any column
mixing them is mostly an assumption about where he lands — and the board reports a rookie at his *expected*
pick, which is an assumption a reader cannot see.

So the player sheet carries value and the pick sheet carries price, and they are joined by the reader at the
pick he is actually making. That removed four columns — salary, the contract length, the surplus and the
deferred share — of which three were the same statement about an assumed pick and the fourth was almost
always five.

`VALUE` is taken over the five years a contract can run rather than over the years the model would sign,
which is a distinction that cost a correction: summed over the recommended length it moved with the pick
too, and by more than the surplus did, because a salary large enough to shorten a contract shortens what is
being summed. Mendoza read $95 at pick nine and $43 at pick one. A column whose whole purpose is comparing
players had a price assumption inside it.

**The three consensus columns are the working, and they are read together.** `FP_ROOKIE` is where the rookie
ranking puts a player at his position and `FP_DYNASTY` where the dynasty ranking does, both as a position and
a rank. The second is what moves his level, and it means nothing without the first beside it: a class's third
receiver usually sits around dynasty WR31, so `WR3` at `WR23` is being told something that `WR2` at `WR25` is
not. That is the whole of why Makai Lemon prices above Jordyn Tyson while the rookie ranking prefers Tyson.
`FP_OVERALL` is the same rookie ranking read across positions, and is what `DEMAND` is keyed on.

**`TIER` is what the evidence can actually separate.** Same rule the auction board tiers ranks by: walk in
order of value, keep a rookie in the current tier while his own upper bound reaches the best value in it,
open a new tier when it does not. Tiered on value rather than surplus, since two rookies the model cannot
separate as players should read as ties whatever they cost.

**`VAL_LOW` and `VAL_HIGH` do the same job across positions**, which is the choice a rookie draft actually
poses — a back against a tight end against a receiver. A tier only compares inside a position; two
overlapping ranges are a tie whoever they belong to.

They are bounds on the **estimate** and not on the outcome: how well nine rookie classes pin down what a
rank is worth, not how widely one career might run. The auction board's `PTSLOW` and `PTSHIGH` are that
other thing, which is why these are named differently.

**And they are asymmetric**, because value over replacement is convex: a level a standard error low loses
less than the same error high gains. That holds for every rookie on the board without exception, which is
why the bounds are two columns rather than one plus-or-minus.

| | low | value | high | range over value |
| --- | --- | --- | --- | --- |
| RB1 | 217 | 294 | 375 | 0.54 |
| WR1 | 145 | 221 | 302 | 0.71 |
| TE1 | 18 | 54 | 96 | **1.44** |
| QB1 | 35 | 109 | 192 | **1.44** |
| QB2 | 18 | 63 | 115 | **1.54** |

**`VALUE` is the expectation over that band, not the value at its midpoint.** Convexity again: pricing at the
level's point estimate understates what a rookie is worth, and understates it in proportion to how badly the
level is pinned down. It is worth a per cent or two at running back and receiver and 17% to 25% at
quarterback and tight end — so the point estimate was quietly marking down the positions the board is least
sure about, and a reader sorting on it would have dropped them for the wrong reason. Integrated over five
points of a normal on the level, the best rookie quarterback goes from $95 to $109.

**A rookie quarterback's contract value runs from a third of the reported figure to nearly double it**, and
that is the honest state of the
position rather than a defect to be tuned away. Superflex starts up to twenty quarterbacks, so replacement
is 209 points a season; only about half the rookie quarterback seasons that happened have ever cleared it,
and a third of them never happened. Value over replacement is convex, so a standard error on a level sitting
near replacement is worth as much as the whole value.

That is worth carrying to the draft as it stands: the board's quarterback numbers are a statement about how
little nine classes can say, not a recommendation to avoid the position.

### Known limits of the rookie board

- **It does not feed back into the auction.** The pot still has a flat 3.3% taken off the top for rookies
  and still assumes five roster spots a team, exactly as before. Those two constants could now be computed
  per season rather than assumed, and are not: changing them moves every price on the auction board, which
  is a separate change with its own evidence to present.
- **Nothing here knows what a team already has.** A pick is priced against league-wide replacement, so a
  rookie quarterback is worth the same to a team starting two of them and to a team holding four. The same
  division the auction board makes, and the same reason: a clearing rate that answered per roster would stop
  being one.
- **Contract length is chosen at the expectation and nothing cleverer.** Bylaw 12.4 wants the length before
  the first season is played, so the rule is the length that leaves the most value over its cost, and
  nothing about what might be learned in year one. At a dollar it almost always says five, and the downside
  it ignores is five dollars.
- **The fifth year is levelled off five classes**, three of them from before the league went superflex. It
  is reported with `CLASSES` beside it for that reason, and it should be read as the weakest column on the
  board rather than as an equal one.
- **A rookie's bye is his team's.** No ranking carries a bye for a rookie — the dynasty export writes 0 —
  and the same week is used in every contract year, there being no schedule for a season four years out.
- **The spread is a rank band's and never a player's.** A rookie deep in a class is given the distribution
  of deep rookies at his position, which says how that group has turned out and nothing about whether he in
  particular is the one who pops. Same doctrine the auction board applies to `PTSLOW` and `PTSHIGH`, and the
  same warning: it must not be read as identifying the risky pick.
- **Class quality is one number per player, and a class is not uniform.** The dynasty blend corrects a weak
  class per position, since it runs off each rookie's own dynasty rank — but a class can be weak at the top
  and deep in the middle, and nothing here reports that shape. `rookieclass` shows only the best five.
- **The blend is half and half because neither index was better**, not because a half is optimal. The two
  order rookie outcomes within a hundredth of each other, so nothing in the record argues for a particular
  weight, and a fitted one would be fitting noise.
- **Availability is four drafts and the curve is nine seasons**, so the two halves of the board rest on very
  different amounts of evidence. That is deliberate — see above — but it means the value column is a much
  firmer claim than the pick column beside it.
- **A pick is priced as a selection and never as currency.** Bylaw 7.2 makes a first round pick the price of
  prising away a franchised player, so a first has an exchange value as well as a use value, and bylaw 7.3
  makes holding one a condition of bidding at all. Nothing here prices either. The board answers which
  player to take with a pick, deliberately and not for want of finishing: what a pick fetches in a trade is a
  different question with different evidence behind it, and the league's own trade history is where it would
  come from.

## Kickers

Kickers were not levelled at all until recently, on the stated grounds that *"the nflverse statistics carry
no kicking"*. That was true of the extract this project kept and of nothing else: the release publishes
every made field goal with its distance, and the league scores field goals by distance. Seventeen of 150
columns were being kept and kickers were filtered out at the fetch. Nothing about the data prevented it.

The consequence was not neutral. No kicker could be levelled, so every one priced at the minimum bid, none
contributed anything to any lineup, and the `MARKET_SHARE` entry for the position was never read by
anything — a dead constant that looked live, and which cost an afternoon of misdiagnosis.

**Levelled, the position turns out to be the one place the board and the league disagree by a factor.**

<!-- figures: fuad/positions -->

| POS | SHARE | TARGETSHARE | PRICEDDEPTH | SEASONS | LOST | P10 | P90 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| PK | 1.5 | 1.0 | 25 | 225 | 11 | 0.36 | 1.41 |

Rank predicts kicker scoring better than the position's reputation suggests. Over 2017-2025 the preseason
PK1 finished inside the top ten kickers in **every one of the nine seasons**, and the curve separates the
top of the position from replacement by more than five standard errors:

<!-- figures: fuad/curve across=POS field=PTS -->

| Rank | PK |
| --- | --- |
| 1 | 113.1 |
| 3 | 107.8 |
| 5 | 103.1 |
| 11 | 93.0 |
| 20 | 78.4 |

Most of that is **rate** rather than availability:

<!-- figures: fuad/curve across=POS field=PPG -->

| Rank | PK |
| --- | --- |
| 1 | 9.64 |
| 11 | 8.23 |

so it is a claim about how well a rank kicks, not about who keeps his job.

Set against what the league pays, that value has nowhere to go. `TARGETSHARE` is the share of the pot each
position takes and `VORSHARE` the share of the board's value over replacement it holds — the second before
any calibration touches it, which is what makes the pair worth reading:

<!-- figures: fuad/positions -->

| POS | TARGETSHARE | VORSHARE |
| --- | --- | --- |
| QB | 23.7 | 25.6 |
| RB | 32.3 | 36.9 |
| WR | 33.5 | 22.9 |
| TE | 9.4 | 8.1 |
| PK | 1.0 | 6.6 |

Kicker is out by a factor of nearly seven and nothing else is out by a factor at all. Quarterback is bought
at about nine tenths of its worth and running back at seven eighths; receiver is bought nearly half again
above it and **tight end a sixth above**, which is the same mispricing
[§5](#5-pulled-towards-how-this-league-actually-bids) reports from the price side. This sentence had tight
end the wrong way round for as long as it has existed, saying a third below where the two columns beside it
have always said above — which is what a figure nothing recomputed buys you even when the figure itself is
generated. Kicker is a different
kind of disagreement from those, and the top kickers carry the largest `EDGE` on the whole board.

**This is reported and not acted on, and the caution is specific.** Value over replacement assumes the
alternative is the best player *not started*, which for kickers is a rank on a preseason list. In practice
a team whose kicker fails replaces him from the waiver wire the following week, at no cost — there are
around 42 kickers with a stat line in a season and the league rosters 13 to 15, so the free pool is deep and
the downside is insurable in a way it is not at any other position. Some of the gap is that, and how much
cannot be measured from auction data. The board therefore prices kickers where the league prices them and
reports the disagreement as `EDGE`, which is what that column is for.

## Restricted free agency, and why bargains are unavailable

An expiring contract is restricted: the team holding it may match the winning bid. That shows up in the
record as stickiness rather than as inflated prices, while the price premium for prising a top-twelve
player loose is only 4%. The friction is spent on availability, not on cost.

**The denominator is the whole of what these numbers mean, so both are reported.** `MOVEDSHARE` is of the
expiring contracts somebody re-signed, which is the question a bidder is asking and what `AVAILABILITY`
carries. `MOVEDOFEXPIRING` is of every contract that expired, re-signed or not.

<!-- figures: fuad/retention -->

| BAND | EXPIRING | SIGNED | MOVEDSHARE | MOVEDOFEXPIRING | SIGNEDSHARE |
| --- | --- | --- | --- | --- | --- |
| 1-12 | 108 | 103 | 0.30 | 0.29 | 0.95 |
| 13-24 | 85 | 73 | 0.47 | 0.40 | 0.86 |
| 25-40 | 80 | 62 | 0.58 | 0.45 | 0.78 |
| 41+ | 142 | 56 | 0.46 | 0.18 | 0.39 |

Read down `MOVEDSHARE` and the stickiness lands on the players worth wanting, seven of ten top-twelve
players staying put against four of ten in the twenties and thirties — and then the deepest band appears to
reverse it. **It does not, and `SIGNEDSHARE` is why.** A deep expiring contract is usually re-signed by
nobody at all: 39% of them find a team against 95% inside the top twelve. Against every contract that
expired, availability falls away steadily all the way down, and the fourth band is much the least available
of the four. The two columns are answering different questions, and the reversal is entirely the
conditioning.

`AVAILABILITY` carries those retention rates onto the board, and `ACQUIRE` is what it takes to prise a
restricted player loose: to win you must clear what he is worth to the incumbent, not merely what the market
settles at. That is `value + 1`, or `price` where the market is already higher.

**The premium is bounded by what the position costs.** On its own that rule assumes an incumbent who
matches all the way up to the model's own valuation, and nothing in it knows what this league pays. Where
`value` runs far above `price` the result is a number nobody would ever put up — the board once asked 16 for
the best kicker on it against a nine-season league record of 5, and routed a plan away from the cheapest
points available to it. The premium is the same few dollars at kicker as at running back; it simply lands on
a market price of one to three rather than of fifteen to thirty, so it multiplies the price instead of
nudging it.

What is wrong there is the premium and not the price, so the premium is what is bounded. Right of first
refusal may add up to the franchise salary — the average of the top five salaries at that position last
season, which the tag already computes and is the closest thing in the data to a statement of what the top
of a position costs here — and no more:

```
ACQUIRE = PRICE + min(max(0, VALUE + 1 - PRICE), franchise salary)
```

The bound is keyed on the gap and not on the position, and outside kicker it binds on nothing: the allowance
runs to tens of dollars at every other position against premiums of two to five, so every other `ACQUIRE` on
the board is the number the unbounded rule gave. At kicker the two best now cost 6 rather than 16 and 14,
against a record of 5.

**Bounding the premium rather than capping the price is what keeps this monotonic**, and the difference is
not cosmetic. A cap on the price is clipped by the `PRICE` floor exactly where the market has already
cleared above the position's top, so it would delete the premium on the five best running backs — the
players whose incumbent would most certainly match — taking Christian McCaffrey from 76 to 74. It is also
discontinuous: capping at running back's franchise salary of 60 prices a back clearing at 60 with no premium
at all and one clearing at 61 at his full `value + 1`, so a marginally better player would cost eleven
dollars more to prise loose. The premium bound has neither problem. It never returns less than `PRICE`, it
can only ever lower the unbounded answer, and it is non-decreasing in rank down the board.

That has a consequence worth stating plainly. **Positive edge on another team's restricted free agent is
arithmetically unavailable.** Worth more than he clears at, and his team matches, so he costs his full worth
and the surplus stays with them. Worth less, and they let him go and you have overpaid. `EDGE` is therefore
measured against `PRICE`, which is the right reading for a player already yours; against `ACQUIRE` the
answer is always no, and `RFRCOST` shows what the right to match costs an outside bidder.

The caveat: acquisition price assumes the incumbent values him the same as the league does, which is least
true exactly when it matters — a team with no starting quarterback will overpay to keep one.

### How steeply the league bids, which is fitted and not chosen

`PRICE_STEEPNESS` bends each position's price curve to how steeply this league actually bids within it, and
it is the one number in the chain that was fitted once and then left. It was fitted against a value column
the model has since changed twice; nothing recomputed it, and by the time anything looked it was half a gamma
from what the record said.

**The estimator is the pricing arithmetic read backwards, not a preference.** [§5](#5-pulled-towards-how-this-league-actually-bids)
sets a position's shares proportional to `value^gamma`, renormalises them to that position's own total, and
prices each player at `1 + rate × share`. Taking logs of the part above the reserved minimum bid,
`log(paid − 1) = a + gamma × log(value)`, and the slope of that line **is** gamma. Nothing is searched for,
and nothing depends on the model's own pot — which matters, because that pot runs 6% to 11% under what the
league spent, and an estimator built on dollar error would quietly bend gamma to absorb it. Level is not
gamma's job.

**A dollar signing is a censored observation, not a cheap one.** Half the kickers and a quarter of the tight
ends go at the minimum bid, which says only that the market cleared them somewhere below it. Fitting over the
rest alone is selection on the outcome and reads a steep market as a flat one — worth 0.2 to 0.3 of gamma at
every position that carries money, which is larger than the corrections it would then be hiding. So they are
kept, and enter the likelihood as the bound they are.

<!-- figures: fuad/steepness -->

| POS | SIGNINGS | CENSORED | GAMMA | SIGMA | INFORCE |
| --- | --- | --- | --- | --- | --- |
| QB | 45 | 3 | 1.01 | 0.88 | 1.01 |
| RB | 62 | 6 | 1.19 | 0.84 | 1.19 |
| WR | 76 | 12 | 1.08 | 1.15 | 1.08 |
| TE | 33 | 8 | 0.87 | 1.29 | 0.87 |
| PK | 30 | 15 | 0.58 | 0.79 | 0.58 |

`GAMMA` is recomputed from the record whenever the figures are; `INFORCE` is what the model carries. **The
point of the pair is that they can disagree**, and `AuctionValuationSpec` fails when they do, so a change to
the value column cannot silently leave the steepness behind again.

**Quarterback used to sit at 1.44 and tight end at 1.51**, and the documentation explained the first as this
league paying a premium for an elite starter under superflex. The record does not support it: at 1.01,
quarterback is bid almost exactly in line with value, and **running back is the steepest position this league
bids**. Correcting the five constants improved the board against every season on record —

| | MAE before | after |
| --- | --- | --- |
| 2022, held out of the fit | 9.15 | 8.77 |
| 2023 | 7.65 | 7.40 |
| 2024 | 7.77 | 7.61 |
| 2025 | 7.85 | 7.60 |

— and the season that gained most is the one the fit never saw, which is the only evidence here that is not
in sample. What it does not fix is the level: `BIAS` is unchanged in sign and size, because gamma moves money
inside a position and never into one.

**What the fit cannot see is the tag.** The best players at a position are held below open bidding and never
appear as a signing, so the steepest part of every curve is fitted where the market was allowed to operate
and extrapolated over where it was not. That is the same censoring as the minimum bid at the other end of the
board, and unlike the minimum bid it is not handled — there is no bound to give the likelihood, because a
tagged player reveals nothing about what he would have fetched.

## How close the board comes to what was paid

Everything above says what the model believes. This says whether the belief resembled an auction, which is a
different question and until recently was not asked anywhere. `check_docs.sh` holds this document to the
figures and `check_strategy.sh` holds a plan to its board; both ask whether the model agrees with itself.
Nothing held the board to the record, and the cost of that turned up twice in one afternoon — a repricing
that made the board measurably worse went unnoticed until somebody thought to look, and `PRICE_STEEPNESS`, a
constant fitted once and offline, turned out to be worth more accuracy than the repricing cost. See
[TODO.md](../TODO.md).

Every superflex season is priced and joined to what each player really went for. **The join is on the MFL id
and never on a name**, so a board row and a roster row are the same player by construction, and a fall in
`PRICED` means the pool has stopped covering the auction rather than that name matching has drifted:

<!-- figures: fuad/accuracy key=SEASON+POS -->

| SEASON | POS | SIGNINGS | PRICED | PAID | COST | MAE | BIAS | RHO | NAIVE |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 2022 | ALL | 68 | 66 | 1416 | 1253 | 8.83 | -2.47 | 0.809 | 9.44 |
| 2023 | ALL | 86 | 83 | 1801 | 1606 | 7.36 | -2.35 | 0.836 | 7.99 |
| 2024 | ALL | 83 | 79 | 1787 | 1676 | 7.51 | -1.41 | 0.844 | 7.86 |
| 2025 | ALL | 88 | 84 | 2103 | 1857 | 7.57 | -2.93 | 0.883 | 7.98 |

`MAE` is the headline: the board is out by seven or eight dollars a player. `RHO` says the ordering is
better than the dollars — around 0.86, so the board knows who is expensive and is less sure how expensive.

**Seven dollars is neither good nor bad until something else has tried the same signings.** `NAIVE` is that
something: each player predicted as the median this league paid at his position and within six ranks of his,
over its **other** seasons. It needs no model at all — no curve, no replacement level, no value over
replacement — and it is the first thing anybody would try instead.

**The board beats it in every season, and loses to it at two positions out of five.** Read down the
positional rows rather than the `ALL` ones, because the whole of the board's advantage is at one position:

<!-- figures: fuad/accuracy key=SEASON+POS -->

| SEASON | POS | PRICED | MAE | NAIVE |
| --- | --- | --- | --- | --- |
| 2023 | QB | 15 | 4.87 | 7.67 |
| 2023 | RB | 19 | 8.89 | 7.13 |
| 2024 | RB | 23 | 11.17 | 9.39 |
| 2025 | RB | 20 | 7.05 | 6.83 |
| 2025 | WR | 29 | 6.34 | 5.47 |

Pooled over the four seasons the board is 9.09 against the rank median's 12.36 at quarterback and 7.07
against 8.10 at tight end, and **7.70 against 8.85 at running back and 8.73 against 9.22 at receiver, the
wrong way round**. Running back it loses in all four seasons, receiver in three of four. Those two positions
carry 173 of the 312 signings and about two thirds of the money.

That is not a floor and it is not noise: a rule with no model in it prices this league's backs and receivers
better than the model does, by more than any lever below is worth. What the board is buying with all its
machinery is quarterback, where consensus rank and a curve beat the league's own history by three dollars a
player, and where superflex makes replacement level the whole question. **This is the open finding on the
board and it is recorded as one** — see [TODO.md](../TODO.md).

**What the error is made of, and why almost none of it is reachable.** The market prices two players of the
same worth about a factor of two apart — that is `SIGMA` on the steepness figure, 0.88 to 1.29 in log
dollars — so most of `MAE` is the auction's own scatter rather than anything the model decides. Every lever
in the chain has been measured against it:

<!-- model: b692f07 -->

| lever | worth | how it was measured |
| --- | --- | --- |
| the pot, and `SPEND_RATE` behind it | nothing | the best single multiplier on every price is 1.00 |
| `MARKET_SHARE` | 0.40 | hand the board each season's actual positional spend |
| `PRICE_STEEPNESS` | 0.54 | the best gamma per season **and** position, fitted on the answer |
| the priced depth | negative | trimming the deep end and giving its money to the top |
| the franchise tag | nothing | tagged signings carry 9% of the error at a below-average 7.35 |

Those five are counterfactual runs rather than model output, so unlike everything else here they are
remembered rather than regenerated, and the stamp above says which model they were taken against.

Two of those are ceilings rather than gains: they are what perfect hindsight on a season would buy, and
nothing can be fitted to a season before it happens. **So no lever inside the pricing chain returns much**,
and differences of a tenth or two between one model and another — the scale everything on this branch moved
by — sit inside what these five can reach.

What that does not mean is that the board is as good as it can be. The lever table asks what the *price
transform* can still win, given the value column it is handed. `NAIVE` asks a different question and answers
it less comfortably: at running back and receiver the value column itself is the thing losing to a rule that
does not have one.

**The bias is not a level error, which is worth saying because it looks like one.** `BIAS` is negative in
every season, and yet scaling every price up makes the board worse, not better: the cheap end is
*over*priced by about five dollars a player and the dear end *under*priced by thirteen. It is a slope, and
the slope is what `PRICE_STEEPNESS` is already fitted to.

**`BIAS` separates a mis-levelled board from a mis-shaped one.** `COST` totals run under `PAID` in every
season, by 6% to 11%, which looks like a question for the pot. It is not: see above, where scaling the whole
board up is measured and makes it worse. The signed error is the average of an overpriced bottom and an
underpriced top, and the per-player error is far larger than the total error, which is what says most of the
seven dollars is shape rather than level.

Position by position it is not evenly spread, and quarterback in 2025 is the outlier worth looking at:

<!-- figures: fuad/accuracy key=SEASON+POS -->

| SEASON | POS | SIGNINGS | PRICED | PAID | COST | MAE | BIAS | RHO |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 2025 | QB | 17 | 16 | 617 | 440 | 14.06 | -11.06 | 0.816 |
| 2025 | RB | 22 | 20 | 612 | 615 | 7.05 | 0.15 | 0.904 |
| 2025 | WR | 30 | 29 | 719 | 629 | 6.34 | -3.10 | 0.895 |
| 2025 | TE | 11 | 11 | 143 | 160 | 7.36 | 1.55 | 0.614 |
| 2025 | PK | 8 | 8 | 12 | 13 | 0.63 | 0.13 | 0.130 |

The league paid $617 for the sixteen quarterbacks the board priced and the board said $415. The ordering was
fine — `RHO` 0.816 — so this is the level at one position in one season, and it is the same turn towards
quarterback that [§5](#5-pulled-towards-how-this-league-actually-bids) reports from the spend side. Kicker's
`RHO` of 0.233 is the other end of it: eight signings and almost all of them at the minimum bid, so there is
barely an ordering to get right.

**`SIGNINGS` is the only column counted over the whole record**, and everything beside it is over `PRICED`.
A dollar the board never quoted a price for cannot be an error in it, so `PAID` here runs below the same
season's total in `fuad/spend.tsv` by whatever the pool did not cover — seventeen quarterbacks were signed in
2025 for $619 and sixteen of them are scored above.

One more caution about the `ALL` row: its `RHO` pools ranks across positions, so it is flattered by how well
the board separates a quarterback from a kicker, which is largely `MARKET_SHARE` doing its job and is fitted
on these very seasons. Three of the five positions under it score lower. Read the positional rows for the
ordering and the `ALL` row for the dollars.

**This is reported and not enforced, and the reason is not timidity.** A threshold would block changes that
are right as readily as changes that are wrong, and it would be a threshold on a figure that is not out of
sample: the curve is built from every season the statistics cover whichever season is being priced, so the
level leaks, and `MARKET_SHARE` and `SPEND_RATE` are fitted on the same seasons the error is measured over.
It measures **fit, not prediction**. What it is good for is comparing two models, which carry those
advantages equally — and that is exactly the use it was written for. 2022 is the one season held out of the
calibration, and it is also the season the league had not yet adjusted to superflex, so it is a weak test
rather than a clean one.

## Why the top of the board is not testable against what has been paid

The largest auction price in the record is $100 and the model's top price is $104. Neither the agreement
this looked like when the model's figure was $90 nor the disagreement it looks like now is evidence of
anything. **The observed prices are censored by the tag**: the best players are tagged at the positional
average of last year's top five and never reach open bidding, so the auction has essentially never had to
price a top-five player. The one time it nearly did, Lamar Jackson in 2025, the winning team paid $100 *and*
gave up a first round pick, so even that understates what he cost.

<!-- figures: fuad/board -->

| FIGURE | VALUE |
| --- | --- |
| TOPPRICE | 105 |
| TOP40PRICE | 82.9 |

What can be checked is concentration rather than level, and it is checked against 87% in 2025: `TOP40PRICE`
is generated rather than quoted, so it moves in the commit that moves it. It has been on both sides of the
record — a point above before ranks were given their own outcome spread, and four under once
that and the steepness refit had both landed, each of them spreading money down the board. That is a wider
gap than this figure has carried before and it is the one place the two changes visibly cost something, set
against an accuracy measured player by player that both of them improved; see
[how close the board comes to what was paid](#how-close-the-board-comes-to-what-was-paid), which is the
better instrument now that there is one. The top prices themselves remain willingness to pay rather than
clearing prices — an auction settles at what the *second* bidder will go to — and the tag keeps that
question unanswerable either way.

## Provenance

**A preseason rank cannot be revised after the fact.** That is the whole reason the curve is indexed by
rank rather than by player, and it is what lets nine finished seasons be used as evidence about a season
nobody has played.

A projection has no such protection, which is why none is used. The league site keeps one projection per
week and rewrites it as the season goes, so a week 8 projection pulled in December was made knowing who got
hurt in week 3: summed across 2025, its projections correlate **0.95** with what actually happened, which
is impossible for a forecast. Week 1 projections, genuinely made in advance, correlate 0.17 to 0.65. A
finished season's projections measured against its own results therefore report hindsight and nothing else.

The two inputs that survive that test are `player_stats.tsv`, raw statistics from nflverse that can be
refetched at any time and restated under any rules, and `redraft_rankings_half_ppr.csv`, the preseason
consensus each season's ranks are read from. Both come from `./season_history_refresh.sh <year>` for
finished seasons; `./data_refresh.sh <year>` collects the coming season's rankings and rosters.
