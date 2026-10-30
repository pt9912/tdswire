# Verifikationsbericht: slice-handshake-login am Stand bda6cd6 — 2026-10-30

**Rolle:** Verifier (Frage: „Bauen wir es richtig?“ gegen Plan, DoD und Entscheidungen). Der Bericht repariert nichts und ändert keine Datei außer sich selbst; es wurde nichts committet oder gepusht.

**Gegenstand:** [slice-handshake-login](../plan/planning/done/slice-handshake-login.md) (Welle welle-handshake-login), Merge `bda6cd6` („Merge slice-handshake-login Umsetzung“) mit den Commits `371d583`, `e2bdeb5`, `c5eb023`, `6aa8d3f`, `7fb950d`. Bezug: [TDS-FA-01](../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-FA-02](../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0001](../plan/adr/0001-tds-version-und-clientprofil.md), [ADR-0002](../plan/adr/0002-tls-und-verschluesselung.md), [ADR-0004](../plan/adr/0004-session-zustandsautomat.md), [ADR-0007](../plan/adr/0007-fehler-und-attention.md), [ADR-0009](../plan/adr/0009-authentifizierungsmodus.md).

**Stand-Hinweis:** `main` steht inzwischen auf `cbdbd80`; seit `bda6cd6` änderte sich unter `src/`, `contracts/`, `gradle/`, `gradle.lockfile` und `build.gradle.kts` nichts (`git diff --stat bda6cd6 HEAD` über diese Pfade ist leer). Alle Läufe betreffen daher den Code von `bda6cd6`. Mutationen, Basislauf und Lastläufe liefen in frischen Kopien im Scratchpad (`/tmp/claude-1000/-Development-tdswire/8562f454-84ba-456b-9970-945e69c035f4/scratchpad/mut/`, `.../probe/`, `.../load1` bis `.../load4`, `.../base/`); der Arbeitsbaum des Repos blieb unverändert (`git status`: nur fremde, unverfolgte Dateien `.claude/worktrees/` und ein später von anderer Hand abgelegter Report).

**Behauptung ≠ Bestätigung:** Der Slice trägt keine DoD-Häkchen und keine Closure-Notiz (§7: „Wird bei der Closure gefüllt“). Die Behauptungen des Implementers (Tests grün, `make gates` grün, 17 rot gesehene Zusagen) liegen nur im Auftrag. Alles unten ist selbst gefahren oder aus dem Code gelesen; wo ich nur gelesen habe, steht es dabei.

## 1. Selbst gefahrene Läufe

| Lauf | Ergebnis |
|---|---|
| `make test` (Rohlauf) | Exit 0 in 1,7 s, **aber nur Cache-Treffer** (`RUN gradle ... test` als `CACHED`): `make test` allein belegt keinen frischen Lauf. Deshalb alle Testläufe unten als `docker build --target test --no-cache-filter test .` (dasselbe Ziel, Stage `test` erzwungen) |
| frischer Testlauf `bda6cd6` | Exit 0, **280 Tests PASSED, 0 FAILED**, `BUILD SUCCESSFUL in 46s` |
| Basis `08e777f` (erster Elternteil des Merge) | 231 PASSED; Zuwachs **49** = 7 + 10 + 5 + 9 + 13 + 5 `@Test` in den sechs Testklassen von `protocol/session` |
| `make gates` | Exit 0 in 6,8 s (überwiegend Cache): `baseline-verify: v6.18.1 OK — 54 Dateien`, `d-check: 243 Datei(en) geprüft, 0 Befund(e)`, Image-Bau, Referenztests, Test-Stage |
| `docker build --target detekt --no-cache-filter detekt` | Exit 0 ohne Cache: `tools/suppression-check.sh src`, `detekt`, `detektMain`, `detektTest`, `BUILD SUCCESSFUL in 1m 19s` |
| Abhängigkeiten | `git diff ca68167 HEAD` über `gradle.lockfile`, `gradle/verification-metadata.xml`, `build.gradle.kts`, `settings-gradle.lockfile`, `gradle/libs.versions.toml`: **leer**, keine neue Abhängigkeit |
| ADRs | `git diff ca68167 HEAD --stat -- docs/plan/adr`: leer; die Accepted-ADRs sind nicht überschrieben (`AGENTS.md` §3.5) |

**Laufzeitzuwachs von `make test`:** Der Basislauf `08e777f` brauchte 42 s und 69 s (zwei Läufe), `bda6cd6` 41 bis 57 s (fünf Läufe) und 46 s (erster Lauf). Der Zuwachs durch 49 Tests liegt unter der Streuung der Läufe; die drei Wanduhr-Tests tragen zusammen rund 0,9 s (200 ms, 300 ms, 400 ms Frist bzw. Blockierzeit). Unter vierfacher Parallellast stieg die Laufzeit auf 85 bis 108 s (Last, nicht Zuwachs).

## 2. DoD Punkt für Punkt

### Liefer-Punkt 1 — Zustandsautomat und PRELOGIN

