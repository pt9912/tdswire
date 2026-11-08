package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.StatementKind
import io.github.pt9912.tdswire.port.ScriptedBackend
import io.github.pt9912.tdswire.protocol.session.Inbound
import io.github.pt9912.tdswire.testsupport.blocking
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.testsupport.toHex
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import kotlin.test.Test
import kotlin.test.assertEquals

private const val ZERO = "00 00 00 00 00 00 00 00"
private const val DESCRIPTOR = "01 00 00 00 35 00 00 00"
private const val IN_TRANSACTION = "16 00 00 00  12 00 00 00  02 00  $DESCRIPTOR  01 00 00 00 "

/**
 * Der Transaktionszustand liegt bei der Schleife einer Sitzung (`SPEC-031`, ADR-0042 Entscheidung 5): die Antworten
 * einer Folge von Anfragen tragen ENVCHANGE Typ 8 und 9 wie am echten SQL Server (Messprotokoll M-20, Szenario
 * `katalog-struktur`), die Anfragen nach dem Beginn tragen den Deskriptor im Kopf der Nachricht zurück (so sendet es
 * der Referenzclient, `1600000012000000 0200 0100000035000000 01000000`) und werden wie jede andere bedient. Die
 * Antwort kommt vom Fake-Backend (`ScriptedBackend`), das die Art meldet.
 */
class TransactionDispatchTest {
    private fun create() = listOf<QueryEvent>(QueryEvent.UpdateCount(0, StatementKind.CREATE_TABLE))

    private fun kinds(vararg kinds: StatementKind) = kinds.map { QueryEvent.UpdateCount(0, it) }

    /** Ein SQL_BATCH, dessen Kopf den Deskriptor der offenen Transaktion trägt. */
    private fun inTransaction(text: String): Inbound = BatchVectors.raw(
        PacketType.SQL_BATCH,
        hex(IN_TRANSACTION) + text.toByteArray(Charsets.UTF_16LE),
    )

    private fun resettingBatch(text: String): Inbound =
        Inbound.Message(TdsMessage(PacketType.SQL_BATCH, BatchVectors.payload(text), resetConnection = true))

    private fun hexOf(text: String) = hex(text).toHex()

    /**
     * Die gemessene Folge, Anfrage für Anfrage über die Schleife: `set implicit_transactions on`, `CREATE TABLE`
     * (ENVCHANGE Typ 8), `ALTER TABLE … ADD` und der bedingte `COMMIT` mit dem zurückgesendeten Deskriptor im Kopf
     * (normale Antwort, der `COMMIT` mit Typ 9 und dem Deskriptor als altem Wert), dann `set implicit_transactions off
     * IF …` und ein `CREATE TABLE` ohne ENVCHANGE. Wächter: eine Schleife, die den Zustand nicht über Anfragen hält
     * (jede Antwort ohne Typ 8), ein Kopf mit Deskriptor, den der Parser oder die Schleife ablehnt.
     *
     * @spec SPEC-031
     * @spec TDS-QA-07
     */
    @Test
    fun `Die Anfragen mit zurueckgesendetem Deskriptor werden bedient und die Folge traegt Typ 8 und 9`() = blocking {
        val backend = ScriptedBackend.inSequence(
            kinds(StatementKind.IMPLICIT_TRANSACTIONS_ON),
            create(),
            kinds(StatementKind.ALTER_TABLE),
            kinds(StatementKind.COMMIT_IF_OPEN),
            kinds(StatementKind.IMPLICIT_TRANSACTIONS_OFF, StatementKind.COMMIT_IF_OPEN),
            create(),
        )
        val session = Session.login(
            BatchVectors.message("set implicit_transactions on "),
            BatchVectors.message("CREATE TABLE t (a INT)"),
            inTransaction("ALTER TABLE t ADD b INT"),
            inTransaction("IF @@TRANCOUNT > 0 COMMIT TRAN"),
            BatchVectors.message("set implicit_transactions off IF @@TRANCOUNT > 0 COMMIT TRAN "),
            BatchVectors.message("CREATE TABLE u (a INT)"),
            Inbound.EndOfStream,
        )
        session.serve(backend)
        assertEquals(6, session.replies.size)
        val expected = listOf(
            "FD 00 00 B9 00 $ZERO",
            "E3 0B 00 08 08 $DESCRIPTOR 00  FD 00 00 C6 00 $ZERO",
            "FD 00 00 D8 00 $ZERO",
            "FD 01 00 C0 00 $ZERO  E3 0B 00 09 00 08 $DESCRIPTOR  FD 00 00 D5 00 $ZERO",
            "FD 01 00 BA 00 $ZERO  FD 00 00 C0 00 $ZERO",
            "FD 00 00 C6 00 $ZERO",
        )
        expected.forEachIndexed { index, text -> assertEquals(hexOf(text), session.hex(index), "Antwort $index") }
        assertEquals(6, backend.requests.size)
    }

