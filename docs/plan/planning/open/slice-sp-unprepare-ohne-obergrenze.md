# Slice slice-sp-unprepare-ohne-obergrenze: Obergrenze von 100 gemeldeten Nummern je Batch aufheben (`SPEC-060` an M-22 angleichen) und den Schutz nach TDS-QA-02 anders tragen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md), [ADR-0037](../../adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md)

**Berührte Spec-Stellen:** SPEC-060 (Fehlerzeile `TDS_SIM_UNKNOWN_PREPARED_HANDLE`: „höchstens für die ersten 100 Nummern“, Log „höchstens 100 Einträge je Batch“, Auflösungs-Trigger der Setzung) · SPEC-030 (Abschnitt Handle-Freigabe) · Abschnitt Grenzen und Stabilität der Spezifikation, soweit er den Schutz trägt. **Die Spezifikation ändert dieser Slice** (Teil des Auftrags: Spezifikation ist Zielbild).

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

**Ziel:** Der Server dieses Repos meldet für `EXEC sp_unprepare <n>` mit vielen unbekannten Nummern in einem Batch jede Nummer, wie der echte SQL Server es nach M-22 tut (100, 101 und 150 Nummern auf dem Draht, 1 bis 2000 am Client); `SPEC-060` ist an den Messwert angeglichen, und der Schutz nach [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität) ist durch einen anderen Träger der Grenze erfüllt: Größe der Nachricht, Zeitbegrenzung und Zählung, ohne dass die Antwort mit der Zahl der Nummern ungebremst wächst.

