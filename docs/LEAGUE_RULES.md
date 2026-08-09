# League rules by season

What the league's settings actually were in each year of collected data: how many players a team starts,
how large a roster it may carry, and how players score. All three have changed, and a salary projection
built across seasons is comparing different games unless it accounts for them.

The starting requirements and the salary cap come from each season's `league.json`, the scoring from each
season's `rules.json`. Max roster size is in neither and is derived; see below.

## Summary

| Season | Teams | Cap | Starters | Superflex | Max roster | Scoring era |
| --- | --- | --- | --- | --- | --- | --- |
| 2017 | 10 | $250 | 8 | no | 25 | 6pt pass TD, 1/20 yd |
| 2018 | 10 | $250 | 8 | no | 25 | 6pt pass TD, 1/20 yd |
| 2019 | 10 | $250 | 8 | no | 25 | 6pt pass TD, 1/20 yd |
| 2020 | 10 | $250 | 8 | no | 25 | 6pt pass TD, 1/20 yd |
| 2021 | 8 | $250 | 8 | no | 30 | 3pt pass TD, 1/40 yd, -1 INT |
| 2022 | 8 | $300 | 10 | **yes** | 30 | 3pt pass TD, 1/40 yd, -1 INT |
| 2023 | 9 | $300 | 10 | yes | 30 | 4.5pt pass TD, 1/30 yd |
| 2024 | 10 | $300 | 10 | yes | 30 | 4.5pt pass TD, 1/30 yd |
| 2025 | 10 | $300 | 10 | yes | 30 | 4.5pt pass TD, 1/30 yd |
| 2026 | 10 | $300 | 10 | yes | 30 | as 2023, plus **TE premium** and extended FG tiers |

Three of these move together in a way worth noticing: the league devalued quarterback scoring in **2021**,
then added a second quarterback slot and raised the cap by 20% in **2022**, then partly restored passing
scoring in **2023**. Quarterback salaries either side of 2022 are not comparable.

## Starting requirements

| | 2017-2021 | 2022-2026 |
| --- | --- | --- |
| Total starters | 8 | 10 |
| QB | 1 | 1-2 |
| RB | 1-2 | 1-3 |
| WR | 2-4 | 2-5 |
| TE | 1-2 | 1-3 |
| PK | 1 | 1 |

Read the ranges as a fixed core plus flex. Through 2021 that is QB, RB, 2×WR, TE, PK fixed with **2 flex**
spots filled from RB/WR/TE. From 2022 the same core carries **4 flex** spots, and because the QB limit rose
to `1-2` one of them may be a second quarterback — the superflex change. The league's own description of
its intent, from the 2025 rule proposals thread: *"our fairly recent super flex addition was intended to
maintain the importance of QB."*

So the league goes from starting 10 quarterbacks league-wide (2021, 8 teams) to as many as 20 (2022+),
against a starter pool that shrank and then grew back with expansion. That is the single largest structural
change in the data.

## Roster limits

`league.json` reports `rosterSize: 80` in every season. That is not the league's limit — it is MFL's
ceiling, left at its default. The real limit is a bylaw enforced by the commissioner rather than by the
site, which is why nothing in the exports carries it.

One year is stated outright. From the commissioner's *Rosters Loaded* post of 20 August 2025:

> Contracts are due September 1 EOD. [...] You also need to get within the min/max roster sizes of 23/30.

For the other seasons the limit is derived from where rosters actually sit at week 1, which is the point
each season's roster compliance is checked. **Injured reserve does not count against it** — every season
carries IR players over the apparent limit, and excluding them makes the ceiling exact:

| Season | Week 1 roster sizes, excluding IR |
| --- | --- |
| 2017 | 20, 20, 21, 22, 23, 24, 24, 24, 24, **25** |
| 2018 | 24, 24, 24, 24, 24, **25, 25, 25, 25, 25** |
| 2019 | 22, 23, 24, 24, 24, 24, 24, **25, 25, 25** |
| 2020 | 21, 24, 24, **25, 25, 25, 25, 25, 25, 25** |
| 2021 | 26, 28, 28, 29, 29, **30, 30, 30** |
| 2022 | 26, 26, 27, 28, 29, **30, 30, 30** |
| 2023 | 27, 27, 28, 29, 29, **30, 30, 30, 30** |
| 2024 | 24, 26, 28, 28, 28, 28, 29, 29, **30, 30** |
| 2025 | 26, 28, 28, 28, 28, 29, 29, 29, 29, **30** |

No team exceeds 25 through 2020 or 30 from 2021, and in both eras teams pile up exactly on the limit —
five at 25 in 2018, six at 25 in 2020, three at 30 in 2021 and 2022, four at 30 in 2023. That clustering is
what a binding cap looks like, and 30 matches the figure the commissioner stated for 2025. The increase
coincides with the 2021 contraction from 10 teams to 8, which left the same player pool spread across
fewer rosters.

The **minimum** is on weaker ground. 23 is stated for 2025 and is not violated from 2021 on, but 2017
(20), 2020 (21) and 2019 (22) all sit below it, so either the minimum was lower before 2021 or it was not
enforced. Treat 23 as known for 2021-2026 and unknown before.

