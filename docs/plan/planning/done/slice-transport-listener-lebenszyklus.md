# Slice slice-transport-listener-lebenszyklus: Listener und Verbindungslebenszyklus: Annahme, Startprobe und geordnetes Schließen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-transport-tls

**Bezug:** [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0002](../../adr/0002-tls-und-verschluesselung.md), [ADR-0003](../../adr/0003-transportframework.md)

**Berührte Spec-Stellen:** SPEC-005 · SPEC-008 · SPEC-010 · SPEC-017 · SPEC-043 · SPEC-044 · SPEC-047 · ARC-002 · ARC-003

**Verantwortlich:** pt9912

**Autor:** pt9912. **Datum:** 2026-10-30.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Ein TCP-Listener nimmt Verbindungen an, gibt jeder einen eigenen Scope und eine Übergabe-Schnittstelle an die spätere Protokollschicht, prüft vor dem Binden die TLS-Konfiguration mit einer Startprobe und beendet sich geordnet; ein Loopback-Test durchläuft Listener, beide TLS-Pfade und Nachrichtenaustausch (Smoke der Welle).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Verbindungs-Zustandsautomat, PRELOGIN-/LOGIN7-Ablauf, Auswahl des Pfades je Client und die Antwort `TDS_SHUTDOWN` — Zustandsautomat und Fehlersemantik sind [ADR-0004](../../adr/0004-session-zustandsautomat.md) und [ADR-0007](../../adr/0007-fehler-und-attention.md) (beide `Proposed`); der Listener übergibt eine Verbindung an einen Handler und kennt keine Protokollzustände. Eine „laufende Anfrage“ kennt der Transport nur als Rückmeldung des Handlers.
- Abbruch laufender Anfragen (Cancel an das Backend, `CANCEL_TIMEOUT`, SPEC-029, SPEC-033) — braucht Session und Backend-Port; der Listener bietet nur die Frist `SHUTDOWN_GRACE` und das Trennen am Ende.
- Validierung der Gesamtkonfiguration und Parsen von `ENCRYPTION_POLICY` (ARC-002, SPEC-010): der Listener bekommt eine validierte Bind-Adresse, einen Port und TLS-Material; wer sie aus Umgebung oder Datei liest, ist eine Lieferung des Packages `config`.
- Logging und Spans (SPEC-017, `tds.connection`) und Metriken — der Listener meldet Ereignisse an eine Schnittstelle; die Anbindung an Telemetrie ist Sache von `observability` (Träger: Welle [welle-betrieb-diagnose](../welle-betrieb-diagnose.md)).
- Start des Serverprozesses, Composition Root, JVM-Flags des Prozesses (`runtime`) und ein Make-Ziel zum Starten.
- MARS, Pooling und `sp_reset_connection` — ungemessen laut Clientprofil.
- Läufe gegen den Referenzclient und einen echten SQL Server — `slice-qa-kompatibilitaet`, `slice-qa-stabilitaet`; dieser Slice belegt Listener-Verhalten am Loopback.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Listener** (SPEC-005, [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)): Bind auf Adresse und Port, Annahmeschleife, je Verbindung ein eigener Coroutine-Scope und verbindungslokaler Zustand; der Absturz oder das Trennen einer Verbindung beeinträchtigt keine andere. Ein belegter Port beendet den Start mit einer Diagnose, die Adresse und Port nennt; nach jedem fehlgeschlagenen Start laufen keine Threads und kein Listener des Servers weiter. *Stand:* von der Nachverifikation bestätigt bis auf den Weg „Aufbau scheitert nach dem Binden“ (V-3); dafür steht seit der zweiten Fix-Runde (`9d3d31d`) ein Test, den kein Verifier nachgeprüft hat.
- [x] **Verbindungslebenszyklus** (SPEC-008, SPEC-044, SPEC-047, [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe)): `LOGIN_TIMEOUT` ab Verbindungsaufbau, bis der Handler „Login abgeschlossen“ meldet, danach schließt der Transport ohne Antwort; Client-Disconnect räumt Scope und Kanal auf; geordnetes Schließen: der Listener schließt zuerst, eine im selben Moment angenommene Verbindung endet sofort, eine Verbindung ohne laufende Anfrage und ohne halb empfangene Nachricht endet sofort, die übrigen höchstens bis `SHUTDOWN_GRACE`, danach wird getrennt; eine zweite Shutdown-Anforderung ist wirkungslos; eine `CancellationException`, die der Handler wirft, während der Scope der Verbindung nicht abgebrochen ist, endet als `HANDLER_FAILED` (Setzung, nicht gemessen), das Trennen und Beenden durch den Listener bleibt `SHUTDOWN` bzw. `LOGIN_TIMEOUT`. *Stand:* von der Nachverifikation bestätigt bis auf zwei Reste: das Warten im abgebrochenen `shutdown()` (V-2) und die Einordnung der Abbruchausnahme; beide hat die zweite Fix-Runde (`9d3d31d`) mit Tests belegt, die kein Verifier nachgeprüft hat. Die Einordnung `HANDLER_FAILED` ist eine Setzung, an Test-Handlern gemessen.
- [x] **TLS-Startprobe** (SPEC-043, SPEC-023, [ADR-0002](../../adr/0002-tls-und-verschluesselung.md), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)): Zertifikatskette und Schlüssel werden geladen und mit der Schleife aus `slice-transport-tls-schleife` im Speicher gegen eine Gegenstelle geprüft, die nur die Gültigkeit der Kette prüft, vor dem Binden des Ports und mit den konfigurierten Versionen und Suiten; Diagnose `CERTIFICATE`, `PRIVATE_KEY` oder `PAIR` ohne Dateipfad und ohne Inhalt in Meldung und Log.

