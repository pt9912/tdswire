# Slice slice-e2e-backend-ausserhalb-modul: E2E-Stufe für ein eigenes Backend und seinen Fehlpfad gegen den Serverprozess

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend) (Scope); [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) (öffentlicher Integrations-Einstieg), [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) (E2E-Läufe nicht in den Gates)

**Berührte Spec-Stellen:** `SPEC-025` (Zahl der Stufen des E2E-Durchgangs in der Zelle „Lauf“ und Zeile im Änderungsprotokoll; Nachzug an den Bestand, kein neuer Vertrag)

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-11-05.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice.

**Ziel:** Eine E2E-Stufe belegt für [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung) und [TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend), was die Sicht heute in keiner Stufe deklariert: ein eigenes In-Memory-Backend (eine eigene `QueryBackend`-Implementierung, die nicht an das Simulations-Backend delegiert) wird über die öffentliche Fläche angebunden und im Serverprozess von einem Client erreicht, und ein eigenes Backend, dessen `execute` scheitert oder das nicht erreichbar ist, ergibt einen definierten Fehler, wobei der Server für weitere Verbindungen verfügbar bleibt; die Stufe steht in `tools/e2e.sh` und in der erzeugten E2E-Abdeckungs-Sicht.

**Bestand und Abgrenzung gegen Stufe 4:** Stufe 4 von `make e2e` (und 5 bis 7) läuft gegen `kompatibilitaetsserver` im Modul `testserver` (bindet das Hauptmodul als Projekt-Abhängigkeit, nur öffentliche Fläche, `BackendSource.Custom`), mit dem Referenzclient über die Prozessgrenze. Der dort angebundene `RecordingBackend` zeichnet auf und delegiert an das Simulations-Backend; Stufe 4 deklariert FA-08 und RB-04 nicht. Backend-Fehler bei offener Verbindung liefern die Stufen 3, 4 und 7 (50004, `TDS_BACKEND_ERROR`, Folgeabfrage gelingt), aber über die Simulation; es fehlt ein eigenes `QueryBackend`, dessen `execute` scheitert oder das nicht erreichbar ist, mit der Prüfung, dass der Server verfügbar bleibt. Die neue Stufe wiederholt Stufe 4 nicht: sie wiederholt weder die Szenarien noch den Aufzeichner, sondern belegt das Backend, das nicht die Simulation ist, und den Fehlpfad eines solchen Backends. Zeigt die Umsetzung, dass eine Ergänzung der Kennungen an Stufe 4 mit einem zusätzlichen Fall derselben Kategorie genügt, ist das die zu wählende Form (keine doppelte Stufe, Änderung von §2 und `tools/e2e.sh` dann mit Begründung in §7).

