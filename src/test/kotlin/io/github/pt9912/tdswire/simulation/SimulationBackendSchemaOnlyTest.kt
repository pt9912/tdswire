package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.QueryEvent
import io.github.pt9912.tdswire.domain.QueryParameter
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.domain.StatementKind
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Form F (`SPEC-036`, `ExecuteReader(SchemaOnly)`, `ADR-0059`) am Backend. Die Anfragetexte der Happy-Fälle sind die
 * Texte der Messung (`referenztest-dotnet/Mitschnitt/messung/2022/schemaonly-spalten.messung`, Anfragen 01 und 03,
 * `schemaonly-sonderfaelle.messung`, Anfragen 03 bis 05; Messprotokoll W-26 bis W-29; `Microsoft.Data.SqlClient` 7.1.1,
 * ein Fall je Instanz). Gemessen sind Spaltenlisten, `*`, die Tabellen `person`, `typen` und eine Tabelle ohne
 * Schlüssel, `WHERE Id = @i` im RPC und die unbekannte Tabelle; alle übrigen Bedingungen von S1, andere Fehler der
 * Auswahl und die Grenzen der Erkennung sind Setzung (`ADR-0059` Entscheidung 2 und 4), nicht Messung.
 */
class SimulationBackendSchemaOnlyTest {
    private val off = QueryEvent.UpdateCount(0, StatementKind.SET_FMTONLY_OFF)
    private val on = QueryEvent.UpdateCount(0, StatementKind.SET_FMTONLY_ON)
    private val rows = listOf(personRow(1, "Ada", 36, "10.50", true))

    /** Der Befehl mit den `SET` des Clients (Messung W-26): führendes Leerzeichen, schließendes `SET` ohne `;`. */
    private fun schemaOnly(command: String) = " SET FMTONLY OFF; SET FMTONLY ON;$command SET FMTONLY OFF;"

    /** Die Spalten der Auswahl ohne `SET`: die Antwort von Form F trägt dieselben (W-27). */
    private fun columnsOf(probe: Probe, command: String): List<*> {
        val plain = collectEvents(probe.backend, "$command WHERE 1 = 0")
        return assertIs<QueryEvent.ResultStart>(plain.first()).columns
    }

    /**
     * Happy (Handvektor W-27, Anfrage 01 von `schemaonly-spalten`): zwei Abschlüsse der `SET`, die Spalten der Auswahl
     * ohne Zeile, der Zähler 0 und das schließende `SET`; die Zeilenquelle bleibt zu. Wächter: eine Antwort ohne die
     * Abschlüsse (die Probe), ohne das schließende `SET`, eine Zeile in der Antwort, eine geöffnete Quelle.
     * @spec SPEC-036
     * @spec TDS-FA-11
     * @spec TDS-QA-01
     */
    @Test
    fun `SchemaOnly liefert die Spalten ohne Zeile zwischen den Abschluessen der SET`() {
        val probe = Probe(rows)
        val command = "SELECT Id, Name, Age, Score FROM person"
        val events = collectEvents(probe.backend, schemaOnly(command))

        val start = assertIs<QueryEvent.ResultStart>(events[2])
        assertEquals(listOf("Id", "Name", "Age", "Score"), start.columns.map { it.name })
        assertEquals(columnsOf(Probe(rows), command), start.columns)
        assertEquals(listOf(off, on, start, QueryEvent.ResultEnd(0), off), events)
        assertEquals(0, probe.opens)
    }

    /**
     * Happy (Handvektor W-26, Anfrage 03 von `schemaonly-spalten`): `*` liefert alle Spalten der Tabelle in ihrer
     * Reihenfolge. Wächter: eine Auswahlliste, die `*` nicht auflöst.
     * @spec SPEC-036
     * @spec TDS-QA-01
     */
    @Test
    fun `SchemaOnly mit Stern liefert alle Spalten`() {
        val probe = Probe(rows)
        val events = collectEvents(probe.backend, schemaOnly("SELECT * FROM person"))
        val start = assertIs<QueryEvent.ResultStart>(events[2])
        assertEquals(personColumns.map { it.name }, start.columns.map { it.name })
        assertEquals(5, events.size)
        assertEquals(0, probe.opens)
    }

