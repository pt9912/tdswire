# Slice slice-transport-coroutines-geruest: Coroutine-Gerüst: kotlinx-coroutines-Pin, Kanal-Abstraktion und Nachrichtenleser über dem Paket-Codec

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-transport-tls

**Bezug:** [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0003](../../adr/0003-transportframework.md)

**Berührte Spec-Stellen:** SPEC-007 · SPEC-024 · SPEC-027 · SPEC-046 · ARC-003 · ARC-013

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

**Ziel:** Der Transport hat eine suspendierende Kanal-Abstraktion über `AsynchronousSocketChannel` und einen Nachrichtenleser und -schreiber, der beliebig zerstückelte Bytes mit dem Paket-Codec aus `slice-codec-tds-paket` zu Nachrichten zusammensetzt und Nachrichten rückstaufest schreibt; `kotlinx-coroutines-core` ist per Lock und Prüfsumme gepinnt. Ein Loopback-Test belegt das in Docker.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- `SSLEngine`, TLS-Handshake und die Kapselung in Paketen vom Typ `0x12` — das ist `slice-transport-tls-schleife`; dieser Slice liefert nur den Kanal, auf dem sie aufsetzt ([ADR-0002](../../adr/0002-tls-und-verschluesselung.md)).
- `AsynchronousServerSocketChannel`, Annahmeschleife, Verbindungs-Scopes, Timeouts und Shutdown — das ist `slice-transport-listener-lebenszyklus`; hier gibt es nur einen verbundenen Kanal.
- Verbindungs-Zustandsautomat, PRELOGIN-/LOGIN7-Ablauf und jede Entscheidung, welche Nachricht in welchem Zustand zulässig ist — der Zustandsautomat ist [ADR-0004](../../adr/0004-session-zustandsautomat.md) (`Proposed`); der Leser liefert Nachrichten (Typ und Bytes) und urteilt nicht über deren Reihenfolge.
- Inhalt und Struktur der Nachrichten (Token-Codec, PRELOGIN-Tabelle): der Leser reicht Bytes weiter und kennt keine Tokens.
- Neue Paket-Codec-Funktionen: Header, Fragmentierung und Grenzprüfung kommen aus `slice-codec-tds-paket` und werden nicht dupliziert; fehlt dort etwas, ist das eine Rückmeldung an diesen Slice, kein Nachbau.
- Lasttests, Latenz-Perzentile und mehr als einige Dutzend gleichzeitige Verbindungen — [ADR-0003](../../adr/0003-transportframework.md) nennt sie als nicht gemessen; sie gehören in `slice-qa-performance`.
- Netty, Java-NIO-Selektoren und alle anderen Laufzeiten — die Wahl ist getroffen.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Abhängigkeit** ([ADR-0003](../../adr/0003-transportframework.md), SPEC-042, [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)): `kotlinx-coroutines-core` steht in `gradle/libs.versions.toml`, `gradle.lockfile` und `gradle/verification-metadata.xml` (geschrieben von `make deps-lock`, nicht von Hand); `make build`, `make detekt` und das Test-Gate laufen mit dem Lock. Die Version nennt der Slice in §7; der Spike maß 1.10.1 (Frage an den Nutzer, siehe §6).
- [x] **Kanal-Abstraktion** im Package `transport` (ARC-003, ARC-013): suspendierende Schnittstelle für Lesen in einen Puffer, vollständiges Schreiben und Schließen; Adapter für `AsynchronousSocketChannel` (Abbruch der Coroutine schließt den Kanal, Lese- und Schreibfehler sind Ergebniswerte mit Kategorie nach SPEC-011, keine ungefangene Ausnahme) und ein Speicher-Kanal für Tests ohne Netz.
- [x] **Nachrichtenleser und -schreiber** (SPEC-007, SPEC-046, SPEC-027): der Leser setzt über den Kanal beliebig zerstückelte Eingabe mit dem Paket-Codec zu Nachrichten zusammen und bricht ab, bevor die Nachricht über der Grenze gepuffert ist (Grenze als Parameter: SPEC-046 vor dem Login, SPEC-007 danach); der Schreiber zerlegt eine Nachricht in Pakete der Packet-Size und schreibt suspendierend, sodass kein Schreibpfad unbegrenzt puffert.

