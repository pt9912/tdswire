package io.github.pt9912.tdswire.transport.channel

import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CoroutinesPinTest {
    /**
     * Die Laufzeitbibliothek liegt auf dem Classpath des Test-Gates, und die aufgelöste (gelockte)
     * Version ist die im Katalog `gradle/libs.versions.toml` eingetragene: der Jar-Name des
     * geladenen Jars trägt dieselbe Version wie der Katalog-Eintrag `coroutines`. Weicht der
     * Katalog vom Lock ab, wird der Test rot. Die Prüfsumme belegt der Test nicht; sie hält
     * Gradle beim Auflösen (`gradle/verification-metadata.xml`).
     */
    @Test
    fun `kotlinx-coroutines-core aufgeloeste Version ist die Katalog-Version`() {
        val jar = Job::class.java.protectionDomain.codeSource.location.path.substringAfterLast('/')
        val entry = Regex("""^coroutines\s*=\s*"([^"]+)"""")
        val catalogVersion = File("gradle/libs.versions.toml").readLines()
            .firstNotNullOfOrNull { entry.find(it)?.groupValues?.get(1) }
        assertNotNull(catalogVersion, "Eintrag `coroutines` fehlt im Katalog")
        assertEquals("kotlinx-coroutines-core-jvm-$catalogVersion.jar", jar)
        assertEquals(42, runBlocking { 40 + 2 })
    }
}
