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

- [ ] **Konfiguration** ([TDS-FA-09.a](../../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose), SPEC-005, SPEC-008, SPEC-010, SPEC-046; Weg und Format nach der Entscheidung aus §4): laden, mit den Defaults der Spezifikation versehen (Port 1433, `LOGIN_TIMEOUT` 15 s, `ENCRYPTION_POLICY` `REQUIRED`, `MAX_PRELOGIN_MESSAGE_BYTES` 65536; die Werte der Spezifikation sind teils "vorläufig") und validieren: fehlende Pflichtoption, Wert außerhalb des Bereichs und eine fehlerhafte Benutzerliste (über die Validierung aus `slice-auth-adapter-benutzerliste`) brechen den Start ab; die Abschwächung der Verschlüsselung gibt es nur durch ausdrückliche Konfiguration und der Start-Log nennt sie ([TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)). Die Diagnose nennt Schlüssel und Art des Fehlers, nie ein Passwort oder einen Prüfwert, und keine automatisch erzeugte Textdarstellung der Konfiguration enthält sie (Test). Tests Happy/Boundary/Negative. Aus den offenen Punkten von [ADR-0011](../../adr/0011-pruefwertformat.md) trägt dieser Slice die Dateirechte und die Übergabe der Benutzerliste im Betrieb (nicht gemessen): der Nutzer entscheidet sie vor dem Start, oder die Closure führt sie als weiter offen mit Grund; die Schlüssel `AUTH_USERS_FILE` (Pfad, Umgebungsvariable) und `AUTH_PLAIN_TESTMODE` (nur der Wert `true` schaltet ein) sind dort gesetzt. Das Lesen der Benutzerliste gehört ebenfalls hierher (Vertrag `contracts/auth-adapter.md`): Der Parser aus `slice-auth-adapter-benutzerliste` bekommt einen Text, entfernt ein UTF-8-BOM am Dateianfang nicht (es würde Teil des ersten Benutzernamens) und lehnt jedes CR als Fehler ab; das Lesen entfernt oder weist das BOM ab (nicht entschieden, Test) und normalisiert die Zeilenenden nicht.
- [ ] **Composition Root, `main`, Start und Stop** (ARC-008): `main` im Package `runtime` verdrahtet Konfiguration, Auth-Adapter, Handshake-Automat und den Listener der Transport-Welle; Start-Reihenfolge Konfiguration laden, validieren, (Startprobe der Transport-Welle), Listener binden; ein fehlgeschlagener Start endet mit Exit-Code ungleich null, ohne dass Threads oder Listener weiterlaufen (Test); Stop schließt Listener und offene Verbindungen. Der Start setzt `-Djdk.tls.server.newSessionTicketCount=0` im Serverprozess (`contracts/security-profile.md` §JVM-Flag; die Test-JVM setzt es seit `slice-transport-tls-schleife` schon) und prüft beim Start, dass es wirkt; ein Test belegt Setzen und Prüfen (ein Prozess ohne das Flag startet nicht). Start und Test laufen in Docker über ein `make`-Target (Name legt der Implementer fest und trägt es in `harness/README.md` §Sensors ein).
- [ ] **Rohbytes-Smoke der Welle**: Ein per `main` mit Testkonfiguration gestarteter Serverprozess beantwortet einen Rohbytes-Client (JVM-Test, kein Referenzclient) in einem Lauf: PRELOGIN, gültiger LOGIN7 → LOGINACK-Folge; unbekannte Kennung und falsches Passwort → byte-gleiche Ablehnungen; SSPI-Merkmal, FeatureExt `0x02` und Passwortwechsel → abgelehnt; mehrpaketiger, byteweise zerstückelter LOGIN7 → Anmeldung; danach Stop ohne hängende Verbindung. Der Smoke ist Teil von `make gates`. Er belegt den Pfad mit dem Rohbytes-Client, nicht die Annahme durch `Microsoft.Data.SqlClient`.

Nachweis-Pflichten für alle drei Punkte:

