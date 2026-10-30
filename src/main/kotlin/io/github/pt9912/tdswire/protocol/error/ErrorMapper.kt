package io.github.pt9912.tdswire.protocol.error

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.protocol.token.DoneKind
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.protocol.wire.ProtocolError
import io.github.pt9912.tdswire.protocol.wire.ProtocolErrorCategory
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage

/**
 * Antwort auf einen Fehler: [message] ist ERROR gefolgt von DONE mit Fehlerstatus, `null` genau bei
 * [ConnectionFate.CLOSE_WITHOUT_REPLY].
 */
class ErrorResponse(val code: ErrorCode, val message: TdsMessage?, val fate: ConnectionFate) {
    init {
        require((message == null) == (fate == ConnectionFate.CLOSE_WITHOUT_REPLY)) {
            "Antwort und Verbindungsfolge passen nicht zusammen"
        }
    }
}

/**
 * Log-Eintrag des Mappings (`SPEC-048`). Trägt den Code und, wenn eine Ausnahme oder ein fremder
 * Code der Anlass war, deren Typnamen; weder den Text einer Ausnahme noch Parameterwerte.
 */
data class ErrorLogEntry(val code: ErrorCode, val cause: String?)

/** Anschluss für [ErrorLogEntry]; der Aufruf wirft nicht und blockiert nicht. */
fun interface ErrorLog {
    fun record(entry: ErrorLogEntry)

    companion object {
        val NONE = ErrorLog { }
    }
}

/**
 * Das Fehler-Mapping (`TDS-FA-06.a`, `ADR-0007`): bildet einen Fehler auf ERROR und DONE mit
 * Fehlerstatus und die Verbindungsfolge ab. Rein bis auf den Log-Aufruf; kennt keinen Strom und
 * keine Sitzung. Der Sequenzierer erhält die Methoden als Funktionsparameter.
 *
 * Festlegung (nicht gemessen): Der Text jeder Antwort ist der feste Text der Regel; der Text einer
 * [QueryEvent.Failure] und der einer Ausnahme erreichen den Client nie. Das DONE ist `DONE` mit
 * Statusbit `ERROR`, Befehlskennung 0 und Zeilenzahl 0 (Bytes nur als Handvektor belegt, nicht am
 * Referenzclient gemessen). Ist der Strom nicht synchron, entfällt die Antwort (`SPEC-031`).
 */
class ErrorMapper(private val log: ErrorLog = ErrorLog.NONE) {
    /** Ein Fehler mit eigenem Code. */
    fun code(code: ErrorCode, streamSynchronous: Boolean = true): ErrorResponse =
        response(code, streamSynchronous, null)

    /**
     * Ein erwarteter Fehler des Backends. Ein Code, der kein [ErrorCode] ist, ist keine erwartete
     * Ablehnung und erreicht den Client als [ErrorCode.TDS_INTERNAL_ERROR] (`SPEC-048`).
     */
    fun failure(failure: QueryEvent.Failure, streamSynchronous: Boolean = true): ErrorResponse {
        val known = ErrorCode.entries.firstOrNull { it.name == failure.code }
        return if (known != null) {
            response(known, streamSynchronous, null)
        } else {
            response(ErrorCode.TDS_INTERNAL_ERROR, streamSynchronous, UNKNOWN_FAILURE_CODE)
        }
    }

    /** Eine Eingabe, die der Nachrichten-Codec abweist (`SPEC-011`, `SPEC-014`). */
    fun protocol(error: ProtocolError, streamSynchronous: Boolean = true): ErrorResponse {
        val code = when (error.category) {
            ProtocolErrorCategory.TDS_PROTOCOL_ERROR -> ErrorCode.TDS_PROTOCOL_ERROR
            ProtocolErrorCategory.TDS_UNSUPPORTED_TYPE -> ErrorCode.TDS_UNSUPPORTED_TYPE
        }
        return response(code, streamSynchronous, null)
    }

    /** Eine unerwartete Ausnahme: immer [ErrorCode.TDS_INTERNAL_ERROR], im Log nur ihr Typname. */
    fun unexpected(exception: Throwable, streamSynchronous: Boolean = true): ErrorResponse =
        response(ErrorCode.TDS_INTERNAL_ERROR, streamSynchronous, exception.javaClass.name)

    private fun response(code: ErrorCode, streamSynchronous: Boolean, cause: String?): ErrorResponse {
        log.record(ErrorLogEntry(code, cause))
        if (!streamSynchronous) {
            return ErrorResponse(code, null, ConnectionFate.CLOSE_WITHOUT_REPLY)
        }
        return ErrorResponse(code, serialize(code.rule), code.rule.fate)
    }

    private fun serialize(rule: ErrorRule): TdsMessage {
        val encoder = TokenEncoder()
        val message = ServerMessage(rule.number, rule.state, rule.severity, rule.text)
        val done = DoneToken(DoneKind.DONE, DoneStatus.ERROR, curCmd = 0, rowCount = 0)
        listOf(ErrorToken(message), done).forEach {
            check(encoder.write(it) is ProtocolResult.Ok) { "Fehlerantwort nicht schreibbar" }
        }
        return TdsMessage(PacketType.TABULAR_RESULT, encoder.toByteArray())
    }

    private companion object {
        const val UNKNOWN_FAILURE_CODE = "unknown-failure-code"
    }
}
