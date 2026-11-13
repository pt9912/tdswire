# Review-Report: slice-betrieb-prozesslauf-logzeilen-inspektion (Folgelauf, Nacharbeit) — 2026-11-13

**Review-Art:** Code (gegen Plan, Verträge und Hard Rules; nicht gegen die DoD); Re-Review der Nacharbeit nach dem ersten Report, nur diese Commits

**Gegenstand:** `slice-betrieb-prozesslauf-logzeilen-inspektion`, Diff `d1d2a3e1..b5c4e276`: Commit b5c4e276 (Nacharbeit) und 5f11ff2c (Merge von `main` e7ca8929, gelesen als Merge). Der Merge trägt keine eigene Auflösung: `git diff e7ca8929 5f11ff2c` enthält nur Dateien des Zweigs, die `main`-Seite (`referenztest-dotnet/Dockerfile`, `spec/spezifikation.md`, `tools/mk/vergleichslauf-d.mk`, Closure und Register des Slice `slice-referenzabgleich-adr-0028-nachzug`, eine Zeile „Verantwortlich" im Plan aus Commit 389e2fae) berührt keine Datei der Nacharbeit. Kein Produktcode: `git diff --stat d1d2a3e1..b5c4e276 -- src/main testserver` ist leer (bestätigt). Code der Nacharbeit: `src/test/…/runtime/LogInspektion.kt` und `TestserverLaufInspektionTest.kt`; sonst Plan und `docs/user/referenz-ereignisse.md`.

**Skill:** `.harness/skills/reviewer.md` @ b5c4e276
**Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-13

**Eingangs-Kontext:**

- erster Report `2026-11-13-5b2f9c60-review-slice-betrieb-prozesslauf-logzeilen-inspektion.md` (F-1 bis F-9)
- `slice-betrieb-prozesslauf-logzeilen-inspektion` (§2, §3 samt Absatz „Schranken der Wartestellen", §6, §8)
- [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md) (Ereignismodell, Bereitschaftszeile), [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md) (Prozesstests in der Stage `test`)
- `SPEC-017` bis `SPEC-019`, `SPEC-048`, `SPEC-052`, `SPEC-009`; `TDS-FA-09`, `TDS-QA-03`
- `AGENTS.md` §3.7, §3.8; `testserver/…/TelemetryJson.kt`, `src/main/…/domain/telemetry/TelemetryEvent.kt` und `MeasurementEvents.kt` (Zuordnung Feld zu Art)

**Sensoren dieses Laufs** (Docker, Wegwerf-Stage `rev` auf der Stage `deps` des `Dockerfile`, `gradle :test --tests …TestserverLaufInspektionTest`, Mutationen als `sed` im Container, der Arbeitsbaum blieb unverändert; ein Gradle-Lauf zugleich, Vordergrund, keine künstliche Last; Rechner-Last beim Start 5,3):

| Lauf | Mutation | Ergebnis | Wanduhr |
|---|---|---|---|
| Basis | keine | grün, Test `PASSED` | 66 s |
| M15 | `cause` = `AQIDBAUGBwgJCgsMDQ4PEA==` (Base64 des Salzes) in `login_rejected` (`LoginRejected`) | rot: „cause steht nicht in dieser Art: {… event=login_rejected … cause=AQIDBAUGBwgJCgsMDQ4PEA==}" (`ONLY_IN`, Zeile 135) | 64 s |
| M16 | `reason` von `login_rejected` = `AQIDBAUGBwgJCgsMDQ4PEA==` (erlaubtes Feld, erlaubte Art) | rot: „die Ausgabe nennt 'AQIDBAUGBwgJCgsMDQ4PEA==':" (Verbotsmenge, Teil des Prüfwerts) | 65 s |
| M17 | `failure` = `x` in `login_accepted` (`LoginAccepted`) | rot: „failure steht nicht in dieser Art: {… event=login_accepted … principal=alice, failure=x}" | 64 s |
| M-a (nicht berichtet) | `AdapterFailed` bei jeder Ablehnung (`LoginTransitions.reject`, `failureType ?: "RuntimeException"`): ein Ereignis, das `TelemetryJson` mit `connection_id` schreibt | **rot an der falschen Stelle**: „connection_id steht nicht in dieser Art: {… event=adapter_failed, connection_id=1, client_profile=unknown, exception_type=RuntimeException}" | 52 s |
| M-b1 (nicht berichtet) | `reason` von `login_rejected` = Salz als Base64URL ohne Padding `AQIDBAUGBwgJCgsMDQ4PEA` | rot, aber nicht an der Verbotsmenge: „expected: <WRONG_PASSWORD> but was: <AQIDBAUGBwgJCgsMDQ4PEA>" (`inspectEvents`, die zufällig festgelegte Marke); `assertNoneOf` lief grün durch | 59 s |
| M-b2 (nicht berichtet) | `cause` des Ereignisses `request_failed` (Feld und Art erlaubt, Wert nirgends festgelegt) = `AQIDBAUGBwgJCgsMDQ4PEA` | **grün** | 52 s |
| M-b3 (nicht berichtet) | `reason` von `login_rejected` = Salz als Hex in Großbuchstaben `0102030405060708090A0B0C0D0E0F10` | rot: „die Ausgabe nennt '0102030405060708090a0b0c0d0e0f10':" (Suche ohne Beachtung der Schreibweise) | 50 s |

Neun Gradle-Läufe, zusammen etwa 9,5 Minuten Bau- und Testzeit (der Bau der Stage dominiert, der Test selbst etwa 5 s). Nicht gefahren (Tabelle in `harness/README.md`): `make clean-build`, `make e2e`, `make gates`. `make docs-check` siehe unten.

---

## Findings

Nachprüfung der Findings des ersten Reports:

| Erstes Finding | Stand im Diff | Beleg |
|---|---|---|
| F-1 (MEDIUM) Prüfwert nur als Marke gesucht | geschlossen für das berichtete Szenario; Rest siehe F-12 | M15, M16, M-b3 rot; `checkValueParts()` leitet Ganzes, Marke, `pbkdf2`, Salz und Hash (Base64 der Datei) und beide als Hex aus `pbkdf2Value(BOB_PASSWORD)` ab |
| F-2 (MEDIUM) Plan-Tabelle | geschlossen | §3 nennt `docs/user/referenz-ereignisse.md`, `tools/e2e.sh` (Zeile 35), `docs/plan/e2e-abdeckung.md` (Zeile 23); §8 nennt die Seiten; die DoD-Zeile „Doku-Update" nennt die Seite und den Verweis in `testserver-manuell.md` |
| F-3 (LOW) Wanduhr-Frist | geschlossen; F-13 (INFO) | `QUERY_TIMEOUT` 3600 für den Lauf |
| F-4 (LOW) Plan-Wortlaut | Wortlaut in §2 berichtigt; zweite Fundstelle offen: F-11 | §2 sagt „folgt dem laufenden Server", „14 Ladeschritte und fünf Anfragen danach" |
| F-5 (LOW) Vorprüfung nach dem Code | geschlossen | der Vermerk „entstand nach dem Code, am Lauf des fertigen Tests" steht in der Überschrift der Vorprüfung |
| F-6 (LOW) Zweite Fundstelle ohne Sensor | geschlossen; F-10 (neu) | KDoc nennt `TelemetryJson`, `issue_kind`, `exception_type`, die Grenze „ein Feld, das der Adapter nie schreibt" |
| F-9 (INFO) Messzahl | geschlossen als Größenordnung; F-14 (INFO) | „etwa 1,2 MB (… die Zahl schreibt der Test nur in die Meldung einer Zusicherung, sie ist Größenordnung)" |

Neue Findings:

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-10 | MEDIUM | Der neue KDoc von `ONLY_IN` nennt die Zuordnung die, „mit der `TelemetryJson` es aus `TelemetryEvent` schreibt". Für `connection_id` (`WITH_CONNECTION`) stimmt das nicht: die Arten `adapter_failed`, `connection_failed`, `tls_first_not_offered` und `tls_handshake_failed` tragen den Kontext mit Verbindung (`TelemetryEvents.kt` Zeile 61, `ConnectionWiring.kt` Zeilen 205, 212, 235) und `TelemetryJson` schreibt `connection_id` dort, sie fehlen in der Menge. Failure-Szenario: ein Lauf, der einen Adapterfehler oder einen Handschlagfehler auslöst, wird an einem Feld rot, das der Adapter richtig schreibt (M-a gefahren: „connection_id steht nicht in dieser Art … adapter_failed"). Die übrigen 15 neuen Zuordnungen stimmen Art für Art (siehe Negativbefunde). | `AGENTS.md` §3.8, [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md) | `LogInspektion.kt` · „die Zuordnung, mit der `TelemetryJson` es aus `TelemetryEvent` schreibt"; `WITH_CONNECTION` | ja — Mutation M-a (rot an falscher Stelle) | Zweite Fundstelle ohne Sensor |
| F-11 | LOW | Der Vermerk „Abweichungen der Erwartungswerte aus §2" in Plan §3 nennt noch „fünf, nicht vier" und „Der Wortlaut „zuerst" in §2 Punkt 2 (a) gehört dem Planner", obwohl §2 jetzt „folgt dem laufenden Server" und „14 Ladeschritte und fünf Anfragen" sagt: der Vermerk beschreibt einen Text, der nicht mehr dasteht, und die Zahl „vier" in §2 gibt es nicht mehr. Grep nach „zuerst", „vier gestellten", „nicht Gegenstand" in `spec/`, `docs/user/`, `docs/plan/`, `tools/` ergab sonst keine Gegenstelle: die Treffer (`spec/lastenheft.md` Zeile 14, `tools/test-lauf-selbsttest.sh` Zeile 8, Plan §1 Zeile 45) sind andere Aussagen. | `AGENTS.md` §3.7 (Kommentar beschreibt abwesenden Text), Reviewer-Skill „Fortschreibung lässt die zweite Fundstelle stehen" | `slice-betrieb-prozesslauf-logzeilen-inspektion` §3 · „Der Wortlaut „zuerst" in §2 Punkt 2 (a) gehört dem Planner" | nein — Grep | Fortschreibung lässt die zweite Fundstelle stehen |
| F-12 | INFO | Rest von F-1: die Verbotsmenge sucht den Prüfwert in den Formen Ganzes, Marke, Salz und Hash als Base64 mit Padding (`==`, `=`) und als Hex, ohne Beachtung der Schreibweise. Base64URL ohne Padding, ein Teilstück oder ein auf zwei Felder geteilter Wert findet sie nicht: M-b2 (Salz ohne Padding in `cause` von `request_failed`) bleibt grün; M-b1 ist nur rot, weil `inspectEvents` das Feld `reason` der Ablehnung festlegt. Die Aussage im KDoc „ganz und in seinen Teilen" gilt für die genannten Formen; der Rest ist die benannte Grenze „sucht nur das Bekannte" und kein neuer Defekt. Die Bindung an den Test-Benutzer folgt: Ganzes, Salz und Hash stammen aus `pbkdf2Value(BOB_PASSWORD)`, dieselbe Quelle wie die Benutzerdatei des Laufs; `Iterationen` (Teil 2 des Werts) wird nicht gesucht. | `TDS-QA-03` | `TestserverLaufInspektionTest.kt` · `checkValueParts` | ja — M-b1, M-b2, M-b3 | Redaktion sucht nur den bekannten Wert |
| F-13 | INFO | Die Begründung in Plan §3 („damit der Server eine offene Anfrage nicht vor der Schranke einer Wartestelle selbst beendet") trägt als Vorsorge; im Zustand des Rückstaus wirkt die Frist nach `SPEC-009` nicht („solange kein Schreibvorgang der Antwort am Rückstau hängt"). Eine Folge ohne die Zeile ist nicht gefahren (sie wäre nur unter Verzug von über 30 s rot, `outcome` `timeout` statt `cancelled`). Das Maximum 3600 s ist Ist-Wert aus `ConfigValidation` (`MAX_REQUEST_TIMEOUT`, `range(… 1..3600)`) und `SPEC-009` („Festlegung, nicht gemessen"): belegt. Aufräumen: `ServerProcess.close()` (im `use`) ruft `destroyForcibly`, der Server hängt nach einem Rot vor der Schranke nicht 3600 s; `LaufClient.use` schließt vorher den Socket. | `SPEC-009`, [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md) | `slice-betrieb-prozesslauf-logzeilen-inspektion` §3 · „Schranken der Wartestellen"; `TestserverLaufInspektionTest.kt` · `QUERY_TIMEOUT_SECONDS` | nein | Wanduhr-Frist im Test |
| F-14 | INFO | Die Vorprüfung nennt weiter die Ziffernfolge „1 225 136 Byte", gekennzeichnet als Größenordnung, deren Zahl der Test nur in die Meldung einer Zusicherung schreibt. Trägt als Größenordnung (§3.8); wer die Zahl nachmessen will, sieht sie nur in einem roten Lauf. | `AGENTS.md` §3.8 | `slice-betrieb-prozesslauf-logzeilen-inspektion` §3 · „1 225 136 Byte in einem Lauf der Probe" | nein | Messzahl aus einem Erzeuger, der nicht im Repo liegt |

### Antworten auf die Prüfpunkte des Auftrags

1. **F-1.** Verbotsmenge und `ONLY_IN`: M15, M16, M17 rot an der benannten Behauptung (siehe Tabelle). Zuordnung Feld zu Art gegen `TelemetryJson.PROPERTIES` und die Unterklassen von `TelemetryEvent`: `cause` (`request_failed`), `reason` (`security_weakened`, `login_rejected`, `connection_closed`, `start_refused`, `tls_first_not_offered`), `failure` (`tls_handshake_failed`), `exception_type` (`bind_failed`, `adapter_failed`, `connection_failed`, `accept_failed`), `issue_kind` und `key` (`config_invalid`), `part` und `form` (`tls_start_rejected`), `address` und `port` (`server_started`, `bind_failed`), `tables` (`simulation_backend_ready`), `span`, `outcome`, `phase`, `request_kind` (`span_ended`) sind vollständig und nicht zu weit; keine Kollision mit den kleingeschriebenen Schlüsseln von `config_effective` (`port` ist dort kein Schlüssel). Nicht stimmig ist die schon vorher vorhandene Menge `connection_id` (F-10); `session_id` (`login_accepted`, `request_failed`, `span_ended`) deckt die Arten, die der Lauf erzeugt, ein Kontext mit Sitzung an `connection_failed` oder `adapter_failed` ist im Code nicht belegt und nicht gefahren. Kodierungen: F-12.
2. **F-3.** Aufräumen, Cancel-Aussage und Beleg des Maximums: F-13.
3. **F-4.** §2 ist richtig gegen den Test (`inspectSpans`: `List(loadSteps) { "ok" } + ok, ok, error, cancelled, ok`; Arten `sql_batch`/`rpc`) und [ADR-0020](../plan/adr/0020-logformat-und-observability-schnittstelle.md) (Bereitschaftszeile nach `StartOutcome`, synchron); kein Ziel (§1) und kein Liefer-Punkt wurde in seiner Aussage verändert, alle drei Haken stehen `- [ ]`. Die Formulierung „`ok`, `ok` als `rpc`" ist knapp, die Arten stehen in §3 (i) und im Test. Zweite Fundstelle: F-11.
4. **F-2/F-5/F-6/F-9.** Tabelle §3 und §8, Vorprüfungs-Vermerk, KDoc „Felder von `TelemetryJson`" (nennt die zwei Namen, die die Spezifikation nicht führt, und die Grenze des Sensors) und Größenordnung 1,2 MB tragen so weit wie ihr Beleg, mit F-10, F-11, F-14 als Rest.
5. **`HexFormat`.** `HexFormat.of().formatHex` liefert Kleinbuchstaben ohne Trenner, dieselbe Kodierung wie `"%02x".format`; die Suche setzt beide Seiten in Kleinbuchstaben; M-b3 bestätigt (Großbuchstaben gefunden). JDK 21 der Stage trägt `HexFormat`.
6. **Mutationen.** M15, M16, M17 und M-a, M-b1, M-b2, M-b3: Tabelle oben. Zu (a): `ONLY_IN` ist an einer Stelle zu eng (F-10); zu lax ist keine Art, die geprüft wurde.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main`, `testserver/` | geprüft, ohne Befund (kein Diff) |
| Merge 5f11ff2c | geprüft, ohne Befund (keine Auflösung, nur `main`-Seite; Zeile „Verantwortlich" stammt aus 389e2fae) |
| `TestserverLaufInspektionTest.kt` (Frist, `checkValueParts`, KDoc) | geprüft, Befunde F-12, F-13 |
| `LogInspektion.kt` (`ONLY_IN`, KDoc) | geprüft, Befund F-10; die 15 neuen Einträge ohne Befund |
| `docs/user/referenz-ereignisse.md` (Zeile 78 „Passwort, Prüfwert, Parameterwert und SQL-Text") | geprüft, ohne Befund (trägt die Formen von F-12; Wörter der Skill-Liste: kein Treffer in der Zeile) |
| Plan §2, §3 Tabelle, §8 | geprüft, ohne Befund (außer F-11) |
| `tools/e2e.sh`, `docs/plan/e2e-abdeckung.md` | geprüft, ohne Befund (unverändert gegenüber dem ersten Report) |
| Suppression (`@Suppress`, `noqa`, `nolint`) im Diff | geprüft, ohne Befund |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 1 |
| LOW | 1 |
| INFO | 3 |

**Finding-Klassen dieses Laufs:** Zweite Fundstelle ohne Sensor (F-10) · Fortschreibung lässt die zweite Fundstelle stehen (F-11) · Redaktion sucht nur den bekannten Wert (F-12) · Wanduhr-Frist im Test (F-13) · Messzahl aus einem Erzeuger, der nicht im Repo liegt (F-14)

## Verdikt

**Merge-blockierend:** ja, wegen F-10 (MEDIUM: der KDoc nennt eine Zuordnung, die gegen `TelemetryJson` für `connection_id` nicht stimmt, gefahren mit M-a). Die vier Nacharbeiten F-1 (für das berichtete Szenario), F-2, F-5 und F-9 sind geschlossen, F-3, F-4 und F-6 mit den Resten F-13, F-11 und F-10. Kein HIGH.

**Übergabe:** Findings an den Implementer; die Finding-Klassen gehen in die Slice-Closure §7. Die Nacharbeit b5c4e276 ändert Testcode; dieser Report ist der Re-Review dazu. Weitere Commits, die Testcode ändern (auch zu F-10), brauchen wieder einen Re-Review oder den Vermerk „Nachbesserung ohne zweiten Review" in §7. Der Report ersetzt keine Verifikation (DoD, `make clean-build`, `make e2e` gehören dem Verifier).
