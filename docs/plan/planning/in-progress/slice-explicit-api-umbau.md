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

**Ziel:** Der Compiler-Schalter `explicitApi` (Modus `strict`) ist eingeschaltet, und öffentlich ist nur die Fläche, die [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) Entscheidung 3 in der Fassung von [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) nennt (mit `SimulationBackend` und `SimCatalog`, ohne `ConfigKeys`); alles andere ist `internal`, soweit Aufrufer im Hauptmodul es zulassen. Die Source-Sets `integrator` und `testserver` übersetzen danach unverändert.

**Zuordnung zur Welle und Reihenfolge (Nutzerentscheidung 2026-11-01):** Der Slice gehört zu [welle-produktschnitt](../welle-produktschnitt.md) und startet erst, wenn `slice-testserver-herausloesen` in `done/` liegt. Der Grund ist der Umfang: Lader, YAML-Leser, Beschreibungs-Leser und Jackson liegen danach nicht mehr in `src/main`, die Menge der zu schließenden Deklarationen ist eine andere, und der Umbau würde sonst Dateien schließen, die der Testserver-Umbau verschiebt.

**Befund, der den Slice begründet:** Die ADR misst im Review (Kopie des Baums, `explicitApi()` strict, Stage `compile`) 738 Diagnosen (645 „Visibility must be specified“, 93 „Return type must be specified“) in 76 von 136 Dateien unter `src/main` und nennt den Umbau zu groß für einen Arbeitsschritt neben Fassade, Source-Set und `main()`. `slice-integrations-einstieg` liefert deshalb den Einstieg und seinen Nachweis ohne diesen Umbau.

**Die Zahl 738 ist überholt und wird neu gemessen.** Seit der Messung haben drei angenommene ADRs die Menge der Namen geändert: [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) nimmt `simulationFile`, `ConfigKeys`, `ConfigLoader`, `ConfigFiles`, `YamlTreeReader`, `SimDescriptionReader`, die `SimTree`-Typen und Jackson aus `src/main` (die Dateien und mit ihnen ein Teil der 76 betroffenen Dateien wandern in den Testserver); [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) nimmt `SimulationBackend` und `SimCatalog` in die Liste auf und macht `SimCatalog.find` `internal`; [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) nimmt Ereignismodell, Senke, den Parameter von `start`, ein Feld der `ServerConfig` (Abstand der Schnappschüsse) und einen Sammler hinzu und löst `ServerLog` samt `LogEntry` und die Rückrufe des Listeners ab. Gemessen wird am Stand nach `slice-testserver-herausloesen` (Kopie des Baums, `explicitApi()` strict, Stage `compile`, wie in der [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md)); je nachdem, welche Slices der Welle [welle-betrieb-diagnose](../welle-betrieb-diagnose.md) bis dahin in `done/` liegen, trägt die Fläche Senke und Modell schon oder sie entstehen später mit ausdrücklicher Sichtbarkeit (der Compiler erzwingt sie dann beim Anlegen). Die Reihenfolge zu diesen Slices ist keine Kante.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Ein Prüfwerkzeug für Binärkompatibilität — [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) nimmt es nicht mit (Option c2, akzeptiertes Negativ).
- Änderungen an Verhalten oder Signaturen der Fläche — der Slice ändert nur Sichtbarkeit und ausdrückliche Typen.
- Die Namen und die Form der Senke und des Ereignismodells — sie legt der Bau von [slice-betrieb-log-schnittstelle](../open/slice-betrieb-log-schnittstelle.md) fest ([ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) Offen (g)); dieser Slice schließt, was dann dasteht, und erfindet keinen Namen.
- Das Herauslösen des Testservers und das Entfernen von `simulationFile`, `ConfigKeys`-Lader und Jackson aus `src/main` — `slice-testserver-herausloesen`, Voraussetzung dieses Slice.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (zwei):

