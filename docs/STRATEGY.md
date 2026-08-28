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
spots. Reasoning from the spend rate about money left over is the same error: prices already sum to
that pot. Each of these is a real premium paid twice.

The rule also has a useful consequence. If a plan needs a figure the board does not carry, that is a
missing report column, not a licence to reach behind. The gap gets fixed once, in the model, where it can
be tested — rather than in each plan, where it cannot.

## The same rule, turned at the source

**No comment states a number something else computes.** A javadoc figure is read while the code is being
edited, by the one person most able to invalidate it, and nothing checks it — which is the arrangement this
whole file exists to argue against, sitting inside the model rather than in a plan. It rotted exactly as
predicted: two comments in one file gave different values for the same anchor, a smoothing radius was
justified by a correlation two to four times weaker than the record supports, and a retired argument about
the best running back survived in three places after the documentation had dropped it.

Four kinds of figure, and only the last belongs in a comment:

| | Where it goes |
| --- | --- |
| What the model computes now — a level, a share, a depth, an anchor | `docs/figures/fuad/<year>`, cited by name and field |
| What the committed data says — a correlation, a retention rate, a spend | a spec that recomputes it, as `AuctionValuationSpec` does |
| What a superseded implementation used to produce | the commit message that replaced it |
| A league rule, or the definition of a constant | the comment, because the number *is* the specification |

The first is a hard rule: if a comment wants a generated figure, it names the file and the column. If no
such figure exists, that is a missing column in `positions.tsv` or `curve.tsv` — the same move a plan makes
when it needs something the board does not carry, for the same reason. `ANCHOR`, `GAMESCORR`, `RATECV` and
`GAMESCV` are all columns that exist because a comment wanted to quote them.

The third is worth stating because the alternative looks tempting. A figure measured against a model that no
longer exists cannot be regenerated and cannot be checked, so a comment carrying one is unfalsifiable
forever. The commit that made the change is where it belongs: dated, immutable, and attached to the diff
that made it true.

**With one exception, and it is the reason the rule is stated in terms of comments.** A rejected alternative
is worth describing where the choice is described, or somebody retries it — and the documentation already
has a convention for that: a block marked **Superseded** and stamped `<!-- model: sha -->`, saying plainly
that the figures in it are not reproducible. That is a different thing from a comment quoting an old number
as though it were current, which is what the rule is aimed at, and the difference is whether a reader can
tell. A retraction carrying no figures at all needs no stamp, having nothing to be wrong about.

So: a superseded figure in running prose or a javadoc comment goes to the commit message. A superseded
figure a reader needs in order not to repeat the mistake stays, marked and stamped, and never anywhere else.

## The contract

Every strategy document:

- lives in `strategy/`, with a four digit year in its filename, which is how the check finds its reports
- declares the model it was written against: `<!-- model: 86277a2 -->`
- marks each table with the report it came from: `<!-- source: salaries -->`
- uses no technical word the board does not itself use

**That last one is asked the other way round from how it looks.** There is no list of internals a plan is
forbidden to name. The check reads the vocabulary out of **every report generated for that season** — their
column names and every value in them, so players, teams, owners and bands all count — and flags anything
shaped like a constant or a class name that appears in none of them.

Every report rather than only the ones a plan marks with a source, because a plan legitimately names a team
or a player in prose that cites no table, and holding it to only the tables it cites would reject that. The
cost is that a word appearing on some other report the plan never opens is admitted too, which widens the
boundary by whatever `reports/<year>` happens to hold.

The reason is that the list came first and it rotted. It named thirty fewer things than the model had, so a
plan could reason from the lineup evaluator, the cut penalty, the kicker depth and four input files and come
back clean, because all of those were written after the list was. **A list of what a plan may not say has to
be told about every constant the model gains. A board does not.** An internal added tomorrow is covered
tomorrow, by not being on a report.

