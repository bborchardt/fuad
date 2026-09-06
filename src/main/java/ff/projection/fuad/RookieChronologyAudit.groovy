package ff.projection.fuad

/** Inventory only: validates the season boundary, not historical snapshot provenance. */
class RookieChronologyAudit {
    static int outcomeSeason(RookieBacktest.Observation row) { row.draftClass.toInteger() + row.year - 1 }

    static List<RookieBacktest.Observation> availableBefore(List<RookieBacktest.Observation> rows, int cutoff) {
        rows.findAll { outcomeSeason(it) < cutoff }
    }

    static List<Map> audit(List<RookieBacktest.Observation> observations) {
        observations*.draftClass.unique().sort().collectMany { draft ->
            int cutoff = draft.toInteger()
            def priorClasses = observations.findAll { it.draftClass.toInteger() < cutoff }
            def available = availableBefore(observations, cutoff)
            (1..5).collect { year ->
                def donors = available.findAll { it.position == 'QB' && it.overallRank <= 50 && it.year == year }
                [cutoff: cutoff, year: year, trainingClasses: donors*.draftClass.unique().size(),
                 trainingQbs: donors.size(), latestTrainingSeason: donors ? donors.collect { outcomeSeason(it) }.max() : null,
                 futureRowsInPriorClasses: priorClasses.count { it.position == 'QB' && it.overallRank <= 50 &&
                         it.year == year && outcomeSeason(it) >= cutoff },
                 evaluationQbs: observations.count { it.draftClass == draft && it.position == 'QB' &&
                         it.overallRank <= 50 && it.year == year }]
            }
        }
    }

    static void main(String[] args) {
        if (args.size() > 1) throw new IllegalArgumentException('Usage: rookie_chronology_audit.sh [output-directory]')
        def rows = audit(RookieBacktest.observations())
        def columns = 'cutoff year trainingClasses trainingQbs latestTrainingSeason futureRowsInPriorClasses evaluationQbs'.tokenize()
        File directory = new File(args ? args[0] : 'reports/fuad/chronology-audit')
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create $directory")
        new File(directory, 'coverage.tsv').withWriter('UTF-8') { writer ->
            writer.println(columns.join('\t'))
            rows.each { row -> writer.println(columns.collect { row[it] == null ? '' : row[it] }.join('\t')) }
        }
        println "Season-boundary inventory written to ${directory.absolutePath}; snapshot availability is NOT verified."
    }
}
