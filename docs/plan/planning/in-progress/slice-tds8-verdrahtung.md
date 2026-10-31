# Slice slice-tds8-verdrahtung: TDS-8.0-Pfad ab dem ersten Byte im Serverprozess verdrahten

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage — der Slice dient der Messung am Referenzclient (`slice-qa-kompatibilitaet`, Welle `welle-qualitaet-grundlage`) und ändert den Dispatch der Welle `welle-anfrageverarbeitung` nicht; läge er dort, schlösse deren Closure erst mit einem Slice, der zu ihrer Aussage (Rohbytes-Folge ab `READY`) nichts beiträgt.

**Bezug:** [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md), [ADR-0002](../../adr/0002-tls-und-verschluesselung.md)

**Berührte Spec-Stellen:** SPEC-001 · SPEC-020 · SPEC-023

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

**Ziel:** Der Pfad ab dem ersten Byte (TDS 8.0, vorgelagertes TLS) ist im Serverprozess verdrahtet, belegt durch eine Rohbytes- und eine JDK-Gegenstelle gegen den Prozess, und das KDoc von `runtime/ConnectionWiring.kt` beschreibt ihn als Ist-Zustand statt als nicht verdrahtet.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Messung am Referenzclient `Microsoft.Data.SqlClient` und Hebung der Codec-Aussage von *Festlegung* auf *gemessen* — `slice-qa-kompatibilitaet`; dieser Slice belegt den Pfad nur gegen eine Rohbytes- und eine JDK-Gegenstelle.
- Der Anfragepfad im Produktionsprozess (Simulations-Backend verdrahten) — `slice-sim-aenderung-integration`; dieser Slice betrifft nur den Weg vom ersten Byte bis zum Handshake.
- Eine neue TLS-Konfiguration, Suiten oder Protokollversionen — Bestand aus [ADR-0002](../../adr/0002-tls-und-verschluesselung.md) und `SPEC-023`; der Slice verdrahtet den vorhandenen Pfad.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [x] **TDS-8.0-Pfad im Serverprozess** (`SPEC-001`, `SPEC-020`): Eine Verbindung, die mit TLS beginnt (`CONNECTED → TLS_NEGOTIATION → PRELOGIN`), wird im Serverprozess angenommen, durchläuft Handshake und Login bis `READY` und wird wie der TDS-7.4-Pfad beendet; ein Test mit einer JDK-Gegenstelle und ein Rohbytes-Test belegen den Pfad am laufenden Prozess, einschließlich des Verhaltens bei Klartext als erstem Byte in diesem Modus (Festlegung des Slice, nicht gemessen). Die Wahl des Pfads je Verbindung (erstes Byte) und ein Konfigurationsschalter, falls nötig, sind Festlegungen des Slice und als solche gekennzeichnet.
- [x] **KDoc als Ist-Zustand:** Das KDoc von `runtime/ConnectionWiring.kt` beschreibt den verdrahteten Pfad statt „nicht verdrahtet“; die Aussage trägt höchstens so weit wie der Test (`AGENTS.md` §3.8): gemessen ist nur die Gegenstelle des Tests, nicht der Referenzclient.
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
      Gilt für den Stand `0fa406a`; die Fix-Runde `2a5e486` hat kein eigenes
      Review (nur Verifier und Implementer sahen sie, siehe §7).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist (Konfiguration, `SPEC-020`): Sicherheitsprofil in `spec/spezifikation.md` (Abschnitt Zertifikate/Konfiguration) nennt die Pfadwahl am ersten Byte statt „nicht verdrahtet“.
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
| `src/main/kotlin/io/github/pt9912/tdswire/runtime/` (`ConnectionWiring.kt`, Listener) | update | Pfad ab dem ersten Byte verdrahten, KDoc nachziehen |
| `src/test/kotlin/io/github/pt9912/tdswire/runtime/` | neu | Happy (JDK-Gegenstelle bis `READY`), Negative (Klartext im TDS-8.0-Modus), Rohbytes-Folge nach [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität); `@spec`-Tag je Test |
| `src/main/kotlin/io/github/pt9912/tdswire/transport/channel/PeekedChannel.kt` | neu | liest das erste Byte, ohne es zu verbrauchen; Grundlage der Pfadwahl je Verbindung |
| `spec/spezifikation.md` (Sicherheitsprofil) | update | „nicht verdrahtet“ durch die Pfadwahl am ersten Byte ersetzen (Setzung) |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-transport-listener-lebenszyklus](../done/welle-transport-tls/slice-transport-listener-lebenszyklus.md) und [slice-handshake-login](../done/welle-handshake-login/slice-handshake-login.md) liegen in `done/` (erfüllt), und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): [ADR-0002](../../adr/0002-tls-und-verschluesselung.md) wird geändert und stellt den Pfad ab dem ersten Byte infrage.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün und die Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Der Pfad ist nur gegen eine Rohbytes- und eine JDK-Gegenstelle belegt; ob der Referenzclient ihn annimmt, ist nicht gemessen (`BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, Träger `slice-qa-kompatibilitaet`). — **Ausgang:** weiter offen (Träger [slice-qa-kompatibilitaet](../open/slice-qa-kompatibilitaet.md); der Slice belegt den Pfad nur gegen eine Rohbytes- und eine JDK-Gegenstelle).
- Ob beide Pfade am selben Port laufen (das erste Byte unterscheidet) oder an zwei Ports, ist beim Start festzulegen und nicht gemessen. — **Ausgang:** entfallen (ein Port; das erste Byte `0x16` wählt den Pfad je Verbindung; Setzung, am Referenzclient nicht gemessen, kein Schalter; die Frage stellt sich nicht mehr, ihre Antwort ist die Setzung).

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

- **Gegenstand:** entfällt (der Slice lieferte).
- **Was hat funktioniert:** Beide Liefer-Punkte liegen vor. `HandshakeConnectionHandler` wählt am ersten Byte (`0x16` gegen sonst) den Pfad; `PeekedChannel` liest das Byte, ohne es zu verbrauchen. Belegt sind der Pfad mit JDK-Gegenstelle (`Tds8WiringTest`: TLS 1.3 und 1.2, ALPN `tds/8.0`, `LoginAck` bei richtigem, keines bei falschem Passwort, Ende wie im 7.4-Pfad), Rohbytes-Fälle (TLS 1.1, abgebrochenes ClientHello, Klartext im TLS-Record, Byte `0x41`), ALPN-Mismatch und Testmodus, der Kind-JVM-Prozess (`Tds8ProcessTest`) und `PeekedChannelTest`. Die Mutationen der Verifikation röteten A (Pfadwahl immer 7.4: 7 Tests), B (Treiberfrist 10000 s: genau `HandshakeWiringTest > Die Frist des Treibers traegt den konfigurierten Wert`), C (Bedingung `!= REQUIRED` entfernt: genau `Tds8DefenseTest`), D (`served = true` vor `buffer.put`: genau `PeekedChannelTest > Ein Puffer ohne Platz wirft`) und E (Frist um `peek` entfernt: genau `HandshakeWiringTest > Die Frist des Wartens auf das erste Byte traegt den konfigurierten Wert`).
- **Was ging anders als geplant:** (a) Der Review (0 HIGH, 2 MEDIUM, 3 LOW, 6 INFO) fand die Treiberfrist ohne tragenden Test (M-1: der Test trug nach dem Umbau die Frist des Wartens auf das erste Byte, sein KDoc nannte die des Treibers), die Bedingung `encryptionPolicy != REQUIRED` ohne unterscheidenden Test (M-2), „ohne Antwort“ breiter als der Beleg (L-1), das KDoc „ohne Verzug“ (L-2) und `PeekedChannel` ohne eigenen Test (L-3). (b) Beim Beheben von L-3 und in der Verifikation zeigte sich ein Fehler im Eigencode: `PeekedChannel.read` setzte `served = true` vor `buffer.put`, ein Puffer ohne Platz verlor das erste Byte; er fiel durch Mutation D auf, nicht durch Lesen. (c) Die Verifikation (0 HIGH, 0 MEDIUM, 2 LOW) bestätigte M-1, M-2, L-1 bis L-3 als behoben; L-4 (Abdeckungsmatrix veraltet) ist mit dieser Closure behoben (`make spec-abdeckung`: `SPEC-020` trägt `Tds8WiringTest` und `Tds8ProcessTest`, 13 Festlegungen ohne Nachweis, vorher 14); L-5 (Flake `BootstrapTest`: ein Zufallsport, der die Zeichenfolge `4711` des Prüfwortes enthält) liegt außerhalb dieses Slice, ein Implementer behebt ihn getrennt und er ist **nicht Teil dieser Closure**.
- **Reports:** [Review M-1, M-2, L-1 bis L-3, I-1 bis I-6](../../../reviews/2026-10-31-0fa406a-slice-tds8-verdrahtung.md), [Verifikation L-4, L-5](../../../reviews/2026-10-31-c3b6439-verifikation-slice-tds8-verdrahtung.md). **Wer was gesehen hat:** (1) Den Stand `0fa406a` (Umsetzung `ecf0aae`) hat der Reviewer gesehen. (2) Die Fix-Runde `2a5e486` hat **kein eigenes Review**; sie haben nur der Verifier (Mutationen A bis E, vier Läufe, Lesen von Spezifikation und KDoc) und der Implementer gesehen. (3) Diese Closure-Notiz und die Register-Einträge hat nur der Planner gesehen; ein Gegenlesen durch einen zweiten Kontext liegt nicht vor.
- **Frischer Lauf der Closure:** `git merge main` meldete „Bereits aktuell“; `docker build --no-cache-filter test --target test .` auf dem Stand `af8ae1d`, einmal: Exit 0, `BUILD SUCCESSFUL in 1m 56s`, 775 Zeilen `PASSED`, keine rote Testzeile, keine übersprungene (der einzige Treffer auf „SKIPPED“ ist die Gradle-Aufgabe `checkKotlinGradlePluginConfigurationErrors`); darin `Tds8WiringTest` (5), `Tds8DefenseTest`, `Tds8ProcessTest` und `PeekedChannelTest` (4) `PASSED`. Das ist ein Lauf, keine Streuungsaussage: die Zeitstreuung von `Tds8ProcessTest` ist nicht gemessen (die Gradle-Ausgabe nennt keine Dauer je Test; gemessen ist Bestehen in 4 von 4 Läufen der Verifikation und in diesem). `make spec-abdeckung` und `make gates` vor jedem Commit der Closure grün; das Ergebnis nach dem Move steht im Bericht der Closure, nicht in dieser Zeile.

**Zuordnung je Aussage.** *gemessen* heißt ein bestandener Lauf am Code dieses Slice gegen die genannte Gegenstelle, **nicht** am Referenzclient `Microsoft.Data.SqlClient` und nicht an einem echten SQL Server; *Handvektor* ein Fall mit Rechnung von Hand; *Selbstkonsistenz* Code gegen Code aus demselben Slice; *Festlegung* eine Entscheidung ohne Messung.

| Stärke | Aussage | Beleg |
|---|---|---|
| gemessen (JDK-Gegenstelle) | Eine Verbindung mit TLS ab dem ersten Byte (TLS 1.3 und 1.2, ALPN `tds/8.0`) erreicht `READY`, bei falschem Passwort kommt kein `LoginAck`, das Ende ist `HANDLER_RETURNED` ohne Fehler wie im 7.4-Pfad | `Tds8WiringTest` (5 Tests); Mutation A rot (7 Tests) |
| gemessen (Rohbytes, wenige Fälle) | TLS 1.1 und Klartext im TLS-Record enden ohne Byte vom Server und ohne Rückfall auf Klartext; Material mit `TEST_UNENCRYPTED` bietet den Pfad nicht an | `Tds8WiringTest`, `Tds8DefenseTest`; Mutation C rot |
| gemessen (Kind-JVM-Prozess) | Der Serverprozess nimmt TLS ab dem ersten Byte an; die Ausgabe nennt weder Benutzer noch Passwort | `Tds8ProcessTest`; 4 von 4 Läufen der Verifikation und der Lauf der Closure bestanden |
| gemessen (Eigencode) | `PeekedChannel` liefert das erste Byte zuerst, gibt `null` bei Ende oder Lesefehler, wirft bei Puffer ohne Platz und behält dabei das Byte, delegiert Schreiben und Schließen | `PeekedChannelTest` (4 Tests); Mutation D rot |
| gemessen (Eigencode) | Die Frist des Wartens auf das erste Byte und die des Treibers tragen je einen eigenen Test | `HandshakeWiringTest`; Mutationen B und E rot, je genau ein Test |
| Selbstkonsistenz | Dass `HandshakeConfig` die Kombination `TLS_FIRST` mit Richtlinie ungleich `REQUIRED` verweigert und der Handler sie vorher abfängt, prüft `Tds8DefenseTest` am selben Code | `Tds8DefenseTest` |
| Festlegung | `0x16` als Unterscheidungsbyte (kein TDS-Pakettyp, ein TDS-7.4-Client beginnt mit `0x12`; Kenntnisstand, **am Referenzclient nicht gemessen**); ein Port statt zwei; kein Konfigurationsschalter; kein Rückfall auf Klartext | `spec/spezifikation.md` `SPEC-020`, KDoc von `ConnectionWiring.kt` |
| nicht gemessen | Ob der Referenzclient den Pfad annimmt; ob die TLS-Engine bei ALPN-Mismatch, `h2` oder abgebrochenem ClientHello einen Alert sendet (die Spezifikation sagt „ohne TDS-Antwort“, die Tests beobachten in den Rohbytes-Fällen kein Byte, den Alert nicht); die Zeitstreuung von `Tds8ProcessTest`; TDS 8.0 mit Pooling | Review L-1, Verifikation Info; `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` |

- **Risiken aus §6:** beide tragen genau einen Ausgang (siehe §6): der Port (ein oder zwei) ist **entfallen** (Setzung: ein Port, Pfadwahl am ersten Byte), die Annahme durch den Referenzclient bleibt **weiter offen** (Träger [slice-qa-kompatibilitaet](../open/slice-qa-kompatibilitaet.md)). Zusätzlich offen gehalten, ohne eigenen §6-Eintrag: die Streuung von `Tds8ProcessTest` ist nicht gemessen und **weiter offen**; ein Träger ist dafür nicht benannt, die Aussage steht in dieser Notiz, und beim ersten Flake-Befund entsteht ein Register-Eintrag.
- **Steering-Loop-Eintrag:** (1) **Lerneintrag, geschärfte Regel, nicht verkörpert:** Steht ein Zustandsfeld (`served`) vor der Wirkung, die es ankündigt (`buffer.put`), gehört ein Test des Fehlerpfads dazu, der das Feld nach dem Fehler prüft; der Fehler in `PeekedChannel.read` fand erst die Mutation D, nicht das Lesen. Das ist die Wiederkehr von `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (jetzt 11×; verkörpert im Reviewer-Skill seit `welle-transport-tls`; M-2, L-3 und der Fehler traten **nach** der Verkörperung auf und der Reviewer fand sie per Mutation: die Regel greift im Review, verhindert aber nicht, dass Eigencode ohne Test entsteht). (2) **Lerneintrag, nicht verkörpert:** Ein Umbau, der eine Frist oder einen Zweig vor eine bestehende setzt, verschiebt die Zeile, die ein vorhandener Test trägt; Name und KDoc des Tests nennen dann noch die alte (M-1: Mutation B grün, Mutation E rot). Neue Beobachtung `BEO-TDS/test-traegt-andere-zeile-als-sein-kdoc`, 1×, **weiter offen**. (3) Gezählt, ohne Neuanlage: `BEO-TDS/aussagen-ueber-messung-hinaus` (jetzt 38×, verkörpert in `AGENTS.md` §3.8; L-1 trat nach der Verkörperung auf und ist behoben); `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor` (jetzt 6×, verkörpert als Handlung in `.claude/commands/implement-slice.md` Schritt 24; L-4 trat auf, weil der Lauf erst in der Closure kommt, und ist mit ihr behoben; ein Gate-Sensor bleibt Entscheidung des Nutzers); `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` (jetzt 14×, **geplant**, Träger `slice-qa-kompatibilitaet`). Ein Anker mit `liegt in` steht hier nicht, weil mit diesem Slice keine Regel mit Herkunfts-Anker verkörpert wurde; die Klassen über der Schwelle sind bereits verkörpert oder geplant, eine weitere Verkörperung wurde nicht vorgenommen.
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-tds8-verdrahtung.md` liegt in [BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test](../observations/BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test/observation.md) (M-2, L-3, Fehler `served`; Zähler 11), [BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md) (L-1, L-2; Zähler 38), [BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor](../observations/BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor/observation.md) (L-4; Zähler 6), [BEO-TDS/tds-8-0-und-pooling-nicht-gemessen](../observations/BEO-TDS/tds-8-0-und-pooling-nicht-gemessen/observation.md) (Zähler 14) und [BEO-TDS/test-traegt-andere-zeile-als-sein-kdoc](../observations/BEO-TDS/test-traegt-andere-zeile-als-sein-kdoc/observation.md) (neu, M-1; Zähler 1). Der Stand (`state.md`) bleibt für die vier vorhandenen Klassen unverändert und ist weiter zutreffend. L-5 (Flake `BootstrapTest`) ist nicht eingetragen: der Fix läuft im Implementer-Kontext, ein Eintrag entsteht dort oder mit dessen Closure.
- **Folge-Slices:** keine neuen. [slice-qa-kompatibilitaet](../open/slice-qa-kompatibilitaet.md) ist der Träger der Messung am Referenzclient (TDS 8.0, Pooling, Codec-Aussage).
- **Drei Paarungen:** Anker: der Eintrag trägt kein `liegt in`, die Paarung entfällt. Folge-Slice: [slice-qa-kompatibilitaet](../open/slice-qa-kompatibilitaet.md) existiert als Datei in `open/`. Register: jede genannte Kennung ist ein Verzeichnis mit nicht leerem `evidence/`.
- **Startbarkeit des Nachfolgers:** [slice-qa-kompatibilitaet](../open/slice-qa-kompatibilitaet.md) ist mit diesem Move **nicht startbar**. Erfüllt von den Start-Voraussetzungen: [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), das Gerüst [slice-qa-dotnet-referenztest-geruest](../done/slice-qa-dotnet-referenztest-geruest.md) in `done/` und `slice-tds8-verdrahtung` in `done/` (dieser Move). Offen: [ADR-0019](../../adr/0019-kompatibilitaetstests-zuschnitt.md) steht auf `Proposed` (der Start gilt erst mit `Accepted`, die DoD-Anpassung setzt der Planner danach), und [slice-sim-aenderung-integration](../in-progress/slice-sim-aenderung-integration.md) liegt zum Stand dieser Closure in `in-progress/`, nicht in `done/`. Dazu ein Verantwortlicher im Kopf und die Bewegung nach `next/` und `in-progress/`.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Das Package `runtime` ist ein Pfad-Cluster, trägt aber keine eigene Strukturregel und keine eigene Inventur-Linie: unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`). Treffer: `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` — geplant (Träger `slice-qa-kompatibilitaet`; dieser Slice ist die Voraussetzung der TDS-8.0-Messung; ein Beleg aus seiner Closure wird als Evidence ergänzt). Ohne Treffer: die übrigen Beobachtungen.

Alle berührten Sub-Areas GF.
