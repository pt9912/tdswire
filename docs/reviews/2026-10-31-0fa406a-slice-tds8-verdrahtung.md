# Review slice-tds8-verdrahtung (Merge 0fa406a)

* Review-Art: Code, gegen Plan und Entscheidungen
* Gegenstand: Diff `ecf0aae^..d528e26` (`ConnectionWiring.kt`, neu `PeekedChannel.kt`, neu `Tds8WiringTest.kt`, `TlsWiringTest.kt`, `spec/spezifikation.md` Sicherheitsprofil, Slice-Plan-Haken)
* Grundlage: `slice-tds8-verdrahtung`, [ADR-0002](../plan/adr/0002-tls-und-verschluesselung.md), [ADR-0009](../plan/adr/0009-authentifizierungsmodus.md), [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit), `SPEC-001`, `SPEC-010`, `SPEC-020`, `SPEC-028`, `AGENTS.md` §3.1 bis 3.8
* Skill: `.harness/skills/reviewer.md` · Modell: claude-sonnet-5-5 · Datum: 2026-10-31
* Gegen DoD wird nicht geprüft (Verifier).

## Läufe

| Lauf | Ergebnis |
|---|---|
| `make test` auf `0fa406a` (main mit beiden gemergten Slices) | Exit 0, 766 Tests, keiner rot |
| Mutation A: `withTimeoutOrNull` um `PeekedChannel.peek` entfernt (Frist des Wartens auf das erste Byte) | rot: `HandshakeWiringTest > Die Frist des Treibers trägt den konfigurierten Wert` (1 von 766); die Frist ist getragen |
| Mutation B: Frist des `HandshakeDriver` im Handler auf 10000 s | grün (Exit 0): siehe M-1 |
| Mutation C: Bedingung `encryptionPolicy != REQUIRED` aus `handshakeConfig` entfernt | grün (Exit 0): siehe M-2 |
| `PeekedChannel.read` und Pfadwahl nach `0x16` | nicht mutiert; die Happy-Tests von `Tds8WiringTest` setzen beide Zweige und das Byte-Weiterreichen voraus (nur gelesen, nicht belegt) |

Die Quelldatei ist nach den Mutationen per `git checkout` wiederhergestellt; kein Produktivcode im Commit.

## Findings

### MEDIUM

**M-1**
* kategorie: MEDIUM
* quelle: `AGENTS.md` §3.8, §3.7, `SPEC-008`
* pfad: `src/test/kotlin/io/github/pt9912/tdswire/runtime/HandshakeWiringTest.kt` · `beendet allein der Treiber die Verbindung binnen der Wartezeit von `eventually``
* befund: Der Test schickt kein Byte; seit dem Diff beendet diesen Fall das Warten auf das erste Byte im Handler, nicht der Treiber (Mutation A macht ihn rot, Mutation B, Treiberfrist 10000 s, lässt alle 766 Tests grün). KDoc und Name behaupten die Treiberfrist, die jetzt kein Test mehr trägt. Szenario: Die Treiberfrist wird gelockert oder fehlt, und nur die Frist des Listeners schließt noch.
* verifizierbar: ja (Mutation B wiederholbar: Argument `setup.login.timeoutSeconds.seconds` an `HandshakeDriver` ändern, `make test`)
* klasse: Test trägt nach Umbau eine andere Zeile als sein KDoc sagt

