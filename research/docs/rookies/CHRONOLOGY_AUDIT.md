# Chronological evaluation readiness audit

This follows the [rank-frequency benchmark](RANK_FREQUENCY.md). It audits whether the archived data
can support an information-limited evaluation; it does not run a new valuation experiment.

Run `./research/run.sh chronology-audit [output-directory]` (normally seconds after compilation), defaulting
to `reports/fuad/chronology-audit`. `coverage.tsv` counts top-50 QB donors by forecast year and contract
year, the latest eligible outcome season, available evaluation outcomes, and rows that would leak if
training were restricted only by draft class. Zero-game seasons count as observations.

## September 6, 2026 findings

### Season boundaries are implementable, but draft-class filtering is insufficient

Each backtest observation has an unambiguous outcome season:
`draftClass + contractYear - 1`. For a preseason cutoff Y, training must satisfy `outcomeSeason < Y`.
A 2017 rookie's fifth season is a 2021 outcome, not information available before 2020 simply because
his draft class is older. The new audit helper enforces this strict boundary and has a direct test.

| Preseason cutoff | Year 1 donors | Year 2 donors | Year 3 donors | Year 4 donors | Year 5 donors |
| --- | ---: | ---: | ---: | ---: | ---: |
| 2020 | 14 | 10 | 4 | 0 | 0 |
| 2022 | 25 | 19 | 14 | 10 | 4 |
| 2025 | 41 | 34 | 29 | 25 | 19 |

These are training player-seasons at each contract year, not independent five-year careers. At the
2020 cutoff, filtering only to earlier draft classes would incorrectly admit 14 year-four and 14
year-five rows. No correctly bounded year-four or year-five donors exist then. By 2022, year-five
training contains only four top-50 QBs from one class. Missing horizons must remain unsupported rather
than being filled from later observations. Evaluation outcomes are also right-censored: the 2025
class currently has seven first-year top-50 QB outcomes but no later years.

`RealisedSeasons.byRank` already accepts an explicit list of seasons. A chronological replacement fit
can therefore be restricted to seasons before the cutoff. The existing `RookieBacktest.replacementFor`
does **not** do that: it excludes target player names but defaults to all 2017–2025 seasons. It must not
be reused unchanged for an information-limited forecast.

### Ranking capture dates are not certified

The [shared data notes](../../../docs/DATA.md#rankings) identify the rankings as manual downloads. The CSVs do not
carry capture timestamps. Repository history gives these first-add dates for each season's rookie and
redraft ranking paths:

| Ranking season | First added to this repository |
| --- | --- |
| 2017 | 2018-08-27 |
| 2018 | 2018-08-27 |
| 2019 | 2019-08-14 |
| 2020 | 2021-09-06 |
| 2021 | 2021-09-06 |
| 2022 | 2022-09-04 |
| 2023 | 2024-01-05 |
| 2024 | 2024-08-25 |
| 2025 | 2026-04-12 |

These dates come from `git log --diff-filter=A --date=short` over the two ranking paths per season.
They are repository evidence, **not download dates**. A late commit does not prove a ranking used
hindsight; an early commit does not prove it was available at FUAD's earlier rookie draft. Some files
also have later edits (for example 2019 and 2024), so first-add history does not certify the current
bytes. A genuine date-specific replay must select and verify the snapshot at the intended cutoff,
including the target class's predictor ranks, rather than trusting a year in a directory name.

### Revised statistics and league rules are separate limitations

The committed raw player-stat extracts were introduced in 2026 (2017's path first appears in commit
`e9b3805`, dated 2026-08-15). The refresh code downloads the current nflverse release and overwrites
the season extract without a source-vintage manifest. Restricting seasons removes future games but
does not reproduce the historical vintage before subsequent corrections or renamed players.

The research currently scores every season with `League.FUAD.scoring = ScoringRules.FUAD_2026` and
fits replacement with `requirements('2026')`. This is intentional standardization in the existing
studies, not proof that those rules were known historically. Historical lineup records exist, but the
league changed scoring and lineup rules; a genuine historical-policy replay is a different target
from forecasting under fixed 2026 rules. Keeping 2026 rules is defensible only when explicitly labeled
a standardized counterfactual.

## Recommended next implementation

Proceed first with a **season-truncated, standardized sensitivity study**, not a certified as-of replay:

1. Freeze CURRENT, FREQUENCY and NESTED, the two event definitions, rank kernel and weight grid.
2. Use only outcome seasons before each outer preseason cutoff, and restrict replacement inputs to
   the same boundary. Keep 2026 scoring/lineup rules explicitly as a fixed counterfactual.
3. For inner historical forecast cutoffs, use only earlier training seasons. Score only inner outcomes
   completed before the outer cutoff; future contract years cannot select the outer weight.
4. Export cutoff, training/evaluation season bounds, donor counts and missing support. Report the
   reduced and changing horizon samples, not the prior study's 177 rows as if all were still usable.
5. Test that perturbing later-season observations cannot change an earlier forecast or its selection.
   Allow future outcomes only in final outer evaluation, never training or selection.

This can assess sensitivity to chronological outcome availability with the current archive. It cannot
settle historical ranking vintage or prospective performance. To claim a real as-of backtest, first
obtain dated ranking provenance and define the historical rules policy; do not silently substitute a
modern snapshot. Future forecasts should record immutable input hashes, capture time, code revision,
cutoff and rules before their outcomes arrive.

No production values or historical files were changed by this audit.
