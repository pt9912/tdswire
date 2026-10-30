package io.github.pt9912.tdswire.domain

/**
 * Das Typmodell des Backends (`SPEC-002`): die Typen, in denen ein Backend Spalten, Parameter und
 * Ausgabewerte beschreibt. Der Umfang ist der Mindestkatalog von `SPEC-003`; Breiten unter 32 Bit,
 * Gleitkomma, Geld, GUID und Binärtypen gehören nicht dazu. Die Abbildung auf TDS-Typen ist nicht
 * Teil dieses Modells.
 *
 * Festlegung ohne Messung: `SPEC-002` definiert `SqlType` nicht; Umfang und Namen setzt dieser Quelltext.
 * Offen (`SPEC-003`): Längenlimit von [UnicodeText], Genauigkeit der Zeittypen und Zeitzonenannahmen.
 * Offen (ADR-0006): welche JVM-Typen die Werte in `Row` und `QueryParameter` je [SqlType] haben.
 */
sealed interface SqlType {
    /** Ganzzahl mit 32 Bit. */
    data object Int32 : SqlType

    /** Ganzzahl mit 64 Bit. */
    data object Int64 : SqlType

    /** Wahrheitswert; der Wert `null` ist NULL. */
    data object Bool : SqlType

    /** Unicode-Text ohne festgelegtes Längenlimit. */
    data object UnicodeText : SqlType

    /**
     * Dezimalzahl mit [precision] Stellen insgesamt, davon [scale] hinter dem Komma. Gefordert ist
     * `precision >= 1` und `0 <= scale <= precision`; eine obere Grenze von [precision] ist nicht
     * festgelegt.
     */
    data class Decimal(val precision: Int, val scale: Int) : SqlType {
        init {
            require(precision >= 1) { "precision muss mindestens 1 sein: $precision" }
            require(scale in 0..precision) { "scale muss in 0..precision liegen: $scale bei precision $precision" }
        }
    }

    /** Kalenderdatum ohne Uhrzeit. */
    data object Date : SqlType

    /** Uhrzeit ohne Datum. */
    data object Time : SqlType

    /** Datum mit Uhrzeit, ohne Zeitzone. */
    data object DateTime : SqlType
}
