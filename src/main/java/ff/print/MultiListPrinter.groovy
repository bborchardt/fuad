package ff.print

import ff.print.fuad.PrintableList

class MultiListPrinter {

    void printLists(PrintableList... lists) {
        int numRows = lists*.list*.size().max()
        (0..numRows-1).each { i ->
            for(int j = 0; j < lists.size(); j++) {
                PrintableList list = lists[j]
                def element = list.list.size() > i ? list.list[i] : null
                list.printer(element)
                if(j < lists.size() - 1)
                print '\t\t'
            }
            println()
        }
    }
}
