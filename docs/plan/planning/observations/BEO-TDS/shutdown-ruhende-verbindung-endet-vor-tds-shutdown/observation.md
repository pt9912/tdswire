# `SPEC-044` Nr. 2 und Nr. 3 treffen eine ruhende Verbindung gegensätzlich

**Sub-Area:** `*` (Spezifikation, Listener)

Nr. 2 beendet eine Verbindung ohne laufende Anfrage und ohne unvollständige Nachricht beim Beginn sofort; Nr. 3 verspricht `TDS_SHUTDOWN` für eine nach dem Beginn eingetroffene Anfrage. Gebaut ist Nr. 2 vor Nr. 3 gelesen: ein sequentieller Client sieht nach dem Beginn nur ein Schließen, `TDS_SHUTDOWN` erreicht nur eine Verbindung mit laufender Anfrage oder halber Nachricht. Dasselbe gilt für den Handshake, zu dem die Spezifikation schweigt (gebaut: endet sofort). Die Zusage von Nr. 3 ist unverändert; `SPEC-044` führt die Frage als offene Frage.
