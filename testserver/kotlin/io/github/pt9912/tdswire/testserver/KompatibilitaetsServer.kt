package io.github.pt9912.tdswire.testserver

import io.github.pt9912.tdswire.domain.QueryRequest
import io.github.pt9912.tdswire.domain.SqlType
import io.github.pt9912.tdswire.port.QueryBackend
import io.github.pt9912.tdswire.port.QueryExecution
import io.github.pt9912.tdswire.runtime.BackendSource
import io.github.pt9912.tdswire.simulation.RowSource
import io.github.pt9912.tdswire.simulation.SimCatalog
import io.github.pt9912.tdswire.simulation.SimColumn
import io.github.pt9912.tdswire.simulation.SimTable
import io.github.pt9912.tdswire.simulation.SimulationBackend
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Zeilen von `kaputt`, bevor die Quelle fehlschlägt; wie im .NET-Test festgehalten. */
internal const val KOMPAT_BROKEN_AFTER = 3

/** Zeilen und Zeichen Text je Zeile von `breit`: Zeilen von rund 1200 Bytes, größer als die Paketgröße 512. */
internal const val WIDE_ROWS = 10
internal const val WIDE_TEXT_CHARS = 600

/** Zeilenzahlen, die die Callbacks von `person` melden; der .NET-Test erwartet dieselben Zahlen. */
internal const val INSERTED_ROWS = 1L
internal const val UPDATED_ROWS = 2L
internal const val DELETED_ROWS = 3L

/** Zeichen der Sitzungskennung in der Logzeile; die Kennung ist je Anmeldung zufällig. */
private const val SESSION_CHARS = 8

/** Alter von `Ada` und `Cy`; gleich, damit die Auswahl nach `Age` zwei Zeilen trifft. */
private const val ADA_AGE = 36

/** Stellen der Dezimalspalten `Score` und `Preis`. */
private const val MONEY_PRECISION = 10

/** Ein Wert von `Gross` außerhalb von `Int`. */
private const val BIG_VALUE = 9_000_000_000L

private val KEY = SimColumn("Id", SqlType.Int32, nullable = false)

/**
 * Die Tabellen des Kompatibilitätslaufs (`make kompatibilitaet`): `person` mit drei Zeilen und Callbacks für
 * Einfügen, Ändern und Löschen, `typen` mit je einer Spalte der acht Typen von `SqlType` und zwei Zeilen (eine
 * gefüllt, eine mit NULL), `breit` mit [WIDE_ROWS] Zeilen zu [WIDE_TEXT_CHARS] Zeichen (Zeilen über der
 * Paketgröße 512; die Wirkung auf die Paketierung der Antworten ist nicht beobachtet, ein Server mit fester
 * Paketgröße ließ den Test grün) und `kaputt`, dessen Quelle nach [KOMPAT_BROKEN_AFTER] Zeilen mit einer Ausnahme
 * endet.
 */
internal object KompatibilitaetsTables {
    fun all(): List<SimTable> = listOf(person(), types(), wide(), broken())

    private fun person() = SimTable(
        name = "person",
        columns = listOf(
            KEY,
            SimColumn("Name", SqlType.UnicodeText),
            SimColumn("Age", SqlType.Int32),
            SimColumn("Score", SqlType.Decimal(MONEY_PRECISION, 2)),
        ),
        primaryKey = listOf("Id"),
        rows = {
            val rows = listOf(
                listOf(1, "Ada", ADA_AGE, BigDecimal("10.50")),
                listOf(2, "Bob", null, BigDecimal("7.25")),
                listOf(3, "Cy", ADA_AGE, null),
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
            SimColumn("Preis", SqlType.Decimal(MONEY_PRECISION, 2)),
            SimColumn("Tag", SqlType.Date),
            SimColumn("Uhr", SqlType.Time),
            SimColumn("Zeit", SqlType.DateTime),
        ),
        primaryKey = listOf("Id"),
        rows = {
            val rows = listOf(
                listOf(
                    1,
                    BIG_VALUE,
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

    private fun wide() = SimTable(
        name = "breit",
        columns = listOf(KEY, SimColumn("Text", SqlType.UnicodeText)),
        primaryKey = listOf("Id"),
        rows = {
            var next = 0
            RowSource { if (next < WIDE_ROWS) listOf(++next, String(CharArray(WIDE_TEXT_CHARS) { 'y' })) else null }
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
 * steht nicht in der Zeile. Nur der Testserver schreibt sie; die Bibliothek schreibt keinen Text einer Anfrage.
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
 * Der Testserver des Kompatibilitätslaufs (Dockerfile-Stage `kompatibilitaetsserver`): [runTestserver] mit den
 * Tabellen von [KompatibilitaetsTables] hinter [RecordingBackend], übergeben als `BackendSource.Custom`. Die
 * Konfiguration kommt aus der Umgebung der Stage (Richtlinie `REQUIRED` mit Schlüsselspeicher); eine Datei der
 * Beschreibung bricht den Start ab.
 */
fun main() {
    val backend = RecordingBackend(SimulationBackend(SimCatalog(KompatibilitaetsTables.all())))
    println("kompatibilitaetsserver: Tabellen person, typen, breit, kaputt")
    runTestserver { fromFile -> BackendSource.Custom(backend).takeIf { fromFile.isEmpty() } }
}
