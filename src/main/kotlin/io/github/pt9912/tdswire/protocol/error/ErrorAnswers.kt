package io.github.pt9912.tdswire.protocol.error

import io.github.pt9912.tdswire.protocol.response.ResponseForm
import io.github.pt9912.tdswire.protocol.response.TokenSink
import io.github.pt9912.tdswire.protocol.response.errorAnswer
import io.github.pt9912.tdswire.protocol.token.DoneCommand
import io.github.pt9912.tdswire.protocol.token.DoneKind
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.ReturnStatusToken
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import java.util.BitSet

/** Die Bytes von [tokens] als Tokenstrom. */
internal fun encodeTokens(tokens: List<Token>): ByteArray {
    val encoder = TokenEncoder()
    tokens.forEach { check(encoder.write(it) is ProtocolResult.Ok) { "Fehlerantwort nicht schreibbar" } }
    return encoder.toByteArray()
}

/**
 * `sp_execute` mit einem Handle, das die Sitzung nicht hält (`SPEC-057`): ERROR 8179 mit State
 * [STATE_EXECUTE_UNKNOWN_HANDLE] und der Nummer des Handles im Text, in der Form RPC; [cause] ist die feste Marke für
 * das Log. Die Verbindung bleibt offen.
 */
internal fun ErrorMapper.executeUnknownHandle(handle: Int, cause: String): ErrorResponse {
    val code = ErrorCode.TDS_SIM_UNKNOWN_PREPARED_HANDLE
    log.record(ErrorLogEntry(code, cause))
    val message = code.rule.message(handle, STATE_EXECUTE_UNKNOWN_HANDLE)
    val answer = TdsMessage(PacketType.TABULAR_RESULT, encodeTokens(errorAnswer(ResponseForm.RPC, listOf(message))))
    return ErrorResponse(code, answer, code.rule.fate)
}

/**
 * Die Antwort auf die Freigabe von [numbers] (`SPEC-030`, `SPEC-060`), Token für Token in [sink]: je Nummer ein
 * RETURNSTATUS und ein DONEPROC mit der Befehlskennung `0x00e0`; die Nummer der Stelle in [unknown] (nicht gehalten)
 * beginnt mit einem ERROR und trägt den RETURNSTATUS der Meldung (8179) und `ERROR` am DONEPROC, jede andere den
 * RETURNSTATUS 0. Alle DONEPROC außer dem letzten tragen `MORE`; ein DONE gibt es nicht. Die Antwort steht nie als
 * Liste oder Puffer im Speicher; gehalten wird, was die Senke hält (ein Paket). Das Log trägt einen Eintrag mit
 * [cause] je Antwort mit nicht gehaltenen Nummern, nicht je Nummer. `false`, wenn die Senke ein Token nicht annimmt;
 * die Antwort ist dann abgebrochen.
 */
internal suspend fun ErrorMapper.releaseAnswer(
    numbers: List<Int>,
    unknown: BitSet,
    cause: String,
    sink: TokenSink,
): Boolean {
    val code = ErrorCode.TDS_SIM_UNKNOWN_PREPARED_HANDLE
    if (!unknown.isEmpty) log.record(ErrorLogEntry(code, cause))
    for ((at, number) in numbers.withIndex()) {
        val more = if (at == numbers.lastIndex) DoneStatus.FINAL else DoneStatus.MORE
        val tokens = if (unknown[at]) {
            val message = code.rule.message(number)
            listOf(ErrorToken(message), ReturnStatusToken(message.number), procDone(more or DoneStatus.ERROR))
        } else {
            listOf(ReturnStatusToken(0), procDone(more))
        }
        for (token in tokens) {
            if (!sink.accept(token)) return false
        }
    }
    return true
}

private fun procDone(status: Int) = DoneToken(DoneKind.DONE_PROC, status, DoneCommand.PROC, 0)