    /**
     * Happy (Handvektor W-26, Anfrage 03 von `schemaonly-sonderfaelle`): eine Tabelle ohne Schlüssel antwortet in
     * derselben Form; die Beschreibung der Tabelle kennt den Schlüssel für Form F nicht als Eingabe. Wächter: eine
     * Antwort, die einen Schlüssel verlangt.
     * @spec SPEC-036
     * @spec TDS-QA-01
     */
    @Test
    fun `SchemaOnly auf eine Tabelle ohne Schluessel antwortet in derselben Form`() {
        val table = SimTable(
            name = "wire_schema_ohne_s",
            columns = listOf(SimColumn("A", SqlType.Int32, nullable = false), SimColumn("B", SqlType.UnicodeText)),
            primaryKey = emptyList(),
            rows = { error("die Zeilenquelle bleibt zu") },
        )
        val backend = SimulationBackend(SimCatalog(listOf(table)))
        val events = collectEvents(backend, schemaOnly("SELECT A, B FROM wire_schema_ohne_s"))
        assertEquals(listOf(off, on), events.take(2))
        assertEquals(listOf("A", "B"), assertIs<QueryEvent.ResultStart>(events[2]).columns.map { it.name })
        assertEquals(listOf(QueryEvent.ResultEnd(0), off), events.drop(3))
    }

    /**
     * Fehler (Handvektor W-29, Anfrage 05 von `schemaonly-sonderfaelle`): die unbekannte Tabelle liefert beide
     * Abschlüsse der führenden `SET`, dann `TDS_SIM_UNKNOWN_OBJECT` mit dem Namen des Textes; das schließende `SET`
     * gibt es nicht. Dass `FMTONLY` nach dem Fehler nicht im Zustand der Verbindung bleibt, ist Setzung ohne Messung am
     * Server (W-29 zeigt die Folgeabfrage des Clients, nicht den Zustand der Bibliothek) und hier nicht getestet.
     * Wächter: ein Fehler ohne die zwei Abschlüsse davor, ein Abschluss für das schließende `SET` nach dem Fehler, ein
     * fehlender Name.
     * @spec SPEC-036
     * @spec SPEC-037
     * @spec TDS-QA-01
     */
    @Test
    fun `SchemaOnly auf eine unbekannte Tabelle meldet den Fehler nach den zwei Abschluessen`() {
        val probe = Probe(rows)
        val events = collectEvents(probe.backend, schemaOnly("SELECT Id FROM gibtesnicht"))
        assertEquals(3, events.size)
        assertEquals(listOf(off, on), events.take(2))
        val failure = assertIs<QueryEvent.Failure>(events[2])
        assertEquals(SimCodes.UNKNOWN_OBJECT, failure.code)
        assertEquals("gibtesnicht", failure.objectName)
        assertEquals(0, probe.opens)
    }

    /**
     * RPC (Handvektor W-28, Anfrage 04 von `schemaonly-sonderfaelle`): der Text mit `WHERE Id = @i` und dem Parameter
     * hat kein schließendes `SET` und endet nach dem Zähler 0 der Auswahl. Wächter: ein erfundenes schließendes `SET`,
     * eine Zeile, eine geöffnete Quelle.
     * @spec SPEC-030
     * @spec SPEC-036
     * @spec TDS-QA-01
     */
    @Test
    fun `SchemaOnly mit Parameter ohne schliessendes SET endet nach der Auswahl`() {
        val probe = Probe(rows)
        val text = " SET FMTONLY OFF; SET FMTONLY ON;SELECT Id, Name FROM person WHERE Id = @i"
        val events = collectEvents(probe.backend, text, listOf(QueryParameter("@i", SqlType.Int32, 1)))
        assertEquals(listOf(off, on), events.take(2))
        assertEquals(listOf("Id", "Name"), assertIs<QueryEvent.ResultStart>(events[2]).columns.map { it.name })
        assertEquals(listOf<QueryEvent>(QueryEvent.ResultEnd(0)), events.drop(3))
        assertEquals(0, probe.opens)
    }

