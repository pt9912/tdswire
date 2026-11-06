# Slice slice-referenzabgleich-live: Referenzabgleich, Live-Lauf: derselbe Referenzclient gegen die Referenzinstanz und gegen den Server mit Simulations-Backend

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-referenzabgleich-release

**Bezug:** [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)

**Berührte Spec-Stellen:** SPEC-025 · SPEC-032 · SPEC-035 · SPEC-045 · SPEC-050 · [TDS-FA-11.a](../../../../spec/spezifikation.md#tds-fa-11a--simulations-backend)

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-10-30.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Der Referenzclient führt dieselben Szenarien nacheinander gegen eine SQL-Server-Referenzinstanz und gegen den Server mit Simulations-Backend aus, das dasselbe Testschema beschreibt; der Lauf ist grün, wenn beide Ausgaben gleich sind oder genau der dokumentierten Abweichung entsprechen (SPEC-045).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Make-Target des Vergleichslaufs D, Container der Referenzinstanz, die Referenz-Seite der Läufe, EULA-Prüfung, Messung von Laufzeit und Ressourcen und der Image-Tag für TDS 8.0 — [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md); dieser Slice nutzt sie und ergänzt die Server-Seite. Fehlt dort etwas, wenn dieser Slice startet, ist das ein Rückführungsgrund, kein stilles Nachbauen.
- Die Szenarien des Referenzclients selbst (S1 bis S18) — [slice-qa-kompatibilitaet](../done/slice-qa-kompatibilitaet.md); dieser Slice führt dieselben Szenarien gegen zwei Ziele aus (das Ziel ist ein Parameter) und schreibt keine zweite Fassung.
- Der Mitschnitt-Lauf nach SPEC-045 — [slice-referenzabgleich-mitschnitt](../done/slice-referenzabgleich-mitschnitt.md); die Tokenfolge sieht der Live-Lauf nicht, er vergleicht, was der Client ausgibt.
- Der TDS-8.0-Pfad — an der Referenzinstanz Version 2022 scheitert `Encrypt=Strict`; dass sie TDS 8.0 nicht anbietet, ist gefolgert, nicht gemessen ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), SPEC-045; offene Frage in [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md) §6); der Abgleich entfällt dort und steht als Grenze in der Ausnahmeliste.
- Das Beheben gefundener Abweichungen im Server — je Befund ein Slice nach der Messung; dieser Slice dokumentiert sie als *Lücke* mit Verweis auf die offene Arbeit.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Server-Seite:** Der Server mit Simulations-Backend läuft für den Lauf in Docker (eigener Container im eigenen Netzwerk, ohne Mount, Basis-Image per Digest, Stage `kompatibilitaetsserver`); seine Tabellen stehen im Code des Integrators (`KompatibilitaetsServer.kt`), das Testschema der Referenzinstanz ist das eingecheckte Skript `testschema.sql` (Entscheidung N5; beide getrennt gepflegt und gemeinsam geändert, SPEC-045). Entscheidung des Nutzers 2026-11-06: das Schema bleibt im Code des Integrators, nicht in der Beschreibungsdatei (sie trägt keine Zeilen, [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md)).
- [x] **Live-Lauf:** Das Szenario (Anmeldung, SELECT mit und ohne Parameter, NULL, Unicode, Decimal, Datum, leere und mehrteilige Ergebnisse, Fehler vor und nach Zeilen, OUTPUT und RETURNSTATUS soweit freigegeben, Cancel mit Folgeabfrage) läuft nacheinander gegen beide Ziele, je Schritt eine Ausgabezeile; vom Server selbst vergebene Werte (Objekt-IDs, Sitzungs-IDs, Zeitstempel, Versionstext) werden nur auf vorhanden oder nicht vorhanden verglichen, Dauern werden nicht verglichen.
- [x] **Ausnahmeliste und Urteil:** Jede Abweichung steht mit Pfad, Anweisung oder Szenario, Grund und Art (*so gewollt* oder *Lücke* mit Verweis auf die offene Arbeit) in der Liste; eine kleinere oder größere Abweichung als die dokumentierte macht den Lauf rot (Test mit verschiedenen Abweichungen); ob der Lauf in `make gates` kommt, entscheidet die in [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md) gemessene Laufzeit.

