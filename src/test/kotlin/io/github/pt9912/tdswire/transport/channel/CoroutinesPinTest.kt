package io.github.pt9912.tdswire.transport.channel

import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class CoroutinesPinTest {
    /**
     * Die Laufzeitbibliothek liegt auf dem Classpath des Test-Gates, und es ist die im Katalog
     * gepinnte Version: das geladene Jar heißt `kotlinx-coroutines-core-jvm-1.10.1.jar`. Der Test
     * belegt den Katalog-Eintrag, nicht die Prüfsumme; diese hält Gradle beim Auflösen
     * (`gradle/verification-metadata.xml`).
     *
     * @spec SPEC-042
     */
    @Test
    fun `kotlinx-coroutines-core liegt in der gepinnten Version vor`() {
        val jar = Job::class.java.protectionDomain.codeSource.location.path.substringAfterLast('/')
        assertEquals("kotlinx-coroutines-core-jvm-1.10.1.jar", jar)
        assertEquals(42, runBlocking { 40 + 2 })
    }
}