| Aussage | Urteil | Beleg |
|---|---|---|
| Explizite Übergangsmatrix für `CONNECTED`, `PRELOGIN`, `TLS_NEGOTIATION`, `LOGIN7`, `READY`, `CLOSED` | **bestätigt, mit Ergänzung** | `HandshakeMatrix.cell(...)` über `ConnectionState` (7 Werte): zusätzlich `AUTHENTICATING` (Zustand während des Adapteraufrufs, im KDoc begründet); flach, KDoc nennt den Zuschnitt als Festlegung ohne Messung |
| beide Reihenfolgen (TDS 7.x PRELOGIN vor TLS, TDS 8.0 TLS zuerst) | **bestätigt** | `HandshakeOrder`; Test „Jede Zelle der Matrix entspricht der Handtabelle in beiden Reihenfolgen“ prüft 2 × 84 Zellen (Folgezustand und Aktionsfolge) gegen eine von Hand geschriebene Tabelle; Treibertests „Annahme in der Reihenfolge PRELOGIN vor TLS“ (Spur `send, tls, send`) und „… TLS vor PRELOGIN“ (Spur `tls, send, send`) |
| Zählung 7 × 12 = 84 (25/4/55) | **bestätigt** | Von mir von Hand nachgerechnet, ohne den Test zu lesen: zulässig = 17 (drei „Ende“-Ereignisse in sechs Zuständen, ohne `LOGIN_TIMEOUT` in `READY`) + 8 (je Zustand spezifisch: 1, 1, 1, 1, 2, 2, 0) = **25**; ignoriert = 3 (in `CLOSED`) + 1 (`LOGIN_TIMEOUT` in `READY`) = **4**; unzulässig = 84 − 29 = **55**. Der Test „Die Matrix zaehlt ihre Zellen aus dem Code“ liest `HandshakeMatrix.census()` und stimmt überein |
| Abweichung zur Spike-Zahl 140 ([ADR-0004](../plan/adr/0004-session-zustandsautomat.md): „Zählung nicht belegt“) | **bestätigt, Closure-Anteil offen** | Die Zahl 140 wird nirgends übernommen; das KDoc des Tests nennt die Abweichung (10 × 14 gegen 7 × 12, eigenes Raster). [ADR-0004](../plan/adr/0004-session-zustandsautomat.md) bleibt unverändert (Accepted). Die Closure-Notiz mit der Abweichung existiert noch nicht (siehe Abschnitt 6) |
| je ein Test pro zulässigem und unzulässigem Übergang (LOGIN7 vor PRELOGIN, zweites PRELOGIN, SQL_BATCH vor `READY`, Nachricht nach `CLOSED`) | **bestätigt** | Je ein benannter Test in `HandshakeMatrixTest` plus die Handtabelle über alle Zellen |
| unzulässiger Übergang beendet kontrolliert, ohne eine zweite Instanz zu berühren | **bestätigt** | Tests „… berührt den Kontext einer zweiten Verbindung nicht“ (Wertvergleich) und „Ein Verstoss auf einer Verbindung beruehrt die andere nicht“ (zwei Verbindungen, ein Treiber) |
| PRELOGIN-Antwort aus der Richtlinie: `REQUIRED` antwortet ENCRYPT_REQ (Festlegung), ENCRYPT_NOT_SUP nur im Testmodus, Unvereinbarkeit bricht vor LOGIN7 ab, kein stilles Downgrade ([ADR-0002](../plan/adr/0002-tls-und-verschluesselung.md)) | **bestätigt** | Handvektor `00 00 15 00 06 01 00 1B 00 01 02 00 1C 00 01 04 00 1D 00 01 FF 00 00 00 00 00 00 03 00 00` (Tabelle mit Offsets ab Nachrichtenanfang, VERSION `0.0.0.0`, ENCRYPT_REQ `03`, leerer INSTOPT, MARS aus): von mir gegen das Tabellenlayout nachgerechnet (Eintrag 4 × 5 Byte + Terminator = 21 = Offset von VERSION). Mutationen M12 bis M14, M24, M31 rot (Abschnitt 4) |
| Nachricht über `MAX_PRELOGIN_MESSAGE_BYTES` abgewiesen | **bestätigt** | Unit-Test (Grenze ± 1) und Drahttest „Zu grosses PRELOGIN“; M12 rot |
| Fristablauf `LOGIN_TIMEOUT` als Ereignis endet im Schließen ohne Antwort | **bestätigt** | Test „Fristablauf schliesst ohne Antwort“ (nur die PRELOGIN-Antwort gesendet, Kanal geschlossen); M11 rot |
| [ADR-0004](../plan/adr/0004-session-zustandsautomat.md)-Punkt (1): Zuschnitt flach, als Festlegung im KDoc | **bestätigt** | KDoc von `ConnectionState`: „Festlegung ohne Messung: eine flache Aufzählung wie im Spike; [ADR-0004](../plan/adr/0004-session-zustandsautomat.md) führt den Zuschnitt als offen“ |
| [ADR-0004](../plan/adr/0004-session-zustandsautomat.md)-Punkt (2): Antwort auf unzulässigen Übergang als Festlegung gekennzeichnet und durch Tests gepinnt | **bestätigt** | KDoc von `HandshakeMatrix` und `HandshakeAutomaton`; `assertClosedSilently` in fünf Tests pinnt „`Close(PROTOCOL_VIOLATION)`, kein `Send`“ |
| [ADR-0004](../plan/adr/0004-session-zustandsautomat.md)-Punkt (3): Matrixzählung aus dem Code | **bestätigt** (siehe oben) | |
| Wechsel der Rahmen-Grenze verliert keine Folgebytes; Test mit Folgebytes hinter LOGIN7; Gegenprobe ohne Wechsel scheitert an `MESSAGE_ABOVE_LIMIT` | **bestätigt** | `MessageReader.useLimits` setzt nur `assembler.limits`; Test „Folgebytes hinter LOGIN7 …“ (kleine Nachricht plus erstes Paket einer 100 000-Byte-Nachricht hinter LOGIN7; nach der Annahme liefert derselbe Leser beide); Test „Ohne Wechsel scheitert …“ erwartet `FramingFailure.MESSAGE_ABOVE_LIMIT`. M09 (Leser verwirft fertige Nachrichten) und M20 (Wechsel entfernt) rot |