**Träger-Slice:** [slice-integrations-einstieg](../done/slice-integrations-einstieg.md) (liegt in `done/` und führt keinen E2E-Punkt); Zeiger und Befund stehen in [slice-e2e-geruest](../done/slice-e2e-geruest.md) §3 und §7.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Ein Verbraucher außerhalb des Gradle-Projekts gegen das gebaute Jar — `internal` ist im Bytecode öffentlich, der Nachweis der Source-Set-Grenze gilt für Kotlin-Quellen ([ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)); ein Lauf außerhalb des Projekts wäre ein eigener Zuschnitt und wird hier nur aufgenommen, wenn die Umsetzung ihn billig trägt.
- Änderungen am öffentlichen Einstieg selbst — er ist durch [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) und den Träger-Slice geliefert; dieser Slice belegt ihn nur über die Prozessgrenze.
- Weitere Backend-Verträge über die Anforderungen [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung) und [TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend) hinaus — der Schnitt ist eine Stufe, kein Vertragsausbau.
- Die Entscheidung, ob `make e2e` ein Gate wird — bleibt bei [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — ≤ 3 Liefer-Punkte.

- [x] **Backend und Lauf:** Ein eigenes Backend (nicht die Simulation, nur öffentliche Fläche, im Modul `testserver` oder einem eigenen ohne Assoziation) wird im Serverprozess angebunden; ein Referenzclient erhält Antworten aus ihm (leeres und gefülltes Resultset) und für ein fehlerhaftes oder nicht erreichbares Backend einen definierten Fehler, der Server bleibt verfügbar; der Lauf ist ein `make`-Target oder eine Kategorie von `make kompatibilitaet`.
- [x] **Stufe und Sicht:** `tools/e2e.sh` führt das Target als Stufe mit Deklaration `e2e_abdeckung` (Kennungen [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend); Kurzbeschreibung mit „Nicht belegt: …“); `make e2e-abdeckung` schreibt die Sicht; Zeile in `harness/README.md` §Sensors.
- [x] **Laufzeit:** Die Zeit der neuen Stufe ist gemessen (Einzelmessung mit Datum, Last und Cache-Zustand, §7).
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist (`SPEC-025`, `README.md`, `harness/README.md`, `tools/mk/e2e.mk`; kein öffentlicher Vertrag des Einstiegs berührt).
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben oder „keine Beobachtung angefallen“ in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area?

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| Eigenes Backend und Server-`main` im Modul `testserver` (Ort legt der Implementer fest) | neu | nur öffentliche Fläche; belegt [TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend); neben `KompatibilitaetsServer.kt`, nicht darin |
| `tools/e2e.sh` | update | neue Stufe mit Deklaration; Aufruf als `make <Target>` |
| `tools/mk/` (Fragment des Targets) | neu | Target der Stufe; Zeile in `harness/README.md` §Sensors |
| `docs/plan/e2e-abdeckung.md` | update (erzeugt) | `make e2e-abdeckung`; nicht von Hand |
| `Dockerfile` (Stage `eigenesbackendserver`), `referenztest-dotnet/Dockerfile` (Stage `eigenes-backend`), `testserver/build.gradle.kts` (Task `eigenesBackendServer`) | update | Server- und Client-Image und Gradle-Task des Laufs |
| `referenztest-dotnet/EigenesBackend/EigenesBackendTests.cs`, `tools/eigenes-backend-test.sh` | neu | Tests des Referenzclients und Lauf mit Zählung am Log |
| `testserver/test/kotlin/.../EigenesBackendTest.kt` | neu | Antworten des Fixtures ohne Server (läuft in `make test`; kein `@spec`-Tag, er belegt nicht den Einstieg) |
| `tools/e2e-selbsttest.sh` | update | acht Stufen, Fall „Rot in Stufe 8“ |
| `README.md`, `harness/README.md` (Zellen `make e2e`, `make eigenes-backend`), `tools/mk/e2e.mk` | update | Stufenzahl, Zeile des Targets |
| `spec/spezifikation.md` (`SPEC-025`, Änderungsprotokoll) | update | „Stufen 3 bis 8“ |
| `docs/plan/planning/welle-produktschnitt.md`, `docs/plan/planning/open/slice-betrieb-log-adapter-testserver.md` | update | zweite Fundstellen der Stufenzahl |

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-e2e-geruest](../done/slice-e2e-geruest.md) liegt in `done/`; `make gates` ist auf `main` grün.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Die öffentliche Fläche reicht für den Prüfling nicht; dann ist das ein Befund zu [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md), nicht in diesem Slice zu lösen.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln.

Alle Punkte der DoD in §2 erledigt, `make e2e` grün mit der neuen Stufe, `make gates` grün, Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst.

- Die Stufe deklariert mehr, als ihr Lauf belegt (Kennung breiter als der Test) — Gegenmittel: „Nicht belegt“ in der Kurzbeschreibung, Abgleich im Review. — **Ausgang:** eingetreten in kleinem Maß: der Review fand zwei Aussagen breiter als der Lauf (F-2 Log-Prüfung ohne Quelle, die schreiben könnte; F-3 `Closed` am Client als Verhalten des Servers gelesen); beide am Text behoben (Deklaration, Skript, Testkopf), die Grenzen stehen in „Nicht belegt“ und unten in §7.
- Die Stufe dupliziert Stufe 4 oder nutzt unbemerkt `internal`-Zugriff (Source-Set mit Assoziation) — Gegenmittel: die Abgrenzung in §1, ein Source-Set ohne Assoziation (`integratorOhneAssoziation`-artige Prüfung). — **Ausgang:** entfallen für Delegation und Wiederholung (kein Simulations-Import, Modul `testserver` ohne `associate`/`friend` nach [ADR-0034](../../adr/0034-testserver-als-gradle-modul.md), Review Prüfpunkt 1, Verifikation §4); als Grenze bleibt (d) unten, die Mutation mit einem `internal`-Zugriff ist nicht gefahren.
- Laufzeit des Durchgangs wächst um die neue Stufe ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)); die Messung steht in §7 des Träger-Gerüsts. — **Ausgang:** eingetreten und gemessen: Stufe 8 kostet 13 bis 15 s im Durchgang (Einzelmessungen des Implementers und des Verifiers, unter verschiedener Last, nicht vergleichbar); der Durchgang aus acht Stufen: 366 s und 311 s. Eine Warm-/Kaltmessung bleibt im Register (`BEO-TDS/e2e-nachweis-ohne-stufe-oder-messung`).

