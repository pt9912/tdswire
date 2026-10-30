package io.github.pt9912.tdswire.config

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest

/** Temporäre Dateien eines Tests; [cleanUp] löscht das Verzeichnis nach jedem Test. */
open class TempFilesTest {
    private val directory: Path = Files.createTempDirectory("tdswire-test")

    /** Legt eine Datei mit [bytes] an und gibt ihren Pfad zurück. */
    fun file(name: String, bytes: ByteArray): String {
        val path = directory.resolve(name)
        Files.write(path, bytes)
        return path.toString()
    }

    fun file(name: String, text: String): String = file(name, text.toByteArray(Charsets.UTF_8))

    @AfterTest
    fun cleanUp() {
        File(directory.toString()).deleteRecursively()
    }
}
