# The Greenfield league

A fourteen team Yahoo snake draft with keepers, full PPR, one quarterback. It shares this project's model
and almost none of the dynasty league's rules, so what follows is what differs and what that costs.

Run it with:

```
./greenfield_report.sh -t all
```

`board` is every ranked player with what he is worth and when this league has taken him, `keepers` is each
declared keeper against the pick it costs, `picks` is what a pick has actually been worth here, and `demand`
and `adp` are when each position comes off the board.

## What the league is

Fourteen teams, fifteen rounds, snake order. Nine starters: QB, WR, WR, RB, RB, TE, W/R/T, K, DEF, with six
on the bench. Scoring is six points a passing touchdown, a point per twenty five passing yards, a tenth of a
point a rushing or receiving yard, six a touchdown, and **a full point per reception at every position** —
no tight end premium. Field goals are three up to forty yards, four for the forties, five beyond.

**None of that has moved in ten seasons.** Every rules export from 2017 to 2026 differs only in two IR slots
that came and went and a column of Yahoo's own defaults. That is the largest single difference from the
dynasty league, which has scored four different ways since 2017 and needs every season restated before any
two can be compared. Here they already are.

Two consequences for the model. Yahoo scores yardage fractionally where MFL truncates, which falls only on
quarterbacks and always downward, so `ScoringRules.fractionalPassingYards` exists and is on for this league
and off for the other. And the lineup being constant means the starting requirements are a property of the
league rather than of a season, which is why `League.GREENFIELD` carries them and `League.FUAD` refuses to.

## The keeper rule

Each team may keep up to two players, at two prices with two eligibilities:

| Slot | Price | Eligible |
| --- | --- | --- |
| A | an 8th round pick | drafted in round 6 or later last season, or undrafted |
| B | a 2nd round pick | drafted in round 3 or later last season, or undrafted |

Since "round 3 or later" contains "round 6 or later", a late round player qualifies for both slots and an
early round one for neither. **Rounds 3 to 5 are a dead zone**: a player drafted there is keepable only at
the dear price, and both of 2026's second round keepers are in it.

The rule holds against the record. Across the eight seasons with a prior draft behind them there are
**98 keepers, none violating eligibility, and no team ever over the limit of two**. Twenty one of the 98
were kept by an owner who did not draft them, which the rule permits: it is a claim about the round a player
went in, not about who took him, so a player traded midseason is keepable by whoever holds him.

Round one carries the same marker in the 2017 and 2019 exports, where it was used for administrative
purposes. Those are discarded, and with them the only two seasons that appeared to break the limit.

## What a rank is worth

The chain is the auction's chain as far as points over replacement, and is the same code: order from the
FantasyPros consensus, level from what those ranks have historically scored restated under the rules being
priced, replacement taken one past what the league actually starts, week by week. See
[PROJECTION.md](PROJECTION.md) for how the curve is built and why it is indexed by rank rather than by
player.

**The order comes from a full PPR ranking and the level from half PPR seasons.** No PPR export survives for
a finished preseason, so the historical order is the half PPR set. Measured on 2026, where both exist, the
two disagree by a mean of 0.5 to 1.35 ranks within a position over the top forty — inside the ±2 the curve
already smooths over. It is a stand-in nonetheless.

### Where the flex goes decides everything

<!-- figures: greenfield_positions -->

| POS | STARTED | REPLRANK |
| --- | --- | --- |
| QB | 14 | 15 |
| RB | 28 | 29 |
| WR | 42 | 43 |
| TE | 14 | 15 |
| PK | 14 | 15 |

Six of the eight modelled slots are fixed by the minimums and one is flex. **Every flex goes to a receiver**:
WR runs to 42 started while RB stays at its minimum of 28 and TE at 14, because in full PPR at fourteen
teams the third receiver outscores the third back and the second tight end at every decision.

