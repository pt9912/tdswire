package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.config.ConfigIssue
import io.github.pt9912.tdswire.config.IssueKind
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val LIMIT = 1_048_576
private const val HEAD = "schema_format: \"1.0\"\n"

/**
 * Das Lesen der Datei der Tabellenbeschreibung durch den Lader (`SPEC-035`): die Größengrenze greift vor dem
 * Parsen, und was keine reguläre Datei ist, wird nicht gelesen. Die Meldung nennt Schlüssel und Art, nie den Pfad.
 */
internal class ConfigSimulationFileTest : TempFilesTest() {
    private fun environment(path: String) =
        testModeEnvironment(file("users.txt", "alice\t{plain}secret\n"), EnvKeys.SIMULATION_DESCRIPTION_FILE to path)

    private fun padded(size: Int): ByteArray =
        HEAD.toByteArray() + "#".repeat(size - HEAD.length - 1).toByteArray() + "\n".toByteArray()

    private fun issueOf(path: String): ConfigIssue = singleIssue(environment(path))

    private fun unreadable() = ConfigIssue(EnvKeys.SIMULATION_DESCRIPTION_FILE, IssueKind.UNREADABLE)

    /**
     * Eine Datei von genau 1 MiB wird gelesen, ein Byte mehr ist `TOO_LARGE`; ein fehlender Schlüssel ergibt keine
     * Bytes, ein Pfad aus Leerzeichen ist `INVALID_VALUE`, eine fehlende Datei `UNREADABLE`.
     *
     * @spec SPEC-035
     */
    @Test
    fun `die Groessengrenze greift vor dem Parsen`() {
        val exact = ConfigLoader.load(environment(file("exact.yaml", padded(LIMIT)))) as ConfigResult.Loaded
        assertContentEquals(padded(LIMIT), exact.simulationFile)

        val over = file("over.yaml", padded(LIMIT + 1))
        assertEquals(ConfigIssue(EnvKeys.SIMULATION_DESCRIPTION_FILE, IssueKind.TOO_LARGE), issueOf(over))
        assertEquals(unreadable(), issueOf("/nicht/vorhanden/sim.yaml"))
        assertEquals(ConfigIssue(EnvKeys.SIMULATION_DESCRIPTION_FILE, IssueKind.INVALID_VALUE), issueOf(" "))

        val noKey = testModeEnvironment(file("u.txt", "alice\t{plain}secret\n"))
        val without = ConfigLoader.load(noKey) as ConfigResult.Loaded
        assertNull(without.simulationFile)
    }

    /**
     * Ein Pfad, der keine reguläre Datei ist, endet mit `UNREADABLE`, ohne gelesen zu werden: ein Verzeichnis, ein
     * Gerät (`/dev/zero` lieferte unbegrenzt Bytes und endete sonst als `TOO_LARGE`) und ein Pfad, den die
     * Plattform nicht bildet (NUL-Zeichen). Eine FIFO ist nicht getestet (ein Öffnen ohne Schreiber bliebe hängen).
     *
     * @spec SPEC-035
     */
    @Test
    fun `ein Pfad ohne reguelaere Datei ist unlesbar`() {
        val directory = File(file("x.yaml", HEAD)).parent
        for (path in listOf(directory, "/dev/zero", "/dev/null", "sim\u0000.yaml")) {
            assertEquals(unreadable(), issueOf(path), path)
        }
    }

    /**
     * Eine reguläre Datei, deren Lesen mit einem Ein-/Ausgabefehler endet, ist `UNREADABLE`. Die Datei ist
     * `/proc/self/mem`: für Java regulär, das Lesen ab Stelle 0 endet mit „Input/output error“ (gemessen unter
     * Linux, auch als Root). Der Test prüft die Voraussetzung selbst.
     *
     * @spec SPEC-035
     */
    @Test
    fun `eine regulaere Datei mit Lesefehler ist unlesbar`() {
        val path = "/proc/self/mem"
        assertTrue(Files.isRegularFile(Paths.get(path)))
        assertFailsWith<IOException> { File(path).inputStream().use { it.readNBytes(1) } }
        assertEquals(unreadable(), issueOf(path))
    }

    /**
     * Ein Symlink auf eine reguläre Datei wird gelesen wie die Datei; ein Symlink auf ein Verzeichnis ist
     * `UNREADABLE`.
     *
     * @spec SPEC-035
     */
    @Test
    fun `ein Symlink auf eine Datei laedt und einer auf ein Verzeichnis ist unlesbar`() {
        val target = File(file("target.yaml", HEAD))
        val toFile = target.parentFile.toPath().resolve("link-file.yaml")
        Files.createSymbolicLink(toFile, target.toPath())
        val toDirectory = target.parentFile.toPath().resolve("link-dir")
        Files.createSymbolicLink(toDirectory, target.parentFile.toPath())

        val loaded = ConfigLoader.load(environment(toFile.toString())) as ConfigResult.Loaded
        assertContentEquals(HEAD.toByteArray(), loaded.simulationFile)
        assertEquals(unreadable(), issueOf(toDirectory.toString()))
    }
}