    /**
     * Setzung (ADR-0059 Entscheidung 2, nicht gemessen): jede erkannte Bedingung von S1 ist erlaubt (`AND`-Folge,
     * `OR`-Folge, `IN`, `IS NULL`, Konstantenterm, ein Parameter), die Antwort hat nie eine Zeile und öffnet die Quelle
     * nicht. Wächter: eine Bedingung, die den Vorfilter öffnet; eine `OR`-Folge, die als Auswahl mit Zeilen läuft.
     * @spec SPEC-036
     */
    @Test
    fun `SchemaOnly erlaubt die Bedingungen von S1 und liefert nie eine Zeile`() {
        val probe = Probe(rows)
        listOf(
            "WHERE Id = 1 AND Age IS NULL",
            "WHERE Id = 1 OR Id = 2",
            "WHERE Id IN (1, 2)",
            "WHERE 1 = 1",
            "WHERE 1 = 0",
            "WHERE Id = @i OR Age = 3",
        ).forEach { where ->
            val events = collectEvents(
                probe.backend,
                schemaOnly("SELECT Id FROM person $where"),
                listOf(QueryParameter("@i", SqlType.Int32, 1)),
            )
            assertEquals(listOf(off, on), events.take(2), where)
            assertEquals(QueryEvent.ResultEnd(0), events[3], where)
            assertEquals(5, events.size, where)
        }
        assertEquals(0, probe.opens)
    }

    /**
     * Setzung (ADR-0059 Entscheidung 4, nicht gemessen): eine unbekannte Spalte (Liste oder Bedingung) und ein Wert,
     * der nicht in den Spaltentyp passt, enden wie die unbekannte Tabelle: zwei Abschlüsse, dann der Fehler, kein
     * schließendes `SET`. Der Fehler der Auswahl im RPC folgt derselben Regel (zwei vorangehende `DONEINPROC`); das ist
     * Setzung, nicht gemessen und nicht getestet (nur der Batch). Wächter: ein Fehler vor den Abschlüssen.
     * @spec SPEC-036
     * @spec SPEC-037
     */
    @Test
    fun `Andere Fehler der Auswahl folgen den zwei Abschluessen`() {
        val probe = Probe(rows)
        val unknownList = collectEvents(probe.backend, schemaOnly("SELECT Nothing FROM person"))
        val unknownWhere = collectEvents(probe.backend, schemaOnly("SELECT Id FROM person WHERE Nothing = 1"))
        val invalid = collectEvents(probe.backend, schemaOnly("SELECT Id FROM person WHERE Id = 'x'"))
        for (events in listOf(unknownList, unknownWhere, invalid)) {
            assertEquals(listOf(off, on), events.take(2))
            assertEquals(3, events.size)
            assertIs<QueryEvent.Failure>(events[2])
        }
        assertEquals(SimCodes.UNKNOWN_OBJECT, (unknownList[2] as QueryEvent.Failure).code)
        assertEquals(SimCodes.UNKNOWN_OBJECT, (unknownWhere[2] as QueryEvent.Failure).code)
        assertEquals(SimCodes.INVALID_VALUE, (invalid[2] as QueryEvent.Failure).code)
        assertEquals(0, probe.opens)
    }

    /**
     * Setzung (ADR-0059 Entscheidung 2): Schlüsselwörter ohne Beachtung der Schreibweise, Leerraum, Kommentare und ein
     * schema-qualifizierter Name; das abschließende `;` und das schließende `SET` sind optional und einzeln erlaubt.
     * Wächter: eine Erkennung, die die Schreibweise der Schlüsselwörter vergleicht.
     * @spec SPEC-036
     */
    @Test
    fun `Schreibweise und optionale Teile der Form F`() {
        val twin = collectEvents(Probe(rows).backend, schemaOnly("SELECT Id FROM person"))
        val variants = mapOf(
            "set fmtonly off;set fmtonly on;select Id from person set fmtonly off" to CLOSED_SIZE,
            "SET FMTONLY OFF ; /* c */ SET FMTONLY ON ;\nSELECT Id FROM dbo.person\nSET FMTONLY OFF ;" to CLOSED_SIZE,
            " SET FMTONLY OFF; SET FMTONLY ON;SELECT Id FROM person" to OPEN_SIZE,
            " SET FMTONLY OFF; SET FMTONLY ON;SELECT Id FROM person;" to OPEN_SIZE,
        )
        for ((text, size) in variants) {
            val events = collectEvents(Probe(rows).backend, text)
            assertEquals(twin.take(2), events.take(2), text)
            assertEquals(QueryEvent.ResultEnd(0), events[3], text)
            assertEquals(size, events.size, text)
        }
    }

