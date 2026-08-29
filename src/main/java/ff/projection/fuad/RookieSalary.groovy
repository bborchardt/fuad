package ff.projection.fuad

import ff.load.util.LoadUtils

import java.math.RoundingMode

/**
 * What a rookie costs, which the league sets by rule and not by bidding.
 *
 * Bylaw 8.3: every position has a baseline, being the salary it would command as the first pick of the
 * draft, and the baseline decays by a fifth for <b>every pick already made</b> — not every pick at that
 * position. So the price falls down the board rather than down a position's own queue, and by the third
 * round it has fallen through the floor and every rookie costs the minimum dollar.
 *
 * <b>The baselines are salaries from a fixed rank, not from a formula.</b> The fifteenth quarterback, the
 * twentieth running back, the thirty fifth receiver, the fifteenth tight end and the fifteenth kicker, as
 * paid at the previous season's trading deadline. That depth is where the league's own churn lives, which
 * is why this rule and the franchise tag disagree about which snapshot they are read from: the tag averages
 * the top five, and nobody in a league's top five changes hands between week 12 and the end of the season.
 * The twentieth running back does. Reading 2025 at the end of the year instead of at the deadline puts the
 * baseline at 9 rather than 7, and moves every running back taken in 2026.
 *
 * <b>The rule reproduces the league's own record exactly.</b> Against all 337 picks that were kept to week
 * 1, this returns the salary actually paid for 330, every miss being one dollar at running back in 2018 and
 * 2019 — those two years' baseline was entered as 5 where the deadline rosters hold 4. See
 * {@code RookieSalarySpec} and docs/fuad/LEAGUE_RULES.md.
 *
 * What it costs is the smaller half of what a pick is worth. See {@code RookieValuation} for the other.
 */
class RookieSalary {

    /**
     * The rank at each position whose salary is that position's baseline, from bylaw 8.3.1.
     *
     * The spread is the rule's own judgement about how deep a position runs, and it is a wide one: a
     * receiver's baseline is read 35 salaries down where a quarterback's is read 15. In a ten team league
     * that is roughly the third receiver on a roster against the second quarterback.
     */
    static final Map<String, Integer> BASELINE_RANK =
            [QB: 15, RB: 20, WR: 35, TE: 15, PK: 15].asImmutable() as Map<String, Integer>

    /** What each pick already made takes off the baseline, from bylaw 8.3.2. */
    static final BigDecimal RETAINED_PER_PICK = 0.8g

    /** No contract in this league is written for less, whatever the formula says. */
    static final int MINIMUM_SALARY = 1

    /**
     * The baseline at each position for a draft held before the given season.
     *
     * @param deadlineRosters  the previous season's rosters as they stood at the week 12 trading deadline
     * @param priorSeasonPlayers  that same season's player list, so a position is read from the year it was paid in
     */
    static Map<String, Integer> baselines(Map deadlineRosters, Map priorSeasonPlayers) {
        Map<String, List<BigDecimal>> salariesByPosition =
                RosterSalaries.byPosition(deadlineRosters, priorSeasonPlayers)

        BASELINE_RANK.collectEntries { String position, Integer rank ->
            List<BigDecimal> salaries = salariesByPosition[position]
            [(position): baseline(salaries, rank)]
        } as Map<String, Integer>
    }

    /**
     * The salary at a given depth, or the minimum where the league does not roster that many.
     *
     * <b>A position thinner than its own baseline rank is not an error.</b> Ten teams have never carried 35
     * receivers between them in some seasons and have never carried 15 kickers in any, and the bylaw says
     * nothing about it. Falling to the minimum is the reading that agrees with the record: every kicker ever
     * drafted here has cost a dollar.
     */
    static int baseline(List<BigDecimal> salaries, int rank) {
        salaries != null && salaries.size() >= rank ?
                salaries[rank - 1].setScale(0, RoundingMode.HALF_UP).intValueExact() : MINIMUM_SALARY
    }

    /**
     * What the pick after {@code picksAlreadyMade} costs at a position with this baseline.
     *
     * The exponent counts picks made before this one, so the first pick of the draft pays the baseline
     * itself. Bylaw 8.3.4 works the example: a running back taken fourth overall against a baseline of 22
     * costs {@code 22 * 0.8^3}, which is 11.
     */
    static int salary(int baseline, int picksAlreadyMade) {
        if (picksAlreadyMade < 0) {
            throw new IllegalArgumentException("picksAlreadyMade must not be negative, was $picksAlreadyMade")
        }
        BigDecimal decayed = baseline * RETAINED_PER_PICK.pow(picksAlreadyMade)
        Math.max(MINIMUM_SALARY, decayed.setScale(0, RoundingMode.HALF_UP).intValueExact())
    }

    /**
     * Every pick of a draft priced, as overall pick to position to salary.
     *
     * The whole board rather than one pick, because what a pick costs is a function of where it sits and
     * nothing else about it: the same rookie is a different price at 1.01 and at 2.01, and a team weighing a
     * trade needs the column and not the cell.
     */
    static Map<Integer, Map<String, Integer>> board(Map<String, Integer> baselines, int picks) {
        (1..picks).collectEntries { int overall ->
            [(overall): baselines.collectEntries { String position, Integer baseline ->
                [(position): salary(baseline, overall - 1)]
            }]
        } as Map<Integer, Map<String, Integer>>
    }

    /** The baselines coming into a season's rookie draft, read from the previous season's deadline. */
    static Map<String, Integer> baselinesFor(String season) {
        String prior = ((season as int) - 1) as String
        baselines(LoadUtils.loadJsonResource(LoadUtils.mflDeadlineRostersResourcePath(prior)) as Map,
                LoadUtils.loadJsonResource(LoadUtils.mflPlayersResourcePath(prior)) as Map)
    }
}
