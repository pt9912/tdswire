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

**Berührte Spec-Stellen:** SPEC-002 · SPEC-009 · SPEC-016 · SPEC-022 · SPEC-031 · SPEC-035 · SPEC-048 (geändert); SPEC-033 (nur Verweis)

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

- [x] **Spikes** (S1 bis S3 aus §3) durchgeführt; Messwerte stehen als Tabelle in der Entscheidung (Messzeilen), die Rohtabellen im Optionsbericht. Der Spike-Code liegt im Scratchpad, ist temporär und nie im Repo. Beleg: die Messtabellen M1 bis M8 stehen im [Optionsbericht](../../../reviews/2026-10-30-b1a23b2-optionen-adr-backend-grenzen.md) und, mit den Grenzen des Spike, in den Messzeilen von [ADR-0013](../../adr/0013-backend-grenzen.md). *Wortlaut geändert bei der Closure (Verifikation V6, Nachprüfung Hinweis 2): der ursprüngliche Satz verlangte die Tabelle „in diesem Slice“, die Zahlen stehen aber nur an den zwei genannten Orten (Drift-Schutz).*
- [x] **Entscheidung des Nutzers** zu den fünf Punkten aus §1 (Optionen aus §3), festgehalten als neue ADR (nächste freie Nummer laut ADR-Index, Status `Accepted` nach Review und Verifikation, `Bezug` auf [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), ohne `Supersedes`, Index und Geschichte nachgezogen); jede Aussage der Entscheidung trägt eine Messzeile oder ist als Setzung des Nutzers gekennzeichnet. Beleg: [ADR-0013](../../adr/0013-backend-grenzen.md) steht auf `Accepted` (Index und Geschichte nachgezogen, kein `Supersedes`). Gewählt hat der Nutzer die Fragen 1, 3, 4 und 5 und die Abnahme-Entscheidung zu [TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände); die Grenze für „kleine“ Ergebnisse (Frage 2) und die Setzungen 5 bis 10 hat der Nutzer **nicht ausdrücklich gewählt**, sie stehen als Setzung (Voreinstellung 2026-10-30, nicht gemessen) (siehe §7).
- [x] **Anschlussstellen** nachgezogen (Stand: Spezifikation `SPEC-002`, `SPEC-022` und die offenen Slices sind nachgeführt; die Slices in `done/` bleiben als Zeitdokumente unverändert, ihr Anschluss läuft über die Folgepflicht in [ADR-0013](../../adr/0013-backend-grenzen.md)): `SPEC-002` und `spec/spezifikation.md` `SPEC-022` (Frist, Zeilendarstellung) sowie die Slices `slice-backend-port-vertrag`, `slice-backend-sequenzierer`, `slice-backend-streaming-nachweis` und `slice-sql-batch-dispatch` (Start-Trigger, Risiken, Aufteilung der Verantwortung).

Nachweis-Pflichten für alle drei Punkte:

- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update für `spec/spezifikation.md` `SPEC-022` und `SPEC-002`, falls ein öffentlicher Vertrag berührt ist.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
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
| KDoc von `QueryBackend`, `QueryExecution`, `QueryEvent`, `QueryRequest`, `ValueMapping` (fünf Dateien mit Sätzen „offen“ mit Verweis auf [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md); `BufferedResponse` ist entfernt, die KDoc des `ResponseSequencer` angepasst) | Anschlussstelle, nicht Teil dieses Laufs | Nur die Sätze „offen“ mit Verweis auf [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), die [ADR-0013](../../adr/0013-backend-grenzen.md) schließt, werden nachgezogen; die zwei weiteren Sätze in `QueryEvent` (Fehlerarten, Fehlerklasse von `Failure`) bleiben. Träger sind Liefer-Punkte der Slices, die den zugehörigen Code liefern, erst nach der Annahme: [slice-backend-streaming-nachweis](../in-progress/slice-backend-streaming-nachweis.md) (zweiter und dritter Punkt) und [slice-attention-cancel-timeout](../open/slice-attention-cancel-timeout.md) (dritter Punkt, Frist); der Slice ändert bis dahin keinen Produktionscode (§1). |

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

