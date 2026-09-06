# QB availability pooling study

Run `./research/run.sh availability-study [output-directory]`, defaulting to
`reports/fuad/availability-study`. This follows the [QB diagnostic](QB_DIAGNOSTICS.md) and does not change
production valuation. The study uses the same archived classes, FUAD scoring and standardized 13 playable
weeks as the rookie backtest.

## Design

The candidates differ only in how they weight historical QB outcome pairs:

- `CURRENT` uses the production sliding window with its minimum-observation widening.
- `LOCAL` uses all supported training QB observations at the contract year, weighted by
  `exp(-abs(donor rookie rank - target rookie rank))` and normalized to sum to one.
- `PARTIAL` mixes CURRENT and LOCAL, choosing the LOCAL weight from 0, 0.25, 0.5, 0.75 and 1.

Each donor retains its scoring rate multiplier and games as a pair. Multipliers are normalized by the
donor's own rookie-rank rate, exactly as in production. Zero-game seasons remain in both distributions.
Unsupported donor rates are excluded by the same positive-level criterion. A target without a supported
level or pool produces a missing prediction rather than a forecast of zero.

The rate curves, level-uncertainty integration and replacement are identical across candidates within each
outer fold. Dynasty multipliers are fixed at one: CURRENT reproduces the prior rookie-only benchmark.
Reweighting paired outcomes changes both availability and the distribution of rate residuals. This is a
test of joint outcome pooling; an improvement cannot be attributed exclusively to games played.

## Nested selection and evaluation

Each class from 2017 through 2025 is held out as an outer fold. Its entire career leaves training. Each of
the other eight classes then serves as an inner holdout; both classes leave the inner curves and outcome
pools. The weight minimizes the equal-class mean of annual games-played squared error among supported
top-50 rookie QBs, over all contract years observed for each inner class. Games are capped at 13, consistent
with the standardized bye used by the VOR replay. Ties select less local weight; no usable evidence selects
zero. No VOR outcome or outer-class outcome is used to choose the weight.

Outer replacement is fitted without the entire outer class, then held fixed across all candidates. Inner
selection does not use replacement or dynasty fits because its objective is games played. Later classes
can train earlier folds, so this is retrospective cross-validation, not a rolling historical forecast.
These classes already informed the decision to investigate pooling; nested selection prevents direct
parameter leakage but does not constitute an untouched prospective test.

The reports are:

- `predictions.tsv`: annual expected games, zero-game probability and expected VOR, with observations and
  each outer fold's selected strength.
- `availability.tsv`: games bias/RMSE, observed and predicted zero-game frequency, and Brier score, by
  class, all/top-50 cohorts, and QB1/QB2–3/QB4+ rank groups. These rows pool observed contract years;
  overlapping careers and training folds are not independent samples.
- `careers.tsv`: VOR bias, MAE, RMSE and Spearman correlation for complete one-, three-, and five-year
  careers, by class and cohort. Missing support is counted; future seasons are never padded with zeroes.
- `selection.tsv`: each outer/inner class and candidate weight, with eligible/scored counts, games MSE and
  whether that strength was selected. All weights use the same supported inner sample.

Lower games RMSE and zero-game Brier score indicate better availability predictions. Better availability
without better VOR or ordering is insufficient to recommend changing the valuation model. Report all
candidates and rank groups rather than choosing a new rule after inspecting their outer errors.

The result tables in this research series remain dated snapshots; automated figure-marker integration is
deferred while the experiments evolve.

## September 6, 2026 results

All nine outer and 72 inner class evaluations completed. Inner selection chose 75% local weight in eight
outer folds and 100% for 2022. There are 177 supported top-50 QB player-seasons in the availability sample,
and 48, 34 and 25 complete careers at horizons one, three and five respectively. These are different
observation units; the 177 annual rows do not represent 177 independent prospects.

| Pooling | Games RMSE | Games bias | Observed zero rate | Predicted zero rate | Zero-game Brier |
| --- | ---: | ---: | ---: | ---: | ---: |
| Current | 5.276 | -2.093 | 16.4% | 33.2% | 0.162 |
| Local | 4.857 | -0.815 | 16.4% | 23.9% | 0.143 |
| Nested partial | 4.920 | -1.083 | 16.4% | 25.9% | 0.143 |

The nested mixture reduces games RMSE by 6.7% and Brier score by 11.5%. The broad outcome pool's backup
risk is poorly calibrated at the top of the rookie ranks, not just for the handful of breakout names:

| Rank group | Player-seasons | Current games RMSE | Partial games RMSE | Observed zero rate | Current predicted zero rate | Partial predicted zero rate |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| QB1 | 35 | 5.369 | 4.341 | 2.9% | 26.2% | 11.4% |
| QB2–3 | 70 | 5.355 | 4.896 | 10.0% | 33.3% | 19.0% |
| QB4+ | 72 | 5.150 | 5.201 | 29.2% | 36.4% | 39.7% |

Local weighting improves the top ranks while making deeper prospects' games and zero-game forecasts
worse. This matters because Allen and Hurts, the two largest five-year misses, were both rookie QB5.

| Pooling | Year 1 VOR RMSE | Three-year VOR RMSE | Five-year VOR RMSE | Five-year MAE | Five-year bias | Five-year Spearman |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Current | 15.91 | 80.14 | 145.33 | 107.84 | -34.85 | 0.219 |
| Local | 16.33 | 81.05 | 142.58 | 110.79 | -24.03 | 0.233 |
| Nested partial | 16.22 | 80.91 | 143.07 | 110.06 | -26.73 | 0.221 |

Better availability does not translate into consistently better valuation. Nested partial pooling improves
five-year RMSE by about 1.6% and reduces underprediction, but increases MAE and worsens shorter-horizon RMSE.
Player ordering is almost unchanged. Keep this as a research candidate, not a production replacement.

The next question is whether the model can improve availability calibration without damaging the
distribution of scoring conditional on playing, especially for deep QBs. A follow-up should separately
measure conditional-rate errors and the rate/games dependence carried by each pool, then evaluate any
alternative jointly on availability and VOR. Do not select new rank-specific exceptions from these outer
results and describe them as independently validated. The current findings are retrospective and do not
establish dollar-price accuracy.
