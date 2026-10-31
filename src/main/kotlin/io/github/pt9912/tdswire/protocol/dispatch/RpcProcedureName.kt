package io.github.pt9912.tdswire.protocol.dispatch

import io.github.pt9912.tdswire.domain.ParameterDirection
import io.github.pt9912.tdswire.domain.QueryParameter

/**
 * Der Prozedurname eines RPC per Name und der Aufruftext, den der Dispatch daraus für das Backend
 * baut (`TDS-FA-04.a` Nr. 5, `SPEC-030`).
 *
 * Der Name ist Nutzereingabe und gelangt als Text in `QueryRequest.sql`. Deshalb gilt eine enge
 * Grammatik, die jede Zeichenfolge ausschließt, die über einen Bezeichner hinaus Anweisungen trägt:
 * ein bis [MAX_PARTS] Teile, durch `.` getrennt, jeder Teil entweder ein regulärer Bezeichner
 * (Buchstabe, `_` oder `#`, dann Buchstaben, Ziffern, `_`, `@`, `#`, `$`) oder in `[ ]` mit `]]` als
 * Escape für ein `]`; höchstens [MAX_PART_CHARS] Zeichen je Teil, kein Steuerzeichen. Die Grammatik ist
 * eine Festlegung ohne Messung (Obergrenzen und Zeichenklassen gesetzt, nicht am Client oder an einem
 * SQL Server gemessen).
 *
 * Der Aufruftext enthält nur den Namen und die Namen der Parameter, nie einen Wert: `EXEC <name>
 * @a = @a, @o = @o OUTPUT`. Die Werte stehen in der Parameterliste desselben `QueryRequest`.
 */
internal object RpcProcedureName {
    const val MAX_PARTS = 4
    const val MAX_PART_CHARS = 128
    private const val SEPARATOR = '.'
    private const val OPEN = '['
    private const val CLOSE = ']'
    private const val CURSOR_PREFIX = "sp_cursor"
    private val catalogNames = setOf("sp_executesql", "sp_prepexec", "sp_execute", "sp_prepare", "sp_unprepare")

    /** Wahr, wenn [name] der Grammatik entspricht. */
    fun isValid(name: String): Boolean = parts(name) != null

    /**
     * Wahr, wenn der letzte Teil eines gültigen [name] (ohne Klammern, ohne Beachtung der Schreibweise) eine
     * Prozedur nennt, die der Dispatch über die ProcID kennt oder ablehnt: `sp_executesql`, `sp_prepexec`,
     * `sp_execute`, `sp_prepare`, `sp_unprepare` und alles mit dem Präfix `sp_cursor`. Per Name gesendet
     * erreichen sie das Backend nicht (Festlegung).
     */
    fun isCatalogProcedure(name: String): Boolean {
        val last = parts(name)?.last()?.let(::unquote)?.lowercase() ?: return false
        return last in catalogNames || last.startsWith(CURSOR_PREFIX)
    }

    /** Der Aufruftext für einen gültigen [name] mit den benannten [parameters] in ihrer Reihenfolge. */
    fun execText(name: String, parameters: List<QueryParameter>): String {
        val arguments = parameters.joinToString(", ") { argument(it) }
        return if (arguments.isEmpty()) "EXEC $name" else "EXEC $name $arguments"
    }

    private fun argument(parameter: QueryParameter): String {
        val suffix = if (parameter.direction == ParameterDirection.INPUT) "" else " OUTPUT"
        return "${parameter.name} = ${parameter.name}$suffix"
    }

    private fun unquote(part: String): String =
        if (part.startsWith(OPEN)) part.substring(1, part.length - 1).replace("]]", "]") else part

    private fun parts(name: String): List<String>? {
        val parts = mutableListOf<String>()
        var start = 0
        while (parts.size < MAX_PARTS) {
            val end = partEnd(name, start) ?: return null
            parts += name.substring(start, end)
            if (end == name.length) return parts
            if (name[end] != SEPARATOR) return null
            start = end + 1
        }
        return null
    }

    private fun partEnd(name: String, start: Int): Int? = when {
        start >= name.length -> null
        name[start] == OPEN -> quotedEnd(name, start)
        else -> plainEnd(name, start)
    }

    /** Das Ende eines Teils in Klammern (Index hinter `]`) oder `null`, wenn er leer, zu lang oder offen ist. */
    private fun quotedEnd(name: String, start: Int): Int? {
        var index = start + 1
        var chars = 0
        while (index < name.length && !name[index].isISOControl()) {
            if (name[index] == CLOSE) {
                if (index + 1 < name.length && name[index + 1] == CLOSE) {
                    index += 2
                    chars++
                    continue
                }
                return (index + 1).takeIf { chars in 1..MAX_PART_CHARS }
            }
            index++
            chars++
        }
        return null
    }

    /** Das Ende eines regulären Bezeichners (Index des Trennzeichens oder Namensende) oder `null`. */
    private fun plainEnd(name: String, start: Int): Int? {
        val first = name[start]
        if (!(first.isLetter() || first == '_' || first == '#')) return null
        var index = start + 1
        while (index < name.length && (name[index].isLetterOrDigit() || name[index] in "_@#$")) index++
        return index.takeIf { it - start <= MAX_PART_CHARS }
    }
}
