# Verifikationsbericht: slice-spec-abdeckung-liest-csharp (HEAD 429aa2f)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: Slice `slice-spec-abdeckung-liest-csharp` (`in-progress/`, Zweig `slice-spec-abdeckung-liest-csharp`, HEAD `429aa2f`, Basis `cbe4fb06`; Commits `dd87c07` Implementierung, `e1c8ac0` Review, `429aa2f` Nachbesserung) gegen DoD §2, Plan §3, [SPEC-045](../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig), [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0024](../plan/adr/0024-lokale-stempel-zwei-klassen.md), [ADR-0035](../plan/adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md) und den Review-Report `2026-11-06-dd87c07-review-slice-spec-abdeckung-liest-csharp.md`. Nutzerentscheid 2026-11-06 („deine Empfehlungen“; im Original nicht gesehen, belegt durch den Auftrag und die Zitate in Slice und Register): Werkzeug erweitern, die zwei lokalen Tests nicht in die Abdeckung, kein neues NuGet-Paket.
* Selbst gefahren (Arbeitsbaum sauber, Basis des Laufs: HEAD `429aa2f`), Protokolle im Scratchpad:
  * `make spec-abdeckung`: Exit 0, Matrix **unverändert** (`docs/plan/spec-abdeckung.md unveraendert`, `git status` sauber); `Stand: 43 von 56`, 9 ohne Nachweis; `SPEC-045`: 20 bestanden / 0 / 0 / 2 nicht gelaufen, Beleg `MitschnittEinheitTests`; die zwei Zeilen „nicht gelaufen“ sind `MitschnittAufnahmeTests` und `MitschnittAbspielenTests`. Bau-Protokoll: `COPY --from=dotnet-bericht` und `COPY tools/spec-abdeckung.sh` ausgeführt, Stage `test-bericht` ausgeführt (`Passed: 28`), nicht `CACHED`.
  * `make spec-abdeckung-selbsttest` frisch (`docker build --no-cache --target spec-abdeckung-selbsttest`; die zwei `CACHED`-Zeilen sind Basis-Image und `WORKDIR`, `COPY` und `RUN` der Stage liefen): Zählung, Schalter, Umbenennung, zwei Verzeichnisse, C#-Fälle, C#-Bericht fehlt grün; fünf Mutationen „rot gesehen“ (`cs-eingang-entfernt`, `trx-ergebnis-immer-bestanden`, `tag-haftet-ueber-andere-zeilen`, `feldzeile-mit-zuweisung-ist-methode`, `trx-ohne-methodenzuordnung`); Gesamt „gruen“.
  * `make referenztest-dotnet` frisch: `docker build --target test --no-cache-filter test referenztest-dotnet`, Exit 0, `Passed: 28, Failed: 0, Skipped: 0`. (Das Make-Target selbst fuhr in `make gates`, siehe unten.)
  * `make docs-check`: 997 Dateien, 0 Befunde. `make stempel-eingaenge-pruefung`: ok.
  * `make gates`: Exit 0. Die drei Stempel (`werkzeug`, `tests`, `stempel`) fanden keinen Beleg an einem Vorfahren (erwartet: Dockerfiles in den Eingangslisten geändert), alle Gruppen liefen voll: Selbsttests der Stempel 118 / 95 / 99 / 64 Fälle, `gate-zuordnung` 16 Gates, Selbsttest 33 Fälle, `stempel-eingaenge` ok.
  * Eigene Proben zu F-1 (unten), Gleichlauf der Gate-Stage `test` (unten).
* Nicht selbst gefahren: `make e2e`, `make vergleichslauf-d` (kein Gate, EULA; für diesen Slice ausgenommen). Das Original des Nutzerentscheids habe ich nicht gesehen.

## Gesamturteil

**Closure-fähig: ja, mit Vorbehalt auf drei Nachzügen, die dem Planner und dem Closure-Schritt gehören (V-1, V-2, V-3); keiner ist ein Mangel des Codes.** Beide Liefer-Punkte der DoD sind geliefert und am Bestand wiederholt: Matrix reproduziert, Selbsttest frisch grün mit fünf roten Mutationen, `make gates` grün, kein neues Paket, kein Gate gelockert. 0 HIGH, 0 MEDIUM, 2 LOW, 3 INFO. Die DoD-Häkchen „Review durchgeführt“, Closure-Notiz, Risiko-Ausgänge und Paarungen sind im Slice offen; das ist vor Closure erwartet und kein Befund (ich setze keine Haken).

