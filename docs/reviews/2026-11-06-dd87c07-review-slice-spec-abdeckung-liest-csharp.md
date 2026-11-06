# Review-Report: slice-spec-abdeckung-liest-csharp — 2026-11-06

**Review-Art:** Code — gegen Plan, Entscheidungen und Hard Rules (nicht gegen die DoD; das ist die Frage des Verifiers).

**Gegenstand:** slice-spec-abdeckung-liest-csharp, Commit `dd87c07` auf Basis `cbe4fb06` (`git diff cbe4fb06..HEAD`, 10 Dateien).

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-06

**Eingangs-Kontext:**

- `slice-spec-abdeckung-liest-csharp` (Plan, DoD, §6 Risiken, §7 Stand des Implementers)
- `SPEC-045`, `TDS-QA-05`, `TDS-QA-06`; [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0024](../plan/adr/0024-lokale-stempel-zwei-klassen.md), [ADR-0030](../plan/adr/0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md), [ADR-0035](../plan/adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md)
- `AGENTS.md` §3.1 bis §3.8
- vorherige Findings am Modul: keine eigenen Reports zu `tools/spec-abdeckung.sh` im Eingang gelesen (Stichprobe über `docs/reviews/` nicht erschöpfend)

## Eigene Läufe

- `make spec-abdeckung` (zweimal): Exit 0, Matrix unverändert gegenüber dem Commit; `Stand: 43 von 56 … 9 ohne Nachweis`; `SPEC-045`: 20 | 0 | 0 | 2 nicht bestanden, Beleg `MitschnittEinheitTests`; die zwei Zeilen „nicht gelaufen“ sind die Tests in `MitschnittAufnahmeTests` und `MitschnittAbspielenTests`. Nachgerechnet: `MitschnittEinheitTests.cs` hat 20 `// @spec`-Zeilen und 20 `[Fact]`, `MitschnittLaufTests.cs` 2 weitere Tags: 20 + 2 stimmt.
- Cache-Falle G1: Bau-Protokoll zeigt `test-bericht 1/3 … 3/3` ausgeführt (nicht `CACHED`), `RUN dotnet test` meldet `Passed: 28`, `test-bericht-export` ebenfalls frisch; die Gradle-Schritte und `COPY referenztest-dotnet` stehen auf `CACHED`.
- `make spec-abdeckung-selbsttest` mit `--no-cache-filter` neu gefahren: Zaehlung, Schalter, Umbenennung, zwei Verzeichnisse, C#-Faelle, C#-Bericht fehlt grün; vier Mutationen „rot gesehen“.
- Eigene Mutationen (Kopie von Erzeuger und Selbsttest im Scratchpad): (a) `NotExecuted` zählt als `pass` — Selbsttest endet mit „C#-Faelle rot“; (b) Rang-Vergleich invertiert (bestes statt schlechtestes Ergebnis) — Selbsttest rot.
- Eigene Probe der Leseregel (Mini-Bestand im Scratchpad): siehe F-1, F-2.
- Fehlerpfad des Berichts: ein Übersetzungsfehler in `Smoke/*.cs` lässt `docker build --target test-bericht-export` mit Exit ungleich 0 abbrechen (`"/bericht": not found`); es entsteht keine Datei, `make` bricht.
- `make stempel-eingaenge-pruefung` ok, `make gate-zuordnung` ok (16 Gates, Selbsttest 33 Fälle), `make docs-check` 0 Befunde. README-Zellen: `make spec-abdeckung` 196, `make spec-abdeckung-selbsttest` 199 Zeichen (Grenze 200).
- Nicht wiederholt: `make gates` gesamt, `make referenztest-dotnet`.

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Kopf, Matrix-Vorspann, Slice und README sagen „Bericht der Stage `test`“, gelesen wird aber der Bericht der Stage `test-bericht`, einer zweiten Stage mit kopierten `COPY`-Zeilen und kopiertem `dotnet test`-Aufruf. Ändert jemand `test` (Filter, weiteres `COPY`, Flag), bleibt `test-bericht` stehen; die Matrix zählt dann Tests als bestanden, die das Gate `referenztest-dotnet` nicht oder anders ausführt (oder umgekehrt als „nicht gelaufen“); kein Sensor hält die beiden Stages gleich. | AGENTS.md §3.8 (Aussage trägt höchstens so weit wie ihr Beleg) | `tools/spec-abdeckung.sh` · „Der Bericht der C#-Tests ist TRX (eingebauter Logger von `dotnet test`, kein Paket)“ und „nur die Stage `test` des .NET-Geruests kennt“; `docs/plan/spec-abdeckung.md` · „TRX des .NET-Laufs der Stage `test`“; `referenztest-dotnet/Dockerfile` · „dieselben Quellen und derselbe Lauf wie `test`“ | ja — Mutation: `--filter` nur in `test` ergänzen, Matrix bleibt unverändert | Zweite Stage mit kopiertem Rezept ohne Gleichlauf-Sensor; Aussage „Bericht der Stage X“ trifft eine Schwesterstage |
| F-2 | LOW | Der Kopf sagt, jede andere Zeile (Feld, Klasse, …) verwirfe den Tag. Eine Feldzeile mit Konstruktoraufruf (`private readonly Foo _x = new Foo();`) zwischen Tag und Methode wird als Methode `Foo` gelesen: der Tag haftet am Feld, die Matrix führt einen falschen Eintrag „nicht gelaufen“, die eigentliche Testmethode verliert den Tag still. Kein falscher Nachweis, aber Kopf und Verhalten weichen ab. | Maintainability / AGENTS.md §3.8 | `tools/spec-abdeckung.sh` · „jede andere Zeile (Feld, Klasse, Namensraum, ein Attribut ueber mehrere Zeilen) verwirft ihn“ | ja — Mini-Bestand mit Tag über `private readonly Foo _x = new Foo();` | Leseregel in Kommentar breiter als das Verhalten |
| F-3 | LOW | Risiko 2 (Tag über der falschen Methode): der Kopf des Erzeugers nennt die Grenze „vertauschter Name“ nicht; sie steht nur in Slice §6/§7. Ein Tag über einer anderen, ebenfalls bestehenden Testmethode wird als bestanden gezählt (belegt: Tag über Hilfsmethode ergibt „nicht gelaufen“, über eine vorhandene Testmethode kein Befund möglich). Eine Gegenmaßnahme gibt es nicht und der Slice behauptet keine; Ausgang bleibt offen. | AGENTS.md §3.8; Slice §6 | `tools/spec-abdeckung.sh` · „Grenzen: der Tag sagt, welche Festlegung der Autor meint“ | nein | Grenze nur im Zeitdokument, nicht beim Erzeuger |
| F-4 | LOW | Das Kommentar in `referenztest-dotnet/Dockerfile` sagt, bei Bau-Fehler „endet der Export ohne Datei und der Erzeuger bricht ab“. Gemessen bricht schon der Build von `test-bericht-export` (`"/bericht": not found`), der Erzeuger läuft nicht an. Ergebnis gleich, Beschreibung ungenau. | AGENTS.md §3.7 (Kommentar beschreibt, was da ist) | `referenztest-dotnet/Dockerfile` · „endet der Export ohne Datei und der Erzeuger bricht ab“ | ja — Übersetzungsfehler einbauen | Kommentar über Fehlerpfad ungenau |
| F-5 | LOW | Slice §1 nennt „21 Einheitstests“ und „23 Stellen“; im Bestand stehen 20 Einheitstests und 22 Tags (20 + 2). Die Matrix und §7 stimmen, der Plantext nicht. | AGENTS.md §3.8 | `slice-spec-abdeckung-liest-csharp` · „Die 21 Einheitstests des Mitschnitt-Werkzeugs“ | ja — `grep -c "// @spec"` | Zählangabe im Plan ohne Messung |
| F-6 | INFO | Zweite Fundstelle (nicht geändert, gehört `slice-qa-vergleichslauf-d-messungen`): Kopfkommentar „`make spec-abdeckung` liest nur Kotlin-Tests: diese …“ ist jetzt falsch. Weitere lebende Fundstellen im Repo außer Zeitdokumenten: keine; die Beobachtung `dod-wortlaut-ueber-der-bauart` (Beschreibung des damaligen Stands) und die Slices unter `done/` sind Zeitdokumente. | Maintainability | `referenztest-dotnet/Mitschnitt/MitschnittEinheitTests.cs` · „`make spec-abdeckung` liest nur Kotlin-Tests: diese“ | ja — Grep | Fortschreibung lässt zweite Fundstelle stehen (benannt, an anderen Slice übergeben) |
| F-7 | INFO | Vorher-Wert „SPEC-045 mit 0 Nachweisen, 10 Waisen, `Stand: 42 von 56`“ stammt aus der eingecheckten Matrix auf `cbe4fb06` (gegengelesen: stimmt mit `git show cbe4fb06:docs/plan/spec-abdeckung.md`), nicht aus einem eigenen Lauf; die Closure-Notiz sagt das auch („eingecheckte Matrix“). Aussage ≤ Beleg gewahrt. Die übrigen Zeilenänderungen der Matrix (`FlaecheEmbeddingTest`, `TelemetryFelderEmbeddingTest`) sind im Diff nachvollziehbar. | AGENTS.md §3.8 | `slice-spec-abdeckung-liest-csharp` · „Vorher (eingecheckte Matrix auf `cbe4fb06`“ | ja | — |
| F-8 | INFO | Stempel: `referenztest-dotnet/Dockerfile` und `Dockerfile` liegen in `tests.eingaenge` bzw. `stempel.eingaenge`; die Änderung entwertet die Stempel der Klassen `tests` und `stempel`, das ist die vorgesehene Wirkung. `make stempel-eingaenge-pruefung` prüft nur die Stage `test` des .NET-Dockerfiles; die `COPY`-Quellen der neuen Stage `test-bericht` (`Smoke`, `Mitschnitt`) liegen unter dem Eintrag `referenztest-dotnet`. Neue Listeneinträge nötig: nein. | [ADR-0024](../plan/adr/0024-lokale-stempel-zwei-klassen.md), [ADR-0030](../plan/adr/0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md) | `tools/stempel/tests.eingaenge` · „referenztest-dotnet“ | ja — `make stempel-eingaenge-pruefung` | — |

## Beantwortung der Prüffragen

1. Nachgerechnet und wiederholt (siehe Eigene Läufe): 20 bestanden / 2 nicht gelaufen, 9 Waisen, `43 von 56`. Mutationen: vier des Implementers rot, zwei eigene rot.
2. Cache-Falle G1: die C#-Testausführung läuft bei jedem Aufruf neu (nicht `CACHED`). Der Gradle-Teil kann einen alten Lauf zeigen und ist benannt: Kopf von `tools/mk/spec-abdeckung.mk` nennt `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`, die Nachweis-Datei im Register ist ergänzt. Der Kopf des Erzeugers selbst nennt nur den TRX-Teil.
3. Leseregel: haftet an der ersten Methodenzeile danach; mehrzeilige Attribute, geschachtelte Klassen, `record`/`struct` stehen im Kopf als nicht gelesen (mehrzeiliges Attribut verlor in der Probe den Tag still, `SPEC-004` blieb Waise ohne Zeile „nicht gelaufen“ — ehrlich benannt, vertretbar). Abweichung F-2, Lücke F-3.
4. Lieferkette: kein neues Paket (`git diff --stat` ohne `packages.lock.json`/`.csproj`), `RUN --network=none`, TRX-Logger eingebaut; [ADR-0035](../plan/adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md) nicht berührt. Die zwei lokalen Tests stehen als „nicht gelaufen“, wie entschieden.
5. Zweite Fundstellen: F-6; SPEC-045-Verweise und Matrix stimmen; `spec/spezifikation.md` nennt den Erzeuger nicht (SPEC-052 erwähnt nur den Tag), kein Nachzug nötig; Register `spec-abdeckung-liest-nur-kotlin` fortgeschrieben.
6. Stempel und Gate-Zuordnung: F-8; README-Zellen ≤ 200.
7. Vorher-Wert: F-7.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `tools/spec-abdeckung-selbsttest.sh` | geprüft, ohne Befund (eigene Mutationen werden erkannt; Fälle Happy/Negative/Boundary vorhanden) |
| `tools/mk/spec-abdeckung.mk` | geprüft, ohne Befund (`mktemp`, `trap`, zwei Builds, `--no-cache-filter` wirksam; kein Host-Gradle/-dotnet, §3.1 gewahrt) |
| `Dockerfile` (Stage `spec-abdeckung`) | geprüft, ohne Befund (Reihenfolge hält den Gradle-Layer; benannter Kontext dokumentiert) |
| `harness/README.md` | geprüft, ohne Befund (Zeilen stimmen mit Verhalten, ≤ 200 Zeichen) |
| `docs/plan/spec-abdeckung.md` | geprüft, ohne Befund (reproduziert, unverändert nach Lauf) |
| `docs/plan/planning/observations/` | geprüft, ohne Befund (Evidenz ergänzt, Stand benennt Träger) |
| Suppression-Verbot (§3.2) | geprüft, ohne Befund (keine `@Suppress`, `nolint`, `SuppressMessage` im Diff) |
| Plan-Tabelle §3 gegen Diff | geprüft, ohne Befund (alle berührten Dateien genannt; Spec-Stelle SPEC-050 geprüft, kein Nachzug) |

## Summary

Keine HIGH-Findings. Ein MEDIUM (F-1: der gelesene Bericht stammt aus einer Schwesterstage, nicht aus `test`; Aussagen und Kommentare nennen `test`), vier LOW, drei INFO. Gelieferte Zahlen und Cache-Verhalten sind nachvollzogen; ein Konflikt-Pfad ist nicht ausgelöst.
