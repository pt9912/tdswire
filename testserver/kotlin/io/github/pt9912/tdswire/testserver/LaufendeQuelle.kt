package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.simulation.RowSource
import io.github.pt9912.tdswire.simulation.SimColumn
import io.github.pt9912.tdswire.simulation.SimTable
import kotlinx.coroutines.delay

/** Pause in Millisekunden vor jeder Zeile von `laufend`; sie hält die Quelle abbrechbar und die Zeilenrate niedrig. */
internal const val RUNNING_PACE_MILLIS = 5L

/**
 * Die Tabelle `laufend` (`Id`, `Text`): ihre Quelle liefert Zeilen ohne Ende, jede nach [RUNNING_PACE_MILLIS] ms, und
 * endet nur durch den Abbruch der Anfrage (Cancel, Frist, Verbindungsende). Eine Zeit, die ein Test abwartet, gibt es
 * nicht: dass die Anfrage beim Abbruch läuft, folgt aus der Quelle. [onClose] wird beim Schließen jeder geöffneten
 * Quelle gerufen.
 */
internal fun runningTable(onClose: () -> Unit = {}) = SimTable(
    name = "laufend",
    columns = listOf(SimColumn("Id", SqlType.Int32, nullable = false), SimColumn("Text", SqlType.UnicodeText)),
    primaryKey = listOf("Id"),
    rows = {
        object : RowSource {
            private var count = 0

            override suspend fun next(): List<Any?> {
                delay(RUNNING_PACE_MILLIS)
                return listOf(++count, "zeile")
            }

            override fun close() = onClose()
        }
    },
)
