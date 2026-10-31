# Welle welle-handshake-login — PRELOGIN-/LOGIN7-Handshake, Anmeldung mit Auth-Adapter und Laufzeit-Bootstrap — Closure-Notiz

> **Zitier-Form** *(bleibt stehen — Norm, kein Ausfüll-Hinweis).* Dieses
> Artefakt friert ein; was es zitiert, bewegt sich weiter. Deshalb: **Kennung,
> nicht Adresse** — `slice-<Kennung>` statt seines Lifecycle-Pfads, `make <target>`
> statt eines Links auf die Sensor-Datei, eine Baseline-Stelle als
> `v<X.Y.Z>` · `regelwerk/<datei>.md` §<Abschnitt> statt als Link
> (Baseline-Regelwerk `grundlagen-harness-dateien.md` §harness/README.md als
> Einstiegspunkt — beim Ausfüllen mit dem adoptierten Tag schreiben).

**Welle:** welle-handshake-login
**Abschluss:** 2026-10-31
**Verantwortlich:** pt9912

## Was wurde geliefert?

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — *was gelernt wurde*: geliefert · was
funktionierte · was anders lief. Mit ID-Bezug, wo es einen gibt.

- Ein aus der Konfiguration über `main` gestarteter Serverprozess führt eine Verbindung ohne TLS und ohne Referenzclient von PRELOGIN über LOGIN7 bis zur Anmeldung: Ein Benutzer der festen Benutzerliste erhält die Login-Antwort-Tokens, unbekannte Kennung und falsches Passwort erhalten byte-gleiche Ablehnungen (ERROR 18456), nicht unterstützte Authentifizierungsmodi werden ohne Adapteraufruf abgelehnt ([TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-FA-02](../../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)).
- `slice-adr-pruefwertformat`: Entscheidung des Prüfwertformats der Benutzerliste (`plain` und `pbkdf2-sha256`, Verfahrenskennung je Eintrag) und des Konfigurationswegs, mit drei Spike-Images und Nutzerwahl, als [ADR-0011](../../adr/0011-pruefwertformat.md) neben [ADR-0009](../../adr/0009-authentifizierungsmodus.md) (`Accepted` bleibt unverändert, `AGENTS.md` §3.5).
- `slice-auth-adapter-benutzerliste`: Adapter-Schnittstelle und Einstiegsimplementierung (feste Benutzerliste, konstanter Vergleich als Bauart mit Strukturtest, Validierung der Liste mit 17 Fehlerarten, Scheinvergleich bei unbekanntem Benutzer).
- `slice-handshake-login`: Handshake-Zustandsautomat (reine Funktion `step`, Treiber `HandshakeDriver`, Schnittstelle `HandshakeChannel`) mit einer Matrix von 84 Zellen, PRELOGIN-Aushandlung, LOGIN7-Zulassung, Ablehnung 18456, mehrpaketiges und zerstückeltes LOGIN7, Adapteraufruf außerhalb des Transport-Threads mit Obergrenze verwaister Aufrufe ([ADR-0004](../../adr/0004-session-zustandsautomat.md)).
- `slice-bootstrap-konfiguration`: Konfiguration laden und validieren, `main`, Start und Stop, Verdrahtung von Listener, Handshake und Auth-Pool, Prüfung des JVM-Flags `-Djdk.tls.server.newSessionTicketCount=0`, und der Rohbytes-Smoke.
- Die Aussage gilt für diesen Pfad und diesen Rohbytes-Client, nicht für die Annahme durch den Referenzclient `Microsoft.Data.SqlClient` (Welle-Datei §6).

