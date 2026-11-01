# Slice slice-betrieb-log-schnittstelle: Strukturiertes Log: Schnittstelle, Pflichtfelder, Adapter und Ablösung der minimalen Log-Anschlüsse

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-betrieb-diagnose

**Bezug:** [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0009](../../adr/0009-authentifizierungsmodus.md), [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) (`Accepted`, Fragen 1 bis 5 und 8: Modell, Senke, Parameter von `start`, `principal`, `client_profile`, Herkunft der IDs), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) (Entscheidung 1: `ServerLog` als Schnittstelle abgelöst), [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) (Entscheidung 1 und 3, durch [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) geschärft)

**Berührte Spec-Stellen:** SPEC-012 · SPEC-017 · SPEC-043 · SPEC-048 · ARC-009 · [TDS-FA-09.a](../../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose)

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912. **Datum:** 2026-10-30 (neu zugeschnitten 2026-11-01 nach [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md)).

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Die Bibliothek definiert das Ereignismodell und die Senke (Arbeitsname `TelemetrySink`, Unterpackage von `domain`) samt öffentlichem Parameter von `TdsServer.start` (Voreinstellung: keine Senke); alle Betriebsereignisse der vorhandenen Aufrufstellen laufen über sie, `ServerLog` samt `STDOUT` ist aus `src/main` abgelöst, und das Hauptmodul enthält keinen Aufruf von `println`, `print` oder `printStackTrace`. Das Modell hat für Passwort, Prüfwert, Parameterwert, SQL-Text, Pfad und Ausnahmetext kein Feld, und ein Sammler macht die Ereignisse in Tests lesbar. Format, Ebene und Warteschlange sind **nicht** Teil der Bibliothek ([ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) Frage 1): sie liegen im Testserver ([slice-betrieb-log-adapter-testserver](slice-betrieb-log-adapter-testserver.md)).

