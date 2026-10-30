package io.github.pt9912.tdswire.transport.framing

/**
 * Pakettyp im TDS-Header (MS-TDS 2.2.3.1.1).
 *
 * Festlegung: Der Katalog nennt die Typen, die der Server liest oder schreibt; ein anderer Wert im
 * Header ist ein Protokollfehler. [PRELOGIN] trägt auch die TLS-Handshake-Daten des TDS-7.x-Pfads,
 * der Codec kapselt sie nicht.
 */
enum class PacketType(val code: Int) {
    SQL_BATCH(CODE_SQL_BATCH),
    PRE_TDS7_LOGIN(CODE_PRE_TDS7_LOGIN),
    RPC(CODE_RPC),
    TABULAR_RESULT(CODE_TABULAR_RESULT),
    ATTENTION(CODE_ATTENTION),
    BULK_LOAD(CODE_BULK_LOAD),
    FEDERATED_AUTH(CODE_FEDERATED_AUTH),
    TRANSACTION_MANAGER(CODE_TRANSACTION_MANAGER),
    LOGIN7(CODE_LOGIN7),
    SSPI(CODE_SSPI),
    PRELOGIN(CODE_PRELOGIN),
    ;

    companion object {
        /** Der Typ zum Headerbyte oder `null` für einen Wert außerhalb des Katalogs. */
        fun fromCode(code: Int): PacketType? = entries.firstOrNull { it.code == code }
    }
}

private const val CODE_SQL_BATCH = 0x01
private const val CODE_PRE_TDS7_LOGIN = 0x02
private const val CODE_RPC = 0x03
private const val CODE_TABULAR_RESULT = 0x04
private const val CODE_ATTENTION = 0x06
private const val CODE_BULK_LOAD = 0x07
private const val CODE_FEDERATED_AUTH = 0x08
private const val CODE_TRANSACTION_MANAGER = 0x0E
private const val CODE_LOGIN7 = 0x10
private const val CODE_SSPI = 0x11
private const val CODE_PRELOGIN = 0x12
