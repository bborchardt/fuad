package ff.run

import ff.fetch.mfl.MflDataRefresh

class DataRefresh {
    static void main(String[] args) {
        int year = 0
        try {
            year = Integer.parseInt(args[0])
            new MflDataRefresh(year, 48571, 'api.myfantasyleague.com').run()
        } catch(Exception e) { e.printStackTrace() }
    }
}
