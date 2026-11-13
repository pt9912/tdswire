# Slice slice-prelogin-serverversion: Server-Version in der PRELOGIN-Antwort so festlegen, dass der JDBC-Treiber den Testserver annimmt

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell — Begründung: Der Slice ist Hürde 1 der d-migrate-Sonde ([d-migrate-sonde-2026-11-05](../../../messungen/d-migrate-sonde-2026-11-05.md)), und die Welle [welle-testserver-manuell](welle-testserver-manuell.md) führt nach dem Umschnitt vom 2026-11-05 d-migrate als fremden Client gegen den Testserver; ihre Out-of-Scope-Liste schließt Änderungen an `src/main` nicht mehr aus (der frühere Grund, sie auszuschließen, ist mit dem Umschnitt entfallen). Angelegt war der Slice in [welle-qualitaet-grundlage](../welle-qualitaet-grundlage.md); [welle-referenzabgleich-release](../welle-referenzabgleich-release.md) scheidet weiterhin aus, weil der Wert hier eine Setzung ist und der Abgleich mit dem echten SQL Server dort anderen Slices gehört.

**Bezug:** [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität) (Kompatibilität), [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung) (PRELOGIN-Antwort), [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md) (Clientprofil, siehe §6).

**Berührte Spec-Stellen:** `spezifikation.md`, Tabelle *Login-Antwort des Servers (Festlegung)*, Zeile *PRELOGIN-Antwort*; Code `src/main/kotlin/io/github/pt9912/tdswire/protocol/session/PreloginNegotiation.kt` (Aufruf `PreloginOption.version(0, 0, 0, 0)`); Test `PreloginNegotiationTest` (Handvektor der Antwort).

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-11-05.

---

## 1. Ziel und Abgrenzung

**Ziel:** Die Server-Version in der PRELOGIN-Antwort ist so festgelegt, dass mssql-jdbc (in d-migrate 1.12.0) den Testserver bei der Anmeldung annimmt und der .NET-Referenzclient ihn weiter annimmt.

Auslöser ist die Messung vom 2026-11-05 (frischer Klon; d-migrate 1.12.0 aus `ghcr.io/pt9912/d-migrate:latest`, Digest `sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491`, Image erzeugt 2026-10-27): Der Treiber lehnt die Antwort mit VERSION `0.0.0.0` ab (`SQL Server version 0 is not supported by this driver`, `Server major version:0`); Register: [festlegung-gilt-nur-fuer-das-clientprofil](../observations/BEO-TDS/festlegung-gilt-nur-fuer-das-clientprofil/observation.md). Der Referenzclient nimmt `0.0.0.0` laut den Referenztests an.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Katalogabfragen auf `sys.*` und `INFORMATION_SCHEMA` und weitere Anfrageformen, die `schema reverse` und die `data`-Befehle von d-migrate brauchen: Das sind eigene Gegenstände mit eigener Spec-Frage (`SPEC-036`). Sie sind inzwischen gemessen (Sonde vom 2026-11-05) und zugeordnet: Anmeldung und Export in [slice-d-migrate-verbindung-und-export](../done/slice-d-migrate-verbindung-und-export.md), Katalog und Zustand nach DDL in [slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md), Schreiben in [slice-schreibpfad-bulk-load](../done/slice-schreibpfad-bulk-load.md) und [slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md); die Entscheidung über Ort und Zuschnitt trifft [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md).
- Aufnahme von JDBC oder d-migrate in das Clientprofil: Das Clientprofil führt den .NET-Referenzclient ([ADR-0001](../../adr/0001-tds-version-und-clientprofil.md)); ob ein zweiter Client ins Profil kommt, ist eine Entscheidung, nicht Teil einer Versionsnummer.
- Ein Nachmessen der Version an einem echten SQL Server: gehört in den Referenzabgleich ([slice-qa-vergleichslauf-d-messungen](../done/slice-qa-vergleichslauf-d-messungen.md)); dieser Slice setzt einen Wert, er misst keinen.

## 2. Definition of Done

