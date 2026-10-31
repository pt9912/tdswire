# Dateirechte und Übergabe der Benutzerliste im Betrieb sind weder gemessen noch entschieden

**Sub-Area:** `*` (Container-Artefakt, Betrieb)

Das Image enthält keine Benutzerliste; sie kommt zur Laufzeit herein (Mount, Secret oder `AUTH_USERS_FILE`). Wer sie besitzt und mit welchen Rechten der Nutzer ohne Root-Rechte (65532) sie liest, ist offen: der Laufzeittest legt sie per `docker cp` als root-eigene, weltlesbare Datei ab und belegt nur diesen Fall. Die [ADR-0011](../../../../adr/0011-pruefwertformat.md) führt den Punkt unter „Offen“; der Slice, der ihn trug, schließt ohne Entscheidung.