Position-and-rank shorthand — `QB2`, `WR38` — is allowed even though no report carries it literally, since
the letters have to be a position the board itself uses. Ordinary football abbreviations the board has no
column for are a short list in `StrategyCheck.PROSE`, and that list is meant to stay short: if a plan wants
a word the board does not have, the answer is usually a missing column.

A marked table is keyed by its **first column**, which must name a column of that report — or, where one
column does not pick out a row, by the columns a `key=` names: `<!-- source: outlook_13 key=PICK+POS -->`.
The outlook is the case: it carries a row per position under each pick, so citing a pick alone names
several rows and is refused rather than answered from one of them. This is the same grammar the figures
markers use; see [check_docs.sh](../check_docs.sh).

Every other
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
| whether a season is rate or availability | `PPG` / `G` on `salaries` | a total that hides which |
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

**Read `TIER` before reading `PRICE`.** The board quotes dollars off levels that are good to seven points
or so, so within a tier the ordering is noise and a plan that ranks players by price inside one has
invented a distinction. QB10, QB11 and QB14 are all tier 5 in 2026:

<!-- figures: fuad/curve across=POS field=PTS -->

| Rank | QB |
| --- | --- |
| 10 | 204.3 |
| 11 | 204.0 |
| 14 | 195.6 |

<!-- figures: fuad/curve across=POS field=SE -->

| Rank | QB |
| --- | --- |
| 10 | 7.4 |
| 11 | 7.3 |
| 14 | 8.8 |

<!-- figures: fuad/curve across=POS field=TIER -->

| Rank | QB |
| --- | --- |
| 10 | 4 |
| 11 | 4 |
| 14 | 5 |

Seven points of spread on estimates carrying seven to nine. They reach the board a few dollars apart all
the same, and nothing in the model says any of them is better than another. That is where the price column
is at its most misleading, and the bye and `AVAIL` are what is left to choose on.

**Named by rank, not by player, and that is the point.** Three quarterbacks hold those ranks this season and
different ones will hold them next, while the claim being made is about the curve rather than about any of
them — it is the same reason the model is indexed by rank in the first place. It is also what lets the
figures above be checked: a rank is on a committed figure and a player is on a board that is regenerated and
never kept.

**`G` is not one of them.** It is a per-rank figure and it moves a long way across a position:

<!-- figures: fuad/curve across=POS field=G -->

| Rank | QB |
| --- | --- |
| 1 | 11.46 |
| 10 | 11.43 |
| 11 | 11.42 |
| 14 | 11.24 |
| 34 | 6.90 |

Eleven and a half games at the top against under seven by rank 34 — but availability is smoothed over ten
ranks either side, so within a single tier it is nearly flat, as the three middle rows show. It tells you
what kind of player a rank is, not which of two neighbours to take. Ordering a tier by `G` is the same
mistake as ordering it by price.

## The roster reports

`./fuad_report.sh -t roster -f 0001` writes two files. They answer the question the auction board
cannot: the board prices a player against a league-wide replacement, which is nobody's actual alternative,
and a team holding one quarterback is not choosing between the same things as a team holding four.

**`roster_<id>.tsv`** — every available player by what he adds to *this* team's starting lineup, in points.

**`roster_depth_<id>.tsv`** — what the 1st, 2nd, 3rd and 4th best available at each position would add,
taken in turn. Every position the lineup fields, kicker included; it used to be a list of four written into
the printer, which quietly stopped being every position when kickers were levelled.

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

**The per-player marginals do not add up.** Each is measured against the roster as it stands, so every one
of them is the value of being the *first* signing at that position. Brett's `roster_0001` prices Lamar
Jackson at 195 and Joe Burrow at 182, and signing both is not worth 377: whichever of them arrives second
is worth 157, because the first already covered the weeks he would have covered. That is what
`roster_depth` is for — it walks the same position down in turn, 195, 157, 57, 17.

Every roster figure in this section is a snapshot rather than a checked one, this row and the kicker row
below alike: unlike everything on the auction board, roster reports are per-team and are not among the
generated figures `./check_docs.sh` verifies. Read them for the shape — steep, then flat — rather than to
the point.