**Das Mehr gegenüber den Slice-DoDs (Welle-Datei §3): der Rohbytes-Smoke gegen einen über den Bootstrap-Pfad gestarteten Prozess.** `ServerSmokeTest > Serverprozess beantwortet den Login-Pfad mit Rohbytes` startet den Prozess über `main` (Kind-JVM mit dem JVM-Flag und einer Testkonfiguration mit `TEST_UNENCRYPTED`) und belegt in einem Lauf: gültiger Login für `plain` und `pbkdf2` (LOGINACK unter den Tokens), unbekannte Kennung und falsches Passwort ergeben dieselben Bytes wie die feste Ablehnung, SSPI-Merkmal, FeatureExt `0x02` und Passwortwechsel ergeben dieselbe Ablehnung, ein LOGIN7 in 20-Byte-Paketen, byteweise gesendet, führt zur Anmeldung, SIGTERM beendet den Prozess und trennt die angemeldete Verbindung, und die Standardausgabe nennt weder Benutzer noch Passwort. Dazu zwei weitere Tests: Start ohne das JVM-Flag (Exit-Code 1, Meldung) und fehlerhafte Konfiguration oder belegter Port (Exit-Code 1, kein Wert in der Ausgabe). Der Client baut seine Bytes von Hand; das ist ein Handvektor und eine Lesart von MS-TDS, keine Messung am Referenzclient.

Die Aussagen der Welle, in zwei Spalten. *Gemessen* heißt: ein bestandener Test oder der Smoke belegt es, am Produkt mit einem Rohbytes-Client oder einem Test-Adapter (kein Referenzclient, kein SQL Server); *Festlegung* heißt MS-TDS-Lesart oder Entscheidung eines Slice ohne Referenzclient-Messung. Die Tabelle führt die Tabellen der vier Slice-Closures zusammen.

| Spalte | Aussage | Beleg im Repo |
|---|---|---|
| gemessen | Vom Prozessstart bis zur Login-Antwort: gültiger Login (`plain`, `pbkdf2`), byte-gleiche Ablehnung für unbekannte Kennung und falsches Passwort, Ablehnung der drei nicht unterstützten Modi, mehrpaketiges zerstückeltes LOGIN7, SIGTERM trennt, keine Geheimnisse in der Ausgabe | `ServerSmokeTest` (3 Tests, `make smoke` und `make test`) |
| gemessen | Die Matrix zählt 84 Zellen (25 zulässig, 4 ignoriert, 55 unzulässig) und entspricht einer von Hand geschriebenen Tabelle; die Antworten auf unbekannte Kennung und falsches Passwort sind im Treiber (mit der echten `UserListAuthAdapter`) und auf dem Draht byte-gleich; Passwort und Kennung stehen in keiner Antwort und keinem Log-Eintrag; der Adapter läuft nicht auf dem Transport-Thread und hält `LOGIN_TIMEOUT` ein; verwaiste Aufrufe sind je `AuthPool` begrenzt | `HandshakeMatrixTest`, `HandshakeDriverTest`, `HandshakeTransportTest`, `LoginAdmissionTest`, `AuthExecutorTest`, `HandshakeWiringTest` |
| gemessen | Der Lader wendet Voreinstellungen und Bereiche an und nennt Schlüssel und Art des Fehlers; kein Wert, Pfad oder Prüfwert in Diagnose, Log oder Textdarstellung; der Start prüft das JVM-Flag zuerst; ein fehlgeschlagener Start hinterlässt keine Threads; der TLS-Handschlag mit `REQUIRED` läuft über den Wechsel des Kanals (JDK-Gegenstelle in der Test-JVM, nicht im `main`-Prozess) | `ConfigLoaderTest`, `ConfigUsersFileTest`, `BootstrapTest`, `SwitchableChannelTest`, `TlsWiringTest` |
| Handvektor | PRELOGIN-Antwort (ENCRYPT_REQ), ERROR 18456 mit DONE, LOGINACK mit DONE, ENVCHANGE Datenbank, Packet Size und Collation, Paketkopf der Ablehnung; mehrpaketiges LOGIN7 (ob ein Client so sendet, ist nicht bekannt); PBKDF2-HMAC-SHA-256 gegen die Vektoren von RFC 7914 | `PreloginNegotiationTest`, `LoginResponseTest`, `HandshakeTransportTest`, `AuthPrimitivesTest` |
| Festlegung | Flache Aufzählung der Zustände (Nutzerentscheidung, 2026-10-30) mit zusätzlichem `AUTHENTICATING`; unzulässiger Übergang, Fristablauf und Fehler schließen ohne Antwort; PRELOGIN-Antwort ENCRYPT_REQ; Folge, State und DONE-Statusbits der Login-Antwort; `AuthPool` mit 4 Plätzen; Grenze von 128 Einheiten je Feld; Wertebereiche der Konfiguration; Prüfwertformat-Einzelheiten (Testmodus-Boden, Kommentare, CRLF, Hash genau 32 Byte, Salz mindestens 16 Byte); Attrappe des Scheinvergleichs; Abbruchausnahme des Adapters als Adapterfehler | KDoc, `contracts/client-profile.md`, `contracts/auth-adapter.md`, [ADR-0011](../../adr/0011-pruefwertformat.md); die Einzelheiten sind teils vom Nutzer nicht bestätigt (Steering-Loop-Einträge) |
| nicht gemessen | Annahme der Antworten durch `Microsoft.Data.SqlClient` und das Verhalten eines echten SQL Servers; TDS 8.0 mit SQL-Authentifizierung und Pooling mit `ResetConnection`; TLS im `main`-Prozess; Zeitgleichheit der Ablehnungen, Wirkung eines PBKDF2-Aufrufs auf den Transport unter Last; Kopien des Passworts im Speicher; Dateirechte und Übergabe der Benutzerliste im Betrieb; Wirkung des JVM-Flags auf dem Draht; Flake-Freiheit über die gefahrenen Läufe hinaus | Träger in der Tabelle unten; Register |

