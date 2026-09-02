# Open questions

Things measured and not yet decided. Each one says what was found, how much it moves, and what a fix would
have to answer — so that picking it up later starts from evidence rather than from the memory of a
conversation.

## `PRICE_STEEPNESS` is fitted against a value column that has since changed shape

`AuctionValuation.PRICE_STEEPNESS` bends each position's price curve as `price ~ value^gamma`, fitted across
this league's signings by consensus rank. Giving each rank its own outcome spread changed the value it was
fitted against — not the level, which `calibrate` pins to `MARKET_SHARE` whatever value does, but the
distribution **inside** a position, which is the only thing gamma describes and the only channel by which
value reaches `PRICE` at all.

Top five's share of a position's value over replacement, over the players in the 2026 pool:

| POS | gamma | before | after | change |
| --- | --- | --- | --- | --- |
| QB | 1.44 | 51.6% | 50.8% | -0.8pt |
| RB | 1.13 | 51.1% | 52.3% | +1.2pt |
| WR | 1.07 | 54.9% | 56.9% | +2.0pt |
| TE | 1.51 | 66.3% | 60.0% | **-6.3pt** |
| PK | 1.00 | 42.3% | 41.1% | -1.2pt |

Tight end moved six points and carries the steepest gamma on the board.

### How much it moves

Prices are more sensitive to gamma than to anything the repricing did. Moving every gamma down by 0.10 and
repricing the 2026 board:

| POS | largest `PRICE` move | at |
| --- | --- | --- |
| QB | 94 to 86 | Lamar Jackson, QB2 |
| RB | 75 to 70 | Jonathan Taylor, RB4 |
| WR | 104 to 94 | Ja'Marr Chase, WR1 |
| TE | 30 to 29 | Kyle Pitts, TE8 |
| PK | 3 to 2 | Ka'imi Fairbairn, PK3 |

Ten dollars at the top of receiver, against the four dollars that was the largest `VALUE` move anywhere on
the board from the spread change that raised this question. A tenth of a gamma is not a small number, and
nothing says how far a refit would move one.

### Nothing would notice it going stale

`MARKET_SHARE` is measured spend: `spend.tsv` regenerates it from the committed seasons and
`AuctionValuationSpec` holds the constant to it, so it cannot drift away from the record without something
failing. Gamma is fitted **against the model**, and the fit is not in the repository — grep finds it only as
a hardcoded map. `figures_refresh.sh` and `check_docs.sh` have nothing to hold it to, so a change to value
moves what it was fitted to and leaves no trace at all. That is the part worth fixing whatever the numbers
turn out to be.

**An attempt to reproduce it does not.** Regressing log price on log value over the 2023-25 signings joined
to consensus rank — 245 of 257 signings and $5,503 of $5,711, so the join is not the problem — gives:

| POS | committed | all signings | above the minimum bid |
| --- | --- | --- | --- |
| QB | 1.44 | 0.64 | 0.59 |
| RB | 1.13 | 0.87 | 0.73 |
| WR | 1.07 | 0.94 | 0.75 |
| TE | 1.51 | 0.82 | 0.59 |
| PK | 1.00 | 0.36 | 0.43 |

Every one lands below one where four of the five committed figures are above it. **That is a difference of
method and not evidence the constants are wrong.** The $1 minimum bid censors the bottom of every position
and flattens a log-log slope, and nothing records whether the original fit dropped those signings, weighted
by dollars, fitted mean price by rank rather than price by signing, or regressed on the dollar `VALUE`
column rather than on value over replacement. The attempt above also pools three seasons of dollars without
normalising each auction's pot, which shifts an intercept per season and can flatten a slope where the pot
moved. Any one of those could account for the gap; the point is that none of them can be ruled out from what
is written down.

### Measured against what the league actually paid

The board can be priced for a past season, so the question is answerable rather than arguable. Pricing
2022-2025 and joining each board to what every player was really paid — the same signings `AuctionSpend`
counts, an expiring contract re-signed or a veteran on no pre-draft roster who was not a later waiver
pickup — joins 245 of 257 signings over the calibrated seasons and 66 of 68 in 2022.

That join is now the model's own, in `AuctionAccuracy` and reported as `accuracy.tsv`; the figures below came
from the scratch version of it, which matched to the dollar except where joining on names rather than on
identifiers cost it a player.

`PTS` and `G` are identical between the two models for every joined player and only `VOR` differs, so this
isolates the outcome spread and nothing else.

