# Verifikation: slice-tds8-verdrahtung (Umsetzung ecf0aae, Review 0fa406a, Fix-Runde 2a5e486, Merge c3b6439) — 2026-10-31

**Rolle:** Verifier (Modul 11), frischer Kontext. Frage: Bauen wir es richtig? Geprüft wird gegen die DoD des Slice [slice-tds8-verdrahtung](../plan/planning/done/slice-tds8-verdrahtung.md), [ADR-0002](../plan/adr/0002-tls-und-verschluesselung.md), [ADR-0009](../plan/adr/0009-authentifizierungsmodus.md), [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit), `SPEC-001`, `SPEC-010`, `SPEC-020`, `SPEC-023`, `SPEC-028` und `AGENTS.md` §3.6 bis §3.8. Nicht Gegenstand: Richtigkeit der Anforderung (Validator), Diff gegen Plan im Sinn des Reviews (Reviewer). Vorliegend: Review [2026-10-31-0fa406a](2026-10-31-0fa406a-slice-tds8-verdrahtung.md) (0 HIGH, 2 MEDIUM, 3 LOW), Fix-Runde 2a5e486.

**Stand:** `c3b6439` (Branch des Worktrees gleich `main`, Arbeitsbaum sauber vor und nach allen Mutationen).

## Ergebnis in einem Satz

DoD und Plan sind für die Liefer-Punkte erfüllt, M-1, M-2, L-1 bis L-3 und der gefundene Fehler `served = true` vor `buffer.put` sind behoben und durch je eine Mutation belegt; es bleiben **kein HIGH-**, **kein MEDIUM-** und zwei LOW-Befunde (einer außerhalb des Slice).

## Sensor-Belege (selbst gefahren)

Logs: `/tmp/claude-1000/-Development-tdswire/8562f454-84ba-456b-9970-945e69c035f4/scratchpad/verif/` (`fresh.log`, `gates.log`, `mut-*.log`, `run1.log` bis `run4.log`, `mut.sh`). Andere Agenten bauten parallel (Last des Hosts 11 bis 21 beim Start der Läufe); von mir lief je Zeitpunkt nur ein Build, jeder unter `timeout 540`.

| Sensor | Ausgang |
|---|---|
| `docker build --no-cache-filter test --target test --progress=plain .` | Exit 0: 772 Zeilen `PASSED`, 0 `FAILED`, 0 übersprungene Tests (die zwei Treffer auf „SKIPPED“/„FAILED“ sind ein Gradle-Task und der Testname `FAILED_CLOSE_CONNECTION`); 1 min 39 s |
| `make gates` | Exit 0 (50 s, Layer aus dem Cache des Laufs oben) |
| Mutation A: Pfadwahl immer `ENCAPSULATED_IN_TDS` | rot: 7 Tests, `Tds8WiringTest` (5 von 5), `Tds8ProcessTest`, `Tds8DefenseTest` |
| Mutation B: Treiberfrist im Handler `10000.seconds` | rot: genau `HandshakeWiringTest > Die Frist des Treibers traegt den konfigurierten Wert` (M-1 behoben; der Test des Wartens auf das erste Byte bleibt grün und trägt eine andere Zeile) |
| Mutation C: Bedingung `encryptionPolicy != REQUIRED` durch `false` ersetzt | rot: genau `Tds8DefenseTest > Material mit TEST_UNENCRYPTED bietet den Pfad ab dem ersten Byte nicht an` (M-2 behoben) |
| Mutation D: `served = true` vor `buffer.put(firstByte)` | rot: genau `PeekedChannelTest > Ein Puffer ohne Platz wirft` (L-3 und der gefundene Fehler behoben) |
| Mutation E: `withTimeoutOrNull` um `PeekedChannel.peek` entfernt | rot: genau `HandshakeWiringTest > Die Frist des Wartens auf das erste Byte traegt den konfigurierten Wert` |
| `Tds8ProcessTest`, Streuung | Lauf 1 bis 4 (frisch, ohne Cache, Last 11 bis 21): in allen vier Läufen `PASSED`; Exit 0 in Lauf 1, 3, 4; Lauf 2 Exit 1 wegen eines anderen Tests, siehe L-5. Die Gradle-Ausgabe nennt keine Dauer je Test, eine Zeitstreuung des Tests ist daher nicht gemessen. |
| `make docs-check` | Ergebnis im Übergabetext des Commits |

Die Quelldateien sind nach jeder Mutation aus einer Sicherung wiederhergestellt; kein Produktivcode im Bericht-Commit.

## DoD Punkt für Punkt