**Jeder in Welle-Datei §6 ausgeschlossene Gegenstand hat einen Träger als Datei oder den Grund, warum nicht** (Prüfung am 2026-10-31 gegen die Dateien im Planning-Lifecycle):

| Ausgeschlossen | Träger |
|---|---|
| Transport und TLS, Listener, Startprobe | Welle `welle-transport-tls` (geschlossen, `welle-transport-tls-results.md`); Verdrahtung im Serverprozess lieferte `slice-bootstrap-konfiguration` |
| Verbindungs-Zustandsautomat jenseits des Logins, ATTENTION | `slice-attention-cancel-timeout` (`open/`), `slice-fehler-zustandsuebergang` und `slice-sql-batch-dispatch` (`done/`) |
| Backend, Simulations-Backend | Welle `welle-backend-streaming` (`slice-backend-port-vertrag` in `done/`, `slice-backend-streaming-nachweis` in `in-progress/`); Welle `welle-simulations-backend` |
| Fehler- und ATTENTION-Semantik | Welle `welle-fehler-abbruch` ([ADR-0007](../../adr/0007-fehler-und-attention.md)) |
| Geordnetes Beenden mit Frist (`SHUTDOWN_GRACE`) | `slice-abbruch-ressourcen-shutdown` (`open/`) |
| Observability-Adapter | `slice-betrieb-log-schnittstelle` (`open/`, Welle `welle-betrieb-diagnose`) |
| Referenzclient, echter SQL Server, Vergleichslauf D, TDS 8.0 mit SQL-Authentifizierung, Pooling | `slice-qa-kompatibilitaet`, `slice-qa-vergleichslauf-d`, `slice-referenzabgleich-mitschnitt` (`open/`) |
| Zeitgleichheit, Drosselung, Loginspektion über das Gesamtsystem, Plätze des `AuthPool` unter Last | `slice-qa-sicherheit` (`open/`); Fuzz und Stabilität: `slice-qa-stabilitaet` (`done/`) |
| Anwendung der angekündigten Packet Size | `slice-backend-streaming-nachweis` (`in-progress/`) |
| Start- oder Laufzeitartefakt, das das JVM-Flag setzt; Dateirechte und Übergabe der Benutzerliste | `slice-release-artefakt-lizenz` (`open/`) |
| `MAX_MESSAGE_BYTES` ohne Konfigurationsschlüssel | `slice-betrieb-zustandsereignisse` (`open/`) |
| Nicht unterstützte Authentifizierung als Funktion (SSPI/Kerberos, Entra-ID, Passwortwechsel) | kein Träger: sie zu unterstützen wäre eine neue Anforderung ([ADR-0009](../../adr/0009-authentifizierungsmodus.md)), die Welle erkennt und lehnt ab |
| Eigene Benutzerverwaltung eines Betreibers, andere Adapter-Varianten | kein Träger-Slice: die Betreiberanforderung ist nicht abnahmebindend und steht als `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt` im Register (die Rückfrage liegt beim Betreiber) |
| Neue Anforderungen oder Änderungen am Lastenheft | kein Träger nötig für diese Welle: die einzige Änderung seit der Eröffnung ist Lastenheft 0.5.0 (`f067e2f`, `IS NULL` im Simulations-Backend, [TDS-FA-11](../../../../spec/lastenheft.md)), sie berührt Handshake und Login nicht |

