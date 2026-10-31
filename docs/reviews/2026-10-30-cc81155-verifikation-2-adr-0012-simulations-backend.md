# Verifikationsbericht 2: [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) Simulations-Backend (Proposed) nach zwei Fix-Runden und Lastenheft 0.5.0 — Stand cc81155, 2026-10-30

**Rolle:** Verifier (Frage: „Bauen wir es richtig?“ gegen Plan, DoD, Spec und Nutzerentscheidung). Der Bericht ändert nichts am Repo außer sich selbst; Reparatur und Entscheidung liegen beim Planner und beim Nutzer.

**Gegenstand:** [slice-adr-simulation-formen](../plan/planning/done/slice-adr-simulation-formen.md) (DoD), [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) (`Proposed`), [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) und [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), die Folge-Slices, der Lastenheft-Diff f067e2f. Bezug: [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-08](../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md).

**Frühere Berichte:** [Verifikation V-1 bis V-11](2026-10-30-69c32e6-verifikation-adr-0012-simulations-backend.md), [Review F-1 bis F-13](2026-10-30-69c32e6-adr-0012-simulations-backend.md), [Nachreview N-1 bis N-8](2026-10-30-1565700-nachreview-adr-0012.md).

**Stand:** `main` bei cc81155 (Merge „Nachführung Lastenheft 0.5.0“ zur ADR). Gelesen: Diff 1565700 bis cc81155 auf ADR, Spec, Lastenheft, Slices; die ADR und der Slice vollständig.

**Hinweis zur Bestätigung:** Für die Nutzerentscheidungen vom 2026-10-30 gibt es im Repo weiter kein Protokoll der Antworten. Geprüft wird, ob die ADR die Liste des Auftrags vollständig und richtig wiedergibt. Für Entscheidung 5 gibt es jetzt einen Primärbeleg im Repo: die Lastenheft-Änderung f067e2f. Dass der Nutzer den Rest so entschieden hat, bestätigt dieser Bericht nicht.

## Ergebnis in Kürze

Alle Befunde der drei Vorberichte sind behoben oder ehrlich offen geführt; keiner ist stillschweigend liegen geblieben. Die acht Nutzerentscheidungen stehen vollständig und richtig in der ADR. Die Setzungen sind als solche gekennzeichnet. Der Lastenheft-Diff enthält genau die drei erlaubten Änderungen und nichts sonst. Die Spec verlinkt nirgends abwärts. Die nachgefahrenen Zahlen stimmen. Neu gefunden habe ich **einen kleinen Träger-Befund (W-1)** und mehrere Hinweise; **keinen Befund, der den Inhalt der ADR ändert.**

**Kann [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) auf `Accepted` gesetzt werden? Ja, inhaltlich ja. Eine Vorbedingung, die den Slice betrifft und nicht die ADR (W-1: ein Satz), und die Entscheidung liegt beim Nutzer/Planner. Offen 12 (a) und (c) sind keine Blocker.** Begründung ausführlich in Abschnitt 7.

## Selbst gefahren (Belege nicht übernommen)

Alle Läufe in Docker, nichts gemountet (das Doku-Gate bringt sein eigenes Read-only-Mount mit). Arbeitsverzeichnis: `/tmp/claude-1000/-Development-tdswire/8562f454-84ba-456b-9970-945e69c035f4/scratchpad/v2` (Repo-Kopie aus `git archive HEAD`, einmal unverändert in `orig`, einmal mit Jackson in `jack`). Das Repo selbst wurde nicht verändert (`git status`: nur das schon vorher ungetrackte `.claude/worktrees/`).

