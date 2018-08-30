package ff.run

import ff.fetch.mfl.MflDataRefresh

class DataRefresh {
    public static void main(String[] args) {
        new MflDataRefresh(2018, 48571, 'www53.myfantasyleague.com').run()
    }
}
