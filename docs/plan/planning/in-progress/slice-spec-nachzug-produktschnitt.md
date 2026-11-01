# Slice slice-spec-nachzug-produktschnitt: Spezifikation auf den Produktschnitt nachziehen (Bibliothek als Produkt, Testserver nur zum Testen)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-produktschnitt

**Bezug:** [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung) (Einstieg), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) (Tabellenbeschreibung, Läufe); [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) (`Accepted`, Folgepflicht 1), [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) (`Accepted`, Folgepflicht Spezifikation: `SPEC-059`, `SPEC-035`), [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) (Entscheidung 3, Fläche).

**Berührte Spec-Stellen:** SPEC-052 · SPEC-005 · SPEC-010 · SPEC-023 · SPEC-035 · SPEC-040 · SPEC-059 (Zusage-Zeile und Vertrag „Einstieg des Integrators“) · Änderungsprotokoll der Spezifikation.

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-11-01.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** `spec/spezifikation.md` sagt, was nach [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) gilt: Das Produkt ist die Bibliothek mit `TdsServer.start`; Umgebungsvariablen, Dateipfade, `simulationFile`, die Startklasse und das Laufzeit-Image sind entweder Aussagen über den Testserver oder entfallen; `SPEC-059` trägt keinen Zwischenstand-Satz mehr.

**Befund, der den Slice begründet** (am 2026-11-01 in `spec/spezifikation.md` gelesen, nichts ausgeführt): `SPEC-052` nennt ein Jar mit Startklasse `MainKt`, `lib/`, Laufzeit-Image, Konfiguration im Container und `make laufzeittest` als Produktaussagen; `SPEC-005` und `SPEC-010` führen `LISTEN_PORT`, `LISTEN_HOST`, `ENCRYPTION_POLICY`, `TLS_KEYSTORE_FILE` und `TLS_KEYSTORE_PASSWORD_FILE` als Konfiguration des Produkts; `SPEC-023` nennt das JVM-Flag „des Serverprozesses“; `SPEC-035` nennt die Datei und `SIMULATION_DESCRIPTION_FILE`; `SPEC-040` den Lauf gegen „den Serverprozess“; `SPEC-059` sagt `ConfigKeys`, `ServerConfig` mit `simulationFile`, „Jar und `lib/` (ein Artefakt, `SPEC-052`)“ und die YAML-Bibliothek zu und trägt einen Satz, der diese Zusagen als Zwischenstand kennzeichnet. Das Lastenheft ist in Version 0.6.0 schon angepasst (Nutzerentscheidung 2026-11-01); die Spezifikation widerspricht ihm bis zu diesem Slice.

**Vorbedingung „Folge-ADR angenommen“: erfüllt.** [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) ließ die Öffentlichkeit von `SimulationBackend` und `SimCatalog` offen (Offen-Punkt 1); [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) ist am 2026-11-01 `Accepted` und wählt Variante (i): beide Namen sind öffentlich, `SimCatalog.find` wird `internal` (Setzung der ADR, der Übersetzungslauf des Umbaus belegt sie). Die Fläche in `SPEC-059` und die Läufe in `SPEC-040` folgen dieser ADR; dieser Slice schreibt keine ADR.