**Enforcement is soft and late.** The limit binds at the September 1 contract deadline, not continuously.
Pre-auction rosters routinely exceed it — 2022's reach 36, and 2026's currently reach 37 — and end-of-year
rosters do too, since in-season pickups push past it. Only the week 1 snapshot reflects a complied-with
roster, so `rosters_post_draft.json` is the file to check a roster-size assumption against.

## Scoring

Identical for every position in every season except where noted. Values shown are per event.

| Event | 2017-2020 | 2021-2022 | 2023-2025 | 2026 |
| --- | --- | --- | --- | --- |
| Passing TD | 6 | **3** | **4.5** | 4.5 |
| Passing yards | .05/yd (1/20) | **.025/yd (1/40)** | **1 per 30 yds** | 1 per 30 yds |
| Interception thrown | -2 | **-1** | -1 | -1 |
| Rushing TD | 6 | 6 | 6 | 6 |
| Rushing yards | .1/yd | .1/yd | .1/yd | .1/yd |
| Receiving TD | 6 | 6 | 6 | 6 |
| Receiving yards | .1/yd | .1/yd | .1/yd | .1/yd |
| Reception | .5 | .5 | .5 | .5, **TE 1.0** |
| 2-point conversion | 2 | 2 | 2 | 2 |
| Fumble lost | -2 | -2 | -2 | -2 |
| Field goal | 3 / 4 / 5 | 3 / 4 / 5 | 3 / 4 / 5 | 3 / 4 / 5 / **6 / 7 / 8 / 9** |
| Extra point | 1 | 1 | 1 | 1 |
| Return and recovery TDs | 6 | 6 | 6 | 6 |

Field goal tiers are by distance: through 2025, 3 points for 1-39 yards, 4 for 40-49, 5 for 50+. From 2026
the tiers continue by decade, 6 for 60-69 up to 9 for 90-99.

**Passing yards are not a smooth rate from 2023.** The rule is `1/30`, one whole point for every completed
30 yards, not 0.0333 per yard: 299 passing yards scores 9, the same as 270. Through 2022 it genuinely was
a per-yard multiplier. This shows up in the scores themselves — every 2023-2025 score is a multiple of 0.1,
while 2021-2022 scores land on quarter-points, which is how the change was dated (see Provenance).

The league is **half PPR**, which matches `redraft_rankings_half_ppr.csv`. Note that the dynasty and rookie
rankings pulled from fantasypros are full PPR, so they already disagree with league scoring by half a point
per reception; from 2026 the tight end premium closes that gap for tight ends only.

### 2026 changes

Proposed in the 2025 rule proposals thread and confirmed by the commissioner on 20 August 2025:

> Final votes:
> - 1PPR for TE passed (2026)
> - new year change passed (start this off season)
> - Kicker scoring change passed (2026)

Both scoring changes are in 2026's `rules.json`. The third is not a scoring change and is not visible in
any export: the league's new year now follows the NFL's rather than a fixed February date, moving the
contract decision deadline from 24 February 2025 to 11 March 2026. It took effect for the 2025-26 offseason.
It matters here only in that keep-or-release decisions are now made with more free agency information.

A fourth proposal, raising tight end receptions to 1.5 rather than 1.0, was on the ballot and did not pass.

## Provenance

Two of the three sources are trustworthy as fetched. One is not.

**`rules.json` is period correct.** Verified independently of the export itself, from the decimal structure
of the league's own recorded weekly scores. A per-yard rate of .05 can only produce totals that are
multiples of .05; a rate of .025 produces quarter-points, which round to two decimals ending in 3 or 8; a
whole point per 30 yards contributes only integers, leaving .1 granularity from rushing and receiving. QB
scores across four weeks of each season:

| Season | Score endings | Implies |
| --- | --- | --- |
| 2017-2020 | 60/60 multiples of .05 | .05/yd |
| 2021-2022 | 27/60 multiples of .05, rest ending .3 or .8 | .025/yd |
| 2023-2025 | 60/60 multiples of .1 | 1 per 30 yds, truncating |

**`league.json` is not, and must not be refetched for past seasons.** Fetching 2021's today returns a $300
cap and the superflex lineup, neither of which the league had until 2022. The committed 2021 file, captured
on 6 September 2021, reports $250 and a single quarterback. The league site does not hold these settings
per season the way it holds rules and rosters; it has carried later settings backwards.

This puts `league.json` in the same category as `rosters.json`: refetchable in the sense that the request
succeeds, but wrong. `season_history_refresh.sh` deliberately writes neither. Treat the committed file as
the record and check `git log` for when a season's was captured — 2017's, 2020's, 2023's, 2024's and 2025's
were each committed a year or more after their season, so they carry the same risk and are only trusted
here because they agree with the seasons either side of them.

**Roster maximums are derived, not sourced**, except 2025's. See the reasoning above.

**The message board is nearly all gone.** Only the 2025 board still exists; every earlier year returns
"Board doesn't exist." So the 2025 rule proposals thread is the only surviving discussion of a rule change,
and the reasoning behind the 2021, 2022 and 2023 changes is not recoverable from the site. The 2025 board
is at `https://www44.myfantasyleague.com/2025/mb/board_show.pl?bid=202548571` and needs no login.

## Refreshing

`rules.json` is fetched for the current year by `./data_refresh.sh <year>` and for completed seasons by
`./season_history_refresh.sh <year> ...`, alongside the roster snapshots and transaction log.