    /**
     * Das Bit RESETCONNECTION setzt den Zustand zurück (Setzung, nicht gemessen): nach `set implicit_transactions on`
     * und einem `CREATE TABLE` mit Typ 8 beginnt das `CREATE TABLE` einer Anfrage mit dem Bit keine Transaktion; die
     * Antwort trägt zuerst ENVCHANGE Typ 18. Wächter: ein Zurücksetzen, das den Zustand der Transaktion lässt (die
     * zweite Antwort trüge Typ 8 oder der folgende `COMMIT` Typ 9).
     *
     * @spec SPEC-031
     * @spec SPEC-001
     */
    @Test
    fun `RESETCONNECTION setzt den Zustand der Transaktion zurueck`() = blocking {
        val backend = ScriptedBackend.inSequence(
            kinds(StatementKind.IMPLICIT_TRANSACTIONS_ON),
            create(),
            create(),
            kinds(StatementKind.COMMIT_IF_OPEN),
        )
        val session = Session.login(
            BatchVectors.message("set implicit_transactions on "),
            BatchVectors.message("CREATE TABLE t (a INT)"),
            resettingBatch("CREATE TABLE u (a INT)"),
            BatchVectors.message("IF @@TRANCOUNT > 0 COMMIT TRAN"),
            Inbound.EndOfStream,
        )
        session.serve(backend)
        assertEquals(hexOf("E3 0B 00 08 08 $DESCRIPTOR 00  FD 00 00 C6 00 $ZERO"), session.hex(1))
        assertEquals(hexOf("${BatchVectors.ENVCHANGE_RESET}  FD 00 00 C6 00 $ZERO"), session.hex(2))
        assertEquals(hexOf("FD 00 00 C0 00 $ZERO"), session.hex(3))
    }

    /**
     * Der Zustand liegt je Verbindung (`SPEC-031`, ADR-0042 Entscheidung 5): die erste Verbindung schaltet
     * `implicit_transactions` ein und beginnt mit `CREATE TABLE` eine Transaktion (Typ 8); die zweite, danach
     * geöffnete Verbindung hat weder die Option noch eine offene Transaktion, ihr `CREATE TABLE` trägt kein Typ 8 und
     * ihr bedingter `COMMIT` kein Typ 9. Wächter: ein Zustand, den alle Verbindungen teilen (statisch, ein Objekt
     * außerhalb der Schleife): die zweite Antwort trüge Typ 9 mit dem Deskriptor der ersten Verbindung.
     *
     * @spec SPEC-031
     * @spec TDS-QA-07
     */
    @Test
    fun `Eine zweite Verbindung sieht die offene Transaktion der ersten nicht`() = blocking {
        val first = Session.login(
            BatchVectors.message("set implicit_transactions on "),
            BatchVectors.message("CREATE TABLE t (a INT)"),
            Inbound.EndOfStream,
        )
        first.serve(ScriptedBackend.inSequence(kinds(StatementKind.IMPLICIT_TRANSACTIONS_ON), create()))
        assertEquals(hexOf("E3 0B 00 08 08 $DESCRIPTOR 00  FD 00 00 C6 00 $ZERO"), first.hex(1))

        val second = Session.login(
            BatchVectors.message("CREATE TABLE u (a INT)"),
            BatchVectors.message("IF @@TRANCOUNT > 0 COMMIT TRAN"),
            Inbound.EndOfStream,
        )
        second.serve(ScriptedBackend.inSequence(create(), kinds(StatementKind.COMMIT_IF_OPEN)))
        assertEquals(hexOf("FD 00 00 C6 00 $ZERO"), second.hex(0))
        assertEquals(hexOf("FD 00 00 C0 00 $ZERO"), second.hex(1))
    }
}
