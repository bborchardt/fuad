# Rookie backtest

Run `./research/run.sh backtest` from the project directory with the same Java environment as the other report
scripts. An optional directory argument changes the default output, `reports/fuad/backtest`.
The command compiles the project and uses local historical resources; it does not refresh rankings or change
the production rookie board.

A full run, including nested holdouts, was observed to take about seven minutes. Runtime depends on the
machine, Java environment and input size. Empty result sets are written as header-only TSV files, replacing
any older output rather than leaving stale results behind.

## What is tested

Each of the 2017–2025 rookie classes is held out in turn. Every observed contract year of that class is
removed from the rookie training data. Its players are also removed from historical veteran rankings before
the replacement curve is fitted. The target's draft-time rookie and dynasty ranks are legitimate predictors;
its outcomes are only used for evaluation.

Training may include later classes. This is leave-one-class-out cross-validation, not an assertion that these
forecasts could have been issued in the historical draft year. The fixed modeling architecture was developed
using this historical record, so results remain exploratory; a future untouched class is a stronger test.

All folds use FUAD 2026 scoring and starting requirements, a 14-week fantasy season, and one standardized bye.
Replacement is rebuilt in each fold with no veteran bye adjustments. This isolates player selection under
one league format rather than mixing the league's past lineup regimes and schedules.

The target is **season-rate points over replacement**: replay the observed scoring rate and games played
against replacement using the production expected-value routine. This preserves injuries and zero-game
seasons, but is not observed weekly lineup optimization, manager start/sit accuracy, or championship wins.
Auction dollars, salary savings, demand/pick forecasts, and cut decisions are intentionally not scored here.

## Candidates

| Model | Forecast |
| --- | --- |
| `FULL_REFIT` | Production rookie curve/outcome machinery and five-point level-uncertainty integration, with a training-only dynasty adjustment. |
| `ROOKIE_ONLY` | Identical forecast with the dynasty multiplier fixed at one. |
| `ROOKIE_POINT` | Rookie-only forecast without level-uncertainty integration; retains the empirical outcome distribution and busts. |
| `DYNASTY_ONLY` | Mean observed VOR among the nearest 20 training observations at the same position and contract year, indexed solely by positional dynasty rank. Boundary ties are retained. |
| `DYNASTY_CENTERED` | Full-refit predictive VOR distribution rescaled to preserve rookie-only expected VOR totals in the training top 50, separately by position and contract year. |
| `DYNASTY_SHRUNK` | Mixture of rookie-only and centered distributions, with its weight selected exclusively by inner class holdouts. |

`FULL_REFIT` tests the model's architecture, not the frozen production constants: expected dynasty ranks,
the 90th-percentile residual cap, nonnegative slope, taper plateau and taper decay are estimated without the
target. Expected ranks use training entrants and the production neighboring-rank median. The fit uses positive
dynasty residuals and positive scoring rates from contract years one through three. It minimizes squared
error of log observed rate divided by the training rookie curve's rate, with a fitted nuisance intercept.
The intercept absorbs the offset between log rates and an arithmetic mean; only the slope enters the
production-shaped adjustment, leaving the baseline curve unchanged. Plateau candidates
are 1–5 and decay candidates are 1, 2, 3, 5, 8. Each fold's winning parameters are exported.

The one-sided functional form, smoothing windows, minimum samples, uncertainty quadrature, and dynasty-only
neighbor count are fixed architecture choices, not parameters chosen by inspecting backtest winners. The
refitting procedure is explicit and reproducible; it is not a reconstruction of the undocumented calculations
that originally produced all production constants.

Dynasty-only uses log rank distance and never reads rookie rank. A player absent from dynasty rankings is
compared to unranked training peers; if none exist, it uses the deepest 20 positional dynasty ranks. Sparse
training pools use all available peers. A rookie curve with no supported level produces a missing forecast,
not zero. Missing forecasts are excluded from **every** candidate's comparison sample and explicitly counted.

Archived rookie lists can repeat a positional rank. Outcomes are joined using a unique temporary player
index before restoring the original predictor rank, so two players sharing a rank retain separate careers.
Name matching otherwise follows the shared historical scorer, including zero outcomes for unmatched names;
historical source and identity errors remain a data limitation.

