# QB annual event-probability study

Run `./rookie_event_study.sh [output-directory]`, defaulting to `reports/fuad/event-study`.
Allow about two minutes, depending on compilation and machine speed. This follows the
[conditional-rate study](RATE_STUDY.md) and does not change production valuation.

## Design fixed before the run

Two annual events test whether the model assigns useful probabilities above replacement:

- `POSITIVE_VOR`: strictly positive annual VOR under the fold's replacement replay.
- `VOR_26`: at least 26 annual VOR points, equivalent to two points above replacement per playable week
  across a full 13-week season. This is a fixed research threshold, not a tuned cutoff or a definition
  of an elite NFL quarterback. A shorter, more productive season can also exceed it.

Zero-game and below-replacement seasons count as failures. These are unconditional event probabilities,
not scoring-rate probabilities conditional on playing. For changing weekly replacement thresholds,
positive annual VOR means positive payoff against at least one threshold in the standardized replay;
it does not mean exceeding every week's replacement or actually being started by a fantasy manager.

CURRENT and nested PARTIAL preserve each historical donor's rate/games pairing. The full weighted
distribution uses the same five-point rate-level uncertainty, capped games, and replacement replay as
the previous studies. Summing weights whose VOR meets the event definition produces its probability.
The event is evaluated on each outcome, **not on the mean predicted VOR**.

Nine whole-class outer holdouts reuse the availability study's 72 inner evaluations. Partial weight
selection still minimizes inner games MSE, never event error. Both models use common pool support.
The simple benchmark is the event frequency among the other classes' top-50 QBs at the same contract
year, evaluated against the same outer-excluded replacement. It uses all such training observations,
including busts, without rank exceptions or smoothing. Its count is exported. This is an intentionally
simple benchmark, not a fitted rank-aware alternative; ALL-cohort comparisons also use this top-50
benchmark, so TOP50 is the primary comparison.

`predictions.tsv` records outcomes, probabilities, benchmark probabilities/counts, selected weights and
expected VOR. `metrics.tsv` reports eligible/scored/missing counts, observed and predicted frequency,
bias, Brier score, benchmark Brier and skill, by class, cohort, rank band and contract year (plus ALL
years). Lower Brier is better. Skill is `1 - model Brier / benchmark Brier`; negative values mean losing
to the training-only benchmark. Missing benchmark support excludes a row from both scores. Zero
benchmark error makes skill undefined rather than infinite.

`calibration.tsv` groups pooled probabilities into five fixed equal-width bins: [0,.2), [.2,.4),
[.4,.6), [.6,.8), [.8,1]. Empty bins are omitted. `selection.tsv` preserves the inner evaluations.
Reliability rows are descriptive, not independent hypothesis tests. No probabilities are recalibrated
using held-out outcomes.

As before, later classes can train earlier folds. These retrospective, overlapping class holdouts have
already guided the research and are not untouched prospective validation. Event scores are annual;
they neither test joint career-tail probabilities nor establish dollar-price accuracy. The threshold
and binning are fixed for this study, not a search whose best result should be deployed.

## September 6, 2026 results

All nine outer folds completed, with unchanged nested weight selections and expected VOR matching the
availability study to report precision. All 177 top-50 QB player-seasons have common scoring support.
There are 62 positive-VOR seasons (35.0%) and 38 seasons reaching 26 VOR (21.5%).

| Event | Observed frequency | Current probability | Partial probability | Current Brier | Partial Brier | Benchmark Brier | Partial skill |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Positive VOR | 35.0% | 22.7% | 27.1% | 0.23917 | 0.22881 | 0.23726 | +3.56% |
| At least 26 VOR | 21.5% | 14.7% | 17.5% | 0.17143 | 0.16985 | 0.16904 | -0.48% |

Partial pooling improves positive-VOR Brier by 4.3% relative to current, beating current in six of nine
classes. The 26-VOR Brier improvement is only 0.9%, with wins in five classes; neither model beats the
training-frequency benchmark on that event overall. Current's benchmark skill is -0.81% for positive
VOR and -1.41% for 26 VOR. These small score differences are descriptive, not significance claims.

| Rank band | Seasons | Positive VOR: observed / current / partial | 26 VOR: observed / current / partial |
| --- | ---: | ---: | ---: |
| QB1 | 35 | 57.1% / 33.9% / 44.3% | 31.4% / 23.3% / 29.6% |
| QB2–3 | 70 | 38.6% / 26.4% / 32.6% | 24.3% / 16.9% / 21.0% |
| QB4+ | 72 | 20.8% / 13.7% / 13.4% | 13.9% / 8.3% / 8.1% |

The frequency correction comes from QB1–3. Deep prospects remain underpredicted, and partial pooling
slightly lowers both event probabilities there. Matching average frequency alone would not establish
better forecasts: QB1's 26-VOR Brier slightly worsens (0.22533 to 0.22632) despite much closer frequency.

| Contract year | Seasons | Current / partial positive-VOR skill | Current / partial 26-VOR skill |
| --- | ---: | ---: | ---: |
| 1 | 48 | +9.4% / +10.4% | +6.7% / +4.7% |
| 2 | 41 | +4.4% / +5.8% | -1.0% / -2.7% |
| 3 | 34 | -0.9% / +11.9% | -0.05% / +5.9% |
| 4 | 29 | -17.3% / -17.4% | -10.2% / -12.4% |
| 5 | 25 | -3.6% / +4.6% | -1.8% / +3.7% |

Neither model is uniformly weak or strong across contract years. Year four is particularly poor against
the benchmark for both events. Later-year samples are smaller and contain only older draft classes;
this does not isolate an aging effect from class composition.

Reliability bins also warn against a blanket upward adjustment. Partial positive-VOR forecasts below
20% average 10.3% but occur 24.6% of the time (57 seasons). In the 40–60% bin they average 46.3% and
occur 45.7% of the time (35 seasons). For 26 VOR, partial forecasts below 20% average 10.8% versus
17.0% observed (112 seasons), while the 40–60% bin averages 44.0% with only one success in six seasons.
The latter is too sparse to justify a new exception. Pooled underprediction is not permission to raise
every probability.

## Decision and next step

Keep production unchanged. Partial pooling has evidence of better annual positive-value calibration,
but not a convincing upper-tail event advantage over a simple training-frequency forecast. This is
consistent with the earlier mixed VOR results; it does not explain all large career misses.

Next test a **training-only probability calibration benchmark**, using inner class holdouts to choose
shrinkage toward the contract-year event frequency. Evaluate it on outer classes against both raw
probabilities and the frequency benchmark. Predefine the candidate grid, preserve zero-game outcomes,
and do not introduce QB4+ or year-four exceptions based on these results. An event-only correction
would remain a diagnostic: changing player values would additionally require a coherent joint outcome
distribution, improved VOR/order metrics, and evaluation beyond these repeatedly inspected classes.

These are dated research snapshots; figure-marker integration remains deferred.
