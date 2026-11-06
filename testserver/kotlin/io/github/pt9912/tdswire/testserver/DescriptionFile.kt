package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.simulation.SimTable

/** Ausgang des Lesens der Datei der Tabellenbeschreibung. */
internal sealed interface DescriptionResult {
    /** Die Tabellen der Datei; ohne Datei die leere Liste. Tabellen aus der Datei haben eine leere Zeilenquelle. */
    class Read(val tables: List<SimTable>) : DescriptionResult

    /** Die Datei ist ungültig; [reason] nennt nie den Inhalt der Datei. */
    class Failed(val reason: String) : DescriptionResult
}

/**
 * Der Dateiteil der Verdrahtung des Simulations-Backends (`TDS-FA-11.a`, `ADR-0012`): die Bytes der Datei werden von
 * [YamlTreeReader] zum Baum und von [SimDescriptionReader] zu `SimTable`-Werten gelesen. Wie die Tabellen mit denen
 * des Testservers einen Katalog bilden, entscheidet der Start der Bibliothek.
 */
internal object DescriptionFile {
    fun tables(file: ByteArray?): DescriptionResult {
        if (file == null) return DescriptionResult.Read(emptyList())
        return try {
            DescriptionResult.Read(SimDescriptionReader.read(YamlTreeReader.read(file)))
        } catch (error: SimLoadException) {
            DescriptionResult.Failed("Beschreibung der Tabellen ungueltig: ${error.message}")
        }
    }
}
