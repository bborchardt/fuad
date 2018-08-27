package ff.print.fuad

import groovy.transform.CompileStatic
import groovy.transform.Immutable

@CompileStatic
@Immutable
class PrintableList {
    List list
    Closure printer
}
