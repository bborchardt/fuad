package ff.print.fuad

import ff.data.Draft
import ff.data.Player
import ff.data.Rank
import ff.data.fuad.FuadData
import ff.data.fuad.FuadPlayer
import ff.data.mfl.MflData
import ff.data.mfl.MflDraftPick
import ff.data.mfl.MflFranchise
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The rookie sheet, which is two lists side by side: the consensus rookie ranking, and whose pick is next.
 *
 * It prices nothing — rookies are drafted rather than bid on, and the auction board excludes them entirely.
 * What it has to get right is the notation, since the whole sheet is read as "who is available at 2.4".
 */
class FuadRookieDraftPrinterSpec extends Specification {

    private static FuadPlayer rookie(int overall, int positionRank = 1, Draft draft = new Draft(round: 1, pick: 5)) {
        new FuadPlayer(player: new Player(name: "Rookie $overall", position: 'WR', team: 'CIN'),
                rookieRank: new Rank(overallRank: overall, positionRank: positionRank),
                draft: draft, mflId: "p$overall")
    }

    private static MflDraftPick pick(int round, int pick, String owner, String name = 'Some Team') {
        new MflDraftPick(round: round, pick: pick,
                franchise: new MflFranchise(id: '0001', name: name, ownerName: owner, players: []))
    }

    private static List<String> rowsOf(List<FuadPlayer> rookies, List<MflDraftPick> picks) {
        StringWriter out = new StringWriter()
        new FuadRookieDraftPrinter(new FuadData(rookieRanks: rookies,
                mflData: new MflData(draftPicks: picks, franchiseByIdMap: [:], playerByNameMap: [:])))
                .print(new PrintWriter(out))
        out.toString().readLines()
    }

    private static List<String> cellsOf(List<FuadPlayer> rookies, List<MflDraftPick> picks, int row = 0) {
        rowsOf(rookies, picks)[row].split('\t', -1) as List
    }

    /**
     * Overall rank read as a draft slot, ten picks to the round.
     *
     * <b>The round boundary is the whole of the arithmetic.</b> Pick ten of a round is {@code overall % 10
     * == 0}, which has to read as the tenth pick of that round rather than the zeroth of the next, and the
     * round number has to not have advanced yet. Every other rank is unremarkable, so these are the ones
     * asserted.
     */
    @Unroll
    def "reads overall rank #overall as slot #slot"() {
        expect:
        cellsOf([rookie(overall)], [pick(1, 1, 'Brett')])[0] == slot

        where:
        overall || slot
        1       || '1.1'
        9       || '1.9'
        10      || '1.10'   // the last pick of round one, not the zeroth of round two
        11      || '2.1'
        20      || '2.10'
        21      || '3.1'
    }

    def "carries the position and rank together, which is how the position is read"() {
        expect:
        cellsOf([rookie(4, 2)], [pick(1, 1, 'Brett')])[2] == 'WR2'
    }

    def "names where the NFL took him, and says so plainly when it did not"() {
        expect: 'a rookie with a draft slot carries it'
        cellsOf([rookie(1, 1, new Draft(round: 2, pick: 14))], [pick(1, 1, 'Brett')])[3] == '2.14'

        and: 'an undrafted rookie is a question mark rather than a blank that reads as missing data'
        cellsOf([rookie(1, 1, null)], [pick(1, 1, 'Brett')])[3] == '?'
    }

    def "shortens an owner to the name anybody uses at the draft"() {
        expect:
        cellsOf([rookie(1)], [pick(2, 4, 'Brett Borchardt')]).last() == 'Brett'
    }

    def "falls back to the franchise name where no owner is recorded"() {
        expect:
        cellsOf([rookie(1)], [pick(2, 4, null, 'Wolfpack Reloaded')]).last() == 'Wolfpack'
    }

    def "prints a row for every pick, the two lists being different lengths"() {
        expect: 'three picks against one ranked rookie still gives three rows'
        rowsOf([rookie(1)], [pick(1, 1, 'Brett'), pick(1, 2, 'Jeff'), pick(1, 3, 'Chris')]).size() == 3
    }

    /**
     * Every row has to have the same columns, or the sheet stops being a table.
     *
     * The two lists are rarely the same length — the consensus ranks far more rookies than the league holds
     * picks, and a team that traded away picks holds fewer — so the shorter one is padded. A padding row
     * that emits a different number of cells shifts every column to its right, which is silent in a TSV and
     * looks like a data error in whatever reads it.
     */
    def "pads a missing entry to the width it would have taken"() {
        given: 'more ranked rookies than picks, and then more picks than ranked rookies'
        List<String> rowsWithSpareRookies =
                rowsOf([rookie(1), rookie(2)], [pick(1, 1, 'Brett')])
        List<String> rowsWithSparePicks =
                rowsOf([rookie(1)], [pick(1, 1, 'Brett'), pick(1, 2, 'Jeff')])

        expect: 'a spare rookie leaves the pick columns empty and the row the same width'
        rowsWithSpareRookies*.split('\t', -1)*.size().unique().size() == 1

        and: 'and a spare pick leaves the rookie columns empty, likewise'
        rowsWithSparePicks*.split('\t', -1)*.size().unique().size() == 1
    }
}
