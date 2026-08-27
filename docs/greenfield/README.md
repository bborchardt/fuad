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

`sheet` is the one to have open while drafting: the same figures as `board`, in one list across all
positions ordered by what a player is worth, with the keepers already marked taken. It is comma separated
rather than tab, being opened by a spreadsheet rather than pasted into one.

`outlook` is separate: it answers for one draft slot rather than for the league, so it takes `-s` and is not
part of `all`.

```
./greenfield_report.sh -t outlook -s 13            # the plan, from the keepers alone
./greenfield_report.sh -t outlook -s 13 -r RB,WR   # re-planned around what has been taken since
```

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
[PROJECTION.md](../fuad/PROJECTION.md) for how the curve is built and why it is indexed by rank rather than by
player.

**The order comes from a full PPR ranking and the level from half PPR seasons.** No PPR export survives for
a finished preseason, so the historical order is the half PPR set. Measured on 2026, where both exist, the
two disagree by a mean of 0.5 to 1.35 ranks within a position over the top forty — inside the ±2 the curve
already smooths over. It is a stand-in nonetheless.

### Where the flex goes decides everything

<!-- figures: greenfield/positions -->

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

<!-- figures: greenfield/curve across=POS field=PTS -->

| Rank | QB | RB | WR | TE |
| --- | --- | --- | --- | --- |
| 1 | 306.1 | 213.4 | 224.0 | 161.5 |
| 15 | 243.4 | 161.9 | 171.3 | 99.8 |

and to what those ranks are worth over the player who would take the slot instead:

<!-- figures: greenfield/curve across=POS field=VOR -->

| Rank | QB | RB | WR | TE |
| --- | --- | --- | --- | --- |
| 1 | 75.4 | 103.8 | 105.2 | 71.3 |
| 15 | 30.5 | 59.4 | 58.1 | 18.3 |

Tight ends level identically in both leagues, which is not an error. A tight end scores six a touchdown, a
tenth a yard and a full point a reception under both rule sets, and neither the passing yardage rate nor the
touchdown value nor the kicking tiers touch him. The two leagues really do score the position the same way.

### The defence is priced, and is worth almost nothing

