**Vorgang:** slice-rpc-prepared-handles
**Fund:** Die Antwort auf `sp_unprepare` (Fehler 50002, das Handle bleibt gültig) und das Verhalten der Handle-Tabelle bei `sp_reset_connection` sind gegen das Test-Backend gesetzt, nicht unter Pooling gemessen; Handles wachsen ohne `sp_unprepare` bis zur Grenze von 1000. Träger bleibt `slice-qa-kompatibilitaet`.
