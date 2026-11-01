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

- [ ] **Skript und Marker:** `tools/e2e.sh` (Präfix `e2e`, Funktion `e2e_abdeckung` vor der ersten Stufe) führt die vier Stufen aus §3 nacheinander aus (ein Docker-Lauf gleichzeitig, Abbruch an der ersten roten Stufe); `repo.mk` setzt `E2E_ABDECKUNG_QUELLE`, `E2E_ABDECKUNG_PRAEFIX` und `E2E_ABDECKUNG_SPEC`; `make e2e-abdeckung` schreibt die Sicht mit den Kennungen der vier Stufen, und jede Kurzbeschreibung nennt die Grenze ihres Belegs (`AGENTS.md` §3.8).
- [ ] **Aufruf und Laufzeit:** Ein Target `make e2e` (Fragment unter `tools/mk/`) mit Zeile in `harness/README.md` §Sensors ruft das Skript; die Laufzeit warm und kalt ist gemessen und mit der Summe der vier einzelnen Läufe verglichen (§7). `make e2e` steht nicht in `GATE_CHECKS`; `laufzeittest` und `simulations-integration` verlassen die Gate-Kette ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md), angenommen am 2026-10-31), und `.github/workflows/e2e.yml` ruft `make e2e`.
- [ ] **Übergabe der Lücken:** Die Folge-Stufen-Tabelle in §3 stimmt bei der Closure mit den E2E-Punkten in den DoDs der genannten Träger-Slices überein; ein Träger in `done/` ohne seine Stufe ist ein Befund dieses Slice, kein stilles Auslassen.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
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
| `tools/e2e.sh` | neu | Stufen 1 bis 4 und die Funktion `e2e_abdeckung`; Pfad im `tools/`-Baum des Repos, nicht in `tools/harness/` |
| `repo.mk` | update | `E2E_ABDECKUNG_QUELLE`, `E2E_ABDECKUNG_PRAEFIX`, `E2E_ABDECKUNG_SPEC` mit einfachem `=`; `E2E_ABDECKUNG_ZIEL` steht schon |
| `tools/mk/e2e.mk` | neu | Target `make e2e`; Zeile in `harness/README.md` §Sensors |
| `docs/plan/e2e-abdeckung.md` | update (erzeugt) | `make e2e-abdeckung`; nicht von Hand |