Nachweis-Pflichten für alle drei Punkte:

- [x] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>` (Konvention nach `slice-qa-spec-abdeckung`); die Tests laufen in `make test` und sind grün.
- [x] Smoke der Welle (Loopback, Docker): der Listener nimmt eine Verbindung an, im 7.4-Pfad läuft der Handshake in `0x12`-Paketen, im 8.0-Pfad ab dem ersten Byte; je eine Nachricht (byteweise zerstückelt) geht hin und zurück durch Listener, Schleife, Nachrichtenleser und -schreiber. Der Handler im Test ist ein Echo, kein Protokoll.
- [x] Mehrere gleichzeitige Verbindungen (Zahl im Test genannt), davon einige mit defektem Handshake, abgeschnittener Nachricht und abruptem Trennen: der Prozess bleibt am Leben, die guten Verbindungen laufen weiter ([TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)).
- [x] Startprobe: je ein Test für abgelaufenes Zertifikat, unlesbare Kette, verschlüsselten oder nicht zugelassenen Schlüssel und nicht passendes Paar; jeder meldet den richtigen Teil, und der Port ist danach nicht gebunden. Testdaten nach der Entscheidung zum Testzertifikat (§6).
- [x] Die Zuordnung *Test | Festlegung* je Aussage steht in der Closure-Notiz.
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8). *Stand:*
      [Review](../../../reviews/2026-10-30-d9bab24-slice-transport-listener-lebenszyklus.md) und
      [Nachverifikation](../../../reviews/2026-10-30-a8072b5-nachverifikation-slice-transport-listener-lebenszyklus.md)
      liegen vor; die zweite Fix-Runde (`9d3d31d`) hat weder Review noch Verifier gesehen.
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist (`harness/README.md` §Sensors nur bei einem neuen Target; `contracts/security-profile.md`, falls die Form von Zertifikat und Schlüssel dort festgelegt wird).
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
| `src/main/kotlin/io/github/pt9912/tdswire/transport/` (Unterpackages, z. B. `listener`, `tls`) <!-- d-check:ignore (geplant: neue Datei) --> | neu | Listener, Verbindungs-Scope, Handler-Schnittstelle, Fristen, Startprobe und TLS-Material-Lader; keine Imports aus `port`, `simulation`, `runtime` (SPEC-041) |
| `src/test/kotlin/io/github/pt9912/tdswire/transport/` <!-- d-check:ignore (geplant: neue Datei) --> | neu | Loopback-Smoke, Parallelität mit defekten Verbindungen, Fristen mit steuerbarer Zeit, Startprobe je Teil; `@spec`-Tag je Test (SPEC-008, SPEC-043, SPEC-044) |
| Testzertifikate | neu | Herkunft offen (Frage an den Nutzer, §6) |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf:

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/transport/listener/` | neu | `TdsListener` (Bind, Annahmeschleife, Beenden), `ManagedConnection` (Scope, `LOGIN_TIMEOUT`, Ende), `ConnectionRegistry` (Aufnahme gegen Beginn des Beendens gesperrt), `ConnectionHandler`/`Connection` (Handler-Schnittstelle), `ListenerConfig`/`ListenerTimes`/`Delayer` (Fristen über eine steuerbare Zeitquelle), `ListenerEvents` |
| `src/main/kotlin/io/github/pt9912/tdswire/transport/tls/` (`TlsMaterial`, `TlsStartProbe`, `ProbeClient`) | neu | PKCS#12-Lader mit Diagnose, Startprobe im Speicher gegen eine Gegenstelle, die nur die Gültigkeit der Kette prüft |
| `src/test/kotlin/io/github/pt9912/tdswire/transport/listener/` und `src/test/kotlin/io/github/pt9912/tdswire/transport/tls/TlsStartProbeTest.kt` | neu | Smoke, parallele Verbindungen, Fristen, Beenden, Start, Startprobe je Teil |
| `src/test/kotlin/io/github/pt9912/tdswire/testsupport/KeyVariants.kt` | neu | Abwandlungen der Testzertifikate der Stage `testkeys` im Test (anderes Schlüsselkennwort, fremder Schlüssel, EC, RSA 1024): kein Eingriff in `Dockerfile` und `build.gradle.kts` |

Festlegungen des Laufs (nicht gemessen, für die Closure-Notiz): Handler-Schnittstelle mit zwei Meldungen (`loginCompleted`, `requestRunning`; eine unvollständig empfangene Nachricht meldet der Handler als „läuft“); eigene `AsynchronousChannelGroup` mit zwei Threads je Listener; Zeitquelle `Delayer` statt `kotlinx-coroutines-test`; die Startprobe nutzt den Pfad ab dem ersten Byte (die Kapselung prüft sie nicht, `SPEC-043`); RSA ab 2048 Bit als zugelassene Schlüsselform; `jdk.tls.acknowledgeCloseNotify` bleibt beim JDK-Standard, der Server antwortet nicht auf `close_notify` der Gegenstelle (Voreinstellung des Aufrufers, vom Nutzer nicht bestätigt); der Listener schließt beim Beenden und bei Fristablauf den TCP-Kanal ohne eigenes `close_notify`, weil er den TLS-Kanal des Handlers nicht kennt.

