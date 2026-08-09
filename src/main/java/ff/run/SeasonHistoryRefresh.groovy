package ff.run

import ff.fetch.mfl.MflRosterSnapshotRefresh
import ff.fetch.mfl.MflTransactionsRefresh
import ff.fetch.mfl.RosterSnapshot

/**
 * Collect the record of a completed season: both roster snapshots and the transaction log. Unlike
 * {@link DataRefresh} this writes nothing that cannot be refetched, so it cannot overwrite the pre draft
 * rosters in rosters.json with today's state.
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
            } + [new MflTransactionsRefresh(Integer.parseInt(year), LEAGUE_ID, HOST) as Runnable]
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