**M-2**
* kategorie: MEDIUM
* quelle: Maintainability (Eigencode ohne tragenden Test), [ADR-0002](../plan/adr/0002-tls-und-verschluesselung.md)
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/runtime/ConnectionWiring.kt` · `setup.tls == null || setup.handshake.encryptionPolicy != EncryptionPolicy.REQUIRED ->`
* befund: Der zweite Teil der Bedingung (Pfad ab dem ersten Byte nur mit `REQUIRED`) ist neu, und seine Entfernung lässt alle Tests grün (Mutation C); die Konfiguration lässt TLS-Material mit `TEST_UNENCRYPTED` nicht zu, der Test `Im Testmodus ohne TLS` trifft nur den Teil `tls == null`. Szenario: Ein späterer Aufrufer baut `HandlerSetup` mit Material und `TEST_UNENCRYPTED`, und der TDS-8.0-Pfad nähme an, obwohl Spec und KDoc das ausschließen. Die Logzeile „keine TLS-Richtlinie“ trifft für beide Zweige zu, nennt aber nur einen Grund.
* verifizierbar: ja (Mutation C)
* klasse: Eigencode, dessen Entfernung keinen Test rot macht

### LOW

**L-1**
* kategorie: LOW
* quelle: `AGENTS.md` §3.8
* pfad: `src/test/kotlin/io/github/pt9912/tdswire/runtime/Tds8WiringTest.kt` · `beenden die Verbindung ohne ein Byte` / `kommt nicht zum PRELOGIN: der Handler trennt ohne Antwort`; `spec/spezifikation.md` · `eine Gegenstelle ohne ALPN `tds/8.0` enden ohne Antwort`
* befund: „Ohne ein Byte Antwort“ ist nur für TLS 1.1 und den Klartext-Rumpf mit `closedByServerWithoutBytes` belegt; beim abgebrochenen ClientHello fehlt die Prüfung ganz (der Client schließt selbst), bei ALPN-Mismatch und `h2` prüft der Test nur „keine Nachricht“ bzw. überspringt den Fall, wenn der Client-Handschlag scheitert. Ob die Engine dort einen TLS-Alert sendet, ist nicht beobachtet; die Spec-Zeile sagt „ohne Antwort“ für beide.
* verifizierbar: ja (Rohbytes-Leser auf den Alert prüfen)
* klasse: Aussage breiter als der Beleg

**L-2**
* kategorie: LOW
* quelle: `AGENTS.md` §3.7
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/runtime/ConnectionWiring.kt` · `der Handler startet den Treiber ohne Verzug`
* befund: Der Satz im KDoc steht noch da; der Handler wartet jetzt bis zu `LOGIN_TIMEOUT` auf das erste Byte, bevor er den Treiber startet. Der Folgesatz („endet damit nie vor der des Listeners“) gilt weiter. Der KDoc-Absatz zur Pfadwahl bricht mitten im Satz in zwei Zeilen um (`ersten Byte` / `(TDS 8.0, …`), ohne Bedeutungsfolge.
* verifizierbar: nein
* klasse: Kommentar beschreibt den Zustand vor dem Diff

