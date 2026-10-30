package io.github.pt9912.tdswire.config

import io.github.pt9912.tdswire.config.auth.UserList
import io.github.pt9912.tdswire.config.auth.UserListResult
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Paths

/**
 * Das Lesen der Dateien der Konfiguration (siehe [ConfigLoader]): Bytes lesen, als UTF-8 lesen, die
 * Benutzerliste prüfen. Ein Fehler wird mit Schlüssel und Art gemeldet, nie mit Pfad oder Ausnahmetext.
 */
internal object ConfigFiles {
    private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    fun users(environment: Map<String, String>, plain: Boolean?, issues: MutableList<ConfigIssue>): UserList? {
        val key = ConfigKeys.AUTH_USERS_FILE
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

    private fun bomIssue(key: String, issues: MutableList<ConfigIssue>): String? {
        issues += ConfigIssue(key, IssueKind.BYTE_ORDER_MARK)
        return null
    }

    private fun validated(text: String, plain: Boolean, issues: MutableList<ConfigIssue>): UserList? =
        when (val result = UserList.parse(text, plain)) {
            is UserListResult.Valid -> result.list

            is UserListResult.Invalid -> {
                issues += ConfigIssue(ConfigKeys.AUTH_USERS_FILE, IssueKind.USER_LIST_INVALID, result.error)
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
