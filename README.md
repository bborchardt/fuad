# fuad

Draft sheets for a dynasty salary cap league, built from myfantasyleague.com league data and fantasypros
consensus rankings.

It carries **two leagues**, and neither is the default one. `fuad` is the dynasty salary cap auction this
repository is named for; `greenfield` is a fourteen team full PPR snake draft with keepers. They share the
model underneath — the curve, replacement, and value over replacement — and none of the rules on top, so
each has its own report script, its own figures directory and its own documentation.

```
# shared: the data both leagues are built from, and the checks over both
./data_refresh.sh <year>                       # this year's league and rankings data
./season_history_refresh.sh <year> ...         # snapshots and transactions for completed seasons
./figures_refresh.sh <year>                    # both leagues' figures, to docs/figures/<league>/<year>
./check_docs.sh [<year>] [<doc.md> ...]        # hold the docs to those figures
./check_strategy.sh <plan.md>                  # hold a plan to the board it was written from

# fuad: the dynasty salary cap auction
./fuad_report.sh -t all [-y <year>]            # reports to reports/fuad/<year>
./fuad_report.sh -t roster -f <id>             # what each player adds to one team's lineup

# greenfield: the keeper snake draft
./greenfield_report.sh -t all                  # board, keepers, picks, demand, adp
./greenfield_report.sh -t outlook -s <slot>    # one slot's plan: what to take, and what cannot wait
```

`fuad` report types: `franchises`, `franchise_projections`, `rankings`, `rookies`, `salaries`, `teams`, `schedule`, or `all`.
`rookies` writes two: the rookie board, priced over the contract a pick comes with rather than over its first
season, and `rookie_picks`, what each pick costs at each position and who is usually still there.
`roster` and `rookie_outlook` are separate: they answer for one named team rather than for the league, so
they take `-f` and are not part of `all`. `greenfield` report types are `board`, `keepers`, `picks`, `demand`, `adp` or `all`, with
`outlook` separate for the same reason — it answers for one draft slot, so it takes `-s`.

See [docs/DATA.md](docs/DATA.md) for the statistics and rankings both leagues are built from, and the two
places a source says something other than what it appears to. Each league's own record is beside its own
documentation: [docs/fuad/DATA.md](docs/fuad/DATA.md) for the auction, where the expansions, contractions
and repairs are, and [docs/greenfield/DATA.md](docs/greenfield/DATA.md) for the keeper league.

See [docs/fuad/PROJECTION.md](docs/fuad/PROJECTION.md) for how the `salaries` report prices an auction: consensus
ranks for order, league-scored projections for the curve, corrected for what a rank really delivers, priced
against the cap the league has left, and settled against the franchise tags it expects to be used. The same
document covers [the rookie draft](docs/fuad/PROJECTION.md#rookies), which is priced differently because it is
bought differently: a pick buys up to five seasons at a salary the league sets by rule, and 89% of what this
year's early picks are worth falls after the season they are spent in.

See [docs/fuad/LEAGUE_RULES.md](docs/fuad/LEAGUE_RULES.md) for what the rules were in each season — starting
requirements, salary cap, roster limits, franchise tag prices and scoring — all of which have changed, and
none of which a projection can assume is constant across years.

See [docs/greenfield/README.md](docs/greenfield/README.md) for the second league: a fourteen team full PPR snake draft
with keepers priced at a second and an eighth round pick. It shares the curve, replacement and value over
replacement with the auction and none of its rules, and its currency is picks rather than dollars.

See [docs/STRATEGY.md](docs/STRATEGY.md) for the rule a draft plan follows: it reasons from the reports and
from nothing behind them, so it cannot drift off a stale board or argue a premium the model has already
priced in. Plans live in `strategy/`, which is not committed.

The same rule applies to the documentation itself. Figures about the model are generated into
`docs/figures/fuad/<year>` and committed, and the tables that cite them are checked against them, so a level or
a depth that moves fails `./check_docs.sh` in the commit that moves it rather than going quietly stale in
prose. The figures are themselves checked against the model that is checked out, so prose agreeing with
figures an older model wrote is a failure rather than a pass, and each document reports how many figures it
was held to — a document with no marked table reads `NONE`, never `OK`. **When the model changes: commit
it, run `./figures_refresh.sh`, then `./check_docs.sh`, and work through whatever it reports.**