### Liefer-Punkt 2 — Login-Antworten

| Aussage | Urteil | Beleg |
|---|---|---|
| Ablehnung als Handvektor: ERROR 18456, State 1, Class 14, feste Meldung; DONE mit Fehlerbit | **bestätigt** | Test „Die Ablehnung ist ein ERROR 18456 mit DONE als Handvektor“: `AA 28 00 | 18 48 00 00 | 01 | 0E | 0D 00 | "Login failed." (UTF-16LE) | 00 | 00 | 00 00 00 00 | FD 02 00 ...`. Von mir nachgerechnet: Token-Länge `0x28` = 4 + 1 + 1 + 2 + 26 + 1 + 1 + 4 = 40; 18456 = `0x4818` little-endian; DONE-Status `0x0002` ist das Fehlerbit; Gesamtlänge 56 Byte, auf dem Draht mit 8 Byte Paketkopf = 64 |
| Annahme: ENVCHANGE Datenbank, Packet Size, Collation, LOGINACK, DONE | **bestätigt, Handvektor nur teilweise** | Test „Die Annahme sendet ENVCHANGE, LOGINACK und DONE“ vergleicht gegen den Test-Decoder (Token-Objekte, kein Hex); als Hex steht nur der Schwanz LOGINACK + DONE (`AD 18 00 01 74 00 00 04 07 "tdswire" 01 00 00 00 FD 00 ...`, nachgerechnet: Länge 24 = 1 + 4 + 1 + 14 + 4). Die ENVCHANGE-Bytes sind im Repo nicht als Hex gepinnt (Finding L-3). Ich habe die Antwort für Datenbank `d`, Packet Size 8000 in einem Wegwerf-Test ausgeben lassen und mit einer vorher von Hand gerechneten Fassung verglichen: `E3 05 00 01 01 64 00 00 | E3 13 00 04 04 "8000" 04 "4096" | E3 08 00 07 05 09 04 D0 00 34 00 | AD ... | FD ...` — identisch (Typ 1 Datenbank, Typ 4 Packet Size als Zeichenkette, Typ 7 SQL-Collation `0x00D00409` + Sort-ID `0x34`) |
| Sitzung mit ID, Principal, Datenbankkontext | **bestätigt** | Test „Die Annahme erzeugt die Sitzung und wechselt die Grenze“; `AuthenticatedSession` trägt auch die Packet Size, Textdarstellung „verborgen“ |
| Ablehnung beendet die Verbindung; genau ein ERROR | **bestätigt** | Treiber-/Drahttests; M05 (Close nach Ablehnung entfernt) rot |
| Ausnahme im Adapter wird wie `Rejected` beantwortet und intern geloggt (nur Typ) | **bestätigt, mit Rest** | Test „Eine Ausnahme im Adapter …“; M07 (als `Accepted`) und M29 (Ausnahmetext ins Log) rot. Rest: `CancellationException` des Adapters wird nicht so beantwortet (Finding M-1) |
| Byte-Gleichheit unbekannte Kennung / falsches Passwort | **bestätigt** | im Treiber (echte `UserListAuthAdapter`, `assertContentEquals` der Nutzlasten und Vergleich mit `LoginResponses.rejection`) **und** auf dem Draht (Test „Ablehnungen sind auf dem Draht byte-gleich“: gesamte Ausgabe bis zum Ende, Paketkopf `04 01 00 40 00 00 01 00` + festes Muster). M04 (Grund als Anhang) und M23 (grundabhängiger State) rot in beiden Ebenen |
| Passwort und Kennung in keiner Antwort, keinem Log, keinem Grund | **bestätigt** | Treibertest sucht Kennungen und Passwörter in der Antwort (UTF-16LE und Bytes) und in `toString` aller Log-Einträge; Ablehnungsgrund ist eine Aufzählung; Textdarstellungen von Ereignissen/Zuständen „verborgen“ (Lesen des Codes) |
| Passwortfeld nach dem Adapteraufruf überschrieben, auch das Zwischenfeld der Abbildung | **bestätigt** | Tests: `passwordChars` nach Annahme und nach Ausnahme nur `\u0000`; „Die Abbildung ueberschreibt Quellfeld und Zwischenfeld“. M08 (Wipe entfernt) und M22 (Zwischenfeld nicht überschrieben) rot |
| Adapteraufruf nicht auf dem Transport-Thread | **bestätigt** | Test mit Ticker (≥ 10 Ticks in 400 ms) und Threadvergleich; M10 (`Dispatchers.Unconfined`) rot in beiden zugehörigen Tests |
| `LOGIN_TIMEOUT` mit langsamem Adapter | **bestätigt** | Test: Adapter blockiert 3 s, Frist 300 ms, Wartezeit < 1,5 s; M11 rot. Der blockierende Aufruf läuft nach der Frist zu Ende (KDoc nennt es); keine Obergrenze (Finding M-2) |
| Mehrpaketiges, byteweise zerstückeltes LOGIN7 → Anmeldung | **bestätigt** | Test „Mehrpaketiges und byteweise zerstueckeltes LOGIN7 …“: 20 Nutzdatenbytes je Paket (≥ 4 Pakete), jedes Byte einzeln gesendet, über Leser, Schreiber und Automat bis `Ready`. Vektor von Hand abgeleitet, als Festlegung gekennzeichnet; keine Aussage, ein Client sende so |

