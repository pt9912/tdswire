# Verifikationsbericht: slice-jar-ohne-main-und-image (Commit 7c1c3bd)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: Branch `worktree-agent-ad6230bb0b2e3feb1` (HEAD `7c1c3bd`, nicht gemergt) gegen DoD und Setzungen in `docs/plan/planning/in-progress/slice-jar-ohne-main-und-image.md`, [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md). Der Review [16223b9](2026-11-01-16223b9-slice-jar-ohne-main-und-image.md) und die Fix-Runde sind Eingabe.
* Herkunft: Der Bericht des Verifier-Agenten, vom Planner in Kurzform übernommen und vollständig abgelegt. Linkziele dienen nur dem Doku-Gate.

## Gesamturteil

**Closure-fähig mit Bedingungen.** Kein HIGH, kein MEDIUM.

## DoD-Punkte

* **1, Jar ohne `Main-Class` und Prüfung: bestätigt.** Echter Bau `make build` grün. Manifest nur `Manifest-Version` und `Class-Path` (drei Jars in `lib/`: `kotlinx-coroutines-core-jvm-1.10.1`, `kotlin-stdlib-2.4.10`, `annotations-23.0.0`), keine `Main-Class`, kein Jackson und kein YAML in `lib/`, 0 Jar-Einträge `testserver`, `META-INF/LICENSE` vorhanden. `manifest-pruefung.sh` meldet `Main-Class` (auch klein geschrieben und leer), Testserver-Klassen im Jar und Jackson in `lib/` (ohne Beachtung der Schreibweise). `manifest-selbsttest.sh` hat 13 Fehlerfälle; der Aufruf in `build-jar` steht genau einmal. Beleg-Mutation (`Main-Class` im `jar`-Task): `make build` rot („Manifest nennt eine Main-Class (io.github.pt9912.tdswire.runtime.MainKt)“).
* **2, Stages entfernt, Container-Lauf mit Testserver: bestätigt.** Stages `runtime` und `build` (Distroless) und ihre Pins sind weg, es bleibt der Pin `gradle:8.14-jdk21`. `make -n build` ist `docker build --target build-jar` ohne `-t`. Kein `docker build` ohne `--target` in `tools`, `Makefile`, `repo.mk`, `.github`, `harness/mk`. `make laufzeittest` grün, 14 s (gestartet, Port 1433, Exit-Code 143, „gestoppt“, weder Benutzer noch Passwort in der Ausgabe).
* **3, Kopplung und Index: bestätigt.** `make gates` grün (8 s, Cache), `make docs-check` 675 Dateien, 0 Befunde, `kopplung-zahlen` und `skript-selbsttest` grün, Wurzel-README im Ist-Zustand, Vermerk zu Startklasse und Laufzeit-Image in `SPEC-052` entfernt. `make e2e` grün in sieben Stufen, 103 s warm bei Last 3 bis 8 (Stufen 1 bis 7: 1, 14, 20, 17, 22, 15, 14 s); `docs/plan/e2e-abdeckung.md` unverändert.

## Mutationen (sechs eigene, alle rot)

`Main-Class` im `jar`-Task gesetzt; Testserver-Prüfung gelöscht (`testserver-im-jar`); Jackson-Prüfung gelöscht (`jackson-in-lib`, `jackson-gross`); `-qi` zu `-q` bei `Main-Class` (`main-klein`); `-qi` zu `-q` bei Jackson (`jackson-gross`); Aufruf aus `build-jar` entfernt („0 mal auf statt einmal“).

## Fix-Runde gegen den Review

F-1 (Entwurfsstatus sichtbar) behoben, danach vom Nutzer bestätigt. F-2: Wortlaut vom Nutzer geklärt. F-3, F-4, F-5, F-6, F-7 behoben, I-3 behoben. I-4 offen: die Zelle `make e2e` in `harness/README.md` (312 s) ist veraltet; Verifier 103 s warm, Implementer 339 s unter hoher Last.

## Findings

* **L-1:** Closure-Pflichten offen: DoD-Haken, §7, Risiko-Ausgänge, Register (`zwei-artefakt-stages-nebeneinander`, `jackson-abhaengigkeit-fuer-embedder-nicht-gemessen`).
* **L-2:** Die `make e2e`-Zelle der README ist veraltet (286 Zeichen, schon vor dem Slice über 200).
* **L-3:** Der Aufruf in `build-jar` ist nur als Textzeile belegt (Grenze, im Selbsttest benannt); der echte Lauf ist über die erste Mutation belegt.
* **I-1:** Die Prüfung ist namensbasiert; `snakeyaml` und `gson` in `lib/` werden nicht erkannt (der Skriptkopf nennt die Grenze).
* **I-2:** `e2e-abdeckung` wird nicht über das Makefile gerufen.
* **I-3:** `stempel-eingaenge-pruefung` war schon vor dem Slice rot (zwei Bestandsbefunde), nicht erneut gefahren.

## Risiken aus Plan §6 (Vorschlag des Verifiers)

Risiken 1 bis 3 und 6 entfallen oder gelöst (kein `docker build` ohne `--target` im Repo; Kommentar `ConfigKeys` weg). Risiko 4 eingetreten und aufgelöst (Wortlaut der Welle, vom Nutzer am 2026-11-01 bestätigt). Risiko 5 (Lizenzprüfung `lib/`) weiter offen (`BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage`). Risiko 7 durch den strengeren Selbsttest abgedeckt.

## Reihenfolge der Merges

Dieser Slice vor `slice-stempel-migration`; Konflikt nur in `harness/README.md` (Sensors-Zeilen `skript-selbsttest`, `build`, `laufzeittest`).
