# Review-Report: slice-qa-performance, Re-Review der Nachbesserung — 2026-11-16

**Review-Art:** Code (gegen Plan, ADR und Hard Rules; nicht gegen die DoD), Folgelauf zu [2026-11-16-b163a5ce-slice-qa-performance-review.md](2026-11-16-b163a5ce-slice-qa-performance-review.md)

**Gegenstand:** Branch `slice-qa-performance`, `git diff b163a5ce..77d485a6` (Commits `008bc6f4`, `77d485a6`); Gesamtstand gegen Plan, ADRs, Hard Rules

**Skill:** `.harness/skills/reviewer.md` @ `77d485a6` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-16

**Eingangs-Kontext:** `slice-qa-performance` (Plan, §3, DoD), [TDS-QA-04](../../spec/lastenheft.md#tds-qa-04--performance), [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0024](../plan/adr/0024-lokale-stempel-zwei-klassen.md), `AGENTS.md` 3.1 bis 3.8, Erst-Review F-1 bis F-12, Messprotokoll `docs/messungen/lasttest-probe-2026-11-15.md`.

Selbst gefahren: `make docs-check` (Exit 0, 1494 Dateien, 0 Befunde), `make stempel-eingaenge-pruefung` (ok), `make gate-zuordnung` (ok, 33 Fälle), `bash tools/lasttest-selbsttest.sh` direkt auf dem Host (nur bash, Stub, kein Docker; ok, 0,5 s), dazu fünf Handaufrufe von `tools/lasttest.sh` mit einem docker-Stub im Scratchpad. Nicht gefahren: `make skript-selbsttest` (Docker-Bau; der Host-Lauf des Selbsttests ersetzt ihn für den Inhalt, nicht für den Stage-Bau), `make gates`, `clean-build`, `e2e`, `lasttest`. Keine künstliche Last; verfügbarer Speicher 11 200 MiB zu Beginn und am Ende.

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| N-1 | MEDIUM | Die Nachbesserung zu F-3 ergänzt den Satz „zuzüglich des Unterschieds im Bau der Zeilen … ungemessen“, lässt aber im selben Absatz die Schlussaussage stehen: „A-Import baut je Zeile eine Map für den Callback; der Server tut dasselbe nach dem Dekodieren, die Kosten dieses Schritts sind in beiden enthalten.“ „Tut dasselbe“ widerspricht „Typüberführung, ob gleich teuer ungemessen“ (Messprotokoll) und dem neuen Satz davor. Ein Leser nimmt den letzten Satz als geltend. | `AGENTS.md` 3.8; Reviewer-Skill (MEDIUM „Fortschreibung lässt die zweite Fundstelle stehen“, „Zusage ohne ihre gemessene Bedingung“) | `docs/plan/referenz-workload.md` · `der Server tut dasselbe nach dem Dekodieren` | ja: Grep | Fortschreibung lässt zweite Fundstelle stehen |
| N-2 | LOW | `make skript-selbsttest` prüft jetzt auch `tools/lasttest.sh` (Umgebungswerte, Mutation), aber der Kopf von `tools/mk/skript-selbsttest.mk`, der Hilfetext des Targets und die Zeile in `harness/README.md` §Sensors nennen nur `lasttest-statistik.sh`. Die Bindungsangabe ist unvollständig, nicht falsch. | Reviewer-Skill (Zahl/Zuordnung an zweitem Ort ohne Sensor); `AGENTS.md` 3.7 | `harness/README.md` · `lasttest-statistik.sh` in der Zeile von `make skript-selbsttest`; `tools/mk/skript-selbsttest.mk` · `der Statistik des Lasttests (tools/lasttest-statistik.sh` | ja | Gate-Beschreibung nicht mitgezogen |
| N-3 | LOW | Die Prüfung der Umgebungswerte lässt führende Nullen durch: `LASTTEST_ZEILEN=08` besteht die Ziffernprüfung und das `-ge 1`, der Aufruf geht bis zu `docker` (Stub gerufen, Exit 1 statt 2); bash liest `08` später in der Arithmetik als ungültige Oktalzahl. Zwanzigstellige Zahlen enden mit Exit 2, aber über die bash-Meldung „Ganzzahliger Ausdruck erwartet“. Der Selbsttest hat dafür keinen Fall. Eigenes Risiko nur für den Aufrufer. | Maintainability (Eingabeprüfung nach Auftrag zu F-10) | `tools/lasttest.sh` · `[[ ${angabe#*=} =~ ^[0-9]+$ ]]` | ja: Handaufruf mit Stub | Eingabeprüfung nicht vollständig |
| N-4 | INFO | F-9 ist behoben (toter Zweig ersetzt durch `if grep … \| head -1 \| grep -q .`), die Ausgabe der Zeile „import aufbau B abgewiesen“ erscheint dadurch nicht mehr im Lauf (sie wird verschluckt). Der neue Zweig ist, wie der alte, nicht rot gesehen; der Commit nennt F-11 nicht, und im Repo (Slice, Messprotokoll) steht kein Vermerk dazu. | Reviewer-Skill (Eigencode ohne rot machenden Test) | `tools/lasttest.sh` · `Import nicht abgewiesen` | nein | Prüfung ohne gesehenes Rot |

## Status der Findings des Erst-Reviews

| ID | Status | Begründung |
|---|---|---|
| F-1 | behoben | Die drei Haken sind `- [ ]` (`git diff b163a5ce..HEAD` am Plan), kein `- [x]` für Liefer-Punkte im Plan. Der Kopf von `docs/plan/referenz-workload.md` führt die Freigabe weiter als „steht aus (Review, dann Nutzer)“; im Diff ist der Kopf unberührt. |
| F-2 | behoben | Die 63 s stehen jetzt im Messprotokoll (Zeile „Laufzeit“, Bau 3 s, Messung 60 s, Werkzeugstand ohne A-Import), die 92 s (Bau 36 s, Messung 56 s) im Nachtrag; 63 − 3 = 60 und 92 − 36 = 56 stimmen. Last 8,2 bis 7,1 und 5,8 bis 6,5 stimmen mit den Kopfzeilen des Protokolls (1-Minuten-Werte), 4,3 bis 5,7 und 91/64 s mit dem Abschnitt „Größere Probe“. Die Quelle der Zahlen ist die Ausgabe des Werkzeugs (`lasttest: Laufzeit …`, im Repo erzeugt). |
| F-3 | teilweise behoben | Satz zu „C gegen A-Import“ ist eingeschränkt; der Schlusssatz desselben Absatzes widerspricht (N-1). |
| F-4 | behoben | Die Plan-Tabelle nennt jetzt `Dockerfile`, `referenztest-dotnet/` (Stage `lasttest`, `Lasttest/*.cs`), `tools/e2e-selbsttest.sh`, `tools/mk/e2e.mk`, `skript-selbsttest.mk`, `lasttest.mk`, `README.md` und das Messprotokoll. Eine Datei darüber hinaus fand der Gegenabgleich gegen `git diff --stat` nicht (`tools/lasttest-selbsttest.sh` und `lasttest.sh` fallen unter „Skript und Make-Fragment unter `tools/`“). |
| F-5 | behoben | `vermerke()` ist die eine Stelle; `stufe()` und beide A-Zeilen (Export je Backend, Import) rufen sie mit dem `vorher` der Stufe. `vorher` wird vor `docker run` von A gesetzt (Zeile um 138); der Vermerk beginnt mit Leerzeichen, die Zeilen bleiben spaltenstabil. Der Kopf des Skripts sagt „in jeder Messzeile (Aufbau A, B und C)“, gleich dem Dokument. Kein Lauf mit Vermerk wurde gesehen (kein Lauf gefahren, siehe N-4). |
| F-6 | behoben | KDoc in `ReferenzZeilen.kt` nennt die Kopplung zu `ReferenzZeilen.cs`, die Sensor-Reichweite (Export ja, Import nein) und sagt nicht mehr „einziger Erzeuger“. Gegenstelle: der Kopplungskommentar steht im Erst-Stand auf der .NET-Seite (laut Erst-Review); Zeilenlänge in `77d485a6` behoben. |
| F-7 | behoben | „8,7- bis 10-Fache“ gegen die Tabelle: 1025/118 = 8,69, 1183/118 = 10,03, 1125/123 = 9,15; stimmt. |
| F-8 | behoben | Vorzeichen geprüft: B kehrt um (1025 gegen 1183 mit Senke schneller, 1035 gegen 1008), C-Export ohne Senke in beiden Läufen langsamer (1131/1125, 1046/1028), C-Import ohne Senke in beiden schneller (563/571, 464/478); „höchstens 3 Prozent“ (2,9 %) und „unter der Streuung“ (Min/Max überlappen) tragen. |
| F-9 | behoben | siehe N-4 (Zweig erreichbar, nicht rot gesehen). |
| F-10 | im Kern behoben | Die Prüfung läuft vor dem Bau und vor jedem `docker`-Aufruf (Stub-Handlauf: `-5`, `abc`, `1.5` enden mit Exit 2 ohne Docker; der Selbsttest belegt fünf Fälle und eine Mutation). `$zeilen` steht nicht mehr im awk-Programmtext (`-v z=`). Rest: N-3. |
| F-11 | offen, kein Befund | Die Nachbesserung benennt die nicht rot gesehenen Prüfungen (Zeilenvergleich, Zählprüfung, Messzeilen-Kopplung, `LasttestTest`, neuer F-9-Zweig, `vermerke()`) nirgends im Repo; der Commit-Text übergeht F-11. Es ist kein Befund, weil F-11 die Gegenprobe an den Verifier gab; die Übergabe ist aber nur dann ehrlich, wenn der Verifier sie als offen liest. |
| F-12 | trägt | siehe unten. |

## Wirksamkeit der neuen Prüfung und ihres Selbsttests

- **Prüfung vor Docker:** die Zahlenprüfung steht nach der Variablenbelegung und vor `lauf=`; davor steht kein Aufruf von `docker`, `git` oder `/proc`. Der Stub-Aufruf belegt Exit 2 ohne Docker für Text, `0`, `2;x` und Seed-Text (Selbsttest, fünf Fälle).
- **Mutation:** `sed '/^# Zahlenwerte vor dem Bau/,/^lauf=/{/^lauf=/!d}'` entfernt den Block (Kommentar bis vor `lauf=`); der Selbsttest meldet, wenn die Mutation nichts änderte (`cmp`) und wenn sie grün bleibt. Ohne den Block lief mein Handaufruf `LASTTEST_ZEILEN=08` bis zum Stub-`docker`, das ist dieselbe Wirkung, die die Mutation messen soll. Die Mutation hängt am Kommentartext „# Zahlenwerte vor dem Bau“; wird er umformuliert, meldet der Selbsttest „aenderte nichts“ und wird laut. Kein stiller Ausfall.
- **Stage:** `Dockerfile` kopiert `tools/lasttest.sh` in `skript-selbsttest`; der Selbsttest braucht `awk` (im Kopf genannt). `make stempel-eingaenge-pruefung` und `make gate-zuordnung` sind ok: `tools/` ist als Verzeichnis Eingang der Klasse `werkzeug` (`tools/stempel/werkzeug.eingaenge`), eine Änderung an `tools/lasttest.sh` ändert damit den Hash der Klasse. Ob `awk` in der Basis der Stage liegt, belegt nur ein Lauf von `make skript-selbsttest` (nicht von mir gefahren; Verifier).

## F-12: Einschränkung statt Anhebung des Wächters

Die Aussage ist ehrlich und im Skript, im Dokument (S8) genannt: Grenze je Container, Summe der Limits 6 GiB nicht geprüft, Client-Spitze ungemessen. Ob es für den Speicher reicht: der Wächter läuft vor dem Server **und** vor dem Client (`speicherwacht` an den Stellen vor `docker run -d` und vor `docker run` des Clients), der zweite Aufruf sieht den Verbrauch des laufenden Servers bereits abgezogen. Im ungünstigen Fall bleiben nach dem Start des Clients 4096 MiB − 2 GiB Client-Limit = 2 GiB frei; die Limits sind Kappen, gemessene Serverspitzen liegen bei 101 bis 178 MB. Der Wächter ist eine Eintrittsprüfung, keine Laufüberwachung; das steht nicht im Text, folgt aber aus „vor jedem Container“. Für 100 000 und 1 Mio Zeilen trägt das; für den Heap-Lauf mit 10 Mio und mehrere Sitzungen (Folge-Slices) ist es keine belegte Aussage. Kein Befund, ein Hinweis für die Folge-Slices: dort wird die Client-Spitze gebraucht.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main` | im Diff nicht berührt; keine Schicht-, Jackson- oder Modulgrenze-Frage; geprüft, ohne Befund |
| `testserver/kotlin` (`ReferenzZeilen.kt`) | nur KDoc geändert, kein `@Suppress`, Zeilenlänge im Kommentar angepasst; geprüft, ohne Befund |
| `Dockerfile`, `tools/stempel/*.eingaenge`, Gate-Zuordnung | COPY-Zeile erweitert, Stempel-Eingänge und Klassenzuordnung ok (zwei Sensoren gefahren); geprüft, ohne Befund außer N-2 |
| `tools/lasttest.sh`, `tools/lasttest-selbsttest.sh` | Host-Lauf des Selbsttests ok; keine Docker-Änderung außer COPY; Aufräumen und Wächter unverändert; geprüft, N-3 und N-4 |
| `docs/messungen/lasttest-probe-2026-11-15.md` | alle neuen Zahlen mit Zeile oder Ableitung im Protokoll (63 s, 92 s, 3 %, 8,7 bis 10); kein neuer Erzeuger außerhalb des Repos; geprüft, ohne Befund |
| `docs/plan/referenz-workload.md` | Kopf unverändert „Freigabe steht aus“, kein Haken in der DoD, Laufzeitzeile mit Belegzeilen; geprüft, N-1 |
| `docs/plan/planning/in-progress/slice-qa-performance.md` | Haken zurückgenommen, Tabelle vollständig gegen `git diff --stat`; geprüft, ohne Befund |
| Zweite Fundstellen (Grep „Faktor 8“, „8 bis 9“, „63 s“, „einzige Erzeuger“, „gleich teuer“, „C gegen A-Import“) | keine alte Lesart außerhalb von `docs/reviews/` übrig, außer N-1; geprüft |
| `docs/user/`, ADR-Bestand | im Diff nicht berührt; geprüft, ohne Befund |

## Summary

Kein HIGH. Ein MEDIUM (N-1: der Schlusssatz in 3.6 des Workload-Dokuments widerspricht der neuen Einschränkung), zwei LOW (N-2 Beschreibung des Gates, N-3 führende Nullen), ein INFO (N-4 / F-11: nicht rot gesehene Prüfungen sind im Repo nicht benannt). F-1, F-2, F-4 bis F-10 tragen; F-3 ist halb behoben (N-1); F-12 reicht für diesen Umfang. Keine Rollen-Widersprüche; der Konflikt-Pfad ist nicht ausgelöst.