### Liefer-Punkt 3 — Nicht unterstützte Modi und ungültige Felder

| Aussage | Urteil | Beleg |
|---|---|---|
| `fIntSecurity`, FeatureExt `0x02`, `fChangePassword` ergeben dasselbe Ablehnungsmuster, Adapter null Aufrufe | **bestätigt** | Treibertest „Nicht unterstuetzte Modi …“ (`adapter.calls == 0`, Nutzlast = `LoginResponses.rejection`, Log trägt je den Grund); Admission-Tests je Modus. M15, M16, M17 rot; Gegenprobe „Eine andere FeatureExt-Option wird nicht abgelehnt“ |
| 128/129-Grenze je Feld | **bestätigt** | Kennung (M19), Passwort (M18) und Datenbankname (M06) je 128 zulässig, 129 abgelehnt; alle drei Mutationen rot |
| ungültige Offsets und Längen → kontrollierte Ablehnung ohne Absturz | **bestätigt mit Abweichung vom Wortlaut** | Der Code antwortet nicht mit dem Ablehnungsmuster, sondern schließt ohne Antwort (`Admission.Malformed` → `PROTOCOL_VIOLATION`); das KDoc nennt es Festlegung, und der Treibertest heißt „schliessen kontrolliert ohne Antwort“. Test über jedes Präfix eines gültigen LOGIN7 und 500 Zufallsfolgen (fester Startwert): nie eine Ausnahme. DoD sagt „Ablehnung“; der Wortlaut ist eng gelesen nicht erfüllt, die Absicht (kein Absturz, kontrolliertes Ende) schon (Finding L-5) |

### Nachweis-Pflichten

| Pflicht | Urteil | Beleg |
|---|---|---|
| `@spec SPEC-<NNN>` an jedem der 49 neuen Tests | **bestätigt** | Skript über die sechs Testdateien: 49 `@Test`, bei jedem steht im KDoc direkt darüber mindestens ein `@spec`-Tag (SPEC-001, -008, -010, -012, -028, -046, -048, -007); 280 Tests laufen grün |
| Jede Aussage an einem Test oder als Festlegung gekennzeichnet | **bestätigt für Code und Vertrag; Zuordnungstabelle offen** | Stichprobe aller KDoc der 15 neuen Hauptdateien auf Wörter wie „gemessen“, „genügt“, „akzeptiert“: gemessen werden nur die drei erlaubten Punkte genannt (LOGINACK mit DONE genügt beim `Open`, Collation-ENVCHANGE für RPC, genau ein ERROR mit DONE), alles andere trägt „Festlegung ohne Messung“; der Satz „der Client akzeptiert das“ steht nirgends. Die Messaussage zu ENCRYPT_REQ/ENCRYPT_ON im KDoc von `PreloginNegotiation` deckt [ADR-0002](../plan/adr/0002-tls-und-verschluesselung.md) (Zeilen „Antwortet der Server ENCRYPT_REQ …“ und „ENCRYPT_ON auf einen Client mit `Encrypt=false` scheiterte“). Die Zuordnung *Test | Festlegung* je Aussage gehört in die Closure-Notiz, die fehlt noch |
| `make gates` grün | **bestätigt** | Abschnitt 1 |
| Review durchgeführt, Report unter `docs/reviews/` | **bestätigt** | `docs/reviews/2026-10-30-bda6cd6-slice-handshake-login.md` (0 HIGH, 3 MEDIUM, 3 LOW, 7 INFO). Die Reviewer-Findings F-3 und F-5 habe ich unabhängig nachgestellt (M-1 unten, M01/M02) |
| Doku-Update, wo ein öffentlicher Vertrag berührt ist | **bestätigt** | `contracts/client-profile.md`: neuer Abschnitt „Login-Antwort des Servers (Festlegung)“ mit der Tabelle PRELOGIN-Antwort/Annahme/Ablehnung/Datenbank und Packet Size; der Abschnitt nennt „Festlegung ohne Messung am Produkt“ und die drei gemessenen Punkte am Spike-Testserver, keine Zusage über die Annahme durch den Referenzclient |
| Closure-Notiz mit Lerneintrag; Beobachtungs-Register fortgeschrieben; jedes Risiko aus §6 mit Ausgang; drei Paarungen | **nicht bestätigt (noch nicht ausgeführt)** | §7 lautet „Wird bei der Closure gefüllt“, alle Risiken in §6 stehen auf „offen bis zur Closure“. Das ist der Zustand vor der Closure, kein Mangel des Codes; die Closure kann erst nach diesem Bericht erfolgen |

