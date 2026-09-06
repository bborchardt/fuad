# Chronological validation and prospective capture

This implements the first two [research priorities](../../TODO.md#rookie-research-priorities-agreed-next-steps).
It does not change production values. Both workflows freeze CURRENT (rookie-only event probabilities),
FREQUENCY and NESTED rank frequency, the 0/26 VOR event thresholds, rank-distance kernel and 0–1
quarter-step local-weight grid. These are QB event forecasts, not a snapshot of the full production
rookie board or a dollar-price forecast.

## Chronology-limited validation

Run `./research/run.sh chronological-study [output-directory]`; default output is
`reports/fuad/chronological-study`. Allow roughly two minutes including compilation.

For entry class Y, training outcomes must have `draftClass + contractYear - 1 < Y`. Replacement
is fitted only from archived redraft/stat seasons before Y, with fixed 2026 scoring and lineup rules.
No later observations are used even from earlier draft classes. The forecast method never reads the
target's outcome; a separate scoring step attaches it afterward. There is no replacement fit in 2017
and no invented probability when a contract year lacks donors or CURRENT lacks support.

For each outer cutoff, inner forecasts are made at earlier classes' own entry-year cutoffs. Only inner
outcomes completed before the outer cutoff may select its weight. Selection averages both event scores
within each class, then gives supported classes equal weight. No usable evidence selects zero local
weight. Inner labels use replacement fitted at the inner forecast cutoff, not a later replacement fit.
The three models share scoring support. Missing support remains in eligible/missing counts.

Reports include cutoff, target outcome season, latest training and replacement season, donor count,
and the latest label season used in each inner evaluation. Historical outer outcomes through 2025 are
used only for scoring; unknown future years are not padded with zeros. The class/rank/year metrics and
reliability bins retain the earlier definitions. Pooled metrics weight player-seasons, while selection
weights classes; samples differ from retrospective leave-class-out studies.

This is a **season-truncated standardized sensitivity**, not a certified as-of replay. The
[chronology audit](CHRONOLOGY_AUDIT.md) documents ranking timestamp gaps, revised statistical vintages,
fixed modern rules and sparse early horizons. Nested chronological selection does not erase the fact
that these classes have already informed the research.

### September 6, 2026 validation results

All nine class cutoffs ran. The chronological common sample contains **81 of 177** eligible top-50
QB player-seasons; 96 lack common forecast support. The 2017 and 2018 classes have no scoreable rows;
2019–2021 have 3, 10 and 16 respectively, and 2022–2025 have 16, 15, 14 and 7. Sparse early curves
and absent longer-horizon donors materially change the sample. There are 36 earlier-class/outer-cutoff
pairs and 180 candidate score rows, including unsupported early pairs.

| Event | Observed | CURRENT probability | FREQUENCY probability | NESTED probability | CURRENT Brier | FREQUENCY Brier | NESTED Brier |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Positive VOR | 24.7% | 23.3% | 34.1% | 33.9% | 0.18531 | 0.20069 | 0.19908 |
| At least 26 VOR | 11.1% | 16.9% | 26.4% | 26.3% | 0.10967 | 0.13310 | 0.13289 |

CURRENT beats the frequency baseline by 7.66% and 17.60% Brier skill, respectively. NESTED's advantage
over frequency is only 0.80% and 0.16%. Inner selection chooses no local weight except at outer cutoffs
2023 and 2024, where it chooses 25%; 2017 has no inner evidence and defaults to zero.

This reverses the pooled preference in retrospective leave-class-out testing. It reinforces the decision
not to deploy the rank-frequency adjustment. It does **not** establish that chronology alone caused the
reversal: donor support, evaluated classes/horizons, and fitted replacement thresholds all change.
The observed event rates are consequently not directly comparable with the previous 177-row study.
No claim of statistical significance or superiority for the complete production model is made.

Season-boundary checks verified every forecast's training/replacement maximum is before its cutoff,
and every inner selection label predates the outer cutoff. Unit tests additionally perturb future
outcomes and confirm earlier forecasts and selections remain unchanged. The next validation refinement
is to compare chronological and leave-class-out forecasts on matched player-seasons while separating
changes in target replacement; avoid attributing all movement to one cause.

## Prospective capture

Run `./research/run.sh prospective-capture [new-directory]`. With no directory it creates a UUID-named
directory under `reports/fuad/captures`. Existing directories, even empty ones, are refused. Allow roughly
two minutes. The workflow is explicitly restricted to the 2026 class until its rules/protocol are reviewed.

The capture makes forecasts for every archived 2026 QB over contract years 1–5, using only completed
2017–2025 outcomes and chronological inner selection. No 2026 outcomes enter fitting; prediction files
contain no actual-outcome column. Prospective evaluation should preserve the top-50 primary cohort
and all five original contract-year forecasts rather than replace them with later reruns.

Each completed directory contains:

- `predictions.tsv` and `selection.tsv`: forecasts and their selection evidence.
- `replacement.json`: the exact fixed replacement thresholds used to define future event labels.
- `inputs.zip`: actual source, tests, resources, build configuration and scripts, including untracked
  research code. This is a local reproducibility archive and includes private league resources; do not
  publish it without reviewing those contents.
- `working-tree.patch`: tracked changes relative to HEAD; untracked source is preserved in the archive.
- `manifest.json`: actual UTC start/completion times, code revision and dirty status, rules, model
  settings, input SHA-256 hashes, dependency hashes, Java version and output artifact hashes.

Inputs and repository status are checked again after prediction. Changes during capture abort completion.
The manifest is written last: a directory without a COMPLETE manifest is incomplete and must not be
evaluated. Such directories are retained for inspection, not overwritten or silently cleaned up.

Run `./research/run.sh prospective-capture --verify <directory>` to verify artifact hashes against the local
manifest. This detects artifact alteration relative to that manifest; it does not authenticate the
manifest itself. UUID paths and refusal to overwrite prevent accidental replacement through this tool,
but local files are not a tamper-proof archive or independent timestamp. For stronger preservation,
retain a separately stored manifest hash and back up the complete capture under access controls.
`reports/` is git-ignored, so committing code alone does **not** preserve these captures remotely.

### Timing eligibility

The NFL announced the first 2026 regular-season kickoff for September 9 at 8:20 p.m. ET, which is
September 10 at 00:20 UTC. This schedule was checked on September 6, 2026 against the
[NFL announcement](https://amp.nfl.com/news/seahawks-to-kick-off-2026-nfl-regular-season-on-wednesday-sept-9-in-seattle).
The manifest compares its actual completion time to that announced deadline and retains the source
and verification date. It does not backdate a late capture or claim the schedule has been reverified
on later runs. Draft-time eligibility remains unverified: a pre-regular-season capture is not necessarily
before FUAD's rookie draft, and can incorporate offseason/preseason information.

When outcomes arrive, score against the captured replacement thresholds and original predictions.
Do not refit the target definition, select another capture based on results, or overwrite the original.
The next evaluation implementation should verify the manifest and enforce this scoring protocol.

### First local capture

Capture `2026-eab11ead-55a6-4fc8-800a-a351b6842d0e` completed at **2026-09-06 16:36:19 UTC**, before
the announced regular-season kickoff. It resides under `reports/fuad/captures/` and passed artifact
hash verification. The working tree was dirty; 402 source/resource/build files were hashed and archived.
Its manifest SHA-256 is
`b9bb6c67cdba48c2e43759f9d7e88bc68ae2791ad8bc10d810b5c3389db78fe6`.

The archived ranking contains 21 QBs, producing 630 player/year/event/model rows. Of these, 300 have
common support. The top-50 cohort contains eight QBs: 234 of 240 forecast rows are supported, with the
six missing rows corresponding to Haynes King's fifth contract year across both events and all three
models. Missing forecasts remain blank, not zero. Chronological selection chooses 25% local weight.

This freezes the **archived ranking cohort**, not an independently verified list of NFL draft entrants.
Membership and rank-snapshot timing should be audited before prospective evaluation using outcome-blind
eligibility rules. Do not silently revise the captured cohort based on who later succeeds or appears in
statistics. The capture is local and git-ignored; no remote backup or independent timestamp was created.
