package io.github.pt9912.tdswire.runtime

import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import io.github.pt9912.tdswire.simulation.RowSource
import io.github.pt9912.tdswire.simulation.SimCatalog
import io.github.pt9912.tdswire.simulation.SimColumn
import io.github.pt9912.tdswire.simulation.SimTable
import io.github.pt9912.tdswire.simulation.SimulationBackend
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Zeilen von `viele`; der .NET-Test (`referenztest-dotnet/Kompatibilitaet`) liest davon nur den Anfang. */
internal const val MANY_ROWS = 300_000

/** Zeichen Text je Zeile von `viele`. */
internal const val MANY_TEXT_CHARS = 128

/** Zeilen von `kaputt`, bevor die Quelle fehlschlägt; wie im .NET-Test festgehalten. */
internal const val KOMPAT_BROKEN_AFTER = 3

/** Zeilenzahlen, die die Callbacks von `person` melden; der .NET-Test erwartet dieselben Zahlen. */
internal const val INSERTED_ROWS = 1L
internal const val UPDATED_ROWS = 2L
internal const val DELETED_ROWS = 3L

/** Zeichen der Sitzungskennung in der Logzeile; die Kennung ist je Anmeldung zufällig. */
private const val SESSION_CHARS = 8

private val KEY = SimColumn("Id", SqlType.Int32, nullable = false)

/**
 * Die Tabellen des Kompatibilitätslaufs (`make kompatibilitaet`): `person` mit drei Zeilen und Callbacks für
 * Einfügen, Ändern und Löschen, `typen` mit je einer Spalte der neun Typen des Mindestkatalogs und zwei Zeilen
 * (eine gefüllt, eine mit NULL), `viele` mit [MANY_ROWS] Zeilen für den Verbindungsabbruch mitten im Ergebnis und
 * `kaputt`, dessen Quelle nach [KOMPAT_BROKEN_AFTER] Zeilen mit einer Ausnahme endet.
 */
internal object KompatibilitaetsTables {
    fun all(): List<SimTable> = listOf(person(), types(), many(), broken())

    private fun person() = SimTable(
        name = "person",
        columns = listOf(
            KEY,
            SimColumn("Name", SqlType.UnicodeText),
            SimColumn("Age", SqlType.Int32),
            SimColumn("Score", SqlType.Decimal(10, 2)),
        ),
        primaryKey = listOf("Id"),
        rows = {
            val rows = listOf(
                listOf(1, "Ada", 36, BigDecimal("10.50")),
                listOf(2, "Bob", null, BigDecimal("7.25")),
                listOf(3, "Cy", 36, null),
            )
            var next = 0
            RowSource { rows.getOrNull(next++) }
        },
        onInsert = { INSERTED_ROWS },
        onUpdate = { _, _ -> UPDATED_ROWS },
        onDelete = { DELETED_ROWS },
    )

    private fun types() = SimTable(
        name = "typen",
        columns = listOf(
            KEY,
            SimColumn("Gross", SqlType.Int64),
            SimColumn("Flag", SqlType.Bool),
            SimColumn("Text", SqlType.UnicodeText),
            SimColumn("Preis", SqlType.Decimal(10, 2)),
            SimColumn("Tag", SqlType.Date),
            SimColumn("Uhr", SqlType.Time),
            SimColumn("Zeit", SqlType.DateTime),
        ),
        primaryKey = listOf("Id"),
        rows = {
            val rows = listOf(
                listOf(
                    1,
                    9_000_000_000L,
                    true,
                    "ü€😀",
                    BigDecimal("12345678.90"),
                    LocalDate.of(2026, 10, 31),
                    LocalTime.of(13, 45, 59, 123_000_000),
                    LocalDateTime.of(2026, 10, 31, 13, 45, 59, 123_000_000),
                ),
                listOf(2, null, null, null, null, null, null, null),
            )
            var next = 0
            RowSource { rows.getOrNull(next++) }
        },
    )

    private fun many() = SimTable(
        name = "viele",
        columns = listOf(KEY, SimColumn("Text", SqlType.UnicodeText)),
        primaryKey = listOf("Id"),
        rows = {
            var next = 0
            RowSource { if (next < MANY_ROWS) listOf(++next, String(CharArray(MANY_TEXT_CHARS) { 'x' })) else null }
        },
    )

    private fun broken() = SimTable(
        name = "kaputt",
        columns = listOf(KEY, SimColumn("Text", SqlType.UnicodeText)),
        primaryKey = listOf("Id"),
        rows = {
            var next = 0
            RowSource {
                if (next < KOMPAT_BROKEN_AFTER) listOf(++next, "zeile") else error("Geheimnis der Quelle")
            }
        },
    )
}

/**
 * Reicht jede Anfrage an [inner] weiter und schreibt vorher eine Zeile `kompatibilitaet: anfrage` mit den ersten
 * Zeichen der Sitzungskennung, dem Text und den Parametern (Name, Typ, Richtung) auf die Standardausgabe: der Lauf
 * liest daran, welchen Text der Referenzclient stellt und auf welcher Sitzung er läuft. Der Wert eines Parameters
 * steht nicht in der Zeile. Nur der Testserver schreibt sie; das Produkt schreibt keinen Text einer Anfrage.
 */
internal class RecordingBackend(private val inner: QueryBackend, private val out: (String) -> Unit = ::println) :
    QueryBackend {
    override suspend fun execute(request: QueryRequest): QueryExecution {
        val parameters = request.parameters.joinToString(",") { "${it.name}:${it.type}:${it.direction}" }
        val session = request.sessionId.take(SESSION_CHARS)
        val sql = request.sql.replace("\n", "\\n")
        out("kompatibilitaet: anfrage session=$session sql=[$sql] parameter=[$parameters]")
        return inner.execute(request)
    }

    override suspend fun cancel(sessionId: String, requestId: String) = inner.cancel(sessionId, requestId)
}

/**
 * Der Serverprozess des Kompatibilitätslaufs (Dockerfile-Stage `kompatibilitaetsserver`): `Main` mit den Tabellen
 * von [KompatibilitaetsTables] hinter [RecordingBackend]. Die Konfiguration kommt aus der Umgebung der Stage
 * (Richtlinie `REQUIRED` mit Schlüsselspeicher).
 */
fun main() {
    val backend = RecordingBackend(SimulationBackend(SimCatalog(KompatibilitaetsTables.all())))
    println("kompatibilitaetsserver: Tabellen person, typen, viele ($MANY_ROWS Zeilen), kaputt")
    runServer(Wiring(log = ServerLog.STDOUT, requests = RequestSource(backend = backend)))
}
