# Slice slice-betrieb-telemetrie-verdrahtung-belegen: Telemetrie: Verdrahtung von ACCEPT_FAILED und CONNECTION_FAILED, Rot-Fälle der Ausgabe-Sperre je Package und die Lücke von Logger.log

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-betrieb-diagnose

**Bezug:** [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) (`Accepted`, Frage 2 und Frage 7)

**Berührte Spec-Stellen:** SPEC-059 · SPEC-041 · SPEC-048 · [TDS-FA-09.a](../../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose)

**Verantwortlich:** Implementer-Agent, Auftrag des Nutzers 2026-11-06.

**Autor:** pt9912. **Datum:** 2026-11-01.

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Drei Aussagen aus Review und Verifikation von [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md) bekommen einen Beleg, den heute nur das Lesen des Codes trägt: (1) `ACCEPT_FAILED` und `CONNECTION_FAILED` entstehen am Server und nicht nur im Modell-Test (Verifikation V-3: die Mutationen M9 und M10 überleben); (2) „im ganzen Hauptmodul ohne Ausnahme“ der Ausgabe-Sperre ist für jedes Package des Hauptmoduls durch einen Rot-Fall belegt oder als Grenze benannt (V-6: Rot-Fälle nur für `runtime`, `protocol`, `transport`); (3) die Lücke von `java.util.logging.Logger.log(Level, …)` ist geschlossen oder bleibt mit Begründung benannt (V-6, Mutation M6).

