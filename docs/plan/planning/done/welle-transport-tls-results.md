# Welle welle-transport-tls — TCP-Listener und TLS-Schicht des Servers — Closure-Notiz

> **Zitier-Form** *(bleibt stehen — Norm, kein Ausfüll-Hinweis).* Dieses
> Artefakt friert ein; was es zitiert, bewegt sich weiter. Deshalb: **Kennung,
> nicht Adresse** — `slice-<Kennung>` statt seines Lifecycle-Pfads, `make <target>`
> statt eines Links auf die Sensor-Datei, eine Baseline-Stelle als
> `v<X.Y.Z>` · `regelwerk/<datei>.md` §<Abschnitt> statt als Link
> (Baseline-Regelwerk `grundlagen-harness-dateien.md` §harness/README.md als
> Einstiegspunkt — beim Ausfüllen mit dem adoptierten Tag schreiben).

**Welle:** welle-transport-tls
**Abschluss:** 2026-10-31
**Verantwortlich:** pt9912

## Was wurde geliefert?

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — *was gelernt wurde*: geliefert · was
funktionierte · was anders lief. Mit ID-Bezug, wo es einen gibt.

- Ein Client erreicht den Server über TCP, und beide Verschlüsselungspfade tragen TDS-Nachrichten über eine eigene `SSLEngine`-Schleife ([TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0002](../../adr/0002-tls-und-verschluesselung.md), [ADR-0003](../../adr/0003-transportframework.md)): im TDS-7.4-Pfad läuft der Handshake in Paketen vom Typ `0x12` und schaltet auf rohes TLS um, im TDS-8.0-Pfad läuft TLS ab dem ersten Byte mit ALPN `tds/8.0`.
- `slice-transport-coroutines-geruest`: `kotlinx-coroutines-core` 1.10.1 gepinnt (Nutzerentscheidung, 2026-10-30), Kanal-Abstraktion mit Speicher- und Socket-Adapter, Nachrichtenleser und -schreiber mit der Grenze „vor dem Puffern“ und Rückstau ([TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)).
- `slice-transport-tls-schleife`: die `SSLEngine`-Schleife mit `0x12`-Kapselung und Umschaltpunkt `FINISHED`, der TDS-8.0-Pfad, Negativfälle (TLS 1.1, keine gemeinsame Suite, abgelaufenes und falsch benanntes Zertifikat); Testzertifikate entstehen im Build per `keytool` (Stage `testkeys`, Nutzerentscheidung 2026-10-30, kein Schlüsselpaar im Repo).
- `slice-transport-listener-lebenszyklus`: Listener, Verbindungs-Scope und Übergabe-Schnittstelle an die Protokollschicht, `LOGIN_TIMEOUT`, geordnetes Schließen mit `SHUTDOWN_GRACE`, Startprobe der TLS-Konfiguration vor dem Binden (PKCS#12, Nutzerentscheidung 2026-10-30) ([TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose)).
- Die Aussage gilt für die Transportschicht gegen eine JDK-Gegenstelle auf Loopback, nicht für die Kompatibilität mit dem Referenzclient (Welle-Datei §6); die Zuordnung unten macht sichtbar, wie weit jede Aussage belegt ist.

**Das Mehr gegenüber den Slice-DoDs (Welle-Datei §3).** Der Smoke `ListenerSmokeTest` durchläuft Listener, `0x12`-Kapselung, TDS-8.0-Pfad und Nachrichtenleser und -schreiber in einem Lauf: `both paths carry a message through listener, loop and message codec` und `defective connections do not disturb good ones` — beide `PASSED` im Lauf der Verifikation unten. Der Handler im Test ist ein Echo, kein Protokoll.

Die Aussagen des Transports, in zwei Spalten. *Gemessen* heißt am Loopback gegen eine **JDK-Gegenstelle** im selben Prozess beobachtet (kein Referenzclient, kein SQL Server) und gilt für Zusagen, die ein bestandener Test trägt; *Festlegung* heißt MS-TDS-Lesart oder Entscheidung eines Slice ohne Referenzclient-Messung. Die Tabelle verdichtet die Tabellen der drei Slice-Closures.

| Spalte | Aussage | Beleg im Repo |
|---|---|---|
| gemessen | Eine Nachricht läuft in beiden Pfaden (7.4 mit `0x12`, 8.0 ab dem ersten Byte), byteweise zerstückelt, durch Listener, Schleife, Nachrichtenleser und -schreiber hin und zurück; 24 gleichzeitige Verbindungen, davon defekte, stören die guten nicht | `ListenerSmokeTest` (2 Tests), `ConnectionStormTest` |
| gemessen | Der Umschaltpunkt `FINISHED` verliert keine Bytes (dicht gepackte Bytes); TLS 1.1, fehlende gemeinsame Suite, abgelaufenes oder falsch benanntes Zertifikat enden in einem Ergebniswert; ein KeyUpdate der Gegenstelle in TLS 1.3 löst einen kurzen Record aus; ein blockierter Schreiber hält den Leser nicht auf | `TlsLoopbackTest` (14), `TlsNegativeTest` (13), `TlsDataPhaseTest` (6), `TlsTicketFlagTest` (3) |
| gemessen | Die Grenze „vor dem Puffern“ (Untergrenze und Obergrenze am Bytezähler), Rückstau am Speicher-Kanal deterministisch; am Socket suspendiert der Schreiber gegen eine Gegenstelle, die nicht liest (eine Maschine, Linux, Loopback, 32 MiB, 500 ms) | `CoroutinesPinTest`, `MemoryChannelTest`, `SocketChannelLoopbackTest` (8) |
| gemessen | `LOGIN_TIMEOUT` ab Verbindungsaufbau in beiden Pfaden (Zeit gesteuert über `ManualDelayer`), Client-Disconnect räumt Scope und Kanal, geordnetes Schließen mit Frist, Fehlstart (belegter oder ungültiger Port, Fehler nach dem Binden) gibt Port und Threads frei, Startprobe meldet Kette, Speicher, Schlüssel, Paar mit dem richtigen Teil und ohne Pfad oder Inhalt | `ListenerLifecycleTest` (6), `ListenerShutdownTest` (10), `ListenerStartTest` (6), `IoGroupTest`, `TlsStartProbeTest` (7) |
| Festlegung | Pin 1.10.1; Handler-Schnittstelle mit zwei Meldungen (`loginCompleted`, `requestRunning`); eigene Kanalgruppe mit zwei Threads je Listener; `Delayer` statt `kotlinx-coroutines-test`; die Startprobe nutzt den Pfad ab dem ersten Byte | KDoc, Slice-Notizen; die Tests belegen das Verhalten, die Wahl folgt keiner Zeile der Spezifikation |
| Festlegung | PKCS#12 als einzige Form, erster Schlüsseleintrag, Speicher- und Schlüsselkennwort gleich, RSA ab 2048 Bit; ein beschädigter Speicher ist `CERTIFICATE`; der Server antwortet nicht auf `close_notify` der Gegenstelle; `HANDLER_FAILED` für eine `CancellationException` des Handlers bei unabgebrochenem Scope | `contracts/security-profile.md`, KDoc von `TlsMaterial` und `ManagedConnection`; die Wahl PKCS#12 hat der Nutzer entschieden, die Einzelheiten sind vom Nutzer nicht bestätigt (siehe Steering-Loop-Einträge) |
| nicht gemessen | Der Referenzclient gegen Listener, Schleife und Startprobe, TDS 8.0 und TLS 1.2 am Referenzclient, ein echter SQL Server, Neuverhandlung in TLS 1.2, Rückstau und Verhalten unter Last im Produktivbetrieb, geordnetes Schließen mit einer echten Session und laufender Anfrage, die zweite Fix-Runde `9d3d31d` des Listener-Slice durch einen unabhängigen Verifier | Träger in der Tabelle unten; Register |

**Jeder in Welle-Datei §6 ausgeschlossene Gegenstand hat einen Träger als Datei oder den Grund, warum nicht** (Prüfung am 2026-10-31 gegen die Dateien im Planning-Lifecycle):

| Ausgeschlossen | Träger |
|---|---|
| Handshake- und Session-Ablauf, Zustandsautomat | `slice-handshake-login` (`done/`, Welle `welle-handshake-login`; [ADR-0004](../../adr/0004-session-zustandsautomat.md)) |
| Backend, Anfrageverarbeitung, Fehlersemantik, `TDS_SHUTDOWN`, Cancel | `slice-backend-port-vertrag` und `slice-sql-batch-dispatch` (`done/`), `slice-abbruch-ressourcen-shutdown` und `slice-attention-cancel-timeout` (`open/`); Wellen `welle-backend-streaming`, `welle-anfrageverarbeitung`, `welle-fehler-abbruch` |
| Authentifizierung | `slice-auth-adapter-benutzerliste` (`done/`, Welle `welle-handshake-login`) |
| Konfigurationsvalidierung, Prozessstart, Composition Root, JVM-Flags | `slice-bootstrap-konfiguration` (`done/`, Welle `welle-handshake-login`) |
| Telemetrie (`observability`) | `slice-betrieb-log-schnittstelle` (`open/`, Welle `welle-betrieb-diagnose`) |
| Referenzclient, echter SQL Server | `slice-qa-kompatibilitaet` und `slice-qa-vergleichslauf-d` (`open/`) |
| Last über einige Dutzend Verbindungen, Langzeitläufe | `slice-qa-performance` (`open/`); Fuzz und parallele Verbindungen: `slice-qa-stabilitaet` (`done/`) |
| Neuverhandlung in TLS 1.2 | `slice-qa-sicherheit` (`open/`) |
| Pooling | `slice-qa-kompatibilitaet` (`open/`, DoD-Punkt 3) |
| MARS, Sitzungswiederaufnahme, ECDSA- und Client-Zertifikate | kein Träger: das Lastenheft verlangt keine davon; MARS nennt es nur als Erhebungsgegenstand der Clientprofil-Abfrage (Zeile der Betreiber-Tabelle), nicht als Anforderung |
| Neue Anforderungen oder Änderungen am Lastenheft | kein Träger nötig für diese Welle: die einzige Änderung seit der Eröffnung ist Lastenheft 0.5.0 (`f067e2f`, `IS NULL` im Simulations-Backend, [TDS-FA-11](../../../../spec/lastenheft.md)), sie berührt Transport nicht |

Die Nutzerentscheidungen aus den Slice-Risiken (§3) sind getroffen und stehen in den Notizen der Slices: Version des Coroutinen-Pins (1.10.1) in `slice-transport-coroutines-geruest`, Herkunft des Testzertifikats (`keytool` im Build) in `slice-transport-tls-schleife`, Format von Zertifikat und Schlüssel (PKCS#12, kein PEM-Lader) in `slice-transport-listener-lebenszyklus`.

## Was hat funktioniert?

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3.

- Der Schnitt gerüst vor schleife vor listener trug in allen drei Slices; keiner brauchte die Rückführung nach Welle-Datei §4. Jeder Slice nutzte den davor, ohne dass eine Schnittstelle neu geschnitten wurde.
- Deterministische Belege statt Zeit: Bytezähler am Kanal für die Grenze vor dem Puffern, steuerbare Zeitquelle (`ManualDelayer`) für alle Fristen, Zertifikate aus der Build-Stage. Nur der Socket-Rückstau ist zeitbasiert, und seine Notiz sagt es.
- Die Mutation des Verifiers fand in allen drei Slices, was der Review beim Lesen für belegt hielt (Pin-Test gegen den Lock statt gegen den Katalog, Konfiguration vom JDK-Default getragen, Wartebedingungen ohne Test); die Trennung Review (Lesen) und Verifikation (Mutation) hat sich bezahlt gemacht.
- Eine unabhängig geschriebene Gegenstelle (`JdkTlsPeer`) und die Gliederung der Aussagen nach Beleg-Stärke machten die Review-Findings behebbar durch Einschränken oder Nachmessen.

## Was ging anders als geplant?

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — jede Zeile moeglichst mit der Konsequenz,
die daraus schon gezogen wurde (Folge-Slice, Spec-Version).

- Der Listener-Slice brauchte zwei Fix-Runden statt einer (Review fünf MEDIUM, Nachverifikation drei weitere). Die zweite Fix-Runde hat kein Verifier nachgeprüft; das steht in der Zuordnung als *nicht gemessen*. Konsequenz: keine Regel, die Grenze ist benannt; die Beobachtung führt `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test`.
- Die Leseseite der TLS-Schleife hing an der Schreibsperre (Review M1); behoben mit `tryLock`, der Wettlauf zwischen Vormerken und Loslassen ist nicht belegt.
- Die Abbruchausnahme `HANDLER_FAILED` entstand als Folge einer Festlegung im Nachbar-Slice (`slice-handshake-login`) und war im Plan nicht genannt (`BEO-TDS/folge-einer-festlegung-im-nachbar-slice-unbenannt`).
- Der Transitiv-Sprung `org.jetbrains:annotations` 13.0 auf 23.0.0 durch den Coroutinen-Pin stand nicht im Plan; er ist in der Notiz des Gerüst-Slice nachgetragen.
- Drei Träger-Prüfungen der Slice-Closures fanden Punkte ohne Träger (Neuverhandlung, `close_notify`, Abbruchausnahme) und legten sie an Slices an (`BEO-TDS/offene-punkte-ohne-traeger`, bereits verkörpert).

## Steering-Loop-Einträge

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 (hier stehen **nur** Beobachtungen, die im
Register 3× erreicht haben; jeder Eintrag nennt seine `BEO-<KUERZEL>/<slug>`) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln (Feld
und Zielort auf **einer** Zeile, Sektionsangabe innerhalb der Backticks).

**Lese-Schritt des Registers (2026-10-31).** Das Register führt 40 Beobachtungen; 13 haben mindestens drei Evidence-Dateien. Sechs davon tragen schon einen Ausgang und werden hier nicht neu entschieden; **sieben stehen über der Schwelle ohne Ausgang** und sind die offene Frage dieser Closure.

*Schon mit Ausgang* (der Lese-Schritt bestätigt den Stand; das Auftreten in den drei Slices dieser Welle ist als Evidence geführt):

- `BEO-TDS/aussagen-ueber-messung-hinaus` (29×): `verkörpert` in `AGENTS.md` §3.8 und im Reviewer-Skill, Anker `seit welle-protokollcodec`. Die Regel trat in allen drei Slices dieser Welle wieder auf (Review fand Aussagen breiter als der Test, z. B. KDoc des Listeners); sie wurde dort als Finding mit Konventions-Anker geführt und behoben, also wirksam im Review, nicht vorbeugend. Nichts zu lockern.
- `BEO-TDS/messung-am-echten-sql-server-fehlt` (16×): `geplant`, Träger `slice-qa-vergleichslauf-d`. `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` (12×): `geplant`, Träger `slice-qa-kompatibilitaet`. Beide Träger sind Dateien in `open/`; die Welle schrieb TDS 8.0 und TLS 1.2 nur gegen die JDK-Gegenstelle fest.
- `BEO-TDS/offene-punkte-ohne-traeger` (15×): `verkörpert`, Anker `seit welle-entscheidungen-grundlage`.
- `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` (5×): `geplant`, Träger `slice-qa-sicherheit`. `BEO-TDS/test-stage-cache-verdeckt-wiederholung` (3×): `verkörpert`, Anker `seit slice-handshake-login`.

*Über der Schwelle, nicht verkörpert, Entscheidung beim Nutzer.* Diese Closure verkörpert keine dieser Regeln und ändert keine Hard Rule in `AGENTS.md`: eine neue Regel oder ein neuer Sensor ist eine Entscheidung des Nutzers, und kein Beleg trägt mehr als die Zählung. Jeder Eintrag steht mit Vorschlag im Bericht der Closure; sein `state.md` trägt den Stand `offen` mit diesem Vermerk. Es gibt kein `liegt in`, die Paarung (a) entfällt.

- `BEO-TDS/strukturtest-ohne-variation-der-eingabe` — 6× (`slice-auth-adapter-benutzerliste`, `slice-backend-typabbildung`, `slice-fehler-zustandsuebergang`, `slice-handshake-login`, `slice-qa-stabilitaet`, `slice-transport-listener-lebenszyklus`). Aus dieser Welle: Kette mit einem Zertifikat (Review F-5).
- `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` — 6× (`slice-backend-sequenzierer`, `slice-bootstrap-konfiguration`, `slice-fehler-mapping`, `slice-fehler-zustandsuebergang`, `slice-sql-batch-dispatch`, `slice-transport-listener-lebenszyklus`). Aus dieser Welle: Warten auf lebende Threads, `NonCancellable`, Aufräumen nach dem Binden.
- `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` — 6× (`slice-adr-simulation-formen`, `slice-backend-sequenzierer`, `slice-backend-typabbildung`, `slice-bootstrap-konfiguration`, `slice-fehler-mapping`, `slice-transport-listener-lebenszyklus`). Aus dieser Welle: `close_notify`, RSA ab 2048 Bit, Diagnose `CERTIFICATE`, `HANDLER_FAILED`.
- `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` — 3× (`slice-fehler-zustandsuebergang`, `slice-transport-coroutines-geruest`, `slice-transport-listener-lebenszyklus`). Aus dieser Welle: Rückstau nur am Speicher-Kanal und auf einer Maschine am Socket belegt.
- `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen` — 3× (`slice-auth-adapter-benutzerliste`, `slice-bootstrap-konfiguration`, `slice-handshake-login`); aus der Nachbarwelle, in dieser Welle ohne Evidence.
- `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung` — 3× (`slice-adr-pruefwertformat`, `slice-adr-simulation-formen`, `slice-auth-adapter-benutzerliste`); aus dieser Welle keine Evidence. Sein `state.md` nannte vorher die Closure von `welle-simulations-backend` als Lese-Schritt; die Schwelle ist erreicht, deshalb liest ihn diese Closure.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 3× (`slice-adr-clientprofil`, `slice-adr-simulation-formen`, `slice-sql-batch-dispatch`); aus dieser Welle keine Evidence. Das Doku-Gate (`make docs-check`, `id-unlinked`) fängt den Zustand; offen ist, ob die Beobachtung damit `gestrichen` werden kann.

**Spec-Lücken:** keine neue benannt. Die offene Lücke zu `SPEC-051` (Einzelfälle ohne Lastenheft-Deckung) gehört der Nachbarwelle und steht im Eintrag oben.

## Beobachtungs-Register (Zeiger)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register — der Zähler wird **nicht** hier gepflegt; diese
Sektion ist ein Zeiger und trägt keine Daten.

Der Zähler steht in [`../observations/`](../observations/).
Was in dieser Welle **3×** erreicht hat, steht oben unter
*Steering-Loop-Einträge*.

## Folge-Slices

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — **derivativ**: Diese Liste zeigt nur,
das Original ist die Slice-Datei. Jeder genannte Folge-Slice muss als Datei im
Planning-Lifecycle existieren; genannt ohne angelegt ist dieselbe Klasse wie
ein halluziniertes Gate.

Keine neuen Slices durch diese Closure. Die Träger der Messlücken existieren als Dateien und führen die Punkte in DoD oder Risiko:

- `slice-qa-kompatibilitaet` (Referenzclient gegen Listener und Startprobe, TDS 8.0, TLS 1.2, Pooling, `close_notify`-Setzung) — `open/`.
- `slice-qa-sicherheit` (Neuverhandlung in TLS 1.2, TLS-Modus-Nachweis) — `open/`.
- `slice-qa-performance` (Last mit vielen Verbindungen, Rückstau im Betrieb) — `open/`.
- `slice-abbruch-ressourcen-shutdown` (Obergrenze von `shutdown()`, geordnetes Schließen mit echter Session) — `open/`.
- `slice-qa-vergleichslauf-d` (Messung am echten SQL Server) — `open/`.

## Verifikation

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 1 — keine Behauptung ohne nachprüfbaren
Anker (Hash, Lauf, Zahl).

- Alle Slices der Welle in `done/`: `slice-transport-coroutines-geruest` (Merge `95c5c56`), `slice-transport-tls-schleife` (Merge `1dad1ad`, Fix-Runde `43d3958`), `slice-transport-listener-lebenszyklus` (Stand `a8072b5` nachverifiziert, zweite Fix-Runde `9d3d31d` ohne Nachprüfung).
- `make gates` Exit 0 am 2026-10-31 auf `main` (`092b7a1`) vor der Closure; `baseline-verify: v6.18.1 OK — 54 Dateien`.
- Frischer Testlauf ohne Cache (`docker build --no-cache-filter test --target test`): `BUILD SUCCESSFUL in 1m 20s`, 633 Tests `PASSED`, kein Test `FAILED`, davon der Welle-Smoke `ListenerSmokeTest > both paths carry a message through listener, loop and message codec() PASSED` und `ListenerSmokeTest > defective connections do not disturb good ones() PASSED`; die Gruppen der Zuordnung oben zählen `TlsLoopbackTest` 14, `TlsNegativeTest` 13, `TlsDataPhaseTest` 6, `TlsTicketFlagTest` 3, `TlsStartProbeTest` 7, `ListenerLifecycleTest` 6, `ListenerShutdownTest` 10, `ListenerStartTest` 6, `SocketChannelLoopbackTest` 8.
- Trigger-Audit, je Klasse:
  - **Carveouts:** zwei aktiv, 0 fällig. [CO-001](../../carveouts/CO-001-nuget-audit-aus.md) und [CO-002](../../carveouts/CO-002-nuget-signaturpruefung-fehlt.md) betreffen die .NET-Referenztests; ihr Auflösungs-Trigger (Folge-Slice `slice-qa-dotnet-lieferkette-haerten` in `done/`) ist nicht eingetreten, der Folge-Slice liegt als Datei in `open/`, und die Welle berührt die .NET-Referenztests nicht. Beide bleiben aktiv; die Welle schließt mit grünem Gate, nicht auf einem Carveout.
  - **Bootstrap-aware Gates:** 0 offen. Die Tabelle *Sensors* in `harness/README.md` führt kein Target mit Schwelle oder Reifestufe; die Bindungen sind ADR- und Carveout-Verweise.
  - **ADR-Re-Evaluierungs-Trigger** der von der Welle berührten, angenommenen ADRs: [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md) (fehlgeschlagener Handshake im verbindlichen Profil: der Referenzclient wurde nicht gefahren, Wächter ist `slice-qa-kompatibilitaet`), [ADR-0002](../../adr/0002-tls-und-verschluesselung.md) (Änderung des Verschlüsselungsdefaults: nein), [ADR-0003](../../adr/0003-transportframework.md) (nachweisbare Leaks: die Review-Funde F-2, F-3 und F-6 des Listener-Slice waren Lecks im Eigencode, behoben und mit Tests belegt, sie widersprechen der Wahl von Coroutinen und `AsynchronousSocketChannel` nicht; unzureichende Backpressure: nicht eingetreten, der Rückstau im Betrieb ist nicht gemessen, Wächter ist `slice-qa-performance`) — 0 fällig. Für die Gate-ADRs ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0010](../../adr/0010-detekt-pre-release.md)) liegt keine Änderung vor (detekt `2.0.0-alpha.6` und SqlClient-Pin unverändert); ob upstream ein Stabil-Release von detekt 2.x erschien, prüft kein Sensor und diese Closure nicht (netzlos) — ein Trigger ohne Wächter, hier benannt.