**Aus den Folgepflichten der angenommenen ADRs gehört hierher:** die Spec-Folgepflicht von [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) (`SPEC-059`, Zeile „Zusage“: `SimulationBackend` und `SimCatalog` als verbindliche Namen mit der Signaturzusage nach Entscheidung 3; `SPEC-035`: der Weg „Backend mit Katalog bauen und als `Custom` übergeben“ neben `Simulation`). Beide Stellen sind schon Gegenstand der Liefer-Punkte 2 und 3; die Folgepflicht wächst in sie hinein, ohne einen vierten Punkt zu ergeben.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Das Lastenheft — es ist in 0.6.0 angepasst und steht im Rang über der Spezifikation; ein Widerspruch wäre ein Befund für den Nutzer, kein Anlass zu ändern.
- Code, Skripte, `Dockerfile`, `build.gradle.kts` und die Zeilen der Sensors-Tabelle in `harness/README.md` — sie folgen mit `slice-testserver-herausloesen` und `slice-jar-ohne-main-und-image`. Die Zeilen von `harness/README.md` beschreiben Targets im Ist-Zustand (`smoke`, `laufzeittest`, `build`, `skript-selbsttest`, `kopplung-zahlen`); vor dem Umbau wären sie falsch (`AGENTS.md` §3.8), und die ADR lässt sie dem Umbau folgen. Das weicht von der Zuordnung ab, die die Planungsanfrage diesem Slice gab, und steht als offene Frage im Bericht der Planung.
- Die Spezifikation des Logs und der Observability (Abschnitte 4 und 5, `TDS-FA-09.a` Nr. 3, `LOG_LEVEL` und `METRICS_INTERVAL_SECONDS` als Eingänge des Testservers, das Format der JSON-Zeile) — [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) ist `Accepted` (2026-11-01) und ihre Spec-Folgepflicht (1) hat einen eigenen Träger: [slice-spec-nachzug-telemetrie](../open/slice-spec-nachzug-telemetrie.md). **Zerlegung begründet:** Nähme dieser Slice sie auf, hätte er sechs Liefer-Punkte (Artefakt und Läufe · Eingänge des Testservers · `SPEC-059` · Pflicht-Logfelder und Ereignismodell · Zugang zu Metriken und Spans · Eingänge und Format des Log-Adapters) statt der erlaubten drei; die Schnittlinie ist der Abschnitt der Spezifikation (Abschnitte 3, 6 und 7 hier, Abschnitte 4 und 5 dort) und die Aussage („Produktschnitt“ gegenüber „Telemetrie“), nicht die Datei. [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) verlangt den Nachzug „in einem Zug“ mit dem von [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md); die beiden Slices laufen deshalb unmittelbar nacheinander (der zweite wartet auf diesen, §4 dort).
- Die Namen der Senke, des Ereignismodells und des Parameters von `start` in der Zusage-Zeile von `SPEC-059`: [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) nennt `TelemetrySink` und `domain.telemetry` ausdrücklich Arbeitsnamen (Offen (g)); die Zusage von `SPEC-059` trüge sonst mehr, als belegt ist (`AGENTS.md` §3.8). Die endgültigen Namen trägt `SPEC-059` mit der Closure von [slice-betrieb-log-schnittstelle](../open/slice-betrieb-log-schnittstelle.md).
- Die Spec-Folgepflichten aus [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) (`SPEC-016`, `SPEC-029`, `SPEC-001`, `SPEC-030` und weitere): sie gehören in die Abbruch- und Reset-Slices, die ein anderer Planner anlegt; dieser Slice fasst sie nicht an.
- Eine Änderung von [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) (Format, Teilmenge, Typabbildung, Fehlerfälle der Tabellenbeschreibung) — sie gelten unverändert für die Datei des Testservers; der Slice verschiebt ihren Ort in der Aussage.
- Angenommene ADRs — sie bleiben unverändert (`AGENTS.md` §3.5). Ob [ADR-0011](../../adr/0011-pruefwertformat.md) (Benutzerliste über Umgebung und Datei) und [ADR-0002](../../adr/0002-tls-und-verschluesselung.md) (Schlüsselspeicher als Datei) eine Folge-ADR brauchen, stellt der Slice fest und meldet es; er entscheidet es nicht.
- `spec/architecture.md` ändert der Slice nur, wenn eine Aussage dort dem Schnitt widerspricht (Prüfung in §3); die Sicht nennt keine ADR, Welle oder Slice (`AGENTS.md` §3.4).

Spec-führend ist gewollt: Das Repo ist Greenfield (`harness/conventions.md`, Modus-Deklaration: Spec und ADRs führen, Code folgt). Zwischen diesem Slice und der Closure der Umbau-Slices sagt die Spezifikation früher als der Code; §6 führt das als Risiko.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Wortlaut und Zuschnitt folgen [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) Folgepflicht 1 und der Spec-Folgepflicht von [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) (beim Übergang `open` → `next` gegen beide ADRs geprüft). Jede Aussage trägt ihren Beleg als Test oder ist als Festlegung gekennzeichnet (`AGENTS.md` §3.8); die Spezifikation nennt keine ADR-Bezüge in Sicht-Dokumenten (`AGENTS.md` §3.4).

Liefer-Punkte (drei):