Nachweis-Pflichten für alle drei Punkte:

- [x] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>` (Konvention nach `slice-qa-spec-abdeckung`); die Tests laufen in `make test` und sind grün.
- [x] Loopback-Test (`127.0.0.1`, Docker-Container, kein Mount): Nachrichten werden byteweise und in zufälligen Stücken (fester Startwert) gesendet und kommen vollständig und gleich an; eine Nachricht über der Grenze beendet den Leser mit einem Ergebniswert, und der Test belegt über die Zahl der gelesenen Bytes, dass nicht erst vollständig gepuffert wurde.
- [x] Rückstau-Test mit einem langsamen Leser: Der Schreiber suspendiert, und die Zahl der im Speicher gehaltenen Bytes bleibt unter einer im Test genannten Grenze. Gemessen ist das am Kanal des Tests, nicht an 100 MiB wie im Spike; eine größere Aussage steht nicht in KDoc oder Notiz.
- [x] Abgeschnittene, überlange und zufällig erzeugte Eingaben sowie ein abrupt geschlossener Kanal enden in einem Ergebniswert; der Testprozess bleibt am Leben ([TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)).
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist (`harness/README.md` §Sensors nur, falls ein neues Target entsteht — geplant ist keines; `contracts/` bleibt unverändert).
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
| `gradle/libs.versions.toml`, `build.gradle.kts` | update | `kotlinx-coroutines-core` als `implementation`; Version nur in der Katalogdatei |
| `gradle.lockfile`, `gradle/verification-metadata.xml` | update | per `make deps-lock` (braucht Netz) neu geschrieben, nicht von Hand |
| `src/main/kotlin/io/github/pt9912/tdswire/transport/channel/` | neu | `SuspendingChannel`, `ChannelResult`, `SocketChannelAdapter` für `AsynchronousSocketChannel`, `MemoryChannel` (Paar mit begrenztem Puffer); keine Imports aus `port`, `simulation`, `runtime` (SPEC-041) |
| `src/main/kotlin/io/github/pt9912/tdswire/transport/messaging/` | neu | `MessageReader` (ruft `MessageAssembler`), `MessageWriter` (ruft `PacketWriter`), Ausgänge `ReadOutcome`, `WriteOutcome`, `TransportError`; `transport/framing` bleibt unverändert |
| `src/test/kotlin/io/github/pt9912/tdswire/transport/channel/`, `.../transport/messaging/`, `.../testsupport/TransportSupport.kt` | neu | Pin, Speicher-Kanal, Loopback, Zerstückelung mit festem Startwert, Grenze vor dem Puffern, Rückstau, abgeschnittene und zufällige Eingaben; `@spec`-Tag je Test; Handvektoren von Hand gebaut, ohne den Paket-Codec |
| `src/test/java/io/github/pt9912/tdswire/transport/channel/FailingCloseChannel.java`, `src/test/kotlin/io/github/pt9912/tdswire/transport/channel/SocketChannelAdapterCloseTest.kt` | neu | Test des Fehlerverhaltens von `close()`: eine Kanal-Attrappe, deren `close()` wirft; sie liegt in Java, siehe §7 (Steering-Loop-Eintrag, Feststellung 3) |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

- Reihenfolge: Pin zuerst (ein Test, der `runBlocking` aufruft, belegt, dass Lock und Prüfsumme greifen), dann Kanal, dann Leser und Schreiber.
- `make detekt` läuft auch über `detektTest`; Befunde zu Coroutinen (z. B. `GlobalCoroutineUsage`, `InjectDispatcher`) werden im Code behoben, nie unterdrückt oder in `config/detekt/detekt.yml` gelockert (`AGENTS.md` §3.2, §3.6).
- Der Paket-Codec (`MessageAssembler`, `PacketWriter`, `FramingLimits`) liegt in `transport/framing`; Leser und Schreiber rufen ihn und rechnen die Grenze nicht nach.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Der Start-Trigger der Welle `welle-transport-tls` ist eingetreten (`slice-codec-tds-paket` liegt in `done/`, `make gates` auf `main` grün) und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2 oder Leser und Schreiber sprengen den Rahmen. Schnittvorschlag: Slice A1 = Pin und Kanal, Slice A2 = Nachrichtenleser und -schreiber; die Welle bekommt dann vier Slices.
- `in-progress` → `open` (blockiert — Carveout?): `make deps-lock` scheitert oder die Abhängigkeit lässt sich nicht per Lock und Prüfsumme fixieren, sodass der Build nicht reproduzierbar ist.

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

- Der Zeitpunkt des Pins: [ADR-0003](../../adr/0003-transportframework.md) sagt „sobald der erste Transport-Code entsteht“; `slice-codec-tds-paket` las das als „sobald Code mit I/O entsteht“ und nahm die Abhängigkeit nicht auf. Dieser Plan nimmt sie in den ersten Slice mit I/O. Entschieden (Nutzer, 2026-10-30): Pin in diesem Slice, Version 1.10.1. — **Ausgang:** **entfallen** — entschieden (Nutzer, 2026-10-30) und umgesetzt: Pin in diesem Slice, Version 1.10.1 in `gradle/libs.versions.toml`, Lock und Prüfsummen von `make deps-lock`. Die Auslegung von [ADR-0003](../../adr/0003-transportframework.md) („sobald der erste Transport-Code entsteht“) ist damit entschieden.
- Das Test-Gate (`make test`) kommt aus `slice-codec-tds-paket`; liegt es beim Start nicht in `done/`, ist der Start-Trigger nicht erfüllt, und dieser Slice liefert kein eigenes zweites Gate. — **Ausgang:** **entfallen** — `slice-codec-tds-paket` liegt in `done/`, `make test` lief (Verifikation: 143 Tests grün in der Stage `test`); der Slice liefert kein eigenes Test-Gate.
- Loopback-Sockets im Docker-Build: Der Test braucht `127.0.0.1` im Build-Container. Das ist ohne Netzfreigabe zu erwarten, aber hier nicht gemessen; scheitert es, ist der Test auf den Speicher-Kanal zu begrenzen, und der Slice kann den Adapter nicht belegen. — **Ausgang:** **entfallen** — nicht eingetreten: Loopback-Sockets laufen im Build-Container ohne Netzfreigabe, die sechs Loopback-Tests liefen in 13 Läufen der Verifikation grün (0 Flakiness).
- Rückstau ist im Spike für 100 MiB gemessen und im Produktivcode „zu liefern“ ([ADR-0003](../../adr/0003-transportframework.md)); dieser Slice belegt eine kleinere Grenze am Test-Kanal, nicht den Produktivbetrieb unter Last. — **Ausgang:** **weiter offen** → Beobachtungs-Register: `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` (neu, siehe §7). Belegt ist nur der Speicher-Kanal (Kapazität 1024, Nachricht 100000 Bytes) und das Suspendieren eines Socket-Schreibers auf dieser Maschine; 100 MiB und Last sind nicht gemessen.
- Der Leser muss die Grenzprüfung vor dem Puffern auslösen, die der Paket-Codec als Funktion anbietet; weichen beide in der Auslegung ab (Summe der Paketlängen gegen Nachrichtenlänge), entsteht ein Doppelbefund. Gegenmittel: der Leser ruft die Prüfung des Codecs, er rechnet nicht selbst. — **Ausgang:** **entfallen** — nicht eingetreten: `MessageReader` ruft `MessageAssembler` und rechnet nichts nach (Review ohne Befund, Mutation M01 des Verifiers rot); es gibt keine zweite Auslegung der Grenze.
- Strenge der statischen Analyse: Coroutinen-Code erzeugt Befunde zu Komplexität und Größe; die Abhilfe ist Zerlegung im Code. — **Ausgang:** **entfallen** — die Befunde der statischen Analyse sind durch Zerlegung im Code behoben, ohne `@Suppress` und ohne Änderung an `config/detekt/detekt.yml`; `make detekt` ist grün. Die eine Umgehung einer Regel durch Java-Quelltext steht als Frage in §7, sie ist kein Fortbestehen dieses Risikos.
- Beobachtung zum Pin: `kotlinx-coroutines-core-jvm` 1.10.1 zieht `org.jetbrains:annotations:23.0.0` mit; per Konfliktauflösung ersetzt es 13.0 auf Compile-, Runtime- und Test-Classpath (13.0 bleibt für die Kotlin-Werkzeug-Konfigurationen). Reine Annotationen, kein Laufzeiteffekt; Lock und Prüfsummen sichern die Auflösung (`gradle.lockfile`). — **Ausgang:** **entfallen** — die Auflösung ist durch `gradle.lockfile` und Prüfsummen gesichert, `org.jetbrains:annotations` sind reine Annotationen ohne Laufzeiteffekt; der Sprung steht hier und in §7 (Verifikation V-4).

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

- **Was hat funktioniert:** Der Schnitt trug: Pin, Kanal und Nachrichtenleser/-schreiber lagen in einem Slice, ohne dass die Rückführung nach §4 nötig wurde. Die Grenze „vor dem Puffern“ ließ sich deterministisch über einen Bytezähler am Kanal belegen statt über Zeit, der Rückstau am Speicher-Kanal ebenso; nur der Socket-Rückstau ist zeitbasiert und sagt das. Die Verifikation fuhr 23 Mutationen (20 rot, 3 überlebt: M07a `CountingChannel` zählt nichts, M17 Katalog auf 1.10.0, M19 Schreibfehler des Socket-Adapters als `Ok`); alle drei sind seitdem durch Tests gedeckt (Angabe des Implementers; der Verifier hat sie nicht erneut mutiert). Der Implementer sah davor 6 und nach den Befunden 4 Mutationen rot (Angabe im Handoff, die Liste liegt nicht im Repo).
- **Was ging anders als geplant:** (a) Der Pin-Test belegte die vom Lock aufgelöste Version, nicht den Katalog (Verifikation V-1); er vergleicht jetzt den Katalogwert mit dem Jar-Namen, und sein KDoc sagt „aufgelöste Version“. (b) Die Nachweise der „Grenze vor dem Puffern“ hatten nur eine Obergrenze und hingen an einem ungeprüften Zähler (V-2); die Untergrenze 4104 bis 4104 + 512 steht jetzt in beiden. (c) Der Schreibfehler des Socket-Adapters war ungetestet (V-3); zwei Tests decken ihn. (d) Der Sprung `org.jetbrains:annotations` 13.0 auf 23.0.0 stand nicht im Plan (V-4; jetzt §6 und hier). (e) Die `@spec`-Tags SPEC-024, SPEC-042 und SPEC-027 sind an Tests entfernt, die das jeweilige Gate nicht belegen (V-5, Review F10). (f) KDoc breiter als das Verhalten: `ChannelResult` („nie eine Ausnahme“, Review F2, V-7), „höchstens ein Lese- und ein Schreibvorgang“ (F1) und `close()` (F3) sind eingeschränkt oder als Festlegung gekennzeichnet. (g) Die Plan-Tabelle nannte keine Java-Datei und keinen Test für `close()`; sie ist nachgezogen. Die Befunde sind eingearbeitet (Merge `922c4eb`).
- **Reports:** [Review](../../../reviews/2026-10-30-95c5c56-slice-transport-coroutines-geruest.md) (kein HIGH, kein MEDIUM; F1 bis F5 LOW, F6 bis F10 INFO) und [Verifikation](../../../reviews/2026-10-30-95c5c56-verifikation-slice-transport-coroutines-geruest.md) (kein HIGH; V-1 bis V-3 MEDIUM, V-4 bis V-7 LOW; 13 Testläufe ohne Flakiness), beide gegen den Merge `95c5c56`. Ein Validator-Lauf entfällt: der Slice liefert keinen End-Nutzer-Wert, außerhalb der Tests nutzt bisher kein Aufrufer Kanal, Leser und Schreiber. CI: der GitHub-Lauf 41028544823 für `30baefa` war erfolgreich und enthält das Test-Gate; ein Lauf für den Closure-Stand liegt zum Zeitpunkt dieser Notiz nicht vor.
- **Version des Pins:** `kotlinx-coroutines-core` 1.10.1 (Nutzerentscheidung, 2026-10-30), nur in `gradle/libs.versions.toml`; `gradle.lockfile` und `gradle/verification-metadata.xml` von `make deps-lock`. Transitiv hebt der Pin `org.jetbrains:annotations` auf Compile-, Runtime- und Test-Classpath von 13.0 auf 23.0.0 (reine Annotationen; 13.0 bleibt für die Kotlin-Werkzeug-Konfigurationen).

**Zuordnung Test | Festlegung je Aussage.** Fünf Stärken: *gemessen* heißt in diesem Lauf an einem echten Socket auf Loopback gegen eine JDK-Gegenstelle beobachtet (nicht am Spike-Testserver, nicht an einem Microsoft-SQL-Server); *Handvektor* heißt von Hand gebaute Bytes ohne den Paket-Codec (`handPacket`, `handMessage`), also eine Lesart des Paketformats und keine Messung; *Selbstkonsistenz* heißt Code gegen Code, auch wenn der Zähler deterministisch ist; *Festlegung ohne Orakel* heißt Setzung ohne Messung und ohne unabhängigen Vektor; *nicht gemessen* nennt, was keine Aussage trägt.

| Stärke | Aussage | Beleg im Repo |
|---|---|---|
| gemessen (JDK-Loopback) | Byteweise und in Zufallsstücken von 1 bis 299 Bytes (Startwert `20261030`) gesendete Nachrichten kommen vollständig und gleich an; FIN und RST enden den Leser in einem Ergebniswert; der Abbruch einer wartenden Coroutine schließt den Socket; ein Schreiber gegen eine nicht lesende Gegenstelle suspendiert (32 MiB, 500 ms, einseitig: ein langsamer Lauf macht den Test nicht rot) | `SocketChannelLoopbackTest`; Verifikation M06 rot, 13 Läufe ohne Flakiness. Das Suspendieren gilt für diese Maschine (Linux, Loopback), das RST-Verhalten ist betriebssystemabhängig (Review F5, F6) |
| Handvektor | Der Schreiber erzeugt über den Adapter dieselben Bytes wie die von Hand gebauten Pakete; die Nachrichtengrenzen beim Abschneiden an jeder Position der Wire-Folge folgen einem von Hand gebauten Orakel | `SocketChannelLoopbackTest` „Writer schreibt ueber den Adapter die erwarteten Bytes“, `MessageReaderTest`; die Vektoren teilen die Lesart des Paketkopfs (SPEC-028) mit dem Paket-Codec |
| Selbstkonsistenz | Rundlauf Schreiber, Speicher-Kanal, Leser ergibt dieselbe Nachricht; zerstückelte, abgeschnittene und zufällige Eingaben (300 Läufe, fester Startwert) enden in einem Ergebniswert; ein abrupt geschlossener Speicher-Kanal ebenso | `MessageReaderTest`, `MessageWriterTest`, `MemoryChannelTest`; Verifikation M02, M09, M12, M14 rot |
| Selbstkonsistenz (deterministischer Zähler) | Der Leser bricht ab, bevor die Nachricht über der Grenze gepuffert ist: mit Grenze 4096 und Lesepuffer 512 liegt die Zahl der gelesenen Bytes zwischen 4104 und 4104 + 512, nicht bei den gesendeten 204800; der Speicher-Kanal hält bei Kapazität 1024 und einer Nachricht von 100000 Bytes höchstens 1024 Bytes, und der Schreiber suspendiert, solange niemand liest | `MessageReaderTest`, `SocketChannelLoopbackTest` (Zähler `CountingChannel`, Untergrenze seit V-2), `MessageWriterTest`; Verifikation M01, M03, M05, M07b, M08, M16, M22 rot. Die Schranke gilt nur für diese Parametrisierung (Grenze 4096, Puffer 512, Kapazität 1024); für andere Werte steht sie nicht |
| Festlegung ohne Orakel | Alle Kanalfehler tragen die Kategorie `TDS_INTERNAL_ERROR`; SPEC-011 ordnet Kanalfehlern keinen Code zu | KDoc von `ChannelError`; `MemoryChannelTest` (`IO_FAILURE`, `CLOSED`) |
| Festlegung ohne Orakel | Kanal-Garantie: Lese- und Schreibfehler sind Ergebniswerte, Programmierfehler (zweiter gleichzeitiger Vorgang, nicht verbundener Kanal, schreibgeschützter Puffer) bleiben Ausnahmen; `close()` fängt nur `IOException` | KDoc von `ChannelResult` und `SocketChannelAdapter`; `SocketChannelAdapterCloseTest` mit der Attrappe `FailingCloseChannel.java` (Review F2, F3) |
| Festlegung ohne Orakel | „Höchstens ein Lese- und ein Schreibvorgang zugleich“ ist eine Anforderung an den Aufrufer, nicht erzwungen: der Speicher-Kanal verschränkt bei zwei Schreibern die Bytes, bei zwei Lesern kann einer hängen bleiben; ein Test dazu fehlt | KDoc von `SuspendingChannel` (Review F1) |
| Festlegung ohne Orakel | `PacketWriter.split` erzeugt alle Pakete einer Nachricht auf einmal; der Speicher des Schreibers wächst mit der Nachricht, und `maxMessageBytes` prüft der Schreiber nicht | KDoc von `MessageWriter`; kein Test, Träger `slice-backend-streaming-nachweis` (Review F8, V-6) |
| Festlegung ohne Orakel | Die Grenze des Lesers (vor dem Login `FramingLimits.preLogin`, danach Standard) ist ein Konstruktorparameter; ein Wechsel am laufenden Leser ist nicht vorgesehen | KDoc von `MessageReader`; kein Test, Träger `slice-handshake-login` (Review F4) |
| nicht gemessen | Rückstau im Produktivbetrieb und unter Last, 100 MiB wie im Spike, mehr als einige Dutzend Verbindungen; andere Plattformen als Linux auf Loopback; TLS-Kapselung; ein echter SQL Server, TDS 8.0 und Pooling (der Transport läuft nur gegen eine JDK-Gegenstelle); die Prüfsumme des Pins belegt kein Test, Gradle hält sie (M18, M20 rot) | Register unten; Träger `slice-transport-tls-schleife`, `slice-qa-performance`, `slice-qa-kompatibilitaet` |

**Offene Punkte der Closure mit Träger** (gegen den Inhalt der Dateien geprüft):

| Offener Punkt | Träger | Stand |
|---|---|---|
| Wechsel der Rahmen-Grenze beim Login am Leser (Review F4) | `slice-handshake-login` (`open/`) | DoD-Punkt 1 und §6 nennen den Wechsel seit dieser Closure; ein Test mit Folgebytes hinter LOGIN7 ist verlangt |
| Speicher des Nachrichtenschreibers wächst mit der Nachricht (Review F8, V-6) | `slice-backend-streaming-nachweis` (`open/`) | DoD-Punkt 1 und §6 nennen den Schreiber seit dieser Closure; die Zähler-Schranke schließt seinen Speicher ein |
| Rückstau im Produktivbetrieb und unter Last | kein Träger-Slice; Register `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` | offen; `slice-qa-performance` führt Rückstau in seiner DoD nicht (Frage an den Nutzer im Bericht der Closure) |
| Katalog und Lock gegeneinander halten (V-1) | kein Träger-Slice; Register `BEO-TDS/katalog-und-lock-ohne-gegenpruefung` | offen; ein Gate dafür braucht eine Entscheidung |

- **Gegenstand:** entfällt (der Slice lieferte).
- **Steering-Loop-Eintrag:** Kein `liegt in`: mit diesem Slice ist keine Regel, kein Sensor und keine Spec-Zeile verkörpert worden; gezählt wird, nicht verkörpert. Vier Feststellungen. (1) **Ein Schranken-Test braucht den Beweis, dass sein Zähler zählt.** Beide Nachweise der Grenze vor dem Puffern prüften nur eine Obergrenze und blieben grün, als der Zähler nichts zählte (M07a); eine Untergrenze prüft den Zähler mit. Das ist eine Prüffrage für den Reviewer und eine Mutation für den Verifier, kein Sensor; Register: `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler` (neu, 1×). (2) **Katalog und Lock werden von keinem Gate gegeneinander gehalten.** Ein von Hand geänderter Katalog ohne `make deps-lock` bleibt grün (M17); der Pin-Test vergleicht nur den aufgelösten Jar-Namen mit dem Katalogwert. Das ist eine Gate-Lücke, die eine Entscheidung braucht und keinen Slice; Register: `BEO-TDS/katalog-und-lock-ohne-gegenpruefung` (neu, 1×). (3) **Eine Kotlin-Regel wurde durch Java-Quelltext umgangen.** Die Attrappe `FailingCloseChannel.java` ist das erste Java-Quellfile des Repos; sie liegt in Java, weil `AsynchronousSocketChannel.connect` den Typ `Void` verlangt und die detekt-Regel `ForbiddenVoid` ihn in Kotlin verbietet. [AGENTS.md](../../../../AGENTS.md) §3.2 nennt nur `@Suppress` und `@SuppressWarnings` im Kotlin-Quelltext; formal ist nichts verletzt, aber der Zweck der Regel (kein `Void` im Quelltext des Repos) wird in einem Quelltext umgangen, den detekt nicht prüft, ohne Ausnahmeeintrag in `config/detekt/detekt.yml` und ohne ADR oder Carveout. Das ist eine Feststellung, keine Entscheidung dieses Slice; die Frage geht an den Nutzer. Register: `BEO-TDS/java-quelle-umgeht-kotlin-regel` (neu, 1×). (4) **Das Muster der Aussagen über den Beleg hinaus trat trotz der verkörperten Regel wieder auf** (KDoc `ChannelResult`, Pin-Test „belegt den Katalog“, KDoc des RST-Zweigs, `@spec`-Tags an Tests, die das Gate nicht belegen): ein weiteres Auftreten von `BEO-TDS/aussagen-ueber-messung-hinaus`, keine Schärfung; die Regel in [AGENTS.md](../../../../AGENTS.md) §3.8 war der Maßstab von Review und Verifikation.
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-transport-coroutines-geruest.md` ergänzt in `BEO-TDS/aussagen-ueber-messung-hinaus` (Review F2, F5, F10; Verifikation V-1, V-5, V-7). Vier neue Beobachtungen, je mit dieser Evidence-Datei: `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`, `BEO-TDS/katalog-und-lock-ohne-gegenpruefung`, `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler`, `BEO-TDS/java-quelle-umgeht-kotlin-regel`. Kein neues Auftreten: `BEO-TDS/messung-am-echten-sql-server-fehlt` und `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` (der Slice macht keine Aussage über einen echten SQL Server, TDS 8.0 oder Pooling; die Grenze steht in der Zeile *nicht gemessen* oben), `BEO-TDS/test-stage-cache-verdeckt-wiederholung` (die Verifikation fuhr die Stage mit `--no-cache-filter test` und behauptete keine Stabilität aus gecachten Läufen), `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` (`make docs-check` ohne Befund) und die übrigen. Die Zähler folgen aus den Dateien; `aussagen-ueber-messung-hinaus` stand schon über der Schwelle und ist verkörpert, sein `state.md` bleibt unverändert.
- **Risiken aus §6:** alle sieben tragen genau einen Ausgang (siehe §6): sechs entfallen, eines weiter offen im Register.
- **Drei Paarungen:** Anker: kein Eintrag trägt `liegt in`, die Paarung entfällt. Folge-Slice: `slice-handshake-login` und `slice-backend-streaming-nachweis` existieren in `open/` und führen die Punkte in ihrem Inhalt. Register: fünf Evidence-Dateien, siehe oben.

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
- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 2×, offen. Betrifft den Slice nur mittelbar: er macht keine Aussage über einen echten SQL Server.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Betrifft dieses Dokument und die Closure-Notiz: `ADR-*` und `TDS-*` stehen als Link.
- Ohne Treffer: `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/vergleichslauf-d-ungeklaert`; `tds-8-0-und-pooling-nicht-gemessen`, `anmeldepfad-sicherheit-nicht-gebaut` und `adapter-festlegungen-ohne-slice` berühren den Kanal nicht.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
