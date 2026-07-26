package ff.print.fuad

import ff.data.fuad.FuadMatchup

class FuadSchedulePrinter {

    private final List<FuadMatchup> matchups

    FuadSchedulePrinter(List<FuadMatchup> matchups) {
        this.matchups = matchups
    }

    void print() {
        matchups.sort { it.week }.each { m ->
            println "${String.format('%02d', m.week)},$m.franchiseId1,$m.franchiseId2"
        }
    }
}
