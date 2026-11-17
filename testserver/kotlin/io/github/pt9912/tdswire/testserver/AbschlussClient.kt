package io.github.pt9912.tdswire.testserver

import kotlin.system.exitProcess

/**
 * Präfix der Zeilen des Abschluss-Clients; die Werkzeuge
 * `tools/heap-abschluss.sh` und `tools/heap-abschluss-tabellen.sh` lesen sie.
 */
internal const val ABSCHLUSS_PREFIX = "abschluss:"

private const val TOKEN_COLMETADATA = 0x81
private const val TOKEN_ROW = 0xD1
private const val TOKEN_DONE = 0xFD
private const val TYPE_INTN = 0x26
private const val TYPE_INT8 = 0x7F
private const val TYPE_INT4 = 0x38
private const val INT8_BYTES = 8
private const val INT4_BYTES = 4
private const val BYTE_MASK = 0xFF
private const val BYTE_BITS = 8
private const val HEX_RADIX = 16
private const val COLUMN_FIXED_BYTES = 7
private const val DONE_BYTES = 13
private const val COUNT_BYTES = 2
private const val USERTYPE_AND_FLAG_BYTES = 6
private const val DONE_STATUS_AT = 1
private const val DONE_COMMAND_AT = 3
private const val DONE_ROWS_AT = 5

/** Das Bit `DONE_MORE` des Status eines DONE (MS-TDS 2.2.7.6): nach diesem Abschluss folgt ein weiteres Ergebnis. */
internal const val DONE_MORE = 0x0001
private const val NAME_UNIT_BYTES = 2
private const val DEFAULT_PACKET = 4096
private const val DEFAULT_ROWS = 1000L
private const val DEFAULT_REPEATS = 5
private const val DEFAULT_DELAYS = "0,10,100,1000"
private const val DEFAULT_FORMS = "ende,folge"
private const val READ_MILLIS = 60_000
private const val EXIT_FAILED = 1
private const val STAMP_COLUMNS = 3

/** Ein DONE einer Antwort: Status, Befehlskennung, Zeilenzahl, Wanduhr des vollständigen Pakets und dessen Nummer. */
internal data class AbschlussDone(val status: Int, val command: Int, val rows: Long, val micros: Long, val packet: Int)

/** Die Zeile eines Ergebnisses, die als letzte gelesen wurde: Werte (nur Ganzzahlen), Wanduhr und Paketnummer. */
internal data class AbschlussZeile(val values: List<Long>, val micros: Long, val packet: Int)

/**
 * Liest die Tokens der Antwort auf einen SQL_BATCH aus den Paketen, die [feed] nacheinander bekommt: COLMETADATA mit
 * Spalten der Typen `INT8`, `INT4` und `INTN` (MS-TDS 2.2.7.4), ROW und DONE (2.2.7.6); jedes andere Token ist ein
 * Fehler.
 * Ein Token zählt mit der Wanduhr und der Nummer des Pakets, in dem sein letztes Byte ankam: ein Token, das ein Paket
 * überschreitet, gilt erst mit dem folgenden Paket als gelesen. Nicht threadsicher.
 */
internal class AbschlussAntwort {
    /** Zeilen je Ergebnis, in der Reihenfolge der Ergebnisse. */
    val rowsPerResult = ArrayList<Int>()

    /** Die erste und die letzte Zeile je Ergebnis. */
    val firstRows = ArrayList<AbschlussZeile>()
    val lastRows = ArrayList<AbschlussZeile>()
    val dones = ArrayList<AbschlussDone>()

    /** Zahl der Pakete, die [feed] bekommen hat, und die Nutzdatenlänge des ersten. */
    var packets = 0
        private set
    var firstPacketBytes = 0
        private set

    private var buffer = ByteArray(0)
    private var columnSizes: List<Int> = emptyList()

    /**
     * Nimmt die Nutzdaten des nächsten Pakets, empfangen zur Wanduhr [micros], und liest alle Tokens, die nun
     * vollständig sind.
     */
    fun feed(payload: ByteArray, micros: Long) {
        packets++
        if (packets == 1) firstPacketBytes = payload.size
        buffer += payload
        while (buffer.isNotEmpty()) {
            val used = token(micros)
            if (used == 0) break
            buffer = buffer.copyOfRange(used, buffer.size)
        }
    }

    /** `true`, wenn kein Byte unverarbeitet im Puffer liegt. */
    fun complete(): Boolean = buffer.isEmpty()

    private fun need(end: Int) {
        if (buffer.size < end) throw NotEnough()
    }

    private fun u8(at: Int): Int {
        need(at + 1)
        return buffer[at].toInt() and BYTE_MASK
    }

    private fun u16(at: Int): Int = u8(at) or (u8(at + 1) shl BYTE_BITS)

    private fun le(at: Int, count: Int): Long {
        var value = 0L
        for (index in count - 1 downTo 0) value = (value shl BYTE_BITS) or u8(at + index).toLong()
        return value
    }

