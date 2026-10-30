package io.github.pt9912.tdswire.domain

/**
 * Das Typmodell des Backends (`SPEC-002`): die Typen, in denen ein Backend Spalten, Parameter und
 * Ausgabewerte beschreibt. Der Umfang ist der Mindestkatalog von `SPEC-003`; Breiten unter 32 Bit,
 * Gleitkomma, Geld, GUID und Binärtypen gehören nicht dazu. Die Abbildung auf TDS-Typen ist nicht
 * Teil dieses Modells.
 *
 * Festlegung ohne Messung: `SPEC-002` definiert `SqlType` nicht; Umfang und Namen setzt dieser Quelltext.
 * Längenlimit von [UnicodeText], Scale der Zeittypen, Zeitzonenannahme, Precision-Obergrenze und die
 * JVM-Typen der Werte je [SqlType] legt die Typabbildung fest (`SPEC-003`, `ValueMapping`).
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
     * festgelegt. Der Codec (`TypeInfoWriter.MAX_PRECISION`) erlaubt 1 bis 38,
     * dieses Modell nimmt auch größere Werte an; die Typabbildung weist eine Precision über 38 mit
     * `TDS_UNSUPPORTED_TYPE` ab (`SPEC-002`).
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

    /** Datum mit Uhrzeit. Das Modell trägt keine Zone; die Typabbildung nimmt Werte ohne Zone an (`SPEC-003`). */
    data object DateTime : SqlType
}
