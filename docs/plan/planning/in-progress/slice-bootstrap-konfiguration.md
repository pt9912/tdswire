# Slice slice-bootstrap-konfiguration: Konfiguration, Bootstrap und Rohbytes-Smoke vom Prozessstart bis zum Login

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-handshake-login

**Bezug:** [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0002](../../adr/0002-tls-und-verschluesselung.md), [ADR-0003](../../adr/0003-transportframework.md), [ADR-0009](../../adr/0009-authentifizierungsmodus.md)

**Berührte Spec-Stellen:** SPEC-005 · SPEC-008 · SPEC-010 · SPEC-046 · ARC-002 · ARC-008 · [TDS-FA-09.a](../../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose)

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

**Ziel:** Der Serverprozess lädt eine Konfiguration (Endpunkt, Login-Frist, Verschlüsselungs-Richtlinie, Größengrenzen, Benutzerliste), validiert sie, verdrahtet im Composition Root (`runtime`) Listener, Handshake und Auth-Adapter, startet und stoppt; ein Rohbytes-Smoke belegt vom Prozessstart bis zur Login-Antwort den Login-Pfad der Welle.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Listener, Sockets, `SSLEngine`, Zertifikats- und Schlüsselkonfiguration, die Startprobe für TLS und die Diagnose für einen belegten Port (SPEC-043) — die geplante Transport-Welle `welle-transport-tls`; dieser Slice ruft deren Listener und hängt ihre Konfigurationsschlüssel an, wenn sie vorliegen.
- Geordnetes Beenden mit Frist (SPEC-044, `SHUTDOWN_GRACE`): Es gibt keine laufenden Anfragen; der Stop schließt Listener und Verbindungen ohne Frist-Logik. Träger: [slice-abbruch-ressourcen-shutdown](../open/slice-abbruch-ressourcen-shutdown.md).
- Observability-Adapter (strukturierte Logs, Metriken, Traces, Korrelations-IDs): nur die Start-, Stop- und Konfigurationsfehler-Meldungen und die Sichtbarkeit einer Abschwächung der Verschlüsselung im Log (SPEC-010). Träger: Welle [welle-betrieb-diagnose](../welle-betrieb-diagnose.md).
- Backend-Port und Simulations-Backend verdrahten — es gibt keine Anfragen; `runtime` darf das Simulations-Backend später verdrahten (ARC-016), tut es hier nicht.
- Smoke mit dem Referenzclient und ein Smoke mit TLS — `slice-qa-kompatibilitaet` bzw. die Transport-Welle; der Rohbytes-Smoke dieses Slice läuft im ausdrücklich konfigurierten unverschlüsselten Testmodus (SPEC-010), solange kein TLS-Listener vorliegt.
- Neu laden der Konfiguration zur Laufzeit und eine Administrationsoberfläche ([TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose) Out-of-Scope).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Konfiguration** ([TDS-FA-09.a](../../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose), SPEC-005, SPEC-008, SPEC-010, SPEC-046; Weg und Format nach der Entscheidung aus §4): laden, mit den Defaults der Spezifikation versehen (Port 1433, `LOGIN_TIMEOUT` 15 s, `ENCRYPTION_POLICY` `REQUIRED`, `MAX_PRELOGIN_MESSAGE_BYTES` 65536; die Werte der Spezifikation sind teils "vorläufig") und validieren: fehlende Pflichtoption, Wert außerhalb des Bereichs und eine fehlerhafte Benutzerliste (über die Validierung aus `slice-auth-adapter-benutzerliste`) brechen den Start ab; die Abschwächung der Verschlüsselung gibt es nur durch ausdrückliche Konfiguration und der Start-Log nennt sie ([TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)). Die Diagnose nennt Schlüssel und Art des Fehlers, nie ein Passwort oder einen Prüfwert, und keine automatisch erzeugte Textdarstellung der Konfiguration enthält sie (Test). Tests Happy/Boundary/Negative. Aus den offenen Punkten von [ADR-0011](../../adr/0011-pruefwertformat.md) trägt dieser Slice die Dateirechte und die Übergabe der Benutzerliste im Betrieb (nicht gemessen): der Nutzer entscheidet sie vor dem Start, oder die Closure führt sie als weiter offen mit Grund; die Schlüssel `AUTH_USERS_FILE` (Pfad, Umgebungsvariable) und `AUTH_PLAIN_TESTMODE` (nur der Wert `true` schaltet ein) sind dort gesetzt. Das Lesen der Benutzerliste gehört ebenfalls hierher (Vertrag `spec/spezifikation.md` `SPEC-051`): Der Parser aus `slice-auth-adapter-benutzerliste` bekommt einen Text, entfernt ein UTF-8-BOM am Dateianfang nicht (es würde Teil des ersten Benutzernamens) und lehnt jedes CR als Fehler ab; das Lesen entfernt oder weist das BOM ab (nicht entschieden, Test) und normalisiert die Zeilenenden nicht.
- [x] **Composition Root, `main`, Start und Stop** (ARC-008): `main` im Package `runtime` verdrahtet Konfiguration, Auth-Adapter, Handshake-Automat und den Listener der Transport-Welle; Start-Reihenfolge Konfiguration laden, validieren, (Startprobe der Transport-Welle), Listener binden; ein fehlgeschlagener Start endet mit Exit-Code ungleich null, ohne dass Threads oder Listener weiterlaufen (Test); Stop schließt Listener und offene Verbindungen. Der Start **prüft** `-Djdk.tls.server.newSessionTicketCount=0` (Wert der Systemeigenschaft, nicht die Wirkung auf dem Draht) und **setzt es nicht selbst** (`spec/spezifikation.md` `SPEC-023` §JVM-Flag); ein Test belegt die Prüfung (ein Prozess ohne das Flag startet nicht), und die Kind-JVM des Tests setzt das Flag. Ein Startartefakt, das das Flag setzt (Jar, `ENTRYPOINT`, Start-Skript), liefert dieser Slice nicht (§6). Start und Test laufen in Docker über ein `make`-Target (Name legt der Implementer fest und trägt es in `harness/README.md` §Sensors ein).
- [x] **Rohbytes-Smoke der Welle**: Ein per `main` mit Testkonfiguration gestarteter Serverprozess beantwortet einen Rohbytes-Client (JVM-Test, kein Referenzclient) in einem Lauf: PRELOGIN, gültiger LOGIN7 → LOGINACK-Folge; unbekannte Kennung und falsches Passwort → byte-gleiche Ablehnungen; SSPI-Merkmal, FeatureExt `0x02` und Passwortwechsel → abgelehnt; mehrpaketiger, byteweise zerstückelter LOGIN7 → Anmeldung; danach Stop ohne hängende Verbindung. Der Smoke ist Teil von `make gates`. Er belegt den Pfad mit dem Rohbytes-Client, nicht die Annahme durch `Microsoft.Data.SqlClient`.

