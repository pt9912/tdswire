# Slice slice-attention-cancel-timeout: Abbruchpfad mit Fristen: ATTENTION, genau ein Cancel, DONE[ATTN], CANCEL_TIMEOUT, QUERY_TIMEOUT mit TDS_TIMEOUT

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-fehler-abbruch

**Bezug:** [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md), [ADR-0004](../../adr/0004-session-zustandsautomat.md), [ADR-0007](../../adr/0007-fehler-und-attention.md), [ADR-0013](../../adr/0013-backend-grenzen.md)

**Berührte Spec-Stellen:** SPEC-001 · SPEC-002 · SPEC-009 · SPEC-016 · SPEC-022 · SPEC-029 · SPEC-031 · SPEC-033 · [TDS-FA-06.a](../../../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung) · ARC-015

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-10-30; zugeschnitten am 2026-11-01 nach [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md).

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Ein ATTENTION-Paket während `RUNNING` oder `STREAMING` löst genau einen Cancel an die Backendoperation aus, stoppt die regulären Zeilen und wird mit genau einer Bestätigung `DONE[ATTN]` beantwortet, ohne ERROR-Token und ohne zweite Nachricht ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Entscheidung 1 und 2); die Verbindung bleibt offen, die folgende Anfrage erhält nur ihre eigene Antwort, und ein Backend, das den Cancel nicht beachtet, führt nach `CANCEL_TIMEOUT` zum Schließen. Der Slice ist außerdem Träger der Frist einer Anfrage ([ADR-0013](../../adr/0013-backend-grenzen.md)): der Dispatch setzt `deadlineEpochMillis` auf Beginn plus `QUERY_TIMEOUT` und umschließt die Sammlung des Stroms mit einem Zeitlimit; der Ablauf geht den Weg des Cancel zum Backend und endet in ERROR `TDS_TIMEOUT` (Class 16, Number 50005) und DONE mit Fehlerstatus bei offener Verbindung. ATTENTION im Handshake schließt die Verbindung ohne Antwort. Die Antworten des Nutzers vom 2026-11-01 sind Setzungen, an keinem Server dieses Repos und nicht am Referenzclient gemessen; die Messung am Referenzclient trägt [slice-cancel-referenzclient-szenario](../done/slice-cancel-referenzclient-szenario.md).

