# fuad

Draft sheets for a dynasty salary cap league, built from myfantasyleague.com league data and fantasypros
consensus rankings.

```
./data_refresh.sh <year>                       # pull this year's league and rankings data
./season_history_refresh.sh <year> ...         # pull snapshots and transactions for completed seasons
./generate_report.sh -t all [-y <year>]        # write reports to reports/<year>
```

Report types: `franchises`, `franchise_projections`, `rankings`, `rookies`, `schedule`, or `all`.

See [docs/DATA.md](docs/DATA.md) for what each data file is, how the league's expansions and contractions
show up in it, and which seasons needed repair.

See [docs/LEAGUE_RULES.md](docs/LEAGUE_RULES.md) for what the rules were in each season — starting
requirements, salary cap, roster limits and scoring — all of which have changed, and none of which a
projection can assume is constant across years.
