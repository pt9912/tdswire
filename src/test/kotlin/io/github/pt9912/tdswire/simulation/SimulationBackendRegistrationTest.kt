package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.testsupport.blocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Die Registrierung der Ausführungen unter Sitzung und Anfrage (die `cancel` findet) endet bei jeder Form, die sich
 * einträgt: `SelectExecution` (S1, S1 mit Parametern, S2), `ChangeExecution` (`INSERT`, `UPDATE`, `DELETE` als Text und
 * mit Parametern, Strukturänderung) und `MergeExecution`. Beobachtet wird die Zahl der Registrierungen des Backends
 * (`SimulationBackend.registrations`, interne Test-Naht), nicht ein Folgeverhalten von `cancel`; jeder Lauf nutzt eine
 * Anfrage je Sitzung, die Naht unterscheidet Anfragen nicht. Die Quelle und der Callback warten auf ein Tor des Tests,
 * kein Test wartet auf die Wanduhr. `WAITFOR` trägt `SimulationBackendWaitTest`.
 */
class SimulationBackendRegistrationTest {
    /** Wo die Attrappe scheitert oder hält: in der Zeilenquelle oder im Callback (auch dem der Strukturänderung). */
    private enum class Where { SOURCE, CALLBACK }

    private enum class Mode { PASS, FAIL, HOLD }

    /**
     * Eine Tabelle `person` mit einer Zeilenquelle (eine Zeile) und Callbacks, die je nach [Mode] gelingen, werfen oder
     * auf [gate] warten; [entered] meldet, dass die Ausführung am Halt steht.
     */
    private class Rig {
        private var source = Mode.PASS
        private var callback = Mode.PASS
        var gate = CompletableDeferred<Unit>()
            private set
        var entered = CompletableDeferred<Unit>()
            private set

        fun pass() {
            source = Mode.PASS
            callback = Mode.PASS
        }

        fun fail(where: Where) {
            pass()
            set(where, Mode.FAIL)
        }

        fun hold(where: Where) {
            pass()
            gate = CompletableDeferred()
            entered = CompletableDeferred()
            set(where, Mode.HOLD)
        }

        fun release() {
            gate.complete(Unit)
        }

        private fun set(where: Where, mode: Mode) {
            if (where == Where.SOURCE) source = mode else callback = mode
        }

        private suspend fun step(mode: Mode) {
            when (mode) {
                Mode.PASS -> Unit

                Mode.FAIL -> error("Geheimnis der Attrappe")

                Mode.HOLD -> {
                    entered.complete(Unit)
                    gate.await()
                }
            }
        }

        private suspend fun changed(): Long {
            step(callback)
            return 1
        }

        private val table = SimTable(
            name = "person",
            columns = personColumns,
            primaryKey = listOf("Id"),
            rows = {
                var delivered = false
                RowSource {
                    step(source)
                    if (delivered) null else personRow(1, "Ada", 36).also { delivered = true }
                }
            },
            onInsert = { changed() },
            onUpdate = { _, _ -> changed() },
            onDelete = { changed() },
        )

        val backend = SimulationBackend({ SimCatalog(listOf(table)) }) { step(callback) }
    }

    /**
     * Eine Anfrage: [sql] mit [parameters]; [holds] ist die Stelle, an der der Lauf des Tests die Ausführung hält
     * (Cancel, Abbruch), [fails] sind die Stellen, an denen die Ausführung mit einem Fehler endet.
     */
    private class Form(
        val label: String,
        val sql: String,
        val parameters: List<QueryParameter> = emptyList(),
        val holds: Where,
        val fails: List<Where>,
    )

    private fun select(label: String, sql: String, parameters: List<QueryParameter> = emptyList()) =
        Form(label, sql, parameters, Where.SOURCE, listOf(Where.SOURCE))

    private fun change(label: String, sql: String, parameters: List<QueryParameter> = emptyList()) =
        Form(label, sql, parameters, Where.CALLBACK, listOf(Where.CALLBACK))

    private val id = listOf(QueryParameter("@p", SqlType.Int32, 1))
    private val idAndName = id + QueryParameter("@n", SqlType.UnicodeText, "Bo")