- Die Messung stammt von einem fingierten Backend; ein echter Treiber verhält sich beim Abbruch und bei der Zeilenform anders (`BEO-TDS/aussagen-ueber-messung-hinaus`). — **Ausgang:** weiter offen; Register: `evidence/slice-adr-backend-grenzen.md` in `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert); die ADR führt die Grenze der Aussage und den Re-Evaluierungs-Trigger „echtes Backend, dessen Treiber blockiert“.
- Die Entscheidung zur Frist berührt `slice-sql-batch-dispatch` (die Frist bleibt dort `null`) und `slice-attention-cancel-timeout` (`TDS_TIMEOUT`, `SPEC-016`); ohne Abstimmung entstehen zwei Auslöser für denselben Fehler. — **Ausgang:** entfallen; [ADR-0013](../../adr/0013-backend-grenzen.md) Entscheidung 4 legt die Quelle (`QUERY_TIMEOUT`), den Durchsetzer (Protokollkern) und den Weg des Ablaufs fest (Weg des Cancel, Ende in `TDS_TIMEOUT`); ATTENTION und Frist gehen denselben Weg. Der Test des Dispatch ändert sich mit dem Träger [slice-attention-cancel-timeout](../open/slice-attention-cancel-timeout.md) (dritter Punkt, Teil (b)); Class und Verbindungsfolge von `TDS_TIMEOUT` sind nicht entschieden und stehen dort als eigene Risikozeile.
- Die Dauer eines blockierenden Aufrufs und der Thread-Verbrauch sind an Image und Hardware des Spikes gemessen und gelten nicht für das Produkt. — **Ausgang:** weiter offen; Register: `evidence/slice-adr-backend-grenzen.md` in `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` (offen, über der Schwelle, siehe §7).
- Die JVM-Typen der Werte je `SqlType` sind hier zu entscheiden (Offen 10 von [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md)): Die Überführungsregel des Simulations-Backends (streng und verlustfrei, Textvergleich, `compareTo` für Dezimalzahlen) gilt für sie und ist bei einer anderen Zeilendarstellung neu zu prüfen; die Entscheidung nennt die Auswirkung auf SPEC-035. — **Ausgang:** entfallen; Entscheidung 1 schreibt `List<Any?>` mit den Klassen je Spaltenart fest, eine andere Zeilendarstellung kommt nicht; die Überführungsregel gilt für diese Klassen und ist nicht neu zu prüfen ([ADR-0013](../../adr/0013-backend-grenzen.md) Konsequenzen, „Positiv“).
- *(bei der Closure ergänzt, Nachprüfung Hinweis 1)* [ADR-0013](../../adr/0013-backend-grenzen.md) Entscheidung 2 lässt die Zusage von [TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände) für den `OutOfMemoryError` als erfüllt gelten, „wenn der Fehler gefangen wird und der Betreiberschalter als Abhilfe im Betriebstext steht“; der Betriebstext ist nicht geliefert, und `SPEC-022` sagt „gilt hierfür als erfüllt“ ohne diese Bedingung. Bis zur Lieferung ist die Bedingung offen. — **Ausgang:** weiter offen; neue Beobachtung `BEO-TDS/bedingung-der-adr-fehlt-in-der-spezifikation` (Beleg `evidence/slice-adr-backend-grenzen.md`); Träger der Lieferung: [slice-release-doku-prozess](../open/slice-release-doku-prozess.md) (erster Liefer-Punkt). Die ADR wird nicht mehr geändert (`AGENTS.md` §3.5).
- *(bei der Closure ergänzt)* Die Setzungen 5 bis 10 der Entscheidung (darunter „keine Grenze für kleine Ergebnisse“) hat der Nutzer nicht ausdrücklich gewählt; mit `Accepted` sind sie ohne Folge-ADR nicht mehr änderbar. — **Ausgang:** weiter offen; Register: `evidence/slice-adr-backend-grenzen.md` in `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` (offen, über der Schwelle, Entscheidung beim Nutzer, siehe §7).

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

- **Was hat funktioniert:** Der Schnitt trug: Die Fragen 1, 3, 4 und 5 sind vom Nutzer anhand gemessener Optionen entschieden, die Entscheidung steht als [ADR-0013](../../adr/0013-backend-grenzen.md) (`Accepted`, ohne `Supersedes`), Spezifikation und Folge-Slices sind nachgeführt. Die Folge von Review (F-1 bis F-13), Verifikation (V1 bis V6) und Nachprüfung führte jede Aussage auf ihren Beleg zurück; die Verifikation fand die offene Abnahme-Frage zu [TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände) vor der Annahme, die Nachprüfung belegte ihre Übernahme.
- **Was ging anders als geplant:** (a) Der Nutzer entschied über das Lastenheft hinaus eine Abnahme-Frage (`OutOfMemoryError` fangen statt Hänger vermeiden), die der Plan nicht vorsah; die ADR trug sie erst nach der Verifikation. (b) Der Wortlaut von DoD-Punkt 1 war erfüllbar nur durch eine zweite Fassung der Zahlen; er ist bei der Closure geändert. (c) Der Betriebstext, an den die Erfüllung von [TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände) geknüpft ist, ist nicht geliefert (siehe §6). (d) Drei Slices führen „Status Proposed“ der ADR als Zustandssatz, der mit `Accepted` veraltet (Register).
- **Reports:** [Review F-1 bis F-13](../../../reviews/2026-10-31-42bd58d-adr-0013-backend-grenzen.md), [Verifikation V1 bis V6](../../../reviews/2026-10-31-c2fea13-verifikation-adr-0013-backend-grenzen.md), [Nachprüfung](../../../reviews/2026-10-31-bec92aa-nachpruefung-adr-0013.md) (V1, V2, V3, V6 erledigt, kein Blocker, zwei LOW: Bedingung ohne Spec-Gegenstück, DoD-Wortlaut — beide hier geführt).
- **Vom Nutzer nicht ausdrücklich gewählt:** die Setzungen 5 bis 10 der ADR, darunter die Grenze für „kleine“ Ergebnisse (keine Grenze, Pflichttext). Der Nutzer wurde gefragt und hat nichts geändert; sie bleiben in der ADR als „Setzung (Voreinstellung 2026-10-30, nicht gemessen)“ gekennzeichnet.

**Zuordnung je Aussage der Entscheidung.** Vier Stärken: *gemessen im Spike* heißt unter den Grenzen der ADR beobachtet — Autor der Spikes gleich Erwartender, Kotlin 2.1.20 und `kotlinx-coroutines-core` 1.10.1 (nicht die Versionen des Repos), Einzel- bis Vierfachläufe auf einem ausgelasteten Rechner, ein fingiertes Backend, nichts an einem echten Treiber oder am Produkt; *Handvektor oder Selbstkonsistenz* heißt ein Fall mit Quelle bzw. Code gegen Code oder Test gegen Fake; *Festlegung* heißt Entscheidung oder Setzung ohne Messung; *nicht gemessen* nennt, was keine Aussage trägt.

| Stärke | Aussage | Beleg |
|---|---|---|
| gemessen im Spike | Bytes je Zeile und Zeilen bis zum `OutOfMemoryError` je Darstellung (M1, M2); Zeit bis zur ersten Zeile, Strom gegen Materialisierung (M3); `OutOfMemoryError` ohne und mit Fangen und mit Prozessende, einschließlich der nicht erklärten Hänger (M5, M6, M6/C); Thread-Verbrauch und Wanddauer eines blockierenden Backends je Umhüllung, Abbruch, Folgen belegter Threads (M7, M7/Abbruch, M7/Folgen); Überschreitung der Frist je Durchsetzer (M8) | [ADR-0013](../../adr/0013-backend-grenzen.md) §Messzeilen; Rohtabellen im [Optionsbericht](../../../reviews/2026-10-30-b1a23b2-optionen-adr-backend-grenzen.md) |
| Handvektor, Selbstkonsistenz | Handvektor keiner (kein Fall mit Quelle außerhalb des Spikes). Selbstkonsistenz: das Fangen im Sequenzierer und das Schließen ohne Antwort nach einer Ausnahme im Schreiben stimmen mit dem gebauten Stand überein (Code gegen ADR), kein Test mit echtem Heap-Überlauf; die Rechnung M4 ist nachgerechnet | [Nachprüfung](../../../reviews/2026-10-31-bec92aa-nachpruefung-adr-0013.md) V2; [Verifikation](../../../reviews/2026-10-31-c2fea13-verifikation-adr-0013-backend-grenzen.md) (Nachrechnen M4) |
| Festlegung (Entscheidung des Nutzers, 2026-10-31) | Zeilendarstellung `List<Any?>` mit Klassen je Spaltenart; `OutOfMemoryError` im Anfragepfad fangen, `TDS_INTERNAL_ERROR`, Verbindung schließt, Betreiberschalter `-XX:+ExitOnOutOfMemoryError`; Abnahme-Entscheidung zu [TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände) (keine gemessene Erfüllung); Vertragspflicht „suspendierend“; Frist durch den Protokollkern aus `QUERY_TIMEOUT` | ADR §Entscheidungen des Nutzers 1 bis 4 |
| Festlegung (Setzung, Voreinstellung 2026-10-30, **vom Nutzer nicht ausdrücklich gewählt**) | keine Grenze für „kleine“ Ergebnisse, Pflichttext; die weiteren Setzungen 5 bis 10 | ADR §Setzungen |
| nicht gemessen | Verhalten eines echten Treibers; Rückstau und Heap im Produktivbetrieb unter Last; Verzögerung durch den zurückgehaltenen Abschluss; Ursache der Hänger beim Fangen; Versionen des Repos, andere Garbage Collectors, mehr als 200 Ströme | ADR §Grenze der Aussage, „Nicht gemessen“ |

**Offene Punkte der ADR mit Träger** (gegen den Inhalt der Dateien geprüft):

| Offener Punkt | Träger | Stand |
|---|---|---|
| Code des Fangens und Ersatz der puffernden Senke | [slice-backend-streaming-nachweis](../in-progress/slice-backend-streaming-nachweis.md) (erster und dritter Liefer-Punkt) | Code gebaut, Verifikation läuft; die Punkte stehen dort in DoD und §6 |
| KDoc-Sätze „offen“ mit Verweis auf [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) in fünf Dateien | derselbe Slice (zweiter und dritter Punkt); für die Frist [slice-attention-cancel-timeout](../open/slice-attention-cancel-timeout.md) (dritter Punkt) | in beiden DoD geführt |
| Frist, Zeitlimit, `TDS_TIMEOUT`; Class und Verbindungsfolge von `TDS_TIMEOUT` (nicht entschieden) | [slice-attention-cancel-timeout](../open/slice-attention-cancel-timeout.md) | DoD (dritter Punkt, Teil (b)) und §6 |
| Zeilenquelle des Simulations-Backends, Klassen je Spaltenart | [slice-sim-auswahl-streaming](../open/slice-sim-auswahl-streaming.md) | DoD und Start-Trigger (bei der Closure angepasst) |
| Betriebstext (Betreiberschalter, Pflichttext) | [slice-release-doku-prozess](../open/slice-release-doku-prozess.md) (erster Liefer-Punkt) und für den Startparameter [slice-release-artefakt-lizenz](../open/slice-release-artefakt-lizenz.md) (erster Liefer-Punkt) | in beiden DoD geführt |
| Rückstau und Heap im Produktivbetrieb, Verzögerung durch den zurückgehaltenen Abschluss | die ADR nennt den Slice für Performance; [slice-qa-performance](../open/slice-qa-performance.md) führt Rückstau, Heap und den zurückgehaltenen Abschluss weder in DoD noch in Risiko (Lasttest „Protokollschicht und Backend getrennt“); Disconnect: [slice-abbruch-ressourcen-shutdown](../open/slice-abbruch-ressourcen-shutdown.md) | **Träger unvollständig**; die Ergänzung dort ist nicht Teil dieser Closure (Dateiliste); `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` zählt 4× |
| Hilfsadapter für blockierende Quellen (Entscheidung 3) | **kein Träger**: Folge-Slice entsteht mit dem Re-Evaluierungs-Trigger der ADR („echtes Backend, dessen Treiber blockiert“); soll Entscheidung 3 dann geändert werden, ist eine Folge-ADR nötig | benannt, kein Träger („Nutzerentscheidung“ ist keiner) |
| Statement- und batch-abbrechende Fehler, `Failure` in der `DONE_MORE`-Vorschau, Reihenfolgeprüfung | weiter offen in [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), nicht Gegenstand dieser ADR | Träger wie dort |

- **Gegenstand:** entfällt (der Slice lieferte).
- **Steering-Loop-Eintrag:** Eine benannte Spec-Lücke, ohne `liegt in`, weil sie noch in keiner versionierten Spec steht: `SPEC-022` (Pflichten des Backends, `OutOfMemoryError`) führt „gilt hierfür als erfüllt“ ohne die Bedingung der ADR (Betreiberschalter im Betriebstext); die ADR lässt die Erfüllung von [TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände) daran hängen. Verankert wird sie, wenn der Nutzer oder der Planner die optionale Zusatzklausel in `SPEC-022` setzt oder der Betriebstext geliefert ist ([slice-release-doku-prozess](../open/slice-release-doku-prozess.md)); bis dahin ist sie gezählt, nicht verkörpert. Daneben vier Feststellungen, gezählt und nicht verkörpert: (1) `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` steht bei 7×, über der Schwelle, nicht verkörpert; Entscheidung beim Nutzer (Regel, Sensor, Träger-Slice oder Streichung). (2) `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` steht bei 4×, über der Schwelle, nicht verkörpert; Entscheidung beim Nutzer, der Träger in [slice-qa-performance](../open/slice-qa-performance.md) fehlt (Tabelle oben). (3) `BEO-TDS/aussagen-ueber-messung-hinaus` und `BEO-TDS/offene-punkte-ohne-traeger` stehen verkörpert (`AGENTS.md` §3.8, `implement-slice.md` Schritt 24); die Befunde F-6 und F-7 bzw. F-3 und F-4 sind die Wiederkehr nach der Verkörperung und stehen als Beleg. (4) `BEO-TDS/risikozeile-nennt-behobenen-zustand` steht bei 2× (siehe Register).
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-adr-backend-grenzen.md` ergänzt in [BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md) (F-6, F-7; Zähler 30), [BEO-TDS/offene-punkte-ohne-traeger](../observations/BEO-TDS/offene-punkte-ohne-traeger/observation.md) (F-3, F-4, Träger Rückstau und Hilfsadapter; Zähler 16), [BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt](../observations/BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt/observation.md) (Setzungen 5 bis 10; Zähler 7), [BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen](../observations/BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen/observation.md) (Zähler 4), [BEO-TDS/dod-satz-verlangt-ablage-die-der-slice-ersetzt](../observations/BEO-TDS/dod-satz-verlangt-ablage-die-der-slice-ersetzt/observation.md) (DoD-Punkt 1; Zähler 2) und [BEO-TDS/risikozeile-nennt-behobenen-zustand](../observations/BEO-TDS/risikozeile-nennt-behobenen-zustand/observation.md) (Zustandssatz „Status Proposed“ in drei Slices; Zähler 2). Neue Beobachtung mit dieser Evidence-Datei: [BEO-TDS/bedingung-der-adr-fehlt-in-der-spezifikation](../observations/BEO-TDS/bedingung-der-adr-fehlt-in-der-spezifikation/observation.md) (Nachprüfung Hinweis 1; Zähler 1).
- **Folge-Slices:** keine neuen. Der Start-Trigger von [slice-sim-auswahl-streaming](../open/slice-sim-auswahl-streaming.md) ist angepasst; welche Slices startbar werden, steht im Bericht des Planners.
- **Risiken aus §6:** alle sechs tragen genau einen Ausgang (siehe §6): zwei entfallen (Frist-Abstimmung, JVM-Typen), vier weiter offen mit Register (Messung am fingierten Backend, Dauer und Threads, Bedingung zu [TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), Setzungen ohne ausdrückliche Wahl).
- **Drei Paarungen:** Anker: der Eintrag trägt kein `liegt in`, die Paarung entfällt. Folge-Slice: jeder genannte Träger existiert als Datei in `open/` oder `in-progress/` und führt den Punkt in seinem Inhalt, mit zwei benannten Ausnahmen (Träger in [slice-qa-performance](../open/slice-qa-performance.md) unvollständig, Hilfsadapter ohne Träger). Register: jede genannte Kennung ist ein Verzeichnis, jedes mit nicht leerem `evidence/`.

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
