# Eine Zeile der Sensors-Tabelle hat zu viele Zellen, und kein Gate liest die Spaltenzahl

**Sub-Area:** `*` (Doku, `harness/README.md` §Sensors)

Eine Änderung an der Zeile `make skript-selbsttest` ließ die Bindungszelle vor einem Komma schließen; die Zeile hatte 6 statt 5 Spalten, der Bezug auf die ADR stand außerhalb der Bindung. `make docs-check` meldete es nicht, auch `make gates` nicht; gefunden hat es der Review, `awk -F'|'` zeigt es. Ein Sensor „Spaltenzahl je Zeile der Sensors-Tabelle“ ist möglich und ist hier nur benannt, nicht gebaut; die Lücke liegt im Doku-Gate, das Links, nicht Tabellenform prüft.
