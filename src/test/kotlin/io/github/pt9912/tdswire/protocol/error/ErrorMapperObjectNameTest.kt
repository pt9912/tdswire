package io.github.pt9912.tdswire.protocol.error

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Der Name der unbekannten Tabelle im Text von `TDS_SIM_UNKNOWN_OBJECT` (`Failure.objectName`): der Text, den der echte
 * SQL Server sendet (Messprotokoll des Referenzabgleichs, Szenario `fehler-tabelle-unbekannt`, 2022 und 2025), und die
 * Grenzen, an denen der Name den Client nicht erreicht.
 */
class ErrorMapperObjectNameTest {
    private val mapper = ErrorMapper()

    private fun failure(code: ErrorCode, name: String?) =
        QueryEvent.Failure(code.name, "Tabelle nicht beschrieben", objectName = name)

    private fun unknownObject(name: String?) = failure(ErrorCode.TDS_SIM_UNKNOWN_OBJECT, name)

    /** Der Text des ERROR, wie der Client ihn liest, über die einzelne Antwort. */
    private fun textOf(failure: QueryEvent.Failure): String {
        val tokens = TokenDecoder(assertNotNull(mapper.failure(failure).message).payload).decodeAll()
        return assertIs<ErrorToken>(tokens[0]).message.text
    }

    /** Der Text über die Abbildung des Sequenzierers. */
    private fun sequencedTextOf(failure: QueryEvent.Failure): String =
        mapper.sequencerMapping().backend(failure).message.text

    /**
     * Mit dem Namen der Tabelle lautet der Text `Invalid object name '<Name>'.`, auf beiden Wegen der Antwort
     * (einzeln und im Sequenzierer); Nummer, State und Class bleiben die der Regel. Hochkomma, Klammerzeichen,
     * Punkt, Leerzeichen, Umlaut und Zeilenumbruch stehen unverändert im Text, wie beim echten Server.
     * Wächter: ein Text ohne Namen, ein verdoppeltes oder entferntes Hochkomma, ein Weg ohne Namen.
     *
     * @spec SPEC-038
     * @spec TDS-QA-01
     */
    @Test
    fun `Der Name der unbekannten Tabelle steht im Text und wird nicht umgeschrieben`() {
        val names = listOf(
            "gibtesnicht", "dbo.gibtesnicht", "GibtEsNicht", "gibtes'nicht", "gibtes''nicht", "gibtes]nicht",
            "gibtes nicht", "gibtes.nicht", "gibtes_ä_nicht", "gibtes\nnicht", "a".repeat(128),
        )
        for (name in names) {
            assertEquals("Invalid object name '$name'.", textOf(unknownObject(name)), name)
            assertEquals("Invalid object name '$name'.", sequencedTextOf(unknownObject(name)), name)
        }
        val tokens = TokenDecoder(assertNotNull(mapper.failure(unknownObject("t")).message).payload).decodeAll()
        val message = assertIs<ErrorToken>(tokens[0]).message
        assertEquals(listOf(208, 1, 16), listOf(message.number, message.state, message.severity))
    }

    /**
     * Ohne Namen, mit leerem Namen, über der Grenze von 515 Zeichen und mit einem Steuerzeichen außer dem Zeilenumbruch
     * gilt der feste Text; der Name erreicht den Client dann nie. Ein Name bei einem Code, dessen Regel keinen Namen
     * trägt, ändert dessen Text nicht. Wächter: eine Grenze, die weiter oder enger liegt (515 trägt, 516 nicht), ein
     * Steuerzeichen im Text, ein Name bei `TDS_BACKEND_ERROR`.
     *
     * @spec SPEC-038
     */
    @Test
    fun `Ein Name ausserhalb der Grenzen oder ohne Regel erreicht den Client nie`() {
        assertEquals("Unknown object.", textOf(unknownObject(null)))
        assertEquals("Unknown object.", textOf(unknownObject("")))
        val longest = "a".repeat(MAX_OBJECT_NAME)
        assertEquals("Invalid object name '$longest'.", textOf(unknownObject(longest)))
        assertEquals("Unknown object.", textOf(unknownObject(longest + "a")))
        assertEquals(515, MAX_OBJECT_NAME)
        for (control in listOf("\u0000", "\r", "\t", "\u001b", "\u007f", "\u0085")) {
            assertEquals("Unknown object.", textOf(unknownObject("gibtes${control}nicht")), control)
            assertEquals("Unknown object.", sequencedTextOf(unknownObject("gibtes${control}nicht")), control)
        }
        val backend = failure(ErrorCode.TDS_BACKEND_ERROR, "geheim")
        assertEquals("Backend error.", textOf(backend))
        assertEquals("Backend error.", sequencedTextOf(backend))
        assertFailsWith<IllegalArgumentException> { ErrorCode.TDS_BACKEND_ERROR.rule.message(objectName = "x") }
    }
}
