# Slice slice-abbruch-ressourcen-shutdown: Ressourcenfreigabe bei Disconnect und geordnetes Beenden mit TDS_SHUTDOWN

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-fehler-abbruch

**Bezug:** [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [ADR-0004](../../adr/0004-session-zustandsautomat.md), [ADR-0007](../../adr/0007-fehler-und-attention.md), [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md)

**Berührte Spec-Stellen:** SPEC-029 · SPEC-031 · SPEC-033 · SPEC-034 · SPEC-044 · SPEC-047 · SPEC-049 · ARC-015

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-10-30.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Ein abrupter oder regulärer Verbindungsabbruch in jedem Zustand gibt Cancel, Backend-Strom und Sitzungsressourcen frei, ohne den Prozess zu gefährden, und das geordnete Beenden nach `SPEC-044` lässt laufende Anfragen bis `SHUTDOWN_GRACE` abschließen, beantwortet spätere Anfragen mit `TDS_SHUTDOWN` und kehrt nach spätestens `SHUTDOWN_GRACE` plus `CANCEL_TIMEOUT` zurück. Die Antwort `TDS_SHUTDOWN` wird vollständig gesendet (ERROR, dann DONE mit Fehlerstatus) und danach geschlossen: das Ende der Antwort führt in einem Übergang nach `CLOSED`, geschlossen wird nach dem Schreiben ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Entscheidung 4, Setzung des Nutzers vom 2026-11-01). Diesen Übergang schlägt die ADR dem Abbruchpfad zu; der Planner legt ihn hierher (Zuschnitt vom 2026-11-01), weil er ein Schritt des geordneten Beendens ist und den Abbruchpfad von [slice-attention-cancel-timeout](../done/slice-attention-cancel-timeout.md) nicht braucht.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Der Abbruchpfad selbst (Cancel, `CANCELLING`, Frist) — `slice-attention-cancel-timeout`; dieser Slice ruft ihn bei Disconnect und nach Ablauf der Shutdown-Frist.
- Listener, Sockets und Erkennen des Verbindungsendes im Transport — Welle `welle-transport-tls`; dieser Slice nimmt das Verbindungsende als Ereignis und den Listener-Stop als Aufruf.
- Fuzz-Gate (defekte Paketlängen, Offsets und Typwerte, Zerstückelung) — `slice-qa-stabilitaet` (geschlossen, Anmeldepfad); hier ein Lecktest mit mehreren Verbindungen, kein Gate. Die Durchsetzung von `MAX_CONNECTIONS` und der Test bis zur Grenze gehören dagegen **hierher** (DoD-Punkt 1): `slice-qa-stabilitaet` hat die Grenze nicht gebaut und schiebt sie auf diesen Slice zurück.
- Observability von Verbindungsende und Shutdown (`SPEC-017`) — Träger: Welle [welle-betrieb-diagnose](../welle-betrieb-diagnose.md); nur der minimale Log-Anschluss.
- Garantierte Unterbrechbarkeit externer Backendoperationen — Out-of-Scope von [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe).
- Zeitgleichheit und Anmeldepfad-Sicherheit — `slice-qa-sicherheit`; Drosselung — `slice-qa-anmeldedrosselung`.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [x] Disconnect in jedem Zustand (Handshake, `IDLE`, `RUNNING`, `STREAMING`, `CANCELLING`; abrupter TCP-Abbruch und reguläres Schließen): Cancel höchstens einmal, Backend-Strom geschlossen, Sitzung entfernt, keine Schreibversuche auf die tote Verbindung, der Prozess läuft weiter; Lecktest mit vielen Verbindungen am Fake-Backend (Zähler offener Ströme und Sitzungen). Dazu gehört der offene Punkt aus [ADR-0004](../../adr/0004-session-zustandsautomat.md): Bei 50 harten Abbrüchen (RST) während `RUNNING` und `STREAMING` traten im Spike nicht abgefangene `IOException: Broken pipe` in den Threads der Kanalgruppe auf (Ursache im Spike-Adapter, nicht analysiert); ein Test mit hartem Abbruch zählt unbehandelte Ausnahmen in den Threads der Laufzeit, und ihre Zahl ist 0 oder die Ursache steht in der Closure-Notiz. **Grenze `MAX_CONNECTIONS`** (`SPEC-034`, Träger aus `slice-qa-stabilitaet`, das sie nicht geliefert hat): Der Listener nimmt höchstens `MAX_CONNECTIONS` gleichzeitige Verbindungen auf (Konfigurationsschlüssel mit Bereichsprüfung, Voreinstellung 1000 vorläufig; gezählt wird dort, wo die Aufnahme heute zählt, im Verbindungsregister des Listeners); eine Verbindung über der Grenze wird abgewiesen, ohne dass der Prozess oder eine bestehende Verbindung leidet, und nach dem Ende einer Verbindung nimmt der Listener wieder auf. Der Test läuft bis zur Grenze mit einer kleinen Grenze (genau `MAX_CONNECTIONS` Verbindungen sind zugleich im Handler, die nächste wird abgewiesen, danach wird nach dem Ende einer Verbindung wieder aufgenommen) und mit dem Verbindungssturm aus `slice-qa-stabilitaet` über der Grenze. ([TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) Negative und Boundary, [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), `SPEC-029`, `SPEC-034`)
- [x] Geordnetes Beenden nach `SPEC-044` Nr. 1 bis 5 mit gesteuerter Uhr: Listener schließt zuerst; Verbindung ohne laufende Anfrage endet sofort; laufende Anfrage schließt innerhalb der Frist regulär ab; eine Anfrage nach dem Beginn wird mit ERROR `TDS_SHUTDOWN` und DONE mit Fehlerstatus beantwortet, danach endet die Verbindung (der Test liest ERROR und DONE vollständig, bevor das Schließen sichtbar wird, [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Entscheidung 4; auch nach `DONE[ATTN]` endet die Verbindung im Beenden, Entscheidung 2 Ausnahme c); nach Ablauf der Frist Abbruch wie bei ATTENTION, nach `CANCEL_TIMEOUT` Trennen; Rückkehr nach spätestens Frist plus Abbruchfrist; zweite Anforderung wirkungslos; `SHUTDOWN_GRACE` = 0 sofort. ([TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) Beenden)
- [x] Der Stop des Bootstrap (`slice-bootstrap-konfiguration`) ruft diesen Ablauf statt des Stops ohne Frist-Logik; Rohbytes-Smoke: Prozess-Stop mit einer idle Verbindung und einer laufenden Anfrage. (Folge-Slice aus `welle-handshake-login` §6, geordnetes Beenden) **E2E-Stufe:** Der Prozess-Stop mit einer laufenden Anfrage, der abrupte TCP-Abbruch gegen den Serverprozess und ein beschädigtes PRELOGIN mit anschließend erreichbarem Prozess (`make laufzeittest` stoppt heute ohne laufende Anfrage) ist als Stufe im Produkt-E2E ([slice-e2e-geruest](../done/slice-e2e-geruest.md)) mit den Kennungen [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung) deklariert und ruft den Lauf über die Prozessgrenze (Referenzclient oder Rohbytes-Client gegen den Serverprozess), nicht über `internal`-Zugriff im Modul; `make e2e-abdeckung` zeigt sie danach in `docs/plan/e2e-abdeckung.md`. Dass dieser Slice die Prozess-Stufe für [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität) und das beschädigte PRELOGIN von [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung) trägt, ist die Zuordnung des Planners, weil kein anderer offener Slice sie am Prozess trägt; trägt er sie nicht, braucht der Gegenstand einen eigenen Slice.
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist. Der Spec-Nachzug (g) nach [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) (Folgepflicht, Spec-Änderung durch den Implementer dieses Slice, nicht durch den Planner) gehört dazu und zählt nicht als Liefer-Punkt: `SPEC-044` und `SPEC-049` kennzeichnen die Herkunft des Schließens nach ERROR und DONE (Setzung des Nutzers 2026-11-01).
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
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/` (`AbortableRun`, `AbortedAnswer`, `InboundTray`, `RequestLoop`, neu `ShutdownGate`) | update, neu | Disconnect während einer Anfrage erkannt (auch in `CANCELLING`), `TDS_SHUTDOWN` für eine nach dem Beginn angenommene Anfrage, Abbruch wie bei ATTENTION nach `SHUTDOWN_GRACE`, Meldung „Anfrage läuft“ an den Listener |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/session/` (`ConnectionState`, `HandshakeAction`, `HandshakeEvent`, `HandshakeAutomaton`, `HandshakeMatrix`) | update | Ereignisart `SHUTDOWN_GRACE_EXPIRED` und Abbruchgrund `SHUTDOWN` als dritter Auslöser neben ATTENTION und Frist |
| `src/main/kotlin/io/github/pt9912/tdswire/runtime/` (`Bootstrap`, `ConnectionWiring`, neu `ConnectionShutdownGate`) | update, neu | Der Stop ist das geordnete Beenden: Frist und Grenze aus der Konfiguration (`SPEC-047`, `SPEC-034`), `CANCEL_TIMEOUT` an den Listener, die Verbindung als `ShutdownGate` der Schleife |
| `src/main/kotlin/io/github/pt9912/tdswire/transport/listener/` (`ConnectionHandler`, `ConnectionRegistry`, `ListenerConfig`, `ListenerEvents`, `ManagedConnection`, `TdsListener`) | update | Aufnahme-Grenze `MAX_CONNECTIONS` im Verbindungsregister mit Abweisung `REFUSED_AT_LIMIT`; angemeldeter Abbruch nach Ablauf der Frist, Trennen nach `CANCEL_TIMEOUT`, Obergrenze des Beendens auch bei einem Handler, der den Abbruch ignoriert |
| `src/main/kotlin/io/github/pt9912/tdswire/config/` (`ServerConfig`, `ConfigValidation`) | update | Öffentlich neu nur zwei Felder mit Voreinstellung: `ListenSettings.maxConnections` und `RequestSettings.shutdownGraceSeconds` (kein neuer Typ, die Parameterzahl von `ServerConfig` bleibt), Schlüssel `MAX_CONNECTIONS` und `SHUTDOWN_GRACE` mit Bereichsprüfung |
| `src/testserver/kotlin/io/github/pt9912/tdswire/testserver/` (`ConfigLoader`, `EnvKeys`) | update | Eingang der beiden Werte als Umgebungsvariablen des Testservers (`SPEC-005`-Muster) |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/DisconnectTest.kt`, `ShutdownDispatchTest.kt` | neu | Disconnect in `RUNNING`, `STREAMING`, `CANCELLING`, Leerlauf; Beenden aus Sicht der Schleife (`TDS-FA-07`, `SPEC-044`, `SPEC-029`) |
| `src/test/kotlin/io/github/pt9912/tdswire/transport/listener/ListenerAbortTest.kt`, `ListenerLimitTest.kt` | neu | Frist, Abbruch, `CANCEL_TIMEOUT`, Obergrenze mit gesteuerter Uhr; `MAX_CONNECTIONS` bis zur Grenze und im Sturm (`TDS-FA-07`, `TDS-QA-02`, `SPEC-034`) |
| `src/test/kotlin/io/github/pt9912/tdswire/runtime/ConnectionLeakTest.kt`, `ShutdownWiringTest.kt`, `ServerSmokeTest.kt` | neu, update | Lecktest mit 250 Verbindungen und 50 RST je Zustand; Beenden über Socket in echter Zeit; Prozess-Stop, RST und beschädigtes PRELOGIN am Serverprozess (E2E-Stufe 1, `TDS-FA-07`, `TDS-QA-02`, `TDS-FA-01`) |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/session/` (`HandshakeMatrixTest`, `SessionSupport`), `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/DispatchSupport.kt`, `src/test/kotlin/io/github/pt9912/tdswire/testsupport/` (`ScriptedWire`, `WireSession`) | update | Matrix um die neue Ereignisart; die Test-Gegenstelle schließt erst nach der Antwort (ein Ende während der Anfrage ist jetzt ein Disconnect) |
| `docs/plan/kompatibilitaetsmatrix.md`, `docs/user/testserver-manuell.md` | update | Zeile TCP-Listener (Beenden, Grenze, Disconnect) und Hinweis zu `docker stop` |
| `spec/spezifikation.md` | update | `SPEC-044` und `SPEC-049`: Herkunft des Schließens nach ERROR und DONE (Nachzug (g)); `SPEC-029`, `SPEC-034`, `SPEC-047`, `SPEC-059`: Disconnect, Grenze und Frist als gebauter Stand; `SPEC-044`/`SPEC-029` zu Verbindungen mit unvollständiger Nachricht oder laufendem Handshake bleiben unberührt (Nutzerentscheidung, Risiko in §6) |
| `docs/user/einbettung-und-betrieb.md`, `tools/e2e.sh`, `docs/plan/e2e-abdeckung.md` | update | Nutzer-Doku zu `stop()`, Konfiguration und Grenzen; Deklaration der Stufe 1 um `TDS-FA-07` und `TDS-QA-02` (Läufe in `ServerSmokeTest`) und die erzeugte Abdeckungs-Sicht |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf, inklusive einer Testdatei-Zeile mit der Akzeptanzkriterien-ID in `Begründung`.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Der Start-Trigger der Welle `welle-fehler-abbruch` ist eingetreten, `slice-attention-cancel-timeout` liegt in `done/`, [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) steht im ADR-Index auf `Accepted` (erfüllt; damit liegt die Nutzerentscheidung zum Zeitpunkt des Schließens nach `TDS_SHUTDOWN` vor), die Slices `slice-transport-listener-lebenszyklus` und `slice-bootstrap-konfiguration` liegen in `done/` (Verbindungsende-Ereignis, Listener-Stop, Stop-Pfad stehen), und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2. Schnittvorschlag: Disconnect (DoD-Punkt 1) und geordnetes Beenden (DoD-Punkte 2 und 3) als zwei Slices; die Welle bekommt dann fünf.
- `in-progress` → `open` (blockiert — Carveout?): Der Transport meldet kein Verbindungsende als Ereignis.

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

- Der Slice ist der größte der Welle: Disconnect über alle Zustände (mit der Grenze `MAX_CONNECTIONS` im selben Liefer-Punkt), ein fünfteiliger Ablauf und der Bootstrap-Anschluss. Die Rückführung nennt den Schnittvorschlag; die Grenze `MAX_CONNECTIONS` ist der Teil, der sich am leichtesten ablösen lässt (eigener Slice mit dem Sturmtest aus `slice-qa-stabilitaet`). — **Ausgang:** entfallen: der Slice wurde in einem Stück geliefert, keine Rückführung; Review, Spec-Zielbild-Review und Verifikation haben ihn mehrfach nachgebessert, der Schnittvorschlag wurde nicht gebraucht
- Spec-Lücke: `SPEC-034` nennt das Ressourcenlimit als Fehlermodus der Anmeldung, beschreibt aber nicht, was der Client bei einer abgewiesenen Verbindung sieht (Schließen ohne Antwort oder ERROR vor dem Login). Entscheidungsbedarf (Nutzer); bis dahin ist „Schließen ohne Antwort“ im Test eine Festlegung ohne Messung, am Referenzclient nicht gemessen. — **Ausgang:** entfallen als Entscheidungsbedarf: Der Nutzer hat die Abweisung ohne Antwortbytes und die Wertebereiche von `MAX_CONNECTIONS` (1 bis 100000) und `SHUTDOWN_GRACE` (0 bis 3600) am 2026-11-06 als Festlegung bestätigt (vermerkt in `SPEC-034`, `SPEC-047`, Nutzer-Doku); was der Referenzclient sieht, bleibt ungemessen: → `BEO-TDS/messung-am-echten-sql-server-fehlt`
- `SPEC-044` Nr. 2 und Schlussabsatz regeln die unvollständige Nachricht (die Verbindung bleibt bis zum Ablauf der Frist offen); das war Code-Schuld und ist gebaut (Nutzerentscheid „Spec ist Zielbild“, 2026-11-06, Mutation M1 rot). Drei Reste, in der Spezifikation und im Register geführt, die Zusage von Nr. 3 ist nicht abgeschwächt: (a) **Offene Frage an die Spezifikation:** Nr. 2 und Nr. 3 treffen eine ruhende Verbindung gegensätzlich; gebaut ist Nr. 2 vor Nr. 3 gelesen (die Verbindung endet beim Beginn sofort, ein sequentieller Client sieht ein Schließen, nie `TDS_SHUTDOWN`); (b) der Handshake: die Spezifikation sagt nicht, wann eine Verbindung mitten im Handshake endet, gebaut ist Nr. 2 wörtlich (sofort); (c) nicht gebaut: die Anweisungsgrenzen eines Batches (`SPEC-044` Nr. 3, der Server kennt sie nicht). — **Ausgang:** weiter offen: (a) und (b) → `BEO-TDS/shutdown-ruhende-verbindung-endet-vor-tds-shutdown`, (c) → `BEO-TDS/batch-anweisungsgrenzen-dem-server-unbekannt`
- Das Schließen nach ERROR und DONE bei `TDS_SHUTDOWN` und nach `DONE[ATTN]` im Beenden ist Setzung des Nutzers vom 2026-11-01 ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Entscheidung 4 und 2c): am Referenzclient wird „ERROR, DONE, dann Schließen“ richtig gelesen (S21, S22), ein Herunterfahren am echten Server ist nicht gemessen. — **Ausgang:** weiter offen: → `BEO-TDS/messung-am-echten-sql-server-fehlt`
- Die Nummer und Meldung von `TDS_SHUTDOWN` kommen aus `slice-fehler-mapping`; ein Herunterfahren ist am echten Server nicht gemessen. — **Ausgang:** weiter offen: → `BEO-TDS/messung-am-echten-sql-server-fehlt`
- Obergrenze des Beendens bei einem Handler, der den Abbruch ignoriert (gemeldet von [slice-transport-listener-lebenszyklus](../done/welle-transport-tls/slice-transport-listener-lebenszyklus.md) §6, Review F-10): `TdsListener.shutdown()` kehrt erst zurück, wenn jeder Handler endet; `SPEC-044` Nr. 5 nennt Frist plus `CANCEL_TIMEOUT` als Obergrenze, der Listener setzt sie ohne Session nicht durch, und `terminate` gibt nach 5 s ohne Meldung auf. DoD-Punkt 2 (Rückkehr nach spätestens Frist plus Abbruchfrist) trägt den Punkt; ein Test mit einem Handler, der den Abbruch ignoriert, gehört dazu. — **Ausgang:** eingetreten und behoben: `awaitConnections` gibt jede Verbindung nach Frist plus `CANCEL_TIMEOUT` auf, ohne Zugabe (die 250 ms `HANDLER_EXIT_WAIT` sind entfernt, Spec-Zielbild-Review F-1); Mutation M2 (`abandon` → `end`) ist rot in `ListenerAbortTest`.
- Zeitverhalten: Tests mit gesteuerter Uhr belegen den Ablauf, nicht reale Fristen unter Last; `SHUTDOWN_GRACE` (10 s) und `CANCEL_TIMEOUT` (5 s) sind vorläufige Defaults. — **Ausgang:** weiter offen: → `BEO-TDS/beenden-unter-last-nicht-gemessen`
- Disconnect bei vom Server geschlossener Verbindung mit Pooling im Client ist nicht gemessen; ebenso TDS 8.0. — **Ausgang:** weiter offen: → `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`
- Ein Fake-Backend belegt die Freigabe von Strom und Sitzung, nicht die Freigabe von Ressourcen eines echten Treibers (Port-Vertrag: das Backend sorgt selbst, Annahme). — **Ausgang:** weiter offen: → `BEO-TDS/test-belegt-nur-den-fake`
- Der minimale Log-Anschluss (`ServerLog`) besteht nicht mehr: [slice-betrieb-log-schnittstelle](../done/slice-betrieb-log-schnittstelle.md) hat die Aufrufstellen auf die Senke (`domain.telemetry`, `Telemetry` in `runtime`) umgestellt, und die Ausgabe-Sperre verbietet `println` im Hauptmodul. Meldet dieser Slice etwas über das Beenden oder das Ende einer Verbindung, geht es über die Senke; neue Ereignisarten gehören zu [slice-betrieb-zustandsereignisse](../next/slice-betrieb-zustandsereignisse.md), und Änderungen an `Bootstrap.kt` und `TdsServer.kt` treffen die Verdrahtung der Senke (der Review maß mit `git merge-tree` keinen Konflikt, übersetzt wurde nicht). — **Ausgang:** entfallen: der Merge von `main` verlief ohne Konflikt und übersetzt (`make gates`, `clean-build`), der Slice meldet nichts über die Senke (der Diff berührt `domain.telemetry` nicht); Beginn und Ende des Beendens als Ereignis bleiben bei [slice-betrieb-zustandsereignisse](../next/slice-betrieb-zustandsereignisse.md)

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