- [x] **Artefakt und Läufe** ([TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)): `SPEC-052` sagt: Bibliotheks-Jar `tdswire.jar` ohne `Main-Class`, Lizenz in `META-INF`, `Class-Path` auf `lib/` für Embedder, Manifest-Prüfung im Bau ohne Startklasse; Laufzeit-Image, Nutzer, Port, Konfiguration im Container und `JAVA_TOOL_OPTIONS` stehen nicht mehr als Produktaussagen da (sie entfallen oder sind Aussagen über den Testserver-Container des Laufs `make laufzeittest`). `SPEC-040` sagt: die Läufe (`make simulations-integration` und die Läufe mit Referenzclient) gehen gegen den Testserver; die Heap-Angabe des Integrationslaufs bleibt eine Angabe des Testservers (Wert unverändert, `make kopplung-zahlen` hält ihn).
- [x] **Eingänge des Testservers** ([TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)): `SPEC-005` und `SPEC-010` nennen `LISTEN_PORT`, `LISTEN_HOST`, `ENCRYPTION_POLICY`, `TLS_KEYSTORE_FILE` und `TLS_KEYSTORE_PASSWORD_FILE` als Eingänge des Testservers; Wertebereiche, Paarung und Voreinstellungen bleiben Aussagen über `ServerConfig` und ihre Prüfung (`ConfigValidation.check`). `SPEC-023`: das JVM-Flag gilt für jeden Prozess, der die Bibliothek trägt, also Embedder und Testserver. `SPEC-035`: Datei und `SIMULATION_DESCRIPTION_FILE` gehören dem Testserver, die Kotlin-Schnittstelle (`BackendSource.Simulation(tables)`) bleibt, `simulationFile` und die Größengrenze von 1 MiB stehen nicht mehr bei der Bibliothek; dazu der Weg „Backend mit Katalog bauen (`SimulationBackend(SimCatalog(tables))`) und als `BackendSource.Custom` übergeben“ neben `Simulation`, mit dem Vermerk der [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md), dass dieser Weg die Namensprüfung des Einstiegs nicht durchläuft und `SimCatalog` Doppelungen mit `IllegalArgumentException` meldet.
- [x] **Einstieg ohne Zwischenstand** ([TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung)): `SPEC-059`: der Zwischenstand-Satz der Zusage-Zeile ist entfernt; die Zusage nennt weder `ConfigKeys` noch `ServerConfig.simulationFile` noch „ein Artefakt“ noch Jackson; die Zeile „Abhängigkeiten“ des Vertrags sagt Bibliothek und `lib/` ohne YAML-Bibliothek; die Fläche folgt [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md): `SimulationBackend(catalog: SimCatalog)` und `SimCatalog(tables: List<SimTable>)` mit `tables` stehen als verbindliche Namen da, mit der Signaturzusage bis `1.0` in der Form von Entscheidung 3 der [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) (Änderung nur über die Spezifikation, im Änderungsprotokoll genannt); `SimCatalog.find` steht nicht in der Liste (die Setzung `internal` ist Festlegung, bis der Umbau sie belegt). Eine Suche über `spec/spezifikation.md` und `spec/architecture.md` nach der Kernaussage (`Startklasse`, `MainKt`, `Laufzeit-Image`, `simulationFile`, `ein Artefakt`, `SIMULATION_DESCRIPTION_FILE`, `Serverprozess`, `Jackson`) findet nur noch Treffer, die den Testserver nennen oder Messungen am Leser der Beschreibung beschreiben; die Trefferliste steht in der Closure-Notiz (gesucht wird die Aussage, nicht die genannte Zeile).

Nachweis-Pflichten für alle Punkte:

