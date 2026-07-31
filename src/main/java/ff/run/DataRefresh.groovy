package ff.run

import ff.fetch.fantasypros.FantasyProsDataRefresh
import ff.fetch.mfl.MflDataRefresh

class DataRefresh {
    static void main(String[] args) {
        int year = 0
        try {
            year = Integer.parseInt(args[0])
            new MflDataRefresh(year, 48571, 'api.myfantasyleague.com').run()

            String apiKey = System.getenv('FANTASYPROS_API_KEY')
            if (apiKey) {
                new FantasyProsDataRefresh(year, apiKey, 'api.fantasypros.com').run()
            } else {
                System.err.println 'Skipping fantasypros refresh: FANTASYPROS_API_KEY is not set'
            }
        } catch(Exception e) { e.printStackTrace() }
    }
}
