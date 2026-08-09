package ff.run

import ff.fetch.mfl.MflRosterSnapshotRefresh
import ff.fetch.mfl.RosterSnapshot

/**
 * Collect post draft and end of year rosters for the given completed seasons, for backfilling seasons whose
 * pre draft data was pulled long ago. Unlike {@link DataRefresh} this writes nothing but the snapshot files,
 * so it cannot overwrite a pre draft snapshot with today's state.
 */
class RosterSnapshotRefresh {
    static void main(String[] args) {
        if (!args) {
            System.err.println 'Usage: RosterSnapshotRefresh <year> [<year> ...]'
            Runtime.getRuntime().exit(-1)
        }
        args.each { String year ->
            RosterSnapshot.values().each { RosterSnapshot snapshot ->
                try {
                    new MflRosterSnapshotRefresh(Integer.parseInt(year), 48571, 'api.myfantasyleague.com', snapshot).run()
                } catch (Exception e) {
                    System.err.println "Skipping $year $snapshot: $e.message"
                }
            }
        }
    }
}