## Was hat funktioniert?

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3.

- Der Schnitt trug in allen vier Slices; keiner brauchte die Rückführung nach Welle-Datei §4. Der Entscheidungs-Slice vor Auth und Bootstrap erlaubte, das Prüfwertformat mit Messzeilen zu wählen, bevor Code davon abhing; die Richtung des Nutzers wurde als Eingabe geprüft („Befund zur Richtung“), bevor sie Entscheidung wurde.
- Die Trennung von Automat (reine Funktion), Treiber und Schnittstelle ließ jede Zusage einzeln mutieren; die Verifikationen fuhren 41 (Auth-Adapter), 32 und 12 (Handshake) und 9 (Bootstrap) Mutationen und fanden jeweils echte Lücken, die der Review beim Lesen nicht sah.
- Ein von Hand gebauter Rohbytes-Client im Smoke hält den Test unabhängig vom Codec des Produkts; der Smoke prüft den Prozess, nicht eine im Test verdrahtete Instanz.
- Die byte-gleiche Ablehnung ist an drei Stellen belegt (Treiber, Draht, Prozess), jede mit anderem Aufbau.

## Was ging anders als geplant?

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — jede Zeile moeglichst mit der Konsequenz,
die daraus schon gezogen wurde (Folge-Slice, Spec-Version).

- Der Plan sagte, der Start setze `-Djdk.tls.server.newSessionTicketCount=0`; gebaut ist nur die Prüfung, ein Startartefakt gibt es nicht (Review F-1 von `slice-bootstrap-konfiguration`). Konsequenz: `slice-release-artefakt-lizenz` trägt das Startartefakt in der DoD; die Spezifikation sagt „prüft, setzt nicht“.
- Die Obergrenze verwaister Adapteraufrufe wurde je Treiber gebaut und als je Pool behauptet (Nachverifikation N-1); die zweite Fix-Runde setzt sie je `AuthPool`. Die Tests der zweiten Fix-Runde hat kein Verifier mutiert.
- Die Matrix zählt 84 Zellen statt der 140 des Spikes in [ADR-0004](../../adr/0004-session-zustandsautomat.md); die ADR bleibt unverändert, die Abweichung steht in der Notiz des Slice.
- Die angekündigte Packet Size hat keinen Anwender am Schreiber; Träger ist `slice-backend-streaming-nachweis`.
- Die Abbruchausnahme des Adapters hat eine Folge im Listener (`HANDLER_FAILED`), die der Plan des Listener-Slice nicht nannte (`BEO-TDS/folge-einer-festlegung-im-nachbar-slice-unbenannt`, 1×).
- Der Slice `slice-adr-pruefwertformat` lag bis zu seiner Closure in `open/`, obwohl an ihm gearbeitet wurde (Verifikation V-25); der Lifecycle-Wechsel wurde nachgeholt. Konsequenz: keine Regel, das Auftreten ist 1×.
- Ein Wanduhr-Test des Listener-Slice wurde unter Last einmal rot (Nachverifikation N-4); die Beobachtung `BEO-TDS/test-stage-cache-verdeckt-wiederholung` führte zu `make clean-build` (Anker `seit slice-handshake-login`).

## Steering-Loop-Einträge

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 (hier stehen **nur** Beobachtungen, die im
Register 3× erreicht haben; jeder Eintrag nennt seine `BEO-<KUERZEL>/<slug>`) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln (Feld
und Zielort auf **einer** Zeile, Sektionsangabe innerhalb der Backticks).

**Lese-Schritt des Registers (2026-10-31).** Das Register führt 40 Beobachtungen; 13 haben mindestens drei Evidence-Dateien (gegen das Dateisystem gezählt). Sechs tragen schon einen Ausgang, sieben stehen über der Schwelle ohne Ausgang. Der Lese-Schritt der Closure von `welle-transport-tls` am selben Tag hat dieselbe Menge gelesen (kein Slice kam dazwischen); der Stand der sieben ist dort und hier gleich.