Nachweis-Pflichten für alle drei Punkte:

- [x] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>` (Konvention nach `slice-qa-spec-abdeckung`); die Tests laufen in `make test` bzw. im Smoke-Target und sind grün.
- [x] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet; die Zuordnung *Test | Festlegung* dieses Slice steht in §7, die der Welle führt die Welle-Closure zusammen (Register, `BEO-TDS/aussagen-ueber-messung-hinaus`, siehe §8).
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
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
| `src/main/kotlin/io/github/pt9912/tdswire/config/` | update | Konfigurationsmodell, Laden, Validierung; erweiterbar um die TLS-Schlüssel der Transport-Welle |
| `src/main/kotlin/io/github/pt9912/tdswire/runtime/` <!-- d-check:ignore (geplant: neues Package) --> | neu | `main`, Composition Root, Start/Stop (ARC-008); das einzige Package, das alles verdrahten darf |
| `Dockerfile`, `tools/mk/` (neue Fragmente oder Ergänzung) | update/neu | Start des Serverprozesses und des Smokes in Docker, Hook in `make gates`; JVM-Optionen nach `spec/spezifikation.md` `SPEC-023`: gebaut ist nur die Prüfung im Start; das Setzen im Betrieb braucht ein Startartefakt (siehe §6) |
| `harness/README.md` | update | Zeile für das neue Target in §Sensors (sonst meldet das Doku-Gate `gate-undocumented`) |
| `spec/spezifikation.md` `SPEC-051` | update | Konfigurationsweg der Benutzerliste, sobald entschieden und gebaut |
| `src/test/kotlin/io/github/pt9912/tdswire/runtime/` <!-- d-check:ignore (geplant: neues Package) --> | neu | Konfigurations-Happy/Boundary/Negative, Startabbruch, Rohbytes-Smoke; nach SPEC-005, SPEC-008, SPEC-010, [TDS-FA-09.a](../../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose); `@spec`-Tag je Test |
| `gradle/libs.versions.toml`, `gradle.lockfile`, `gradle/verification-metadata.xml` | update, nur falls das Konfigurationsformat eine Abhängigkeit braucht | per `make deps-lock` (Netz), nicht von Hand; `kotlinx-coroutines-core` kommt mit dem Transport-Code ([ADR-0003](../../adr/0003-transportframework.md)) |
| `src/main/kotlin/io/github/pt9912/tdswire/transport/channel/SwitchableChannel.kt` | neu | Kanal mit einmaligem Wechsel vom rohen zum verschlüsselten Ziel; Leser und Schreiber des Handlers bleiben beim TLS-Handschlag unverändert (Ist-Zustand im ersten Lauf: `TransportHandshakeChannel` hält einen festen Kanal) |
| `src/main/kotlin/io/github/pt9912/tdswire/config/` (`ServerConfig`, `ConfigLoader`, `ConfigFiles`) | neu | Schlüssel, Bereiche, Lesen der Dateien; `config` darf nur `domain` importieren, deshalb eigene Richtlinien-Aufzählung statt `EncryptionPolicy` |
| `Dockerfile` (Stage `smoke`), `tools/mk/smoke.mk` | neu | `make smoke` als engster Sensor; `ServerSmokeTest` läuft auch in der Stage `test`, daher kein zweiter Eintrag in `GATE_CHECKS` |
| `spec/spezifikation.md` ([Sicherheitsprofil](../../../../spec/spezifikation.md#sicherheitsprofil-vertrag-zu-spec-023), [Authentifizierungsadapter](../../../../spec/spezifikation.md#authentifizierungsadapter-vertrag-zu-spec-051)) | update | Schlüssel für den TLS-Speicher, Flag-Prüfung beim Start |

Der Implementer-Agent hat die Liste im ersten Lauf erweitert (Zeilen oben).

- Reihenfolge: Konfiguration zuerst (mit Tests ohne Prozess), dann `main` mit Start/Stop gegen den Listener, zuletzt der Smoke.
- Die Konfigurationsschlüssel und das Modell gehören diesem Slice; die TLS-Schlüssel gehören der Transport-Welle. Wer das Modell zuerst anlegt, gibt es dem anderen (siehe §6).
- `make detekt` läuft auch über `detektTest`; jeder Befund in neuem Code wird im Code behoben, nie unterdrückt (`AGENTS.md` §3.2, §3.6).
## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Der Start-Trigger der Welle `welle-handshake-login` ist eingetreten, `slice-handshake-login` liegt in `done/` (der Auth-Slice `slice-auth-adapter-benutzerliste`, dessen Validierung dieser Slice ruft, liegt bereits in `done/`), der Listener-Slice der Transport-Welle `welle-transport-tls` liegt in `done/` (Kennung legt deren Planung fest), `slice-adr-pruefwertformat` liegt in `done/` (Konfigurationsweg und Format sind entschieden: Nutzerentscheidung, festgehalten als `Accepted`-ADR oder als Fortschreibung von SPEC-051 und `spec/spezifikation.md` `SPEC-051`) und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2. Schnittvorschlag: Slice C1 = Konfiguration, Slice C2 = `main`, Start/Stop und Smoke; die Welle bekommt dann vier Slices.
- `in-progress` → `open` (blockiert — Carveout?): Der Listener der Transport-Welle bietet keine Schnittstelle, an der ein Nachrichten-Handler hängen kann, oder das Konfigurationsformat ist nicht entschieden.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün (der Smoke läuft darin), der Review-Report liegt vor, und die Closure-Notiz trägt den Steering-Loop-Lerneintrag (geschärfte Regel, neuer Sensor oder benannte Spec-Lücke; eine Closure ohne Lerneintrag ist keine). Als Slice mit dem Welle-Smoke trägt seine Notiz die Verbindung zur Welle-Closure: welche Aussagen *gemessen* (Smoke, Test) und welche *Festlegung* sind.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Konfigurationsweg und Format sind in [ADR-0011](../../adr/0011-pruefwertformat.md) (`Accepted`) entschieden: eine eigene Datei nur für die Benutzerliste (`AUTH_USERS_FILE`), eigenes Zeilenformat mit eigenem Parser, Testmodus-Schalter `AUTH_PLAIN_TESTMODE`; es kommt keine neue Abhängigkeit hinzu. Das Konfigurationsmodell bleibt in diesem Slice, den Prüfwert parst und validiert `slice-auth-adapter-benutzerliste`. Die Dateirechte und die Übergabe im Betrieb sind in der ADR offen und stehen in der DoD dieses Slice. — **Ausgang:** entfallen für Weg und Format: gebaut und getestet (`AUTH_USERS_FILE`, `AUTH_PLAIN_TESTMODE`, `ConfigLoaderTest`, `ConfigUsersFileTest`); Dateirechte und Übergabe führt das Risiko „Prüfwerte stehen in der Konfiguration“ unten.
- Überschneidung mit der Transport-Welle: Konfigurationsmodell und Schlüssel (Port, Bind-Adresse, TLS), die Startprobe und das Setzen der JVM-Option `-Djdk.tls.server.newSessionTicketCount=0` (`spec/spezifikation.md` `SPEC-023`: "beim Start des Serverprozesses zu setzen") berühren beide Wellen. Die Zuordnung ist mit der Transport-Planung abzustimmen; der Plan nimmt an, dass das Konfigurationsmodell und der Start des Prozesses hier liegen und die Transport-Welle ihre Schlüssel und die Startprobe einhängt. — **Ausgang:** entfallen: Modell, Lader und Start liegen hier, die Startprobe des Listeners läuft vor dem Binden (Test, Mutation M19 rot), die TLS-Schlüssel hängen am Modell; das Flag prüft der Start und setzt es nicht (nächstes Risiko).
- Kein Startartefakt setzt das Flag: Es gibt kein Jar mit Startklasse und kein Laufzeit-Image, das den Serverprozess mit `-Djdk.tls.server.newSessionTicketCount=0` startet; gebaut ist die Prüfung beim Start und das Setzen in der Kind-JVM des Tests. Die Zusage aus `SPEC-023`, das Flag "beim Start des Serverprozesses zu setzen", ist für den Betrieb nicht belegt. Träger: [slice-release-artefakt-lizenz](../open/slice-release-artefakt-lizenz.md) liefert Jar, Startklasse und Laufzeit-Image, führte das Flag bei der Planung nicht in DoD oder Risiko; die Closure hat die DoD-Zeile dort ergänzt (Laufzeit-Image: ein Start- oder Laufzeitartefakt setzt das Flag, die Prüfung des Starts bleibt). — **Ausgang:** weiter offen: Träger `slice-release-artefakt-lizenz` (DoD-Punkt 1); Register `BEO-TDS/offene-punkte-ohne-traeger` (Träger fehlte bis zur Closure).
- Einordnung einer Abbruchausnahme am echten Handler (gemeldet von [slice-transport-listener-lebenszyklus](../done/slice-transport-listener-lebenszyklus.md) §6): `ManagedConnection.finish` ordnet eine `CancellationException`, die der Handler bei unabgebrochenem Scope wirft, als `HANDLER_FAILED` ein (Setzung, an Test-Handlern gemessen, vom Nutzer nicht bestätigt). Ob der Handshake-Handler mit der Anbindung dieses Slice so reagiert, wie die Setzung es annimmt, ist ungemessen; der Rohbytes-Smoke prüft es mit einem Handler, der von selbst abbricht, oder die Setzung wird hier benannt geändert. — **Ausgang:** weiter offen: gemessen ist, dass der Handshake-Handler eine Abbruchausnahme des Adapters als Adapterfehler beantwortet und mit `HANDLER_RETURNED` endet, nie mit `HANDLER_FAILED` (`HandshakeWiringTest`, Mutation M14 rot); die Setzung `HANDLER_FAILED` für eine Abbruchausnahme eines Handlers bei unabgebrochenem Scope bleibt vom Nutzer unbestätigt, Register `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`. Der Slice ändert die Setzung nicht.
- Der Smoke ist ein Rohbytes-Client, kein Referenzclient: Er belegt, was der Server auf handgebauten Bytes antwortet, nicht, dass `Microsoft.Data.SqlClient` die Antwort annimmt. Die Aussage "Login funktioniert" darf in Notiz und Doku nicht ohne diese Einschränkung stehen (`BEO-TDS/aussagen-ueber-messung-hinaus`). — **Ausgang:** weiter offen: die Einschränkung steht in Notiz, KDoc und `make smoke`; Träger der Messung am Referenzclient ist `slice-qa-kompatibilitaet` (DoD-Punkt 1 und 2); Register `BEO-TDS/aussagen-ueber-messung-hinaus`.
- Wie der Smoke in Docker läuft (Prozess und Client im Test-Container über Loopback oder eigener Container im Netz), ist nicht entschieden; Docker-only (`AGENTS.md` §3.1) und die Basis-Images per Digest sind einzuhalten. — **Ausgang:** entfallen: entschieden und gebaut: Stage `smoke`, Kind-JVM im Test-Container über Loopback, Basis-Images unverändert per Digest.
- Prüfwerte stehen in der Konfiguration: Dateirechte, Übergabe in den Container und Anzeige in Diagnose und Log sind Teil der Sicherheitsaussage ([TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)); ein Test belegt nur die Textdarstellung, nicht die Dateirechte. — **Ausgang:** weiter offen: die Anzeige ist belegt (Textdarstellungen „verborgen“, Diagnose ohne Wert); Dateirechte und Übergabe im Betrieb sind weder gemessen noch vom Nutzer entschieden ([ADR-0011](../../adr/0011-pruefwertformat.md) §Offen). `slice-qa-sicherheit` trägt sie nicht (seine DoD nennt Log, Zeitgleichheit, TLS); `slice-release-artefakt-lizenz` führt die Übergabe in den Container (Risiko ergänzt in dieser Closure) und braucht die Entscheidung des Nutzers; Register `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`.
- Die Defaults `LOGIN_TIMEOUT`, `MAX_PRELOGIN_MESSAGE_BYTES` und `MAX_MESSAGE_BYTES` sind in der Spezifikation "vorläufig"; der Slice übernimmt sie als Festlegung und misst sie nicht. — **Ausgang:** weiter offen: sie bleiben Festlegung (Zuordnung in §7); `MAX_MESSAGE_BYTES` ist nicht konfigurierbar (der Code kennt nur `MAX_PRELOGIN_MESSAGE_BYTES`), Träger des Schlüssels ist `slice-betrieb-zustandsereignisse` (Konfigurationsabdeckung); Register `BEO-TDS/aussagen-ueber-messung-hinaus`.
- Ein `AuthPool` je Prozess ([slice-handshake-login](../done/slice-handshake-login.md)): Die Obergrenze der gleichzeitigen Adapteraufrufe gilt je `AuthPool`-Instanz; `HandshakeDriver` baut ohne Übergabe einen eigenen Pool mit der Voreinstellung. Die Verdrahtung gibt allen Treibern des Prozesses denselben Pool, sonst gilt die Obergrenze je Treiber (und bei einem Treiber je Verbindung gar nicht); ein Test der Verdrahtung belegt, dass zwei Verbindungen einen Pool teilen. Die Zahl der Plätze ist nicht gemessen (Träger `slice-qa-sicherheit`). Der Fristbeginn von `LOGIN_TIMEOUT` liegt beim Aufruf von `HandshakeDriver.run`, nicht bei der TCP-Annahme (SPEC-008 sagt „ab Verbindungsaufbau“): die Verdrahtung ruft den Treiber ohne Verzug nach der Annahme. — **Ausgang:** entfallen: die Verdrahtung ist gemessen (zwei Verbindungen teilen einen Pool, Mutation M08 rot; die Frist des Treibers trägt den konfigurierten Wert, Mutation X1 rot; die Frist des Listeners beginnt mit der Annahme, Mutation M09 rot). Die Zahl der Plätze bleibt ungemessen und liegt schon bei `slice-qa-sicherheit` (DoD-Punkt 2; Register `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`).
- Größe: drei Liefer-Punkte, davon ein Smoke über den ganzen Pfad. — **Ausgang:** entfallen: die Rückführung nach §4 war nicht nötig.

Bei der Closure hinzugekommen (Review F-5 bis F-7, F-9, Verifikation):

- Die Stage `smoke` und die Stage `test` führen `ServerSmokeTest` beide (drei Kind-JVMs mehr je `make gates`); `Dockerfile` und `smoke.mk` benennen es (Review F-5). — **Ausgang:** entfallen, angenommen: die Laufzeit ist der Preis eines eigenen engsten Sensors; kein Register-Eintrag, weil der Test in `make gates` läuft und nichts unbelegt bleibt.
- Herkunftsstufen des Smoke (Review F-6): Rohbytes-Client auf dem Klassenpfad der Test-JVM, Modus `TEST_UNENCRYPTED`; der TLS-Pfad ist nur in `TlsWiringTest` belegt, nicht im `main`-Prozess. — **Ausgang:** entfallen: INFO ohne Handlungsbedarf, die Einschränkung steht in Notiz und KDoc; TDS 8.0 ist nicht verdrahtet (`HandshakeConnectionHandler`, KDoc), siehe das Risiko darunter.
- Das Keystore-Kennwort und der Text der Benutzerliste liegen als `String` und `ByteArray` im Heap, nur das `char[]` wird überschrieben (Review F-7); keine Textdarstellung zeigt sie. — **Ausgang:** weiter offen, angenommen als Grenze: die Spezifikation sagt dazu nichts, die Aussage „Geheimnis überschrieben“ gilt nur für das `char[]`; Register `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`.
- Der offene `RawClient` im Fehlerpfad von `Serverprozess beantwortet den Login-Pfad mit Rohbytes` wird nicht geschlossen, die zweite Hälfte von Review F-9. — **Ausgang:** entfallen, angenommen: mit der Kind-JVM endet auch die Gegenstelle, kein Fehler beobachtet (INFO).
- TDS 8.0 (TLS ab dem ersten Byte) ist nicht verdrahtet; Pooling ist im Smoke nicht geprüft. — **Ausgang:** weiter offen: Träger `slice-qa-kompatibilitaet` (DoD-Punkt 2); Register `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`.
- `SHUTDOWN_GRACE` hat keinen Konfigurationsschlüssel; `Bootstrap` übergibt eine Frist von 0 s (Festlegung), die Voreinstellung der Spezifikation (`SPEC-047`) ist 10 s (Review F-4). — **Ausgang:** weiter offen: Träger `slice-abbruch-ressourcen-shutdown` (DoD-Punkt 3 und Plan: Frist aus der Konfiguration).
- Die Frist des Treibers läuft in Echtzeit; der Test `Die Frist des Treibers trägt den konfigurierten Wert` hängt an einem Zeitfenster von 5 s gegen eine Frist von 1 s und ist unter starker Last der naheliegendste Kandidat für ein Flackern (Vermutung, nicht gemessen). — **Ausgang:** weiter offen: kein Flackern beobachtet; Register `BEO-TDS/einmal-rot-ohne-gesicherte-ausgabe`.
- Ein Test der Fix-Runde war einmal rot, die Ursache wurde nicht erfasst; vier Läufe der Verifikation (und die ganze Stage `test`, 500 Tests) blieben grün. — **Ausgang:** weiter offen: Flake ohne Beleg, weder behauptet noch ausgeschlossen; Register `BEO-TDS/einmal-rot-ohne-gesicherte-ausgabe`; beim nächsten roten Lauf die Ausgabe sichern, bevor neu gestartet wird.

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

- **Was hat funktioniert:** Der Schnitt trug, die Rückführung nach §4 war nicht nötig: Konfiguration, Composition Root und Smoke lagen in einem Slice und ließen sich einzeln mutieren. Der Verifier fuhr neun Mutationen selbst, alle rot (M07 Flag-Prüfung, M08 gemeinsamer Pool, M09 Frist des Listeners, M11 `SwitchableChannel`, M14 Abbruchausnahme, M19 Startprobe, M21 Stop, X1 Frist des Treibers, F-8 als Kompilierfehler); der Implementer hatte vorher weitere gefahren (Angabe des Implementers, die Liste liegt nicht im Repo). Der Review fand mit X1 eine überlebende Mutation, die die Fix-Runde mit einem eigenen Test schloss. Die Stage `test` lief frisch ohne Cache: 500 Tests bestanden, 0 fehlgeschlagen.
- **Was ging anders als geplant:** (a) Der Plan sagte, der Start setze `-Djdk.tls.server.newSessionTicketCount=0`; gebaut ist nur die Prüfung, ein Startartefakt gibt es nicht. Die Spezifikation wurde auf „prüft, setzt nicht“ umgeschrieben, der Plan blieb stehen (Review F-1); die Fix-Runde hat Plan und Risiko nachgezogen. (b) Das KDoc von `ServerLog` sagte, eine Zeile nenne nie einen Konfigurationswert; Port und Adresse beim Bindefehler sind einer (Review F-2). (c) Die Frist des Treibers war mit keinem konfigurierten Wert belegt (Review F-3, Mutation X1); der neue Test `Die Frist des Treibers trägt den konfigurierten Wert` färbt X1 rot, und zwar genau ein Test. (d) `shutdownGrace` 0 stand ohne Kennzeichnung gegen die Voreinstellung von 10 s (Review F-4); das KDoc von `Bootstrap` nennt jetzt die Festlegung. (e) `ConfigIssue.detail` ist kein freier Text mehr, sondern der Typ `UserListError` (Review F-8); eine Diagnose mit Passwort lässt sich nicht mehr übersetzen. (f) `ServerProcess.close` wartet nach `destroyForcibly` (Review F-9, erste Hälfte). (g) Die Plan-Tabelle bindet [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung) und [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), die Commits der Umsetzung und der Fix-Runde zitieren [TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände) (Verifikation Abschnitt 8, INFO); der Commit-Hook prüft nur das Vorhandensein einer Kennung, die Rückverfolgung der Commits zur Anforderung des Plans ist dadurch schwächer. (h) `docs/plan/spec-abdeckung.md` ist in dieser Closure mit `make spec-abdeckung` neu erzeugt (die Tests des Repos liefen frisch); 17 Festlegungen stehen dort ohne Nachweis.
- **Reports:** [Review](../../../reviews/2026-10-30-d5b0270-slice-bootstrap-konfiguration.md) (0 HIGH; F-1 bis F-3 MEDIUM, F-4 und F-5 LOW, F-6 bis F-9 INFO) gegen den Merge `d5b0270`, und [Verifikation](../../../reviews/2026-10-30-dcb6b27-verifikation-slice-bootstrap-konfiguration.md) am Stand `dcb6b27`, die zugleich die Fix-Runde `61141fd` nachverifiziert (F-1 bis F-4 und F-8 behoben, F-9 zur Hälfte; F-5 bis F-7 nicht angefasst). Die Fix-Runde hat keinen eigenen Nach-Review; die Nachverifikation ersetzt ihn nicht. Der Verifier fuhr `make gates` nicht vollständig (Last), nur `make detekt`, die Stage `test` und `make docs-check`; `make gates` lief in dieser Closure vor jedem Inhalts-Commit und nach dem Move. Ein Validator-Lauf entfällt: der Slice liefert keinen Nutzerwert außerhalb der Tests, der Server beantwortet nur den Rohbytes-Client; die Validierung gehört hinter einen Referenzclient-Lauf.
- **Laufzeit von `make test`:** nicht gemessen; der `ServerSmokeTest` startet drei Kind-JVMs und läuft in der Stage `smoke` und in der Stage `test` (Review F-5, angenommen).

**Zuordnung gemessen | Handvektor | Selbstkonsistenz | Festlegung | nicht gemessen je Aussage.** *Gemessen* heißt beobachtet in einem Lauf dieses Repos, am Test-Kanal, am Test-Adapter und am Rohbytes-Client in der Test-JVM; **gegen den Referenzclient und gegen einen echten SQL Server wurde in diesem Slice nichts gemessen.** *Handvektor* heißt ein Vektor mit Quelle; dieser Slice schreibt keinen. *Selbstkonsistenz* heißt Code gegen Code oder Code gegen Spezifikation gelesen. *Festlegung* heißt Setzung ohne Messung.

| Stärke | Aussage | Beleg im Repo |
|---|---|---|
| gemessen | Der Lader wendet die Voreinstellungen an (Port 1433, `LOGIN_TIMEOUT` 15, `MAX_PRELOGIN_MESSAGE_BYTES` 65536, `ENCRYPTION_POLICY` `REQUIRED`, Host `0.0.0.0`), lehnt Werte außerhalb der Bereiche ab und nennt Schlüssel und Art des Fehlers; fehlende Pflichtoption, ungültiger Wert und fehlerhafte Benutzerliste brechen den Start ab | `ConfigLoaderTest`, `ConfigUsersFileTest`, `BootstrapTest`; Happy, Boundary, Negative. Die Werte und Bereiche selbst sind Festlegung (unten) |
| gemessen | Keine Diagnose, kein Log und keine Textdarstellung von `ServerConfig`, `AuthSettings`, `TlsKeyMaterial` enthält Passwort, Prüfwert, Pfad oder Benutzernamen; die Diagnose der Benutzerliste trägt Art und Zeilennummer als Typ `UserListError`, freier Text ist nicht mehr übersetzbar | Tests der Pakete `config` und `runtime`; Verifikation F-2, F-8 (Kompilierfehler der Mutation). Ausnahme, benannt: gebundener Port und bei Bindefehler Adresse und Port stehen im Log |
| gemessen | Der Start prüft die Systemeigenschaft `jdk.tls.server.newSessionTicketCount` zuerst (vor Laden und TLS-Material); ein Prozess ohne `0` startet nicht (Exit-Code 1); die Kind-JVM des Tests setzt das Flag | `BootstrapTest`, `ServerSmokeTest`; M07 rot. Gemessen ist der Wert der Eigenschaft, nicht die Wirkung auf dem Draht |
| gemessen | Ein fehlgeschlagener Start (Konfiguration, Speicher, Startprobe, belegter Port) endet mit Exit-Code 1 ohne weiterlaufende Threads oder Listener; der Stop gibt Port und Threads frei und trennt angemeldete Verbindungen | `BootstrapTest`, `ServerSmokeTest`, `HandshakeWiringTest`; M19, M21 rot |
| gemessen (Rohbytes-Client, Kind-JVM, `TEST_UNENCRYPTED`) | Ein per `main` gestarteter Prozess beantwortet PRELOGIN und gültiges LOGIN7 mit der LOGINACK-Folge; unbekannte Kennung und falsches Passwort erhalten byte-gleiche Ablehnungen; SSPI-Merkmal, FeatureExt `0x02` und Passwortwechsel werden abgelehnt; ein LOGIN7 in 20-Byte-Paketen führt zur Anmeldung; SIGTERM beendet den Prozess ohne hängende Verbindung | `ServerSmokeTest` (3 Tests), Teil von `make gates` über die Stage `test`; **nicht** am Referenzclient: die Aussage „Login funktioniert“ gilt nur mit dieser Einschränkung |
| gemessen | Zwei Verbindungen teilen sich einen `AuthPool`; die Frist des Listeners beginnt mit der Annahme; die Frist des Treibers trägt den konfigurierten Wert; eine Abbruchausnahme des Adapters ist ein Adapterfehler und endet als `HANDLER_RETURNED` | `HandshakeWiringTest`; M08, M09, X1, M14 rot. Die Frist des Treibers läuft in Echtzeit (1 s gegen ein Fenster von 5 s) |
| gemessen (JDK-Gegenstelle in der Test-JVM, nicht im `main`-Prozess) | `SwitchableChannel` wechselt einmal vom rohen zum verschlüsselten Ziel; der TLS-Handschlag mit `REQUIRED` läuft über den Wechsel | `SwitchableChannelTest`, `TlsWiringTest`; M11 rot |
| Selbstkonsistenz | Schlüssel, Bereiche und Voreinstellungen im Lader stimmen mit der Tabelle in Abschnitt 3 der Spezifikation und den Schlüsseln aus [ADR-0011](../../adr/0011-pruefwertformat.md) überein (`AUTH_USERS_FILE`; `AUTH_PLAIN_TESTMODE` nur genau `true`, `TRUE` ist ein Fehler) | gelesen vom Reviewer (Prüfpunkt 9) und vom Verifier (DoD 1); keine Messung |
| Festlegung | Wertebereiche 0..65535, 1..3600, 512..65536; das Lesen der Benutzerliste weist ein UTF-8-BOM ab und normalisiert die Zeilenenden nicht; ein leerer Wert von `ENCRYPTION_POLICY` ist ungültig; `TEST_UNENCRYPTED` mit einem TLS-Schlüssel ist ein Konflikt, `REQUIRED` ohne Schlüssel fehlt | KDoc, `spec/spezifikation.md` (Abschnitt 3 und Abschnitt 6, `SPEC-051`); Tests pinnen das Verhalten, nicht seine Richtigkeit |
| Festlegung | Die Voreinstellungen `LOGIN_TIMEOUT`, `MAX_PRELOGIN_MESSAGE_BYTES` sind in der Spezifikation „vorläufig“; `MAX_MESSAGE_BYTES` ist nicht konfigurierbar | Spezifikation Abschnitt 3; Register `BEO-TDS/aussagen-ueber-messung-hinaus` |
| Festlegung | `shutdownGrace` ist 0 s (nicht die Voreinstellung 10 s von `SPEC-047`); nach dem Login ohne Backend wartet der Handler auf die nächste Nachricht und kehrt zurück, eine Anfrage bleibt unbeantwortet; die angekündigte Packet Size wird nicht angewandt | KDoc von `Bootstrap`, `HandshakeConnectionHandler` |
| Festlegung (vom Nutzer unbestätigt) | Eine Abbruchausnahme, die der Handler bei unabgebrochenem Scope wirft, ordnet `ManagedConnection.finish` als `HANDLER_FAILED` ein | gemessen an Test-Handlern im Listener-Slice, hier nicht verändert; Register `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` |
| nicht gemessen | Annahme der Antworten durch `Microsoft.Data.SqlClient`; TLS im `main`-Prozess; TDS 8.0 (nicht verdrahtet) und Pooling; Dateirechte und Übergabe der Benutzerliste im Betrieb; die Wirkung von `-Djdk.tls.server.newSessionTicketCount=0` auf dem Draht und ein Startartefakt, das es setzt; Kopien des Keystore-Kennworts und der Benutzerliste im Heap (nur das `char[]` wird überschrieben); `SHUTDOWN_GRACE` mit Frist; Last; Flake-Freiheit des Smoke (vier Läufe des Verifiers, ein Rot der Fix-Runde ohne erfasste Ursache) | Register und Träger in der Tabelle unten |

**Offene Punkte der Closure mit Träger** (gegen den Inhalt der Dateien geprüft):

| Offener Punkt | Träger | Stand |
|---|---|---|
| Kein Start- oder Laufzeitartefakt setzt das Flag | `slice-release-artefakt-lizenz` (`open/`) | DoD-Punkt 1 (Laufzeit-Image) nennt seit dieser Closure: ein Start- oder Laufzeitartefakt setzt `-Djdk.tls.server.newSessionTicketCount=0`, die Prüfung des Starts bleibt |
| Dateirechte und Übergabe der Benutzerliste im Betrieb ([ADR-0011](../../adr/0011-pruefwertformat.md) §Offen) | `slice-release-artefakt-lizenz` (`open/`) | ein Risiko in §6 seit dieser Closure (Übergabe in den Container, Dateirechte); `slice-qa-sicherheit` trägt sie nicht; die Entscheidung liegt beim Nutzer |
| TDS 8.0, Pooling, Annahme durch den Referenzclient | `slice-qa-kompatibilitaet` (`open/`) | DoD-Punkte 1 und 2 |
| `SHUTDOWN_GRACE` ohne Schlüssel, Frist des Stops | `slice-abbruch-ressourcen-shutdown` (`open/`) | DoD-Punkt 3 und §3 (Frist aus der Konfiguration, `SPEC-047`) |
| `MAX_MESSAGE_BYTES` ohne Schlüssel | `slice-betrieb-zustandsereignisse` (`open/`) | DoD „Konfigurationsabdeckung“ |
| Plätze des `AuthPool`, Zeitgleichheit, Drosselung | `slice-qa-sicherheit` (`open/`) | DoD-Punkt 2 |
| Setzung `HANDLER_FAILED` für die Abbruchausnahme | kein Träger-Slice; Register `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` | Frage an den Nutzer |
| Kopien des Keystore-Kennworts im Heap | kein Träger-Slice; Register `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen` | angenommen als Grenze (Review F-7) |

- **Gegenstand:** entfällt (der Slice lieferte).
- **Steering-Loop-Eintrag:** Kein `liegt in`: mit diesem Slice ist keine Regel, kein Sensor und keine Spec-Zeile verkörpert worden; gezählt wird, nicht verkörpert. Fünf Feststellungen. (1) **Eine Abweichung vom Wortlaut des Plans gehört im selben Zug in den Plan.** Der Implementer setzte die Spezifikation auf „prüft, setzt nicht“ und ließ im Plan „setzt“ stehen (Review F-1); erst der Review machte daraus ein Risiko mit Träger, und der Träger führte das Flag nicht (Verifikation 5.2). Das ist ein weiteres Auftreten von `BEO-TDS/aussagen-ueber-messung-hinaus` und von `BEO-TDS/offene-punkte-ohne-traeger` (verkörpert in `.claude/commands/implement-slice.md` Schritt 24 nur für Entscheidungs-Slices; ein weiteres Auftreten in einem Implementierungs-Slice, die Frage an den Nutzer, ob der Absatz ausgedehnt wird, bleibt offen). (2) **Doppel-Timer, nur eine Hälfte belegt:** die Frist des Treibers blieb bei jeder Änderung grün (Mutation X1), weil die Tests nur den Wert des Listener-Timers an `ManualDelayer` prüften; ein weiteres Auftreten von `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test`, jetzt 4× und über der Schwelle, nicht verkörpert; der Sensor war der Review per Mutation. (3) **Ein Typ schließt aus, was ein Test nur erkennt:** `ConfigIssue.detail` als `UserListError` statt `String` macht die Mutation „Diagnose mit Passwort“ zu einem Kompilierfehler; das ist stärker als die vier Tests, die vorher rot wurden. Keine Registerklasse, ein Hinweis für künftige Diagnose-Typen. (4) **Benannte Spec-Lücke:** `SPEC-023` verlangt, dass der Serverprozess das Flag trägt („der Aufruf des Prozesses setzt es“); es gibt keinen Aufruf im Repo, der es tut, belegt ist nur die Prüfung im Start. Die Spezifikation ist wahr (sie sagt „Setzung“ und nennt den Prüfumfang), die Lücke liegt im Artefakt; Träger ist `slice-release-artefakt-lizenz`. (5) **`BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen` erreicht mit diesem Slice 3×** (Keystore-Kennwort und Listentext als `String` und `ByteArray` im Heap): über der Schwelle, nicht verkörpert; der Eintrag geht in den Lese-Schritt der Welle-Closure von `welle-handshake-login`. Zusätzlich erreichen `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` (jetzt 5×) und `BEO-TDS/strukturtest-ohne-variation-der-eingabe` (unverändert 4×) die Schwelle schon seit früheren Slices; sie bleiben über der Schwelle, nicht verkörpert.
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-bootstrap-konfiguration.md` ergänzt in `BEO-TDS/aussagen-ueber-messung-hinaus` (Review F-1, F-2; Verifikation 5.2), `BEO-TDS/offene-punkte-ohne-traeger` (Verifikation 5.2), `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (Review F-3), `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen` (Review F-7), `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` (`HANDLER_FAILED`), `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` (TDS 8.0 nicht verdrahtet) und `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` (Dateirechte und Übergabe; der Stand nennt jetzt den Träger dafür). Eine neue Beobachtung mit dieser Evidence-Datei: `BEO-TDS/einmal-rot-ohne-gesicherte-ausgabe` (ein roter Lauf der Fix-Runde, Ursache nicht erfasst; die Frist des Treibers in Echtzeit ist der Kandidat, nicht gemessen). Kein Auftreten: `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler`, `BEO-TDS/test-belegt-nur-den-fake`, `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` (`make docs-check` ohne Befund) und die übrigen. Die Zähler folgen aus den Dateien.
- **Risiken aus §6:** alle achtzehn tragen genau einen Ausgang (siehe §6): acht entfallen (davon zwei angenommen), zehn bleiben weiter offen, jeder mit Träger-Slice oder Register-Eintrag.
- **Drei Paarungen:** Anker: kein Eintrag trägt `liegt in`, die Paarung entfällt. Folge-Slice: die Träger der Tabelle oben existieren in `open/` und führen die Punkte in DoD oder Risiko (gegen die Dateien geprüft, die DoD-Zeile und das Risiko in `slice-release-artefakt-lizenz` kamen mit dieser Closure). Register: eine Evidence-Datei dieses Slice in sieben bestehenden Klassen und eine neue Beobachtung.
- **Für die Welle-Closure von `welle-handshake-login`:** mit diesem Slice liegen alle vier Slices der Welle in `done/`. Das Mehr des Closure-Triggers (Rohbytes-Smoke gegen einen über den Bootstrap gestarteten Prozess: gültiger Login, byte-gleiche Ablehnung, abgelehnte Modi, mehrpaketiges LOGIN7) belegt `ServerSmokeTest`; die Zuordnung der Welle führt die Welle-Closure aus den Tabellen der Slices zusammen.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Die Pakete `runtime` und `config` ist ein Pfad-Cluster (Achse 3), trägt aber keine eigene Strukturregel (Achse 1) und keine eigene Inventur-Linie (Achse 2): eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, acht Beobachtungen, Stand des gemergten `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/adapter-festlegungen-ohne-slice` — 1×, offen. Treffer: Der Konfigurationsweg des Adapters ist offen; dieser Slice und `slice-auth-adapter-benutzerliste` tragen ihn.
- `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` — 1×, offen. Treffer: Sicherheitsaussagen zur Konfiguration (Prüfwerte nicht im Log); Zeitgleichheit und Drosselung bleiben bei `slice-qa-sicherheit`.
- `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` — 2×, offen. Treffer: Der Smoke läuft ohne TLS und ohne Pool; TDS 8.0 ist ungemessen und nicht Teil des Smokes.
- `BEO-TDS/aussagen-ueber-messung-hinaus` — 6×, Stand *geplant*, Träger `welle-entscheidungen-grundlage`. Treffer: Smoke ≠ Referenzclient (§6); Zuordnung *Test | Festlegung*.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Treffer: `ADR-*`- und `TDS-*`-Kennungen stehen als Link.
- Ohne Treffer: `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/vergleichslauf-d-ungeklaert`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
