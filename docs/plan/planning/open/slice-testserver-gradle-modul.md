# Slice slice-testserver-gradle-modul: Testserver als eigenes Gradle-Modul, das die Bibliothek als Projekt-Abhängigkeit bindet

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0034](../../adr/0034-testserver-als-gradle-modul.md) (Accepted; die Nummer ist beim Merge gegen den ADR-Index von `main` zu prüfen, `0033` ist auf einem anderen Zweig vergeben und wird dort `Accepted`; die Indexzeilen kollidieren, beide bleiben), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md), [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md), [ADR-0030](../../adr/0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md)

**Berührte Spec-Stellen:** SPEC-052 (Testserver, Abbild aus dem Klassenpfad) · SPEC-040 (Läufe gegen den Testserver, Heap-Angabe) · SPEC-042 (Lock und Prüfsummen) · SPEC-059 (Nachweis ohne `internal`) · Änderungsprotokoll

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-06.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert; jeder Ausschluss mit Begründung.

**Ziel:** Der Testserver ist ein eigenes Gradle-Modul mit eigener `build.gradle.kts`, das die Bibliothek als Projekt-Abhängigkeit bindet; Jackson steht nur in seinem Lock, die Modulgrenze belegt „nur öffentliche Fläche“, und alle Gates und E2E-Läufe laufen auf dem neuen Aufbau grün, wie [ADR-0034](../../adr/0034-testserver-als-gradle-modul.md) es festlegt.

**Nutzerentscheidung (2026-11-06, wörtlich):** „aber wir wollen die lib testen, also muss sie in einem gradle-file auch eingebunden werden. bitte anpassen“. Die Lesart (eigenes Modul, Einbindung der Bibliothek als Abhängigkeit) hat der Nutzer am 2026-11-06 mit „ja deine empfehlungen“ bestätigt; damit gelten: `integrator` bleibt Source-Set, der Lauf trägt das Jar `tdswire.jar` mit Manifest und Lizenz, Ort `testserver/`, Reihenfolge vor den Code-Slices der Welle und vor dem Sensor der öffentlichen Fläche, Lock-Aussage nur als Absicht.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Neue Funktion des Testservers (Katalog, Schreiben, Massenladen, Log-Adapter) — [slice-katalog-und-ddl-zustand](../open/slice-katalog-und-ddl-zustand.md), [slice-schreibpfad-bulk-load](../open/slice-schreibpfad-bulk-load.md), [slice-schreibpfad-merge-output](../open/slice-schreibpfad-merge-output.md), [slice-betrieb-log-adapter-testserver](../open/slice-betrieb-log-adapter-testserver.md); dieser Slice bewegt und verdrahtet, er ändert kein Verhalten des Testservers oder der Bibliothek. Jede Verhaltensänderung wäre ein anderer Vorgang mit eigenem Beleg.
- Der Schnitt der öffentlichen Fläche — [slice-oeffentliche-flaeche-verkleinern-und-belegen](../open/slice-oeffentliche-flaeche-verkleinern-und-belegen.md). Fehlt dem Testserver nach dem Umzug ein Name, ist das ein Befund über die Fläche und kein Anlass, `internal` zu öffnen ([ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) Entscheidung 1); er geht in die Closure-Notiz und ans Register.
- `integrator` als Modul, Teilung der Bibliothek in mehrere Module, Veröffentlichung in ein Repository — die ADR führt sie als Alternativen C und E; der Nutzer hat entschieden, dass der Einbettungstest ein Source-Set der Wurzel bleibt.
- Umbau der Hilfen `ServerProcess`, `ServerSmokeTest`, `LaufzeitClient` — sie bleiben in `src/test` ([ADR-0034](../../adr/0034-testserver-als-gradle-modul.md) Entscheidung 8); ein Zyklus zwischen Wurzel-`test` und Modul (M4) wäre ein Rückführungsgrund, kein Auftrag dieses Slice.
- Eine neue Gate-Klasse oder ein Gate für den Lock (Jackson nur im Modul-Lock) — [ADR-0034](../../adr/0034-testserver-als-gradle-modul.md) führt es als Absicht ohne Gate; ein Gate dafür wäre eine eigene Entscheidung nach [ADR-0023](../../adr/0023-gate-klassen-und-stempel.md).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Modul, Bindung und Umzug** ([TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit)): `settings.gradle.kts` nimmt `:testserver` auf; `testserver/build.gradle.kts` bindet `project(":")`; der Umzug `src/testserver` → `testserver` ist ein reiner `git mv`-Commit vor jeder Inhaltsänderung ([AGENTS.md](../../../../AGENTS.md) §3.3); die Tasks `test`, `testserverKlassenpfad`, `integrationsServer` (Heap 48 MiB unverändert), `kompatibilitaetsServer` stehen im Modul; die Wurzel-`test` bekommt den Klassenpfad des Moduls. **Belege:** M1 (Mutation: ein Zugriff auf `internal` bricht die Übersetzung des Moduls; erst dann entfällt `testserverOhneAssoziation`), M2 (der Laufzeit-Klassenpfad des Testservers trägt das Jar `tdswire.jar` mit Manifest und Lizenz, nicht das Klassenverzeichnis; die Klassenpfad-Datei als Beleg in der Closure-Notiz), M4 (kein Zyklus: `make test`, `make smoke` grün).
- [ ] **Lock, Dockerfile, detekt und Stempel-Eingänge** ([TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)): eigene `testserver/gradle.lockfile`, `make deps-lock` schreibt drei Lock-Dateien und die Prüfsummen, `abhaengigkeitenLaden` lädt die Konfigurationen des Moduls (die `--offline`-Stages laufen); Stages des Dockerfiles kopieren das Modul, `build-jar` baut allein die Bibliothek; detekt läuft im Modul mit unveränderter `config/detekt/detekt.yml`; `tests.eingaenge` und `werkzeug.eingaenge` sind nachgezogen, die festen Pfade der Skripte (`integrationszahlen-*`, `stempel-eingaenge-selbsttest`, `spec-abdeckung`, `testserver`, `testserver-selbsttest`, `simulationstest`) stimmen. **Belege:** M3 (`make deps-lock` ohne Versionsänderung: Jackson nur im Modul-Lock, Diff der Prüfsummen erklärt), M5 (`make detekt`, `make detekt-selbsttest` grün ohne Änderung von `detekt.yml`), M6 (`make stempel-eingaenge-pruefung` rot vor dem Nachzug der Listen, grün danach; `make stempel-eingaenge`, `make stempel-abbild`, `make stempel-gruppe`, `make kopplung-zahlen` grün).
- [ ] **Spec, Doku und Messung nachgezogen:** `SPEC-052`, `SPEC-040` (Pfad, Heap-Angabe), `SPEC-042` und `SPEC-059` (Nachweis über die Modulgrenze statt über den Sensor) nennen den Stand; `harness/README.md` (Sensors- und Werkzeugzeilen) und `docs/user/testserver-manuell.md` (Pfade) folgen; M7 (Laufzeiten vorher und nachher, Host, Last) liegt als Messprotokoll unter `docs/messungen/`; das Änderungsprotokoll der Spezifikation nennt die Änderung. Aussagen nur so weit, wie ein Gate oder eine Messzeile sie trägt ([AGENTS.md](../../../../AGENTS.md) §3.8).