**Gegenstand:** geliefert, soweit belegt; die Reste stehen unter „Nicht belegt“. Code: `4e79d285`, Review-Nachzug `bbebaf48`, `d3e07c00`, `ca6792c1`, Wortlaut der KDoc `c6987c8a`. Berichte: [Code-Review](../../../reviews/2026-11-06-4e79d285-slice-abbruch-ressourcen-shutdown.md) (0 HIGH, 2 MEDIUM F-1 und F-2, 4 LOW F-3 bis F-6, 4 INFO F-7 bis F-10; F-1 bis F-6 behoben), [Spec-Zielbild-Review](../../../reviews/2026-11-06-f6ce18c7-spec-zielbild-abweichungen.md) (für diesen Zweig F-1 und F-2 HIGH, F-3 MEDIUM, F-14 INFO; behoben), [Verifikation](../../../reviews/2026-11-06-47d3020e-verifikation-slice-abbruch-ressourcen-shutdown.md) (Code erfüllt `SPEC-044` Nr. 2, 3, 5 und DoD-Punkte 1 bis 3; V-1 MEDIUM, V-2 LOW behoben in `c6987c8a`, V-3 und V-4 INFO). Maßstab nach Nutzerentscheid vom 2026-11-06 „Spec ist Zielbild“: der Code folgt der Spezifikation, nicht umgekehrt.

