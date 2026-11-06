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

**Bezug:** [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0034](../../adr/0034-testserver-als-gradle-modul.md) (Accepted, 2026-11-06), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md), [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md), [ADR-0030](../../adr/0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md)

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
- Der Schnitt der öffentlichen Fläche — [slice-oeffentliche-flaeche-verkleinern-und-belegen](../in-progress/slice-oeffentliche-flaeche-verkleinern-und-belegen.md). Fehlt dem Testserver nach dem Umzug ein Name, ist das ein Befund über die Fläche und kein Anlass, `internal` zu öffnen ([ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) Entscheidung 1); er geht in die Closure-Notiz und ans Register.
- `integrator` als Modul, Teilung der Bibliothek in mehrere Module, Veröffentlichung in ein Repository — die ADR führt sie als Alternativen C und E; der Nutzer hat entschieden, dass der Einbettungstest ein Source-Set der Wurzel bleibt.
- Umbau der Hilfen `ServerProcess`, `ServerSmokeTest`, `LaufzeitClient` — sie bleiben in `src/test` ([ADR-0034](../../adr/0034-testserver-als-gradle-modul.md) Entscheidung 8); ein Zyklus zwischen Wurzel-`test` und Modul (M4) wäre ein Rückführungsgrund, kein Auftrag dieses Slice.
- Eine neue Gate-Klasse oder ein Gate für den Lock (Jackson nur im Modul-Lock) — [ADR-0034](../../adr/0034-testserver-als-gradle-modul.md) führt es als Absicht ohne Gate; ein Gate dafür wäre eine eigene Entscheidung nach [ADR-0023](../../adr/0023-gate-klassen-und-stempel.md).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Modul, Bindung und Umzug** ([TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit)): `settings.gradle.kts` nimmt `:testserver` auf; `testserver/build.gradle.kts` bindet `project(":")`; der Umzug `src/testserver` → `testserver` ist ein reiner `git mv`-Commit vor jeder Inhaltsänderung ([AGENTS.md](../../../../AGENTS.md) §3.3); die Tasks `test`, `testserverKlassenpfad`, `integrationsServer` (Heap 48 MiB unverändert), `kompatibilitaetsServer` stehen im Modul; die Wurzel-`test` bekommt den Klassenpfad des Moduls. **Belege:** M1 (Mutation: ein Zugriff auf `internal` bricht die Übersetzung des Moduls; erst dann entfällt `testserverOhneAssoziation`), M2 (der Laufzeit-Klassenpfad des Testservers trägt das Jar `tdswire.jar` mit Manifest und Lizenz, nicht das Klassenverzeichnis; die Klassenpfad-Datei als Beleg in der Closure-Notiz), M4 (kein Zyklus: `make test`, `make smoke` grün).
- [x] **Lock, Dockerfile, detekt und Stempel-Eingänge** ([TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)): eigene `testserver/gradle.lockfile`, `make deps-lock` schreibt drei Lock-Dateien und die Prüfsummen, `abhaengigkeitenLaden` lädt die Konfigurationen des Moduls (die `--offline`-Stages laufen); Stages des Dockerfiles kopieren das Modul, `build-jar` baut allein die Bibliothek; detekt läuft im Modul mit unveränderter `config/detekt/detekt.yml`; `tests.eingaenge` und `werkzeug.eingaenge` sind nachgezogen, die festen Pfade der Skripte (`integrationszahlen-*`, `stempel-eingaenge-selbsttest`, `spec-abdeckung`, `testserver`, `testserver-selbsttest`, `simulationstest`) stimmen. **Belege:** M3 (`make deps-lock` ohne Versionsänderung: Jackson nur im Modul-Lock, Diff der Prüfsummen erklärt), M5 (`make detekt`, `make detekt-selbsttest` grün ohne Änderung von `detekt.yml`), M6 (`make stempel-eingaenge-pruefung` rot vor dem Nachzug der Listen, grün danach; `make stempel-eingaenge`, `make stempel-abbild`, `make stempel-gruppe`, `make kopplung-zahlen` grün).
- [x] **Spec, Doku und Messung nachgezogen:** `SPEC-052`, `SPEC-040` (Pfad, Heap-Angabe), `SPEC-042` und `SPEC-059` (Nachweis über die Modulgrenze statt über den Sensor) nennen den Stand; `harness/README.md` (Sensors- und Werkzeugzeilen) und `docs/user/testserver-manuell.md` (Pfade) folgen; M7 (Laufzeiten vorher und nachher, Host, Last) liegt als Messprotokoll unter `docs/messungen/`; das Änderungsprotokoll der Spezifikation nennt die Änderung. Aussagen nur so weit, wie ein Gate oder eine Messzeile sie trägt ([AGENTS.md](../../../../AGENTS.md) §3.8).

