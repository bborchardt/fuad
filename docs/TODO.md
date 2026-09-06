# Open questions

Things measured and not yet decided. Each one says what was found, how much it moves, and what a fix would
have to answer — so that picking it up later starts from evidence rather than from the memory of a
conversation.

## Rookie dynasty calibration has not earned a production change

The [rookie backtest](fuad/ROOKIE_BACKTEST.md#nested-comparison-september-5-2026) compared nine outer class
holdouts with 40 inner evaluations to choose shrinkage. On 224 supported five-year top-50 careers,
centering and nested shrinkage reduced RMSE from 95.32 to 95.17 VOR points (0.16%) and bias from -1.56 to
-0.78, but lowered ranking correlation from 0.508 to 0.499. The uncentered full refit had +12.24 points
of bias. Centering addresses that inflation without establishing a stronger five-year player-selection model.

The positional result is mixed: QB RMSE worsened from 145.33 to 149.38, while RB and WR improved. Only 25
QB careers support that comparison. Twenty of 244 eligible five-year players lacked supported forecasts
and were excluded equally across candidates. Retain the adjustments as research candidates.

A production change would need to explain the QB weakness (replacement level, comparables, or individual
class sensitivity), improve ordering as well as calibration, and survive evaluation beyond the classes
already used to develop these experiments. Preserve dated 2026 forecasts before incorporating that season's
outcomes. The existing study evaluates VOR rather than dollar-price accuracy; improved VOR predictions
would still need their dollar consequences checked.

The [QB diagnostic](fuad/QB_DIAGNOSTICS.md) narrows this question: four breakout careers account for
72.4% of five-year squared error, while the other 21 QBs are overpredicted by 16.90 VOR points on average.
The shortfall persists under replacement thresholds 20% lower and higher. Investigate calibration of the
probability of becoming a starter and the rank-dependent availability pool, rather than raising every QB's
value. Draft-time predictors must be evaluated across all prospects, including busts, with class holdouts.
For rookie QB1, the first-year outcome pool averages 6.57 games against 10.44 at the exact rank in training;
year two averages 8.27 against 12.38. Test whether borrowing availability across ranks is calibrated before
adding new player-specific signals; the sparse exact-rank averages are diagnostic evidence, not replacements
to install without holdout evaluation.

The [nested availability study](fuad/AVAILABILITY_STUDY.md) has now tested rank-weighted and partial pooling.
On 177 top-50 QB player-seasons, partial pooling improves games RMSE from 5.28 to 4.92 and zero-game Brier
from 0.162 to 0.143, chiefly at QB1–3; QB4+ worsens. Five-year VOR RMSE improves from 145.33 to 143.07, but
MAE increases and first-/three-year RMSE worsen. Production remains unchanged. The open question is now
whether availability can be calibrated while preserving the appropriate conditional-rate distribution and
rate/games dependence, particularly among deep prospects. Improved games forecasts alone are insufficient.

## The board still loses to a rank median at running back and receiver

`AuctionStudy` scores every model with its target auction absent from the points curve, spend rate,
positional shares, steepness and rank history, and it carries `RANK_MEDIAN_RAW` beside them: each signing
predicted as the median this league paid at the same position within six ranks, over its **other** seasons.
No curve, no replacement level, no value over replacement.

Held out, pooled over 2022-2025:

| POS | signings | board | rank median | |
| --- | --- | --- | --- | --- |
| QB | 56 | 10.39 | 12.36 | board by 1.97 |
| RB | 74 | 8.86 | 7.70 | **rank median by 1.16** |
| WR | 100 | 9.68 | 8.66 | **rank median by 1.02** |
| TE | 44 | 6.98 | 8.10 | board by 1.12 |
| PK | 39 | 0.97 | 0.90 | rank median by 0.07 |
| all | 313 | 8.15 | 8.05 | **rank median by 0.10** |

**Moving the market shape from VOR to expected points narrowed this and did not close it.** Running back was
2.26 behind and is 1.16; receiver was 1.49 and is 1.02. The aggregate is still the wrong way round, and the
board's advantage is still one position carrying the rest.

Drop the first superflex auction, which every model finds hard and the rank median finds hardest, and it
gets worse rather than better: 7.99 against 7.65 over three folds, and 9.16 against 7.87 at running back.
See `POOLEDEX2022` in
[PROJECTION.md](fuad/PROJECTION.md#the-first-fold-flatters-every-model-so-it-is-reported-both-ways).

### What a fix would have to answer

- **Whether it is the curve or the market.** A rank median encodes what this league pays; the points curve
  encodes what a rank scores. Points shaping has now been tried and wins against VOR without beating the
  median, which narrows the question rather than answering it: the remaining gap is not replacement level.
- **Whether the blends were rejected for the right reason.** `BLEND_50` pools at 7.98 against points' 8.15
  and reaches 7.64 without 2022, where the rank median is 7.65. It was not shipped because it fails to beat
  points in every fold — and the only fold it fails in is 2022. That is a thinner reason than it looked.
- **Whether quarterback is the exception or the rule.** Superflex is what makes quarterback replacement
  bite, and it is the one position where the board is decisively better. If the board only adds value where
  replacement is scarce, that is worth knowing plainly rather than averaged into one number.

## The points-shaped price needs its first prospective season

What four folds cannot supply is a genuinely later season. When the 2026 post-auction roster exists, score
it before changing the model or adding 2026 to any fit. The question is whether points still beat VOR, and
whether any rank blend finally improves consistently rather than only in the pooled result.

## The receiver exponent is well determined and its consequences are not

`PRICE_STEEPNESS` at receiver is fitted at 4.00 on a profile-likelihood interval of [3.18, 4.88], so the
steep market is real and a flatter one is not available to be chosen. But that exponent applies across the
position's whole points range, and moving it across its own interval prices the top receiver anywhere from
about 106 to 153 — against a record whose largest receiver price is 94 and which has never had to price a
WR1 at all, the tag having removed every one of them.

Nothing here is wrong. What is missing is any observation that could narrow it: the six *bid away* events in
`tags.tsv` are the only times this league has revealed what it would pay for a tagged player, and
`PriceSteepness` does not see them. Whether they can be used — they are right-to-match auctions rather than
open bidding, and four of the six are pre-superflex — is the question. Kicker is the other end of the same
problem: its interval is [-0.81, 4.75], so the 1.60 it carries is a constant and not a measurement.