- Zuordnung *gemessen | Festlegung* und Träger je ausgeschlossenem Gegenstand: siehe oben (Closure-Trigger der Welle-Datei).
- Drei Paarungen: (a) Anker — kein Eintrag trägt `liegt in`, entfällt. (b) Folge-Slice — die fünf genannten Dateien liegen in `open/`. (c) Register — alle genannten `BEO-TDS/<slug>` existieren als Verzeichnis mit nicht leerem `evidence/` (gegen das Dateisystem geprüft).
- Archivierung (Schritt 4): `archive-welle welle-transport-tls` lief mit Kennung [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität) in zwei Commits (`1dfedac` reiner Move, `17e94cd` Archiv, Stubs und Verweis-Nachzug): 4 Slices und der Welle-Plan nach `done/welle-transport-tls/archiv.zip` (112370 Bytes, 13 Dateien), 5 Stubs, 8 Review-Reports aus `docs/reviews/` entfernt (je 2 zu den vier Slices), Verweis-Nachzug in 11 Dateien. Befunde:
  - Der vierte Slice im Archiv ist `slice-codec-rpc-parser`: er war wellenlos und lag vor dieser Closure (kein anderes Archiv beanspruchte ihn); er gehört nicht zur Welle, wurde aber nach der Regel „wellenlose seit der letzten Closure“ mit eingesammelt.
  - Die Vorprüfung sperrte einmal mit `[haenger]`: der Report von `slice-qa-stabilitaet` nannte den Dateinamen des Listener-Reviews in einer Aufzählung (kein Link). Der Dateiname wurde durch eine Beschreibung ersetzt (Commit `b25a073`), danach lief die Vorprüfung ohne Sperre. Ein zweiter Halt war der unsaubere Arbeitsbaum durch das ungetrackte Verzeichnis `.claude/worktrees/`; es steht lokal in `.git/info/exclude`, nicht im Repo.
  - Die Vorprüfung `[untergrenze]` sperrte nicht: `done/` führte schon ein `archiv.zip`.
  - Geltungsbereich der Sensoren vor der Archivierung geprüft: kein Skript, kein Make-Fragment und kein Eintrag in `.d-check.yml` keilt auf `done/*.md` (der einzige Treffer ist ein auskommentierter Eintrag); `make docs-check` ohne Befund nach dem Lauf (391 Dateien), die Stubs tragen ihre Kennungen als Links.
- `make gates` Exit 0 nach dem Move und nach der Archivierung.
