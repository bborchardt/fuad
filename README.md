# fuad

Draft sheets for a dynasty salary cap league, built from myfantasyleague.com league data and fantasypros
consensus rankings.

```
./data_refresh.sh <year>                       # pull this year's league and rankings data
./roster_snapshot_refresh.sh <year> ...        # pull roster snapshots for completed seasons
./generate_report.sh -t all [-y <year>]        # write reports to reports/<year>
```

Report types: `franchises`, `franchise_projections`, `rankings`, `rookies`, `schedule`, or `all`.

See [docs/DATA.md](docs/DATA.md) for what each data file is, how the league's expansions and contractions
show up in it, and which seasons needed repair.
