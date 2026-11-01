package io.github.pt9912.tdswire.testserver

/**
 * Namensvergleich des Lesers der Tabellenbeschreibung (`SPEC-036`): Namen gelten ohne Beachtung der
 * Groß-/Kleinschreibung als gleich, nach derselben Regel wie der Katalog der Bibliothek (Codepunkte nach
 * `toLowerCase(toUpperCase(cp))`, Festlegung ohne Messung). Der Katalog prüft beim Start jeden Namen erneut.
 */
internal object NameFold {
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
