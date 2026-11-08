package io.github.pt9912.tdswire.protocol.error

import io.github.pt9912.tdswire.protocol.response.ResponseForm
import io.github.pt9912.tdswire.protocol.response.TokenSink
import io.github.pt9912.tdswire.protocol.response.errorAnswerTail
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult

/** Die Bytes von [tokens] als Tokenstrom. */
internal fun encodeTokens(tokens: List<Token>): ByteArray {
    val encoder = TokenEncoder()
    tokens.forEach { check(encoder.write(it) is ProtocolResult.Ok) { "Fehlerantwort nicht schreibbar" } }
    return encoder.toByteArray()
}

/**
 * Die Antwort auf eine Freigabe, die Nummern nicht hielt (`SPEC-060`): ein ERROR je Nummer aus [handles] (nicht leer,
 * in der Reihenfolge, ohne Obergrenze) und der Abschluss der Form BATCH, Token für Token in [sink]. Die Antwort
 * steht nie als Liste oder Puffer im Speicher; gehalten wird, was die Senke hält (ein Paket). Das Log trägt einen
 * Eintrag mit [cause] je Antwort, nicht je Nummer. `false`, wenn die Senke ein Token nicht annimmt; die Antwort ist
 * dann abgebrochen.
 */
internal suspend fun ErrorMapper.unknownHandles(handles: List<Int>, cause: String, sink: TokenSink): Boolean {
    val code = ErrorCode.TDS_SIM_UNKNOWN_PREPARED_HANDLE
    log.record(ErrorLogEntry(code, cause))
    for (handle in handles) {
        if (!sink.accept(ErrorToken(code.rule.message(handle)))) return false
    }
    return errorAnswerTail(ResponseForm.BATCH, code.rule.message(handles.last())).all { sink.accept(it) }
}
