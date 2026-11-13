# Ob die Adresse der Gegenstelle im Ereignis erscheint, hat keinen Träger

**Sub-Area:** `*` (Telemetrie, Anmeldedrosselung)

[ADR-0020](../../../../adr/0020-logformat-und-observability-schnittstelle.md) nennt unter „Offen“ (e) die Quelladresse der Gegenstelle im Ereignis und setzt als Träger „dieselbe Entscheidung zur Anmeldedrosselung“. [ADR-0016](../../../../adr/0016-anmeldedrosselung.md) führt „ob die Drosselung im Log erscheint und mit welcher Adressangabe“ unter „Nicht entschieden“; `slice-qa-anmeldedrosselung` nennt die Log-Ausgabe der Drosselung als nicht gemessen (sie schreibt nichts). Das Ereignismodell trägt keine Adresse der Gegenstelle (`address` gehört dem Bindefehler). Keine Datei im Planning-Lifecycle führt die Entscheidung.
