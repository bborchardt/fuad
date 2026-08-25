package ff.league

import ff.load.nflverse.ScoringRules
import ff.projection.StarterRequirements
import spock.lang.Specification

/**
 * Two leagues, and nothing that assumes there is one.
 *
 * These are cheap assertions about configuration, which is the point: Groovy resolves a static constant at
 * run time, so renaming one and missing a reference compiles perfectly and fails only when something runs.
 * The scoring differences in particular produce numbers rather than errors when applied to the wrong league,
 * so they have to be asserted rather than noticed.
 */
class LeagueSpec extends Specification {

    def "the two leagues score the same week differently, and neither is an error"() {
        given: 'a quarterback week: 267 passing yards, two touchdowns, one interception'
        Map<String, String> week = [position: 'QB', passing_yards: '267', passing_tds: '2',
                                    passing_interceptions: '1']

        expect: 'the dynasty league truncates at a point per thirty yards -- 8, not 8.9'
        League.FUAD.scoring.score(week) == 4.5 * 2 + 8 - 1

        and: 'Greenfield pays the remainder at a point per twenty five, and six for a passing touchdown'
        League.GREENFIELD.scoring.score(week) == 6.0 * 2 + 267 / 25 - 1
    }

    def "Greenfield pays a full point per reception at every position, with no tight end premium"() {
        expect:
        League.GREENFIELD.scoring.score([position: position, receptions: '6']) == 6.0

        where:
        position << ['WR', 'RB', 'TE']
    }

    def "the dynasty league pays its tight ends double what it pays everyone else"() {
        expect:
        League.FUAD.scoring.score([position: 'TE', receptions: '6']) == 6.0
        League.FUAD.scoring.score([position: 'WR', receptions: '6']) == 3.0
    }

    def "Greenfield's field goals stop at five points, where the dynasty league's keep climbing"() {
        expect:
        League.GREENFIELD.scoring.fieldGoal(62) == 5.0
        League.FUAD.scoring.fieldGoal(62) == 6.0
    }

    def "Greenfield starts eight modelled slots of a nine slot lineup, one of them flex"() {
        given:
        StarterRequirements requirements = League.GREENFIELD.requirements()

        expect: 'QB, RB, RB, WR, WR, TE, K are fixed, leaving exactly one flex from RB, WR or TE'
        requirements.perTeamStarters() == 8
        requirements.perTeamMinimums() == [QB: 1, RB: 2, WR: 2, TE: 1, PK: 1]
        requirements.perTeamMinimums().values().sum() == 7
        requirements.perTeamMaximums() == [QB: 1, RB: 3, WR: 3, TE: 2, PK: 1]
    }

    def "the defence's slot is left out rather than handed to a flex that cannot fill it"() {
        expect: 'nine are started, but only eight can be modelled, and the ninth is absent not reassigned'
        League.GREENFIELD.startersPerTeam == 8
        !League.GREENFIELD.scoredPositions.contains('DST')
        !League.GREENFIELD.starterMaximums.containsKey('DST')
    }

    def "the dynasty league refuses to name a lineup, having started two different ones"() {
        when:
        League.FUAD.requirements()

        then: 'read per season from that season\'s league.json, never guessed at from here'
        IllegalStateException e = thrown()
        e.message.contains('no fixed lineup')

        and:
        !League.FUAD.hasFixedLineup()
        League.GREENFIELD.hasFixedLineup()
    }

    def "quarterback replacement is fixed at fifteen however the flex falls"() {
        given: 'plausible descending points, the flex free to go wherever it likes'
        Map<String, List<BigDecimal>> points = ['QB', 'RB', 'WR', 'TE', 'PK'].collectEntries { String position ->
            [(position): (1..60).collect { (200 - it * 2) as BigDecimal }]
        }

        when:
        Map<String, Integer> started = League.GREENFIELD.requirements().startersByPosition(points)

        then: 'quarterback is capped at one a team, so no flex can reach it and the count cannot move'
        started.QB == 14
        started.PK == 14

        and: 'the flex goes among the three positions that can take it, and every slot is filled'
        started.values().sum() == 8 * 14
        started.RB + started.WR + started.TE == 84

        and: 'which is the whole quarterback difference between the two leagues: replacement at rank 15,'
        'against rank 21 under superflex, where twenty of about fifty usable starters are started'
        started.QB + 1 == 15
    }

    def "a league names its own scoring rather than borrowing whichever set is current"() {
        expect:
        League.FUAD.scoring.is(ScoringRules.FUAD_2026)
        League.GREENFIELD.scoring.is(ScoringRules.GREENFIELD)
    }
}
