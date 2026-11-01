# Slice slice-betrieb-log-adapter-testserver: Log-Adapter im Testserver: JSON-Zeile, Ebene, Warteschlange, Prozessende und Inspektion am laufenden Prozess

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-betrieb-diagnose

**Bezug:** [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität); [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) (`Accepted`, Fragen 1 und 7, Folgepflicht 2: Adapter, Warteschlange, Regel zum Prozessende, Inspektion, Skript-Muster), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) (`Accepted`, Entscheidungen 2, 6, 7: Jackson und Umgebung im Testserver, Bereitschaftszeile), [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) (Begriff E2E-Lauf).

**Berührte Spec-Stellen:** [TDS-FA-09.a](../../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose) (Nr. 3) · SPEC-012 · SPEC-043 · SPEC-048 · SPEC-040 (Läufe gegen den Testserver; die Aussagen stehen nach `slice-spec-nachzug-telemetrie`, dieser Slice setzt sie im Testserver um und ändert die Spezifikation nur bei einem Befund).

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

**Ziel:** Der Testserver (Source-Set `testserver`) registriert über den Parameter von `TdsServer.start` einen Adapter, der jedes Ereignis der Bibliothek als eine JSON-Zeile auf die Standardausgabe schreibt (Streaming-Generator von `jackson-core`), nach `LOG_LEVEL` filtert, über eine begrenzte Warteschlange mit einem Schreiber ausgibt und beim Prozessende nach der Regel der ADR leert; die E2E-Skripte lesen wieder Zeilen der Bibliothek, jetzt als Ereignisse, und ein Prozesstest inspiziert alle Zeilen eines Laufs.

**Befund, der den Slice begründet** (aus [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) gelesen, nichts ausgeführt): Die Bibliothek schreibt nirgends hin (Frage 1); ein Embedder und der Testserver erreichen das Log über denselben öffentlichen Weg. Das Format, die Ebene und der Weg zur Standardausgabe sind Sache des Testservers. Nach `slice-testserver-herausloesen` liest kein E2E-Lauf Zeilen der Bibliothek, und die Skript-Prüfungen (a) bis (c) der [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) sowie die weiteren Muster stehen herausgenommen oder mit der Kennung dieses Slice gekennzeichnet. Die Messungen i bis iii der ADR (Blockieren der Standardausgabe bei stockendem Leser, Verlust beim Prozessende) sind Einzelmessungen mit einem Pipe-Leser im Container, kein Beleg für den Betrieb.