| Lauf | Ergebnis | Urteil |
|---|---|---|
| **`make deps-lock` (der reparierte Weg, volle Aufgabenliste mit `test`) auf der unveränderten Kopie** | Exit 0; `gradle.lockfile`, `settings-gradle.lockfile`, `gradle/verification-metadata.xml` **byte-gleich** mit dem Repo (`cmp`) | Reparatur 48a8ebf wirkt: der Befund des [Nachreview N-1](2026-10-30-1565700-nachreview-adr-0012.md) („413 tests completed, 46 failed“) tritt nicht mehr auf |
| **Dasselbe mit `jackson-dataformat-yaml` 2.21.7 als `implementation`** (Version in `gradle/libs.versions.toml`, eine Zeile in `build.gradle.kts`; sonst nichts) | Exit 0. `gradle.lockfile` **+6 Zeilen, 0 entfernt** (`jackson-annotations` 2.21, `-core`, `-databind`, `-dataformat-yaml`, `jackson-bom`, `snakeyaml` 2.5); `settings-gradle.lockfile` unverändert; `verification-metadata.xml` **122 auf 133 Komponenten, +73 Zeilen, 0 entfernt**, 60 410 auf 64 948 Byte (rund 4,5 KB). Die elf neuen: `oss-parent` 75, `jackson-base`, `jackson-bom`, `jackson-parent` 2.21, `jackson-annotations` 2.21, `-core`, `-databind`, `-dataformat-yaml`, `-dataformats-text`, `junit-bom` 5.14.1, `snakeyaml` 2.5 | Messzeile **MK bestätigt**, jetzt auch mit dem echten Werkzeug und mit `test` statt nur mit der Ersatz-Aufgabenliste ohne `test`. Die Zahlen der ADR gelten also unverändert |
| Maven-Katalog `maven-metadata.xml` für `jackson-dataformat-yaml` | `latest` und `release` 2.22.4; Liste 2.21.5 bis 2.21.8 und 2.22.0 bis 2.22.4; `lastUpdated` 2026-10-17. POM-Abruf HTTP 200 für 2.21.7 (21.09.), 2.21.8 (17.10.) und 2.22.4 (17.10.) | Messzeile **MV bestätigt** (2.22.4, 2.21.7, 2.21.8 mit POM vom 2026-10-17) |
| d-migrate per `gh api` | Commit `a33984910bc3c760ad2f7c9733f5da0f12e1abf7` existiert, 2026-10-27 12:08; Release `v1.12.0` vom 2026-10-27 (`target_commitish` `main`); `main` steht heute noch auf diesem Commit; Lizenz `MIT`; `gradle.properties` bei a339849: `jacksonVersion=2.21.7` | Belegstand, Entscheidung 4 („die Version, die d-migrate verwendet“) und Lizenzaussage **bestätigt**; der Permalink ist aktuell, kein neueres Release |
| `spec/schema.json` bei a339849 (geparst) | JSON Schema 2020-12; Pflicht oben `schema_format`, `name`, `version` (`tables` nicht); `schema_format` Konstante `"1.0"`; `additionalProperties: false` oben, in `table` und in `column`; Tabelle Pflicht `columns` (`minProperties` 1) und `primary_key` (`minItems` 1); Spalte Pflicht `type`; Datei-Ebene 13 Felder (4 gelesen, 9 ignoriert), Tabelle 7 (2 gelesen, 5 ignoriert), Spalte 19 (7 gelesen, 5 ignoriert, 7 typgebunden); `type`-Enumeration genau 21 Werte; `max_length` und `ordinal` `minimum` 1; `precision` `minimum` 1 und `scale` `minimum` 0, beide „Nur bei type: decimal (Pflicht)“ | Setzungen 2, 3 und 8, Entscheidung 8 (zehn abbildbar, elf plus `datetime` mit Zone abgewiesen), Offen 7 **bestätigt**; keine Lücke in der Ignorierliste |
| `make docs-check` vor diesem Bericht | `d-check: 313 Datei(en) geprüft, 0 Befund(e)` | grün; Lauf mit Bericht siehe Abschnitt 8 |

**Nicht nachgefahren:** `make gates` (kein Code im Diff; die DoD-Zeile „`make gates` grün“ ist im Slice ehrlich offen), die Spikes M1, M2, ML, MD1 bis MD3, MR sowie die Läufe von `schema generate` (Image 1.12.0). Diese Zahlen stützen sich auf die Optionsberichte, die frühere Verifikation hat ML (117 bis 130 µs gegen 205 µs, Größenordnung) und die Jar-Größen (2 788 997 Byte) bereits nachgefahren.

## 1. DoD Punkt für Punkt ([slice-adr-simulation-formen](../plan/planning/done/slice-adr-simulation-formen.md) §2)