*Schon mit Ausgang* (der Lese-Schritt bestätigt den Stand):

- `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` (5×, vier davon aus dieser Welle, eines aus `slice-adr-authentifizierung`): `geplant`, Träger `slice-qa-sicherheit`; dessen DoD nennt Zeitgleichheit, Drosselung, den Scheinvergleich bei gemischten Listen und die Belegung der Plätze des `AuthPool`. Der Träger existiert in `open/`.
- `BEO-TDS/test-stage-cache-verdeckt-wiederholung` (3×, mit `slice-handshake-login` erreicht): `verkörpert` in `tools/mk/ci.mk:clean-build` und `harness/README.md` §Sensors, Anker `seit slice-handshake-login`. Die Regel erzwingt kein Gate: kein Sensor prüft, ob eine Stabilitätsaussage aus einem Lauf ohne Cache stammt (Grenze der Deklaration, benannt in der Slice-Closure).
- `BEO-TDS/aussagen-ueber-messung-hinaus` (29×): `verkörpert` in `AGENTS.md` §3.8 und im Reviewer-Skill; trat in allen vier Slices wieder auf (Messzeile ohne Messbedingung, `je Pool` mit einem Treiber belegt, KDoc breiter als der Test) und wurde als Finding behoben. `BEO-TDS/offene-punkte-ohne-traeger` (15×): `verkörpert` in `.claude/commands/implement-slice.md` Schritt 24 für Entscheidungs-Slices; in dieser Welle trat es in `slice-auth-adapter-benutzerliste`, `slice-handshake-login` und `slice-bootstrap-konfiguration` auf, also in Implementierungs-Slices. Ob der Absatz darauf ausgedehnt wird, ist seit der Closure von `slice-auth-adapter-benutzerliste` eine offene Frage an den Nutzer, die diese Closure nicht entscheidet.
- `BEO-TDS/messung-am-echten-sql-server-fehlt` (16×): `geplant`, Träger `slice-qa-vergleichslauf-d`. `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` (12×): `geplant`, Träger `slice-qa-kompatibilitaet`. Beide Träger sind Dateien in `open/`.

*Über der Schwelle, nicht verkörpert, Entscheidung beim Nutzer.* Diese Closure verkörpert keine dieser Regeln und ändert keine Hard Rule in `AGENTS.md`: eine neue Regel oder ein neuer Sensor ist eine Entscheidung des Nutzers. Jeder Eintrag steht mit Vorschlag im Bericht der Closure; sein `state.md` trägt den Stand `offen` mit diesem Vermerk. Es gibt kein `liegt in`, die Paarung (a) entfällt.

