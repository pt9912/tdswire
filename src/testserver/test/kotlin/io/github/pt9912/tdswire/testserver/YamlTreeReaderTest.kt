package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.simulation.SimList
import io.github.pt9912.tdswire.simulation.SimLoadException
import io.github.pt9912.tdswire.simulation.SimMap
import io.github.pt9912.tdswire.simulation.SimNode
import io.github.pt9912.tdswire.simulation.SimPosition
import io.github.pt9912.tdswire.simulation.SimScalar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

/**
 * Der Adapter von YAML-Bytes zum Baum (`SPEC-035` „Verdrahtung“): Aufbau mit Zeile und Spalte, die Schreibweise der
 * Skalare, die abgewiesenen Formen und die Meldungen ohne Inhalt. Positionen sind Zeile und Spalte ab 1 im Text der
 * Testdatei; die Deutung der Skalare ist die der Bibliothek, gemessen an diesen Eingaben (Jackson 2.21.7).
 */
internal class YamlTreeReaderTest {
    private fun read(text: String): SimNode = YamlTreeReader.read(text.toByteArray(Charsets.UTF_8))

    private fun load(text: String): SimLoadException = assertFailsWith<SimLoadException> { read(text) }

    private fun pos(line: Int, column: Int) = SimPosition(line, column)

    private fun entry(map: SimNode, key: String) = assertIs<SimMap>(map).entries.single { it.key == key }

    /**
     * Happy: Abbildung, Liste und Skalar mit der Stelle, an der sie beginnen; der Schlüssel trägt seine eigene
     * Stelle.
     * @spec SPEC-035
     */
    @Test
    fun `der Baum traegt Abbildung Liste und Skalar mit Zeile und Spalte`() {
        val root = read("name: demo\nkeys:\n  - a\n  - b\nnested:\n  inner: 1\n")

        val map = assertIs<SimMap>(root)
        assertEquals(listOf("name", "keys", "nested"), map.entries.map { it.key })
        assertEquals(pos(1, 1), map.entries[0].keyPosition)
        assertEquals(SimScalar("demo", false, pos(1, 7)), map.entries[0].value)
        assertEquals(pos(2, 1), map.entries[1].keyPosition)
        val list = assertIs<SimList>(map.entries[1].value)
        assertEquals(listOf("a", "b"), list.items.map { assertIs<SimScalar>(it).text })
        assertEquals(pos(3, 5), list.items[0].position)
        assertEquals(pos(4, 5), list.items[1].position)
        val inner = assertIs<SimMap>(map.entries[2].value)
        assertEquals(pos(6, 3), inner.entries.single().keyPosition)
        assertEquals(pos(6, 10), inner.entries.single().value.position)
    }

    /**
     * Boundary: nur ein Skalar ohne Anführungszeichen, den die Bibliothek als Wahrheitswert, Zahl oder NULL liest,
     * ist schlicht; Text und jeder Skalar mit Anführungszeichen sind es nicht. Der Text ist die Schreibweise der
     * Datei (`010`, `True`, `1.0`, `~`); ein leerer Wert ohne Anführungszeichen ist ein leerer Text.
     * @spec SPEC-035
     */
    @Test
    fun `schlicht ist nur ein Skalar ohne Anfuehrungszeichen der Wahrheitswert Zahl oder NULL ist`() {
        val root = read("a: true\nb: \"true\"\nc: 5\nd: '5'\ne: abc\nf: ~\ng: 010\nh: True\ni: 1.0\nj: \"\"\nk:\n")

        val seen = HashMap<String, Pair<String, Boolean>>()
        for (entry in assertIs<SimMap>(root).entries) {
            val scalar = assertIs<SimScalar>(entry.value)
            seen[entry.key] = scalar.text to scalar.plain
        }
        assertEquals("true" to true, seen["a"])
        assertEquals("true" to false, seen["b"])
        assertEquals("5" to true, seen["c"])
        assertEquals("5" to false, seen["d"])
        assertEquals("abc" to false, seen["e"])
        assertEquals("~" to true, seen["f"])
        assertEquals("010" to true, seen["g"])
        assertEquals("True" to true, seen["h"])
        assertEquals("1.0" to true, seen["i"])
        assertEquals("" to false, seen["j"])
        assertEquals("" to false, seen["k"])
    }

    /**
     * Boundary: ein doppelter Schlüssel bleibt in der Reihenfolge erhalten (den Fehler meldet der Leser, nicht der
     * Adapter); ein Byte Order Mark am Anfang wird übergangen und die Stellen zählen ohne es.
     * @spec SPEC-035
     */
    @Test
    fun `ein doppelter Schluessel bleibt und ein Byte Order Mark wird uebergangen`() {
        val twice = assertIs<SimMap>(read("a: 1\na: 2\n"))
        assertEquals(listOf("a", "a"), twice.entries.map { it.key })
        assertEquals(listOf("1", "2"), twice.entries.map { assertIs<SimScalar>(it.value).text })

        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "key: v\n".toByteArray()
        val withMark = assertIs<SimMap>(YamlTreeReader.read(bytes))
        assertEquals("key", withMark.entries.single().key)
        assertEquals(pos(1, 1), withMark.entries.single().keyPosition)
    }