    /** Die Länge des Tokens am Anfang des Puffers, wenn es vollständig ist, sonst 0. */
    private fun token(micros: Long): Int = try {
        when (val kind = u8(0)) {
            TOKEN_COLMETADATA -> metadata()
            TOKEN_ROW -> row(micros)
            TOKEN_DONE -> done(micros)
            else -> error("Token 0x${kind.toString(HEX_RADIX)} nicht gelesen")
        }
    } catch (_: NotEnough) {
        0
    }

    private fun metadata(): Int {
        val count = u16(1)
        var at = 1 + COUNT_BYTES
        val sizes = ArrayList<Int>()
        repeat(count) {
            val type = u8(at + USERTYPE_AND_FLAG_BYTES)
            at += COLUMN_FIXED_BYTES
            when (type) {
                TYPE_INT8 -> sizes += INT8_BYTES

                TYPE_INT4 -> sizes += INT4_BYTES

                TYPE_INTN -> {
                    u8(at)
                    sizes += 0
                    at += 1
                }

                else -> error("Spaltentyp 0x${type.toString(HEX_RADIX)} nicht gelesen")
            }
            at += 1 + NAME_UNIT_BYTES * u8(at)
            need(at)
        }
        columnSizes = sizes
        rowsPerResult += 0
        return at
    }

    private fun row(micros: Long): Int {
        var at = 1
        val values = ArrayList<Long>()
        for (size in columnSizes) {
            val length = if (size > 0) size else u8(at++)
            need(at + length)
            values += if (length == 0) 0L else le(at, length)
            at += length
        }
        val zeile = AbschlussZeile(values, micros, packets)
        val result = rowsPerResult.size - 1
        if (rowsPerResult[result] == 0) firstRows += zeile
        rowsPerResult[result] = rowsPerResult[result] + 1
        if (lastRows.size < rowsPerResult.size) lastRows += zeile else lastRows[result] = zeile
        return at
    }

    private fun done(micros: Long): Int {
        need(DONE_BYTES)
        dones += AbschlussDone(
            status = u16(DONE_STATUS_AT),
            command = u16(DONE_COMMAND_AT),
            rows = le(DONE_ROWS_AT, INT8_BYTES),
            micros = micros,
            packet = packets,
        )
        return DONE_BYTES
    }
}

/** Die Antwort endet mitten in einem Token: der Rest kommt mit dem nächsten Paket. Ohne Stapelspur, kein Fehler. */
private class NotEnough : RuntimeException(null, null, false, false)

/**
 * Eine Messung des zurückgehaltenen Abschlusses (alle Zeiten in Mikrosekunden): [zeileBisDoneClient] ist der Abstand
 * zwischen dem Empfang der letzten Zeile des ersten Ergebnisses und dem seines DONE am Client (Definition der Messung);
 * die übrigen Zeiten setzen die Stempel des Backends ein ([AbschlussStempel], gleiche Wanduhr im selben Kernel).
 */
internal data class AbschlussMessung(
    val form: AbschlussForm,
    val delayMillis: Long,
    val rows: Long,
    val results: Int,
    val moreBit: Boolean,
    val packets: Int,
    val packetBytes: Int,
    val samePacket: Boolean,
    val firstRowUs: Long,
    val zeileBisDoneClient: Long,
    val zeileEmitBisZeile: Long,
    val zeileEmitBisDone: Long,
    val naechstesBisDone: Long,
    val requestBisDone: Long,
) {
    /** Die Rohzeile `abschluss: lauf …` dieser Messung als Wiederholung [repeat] (0 ist der Warmlauf). */
    fun line(repeat: Int): String =
        "$ABSCHLUSS_PREFIX lauf form ${form.schluessel} verzoegerung_ms $delayMillis wdh $repeat zeilen $rows " +
            "ergebnisse $results more_bit $moreBit pakete $packets paket_nutzbytes $packetBytes " +
            "zeile_und_done_im_selben_paket $samePacket erste_zeile_us $firstRowUs " +
            "zeile_bis_done_client_us $zeileBisDoneClient zeile_emit_bis_zeile_gesehen_us $zeileEmitBisZeile " +
            "zeile_emit_bis_done_gesehen_us $zeileEmitBisDone naechstes_bis_done_gesehen_us $naechstesBisDone " +
            "anfrage_bis_done_us $requestBisDone"
}

/**
 * Misst einen Lauf: sendet `SELECT * FROM abschluss` mit [rows] Zeilen, der [form] und der Verzögerung [delayMillis],
 * liest die Antwort mit Wanduhr je Paket und danach die Stempel des Backends (`SELECT * FROM abschluss_stand`). Prüft,
 * was die Messung voraussetzt (Zeilenzahl, Zahl der Ergebnisse, `DONE_MORE` genau am ersten Abschluss der Form
 * `folge`, letztes DONE ohne `DONE_MORE`) und wirft sonst.
 */
