package io.github.pt9912.tdswire.protocol.response

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.ResultColumn
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import io.github.pt9912.tdswire.protocol.token.DoneCommand
import io.github.pt9912.tdswire.protocol.token.DoneKind
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.protocol.token.Token
import kotlinx.coroutines.flow.Flow

/** Feste Stellvertreter-Meldungen; die Nummern sind keine Zuordnung des Katalogs (`TDS-FA-06.a`). */
internal val BACKEND_MESSAGE = ServerMessage(number = 50001, state = 1, severity = 16, text = "backend")
internal val TYPE_MESSAGE = ServerMessage(number = 50002, state = 1, severity = 16, text = "typ")
internal val INTERNAL_MESSAGE = ServerMessage(number = 50003, state = 1, severity = 20, text = "intern")

internal val FIXED_MAPPING = ErrorMapping(
    backend = { MappedError(BACKEND_MESSAGE, closesConnection = false) },
    typeMapping = { MappedError(TYPE_MESSAGE, closesConnection = false) },
    internal = { MappedError(INTERNAL_MESSAGE, closesConnection = true) },
)

/** Wie [FIXED_MAPPING], zeichnet aber die Ursachen auf, mit denen der Sequenzierer `internal` ruft. */
internal class CausesMapping {
    val causes = mutableListOf<Throwable?>()
    val mapping = ErrorMapping(
        backend = FIXED_MAPPING.backend,
        typeMapping = FIXED_MAPPING.typeMapping,
        internal = {
            causes += it
            FIXED_MAPPING.internal(it)
        },
    )
}

internal val REQUEST = QueryRequest("s", "r", "SELECT 1", emptyList(), null)

internal val INT_COLUMN = ResultColumn("n", SqlType.Int32, nullable = false)

internal fun start() = QueryEvent.ResultStart(listOf(INT_COLUMN))

internal fun row(value: Int) = QueryEvent.Row(listOf(value))

internal fun done(status: Int, rows: Long = 0, cmd: Int = DoneCommand.NONE) =
    DoneToken(DoneKind.DONE, status, cmd, rows)

/** DONE mit der Befehlskennung eines Ergebnisses (`0x00c1`). */
internal fun doneSelect(status: Int, rows: Long = 0) = done(status, rows, DoneCommand.SELECT)

/** DONE mit der Befehlskennung eines Batches ohne offenes Ergebnis (`0x00fd`). */
internal fun doneBatch(status: Int, rows: Long = 0) = done(status, rows, DoneCommand.BATCH)

/** Eine Senke, die Tokens aufzeichnet und das [rejectAt]-te Token (ab 1) nicht annimmt. */
internal class RecordingSink(private val rejectAt: Int? = null) : TokenSink {
    val tokens = mutableListOf<Token>()
    private var calls = 0

    override suspend fun accept(token: Token): Boolean {
        calls++
        if (calls == rejectAt) return false
        tokens += token
        return true
    }
}

/**
 * Ein Backend mit frei gewähltem Strom und Zählern für `close()`; `execute` kann eine Ausnahme werfen,
 * `close()` kann suspendieren ([onClose]). [closesCompleted] zählt nur ein `close()`, das zu Ende lief.
 */
internal class FlowBackend(
    private val onExecute: () -> Unit = {},
    private val onClose: suspend () -> Unit = {},
    private val source: () -> Flow<QueryEvent>,
) : QueryBackend {
    var closeCalls = 0
        private set

    var closesCompleted = 0
        private set

    override suspend fun execute(request: QueryRequest): QueryExecution {
        onExecute()
        return object : QueryExecution {
            override val events: Flow<QueryEvent> = source()

            override suspend fun close() {
                closeCalls++
                onClose()
                closesCompleted++
            }
        }
    }

    override suspend fun cancel(sessionId: String, requestId: String) = Unit
}

/** Status eines DONE-Tokens mit Zeilenzahl und Fortsetzung. */
internal const val COUNT_MORE = DoneStatus.COUNT or DoneStatus.MORE
