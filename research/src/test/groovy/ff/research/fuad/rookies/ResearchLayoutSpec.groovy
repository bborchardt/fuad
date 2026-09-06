package ff.research.fuad.rookies

import spock.lang.Specification

/**
 * The research build against the production build it compiles.
 *
 * research/pom.xml is standalone but not independent: it compiles ../src/main/java itself, so the two
 * builds must agree about Groovy, Spock and the plugins that run them. Nothing else notices when they stop
 * agreeing — the research build keeps passing against a different Groovy than production's, and a study's
 * result is then a result about a compiler nobody is shipping.
 */
class ResearchLayoutSpec extends Specification {

    private static File repository() {
        File directory = new File('.').canonicalFile
        while (directory && !new File(directory, 'research/pom.xml').isFile()) directory = directory.parentFile
        assert directory: 'The research build must run inside the repository it is part of'
        directory
    }

    /** Every version an artifactId is declared at, so a duplicate declaration cannot hide behind the first. */
    private static Map<String, Set<String>> versions(File pom) {
        Map<String, Set<String>> declared = [:].withDefault { [] as Set }
        (pom.getText('UTF-8') =~ /(?s)<artifactId>([^<]+)<\/artifactId>\s*(?:<type>[^<]+<\/type>\s*)?<version>([^<]+)<\/version>/)
                .each { List<String> match -> declared[match[1].trim()] << match[2].trim() }
        declared
    }

    def 'the research build pins every shared dependency and plugin to the production version'() {
        given:
        File repository = repository()
        Map<String, Set<String>> production = versions(new File(repository, 'pom.xml'))
        Map<String, Set<String>> research = versions(new File(repository, 'research/pom.xml'))

        expect:
        production && research
        research.keySet().intersect(production.keySet()).every { research[it] == production[it] }

        and: 'the ones that decide what the studies actually compile and run against'
        ['groovy-bom', 'spock-core', 'gmavenplus-plugin'].every { research[it] && research[it] == production[it] }
    }

    def 'every study the runner offers is a class the runner can load'() {
        given:
        File repository = repository()
        String runner = new File(repository, 'research/run.sh').getText('UTF-8')

        expect: 'each main= line names a class in this package'
        (runner =~ /main=(\w+)/).collect { List<String> match -> match[1] }.toUnique().every { String name ->
            ResearchLayoutSpec.classLoader.loadClass("ff.research.fuad.rookies.$name")
        }

        and: 'and every command the help text advertises has such a line'
        (runner =~ /echo '([^']*)'/).collect { List<String> match -> match[1] }.join(' ')
                .replaceAll(/Studies:|Git records:|,/, ' ').tokenize().every { String command ->
            runner.contains("$command)") || runner.contains("$command|")
        }
    }
}