**Belegt (Läufe der Verifikation am Stand `47d3020e`, 2026-11-06):** `make docs-check` 0 Befunde; `make gates FORCE=1` Exit 0; `make clean-build` (Frischbeleg ohne Cache) Exit 0, 972 Zeilen `PASSED`, 0 rote Tests; `make e2e` grün, 7 Stufen, 291 s, Stufe 1 trägt `TDS-FA-07` und `TDS-QA-02`; Mutationen M1 (Nachrichtenrest in `ManagedConnection.busy`, 4 rot), M2 (`abandon` → `end`, 1 rot), M3 (`TDS_SHUTDOWN` aus `RequestLoop.answer`, 6 rot) vom Verifier gefahren. 

**Gate-Beleg (Planner, nach dem Merge von `main` und dem Move nach `done/`, 2026-11-06):** `make docs-check` 916 Dateien, 0 Befunde; `make gates` Exit 0 (11 s, die Klassen `werkzeug`, `tests`, `stempel` bei passendem Beleg übersprungen); `make gates FORCE=1` Exit 0 (28 s, 72 Docker-Schichten `CACHED`, belegt Stempel, Host-Selbsttests und Doku, nicht frische Gradle-Arbeit); `make clean-build` (ohne Cache: Jar, Tests, detekt) Exit 0, 7 min 37 s, 972 Zeilen `PASSED`, 0 rote Tests. Seit dem Stand der Verifikation (`47d3020e`) ändert sich im Quelltext nur der KDoc von `ListenerAbortTest` (V-2). `make e2e` ist nicht neu gefahren (Verifikation: 7 Stufen grün, 291 s).

