# ADR-0024: Lokale Stempel als allgemeiner Mechanismus — genau zwei Stempel, Beleg als git note

**Status:** Proposed

**Datum:** 2026-10-31

**Autor:** pt9912

**Bezug:** [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0023](0023-gate-klassen-und-stempel.md), [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0008](0008-dotnet-kompatibilitaetstests.md), [ADR-0019](0019-kompatibilitaetstests-zuschnitt.md), [AGENTS.md](../../../AGENTS.md) §3.5, §3.6, §3.8

**Schärft:** — (Prozess-ADR; keine Spec-Stelle legt die Gate-Struktur fest, die Spec wird nicht angefasst). Schärft [ADR-0023](0023-gate-klassen-und-stempel.md) in zwei Punkten: Entscheidung 6 (lokaler Speicherort, dort zurückgestellt) und Entscheidung 1 (Klassenliste, `referenztest-dotnet` dort eine eigene Einheit). [ADR-0023](0023-gate-klassen-und-stempel.md) bleibt `Accepted` und wird nicht verändert; was sie hier nicht schärft, gilt unverändert.

**Regeln:** Baseline-Regelwerk `modul-04-adrs.md`
§Ziel-Form: ADR (MADR).

---

## Kontext

**Nutzerargument vom 2026-10-31** gegen das Zurückstellen der lokalen Stempel: „das beruht nur auf aktuellen Gates, berücksichtigt aber nicht zukünftige“. Dazu die Korrektur: „wir wollten doch zwei Stempel machen: Tool und Tests trennen“.

**Was [ADR-0023](0023-gate-klassen-und-stempel.md) zurückstellte:** Entscheidung 6 und 8 stellen die lokalen Stempel zurück, Trigger „das Werkzeug-Gate läuft lokal spürbar länger als die heutigen unter 10 s“. Die Messung dort gilt für die heutigen Werkzeug-Gates bei warmem Docker-Layer-Cache. Sie sagt nichts über Gates, die es noch nicht gibt. Diese ADR ändert nicht die Messung, sondern die Grundlage der Entscheidung.

**Belege für die Begründung:**

1. **Der Layer-Cache trägt nur reine Stages.** Er übergeht ein Gate, wenn dessen Stage nur schmale `COPY`-Zeilen liest und selbst nichts von außen nimmt (Docker-Build, Digest-Pins, Lock). Das ist der Befund der Messung in [ADR-0023](0023-gate-klassen-und-stempel.md) (Fälle 1, 2, 6). Die geplanten Lauf-Arten sind nach den Plänen im Verzeichnis `docs/plan/planning/open/` anders gebaut (gelesen, nicht gemessen):
   - der Kompatibilitätsslice: Ende-zu-Ende-Szenarien mit echtem .NET-Client gegen den Server, „ein Gate“ (Ziel des Plans); [ADR-0019](0019-kompatibilitaetstests-zuschnitt.md) (`Proposed`) entscheidet das Container-Modell;
   - der Performance-Slice: reproduzierbarer Lasttest mit Referenz-Workload;
   - der Synchronitäts-Slice: Folgeanfragen nach Fehler und Abbruch auf derselben Verbindung;
   - der Vergleichslauf-D-Slice: Referenzabgleich gegen einen SQL-Server-Container, ausdrücklich zunächst außerhalb von `make gates` und erst nach seiner Messung ein Kandidat dafür.

   Sie brauchen Container, Netze und Uhren (Timeouts, Abbruch, Last). Ein Layer-Cache übergeht solche Läufe nicht zuverlässig oder gar nicht; ein Stempel über die Eingänge ist das Mittel, sie bei gleichem Eingang nicht zu wiederholen. Ob ein einzelner dieser Läufe überspringbar ist, hängt an Entscheidung 6.
