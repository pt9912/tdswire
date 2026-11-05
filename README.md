# JVM TDS Server

## Was ist JVM TDS Server?

Eine Kotlin-Bibliothek für die JVM, die einen Serverendpunkt bereitstellt, der das SQL-Server-Protokoll
Tabular Data Stream (TDS) spricht. .NET-Anwendungen verbinden sich mit ihrem
gewohnten SqlClient; der Server authentifiziert sie, reicht unterstützte Anfragen
an ein austauschbares Backend weiter und liefert Resultate und Fehler im
erwarteten Protokollformat zurück. Er ist kein Ersatz für Microsoft SQL Server,
sondern deckt einen klar abgegrenzten Funktionsumfang ab. Abfrage-Engine und
Datenhaltung gehören nicht dazu.

## Was kann ich heute tun?

Das Produkt ist eine Bibliothek: `make build` baut das Bibliotheks-Jar `tdswire.jar` ohne `Main-Class`.
Einbettende starten den Server über `TdsServer.start`. Ein Produkt-Image und einen Start des
Produkts im Container gibt es nicht. Der Testserver in `src/testserver` ist ein ausführbarer
Serverprozess nur zur Prüfung der Bibliothek (E2E- und Kompatibilitätsläufe), kein Produkt.

- `make gates` führt die Gates aus (Tests, detekt, Build, Dokumentreferenzen, vendored Baseline).
- `make e2e` führt den E2E-Durchgang in sieben Stufen aus, ein Gate ist er nicht.
- `make testserver-up` startet den Testserver von Hand, `make testserver-down` stoppt ihn; Anleitung, Anmeldung und Grenzen: [`docs/user/testserver-manuell.md`](docs/user/testserver-manuell.md).
- `make help` listet alle vorhandenen Targets.
- Lastenheft, Spezifikation und Architektursicht liegen unter `spec/`.
- Die Architekturentscheidungen mit ihrem Status stehen im [ADR-Index](docs/plan/adr/README.md).

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
