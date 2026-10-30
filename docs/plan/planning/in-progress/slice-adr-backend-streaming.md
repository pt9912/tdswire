# Slice slice-adr-backend-streaming: ADR-0006 entscheiden: Backend-Schnittstelle und Resultset-Streaming

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-entscheidungen-grundlage

**Bezug:** [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)

**Berührte Spec-Stellen:** SPEC-002 · SPEC-035 · SPEC-031 · ARC-006 · ARC-007 · ARC-015

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-10-29.

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) ist mit einem Nachweis entschieden und `Accepted`; die offene Frage lautet: Ergebnismodell (Materialisierung, Event-Stream, Iterator) und Fehlersemantik im Strom.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Produktionscode der gewählten Option — er entsteht in einem Slice der funktionalen Welle, weil dieser Slice entscheidet und nicht baut.
- Änderungen am Lastenheft — eine ADR schärft höchstens die Spezifikation; ein Widerspruch zum Lastenheft ist ein eigener Vorgang.

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [ ] Spike durchgeführt; seine Ergebnisse (Messwerte, Matrix, Befund) stehen in der ADR. Der Spike-Code ist temporär und nach der Entscheidung gelöscht.
- [ ] [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) auf `Accepted`, Option gewählt, Geschichte und ADR-Index nachgezogen.
- [ ] Spec-Stellen aus dem `Schärft:`-Feld der ADR an die Entscheidung angepasst.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
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
| `docs/plan/adr/0006-backend-vertrag-und-streaming.md` | update | Entscheidung, Status, Geschichte |
| `docs/plan/adr/README.md` | update | Indexzeile |
| Spike (temporär, nicht im Repo behalten) | neu, danach löschen | Ergebnisse gehen in die ADR |
| `spec/spezifikation.md` | update | Stellen aus `Schärft:` |
| `docs/plan/planning/in-progress/roadmap.md` | update | „In Arbeit“ nennt diesen Slice |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

**Zwischenstand der Messung** für [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) (Spike per Dockerfile ohne Mount; JDK 21 mit `-Xmx256m`, Kotlin 2.1.20, `kotlinx-coroutines-core` 1.10.1; Transport wie in [ADR-0003](../../adr/0003-transportframework.md) Option C, die Sitzung ist der explizite Automat aus dem Spike von [slice-adr-session-zustand](slice-adr-session-zustand.md) mit einer `CANCEL_TIMEOUT`-Frist von 5 s, ohne dass das dessen Entscheidung vorwegnimmt; Referenzclient `Microsoft.Data.SqlClient` 7.1.1 auf .NET 10 (SDK 10.0.403) im selben Container über `localhost`, `Pooling=false`, TDS 7.4 ohne TLS, wenn nichts anderes steht. Das Backend ist ein Generator mit drei Spalten (`INT`, `NVARCHAR`, `BIGINT`, rund 40 Byte je Zeile auf der Leitung) ohne Query-Engine. Die Läufe sind Einzelläufe auf einem Rechner, auf dem andere Builds liefen; nur die 5-Mio-Zeilen-Messung ist dreimal wiederholt.)

Gebaut sind die drei Ergebnismodelle hinter demselben Encoder (Zeilen in Pakete bis etwa 8 KiB, DONE-Familie, ERROR) und derselben Sitzung:

