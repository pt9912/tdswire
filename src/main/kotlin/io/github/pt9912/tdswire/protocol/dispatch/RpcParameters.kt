package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.ParameterDirection
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.protocol.rpc.RpcParameter
import io.github.pt9912.tdswire.protocol.rpc.RpcType
import io.github.pt9912.tdswire.protocol.rpc.RpcTypeKind
import io.github.pt9912.tdswire.protocol.rpc.RpcValue
import java.util.Locale

/**
 * Warum der Dispatch einen RPC ablehnt, bevor das Backend gerufen wird. Bei [ENCODING] sieht der Client den
 * Fehler des Parsers und die Verbindung schließt; bei jedem anderen Grund `TDS_UNSUPPORTED_RPC` bei
 * offener Verbindung. Der Name des Grundes steht im Log (nie ein Wert).
 */
internal enum class RpcRejection {
    /** Struktur- oder Kodierfehler des Parsers (Format, Länge, unbekannte Typkennung, TYPE_INFO, ALL_HEADERS). */
    ENCODING,

    /** ProcID außerhalb des Katalogs (`sp_prepare`, `sp_unprepare`, `sp_cursor*`) oder ein Name, den er führt. */
    UNKNOWN_PROCEDURE,

    /** Prozedurname außerhalb der Grammatik von [RpcProcedureName]. */
    PROCEDURE_NAME,

    /** Parametername leer, ohne `@`, außerhalb der Grammatik oder doppelt. */
    PARAMETER_NAME,

    /** Statusbyte mit einem anderen Bit als OUTPUT (Bit 0). */
    STATUS,

    /** Typ ohne Entsprechung in `SqlType`, auch ein gültig kodierter PLP-Typ (`nvarchar(max)`). */
    TYPE,

    /** Wert außerhalb des Bereichs seines Typs. */
    VALUE,
}

/** Interne Abkürzung des Kontrollflusses der Abbildung; wird an der Grenze von [RpcBinder] gefangen. */
internal class RpcRejected(val reason: RpcRejection) : RuntimeException(reason.name, null, false, false)

/**
 * Bildet die Wertparameter eines RPC auf [QueryParameter] ab (`TDS-FA-04.a` Nr. 3, `SPEC-002`,
 * `SPEC-003`). Typ und Wert kommen aus der TYPE_INFO und den Wertbytes des Parameters; die
 * Deklarationsliste von `sp_executesql` wird nicht gedeutet.
 *
 * Abbildung (Festlegung, nicht gemessen): `INTN` mit 4 und 8 Byte sind `Int32` (`Int`) und `Int64`
 * (`Long`); `BITN` ist `Bool`; `NVARCHAR` und `NCHAR` sind `UnicodeText` (`String`, UTF-16-Einheiten
 * unverändert); `DECIMALN` und `NUMERICN` sind `Decimal(precision, scale)` (`BigDecimal`); `DATEN`,
 * `TIMEN` und `DATETIME2N` sind `Date`, `Time` und `DateTime` (`LocalDate`, `LocalTime`,
 * `LocalDateTime`); `DATETIMN` mit 8 Byte ist `DateTime` (`datetime`: Tage seit 1900-01-01 und Ticks zu 1/300 s;
 * mssql-jdbc sendet so die Zeit einer `time`-Spalte mit dem Datum 1970-01-01, gemessen am echten SQL Server). Jeder
 * andere Typ (`INTN` mit 1 und 2 Byte, Gleitkomma, Geld, GUID, Binär, `varchar`, `DATETIMN` mit 4 Byte,
 * `DATETIMEOFFSETN`) ist [RpcRejection.TYPE]. `INTN` mit 1 und 2 Byte entspricht `Int16` und `UInt8`, die `SqlType` als
 * Spaltentypen der Antworten führt; als Parameter bleibt er abgewiesen (Setzung), und für die übrigen Typen entsteht
 * hier kein zweiter Katalog.
 *
 * Richtung (Festlegung): Bit 0 des Statusbytes ist auf dem Draht OUTPUT und InputOutput zugleich. Ein
 * gesetztes Bit mit NULL-Wert ist `OUTPUT`, mit Wert `INPUT_OUTPUT`; ohne Bit ist es `INPUT`. Ein
 * InputOutput-Parameter mit NULL erscheint damit als `OUTPUT`; beide tragen als Eingabe NULL. Jedes
 * andere Bit des Statusbytes (auch Bit 1, der Standardwert) ist [RpcRejection.STATUS].
 *
 * Namen (Festlegung): `@` und 1 bis [MAX_NAME_CHARS] − 1 Zeichen aus Buchstaben, Ziffern, `_`, `@`, `#`
 * und `$`, ohne Beachtung der Schreibweise eindeutig; der Name bleibt wie gesendet, mit `@`. Die Obergrenze
 * zählt das `@` mit. Ein zweites `@` an zweiter Stelle (`@@x`) ist beim RPC per Prozedurname abgelehnt, wo
 * der Name im Aufruftext auch rechts von `=` steht und `@@x` eine globale Funktion des Ziels bezeichnen kann
 * statt des gesendeten Wertes; bei `sp_executesql` steht der Name nicht im Text und `@@x` ist nicht
 * abgelehnt (Festlegung ohne Messung, Nutzerentscheidung vom 2026-10-31). Ein Parameter ohne Namen ist beim RPC
 * per Prozedurname [RpcRejection.PARAMETER_NAME]; bei `sp_executesql`, `sp_prepexec` und `sp_execute` heißt der
 * Wertparameter an Stelle `i` (ab 0, in der Reihenfolge der Wertparameter) `@P<i>` (Setzung: mssql-jdbc 13.4.0
 * sendet die Werte ohne Namen und nennt sie in der Deklaration so; ein Name, der danach doppelt ist, bleibt
 * [RpcRejection.PARAMETER_NAME]).
 */
