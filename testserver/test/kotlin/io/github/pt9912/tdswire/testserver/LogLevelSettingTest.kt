package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.config.ConfigIssue
import io.github.pt9912.tdswire.config.IssueKind
import io.github.pt9912.tdswire.domain.telemetry.EventLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `LOG_LEVEL` als Eingang des Testservers ([LogLevelSetting]). */
internal class LogLevelSettingTest {
    private fun environment(value: String?) = if (value == null) emptyMap() else mapOf(LogLevelSetting.KEY to value)

    /**
     * Ohne Schlüssel gilt `INFO`; die vier Namen in genau dieser Schreibweise wählen ihre Ebene und sind kein Fehler.
     *
     * @spec SPEC-052
     */
    @Test
    fun `Ohne Schluessel gilt INFO und die vier Namen waehlen ihre Ebene`() {
        assertEquals(EventLevel.INFO, LogLevelSetting.levelOf(environment(null)))
        assertNull(LogLevelSetting.issue(environment(null)))
        for (level in EventLevel.entries) {
            assertEquals(level, LogLevelSetting.levelOf(environment(level.name)))
            assertNull(LogLevelSetting.issue(environment(level.name)))
        }
    }

    /**
     * Jeder andere Wert ist `INVALID_VALUE`: andere Schreibweise, ein unbekannter Name, Leerraum, eine Zahl und der
     * leere Wert.
     *
     * @spec SPEC-052
     */
    @Test
    fun `Jeder andere Wert ist ungueltig`() {
        val invalid = ConfigIssue(LogLevelSetting.KEY, IssueKind.INVALID_VALUE)
        for (value in listOf("info", "Warn", "TRACE", "", " INFO", "WARN ", "3", "OFF")) {
            assertEquals(invalid, LogLevelSetting.issue(environment(value)), value)
            assertEquals(EventLevel.INFO, LogLevelSetting.levelOf(environment(value)), value)
        }
    }

    /**
     * Ein Ereignis kommt durch, wenn seine Ebene nicht tiefer liegt als die gewählte; die Reihenfolge der Aufzählung
     * ist `ERROR`, `WARN`, `INFO`, `DEBUG`, und die Filterung hängt an ihr.
     *
     * @spec SPEC-052
     */
    @Test
    fun `Die Ebene eines Ereignisses kommt durch, wenn sie nicht tiefer liegt`() {
        assertEquals(listOf("ERROR", "WARN", "INFO", "DEBUG"), EventLevel.entries.map { it.name })
        for (chosen in EventLevel.entries) {
            for (event in EventLevel.entries) {
                val expected = event.ordinal <= chosen.ordinal
                assertEquals(expected, LogLevelSetting.passes(event, chosen), "$event bei $chosen")
            }
        }
        assertTrue(LogLevelSetting.passes(EventLevel.ERROR, EventLevel.ERROR))
        assertFalse(LogLevelSetting.passes(EventLevel.WARN, EventLevel.ERROR))
    }
}
