package io.github.pt9912.tdswire.protocol.error

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Die Namen, die die KDoc von `QueryEvent.Failure.code` und `BulkTargetResult.Rejected.code` nennen, sind die Liste
 * [ErrorMapper.BACKEND_CODES] und `TDS_INTERNAL_ERROR` (die Folge eines anderen Namens). Gelesen werden die Quelltexte
 * im Projekt; Nutzer-Doku und Spezifikation liegen nicht in der Stage `test` und sind nicht gehalten.
 * Wächter: ein Name der Liste des Kerns, der in der KDoc fehlt, und ein Name in der KDoc, den der Kern nicht annimmt.
 */
class BackendCodesKdocTest {
    private val known = ErrorMapper.BACKEND_CODES.map { it.name }.toSet()

    private fun names(path: String, from: String, to: String): Set<String> {
        val text = File(path).readText()
        val start = text.indexOf(from)
        val block = text.substring(start, text.indexOf(to, start))
        return Regex("TDS_[A-Z_]+").findAll(block).map { it.value }.toSet()
    }

    /**
     * Die KDoc von `QueryEvent.Failure.code` nennt die Namen des Kerns.
     *
     * @spec SPEC-059
     */
    @Test
    fun failureCodeNenntDieNamenDesKerns() {
        val found = names(
            "src/main/kotlin/io/github/pt9912/tdswire/domain/QueryEvent.kt",
            "Ein Fehler des Backends, auch beim Start",
            "public data class Failure",
        )
        assertEquals(known + "TDS_INTERNAL_ERROR", found)
    }

    /**
     * Die KDoc von `BulkTargetResult.Rejected.code` nennt die Namen des Kerns.
     *
     * @spec SPEC-059
     */
    @Test
    fun rejectedCodeNenntDieNamenDesKerns() {
        val found = names(
            "src/main/kotlin/io/github/pt9912/tdswire/port/BulkLoading.kt",
            "Die Anweisung endet mit dem Fehler [code], ohne ein Ziel",
            "public data class Rejected(val code: String, val message: String) : BulkTargetResult",
        )
        assertEquals(known + "TDS_INTERNAL_ERROR", found)
    }
}
