# The keeper league's data

Ten seasons of draft exports, rules exports and owner lists, pulled by hand from Yahoo. Under
`src/main/resources/ff/greenfield/data/<year>`: `draft.tsv`, `teams.tsv`, `rules.txt`, and for the season
being drafted `draft_order.tsv` and `keepers.tsv`. `owners.tsv` holds the league's nineteen owners.

**The raw exports are not committed.** They carry manager emails and full names; only a scrubbed extract is,
with email used as the join key and then discarded and names cut to a first name and an initial.

Email earns that role by being the only stable identity in the exports: nineteen addresses, each mapping to
exactly one manager across ten years, against team names that change for two to six of the fourteen teams
every season. Joining seasons on team name would silently merge unrelated franchises, which is the same trap
franchise ids set in the other league — see [DATA.md](../fuad/DATA.md#franchises). The owners export also carries
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