    /**
     * Negative: ein Anker an einer Abbildung oder Liste, ein Alias und ein Tag sind ein Ladefehler mit der Stelle des
     * Knotens und dem Pfad; die Meldung nennt weder den Namen des Ankers noch den Tag. Grenze: ein Anker an einem
     * Skalar, auf den nichts verweist, zeigt die Bibliothek nicht und bleibt ohne Wirkung; verweist ein Alias auf
     * ihn, ist der Alias der Fehler.
     * @spec SPEC-035
     */
    @Test
    fun `Anker an Sammlungen Aliase und Tags werden abgewiesen`() {
        val inputs = mapOf(
            "anker-an-abbildung" to "a: &geheim-anker\n  b: 1\n",
            "anker-an-liste" to "a: &geheim-anker [1]\n",
            "alias" to "a: &geheim-anker 1\nb: *geheim-anker\n",
            "tag" to "a: !!str 1\n",
            "tag-an-abbildung" to "a: !geheim-tag\n  b: 1\n",
        )
        for ((name, text) in inputs) {
            val error = load(text)

            assertEquals("Anker, Alias oder Tag nicht erlaubt", error.reason, name)
            assertFalse(error.message.orEmpty().contains("geheim"), name)
        }
        val list = load("a: &geheim-anker [1]\n")
        assertEquals("a", list.path)
        assertEquals(1, list.position.line)
        val alias = load("a: &geheim-anker 1\nb: *geheim-anker\n")
        assertEquals("b", alias.path)
        assertEquals(2, alias.position.line)

        val loose = assertIs<SimMap>(read("a: &geheim-anker 1\nb: 2\n"))
        assertEquals(listOf("a", "b"), loose.entries.map { it.key })
    }

    /**
     * Negative: ein leeres Dokument, mehr als ein Dokument, ein Syntaxfehler und eine Tiefe über der Grenze der
     * Bibliothek enden mit einem Ladefehler ohne den Text der Eingabe und ohne den Text der Bibliothek; der
     * Syntaxfehler trägt Zeile und Spalte.
     * @spec SPEC-035
     */
    @Test
    fun `leere Dokumente Mehrfachdokumente und Syntaxfehler sind Ladefehler`() {
        assertEquals("Dokument leer", load("").reason)
        assertEquals("mehr als ein Dokument", load("a: 1\n---\nb: 2\n").reason)

        val syntax = load("a: 1\nb: [geheim-inhalt\n")
        assertEquals("YAML nicht lesbar", syntax.reason)
        assertFalse(syntax.message.orEmpty().contains("geheim"), syntax.message)
        assertEquals(2, syntax.position.line)

        val deep = load("[".repeat(DEPTH) + "]".repeat(DEPTH))
        assertFalse(deep.message.orEmpty().contains("["), deep.message)
    }

    /**
     * Negative: Bytes, die kein UTF-8 sind, enden mit einem Ladefehler an der Stelle 1/1 und ohne die Bytes in der
     * Meldung. Kontrollfall: dieselben Zeichen als gültiges UTF-8 laden.
     * @spec SPEC-035
     */
    @Test
    fun `Bytes ausserhalb von UTF-8 sind ein Ladefehler`() {
        val broken = "key: ".toByteArray() + byteArrayOf(0xC3.toByte(), 0x28)

        val error = assertFailsWith<SimLoadException> { YamlTreeReader.read(broken) }

        assertEquals("Datei ist kein gültiges UTF-8", error.reason)
        assertEquals(pos(1, 1), error.position)
        assertEquals("ä", assertIs<SimScalar>(entry(read("key: ä\n"), "key").value).text)
    }

    /**
     * Negative: Zeichen, die YAML nicht zulässt, und ein Zeichen außerhalb der Grundebene enden als Ladefehler
     * "YAML nicht lesbar" und nicht als andere Ausnahme; der Adapter liest aus einem Text im Speicher, ein Fehler des
     * Lesens (`IOException`) tritt dort nicht auf und hat keinen eigenen Zweig.
     * @spec SPEC-035
     */
    @Test
    fun `unzulaessige Zeichen sind ein Ladefehler und keine andere Ausnahme`() {
        for (text in listOf("a: x\u0000y\n", "a: x\u0007y\n", "\u0000", "a: \"\u0001\"\n")) {
            assertEquals("YAML nicht lesbar", load(text).reason, text)
        }
        assertEquals("\uD83D\uDE00", assertIs<SimScalar>(entry(read("k: \uD83D\uDE00\n"), "k").value).text)
    }

    private companion object {
        /** Mehr als die Tiefe 1000, die Jackson von sich aus begrenzt (`ADR-0012`, Messzeile MR). */
        const val DEPTH = 1200
    }
}