- [x] Wert gewählt und als Setzung in `spezifikation.md` festgelegt: Tabelle *Login-Antwort des Servers (Festlegung)* nennt den neuen Wert, kennzeichnet ihn als Setzung (nicht gemessen) und nennt die Messung der Ablehnung von `0.0.0.0` mit Datum, Client und Fehlertext.
- [x] Code und Test: `PreloginNegotiation.kt` sendet den Wert; `PreloginNegotiationTest` hält den Handvektor der Antwort (VERSION-Bytes) fest; die .NET-Referenztests bleiben grün. — Code und Test bestätigt (`10 00 03 E8 00 06`). „Referenztests bleiben grün“ ist im Sinn von „Anmeldung und alle Szenarien gelingen mit `16.0.1000.6`“ erfüllt (`make kompatibilitaet`: 13 Tests grün laut [Verifikation](../../../reviews/2026-11-05-bc7d7fe-verifikation-slice-prelogin-serverversion.md)). Der Wert am .NET-Client ist nicht gesondert gemessen: `ServerVersion` wird nur auf „nicht leer“ geprüft und stammt aus dem LOGINACK, nicht aus der PRELOGIN-VERSION.
- [x] Beleg am JDBC-Treiber: ein Login mit mssql-jdbc oder d-migrate gegen den Testserver (`make testserver-up`) kommt über die Anmeldung hinaus; die Messung nennt Client, Version, Datum und Host, oder ein Treiber-Lauf im Repo belegt es als Test. Was danach scheitert, wird gemeldet, nicht behoben.

## 3. Plan (vor Code)

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `spec/spezifikation.md` | update | Festlegung VERSION, [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität); Setzung kennzeichnen |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/session/PreloginNegotiation.kt` | update | neuer Wert statt `version(0, 0, 0, 0)` |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/session/PreloginNegotiationTest.kt` | update | Handvektor der Antwort, Happy-Path nach [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung) |

## 4. Trigger

**Start** (`next` → `in-progress`): Die Welle [welle-testserver-manuell](welle-testserver-manuell.md) läuft, und die Frage zum Wert (§6) ist entschieden oder der Slice setzt den Wert ausdrücklich als Setzung.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): Der Login mit dem Treiber scheitert nach der Versionsänderung an etwas anderem als der Version und verlangt eine Änderung außerhalb von PRELOGIN.
- `in-progress` → `open` (blockiert): die Entscheidung zu [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md) (§6) steht aus.

## 5. Closure-Trigger

DoD vollständig, `make gates` grün, Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

- **Offene Frage zum Wert:** die Version eines echten SQL Servers, wie ihn der Referenzabgleich liefert, oder ein fester Wert? Beides ist eine Setzung; ein gemessener Wert liegt erst vor, wenn der Referenzabgleich ihn liefert, und er wird hier nicht erfunden. Der Wert muss die Mindestversion beachten, die mssql-jdbc verlangt (nicht gemessen; die Messung nannte nur den abgelehnten Wert 0). — **Ausgang:** **entfallen** als Entscheidung dieses Slice: Der Wert `16.0.1000.6` ist als Setzung gewählt (Spec, Messprotokoll [prelogin-serverversion-2026-11-05](../../../messungen/prelogin-serverversion-2026-11-05.md)). Der echte Wert kommt aus dem Referenzabgleich [slice-qa-vergleichslauf-d-messungen](../done/slice-qa-vergleichslauf-d-messungen.md); dorthin geht er als Hinweis, die Frage bleibt dort offen. Die Mindest-Hauptversion von mssql-jdbc ist nicht gemessen.
- **ADR nötig?** Die Spezifikation wird geschärft, das Lastenheft bleibt unverändert (die Server-Version ist keine Anforderung; [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung) nimmt „undokumentierte Clientvarianten“ aus). Eine Setzung innerhalb der Spezifikation braucht nach heutiger Lesart keine ADR. Eine ADR (neue, die [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md) nicht überschreibt) wird nötig, sobald JDBC oder d-migrate ins Clientprofil aufgenommen werden soll. Der Slice entscheidet das nicht; der Implementer legt die Frage dem Nutzer vor, bevor er den Wert als Profil-Zusage formuliert. — **Ausgang:** **entfallen**, solange JDBC und d-migrate nicht ins Clientprofil kommen; der Wert steht in der Spec ausdrücklich außerhalb des Clientprofils. Die Frage zum Clientprofil bleibt offen → [festlegung-gilt-nur-fuer-das-clientprofil](../observations/BEO-TDS/festlegung-gilt-nur-fuer-das-clientprofil/observation.md) im Register (Evidenz ergänzt); die Entscheidung liegt beim Nutzer.
- Der Wert bricht den Referenzclient: Die Referenztests nehmen `0.0.0.0` an, ein anderer Wert ist dort nicht gemessen. — **Ausgang:** Referenzclient bricht nicht: Anmeldung und alle Szenarien gelingen mit `16.0.1000.6` (`make kompatibilitaet`, 13 Tests grün, Verifikation). Der Wert am Client ist nicht gesondert gemessen (DoD Punkt 2).

## 7. Closure-Notiz

