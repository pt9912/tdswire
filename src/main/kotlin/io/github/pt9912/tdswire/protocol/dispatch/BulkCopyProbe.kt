package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.telemetry.SpanOutcome
import io.github.pt9912.tdswire.port.BulkProbeColumn
import io.github.pt9912.tdswire.protocol.error.ConnectionFate
import io.github.pt9912.tdswire.protocol.error.ErrorMapper
import io.github.pt9912.tdswire.protocol.result.ColumnMapping
import io.github.pt9912.tdswire.protocol.session.HandshakeEvent
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.ColumnDef
import io.github.pt9912.tdswire.protocol.token.DoneCommand
import io.github.pt9912.tdswire.protocol.token.DoneKind
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.ReturnStatusToken
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.TdsType
import io.github.pt9912.tdswire.protocol.token.TdsValue
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.protocol.wire.ByteWriter
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage

/**
 * Die Antwort auf die Probe der Zieltabelle von `SqlBulkCopy` (`SPEC-036`, Form B): vier Ergebnisse in der Folge,
 * in der der echte Server sie liefert (Messreihe M-8 bis M-12, Instanz 2022 und 2025, Tabellen `bulk_typen` und
 * `bulk_schluessel`): (1) `@@TRANCOUNT` als `INT`, Wert 0; (2) die Spalten der Tabelle ohne Zeile (`SET FMTONLY ON`),
 * Flags `0x0008`, nullbar `0x0009`; (3) je Spalte `colid`, `name`, `tds_collation` (`BINARY(5)`) und `collation`
 * ([BulkCopyProbeSets]); (4) die leere Tabelle `Canonical_Column_Name`, `Aliased_Column_Name`. Dazwischen stehen die
 * DONE, DONEINPROC, RETURNSTATUS und DONEPROC der Anweisungen des Batches mit den Statusbits und Befehlen der
 * Aufzeichnung. Die Zeilenzahl von DONEINPROC nach der Abfrage der Spaltennamen und nach den Kollationen ist die Zahl
 * der Spalten.
 *
 * Eine Dezimalspalte meldet in (2) 17 Byte für jede Precision (Aufzeichnung: `DECIMALN(10,2)` mit Größe `0x11`); der
 * Referenzclient schreibt den Wert der Nachricht `0x07` immer mit 17 Byte; bei der Größe 9 sendete er im Lauf am
 * Testserver ein Längenbyte 9 vor diesen 17 Byte, mit 17 (Aufzeichnung) sind beide 17. Text hat in (2) die Länge der
 * Beschreibung (`maxChars`).
 *
 * Abweichung von der Aufzeichnung: dem COLMETADATA folgen keine zwei Nullbyte hinter der Spaltenzahl (die Aufzeichnung
 * zeigt sie in jedem COLMETADATA des echten Servers an diesen Client; der Server dieses Repos handelt das Merkmal nicht
 * aus, und sein COLMETADATA trägt sie nirgends).
 */
internal object BulkCopyProbe {
    private const val FLAGS_COMPUTED = 0x0020
    private const val FLAGS_UPDATABLE_UNKNOWN = 0x0008
    private const val DECIMAL_BYTES = 17
    private const val CMD_SET_FMTONLY_ON = 0x00B9
    private const val CMD_SET_FMTONLY_OFF = 0x00BA
    private const val CMD_DROP = 0x00C7
    private const val CMD_STATEMENT_A = 0x00C6
    private const val CMD_STATEMENT_B = 0x00C0
    private const val MORE_COUNT = DoneStatus.MORE or DoneStatus.COUNT

    /** Ein DONE der Folge zwischen dem ersten Ergebnis und dem COLMETADATA der Tabelle: Status, Befehl, Zeilenzahl. */
    private class Step(val status: Int, val command: Int, val count: Long)

    private val statements = listOf(
        Step(MORE_COUNT, DoneCommand.SELECT, 1),
        Step(MORE_COUNT, DoneCommand.SELECT, 1),
        Step(MORE_COUNT, DoneCommand.SELECT, 1),
        Step(DoneStatus.MORE, CMD_STATEMENT_A, 0),
        Step(DoneStatus.MORE, CMD_STATEMENT_B, 0),
        Step(MORE_COUNT, DoneCommand.SELECT, 1),
        Step(MORE_COUNT, DoneCommand.SELECT, 1),
        Step(DoneStatus.MORE, CMD_STATEMENT_B, 0),
        Step(DoneStatus.MORE, CMD_STATEMENT_B, 0),
        Step(MORE_COUNT, DoneCommand.SELECT, 1),
        Step(MORE_COUNT, DoneCommand.SELECT, 1),
    )

