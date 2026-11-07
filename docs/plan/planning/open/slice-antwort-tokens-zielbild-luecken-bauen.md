# Slice slice-antwort-tokens-zielbild-luecken-bauen: Zielbild-Lücken der Antwort-Tokens bauen: Flag-Feld der Ausgabewerte, `curCmd` des Änderungszählers, von `EXEC sp_unprepare` und nach Fristablauf, Tokenfolge von `sp_unprepare`

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md)

**Berührte Spec-Stellen:** SPEC-030 (Flag-Feld des RETURNVALUE von Ausgabewerten des Backends, `curCmd`, Tokenfolge der Handle-Freigabe) · SPEC-031 (Abschluss der Antwort, `curCmd`) · SPEC-059 (öffentliche Fläche, falls der Port eine Anweisungsart trägt) · SPEC-060 (Antwortform bei `sp_unprepare`, nur Tokenfolge). **Die Spezifikation ändert dieser Slice** (Spezifikation ist Zielbild; die „bekannte Abweichung“ entfällt).

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-07.

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Die Antwort-Tokens des Servers dieses Repos stimmen mit den gemessenen des echten SQL Servers überein, wo die Spezifikation es als Zielbild verlangt: (1) das Flag-Feld des RETURNVALUE aller Ausgabewerte des Backends ist `0x0000` (10 gemessene Typen, mit Wert, NULL und ohne Zuweisung, M-24); (2) `curCmd` des Abschlusses nach einer Änderung: INSERT `0x00c3`, DELETE `0x00c4`, UPDATE `0x00c5` ([Messreihe am Draht](../../../messungen/referenzabgleich.md#messreihe-am-draht-messungen-vom-2026-11-06), W-6), und nach Fristablauf bei offenem Ergebnis `0x00c1`; (3) `EXEC sp_unprepare` schließt mit `RETURNSTATUS` und `DONEPROC` Befehl `0x00e0`, ohne `DONE` (M-23), im Fehlerfall je Nummer `ERROR`, `RETURNSTATUS 8179`, `DONEPROC`.

**Herkunft.** Aus dem Auflösungs-Trigger „nach der A-20-Messung wird der Bau-Slice angelegt“ in [slice-qa-vergleichslauf-d-messungen-bulk-merge](../done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md) §2 (eingetreten: M-23 und M-24 des [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07)) und aus dem Register [`BEO-TDS/zielbild-luecken-der-antwort-tokens`](../observations/BEO-TDS/zielbild-luecken-der-antwort-tokens/state.md). Nutzerentscheid 2026-11-07 (per Rückfrage an den Nutzer, Beleg im Register-Eintrag dort). Nutzerentscheid: Bau-Slice anlegen für Flag-Feld `0x0000` aller Backend-Ausgabewerte (10 gemessene Typen), `curCmd` des Update-Zählers (INSERT, DELETE, UPDATE), `curCmd` von `EXEC sp_unprepare` (`DONEPROC` `0x00e0`), `curCmd` nach Fristablauf bei offenem Ergebnis (`0x00c1`) und die Tokenfolge von `sp_unprepare`. **Umplanung** gegenüber dem Plan von [slice-antwort-tokens-an-messung-angleichen](../done/slice-antwort-tokens-an-messung-angleichen.md) (Nr 8, Lücken ohne Bau): im Drift-Log der Roadmap eingetragen. Die Spezifikation ist Zielbild, der echte SQL Server die Referenz (Nutzergrundsatz 2026-11-06). Die Messung [slice-qa-vergleichslauf-d-messungen-bulk-merge](../done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md) liegt vor (Einzelmessungen je Instanz 2022 und 2025, Referenzclient `Microsoft.Data.SqlClient`, nicht mssql-jdbc von d-migrate).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Menge der gemeldeten Nummern bei `sp_unprepare` — [slice-sp-unprepare-ohne-obergrenze](slice-sp-unprepare-ohne-obergrenze.md); beide Slices berühren die Batch-Freigabe (gleicher Pfad), wer später startet, zieht nach.
- SESSIONSTATE (Nr 8 des Vorgängers): „vorerst nicht senden“, Nutzerentscheid 2026-11-06, Re-Evaluierungs-Trigger dort; unberührt.
- Die Ursache des SESSIONSTATE-Unterschieds nach dem Abbruch (nur Instanz 2022, Zustandskennung 254) und `COLMETADATA` hinter `RETURNVALUE` — Lücken ohne Träger (Nutzerentscheid 2026-11-06).
- Der Abschluss von `CREATE TABLE` und `ALTER TABLE` (SQL_BATCH, `0x00c6`, `0x00d8`) — [slice-katalog-und-struktur-an-messung-angleichen](slice-katalog-und-struktur-an-messung-angleichen.md).

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Flag-Feld der Ausgabewerte** ([TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): Das Flag-Feld des RETURNVALUE jedes Ausgabewerts des Backends (`int`, `smallint`, `bigint`, `bit`, `decimal`, `float`, `nvarchar`, `date`, `time`, `datetime2`; mit Wert, NULL und ohne Zuweisung) ist `0x0000` (M-24); `SPEC-030` ohne die bekannte Abweichung; je Typ ein Test.
- [ ] **`curCmd` des Änderungszählers und nach Fristablauf** ([TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): Der Abschluss nach INSERT, DELETE und UPDATE trägt `0x00c3`, `0x00c4`, `0x00c5`, nach Fristablauf bei offenem Ergebnis `0x00c1` (W-6); dafür trägt der Port die Anweisungsart. **Fläche:** Braucht der Port einen neuen öffentlichen Namen (Anweisungsart im Ergebnis des Callbacks), ist das öffentliche Fläche nach `SPEC-059` ([ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md), `make flaeche`); Rückführung `in-progress` → `next`, Entscheidung über die Fläche vor dem Bau. Tests je Anweisungsart.
- [ ] **`sp_unprepare`** ([TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): Erfolg schließt mit `RETURNSTATUS` 0 und `DONEPROC` Status 0, Befehl `0x00e0`, ohne `DONE`; unbekannte Nummer: `ERROR`, `RETURNSTATUS 8179`, `DONEPROC` (`MORE|ERROR` in der Mitte), Befehl `0x00e0` (M-22, M-23); `SPEC-030`, `SPEC-060` und die Beobachtungen [`BEO-TDS/zielbild-luecken-der-antwort-tokens`](../observations/BEO-TDS/zielbild-luecken-der-antwort-tokens/state.md) sind angeglichen; Tests auf dem Draht, Belegtest gegen die Ablage der Messung.

Nachweis-Pflichten für alle Punkte:

- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)


Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/` (Antwortbildung, Sequenzierer, Port der Änderung) | update | Flag-Feld, `curCmd`, Tokenfolge |
| `testserver/kotlin/` (Integrator, falls die Anweisungsart zurückgegeben wird) | update (falls betroffen) | Anteil des Integrators |
| `spec/spezifikation.md` (`SPEC-030`, `SPEC-031`, `SPEC-059`, `SPEC-060`) | update | Zielbild, bekannte Abweichungen entfallen |
| Kompatibilitätsmatrix der Spezifikation, `docs/messungen/` (Ausnahmeliste A-20) | update | Stand des Servers gegen den echten |
| Tests (Kotlin; Referenztest .NET nur lesend) | neu / update | Happy/Boundary/Negative nach [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte) |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-qa-vergleichslauf-d-messungen-bulk-merge](../done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md) liegt in `done/` (erfüllt mit der Closure dieses Slice: Messungen M-23 und M-24 liegen vor) und [slice-antwort-tokens-an-messung-angleichen](../done/slice-antwort-tokens-an-messung-angleichen.md) liegt in `done/` (erfüllt).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): die Fläche wächst oder mehr als drei Liefer-Punkte; Schnittvorschlag: Flag-Feld | `curCmd` mit Port | `sp_unprepare`.
- `in-progress` → `open` (blockiert): die Anweisungsart im Port braucht eine Entscheidung über die öffentliche Fläche, die der Nutzer treffen muss.

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle drei Liefer-Punkte der DoD erledigt, `make gates` grün (auch `make flaeche`), der Review-Report liegt vor, die Zeilen M-23 und M-24 sind im [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07) von „widerspricht“ auf „angeglichen“ gezogen, die Closure-Notiz trägt den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Die Anweisungsart im Port kann neue öffentliche Fläche bedeuten (`SPEC-059`, `make flaeche`); ohne den Port bleibt `curCmd` des Update-Zählers eine Lücke. — **Ausgang:** offen bis zur Closure.
- Gemessen sind Einzelfälle je Instanz mit dem Referenzclient; `curCmd` von weiteren Anweisungsarten (MERGE `0x0117`, SELECT) ist nur dort gebaut, wo W-6 und M-13 messen. — **Ausgang:** offen bis zur Closure.
- Der gemeinsame Pfad mit [slice-sp-unprepare-ohne-obergrenze](slice-sp-unprepare-ohne-obergrenze.md) erzeugt bei gleichzeitigem Lauf Konflikte. — **Ausgang:** offen bis zur Closure.

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

Wird bei der Closure gefüllt.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); eine eigene Sub-Area besteht nicht.

**Vorgelagert — offene Beobachtungen sichten:** [`BEO-TDS/zielbild-luecken-der-antwort-tokens`](../observations/BEO-TDS/zielbild-luecken-der-antwort-tokens/state.md) (Gegenstand), [`BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server`](../observations/BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server/state.md).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