**Nicht belegt (Grenzen):** Fristen unter Last und reale Dauer (gesteuerte Uhr, `BEO-TDS/beenden-unter-last-nicht-gemessen`); `TDS_SHUTDOWN`, die Reihenfolge „ERROR, DONE, dann Schließen“, das Ende nach `DONE[ATTN]` im Beenden und die Abweisung ohne Antwortbytes am echten SQL Server und am Referenzclient (`BEO-TDS/messung-am-echten-sql-server-fehlt`); Disconnect mit Pooling und unter TDS 8.0; Freigabe von Ressourcen eines echten Treibers (nur Fake-Backend). Zwei Festlegungen im Code sind nicht vom Nutzer bestätigt: die Form `DONE[ATTN]` ohne ERROR im Beenden und das Ende der Gegenstelle als Disconnect auch bei halb geschlossener Verbindung.

**Benannte Spec-Lücke (Spannung, nichts abgeschwächt):** `SPEC-044` Nr. 2 und Nr. 3 treffen eine ruhende Verbindung gegensätzlich: gebaut ist Nr. 2 vor Nr. 3 gelesen, eine ruhende Verbindung endet beim Beginn sofort, `TDS_SHUTDOWN` erreicht nur eine Verbindung mit laufender Anfrage oder halber Nachricht; ebenso fehlt die Aussage zum Handshake. `SPEC-044` führt dies als „Offene Frage an die Spezifikation“ (Verifikation V-1), die Zusage von Nr. 3 bleibt. Träger im Register: [shutdown-ruhende-verbindung-endet-vor-tds-shutdown](../observations/BEO-TDS/shutdown-ruhende-verbindung-endet-vor-tds-shutdown/observation.md) (Träger-Vorschlag des Planners, zur Bestätigung durch den Nutzer: nach der Entscheidung ein Folge-Slice, noch ohne Kennung). Nicht gebaut bleibt „von den übrigen Anweisungen eines Batches läuft nach dem Beginn nichts mehr“ (`SPEC-044` Nr. 3): [batch-anweisungsgrenzen-dem-server-unbekannt](../observations/BEO-TDS/batch-anweisungsgrenzen-dem-server-unbekannt/observation.md) (Träger-Vorschlag: Backend-Vertrag um eine Anweisungsgrenze, zuerst als Entscheidung).