| DoD-Punkt | Haken | Befund |
|---|---|---|
| Liefer-Punkt 1 — Optionen mit Messung (a) Beschreibungsform, (b) Scanner-Grenzfälle, (c) Überführung; „Messwerte stehen als Tabelle im Slice und in der Entscheidung“ | `[ ]` | **ehrlich offen, Sachstand unverändert seit V-8.** Inhaltlich erfüllt: Optionen und Messzeilen M1 bis MV in der ADR, Rohtabellen in den beiden Berichten. Formal nicht erfüllt: Der DoD-Satz verlangt die Tabelle „im Slice“, steht unverändert da; die *Stand*-Zeile verlinkt jetzt den [Verifikationsbericht (V-8)](2026-10-30-69c32e6-verifikation-adr-0012-simulations-backend.md) (N-3 behoben) und schlägt die Satzänderung vor. Die Änderung des DoD-Satzes ist Sache von Planner und Nutzer; **offen, kein Mangel der ADR**. Der Aufwand eines YAML-Parsers ohne Bibliothek ist nicht gemessen und als solcher in der Alternativen-Tabelle der ADR geführt (V-7 behoben) |
| Liefer-Punkt 2 — Entscheidung des Nutzers als neue ADR | `[ ]` | **zu Recht offen** (`Accepted` fehlt). Erfüllt: nächste freie Nummer, `Bezug` auf [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), kein `Supersedes`, ADR-Index (Zeile `Proposed`), Geschichte nachgezogen (vier Zeilen), jede Aussage trägt Messzeile, Art-Vermerk oder ist als Setzung gekennzeichnet (Abschnitt 3). Review ([F-1 bis F-13](2026-10-30-69c32e6-adr-0012-simulations-backend.md), [N-1 bis N-8](2026-10-30-1565700-nachreview-adr-0012.md)) und Verifikation (dieser Bericht) liegen vor |
| Liefer-Punkt 3 — Anschlussstellen | `[x]` | **erfüllt.** SPEC-035 und SPEC-036 fortgeschrieben, SPEC-040 und SPEC-045 nachgezogen; Folge-Slices mit Start-Trigger und Risikozeilen angeschlossen (Abschnitt 4); Vertragsdatei: Antwort A (keine). Die Begründung für das Wort „vorläufig“ in der Überschrift von SPEC-036 trägt: [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md) (`Accepted`) verlinkt den Anker, der Sachgrund steht im Text |
| Nachweis-Pflichten (`make gates`, Review, Doku-Update, Closure-Notiz, Register, Risiken, Paarungen) | `[ ]` | alle ehrlich offen. §7 „Wird bei der Closure gefüllt“. Sieben Risiken in §6 tragen „offen bis zur Closure“, das ist vor der Closure zulässig. Die Review-Zeile kann der Planner nach Abgleich mit F- und N-Bericht abhaken; die zweite Fix-Runde (c00e8eb) und die Nachführung 9dbfcfe hat kein Reviewer gesehen, nur dieser Verifier (kleine Diffs, Abschnitt 2 und 5) |

Die *Stand*-Zeile in Punkt 3 spricht von „zwei Risikozeilen in codec-datentypen-erweiterung und referenzabgleich-live“; tatsächlich tragen fünf Slices außerhalb der Welle Zeilen (zusätzlich `qa-kompatibilitaet`, `adr-backend-grenzen`, `release-artefakt-lizenz`). Das ist eine Untertreibung, kein Fehler; keine Aktion nötig.

## 2. Lastenheft-Diff (f067e2f, Rang 1)

`git diff f067e2f~1 f067e2f -- spec/lastenheft.md`: **3 Zeilen hinzugefügt, 2 entfernt, drei Stellen, sonst nichts.**

1. Kopf: `Version: 0.3.0` auf `0.5.0`. Der Kopf stand nach dem Commit 3ef432f („Lastenheft 0.4.0“) noch auf 0.3.0, obwohl die Historie 0.4.0 führt; der Sprung auf 0.5.0 gleicht beides an. Keine Änderung von Inhalt.
2. [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) Beschreibung: eingefügt „sowie der Prüfung auf NULL (`IS NULL`, `IS NOT NULL`)“ hinter „einfachen Gleichheits- und Mengenbedingungen“; der Rest des Absatzes ist byte-gleich (Wort für Wort verglichen).
3. Historie: Zeile `0.5.0 | 2026-10-30 | … | [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)`.

Nicht berührt: Akzeptanzkriterien von [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), die Zeile unter „Offene Entscheidungen“ (steht weiter dort, Zeile 216), alle anderen Anforderungen. `spec/architecture.md` und `contracts/` unverändert. **Die ADR hat das Lastenheft nicht geändert** (kein Eintrag in den Commits 9dbfcfe, c00e8eb, e133a8e); die Änderung ist ein eigener Commit des Nutzers. Ob das Lastenheft-Datum im Kopf (`2026-10-29`, unverändert) mitzuziehen ist, ist Sache des Nutzers (Hinweis W-4).

## 3. Nutzerentscheidungen vom 2026-10-30 gegen die ADR

