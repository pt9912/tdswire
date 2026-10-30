package io.github.pt9912.tdswire.transport.tls

import io.github.pt9912.tdswire.transport.channel.ChannelError
import io.github.pt9912.tdswire.transport.channel.ChannelFailure

/** Fehlerkategorie nach `SPEC-011` und `SPEC-048`. */
enum class TlsErrorCategory {
    TDS_PROTOCOL_ERROR,
    TDS_INTERNAL_ERROR,
}

/** Grund, aus dem die TLS-Schleife einen Handshake oder eine Datenoperation beendet. */
enum class TlsFailure(val category: TlsErrorCategory) {
    /** Die Engine bricht den Handshake ab: Zertifikat, Protokollversion, Suite, Alert der Gegenstelle. */
    HANDSHAKE_FAILED(TlsErrorCategory.TDS_PROTOCOL_ERROR),

    /** Die Gegenstelle bietet im TDS-8.0-Pfad nicht das ALPN `tds/8.0` an oder wählt ein anderes. */
    ALPN_MISMATCH(TlsErrorCategory.TDS_PROTOCOL_ERROR),

    /** Die Bytes der Gegenstelle verletzen die Kapselung: Header, Pakettyp oder Anwendungsdaten im Handshake. */
    PROTOCOL_VIOLATION(TlsErrorCategory.TDS_PROTOCOL_ERROR),

    /** Ein TLS-Record ist nach dem Handshake ungültig (Prüfsumme, Länge, Typ). */
    RECORD_INVALID(TlsErrorCategory.TDS_PROTOCOL_ERROR),

    /** Der Kanal endet vor `close_notify` oder meldet einen E/A-Fehler. */
    CONNECTION_ABORTED(TlsErrorCategory.TDS_INTERNAL_ERROR),

    /** Der Kanal ist lokal geschlossen. */
    CLOSED(TlsErrorCategory.TDS_INTERNAL_ERROR),
}

/**
 * Ergebnisfehler der TLS-Schleife. [detail] nennt einen Ausnahmetyp oder eine feste Beschreibung,
 * nie den Text einer Ausnahme, nie Zertifikats- oder Schlüsselinhalt und nie Nutzdaten.
 * Festlegung: die Kategorie je Grund steht in [TlsFailure]; sie folgt keiner Zeile der Spezifikation.
 */
data class TlsError(val failure: TlsFailure, val detail: String) {
    val category: TlsErrorCategory get() = failure.category

    /** Der Fehler in der Form der Kanal-Schnittstelle: geschlossen oder E/A-Fehler, Grund und Detail im Text. */
    fun toChannelError(): ChannelError {
        val reason = if (failure == TlsFailure.CLOSED) ChannelFailure.CLOSED else ChannelFailure.IO_FAILURE
        return ChannelError(reason, "$failure:$detail")
    }
}

/** Ausgang einer TLS-Operation: ein Wert oder ein [TlsError], nie eine Ausnahme der Gegenstelle. */
sealed interface TlsResult<out T> {
    data class Ok<out T>(val value: T) : TlsResult<T>

    data class Failed(val error: TlsError) : TlsResult<Nothing>
}

internal fun failed(failure: TlsFailure, detail: String): TlsResult.Failed = TlsResult.Failed(TlsError(failure, detail))
