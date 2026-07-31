package ff.print.fuad

import ff.data.fuad.FuadMatchup

class FuadSchedulePrinter {

    private final List<FuadMatchup> matchups

    FuadSchedulePrinter(List<FuadMatchup> matchups) {
        this.matchups = matchups
    }

    void print(PrintWriter out) {
        matchups.sort { it.week }.each { m ->
            out.println "${String.format('%02d', m.week)},$m.franchiseId1,$m.franchiseId2"
        }
    }
}
