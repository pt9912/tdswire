package io.github.pt9912.tdswire.protocol.error

import io.github.pt9912.tdswire.protocol.response.ResponseForm
import io.github.pt9912.tdswire.protocol.response.errorAnswerTail
import io.github.pt9912.tdswire.protocol.token.ErrorToken
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage

/** Die Bytes von [tokens] als Tokenstrom. */
internal fun encodeTokens(tokens: List<Token>): ByteArray {
    val encoder = TokenEncoder()
    tokens.forEach { check(encoder.write(it) is ProtocolResult.Ok) { "Fehlerantwort nicht schreibbar" } }
    return encoder.toByteArray()
}

/**
 * ERROR je Nummer aus [handles] (nicht leer, Regel mit [ErrorRule.namesHandle]) und der Abschluss der [form], in einem
 * Puffer genau der Größe der Antwort: der erste Durchlauf summiert die Größen der Token, der zweite schreibt sie. Der
 * Speicher der Antwort ist die Antwort selbst, keine Liste von Meldungen oder Token und kein wachsender Puffer.
 */
internal fun handleAnswer(rule: ErrorRule, handles: List<Int>, form: ResponseForm): TdsMessage {
    val tail = encodeTokens(errorAnswerTail(form, rule.message(handles.last())))
    val size = handles.fold(tail.size.toLong()) { sum, handle -> sum + errorBytes(rule, handle).size }
    check(size <= Int.MAX_VALUE) { "Fehlerantwort zu groß" }
    val payload = ByteArray(size.toInt())
    var at = 0
    for (handle in handles) {
        val token = errorBytes(rule, handle)
        token.copyInto(payload, at)
        at += token.size
    }
    tail.copyInto(payload, at)
    return TdsMessage(PacketType.TABULAR_RESULT, payload)
}

private fun errorBytes(rule: ErrorRule, handle: Int) = encodeTokens(listOf(ErrorToken(rule.message(handle))))
