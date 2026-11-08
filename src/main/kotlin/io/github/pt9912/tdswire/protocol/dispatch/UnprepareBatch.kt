package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.protocol.error.ErrorMapper
import io.github.pt9912.tdswire.protocol.error.releaseAnswer
import io.github.pt9912.tdswire.protocol.response.SequenceOutcome
import io.github.pt9912.tdswire.protocol.response.TokenSink
import java.util.BitSet

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

    /** Gibt die [numbers] der Reihe nach in [handles] frei; geliefert werden die Stellen der nicht gehaltenen. */
    suspend fun release(numbers: List<Int>, handles: PreparedHandles): BitSet {
        val unknown = BitSet(numbers.size)
        numbers.forEachIndexed { at, number -> if (!handles.release(number)) unknown.set(at) }
        return unknown
    }

    /**
     * Schreibt die Antwort auf die Freigabe von [numbers], deren nicht gehaltene Stellen [unknown] nennt: je Nummer
     * RETURNSTATUS und DONEPROC mit der Befehlskennung `0x00e0`, bei einer nicht gehaltenen Nummer davor ein ERROR,
     * ohne Obergrenze und ohne DONE (`SPEC-030`, `SPEC-060`, gemessen: M-22, M-23), Token für Token in die [sink]. Die
     * Antwort steht nie als Liste oder Puffer im Speicher. Der Ausgang ist [SequenceOutcome.COMPLETED], wenn die
     * Sitzung jede Nummer hielt, [SequenceOutcome.FAILED], wenn sie eine nicht hielt, und
     * [SequenceOutcome.CLOSE_WITHOUT_COMPLETION], wenn die [sink] ein Token nicht annimmt; die Verbindung endet dann
     * ohne weitere Antwort.
     */
    suspend fun answer(numbers: List<Int>, unknown: BitSet, sink: TokenSink, errors: ErrorMapper): SequenceOutcome =
        when {
            !errors.releaseAnswer(numbers, unknown, UNKNOWN_CAUSE, sink) -> SequenceOutcome.CLOSE_WITHOUT_COMPLETION
            unknown.isEmpty -> SequenceOutcome.COMPLETED
            else -> SequenceOutcome.FAILED
        }
}
