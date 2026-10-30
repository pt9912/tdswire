# Slice slice-handshake-login: Handshake-Zustandsautomat von PRELOGIN bis zur Login-Antwort

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-handshake-login

**Bezug:** [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-FA-02](../../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md), [ADR-0002](../../adr/0002-tls-und-verschluesselung.md), [ADR-0009](../../adr/0009-authentifizierungsmodus.md)

**Berührte Spec-Stellen:** SPEC-001 · SPEC-007 · SPEC-008 · SPEC-010 · SPEC-012 · SPEC-028 · SPEC-046 · SPEC-048 · ARC-004 · ARC-007 · [TDS-FA-01.a](../../../../spec/spezifikation.md#tds-fa-01a--tcp-verbindung-und-prelogin) · [TDS-FA-02.a](../../../../spec/spezifikation.md#tds-fa-02a--login7-und-sitzung)

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

**Ziel:** Ein Verbindungs-Zustandsautomat im Package `protocol` nimmt vollständige Nachrichten (aus dem Codec) und Ereignisse (TLS ausgehandelt, Frist abgelaufen) entgegen und führt PRELOGIN, LOGIN7 und die Antwort aus: Annahme mit LOGINACK, ENVCHANGE und DONE und einer Sitzung im Zustand `READY`, Ablehnung als festes Muster (ein ERROR 18456, DONE mit Fehlerstatus, Verbindungsende), nicht unterstützte Modi ohne Adapteraufruf abgelehnt; ohne Netzwerk, ohne TLS-Maschine, mit mehrpaketigem LOGIN7 über den Paket-Codec.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Sockets, `SSLEngine`, TLS-Kapselung in Paketen vom Typ `0x12`, Listener, Ausführung von Timern — die geplante Transport-Welle `welle-transport-tls`; der Automat bekommt "TLS ausgehandelt" und "Frist abgelaufen" als Ereignisse und wertet sie aus.
- Laden der Konfiguration, Composition Root, Verdrahtung mit Listener und Adapter — `slice-bootstrap-konfiguration`; der Automat bekommt Richtlinie, Grenzen und Adapter als Parameter.
- Prüfung von Kennung und Passwort — `slice-auth-adapter-benutzerliste`; hier läuft ein Test-Adapter und, für den Byte-Vergleich, die echte Benutzerliste aus jenem Slice.
- `READY`-Verarbeitung: SQL_BATCH, RPC, Anfragezustand, ATTENTION, Wiederverwendung der Verbindung (SPEC-001 untere Hälfte, SPEC-029) — der Zustand `READY` entsteht, nimmt aber noch nichts an; Träger sind `slice-attention-cancel-timeout`, `slice-fehler-zustandsuebergang` (beide `welle-fehler-abbruch`) und `slice-sql-batch-dispatch` (`welle-anfrageverarbeitung`).
- SSPI/Kerberos, Entra-ID und Passwortwechsel unterstützen — sie werden am LOGIN7 erkannt und abgelehnt ([ADR-0009](../../adr/0009-authentifizierungsmodus.md)); Unterstützung wäre eine neue Anforderung.
- Messung mit dem Referenzclient und einem echten SQL Server (mehrpaketiges LOGIN7, TDS 8.0 mit SQL-Authentifizierung, Pooling mit `ResetConnection`, State und Token-Folge eines echten Servers) — `slice-qa-kompatibilitaet` und `slice-qa-reproduzierbarkeit`; dieser Slice belegt Vektoren, die von Hand aus MS-TDS abgeleitet sind.
- Drosselung wiederholter Anmeldeversuche — `slice-qa-sicherheit`; die Entscheidung ist offen ([ADR-0009](../../adr/0009-authentifizierungsmodus.md) §Offen).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Zustandsautomat und PRELOGIN** (ARC-004, SPEC-001, SPEC-028, SPEC-010, SPEC-046, SPEC-008): explizite Übergangsmatrix für `CONNECTED`, `PRELOGIN`, `TLS_NEGOTIATION`, `LOGIN7`, `READY` (login-seitig), `CLOSED` in beiden Reihenfolgen des Clientprofils (TDS 7.x: PRELOGIN vor TLS; TDS 8.0: TLS vor PRELOGIN; die Reihenfolge von TDS 8.0 ist Festlegung, [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md)); je ein Test pro zulässigem und pro unzulässigem Übergang (LOGIN7 vor PRELOGIN, zweites PRELOGIN, SQL_BATCH vor `READY`, Nachricht nach `CLOSED`); ein unzulässiger Übergang beendet die Verbindung kontrolliert, ohne den Zustand einer zweiten Automaten-Instanz zu berühren. PRELOGIN-Antwort aus der Verschlüsselungs-Richtlinie: `REQUIRED` antwortet ENCRYPT_REQ oder ENCRYPT_ON (welche, ist Festlegung), ENCRYPT_NOT_SUP nur im ausdrücklich konfigurierten Testmodus, Unvereinbarkeit von Client-Anforderung und Richtlinie bricht vor LOGIN7 ab, kein stilles Downgrade ([ADR-0002](../../adr/0002-tls-und-verschluesselung.md)). Eine Nachricht über `MAX_PRELOGIN_MESSAGE_BYTES` wird abgewiesen (SPEC-046); der Fristablauf (`LOGIN_TIMEOUT`, als Ereignis) endet im Schließen ohne Antwort (SPEC-008). Aus den offenen Punkten von [ADR-0004](../../adr/0004-session-zustandsautomat.md) trägt dieser Slice drei: (1) den Zuschnitt der Aufzählung (eine flache Aufzählung oder Verbindungs- und Anfragezustand getrennt; `SPEC-001` führt ihn offen) — **Entschieden (Nutzer, 2026-10-30):** flach, eine Aufzählung wie im Spike (gemessen sind dort 10 Zustände × 14 Ereignisse; die Zweiteilung nach `SPEC-001` in Verbindungs- und Anfragezustand ist nicht gemessen); der Zuschnitt steht im KDoc als Festlegung und ist kein offener Punkt dieses Slice mehr; (2) die Antwort auf einen unzulässigen Übergang im Handshake (der Spike schloss ohne Antwort, eine Setzung des Spike; `slice-fehler-zustandsuebergang` führt denselben Punkt für den Anfragezustand) — als Festlegung gekennzeichnet und durch die Tests dieses Punkts gepinnt; (3) die Zählung der Spike-Matrix in [ADR-0004](../../adr/0004-session-zustandsautomat.md) (140 Zellen und 700 Läufe gegen die Angabe, `CANCELLING` und `COMPLETED` seien keine Matrixzeilen), die dort als nicht belegt steht: die Matrix dieses Slice zählt ihre Zellen je Zustand und Ereignis aus dem Code, und die Closure-Notiz nennt die Abweichung zur Spike-Zahl, ohne diese zu übernehmen. Hinzu kommt der Wechsel der Rahmen-Grenze: `MessageReader` aus `slice-transport-coroutines-geruest` trägt `limits` als Konstruktorparameter und wechselt sie nicht an einem laufenden Leser; der Automat löst, wie die Grenze von der Vor-Login-Grenze (SPEC-046) auf die Standardgrenze (SPEC-007) übergeht, ohne bereits gelesene Folgebytes zu verlieren, und ein Test mit Folgebytes hinter LOGIN7 belegt es.
- [ ] **Login-Antworten** ([TDS-FA-02.a](../../../../spec/spezifikation.md#tds-fa-02a--login7-und-sitzung), SPEC-012, SPEC-048, SPEC-028): Annahme sendet LOGINACK, die festgelegten ENVCHANGE und DONE und erzeugt die Sitzung (ID, Principal, Datenbankkontext); Ablehnung sendet genau ein ERROR (18456, Class 14, einheitlicher State, feste Meldung ohne Kennung) und DONE mit Fehlerstatus und beendet die Verbindung; eine Ausnahme im Adapter wird wie `Rejected` beantwortet und intern geloggt. Tests: Antwortbytes für unbekannte Kennung und falsches Passwort sind byte-gleich (am Produkt mit dem Rohbytes-Test, nicht am Referenzclient); Passwort und Kennung stehen in keiner Antwort, keinem Log-Eintrag und keinem Ablehnungsgrund; das Passwortfeld ist nach dem Adapteraufruf überschrieben, auch das Zwischenfeld bei der Abbildung von `SecretChars` (`protocol/login7`) nach `PasswordChars` (`config/auth`, Konstruktor kopiert; `contracts/auth-adapter.md`); der Adapteraufruf blockiert und ist nicht `suspend` (Festlegung aus `slice-auth-adapter-benutzerliste`), der Automat ruft ihn deshalb nicht auf dem Transport-Thread und hält `LOGIN_TIMEOUT` ein (ein Test mit einem langsamen Test-Adapter); ein LOGIN7, das über mehrere Pakete läuft und byteweise zerstückelt ankommt, führt über Paket-Codec und Automat zur Anmeldung (von Hand aus MS-TDS abgeleiteter Vektor, Festlegung).
- [ ] **Nicht unterstützte Modi und ungültige Felder** ([ADR-0009](../../adr/0009-authentifizierungsmodus.md)): `fIntSecurity` (SSPI-Block), FeatureExt-Option `0x02` und `fChangePassword` ergeben dasselbe Ablehnungsmuster, ohne den Adapter zu rufen (Test: der Test-Adapter zählt null Aufrufe); ungültige Offsets und Längen der LOGIN7-Felder ergeben eine kontrollierte Ablehnung ohne Absturz ([TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung) Negative, [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)). **Festlegung ohne Messung:** „kontrollierte Ablehnung“ heißt für ungültige Offsets und Längen, dass die Verbindung ohne Antwort geschlossen wird (`CloseReason.PROTOCOL_VIOLATION`, kein Adapteraufruf, kein Absturz); das feste Ablehnungsmuster (ERROR 18456 mit DONE) gilt für die Ablehnungen nach einem gültig gelesenen LOGIN7 (nicht unterstützte Modi, Eingaben über 128 Einheiten, Adapter). Ein Client, der ein unlesbares LOGIN7 sendet, bekommt keine Fehlerantwort; gemessen ist das Verhalten eines echten SQL Server hier nicht.

Nachweis-Pflichten für alle drei Punkte:

- [ ] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>` (Konvention nach `slice-qa-spec-abdeckung`); die Tests laufen in `make test` und sind grün.
- [ ] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet (KDoc des Elements). Die Zuordnung *Test | Festlegung* je Aussage steht in der Closure-Notiz. Gemessen am Referenzclient (Spike-Testserver, nicht am Produkt) sind nur: LOGINACK mit DONE genügt dem Client beim `Open`, die Collation-ENVCHANGE für RPC mit Zeichenparametern ([ADR-0005](../../adr/0005-rpc-und-prepared-statements.md)), genau ein ERROR mit DONE bei Ablehnung. Welche weiteren ENVCHANGE gesendet werden (Datenbank, Packet Size), der State-Wert der Ablehnung und das Verhalten bei mehrpaketigem LOGIN7 sind Festlegungen. Die Aussage "der Client akzeptiert das" steht in keinem KDoc und keiner Notiz.
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
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/` (Unterpackage, z. B. `handshake`) <!-- d-check:ignore (geplant: neues Package) --> | neu | Zustandsautomat, Übergangsmatrix, Login-Antwort-Bildung; importiert Domain, Security-Verträge (Adapter aus `config`) und die Codec-Typen des Packages `protocol`, nichts aus `transport`-Implementierung, `simulation`, `runtime` (ARC-004, SPEC-041) |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/` (Parser LOGIN7 aus `slice-codec-tds-paket`) | update, nur falls nötig | Falls der LOGIN7-Parser `fIntSecurity`, FeatureExt und `fChangePassword` nicht liefert, ergänzt dieser Slice sie (siehe §6) |
| `contracts/client-profile.md` | update | Login-Antwort-Folge als Festlegung des Servers, soweit sie das Clientprofil berührt; keine Zusage über die Annahme durch den Client |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/` <!-- d-check:ignore (geplant: neues Package) --> | neu | Übergangsmatrix (zulässig/unzulässig), Byte-Vektoren der Antworten, Byte-Gleichheit der Ablehnungen, Zerstückelung über Paket-Codec, Log-Inspektion am Anmeldepfad; nach SPEC-001, SPEC-012, SPEC-028, SPEC-046; `@spec`-Tag je Test |
Der Implementer-Agent erweitert die Liste in seinem ersten Lauf. Ergänzungen und Schnitt des ersten Laufs (Ist-Zustand gegen den Plan gemessen: Parser, Codec, `AuthAdapter` und `MessageReader` stehen auf `main`; der LOGIN7-Parser liefert `fIntSecurity`, FeatureExt-Kennungen und `fChangePassword`, eine Codec-Erweiterung entfällt):

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/session/` | neu | Automat als reine Funktion `step(Kontext, Ereignis) → (Folgekontext, Aktionen)` mit flacher Zustandsaufzählung (Nutzerentscheidung 2026-10-30), Matrix, PRELOGIN-Antwort, Login-Antworten, Abbildung `SecretChars` → `PasswordChars`; importiert aus `transport.framing` nur Wertetypen und Konstanten (`TdsMessage`, `PacketType`, `FramingLimits`, `PacketHeader`; keine Leser, Schreiber oder Kanäle); Treiber `HandshakeDriver` hinter der Schnittstelle `HandshakeChannel`, ohne Import eines Lesers, Schreibers oder Kanals |
| `src/main/kotlin/io/github/pt9912/tdswire/transport/messaging/` | update und neu | `MessageReader.useLimits` wechselt die Grenze am laufenden Leser, ohne Puffer und Assembler zu verwerfen (der Assembler trägt `limits` als veränderliche Eigenschaft); `TransportHandshakeChannel` setzt `HandshakeChannel` auf Leser, Schreiber und Kanal um (Transport importiert die Protocol-Schnittstelle, `spec/architecture.md` §2) |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/session/` | neu | Matrixtest, PRELOGIN-, Login-, Modi- und Treibertests mit `@spec`-Tag |

- Die Spannung „Automat importiert nichts aus der Transport-Implementierung“ löst die Schnittstelle `HandshakeChannel` (Package `protocol.session`): der Treiber ruft nur sie; der Grenzwechsel ist die Aktion `SwitchToSessionLimits` und `HandshakeChannel.useSessionLimits`.
- Offene Punkte aus [ADR-0004](../../adr/0004-session-zustandsautomat.md) und [ADR-0007](../../adr/0007-fehler-und-attention.md) entscheidet dieser Slice nicht; er wählt die einfachste Form und kennzeichnet sie im KDoc als „Festlegung ohne Messung / offen“: unzulässiger Übergang und ATTENTION im Handshake: Verbindung schließen ohne Antwort; das RESETCONNECTION-Bit wertet der Automat nicht aus; Fehler beim Lesen und Fristablauf: schließen ohne Antwort; der Sitzungszustand nach Fehlern, Cancel/Timeout als Fehler und das Schließen nach `TDS_SHUTDOWN` berührt der Handshake nicht.
- Reihenfolge: Zustandsautomat mit PRELOGIN, dann die Login-Antworten, dann die nicht unterstützten Modi.
- Die Nahtstelle zum Transport (vollständige Nachricht hinein; Antwort-Nachrichten und "Verbindung beenden" heraus; Ereignisse TLS und Frist) wird in diesem Slice als Schnittstelle des Packages `protocol` geschnitten und mit dem Planer der Transport-Welle abgestimmt (§6).
- `make detekt` läuft auch über `detektTest`; große `when`-Blöcke des Automaten werden im Code zerlegt, nie unterdrückt (`AGENTS.md` §3.2, §3.6).
## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Der Start-Trigger der Welle `welle-handshake-login` ist eingetreten, `slice-auth-adapter-benutzerliste` liegt in `done/` (erfüllt: die Adapter-Schnittstelle steht, siehe `done/slice-auth-adapter-benutzerliste.md`), [ADR-0004](../../adr/0004-session-zustandsautomat.md) steht im ADR-Index auf `Accepted` (erfüllt: expliziter Automat und `Mutex` je Sitzung sind entschieden; der Zuschnitt der Zustände ist **Entschieden (Nutzer, 2026-10-30)**: flach, siehe DoD-Punkt 1) und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2. Schnittvorschlag: Slice B1 = Zustandsautomat und PRELOGIN, Slice B2 = Login-Antworten und nicht unterstützte Modi; die Welle bekommt dann vier Slices.
- `in-progress` → `open` (blockiert — Carveout?): die Nachrichten-Schnittstelle zum Transport lässt sich mit der Transport-Welle nicht abstimmen.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün (einschließlich `make test`), der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung *Test | Festlegung* je Aussage sowie den Steering-Loop-Lerneintrag (geschärfte Regel, neuer Sensor oder benannte Spec-Lücke; eine Closure ohne Lerneintrag ist keine). Als benannte Spec-Lücke kommt in Frage, was `SPEC-028` offen lässt: „Genaue Felder, Reihenfolge, TDS-Version und Login-Feature-Extensions werden im versionierten Clientprofil durch Paketmitschnitt und Integrationstest fixiert.“

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- [ADR-0004](../../adr/0004-session-zustandsautomat.md) ist `Accepted` (expliziter Automat, `Mutex` je Sitzung); offen bleiben dort die Antwort auf unzulässige Übergänge (in DoD-Punkt 1 getragen); der Zuschnitt der Zustände ist **Entschieden (Nutzer, 2026-10-30)**: flach (eine Aufzählung wie im Spike, 10 Zustände × 14 Ereignisse gemessen; die Zweiteilung nach `SPEC-001` ist nicht gemessen), und die ADR führt ihn unverändert als offen sowie ATTENTION im Handshake (`slice-attention-cancel-timeout`), Sitzungszustand nach Fehler und `RESETCONNECTION` (`slice-fehler-zustandsuebergang`). Der Handshake-Automat entsteht in diesem Slice; ob der Zuschnitt später für den Anfragezustand trägt, zeigt `slice-sql-batch-dispatch`. — **Ausgang:** offen bis zur Closure.
- Die Token-Folge der Annahme ist nur teilweise gemessen (LOGINACK mit DONE genügt dem Client beim `Open`; Collation-ENVCHANGE für RPC), und nur am Spike-Testserver in TDS 7.4 ohne Pool. Die übrigen ENVCHANGE, der State-Wert der Ablehnung und die Statusbits des DONE sind Festlegungen; ein echter SQL Server ist nicht gemessen (`BEO-TDS/messung-am-echten-sql-server-fehlt`, 2×). Der einmal am echten Server beobachtete Client-Wert ist State 1, Class 14, Nummer 18456. — **Ausgang:** offen bis zur Closure.
- Mehrpaketiges LOGIN7, die TDS-8.0-Reihenfolge und Pooling sind ungemessen (`BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, 2×). Ob ein Client LOGIN7 überhaupt über mehrere Pakete sendet, ist nicht bekannt; der Test belegt, dass der Automat zerstückelte Eingabe verarbeitet, nicht dass ein Client sie erzeugt. — **Ausgang:** offen bis zur Closure.
- Die Byte-Gleichheit der Ablehnungen ist am Produkt nur mit dem Rohbytes-Test gemessen; am Referenzclient und am Produkt gilt sie als Anforderung (`SPEC-012`), gemessen ist sie am Spike-Testserver. — **Ausgang:** offen bis zur Closure.
- Nahtstelle zum Transport: Die Transport-Welle liefert den Listener und plant parallel; passt die Nachrichten-Schnittstelle nicht, wird sie im Bootstrap-Slice umgebaut. Wer sie schneidet und wo sie liegt (`protocol` oder `transport`), ist mit der Transport-Planung abzustimmen. — **Ausgang:** offen bis zur Closure.
- Liefert der LOGIN7-Parser aus `slice-codec-tds-paket` die Merkmale `fIntSecurity`, FeatureExt und `fChangePassword` nicht, entsteht eine Codec-Erweiterung in einem bereits geschlossenen Slice; der Plan nimmt sie in diesen Slice auf (Plan-Tabelle), falls klein, sonst als Folge-Slice. — **Ausgang:** offen bis zur Closure.
- Der LOGIN7-Parser aus `slice-codec-tds-paket` begrenzt Kennung und Passwort nicht auf 0 bis 128 UTF-16-Einheiten ([ADR-0009](../../adr/0009-authentifizierungsmodus.md), `contracts/auth-adapter.md`; Review F-7 des Codec-Slice): die Grenze gehört in den Anmeldepfad dieses Slice, nicht in den Codec, und ein Test belegt die Ablehnung darüber. — **Ausgang:** offen bis zur Closure.
- Es gibt noch keinen Observability-Adapter (ARC-009; Träger: Welle [welle-betrieb-diagnose](../welle-betrieb-diagnose.md), die diesen Anschluss ablöst); der Slice braucht einen minimalen Log-Anschluss für Ablehnungsgründe. Er darf die spätere Observability-Schnittstelle nicht vorwegnehmen und trägt keine Kennung und kein Passwort. — **Ausgang:** offen bis zur Closure.
- Das Parse-Ergebnis des LOGIN7 trägt das Passwort; keine automatisch erzeugte Textdarstellung darf es ausgeben ([TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), Codec-Slice trägt die Textdarstellung seines Typs, dieser Slice die der Automaten-Zustände und Ereignisse). — **Ausgang:** offen bis zur Closure.
- Der Adapteraufruf blockiert (ein Pbkdf2-Aufruf mit 210 000 Iterationen dauerte im Spike 54 ms, unter Last 23,6 s für die letzte Ableitung): Eine Messung zur Wirkung auf den Transport liegt nicht vor ([ADR-0011](../../adr/0011-pruefwertformat.md), `contracts/auth-adapter.md`); der Test des zweiten Liefer-Punkts belegt nur, dass der Automat den Aufruf nicht auf dem Transport-Thread ausführt, keine Last-Aussage. — **Ausgang:** offen bis zur Closure.
- Verwaiste Adapteraufrufe nach Fristablauf (Review F-1, Verifikation M-2): Ein laufender Aufruf ist nicht unterbrechbar und läuft nach `LOGIN_TIMEOUT` zu Ende, sein Ergebnis wird verworfen und das Passwort überschrieben. Behoben ist die Zahl: `AuthPool` begrenzt die gleichzeitigen Aufrufe (Voreinstellung 4 Plätze auf `Dispatchers.IO`, Festlegung ohne Messung), und ein Aufruf, der bei Fristablauf noch auf einen Platz wartet, beginnt nie (Tests `Verwaiste Adapteraufrufe sind begrenzt …`, `Ein wartender Aufruf beginnt …`). Nicht behoben: Ein nicht angemeldeter Client kann mit mehreren Verbindungen alle Plätze für die Dauer einer Ableitung belegen; regulär anmeldende Clients warten dahinter und laufen selbst in `LOGIN_TIMEOUT`. Die Zahl der Plätze ist nicht gemessen, eine Last-Aussage fehlt, und es gibt keine Drosselung wiederholter Anmeldeversuche. Träger der Drosselung ist `slice-qa-sicherheit` (Entscheidung offen, [ADR-0009](../../adr/0009-authentifizierungsmodus.md) §Offen). — **Ausgang:** weiter offen; Träger `slice-qa-sicherheit`.
- Die angekündigte Packet Size hat keinen Anwender (Review F-2): Der Server nennt sie per ENVCHANGE und führt sie in `AuthenticatedSession.packetSize`, aber nichts stellt den Paket-Schreiber des Transports darauf ein; der Schreiber arbeitet mit der Größe, mit der der Transport ihn baut. Fordert ein Client eine kleinere Größe als die des Schreibers, kann er Pakete über seiner Größe sehen. Dieser Slice baut die Anwendung nicht: Träger wäre der Transport, und die Nahtstelle zum Schreiber (Verdrahtung, Größe je Sitzung) ist dort nicht geschnitten. Das Verhalten steht als Lücke in `contracts/client-profile.md` und im KDoc von `LoginResponses.negotiatedPacketSize` und `AuthenticatedSession`; die Grenzen der Annahme (größer als der Paketkopf, höchstens `MAX_TDS_PACKET_BYTES`) sind getestet. Ein Träger-Slice ist nicht benannt; der nächste Berührungspunkt ist die Schreiber-Verdrahtung in `slice-backend-streaming-nachweis`. Das Beobachtungs-Register führt der Planner nach. — **Ausgang:** weiter offen; kein Träger-Slice.
- Der Schreibvorgang einer Antwort ist abbrechbar (kein `NonCancellable`): Ein Fristablauf oder Abbruch während `send` kann ein Paket halb schreiben; der Treiber schließt die Verbindung danach (Test `Fristablauf waehrend des Schreibens der Annahme schliesst den Kanal`). Ob ein Schreiben gegen Abbruch zu schützen ist, ist in [ADR-0004](../../adr/0004-session-zustandsautomat.md) offen und im KDoc von `HandshakeSession.expire` als solcher gekennzeichnet. — **Ausgang:** weiter offen; Träger ist [ADR-0004](../../adr/0004-session-zustandsautomat.md) (offener Punkt).
- Eine `CancellationException` aus dem Adapter zählt nicht als Ablehnung (Festlegung dieser Fix-Runde, Review F-3): Der Treiber reicht sie weiter und schließt den Kanal; der Client bekommt keine Antwort. Die Alternative (wie eine Adapter-Ausnahme mit dem Ablehnungsmuster beantworten, Verifikation M-1) ist nicht gewählt, weil der Aufrufer sonst einen Abbruch seiner eigenen Coroutine nicht von einem Adapterfehler unterscheiden kann; eine Entscheidung des Planers/Architekten steht aus, falls die andere Form gewollt ist. — **Ausgang:** weiter offen bis zur Entscheidung.
- Größe: drei Liefer-Punkte, der erste mit Matrix, PRELOGIN-Richtlinie und zwei Protokollpfaden. — **Ausgang:** offen bis zur Closure. Rückführung und Schnitt in §4.
- Der Wechsel der Rahmen-Grenze beim Login (Konstruktorparameter `limits` des `MessageReader`, kein Wechsel am laufenden Leser; Review F4 und KDoc des Lesers): ein Austausch des Lesers könnte Folgebytes aus Puffer und Assembler verwerfen. — **Ausgang:** offen bis zur Closure; Träger ist der erste Liefer-Punkt.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Das Package `protocol` (Handshake) ist ein Pfad-Cluster (Achse 3), trägt aber keine eigene Strukturregel (Achse 1) und keine eigene Inventur-Linie (Achse 2): eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, acht Beobachtungen, Stand des gemergten `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` — 1×, offen. Treffer: Loginspektion am Anmeldepfad und Byte-Gleichheit der Ablehnung als Vorleistung; Zeitgleichheit und Drosselung bleiben bei `slice-qa-sicherheit`.
- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 2×, offen. Treffer: Token-Folge, DONE-Statusbits und State der Ablehnung (§6). Erreicht die Closure dieses Slice einen dritten Beleg, wird die Beobachtung mit dieser Welle zur Lücke und braucht einen Slice (Träger bleibt `slice-qa-reproduzierbarkeit`).
- `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` — 2×, offen. Treffer: mehrpaketiges LOGIN7 und TDS-8.0-Reihenfolge (§6); dieselbe 3×-Prüfung bei der Closure.
- `BEO-TDS/aussagen-ueber-messung-hinaus` — 6×, Stand *geplant*, Träger `welle-entscheidungen-grundlage`. Treffer: Zuordnung *Test | Festlegung* (§2, §6).
- `BEO-TDS/adapter-festlegungen-ohne-slice` — 1×, offen. Treffer: Der Slice nutzt die Signatur aus `slice-auth-adapter-benutzerliste`.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Treffer: `ADR-*`- und `TDS-*`-Kennungen stehen als Link.
- Ohne Treffer: `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/vergleichslauf-d-ungeklaert`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
