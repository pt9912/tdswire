package io.github.pt9912.tdswire.config.auth

/**
 * Parameter des Verfahrens `pbkdf2-sha256` (`ADR-0011`). Die Iterationszahlen sind Setzungen des
 * Nutzers; Hashlänge und Salzmindestlänge sind Festlegungen ohne Messung (offener Punkt 4 der ADR).
 */
object Pbkdf2Limits {
    const val ITERATIONS_FLOOR = 210_000
    const val ITERATIONS_DEFAULT = 600_000
    const val ITERATIONS_CEILING = 10_000_000

    /** Festlegung ohne Messung: Der Hash hat genau die Ausgabelänge von SHA-256. */
    const val HASH_BYTES = 32

    /** Festlegung ohne Messung: Das Salz hat mindestens 16 Byte. */
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