    /**
     * Negativ (ADR-0059 Entscheidung 1 und 2): jede andere Folge endet als einziges `Failure` `TDS_UNSUPPORTED_SQL`,
     * ohne Abschluss der `SET`, ohne Zeilenquelle: die Texte von `KeyInfo` und `SchemaOnly|KeyInfo` (Messung,
     * `keyinfo-spalten` Anfrage 01, `schemaonly-und-keyinfo` Anfrage 01), kein führendes `SET FMTONLY OFF;`, kein `;`
     * hinter einem der führenden `SET`, die `SET` in anderer Reihenfolge, ein `;` vor dem schließenden `SET`, ein
     * doppeltes schließendes `SET`, eine dritte `SET`-Anweisung, ein `SET FMTONLY ON` vor der Form (die Probe der
     * Form B liest ihren Rest nicht als Form F). Wächter: eine Erkennung, die `NO_BROWSETABLE` mitnimmt oder die
     * Reihenfolge nicht prüft.
     * @spec SPEC-036
     * @spec SPEC-037
     */
    @Test
    fun `Texte ausserhalb der Form F sind TDS_UNSUPPORTED_SQL ohne Abschluss`() {
        val probe = Probe(rows)
        listOf(
            " SET FMTONLY OFF; SET NO_BROWSETABLE ON;SELECT Id, Name, Age, Score FROM person SET NO_BROWSETABLE OFF;",
            " SET FMTONLY OFF; SET NO_BROWSETABLE ON; SET FMTONLY ON;SELECT Id FROM person" +
                " SET FMTONLY OFF; SET NO_BROWSETABLE OFF;",
            " SET FMTONLY OFF; SET FMTONLY ON; SET NO_BROWSETABLE ON;SELECT Id FROM person",
            " SET FMTONLY ON;SELECT Id FROM person SET FMTONLY OFF;",
            " SET FMTONLY OFF SET FMTONLY ON;SELECT Id FROM person",
            " SET FMTONLY OFF x SET FMTONLY ON;SELECT Id FROM person",
            " SET FMTONLY OFF; SET FMTONLY ON x SELECT Id FROM person",
            " SET FMTONLY OFF; SET FMTONLY ON SELECT Id FROM person",
            " SET FMTONLY ON; SET FMTONLY OFF;SELECT Id FROM person",
            " SET FMTONLY OFF; SET FMTONLY OFF;SELECT Id FROM person",
            " SET FMTONLY OFF; SET FMTONLY ON;SELECT Id FROM person; SET FMTONLY OFF;",
            " SET FMTONLY OFF; SET FMTONLY ON;SELECT Id FROM person SET FMTONLY OFF SET FMTONLY OFF;",
            " SET FMTONLY OFF; SET FMTONLY ON; SET NOCOUNT ON;SELECT Id FROM person",
            " SET FMTONLY OFF; SET FMTONLY ON;",
            " SET FMTONLY OFF; SET FMTONLY ON; SET FMTONLY OFF;",
            "SET FMTONLY ON SET FMTONLY OFF; SET FMTONLY ON;SELECT Id FROM person",
            "SET FMTONLY ON SET FMTONLY OFF; SET FMTONLY ON;SELECT Id FROM person SET FMTONLY OFF;",
            "EXEC sp_executesql N'SET FMTONLY ON SET FMTONLY OFF; SET FMTONLY ON; SELECT Id FROM person'",
        ).forEach { text ->
            val events = collectEvents(probe.backend, text)
            assertEquals(SimCodes.UNSUPPORTED_SQL, assertIs<QueryEvent.Failure>(events.single(), text).code, text)
        }
        assertEquals(0, probe.opens)
    }