- [x] `make gates` grün (Implementer, vor jedem Commit mit Inhalt).
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update: Änderungsprotokoll der Spezifikation um eine Zeile ergänzt; `make spec-abdeckung` neu erzeugt und committet, falls eine `SPEC-*`-Kennung entsteht oder entfällt (erwartet: keine).
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo mit Wellen von der Welle-Closure geprüft.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `spec/spezifikation.md`, `SPEC-052` (Abschnitt 7, Festlegungen der Harness-Werkzeuge) | update | Bibliotheks-Jar ohne Startklasse, Wegfall der Produkt-Image-Aussagen, Manifest-Prüfung ohne Startklasse |
| `spec/spezifikation.md`, `SPEC-040` (Abschnitt 7) | update | Läufe gegen den Testserver; Heap-Angabe als Angabe des Testservers; der Aufzeichner des Kompatibilitätslaufs umhüllt das Simulations-Backend der Bibliothek, falls `SPEC-040` die Läufe so beschreibt |
| `spec/spezifikation.md`, `SPEC-005`, `SPEC-010`, `SPEC-023` (Tabelle der Festlegungen, Sicherheitsprofil) | update | Umgebungsvariablen und Dateipfade als Eingänge des Testservers; JVM-Flag für Embedder und Testserver |
| `spec/spezifikation.md`, `SPEC-035` (Abschnitt „Tabellenbeschreibung und Zeilenquelle“) | update | Datei und Variable beim Testserver, `simulationFile` weg; Weg `SimulationBackend(SimCatalog(…))` als `Custom` ([ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md)) |
| `spec/spezifikation.md`, `SPEC-059` (Zusage-Zeile, Vertrag „Einstieg des Integrators“) | update | Zwischenstand-Satz entfernt; `ConfigKeys`, `simulationFile`, „ein Artefakt“, YAML-Bibliothek angepasst; `SimulationBackend` und `SimCatalog` als verbindliche Namen ([ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md)); keine Namen der Senke (siehe §1) |
| `spec/spezifikation.md`, Änderungsprotokoll | update | eine Zeile je Nachzug |
| `spec/architecture.md` (`ARC-008`, Abschnitt der Schichten) | prüfen | nur ändern, wenn eine Aussage dem Schnitt widerspricht; ohne ADR-, Wellen- und Slice-Bezug |
| `docs/plan/spec-abdeckung.md` | update (erzeugt) | `make spec-abdeckung`, falls sich die Menge der Festlegungen ändert |

**Kennzeichnung des Zielzustands (Setzung des Implementers):** Die Spezifikation führt dem Code voraus (Greenfield). Wo eine Aussage des Produktschnitts am gebauten Stand noch nicht gilt, trägt die Stelle den Satz „**Zielzustand, nicht gebaut:** …“ (Muster der Spezifikation: „Zielbild, nicht gebaut“ in `SPEC-001`), der den gebauten Stand ohne Slice-, Wellen- oder ADR-Bezug nennt und, wo ein Zeitpunkt zu nennen ist, mit „gelten ab dem Umbau, der den Server zum Testserver macht“ endet (der Planungsbegriff „Produktschnitt“ steht nicht in der Spezifikation; die Wortlaut-Setzung folgt dem Review-Befund F-4). Eigene Vermerke (elf): `SPEC-052` (Zeile), `SPEC-040` (Klammer), `SPEC-005`, `SPEC-010`, `SPEC-035` (Absatz „Beschreibung als Datei“ und Spiegelstrich „Datei und Umgebungsvariable“), `SPEC-051` (Konfigurationsweg), Schlüsselmaterial, Einstieg (`BackendSource`, Prüfung, Abhängigkeiten). Verweise auf den Vermerk des Konfigurationswegs im selben Abschnitt (drei): `SPEC-051` „Lesen der Datei“, „Testmodus-Schalter“ und „Validierung der Liste“. Zusammen 14 Stellen. Die Umbau-Slices nehmen den Satz an der jeweiligen Stelle heraus, sobald der Code ihn trägt; ein stehengebliebener Satz ist dort ein Befund (`BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`).

Fundstellensuche (Teil der Arbeit, nicht des Ergebnisses): `Startklasse`, `MainKt`, `Laufzeit-Image`, `JAVA_TOOL_OPTIONS`, `ConfigKeys`, `simulationFile`, `SIMULATION_DESCRIPTION_FILE`, `LISTEN_`, `TLS_KEYSTORE`, `AUTH_USERS_FILE`, `Serverprozess`, `Jackson` über `spec/`. Treffer außerhalb der sieben genannten Festlegungen (erwartet etwa `SPEC-043` TLS-Material, `SPEC-051` Benutzerliste) gehören in diesen Slice, wenn sie dieselbe Aussage tragen (`BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`), alle Bedingungen beobachtbar:

