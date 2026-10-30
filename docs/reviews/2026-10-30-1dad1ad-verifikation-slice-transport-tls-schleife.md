# Verifikationsbericht: slice-transport-tls-schleife am Stand 1dad1ad — 2026-10-30

**Rolle:** Verifier (Frage: „Bauen wir es richtig?“ gegen Plan, DoD und Entscheidungen). Der Bericht repariert nichts und ändert keine Datei außer sich selbst.

**Gegenstand:** [slice-transport-tls-schleife](../plan/planning/done/slice-transport-tls-schleife.md) (Welle welle-transport-tls), Merge `1dad1ad` (Commits `2cd20d3`, `949f144`). Bezug: [TDS-FA-01](../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0001](../plan/adr/0001-tds-version-und-clientprofil.md), [ADR-0002](../plan/adr/0002-tls-und-verschluesselung.md), [ADR-0003](../plan/adr/0003-transportframework.md); Spec-Stellen [SPEC-023](../../spec/spezifikation.md#3-defaults-und-konstanten) und [SPEC-028](../../spec/spezifikation.md#spec-028--prelogin-tls-und-login7-zustands--und-antwortvertrag).

**Stand-Hinweis:** `main` steht inzwischen auf `a8a48f7`. `git diff 1dad1ad HEAD` ist für `src/main/.../transport`, `src/test/.../transport`, `src/test/.../testsupport`, `Dockerfile`, `gradle/` und `gradle.lockfile` leer (Befehl ausgeführt, keine Zeile); alle Läufe unten betreffen deshalb denselben TLS-Code wie `1dad1ad`. Die Läufe im Verlauf der Prüfung liefen auf dem jeweils aktuellen Arbeitsbaum bzw. `git archive HEAD`; geändert haben sich zwischendurch nur `domain/`, `port/`, Planungs- und Review-Dokumente.

**Behauptung ≠ Bestätigung:** Der Slice trägt keine Häkchen und keine Closure-Notiz (§7: „Wird bei der Closure gefüllt“). Belege lagen nur als Aufruf-Behauptung vor (17 rote Mutationen M1–M17, Skript und Ergebnisdatei im Scratchpad); die M-Liste habe ich nicht übernommen, sondern 26 eigene Läufe gefahren (Abschnitt 3), darunter sechs Wiederholungen der Kernfälle von M1–M17.

## 1. Selbst gefahrene Läufe

| Lauf | Ergebnis |
|---|---|
| `make test` (Stage `test`, zuerst aus dem Cache, dann `docker build --target test --no-cache-filter test --progress=plain .`) | grün, `BUILD SUCCESSFUL in 1m 13s`; alle 27 `Tls*`-Tests `PASSED`, kein `FAILED`/`SKIPPED` bei Tests |
| `make gates` | Exit 0, 1 m 52 s: d-check 236 Dateien, 0 Befunde; detekt (Stage `detekt`) grün; `build` grün; Referenztest-Stage grün; `test` frisch (`5 actionable tasks: 5 executed`, 44 s) grün |
| Stabilität: Stage `test` mit Filter `io.github.pt9912.tdswire.transport.tls.*`, **6 Läufe gleichzeitig** (Kopie des Repos, Build-Arg `RUN_ID` trennt den Cache-Schlüssel), Host-Last (Load Average ca. 74) | 6 von 6 grün, je 27 Tls-Tests `PASSED`, je 82–85 s Gesamtlauf; zusammen mit dem Lauf in `make test` also 7 von 7 für `TlsLoopbackTest` (14), `TlsNegativeTest` (11), `TlsTicketFlagTest` (2) |
| Dauer der TLS-Tests | die 27 Tests brauchen im Log rund 8 s Wanduhr (`TlsTicketFlagTest` je ca. 2 s, Kind-JVM), der Testlauf insgesamt 45–73 s je nach Last |

Flakiness: keine beobachtet (0 von 7 Läufen mit einem roten Tls-Test). Die Aussage trägt für diese Last und diese Läufe, nicht darüber hinaus.

## 2. DoD Punkt für Punkt

Der Slice liegt noch in `in-progress/`; die Häkchen stehen offen, §7 ist leer. Bewertet wird, ob der **Inhalt** jedes Punktes getragen ist; die reinen Closure-Pflichten sind eigene Zeilen.

### Liefer-Punkte

| Punkt | Urteil | Beleg |
|---|---|---|
| **SSLEngine-Schleife als Kanal-Dekorierer** ([SPEC-023](../../spec/spezifikation.md#3-defaults-und-konstanten), [SPEC-028](../../spec/spezifikation.md#spec-028--prelogin-tls-und-login7-zustands--und-antwortvertrag), [ADR-0003](../plan/adr/0003-transportframework.md)) | **bestätigt, mit Einschränkung (F1, F2)** | `TlsChannel : SuspendingChannel` umhüllt den inneren Kanal; `TlsHandshake` behandelt `NEED_TASK`/`NEED_WRAP`/`NEED_UNWRAP`; `TlsRecordIo.runTasks` führt die Tasks auf dem Dispatcher aus; Mindestversion in `TlsParameters.init` (Test `parameters below TLS 1_2 are refused`, Mutation R-Mindestversion rot im Lauf des Implementers, hier nicht wiederholt); Fehler als `TlsResult.Failed`/`ChannelResult.Failed` mit `TlsFailure` und Kategorie (`TlsError.kt`); `detail` nennt nur Ausnahmetyp oder feste Beschreibung (Test `assertNoLeak` in `TlsNegativeTest`); Puffer der Entschlüsselung `TlsReader.plain` mit fester Größe (Test `decrypted buffer stays bounded while a mebibyte passes`; Mutation R03 mit 16-MiB-Puffer rot). Eingeschränkt: das Wachsen von `netIn`/`netOut` und die Serverkonfiguration von Suiten und Protokollen sind nicht unterschieden (F1, F2). |
| **TDS-7.4-Pfad** ([SPEC-028](../../spec/spezifikation.md#spec-028--prelogin-tls-und-login7-zustands--und-antwortvertrag), [ADR-0002](../plan/adr/0002-tls-und-verschluesselung.md)) | **bestätigt** | `NetOutbound.flush` verpackt die Sendefolge über `PacketWriter.split(PacketType.PRELOGIN, …)` (Typ `0x12`), `NetInbound` liest über `PacketHeader.parse` und verlangt `PRELOGIN`; Umschalten in `TlsAcceptor.switchToStream` nur, wenn `endPackets()` beider Seiten sauber ist; der Lesepuffer `raw` gehört dem Strom und bleibt beim Umschalten erhalten. Tests: `bytes behind the last handshake packet reach the data phase` (Mutation R01, Lesepuffer verwerfen: rot, genau dieser Test), `server wraps handshake in typed packets and then writes raw records`, `handshake packet reaching past FINISHED is a protocol violation` (R04 rot), `handshake bytes in a packet of the wrong type …` (R06 rot), Paketgröße und Typ (N14: `SQL_BATCH` statt `PRELOGIN` rot in 15 Tests), Zerstückelung byteweise und zufällig mit festem Startwert für alle Suiten in beiden TLS-Versionen. Flag: siehe Abschnitt 4. |
| **TDS-8.0-Pfad** ([SPEC-028](../../spec/spezifikation.md#spec-028--prelogin-tls-und-login7-zustands--und-antwortvertrag), [ADR-0002](../plan/adr/0002-tls-und-verschluesselung.md)) | **bestätigt** | `TlsPath.FROM_FIRST_BYTE`: Engine mit `applicationProtocols = [tds/8.0]`, nach dem Handshake geprüft in `switchToStream` (`ALPN_MISMATCH`); Test `eight zero path starts with a TLS record`, ALPN-Abweisung `eight zero path rejects a peer without the ALPN tds 8_0` (R02, ALPN-Prüfung entfernt: rot), Klartext statt TLS `plaintext instead of TLS …`; ohne Angebot des ALPN (N09) rot in 11 Tests. Gleicher `MessageReader`/`MessageWriter` des Gerüsts in `exchangeMessages`. |
| Gegenstelle: JDK-`SSLEngine`, nicht Referenzclient | **bestätigt** | `JdkTlsPeer` in `TlsTestSupport.kt` baut Client-Engine und eigene Paketbehandlung; Test-KDoc nennt „nicht gegen den Referenzclient“ (`TlsLoopbackTest.kt` Z. 38, `TlsTicketFlagTest.kt` Z. 14, `TlsNegativeTest.kt` Z. 231); zusätzlich zwei Läufe über echte Sockets (`… works over a real socket`). |

### Nachweis-Pflichten

| Punkt | Urteil | Beleg |
|---|---|---|
| `@spec SPEC-<NNN>` im KDoc jedes Tests, Tests laufen in `make test` | **bestätigt** | alle 27 `@Test` tragen den Block-Tag (Zählung: 14 + 11 + 2 Tags gegen 14 + 11 + 2 `@Test`); `make test` grün; die Waisenprüfung läuft in `make gates` mit |
| Loopback je Pfad, beide Richtungen, byteweise und zufällig (fester Startwert), TLS 1.2 und 1.3 je mit Suiten aus SPEC-023 | **bestätigt** | `…byte by byte for every suite` und `…in random pieces for every suite` je Pfad, `Random(seed)` mit festem Startwert in `TlsHarness.kt` |
| Negativfälle enden in einem Ergebniswert | **bestätigt** | abgelaufen (`abgelaufen.p12`, Startdatum fest 2020), falscher Name, TLS 1.1 (handgebautes ClientHello), falscher Pakettyp, abgeschnittener Handshake (N20: EOF als Handshake-Ende rot), Klartext im 8.0-Pfad, abrupter Abbruch (R05, N13 rot); kein Rückfall auf Klartext: `assertClosed` (kein Byte mehr auf dem Draht nach Fehler) und `payload never appears in plaintext on the wire` |
| Zuordnung *Test \| Festlegung* in der Closure-Notiz | **nicht bestätigt (offen)** | §7 leer; KDoc und `contracts/security-profile.md` kennzeichnen die Festlegungen bereits, es gibt keine Aussage „der Referenzclient akzeptiert das“ (Suche ohne Treffer) |
| `make gates` grün | **bestätigt** | Abschnitt 1 |
| Review, Report unter `docs/reviews/` | **bestätigt** | [`2026-10-30-1dad1ad-slice-transport-tls-schleife.md`](2026-10-30-1dad1ad-slice-transport-tls-schleife.md) liegt vor (0 HIGH, 3 MEDIUM, 5 LOW); Häkchen im Slice steht nicht |
| Doku-Update `contracts/security-profile.md` | **bestätigt** | Abschnitt „Festlegungen der TLS-Schleife“ (ALPN-Abweisung, `0x12`-Pflicht, Abbruch ohne `close_notify`, Fehlertexte, KeyUpdate), als nicht am Referenzclient gemessen gekennzeichnet; KeyUpdate-Zeile siehe F5 |
| Closure-Notiz, Lerneintrag, Register, Risiko-Ausgänge, drei Paarungen | **nicht bestätigt (offen, erwartbar vor Closure)** | §7 „Wird bei der Closure gefüllt“; sieben Risiken in §6 tragen „offen bis zur Closure“ |

## 3. Mutationen (eigene Läufe, frische Kopien)

Je Mutation eine Kopie aus `git archive HEAD` im Scratchpad (`…/scratchpad/vf2/mw/<Name>`), eine Zeile geändert, Stage `test` mit Filter auf `transport.tls.*`, Docker-only. Skripte: `…/scratchpad/vf2/vmut.py`, `…/scratchpad/vf2/vmut2.py`, Logs `…/scratchpad/vf2/mw/*.log`.

Rot = ein Test fiel (Nachweis wirksam). Grün = kein Test merkt es (Lücke oder äquivalente Mutante).

| Mutation | Stelle | Ergebnis |
|---|---|---|
| R01 Lesepuffer beim Umschalten verwerfen | `NetInbound.endPackets` | **rot** (`bytes behind the last handshake packet …`) |
| R02 ALPN-Prüfung entfernt | `TlsAcceptor.switchToStream` | **rot** (`eight zero path rejects a peer without the ALPN`) |
| R03 Entschlüsselungspuffer 16 MiB | `TlsReader.plain` | **rot** (`decrypted buffer stays bounded …`) |
| R04 Rest hinter FINISHED erlaubt | `NetInbound.endPackets` | **rot** (`handshake packet reaching past FINISHED …`) |
| R05 Abbruch ohne `close_notify` als Ende | `TlsReader.endOfStream` | **rot** (`abrupt close after the handshake …`) |
| R06 Pakettyp-Prüfung entfernt | `NetInbound.admit` | **rot** (`handshake bytes in a packet of the wrong type …`) |
| N01 „Anwendungsdaten im Handshake“ nicht abgewiesen | `TlsHandshake.afterUnwrap` | **grün** (nicht getestet; KDoc sagt „ungetestet“, ehrlich) |
| N02 `BUFFER_OVERFLOW` in `wrap` ohne Vergrößerung | `TlsRecordIo.wrap` | **grün** |
| N03 `netIn` wächst nie | `TlsRecordIo.pull` | **grün** |
| N04 `adoptSession` leer (kein Wachsen von `netIn`/`netOut`) | `TlsRecordIo.adoptSession` | **grün** |
| N05 letzte Sendefolge bei `FINISHED` nicht geflusht | `TlsHandshake.loop` | **rot** (3 Tests) |
| N06 Rest der Paketnutzdaten nicht gezählt | `NetInbound.movePayload` | **rot** (13 Tests) |
| N07 Paketlänge ohne Header abgezogen | `NetInbound.admit` | **rot** (13 Tests) |
| N08 Abbruch (Coroutine) schließt den Kanal nicht | `TlsChannel.closingOnCancel` | **grün** |
| N09 ALPN nicht angeboten | `TlsAcceptor.serverEngine` | **rot** (11 Tests) |
| N10 ALPN-Prüfung nur auf „irgendeines“ | `TlsAcceptor.switchToStream` | **grün**, äquivalent: der Server bietet nur `tds/8.0`, jede andere Aushandlung scheitert in der Engine |
| N11 Suiten der Konfiguration nicht gesetzt | `TlsAcceptor.serverEngine` | **grün** (F1) |
| N12 Protokolle der Konfiguration nicht gesetzt | `TlsAcceptor.serverEngine` | **grün** (F1) |
| N13 Fehlergrund `CLOSED` und E/A vertauscht | `TlsError.toChannelError` | **rot** (1 Test) |
| N14 Handshake-Pakettyp `SQL_BATCH` statt `0x12` | `NetOutbound.flush` | **rot** (15 Tests) |
| N15 Schreiben auf ausgangsseitig geschlossene Engine kein Fehler | `TlsWriter.afterWrap` | **grün** |
| N16 Antwort-Drain der Leseseite aus (`MAX_DRAIN_ROUNDS = 0`) | `TlsWriter` | **rot** (nur `close_notify ends the stream cleanly …`) |
| N17 Server antwortet nicht auf `close_notify` der Gegenstelle | `TlsReader.afterUnwrap` | **grün** |
| N18 `closeGracefully` schließt den inneren Kanal nicht | `TlsChannel.closeGracefully` | **grün** |
| N19 Tasks ohne den konfigurierten Dispatcher | `TlsRecordIo.runTasks` | **grün**, nicht beobachtbar (äquivalent im Test) |
| N20 Strom-Ende im Handshake als `FINISHED` | `TlsHandshake.pulled` | **rot** (`truncated handshake …`) |

Zusammenfassung: 26 Mutationen, 14 rot, 12 grün; davon sind N10 und N19 äquivalent bzw. nicht beobachtbar, 10 sind echte Lücken im Nachweis (N01–N04, N08, N11, N12, N15, N17, N18; Einordnung in Abschnitt 6). Gegenprobe zum Flag und zum Init-Skript: Abschnitt 4.

## 4. Nutzerentscheidungen

| Entscheidung | Urteil | Beleg |
|---|---|---|
| Testzertifikat per `keytool` in der Stage `testkeys`, kein Schlüssel im Repo | **bestätigt** | `Dockerfile` Stage `testkeys` (`FROM basis`, `keytool -genkeypair …`); `git ls-files` nach `*.p12`, `*.jks`, `*.pem`, `*.key`, `*.pfx`, `*.crt`: keine Treffer |
| PKCS#12 | **bestätigt** | `-storetype PKCS12`; `KeyStore.getInstance("PKCS12")` in `TestKeys`; Dateien `gueltig.p12`, `falscher-name.p12`, `abgelaufen.p12` |
| ALPN-Abweisung | **bestätigt** | Abschnitt 2, TDS-8.0-Pfad; als Festlegung in KDoc und Sicherheitsprofil |
| Flag `-Djdk.tls.server.newSessionTicketCount=0` in der Test-JVM wirksam | **bestätigt** | `gradle/test-jvm.init.gradle.kts` (`jvmArgs`), Test `test JVM sets the session ticket flag`. Kind-JVM-Test `without the session ticket flag …`: mit Flag `exchange_ok`, ohne `exchange_failed` (der Test verlangt beides) |
| Gegenprobe: Init-Skript ändern, `make test` rot | **bestätigt** | Kopie mit gelöschter `jvmArgs`-Zeile: `docker build --target test` Exit 1, 8 Tests rot (u. a. `test JVM sets the session ticket flag`, `encapsulated path works over a real socket`). Kopie mit Wert `1` statt `0`: Exit 1, dieselben 8 Tests rot. Anmerkung: der Kind-JVM-Test selbst bleibt dabei grün (er setzt das Flag in der Kind-JVM selbst); die rote Wirkung kommt vom Test der Test-JVM und von den TLS-1.3-Tests im 7.4-Pfad |
| Mit Flag geht es, ohne scheitert das Lesen | **bestätigt für diese Gegenstelle** | siehe Kind-JVM-Test; die Aussage trägt für JDK-Gegenstelle im selben Prozess, ohne Messung am Referenzclient (so im Test-KDoc gekennzeichnet) |

## 5. Reproduzierbarkeit und Layer-Cache

* `docker build --target deps --progress=plain .`: alle `deps`-Schritte `CACHED` (WORKDIR, beide COPY, `gradle … abhaengigkeitenLaden`). Die Eingänge der Stage `deps` sind gegen `e2510c9` unverändert: `git diff e2510c9 HEAD` für `gradle.lockfile`, `settings-gradle.lockfile`, `gradle/verification-metadata.xml`, `gradle/libs.versions.toml`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` ist leer; keine neue Abhängigkeit (`transport/tls` importiert nur `transport.channel`, `transport.framing`, JDK, Coroutinen).
* Zweimal `docker build --target testkeys --no-cache-filter testkeys --output type=local,…`: beide Läufe grün, die `.p12`-Dateien unterscheiden sich byteweise (zufällige Schlüssel; erwartet, kein Test hängt an Seriennummer oder Schlüssel). Zeitabhängigkeit: `gueltig.p12` und `falscher-name.p12` laufen ab Bauzeit 3650 Tage (im Lauf: gültig 2026-10-30 bis 2036-10-27); `abgelaufen.p12` hat ein fest konstruiertes Blatt (`-startdate 2020/01/01`, 30 Tage, abgelaufen 2020-01-31) und ist nicht zeitabhängig; die Test-CA im Speicher ist ab Bauzeit gültig. Folge: ein Layer, der zehn Jahre im Cache bleibt, würde die gültigen Zertifikate ungültig machen; das ist praktisch nur bei einem uralten Cache relevant.
* Auswirkung auf `deps`: keine. Die Stage `testkeys` hängt an `basis`, nicht an `deps`; `test` kopiert `build/testkeys` und das Init-Skript erst nach `deps`. `deps` blieb im Lauf mit neu gebauter `testkeys`-Stage `CACHED`.
* Der Cache der Stage `test` wird bei jedem Neubau von `testkeys` ungültig (neue Dateien); das ist gewollt und kostet nur den Testlauf.

## 6. Out-of-Scope, Schichten, Aussagen

* Out-of-Scope eingehalten: kein Listener, kein `LOGIN_TIMEOUT`, kein Handshake-Automat (kein Treffer für ServerSocket/Listener/`ENCRYPT_*` in `transport/tls`; `AsynchronousServerSocketChannel` kommt nur im Test `overSockets`), kein Referenzclient, keine Nachrichteninhalte. Der Pfad wird als Parameter (`TlsPath`) übergeben, nicht entschieden.
* Schichtgrenzen: `make detekt` grün; `config/detekt/detekt.yml` unverändert; Imports aus `transport.channel`, `transport.framing` und JDK/Coroutinen. `@Suppress` nicht vorhanden (Suppression-Prüfung Teil der Stage `detekt`, grün).
* [AGENTS.md 3.8](../../AGENTS.md): KDoc, Sicherheitsprofil und Slice kennzeichnen Festlegungen durchgehend (4096 Packet Size „Festlegung ohne Messung“, ALPN-Abweisung, Fehlerkategorien „folgt keiner Zeile der Spezifikation“, Anwendungsdaten im Handshake „ungetestet“); eine Aussage über den Referenzclient oder SQL Server steht nicht. Ausnahme: F5 (KeyUpdate).
* Dokumentierte Grenzen im Slice §6 (KeyUpdate in TLS 1.3 nicht umfassend, Flag prozessweit) stehen noch ohne Ausgang.

## 7. Findings

### HIGH

Keine.

### MEDIUM

* **F1 (M, zugleich Review M3), Suiten und Protokolle der Serverkonfiguration sind nicht belegt.** Mutationen N11 und N12 (Suiten bzw. Protokolle nicht an die Engine gegeben) bleiben grün. Der Test `TLS 1_1 hello is rejected in both paths` wird vom JDK-Default getragen (TLS 1.1 ist im JDK gesperrt), und die Positivtests geben dieselbe Konfiguration an Server und Gegenstelle. Es gibt keinen Test, in dem eine Gegenstelle nur eine Suite außerhalb von [SPEC-023](../../spec/spezifikation.md#3-defaults-und-konstanten) (z. B. CBC) oder nur TLS 1.2 bzw. 1.3 gegen einen auf die andere Version beschränkten Server anbietet. Betrifft [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit) und die DoD-Aussage „Suiten nach SPEC-023“. Empfehlung: Negativtest mit Gegenstelle ohne Schnittmenge.
* **F2, Wachsen von `netIn`/`netOut` und `BUFFER_OVERFLOW` sind ungetestet.** N02, N03 und N04 bleiben grün. Die KDoc-Zusage „kein Puffer wächst darüber hinaus, ein Record, der nicht hineinpasst, ist ein Fehler“ ([AGENTS.md 3.8](../../AGENTS.md)) hat nur für den Entschlüsselungspuffer einen Test. Mit der JDK-Engine ist der Zweig voraussichtlich nicht erreichbar (die Sitzung nennt schon vor dem Handshake ihre Höchstgröße); dann ist er toter Code oder braucht eine Gegenstelle mit überlangem Record. Empfehlung: Test mit überlangem Record oder den Zweig als nicht erreichbar kennzeichnen.

### LOW

* **F3, Resourcen-Verhalten ohne Beleg.** N08 (Abbruch schließt den inneren Kanal nicht) und N18 (`closeGracefully` schließt den inneren Kanal nicht) bleiben grün, obwohl KDoc beides zusagt („schließt den inneren Kanal“, `closingOnCancel`). Kein Test beobachtet das Schließen nach Abbruch oder nach `closeGracefully`.
* **F4, Antwort auf `close_notify` der Gegenstelle und Schreiben nach Schließen.** N17 (keine Antwort-`close_notify`) und N15 (Schreiben auf ausgangsseitig geschlossene Engine ohne Fehler) bleiben grün; der Test `close_notify ends the stream cleanly …` prüft nur das gelesene Ende (-1), nicht, dass die Gegenseite eine `close_notify` zurückbekommt.
* **F5, KeyUpdate-Test beobachtet das Beantworten nicht.** N16 (Drain der Leseseite aus) lässt `key update requested by the peer …` grün; die Antwort wird beim nächsten Schreiben von `writeLocked` nachgeholt. Die Zeile im Sicherheitsprofil („beantwortet die Schleife“) trägt nur „Nachrichten laufen weiter“. Deckt sich mit Review M2.
* **F6, `TlsHandshake`-Zweig „Anwendungsdaten im Handshake“ (N01) ungetestet.** KDoc benennt das selbst; als LOW, solange die Kennzeichnung bleibt.
* **F7, tote Konstante `WRAP_ATTEMPTS`** in `TlsRecordIo` (Review L1, bestätigt: im Quelltext nicht gelesen).
* **F8, Timeout im Kind-JVM-Test wirkungslos** (`readText()` vor `waitFor`, Review L5): bei einer hängenden Kind-JVM blockiert der Testlauf, bis die Kind-JVM selbst endet (internes `withTimeout` 20 s begrenzt es auf die Probe, nicht auf einen Hänger vor `main`). Im Lauf nicht aufgetreten.
* **F9, Stabilitätsbeleg ist begrenzt:** 7 von 7 Läufen unter Last; kein Beleg für andere Plattformen oder JDKs (Stage fest auf einem Image).
* **F10, Zertifikate laufen 3650 Tage ab Bauzeit** (Abschnitt 5); nur relevant bei sehr altem Layer-Cache.

## 8. Rückfragen und Rückwärtspfad

* Die Closure-Notiz sollte F1–F5 in der Zuordnung *Test \| Festlegung* als „Festlegung, nicht belegt“ führen oder je einen Test nachziehen.
* Benannte Spec-Lücke (Kandidat für den Lerneintrag): Mutationen der Konfiguration (F1) zeigen, dass der Sensor „Positivtest mit gleicher Konfiguration beider Seiten“ die Server-Konfiguration nicht unterscheidet; ein Negativtest ohne Schnittmenge ist der fehlende Sensor.
* Nicht geprüft: Verhalten am Referenzclient und an einem echten SQL Server (per Slice-Grenze ausgeschlossen), andere JDK-Versionen, Läufe auf anderer Hardware, die 17 Mutationen M1–M17 des Implementers einzeln (sechs Kernfälle als R01–R06 wiederholt).
