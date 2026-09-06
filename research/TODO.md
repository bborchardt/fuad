# Rookie valuation research roadmap

## Rookie research priorities: agreed next steps

No valuation adjustment has yet earned a production recommendation. Prioritize stronger validation
over further small calibration variants, in this order:

1. **Chronology-limited validation:** freeze CURRENT, FREQUENCY and NESTED rank frequency, event
   definitions, rank kernel and weight grid. Bound outcomes and replacement inputs by each forecast
   cutoff, including inner selection labels. Report missing horizons and class-level as well as pooled
   scores. Label this a standardized sensitivity study, not a certified historical replay, until ranking
   timestamps and source vintages are verified.
2. **Prospective capture:** preserve forecasts before their outcomes arrive, with input hashes, capture
   time, forecast cutoff, scoring/lineup rules, code revision and dirty-tree provenance. Keep captures
   immutable and distinguish a genuine preseason capture from one made after play has begun. Do not
   let incomplete current-season outcomes enter training or imply that hashes prove historical availability.
3. **Full valuation validation:** turn any surviving probability improvement into a coherent joint
   games/scoring distribution, then test career VOR, ordering and the complete production model, not
   just its rookie-only component.
4. **Other positions:** diagnose RB, WR and TE separately; the QB investigation is not a completed
   review of the entire rookie valuation model.
5. **Additional draft-time information:** test predictors such as NFL draft capital and rushing
   production only after establishing historically available inputs and strong rank/year benchmarks.
6. **Decision and price consequences:** evaluate player selection and relative value at actual rookie
   contract costs; improved event probabilities alone do not establish dollar-price accuracy.

Track the validation and capture work here before changing production assumptions. Existing
[chronology audit](docs/rookies/CHRONOLOGY_AUDIT.md) and [rank-frequency findings](docs/rookies/RANK_FREQUENCY.md)
define the current information limits and frozen benchmark candidates.

**Validation implementation completed:** the [chronological sensitivity](docs/rookies/VALIDATION_AND_CAPTURE.md)
scores 81/177 eligible top-50 QB player-seasons. CURRENT beats frequency by 7.66% positive-VOR Brier
skill and 17.60% for 26 VOR; NESTED gains only 0.80%/0.16%. The earlier pooled benchmark preference
does not survive this test. Different samples and replacement targets prevent a simple causal comparison.
Keep production unchanged; next compare matched samples and isolate target-definition effects.

**Capture workflow implemented:** `./research/run.sh prospective-capture` preserves 2026 QB event forecasts,
cutoff-limited selection, replacement, source/resources, hashes and dirty-tree provenance in new-only
local capture directories. Verification checks artifact integrity; this is not external tamper-proof
storage. Captures are git-ignored, so `./research/run.sh snapshot-export` reduces one to the part Git
should hold and commits it under `snapshots/`. Next implement scoring against the original capture and
fixed replacement, without selecting a favorable rerun or refitting event labels.

**First local capture completed:** `2026-eab11ead-55a6-4fc8-800a-a351b6842d0e`, September 6 at
16:36:19 UTC, with verified artifact hashes. It precedes the announced NFL regular-season kickoff but
does not certify FUAD draft-time eligibility. Eight archived top-50 QBs have 39/40 supported annual
forecasts per model/event. Audit archived cohort membership and snapshot timing with outcome-blind
rules, then preserve a separate backup; neither remote backup nor independent timestamping is done.

**Capture cohort audit completed:** [CAPTURE_AUDIT.md](docs/rookies/CAPTURE_AUDIT.md) verifies all eight
primary QBs against captured MFL entry years and official team entry announcements. Of 21 archived
names, 15 match MFL and six remain unmatched; the latter are outside top 50 and have no supported
forecasts. Preserve the original cohort and flag the unresolved all-ranks membership, rather than
silently treating unmatched names as busts. Captured ranking bytes match the September 3 commit;
FUAD draft-time availability remains unverified. A pinned-hash same-device copy is verified, but an
off-device private destination and independent timestamp are still outstanding. Next implement scoring
against the unchanged primary capture, with complete-season checks and captured replacement.

**Committed forecast record:** [snapshots/2026-09-06-qb-events](snapshots/2026-09-06-qb-events) holds the
capture's forecasts, replacement, working-tree patch and manifest — 144KB, with `inputs.zip` recorded by
SHA-256 rather than stored, since the manifest already hashes every input file individually.
`./research/run.sh snapshot-verify <snapshot> [<capture>]` checks the record against itself, against the
capture manifest, and against a surviving local capture. This ends the git-ignored single-machine
exposure and gives distribution and tamper-evidence within a shared history. It is **not** an independent
trusted timestamp: a commit date is written by whoever makes the commit, so draft-time eligibility remains
UNVERIFIED and an off-device private destination and independent timestamp are still outstanding.