**Zuschnitt (2026-11-01).** Der Slice trug fünf Liefer-Punkte, die Grenze ist drei. [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) schlägt den Schnitt vor, und der Planner folgt ihm: dieser Slice behält den **Abbruchpfad mit Fristen** (ehemals Punkte 1 bis 3); das Cancel-Szenario von `SPEC-040` und der Cancel-Teil der Messmethode von [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) am Referenzclient (ehemals Punkte 4 und 5) liegen in [slice-cancel-referenzclient-szenario](../done/slice-cancel-referenzclient-szenario.md); das Zurücksetzen (`RESETCONNECTION`) in [slice-resetconnection-zuruecksetzen](../done/slice-resetconnection-zuruecksetzen.md). Das Schließen nach `TDS_SHUTDOWN` nach ERROR und DONE, das der Vorschlag der ADR in den Abbruchpfad zählt, bleibt in [slice-abbruch-ressourcen-shutdown](../done/slice-abbruch-ressourcen-shutdown.md) (Setzung des Planners: es ist ein Übergang des geordneten Beendens, dessen Ablauf dort gebaut wird, und braucht den Abbruchpfad dieses Slice nicht).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Cancel-Szenario von `SPEC-040`, `SqlCommand.Cancel()`, `CancellationToken`, Command-Timeout, ATTENTION während des Streamings und `TDS_TIMEOUT` als Serverfehler am Referenzclient — [slice-cancel-referenzclient-szenario](../done/slice-cancel-referenzclient-szenario.md). Die Tests dieses Slice sind Rohbytes-Tests mit einem Fake-Backend und belegen den Referenzclient nicht; [CO-004](../../carveouts/done/CO-004-cancel-szenario-spec-040-fehlt.md) bleibt bis dort bestehen.
- `RESETCONNECTION` — [slice-resetconnection-zuruecksetzen](../done/slice-resetconnection-zuruecksetzen.md): anderer Auslöser, kein Abbruch, sondern eine Aktion am Übergang, der die Anfrage annimmt.
- Schließen nach `TDS_SHUTDOWN`, Cancel beim Disconnect und beim geordneten Beenden — [slice-abbruch-ressourcen-shutdown](../done/slice-abbruch-ressourcen-shutdown.md); dort wird derselbe Abbruchpfad aufgerufen, hier wird er gebaut.
- Die Zwei-Nachrichten-Form der Cancel-Antwort (SESSIONSTATE und `DONE[ERR]`, dann `DONE[ATTN]`, R11, R12 der Messung in [ADR-0007](../../adr/0007-fehler-und-attention.md)) — [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Entscheidung 1 baut sie nicht, bevor das Referenzclient-Szenario sie braucht; das entscheidet die Messung in [slice-cancel-referenzclient-szenario](../done/slice-cancel-referenzclient-szenario.md).
- ATTENTION im Leerlauf über die Bestätigung nach Entscheidung 1 hinaus, die Antwort auf unzulässige Übergänge außerhalb des Handshakes, Sitzungszustand nach einem Fehler — von [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) (Abschnitt „Offen“) weiter offen geführt; der Slice setzt die Entscheidungen um und entscheidet diese nicht, Grundregel A (schließen) bleibt dort Festlegung des Baus.
- Fehler-Mapping und Verbindungsfolge nach Fehlern — `slice-fehler-mapping`, `slice-fehler-zustandsuebergang` (beide in `done/`).
- Garantierte Unterbrechbarkeit beliebiger externer Backendoperationen — Out-of-Scope von [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe); der Slice belegt nur die Frist `CANCEL_TIMEOUT` und das Schließen.
- MARS und mehrere gleichzeitige Anfragen je Sitzung (`SPEC-004` lässt das offen).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [x] **ATTENTION, Cancel und Bestätigung.** ATTENTION wird als eigenes Paket erkannt, auch während Streaming; in `RUNNING` und `STREAMING` wird genau einmal Cancel an das Backend gegeben, es folgt keine reguläre Zeile mehr, es folgt genau eine Bestätigung `DONE[ATTN]` ohne ERROR-Token und ohne zweite Nachricht ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Entscheidung 1 und 2), und die Sitzung nimmt erst danach wieder eine Anfrage an. Der Zwischenschritt zwischen `RUNNING`/`STREAMING` und `IDLE` (`CANCELLING`) ist Zustand der Aufzählung oder Übergang mit Aktion; das entscheidet der Bau, der Zuschnitt-Trigger von [ADR-0015](../../adr/0015-spannungen-adr-gegen-spec.md) gilt weiter ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) „Wirkung auf den Zustandsautomaten“). Rohbytes-Tests mit suspendierendem und mit blockierendem Fake-Backend, ein Test mit unmittelbar folgender Anfrage, die nur ihre eigene Antwort erhält. ([TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) Happy Path, [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), `SPEC-029`) Dazu gehört der offene Punkt aus [ADR-0004](../../adr/0004-session-zustandsautomat.md), ob das Schreiben eines Pakets von der Abbruchanforderung unabhängig laufen muss (der Spike schrieb jedes Paket als `NonCancellable`, ein Lauf ohne diese Maßgabe wurde nicht gebaut; [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) führt den Punkt weiter offen): ein Test bricht mitten in einem Paket ab und prüft, dass kein halbes Paket auf der Leitung bleibt; die gewählte Form steht als Festlegung im KDoc. **E2E-Stufe:** Cancel mit unmittelbar folgender Abfrage am Referenzclient über die Prozessgrenze trägt [slice-cancel-referenzclient-szenario](../done/slice-cancel-referenzclient-szenario.md) (Stufe 3 und Stufe 7 des Produkt-E2E, Kennungen [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)); dieser Slice deklariert keine eigene Stufe, weil sein Nachweis Rohbytes gegen ein Fake-Backend im Modul ist (Risiko in §6).
- [x] **Wettlauf und ATTENTION im Handshake.** Der erste Übergang gewinnt: Kreuzen sich DONE und ATTENTION, oder die Frist des Kerns und ATTENTION ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Entscheidung 2), entsteht kein doppeltes DONE, keine zweite Antwort und kein zweiter Cancel; ein ATTENTION nach vollständig gesendetem Abschluss, auch nach dem Abschluss eines Timeouts, ist der Fall „ATTENTION nach Ende der Antwort“ mit der Bestätigung nach Entscheidung 1 und ohne Cancel; wiederholtes ATTENTION löst keinen zweiten Cancel aus. Der Wettlauftest läuft mit gesteuerter Reihenfolge statt Wiederholung bis zum Zufallsfund. ATTENTION in PRELOGIN und LOGIN7 (und TLS, soweit der Automat diesen Zustand führt) schließt die Verbindung ohne Antwortbytes ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Entscheidung 3, Grundregel A): ein Test je Handshake-Zustand, den `slice-handshake-login` liefert. ([TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) Boundary, `SPEC-001`, `SPEC-029`)
- [x] **Zeitlimits** `CANCEL_TIMEOUT` und Frist einer Anfrage, beide mit gesteuerter Uhr. (a) `CANCEL_TIMEOUT`: Ein Backend, das den Cancel nicht beachtet, führt nach der Frist zum Schließen der Verbindung mit geschlossenem Backend-Strom und freigegebener Sitzung; nur der vom Backend ignorierte Cancel schließt, nicht der Cancel selbst ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Entscheidung 2 Ausnahme b). (b) Frist ([ADR-0013](../../adr/0013-backend-grenzen.md)): der Dispatch setzt `deadlineEpochMillis` auf Beginn plus `QUERY_TIMEOUT` (heute `null`; der Test des SQL_BATCH-Dispatch hält das fest und ändert sich hier) und umschließt die Sammlung mit einem Zeitlimit; bei Ablauf geht der Lauf den Weg des Cancel (Strom abbrechen, `close()`, zurückgehaltenen Abschluss verwerfen) und endet in ERROR `TDS_TIMEOUT` (Class 16, Number 50005) und DONE mit Fehlerstatus bei offener Verbindung; ist der Strom nicht mehr sicher abzuschließen, schließt die Verbindung ohne ERROR und DONE (`SPEC-031`). Tests: Ablauf mit unmittelbar folgender Anfrage auf derselben Verbindung, Frist in der Vergangenheit, `null` ohne Zeitlimit, Backend ignoriert den Hinweis. `QUERY_TIMEOUT` und `CANCEL_TIMEOUT` kennt der Quelltext heute nicht als Einstellung (`SPEC-009` nennt `QUERY_TIMEOUT` konfigurierbar); der Weg der Einstellung gehört hierher, weil [slice-cancel-referenzclient-szenario](../done/slice-cancel-referenzclient-szenario.md) einen kurzen Wert am Serverprozess braucht (Risiko in §6). Mit dem Code von (b) wird der KDoc-Satz „offen“ zur Frist in `QueryRequest` durch die geltende Zusage ersetzt (nur Kommentar). (`SPEC-033`, `SPEC-009`, `SPEC-016`, `SPEC-022`)
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist. Der Spec-Nachzug nach [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) (Folgepflicht, Spec-Änderung durch den Implementer dieses Slice, nicht durch den Planner) gehört dazu und zählt nicht als Liefer-Punkt (Setzung des Planners: er folgt wortgleich der Entscheidung und wächst nicht mit dem Code): (a) `SPEC-016`, Zeile `TDS_TIMEOUT`: Aktion „ERROR und DONE mit Fehlerstatus, Verbindung offen“ statt „Fehler/Disconnect“; (b) Zuordnungstabelle in §4 der Spezifikation: `TDS_TIMEOUT` Class 16 (statt 20), offen, Herkunft „Setzung des Nutzers 2026-11-01“, die Sätze zur offenen Einzelzuordnung von `TDS_TIMEOUT` und „offen, ob Cancel und Timeout als Fehler gelten“ entfallen; (c) `TDS-FA-06.a` Nr. 6: `TDS_TIMEOUT` gehört zur Anfragefehler-Gruppe, „Noch festzulegen“ entfällt; (d) `SPEC-029`: die Cancel-Antwort ist `DONE[ATTN]` allein, Cancel ist kein Fehler; (e) `SPEC-001`, nur der Teil „ATTENTION im Handshake schließt ohne Antwort“ (der Teil `RESETCONNECTION` liegt in [slice-resetconnection-zuruecksetzen](../done/slice-resetconnection-zuruecksetzen.md)); (h) Absatz „Offene Punkte des Backend-Vertrags“, letzter Punkt (Class von `TDS_TIMEOUT`); dazu die Zeile 12 der bekannten Lücken in der Kompatibilitätsmatrix („Wirkung von ATTENTION“) und der Test, der `TDS_TIMEOUT` heute als Class 20 festhält (`ErrorMapperTest`, `DispatchFailureTest`). Der Nachzug (g) zu `SPEC-044` und `SPEC-049` liegt in [slice-abbruch-ressourcen-shutdown](../done/slice-abbruch-ressourcen-shutdown.md), die Nachzüge (f) und (i) in [slice-resetconnection-zuruecksetzen](../done/slice-resetconnection-zuruecksetzen.md).
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
| Anfragezustand (Response Sequencer, ARC-015) | update | Zwischenschritt `CANCELLING` (Zustand oder Übergang mit Aktion), Übergänge `RUNNING`/`STREAMING` → `CANCELLING` → `COMPLETED`, Race-Regel |
| `src/main/kotlin/io/github/pt9912/tdswire/transport/` (Nachrichtenleser) | update | ATTENTION als eigenes Paket erkennen, auch bei laufendem Schreiben; an den Sequencer melden; **Ist-Zustand:** kein Eingriff in `transport/`, das gleichzeitige Lesen liegt in `dispatch/InboundTray` (Zeile darunter) |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/session/` (Package-Name ein Vorschlag) | update | Fristüberwachung `CANCEL_TIMEOUT`, Schließen der Verbindung; ATTENTION in den Handshake-Zuständen schließt ohne Antwort |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/` (`RequestLoop`) | update | Frist setzen (Beginn plus `QUERY_TIMEOUT`) und Zeitlimit um die Sammlung des Stroms ([ADR-0013](../../adr/0013-backend-grenzen.md)); Ablauf in `TDS_TIMEOUT` |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorCode.kt` | update | `TDS_TIMEOUT`: Class 16, Verbindung offen (Anfragefehler-Gruppe) statt Class 20 und Schließen |
| `src/main/kotlin/io/github/pt9912/tdswire/config/` (Package-Name ein Vorschlag) | update | Einstellung für `QUERY_TIMEOUT` und `CANCEL_TIMEOUT` mit Bereichsprüfung ([slice-cancel-referenzclient-szenario](../done/slice-cancel-referenzclient-szenario.md) setzt einen kurzen `QUERY_TIMEOUT` am Serverprozess) |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/session/` | neu | Rohbytes-Tests mit Fake-Backend: Cancel einmal, Race, Frist, Handshake ([TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)) |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/error/ErrorMapperTest.kt`, `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/DispatchFailureTest.kt` | update | halten `TDS_TIMEOUT` als Class 20 fest; ändern sich mit der Setzung |
| `spec/spezifikation.md`, `docs/plan/kompatibilitaetsmatrix.md` | update | Spec-Nachzug (a) bis (e) und (h) im Doku-Update von §2, Zeile 12 der Lücken |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/session/` (`ConnectionState`, `HandshakeMatrix`, `HandshakeAutomaton`, `HandshakeAction`, `HandshakeEvent`, `HandshakeSession`, neu `AttentionAck`) | update | `CANCELLING` als Zustand der Aufzählung, Ereignisse ATTENTION in `RUNNING`/`STREAMING` und Fristablauf als gemeinsamer Auslöser (Aktion `Cancel`, der erste Übergang gewinnt), Bestätigung `DONE[ATTN]`, ATTENTION in `READY` mit Bestätigung statt Schließen, Schreibsperre der regulären Antwort ab `CANCELLING`, Paket-Schreiben unabhängig von der Abbruchanforderung |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/` (neu `InboundTray`, `RequestTimes`) | update, neu | gleichzeitiges Lesen während der Anfrage (ein ausstehender Lesevorgang bleibt bis zur nächsten Nachricht stehen), Abbruchlauf, `CANCEL_TIMEOUT` |
| `src/main/kotlin/io/github/pt9912/tdswire/config/` (`ServerConfig`, `ConfigValidation`), `src/main/kotlin/io/github/pt9912/tdswire/runtime/` (`ConfigLoader`, `Bootstrap`, `ConnectionWiring`) | update | Weg der Einstellung: `QUERY_TIMEOUT` und `CANCEL_TIMEOUT` als Schlüssel der Umgebung, Bereichsprüfung, Voreinstellung nach `SPEC-009` und `SPEC-033`, Verdrahtung in die Anfrageschleife |
| `src/main/kotlin/io/github/pt9912/tdswire/domain/QueryRequest.kt` | update (nur Kommentar) | KDoc zur Frist: geltende Zusage statt „offen“ |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/` (neu `AttentionCancelTest`, `QueryTimeoutTest`, `CancelTimeoutTest`), `src/test/kotlin/io/github/pt9912/tdswire/protocol/session/` (`HandshakeMatrixTest`, neu `AttentionHandshakeTest`), `src/test/kotlin/io/github/pt9912/tdswire/config/ConfigValidationTest.kt`, `src/test/kotlin/io/github/pt9912/tdswire/runtime/ConfigLoaderTest.kt` | neu, update | Belege zu `TDS-FA-07` (Happy Path, Boundary), `TDS-QA-07`, `SPEC-029`, `SPEC-001`, `SPEC-016`, `SPEC-033`, `SPEC-009` |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf, inklusive einer Testdatei-Zeile mit der Akzeptanzkriterien-ID in `Begründung`.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Der Start-Trigger der Welle `welle-fehler-abbruch` ist eingetreten, `slice-fehler-zustandsuebergang` liegt in `done/`, [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) steht im ADR-Index auf `Accepted` (erfüllt; damit liegen die Nutzerentscheidungen zum Inhalt der Cancel-Antwort, zu Cancel und Timeout als Fehler und zu ATTENTION im Handshake vor), und ein Verantwortlicher ist im Kopf eingetragen.