Nachweis-Pflichten für alle drei Punkte:

- [ ] `make gates` grün; zusätzlich laufen `make e2e` (Stufen 1 bis 7) und `make testserver-selbsttest` grün auf dem neuen Aufbau (kein Gate, [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)); ein rotes Gate erreicht `done/` nur mit Carveout.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update für die oben genannten Stellen (öffentlicher Vertrag der Spezifikation berührt).
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben — Treffer aus §8 mit Evidence-Datei, oder „keine Beobachtung angefallen“ in §7; **kein Zähler wird gesetzt**, er folgt aus den Dateien.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **mit** Wellen von der Welle-Closure.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort. Der Implementer erweitert sie in seinem ersten Lauf (Übersetzungslauf, `make stempel-eingaenge-pruefung`).

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/testserver` → `testserver` | `git mv` (eigener Commit) | Umzug des Verzeichnisses mit Quellen, Tests und Ressourcen; Inhalt unverändert |
| `settings.gradle.kts` | update | `include(":testserver")` |
| `testserver/build.gradle.kts`, `testserver/gradle.lockfile` | neu | Modul: Plugins, `project(":")`, Jackson, Tests, `sourceSets` mit den bisherigen Verzeichnissen, Tasks `testserverKlassenpfad`, `integrationsServer`, `kompatibilitaetsServer`, Sperre |
| `build.gradle.kts`, `gradle.lockfile` | update | Source-Set `testserver`, Sensor `testserverOhneAssoziation` (nach M1), Tasks und `detekt.source` verlieren die Modul-Teile; Konfiguration für den Klassenpfad des Moduls; `abhaengigkeitenLaden` liest auch die Sperre des Moduls; Jar-Task der Wurzel bleibt |
| `gradle/verification-metadata.xml` | update (über `make deps-lock`) | Prüfsummen mit zwei Projekten |
| `Dockerfile` | update | `COPY`-Zeilen der Stages (`deps` mit Build-Datei und Sperre des Moduls, übrige mit `testserver`), `build-jar` mit `:assemble`, Pfade im Abbild |
| `tools/mk/build.mk` | update | `DEPS_TASKS` ohne `testserverTest`, `detektTestserver` |
| `tools/stempel/tests.eingaenge`, `tools/stempel/werkzeug.eingaenge` | update | Eintrag `testserver`; Pfade der Stage `kopplung-zahlen`; Modul-Build-Datei und -Sperre |
| `tools/integrationszahlen-pruefung.sh`, `tools/integrationszahlen-selbsttest.sh`, `tools/stempel-eingaenge-selbsttest.sh`, `tools/spec-abdeckung.sh`, `tools/testserver.sh`, `tools/testserver-selbsttest.sh`, `tools/simulationstest.sh` | update | feste Pfade `src/testserver` und `build/test-results/testserverTest` |
| Textprüfung auf `friend`/`associateWith` in `testserver/build.gradle.kts` | neu (Form offen) | Sperre des Weges, die Modulgrenze zu öffnen ([ADR-0034](../../adr/0034-testserver-als-gradle-modul.md) Entscheidung 3); Klasse nach [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) |
| `spec/spezifikation.md`, `harness/README.md`, `docs/user/testserver-manuell.md` | update | siehe Liefer-Punkt 3 |
| `docs/messungen/` — neues Messprotokoll | neu | M1 bis M7 mit Datum, Host, Last |

**Reihenfolge der Commits (Ansatz):** (1) reiner `git mv src/testserver testserver`; (2) Modul, Wurzel-Build, Lock und Dockerfile in einem Stand, der `make gates` besteht (zwischen Move und Nachzug ist das Repo rot, beide Commits gehören in denselben Push, [AGENTS.md](../../../../AGENTS.md) §3.3); (3) Listen und Skriptpfade; (4) Spec und Doku. M1, M2 und M4 werden vor (2) an einem Wegwerf-Stand im Scratchpad gemessen, damit ein Befund die Entscheidung der ADR ändert, bevor das Repo umgebaut ist.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [ADR-0034](../../adr/0034-testserver-als-gradle-modul.md) steht auf `Accepted` (erfüllt am 2026-11-06) und ein Verantwortlicher ist eingetragen.

**Kanten (Reihenfolge vom Nutzer am 2026-11-06 entschieden: dieser Slice vor den Code-Slices der Welle und vor dem Sensor der öffentlichen Fläche):**

- **[slice-oeffentliche-flaeche-verkleinern-und-belegen](../open/slice-oeffentliche-flaeche-verkleinern-und-belegen.md):** dieser Slice steht **vor** jenem Slice, genauer vor dessen Sensor (Liefer-Punkt 2). Sein Sensor liest „die Verwendungen in `src/integrator` und `src/testserver`“; nach diesem Slice ist der Pfad `testserver/kotlin`, und die Übersetzung des Moduls belegt die Erreichbarkeit der dort verwendeten Namen über die Projekt-Grenze (stärker als die Namenssuche). Jener Slice liest damit den neuen Pfad und baut seinen Sensor nicht für den alten; er trägt die Kante als eigenen Start-Trigger (Nachzug beim Wechsel nach `next/`, nicht in diesem Plan). Der Nutzer-Schnitt der Namen (Liefer-Punkt 1 jenes Slice) kann früher beginnen; nur der Sensor wartet. Macht jener Slice einen Namen `internal`, den der Testserver nutzt, bricht die Übersetzung des Moduls statt eines Sensors — gewollt.
- **[slice-adr-testserver-bestand-und-katalog](../in-progress/slice-adr-testserver-bestand-und-katalog.md)** (`in-progress/`): keine Abhängigkeit in beide Richtungen. Seine ADR (Nummer `0033`, auf einem anderen Zweig, wird dort `Accepted`) führt in der Fitness Function die Zeile „Source-Set `testserver` ohne Assoziation“; nach `Accepted` ersetzt [ADR-0034](../../adr/0034-testserver-als-gradle-modul.md) die Ortsangabe, ohne dass die Datei jener ADR geändert wird. Die Code-Slices, die diese ADR vorbereitet (Katalog, Massenladen, MERGE), ändern Dateien unter dem Testserver-Pfad: dieser Slice steht **vor** ihnen (Nutzerentscheidung), damit sie nur einmal den neuen Pfad schreiben; sie tragen die Kante als eigenen Start-Trigger, sobald dieser Plan angenommen ist (Nachzug beim Wechsel nach `next/`, nicht in diesem Plan).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte oder ein Befund aus M3, der eine eigene Entscheidung über Lock und Prüfsummen verlangt; Schnittvorschlag: Modul und Bindung | Lock, Dockerfile, Stempel.
- `in-progress` → `open` (blockiert): M1 ist rot (die Modulgrenze sperrt `internal` nicht) oder M4 zeigt einen Zyklus — beides ändert die Entscheidung der ADR und braucht eine neue Nutzerentscheidung; oder der Nutzer ändert die Lesart.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün, `make e2e` ist grün, der Review-Report liegt vor, [ADR-0034](../../adr/0034-testserver-als-gradle-modul.md) steht im Index auf `Accepted`, und die Closure-Notiz trägt die Messzeilen M1 bis M7, die Zuordnung *Test | Sensor | Festlegung* je Aussage und den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Die Modulgrenze sperrt `internal` womöglich nicht** (M1; Annahme der ADR, gelesen, nicht gemessen). Dann bleibt der Sensor `testserverOhneAssoziation`, und die ADR ist anzupassen. — **Ausgang:** offen bis zur Closure.
- **Zyklus Wurzel-`test` ↔ Modul** (M4): `ServerProcess` braucht den Klassenpfad des Moduls, das Modul die Bibliothek. Fallback ist ein Umbau der Smoke-Hilfen (eigene Entscheidung). — **Ausgang:** offen bis zur Closure.
- **Lock und Prüfsummen mit zwei Projekten** (M3): Verhalten von `verification-metadata.xml` und `--write-locks` bei zwei Projekten nicht gemessen; `abhaengigkeitenLaden` lädt sonst die Konfigurationen des Moduls nicht, und `--offline`-Stages scheitern spät (`BEO-TDS/katalog-und-lock-ohne-gegenpruefung`). — **Ausgang:** offen bis zur Closure.
- **Der Pfad trifft eine Regel, die er nicht treffen soll** (M5): Das Layout ist gewählt, damit die Ausgabe-Sperre das Modul nicht trifft; ob eine andere Regel von `detekt.yml` es trifft, ist nicht gemessen. Eine Regel wird nicht gelockert ([AGENTS.md](../../../../AGENTS.md) §3.6). — **Ausgang:** offen bis zur Closure.
- **Stille Lücke der Eingangslisten** (M6): Ein Pfad, den die Prüfung nicht als `COPY` sieht (Skripte mit festen Pfaden), kann im Stempel fehlen (`BEO-TDS/eingangsliste-je-stage-von-hand`). — **Ausgang:** offen bis zur Closure.
- **Zwei Bindungsformen der Bibliothek** (`integrator` bindet das Klassenverzeichnis, der Testserver die Projekt-Abhängigkeit): vom Nutzer so entschieden ([ADR-0034](../../adr/0034-testserver-als-gradle-modul.md)). — **Ausgang:** entfallen als Mangel; ob die Beobachtung ins Register gehört, notiert die Closure-Notiz.
- **Laufende Arbeiten am alten Pfad** (Code-Slices der Welle, `slice-betrieb-log-adapter-testserver`): Merge-Konflikte durch den Umzug; `git mv` und Inhalt sind getrennte Commits, damit Rename-Erkennung trägt. — **Ausgang:** offen bis zur Closure.
- **Nummer der ADR:** `0033` ist anderweitig vergeben; beim Merge gegen den Index von `main` prüfen und Dateiname, Index, `Bezug` und alle Verweise dieses Slice nachziehen. — **Ausgang:** offen bis zum Merge.

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
Schritte** und die **vier Pflichtkriterien**.

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); der Slice berührt Build, Dockerfile, Stempel-Listen, Skripte, den Testserver und die Spezifikation, eine eigene Sub-Area besteht nicht.

**Vorgelagert — offene Beobachtungen sichten:** Namensliste des Registers durchgegangen, Texte nicht einzeln gelesen. Treffer: `BEO-TDS/katalog-und-lock-ohne-gegenpruefung` (Lock), `BEO-TDS/eingangsliste-je-stage-von-hand` (Stempel-Listen), `BEO-TDS/java-quelle-umgeht-kotlin-regel` (Grenze des Nachweises bleibt), `BEO-TDS/jackson-abhaengigkeit-fuer-embedder-nicht-gemessen` (der Lock des Moduls trägt die Jackson-Aussage zusätzlich), `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` (Lesart der Nutzerentscheidung vom 2026-11-06). Zähler nicht gesetzt; sie folgen aus den Dateien.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
