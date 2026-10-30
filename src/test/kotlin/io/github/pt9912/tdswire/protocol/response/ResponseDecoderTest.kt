package io.github.pt9912.tdswire.protocol.response

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.ColumnDef
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.TdsType
import io.github.pt9912.tdswire.protocol.token.TdsValue
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Die Ausgabe des Sequenzierers, als Bytes geschrieben und vom Test-Decoder zurückgelesen, gleicht einer
 * im Test ausgeschriebenen Tokenfolge. Der Decoder belegt Selbstkonsistenz mit dem Encoder, keine
 * Protokolltreue (das tun die Handvektoren in `ResponseMappingTest`).
 */
class ResponseDecoderTest {
    private val column = ColumnDef("n", TdsType.Int4Type, nullable = false)
    private val metadata = ColMetadataToken(listOf(column))

    private fun intRow(value: Int) = RowToken(listOf(TdsValue.IntegerValue(value.toLong())))

    /** Läuft den Sequenzierer, schreibt die Tokens als Bytes und liest sie zurück. */
    private fun roundTrip(vararg events: QueryEvent): Pair<List<Token>, List<Token>> {
        val sink = RecordingSink()
        runBlocking { ResponseSequencer(FIXED_MAPPING).run(ScriptedBackend.ofList(*events), REQUEST, sink) }
        val encoder = TokenEncoder()
        sink.tokens.forEach { encoder.write(it) }
        return sink.tokens to TokenDecoder(encoder.toByteArray()).decodeAll()
    }

    /**
     * Szenario aus der Entscheidung zum Backend-Vertrag: Zeilen, Update-Count, Zeilen, leeres Ergebnis.
     *
     * @spec SPEC-031 SPEC-002
     */
    @Test
    fun `Szenario Zeilen, Update-Count, Zeilen, leeres Ergebnis wird vom Decoder gleich zurueckgelesen`() {
        val (written, decoded) = roundTrip(
            start(), row(1), row(2), QueryEvent.ResultEnd(2),
            QueryEvent.UpdateCount(5),
            start(), row(3), QueryEvent.ResultEnd(1),
            start(), QueryEvent.ResultEnd(0),
        )
        val expected = listOf<Token>(
            metadata, intRow(1), intRow(2), done(COUNT_MORE, 2),
            done(COUNT_MORE, 5),
            metadata, intRow(3), done(COUNT_MORE, 1),
            metadata, done(DoneStatus.COUNT, 0),
        )
        assertEquals(expected, decoded)
        assertEquals(written, decoded)
    }

    /**
     * Ein Update-Count allein: ein DONE mit `COUNT` und der Zahl, ohne `MORE`.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Update-Count wird vom Decoder als DONE mit COUNT zurueckgelesen`() {
        val (written, decoded) = roundTrip(QueryEvent.UpdateCount(7))
        assertEquals(listOf<Token>(done(DoneStatus.COUNT, 7)), decoded)
        assertEquals(written, decoded)
    }

    /**
     * Leeres Ergebnis mit Metadaten und der leere Strom (`TDS-FA-08` Boundary).
     *
     * @spec SPEC-031
     */
    @Test
    fun `leeres Ergebnis und leerer Strom werden vom Decoder gleich zurueckgelesen`() {
        val (_, empty) = roundTrip(start(), QueryEvent.ResultEnd(0))
        assertEquals(listOf<Token>(metadata, done(DoneStatus.COUNT, 0)), empty)
        val (_, nothing) = roundTrip()
        assertEquals(listOf<Token>(done(DoneStatus.FINAL, 0)), nothing)
    }

    /**
     * Fehler nach Zeilen: die Zeilen, dann ERROR mit der Meldung des Mappings und DONE mit Fehlerstatus.
     *
     * @spec SPEC-031
     */
    @Test
    fun `Fehler nach Zeilen wird vom Decoder gleich zurueckgelesen`() {
        val (written, decoded) = roundTrip(start(), row(1), row(2), QueryEvent.Failure("x", "m"))
        val expected = listOf<Token>(
            metadata,
            intRow(1),
            intRow(2),
            ErrorToken(BACKEND_MESSAGE),
            done(DoneStatus.ERROR),
        )
        assertEquals(expected, decoded)
        assertEquals(written, decoded)
    }
}
