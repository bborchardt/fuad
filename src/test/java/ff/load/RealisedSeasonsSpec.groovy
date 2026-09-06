package ff.load

import ff.data.Player
import ff.data.Rank
import ff.data.fantasypros.FpRankedPlayer
import ff.league.League
import ff.load.nflverse.NflverseStatsLoader
import ff.load.nflverse.NflverseTeamStatsLoader
import spock.lang.Specification

class RealisedSeasonsSpec extends Specification {
    def 'position keeps a nickname-surnamed receiver separate from the Eagles defence'() {
        given:
        GroovyMock(NflverseStatsLoader, global: true)
        GroovyMock(NflverseTeamStatsLoader, global: true)
        NflverseStatsLoader.seasonPoints('2021', _) >> ['Brennan Eagles': 12g]
        NflverseStatsLoader.gamesPlayed('2021') >> ['Brennan Eagles': 2]
        NflverseStatsLoader.played('2021') >> (['Brennan Eagles'] as Set)
        NflverseTeamStatsLoader.seasonPoints('2021', _) >> [PHI: 99g]
        NflverseTeamStatsLoader.gamesPlayed('2021') >> [PHI: 13]
        def receiver = new FpRankedPlayer(new Player('Brennan Eagles', 'DAL', 'WR'), new Rank(1, 1), '7')
        def defence = new FpRankedPlayer(new Player('Philadelphia Eagles', 'PHI', 'DST'), new Rank(2, 1), '14')

        when:
        def realised = RealisedSeasons.byRank(League.GREENFIELD, { [receiver, defence] }, ['2021'])

        then:
        realised.WR[1]*.points == [12g]
        realised.WR[1]*.games == [2]
        realised.DST[1]*.points == [99g]
        realised.DST[1]*.games == [13]
    }

    def 'a nickname-surnamed rookie without statistics remains a zero-game observation'() {
        given:
        GroovyMock(NflverseStatsLoader, global: true)
        NflverseStatsLoader.seasonPoints('2021', _) >> [:]
        NflverseStatsLoader.gamesPlayed('2021') >> [:]
        NflverseStatsLoader.played('2021') >> ([] as Set)
        def receiver = new FpRankedPlayer(new Player('Brennan Eagles', 'DAL', 'WR'), new Rank(1, 1), '7')

        when:
        def realised = RealisedSeasons.byRank(League.FUAD, { [receiver] }, ['2021'])

        then:
        realised.WR[1]*.points == [0g]
        realised.WR[1]*.games == [0]
    }
}
