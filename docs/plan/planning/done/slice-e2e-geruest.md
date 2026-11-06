# Slice slice-e2e-geruest: Produkt-E2E-Skript, das die vorhandenen Läufe bündelt, und die erzeugte E2E-Abdeckungs-Sicht

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage — die Welle belegt jede Qualitätsanforderung mit einer laufenden Messung; die Sicht zeigt, welche Anforderung noch keinen Lauf über die Prozessgrenze hat (Wahl des Planners, der Nutzer kann umlenken).

**Bezug:** [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-RB-02](../../../../spec/lastenheft.md#tds-rb-02--externer-client) (Scope: die Stufen des Gerüsts; die übrigen Anforderungen tragen die Folge-Stufen in §3)

**Berührte Spec-Stellen:** —

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-10-31.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Ein vom Repo gepflegtes Produkt-E2E-Skript `tools/e2e.sh` bündelt die vorhandenen Läufe, die den Serverprozess über die Prozessgrenze prüfen, als Stufen mit je einer Kennungs-Deklaration; `make e2e-abdeckung` erzeugt daraus `docs/plan/e2e-abdeckung.md`, und jede Anforderung des Lastenhefts ohne Stufe ist in dieser Sicht als Lücke lesbar (das Waisen-Urteil fällt der Leser, nicht der Erzeuger).

**Warum.** Die Sicht enthält heute allein die Zeile der Harness-Selbstprüfung (`tools/harness/selbstpruefung.sh`, Kennung `—`); ein Produkt-E2E ist nicht deklariert, obwohl `make gates` schon Läufe über die Prozessgrenze führt. Eine Anforderung gilt hier als *Ende-zu-Ende belegt*, wenn ein Lauf den Serverprozess über den Zugangsweg prüft, den die Anforderung nennt (Referenzclient oder Rohbytes-Client gegen den Prozess) — nicht, wenn ein Test im Modul mit `internal`-Zugriff sie prüft. Die Bestandsaufnahme steht in den Tabellen in §3 und in den E2E-Punkten der DoDs der Träger-Slices.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Neue Läufe oder Szenarien (Referenzclient-Szenarien, Cancel, TLS am Prozess, Last) — die Folge-Stufen in §3 gehören den Träger-Slices, weil jeder dieser Läufe Produktverhalten voraussetzt, das dort entsteht; dieser Slice bündelt nur Bestand.
- Die Entscheidung, ob das Voll-E2E als treibende Kraft für DoDs gilt, ob `make e2e` ein Gate wird und ob die Sicht mehrere Quellen führt — das sind Entscheidungen, die eine ADR braucht (offene Fragen im Bericht der Planung, ein Architect schreibt sie); bis dahin ist `make e2e` kein Gate, und der Slice setzt keine davon voraus.
- Änderungen am Erzeuger `tools/harness/e2e-abdeckung.sh` und am Fragment `harness/mk/e2e-abdeckung.mk` — beide gehören dem Werkzeug und werden bei jedem Lauf kanonisch neu geschrieben; gesetzt wird allein in `repo.mk`.
- Lizenz ([TDS-RB-05](../../../../spec/lastenheft.md#tds-rb-05--lizenz)) und Wartbarkeit ([TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit)) — der Nachweis ist eine Datei bzw. eine Matrix, kein Lauf über die Prozessgrenze; ihre Zeilen in der Sicht fehlen zu Recht.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [x] **Skript und Marker:** `tools/e2e.sh` (Präfix `e2e`, Funktion `e2e_abdeckung` vor der ersten Stufe) führt die Stufen aus §3 nacheinander aus (sieben Stufen: Stufe 4 des Plans entfällt, die Stufen 4 bis 7 sind die Referenzclient-Läufe von `make kompatibilitaet`, siehe Tabelle) (ein Docker-Lauf gleichzeitig, Abbruch an der ersten roten Stufe); `repo.mk` setzt `E2E_ABDECKUNG_QUELLE`, `E2E_ABDECKUNG_PRAEFIX` und `E2E_ABDECKUNG_SPEC`; `make e2e-abdeckung` schreibt die Sicht mit den Kennungen der Stufen, und jede Kurzbeschreibung nennt die Grenze ihres Belegs (`AGENTS.md` §3.8).
- [x] **Aufruf und Laufzeit:** Ein Target `make e2e` (Fragment unter `tools/mk/`) mit Zeile in `harness/README.md` §Sensors ruft das Skript; die Laufzeit des Durchgangs mit sieben Stufen ist gemessen, mit Datum, Last und Cache-Zustand (§7). **Abweichung vom ursprünglichen Wortlaut („warm und kalt gemessen und mit der Summe der vier einzelnen Läufe verglichen“) auf Nutzerentscheidung vom 2026-11-05:** Warmwert, Kaltwert und der Vergleich mit der Summe der Einzelläufe der sieben Stufen sind nicht gemessen und als weiter offen geführt, Träger [BEO-TDS/e2e-nachweis-ohne-stufe-oder-messung](../observations/BEO-TDS/e2e-nachweis-ohne-stufe-oder-messung/observation.md). `make e2e` steht nicht in `GATE_CHECKS`; `laufzeittest` und `simulations-integration` verlassen die Gate-Kette ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md), angenommen am 2026-10-31), und `.github/workflows/e2e.yml` ruft `make e2e`. **Mit Einschränkung abgehakt:** gemessen ist ein Durchgang (317 s); Warm-, Kaltwert und Vergleich mit den Einzelläufen sind weiter offen (Nutzerentscheidung 2026-11-05, §7).
- [x] **Übergabe der Lücken:** Die Folge-Stufen-Tabelle in §3 stimmt bei der Closure mit den E2E-Punkten in den DoDs der genannten Träger-Slices überein; ein Träger in `done/` ohne seine Stufe ist ein Befund dieses Slice, kein stilles Auslassen. **Mit Einschränkung abgehakt:** FA-08/RB-04 durch Folge-Slice, FA-10 und QA-02 (Beenden) durch Register-Einträge getragen, nicht geschlossen (§7).
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist. Kein öffentlicher Vertrag berührt (nur Planungsdokumente).
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `tools/e2e.sh` | neu | sieben Stufen (1 bis 3 und die Referenzclient-Läufe 4 bis 7) und die Funktion `e2e_abdeckung`; Pfad im `tools/`-Baum des Repos, nicht in `tools/harness/` |
| `repo.mk` | update | `E2E_ABDECKUNG_QUELLE`, `E2E_ABDECKUNG_PRAEFIX`, `E2E_ABDECKUNG_SPEC` mit einfachem `=`; `E2E_ABDECKUNG_ZIEL` steht schon |
| `tools/mk/e2e.mk` | neu | Target `make e2e`; Zeile in `harness/README.md` §Sensors |
| `docs/plan/e2e-abdeckung.md` | update (erzeugt) | `make e2e-abdeckung`; nicht von Hand |

| Stufe (Präfix `e2e:`) | Ruft | Deklarierte Kennungen | Zugangsweg | Belegt — und nicht |
|---|---|---|---|---|
| 1 Rohbytes-Login und Startverhalten des Prozesses | `make smoke` | [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-FA-02](../../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose) | außerhalb: Kindprozess `main`, Rohbytes-Client (kein Referenzclient) | PRELOGIN/LOGIN7 mit LOGINACK; Start ohne JVM-Flag bricht ab; fehlerhafte Konfiguration und belegter Port beenden den Start mit Exit-Code 1. Nicht: Referenzclient, TLS, Anfragepfad, Inhalt der Diagnose (nicht geprüft) |
| 2 Container-Artefakt | `make laufzeittest` | [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit) | außerhalb: Container, Rohbytes-Client aus zweitem Container, `docker stop` | Image startet, Login (`TEST_UNENCRYPTED`) gelingt, Stop mit Exit-Code 143; die Ausgabe nennt weder Benutzer noch Passwort (nur der Erfolgsfall, nicht TLS-Modus oder Fehlpfade). `TDS-QA-06` nur der Docker-Teil, nicht Clean-Build in der CI |
| 3 Referenzclient gegen Simulations-Backend | `make simulations-integration` | [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-03](../../../../spec/lastenheft.md#tds-fa-03--einfache-sql-batches), [TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände) | außerhalb: `Microsoft.Data.SqlClient` gegen den Serverprozess; der Prozess startet über `TdsServer.start` (Source-Set `testserver`, Tabellen mit Zeilenquellen aus `IntegrationServer.kt`, nur öffentliche Fläche der Bibliothek) | Auswahl mit Bedingung, leeres Ergebnis, Zeilenquelle über dem Heap, Fehler der Zeilenquelle als `SqlException` mit nutzbarer Verbindung (ohne Parameter, ohne TLS, ohne Pool). Nicht: parametrisierte Abfrage (RPC), Cancel, Anweisung außerhalb der erkannten Formen |
| Plan-Stufe 4 entfällt: Gate | `make referenztest-dotnet` bleibt Gate (`GATE_CHECKS`) | — | — | Nutzerentscheidung 2026-11-01: Aussage (Testprojekt baut gesperrt und läuft, Smoke gegen unbelegten Port) ist keine Aussage über die Prozessgrenze; `tools/e2e.sh` führt sie nicht mehr, die folgenden Stufen rücken nach. [TDS-RB-02](../../../../spec/lastenheft.md#tds-rb-02--externer-client) trägt die Szenario-Stufe |
| 4 bis 7 (Bestand) | `make kompatibilitaet KATEGORIE=Szenario`, `Anmeldung`, `Luecken`, `Synchronitaet` | Kennungen je Stufe: erzeugte Sicht `docs/plan/e2e-abdeckung.md` | außerhalb: `Microsoft.Data.SqlClient` im zweiten Container gegen `kompatibilitaetsserver` (Source-Set `testserver`) | Belegt und nicht belegt: „Nicht belegt“ der Deklaration in `tools/e2e.sh`; die Tabelle wiederholt es nicht (Stand-Angaben nicht an zwei Orten, `BEO-TDS/stand-angaben-in-matrix-und-slice-plan-driften`) |

**Folge-Stufen** — Anforderungen ohne Stufe oder mit einer Stufe, die nur einen Teil ihrer Akzeptanzkriterien trägt, und der Slice, dessen DoD die Stufe verlangt (Stand 2026-11-05 gegen die erzeugte Sicht `docs/plan/e2e-abdeckung.md` mit sieben Stufen; Beleg sind die DoDs der Träger und die Sicht, diese Tabelle ist der Zeiger):

| Kennung | Lücke | Träger |
|---|---|---|
| [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen) | geliefert: Stufe 4 deklariert FA-04 und FA-05 (parametrisierte Abfrage, alle acht Typen von SqlType), Stufen 5 und 6 FA-04 (Prepare, RPC-Parametertypen, xml, mehrere RPCs); Rest steht im „Nicht belegt“ der Stufen | [slice-qa-kompatibilitaet](../done/slice-qa-kompatibilitaet.md) |
| [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-RB-02](../../../../spec/lastenheft.md#tds-rb-02--externer-client) | geliefert: Stufen 4 bis 7 deklarieren beide (Szenarien Anmeldung, SELECT, RPC, NULL, Fehler am Referenzclient); offen bleibt der echte SQL Server (Zeile Referenzabgleich) | [slice-qa-kompatibilitaet](../done/slice-qa-kompatibilitaet.md) |
| [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit) (TLS-Modus) | geliefert: Stufe 4 (TDS 7.4 mit TLS, Kette und Name geprüft) und Stufe 5 (TDS 8.0) deklarieren QA-03; Rest („Nicht belegt“ der Stufe 5: TLS 1.2 und andere Suiten) bleibt Grenze | [slice-qa-kompatibilitaet](../done/slice-qa-kompatibilitaet.md) |
| [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) (ATTENTION), [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) | Der Server beantwortet ATTENTION während einer Anfrage heute nicht; kein Cancel- oder Folgeabfrage-Lauf | [slice-cancel-referenzclient-szenario](../done/slice-cancel-referenzclient-szenario.md) (Abbruchpfad im Server: [slice-attention-cancel-timeout](../done/slice-attention-cancel-timeout.md)), [slice-qa-synchronitaet](../done/slice-qa-synchronitaet.md) |
| [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) (Beenden, abrupter TCP-Abbruch), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung) (beschädigtes PRELOGIN) | Stufe 2 stoppt ohne laufende Anfrage; Stufe 6 deklariert QA-02 nur für die Lücken 3, 4, 13 und 14 der Kompatibilitätsmatrix; Stabilität (Fuzz, Sturm, Fragmentierung) läuft im Modul | [slice-abbruch-ressourcen-shutdown](../done/slice-abbruch-ressourcen-shutdown.md) (Zuschnitt-Entscheidung des Planners: sonst trägt kein offener Slice QA-02 am Prozess) |
| [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose) (Zustandsereignisse im Log) | Stufe 1 belegt nur Start und Exit-Code; Ereignisse am Prozess fehlen | [slice-betrieb-zustandsereignisse](../open/slice-betrieb-zustandsereignisse.md) |
| [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) (Anweisung außerhalb der erkannten Formen, Änderungsanweisung) | geliefert: Stufe 4 deklariert FA-11 nicht, nennt aber Einfügen, Ändern, Löschen mit Zeilenzahl und „außerhalb der Formen“ am Referenzclient; die Kennung steht dort nicht, die Aussage trägt der Lauf | [slice-sim-aenderung-integration](../done/welle-simulations-backend/slice-sim-aenderung-integration.md) |
| [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte) | mehrteilige Antworten und Ausgabewerte: kein Lauf, weil das Simulations-Backend sie nicht liefert (Stufe 4 „Nicht belegt“) | Register [BEO-TDS/fa-10-mehrteilige-antwort-ohne-backend-traeger](../observations/BEO-TDS/fa-10-mehrteilige-antwort-ohne-backend-traeger/observation.md); [slice-qa-kompatibilitaet](../done/slice-qa-kompatibilitaet.md) in `done/` führt keine Stufe, ein Träger der Backend-Seite ist nicht benannt (Ausgang in §7) |
| [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance) | Lasttest fehlt | [slice-qa-performance](../open/slice-qa-performance.md) |
| [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend) | Die Sicht deklariert beide Kennungen in keiner Stufe. Bestand: Stufe 4 (und 5 bis 7) läuft gegen `kompatibilitaetsserver` (Source-Set `testserver`, ohne Assoziation mit dem Hauptmodul, `BackendSource.Custom` mit `RecordingBackend`), also gegen ein über die öffentliche Fläche angebundenes Backend im Serverprozess mit Referenzclient; `RecordingBackend` delegiert aber an das Simulations-Backend, Backend-Fehler bei offener Verbindung liefern die Stufen 3, 4 und 7 (50004, `TDS_BACKEND_ERROR`, Folgeabfrage gelingt), aber über die Simulation; es fehlt ein eigenes `QueryBackend`, dessen `execute` scheitert oder das nicht erreichbar ist, mit der Prüfung, dass der Server verfügbar bleibt ([TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung) Negative). Grenze: `internal` ist im Bytecode öffentlich, der Nachweis gilt für Kotlin-Quellen im Source-Set; ein Verbraucher außerhalb des Gradle-Projekts gegen das gebaute Jar besteht nicht | [slice-e2e-backend-ausserhalb-modul](../in-progress/slice-e2e-backend-ausserhalb-modul.md) (Folge-Slice; Vorgänger-Träger [slice-integrations-einstieg](../done/slice-integrations-einstieg.md) in `done/` führt keinen E2E-Punkt) |
| [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität) (Referenzabgleich) | vorläufig, kein Lauf | [slice-qa-vergleichslauf-d](../done/slice-qa-vergleichslauf-d.md) (Werkzeug, kein Gate; keine Stufe verlangt) |

**Ansatz.** Das Skript führt jede Stufe als `make <Target>` und deklariert sie mit `e2e_abdeckung "<Kennungen>" "<Kurzbeschreibung>" "<Anker>"`, der Anker ein Ausschnitt der `make`-Zeile derselben Stufe. Die Sicht ist eine Deklaration, kein Lauf-Beleg (Kopf des Erzeugers): dass eine Stufe ihre Kennungen trägt, belegt der grüne Lauf der Stufe, nicht die Datei. Ob die Selbstprüfung des Harness als fünfte Stufe mit Kennung `—` im Skript steht oder aus der Sicht ausscheidet, ist die offene Frage zu mehreren Quellen (der Erzeuger liest genau eine Quelle mit einem Präfix).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `make gates` ist auf `main` grün. Die vier Läufe der Stufen existieren; keine ADR ist Voraussetzung, weil der Slice nichts Gate-Pflichtiges entscheidet.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Der Erzeuger lehnt das Skript ab, und die Ablehnung lässt sich nicht in `tools/e2e.sh` lösen (eine Änderung am Erzeuger wäre Sache des Werkzeugs).

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün, `make e2e-abdeckung` meldet „unveraendert“ über der eingecheckten Sicht, und die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Eine Stufe deklariert mehr, als ihr Lauf belegt (Kennung breiter als der Test) — die Sicht ist eine Deklaration, und der Erzeuger prüft den Text, nicht den Lauf. Gegenmittel: jede Kurzbeschreibung nennt, was nicht belegt ist; der Review gleicht sie gegen den Lauf ab (verwandt: `BEO-TDS/test-belegt-nur-den-fake`, `BEO-TDS/aussage-ueber-schichtgrenze-ohne-test`). — **Ausgang:** weiter offen: → `BEO-TDS/aussage-ueber-schichtgrenze-ohne-test` im Register. Dieser Slice hat keine Deklaration geändert; `make skript-selbsttest` hält nur das Vorhandensein von „Nicht belegt“, ob eine Kennung breiter ist als ihr Test, prüft allein der Review, und Review und Verifikation dieses Slice haben die Deklarationen nicht gegen den Lauf abgeglichen.
- `make e2e` als Gate führte die Stufen ein zweites Mal aus (`make smoke` läuft zudem in `make test`); die Messung in §7 zeigt die Mehrkosten. — **Ausgang:** entfallen: [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) hat die E2E-Läufe aus `GATE_CHECKS` genommen, `make e2e` ist kein Gate; die Kosten des Durchgangs stehen in §7 (317 s und 360 s bei hoher Last, Warm- und Kaltwert weiter offen im Register).
- Der Erzeuger kennt eine Quelle und ein Präfix; die Harness-Selbstprüfung erscheint nur, wenn das Produkt-Skript sie aufruft. — **Ausgang:** entfallen: die Selbstprüfung ist aus der Sicht gefallen (Option a, Setzung in §7), `tools/e2e.sh` führt sie nicht, die Sicht hat eine Quelle und ein Präfix; die Frage „mehrere Quellen“ stellt sich für diesen Slice nicht.
- Die Zuordnung von `TDS-QA-02` am Prozess an `slice-abbruch-ressourcen-shutdown` ist eine Wahl des Planners; trägt der Slice sie nicht, braucht der Gegenstand einen eigenen Slice. — **Ausgang:** weiter offen: → `BEO-TDS/e2e-nachweis-ohne-stufe-oder-messung` im Register. Stufe 6 deklariert QA-02 für vier Lücken der Matrix; der Träger [slice-abbruch-ressourcen-shutdown](../done/slice-abbruch-ressourcen-shutdown.md) führt QA-02 im Bezug und Disconnect-Fälle in der DoD, als Lauf über die Prozessgrenze aber nur den Rohbytes-Smoke mit Prozess-Stop, keinen Referenzclient-Lauf; Beenden und abrupter TCP-Abbruch am Prozess sind damit nicht belegt.

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

**Gegenstand:** geliefert mit Einschränkungen: Skript `tools/e2e.sh`, Target `make e2e` und erzeugte Sicht bestehen (sieben Stufen); Warmwert, Kaltwert und Vergleich mit den Einzelläufen sind nicht gemessen (DoD 2, Nutzerentscheidung vom 2026-11-05). Commits des Zweigs `slice-e2e-geruest` auf `main` 3cfdd01: `b27e292` (Messung des Implementers, Folge-Stufen-Zeile), `4ac95e6` (Folge-Slice), `08ff794` (DoD 2 auf sieben Stufen, Messung des Planners), `c012ede` (Review-Befunde F-1 bis F-6), danach der Commit dieser Closure. Das Skript und die Targets entstanden vor diesem Zweig (die Basis `main` führt sie schon); der Zweig ändert nur Planungsdokumente. Berichte: [Review](../../../reviews/2026-11-05-08ff794-slice-e2e-geruest.md) (kein HIGH, drei MEDIUM, F-1 bis F-6 eingearbeitet), [Verifikation](../../../reviews/2026-11-05-c012ede-verifikation-slice-e2e-geruest.md) (noch nicht closure-fähig, kein HIGH, zwei MEDIUM, ein LOW, Bedingungen B-1 bis B-4).

| Bedingung der Verifikation | Ausgang |
|---|---|
| B-1 DoD 2 messen oder Wortlaut ändern | Wortlaut geändert, sichtbar in DoD 2 und unter „Abweichung vom Wortlaut“ weiter unten (Nutzerentscheidung 2026-11-05: auf sieben Stufen anpassen, einmal messen, kalt bleibt „nicht gemessen“). Gemessen: ein Durchgang, 317 s. Warm, kalt und Vergleich: weiter offen, Träger [BEO-TDS/e2e-nachweis-ohne-stufe-oder-messung](../observations/BEO-TDS/e2e-nachweis-ohne-stufe-oder-messung/observation.md) (kein neuer Slice). |
| B-2 Folge-Stufen-Tabelle gegen die Sicht, FA-10 und FA-07 | Tabelle in §3 gegen `docs/plan/e2e-abdeckung.md` nachgezogen (FA-04/FA-05, QA-01/RB-02, QA-03, FA-11, QA-02 sind in den Stufen 4 bis 6 deklariert bzw. getragen). **FA-10:** Befund: Träger [slice-qa-kompatibilitaet](../done/slice-qa-kompatibilitaet.md) liegt in `done/` ohne Stufe; Ausgang: Register-Eintrag [BEO-TDS/fa-10-mehrteilige-antwort-ohne-backend-traeger](../observations/BEO-TDS/fa-10-mehrteilige-antwort-ohne-backend-traeger/observation.md) (besteht, Ursache ist die Backend-Seite: das Simulations-Backend liefert keine mehrteiligen Antworten; ein Träger dort ist nicht benannt; ein Folge-Slice für die Stufe allein wäre ohne diesen Träger nicht lieferbar). **FA-07 (Beenden, abrupter TCP-Abbruch):** Träger [slice-abbruch-ressourcen-shutdown](../done/slice-abbruch-ressourcen-shutdown.md) liegt in `open/`, kein Befund der `done/`-Art; sein Lauf ist nur der Rohbytes-Smoke, siehe Risiko 4 in §6. **FA-07 (ATTENTION):** Träger [slice-cancel-referenzclient-szenario](../done/slice-cancel-referenzclient-szenario.md) in `open/`. |
| B-3 Fehlpfad-Aussage, Stufenzahlen | Fehlpfad in §3 und im Folge-Slice §1 auf den Bestand eingeschränkt (Stufen 3, 4, 7 liefern Backend-Fehler 50004 bei offener Verbindung; es fehlt ein eigenes `QueryBackend`, dessen `execute` scheitert oder das nicht erreichbar ist, mit Prüfung der Verfügbarkeit). „Stufen 1 bis 4“ und „drei Plan-Stufen“ auf sieben Stufen gebracht (Dateitabelle, DoD 1, Stufentabelle). |
| B-4 Closure-Pflichten | Diese Notiz, §6 mit Ausgängen, Register, Haken. |

**Messung des Durchgangs (DoD 2):** Einzelmessung 2026-11-05, ein Lauf `make e2e`, 317 s (Stufen 76 s, 23 s, 99 s, 60 s, 17 s, 20 s, 22 s) bei Last 20, Cache nicht kontrolliert; zweite Einzelmessung des Implementers 360 s. Einordnung und Streuung stehen in den Absätzen darunter.

**Gates:** `make docs-check` und `make gates` grün auf dem Stand dieser Closure (Exit 0). Der Zweig ändert keine Produktionsdatei.

**Grenzen (nicht belegt):** Warmwert, Kaltwert und Vergleich mit den Einzelläufen für sieben Stufen; `make e2e` auf dem Stand der Closure ist nicht erneut gefahren (die Messung stammt von `3cfdd01` mit denselben Quellen, der Zweig ändert nur Markdown); ob eine Kennung der Sicht breiter ist als ihr Test, ist von Review und Verifikation nicht geprüft worden (Risiko 1).

**Steering-Loop-Eintrag:** gezählt, nicht verkörpert: Ein Slice-Plan, der einen Zuschnitt (Anzahl der Stufen, Messung warm und kalt) festschreibt, wird vom Bestand überholt, bevor er schließt; die Folge-Stufen-Tabelle driftete gegen die erzeugte Sicht, und die Messung galt für einen Zuschnitt, den es nicht mehr gab. Auslöser: `BEO-TDS/stand-angaben-in-matrix-und-slice-plan-driften` (jetzt 2×, Evidence `slice-e2e-geruest`) und die neue Klasse `BEO-TDS/e2e-nachweis-ohne-stufe-oder-messung` (1×).

**Beobachtungs-Register (`../observations/`):** `BEO-TDS/e2e-nachweis-ohne-stufe-oder-messung/` neu angelegt, Beleg `evidence/slice-e2e-geruest.md` (1×); `evidence/slice-e2e-geruest.md` in `BEO-TDS/stand-angaben-in-matrix-und-slice-plan-driften/` ergänzt, Zähler steht damit bei 2×.

**Folge-Slices:** [slice-e2e-backend-ausserhalb-modul](../in-progress/slice-e2e-backend-ausserhalb-modul.md) (E2E-Stufe für ein eigenes Backend und seinen Fehlpfad; Träger von FA-08 und RB-04) — Datei in `open/`, in der Tabelle von `welle-qualitaet-grundlage` eingetragen.

**Risiken aus §6:** alle vier tragen einen Ausgang (Risiken 1 und 4 weiter offen im Register, 2 und 3 entfallen).

**Drei Paarungen:** Anker: kein Anker gesetzt, weil nichts verkörpert wurde. Folge-Slice: `slice-e2e-backend-ausserhalb-modul` liegt in `open/`. Register: beide Evidence-Dateien liegen. Die Paarung im Repo mit Wellen-Betrieb prüft die Welle-Closure; das Häkchen bleibt offen.

**Messung des Implementers (Einzelmessungen am 2026-10-31, je ein Lauf, keine Streuung):**

| Lauf | Zeit |
|---|---|
| `make e2e` warm (zweiter Lauf, Layer-Cache vorhanden) | 46 s (Stufen 2 s, 21 s, 21 s, 2 s) |
| die vier Targets einzeln, warm, nacheinander | 3 s, 20 s, 21 s, 1 s, zusammen 45 s |
| `make e2e` kalt (frischer buildx-Builder ohne Cache, danach entfernt) | 323 s (Stufen 106 s, 108 s, 104 s, 5 s) |
| `make e2e` nach Quelltext-Änderung (Gradle-Layer neu, Basis-Images im Cache) | 187 s (Stufen 54 s, 63 s, 69 s, 1 s) |
| `make gates` warm mit `laufzeittest` und `simulations-integration` | 49 s |
| `make gates` warm ohne beide (Stand nach der ADR zu den E2E-Läufen) | 9 s (alle Stages aus dem Cache; Einzelwert, kein Maß für einen Lauf nach Quelltext-Änderung) |

**Altstand 2026-10-31, vier Stufen (gilt nicht für den heutigen Bestand mit sieben Stufen):** Der Durchgang kostet warm so viel wie die Summe der Einzelläufe (Differenz 1 s); die Stufen laufen nicht doppelt gegenüber ihrem Einzellauf, sondern doppelt gegenüber der Zeit, die `make gates` bisher für zwei von ihnen aufwendete. Die Kaltzeiten der Stufen 1 bis 3 liegen bei je rund 105 s (106 s, 108 s, 104 s); ob und warum sie unter den früheren Einzel-Kaltwerten (88 bis 171 s, 131 s) liegen, ist nicht gemessen; die Summe der vier Einzel-Kaltläufe ist nicht gemessen. Setzungen: der Durchgang bricht an der ersten roten Stufe ab; die Harness-Selbstprüfung fällt aus der Sicht (Option a), sie bleibt Werkzeug ohne Gate.

**Messung des Implementers am 2026-11-05 (sieben Stufen, ein Lauf, Zweig `worktree-agent-a202fbb3da2b7c48b`, Cache nicht kontrolliert; die Last 15 ist eine Angabe des Implementers ohne Beleg im Repo und nicht nachprüfbar):** `make e2e` 360 s (Stufen 58 s, 113 s, 57 s, 60 s, 24 s, 24 s, 24 s). Vergleich mit der Summe der Einzelläufe für sieben Stufen nicht gemessen; kalt nicht gemessen.

**Befund zu DoD 3 (Übergabe der Lücken):** [slice-integrations-einstieg](../done/slice-integrations-einstieg.md) ist in `done/` ohne E2E-Punkt; [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung) und [TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend) deklarieren in der Sicht keine Stufe; der Bestand trägt die Anbindung teilweise (Zeile in §3). **Ausgang:** Träger der Lücke ist der Folge-Slice [slice-e2e-backend-ausserhalb-modul](../in-progress/slice-e2e-backend-ausserhalb-modul.md) (Welle welle-qualitaet-grundlage); die Zeile in der Folge-Stufen-Tabelle in §3 nennt ihn und grenzt ihn gegen Stufe 4 ab. Die Lücke ist damit benannt und getragen, nicht geschlossen: bis dieser Slice in `done/` liegt, deklariert keine Stufe FA-08 und RB-04.

**Messung des Planners am 2026-11-05 (sieben Stufen, ein Lauf, Zweig `slice-e2e-geruest` auf `main` 3cfdd01, frischer Worktree):** `make e2e` 317 s (Stufen 76 s, 23 s, 99 s, 60 s, 17 s, 20 s, 22 s; Summe der Stufenzeiten gleich Gesamt). Last (`uptime`) 20 vor dem Lauf, 19,5 danach (Lastmittel 1 min, 20 bzw. 19,50). Cache-Zustand: nicht kontrolliert, Docker-Layer des Hosts waren vorhanden (die Zahl der als `CACHED` gemeldeten Schritte im Lauflog ist nicht abgelegt und nicht nachprüfbar). Das ist ein Wert unter hoher Last mit nicht kontrolliertem Cache, weder ein Warm- noch ein Kaltwert. Kalt nicht gemessen. Der Vergleich mit separat gestarteten Einzelläufen der sieben Stufen ist nicht gemessen; die Stufenzeiten sind die Zeiten der Targets innerhalb des Durchgangs. Gegenüber der Messung des Implementers desselben Tages (360 s) liegt dieser Lauf 43 s niedriger (Gesamt −12 %); Stufe 1 war 18 s langsamer (76 s gegen 58 s), Stufe 2 90 s schneller (23 s gegen 113 s), Stufe 3 42 s langsamer (99 s gegen 57 s), Stufe 5 7 s schneller (17 s gegen 24 s); die Streuung zwischen zwei Läufen ist damit je Stufe groß und aus zwei Einzelläufen nicht zu beziffern.

**Abweichung vom Wortlaut (DoD 2):** Die DoD sprach von „der Summe der vier einzelnen Läufe“. Der Bestand hat sieben Stufen (Stufe 4 des Plans entfällt als Gate, `make referenztest-dotnet`; die Stufen 4 bis 7 sind die Referenzclient-Läufe von `make kompatibilitaet`, `tools/e2e.sh`). Das DoD-Wort ist auf „die einzelnen Läufe der sieben Stufen“ angepasst. Die Messungen des 2026-10-31 oben (vier Stufen) gelten für den damaligen Stand, nicht für den heutigen.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Der Slice berührt das ganze Repo (`*`, Kürzel `TDS`); eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen: `BEO-TDS/test-belegt-nur-den-fake` (4×, verkörpert), `BEO-TDS/aussage-ueber-schichtgrenze-ohne-test` (1×, offen), `BEO-TDS/zusage-ohne-sensor-in-der-ci-kette` (2×, offen) und `BEO-TDS/messung-am-echten-sql-server-fehlt` (22×, geplant in `slice-qa-vergleichslauf-d`) berühren den Gegenstand. Eine Beobachtung „Nachweis von der falschen Seite der Grenze“ (Lauf im Modul statt über die Prozessgrenze) legt die Planung nicht an: das Register schreibt die Closure, und die vorhandenen Kennungen decken je eine Hälfte; legt ein Closure-Lauf eine an, zitiert er diese.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