Nachweis-Pflichten für alle drei Punkte:

- [x] `make gates` grün; zusätzlich laufen `make e2e` (Stufen 1 bis 7) und `make testserver-selbsttest` grün auf dem neuen Aufbau (kein Gate, [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)); ein rotes Gate erreicht `done/` nur mit Carveout.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update für die oben genannten Stellen (öffentlicher Vertrag der Spezifikation berührt).
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — Treffer aus §8 mit Evidence-Datei, oder „keine Beobachtung angefallen“ in §7; **kein Zähler wird gesetzt**, er folgt aus den Dateien.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **mit** Wellen von der Welle-Closure.

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
| `config/detekt/detekt.yml` (Kommentar der Ausgabe-Sperre), `Dockerfile` (Köpfe der Stages `deps`, `compile`, `test`, `detekt-selbsttest`), `build.gradle.kts` und `testserver/build.gradle.kts` (Kommentare), `tools/jackson-sperre.sh`, `tools/manifest-pruefung.sh`, `tools/e2e.sh`, `docs/plan/e2e-abdeckung.md`, `ServerProcess.kt`, `IntegrationServer.kt` | update (nur Kommentare und Text) | zweite Fundstellen der Lesart „Source-Set `testserver`“ bzw. des alten Pfads; keine Regel und kein Wert geändert (nach dem Review nachgezogen) |
| `docs/plan/planning/welle-produktschnitt.md` (Ziel, DoD-Punkt 3 und 5, Ausschluss), offene Pläne unter `open/`, `welle-testserver-manuell.md` | update (Pfad, Bezeichnung des Prüfgegenstands) | Ziel der Welle unverändert; Zeile 56 (Titel eines erledigten Slice) bleibt |

**Reihenfolge der Commits (Ansatz):** (1) reiner `git mv src/testserver testserver`; (2) Modul, Wurzel-Build, Lock und Dockerfile in einem Stand, der `make gates` besteht (zwischen Move und Nachzug ist das Repo rot, beide Commits gehören in denselben Push, [AGENTS.md](../../../../AGENTS.md) §3.3); (3) Listen und Skriptpfade; (4) Spec und Doku. M1, M2 und M4 werden vor (2) an einem Wegwerf-Stand im Scratchpad gemessen, damit ein Befund die Entscheidung der ADR ändert, bevor das Repo umgebaut ist.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [ADR-0034](../../adr/0034-testserver-als-gradle-modul.md) steht auf `Accepted` (erfüllt am 2026-11-06) und ein Verantwortlicher ist eingetragen.

**Kanten (Reihenfolge vom Nutzer am 2026-11-06 entschieden: dieser Slice vor den Code-Slices der Welle und vor dem Sensor der öffentlichen Fläche):**