    /** Das Ende der Anfrage mit der Antwort (oder dem Fehler der Abbildung) und das Ergebnis des Spans. */
    fun finished(
        columns: List<BulkProbeColumn>,
        errors: ErrorMapper,
    ): Pair<SpanOutcome, HandshakeEvent.RequestFinished> = when (val answer = answer(columns)) {
        is ProtocolResult.Ok ->
            SpanOutcome.OK to requestFinished(ConnectionFate.OPEN, TdsMessage(PacketType.TABULAR_RESULT, answer.value))

        is ProtocolResult.Failed ->
            SpanOutcome.PROTOCOL_ERROR to errors.protocol(answer.error).let { requestFinished(it.fate, it.message) }
    }

    /** Die Bytes der Antwort (TABULAR_RESULT) für die Spalten der Tabelle; Fehler, wenn eine nicht abbildbar ist. */
    fun answer(columns: List<BulkProbeColumn>): ProtocolResult<ByteArray> {
        val defs = when (val mapped = ColumnMapping.map(columns.map { it.column })) {
            is ProtocolResult.Failed -> return mapped
            is ProtocolResult.Ok -> mapped.value.mapIndexed { index, def -> tableColumn(def, columns[index]) }
        }
        val out = ByteWriter()
        val encoder = TokenEncoder()
        val count = columns.size.toLong()
        write(encoder, ColMetadataToken(listOf(ColumnDef("", TdsType.Int4Type, false, extraFlags = FLAGS_COMPUTED))))
        write(encoder, RowToken(listOf(TdsValue.IntegerValue(0))))
        done(encoder, DoneKind.DONE, MORE_COUNT, DoneCommand.SELECT, 1)
        statements.forEach { done(encoder, DoneKind.DONE, it.status, it.command, it.count) }
        procedure(encoder, count)
        done(encoder, DoneKind.DONE, MORE_COUNT, DoneCommand.SELECT, 1)
        done(encoder, DoneKind.DONE, DoneStatus.MORE, CMD_SET_FMTONLY_ON, 0)
        write(encoder, ColMetadataToken(defs))
        procedure(encoder, 0)
        done(encoder, DoneKind.DONE, DoneStatus.MORE, CMD_SET_FMTONLY_OFF, 0)
        out.bytes(encoder.drain())
        BulkCopyProbeSets.collations(out, columns)
        procedure(encoder, count)
        out.bytes(encoder.drain())
        BulkCopyProbeSets.aliases(out)
        done(encoder, DoneKind.DONE, MORE_COUNT, DoneCommand.SELECT, 0)
        done(encoder, DoneKind.DONE, DoneStatus.FINAL, CMD_DROP, 0)
        out.bytes(encoder.drain())
        return ProtocolResult.Ok(out.toByteArray())
    }

    /** Das Ende eines `EXEC` mit Ergebnis: DONEINPROC mit [rows], RETURNSTATUS 0 und DONEPROC. */
    private fun procedure(encoder: TokenEncoder, rows: Long) {
        done(encoder, DoneKind.DONE_IN_PROC, MORE_COUNT, DoneCommand.SELECT, rows)
        write(encoder, ReturnStatusToken(0))
        done(encoder, DoneKind.DONE_PROC, DoneStatus.MORE, DoneCommand.PROC, 0)
    }

    private fun done(encoder: TokenEncoder, kind: DoneKind, status: Int, command: Int, rows: Long) =
        write(encoder, DoneToken(kind, status, command, rows))

    private fun write(encoder: TokenEncoder, token: Token) {
        check(encoder.write(token) is ProtocolResult.Ok) { "Antwort nicht schreibbar" }
    }

    private fun tableColumn(def: ColumnDef, source: BulkProbeColumn): ColumnDef {
        val type = def.type
        val sized = when (type) {
            is TdsType.NVarcharType -> type.copy(maxChars = source.maxChars)
            is TdsType.DecimalType -> type.copy(bytes = DECIMAL_BYTES)
            else -> type
        }
        return def.copy(type = sized, extraFlags = FLAGS_UPDATABLE_UNKNOWN)
    }
}
