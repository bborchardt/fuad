package ff.run

import ff.fetch.mfl.MflDataRefresh

class DataRefresh {
    static void main(String[] args) {
        new MflDataRefresh(2020, 48571, 'api.myfantasyleague.com').run()
    }
}
