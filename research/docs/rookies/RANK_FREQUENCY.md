# Rank-aware QB event-frequency benchmark

Run `./research/run.sh rank-frequency [output-directory]`, defaulting to `reports/fuad/rank-frequency`.
Allow about three minutes, depending on compilation and machine speed. This follows the
[event-calibration study](EVENT_CALIBRATION.md). Production valuation remains unchanged.

## Fixed design

The benchmark predicts the same two annual events: positive VOR and at least 26 VOR points. It borrows
only from other classes' top-50 QBs at the same contract year. Every donor retains its binary event
outcome, including zero-game failures. Donor weight is `exp(-abs(donor QB rank - target QB rank))`,
normalized to sum to one. The bandwidth is fixed, not searched against outer outcomes. The probabilities
use observed event frequencies, not the rate curve or its uncertainty distribution. Event definitions
still depend on the fitted replacement replay; this is not a replacement-free benchmark.

Four forecasts share the same supported evaluation sample:

- CURRENT: the existing joint-outcome model's event probability.
- FREQUENCY: the rank-agnostic contract-year event frequency.
- LOCAL: the rank-weighted event frequency.
- NESTED: `(1 - weight) * FREQUENCY + weight * LOCAL`.

The weight grid is 0, 0.25, 0.5, 0.75 and 1. For each outer class, eight inner class holdouts choose
one weight across both events, all ranks and all observed contract years. Selection minimizes the
equal-class mean Brier score among supported top-50 QBs. Exact ties and no usable evidence choose
zero local weight. There are no rank-band or contract-year exceptions. Sharing the same donors and
weight preserves P(VOR >= 26) <= P(VOR > 0), but does not impose monotonicity across rookie ranks.

Both inner and outer classes leave the inner donor sets, rate curves, outcome pools and replacement
fitting. Outer forecasts refit excluding only the outer class. The CURRENT calculation and cached
replacement exclusion logic are shared with the calibration study. No held-out event label selects
its own forecast. A missing CURRENT forecast, global frequency or local frequency excludes the row
equally from all four models. ALL-cohort outputs also use top-50 donors; TOP50 is the primary sample.

`predictions.tsv` exports probabilities, selected local weight, global donor count, local probability
and local effective sample size `(sum weights)^2 / sum(weights^2)`. Effective sample size describes
weight concentration, not independent evidence across overlapping careers or folds. `selection.tsv`
contains the 360 inner-class/candidate evaluations and event-row counts. `metrics.tsv` and
`calibration.tsv` reuse the previous study's class/rank/year Brier metrics and fixed reliability bins.
Positive skill means beating FREQUENCY. CURRENT has no local weight, so its strength field is blank.

The same retrospective limitations apply: later classes can train earlier ones, these classes informed
the research, and small rank/year groups are not untouched tests. Two ordered event probabilities do
not determine career VOR, dollar price or the outcome distribution. This benchmark makes no new
valuation claim. Research snapshots remain outside figure-marker automation while the series evolves.

## September 6, 2026 results

All nine outer folds and 72 inner evaluations completed, with all 177 top-50 QB player-seasons on
common support. CURRENT and FREQUENCY reproduce the previous study to report precision. The selected
local weight is 50% for 2017–2021 and 2024, and 75% for 2022, 2023 and 2025. Local donor effective
sample size ranges from 4.90 to 24.59, with a median of 15.14 across the top-50 annual forecasts.

| Event | Observed frequency | CURRENT Brier | FREQUENCY Brier | LOCAL Brier | NESTED Brier | NESTED probability | NESTED skill vs frequency |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Positive VOR | 35.0% | 0.23917 | 0.23726 | 0.22387 | 0.22840 | 33.7% | +3.73% |
| At least 26 VOR | 21.5% | 0.17143 | 0.16904 | 0.16893 | 0.16823 | 21.4% | +0.48% |

NESTED improves pooled Brier over CURRENT by 4.5% for positive VOR and 1.9% for 26 VOR. It beats
FREQUENCY in seven of nine classes for positive VOR and five for 26 VOR. Against CURRENT, however,
it wins in only four and three classes respectively: the pooled advantage is not broad class-level
dominance. These scores do not establish statistically reliable gains.

LOCAL's lower positive-VOR Brier is reported, not substituted for the inner-selected rule after seeing
outer results. The higher-threshold event shows a much smaller benefit from rank: LOCAL's skill versus
FREQUENCY is just +0.07%, versus +0.48% for NESTED. Near-matching pooled event frequency alone does not
establish accurate identification of individual successes.

| Rank band | Seasons | Positive VOR: observed / NESTED | 26 VOR: observed / NESTED | NESTED positive-VOR skill | NESTED 26-VOR skill |
| --- | ---: | ---: | ---: | ---: | ---: |
| QB1 | 35 | 57.1% / 42.8% | 31.4% / 26.3% | +5.80% | -0.87% |
| QB2–3 | 70 | 38.6% / 35.1% | 24.3% / 21.9% | +1.03% | -0.65% |
| QB4+ | 72 | 20.8% / 27.8% | 13.9% / 18.6% | +5.47% | +3.27% |

The nearly correct pooled frequencies conceal offsetting rank-level errors: underprediction at QB1
and overprediction at QB4+. Brier can improve despite a biased group average because it also depends
on how probabilities distinguish individual outcomes. Do not treat group frequency matching as the
only objective or add hand-tuned rank exceptions from these tables.

## Decision and next step

Keep production unchanged. A simple rank/year frequency benchmark can match or beat the current
model on these annual events, so additional outcome-model complexity has not established an event
forecast advantage here. This does not show that frequency alone values full careers better: it has
no expected-VOR magnitude forecast or career dependence model.

The next priority is a **chronological evaluation of a frozen shortlist**, not another exception tuned
to these class holdouts. Carry CURRENT, FREQUENCY and NESTED forward with the existing event thresholds,
rank kernel and weight grid. At each forecast cutoff, restrict both training outcomes and replacement
inputs to information available then; do not merely exclude later draft classes while retaining their
future realized seasons. Audit historical data availability before calling this a rolling forecast.

This would test whether the modest benchmark gains survive a more realistic information boundary.
The existing classes are already research-exposed, so even rolling evaluation is not untouched
prospective validation. Preserve future forecasts before their outcomes arrive. Only after that should
new draft-time predictors or a coherent replacement valuation distribution be proposed.
