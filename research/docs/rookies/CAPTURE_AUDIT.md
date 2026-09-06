# First prospective capture: cohort and preservation audit

This audits capture `2026-eab11ead-55a6-4fc8-800a-a351b6842d0e`, whose manifest hash was recorded
before the audit in [VALIDATION_AND_CAPTURE.md](VALIDATION_AND_CAPTURE.md). It does not rerun forecasts,
change the captured inputs, remove players, or score any outcome.

## Reproducible checks

`./research/run.sh capture-audit audit <capture-directory> <expected-manifest-sha256> <new-output-directory>`
first checks the manifest against the separately recorded SHA-256, then verifies its artifacts. It reads
the ranking and MFL player data **inside inputs.zip**, verifies each against its captured input hash,
and checks that forecast names/ranks and the five-year/two-event/three-model grid match the ranking.

The cohort check performs exact case-insensitive first/last-name reversal, restricted to QB position.
A unique MFL match with `draft_year = 2026` is `MATCHED_ENTRY_YEAR`; other years, ambiguous names and
missing names receive separate statuses. This is deliberately conservative: missing does not mean
ineligible, a failed name match does not become a zero-game season, and current roster status or future
performance cannot determine inclusion. The audit checks the archive's evidence, not MFL's accuracy.

Audit outputs are `cohort.tsv` and `audit.json`, in a new-only directory separate from the capture.
The original manifest SHA-256 is
`b9bb6c67cdba48c2e43759f9d7e88bc68ae2791ad8bc10d810b5c3389db78fe6`.

## September 6, 2026 findings

All eight top-50 QBs match 2026 entries in the captured MFL player database. Official team sources
independently confirm that they entered the NFL in 2026, without requiring that they made a roster,
stayed healthy or subsequently played:

| Captured rookie QB rank | Player | Entry evidence |
| --- | --- | --- |
| 1 | Fernando Mendoza | [Raiders draft announcement](https://www.raiders.com/news/fernando-mendoza-no-1-overall-pick-raiders-quarterback-2026-nfl-draft-indiana-football) |
| 2 | Ty Simpson | [Rams draft announcement](https://www.therams.com/news/ty-simpson-nfl-draft-quarterback-alabama) |
| 3 | Carson Beck | [Cardinals draft record](https://www.azcardinals.com/draft/2026/round-3-pick-65) |
| 4 | Cole Payton | [Eagles draft announcement](https://www.philadelphiaeagles.com/news/eagles-draft-cole-payton-north-dakota-state-no-178-overall) |
| 5 | Cade Klubnik | [Jets draft-class record](https://www.newyorkjets.com/news/jets-draft-class-calls-2026) |
| 6 | Drew Allar | [Steelers draft announcement](https://www.steelers.com/news/steelers-select-allar-in-third-round) |
| 7 | Taylen Green | [Browns draft announcement](https://www.clevelandbrowns.com/news/browns-select-qb-taylen-green-with-the-no-182-pick-in-the-2026-nfl-draft) |
| 8 | Haynes King | [Panthers undrafted signing announcement](https://www.panthers.com/news/panthers-sign-10-undrafted-rookies-haynes-king) |

Of all 21 captured names, 15 match a 2026 MFL entry and six have no exact match: Arch Manning,
Malachi Nelson, Jake Retzlaff, Tommy Castellanos, Kyron Drones and Joey Aguilar. All six are outside
the primary top-50 cohort and already lack forecast support for every year/model/event in the capture.
Do not infer all six are ineligible from the missing matches. There is positive evidence of a cohort
problem for at least one: [Texas lists Arch Manning on its 2026 college roster](https://texaslonghorns.com/sports/football/roster).
His presence in the archived rookie ranking is not proof that he entered the NFL that year.

The primary cohort can remain the original eight QBs, with no membership edits or outcome-dependent
exclusions. Preserve all 21 original names for traceability. An independently verified all-ranks entrant
cohort would need further evidence; this audit does not claim one. It also does not repair historical
cohort membership in the earlier backtests.

### Snapshot timing

The captured 2026 rookie-ranking hash is
`70a447830437b18e3c2b9c6f24e71ff688e39d6e6b6c0006ca14f8dcad3265fb`.
It matches the file at commit `d2b924a`, dated September 3, 2026, 20:39:27 -05:00. This ties the
captured bytes to a repository version before capture and before the announced regular-season kickoff.
It is not a FantasyPros download timestamp, independent timestamp, or proof the snapshot preceded
FUAD's rookie draft. Draft-time eligibility remains unverified.

## Private preservation

`./research/run.sh capture-audit backup <capture-directory> <expected-manifest-sha256> <new-directory>`
copies the manifest and its listed artifacts without overwrite, then verifies the copy against the
same pinned manifest hash. The original is untouched. Interrupted/incomplete copies are left for
inspection and are not evidence of successful backup until verification passes.

A verified local copy was created under
`reports/fuad/capture-backups/2026-eab11ead-55a6-4fc8-800a-a351b6842d0e`.
This is same-device redundancy, **not** disaster recovery, an independent timestamp, or a remote
backup. Both directories are git-ignored. The archive contains private league resources and must not
be uploaded to the code repository or another destination without approval. An off-device private
destination is still needed; no external transfer has been made.

## Next safeguards

1. Preserve the pinned capture off-device at the user's selected private destination.
2. Implement a future scorer that verifies the original manifest, preserves the audited top-50 cohort,
   uses captured replacement, and rejects incomplete outcome seasons rather than calling them zeros.
3. Keep any unresolved all-ranks membership review separate from the frozen primary analysis.
4. Continue matched-sample chronology comparisons separately; do not alter the prospective forecasts
   in response to later research results.