- `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen` — 3× (`slice-auth-adapter-benutzerliste`, `slice-bootstrap-konfiguration`, `slice-handshake-login`), alle drei aus dieser Welle: Das Überschreiben der Passwortfelder schützt nur diese Felder; weitere Kopien (Schlüsselobjekt der JDK-Ableitung, UTF-8-Zwischenpuffer, `PBEKeySpec`, Keystore-Kennwort, Benutzerliste im Heap) sind aus dem Code gelesen und nicht per Speicherabzug gemessen. Kein Träger-Slice.
- `BEO-TDS/strukturtest-ohne-variation-der-eingabe` — 6×, davon aus dieser Welle `slice-auth-adapter-benutzerliste` (Attrappe des Scheinvergleichs, Mutation M22) und `slice-handshake-login` (Obergrenze „je Pool“ mit einem Treiber).
- `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` — 6×, davon aus dieser Welle `slice-bootstrap-konfiguration` (`HANDLER_FAILED` für die Abbruchausnahme).
- `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` — 6×, davon aus dieser Welle `slice-bootstrap-konfiguration` (Review F-3).
- `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung` — 3× (`slice-adr-pruefwertformat`, `slice-adr-simulation-formen`, `slice-auth-adapter-benutzerliste`), zwei aus dieser Welle: `SPEC-051` führt eine Einzelfallliste (Verfahren, Datei, Iterationen, Parser, `AUTH_PLAIN_TESTMODE`, Passwort-Randfälle), deren Deckung durch das Lastenheft nicht belegt ist.
- `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` — 3×, `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 3×: aus dieser Welle keine Evidence (siehe die Closure von `welle-transport-tls`).

**Spec-Lücken, benannt und offen** (kein `liegt in`, weil in einer Spec und nicht an einem Zielort verkörpert):

- `SPEC-051` (Einzelfälle ohne belegte Deckung durch das Lastenheft, siehe oben) und die fünf in [ADR-0011](../../adr/0011-pruefwertformat.md) weiter unter „Offen“ stehenden, inzwischen entschiedenen Punkte: Es braucht eine Folge-Entscheidung oder den Nachzug von `SPEC-051`. Der Träger ist keine Slice-Datei, sondern die Beobachtung `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung`; die Frage geht an den Nutzer.
- `SPEC-028` verlangt für das erfolgreiche LOGIN7 „eine durch Referenztests bestätigte Folge zulässiger Tokens“; bestätigt sind nur die drei Messpunkte des Spike, Folge, State und Statusbits sind Festlegung. Sie schließt sich mit den Messlücken `slice-qa-vergleichslauf-d`, `slice-qa-kompatibilitaet` und `slice-referenzabgleich-mitschnitt`.

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

Keine neuen Slices durch diese Closure. Die Träger der offenen Punkte existieren als Dateien:

- `slice-qa-sicherheit` (Zeitgleichheit, Drosselung, Plätze des `AuthPool`, Scheinvergleich bei gemischten Listen) — `open/`.
- `slice-qa-kompatibilitaet`, `slice-qa-vergleichslauf-d`, `slice-referenzabgleich-mitschnitt` (Referenzclient, echter SQL Server, Token-Folge) — `open/`.
- `slice-release-artefakt-lizenz` (Startartefakt für das JVM-Flag, Dateirechte und Übergabe der Benutzerliste) — `open/`.
- `slice-abbruch-ressourcen-shutdown`, `slice-attention-cancel-timeout`, `slice-betrieb-log-schnittstelle`, `slice-betrieb-zustandsereignisse` — `open/`.
- `slice-backend-streaming-nachweis` (Anwendung der angekündigten Packet Size) — `in-progress/`.

## Verifikation

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 1 — keine Behauptung ohne nachprüfbaren
Anker (Hash, Lauf, Zahl).

- Alle Slices der Welle in `done/`: `slice-adr-pruefwertformat`, `slice-auth-adapter-benutzerliste` (Merge `bd9eac0`), `slice-handshake-login` (Merge `bda6cd6`, Nachverifikation `dc08d50`), `slice-bootstrap-konfiguration` (Merge `d5b0270`, Verifikation `dcb6b27`).
- Der Smoke wurde für diese Closure real gefahren: `make smoke` entspricht `docker build --target smoke --progress=plain .`; der Lauf mit `--no-cache-filter smoke` endete mit Exit 0, `BUILD SUCCESSFUL in 1m 11s`, und die Ausgabe nennt `ServerSmokeTest > Serverprozess beantwortet den Login-Pfad mit Rohbytes() PASSED`, `… Ein Prozess ohne das JVM-Flag startet nicht() PASSED` und `… Fehlerhafte Konfiguration und belegter Port beenden den Start mit Exit-Code 1() PASSED`.
- `make gates` Exit 0 am 2026-10-31 (`baseline-verify: v6.18.1 OK — 54 Dateien`; `make docs-check` ohne Befund).
- Frischer Testlauf ohne Cache (`docker build --no-cache-filter test --target test`, Stand `092b7a1`; `src/`, `build.gradle.kts`, `gradle/` und `Dockerfile` sind seither unverändert): `BUILD SUCCESSFUL in 1m 20s`, 633 Tests `PASSED`, kein Test `FAILED`; die drei Tests des `ServerSmokeTest` laufen auch in dieser Stage.
- Trigger-Audit, je Klasse:
  - **Carveouts:** zwei aktiv, 0 fällig. [CO-001](../../carveouts/CO-001-nuget-audit-aus.md) und [CO-002](../../carveouts/CO-002-nuget-signaturpruefung-fehlt.md) betreffen die .NET-Referenztests; ihr Auflösungs-Trigger (Folge-Slice `slice-qa-dotnet-lieferkette-haerten` in `done/`) ist nicht eingetreten, der Folge-Slice liegt in `open/`, die Welle berührt die .NET-Referenztests nicht. Diese Welle hat keinen Carveout eröffnet.
  - **Bootstrap-aware Gates:** 0 offen. Die Tabelle *Sensors* in `harness/README.md` führt kein Target mit Schwelle oder Reifestufe.
  - **ADR-Re-Evaluierungs-Trigger** der von der Welle berührten, angenommenen ADRs: [ADR-0004](../../adr/0004-session-zustandsautomat.md) (neue TDS-Nachrichtentypen oder Session-Races: nein; ein Schreibwettlauf auf dem echten Transport: nicht gemessen, die Leseseite der TLS-Schleife hing an der Schreibsperre und wurde behoben, der Wettlauf zwischen Vormerken und Loslassen ist nicht belegt; Messung am echten SQL Server: nicht gefahren, Wächter `slice-qa-vergleichslauf-d`), [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) (abweichende Antwort eines echten SQL Servers: nicht gemessen, derselbe Wächter), [ADR-0009](../../adr/0009-authentifizierungsmodus.md) (neuer Modus im Clientprofil oder neue Betreiber-Sicherheitsrichtlinie: nein), [ADR-0011](../../adr/0011-pruefwertformat.md) (weiteres Verfahren: nein; andere Dateiformate: nein; Messung auf dem Laufzeit-Image: nicht gefahren, es gibt kein Laufzeit-Image, Träger `slice-release-artefakt-lizenz`; Betreiberanforderung an die Übergabe: nicht eingetreten) — 0 fällig. Offen ist der Nachzug der in [ADR-0011](../../adr/0011-pruefwertformat.md) unter „Offen“ geführten, inzwischen entschiedenen Punkte; das ist keine Re-Evaluierung, sondern die Spec-Lücke oben (Entscheidung beim Nutzer).
- Zuordnung *gemessen | Festlegung* und Träger je ausgeschlossenem Gegenstand: siehe oben (Closure-Trigger der Welle-Datei).
- Drei Paarungen: (a) Anker — kein Eintrag dieser Closure trägt `liegt in`, entfällt (der Anker des schon verkörperten `clean-build` gehört zur Closure von `slice-handshake-login`). (b) Folge-Slice — die genannten Dateien liegen in `open/` und `in-progress/`. (c) Register — alle genannten `BEO-TDS/<slug>` existieren als Verzeichnis mit nicht leerem `evidence/` (gegen das Dateisystem geprüft).
- Archivierung (Schritt 4): `archive-welle welle-handshake-login` lief mit Kennung [TDS-FA-02](../../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung) in zwei Commits (`448653e` reiner Move, `c1404bf` Archiv, Stubs und Verweis-Nachzug): 4 Slices und der Welle-Plan nach `done/welle-handshake-login/archiv.zip` (132901 Bytes, 12 Dateien), 5 Stubs, 7 Review-Reports aus `docs/reviews/` entfernt, Verweis-Nachzug in 22 Dateien. Befunde:
  - Die Vorprüfung sperrte einmal mit `[haenger]`: der Report von `slice-backend-typabbildung` verlinkte die Verifikation von `slice-handshake-login`. Der Link wurde durch eine Beschreibung ersetzt (Commit `bad6fc9`), danach lief die Vorprüfung ohne Sperre. Wellenlose Slices gab es seit der Archivierung von `welle-transport-tls` keine (`wellenlos: 0`).
  - Reports, die einen Slice nicht im Dateinamen tragen, ordnet das Werkzeug keinem Slice zu und lässt sie liegen; für `slice-adr-pruefwertformat` trägt `docs/reviews/` keinen Report mit dem Slice-Namen (ein Sammel-Report zu mehreren ADRs, `…-46be900-verifikation-adr-0004-0006-0007-0010-0011.md`, bleibt).
  - Geltungsbereich der Sensoren: wie bei `welle-transport-tls` geprüft (kein Sensor keilt auf `done/*.md`); `make docs-check` ohne Befund nach dem Lauf (385 Dateien).
- `make gates` Exit 0 nach dem Move und nach der Archivierung.