| # | Entscheidung laut Auftrag | [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) | Befund |
|---|---|---|---|
| 1 | Datei zusätzlich zur Kotlin-Schnittstelle | Entscheidung 1 (Frage 1 B); Verdrahtung Setzung 11; SPEC-035 „Beschreibung als Datei“ | **richtig, vollständig** |
| 2 | d-migrate-Modell YAML | Entscheidung 2: neutrales Schema-Modell, `schema_format` `1.0`, Permalink a339849, nichts kopiert, Lizenz MIT belegt | **richtig**; Permalink und Lizenz selbst bestätigt |
| 3 | YAML direkt | Entscheidung 3 (N1 A); Alternativen DDL, beides, JSON mit Gründen | **richtig** |
| 4 | Jackson 2.21.7 | Entscheidung 4 (N2 A); Version von d-migrate (bestätigt), Katalog mit 2.21.8 und 2.22.4 genannt, Strom-API, keine Objekte aus Tags; „ein weiteres Motiv des Nutzers ist nicht belegt“ | **richtig**; der Hinweis, 2.21.8 wäre eine eigene Entscheidung, ist ehrlich |
| 5 | `IS [NOT] NULL` jetzt vom Lastenheft 0.5.0 gedeckt | Entscheidung 5: „Entscheidung des Nutzers, vom Lastenheft gedeckt“, Lastenheft seit 0.5.0 wörtlich, Offen 12 (b) erledigt mit Beleg; Tabelle „Deckung“ führt die Zeile als „wörtlich“; [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) sagt „durch das Lastenheft (Version 0.5.0, `[TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)`) gedeckt“ | **richtig und gegen das Lastenheft belegt** (Abschnitt 2); der frühere Befund [N-2](2026-10-30-1565700-nachreview-adr-0012.md) („Spec-Zusage ohne Anker im Lastenheft“) ist damit **entfallen**. Geschichte-Zeile 1 sagt noch „als Setzung des Nutzers“; Zeile 4 stellt es richtig, das ist die Chronik (W-5) |
| 6 | Überführung streng, verlustfrei | Entscheidung 6 (Frage 5 A), M2 mit „zehn im Ergebnis, zwei nur im Wortlaut“, `TDS_SIM_INVALID_VALUE`, Ausnahmeliste | **richtig** |
| 7 | `max_length` lesen, nicht durchsetzen | Entscheidung 7 (N4a A), positive Ganzzahl (Schema: `minimum` 1, bestätigt), Ausnahmeliste (1) | **richtig** |
| 8 | Typlücken beim Laden abweisen | Entscheidung 8 (N4b A), Ladefehler mit Pfad, Zeile, Spalte, Typname; kein Teilladen; zehn Typen in Tabelle; Liste der Abgewiesenen deckt die übrigen elf plus `datetime` mit Zone | **richtig, gegen die 21er-Enumeration vollständig** |

**Kennzeichnung der Art:** Die ADR trennt „Entscheidungen des Nutzers“ (acht) von „Setzungen (Voreinstellung 2026-10-30, nicht gemessen)“ (vierzehn). Die Art **„Setzung des Nutzers“** (Zeile 51) wird jetzt von keiner Entscheidung mehr benutzt; sie ist ein toter Begriff der Legende, kein Fehler. Setzungen, die nur die Spec tragen (Skalare, Dateigröße, Scanner, Überführung, Ladefehler), tragen dort „Setzung, nicht am Server gemessen“. Die Lesart „die Liste der positiven Formen ist abschließend“ ist in ADR **und** in [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) als „vom Nutzer nicht bestätigt“ geführt (F-2 behoben).

## 4. Konsistenz ADR, Spec, Folge-Slices