| DoD-Punkt (Slice §2) | Befund |
|---|---|
| TDS-8.0-Pfad im Serverprozess (`SPEC-001`, `SPEC-020`) | erfüllt: `HandshakeConnectionHandler.handle` wählt am ersten Byte (`0x16` gegen sonst), `TlsPath.FROM_FIRST_BYTE` mit `HandshakeOrder.TLS_FIRST`. `Tds8WiringTest` (JDK-Engine, TLS 1.3 und 1.2, ALPN `tds/8.0`, `ENCRYPT_REQ`, `LoginAck` bei richtigem, keines bei falschem Passwort, Ende als `HANDLER_RETURNED` ohne Fehler wie im 7.4-Pfad), Rohbytes-Fälle (TLS 1.1, abgebrochenes ClientHello, Klartext im TLS-Record, Byte `0x41`), ALPN-Mismatch und Testmodus; `Tds8ProcessTest` am Kind-JVM-Prozess. Verhalten bei Klartext im Modus: Klartext im Record scheitert im Handschlag ohne Antwort und ohne Rückfall (Test, Mutation A). Pfadwahl und Fehlen eines Schalters sind in KDoc und Spezifikation als Festlegung, nicht gemessen, gekennzeichnet |
| KDoc als Ist-Zustand | erfüllt: `ConnectionWiring.kt` beschreibt den verdrahteten Pfad; der Satz „startet den Treiber ohne Verzug“ ist durch das Warten auf das erste Byte ersetzt (L-2 behoben), die Aussage endet bei „JDK-Gegenstelle und Rohbytes, nicht … Referenzclient“ (§3.8) |
| `make gates` grün | erfüllt (eigener Lauf, Exit 0) |
| Review durchgeführt, Report unter `docs/reviews/` | Report liegt vor; der Haken im Plan ist offen und gehört zur Closure (kein Befund des Codes) |
| Doku-Update (`SPEC-020`, Sicherheitsprofil) | erfüllt: `spec/spezifikation.md` nennt die Pfadwahl am ersten Byte statt „nicht verdrahtet“; keine ADR-Wörter oder Pfade in der Sicht (§3.4 nicht berührt) |
| Closure-Notiz, Register, Risiko-Ausgänge, drei Paarungen | offen, nicht Gegenstand dieser Verifikation; §6 trägt beide Risiken „offen bis zur Closure“ |

Liefer-Punkte: zwei (≤ 3). Alle Kästchen der DoD, die der Implementer setzen durfte, sind gesetzt, die übrigen stehen zutreffend offen.

## Behebung der Review-Findings

| Finding | Behoben? | Beleg |
|---|---|---|
| M-1 Treiberfrist ohne tragenden Test | ja | neuer Test schickt `0x12` und schweigt; Mutation B rot nur dort. Der alte Test heißt jetzt „Frist des Wartens auf das erste Byte“ (Mutation E rot nur dort) und sein KDoc verweist auf den Test darunter |
| M-2 Bedingung `!= REQUIRED` ohne Test | ja | eigener Zweig mit eigener Logzeile („Richtlinie nicht REQUIRED“), Kommentar als Verteidigung gekennzeichnet; `Tds8DefenseTest` baut ein `HandlerSetup` mit Material und `TEST_UNENCRYPTED`; Mutation C rot. Dass `HandshakeConfig` die Kombination mit `TLS_FIRST` verweigert (`require` in `ConnectionState.kt`), stimmt mit dem Test-KDoc überein |
| L-1 „ohne Antwort“ breiter als der Beleg | ja | Spezifikation und Test-KDoc sagen „ohne TDS-Antwort“ und nennen den Alert als nicht beobachtet. Die Tests prüfen „kein Byte“ weiterhin für TLS 1.1 und Klartext im Record, das ist enger als die Spezifikation und damit gedeckt |
| L-2 KDoc „ohne Verzug“ | ja | Satz ersetzt; der Absatz zur Pfadwahl steht zusammenhängend |
| L-3 `PeekedChannel` ohne Test | ja | `PeekedChannelTest` mit vier Fällen (Byte wird zuerst geliefert, `null` bei EOF und Lesefehler, Puffer ohne Platz wirft und das Byte bleibt erhalten, Schreiben und Schließen delegieren) |
| Fehler `served = true` vor `buffer.put` | ja | `read` legt das Byte vor dem Setzen von `served` ab; Mutation D rot nur im Test „Ein Puffer ohne Platz wirft“ |

## ADR-Konformität

