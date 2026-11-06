# Slice slice-oeffentliche-flaeche-verkleinern-und-belegen: Öffentliche Fläche auf das Nötige verkleinern und jeden zugesagten Namen über die öffentliche Fläche belegen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-betrieb-diagnose (Begründung in §6: die Telemetrie, deren Ereignismodell den größten Teil der Fläche stellt, wird in dieser Welle gebaut und von ihr weitergeführt; die Entscheidung zur Fläche steht vor deren Folge-Slices)

**Bezug:** [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit) (Scope: Modulgrenzen, Tests, Dokumentation), [TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend) (Einbettung durch einen Integrator), [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose) (Telemetrie als Teil der Fläche), [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) (Zusage der Fläche, Nachweis), [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) (Ereignismodell), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) (Bibliothek ist das Produkt, Testserver bindet nur die öffentliche Fläche)

**Berührte Spec-Stellen:** SPEC-059 (Tabelle „Zusage“, „Die Namen nach Package“) · SPEC-035 (nur falls `simulation`-Namen fallen) · `spezifikation.md` Abschnitte 4 und 5 (Ereignismodell) · Änderungsprotokoll

**Verantwortlich:** —

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-05.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Die öffentliche Fläche der Bibliothek enthält nur, was ein Einbettender zum Einsetzen der Bibliothek braucht, und jeder Name, den [SPEC-059](../../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059) zusagt, ist über die öffentliche Fläche in `src/integrator` oder im Testserver (`src/testserver/kotlin`) belegt; ein Sensor meldet Abweichungen in beiden Richtungen.

**Nutzerentscheidung (2026-11-05, wörtlich):** „Das soll eine API sein. Also nur die Klassen, die man wirklich dafür benötigt.“ Das entscheidet die **Richtung** zu Option (c) „Fläche schrumpfen“ des Registereintrags [BEO-TDS/oeffentliche-flaeche-der-telemetrie-ohne-entscheidung](../observations/BEO-TDS/oeffentliche-flaeche-der-telemetrie-ohne-entscheidung/observation.md) (Optionen (a), (b), (c) aus §6 von [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md)) im Prinzip. Sie entscheidet **nicht**, welche konkreten Namen bleiben: Der Schnitt ist Gegenstand von Liefer-Punkt 1 und braucht die Bestätigung des Nutzers, bevor Code geändert wird. Option (c) ändert die API (Modell, Abbildung, Tests je Art, Adapter-Slice) und muss vor einem Release fallen, weil sie danach die Zusage der Typnamen bräche.

**Kriterium je Name** (aus der Nutzerentscheidung abgeleitet, Setzung dieses Plans): Ein Name bleibt öffentlich, wenn ein Einbettender ihn für eine dieser Aufgaben braucht: Konfiguration, Start und Stop, Backend-Anbindung, Anmeldung und Benutzerliste, Entgegennehmen von Telemetrie. Ist er Implementierungsdetail, wird er `internal` oder entfällt (zusammengefasst).