**Herkunft:** die Punkte stammen aus dem Bericht `docs/reviews/2026-11-01-2e6b7ba-verifikation-slice-betrieb-log-schnittstelle.md` (V-3, V-6) und aus der Closure-Notiz von [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Umfang der öffentlichen Fläche (18 Ereignisarten, 4 Aufzählungen, rund 50 Namen; Optionen (a), (b), (c) in §6 von [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md)) — entschieden und umgesetzt in [slice-oeffentliche-flaeche-verkleinern-und-belegen](../done/slice-oeffentliche-flaeche-verkleinern-und-belegen.md) (`BEO-TDS/oeffentliche-flaeche-der-telemetrie-ohne-entscheidung`, gestrichen); die Arten stehen als `EventKind`, die Tests dieses Slice belegen sie in dieser Form.
- Neue Ereignisarten, `request_id` und `duration_ms` — [slice-betrieb-zustandsereignisse](../open/slice-betrieb-zustandsereignisse.md) und [slice-betrieb-metriken-tracing](../open/slice-betrieb-metriken-tracing.md); hier wird nur belegt, was gebaut ist.
- Der JSON-Adapter und die Inspektion des laufenden Prozesses — [slice-betrieb-log-adapter-testserver](../done/slice-betrieb-log-adapter-testserver.md).
- `Error` aus der Senke bei `ACCEPT_FAILED` und `CONNECTION_FAILED`: bleibt „weder gemessen noch zugesagt“ (KDoc von `TelemetrySink`); der Slice darf es messen, muss es nicht. Nutzerentscheid 2026-11-06: Ein `Error` aus der Senke als Auslöser von `CONNECTION_FAILED` genügt als Grenze des Tests; ein anderer Auslöser wird nicht gesucht, und eine Zusage darüber hinaus gibt es nicht.
- Weitere Methoden von `java.util.logging.Logger` (`config`, `fine` und weitere): bleiben unter der benannten Grenze der Sperre, ohne eigene Sperre (Nutzerentscheid 2026-11-06).

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Verdrahtung von `ACCEPT_FAILED` und `CONNECTION_FAILED`** ([TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose)): ein Test löst jede der beiden Arten über den Server aus (ein Handler, der wirft; ein Listener, dessen Annehmen scheitert) und liest sie im Sammler; er prüft die Felder der Art und die Grenze „`CONNECTION_FAILED` trägt keine `session_id` und das Profil `unknown`“ aus der Spezifikation (Abschnitt 4). **Beleg:** die Mutationen „`ListenerEvent.Closed` liefert kein Ereignis“ und „`ListenerEvent.AcceptFailed` liefert kein Ereignis“ machen je mindestens einen Test rot. Der Redaktionstest (`TelemetryRedactionTest`) bekommt für beide Arten einen Fall über den Server, mit je verschiedenen Eingabewerten.
- [x] **Rot-Fälle der Ausgabe-Sperre je Package** ([TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit)): unter `config/detekt/selbsttest-aufrufe/` liegt ein Rot-Fall für `domain`, `port`, `config`, `observability` und `simulation`, den `make detekt-selbsttest` fährt; **Beleg:** die Verengung des `includes` von `ForbiddenMethodCall/ausgabe` auf drei Packages macht `make detekt-selbsttest` rot (Mutation M11 der Verifikation). Der Erlaubt-Fall in `domain` und `port` (heute ohne `println`) belegt, dass die Kern-Regel dort nicht trifft; bleibt er, trägt er sein `println` wieder oder die Datei nennt, was sie nicht belegt.
- [x] **Lücke `Logger.log`** ([TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit)): entweder ein Eintrag der Sperre für `java.util.logging.Logger.log` (alle Überladungen, die der Übersetzer auflöst) mit Rot-Fall, oder die Grenze steht mit Begründung in `config/detekt/detekt.yml` und in der Closure-Notiz; Ziel ist ein Beleg, kein Versprechen. Die Aufrufe `Thread.dumpStack` und `printStackTrace(PrintStream)` sind dabei gemessen und genannt (der Review fuhr sie nicht).

Nachweis-Pflichten für alle drei Punkte:

- [x] Der Flake von `TelemetryRedactionTest` (einmal rot in der mutierten Kopie unter Last, Verifikation V-7 von [slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md)) wird in diesem Slice stabilisiert, ohne eigenen Träger (Nutzerentscheid 2026-11-06). **Eingeschränkt gehakt (Verifikation V-3):** der Fix ist aus der Reihenfolge begründet, seine Wirkung ist nicht gemessen; der verlangte reproduzierte Fehler liegt nicht vor (zwölf von zwölf Läufen ohne Fix grün), die Mutation „Fix zurücknehmen“ überlebt (`BEO-TDS/flake-fix-ohne-reproduzierten-fehler`).
- [x] Jeder neue Test trägt im KDoc den Block-Tag `@spec SPEC-<NNN>`; die Tests laufen in `make test` und sind grün.
- [x] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet (`BEO-TDS/aussagen-ueber-messung-hinaus`).
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update: der Satz zu `CONNECTION_FAILED` in Abschnitt 4 der Spezifikation trägt nach dem Test die Kennzeichnung „gebaut“ mit Verweis auf den Test; `SPEC-059` ändert sich durch diesen Slice nicht (die Arten stehen dort als `EventKind`).
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
| `src/test/kotlin/io/github/pt9912/tdswire/runtime/` (Wiring-Tests, `TelemetryRedactionTest`) | update / neu | Handler, der wirft; Listener, dessen Annehmen scheitert; Felder und Redaktion beider Arten (Happy, Negative) nach [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose) |
| `config/detekt/selbsttest-aufrufe/src/main/kotlin/io/github/pt9912/tdswire/` | neu | Rot-Fälle je Package (`domain`, `port`, `config`, `observability`, `simulation`) |
| `config/detekt/detekt.yml` | update | Eintrag oder benannte Grenze für `Logger.log`; nur Verschärfung (`AGENTS.md` §3.6) |
| `spec/spezifikation.md` Abschnitt 4 | update | Satz zu `CONNECTION_FAILED` mit Verweis auf den Test |
| `tools/detekt-selbsttest.sh` | update | Packages der Rot-Fälle aus den Testdaten der Importregeln abgeleitet, Funde je Methode gegen die Markierungen gezählt (eine Überladung ohne Fund fällt auf) |
| `src/test/kotlin/io/github/pt9912/tdswire/runtime/AcceptChild.kt` | neu | Kind-JVM mit `ulimit -n`: Auslöser für `ACCEPT_FAILED` ohne Naht im Produktcode |
| `config/detekt/selbsttest-aufrufe/src/main/kotlin/io/github/pt9912/tdswire/{runtime,protocol,transport}/Aufruf.kt` | update | Umbenennung des Objekts `Aufruf` in `AusgabeAufruf` (keine Namenskollision mit dem Kern-Objekt im selben Source-Set) und je Datei sechs neue Funktionen mit zehn Aufrufen der erweiterten Sperre (`printStackTrace` mit `PrintStream` und `PrintWriter`, `Thread.dumpStack`, fünfmal `Logger.log`, `Logger.logp`, `Logger.logrb`), zusammen 30 Aufrufe |
| `src/test/kotlin/io/github/pt9912/tdswire/runtime/TelemetryRedactionTest.kt` | update | Stabilisierung `eventually("LOGIN_ACCEPTED")` vor `server.stop()`: `loginAndSend` wartet nicht auf das Ereignis (Wettlauf, den der Test mit den neuen Fällen sichtbar machte) |
| `Dockerfile` (Stage `quellpakete`), `tools/stempel/werkzeug.eingaenge` | neu / update | Liste der Packages von `src/main` für den Selbsttest der Ausgabe-Sperre (Review F-6) |
| `tools/stempel-eingaenge-selbsttest.sh`, `tools/stempel-gruppe-selbsttest.sh` | update | Folge des neuen Eingangs `src/main`: der Fall „Fragment der Klasse `werkzeug` außerhalb der Einträge“ wandert nach `src/test/neu.mk`, aus einem Fall „src geändert“ werden zwei (`src/test` nur `tests`, `src/main` `werkzeug` und `tests`) |
| `docs/plan/planning/observations/BEO-TDS/listener-annahmeschleife-steht-bei-erschoepftem-dateilimit-still/` | neu | Register-Eintrag des Befunds F-2 (Annahmeschleife steht), Träger für §6 |
| `harness/README.md` §Sensors | update | Zeile `make detekt-selbsttest`: `src/main` ist Eingang des Stempels der Klasse `werkzeug`, mit Kosten (Verifikation V-5) |

Umsetzung: `ACCEPT_FAILED` entsteht am Limit offener Dateien einer Kind-JVM (keine Naht im Produktcode, `BEO-TDS/test-naht-im-produktivcode` nicht berührt); `CONNECTION_FAILED` entsteht, wenn die Senke nach `LOGIN_ACCEPTED` einen `Error` wirft, weil Backend und Adapter eine Ausnahme jeder Klasse auf eine Antwort abbilden. Die Sperre nennt zusätzlich `Logger.logp`, `Logger.logrb` und `Thread.dumpStack`; `printStackTrace(PrintStream)` und `printStackTrace(PrintWriter)` melden unter `kotlin.Throwable.printStackTrace` (gemessen).


## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md) liegt in `done/`; [slice-oeffentliche-flaeche-verkleinern-und-belegen](../done/slice-oeffentliche-flaeche-verkleinern-und-belegen.md) liegt in `done/` (erfüllt: die Nutzerentscheidung zum Umfang der öffentlichen Fläche ist gefallen und umgesetzt, die Arten stehen als `EventKind`); ein Verantwortlicher ist eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte; Schnittvorschlag: Verdrahtung | Sperren.
- `in-progress` → `open` (blockiert): die Entscheidung zur Fläche fällt erst während des Baus und ändert die Arten.

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün (einschließlich `make test` und `make detekt-selbsttest`), der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung *Test | Festlegung* und den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Ein Test über den Server für `ACCEPT_FAILED` verlangt einen Listener, dessen Annehmen scheitert; ob das ohne eine Naht im Produktcode geht (`BEO-TDS/test-naht-im-produktivcode`), ist ungeklärt. — **Ausgang:** entfallen: der Test läuft in einer Kind-JVM mit `ulimit -n` (`AcceptChild`), `src/main` ist im Diff unverändert (Verifikation, Randbedingung „Kein Produktivcode im Diff“).
- Der Rot-Fall je Package belegt die Konfiguration des `includes`, nicht jeden künftigen Aufruf (Typauflösung, nur genannte Aufrufe). — **Ausgang:** eingetreten und als Grenze benannt: der Kommentar der Regel in `config/detekt/detekt.yml` nennt, was gesperrt ist (`log`, `logp`, `logrb`, `Thread.dumpStack`, `printStackTrace`) und was nicht (`config`, `fine`, `finer`, `finest`, `entering`, `exiting`, `throwing`, ein Logger einer Fremdbibliothek); Nutzerentscheid 2026-11-06: keine weitere Sperre.
- Bei erschöpftem Limit offener Dateien endet der `failed`-Callback von `TdsListener.acceptOne` mit einem `NoClassDefFoundError`, wenn die Klasse des Fehlerpfads nicht geladen ist: die Annahmeschleife steht ohne Meldung; der Test von `ACCEPT_FAILED` belegt das Ereignis nur mit vorgeladenen Klassen (Review F-2). Produktcode bleibt in diesem Slice unverändert. — **Ausgang:** weiter offen, Register `BEO-TDS/listener-annahmeschleife-steht-bei-erschoepftem-dateilimit-still`, Träger [slice-listener-annahmeschleife-bei-erschoepften-dateien](../open/slice-listener-annahmeschleife-bei-erschoepften-dateien.md) (`open/`).
- Der Test von `ACCEPT_FAILED` hängt an der Umgebung: `sh`, `ulimit -n` (128 im `TelemetryWiringTest`, 128 und 160 im `TelemetryRedactionTest`) und Zeitschranken (10 s, 40 s und 60 s) stehen nur im Quelltext von `AcceptChild` und den zwei Tests (Review F-9, Verifikation V-6e); die Zeitschranken des Verifiers (40 s, 60 s) hielten. Gemessen: acht von acht Läufen der zwei Klassen und ein `make clean-build` bei Last 16 bis 22 auf einem Host, Docker-Image der Stage `test`; keine Aussage für andere Hosts oder Images. — **Ausgang:** weiter offen: wird der Test auf einem anderen Host oder unter höherer Last rot, ohne dass sich Produktcode ändert, sind Limit und Zeitschranken zu überprüfen, nicht der Test zu löschen.
- Ausgabewege außerhalb der Sperre sind nicht gemessen: `System.Logger` und `PrintStream.append` sind weder gesperrt noch als Rot-Fall belegt, die Grenze der Sperre sagt nur allgemein „nur die genannten Aufrufe“; die Liste der Packages für den Selbsttest nimmt nur Ordner direkt unter `io/github/pt9912/tdswire` (Quelltext an anderer Stelle von `src/main` fiele nicht auf, die Sperre selbst hat `includes` über den ganzen Baum). — **Ausgang:** weiter offen als benannte, nicht gemessene Grenze (Verifikation V-6, Review F-6); ein neuer Ausgabeweg im Produktcode gehört mit einem Eintrag der Sperre und einem Rot-Fall zusammen.
- Der neue Eingang `src/main` der Klasse `werkzeug` (Stage `quellpakete`) hat Kosten: jede Änderung unter `src/main` macht den Stempel ungültig, `make gates` fährt dann die Gates der Klasse mit (Verifikation V-5). — **Ausgang:** eingetreten und getragen: `harness/README.md` §Sensors nennt es in der Zeile `make detekt-selbsttest`; [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) bleibt unverändert (`AGENTS.md` §3.5, die Änderung verschärft nur und lockert nichts nach §3.6).
- Der Flake-Fix in `TelemetryRedactionTest` trägt keine Messung seiner Wirkung (Verifikation V-3). — **Ausgang:** eingetreten und benannt: DoD, §7 und Register führen den Fix als „aus der Reihenfolge begründet, Wirkung nicht gemessen“; `BEO-TDS/flake-fix-ohne-reproduzierten-fehler` hat damit 3 Dateien.

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

- **Zuordnung Test | Sensor | Festlegung:** *Test* (`TelemetryWiringTest`, 3 Fälle; `TelemetryRedactionTest`, Fälle je Art über den Server): `ACCEPT_FAILED` am Limit offener Dateien einer Kind-JVM (`AcceptChild`, nur mit vorgeladenen Klassen) mit Ebene, Typname, Verbindung und Wiederannehmen; `CONNECTION_FAILED` bei einer Ausnahme der Klasse `Error` aus der Senke nach `LOGIN_ACCEPTED`, ohne `session_id` und mit Profil `unknown`; Redaktion mit Marken in Benutzername, Kennwort und Ausnahmetext; kein `CONNECTION_FAILED` ohne Ausnahme des Handlers. *Sensor* (`make detekt-selbsttest`, 132 Aufruf-Fälle: 12 Kern und 120 der Ausgabe-Sperre, 15 Einträge mal 8 Packages): ein Rot-Fall je Package, die Funde je Methode gegen die Markierungen gezählt, die Packages gegen `src/main`; Sperre für `Logger.log`, `logp`, `logrb` und `Thread.dumpStack`. *Festlegung* (nur Text): der Auslöser `Error` aus der Senke genügt als Grenze (Nutzerentscheid 2026-11-06); 128 offene Dateien als Setzung des Tests; die Grenze der Sperre (`config`, `fine`, `finer`, `finest`, `entering`, `exiting`, `throwing`, Logger einer Fremdbibliothek, `System.Logger`, `PrintStream.append`).
- **Was hat funktioniert:** (1) Der Test von `ACCEPT_FAILED` ohne Naht im Produktcode: eine Kind-JVM mit `ulimit -n` und dem Vorladen der Klassen, `src/main` blieb unverändert. (2) Die Mutation „`AcceptChild` ohne `preloadClasses()`“ im Review fand die Bedingung, unter der das Ereignis überhaupt entsteht, und damit den Befund F-2 (Produkt, Folge-Slice). (3) Der Zähler je Methode im Selbsttest der Sperre und der Vergleich der Packages mit `src/main` (Review F-6): eine fehlende Überladung, ein entfernter Eintrag und ein neues Package ohne Rot-Fall machen `make detekt-selbsttest` rot (drei Mutationen der Verifikation). (4) Die Verifikation fuhr Frischbelege (`make clean-build`, `docker build --no-cache --target detekt-selbsttest`) statt der Cache-Stufen und fand so die Grenze von `make test` aus dem Cache.
- **Was ging anders als geplant:** (1) Der Plan sah „je drei neue Aufrufe“ in drei vorhandenen Dateien vor, der Diff trägt je Datei sechs Funktionen mit zehn Aufrufen (Plan-Tabelle zählte nicht gegen den Diff). (2) `CONNECTION_FAILED` ließ sich nicht über eine Ausnahme des Backends auslösen, weil Backend und Adapter jede `Exception` auf eine Antwort abbilden; der Auslöser ist ein `Error` aus der Senke. (3) Der Slice berührte den Stempel: die Stage `quellpakete` machte `src/main` zum Eingang der Klasse `werkzeug` und zog zwei Selbsttests des Stempels nach. (4) Der Flake von `TelemetryRedactionTest` kam als Auftrag der Nutzerentscheide hinzu; der verlangte reproduzierte Fehler ließ sich nicht herstellen. (5) Der Befund F-2 gehörte nicht zum Auftrag; er hat einen eigenen Slice.
- **Review und Verifikation:** Review `43b86c45` (Stand `84edd7eb`; 0 HIGH, 4 MEDIUM F-1 bis F-4, 2 LOW F-5 und F-6, 3 INFO F-7 bis F-9): F-1, F-2 (Register und Folge-Slice), F-4, F-5, F-6 behoben in `77c9a718`; F-3 (Haken vor Verifikation) in dieser Closure; F-7 bis F-9 INFO, als Grenzen in §6 geführt. Verifikation `83203253` (Stand `33b0e7e1`; 0 HIGH, 3 MEDIUM V-1 bis V-3, 2 LOW V-4 und V-5, 1 INFO V-6): V-1 (Nutzerentscheide und Register-Zustand) mit dem Merge von `main` und dem Nachzug des Registers, V-2 (`@spec SPEC-048` am Test „Eine Verbindung ohne Ausnahme des Handlers …“; der Test trägt jetzt den Tag), V-3 als Einschränkung der DoD-Zeile, V-4 in der Plan-Tabelle, V-5 in `harness/README.md`, V-6 in §6. Die Nachzüge `77c9a718`, `c7082ca4` und `33b0e7e1` hatten keinen Re-Review; der Verifier las und fuhr sie. Der Planner hat nach der Verifikation nur Haken, Tag, Dokumente und Register geändert, keinen Produktivcode.
- **Gemessen** (Einzelmessungen am 2026-11-06, ein Host, Last 16 bis 22 nach `uptime`, kein Beleg für andere Hosts): Verifikation: `make clean-build` Exit 0 in 8 min 39 s, 1007 Testfälle `PASSED`, 0 `FAILED`; achtmal die zwei Klassen `TelemetryWiringTest` und `TelemetryRedactionTest` einzeln in einer Kopie, 8 von 8 grün; `docker build --no-cache --target detekt-selbsttest` Exit 0, 132 Aufruf-Fälle; `make gates FORCE=1` Exit 0 in 64 s aus dem Layer-Cache (kein Frischbeleg der Tests); Mutationen M9, M10, zwei Lecks, drei Mutationen der Sperre rot. Nicht gefahren: die Mutation „eine Überladung von `Logger.log` fällt aus den Aufrufen“ (der Zähler ist gelesen, nicht mutiert). Die Läufe des Planners dieser Closure stehen im Bericht an den Nutzer, nicht hier.
- **Nutzerpunkte (festgehalten, nicht vom Planner entschieden):** (1) Der DoD-Haken zum Flake ist eingeschränkt gesetzt („Wirkung nicht gemessen“), der Nutzerentscheid verlangte einen reproduzierten Fehler; ob die Einschränkung genügt, bestätigt der Nutzer. (2) `BEO-TDS/flake-fix-ohne-reproduzierten-fehler` hat mit diesem Slice 3 Dateien; ob und wo die Regel verkörpert wird (Empfehlung: Prüfpunkt im Reviewer-Skill und im Verifier-Schritt), entscheidet der Nutzer, der Lese-Schritt gehört in die Closure von [welle-betrieb-diagnose](../welle-betrieb-diagnose.md). (3) Die Matrix `docs/plan/spec-abdeckung.md` nennt `TelemetryWiringTest` nicht unter `SPEC-048`; sie ist nicht neu erzeugt (`make spec-abdeckung` ist kein Gate und fährt alle Tests), der Nachweis liegt im KDoc.
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-betrieb-telemetrie-verdrahtung-belegen.md` ergänzt in [BEO-TDS/haken-vor-verifikation-gesetzt](../observations/BEO-TDS/haken-vor-verifikation-gesetzt/observation.md) (F-3, V-2; verkörpert, 6 Dateien), [BEO-TDS/plan-tabelle-nennt-neue-datei-nicht](../observations/BEO-TDS/plan-tabelle-nennt-neue-datei-nicht/observation.md) (F-4, V-4; verkörpert, 7 Dateien), [BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md) (F-1; verkörpert), [BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten](../observations/BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten/observation.md) (Review-Report ohne Links, `make docs-check` rot; gestrichen, das Gate deckt es), [BEO-TDS/flake-fix-ohne-reproduzierten-fehler](../observations/BEO-TDS/flake-fix-ohne-reproduzierten-fehler/observation.md) (V-3; 3 Dateien, offen, Schwelle erreicht) und [BEO-TDS/listener-annahmeschleife-steht-bei-erschoepftem-dateilimit-still](../observations/BEO-TDS/listener-annahmeschleife-steht-bei-erschoepftem-dateilimit-still/observation.md) (F-2; neu, 1 Datei, Folge-Slice benannt).
- **Steering-Loop-Eintrag:** Kein Eintrag in dieser Closure verkörpert, daher kein `liegt in`. Gelernt, in vier Klassen: (1) *Haken vor der Verifikation*: sechste Instanz; die Regel (Reviewer-Skill, Planner-Schritt, `implement-slice`) lag auf dem Zweig des Implementers noch nicht, ihr Wirken ist an dieser Instanz nicht belegbar, nur der Planner-Schritt wurde angewandt (Haken erst nach den Läufen des Verifiers und der Ergänzung des Tags). (2) *Plan-Tabelle zählt nicht gegen den Diff*: siebte Instanz; die Regel gilt, der Review fand die Lücke bei den vorhandenen Dateien, die Verifikation bei den Skripten und der Zahl — der Abgleich Tabelle gegen Diff braucht Zahlen und Dateinamen, nicht nur die Dateien, die der Review zufällig liest. (3) *Doku-Report mit `id-unlinked`*: das Gate fängt es (gestrichen), siebtes Auftreten; der Review-Report ist das Dokument, das die Kennungen ohne Link nennt. (4) *Test mit Flake-Fix ohne reproduzierten Fehler*: dritte Instanz, Schwelle erreicht; der Fix wurde aus der Reihenfolge begründet, die Mutation „Fix zurücknehmen“ überlebt, und der Slice führt ihn ehrlich als nicht gemessen — das ist die Aussage ≤ Beleg von `AGENTS.md` §3.8 auf einen Test angewandt. Eine *benannte Spec-Lücke* entsteht nicht: der Satz zu `CONNECTION_FAILED` in Abschnitt 4 trägt seine Bedingungen.
- **Risiken aus §6:** je Zeile mit Ausgang eingetragen (Naht entfallen, Grenze der Sperre eingetreten und benannt, Annahmeschleife weiter offen mit Folge-Slice, Umgebungsabhängigkeit weiter offen, nicht gemessene Ausgabewege weiter offen, Kosten des Stempel-Eingangs getragen, Flake-Fix nicht gemessen benannt).
- **Folge-Slices:** [slice-listener-annahmeschleife-bei-erschoepften-dateien](../open/slice-listener-annahmeschleife-bei-erschoepften-dateien.md) (Start-Trigger „dieser Slice in `done/`“ erfüllt; ändert den Produktcode der Annahmeschleife), [slice-betrieb-log-adapter-testserver](../done/slice-betrieb-log-adapter-testserver.md) (formatiert die Arten; die Verdrahtung am Server ist jetzt belegt, `EventKind` und Felder unverändert). Nichts gestartet.
- **Drei Paarungen:** Das Repo führt Wellen; die Paarungen prüft die Closure von [welle-betrieb-diagnose](../welle-betrieb-diagnose.md). Anker ↔ Folge-Slice: kein Anker gesetzt. Register ↔ Closure-Notiz: alle genannten Kennungen existieren als Verzeichnis mit nicht leerem `evidence/` (geprüft am Dateisystem). Folge-Slices: alle existieren als Datei im Planning-Lifecycle.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); der Slice berührt Tests und `config/detekt`, eine eigene Sub-Area besteht nicht.

**Vorgelagert — offene Beobachtungen sichten:** `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (verkörpert; Wiederkehr: die Fälle V-3 und V-6 stammen von dort), `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert), `BEO-TDS/test-naht-im-produktivcode`, `BEO-TDS/strukturtest-ohne-variation-der-eingabe`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
