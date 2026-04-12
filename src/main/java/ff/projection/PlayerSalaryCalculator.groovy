package ff.projection

import ff.data.fuad.FuadPlayer
import groovy.transform.Immutable

class PlayerSalaryCalculator {

    private static final Map<String, PositionParameters> POSITIONS = [
            QB: new PositionParameters(57.00202, -2.004908, 0.01371272, 50),
            RB: new PositionParameters(77.5353, -2.589711, 0.02066588, 80),
            WR: new PositionParameters(76.8435, -1.862036, 0.01100244, 110),
            TE: new PositionParameters(44.87413, -2.805514, 0.0431573, 40),
            PK: new PositionParameters(6.3, -1.782143, 0.1607143, 20)
    ].asImmutable()

    private static final double INFLATION_RATE = 1


    static String projectedSalary(FuadPlayer player) {
        return projectedSalary(player.player.position, player.redraftRank?.positionRank ?: 999, player.rookie) ?: ''
    }

    static int projectedSalary(String position, int positionRank, boolean rookie) {
        PositionParameters params = POSITIONS[position]
        if (positionRank >= params.draftableCutoff) {
            return 0
        } else if (rookie) {
            return 1
        } else {
            return Math.max(1, INFLATION_RATE *
                    (params.coefficient1 + params.coefficient2 * positionRank + params.coefficient3 * positionRank * positionRank) as int
            )
        }
    }

    @Immutable
    private static class PositionParameters {
        double coefficient1
        double coefficient2
        double coefficient3
        int draftableCutoff
    }
}