**Zuschnitt (begründet).** Der Slice liegt in `welle-betrieb-diagnose` und nicht in `slice-testserver-herausloesen`: Der Adapter braucht die Senke, die erst `slice-betrieb-log-schnittstelle` baut, und jener Slice trägt schon drei große Liefer-Punkte. Er liegt auch nicht in `slice-betrieb-log-schnittstelle`, weil dieser sonst sechs statt drei Liefer-Punkte trüge und die Bibliothek (ohne Jackson, ohne Standardausgabe) und der Testserver in verschiedenen Source-Sets mit eigenem Lock gebaut werden. Reihenfolge als Kanten: Testserver-Umbau, Log-Schnittstelle, dieser Slice (§4).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Modell, Senke, Parameter von `start`, Sammler, Ablösung von `ServerLog`, Sperren für `println` und `simulation` — [slice-betrieb-log-schnittstelle](../in-progress/slice-betrieb-log-schnittstelle.md); Voraussetzung dieses Slice.
- Neue Ereignisarten, Startdiagnose als Inhalt (Adresse, Port, TLS-Teil ohne Pfad) und Konfigurationsabdeckung — [slice-betrieb-zustandsereignisse](slice-betrieb-zustandsereignisse.md); hier wird der Mechanismus des Prozessendes belegt, dort der Inhalt der Diagnose.
- Das Ereignis `metrics`, Span-Ende und ihre Serialisierung — [slice-betrieb-metriken-tracing](slice-betrieb-metriken-tracing.md); der Adapter serialisiert die Ereignisarten, die beim Bau dieses Slice existieren, und bricht beim Hinzufügen einer Art die Übersetzung (geschlossene Menge, `when` ohne `else`).
- Eine Warteschlange um jede übergebene Senke in der Bibliothek (Option D der Frage 7) — Re-Evaluierungs-Trigger der ADR; die Warteschlange ist Sache dieses Adapters.
- Adapter für Embedder (SLF4J, `System.Logger`, Logging-Brücken) und alle Aussagen über deren Escaping, Ziele, Aufbewahrung — Embedder; sie sind ein Beispiel, kein Vertrag.
- Die Wirkung der Warteschlange und des Generators unter Last — [slice-qa-performance](slice-qa-performance.md) (`BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`); dieser Slice belegt Verhalten, keine Durchsatzzahl.
- Die Spezifikation — sie steht nach [slice-spec-nachzug-telemetrie](../done/slice-spec-nachzug-telemetrie.md); ein Widerspruch, den der Bau findet, ist ein Befund für den Nutzer, kein stilles Ändern.
- Java-Quellen oder Reflection im Source-Set `testserver` — akzeptiertes Negativ der [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **JSON-Adapter und Ebene** ([TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)): Ein Adapter im Source-Set `testserver` implementiert die Senke und schreibt je Ereignis eine JSON-Zeile mit den Feldern `ts` (UTC, ISO-8601, aus `at`), `level`, `event` und den Feldern des Ereignisses, mit dem Streaming-Generator von `jackson-core` (Abhängigkeit des Source-Sets, Lock und Prüfsummen über `make deps-lock`, soweit ein Eintrag fehlt); das Escaping aller Zeichenketten ist vollständig, getestet mit Anführungszeichen, Steuerzeichen und Unicode in `principal`, Adresse, Typname und Schlüsselname, auch mit einem über die Längengrenze gekürzten `principal` (die Zeile bleibt gültiges JSON). `LOG_LEVEL` (`ERROR`, `WARN`, `INFO`, `DEBUG`, Voreinstellung `INFO`; ein anderer Wert bricht den Start des Testservers mit dem bisherigen Exit-Code ab) ist Eingang des Testservers und filtert im Adapter nach der Ebene des Ereignisses; `main` übergibt den Adapter über den Parameter von `start`. **Belege:** Tests in `testserverTest` (Escaping, Ebene, ungültiger Wert).
- [ ] **Warteschlange und Prozessende** ([TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) Frage 7): Der Adapter hat eine begrenzte Warteschlange (1024 Zeilen, Setzung) und einen Schreiber-Thread, der erst mit dem laufenden Server startet; bei Überlauf wird die neue Zeile verworfen und gezählt, die Zahl steht im Feld `dropped` der nächsten geschriebenen Zeile. Ereignisse vor dem erfolgreichen Start (ungültige Konfiguration, Bindefehler, fehlgeschlagene Startprobe der TLS-Konfiguration) und der Grund in `StartOutcome.Aborted` schreibt der Testserver vor dem Prozessende synchron und ohne Warteschlange. Der Adapter bietet `close` (oder `flush`) mit Frist (2 s, Setzung), das `main` und der Shutdown-Hook vor dem Prozessende rufen, auch vor `exitProcess`; was nach der Frist in der Warteschlange bleibt, geht verloren und wird mit der Zahl in einer letzten, synchron geschriebenen Zeile (`dropped`) gemeldet, soweit der Kanal annimmt; hängt der Kanal, endet der Prozess trotzdem nach der Frist. Die Bereitschaftszeile `tdswire: gestartet, Port <port>` bleibt zeichengleich, wird synchron über denselben Schreiber geschrieben und nie über die Warteschlange; der Hook schreibt `tdswire: gestoppt` zuletzt. **Belege:** ein Nebenläufigkeitstest der Warteschlange (Überlauf blockiert den Aufrufer nicht, `dropped` ist sichtbar, ein gestauter Schreiber hält den Test nicht an); ein Prozesstest mit Startabbruch (belegter Port, ungültige Konfiguration) liest die Diagnosezeile; ein Test mit gestautem Kanal prüft, dass der Prozess nach der Frist endet.
- [ ] **Inspektion und Skript-Muster am laufenden Prozess** ([TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): Ein Prozesstest (Kind-JVM über `main` des Testservers, Standardausgabe gelesen, Form von `ServerSmokeTest`) inspiziert alle Zeilen eines Laufs mit Anmeldung angenommen und abgelehnt, TLS-Fehler und Startabbruch (soweit die Ereignisarten zum Bau existieren): weder Passwort noch Kennung einer abgelehnten Anmeldung noch Prüfwert; die Bereitschaftszeile steht unverändert da. Die in `slice-testserver-herausloesen` herausgenommenen oder gekennzeichneten Skript-Prüfungen sind auf Felder des Modells umgestellt und die Kennzeichnungen entfernt: (a) „Log nennt keinen Handlerfehler“ prüft das Ereignis „Adapterfehler“ (oder die Art, die der Bau dafür vorsieht), (b) die Prüfung auf `secret` und `Geheimnis` liest den Strom aus Ereigniszeilen und Backend-Log des Testservers, (c) der Grund einer abgelehnten Anmeldung (`UNSUPPORTED_PASSWORD_CHANGE`, `UNSUPPORTED_FEDERATED_AUTHENTICATION`) steht in der Grund-Marke des Ereignisses „Anmeldung abgelehnt“, dazu die weiteren Muster aus der Closure-Notiz jenes Slice (etwa `Simulations-Backend: 4 Tabellen` und `Fehler: <Code>`). **E2E-Stufen:** die Stufen, die Skript-Muster lesen, sind in `tools/e2e.sh` mit den Kennungen [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose) und [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit) deklariert, `make e2e` ist in allen sieben Stufen grün (Messung in der Closure-Notiz, nicht aus dem Gedächtnis), und `make e2e-abdeckung` zeigt sie in `docs/plan/e2e-abdeckung.md`; der Lauf geht über die Prozessgrenze. Dieser Punkt ist das Gesamtsystem-Gegenstück zur Loginspektion des minimalen Logs in `slice-qa-sicherheit` (Träger der Trägerlücke F-1; vom Nutzer 2026-10-31 für `slice-betrieb-log-schnittstelle` bestätigt, mit der Verschiebung hierher Setzung des Planners).

Einordnung des Prozesstests: Ein Kindprozess ist nach [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) Entscheidung 1 ein E2E-Lauf; Entscheidung 4 hält `make smoke` in der Stage `test`, und dieser Test hängt an derselben Form. Gäbe der Nutzer das nicht her, läuft die Inspektion im selben Prozess mit umgeleiteter Standardausgabe (ohne Prozessgrenze, schwächerer Beleg, [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) Fitness Function).

Nachweis-Pflichten für alle Punkte:

- [ ] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>` (Konvention nach [slice-qa-spec-abdeckung](../done/slice-qa-spec-abdeckung.md)); die Tests laufen in `make test` und sind grün.
- [ ] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet; die Zuordnung *Test | Festlegung* je Aussage steht in der Closure-Notiz (`BEO-TDS/aussagen-ueber-messung-hinaus`, siehe §8).
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
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
| `src/testserver/kotlin/…/testserver/` (Adapter, Warteschlange, Schreiber) <!-- d-check:ignore (geplant: neue Datei) --> | neu | Senke des Testservers; `jackson-core` nur hier |
| `src/testserver/kotlin/…/testserver/Main.kt` und Lader <!-- d-check:ignore (geplant: Pfad nach slice-testserver-herausloesen) --> | update | `LOG_LEVEL`, Registrierung über den Parameter von `start`, `close` im Hook und vor `exitProcess`, synchrone Diagnose vor dem Prozessende |
| `src/testserver/` (Tests) | neu | Escaping, Ebene, Warteschlange, Prozessende, Inspektion; `@spec`-Tag SPEC-012, SPEC-043, SPEC-048 |
| `build.gradle.kts`, `gradle.lockfile`, `gradle/verification-metadata.xml` | update (erzeugt) | `jackson-core` für das Source-Set, falls der Eintrag nach dem Umbau fehlt (`make deps-lock`, braucht Netz) |
| `tools/simulationstest.sh`, `tools/kompatibilitaetstest.sh`, `tools/laufzeittest.sh` | update | Muster auf Felder des Modells; Kennzeichnungen aus `slice-testserver-herausloesen` entfernt |
| `tools/e2e.sh` | update | Kennungen und Texte der Stufen, die Ereignisse lesen |
| `docs/plan/e2e-abdeckung.md`, `docs/plan/spec-abdeckung.md` | update (erzeugt) | `make e2e-abdeckung`, `make spec-abdeckung` |
| `harness/README.md` | update | Zeilen `make smoke`, `make simulations-integration` und `make e2e`, soweit sie das Log des Testservers nennen |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`), alle Bedingungen beobachtbar:

1. [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) steht im ADR-Index auf `Accepted` (erfüllt: Annahme am 2026-11-01).
2. [slice-testserver-herausloesen](../done/slice-testserver-herausloesen.md) liegt in `done/` (der Testserver existiert, bindet nur die öffentliche Fläche, und die herausgenommenen Skript-Prüfungen tragen die Kennung dieses Slice).
3. [slice-betrieb-log-schnittstelle](../in-progress/slice-betrieb-log-schnittstelle.md) liegt in `done/` (Modell, Senke, Parameter von `start` und Sammler stehen als öffentliche Fläche).
4. [slice-spec-nachzug-telemetrie](../done/slice-spec-nachzug-telemetrie.md) liegt in `done/` (die Spezifikation sagt `LOG_LEVEL`, Format und Warteschlange als Aussagen über den Testserver; folgt aus Bedingung 3, hier genannt, damit die Kante lesbar ist).
5. Ein Verantwortlicher ist im Kopf eingetragen.

**Folge-Kanten:** [slice-betrieb-zustandsereignisse](slice-betrieb-zustandsereignisse.md) und [slice-betrieb-metriken-tracing](slice-betrieb-metriken-tracing.md) warten auf diesen Slice.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte oder der Bau zerfällt; Zerlegung: (a) Adapter und Ebene | (b) Warteschlange und Prozessende | (c) Inspektion und Skript-Muster; (b) setzt (a) voraus, (c) setzt (a) und (b) voraus, jeder Teil schließt mit grünem `make gates`. Zwischen (a) und (c) bleiben die Skript-Prüfungen der Bibliothekszeilen herausgenommen; der Zustand steht in der Roadmap, nicht als stilles Rot.
- `in-progress` → `open` (blockiert — Carveout?): der Adapter braucht einen Typ des Modells oder der Senke, der nicht öffentlich ist (Befund über die Fläche und Folge-ADR, nicht `internal` öffnen); oder ein Embedder verlangt ein fertiges Format oder eine Warteschlange in der Bibliothek (Re-Evaluierungs-Trigger der [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md), Folge-ADR); oder `slice-betrieb-log-schnittstelle` fällt nach `open/` zurück; oder ein Gate müsste gelockert werden (`AGENTS.md` §3.6).

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün (einschließlich `make test`), `make e2e` ist in allen sieben Stufen grün (Messung in der Closure-Notiz), der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung *Test | Festlegung* je Aussage, die Liste der umgestellten Skript-Muster und den Steering-Loop-Lerneintrag. Gemessen sind Escaping, Ebene, Überlauf und Prozessende an Tests und der Inspektionslauf über die Prozessgrenze; nicht gemessen sind Last, Zeit und Speicher des Generators und der Betrieb hinter einem Log-Treiber ([ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md), akzeptierte Negative).

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Die Liste der herausgenommenen Skript-Prüfungen stammt aus der Closure-Notiz von `slice-testserver-herausloesen` und aus dem Lesen der Skripte; sie kann unvollständig sein, und eine Prüfung, die dort nicht stand, bleibt leer wahr (`BEO-TDS/aussagen-ueber-messung-hinaus`). — **Ausgang:** offen bis zur Closure.
- Die Skript-Prüfungen, die `slice-testserver-herausloesen` herausgenommen hat und die dieser Slice ersetzt, stehen vollständig im Abschnitt Übergabe in §7 von [slice-testserver-herausloesen](../done/slice-testserver-herausloesen.md) und mit der Kennung dieses Slice in den Skripten (`grep slice-betrieb-log-adapter-testserver tools/*.sh`); fehlt eine Prüfung dort, bleibt sie leer wahr. — **Ausgang:** offen bis zur Closure.
- Abnahmeschritt, weil kein Sensor die Kennzeichnung hält (Mutation m7 der Verifikation von `slice-testserver-herausloesen`: alle Zeilen `Entfallen, Ersatz in slice-betrieb-log-adapter-testserver` aus den Skripten löschen, `skript-selbsttest` bleibt grün): vor der Closure läuft `grep slice-betrieb-log-adapter-testserver tools/*.sh`, und jede Fundstelle ist ersetzt oder mit Grund belassen. — **Ausgang:** offen bis zur Closure.
- Zusätzlich zur Übergabeliste in `slice-testserver-herausloesen` §7 zu ersetzen: (a) die Prüfung „weder Kennwort noch Text der Quellenausnahme“ in `tools/simulationstest.sh` ist leer wahr, weil das Log der Bibliothek fehlt, das die Quellenausnahme trug; (b) die Prüfungen des Produkt-Images in `tools/laufzeittest.sh` (Nutzer, Lizenz in `/app`, Dateiliste ohne Benutzerliste, Schlüsselspeicher und Passwort im Image, `JAVA_TOOL_OPTIONS`) sind ohne Ersatz entfallen; die Dateinamen-Prüfung gegen Geheimnisse im Image hat keinen Träger, dieser Slice entscheidet, ob er sie dem Testserver-Image zuordnet. — **Ausgang:** offen bis zur Closure.
- Folgepflichten aus der Verifikation: (a) `tools/laufzeittest.sh` prüft mit `grep -Eq 'alice|secret'`; sobald der Adapter Login-Zeilen mit Benutzernamen schreibt, wird die Prüfung falsch rot oder falsch grün, die Grenze ist neu zu ziehen (die Spezifikation lässt den Benutzer nur im Feld `principal` zu); (b) `SPEC-010` fordert die Konfiguration „im Log sichtbar“, der Testserver schreibt die `ACHTUNG`-Zeilen heute nicht und die Bibliothek loggt über `ServerLog.NONE`; der Widerspruch zu [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) Entscheidung 7 gilt bis zu diesem Slice. — **Ausgang:** offen bis zur Closure.
- Bei Überlauf fehlen Zeilen, auch Sicherheitsereignisse (Messung i der ADR: 18 869 von 20 000 verworfen bei einem 5 s stockenden Leser und einem Stoß ohne Pause, keine Last des Produkts); der Inspektionstest belegt die Abwesenheit von Geheimnissen, nicht die Vollständigkeit der Zeilen. — **Ausgang:** offen bis zur Closure (erwartet: weiter offen → `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`).
- Der Wettlauf beim sofortigen Prozessende ist nicht ausgeschlossen (Messung iii: in zehn Läufen kein Verlust, das belegt nicht, dass er nicht auftreten kann); die Regel (synchrone Diagnose, `close` mit Frist) gilt unabhängig, und ein Prozesstest dazu kann flackern. — **Ausgang:** offen bis zur Closure.
- Die JSON-Zeile zum Ereignis „Port gebunden“ steht neben der Bereitschaftszeile; die `grep`-Muster der Skripte treffen sie nicht (Wortlaut gelesen, nicht ausgeführt). — **Ausgang:** offen bis zur Closure.
- Schreibt der Testserver die Bereitschaftszeile synchron über denselben Schreiber wie die JSON-Zeilen, kann ein langsamer Kanal den Start anhalten; die Zusage „blockiert nicht“ gilt für die Senke, nicht für die Bereitschaftszeile. — **Ausgang:** offen bis zur Closure.
- Zeit und Speicher des Jackson-Generators und die Kosten des Ereignisbaus sind ungemessen (akzeptiertes Negativ der [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md); der Generator läuft nur im Testserver). — **Ausgang:** offen bis zur Closure (erwartet: weiter offen → Register).
- Der Prozesstest als Kindprozess ist ein E2E-Lauf nach [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md); hält der Nutzer ihn nicht in der Stage `test`, wird die Inspektion zum schwächeren Beleg im selben Prozess. — **Ausgang:** offen bis zur Closure.
- Gecachte grüne Läufe der Stage `test` belegen Wiederholung nicht (`BEO-TDS/test-stage-cache-verdeckt-wiederholung`); der Überlauftest wird mit `make clean-build` frisch belegt. — **Ausgang:** offen bis zur Closure.
- Aufgabe aus der Verifikation von [slice-explicit-api-umbau](../done/slice-explicit-api-umbau.md) (LOW-B): `internal object ConfigKeys` trägt tote Konstanten (`ENCRYPTION_POLICY`, `AUTH_USERS_FILE`, `AUTH_PLAIN_TESTMODE` ohne Aufrufer in `src/main`; der Test, der sie gegen `EnvKeys` pinnte, entfiel im Umbau). Beim Anfassen von `EnvKeys` und des Laders entscheiden: entfernen oder wieder an einen Aufrufer binden; keine Sichtbarkeitsfrage. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration kennt nur `*` (Greenfield, Kürzel `TDS`). Der Slice berührt `src/testserver`, `build.gradle.kts`, `tools/` und `docs/plan/`: ein Pfad-Cluster (Achse 3) ohne eigene Strukturregel oder Inventur-Linie — eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, am 2026-11-01). Treffer: `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` (3×, geplant; die Gesamtinspektion am Prozess trägt dieser Slice), `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` (geplant bei `slice-qa-performance`; Warteschlange und Überlauf sind Setzung, Risiko in §6), `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen` (3×, offen; Speicher, nicht Ausgabe, nicht Gegenstand), `BEO-TDS/strukturtest-ohne-variation-der-eingabe` (verkörpert; Eingaben des Escaping-Tests variieren), `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert, `AGENTS.md` §3.8), `BEO-TDS/test-stage-cache-verdeckt-wiederholung` (verkörpert; `make clean-build`), `BEO-TDS/java-quelle-umgeht-kotlin-regel` (offen; Grenze des Nachweises „nur öffentliche Fläche“), `BEO-TDS/katalog-und-lock-ohne-gegenpruefung` (offen; `make deps-lock` für `jackson-core`), `BEO-TDS/move-commit-laesst-detekt-rot` (offen; falls Dateien des Testservers bewegt werden), `BEO-TDS/zahl-an-mehreren-orten-ohne-sensor` (offen; Zahlen 1024 und 2 s stehen in Test, KDoc und Spezifikation), `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` (10×, offen; Zuschnitt in drei Slices und Verschiebung des Inspektionstests sind Lesarten des Planners), `BEO-TDS/dod-haengt-an-offenem-slice-ohne-rueckfuehrung` (3×, offen; die Kanten in §4 nennen Start-Trigger und Rückführung). Sonst keine Treffer.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
