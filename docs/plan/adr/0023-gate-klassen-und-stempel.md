# ADR-0023: Gate-Klassen und Stempel — Überspringen nur mit Beleg

**Status:** Proposed

**Datum:** 2026-10-31

**Autor:** pt9912

**Bezug:** [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0008](0008-dotnet-kompatibilitaetstests.md), [AGENTS.md](../../../AGENTS.md) §3.6

**Schärft:** — (Prozess-ADR; keine Spec-Stelle legt die Gate-Struktur fest, die Spec wird nicht angefasst)

**Regeln:** Baseline-Regelwerk `modul-04-adrs.md`
§Ziel-Form: ADR (MADR).

---

## Kontext

**Nutzerziel vom 2026-10-31:** „die gates müssen performant sein — wir sollten zwischen Werkzeug-Gate und Test-Gate unterscheiden und jeweils einen Stempel reservieren“; „man kann den sha und den commit-hash passend kombinieren“; „noch liegt der hash in .harness/state, aber das können wir doch jetzt besser machen“.

**Ist-Zustand** (aus den Fragmenten gelesen):

- `GATE_CHECKS` führt `test`, `detekt`, `build`, `detekt-selbsttest`, `skript-selbsttest`, `docs-check`, `baseline-verify` und `referenztest-dotnet`. E2E-Läufe stehen nach [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md) nicht darin.
- Zwei Gates überspringen sich schon heute bei unveränderten Eingängen, jedes mit eigenem Mechanismus unter `.harness/state/` (nicht versioniert): `referenztest-dotnet` mit einem Inhalts-Hash über Dateien (`tools/eingangs-hash.sh`), `detekt-selbsttest` mit einem mtime-Stempel über eine Eingangsliste. Beide Stempel sind privat je Arbeitskopie und reisen nicht; ein frischer Klon und jeder CI-Lauf führen alles aus.
- Der Gate-Nachweis (`record-gates`, Stop-Hook) besteht aus dem Inhalts-Hash des Gesamtbaums und dem HEAD (`.harness/state/gates-passed.*`). Er sagt „`make gates` war über genau diesem Inhalt grün“, nicht, welches Gate welche Eingänge hatte.
- Die Dockerfile-Stage `test` kopiert `spec/spezifikation.md` und `harness/README.md` (für Kopplungstests wie `IntegrationZahlenTest`), ebenso `spec-abdeckung` und `lock`. **Hypothese, nicht gemessen:** eine reine Doku-Änderung macht den Layer der Stage `test` ungültig und löst sie aus (Stage `test` ohne Cache: 89 bis 102 s, Einzelmessung aus [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md)). Eine Messung läuft und wird nachgereicht; die Entscheidung unten hängt nicht an ihr, die Umsetzung (Folgepflicht 3) schon.
- `.github/workflows/gates.yml` führt allein `make gates`, mit `fetch-depth: 0` und `permissions: contents: read`.

**Regel:** [AGENTS.md](../../../AGENTS.md) §3.6 — Gates werden nur per ADR gelockert. Ein Gate, das bei gleichem Eingang nicht läuft, ist keine Senkung der Schwelle, solange der Beleg für genau diesen Eingang vorliegt; wo er nicht vorliegt, läuft es. Die Zuordnung der Targets zu Klassen und der Beleg-Mechanismus sind trotzdem eine Entscheidung über die Gate-Kette und gehören in eine ADR.

**Annahme, die kippt:** Dass ein Gate-Ergebnis eine Funktion seiner Eingänge ist (Docker-Build mit gepinnten Digests und Lock). Ein Gate mit verstecktem Eingang (Netz, Uhrzeit, Tool aus dem Host) darf nicht übersprungen werden; das gilt für jede Klasse unten.

## Entscheidung

Wir wählen **Option B** (zwei Klassen, ein Stempel je Klasse, Beleg am Commit), mit Speicherort (c) lokal und (d) in der CI.

