# Slice slice-adr-backend-grenzen: Grenzen des Backend-Vertrags entscheiden (Zeilendarstellung, kleine Ergebnisse, `OutOfMemoryError`, blockierendes Backend, Frist)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle — Folge-Slice von `slice-adr-backend-streaming` (`welle-entscheidungen-grundlage`); er trägt die offenen Punkte von [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), die keine Welle der funktionalen Slices schneidet. Er hat keine Closure-Bedingung außerhalb seiner DoD.

**Bezug:** [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)

**Berührte Spec-Stellen:** SPEC-002 · SPEC-031 · SPEC-035 · SPEC-033

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

**Ziel:** Fünf Punkte, die [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) nach ihrer Annahme offen führt und denen kein Slice der funktionalen Wellen einen Träger gibt, sind vom Nutzer anhand gemessener Optionen entschieden und stehen in einer Folge-ADR fest: die Zeilendarstellung samt Heap-Grenze eines materialisierenden Backends, die Grenze für „kleine“ Ergebnisse (fertige Liste), die Behandlung von `OutOfMemoryError` im Anfragepfad, Abbruch und Thread-Verbrauch eines blockierenden Backends hinter dem `Flow` und die Semantik der Frist `deadlineEpochMillis`. Eine `Accepted`-ADR bleibt unverändert (`AGENTS.md` §3.5); die Entscheidung steht deshalb in einer Folge-ADR ohne `Supersedes`.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Produktionscode (Port, Sequenzierer, Backend-Adapter) — der Slice entscheidet und baut nicht; der Code entsteht in `slice-backend-port-vertrag`, `slice-backend-sequenzierer`, `slice-backend-typabbildung` und `slice-backend-streaming-nachweis`, die die Entscheidung als Anschluss übernehmen.
- Statement- und batch-abbrechende Fehler im Strom, `Failure` in der `DONE_MORE`-Vorschau und die Reihenfolgeprüfung — `slice-backend-sequenzierer` und `slice-backend-port-vertrag` (dort mit Nutzerentscheidung geführt); dieser Slice berührt den Sequenzierer nicht.
- Messung gegen einen echten Treiber oder eine echte Query-Engine — es gibt keinen; die Spikes laufen gegen ein fingiertes Backend, und jede Aussage über echte Treiber bleibt eine Annahme (`BEO-TDS/aussagen-ueber-messung-hinaus`).
- Lastverhalten und Durchsatz im Produkt — `slice-qa-performance`; die Spikes messen Größenordnungen im Spike-Maßstab, keine Leistungszusage.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Spikes** (S1 bis S3 aus §3) durchgeführt; Messwerte, Optionen und Befund stehen als Tabelle in diesem Slice und in der Entscheidung. Der Spike-Code liegt im Scratchpad, ist temporär und nie im Repo. Beleg: die Messtabellen M1 bis M8 stehen im [Optionsbericht](../../../reviews/2026-10-30-b1a23b2-optionen-adr-backend-grenzen.md) und, mit den Grenzen des Spike, in den Messzeilen von [ADR-0013](../../adr/0013-backend-grenzen.md) (nicht in diesem Slice).
- [ ] **Entscheidung des Nutzers** zu den fünf Punkten aus §1 (Optionen aus §3), festgehalten als neue ADR (nächste freie Nummer laut ADR-Index, Status `Accepted` nach Review und Verifikation, `Bezug` auf [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), ohne `Supersedes`, Index und Geschichte nachgezogen); jede Aussage der Entscheidung trägt eine Messzeile oder ist als Setzung des Nutzers gekennzeichnet.
- [x] **Anschlussstellen** nachgezogen (Stand: Spezifikation `SPEC-002`, `SPEC-022` und die offenen Slices sind nachgeführt; die Slices in `done/` bleiben als Zeitdokumente unverändert, ihr Anschluss läuft über die Folgepflicht in [ADR-0013](../../adr/0013-backend-grenzen.md)): `SPEC-002` und `spec/spezifikation.md` `SPEC-022` (Frist, Zeilendarstellung) sowie die Slices `slice-backend-port-vertrag`, `slice-backend-sequenzierer`, `slice-backend-streaming-nachweis` und `slice-sql-batch-dispatch` (Start-Trigger, Risiken, Aufteilung der Verantwortung).

Nachweis-Pflichten für alle drei Punkte:

- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update für `spec/spezifikation.md` `SPEC-022` und `SPEC-002`, falls ein öffentlicher Vertrag berührt ist.
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
| Spikes im Scratchpad (Dockerfile je Spike, ohne Mount, Basis-Images per Digest) | neu, danach löschen | nie im Repo; Ergebnisse gehen als Tabelle in diesen Slice und in die Entscheidung |
| `docs/plan/adr/` (neue ADR) und `docs/plan/adr/README.md` | neu / update | Entscheidung, Status, Geschichte, Indexzeile |
| `spec/spezifikation.md` (`SPEC-002`, `SPEC-022`) | update | Stellen aus `Schärft:` der neuen ADR |
| `docs/plan/planning/` (die vier Backend-Slices, `slice-sql-batch-dispatch`) | update | Anschlussstellen |
| KDoc von `QueryBackend`, `QueryExecution`, `QueryEvent`, `QueryRequest`, `ResponseSequencer`, `ValueMapping`, `BufferedResponse` | Anschlussstelle, nicht Teil dieses Laufs | Nur die Sätze „offen“ mit Verweis auf [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), die [ADR-0013](../../adr/0013-backend-grenzen.md) schließt, werden nachgezogen; die zwei weiteren Sätze in `QueryEvent` (Fehlerarten, Fehlerklasse von `Failure`) bleiben. Träger sind Liefer-Punkte der Slices, die den zugehörigen Code liefern, erst nach der Annahme: [slice-backend-streaming-nachweis](slice-backend-streaming-nachweis.md) (zweiter und dritter Punkt) und [slice-attention-cancel-timeout](../open/slice-attention-cancel-timeout.md) (dritter Punkt, Frist); der Slice ändert bis dahin keinen Produktionscode (§1). |

**Spikes (Scratchpad, Docker-only, nichts mounten)** — gemessen wird im Maßstab von [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) (JDK 21, Kotlin, `kotlinx-coroutines-core`, `-Xmx256m`, fingiertes Backend); der Nutzer sieht die Optionen erst nach den Messwerten:

- **S1 — Zeilendarstellung und kleine Ergebnisse:** Heap je Zeile und Grenze bei `-Xmx256m` für `List<Any?>` gegen eine kompaktere Darstellung (Spalten als typisierte Arrays); Dauer bis zur ersten Zeile und Gesamtdauer für wachsende Ergebnisgrößen. Optionen für die Grenze „klein“: feste Zeilenzahl, feste Byteschätzung, keine Grenze (jedes Backend entscheidet selbst).
- **S2 — `OutOfMemoryError`:** Optionen: im Anfragepfad fangen und als Fehler melden (der Spike von [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) tat es), die Sitzung schließen, den Prozess beenden. Gemessen wird, ob der Prozess danach weitere Anfragen auf anderen Verbindungen bearbeitet und ob die Sitzung konsistent bleibt; ob das Fangen im Produktivcode tragbar ist, ist eine Setzung des Nutzers.
- **S3 — blockierendes Backend und Frist:** ein blockierender Aufruf hinter dem `Flow` (Wrapper auf `Dispatchers.IO` mit und ohne Unterbrechbarkeit): Thread-Verbrauch bei 200 Strömen, Abbruchdauer, Verhalten bei Blockade über `CANCEL_TIMEOUT`; Optionen für die Frist `deadlineEpochMillis`: der Protokollkern setzt sie durch, das Backend setzt sie durch, das Feld entfällt.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Ein Verantwortlicher ist im Kopf eingetragen und der Nutzer steht für die Wahl der Optionen aus §3 bereit. Der Slice braucht keinen Code der Backend-Slices; er läuft vor `slice-backend-port-vertrag` und `slice-backend-streaming-nachweis`, weil deren Risiken auf ihn zeigen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2; Schnittvorschlag: S3 (blockierendes Backend und Frist) als eigener Slice.
- `in-progress` → `open` (blockiert — Carveout?): Der Nutzer entscheidet nicht, oder ein Spike lässt sich ohne echten Treiber nicht aussagekräftig bauen und die Entscheidung bliebe eine reine Setzung.

## 5. Closure-Trigger

Alle Punkte der DoD in §2 erledigt, `make gates` grün und die Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben. Jede Aussage der Entscheidung trägt eine Messzeile (S1 bis S3) oder ist als Setzung des Nutzers gekennzeichnet (`BEO-TDS/aussagen-ueber-messung-hinaus`).

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Die Messung stammt von einem fingierten Backend; ein echter Treiber verhält sich beim Abbruch und bei der Zeilenform anders (`BEO-TDS/aussagen-ueber-messung-hinaus`). — **Ausgang:** offen bis zur Closure.
- Die Entscheidung zur Frist berührt `slice-sql-batch-dispatch` (die Frist bleibt dort `null`) und `slice-attention-cancel-timeout` (`TDS_TIMEOUT`, `SPEC-016`); ohne Abstimmung entstehen zwei Auslöser für denselben Fehler. — **Ausgang:** offen bis zur Closure.
- Die Dauer eines blockierenden Aufrufs und der Thread-Verbrauch sind an Image und Hardware des Spikes gemessen und gelten nicht für das Produkt. — **Ausgang:** offen bis zur Closure.
- Die JVM-Typen der Werte je `SqlType` sind hier zu entscheiden (Offen 10 von [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md)): Die Überführungsregel des Simulations-Backends (streng und verlustfrei, Textvergleich, `compareTo` für Dezimalzahlen) gilt für sie und ist bei einer anderen Zeilendarstellung neu zu prüfen; die Entscheidung nennt die Auswirkung auf SPEC-035. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield); der Slice berührt Spec, Vertragsdatei und ADR, eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/`) durchgegangen. Treffer: `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert; Grund für die Messzeilen-Pflicht in §5) und `BEO-TDS/offene-punkte-ohne-traeger` (dieser Slice ist der Träger der fünf Punkte aus `slice-adr-backend-streaming`).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