internal fun messeAbschluss(
    client: RohbytesClient,
    form: AbschlussForm,
    delayMillis: Long,
    rows: Long,
): AbschlussMessung {
    val answer = AbschlussAntwort()
    val sent = client.batch(
        "SELECT * FROM $ABSCHLUSS_TABLE /*zeilen=$rows,form=${form.schluessel},verzoegerung=$delayMillis*/",
        answer::feed,
    )
    val expectedResults = if (form == AbschlussForm.FOLGE) 2 else 1
    check(answer.complete()) { "Antwort endet mitten in einem Token" }
    check(answer.rowsPerResult.size == expectedResults && answer.dones.size == expectedResults) {
        "Form ${form.schluessel}: ${answer.rowsPerResult.size} Ergebnisse und ${answer.dones.size} DONE " +
            "statt $expectedResults"
    }
    check(answer.rowsPerResult[0].toLong() == rows) { "${answer.rowsPerResult[0]} Zeilen statt $rows" }
    val more = answer.dones.first().status and DONE_MORE != 0
    check(more == (form == AbschlussForm.FOLGE)) { "DONE_MORE am ersten Abschluss: $more bei Form ${form.schluessel}" }
    check(answer.dones.last().status and DONE_MORE == 0) { "das letzte DONE trägt DONE_MORE" }
    val stamps = AbschlussAntwort()
    client.batch("SELECT * FROM $ABSCHLUSS_STAND_TABLE", stamps::feed)
    val values = stamps.lastRows.single().values
    check(values.size == STAMP_COLUMNS) { "Stempel: ${values.size} Spalten" }
    val (zeileEmit, _, naechstes) = values
    val lastRow = answer.lastRows[0]
    val done = answer.dones[0]
    return AbschlussMessung(
        form = form,
        delayMillis = delayMillis,
        rows = rows,
        results = expectedResults,
        moreBit = more,
        packets = answer.packets,
        packetBytes = answer.firstPacketBytes,
        samePacket = lastRow.packet == done.packet,
        firstRowUs = answer.firstRows[0].micros - sent,
        zeileBisDoneClient = done.micros - lastRow.micros,
        zeileEmitBisZeile = lastRow.micros - zeileEmit,
        zeileEmitBisDone = done.micros - zeileEmit,
        naechstesBisDone = done.micros - naechstes,
        requestBisDone = done.micros - sent,
    )
}

/** Alle Verzögerungen einer Form: je Wert der Warmlauf (`wdh 0`) und [repeats] Wiederholungen, eine Zeile je Lauf. */
private fun messeFolge(client: RohbytesClient, form: AbschlussForm, delays: List<Long>, rows: Long, repeats: Int) {
    for (delay in delays) {
        for (repeat in 0..repeats) println(messeAbschluss(client, form, delay, rows).line(repeat))
    }
}

/**
 * Der Messclient der Abschluss-Messung (`tools/heap-abschluss.sh`, Image der Stage `lasttestserver`, diese Hauptklasse,
 * ein eigener Container neben dem Server). Umgebung: `ABSCHLUSS_ZIEL` (`host:port`), `ABSCHLUSS_VERZOEGERUNG_MS`
 * (Vorgabe `0,10,100,1000`), `ABSCHLUSS_FORMEN` (Vorgabe `ende,folge`), `ABSCHLUSS_ZEILEN` (Vorgabe 1000),
 * `ABSCHLUSS_WDH` (Vorgabe 5 Wiederholungen nach einem Warmlauf je Form und Verzögerung; der Warmlauf steht als
 * `wdh 0`). Je Lauf eine Zeile `abschluss: lauf …` ([AbschlussMessung.line]); Exit 1 bei jeder verletzten
 * Voraussetzung.
 */
fun main() {
    val environment = System.getenv()
    val target = environment["ABSCHLUSS_ZIEL"] ?: error("ABSCHLUSS_ZIEL fehlt (host:port)")
    val delays = (environment["ABSCHLUSS_VERZOEGERUNG_MS"] ?: DEFAULT_DELAYS).split(',').map { it.trim().toLong() }
    val forms = (environment["ABSCHLUSS_FORMEN"] ?: DEFAULT_FORMS).split(',').map { AbschlussForm.of(it.trim()) }
    val rows = environment["ABSCHLUSS_ZEILEN"]?.toLong() ?: DEFAULT_ROWS
    val repeats = environment["ABSCHLUSS_WDH"]?.toInt() ?: DEFAULT_REPEATS
    println("$ABSCHLUSS_PREFIX kopf ziel=$target zeilen=$rows wdh=$repeats packet_size=$DEFAULT_PACKET")
    try {
        val host = target.substringBeforeLast(':')
        val port = target.substringAfterLast(':').toInt()
        RohbytesClient(host, port, DEFAULT_PACKET, READ_MILLIS).use { client ->
            check(client.login("alice", "secret")) { "Anmeldung abgelehnt" }
            for (form in forms) messeFolge(client, form, delays, rows, repeats)
        }
    } catch (e: IllegalStateException) {
        println("$ABSCHLUSS_PREFIX FEHLER ${e.message}")
        exitProcess(EXIT_FAILED)
    }
}