## 3. Schichten, Out-of-Scope, Abhängigkeiten

| Prüfung | Urteil | Beleg |
|---|---|---|
| `protocol/session` importiert aus `transport` nur Wertetypen von `transport.framing` | **bestätigt, Slice-Text veraltet** | Import-Liste der 15 Dateien: `TdsMessage`, `PacketType`, `FramingLimits`, **`PacketHeader`** (`LoginResponses.kt`), nichts aus `transport.channel`, `transport.messaging`, `transport.tls`, `simulation`, `runtime`. Slice §3 nennt nur `TdsMessage` und `PacketType` (Finding L-1). `make detekt` (Importregeln der Schichten, `ForbiddenMethodCall`) grün |
| `transport.messaging.TransportHandshakeChannel` importiert die Protocol-Schnittstelle | **bestätigt** | Richtung transport → protocol, wie Slice §3 festhält; detekt grün |
| Out-of-Scope: kein Listener, kein TLS-Code, keine Anfrageverarbeitung, keine Drosselung, kein Dateilesen | **bestätigt** | Suche in `protocol/session` und `TransportHandshakeChannel.kt` nach `ServerSocket`, `SSLEngine`, `SSLContext`, `java.io.File`, `java.nio.file`, `Files.`, `readText`, `readBytes`, `Thread.sleep`, `runBlocking`, `GlobalScope`: kein Treffer. TLS ist die Schnittstelle `startTls()` mit Lambda des Aufrufers; `READY`-Nachrichten gehen nur als Aktion `Forward` hinaus |
| keine neue Abhängigkeit | **bestätigt** | Abschnitt 1 |
| keine Unterdrückung (`AGENTS.md` §3.2) | **bestätigt** | `tools/suppression-check.sh src` im detekt-Lauf grün |

## 4. Selbst ausgeführte Mutationen

Jede Mutation lief in einer frischen Kopie des Arbeitsbaums von `bda6cd6` (`/tmp/claude-1000/-Development-tdswire/8562f454-84ba-456b-9970-945e69c035f4/scratchpad/mut/`, vor jeder Mutation per `rsync --delete` auf `src/` zurückgesetzt), mit `docker build --target test --no-cache-filter test`. Keine Mutation scheiterte am Kompilieren; „rot“ heißt: Tests liefen und mindestens einer schlug fehl. Von 32 Mutationen waren **29 rot und 3 überlebten**. Die Logs liegen im Scratchpad als `m01-….log` bis `m32-….log`.