| | n | old MAE | new MAE | difference | 95% CI |
| --- | --- | --- | --- | --- | --- |
| 2023-25, as committed | 245 | 7.33 | 7.77 | +0.44 | +0.16 to +0.73 |
| 2023-25, each at its own best gamma | 245 | 6.87 | 7.14 | +0.27 | +0.00 to +0.53 |
| 2022, held out of the calibration | 66 | 8.86 | 9.15 | +0.29 | -0.56 to +1.47 |

**Giving each rank its own outcome spread makes the board fit the record worse, and refitting gamma does not
rescue it.** It recovers about two fifths of the gap and leaves the rest, which is marginal at the pooled
level and driven by running back (+0.58, CI +0.03 to +1.10) and receiver (+0.41, CI -0.12 to +0.92).
Quarterback is flat, tight end is slightly better, kicker is identical to the dollar. The one held-out season
cannot tell the two apart at all, and has the new board closer on more players than the old one while its
mean error is higher.

Scaling each board to the pot actually spent, and then to each position's own actual spend, moves the gap
hardly at all — 7.28 against 7.75 on the last of those — so this is entirely the shape **inside** a position,
which is what gamma and the outcome spread both act on and what makes the two confounded.

### The committed gammas are worth more than any of this

The same sweep, read down its own column, says the constants are a long way from what fits:

| POS | committed | best on this measure | MAE at committed | at best |
| --- | --- | --- | --- | --- |
| QB | 1.44 | 1.0 | 10.24 | 8.72 |
| RB | 1.13 | 0.8 | 8.54 | 7.55 |
| WR | 1.07 | 1.0 | 7.86 | 7.86 |
| TE | 1.51 | 1.0 | 7.14 | 6.10 |
| PK | 1.00 | 1.2 | 0.82 | 0.79 |

Refitting is worth about 0.6 of mean absolute error on the old model and 0.8 on the new one, against the 0.27
to 0.44 the spread change costs. **So the two together leave the board better than it is today** — 7.33
becomes 7.14 — while the spread change alone makes it worse. That is the whole of the merge argument, and it
is why the two belong in one change rather than two.

It also means the log-log regression above is not alone: two methods that share no arithmetic both say gamma
should be lower than what is committed, and at quarterback and tight end much lower. A gamma near one is no
steepening at all, which contradicts the reading these constants exist to express — that this league pays a
premium for an elite starter. Something is wrong in one direction or the other and neither method can say
which.

**None of the above is out of sample.** The curve is built from 2017-2025 whichever season is priced, so the
level leaks; `MARKET_SHARE` is fitted on the same three seasons the error is measured over; and the best-gamma
rows fit five parameters on the same 245 observations they are scored on. Both models carry all three
equally, so the comparison between them is fair while the absolute figures are flattered. 2022 is the only
genuinely held-out season and it is one season the league had not adjusted to superflex in.

### What a fix would have to answer

- **What the procedure was.** Nothing else can be settled until the fit can be reproduced, and the constants
  are the only record of it. It is the same problem `check_docs.sh` was written for, one level further in: a
  number generated once and then quoted.
- **What to do about the minimum bid.** Half the kicker signings and a third of the tight ends are at $1,
  which is a censored observation rather than a cheap one. Dropping them fits the shape of what the league
  bids for and throws away most of what it bids on.
- **Whether it is refitted or regenerated.** A constant refitted by hand goes stale the next time value
  moves, and value has now moved twice. A fit that lives in the model and writes its figures like everything
  else cannot.
- **Whether gamma should be fitted against value at all.** It is fitted against a model quantity, so it
  inherits every change to that quantity. Fitting price against the consensus **rank** instead would make it
  a description of the league that a repricing cannot invalidate — at the cost of no longer composing with
  the value curve the way `steepen` assumes.
- **Whether the sweep belongs in the repository.** Half of this is now answered: the board is held to the
  record by `AuctionAccuracy`, written to `docs/figures/fuad/<year>/accuracy.tsv` on every refresh and
  described in [PROJECTION.md](fuad/PROJECTION.md#how-close-the-board-comes-to-what-was-paid), so the
  question "is the board any good" no longer starts from nothing. The sweep that answers "what should gamma
  be" is still a scratch script — a constant patched and the project rebuilt, seven times, twice over — and
  it is the half that would have to run for a refit to be checkable rather than asserted.

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