Nachweis-Pflichten für alle drei Punkte:

- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| Referenztestprojekt (`referenztest-dotnet/`) | update | Ziel als Parameter der vorhandenen Szenarien; Ausgabe je Schritt eine Zeile |
| Tabellen im Code des Integrators (`testserver/kotlin/io/github/pt9912/tdswire/testserver/KompatibilitaetsServer.kt`) | unverändert genutzt | Server-Seite: das Schema bleibt im Code (Nutzerentscheidung 2026-11-06); Gegenstück ist das Skript der Referenzinstanz (nächste Tabelle) |
| Ausnahmeliste (`referenztest-dotnet/Vergleich/live-ausnahmen.tsv`, Prosa in `docs/messungen/referenzabgleich.md`) | neu | Ablage nach dem Rahmen aus [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md) |
| `tools/mk/` | update | Make-Target des Vergleichslaufs aus [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md) erweitern; Eintrag in `harness/README.md` §Sensors |

Erweitert im ersten Lauf des Implementers:

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `referenztest-dotnet/Vergleich/LiveSzenarioTests.cs` | neu | das Szenario des Live-Laufs, 25 Schritte, Ziel und Benutzer aus der Umgebung, eine Ausgabezeile je Schritt |
| `referenztest-dotnet/Vergleich/testschema.sql` | neu | Testschema der Referenzinstanz (Gegenstück zu den Tabellen im Code des Kompatibilitätsservers); als Ressource im Test-Assembly (`TdsWire.Referenztests.csproj`) |
| `referenztest-dotnet/Vergleich/live-ausnahmen.tsv` | neu | maschinenlesbare Ausnahmeliste; die Prosa steht in `docs/messungen/referenzabgleich.md` |
| `referenztest-dotnet/Vergleich/ReferenzMessungTests.cs` | neu | Gegenmessungen an der Referenzinstanz zu den Befunden der Spikes (kein Urteil) |
| `tools/vergleichslauf-d.sh`, `tools/mk/vergleichslauf-d.mk` | update | Läufe `live-*` und `messung-*`, Vergleich mit der Ausnahmeliste (`--vergleich`), Start der Referenzinstanz als Funktion |
| `tools/vergleichslauf-d-selbsttest.sh` | update | Vergleich mit Gleichheit, gelisteter, kleinerer, größerer und nicht gelisteter Abweichung (Teil von `make skript-selbsttest`) |
| `docs/messungen/referenzabgleich.md`, `harness/README.md` | update | Ausnahmeliste A-2 bis A-9, Messungen mit Befehl, Bedingungen und Digest; Zeile in §Sensors |

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [welle-simulations-backend](../done/welle-simulations-backend/welle-simulations-backend.md) liegt in `done/`, und [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md) und [slice-qa-kompatibilitaet](../done/slice-qa-kompatibilitaet.md) liegen in `done/` (Vergleichslauf D, Referenzinstanz, Szenarien), und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2 oder die Server-Seite sprengt den Rahmen; Schnittvorschlag: Schema-Gleichlauf als eigener Slice.
- `in-progress` → `open` (blockiert — Carveout?): das Make-Target des Vergleichslaufs oder die Referenzinstanz aus [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md) steht nicht zur Verfügung, oder die EULA-Zustimmung ist rechtlich nicht geklärt.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün, der Review-Report liegt vor, und die Closure-Notiz trägt die Ausnahmeliste mit Art und Verweis je Regel, die Grenzen des Laufs (Referenzinstanzen 2022 und 2025 am 2026-11-06 gemessen, TDS 7.4; [ADR-0028](../../adr/0028-referenzinstanz-fuer-tds-8-0.md) ist Proposed, SPEC-045 sagt noch „eine Instanz Version 2022“, Nachzug: [slice-referenzabgleich-adr-0028-nachzug](../open/slice-referenzabgleich-adr-0028-nachzug.md)) und den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Quelle des Skripts der Referenzinstanz und Gemeinsamkeit mit der Beschreibungsdatei (N5, [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) Offen 3): Das Skript ist das eingecheckte `testschema.sql`, das Schema des Servers bleibt im Code des Integrators (`KompatibilitaetsServer.kt`); die Beschreibungsdatei trägt keine Zeilen und ist nicht die gemeinsame Quelle (Entscheidung des Nutzers 2026-11-06, Empfehlung übernommen). Was die Referenzinstanz mit einem aus der Beschreibung erzeugten Skript (`schema generate --target mssql`) tut, ist nicht gemessen und nicht Gegenstand dieses Slice. — **Ausgang:** entfallen (entschieden; der Satz in `SPEC-045` zur Pflege beider Seiten ist damit nachzuführen, nicht zu entscheiden: kein Träger benannt, Verifikation V-5).
- Servergleiche, die [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) nennt (Offen 6: Textlänge, Identität, Dezimalzahl über 38, `json`/`array`/`enum`, Schemas, nicht erzwungene Eigenschaften, Collation, Sitzungsoptionen, Zeilen; außerdem Überführung von Literalen und Parametern in den Spaltentyp, Textvergleich, Kommentarschachtelung, Rundung): Das Szenario des Live-Laufs enthält zu den Punkten dieser Aufzählung keinen eigenen Schritt (Wertschritte `select-typen`, `select-breit`, `parameter-*` lesen Typen und Werte, mehr nicht); sie sind nicht am Server gemessen. — **Ausgang:** weiter offen (Register `BEO-TDS/messung-am-echten-sql-server-fehlt`; kein Träger-Slice).
- Gemessen sind 2022 und 2025 (Live-Lauf, 2026-11-06; Nachzug von `SPEC-045` und [ADR-0028](../../adr/0028-referenzinstanz-fuer-tds-8-0.md) `Proposed`: [slice-referenzabgleich-adr-0028-nachzug](../open/slice-referenzabgleich-adr-0028-nachzug.md)); an 2022 scheitert `Encrypt=Strict`, und das Server-Log nennt TLS 1.0 bis 1.2 ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)): TDS 8.0 und TLS 1.3 sind an ihr nicht abgleichbar; der Live-Lauf läuft mit TDS 7.4 und `Encrypt=Mandatory`. — **Ausgang:** weiter offen (`BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`; Träger des Nachzugs [slice-referenzabgleich-adr-0028-nachzug](../open/slice-referenzabgleich-adr-0028-nachzug.md)).
- Der Fall „Fehler nach Zeilen“ hatte bisher keine Server-Referenz; der Lauf liefert sie (Schritt `fehler-nach-zeilen`: drei Zeilen, dann `Number 8134, Class 16`, Verbindung und Folgeabfrage offen, beide Instanzen) und widerlegt keine Festlegung der Spezifikation oder von [ADR-0007](../../adr/0007-fehler-und-attention.md): gleich sind Zeilenzahl vor dem Fehler, Class und Zustand, es weicht nur der Text ab (A-6, so gewollt, `TDS_BACKEND_ERROR`). — **Ausgang:** entfallen.
- Verglichen wird, was der Lauf ausgibt; was nicht ausgegeben wird, ist nicht verglichen. „Grün“ belegt Übereinstimmung mit der Ausnahmeliste, nicht mit dem Zielbild: 13 von 25 Schritten weichen ab (3 so gewollt, 10 Lücke), und aus „grün“ folgt nichts über Tokenfolge, Statusbits, Paketierung, ENVCHANGE oder den Draht von `Cancel()`. Die Grenze steht in §7 und im Messprotokoll. — **Ausgang:** weiter offen (Wire: [slice-referenzabgleich-mitschnitt](../done/slice-referenzabgleich-mitschnitt.md) und [slice-qa-vergleichslauf-d-messungen](../open/slice-qa-vergleichslauf-d-messungen.md)).
- Der Lauf braucht `ACCEPT_EULA=Y` für das Image der Referenzinstanz; die Zustimmung ist die Entscheidung des Nutzers pt9912 (2026-11-01, für diesen Slice am 2026-11-06 bekräftigt) und rechtlich ungeprüft ([slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md) trägt die Prüfung). Der Lauf ist lokal; Aufnahme in `make gates` oder die CI ist abgelehnt (Nutzerentscheidung 2026-11-06). — **Ausgang:** weiter offen (Rechtsprüfung; Register `BEO-TDS/vergleichslauf-d-ungeklaert`).
- Ein Vergleich mit Ausnahmeliste kann durch zu weite Regeln alles grün machen: Die Regeln sind je Schritt und mit wortgleichem Ergebnispaar eng, `--vergleich` urteilt rot bei kleinerer, größerer und nicht gelisteter Abweichung, bei doppelter Regel und bei Regel ohne Abweichung (Selbsttest in `make skript-selbsttest`, vom Verifier gefahren, Exit 0). — **Ausgang:** entfallen.
- Schema-Gleichlauf ohne Sensor: Eine Änderung an Precision, Länge oder Nullbarkeit in `KompatibilitaetsServer.kt` ohne `testschema.sql` lässt `make gates` grün (der Lauf ist kein Gate, [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)); sie fällt nur auf, wenn `LAUF=live` läuft und ein Wertschritt sie liest. Die Schritte `schema-*` wären der Sensor, scheitern aber am Server (A-8). — **Ausgang:** weiter offen (Register `BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht`, `BEO-TDS/schemaonly-keyinfo-fmtonly-ohne-traeger`).
- Offene Schulden aus der Ausnahmeliste (Zielbild: der echte SQL Server ist die Referenz, jede Abweichung ist Schuld des Servers, außer Spezifikation oder ADR nennen sie als Ziel): A-2 mehrteilige Antwort, A-3 Class 20 bei unbekannter Tabelle, A-4 und A-5 erkannte Anweisungsformen, A-7 Text der Antwort auf Cancel, A-8 `SchemaOnly`/`KeyInfo`/`FMTONLY`, A-9 feste Zeilenzahlen des Callbacks. Keine hat einen bauenden Träger; jede trägt eine Beobachtung im Register mit Träger-Vorschlag (siehe §7), Nutzerentscheidung 2026-11-06: A-3, A-4, A-5, A-9 bleiben Lücke. — **Ausgang:** weiter offen (Register, Träger-Vorschläge zur Bestätigung durch den Nutzer).
- Widerspruch einer Gegenmessung zu einer `Accepted`-ADR: `sp_unprepare` mit unbekanntem Handle endet am echten Server mit Fehler 8179 (auch als Batch, 2022 und 2025), [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md) setzt für die Batch-Form „Erfolg (Setzung)“. Die ADR blieb unverändert (`AGENTS.md` §3.5); [ADR-0037](../../adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md) (`Accepted`, 2026-11-06) ersetzt den betroffenen Halbsatz teilweise und wörtlich benannt. Beleg: `docs/messungen/referenzabgleich.md`, M-4. — **Ausgang:** eingetreten (entschieden durch [ADR-0037](../../adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md); der Bau liegt bei [slice-handle-freigabe-und-grenze-bauen](../in-progress/slice-handle-freigabe-und-grenze-bauen.md)).
- Kopplung des Live-Laufs an die Dockerfile-Stage `kompatibilitaetsserver` und den Pfad `/home/gradle/server.pem`: kein Sensor hält sie (der Lauf ist kein Gate). Stand nach dem Modul-Umbau (`testserver/` auf `main`): Stage-Name und Pfad unverändert, `live-2022` und `live-2025` grün (Verifikation und Lauf der Closure nach dem Merge von `main`). — **Ausgang:** weiter offen (Register `BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht`).
- Eigencode ohne unterscheidenden Test (Review F-8): der Zweig `leck` und die Wartebedingung `gestartet` in `tools/vergleichslauf-d.sh` liegen hinter echtem Docker; der Selbsttest erreicht sie nicht. — **Ausgang:** weiter offen (Register `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test`; Auswirkung klein: Wegwerf-Kennwort, nur lokal).

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren** statt neu
formulieren — sonst zählt das Register zwei Namen getrennt) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln (das
Feld `liegt in` steht **nur**, wenn mit diesem Slice wirklich etwas verkörpert
wurde; Feld und Zielort auf **einer** Zeile, Sektionsangabe innerhalb der
Backticks). Ging der Gegenstand an einen anderen Slice oder entfiel er, trägt
diese Sektion die Zeile `Gegenstand:` mit Kennung oder Grund und jedes Risiko
aus §6 seinen Ausgang; die Liefer-Punkte der DoD bleiben leer
(`modul-05-planning-harness.md` §Ein Slice, dessen Gegenstand ein anderer
übernimmt).

