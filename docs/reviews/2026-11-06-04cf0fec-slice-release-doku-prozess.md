# Review-Report: slice-release-doku-prozess — 2026-11-06

**Review-Art:** Code (Diff gegen Plan, Entscheidungen und Hard Rules). Gegen die DoD wird nicht geprüft; das ist die Frage des Verifiers. Das DoD-Wortlaut-Problem (F-1) ist ein Plan-Defekt und steht deshalb hier.

**Gegenstand:** Diff `1c9a85c9..04cf0fec` (Commits `4b7132d0`, `cc912f6a`, `04cf0fec`): `docs/user/einbettung-und-betrieb.md`, `docs/maintainer/releasing.md`, Rang-6-Zeilen in `AGENTS.md`, `harness/README.md`, `README.md`, Haken im Slice-Plan.

**Skill:** `.harness/skills/reviewer.md` @ 04cf0fec
**Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-06

**Eingangs-Kontext:**

- Slice-Plan slice-release-doku-prozess (§1 bis §3, §6)
- [ADR-0010](../plan/adr/0010-detekt-pre-release.md), [ADR-0013](../plan/adr/0013-backend-grenzen.md), [ADR-0018](../plan/adr/0018-laufzeit-artefakt.md), [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md)
- `SPEC-005`, `SPEC-008`, `SPEC-009`, `SPEC-010`, `SPEC-033`, `SPEC-034`, `SPEC-046`, `SPEC-047`, `SPEC-051`, `SPEC-052`, `SPEC-053`, `SPEC-059`
- [TDS-FA-08](../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz)
- `AGENTS.md` §3.1, §3.7, §3.8, §5 Nr. 5

**Eigene Läufe (Worktree, nur lesend):**

| Lauf | Ergebnis |
|---|---|
| `ServerConfig.kt`, `ConfigValidation.kt`, `TdsServer.kt`, `Bootstrap.kt`, `TelemetrySink.kt`, `TelemetryEvent.kt`, `SimModel.kt` gegen die Tabellen und Sätze der Nutzer-Doku | Namen, Voreinstellungen und Bereiche aller 11 Konfigurationsschlüssel stimmen mit Code und Spezifikation überein (Details unter Negativbefunde) |
| `grep SHUTDOWN_GRACE, MAX_CONNECTIONS, request_id, METRICS_INTERVAL, duration_ms` in `src/main` | `SHUTDOWN_GRACE` nur als interne Frist im Listener (`Bootstrap` setzt 0), `MAX_CONNECTIONS`, `request_id`, `METRICS_INTERVAL`, `duration_ms` kommen nicht vor |
| `curl` auf die Maven-Metadaten von `dev.detekt:detekt-cli` mit der Abfrage aus `releasing.md` | `latest`/`release` = `2.0.0-alpha.6`, Versionen `alpha.0` bis `alpha.6`, Abfrage ohne Ausgabe (stimmt mit der Aussage der Anleitung) |
| `gh release list -R pt9912/ai-harness-course -L 2 --json tagName,isLatest` | `v6.18.1` Latest, `v6.18.0` (stimmt mit der Aussage der Anleitung; ein Lauf an diesem Tag) |
| `git tag`; `build.gradle.kts`; `gradle/libs.versions.toml` | kein Tag; `version = "0.1.0-SNAPSHOT"`; `detekt = "2.0.0-alpha.6"` |
| ADR-0013-Zahlen (M1, M2, M7) | 173/454 Byte, 1,54 Mio Zeilen, 3099 ms/77 Threads, 377 ms/11, 707 ms stehen so in der ADR |

Nicht ausgeführt: `make gates`, `make docs-check` (Aussage des Implementers im Slice-Plan unbestätigt von mir).

