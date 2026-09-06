# Nested QB event-probability calibration

Run `./rookie_event_calibration.sh [output-directory]`, defaulting to
`reports/fuad/event-calibration`. This follows the [event study](EVENT_STUDY.md).
Allow about three minutes, depending on compilation and machine speed.
Production valuations and outcome distributions remain unchanged.

## Fixed design

This experiment shrinks CURRENT event probabilities toward the training-only, same-contract-year
top-50 QB event frequency. It retains the previous definitions: strictly positive annual VOR and at
least 26 annual VOR. Zero-game seasons are failures, not missing observations.

The candidate weights are 0, 0.25, 0.5, 0.75 and 1:
`calibrated probability = (1 - weight) * current probability + weight * training frequency`.
There are no rank-specific or contract-year-specific weight exceptions. One weight is shared across
both events, preserving the ordering P(VOR >= 26) <= P(VOR > 0).

For each of nine outer class holdouts, the other eight classes serve as inner holdouts. Each inner
forecast excludes both the outer and inner class from curves, outcome pools, frequency donors, and
replacement fitting. Inner event labels also use this two-class-excluded replacement. The outer
forecast refits those components excluding only the outer class. Symmetric exclusion sets share cached
replacement fits, but never held-out outcomes.

Selection minimizes the equal-class mean inner Brier score, averaging both events and all observed
contract years among supported top-50 QBs within a class. Thus a larger class does not receive more
selection weight; every supported player-season contributes two event rows. Exact ties and missing
usable evidence choose zero shrinkage. This deliberately differs from selecting weights separately
for each event, which could produce inconsistent event ordering.

The candidates reported on common outer support are CURRENT (weight zero), FREQUENCY (weight one),
and CALIBRATED (inner-selected weight). A missing raw forecast or benchmark leaves all three missing,
even if the frequency alone exists. There is no separate partial-pooling candidate: correctly
calibrating its previously nested availability selection would require another nesting layer. Starting
with CURRENT isolates whether calibration adds value without reusing an inner class's outcomes to
select the forecast being calibrated.

`predictions.tsv` contains each event outcome, raw/calibrated/frequency probabilities, benchmark donor
counts, and selected weight. `selection.tsv` contains all 360 inner-class/candidate evaluations, with
eligible/scored **event-row** counts and mean Brier. The metrics and fixed reliability bins follow the
event study, by class, rank band and contract year. Reported pooled Brier weights observations equally;
the inner selection objective weights classes equally. These are different summaries by design.

This is a probability benchmark, not a replacement valuation distribution. Two ordered event
probabilities alone do not determine expected VOR, career dependence, or dollar prices. No new VOR
or career ranking is claimed. Later classes still train earlier holdouts, and these classes have already
guided the research. Nested selection prevents direct parameter leakage, not research-level overfitting
or the need for future validation. Research figure-marker integration remains deferred.

## September 6, 2026 results

All nine outer and 72 inner class evaluations completed. Inner selection chooses 25% shrinkage for
2017, 2018, 2020, 2021, 2022 and 2023, and 50% for 2019, 2024 and 2025. The raw probabilities,
event labels and training-frequency benchmarks reproduce the preceding event study to report precision.
All 177 top-50 player-seasons have common support, yielding 354 event rows per model.

| Event | Observed | Current probability | Calibrated probability | Frequency probability | Current Brier | Calibrated Brier | Frequency Brier | Calibrated skill |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Positive VOR | 35.0% | 22.7% | 25.9% | 33.5% | 0.23917 | 0.23527 | 0.23726 | +0.84% |
| At least 26 VOR | 21.5% | 14.7% | 16.7% | 21.5% | 0.17143 | 0.16981 | 0.16904 | -0.45% |

The calibrated forecast improves Brier by 1.6% for positive VOR and 0.9% for 26 VOR relative to raw
CURRENT. It beats CURRENT in only four of nine outer classes for each event. Positive-VOR skill against
the frequency benchmark is slightly positive; 26-VOR skill remains negative. The near-perfect pooled
frequency calibration for the latter event is not evidence that it identifies individual successes.

| Rank band | Seasons | Positive VOR: observed / current / calibrated | 26 VOR: observed / current / calibrated |
| --- | ---: | ---: | ---: |
| QB1 | 35 | 57.1% / 33.9% / 33.7% | 31.4% / 23.3% / 22.7% |
| QB2–3 | 70 | 38.6% / 26.4% / 28.6% | 24.3% / 16.9% / 18.3% |
| QB4+ | 72 | 20.8% / 13.7% / 19.6% | 13.9% / 8.3% / 12.3% |

The rank-agnostic frequency target helps deep prospects but does not correct QB1. Positive-VOR Brier
for QB4+ improves from 0.18254 to 0.17698; 26-VOR Brier improves from 0.12807 to 0.12520. At QB1,
positive-VOR Brier is essentially unchanged (0.31816 to 0.31817) and 26-VOR Brier worsens (0.22533
to 0.22669). Moving every rank toward the same contract-year frequency trades away rank distinctions.

The earlier partial-pooling study had a stronger positive-VOR Brier of 0.22881, while its 26-VOR Brier
of 0.16985 was almost identical to this study's 0.16981. That is descriptive context, not an outer-test
license to choose one method for each event or rank. Partial pooling improved mainly QB1–3; frequency
shrinkage improves mainly deeper prospects. Neither experiment has established a general valuation fix.

## Decision and next question

Keep production unchanged. Simple calibration reduces some underprediction but does not solve the
upper-tail event problem or demonstrate robust class-level gains. These small, repeatedly inspected
retrospective differences do not justify translating corrected probabilities into new prices.

The next bounded benchmark is a **training-only rank-aware event frequency**, partially pooled toward
the contract-year frequency, with its pooling strength selected inside class holdouts. Compare it with
both the rank-agnostic frequency and CURRENT before adding player-specific predictors. Predefine the
pooling candidates and retain one shared rule across events; do not add separate QB1/QB4+ exceptions
from these tables. If a rank-aware frequency benchmark performs as well as the model, the research
priority becomes whether draft-time information can add discrimination beyond rookie rank and year.
Any production proposal still needs coherent outcome forecasts and validation outside these classes.
