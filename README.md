# JVM TDS Server

## Was ist JVM TDS Server?

Ein eigenständiger Serverendpunkt auf der JVM (Kotlin), der das SQL-Server-Protokoll
Tabular Data Stream (TDS) spricht. .NET-Anwendungen verbinden sich mit ihrem
gewohnten SqlClient; der Server authentifiziert sie, reicht unterstützte Anfragen
an ein austauschbares Backend weiter und liefert Resultate und Fehler im
erwarteten Protokollformat zurück. Er ist kein Ersatz für Microsoft SQL Server,
sondern deckt einen klar abgegrenzten Funktionsumfang ab. Abfrage-Engine und
Datenhaltung gehören nicht dazu.

## Was kann ich heute tun?

Es gibt noch keinen Server-Code. Der Bestand besteht aus Spezifikation, Harness
und Dokumenten-Gates:

- `make gates` prüft die vendored Baseline und die Dokumentreferenzen und läuft grün.
- `make help` listet alle vorhandenen Targets.
- Lastenheft, Spezifikation und Architektursicht liegen als Entwurf unter `spec/`.
- Die Architekturentscheidungen stehen als Vorschläge im [ADR-Index](docs/plan/adr/README.md);
  keine ist angenommen.

## Warum JVM TDS Server?

Bestehende .NET-Anwendungen sollen ein eigenes Abfrage-Backend nutzen können,
ohne einen proprietären Clientadapter zu brauchen. Der Server übernimmt dafür
die Protokollarbeit, das Backend implementiert nur einen schmalen Vertrag ohne
TDS-Paketkodierung.

## Kerngedanke

Protokoll und Backend sind getrennt. Der Server verantwortet Framing,
Verhandlung, Sitzungen, Fehlerabbildung und Abbruch; das Backend sieht nur
typisierte Anfragen, Parameter und Ergebnisströme. Maßgeblich für die
Kompatibilität ist das Verhalten eines festgelegten Referenzclients, nicht der
vollständige TDS-Standard.

## Was macht es vertrauenswürdig?

- **Prozess:** [`AGENTS.md`](AGENTS.md) (Hard Rules), [`harness/README.md`](harness/README.md) (Source Precedence, Gates).
- **Verträge:** [`spec/lastenheft.md`](spec/lastenheft.md) (`TDS-*`-IDs mit Akzeptanzkriterien).
- **Gates:** `make gates` führt `baseline-verify` (Integrität der vendored Baseline) und
  `docs-check` (Dokumentreferenzen, Kennungen, Struktur) aus. Code-Gates gibt es erst mit
  dem Code.
- **Auditierbarkeit:** Entscheidungen in `docs/plan/adr/`, Planung in `docs/plan/planning/`.
