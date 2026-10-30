package io.github.pt9912.tdswire.protocol.error

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.protocol.response.ErrorMapping
import io.github.pt9912.tdswire.protocol.response.MappedError
import io.github.pt9912.tdswire.protocol.response.SequenceOutcome
import io.github.pt9912.tdswire.protocol.response.failedDone
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

/** Die ERROR-Meldung der Regel: Nummer, State, Class und fester Text; die einzige Stelle, die sie bildet. */
fun ErrorRule.message(): ServerMessage = ServerMessage(number, state, severity, text)

/**
 * Das Fehler-Mapping (`TDS-FA-06.a`, `ADR-0007`): bildet einen Fehler auf ERROR und DONE mit
 * Fehlerstatus und die Verbindungsfolge ab. Rein bis auf den Log-Aufruf; kennt keinen Strom und
 * keine Sitzung. Der Sequenzierer erhält über [sequencerMapping] Funktionen nach [MappedError]; die
 * Regel ([ErrorRule]) ist die einzige Quelle von Meldung und Verbindungsfolge, die Folge eines
 * Ausgangs des Sequenzierers liefert [fateOf].
 *
 * Festlegung (nicht gemessen): Der Text jeder Antwort ist der feste Text der Regel; der Text einer
 * [QueryEvent.Failure] und der einer Ausnahme erreichen den Client nie. Das DONE ist `DONE` mit
 * Statusbit `ERROR`, Befehlskennung 0 und Zeilenzahl 0 (Bytes nur als Handvektor belegt, nicht am
 * Referenzclient gemessen). Ist der Strom nicht synchron, entfällt die Antwort (`SPEC-031`); der Sequenzierer
 * meldet denselben Fall als `CLOSE_WITHOUT_COMPLETION`, und [fateOf] bildet ihn auf `CLOSE_WITHOUT_REPLY` ab.
 */
class ErrorMapper(private val log: ErrorLog = ErrorLog.NONE) {
    /** Ein Fehler mit eigenem Code. */
    fun code(code: ErrorCode, streamSynchronous: Boolean = true): ErrorResponse =
        response(code, streamSynchronous, null)

    /**
     * Ein erwarteter Fehler des Backends. Nur ein Code aus [BACKEND_CODES] ist eine erwartete
     * Ablehnung des Backends; jeder andere Code (fremd oder ein Code, den nur der Server selbst
     * erzeugt, etwa `TDS_LOGIN_FAILED` oder `TDS_SHUTDOWN`) erreicht den Client als
     * [ErrorCode.TDS_INTERNAL_ERROR] (`SPEC-048`).
     */
    fun failure(failure: QueryEvent.Failure, streamSynchronous: Boolean = true): ErrorResponse {
        val classified = classify(failure)
        return response(classified.code, streamSynchronous, classified.cause)
    }

    /** Eine Eingabe, die der Nachrichten-Codec abweist (`SPEC-011`, `SPEC-014`). */
    fun protocol(error: ProtocolError, streamSynchronous: Boolean = true): ErrorResponse =
        response(protocolCode(error), streamSynchronous, null)

    /** Eine unerwartete Ausnahme: immer [ErrorCode.TDS_INTERNAL_ERROR], im Log nur ihr Typname. */
    fun unexpected(exception: Throwable, streamSynchronous: Boolean = true): ErrorResponse =
        response(ErrorCode.TDS_INTERNAL_ERROR, streamSynchronous, exception.javaClass.name)

