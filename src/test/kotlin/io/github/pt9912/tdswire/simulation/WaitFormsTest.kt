package io.github.pt9912.tdswire.simulation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Tests der Zuordnung zu Form Z (`SPEC-036`, `ADR-0058` Entscheidung 5): `WAITFOR DELAY '<hh>:<mm>:<ss>'` als einziger
 * Text. Gemessen ist nur `'00:00:10'` (Mitschnitt der vier Szenarien mit `WAITFOR`); der Bereich von `hh:mm:ss` und
 * `00:00:00` sind Setzung. Die Erwartungen sind von Hand geschrieben.
 */
class WaitFormsTest {
    private fun assertWait(expected: Duration, sql: String) {
        val result = recognize(sql)
        assertIs<Recognition.Wait>(result, "erwartet Form Z: $sql")
        assertEquals(expected, result.delay, sql)
    }

    /**
     * Happy und Boundary: die gemessene Frist, die Grenzen des Bereichs (`00:00:00`, `23:59:59`), eine gemischte Frist,
     * jede Schreibweise der Schlüsselwörter, Leerraum, Kommentare und ein abschließendes `;`.
     * Wächter: eine Erkennung, die Stunden, Minuten und Sekunden vertauscht oder die Frist verwirft.
     * @spec SPEC-036
     */
    @Test
    fun `erkennt WAITFOR DELAY mit hh mm ss`() {
        assertWait(10.seconds, "WAITFOR DELAY '00:00:10'")
        assertWait(Duration.ZERO, "WAITFOR DELAY '00:00:00'")
        assertWait(23.hours + 59.minutes + 59.seconds, "WAITFOR DELAY '23:59:59'")
        assertWait(1.hours + 2.minutes + 3.seconds, "WAITFOR DELAY '01:02:03'")
        assertWait(5.minutes, "waitfor delay '00:05:00';")
        assertWait(10.seconds, "  WaitFor\r\n\tDelay /* c */ '00:00:10' -- x\n")
    }

    /**
     * Negativ (`ADR-0058` Entscheidung 5): außerhalb des Bereichs, andere Schreibweisen der Frist, andere Formen von
     * `WAITFOR`, Variable oder Parameter, Folgeanweisungen und jeder Text vor oder nach dem einen Satz. Die Frist ist
     * ein Zeichenliteral ohne `N` aus ASCII-Ziffern, je zwei.
     * @spec SPEC-036
     */
    @Test
    fun `WAITFOR DELAY hat keine Abwandlung`() {
        assertOnlyWaitTwin(
            "WAITFOR DELAY '00:00:10'",
            "WAITFOR DELAY '24:00:00'",
            "WAITFOR DELAY '00:60:00'",
            "WAITFOR DELAY '00:00:60'",
            "WAITFOR DELAY '0:00:10'",
            "WAITFOR DELAY '00:00:1'",
            "WAITFOR DELAY '00:10'",
            "WAITFOR DELAY '10'",
            "WAITFOR DELAY '00:00:10.5'",
            "WAITFOR DELAY '00:00:10.500'",
            "WAITFOR DELAY '000:00:10'",
            "WAITFOR DELAY ' 00:00:10'",
            "WAITFOR DELAY '00:00:10 '",
            "WAITFOR DELAY '00-00-10'",
            "WAITFOR DELAY '-0:00:10'",
            "WAITFOR DELAY '٠٠:٠٠:١٠'",
            "WAITFOR DELAY ''",
            "WAITFOR DELAY N'00:00:10'",
            "WAITFOR DELAY 00:00:10",
            "WAITFOR DELAY 10",
            "WAITFOR DELAY @d",
            "WAITFOR DELAY @@VERSION",
            "WAITFOR DELAY",
            "WAITFOR",
            "WAITFOR TIME '10:00:00'",
            "WAITFOR DELAY TIME '00:00:10'",
            "WAITFOR (RECEIVE 1)",
            "WAITFOR DELAY '00:00:10' x",
            "WAITFOR DELAY '00:00:10', TIMEOUT 1",
            "WAITFOR DELAY '00:00:10';;",
            "WAITFOR DELAY '00:00:10'; SELECT 1",
            "WAITFOR DELAY '00:00:10'; WAITFOR DELAY '00:00:10'",
            "SELECT 1; WAITFOR DELAY '00:00:10'",
            "SET ANSI_NULLS ON; WAITFOR DELAY '00:00:10'",
            "WAITFOR DELAY '00:00:10' SELECT 1",
            "SELECT 1 WAITFOR DELAY '00:00:10'",
            "SET FMTONLY ON WAITFOR DELAY '00:00:10'",
            "IF 1 = 1 WAITFOR DELAY '00:00:10'",
        )
    }

    private fun assertOnlyWaitTwin(twin: String, vararg variants: String) {
        assertIs<Recognition.Wait>(recognize(twin), "Zwilling muss Form Z sein: $twin")
        for (variant in variants) assertNotRecognized(variant)
    }

    /**
     * Die Planung gibt die Frist weiter und prüft keine Tabelle: `WAITFOR DELAY` ist ein Text ohne Objekte.
     * @spec SPEC-036
     */
    @Test
    fun `der Planer reicht die Wartezeit weiter`() {
        val planner = StatementPlanner(SimCatalog(emptyList()))
        assertEquals(PlanResult.Wait(10.seconds), planner.plan("WAITFOR DELAY '00:00:10'"))
        assertIs<PlanResult.Rejected>(planner.plan("WAITFOR DELAY '24:00:00'"))
    }
}
