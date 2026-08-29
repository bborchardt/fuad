package ff.projection.fuad

import ff.data.fuad.RookiePick
import ff.load.fuad.RookieDraftHistory
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The rookie salary is set by bylaw 8.3 rather than by bidding, so like the franchise tag it can be checked
 * against the league's own record instead of being fitted to it.
 *
 * It comes out exactly right 330 times in 337. See docs/fuad/LEAGUE_RULES.md.
 */
class RookieSalarySpec extends Specification {

    private static List<RookiePick> keptPicks(String season) {
        RookieDraftHistory.picks(season).findAll { it.kept && it.position in RookieSalary.BASELINE_RANK.keySet() }
    }

    def "the first pick of the draft pays the baseline itself"() {
        expect:
        RookieSalary.salary(22, 0) == 22
    }

    def "bylaw 8.3.4 works its own example"() {
        expect: 'a running back taken fourth against a baseline of 22'
        RookieSalary.salary(22, 3) == 11
    }

    @Unroll
    def "a baseline of #baseline decays to #expected by pick #pick"() {
        expect:
        RookieSalary.salary(baseline, pick - 1) == expected

        where:
        baseline | pick | expected
        15       | 1    | 15
        15       | 2    | 12
        15       | 3    | 10
        15       | 5    | 6
        15       | 10   | 2
        15       | 15   | 1
        1        | 1    | 1
        1        | 40   | 1
    }

    def "the decay counts every pick made, not every pick at that position"() {
        expect: 'the second quarterback taken, ninth overall, is priced ninth and not second'
        RookieSalary.salary(20, 8) == RookieSalary.salary(20, 8)
        RookieSalary.salary(20, 8) == 3
        RookieSalary.salary(20, 1) == 16
    }

    def "a position the league rosters fewer of than its baseline rank falls to the minimum"() {
        expect: 'no season has ever carried 35 receivers at a salary worth reading'
        RookieSalary.baseline([50.0g, 20.0g, 3.0g], 35) == 1
        RookieSalary.baseline(null, 15) == 1
    }

    @Unroll
    def "#season baselines are #expected"() {
        expect:
        RookieSalary.baselinesFor(season) == expected

        where:
        season | expected
        '2021' | [QB: 1, RB: 3, WR: 1, TE: 1, PK: 1]
        '2022' | [QB: 1, RB: 4, WR: 1, TE: 1, PK: 1]
        '2023' | [QB: 1, RB: 5, WR: 1, TE: 1, PK: 1]
        '2024' | [QB: 15, RB: 5, WR: 1, TE: 1, PK: 1]
        '2025' | [QB: 12, RB: 15, WR: 2, TE: 1, PK: 1]
        '2026' | [QB: 20, RB: 7, WR: 1, TE: 1, PK: 1]
    }

    /**
     * The rule, run over every pick the league kept, against what it actually charged for him.
     *
     * <b>2018 and 2019 are the only seasons it misses, and it misses them the same way.</b> Both years'
     * running backs were charged off a baseline of 5 where the deadline rosters hold 4, so every running
     * back taken early in those two drafts comes out a dollar light. Seven picks in nine drafts, none of
     * them past a dollar, and no other position or season disagrees at all.
     */
    @Unroll
    def "#season rookie salaries come out at what the league charged: #exact of #total"() {
        given:
        Map<String, Integer> baselines = RookieSalary.baselinesFor(season)
        List<RookiePick> picks = keptPicks(season)

        when:
        int matched = picks.count { RookiePick pick ->
            RookieSalary.salary(baselines[pick.position], pick.overall - 1) == pick.salary
        }

        then:
        picks.size() == total
        matched == exact

        where:
        season | exact | total
        '2018' | 35    | 39
        '2019' | 35    | 38
        '2020' | 39    | 39
        '2021' | 38    | 38
        '2022' | 38    | 38
        '2023' | 46    | 46
        '2024' | 52    | 52
        '2025' | 47    | 47
    }

    def "every miss in the record is one dollar at running back in 2018 or 2019"() {
        when:
        List<String> misses = RookieDraftHistory.PRICED_SEASONS.collectMany { String season ->
            Map<String, Integer> baselines = RookieSalary.baselinesFor(season)
            keptPicks(season).findAll { RookiePick pick ->
                RookieSalary.salary(baselines[pick.position], pick.overall - 1) != pick.salary
            }.collect { RookiePick pick ->
                "$pick.season $pick.position ${pick.salary - RookieSalary.salary(baselines[pick.position], pick.overall - 1)}" as String
            }
        }

        then:
        misses.every { it.endsWith('RB 1') }
        misses.every { it.startsWith('2018') || it.startsWith('2019') }
        misses.size() == 7
    }
}
