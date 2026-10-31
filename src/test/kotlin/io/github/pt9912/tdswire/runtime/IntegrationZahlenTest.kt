package io.github.pt9912.tdswire.runtime

import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Die Zahlen des Integrationslaufs (`SPEC-040`, `make simulations-integration`) stehen an mehreren Orten: Heap in
 * `build.gradle.kts`, Text von `gross` in [BIG_ROWS] und [BIG_TEXT_CHARS], beide Zahlen in der Spezifikation und der
 * Heap in `harness/README.md`. Dieser Test hält die Orte gegeneinander (Kotlin und .NET hält der .NET-Test).
 */
internal class IntegrationZahlenTest {
    /**
     * Boundary: der Heap des Serverprozesses (`maxHeapSize`) und der Text von `gross` stehen in der Spezifikation,
     * der Heap auch in der Zeile des Ziels in `harness/README.md`; ändert jemand einen Wert, wird der Test rot.
     * Kein `@spec`-Tag: der Test belegt die Kopplung der Zahlen, nicht den Lauf des Gates.
     */
    @Test
    fun `Heap und Text des Integrationslaufs stimmen in Gradle Spezifikation und README`() {
        val gradle = File("build.gradle.kts").readText()
        val heapMiB = assertNotNull(
            Regex("""maxHeapSize\s*=\s*"(\d+)m"""").find(gradle)?.groupValues?.get(1),
            "maxHeapSize fehlt in build.gradle.kts",
        )
        val chars = (BIG_ROWS.toLong() * BIG_TEXT_CHARS).toString().reversed().chunked(3).joinToString(" ").reversed()
        val spec = File("spec/spezifikation.md").readLines().single { it.startsWith("| `SPEC-040`") }
        assertTrue(spec.contains("Serverprozess mit $heapMiB MiB Heap, Text von $chars Zeichen"), spec)
        val target = File("harness/README.md").readLines().single { it.startsWith("| `make simulations-integration`") }
        assertTrue(target.contains("über dem Heap ($heapMiB MiB)"), target)
    }
}