1. [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) steht im ADR-Index auf `Accepted` (erfüllt: Annahme am 2026-11-01).
2. **Vorbedingung „Folge-ADR angenommen“ (erfüllt):** [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) steht im ADR-Index auf `Accepted` (Annahme am 2026-11-01, Variante (i)).
3. Der Start-Trigger von [welle-produktschnitt](../welle-produktschnitt.md) ist eingetreten (§2 der Welle: [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) `Accepted` und Nennung des Starts durch den Nutzer).
4. Ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): die Fundstellensuche (§3) findet Festlegungen außerhalb der drei Liefer-Punkte, die dieselbe Aussage tragen und mehr als einen weiteren Punkt ergäben; Zerlegung nach Abschnitt der Spezifikation (der Schnitt zu [slice-spec-nachzug-telemetrie](../open/slice-spec-nachzug-telemetrie.md) ist schon gezogen).
- `in-progress` → `open` (blockiert — Carveout?): [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) oder [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) wird durch eine Folge-ADR abgelöst, die die Fläche in `SPEC-059` oder die Läufe in `SPEC-040` über das Geplante hinaus ändert; oder [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) wird `Superseded`.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün (einschließlich `make docs-check`), der Review-Report liegt vor, die Trefferliste der Fundstellensuche steht in der Closure-Notiz, und die Closure-Notiz trägt den Steering-Loop-Lerneintrag (geschärfte Regel, neuer Sensor oder benannte Spec-Lücke). Gemessen ist die Gleichheit von Spezifikation und ADR und Lastenheft 0.6.0 (Selbstkonsistenz), nicht ein Verhalten des Codes: Der Code sagt bis zur Closure der Umbau-Slices etwas anderes.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Die Spezifikation sagt früher als der Code (Greenfield, Spec führt): zwischen diesem Slice und `slice-testserver-herausloesen` stimmen `SPEC-035`, `SPEC-052`, `SPEC-059` nicht mit `src/main` überein; ein Leser des Quelltexts findet `simulationFile` und `MainKt` noch. — **Ausgang:** weiter offen; Träger [slice-testserver-herausloesen](../open/slice-testserver-herausloesen.md) und [slice-jar-ohne-main-und-image](../open/slice-jar-ohne-main-und-image.md): die Zielzustand-Vermerke sind gekennzeichnet (14 Stellen), und beide Slices tragen die Nachweis-Pflicht, die Vermerke zu entfernen, mit Zuordnung der Stellen (Verifikation V-1).
- Festlegungen außerhalb der sieben genannten tragen dieselbe Aussage (etwa `SPEC-043`, `SPEC-051`, Sicherheitsprofil, Kompatibilitätsmatrix) und bleiben beim Nachzug stehen (`BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`, 3×). — **Ausgang:** eingetreten: Review F-1 und F-2 fanden `SPEC-035` (Absatz „Beschreibung als Datei“), `SPEC-051` und das Schlüsselmaterial ohne Vermerk; die Fix-Runde (1e38f5d) hat sie behoben, die Evidence liegt im Register (jetzt 4×).
- Die Folge-ADR zu Offen-Punkt 1 wird anders entschieden als die Empfehlung (i). — **Ausgang:** entfallen: [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md) wählt (i) (2026-11-01).
- Die Zusage-Zeile von `SPEC-059` nennt die Namen von `SimulationBackend` und `SimCatalog` vor dem Übersetzungslauf des Umbaus (`SimCatalog.find` als `internal` ist Setzung der ADR). — **Ausgang:** weiter offen; Träger [slice-testserver-herausloesen](../open/slice-testserver-herausloesen.md): der Übersetzungslauf belegt die Namen. Die Zusage nennt nur Konstruktoren und `tables`, `find` nicht (Verifikation: bestätigt).
- `SPEC-059` wird von zwei Spec-Schritten berührt (dieser Slice: [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md); `slice-betrieb-log-schnittstelle`: die Namen der Senke); die Fundstelle der Zusage kann dazwischen als vollständig gelesen werden (`BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`). — **Ausgang:** weiter offen; Träger [slice-betrieb-log-schnittstelle](../open/slice-betrieb-log-schnittstelle.md): die Zusage-Zeile nennt die Senke nicht und trägt keinen Zwischenstand-Satz, die Namen folgen mit dem Bau der Log-Schnittstelle.
- Ob [ADR-0011](../../adr/0011-pruefwertformat.md) und [ADR-0002](../../adr/0002-tls-und-verschluesselung.md) durch den Wegfall des Betriebswegs (Umgebung, Dateien) eine Folge-ADR brauchen, ist in [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) „nicht festgestellt“. — **Ausgang:** weiter offen; Entscheidung beim Nutzer, kein Träger-Slice. Das Register führt die Frage als `BEO-TDS/folge-adr-betriebsweg-nach-produktschnitt-ungeklaert`; keine Zeile der Spezifikation widerspricht den beiden ADRs.
- Eine Zusage der Spezifikation hat nach dem Nachzug keinen Test mehr, der sie trägt (etwa der Container-Teil von `SPEC-052`, dessen Tag `@spec` heute nur den Lizenztext nennt); `make spec-abdeckung` zeigt Waisen erst nach dem Umbau. — **Ausgang:** weiter offen; Register `BEO-TDS/zusage-container-teil-spec-052-ohne-test`, die Welle-Closure prüft `make spec-abdeckung` nach dem Umbau.

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

