# Salary projection

How `-t salaries` decides what a player will cost at auction. Run it with:

```
./generate_report.sh -t salaries -y 2026
```

Built for the season being auctioned, and not meant to run on past ones. `PlayerSalaryCalculator`, a curve
fitted from positional rank straight to dollars, still backs the other reports.

Fitting dollars to rank directly is the obvious alternative, and three things rule it out here. It pools a
$250 and a $300 cap, and one-quarterback and superflex lineups, as if they were one league. It reads the
**46 franchise tags** as bids, when their prices are set by rule and land on the most expensive players in
the data. And it prices each player alone, so nothing makes the answers add up to the money that exists.
None of it can price 2026's tight end premium at all, since no season under that rule has been played.

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
him QB14, so the whole rank goes with him; history levels QB14 at 194, which is what QB14s have actually
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

**Ranked seasons that never happened are in the sample as zeros.** 46 of them across the 2,187 observations
that carry money, and 10 inside the depth the league actually rosters — Andrew Luck's 2017 shoulder,
Le'Veon Bell's 2018 holdout, Gus Edwards' 2021 knee, Joe Mixon's 2025 foot. They are exactly the seasons
that busted hardest, so dropping them biases every curve upward and cuts off the left tail a bench is
priced against.

Which is why the name matching between the rankings and the statistics has to be careful before it gives
up: **an unmatched name and a season lost to injury are indistinguishable, and both score zero.** See
[DATA.md](DATA.md#player-names) for how names are matched.

What that produces at the top of each position, in points over the fourteen week regular season:

| | QB | RB | WR | TE |
| --- | --- | --- | --- | --- |
| Rank 1 | 239 | 170 | 176 | 166 |
| Rank 6 | 217 | 187 | 161 | 112 |
| Rank 24 | 157 | 110 | 124 | 76 |

RB1 below RB3 and RB6 is not a bug, but it is not what it looks like either. The consensus best running
back is not outplayed by the ones behind him — **he misses more football than they do**:

| RB rank | Season points | Points per game | Games played |
| --- | --- | --- | --- |
| 1 | 169.6 | **16.29** | **10.04** |
| 3 | 179.7 | 16.45 | 10.60 |
| 5 | 188.2 | 15.76 | 11.87 |
| 8 | 177.6 | 15.36 | 11.42 |

On rate he is at the top of the position, where the consensus puts him. On availability he is at the bottom
of the top eight, which is what a bell cow's workload buys him, and the season total is the product of the
two. For pricing a salary the total is still the right number — a back who plays ten games is worth what he
scored in ten games. But the reason matters, and the curve reports it as though the consensus were simply
wrong about who is best.

**Indexing by rank, not by player, is what keeps this honest.** See [Provenance](#provenance).

### 3. Replacement level, week by week

`StarterRequirements` works out how many players at each position the league actually starts. That is not a
setting to read off: the lineup is ten of QB 1-2, RB 1-3, WR 2-5, TE 1-3, PK 1, so six slots are fixed by
the minimums and four are flex. Allocating the flex greedily across the league gives, for 2026:

| | QB | RB | WR | TE | PK |
| --- | --- | --- | --- | --- | --- |
| Started league-wide | 20 | 26 | 31 | 13 | 10 |

Tight end now draws three flex spots, where against the projection curve it drew none. The 2026 premium
gives the position a point a reception and history says the best tight ends have been worth 166 points, a
shade under the best receivers, so the third best tight end on a roster now beats the deep running backs he
used to lose to. Superflex, meanwhile, means 20 of about 50 usable quarterbacks start, which pushes
quarterback replacement very high and compresses what the best ones are worth over it.

Replacement is then taken **per week**, as the best player at that position who would not be started *that
week*. A team fields a lineup every week, so on a week when six teams are on bye its alternative is worse
than the season table suggests. Season totals hide this; pricing weekly moves about 1.6 points of the
league's total value onto quarterbacks and running backs.

### 3b. Outcome spread, which is what a bench is worth

Value over replacement at a player's expected points is `max(0, E[X] - replacement)`. What a roster spot is
actually worth is `E[max(0, X - replacement)]`, because a player only has to be started in the weeks he is
good. The second is never smaller, and the gap is widest at replacement level — exactly where a bench sits:

| | Rank | `E[max(0,X-r)]` | `max(0,E[X]-r)` | Missed |
| --- | --- | --- | --- | --- |
| QB | 10 | 55 | 34 | +21 |
| WR | 38 | 18 | 0 | +18 |
| WR | 30 | 17 | 0 | +17 |
| RB | 30 | 16 | 0 | +16 |
| RB | 48 | 7 | 0 | +7 |

The spread is real: realised points at a given preseason rank vary with a coefficient of variation of 0.49
to 0.57 by position, and players nominally below replacement still clear it often — WR38 does 45% of the
time.

So a season is replayed against **every realised-over-expected ratio the position has produced**, and value
over replacement averaged across them. Replacement itself stays at its expectation, being the best of
whoever is left rather than one player's season.

**The distribution is used as observed, not fitted.** It is badly lopsided: almost all the variance is a
left tail of seasons lost to injury, reaching zero at every position, while the upside stops around 1.9 to
2.0 times expectation at the 95th percentile. Fitting a lognormal to that variance mirrors the left tail
into a right one and invents multipliers above three, pricing the bench as if every deep player might
become a star, which flattens the whole board.

**Only ranks levelled above a quarter of the position's best contribute to it.** Not because the deep ranks
are uninteresting but because a ratio taken against a very small number is not a ratio of the same thing.
The consensus ranks receivers 140 deep, and by the bottom of that list a rank is levelled at three or four
points a season, so one player who turns out to be a starter comes back as sixteen times expectation. Those
are not seasons that beat their expectation, they are seasons the consensus was not really making a claim
about. Letting them in invents exactly the multipliers above three the distribution is kept empirical to
avoid.

### 4. Dollars from a known pot

Teams spend a fairly steady share of the cap they have free — 65% to 85% across the record, **80%** across
the four superflex seasons — so the pot is knowable before the auction. For 2026 that is $2,438 free
against $1,950 expected spend.

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

| Share of auction spend | QB | RB | WR | TE |
| --- | --- | --- | --- | --- |
| 2022 (excluded) | 13.9% | 22.3% | **56.2%** | 7.6% |
| 2023 | 21.8% | 27.3% | 37.8% | 13.1% |
| 2024 | 16.7% | 42.5% | 30.4% | 10.4% |
| 2025 | 29.8% | 29.8% | 34.9% | 5.5% |
| **Calibration** | **23.3%** | **33.0%** | **34.4%** | **9.3%** |

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

## What it produces for 2026

105 players priced, $1,878 total. The highest `PRICE` is Ja'Marr Chase at $79, which no one pays because he
is tagged at $61.

| | Model 2026 | Actual 2025 |
| --- | --- | --- |
| Top price | $79 | $100 |
| Players above $1 | 70 | 70 |
| Teams tagging | 9 | 7 |

Position shares are the ones the league actually spends, since `MARKET_WEIGHT` is 1.0:

| | QB | RB | WR | TE |
| --- | --- | --- | --- | --- |
| Model | 23.3% | 33.0% | 34.0% | 9.7% |
| Calibration | 23.3% | 33.0% | 34.4% | 9.3% |

Nine tags are predicted, one per team, led by Ja'Marr Chase at a $61 tag against a $79 market price.

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
  into price, which is a league-wide clearing rate and would stop being one.
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
  low against the market because they are computed from 2025 salaries.
- **The calibration is fitted on three seasons.** Positional shares swing hard year to year, and dropping
  2022 as a transition year buys accuracy at the cost of a thinner sample.
- **The market price of a tagged player is never tested.** It is a counterfactual for a player who will not
  reach the auction, and no observed price can confirm or refute it — see below.
- **Kickers have no curve at all.** The nflverse statistics carry no kicking, so no rank at the position can
  be levelled and every kicker prices at the minimum bid. The league has spent under 1% of its auction on
  them in every season on record, so this costs nothing in money. It costs something in visibility: a team
  with no kicker cannot field a legal lineup and would see the position on no report, which is what the
  `NEEDS` column on `-t teams` exists to say without pricing it.
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
- `PTSLOW` / `PTSHIGH` — the same rank in a bad season and a good one, at the 10th and 90th percentile of
  realised outcomes. **The spread belongs to the position, not to the player.** Every quarterback gets the
  same proportional range around his own level, because realised variation cannot tell an erratic player
  from one the consensus misjudged. A wide range means the position is wide; it never means this player is
  the risky one.
- `BYE` — the week he is off. A fact of the schedule rather than a judgement about him, and the one thing
  about a particular player the model is willing to know.
- `TIER` — the band of ranks at this position the curve **cannot tell apart**, 1 being the best. Levels are
  means of about 45 realised seasons and carry a standard error of ten points or so at quarterback, so the
  curve resolves QB2 from QB17 and has no business resolving QB10 from QB14. Players sharing a tier are
  ties: choose between them on price, bye or roster fit, never on the order they sit in. Compare only
  within a position, and see [What a tier is for](#what-a-tier-is-for).
- `RANK` / `DYNRANK` — his consensus rank for this season, and for the long run. Everything above is built
  on `RANK` alone, because a salary buys one season. `DYNRANK` is carried and never priced, for the second
  decision taken at the same moment as the price: how many years to sign him for. Dynasty rank is the
  better predictor of what a signing goes on to score over the following two seasons, at every price band —
  but the top of the board cannot act on it, since a long deal there is unwritable under the cut penalty.

Within-position steepness is fitted per position as `price ~ value^gamma` over historical signings by
consensus rank over the same seasons: **QB 1.44, RB 1.13, WR 1.07, TE 1.51**. Tight end is the fragile one
— it swings on a handful of signings a year and moved from 0.84 to 1.51 when 2022 came out. Quarterback is much the steepest, which is the
league paying an elite-starter premium that value over replacement will not produce on its own, since
superflex starts twenty quarterbacks and so sets a high replacement. This belongs to price and never to
value: it describes behaviour, not worth.

The split immediately shows what the blend was hiding. This league **overpays for receivers and underpays
for running backs**: Ja'Marr Chase is worth $58 and priced at $79, while Christian McCaffrey is worth $62
and priced at $69.

## What a tier is for

The board reports prices to the dollar off levels that are good to about ten points. Across the flat middle
of a position that is a false precision, and it shows up as an ordering the evidence does not support.

Quarterback in 2026 is the clearest case. The curve is nearly flat from QB6 to QB16 — a 15% span across ten
ranks — and each level carries a standard error of ten to twelve points:

| Rank | Levelled | Standard error |
| --- | --- | --- |
| 10 | 193.5 | ±10.2 |
| 11 | 188.1 | ±10.7 |
| 12 | 201.6 | ±10.5 |
| 14 | 194.5 | ±11.0 |

**QB14 lands a point above QB10 on estimates that are each ±11.** That is not a claim that the fourteenth
quarterback is better than the tenth; it is the curve saying it cannot separate them, and rank 11 comes out
lowest of the three only because two of its nine seasons collapsed to 54 and 12 points.

Two things then amplify a point of noise into dollars, and both are worst at quarterback. Superflex starts
20 of them, so replacement sits at QB20 and value is a small difference against a large number: 1 point on
a 194-point season is 3% of a 61-point value over replacement. Then `PRICE_STEEPNESS` for QB, at 1.44, is
the steepest of any position and stretches that again. Half a per cent of points becomes six per cent of
price.

`TIER` says so directly. Herbert, Mahomes, Lawrence and Stafford all come out tier 4, priced $38 down to
$32, and the spread between them should be read as nothing at all.

Tiers are built by walking the ranks **in order of level, not of rank**, grouping while a rank stays within
one standard error of the best in its tier. Ordering by rank would put a boundary wherever the curve dips
and recovers — it split Lawrence from Mahomes on a one-point difference, which is the fault this exists to
remove. A tier is therefore a set of ranks rather than a range, and need not be contiguous.

The flatness itself is real, and it is the most useful thing the model says about quarterbacks: it is why
the mid tier costs half as much for a fifth fewer points. Trust the flat region; distrust the order inside
it.

## Who is in the pool, and who is not

Three groups, treated differently.

**Expiring contracts** are restricted free agents: 111 of them in 2026. Their team may match.

**Unrostered veterans** are unrestricted, and go to the highest bid. They are cut off at roughly the depth
the league actually rosters (QB 30, RB 45, WR 50, TE 25), since deeper players are never signed and would
only dilute the board. Historically 7 to 22 veterans a year are signed from outside the pre-auction
rosters. **They are the only players on whom a positive edge is available**, for the reason in the next
section.

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

The largest auction price in the record is $100 and the model's top price is $79, which looks like
agreement and is not evidence of any. **The observed prices are censored by the tag**: the best players are
tagged at the positional average of last year's top five and never reach open bidding, so the auction has
essentially never had to price a top-five player. The one time it nearly did, Lamar Jackson in 2025, the
winning team paid $100 *and* gave up a first round pick, so even that understates what he cost.

What can be checked is concentration rather than level: the board puts 86% of the pot in its top 40 against
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