That sets replacement at every position at once, and it is the opposite of the dynasty league, where the
flex is what makes quarterback scarce. Here quarterback is capped at one a team, so no flex can reach it and
fourteen are started however the rest falls — **replacement at rank 15, against rank 21 under superflex.**

What that does to the top of each position:

<!-- figures: greenfield_curve across=POS field=PTS -->

| Rank | QB | RB | WR | TE |
| --- | --- | --- | --- | --- |
| 1 | 306.1 | 213.4 | 224.0 | 161.5 |
| 15 | 243.4 | 161.9 | 171.3 | 99.8 |

and to what those ranks are worth over the player who would take the slot instead:

<!-- figures: greenfield_curve across=POS field=VOR -->

| Rank | QB | RB | WR | TE |
| --- | --- | --- | --- | --- |
| 1 | 75.4 | 103.8 | 105.2 | 71.3 |
| 15 | 30.5 | 59.4 | 58.1 | 18.3 |

Tight ends level identically in both leagues, which is not an error. A tight end scores six a touchdown, a
tenth a yard and a full point a reception under both rule sets, and neither the passing yardage rate nor the
touchdown value nor the kicking tiers touch him. The two leagues really do score the position the same way.

**Team defences are started and not priced.** The league scores one, the statistics here are per player, and
a position with no curve is reported as having none rather than guessed at. That is why eight slots are
modelled against a nine slot lineup: the starter count is what the flex is allocated against, so a slot no
modelled position can fill has to come out of it, or the defence's slot is handed to an extra back and
replacement is pushed a rank deeper at whichever position wins it.

## What a pick is worth

Not what consensus order says. No draft has ever obeyed consensus order, and this league has nine of its
own — same fourteen teams, same scoring, same keeper rule. `DraftHistory` walks each of them against a board
ordered by what the model says players are worth and records, at every pick, what the best undrafted player
was worth.

<!-- figures: greenfield_picks -->

| ROUND | BESTFIRST | BESTLAST | DROP |
| --- | --- | --- | --- |
| 1 | 105.2 | 87.2 | 18.0 |
| 2 | 80.6 | 73.4 | 7.2 |
| 3 | 68.9 | 61.2 | 7.7 |
| 4 | 61.2 | 44.7 | 16.4 |
| 5 | 44.7 | 43.2 | 1.5 |
| 6 | 43.2 | 43.2 | 0.0 |
| 7 | 38.9 | 37.0 | 1.9 |
| 8 | 37.0 | 35.7 | 1.3 |
| 9 | 35.7 | 26.4 | 9.3 |
| 10 | 24.9 | 18.6 | 6.3 |

**Two cliffs and a plateau.** Value falls hard through round one, again across round four, and again at round
nine. Between rounds five and eight it barely moves at all: an eighth is worth about what a fifth is.

The plateau is a trade rule. Moving down inside it costs almost nothing; moving across a cliff costs a great
deal. It is also why a keeper priced at an eighth is giving up more than its round number suggests.

Why the middle rounds hold their value is the finding underneath. **This league leaves a startable
quarterback on the board into round eight in most seasons** — Philip Rivers, Matthew Stafford, Joe Burrow,
Dak Prescott and Justin Fields are five of the nine best-available-at-pick-103. Fourteen teams start one
apiece against a rank 15 replacement, so a QB10 to QB13 still carries thirty to forty points over
replacement while the room spends round eight elsewhere.

## When each position comes off the board

Value says who to draft. It does not say when he will be gone, and taking the highest value over replacement
at every pick is only correct if the board waits for you. It does not.

<!-- figures: greenfield_demand -->

| ROUND | QB | RB | WR | TE | PK | DST |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 0 | 8 | 6 | 1 | 0 | 0 |
| 4 | 4 | 23 | 24 | 4 | 0 | 0 |
| 6 | 7 | 31 | 36 | 9 | 0 | 0 |
| 8 | 12 | 40 | 43 | 12 | 0 | 3 |
| 9 | 15 | 42 | 49 | 13 | 2 | 3 |
| 15 | 25 | 60 | 71 | 24 | 12 | 16 |
| STARTED | 14 | 28 | 42 | 14 | 14 | 14 |