**Hinweis zum Carveout:** [CO-004](../../carveouts/done/CO-004-cancel-szenario-spec-040-fehlt.md) (Entscheidung des Nutzers 2026-10-31) nennt als Folge-Slice den Slice, der das Cancel-Szenario am Referenzclient liefert: seit dem Zuschnitt vom 2026-11-01 ist das [slice-cancel-referenzclient-szenario](../done/slice-cancel-referenzclient-szenario.md). Dieser Slice ist dessen Voraussetzung; der Carveout ist erst aufgelöst, wenn [slice-cancel-referenzclient-szenario](../done/slice-cancel-referenzclient-szenario.md) in `done/` liegt und das Szenario besteht.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2, oder der dritte Punkt (Zeitlimits) sprengt den Rahmen, auch durch den Weg der Einstellung von `QUERY_TIMEOUT`; Schnittvorschlag: Frist und Zeitlimit (`QUERY_TIMEOUT`, Teil (b)) als eigener Slice hinter diesem, der den Abbruchpfad des Cancel nutzt. Auslöser, am Slice-Plan beobachtbar: die Häkchen der DoD-Punkte 1 und 2 sind gesetzt, Teil (a) von Punkt 3 steht mit grünem Test, und für Teil (b) gibt es noch keinen Test. Dann wird (b) mit einem neuen Slice-Plan herausgelöst, Punkt 3 dieses Slice schrumpft auf (a), und die Zeile zur Frist in §6 geht mit.
- `in-progress` → `open` (blockiert — Carveout?): Der Port bietet kein `cancel`, das ein Fake-Backend beobachtbar macht (`spec/spezifikation.md` `SPEC-022` führt `cancel(sessionId, requestId)` als Entwurf), oder der Anfragezustand der Welle `welle-anfrageverarbeitung` lässt `CANCELLING` nicht zu.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün (einschließlich `make test`) und die Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Die Inhalte der Cancel-Antwort (`DONE[ATTN]` allein), die Class 16 von `TDS_TIMEOUT` mit offener Verbindung und das Schließen bei ATTENTION im Handshake sind Setzungen des Nutzers vom 2026-11-01 ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md)); der Slice baut sie und belegt sie mit Rohbytes gegen ein Fake-Backend, nicht am Referenzclient und nicht an einem echten SQL Server. Die Messung am Referenzclient trägt [slice-cancel-referenzclient-szenario](../done/slice-cancel-referenzclient-szenario.md); ein Fehlverhalten dort ist ein Re-Evaluierungs-Fall von [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md). — **Ausgang:** weiter offen: die Inhalte der Cancel-Antwort, Class 16 und das Schließen im Handshake sind Setzungen, mit Rohbytes gegen ein Fake-Backend belegt; die Messung trägt [slice-cancel-referenzclient-szenario](../done/slice-cancel-referenzclient-szenario.md) (Re-Evaluierungs-Fall von [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md), [CO-004](../../carveouts/done/CO-004-cancel-szenario-spec-040-fehlt.md) bleibt)
- Am echten Server ist nur ATTENTION nach vollständig gesendeter Antwort gemessen (R13), im Leerlauf nicht; wann der Server im Leerlauf antwortet, bleibt Setzung. — **Ausgang:** weiter offen: → `BEO-TDS/messung-am-echten-sql-server-fehlt` (Leerlauf am echten Server nicht gemessen)
- Das Verhalten bei fehlender oder falscher Bestätigung (Client hängt bis zum Timeout) ist am Testserver gemessen, die Wirkung unter Pooling und TDS 8.0 nicht. — **Ausgang:** weiter offen: → `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`
- Das Fehlen einer E2E-Stufe in diesem Slice (Nachweis nur Rohbytes im Modul) darf nicht als Lücke von [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) und [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) stehen bleiben: die Stufen liegen in [slice-cancel-referenzclient-szenario](../done/slice-cancel-referenzclient-szenario.md). Wird dieser Slice vor jenem geschlossen, trägt die Sicht `docs/plan/e2e-abdeckung.md` die Lücke weiter. — **Ausgang:** weiter offen: keine E2E-Stufe für Cancel; Träger [slice-cancel-referenzclient-szenario](../done/slice-cancel-referenzclient-szenario.md), bis dahin trägt die Lücke `docs/plan/e2e-abdeckung.md`
- Wettlauftests, die nur durch Wiederholung Treffer erzeugen, sind Zufallsbelege; die DoD verlangt gesteuerte Reihenfolge. — **Ausgang:** entfallen für die Tests mit gesteuerter Reihenfolge (Eingriffe der Senke, gesteuerte Pausen; Mutationen rot); Ausnahme `AttentionWireTest > ATTENTION mitten in einem Paket` (acht `yield()`-Hops, Verifikation F-2): → `BEO-TDS/mutation-haengt-an-ungesteuerter-reihenfolge`
- Ein Backend ist nach dem Port-Vertrag suspendierend, ein blockierender Treiber liegt hinter der Umhüllung des Integrators ([ADR-0013](../../adr/0013-backend-grenzen.md)); der Fake-Backend-Test belegt `CANCEL_TIMEOUT` und die Frist, nicht das Verhalten eines echten blockierenden Treibers. Die Messungen der Frist (Überschreitung 0 bis 3 ms, bei nicht unterbrechbarem Aufruf bis zu dessen Ende) stammen aus einem Spike mit anderen Versionen und gelten nicht für diesen Quelltext. — **Ausgang:** weiter offen: Fake-Backend statt echtem blockierendem Treiber, die Spike-Messungen gelten nicht für diesen Quelltext: → `BEO-TDS/test-belegt-nur-den-fake`
- Zwei Auslöser für denselben Abbruch (ATTENTION des Clients, Frist des Kerns) gehen denselben Weg des Cancel; ohne gemeinsamen Pfad entstünde ein doppeltes DONE oder ein doppelter Cancel. Der Wettlauftest des zweiten Punkts nimmt die Frist als zweiten Auslöser auf (gesteuerte Reihenfolge). — **Ausgang:** entfallen: ein gemeinsamer Pfad (`AbortableRun`), nur der erste Auslöser gewinnt; der Wettlauftest nimmt die Frist als zweiten Auslöser auf (Verifikation: `AbortableRun.watch` ohne `tray.drop()` und `if (first != null) job.cancel()` entfernt, beide rot)
- Der Weg der Einstellung von `QUERY_TIMEOUT` und `CANCEL_TIMEOUT` (Schlüssel, Bereich, Ort) ist weder in `SPEC-009` noch in [ADR-0013](../../adr/0013-backend-grenzen.md) festgelegt; er wächst in Punkt 3 hinein und löst die Rückführung in `next` aus, wenn er den Rahmen sprengt. — **Ausgang:** entfallen: der Weg der Einstellung (`ConfigKeys`, Bereich 1 bis 3600, Voreinstellung 30 s und 5 s, Loader, Bootstrap, `ConnectionWiring`) passte in Punkt 3, die Rückführung in `next` wurde nicht ausgelöst

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