## Rookie dynasty calibration has not earned a production change

The [rookie backtest](docs/rookies/ROOKIE_BACKTEST.md#nested-comparison-september-5-2026) compared nine outer class
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

The [QB diagnostic](docs/rookies/QB_DIAGNOSTICS.md) narrows this question: four breakout careers account for
72.4% of five-year squared error, while the other 21 QBs are overpredicted by 16.90 VOR points on average.
The shortfall persists under replacement thresholds 20% lower and higher. Investigate calibration of the
probability of becoming a starter and the rank-dependent availability pool, rather than raising every QB's
value. Draft-time predictors must be evaluated across all prospects, including busts, with class holdouts.
For rookie QB1, the first-year outcome pool averages 6.57 games against 10.44 at the exact rank in training;
year two averages 8.27 against 12.38. Test whether borrowing availability across ranks is calibrated before
adding new player-specific signals; the sparse exact-rank averages are diagnostic evidence, not replacements
to install without holdout evaluation.

The [nested availability study](docs/rookies/AVAILABILITY_STUDY.md) has now tested rank-weighted and partial pooling.
On 177 top-50 QB player-seasons, partial pooling improves games RMSE from 5.28 to 4.92 and zero-game Brier
from 0.162 to 0.143, chiefly at QB1–3; QB4+ worsens. Five-year VOR RMSE improves from 145.33 to 143.07, but
MAE increases and first-/three-year RMSE worsen. Production remains unchanged. The open question is now
whether availability can be calibrated while preserving the appropriate conditional-rate distribution and
rate/games dependence, particularly among deep prospects. Improved games forecasts alone are insufficient.

The [conditional-rate study](docs/rookies/RATE_STUDY.md) finds little aggregate rate change: on 148 played
top-50 QB seasons, RMSE moves from 7.192 to 7.173 points/game and CRPS from 4.204 to 4.176. Breaking
rate/duration dependence worsens five-year VOR RMSE from 145.33 to 149.75 for current pooling and from
143.07 to 146.46 for partial pooling, despite reducing MAE. Deep QBs have the largest rate errors and
undercoverage. Next measure held-out above-replacement and high-VOR event probabilities, including busts,
before adding predictors; improved availability and conditional mean-rate bias are not enough to price
the upper tail. Preserve pairing and production behavior pending stronger evidence.

The [event-probability study](docs/rookies/EVENT_STUDY.md) finds positive VOR in 35.0% of 177 top-50 QB seasons,
against current/partial probabilities of 22.7%/27.1%. Partial improves Brier from 0.23917 to 0.22881
and beats a training-only contract-year frequency benchmark by 3.56%. For a fixed 26-VOR event,
both still lose to that benchmark (partial skill -0.48%). Deep prospects remain underpredicted, while
higher-probability bins do not support a blanket increase. Next test nested shrinkage of event
probabilities toward training-only contract-year frequencies, without rank/year exceptions. This would
be a calibration diagnostic, not a production value change without a coherent outcome distribution.

The [nested event-calibration study](docs/rookies/EVENT_CALIBRATION.md) selects 25–50% shrinkage of CURRENT
toward those frequencies. Positive-VOR Brier moves from 0.23917 to 0.23527 (benchmark skill +0.84%);
26-VOR Brier moves from 0.17143 to 0.16981 but still loses to frequency (skill -0.45%). Each event
improves in only four of nine classes. Gains favor deep QBs, while QB1 does not improve. Next compare
a training-only rank-aware frequency benchmark, with nested pooling toward contract-year frequency,
before introducing individual predictors or translating probability corrections into valuations.

The [rank-aware frequency benchmark](docs/rookies/RANK_FREQUENCY.md) selects 50–75% local rank weight.
Nested Brier is 0.22840 for positive VOR and 0.16823 for 26 VOR, versus CURRENT's 0.23917/0.17143;
skill versus plain frequency is +3.73%/+0.48%. Pooled calibration masks QB1 underprediction and QB4+
overprediction. Gains over CURRENT occur in only four/three of nine classes. Freeze CURRENT,
FREQUENCY and NESTED for a chronological evaluation next, auditing season-level outcome and replacement
availability at each cutoff rather than only excluding later draft classes. Production remains unchanged.

The [chronology audit](docs/rookies/CHRONOLOGY_AUDIT.md) confirms season-level filtering is feasible, but a
certified as-of replay is not established: rankings lack capture timestamps, some first commits are
postseason, statistics are current-vintage extracts, and research uses fixed 2026 rules. Before 2020
there are no year-four/five top-50 QB donors; before 2022 there are only four year-five donors. Next
implement an explicitly labeled season-truncated sensitivity with cutoff-limited replacement and
inner validation outcomes, preserving missing horizons. Historical snapshot provenance remains a
separate requirement for an actual information-as-known-then claim.

