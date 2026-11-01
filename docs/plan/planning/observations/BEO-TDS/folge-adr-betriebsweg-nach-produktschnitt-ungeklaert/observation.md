# Ob der Wegfall des Betriebswegs eine Folge-ADR zu Prüfwertformat und TLS verlangt, ist nicht festgestellt

**Sub-Area:** `*` (Spezifikation, ADRs)

[ADR-0011](../../../../adr/0011-pruefwertformat.md) und [ADR-0002](../../../../adr/0002-tls-und-verschluesselung.md) sprechen von Datei und Schlüsselnamen der Konfiguration. Seit [ADR-0025](../../../../adr/0025-produktschnitt-bibliothek-und-testserver.md) gehören Umgebung und Dateien dem Testserver, nicht der Bibliothek. Keine Zeile der Spezifikation widerspricht den beiden ADRs, sobald man sie auf den Testserver bezieht; ob die Zuordnung eine Folge-ADR (`Supersedes`) verlangt, ist nicht entschieden. Eine angenommene ADR wird nicht überschrieben (`AGENTS.md` §3.5).