Read every row against `STARTED`. Once a position's count passes it, everyone drafting after is choosing
from below the replacement the whole board is priced against.

**Running back is exhausted in round six.** Eight go in the first round alone and thirty one — more than the
twenty eight the league starts — are gone by the end of the sixth. Receiver lasts until round eight,
quarterback until nine, tight end until ten. **Kicker never runs out**: twelve are taken in fifteen rounds
against fourteen starting slots, so the room ends the draft short of a position it has to field and fills it
off waivers. Defences are barely different — sixteen taken against fourteen needed, and none at all before
round seven.

That is the asymmetry a plan has to be built around, and it is not what value alone would suggest. The typical
pick each positional rank has gone at:

<!-- figures: greenfield_adp -->

| RANK | QB | RB | WR | TE | PK | DST |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 34 | 2 | 7 | 23 | 139 | 106 |
| 5 | 56 | 8 | 12 | 58 | 175 | 146 |
| 10 | 90 | 16 | 25 | 87 | 191 | 188 |
| 14 | 119 | 25 | 33 | 131 | 199 | 197 |

**The best quarterback in the league goes at pick 34.** He is worth 75.4 over replacement, against about 65
for the best player otherwise available there — so the room leaves roughly ten points on the table at the
position, every year, and goes on leaving it: QB5 goes at 56 and QB10 at 90.

Running back is the mirror image. RB1 goes at pick 2 and is worth 103.8, which is about what the pick is
worth, so there is no bargain at the top — the scarcity is at the other end. RB14 goes at pick 25 and RB28,
the last starter, at pick 67, after which the position is below replacement for everyone left.

**So the two readings pull in opposite directions and both are right.** Value says take the best back or
receiver early, because that is where the points are. Demand says the backs will be gone and the
quarterbacks will not, so the quarterback can wait and the back cannot. A plan that reads only `VOR` drafts
a good team with no startable back; one that reads only `ADP` reaches for scarcity that is not worth having.

`ADP` is on the board beside `VOR` for exactly this reason.

**Team defences are counted, though nothing prices them.** They needed a team map to count at all: the
rankings write "Denver Broncos", or "Chicago (CHI)" in the 2018 to 2020 exports, where the draft export
writes "Bears" — and the last two share no prefix, no suffix and no word, so none of the name matching that
works for players reaches them. `NflTeams` resolves all three forms onto one abbreviation, including the
three franchises that renamed inside the collected seasons: Washington is Redskins through 2019, Football
Team in 2020 and 2021, and Commanders since, while the Raiders and Chargers each moved city and kept a
nickname.

With that, `UNRANKED` falls from seventeen picks a draft to two — a couple of deep fliers no ranking
carried, rather than a whole position hiding in the residual. The first defence off the board goes at pick
106, which is round eight: the same round the room is leaving startable quarterbacks sitting.

## The 2026 keepers

<!-- figures: greenfield_keepers -->

| PLAYER | POS | RANK | COSTROUND | VOR | MEASURED | MEASUREDSURPLUS |
| --- | --- | --- | --- | --- | --- | --- |
| Drake Maye | QB | 3 | 8 | 67.1 | 37.0 | 30.1 |
| Chris Olave | WR | 10 | 8 | 64.3 | 37.0 | 27.3 |
| Javonte Williams | RB | 17 | 8 | 52.0 | 37.0 | 15.0 |
| Luther Burden III | WR | 23 | 8 | 49.6 | 35.7 | 13.8 |
| Travis Etienne Jr. | RB | 18 | 8 | 45.9 | 37.0 | 8.9 |
| Omarion Hampton | RB | 9 | 2 | 79.2 | 77.0 | 2.2 |
| Trevor Lawrence | QB | 9 | 8 | 36.8 | 37.0 | -0.2 |
| Cam Skattebo | RB | 20 | 8 | 33.5 | 37.0 | -3.5 |
| Tucker Kraft | TE | 6 | 8 | 31.4 | 37.0 | -5.6 |
| Kyle Pitts Sr. | TE | 7 | 8 | 28.0 | 37.0 | -9.1 |
| Rashee Rice | WR | 12 | 2 | 58.2 | 76.7 | -18.5 |
| Jacory Croskey-Merritt | RB | 39 | 8 | 6.9 | 35.7 | -28.8 |

