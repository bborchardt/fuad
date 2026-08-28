# Data

What the model is built from, where it comes from, and every place a source says something other than what
it appears to. Both leagues are levelled from the same statistics and ordered by the same rankings, so what
follows is about those; each league's own record is its own document.

See [fuad/DATA.md](fuad/DATA.md) for the auction's league data, which is where most of the repairs and
irrecoverable snapshots are, and [greenfield/DATA.md](greenfield/DATA.md) for the keeper league's.

## Player statistics

Under `src/main/resources/ff/nflverse/data/<year>`, 2017 onwards: `player_stats.tsv`, the raw weekly
statistics every expected point is now built from.

| | |
| --- | --- |
| Source | nflverse-data release `stats_player`, one file per season, 1999 onwards under one schema |
| Collected by | `./season_history_refresh.sh <year>`, alongside that season's league record |
| Kept | 16 scoring columns, weeks 1-14 of the regular season, at QB, RB, WR, TE and PK |
| Size | about 2.3MB for nine seasons, from 8MB and 150 columns a season published |

Statistics rather than fantasy points, because the league has scored four different ways since 2017 and a
season scored under its own rules cannot be compared with one scored under another's. `ScoringRules` takes
the rules as a parameter, so any season can be restated under the rules being priced. Points computed by
anyone else are points under somebody else's rules.

Two things to know about the extract:

- nflverse writes a player's **current** name into every season he ever played, so Robby Anderson is
  "Robbie Chosen" as far back as 2017. That is what the alias map is for; see [Player names](#player-names).
- `position` comes from the roster and is occasionally not the one he plays. Travis Hunter is a `CB` in
  2025, so the position filter drops his receiving, and his ranked season comes back as a zero.

The transaction log matters more than it looks. Rosters only show where players ended up, so a move that a
later move undid leaves no trace in them at all. Both expansion drafts are in the log as commissioner
roster loads, which is how the 2023 one was reconstructed.

`salary_adjustments.json` is the only record of a rule that appears in no settings file. Every adjustment
this league has ever made is a cut penalty, and each carries the contract it was charged for in its
description — `Treylon Burks (2yrs@1)`, sometimes several to a row. That is what turned the cut penalty
from an unverifiable bylaw into a rule checked against 384 charges. See
[LEAGUE_RULES.md](fuad/LEAGUE_RULES.md#confirmed-against-every-penalty-ever-charged).


## Team defences

Under `src/main/resources/ff/nflverse/data/<year>`: `team_stats.tsv`, one row per team per week.

| | |
| --- | --- |
| Source | nflverse-data release `stats_team`, plus nfldata `games.csv` for the scores |
| Collected by | `./season_history_refresh.sh <year>`, alongside the player statistics |
| Kept | the counts a defence is scored on, weeks 1-14, all 32 teams |
| Size | 144KB for nine seasons, against 2.2MB for the players |

**Points allowed comes from a different file and is folded in at collection.** The team release carries
every defensive count a league scores and no scores at all, and points allowed is the largest term in
Yahoo's defence scoring by a wide margin. `games.csv` is one file for every season rather than one per
season, joined on the game id the team release already carries.

Three things need care, and none of them says so in its name.

`def_tds` **excludes fumble returns.** Of 220 team weeks with a fumble recovery touchdown, 197 carry
`def_tds` at zero, so interception returns are counted in one column and fumble returns in the other. They
are added, and it is not double counting.

`fumble_recovery_tds` **does not say whose fumble it was.** A team recovering its own in the end zone has
scored on offence, not defence. Where it recovered no opponent fumble that week it cannot have been the
defence, and `fumble_recovery_opp` is kept so the loader can tell — that rules out 11 of the 220. The
remaining 102 weeks carry both kinds and no column here separates them.

**The two sources disagree about relocated franchises, in opposite directions.** The team release writes
today's abbreviation into every season a franchise ever played, so the Raiders are `LV` in 2017; the scores
write the abbreviation of the day, `OAK`. Joined as written, thirteen team weeks of 2017 have no score. It
is the same forward-dating nflverse does to [player names](#player-names), done to teams, and both sides go
through `NflTeams`.

The fetch **refuses a team week it cannot score** rather than dropping it, which is what surfaced the
relocation problem. Dropped silently, it would have read as a defence that played fewer games than it did.


## Rankings

Under `src/main/resources/ff/fantasypros/data/<year>`: `dynasty_rankings_ppr.csv`,
`redraft_rankings_half_ppr.csv`, `rookie_rankings_ppr.csv`. Through 2025 these are tab separated with
unquoted values; from 2026 they are comma separated with quoted values. `FantasyProsLoader` detects which
from the header line and reads both.

**These are downloaded by hand, and nothing fetches them.** There was briefly an API client; it is gone.
The public key is limited to the first ten players of any ranking set, which is useless, and an automated
path that silently returns a partial set is worse than no automated path at all.

**Fantasypros does not put kickers in a superflex ranking**, which is the format this league needs, so from
2026 they come as their own single position export: `kicker_rankings.csv`. Having only one position in it,
that file has no `POS` column — the position is the file and the rank is the row —
and `FantasyProsLoader.loadRedraftRankedPlayers` merges it into the redraft set so that everything
downstream sees one ranking. Through 2025 the superflex exports still carried kickers and no extra file is
needed.

It matters because a set that quietly loses a position is invisible until somebody needs it: 2026's went
unnoticed until six teams needed a kicker and the board had none to show them. `RankingCoverageSpec`
asserts the merged set carries all five positions, that kicker ranks run from 1 without gaps or repeats,
and that merged kickers do not land among the best players overall.

### Tier rows

The site's export separates tiers with an empty row, and **its rank column skips those rows rather than
counting them**. The loader used to renumber around them, which collided two players onto a single rank in
every 2026 set — A.J. Brown with Saquon Barkley, Brian Thomas Jr. with Sam LaPorta. Harmless in the main
files, where the positional rank is read from `POS`, and not harmless in the kicker export, where the rank
*is* the positional rank. Ranks are now taken as written. No tab separated export has tier rows at all, so
nothing before 2026 is affected either way.


## Player names

`LoadUtils.NAME_ALIASES` maps nicknames that share no prefix with the given name, which no amount of fuzzy
matching pairs up. It is applied to **every** source, because it is not only fantasypros that uses the
nickname: MFL called Marquise Brown "Hollywood" in 2024 and "Marquise" in every other year, so aliasing one
side only would fix one season and break another.

Current entries: Hollywood Brown to Marquise Brown, Dee Eskridge to D'Wayne Eskridge, Mike Badgley to
Michael Badgley, Robbie Chosen to Robby Anderson.

The last is nflverse's doing rather than a nickname: it backdates a player's current name over his whole
career, and Robby Anderson changed his in 2022. Without the alias his 2018, 2019 and 2021 seasons look like
ranked players who never took the field, which is a mistake with teeth — an unmatched name and a season
lost to injury are indistinguishable, and both score zero. Everything else is matched on an exact name
first, then on progressively shorter prefixes of first and last name, with each statistics line claimed at
most once so the specific matches are made before the loose ones can go wrong. That is what tells Gabe from
Gabriel and Kenny from Kenneth.


## Collecting it

```
./data_refresh.sh <year>                 # this year's league and rankings data
./season_history_refresh.sh <year> ...   # statistics and league records for completed seasons
```

Both scripts serve both leagues: the statistics they collect level every curve here, and the rankings order
it. What each league's own record needs on top, and what cannot be refetched at all, is in that league's
own data document.