    private val merge = "MERGE INTO [dbo].[person] AS tgt USING (VALUES ( @P0 ,  @P1 ,  @P2 )) AS src ([Id], [Name], " +
        "[Age]) ON tgt.[Id] = src.[Id] WHEN MATCHED THEN UPDATE SET tgt.[Name] = src.[Name], tgt.[Age] = src.[Age] " +
        "WHEN NOT MATCHED THEN INSERT ([Id], [Name], [Age]) VALUES (src.[Id], src.[Name], src.[Age]) OUTPUT \$action;"

    private fun mergeParameters(key: Int) = listOf(
        QueryParameter("@P0", SqlType.Int32, key),
        QueryParameter("@P1", SqlType.UnicodeText, "Bo"),
        QueryParameter("@P2", SqlType.Int32, 5),
    )

    /**
     * Die Zahl der Registrierungen sinkt nach Ende, Fehler, `cancel`, Abbruch des Sammlers und `close()` ohne Sammeln
     * auf den Stand vor der Anfrage. Jeder Weg läuft ohne `close()` nach dem Sammeln: der Strom trägt sich selbst aus,
     * und `close()` trägt aus, wenn nie gesammelt wurde; beide Wege sind getrennt belegt.
     */
    private suspend fun CoroutineScope.endsRegistration(form: Form) {
        val rig = Rig()
        val backend = rig.backend
        val label = form.label
        fun request() = queryRequest(form.sql, form.parameters)
        assertEquals(0, backend.registrations, "$label: vor der Anfrage")

        val ended = backend.execute(request())
        assertEquals(1, backend.registrations, "$label: ausgeführt, noch nicht gesammelt")
        val events = ended.events.toList()
        assertTrue(events.none { it is QueryEvent.Failure }, "$label: der Lauf endet ohne Fehler: $events")
        assertEquals(0, backend.registrations, "$label: nach dem Ende")

        for (where in form.fails) {
            rig.fail(where)
            val failed = backend.execute(request()).events.toList()
            assertTrue(failed.any { it is QueryEvent.Failure }, "$label: Fehler erwartet ($where): $failed")
            assertEquals(0, backend.registrations, "$label: nach dem Fehler ($where)")
        }

        rig.hold(form.holds)
        val cancelled = launch { backend.execute(request()).events.toList() }
        rig.entered.await()
        assertEquals(1, backend.registrations, "$label: die gehaltene Anfrage ist registriert (Cancel)")
        backend.cancel("s1", "r1")
        rig.release()
        cancelled.join()
        assertEquals(0, backend.registrations, "$label: nach dem Cancel")

        rig.hold(form.holds)
        val aborted = launch { backend.execute(request()).events.toList() }
        rig.entered.await()
        assertEquals(1, backend.registrations, "$label: die gehaltene Anfrage ist registriert (Abbruch)")
        aborted.cancel()
        aborted.join()
        assertEquals(0, backend.registrations, "$label: nach dem Abbruch des Sammlers")

        rig.pass()
        val unused = backend.execute(request())
        assertEquals(1, backend.registrations, "$label: ausgeführt, nicht gesammelt (close)")
        unused.close()
        assertEquals(0, backend.registrations, "$label: nach close() ohne Sammeln")
    }

    /**
     * `SelectExecution` der Form S1 trägt sich aus: Auswahl ohne Parameter, die Zeilenquelle scheitert oder hält.
     * Wächter: eine Auswahl, die sich nicht austrägt (dann bliebe je Anfrage ein Eintrag im Backend), geprüft je Weg:
     * `onCompletion` des Stroms (Ende, Fehler, Cancel, Abbruch) und `close()`.
     * @spec SPEC-029
     */
    @Test
    fun `die Registrierung der Auswahl S1 endet mit Ende Fehler Cancel Abbruch und close`() = blocking {
        endsRegistration(select("S1", "SELECT id, name FROM person"))
    }

    /**
     * Dieselbe Austragung für eine Auswahl mit Parameter (RPC `sp_executesql`): die Verdrahtung ist die der Auswahl
     * ohne Parameter, der Parameter ändert den Weg der Austragung nicht.
     * @spec SPEC-029
     */
    @Test
    fun `die Registrierung der Auswahl S1 mit Parameter endet mit Ende Fehler Cancel Abbruch und close`() = blocking {
        endsRegistration(select("S1 RPC", "SELECT id FROM person WHERE id = @p", id))
    }

