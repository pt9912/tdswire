package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.BulkRows
import java.math.BigDecimal
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Das Laden einer Nachricht 0x07 in eine Tabelle (`ADR-0033` Nr. 5, `SPEC-035`): ordnet die Spalten der Nachricht den
 * Spalten der Beschreibung zu (ohne Beachtung der Schreibweise), überführt jeden Wert in den Typ seiner Spalte und
 * gibt alle Zeilen der Nachricht in einem Aufruf an `onInsertBatch`; hat die Tabelle nur `onInsert`, ruft sie ihn je
 * Zeile in der Reihenfolge der Nachricht (Setzung: dann ist ein Fehler in der Mitte ein Teilbestand, keine
 * Transaktion). Es wird erst überführt, dann gerufen: ein Wert, der nicht passt, endet mit `TDS_SIM_INVALID_VALUE`,
 * bevor eine Zeile den Integrator erreicht. Eine Nachricht ohne Zeile ruft keinen Callback und zählt 0.
 *
 * Ein Fehler des Callbacks meldet die Bibliothek als `TDS_SIM_KEY_VIOLATION` (2627, Class 14): so antwortet der echte
 * Server auf den Schlüsselverstoß einer Nachricht, und keine Zeile der Nachricht bleibt (Messprotokoll M-12, ein Fall,
 * `SqlBulkCopy`). Das weicht vom Wortlaut von `ADR-0033` Nr. 5 (iii) („Fehler des Callbacks“, `TDS_BACKEND_ERROR`) und
 * Nr. 6 („Teilbestand“) ab; die Folge-ADR ist Slice `slice-adr-massenladen-atomar-an-messung`, bis dahin folgt der
 * Code der Messung. Ob der Callback atomar arbeitet, entscheidet der Integrator; die Bibliothek nimmt nichts zurück.
 *
 * Überführung der Werte der Nachricht (Setzung, nicht am Server gemessen): `Int` und `Long` gelten in einer
 * `Int32`-Spalte, wenn sie in deren Wertebereich passen (der Treiber sendet auch Werte einer `INT`-Spalte als 8 Byte),
 * ebenso in `Int16` (−32768 bis 32767) und `UInt8` (0 bis 255), in `Int64` immer; `Boolean` in `Bool`, `0` und `1`
 * ebenso; `BigDecimal`, `Int` und `Long` in `Decimal`, wenn die Zahl ohne Rundung und Überlauf in Precision und
 * Skala passt; ein Text in `UnicodeText`, und, im ISO-Format ([SimValues.dateTime] für den Zeitpunkt), in `Date`,
 * `Time` und `DateTime` (so sendet der Treiber sie als `VARCHAR`); ein Datum, eine Zeit und ein Zeitpunkt in der
 * Spalte ihres Typs. Jede andere Paarung ist ungültig. NULL gilt für jede Spalte (wie in `SimValues`: der Schlüssel
 * und die Nullbarkeit wirken nicht auf Callbacks). Die Meldungen nennen nie einen Wert.
 */
internal object BulkLoads {
    private const val INVALID = "Wert nicht in den Spaltentyp überführbar"
    private const val DUPLICATE = "Spalte doppelt in der Nachricht"
    private const val NO_CALLBACK = "Einfügen für diese Tabelle nicht verfügbar"
    private const val FAILED = "Änderung fehlgeschlagen"

    /** Lädt [rows] in [table] und liefert das Ereignis, mit dem die Antwort endet. */
    suspend fun load(table: SimTable, rows: BulkRows): QueryEvent {
        val columns = rows.columns.map { table.column(it.name) }
        return when {
            columns.any { it == null } -> QueryEvent.Failure(SimCodes.UNKNOWN_OBJECT, "Spalte nicht beschrieben")

            SimNames.firstDuplicate(rows.columns.map { it.name }) != null ->
                QueryEvent.Failure(SimCodes.UNSUPPORTED_SQL, DUPLICATE)

            else -> when (val mapped = convert(rows, columns.map { requireNotNull(it) })) {
                is Mapped.Bad -> QueryEvent.Failure(SimCodes.INVALID_VALUE, mapped.reason)
                is Mapped.Rows -> call(table, mapped.rows)
            }
        }
    }

    private sealed interface Mapped {
        class Rows(val rows: List<Map<String, Any?>>) : Mapped

        class Bad(val reason: String) : Mapped
    }

    private fun convert(rows: BulkRows, columns: List<SimColumn>): Mapped {
        val converted = ArrayList<Map<String, Any?>>(rows.rows.size)
        for (row in rows.rows) {
            val values = LinkedHashMap<String, Any?>(columns.size)
            for ((index, column) in columns.withIndex()) {
                when (val value = value(row[index], column.type)) {
                    is Converted.Value -> values[column.name] = value.value
                    is Converted.Invalid -> return Mapped.Bad(value.reason)
                }
            }
            converted += values
        }
        return Mapped.Rows(converted)
    }

    private suspend fun call(table: SimTable, rows: List<Map<String, Any?>>): QueryEvent {
        val batch = table.onInsertBatch
        val single = table.onInsert
        val result = when {
            rows.isEmpty() -> Result.success(0L)
            batch != null -> guarded { batch(rows) }
            single != null -> guarded { rows.sumOf { single(it) } }
            else -> null
        } ?: return QueryEvent.Failure(SimCodes.UNSUPPORTED_SQL, NO_CALLBACK)
        return result.fold(
            onSuccess = { count ->
                if (count >= 0) QueryEvent.UpdateCount(count) else QueryEvent.Failure(SimCodes.BACKEND_ERROR, FAILED)
            },
            onFailure = { QueryEvent.Failure(SimCodes.KEY_VIOLATION, FAILED) },
        )
    }

    private fun value(value: Any?, target: SqlType): Converted =
        if (value == null) Converted.Value(null) else converted(value, target) ?: Converted.Invalid(INVALID)

    private fun converted(value: Any, target: SqlType): Converted? = when (target) {
        SqlType.UInt8, SqlType.Int16, SqlType.Int32, SqlType.Int64 -> SimIntegers.load(value, target)

        SqlType.Bool -> bool(value)

        SqlType.UnicodeText -> (value as? String)?.let { Converted.Value(it) }

        is SqlType.Decimal -> SimValues.widenToDecimal(value, target).takeIf {
            value is BigDecimal || value is Int || value is Long
        }

        SqlType.Date -> temporal(value, LocalDate::class.java) { LocalDate.parse(it) }

        SqlType.Time -> temporal(value, LocalTime::class.java) { LocalTime.parse(it) }

        SqlType.DateTime -> temporal(value, LocalDateTime::class.java) { SimTemporal.dateTime(it) }
    }

    private fun bool(value: Any): Converted? = when {
        value is Boolean -> Converted.Value(value)
        value is Int && (value == 0 || value == 1) -> Converted.Value(value == 1)
        value is Long && (value == 0L || value == 1L) -> Converted.Value(value == 1L)
        else -> null
    }

    private fun <T : Any> temporal(value: Any, type: Class<T>, parse: (String) -> T): Converted? = when {
        type.isInstance(value) -> Converted.Value(value)

        value is String -> try {
            Converted.Value(parse(value))
        } catch (ignored: DateTimeException) {
            null
        }

        else -> null
    }
}
