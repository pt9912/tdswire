# Slice slice-stempel-mechanismus: Stempel-Skript und geprüfte Eingangslisten: pruefe, schreibe, einrichten, COPY-Abdeckung mit Mutation

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle (Begründung in §1)

**Bezug:** [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md), [ADR-0023](../../adr/0023-gate-klassen-und-stempel.md), [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)

**Berührte Spec-Stellen:** —

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-11-01.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Ein Skript `tools/stempel.sh` mit den Schritten `pruefe`, `schreibe` und `einrichten` und zwei geprüfte Eingangslisten (`tools/stempel/werkzeug.eingaenge`, `tools/stempel/tests.eingaenge`) liegen vor ([ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Entscheidung 4, 5 und 10). Ein Selbsttest belegt jeden Fehlerpfad des Skripts einzeln (jeder endet im Lauf, nie im Überspringen) und, dass jede `COPY`-Quelle einer Stage der Klasse von einem Listeneintrag überdeckt ist, mit einer Mutation, die rot wird. Verdrahtet wird noch nichts: kein Gate ändert sein Verhalten; das tut [slice-stempel-migration](../next/slice-stempel-migration.md). Der Slice geht voran, weil [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) verlangt, dass ohne diesen Selbsttest kein Stempel geschrieben wird (Folgepflicht 0).

**Zuordnung zur Welle (Setzung des Planners): ohne Welle.** (1) Die Laufzeit der Gates ist ein Wunsch des Nutzers, kein Teil einer Anforderung des Lastenhefts ([ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Kontext); [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) steht als Bezug, weil die Stempel sie nicht schwächen dürfen (die CI läuft voll, lokal wird nur mit Beleg übersprungen, `AGENTS.md` §3.6), nicht weil sie einen Beleg dafür liefern. (2) `welle-qualitaet-grundlage` will, dass jede der sieben Qualitätsanforderungen eine laufende, belegte Messung hat; die Stempel tragen dazu nichts bei, und ein weiterer Slice in der Tabelle der laufenden Welle verlängerte ihren Abschluss. (3) Es gibt keine Closure-Bedingung, die von den DoDs der beiden Stempel-Slices verschieden wäre (Modul 6: sonst keine Welle). Die Alternative, beide Slices in `welle-qualitaet-grundlage` aufzunehmen, ist aus (2) nicht gewählt.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Gruppenschritte in den Make-Fragmenten, Zuordnung der Gates zu den zwei Klassen, Migration von `detekt-selbsttest.ok` und `referenztest-dotnet.hash`, Prüfung der Zuordnung und Nachmessung der Laufzeiten — [slice-stempel-migration](../next/slice-stempel-migration.md); hier ändert kein bestehendes Gate sein Verhalten, damit der Selbsttest vor dem ersten geschriebenen Stempel steht.
- Die CI-Seite ([ADR-0023](../../adr/0023-gate-klassen-und-stempel.md) Entscheidung 7: ein grüner Check `gates` an einem Vorfahren belegt alle Klassen) — dritter Schritt der Reihenfolge ([ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Entscheidung 9). Es gibt dafür keinen Slice: sie verlangt das Leserecht `checks: read` für `gates.yml` und die Abfrage des Check-Status mit Ausfallpfad (Risiko 6 von [ADR-0023](../../adr/0023-gate-klassen-und-stempel.md)) und ist eine eigene Entscheidung des Nutzers über den Rechtezuwachs. Der Planner legt den Slice an, wenn [slice-stempel-mechanismus](slice-stempel-mechanismus.md) und [slice-stempel-migration](../next/slice-stempel-migration.md) in `done/` liegen und der Nutzer die CI-Seite verlangt.
- Notes zu pushen oder zu holen — nicht gewollt (Entscheidung des Nutzers, 2026-11-01); ein anderer Klon und die CI laufen voll.
- Lasttest-Gates und Gates mit verstecktem Eingang (Netz, Uhrzeit, Last) — laufen immer und stehen in keinem Stempel-Umfang ([ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Entscheidung 6); die Aufnahmebedingung ist eine Setzung ohne Sensor ([ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md), Abschnitt „Fitness Function“), hier nicht gebaut.
- Weitere Stempel oder Aufteilung der Klassen — [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Re-Evaluierungs-Trigger, dann neue Entscheidung.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [x] **Eingangslisten und ihre Prüfung** ([ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Entscheidung 10, Folgepflicht 0): `tools/stempel/werkzeug.eingaenge` und `tools/stempel/tests.eingaenge` mit dem Anfangsinhalt aus Entscheidung 10 (ein Pfad je Zeile, Verzeichnisse gelten rekursiv, `#` beginnt einen Kommentar; beide Listen und `tools/stempel.sh` stehen in den Listen **beider** Stempel). Ein Selbsttest (Target `stempel-eingaenge`, kein Gate bis zur Migration, §7) liest das `Dockerfile` zeilenweise (`FROM <image> AS <name>` beginnt eine Stage, `COPY` und `ADD` ohne `--from=` sind ihre Eingänge, `FROM <vorige-stage> AS` vererbt) und prüft: jede Quelle einer Stage der Klasse `tests` (`test`, `compile`, `detekt`, `build-jar`, gegebenenfalls `smoke`, samt Vorgänger-Stages) und das `COPY` von `referenztest-dotnet/Dockerfile` ist von einem Eintrag in `tests.eingaenge` überdeckt, jede Quelle der Stages der Werkzeug-Gates und die Scan-Wurzeln aus `.d-check.yml` von einem Eintrag in `werkzeug.eingaenge` (Überdeckung: gleicher Pfad oder ein Elternverzeichnis; Platzhalter als Muster; `COPY .` verlangt den Eintrag `.` und ist als Befund zu behandeln, nicht zu erlauben; eine nicht erkannte Form von `COPY` ist ein Befund, kein stilles Ignorieren). **Mutation:** der Selbsttest fügt in eine Kopie des `Dockerfile` eine `COPY`-Zeile ohne Listeneintrag ein und muss rot werden; ein Selbsttest, der das nicht zeigt, gilt nicht als gelaufen. Die Laufzeit des Selbsttests (warm und kalt) ist gemessen und im Kopf des Skripts genannt. ([TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit))
- [x] **Stempel-Skript** ([ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Entscheidung 4 und 5, Folgepflicht 1 und 3a): `bash tools/stempel.sh pruefe <klasse>` endet mit Exit 0 **nur**, wenn die Übersprung-Bedingung aus [ADR-0023](../../adr/0023-gate-klassen-und-stempel.md) Entscheidung 4 erfüllt ist (ein Vorfahr von HEAD, HEAD eingeschlossen, mit gleichem Eingangs-Hash und grünem Beleg); jeder andere Fall endet mit Exit 1 und einer Grund-Zeile auf stderr, einen dritten Ausgang gibt es nicht; `pruefe` gibt den berechneten Eingangs-Hash (SHA-256 über `<Pfad> <Git-Tree-ID>` aller Eingangspfade, sortiert) auf stdout aus. `schreibe <klasse> [hash]` hängt die Zeile `stempel <klasse> <eingangs-hash> gruen` per `git notes append` an `refs/notes/gates` des Commits und bricht ab, wenn der Hash jetzt ein anderer ist als der übergebene oder die Eingangspfade der Klasse nicht sauber sind (versioniert, unversioniert, ignoriert; Änderungen außerhalb machen die Klasse nicht schmutzig). `einrichten` setzt `notes.rewriteRef` für `refs/notes/gates` lokal im Klon, idempotent. Der Selbsttest belegt jeden Fehlerpfad einzeln: flacher Klon, schmutziger Eingangsbaum, geänderte Datei, unlesbare Note, kein Vorfahr, Hash ändert sich während des Laufs, gleichzeitiges Schreiben aus zwei Arbeitsbäumen (die eigene Sperre des Skripts hält keine Zeile zurück, siehe §7), zweiter Aufruf von `einrichten` ändert nichts, mehrere gleiche Zeilen mit demselben Hash führen zum Überspringen (kein Fehlerpfad, [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Entscheidung 4), dazu Amend, Rebase und Squash mit `notes.rewriteRef` (bisher nicht geprüft, [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Kontext 3); eine Note gilt nur bei exakt passender Klasse und exaktem Hash. Die Laufzeit des Selbsttests (warm und kalt) ist gemessen und im Kopf des Skripts genannt. ([TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit))
- [x] `make gates` grün (ohne die Selbsttests der Stempel, sie sind eigene Targets und kein Gate bis zur Migration, §7).
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update: `harness/README.md` nennt die Selbsttests des Slice in der Sensors-Tabelle (Bindung: [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md)); das Skript selbst steht dort erst, wenn es ein Gate trägt (`slice-stempel-migration`). Eine Spec-Stelle wird nicht berührt.
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
| `tools/stempel.sh` | neu | `pruefe`, `schreibe`, `einrichten` ([ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Entscheidung 4 und 5); im Kopf Zusage, Eingänge und Laufzeit des Selbsttests |
| `tools/stempel/werkzeug.eingaenge`, `tools/stempel/tests.eingaenge` | neu | Eingangslisten, Anfangsinhalt aus [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Entscheidung 10 |
| `tools/stempel-liste.sh` | neu | Lesen einer Eingangsliste, eine Syntax für Skript und Prüfung (Review I-5) |
| `tools/stempel-selbsttest.sh` | neu | jeder Fehlerpfad einzeln, Amend/Rebase/Squash, gleichzeitiges Schreiben ([TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)); legt Wegwerf-Repos im Scratch an |
| `tools/stempel-eingaenge-pruefung.sh`, `tools/stempel-eingaenge-selbsttest.sh` | neu | Überdeckung der `COPY`-Quellen durch die Listen und die Mutation (Name der Skripte ein Vorschlag) |
| `Dockerfile` | keine Änderung | Die Selbsttests laufen auf dem Host mit `bash` und `git` (Setzung des Implementers, Begründung im Kopf von `tools/stempel-selbsttest.sh`); `git` steht in der Stage `basis`, ein Lauf in Docker ist nicht gebaut |
| `tools/stempel-abbild-selbsttest.sh` | neu | die echten Listen gegen ein Abbild des Arbeitsbaums, eigenes Target `stempel-abbild` (Nutzerentscheidung 2026-11-01: Verschlankung) |
| `tools/mk/stempel.mk` | neu | Targets `stempel-selbsttest`, `stempel-eingaenge`, `stempel-abbild`; **nicht** in `GATE_CHECKS` (Nutzerentscheidung 2026-11-01, siehe §7) |
| `harness/README.md` | update | Zeilen der drei Targets in der Werkzeug-Tabelle (kein Gate) |
| `docs/plan/planning/in-progress/roadmap.md` | update | Block „In Arbeit (ohne Welle)“ |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf, inklusive einer Testdatei-Zeile mit der Akzeptanzkriterien-ID in `Begründung`.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) steht im ADR-Index auf `Accepted` (erfüllt), die Entkopplung der Test-Stage ([ADR-0023](../../adr/0023-gate-klassen-und-stempel.md) Folgepflicht 1, erster Schritt der Reihenfolge in [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Entscheidung 9) ist gebaut (beobachtbar: `make kopplung-zahlen` existiert und steht in `GATE_CHECKS`; erfüllt) und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2, oder die Auswertung des `Dockerfile` (Stage-Kette, Platzhalter, nicht erkannte Formen) sprengt den Rahmen; Schnittvorschlag: Listen mit ihrer Prüfung (Punkt 1) und Skript (Punkt 2) als zwei Slices. Auslöser, am Slice-Plan beobachtbar: das Häkchen von Punkt 1 ist gesetzt und für Punkt 2 gibt es noch keinen Test.
- `in-progress` → `open` (blockiert — Carveout?): Die Selbsttests lassen sich ohne `git` im Image nicht in Docker fahren und der Nutzer will weder `git` im Image noch einen Lauf auf dem Host (Host-Abhängigkeiten sind `bash`, `git` und `docker`, [harness/conventions.md](../../../../harness/conventions.md)); der Weg ist dann eine Entscheidung.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün und die Targets `stempel-selbsttest`, `stempel-eingaenge`, `stempel-abbild` grün (eigene Targets, kein Gate bis zur Migration), die Mutation der Eingangslisten wird rot, jeder Fehlerpfad des Skripts hat seinen Test, und die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Falsch grün** ([ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Entscheidung 10): Der Selbsttest belegt „kein `COPY` ohne Listeneintrag“, nicht „kein versteckter Eingang“; ein Gate, das eine Datei liest, die kein `COPY` holt (Netz zur Laufzeit, Host-Werkzeug), wird davon nicht erfasst. Die Aufnahmebedingung (6) der ADR trägt das, ohne Sensor. — **Ausgang:** weiter offen: → `BEO-TDS/eingangsliste-je-stage-von-hand`
- Die Prüfung der Listen leitet `COPY`-Quellen, Ziele von Inline-Links in den von d-check gelesenen Markdown-Dateien und Make-Dateien ab; die Zuordnung der Gates zu Stages und die festen Eingänge (`.dockerignore`, `d-check.mk`, `Makefile`, Fragmente) stehen von Hand im Skript, Referenz-Definitionen von Links und HTML-Anker sieht sie nicht. — **Ausgang:** weiter offen: → `BEO-TDS/eingangsliste-je-stage-von-hand`
- Die Auswertung des `Dockerfile` kennt nicht jede Form von `COPY` und `ADD` (Heredoc, `--chmod`, Variablen); eine unbekannte Form muss ein Befund sein, kein stilles Ignorieren. — **Ausgang:** entfallen: unbekannte Formen sind ein Befund (Selbsttest, Verifikation mit 18 Stichproben); die Lücke `COPY --from=<stage>` (V-1) ist behoben und mit einer Mutation belegt, ein zyklisches `COPY --from` hat seit Review F-3 einen Fall
- Der Selbsttest des Skripts braucht `git` und Wegwerf-Repos; das Basis-Image enthält `git` nach dem `Dockerfile` nicht ausdrücklich (nicht geprüft). — **Ausgang:** entfallen: `git` ist im Basis-Image vorhanden, die Selbsttests laufen auf dem Host mit `bash` und `git` (Kopf von `tools/stempel-selbsttest.sh`)
- Gleichzeitiges Schreiben der Note aus zwei Arbeitsbäumen sowie Rebase und Squash mit `notes.rewriteRef` sind bisher nicht geprüft ([ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Kontext 3 und Negativ 7); der Selbsttest prüft sie, ein anderes Ergebnis als „Sperre, Stempel ungesetzt“ oder „Note übernommen, Hash entscheidet“ wäre ein Befund für die ADR. — **Ausgang:** eingetreten: die Erwartung der ADR (Sperre, Stempel ungesetzt) traf nicht zu, `git notes append` verlor still Zeilen; beherrscht durch die eigene Sperre des Skripts, Evidenz → `BEO-TDS/git-notes-append-verliert-zeilen-parallel`
- Das Skript und die Listen sind Eingang **beider** Stempel; jede Änderung daran fährt beide Klassen (Entscheidung 10, akzeptiert, irrt zur sicheren Seite). — **Ausgang:** entfallen: von der ADR als Negativ geführt, kein Risiko des Slice
- Die Laufzeit der Selbsttests ist eine Einzelmessung auf einem Rechner; sie trägt keine Aussage über andere Hosts. — **Ausgang:** weiter offen: Einzelmessungen im Kopf von `tools/mk/stempel.mk`, kalt nicht gemessen; die Nachmessung trägt [slice-stempel-migration](../next/slice-stempel-migration.md)
- Die CI-Seite hat keinen Slice (§1); bis sie gebaut ist, läuft die CI voll und kein Stempel wirkt dort. — **Ausgang:** weiter offen: Träger ist [ADR-0023](../../adr/0023-gate-klassen-und-stempel.md) Entscheidung 7; der Slice wird angelegt, wenn [slice-stempel-mechanismus](slice-stempel-mechanismus.md) und [slice-stempel-migration](../next/slice-stempel-migration.md) in `done/` liegen und der Nutzer die CI-Seite verlangt

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

**Gegenstand:** geliefert (`tools/stempel.sh` mit `pruefe`, `schreibe`, `einrichten`; Listen `tools/stempel/werkzeug.eingaenge` und `tools/stempel/tests.eingaenge`; Selbsttests; kein Gate verdrahtet).

**Gemessen** (Einzelmessungen am 2026-11-01 auf einem Host, Git 2.43.0, Lastmittel um 12 durch andere Agenten, kein Beleg für andere Hosts; kalt, mit leerem Dateicache, nicht gemessen, das braucht Root):

- Läufe bei der Closure: `make stempel-selbsttest` 113 Fälle in 1,3 s, `make stempel-eingaenge` 93 Fälle plus Prüfung in 3,4 s, `make stempel-abbild` 68 Fälle in 3,1 s, alle wie erwartet. Die Werte im Kopf von `tools/mk/stempel.mk` (1,2 s, 2,6 s, 2,6 s) sind frühere Einzelmessungen derselben Art; sie streuen mit der Last.
- Vor der Verschlankung maß die Verifikation 18,5 s (`stempel-selbsttest`, 179 Fälle) und 27,4 s (`stempel-eingaenge`, 92 Fälle); `make gates` mit beiden als Gates 55,9 s, ohne sie 6,3 s (Docker-Cache warm).
- Gleichzeitiges Schreiben: 60 `git notes append` aus zwei Arbeitsbäumen auf dieselbe Note gaben 60-mal Exit 0, in der Note blieben 39 Zeilen (Messung im Kopf von `tools/stempel-selbsttest.sh`; der Implementer sah in weiteren Läufen 36 bis 47 Zeilen). Mit der Sperre des Skripts: 80 gesetzt, 0 ungesetzt (Verifikation).
- Mutationen: 14 von `tools/stempel.sh` in der Verifikation, 12 rot, M13 und M14 überlebten und sind seither mit Fällen belegt (rot); 30 im Review der Verschlankung, 24 rot, die drei echten Lücken (F-2, F-3, F-4) sind behoben, der Implementer sah die Fix-Mutationen (Aussetzen von `g_gleichzeitig`, zyklisches `COPY --from`, ignorierte `STEMPEL_SPERRE_VERSUCHE`) rot; der Reviewer hat die Fix-Runde nicht erneut gefahren.

**Handvektor:** keiner; keine Aussage dieses Slice hängt an einem Standard-Vektor.

**Selbstkonsistenz:** `tools/stempel-eingaenge-pruefung.sh` hält die Listen gegen die `COPY`-Quellen des `Dockerfile`, die Link-Ziele der von d-check gelesenen Markdown-Dateien und die Make-Dateien; `tools/stempel-abbild-selbsttest.sh` zeigt je Listeneintrag, dass er den Hash seiner Klasse ändert.

**Festlegung (Setzungen):** (1) Die Selbsttests laufen auf dem Host mit `bash` und `git`, nicht in Docker. (2) Das Skript serialisiert seine Aufrufe mit einer eigenen Sperre im gemeinsamen Git-Verzeichnis. (3) Die Listen weichen vom Anfangsinhalt der [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Entscheidung 10 ab: `config` statt `config/detekt/selbsttest*`; `werkzeug` zusätzlich `README.md`, `AGENTS.md`, `.claude/{agents,commands,rules}`, `d-check.mk`, `referenztest-dotnet/Dockerfile`, die Gradle-Dateien und Einzeldateien der `COPY`-Zeilen, `.harness/skills` und `SqlType.kt` (Ziele von Markdown-Links, deren Existenz d-check prüft); `tests` zusätzlich `.dockerignore`, `Makefile`, `repo.mk` und die Fragmente `builder`, `test`, `build`, `referenztest-dotnet` unter `tools/mk/`; belegt durch `tools/stempel-eingaenge-pruefung.sh`. (4) Mehrere gleiche Zeilen mit demselben Hash führen zum Überspringen ([ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Entscheidung 4: „ändern nichts“); der DoD-Wortlaut zählte den Fall unter die Fehlerpfade und ist angepasst, die ADR gilt.

**Abweichung der Lieferung vom DoD-Wortlaut (Nutzerentscheidung 2026-11-01):** Die Selbsttests sind eigene Targets (`stempel-selbsttest`, `stempel-eingaenge`, `stempel-abbild`) und **kein Gate**; sie stehen nicht in `GATE_CHECKS`, weil beide als Gates `make gates` von 6,3 s auf 55,9 s hoben, was der Nutzer das Gegenteil des Zwecks der Stempel nannte. Das ist keine Lockerung nach `AGENTS.md` §3.6: kein bestehendes Gate verliert eine Prüfung, die Selbsttests prüfen nur sich selbst. Folge: Ein Fehler in `tools/stempel*`, den Listen oder dem `Dockerfile`-Bezug macht bis zur Migration kein Gate rot; wer sie ändert, führt die drei Targets selbst aus. Der DoD-Wortlaut „Selbsttest im Werkzeug-Gate“ und „`make gates` grün einschließlich der neuen Selbsttests“ ist in §2 und §5 angepasst. Aufnahme in `GATE_CHECKS` mit [slice-stempel-migration](../next/slice-stempel-migration.md), wenn der `werkzeug`-Stempel sie überspringt. Die Verifikation fragte, ob die Selbsttests dort einen engeren Stempel bekommen sollen (V-4); bei rund 8 s Laufzeit ist das als Punkt der Migration zu entscheiden, nicht hier.

**Nicht gemessen:** kalte Laufzeit (V-3); `ADD` mit lokaler Quelle (von der Verifikation nicht eigens gefahren); ob d-check `.gitignore` beim Scannen beachtet (V-5), `.gitignore`, `.github/workflows`, `.githooks`, `.claude/hooks` und `.gitattributes` stehen in keiner Liste, kein Gate der Klassen liest sie nach dem Befund der Verifikation; Betrieb mit mehreren Agenten (nur im Selbsttest); `docs-check` auf dem gemergten Stand; die CI ruft die Selbsttests nicht (`gates.yml` führt nur `make gates`), ob ein eigener Schritt sie rufen soll, ist offen (Nutzerentscheidung, in [slice-stempel-migration](../next/slice-stempel-migration.md) §6 als Punkt geführt, zusammen mit der CI-Seite aus [ADR-0023](../../adr/0023-gate-klassen-und-stempel.md) Entscheidung 7).

**Befund gegen die ADR (Register-Evidenz, nicht verkörpert):** Die Erwartung von [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) Kontext 3 und Negativ 7 („eine Sperre, der Stempel wird nicht gesetzt“) trifft nicht zu: `git notes append` aus zwei Arbeitsbäumen verliert still Zeilen. Die ADR bleibt `Accepted` und unverändert; die Sperre des Skripts ist eine Umsetzungsfolge. Ob daraus eine ADR mit `Supersedes` wird, entscheidet der Nutzer. Die Evidenz steht unter [BEO-TDS/git-notes-append-verliert-zeilen-parallel](../observations/BEO-TDS/git-notes-append-verliert-zeilen-parallel/observation.md).

**Review und Verifikation:** Review d286322 (HIGH F-1, `d-check.mk` fehlte in der Liste, behoben), Verifikation d00ead2 (V-1 bis V-5; V-1 `COPY --from`, V-2 Schreibfehler und fehlende Zeile, V-3 Köpfe ohne Fallzahlen behoben, mit Mutationen belegt), Review eac6842 der Verschlankung (F-1 bis F-6, behoben in 10baaf0). Berichte: `docs/reviews/2026-11-01-d286322-slice-stempel-mechanismus.md`, `docs/reviews/2026-11-01-d00ead2-verifikation-slice-stempel-mechanismus.md`, `docs/reviews/2026-11-01-eac6842-slice-stempel-mechanismus-verschlankung.md`.

**Ausgang der Risiken in §6:** in §6 je Zeile eingetragen: Falsch grün und Eingangslisten von Hand: weiter offen (Register); unbekannte `COPY`-Formen und `git` im Image: entfallen; gleichzeitiges Schreiben: eingetreten und beherrscht; beide Listen und Skript als Eingang beider Stempel: entfallen; Laufzeit: weiter offen (Migration); CI-Seite: weiter offen.

**Steering-Loop-Eintrag:** Kein Eintrag verkörpert, daher kein `liegt in`. Gelernt: (1) Mutationen im Review und in der Verifikation fanden Lücken, die 100 grüne Fälle nicht zeigten (Gruppen, die ohne Fälle durchlaufen, Schutz ohne Fall, Zusage nur als Text geprüft); die Fixes im Selbsttest (Mindestzahl je Gruppe, Fälle für Schreibfehler und Zyklus) stehen im Code, sind aber keine deklarierte Regel mit Anker. Vorschlag, nicht gesetzt: ein Selbsttest zählt Fälle je Gruppe und gilt sonst als nicht gelaufen; unter der 3×-Schwelle ([BEO-TDS/schranken-test-mit-ungeprueftem-zaehler](../observations/BEO-TDS/schranken-test-mit-ungeprueftem-zaehler/observation.md) ist die nächste vorhandene Beobachtung, die Zuordnung des Treffers ist offen). (2) Eine benannte Lücke zwischen ADR und Werkzeug: die Annahme über die Sperre von `git notes` (siehe Befund oben).

**Register:** [BEO-TDS/eingangsliste-je-stage-von-hand](../observations/BEO-TDS/eingangsliste-je-stage-von-hand/observation.md) erhält die Datei `evidence/slice-stempel-mechanismus.md` (Treffer: `d-check.mk` fehlte in der Liste, Link-Ziele wie `SqlType.kt` fehlten, `COPY --from=<stage>` brachte die Eingänge der Quell-Stage nicht mit; ein Vorgang zählt einmal, der Zähler steht damit bei 2×, Zuordnung eines Ausgangs erst ab 3×); neu [BEO-TDS/git-notes-append-verliert-zeilen-parallel](../observations/BEO-TDS/git-notes-append-verliert-zeilen-parallel/observation.md) (1×). `BEO-TDS/test-stage-cache-verdeckt-wiederholung` ist verkörpert und wird von [slice-stempel-migration](../next/slice-stempel-migration.md) benutzt, ohne neue Evidenz.

**Folge-Slice:** [slice-stempel-migration](../next/slice-stempel-migration.md) (liegt in `open/`, der Start-Trigger ist mit diesem Slice in `done/` erfüllt).

**Drei Paarungen:** Das Repo führt Wellen; die Paarungen prüft die nächste Welle-Closure. Anker ↔ Folge-Slice: kein Anker gesetzt. Register ↔ Closure-Notiz: getragen (die zwei Pfade stehen oben). Folge-Slice: genannt.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Die berührten Pfade (`tools`, `Dockerfile`, `tools/mk`, `harness/README.md`) sind Werkzeug-Cluster (Achse 3), tragen aber keine eigene Strukturregel (Achse 1) und keine eigene Inventur-Linie (Achse 2): eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, Stand des gemergten `main` am 2026-11-01); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/eingangsliste-je-stage-von-hand` — 1×, offen, unter der Schwelle. Treffer: die Listen der Eingangsdateien je Docker-Stage stehen von Hand ohne Sensor; Punkt 1 ist dieser Sensor (§6). Der Planner setzt keinen Ausgang.
- `BEO-TDS/test-stage-cache-verdeckt-wiederholung` — 5×, Stand *verkörpert* (`make clean-build`). Treffer: ein Stempel verdeckt die Wiederholung grüner Läufe zusätzlich zum Layer-Cache; `make clean-build` und `FORCE=1` bleiben der Frischbeleg (Sache von `slice-stempel-migration`).
- Ohne Treffer: die übrigen Einträge des Registers.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
