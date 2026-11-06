package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Die Antworten des eigenen Backends des Laufs `make eigenes-backend` (`EigenesBackendServer.kt`), ohne Server: der
 * Referenzclient-Lauf liest dieselben Tabellen, und dieser Test hält fest, was das Backend je Text liefert.
 */
internal class EigenesBackendTest {
    private val log = mutableListOf<String>()
    private val backend = EigenesBackend { log += it }

    private fun events(sql: String): List<QueryEvent> = runBlocking {
        backend.execute(QueryRequest("s", "r", sql, emptyList(), null)).events.toList()
    }

    private fun failureCode(sql: String): String = assertIs<QueryEvent.Failure>(events(sql).single()).code

    /**
     * `eigen` liefert zwei Zeilen, `leer` die Spalten ohne Zeile.
     *
     * @spec SPEC-059
     */
    @Test
    fun `ein gefuelltes und ein leeres Ergebnis`() {
        val filled = events("SELECT Id, Name FROM eigen")
        assertEquals(2, filled.count { it is QueryEvent.Row })
        assertEquals(QueryEvent.ResultEnd(2), filled.last())
        val empty = events("SELECT Id, Name FROM leer")
        assertEquals(2, assertIs<QueryEvent.ResultStart>(empty.first()).columns.size)
        assertEquals(listOf(QueryEvent.ResultEnd(0)), empty.drop(1))
    }

    /**
     * `fehler` endet am Start mit `TDS_BACKEND_ERROR`, `fehler_nach_zeilen` nach den Zeilen, `wirft` wirft aus
     * `execute`, ein fremder Text ergibt `TDS_UNSUPPORTED_SQL`; der Text einer Ausnahme steht nicht in der Ausgabe.
     *
     * @spec SPEC-059
     */
    @Test
    fun `die Fehlerpfade des Backends`() {
        assertEquals("TDS_BACKEND_ERROR", failureCode("SELECT Id, Name FROM fehler"))
        val late = events("SELECT Id, Name FROM fehler_nach_zeilen")
        assertEquals(OWN_BACKEND_ROWS_BEFORE_FAILURE, late.count { it is QueryEvent.Row })
        assertIs<QueryEvent.Failure>(late.last())
        assertFailsWith<IOException> { events("SELECT Id, Name FROM wirft") }
        assertEquals("TDS_UNSUPPORTED_SQL", failureCode("DELETE FROM eigen"))
        assertEquals(4, log.size)
        assertEquals(false, log.any { it.contains("Geheimnis") })
    }
}
