package ff.print.greenfield

import ff.data.greenfield.KeeperSurplus

import java.math.RoundingMode

/**
 * What each declared keeper is worth against the pick it costs, both ways of reading the pick.
 *
 * {@code SURPLUS} takes the pick at consensus order — whoever the rankings say is next. {@code MEASURED}
 * takes it at what this league has actually left on the board there, over nine of its own drafts. The pair
 * is the answer: nobody drafts in consensus order, and nobody is handed the best player the model can see
 * either. An owner drafting off this board should weigh {@code MEASURED}, because that is the value he is
 * really giving up.
 *
 * <b>These do not add up.</b> Several owners surrendering adjacent picks are each measured against the same
 * next best player, and only one of them could have had him. Every row answers "what do I gain by keeping,
 * if everyone else keeps what they have declared" — which is the question an owner has, and not a valuation
 * of the rule.
 */
class GreenfieldKeeperPrinter {

    private final List<KeeperSurplus> keepers

    GreenfieldKeeperPrinter(List<KeeperSurplus> keepers) {
        this.keepers = keepers
    }

    void print(PrintWriter out) {
        out.println(['OWNER', 'PLAYER', 'POS', 'RANK', 'COSTROUND', 'COSTPICK', 'PRIORROUND', 'ELIGIBLE',
                     'VOR', 'CONSENSUS', 'MEASURED', 'SURPLUS', 'MEASUREDSURPLUS'].join('\t'))
        keepers.sort { a, b ->
            (b.measuredSurplus() ?: b.surplus()) <=> (a.measuredSurplus() ?: a.surplus())
        }.each { KeeperSurplus k ->
            out.println([
                    k.owner, k.player, k.position ?: '', k.positionRank ?: '',
                    k.costRound, k.costPick,
                    k.priorRound ?: 'undrafted',
                    k.eligible ? 'Y' : 'NO',
                    one(k.keeperValue), one(k.alternativeValue), one(k.measuredAlternativeValue),
                    one(k.surplus()), one(k.measuredSurplus()),
            ].join('\t'))
        }
    }

    /** Blank where there is no measurement, since absent and worthless are not the same claim. */
    private static String one(BigDecimal value) {
        value == null ? '' : value.setScale(1, RoundingMode.HALF_UP) as String
    }
}
