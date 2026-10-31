package io.github.pt9912.tdswire.runtime

import com.fasterxml.jackson.core.JsonLocation
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLParser
import io.github.pt9912.tdswire.simulation.SimEntry
import io.github.pt9912.tdswire.simulation.SimList
import io.github.pt9912.tdswire.simulation.SimLoadException
import io.github.pt9912.tdswire.simulation.SimMap
import io.github.pt9912.tdswire.simulation.SimNode
import io.github.pt9912.tdswire.simulation.SimPosition
import io.github.pt9912.tdswire.simulation.SimScalar
import io.github.pt9912.tdswire.simulation.SimTreeAccess
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Der Adapter der Tabellenbeschreibung (`SPEC-035` „Verdrahtung“, `ADR-0012`): liest die Bytes einer YAML-Datei mit
 * der Strom-API von Jackson zu einem [SimNode]-Baum aus Abbildung, Liste und Skalar mit Zeile und Spalte. Die
 * Größengrenze prüft der Lader der Konfiguration vor dem Lesen der Datei, nicht dieser Adapter.
 *
 * Festlegungen (Setzung, nicht gemessen): Die Datei muss gültiges UTF-8 sein, eine Byte Order Mark am Anfang wird
 * übergangen. Ein Dokument; Anker an Abbildung oder Liste, Aliase und Tags sind ein Ladefehler (ein Anker an einem
 * Skalar zeigt die Strom-API nicht; verweist ein Alias auf ihn, ist der Alias der Ladefehler, sonst bleibt er ohne
 * Wirkung, Grenze gemessen mit Jackson 2.21.7); Schlüssel sind Skalare. Ein Skalar ohne
 * Anführungszeichen, den Jackson als Wahrheitswert, Zahl oder NULL liest, ist „schlicht“ ([SimScalar.plain]),
 * ein Skalar mit Anführungszeichen und ein Text nicht; die Schreibweise ist die der Datei, nicht die Deutung der
 * Bibliothek. Die Meldung eines Fehlers nennt Pfad, Zeile und Spalte und einen festen Grund, nie den Inhalt und nie
 * den Text der Bibliothek.
 */
internal object YamlTreeReader {
    private const val BOM = '\uFEFF'
    private val START = SimPosition(1, 1)

    /** Der Baum der Datei oder eine [SimLoadException]. */
    fun read(bytes: ByteArray): SimNode {
        val text = decode(bytes)
        return try {
            YAMLFactory.builder().build().createParser(text).use { Reading(it).document() }
        } catch (ignored: JsonProcessingException) {
            // Der Text der Bibliothek kann Inhalt der Datei nennen; die Meldung trägt nur die Stelle.
            throw SimLoadException(SimTreeAccess.ROOT, at(ignored.location), "YAML nicht lesbar")
        } catch (ignored: IOException) {
            throw SimLoadException(SimTreeAccess.ROOT, START, "Datei nicht lesbar")
        }
    }

    private fun at(location: JsonLocation?): SimPosition =
        if (location == null) START else SimPosition(maxOf(location.lineNr, 1), maxOf(location.columnNr, 1))

    private fun decode(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
            .removePrefix(BOM.toString())
    } catch (ignored: CharacterCodingException) {
        throw SimLoadException(SimTreeAccess.ROOT, START, "Datei ist kein gültiges UTF-8")
    }

    /** Ein Lauf über die Token eines Parsers; [path] ist der Pfad des Knotens, der gerade gelesen wird. */
    private class Reading(private val parser: YAMLParser) {
        private var path = SimTreeAccess.ROOT

        fun document(): SimNode {
            if (parser.nextToken() == null) fail("Dokument leer")
            val root = node()
            if (parser.nextToken() != null) fail("mehr als ein Dokument")
            return root
        }

        private fun position(): SimPosition = at(parser.currentTokenLocation())

        private fun fail(reason: String): Nothing = throw SimLoadException(path, position(), reason)

        private fun node(): SimNode {
            if (parser.objectId != null || parser.isCurrentAlias || parser.typeId != null) {
                fail("Anker, Alias oder Tag nicht erlaubt")
            }
            return when (val token = parser.currentToken) {
                JsonToken.START_OBJECT -> map()

                JsonToken.START_ARRAY -> list()

                JsonToken.VALUE_STRING -> SimScalar(parser.text, plain = false, position())

                JsonToken.VALUE_TRUE, JsonToken.VALUE_FALSE, JsonToken.VALUE_NULL,
                JsonToken.VALUE_NUMBER_INT, JsonToken.VALUE_NUMBER_FLOAT,
                -> SimScalar(parser.text, plain = true, position())

                else -> fail("unerwartetes Token $token")
            }
        }

        private fun map(): SimMap {
            val start = position()
            val here = path
            val entries = ArrayList<SimEntry>()
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                val key = parser.currentName()
                val keyPosition = position()
                path = SimTreeAccess.child(here, key)
                parser.nextToken()
                entries += SimEntry(key, keyPosition, node())
            }
            path = here
            if (parser.currentToken != JsonToken.END_OBJECT) fail("Abbildung nicht lesbar")
            return SimMap(entries, start)
        }

        private fun list(): SimList {
            val start = position()
            val here = path
            val items = ArrayList<SimNode>()
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                path = SimTreeAccess.item(here, items.size)
                items += node()
            }
            path = here
            return SimList(items, start)
        }
    }
}
