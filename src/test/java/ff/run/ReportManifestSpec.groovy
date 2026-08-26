package ff.run

import ff.run.ModelHistory
import spock.lang.Specification
import spock.lang.TempDir

/**
 * The stamp every other check is founded on, asserted directly.
 *
 * {@code StrategyCheck} refuses to hold a plan to a board the model has moved past, and {@code DocsCheck}
 * refuses to hold the documentation to figures an older model wrote. Both of those refusals are this class
 * answering two questions — which model wrote a file, and has the model moved since — and neither had a
 * spec. What is asserted here is the reading and writing of the manifest, which is deterministic; the git
 * questions are exercised against this repository, which is the only place they mean anything.
 */
class ReportManifestSpec extends Specification {

    @TempDir
    File temp

    def "stamps each report type separately, so one run does not erase another's provenance"() {
        when: 'the salaries board is written, and the teams report an hour later'
        ReportManifest.stamp(temp, ['salaries'])
        ReportManifest.stamp(temp, ['teams'])

        then: 'both are recorded, rather than the second overwriting the first'
        ReportManifest.read(temp).keySet() == ['salaries', 'teams'] as Set
    }

    def "rewrites the stamp of a type that is regenerated"() {
        given:
        ReportManifest.stamp(temp, ['salaries'])
        String first = ReportManifest.read(temp).salaries.generated

        when: 'the same report is written again'
        Thread.sleep(1100)
        ReportManifest.stamp(temp, ['salaries'])

        then: 'there is still one line for it, carrying the later time'
        ReportManifest.read(temp).size() == 1
        ReportManifest.read(temp).salaries.generated != first
    }

    def "reads back what it wrote, past the comment header"() {
        when:
        ReportManifest.stamp(temp, ['salaries'], 'figures_refresh.sh')

        then:
        new File(temp, ReportManifest.FILE_NAME).readLines().first().startsWith('#')
        new File(temp, ReportManifest.FILE_NAME).readLines().first().contains('figures_refresh.sh')
        ReportManifest.read(temp).salaries.type == 'salaries'
    }

    def "reports a directory that has never been stamped as holding nothing"() {
        expect: 'empty rather than null, there being no manifest to be wrong about'
        ReportManifest.read(new File(temp, 'never-written')) == [:]
    }

    def "separates the sha from the mark that says the tree was dirty"() {
        given:
        new File(temp, ReportManifest.FILE_NAME).text =
                "# header\nsalaries 59b4f91-dirty 2026-08-17T01:24:03Z\nteams e983730 2026-08-17T20:25:47Z\n"

        when:
        Map<String, ReportManifest.Stamp> stamps = ReportManifest.read(temp)

        then: 'a dirty stamp still names its commit, which is what a message has to quote back'
        stamps.salaries.dirty
        stamps.salaries.sha() == '59b4f91'

        and:
        !stamps.teams.dirty
        stamps.teams.sha() == 'e983730'
    }

    /**
     * The question the checks actually ask, and the reason it is not an equality test against HEAD: a
     * generated file is stamped with HEAD as it was written and then committed, so its stamp names the
     * parent of the commit holding it forever after.
     */
    def "says the model has not moved across a commit that touched no source"() {
        expect: 'this spec is not under src/main, so HEAD against itself moved nothing'
        ReportManifest.modelMovedSince('HEAD') == false
    }

    def "says the model has moved across a commit the model has moved since"() {
        expect: 'taken from this repository at run time, since a sha written down here goes stale silently'
        ReportManifest.modelMovedSince(ModelHistory.supersededModel())
    }

    def "declines to answer for a sha this repository does not have"() {
        expect: 'null rather than false — a manifest naming an unknown commit is broken, not up to date'
        ReportManifest.modelMovedSince('0000000') == null
        ReportManifest.modelMovedSince(null) == null
    }

    /**
     * <b>A git that cannot answer counts as dirty</b>, which is the branch that used to read as clean.
     *
     * That branch is not reachable from here: {@code git} inherits the JVM's working directory, and this
     * suite runs inside the repository, so the command always answers. What can be asserted is the other
     * half — that a working tree git <i>does</i> answer for is reported as whatever git said, and not
     * inverted — and that {@link ReportManifest#currentModel} marks the sha to match.
     */
    def "reports the working tree the same way git does"() {
        given:
        Process status = new ProcessBuilder('git', 'status', '--porcelain', '--', 'src/main', 'pom.xml').start()
        String changed = status.inputStream.text.trim()
        status.waitFor()

        expect:
        ReportManifest.modelIsDirty() == !changed.isEmpty()

        and: 'and the stamp a report would carry says the same thing'
        ReportManifest.currentModel().endsWith('-dirty') == !changed.isEmpty()
    }
}
