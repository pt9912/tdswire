package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.protocol.error.ConnectionFate
import io.github.pt9912.tdswire.protocol.error.ErrorMapper
import io.github.pt9912.tdswire.protocol.error.unknownHandles
import io.github.pt9912.tdswire.protocol.session.HandshakeEvent
import io.github.pt9912.tdswire.protocol.session.ReplyStream
import io.github.pt9912.tdswire.protocol.token.DoneKind
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage

/**
 * Die Batch-Form der Freigabe von Prepared-Handles (`SPEC-030`, `SPEC-001`): `EXEC sp_unprepare <n>`, weitere
 * Anweisungen derselben Form durch `;` getrennt, höchstens ein `;` am Ende. Das ist die Form, die mssql-jdbc 13.4.0
 * sendet (Messzeile t5 des Messprotokolls `docs/messungen/handle-grenze-optionen-2026-11-06.md`: `EXEC sp_unprepare
 * 1;EXEC sp_unprepare 2;…`; das Ende des Textes steht nicht im Mitschnitt).
 *
 * Festlegung ohne Messung: Erkannt wird nur ein Text, der ganz aus dieser Form besteht, mit genau diesem Wortlaut
 * (`EXEC` groß, `sp_unprepare` klein, ein Leerzeichen vor der Nummer, kein Leerraum um das `;`, keine Klammern, kein
 * Zeilenumbruch). Die Nummer besteht aus ASCII-Ziffern ohne führende Null (außer `0`) und liegt im Bereich von
 * `Int`; ein Minuszeichen, ein anderes Zeichen oder eine größere Zahl macht den ganzen Text zu keiner Freigabe. Ein
 * solcher Text geht wie jeder andere Batch an das Backend. Die Größe des Textes begrenzt die Nachricht
 * (`MAX_MESSAGE_BYTES`), nicht dieser Leser.
 */
internal object UnprepareBatch {
    private const val STATEMENT = "EXEC sp_unprepare "
    private const val SEPARATOR = ';'
    private const val MAX_DIGITS = 10
    private const val FAILED = -1

    /** Die feste Marke im Log für eine Nummer, die die Sitzung nicht hielt. */
    const val UNKNOWN_CAUSE = "unknown-prepared-handle"

    /** Die Nummern in der Reihenfolge des Textes; `null`, wenn [text] nicht ganz die Form der Freigabe hat. */
    fun parse(text: String): List<Int>? {
        val handles = ArrayList<Int>()
        var at = 0
        do {
            at = statement(text, at, handles)
        } while (at in 0 until text.length)
        return handles.takeIf { at == text.length }
    }

    /** Liest die Anweisung ab [at] in [into]; der Index hinter ihr samt `;`, oder [FAILED]. */
    private fun statement(text: String, at: Int, into: MutableList<Int>): Int {
        val first = at + STATEMENT.length
        var end = first
        if (text.startsWith(STATEMENT, at)) {
            while (end < text.length && text[end] in '0'..'9') end++
        }
        val handle = handle(text, first, end) ?: return FAILED
        val next = when {
            end == text.length -> end
            text[end] == SEPARATOR -> end + 1
            else -> FAILED
        }
        if (next != FAILED) into += handle
        return next
    }

    private fun handle(text: String, from: Int, to: Int): Int? {
        val digits = to - from
        val wellFormed = digits in 1..MAX_DIGITS && (digits == 1 || text[from] != '0')
        return if (wellFormed) text.substring(from, to).toLong().takeIf { it <= Int.MAX_VALUE }?.toInt() else null
    }

    /** Gibt die [numbers] der Reihe nach in [handles] frei; geliefert werden die nicht gehaltenen, der Reihe nach. */
    suspend fun release(numbers: List<Int>, handles: PreparedHandles): List<Int> =
        numbers.filterNot { handles.release(it) }

    /**
     * Die Antwort auf die Freigabe, deren nicht gehaltene Nummern [unknown] sind: ein DONE ohne Bits und ohne
     * Zeilenzahl, wenn die Sitzung jede Nummer hielt (Festlegung, die Tokenfolge eines echten Servers ist nicht
     * gemessen); sonst ein ERROR je Nummer ohne Obergrenze (`SPEC-060`, gemessen: M-22), Token für Token in den Strom
     * aus [open] geschrieben, nach dem Token [lead], wenn es eines gibt. Die Antwort steht nie als Liste oder Puffer
     * im Speicher. Scheitert das Schreiben, endet die Verbindung ohne weitere Antwort; sonst bleibt sie offen.
     */
    suspend fun answer(
        unknown: List<Int>,
        open: () -> ReplyStream,
        lead: Token?,
        errors: ErrorMapper,
    ): HandshakeEvent.RequestFinished {
        if (unknown.isEmpty()) return completed()
        val reply = open()
        val response = StreamedResponse(reply) { }
        val sink = if (lead != null) LeadingToken(response, lead) else response
        val written = errors.unknownHandles(unknown, UNKNOWN_CAUSE, sink)
        return if (written && reply.finish()) {
            HandshakeEvent.RequestFinished(null, connectionOpen = true, replyWritten = true)
        } else {
            HandshakeEvent.RequestFinished(null, connectionOpen = false)
        }
    }

    private fun completed(): HandshakeEvent.RequestFinished {
        val encoder = TokenEncoder()
        check(encoder.write(DoneToken(DoneKind.DONE, DoneStatus.FINAL, 0, 0)) is ProtocolResult.Ok) {
            "Antwort nicht schreibbar"
        }
        return requestFinished(ConnectionFate.OPEN, TdsMessage(PacketType.TABULAR_RESULT, encoder.toByteArray()))
    }
}