**Nutzerentscheidungen (2026-11-06, „deine Empfehlungen“):** Wertebereiche `MAX_CONNECTIONS` 1 bis 100000 und `SHUTDOWN_GRACE` 0 bis 3600 und die Abweisung ohne Antwortbytes sind als Festlegung bestätigt (vermerkt in `SPEC-034`, `SPEC-047`, Nutzer-Doku und im Änderungsprotokoll der Spezifikation); die Spannung zwischen Nr. 2 und Nr. 3 ist als offene Frage geführt, nicht entschieden; der Handshake-Rest bleibt offen.

**Steering-Loop-Eintrag:** geschärfte Beobachtungen und eine benannte Spec-Lücke, keine neue Regel; nichts verkörpert, daher kein `liegt in`. Die Klassen aus Review F-1 bis F-10, Spec-Zielbild-Review F-1 bis F-3 und Verifikation V-1 bis V-4: Aussage breiter als der Beleg (Review F-1, Spec-Zielbild F-1 und F-2: ein „Gebaut:“-Satz hob die Obergrenze um 250 ms und verengte `TDS_SHUTDOWN`); neu als eigene Klasse: der „Gebaut:“-Satz, der die Zusage verengt, und die Code-Schuld unter dem Namen einer Spec-Lücke (Spec-Zielbild F-1 bis F-3, [gebaut-satz-verengt-zusage-der-spezifikation](../observations/BEO-TDS/gebaut-satz-verengt-zusage-der-spezifikation/observation.md)); zweite Fundstelle bleibt stehen (Review F-5 Matrix, F-6 `testserver-manuell`, Verifikation V-2 KDoc des Obergrenzen-Tests); Zeile oder Zweig ohne unterscheidenden Test (Review F-3: Prozesstests für RST und beschädigtes PRELOGIN trugen `@spec` und blieben grün ohne die Disconnect-Erkennung; die Verifikation fand die Tests zu `SPEC-044` durch M1 bis M3 diskriminierend); Kommentar nennt Abwesendes (V-2); Lesart der Nutzerentscheidung unbestätigt (V-3, jetzt bestätigt); neu: eine Spannung zwischen zwei Zusagen ohne Führung (V-1, [shutdown-ruhende-verbindung-endet-vor-tds-shutdown](../observations/BEO-TDS/shutdown-ruhende-verbindung-endet-vor-tds-shutdown/observation.md)), Test-Gegenstelle, die das Ende per `yield`-Schleife hält (`holdEndWhile`, Review F-8, [test-gegenstelle-haelt-ende-bis-zustand-ohne-zeitgrenze](../observations/BEO-TDS/test-gegenstelle-haelt-ende-bis-zustand-ohne-zeitgrenze/observation.md)), Ursache einer Abweisung im zweiten Sperrfenster erhoben (Review F-4, [zustand-nach-abweisung-in-zweitem-sperrfenster-erhoben](../observations/BEO-TDS/zustand-nach-abweisung-in-zweitem-sperrfenster-erhoben/observation.md)). Der nächste Slice, der eine Spec-Zeile an den gebauten Stand nachzieht, prüft vor dem Schreiben eines „Gebaut:“-Satzes, ob er die Zusage daneben einschränkt (Hinweis, keine Regel).