| Nr. | Mutation | Ergebnis | rote Tests |
|---|---|---|---|
| M01 | zweites PRELOGIN im Zustand `LOGIN7` zulässig | **rot, nur Zählung** | „Die Matrix zaehlt …“ |
| M02 | ATTENTION im Zustand `LOGIN7` zulässig | **rot, nur Zählung** | „Die Matrix zaehlt …“ |
| M03 | `AUTHENTICATING` → zweites LOGIN7 zulässig | rot | Zählung; Handtabelle |
| M04 | Ablehnung trägt den Grund (Anhangbyte) | rot | Ausnahme-Test, Modi-Test, Byte-Gleichheit (Treiber), Draht |
| M05 | Close-Aktion nach Ablehnung entfernt | rot | vier Treiber-/Drahttests, Handtabelle |
| M06 | Datenbankname-Grenze 129 statt 128 | rot | „Ein Datenbankname ueber 128 …“ |
| M07 | Adapter-Ausnahme als `Accepted` | rot | „Eine Ausnahme im Adapter …“ |
| M08 | Passwort-Überschreiben (Wipe) im `AuthExecutor` entfernt | rot | „Annahme … PRELOGIN vor TLS“, „Eine Ausnahme im Adapter …“ |
| M09 | `useLimits` leert fertige Nachrichten | rot | „Folgebytes hinter LOGIN7 …“ |
| M10 | Dispatcher `Unconfined` | rot | „Der Adapter blockiert den Transport-Thread nicht“, „Ein langsamer Adapter …“ |
| M11 | Frist (`withTimeoutOrNull`) entfernt | rot | „Fristablauf …“, „Ein langsamer Adapter …“ |
| M12 | PRELOGIN-Grenze um 1 großzügiger | rot | „PRELOGIN ueber der Vor-Login-Grenze …“ |
| M13 | TLS-Fehler ignoriert | rot | „Ende, Lesefehler, TLS-Fehler und Schreibfehler …“ |
| M14 | `REQUIRED` nimmt `ENCRYPT_NOT_SUP` an | rot | „Unvereinbare Verschluesselung …“, „REQUIRED bricht …“ |
| M15 | `fIntSecurity` ignoriert | rot | Modi-Test, „fIntSecurity wird abgelehnt“ |
| M16 | `fChangePassword` ignoriert | rot | Modi-Test, „fChangePassword …“, „Die Passwoerter des LOGIN7 …“ |
| M17 | FeatureExt `0x02` ignoriert | rot | Modi-Test, „FeatureExt 0x02 …“ |
| M18 | Passwortgrenze 129 | rot | „Eingaben ueber 128 Einheiten …“ |
| M19 | Kennungsgrenze 129 | rot | „Eingaben ueber 128 Einheiten …“ |
| M20 | Grenzwechsel (`SwitchToSessionLimits`) entfernt | rot | Handtabelle, „Annahme …“, „Folgebytes …“, „Die Annahme erzeugt die Sitzung …“ |
| M21 | `Mutex` entfernt | rot | „Das Schliessen wartet auf das laufende Schreiben“ |
| M22 | Zwischenfeld der Passwort-Abbildung nicht überschrieben | rot | „Die Abbildung ueberschreibt …“ |
| M23 | State der Ablehnung hängt vom Grund ab | rot | wie M04 |
| M24 | Testmodus nimmt jeden Client an (stilles Downgrade) | rot | „Testmodus bricht bei Client mit ON oder REQ ab“ |
| M25 | Zwangsschließen in `expire()` (Annahme beschlossen, Schreiben unterbrochen) entfernt | **überlebt** | — (Review F-4 bestätigt) |
| M26 | Packet-Size-Obergrenze `<` statt `<=` (genau `MAX_TDS_PACKET_BYTES`) | **überlebt** | — (Grenzwert nicht getestet; 70 000 und 4 getestet) |
| M27 | `WRITE_FAILED` → `READ_FAILED` beim Schreibabbruch | rot | „Ende, Lesefehler, TLS-Fehler und Schreibfehler …“ |
| M28 | `LOGIN_TIMEOUT` in `READY` zulässig statt ignoriert | rot | Zählung, Handtabelle |
| M29 | Ausnahmetext statt Typ ins Log | rot | „Eine Ausnahme im Adapter …“ |
| M30 | `closed()` behält `pending` (Datenbank/Packet Size) | **überlebt** | — (kein Geheimnis im Kontext; der Test prüft den Kontext nach dem Schließen nicht) |
| M31 | TDS-8.0-Reihenfolge beginnt trotz bestehendem TLS einen zweiten Handschlag | rot | zwei Treiber-/PRELOGIN-Tests, Handtabelle |
| M32 | Standarddatenbank bei leerem LOGIN7-Feld entfernt | rot | „Voreinstellungen fuer Datenbank, …“ |

**Bewertung M01/M02:** Beide Mutationen ändern nur die Zählung 25 → 26, weil `HandshakeAutomaton.message(...)` nach Zustand, nicht nach Ereignisart verzweigt: das zweite PRELOGIN (bzw. ATTENTION) in `LOGIN7` landet im LOGIN7-Parser, scheitert dort als `Malformed` und schließt mit `PROTOCOL_VIOLATION`, beobachtbar identisch zur unzulässigen Zelle. Die Zählung ist damit der einzige Wächter dieser Zellen; die Handtabelle unterscheidet sie nicht. Als Latenz-Risiko: eine Nachricht eines anderen Pakettyps mit gültiger LOGIN7-Nutzlast würde in diesem Zustand als Anmeldung gelesen. Im ausgelieferten Code ist die Zelle unzulässig und ein Wegwerf-Test (`ATTENTION` mit gültiger LOGIN7-Nutzlast im Zustand `LOGIN7`) schließt mit `PROTOCOL_VIOLATION`; die Gefahr entsteht erst, wenn jemand die Zelle öffnet (Finding L-2).

**Nicht mutierte Zweige, die ich zusätzlich geprüft habe:**