**And they do go stale, which is the argument for the rest of this file rather than against it.** Giving
kicker a single priced depth moved its row from 100, 26, 8, 2 to the figures below — the shape it makes the
point with intact, every number different, and nothing but a reader to notice. The auction board's figures
cannot drift that way, because a marked table fails the check in the commit that moves it.

**The kicker row has a shape of its own, and it is the one worth reading closely.** Brett's is 106, 24, 5, 0.
The first is worth as much as a second running back because he fills a slot that is otherwise standing
empty — a lineup short a kicker forfeits those points every week, and six of the ten teams go into 2026
short one. The drop to 24 is the whole point of the row: only one kicker starts, so the second is worth the
two or three weeks the first is away and nothing else, and the fourth is worth nothing at all. A kicker
priced far under what the board says he is worth invites buying two. The row is what says once is enough.

Depth is worth more than it used to read, and the reason is that a lost season is now weeks a player is
absent rather than a year of him playing badly. A backup behind a starter who misses six games covers six
weeks; the old shape left the starter nominally in the lineup all year at a reduced rate, so the backup
covered nothing and `ADDEXP` gave depth no credit for injury at all.

<!-- model: b5e0874 -->

> **Superseded**: measured against a model that no longer exists, where Brett's third quarterback came out
> at 24 points against the 57 above.

**A team is evaluated, never optimised.** Nothing here recommends a roster or solves for one under the cap.
It says what a roster scores; which players to buy stays a judgement, made against prices the model is
least confident about at exactly the top of the board where the money is.

### The kicker, and why `NEEDS` exists

A kicker used to score nothing in this model. The statistics this project kept carried no kicking, so no
kicker could be levelled, every one priced at the minimum bid, and none added anything to any lineup. A team
holding no kicker would never have seen the position surface on any report — while still being unable to
field a legal lineup.

That is the one place the board would have forced a plan to bring knowledge from outside it, and the old
plan did exactly that, in a hand-written aside: *"Kicker is a checklist item, not a strategy. Just do not
forget to draft one."*

**Kickers are levelled now**, and the aside turns out to have been wrong on the substance as well as out of
bounds. See [PROJECTION.md](fuad/PROJECTION.md#kickers) — the position is the one place on the board where what
the model thinks a player is worth and what this league pays for him differ by a factor rather than by a
margin. `NEEDS` stays all the same: a team short at a position should be told so by the report.

`NEEDS` on `teams` closes it. It reports each position where a team holds fewer players than the lineup
requires, as `PK:1`. Saying a roster is short at a position needs no curve for that position — which is the
same move the rest of that report makes: state what is true, and decline to price what cannot be priced.
In 2026 it reads `PK:1` for six of the ten teams and is empty for the rest, which is the whole of the
league's roster-legality problem.

The header of each roster report names the same gap from the other side, counting how many of a team's
signed players the lineup can actually use and naming the ones it cannot — a kicker, or anyone ranked past
the depth the curve still prices. Their salaries and their roster spots are counted everywhere else — free
cap, `COMMITTED`, `SIGNED`, `SLOTS`, `MINSIGN` — and only the lineup cannot use them. Dropping them would
change no figure, since a player worth nothing is never selected and could only fill a slot that would
stand empty anyway, but the two counts differ and should be seen to.

## Running the check

```
./check_strategy.sh strategy/2026-draft-plan.md
```

It reports every disagreement at once, with line numbers, and exits non-zero on any. Three kinds:

| | |
| --- | --- |
| Provenance | a cited report was generated by a different model than the document declares |
| Figures | a cited number is not what the board says |
| Boundary | the document uses a technical word no cited report carries, an input file, or a source path |

A report generated from uncommitted changes under `src/main` is stamped `-dirty`, and a plan cannot be checked
against one: the sha does not then describe what actually ran. Commit the model, regenerate, and re-check.

## The manifest

`fuad_report.sh` writes `reports/<year>/MANIFEST`, one line per report, recording the model that
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