* **ADR gegen SPEC-035/036:** gelesene Teilmenge, Ignorierliste (7 + 5 + 7 Spaltenfelder), Pflichtfelder, `tables` als Pflicht („strenger als das Schema“), Nullbarkeit, Tabellenname, Typabbildung, `max_length`, Skalare, 1-MiB-Grenze, Ladefehler, Verdrahtung, Scanner (fünfzehn Wörter), Überführung und Längengrenze (Limit `SPEC-003`, Code `SPEC-014`, behoben N-4) stimmen überein. Das Wort „vorläufig“ in der Überschrift von SPEC-036 bleibt, mit Sachgrund.
* **Spec verlinkt nie abwärts:** `grep` auf `ADR`, `slice-`, `welle-`, `docs/plan`, `Nachreview`, `Optionsbericht` in `spec/` ergibt in `spec/lastenheft.md` und `spec/architecture.md` **0 Treffer**; in `spec/spezifikation.md` **keine Kennung und keinen Link**, einen einzigen Fließtext-Treffer (Zeile 672, „in der ADR als Entscheidung festgehalten“, ohne Kennung, nicht im Diff dieser Runde, nicht zur Simulations-ADR gehörig). Der einzige externe Link ist der Permalink auf `spec/schema.json` von d-migrate. [AGENTS.md](../../AGENTS.md) §3.4 eingehalten.
* **ADR-Bezüge in `.md` nur als Links:** Suche nach ADR- und TDS-Kennungen ohne Link in der ADR, dem Slice und den neun Folge-Slices: 0 Treffer außer dem Titel der ADR selbst (der Überschrift); in der Spec stehen die `TDS-*` als Text in Backticks, das gilt für die Spec seit jeher.
* **Folge-Slices:** [slice-sim-erkennung](../plan/planning/open/slice-sim-erkennung.md) (Leser ohne Bibliothek, Offen 7 und 8, Größen-Risiko, Belegstand), [slice-sim-aenderung-integration](../plan/planning/open/slice-sim-aenderung-integration.md) (Jackson-Abhängigkeit, Adapter, `ForbiddenImport`-Zeile mit Selbsttest-Pflicht, Umgebungsvariable Offen 9), [slice-sim-auswahl-streaming](../plan/planning/open/slice-sim-auswahl-streaming.md), [slice-codec-datentypen-erweiterung](../plan/planning/open/slice-codec-datentypen-erweiterung.md) (Offen 1, 2), [slice-qa-kompatibilitaet](../plan/planning/open/slice-qa-kompatibilitaet.md) (Offen 4, 5), [slice-adr-backend-grenzen](../plan/planning/in-progress/slice-adr-backend-grenzen.md) (Offen 10) und [slice-release-artefakt-lizenz](../plan/planning/open/slice-release-artefakt-lizenz.md) (Lizenz) tragen je einen Verweis auf die ADR; **ein Träger ist zu schmal (W-1)**. Offen 11 hat ausdrücklich keinen Träger und ist so akzeptiert.
* **Detekt/Selbsttest:** `config/`, `build.gradle.kts` und `gradle/` sind seit dem Stand 69c32e6 unverändert (nur das `Dockerfile` hat die Reparatur der Stage `lock`); die Fitness-Zeile „Kein Wächter heute“ ist damit weiter wahr.

## 5. Plan-vs-Doku-Diff (seit 1565700)

| Plan (Slice §3) | Ist | Befund |
|---|---|---|
| ADR | zwei Fix-Runden (e133a8e, c00e8eb), eine Nachführung (9dbfcfe); Status `Proposed`, Geschichte hat vier Zeilen | erfüllt; **keine Nutzerentscheidung geändert** (Geschichte-Zeilen 2 bis 4 sagen es; ich habe die Wortlaute der acht Entscheidungen gegen den Stand 69c32e6 gehalten: gleich außer Entscheidung 5, dort ist die Deckung durch das Lastenheft die Änderung) |
| ADR-Index | Zeile mit `Proposed` und Bezügen | erfüllt |
| Spec | 9dbfcfe: SPEC-035 „Setzung des Nutzers“ auf „durch das Lastenheft gedeckt“; c00e8eb: Fehlercode-Quelle | erfüllt; die Spec-Historie (Zeile 808) nennt die Nachführung 0.5.0 nicht (W-5, Hinweis) |
| Slices | drei Slices der Welle und fünf außerhalb tragen Zeilen; `slice-sim-aenderung-integration` hat die Risikozeile zum Lock-Werkzeug; `slice-adr-simulation-formen` Risikozeile zu Offen 12 (a) und (c) | erfüllt; **zwei Rückstände W-1, W-2** |
| nicht geplant, nicht erfolgt | Code, `config/`, `gradle/`, Lastenheft durch die ADR | `git diff` auf diese Pfade leer außer dem Lastenheft-Commit des Nutzers (f067e2f) und der Reparatur 48a8ebf des `Dockerfile` (nicht Teil der ADR-Arbeit) |

## 6. Stand aller früheren Befunde

**Review F-1 bis F-13**

| ID | Stand | Beleg |
|---|---|---|
| F-1 Gate behauptet, greift nicht | **behoben, ehrlich**: Fitness-Zeile „Kein Wächter heute“ mit Träger; Pflicht-Selbsttest im Verdrahtungs-Punkt | ADR Fitness Function; `slice-sim-aenderung-integration` Liefer-Punkt 3 |
| F-2 Lesart als Tatsache | behoben: SPEC-036 sagt „Setzung … vom Nutzer nicht bestätigt“ | SPEC-036 „Nicht erkannt“ |
| F-3 Träger fehlt | (a) und (c) mit Offen 12 und Risikozeile im Slice getragen, (b) **erledigt** durch Lastenheft 0.5.0 | ADR Offen 12; Slice §6 |
| F-4 Längenlimit | behoben (Setzung 9, SPEC-035, Ausnahmeliste (1)) | — |
| F-5 Feldliste, F-6 Offen 7, F-7 Zählung, F-9 Motiv | behoben und gegen das Schema bestätigt | Lauf oben |
| F-8 Träger ohne Verweis | behoben: beide Slices tragen den Link | Abschnitt 4 |
| F-10 Begründung „Accepted-ADR“ | behoben: Slice nennt [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md) als Anker | Slice §2 Punkt 3 |
| F-11 Lock-Werkzeug | **behoben**: `make deps-lock` läuft durch (48a8ebf), von mir reproduziert, auch mit Jackson | Lauf oben; Rest W-2 |
| F-12, F-13 | Info, keine Aktion | — |