**Umsetzungsstand (Implementer, 2026-11-01):**

- Drift gemessen vor der Änderung (gelesen, nichts ausgeführt außer den Gates): `build.gradle.kts` setzt `Main-Class`, das `Dockerfile` führt die Stage `runtime`, `ServerConfig.simulationFile`, `ConfigKeys`, `ConfigLoader` und Jackson stehen in `src/main`. Die Spezifikation sagt daher an 14 Stellen Zielzustand (elf eigene Vermerke, drei Verweise; siehe §3).
- Trefferliste der Suche (`Startklasse`, `MainKt`, `Laufzeit-Image`, `simulationFile`, `ein Artefakt`, `SIMULATION_DESCRIPTION_FILE`, `Serverprozess`, `Jackson`, `ConfigKeys`, `Zwischenstand` über `spec/spezifikation.md` und `spec/architecture.md`): `simulationFile`, `MainKt`, `ConfigKeys`, `ein Artefakt` ohne Treffer; `SIMULATION_DESCRIPTION_FILE` in `SPEC-035` (Testserver) und in Historie-Zeilen; `Jackson` in `SPEC-035` (Messung am Leser der Beschreibung); `Serverprozess` in `SPEC-040` (Angabe des Testservers, vom Gate `make kopplung-zahlen` im Wortlaut „Serverprozess mit 48 MiB Heap, Text von 64 000 000 Zeichen“ festgehalten) und `SPEC-052` (Testserver); `Startklasse` und `Laufzeit-Image` in `SPEC-052` (Aussage „ohne“ und Zielzustand-Satz) und in Historie-Zeilen; `Zwischenstand` nur in der neuen Zeile des Änderungsprotokolls. `spec/architecture.md`: keine Treffer, keine Aussage dem Schnitt widersprechend, unverändert.
- Systematische Nachsuche nach Präsens-Aussagen, die der gebaute Stand widerlegt und die keinen Marker im selben Absatz tragen (Suchbegriffe `Testserver`, `gehören dem`, `Bibliothek nimmt`, `Bibliothek kennt`, `Bibliothek liest`, `Umgebungsvariable des Testservers`; Review-Fix): ohne Marker waren der Eingangssatz von `SPEC-035` (Absatz „Beschreibung als Datei“, jetzt eigener Vermerk), `SPEC-051` „Testmodus-Schalter“ und „Validierung der Liste“ (jetzt Verweis). Treffer ohne Gegensatz zum Code: Messungen am Spike-Testserver (Zeilen 35, 168, 443, 950, 961, 1092, 1134), das JVM-Flag (`SPEC-023`, gilt für jeden Prozess), `SPEC-025` (Containerisierter Testserver: die Integrations- und Kompatibilitätsserver laufen im Container), Schlüsselmaterial/`SPEC-005`/`SPEC-010` (markiert).
- `ConfigKeys`: Wegfall aus der Zusage steht im Änderungsprotokoll; die Schlüssel in `ConfigIssue` sind Text ohne zugesagten Wortlaut, `ConfigIssue` und `IssueKind` bleiben als Typen der Signatur zugesagt (Setzung, Review-Befund F-3).
- „Für Test und kleine Betriebe gedacht“ (`SPEC-051`, Review-Befund F-5): geprüft, trägt weiter als Aussage über die feste Benutzerliste der Bibliothek (kein Betriebsweg-Anspruch des Testservers); unverändert.
- Fundstellen außerhalb der sieben genannten Festlegungen, die dieselbe Aussage trugen und hier mitgezogen wurden, ohne einen vierten Punkt zu ergeben: `SPEC-025` (Wort „Testserver“), `SPEC-051` (Konfigurationsweg, Lesen der Datei, Testmodus-Schalter, Zuordnung `AUTH_USERS_FILE`), Schlüsselmaterial des Sicherheitsprofils (`TLS_KEYSTORE_*`).
- Folge-ADR zu [ADR-0011](../../adr/0011-pruefwertformat.md) und [ADR-0002](../../adr/0002-tls-und-verschluesselung.md) (Befund, keine Entscheidung): Beide Entscheidungen bleiben als Aussagen über Prüfwertformat, Datei und Schlüsselnamen lesbar, sobald man sie auf den Testserver bezieht; keine Zeile der Spezifikation widerspricht ihnen. Offen bleibt, ob die Zuordnung „Datei und Umgebung gehören dem Testserver“ eine Folge-ADR verlangt (Nutzer/Planner entscheidet).