- [ ] **`explicitApi` strict eingeschaltet:** die Zahl der Diagnosen am Stand nach `slice-testserver-herausloesen` ist gemessen und steht mit dem Vergleich zu den 738 der [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) in der Closure-Notiz; `make build` übersetzt `src/main` mit `explicitApi()` ohne Diagnose; `make detekt` (ktlint, `warningsAsErrors`) ist grün.
- [ ] **Fläche verkleinert und belegt:** Deklarationen außerhalb der Liste der ADR (Entscheidung 3 in der Fassung von [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md), dazu die Namen aus [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md), soweit gebaut) sind `internal`, darunter `SimCatalog.find` (Setzung der [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md), belegt durch den Übersetzungslauf); die Source-Sets `integrator` **und** `testserver` übersetzen und `make test` ist grün; die Liste der öffentlichen Typen steht im KDoc des Einstiegs oder in `SPEC-059`. Die Mutation „Testserver ruft `SimCatalog.find`“ macht die Übersetzung des Source-Sets rot.

Nachweis-Pflichten für alle Punkte:

- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update: `SPEC-059` nennt die endgültige Liste der öffentlichen Typen (einschließlich `SimulationBackend` und `SimCatalog`, ohne `ConfigKeys`); die Suche nach den abgelösten Namen (`ConfigKeys`, `simulationFile`) über `spec/` findet keine zweite Fundstelle (`BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`); `SHUTDOWN_GRACE` und `QUERY_TIMEOUT` sind als Eingang ausgezeichnet oder nicht mehr „konfigurierbar“ genannt (§6).
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

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
| `spec/spezifikation.md` (`SPEC-059`, `SPEC-047`, `SPEC-009`) | update | endgültige Liste der öffentlichen Namen, `SHUTDOWN_GRACE` als Zielzustand, Eingang von `QUERY_TIMEOUT` |

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

- Lesehilfe-Lücke der Spezifikation (Befund der Verifikation von `slice-testserver-herausloesen`): `SHUTDOWN_GRACE` (`SPEC-047`) und `QUERY_TIMEOUT` (`SPEC-009`) stehen als „konfigurierbar“ in der Festlegungstabelle, sind über `TdsServer.start` aber nicht einstellbar und nicht als Eingang ausgezeichnet wie `LISTEN_*`, `LOGIN_TIMEOUT` und `MAX_PRELOGIN_MESSAGE_BYTES`; die endgültige öffentliche Fläche entscheidet, ob die Auszeichnung fehlt oder die Konfiguration, und der Spec-Nachzug dieses Slice schließt die Lücke (`BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`). — **Ausgang:** offen bis zur Closure.
- Eine Deklaration, die ein Test im Hauptmodul braucht, kann nicht `internal` werden, ohne den Test zu ändern; die Tests liegen im selben Modul und sehen `internal`. — **Ausgang:** offen bis zur Closure.
- Die Liste der ADR ist die Absicht, nicht ein Bestand; die endgültige Liste legt der Übersetzungslauf fest. — **Ausgang:** offen bis zur Closure.
- Die Senke und das Ereignismodell von [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) kommen im Lauf der Welle [welle-betrieb-diagnose](../welle-betrieb-diagnose.md) hinzu und können vor oder nach diesem Slice entstehen; entstehen sie davor, tragen sie die ausdrückliche Sichtbarkeit selbst, entstehen sie danach, schließt dieser Slice sie nicht nach. — **Ausgang:** offen bis zur Closure (die Closure-Notiz nennt, welcher Fall eingetreten ist).
- Der Kommentar von `ServerConfig` („entsteht nur über `ConfigLoader.load`“, [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) Kontext) ist nach dem Testserver-Umbau neu zu lesen; dieser Slice korrigiert ihn nur, soweit die Sichtbarkeit ihn betrifft. — **Ausgang:** offen bis zur Closure.
- `build.gradle.kts` wird auch von `slice-jar-ohne-main-und-image` berührt (Manifest); die beiden Slices ändern verschiedene Blöcke, ein Rebase kann dennoch Konflikte zeigen. — **Ausgang:** offen bis zur Closure.

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

Wird bei der Closure gefüllt.

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