**Half of them are losses.** The board reports two readings and this is the lower: `MEASURED` prices the
forfeited pick at what this league has really left on it, where the report's `SURPLUS` column prices it at
whoever consensus says is next. Neither is the truth alone — nobody drafts in consensus order, and nobody is
handed the best player the model can see either — but an owner drafting off this board should weigh the
measured one, because that is the value he is actually giving up.

Both second round keepers were drafted in rounds three to five and so could not reach the cheap slot at all.
Neither owner chose the dear price; both were forced into it by the dead zone.

**The surpluses do not add up.** Several owners surrendering adjacent eighth round picks are each measured
against the same next best player, and only one of them could have had him. Every row answers "what do I
gain by keeping, if everyone else keeps what they have declared", which is the question an owner has and not
a valuation of the rule.

## What the data is, and what was done to it

Ten seasons of draft exports, rules exports and owner lists, pulled by hand from Yahoo. Under
`src/main/resources/ff/greenfield/data/<year>`: `draft.tsv`, `teams.tsv`, `rules.txt`, and for the season
being drafted `draft_order.tsv` and `keepers.tsv`. `owners.tsv` holds the league's nineteen owners.

**The raw exports are not committed.** They carry manager emails and full names; only a scrubbed extract is,
with email used as the join key and then discarded and names cut to a first name and an initial.

Email earns that role by being the only stable identity in the exports: nineteen addresses, each mapping to
exactly one manager across ten years, against team names that change for two to six of the fourteen teams
every season. Joining seasons on team name would silently merge unrelated franchises, which is the same trap
franchise ids set in the other league — see [DATA.md](DATA.md#franchises). The owners export also carries
team names in full where the draft export truncates them, so `Good But Not...` resolves against
`Good But Not Great`, and all 1,890 picks join to an owner with nothing unresolved.

### Two things that produced plausible numbers rather than errors

Both were found by disbelieving an output, and both are recorded because either would silently return a
wrong answer if reintroduced.

**Keepers are recorded at the round they cost**, which is a price and not the moment they left the board.
Walking a draft file as written leaves a keeper looking like the best available for most of the draft: 2019's
James Conner sat at the top of the board for 84 picks that way, and every pick value past the first round
was his.

**An unmatched name is never marked drafted**, so it pins the top of the board for the rest of the draft.
Suffix drift between Yahoo and FantasyPros held the measurement at one board position, and the alias map
held it at another until it was applied to both sides. The Yahoo export backdates Robby Anderson to
"Robbie Chosen" exactly as nflverse does, so 2019 reported him as the best player available in round eight
of a draft he was taken in. See [DATA.md](DATA.md#player-names).

Keeper markers themselves are a private use character, `U+E03E`, carried out of Yahoo's icon font by the
copy and paste and sitting inside the player name. It is parsed to a boolean at load, because it is
invisible in most editors and would otherwise break every name match downstream while looking like nothing
at all.

## What is not modelled

- **Team defences.** One starting slot of nine. They are counted — `demand` and `adp` both carry them — but
  not valued, and will not be until team defence statistics are collected. Nothing on the board prices one.
- **In-season acquisitions.** The board values a draft, and this league has unlimited FAB waivers.
- **Draft pick trades.** The league allows them; nothing here prices one, though the pick table is what
  such a price would be read from.
- **The deep rounds of the pick table.** A pick spent on a player no ranking carried cannot be matched, so
  he is never taken off the board and the best available past him is overstated. Rare early, common late.
