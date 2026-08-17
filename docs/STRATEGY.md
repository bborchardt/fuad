# Strategy documents

A strategy document decides what to do at an auction. It is kept out of the repository — `strategy/` is
gitignored — because it is a plan against other people who share this league, but the rule it follows is
tracked, and so is the check that enforces it.

**A plan reasons from model outputs only.** The reports under `reports/<year>` are the entire permitted
input. Nothing behind them — not a rule, not a constant, not a raw statistic — may enter a plan's
reasoning.

## Why

Two failures, and a plan that breaks the rule usually has both.

**Figures drift.** A plan is written against one board and the model then moves. Relevelling the points
curve on history moved every quarterback price by up to half, and a plan written the week before still
read as current: prices that were badly out of date looked exactly like prices that were not. Partial
drift is worse than total drift, because a document that is wrong in one section and right in the next
gives no sign of which is which.

**Reasoning double-counts.** The board has already priced everything the model knows. Arguing that the
2026 tight end premium makes the position worth spending on is arguing a point `PTS` has already
capitalised, and which the flex allocation has capitalised a second time by giving tight end three starting
spots. Reasoning from the ~80% spend rate about money left over is the same error: prices already sum to
that pot. Each of these is a real premium paid twice.

The rule also has a useful consequence. If a plan needs a figure the board does not carry, that is a
missing report column, not a licence to reach behind. The gap gets fixed once, in the model, where it can
be tested — rather than in each plan, where it cannot.

## The contract

Every strategy document:

- lives in `strategy/`, with a four digit year in its filename, which is how the check finds its reports
- declares the model it was written against: `<!-- model: 86277a2 -->`
- marks each table with the report it came from: `<!-- source: salaries -->`
- names no model internal, input file, or source path

A marked table is keyed by its **first column**, which must name a column of that report. Every other
heading that matches a report column is verified. Headings that match nothing are the plan's own
commentary and are left alone, so a table can carry a note, a running total, or a combination the board
does not price:

```
<!-- source: salaries -->

| PLAYER | PRICE | PTS | BYE | note |
| --- | --- | --- | --- | --- |
| Kyler Murray | 21 | 174 | 8 | unrestricted, nobody can match |
```

`PLAYER`, `PRICE`, `PTS` and `BYE` are checked against `reports/2026/salaries.tsv`; `note` is not.

## What the board carries

The columns exist so that a plan never has to reach behind them. Where an earlier plan went to the model
for something, that something is now a column:

| Wanted | Column | Instead of |
| --- | --- | --- |
| how a set of players covers a season | `BYE` on `salaries` | the bye table inside the model |
| how wide outcomes run | `PTSLOW` / `PTSHIGH` | the raw multipliers in the points curve |
| how many players a team must buy | `MINSIGN` / `MAXSIGN` / `ROOKIES` on `teams` | the 23/30 roster bylaw |
| what a team can spend | `FREECAP`, `EXPOSURE`, `EXP/CAP` on `teams` | the cap and the spend rate |
| what a player adds to **my** lineup | `ADDEXP` / `ADDHIND` on `roster_<id>` | a lineup worked out by hand |
| whether a third at a position is worth it | `ADD1`-`ADD4` on `roster_depth_<id>` | the same, but harder |
| a position I cannot field at all | `NEEDS` on `teams` | remembering to buy a kicker |
| how long to sign someone for | `DYNRANK` beside `RANK` | the dynasty ranking, read separately |
| which price gaps are real | `TIER` | reading a $2 gap as a ranking |

`PTSLOW` and `PTSHIGH` carry a caveat worth restating, because the whole point of the boundary is not to
assume things the model does not know: **the range is the position's, scaled to the player.** Two players
at one position have the same proportional spread. It says nothing about which of them is the safer pick,
and a plan that treats it as though it does has smuggled in a belief the model does not hold.

If a plan needs something not in this table, that is a column the board is missing. Add it to the model,
where it can be tested, rather than working it out in the plan, where it cannot.

**Read `TIER` before reading `PRICE`.** The board quotes dollars off levels that are good to about ten
points, so within a tier the ordering is noise and a plan that ranks players by price inside one has
invented a distinction. Herbert, Mahomes, Lawrence and Stafford are all QB tier 4 in 2026, priced $38 to
$32; nothing in the model says any of them is better than another. That is the tier where the price
column is at its most misleading and the bye and availability columns at their most useful.

## The roster reports

`./generate_report.sh -t roster -f 0001` writes two files. They answer the question the auction board
cannot: the board prices a player against a league-wide replacement, which is nobody's actual alternative,
and a team holding one quarterback is not choosing between the same things as a team holding four.

**`roster_<id>.tsv`** — every available player by what he adds to *this* team's starting lineup, in points.

