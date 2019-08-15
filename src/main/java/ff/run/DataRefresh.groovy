package ff.run

import ff.fetch.mfl.MflDataRefresh

class DataRefresh {
    static void main(String[] args) {
        new MflDataRefresh(2019, 48571, 'www53.myfantasyleague.com').run()
    }
}