**Gegenstand:** geliefert (Abbruchpfad mit Fristen: ATTENTION als eigenes Paket, genau ein Cancel, `DONE[ATTN]` allein, `CANCEL_TIMEOUT`, `QUERY_TIMEOUT` mit `TDS_TIMEOUT` Class 16 bei offener Verbindung, ATTENTION im Handshake schließt, Weg der Einstellung). Commits: Pfadliste `b634b6b`, Abbruchpfad `ce1819e`, Wiring `ca5688c`, Spec-Nachzug `0000999`, Fix-Runde nach Review `0ee107a`, Spec `018ebac`, Fix-Runde nach Verifikation `a7a0a0a`, Merge von `main` mit dem Testserver-Umbau `5cc30f8`.

**Gemessen** (Einzelmessungen am 2026-11-01 auf einem Host mit Last durch andere Agenten, Docker-Cache teils warm, kein Beleg für andere Hosts):

- Verifikation `8cacbb7` (eigene Läufe): `make gates` Exit 0 in 9 s (alle Stages aus dem Cache, Warmwert); `make matrix-zitate` Exit 0 in 92 s, 872 bestandene Testfälle, 237 Zitate, 0 ohne PASSED; `make e2e` Exit 0 in 294 s, sieben Stufen grün, kein Cancel-Beleg.
- Mutationen: Review 16 (drei überlebend: verlierendes ATTENTION, `remaining == 0`, lockfreies Schließen in `abandon()`; die ersten zwei nach `0ee107a` tot); Verifikation 7 eigene, sechs rot, eine überlebend (`DEFAULT_CANCEL_TIMEOUT` 5 s auf 6 s, F-4, behoben in `a7a0a0a`).
- Implementer-Läufe auf dem Stand `5cc30f8` (nicht von der Verifikation wiederholt): `make build` 48 s, `make gates` 265 s, `make e2e` 272 s mit sieben Stufen grün, `make matrix-zitate` 94 s grün, Mutation `QUERY_TIMEOUT` im Lader des Testservers rot gesehen.
- Closure `5cc30f8` plus Closure-Dateien: `make gates` Exit 0 in 9,2 s (Einzelmessung; alle Docker-Stages aus dem Cache, weil sich seit dem Implementer-Lauf nur Dokumente änderten; kein Maß der Laufzeit, der echte Lauf stand bei 265 s); `make spec-abdeckung` Exit 0, `docs/plan/spec-abdeckung.md` neu geschrieben, 12 Festlegungen ohne Nachweis (Ausgabe des Erzeugers, nicht im Einzelnen geprüft).

