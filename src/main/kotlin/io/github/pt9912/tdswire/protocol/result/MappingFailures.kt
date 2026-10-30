package io.github.pt9912.tdswire.protocol.result

import io.github.pt9912.tdswire.protocol.wire.ProtocolError
import io.github.pt9912.tdswire.protocol.wire.ProtocolFailure
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult

/**
 * Ein Abbildungsfehler. Alle Gründe, die die Abbildung nutzt, tragen die Kategorie
 * `TDS_UNSUPPORTED_TYPE` (`SPEC-014`); [detail] nennt Typen, Längen und Spaltenpositionen, nie einen
 * Wert der Zeile.
 */
internal fun failed(failure: ProtocolFailure, detail: String): ProtocolResult.Failed =
    ProtocolResult.Failed(ProtocolError(failure, detail))

/** Der Wert hat nicht die JVM-Klasse, die der Typ der Spalte verlangt; genannt wird nur die Klasse. */
internal fun mismatch(expected: String, actual: Any): ProtocolResult.Failed =
    failed(ProtocolFailure.TYPE_MISMATCH, "erwartet $expected, erhalten ${actual::class.simpleName ?: "anonym"}")