## Reports

- `predictions.tsv`: each player, contract year, candidate, observed VOR, predicted VOR and predictive bounds.
- `fits.tsv`: training classes and dynasty parameters for every holdout, for leakage and stability inspection.
- `calibration.tsv`: outer training calibration factors by position and contract year.
- `selection.tsv`: every inner class/strength score, sample counts, training class list, and selected weight.
- `metrics.tsv`: each fold and the pooled sample, by position and all positions, for all ranked players and
  the draft-relevant overall top 50. Horizons are one, three and five cumulative contract years.

Only classes with the entire horizon observed are eligible: nine classes at one year, seven at three, five
at five. Later unobserved seasons are never treated as zero. `eligible`, `scored`, and `missing` expose losses
from unsupported forecasts. Metrics use the identical common sample for all candidates.

MAE and RMSE are forecast errors in VOR points (lower is better). Bias is prediction minus observation;
positive means overvaluation. Actual and predicted means expose aggregate calibration. Spearman correlation
uses average ranks for ties and is blank for a constant series. Read individual folds and positions alongside
pooled results: a pooled ranking can benefit simply from distinguishing positions or deep picks from early ones.

The 10th and 90th percentiles are **predictive** bounds from each forecast's outcome mixture. `coverage80`
and `width80` are scored for year-one predictions only. Discrete zero outcomes can yield coverage above 80%
even in a calibrated model. These are not the production `VAL_LOW` and `VAL_HIGH` sensitivity bands.
No three- or five-year interval is reported: summing marginal quantiles would imply an unsupported assumption
about career dependence. This first backtest does not claim to validate the production valuation bands.

MAE alone rewards conservative forecasts for highly skewed rookie outcomes. Compare RMSE, bias, ranking,
coverage and width before selecting a model; a different winner by position is a finding, not permission to
tune on that same held-out sample and call the resulting improvement independently validated.

## Initial run: September 5, 2026

The top-50 common sample contains 444 first-year players, 344 complete three-year careers, and 224 complete
five-year careers. The five-year sample excludes 20 of 244 eligible players because at least one forecast
year lacks support. These exclusions are shared across candidates and warrant attention before generalizing
to every draft pick.

| Candidate | Year 1 MAE | Three-year MAE | Five-year MAE | Five-year RMSE | Five-year bias | Five-year Spearman |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Full, refitted | 8.22 | 34.93 | 65.60 | 99.86 | +12.24 | 0.501 |
| Rookie only | 7.07 | 32.57 | 60.17 | 95.32 | -1.56 | 0.508 |
| Rookie, no level uncertainty | 6.87 | 31.85 | 58.51 | 95.72 | -5.10 | 0.499 |
| Dynasty only | 6.42 | 32.95 | 61.80 | 97.69 | -6.21 | 0.392 |

All errors are in VOR points, not dollars. The refitted dynasty premium increases five-year MAE by about 9%
relative to rookie-only and changes aggregate bias from near zero to positive. The result is not uniform:
it improves five-year MAE in two of five held-out classes, and improves pooled first-year Spearman from
0.394 to 0.424. This supports investigating calibration rather than claiming dynasty rankings contain no
information. The fitted slopes range from 0.38 to 0.70 and are not the production slope of 0.258.

Removing level-uncertainty integration lowers MAE but slightly worsens five-year RMSE and increases
underprediction. There is no metric-independent winner. Year-one 80% predictive coverage is 94.1% for the
full refit, 92.1% for rookie-only, 91.9% without level uncertainty, and 91.2% for dynasty-only; zero-mass
discreteness and interval widths must be considered before calling these intervals well calibrated.

These figures record the initial run. The generated TSV files are the authoritative results when inputs or
code change; the snapshot is not a test that future runs are required to reproduce.

## Centering and nested shrinkage

