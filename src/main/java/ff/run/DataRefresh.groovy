package ff.run

import ff.fetch.fantasypros.FantasyProsDataRefresh
import ff.fetch.mfl.MflDataRefresh
import ff.fetch.mfl.MflRosterSnapshotRefresh
import ff.fetch.mfl.MflTransactionsRefresh
import ff.fetch.mfl.MflWeeklyScoresRefresh
import ff.fetch.mfl.RosterSnapshot

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
            new MflWeeklyScoresRefresh(year - 1, 48571, 'api.myfantasyleague.com').run()

            String apiKey = System.getenv('FANTASYPROS_API_KEY')
            if (apiKey) {
                new FantasyProsDataRefresh(year, apiKey, 'api.fantasypros.com').run()
            } else {
                System.err.println 'Skipping fantasypros refresh: FANTASYPROS_API_KEY is not set'
            }
        } catch(Exception e) { e.printStackTrace() }
    }
}
