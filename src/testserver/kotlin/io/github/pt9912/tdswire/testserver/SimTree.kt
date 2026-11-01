package io.github.pt9912.tdswire.simulation

/** Eine Stelle im Dokument; [line] und [column] zählen ab 1. */
data class SimPosition(val line: Int, val column: Int)

/**
 * Der Baum, den ein Adapter aus der Datei erzeugt und der Leser der Tabellenbeschreibung liest
 * (`SPEC-035`): Abbildung, Liste und Skalar, jeder Knoten mit seiner Stelle im Dokument. Der Baum kennt
 * keine YAML-Bibliothek; Anker, Aliase, Tags und Merge-Schlüssel kommen in ihm nicht vor.
 */
sealed interface SimNode {
    /** Die Stelle, an der der Knoten im Dokument beginnt. */
    val position: SimPosition
}

/**
 * Ein Skalar. [text] ist die Schreibweise im Dokument; [plain] ist wahr, wenn sie ohne Anführungszeichen
 * stand. Wahrheitswerte und Ganzzahlen liest der Leser nur aus einem schlichten Skalar.
 */
data class SimScalar(val text: String, val plain: Boolean, override val position: SimPosition) : SimNode

/** Eine Liste in der Reihenfolge des Dokuments. */
data class SimList(val items: List<SimNode>, override val position: SimPosition) : SimNode

/** Ein Eintrag einer Abbildung; [keyPosition] ist die Stelle des Schlüssels. */
data class SimEntry(val key: String, val keyPosition: SimPosition, val value: SimNode)

/** Eine Abbildung in der Reihenfolge des Dokuments; ein Schlüssel darf in [entries] mehrfach vorkommen. */
data class SimMap(val entries: List<SimEntry>, override val position: SimPosition) : SimNode

/**
 * Ein Fehler beim Laden der Beschreibung (`SPEC-035` Ladefehler). Die Meldung nennt den [path] im
 * Dokument, die Stelle und einen festen Grund, nie den Inhalt eines Werts. Der Pfad besteht aus
 * Schlüsseln des Dokuments, ein Abschnitt höchstens [MAX_SEGMENT] Codepunkte lang, Steuer-, Format-, Zeilen- und
 * Absatztrennzeichen als `?`.
 * Kein TDS-Fehlercode: beim Laden ist kein Client verbunden.
 */
class SimLoadException(val path: String, val position: SimPosition, val reason: String) :
    Exception("$path (Zeile ${position.line}, Spalte ${position.column}): $reason") {
    companion object {
        /** Obergrenze eines Pfadabschnitts in Codepunkten. */
        const val MAX_SEGMENT = 64
    }
}
