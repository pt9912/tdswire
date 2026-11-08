package io.github.pt9912.tdswire.protocol.token

import io.github.pt9912.tdswire.domain.StatementKind

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

/** ENVCHANGE (`0xE3`) mit der Art der Änderung. Festlegung: Der Katalog umfasst Typ 1, 4, 7, 8, 9 und 18. */
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

    /**
     * Typ 8: Beginn einer Transaktion, der neue Wert ist der Deskriptor (8 Byte), der alte Wert ist leer. Am echten
     * SQL Server nach `CREATE TABLE` bei `set implicit_transactions on` gemessen (Messprotokoll M-20, 2022 und 2025).
     */
    data class BeginTransaction(val descriptor: Long) : EnvChange

    /**
     * Typ 9: Ende einer Transaktion durch `COMMIT`, der neue Wert ist leer, der alte Wert ist der Deskriptor (8 Byte).
     * Am echten SQL Server gemessen (Messprotokoll M-20, 2022 und 2025).
     */
    data class CommitTransaction(val descriptor: Long) : EnvChange
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
 * Form des Ereignisses nicht ([forKind] bildet die Art eines Änderungszählers ab). [INSERT_BULK] ist der Befehl der
 * Antwort auf eine Nachricht des Massenladens, gemessen mit `SqlBulkCopy` (Messprotokoll M-8 und M-12, Instanz 2022
 * und 2025), mit Zähler bei Erfolg und mit `ERROR` bei einem Schlüsselverstoß.
 */
internal object DoneCommand {
    const val NONE = 0x0000
    const val SELECT = 0x00C1
    const val BATCH = 0x00FD
    const val PROC = 0x00E0
    const val INSERT_BULK = 0x00F0
    private const val INSERT = 0x00C3
    private const val DELETE = 0x00C4
    private const val UPDATE = 0x00C5
    private const val CREATE_TABLE = 0x00C6
    private const val ALTER_TABLE = 0x00D8
    private const val IMPLICIT_TRANSACTIONS_ON = 0x00B9
    private const val IMPLICIT_TRANSACTIONS_OFF = 0x00BA
    private const val COMMIT_IF_OPEN = 0x00C0

    /** Der Abschluss des `COMMIT` hinter einer offenen Transaktion (Messprotokoll M-20). */
    const val COMMIT_TRAN = 0x00D5

    /**
     * Die Kennung des Abschlusses der Art [kind] (Messprotokoll W-6 und M-20); [NONE] für `UNKNOWN`. Die Arten aus M-20
     * sind im SQL_BATCH gemessen; die Form RPC wendet ihre Kennung nicht an (`ResponseRun`).
     */
    fun forKind(kind: StatementKind): Int = when (kind) {
        StatementKind.UNKNOWN -> NONE
        StatementKind.INSERT -> INSERT
        StatementKind.DELETE -> DELETE
        StatementKind.UPDATE -> UPDATE
        StatementKind.CREATE_TABLE -> CREATE_TABLE
        StatementKind.ALTER_TABLE -> ALTER_TABLE
        StatementKind.IMPLICIT_TRANSACTIONS_ON -> IMPLICIT_TRANSACTIONS_ON
        StatementKind.IMPLICIT_TRANSACTIONS_OFF -> IMPLICIT_TRANSACTIONS_OFF
        StatementKind.COMMIT_IF_OPEN -> COMMIT_IF_OPEN
    }

    /**
     * Wahr, wenn der Abschluss der Art [kind] im SQL_BATCH eine Zeilenzahl trägt (Bit `COUNT`): `UNKNOWN` und die
     * Änderungen (W-6); die Arten aus M-20 schließen ohne.
     */
    fun countsRows(kind: StatementKind): Boolean = when (kind) {
        StatementKind.UNKNOWN, StatementKind.INSERT, StatementKind.DELETE, StatementKind.UPDATE -> true

        StatementKind.CREATE_TABLE,
        StatementKind.ALTER_TABLE,
        StatementKind.IMPLICIT_TRANSACTIONS_ON,
        StatementKind.IMPLICIT_TRANSACTIONS_OFF,
        StatementKind.COMMIT_IF_OPEN,
        -> false
    }
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