| Stufe (Präfix `e2e:`) | Ruft | Deklarierte Kennungen | Zugangsweg | Belegt — und nicht |
|---|---|---|---|---|
| 1 Rohbytes-Login und Startverhalten des Prozesses | `make smoke` | [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-FA-02](../../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose) | außerhalb: Kindprozess `main`, Rohbytes-Client (kein Referenzclient) | PRELOGIN/LOGIN7 mit LOGINACK; Start ohne JVM-Flag bricht ab; fehlerhafte Konfiguration und belegter Port beenden den Start mit Exit-Code 1. Nicht: Referenzclient, TLS, Anfragepfad, Inhalt der Diagnose (nicht geprüft) |
| 2 Container-Artefakt | `make laufzeittest` | [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit) | außerhalb: Container, Rohbytes-Client aus zweitem Container, `docker stop` | Image startet, Login (`TEST_UNENCRYPTED`) gelingt, Stop mit Exit-Code 143; die Ausgabe nennt weder Benutzer noch Passwort (nur der Erfolgsfall, nicht TLS-Modus oder Fehlpfade). `TDS-QA-06` nur der Docker-Teil, nicht Clean-Build in der CI |
| 3 Referenzclient gegen Simulations-Backend | `make simulations-integration` | [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-03](../../../../spec/lastenheft.md#tds-fa-03--einfache-sql-batches), [TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände) | außerhalb: `Microsoft.Data.SqlClient` gegen den Serverprozess; der Einstieg des Servers ist `runServer(Wiring(...))` im Test-Quelltext (`IntegrationServer.kt`), nicht `main` | Auswahl mit Bedingung, leeres Ergebnis, Zeilenquelle über dem Heap, Fehler der Zeilenquelle als `SqlException` mit nutzbarer Verbindung (ohne Parameter, ohne TLS, ohne Pool). Nicht: parametrisierte Abfrage (RPC), Cancel, Anweisung außerhalb der erkannten Formen |
| 4 Referenztest-Gerüst | `make referenztest-dotnet` | [TDS-RB-02](../../../../spec/lastenheft.md#tds-rb-02--externer-client) | außerhalb des Servers: kein Serverprozess | Das versionierte Testprojekt baut gesperrt und läuft; der Smoke-Test verbindet zu einem unbelegten Port und erhält eine `SqlException`. Nicht: irgendein Serververhalten |

**Folge-Stufen** — Anforderungen ohne Stufe oder mit einer Stufe, die nur einen Teil ihrer Akzeptanzkriterien trägt, und der Slice, dessen DoD die Stufe verlangt (Stand der Bestandsaufnahme 2026-10-31; Beleg sind die DoDs der Träger, diese Tabelle ist der Zeiger):

| Kennung | Lücke | Träger |
|---|---|---|
| [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte) | RPC, Typkatalog, mehrteilige Antworten und OUTPUT am Referenzclient: kein Lauf über die Prozessgrenze | [slice-qa-kompatibilitaet](../done/slice-qa-kompatibilitaet.md) |
| [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-RB-02](../../../../spec/lastenheft.md#tds-rb-02--externer-client) | Szenarien Anmeldung, SELECT, RPC, NULL, Fehler am Referenzclient; Stufe 3 trägt nur das Simulations-Backend | [slice-qa-kompatibilitaet](../done/slice-qa-kompatibilitaet.md) |
| [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit) (TLS-Modus) | TLS-Handshake nur gegen JDK-Gegenstelle im Modul, am Prozess nur Rohbytes ohne TLS | [slice-qa-kompatibilitaet](../done/slice-qa-kompatibilitaet.md) (TDS 8.0 und TLS am Referenzclient) |
| [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) (ATTENTION), [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) | Der Server beantwortet ATTENTION während einer Anfrage heute nicht; kein Cancel- oder Folgeabfrage-Lauf | [slice-cancel-referenzclient-szenario](../open/slice-cancel-referenzclient-szenario.md) (Abbruchpfad im Server: [slice-attention-cancel-timeout](../in-progress/slice-attention-cancel-timeout.md)), [slice-qa-synchronitaet](../done/slice-qa-synchronitaet.md) |
| [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) (Beenden, abrupter TCP-Abbruch), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung) (beschädigtes PRELOGIN) | Stufe 2 stoppt ohne laufende Anfrage; Stabilität (Fuzz, Sturm, Fragmentierung) läuft im Modul | [slice-abbruch-ressourcen-shutdown](../open/slice-abbruch-ressourcen-shutdown.md) (Zuschnitt-Entscheidung des Planners: sonst trägt kein offener Slice QA-02 am Prozess) |
| [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose) (Zustandsereignisse im Log) | Stufe 1 belegt nur Start und Exit-Code; Ereignisse am Prozess fehlen | [slice-betrieb-zustandsereignisse](../open/slice-betrieb-zustandsereignisse.md) |
| [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) (Anweisung außerhalb der erkannten Formen, Änderungsanweisung) | Negativfall am Referenzclient fehlt in Stufe 3 | [slice-sim-aenderung-integration](../done/welle-simulations-backend/slice-sim-aenderung-integration.md) |
| [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance) | Lasttest fehlt | [slice-qa-performance](../open/slice-qa-performance.md) |
| [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend) | Der öffentliche Einstieg für ein Backend fehlt; ein Backend außerhalb des Moduls ohne `internal`-Zugriff gegen den Serverprozess ist nicht belegt (Stufe 3 setzt die Tabellen über den Test-Einstieg) | `slice-integrations-einstieg` (liegt in diesem Stand nicht vor; der DoD-Punkt ist dort nachzutragen) |
| [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität) (Referenzabgleich) | vorläufig, kein Lauf | [slice-qa-vergleichslauf-d](../open/slice-qa-vergleichslauf-d.md) (Werkzeug, kein Gate; keine Stufe verlangt) |

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

- Eine Stufe deklariert mehr, als ihr Lauf belegt (Kennung breiter als der Test) — die Sicht ist eine Deklaration, und der Erzeuger prüft den Text, nicht den Lauf. Gegenmittel: jede Kurzbeschreibung nennt, was nicht belegt ist; der Review gleicht sie gegen den Lauf ab (verwandt: `BEO-TDS/test-belegt-nur-den-fake`, `BEO-TDS/aussage-ueber-schichtgrenze-ohne-test`). — **Ausgang:** offen bis zur Closure.
- `make e2e` als Gate führte die vier Stufen ein zweites Mal aus (`make smoke` läuft zudem in `make test`); die Messung in §2 zeigt die Mehrkosten. — **Ausgang:** offen bis zur Closure.
- Der Erzeuger kennt eine Quelle und ein Präfix; die Harness-Selbstprüfung erscheint nur, wenn das Produkt-Skript sie aufruft. — **Ausgang:** offen bis zur Entscheidung der ADR (Frage „mehrere Quellen“).
- Die Zuordnung von `TDS-QA-02` am Prozess an `slice-abbruch-ressourcen-shutdown` ist eine Wahl des Planners; trägt der Slice sie nicht, braucht der Gegenstand einen eigenen Slice. — **Ausgang:** offen bis zur Closure.

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

**Messung des Implementers (Einzelmessungen am 2026-10-31, je ein Lauf, keine Streuung):**

| Lauf | Zeit |
|---|---|
| `make e2e` warm (zweiter Lauf, Layer-Cache vorhanden) | 46 s (Stufen 2 s, 21 s, 21 s, 2 s) |
| die vier Targets einzeln, warm, nacheinander | 3 s, 20 s, 21 s, 1 s, zusammen 45 s |
| `make e2e` kalt (frischer buildx-Builder ohne Cache, danach entfernt) | 323 s (Stufen 106 s, 108 s, 104 s, 5 s) |
| `make e2e` nach Quelltext-Änderung (Gradle-Layer neu, Basis-Images im Cache) | 187 s (Stufen 54 s, 63 s, 69 s, 1 s) |
| `make gates` warm mit `laufzeittest` und `simulations-integration` | 49 s |
| `make gates` warm ohne beide (Stand nach der ADR zu den E2E-Läufen) | 9 s (alle Stages aus dem Cache; Einzelwert, kein Maß für einen Lauf nach Quelltext-Änderung) |

Der Durchgang kostet warm so viel wie die Summe der Einzelläufe (Differenz 1 s); die Stufen laufen nicht doppelt gegenüber ihrem Einzellauf, sondern doppelt gegenüber der Zeit, die `make gates` bisher für zwei von ihnen aufwendete. Die Kaltzeiten der Stufen 1 bis 3 liegen bei je rund 105 s (106 s, 108 s, 104 s); ob und warum sie unter den früheren Einzel-Kaltwerten (88 bis 171 s, 131 s) liegen, ist nicht gemessen; die Summe der vier Einzel-Kaltläufe ist nicht gemessen. Setzungen: der Durchgang bricht an der ersten roten Stufe ab; die Harness-Selbstprüfung fällt aus der Sicht (Option a), sie bleibt Werkzeug ohne Gate.

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
