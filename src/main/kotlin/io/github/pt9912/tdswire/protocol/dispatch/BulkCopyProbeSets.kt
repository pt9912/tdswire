package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.BulkProbeColumn
import io.github.pt9912.tdswire.protocol.result.ColumnMapping
import io.github.pt9912.tdswire.protocol.token.TypeInfoWriter
import io.github.pt9912.tdswire.protocol.wire.ByteWriter

/**
 * Das dritte und das vierte Ergebnis der Antwort auf die Probe der Zieltabelle von `SqlBulkCopy` ([BulkCopyProbe]),
 * von Hand geschrieben, weil das dritte eine binäre Spalte (`tds_collation`, `BINARY(5)`) trägt, die das Typmodell des
 * Backends und der Encoder nicht kennen. Gemessen (Messreihe M-8 bis M-12): (3) `colid` `INT` nicht nullbar, `name`,
 * `tds_collation` (Flags `0x0021`) und `collation`, ORDER auf `colid`, je Spalte der Tabelle eine Zeile: für Text ROW
 * mit den fünf Bytes der Kollation und ihrem Namen, sonst NBCROW mit NULL für `tds_collation` und `collation`;
 * (4) `Canonical_Column_Name` und `Aliased_Column_Name` ohne Zeile, ORDER auf das Ordinal 0. Spalten vom Typ `sysname`
 * (`NVARCHAR(128)`) tragen den Benutzertyp 256, ausgenommen `name` (0). Der Name der Kollation [COLLATION_NAME] ist
 * Setzung, am echten Server nur für `SQL_Latin1_General_CP1_CI_AS` aufgezeichnet.
 */
internal object BulkCopyProbeSets {
    /** Name der Kollation `ColumnMapping.COLLATION` (wie `CatalogAnswers`, am echten Server nicht gemessen). */
    const val COLLATION_NAME = "SQL_Latin1_General_CP1_CI_AS"

    private const val FLAGS_UPDATABLE_UNKNOWN = 0x0008
    private const val FLAGS_BINARY = 0x0021
    private const val SYSNAME_CHARS = 128
    private const val SYSNAME_USER_TYPE = 256
    private const val COLLATION_BYTES = 5
    private const val COLLATION_COLUMNS = 4
    private const val ALIAS_COLUMNS = 2
    private const val ORDER_BYTES = 2
    private const val ORDER_COLID = 1
    private const val ORDER_OUTSIDE = 0
    private const val TOKEN_COLMETADATA = 0x81
    private const val TOKEN_ORDER = 0xA9
    private const val TOKEN_ROW = 0xD1
    private const val TOKEN_NBCROW = 0xD2
    private const val TYPE_INT4 = 0x38
    private const val TYPE_BIGBINARY = 0xAD
    private const val TYPE_NVARCHAR = 0xE7
    private const val NULL_LAST_TWO = 0x0C
    private const val NAME_WHAT = "Spaltenname"
    private const val NULLABLE = TypeInfoWriter.FLAG_NULLABLE

    /** Ergebnis (3): COLMETADATA, ORDER auf `colid` und eine Zeile je Spalte der Tabelle. */
    fun collations(out: ByteWriter, columns: List<BulkProbeColumn>) {
        out.u8(TOKEN_COLMETADATA)
        out.u16Le(COLLATION_COLUMNS)
        out.i32Le(0)
        out.u16Le(FLAGS_UPDATABLE_UNKNOWN)
        out.u8(TYPE_INT4)
        out.bVarchar("colid", NAME_WHAT)
        sysname(out, FLAGS_UPDATABLE_UNKNOWN or NULLABLE, "name", userType = 0)
        out.i32Le(0)
        out.u16Le(FLAGS_BINARY)
        out.u8(TYPE_BIGBINARY)
        out.u16Le(COLLATION_BYTES)
        out.bVarchar("tds_collation", NAME_WHAT)
        sysname(out, FLAGS_UPDATABLE_UNKNOWN or NULLABLE, "collation", userType = SYSNAME_USER_TYPE)
        order(out, ORDER_COLID)
        columns.forEachIndexed { index, source -> row(out, index + 1, source) }
    }

    /** Ergebnis (4): die leere Tabelle der Alias-Zuordnung; ORDER nennt eine Spalte außerhalb der Auswahl. */
    fun aliases(out: ByteWriter) {
        out.u8(TOKEN_COLMETADATA)
        out.u16Le(ALIAS_COLUMNS)
        sysname(out, FLAGS_UPDATABLE_UNKNOWN, "Canonical_Column_Name", userType = SYSNAME_USER_TYPE)
        sysname(out, FLAGS_UPDATABLE_UNKNOWN, "Aliased_Column_Name", userType = SYSNAME_USER_TYPE)
        order(out, ORDER_OUTSIDE)
    }

    private fun order(out: ByteWriter, ordinal: Int) {
        out.u8(TOKEN_ORDER)
        out.u16Le(ORDER_BYTES)
        out.u16Le(ordinal)
    }

    private fun row(out: ByteWriter, id: Int, source: BulkProbeColumn) {
        val text = source.column.type == SqlType.UnicodeText
        out.u8(if (text) TOKEN_ROW else TOKEN_NBCROW)
        if (!text) out.u8(NULL_LAST_TWO)
        out.i32Le(id)
        counted(out, source.column.name)
        if (text) {
            out.u16Le(COLLATION_BYTES)
            TypeInfoWriter.writeCollation(out, ColumnMapping.COLLATION)
            counted(out, COLLATION_NAME)
        }
    }

    private fun sysname(out: ByteWriter, flags: Int, name: String, userType: Int) {
        out.i32Le(userType)
        out.u16Le(flags)
        out.u8(TYPE_NVARCHAR)
        out.u16Le(SYSNAME_CHARS * Short.SIZE_BYTES)
        TypeInfoWriter.writeCollation(out, ColumnMapping.COLLATION)
        out.bVarchar(name, NAME_WHAT)
    }

    /** Ein Wert mit Zwei-Byte-Länge in Byte (`NVARCHAR`). */
    private fun counted(out: ByteWriter, text: String) {
        out.u16Le(text.length * Short.SIZE_BYTES)
        out.utf16(text)
    }
}