- [ ] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>` (Konvention nach `slice-qa-spec-abdeckung`); die Tests laufen in `make test` bzw. im Smoke-Target und sind grün.
- [ ] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet; die Zuordnung *Test | Festlegung* je Aussage der Welle steht in der Welle-Closure (Register, `BEO-TDS/aussagen-ueber-messung-hinaus`, siehe §8).
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/config/` | update | Konfigurationsmodell, Laden, Validierung; erweiterbar um die TLS-Schlüssel der Transport-Welle |
| `src/main/kotlin/io/github/pt9912/tdswire/runtime/` <!-- d-check:ignore (geplant: neues Package) --> | neu | `main`, Composition Root, Start/Stop (ARC-008); das einzige Package, das alles verdrahten darf |
| `Dockerfile`, `tools/mk/` (neue Fragmente oder Ergänzung) | update/neu | Start des Serverprozesses und des Smokes in Docker, Hook in `make gates`; JVM-Optionen nach `contracts/security-profile.md`, soweit die Transport-Welle sie nicht setzt (siehe §6) |
| `harness/README.md` | update | Zeile für das neue Target in §Sensors (sonst meldet das Doku-Gate `gate-undocumented`) |
| `contracts/auth-adapter.md` | update | Konfigurationsweg der Benutzerliste, sobald entschieden und gebaut |
| `src/test/kotlin/io/github/pt9912/tdswire/runtime/` <!-- d-check:ignore (geplant: neues Package) --> | neu | Konfigurations-Happy/Boundary/Negative, Startabbruch, Rohbytes-Smoke; nach SPEC-005, SPEC-008, SPEC-010, [TDS-FA-09.a](../../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose); `@spec`-Tag je Test |
| `gradle/libs.versions.toml`, `gradle.lockfile`, `gradle/verification-metadata.xml` | update, nur falls das Konfigurationsformat eine Abhängigkeit braucht | per `make deps-lock` (Netz), nicht von Hand; `kotlinx-coroutines-core` kommt mit dem Transport-Code ([ADR-0003](../../adr/0003-transportframework.md)) |
| `src/main/kotlin/io/github/pt9912/tdswire/transport/channel/SwitchableChannel.kt` | neu | Kanal mit einmaligem Wechsel vom rohen zum verschlüsselten Ziel; Leser und Schreiber des Handlers bleiben beim TLS-Handschlag unverändert (Ist-Zustand im ersten Lauf: `TransportHandshakeChannel` hält einen festen Kanal) |
| `src/main/kotlin/io/github/pt9912/tdswire/config/` (`ServerConfig`, `ConfigLoader`, `ConfigFiles`) | neu | Schlüssel, Bereiche, Lesen der Dateien; `config` darf nur `domain` importieren, deshalb eigene Richtlinien-Aufzählung statt `EncryptionPolicy` |
| `Dockerfile` (Stage `smoke`), `tools/mk/smoke.mk` | neu | `make smoke` als engster Sensor; `ServerSmokeTest` läuft auch in der Stage `test`, daher kein zweiter Eintrag in `GATE_CHECKS` |
| `contracts/security-profile.md` | update | Schlüssel für den TLS-Speicher, Flag-Prüfung beim Start |

Der Implementer-Agent hat die Liste im ersten Lauf erweitert (Zeilen oben).

- Reihenfolge: Konfiguration zuerst (mit Tests ohne Prozess), dann `main` mit Start/Stop gegen den Listener, zuletzt der Smoke.
- Die Konfigurationsschlüssel und das Modell gehören diesem Slice; die TLS-Schlüssel gehören der Transport-Welle. Wer das Modell zuerst anlegt, gibt es dem anderen (siehe §6).
- `make detekt` läuft auch über `detektTest`; jeder Befund in neuem Code wird im Code behoben, nie unterdrückt (`AGENTS.md` §3.2, §3.6).
## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Der Start-Trigger der Welle `welle-handshake-login` ist eingetreten, `slice-handshake-login` liegt in `done/` (der Auth-Slice `slice-auth-adapter-benutzerliste`, dessen Validierung dieser Slice ruft, liegt bereits in `done/`), der Listener-Slice der Transport-Welle `welle-transport-tls` liegt in `done/` (Kennung legt deren Planung fest), `slice-adr-pruefwertformat` liegt in `done/` (Konfigurationsweg und Format sind entschieden: Nutzerentscheidung, festgehalten als `Accepted`-ADR oder als Fortschreibung von SPEC-051 und `contracts/auth-adapter.md`) und ein Verantwortlicher ist im Kopf eingetragen.

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