- Reihenfolge: Listener und Scope zuerst, dann Fristen und Schließen, zuletzt die Startprobe (sie braucht die Schleife und das Material).
- Die Handler-Schnittstelle nach ARC-003 („Protocol-Schnittstellen“) wird so klein wie möglich gehalten: Verbindung entgegennehmen, „Login abgeschlossen“ und „Anfrage läuft“ melden. Ihre endgültige Form legt die Protokollschicht fest; der Slice nennt sie in §7 als Festlegung.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Der Start-Trigger der Welle `welle-transport-tls` ist eingetreten, `slice-transport-tls-schleife` liegt in `done/` (erfüllt: die Schleife in beiden Pfaden steht auf `main`), und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2 oder die Startprobe samt Material-Lader sprengt den Rahmen. Schnittvorschlag: Slice C1 = Listener und Lebenszyklus, Slice C2 = Startprobe und TLS-Material; die Welle bekommt dann vier Slices.
- `in-progress` → `open` (blockiert — Carveout?): die Handler-Schnittstelle lässt sich nicht ohne eine Entscheidung zum Zustandsautomaten ([ADR-0004](../../adr/0004-session-zustandsautomat.md)) schneiden, oder das Format von Zertifikat und Schlüssel ist ungeklärt.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün (einschließlich `make test`), der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung *Test | Festlegung* je Aussage sowie den Steering-Loop-Lerneintrag (geschärfte Regel, neuer Sensor oder benannte Spec-Lücke; eine Closure ohne Lerneintrag ist keine).

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Größe: drei Liefer-Punkte mit Listener, Fristen und Startprobe; die Startprobe hängt an der Schleife und am Material-Format und kann den Slice sprengen. Rückführung und Schnitt in §4. — **Ausgang:** entfallen: die drei Liefer-Punkte wurden in einem Slice geliefert, die Rückführung nach §4 war nicht nötig.
- Format von Zertifikat und Schlüssel (PKCS#12, PEM): SPEC-043 nennt die Teile, aber nicht die Formen; ein PEM-Lader ohne Fremdbibliothek ist Eigencode mit Sicherheitsrelevanz. Entschieden (Nutzer, 2026-10-30): PKCS#12 (JDK-Standard), kein PEM-Lader. — **Ausgang:** entfallen: es gibt keinen PEM-Lader; die Form steht als Setzung in `contracts/security-profile.md` §Schlüsselmaterial. Zwei Setzungen (RSA ab 2048 Bit, dort genannt; Diagnose `CERTIFICATE` bei beschädigtem Speicher oder falschem Kennwort, nur im KDoc von `TlsMaterial`) sind ungemessen und vom Nutzer nicht bestätigt; sie stehen unter dem letzten Risiko dieser Liste und im Register.
- Die Startprobe prüft laut SPEC-043 weder Vertrauen noch die TDS-Kapselung; ein Test darf daraus nicht „der Client akzeptiert das Zertifikat“ ableiten. — **Ausgang:** weiter offen: kein Test leitet es ab (Test `probe uses the configured versions and suites and checks neither trust nor name`), ob der Referenzclient das Zertifikat annimmt, ist ungemessen → `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, Träger `slice-qa-kompatibilitaet` (§6).
- Die Frist `LOGIN_TIMEOUT` ist laut Spezifikation vorläufig (15 s); Tests dürfen sie nur über eine steuerbare Zeitquelle prüfen, nicht über Wartezeit, sonst wird das Test-Gate langsam und flackert. — **Ausgang:** entfallen: die Fristtests laufen über `ManualDelayer` ohne Wartezeit. Der Wert von `LOGIN_TIMEOUT` bleibt vorläufig und gehört der Konfiguration (`slice-bootstrap-konfiguration`, §6). Das Flackern eines Tests aus anderem Grund steht im letzten Risiko.
- Ein geordnetes Schließen mit laufender Anfrage ist ohne Session nicht vollständig belegbar; der Slice belegt Fristen und Trennen mit einem Test-Handler, nicht das Verhalten gegen eine echte Anfrage. Die Lücke steht in der Notiz und im Folge-Slice der funktionalen Welle. — **Ausgang:** weiter offen: Fristen und Trennen sind mit Test-Handlern belegt, nicht gegen eine echte Anfrage; ein Handler, der den Abbruch ignoriert, hält `shutdown()` ohne Obergrenze auf (Review F-10) → `BEO-TDS/test-belegt-nur-den-fake`, Träger `slice-abbruch-ressourcen-shutdown` (§6 und DoD-Punkt 2).
- Gleichzeitige Verbindungen: der Spike maß 200 (einzelner Lauf); der Test hier nennt eine kleinere Zahl und behauptet keine Last (`slice-qa-performance`). — **Ausgang:** weiter offen: der Test nennt 24 Verbindungen und ist keine Lastmessung → `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`, Träger `slice-qa-performance`.
- Zeitbegrenzung des TLS-Handshakes: die Schleife aus `slice-transport-tls-schleife` setzt keine Frist; eine Gegenstelle kann den Handshake mit einzelnen Bytes beliebig lange offenhalten. `LOGIN_TIMEOUT` läuft ab Verbindungsaufbau und umfasst deshalb den Handshake in beiden Pfaden; ein Test mit steuerbarer Zeit belegt, dass eine Verbindung im Handshake nach Ablauf der Frist getrennt wird. — **Ausgang:** entfallen: der Test `login timeout closes a connection stuck in the handshake in both paths` belegt die Trennung in beiden Pfaden mit steuerbarer Zeit.
- Antwort auf `close_notify` der Gegenstelle: die JDK-Engine antwortet ohne `jdk.tls.acknowledgeCloseNotify` nicht von selbst, die Schleife auch nicht (Festlegung der TLS-Schleife, `TlsReader`). Ob der Server beim geordneten Schließen (`SPEC-044`) auf ein `close_notify` des Clients mit eigenem `close_notify` antwortet oder das Flag setzt, ist eine Nutzerentscheidung; der Slice legt sie vor und setzt sie um, oder führt die Antwort als Folge-Slice. — **Ausgang:** weiter offen: umgesetzt ist die Voreinstellung des Aufrufers (der Server antwortet nicht, `jdk.tls.acknowledgeCloseNotify` beim JDK-Standard, der Listener schließt den TCP-Kanal ohne eigenes `close_notify`); ungemessen und vom Nutzer nicht bestätigt → `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`, Träger der Messung am Referenzclient `slice-qa-kompatibilitaet` (§6).
- Schichtgrenzen: `transport` darf `protocol`-Schnittstellen nutzen (ARC-003), `make detekt` belegt das nur über `import`-Zeilen (SPEC-041); der Plan hält die Handler-Schnittstelle in `transport` und importiert nichts aus `protocol`. — **Ausgang:** entfallen: `listener` und `tls` importieren nur aus `transport` (`channel`, `framing`, `tls`); `make detekt` ist grün.
- Einordnung einer Abbruchausnahme aus dem Handler (gemeldet von [slice-handshake-login](../done/slice-handshake-login.md), Nachverifikation N-3): bisher ordnete `ManagedConnection.finish` jede `CancellationException`, die den Handler verlässt, als `ConnectionEnd.SHUTDOWN` ein, auch wenn nichts den Scope abgebrochen hatte. Entschieden (Setzung des Implementers im Auftrag des Nutzers, nicht gemessen): ist der Scope der Verbindung abgebrochen (durch `end` oder das Beenden des Listeners), bleibt es bei der Ursache des Abbruchs (`SHUTDOWN`, `LOGIN_TIMEOUT`, `SHUTDOWN_GRACE_EXPIRED`); wirft der Handler sie bei unabgebrochenem Scope, ist es ein Handlerfehler (`HANDLER_FAILED`, Verbindung schließt, Nachbarn unberührt). Belegt durch zwei Tests (Handlerfehler: `a cancellation thrown by the handler on its own is a handler failure`; Abbruch durch den Listener: `a cancelled shutdown still releases the connections and the threads`), je mit einer Mutation rot gesehen. Grenze: ein Handler, der nach einem Abbruch durch den Listener eine eigene Ausnahme wirft, hat die Ursache des Abbruchs; ein Abbruch des Scopes im selben Moment, in dem der Handler von selbst abbricht, ist nicht unterscheidbar. — **Ausgang:** weiter offen: die Einordnung ist gesetzt und an Test-Handlern gemessen, gegen den Handshake-Handler ist sie ungemessen und vom Nutzer nicht bestätigt; die zweite Fix-Runde hat kein Verifier nachgeprüft → `BEO-TDS/test-belegt-nur-den-fake` und `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`, Träger `slice-bootstrap-konfiguration` (§6). Die Kopplung an den Nachbar-Slice steht schon in `BEO-TDS/folge-einer-festlegung-im-nachbar-slice-unbenannt`.
- Ein Wanduhr-Test dieses Slice wurde einmal unter Last rot (gemeldet von [slice-handshake-login](../done/slice-handshake-login.md), Nachverifikation N-4): `login timeout closes a connection stuck in the handshake in both paths` brach mit `NoSuchElementException: Collection is empty` bei `handler.wires.single()` ab, weil der Test auf die Verbindung des Handlers zugriff, bevor der Handler sie registriert hatte. Der Test wartet jetzt vor dem Freigeben der Frist auf die Registrierung (`handler.wires.size == 1`). Der Fehler ist weder vor noch nach der Änderung wieder aufgetreten: die Nachverifikation lief 200 Wiederholungen (400 Läufe) unter 60 Endlosschleifen auf 20 Kernen, mit und ohne die Zeile grün; die Änderung ist damit durch Analyse der Reihenfolge von Frist und Handler begründet, nicht durch einen rot gesehenen Lauf. — **Ausgang:** weiter offen: unter 200 Wiederholungen und 60 Schleifen nicht reproduziert, Wirkung der Änderung nicht belegt → `BEO-TDS/flake-fix-ohne-reproduzierten-fehler`. Ein Träger, der es schließen könnte, existiert nicht; der Eintrag bleibt im Register, bis der Test erneut flackert oder jemand den Fehler reproduziert.

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

- **Was hat funktioniert:** Der Schnitt trug: Listener, Lebenszyklus und Startprobe lagen in einem Slice, die Rückführung nach §4 war nicht nötig. Die Handler-Schnittstelle mit zwei Meldungen (`loginCompleted`, `requestRunning`) reichte für Fristen, geordnetes Schließen und die Anbindung des Handshake-Handlers aus `slice-handshake-login`; der Smoke der Welle läuft in beiden Pfaden durch Listener, Schleife, Nachrichtenleser und -schreiber. Die steuerbare Zeitquelle (`ManualDelayer`) machte alle Fristtests wartezeitfrei. Die Tests des Packages `transport.listener` und `transport.tls` laufen in `make test` grün (413 bestandene Tests im Gesamtbestand beim Lauf von `make spec-abdeckung` dieser Closure, kein Fehlschlag).
- **Was ging anders als geplant:** (a) **Zwei Fix-Runden statt einer.** Der Review fand fünf MEDIUM (F-1 bis F-5), die Nachverifikation drei weitere MEDIUM (V-1 bis V-3), alle aus der ersten Fix-Runde (`7a3032f`): Wartebedingungen und Zweige, deren Entfernen keinen Test färbte, und KDoc-Zusagen breiter als ihr Test (Register: `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test`, `BEO-TDS/aussagen-ueber-messung-hinaus`). (b) **Gemessene Ressourcenlecks** (F-2, F-3, F-6): abgebrochenes `shutdown()`, Port außerhalb 0..65535 und Threads nach Fehlstart; behoben und mit Tests belegt; die Mutationen M1 (F-2), M2 (F-3) und M5 (F-6) der Nachverifikation färben sie rot. (c) **Die Abbruchausnahme** (gemeldet von `slice-handshake-login`, Nachverifikation N-3) war als Folge einer Festlegung im Nachbar-Slice im Plan nicht genannt (Register: `BEO-TDS/folge-einer-festlegung-im-nachbar-slice-unbenannt`); die zweite Fix-Runde setzt sie: `HANDLER_FAILED` bei unabgebrochenem Scope, sonst die Ursache des Abbruchs. (d) **Das Flackern von F-1** ist nicht reproduziert: der Test wartet jetzt auf die Registrierung des Handlers, die Wirkung ist nicht gemessen (Register: `BEO-TDS/flake-fix-ohne-reproduzierten-fehler`).
- **Reports:** [Review](../../../reviews/2026-10-30-d9bab24-slice-transport-listener-lebenszyklus.md) (0 HIGH, 5 MEDIUM, 3 LOW, 4 INFO) und [Nachverifikation](../../../reviews/2026-10-30-a8072b5-nachverifikation-slice-transport-listener-lebenszyklus.md) (0 HIGH; V-1 bis V-3 MEDIUM, V-4 und V-5 LOW, V-6 und V-7 INFO) gegen den Stand `a8072b5`. Eingearbeitet in der ersten Fix-Runde `7a3032f`: F-1 bis F-8 und F-12; in der zweiten (`9d3d31d`): V-1 bis V-6 und die Abbruchausnahme. F-9 bis F-11 sind INFO ohne Handlung (F-10 siehe §6). V-7 (Stand der eingecheckten Matrix) erledigt diese Closure durch einen neuen Lauf von `make spec-abdeckung`. Ein Validator-Lauf entfällt: der Slice liefert keinen End-Nutzer-Wert, außer den Tests ruft bisher niemand den Listener auf.
- **Nachprüfung der zweiten Fix-Runde, ehrlich:** **Kein Verifier hat die zweite Fix-Runde (`9d3d31d`) nachgeprüft, und kein Review hat sie gesehen.** Sie umfasst V-1 bis V-6, die neuen Tests `IoGroupTest` (2), den Test des Zweigs `launched`, den Test der Abbruchausnahme und `ListenerTestBaseTest` sowie die Änderung an `ManagedConnection.finish`. Die Mutationen V1 bis V4 (zu V-1 bis V-4) und CEa und CEb (zur Abbruchausnahme) hat der Implementer einzeln rot gesehen; das ist seine Angabe, kein Beleg eines unabhängigen Laufs, und die Mutationsliste liegt nicht im Repo. Überlebt haben in der Nachverifikation vor dieser Runde M5b, M5c, M8, M6 und M10; ihre erneute Prüfung durch einen Verifier steht aus.
- **Gate:** `make gates` Exit 0 vor jedem Inhalts-Commit dieser Closure und nach dem Move.

**Zuordnung Test | Festlegung je Aussage.** Fünf Stärken: *gemessen* heißt am **Loopback gegen eine JDK-`SSLEngine` als Gegenstelle** im selben Prozess beobachtet (nicht am Referenzclient, nicht an einem SQL Server); *Handvektor* heißt ein von Hand gebautes Byte-Muster; *Selbstkonsistenz* heißt Code gegen Code ohne unabhängige Gegenstelle; *Festlegung* heißt Setzung ohne Messung; *nicht gemessen* nennt, was keine Aussage trägt.

| Stärke | Aussage | Beleg im Repo |
|---|---|---|
| gemessen (Loopback, JDK-Gegenstelle) | Eine Nachricht läuft in beiden Pfaden (7.4 mit `0x12`, 8.0 ab dem ersten Byte), byteweise zerstückelt, durch Listener, Schleife, Nachrichtenleser und -schreiber hin und zurück | `ListenerSmokeTest` (`both paths carry a message through listener, loop and message codec`); Handler im Test ist ein Echo, kein Protokoll |
| gemessen (Loopback, JDK-Gegenstelle) | 24 gleichzeitige Verbindungen (zwei Listener, je 12: 6 gute, 2 mit Müll statt Handshake, 2 abgeschnitten, 2 sofort getrennt): die guten laufen weiter, alle Verbindungen sind danach entfernt; ein abstürzender Handler beendet nur seine Verbindung | `ListenerSmokeTest` (`defective connections do not disturb good ones`), `ListenerLifecycleTest` (`a crashing handler ends only its own connection`); keine Lastmessung |
| gemessen (Loopback) | Ein belegter oder ungültiger Port beendet den Start mit Adresse und Port in der Diagnose; nach jedem Fehlstart läuft kein Thread weiter, auch bei 300 Wiederholungen mit sofortiger Prüfung; der Weg „Aufbau scheitert nach dem Binden“ gibt Port und Threads frei (Test seit `9d3d31d`, vom Verifier nicht nachgeprüft) | `ListenerStartTest` (`occupied port …`, `invalid port …`, `a failed start returns only after its threads have ended`, `failed probe leaves the port unbound …`, `a failing build after the bind …`); M2, M5 der Nachverifikation rot; `IoGroupTest` (V-1, V-2, vom Verifier nicht nachgeprüft) |
| gemessen (Loopback, Zeit gesteuert) | `LOGIN_TIMEOUT` ab Verbindungsaufbau, auch im Handshake in beiden Pfaden; nach „Login abgeschlossen“ bleibt die Verbindung; Client-Disconnect räumt Scope und Kanal; der Listener nimmt nach einem abrupten Gegenüber weiter an | `ListenerLifecycleTest` (vier Tests); die Frist läuft über `ManualDelayer`, nicht über Wartezeit; zu F-1 siehe Zeile *nicht gemessen* |
| gemessen (Loopback, Zeit gesteuert) | Geordnetes Schließen: Listener schließt zuerst, Verbindung ohne Anfrage endet sofort, die übrigen höchstens bis `SHUTDOWN_GRACE`, danach getrennt, Frist 0 trennt sofort, zweite Anforderung wirkungslos, eine vom Register abgewiesene Verbindung wird geschlossen, ohne Handler zu starten, ein abgebrochenes `shutdown()` gibt Verbindungen und Threads frei | `ListenerShutdownTest` (zehn Tests); M1, M3 der Nachverifikation rot |
| gemessen (Loopback) | Die Startprobe meldet abgelaufene Kette (jedes Zertifikat der Kette, auch ein abgelaufenes Zwischenzertifikat), unlesbaren Speicher, fehlenden, verschlüsselten oder nicht zugelassenen Schlüssel und nicht passendes Paar mit dem richtigen Teil; Diagnosen nennen weder Pfad noch Inhalt; der Port bleibt ungebunden | `TlsStartProbeTest` (sieben Tests); M4 der Nachverifikation rot; Testzertifikate aus der Stage `testkeys`, kein Schlüssel im Repo |
| gemessen, Gültigkeit der Aussage eingeschränkt | Die Einordnung `HANDLER_FAILED` bei einer `CancellationException` des Handlers mit unabgebrochenem Scope, Ursache des Abbruchs sonst | `ListenerLifecycleTest` (`a cancellation thrown by the handler on its own is a handler failure`), `ListenerShutdownTest` (`a cancelled shutdown …`); gemessen an Test-Handlern, die Setzung selbst bleibt unter *Festlegung* |
| Selbstkonsistenz | Das Register verweigert die Aufnahme nach Beginn des Beendens; das Aufräumen der Testbasis beendet jeden gestarteten Listener | `ListenerShutdownTest` (`registry refuses admission …`), `ListenerTestBaseTest` (seit `9d3d31d`, vom Verifier nicht nachgeprüft) |
| Festlegung | Handler-Schnittstelle mit zwei Meldungen; eine unvollständig empfangene Nachricht meldet der Handler als „läuft“; eigene Kanalgruppe mit zwei Threads je Listener; `Delayer` statt `kotlinx-coroutines-test`; die Startprobe nutzt den Pfad ab dem ersten Byte | KDoc, §3 dieses Slice; die Tests belegen das Verhalten, die Wahl folgt keiner Zeile der Spezifikation |
| Festlegung | PKCS#12 als einzige Form, erster Schlüsseleintrag, Speicher- und Schlüsselkennwort gleich, RSA ab 2048 Bit; ein beschädigter oder mit falschem Kennwort geöffneter Speicher ist `CERTIFICATE` | `contracts/security-profile.md` §Schlüsselmaterial (die ersten drei), KDoc von `TlsMaterial` (die Diagnose); vom Nutzer nicht bestätigt (die Wahl PKCS#12 selbst hat der Nutzer entschieden); `SPEC-043` verweist nicht darauf (siehe Steering-Loop-Eintrag) |
| Festlegung | Der Server antwortet nicht auf `close_notify` der Gegenstelle (`jdk.tls.acknowledgeCloseNotify` beim JDK-Standard); der Listener schließt beim Beenden und bei Fristablauf den TCP-Kanal ohne eigenes `close_notify` | KDoc, Sicherheitsprofil der TLS-Schleife; Voreinstellung des Aufrufers, vom Nutzer nicht bestätigt, am Referenzclient nicht gemessen |
| Festlegung | `HANDLER_FAILED` für eine `CancellationException` des Handlers bei unabgebrochenem Scope; ein Abbruch des Scopes im selben Moment, in dem der Handler von selbst abbricht, ist nicht unterscheidbar | §6 dieses Slice, KDoc von `ManagedConnection`; vom Nutzer nicht bestätigt, gegen den Handshake-Handler nicht gemessen |
| nicht gemessen | Der Referenzclient (`Microsoft.Data.SqlClient`) gegen Listener und Startprobe in beiden Pfaden; ob er das Zertifikat der geprüften Form annimmt (die Probe prüft weder Vertrauen noch Namen) | Träger `slice-qa-kompatibilitaet`; Register `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` |
| nicht gemessen | Geordnetes Schließen mit einer echten Session und einer laufenden Anfrage; ein Handler, der den Abbruch ignoriert (`shutdown()` ohne Obergrenze, F-10; `terminate` gibt nach 5 s ohne Meldung auf) | Träger `slice-abbruch-ressourcen-shutdown`; Register `BEO-TDS/test-belegt-nur-den-fake` |
| nicht gemessen | Verhalten unter Last mit vielen Verbindungen (der Test nennt 24) | Träger `slice-qa-performance`; Register `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` |
| nicht gemessen | Die Wirkung der Änderung zu F-1 (Warten auf die Registrierung des Handlers); der Fehler trat unter 200 Wiederholungen bei 60 Endlosschleifen weder mit noch ohne die Zeile auf | Register `BEO-TDS/flake-fix-ohne-reproduzierten-fehler` |
| nicht gemessen | Die Verdrahtung im Serverprozess: Start-Reihenfolge, Setzen von `-Djdk.tls.server.newSessionTicketCount=0`, Anbindung des Handshake-Handlers | Träger `slice-bootstrap-konfiguration` (DoD *Composition Root, `main`, Start und Stop* und §6) |
| nicht gemessen | Die zweite Fix-Runde `9d3d31d` durch einen unabhängigen Verifier | siehe *Nachprüfung der zweiten Fix-Runde* oben |

**Offene Punkte der Closure mit Träger** (gegen den Inhalt der Dateien geprüft; jeder Träger existiert als Datei im Planning-Lifecycle und führt den Punkt in seiner DoD oder seinem Risiko):

| Offener Punkt | Träger | Stand |
|---|---|---|
| Abbruchausnahme `HANDLER_FAILED` gegen den Handshake-Handler | `slice-bootstrap-konfiguration` (`open/`) | §6, ergänzt in dieser Closure (vorher ohne Träger) |
| Obergrenze von `shutdown()` bei einem Handler, der den Abbruch ignoriert (F-10) | `slice-abbruch-ressourcen-shutdown` (`open/`) | §6, ergänzt in dieser Closure; die DoD nennt die Rückkehr nach Frist plus Abbruchfrist |
| `close_notify`-Setzung, RSA ab 2048 Bit, Annahme des geprüften Zertifikats durch den Referenzclient | `slice-qa-kompatibilitaet` (`open/`) | §6, ergänzt in dieser Closure (vorher ohne Träger) |
| Setzen und Prüfen von `-Djdk.tls.server.newSessionTicketCount=0` im Serverprozess; Verdrahtung des Listeners | `slice-bootstrap-konfiguration` (`open/`) | DoD *Composition Root, `main`, Start und Stop*, seit `slice-transport-tls-schleife` |
| Last mit vielen Verbindungen | `slice-qa-performance` (`open/`) | Ziel: gleichzeitige Sitzungen |
| Nachprüfung der zweiten Fix-Runde, Wirkung der Änderung zu F-1 | kein Slice | im Register geführt (`BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test`, `BEO-TDS/flake-fix-ohne-reproduzierten-fehler`); wird bei der Verifikation der Welle-Closure oder durch einen weiteren Fund wieder aufgenommen |

- **Gegenstand:** entfällt (der Slice lieferte).
- **Steering-Loop-Eintrag:** Kein `liegt in`: mit diesem Slice ist keine Regel, kein Sensor und keine Gate-Zeile verkörpert worden; gezählt wird, nicht verkörpert. Vier Feststellungen. (1) **Die Mutation des Verifiers findet Zeilen ohne Test, die der Review beim Lesen für belegt hält.** Eigencode (Warten auf lebende Threads, `NonCancellable`, Aufräumen im Zweig nach dem Binden, Verdrahtung des Abweisungszweigs) ließ jeden Test grün, als er entfernt wurde; zwei KDoc-Zusagen sagten mehr, als die Tests trugen. Die Antwort an diesem Slice sind Tests je Zeile (`IoGroupTest` u. a.); als Prüffrage für Implementer und Reviewer („Welcher Test wird rot, wenn diese Zeile entfällt?“) ist sie noch keine Regel. Register: `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (neu, 1×). (2) **Das Muster der Aussage über den Beleg hinaus trat trotz der verkörperten Regel wieder auf** (`BEO-TDS/aussagen-ueber-messung-hinaus`, jetzt 22 Belege): `AGENTS.md` §3.8 war der Maßstab, Review und Nachverifikation fanden die Aussagen vor der Closure; keine Schärfung. (3) **Eine Fix-Runde ohne unabhängige Nachprüfung führt ihre Mutationen als Angabe des Implementers.** Hier die zweite; dasselbe steht in der Closure-Notiz von `slice-transport-tls-schleife` („Nachprüfung durch den Verifier steht aus“) und in der Evidence von `BEO-TDS/strukturtest-ohne-variation-der-eingabe` zu `slice-backend-typabbildung` („kein Verifier hat nachgeprüft“). Die Closure führt sie als *nicht gemessen*, statt sie in die Häkchen zu ziehen; als Regel („jede Fix-Runde nach Verifikation wird nachverifiziert“) ist das nicht verkörpert und hier nicht beschlossen. (4) **Benannte Spec-Lücke:** `SPEC-043` nennt die Form von Zertifikat und Schlüssel („nicht zugelassene Form“) nicht und verweist nicht auf `contracts/security-profile.md` §Schlüsselmaterial, wo die Setzung steht; ein Leser der Spezifikation findet PKCS#12 und RSA ab 2048 Bit nicht. Die Lücke ist benannt, hat aber noch keine Zeile in einer versionierten Spec und keinen Träger-Slice. Über der Schwelle steht `BEO-TDS/strukturtest-ohne-variation-der-eingabe` (mit diesem Vorgang 4×, Stand weiter *offen*): der Lese-Schritt gehört in die Closure der laufenden Welle (`/close-welle` für `welle-transport-tls`), nicht in diese.
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-transport-listener-lebenszyklus.md` ergänzt in `BEO-TDS/aussagen-ueber-messung-hinaus`, `BEO-TDS/strukturtest-ohne-variation-der-eingabe` (Kette mit einem Zertifikat, Review F-5), `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` (`close_notify`, RSA, Diagnose `CERTIFICATE`, `HANDLER_FAILED`), `BEO-TDS/test-belegt-nur-den-fake` (Test-Handler statt echter Session), `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` (Listener und Probe nur gegen die JDK-Gegenstelle) und `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` (24 Verbindungen); zwei neue Beobachtungen mit dieser Evidence-Datei: `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` und `BEO-TDS/flake-fix-ohne-reproduzierten-fehler`. Kein neues Auftreten: `BEO-TDS/messung-am-echten-sql-server-fehlt` (der Slice macht keine Aussage über einen SQL Server), `BEO-TDS/folge-einer-festlegung-im-nachbar-slice-unbenannt` (derselbe Vorgang wie in der Evidence von `slice-handshake-login`, ein Vorgang zählt einmal), `BEO-TDS/test-stage-cache-verdeckt-wiederholung` (die Wiederholungsläufe der Nachverifikation liefen mit `--no-cache-filter test`), `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` (`make docs-check` ohne Befund) und die übrigen. Die Zähler folgen aus den Dateien; `aussagen-ueber-messung-hinaus` steht schon auf `verkörpert`, ihr `state.md` bleibt unverändert.
- **Risiken aus §6:** alle elf tragen genau einen Ausgang (siehe §6): fünf entfallen, sechs weiter offen mit Register und Träger (beim Flackern von F-1 ohne Träger).
- **Drei Paarungen:** Anker: kein Eintrag trägt `liegt in`, die Paarung entfällt. Folge-Slice: die Träger in der Tabelle oben existieren als Dateien in `open/` und führen die Punkte in ihrem Inhalt. Register: acht Evidence-Dateien dieses Vorgangs, siehe oben.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Das Package `transport` ist ein Pfad-Cluster (Achse 3), trägt aber keine eigene Strukturregel (Achse 1) und keine eigene Inventur-Linie (Achse 2): eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, acht Beobachtungen, Stand des Worktrees auf `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/aussagen-ueber-messung-hinaus` — 6×, Stand *geplant*, Träger `welle-entscheidungen-grundlage`. Betrifft diesen Slice als Implementierungs-Slice: jede Aussage trägt einen Test oder ist als Festlegung gekennzeichnet (§2, §6). Kein weiterer Folge-Slice; die Closure-Notiz hält fest, ob das Muster wieder auftrat.
- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 2×, offen. Betrifft jede Aussage über das Verhalten gegen einen echten SQL Server oder den Referenzclient: der Slice macht keine (§1, §6).
- `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` — 2×, offen. Betrifft den TDS-8.0-Pfad: dort gilt MS-TDS- und ADR-Lesart, nicht Messung am Server dieses Repos.
- `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` — 1×, offen. Betrifft die Startprobe und das Fehlen von Pfad und Inhalt in Diagnosen ([TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)); Zeitgleichheit und Prüfwertformat bleiben bei `slice-qa-sicherheit`.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Betrifft dieses Dokument und die Closure-Notiz: `ADR-*` und `TDS-*` stehen als Link.
- Ohne Treffer: `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/vergleichslauf-d-ungeklaert`; `adapter-festlegungen-ohne-slice` berührt den Listener nicht.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
