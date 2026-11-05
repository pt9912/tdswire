# Slice slice-prelogin-serverversion: Server-Version in der PRELOGIN-Antwort so festlegen, dass der JDBC-Treiber den Testserver annimmt

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell — Begründung: Der Slice ist Hürde 1 der d-migrate-Sonde ([d-migrate-sonde-2026-11-05](../../../messungen/d-migrate-sonde-2026-11-05.md)), und die Welle [welle-testserver-manuell](../welle-testserver-manuell.md) führt nach dem Umschnitt vom 2026-11-05 d-migrate als fremden Client gegen den Testserver; ihre Out-of-Scope-Liste schließt Änderungen an `src/main` nicht mehr aus (der frühere Grund, sie auszuschließen, ist mit dem Umschnitt entfallen). Angelegt war der Slice in [welle-qualitaet-grundlage](../welle-qualitaet-grundlage.md); [welle-referenzabgleich-release](../welle-referenzabgleich-release.md) scheidet weiterhin aus, weil der Wert hier eine Setzung ist und der Abgleich mit dem echten SQL Server dort anderen Slices gehört.

**Bezug:** [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität) (Kompatibilität), [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung) (PRELOGIN-Antwort), [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md) (Clientprofil, siehe §6).

**Berührte Spec-Stellen:** `spezifikation.md`, Tabelle *Login-Antwort des Servers (Festlegung)*, Zeile *PRELOGIN-Antwort*; Code `src/main/kotlin/io/github/pt9912/tdswire/protocol/session/PreloginNegotiation.kt` (Aufruf `PreloginOption.version(0, 0, 0, 0)`); Test `PreloginNegotiationTest` (Handvektor der Antwort).

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912. **Datum:** 2026-11-05.

---

## 1. Ziel und Abgrenzung

**Ziel:** Die Server-Version in der PRELOGIN-Antwort ist so festgelegt, dass mssql-jdbc (in d-migrate 1.12.0) den Testserver bei der Anmeldung annimmt und der .NET-Referenzclient ihn weiter annimmt.

Auslöser ist die Messung vom 2026-11-05 (frischer Klon; d-migrate 1.12.0 aus `ghcr.io/pt9912/d-migrate:latest`, Digest `sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491`, Image erzeugt 2026-10-27): Der Treiber lehnt die Antwort mit VERSION `0.0.0.0` ab (`SQL Server version 0 is not supported by this driver`, `Server major version:0`); Register: [festlegung-gilt-nur-fuer-das-clientprofil](../observations/BEO-TDS/festlegung-gilt-nur-fuer-das-clientprofil/observation.md). Der Referenzclient nimmt `0.0.0.0` laut den Referenztests an.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Katalogabfragen auf `sys.*` und `INFORMATION_SCHEMA` und weitere Anfrageformen, die `schema reverse` und die `data`-Befehle von d-migrate brauchen: Das sind eigene Gegenstände mit eigener Spec-Frage (`SPEC-036`). Sie sind inzwischen gemessen (Sonde vom 2026-11-05) und zugeordnet: Anmeldung und Export in [slice-d-migrate-verbindung-und-export](../open/slice-d-migrate-verbindung-und-export.md), Katalog und Zustand nach DDL in [slice-katalog-und-ddl-zustand](../open/slice-katalog-und-ddl-zustand.md), Schreiben in [slice-schreibpfad-bulk-load](../open/slice-schreibpfad-bulk-load.md) und [slice-schreibpfad-merge-output](../open/slice-schreibpfad-merge-output.md); die Entscheidung über Ort und Zuschnitt trifft [slice-adr-testserver-bestand-und-katalog](../open/slice-adr-testserver-bestand-und-katalog.md).
- Aufnahme von JDBC oder d-migrate in das Clientprofil: Das Clientprofil führt den .NET-Referenzclient ([ADR-0001](../../adr/0001-tds-version-und-clientprofil.md)); ob ein zweiter Client ins Profil kommt, ist eine Entscheidung, nicht Teil einer Versionsnummer.
- Ein Nachmessen der Version an einem echten SQL Server: gehört in den Referenzabgleich ([slice-qa-vergleichslauf-d-messungen](../open/slice-qa-vergleichslauf-d-messungen.md)); dieser Slice setzt einen Wert, er misst keinen.

## 2. Definition of Done

