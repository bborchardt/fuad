# research

Rookie valuation experiments, kept apart from the model that prices the draft.

Everything here asks whether the production rookie board could be better. Nothing here has yet answered
yes: **no adjustment measured in this directory has earned a production change**, and the
[roadmap](TODO.md) says for each one what was found and what it would still have to show. The separation is
the point — a study that has not earned its way into the model should not be able to change what the model
does, and should not have to be deleted to prove it.

```
./research/run.sh help                         # every study and record command
./research/run.sh <study> [<output-directory>] # default output is reports/fuad/<study>
./mvnw -f research/pom.xml verify              # the research build and its tests
```

The studies are `backtest`, `qb-diagnostics`, `availability-study`, `rate-study`, `event-study`,
`event-calibration`, `rank-frequency`, `chronology-audit`, `chronological-study`, `prospective-capture`
and `capture-audit`. Each writes its own report directory under `reports/`, which is not committed, and
each has a document under [docs/rookies/](docs/rookies) recording what the run found on the day it ran.

## The boundary

`research/pom.xml` is a separate build with its own tests, and the production build does not know it
exists: `./mvnw verify` at the repository root compiles and tests production alone, and the production jar
carries none of this. The dependency runs the other way — the research build compiles `../src/main/java`
itself, so a study measures the model that is actually checked out rather than a copy of it that has
drifted. That direction is what makes the separation useful and also what makes it fragile, so
`ResearchLayoutSpec` holds the two builds to the same Groovy, Spock and plugin versions, and CI runs both.

Production keeps the rookie board itself — `RookieValuation`, `RookieSalary`, `RookieOutcomes` and the
figures printer. What moved here is only the machinery for asking how well that board has done.

## Snapshots

A forecast is worth nothing as evidence if it can be revised after its outcomes arrive, and a capture
under `reports/` is ignored by Git and lives on one machine. `research/snapshots/` is where a forecast
becomes a record: committed, reviewed and distributed like anything else in the tree.

```
./research/run.sh prospective-capture                            # a local capture under reports/
./research/run.sh snapshot-export <capture> <manifest-sha256> <new-snapshot-directory>
./research/run.sh snapshot-verify <snapshot-directory> [<local-capture-directory>]
./research/run.sh snapshot-capture <new-capture> <new-snapshot-directory>
```

The export leaves `inputs.zip` behind and records its SHA-256 instead. Nothing verifiable is lost: the
capture manifest already hashes every input file individually, so the committed record still identifies the
inputs exactly without carrying a second copy of the tree — 144KB rather than 3.8MB. A snapshot whose
committed and omitted files do not together account for every artifact the manifest names is refused
rather than read as complete.

**What a snapshot is not.** It is not an independent trusted timestamp: a commit date is written by
whoever makes the commit, and Git gives distribution and tamper-evidence within a shared history, not
proof that a forecast existed on the day it claims. Draft-time eligibility stays `UNVERIFIED` in the
manifest for the same reason. The existing record,
[2026-09-06-qb-events](snapshots/2026-09-06-qb-events), was captured before the announced NFL kickoff and
says so in its own manifest; that is a check of the schedule, not a certification.

## Reading the documents

The result tables under `docs/rookies/` are dated snapshots of a run, deliberately outside the
`<!-- figures: -->` automation that holds production documentation to regenerated figures. A research
document is a record of what one run found on one day, not a claim about what the model does now, and
holding it to today's figures would quietly rewrite the history the series exists to keep. Their links are
checked by `./check_docs.sh` like every other document in the tree.