**Nicht unabhängig verifiziert: Delta `a7a0a0a..5cc30f8`.** Die Verifikation lief auf `8cacbb7`. Seither: (1) Fix-Commit `a7a0a0a` (fünf Dateien, davon vier unter `src` und `spec` mit 8 Zeilen hinzu und 6 weg: KDoc `RequestLoop`, Meldungstext `DispatchFailureTest`, Konstanten von `RequestTimes` aus `ConfigDefaults`, ein Satz `SPEC-029`, dazu der Verifikationsbericht); (2) Merge `5cc30f8` von `main` (`4a6d88e`, Umbau `slice-testserver-herausloesen`): `git diff 8cacbb7..HEAD --stat` zeigt 96 Dateien (+1969, −967), davon nahezu alles der Merge: Testserver-Source-Set, Lader und Beschreibungsleser dorthin verschoben, `tools/`-Skripte, Spec- und Slice-Dokumente. Zusammenführung von Hand: der Lader des Testservers nimmt `QUERY_TIMEOUT` und `CANCEL_TIMEOUT`, `ServerConfig.requests` ist wieder ein Konstruktorparameter ohne `var`, `EnvironmentConfig.kt` liegt im Testbestand. Belegt nur durch die oben genannten Implementer-Läufe, ohne eigenen Review und ohne eigene Mutation des Delta außer der einen.