    /**
     * Negativ (ADR-0059 Entscheidung 2, Setzung): hinter den führenden `SET` ist nur eine Auswahl der Form S1 aus einer
     * Tabelle erkannt; `SELECT <Ganzzahl>` (auch mit Alias), zwei Auswahlen (S2), `INSERT`, `UPDATE`, `DELETE`,
     * `MERGE`, `CREATE TABLE`, `WAITFOR`, ein Alias, ein Verbund und ein Katalogtext enden mit `TDS_UNSUPPORTED_SQL`
     * ohne Abschluss. Wächter: eine Auswahl, die jede Form der Liste hinter den `SET` zulässt.
     * @spec SPEC-036
     * @spec SPEC-037
     */
    @Test
    fun `Hinter den SET ist nur eine Auswahl der Form S1 erkannt`() {
        val probe = Probe(rows)
        listOf(
            "SELECT 2",
            "SELECT 1 AS eins",
            "SELECT Id FROM person; SELECT Name FROM person",
            "INSERT INTO person (Id) VALUES (9)",
            "UPDATE person SET Age = 1 WHERE Id = 1",
            "DELETE FROM person WHERE Id = 1",
            "CREATE TABLE neu (A int)",
            "WAITFOR DELAY '00:00:01'",
            "SELECT Id AS Kennung FROM person",
            "SELECT p.Id FROM person p JOIN person q ON p.Id = q.Id",
            "SELECT Id FROM person WHERE Id < 2",
            "SELECT COUNT(*) FROM person",
        ).forEach { command ->
            val events = collectEvents(probe.backend, schemaOnly(command))
            assertEquals(SimCodes.UNSUPPORTED_SQL, assertIs<QueryEvent.Failure>(events.single(), command).code, command)
            val open = collectEvents(probe.backend, " SET FMTONLY OFF; SET FMTONLY ON;$command")
            assertEquals(SimCodes.UNSUPPORTED_SQL, assertIs<QueryEvent.Failure>(open.single(), command).code, command)
        }
        assertEquals(0, probe.opens)
    }

    /**
     * Die Probe der Zieltabelle (Form B) bleibt unverändert (`ADR-0059` Entscheidung 6): ohne führendes `SET FMTONLY
     * OFF;` und ohne `;` antwortet sie mit den Spalten und ohne Abschlüsse der `SET`; mit dem Text von Form F hat sie
     * nichts zu tun. Wächter: Abschlüsse der `SET` in der Antwort der Probe.
     * @spec SPEC-036
     */
    @Test
    fun `Die Probe der Zieltabelle antwortet weiter ohne Abschluesse der SET`() {
        val probe = Probe(rows)
        val events = collectEvents(probe.backend, "SET FMTONLY ON SELECT Id FROM person SET FMTONLY OFF")
        assertEquals(2, events.size)
        assertIs<QueryEvent.ResultStart>(events.first())
        assertEquals(QueryEvent.ResultEnd(0), events.last())
        val exec = collectEvents(probe.backend, "EXEC sp_executesql N'SET FMTONLY ON SELECT Id FROM person'")
        assertEquals(2, exec.size)
        assertEquals(0, probe.opens)
    }

    /**
     * Die Ausführung der Form F gibt die der Auswahl frei: nach `close()` ist keine Ausführung unter Sitzung und
     * Anfrage registriert, und ein Cancel vor dem Sammeln beendet die Auswahl vor dem Ergebnis (die Abschlüsse der
     * `SET` stehen davor und danach, ein Ergebnis fehlt). Die Ereignisse nach einem Cancel sind Setzung, am Server
     * nicht gemessen (`SPEC-036` Form F); belegt ist die Austragung nur für `close()` und Cancel, nicht für das normale
     * Ende ohne `close()` und nicht für den Fehlerzweig. Wächter: ein `close()`, das die Auswahl nicht schließt, ein
     * Cancel, das die Auswahl nicht erreicht.
     * @spec SPEC-036
     */
    @Test
    fun `close und cancel wirken auf die Auswahl der Form F`() {
        val probe = Probe(rows)
        val backend = probe.backend
        runBlocking {
            val closed = backend.execute(queryRequest(schemaOnly("SELECT Id FROM person")))
            assertEquals(1, backend.registrations)
            closed.close()
            assertEquals(0, backend.registrations)

            val cancelled = backend.execute(queryRequest(schemaOnly("SELECT Id FROM person")))
            backend.cancel("s1", "r1")
            val events = cancelled.events.toList()
            cancelled.close()
            assertEquals(listOf(off, on, off), events)
            assertEquals(0, backend.registrations)
        }
    }

    private companion object {
        /** Zwei Abschlüsse, Anfang und Ende des Ergebnisses, schließendes `SET`. */
        const val CLOSED_SIZE = 5

        /** Wie [CLOSED_SIZE] ohne schließendes `SET`. */
        const val OPEN_SIZE = 4
    }
}