**Gegenstand:** geliefert, DoD vollständig (Punkt 2 mit der benannten Messgrenze). Code: `fbd2a44` (VERSION `16.0.1000.6`, Handvektor, Spec, Matrix), `5b1ad85` (Messprotokoll der Anmeldung), `bc7d7fe` (Review-Befunde F-1 bis F-4: Ablehnung von `0.0.0.0` nachgemessen). Berichte: [Review](../../../reviews/2026-11-05-5b1ad85-slice-prelogin-serverversion.md) (kein HIGH, ein MEDIUM, drei LOW, alle eingearbeitet), [Verifikation](../../../reviews/2026-11-05-bc7d7fe-verifikation-slice-prelogin-serverversion.md) (closure-fähig mit drei Bedingungen, kein HIGH, kein MEDIUM). Messprotokoll: [prelogin-serverversion-2026-11-05](../../../messungen/prelogin-serverversion-2026-11-05.md).

| Bedingung der Verifikation | Ausgang |
|---|---|
| B-1 `make gates` | grün (Exit 0) auf dem Zweig `slice-prelogin-serverversion` (Code-Stand `bc7d7fe`; seither nur Dokumente); Stempel-Gruppen mit Beleg übersprungen, Doku-Gate 0 Befunde. Auf `main` folgt der Lauf des Aufrufers nach dem Merge. |
| B-2 Ausgänge in §6 | alle drei geschlossen, siehe §6 (Wert: Setzung; ADR: entfallen; Referenzclient: Anmeldung gelingt). |
| B-3 Aussage zu DoD 2 | wörtlich in der DoD-Zeile: Anmeldung und Szenarien gelingen, der Wert am .NET-Client ist nicht gesondert gemessen. |

**Messung:** Ablehnung mit `0.0.0.0` (Lauf A): Exit 4, `Server major version:0 is not supported by this driver`. Annahme mit `16.0.1000.6` (Lauf B): die Meldung zur Version entfällt, Exit 4 mit `Unsupported SQL.` (Fehler nach dem Login, kein Versionsfehler). `make clean-build` (Verifier): 907 Testfälle `PASSED`, keiner `FAILED`; Handvektor `10 00 03 E8 00 06` im Test grün. Einzelmessung, ein Host.

**Grenzen (nicht belegt):** Der Wert ist eine Setzung, kein Wert eines echten SQL Servers; die Mindest-Hauptversion von mssql-jdbc ist nicht gemessen; der Wert am .NET-Client ist nicht gesondert gemessen (ein eigener Test wäre ein eigener Gegenstand). Nach dem Login scheitert `schema reverse` mit `Unsupported SQL.`, vermutlich am Verbindungstest `SELECT 1`; das gehört in [slice-d-migrate-verbindung-und-export](../done/slice-d-migrate-verbindung-und-export.md) und wurde nicht behoben.

**Steering-Loop-Eintrag:** geschärfte Beobachtung, keine neue Regel: Eine Messaussage der Spec („Gemessen am 2026-11-05 … lehnt der Treiber ab“) stand breiter als das Protokoll, das nur die Annahme belegte; der Review fand es als MEDIUM, behoben durch Nachmessen und ein Protokoll mit beiden Läufen. Register: Evidenz in [aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md) (weiteres Auftreten, Klasse bereits verkörpert in `AGENTS.md` §3.8 und `.harness/skills/reviewer.md`, kein neuer Zielort).

**Beobachtungs-Register (`../observations/`):** zwei Evidence-Dateien: `BEO-TDS/aussagen-ueber-messung-hinaus/evidence/slice-prelogin-serverversion.md` und `BEO-TDS/festlegung-gilt-nur-fuer-das-clientprofil/evidence/slice-prelogin-serverversion.md`; Zähler nicht gesetzt.

**Folge-Slices:** [slice-d-migrate-verbindung-und-export](../done/slice-d-migrate-verbindung-und-export.md) (`Unsupported SQL.` nach dem Login), [slice-qa-vergleichslauf-d-messungen](../done/slice-qa-vergleichslauf-d-messungen.md) (echter Wert der VERSION).

**Risiken aus §6:** alle drei mit Ausgang (siehe oben).

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Eine Sub-Area (`*`, Kürzel `TDS`); keine Aufteilung nötig.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen. Treffer: [festlegung-gilt-nur-fuer-das-clientprofil](../observations/BEO-TDS/festlegung-gilt-nur-fuer-das-clientprofil/observation.md) (1×, neu); verwandt ohne Zählung hier: [messung-am-echten-sql-server-fehlt](../observations/BEO-TDS/messung-am-echten-sql-server-fehlt/observation.md) (geplant bei [slice-qa-vergleichslauf-d-messungen](../done/slice-qa-vergleichslauf-d-messungen.md)).

Alle berührten Sub-Areas GF.