**Handvektor:** `DONE_ATTN` (13 Byte, im Test `BatchVectors.DONE_ATTN` als Hex verglichen) und der ERROR-Token von `TDS_TIMEOUT` (50005, Class 16, State 1, „Timeout.“, Beginn `AA 1E 00 55 C3 00 00 01 10`) in `ErrorMapperTest` sind Handvektoren **ohne Quelle** (kein Standard-Vektor, nicht am Referenzclient oder echten Server gemessen); die Praxis gleicht `DONE_ERROR`.

**Selbstkonsistenz:** Rohbytes-Tests gegen Fake-Backends mit demselben Codec, der die Antwort erzeugt; `AttentionAck` und `HandshakeMatrix` sind gegen Handtabellen (150 Zellen) gehalten, nicht gegen einen fremden Server.

**Festlegung (Setzungen):** (1) Die Antworten des Nutzers vom 2026-11-01 ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md)): `DONE[ATTN]` allein, Class 16 für `TDS_TIMEOUT` bei offener Verbindung, Schließen bei ATTENTION im Handshake. (2) Jedes weitere ATTENTION wird einzeln bestätigt („N ATTENTION ergeben N Bestätigungen“, `SPEC-029`) — ungemessen, siehe Spec-Lücke unten. (3) Der erste Übergang gewinnt, Auslöser sind ATTENTION und Frist über einen Pfad. (4) Voreinstellung `QUERY_TIMEOUT` 30 s und `CANCEL_TIMEOUT` 5 s, Bereich 1 bis 3600 (`SPEC-009`, `SPEC-033`), auch für Einbettende ohne Angabe (`HandlerSetup.requests`; Verhaltensänderung des öffentlichen Einstiegs, Review F12). (5) Ein Schreibvorgang hält die Sitzungssperre bis zum Ende des `write`; ein Schreibvorgang am Rückstau hemmt Frist und ATTENTION (Grenze in `SPEC-029`/`SPEC-033`, ungetestet gegen echten Rückstau).