**Zuschnitt gegenüber dem Plan vom 2026-10-30 (begründet).** Der frühere Punkt „Adapter und Konfiguration“ (Ausgabe nach Format der ADR, Log-Ebene über die Konfiguration, Sammler als zweiter Adapter) ist in zwei Teile zerfallen: Der Sammler und die Senke bleiben hier (Bibliothek), der JSON-Adapter mit `jackson-core`, `LOG_LEVEL`, die begrenzte Warteschlange, die Regel zum Prozessende und die Prozess-Inspektion wandern in einen eigenen Slice. Gründe: (1) Der Adapter braucht sowohl die Senke (dieser Slice) als auch den Testserver (`slice-testserver-herausloesen`) und liegt in einem anderen Source-Set mit eigenem Lock und eigenen Prozess-Tests; (2) er in `slice-testserver-herausloesen` zu führen ginge nicht, weil jener Slice schon drei große Liefer-Punkte trägt (Rückführung 2a bis 2c) und die Senke dann noch nicht existiert; (3) er in diesen Slice zu nehmen ergäbe sechs statt drei Liefer-Punkte. Die Reihenfolge ist Kante: Testserver-Umbau, dann dieser Slice, dann der Adapter-Slice.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- JSON-Zeile, `LOG_LEVEL`, begrenzte Warteschlange mit `dropped`, Regel zum Prozessende, Bereitschaftszeile, Inspektion aller Zeilen des laufenden Testserver-Prozesses und die Umstellung der Skript-Muster der E2E-Läufe — [slice-betrieb-log-adapter-testserver](slice-betrieb-log-adapter-testserver.md); das Format ist eine Aussage über den Testserver und kein Vertrag der Bibliothek ([ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) Frage 8).
- Die Spezifikation der Pflicht-Logfelder, des Feldes der Anmeldekennung, von `client_profile` und der Herkunft der IDs — [slice-spec-nachzug-telemetrie](../in-progress/slice-spec-nachzug-telemetrie.md); sie steht vor diesem Slice, damit die Spezifikation führt. Die endgültigen Namen der öffentlichen Typen trägt dieser Slice in `SPEC-059` nach (Doku-Pflicht unten).
- Metriken und Spans, der Abstand der Schnappschüsse (`METRICS_INTERVAL_SECONDS`, Wert der `ServerConfig`) und das Ereignis `metrics` — [slice-betrieb-metriken-tracing](slice-betrieb-metriken-tracing.md); dieser Slice trägt nur das Log.
- Welche Zustandsereignisse der Server meldet (Start, Bindung, Beenden, Login) und die Startdiagnose als Prozesstest — [slice-betrieb-zustandsereignisse](slice-betrieb-zustandsereignisse.md); hier wird die Schnittstelle gebaut und werden die vorhandenen Aufrufstellen umgestellt, ohne neue Ereignisarten zu erfinden, die keine Aufrufstelle haben (Bau legt die Menge fest; der Entwurf der ADR ist Setzung).
- Austausch des Authentifizierungsadapters — bleibt nicht öffentlich ([ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) Entscheidung 1); der Parameter wirkt nur auf Log und Telemetrie.
- TLS-Modi und Konfigurationsnegativtests — [slice-qa-sicherheit](../done/slice-qa-sicherheit.md) (Tests liegen dort).
- Kopien des Passworts im Speicher von JVM und Adapter — `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`; das Log betrifft die Ausgabe, nicht den Speicher.
- Rotation, Aggregation und Versand der Logzeilen, Ziel (Datei, Standardausgabe), Escaping im Adapter eines Embedders — Betreiber und Adapter ([ARC-014](../../../../spec/architecture.md#3-externe-abhängigkeiten): austauschbar); die Bibliothek schreibt nirgends hin.
- Die Schlusszusage `explicitApi` für die neuen Typen — [slice-explicit-api-umbau](slice-explicit-api-umbau.md); die neuen Typen tragen ihre ausdrückliche Sichtbarkeit trotzdem selbst, wenn der Umbau schon steht.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Modell, Senke, Parameter und Sammler** ([TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)): Typen in einem Unterpackage von `domain` (Ort nach [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) Frage 2 A, Namen Setzung des Baus); geschlossene Menge von Ereignisarten mit typisierten Feldern, **kein Freitext**; das Ereignis trägt Zeitpunkt (`at`, Zeitgeber der Bibliothek), Ebene (`ERROR`, `WARN`, `INFO`, `DEBUG`, je Ereignisart fest) und die Pflichtfelder der Spezifikation, soweit vorhanden: `connection_id` (Zähler des Listeners), `session_id` und `request_id` (zufällige UUID, wie der Code sie heute bildet), `client_profile` (aus dem ausgehandelten Protokoll: `tds-7.4` oder `tds-8.0` mit Verschlüsselungsmodus, vor der Aushandlung `unknown`), `error_code` (`TDS_*`), Ausnahmen nur als Typname; `principal` steht nur im Ereignis „Anmeldung angenommen“; Zeichenketten-Felder sind im Modell auf 256 Zeichen gekürzt (Setzung der ADR, nicht gemessen). Die Senke ist eine Schnittstelle mit einer Funktion, die ein Ereignis entgegennimmt; das KDoc trägt den Vertrag (blockiert nicht, wirft nicht, Besitz und Schließen liegen bei dem, der sie übergibt) und verweist auf den Adapter des Testservers als Beispiel; die Bibliothek fängt Ausnahmen der Senke ab. `TdsServer.start` bekommt den öffentlichen Parameter mit Voreinstellung „keine Senke“ (Form `start(config, source, telemetry = …NONE)`, Setzung); `Wiring` bleibt `internal`. Ein Sammler (hält die Ereignisse in einer Liste) steht für `src/test`, den Einbettungstest und die Tests des Testservers bereit (öffentlicher Typ oder je Source-Set, Entscheidung des Baus mit Begründung in der Closure-Notiz). Die Instanztrennung bei mehreren Servern in einer JVM ([ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) Offen (f): `connection_id` zählt je Listener) legt der Bau fest, entweder als Feld im Ereignis oder als benannte Grenze. **Belege:** Tests mit dem Sammler: Pflichtfelder je Ereignisart vorhanden, soweit zutreffend; eine Senke, die wirft, bricht weder Anfrage noch Verbindung (Verhalten, das die ADR als „nicht gemessen“ führt); der Vertrag der Senke legt fest, was mit `CancellationException` und `Error` geschieht (Review I-1 von `slice-adr-observability`: „fängt Ausnahmen ab und verwirft sie“ lässt es offen; [ADR-0013](../../adr/0013-backend-grenzen.md) Entscheidung 7 behandelt die Abbruchausnahme) und ein Test belegt es; ohne Senke schreibt die Bibliothek nichts.
- [ ] **Ablösung der Log-Anschlüsse und zwei Sperren** ([TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)): `ServerLog` (mit `NONE` und `STDOUT`), `LogEntry` des Handshakes und die Rückrufe des Listeners rufen nach dem Bau die Senke; `ServerLog.STDOUT` bleibt **nicht** in `src/main`; die Aufrufstellen aus [slice-handshake-login](../done/welle-handshake-login/slice-handshake-login.md), [slice-transport-listener-lebenszyklus](../done/welle-transport-tls/slice-transport-listener-lebenszyklus.md), [slice-bootstrap-konfiguration](../done/welle-handshake-login/slice-bootstrap-konfiguration.md), [slice-fehler-mapping](../done/slice-fehler-mapping.md) und [slice-abbruch-ressourcen-shutdown](slice-abbruch-ressourcen-shutdown.md) sind umgestellt (Verhalten unverändert, Zeilen ändern Form und Felder); die Bibliothekstests, die `ServerLog.STDOUT` nutzen (`LoginLogInspectionTest`, `HandshakeWiringTest` und weitere, die der Übersetzungslauf nennt), lesen über den Sammler. Zwei Sperren in `config/detekt/detekt.yml`, beide Verschärfungen (`AGENTS.md` §3.6): `ForbiddenMethodCall` sperrt `println`, `print` (`kotlin.io`, `PrintStream`) und `printStackTrace` im **ganzen Hauptmodul ohne Ausnahme** (`includes` über `domain` und `port` hinaus), und `ForbiddenImport/simulation` bekommt einen Eintrag für das Unterpackage der Schnittstelle; je Eintrag ein Rot-Fall unter `config/detekt/selbsttest-aufrufe/` beziehungsweise der Importtestdaten, den `make detekt-selbsttest` fährt, mit Begründung am Eintrag. **Dritte Sperre aus dem Review von `slice-adr-observability` (M-3):** Die Aussage der [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) Frage 2 A „eine Regelzeile“ ist am Bestand nicht belegt: `ForbiddenImport/protocol` sperrt nur `simulation` und `runtime`, `ForbiddenImport/transport` sperrt `port`, `simulation`, `runtime`; beide erlauben `observability`, und die Fitness-Tabelle der ADR nennt nur `simulation` und `println`. Der Bau entscheidet mit Begründung in der Closure-Notiz, ob `protocol` und `transport` den Aggregator in `observability` nicht mehr importieren dürfen (Austauschbarkeit nach ARC-014: sie rufen nur die Senke); wird die Sperre gesetzt, gehören Eintrag und Rot-Fall (`protocol` oder `transport` importiert `observability`) in die Importtestdaten, andernfalls steht die Grenze als benannte Lücke in der Closure-Notiz. **Belege:** `make detekt` und `make detekt-selbsttest` grün; ob die Sperre einen Aufruf über `System.out` trifft, belegt der Rot-Fall (die ADR nennt es „nicht gemessen“); `grep -rn "ServerLog" src/main` findet nichts.
- [ ] **Redaktion und Einbettung** ([TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit)): Ein Redaktionstest je Ereignisart gibt Eingaben mit Passwort, Prüfwert, Parameterwert, SQL-Text, Pfad und Inhalt von Zertifikat und Schlüssel sowie Ausnahmetext (SPEC-043, SPEC-048) hinein und findet nichts davon in einem Ereignis des Sammlers — mit je verschiedenen Eingabewerten (`BEO-TDS/strukturtest-ohne-variation-der-eingabe`). Ein Test für `principal` mit einem Authentifizierungsadapter, der die Eingabe als Kennung zurückgibt (Steuerzeichen, Anführungszeichen, länger als 256 Zeichen), belegt die Längengrenze im Modell und, dass das Feld nur im Ereignis „Anmeldung angenommen“ steht; er belegt **nicht** die Abwesenheit eines Geheimnisses in der Kennung eines eigenen Adapters (Grenze der [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md), in der Closure-Notiz zu nennen). Der Einbettungstest im Source-Set `integrator` registriert einen Sammler über den Parameter von `start`, startet, bedient und stoppt; die Ereignisse erscheinen im Sammler, die Standardausgabe des Prozesses bleibt leer (umgeleitet, im selben Prozess: schwächerer Beleg als ein Prozesstest), und ohne Senke läuft der Server und schreibt nichts. Der Inspektionstest über alle Zeilen des **laufenden Testserver-Prozesses** gehört nicht hierher, sondern in [slice-betrieb-log-adapter-testserver](slice-betrieb-log-adapter-testserver.md) (Träger der Trägerlücke F-1 aus dem Review von `slice-qa-sicherheit`, vom Nutzer 2026-10-31 bestätigt als Gesamtsystem-Gegenstück; der Träger wechselt dorthin, weil die Zeilen erst mit dem Adapter existieren — Setzung des Planners, zur Bestätigung).

Nachweis-Pflichten für alle drei Punkte:

- [ ] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>` (Konvention nach [slice-qa-spec-abdeckung](../done/slice-qa-spec-abdeckung.md)); die Tests laufen in `make test` und sind grün.
- [ ] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet; die Zuordnung *Test | Festlegung* je Aussage steht in der Closure-Notiz (`BEO-TDS/aussagen-ueber-messung-hinaus`, siehe §8).
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update: `SPEC-059` nennt die endgültigen Namen der Senke, des Ereignismodells und des Parameters von `start` in der Zusage-Zeile (Signaturzusage bis `1.0` in der Form von Entscheidung 3 der [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md)); KDoc der Senke trägt den Vertrag ([ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) Frage 8 A); `harness/README.md` nennt die neuen Sperren in der Zeile von `make detekt` und `make detekt-selbsttest`, soweit sich deren Aussage ändert.
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
| Unterpackage von `domain` (Arbeitsname `domain.telemetry`) <!-- d-check:ignore (geplant: neue Datei) --> | neu | Ereignismodell, Senke, Sammler (Ort nach [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) Frage 2 A); reine Werte, keine Fremdbibliothek, kein `java.util.logging` |
| `src/main/kotlin/io/github/pt9912/tdswire/runtime/TdsServer.kt` | update | öffentlicher Parameter von `start` mit Voreinstellung „keine Senke“; `Wiring` bleibt `internal` |
| `src/main/kotlin/io/github/pt9912/tdswire/runtime/ServerLog.kt`, `protocol/` (`LogEntry`), `transport/` (`ListenerEvents`), `config/`, `runtime/` | update / entfernt | Aufrufstellen rufen die Senke; `ServerLog` samt `STDOUT` entfällt |
| `src/main/kotlin/io/github/pt9912/tdswire/observability/` | update | nur Aggregation, falls dieser Slice sie braucht; sonst bleibt sie `slice-betrieb-metriken-tracing` |
| `config/detekt/detekt.yml`, `config/detekt/selbsttest-aufrufe/` und Importtestdaten | update / neu | `ForbiddenMethodCall` über das ganze Hauptmodul; `ForbiddenImport/simulation` für das Unterpackage; Rot-Fälle |
| `src/test/kotlin/io/github/pt9912/tdswire/` (Tests, die `ServerLog.STDOUT` nutzen; `observability/`) <!-- d-check:ignore (geplant: neue Datei) --> | update / neu | Sammler statt `STDOUT`; Redaktionstests Happy/Boundary/Negative; `@spec`-Tag SPEC-012, SPEC-043, SPEC-048 |
| `src/integrator/` | update | Einbettungstest mit Sammler und leerer Standardausgabe |
| `spec/spezifikation.md`, `SPEC-059` | update | endgültige Namen der Senke (Zusage-Zeile) |
| `harness/README.md` | update | Zeilen der Sperren, soweit ihre Aussage sich ändert |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`), alle Bedingungen beobachtbar:

1. [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) steht im ADR-Index auf `Accepted` (erfüllt: Annahme am 2026-11-01). Die Entscheidung ist damit getroffen; die Closure von [slice-adr-observability](../done/slice-adr-observability.md) ist keine Bedingung dieses Slice (ihr Review und ihre Verifikation laufen unabhängig).
2. [slice-testserver-herausloesen](../done/slice-testserver-herausloesen.md) liegt in `done/`: `main()` und das Setzen von `ServerLog.STDOUT` haben `src/main` verlassen, der Testserver bindet nur die öffentliche Fläche, und die Skript-Prüfungen der Bibliothekszeilen sind herausgenommen oder mit diesem Träger gekennzeichnet. Ohne diese Kante bliebe `STDOUT` am Prozess, und die Sperre `println` im Hauptmodul wäre rot.
3. [slice-spec-nachzug-telemetrie](../in-progress/slice-spec-nachzug-telemetrie.md) liegt in `done/`: die Spezifikation nennt Pflicht-Logfelder, `principal`, `client_profile` und die Herkunft der IDs, bevor der Code sie baut.
4. [slice-handshake-login](../done/welle-handshake-login/slice-handshake-login.md), [slice-bootstrap-konfiguration](../done/welle-handshake-login/slice-bootstrap-konfiguration.md) und [slice-transport-listener-lebenszyklus](../done/welle-transport-tls/slice-transport-listener-lebenszyklus.md) liegen in `done/` (die Aufrufstellen existieren; erfüllt).
5. **Nutzerentscheidung offen:** [ADR-0029](../../adr/0029-log-parameter-am-einstieg.md) (`Proposed`) beantwortet Offen (b) der [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) und löst die Sätze zum Log in [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) und [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) teilweise ab; der Nutzer entscheidet über die Annahme („Annahme [ADR-0029](../../adr/0029-log-parameter-am-einstieg.md)“). Bis dahin widersprechen zwei `Accepted`-ADRs dem Parameter von `start` (Review M-1 von `slice-adr-observability`); der Slice startet erst mit `Accepted` von [ADR-0029](../../adr/0029-log-parameter-am-einstieg.md) oder mit einer ausdrücklichen anderen Entscheidung des Nutzers.
6. **Nutzerentscheidung offen (M-4):** Was nennt `StartOutcome.Aborted.reason` bei einem Bindefehler? Gebaut steht Adresse und Port im `reason` (`ListenerConfig.kt`, `Bootstrap.kt`); die Spezifikation sagt „ohne Konfigurationswert“ und trägt den Zielzustand-Vermerk, [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) „derselbe Inhalt wie das Abbruch-Ereignis“. Empfehlung des Koordinators: `reason` darf Adresse und Port nennen, die Regel der Spezifikation wird präzisiert. Die Entscheidung ist nicht getroffen (Befund aus der Verifikation von [slice-spec-nachzug-telemetrie](../in-progress/slice-spec-nachzug-telemetrie.md)); der Slice baut `reason` erst danach.
7. Ein Verantwortlicher ist im Kopf eingetragen.

**Folge-Kanten:** [slice-betrieb-log-adapter-testserver](slice-betrieb-log-adapter-testserver.md) wartet auf diesen Slice; [slice-betrieb-zustandsereignisse](slice-betrieb-zustandsereignisse.md) und [slice-betrieb-metriken-tracing](slice-betrieb-metriken-tracing.md) warten auf den Adapter-Slice.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2 oder die Ablösung sprengt den Rahmen; Schnittvorschlag: (a) Modell, Senke, Parameter, Sammler | (b) Ablösung der Aufrufstellen und Sperren | (c) Redaktion und Einbettung; (b) und (c) setzen (a) voraus.
- `in-progress` → `open` (blockiert — Carveout?): ein Slice, dessen Aufrufstelle umzustellen ist (etwa `slice-abbruch-ressourcen-shutdown`, [welle-fehler-abbruch](../welle-fehler-abbruch.md)), ist noch in Arbeit — die Umstellung wartet auf dessen `done/`; oder `slice-testserver-herausloesen` fällt zurück; oder die Öffentlichkeit der neuen Typen verlangt eine Änderung an der Fläche, die [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) und [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) nicht tragen.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün (einschließlich `make test` und `make detekt-selbsttest`), der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung *Test | Festlegung* je Aussage, die Entscheidungen des Baus zu den Offen-Punkten (f) und (g) der [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) sowie den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Die Ablösung berührt Code, den andere Slices schreiben (`slice-abbruch-ressourcen-shutdown` und die Abbruch-Slices nach [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) in [welle-fehler-abbruch](../welle-fehler-abbruch.md)); sie läuft erst, wenn deren Slice in `done/` liegt, damit kein Slice unter einem anderen die Aufrufstelle wechselt. Seit der Lockerung des Start-Triggers von [welle-produktschnitt](../welle-produktschnitt.md) ist die Reihenfolge zu jener Welle nicht mehr durch Wellen-Trigger gesichert. — **Ausgang:** offen bis zur Closure.
- Die Garantie „kein Feld für Geheimnisse“ gilt nur für das Modell und nur, solange es kein Freitextfeld trägt; die vier Zeichenketten-Felder (`principal`, Adresse beim Bindefehler, Typname der Ausnahme, Schlüsselname) sind keine geschlossene Menge, und die Kennung eines eigenen Authentifizierungsadapters kann ein Geheimnis tragen ([ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) Frage 2). Der Redaktionstest prüft den Ausnahmetext (SPEC-048) ausdrücklich. — **Ausgang:** offen bis zur Closure.
- Der Redaktionstest prüft die Ereignisarten, die es bei der Closure gibt; eine spätere Ereignisart ohne Test fällt nicht auf. Die Closure nennt die Grenze (`BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`). — **Ausgang:** offen bis zur Closure.
- Schalter für Parameterwerte: entschieden (Frage 4 A: keiner; das Modell hat kein Feld dafür). Ein Test belegt, dass kein Ereignis Parameterwerte trägt. — **Ausgang:** entfallen: [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) Frage 4 A (2026-11-01); der Test ist Teil von Liefer-Punkt 3.
- Die Senke eines Embedders kann den Anfragepfad anhalten (Vertrag „blockiert nicht“ ist Zusage an den Aufrufer, die Bibliothek schützt nicht davor, Frage 7, akzeptiertes Negativ). Rückstau unter Last ist ungemessen (`BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`, geplant bei [slice-qa-performance](slice-qa-performance.md)). — **Ausgang:** offen bis zur Closure (erwartet: weiter offen → Register).
- Die Namen (`TelemetrySink`, `domain.telemetry`) sind Arbeitsnamen; `SPEC-059` trägt sie erst mit der Closure dieses Slice, und der Name, den der Bau wählt, kann von der ADR abweichen ([ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) Offen (g)). — **Ausgang:** offen bis zur Closure.
- Instanztrennung bei mehreren Servern in einer JVM ([ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) Offen (f)): `connection_id` zählt je Listener; ohne Feld im Ereignis mischen sich die Ereignisse zweier Server in derselben Senke. — **Ausgang:** offen bis zur Closure.
- Die Importregel `protocol`/`transport` gegen `observability` fehlt im Bestand (Review M-3 von `slice-adr-observability`); „eine Regelzeile“ der ADR ist nicht gemessen — die Entscheidung steht in Liefer-Punkt 2. — **Ausgang:** offen bis zur Closure.
- Ob die Importregel `domain.*` der Domain-Regel das Unterpackage der Schnittstelle zulässt und ob `ForbiddenMethodCall` Aufrufe über `System.out` trifft, ist in der ADR nicht gemessen; der Bau belegt es mit den Rot-Fällen. — **Ausgang:** offen bis zur Closure.
- Der Träger des Inspektionstests über alle Zeilen des laufenden Prozesses wechselt auf [slice-betrieb-log-adapter-testserver](slice-betrieb-log-adapter-testserver.md); bis zu dessen Closure ist die Trägerlücke F-1 aus dem Review von `slice-qa-sicherheit` offen. — **Ausgang:** offen bis zur Closure (erwartet: eingetreten, → `slice-betrieb-log-adapter-testserver`).

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Die Observability-Schicht (ARC-009) ist ein Pfad-Cluster (Achse 3) mit eigener Importregel (Achse 1 plausibel); eine eigenständig abgleichbare Inventur-Linie (Achse 2) besteht erst mit dem Code. Der Modus Greenfield bliebe derselbe, eine eigene Zeile in `harness/conventions.md` löste keine andere Regel aus; `*` bleibt, eine Ausdifferenzierung ist Sache des Nutzers und hier nicht vorgenommen.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, 19 Beobachtungen, Stand des Worktrees auf `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`); nachgesehen am 2026-11-01 nach [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) (zusätzlich berührt: `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`, geplant bei `slice-qa-performance`, Risiko in §6; `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`, 10×, offen: die Verlagerung des Inspektionstests in den Adapter-Slice und der Zuschnitt in zwei Slices sind Lesarten des Planners, zur Bestätigung; `BEO-TDS/dod-haengt-an-offenem-slice-ohne-rueckfuehrung`, 3×, offen: die Kanten in §4 nennen Start-Trigger und Rückführung):