    /**
     * Die Abbildung als Funktionen für den Sequenzierer, der ERROR und DONE selbst in die laufende
     * Nachricht schreibt. Meldung, Verbindungsfolge und Log stammen aus denselben Regeln wie bei
     * [failure] und [protocol]; [ErrorMapping.internal] loggt eine Ausnahme über [unexpected], einen
     * Reihenfolgeverstoß (`null`) mit der Marke `sequence-violation`.
     */
    fun sequencerMapping(): ErrorMapping = ErrorMapping(
        backend = { failure ->
            val classified = classify(failure)
            log.record(ErrorLogEntry(classified.code, classified.cause))
            mapped(classified.code)
        },
        typeMapping = { error ->
            val code = protocolCode(error)
            log.record(ErrorLogEntry(code, null))
            mapped(code)
        },
        internal = { cause ->
            if (cause != null) unexpected(cause) else response(ErrorCode.TDS_INTERNAL_ERROR, true, SEQUENCE_VIOLATION)
            mapped(ErrorCode.TDS_INTERNAL_ERROR)
        },
    )

    /** Die Verbindungsfolge zu einem Ausgang des Sequenzierers; der Ausgang trägt sie vollständig. */
    fun fateOf(outcome: SequenceOutcome): ConnectionFate = when (outcome) {
        SequenceOutcome.COMPLETED, SequenceOutcome.FAILED -> ConnectionFate.OPEN
        SequenceOutcome.FAILED_CLOSE_CONNECTION -> ConnectionFate.CLOSE_AFTER_REPLY
        SequenceOutcome.CLOSE_WITHOUT_COMPLETION -> ConnectionFate.CLOSE_WITHOUT_REPLY
    }

    private fun mapped(code: ErrorCode) = MappedError(code.rule.message(), code.rule.fate != ConnectionFate.OPEN)

    private class Classified(val code: ErrorCode, val cause: String?)

    private fun classify(failure: QueryEvent.Failure): Classified {
        val known = BACKEND_CODES.firstOrNull { it.name == failure.code }
        return if (known != null) {
            Classified(known, null)
        } else {
            Classified(ErrorCode.TDS_INTERNAL_ERROR, UNKNOWN_FAILURE_CODE)
        }
    }

    private fun protocolCode(error: ProtocolError): ErrorCode = when (error.category) {
        ProtocolErrorCategory.TDS_PROTOCOL_ERROR -> ErrorCode.TDS_PROTOCOL_ERROR
        ProtocolErrorCategory.TDS_UNSUPPORTED_TYPE -> ErrorCode.TDS_UNSUPPORTED_TYPE
    }

    private fun response(code: ErrorCode, streamSynchronous: Boolean, cause: String?): ErrorResponse {
        log.record(ErrorLogEntry(code, cause))
        if (!streamSynchronous) {
            return ErrorResponse(code, null, ConnectionFate.CLOSE_WITHOUT_REPLY)
        }
        return ErrorResponse(code, serialize(code.rule), code.rule.fate)
    }

    private fun serialize(rule: ErrorRule): TdsMessage {
        val encoder = TokenEncoder()
        listOf(ErrorToken(rule.message()), failedDone()).forEach {
            check(encoder.write(it) is ProtocolResult.Ok) { "Fehlerantwort nicht schreibbar" }
        }
        return TdsMessage(PacketType.TABULAR_RESULT, encoder.toByteArray())
    }

    companion object {
        /**
         * Positivliste (Festlegung, nicht gemessen): die Codes, die ein Backend als [QueryEvent.Failure]
         * melden darf. Die übrigen Codes erzeugt der Server selbst (Protokoll, Login, RPC-Dispatch,
         * interner Fehler, Beenden).
         */
        val BACKEND_CODES: List<ErrorCode> = listOf(
            ErrorCode.TDS_BACKEND_ERROR,
            ErrorCode.TDS_UNSUPPORTED_SQL,
            ErrorCode.TDS_UNSUPPORTED_TYPE,
            ErrorCode.TDS_SIM_UNKNOWN_OBJECT,
            ErrorCode.TDS_SIM_INVALID_VALUE,
            ErrorCode.TDS_TIMEOUT,
        )

        private const val UNKNOWN_FAILURE_CODE = "unknown-failure-code"
        private const val SEQUENCE_VIOLATION = "sequence-violation"
    }
}
