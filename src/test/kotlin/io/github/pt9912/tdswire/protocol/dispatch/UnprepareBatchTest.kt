package io.github.pt9912.tdswire.protocol.dispatch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Die Erkennung der Batch-Form der Freigabe (`SPEC-030`) ohne Verbindung. Die Form ist an zwei Batches von
 * mssql-jdbc 13.4.0 gelesen (Messzeile t5), das Ende des Textes steht nicht im Mitschnitt; Schreibweise und
 * Zahlenbereich sind Festlegungen und nicht gemessen.
 */
class UnprepareBatchTest {
    /**
     * Die Form des Treibers: eine Anweisung, mehrere durch `;`, ein `;` am Ende; die Nummern kommen in der
     * Reihenfolge des Textes, auch doppelte. Wächter: ein Leser, der nur die erste Anweisung liest, die Reihenfolge
     * ändert oder doppelte Nummern zusammenlegt.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Die Form des Treibers wird gelesen`() {
        assertEquals(listOf(1), UnprepareBatch.parse("EXEC sp_unprepare 1"))
        val three = "EXEC sp_unprepare 1;EXEC sp_unprepare 2;EXEC sp_unprepare 11"
        assertEquals(listOf(1, 2, 11), UnprepareBatch.parse(three))
        assertEquals(listOf(5, 3), UnprepareBatch.parse("EXEC sp_unprepare 5;EXEC sp_unprepare 3;"))
        assertEquals(listOf(7, 7), UnprepareBatch.parse("EXEC sp_unprepare 7;EXEC sp_unprepare 7"))
    }

    /**
     * Die Grenzen des Zahlenbereichs: `0` und `Int.MAX_VALUE` sind Nummern (unbekannt, aber erkannt); eine Zahl
     * über `Int`, mehr als zehn Ziffern, eine führende Null und ein Minuszeichen machen den Text zu keiner
     * Freigabe. Wächter: ein Bereich mit `<` statt `<=`, ein `toInt()` ohne Prüfung (Überlauf zu einer negativen
     * Nummer), ein Minuszeichen, das als Ziffer gilt.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Der Zahlenbereich ist 0 bis Int MAX_VALUE ohne fuehrende Null`() {
        assertEquals(listOf(0), UnprepareBatch.parse("EXEC sp_unprepare 0"))
        assertEquals(listOf(Int.MAX_VALUE), UnprepareBatch.parse("EXEC sp_unprepare 2147483647"))
        assertNull(UnprepareBatch.parse("EXEC sp_unprepare 2147483648"))
        assertNull(UnprepareBatch.parse("EXEC sp_unprepare 4294967297"))
        assertNull(UnprepareBatch.parse("EXEC sp_unprepare 12345678901"))
        assertNull(UnprepareBatch.parse("EXEC sp_unprepare 01"))
        assertNull(UnprepareBatch.parse("EXEC sp_unprepare 00"))
        assertNull(UnprepareBatch.parse("EXEC sp_unprepare -1"))
    }

    /**
     * Jede andere Schreibweise und jede Abweichung der Form ergibt `null`: der Text geht dann wie jeder Batch an das
     * Backend. Wächter: ein Leser, der Groß-/Kleinschreibung, Leerraum, `EXECUTE`, Klammern, Zeilenumbruch, ein
     * Anhängsel oder eine leere Anweisung zulässt.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Jede andere Schreibweise und jede Abweichung der Form ist keine Freigabe`() {
        listOf(
            "",
            ";",
            "EXEC sp_unprepare",
            "EXEC sp_unprepare ",
            "EXEC sp_unprepare x",
            "EXEC sp_unprepare 1x",
            "EXEC sp_unprepare 1 ",
            "EXEC sp_unprepare  1",
            " EXEC sp_unprepare 1",
            "exec sp_unprepare 1",
            "EXECUTE sp_unprepare 1",
            "EXEC SP_UNPREPARE 1",
            "EXEC sp_unprepare (1)",
            "EXEC sp_unprepare @h",
            "EXEC sp_unprepare 1\n",
            "EXEC sp_unprepare\n1",
            "EXEC sp_unprepare 1;;",
            "EXEC sp_unprepare 1;;EXEC sp_unprepare 2",
            "EXEC sp_unprepare 1; EXEC sp_unprepare 2",
            "EXEC sp_unprepare 1 ;EXEC sp_unprepare 2",
            "EXEC sp_unprepare 1;SELECT 1",
            "EXEC sp_unprepare 1;EXEC sp_unprepare",
            "EXEC sp_unprepare 1,2",
            "SELECT 1",
            "EXEC sp_unprepare 1; SELECT 1",
            "EXEC sp_executesql N'x'",
            "EXEC sp_unprepare1",
        ).forEach { assertNull(UnprepareBatch.parse(it), it) }
    }

    /**
     * Die Länge des Textes begrenzt dieser Leser nicht (die Nachricht ist durch `MAX_MESSAGE_BYTES` begrenzt, geprüft
     * vom Transport): 200000 Anweisungen, rund 4 MiB, werden gelesen; ein Fehler am Ende des Textes macht den
     * ganzen Text zu keiner Freigabe. Wächter: ein Leser, der bei großen Texten abbricht, eine Rekursion über die
     * Anweisungen, ein Leser, der den Fehler am Ende übersieht.
     *
     * @spec SPEC-030
     */
    @Test
    fun `Ein langer Text wird ganz gelesen und ein Fehler am Ende gilt fuer den ganzen Text`() {
        val count = 200_000
        val text = (1..count).joinToString(";") { "EXEC sp_unprepare $it" }
        assertEquals((1..count).toList(), UnprepareBatch.parse(text))
        assertNull(UnprepareBatch.parse("$text;SELECT 1"))
    }
}
