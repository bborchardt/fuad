# Salary projection

How `-t salaries` decides what a player will cost at auction. Run it with:

```
./generate_report.sh -t salaries -y 2026
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

<!-- figures: positions -->

| POS | SEASONS | LOST |
| --- | --- | --- |
| QB | 324 | 5 |
| RB | 584 | 13 |
| WR | 900 | 16 |
| TE | 405 | 10 |

Which is why the name matching between the rankings and the statistics has to be careful before it gives
up: **an unmatched name and a season lost to injury are indistinguishable, and both score zero.** See
[DATA.md](DATA.md#player-names) for how names are matched.

What that produces at the top of each position, in points over the fourteen week regular season:

<!-- figures: curve across=POS field=PTS -->

| Rank | QB | RB | WR | TE |
| --- | --- | --- | --- | --- |
| 1 | 245.2 | 188.4 | 184.3 | 161.5 |
| 6 | 220.5 | 178.6 | 161.2 | 116.4 |
| 24 | 154.3 | 108.5 | 122.1 | 74.6 |

and per game played:

<!-- figures: curve across=POS field=PPG -->

| Rank | QB | RB | WR | TE |
| --- | --- | --- | --- | --- |
| 1 | 21.39 | 16.94 | 16.09 | 15.06 |
| 6 | 19.26 | 16.51 | 14.20 | 10.90 |
| 24 | 15.26 | 10.41 | 11.16 | 7.18 |

### 2b. A season is a rate times an availability

Those are two numbers because a season is two things, and they are differently caused: how good a player is
when he plays, and how much football he plays. For ranked quarterback seasons the rate scatters with a
coefficient of variation of 0.25 and games played with 0.25, so **about half the variation in a season
total is availability rather than production.**

**How much the two halves have to say about rank is not the same, and it differs by position.** Over the
same span of ranks, receiver loses almost all of its level to the rate while quarterback loses a third of
its own to availability:

<!-- figures: curve across=POS field=PPG -->

| Rank | QB | WR |
| --- | --- | --- |
| 1 | 21.39 | 16.09 |
| 34 | 11.22 | 9.86 |

<!-- figures: curve across=POS field=G -->

| Rank | QB | WR |
| --- | --- | --- |
| 1 | 11.46 | 11.45 |
| 34 | 6.90 | 10.71 |

A receiver 34 ranks down plays essentially as much football as the best one and simply scores less when he
does. A quarterback 34 ranks down is a different kind of player: the league has 32 starting jobs, so past
about rank 26 he is a backup who plays when somebody gets hurt. A single levelled season total says the same
thing about both — worth about 60% of the best — and hides that one is worse and the other is absent.

**Levelling the product directly carries that noise into the level, and the measurement says by how much.**
Where rank *r+1* levels above rank *r*, the step is noise the smoothing failed to remove; summed over the
priced ranks and taken against the curve's range, it is how far a curve travels backwards. Levelling the
season totals against splitting them, over a rank window common to all four positions:

<!-- figures: positions -->

| POS | BACKWARDTOTALS | BACKWARD |
| --- | --- | --- |
| QB | 24.5 | 6.6 |
| RB | 54.5 | 12.4 |
| WR | 61.2 | 21.9 |
| TE | 20.3 | 11.6 |

Better at every position, and by a factor of two to four at three of them.

> **Superseded.** This section used to argue the split from a single case — that levelling season totals
> made the consensus best running back appear to be outplayed by RB5 — and that case no longer exists,
> which is the change working rather than an argument still standing. RB1 now levels above RB5 on the
> season, on the rate and on games played alike. The measurement above replaced it because a curve-wide
> number can be regenerated and an anecdote cannot.

**Availability is smoothed five times harder than the rate**, over ranks ±10 rather than ±2. How much
football a player misses is nearly unrelated to where he was ranked — the correlation between rank and games
played is −0.04 at running back, −0.09 at receiver and −0.14 at tight end — so a narrow window fits noise
and multiplies it straight back into the level. At the rate's own radius the curve came out *less* monotone
than the season totals it replaced.

<!-- model: 7556912 -->

> Measured when the radius was chosen, against a model since superseded, and not reproducible: the
> alternative is a constant the curve no longer accepts. Backward movement at ±2 ran 24% at quarterback,
> 44% at running back, 83% at receiver and 35% at tight end, against the ±10 figures in the table above.

Smoothed, though, and not held flat, which was the first attempt. Quarterback is why:

<!-- figures: curve across=POS field=G -->

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

<!-- figures: positions -->

| POS | STARTED | REPLRANK |
| --- | --- | --- |
| QB | 20 | 21 |
| RB | 26 | 27 |
| WR | 32 | 33 |
| TE | 12 | 13 |
| PK | 10 | 11 |

Superflex means 20 of about 50 usable quarterbacks start, which pushes quarterback replacement very high
and compresses what the best ones are worth over it. That much is decisive and stable.

**The last few flex spots are not.** The comparison that decides them is between a league's 32nd to 34th
receiver and its 11th to 13th tight end, and those are the same players:

<!-- figures: curve across=POS field=PTS -->

| Rank | TE |
| --- | --- |
| 11 | 109.4 |
| 12 | 110.3 |
| 13 | 107.2 |

<!-- figures: curve across=POS field=PTS -->

| Rank | WR |
| --- | --- |
| 32 | 103.6 |
| 33 | 104.8 |
| 34 | 105.6 |

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

`VOR` is the first, which is what the board prices with; `VOREXP` is the second, carried only so the two can
be compared. Read down a position at a time:

<!-- figures: curve across=POS field=VOR -->

| Rank | QB | WR | RB |
| --- | --- | --- | --- |
| 10 | 41.2 | | |
| 30 | | 19.1 | 15.3 |
| 38 | | 14.1 | |
| 48 | | | 4.9 |

<!-- figures: curve across=POS field=VOREXP -->

| Rank | QB | WR | RB |
| --- | --- | --- | --- |
| 10 | 31.6 | | |
| 30 | | 3.7 | 0.2 |
| 38 | | 0.3 | |
| 48 | | | 0.0 |

Every one of those gaps is positive, and it is widest where a player sits near replacement rather than well
clear of it — RB48 is worth nothing at all on the second reading and about five points on the first.

The spread is real: realised points at a given preseason rank vary with a coefficient of variation of 0.5 to
0.6 by position, and players nominally below replacement still clear it often.

So a season is replayed against **every season the position has actually produced**, and value over
replacement averaged across them.

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

<!-- figures: board -->

| FIGURE | VALUE | Actual 2025 |
| --- | --- | --- |
| Players above $1 | 77 | 70 |
| Top cost | 83 | $100 |
| Top 40 cost | 87.6 | 87% |

<!-- model: 59b4f91 -->

> **Superseded, and not reproducible**: each of the three alternatives below is an implementation the model
> no longer has. Charging missed weeks against the deepest rank the curve still prices left **27** players
> above the minimum bid with a top `COST` of **$236** and 96.5% of the money in the top 40; charging them
> against nobody left **23**, **$268** and the same 96.5%. Giving each rank its own availability instead —
> the mildest of the three — left **70**, **$81** and 88.3%, which is the only one that did not wreck the
> board.

Twenty-seven players above the minimum bid against the seventy the league signs, and tight end priced out
of existence altogether. The reason is structural rather than a matter of picking a gentler bar: the games
a season loses are drawn from the position's pooled distribution, so the charge is **the same number for
every player at a position**. Subtract a constant from everyone and let the positional shares renormalise,
and the money tilts to whoever had most of it already — the top tenth of the board goes from 34.7% to
60.2%. A rank-invariant penalty cannot correct a rank-invariant assumption; it only steepens the curve.

The one thing here that is rank-graded moves nothing. Value over replacement draws its games from the
pooled distribution while `PTS` uses the rank's own, which is a real inconsistency, and repairing it shifts
no quarterback on the board by more than a dollar. The ranks where availability genuinely differs — the back
of quarterback plays barely six games against the best one's eleven and a half — are already at the minimum
bid.

**What settles it is that the cost is charged elsewhere, once.** At the auction a team buys its whole
roster in one sitting, so what covers an injured starter is another player it also bought. Charging the
starter for his own absence *and* making the team buy a backup pays for the same week twice. `-t roster`
prices that backup and finds him expensive — a third quarterback is worth 57 points to Brett, nearly all of
it bye and injury cover. So absence is charged in the report that can see a roster, and a league-wide
clearing price, which cannot see one, leaves it alone. See [STRATEGY.md](STRATEGY.md#the-roster-reports).

**The distribution is used as observed, not fitted.** It is badly lopsided. Almost all the variance is a left
tail of seasons lost to injury, reaching zero at every position, while the upside is far more compressed —
nine seasons in ten land under about one and three quarters times expectation:

<!-- figures: positions -->

| POS | P10 | P90 |
| --- | --- | --- |
| QB | 0.36 | 1.50 |
| RB | 0.23 | 1.71 |
| WR | 0.30 | 1.68 |
| TE | 0.31 | 1.72 |

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

Teams spend a fairly steady share of the cap they have free — 65% to 85% across the record, **80%** across
the four superflex seasons — so the pot is knowable before the auction:

<!-- figures: board -->

| FIGURE | VALUE |
| --- | --- |
| Free cap | 2438 |
| Expected spend | 1950 |

The fifth that goes unspent is deliberate, and it is why a model that assumed teams bid to the cap would
price everything too high. Cap is what absorbs in-season signings, and it is what a team releasing a bad
contract pays the penalty out of — charged to the current year and cleared at the end of it, so cap left
over is also the mechanism by which a mistake is prevented from reaching next season. See
[LEAGUE_RULES.md](LEAGUE_RULES.md#the-cut-penalty).

Most of that reserve is never drawn on, which is what makes it insurance rather than a budget. In-season
salary added between the week 1 and end-of-year rosters runs **0.3% to 2.3% of the league's cap**, a mean
of about 1.2%, though 4 to 8 of the 8 to 10 teams add something every single season. Teams hold far more
room than they use, every year, and go on holding it.

That 80% is counted over **distinct players**. The week 1 snapshots repeat a handful of roster rows
verbatim, same franchise and same salary, so summing rows rather than players double counts those
contracts and puts the figure at 83%. `AuctionValuationSpec` recomputes this and the market shares from the
committed seasons, so neither constant can drift from the data it came from.

Each player who must be signed is reserved a dollar and the rest is shared in proportion to value over
replacement, so **prices sum to the money available**.

### 5. Pulled towards how this league actually bids

Value over replacement puts far more of the pot on running backs than this league spends, and far less on
wide receivers, so prices are scaled to the shares the league actually pays.

**Calibrated on 2023-2025, not on 2022.** Superflex arrived in 2022 and the league had not adjusted to it:
wide receivers took **56.2%** of that auction against 30.4% to 37.8% in every season since, and
quarterbacks 13.9% against 16.7% to 29.8%. Averaging that year in drags wide receiver up by four points of
the pot and holds quarterback down, which showed up as top receivers priced above anything the league has
ever paid.

<!-- figures: spend across=POS field=SHAREXPK -->

| Share of auction spend | QB | RB | WR | TE | |
| --- | --- | --- | --- | --- | --- |
| 2022 | 13.9% | 22.3% | **56.2%** | 7.6% | excluded |
| 2023 | 21.8% | 27.3% | 37.8% | 13.1% | |
| 2024 | 16.7% | 42.5% | 30.4% | 10.4% | |
| 2025 | 29.8% | 29.8% | 34.9% | 5.5% | |
| 2023-2025 | 23.3% | 33.0% | 34.4% | 9.3% | what the model calibrates to |

Those rows are what the league paid, generated from the committed seasons into
`docs/figures/<year>/spend.tsv` and checked cell by cell, so the case for dropping 2022 is evidence a
reader can check rather than a claim they have to take. The last row is the pooled span the calibration is
fitted over, and it is `TARGETSHARE`:

<!-- figures: positions -->

| POS | TARGETSHARE |
| --- | --- |
| QB | 23.1 |
| RB | 32.7 |
| WR | 34.1 |
| TE | 9.2 |
| PK | 0.9 |

**The table above is on the four-position basis; `TARGETSHARE` is on the whole-auction one.** Kickers take
0.5% to 1.6% a season, and leaving them in or out of the denominator moves every other position by a few
tenths — enough to be mistaken for rounding and enough to matter to a constant compared against them.
`spend.tsv` carries both: `SHAREXPK` is the table's basis, `SHARE` is the share of every dollar, and it is
`SHARE` the model calibrates to now that kickers are priced along with everyone else.

The repricing is real, and it is not a stock of old contracts running off. Money already committed says the
same thing from the other side: quarterback contracts have gone from 16% to 30% of committed salary since
2022 and running back from 43% to 20%, while **wide receiver has stayed flat at 36-42%**. Nothing is
expiring away; the league is simply buying different positions.

### 6. Franchise tags, iterated to a fixed point

Tags take 12-37% of the pot out of open bidding, so they cannot be ignored. Each team tags the expiring
player it saves most on, so long as the saving is positive; those players leave the pool and their tag
price leaves the pot; everything reprices; repeat until the set of tags stops changing.

**Tag surplus is measured against the market price, not against what the player ends up costing.** A tagged
player costs the tag price by definition, and comparing that against itself makes every tag look pointless
— which sends the loop oscillating forever rather than settling. The board therefore reports both a
`MARKET` price and a `SALARY`.

A tagged player's `MARKET` is the price he would have fetched **had his own team not tagged him, with every
other team's tag still standing**. That puts him back in the bidding and puts the money his team would have
spent tagging him back in the pot. Both halves matter: pricing him against a pool he has been removed from,
or against a pot his own tag has already left, overstates him by a quarter at the top, since once the
tagged are gone the best player left is a large share of what remains.

**It does not always reach a fixed point, and where it does not the board says so.** Two expiring players
on one roster can each be the better tag once the other is tagged: taking one out of the bidding puts his
tag price back in the pot and lifts what the other would fetch, which flips the saving back the other way.
2026 does this for franchise 0001, where Lamar Jackson saves $17 against his market price and Amon-Ra St.
Brown $19 — a gap well inside the standard error the levels carry, so the model genuinely cannot separate
them.

What matters is that the set the board **reports** is the set it was **priced with**. Those came apart
once: the loop ran out of rounds part way round the cycle and the tags were re-read from the round after
the last pricing, so a team was told to tag one player while all 106 prices, his own included, assumed it
had tagged another. The prices were never wrong — only the column naming the tag was. `AuctionValuation`
now stops on the set it priced and prints an unsettled warning naming the players it cannot choose between,
rather than letting half a turn of the cycle read as an answer. `FranchiseTagSettlementSpec` holds it to
that.

## What it produces for 2026

The highest `PRICE` is Ja'Marr Chase, which no one pays because he is tagged well below it.

<!-- figures: board -->

| FIGURE | VALUE | Actual 2025 |
| --- | --- | --- |
| Players | 106 | |
| Total cost | 1862 | |
| Top price | 89 | $100 |
| Players above $1 | 77 | 70 |
| Top 40 price | 88.3 | 87% |
| Teams tagging | 9 | 7 |

Position shares are the ones the league actually spends, since `MARKET_WEIGHT` is 1.0. `SHARE` is what the
board came out at and `TARGETSHARE` what the calibration aimed for:

<!-- figures: positions -->

| POS | PLAYERS | RESERVE | SHARE | TARGETSHARE |
| --- | --- | --- | --- | --- |
| QB | 19 | 1.0 | 23.1 | 23.1 |
| RB | 23 | 1.2 | 32.4 | 32.7 |
| WR | 25 | 1.3 | 33.6 | 34.1 |
| TE | 21 | 1.1 | 9.6 | 9.2 |
| PK | 18 | 0.9 | 1.2 | 0.9 |

**Those two columns do not match, and the reason is not a failure of the calibration.** It hits the target
exactly — the shares of value it hands on are `TARGETSHARE` to the decimal, and bending each position's
curve to its own steepness leaves those totals alone. What moves them afterwards is the dollar reserved for
every roster spot still to be filled, which comes to about 5% of the pot and is **handed out by headcount
rather than by worth**. `RESERVE` is each position's share of it.

So a position carrying many cheap players finishes above its target and one carrying fewer dearer players
below it. Add `RESERVE` to `TARGETSHARE` scaled down by the reserved fraction and every position lands
within a third of a point of `SHARE`. Kicker is the plain case: 18 of the board's 106 players, and under a
hundredth of its money, so almost the whole of its 1.2 is minimum bids.

Nine tags are predicted, one per team.

The concentration is the number to watch. Splitting rate from availability raised the top of the board — the
best players lose less to the injury smear than the deep ones did — and the top forty now hold a little
under 89% of what open bidding would pay, against the 87% the league actually spent in 2025. A two point
drift toward a more top-heavy board than the record, small but in the direction the model is least able to
check, since the tag keeps the very top from ever being priced in the open.

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
enormously — exposure against free cap has run from 0.30 to 1.92, and one team went into 2025 with twenty
expiring players worth more than its whole budget and kept three — but across 29 team seasons the
correlation between how stretched a team is and how much of its roster it keeps is **+0.13**. Real one at a
time, invisible on average. Turning that into a price adjustment would be fitting noise; handing it to a
reader who knows the league is not.

## Known limits

- **Rookie pricing is a flat allowance, not a model.** Early first round picks do go above the minimum, and
  none of that shape is captured — only the league-wide total and the roster spots.
- **Nothing prices team need or budget, so top-end prices are ceilings rather than clearing prices.** Value
  over replacement is the most a rational team would pay, but an auction clears at what the *second* bidder
  will go to, and that gap is widest at the very top. A desperate buyer with cap to spend is averaged into
  a league-wide rate rather than identified. Treat the top of the board as a walk-away number, and `-t
  teams` as the stopgap; pricing at the highest bidder needs an auction simulation.
- **Prices are still per player, so nothing on this board sees a roster.** Two players are priced
  identically whether they cover each other's byes or share one, and a spare at a position a team already
  starts two of is priced as though it were his first. `-t roster` answers that separately, in points and
  for one named team; see [STRATEGY.md](STRATEGY.md#the-roster-reports). It is deliberately not fed back
  into price, which is a league-wide clearing rate and would stop being one. That division is also what
  keeps the cost of a missed week from being charged twice; see [3c](#3c-why-a-missed-week-costs-nothing-which-is-not-the-same-as-being-free).
- **The spread cannot tell volatility from disagreement.** It is realised variation, so a genuinely erratic
  player and one the consensus simply misjudged look identical. For pricing that is the right total, but it
  means the model has no notion of a safe pick versus a risky one — which is why `PTSLOW` and `PTSHIGH` are
  a position's range scaled to a player, and must not be read as his own.
- **Contract length is not modelled.** A salary buys one season, and length is chosen jointly with price
  rather than being an input. The record shows the shape the cut penalty implies: 87% of signings above $40
  are one-year deals, against 70% at $1-2, and the dynasty-minus-redraft gap tracks length four times more
  strongly at the cheap end (correlation 0.290 against 0.12-0.13 higher up). `DYNRANK` is carried on the
  board so a plan can weigh this; nothing prices it. See
  [LEAGUE_RULES.md](LEAGUE_RULES.md#contract-length).
- **Nine of ten teams are predicted to tag**, at the high end of the observed 5-to-9. 2026's tag prices are
  low against the market because they are computed from 2025 salaries. One of the nine does not settle: see
  [the tag loop](#6-franchise-tags-iterated-to-a-fixed-point).
- **Tag surplus asks what a tag saves, never whether the player is worth it.** It is `MARKET` less the tag
  price, so the tag a team is told to use is the one it saves most on even where that player
  is priced above what he is worth. For 2026's undecided pair the two readings disagree: Lamar Jackson's
  $66 tag buys $67 of `VALUE` while Amon-Ra St. Brown's $61 buys $57, so the cheaper saving is the better
  contract. Nothing in the model weighs that.
- **The calibration is fitted on three seasons.** Positional shares swing hard year to year, and dropping
  2022 as a transition year buys accuracy at the cost of a thinner sample.
- **The market price of a tagged player is never tested.** It is a counterfactual for a player who will not
  reach the auction, and no observed price can confirm or refute it — see below.
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
  way. See [DATA.md](DATA.md).

## Value and price are separate numbers

They answer different questions and blending them answered neither. The board reports both.

- `VALUE` — worth: value over replacement priced against the cap, with no adjustment for this league.
- `PRICE` — what open bidding here is expected to settle at: value calibrated to the positional shares the
  league actually spends (`MARKET_WEIGHT` is now 1.0) and to how steeply it bids **within** a position.
- `COST` — what the holding team actually pays, which is the tag price for a franchised player.
- `ACQUIRE` — what it takes to prise him off the team that holds him.
- `EDGE` / `BAND` — `VALUE − PRICE`, banded rather than given to the dollar, because it is the difference
  of two noisy estimates and a precise figure would overstate the resolution.

Alongside them the board carries what a plan needs in order to reason without going behind it:

- `PTS` — expected points: what this rank has historically been worth, under the rules being priced.
- `PPG` / `G` — the two halves that season is the product of: what he scores in a game he plays, and how
  many games he plays. They multiply back out to `PTS`, to the one decimal place the board prints them at,
  and that costs something to arrange — the level is
  anchored back to the mean season the position actually had, about five per cent above the product of the
  two separate means, so `PPG` is the rate that level implies rather than the raw mean behind it.

  They are carried because the two halves are differently caused and the total hides which one a player is
  made of. Availability is the one that moves with rank, and only at quarterback: past about rank 26 a
  quarterback is a backup who plays when somebody gets hurt, while a receiver that far down plays nearly as
  much football as the best one and simply scores less doing it. See
  [2b](#2b-a-season-is-a-rate-times-an-availability) for the figures.

  **`G` does not break a tie inside a tier.** Availability is smoothed across ten ranks either side, because
  rank predicts it weakly — the correlation is −0.04 at running back — and a tier is a band of neighbouring
  levels, so the ranks in one share nearly the same figure. Across a whole tier of 2026 the widest spread is
  0.7 games and the usual one is 0.3. Reading an order into that is the same false precision `TIER` exists
  to remove.

  **`G` is not `AVAIL`.** `G` is how much football he plays; `AVAIL` is the chance he ever reaches another
  team, which is a fact about the right of first refusal and nothing to do with his health.
- `PTSLOW` / `PTSHIGH` — the same rank in a bad season and a good one, at the 10th and 90th percentile of
  realised outcomes. **The spread belongs to the position, not to the player.** Every quarterback gets the
  same proportional range around his own level, because realised variation cannot tell an erratic player
  from one the consensus misjudged. A wide range means the position is wide; it never means this player is
  the risky one.
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

Within-position steepness is fitted per position as `price ~ value^gamma` over historical signings by
consensus rank over the same seasons:

<!-- figures: positions -->

| POS | GAMMA |
| --- | --- |
| QB | 1.44 |
| RB | 1.13 |
| WR | 1.07 |
| TE | 1.51 |

Tight end is the fragile one
— it swings on a handful of signings a year and moved from 0.84 to 1.51 when 2022 came out. Quarterback is much the steepest, which is the
league paying an elite-starter premium that value over replacement will not produce on its own, since
superflex starts twenty quarterbacks and so sets a high replacement. This belongs to price and never to
value: it describes behaviour, not worth.

The split immediately shows what the blend was hiding. This league **overpays for receivers and underpays
for running backs**: Ja'Marr Chase is worth $69 and priced at $90, while Christian McCaffrey is worth $79
and priced at $75.

## What a tier is for

The board reports prices to the dollar off levels that are good to seven points or so. Across the flat middle
of a position that is a false precision, and it shows up as an ordering the evidence does not support.

Quarterback is the clearest case. The curve is nearly flat across the middle of the position, and each
level carries a standard error of seven to nine points:

<!-- figures: curve across=POS field=PTS -->

| Rank | QB |
| --- | --- |
| 8 | 212.6 |
| 10 | 202.9 |
| 11 | 202.4 |
| 12 | 207.2 |
| 14 | 195.6 |

<!-- figures: curve across=POS field=SE -->

| Rank | QB |
| --- | --- |
| 8 | 7.7 |
| 10 | 7.4 |
| 11 | 7.3 |
| 12 | 7.8 |
| 14 | 8.8 |

<!-- figures: curve across=POS field=TIER -->

| Rank | QB |
| --- | --- |
| 8 | 4 |
| 10 | 5 |
| 11 | 5 |
| 12 | 4 |
| 14 | 5 |

QB10 and QB11 are half a point apart on estimates carrying seven. That is not a claim about which is
better; it is the curve saying it cannot separate them.

Two things then amplify a point of noise into dollars, and both are worst at quarterback. Superflex starts
20 of them, so replacement sits at QB20 and value is a small difference against a large number. Then
`PRICE_STEEPNESS` for QB, at 1.44, is the steepest of any position and stretches that again.

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

<!-- figures: positions -->

| POS | PRICEDDEPTH |
| --- | --- |
| QB | 36 |
| RB | 65 |
| WR | 100 |
| TE | 45 |
| PK | 25 |

Kicker's 25 is set by hand, being the one position that rule cannot bound. The test is a level under a
quarter of the position's best, and it never fires at kicker because the curve there is nearly flat — the
42nd ranked kicker still levels around three fifths of the first, so the whole ranked pool would come onto
the board. Flat is not the same as valuable: only ten kickers start, so everything past about the eleventh
is below replacement and worth nothing to anybody. Left uncapped the board carried 29 kickers, 27 of them
at the minimum bid, and the dollar reserved for each came to more than the position's whole budget. 25 is
the deepest kicker ever signed at auction, and covers 95% of those on a week 1 roster.

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

The depth is taken from the curve rather than set by hand. The four numbers it replaced (QB 30, RB 45, WR
50, TE 25) were too shallow against what the league has actually signed, most badly at receiver, where
**26% of historical signings — and 18% of those above the minimum bid — fell beyond rank 50**.

**Rookies are excluded entirely.** They are drafted separately after the auction and cannot be bid on. Two
simple allowances stand in for them, both checked against the record by `AuctionValuationSpec`:

- **Roster spots**: five rounds times teams. Nearly every pick is kept — 38, 46, 52 and 49 rookies rostered
  at week 1 across 2022-2025, against 40, 45, 50 and 50 picks.
- **Budget**: 3.5% of the pot, taken off the top. Rookie prices are set by rule off the previous year's
  positional prices and come out very low: $42, $52, $69 and $76 for the whole league, which is 3.0% to
  4.0% of the auction pot.

That leaves the spots the auction has to fill, as `teams x 30 - under contract - 5 x teams`. It predicts
what the league actually signs closely: 65 against 71 in 2022, 90 against 93 in 2023, 103 against 96 in
2024, and 92 against 92 in 2025. A dollar is reserved per **spot**, not per player on the board, since the
pool holds everyone who could be bid on and only the spots get filled.

## Kickers

Kickers were not levelled at all until recently, on the stated grounds that *"the nflverse statistics carry
no kicking"*. That was true of the extract this project kept and of nothing else: the release publishes
every made field goal with its distance, and the league scores field goals by distance. Seventeen of 150
columns were being kept and kickers were filtered out at the fetch. Nothing about the data prevented it.

The consequence was not neutral. No kicker could be levelled, so every one priced at the minimum bid, none
contributed anything to any lineup, and the `MARKET_SHARE` entry for the position was never read by
anything — a dead constant that looked live, and which cost an afternoon of misdiagnosis.

**Levelled, the position turns out to be the one place the board and the league disagree by a factor.**

<!-- figures: positions -->

| POS | SHARE | TARGETSHARE | PRICEDDEPTH | SEASONS | LOST | P10 | P90 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| PK | 1.2 | 0.9 | 25 | 225 | 11 | 0.36 | 1.43 |

Rank predicts kicker scoring better than the position's reputation suggests. Over 2017-2025 the preseason
PK1 finished inside the top ten kickers in **every one of the nine seasons**, and the curve separates the
top of the position from replacement by more than five standard errors:

<!-- figures: curve across=POS field=PTS -->

| Rank | PK |
| --- | --- |
| 1 | 113.3 |
| 3 | 108.0 |
| 5 | 103.3 |
| 11 | 92.2 |
| 20 | 78.5 |

Most of that is **rate** rather than availability:

<!-- figures: curve across=POS field=PPG -->

| Rank | PK |
| --- | --- |
| 1 | 9.66 |
| 11 | 8.16 |

so it is a claim about how well a rank kicks, not about who keeps his job.

Set against what the league pays, that value has nowhere to go. `TARGETSHARE` is the share of the pot each
position takes and `VORSHARE` the share of the board's value over replacement it holds — the second before
any calibration touches it, which is what makes the pair worth reading:

<!-- figures: positions -->

| POS | TARGETSHARE | VORSHARE |
| --- | --- | --- |
| QB | 23.1 | 25.2 |
| RB | 32.7 | 36.7 |
| WR | 34.1 | 25.7 |
| TE | 9.2 | 6.7 |
| PK | 0.9 | 5.7 |

Kicker is out by a factor of six and nothing else is out by a factor at all. Quarterback and running back
are bought at about nine tenths of their worth; receiver is bought a third above it and tight end a third
below, which is the same mispricing
[§5](#5-pulled-towards-how-this-league-actually-bids) reports from the price side. Kicker is a different
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
record as stickiness rather than as inflated prices — retention runs at **74% inside the top twelve** at a
position against 41% in the twenties, while the price premium for prising a top-twelve player loose is only
4%. The friction is spent on availability, not on cost.

`AVAILABILITY` carries those retention rates onto the board, and `ACQUIRE` is `max(price, value + 1)` for a
restricted player: to win you must clear what he is worth to the incumbent, not merely what the market
settles at.

That has a consequence worth stating plainly. **Positive edge on another team's restricted free agent is
arithmetically unavailable.** Worth more than he clears at, and his team matches, so he costs his full worth
and the surplus stays with them. Worth less, and they let him go and you have overpaid. `EDGE` is therefore
measured against `PRICE`, which is the right reading for a player already yours; against `ACQUIRE` the
answer is always no, and `RFRCOST` shows what the right to match costs an outside bidder.

The caveat: acquisition price assumes the incumbent values him the same as the league does, which is least
true exactly when it matters — a team with no starting quarterback will overpay to keep one.

## Why the top of the board is not testable against what has been paid

The largest auction price in the record is $100 and the model's top price is $90, which looks like
agreement and is not evidence of any. **The observed prices are censored by the tag**: the best players are
tagged at the positional average of last year's top five and never reach open bidding, so the auction has
essentially never had to price a top-five player. The one time it nearly did, Lamar Jackson in 2025, the
winning team paid $100 *and* gave up a first round pick, so even that understates what he cost.

What can be checked is concentration rather than level: the board puts 89% of the pot in its top 40 against
87% in 2025. The top prices themselves remain willingness to pay rather than clearing prices — an auction
settles at what the *second* bidder will go to — and the tag keeps that question unanswerable either way.

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