It was left out at first on the belief that the statistics here are per player and a defence is not a
player. That was the belief that kept kickers out too, and levelling them found real value — so the same
question was asked of the defence, and answered the same way. See [DATA.md](../DATA.md#team-defences).

<!-- figures: greenfield/positions -->

| POS | BEST | REPLPTS |
| --- | --- | --- |
| WR | 224.0 | 116.4 |
| RB | 213.4 | 108.9 |
| QB | 306.1 | 243.4 |
| TE | 161.5 | 99.8 |
| PK | 113.0 | 87.2 |
| DST | 94.9 | 88.8 |

**Six points across a season**, against the kicker's twenty six and the tight end's sixty two. It is the
flattest position in the league by a wide margin, and the curve is not even monotone through the ranks that
start:

<!-- figures: greenfield/curve across=POS field=PTS -->

| Rank | DST |
| --- | --- |
| 1 | 94.9 |
| 8 | 96.8 |
| 15 | 88.8 |

The eighth defence levels above the first. Preseason rank carries almost nothing about which defence will
be good, and none of what little spread there is comes from availability — every defence plays all thirteen
games, so the games column is a constant.

**Which is why one is taken in the last third of the draft, and why that is right.** No defence comes off
the board here before round seven and the first goes at pick 106. The room's behaviour and the model agree,
which is worth more than either alone.

The defence is capped at one a team, so it takes no flex and changes nothing about what any other position
is worth.

## What a pick is worth

Not what consensus order says. No draft has ever obeyed consensus order, and this league has nine of its
own — same fourteen teams, same scoring, same keeper rule. `DraftHistory` walks each of them against a board
ordered by what the model says players are worth and records, at every pick, what the best undrafted player
was worth.

<!-- figures: greenfield/picks -->

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

<!-- figures: greenfield/demand -->

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

<!-- figures: greenfield/adp -->

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

## Drafting from one slot

`outlook` is the sheet to have open while the draft runs. For one slot, at each of its picks, it reports the
best rank still likely to be there at every position and what that position loses by the time this slot
picks again.

**`DECAY` is the column to draft from.** Value says which player is worth most now; decay says which
position will have fallen furthest by the next turn, and only the second can be acted on. A back and a
receiver worth the same today are not the same pick if the backs will be gone in two rounds and the
receivers will not. It is the same asymmetry the demand table shows, applied to one slot's actual picks.

**It is roster-aware, which is what makes it a plan rather than a list.** Each position carries `HELD` and a
`STATUS` — `NEED` for a starting slot still unfilled, `FLEX` for one that can still take a better player up
to the position's cap, `FULL` for one that cannot start another however he grades. Only positions that can
still improve a lineup are candidates, so a team holding its quarterback stops being offered quarterbacks.

The row marked `TAKE` is the recommendation, and it is **chosen for the whole draft rather than for the
pick it sits on**. Taking whichever position falls furthest before the next pick is a different rule and a
worse one: it looks one gap ahead, so it cannot see that a position it defers will be far worse by the time
it comes back.

Against slot 13's real board that cost thirteen points of starting value. The one-gap rule took the third
best quarterback in round three, which pushed the third receiver out to round seven — where receivers are
worth a third of what they are in round five. The quarterback deferred instead cost twenty five and the
receiver gained twenty, and no comparison of a single gap could tell.

`DECAY` is still the column to read when the board disagrees with the plan, because it says which of the
positions actually in front of you will not wait. Keepers are counted without being asked for; `-r` is for
what has really been taken as the draft runs, and the rest re-plans around it.

**Two things it does not do, both deliberate.** It stops recommending once every starting slot the model
prices is full: what a bench is worth is bye cover, injury cover and optionality, which is `LineupValue`'s
question and not this sheet's, and inventing a preference it cannot support would be worse than saying
`BENCH`. And it has no defence in it, so the plan covers eight of the nine starting slots and the ninth has
to be remembered — though no defence comes off the board here before round seven.

**A forfeited pick shows up as a longer gap, and that is the cost of a keeper beyond its price.** Giving up
an eighth does not only cost the player it would have returned; it doubles the wait around it, and a
position that runs out inside that gap runs out without this team.

The figures here are per slot, so unlike every other table in this document they are not committed and not
checked — there is no canonical slot to generate them for. Run the command.

### Drafting from a spreadsheet

`sheet` carries two columns that answer different questions, and a plan needs both.

`VOR` is what a player is worth over the one who would take his slot otherwise. `ADP` is the pick this
league has typically taken him at — its own median over nine drafts, pooled with the two ranks either side,
and not a national number. `VORRANK` is where he sits by worth, so `EDGE` — `ADP` less `VORRANK` — is how
far this room lets him fall past his value.

The best back is worth 103.8 and goes at pick 2; the best quarterback is worth 75.4 and goes at 34. He is
worth less and lasts thirty picks longer, and no single column says that.

**A large edge on a small value is still small.** The widest edges belong to defences, which fall seventy
picks past their worth and are worth fourteen points. `EDGE` decides between players of similar `VOR`, or
says a position can wait; it never argues for taking a lesser player. Draft down the `VOR` column and let
`EDGE` tell you what will still be there next time.

## The 2026 keepers

<!-- figures: greenfield/keepers -->

| PLAYER | POS | RANK | VOR | MEASURED | POSVALUE | MEASUREDSURPLUS | POSSURPLUS |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Chris Olave | WR | 10 | 64.3 | 37.0 | 16.8 | 27.3 | 47.5 |
| Javonte Williams | RB | 17 | 52.0 | 37.0 | 7.7 | 15.0 | 44.2 |
| Travis Etienne Jr. | RB | 18 | 45.9 | 37.0 | 7.7 | 8.9 | 38.1 |
| Luther Burden III | WR | 23 | 49.6 | 35.7 | 15.9 | 13.8 | 33.6 |
| Drake Maye | QB | 3 | 67.1 | 37.0 | 36.9 | 30.1 | 30.1 |
| Cam Skattebo | RB | 20 | 33.5 | 37.0 | 7.7 | -3.5 | 25.8 |
| Omarion Hampton | RB | 9 | 79.2 | 77.0 | 73.6 | 2.2 | 5.7 |
| Tucker Kraft | TE | 6 | 31.4 | 37.0 | 26.6 | -5.6 | 4.8 |
| Kyle Pitts Sr. | TE | 7 | 28.0 | 37.0 | 26.6 | -9.1 | 1.4 |
| Jacory Croskey-Merritt | RB | 39 | 6.9 | 35.7 | 7.7 | -28.8 | -0.8 |
| Trevor Lawrence | QB | 9 | 36.8 | 37.0 | 42.3 | -0.2 | -5.5 |
| Rashee Rice | WR | 12 | 58.2 | 76.7 | 67.1 | -18.5 | -9.0 |

**Which column decides depends on whether the keeper is a starter, and the two disagree about six of the
twelve.**

`MEASURED` prices the forfeited pick at the best player this league has really left on the board there.
`POSVALUE` prices it at the best player *at the keeper's own position*. The gap between them is the whole
subject, because this league caps a team at one quarterback, two tight ends, one kicker and one defence —
so the best player available is frequently one the owner cannot field. A pick priced at a second
quarterback nobody can start overstates what was given up by the whole difference.

Cam Skattebo is the case. The best player left at pick 100 is QB12, worth 42, so the measured reading makes
keeping him a loss of 3.5. But the best *back* left is RB40, worth 7.7 — and an owner keeping a starting
back is choosing between Skattebo and that, not between Skattebo and a quarterback he already has. Read
positionally he is worth 25.8. Kyle Pitts and Tucker Kraft flip the same way for the same reason.

**Trevor Lawrence flips the other way**, and that is the reading working rather than failing. Quarterbacks
are the one position this league leaves on the board, so QB12 really is there at pick 99 and really is worth
more than QB9 — keeping him is a loss on both readings, and more clearly on the positional one.

An owner already set at a position should read `MEASUREDSURPLUS`; one whose keeper is a starter he would
otherwise have to replace should read `POSSURPLUS`. The two bracket the decision and neither is the truth
alone.

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
franchise ids set in the other league — see [DATA.md](../DATA.md#franchises). The owners export also carries
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
of a draft he was taken in. See [DATA.md](../DATA.md#player-names).

Keeper markers themselves are a private use character, `U+E03E`, carried out of Yahoo's icon font by the
copy and paste and sitting inside the player name. It is parsed to a boolean at load, because it is
invisible in most editors and would otherwise break every name match downstream while looking like nothing
at all.

## What is not modelled

- **Streaming a defence.** The board values a draft. Points allowed is the largest term in a defence's
  score and it depends on the opponent, which is a schedule fact known weekly — so which defence to start in
  a given week is a real question and not one anything here answers.
- **In-season acquisitions.** The board values a draft, and this league has unlimited FAB waivers.
- **Draft pick trades.** The league allows them; nothing here prices one, though the pick table is what
  such a price would be read from.
- **The deep rounds of the pick table.** A pick spent on a player no ranking carried cannot be matched, so
  he is never taken off the board and the best available past him is overstated. Rare early, common late.
