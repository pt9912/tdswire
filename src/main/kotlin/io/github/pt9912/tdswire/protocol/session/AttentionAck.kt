package io.github.pt9912.tdswire.protocol.session

import io.github.pt9912.tdswire.protocol.token.DoneCommand
import io.github.pt9912.tdswire.protocol.token.DoneKind
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage

/**
 * Die Bestätigung eines ATTENTION (`SPEC-029`, `ADR-0027` Entscheidung 1, `ADR-0054`): ein DONE mit dem Statusbit
 * `ATTENTION`, Befehlskennung `0x00fd` und Zeilenzahl 0, ohne ERROR-Token. Die Bytes sind der Handvektor der zweiten
 * Nachricht der Aufnahmen `cancel-streaming` und `cancel-wartend` beider Referenzinstanzen (2022 und 2025). Als
 * einzelne Nachricht bestätigt sie ein ATTENTION nach Ende der Antwort, im Leerlauf und im geordneten Beenden; auf ein
 * ATTENTION in laufender Anfrage folgt sie als zweite Nachricht hinter dem `DONE[ERROR]` der abgebrochenen Antwort.
 */
internal object AttentionAck {
    private val done = DoneToken(DoneKind.DONE, DoneStatus.ATTENTION, DoneCommand.BATCH, rowCount = 0)

    /** Das Token als Bytes, um an eine laufende Antwort angehängt zu werden. */
    val bytes: ByteArray by lazy {
        val encoder = TokenEncoder()
        check(encoder.write(done) is ProtocolResult.Ok) { "Bestätigung nicht schreibbar" }
        encoder.toByteArray()
    }

    /**
     * Die Bestätigung eines ATTENTION, das ein angekündigtes Massenladen abbricht (nach `INSERT BULK`, vor der
     * Nachricht 0x07): ein DONE mit `ERROR` und `ATTENTION`, Befehl `0x00fd`, ohne ERROR-Token (Messprotokoll M-11,
     * Instanz 2022 und 2025, `SqlBulkCopy`).
     */
    val bulkAborted: TdsMessage by lazy {
        val encoder = TokenEncoder()
        val token = DoneToken(DoneKind.DONE, DoneStatus.ERROR or DoneStatus.ATTENTION, DoneCommand.BATCH, rowCount = 0)
        check(encoder.write(token) is ProtocolResult.Ok) { "Bestätigung nicht schreibbar" }
        TdsMessage(PacketType.TABULAR_RESULT, encoder.toByteArray())
    }

    /** Die Bestätigung als eigene Antwort-Nachricht (ATTENTION ohne laufende Anfrage). */
    val message: TdsMessage by lazy { TdsMessage(PacketType.TABULAR_RESULT, bytes) }
}