2. **Es gibt schon zwei verschiedene Stempel-Mechanismen** (gelesen): `tools/mk/detekt-selbsttest.mk` mit mtime-Stempel und Eingangsliste, `tools/mk/referenztest-dotnet.mk` mit Inhalts-Hash (`tools/eingangs-hash.sh`), beide in `.harness/state/`. Ohne gemeinsamen Mechanismus entsteht für das nächste Gate ein dritter.
3. **`.harness/state/` ist je Arbeitsbaum.** Jeder Worktree eines Agenten startet ohne Stempel. **Prüfung im Scratchpad** (Git 2.43.0, kein Repo-Inhalt berührt): ein zweiter Worktree desselben Repos sieht eine Note unter `refs/notes/gates`, die im ersten gesetzt wurde, und umgekehrt; `git rev-parse --git-common-dir` aus dem Worktree zeigt auf das `.git` des Hauptbaums, `refs/notes/gates` liegt dort. Ein frischer `git clone` sieht die Note nicht; nach `git fetch origin refs/notes/gates:refs/notes/gates` sieht er sie. Nach `git commit --amend` bleibt die Note am alten Commit; mit `git config notes.rewriteRef refs/notes/gates` wurde sie im Versuch auf den neuen Commit übernommen. Zwei Zeilen am selben Commit gelingen mit `git notes append` (durch eine Leerzeile getrennt). Nicht geprüft: Rebase und Squash mit `notes.rewriteRef`, gleichzeitiges Schreiben aus zwei Arbeitsbäumen.

**Regel:** [AGENTS.md](../../../AGENTS.md) §3.6 — ein Gate, das bei gleichem Eingang nicht läuft, ist keine Senkung der Schwelle, solange der Beleg für genau diesen Eingang vorliegt; wo er nicht vorliegt, läuft es. Das gilt unverändert aus [ADR-0023](0023-gate-klassen-und-stempel.md).

**Annahme, die kippt:** Dass das Ergebnis eines Gates eine Funktion seiner Eingänge ist. Ein Gate mit verstecktem Eingang (Netz, Uhrzeit, Last, Host-Werkzeug) wird nicht übersprungen (Entscheidung 6).

## Entscheidung

Wir wählen **Option B** (git notes, lokal) als **allgemeinen** Mechanismus, mit **genau zwei Stempeln**. Übernommen aus [ADR-0023](0023-gate-klassen-und-stempel.md), unverändert: Format des Stempels, Eingangs-Hash aus Git-Tree-IDs, Überspringen nur bei Beleg (Entscheidung 2 bis 5 dort), Bindung an `record-gates` und Stop-Hook.