- **Belege:** Review [2026-11-06-7cb411b2-slice-referenzabgleich-live](../../../reviews/2026-11-06-7cb411b2-slice-referenzabgleich-live.md) (0 HIGH, 5 MEDIUM, 3 LOW, 3 INFO; F-1 bis F-11), Verifikation [2026-11-06-5e01ac44-verifikation-slice-referenzabgleich-live](../../../reviews/2026-11-06-5e01ac44-verifikation-slice-referenzabgleich-live.md) (0 DoD-Verletzungen; V-1 bis V-6). Messprotokoll und Ausnahmeliste: [referenzabgleich](../../../messungen/referenzabgleich.md). Bezug: [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md). Bedingung des Laufs: die EULA-Zustimmung des Nutzers pt9912 für das Developer-Image (2026-11-01, 2026-11-06), lokal; keine Rechtsaussage des Repos.
- **Was hat funktioniert:** Der Referenzclient läuft mit 25 Schritten nacheinander gegen 2022 und 2025 und gegen den Server dieses Repos; 12 Schritte sind gleich, 13 gleich der gelisteten Abweichung. Die Nachrechnung durch Review und Verifikation stimmt (12 + 13 = 25), der Selbsttest fängt Gleichheit, gelistete, kleinere, größere und nicht gelistete Abweichung. Der Lauf lieferte die ersten Server-Referenzen für „Fehler nach Zeilen“ und die Gegenmessungen M-1 bis M-5, darunter M-4, die [ADR-0037](../../adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md) auslöste.
- **Was ging anders als geplant:** (1) Die Server-Seite wurde umgeschnitten: das Schema bleibt im Code des Integrators, nicht in der Beschreibungsdatei (Nutzerentscheidung 2026-11-06, N5); DoD 1 ist umformuliert, §3 und §6 nach der Verifikation nachgezogen (V-1). (2) Drei Abweichungen trugen „so gewollt“ ohne Beleg in Spezifikation oder ADR und stehen jetzt als Lücke (A-3, A-4/A-5, A-9; F-1, F-2, F-6). (3) Drei Lücken (A-2, A-7, A-8) nannten falsche oder keine Träger (F-3); die Träger-Vorschläge stehen im Register. (4) Die Gegenmessung M-4 widersprach einer `Accepted`-ADR (F-4); die Folge-ADR ist [ADR-0037](../../adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md).
- **Frischer Lauf der Closure:** Nach dem Merge von `main` in den Zweig: `make vergleichslauf-d LAUF=live` Exit 0, beide Teile „live: gruen (25 Schritte)“ und „Live-Vergleich: gleich oder genau die gelistete Abweichung“, 12 Zeilen gleich und 13 Ausnahmen je Instanz gezählt; Laufzeit `live-2022` 80 s bei Lastmittel 35 bis 40 (Einzelmessung, andere Last als im Protokoll). Referenz-Text bei `cancel-mit-folgeabfrage` in beiden Teilläufen die lange Variante („… Operation cancelled by user.“); die Verifikation sah bei 2022 die kurze. `make docs-check` und `make gates`: siehe Commit-Reihe der Closure.
- **Grenze der Aussage „grün“:** Der Lauf ist grün, wenn jede Abweichung mit der Liste übereinstimmt, nicht wenn der Server dem Zielbild entspricht. 13 von 25 Schritten weichen ab: 3 so gewollt (A-1 Anmeldetext, A-6 Text des Quellfehlers), 10 Lücke (A-2, A-3, A-4, A-5, A-7, A-8 mit drei Schritten, A-9 mit zwei). Verglichen ist die Ausgabe des Clients, nicht der Draht; aus „grün“ folgt nichts über Tokenfolge, Statusbits, Paketierung, ENVCHANGE, den Draht von `Cancel()`, OUTPUT und RETURNSTATUS von Prozeduren, TDS 8.0 oder andere Clients. Die Läufe sind Einzelmessungen eines Hosts mit `Microsoft.Data.SqlClient` 7.1.1, TDS 7.4.
- **Zuordnung, Stand der Closure:**

  | Aussage | Zuordnung | Wert und Grenze |
  |---|---|---|
  | 25 Schritte, 12 gleich, 13 gleich der gelisteten Abweichung | gemessen (Client gegen echten Server und Client gegen gebauten Server) | 2022 und 2025, Einzelmessungen 2026-11-06; Implementer, Verifier (2022) und Closure (beide) |
  | Server weicht in 10 Schritten als Lücke ab | gemessen, Schuld des Servers am Zielbild | Träger offen, Register, Nutzerentscheidung 2026-11-06 |
  | A-1 und A-6 „so gewollt“ | Festlegung | SPEC-012, [ADR-0009](../../adr/0009-authentifizierungsmodus.md), `TDS_BACKEND_ERROR` |
  | Cancel-Text der Referenz in zwei Varianten | gemessen, Ursache nicht untersucht | beide Instanzen beide Varianten; woher der Text kommt, ist am Draht zu klären |
  | M-4: Fehler 8179 bei unbekanntem Handle | gemessen, am Client | Draht (ProcID, Tokens) nicht gemessen; Folge: [ADR-0037](../../adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md) |
  | „frühere Läufe“ (`live-2022` grün, `live-2025` rot) im Protokoll | Berichtsaussage | ohne abgelegte Ausgabe (V-3); im Protokoll gekennzeichnet |
  | Schema des Servers gleich Skript der Referenzinstanz | nicht gehalten | kein Sensor (§6) |
  | TDS 8.0 | nicht abgeglichen | an 2022 nicht erreichbar, an 2025 in B-4 als Sonde gemessen, nicht im Live-Lauf |