---

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Die DoD-Punkte 2 und 3 nennen Entscheidungen des Nutzers als Inhalt der Lieferung („nach den Entscheidungen des Nutzers“, „in der Form, die der Nutzer wählt“). Der Implementer kann sie nicht allein schließen; geliefert ist ein Schritt, der Haken bleibt offen, und der Slice kann ohne Nutzerentscheidung nicht nach `done/`. Die Rückführung `in-progress` → `open` (blockiert) ist als Trigger benannt, der Plan sagt aber nicht, welcher der beiden Wege gilt. | Maintainability (Plan-Defekt); Baseline `v6.18.1` · `regelwerk/modul-05-planning-harness.md` §Closure | `docs/plan/planning/in-progress/slice-release-doku-prozess.md` · „Maintainer-Doku“ und „Wächter für“ | nein | DoD hängt an Nutzerentscheidung ohne Auflösungsweg |
| F-2 | LOW | `releasing.md` Schritt 1 setzt eine Bedingung: „der Lauf auf dem Release-Stand von `main` muss `success` sein“. Sie ist weder als Setzung gekennzeichnet noch in Spec oder ADR verankert; das Release-Kriterium ist laut Schritt 2 und „Offene Entscheidungen“ sonst als nicht entschieden geführt. | `AGENTS.md` §3.8 | `docs/maintainer/releasing.md` · „der Lauf auf dem Release-Stand von `main` muss `success` sein“ | nein | Festlegung ohne Kennzeichnung als Setzung |
| F-3 | LOW | Die Nutzer-Doku enthält Planungs- und Registerzeug: den Abschnitt-Punkt „Offene Entscheidungen des Nutzers, zu denen diese Anleitung nichts behauptet“, die Register-Namen `BEO-TDS/messung-am-echten-sql-server-fehlt` und `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` sowie „Entscheidungen des Nutzers vom 2026-10-31“. Regel 5 sagt: keine Planung, was kommt steht in `docs/plan/planning/`. | `AGENTS.md` §5 Nr. 5 | `docs/user/einbettung-und-betrieb.md` · „Offene Entscheidungen des Nutzers, zu denen diese Anleitung nichts behauptet“ | nein | Planung oder Register-Zeiger in Nutzer-Doku |
| F-4 | LOW | `releasing.md` trägt zwei Zustandsstellen ohne Sensor: „Stand dieser Anleitung `v6.18.1`“ (der Pin ändert sich bei jedem Baseline-Sprung, kein Gate hält die Zeile) und den Absatz „Erster Lauf, 2026-11-06“ als Chronik eines einzelnen Abrufs in einer Ablaufbeschreibung. | `AGENTS.md` §3.7 (Zustandsfelder) | `docs/maintainer/releasing.md` · „Stand dieser Anleitung `v6.18.1`“ | nein | Zustandsangabe ohne Anker oder Sensor |
| F-5 | LOW | Im Slice-Plan stehen vier `d-check:ignore`-Marker mit „geplant: neues Verzeichnis“ / „geplant: neue Datei“ an `docs/user/` und `docs/maintainer/releasing.md`, die jetzt bestehen; die Marker beschreiben einen Zustand, der nicht mehr da ist. Der Commit entfernt nur die Marker in `AGENTS.md` und `harness/README.md`. | `AGENTS.md` §3.7 | `docs/plan/planning/in-progress/slice-release-doku-prozess.md` · „`docs/user/` <!-- d-check:ignore (geplant: neues Verzeichnis) -->“ | ja — `make docs-check` bleibt grün, meldet es aber nicht | Marker für entstandene Datei bleibt stehen |
| F-6 | LOW | Der Implementer hat den Haken des Liefer-Punkts 1 gesetzt und den DoD-Text um „Behauptung des Implementers, Bestätigung beim Verifier“ erweitert. Ein Haken vor der Verifikation und eine Selbstbeschreibung im DoD-Wortlaut verwischen, wer den Punkt schließt. | Maintainability; Baseline `v6.18.1` · `regelwerk/modul-08-agentenrollen.md` | `docs/plan/planning/in-progress/slice-release-doku-prozess.md` · „Behauptung des Implementers, Bestätigung beim Verifier“ | nein | Haken vor Verifikation, Rollen-Text im DoD |
| F-7 | INFO | Die Nutzer-Doku sagt, der Integrator übergebe `UserList.parse(text, plainTestMode)`; der Rückgabetyp ist `UserListResult`, `AuthSettings` nimmt eine `UserList`. Wie aus dem Ergebnis die `UserList` wird, steht nicht da. Zuständig: Verifier/Implementer prüfen, ob der Satz für einen Integrator ausreicht. | `TDS-FA-08` | `docs/user/einbettung-und-betrieb.md` · „übergibt `UserList.parse(text, plainTestMode)`“ | nein | Beschriebener Aufruf ohne Rückgabetyp |
| F-8 | INFO | Die Schlüsseltabelle hält sich nur durch Lesen; kein Test gleicht sie mit `ServerConfig` ab. Die Doku benennt die Grenze selbst (letzter Punkt „Grenzen und nicht Belegtes“), der Plan führt sie als offenes Risiko (§6). Verwandt: `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`. Zuständig: Nutzer (Sensor ja/nein). | `TDS-QA-05` | `docs/user/einbettung-und-betrieb.md` · „halten sich nur durch Lesen“ | nein | Doku-Liste ohne Sensor gegen Code |
| F-9 | INFO | `releasing.md` ruft `curl` und `gh` auf dem Host. `AGENTS.md` §3.1 und der Skill nennen nur JDK, Gradle und Kotlin; die Abfragen sind keine Gates und die Datei nennt sie Handlungen. Kein Verstoß; die Host-Abhängigkeit über `bash`, `git`, `docker` hinaus besteht für diese Handlung (`harness/conventions.md` begründet das für den Freshness-Audit). | `AGENTS.md` §3.1 | `docs/maintainer/releasing.md` · „curl -s https://repo1.maven.org“ | nein | Host-Werkzeug in Handlung |

## Prüfpunkte der Anfrage