**Verifikation V-1 bis V-11**

| ID | Stand |
|---|---|
| V-1 | wie F-1: behoben, ehrlich |
| V-2 | wie F-5: behoben |
| V-3, V-4 | behoben: `slice-adr-backend-grenzen` (Offen 10, Zeile 113) und `slice-release-artefakt-lizenz` (Satz „nicht mehr hypothetisch“) tragen den ADR-Link |
| V-5 | behoben: 2.21.8 in MV und Entscheidung 4; Wirkung ausdrücklich „nicht geprüft“ |
| V-6 | MD2 auf „sechs Messungen“ korrigiert; ML als Größenordnung gekennzeichnet |
| V-7 | behoben: Alternativenzeile „eigener YAML-Leser“, „nicht gebaut und nicht gemessen“ |
| V-8 | **offen, ehrlich** (DoD-Satz; Planner/Nutzer) |
| V-9 | behoben: Setzungsvermerke in SPEC-035, „beobachtet an drei Eingabedateien“, Offen 7 mit dem Beleg „Pflicht“ |
| V-10 | behoben: [SPEC-040](../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) nennt Leser und Ladefehler, SPEC-045 die gemeinsame Quelle |
| V-11 | behoben: Größen-Risikozeile in `slice-sim-erkennung` (Zeile 119) |

**Nachreview N-1 bis N-8**

| ID | Stand |
|---|---|
| N-1 Lock-Werkzeug ohne Träger, zeitgebundene Zahl | **behoben**: Zahl durch Folgepflicht ersetzt, Risikozeile im Verdrahtungs-Slice, Werkzeug repariert und von mir reproduziert; der Wortlaut der Risikozeile ist nach der Reparatur veraltet (W-2) |
| N-2 IS [NOT] NULL ohne Anker im Lastenheft | **erledigt** durch Lastenheft 0.5.0 (Abschnitt 2) |
| N-3 Verweis ins Leere | behoben: Link auf den Verifikationsbericht |
| N-4 Fehlercode-Quelle | behoben: „Limit: SPEC-003; Code: SPEC-014 und Typabbildung“ in ADR und SPEC-035 |
| N-5 Review-Link | behoben: „Probe im Review F-1“ trägt einen Link |
| N-6 Lizenzmaß | behoben: ADR und Lizenz-Slice nennen, was belegt ist und was nicht (`core`, `annotations` ungeprüft) |
| N-7 ML | behoben: als Größenordnung gekennzeichnet |
| N-8 Verifikation wiederholen | **dieser Bericht** |

## 7. Kann [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) jetzt auf `Accepted` gesetzt werden? ([AGENTS.md](../../AGENTS.md) §3.5)

Nach `Accepted` ist die ADR nur noch per Folge-ADR mit `Supersedes` änderbar. Deshalb ist die Frage: Steht in der ADR etwas, das falsch, breiter als der Beleg oder ohne Träger ist und das nach `Accepted` nicht mehr geradegezogen werden kann?

**Antwort: Inhaltlich ja.** Ich finde in der ADR keinen Satz, der breiter ist als sein Beleg, keine Nutzerentscheidung, die falsch oder unvollständig wiedergegeben ist, keinen Widerspruch zu Lastenheft, [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) oder [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md), keinen offenen Punkt ohne Träger außer dem einen schmalen (W-1). Alle nachgefahrenen Zahlen stimmen.

**Vorbedingung (kein ADR-Eingriff):**

* **W-1 vor dem Statuswechsel erledigen.** Der Slice selbst verlangt in §6, dass die Träger „vor `Accepted`“ stehen. Es fehlt ein Satz in [slice-referenzabgleich-live](../plan/planning/open/slice-referenzabgleich-live.md) (Abschnitt 8).

**Zum Statuswechsel gehört (Planner/Implementer, Mechanik, keine Findings):** Status in der ADR und im ADR-Index auf `Accepted`, eine Geschichte-Zeile `Accepted`, der Haken in Slice §2 Punkt 2, die Start-Trigger der drei Code-Slices (verlangen `Accepted`) sind dann erfüllbar. Die Begründung für „vorläufig“ in Slice §2 Punkt 3 (die ADR sei noch `Proposed`) ist nach dem Wechsel anzupassen: ab dann trägt auch die ADR den Anker `…-vorläufig` (das Feld `Schärft`), die Überschrift darf also **nicht** umbenannt werden.