1. **Zwei Gate-Klassen, dazu eine eigene Einheit.**
   - **Werkzeug-Gate:** prüft Doku, Harness und Skripte: `docs-check`, `baseline-verify`, `detekt-selbsttest`, `skript-selbsttest` und — nach der Entkopplung (Folgepflicht 3) — die Kopplungstests Doku gegen Doku (`IntegrationZahlenTest`-Art). Eingänge: `docs/`, `spec/`, `harness/`, `tools/`, `.d-check.yml`, `config/detekt/selbsttest*`, dazu `Makefile`, `repo.mk` und `.harness/baseline/`.
   - **Test-Gate:** prüft den Produktcode: `test`, `detekt`, `build` (mit der Manifest-Prüfung in `build-jar`). Eingänge: `src/`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/`, `gradle.lockfile`, `settings-gradle.lockfile`, `Dockerfile`, `config/detekt/`, `LICENSE`.
   - **`referenztest-dotnet`** bleibt Gate ([ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md) Entscheidung 4, nicht berührt) und ist eine eigene Einheit mit eigenen Eingängen (`referenztest-dotnet/` und sein Fragment). Es bekommt denselben Stempel-Mechanismus statt der eigenen Hash-Datei.
   - Die Eingangsliste einer Klasse ist die **Vereinigung der Eingänge ihrer Targets**, abgeleitet aus den `COPY`-Quellen der Stages und den Fragmenten; die Listen oben sind der Startstand der Umsetzung. Heute liest `detekt-selbsttest` (Stage erbt von `deps`) auch `Dockerfile` und die Gradle-Dateien: eine Doku-Änderung berührt es nicht, eine Gradle-Änderung schon. Diese Überschneidung ist erlaubt (sie kostet einen Lauf, nie eine falsche Auslassung).
2. **Stempel = Paar aus Eingangs-Hash und Commit.** Der **Eingangs-Hash** einer Klasse ist der SHA-256 über die Zeilen `<Pfad> <Git-Tree-ID>` aller Eingangspfade, mit `git rev-parse <Commit>:<Pfad>` bestimmt (sortiert; ein fehlender Pfad ist ein Fehler, kein leerer Wert). Das liest keine Dateien und ist deterministisch. Der **Commit** ist die Herkunft: der Commit, über dessen Baum der grüne Lauf der Klasse lief.
3. **Der Beleg ist der Stempel an einem Commit.** Er sagt: „Klasse K, Eingangs-Hash H, grün, an Commit C“. Er wird nur nach einem grünen Lauf der Klasse geschrieben, und nur wenn der Arbeitsbaum in den Eingangspfaden der Klasse sauber ist (versioniert, unversioniert und ignoriert) und der Eingangs-Hash vor und nach dem Lauf derselbe ist.
4. **Überspringen nur bei Beleg.** Eine Klasse läuft nicht, wenn ein Vorfahre C von HEAD (HEAD eingeschlossen) denselben Eingangs-Hash der Klasse hat **und** für C ein grüner Beleg der Klasse vorliegt. **Jeder andere Fall führt zum Lauf:** kein Treffer, flacher Klon, Abfragefehler, Eingangspfade im Arbeitsbaum nicht sauber, Beleg nicht eindeutig grün (mehrere, widersprüchliche, unlesbar), umgeschriebene Historie (Rebase, Squash). Ein Fehler beim Nachschlagen ist weder rot noch grün des Gates, er ist nur „Lauf“. `FORCE=1` erzwingt den Lauf wie heute bei `referenztest-dotnet`.
   - Setzung: die Vorfahren-Bedingung ist strenger als nötig. Inhaltlich trägt der Eingangs-Hash allein; der Vorfahr hält die Herkunft prüfbar und verhindert, dass der Beleg eines fremden Zweigs trägt. Wer sie lockert, braucht eine neue Entscheidung.
5. **Bindung an den Gate-Nachweis bleibt unverändert.** `record-gates` und der Stop-Hook behalten Gesamtbaum-Hash und HEAD als Nachweis; nichts daran wird aufgeweicht. `make gates` ist grün, wenn jede Klasse entweder grün lief oder mit Beleg übersprungen wurde; erst dann wird der Nachweis geschrieben. Eine übersprungene Klasse ohne passenden Beleg gibt es nicht (Entscheidung 4).
6. **Speicherort des Belegs** (Optionen unten): lokal **(c) git notes** unter `refs/notes/gates` am grünen Commit; in der CI **(d) der Check-Status** des Workflows `gates` am Commit. `.harness/state/` behält den Gate-Nachweis des Stop-Hooks (Entscheidung 5), nicht die Klassen-Stempel; die beiden bestehenden Einzelmechanismen (`detekt-selbsttest.ok`, `referenztest-dotnet.hash`) entfallen zugunsten des einen.
7. **CI.** Ein grüner Check `gates` an einem Vorfahren C belegt alle Klassen für C. Für die Abfrage braucht `gates.yml` zusätzlich ein Leserecht auf Checks (`checks: read`; ob `contents: read` genügt, ist nicht geprüft) und das Token in der Umgebung des Abfrageschritts. **Das ist ein Rechtezuwachs** gegenüber dem heutigen „Der Lauf liest nur“ (Lesen weiterer Daten, kein Schreibrecht, keine Secrets). Das Token steht nur dem Abfrageschritt zur Verfügung, nicht dem Docker-Build. Die Lastenheft-Messmethode von [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) nennt „Clean-Build und Integrationstests in CI“: **ein Push auf `main` und ein Release-Stand laufen deshalb immer voll** (Setzung); Überspringen mit Beleg gilt für Pushes auf andere Zweige und Pull Requests.
8. **Reihenfolge der Umsetzung** (nach `Accepted`): zuerst lokal (c), danach die CI (d). Bis (d) umgesetzt ist, läuft die CI voll und verliert nichts.

## Verglichene Alternativen

**Zur Gate-Struktur:**

| Option | Pro | Contra |
|---|---|---|
| A — nichts tun | Kein Aufwand; kein Fehlerpfad „fälschlich übersprungen“ | Jede Doku-Änderung kann die Stage `test` auslösen (Hypothese, Messung steht aus); zwei private, unterschiedliche Mechanismen bleiben; CI und frische Klone laufen immer alles |
| **B — zwei Klassen, ein Stempel je Klasse, Beleg am Commit** | Folgt dem Nutzerziel; eine Doku-Änderung berührt das Test-Gate nicht (nach Entkopplung), eine Quelländerung nicht das Werkzeug-Gate; ein Mechanismus für alle; Beleg reist (c, d) | Eingangslisten sind eine zweite Wahrheit, die driftet (Gegenmittel: Selbsttest, Fitness Function); Fehlerpfade sind zu bauen und zu testen |
| C — ein Stempel für alle Gates | Einfachster Mechanismus | Eine Doku-Änderung löst Tests aus, eine Quelländerung die Doku-Prüfung; verfehlt das Nutzerziel |
| D — Cache der CI (`actions/cache`, Schlüssel = Hash) | Kein Rechtezuwachs, keine Notes | Gate wird aus einem Cache übersprungen; ein vergifteter Cache wäre ein grünes Gate ohne Lauf; der Cache altert und ist auf Zweige verteilt; löst die lokale Seite nicht |

**Zum Speicherort des Belegs:**

| Option | Pro | Contra |
|---|---|---|
| (a) `.harness/state/` wie heute | Kein Aufwand; privat | Reist nicht (CI, andere Klone, Worktrees teilen ihn nicht); an keinen Commit gebunden, nur an den Inhalt |
| (b) eingecheckte Datei | Reist mit dem Commit | Merge-Konflikte bei parallelen Zweigen; der Autor setzt die Aussage selbst, ein Commit mit „grün“ vor dem Lauf wäre möglich; ändert den Eingang, den sie belegt — **nicht empfohlen** |
| **(c) git notes `refs/notes/gates` am grünen Commit** | Lokal schreibbar, ohne den Commit zu ändern; an den Commit gebunden; pushbar | Notes werden nicht automatisch gezogen oder gepusht; Rebase, Squash und Amend lassen sie am alten Commit zurück (dann Lauf); wer schreiben kann, kann einen Beleg setzen (gleiche Vertrauensgrenze wie `.harness/state/` heute) |
| **(d) Check-Status der CI am Commit (`gates` grün)** | Für die CI der Beleg, der ohne Vertrauen in einen lokalen Autor trägt; nichts im Repo zu pflegen | Braucht Netz, Token und Leserecht (Rechtezuwachs, Entscheidung 7); ein Squash-Merge erzeugt einen neuen Commit ohne Check; die Abfrage kann ausfallen (dann Lauf) |

Empfehlung: lokal (c), in der CI (d).

## Konsequenzen

- Positiv: eine Änderung an Doku oder Harness löst das Test-Gate nicht mehr aus (sobald die Stage `test` von `spec/` und `harness/README.md` entkoppelt ist); eine Änderung an `src/` löst `docs-check` nicht aus; ein Checkout, Merge oder Rebase ohne Inhaltsänderung löst nichts aus (bei vorhandenem Beleg); ein Mechanismus ersetzt zwei.
- Negativ: siehe „Akzeptierte Negative“.
- Folgepflicht (nach `Accepted`, durch einen Implementer, nicht durch diese ADR):
  1. Zuordnung der Targets zu den Klassen in den Fragmenten festlegen; die Eingangslisten je Klasse aus den `COPY`-Quellen ableiten; `harness/README.md` nennt Klassen und Bindung auf diese ADR.
  2. Ein Stempel-Skript (Eingangs-Hash, Beleg schreiben, Beleg nachschlagen, Fehlerpfade nach Entscheidung 4) und ein Selbsttest, der jeden Fehlerpfad einzeln belegt (flacher Klon, schmutziger Baum, Fehler der Abfrage, widersprüchlicher Beleg, umgeschriebene Historie, geänderte Datei in den Eingängen).
  3. Entkopplung der Stage `test` von `spec/spezifikation.md` und `harness/README.md`: die Kopplungstests wandern ins Werkzeug-Gate; vorher die laufende Messung der Hypothese auswerten, nach der Umsetzung nachmessen.
  4. Migration von `referenztest-dotnet` und `detekt-selbsttest` auf den Stempel; `tools/eingangs-hash.sh` bleibt, wo ein Hash über Dateien nötig ist, oder entfällt mit Begründung.
  5. CI: Abfrageschritt und Rechte in `gates.yml` nach Entscheidung 7; `make workflow-lint` fahren.
  6. Messung der Gate-Laufzeiten vorher und nachher (warm und kalt) als Beleg; ohne Messung trägt keine Laufzeitaussage.

## Akzeptierte Negative

1. **Rebase, Squash und Amend schreiben Commits um:** der Beleg liegt am alten Commit, der Vorfahren-Test findet ihn nicht, die Klasse läuft voll. Kosten: ein Lauf; der Fehler irrt zur sicheren Seite. Squash-Merges auf `main` laufen in der CI voll (Entscheidung 7).
2. **Ein schmutziger Eingangsbaum läuft immer voll** und schreibt keinen Beleg. Der übliche Ablauf „ändern, `make gates`, committen“ spart beim ersten Lauf nichts; er spart beim nächsten Commit, der die Klasse nicht berührt.
3. **Ein Gate wird auf Grundlage eines Belegs übersprungen.** Ein falscher Beleg (von Hand gesetzte Note, unvollständige Eingangsliste, versteckter Eingang) ergibt ein grünes Gate ohne Lauf. Begrenzt durch: Schreiben nur nach grünem Lauf, Selbsttest der Eingangsliste gegen die `COPY`-Quellen, Vollauf auf `main`. Nicht ausgeschlossen: ein von Hand gesetzter Beleg; die Vertrauensgrenze ist die des Schreibrechts auf das Repo.
4. **Aufwand:** Stempel-Skript, Selbsttest, Migration und CI-Anpassung sind eigene Arbeit; die Eingangslisten sind zu pflegen.
5. **Die Eingangslisten der Klassen überschneiden sich** (Entscheidung 1, letzter Punkt): eine Änderung in `Dockerfile` oder `gradle/` löst beide Klassen aus. Akzeptiert, weil es zur sicheren Seite irrt.
6. **Die Abfrage des CI-Checks hat einen Ausfallpfad und einen Rechtezuwachs** (Entscheidung 7); bis (d) umgesetzt ist, läuft die CI voll.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| Selbsttest des Stempel-Skripts (nach Umsetzung) | jeder Fehlerpfad der Entscheidung 4 endet im Lauf, nie im Überspringen; eine Datei in den Eingängen ändert den Eingangs-Hash | heute kein Sensor; nicht behauptet |
| Prüfung der Eingangsliste (nach Umsetzung) | jede `COPY`-Quelle einer Stage der Klasse steht in deren Eingangsliste | heute kein Sensor; nicht behauptet |

## Re-Evaluierungs-Trigger

- Ein Gate lief auf Grundlage eines Belegs nicht und hätte rot sein müssen (Eingang vergessen, Beleg falsch): die Entscheidung wird neu bewertet; Vorfahren-Bedingung und Vollauf-Regeln sind die ersten Kandidaten zur Verschärfung.
- Die Messung nach der Entkopplung zeigt keinen Gewinn (Doku-Änderungen lösen die Test-Stage weiter aus, oder die Gates sind warm ohnehin billig): Aufwand und Nutzen neu abwägen, im Zweifel Option A.
- Ein Gate bekommt einen Eingang außerhalb des Repos (Netz, Host-Tool): es verlässt die Stempel-Logik.
- Die Plattform ändert Rechte oder Ablauf der Check-Abfrage, oder der Aufwand der Notes (Push, Fetch) wird spürbar: Speicherort neu bewerten.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-10-31 | Nutzerziel „Gates performant; Werkzeug-Gate und Test-Gate trennen, je ein Stempel; SHA und Commit kombinieren; Hash nicht mehr nur in .harness/state“ | Sitzung |
| 2026-10-31 | Proposed | — |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