* Wegwerf-Test im Scratchpad (`probe/`): Adapter wirft `CancellationException` → `HandshakeDriver.run` wirft `java.util.concurrent.CancellationException` nach außen; gesendet ist nur die PRELOGIN-Antwort, der Kanal ist **nicht** geschlossen (Finding M-1).
* Wegwerf-Test: ATTENTION mit gültiger LOGIN7-Nutzlast im Zustand `LOGIN7` → `CLOSED`, `Close(PROTOCOL_VIOLATION)` (siehe oben).
* Durchsicht ohne Mutation: `expire()` und der Pfad „Annahme beschlossen, nicht gesendet“ (M25), `AuthExecutor` ohne Obergrenze verwaister Aufrufe (Review F-1), Packet Size wird angekündigt, aber im Schreiber nicht gesetzt (Review F-2).

## 5. Test-Stabilität

Gefahren wurden die gesamten 280 Tests, darunter die Wanduhr-Tests (Frist 200 ms, Frist 300 ms gegen Adapter 3 s, Ticker 400 ms).

| Lauf | Ergebnis |
|---|---|
| 1 frischer Lauf (Abschnitt 1) | 280 PASSED, 0 FAILED |
| 5 Läufe nacheinander | 5 × Exit 0, je 280 PASSED, `BUILD SUCCESSFUL in 41 bis 57 s` |
| 8 Läufe unter Parallellast (vier Docker-Builds gleichzeitig, zwei Runden, 20 Kerne) | 8 × Exit 0, je 280 PASSED, 0 FAILED; 85 bis 108 s je Lauf |
| Läufe der 32 Mutationen ohne Bezug zu den Wanduhr-Tests | die Wanduhr-Tests waren dort nur rot, wo die Mutation sie treffen sollte (M10, M11) |

**Flakiness: keine beobachtet** (14 vollständige Läufe, davon 8 unter vierfacher Last, kein einziger roter Wanduhr-Test). Die Schwellen sind großzügig (Ticker: mindestens 10 Ticks bei erwarteten rund 40; langsamer Adapter: unter 1,5 s bei 3 s Blockierzeit); die Aussage trägt für diese Last, nicht für eine stärker überlastete CI. Der Test `Ein langsamer Adapter …` lässt einen 3-s-Thread im `Dispatchers.IO` über das Testende hinaus laufen; das stört die gefahrenen Läufe nicht.

## 6. Offene Punkte vor der Closure (keine Code-Mängel)

* Closure-Notiz in §7: Zuordnung *Test | Festlegung* je Aussage, die Abweichung der Zählung (84 gegen 140) ohne Übernahme der Spike-Zahl, Steering-Loop-Lerneintrag, Ausgang je Risiko aus §6, Register-Fortschreibung (`BEO-TDS/…`).
* Die Risiken zu Messung am echten SQL Server, mehrpaketigem LOGIN7/TDS 8.0 und Nahtstelle zum Transport sind inhaltlich weiter offen; ihr Ausgang ist „weiter offen“ mit Träger, nicht „entfallen“.
* [ADR-0004](../plan/adr/0004-session-zustandsautomat.md) und [ADR-0007](../plan/adr/0007-fehler-und-attention.md) bleiben mit ihren offenen Punkten unverändert; das KDoc kennzeichnet sie als „Festlegung ohne Messung / offen“: Antwort auf unzulässigen Übergang, ATTENTION im Handshake, RESETCONNECTION nicht ausgewertet, Ende nach abgelehntem Login (Setzung des Nutzers aus [ADR-0007](../plan/adr/0007-fehler-und-attention.md), nicht gemessen), Zuschnitt der Aufzählung. Die Punkte „Sitzungszustand nach Fehlern“ und „Schließen nach `TDS_SHUTDOWN`“ berührt der Handshake nicht und nennt der Code nicht.

## 7. Findings

**HIGH: keine.**

**MEDIUM**

* **M-1 — `CancellationException` des Adapters verlässt den Treiber und lässt die Verbindung offen.** `AuthExecutor` wirft sie weiter (`if (failure is CancellationException) throw failure`); `HandshakeDriver.run` gibt sie nach außen, ohne den Kanal zu schließen. Nachgestellt im Wegwerf-Test: `THROWS java.util.concurrent.CancellationException`, nur die PRELOGIN-Antwort gesendet, `closed=false`. Kein Test deckt den Pfad. Die DoD verlangt, dass eine Ausnahme im Adapter wie `Rejected` beantwortet wird ([ADR-0009](../plan/adr/0009-authentifizierungsmodus.md)); der Aufrufer sieht hier zudem eine Abbruchausnahme, die wie ein Abbruch seiner eigenen Coroutine aussieht. Entspricht Review F-3. Empfehlung: im Executor nur den Abbruch des eigenen Geltungsbereichs weiterreichen und die Ausnahme des Adapters als `ADAPTER_FAILURE` beantworten; Test mit `CancellationException` aus dem Adapter.
* **M-2 — Keine Obergrenze für verwaiste Adapteraufrufe nach Fristablauf.** `AuthExecutor` hat einen eigenen Geltungsbereich ohne Begrenzung; nach `LOGIN_TIMEOUT` läuft der blockierende Aufruf zu Ende, und viele nicht angemeldete Verbindungen können viele solcher Aufrufe erzeugen. Aus dem Code gelesen, nicht gefahren; Slice §6 führt „Messung zur Wirkung auf den Transport liegt nicht vor“ als offenes Risiko, die fehlende Obergrenze steht dort nicht. Entspricht Review F-1; gehört in die Closure als Risiko-Ausgang „weiter offen“ mit Träger ([TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit), Drosselung beim Sicherheits-Slice).