* [ADR-0002](../plan/adr/0002-tls-und-verschluesselung.md): Standard TLS verlangt; der TDS-8.0-Pfad läuft ab dem ersten Byte mit ALPN `tds/8.0` und besteht nur mit Material und `REQUIRED`. Kein Rückfall auf Klartext (Test), kein Downgrade: `0x16` führt nie in den Klartext-Pfad. Keine neue TLS-Konfiguration (`TlsParameters.SPEC_023` unverändert). Konform.
* [ADR-0009](../plan/adr/0009-authentifizierungsmodus.md): der Anmeldepfad ist unverändert (gleicher `HandshakeDriver`, gleicher `AuthPool`); falsches Passwort gibt im 8.0-Pfad keinen `LoginAck` (Test), `Tds8ProcessTest` prüft, dass die Ausgabe weder Benutzer noch Passwort nennt. Konform.
* `0x16` als Unterscheidungsbyte ist am Referenzclient nicht gemessen. Die Spezifikation kennzeichnet es: „wählt je Verbindung am ersten Byte (Setzung, nicht gemessen)“, ebenso KDoc und Plan §6/§8 (Träger `slice-qa-kompatibilitaet`). Kein Befund.

## Plan gegen Code

| Plan §3 | Ist |
|---|---|
| `runtime/` (`ConnectionWiring.kt`), update | geändert wie geplant |
| `src/test/.../runtime/` neu, Happy, Negative, Rohbytes, `@spec`-Tag je Test | `Tds8WiringTest` mit `Tds8DefenseTest` und `Tds8ProcessTest`; jeder Test trägt `@spec`. Der Prozess-Test steht nicht in der Plan-Tabelle; der Plan erlaubt die Erweiterung (Zeile „Implementer erweitert die Liste“), die DoD verlangt ihn („am laufenden Prozess“) |
| `transport/channel/PeekedChannel.kt` neu | vorhanden, mit `PeekedChannelTest` (nicht in der Plan-Tabelle, aus L-3) |
| `spec/spezifikation.md` Sicherheitsprofil | geändert |
| Nicht im Plan, aber geändert | `TlsWiringTest.kt` (3 Zeilen), `HandshakeWiringTest.kt` (Fix-Runde); beide im Review benannt |

`@Suppress` im Quelltext: keine Treffer. Keine Gate-Senkung (§3.6), keine geänderte Accepted-ADR (§3.5). Die Aussagen in Spezifikation, KDoc und Tests tragen höchstens so weit wie ihr Beleg (§3.8); Kommentare beschreiben den Ist-Zustand (§3.7).

## Befunde

### HIGH
Keine.

### MEDIUM
Keine.

### LOW

**L-4** (`docs/plan/spec-abdeckung.md`): die erzeugte Abdeckungsmatrix führt `SPEC-020` als „OHNE NACHWEIS“ (0 Tests), obwohl `Tds8WiringTest` und `Tds8ProcessTest` das Tag tragen. Die Datei ist ein Erzeugnis von `make spec-abdeckung` (kein Gate) und nicht neu erzeugt, also ein Stand vor dem Slice. Regenerieren bei der Closure; nicht von mir gefahren, weil das Dateien im Repo ändert.

**L-5** (außerhalb des Slice, bestehender Test): `BootstrapTest > Der Start-Log nennt jede Abschwaechung und keinen Wert` schlug in Lauf 2 fehl, weil der zufällig gewählte Port `34711` die Zeichenfolge `4711` des Prüfwortes enthält (Log: „gestartet, Port 34711“, `BootstrapTest.kt` Zeile 182). Ein portabhängiger Flake, je Lauf unwahrscheinlich, aber ein roter `make gates` ohne Ursache im Code. Nicht Gegenstand dieses Slice; als Beobachtung ins Register oder als eigener Fix (Prüfwort ohne Ziffernfolge im Portbereich).

### INFO

* Die Zeitstreuung von `Tds8ProcessTest` ist nicht gemessen (keine Dauer je Test in der Ausgabe); gemessen ist nur Bestehen in 4 von 4 Läufen unter Last.
* Die Spezifikation lässt offen, ob die TLS-Engine einen Alert sendet; die Tests beobachten in den zwei `HANDSHAKE_FAILED`-Fällen mit Rohbytes kein Byte. Die Spezifikation ist enger als der Beleg, kein Widerspruch.
* Offen für die Closure: DoD-Haken „Review durchgeführt“, Closure-Notiz mit Lerneintrag (Finding-Klassen aus dem Review), Register-Evidenz für `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, Ausgänge der zwei Risiken in §6 (Port: ein Port, Pfadwahl am ersten Byte, Setzung).

## Verdikt

Aus Sicht der Verifikation ist der Slice nach der Fix-Runde bereit für die Closure; am Code ist keine Nacharbeit nötig. L-4 und L-5 sind nicht blockierend.
