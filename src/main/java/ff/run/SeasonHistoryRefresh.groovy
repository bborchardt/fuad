package ff.run

import ff.fetch.mfl.MflRosterSnapshotRefresh
import ff.fetch.mfl.MflRulesRefresh
import ff.fetch.mfl.MflSalaryAdjustmentsRefresh
import ff.fetch.mfl.MflTransactionsRefresh
import ff.fetch.mfl.RosterSnapshot
import ff.fetch.nflverse.NflverseStatsRefresh
import ff.fetch.nflverse.NflverseTeamStatsRefresh

/**
 * Collect the record of a completed season: both roster snapshots, the transaction log, the scoring rules,
 * the salary adjustments, and the raw statistics every expected point is built from. Unlike
 * {@link DataRefresh} this writes nothing that cannot be refetched, so it cannot overwrite the pre draft
 * rosters in rosters.json with today's state.
 *
 * It also leaves league.json alone, which matters for the same reason: the league site does not keep that
 * one period correct. Refetching 2021's today reports the salary cap and superflex lineup the league only
 * adopted in 2022. See docs/fuad/LEAGUE_RULES.md.
 */
class SeasonHistoryRefresh {

    private static final int LEAGUE_ID = 48571
    private static final String HOST = 'api.myfantasyleague.com'

    static void main(String[] args) {
        if (!args) {
            System.err.println 'Usage: SeasonHistoryRefresh <year> [<year> ...]'
            Runtime.getRuntime().exit(-1)
        }
        args.each { String year ->
            List<Runnable> refreshes = RosterSnapshot.values().collect { RosterSnapshot snapshot ->
                new MflRosterSnapshotRefresh(Integer.parseInt(year), LEAGUE_ID, HOST, snapshot) as Runnable
            } + [new MflTransactionsRefresh(Integer.parseInt(year), LEAGUE_ID, HOST) as Runnable,
                 new MflRulesRefresh(Integer.parseInt(year), LEAGUE_ID, HOST) as Runnable,
                 new MflSalaryAdjustmentsRefresh(Integer.parseInt(year), LEAGUE_ID, HOST) as Runnable,
                 // Not the league site at all, and the one thing here every expected point is built from.
                 new NflverseStatsRefresh(Integer.parseInt(year)) as Runnable,
                 new NflverseTeamStatsRefresh(Integer.parseInt(year)) as Runnable]
            refreshes.each { refresh ->
                try {
                    refresh.run()
                } catch (Exception e) {
                    System.err.println "Skipping $year ${refresh.class.simpleName}: $e.message"
                }
            }
        }
    }
}
