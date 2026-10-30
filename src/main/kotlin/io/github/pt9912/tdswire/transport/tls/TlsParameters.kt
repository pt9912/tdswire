package io.github.pt9912.tdswire.transport.tls

/** Wie TLS beginnt: den Pfad bestimmt der Aufrufer, die Schleife entscheidet ihn nicht (`SPEC-028`). */
enum class TlsPath {
    /** TDS 7.4: Handshake in Paketen vom Typ `0x12`, nach `FINISHED` rohes TLS auf dem Strom. */
    ENCAPSULATED_IN_TDS,

    /** TDS 8.0: TLS ab dem ersten Byte des Stroms, ALPN `tds/8.0`. */
    FROM_FIRST_BYTE,
}

/**
 * Protokollversionen und Suiten, die die Engine anbietet (`SPEC-023`). Mindestversion ist TLS 1.2:
 * jede andere Version in [protocols] ist ein Programmierfehler des Aufrufers (`IllegalArgumentException`).
 *
 * Festlegung (Grenze): Geprüft werden nur die Protokolle. [cipherSuites] muss nicht leer sein; ob die
 * Suiten zu `SPEC-023` gehören, prüft die Klasse nicht. Eine Suite außerhalb davon (etwa CBC) geht
 * unverändert an die Engine, und die Auswahl der Suiten gehört dem Aufrufer; [SPEC_023] ist die
 * Auswahl, die die Spezifikation nennt.
 */
data class TlsParameters(val protocols: List<String>, val cipherSuites: List<String>) {
    init {
        require(protocols.isNotEmpty() && protocols.all { it in ALLOWED_PROTOCOLS }) {
            "protocols nur TLSv1.2 und TLSv1.3"
        }
        require(cipherSuites.isNotEmpty()) { "cipherSuites darf nicht leer sein" }
    }

    companion object {
        val ALLOWED_PROTOCOLS = listOf("TLSv1.3", "TLSv1.2")

        /** Die Suiten von `SPEC-023` für TLS 1.3. */
        val TLS13_SUITES = listOf("TLS_AES_256_GCM_SHA384", "TLS_AES_128_GCM_SHA256")

        /** Die Suiten von `SPEC-023` für TLS 1.2. */
        val TLS12_SUITES = listOf(
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
        )

        /** Beide Versionen mit allen Suiten von `SPEC-023`. */
        val SPEC_023 = TlsParameters(ALLOWED_PROTOCOLS, TLS13_SUITES + TLS12_SUITES)
    }
}