**Herkunft.** Aus dem Auflösungs-Trigger der Setzung in `SPEC-060` (eingetreten durch die Zusatzmessung M-22 des [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07)). Träger-Register: [`BEO-TDS/obergrenze-gemeldete-nummern-je-batch-weicht-vom-echten-server-ab`](../observations/BEO-TDS/obergrenze-gemeldete-nummern-je-batch-weicht-vom-echten-server-ab/state.md). Nutzerentscheid 2026-11-07 (per Rückfrage an den Nutzer, Beleg im Register-Eintrag dort). Nutzerentscheid: die Obergrenze 100 aufheben, `SPEC-060` an den Messwert angleichen, der Server meldet jede unbekannte Nummer; der Schutz nach [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität) wird anders getragen. Die Spezifikation ist Zielbild, der echte SQL Server die Referenz (Nutzergrundsatz 2026-11-06). Die Messung [slice-qa-vergleichslauf-d-messungen-bulk-merge](../done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md) liegt vor (Einzelmessungen je Instanz 2022 und 2025, Referenzclient `Microsoft.Data.SqlClient`, nicht mssql-jdbc von d-migrate).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Tokenfolge und der `curCmd` der Antwort auf `EXEC sp_unprepare` (`RETURNSTATUS`, `DONEPROC`, Befehl `0x00e0`, kein `DONE`) — [slice-antwort-tokens-zielbild-luecken-bauen](slice-antwort-tokens-zielbild-luecken-bauen.md); beide Slices berühren denselben Pfad (Batch-Freigabe), wer später startet, zieht die Antwortform des anderen nach.
- Handles je Sitzung, Speicher je Handle und die Grenze der Handles ([ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md)) — geliefert in [slice-handle-freigabe-und-grenze-bauen](../done/slice-handle-freigabe-und-grenze-bauen.md); dieser Slice ändert nur die Meldung unbekannter Nummern.
- Eine Messung über 2000 Nummern am Client hinaus oder auf dem Draht über 150 — nicht gemessen (Grenze der Messung M-22); der Schutz muss mit beliebig großen Nachrichten rechnen.

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Spezifikation und Antwort** ([TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): `SPEC-060` nennt keine Obergrenze der Meldungen und keine Begrenzung der Log-Einträge je Batch mehr; die Zeile trägt den Messwert M-22 (Draht: 100, 101, 150; Client: bis 2000) als Messvermerk mit der Grenze „Einzelmessung je Instanz“; die „Setzung, nicht gemessen“ der Obergrenze entfällt, der Auflösungs-Trigger ist als erfüllt vermerkt; die Bibliothek meldet je unbekannter Nummer einen Fehler; Tests an den Rändern 99, 100, 101, 150 und 2000.
- [ ] **Schutz nach [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)** ([TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)): Die Wahl des Trägers (Größe der Nachricht, Zeitbegrenzung, Zählung ohne Antwort-Explosion) ist im Slice begründet und belegt: ein Batch mit mindestens 2000 unbekannten Nummern endet in begrenzter Zeit und mit begrenztem Speicher, die Verbindung bleibt offen, die Folgeabfrage gelingt; Messung mit Zahlen (Zeit, Heap), kein Satz ohne Messzeile (`AGENTS.md` §3.8). Wird die Grenze zu einer neuen Konfiguration oder einem neuen öffentlichen Namen, ist die Rückführung benannt.
- [ ] **Nachzug** ([TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit)): Nutzer-Doku und Kompatibilitätsmatrix nennen die Obergrenze nicht mehr als Grenze; Beobachtung `BEO-TDS/obergrenze-gemeldete-nummern-je-batch-weicht-vom-echten-server-ab` ist fortgeschrieben.

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
| `src/main/kotlin/io/github/pt9912/tdswire/` (Batch-Freigabe, Meldung unbekannter Nummern, Schutz der Nachricht) | update | Obergrenze entfällt, anderer Träger der Grenze |
| `spec/spezifikation.md` (`SPEC-060`, `SPEC-030`, Abschnitt Grenzen) | update | Zielbild folgt dem Messwert |
| `docs/user/`, Kompatibilitätsmatrix der Spezifikation | update | Aussage über die Obergrenze entfällt |
| Tests (Kotlin) | neu / update | Ränder 99/100/101/150/2000, Last-Beleg des Schutzes |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-qa-vergleichslauf-d-messungen-bulk-merge](../done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md) liegt in `done/` (erfüllt mit der Closure dieses Slice) und ein Verantwortlicher ist eingetragen. Kein weiterer Slice als Voraussetzung.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): die Wahl des Trägers des Schutzes verlangt eine ADR (neue Konfiguration oder Fläche); Schnittvorschlag: ADR | Bau.
- `in-progress` → `open` (blockiert): der Schutz lässt sich ohne die Obergrenze nicht belegen; der Nutzer entscheidet über eine Grenze, die vom echten Server abweicht und als solche gekennzeichnet bleibt.

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle drei Liefer-Punkte der DoD erledigt, `make gates` grün, der Review-Report liegt vor, `SPEC-060` trägt keine Obergrenze mehr, die Messzeile des Schutzes (Zeit, Speicher bei 2000 Nummern) liegt vor, die Closure-Notiz trägt den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Der Server schreibt je Nummer einen ERROR, RETURNSTATUS und DONEPROC (nach [slice-antwort-tokens-zielbild-luecken-bauen](slice-antwort-tokens-zielbild-luecken-bauen.md)); die Antwort wächst mit der Zahl der Nummern, die Größe der Nachricht begrenzt sie. — **Ausgang:** offen bis zur Closure.
- Der Wert der echten Grenze (Nachrichtengröße des echten Servers) ist nicht gemessen; der Schutz des Servers dieses Repos ist eigene Setzung mit Messzeile. — **Ausgang:** offen bis zur Closure.
- Der gemeinsame Pfad mit [slice-antwort-tokens-zielbild-luecken-bauen](slice-antwort-tokens-zielbild-luecken-bauen.md) erzeugt bei gleichzeitigem Lauf Konflikte (Reihenfolge, Setzung des Planners 2026-11-08: dieser Slice zuerst, [slice-antwort-tokens-zielbild-luecken-bauen](slice-antwort-tokens-zielbild-luecken-bauen.md) startet nach seinem `done/` und zieht die Antwortform nach). — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — offene Beobachtungen sichten:** [`BEO-TDS/obergrenze-gemeldete-nummern-je-batch-weicht-vom-echten-server-ab`](../observations/BEO-TDS/obergrenze-gemeldete-nummern-je-batch-weicht-vom-echten-server-ab/state.md) (Gegenstand), [`BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server`](../observations/BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server/state.md).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