**Offen 12 (a) und (c): Träger, keine Blocker.**

* **(a) Lesart „die Liste der positiven Formen ist abschließend“.** Die ADR sagt es selbst: Die Tabelle „Deckung“ gilt „nur dann“, wenn man die positive Aufzählung so liest; sie behauptet die Lesart nicht, sie benennt sie. Ihr Wortlaut bleibt nach `Accepted` wahr, gleich wie der Nutzer entscheidet, denn die Bedingung steht im Text. Was von der Entscheidung abhängt, ist die **Spec** (Rang 2, fortschreibbar: der Satz „vom Nutzer nicht bestätigt“ in SPEC-036) oder das **Lastenheft** (Rang 1, falls der Nutzer weitere Formen ausschließen will, wie er es für `IS [NOT] NULL` getan hat). Beides ändert die ADR nicht. **Einzige Ausnahme:** Entscheidet der Nutzer, dass eine der „Nicht erkannt“-Formen (etwa `OR`) *doch* erkannt werden soll, ändert sich Entscheidung 14 der ADR und es braucht eine Folge-ADR. Das ist ein Wechsel der Sache, nicht der Lesart, und kein Grund, `Accepted` jetzt zu verweigern; die ADR setzt die Formen, wie der Nutzer sie gewählt hat.
* **(c) Zeile „Offene Entscheidungen“ zu [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend).** Das ist Buchführung im Lastenheft (Rang 1), die der Nutzer führt; die ADR sagt ausdrücklich, dass sie diese nicht trifft. Sie ändert nichts am Text der ADR und nichts an den Start-Triggern der Code-Slices (die verlangen `Accepted`, nicht die Zeile). Sie hängt umgekehrt an `Accepted`: erst mit der Annahme gilt die Festlegung als getroffen, und die Zeile kann als erledigt geführt werden. Sie blockiert die **Closure des Slice** ([§5](../plan/planning/done/slice-adr-simulation-formen.md): jeder offene Punkt braucht Träger oder Ausgang), nicht die Annahme der ADR. Vor der Abnahme der Anforderung muss sie geführt sein; die Zeile verlangt den Trigger „vor Implementierung des Simulations-Backends“, und der Nutzer sollte sie vor dem Start des ersten Code-Slices führen (Empfehlung, nicht Blocker).

**Was nach `Accepted` nicht mehr korrigierbar ist und deshalb jetzt noch geglättet werden kann (alles optional, siehe W-3 bis W-5):** die Geschichte-Zeile 1 („`IS [NOT] NULL` als Setzung des Nutzers“, überholt durch Zeile 4), die tote Art „Setzung des Nutzers“ in der Legende (Zeile 51) und ein Satz in der Messzeile MK, dass `make deps-lock` seit der Reparatur dieselben Zahlen *mit* `test` liefert (der Wortlaut „ohne `test`“ ist als Messbedingung richtig und bleibt es).

## 8. Befunde

Schweregrade: M = mittel (vor `Accepted` beheben), L = klein, H = Hinweis. Kein M, kein HIGH.

**W-1 (L, vor `Accepted`) — Träger von Offen 6 ist enger als der Punkt.**
[ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) Offen 6: „Servergleiche zu Überführung, Textvergleich, Kommentarschachtelung, Rundung, Collation, Sitzungsoptionen und dem Verhalten der Referenzinstanz mit dem erzeugten Skript. Träger: Slice des Live-Laufs im Referenzabgleich“. Die Risikozeile in [slice-referenzabgleich-live](../plan/planning/open/slice-referenzabgleich-live.md) (Zeile 111) nennt davon Collation, Sitzungsoptionen und das Skript (N5) und die neun Ausnahmekandidaten; **Überführung, Textvergleich, Kommentarschachtelung und Rundung stehen in dieser Datei nicht** (`grep` auf „berührung“, „Bezeichner“, „dreiwertig“, „Textvergleich“, „Kommentar“, „Rundung“: 0 Treffer). [slice-sim-auswahl-streaming](../plan/planning/open/slice-sim-auswahl-streaming.md) (Zeile 111) führt sie als Setzungen, die als Festlegung getestet werden, und verweist auf `BEO-TDS/messung-am-echten-sql-server-fehlt`; deren Träger ist `slice-qa-vergleichslauf-d`, die Server-Seite. Damit ist die Zuordnung der ADR auf den Live-Lauf nur halb getragen. **Empfehlung:** In die Risikozeile von `slice-referenzabgleich-live` einen Satz: Der Lauf vergleicht auch Überführung, Textvergleich (Groß-/Kleinschreibung, nachgestellte Leerzeichen), Kommentarschachtelung und Rundung gegen die Setzungen der ADR. Das ist eine Slice-Änderung, keine ADR-Änderung (`BEO-TDS/offene-punkte-ohne-traeger`).

