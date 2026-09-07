# Loading a season onto the league site

The auction is held in a room and the rookie draft is run by email, so for a few weeks each year what
happened exists in the commissioner's workbook and nowhere else. This is how it gets onto
myfantasyleague.com.

Run with [`./mfl_load.sh`](../../mfl_load.sh). The workbook it reads is `reports/fuad/fuad_<year>.xlsx`,
which is not in the tree: `reports/` is generated output and the workbook is kept by hand alongside it.

**Two steps in this run book cannot be automated.** They are marked *by hand* below, and they are not
oversights — [Why the rosters are loaded by hand](#why-the-rosters-are-loaded-by-hand) records what was
tried. Everything else is a step of `mfl_load.sh`.

## Before starting

- `./data_refresh.sh <year>` first. The plan resolves the workbook against
  `src/main/resources/ff/mfl/data/<year>/players.json` and reads pick ownership from the league site, and a
  stale local copy disagrees with the site about traded picks. See [DATA.md](DATA.md).
- The previous season's `rosters_deadline.json` must be present. Rookie salaries are read from it under
  bylaw 8.3.1 rather than entered by hand — see [rookie salaries](LEAGUE_RULES.md#rookie-salaries).
- Credentials go in the environment, never on the command line, so the password stays out of the shell
  history:

```bash
read -rs "MFL_PASS?MFL password: "; export MFL_PASS
export MFL_USER=<commissioner>
```

**Rehearse on a clone.** Copy the league on the league site and run the whole book against the copy first.
The league id has no default anywhere in this tooling for the same reason: every step names it, so none can
reach the real league because a variable was forgotten.

## The run book

### 1. Plan

```bash
./mfl_load.sh -t plan -l <league>
```

Reads the workbook, resolves every name against the league's player database, and writes
`reports/fuad/<year>/load`: the per-team lists, the review sheets, the payloads, and `ir.tsv`.

It writes nothing to the league site. It fails rather than producing a partial plan — an unmatched name, an
owner whose column header does not match `owners.json`, a pick whose owner disagrees with the site.

**`ir.tsv` is captured here for a reason.** The workbook does not record injured reserve and the Load
Rosters page returns everybody active, so the only copy of who is on IR is the league site as it stands
before anything is cleared. Read after step 3 it would be empty and those players would come back active.

Check `releases.tsv` before going further. It is who the sheet says left, and a player who was meant to be
re-signed but whose salary cell was left blank appears here rather than on a roster.

### 2. Unlock all players — *by hand*

Commissioner → free agent settings → unlock all players.

**Do not skip this.** The league site locks a player automatically when he is dropped, and a locked player
cannot be added back — not by the Load Rosters page and not by any API call. Skipped, step 4 fails for every
player who was on a roster a moment earlier, which is most of them.

### 3. Clear

```bash
./mfl_load.sh -t clear -l <league>
```

Drops every player from every roster. The Load Rosters page refuses a list naming a player who is on
somebody else's team, and after an auction most of the league has moved, so the rosters have to be empty
before any list will load.

This reads the rosters as they are rather than working from the plan, so it is safe to re-run.

It writes one `FREE_AGENT` transaction per franchise — ten rows, not one per player. They are visible only
to the commissioner and can be deleted from the log afterwards.

### 4. Load the rosters — *by hand*

Commissioner → Load Rosters. Paste `reports/fuad/<year>/load/lists/<franchise>_<owner>.txt` for each team,
one file per franchise.

The lists carry the league site's own spelling, surname first, which is what the page matches on. A name
two players share is refused by step 1 rather than guessed at, so anything in these files resolves to one
player — but the page matches against every player in the database rather than the league, and the league
has carried a Justin Jefferson at both receiver and linebacker. If the page asks, the review sheet
`rosters.tsv` carries the player id.

The lists hold the auction rosters only. Rookies arrive in step 5 from the draft.

### 5. Load everything else

```bash
./mfl_load.sh -t load -l <league>
```

Contracts, the rookie draft, rookie salaries, and injured reserve, in that order. The draft has to follow
the rosters, and injured reserve has to follow the draft. Verification runs at the end.

### 6. Verify

```bash
./mfl_load.sh -t verify -l <league>
```

Compares the league site against the plan player by player. Run it again after any manual correction.

A clean run reports every franchise `exact`, zero salary and contract-year mismatches, injured reserve
restored, and every pick filled.

### 7. Delete the clear's transactions — *by hand*, optional

The ten `FREE_AGENT` rows from step 3, if you would rather the log held only real moves.

## What the load does not set

**Rookie contract lengths.** Bylaw 12.4 makes the length the drafting team's choice, declared by the cut
down date, so the draft leaves all of them at `contractYear` 0. Bylaw 4.6 means an unassigned rookie does
not count against the cap in the meantime, so nothing is wrong until owners declare. See
[what a pick actually commits](LEAGUE_RULES.md#what-a-pick-actually-commits).

**Roster limits.** A load lands well over the 23/30 bylaw, because it happens before the cut down. See
[roster limits](LEAGUE_RULES.md#roster-limits).

## Why the rosters are loaded by hand

The league site has no roster import. Of its twenty six import types, only two reach a roster at all:

- **`auctionResults`** loads an offline auction in one call, which is exactly this job, but it needs the
  site's auction module. This league has never used it — the auction is held in a room — and the site
  answers `No auction set up`. Worse, with `OVERWRITE=1` and a payload it does not recognise it clears
  every roster and adds nothing, and reports `OK`.
- **`fcfsWaiver`** adds and drops one franchise at a time. Drops always work. **Adds are refused whenever
  the free agent pool is locked**, which it is here: the league runs `BBID_FCFS`, and every free agent
  carries `status: locked`. A load built on it drops sixty nine players successfully and then fails on all
  forty one adds.

So drops are automated and adds are not. The commissioner's Load Rosters page has neither restriction.

## Things that have gone wrong

**`OK` does not mean it worked.** The site answers a request it could not apply exactly as it answers one
it did. A load once returned four `OK`s and left the league empty. Nothing in the tooling treats `OK` as
evidence; step 6 reads the result back, and that is the only thing that decides whether a load worked.

**The import format is the export format.** The site documents an XML example for `salaries` and for
nothing else. A `draftResults` payload missing its `draftUnit` wrapper, or writing `round="1"` where the
export writes `round="01"`, imports nothing and returns `OK`.

**Writes go to the league's own host.** The site answers other hosts with a redirect, and a POST is not
re-sent across one, so a write aimed at `api.myfantasyleague.com` can be dropped in silence. The tooling
reads the league's host out of its own export.

**The sheet's pick column cannot be read.** It is written as round-point-pick and stored as a number, so
1.1 and 1.10 are the same value and the tenth pick of a round is indistinguishable from the first. Row
order is what the draft is read from.

**A blank salary means unsigned, not zero.** The site stores those contracts at 0.01 because it rejects a
zero. Unsigned players are released rather than loaded — see [the rollover rule](DATA.md#the-rollover-rule).
