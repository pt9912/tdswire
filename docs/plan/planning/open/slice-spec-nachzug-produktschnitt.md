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

**Bezug:** [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung) (Einstieg), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) (Tabellenbeschreibung, Läufe); [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) (`Accepted`, Folgepflicht 1), [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) (Entscheidung 3, Fläche).

**Berührte Spec-Stellen:** SPEC-052 · SPEC-005 · SPEC-010 · SPEC-023 · SPEC-035 · SPEC-040 · SPEC-059 (Zusage-Zeile und Vertrag „Einstieg des Integrators“) · Änderungsprotokoll der Spezifikation.

**Verantwortlich:** — bis zur Priorisierung.

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

**Vorbedingung vor dem Start: Folge-ADR angenommen.** [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) lässt die Öffentlichkeit von `SimulationBackend` und `SimCatalog` offen (Offen-Punkt 1, Entscheidung des Nutzers; Empfehlung des Architect: beide öffentlich, als Folge-ADR zu Entscheidung 3 von [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md)). Die Fläche in `SPEC-059` und die Läufe in `SPEC-040` hängen daran. Die Folge-ADR ist ein ADR-Schritt vor diesem Slice und nicht sein Inhalt; dieser Slice schreibt keine ADR.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Das Lastenheft — es ist in 0.6.0 angepasst und steht im Rang über der Spezifikation; ein Widerspruch wäre ein Befund für den Nutzer, kein Anlass zu ändern.
- Code, Skripte, `Dockerfile`, `build.gradle.kts` und die Zeilen der Sensors-Tabelle in `harness/README.md` — sie folgen mit `slice-testserver-herausloesen` und `slice-jar-ohne-main-und-image`. Die Zeilen von `harness/README.md` beschreiben Targets im Ist-Zustand (`smoke`, `laufzeittest`, `build`, `skript-selbsttest`, `kopplung-zahlen`); vor dem Umbau wären sie falsch (`AGENTS.md` §3.8), und die ADR lässt sie dem Umbau folgen. Das weicht von der Zuordnung ab, die die Planungsanfrage diesem Slice gab, und steht als offene Frage im Bericht der Planung.
- Die Spezifikation des Logs und der Observability (Abschnitte 4 und 5) — [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) ist `Proposed`; der Nachzug nimmt kein Log der Bibliothek an, das nicht festgelegt ist.
- Eine Änderung von [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) (Format, Teilmenge, Typabbildung, Fehlerfälle der Tabellenbeschreibung) — sie gelten unverändert für die Datei des Testservers; der Slice verschiebt ihren Ort in der Aussage.
- Angenommene ADRs — sie bleiben unverändert (`AGENTS.md` §3.5). Ob [ADR-0011](../../adr/0011-pruefwertformat.md) (Benutzerliste über Umgebung und Datei) und [ADR-0002](../../adr/0002-tls-und-verschluesselung.md) (Schlüsselspeicher als Datei) eine Folge-ADR brauchen, stellt der Slice fest und meldet es; er entscheidet es nicht.
- `spec/architecture.md` ändert der Slice nur, wenn eine Aussage dort dem Schnitt widerspricht (Prüfung in §3); die Sicht nennt keine ADR, Welle oder Slice (`AGENTS.md` §3.4).

Spec-führend ist gewollt: Das Repo ist Greenfield (`harness/conventions.md`, Modus-Deklaration: Spec und ADRs führen, Code folgt). Zwischen diesem Slice und der Closure der Umbau-Slices sagt die Spezifikation früher als der Code; §6 führt das als Risiko.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Wortlaut und Zuschnitt folgen [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) Folgepflicht 1 (beim Übergang `open` → `next` gegen die ADR und die Folge-ADR zu Offen-Punkt 1 geprüft). Jede Aussage trägt ihren Beleg als Test oder ist als Festlegung gekennzeichnet (`AGENTS.md` §3.8); die Spezifikation nennt keine ADR-Bezüge in Sicht-Dokumenten (`AGENTS.md` §3.4).

Liefer-Punkte (drei):