## DoD-Punkte

| # | DoD-Punkt | Urteil | Beleg |
|---|---|---|---|
| 1 | Der Erzeuger liest C# und den .NET-Bericht: C#-Eingang (`CS_QUELLE`, Vorgabe), TRX-Bericht (`CS_BERICHTE`) aus der Stage per Ausgabe herausgeführt; ein getaggter C#-Test zählt bei bestandenem Testfall, sonst „rot/übersprungen“ bzw. „nicht gelaufen“; kein Gate; Zeile in `harness/README.md` und Kopf nennen den Eingang; `SPEC-045` mit Nachweisen | erfüllt, mit V-1 (Wortlaut „Stage `test`“) | `tools/spec-abdeckung.sh` Kopf: Eingänge `CS_QUELLE`, `CS_BERICHTE` und Leseregel des C#-Tags samt Grenzen. Bericht: Stage `test-bericht` in `referenztest-dotnet/Dockerfile` mit eingebautem Logger `trx;LogFileName=bericht.trx`, `|| echo` (roter Test verliert den Bericht nicht), `test-bericht-export` per `--output`; `tools/mk/spec-abdeckung.mk` baut zuerst den Export mit `--no-cache-filter test-bericht`, reicht ihn als benannten Kontext `dotnet-bericht` an `spec-abdeckung-export` (Stage kopiert Quellen und Skript hinter den Gradle-Lauf). `SPEC-045`: 20 bestanden, 2 nicht gelaufen, Matrix reproduziert. `make spec-abdeckung` bleibt ohne Gate-Anbindung (kein `GATE_CHECKS`; `git diff` berührt `tools/mk/*.mk` nur im Rezept und Kopf). README-Zeilen aktualisiert (Kotlin- und C#-Tests; fünf Mutationen). |
| 2 | Selbsttest: Mini-Bestand mit C#-Quelle und TRX; Happy, Negative (rot, nicht gelaufen, übersprungen, unbekannt getaggt), Boundary (mehrere Tags/Zeilen, Tag ohne Methode, Kotlin- und C#-Nachweis derselben Kennung, mehrere Attribute); mindestens eine Mutation (C#-Eingang entfernt) rot | erfüllt | Frischlauf siehe oben: Fall „C#-Faelle“, „C#-Bericht fehlt“ und fünf Mutationen rot gesehen, darunter genau die geforderte `cs-eingang-entfernt`. Die Fälle selbst habe ich im Selbsttest-Skript nicht Zeile für Zeile gegen die Aufzählung der DoD abgehakt; Stichprobe und der Review-Report (Negativbefund) tragen es. |
| 3 | Nachweis: Wirkung am echten Bestand vorher/nachher | erfüllt | §7: vorher `SPEC-045` 0 Nachweise, 10 ohne Nachweis, `42 von 56` (eingecheckte Matrix `cbe4fb06`; gegengelesen im Review F-7); nachher 20 / 2, 9 ohne Nachweis, `43 von 56`: von mir wiederholt und gleich. Differenz 42 auf 43 und 10 auf 9 trägt `SPEC-045`; die übrigen Zeilenänderungen kommen von Kotlin-Tests auf `main` (laut §7). Die Aussage „absichtlich roter Assert ergab 19 / 3“ habe ich nicht wiederholt (Review und Implementer-Beleg). |
| 4 | `make gates` grün | erfüllt | von mir gefahren, Exit 0 (siehe oben). |
| 5 | Review durchgeführt, Report liegt vor | Report liegt vor (`docs/reviews/2026-11-06-dd87c07-review-…`); Häkchen im Slice offen | Planner/Closure setzt es. |
| 6 | Doku-Update (`harness/README.md`) | erfüllt | Zeilen `make spec-abdeckung`, `make spec-abdeckung-selbsttest` geändert; `make docs-check` 0 Befunde. `spec/spezifikation.md` nennt den Erzeuger nicht (Prüfauftrag aus „Berührte Spec-Stellen“, SPEC-050): bestätigt, kein Nachzug. |
| 7 | Beobachtungs-Register fortgeschrieben | erfüllt | `BEO-TDS/spec-abdeckung-liest-nur-kotlin/state.md` (Träger entschieden, Umsetzungsstand), `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor/evidence/slice-spec-abdeckung-liest-csharp.md` neu. |
| 8 | Closure-Notiz, Risiko-Ausgänge, Paarungen | offen (erwartet) | §6: Risiko 1 „entfallen“, Risiken 2 und 3 „offen bis zur Closure“. Inhaltlich wären Risiko 2 „weiter offen, benannt im Kopf des Erzeugers“ und Risiko 3 „teilweise entfallen (C#-Teil frisch erzwungen), Gradle-Teil weiter offen“ die Antworten; die Entscheidung gehört dem Planner. |

## Entscheidungs-Konformität

| Entscheidung | Urteil | Beleg |
|---|---|---|
| Werkzeug erweitern, Logik bleibt in .NET ([ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md)) | konform | Erzeuger liest C#; kein Test nach Kotlin verlegt. |
| Die zwei lokalen Tests nicht in die Abdeckung | konform | `MitschnittLaufTests.cs` unberührt; beide stehen als „nicht gelaufen (kein Ergebnis im Bericht)“, im Kopf des Mk-Fragments benannt. |
| Kein neues NuGet-Paket ([ADR-0035](../plan/adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md)) | konform | `git diff --stat cbe4fb06..HEAD` enthält weder `packages.lock.json` noch `.csproj`; Logger `trx` ist eingebaut; `RUN --network=none`. |
| `make spec-abdeckung` kein Gate | konform | siehe DoD 1. |
| Kein Gate gelockert ([AGENTS.md](../../AGENTS.md) §3.6), kein `@Suppress` (§3.2), Docker-only (§3.1) | konform | keine Schwelle, keine Detekt-Konfiguration im Diff; Mk-Rezept ruft nur `docker build`. |
| Stempel-Eingangslisten ([ADR-0024](../plan/adr/0024-lokale-stempel-zwei-klassen.md)) | konform | beide Dockerfiles liegen schon in den Listen; `make stempel-eingaenge-pruefung` ok; kein neuer `COPY`-Pfad außerhalb. |

## F-1: Gate-Stage `test` unverändert? Und wird die Kopplung rot?

**Befehlsabgleich.** Vorher (`cbe4fb06`): `FROM restore AS test`, `COPY Smoke Smoke`, `COPY Mitschnitt Mitschnitt`, `RUN --network=none dotnet test --no-restore --nologo`. Nachher: `FROM test-quellen AS test` mit `RUN --network=none dotnet test $DOTNET_TEST_ARGS`, die `COPY` und `ENV DOTNET_TEST_ARGS="--no-restore --nologo"` stehen in `test-quellen`. Die Shell-Form `RUN` expandiert zur selben Zeile.

**Beweis per Bau-Vergleich** (alte und neue Fassung des Dockerfiles, `--target test`, selber Kontext): `docker history` zeigt dieselbe Folge (`restore`, `COPY Smoke`, `COPY Mitschnitt`, `dotnet test`), der Inhalt von `/src/Smoke` und `/src/Mitschnitt` im Abbild ist byte-gleich (sha256 je Datei), `Config` ist bis auf **eine zusätzliche Umgebungsvariable** `DOTNET_TEST_ARGS=--no-restore --nologo` gleich. Die Layer-Digests der drei letzten Schichten unterscheiden sich; die Dateizeiten der Quellen (mtime) stammen aus unterschiedlichen früheren Bauten (11:58 gegen 13:10/13:24), mehr habe ich nicht untersucht. Der Befund: das Gate führt dieselben Argumente aus wie zuvor; ein Stage-`test`-Lauf ist mit `--no-cache-filter test` grün (28 bestanden).

**Mutation 1** (Kopie des HEAD im Scratchpad, nur die `RUN`-Zeile der Stage `test` um `--filter "FullyQualifiedName~Smoke"` ergänzt; `make spec-abdeckung`): Exit 0, die Matrix ist **byte-gleich** zum Stand (`43 von 56`, `SPEC-045` 20 / 2). **Nichts wird rot.** Die Matrix zählt weiter Tests als bestanden, die die Gate-Stage dann nicht mehr ausführte. Der Selbsttest sieht die Dockerfile-Stages nicht; `make gates` ändert sich nicht (der Filter verkleinert nur die Gate-Stage).

**Mutation 2** (Argumente in der Basis `test-quellen`: `ENV DOTNET_TEST_ARGS="… --filter FullyQualifiedName~Smoke"`): beide Stages erben; die Matrix folgt (`SPEC-045` 0 Nachweise, 22 nicht gelaufen, `42 von 56`, `make spec-abdeckung` Exit 0). Die Kopplung hält also **für Quellen und Argumente**, aber ein Rot gibt es nicht; die Matrix zeigt Waisen und das Gate bleibt grün.

Urteil: die Setzung in §7 („ein Gleichlauf-Sensor ist damit nicht nötig“) trägt für `COPY`-Zeilen und `DOTNET_TEST_ARGS`; sie trägt **nicht** für Änderungen an der `RUN`-Zeile der Stage `test` selbst. Das ist Restrisiko V-2; die Aussage in §7 ist enger zu lesen, als sie dasteht.

## Review-Findings F-1 bis F-5 am Text

| ID | Urteil | Beleg im HEAD |
|---|---|---|
| F-1 (Schwesterstage ohne Gleichlauf) | behoben im Rahmen der Basis-Stage, Restrisiko V-2 | Basis `test-quellen`, Aussagen im Kopf des Erzeugers, in der Matrix (`Quellen und Argumente wie die Stage test`) und im README nennen `test-bericht`; Mutation 1 bleibt unsichtbar. |
| F-2 (Feldzeile mit Zuweisung) | behoben | Kopf: „ohne `=` vor der ersten `(`“; neuer Selbsttest-Fall und Mutation `feldzeile-mit-zuweisung-ist-methode` rot gesehen (Frischlauf); fünf statt vier Mutationen, README-Zeile nennt „fünf“. |
| F-3 (Grenze vertauschter Tag) | behoben | Kopf nennt „Grenze: der C#-Tag haftet an der naechsten Methodenzeile … ein Tag ueber einer anderen vorhandenen und bestandenen Methode zaehlt fuer diese“. |
| F-4 (Kommentar Fehlerpfad) | behoben | Dockerfile: „der Build von `test-bericht-export` bricht ab (`"/bericht": not found`)“, gleich dem gemessenen Verhalten des Reviews (von mir nicht wiederholt). |
| F-5 (Zahlen im Slice) | teils: Stelle behoben; es bleibt „22 Stellen“ in §1 als richtig (20 + 2) | `grep` zeigt keine „21 Einheitstests“ oder „23 Stellen“ mehr. |

## Findings

| ID | Kategorie | Befund | Pfad | Klasse |
|---|---|---|---|---|
| V-1 | LOW (offen für den Planner, nicht gelöst) | Der Planner-Teil des Slice sagt an vier Stellen „Stage `test`“ für den Bericht des .NET-Laufs, geliefert ist die Stage `test-bericht` (der Implementer-Teil §7 und §5 sagen das richtig). Betroffen: **DoD §2 Z. 66** („den Testbericht der Stage `test` des .NET-Gerüsts“), **Plan-Tabelle §3 Z. 93** („`referenztest-dotnet/Dockerfile` (Stage `test`)“), **Z. 102** („Die Stage `test` des .NET-Gerüsts schreibt den Bericht bisher nicht heraus“, mit dem Satz „ohne die Gate-Stage `referenztest-dotnet` zu verlangsamen oder zu ändern“) und **Rückführung 2 in §4 Z. 115** („die Stage `test` … liefert ohne neues Paket keinen lesbaren Bericht“); dazu §1 Z. 35 und Z. 37 („der Bericht ist der Lauf der Stage `test`“). Die DoD in Z. 66 ist damit nach Wortlaut nicht erfüllt (Bericht kommt aus `test-bericht`, einer Schwesterstage der Basis `test-quellen`); sachlich ist die Zusage („Bericht des .NET-Laufs, ohne Gate-Stage zu ändern“) geliefert. Ob der Wortlaut zu ändern oder die Abweichung als Lieferform zu vermerken ist, entscheidet der Planner; ich habe nichts geändert. Gleiche Klasse wie `BEO-TDS/dod-wortlaut-ueber-der-bauart`. | `docs/plan/planning/in-progress/slice-spec-abdeckung-liest-csharp.md` Z. 35, 37, 66, 93, 102, 115 | DoD-Wortlaut enger/anders als die Bauart |
| V-2 | LOW | Restrisiko aus F-1: die `RUN`-Zeile der Stage `test` ist nicht gekoppelt (Mutation 1: `--filter` nur dort, nichts wird rot, Matrix unverändert). Kopf, README und §7 sagen „Quellen und Argumente“ geteilt, was für `COPY` und `DOTNET_TEST_ARGS` stimmt (Mutation 2), nicht für weitere Flags in der `RUN`-Zeile. Ein Sensor dafür liegt nicht vor und der Slice behauptet keinen; die Setzung „ein Gleichlauf-Sensor ist damit nicht nötig“ in §7 ist breiter als der Beleg ([AGENTS.md](../../AGENTS.md) §3.8). Eine Option (nicht ausgeführt): `RUN`-Zeile ebenfalls in die Basis (`dotnet test $DOTNET_TEST_ARGS`, Logger über Variable). | `docs/plan/planning/in-progress/slice-spec-abdeckung-liest-csharp.md` §7 „Setzung Weg des Berichts“; `referenztest-dotnet/Dockerfile` Stages `test`, `test-bericht` | Aussage trägt weniger weit als geschrieben |
| V-3 | INFO | Zweite Fundstelle (nicht geändert, gehört dem parallelen Slice `slice-qa-vergleichslauf-d-messungen`): `referenztest-dotnet/Mitschnitt/MitschnittEinheitTests.cs` Z. 13 bis 14, „`make spec-abdeckung` liest nur Kotlin-Tests: diese …“, ist falsch. | `referenztest-dotnet/Mitschnitt/MitschnittEinheitTests.cs:13` | Fortschreibung lässt Fundstelle stehen (benannt, übergeben) |
| V-4 | INFO | Grep im ganzen Repo (ohne `.git`, `.harness`, `docs/reviews/`, `done/`) nach „liest nur Kotlin“ / „nur Kotlin“ / „Stage `test`“ im Zusammenhang mit der Abdeckung: außer V-1 und V-3 keine lebende Fundstelle. Treffer in `BEO-TDS/dod-wortlaut-ueber-der-bauart/observation.md` beschreiben den damaligen Stand (Zeitdokument). `harness/README.md` Z. 97 und `tools/mk/matrix-zitate.mk` nennen die Stage `test` für `make matrix-zitate`, das ist richtig und gehört nicht zur Abdeckung. | — | — |
| V-5 | INFO | Schwellen der Verifikation: die DoD verlangt „mindestens eine Mutation“; geliefert und von mir gesehen sind fünf. Cache: der Gradle-Teil der Matrix hängt weiter am Layer-Cache seiner Quellen (`BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`, im Kopf von `tools/mk/spec-abdeckung.mk` benannt, im Kopf des Erzeugers nur für den TRX-Teil); mein Lauf zeigte den Gradle-Teil als `CACHED`, die Matrix war trotzdem gleich. | — | — |

## Für den Planner (Übergabe, nichts gelöst)

1. V-1: Wortlaut „Stage `test`“ in DoD Z. 66, Plan-Tabelle Z. 93 und Z. 102, Rückführung 2 (Z. 115), §1 Z. 35/37: auf `test-bericht` (bzw. „Bericht des .NET-Laufs“) ziehen oder die Lieferform als Abweichung vom Wortlaut festhalten.
2. V-2: Entscheiden, ob die `RUN`-Zeile mitgekoppelt wird (Folge-Arbeit am Dockerfile) oder die Setzung in §7 und der Kopf des Erzeugers auf „`COPY` und `DOTNET_TEST_ARGS`“ eingeschränkt wird.
3. §6 Risiken 2 und 3: Ausgänge setzen. Closure-Notiz mit Lerneintrag, Häkchen „Review“ und die Paarungen sind Sache der Closure.
4. V-3 geht an den Slice `slice-qa-vergleichslauf-d-messungen`.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| Kein neues Paket / keine Lock-Änderung | geprüft, ohne Befund |
| Gate-Kette unverändert (`GATE_CHECKS`, Schwellen, Detekt) | geprüft, ohne Befund |
| Matrix `docs/plan/spec-abdeckung.md` reproduzierbar | geprüft, ohne Befund (unverändert nach Lauf) |
| `make docs-check`, `make stempel-eingaenge-pruefung`, `make gates` | geprüft, ohne Befund |