**Schwellen-Übertritt (nur Vorschlag, nicht verkörpert):** keine Klasse hat mit diesem Slice die Schwelle neu überschritten. `BEO-TDS/kommentar-nennt-abwesenden-oder-unwirklichen-zustand` steht jetzt bei 6 (war 5, offen, der Lese-Schritt gehört zur Closure der Welle `welle-testserver-manuell`); `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` steht bei 16 (offen, Entscheidung beim Nutzer). Die verkörperten Klassen `aussagen-ueber-messung-hinaus`, `fortschreibung-laesst-zweite-fundstelle-stehen`, `zeile-oder-zweig-ohne-unterscheidenden-test` und `test-belegt-nur-den-fake` tragen je einen weiteren Beleg; eine neue Verkörperung folgt daraus nicht.

**Beobachtungs-Register:** Evidenz-Dateien `evidence/slice-abbruch-ressourcen-shutdown.md` in [aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md), [fortschreibung-laesst-zweite-fundstelle-stehen](../observations/BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen/observation.md), [zeile-oder-zweig-ohne-unterscheidenden-test](../observations/BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test/observation.md), [kommentar-nennt-abwesenden-oder-unwirklichen-zustand](../observations/BEO-TDS/kommentar-nennt-abwesenden-oder-unwirklichen-zustand/observation.md), [lesart-einer-nutzerentscheidung-unbestaetigt](../observations/BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt/observation.md), [messung-am-echten-sql-server-fehlt](../observations/BEO-TDS/messung-am-echten-sql-server-fehlt/observation.md), [tds-8-0-und-pooling-nicht-gemessen](../observations/BEO-TDS/tds-8-0-und-pooling-nicht-gemessen/observation.md) und [test-belegt-nur-den-fake](../observations/BEO-TDS/test-belegt-nur-den-fake/observation.md); neue Beobachtungen mit je 1×: [gebaut-satz-verengt-zusage-der-spezifikation](../observations/BEO-TDS/gebaut-satz-verengt-zusage-der-spezifikation/observation.md), [shutdown-ruhende-verbindung-endet-vor-tds-shutdown](../observations/BEO-TDS/shutdown-ruhende-verbindung-endet-vor-tds-shutdown/observation.md), [batch-anweisungsgrenzen-dem-server-unbekannt](../observations/BEO-TDS/batch-anweisungsgrenzen-dem-server-unbekannt/observation.md), [test-gegenstelle-haelt-ende-bis-zustand-ohne-zeitgrenze](../observations/BEO-TDS/test-gegenstelle-haelt-ende-bis-zustand-ohne-zeitgrenze/observation.md), [zustand-nach-abweisung-in-zweitem-sperrfenster-erhoben](../observations/BEO-TDS/zustand-nach-abweisung-in-zweitem-sperrfenster-erhoben/observation.md) und [beenden-unter-last-nicht-gemessen](../observations/BEO-TDS/beenden-unter-last-nicht-gemessen/observation.md). Die Zähler folgen aus den Dateien.

