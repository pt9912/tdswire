package io.github.pt9912.tdswire.config.auth

/**
 * Parameter des Verfahrens `pbkdf2-sha256` (`ADR-0011`). Die Iterationszahlen sind Setzungen des
 * Nutzers; Hashlänge und Salzmindestlänge (ohne Obergrenze) sind vom Nutzer entschieden (2026-10-30), eine
 * Setzung, nicht gemessen.
 */
object Pbkdf2Limits {
    const val ITERATIONS_FLOOR = 210_000

    /** Wert der Konfiguration (`ADR-0011`); im Code liest ihn noch niemand. */
    const val ITERATIONS_DEFAULT = 600_000
    const val ITERATIONS_CEILING = 10_000_000

    /** Entschieden (Nutzer, 2026-10-30), nicht gemessen: Der Hash hat genau die Ausgabelänge von SHA-256. */
    const val HASH_BYTES = 32

    /** Entschieden (Nutzer, 2026-10-30), nicht gemessen: Das Salz hat mindestens 16 Byte, ohne Obergrenze. */
    const val MIN_SALT_BYTES = 16
}

/** Der geparste Prüfwert eines Eintrags. Die Textdarstellung nennt keinen Wert. */
internal sealed interface StoredSecret {
    /** Klartext als UTF-8-Bytes; nur im Testmodus zulässig. */
    class Plain(val utf8: ByteArray) : StoredSecret {
        override fun toString(): String = "Plain(verborgen)"
    }

    class Pbkdf2(val salt: ByteArray, val iterations: Int, val hash: ByteArray) : StoredSecret {
        override fun toString(): String = "Pbkdf2(verborgen)"
    }
}
