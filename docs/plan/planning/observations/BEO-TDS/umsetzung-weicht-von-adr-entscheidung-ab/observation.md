# Die Umsetzung weicht von einer Entscheidung der angenommenen ADR ab

**Sub-Area:** `*` (Build, ADRs)

Die Umsetzung findet beim Bauen einen Weg, der von einer Einzelentscheidung der angenommenen ADR abweicht (hier: [ADR-0034](../../../../adr/0034-testserver-als-gradle-modul.md) Entscheidung 8 setzt eine auflösbare Konfiguration `project(":testserver")` für die Wurzel-`test`; gebaut ist die Datei der Modul-Task `testserverKlassenpfad`). Gemessen ist nur die gebaute Lösung, die Entscheidung der ADR nicht. Die ADR bleibt unverändert (`AGENTS.md` §3.5); die Abweichung braucht einen benannten Ausgang (Hinnahme oder Folge-ADR), sonst steht die ADR gegen den Code. Gesucht wird im Review: jede Entscheidung der ADR gegen den Bau, nicht nur die Messpunkte.