- [ ] **Artefakt und Läufe** ([TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)): `SPEC-052` sagt: Bibliotheks-Jar `tdswire.jar` ohne `Main-Class`, Lizenz in `META-INF`, `Class-Path` auf `lib/` für Embedder, Manifest-Prüfung im Bau ohne Startklasse; Laufzeit-Image, Nutzer, Port, Konfiguration im Container und `JAVA_TOOL_OPTIONS` stehen nicht mehr als Produktaussagen da (sie entfallen oder sind Aussagen über den Testserver-Container des Laufs `make laufzeittest`). `SPEC-040` sagt: die Läufe (`make simulations-integration` und die Läufe mit Referenzclient) gehen gegen den Testserver; die Heap-Angabe des Integrationslaufs bleibt eine Angabe des Testservers (Wert unverändert, `make kopplung-zahlen` hält ihn).
- [ ] **Eingänge des Testservers** ([TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)): `SPEC-005` und `SPEC-010` nennen `LISTEN_PORT`, `LISTEN_HOST`, `ENCRYPTION_POLICY`, `TLS_KEYSTORE_FILE` und `TLS_KEYSTORE_PASSWORD_FILE` als Eingänge des Testservers; Wertebereiche, Paarung und Voreinstellungen bleiben Aussagen über `ServerConfig` und ihre Prüfung (`ConfigValidation.check`). `SPEC-023`: das JVM-Flag gilt für jeden Prozess, der die Bibliothek trägt, also Embedder und Testserver. `SPEC-035`: Datei und `SIMULATION_DESCRIPTION_FILE` gehören dem Testserver, die Kotlin-Schnittstelle (`BackendSource.Simulation(tables)`) bleibt, `simulationFile` und die Größengrenze von 1 MiB stehen nicht mehr bei der Bibliothek.
- [ ] **Einstieg ohne Zwischenstand** ([TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung)): `SPEC-059`: der Zwischenstand-Satz der Zusage-Zeile ist entfernt; die Zusage nennt weder `ConfigKeys` noch `ServerConfig.simulationFile` noch „ein Artefakt“ noch Jackson; die Zeile „Abhängigkeiten“ des Vertrags sagt Bibliothek und `lib/` ohne YAML-Bibliothek; die Fläche folgt der Folge-ADR zu Offen-Punkt 1. Eine Suche über `spec/spezifikation.md` und `spec/architecture.md` nach der Kernaussage (`Startklasse`, `MainKt`, `Laufzeit-Image`, `simulationFile`, `ein Artefakt`, `SIMULATION_DESCRIPTION_FILE`, `Serverprozess`, `Jackson`) findet nur noch Treffer, die den Testserver nennen oder Messungen am Leser der Beschreibung beschreiben; die Trefferliste steht in der Closure-Notiz (gesucht wird die Aussage, nicht die genannte Zeile).

Nachweis-Pflichten für alle Punkte:

- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update: Änderungsprotokoll der Spezifikation um eine Zeile ergänzt; `make spec-abdeckung` neu erzeugt und committet, falls eine `SPEC-*`-Kennung entsteht oder entfällt (erwartet: keine).
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo mit Wellen von der Welle-Closure geprüft.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `spec/spezifikation.md`, `SPEC-052` (Abschnitt 7, Festlegungen der Harness-Werkzeuge) | update | Bibliotheks-Jar ohne Startklasse, Wegfall der Produkt-Image-Aussagen, Manifest-Prüfung ohne Startklasse |
| `spec/spezifikation.md`, `SPEC-040` (Abschnitt 7) | update | Läufe gegen den Testserver; Heap-Angabe als Angabe des Testservers |
| `spec/spezifikation.md`, `SPEC-005`, `SPEC-010`, `SPEC-023` (Tabelle der Festlegungen, Sicherheitsprofil) | update | Umgebungsvariablen und Dateipfade als Eingänge des Testservers; JVM-Flag für Embedder und Testserver |
| `spec/spezifikation.md`, `SPEC-035` (Abschnitt „Tabellenbeschreibung und Zeilenquelle“) | update | Datei und Variable beim Testserver, `simulationFile` weg |
| `spec/spezifikation.md`, `SPEC-059` (Zusage-Zeile, Vertrag „Einstieg des Integrators“) | update | Zwischenstand-Satz entfernt; `ConfigKeys`, `simulationFile`, „ein Artefakt“, YAML-Bibliothek angepasst; Fläche nach Folge-ADR |
| `spec/spezifikation.md`, Änderungsprotokoll | update | eine Zeile je Nachzug |
| `spec/architecture.md` (`ARC-008`, Abschnitt der Schichten) | prüfen | nur ändern, wenn eine Aussage dem Schnitt widerspricht; ohne ADR-, Wellen- und Slice-Bezug |
| `docs/plan/spec-abdeckung.md` | update (erzeugt) | `make spec-abdeckung`, falls sich die Menge der Festlegungen ändert |