- **[slice-oeffentliche-flaeche-verkleinern-und-belegen](../in-progress/slice-oeffentliche-flaeche-verkleinern-und-belegen.md):** dieser Slice steht **vor** jenem Slice, genauer vor dessen Sensor (Liefer-Punkt 2). Sein Sensor liest „die Verwendungen in `src/integrator` und `src/testserver`“; nach diesem Slice ist der Pfad `testserver/kotlin`, und die Übersetzung des Moduls belegt die Erreichbarkeit der dort verwendeten Namen über die Projekt-Grenze (stärker als die Namenssuche). Jener Slice liest damit den neuen Pfad und baut seinen Sensor nicht für den alten; er trägt die Kante als eigenen Start-Trigger (Nachzug beim Wechsel nach `next/`, nicht in diesem Plan). Der Nutzer-Schnitt der Namen (Liefer-Punkt 1 jenes Slice) kann früher beginnen; nur der Sensor wartet. Macht jener Slice einen Namen `internal`, den der Testserver nutzt, bricht die Übersetzung des Moduls statt eines Sensors — gewollt.
- **[slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md)** (`done/`): keine Abhängigkeit in beide Richtungen. Seine ADR ([ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), `Accepted`) führt in der Fitness Function die Zeile „Source-Set `testserver` ohne Assoziation“; nach `Accepted` ersetzt [ADR-0034](../../adr/0034-testserver-als-gradle-modul.md) die Ortsangabe, ohne dass die Datei jener ADR geändert wird. Die Code-Slices, die diese ADR vorbereitet (Katalog, Massenladen, MERGE), ändern Dateien unter dem Testserver-Pfad: dieser Slice steht **vor** ihnen (Nutzerentscheidung), damit sie nur einmal den neuen Pfad schreiben; sie tragen die Kante als eigenen Start-Trigger, sobald dieser Plan angenommen ist (Nachzug beim Wechsel nach `next/`, nicht in diesem Plan).

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