**`roster_depth_<id>.tsv`** — what the 1st, 2nd, 3rd and 4th best available at each position would add,
taken in turn.

Two columns, because the lineup can be set two ways and neither is true:

- `ADDEXP` — starters chosen on preseason ranks, then whatever the season gives. Nobody knows in advance.
- `ADDHIND` — starters chosen knowing how the season turned out. Nobody is still guessing by week four.

**Both of them can see who is playing.** What a manager cannot know is how good a player will turn out; he
can always see who is hurt, and nobody starts a man who is not on the field. So availability binds both
readings and only form is bracketed between them. The bracket is narrower than it used to be for that
reason, and the width it lost was never real: it came of pretending a lineup would keep starting a player
who had been out since October.

The split is readable in itself: **`ADDEXP` is what covering byes and absences is worth, `ADDHIND − ADDEXP`
is what optionality is worth.** A spare a lineup would never reach for on preseason ranks, and who is never
needed to cover anybody, shows nothing in the first column and everything in the second.

Both are **points, never dollars** — the most this team would rationally pay, against `PRICE` for what he
will cost. Comparing them is the reader's job, and the reason to run this at all.

Two things not to misread:

**The per-player marginals do not add up.** Each is measured against the roster as it stands, so they are
all the value of being the *first* signing at that position. Brett's board shows a first quarterback worth
210 and a second worth 154; signing both is not worth 364. That is what `roster_depth` is for — the same
board puts the third quarterback at 59 and the fourth at 15.

Depth is worth more than it used to read, and the reason is that a lost season is now weeks a player is
absent rather than a year of him playing badly. A backup behind a starter who misses six games covers six
weeks; the old shape left the starter nominally in the lineup all year at a reduced rate, so the backup
covered nothing and `ADDEXP` gave depth no credit for injury at all. Brett's third quarterback went from 24
points to 59 on that change alone.

**A team is evaluated, never optimised.** Nothing here recommends a roster or solves for one under the cap.
It says what a roster scores; which players to buy stays a judgement, made against prices the model is
least confident about at exactly the top of the board where the money is.

### The kicker, and why `NEEDS` exists

A kicker scores nothing in this model. The nflverse statistics carry no kicking, so no kicker can be
levelled, every one of them prices at the minimum bid, and none adds anything to any lineup. A team holding
no kicker would therefore never see the position surface on any report — while still being unable to field
a legal lineup.

That is the one place the board would have forced a plan to bring knowledge from outside it, and the old
plan did exactly that, in a hand-written aside: *"Kicker is a checklist item, not a strategy. Just do not
forget to draft one."*

`NEEDS` on `teams` closes it. It reports each position where a team holds fewer players than the lineup
requires, as `PK:1`. Saying a roster is short at a position needs no curve for that position — which is the
same move the rest of that report makes: state what is true, and decline to price what cannot be priced.
In 2026 it reads `PK:1` for six of the ten teams and is empty for the rest, which is the whole of the
league's roster-legality problem.

The header of each roster report names the same gap from the other side: `15 of 16 signed in the lineup,
1 unpriced (PK Evan McPherson)`. His salary and his roster spot are counted everywhere — free cap,
`COMMITTED`, `SIGNED`, `SLOTS`, `MINSIGN` — and only the lineup cannot use him. Dropping him changes no
figure, since a player worth nothing is never selected and could only fill a slot that would stand empty
anyway, but the two counts differ and should be seen to.

## Running the check

```
./check_strategy.sh strategy/2026-draft-plan.md
```

It reports every disagreement at once, with line numbers, and exits non-zero on any. Three kinds:

| | |
| --- | --- |
| Provenance | a cited report was generated by a different model than the document declares |
| Figures | a cited number is not what the board says |
| Boundary | the document names a model internal, an input file, or a source path |

A report generated from uncommitted changes under `src` is stamped `-dirty`, and a plan cannot be checked
against one: the sha does not then describe what actually ran. Commit the model, regenerate, and re-check.

## The manifest

`generate_report.sh` writes `reports/<year>/MANIFEST`, one line per report, recording the model that
produced it:

```
salaries 86277a2 2026-08-15T16:20:31Z
teams 86277a2 2026-08-15T16:20:31Z
```

Reports are not committed, so without this nothing records which model a given board came from. Only the
reports actually written are stamped, and a run that fails partway still stamps the ones that succeeded —
which is what `-t all` does for 2023, whose schedule cannot be generated.

## When the model moves

The plan does not silently follow it. Regenerate the reports, run the check, and work through what it
reports — a price that moved by half is a conclusion to revisit, not a number to paste in. Then update the
`<!-- model: -->` marker, which is the assertion that you have done so.
