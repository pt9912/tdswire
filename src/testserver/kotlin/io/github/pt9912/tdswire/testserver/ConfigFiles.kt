package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.config.ConfigIssue
import io.github.pt9912.tdswire.config.IssueKind
import io.github.pt9912.tdswire.config.auth.UserList
import io.github.pt9912.tdswire.config.auth.UserListResult
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Paths

/** Obergrenze der Datei der Tabellenbeschreibung in Bytes, vor dem Parsen (`SPEC-035`, Voreinstellung 1 MiB). */
internal const val MAX_SIMULATION_FILE_BYTES = 1_048_576

/**
 * Das Lesen der Dateien der Konfiguration (siehe [ConfigLoader]): Bytes lesen, als UTF-8 lesen, die
 * Benutzerliste prüfen. Ein Fehler wird mit Schlüssel und Art gemeldet, nie mit Pfad oder Ausnahmetext.
 */
internal object ConfigFiles {
    private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    fun users(environment: Map<String, String>, plain: Boolean?, issues: MutableList<ConfigIssue>): UserList? {
        val key = EnvKeys.AUTH_USERS_FILE
        val path = environment[key]
        if (path == null) issues += ConfigIssue(key, IssueKind.MISSING)
        val bytes = path?.let { readBytes(key, it, issues) }
        val text = when {
            bytes == null -> null
            bytes.startsWith(BOM) -> bomIssue(key, issues)
            else -> decode(key, bytes, issues)
        }
        return if (text == null || plain == null) null else validated(text, plain, issues)
    }

    /** Die Bytes der Datei der Tabellenbeschreibung; `null` ohne Schlüssel und nach einem Fehler (in [issues]). */
    fun simulation(environment: Map<String, String>, issues: MutableList<ConfigIssue>): ByteArray? {
        val key = EnvKeys.SIMULATION_DESCRIPTION_FILE
        val path = environment[key]
        val bytes = when {
            path == null -> null
            path.isBlank() -> invalid(key, IssueKind.INVALID_VALUE, issues)
            else -> readAtMost(key, path, MAX_SIMULATION_FILE_BYTES, issues)
        }
        val tooLarge = bytes != null && bytes.size > MAX_SIMULATION_FILE_BYTES
        if (tooLarge) issues += ConfigIssue(key, IssueKind.TOO_LARGE)
        return bytes.takeIf { !tooLarge }
    }

    private fun invalid(key: String, kind: IssueKind, issues: MutableList<ConfigIssue>): ByteArray? {
        issues += ConfigIssue(key, kind)
        return null
    }

    /**
     * Liest höchstens [limit] plus ein Byte: ein größeres Ergebnis sagt „zu groß“, ohne die Datei ganz zu laden.
     * Nur eine reguläre Datei (auch über einen Symlink) wird geöffnet; ein Verzeichnis, ein Gerät und eine reguläre
     * Datei mit Lesefehler sind `UNREADABLE` (getestet, der Lesefehler über `/proc/self/mem`, weil die Tests als Root
     * laufen; eine Datei ohne Leserecht ist nicht getestet). Eine FIFO ist keine reguläre Datei, wird also nicht
     * geöffnet (nicht getestet: ein Öffnen ohne Schreiber bliebe hängen).
     */
    private fun readAtMost(key: String, path: String, limit: Int, issues: MutableList<ConfigIssue>): ByteArray? = try {
        val file = Paths.get(path)
        if (Files.isRegularFile(file)) {
            Files.newInputStream(file).use { it.readNBytes(limit + 1) }
        } else {
            issues += ConfigIssue(key, IssueKind.UNREADABLE)
            null
        }
    } catch (ignored: IOException) {
        issues += ConfigIssue(key, IssueKind.UNREADABLE)
        null
    } catch (ignored: InvalidPathException) {
        issues += ConfigIssue(key, IssueKind.UNREADABLE)
        null
    }

    private fun bomIssue(key: String, issues: MutableList<ConfigIssue>): String? {
        issues += ConfigIssue(key, IssueKind.BYTE_ORDER_MARK)
        return null
    }

    private fun validated(text: String, plain: Boolean, issues: MutableList<ConfigIssue>): UserList? =
        when (val result = UserList.parse(text, plain)) {
            is UserListResult.Valid -> result.list

            is UserListResult.Invalid -> {
                issues += ConfigIssue(EnvKeys.AUTH_USERS_FILE, IssueKind.USER_LIST_INVALID, result.error)
                null
            }
        }

    fun readBytes(key: String, path: String, issues: MutableList<ConfigIssue>): ByteArray? = try {
        Files.readAllBytes(Paths.get(path))
    } catch (ignored: IOException) {
        issues += ConfigIssue(key, IssueKind.UNREADABLE)
        null
    } catch (ignored: InvalidPathException) {
        issues += ConfigIssue(key, IssueKind.UNREADABLE)
        null
    }

    fun decode(key: String, bytes: ByteArray, issues: MutableList<ConfigIssue>): String? = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (ignored: CharacterCodingException) {
        issues += ConfigIssue(key, IssueKind.NOT_UTF8)
        null
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}