- **Die Modulgrenze sperrt `internal` womöglich nicht** (M1; Annahme der ADR, gelesen, nicht gemessen). Dann bleibt der Sensor `testserverOhneAssoziation`, und die ADR ist anzupassen. — **Ausgang:** entfallen. M1 ist rot (Protokoll M1.2; Review und Verifikation wiederholt: `:testserver:compileKotlin FAILED`, `Cannot access 'object SqlLexemes : Any': it is internal in file.`); der Sensor ist entfernt.
- **Zyklus Wurzel-`test` ↔ Modul** (M4): `ServerProcess` braucht den Klassenpfad des Moduls, das Modul die Bibliothek. Fallback ist ein Umbau der Smoke-Hilfen (eigene Entscheidung). — **Ausgang:** entfallen. Kein Zyklus (Protokoll M4; `make clean-build` und `make e2e` Stufe 1 in der Verifikation grün).
- **Lock und Prüfsummen mit zwei Projekten** (M3): Verhalten von `verification-metadata.xml` und `--write-locks` bei zwei Projekten nicht gemessen; `abhaengigkeitenLaden` lädt sonst die Konfigurationen des Moduls nicht, und `--offline`-Stages scheitern spät (`BEO-TDS/katalog-und-lock-ohne-gegenpruefung`). — **Ausgang:** entfallen. Protokoll M3: Koordinaten 97 (alte Wurzel-Sperre), 91 (neue Wurzel) und 97 (Modul), die Vereinigung gleicht der alten, `verification-metadata.xml` ohne Diff; im Review wiederholt, in der Verifikation nur die Jackson-Zählung statisch (0 in der Wurzel, 5 im Modul); `make deps-lock` wiederholte die Verifikation nicht (Netz).
- **Der Pfad trifft eine Regel, die er nicht treffen soll** (M5): Das Layout ist gewählt, damit die Ausgabe-Sperre das Modul nicht trifft; ob eine andere Regel von `detekt.yml` es trifft, ist nicht gemessen. Eine Regel wird nicht gelockert ([AGENTS.md](../../../../AGENTS.md) §3.6). — **Ausgang:** entfallen. `config/detekt/detekt.yml` ohne Regeländerung (nur ein Kommentar); `make detekt` und `make detekt-selbsttest` grün (Protokoll M5, Gate-Lauf der Closure).
- **Stille Lücke der Eingangslisten** (M6): Ein Pfad, den die Prüfung nicht als `COPY` sieht (Skripte mit festen Pfaden), kann im Stempel fehlen (`BEO-TDS/eingangsliste-je-stage-von-hand`). — **Ausgang:** entfallen als Eintritt in diesem Slice: `make stempel-eingaenge-pruefung` grün nach dem Nachzug der Listen, Pfade der Skripte vom `make e2e` gefahren. Die Klasse bleibt als Beobachtung offen; die Verifikation hat Kaltlauf und Stempel einzeln nicht gefahren.
- **Zwei Bindungsformen der Bibliothek** (`integrator` bindet das Klassenverzeichnis, der Testserver die Projekt-Abhängigkeit): vom Nutzer so entschieden ([ADR-0034](../../adr/0034-testserver-als-gradle-modul.md)). — **Ausgang:** entfallen als Mangel; ob die Beobachtung ins Register gehört, notiert die Closure-Notiz.
- **Laufende Arbeiten am alten Pfad** (Code-Slices der Welle, `slice-betrieb-log-adapter-testserver`): Merge-Konflikte durch den Umzug; `git mv` und Inhalt sind getrennte Commits, damit Rename-Erkennung trägt. — **Ausgang:** weiter offen, als Grenze benannt: der Move `76a2e07c` ist rein (26 Renames, 0 Zeilen); die Merges in den Zweig (`a572c5de`) liefen, die Konflikte mit späteren Änderungen am alten Pfad entstehen erst beim Merge nach `main` und gehören dem Merge.
- **Angenommene ADRs führen noch „Source-Set `testserver`“** ([ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) Fitness Function, Zeile „Übersetzung des Source-Sets `testserver`“, `make test`; [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) Fitness Function, Zeile „Source-Set `testserver` ohne Assoziation zu `main`“, `make test`). Beide sind `Accepted` und bleiben unverändert ([AGENTS.md](../../../../AGENTS.md) §3.5); [ADR-0034](../../adr/0034-testserver-als-gradle-modul.md) ersetzt nur Teile von [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md). Ob eine Folge-ADR oder eine benannte Grenze die Ortsangabe auf das Modul lenkt, war eine Entscheidung des Nutzers. — **Ausgang:** weiter offen, als benannte Grenze (Entscheidung des Nutzers, 2026-11-06, „deine empfehlungen“): keine Folge-ADR; [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) (Zeile 188) und [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) (Zeile 66) bleiben unverändert und gelten mit dem Ort „Modul `:testserver`“ ([ADR-0034](../../adr/0034-testserver-als-gradle-modul.md)); kein Sensor hält die Gleichheit der Lesart. Register: `BEO-TDS/angenommene-adr-fuehrt-ortsangabe-nach-umbau`.
- **Abweichung von [ADR-0034](../../adr/0034-testserver-als-gradle-modul.md) Entscheidung 8:** Die Wurzel-`test` liest die Datei der Modul-Task statt einer auflösbaren Konfiguration `project(":testserver")`; gemessen ist die Dateilösung (M3.3, M4), die Konfigurationslösung nicht (Messprotokoll, Abschnitt M4). — **Ausgang:** eingetreten, hingenommen (Entscheidung des Nutzers, 2026-11-06): die Dateilösung bleibt, [ADR-0034](../../adr/0034-testserver-als-gradle-modul.md) bleibt unverändert, die Konfigurationslösung ist nicht gefahren. Register: `BEO-TDS/umsetzung-weicht-von-adr-entscheidung-ab`.
- **Nummer der ADR:** `0034` steht im ADR-Index von `main` neben [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md). — **Ausgang:** entfallen.

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

