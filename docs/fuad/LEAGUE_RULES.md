# League rules by season

What the league's settings actually were in each year of collected data: how many players a team starts,
how large a roster it may carry, what it costs to franchise a player, and how players score. All of these
have changed, and a salary projection built across seasons is comparing different games unless it accounts
for them.

The starting requirements and the salary cap come from each season's `league.json`, the scoring from each
season's `rules.json`. Max roster size and the franchise salary are in neither: the first is a bylaw the
site does not hold, the second is derived from the prior season's salaries. Both are covered below.

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

Each team may also franchise one free agent at a rule-set price, which changes every year; see
[The franchise tag](#the-franchise-tag). Contracts run one to five years and cost something to get out of;
see [Contract length](#contract-length).

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

## Contract length

A contract runs from one to **five** years, chosen by the signing team at the moment it bids. Both ends are
used: across the 534 skill-position signings on record, 344 are one-year deals and 11 are five-year ones.

| Years | 0 | 1 | 2 | 3 | 4 | 5 |
| --- | --- | --- | --- | --- | --- | --- |
| Signings | 48 | 344 | 78 | 46 | 7 | 11 |

A `contractYear` of 0 is a deal that expires at the end of the season it was signed in; all 48 are at the $1
minimum.

### The cut penalty

Releasing a player costs the greater of, both measured over the years **remaining** on the contract,
rounded **up**, and charged against the **current** year's cap:

- **40% of the dollars left on the contract**, and
- **$1 per year remaining.**

`CutPenalty` computes this and `CutPenaltySpec` asserts it against every penalty the league has ever
charged.

The floor is the part that is easy to miss, and it binds in exactly one place. Below $2.50 a year the 40%
is worth less than a dollar, so the minimum takes over — which makes a cheap long contract proportionally
the most expensive thing in the league to get out of:

| Contract, freshly signed | 40% of remaining | $1 per year remaining | Penalty | As a multiple of the annual salary |
| --- | --- | --- | --- | --- |
| $1 × 5yr | $2 | **$5** | $5 | **5.0x** |
| $2 × 5yr | $4 | **$5** | $5 | 2.5x |
| $5 × 3yr | **$6** | $3 | $6 | 1.2x |
| $50 × 1yr | **$20** | $1 | $20 | 0.4x |

Because both halves count only what is left, **the exposure decays as the deal runs down**. The same $1
five-year contract costs $5 to escape in its first year, $4 in its second, and a dollar in its last:

| $1 × 5yr, cut after | 0 yrs | 1 yr | 2 yrs | 3 yrs | 4 yrs |
| --- | --- | --- | --- | --- | --- |
| Years remaining | 5 | 4 | 3 | 2 | 1 |
| Penalty | $5 | $4 | $3 | $2 | $1 |

So the two ends of the board are constrained differently rather than one being free. A large salary cannot
carry length, because 40% of a long deal on real money is crippling in the year it is eaten — which is why
**87% of signings above $40 are one-year deals**. A minimum salary can carry length, but not for nothing: a
five-year dollar player costs five dollars to walk away from in the year you would most want to, five times
what he costs to keep. That is a deliberate brake on hoarding long free lottery tickets, and it works as a
brake rather than a bar — 22 of the 115 signings at $1-2 run three years or more.

**A penalty is charged once, to the current year, and clears at the end of it.** Nothing carries forward:
there is no dead money in this league. That bounds what a bad contract can cost. The worst case for a
five-year dollar player who fails immediately is $5 against one season's cap and a clean sheet the next,
rather than an obligation that follows the team around — which is why a long deal at the minimum is a
defensible bet and a long deal at $50 is not, and why the damage is a cash-flow problem rather than a
structural one.

### Confirmed against every penalty ever charged

The rule is a bylaw, absent from both `league.json` and `rules.json`. But the league site keeps a
`salaryAdjustments` export, and in this league every adjustment in its history is a cut penalty, carrying
the contract it was charged for in its description:

```
Treylon Burks (2yrs@1)                              amount 2
Roman Wilson (4yrs@1)                               amount 4
DeAndre Hopkins (1yrs@20) : Tyler Boyd (2yrs@1)     amount 10
```

384 adjustments across 2017-2025, covering 615 individual releases — a single adjustment may batch several
cuts made the same day, so the charge is the sum over the contracts named. **383 of the 384 come back
exactly.** The one that does not is a six-cut batch in 2020 entered a dollar light, which is what a hand
slip looks like.

Both halves of the rule are needed, and neither carries the record alone:

| Priced as | Adjustments reproduced exactly |
| --- | --- |
| 40% of remaining, no floor | 225 of 384 |
| $1 per year remaining, no rate | 253 of 384 |
| **the greater of the two** | **383 of 384** |

The floor's threshold is confirmed from the other side too. It governs **460 of the 615 releases**, three
quarters of them, and the salaries where it governs are exactly $1 and $2 — never $3 or more, which is what
a cutover at $2.50 a year implies and nothing else does. 105 releases land on a fraction before rounding,
and every one of them rounds up.

**What is still unverified** is the clearing. Nothing in the export says a penalty expired rather than
carried, since each adjustment is a single dated charge; that half rests on the commissioner's description
alone.

### What it means for the model

Nothing, directly: the model prices one season and contract length is not an input to it. See
[PROJECTION.md](PROJECTION.md#known-limits).

It is documented here because length is chosen jointly with price, and because it explains a shape the
signings data shows plainly. It is also why `DYNRANK` is carried on the board: what a player is worth over
five years is a different question from what he is worth this season, and only the second is priced.

## The franchise tag

Each team may franchise **one** free agent, keeping them out of the auction by signing them at the average
of the top five salaries at that player's position the previous season, rounded to the nearest dollar.
Another team may bid on a franchised player, but only by compensating with rookie draft picks. That is
enough friction to make it uncommon rather than unheard of: six of the 46 confirmed tags were bid away.

This matters for a salary model more than it might seem. A tagged player's price is **set by rule, not by
bidding**, and tags land almost exclusively on the most expensive players in the auction — exactly the
observations with the most leverage on a fitted curve. **46 of the 609 signings are confirmed tags**,
including most of the top-end quarterback and wide receiver prices in the data. Treat them as fixed points,
not as evidence of what the market pays.

The tag is also common, not rare: in a typical season half to three quarters of the league uses it.

`FranchiseSalaryCalculator` computes the values below from the previous season's end-of-year rosters.
`FranchiseTagIdentifier` works out which signings they priced, and the two specs assert both.

### Franchise salary by season

The price of tagging a player at each position, coming into that season's auction. The average is rounded
to the nearest dollar; five salaries average to a fifth of a dollar and never to a half, so no tie-breaking
rule is needed and none of the values below is ambiguous.

<!-- figures: fuad/rates across=POS field=RATE -->

| Season | QB | RB | WR | TE | PK |
| --- | --- | --- | --- | --- | --- |
| 2018 | 41 | 47 | 91 | 29 | 1 |
| 2019 | 42 | 42 | 94 | 25 | 1 |
| 2020 | 28 | 30 | 98 | 24 | 3 |
| 2021 | 23 | 40 | 92 | 23 | 2 |
| 2022 | 33 | 43 | 94 | 21 | 2 |
| 2023 | 36 | 52 | 82 | 21 | 3 |
| 2024 | 45 | 61 | 71 | 38 | 4 |
| 2025 | 47 | 64 | 61 | 32 | 3 |
| 2026 | 66 | 60 | 61 | 24 | 3 |

2017 has no value because it would need 2016 salaries, which predate the data. Which of these were actually
paid, and by whom, is in [Confirmed tags](#confirmed-tags) below.

**2026's quarterback tag is 66**, up 40% in a year and the highest any tag has ever been at any position.
That is one contract's doing: Lamar Jackson at 100 in 2025, well clear of the 55 behind him. Any team
weighing a quarterback tag this offseason is paying for that outlier.

### Identifying a tag

Nothing in the league data flags a tag, so all of them are inferred. Two things give them away.

An **uncontested** tag is a team re-signing its own expiring player, for one year, at exactly the franchise
salary. That price is set by rule rather than bid, and it stands out sharply. Across 2018-2025 there are
172 same-team one-year re-signings of an expiring player; the distribution of what they paid relative to
their position's franchise salary is:

| Salary minus rate | -8 | -7 | -6 | -3 | -1 | **0** | +2 | +7 | +8 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Signings | 1 | 1 | 2 | 3 | 2 | **42** | 1 | 1 | 1 |

Every other value in that range holds nothing. **42 signings land exactly on the rate against a background
of 0.75 per neighbouring dollar**, so an exact hit is roughly fifty times likelier to be a tag than a
coincidence, and about one of the 42 is expected to be chance.

A **contested** tag is a player signed away by another team, which the league only allows against
compensation in rookie draft picks. That leaves a first round pick moving from the signing team to the team
that held the player, with nothing coming back. Six appear in the data, and each one matches exactly one
player who changed hands in that auction. Contested tags are **not** restricted to one year — the winning
team writes its own contract, and the six run from one year to five.

What no method recovers is a tag that was bid up and kept, or bid away without the compensation being
recorded as a trade; both look like an ordinary expensive auction win. The lever against them is that a
team gets one tag, so an above-rate signing by a team that already tagged someone was not a tag. That rules
out 22 of the 35 above-rate signings and leaves 13 unresolvable candidates.

`FranchiseTagIdentifier` implements this and reports one of three statuses: `CONFIRMED` for a tag priced by
the rule or paid for with a pick, `UNCERTAIN` where a team holds more signings at its rate than it has
tags, and `CANDIDATE` for an above-rate signing that the one-per-team rule cannot rule out.

### Tags by season

| Season | Teams | Confirmed | Uncertain | Candidates | Confidence |
| --- | --- | --- | --- | --- | --- |
| 2018 | 10 | 9 | 0 | 0 | High |
| 2019 | 10 | 3 | 0 | 3 | **Low, likely incomplete** |
| 2020 | 10 | 3 | 2 | 1 | Medium |
| 2021 | 8 | 5 | 0 | 2 | High |
| 2022 | 8 | 6 | 0 | 1 | High |
| 2023 | 9 | 7 | 0 | 3 | Medium-high |
| 2024 | 10 | 6 | 0 | 0 | High |
| 2025 | 10 | 7 | 0 | 3 | High, confirmed by the league |

The strongest evidence that this is reading the league correctly is a test it was never constrained to
pass: **the one-tag-per-team limit holds in every season**, and in 2018 nine tags land on nine different
teams. Nothing in the method enforces that across teams, and coincidences would have broken it.

**2025 is confirmed against the league's own record.** The commissioner named Lamar Jackson, Jalen Hurts,
Joe Burrow, Patrick Mahomes and CeeDee Lamb; the method recovers all five, plus Jahmyr Gibbs and Saquon
Barkley, whose omission was recall rather than absence.

**2019 is the weak season.** Three tags in a ten-team league, the year after nine, is not credible. Odell
Beckham at 100 and Davante Adams at 115 both look like tags bid away without a recorded pick trade. Treat
2019 as a floor rather than a count.

**2023 and 2024 record no first round pick trades at all**, so a contested tag in either season would be
invisible. Their confirmed counts are uncontested tags only.

### Confirmed tags

<!-- figures: fuad/tags key=SEASON+PLAYER -->

| Season | Player | Pos | Salary | Rate | Basis |
| --- | --- | --- | --- | --- | --- |
| 2018 | DeAndre Hopkins | WR | 91 | 91 | exact |
| 2018 | Julio Jones | WR | 91 | 91 | exact |
| 2018 | Antonio Brown | WR | 91 | 91 | exact |
| 2018 | Tom Brady | QB | 41 | 41 | exact |
| 2018 | Drew Brees | QB | 41 | 41 | exact |
| 2018 | Travis Kelce | TE | 29 | 29 | exact |
| 2018 | Zach Ertz | TE | 29 | 29 | exact |
| 2018 | Rob Gronkowski | TE | 29 | 29 | exact |
| 2018 | Le'Veon Bell | RB | 85 | 47 | bid away, 0010 to 0005 |
| 2019 | DeAndre Hopkins | WR | 94 | 94 | exact |
| 2019 | Antonio Brown | WR | 94 | 94 | exact |
| 2019 | Le'Veon Bell | RB | 43 | 42 | bid away, 0008 to 0004 |
| 2020 | Davante Adams | WR | 98 | 98 | exact |
| 2020 | Aaron Jones | RB | 30 | 30 | exact |
| 2020 | Ezekiel Elliott | RB | 42 | 30 | bid away, 0008 to 0001 |
| 2021 | Tyreek Hill | WR | 92 | 92 | exact |
| 2021 | Derrick Henry | RB | 40 | 40 | exact |
| 2021 | Travis Kelce | TE | 23 | 23 | exact |
| 2021 | Tom Brady | QB | 23 | 23 | exact |
| 2021 | Dak Prescott | QB | 38 | 23 | bid away, 0006 to 0002 |
| 2022 | Cooper Kupp | WR | 94 | 94 | exact |
| 2022 | Davante Adams | WR | 94 | 94 | exact |
| 2022 | Derrick Henry | RB | 43 | 43 | exact |
| 2022 | Lamar Jackson | QB | 33 | 33 | exact |
| 2022 | Patrick Mahomes | QB | 33 | 33 | exact |
| 2022 | Joe Mixon | RB | 55 | 43 | bid away, 0005 to 0008 |
| 2023 | Cooper Kupp | WR | 82 | 82 | exact |
| 2023 | Davante Adams | WR | 82 | 82 | exact |
| 2023 | Stefon Diggs | WR | 82 | 82 | exact |
| 2023 | Austin Ekeler | RB | 52 | 52 | exact |
| 2023 | Christian McCaffrey | RB | 52 | 52 | exact |
| 2023 | Lamar Jackson | QB | 36 | 36 | exact |
| 2023 | Patrick Mahomes | QB | 36 | 36 | exact |
| 2024 | Tyreek Hill | WR | 71 | 71 | exact |
| 2024 | Christian McCaffrey | RB | 61 | 61 | exact |
| 2024 | Jahmyr Gibbs | RB | 61 | 61 | exact |
| 2024 | Lamar Jackson | QB | 45 | 45 | exact |
| 2024 | Patrick Mahomes | QB | 45 | 45 | exact |
| 2024 | Travis Kelce | TE | 38 | 38 | exact |
| 2025 | CeeDee Lamb | WR | 61 | 61 | exact |
| 2025 | Jahmyr Gibbs | RB | 64 | 64 | exact |
| 2025 | Saquon Barkley | RB | 64 | 64 | exact |
| 2025 | Jalen Hurts | QB | 47 | 47 | exact |
| 2025 | Joe Burrow | QB | 47 | 47 | exact |
| 2025 | Patrick Mahomes | QB | 47 | 47 | exact |
| 2025 | Lamar Jackson | QB | 100 | 47 | bid away, 0006 to 0001 |

Two patterns worth carrying into a model. Tags **cluster by position within a season** — three wide
receivers in 2023, three tight ends in 2018, three quarterbacks in 2025 — because the tag is a bargain
exactly when a position's market has run ahead of last year's top five, so several teams reach the same
conclusion at once. And **usage tracks the superflex change**: one quarterback tag in 2018-2021, then
Mahomes and Jackson every year from 2022, then four of the seven 2025 tags.

### Uncertain and candidate tags

Two 2020 signings on franchise 0010 both sit exactly on their rate — Russell Wilson at 28 and Austin Ekeler
at 30 — and a team has only one tag, so exactly one of them is a coincidence and nothing distinguishes
them. These are the expected one-in-42. Both are recorded `UNCERTAIN` and neither should be treated as a
fixed point.

The 13 `CANDIDATE` signings are above their rate on a team that tagged nobody else, so they could be a tag
bid up or won without a recorded pick trade, or simply an expensive auction:

| Season | Player | Pos | Salary | Rate | Multiple |
| --- | --- | --- | --- | --- | --- |
| 2019 | Odell Beckham | WR | 100 | 94 | 1.06x |
| 2019 | Travis Kelce | TE | 35 | 25 | 1.40x |
| 2019 | Zach Ertz | TE | 33 | 25 | 1.32x |
| 2020 | Todd Gurley | RB | 43 | 30 | 1.43x |
| 2021 | Stefon Diggs | WR | 100 | 92 | 1.09x |
| 2021 | Russell Wilson | QB | 30 | 23 | 1.30x |
| 2022 | Christian McCaffrey | RB | 60 | 43 | 1.40x |
| 2023 | Josh Allen | QB | 65 | 36 | 1.81x |
| 2023 | Travis Kelce | TE | 50 | 21 | 2.38x |
| 2023 | Kirk Cousins | QB | 40 | 36 | 1.11x |
| 2025 | Justin Jefferson | WR | 80 | 61 | 1.31x |
| 2025 | Baker Mayfield | QB | 70 | 47 | 1.49x |
| 2025 | Jordan Love | QB | 52 | 47 | 1.11x |

Some of these are mutually exclusive: 2023's three all belong to franchise 0005 and 2025's Jefferson and
Love both to 0003, so at most one of each group is a tag.

### Cautions when re-deriving this

- **Round the franchise salary before matching.** An earlier pass of this analysis compared against the
  unrounded average and concluded the tag went unused before 2021. It found nothing in 2018 because the
  rate was 91.40 and 29.20 while the signings were at 91 and 29. Rounding turns up 42 exact matches.
- **Kickers are noise.** The PK rate sits at or within a dollar or two of the $1 minimum every season, so
  ordinary cheap kicker signings hit it by coincidence — five did in 2018 alone. `FranchiseTagIdentifier`
  excludes the position outright.
- **Read positions from the season the salary was paid in.** Using a recent `players.json` for an old
  season silently drops everyone who has since retired, which is precisely where the top five sits — it
  moves the 2021 tight end rate from 23 to 17 and breaks the Kelce match.
- **The tagging team is the one holding the expiring contract**, not the one that ends up with the player.
  Lamar Jackson was tagged by 0006 in 2025 and signed by 0001, who already had a tag of their own on Jalen
  Hurts. Counting him against 0001 would look like a rule violation.

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

**The cut penalty is a bylaw, and the arithmetic of it is confirmed.** It appears in no settings file, but
`salary_adjustments.json` records every penalty ever charged and 383 of 384 come back exactly from the
stated rule. The clearing at year end is the part still resting on the commissioner's word. See
[Confirmed against every penalty ever charged](#confirmed-against-every-penalty-ever-charged).

**Franchise salaries are computed, and the computation is confirmed by the data.** 42 signings across eight
seasons come in at exactly the top-five average for their position, against a background of fewer than one
per neighbouring dollar. A rule stated independently and a spike that sharp is about as much confirmation
as this data can give.

**Which tags were used is inferred, and 2025 is the only season checked against the league's own record.**
The method recovers all of 2025 and passes the one-tag-per-team test in every season, but 2019 in
particular is likely undercounted, and any contested tag whose compensation was not recorded as a trade is
invisible everywhere. Per-season confidence is in [Tags by season](#tags-by-season).

**The average is taken over the prior season's end-of-year salaries**, which the data now settles. The two
bases agree everywhere except 2020 RB, where end-of-year gives 30 and post-draft 27 — and both 2020 running
back tags, Aaron Jones and Austin Ekeler, signed at exactly 30. One season decides it, but it decides it
cleanly.

**The message board is nearly all gone.** Only the 2025 board still exists; every earlier year returns
"Board doesn't exist." So the 2025 rule proposals thread is the only surviving discussion of a rule change,
and the reasoning behind the 2021, 2022 and 2023 changes is not recoverable from the site. The 2025 board
is at `https://www44.myfantasyleague.com/2025/mb/board_show.pl?bid=202548571` and needs no login.

## Refreshing

`rules.json` is collected by both refresh scripts, alongside the roster snapshots and transaction log.
`league.json` is written by neither, for the reason above. See [DATA.md](DATA.md#refreshing).
