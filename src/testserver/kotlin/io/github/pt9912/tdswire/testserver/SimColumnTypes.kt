package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.SqlType

/** Die Felder einer Spalte, die den Typ bestimmen; die Ganzzahlen und der Wahrheitswert sind schon geprüft. */
internal data class ColumnTypeFields(
    val typeName: String,
    val typePosition: SimPosition,
    val precision: Pair<Int, SimPosition>?,
    val scale: Pair<Int, SimPosition>?,
    val timezone: Boolean,
)

/**
 * Typabbildung der Datei (`SPEC-035`, nach ADR-0012 Entscheidung 8): zehn der 21 neutralen Typen haben
 * einen Typ des Mindestkatalogs, jeder andere weist die Datei ab. Der Typname steht in der Meldung nur,
 * wenn er einer der 21 bekannten Namen ist.
 */
internal object SimColumnTypes {
    /** Höchste Precision der Dezimalzahl (`SPEC-003`); darüber weist der Leser ab. */
    const val MAX_PRECISION = 38

    private val UNMAPPED = setOf(
        "smallint", "char", "float", "uuid", "binary", "xml", "json", "array", "enum", "geometry", "fulltext",
    )

    /** Der Typ der Spalte; `identifier` ist nicht nullbar, das entscheidet [isIdentifier]. */
    fun map(fields: ColumnTypeFields, path: String, columnPosition: SimPosition): SqlType {
        val typePath = SimTreeAccess.child(path, "type")
        return when (fields.typeName) {
            "identifier", "integer" -> SqlType.Int32
            "biginteger" -> SqlType.Int64
            "boolean" -> SqlType.Bool
            "text", "email" -> SqlType.UnicodeText
            "date" -> SqlType.Date
            "time" -> SqlType.Time
            "datetime" -> datetime(fields, typePath)
            "decimal" -> decimal(fields, path, columnPosition)
            in UNMAPPED -> fail(typePath, fields, "Typ ${fields.typeName} ohne Entsprechung")
            else -> fail(typePath, fields, "Typ unbekannt")
        }
    }

    private fun fail(path: String, fields: ColumnTypeFields, reason: String): Nothing =
        SimTreeAccess.fail(path, fields.typePosition, reason)

    private fun datetime(fields: ColumnTypeFields, typePath: String): SqlType {
        if (fields.timezone) fail(typePath, fields, "Typ datetime mit timezone: true ohne Entsprechung")
        return SqlType.DateTime
    }

    fun isIdentifier(typeName: String): Boolean = typeName == "identifier"

    private fun decimal(fields: ColumnTypeFields, path: String, columnPosition: SimPosition): SqlType {
        val precision = fields.precision ?: SimTreeAccess.fail(path, columnPosition, "precision fehlt bei decimal")
        val scale = fields.scale ?: SimTreeAccess.fail(path, columnPosition, "scale fehlt bei decimal")
        if (precision.first > MAX_PRECISION) {
            val here = SimTreeAccess.child(path, "precision")
            SimTreeAccess.fail(here, precision.second, "precision über $MAX_PRECISION")
        }
        if (scale.first > precision.first) {
            SimTreeAccess.fail(SimTreeAccess.child(path, "scale"), scale.second, "scale größer als precision")
        }
        return SqlType.Decimal(precision.first, scale.first)
    }
}