**Closure (Planner, 2026-11-01):**

- **Was hat funktioniert:** Review ([Bericht](../../../reviews/2026-11-01-72d8ac8-slice-spec-nachzug-produktschnitt.md)) und Fix-Runde fanden die zweiten Fundstellen vor der Closure; die Verifikation ([Bericht](../../../reviews/2026-11-01-1e38f5d-verifikation-slice-spec-nachzug-produktschnitt.md)) bestätigte alle drei Liefer-Punkte und alle 14 Zielzustand-Stellen gegen den gebauten Stand (kein HIGH, kein MEDIUM). Die Mutation am Heap-Satz von `SPEC-040` (`48 MiB` auf `47 MiB`) machte `make kopplung-zahlen` rot und wurde zurückgesetzt.
- **Zuordnung der Aussagen:**
  - *Gemessen:* nur die Kopplung des Heap- und Textsatzes von `SPEC-040` an Gradle, Kotlin, .NET und README (Mutation, Exit 2 mit Befund). Der Slice enthält sonst keine Code-Aussage.
  - *Handvektor:* keiner.
  - *Selbstkonsistenz der Spezifikation:* Die Menge der `SPEC-*`-Kennungen ist gleich (56 und 56), keine ADR-, Slice-, Wellen- oder `docs/`-Token in `spec/`, jede Zielzustand-Aussage gegen den gelesenen Quelltext (`build.gradle.kts`, `Dockerfile`, `Main.kt`, `Bootstrap.kt`, `TdsServer.kt`) geprüft; das ist Lesen, kein Ausführen.
  - *Festlegung:* die Zusage der Namen `SimulationBackend(catalog: SimCatalog)` und `SimCatalog(tables)`, die Spezifikation ohne YAML-Bibliothek in `lib/`, `Class-Path` als Komfort, die Wertebereiche der Eingänge des Testservers.
  - *Nicht gemessen:* ob die Bibliothek ohne die YAML-Bibliothek läuft, der Container-Teil von `SPEC-052` am Testserver (beides erst nach dem Umbau), und V-4 der Verifikation (Lesehilfe-Lücke, siehe Folge-Slices).
