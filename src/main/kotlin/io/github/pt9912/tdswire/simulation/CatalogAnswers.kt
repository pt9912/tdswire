package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.SqlType

/** Wie eine Spalte im Katalog erscheint: Typname von `sys.types` und die Maße von `sys.columns`. */
internal data class CatalogType(val typeName: String, val maxLength: Int, val precision: Int, val scale: Int)

/**
 * Die Antworten auf die Katalogabfragen (`SPEC-036`, Form K), abgeleitet aus dem Katalog, den der Integrator der
 * Bibliothek mitteilt: Tabellen, Spalten und Schlüssel kommen aus den [SimTable]-Werten, alles andere (Sichten,
 * Sequenzen, Routinen, Fremdschlüssel, Indizes, Prüfbedingungen) ist ein leeres Ergebnis mit den Spalten der Abfrage.
 * Eine Identität, eine Berechnung und ein Standardwert kommen nicht vor (`is_identity` ist 0, `default_definition`
 * NULL; Setzung, `SimColumn` trägt keine). Ein unbekanntes Schema oder eine unbekannte Tabelle ist ein leeres
 * Ergebnis, kein Fehler (`OBJECT_ID` des Servers liefert dann NULL).
 *
 * Der Server meldet `max_length` in Byte: ein `nvarchar(n)` hat `2 * n` (Setzung, am Server nicht gemessen; gemessen
 * ist der Lauf mit d-migrate, der 4000 Zeichen als `max_length: 4000` liest). Die Typen und Maße von `bigint`, `bit`,
 * `time` und `datetime2` (Skala der Zeittypen 7) sowie die Maße von `smallint` (2 Byte, Precision 5, Skala 0) und
 * `tinyint` (1, 3, 0) sind am Referenzclient gemessen (M-17 des Referenzabgleichs, beide Instanzen).
 */
internal object CatalogAnswers {
    private const val DEFAULT_COLLATION = "SQL_Latin1_General_CP1_CI_AS"
    private const val NVARCHAR_BYTES = 2
    private const val BIGINT_PRECISION = 19
    private const val INT_PRECISION = 10
    private const val DATE_PRECISION = 10
    private const val TIME_PRECISION = 16
    private const val TIME_SCALE = 7
    private const val DATETIME2_PRECISION = 27
    private const val DATETIME2_BYTES = 8
    private const val TIME_BYTES = 5
    private const val DATE_BYTES = 3
    private const val BIGINT_BYTES = 8
    private const val INT_BYTES = 4
    private const val SMALLINT_BYTES = 2
    private const val SMALLINT_PRECISION = 5
    private const val TINYINT_BYTES = 1
    private const val TINYINT_PRECISION = 3
    private const val MAX_DIGITS_5_BYTES = 9
    private const val MAX_DIGITS_9_BYTES = 19
    private const val MAX_DIGITS_13_BYTES = 28
    private const val DECIMAL_BYTES_SMALL = 5
    private const val DECIMAL_BYTES_MEDIUM = 9
    private const val DECIMAL_BYTES_LARGE = 13
    private const val DECIMAL_BYTES_FULL = 17

    fun of(form: CatalogForm, catalog: SimCatalog, parameters: List<QueryParameter>): List<QueryEvent> {
        val argument = if (form.template.contains('?')) parameters.firstOrNull()?.value else null
        if (form.template.contains('?') && argument != null && argument !is String) {
            return listOf(QueryEvent.Failure(SimCodes.INVALID_VALUE, "Parameter der Katalogabfrage ist kein Text"))
        }
        val rows = rows(form, catalog, argument as String?)
        return buildList {
            add(QueryEvent.ResultStart(form.columns))
            rows.forEach { add(QueryEvent.Row(it)) }
            add(QueryEvent.ResultEnd(rows.size.toLong()))
        }
    }

    private fun rows(form: CatalogForm, catalog: SimCatalog, arg: String?): List<List<Any?>> = when (form.source) {
        CatalogSource.EMPTY -> emptyList()
        CatalogSource.SCHEMA_NAME -> listOf(listOf(DEFAULT_SCHEMA))
        CatalogSource.TABLES -> tables(catalog, arg)
        CatalogSource.COLUMNS -> columns(tableOf(catalog, arg))
        CatalogSource.PRIMARY_KEY -> tableOf(catalog, arg)?.primaryKey.orEmpty().map { listOf(it) }
        CatalogSource.COLLATIONS -> tableOf(catalog, arg)?.columns.orEmpty().map { listOf(collationOf(it), false) }
    }

    private fun tables(catalog: SimCatalog, schema: String?): List<List<Any?>> {
        if (schema == null) return emptyList()
        return catalog.tables
            .filter { SimNames.fold(it.schema) == SimNames.fold(schema) }
            .sortedBy { it.name }
            .map { listOf(it.name, it.schema) }
    }

