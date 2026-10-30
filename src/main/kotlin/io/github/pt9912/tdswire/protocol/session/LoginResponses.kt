package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.protocol.token.Collation
import io.github.pt9912.tdswire.protocol.token.DoneKind
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.EnvChange
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.LoginAck
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.transport.framing.FramingLimits
import io.github.pt9912.tdswire.transport.framing.PacketHeader
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage

/**
 * Die Antworten auf LOGIN7 (`TDS-FA-02.a`, `SPEC-012`, `SPEC-028`).
 *
 * Gemessen am Referenzclient gegen den Spike-Testserver (nicht am Produkt, TDS 7.4, ohne Pool):
 * LOGINACK mit DONE genügt dem Client beim `Open`, die Collation-ENVCHANGE braucht der RPC mit
 * Zeichenparametern, die Ablehnung braucht genau ein ERROR mit DONE (`ADR-0009`, `ADR-0005`).
 * Festlegung ohne Messung: die Reihenfolge der Tokens (ENVCHANGE Datenbank, Packet Size, Collation,
 * dann LOGINACK, dann DONE), die ENVCHANGE für Datenbank und Packet Size und ihre Werte, die
 * Collation `LATIN1_GENERAL_CI_AS`, Programmname und -version, die TDS-Version in LOGINACK je
 * Reihenfolge (TDS 8.0: `0x08000000`), die Statusbits `FINAL` der Annahme und `ERROR` der
 * Ablehnung, der State 1 und der feste Text der Ablehnung ohne Kennung.
 */
internal object LoginResponses {
    const val LOGIN_FAILED_NUMBER = 18456
    const val LOGIN_FAILED_STATE = 1
    const val LOGIN_FAILED_CLASS = 14
    const val LOGIN_FAILED_TEXT = "Login failed."
    const val DEFAULT_PACKET_SIZE = 4096
    const val TDS_7_4 = 0x74000004
    const val TDS_8_0 = 0x08000000
    private const val INTERFACE_SQL_TSQL = 1
    private const val PROGRAM_NAME = "tdswire"
    private const val PROGRAM_VERSION = 0x01000000

    /** Die feste Ablehnung: genau ein ERROR, dann DONE mit Fehlerstatus; für jeden Grund dieselben Bytes. */
    val rejection: TdsMessage by lazy {
        val error = ServerMessage(
            number = LOGIN_FAILED_NUMBER,
            state = LOGIN_FAILED_STATE,
            severity = LOGIN_FAILED_CLASS,
            text = LOGIN_FAILED_TEXT,
        )
        result(ErrorToken(error), DoneToken(DoneKind.DONE, DoneStatus.ERROR, curCmd = 0, rowCount = 0))
    }

    /** Die Annahme für eine Sitzung mit [database] und der ausgehandelten [packetSize]. */
    fun acceptance(order: HandshakeOrder, database: String, packetSize: Int): TdsMessage = result(
        EnvChange.Database(newValue = database, oldValue = ""),
        EnvChange.PacketSize(newValue = packetSize, oldValue = DEFAULT_PACKET_SIZE),
        EnvChange.SqlCollation(newValue = Collation.LATIN1_GENERAL_CI_AS, oldValue = null),
        LoginAck(
            interfaceType = INTERFACE_SQL_TSQL,
            tdsVersion = if (order == HandshakeOrder.TLS_FIRST) TDS_8_0 else TDS_7_4,
            progName = PROGRAM_NAME,
            progVersion = PROGRAM_VERSION,
        ),
        DoneToken(DoneKind.DONE, DoneStatus.FINAL, curCmd = 0, rowCount = 0),
    )

    /**
     * Die Packet Size der Sitzung. Festlegung ohne Messung: die verlangte Größe, wenn der Paket-Codec
     * sie schreiben kann, sonst [DEFAULT_PACKET_SIZE].
     */
    fun negotiatedPacketSize(requested: Int): Int =
        if (requested > PacketHeader.SIZE && requested <= FramingLimits.MAX_TDS_PACKET_BYTES) {
            requested
        } else {
            DEFAULT_PACKET_SIZE
        }

    private fun result(vararg tokens: Token): TdsMessage {
        val encoder = TokenEncoder()
        tokens.forEach { token ->
            check(encoder.write(token) is ProtocolResult.Ok) { "Login-Antwort nicht schreibbar" }
        }
        return TdsMessage(PacketType.TABULAR_RESULT, encoder.toByteArray())
    }
}