**Abweichungen von Plan und DoD-Wortlaut:** (a) `CANCELLING` ist Zustand der flachen Aufzählung (nicht Übergang mit Aktion), die Wahl ließ der DoD dem Bau ([ADR-0015](../../adr/0015-spannungen-adr-gegen-spec.md)-Trigger nicht ausgelöst). (b) Die Schleife liest während der Anfrage voraus (`InboundTray`: ein ausstehender Lesevorgang bleibt stehen); der Plan hatte dafür `transport/` vorgesehen, dort wurde nichts geändert. (c) Bestandstests abgeschwächt: `SqlBatchDispatchTest` und `DispatchFateTest` belegen die Annahme nur aus `READY` indirekt über den Zustand, die Zusage trägt `HandshakeMatrixTest` (Review F7; die Matrix trägt „Annahme nur aus READY“). (d) `HandlerSetup.requests` ist `var`; `ServerConfig.requests` war zeitweise `var` neben dem Konstruktor (F9) und ist seit dem Merge wieder Konstruktorparameter. (e) Plan §3: `transport/` „update“ im Ist-Zustand ohne Eingriff, in der Tabelle vermerkt. (f) Die Doku-Haken (f), (g) und (i) liegen in [slice-resetconnection-zuruecksetzen](../done/slice-resetconnection-zuruecksetzen.md) und [slice-abbruch-ressourcen-shutdown](../done/slice-abbruch-ressourcen-shutdown.md), nicht angefasst; (a) bis (e) und (h) laut Verifikation bestätigt.

**Nicht gemessen:** Referenzclient (`Cancel()`, `CancellationToken`, Command-Timeout, ATTENTION im Streaming, `TDS_TIMEOUT` als `SqlException`); ein echter SQL Server (Leerlauf-ATTENTION, Cancel-Antwort); ein echter blockierender Treiber hinter der Umhüllung des Integrators; N Bestätigungen am Client; Frist bei echtem Rückstau (TCP); Pooling und TDS 8.0 unter Cancel; Kaltlauf der Gates; Zwischenstände der Commit-Folge.

**Review und Verifikation:** Review `0000999` (kein HIGH; F1 bis F3 MEDIUM, F4 bis F9 LOW, F10 bis F12 INFO; behoben in `0ee107a`, `018ebac`), Verifikation `8cacbb7`: closure-fähig mit Bedingungen (M1 KDoc `RequestLoop`: behoben in `a7a0a0a`; M2 diese Notiz; M3 Folgepflicht, unten). Berichte: `docs/reviews/2026-11-01-0000999-slice-attention-cancel-timeout.md`, `docs/reviews/2026-11-01-8cacbb7-verifikation-slice-attention-cancel-timeout.md`. Offen aus der Verifikation, nicht behoben: F-2 (`yield()`-Hops im Test), F-3 (`AbbruchWiringTest` prüft nur die Untergrenze 1 s gegen die echte Uhr, die Obergrenze entfiel; kein Befund mehr), F-5 (SPEC-029 enger als der Code, harmlos).

**Ausgang der Risiken in §6:** in §6 je Zeile eingetragen: Cancel-Antwort-Inhalte, fehlende E2E-Stufe, Fake statt Treiber, Leerlauf am echten Server, Pooling und TDS 8.0: weiter offen; Wettlauftests: entfallen mit einer Ausnahme (F-2); zwei Auslöser ein Pfad, Weg der Einstellung: entfallen.