- `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` — 3×, Stand *geplant* (Träger [slice-qa-sicherheit](../done/slice-qa-sicherheit.md)). Betrifft die Redaktion von Passwort und Prüfwert im Log; die Redaktion im Modell trägt dieser Slice, die Gesamtinspektion am Prozess [slice-betrieb-log-adapter-testserver](slice-betrieb-log-adapter-testserver.md); [slice-qa-sicherheit](../done/slice-qa-sicherheit.md) prüft das minimale Log von heute.
- `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen` — 1×, offen. Betrifft die Speicherkopien, nicht die Ausgabe; ausdrücklich nicht Gegenstand (§1).
- `BEO-TDS/strukturtest-ohne-variation-der-eingabe` — 1×, offen. Betrifft den Redaktionstest: die Eingabewerte variieren je Lauf (§2).
- `BEO-TDS/aussagen-ueber-messung-hinaus` — 16×, Stand *verkörpert* (`AGENTS.md` §3.8, Reviewer-Skill). Betrifft diesen Slice: Jede Aussage über „nie im Log“ nennt die geprüften Ereignisarten (§6).
- `BEO-TDS/test-stage-cache-verdeckt-wiederholung` — 2×, offen. Betrifft Tests in der Docker-Stage: gecachte grüne Läufe belegen Wiederholung nicht.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Slice und Closure-Notiz nennen `ADR-*`- und `TDS-*`-Kennungen als Link.
- Ohne Treffer: `BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, `BEO-TDS/vergleichslauf-d-ungeklaert`, `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`, `BEO-TDS/offene-punkte-ohne-traeger`, `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung`, `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler`, `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`, `BEO-TDS/ablage-im-repo-bricht-docs-check`, `BEO-TDS/katalog-und-lock-ohne-gegenpruefung`, `BEO-TDS/java-quelle-umgeht-kotlin-regel`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/adapter-festlegungen-ohne-slice`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
