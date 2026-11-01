# Slice slice-explicit-api-umbau: Öffentliche Fläche mit explicitApi festlegen und verkleinern

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-produktschnitt (zugeordnet am 2026-11-01, Nutzerentscheidung; vorher ohne Welle)

**Bezug:** [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend), [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) (Entscheidung 3), [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) (`Accepted`, ersetzt Entscheidung 3 teilweise: `SimulationBackend` und `SimCatalog` öffentlich, `SimCatalog.find` `internal`), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) (`Accepted`: `ConfigKeys`, `simulationFile` und Jackson verlassen die Bibliothek), [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) (`Accepted`: Modell, Senke und Parameter von `start` kommen zur Fläche, sobald sie gebaut sind).

**Berührte Spec-Stellen:** SPEC-059 (öffentliche Fläche des Einstiegs).

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-10-31 (fortgeschrieben 2026-11-01).

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Der Compiler-Schalter `explicitApi` (Modus `strict`) ist eingeschaltet, und öffentlich ist nur die Fläche, die [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) Entscheidung 3 in der Fassung von [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) nennt (mit `SimulationBackend` und `SimCatalog`, ohne `ConfigKeys`); alles andere ist `internal`, soweit Aufrufer im Hauptmodul es zulassen. Die Source-Sets `integrator` und `testserver` übersetzen danach gegen diese Fläche; ihre Tests, die `ConfigKeys`, `SimTable.column` oder die Typen des Authentifizierungsadapters nannten, ändern sich (§3), ihr Produktionscode nicht.

**Zuordnung zur Welle und Reihenfolge (Nutzerentscheidung 2026-11-01):** Der Slice gehört zu [welle-produktschnitt](../welle-produktschnitt.md) und startet erst, wenn `slice-testserver-herausloesen` in `done/` liegt. Der Grund ist der Umfang: Lader, YAML-Leser, Beschreibungs-Leser und Jackson liegen danach nicht mehr in `src/main`, die Menge der zu schließenden Deklarationen ist eine andere, und der Umbau würde sonst Dateien schließen, die der Testserver-Umbau verschiebt.

**Befund, der den Slice begründet:** Die ADR misst im Review (Kopie des Baums, `explicitApi()` strict, Stage `compile`) 738 Diagnosen (645 „Visibility must be specified“, 93 „Return type must be specified“) in 76 von 136 Dateien unter `src/main` und nennt den Umbau zu groß für einen Arbeitsschritt neben Fassade, Source-Set und `main()`. `slice-integrations-einstieg` liefert deshalb den Einstieg und seinen Nachweis ohne diesen Umbau.

**Die Zahl 738 ist überholt und wird neu gemessen.** Seit der Messung haben drei angenommene ADRs die Menge der Namen geändert: [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) nimmt `simulationFile`, `ConfigKeys`, `ConfigLoader`, `ConfigFiles`, `YamlTreeReader`, `SimDescriptionReader`, die `SimTree`-Typen und Jackson aus `src/main` (die Dateien und mit ihnen ein Teil der 76 betroffenen Dateien wandern in den Testserver); [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) nimmt `SimulationBackend` und `SimCatalog` in die Liste auf und macht `SimCatalog.find` `internal`; [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) nimmt Ereignismodell, Senke, den Parameter von `start`, ein Feld der `ServerConfig` (Abstand der Schnappschüsse) und einen Sammler hinzu und löst `ServerLog` samt `LogEntry` und die Rückrufe des Listeners ab. Gemessen wird am Stand nach `slice-testserver-herausloesen` (Kopie des Baums, `explicitApi()` strict, Stage `compile`, wie in der [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md)); je nachdem, welche Slices der Welle [welle-betrieb-diagnose](../welle-betrieb-diagnose.md) bis dahin in `done/` liegen, trägt die Fläche Senke und Modell schon oder sie entstehen später mit ausdrücklicher Sichtbarkeit (der Compiler erzwingt sie dann beim Anlegen). Die Reihenfolge zu diesen Slices ist keine Kante.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Ein Prüfwerkzeug für Binärkompatibilität — [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) nimmt es nicht mit (Option c2, akzeptiertes Negativ).
- Änderungen an Verhalten oder Signaturen der Fläche — der Slice ändert nur Sichtbarkeit und ausdrückliche Typen.
- Die Namen und die Form der Senke und des Ereignismodells — sie legt der Bau von [slice-betrieb-log-schnittstelle](../in-progress/slice-betrieb-log-schnittstelle.md) fest ([ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) Offen (g)); dieser Slice schließt, was dann dasteht, und erfindet keinen Namen.
- Das Herauslösen des Testservers und das Entfernen von `simulationFile`, `ConfigKeys`-Lader und Jackson aus `src/main` — `slice-testserver-herausloesen`, Voraussetzung dieses Slice.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (zwei):