**Steering-Loop-Eintrag:** Kein Eintrag verkörpert, daher kein `liegt in`. Gelernt: (1) Benannte Spec-Lücke: „Jedes weitere ATTENTION wird einzeln bestätigt“ (`SPEC-029`, Setzung) ist an keinem Client gemessen; ein Client mit zwei Abbruchauslösern könnte das zweite `DONE[ATTN]` seiner Folgeabfrage zuordnen ([TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)). Die Folgepflicht steht in [slice-cancel-referenzclient-szenario](../done/slice-cancel-referenzclient-szenario.md) (DoD und §6): N Bestätigungen, `DONE[ATTN]` allein, ATTENTION im Leerlauf; Fehlverhalten dort ist ein Re-Evaluierungs-Fall von [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md), [CO-004](../../carveouts/done/CO-004-cancel-szenario-spec-040-fehlt.md) bleibt. (2) Eine Spec-Zusage über eine Frist trägt nur mit ihrer Bedingung (Rückstau hält die Sperre): dieselbe Klasse wie `BEO-TDS/aussagen-ueber-messung-hinaus`, bereits verkörpert; der Review fand sie trotzdem (F2). (3) Eine Voreinstellung in zwei Quellen ließ eine Mutation überleben (F-4): Klasse `BEO-TDS/zahl-an-mehreren-orten-ohne-sensor`; Vorschlag, nicht gesetzt: ein Test, der die Voreinstellung der Einstellung und des Einstiegs gegeneinander hält.

**Register:** Evidenz `slice-attention-cancel-timeout.md` ergänzt zu [BEO-TDS/test-belegt-nur-den-fake](../observations/BEO-TDS/test-belegt-nur-den-fake/observation.md), [BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test](../observations/BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test/observation.md), [BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md), [BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen](../observations/BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen/observation.md), [BEO-TDS/zahl-an-mehreren-orten-ohne-sensor](../observations/BEO-TDS/zahl-an-mehreren-orten-ohne-sensor/observation.md), [BEO-TDS/mutation-haengt-an-ungesteuerter-reihenfolge](../observations/BEO-TDS/mutation-haengt-an-ungesteuerter-reihenfolge/observation.md). Zitiert ohne neue Evidenz: [BEO-TDS/messung-am-echten-sql-server-fehlt](../observations/BEO-TDS/messung-am-echten-sql-server-fehlt/observation.md), [BEO-TDS/tds-8-0-und-pooling-nicht-gemessen](../observations/BEO-TDS/tds-8-0-und-pooling-nicht-gemessen/observation.md). Neu: keine, alle Treffer passten auf vorhandene Beobachtungen. Zähler nicht gesetzt.

**Folge-Slice:** [slice-cancel-referenzclient-szenario](../done/slice-cancel-referenzclient-szenario.md) (Start-Trigger mit diesem Slice in `done/` erfüllt; Folgepflicht M3 eingetragen, Stufennummern auf die sieben Stufen von `make e2e` nachgezogen), [slice-resetconnection-zuruecksetzen](../done/slice-resetconnection-zuruecksetzen.md) (Stufennummer nachgezogen), [slice-abbruch-ressourcen-shutdown](../done/slice-abbruch-ressourcen-shutdown.md).

**Drei Paarungen:** Das Repo führt Wellen; die Paarungen prüft die Welle-Closure. Anker ↔ Folge-Slice: kein Anker gesetzt. Register ↔ Closure-Notiz: getragen (Pfade oben). Folge-Slice: genannt.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Die berührten Packages (`protocol`, `transport`, `config`) sind Pfad-Cluster (Achse 3), tragen aber keine eigene Strukturregel (Achse 1) und keine eigene Inventur-Linie (Achse 2): eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, Stand des gemergten `main` am 2026-11-01); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 25×, Stand *geplant*. Treffer: ATTENTION im Leerlauf, Cancel-Antwort am Server dieses Repos (§6).
- `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` — 17×, Stand *offen* (über der Schwelle, ohne Ausgang, Entscheidung beim Nutzer). Treffer: Pooling und TDS 8.0 für Cancel und Timeout (§6).
- `BEO-TDS/aussagen-ueber-messung-hinaus` — 45×, Stand *verkörpert*. Treffer: Rohbytes-Test mit Fake-Backend belegt nicht den Referenzclient.
- `BEO-TDS/zustandspunkte-nach-fehler-ohne-entscheidung` — 1×, offen. Treffer: unzulässige Übergänge und Sitzungszustand nach Fehler bleiben nach [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) offen (§1).
- `BEO-TDS/test-belegt-nur-den-fake` — 4×, Stand *verkörpert*. Treffer: Fake-Backend-Tests für `CANCEL_TIMEOUT` und die Frist (§6).
- `BEO-TDS/mutation-haengt-an-ungesteuerter-reihenfolge` — 1×, offen. Treffer: Wettlauftest mit gesteuerter Reihenfolge (Punkt 2).
- Ohne Treffer: die übrigen Einträge des Registers.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