- **Re-Review der Nachbesserung `5e01ac44` (V-4):** nicht nötig, begründet. Die Nachbesserung ändert die Spalten *Art* und *Grund* von neun Regeln der Ausnahmeliste, Textstellen in Protokoll und Plan und eine Pfadangabe im Kommentar von `testschema.sql`; das Verhalten des Laufs (Vergleich, Schritte, Server) ist unverändert. Der Verifier hat die Änderungen gelesen und am Stand `5e01ac44` `live-2022` und `make skript-selbsttest` gefahren (grün); die Closure fuhr beide Instanzen nach dem Merge. Offen bleibt: keine neue Mutation der Ausnahmeliste nach der Nachbesserung (der Selbsttest deckt sie ab); `live-2025` und die Gegenmessungen M-1 bis M-5 hat der Verifier nicht selbst gefahren, der Reviewer wiederholte M-4 an 2022.
- **Steering-Loop-Eintrag:** Kein Eintrag verkörpert, daher kein `liegt in`. Gelernt, gezählt und nicht verkörpert: (1) **Art ohne Beleg:** Die Art *so gewollt* braucht einen Beleg in Spezifikation oder ADR; eine Setzung des Slice, eine offene Festlegung oder eine Konstante der Fixture ist *Lücke* (F-1, F-2, F-6; `BEO-TDS/aussagen-ueber-messung-hinaus`, `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`). (2) **Lücke ohne bauenden Träger:** Eine Lücke der Liste verlangt „Verweis auf offene Arbeit“; ein Slice, der sie nicht nennt, ist kein Träger (F-3; sechs neue Beobachtungen, siehe Register). (3) **Messung gegen Accepted-ADR:** Eine Gegenmessung kann eine Setzung einer `Accepted`-ADR widerlegen; der Pfad ist die Folge-ADR mit wörtlich benanntem `Supersedes` (F-4, [ADR-0037](../../adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md), `AGENTS.md` §3.5 gewahrt). (4) **Plan nach Plan-Änderung:** Nach einer Änderung des Liefer-Punkts blieben §3 und das erste Risiko in §6 stehen (V-1; `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`, Evidence ergänzt). (5) **Messklasse:** Der Lauf misst „Client gegen echten Server“ und „Client gegen gebauten Server“, beides am Client-Ausgang, nicht am Draht (`BEO-TDS/spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar`, Evidence ergänzt). (6) **Nichtdeterminismus der Referenz:** Der Server liefert für denselben Schritt zwei Texte; die Liste führt zwei Regeln, ein dritter Text macht den Lauf rot (F-9; `BEO-TDS/cancel-antwort-text-weicht-vom-echten-server-ab`). (7) **Kopplung ohne Gate:** ein Lauf außerhalb der Gates hält seine Kopplungen nicht (F-5, F-11; `BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht`). Als benannte Spec-Lücke bleiben: `SPEC-045` nennt eine Instanz der Version 2022 und die Quelle des Skripts als ungeregelt (Träger des ersten Teils [slice-referenzabgleich-adr-0028-nachzug](../open/slice-referenzabgleich-adr-0028-nachzug.md); N5 benennt keinen Träger), `SPEC-050` führt den Live-Lauf als „noch einzurichten“ im E2E-Durchgang, gewollt nicht aufgenommen (V-5).
- **Beobachtungs-Register (`../observations/`):** Neu angelegt, je `offen`, je mit `evidence/slice-referenzabgleich-live.md` und Träger-Vorschlag in `state.md`: `BEO-TDS/unbekannte-tabelle-class-20-statt-16-am-server` (A-3), `BEO-TDS/anweisungsformen-des-simulations-backends-enger-als-der-server` (A-4, A-5), `BEO-TDS/cancel-antwort-text-weicht-vom-echten-server-ab` (A-7), `BEO-TDS/schemaonly-keyinfo-fmtonly-ohne-traeger` (A-8; Vorschlag [slice-katalog-und-ddl-zustand](../in-progress/slice-katalog-und-ddl-zustand.md), dessen DoD hier nicht geändert ist), `BEO-TDS/callback-zeilenzahl-ist-fixture-konstante` (A-9), `BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht` (Schema-Gleichlauf, Stage). `evidence/slice-referenzabgleich-live.md` ergänzt in `BEO-TDS/fa-10-mehrteilige-antwort-ohne-backend-traeger` (A-2, Vorschlag: eigener Folge-Slice), `BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (F-8), `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`, `BEO-TDS/aussagen-ueber-messung-hinaus`, `BEO-TDS/spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar`. Die Zähler folgen aus den Dateien; nichts verkörpert, kein Stand `verkörpert` oder `geplant` gesetzt.
- **Folge-Slices:** keine neuen angelegt. Startbar durch diese Closure: [slice-referenzabgleich-mitschnitt](../done/slice-referenzabgleich-mitschnitt.md) (Trigger „liegt in `done/`“ erfüllt; ein Verantwortlicher ist nicht eingetragen). Weiter: [slice-referenzabgleich-adr-0028-nachzug](../open/slice-referenzabgleich-adr-0028-nachzug.md), [slice-qa-vergleichslauf-d-messungen](../open/slice-qa-vergleichslauf-d-messungen.md), [slice-handle-freigabe-und-grenze-bauen](../in-progress/slice-handle-freigabe-und-grenze-bauen.md) ([ADR-0037](../../adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md)).
- **Risiken aus §6:** je ein Ausgang, siehe §6 (zwölf Risiken: eines eingetreten, drei entfallen, acht weiter offen mit Register-Verweis oder Träger).
- **Offene Entscheidungen beim Nutzer:** Bestätigung der Träger-Vorschläge im Register (A-2, A-3, A-4/A-5, A-7, A-8, A-9; für A-8 die Aufnahme in [slice-katalog-und-ddl-zustand](../in-progress/slice-katalog-und-ddl-zustand.md) oder ein eigener Slice); Nachzug von `SPEC-045` zu N5 und von `SPEC-050`; ein Sensor für Schema- und Stage-Kopplung.
- **Drei Paarungen:** Das Repo führt Wellen; die Paarungen prüft die Closure von [welle-referenzabgleich-release](../welle-referenzabgleich-release.md). Anker ↔ Folge-Slice: kein Anker gesetzt; Folge-Slices sind Dateien in `open/`. Register ↔ Closure-Notiz: alle genannten Kennungen existieren als Verzeichnis mit nicht leerem `evidence/`.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung — dort die **zwei vorgelagerten
Schritte** (sie stehen in jedem Slice-Plan, unabhängig von Modus und
Slice-Typ) und die **vier Pflichtkriterien** (Konventionen-Dichte ·
Phase-Reife · Evidenz-/Diskrepanz-Risiko · Reconciliation-Aufwand), vier und
nicht mehr.

**Der Abschnitt selbst entfällt nie.** Die zwei vorgelagerten Prüfungen laufen
in **jedem** Slice-Plan — sie hängen weder am Modus noch am Slice-Typ. Bedingt
ist allein der Modus-Begründungsblock am Ende; deshalb nennt der Titel beide
Hälften.

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Der Slice berührt Testwerkzeug, Docker-Stages und Dokumentation; keine dieser Strukturen trägt eine eigene Strukturregel (Achse 1) oder eine eigene Inventur-Linie (Achse 2) neben dem Repo insgesamt: eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, 19 Beobachtungen, Stand des Worktrees auf `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 9×, Stand *geplant* (Träger [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md)). Träger der Server-Seite für den Teil, den der Client beobachtet; die Wire-Messung trägt [slice-referenzabgleich-mitschnitt](../done/slice-referenzabgleich-mitschnitt.md).
- `BEO-TDS/vergleichslauf-d-ungeklaert` — 1×, offen (Träger [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md)). Betrifft CI-Machbarkeit, EULA und Häufigkeit des Laufs: Träger bleibt [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md), dieser Slice wartet auf deren Ergebnis.
- `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` — 7×, Stand *geplant* (Träger [slice-qa-kompatibilitaet](../done/slice-qa-kompatibilitaet.md)). Betrifft die Grenze des Laufs: TDS 8.0 ist an der Referenzinstanz nicht abgleichbar.
- `BEO-TDS/aussagen-ueber-messung-hinaus` — 16×, Stand *verkörpert* (`AGENTS.md` §3.8, Reviewer-Skill). Betrifft diesen Slice: Jede Aussage über „gleich wie die Referenz“ nennt Instanz, Version und Szenario (§6).
- `BEO-TDS/strukturtest-ohne-variation-der-eingabe` — 1×, offen. Betrifft den Test der Ausnahmeliste: verschiedene Abweichungen statt einer (§2).
- `BEO-TDS/test-stage-cache-verdeckt-wiederholung` — 2×, offen. Betrifft Tests in der Docker-Stage: gecachte grüne Läufe belegen Wiederholung nicht.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Slice und Closure-Notiz nennen `ADR-*`- und `TDS-*`-Kennungen als Link.
- Ohne Treffer: `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`, `BEO-TDS/offene-punkte-ohne-traeger`, `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung`, `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`, `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`, `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler`, `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`, `BEO-TDS/ablage-im-repo-bricht-docs-check`, `BEO-TDS/katalog-und-lock-ohne-gegenpruefung`, `BEO-TDS/java-quelle-umgeht-kotlin-regel`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/adapter-festlegungen-ohne-slice`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
