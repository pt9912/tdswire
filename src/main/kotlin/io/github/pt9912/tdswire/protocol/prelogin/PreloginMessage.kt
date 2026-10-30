package io.github.pt9912.tdswire.protocol.prelogin

/** Wert der PRELOGIN-Option ENCRYPTION (MS-TDS 2.2.6.5). */
object EncryptionMode {
    const val OFF = 0x00
    const val ON = 0x01
    const val NOT_SUPPORTED = 0x02
    const val REQUIRED = 0x03
}

/** Option-Kennungen der PRELOGIN-Tabelle (MS-TDS 2.2.6.5); `TERMINATOR` beendet die Tabelle. */
object PreloginToken {
    const val VERSION = 0x00
    const val ENCRYPTION = 0x01
    const val INSTOPT = 0x02
    const val THREADID = 0x03
    const val MARS = 0x04
    const val TRACEID = 0x05
    const val FEDAUTHREQUIRED = 0x06
    const val NONCEOPT = 0x07
    const val TERMINATOR = 0xFF
}

/** Eine Option der PRELOGIN-Tabelle mit ihren Rohdaten. */
class PreloginOption(val token: Int, val data: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is PreloginOption && token == other.token && data.contentEquals(other.data)

    override fun hashCode(): Int = token * HASH_FACTOR + data.contentHashCode()

    override fun toString(): String = "PreloginOption(token=$token, dataBytes=${data.size})"

    companion object {
        private const val HASH_FACTOR = 31
        private const val BYTE_MASK = 0xFF
        private const val BYTE_BITS = 8

        /**
         * VERSION: Haupt-, Nebenversion, Build (zwei Bytes Big-Endian), Unter-Build (zwei Bytes).
         * Festlegung ohne Orakel: Die Byteordnung des Unter-Builds (hier Big-Endian) ist nicht gemessen.
         */
        fun version(major: Int, minor: Int, build: Int, subBuild: Int): PreloginOption = PreloginOption(
            PreloginToken.VERSION,
            byteArrayOf(
                major.toByte(),
                minor.toByte(),
                (build ushr BYTE_BITS).toByte(),
                (build and BYTE_MASK).toByte(),
                (subBuild ushr BYTE_BITS).toByte(),
                (subBuild and BYTE_MASK).toByte(),
            ),
        )

        fun encryption(mode: Int): PreloginOption = PreloginOption(PreloginToken.ENCRYPTION, byteArrayOf(mode.toByte()))

        /** INSTOPT: Name der Instanz als nullterminierte Bytefolge; leer ist ein einzelnes Nullbyte. */
        fun instance(name: String): PreloginOption =
            PreloginOption(PreloginToken.INSTOPT, name.toByteArray(Charsets.US_ASCII) + byteArrayOf(0))

        fun mars(enabled: Boolean): PreloginOption =
            PreloginOption(PreloginToken.MARS, byteArrayOf(if (enabled) 1 else 0))

        fun fedAuthRequired(required: Boolean): PreloginOption =
            PreloginOption(PreloginToken.FEDAUTHREQUIRED, byteArrayOf(if (required) 1 else 0))
    }
}

/**
 * Der Inhalt einer PRELOGIN-Nachricht (Anfrage wie Antwort): die Optionen in der Reihenfolge der
 * Tabelle, ohne Terminator. Optionen mit unbekannter Kennung bleiben als Rohdaten erhalten; ob
 * sie angenommen werden, entscheidet das Kompatibilitätsprofil des Aufrufers, nicht der Codec.
 */
class PreloginMessage(val options: List<PreloginOption>) {
    /** Die erste Option mit dieser Kennung. */
    fun option(token: Int): PreloginOption? = options.firstOrNull { it.token == token }

    /** Das Byte der ENCRYPTION-Option, `null`, wenn sie fehlt oder leer ist. */
    val encryption: Int? get() = firstByte(PreloginToken.ENCRYPTION)

    /** Ob das Byte der FEDAUTHREQUIRED-Option ungleich null ist, `null`, wenn sie fehlt oder leer ist. */
    val fedAuthRequired: Boolean? get() = firstByte(PreloginToken.FEDAUTHREQUIRED)?.let { it != 0 }

    private fun firstByte(token: Int): Int? = option(token)?.data?.firstOrNull()?.toInt()?.and(BYTE_MASK)

    private companion object {
        const val BYTE_MASK = 0xFF
    }
}