**LOW**

* **L-1 — Slice-Text veraltet:** Slice §3 sagt, `protocol/session` importiere aus `transport.framing` „nur die Wertetypen `TdsMessage`, `PacketType`“; tatsächlich kommen `FramingLimits` (vier Dateien) und `PacketHeader` (`LoginResponses.kt`) hinzu. Alles Wertetypen/Konstanten, keine Leser/Schreiber/Kanäle, detekt grün; nur der Planungstext ist zu korrigieren (entspricht Review F-6).
* **L-2 — Handtabelle und Zählung unterscheiden die Zellen `LOGIN7 × PRELOGIN_MESSAGE` und `LOGIN7 × ATTENTION_MESSAGE` nicht von „zulässig“ (M01/M02):** `message()` dispatcht nach Zustand statt nach Ereignisart. Empfehlung: nach `event.kind` verzweigen oder in der Handtabelle die Aktionen (z. B. keine Parser-Aufrufe) prüfen; entspricht Review F-5.
* **L-3 — Die ENVCHANGE-Bytes der Annahme sind nicht als Hex-Handvektor gepinnt:** Der Test vergleicht Token-Objekte über den Test-Decoder; als Hex stehen nur LOGINACK und DONE. Meine unabhängige Hand-Ableitung stimmt mit den tatsächlichen Bytes überein (Abschnitt 2); die DoD fordert den Hex-Vektor nicht ausdrücklich, aber der Slice nennt „Antwortbytes“ und „Byte-Vektoren der Antworten“ (§3).
* **L-4 — Überlebende Mutationen M25, M26, M30:** Zweig „Annahme beschlossen, Schreiben unterbrochen“ in `expire()` (Review F-4), Grenzwert `MAX_TDS_PACKET_BYTES` bei der Packet Size und der Kontext nach `closed()` sind ohne Test. Kein Geheimnis betroffen.
* **L-5 — DoD-Wortlaut „kontrollierte Ablehnung“ bei ungültigen Offsets/Längen:** Der Code schließt ohne Antwort. Als Festlegung gekennzeichnet (Review F-11); die Closure-Notiz sollte den Wortlaut der DoD an den Ist-Zustand anbinden.
* **L-6 — Abdeckungsmatrix nicht nachgeführt:** `docs/plan/spec-abdeckung.md` zeigt `SPEC-001` mit 0 Tests und „OHNE NACHWEIS“; die 49 neuen Tests (mit `@spec`-Tag) sind dort nicht abgebildet. `make spec-abdeckung` ist kein Gate und die DoD verlangt keine Nachführung; ich habe die Datei nicht neu erzeugt (keine bleibende Änderung).
* **L-7 — `make test` allein belegt keinen frischen Lauf** (Cache-Treffer bei unverändertem `src/`); wer die Aussage „Tests grün“ prüft, braucht `--no-cache-filter test` (bekannte Beobachtung `BEO-TDS/test-stage-cache-verdeckt-wiederholung`).

**INFO**

* Angekündigte, nicht gesetzte Packet Size (Review F-2): `AuthenticatedSession.packetSize` und die ENVCHANGE nennen sie, der Schreiber des Transports nutzt eine feste Größe; Träger ist der Transport. Eine kleinere Antwortpaketgröße verletzt das Protokoll nicht; die Aussage steht als Festlegung im Clientprofil, ist aber noch nicht wirksam.
* Hand-Nachrechnung der Zählung (25/4/55), des PRELOGIN-Antwortvektors, des ERROR/DONE-Vektors und des LOGINACK/DONE-Schwanzes stimmt mit dem Code überein.
* Während des Laufs änderte sich `main` (neue Merges und Reports anderer Slices); der geprüfte Code (`src/`) blieb identisch.

## 8. Verdikt

Die drei Liefer-Punkte sind im Code und in den Tests **bestätigt**, mit den Einschränkungen M-1 (Adapter-`CancellationException`), M-2 (keine Obergrenze verwaister Aufrufe, in der Closure als Risiko zu führen) und den LOW-Befunden. Nicht bestätigt sind allein die Closure-Pflichten (Notiz, Lerneintrag, Risiko-Ausgänge, Register), weil sie erst nach diesem Bericht entstehen. Empfehlung an den Planner: M-1 vor der Closure beheben oder mit Begründung annehmen; M-2 und die Zählungsabweichung in der Closure-Notiz führen; L-1 in Slice §3 berichtigen.