- **Gegenstand:** geliefert. Commits: reiner Move `76a2e07c` (26 Renames, 0 Zeilen), Modul und Bindung `084c3108`, Listen, Skriptpfade und Sperre der Modulgrenze `ee93d602`, Messprotokoll `63f8d992`, Merge von `main` `a572c5de`, Kommentare `354215d3`, Spec und Doku `cf25e7b7`, Haken und Messprotokoll `54d35b64`, `661740c6`; Review `1c35a59d`, Review-Nachzug `ca952e3b`, Verifikation `4ea7f4f0`. Messprotokoll: [testserver-gradle-modul-2026-11-06](../../../messungen/testserver-gradle-modul-2026-11-06.md).
- **Was hat funktioniert:** (1) Die Modulgrenze trägt „nur öffentliche Fläche“ stärker als der Sensor `testserverOhneAssoziation`: M1 ist rot (Zugriff auf `internal` bricht `:testserver:compileKotlin`), im Review und in der Verifikation unabhängig wiederholt; der Sensor ist entfernt. (2) M2: der Laufzeit-Klassenpfad des Testservers trägt das Jar `tdswire.jar` (Manifest ohne `Main-Class`, Lizenz), kein Klassenverzeichnis; die Task bricht bei einem Verzeichnis außerhalb des Moduls (beide Zweige rot gesehen). (3) M3: Jackson steht nur in `testserver/gradle.lockfile` (0 in der Wurzel, 5 im Modul), die Prüfsummen ohne Diff. (4) Der Umzug als reiner Move vor jeder Inhaltsänderung hielt die Rename-Erkennung ([AGENTS.md](../../../../AGENTS.md) §3.3). (5) Messen am Wegwerf-Stand vor dem Umbau (M1, M2, M4) ersparte, die ADR nach dem Umbau zu ändern.
- **Was ging anders als geplant:** (1) Die Wurzel-`test` liest die Datei der Modul-Task statt einer auflösbaren Konfiguration — **benannte Abweichung von [ADR-0034](../../adr/0034-testserver-als-gradle-modul.md) Entscheidung 8**, vom Nutzer hingenommen (2026-11-06); gemessen ist die Dateilösung (M3.3, M4), die Konfigurationslösung ist nicht gefahren; die ADR bleibt unverändert. (2) Der Plan nannte die zweiten Fundstellen des Begriffs „Source-Set `testserver`“ nicht; der Review fand sie (F-1) und der Nachzug (`354215d3`, `ca952e3b`) behob sie. (3) Zwei angenommene ADRs führen den alten Ort in der Fitness Function (F-2); **benannte Grenze statt Folge-ADR** (Entscheidung des Nutzers, 2026-11-06): [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) (Zeile 188) und [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) (Zeile 66) bleiben unverändert und gelten mit dem Ort „Modul `:testserver`“; kein Sensor hält die Gleichheit der Lesart.
- **Review und Verifikation:** Review `1c35a59d` (0 HIGH, 3 MEDIUM F-1 bis F-3, 3 LOW F-4 bis F-6, 6 INFO F-7 bis F-12): F-1, F-3 bis F-7, F-10 und F-12 behoben in `ca952e3b`, F-2 und F-3 (Abweichung) als Nutzerpunkte (a) und (b) entschieden und oben geführt, F-8, F-9, F-11 INFO unverändert (Rest-Lücke der Sperre ist im Skriptkopf benannt; `.dockerignore` ohne `**/` nicht gefahren). Verifikation `4ea7f4f0`: DoD-konform. Berichte: `docs/reviews/2026-11-06-661740c6-slice-testserver-gradle-modul.md`, `docs/reviews/2026-11-06-ca952e3b-verifikation-slice-testserver-gradle-modul.md`.
- **Gemessen** (Einzelmessungen am 2026-11-06, ein Host, Last nicht konstant, kein Beleg für andere Hosts): Verifikation am Stand `ca952e3b`: `make docs-check` 844 Dateien, 0 Befunde; `make gates FORCE=1` Exit 0 mit **Cache-Falle G1** (alle Docker-Schichten `CACHED`: der Lauf belegt Stempel, Host-Selbsttests und Doku, nicht frische Gradle-Arbeit); Frischbeleg `make clean-build` Exit 0, 8 min 40 s, 933 Zeilen `PASSED` (gleich M4.2); `make e2e` Exit 0, sieben Stufen, 305 s; `make testserver-selbsttest` Exit 0, 70 s, alle Mutationen rot. Nicht von der Verifikation wiederholt: `make deps-lock` (Netz), Kaltläufe von `make build` und `make smoke` einzeln. Closure-Lauf: `make docs-check` und `make gates` siehe Zeile „Closure-Lauf“ unten.
- **Stufe 7 von `make e2e`:** Aus den vier Läufen des Protokolls (E1.1 alte Testdatei mit Modul rot, E1.2 alte Testdatei ohne Modul rot, E1.3 neue Testdatei von `main` ohne Modul grün, E2.2 neue Testdatei mit Modul grün) folgt: der Wechsel von rot auf grün hängt an der Testdatei von `main`, nicht am Modul (Review F-10, im Protokoll nachgezogen).
- **Nicht erklärt:** M7: die Last des Hosts (3,5 bis 16, zum Ende 25) ist nicht geklärt; ein Ausreißer von `make build` nachher (86 s gegen 34 bis 40 s) ist nicht untersucht; die Spannen von `make smoke` überlappen nicht (45/50 s gegen 51/51 s, Abstand 1 s, kleiner als die Streuung bei `make laufzeittest`); keine Schwelle gilt. Weiter nicht gemessen: Java-Quellen und Reflection im Modul, Kaltläufe, Prozess von `make testserver-up`.
- **Zwei Bindungsformen** (§6): als Mangel entfallen; keine Beobachtung eingetragen, weil sie vom Nutzer so entschieden ist ([ADR-0034](../../adr/0034-testserver-als-gradle-modul.md)).
- **Steering-Loop-Eintrag:** Kein Eintrag verkörpert, daher kein `liegt in`; der Sensor `tools/modulgrenze-sperre.sh` ist Liefergegenstand und trägt keinen Herkunfts-Anker. Gelernt: (1) Wenn ein Umbau den Ort eines Prüfgegenstands verschiebt, stehen die alten Ortsangaben auch in unveränderlichen angenommenen ADRs; eine Folge-ADR ist dafür nicht der einzige Weg, eine benannte Grenze mit Datum und Entscheidung des Nutzers genügt — gezählt als neue Klasse `BEO-TDS/angenommene-adr-fuehrt-ortsangabe-nach-umbau` (benannte Grenze, nicht Spec-Lücke). (2) Die Review-Prüfung gegen die ADR gehört auf **jede Entscheidung** der ADR, nicht nur auf die Messpunkte: Entscheidung 8 wich ab, ohne dass Plan oder Protokoll es nannten, bis der Review sie las — `BEO-TDS/umsetzung-weicht-von-adr-entscheidung-ab` (neu, 1×). (3) `make gates` ist bei vollem Layer-Cache kein Frischbeleg; die Verifikation führte den Frischbeleg (`make clean-build`) selbst — `BEO-TDS/test-stage-cache-verdeckt-wiederholung` (verkörpert, Evidenz ergänzt). (4) Die zweite Fundstelle des Begriffs fand wieder der Review, nicht der Plan — `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (verkörpert im Reviewer-Skill, nur Evidenz ergänzt, kein Nachtrag an der Regel).
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-testserver-gradle-modul.md` ergänzt in [BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen](../observations/BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen/observation.md) (F-1; 13 Dateien, verkörpert), [BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md) (F-3, F-4, F-10, F-12; verkörpert), [BEO-TDS/kommentar-nennt-abwesenden-oder-unwirklichen-zustand](../observations/BEO-TDS/kommentar-nennt-abwesenden-oder-unwirklichen-zustand/observation.md) (F-3, F-6; 5 Dateien, über der Schwelle, nicht verkörpert, Lese-Schritt bei der Closure von `welle-testserver-manuell`), [BEO-TDS/test-stage-cache-verdeckt-wiederholung](../observations/BEO-TDS/test-stage-cache-verdeckt-wiederholung/observation.md) (V-1; verkörpert). Neu angelegt: [BEO-TDS/angenommene-adr-fuehrt-ortsangabe-nach-umbau](../observations/BEO-TDS/angenommene-adr-fuehrt-ortsangabe-nach-umbau/observation.md) (F-2, Nutzerpunkt (a), 1×) und [BEO-TDS/umsetzung-weicht-von-adr-entscheidung-ab](../observations/BEO-TDS/umsetzung-weicht-von-adr-entscheidung-ab/observation.md) (F-3, Nutzerpunkt (b), 1×). Zähler nicht gesetzt. Gesichtet und ohne neue Evidenz: `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` (die Lesart dieses Slice ist vom Nutzer am 2026-11-06 bestätigt, §1), `BEO-TDS/java-quelle-umgeht-kotlin-regel` (Grenze bleibt), `BEO-TDS/jackson-abhaengigkeit-fuer-embedder-nicht-gemessen` (Lock des Moduls trägt die Zählung, kein Gate), `BEO-TDS/katalog-und-lock-ohne-gegenpruefung` und `BEO-TDS/eingangsliste-je-stage-von-hand` (nicht eingetreten).
- **Folge-Slices:** [slice-oeffentliche-flaeche-verkleinern-und-belegen](../in-progress/slice-oeffentliche-flaeche-verkleinern-und-belegen.md) (Start-Trigger „Modul-Slice in `done/`“ erfüllt; sein Sensor liest `testserver/kotlin`), [slice-katalog-und-ddl-zustand](../open/slice-katalog-und-ddl-zustand.md), [slice-schreibpfad-bulk-load](../open/slice-schreibpfad-bulk-load.md), [slice-schreibpfad-merge-output](../open/slice-schreibpfad-merge-output.md) (jeweils über die Kette; schreiben den neuen Pfad), [slice-adr-handle-grenze-ersatz](../done/slice-adr-handle-grenze-ersatz.md) (unabhängig).
- **Risiken aus §6:** je Zeile eingetragen: M1, M4, M3, M5, M6 und Nummer der ADR entfallen; Zwei Bindungsformen entfallen als Mangel; Abweichung von Entscheidung 8 eingetreten, hingenommen; laufende Arbeiten am alten Pfad und die Ortsangabe in [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md)/[ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) weiter offen als benannte Grenze.
- **Drei Paarungen:** Das Repo führt Wellen; die Paarungen prüft die Closure von [welle-testserver-manuell](../welle-testserver-manuell.md). Anker ↔ Folge-Slice: kein Anker gesetzt. Register ↔ Closure-Notiz: alle genannten Kennungen existieren als Verzeichnis mit nicht leerem `evidence/` (geprüft am Dateisystem). Folge-Slices: alle existieren als Datei im Planning-Lifecycle.
- **Closure-Lauf:** `make docs-check` nach dem Move: 855 Dateien, 0 Befunde. `make gates` Exit 0, 6 s: die Klassen `werkzeug`, `tests` und `stempel` wurden mit Beleg übersprungen (Stempel auf dem Code des Verifikationsstands, der Closure-Diff berührt nur Dokumente), gelaufen sind `docs-check`, `gate-zuordnung`, `gate-zuordnung-selbsttest` (33 Fälle) und `stempel-eingaenge`. Der Lauf belegt damit keine frische Gradle-Arbeit; der Frischbeleg ist `make clean-build` der Verifikation (oben).

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung — dort die **zwei vorgelagerten
Schritte** und die **vier Pflichtkriterien**.

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); der Slice berührt Build, Dockerfile, Stempel-Listen, Skripte, den Testserver und die Spezifikation, eine eigene Sub-Area besteht nicht.

**Vorgelagert — offene Beobachtungen sichten:** Namensliste des Registers durchgegangen, Texte nicht einzeln gelesen. Treffer: `BEO-TDS/katalog-und-lock-ohne-gegenpruefung` (Lock), `BEO-TDS/eingangsliste-je-stage-von-hand` (Stempel-Listen), `BEO-TDS/java-quelle-umgeht-kotlin-regel` (Grenze des Nachweises bleibt), `BEO-TDS/jackson-abhaengigkeit-fuer-embedder-nicht-gemessen` (der Lock des Moduls trägt die Jackson-Aussage zusätzlich), `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` (Lesart der Nutzerentscheidung vom 2026-11-06). Zähler nicht gesetzt; sie folgen aus den Dateien.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