1. **Konfigurationstabelle** — alle 11 Schlüssel (`LISTEN_HOST`, `LISTEN_PORT`, `LOGIN_TIMEOUT`, `MAX_PRELOGIN_MESSAGE_BYTES`, `ENCRYPTION_POLICY`, `TLS_KEYSTORE_FILE`, `TLS_KEYSTORE_PASSWORD_FILE`, `AUTH_USERS_FILE`, `AUTH_PLAIN_TESTMODE`, `QUERY_TIMEOUT`, `CANCEL_TIMEOUT`) und die Feldzuordnung (`listen`, `login`, `requests`, `encryption`, `tls`, `auth`) stimmen mit `ServerConfig.kt`, `ConfigValidation.kt` und den Zeilen der Spezifikation überein: 0.0.0.0, 1433, 15 s, 65536, `REQUIRED`, 30 s, 5 s; Bereiche 0..65535, 1..3600, 512..65536, Host nicht leer, Paarung REQUIRED/TEST_UNENCRYPTED. Ohne Befund.
2. **„Gebaut“ gegen Code** — Metriken, Spans, `request_id`, `duration_ms`, `METRICS_INTERVAL_SECONDS`, `MAX_CONNECTIONS` stehen als nicht gebaut und sind im Code nicht vorhanden; `SHUTDOWN_GRACE` ist kein Eingang (Stop mit Frist 0, `Bootstrap`). Die genannten Ereignisarten (`EncryptionWeakened`, `ConfigInvalid`, `TlsStartRejected`, `BindFailed`, `StartRefused`, `LoginAccepted`, `RequestFailed`), Ebenen, Kürzung auf 256 und Clientprofil-Werte stimmen. Ohne Befund.
3. **Aussage ≤ Beleg** — ADR-0013-Zahlen sind als Spike-Messung mit Bedingungen gekennzeichnet; Setzungen sind benannt. Freshness-Audit (`v6.18.1` Latest) und detekt-Beobachtung (Metadaten, `alpha.6`) von mir nachgemessen und gleich. Zwei kleine Lücken: F-2, F-4. Ein Nachweis, dass die detekt-Abfrage bei `2.0.0` greift, habe ich nicht wiederholt; die Anleitung nennt ihn als Prüfung an einer Kopie.
4. **Regel 5 / Chronik** — F-3 (Nutzer-Doku), F-4 (Maintainer-Doku, Regel 5 gilt dort nicht wörtlich, §3.7 für Zustandsfelder).
5. **Entfernte Marker und Links** — Marker in `AGENTS.md` (Rang 6 und Regel 5) und `harness/README.md` (Rang 6) entfernt, Links lösen auf existierende Dateien auf; `README.md` ergänzt. F-5 für die im Slice-Plan verbliebenen Marker. Der Lauf von `make docs-check` ist nicht meiner.
6. **Entscheidungen, die dem Nutzer vorbehalten sind** — Versionsschema, Tag, Registry, Signierung, SBOM, `make e2e` im Release, Werkzeug-Target für den Wächter, Lizenzhinweise, Dateirechte: alle als offen geführt, keine gesetzt. Der Wächter als Schritt ist der Grundfall des Plans und als solcher ausgewiesen; der Ausgang beim Eintreten (neuer Slice mit `Supersedes`, ADR-0010 unverändert) folgt Plan und AGENTS §3.5. Nur F-2 setzt eine unausgewiesene Bedingung. Das DoD-Wortlaut-Problem ist F-1.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main` (`config`, `runtime`, `domain/telemetry`, `simulation`) gegen die Aussagen der Nutzer-Doku | geprüft, ohne Befund (außer F-7) |
| `spec/spezifikation.md` (SPEC-005/008/009/010/033/034/046/047/053) | geprüft, ohne Befund |
| `AGENTS.md`, `harness/README.md`, `README.md` | geprüft, ohne Befund |
| `docs/plan/adr/` (ADR-0010 Status Accepted, unverändert; ADR-0013, ADR-0018) | geprüft, ohne Befund; keine ADR im Diff geändert |
| Quelltext, `config/detekt/`, `build.gradle.kts`, `Dockerfile` | nicht im Diff; keine Suppression, keine Gate-Änderung |
| Schichtregeln, Protokollkern, Anmeldepfad | nicht berührt |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 1 |
| LOW | 5 |
| INFO | 3 |

**Finding-Klassen dieses Laufs:** DoD hängt an Nutzerentscheidung ohne Auflösungsweg · Festlegung ohne Kennzeichnung als Setzung · Planung oder Register-Zeiger in Nutzer-Doku · Zustandsangabe ohne Anker oder Sensor · Marker für entstandene Datei bleibt stehen · Haken vor Verifikation, Rollen-Text im DoD · Doku-Liste ohne Sensor gegen Code

## Verdikt

**Merge-blockierend:** nein für die gelieferten Dokumente (kein HIGH; die Nutzer-Doku ist gegen den Code getragen). F-1 blockiert das Schließen des Slice, nicht die Lieferung: Haken 2 und 3 brauchen die Nutzerentscheidung oder eine Änderung des DoD-Wortlauts durch den Architect.

**Übergabe:** Findings an den Implementer; F-1 zusätzlich an den Architect (Plan-Defekt, Rückkante Review → Plan). Die Finding-Klassen gehen in die Closure §7. Die DoD-Konformität prüft der Verifier.
