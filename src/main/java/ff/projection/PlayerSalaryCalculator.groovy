package ff.projection

import ff.data.fuad.FuadPlayer
import groovy.transform.Immutable

class PlayerSalaryCalculator {

    private static final Map<String, PositionParameters> POSITIONS = [
            QB: new PositionParameters(3, 42, 1.3, 30),
            RB: new PositionParameters(18, 42, 0.8, 70),
            WR: new PositionParameters(10, 94, 0.7, 95),
            TE: new PositionParameters(5, 25, 1, 30),
            PK: new PositionParameters(1, 1, 1, 20)
    ].asImmutable()


    static String projectedSalary(FuadPlayer player) {
        return projectedSalary(player.player.position, player.redraftRank?.positionRank ?: 999, player.rookie) ?: ''
    }

    static int projectedSalary(String position, int positionRank, boolean rookie) {
        PositionParameters params = POSITIONS[position]
        if(positionRank >= params.draftableCutoff) {
            return 0
        } else if(rookie) {
            return 1
        } else if(positionRank <= params.franchiseCutoff) {
            return params.franchiseSalary
        } else {
            return Math.max(
                    1,
                    Math.pow(0.9, params.positionScalingFactor * (positionRank - params.franchiseCutoff)) * params.franchiseSalary
            )
        }
    }

    @Immutable
    private static class PositionParameters {
        int franchiseCutoff
        int franchiseSalary
        double positionScalingFactor
        int draftableCutoff
    }
}