- [ ] Wert gewählt und als Setzung in `spezifikation.md` festgelegt: Tabelle *Login-Antwort des Servers (Festlegung)* nennt den neuen Wert, kennzeichnet ihn als Setzung (nicht gemessen) und nennt die Messung der Ablehnung von `0.0.0.0` mit Datum, Client und Fehlertext.
- [ ] Code und Test: `PreloginNegotiation.kt` sendet den Wert; `PreloginNegotiationTest` hält den Handvektor der Antwort (VERSION-Bytes) fest; die .NET-Referenztests bleiben grün.
- [ ] Beleg am JDBC-Treiber: ein Login mit mssql-jdbc oder d-migrate gegen den Testserver (`make testserver-up`) kommt über die Anmeldung hinaus; die Messung nennt Client, Version, Datum und Host, oder ein Treiber-Lauf im Repo belegt es als Test. Was danach scheitert, wird gemeldet, nicht behoben.

## 3. Plan (vor Code)

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `spec/spezifikation.md` | update | Festlegung VERSION, [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität); Setzung kennzeichnen |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/session/PreloginNegotiation.kt` | update | neuer Wert statt `version(0, 0, 0, 0)` |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/session/PreloginNegotiationTest.kt` | update | Handvektor der Antwort, Happy-Path nach [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung) |

## 4. Trigger

**Start** (`next` → `in-progress`): Die Welle [welle-testserver-manuell](../welle-testserver-manuell.md) läuft, und die Frage zum Wert (§6) ist entschieden oder der Slice setzt den Wert ausdrücklich als Setzung.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): Der Login mit dem Treiber scheitert nach der Versionsänderung an etwas anderem als der Version und verlangt eine Änderung außerhalb von PRELOGIN.
- `in-progress` → `open` (blockiert): die Entscheidung zu [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md) (§6) steht aus.

## 5. Closure-Trigger

DoD vollständig, `make gates` grün, Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

- **Offene Frage zum Wert:** die Version eines echten SQL Servers, wie ihn der Referenzabgleich liefert, oder ein fester Wert? Beides ist eine Setzung; ein gemessener Wert liegt erst vor, wenn der Referenzabgleich ihn liefert, und er wird hier nicht erfunden. Der Wert muss die Mindestversion beachten, die mssql-jdbc verlangt (nicht gemessen; die Messung nannte nur den abgelehnten Wert 0). — **Ausgang:** weiter offen: → `BEO-TDS/festlegung-gilt-nur-fuer-das-clientprofil` im Register
- **ADR nötig?** Die Spezifikation wird geschärft, das Lastenheft bleibt unverändert (die Server-Version ist keine Anforderung; [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung) nimmt „undokumentierte Clientvarianten“ aus). Eine Setzung innerhalb der Spezifikation braucht nach heutiger Lesart keine ADR. Eine ADR (neue, die [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md) nicht überschreibt) wird nötig, sobald JDBC oder d-migrate ins Clientprofil aufgenommen werden soll. Der Slice entscheidet das nicht; der Implementer legt die Frage dem Nutzer vor, bevor er den Wert als Profil-Zusage formuliert. — **Ausgang:** weiter offen: → `BEO-TDS/festlegung-gilt-nur-fuer-das-clientprofil` im Register
- Der Wert bricht den Referenzclient: Die Referenztests nehmen `0.0.0.0` an, ein anderer Wert ist dort nicht gemessen. — **Ausgang:** weiter offen: → `BEO-TDS/festlegung-gilt-nur-fuer-das-clientprofil` im Register (durch die .NET-Referenztests der DoD zu belegen)

## 7. Closure-Notiz

- **Was hat funktioniert:** —
- **Was ging anders als geplant:** —
- **Steering-Loop-Eintrag:** —
- **Beobachtungs-Register (`../observations/`):** —
- **Folge-Slices:** —
- **Risiken aus §6:** —

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Eine Sub-Area (`*`, Kürzel `TDS`); keine Aufteilung nötig.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen. Treffer: [festlegung-gilt-nur-fuer-das-clientprofil](../observations/BEO-TDS/festlegung-gilt-nur-fuer-das-clientprofil/observation.md) (1×, neu); verwandt ohne Zählung hier: [messung-am-echten-sql-server-fehlt](../observations/BEO-TDS/messung-am-echten-sql-server-fehlt/observation.md) (geplant bei [slice-qa-vergleichslauf-d-messungen](../open/slice-qa-vergleichslauf-d-messungen.md)).

Alle berührten Sub-Areas GF.