## 7. Closure-Notiz

**Gegenstand:** geliefert: Stufe 8 von `make e2e` (`make eigenes-backend`): ein eigenes `QueryBackend` im Modul `testserver` (`EigenesBackendServer.kt`, ohne Delegation an die Simulation, nur öffentliche Fläche), im Serverprozess angebunden, vom Referenzclient im zweiten Container gelesen (gefülltes und leeres Ergebnis; `fehler` am Start und `fehler_nach_zeilen` mit `Number 50004, Class 16`, Verbindung danach nutzbar; `wirft` mit `Number 50007, Class 20`, Server beantwortet danach neue Verbindungen); Deklaration `TDS-FA-08 TDS-RB-04` mit „Nicht belegt“ in der erzeugten Sicht; `SPEC-025` auf „Stufen 3 bis 8“. Die Folge 50007/Class 20 bei werfendem `execute` ist keine bloße Messung, sondern durch `SPEC-048` festgelegt (unerwartete Ausnahme im Backend-Adapter, Severity 20, fester Text, Verbindungsende); der Satz des Reviews (F-7), sie sei „nirgends als Zusage festgelegt“, ist damit berichtigt (Verifikation V-3). Commits des Zweigs `worktree-agent-ae2dacd15aa73d2f5` (Basis `6b28cd69`): `b3b71d76` (Implementierung), `4436821d` (Messung, Haken), `b8e1f7b9`/`00f0a804` (Review), `0cd8db72` (Nachbesserung), `125a5456`/`8da14650` (Verifikation), danach die Commits dieser Closure. Berichte: [Review](../../../reviews/2026-11-06-4436821-slice-e2e-backend-ausserhalb-modul.md) (0 HIGH, 1 MEDIUM, 4 LOW, 4 INFO), [Verifikation](../../../reviews/2026-11-06-0cd8db72-verifikation-slice-e2e-backend-ausserhalb-modul.md) (konform, 0 Abweichungen, 4 Anmerkungen INFO).

**Gates (Belege):** Frischbeleg ist `make clean-build` (Verifier, `--no-cache`, Exit 0; Unit-Tests und detekt frisch, `EigenesBackendTest` beide Fälle PASSED), dazu `make e2e` mit acht Stufen grün (311 s) und `docker build --no-cache --target skript-selbsttest .` grün. `make gates` ist grün, stützt sich für die Gruppen `werkzeug`, `tests` und `stempel` aber auf Stempelbelege und ist deshalb für Tests und detekt kein Frischbeleg; `make docs-check` 0 Befunde. Lauf des Planners am Ende der Closure: siehe Abschluss unten.

**Befunde des Reviews und ihr Ausgang (Klassen):**

| Befund | Klasse | Ausgang |
|---|---|---|
| F-1 (MEDIUM) | zweite Fundstellen der Stufenzahl („sieben Stufen“ in `welle-produktschnitt`, `slice-betrieb-log-adapter-testserver`) | in `0cd8db72` nachgezogen; Verifikation §5 per Grep bestätigt |
| F-2 (LOW) | Prüfpunkt belegt nichts über die Bibliothek: die Log-Prüfung sieht nur Zeilen des Backends, die Bibliothek schreibt ohne Senke nichts | Name und Skriptkommentar auf die Grenze gebracht; die Aussage über die Bibliothek trägt `TelemetryRedactionTest` (`SPEC-048`), nicht diese Stufe |
| F-3 (LOW) | Nachweisgrenze: `Closed` ist ein Zustand des Clients, kein beobachtetes Schließen durch den Server | Deklaration, Skript und Testkopf berichtigt |
| F-4 (LOW) | `@spec SPEC-059` an einem Test des Fixtures, nicht des Einstiegs; die Matrix hätte zwei Nachweise gezählt, die nichts über den Einstieg sagen | Tags entfernt |
| F-5 (LOW) | Plan-Tabelle und Kopf „Berührte Spec-Stellen“ nennen berührte Dateien und `SPEC-025` nicht | Tabelle und Kopf ergänzt |
| F-6 (INFO) | Mutationen M1/M2 und Grenzen standen nicht im Repo | stehen unten; der Verifier hat M1 und M2 selbst gefahren und dieselben Ergebnisse gesehen (M1 3 von 4, M2 2 von 4) |
| F-7 (INFO) | 50007/Class 20 „nirgends als Zusage“ | berichtigt: `SPEC-048` legt es fest (siehe Gegenstand) |
| F-8, F-9 (INFO) | Messwerte reproduziert; `make kompatibilitaet` bleibt „Stufen 4 bis 7“ zu Recht | kein Ausgang nötig |

