# Rookie quarterback diagnostic

Run `./research/run.sh qb-diagnostics [output-directory]`, defaulting to `reports/fuad/qb-diagnostics`.
This follows the [rookie backtest](ROOKIE_BACKTEST.md), running nine class holdouts without the nested
shrinkage search. Production valuation is unchanged.

The study isolates rookie-only, rookie-only without level uncertainty, and full-refit forecasts for top-50
rookie QBs. Each entire target class leaves the training curves, dynasty fit and replacement curve. All
candidates and threshold scenarios use identical supported careers. Only complete horizons are scored.
The baseline reproduces the original 25-player five-year QB sample and its 145.33 RMSE.

## Reports and interpretation

- `seasons.tsv` records observed points, games and active-game rate alongside the forecast rate, empirical
  outcome-pool games and zero-game share. It also shows mean games at the exact rookie rank and within the
  rate curve's local window (two ranks either side), plus the local observation count and training classes.
  These overlapping training samples are diagnostic comparables, not independent validation observations.
  Missing observed rates denote zero games. Do not multiply forecast rate by mean outcome games to infer
  expected production: the replay retains rate/availability dependence through paired outcomes.
- `predictions.tsv` records each annual forecast and observation under replacement multipliers 0.8, 1, 1.2.
- `metrics.tsv` aggregates one-, three-, and five-year results by class and across classes.
- `influence.tsv` ranks baseline five-year rookie-only errors by their contribution to squared error and
  reports metrics with that one evaluation player removed. It does not refit the model without that player.

Changing replacement changes the quantity being predicted. A smaller RMSE at a higher threshold is not
evidence for adopting that threshold: it can simply shrink every player's VOR toward zero. These fixed
sensitivity scenarios are not candidates in a search for the best-performing league rule.

## September 6, 2026 findings

| Rookie-only replacement | Mean actual VOR | Mean predicted VOR | Bias | RMSE | Spearman |
| --- | ---: | ---: | ---: | ---: | ---: |
| 80% of baseline | 192.26 | 120.80 | -71.46 | 208.48 | 0.222 |
| Baseline | 110.69 | 75.84 | -34.85 | 145.33 | 0.219 |
| 120% of baseline | 53.67 | 45.23 | -8.43 | 84.12 | 0.080 |

Underprediction persists across these thresholds. Lowering replacement makes the absolute shortfall larger;
raising it reduces the target's scale and worsens ordering. This provides no case for changing replacement
simply to remove the aggregate QB bias.

| Player | Five-year actual VOR | Predicted VOR | Share of squared error |
| --- | ---: | ---: | ---: |
| Josh Allen | 375.38 | 26.78 | 23.0% |
| Jalen Hurts | 358.27 | 24.72 | 21.1% |
| Patrick Mahomes | 383.85 | 84.82 | 16.9% |
| Lamar Jackson | 331.73 | 86.91 | 11.4% |

These four players account for 72.4% of squared error. Descriptively excluding them changes average bias
from -34.85 to **+16.90** across the remaining 21 QBs. They must remain in the actual evaluation; their
exclusion diagnoses concentration and is not an improvement to the model or a reason to discard breakouts.

Class bias is also inconsistent: -97.84 in 2017, -59.66 in 2018, +28.26 in 2019, -114.54 in 2020, and
+56.30 in 2021. A global upward QB adjustment would exacerbate the already-overvalued classes.

The two largest misses were both rookie QB5. In contract years three and four, the held-out Allen and
Hurts forecasts draw from outcome pools averaging roughly 3.3–4.2 games, with 45–49% zero-game seasons.
Both players actually played 13 games in those years. Some forecast rates also undershoot their realized
rates, but the model assigns substantial probability to never playing. That is appropriate for many deep
QB prospects; hindsight about these successful careers does not establish that those probabilities were
wrong before their drafts.

There is also evidence of a pooling mismatch independent of picking out successful names. For the first
QB in each rookie class, mean training availability at that exact rank is consistently higher than the
availability in the wider outcome pool used to value him. Averaging the diagnostic's training means across
outer QB1 folds gives:

| Contract year | Outcome-pool games | Same-rank training games | Local-window training games |
| --- | ---: | ---: | ---: |
| 1 | 6.57 | 10.44 | 9.04 |
| 2 | 8.27 | 12.38 | 10.67 |
| 3 | 5.79 | 9.57 | 8.76 |
| 4 | 5.67 | 9.83 | 8.44 |
| 5 | 5.43 | 7.60 | 7.27 |

The outcome pool borrows games from deeper ranks while normalizing their scoring rates. This suggests
that rank-dependent availability deserves a direct calibration test. It does not establish the net VOR
bias from that mechanism: rate normalization, rate/game dependence, and the curve's anchor also affect
the result. The exact-rank means are sparse and are not automatically better forecasts.

The next useful experiment is to test whether draft-time information can distinguish future starting QBs
from backups at the same rookie rank, and whether the wide availability pool is calibrated by rank. Evaluate
starter probability and production conditional on playing separately, retaining busts and using class
holdouts. Do not assign these winners full seasons retroactively or add a blanket rushing-QB premium based
on their names. These remain dated research findings, not a validated change to player or dollar valuation.
