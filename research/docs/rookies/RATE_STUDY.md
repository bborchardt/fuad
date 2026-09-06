# QB conditional-rate and dependence study

Run `./research/run.sh rate-study [output-directory]`, defaulting to `reports/fuad/rate-study`.
Allow about two minutes including compilation, depending on the machine. This follows the
[availability study](AVAILABILITY_STUDY.md); production valuation remains unchanged.

## What the comparison isolates

Partial pooling improved availability without consistently improving VOR. Reweighting historical
rate/games pairs changes three things: games, scoring rates, and their dependence. This study separates
those effects using five forecasts, with identical rate curves, level uncertainty and replacement:

| Forecast | Games marginal | Conditional-active rate marginal | Pairing |
| --- | --- | --- | --- |
| CURRENT_JOINT | Current | Current | Original donor pairs |
| PARTIAL_JOINT | Nested partial | Nested partial | Original donor pairs |
| CURRENT_INDEPENDENT | Current | Current | Independent |
| PARTIAL_INDEPENDENT | Nested partial | Nested partial | Independent |
| PARTIAL_GAMES_CURRENT_RATE | Nested partial | Current | Independent |

The joint forecasts reproduce CURRENT and PARTIAL from the preceding study. The other three are
diagnostic counterfactuals, not recommended valuation fixes. Independence removes the relationship
between scoring rate and duration conditional on playing. It does not remove the probability of playing:
expected capped games includes zero-game outcomes and multiplies expected per-game positive VOR,
averaged over the 13 playable weeks and the conditional-active rate distribution. This applies play
probability exactly once. The reported `coupling` is joint minus independent expected VOR for the same
marginals; it is not an observed causal effect.

Conditional-active means **any recorded game**, including relief appearances, not becoming a starter.
Zero-game seasons have no observed rate and leave only the rate-scoring sample. They remain in games
and VOR evaluation. Negative or low rates from played seasons are retained. Each rate distribution uses
the same five-point level-uncertainty integration as the joint model.

The nested availability weight is reused unchanged: nine outer class holdouts, each with eight inner
class holdouts, selecting only on inner games MSE. No rate or VOR error selects a new parameter here.
Entire held-out careers leave curves and outcome pools, and replacement excludes the outer class.
This remains retrospective class cross-validation, not a rolling forecast or an untouched test: these
classes informed the research direction, and overlapping careers/folds are not independent samples.

## Outputs and scoring

- `predictions.tsv`: annual observed/expected games, conditional rate mean and 10th/90th percentiles,
  rate CRPS, observed/expected VOR, and the joint-minus-independent contribution.
- `rates.tsv`: rate bias, RMSE, CRPS, central 80% interval coverage/width and mean annual coupling,
  by class, all/top-50 cohorts and rookie QB rank bands. Eligible, played and scored counts are distinct.
  CRPS scores the entire rate distribution in points per game; lower is better.
- `careers.tsv`: complete one-, three- and five-year VOR metrics on common support across all five
  variants, including missing counts. Future years are never padded with zeroes.
- `selection.tsv`: the unchanged inner availability evaluations and chosen weights.

The independence variants share their conditional rate metrics with the corresponding joint variant;
these duplicate report rows are not additional evidence. All five variants share the rate-scoring sample
in this run. Coverage alone is not accuracy: excessively wide intervals can cover almost everything.

## September 6, 2026 results

The current and partial joint forecasts reproduce the prior availability study to report precision.
Selection again chooses 75% local weight except for 2022, which chooses 100%. Of 177 supported top-50
QB player-seasons, 148 have a rate to score and 29 have no games. All 48, 34 and 25 complete top-50
careers have common support at horizons one, three and five respectively.

| Conditional-active rate metric | Current | Nested partial |
| --- | ---: | ---: |
| Bias (points/game) | -0.966 | -0.557 |
| RMSE (points/game) | 7.192 | 7.173 |
| CRPS | 4.204 | 4.176 |
| Central 80% coverage | 81.1% | 79.1% |
| Central 80% width (points/game) | 21.504 | 19.367 |

Conditional-rate accuracy barely changes: RMSE improves 0.3% and CRPS 0.7%. That does not support a
broad claim that partial pooling damages the rate distribution. The rank groups are mixed:

| Rank band | Played seasons | Current / partial RMSE | Current / partial CRPS | Current / partial 80% coverage |
| --- | ---: | ---: | ---: | ---: |
| QB1 | 34 | 6.695 / 6.818 | 3.841 / 3.854 | 94.1% / 85.3% |
| QB2–3 | 63 | 6.720 / 6.643 | 4.001 / 3.884 | 87.3% / 82.5% |
| QB4+ | 51 | 8.028 / 7.990 | 4.697 / 4.752 | 64.7% / 70.6% |

The deepest prospects have the largest rate errors and undercoverage in both pools. Partial pooling's
slightly lower RMSE there coexists with worse distributional CRPS; a mean-error improvement is not a
general improvement in probabilistic forecasts.

| Forecast | Year 1 VOR RMSE | Three-year RMSE | Five-year RMSE | Five-year MAE | Five-year bias | Five-year Spearman |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Current joint | 15.91 | 80.14 | 145.33 | 107.84 | -34.85 | 0.219 |
| Partial joint | 16.22 | 80.91 | 143.07 | 110.06 | -26.73 | 0.221 |
| Current independent | 15.94 | 81.25 | 149.75 | 103.32 | -54.10 | 0.217 |
| Partial independent | 16.23 | 81.37 | 146.46 | 104.27 | -45.35 | 0.229 |
| Partial games / current rate, independent | 15.94 | 81.19 | 145.94 | 104.65 | -40.64 | 0.251 |

Preserving the rate/duration pairing raises mean five-year VOR by 19.25 in the current pool and 18.61
in the partial pool. Removing it worsens underprediction and RMSE, although it lowers MAE. The hybrid's
somewhat higher rank correlation is not a validated selection rule, especially on just 25 careers.

The mean five-year forecast change from current joint (75.84) to partial joint (83.95) can be decomposed
along this explicitly chosen path:

- Changing games alone under independence: **+13.46** VOR (56.59 to 70.05).
- Then changing the conditional rate marginal: **-4.71** VOR (70.05 to 65.34).
- Change in the pairing contribution: **-0.64** VOR (19.25 to 18.61).

Together these yield **+8.11** VOR. This explains part of the forecast movement, not a decomposition of
RMSE or a causal explanation of player careers. The order of marginal substitutions matters. In
particular, a better conditional mean-rate bias does not guarantee more above-replacement payoff:
VOR depends on the distribution above the replacement threshold, not merely its mean.

## Decision and next question

Keep production unchanged. Neither removing dependence nor changing only the games marginal produces
consistent gains across valuation metrics and horizons. The evidence points beyond a simple availability
correction: the hard cases are deep prospects' scoring distributions and the upper tail above replacement.

Next, score held-out probabilities of exceeding the fold-specific replacement level and a fixed high-VOR
threshold, by rank and contract year, retaining zero-game seasons as failures. Compare calibration and
Brier scores before introducing a new predictor. Use the same class exclusions and report sparse-group
counts; do not tune a threshold or rank exception on these outer errors. Any subsequent model change
needs evaluation beyond the classes that motivated it. These VOR diagnostics do not establish dollar-price
accuracy. Result tables remain dated snapshots; figure-marker integration is deferred with the rest of
this research series.