**Messung der Laufzeit (Einzelmessung, 2026-11-06, Implementer):** `make eigenes-backend` im Durchgang `make e2e` (Stufe 8): 13 s mit warmem Cache der Images (Last zu Beginn des Durchgangs 16,6 / 17,9 / 16,0, am Ende 7,9 / 13,1 / 14,9); der Durchgang aus acht Stufen: 366 s (Stufen 1 bis 7: 95, 87, 53, 46, 17, 18 und 37 s). Erstlauf des Targets mit Bau der beiden Images: 108 s (Bau 98 s, Tests 10 s). Kein Mittelwert, keine Streuung.

**Rot gesehen (Mutationen des Implementers, 2026-11-06, je ein Lauf von `make eigenes-backend`, danach zurückgenommen):**

| # | Mutation in `EigenesBackendServer.kt` | Ergebnis |
|---|---|---|
| M1 | Tabelle `fehler` liefert `ResultStart` und `ResultEnd(0)` statt eines `Failure` | Exit 2: Test „Ein_Fehler_des_Backends_am_Start …“ rot (3 von 4 bestanden), dazu die Zählung „6 Anfragen an eigen“ rot |
| M2 | Tabelle `wirft` beendet den Prozess (`Runtime.getRuntime().halt(1)`) statt eine Ausnahme zu werfen | Exit 2: Test „… der Server bleibt verfügbar“ rot (2 von 4 bestanden), neue Verbindungen scheitern mit „server not found“ |

**Nicht rot gesehen (Grenzen):** (a) Dass der Text des Backends und der Ausnahme den Client nicht erreicht: im .NET-Test assertiert (`DoesNotContain`), aber keine Mutation an der Fehlerabbildung der Bibliothek gefahren. (b) Die Stufenzählung `stufen=8` in `tools/e2e-selbsttest.sh`: nur grün gelaufen, keine Gegenprobe mit einem Skript mit neun Stufen. (c) Die Prüfung des Server-Logs auf Kennwort und Ausnahmetext sieht nur Zeilen des Backends (der Server läuft ohne Senke); sie kann nur rot werden, wenn das Backend selbst schreibt. (d) Dass das Modul nur öffentliche Namen nutzt, trägt die Übersetzung des Moduls ([ADR-0034](../../adr/0034-testserver-als-gradle-modul.md)); eine Mutation mit einem `internal`-Zugriff habe ich nicht gefahren.

**Weitere Grenzen der Closure:** (e) Die Nachbesserung `0cd8db72` ist nicht erneut gereviewt worden. Entscheidung, begründet: sie ändert keine Logik der Bibliothek und des Servers, sondern Text (Deklaration, Skript- und Testkommentare, Name eines Prüfpunkts), das Entfernen zweier `@spec`-Tags, die Plan-Tabelle und zwei Stufenzahlen; der Verifier hat jeden Befund F-1 bis F-6 am Text gegen den Review geprüft und danach `make clean-build`, `make e2e` und M1/M2 selbst gefahren. Ein zweiter Review durch einen dritten Kontext hat die Nachbesserung nicht gesehen; das ist die Grenze, kein Beleg. (f) Warm-/Kaltwert der acht Stufen und Vergleich mit den Einzelläufen sind nicht gemessen; die zwei Durchgänge (366 s, 311 s) stammen aus verschiedenen Lasten. (g) „Nicht erreichbar“ ist als `Failure` am Start des Backends modelliert; ein Backend, das beim Start des Servers nicht erreichbar ist, belegt die Stufe nicht (steht unter „Nicht belegt“). (h) Verbraucher außerhalb des Gradle-Projekts gegen das gebaute Jar: nicht Teil dieses Slice (§1).

**Steering-Loop-Eintrag:** gezählt, nicht verkörpert; der Planner verkörpert nichts still. Lerneintrag (Klassen der Befunde): (1) Ein Nachweis, der nur Zeilen einer Seite sieht (Log ohne Senke der Bibliothek; Zustand `Closed` des Clients), trägt nur diese Seite; die Deklaration der Stufe nannte die breitere Aussage, der Review fand sie über die Quelle der Zeilen. (2) Die bereits verkörperte Prüfung „Fortschreibung lässt die zweite Fundstelle stehen“ (`.harness/skills/reviewer.md`) traf wieder, als MEDIUM, an zwei Planungsdokumenten, die ein lebendes Ziel mit der alten Stufenzahl führten: die Regel wirkt, die Fundstellen kennt der Plan weiter nicht vorab. (3) Ein `@spec`-Tag an einem Test des Fixtures zählt in der Abdeckungsmatrix als Nachweis für den Einstieg. (4) Die Plan-Tabelle nannte wieder berührte Dateien nicht (4× im Register). Zähler und Vorschläge: Register unten.

