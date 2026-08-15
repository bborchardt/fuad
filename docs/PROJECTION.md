# Salary projection

How `-t salaries` decides what a player will cost at auction. Run it with:

```
./generate_report.sh -t salaries -y 2026
```

This replaces nothing: `PlayerSalaryCalculator`, a fitted curve from positional rank to dollars, still backs
the other reports. This model is built for the season being auctioned and is not meant to run on past ones.

## Why the old model was not enough

Measured against held-out seasons, the rank curve has a mean absolute error of **$6.51 in 2024 and $7.37 in
2025**. Three things were wrong with it beyond noise:

- It was fitted across a $250 and a $300 cap, and across one-quarterback and superflex lineups, as if they
  were one league.
- It included the **46 franchise tags**, whose prices are set by rule rather than bidding and which sit on
  the most expensive players in the data.
- It priced each player alone, so nothing made the answers add up to the money that exists.

None of that could price 2026's tight end premium either, since no season with that rule has been played.

## The chain

### 1. Order from consensus, curve from projections

`PointsCurve` takes the **order** from the FantasyPros consensus and the **shape** from the league site's
own projections. The player consensus ranks WR1 is valued at whatever the best projected wide receiver is
worth, whether or not the projection agrees they are the same player.

The split is deliberate. A ranking is a judgement about who is better, which is what expert consensus is
for and all it gives you. A projection carries the gaps between one rank and the next, which a ranking
cannot express, and it carries them under this league's scoring — so the 2026 tight end premium and the
one-point-per-30-yards passing rule are already in the numbers. The projection never gets to decide who is
good.

### 2. Corrected for what a rank actually delivers

Projections assume a full healthy season. Ranks do not survive one. Comparing three finished seasons of
actual scoring, indexed by the consensus rank each player held **before** that season, against the
projection curve:

| rank 1 vs rank 24 | QB | RB | WR | TE |
| --- | --- | --- | --- | --- |
| Realised | 1.74x | 1.26x | 1.30x | 2.10x |
| Projected | 1.46x | 1.70x | 1.62x | 1.93x |

Projections run 20-40% above realised scoring, and are too steep at running back and wide receiver while
being too flat at quarterback and tight end. The correction is fitted per position on log scales,
`actual = e^a · projected^b`, which takes out the optimism and the steepness together. Two numbers per
position is about all three seasons will carry.

It is smoothed over two ranks either side before fitting, and only ranks projected above a quarter of the
position's best are fitted at all. Without both, the fit is dragged flat by deep noisy ranks and puts the
best wide receiver at 113 points against a 237-point projection. Per-rank means are hopeless on their own:
the fifth ranked quarterback averages 108 points across three seasons and the eighth averages 237.