**Befund der Ausgangsmessung (grobe Namenssuche vom 2026-11-05, kein Aufrufbeleg, keine Zusage nach `AGENTS.md` §3.8):** `SPEC-059` nennt 67 Namen (ohne `AuthAdapter`, `ConfigKeys`). Der Testserver verwendet 31 davon, der Einbettungstest 37. Von keinem der beiden verwendet werden 23: `AcceptFailed`, `AdapterFailed`, `BindFailed`, `ConnectionFailed`, `LoginRejected`, `RequestFailed`, `StartRefused`, `TlsFirstNotOffered`, `TlsHandshakeFailed`, `TlsStartRejected`, `SimulationBackendReady`, `StartRefusal`, `LoginRejectionReason`, `TlsFirstRefusal`, `EventLevel`, `QueryParameter`, `ParameterDirection`, `RowFilter`, `FilterTerm`, `SimQuery`, `UserListError`, `UserListErrorKind`, `Principal`. Grenzen: Die Suche prüft Namen, nicht Aufrufe; Unit-Tests mit `internal`-Zugriff können diese Namen belegen, das belegt nicht die Erreichbarkeit über die öffentliche Fläche; ein Name, der nur als Typ eines Rückgabewerts vorkommt, wird nicht gefunden, ist aber erreichbar. **Die Zahlen sind vor dem Start neu zu messen** (Drift: `slice-betrieb-log-adapter-testserver`, `slice-betrieb-zustandsereignisse` und andere Slices verändern Testserver, Tests und Modell).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Verhalten des Servers (Protokoll, Anmeldung, Abbruch) — der Slice ändert Sichtbarkeit und Zuschnitt von Namen, nicht, was der Server tut; jede Verhaltensänderung wäre ein anderer Vorgang mit eigenem Beleg.
- JSON-Adapter, `LOG_LEVEL` und Inspektion des laufenden Prozesses — [slice-betrieb-log-adapter-testserver](../open/slice-betrieb-log-adapter-testserver.md); er formatiert die Arten, die dieser Slice festlegt, und fängt deshalb nach ihm an (siehe §4).
- Verdrahtungstest für `AcceptFailed` und `ConnectionFailed`, Rot-Fälle der Ausgabe-Sperre, Lücke `Logger.log` — [slice-betrieb-telemetrie-verdrahtung-belegen](../open/slice-betrieb-telemetrie-verdrahtung-belegen.md); sein Belegobjekt ändert sich mit dem Schnitt, die Reihenfolge ist in §4 gesetzt.
- Neue Ereignisarten, `request_id`, `duration_ms` — [slice-betrieb-zustandsereignisse](../open/slice-betrieb-zustandsereignisse.md) und [slice-betrieb-metriken-tracing](../open/slice-betrieb-metriken-tracing.md); hier wird nur zugeschnitten, was gebaut ist.
- Änderungen am Lastenheft — die Fläche ist Sache der Spezifikation (Rang 2) und einer möglichen ADR (§6); das Lastenheft nennt keine Namen.
- Ein Folge-Slice zur `PRELOGIN`-Serverversion (Gedanke einer Folge für JDBC/`PRELOGIN`): in `open/` und in der Roadmap gibt es keinen solchen Slice; der Slice rührt das Protokoll nicht an, ein Verweis entfällt.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Schnitt bestätigt und umgesetzt** ([TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend)): Der Implementer misst die Ausgangszahlen neu und leitet den Schnitt nach dem Kriterium aus §1 aus dem Bestand ab. Er legt dem Nutzer eine Liste **bleibt / wird `internal` / entfällt (zusammengefasst)** mit Begründung je Gruppe vor; **vor der Bestätigung ändert er keinen Code.** Hauptkandidat zum Zusammenfassen ist das Ereignismodell (18 Arten, 4 Aufzählungen): etwa eine kleine Ereignisklasse mit Art statt je Art ein öffentlicher Typ, mit Blick auf die Überlappung der vier Abbruch-Arten mit `StartOutcome.Aborted`, auf `EncryptionWeakened` und `PlainTestModeActive` als eine Art und auf `SimulationBackendReady`. Das sind Kandidaten, keine Entscheidung. Nach der Bestätigung gilt der Schnitt im Code: Namen außerhalb der Liste sind `internal` oder entfallen; Abbildung, Tests je Art und Einbettungstest sind nachgezogen.
- [ ] **Sensor** (Klasse und Form offen, §6): Ein Sensor liest die Namensliste aus `SPEC-059`, die tatsächlich öffentlichen Deklarationen des Hauptmoduls (`explicitApi` Modus `strict`) und die Verwendungen in `src/integrator` und `src/testserver`. Er meldet (a) Namen der Liste ohne Beleg über die öffentliche Fläche, (b) öffentliche Deklarationen ohne Eintrag in der Liste, und bricht bei jedem Fund. **Beleg:** Mutation je Zweig macht ihn rot (ein zugesagter Name ohne Verwendung; eine neue öffentliche Deklaration ohne Eintrag; zusätzlich ein Fall, der grün bleiben muss). Seine Grenzen stehen in seinem Kopf und in der Closure-Notiz: Namenssuche ist kein Aufrufbeleg; im Bytecode ist `internal` öffentlich, der Sensor gilt für Kotlin-Quellen.
- [ ] **Spec und Doku nachgezogen:** `SPEC-059` (Tabelle „Zusage“, „Die Namen nach Package“) nennt genau die bestätigte Liste, der Satz „ob die Felder der Fälle … Teil der Zusage sind, ist offen“ ist durch die Entscheidung ersetzt; Abschnitte 4 und 5 der Spezifikation und das KDoc der betroffenen Typen folgen; das Änderungsprotokoll nennt die Änderung. Aussagen nur so weit, wie der Sensor oder ein Test sie trägt (`AGENTS.md` §3.8).

Nachweis-Pflichten für alle drei Punkte:

- [ ] Jeder neue Test trägt im KDoc den Block-Tag `@spec SPEC-059`; die Tests laufen in `make test` und sind grün.
- [ ] `make gates` grün; ist der Sensor ein Gate, ist er in der Sensors-Tabelle von `harness/README.md` mit Klasse und Bindung eingetragen und `make gate-zuordnung` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update: `SPEC-059` und Änderungsprotokoll (öffentlicher Vertrag berührt).
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben — der Eintrag `BEO-TDS/oeffentliche-flaeche-der-telemetrie-ohne-entscheidung` bekommt eine Evidence-Datei und seinen Ausgang; **kein Zaehler wird gesetzt**, er folgt aus den Dateien.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| Vorlage an den Nutzer (Liste bleibt / `internal` / entfällt) | neu | Schnitt nach dem Kriterium aus §1; Bestätigung vor Code (Liefer-Punkt 1) |
| `src/main/kotlin/io/github/pt9912/tdswire/domain/telemetry/` und Abbildung in `runtime` | refactor | Ereignismodell zusammenfassen oder `internal` setzen, je nach Bestätigung |
| weitere Packages des Hauptmoduls (`config`, `config.auth`, `domain`, `port`, `simulation`) | update | Sichtbarkeit der Namen ohne Beleg über die öffentliche Fläche |
| `src/integrator/` (Einbettungstest) | update | Belege der bleibenden Namen über die öffentliche Fläche |
| `src/test/kotlin/…` | update | Tests je Art folgen dem Zuschnitt; Unit-Tests mit `internal`-Zugriff belegen Verhalten, nicht Erreichbarkeit |
| Sensor (Skript unter `tools/` mit Fragment unter `tools/mk/`, Selbsttest) | neu | Vergleich Liste, Deklarationen, Verwendungen; Klasse und Eingangsliste nach [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) (§6) |
| `spec/spezifikation.md` (SPEC-059, Abschnitte 4 und 5, Änderungsprotokoll) | update | Rang 2; Zusage nennt die bestätigte Liste |
| `harness/README.md` §Sensors | update | Zeile des Sensors, falls Gate oder Werkzeug |

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Die Richtung ist entschieden (Nutzerentscheidung 2026-11-05, §1); ein Verantwortlicher ist eingetragen, und [slice-testserver-gradle-modul](../in-progress/slice-testserver-gradle-modul.md) liegt in `done/` (Reihenfolge vom Nutzer am 2026-11-06 entschieden: Modul-Umbau vor dem Schnitt der Fläche; der Sensor von Liefer-Punkt 2 liest den Pfad des Moduls und baut nicht für den alten Pfad; der Schnitt steht vor [slice-katalog-und-ddl-zustand](slice-katalog-und-ddl-zustand.md), [slice-schreibpfad-bulk-load](slice-schreibpfad-bulk-load.md) und [slice-schreibpfad-merge-output](slice-schreibpfad-merge-output.md), die Namen hinzufügen). Der konkrete Schnitt ist **kein** Start-Trigger, sondern Liefer-Punkt 1 mit Bestätigung des Nutzers vor Code. Der Slice beginnt vor einem Release und vor [slice-betrieb-log-adapter-testserver](../open/slice-betrieb-log-adapter-testserver.md) (der Adapter formatiert die Arten) und vor [slice-betrieb-telemetrie-verdrahtung-belegen](../open/slice-betrieb-telemetrie-verdrahtung-belegen.md) (seine Belegobjekte ändern sich); diese beiden Slices tragen die Kante als eigenen Start-Trigger, sobald dieser Plan angenommen ist (Nachzug beim Wechsel nach `next/`, nicht in diesem Plan).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte; Schnittvorschlag: Schnitt und Umsetzung | Sensor.
- `in-progress` → `open` (blockiert): der Nutzer bestätigt den Schnitt nicht oder verlangt Änderungen am Lastenheft; oder ein Release liegt vor der Umsetzung (dann bräche (c) die Zusage der Typnamen; neue Entscheidung nötig).

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün, der Review-Report liegt vor, der Sensor meldet auf dem Stand der Closure weder einen Namen der Liste ohne Beleg noch eine öffentliche Deklaration ohne Eintrag, und die Closure-Notiz trägt die Zuordnung *Test | Sensor | Festlegung* und den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Offene Nutzerentscheidung: der konkrete Schnitt.** Die Richtung (c) ist am 2026-11-05 im Prinzip entschieden; welche Namen bleiben, bestätigt der Nutzer an der Liste aus Liefer-Punkt 1. — **Ausgang:** offen bis zur Closure.
- **Braucht die Verkleinerung eine ADR?** Sie schreibt die Zusage von [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) fort (das Lastenheft bleibt unberührt); `AGENTS.md` §3.5 verbietet das Überschreiben einer `Accepted`-ADR, eine Änderung wäre eine neue ADR mit `Supersedes`, etwa als Teil-Ersatz wie bei [ADR-0026](../../adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md). Ob die Änderung von `SPEC-059` samt Änderungsprotokoll genügt oder eine ADR nötig ist, ist offen und mit dem Nutzer zu klären, bevor Code ändert. — **Ausgang:** offen bis zur Closure.
- **Sensor: Werkzeug oder Gate, und welche Klasse.** Als Gate wäre er in `make gates` (Klassen nach [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md); braucht er den Quelltext des Produkts, gehört er zu `tests`), als Werkzeug außerhalb (wie die E2E-Läufe, [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)). Ein Werkzeug ohne Gate lockert nichts, ein Gate, das später rot wird, verlangt Pflege; `AGENTS.md` §3.6 verbietet nur, ein bestehendes Gate zu lockern. Die Entscheidung trifft der Implementer mit Begründung in der Closure-Notiz, bei Zweifel der Nutzer. — **Ausgang:** offen bis zur Closure.
- **Grenzen des Sensors.** Namenssuche ist kein Aufrufbeleg (ein Name kann in einem Kommentar, einer Zeichenkette oder einem toten Aufruf stehen); im Bytecode ist `internal` öffentlich; Tests mit `internal`-Zugriff zählen nicht. Der Sensor belegt „im Quelltext der Einbettenden vorkommend“, nicht „erreichbar und wirksam“. — **Ausgang:** offen bis zur Closure.
- **Namen ohne Beleg, die kein Einbettender braucht, aber lesen muss.** Ein Adapter liest Felder und Konstanten zwangsläufig; wer Arten zusammenfasst, verschiebt den Wert in eine Art-Konstante und macht jede neue Art zum Bruch eines `when`. Das ist beim Schnitt zu benennen. — **Ausgang:** offen bis zur Closure.
- **Bereits entfernter Name: `StartRefusal.JVM_FLAG_MISSING`.** [slice-tls-d-migrate](../done/slice-tls-d-migrate.md) hat die Konstante des öffentlichen Enums `StartRefusal` entfernt ([ADR-0032](../../adr/0032-tls-obergrenze-je-pfad-und-ticket-flag.md), Folgepflicht; kein veröffentlichtes Artefakt, keine Versionspolitik). Die Zeile gehört in die Liste der Änderungen an der Fläche (Änderungsverlauf von `SPEC-059`) und ist bei der Neumessung der Ausgangszahlen zu beachten; ob und wie `StartRefusal` selbst im Schnitt bleibt, entscheidet dieser Slice mit dem Nutzer, hier ist nichts vorweggenommen. — **Ausgang:** offen bis zur Closure.
- **Möglicher neuer Name aus der Handle-Grenze.** [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md) (`Accepted` am 2026-11-06) setzt einen einstellbaren Grenzwert der Handles je Sitzung; ob er ein neuer öffentlicher Name ist oder eine bestehende Konfiguration trägt, ist dort offen (Offen 7) und wird in der Spezifikation mit diesem Slice abgestimmt. Der Slice berücksichtigt den Namen in seiner Liste. — **Ausgang:** offen bis zur Closure.
- **Welle.** Der Slice steht in `welle-betrieb-diagnose`, weil dort das Ereignismodell gebaut wird und die Folge-Slices von ihm abhängen; die Closure der Welle (alle Slices in `done/`) hängt damit an diesem Slice. Passt der Nutzer den Schnitt der Wellen an, ist „ohne Welle“ (Closure-Bedingung gleich der DoD) die Alternative. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); der Slice berührt Hauptmodul, Source-Sets `integrator` und `testserver`, Werkzeuge und Spezifikation, eine eigene Sub-Area besteht nicht.

**Vorgelagert — offene Beobachtungen sichten:** `BEO-TDS/oeffentliche-flaeche-der-telemetrie-ohne-entscheidung` (Gegenstand dieses Slice; Richtung entschieden, Schnitt offen), `BEO-TDS/aussagen-ueber-messung-hinaus` (die Zusage der Liste trägt nur so weit wie der Sensor; verkörpert), `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (Mutation je Zweig des Sensors; verkörpert). Zähler nicht gesetzt; sie folgen aus den Dateien im Register.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
