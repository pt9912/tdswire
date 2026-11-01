package io.github.pt9912.tdswire.config

import io.github.pt9912.tdswire.config.auth.UserListError

/** Die Art eines Konfigurationsfehlers; der Wert des Schlüssels erscheint nie in der Diagnose. */
enum class IssueKind {
    /** Eine Pflichtoption fehlt. */
    MISSING,

    /** Der Wert hat nicht die Form des Schlüssels. */
    INVALID_VALUE,

    /** Der Wert hat die Form, liegt aber außerhalb des Bereichs. */
    OUT_OF_RANGE,

    /** Der Schlüssel ist in der gewählten Betriebsart nicht erlaubt. */
    CONFLICT,

    /** Die genannte Datei ist nicht lesbar. */
    UNREADABLE,

    /** Die Datei ist kein gültiges UTF-8. */
    NOT_UTF8,

    /** Die Datei beginnt mit einem UTF-8-BOM. */
    BYTE_ORDER_MARK,

    /** Die Datei ist größer als erlaubt. */
    TOO_LARGE,

    /** Die Benutzerliste ist ungültig; [ConfigIssue.detail] trägt Fehlerart und Zeilennummer. */
    USER_LIST_INVALID,
}

/**
 * Ein Konfigurationsfehler: Schlüssel und Art, bei der Benutzerliste Fehlerart und Zeile in [detail]. Er
 * trägt nie einen Wert, einen Dateipfad, einen Prüfwert oder ein Passwort (`TDS-QA-03`). [detail] hat den Typ
 * [UserListError], eine Aufzählung und eine Zeilennummer: ein Aufrufer kann keinen freien Text einsetzen.
 */
data class ConfigIssue(val key: String, val kind: IssueKind, val detail: UserListError? = null) {
    val message: String get() = if (detail == null) "$key: $kind" else "$key: $kind (${describe(detail)})"

    private fun describe(error: UserListError): String = "${error.kind}${error.line?.let { ", Zeile $it" }.orEmpty()}"
}