**Indexing by rank, not by player, is what keeps this honest.** See [Provenance](#provenance).

### 3. Replacement level, week by week

`StarterRequirements` works out how many players at each position the league actually starts. That is not a
setting to read off: the lineup is ten of QB 1-2, RB 1-3, WR 2-5, TE 1-3, PK 1, so six slots are fixed by
the minimums and four are flex. Allocating the flex greedily across the league gives, for 2026:

| | QB | RB | WR | TE | PK |
| --- | --- | --- | --- | --- | --- |
| Started league-wide | 20 | 30 | 30 | 10 | 10 |

Tight end draws no flex at all — the third best tight end is worth less than the thirty-first best running
back. Superflex, meanwhile, means 20 of about 50 usable quarterbacks start, which pushes quarterback
replacement very high and compresses what the best ones are worth over it.

Replacement is then taken **per week**, as the best player at that position who would not be started *that
week*. A team fields a lineup every week, so on a week when six teams are on bye its alternative is worse
than the season table suggests. Season totals hide this; pricing weekly moves about 1.6 points of the
league's total value onto quarterbacks and running backs.

### 3b. Outcome spread, which is what a bench is worth

Value over replacement at a player's projection is `max(0, E[X] - replacement)`. What a roster spot is
actually worth is `E[max(0, X - replacement)]`, because a player only has to be started in the weeks he is
good. The second is never smaller, and the gap is widest at replacement level — exactly where a bench sits:

| | Rank | `E[max(0,X-r)]` | `max(0,E[X]-r)` | Missed |
| --- | --- | --- | --- | --- |
| QB | 10 | 42 | 14 | +28 |
| WR | 30 | 21 | 0 | +21 |
| RB | 30 | 28 | 11 | +17 |
| RB | 48 | 10 | 0 | +10 |

The spread is real: realised points at a given preseason rank vary with a coefficient of variation around
0.45, and players nominally below replacement still clear it often — WR38 does 43% of the time.

So a season is replayed against **every realised-over-expected ratio the position has produced**, and value
over replacement averaged across them. Replacement itself stays at its expectation, being the best of
whoever is left rather than one player's season.

**The distribution is used as observed, not fitted.** It is badly lopsided: almost all the variance is a
left tail of seasons lost to injury, down to zero at quarterback, while the upside stops around 1.6 to 1.9
times expectation. Fitting a lognormal to that variance mirrors the left tail into a right one and invents
multipliers above three, which prices the bench as if every deep player might become a star. That attempt
put 71 players above a dollar but flattened the whole board and collapsed the tag count to three.

### 4. Dollars from a known pot

Teams spend a fairly steady share of the cap they have free — 65% to 85% across the record, **80%** across
the four superflex seasons — so the pot is knowable before the auction. For 2026 that is $2,438 free
against $1,950 expected spend.

That 80% is counted over distinct players. The week 1 snapshots repeat a handful of roster rows verbatim,
same franchise and same salary, and summing rows rather than players counts those contracts twice and puts
the figure at 83% — Cooper Kupp's $94 in 2022 is one of them. `AuctionValuationSpec` recomputes both this
and the market shares from the committed seasons, so neither constant can drift from the data it came
from. Each player who must be signed is reserved a dollar and the rest is shared in proportion to value
over replacement, so **prices sum to the money available**.

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
spent tagging him back in the pot. Getting this wrong is expensive: pricing him at a rate set by a pool he
had been removed from, against a pot his tag had already left, inflated Ja'Marr Chase from $163 to $178,
because once the tagged are gone the top of the board is a large share of what remains.

## What it produces for 2026

105 players priced, $1,873 total. The highest `MARKET` price is Ja'Marr Chase at $97,
which no one pays because he is tagged at $61.

| | Model 2026 | Actual 2025 |
| --- | --- | --- |
| Top price | $97 | $100 |
| Players above $1 | 68 | 70 |
| Teams tagging | 7 | 7 |

Position shares land between pure value over replacement and the market, which is what `MARKET_WEIGHT` of
0.5 asks for:

| | QB | RB | WR | TE |
| --- | --- | --- | --- | --- |
| Model | 24.5% | 37.8% | 30.8% | 6.9% |
| Market | 21.2% | 30.6% | 38.5% | 8.9% |

Ten tags are predicted, one per team, led by Ja'Marr Chase at a $61 tag against a market price well above
it.

## Team context

`-t teams` reports what each team brings to the auction: roster and contract counts, positions already
signed, free cap, and **exposure** — what keeping every one of its expiring players would cost, against the
cap it has to do it with.

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
- **Nothing prices team need or budget.** Prices are a league-wide clearing rate, so a desperate buyer with
  cap to spend is averaged in rather than identified. `-t teams` is the stopgap; pricing at the highest
  bidder needs the auction simulation.
- **The spread cannot tell volatility from disagreement.** It is realised variation, so a genuinely erratic
  player and one the consensus simply misjudged look identical. For pricing that is the right total, but it
  means the model has no notion of a safe pick versus a risky one.
- **Contract length is not modelled.** Length is chosen jointly with price rather than being an input:
  longer deals go to players with better dynasty-than-redraft ranks, but cost *less* per year, since the
  expensive win-now players take one-year deals. Redraft rank predicts salary better than dynasty rank
  (-0.627 against -0.558). The dynasty gap is not yet used.
- **Ten of ten teams are predicted to tag**, at the high end of the observed 5-to-9. 2026's tag prices are
  low against the market because they are computed from 2025 salaries.
- **The calibration is fitted on three seasons.** Positional shares swing hard year to year, and dropping
  2022 as a transition year buys accuracy at the cost of a thinner sample.
- **Top-end prices are ceilings, not clearing prices.** Value over replacement is the most a rational team
  would pay, but an auction clears at what the *second* bidder will go to, and that gap is widest at the
  very top. Nothing here models per-team budgets or bidding, so treat the top of the board as a walk-away
  number rather than a forecast. This is the piece the auction simulation would have supplied.
- **The market price of a tagged player is never tested.** It is a counterfactual for a player who will not
  reach the auction, and no observed price can confirm or refute it — see below.

## Value and price are separate numbers

They answer different questions and blending them answered neither. The board reports both.

- `VALUE` — worth: value over replacement priced against the cap, with no adjustment for this league.
- `PRICE` — what open bidding here is expected to settle at: value calibrated to the positional shares the
  league actually spends (`MARKET_WEIGHT` is now 1.0) and to how steeply it bids **within** a position.
- `COST` — what the holding team actually pays, which is the tag price for a franchised player.
- `ACQUIRE` — what it takes to prise him off the team that holds him.
- `EDGE` / `BAND` — `VALUE − PRICE`, banded rather than given to the dollar, because it is the difference
  of two noisy estimates and a precise figure would overstate the resolution.

Within-position steepness is fitted per position as `price ~ value^gamma` over historical signings by
consensus rank over the same seasons: **QB 1.44, RB 1.13, WR 1.07, TE 1.51**. Tight end is the fragile one
— it swings on a handful of signings a year and moved from 0.84 to 1.51 when 2022 came out. Quarterback is much the steepest, which is the
league paying an elite-starter premium that value over replacement will not produce on its own, since
superflex starts twenty quarterbacks and so sets a high replacement. This belongs to price and never to
value: it describes behaviour, not worth.

The split immediately shows what the blend was hiding. This league **overpays for receivers and underpays
for running backs**: Ja'Marr Chase is worth $80 and priced at $114, while Christian McCaffrey is worth $73
and priced at $64.

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

## Why the top of the board exceeds anything ever paid

The largest auction price in the record is $100. The model puts Chase at $163. Both can be true, because
**the observed prices are censored by the tag**: the best players are tagged at the positional average of
last year's top five and never reach open bidding, so the auction has essentially never had to price a
top-five player. The one time it nearly did, Lamar Jackson in 2025, the winning team paid $100 *and* gave up
a first round pick, so even that understates what he cost.

Some of the gap is genuinely the model: prices at the very top are willingness to pay rather than clearing
prices, and the board is too concentrated overall, putting 94% of the pot in its top 40 against 87% in
2025. But the rest is a real edge the rules create, and it is why the model expects all ten teams to tag.

## Provenance

**Projections are only a forecast if collected before the season.** The league site keeps one projection per
week and rewrites it as the season goes, so a week 8 projection pulled in December was made knowing who got
hurt in week 3. Summed across 2025, its projections correlate **0.95** with what actually happened —
impossible for a forecast. Week 1 projections, which were genuinely made in advance, correlate 0.17 to 0.65.

Two consequences, both load-bearing:

- `projected_scores.json` must be captured **before the season** and never refetched afterwards. This puts
  it in the same category as `rosters.json` and `league.json`: refetchable in the sense that the request
  succeeds, but wrong. `MflWeeklyScoresRefresh` refuses to overwrite projections once week 1 and week 14
  stop resembling each other, which is the signature of in-season revision.
- The realisation correction can never compare a finished season's projections against its results, because
  that measures hindsight. It compares **realised scoring against preseason consensus rank**, which cannot
  be revised after the fact.

`player_scores.json` has no such problem and is refetchable at any time.

Both are collected by `./data_refresh.sh <year>` for the coming season and
`./season_history_refresh.sh <year>` for finished ones.
