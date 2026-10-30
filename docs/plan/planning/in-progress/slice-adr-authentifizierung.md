# Slice slice-adr-authentifizierung: ADR-0009 entscheiden: Authentifizierungsmodus und Authentifizierungsadapter

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-entscheidungen-grundlage

**Bezug:** [TDS-FA-02](../../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0009](../../adr/0009-authentifizierungsmodus.md)

**Berührte Spec-Stellen:** [SPEC-004](../../../../spec/spezifikation.md#spec-004--testprofil) · [SPEC-012](../../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) · [SPEC-051](../../../../spec/spezifikation.md#6-externe-verträge) · [TDS-FA-02.a](../../../../spec/spezifikation.md#tds-fa-02a--login7-und-sitzung) · [TDS-FA-09.a](../../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose)

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-10-29.

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** [ADR-0009](../../adr/0009-authentifizierungsmodus.md) ist mit einem Nachweis entschieden und `Accepted`; die offene Frage lautet: Modus, Adapter-Schnittstelle und Umgang mit Geheimnissen.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Produktionscode der gewählten Option — er entsteht in einem Slice der funktionalen Welle, weil dieser Slice entscheidet und nicht baut.
- Änderungen am Lastenheft — eine ADR schärft höchstens die Spezifikation; ein Widerspruch zum Lastenheft ist ein eigener Vorgang.

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [x] Spike durchgeführt; seine Ergebnisse (Messwerte, Matrix, Befund) stehen in der ADR. Der Spike-Code ist temporär und nach der Entscheidung gelöscht.
- [x] [ADR-0009](../../adr/0009-authentifizierungsmodus.md) auf `Accepted`, Option gewählt, Geschichte und ADR-Index nachgezogen.
- [x] Spec-Stellen aus dem `Schärft:`-Feld der ADR an die Entscheidung angepasst.
- [x] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
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
| `docs/plan/adr/0009-authentifizierungsmodus.md` | update | Entscheidung, Status, Geschichte |
| `docs/plan/adr/README.md` | update | Indexzeile |
| Spike (temporär, nicht im Repo behalten) | neu, danach löschen | Ergebnisse gehen in die ADR |
| `spec/spezifikation.md` | update | Stellen aus `Schärft:` |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

**Zwischenstand der Messung** (Spike per Dockerfile ohne Mount; `Microsoft.Data.SqlClient` 7.1.1, .NET SDK 10.0.403; Testserver in C# im Client-Container, Spike-Code temporär und nicht im Repo; Passwörter sind klare Dummy-Werte, im Bericht stehen nur Längen und das Ergebnis der Entschleierung; Zertifikat `CN=localhost`, selbstsigniert, `TrustServerCertificate=true`). Der Testserver sendet PRELOGIN (ENCRYPT_NOT_SUP für den unverschlüsselten Testmodus, ENCRYPT_ON bzw. ENCRYPT_OFF im TLS-Tunnel), liest LOGIN7 und antwortet mit eigenen Tokens. In jedem Fall, in dem der Client verbunden hat, war es genau ein Verbindungsversuch: der Client wiederholt keinen der gemessenen Fehler.

**LOGIN7 des Referenzclients** (SQL-Authentifizierung, `Encrypt=false`, Nachricht 509 Byte, `TDSVersion=0x74000004`, `PacketSize=8000`, `ClientProgVer=0x06000000`; im Tunnel mit `Encrypt=true` dieselbe Struktur und Länge):

| Bereich | Offset | Beobachtung |
|---|---|---|
| Festteil | 0 bis 35 | Flags bei 24 bis 27: OptionFlags1 `0xE0` (fUseDB, fDatabase, fSetLang), OptionFlags2 `0x03` (fLanguage, fODBC), TypeFlags `0x00`, OptionFlags3 `0x10` (fExtension); ConnectionID, ClientTimeZone und ClientLCID sind `0` |
| HostName | 36 (ib, cch) | Daten ab Byte 94, dem Ende der Offset-Tabelle; Wert ist der Rechnername des Clients, mit `Workstation ID=WS-1` der Text `WS-1` |
| UserName | 40 | UTF-16LE, Länge in UTF-16-Einheiten |
| Password | 44 | UTF-16LE, verschleiert, Länge in UTF-16-Einheiten |
| AppName | 48 | Standard `Core Microsoft SqlClient Data Provider`, mit `Application Name=AppX` der Text `AppX` |
| ServerName | 52 | der `Data Source`-Wert der Verbindung |
| Extension | 56 | 4-Byte-Zeiger auf die FeatureExt-Liste am Ende der Daten (mit OptionFlags3 `0x10`) |
| CltIntName | 60 | `Core Microsoft SqlClient Data Provider`, unabhängig von `Application Name` |
| Language | 64 | leer, mit `Current Language=German` der Text `German` |
| Database | 68 | leer ohne `Database=`, sonst der Wert |
| ClientID | 72 | 6 Byte, je Verbindung wechselnd |
| SSPI | 78 (ib, cb) | `cb=0` bei SQL-Authentifizierung; `cbSSPILong` bei 90 |
| AtchDBFile | 82 | leer, mit `AttachDBFilename` der Pfad |
| ChangePassword | 86 | leer, sonst das neue Passwort verschleiert |

Leere Felder zeigen alle auf denselben Offset (Länge `0`); die Daten stehen in Feldreihenfolge.

| Fall | Beobachtung |
|---|---|
| Verschleierung | je Byte XOR `0xA5`, danach Nibble-Tausch (Empfänger: erst XOR, dann Tausch) über UTF-16LE; am echten Paket in allen Fällen bestätigt, die Entschleierung ergibt den Dummy-Wert |
| `Encrypt=false` (ENCRYPT_NOT_SUP), Annahme nur mit LOGINACK und DONE (66 Byte) | `Open` gelingt: `ServerVersion=16.00.4000` aus der LOGINACK-Version, `Database` leer, `PacketSize=8000`; danach sendet der Client nichts; der Client verlangt kein FEATUREEXTACK, obwohl er FeatureExt-Optionen sendet |
| Annahme mit ENVCHANGE (Datenbank, Sprache, Paketgröße) und INFO | `Database='master'` aus dem ENVCHANGE; ohne ENVCHANGE zeigt der Client den Wert aus der Verbindungszeichenfolge (`mydb`) |
| Ablehnung: ERROR `0xAA` (Nummer 18456, State 1, Class 14, Meldung `Login failed for user 'sa'.`, Servername, Prozedur leer, Zeile 1) und DONE mit Status `0x0002` | sofortige `SqlException` (5 ms): `Number=18456`, `State=1`, `Class=14`, `LineNumber=1`, `Procedure=''`, `Message` wie gesendet, `Errors=1`, `IsTransient=false`; `Server` zeigt den `Data Source`-Wert des Clients, nicht den Servernamen aus dem Token; der Client schließt die Verbindung, kein zweiter Versuch; gleiches Ergebnis, wenn der Server die Verbindung offen lässt |
| Ablehnung im TLS-Tunnel (`Encrypt=true`, ENCRYPT_ON, TLS 1.2 im Testserver) | identisch: `Number=18456`, `State=1`, `Class=14`; die Annahme im Tunnel gelingt; bei ENCRYPT_OFF kommt die Antwort im Tunnel an (Annahme und Ablehnung wie oben), eine unverschlüsselt gesendete Antwort bricht der Client mit Klasse 20 ab („Cannot determine the frame size“) |
| nur ERROR, kein DONE, Server schließt | `SqlException` nach etwa 850 ms mit zwei Fehlern: 18456 und ein Verbindungsfehler Klasse 20; `Message` verkettet beide |
| nur DONE mit Fehlerstatus, kein ERROR | `Number=0`, `Class=11`, „A severe error occurred on the current command“ |
| Server schließt nach LOGIN7 ohne Antwort | `Number=0`, `Class=20`, „A connection was successfully established with the server, but then an error occurred during the login process“ |
| LOGINACK ohne DONE | der Client wartet auf DONE; Abbruch erst, als der Server nach 4,5 s schließt (`Class=20`, dieselbe Meldung); LOGINACK allein beendet die Anmeldung nicht |
| Class 11, 14, 16, 20; State 0, 1, 255; Nummern 50000 und 4060; leere Meldung; Prozedur `sp_login` | alle Werte erscheinen unverändert in `SqlException`; keine Klasse löst einen Wiederholungsversuch aus; die leere Meldung ergibt einen leeren `Message`-Text |
| zwei ERROR-Tokens oder INFO vor ERROR | `Errors=2`; `Message` verkettet beide Texte; für die Ablehnung genau ein ERROR senden |
| Kennung mit Unicode und Backslash, leere Kennung, Kennung mit 128 Zeichen | jeweils unverändert im Feld; leere Kennung: `cch=0`, `Open` gelingt |
| leeres Passwort | `cch=0`, `Open` gelingt |
| Passwort mit 23 Zeichen und Sonderzeichen (`;`, `=`, `"`, `'`, `{}`, `\`, Tab), Passwort mit Umlaut, Euro, CJK und Surrogatpaar (15 UTF-16-Einheiten) | entschleiert bytegleich zum Eingabewert; die Länge zählt UTF-16-Einheiten, nicht Zeichen |
| Passwort mit 128 Zeichen | gesendet, `cch=128`, Nachricht 745 Byte |
| Passwort mit 129 oder 1000 Zeichen, Kennung mit 129 Zeichen | der Client wirft `ArgumentException` („exceeds its limit of '128'“) vor dem Verbindungsaufbau; der Server sieht keine Verbindung |
| `Database=db1`, `Application Name`, `Workstation ID`, `ApplicationIntent=ReadOnly`, `Packet Size=4096`, `AttachDBFilename` | die Felder stehen wie in der Tabelle oben; ReadOnly setzt TypeFlags `0x20` und die FeatureExt-Option `0x08` mit Wert `01`; `Packet Size` setzt `PacketSize` im LOGIN7 |
| Standardwerte mit Pooling an | nach LOGIN7 nichts Zusätzliches vom Client |
| `Multiple Active Result Sets=true` | PRELOGIN-MARS `01`; nach der Annahme sendet der Client sofort ein Paket mit Typbyte `0x53` (SMP, Multiplexing), nicht weiter ausgewertet |
| FeatureExt bei SQL-Authentifizierung | Optionen `0x10` (120 Byte, beginnt mit UTF-16-Text), `0x01`, `0x04` (`03`), `0x05`, `0x09` (`02`), `0x0A`, `0x0B`, `0x0D` (`01`), `0x0E` (`01`), `0x0F`; die Namen sind hier nicht am Paket verifiziert |
| `Authentication=SqlPassword` | LOGIN7 wie ohne die Option |
| `Authentication=ActiveDirectoryPassword`, `…ServicePrincipal`, `…Integrated`, `…Default` (Server-PRELOGIN-Antwort ohne und mit FEDAUTHREQUIRED, Password auch im Tunnel) | der Client sendet LOGIN7 in jedem Fall, mit leerem UserName und Password (`cch=0`), OptionFlags2 `0x03` (kein fIntSecurity) und FeatureExt `0x02` mit 2 Byte: `0401` (Server ohne FEDAUTHREQUIRED), `0501` (mit; Password und ServicePrincipal), `0502` (Integrated), `0503` (Default); die Ablehnung mit 18456 zeigt der Client als gewöhnliche `SqlException` 18456 |
| `Integrated Security=true` ohne Kerberos im Container | der Client verbindet per TCP und bricht ohne ein Byte ab: `Number=0`, `Class=11`, `Procedure='GenClientContext'`, „The target principal name is incorrect. Cannot generate SSPI context.“; der Server sieht kein PRELOGIN |
| `Integrated Security=true` mit MIT-Kerberos-KDC und Ticket im Container (Principal `MSSQLSvc/localhost.:14330`) | LOGIN7 mit OptionFlags2 `0x83` (fIntSecurity), UserName und Password leer, SSPI-Block 770 Byte ab Offset 310 (`cbSSPILong=0`), beginnt mit `60 82 02 FE 06 06 2B 06 01 05 05 02` (GSS-API-Token, OID 1.3.6.1.5.5.2); die Ablehnung mit 18456 zeigt der Client als gewöhnliche `SqlException` 18456 |
| `SqlCredential` (`Open`) mit `Encrypt=false` und im Tunnel | LOGIN7 wie bei der Verbindungszeichenfolge; `Open` gelingt |
| Passwortwechsel (`SqlConnection.ChangePassword` mit `SqlCredential` und mit Zeichenfolge) | OptionFlags3 `0x11` (fChangePassword), das neue Passwort im Feld ChangePassword (verschleiert, Entschleierung bestätigt); das alte Passwort steht weiter im Feld Password; bei Annahme (LOGINACK, DONE) kehrt die Methode zurück, der Client schließt die Verbindung |
| Ablehnung mit Nummer 18488 oder 18487 | `SqlException` mit der gesendeten Nummer und Meldung, ohne Sonderbehandlung (auch beim Passwortwechsel) |
| Ablehnung für unbekannte Kennung und für falsches Passwort, Meldung `Login failed for user '<Name>'.`, derselbe State 1 | `SqlException` mit denselben Feldern (`Number`, `State`, `Class`, `LineNumber`, `Procedure`); die Antwort-Bytes unterscheiden sich nur im Namen der Kennung; der Grund steht nur im Log des Servers |
| dieselbe Ablehnung mit fester Meldung `Login failed.` | die Antwort-Bytes sind für beide Fälle identisch (SHA-256 gleich), der Client zeigt in beiden Fällen dasselbe |
| Ablehnung mit State 5 für unbekannte Kennung und State 8 für falsches Passwort | der Client reicht den State durch: die Anwendung sieht `State=5` bzw. `State=8` und kann beide Fälle unterscheiden |

**Befunde**

- Alle drei Optionen aus [ADR-0009](../../adr/0009-authentifizierungsmodus.md) bekommen im LOGIN7 dieselben Eingaben: Kennung und Passwort nach der Entschleierung, Datenbank, Anwendungsname, Rechnername, Intent. Der Referenzclient verlangt weder ein FEATUREEXTACK noch einen bestimmten Servernamen in der Antwort.
- Die Verschleierung ist reversibel und kein Schutz; im unverschlüsselten Testmodus (ENCRYPT_NOT_SUP) liegt das Passwort damit lesbar auf der Leitung, im Tunnel steht dieselbe Nachricht im TLS (zu [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)).
- Kennung und Passwort sind clientseitig auf 128 Zeichen begrenzt; das Feld zählt UTF-16-Einheiten. Ein Passwort kann leer, Unicode oder mit Sonderzeichen sein; der Adapter bekommt UTF-16, nicht Bytes in einer Kodierung nach Wahl.
- Eine Ablehnung braucht genau ein ERROR-Token (Nummer 18456, State, Class 14) und danach DONE mit Fehlerstatus; der Client macht daraus eine `SqlException` mit denselben Werten und versucht es nicht erneut. Weitere Tokens vor oder nach dem ERROR verändern `Message` und `Errors`.
- Für [SPEC-012](../../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) ist die Gleichheit der Ablehnung an den Antwort-Bytes erreichbar: bei fester Meldung ohne Namen sind die Bytes beider Fälle gleich; mit dem Namen der Kennung (wie bei SQL Server) sind sie es für dieselbe Kennung. Der State kommt unverändert in der Anwendung an; ihn einheitlich zu senden ist Sache des Servers. Zeitgleichheit ist nicht gemessen.
- Nicht unterstützte Modi sind für den Server am LOGIN7 erkennbar (SSPI-Block mit fIntSecurity, FeatureExt `0x02` bei leerem UserName); eine Ablehnung mit 18456 zeigt der Client wie eine falsche Anmeldung. Der Server bekommt ein LOGIN7 mit SSPI-Block nur, wenn der Client sein Token erzeugen konnte.
- Dem Client genügt zur Entscheidung die Ablehnung (ein ERROR, ein DONE) oder die Annahme (LOGINACK, DONE); LOGIN7 trägt für die Optionen A, B und C dieselben Felder. Ob ein Backend die Zugangsdaten sieht (Option C), ist am Client nicht abzulesen.
- Folgerungen für die Adapter-Schnittstelle aus den Messwerten: Eingabe Kennung (UTF-16, 0 bis 128 Zeichen, auch leer) und Passwort als entschleiertes Zeichen- oder Byte-Feld (0 bis 128 UTF-16-Einheiten, auch leer; nach dem Aufruf zu löschen); Verbindungskontext (Datenbank, Anwendungsname, Rechnername, Intent, Verschlüsselungsmodus, Auth-Merkmale: fIntSecurity, FeatureExt, ChangePassword mit neuem Passwort); Ausgabe akzeptiert oder abgelehnt, bei Ablehnung ein Grund nur für das Log, getrennt vom festen Antwortmuster an den Client (Nummer, State, Class, Meldung).

**Nicht gemessen:** die Server-SSPI-Antwort (Token `0xED`) und mehrstufige SSPI-Austausche; das Entra-ID-Token (FEDAUTHINFO-Antwort und Token-Nachricht, nach Vorgabe nicht ausgelöst); der Zeitunterschied zwischen unbekannter Kennung und falschem Passwort; TDS 8.0 (`Encrypt=Strict`) mit SQL-Authentifizierung; TLS 1.3 im Tunnel mit dem Spike-Server (das NewSessionTicket landet in einem Paket vom Typ `0x12`, siehe `contracts/security-profile.md`; der Spike lief im Tunnel mit TLS 1.2); Verhalten bei Pooling mit `ResetConnection`; LOGIN7 über mehrere Pakete; die Namen der FeatureExt-Optionen am Paket; andere Clients; ein echter SQL Server als Referenz für State und Meldung; transiente Fehler (kein Fall hat einen Wiederholungsversuch ausgelöst).

**Wiederholung:** ein Dockerfile mit dem Testserver, `docker build --progress=plain -t spike-auth <Verzeichnis>`, ohne Mount; für den Kerberos-Fall ein zweites Dockerfile mit MIT-Kerberos-KDC und `kinit` im Container. Der Spike-Code liegt nicht im Repo.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `slice-adr-clientprofil` liegt in `done/`.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Die LOGIN7-Felder des Referenzclients lassen den gedachten Modus nicht zu.

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün und die Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Die LOGIN7-Felder des Referenzclients lassen den gedachten Modus nicht zu. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield); der Slice berührt Spec und ADR, eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/` trägt nur die `README.md`): keine Treffer.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
