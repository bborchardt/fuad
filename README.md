# fuad

Draft sheets for a dynasty salary cap league, built from myfantasyleague.com league data and fantasypros
consensus rankings.

```
./data_refresh.sh <year>                       # pull this year's league and rankings data
./season_history_refresh.sh <year> ...         # pull snapshots and transactions for completed seasons
./generate_report.sh -t all [-y <year>]        # write reports to reports/<year>
./generate_report.sh -t roster -f <id>         # what each player adds to one team's lineup
./check_strategy.sh <plan.md>                  # hold a plan to the board it was written from
./figures_refresh.sh <year>                    # write the model's own figures to docs/figures/<year>
./check_docs.sh [<year>] [<doc.md> ...]        # hold the docs to those figures
```

Report types: `franchises`, `franchise_projections`, `rankings`, `rookies`, `salaries`, `teams`, `schedule`, or `all`.
`roster` is separate: it answers for one named team rather than for the league, so it takes `-f` and is not
part of `all`.

See [docs/DATA.md](docs/DATA.md) for what each data file is, how the league's expansions and contractions
show up in it, and which seasons needed repair.

See [docs/PROJECTION.md](docs/PROJECTION.md) for how the `salaries` report prices an auction: consensus
ranks for order, league-scored projections for the curve, corrected for what a rank really delivers, priced
against the cap the league has left, and settled against the franchise tags it expects to be used.

See [docs/LEAGUE_RULES.md](docs/LEAGUE_RULES.md) for what the rules were in each season — starting
requirements, salary cap, roster limits, franchise tag prices and scoring — all of which have changed, and
none of which a projection can assume is constant across years.

See [docs/STRATEGY.md](docs/STRATEGY.md) for the rule a draft plan follows: it reasons from the reports and
from nothing behind them, so it cannot drift off a stale board or argue a premium the model has already
priced in. Plans live in `strategy/`, which is not committed.

The same rule applies to the documentation itself. Figures about the model are generated into
`docs/figures/<year>` and committed, and the tables that cite them are checked against them, so a level or
a depth that moves fails `./check_docs.sh` in the commit that moves it rather than going quietly stale in
prose. The figures are themselves checked against the model that is checked out, so prose agreeing with
figures an older model wrote is a failure rather than a pass, and each document reports how many figures it
was held to — a document with no marked table reads `NONE`, never `OK`. **When the model changes: commit
it, run `./figures_refresh.sh`, then `./check_docs.sh`, and work through whatever it reports.**