**Folge-Slices:** keiner angelegt. Der Start-Trigger von [slice-betrieb-zustandsereignisse](../next/slice-betrieb-zustandsereignisse.md) (dieser Slice in `done/`) ist erfüllt; sein Risiko zu `MAX_CONNECTIONS` führt jetzt den gebauten Stand. Die Welle [welle-fehler-abbruch](welle-fehler-abbruch.md) hat mit diesem Slice alle sechs Slices in `done/`; ihre Closure (`/close-welle`) ist fällig und prüft das Mehr des Wellen-Smoke aus ihrem §3.

**Hinweis zu Reviewer-Artefakten:** Zur Gate-Grünheit wurden in den Berichten `ADR-*`- und `TDS-*`-Kennungen in Links umgewandelt (Inhalt unverändert, Klasse `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten`).

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Die berührten Packages (`protocol`, `transport`, `runtime`) sind Pfad-Cluster (Achse 3), tragen aber keine eigene Strukturregel (Achse 1) und keine eigene Inventur-Linie (Achse 2): eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, acht Beobachtungen, Stand des gemergten `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 3×, Stand *geplant*. Treffer: `TDS_SHUTDOWN` und Verbindungsende am echten Server nicht gemessen (§6).
- `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` — 3×, Stand *geplant*. Treffer: Pooling bei vom Server geschlossener Verbindung (§6).
- `BEO-TDS/aussagen-ueber-messung-hinaus` — 7×, Stand *verkörpert*. Treffer: Lecktest und Zeittests belegen das Fake-Backend und die gesteuerte Uhr, nicht den Betrieb.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Treffer: Kennungen stehen als Link.
- Ohne Treffer: `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`, `BEO-TDS/adapter-festlegungen-ohne-slice`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/vergleichslauf-d-ungeklaert`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