1. **Genau zwei Stempel:** `werkzeug` und `tests`. Die **Zahl der Stempel bleibt zwei**, der **Mechanismus ist einer** (ein Skript). Ein neues Gate bekommt keinen eigenen Stempel, sondern gehört zu einem der beiden.
   - **`werkzeug`:** `docs-check`, `baseline-verify`, `detekt-selbsttest`, `skript-selbsttest` und die ausgelagerten Doku-Kopplungstests ([ADR-0023](0023-gate-klassen-und-stempel.md) Folgepflicht 1). Eingänge: wie dort (`docs/`, `spec/`, `harness/`, `tools/`, `.d-check.yml`, `config/detekt/selbsttest*`, `Makefile`, `repo.mk`, `.harness/baseline/`), dazu die Eingänge von `detekt-selbsttest` (`Dockerfile`, Gradle-Dateien, `config/detekt/detekt.yml`; Überschneidung ist erlaubt, [ADR-0023](0023-gate-klassen-und-stempel.md) Entscheidung 1).
   - **`tests`:** `test`, `detekt`, `build` (mit der Manifest-Prüfung), `referenztest-dotnet` (Entscheidung 2) und künftige Gates, die Verhalten prüfen (Entscheidung 3). Eingänge: wie dort (`src/`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/`, beide Lockfiles, `Dockerfile`, `config/detekt/`, `LICENSE`), dazu `referenztest-dotnet/` und sein Fragment.
   - Die Eingangsliste eines Stempels ist die **Vereinigung der Eingänge seiner Gates** ([ADR-0023](0023-gate-klassen-und-stempel.md) Entscheidung 1). Sie steht als eigene, geprüfte Datei im Repo (Entscheidung 10); die Listen oben sind ihr Anfangsinhalt. Die Liste und das Stempel-Skript sind selbst Eingänge **beider** Stempel.
   - **Ein Stempel steht für den ganzen Stempel-Umfang.** Er wird nur geschrieben, wenn **alle** Gates der Klasse im selben Lauf grün waren; `make test` allein schreibt nichts. Läuft eine Klasse, laufen alle ihre Gates.
2. **`referenztest-dotnet` gehört zu `tests`** (Schärfung von [ADR-0023](0023-gate-klassen-und-stempel.md) Entscheidung 1, dort „eigene Einheit“; der Rest von Entscheidung 1 gilt). Grund: es ist ein Testlauf gegen den Client-Vertrag ([ADR-0008](0008-dotnet-kompatibilitaetstests.md)), kein Prüfwerkzeug der Doku. Preis: eine Änderung an `src/` fährt es mit. Im Docker-Layer-Cache kostet das nach der Messung der vorigen Fassung dieses Zuschnitts (Einzelmessung der Stage-Zeit im Cache, ~2 s; hier nicht neu gemessen) wenig; ohne Cache ist es eine Stage mehr. **Alternative `werkzeug`:** dann liefe es bei Doku-Änderungen mit, die es nicht berühren, und nicht bei Gradle-Änderungen, die es auch nicht berühren; es wäre dem Werkzeug-Gate näher als dem Verhaltenstest, weil es keinen Server prüft (Smoke ohne Server). Empfohlen wird `tests`, weil die Zuordnung dem Zweck folgt und der Preis klein ist; die Alternative ist zulässig, wenn die Messung den Preis als spürbar zeigt (Trigger).
3. **Zuordnungsregel für neue Gates:** ein Gate gehört zu `tests`, wenn es Quellcode oder Verhalten des Produkts prüft; sonst zu `werkzeug`; **braucht es beides, gehört es zu `tests`**. Die Zuordnung steht im Make-Fragment des Gates und in der Tabelle `harness/README.md` §Sensors (Bindung: diese ADR). Ein Gate wechselt die Klasse nur per Änderung dieser Zuordnung, nicht stillschweigend.
4. **Speicherort (Option B): git note unter `refs/notes/gates`** am grünen Commit, eine Zeile je Stempel in der Form `stempel <klasse> <eingangs-hash> gruen`, mehrere Zeilen am selben Commit per `git notes append`. Gelesen wird die Zeile mit exakt passender Klasse und exaktem Hash; andere Zeilen, unlesbarer Inhalt oder mehrere gleiche Zeilen mit demselben Hash ändern nichts (gleicher Hash, gleiche Aussage). Schreibt ein Aufruf nicht (Sperre, Fehler), ist der Stempel nicht gesetzt: der nächste Lauf läuft, kein Fehler des Gates.
   - Die Note liegt im gemeinsamen Git-Verzeichnis und gilt für alle Worktrees und Agenten desselben Klons (Belegt in Kontext 3). Ein Agent im frischen Worktree übernimmt damit den Stempel des Hauptbaums.
   - **Nicht übertragen** wird die Note: kein automatisches `push` oder `fetch`. Ein anderer Klon oder die CI sieht sie nicht und läuft voll. Ob Notes geteilt werden, ist nicht entschieden (offene Frage 1); die CI-Seite bleibt [ADR-0023](0023-gate-klassen-und-stempel.md) Entscheidung 7.
   - `notes.rewriteRef` für `refs/notes/gates` ist **optional** (Kontext 3: Amend getestet, Rebase und Squash nicht): die Note trägt den Eingangs-Hash, eine übernommene Zeile ist darum nur dann wirksam, wenn der Hash des neuen Commits derselbe ist. Ohne die Einstellung läuft die Klasse nach Amend und Rebase.
5. **Schnittstelle für Gates: ein Skript im `tools/`-Baum des Repos** (nicht unter `tools/harness/`, das dem Harness gehört): `bash tools/stempel.sh pruefe <klasse>` und `bash tools/stempel.sh schreibe <klasse> [hash]`. Das Skript liest die Eingangsliste der Klasse aus `tools/stempel/<klasse>.eingaenge` (Entscheidung 10); die Make-Fragmente geben keine Pfade mit.
   - `pruefe` endet mit Exit 0 **nur**, wenn die Übersprung-Bedingung aus [ADR-0023](0023-gate-klassen-und-stempel.md) Entscheidung 4 erfüllt ist; **jeder andere Fall** (kein Treffer, flacher Klon, Abfragefehler, nicht sauber, Beleg unlesbar, kein Vorfahr) endet mit Exit 1 und einer Grund-Zeile auf stderr. Es gibt keinen dritten Ausgang. `FORCE=1` im Aufruf des Targets überspringt `pruefe` und läuft.
   - `pruefe` gibt den berechneten Eingangs-Hash auf stdout aus; `schreibe` schreibt nach dem grünen Lauf die Zeile und bricht ab, wenn der Hash jetzt ein anderer ist als der übergebene (Änderung während des Laufs) oder die Eingangspfade nicht sauber sind. Das ist der Ablauf „Hash vor und nach dem Lauf derselbe“ aus [ADR-0023](0023-gate-klassen-und-stempel.md) Entscheidung 3.
   - Sauberkeit gilt **nur in den Eingangspfaden der Klasse** (versioniert, unversioniert, ignoriert; [ADR-0023](0023-gate-klassen-und-stempel.md) Entscheidung 3). Eine Änderung außerhalb macht die Klasse nicht schmutzig.
   - Gate-Verdrahtung: jede Klasse bekommt einen Gruppenschritt im Make-Fragment (`pruefe`, sonst Lauf der Gates der Klasse, dann `schreibe`); die genaue Form der Make-Verdrahtung ist Sache der Umsetzung.
6. **Aufnahmebedingung für künftige Gates (Setzung):** ein Gate trägt im Stempel nur, wenn alle seine Eingänge in der Eingangsliste stehen (auch Image-Digests und Versionsstände, die der Lauf zieht). Ein Gate mit **verstecktem Eingang** (Netz zur Laufzeit, Wanduhr, Last als Ergebnis-Einfluss) steht **nicht** im Stempel-Umfang einer Klasse und läuft immer. Insbesondere gilt für Lasttests mit Messwerten gegen Grenzwerte: sie hängen an Rechner und Last; ob und wie ein Lasttest-Gate übersprungen werden darf, ist **nicht** durch diese ADR entschieden (offene Frage 2). Container-Läufe mit per Digest gepinntem Image und festem Szenario-Satz sind aufnahmefähig, sofern ihr Ergebnis nicht von der Uhr abhängt; ein Zeit-Szenario (Timeout, Abbruch) ist darauf in der Umsetzung zu prüfen, vor der Aufnahme.
7. **Migration der zwei Altmechanismen.** `.harness/state/detekt-selbsttest.ok` (mtime) und `.harness/state/referenztest-dotnet.hash` (Inhalts-Hash) entfallen; beide Gates gehen in die zwei Stempel auf (`detekt-selbsttest` nach `werkzeug`, `referenztest-dotnet` nach `tests`). Die Skip-Logik in den beiden Fragmenten entfällt zugunsten des Gruppenschritts. `tools/eingangs-hash.sh` und sein Selbsttest bleiben, solange ein Hash über Dateien nötig ist, sonst entfallen sie mit Begründung ([ADR-0023](0023-gate-klassen-und-stempel.md) Folgepflicht 4). Zu prüfen in der Umsetzung: `bin/` und `obj/` unter `referenztest-dotnet/` zählten im alten Hash nicht mit; die Sauberkeitsprüfung zählt auch Ignoriertes, und es darf nicht dazu führen, dass die Klasse dauerhaft „nicht sauber“ und damit immer Lauf ist (auf dem Host baut nichts, siehe Fragment; der Selbsttest belegt es).
8. **Verhältnis zu `record-gates` und Stop-Hook: unverändert bindend.** Sie behalten Gesamtbaum-Hash und HEAD als Nachweis (`.harness/state/gates-passed.*`). `make gates` ist grün, wenn jede Klasse entweder grün lief oder mit Beleg übersprungen wurde; erst dann wird der Nachweis geschrieben. Die Stempel sind ein Beleg über Klassen, der Nachweis einer über den Gesamtbaum; keiner ersetzt den anderen.
9. **Reihenfolge der Umsetzung** (nach `Accepted`): **erstens** die Entkopplung der Test-Stage ([ADR-0023](0023-gate-klassen-und-stempel.md) Folgepflicht 1, läuft bereits); **zweitens** der lokale Stempel-Mechanismus dieser ADR (Skript, Selbsttest, Migration); **drittens** die CI-Seite ([ADR-0023](0023-gate-klassen-und-stempel.md) Entscheidung 7). Der Zeitpunkt des zweiten Schritts hängt nicht mehr am Trigger aus [ADR-0023](0023-gate-klassen-und-stempel.md) Entscheidung 8; der Satz „lokale Stempel zurückgestellt“ ist damit abgelöst, der Rest dieser Entscheidung gilt weiter.

10. **Die Eingangsliste ist ein eigenes, geprüftes Artefakt** (Nutzer: „die Eingangsliste für den Test brauchen wir auch“).
    - **Ort:** je Stempel eine Datei im `tools/`-Baum: `tools/stempel/werkzeug.eingaenge` und `tools/stempel/tests.eingaenge`. Ein Pfad je Zeile, Verzeichnisse erlaubt (gelten rekursiv), Zeilen mit `#` sind Kommentare. Das Stempel-Skript liest sie. Anfangsinhalt sind die Listen aus Entscheidung 1 ([ADR-0023](0023-gate-klassen-und-stempel.md) Entscheidung 1: Werkzeug `docs/`, `spec/`, `harness/`, `tools/`, `.d-check.yml`, `Makefile`, `repo.mk`, `.harness/baseline/`, `config/detekt/selbsttest*`; Tests `src/`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/`, `gradle.lockfile`, `settings-gradle.lockfile`, `Dockerfile`, `config/detekt/`, `LICENSE`, dazu mit Entscheidung 2 `referenztest-dotnet/`).
    - **Die Änderung einer Liste ist die Änderung eines Eingangs:** beide Listen und `tools/stempel.sh` stehen in den Listen **beider** Stempel (liegen sie unter `tools/`, deckt `werkzeug` sie schon ab; in `tests` stehen sie ausdrücklich).
    - **Das Risiko, benannt:** eine Datei, die ein Gate liest und die **nicht** in seiner Liste steht, lässt das Gate fälschlich übersprungen werden, obwohl sich sein Eingang geändert hat (**falsch grün**, die gefährliche Richtung). Eine überflüssige Datei kostet nur einen Lauf. Darum ist die Liste großzügig zu halten und geprüft.
    - **Prüfung (Pflicht-Sensor, im Werkzeug-Gate):** ein Selbsttest vergleicht die Liste von `tests` mit den `COPY`- und `ADD`-Quellen der Dockerfile-Stages, die zum Test-Gate gehören (`test`, `compile`, `detekt`, `build-jar`, gegebenenfalls `smoke`, und die Stage-Kette dahinter über `FROM <stage> AS`), und für `referenztest-dotnet` mit dem `COPY` seines eigenen Dockerfiles. Die Liste von `werkzeug` wird verglichen mit den Eingängen der Werkzeug-Gates: den Pfaden, die deren Stages kopieren (u. a. `tools/*.sh`), und den Scan-Wurzeln in `.d-check.yml`. **Regel:** jede Quelle eines `COPY` einer Stage der Klasse (samt Vorgänger-Stages) muss von einem Listeneintrag **überdeckt** sein (gleicher Pfad oder ein Eintrag, der ein Elternverzeichnis nennt); sonst ist der Selbsttest rot. **Mutation:** der Selbsttest fügt in eine Kopie des Dockerfiles eine `COPY`-Zeile ohne Listeneintrag ein und muss rot werden; ein Selbsttest, der das nicht zeigt, gilt nicht als gelaufen.
    - **Methode:** das Dockerfile wird zeilenweise gelesen; `FROM <image> AS <name>` beginnt eine Stage, `COPY` und `ADD` mit Quellen (ohne `--from=`) sind ihre Eingänge, `FROM <vorige-stage> AS` vererbt deren Eingänge. `COPY --from=<stage>` ist kein Kontext-Eingang, sondern ein Artefakt einer anderen Stage und wird über deren Eingänge erfasst. Platzhalter (`*`) sind als Muster zu überdecken.
    - **Grenze (Einzelfall, benannt):** ein `COPY` erfasst nicht, was ein Build-Schritt zur Laufzeit holt (Netz, Basis-Image, ein Paket-Manager) — das sind Digest-Pins und Lockfiles, die selbst in der Liste stehen müssen —; nicht Make-Fragmente und Skripte, die ein Gate außerhalb von Docker aufruft (`tools/mk/`, `Makefile`: in `werkzeug` aufgeführt, aber nicht gegen eine Stage prüfbar); nicht die Dateien des Build-Kontexts, die ein `COPY .` ganz holt (dann ist die ganze Wurzel Eingang, der Selbsttest verlangt dafür den Eintrag `.`, und die Klasse wäre bei jeder Änderung neu zu fahren: das ist als Befund zu behandeln, nicht zu erlauben). Der Selbsttest belegt „kein `COPY` ohne Eintrag“, nicht „kein versteckter Eingang“; Letzteres bleibt Sache der Aufnahmebedingung (6).

## Verglichene Alternativen

**Speicherort des lokalen Belegs:**

| Option | Pro | Contra |
|---|---|---|
| (a) `.harness/state/` wie bisher, je Worktree | Kein Aufwand; kein Schreibzugriff auf Git-Refs | Jeder Agent-Worktree startet ohne Stempel (Kontext 3), jeder Klon ebenso; an keinen Commit gebunden; ein Mechanismus je Gate bleibt wahrscheinlich |
| **(b) git notes `refs/notes/gates` lokal** — empfohlen | Gilt für alle Worktrees des Klons (im Scratchpad belegt); an den Commit gebunden, ändert ihn nicht; pushbar, wenn je gewollt; ein Skript für alle | Nicht automatisch gezogen oder gepusht; Rebase und Squash lassen die Note am alten Commit zurück (dann Lauf); gleichzeitiges Schreiben aus zwei Arbeitsbäumen nicht geprüft; wer schreiben kann, kann einen Beleg setzen |
| (c) gemeinsames Verzeichnis außerhalb des Baums (z. B. unter dem Home des Nutzers oder im gemeinsamen Git-Verzeichnis als Datei) | Gilt für alle Worktrees; keine Git-Objekte | Neuer Ort mit eigenem Format, eigener Bereinigung und eigenen Rechten; an keinen Commit gebunden außer durch Inhalt; das gemeinsame Git-Verzeichnis ist über `git rev-parse --git-common-dir` erreichbar, aber ein Ordner dort ist Git-fremder Inhalt |
| (d) eingecheckte Datei | Reist mit dem Commit | Ändert den Eingang, den sie belegt; Merge-Konflikte bei parallelen Zweigen; der Autor setzt die Aussage selbst — wie in [ADR-0023](0023-gate-klassen-und-stempel.md) **nicht empfohlen** |

**Zahl der Stempel:**

| Option | Pro | Contra |
|---|---|---|
| Ein Stempel je Gate | Feinste Auslassung | Eingangslisten und Noten je Gate; wächst mit jedem Gate, genau das Problem aus Kontext 2 |
| Ein Stempel für alles | Einfachster Mechanismus | Doku-Änderung löst Tests aus und umgekehrt ([ADR-0023](0023-gate-klassen-und-stempel.md) Option C) |
| **Zwei Stempel `werkzeug` und `tests`** — gewählt | Folgt dem Nutzerziel; wächst nicht mit der Gate-Zahl; Zuordnungsregel (Entscheidung 3) | Ein Gate in `tests` läuft mit, wenn ein anderes Gate derselben Klasse neu läuft (grobe Granularität) |

## Konsequenzen

- Positiv: neue Gates ordnen sich einer Klasse zu statt einen Mechanismus zu bauen; frische Agent-Worktrees übernehmen den Beleg des Klons; Checkout, Merge oder Amend ohne Inhaltsänderung lösen (bei vorhandenem Beleg) nichts aus; zwei Altmechanismen entfallen.
- Negativ: siehe „Akzeptierte Negative“.
- Folgepflicht (nach `Accepted`, durch einen Implementer):
  0. Die zwei Eingangslisten (`tools/stempel/*.eingaenge`) und der Selbsttest gegen die `COPY`-Quellen mit Mutation (Entscheidung 10), im Werkzeug-Gate. Das nimmt auf, was bisher von Hand je Stage gepflegt wird (Eingangsliste je Stage ohne Prüfung, ein beobachteter, noch nicht verkörperter Befund); ohne Selbsttest wird kein Stempel geschrieben.
  1. Skript `tools/stempel.sh` mit Selbsttest, der jeden Fehlerpfad aus Entscheidung 5 einzeln belegt (flacher Klon, schmutziger Eingangsbaum, geänderte Datei, unlesbare Note, Hash ändert sich während des Laufs, gleichzeitiges Schreiben).
  2. Gruppenschritte und Zuordnung in den Fragmenten; Eingangslisten aus den `COPY`-Quellen; `harness/README.md` nennt die zwei Klassen und die Bindung auf diese ADR und [ADR-0023](0023-gate-klassen-und-stempel.md).
  3. Migration der zwei Altmechanismen (Entscheidung 7).
  4. Nachmessung der Laufzeit nach Migration (warm und kalt); ohne Messung trägt keine Laufzeitaussage.

## Akzeptierte Negative

1. **Rebase, Squash und Amend** (ohne `notes.rewriteRef`) lassen die Note am alten Commit: die Klasse läuft. Kosten: ein Lauf; irrt zur sicheren Seite.
2. **Ein schmutziger Eingangsbaum** läuft voll und schreibt nichts.
3. **Ein falscher Beleg** (von Hand gesetzte Note, unvollständige Eingangsliste jenseits dessen, was der Selbsttest über die `COPY`-Quellen sieht, versteckter Eingang) ergibt ein grünes Gate ohne Lauf. Begrenzt durch: Schreiben nur durch `schreibe` nach grünem Lauf aller Gates der Klasse, Eingangsliste aus den `COPY`-Quellen mit Selbsttest, Vollauf in der CI. Nicht ausgeschlossen ist der Beleg durch jemanden mit Schreibrecht auf das Git-Verzeichnis; die Vertrauensgrenze ist dieselbe wie die von `.harness/state/` heute.
4. **Grobe Granularität:** zwei Stempel heißen, dass eine Änderung an `src/` alle Gates von `tests` fährt, auch `referenztest-dotnet`, und eine Änderung an `Dockerfile` oder den Gradle-Dateien beide Klassen. Akzeptiert: es irrt zur sicheren Seite, und je Gate wäre das Wachstum aus Kontext 2.
5. **Notes sind nur lokal:** ein frischer Klon und die CI laufen voll, bis (offene Frage 1) oder [ADR-0023](0023-gate-klassen-und-stempel.md) Entscheidung 7 etwas anderes festlegen.
6. **Lasttest-Gates sind durch diese ADR nicht übersprungbar gemacht** (Entscheidung 6); das Argument 1 des Kontexts trägt für sie nicht ohne eigene Entscheidung. Einmaliges, benanntes Negativ, kein Folge-Dokument.
7. **Gleichzeitiges Schreiben der Note aus zwei Arbeitsbäumen ist nicht geprüft.** Folge im ungünstigen Fall: eine Sperre, der Stempel wird nicht gesetzt, der nächste Lauf läuft (Entscheidung 4).

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| Selbsttest von `tools/stempel.sh` (nach Umsetzung) | jeder Fehlerpfad endet im Lauf (Exit 1), nie im Überspringen; eine Datei in den Eingängen ändert den Eingangs-Hash | heute kein Sensor; nicht behauptet |
| Selbsttest der Eingangslisten (Pflicht, im Werkzeug-Gate; nach Umsetzung) | jede `COPY`-Quelle einer Stage der Klasse ist von einem Listeneintrag überdeckt; die Mutation (eine `COPY`-Zeile ohne Eintrag) wird rot | heute kein Sensor; nicht behauptet |
| Prüfung der Zuordnung (nach Umsetzung) | jedes Target in `GATE_CHECKS` gehört zu genau einer der zwei Klassen | heute kein Sensor; nicht behauptet |

## Re-Evaluierungs-Trigger

- Ein Gate lief auf Grundlage eines Belegs nicht und hätte rot sein müssen: Entscheidung neu bewerten; die Aufnahmebedingung (6) ist der erste Kandidat zur Verschärfung.
- Die Klasse `tests` wird durch nicht cachefähige Gates lokal spürbar langsam, obwohl nur ein Teil ihrer Eingänge sich ändert: eine Aufteilung in weitere Stempel ist eine neue Entscheidung, nicht ein Anhang an diese.
- Die Nachmessung zeigt, dass `referenztest-dotnet` in `tests` spürbar mitläuft: Zuordnung zu `werkzeug` erwägen (Entscheidung 2).
- Notes erweisen sich als störend (Sperren bei parallelen Agenten, Verlust durch `git gc` oder Klone): Speicherort (c) bewerten.
- Der Selbsttest der Eingangslisten lässt einen versteckten Eingang durch, der ein Gate falsch grün machte: Grenze des Selbsttests (Entscheidung 10) neu bewerten, Aufnahmebedingung (6) verschärfen.
- Die CI bekommt Notes oder einen anderen Beleg-Weg: Verhältnis zu [ADR-0023](0023-gate-klassen-und-stempel.md) Entscheidung 7 klären.

## Offene Fragen an den Nutzer

1. Sollen Notes (`refs/notes/gates`) je gepusht und geholt werden, damit andere Klone und die CI sie sehen? (Vertrauensgrenze: wer pushen darf, setzt Belege. Standard dieser ADR: nein.)
2. Dürfen Lasttest-Gates mit Messwerten gegen Grenzwerte je mit Beleg übersprungen werden, oder laufen sie immer? (Standard dieser ADR: immer.)
3. Soll `notes.rewriteRef` für `refs/notes/gates` per Setup eingerichtet werden (Amend wurde getestet, Rebase und Squash nicht)?

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-10-31 | Nutzerargument: Zurückstellen beruhe nur auf heutigen Gates; Korrektur: zwei Stempel, Tool und Tests | Sitzung |
| 2026-10-31 | Proposed | — |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