- [x] **`explicitApi` strict eingeschaltet:** die Zahl der Diagnosen am Stand nach `slice-testserver-herausloesen` ist gemessen und steht mit dem Vergleich zu den 738 der [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) in der Closure-Notiz; `make build` übersetzt `src/main` mit `explicitApi()` ohne Diagnose; `make detekt` (ktlint, `warningsAsErrors`) ist grün.
- [x] **Fläche verkleinert und belegt:** Deklarationen außerhalb der Liste der ADR (Entscheidung 3 in der Fassung von [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md), dazu die Namen aus [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md), soweit gebaut) sind `internal`, darunter `SimCatalog.find` (Setzung der [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md), belegt durch den Übersetzungslauf); die Source-Sets `integrator` **und** `testserver` übersetzen und `make test` ist grün; die Liste der öffentlichen Typen steht im KDoc des Einstiegs oder in `SPEC-059`. Die Mutation „Testserver ruft `SimCatalog.find`“ macht die Übersetzung des Source-Sets rot.

Nachweis-Pflichten für alle Punkte:

- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update: `SPEC-059` nennt die endgültige Liste der öffentlichen Typen (einschließlich `SimulationBackend` und `SimCatalog`, ohne `ConfigKeys`); die Suche nach den abgelösten Namen (`ConfigKeys`, `simulationFile`) über `spec/` findet keine zweite Fundstelle (`BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`); `SHUTDOWN_GRACE` und `QUERY_TIMEOUT` sind als Eingang ausgezeichnet oder nicht mehr „konfigurierbar“ genannt (§6).
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `build.gradle.kts` | update | `kotlin { explicitApi() }` |
| `src/main/kotlin/` (76 Dateien laut ADR-Messung) | refactor | ausdrückliches `public` oder `internal` und Rückgabetypen, Sichtbarkeit nach der Liste der ADR |
| `src/test/kotlin/` (Hilfsdateien) | update | Hilfsdeklarationen, die interne Typen der Bibliothek zeigen, werden `internal` (die Tests liegen im Hauptmodul und sehen `internal`) |
| `src/integrator/kotlin/` (`EmbeddingTest.kt`) | update | nennt `ConfigKeys` nicht mehr; die Schlüssel eines `ConfigIssue` sind Text ohne Zusage (`SPEC-059`) |
| `src/testserver/test/kotlin/` (`ConfigLoaderTest`, `ConfigUsersFileTest`, `SimDescriptionReaderTest`, neu `LoginProbe`) | update | kein Zugriff auf `ConfigKeys`, `SimTable.column` und die Typen des Authentifizierungsadapters; die Benutzerliste wird über eine Anmeldung am laufenden Server belegt |
| `PasswordChars.reveal`, `UserList.plainTestModeFrom` (in `src/main`) | bleiben öffentlich | Setzung des Implementers: `plainTestModeFrom` ruft der Lader des Testservers (Produktionscode, `ConfigLoader`); `reveal` ruft ein Test des Testservers (`ConfigUsersFileTest`), der das gelesene TLS-Passwort vergleicht. `PasswordChars.wipe` und `length` sind `internal`, kein Aufrufer außerhalb des Hauptmoduls |
| `spec/spezifikation.md` (`SPEC-059`, `SPEC-047`, `SPEC-034`, `SPEC-009`) | update | endgültige Liste der öffentlichen Namen, `SHUTDOWN_GRACE` als Zielzustand, Eingang von `QUERY_TIMEOUT` |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`), alle Bedingungen beobachtbar:

1. `slice-integrations-einstieg` liegt in `done/` (erfüllt: das Source-Set `integrator` belegt, dass die verkleinerte Fläche reicht).
2. `slice-testserver-herausloesen` liegt in `done/` (Nutzerentscheidung 2026-11-01; Grund in §1: kein doppelter Umbau).
3. Der Start-Trigger von [welle-produktschnitt](../welle-produktschnitt.md) ist eingetreten.
4. Ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): die Umstellung lässt sich nicht in einem Zug übersetzen und gaten; Zerlegung je Package.
- `in-progress` → `open` (blockiert — Carveout?): `explicitApi` ist mit `make detekt` unverträglich, ohne dass eine Regel gelockert werden dürfte (`AGENTS.md` §3.6); oder `slice-testserver-herausloesen` fällt nach `open/` zurück; oder der Testserver braucht einen Typ, der nicht in der Liste steht (Befund und Folge-ADR nach [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md), nicht `internal` öffnen).

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün, der Review-Report liegt vor, und die Closure-Notiz trägt den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Lesehilfe-Lücke der Spezifikation (Befund der Verifikation von `slice-testserver-herausloesen`): `SHUTDOWN_GRACE` (`SPEC-047`) und `QUERY_TIMEOUT` (`SPEC-009`) stehen als „konfigurierbar“ in der Festlegungstabelle, sind über `TdsServer.start` aber nicht einstellbar und nicht als Eingang ausgezeichnet wie `LISTEN_*`, `LOGIN_TIMEOUT` und `MAX_PRELOGIN_MESSAGE_BYTES`; die endgültige öffentliche Fläche entscheidet, ob die Auszeichnung fehlt oder die Konfiguration, und der Spec-Nachzug dieses Slice schließt die Lücke (`BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`). — **Ausgang:** eingetreten und behoben (`SPEC-047` und `SPEC-034` tragen „Zielzustand, nicht gebaut“, `SPEC-009` nennt `ServerConfig.requests` als Eingang; belegt gegen den Quelltext in der Verifikation).
- Eine Deklaration, die ein Test im Hauptmodul braucht, kann nicht `internal` werden, ohne den Test zu ändern; die Tests liegen im selben Modul und sehen `internal`. — **Ausgang:** nicht eingetreten (Hilfsdeklarationen in `src/test` sind `internal`, kein Verhaltenswechsel; Normalisierungsvergleich des Verifiers).
- Die Liste der ADR ist die Absicht, nicht ein Bestand; die endgültige Liste legt der Übersetzungslauf fest. — **Ausgang:** bestätigt (die Liste in `SPEC-059` deckt sich mit dem Übersetzungslauf: 35 Top-Level-Typen, 24 verschachtelte, 2 Companions).
- Die Senke und das Ereignismodell von [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) kommen im Lauf der Welle [welle-betrieb-diagnose](../welle-betrieb-diagnose.md) hinzu und können vor oder nach diesem Slice entstehen; entstehen sie davor, tragen sie die ausdrückliche Sichtbarkeit selbst, entstehen sie danach, schließt dieser Slice sie nicht nach. — **Ausgang:** eingetreten, Fall „danach“: die Namen der Senke und des Modells sind nicht gebaut; dieser Slice schließt nichts nach, sie tragen ihre ausdrückliche Sichtbarkeit selbst.
- Der Kommentar von `ServerConfig` („entsteht nur über `ConfigLoader.load`“, [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) Kontext) ist nach dem Testserver-Umbau neu zu lesen; dieser Slice korrigiert ihn nur, soweit die Sichtbarkeit ihn betrifft. — **Ausgang:** nicht inhaltlich geprüft; die Sichtbarkeit betrifft den Kommentar nicht, der Wortlaut blieb unverändert. Der Satz „entsteht nur über `ConfigLoader.load`“ ist nach dem Testserver-Umbau weiter neu zu lesen, Träger: [slice-betrieb-log-schnittstelle](../in-progress/slice-betrieb-log-schnittstelle.md), der `ServerConfig` ohnehin anfasst.
- Kein Sensor hält die Menge der öffentlichen Klassen gegen die Liste in `SPEC-059`: `explicitApi` beweist nur eine ausdrückliche Sichtbarkeit; eine versehentlich `public` gemachte Klasse oder eine Listen-Klasse, die `internal` wird, lässt kein Gate rot werden (Befund des Reviews, MEDIUM-1). Entscheidung des Nutzers offen: ein Sensor (Test oder Skript, das die öffentlichen Klassen gegen die Liste vergleicht; kein Werkzeug für Binärkompatibilität im Sinne von [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) Option c2) oder die Grenze bleibt benannt. Nicht gebaut. — **Ausgang:** weiter offen, Nutzerentscheidung; Träger: [slice-release-doku-prozess](../open/slice-release-doku-prozess.md) §6 (Verweis dort eingetragen), Nachweis der Lücke: Mutation A des Verifiers.
- `Principal` steht in der Liste der [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) Entscheidung 3, ist aber nur über den nicht öffentlichen Authentifizierungsadapter erreichbar (Befund des Reviews, MEDIUM-2); der Slice ändert die Liste nicht, `SPEC-059` vermerkt es. Entscheidung beim Nutzer und Architect (Typ aus der Fläche nehmen oder einen Weg über den Einstieg schaffen). — **Ausgang:** weiter offen, Entscheidung bei Nutzer und Architect; Träger: [slice-release-doku-prozess](../open/slice-release-doku-prozess.md) §6, Vermerk in `SPEC-059`.
- `build.gradle.kts` wird auch von `slice-jar-ohne-main-und-image` berührt (Manifest); die beiden Slices ändern verschiedene Blöcke, ein Rebase kann dennoch Konflikte zeigen. — **Ausgang:** nicht eingetreten (kein Rebase-Konflikt gemessen); der Merge der Parallel-Branches (`ConfigLoaderTest.kt`, `EnvKeys.kt`) ist nicht auf semantische Konflikte geprüft.

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

**Umfang und was nicht unabhängig verifiziert ist.** Verifiziert hat der Verifier ([Bericht](../../../reviews/2026-11-01-bb0905c-verifikation-slice-explicit-api-umbau.md)) den Stand `bb0905c` (nach der Fix-Runde zum [Review](../../../reviews/2026-11-01-cd2dcf7-slice-explicit-api-umbau.md)): closure-fähig mit Bedingungen, die Closure-Pflichten sind, kein Code-Mangel; kein HIGH, kein MEDIUM an Code oder Spec. Nach `bb0905c` ändert sich am Code nichts.

**Zuordnung der Aussagen:**

- *Gemessen:* (1) Diagnosen von `explicitApi()` strict am Stand vor dem Umbau (`4166ef4`): **763 = 670 „Visibility must be specified“ + 93 „Return type must be specified“ in 78 von 138 Dateien** unter `src/main`; die [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) maß 738 = 645 + 93 in 76 von 136. Der Unterschied folgt aus den Änderungen seit der Messung (zwei Dateien mehr, 25 Sichtbarkeits-Diagnosen mehr), die 93 Rückgabetypen sind gleich. (2) Öffentliche Fläche, vom Verifier selbst aus allen `public` in `src/main` ermittelt: 35 Top-Level-Typen (nach Package exakt die Namensliste von `SPEC-059`) + 24 verschachtelte + 2 `public companion object` = **61 öffentliche Klassen**; der Wert „349 auf 61“ stammt aus dem Dump des Implementers und wurde vom Verifier **nicht nachgemessen**. (3) `make clean-build` (`--no-cache`): 881 PASSED (811 `:test`, 4 `:integratorTest`, 66 `:testserverTest`), 0 FAILED, alle fünf detekt-Tasks grün, `compileIntegratorKotlin` und `compileTestserverKotlin` grün. (4) `make docs-check`: 0 Befunde; `make matrix-zitate`: 881 bestandene Testfälle, 0 Zitate ohne PASSED. (5) Mutationen des Verifiers: Klasse ohne Modifikator rot; Testserver ruft `SimCatalog.find` rot; Listen-Klasse `ConfigValidation` oder Listen-Member `PasswordChars.reveal` `internal` rot; `LoginProbe.login` immer `true` rot. (6) Normalisierung (ohne `public`, `internal`, Umbrüche, Trailing-Commas) aller 108 geänderten `src/`-Dateien gegen `4166ef4`: 103 identisch (alle `src/main`, die Hilfsdateien in `src/test`), 4 weichen im Plan §3 gelistet ab, 1 neu (`LoginProbe`).
- *Gate-Läufe, Einordnung:* `make gates` 10,7 s ist ein **reiner Cache-Beleg** (alle Stages `CACHED`, Tests nicht neu gelaufen); der Frischbeleg ist `make clean-build` (7 min 21 s bei hoher Host-Last, Einzelmessung). **`make e2e` wurde nicht gefahren.** Grund des Verifiers: der Diff ändert nur Sichtbarkeit, Umbrüche und Tests; Produktionscode des Testservers, `Dockerfile`, `Makefile`, `tools/` und `config/` sind gegenüber `4166ef4` unverändert, `src/main` und `src/testserver/kotlin` enthalten keine Reflexion, keinen `ServiceLoader`, kein `Class.forName`, und der Rohbytes-Smoke lief in `make test` grün. Restrisiko: kein E2E-Lauf bestätigt den Bytecode dieses Stands.
- *Handvektor:* keiner.
- *Selbstkonsistenz:* `SPEC-059` (Liste) gegen den Übersetzungslauf (35/24/2); die Marker „Zielzustand, nicht gebaut“ in `SPEC-047` (`Bootstrap.kt:114`, `ListenerConfig.kt:27`) und `SPEC-034` (Name kommt in `src/main` nicht vor) gegen den Quelltext; die Suche nach `ConfigKeys` und `simulationFile` in `spec/` findet drei Stellen, alle sagen, dass der Name nicht zur Fläche gehört (`simulationFile`: keine).
- *Festlegung / Setzung, nicht gemessen:* `PasswordChars.reveal` und `UserList.plainTestModeFrom` bleiben öffentlich (Aufrufer: Lader des Testservers, `ConfigUsersFileTest`; Mutation H belegt den Bedarf von `reveal`); `PasswordChars.wipe` und `length` sind `internal`. `SHUTDOWN_GRACE` als Zielzustand, `MAX_CONNECTIONS` als Zielzustand.
- *Abweichungen vom Plan:* (a) §1 „Tests übersetzen unverändert“ war nicht haltbar: vier Testdateien des Testservers und des Source-Sets `integrator` wurden geändert, weil sie `internal` Typen nutzten (Plan §3 ergänzt). (b) Die Benutzerliste wird über `LoginProbe` (Anmeldung am laufenden Server) belegt; das ist ein Oracle über den Server statt über den Adapter. (c) `reveal` und `plainTestModeFrom` bleiben öffentlich, obwohl die Liste der ADR sie nicht ausdrücklich nennt. (d) Die Schlüsselprüfung im `EmbeddingTest` ist schwächer als zuvor (nur `kind` und Verschiedenheit der Schlüssel; der Schlüsseltext ist ohne Zusage, `SPEC-059`), `ConfigLoaderTest` pinnt die Namen über einen Verhaltenstest.
- *Nicht gemessen und nicht zugesagt:* **Kein Sensor hält die Menge der öffentlichen Klassen gegen die Liste** (`explicitApi` beweist nur eine ausdrückliche Sichtbarkeit; Mutation A: `SimCatalog.find` `public`, `make build` grün; `SPEC-059` sagt es). `Principal` steht in der Liste, ist aber nur über den nicht öffentlichen Authentifizierungsadapter erreichbar. Die Zahl 349 auf 61 (siehe oben). Ein semantischer Konflikt beim Merge der Parallel-Branches (Stempel-Migration fasst `ConfigLoaderTest.kt` und `EnvKeys.kt` an).

**Offene Nutzerpunkte (nichts entschieden):** (1) Sensor für die Menge der öffentlichen Klassen, ja oder die Grenze bleibt benannt; (2) `Principal`: Typ aus der Fläche nehmen oder einen Weg über den Einstieg schaffen. Beide stehen als offene Punkte in [slice-release-doku-prozess](../open/slice-release-doku-prozess.md) §6.

**Befunde der Verifikation ohne Code-Änderung:** LOW-A: der Kommentar der Stage `runtime` im `Dockerfile` (Z. 217) nennt „(ConfigKeys)“; steht in [slice-jar-ohne-main-und-image](../done/slice-jar-ohne-main-und-image.md) §6 (Eintrag 6, mit Verweis auf den Review), dort beim Umschreiben der Stage zu erledigen. LOW-B: tote Konstanten in `internal object ConfigKeys` (`ENCRYPTION_POLICY`, `AUTH_USERS_FILE`, `AUTH_PLAIN_TESTMODE` ohne Aufrufer in `src/main`; der Test, der sie gegen `EnvKeys` pinnte, entfiel im Umbau): als Aufgabe in [slice-betrieb-log-adapter-testserver](../open/slice-betrieb-log-adapter-testserver.md) §6 eingetragen, weil dieser Slice den Testserver-Lader anfasst.

**Closure (Planner, 2026-11-01):**

- **Was hat funktioniert:** Der Compiler erzwingt die Richtung „zu viel intern“: Fünf von sechs Mutationen des Verifiers wurden rot, die Listen-Klassen und -Member werden von Testserver und Tests wirklich gebraucht. Die Suche nach den abgelösten Namen fand die Dockerfile-Fundstelle schon im Review.
- **Was ging anders als geplant:** Plan §1 sagte „übersetzen unverändert“; vier Testdateien mussten angepasst werden. Die DoD-Suche nach `ConfigKeys` lief nur über `spec/` und ließ den Dockerfile-Kommentar stehen. Die erste Fassung von `SPEC-059` sagte mehr, als der Compiler belegt (MEDIUM-1 des Reviews).
- **Steering-Loop-Eintrag (Lerneintrag):** „Der Compiler erzwingt sie“ trägt eine Zusage nur so weit, wie der Compiler sie erzwingt: `explicitApi` beweist eine ausdrückliche Sichtbarkeit, nicht, dass die Menge der öffentlichen Namen die Liste ist; die Zusage über die Liste nennt den Beleg und seine Grenze (die Mutation „Nicht-Listen-Member `public`“ bleibt grün) oder bekommt einen Sensor. Gezählt, nicht verkörpert (kein `liegt in`): Fall von `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert) und `BEO-TDS/zusage-ohne-sensor-in-der-ci-kette`; Entscheidung über Verkörperung beim Nutzer beziehungsweise bei der Welle-Closure.
- **Über der Schwelle, nicht verkörpert, Entscheidung beim Nutzer:** `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` hat mit diesem Slice sieben Evidenz-Dateien (neu: Dockerfile-Kommentar, zweite Fundstelle außerhalb von `spec/`); der Zähler in `state.md` ist nicht gesetzt. Hier wird nichts verkörpert.
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-explicit-api-umbau.md` in `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (Dockerfile), `BEO-TDS/aussagen-ueber-messung-hinaus` (erste Fassung von `SPEC-059` breiter als der Beleg) und `BEO-TDS/zusage-ohne-sensor-in-der-ci-kette` (Menge der öffentlichen Klassen). `BEO-TDS/zahl-an-mehreren-orten-ohne-sensor`: nicht einschlägig (die 763 stehen an einem Ort). Keine neue Beobachtung angelegt.
- **Folge-Slices:** keine neuen; LOW-A, LOW-B und die zwei Nutzerpunkte stehen in den genannten Zielslices.
- **Risiken aus §6:** acht Risiken: eines eingetreten und behoben, eines eingetreten (Fall „danach“), drei nicht eingetreten oder bestätigt, ein Nichtprüfen mit Träger, zwei weiter offen mit Träger (Sensor, `Principal`).
- **Gate-Läufe:** `make spec-abdeckung` am Stand der Closure: 1 m 31 s (Einzelmessung), `docs/plan/spec-abdeckung.md` unverändert (12 Festlegungen ohne Nachweis); `make docs-check` und `make gates` am Ende der Closure: grün (Einzelmessung, Zeit im Bericht des Planners).
- **Drei Paarungen:** Anker: kein Eintrag mit `liegt in`, nichts zu paaren. Folge-Slice: die Folgepflichten stehen in den Zielslices. Register: die Dateien liegen vor.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung — dort die **zwei vorgelagerten
Schritte** (sie stehen in jedem Slice-Plan, unabhängig von Modus und
Slice-Typ) und die **vier Pflichtkriterien** (Konventionen-Dichte ·
Phase-Reife · Evidenz-/Diskrepanz-Risiko · Reconciliation-Aufwand), vier und
nicht mehr.