    private fun tableOf(catalog: SimCatalog, objectName: String?): SimTable? {
        val qualified = objectName?.let { QualifiedName.parse(it) } ?: return null
        return catalog.find(qualified.schema, qualified.name)
    }

    private fun columns(table: SimTable?): List<List<Any?>> = table?.columns.orEmpty().mapIndexed { index, column ->
        val type = typeOf(column)
        listOf(
            column.name, type.typeName, type.maxLength, type.precision, type.scale, column.nullable, false, false,
            index + 1, null, null, null, null, null,
        )
    }

    /** Typname und Maße einer Spalte; Zuordnung und Quelle der Werte: Kopf des Objekts. */
    fun typeOf(column: SimColumn): CatalogType = when (val type = column.type) {
        SqlType.UInt8 -> CatalogType("tinyint", TINYINT_BYTES, TINYINT_PRECISION, 0)
        SqlType.Int16 -> CatalogType("smallint", SMALLINT_BYTES, SMALLINT_PRECISION, 0)
        SqlType.Int32 -> CatalogType("int", INT_BYTES, INT_PRECISION, 0)
        SqlType.Int64 -> CatalogType("bigint", BIGINT_BYTES, BIGINT_PRECISION, 0)
        SqlType.Bool -> CatalogType("bit", 1, 1, 0)
        SqlType.UnicodeText -> CatalogType("nvarchar", textBytes(column.maxLength), 0, 0)
        is SqlType.Decimal -> CatalogType("decimal", decimalBytes(type.precision), type.precision, type.scale)
        SqlType.Date -> CatalogType("date", DATE_BYTES, DATE_PRECISION, 0)
        SqlType.Time -> CatalogType("time", TIME_BYTES, TIME_PRECISION, TIME_SCALE)
        SqlType.DateTime -> CatalogType("datetime2", DATETIME2_BYTES, DATETIME2_PRECISION, TIME_SCALE)
    }

    /**
     * Die Kollation einer Textspalte; jede andere Spalte meldet NULL wie der Server. Der Name ist Setzung (die
     * Kollation der Datenbank eines echten Servers ist nicht gemessen).
     */
    private fun collationOf(column: SimColumn): String? =
        if (column.type == SqlType.UnicodeText) DEFAULT_COLLATION else null

    /** Länge eines `nvarchar` in Byte; `nvarchar(max)` meldet der Server als -1. */
    private fun textBytes(length: Int): Int = if (length == UNBOUNDED_TEXT) UNBOUNDED_TEXT else NVARCHAR_BYTES * length

    /** Speicher einer Dezimalzahl nach ihrer Precision: 5, 9, 13 oder 17 Byte. */
    private fun decimalBytes(precision: Int): Int = when {
        precision <= MAX_DIGITS_5_BYTES -> DECIMAL_BYTES_SMALL
        precision <= MAX_DIGITS_9_BYTES -> DECIMAL_BYTES_MEDIUM
        precision <= MAX_DIGITS_13_BYTES -> DECIMAL_BYTES_LARGE
        else -> DECIMAL_BYTES_FULL
    }
}

/** Ein Name aus `OBJECT_ID(?)`: höchstens zwei Teile, jeder unquotiert oder in `[…]`. */
internal data class QualifiedName(val schema: String?, val name: String) {
    companion object {
        /** Der Name oder `null`, wenn der Text keiner mit einem oder zwei Teilen ist. */
        fun parse(text: String): QualifiedName? {
            val parts = split(text)
            return when (parts?.size) {
                1 -> QualifiedName(null, parts[0])
                2 -> QualifiedName(parts[0], parts[1])
                else -> null
            }
        }

        /** Die Teile von [text] durch `.` getrennt, oder `null`, wenn ein Teil fehlt oder eine Klammer offen bleibt. */
        private fun split(text: String): List<String>? {
            val parts = ArrayList<String>(2)
            var index = 0
            var valid = true
            while (valid && index <= text.length) {
                val part = part(text, index)
                valid = part != null && (part.second >= text.length || text[part.second] == '.')
                if (part != null) {
                    parts += part.first
                    index = part.second + 1
                }
            }
            return parts.takeIf { valid }
        }

        private fun part(text: String, from: Int): Pair<String, Int>? {
            if (from < text.length && text[from] == '[') return SqlLexemes.readDelimited(text, from + 1, ']')
            val end = text.indexOf('.', from).let { if (it < 0) text.length else it }
            return text.substring(from, end).takeIf { it.isNotEmpty() && '[' !in it && ']' !in it }?.let { it to end }
        }
    }
}