internal object RpcParameters {
    const val MAX_NAME_CHARS = 128
    private const val NAME_PREFIX = '@'
    private const val POSITIONAL_PREFIX = "@P"
    private const val INT32_BYTES = 4
    private const val INT64_BYTES = 8
    private const val DATETIME_BYTES = 8

    /**
     * Die Parameter in der Reihenfolge der Nachricht. [nameInText] ist wahr, wenn die Namen im Aufruftext
     * stehen (RPC per Prozedurname); nur dann ist `@@x` abgelehnt.
     */
    fun map(parameters: List<RpcParameter>, nameInText: Boolean): List<QueryParameter> {
        val seen = mutableSetOf<String>()
        return parameters.mapIndexed { index, parameter ->
            val name = if (parameter.name.isEmpty() && !nameInText) "$POSITIONAL_PREFIX$index" else parameter.name
            checkName(name, nameInText)
            if (!seen.add(name.lowercase(Locale.ROOT))) {
                throw RpcRejected(RpcRejection.PARAMETER_NAME)
            }
            map(parameter, name)
        }
    }

    private fun map(parameter: RpcParameter, name: String): QueryParameter {
        if (parameter.status and RpcParameter.STATUS_OUTPUT.inv() != 0) throw RpcRejected(RpcRejection.STATUS)
        val type = sqlType(parameter.type)
        val value = value(type, parameter.type, parameter.value)
        return QueryParameter(name, type, value, direction(parameter.output, value))
    }

    private fun direction(output: Boolean, value: Any?): ParameterDirection = when {
        !output -> ParameterDirection.INPUT
        value == null -> ParameterDirection.OUTPUT
        else -> ParameterDirection.INPUT_OUTPUT
    }

    private fun checkName(name: String, nameInText: Boolean) {
        val valid = name.length in 2..MAX_NAME_CHARS && name[0] == NAME_PREFIX &&
            !(nameInText && name[1] == NAME_PREFIX) &&
            name.drop(1).all { it.isLetterOrDigit() || it in "_@#$" }
        if (!valid) throw RpcRejected(RpcRejection.PARAMETER_NAME)
    }

    private fun sqlType(type: RpcType): SqlType = when (type.kind) {
        RpcTypeKind.INTN -> integerType(type.length)
        RpcTypeKind.BITN -> SqlType.Bool
        RpcTypeKind.NVARCHAR, RpcTypeKind.NCHAR -> SqlType.UnicodeText
        RpcTypeKind.DECIMALN, RpcTypeKind.NUMERICN -> SqlType.Decimal(type.precision, type.scale)
        RpcTypeKind.DATEN -> SqlType.Date
        RpcTypeKind.TIMEN -> SqlType.Time
        RpcTypeKind.DATETIME2N -> SqlType.DateTime
        RpcTypeKind.DATETIMN -> datetimeType(type)
        else -> throw RpcRejected(RpcRejection.TYPE)
    }

    private fun datetimeType(type: RpcType): SqlType =
        if (type.length == DATETIME_BYTES) SqlType.DateTime else throw RpcRejected(RpcRejection.TYPE)

    private fun integerType(length: Int): SqlType = when (length) {
        INT32_BYTES -> SqlType.Int32
        INT64_BYTES -> SqlType.Int64
        else -> throw RpcRejected(RpcRejection.TYPE)
    }

    private fun value(sqlType: SqlType, type: RpcType, value: RpcValue): Any? = when (value) {
        RpcValue.NullValue -> null
        is RpcValue.IntegerValue -> if (sqlType == SqlType.Int32) value.value.toInt() else value.value
        is RpcValue.BitValue -> value.value
        is RpcValue.TextValue -> value.value
        is RpcValue.RawValue -> RpcWireValues.decode(type, value.bytes)
    }
}
