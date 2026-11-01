package io.github.pt9912.tdswire.simulation

/**
 * Zugriff auf den Baum für den Leser der Tabellenbeschreibung. Jeder Fehler ist eine
 * [SimLoadException] mit festem Grund; kein Grund enthält den Wert aus dem Dokument.
 */
internal object SimTreeAccess {
    /** Pfad der Wurzel in Meldungen. */
    const val ROOT = "(Dokument)"

    fun fail(path: String, at: SimPosition, reason: String): Nothing = throw SimLoadException(path, at, reason)

    /**
     * Der Pfad des Eintrags [key] unter [path]. Der Abschnitt hat höchstens [SimLoadException.MAX_SEGMENT]
     * Codepunkte (ein Ersatzzeichenpaar wird nicht getrennt); Steuer-, Format-, Zeilen- und Absatztrennzeichen
     * (Kategorien `Cc`, `Cf`, `Zl`, `Zp`) und einzelne Ersatzzeichen (`Cs`) werden zu `?`.
     */
    fun child(path: String, key: String): String {
        val points = key.codePoints().toArray()
        val clean = StringBuilder()
        for (cp in points.take(SimLoadException.MAX_SEGMENT)) {
            clean.appendCodePoint(if (isUnsafe(cp)) '?'.code else cp)
        }
        val segment = if (points.size > SimLoadException.MAX_SEGMENT) "$clean…" else clean.toString()
        return if (path == ROOT) segment else "$path.$segment"
    }

    private fun isUnsafe(cp: Int): Boolean = when (Character.getType(cp).toByte()) {
        Character.CONTROL, Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR,
        Character.SURROGATE,
        -> true

        else -> false
    }

    fun item(path: String, index: Int): String = "$path[$index]"

    fun asMap(node: SimNode, path: String): SimMap = node as? SimMap ?: fail(path, node.position, "Abbildung erwartet")

    fun asList(node: SimNode, path: String): SimList = node as? SimList ?: fail(path, node.position, "Liste erwartet")

    /**
     * Die gelesenen Einträge von [map] nach Schlüssel. Ein doppelter Schlüssel und jeder Schlüssel, der weder
     * in [read] noch in [ignored] steht, ist ein Ladefehler (`additionalProperties: false` im Schema).
     */
    fun fields(map: SimMap, path: String, read: Set<String>, ignored: Set<String>): Map<String, SimEntry> {
        val seen = HashSet<String>(map.entries.size)
        val result = LinkedHashMap<String, SimEntry>()
        for (entry in map.entries) {
            val here = child(path, entry.key)
            if (!seen.add(entry.key)) fail(here, entry.keyPosition, "Feld doppelt")
            if (entry.key in read) {
                result[entry.key] = entry
            } else if (entry.key !in ignored) {
                fail(here, entry.keyPosition, "Feld unbekannt")
            }
        }
        return result
    }

    fun required(fields: Map<String, SimEntry>, key: String, map: SimMap, path: String): SimEntry =
        fields[key] ?: fail(path, map.position, "Pflichtfeld $key fehlt")

    /** Der Text eines nicht leeren Skalars, gleich ob mit oder ohne Anführungszeichen. */
    fun text(node: SimNode, path: String): String {
        val scalar = node as? SimScalar ?: fail(path, node.position, "Skalar erwartet")
        if (scalar.text.isEmpty()) fail(path, node.position, "Text darf nicht leer sein")
        return scalar.text
    }

    /** `true` oder `false`, nur schlicht geschrieben; `yes`, `"true"` und Verwandte sind ein Ladefehler. */
    fun boolean(node: SimNode, path: String): Boolean {
        val scalar = node as? SimScalar ?: fail(path, node.position, "Skalar erwartet")
        return when {
            scalar.plain && scalar.text == "true" -> true
            scalar.plain && scalar.text == "false" -> false
            else -> fail(path, node.position, "nur true oder false erlaubt")
        }
    }

    /** Eine Ganzzahl in kanonischer Schreibweise (`0|[1-9][0-9]*`, schlicht) ab [min], die in `Int` passt. */
    fun integer(node: SimNode, path: String, min: Int): Int {
        val scalar = node as? SimScalar ?: fail(path, node.position, "Skalar erwartet")
        val digits = scalar.text
        val canonical = scalar.plain && digits.isNotEmpty() && digits.all { it in '0'..'9' } &&
            (digits == "0" || digits[0] != '0')
        if (!canonical) fail(path, node.position, "Ganzzahl in kanonischer Schreibweise erwartet")
        val value = digits.toIntOrNull() ?: fail(path, node.position, "Ganzzahl zu groß")
        if (value < min) fail(path, node.position, "Ganzzahl kleiner als $min")
        return value
    }
}
