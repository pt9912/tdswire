package io.github.pt9912.tdswire.domain

/**
 * Die angemeldete Identität, die ein Authentifizierungsadapter bei Annahme liefert und die in die
 * Session geht (`SPEC-051`). Sie trägt den Benutzernamen; die Textdarstellung nennt ihn nicht
 * (`TDS-QA-03`), wer ihn braucht, liest [name].
 */
class Principal(val name: String) {
    override fun equals(other: Any?): Boolean = other is Principal && other.name == name

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String = "Principal(verborgen)"
}
