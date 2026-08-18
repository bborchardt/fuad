package ff.print.fuad

import ff.data.PlayerValuation
import ff.data.fuad.FuadData
import ff.data.mfl.MflFranchise
import ff.data.mfl.MflPlayer
import ff.projection.AuctionValuation
import ff.projection.StarterRequirements

import java.math.RoundingMode

/**
 * What each team brings to the auction: what it has, what it needs, and what it can spend.
 *
 * Deliberately context rather than prices. The auction clears at the highest bidder, not the average one,
 * so the outliers come from a particular team being thin at a position with money to fix it. Team state
 * varies enormously, and how enormously is in docs/figures/&lt;year&gt;/stretch.tsv: MINSTRETCH against
 * MAXSTRETCH for the range, and a row per team season for the cases behind it.
 *
 * It is reported and not priced because it does not predict well enough to price. STRETCHCORR on the same
 * table is the correlation between how stretched a team is and how much of its roster it keeps, and it is
 * near nothing. The situations are real one at a time and invisible on average, so this is the half of the
 * problem worth handing to a human rather than a model.
 */
class FuadTeamContextPrinter {

    private static final List<String> POSITIONS = ['QB', 'RB', 'WR', 'TE', 'PK'].asImmutable()

    /** A full roster, from the league bylaws. See docs/LEAGUE_RULES.md. */
    private static final int MAX_ROSTER = 30

    /**
     * The smallest roster the league allows, from the same bylaw, stated for 2025 and unviolated since 2021.
     *
     * Reported rather than left to the reader because how many players a team <i>must</i> sign is half of
     * what its budget means: a team with two spots to fill and one with nine are not in the same auction
     * even on identical cap space.
     */
    private static final int MIN_ROSTER = 23

    private final FuadData fuadData
    private final List<PlayerValuation> valuations
    private final int salaryCap
    private final Map<String, Integer> lineupMinimums

    FuadTeamContextPrinter(FuadData fuadData, List<PlayerValuation> valuations, int salaryCap,
                           StarterRequirements requirements) {
        this.fuadData = fuadData
        this.valuations = valuations
        this.salaryCap = salaryCap
        this.lineupMinimums = requirements.perTeamMinimums()
    }

    /**
     * Positions a team cannot field a legal lineup at, as `POS:short`.
     *
     * Reported rather than priced, for the same reason the rest of this report is. A kicker is the case
     * that forced it: the statistics this project kept carried no kicking, so no kicker could be levelled and every one of them
     * prices at the minimum bid and adds nothing to any lineup. A team with none would therefore never see
     * the position surface anywhere, and would have to carry "remember to buy a kicker" as knowledge from
     * outside the model — which is exactly what a plan reasoning from the board is not supposed to need.
     *
     * Saying a roster is short at a position needs no curve for it. See docs/STRATEGY.md.
     */
    private String needs(List<MflPlayer> signed) {
        lineupMinimums.collect { String position, int minimum ->
            int held = signed.count { it.player.position == position }
            held < minimum ? "$position:${minimum - held}" : null
        }.findAll { it != null }.join(',')
    }

    void print(PrintWriter out) {
        Map<String, List<PlayerValuation>> expiringByTeam = valuations
                .findAll { it.franchiseId }
                .groupBy { it.franchiseId }

        out.println((['TEAM', 'OWNER', 'ROSTER', 'SIGNED', 'EXPIRING', 'SLOTS', 'ROOKIES', 'MINSIGN',
                      'MAXSIGN', 'NEEDS', 'COMMITTED', 'FREECAP', 'EXPOSURE', 'EXP/CAP', 'TAGS',
                      'TAGCOST'] + POSITIONS.collect { "${it}SIGNED" }).join('\t'))
        fuadData.mflData.franchiseByIdMap.sort { it.key }.each { String id, MflFranchise franchise ->
            List<MflPlayer> signed = franchise.players.findAll { it.contract }
            List<PlayerValuation> expiring = expiringByTeam[id] ?: []
            // Coalesced before the cast, not after: an empty roster sums to null, and null as int throws.
            int committed = (signed.sum { it.contract.salary } ?: 0) as int
            int freeCap = salaryCap - committed
            // What keeping every expiring player would cost: a tag price where one is expected, the market
            // price otherwise. Against free cap it says how much of the roster the budget can actually hold.
            int exposure = (expiring.sum { it.salary } ?: 0) as int
            List<PlayerValuation> tagged = expiring.findAll { it.franchiseTagged }

            // The rookie draft fills spots the auction then does not have to, so it comes off both ends of
            // what a team has to buy. Rounds times one, as the model assumes league-wide; a team that has
            // traded picks away will actually hold more or fewer than this.
            int rookies = AuctionValuation.ROOKIE_ROUNDS

            out.println(([
                    id,
                    franchise.ownerName ?: franchise.name,
                    franchise.players.size(),
                    signed.size(),
                    expiring.size(),
                    Math.max(0, MAX_ROSTER - signed.size()),
                    rookies,
                    Math.max(0, MIN_ROSTER - signed.size() - rookies),
                    Math.max(0, MAX_ROSTER - signed.size() - rookies),
                    needs(signed),
                    committed,
                    freeCap,
                    exposure,
                    freeCap > 0 ? (exposure / freeCap as BigDecimal).setScale(2, RoundingMode.HALF_UP) : '',
                    tagged.size(),
                    tagged.sum { it.salary } ?: 0
            ] + POSITIONS.collect { String position ->
                signed.count { it.player.position == position }
            }).join('\t'))
        }
    }
}