**W-2 (L) — Die Risikozeile „Lock-Werkzeug“ in [slice-sim-aenderung-integration](../plan/planning/open/slice-sim-aenderung-integration.md) ist veraltet.**
Sie sagt: „`make deps-lock` scheitert in der Stage `lock` an den Tests; eine Reparatur ist außerhalb dieses Slice beauftragt.“ Die Reparatur ist erfolgt (48a8ebf, Merge 1b5f6f4) und von mir mit der vollen Aufgabenliste reproduziert (Exit 0, Lock byte-gleich). Der Slice ist `open/`; der Satz kann bei der Pflege des Slice auf „repariert (48a8ebf); die Closure beschreibt, wie Lock und Prüfsummen geschrieben wurden“ gekürzt werden. Die ADR bleibt wahr (sie sagt „ob `make deps-lock` dazu funktioniert hat“). Kein Blocker.

**W-3 (H) — Messzeile MK und Reparatur.**
MK führt die Aufgaben „ohne `test`“ als Messbedingung (zu ihrer Zeit nötig, weil die Stage `lock` an `test` scheiterte). Mit dem reparierten Weg läuft die volle Liste und liefert dieselben Zahlen (+6, +73 Zeilen, 11 Komponenten, 4 538 Byte). Wer die ADR liest, könnte „ohne `test`“ als bleibende Einschränkung lesen. Optional vor `Accepted` ein Halbsatz in MK; danach nur per Folge-ADR.

**W-4 (H) — Lastenheft-Kopf.**
`Datum: 2026-10-29` im Kopf ist unverändert, obwohl die Version 0.5.0 vom 2026-10-30 ist. Der Kopf trug schon vorher kein Datum je Version (die Historie führt sie). Sache des Nutzers (Rang 1); die Prüfung „nichts sonst geändert“ bleibt davon unberührt.

**W-5 (H) — Chronik und Legende.**
(a) ADR Geschichte-Zeile 1 und Legende (Zeile 51) führen noch „Setzung des Nutzers“ für `IS [NOT] NULL` bzw. als Art; Zeile 4 stellt es richtig. (b) Die Spec-Historie (Zeile 808) nennt die Nachführung 0.5.0 nicht; [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) selbst ist richtig. Beides folgenlos.

**W-6 (H) — Nicht reproduziert.**
M1 (Scanner), M2 (Überführung), ML, MD1 bis MD3, MR sind von mir nicht neu gelaufen; sie stützen sich auf die beiden Optionsberichte (Autor gleich Erwartender, wie die ADR es selbst vorn sagt) und, für ML, auf die erste Verifikation (Größenordnung). Dazu die Grenze der Aussage in der ADR: nichts am echten SQL Server gemessen.

## 9. Sensoren dieses Berichts

* `make docs-check` vor dem Bericht: `d-check: 313 Datei(en) geprüft, 0 Befund(e)`. Lauf mit diesem Bericht: siehe unten.
* Kennungen in diesem Bericht sind als Links geschrieben, soweit sie Dokumente benennen; keine verschachtelten Backticks.
* Selbst gefahren: `make deps-lock` zweimal auf Repo-Kopien (Docker, nichts gemountet, Repo unverändert), `maven-metadata.xml`, `gh api` auf d-migrate, `spec/schema.json` geparst. Nicht gefahren: `make gates`.
* Der Bericht ersetzt weder die Entscheidung über `Accepted` noch die des Nutzers zu Offen 12.

## 10. Empfehlung an den Planner

1. **W-1** in `slice-referenzabgleich-live` (ein Satz), dann steht `Accepted` inhaltlich nichts entgegen. Der Nutzer entscheidet.
2. **Optional vor `Accepted`** (danach nur per Folge-ADR): W-3 (Halbsatz in MK), W-5 (Chronik).
3. **Slice-Pflege:** W-2 (Risikozeile), V-8 (DoD-Satz von Liefer-Punkt 1), die Review-Zeile abhaken, nach `Accepted` die Begründung der „vorläufig“-Überschrift nachführen.
4. **Beim Nutzer:** Offen 12 (a), (c) und W-4; empfohlen vor dem Start des ersten Code-Slices (c), spätestens zur Closure.
5. Diese Verifikation gilt für Stand cc81155; nach Änderungen an W-1 bis W-3 genügt eine Zeilenprüfung, keine zweite volle Verifikation.