**Vorschläge zu Schwellen-Übertritten (Entscheidung beim Nutzer, keine Regel geschrieben):** (a) `BEO-TDS/aussage-ueber-schichtgrenze-ohne-test` steht mit diesem Slice bei 3× und überschreitet die Schwelle; Vorschlag: Prüfpunkt im Reviewer-Skill (MEDIUM-Liste) „Aussage über das Verhalten einer Gegenseite ruht auf Zeilen, die nur die eigene Seite schreibt“ mit Anker `seit welle-qualitaet-grundlage`, oder bewusste Streichung mit Begründung; der Lese-Schritt der Welle-Closure entscheidet. (b) `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht` steht bei 4× (Stand im Register noch „offen“ trotz 3× vor diesem Slice); Vorschlag: Prüfpunkt „Plan-Tabelle gegen Diff abgleichen“ in den Reviewer-Skill (Plan-Review) oder Sensor, der Dateien des Diffs gegen die Tabelle in §3 listet; Spec-Straten und Hard Rules bleiben unberührt.

**Nachtrag Steering-Loop-Eintrag (2026-11-06, Entscheidung des Nutzers „deine empfehlungen“):** Die Vorschläge (a) und (b) darüber sind entschieden und als Prüfpunkte der MEDIUM-Liste verkörpert. (a) [BEO-TDS/aussage-ueber-schichtgrenze-ohne-test](../observations/BEO-TDS/aussage-ueber-schichtgrenze-ohne-test/observation.md) (3×): Prüfpunkt „Aussage über eine Gegenseite ruht auf Zeilen, die nur die eigene Seite schreibt“; Slice-Beleg: F-2, F-3. liegt in `.harness/skills/reviewer.md`

**Nachtrag Steering-Loop-Eintrag, zweiter Punkt (2026-11-06, Entscheidung des Nutzers „deine empfehlungen“):** (b) [BEO-TDS/plan-tabelle-nennt-neue-datei-nicht](../observations/BEO-TDS/plan-tabelle-nennt-neue-datei-nicht/observation.md) (4×): Prüfpunkt „Plan-Tabelle (§3) und Kopf ‚Berührte Spec-Stellen‘ gegen den Diff abgleichen“; ein Sensor für den Abgleich ist nicht entschieden und nicht gebaut; Slice-Beleg: F-5. liegt in `.harness/skills/reviewer.md`

**Beobachtungs-Register (`../observations/`):** Evidence `slice-e2e-backend-ausserhalb-modul.md` neu in [BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen](../observations/BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen/observation.md) (F-1), [BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md) (F-3), [BEO-TDS/aussage-ueber-schichtgrenze-ohne-test](../observations/BEO-TDS/aussage-ueber-schichtgrenze-ohne-test/observation.md) (F-2, jetzt 3×), [BEO-TDS/test-belegt-nur-den-fake](../observations/BEO-TDS/test-belegt-nur-den-fake/observation.md) (F-4) und [BEO-TDS/plan-tabelle-nennt-neue-datei-nicht](../observations/BEO-TDS/plan-tabelle-nennt-neue-datei-nicht/observation.md) (F-5, jetzt 4×). Keine neue Klasse: F-6 (Mutationen nicht im Repo) war ein Zeitpunkt vor der Closure, nicht ein Fehlen.

**Folge-Slices:** keine. Die Folgepunkte sind Grenzen (e) bis (h) und die Register-Vorschläge; ein Start-Trigger eines offenen Slice nennt diesen Slice nicht (Grep: nur die Reihenfolge-Verweise in `welle-testserver-manuell` §5 und im Risiko von `slice-schreibpfad-bulk-load`, Pfade vom Move nachgezogen).

**Drei Paarungen:** Anker: kein Anker gesetzt, weil nichts verkörpert wurde. Folge-Slice: keiner genannt. Register: jede genannte Kennung `BEO-TDS/<slug>` besteht als Verzeichnis, jedes trägt ein nicht leeres `evidence/`.

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Der Slice berührt das ganze Repo (`*`, Kürzel `TDS`); eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen; berührt: `BEO-TDS/test-belegt-nur-den-fake` (Prüfling gegen den Fake statt gegen den Prozess) und `BEO-TDS/aussage-ueber-schichtgrenze-ohne-test`.

Alle berührten Sub-Areas GF.