- **Was ging anders als geplant:** Der Nachzug führte die Spezifikation dem Code voraus und musste daher einen Zielzustand-Vermerk erfinden (Setzung des Implementers, Muster „Zielbild, nicht gebaut“). Die Fix-Runde nach dem Review ergänzte drei Vermerke und einen Verweis (F-1, F-2) und nahm den Planungsbegriff aus der Spezifikation (F-4). Die Verifikation fand, dass die Pflicht, die Vermerke nach dem Umbau zu entfernen, nur im Plan stand (V-1).
- **Steering-Loop-Eintrag:** Eine Spec, die dem Code vorausgeht, braucht in den Trägern des Umbaus die Nachweis-Pflicht, ihre Zielzustand-Vermerke zu entfernen; sonst bleiben sie als falsche Präsens-Aussagen stehen. Eingetragen in beide Umbau-Slices ([slice-testserver-herausloesen](../open/slice-testserver-herausloesen.md), [slice-jar-ohne-main-und-image](../open/slice-jar-ohne-main-und-image.md)) als Nachweis-Pflicht mit Zuordnung der Stellen, einmalig (1×, Slice-Zuschnitt, keine Regel in `AGENTS.md`, Gate oder Skill). Verkörpert ist damit keine Regel, der Eintrag trägt kein `liegt in`: gezählt, nicht verkörpert.
- **Über der Schwelle, nicht verkörpert, Entscheidung beim Nutzer:** `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` steht mit diesem Slice bei 4× (`slice-antwort-mehrteilig-output`, `slice-qa-sicherheit`, `slice-qa-anmeldedrosselung`, `slice-spec-nachzug-produktschnitt`; Befunde innerhalb des Slice: Review F-1 und F-2, Verifikation V-1; ein Vorgang zählt einmal). Den Lese-Schritt macht die Welle-Closure; hier wird nichts verkörpert.
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-spec-nachzug-produktschnitt.md` ergänzt in `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (4×); neu angelegt `BEO-TDS/folge-adr-betriebsweg-nach-produktschnitt-ungeklaert` (1×) und `BEO-TDS/zusage-container-teil-spec-052-ohne-test` (1×).
- **Folge-Slices:** keine neuen. V-1 steht als Nachweis-Pflicht ohne neuen Liefer-Punkt in [slice-testserver-herausloesen](../open/slice-testserver-herausloesen.md) (12 Stellen) und [slice-jar-ohne-main-und-image](../open/slice-jar-ohne-main-und-image.md) (2 Stellen); V-4 (`LOGIN_TIMEOUT`, `MAX_PRELOGIN_MESSAGE_BYTES`, `SHUTDOWN_GRACE`, `QUERY_TIMEOUT` nicht als Eingang des Testservers ausgezeichnet) steht als Lesehilfe-Hinweis im Nachweis von `slice-testserver-herausloesen`. V-5 (Start-Log in `SPEC-051`) gehört zu [slice-spec-nachzug-telemetrie](../open/slice-spec-nachzug-telemetrie.md).
- **Folge-ADR-Frage (Nutzer):** Braucht der Wegfall des Betriebswegs eine Folge-ADR zu [ADR-0011](../../adr/0011-pruefwertformat.md) und [ADR-0002](../../adr/0002-tls-und-verschluesselung.md)? Befund: kein Widerspruch in der Spezifikation; Entscheidung beim Nutzer.
- **Risiken aus §6:** siehe §6 (ein Risiko entfallen, eines eingetreten, fünf weiter offen mit Träger oder Register).
- **Drei Paarungen:** Anker: kein Eintrag mit `liegt in`, nichts zu paaren. Folge-Slice: V-1 und V-4 stehen in den DoDs der Umbau-Slices. Register: die drei Einträge oben liegen als Dateien vor. Die Welle-Closure prüft sie erneut.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration kennt nur `*` (Greenfield, Kürzel `TDS`); der Slice berührt `spec/` ohne eigene Strukturregel oder Inventur-Linie. Keine feinere Sub-Area.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`). Treffer: `BEO-TDS/spec-059-zusagen-vor-produktschnitt` (1×, offen; dieser Slice ist sein Träger), `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (3×, offen; Fundstellensuche in §2 und §3), `BEO-TDS/jackson-abhaengigkeit-fuer-embedder-nicht-gemessen` (1×, offen; die Spezifikation sagt „ohne Jackson“ erst als Festlegung, die Messung trägt `slice-testserver-herausloesen`), `BEO-TDS/spec-nachzug-nach-messung-offen` (1×, offen; Lesart der Setzungen beim Nachzug), `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung` (4×, offen; die Festlegungen dieses Slice haben Lastenheft-Bezug in `Bezug`). Nachgesehen am 2026-11-01 (nach [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md), [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md), [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md)): `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` (10×, offen; die Setzung `SimCatalog.find` `internal` und der Zuschnitt in zwei Spec-Slices sind Lesarten des Planners und stehen zur Bestätigung im Bericht), `BEO-TDS/offene-punkte-ohne-traeger` (verkörpert; die Offen-Punkte (a) bis (h) der [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) haben je einen Träger, die Spec-Seite ist `slice-spec-nachzug-telemetrie`). Sonst keine Treffer.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
