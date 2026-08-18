package ff.data

import groovy.transform.CompileStatic
import groovy.transform.Immutable

/**
 * One season a ranked player actually had, split into the two things that make it up.
 *
 * A fantasy season is a rate multiplied by an availability, and they are separately caused: how good a
 * player is when he plays, and how much football he plays. Keeping them apart is what lets the curve say a
 * rank was absent rather than bad, instead of reporting the product and being unable to tell the two apart.
 *
 * A season lost entirely is {@code games = 0}, which carries no rate at all and is not evidence about one.
 * It belongs in the availability half and is counted there.
 */
@CompileStatic
@Immutable
class RealisedSeason {

    /** Points scored over the weeks this league pays for, restated under the rules being priced. */
    BigDecimal points

    /** Games actually played over those weeks. Thirteen is a full season, the fourteenth being the bye. */
    int games

    /** Points per game, or null for a season that never happened and so says nothing about a rate. */
    BigDecimal getRate() { games > 0 ? points / games : null }
}
