# Ein Bulk-Load-Paket (Typ 0x07) erscheint im Span `tds.query` als `sql_batch`

**Sub-Area:** `*` (Bibliothek, öffentliche Fläche, Telemetrie)

`tds.request_kind` kennt zwei Werte, `sql_batch` und `rpc`. Das Paket Bulk-Load (`SPEC-036`) zählt als `sql_batch`; ein Auswertender kann es von einem SQL_BATCH nicht unterscheiden. Die Setzung ist die kleinste Menge von Werten, nicht gemessen und nicht mit einem Nutzer abgestimmt. Ein dritter Wert `bulk_load` änderte keinen Typnamen (`requestKind` ist ein `String?`), aber die Menge der öffentlichen Marken.

Optionen: (a) Setzung `sql_batch` (Stand); (b) dritter Wert `bulk_load` (Enum `RequestKind`, Spezifikation Abschnitt 5, Tests).