Fundstellensuche (Teil der Arbeit, nicht des Ergebnisses): `Startklasse`, `MainKt`, `Laufzeit-Image`, `JAVA_TOOL_OPTIONS`, `ConfigKeys`, `simulationFile`, `SIMULATION_DESCRIPTION_FILE`, `LISTEN_`, `TLS_KEYSTORE`, `AUTH_USERS_FILE`, `Serverprozess`, `Jackson` über `spec/`. Treffer außerhalb der sieben genannten Festlegungen (erwartet etwa `SPEC-043` TLS-Material, `SPEC-051` Benutzerliste) gehören in diesen Slice, wenn sie dieselbe Aussage tragen (`BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`), alle Bedingungen beobachtbar:

1. [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) steht im ADR-Index auf `Accepted` (erfüllt: Annahme am 2026-11-01).
2. **Vorbedingung „Folge-ADR angenommen“ (nicht erfüllt):** die Nutzerentscheidung zu Offen-Punkt 1 der ADR (Öffentlichkeit von `SimulationBackend` und `SimCatalog`) ist als Folge-ADR zu Entscheidung 3 von [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) im ADR-Index auf `Accepted`. Die Folge-ADR schreibt der Architect, nicht dieser Slice.
3. Der Start-Trigger von [welle-produktschnitt](../welle-produktschnitt.md) ist eingetreten (§2 der Welle).
4. Ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): die Fundstellensuche (§3) findet Festlegungen außerhalb der drei Liefer-Punkte, die dieselbe Aussage tragen und mehr als einen weiteren Punkt ergäben; Zerlegung nach Abschnitt der Spezifikation.
- `in-progress` → `open` (blockiert — Carveout?): die Folge-ADR wird mit der Variante (ii) oder (iii) angenommen (Offen-Punkt 1) und ändert die Fläche in `SPEC-059` und die Läufe in `SPEC-040` über das Geplante hinaus; oder [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) wird mit einem öffentlichen Log-Weg `Accepted` und verändert die Zusage-Zeile von `SPEC-059`; oder [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) wird `Superseded`.

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

- Die Spezifikation sagt früher als der Code (Greenfield, Spec führt): zwischen diesem Slice und `slice-testserver-herausloesen` stimmen `SPEC-035`, `SPEC-052`, `SPEC-059` nicht mit `src/main` überein; ein Leser des Quelltexts findet `simulationFile` und `MainKt` noch. — **Ausgang:** offen bis zur Closure.
- Festlegungen außerhalb der sieben genannten tragen dieselbe Aussage (etwa `SPEC-043`, `SPEC-051`, Sicherheitsprofil, Kompatibilitätsmatrix) und bleiben beim Nachzug stehen (`BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`, 3×). — **Ausgang:** offen bis zur Closure.
- Die Folge-ADR zu Offen-Punkt 1 wird anders entschieden als die Empfehlung (i); der Zuschnitt der Fläche in `SPEC-059` und der Läufe in `SPEC-040` ist dann neu zu lesen. — **Ausgang:** offen bis zur Closure.
- Ob [ADR-0011](../../adr/0011-pruefwertformat.md) und [ADR-0002](../../adr/0002-tls-und-verschluesselung.md) durch den Wegfall des Betriebswegs (Umgebung, Dateien) eine Folge-ADR brauchen, ist in [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) „nicht festgestellt“. — **Ausgang:** offen bis zur Closure.
- Eine Zusage der Spezifikation hat nach dem Nachzug keinen Test mehr, der sie trägt (etwa der Container-Teil von `SPEC-052`, dessen Tag `@spec` heute nur den Lizenztext nennt); `make spec-abdeckung` zeigt Waisen erst nach dem Umbau. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration kennt nur `*` (Greenfield, Kürzel `TDS`); der Slice berührt `spec/` ohne eigene Strukturregel oder Inventur-Linie. Keine feinere Sub-Area.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`). Treffer: `BEO-TDS/spec-059-zusagen-vor-produktschnitt` (1×, offen; dieser Slice ist sein Träger), `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (3×, offen; Fundstellensuche in §2 und §3), `BEO-TDS/jackson-abhaengigkeit-fuer-embedder-nicht-gemessen` (1×, offen; die Spezifikation sagt „ohne Jackson“ erst als Festlegung, die Messung trägt `slice-testserver-herausloesen`), `BEO-TDS/spec-nachzug-nach-messung-offen` (1×, offen; Lesart der Setzungen beim Nachzug), `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung` (4×, offen; die Festlegungen dieses Slice haben Lastenheft-Bezug in `Bezug`). Sonst keine Treffer.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