**L-3**
* kategorie: LOW
* quelle: Maintainability (neuer öffentlicher Typ ohne eigenen Test)
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/transport/channel/PeekedChannel.kt` · `suspend fun peek(inner: SuspendingChannel): PeekedChannel?`
* befund: Kein Test nennt `PeekedChannel`; der Zweig „Gegenstelle endet oder scheitert vor dem ersten Byte“ (`null`) hat keinen Fall, und `read` mit einem Puffer ohne Restplatz würde `BufferOverflowException` werfen (nicht im Vertragstext). Gelesen: beide Randfälle sind von außen kaum unterscheidbar (EOF endet im Leser ebenso), daher LOW.
* verifizierbar: ja (Unit-Test am Kanal)
* klasse: Neuer Kanal-Typ nur über Integrationstests belegt

### INFO

**I-1** · Frage (1), Erkennung: `0x16` ist kein definierter TDS-Pakettyp (Typen 0x01 bis 0x12), ein TDS-7.4-Client beginnt mit `0x12`, ein Fehlgriff nach 7.4 ist damit nicht aus dem Protokoll abzuleiten (Literatur-/Kenntnisstand, hier nicht am Referenzclient gemessen; die Spec-Zeile kennzeichnet das als Setzung). Ein Downgrade ist nicht möglich: `0x16` führt nie in den Klartext-Pfad, im Testmodus wird getrennt. `PeekedChannel` ist read-once (`served` ohne `@Volatile`, ein Leser zur Zeit, Coroutine-Wechsel geben Happens-Before), `close` delegiert, Fehlerpfade geben `null`, und die Verbindung schließt der Listener.

**I-2** · Frage (3), Slowloris: Ein stummer Client hält seinen Slot bis `LOGIN_TIMEOUT`, wie vorher; die Frist des Listeners bleibt die äußere Grenze (Treiberfrist beginnt erst nach dem ersten Byte, endet aber nie vor der des Listeners). `AuthPool` wird vor `READY` nicht berührt. Ein Handler-Rückkehr durch die Peek-Frist kann im Echtzeitbetrieb als `HANDLER_RETURNED` statt `LOGIN_TIMEOUT` gemeldet werden, wenn sie vor dem Zeitgeber des Listeners abläuft (Beschriftung des Ereignisses, keine Ressourcenfolge).

**I-3** · Frage (2), Log: Die neue Zeile `TLS ab dem ersten Byte nicht angeboten: keine TLS-Richtlinie` und `TLS-Handschlag gescheitert: <Grund>` enthalten keine Zugangsdaten; `Tds8ProcessTest` prüft, dass Benutzer und Passwort nicht in der Ausgabe stehen. Ein stummes Ende (kein Byte, Frist) loggt nichts; Zeile je abgelehnter Verbindung ohne Grenze wie bisher.

**I-4** · Frage (5), Tests: `Tds8ProcessTest` benutzt Port 0, `use {}` für Kind-JVM und Client, `blocking`-Frist und `ServerProcess`-Wartezeit 60 s; die Antworten werden mit `withTimeoutOrNull` bzw. `closedByServerWithoutBytes` begrenzt. Kein Zeitrisiko im Lauf (ein grüner Lauf, nicht wiederholt, daher keine Aussage über Streuung). `log.lines.clear()` je Fall setzt voraus, dass die vorige Verbindung ihre Zeile geschrieben hat; `assertStillServesStrict` stellt das sicher.

**I-5** · Frage (8): `make test` auf `0fa406a` grün; `ConnectionWiring.kt` ist von `slice-sim-aenderung-integration` unberührt, `Bootstrap` kompiliert mit `RequestSource`. `make gates` und `make docs-check` vor dem Commit grün.

**I-6** · Frage (6), Spec-Zeile: keine `ADR-`-Wörter oder Pfade, Setzung („Setzung, nicht gemessen“) an der Pfadwahl gekennzeichnet; die Folgesätze („Der Pfad … besteht nur mit …“, „enden ohne Antwort“) stehen ohne eigene Kennzeichnung, siehe L-1.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main/.../transport/channel/PeekedChannel.kt` | geprüft, Befund L-3 |
| `src/main/.../runtime/ConnectionWiring.kt` | geprüft, Befunde M-2, L-2 |
| `src/test/.../runtime/Tds8WiringTest.kt` | geprüft, Befund L-1 |
| `src/test/.../runtime/HandshakeWiringTest.kt` (nicht im Diff, betroffen) | geprüft, Befund M-1 |
| `src/test/.../runtime/TlsWiringTest.kt` | geprüft, ohne Befund |
| `spec/spezifikation.md` Sicherheitsprofil | geprüft, ohne Befund (Hinweis I-6, Aussagenbreite unter L-1) |
| Suppression (`@Suppress`), Schichtimporte, Host-Aufrufe im Diff | geprüft, ohne Befund |
| Accepted-ADRs | geprüft, ohne Befund (keine geändert) |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 2 |
| LOW | 3 |
| INFO | 6 |

**Finding-Klassen dieses Laufs:** Test trägt nach Umbau eine andere Zeile als sein KDoc sagt · Eigencode, dessen Entfernung keinen Test rot macht · Aussage breiter als der Beleg · Kommentar beschreibt den Zustand vor dem Diff · Neuer Kanal-Typ nur über Integrationstests belegt

## Verdikt

**Merge-blockierend:** ja für M-1 und M-2 nach der Regel des Skills; sachlich ist der Pfad nach Prüfung sicher (kein Downgrade, Frist bleibt begrenzt), beide MEDIUM betreffen Test-Tragfähigkeit.

**Übergabe:** Findings gehen an den Implementer; die Finding-Klassen gehen in die Slice-Closure §7. Der Report ersetzt keine Verifikation.