For each outer fold, the centered candidate multiplies the full-refit VOR distribution by
`sum(rookie-only predicted VOR) / sum(full-refit predicted VOR)` within each position and contract year.
Both sums use the same supported top-50 training observations, including zero-game players. This preserves
the baseline model's predicted total on that training sample, rather than fitting to a noisy observed total.
It reallocates the baseline value toward favored dynasty prospects instead of adding premiums on top of an
unconditional historical average. The same factors apply to deeper players, but preservation of their totals
is not guaranteed. A group with zero total full-refit value uses a neutral factor of one.

Centering is done in VOR space because preserving an average scoring rate alone does not preserve expected
VOR after its nonlinear replacement threshold. This is a new research candidate; it is not installed in the
production rookie valuation. No held-out player or class influences its centering factor.

Shrinkage mixes the rookie-only predictive distribution with the centered predictive distribution at weights
0, 0.25, 0.5, 0.75 and 1. Zero reproduces rookie-only exactly. For each outer class, every remaining class
with five observed contract years is an inner holdout. Each inner fit excludes BOTH classes from rookie
curves, dynasty fitting, centering and veteran replacement. Only complete, supported top-50 five-year careers
are scored, with identical samples at all strengths. The selected strength minimizes the equal-class mean
of inner five-year squared error; ties favor the smaller adjustment, and absent usable evidence selects zero.
This uses four or five inner classes per outer fold and one global weight, rather than separate weights on
very small positional samples. Parameters are refitted on all outer training classes for the final forecast.

Predictive bounds for the mixture come from mixture quantiles, not interpolated endpoints. All existing
models remain in the report. Outer errors, bias, rank correlation, positional breakdowns and individual
classes are evaluation results only; none choose the mixture strength. The design of this follow-up was
motivated by the initial results on these same classes, so nested fitting prevents direct parameter leakage
but does not make these data a newly untouched confirmation set.

## Nested comparison: September 5, 2026

All nine outer folds and 40 inner class evaluations completed on the same common samples as the initial
run. The selected centered-distribution weights were 0.50, 0.25, 0.25, 0.75, 0.50, 0.50, 0.25, 0.75, 0.50
for outer classes 2017 through 2025 respectively. These are predictive-mixture weights, not a replacement
for the production dynasty slope.

| Candidate | Year 1 RMSE | Three-year RMSE | Five-year RMSE | Five-year MAE | Five-year bias | Five-year Spearman |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Rookie only | 14.41 | 52.95 | 95.32 | 60.17 | -1.56 | 0.508 |
| Full, refitted | 15.08 | 54.13 | 99.86 | 65.60 | +12.24 | 0.501 |
| Dynasty centered | 13.97 | 51.56 | 95.30 | 59.03 | -0.95 | 0.485 |
| Dynasty centered, nested shrinkage | 14.17 | 52.26 | 95.17 | 59.63 | -0.78 | 0.499 |

Centering removes most of the full refit's upward bias. Nested shrinkage improves five-year RMSE over
rookie-only by just 0.16% and MAE by 0.90%, while reducing rank correlation. It improves five-year RMSE in
four of five outer classes, but its deterioration in 2020 offsets much of those gains. MAE improves in three
of five classes. The simpler centered candidate is more useful at the shorter horizons, but its five-year
ranking correlation is weaker still.

The positional result is mixed, even with one globally selected weight:

| Position | Five-year N | Rookie-only RMSE | Nested-shrinkage RMSE | Rookie-only Spearman | Nested-shrinkage Spearman |
| --- | ---: | ---: | ---: | ---: | ---: |
| QB | 25 | 145.33 | 149.38 | 0.219 | 0.126 |
| RB | 78 | 95.64 | 94.40 | 0.618 | 0.621 |
| WR | 93 | 88.56 | 87.42 | 0.431 | 0.428 |
| TE | 28 | 47.94 | 48.23 | 0.321 | 0.355 |

Recommendation: retain these as research candidates rather than changing production valuation. Recentring
addresses a concrete calibration problem, but the incremental five-year gain over rookie-only is too small
and position-dependent to establish a stronger player-selection model. The short-horizon results justify
continued investigation or prospective evaluation; they do not validate dollar values or warrant selecting
position-specific rules after inspecting these same outer results.
