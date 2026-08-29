package ff.load.fuad

import ff.data.RealisedSeason
import ff.data.fantasypros.FpRankedPlayer
import ff.league.League
import ff.load.RealisedSeasons
import ff.load.fantasypros.FantasyProsLoader
import ff.load.util.LoadUtils
import ff.projection.PointsCurve

/**
 * What a rookie has gone on to score, a contract year at a time.
 *
 * <b>A rookie is not priced like a free agent because he is not bought like one.</b> An auction salary buys
 * one season, so the board levels one season and stops. A rookie pick buys up to five at a salary the league
 * fixes at the moment he is drafted — a dollar, for all but the first few picks of a strong class — so what
 * he is worth in his fourth year is not a footnote to the decision, it is most of it. That is the whole of
 * why this exists beside {@link ff.load.fuad.FuadValuationLoader} rather than inside it.
 *
 * <b>It is the same levelling, run five times against a shifted ranking.</b> {@link RealisedSeasons#byRank}
 * scores whichever ranking it is handed against a season's statistics, so asking what a rookie is worth in
 * his third year is asking it to score the class of three years ago against this season. Nothing about the
 * curve, the rate and availability split, the smoothing or the outcome spread needs to know that the rank it
 * is levelling is a rookie's rather than a veteran's. Five curves come out where one went in.
 *
 * <b>The later years rest on fewer classes, and they say so.</b> Nine rookie classes have a first season in
 * the collected record and only five have a fifth, so {@link #classesBehind} falls from nine to five as the
 * contract runs. It is reported everywhere the fifth year is, because a reader has no other way to tell a
 * level built from 45 seasons from one built from 25.
 *
 * The ranking is FantasyPros' rookie consensus, which orders a class the way the redraft consensus orders a
 * season, and rookies are levelled at their <b>positional</b> rookie rank: rookie RB1 is a thing that
 * happens once a year and can be pooled across classes, where "the fourth rookie taken" is a different
 * animal in a strong class and a weak one.
 *
 * See docs/fuad/PROJECTION.md.
 */
class RookieSeasons {

    /** The longest contract the league writes, from bylaw 2.2, and so the last year worth levelling. */
    static final int CONTRACT_YEARS = 5

    private final League league
    private final Map<Integer, PointsCurve> curvesByContractYear = [:]

    RookieSeasons() {
        this(League.FUAD)
    }

    RookieSeasons(League league) {
        this.league = league
    }

    /**
     * The curve for one year of a rookie contract, his first being year one.
     *
     * Built once each and held, since each one restates nine seasons of statistics to make it.
     */
    PointsCurve curve(int contractYear) {
        requireContractYear(contractYear)
        curvesByContractYear.computeIfAbsent(contractYear) { int year ->
            PointsCurve.of(RealisedSeasons.byRank(league, { String season -> classIn(season, year) }))
        }
    }

    /** Every contract year's curve, in order. */
    Map<Integer, PointsCurve> curves() {
        (1..CONTRACT_YEARS).collectEntries { [(it): curve(it)] } as Map<Integer, PointsCurve>
    }

    /**
     * How many rookie classes the record can observe in a given contract year.
     *
     * Nine in the first year and five in the fifth, since a class drafted in 2022 has no fifth season yet.
     * This is the honest denominator behind every level {@link #curve} returns for that year.
     */
    int classesBehind(int contractYear) {
        requireContractYear(contractYear)
        league.seasons.count { String season -> classIn(season, contractYear) } as int
    }

    /**
     * The class that is in its {@code contractYear} during {@code season}, or nothing where it predates the
     * collected rankings.
     *
     * A season is evidence about a class that far behind it: 2025's statistics say what the class of 2021 was
     * worth in its fifth year, and nothing at all about a class of 2016 the rankings do not reach.
     */
    private static Collection<FpRankedPlayer> classIn(String season, int contractYear) {
        String drafted = ((season as int) - (contractYear - 1)) as String
        String rankings = LoadUtils.fpRookieRankingsPprResourcePath(drafted)
        LoadUtils.hasResource(rankings) ?
                new FantasyProsLoader().loadRankedPlayers(rankings).values() : []
    }

    private static void requireContractYear(int contractYear) {
        if (contractYear < 1 || contractYear > CONTRACT_YEARS) {
            throw new IllegalArgumentException(
                    "A contract runs one to $CONTRACT_YEARS years, so there is no year $contractYear.")
        }
    }
}