**Der Abschnitt selbst entfällt nie.** Die zwei vorgelagerten Prüfungen laufen
in **jedem** Slice-Plan — sie hängen weder am Modus noch am Slice-Typ. Bedingt
ist allein der Modus-Begründungsblock am Ende; deshalb nennt der Titel beide
Hälften.

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration kennt nur `*` (Greenfield, Kürzel `TDS`); der Umbau berührt alle Packages unter `src/main`, ohne eine eigene Strukturregel oder Inventur-Linie. Keine feinere Sub-Area.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`): `BEO-TDS/oeffentlicher-einstieg-ohne-traeger` (gestrichen, der Einstieg ist öffentlich und belegt) ist der Anlass der Vorgänger-Arbeit. Nachgesehen am 2026-11-01 (Zuordnung zur Welle, nach [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md)): `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (3×, offen; die Suche nach den abgelösten Namen steht in der Doku-Pflicht), `BEO-TDS/nachweis-von-der-falschen-seite-der-grenze` (offen; die Mutation zu `SimCatalog.find` belegt die Fläche von der Seite des Testservers), `BEO-TDS/test-naht-im-produktivcode` (offen; Tests im Hauptmodul sehen `internal`, siehe Risiko 1), `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` (10×, offen; die Reihenfolge „nach dem Testserver-Umbau“ ist Nutzerentscheidung, die Lesart der Folgen für die Namensmenge ist Lesart des Planners). Sonst keine Treffer.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