- Konfigurationsweg und Format sind in [ADR-0011](../../adr/0011-pruefwertformat.md) (`Accepted`) entschieden: eine eigene Datei nur für die Benutzerliste (`AUTH_USERS_FILE`), eigenes Zeilenformat mit eigenem Parser, Testmodus-Schalter `AUTH_PLAIN_TESTMODE`; es kommt keine neue Abhängigkeit hinzu. Das Konfigurationsmodell bleibt in diesem Slice, den Prüfwert parst und validiert `slice-auth-adapter-benutzerliste`. Die Dateirechte und die Übergabe im Betrieb sind in der ADR offen und stehen in der DoD dieses Slice. — **Ausgang:** offen bis zur Closure.
- Überschneidung mit der Transport-Welle: Konfigurationsmodell und Schlüssel (Port, Bind-Adresse, TLS), die Startprobe und das Setzen der JVM-Option `-Djdk.tls.server.newSessionTicketCount=0` (`contracts/security-profile.md`: "beim Start des Serverprozesses zu setzen") berühren beide Wellen. Die Zuordnung ist mit der Transport-Planung abzustimmen; der Plan nimmt an, dass das Konfigurationsmodell und der Start des Prozesses hier liegen und die Transport-Welle ihre Schlüssel und die Startprobe einhängt. — **Ausgang:** offen bis zur Closure.
- Einordnung einer Abbruchausnahme am echten Handler (gemeldet von [slice-transport-listener-lebenszyklus](../done/slice-transport-listener-lebenszyklus.md) §6): `ManagedConnection.finish` ordnet eine `CancellationException`, die der Handler bei unabgebrochenem Scope wirft, als `HANDLER_FAILED` ein (Setzung, an Test-Handlern gemessen, vom Nutzer nicht bestätigt). Ob der Handshake-Handler mit der Anbindung dieses Slice so reagiert, wie die Setzung es annimmt, ist ungemessen; der Rohbytes-Smoke prüft es mit einem Handler, der von selbst abbricht, oder die Setzung wird hier benannt geändert. — **Ausgang:** offen bis zur Closure.
- Der Smoke ist ein Rohbytes-Client, kein Referenzclient: Er belegt, was der Server auf handgebauten Bytes antwortet, nicht, dass `Microsoft.Data.SqlClient` die Antwort annimmt. Die Aussage "Login funktioniert" darf in Notiz und Doku nicht ohne diese Einschränkung stehen (`BEO-TDS/aussagen-ueber-messung-hinaus`). — **Ausgang:** offen bis zur Closure.
- Wie der Smoke in Docker läuft (Prozess und Client im Test-Container über Loopback oder eigener Container im Netz), ist nicht entschieden; Docker-only (`AGENTS.md` §3.1) und die Basis-Images per Digest sind einzuhalten. — **Ausgang:** offen bis zur Closure.
- Prüfwerte stehen in der Konfiguration: Dateirechte, Übergabe in den Container und Anzeige in Diagnose und Log sind Teil der Sicherheitsaussage ([TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)); ein Test belegt nur die Textdarstellung, nicht die Dateirechte. — **Ausgang:** offen bis zur Closure.
- Die Defaults `LOGIN_TIMEOUT`, `MAX_PRELOGIN_MESSAGE_BYTES` und `MAX_MESSAGE_BYTES` sind in der Spezifikation "vorläufig"; der Slice übernimmt sie als Festlegung und misst sie nicht. — **Ausgang:** offen bis zur Closure.
- Ein `AuthPool` je Prozess ([slice-handshake-login](../done/slice-handshake-login.md)): Die Obergrenze der gleichzeitigen Adapteraufrufe gilt je `AuthPool`-Instanz; `HandshakeDriver` baut ohne Übergabe einen eigenen Pool mit der Voreinstellung. Die Verdrahtung gibt allen Treibern des Prozesses denselben Pool, sonst gilt die Obergrenze je Treiber (und bei einem Treiber je Verbindung gar nicht); ein Test der Verdrahtung belegt, dass zwei Verbindungen einen Pool teilen. Die Zahl der Plätze ist nicht gemessen (Träger `slice-qa-sicherheit`). Der Fristbeginn von `LOGIN_TIMEOUT` liegt beim Aufruf von `HandshakeDriver.run`, nicht bei der TCP-Annahme (SPEC-008 sagt „ab Verbindungsaufbau“): die Verdrahtung ruft den Treiber ohne Verzug nach der Annahme. — **Ausgang:** offen bis zur Closure.
- Größe: drei Liefer-Punkte, davon ein Smoke über den ganzen Pfad. — **Ausgang:** offen bis zur Closure. Rückführung und Schnitt in §4.

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

Wird bei der Closure gefüllt.

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
