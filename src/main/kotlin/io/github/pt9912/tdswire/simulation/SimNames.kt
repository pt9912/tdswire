package io.github.pt9912.tdswire.simulation

/**
 * Namensvergleich des Simulations-Backends (`SPEC-036`): Tabellen-, Schema- und Spaltennamen gelten ohne
 * Beachtung der Groß-/Kleinschreibung als gleich. Festlegung ohne Messung: gleich sind zwei Namen, wenn
 * ihre Codepunkte nach `toLowerCase(toUpperCase(cp))` übereinstimmen (die Regel von
 * `String.equalsIgnoreCase`); die Collation eines echten Servers ist nicht gemessen (`SPEC-045`).
 */
internal object SimNames {
    /** Der Vergleichsschlüssel von [name]: zwei Namen sind genau dann gleich, wenn ihre Schlüssel gleich sind. */
    fun fold(name: String): String = buildString(name.length) {
        name.codePoints().forEach { appendCodePoint(Character.toLowerCase(Character.toUpperCase(it))) }
    }

    /** Index des ersten Namens in [names], der einen früheren wiederholt, oder `null`. */
    fun firstDuplicate(names: List<String>): Int? {
        val seen = HashSet<String>(names.size)
        return names.indices.firstOrNull { !seen.add(fold(names[it])) }
    }
}