    /**
     * `SelectExecution` der Form S2 (zwei Auswahlen) trägt sich über eine eigene Verdrahtung aus: Ende nach beiden
     * Ergebnissen, Fehler der Zeilenquelle, Cancel, Abbruch und `close()`.
     * Wächter: die Austragung der Folge, die in `SimulationBackend.execute` getrennt von der Auswahl S1 verdrahtet ist.
     * @spec SPEC-029
     */
    @Test
    fun `die Registrierung der Auswahlen S2 endet mit Ende Fehler Cancel Abbruch und close`() = blocking {
        endsRegistration(select("S2", "SELECT id FROM person; SELECT name FROM person"))
    }

    /**
     * `ChangeExecution` für `INSERT`, `UPDATE` und `DELETE`, als Text und mit Parametern: der Callback gelingt, wirft
     * oder hält.
     * Wächter: eine Änderung, die sich nicht austrägt, geprüft je Weg wie bei der Auswahl.
     * @spec SPEC-029
     */
    @Test
    fun `die Registrierung der Aenderung endet mit Ende Fehler Cancel Abbruch und close`() = blocking {
        endsRegistration(change("INSERT", "INSERT INTO person (id, name) VALUES (1, 'Ada')"))
        endsRegistration(change("INSERT RPC", "INSERT INTO person (id, name) VALUES (@p, @n)", idAndName))
        endsRegistration(change("UPDATE", "UPDATE person SET name = 'Bo' WHERE id = 1"))
        endsRegistration(change("UPDATE RPC", "UPDATE person SET name = @n WHERE id = @p", idAndName))
        endsRegistration(change("DELETE", "DELETE FROM person WHERE id = 1"))
        endsRegistration(change("DELETE RPC", "DELETE FROM person WHERE id = @p", id))
    }

    /**
     * Die Strukturänderung ist eine `ChangeExecution` mit eigener Verdrahtung in `SimulationBackend.structure`: der
     * Callback `onStructure` gelingt, wirft oder hält, die Registrierung endet wie bei den anderen Änderungen.
     * @spec SPEC-029
     */
    @Test
    fun `die Registrierung der Strukturaenderung endet mit Ende Fehler Cancel Abbruch und close`() = blocking {
        endsRegistration(change("CREATE TABLE", dMigrateStructureTexts.single { it.startsWith("CREATE TABLE") }))
    }

    /**
     * `MergeExecution`: Schlüssel nicht vorhanden (`INSERT`), die Zeilenquelle scheitert oder hält, der Callback
     * scheitert.
     * Wächter: ein `MERGE`, das sich nicht austrägt, geprüft je Weg wie bei der Auswahl.
     * @spec SPEC-029
     */
    @Test
    fun `die Registrierung des MERGE endet mit Ende Fehler Cancel Abbruch und close`() = blocking {
        endsRegistration(
            Form("MERGE", merge, mergeParameters(key = 2), Where.SOURCE, listOf(Where.SOURCE, Where.CALLBACK)),
        )
    }

    /**
     * Texte, die keine Zeilenquelle und keinen Callback rufen (Form C: `SELECT 1`, `SET`), tragen sich nicht ein: die
     * Zahl der Registrierungen bleibt 0, es gibt nichts auszutragen.
     * Wächter: eine Form C, die sich einträgt, ohne sich auszutragen.
     * @spec SPEC-029
     */
    @Test
    fun `Form C traegt sich nicht ein`() = blocking {
        val backend = Rig().backend
        for (sql in listOf("SELECT 1", "SET LOCK_TIMEOUT 30000")) {
            val execution = backend.execute(queryRequest(sql))
            assertEquals(0, backend.registrations, "$sql: ausgeführt")
            val events = execution.events.toList()
            assertTrue(events.none { it is QueryEvent.Failure }, "$sql ist erkannt: $events")
            execution.close()
            assertEquals(0, backend.registrations, "$sql: nach dem Ende")
        }
    }
}
