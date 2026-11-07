# Verifikationsbericht: slice-e2e-backend-ausserhalb-modul (Basis 6b28cd69, HEAD 0cd8db72) — 2026-11-06

**Rolle:** Verifier (Modul 11), Frage „Bauen wir es richtig?“ gegen Plan und DoD. Kein Review des Diffs (siehe [Review-Report](2026-11-06-4436821-slice-e2e-backend-ausserhalb-modul.md)), keine Validierung.

**Gegenstand:** Slice [slice-e2e-backend-ausserhalb-modul](../plan/planning/done/slice-e2e-backend-ausserhalb-modul.md); Zweig `worktree-agent-ae2dacd15aa73d2f5`; Commits b3b71d76 (Implementierung), 4436821d (DoD-Haken), b8e1f7b9/00f0a804 (Review), 0cd8db72 (Nachbesserung).

**Maßstab:** DoD §2 des Slice; [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0034](../plan/adr/0034-testserver-als-gradle-modul.md); `SPEC-025`, `SPEC-040`, `SPEC-048`, `SPEC-059`; [TDS-FA-08](../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-RB-04](../../spec/lastenheft.md#tds-rb-04--externes-backend).

**Urteil: konform.** Die vier gesetzten DoD-Haken (Backend und Lauf, Stufe und Sicht, Laufzeit, `make gates`) sind durch eigene Läufe belegt; die sechs offenen Haken sind zu Recht offen (Closure-Arbeit). Kein Gate gelockert, keine ADR geändert, keine Haken vorab. 0 Abweichungen, 4 Anmerkungen (alle INFO).

---

## 1. Selbst gefahrene Sensoren (Belege des Implementers nicht übernommen)

Last des Hosts während der Läufe: 1-Minuten-Wert 7 bis 12 (geteilter Host, fremde Läufe parallel). Alle Läufe am unveränderten Zweig-HEAD 0cd8db72; `git status` nach den Läufen leer.

| Sensor | Ergebnis | Anmerkung |
|---|---|---|
| `make eigenes-backend` | Exit 0, 50 s (Bau 37 s, Tests 13 s) | 4 von 4 Tests; Anfragen je Tabelle eigen 6, leer 1, fehler 1, fehler_nach_zeilen 1, wirft 1 wie erwartet; Server läuft danach |
| `make e2e` | Exit 0, „gruen (8 Stufen, 311 s)“ | Stufen 1 bis 8 grün: 66, 89, 20, 45, 20, 19, 37, 15 s |
| `make e2e-abdeckung` | Exit 0, „unveraendert — docs/plan/e2e-abdeckung.md (8 Stufen, 8 Deklarationen, 0 ohne Kennung)“ | Datei nach 0cd8db72 byte-gleich zum Erzeuger |
| `make skript-selbsttest` | Exit 0, aber vollständig aus dem Layer-Cache (Cache-Falle G1) | deshalb zusätzlich `docker build --no-cache --target skript-selbsttest .`: Exit 0, „e2e-selbsttest: ok (Abbruch an Stufe 1, 2, 5, 7 und 8, alle gruen, Teil Nicht belegt in 8 Stufen)“, `manifest-selbsttest: ok`, `vergleichslauf-d-selbsttest: gruen` |
| `make docs-check` | Exit 0, „943 Datei(en) geprüft, 0 Befund(e)“ | |
| `make gates` | Exit 0 | die Gruppen `werkzeug`, `tests`, `stempel` wurden mit Stempelbeleg übersprungen (Ausgabe „uebersprungen, Beleg fuer …“); gelaufen sind `immer`-Gates (u. a. `docs-check`, `gate-zuordnung` 15 Gates / 33 Fälle, `stempel-eingaenge-pruefung`). Ein grünes `gates` ist hier kein Frischbeleg für Tests und detekt |
| `make clean-build` (Frischbeleg, `--no-cache`) | Exit 0; alle Gradle-Stages `BUILD SUCCESSFUL`; `EigenesBackendTest` beide Fälle PASSED | deckt Unit-Tests und detekt frisch ab |

Nicht von mir gefahren: `detekt-selbsttest`, `kopplung-zahlen`, `referenztest-dotnet`, `stempel-*`-Selbsttests als eigene Läufe (nur über Stempelbeleg von `make gates`).

## 2. Mutationen, selbst gefahren (je ein Lauf `make eigenes-backend`, danach `EigenesBackendServer.kt` zurückkopiert, Arbeitsbaum sauber)

| # | Mutation | Ergebnis |
|---|---|---|
| M1 | Tabelle `fehler` liefert `ResultStart` und `ResultEnd(0)` statt `Failure` | Exit 2; Test „Ein_Fehler_des_Backends_am_Start …“ Failed (3 von 4 bestanden); „Alle 4 Tests“ und „6 Anfragen an eigen“ FEHLER; ROT |
| M2 | `wirft` beendet den Prozess (`Runtime.getRuntime().halt(1)`) statt zu werfen | Exit 2; Tests „… wirft … Server bleibt verfuegbar“ und „gefuelltes und leeres Ergebnis“ Failed (2 von 4); „Server laeuft nach den Tests“ FEHLER; ROT |

Beide entsprechen den Angaben in Slice §7 (M1: 3 von 4, M2: 2 von 4). Damit ist die DoD-Aussage „Server bleibt für weitere Verbindungen verfügbar“ nicht nur grün gesehen, sondern in beide Richtungen rot gesehen.

## 3. DoD-Konformität (§2 des Slice)

| DoD-Punkt | Haken | Beleg | Urteil |
|---|---|---|---|
| Backend und Lauf | gesetzt | `EigenesBackend` implementiert `QueryBackend` allein (Imports nur `domain.*`, `port.*`, `runtime.BackendSource`, kotlinx), kein Simulations-Import, keine Delegation; `BackendSource.Custom(backend).takeIf { fromFile.isEmpty() }`; Modul `testserver` kompiliert gegen das Hauptmodul als Projekt-Abhängigkeit ([ADR-0034](../plan/adr/0034-testserver-als-gradle-modul.md), Source-Set-Grenze durch das Modul, nicht durch Assoziation). Client im zweiten Container erhält gefüllt (2 Zeilen) und leer (Spalten, 0 Zeilen). Gemessen: `fehler` am Start und `fehler_nach_zeilen` (3 Zeilen gelesen): `Number 50004, Class 16, State 1, 'Backend error.'`, Verbindung danach nutzbar; `wirft`: `Number 50007, Class 20, State 1, 'Internal error.'`, Zustand `Closed`, drei neue Verbindungen beantwortet; Prozess läuft. Target `make eigenes-backend` | belegt |
| Stufe und Sicht | gesetzt | `tools/e2e.sh` Stufe 8 mit `e2e_abdeckung "TDS-FA-08 TDS-RB-04" … Nicht belegt: …`, Lauf `lauf 8 make eigenes-backend`; `make e2e-abdeckung` unverändert (8 Deklarationen, 0 ohne Kennung); Zeile `make eigenes-backend` in `harness/README.md` §Sensors; Selbsttest deckt Rot in Stufe 8 (no-cache bestätigt) | belegt |
| Laufzeit | gesetzt | Slice §7: Einzelmessung mit Datum, Last, Cache-Zustand (Stufe 8: 13 s warm; Durchgang 366 s; Erstlauf 108 s). Eigene Gegenmessung (kein Mittelwert, andere Last): Stufe 8 15 s, Durchgang 311 s, Target allein 50 s mit Bau der Images aus warmem Cache | belegt (Größenordnung reproduziert) |
| `make gates` grün | gesetzt | siehe §1; für Tests/detekt gestützt durch `make clean-build` | belegt, mit der Einschränkung zum Stempel |
| Review, Doku-Update, Closure-Notiz, Register, Risiko-Ausgänge, drei Paarungen | offen | Review-Report liegt vor (`2026-11-06-4436821-…`, nach Nachbesserung 0cd8db72 kein Re-Review); §7 enthält noch „Wird bei der Closure gefüllt.“, §6-Risiken tragen „offen bis zur Closure“ | zu Recht offen, nicht vorab gesetzt |

## 4. Entscheidungs-Konformität

- **[ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md):** `make e2e` und `make eigenes-backend` bleiben außerhalb von `GATE_CHECKS` (`tools/mk/eigenes-backend.mk` ohne Gate-Eintrag); `gate-zuordnung` grün. Konform.
- **[ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md) / [ADR-0034](../plan/adr/0034-testserver-als-gradle-modul.md):** Server-`main` liegt im Modul `testserver` (neben, nicht in `KompatibilitaetsServer.kt`), `testserver/build.gradle.kts` ohne `associate`/`friend`; nur öffentliche Namen. Konform. Nicht gefahren: Mutation mit `internal`-Zugriff (vom Slice §7 (d) als Grenze benannt).
- **SPEC-040 / SPEC-059:** die Stufe nutzt `TdsServer.start` mit `BackendSource.Custom` (SPEC-059); das Simulations-Backend wird nicht berührt. `EigenesBackendTest.kt` trägt nach 0cd8db72 kein `@spec`-Tag (Review F-4 erledigt).
- **SPEC-048 / SPEC-025:** ein werfendes `execute` ergibt `TDS_INTERNAL_ERROR` (Severity 20, fester Text ohne Ausnahmetext, Verbindungsende) — gemessen 50007/Class 20/„Internal error.“, deckungsgleich. SPEC-025 „Stufen 3 bis 8“ mit Zeile im Änderungsprotokoll.
- **[TDS-FA-08](../../spec/lastenheft.md#tds-fa-08--backend-anbindung) / [TDS-RB-04](../../spec/lastenheft.md#tds-rb-04--externes-backend):** Happy Path, leeres Resultset, fehlerhaftes Backend belegt. „Nicht erreichbar“ ist als `Failure` am Start modelliert; ein beim Serverstart nicht erreichbares Backend steht ehrlich unter „Nicht belegt“.
- **Hard Rules:** Docker-only (alle Läufe über `make`), kein `@Suppress`, `gates` ohne Lockerung, keine ADR geändert; Aussagen in Deklaration, Skript und Testkopf tragen ihre Grenze (Log ohne Senke, `Closed` am Client; Review F-2/F-3 am Text behoben, geprüft in `tools/e2e.sh` und `tools/eigenes-backend-test.sh`).

## 5. Zweite Fundstellen (Grep über das Repo ohne `done/`, `docs/reviews/`, `docs/messungen/`, `observations/`, `.harness/`)

Gesucht: „sieben Stufen“, „7 Stufen“, „Stufen 3 bis 7“, „Stufen 1 bis 7“, „alle sieben“, „siebte Stufe“, „Stufe 7 als letzte“, „acht Stufen“, „8 Stufen“.

- Nachgezogen und richtig: `README.md` (acht), `harness/README.md` Zelle `make e2e` (8 Stufen; die Zeiten 103/339 s sind ausdrücklich „Stufen 1 bis 7“), `tools/mk/e2e.mk`, `tools/e2e.sh` („8 Stufen“), `tools/e2e-selbsttest.sh`, `spec/spezifikation.md` SPEC-025 („Stufen 3 bis 8“), `docs/plan/planning/welle-produktschnitt.md` („acht Stufen“), `done/slice-betrieb-log-adapter-testserver.md` (nachgezogen mit 0cd8db72; keine „sieben“-Fundstelle mehr).
- Bleibt mit Grund: `tools/mk/kompatibilitaet.mk` und `harness/README.md` Zelle `make kompatibilitaet` („Stufen 4 bis 7“ — richtig, Stufe 8 ist keine Kategorie); `spec/spezifikation.md` Änderungsprotokoll 2026-11-01 („lief mit den sieben Stufen grün“ — Zeitdokument, Stand damals); `open/slice-kompatibilitaetsmatrix-erzeugen.md` („7 Stufen“ meint die Herkunfts-Marker, kein E2E).
- Anmerkung V-1 (INFO): `docs/plan/planning/in-progress/roadmap.md` Zeile 29 nennt „Warm-/Kaltmessung der sieben Stufen“ als offenen Punkt aus `slice-e2e-geruest` §7. Das ist der Stand jenes Slice und der Name der Beobachtung; ein Nachzug ist nicht geboten, wird aber relevant, sobald die Warm-/Kaltmessung gefahren wird (dann sind es acht Stufen). Kein Befund.
- Keine Fundstelle in `docs/user/`, `.github/workflows/`.

## 6. Anmerkungen (alle INFO, keine Rückgabe an den Implementer nötig)

- **V-1:** siehe §5, Roadmap-Zeile.
- **V-2 (Cache-Falle G1):** `make skript-selbsttest` lief aus dem Layer-Cache; `make gates` übersprang drei Gruppen per Stempel. Der Frischbeleg stammt aus `docker build --no-cache --target skript-selbsttest` und `make clean-build`. Wer die Closure schreibt, soll „gates grün“ nicht auf den Stempellauf allein stützen.
- **V-3:** Der Review-Report (F-7) schreibt, die Folge 50007/Class 20 bei werfendem `execute` sei „nirgends als Zusage festgelegt“. `SPEC-048` legt sie fest (unerwartete Ausnahme im Backend-Adapter, Severity 20, fester Text, Verbindungsende). Die Stufe belegt damit eine Zusage der Spezifikation, nicht nur ein gemessenes Verhalten; am Befund des Reviews ändert das nichts (INFO), die Closure-Notiz kann `SPEC-048` als Beleg nennen.
- **V-4 (Messung):** Die Laufzeitzahlen im Slice §7 (Durchgang 366 s) und meine (311 s) sind zwei Einzelmessungen unter unterschiedlicher, hoher Last; sie sind nicht vergleichbar und nicht gemittelt. Keine Abweichung der DoD („gemessen“), nur ein Hinweis, dass die Größenordnung (Stufe 8 13 bis 15 s warm) reproduziert wurde.

## 7. Plan-vs-Code-Diff

Der Diff 6b28cd69..0cd8db72 (19 Dateien, +531/−19) deckt die Tabelle §3 des Slice vollständig ab: Server und Fixture (`EigenesBackendServer.kt`), Stufe (`tools/e2e.sh`), Target (`tools/mk/eigenes-backend.mk`), Sicht (`docs/plan/e2e-abdeckung.md`), Stage/Task (`Dockerfile`, `referenztest-dotnet/Dockerfile`, `testserver/build.gradle.kts`), Tests (`EigenesBackendTests.cs`, `EigenesBackendTest.kt`, `tools/eigenes-backend-test.sh`), Selbsttest, Zellen (`README.md`, `harness/README.md`, `e2e.mk`), SPEC-025 und zweite Fundstellen. Nach der Nachbesserung nennt die Tabelle auch die zuvor ungeplanten Dateien und der Kopf „Berührte Spec-Stellen“ `SPEC-025` (Review F-5). Keine Datei im Diff, die der Plan nicht trägt; keine Plan-Zeile ohne Diff. Die Abgrenzung §1 (keine Delegation an die Simulation, keine Wiederholung von Stufe 4) ist im Code eingehalten.

## 8. Übergabe an den Planner

Verifikation bestanden für die gelieferten Punkte. Für die Closure bleiben: Closure-Notiz mit Steering-Loop-Lerneintrag (§7 noch „Wird bei der Closure gefüllt.“), Ausgänge der drei Risiken in §6, Register-Vermerk (berührt: `BEO-TDS/test-belegt-nur-den-fake`, `BEO-TDS/aussage-ueber-schichtgrenze-ohne-test`), Haken „Review durchgeführt“ (Report liegt vor; Re-Review der Nachbesserung 0cd8db72 ist nicht erfolgt, die Befunde F-1 bis F-6 sind durch diese Verifikation am Text geprüft, nicht durch einen zweiten Review), Haken „Doku-Update“.
