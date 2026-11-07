package io.github.pt9912.tdswire.protocol.token

/** Ein Token der Antwortseite (MS-TDS 2.2.7); [TokenEncoder] schreibt es in den Tokenstrom. */
internal sealed interface Token

/**
 * LOGINACK (`0xAD`): Schnittstelle (1 = SQL_TSQL), TDS-Version (`0x74000004` für 7.4), Programmname
 * und Programmversion (Haupt-, Neben-Version und zwei Bytes Build in den vier Bytes der Zahl).
 * Die Bytefolge der TDS-Version (`74 00 00 04`) ist am Spike-Testserver gemessen (`ADR-0005`,
 * Abschnitt „Weitere Befunde der Messung“). Festlegung ohne Orakel: Die Programmversion steht
 * ebenfalls Big-Endian im Token; dafür liegt weder Messung noch unabhängiger Vektor vor.
 */
internal data class LoginAck(val interfaceType: Int, val tdsVersion: Int, val progName: String, val progVersion: Int) :
    Token

/** ENVCHANGE (`0xE3`) mit der Art der Änderung. Festlegung: Der Katalog umfasst Typ 1, 4, 7 und 18. */
internal sealed interface EnvChange : Token {
    /**
     * Typ 18: Rücksetzen der Verbindung, neuer und alter Wert leer. Erstes Token der Antwort auf eine Anfrage mit dem
     * Statusbit `RESETCONNECTION`; am echten SQL Server gemessen (Messprotokoll W-7, 2022 und 2025, Batch).
     */
    data object ResetConnection : EnvChange

    /** Typ 1: Datenbank. */
    data class Database(val newValue: String, val oldValue: String) : EnvChange

    /** Typ 4: Packet Size, beide Werte als Dezimaltext im Token. */
    data class PacketSize(val newValue: Int, val oldValue: Int) : EnvChange

    /** Typ 7: SQL-Kollation; `oldValue` ist `null`, wenn es keine vorige gab. */
    data class SqlCollation(val newValue: Collation, val oldValue: Collation?) : EnvChange
}

/** Nummer, Zustand, Schweregrad, Text und Herkunft einer INFO- oder ERROR-Meldung. */
internal data class ServerMessage(
    val number: Int,
    val state: Int,
    val severity: Int,
    val text: String,
    val serverName: String = "",
    val procName: String = "",
    val lineNumber: Int = 0,
)

/** INFO (`0xAB`). */
internal data class InfoToken(val message: ServerMessage) : Token

/** ERROR (`0xAA`). */
internal data class ErrorToken(val message: ServerMessage) : Token

/** Statusbits von DONE, DONEPROC und DONEINPROC (MS-TDS 2.2.7.6). */
internal object DoneStatus {
    const val FINAL = 0x0000
    const val MORE = 0x0001
    const val ERROR = 0x0002
    const val IN_TRANSACTION = 0x0004
    const val COUNT = 0x0010
    const val ATTENTION = 0x0020
    const val RPC_IN_BATCH = 0x0080
    const val SERVER_ERROR = 0x0100
}

/**
 * Befehlskennungen (`curCmd`) der DONE-Familie, am echten SQL Server gemessen (Messprotokoll W-6, Einzelmessung je
 * Instanz 2022 und 2025): [SELECT] für den Abschluss eines Ergebnisses, [BATCH] für einen Batch ohne Ergebnis, einen
 * Fehler ohne offenes Ergebnis und ATTENTION, [PROC] für DONEPROC. [NONE] sagt „keine Aussage“: der Wert hängt an der
 * Form des Ereignisses nicht (etwa INSERT, UPDATE, DELETE). [INSERT_BULK] ist der Befehl der Antwort auf eine Nachricht
 * des Massenladens, gemessen mit `SqlBulkCopy` (Messprotokoll M-8 und M-12, Instanz 2022 und 2025), mit Zähler bei
 * Erfolg und mit `ERROR` bei einem Schlüsselverstoß.
 */
internal object DoneCommand {
    const val NONE = 0x0000
    const val SELECT = 0x00C1
    const val BATCH = 0x00FD
    const val PROC = 0x00E0
    const val INSERT_BULK = 0x00F0
}

/** Die drei Formen des Abschlusstokens. */
internal enum class DoneKind(val code: Int) {
    DONE(CODE_DONE),
    DONE_PROC(CODE_DONE_PROC),
    DONE_IN_PROC(CODE_DONE_IN_PROC),
}

private const val CODE_DONE = 0xFD
private const val CODE_DONE_PROC = 0xFE
private const val CODE_DONE_IN_PROC = 0xFF

/**
 * DONE, DONEPROC oder DONEINPROC: Statusbits ([DoneStatus]), Befehlskennung und Zeilenzahl.
 * Festlegung: Der Encoder schreibt die Zeilenzahl so, wie sie gegeben ist, auch ohne das Bit
 * `COUNT`; ob sie gilt, entscheidet der Aufrufer über das Bit.
 */
internal data class DoneToken(val kind: DoneKind, val status: Int, val curCmd: Int, val rowCount: Long) : Token

/** COLMETADATA (`0x81`) mit den Spalten; eine leere Liste schreibt `0x0000` Spalten. */
internal data class ColMetadataToken(val columns: List<ColumnDef>) : Token

/** ROW (`0xD1`): ein Wert je Spalte des zuletzt geschriebenen COLMETADATA. */
internal data class RowToken(val values: List<TdsValue>) : Token

/** RETURNSTATUS (`0x79`): der Rückgabewert einer Prozedur. */
internal data class ReturnStatusToken(val value: Int) : Token

/** RETURNVALUE (`0xAC`): Ausgabeparameter mit Ordinal, Name (so, wie der Aufrufer ihn nennt), Status, Typ und Wert. */
internal data class ReturnValueToken(
    val ordinal: Int,
    val name: String,
    val status: Int,
    val column: ColumnDef,
    val value: TdsValue,
) : Token
