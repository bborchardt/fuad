package ff.load.util

import groovy.transform.Immutable
import spock.lang.Specification
import spock.lang.Unroll

class LoadUtilsSpec extends Specification {

    @Unroll
    def "convert to first name first"() {
        expect:
        LoadUtils.nameFirstThenLast(name) == normalized

        where:
        name              | normalized
        'Bob Barker'      | 'Bob Barker'
        'Barker, Bob'     | 'Bob Barker'
        'Barker Jr., Bob' | 'Bob Barker Jr.'
        'Bob Barker Jr.'  | 'Bob Barker Jr.'
    }

    @Unroll
    def "convert to last name first"() {
        expect:
        LoadUtils.nameLastThenFirst(name) == normalized

        where:
        name              | normalized
        'Bob Barker'      | 'Barker, Bob'
        'Barker, Bob'     | 'Barker, Bob'
        'Barker Jr., Bob' | 'Barker Jr., Bob'
        'Bob Barker Jr.'  | 'Barker Jr., Bob'
    }

    @Unroll
    def "tokenize name"() {
        expect:
        LoadUtils.tokenizeFirstLast(name) == [first, last]

        where:
        name              | first | last
        'Bob Barker'      | 'Bob' | 'Barker'
        'Barker, Bob'     | 'Bob' | 'Barker'
        'Barker Jr., Bob' | 'Bob' | 'Barker Jr.'
        'Bob Barker Jr.'  | 'Bob' | 'Barker Jr.'
    }

    @Unroll
    def "is name match"() {
        expect:
        LoadUtils.isNameMatch(n1, n2, 3) == result

        where:
        n1             | n2              | result
        'Bob Jones'    | 'Bobby Jonson'  | true
        'Bobby Jonson' | 'Bob Jones'     | true
        'Al Morris'    | 'Albert Mo'     | true
        'Allen Jones'  | 'Albert Jones'  | false
        'Jimmy Jones'  | 'Jimmy Johnson' | false
    }

    @Unroll
    def "starts with"() {
        expect:
        LoadUtils.startsWith(n1, n2, numChars) == result

        where:
        n1        | n2        | numChars | result
        'Bob'     | 'Bobby'   | 8        | true
        'Bob'     | 'Bobby'   | 3        | true
        'Bob'     | 'Bobby'   | 2        | true
        'Bobby'   | 'Bob'     | 8        | true
        'Bobby'   | 'Bob'     | 3        | true
        'Bobby'   | 'Bob'     | 2        | true
        'Roberta' | 'Roberto' | 6        | true
        'Roberta' | 'Roberto' | 7        | false
        'Frank'   | 'Jimmy'   | 1        | false
        'Frank'   | 'Jimmy'   | 0        | true
    }
}
