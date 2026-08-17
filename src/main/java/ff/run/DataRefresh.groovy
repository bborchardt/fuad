package ff.run

import ff.fetch.mfl.MflDataRefresh
import ff.fetch.mfl.MflRosterSnapshotRefresh
import ff.fetch.mfl.MflTransactionsRefresh
import ff.fetch.mfl.RosterSnapshot

/**
 * Collect this year's league data, and last season's record while it is still recoverable.
 *
 * <b>Nothing here fetches the consensus rankings.</b> They are downloaded by hand from fantasypros and
 * committed, which is the only way that has ever produced a complete set: the public API key is limited to
 * the first ten players of any ranking, and the one automated attempt silently dropped a position without
 * anybody noticing until an auction needed it. See docs/DATA.md.
 */
class DataRefresh {
    static void main(String[] args) {
        int year = 0
        try {
            year = Integer.parseInt(args[0])
            new MflDataRefresh(year, 48571, 'api.myfantasyleague.com').run()

            // The rosters just pulled for this year have their expiring contracts wiped, so pick up last
            // season's record while it is still around to say what those contracts were.
            RosterSnapshot.values().each { snapshot ->
                new MflRosterSnapshotRefresh(year - 1, 48571, 'api.myfantasyleague.com', snapshot).run()
            }
            new MflTransactionsRefresh(year - 1, 48571, 'api.myfantasyleague.com').run()

            println 'Rankings are not fetched: download them from fantasypros by hand. See docs/DATA.md.'
        } catch(Exception e) { e.printStackTrace() }
    }
}
