package ff.load.fuad

import ff.data.fuad.RookiePick
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The nine drafts the league has held, as recovered from the league site.
 *
 * <b>This is a test about data collection as much as about loading.</b> Seven of the nine drafts were kept
 * in this repository as empty slots for years, because the export is pulled before the draft is held, and
 * nothing noticed: a draft with no players in it parses perfectly. These counts are what would have caught
 * it, so they are asserted per season rather than in total.
 */
class RookieDraftHistorySpec extends Specification {

    @Unroll
    def "#season held #picks picks over #rounds rounds, making #made and keeping #kept"() {
        given:
        List<RookiePick> drafted = RookieDraftHistory.picks(season)

        expect: 'every pick that was made names a player, which is the whole reason the file was refetched'
        drafted.size() == picks
        drafted.count { it.made } == made
        drafted.findAll { it.made }.every { it.playerId && it.playerName && it.position }

        and: 'the rounds the league drafted that year'
        drafted*.round.max() == rounds

        and: 'and what survived to week 1, bylaw 12.2 letting the rest go for nothing'
        drafted.count { it.kept } == kept

        where:
        season | picks | made | rounds | kept
        '2017' | 43    | 42   | 4      | 33
        '2018' | 40    | 40   | 4      | 39
        '2019' | 40    | 40   | 4      | 38
        '2020' | 40    | 40   | 4      | 39
        '2021' | 40    | 40   | 5      | 38
        '2022' | 40    | 40   | 5      | 38
        '2023' | 50    | 50   | 5      | 46
        '2024' | 55    | 55   | 5      | 52
        '2025' | 50    | 50   | 5      | 47
    }

    /**
     * The overall pick is the position in the file, and bylaw 8.3 prices off it.
     *
     * The order does not reverse — bylaw 8.1 has teams picking worst to first in every round — so a team's
     * slot is the same number in each of them, and the overall pick is the round and the slot read together.
     * Getting this backwards would price every second round pick as though the draft ran the other way.
     */
    def "numbers picks across the draft in the order they were made"() {
        given:
        List<RookiePick> drafted = RookieDraftHistory.picks('2025')

        expect:
        drafted*.overall == (1..50).toList()

        and: 'ten teams, so the first pick of round two is the eleventh'
        drafted.find { it.round == 2 && it.pick == 1 }.overall == 11
    }

    def "a pick waived before the season carries no salary rather than a zero"() {
        expect:
        RookieDraftHistory.picksBySeason().values().flatten()
                .every { RookiePick pick -> pick.kept ? pick.salary >= 1 : pick.salary == null }
    }

    /**
     * The one slot in nine drafts that nobody used.
     *
     * 2017's 4.04 was skipped by the commissioner, and the export writes four dashes where a player id goes.
     * It is kept as a row because the slot existed and was owned: dropping it would renumber every pick
     * after it, and the overall pick number is exactly what bylaw 8.3 charges a salary off.
     */
    def "keeps a skipped pick as a slot with nobody in it"() {
        given:
        RookiePick skipped = RookieDraftHistory.picks('2017').find { !it.made }

        expect:
        skipped.overall == 36
        skipped.round == 4
        skipped.franchiseId == '0002'
        skipped.playerId == null
        !skipped.kept
    }

    /**
     * The rookie draft is where the expansion years show up, and they show up as extra picks.
     *
     * 2017 carries three over four rounds of ten and 2024 five over five rounds of ten, both of them the
     * commissioner handing a new franchise stock. Anything reading rounds times teams gets those two years
     * wrong. See docs/fuad/DATA.md.
     */
    def "carries the expansion picks rather than assuming rounds times teams"() {
        expect:
        RookieDraftHistory.picks('2017').size() == 43
        RookieDraftHistory.picks('2024').size() == 55
    }
}