- **A**: `suspend fun materialize(Befehl): List<Part>` baut Ergebnisse als `ArrayList<List<Any?>>` vollständig im Heap; ein Fehler in einem Ergebnis verwirft dieses unvollständige Ergebnis und hängt einen Fehlerteil an, vorher abgeschlossene Ergebnisse bleiben. Dann kodiert der Encoder alles.
- **B**: `Flow<QEvent>` mit `ResultStart`, `Row`, `ResultEnd`, `UpdateCount` und `Failure` im Strom (wie die Skizze in [SPEC-002](../../../../spec/spezifikation.md#spec-002--interner-query-vertrag)); ein Antwort-Sequenzierer hält ein `ResultEnd` bzw. `UpdateCount` zurück, bis das nächste Ereignis oder das Ende bekannt ist, um das Bit `DONE_MORE` zu setzen (`b`), und eine Variante ohne diese Vorschau (`bn`: jedes Ergebnisende ohne `DONE_MORE`).
- **C**: ein blockierender `Iterator<QEvent>`; die Schleife läuft in `withContext(Dispatchers.IO)`, jedes `hasNext()`/`next()` in `runInterruptible` (`c`); `cn`: dieselbe Schleife, aber das Backend ignoriert Interrupts (Setzung, ein nicht unterbrechbarer Treiberaufruf); `cs`: die Schleife direkt auf den vier Threads des Sitzungs-Dispatchers.

Die Fehlersemantik im Strom ist in B und C ein `Failure`-Ereignis, das der Sequenzierer in ERROR plus DONE mit Fehlerstatus übersetzt, oder (Variante) das Schließen der Verbindung; eine Ausnahme aus dem Backend statt des Ereignisses ist nicht gebaut.

| Messung (Referenzclient, TDS 7.4 ohne TLS) | A materialisiert | B Flow | C Iterator (`c`) |
|---|---|---|---|
| 1 Mio Zeilen: erste Zeile / gesamt (ms) / Live-Heap-Maximum (MB) | 161 / 786 / 153 | 2 / 683 / 4 | 1 / 794 / 3 |
| 1,5 Mio Zeilen: erste Zeile / gesamt (ms) / Live-Heap-Maximum (MB) | 202 / 1146 / 228 | 1 / 693 / 4 | 2 / 1075 / 4 |
| 2 Mio Zeilen | `OutOfMemoryError` nach 1 691 118 materialisierten Zeilen (Heap 251 MB); der Client erhält einen Fehler, der Serverprozess läuft weiter | 1000 ms gesamt, 4 MB | 1364 ms gesamt, 4 MB |
| 5 Mio Zeilen, gesamt in ms (drei Wiederholungen) | `OutOfMemoryError` | 2236; 2378 / 2281 / 2170 | 3311; 3706 / 3239 / 3073 |
| 20 Mio Zeilen (rund 800 MB auf der Leitung): gesamt (ms) / Live-Heap-Maximum (MB) | nicht versucht | 9391 / 4 | 13 746 / 4 |
| Langsamer Leser (1 Mio Zeilen, 1000 gelesen, 5 s Pause): vom Backend gelieferte Zeilen / Live-Heap (MB) | 1 000 000 / 153 | 53 755 / 3 | 53 755 / 3 |
| Fehler nach Zeile 50 000 (ERROR + DONE): beim Client angekommene Zeilen / danach Folgebefehl auf derselben Verbindung | 0 / ja | 50 000 / ja | 50 000 / ja |
| Fehler vor der ersten Zeile: Zeilen / Folgebefehl | 0 / ja | 0 / ja | 0 / ja |
| Fehler nach Zeile 50 000, Verbindung schließen: Zeilen / Client-Meldung / Folgebefehl | 0 / „transport-level error“ / nein | 49 913 / „transport-level error“ / nein | 49 913 / „transport-level error“ / nein |
| Mehrere Ergebnisse (Zeilen 3, Update-Count 5, Zeilen 2, leeres Ergebnis): Resultsets / `RecordsAffected` / `ExecuteNonQuery` | 3 (3, 2, 0 Zeilen) / 5 / 5 | 3 (3, 2, 0) / 5 / 5 | 3 (3, 2, 0) / 5 / 5 |
| dasselbe mit Fehler nach dem zweiten Ergebnis | 2 Resultsets, dann `SqlException` beim `NextResult`, Folgebefehl ja | gleich | gleich |
| `Cancel()` 600 ms nach Start bei einem Backend, das 3000 ms bzw. 8000 ms blockiert: ms bis der Client fertig ist | 8 / 1 (Blockade in der Materialisierung, 0 Zeilen gelesen) | 7 / 1 | 7 / 1 |
| 500 000 Zeilen mit TDS 7.4 und TLS sowie mit TDS 8.0, Folgebefehl | ja / ja | ja / ja | ja / ja |
| Zeilen Code des Backend-Teils (A: `materialize` 26 + `produceA` 17; B: `flowFor` 24 + `produceB` 5; C: `iterFor` 25 + `produceC` 9; B und C teilen den Sequenzierer mit 15 Zeilen) | 43 | 29 + 15 | 34 + 15 |

| Messung zur Blockade und Nebenläufigkeit | A | B | C (`c`) | `cn` | `cs` |
|---|---|---|---|---|---|
| 200 gleichzeitige Verbindungen, je 5000 Zeilen mit 2 ms Backend-Wartezeit je 50 Zeilen: ok / Wanddauer (ms) | 200 / 464 | 200 / 354 | 200 / 708 | 200 / 734 | 200 / 10 757 |
| Server-Threads, Maximum seit Reset (enthält etwa 15 Grundthreads) | 16 | 16 | 94 | 93 | 15 |
| `Cancel()` bei 3000 ms Blockade: ms bis der Client fertig ist / Folgebefehl | – | – | 7 / ja | 2478 / ja | – |
| `Cancel()` bei 8000 ms Blockade: ms / Ergebnis | – | – | 1 / ja | 5006 / Sitzung schließt nach `CANCEL_TIMEOUT`, Client „transport-level error“, Verbindung zu | – |

`bn` (ohne `DONE_MORE`-Vorschau) lieferte bei der Mehrfach-Ergebnis-Antwort dasselbe wie `b`: 3 Resultsets, `RecordsAffected` 5, `ExecuteNonQuery` 5, Folgebefehl ja. Der Live-Heap ist eine Stichprobe alle 500 ms nach `System.gc()`; „0“ oder „3“ bei kurzen Läufen heißt: zwischen den Stichproben nichts Größeres beobachtet, nicht: nichts verbraucht.

Befunde:

- **A** hält das ganze Ergebnis im Heap: bei dieser Zeilenform (`List<Any?>` je Zeile; gemessen 153 MB Live-Heap bei 1 Mio Zeilen, rund 150 Byte je Zeile gegen 40 Byte auf der Leitung) und 256 MB trägt die Messung 1,5 Mio Zeilen und scheitert bei 2 Mio. Die erste Zeile kommt erst nach dem Materialisieren (160 bis 200 ms bei 1 bis 1,5 Mio). Die Grenze hängt an der Zeilendarstellung (eine kompaktere Darstellung ist nicht gebaut) und am Heap, nicht an der Option allein. Der `OutOfMemoryError` wurde im Anfragepfad gefangen und als Fehler gemeldet; der Prozess bearbeitete danach weitere Anfragen. Ob das Fangen in einem Produktivcode tragbar ist, ist nicht gemessen.
- **B und C** halten den Heap flach (höchstens 4 MB Live-Heap von 1 Mio bis 20 Mio Zeilen) und liefern die erste Zeile nach 1 bis 2 ms. Bei einem langsamen Leser lief das Backend nur 53 755 Zeilen voraus (bei etwa 40 Byte je Zeile rund 2 MB; ob das den Socket-Puffern entspricht, ist nicht geprüft); in A hatte es vor der ersten Zeile alle 1 Mio geliefert. Die gleiche Zahl bei B und C ist gemessen, nicht erklärt.
- **Fehlersemantik:** A liefert bei einem Fehler nach 50 000 von 100 000 Zeilen keine Teilzeilen (atomar), B und C liefern die 50 000 Zeilen und dann den Fehler. In allen drei bleibt die Verbindung nach ERROR + DONE benutzbar. Beim Schließen der Verbindung statt ERROR kamen bei B und C 49 913 von 50 000 Zeilen an (die letzten, noch nicht gesendeten Zeilen im Paketpuffer gehen verloren), bei A keine; der Client meldet „transport-level error“ und die Verbindung ist danach nicht benutzbar. Welche Semantik das Lastenheft verlangt, ist keine Messfrage.
- **Durchsatz:** C war bei 5 Mio und 20 Mio Zeilen langsamer als B (5 Mio: 3073 bis 3706 ms gegen 2170 bis 2378 ms; 20 Mio: 13 746 gegen 9391 ms, +46 %), bei 1 Mio kaum (794 gegen 683 ms). Die Ursache ist nicht isoliert (je Zeile zwei `runInterruptible`-Aufrufe und der `iterator`-Builder sind Kandidaten); ein Iterator, der Zeilenblöcke statt einzelner Zeilen liefert, ist nicht gebaut.
- **Blockierung:** Ein blockierendes Backend hält in C einen Thread je laufendem Aufruf: bei 200 Strömen stieg die Zahl der Server-Threads von 16 auf 94 (`Dispatchers.IO`), die Wanddauer lag bei 708 ms gegen 354 ms bei B. Auf den vier Sitzungs-Threads (`cs`) dauerten die 200 Ströme 10,8 s (30-fach) bei 15 Threads; ob dabei andere Verbindungen verhungern, ist nicht gemessen. Beim Abbruch hängt C am Interrupt-Verhalten des Backends: unterbrechbar kam der Client nach 1 bis 7 ms fertig wie bei B, nicht unterbrechbar (`cn`) erst nach dem Ende der Blockade (2478 ms bei 3000 ms), und bei einer Blockade über `CANCEL_TIMEOUT` ([SPEC-033](../../../../spec/spezifikation.md#3-defaults-und-konstanten)) schloss die Sitzung die Verbindung nach 5 s. Wie sich echte Backends verhalten, ist eine Annahme des Spike.
- **DONE_MORE:** Ohne Vorschau (`bn`) akzeptierte der Referenzclient die Mehrfach-Ergebnis-Antwort in diesen Szenarien unverändert. Das zeigt, dass dieser Client das Bit hier nicht braucht, nicht, dass es entbehrlich ist; die Spezifikation des Protokolls verlangt es, und ein echter SQL Server wurde nicht gemessen. Die Vorschau kostet in B und C einen zurückgehaltenen Abschluss, dessen Verzögerung nicht gemessen ist.
- Der Sequenzierer des Spike vertraut der Reihenfolge der Ereignisse: unzulässige Ereignisfolgen aus dem Backend (Zeile vor `ResultStart`, doppeltes `ResultEnd`) wurden **nicht** erzeugt und eine Prüfung der Reihenfolge ([SPEC-031](../../../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität)) ist nicht gebaut.

Empfehlung des Agenten (**Empfehlung, keine Entscheidung**): Option B. Sie ist in der Messung die einzige, die zugleich den Speicher unabhängig von der Ergebnisgröße begrenzt (A scheitert bei 2 Mio Zeilen), die erste Zeile sofort liefert, ohne einen Thread je Backend-Aufruf auskommt (C: 94 gegen 16 Threads bei 200 Strömen) und den Abbruch ohne Annahme über das Backend schnell abschließt (C nur bei unterbrechbarem Backend); die Skizzen von Backend-Vertrag und Zeilenquelle sind bereits suspendierend ([SPEC-002](../../../../spec/spezifikation.md#spec-002--interner-query-vertrag), [SPEC-035](../../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends)). Kleine Ergebnisse als Ereignisse aus einer fertigen Liste zu liefern ist in B möglich und macht A zum Sonderfall statt zur Option. Die Kosten von B in der Messung: die Fehler- und Abbruchsemantik liegt im Sequenzierer (Vorschau für `DONE_MORE`, Teilergebnisse vor dem Fehler), den A nicht braucht. Die Fehlersemantik (Teilzeilen vor ERROR, oder Verbindungsende) bleibt eine Entscheidung, die die Messung nicht trifft.

Die Messung trägt **nicht**: eine echte Query-Engine oder einen echten Treiber (Blockierverhalten und Interrupts sind Annahmen), breitere Zeilen, LOBs und PLP, mehr als drei Spalten, RETURNVALUE/OUTPUT-Parameter im Strom, einen blockweise lieferenden Iterator, eine kompaktere Zeilendarstellung in A, andere Garbage Collectors, mehr als 200 Verbindungen, Latenz-Perzentile, Langzeitlauf, die Antworten eines echten SQL Servers (auch zum Bit `DONE_MORE`), MARS, Transaktionen und `QUERY_TIMEOUT`/Deadline im Backend-Vertrag. Der Referenzclient las bei den großen Ergebnissen jeweils bis zum Ende; Szenarien, in denen er nach wenigen Zeilen schließt (`Dispose` des Readers), sind nicht gemessen.

**Entscheidung des Nutzers (2026-10-30):** Ergebnismodell B (`Flow` von Ereignissen) mit einem Ereignis Vorschau für `DONE_MORE`; kleine Ergebnisse dürfen als fertige Liste ausgegeben werden. [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) trägt Messung, Entscheidung und die offenen Punkte (Fehlersemantik im Strom samt statement- und batch-abbrechenden Fehlern und Zusammenspiel mit der Vorschau, Reihenfolgeprüfung, Grenze für kleine Ergebnisse, Abbruch eines blockierenden Backends hinter dem Flow, Semantik der Frist) und bleibt `Proposed` bis Review und Verifikation; `SPEC-002`, `SPEC-022` und `contracts/query-backend.md` sind nachgezogen.

**Nachführung (Entscheidung des Nutzers, 2026-10-30):** Fehlersemantik im Strom wie `SPEC-031`: ERROR und DONE nur, solange der Strom synchron ist, sonst Schließen ohne Abschluss (Setzung). Das Feld `deadlineEpochMillis` ist bestätigt, seine Semantik bleibt offen. Offen mit Zuständigkeit Backend-/Mapping-Slice, nach Messung: ob ein `Failure` in der `DONE_MORE`-Vorschau als „nächstes Ereignis“ zählt, und die Abbildung statement- (R04) und batch-abbrechender Fehler (R05 bis R07) im Strom. Weiter offen: Prüfung der Ereignisreihenfolge, Grenze für „kleine“ Ergebnisse, `OutOfMemoryError`-Umgang. [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) und `contracts/query-backend.md` sind nachgeführt.

Wiederholung: die Spike-Dateien (`Dockerfile`, `kotlin/`, `dotnet/`, `run.sh`) liegen nur im Scratchpad des Spike-Agenten und gehören nicht ins Repo; `docker build --progress=plain .` im Verzeichnis führt Server und Referenzclient je Option aus und gibt das Protokoll im Build-Log aus. Die Zahlen der Tabellen stammen aus zwei Läufen desselben Spike: dem zweiten Hauptlauf mit allen Szenarien und einem Korrekturlauf für die Mehrfach-Ergebnis-Antwort und TLS. Ein erster Hauptlauf war nicht verwertbar und wurde wiederholt, weil der Client die Folgeanfrage falsch prüfte (erwartete Zeile 1 statt 0) und der Testserver den Update-Count mit dem Befehlscode eines `SELECT` sendete (`RecordsAffected` blieb −1); außerdem lief das Warten des nicht unterbrechbaren Backends in 20-ms-Schritten, was die 200-Ströme-Messung für `cn` verfälschte (6330 ms; nach der Korrektur auf 1-ms-Schritte 734 ms).

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `slice-adr-transport` liegt in `done/`.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Die Prototypen für große Resultsets hängen am Backpressure des Transports.

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün und die Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Die Prototypen für große Resultsets hängen am Backpressure des Transports. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield); der Slice berührt Spec und ADR, eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/` trägt nur die `README.md`): keine Treffer.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
