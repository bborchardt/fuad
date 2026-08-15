# Data

What lives under `src/main/resources`, where it comes from, and every place the league's history makes a
season's data differ from the others. The point of this file is that none of it has to be inferred from the
raw JSON.

## Files per season

Under `src/main/resources/ff/mfl/data/<year>`:

| File | Point in time | Refetchable |
| --- | --- | --- |
| `rosters.json` | Before that season's auction | **No** |
| `rosters_post_draft.json` | Week 1, after the auction, before in season pickups | Yes |
| `rosters_end_of_year.json` | The close of the season | Yes |
| `transactions.json` | Every move made that season | Yes |
| `rules.json` | That season's scoring rules | Yes |
| `player_scores.json` | What players scored each week, under that season's rules | Yes |
| `players.json`, `owners.json`, `draft.json` | When last refreshed | Yes |
| `league.json` | When last refreshed | **No** (see below) |

MFL keeps one live copy of each season and moves it forward in place, so `rosters.json` is irrecoverable:
by the time a season is over, the site no longer knows what its rosters looked like before the auction.

`league.json` is worse, because refetching it appears to work. It holds the starting requirements and the
salary cap, and the site does not keep those per season — refetching 2021's today reports the $300 cap and
superflex lineup the league only adopted in 2022, in place of the $250 and single quarterback the
contemporaneous file records. The committed file is the record. See
[LEAGUE_RULES.md](LEAGUE_RULES.md#provenance).

There used to be a third, `projected_scores.json`, which had to be captured before a season started because
the site rewrites its projections as the season goes — summed over 2025 they correlate 0.95 with what
actually happened, which no forecast does. Nothing is priced off projections any more, so it has been
deleted rather than left to rot. See [PROJECTION.md](PROJECTION.md).

Everything else is a genuine record of a finished season and can be refetched at any time. `rules.json` in
particular is period correct, verified against the league's own recorded scores.

Under `src/main/resources/ff/nflverse/data/<year>`, 2017 onwards: `player_stats.tsv`, the raw weekly
statistics every expected point is now built from.

| | |
| --- | --- |
| Source | nflverse-data release `stats_player`, one file per season, 1999 onwards under one schema |
| Collected by | `./season_history_refresh.sh <year>`, alongside that season's league record |
| Kept | 17 scoring columns, weeks 1-14 of the regular season, at QB, RB, WR and TE |
| Size | about 1.9MB for nine seasons, from 8MB and 150 columns a season published |

Statistics rather than fantasy points, because the league has scored four different ways since 2017 and a
season scored under its own rules cannot be compared with one scored under another's. `ScoringRules` takes
the rules as a parameter, so any season can be restated under the rules being priced. Points computed by
anyone else are points under somebody else's rules.

Two things to know about the extract:

- nflverse writes a player's **current** name into every season he ever played, so Robby Anderson is
  "Robbie Chosen" as far back as 2017. That is what the alias map is for; see [Player names](#player-names).
- `position` comes from the roster and is occasionally not the one he plays. Travis Hunter is a `CB` in
  2025, so the QB/RB/WR/TE filter drops his receiving, and his ranked season comes back as a zero.

The transaction log matters more than it looks. Rosters only show where players ended up, so a move that a
later move undid leaves no trace in them at all. Both expansion drafts are in the log as commissioner
roster loads, which is how the 2023 one was reconstructed.

Under `src/main/resources/ff/fantasypros/data/<year>`: `dynasty_rankings_ppr.csv`,
`redraft_rankings_half_ppr.csv`, `rookie_rankings_ppr.csv`. Through 2025 these are tab separated with
unquoted values; from 2026 they are comma separated with quoted values. `FantasyProsLoader` detects which
from the header line and reads both.

## The rollover rule

Each season's `rosters.json` is the prior season's `rosters_end_of_year.json` rolled over:

- a contract with a year or more left carries over with `contractYear` decremented and `salary` unchanged
- a contract reaching `contractYear` 0 keeps the player but has its `salary` wiped to `0.01`, which is what
  the league site stores in place of a zero it will not accept
- a player already at `contractYear` 0, meaning signed in season for the remainder of that year, is dropped

Two things legitimately break the correspondence, and neither is an error: players released or retired
between the end of the season and the snapshot, and franchises being renumbered (see below).

`MflRosterContinuitySpec` asserts this across every season. It permits players to go missing and franchises
to move, and fails on a contract that appears from nowhere or rolls over to the wrong number or salary.

Salaries wiped to `0.01` in `rosters.json` are exactly the free agents that season's auction priced, and
what they went for is in the same season's `rosters_post_draft.json`. That join yields **609 signings**
across 2017-2025, median $12.

## Franchises

Franchise ids are slots, not identities. Owners have changed slots, and slots have been reused by different
owners, so joining seasons on franchise id will silently merge unrelated teams.

| id | 2017 | 2018 | 2019 | 2020 | 2021 | 2022 | 2023 | 2024 | 2025 | 2026 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 0001 | Brett | Brett | Brett | Brett | Brett | Brett | Brett | Brett | Brett | Brett |
| 0002 | Mark | Jon | Jon | Jon | Pete | Pete | Pete | Pete | Pete | Pete |
| 0003 | Nick | Nick | Nick | Nick | Troy | Troy | Troy | Troy | Troy | Martin |
| 0004 | Jeff | Jeff | Jeff | Jeff | Jeff | Jeff | Jeff | Jeff | Jeff | Jeff |
| 0005 | Paul | Paul | Paul | Adam | Brian | Brian | Brian | Brian | Brian | Brian |
| 0006 | Mike | Mike | Mike | Mike | Mike | Mike | Mike | Mike | Mike | Mike |
| 0007 | Marcus | Marcus | Marcus | Nate | Nate | Nate | Nate | Nate | Quinlan | Quinlan |
| 0008 | Scott | Chris | Chris | Chris | Chris | Chris | Chris | Chris | Chris | Chris |
| 0009 | Brian | Brian | Brian | Brian | — | — | Franz | Franz | Franz | Franz |
| 0010 | Troy | Troy | Troy | Troy | — | — | — | Matthew | Matthew | Matthew |

League size: **10** teams 2017-2020, **8** in 2021-2022, **9** in 2023, **10** from 2024.

### 2021, contraction from 10 to 8

Nick (0003) and Adam (0005) left. Rather than their slots going empty, the two remaining owners in the
highest slots moved down into them, carrying their rosters intact:

- Brian moved 0009 to 0005, bringing 26 players
- Troy moved 0010 to 0003, bringing 25 players

The departing owners' rosters were released, 47 contracts in all. So a player who looks traded between 2020
and 2021 was almost certainly not: **51 of the roster moves that year are the renumbering**, not
transactions. Note that 0009 and 0010 later come back under different owners, which is where an id based
join goes wrong: Brian's 0009 and Franz's 0009 are different teams.

### 2023, expansion to 9

Franz was added in slot 0009, filled by an expansion draft off other rosters plus extra rookie picks. That
year's `rosters.json` was taken **before** the expansion draft, so franchise 0009 sat on an empty roster
while its 25 players still counted against the teams that lost them. That has been repaired; see below.

The schedule generator requires equal size divisions, so `-t schedule` cannot run for 2023 and fails with
`Schedule generation requires equal-size divisions, found sizes 4 and 5`. Under `-t all` the other four
reports are written first and only the schedule fails.

### 2024, expansion to 10

Matthew was added in slot 0010, again by expansion draft. That year's `rosters.json` was taken **after**
the expansion draft: 0010 already holds 26 players, three from each existing franchise and two from 0003.
Those 26 show as franchise moves against 2023's end of year rosters.

This is the convention both expansion seasons now follow.

## Repairs

### 2022 pre draft rosters, rebuilt

`2022/rosters.json` as originally collected was not a pre draft snapshot at all. Its player set is identical
to the 2022 **end of year** rosters, so it was pulled after that season rather than before it. Every 2022
auction signing appeared as a pre existing contract, no contract showed as expiring, and the season
contributed nothing to the signings dataset.

Since the real snapshot cannot be refetched, the file was derived from `2021/rosters_end_of_year.json` by
`MflPreDraftRosterBuilder`, applying the rollover rule above:

```
./season_history_refresh.sh 2021
java -cp "target/classes:$(cat target/classpath.txt)" ff.run.PreDraftRebuild 2022
```

It now holds 245 contracts, 108 of them wiped, and 2022 contributes 64 signings.

**What the derived file cannot tell you**: it is exactly who was under contract in December 2021, on the
team holding that contract. Offseason releases, retirements and trades between the end of 2021 and the 2022
auction are not in it. Every other season's `rosters.json` loses a handful of players that way, 1 to 19 per
year, so 2022 likely overstates rosters by roughly that much and may attribute a few players to the team
that held them in December rather than the one that took them into the auction.

### 2023 expansion draft, applied

`2023/rosters.json` was a real snapshot, just taken a few weeks before 2024's equivalent was: 0009 was
still empty and its 25 players still sat on the eight rosters that were about to lose them. Season over
season that made the same league event visible in one year and invisible in the next.

`MflExpansionDraftBuilder` moved the selections onto 0009, carrying each contract across untouched:

```
./season_history_refresh.sh 2023
java -cp "target/classes:$(cat target/classpath.txt)" ff.run.ExpansionDraftRebuild 2023 0009
```

Roster sizes went from 27-31 per team with an empty 0009, to 23-28 with 25 on 0009, which is the shape 2024
has. The player set and every contract are unchanged; only which franchise holds them moved. Six of the 25
carry wiped contracts, inherited by 0009 as its own expiring players to re-sign or lose in the auction, and
four of those six it did re-sign.

**The selections come from the transaction log, not the week 1 rosters.** Only 22 of the 25 were on 0009 at
week 1: it drafted Jalen Tolbert, Taysom Hill and Brandon McManus on August 31 and released all three in the
following week, so rosters alone undercount the expansion draft by three and no comparison of snapshots can
recover them.

One difference this does not address: Jameson Williams was traded from 0003 to 0004 the day before the
expansion draft, and the pre draft snapshot predates it. The repair moves expansion selections only, so
that trade is still missing from 2023's pre draft rosters.

## Player names

`LoadUtils.NAME_ALIASES` maps nicknames that share no prefix with the given name, which no amount of fuzzy
matching pairs up. It is applied to **every** source, because it is not only fantasypros that uses the
nickname: MFL called Marquise Brown "Hollywood" in 2024 and "Marquise" in every other year, so aliasing one
side only would fix one season and break another.

Current entries: Hollywood Brown to Marquise Brown, Dee Eskridge to D'Wayne Eskridge, Robbie Chosen to
Robby Anderson.

The last is nflverse's doing rather than a nickname: it backdates a player's current name over his whole
career, and Robby Anderson changed his in 2022. Without the alias his 2018, 2019 and 2021 seasons look like
ranked players who never took the field, which is a mistake with teeth — an unmatched name and a season
lost to injury are indistinguishable, and both score zero. Everything else is matched on an exact name
first, then on progressively shorter prefixes of first and last name, with each statistics line claimed at
most once so the specific matches are made before the loose ones can go wrong. That is what tells Gabe from
Gabriel and Kenny from Kenneth.

## Refreshing

```
./data_refresh.sh <year>                 # this year's MFL and fantasypros data, plus last season's record
./season_history_refresh.sh <year> ...   # snapshots and transactions for completed seasons
```

`season_history_refresh.sh` never touches `rosters.json` or `league.json`, so it cannot overwrite an
irrecoverable pre draft snapshot or a season's starting requirements with today's state, and it is safe to
rerun on old years. It refuses to write rosters for a season whose contracts are still wiped, since that
means the auction has not been entered yet.

**2026 is at that point now**: its auction has not been run, so it has no snapshots. Collect them with
`./season_history_refresh.sh 2026` once the season's contracts are in, which will also let 2026 signings
join the dataset.

Seasons before 2020 redirect to `http`, which `HttpURLConnection` will not follow from an `https` request
and which yields an empty body rather than an error. `FetchUtils.fetchText` follows redirects itself and
keeps them on `https`.
