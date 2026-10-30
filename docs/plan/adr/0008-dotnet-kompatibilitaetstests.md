# ADR-0008: Referenzclient und .NET-Kompatibilitätstests

**Status:** Proposed

**Datum:** 2026-10-29

**Autor:** pt9912

**Bezug:** [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose)

**Schärft:** [SPEC-045](../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig), [SPEC-050](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge), [SPEC-021](../../../spec/spezifikation.md#6-externe-verträge), [SPEC-025](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge), [SPEC-032](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge)

---

## Kontext

Unit-Tests der TDS-Kodierung allein belegen nicht, dass SqlConnection.Open, ExecuteReader, Parameter und Cancel funktionieren.

Diese ADR legt fest, **wie** die Kompatibilität nachgewiesen wird (Testmatrix und Ausführung in der CI); den Inhalt des Clientprofils entscheidet [ADR-0001](0001-tds-version-und-clientprofil.md).

Gemessen in einem Spike per Dockerfile ohne Mount (Testprojekt mit `Microsoft.Data.SqlClient` 7.1.1 auf `mcr.microsoft.com/dotnet/sdk:10.0.403`, Digest `sha256:de4fe90a…`; Docker 29.9.2 mit BuildKit auf einem Host mit 20 CPUs, 31 GiB Speicher, x86_64; Zahlen aus einem einzelnen Lauf je Zeile). Der Spike-Code ist gelöscht; die Messwerte stehen nur hier. Zwei Messreihen:

**Option B (Stub-Server, ein oder zwei Container).** Der Stub beantwortet PRELOGIN (`ENCRYPT_NOT_SUP`), LOGIN7 (LOGINACK, DONE) und ein `SELECT 1` (COLMETADATA, ROW, DONE); die Tests prüfen `Connection.Open` und `ExecuteScalar` mit `Encrypt=false`. TLS, TDS 8.0, Parameter und Cancel sind dort nicht Teil des Spikes: gemessen sind drei Tests (`Open` und `SELECT 1`) gegen einen Minimal-Stub, nicht der Pflichtumfang der Matrix unten.

| Messung | B .NET-Referenztests im Container |
|---|---|
| Kaltbau (`--no-cache`, Basis-Image lokal, NuGet aus dem Netz) | 21,6 s (Restore 7,0 s, Build 4,3 s, Test 1,9 s, Image-Export 6,7 s) |
| Aufbau warm | vollständig gecacht 0,9 s; nur Testquelltext geändert 9,5 s; `.csproj` geändert (Restore läuft neu) 23,8 s |
| Image-Größe | 1,59 GB auf der Platte, 464 MB Inhalt; die Basis `sdk:10.0.403` allein 1,31 GB, 359 MB Inhalt |
| Netzbedarf | Basis-Image (Pull nicht gemessen) und NuGet-Restore von `api.nuget.org` bei jedem Lauf mit geänderter `.csproj` oder `packages.lock.json`; `docker build --network none` scheitert dann mit `NU1301`; ein reiner Quelltextlauf braucht kein Netz, solange der Restore-Layer im Cache liegt (offline nicht belegt, weil eine andere Netzwerk-Einstellung den Cache-Schlüssel ändert) |
| Host-Voraussetzung | Docker und `make`; auf dem Host liegt kein `dotnet` |
| `Open` + `SELECT 1`, ein Container (Stub im Testprozess; Laufzeit der Pflicht-Matrix nicht gemessen) | 2,9 s gesamt, 3/3 Tests, `dotnet test` meldet 226 ms |
| Dasselbe, zwei Container in eigenem Docker-Netzwerk (kein Mount, kein veröffentlichter Port) | 3,6 s gesamt, 3/3 Tests |
| 4 Läufe gleichzeitig | ein Container 4,8 s, zwei Container 10,5 s; alle 4 grün, keine Reste |
| Abbruch (Signal an die Prozessgruppe, wie Strg-C) | INT und TERM: ein und zwei Container räumen vollständig auf (`trap` im Rezept); KILL: es bleiben Client-Container, bei zwei Containern zusätzlich Server-Container und Netzwerk |
| Ergebnis ohne Mount | `docker run`: Exit-Code 0 oder 2 (rot) und Zusammenfassung auf stdout; `docker build --target test`: Exit-Code 1 bei rotem Test, Zusammenfassungszeile im Build-Log |

Befunde zu B:

- Der Restore ist mit `packages.lock.json` und `--locked-mode` gesperrt: eine geänderte Version in der `.csproj` bricht mit `NU1004`, ein verfälschter `contentHash` mit `NU1403`. Die Lock-Datei entsteht in einer Dockerfile-Stage und kommt per `docker build --output` heraus (wie `make deps-lock` für Gradle). Das Lock des Testprojekts führt 37 `contentHash`-Einträge, auch für transitive Pakete.
- Der Clean-Build läuft nur mit Docker und `make`: Dockerfile-Frontend und Basis-Image sind per Digest festlegbar, die Pakete per Lock und Hash, die Quelle über eine `nuget.config` mit `<clear />` und einer einzigen Quelle samt `packageSourceMapping`. Das Tag `10.0.403` löst auf denselben Digest auf, den das lokale `sdk:10.0` trägt.
- NuGet Audit fragt beim Restore online nach Schwachstellen. `Microsoft.Data.SqlClient` 7.1.1 zieht `System.Security.Cryptography.Pkcs` 9.0.18 nach, für das zum Messzeitpunkt `NU1902` und `NU1903` gemeldet wurden; mit `-warnaserror` bricht der Build deshalb. Ein Ergebnis, das vom Tag der Abfrage abhängt, ist nicht reproduzierbar; der Spike setzte `NuGetAudit=false`.
- Die Signaturprüfung (`signatureValidationMode=require`) greift unter Linux: ohne `trustedSigners` scheitert der Restore mit `NU3034`. Mit dem Repository-Zertifikat von nuget.org als einzigem Eintrag scheiterten weiter Pakete mit „signed but not by a trusted signer“; eine funktionierende Liste ist nicht gebaut. Das Autorenzertifikat von `Microsoft.Data.SqlClient` 7.1.1 ist Microsoft (SHA-256 `9A1B131B…`), das Repository-Zertifikat `1F4B311D…`.
- Ein Test-Stage im Dockerfile (`RUN dotnet test`) liefert den Exit-Code des Builds, ist nach einem grünen Lauf aber gecacht (0,9 s ohne Ausführung); ein Test, der Netz oder Zeit braucht, braucht `--no-cache-filter` oder ein `docker run`. Im Stage-Build ist nur ein Stub im Testprozess möglich.
- Ein Container ist einfacher: kein Netzwerk, kein Bereitschaftstest, kein Aufräumen von Hand. Zwei Container trennen Server und Client als Prozesse, wie es der echte Server und der echte Client später sind, kosten rund 0,7 s je Lauf, brauchen ein Netzwerk je Lauf (Namen mit PID), eine Bereitschaftsschleife auf das Server-Log und ein `trap`; bei `SIGKILL` bleiben Container und Netzwerk stehen.
- Beobachtung aus dem Transport-Spike ([ADR-0003](0003-transportframework.md)): der JDK-Server schickt bei TLS 1.3 ein NewSessionTicket, das der .NET-Client im TDS-Paket vom Typ `0x12` nicht lesen kann. Ob ein JVM-Test mit einem JDK-Client das ebenfalls gemeldet hätte, ist nicht gemessen; Option A wurde in keinem Spike gebaut oder gemessen.
- Der Stub antwortete mit einem Minimum an Tokens (LOGINACK ohne ENVCHANGE und ohne FEATUREEXTACK) und der Client akzeptierte es. Das sagt nichts über die vollen Antworten des Servers.

**Option D (echter SQL Server, ein Lauf).** Image `mcr.microsoft.com/mssql/server:2022-latest`, Digest `sha256:cf8f0ab2ebe61feec2eed2629061a07d247f791c2a8ddda74a4ac9f06a291718`, SQL Server 2022 (RTM-CU27-GDR) 16.0.4305.5, Ubuntu 22.04, `linux/amd64`, im Image erstellt am 2026-10-03. Der Pull gelang aus dem Netz ohne Anmeldung; seine Dauer wurde nicht gemessen. Der Server lief als eigener Container in einem eigenen Docker-Netzwerk (`docker run -d`, `ACCEPT_EULA=Y`, `MSSQL_PID=Developer`, SA-Kennwort per Umgebungsvariable, kein Mount, kein veröffentlichter Port), der Referenzclient als zweiter Container aus einem Dockerfile-Build (`docker build` 11,5 s kalt für den Clientteil). Die Container und das Netzwerk dieses Spikes wurden danach entfernt; was am Host an Images liegt, ist nicht Teil der Aussage.

| Messung | D echter SQL Server |
|---|---|
| Image-Größe | 2,35 GB auf der Platte, 627 MB Inhalt |
| Start bis Log-Meldung „ready for client connections“ | 7,7 s (kalter Start eines neuen Containers; Datenbanken werden dabei angelegt) |
| Speicher im Leerlauf | 933 MiB (`docker stats`) |
| Referenzlauf (Schritte siehe unten; ihre Zahl ist ohne den gelöschten Code nicht nachprüfbar) | 1,6 s gesamt einschließlich Container-Start des Clients |
| Wiederholung | `Encrypt=false` und `Encrypt=true;TrustServerCertificate=true` lieferten bis auf die Cancel-Dauer (505 ms, 504 ms) identische Ausgaben |

Verhalten des echten Servers, gemessen mit `Microsoft.Data.SqlClient` 7.1.1 (je Schritt eine Zeile; Auszug der Werte, die der Simulation Erwartungen setzen):

- Anmeldung: `ServerVersion` 16.00.4305, `PacketSize` 8000. Falsches Kennwort für `sa` und unbekannter Benutzer ergeben beide `Number=18456, State=1, Class=14`, die Meldung nennt nur den Benutzernamen („Login failed for user '…'“). Der Client sieht also keinen Unterschied zwischen beiden Fällen (zu `SPEC-012`); der interne State des Servers ist hier nicht sichtbar.
- Typen: `decimal(10,2)`, `bit`, `float`, `datetime2(3)`, `date`, `int`, `nvarchar` mit Unicode (inklusive Zeichen außerhalb der BMP) kommen unverändert an; `NULL` als `DBNull`.
- Ergebnisse: ein leeres Ergebnis liefert Spaltenbeschreibung ohne Zeilen; zwei Anweisungen in einem Text liefern zwei Resultsets; `UPDATE` und `DELETE` melden die Zahl betroffener Zeilen (2, 3).
- Dreiwertige Logik: `name = NULL` trifft keine Zeile, `name IN (N'a', NULL)` trifft nur die Zeile mit `a`; Spaltennamen sind ohne Beachtung der Groß-/Kleinschreibung gleich (`NAME` findet `name`); `"id"` und `"dbo"."t"` als Bezeichner sind mit dem Standardverhalten des Clients (`QUOTED_IDENTIFIER ON`) gültig. Das bestätigt die vorläufigen Annahmen aus [SPEC-036](../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) und [SPEC-035](../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) für diese Stichprobe, nicht allgemein.
- Parameter: `SELECT @p + 1` mit `Int` liefert 42, ein `NULL`-Parameter `DBNull`, `NVarChar` mit `ü€` kommt unverändert zurück.
- Fehler: unbekannte Tabelle `Number=208, State=1, Class=16`; Division durch Null in einem `UNION ALL` `Number=8134, State=1, Class=16`; die Verbindung bleibt danach geöffnet und nutzbar. Ob der Server beim zweiten Fehler zuvor Zeilen gesendet hat, unterscheidet dieses Szenario nicht (kein Mitschnitt); der Fall „Fehler nach Zeilen“ ist damit nicht belegt.
- OUTPUT-Parameter und Rückgabewert einer Prozedur: `out=7`, `ret=3`.
- Cancel: `Command.Cancel()` nach 500 ms bei `WAITFOR DELAY` ergibt eine `SqlException` mit `Number=0, State=0, Class=11` und dem Text „A severe error occurred on the current command … Operation cancelled by user.“ nach rund 505 ms; die Verbindung ist danach `Open` und beantwortet die nächste Abfrage. Der Fehlerzustand `Number=0` entstammt dem Client, nicht einer Server-ERROR-Meldung; was der Server auf der Leitung sendet (DONE mit Attention-Bit), wurde nicht mitgeschnitten.
- Abweichung von den Annahmen des Entwurfs: `Encrypt=Strict` (TDS 8.0) scheitert gegen diesen Server mit `Class=20, Number=0` („error during connection initialization process before login“). SQL Server 2022 bietet TDS 8.0 nicht an; der TDS-8.0-Pfad ist an dieser Referenzinstanz also nicht abgleichbar. Der Server-Image-Tag für TDS 8.0 (etwa eine neuere SQL-Server-Version) wurde nicht ermittelt.
- `Encrypt=true;TrustServerCertificate=true` funktionierte; laut Server-Log sind TLS 1.0 bis 1.2 erlaubt, TLS 1.3 ist nicht aufgeführt (die ausgehandelte Version wurde nicht mitgeschnitten). Gegenüber [SPEC-023](../../../spec/spezifikation.md#6-externe-verträge) (Mindestversion 1.2, TLS 1.3 bevorzugt) ist die Referenzinstanz bei TLS 1.3 also kein Maßstab.

**Wiederholung durch den Review** ([Report](../../reviews/2026-10-30-7fe5583-052712d-adr-0008.md); ein Host, ein Lauf je Zeile, eigener Client statt des gelöschten Codes). Bestätigt: Image-Digest und Größe, Start (8,2 s), Speicher (922 MiB), `ServerVersion` und `PacketSize`, 18456/State 1/Class 14, 208 und 8134, Cancel (rund 505 ms, Verbindung danach `Open`), `Encrypt=Strict` scheitert mit Class 20, Log nennt TLS 1.0 bis 1.2. Nicht wiederholt: UPDATE-/DELETE-Zeilenzahlen, Parameter, OUTPUT und Rückgabewert, `IN (…, NULL)`, quotierte Bezeichner, `float`, `datetime2(3)`, `date`, Pull-Dauer, kalter NuGet-Cache. Diese Werte sind einmal gemessen und kein zweites Mal belegt.

**Messgrenzen.** Ein Lauf je Zeile, ein Host, ein Image-Stand (`2022-latest` ist ein beweglicher Tag; erst der Digest fixiert). Nicht gemessen: Dauer des Pulls beider Images, kalter NuGet-Cache auf einem frischen Runner, Verhalten auf Runnern mit weniger Speicher als 31 GiB (hier nicht geprüft), arm64-Hosts (das Image ist `amd64`), Mitschnitt-Lauf nach [SPEC-045](../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig), Schema- und Ausnahmelistenpflege, Gradle-Teil von Option A (es gibt noch keinen Test-Stage), TLS 1.3 und TDS 8.0 gegen den Server dieses Repos, RPC-Typen jenseits der oben genannten, ein Server-Image des Repos statt des Stubs, eine Laufzeitgrenze für Server-Container bei `SIGKILL`, eine funktionierende `trustedSigners`-Liste. Option C (manuelle Tests) ist nicht gemessen; sie lässt sich nicht auf Reproduzierbarkeit prüfen.

## Entscheidung

Wir wählen **Option B zusätzlich Option D**: containerisierte .NET-Referenztests mit `Microsoft.Data.SqlClient` 7.1.1 auf .NET 10 (SDK 10.0.403) gegen den Server dieses Repos, dazu ein Referenzabgleich nach [SPEC-045](../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) gegen einen echten SQL-Server-Container.

- **B ist die Pflicht-Matrix.** Ein Container: Server des Repos (bis dahin ein Stub) und Referenzclient im selben Container, ohne Netzwerk, ohne Bereitschaftsschleife. Pflichtumfang gemäß [SPEC-025](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) und [SPEC-032](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge): Anmeldung (Erfolg und gleiche Ablehnung), `SQL_BATCH`, RPC mit Parametern und `NULL`, Typen aus der Messung, Fehler vor und nach Zeilen, Cancel mit Folgeabfrage, Mehrfachergebnisse, TDS 7.4 und TDS 8.0 mit vertrauenswürdigem Serverzertifikat. Zwei Container (Server und Client getrennt, eigenes Netzwerk) bleiben zulässig, sobald ein Szenario getrennte Prozesse braucht (etwa Verbindungsabbruch); dann mit Netzwerknamen je Lauf, Bereitschaftsschleife und `trap` im Rezept.
- **D ist der Vergleichslauf, nicht der Ersatz.** Der echte SQL Server beantwortet, *was* der Client erwarten darf; B beantwortet, ob *dieser Server* es liefert. D kann B nicht ersetzen, weil D den Server dieses Repos nicht prüft, und B kann D nicht ersetzen, weil B nur die Annahmen des Entwurfs wiederholt. D läuft als eigenes Make-Target (Live-Lauf, später Mitschnitt-Lauf) mit zwei Containern in eigenem Netzwerk ohne Mount und ist ein **eigenes Make-Target, nicht Teil von `make gates`** (Entscheidung des Nutzers, 2026-10-30): es läuft nicht bei jedem Gate-Lauf. Es kommt erst in `make gates`, wenn die Ausnahmeliste für das gemessene Schema besteht und Laufzeit sowie Netz-, Platten- und Speicherbedarf auf dem Zielsystem gemessen sind; bis dahin ist es ein Werkzeug ohne Gate-Wirkung. Die Pflicht-Matrix B ist Teil von `make gates`. Wie oft D in der CI läuft, ist offen.
- **Auswahlgrund.** Die Messung belegt, dass beide Läufe auf einem Docker-Host mit nur Docker und `make` machbar sind (B: drei Tests `Open` und `SELECT 1` gegen den Minimal-Stub in 2,9 s, die Laufzeit der Pflicht-Matrix ist nicht gemessen; D: Start 7,7 s plus Lauf 1,6 s) und dass der echte Client Antworten liest, die der Entwurf selbst nicht prüft (NewSessionTicket im Transport-Spike). Option A bleibt als Unit-Ebene ([SPEC-024](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge)). Dass A weniger findet als B, ist eine Folgerung (die Gegenseite ist ein anderer Leser), nicht gemessen. Gewählt wird B, weil nur der echte Client die Kompatibilität nach [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität) belegt. Option C ist nicht reproduzierbar. D allein genügte nicht, weil es den Server dieses Repos nicht prüft.
- **Pins.** Die Basis-Images (`dotnet/sdk:10.0.403`, `mssql/server`) stehen per Digest im Dockerfile; für SQL Server ist der gemessene Digest der Ausgangspunkt, die Wahl des Tags ist Teil des Folge-Slice (mit `2022-latest` scheitert `Encrypt=Strict`; der Grund, TDS 8.0 werde nicht angeboten, ist gefolgert). Pakete per `packages.lock.json` mit `--locked-mode`, eine Quelle in `nuget.config` mit `packageSourceMapping`.
- **EULA.** Die Nutzung des SQL-Server-Images setzt `ACCEPT_EULA=Y` voraus. Der Repo-Inhaber (pt9912) hat für die Nutzung des Images in diesem Repo zugestimmt (Entscheidung des Nutzers, Stand 2026-10-30). Festgehalten wird das in dieser ADR, im Make-Fragment des Gates als gesetzte Variable mit Kommentar-Zeiger auf diese ADR und in der Nutzer-Doku, sobald eine entsteht. Der Lizenztext wird nicht ins Repo kopiert; verbindlich ist der Text des Herstellers zum Zeitpunkt des Pulls. Ob und wie die Bedingungen für CI-Läufe, Forks oder die Edition `Developer` (`MSSQL_PID=Developer`) gelten, ist **nicht geprüft**; eine Rechtsprüfung hat nicht stattgefunden und ist vor dem Aufnehmen von D in `make gates` zu klären.
- **Offene Punkte, ausdrücklich nicht entschieden.** (1) *NuGet Audit:* Der Spike setzte `NuGetAudit=false`, weil das Ergebnis vom Abfragetag abhängt. Ob das Repo die Schwachstellenprüfung anderweitig führt (etwa als eigenes, nicht reproduzierbares Werkzeug außerhalb von `make gates`) oder bewusst verzichtet, ist offen und vom Folge-Slice zur Reproduzierbarkeit ([TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)) zu entscheiden; wird das Abschalten dort nicht beendet, braucht es einen Carveout mit Trigger (`AGENTS.md` §3.6). Die Bewertung gegen [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) steht aus; gemessen ist nur, dass der Spike `NuGetAudit=false` setzte. (2) *`trustedSigners`:* Eine funktionierende Liste ist nicht gebaut; gleicher Träger wie (1), der Folge-Slice zur Reproduzierbarkeit ([TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)). Gemessen ist, dass `signatureValidationMode=require` ohne Liste scheitert; die Signaturprüfung ist damit nicht als Gate belegt, belegt ist die Lock-Prüfung mit Hashes (`NU1403`).

## Verglichene Alternativen

| Option                                  | Pro                                | Contra                                    |
| --------------------------------------- | ---------------------------------- | ----------------------------------------- |
| A — ausschließlich JVM-Protokolltests   | Schnell und deterministisch        | Keine echte Client-Kompatibilitätsaussage; nicht gemessen |
| **B — containerisierte .NET-Referenztests (Pflicht)** | Reproduzierbare End-to-End-Evidenz mit dem echten Client; Minimal-Stub, drei Tests: 2,9 s je Lauf, kalt 21,6 s | CI-Laufzeit, Pflege, Image 1,59 GB, Netzbedarf für Restore und Pull; NuGet-Audit und `trustedSigners` offen |
| C — manuelle Tests gegen .NET-Anwendung | Realitätsnah                       | Nicht zuverlässig reproduzierbar          |
| **D — B plus Referenzabgleich gegen eine echte SQL-Server-Instanz (`SPEC-045`)** | Erwartungen sind gemessen, nicht angenommen (Auszug der Schritte, einmal gemessen); Abweichungen stehen mit Grund in einer Ausnahmeliste; Start 7,7 s | Image 2,35 GB, Speicher rund 0,9 GB im Leerlauf, EULA, Pflege von Schema und Ausnahmeliste; `Encrypt=Strict` scheitert mit `2022-latest`, TLS 1.3 im Log nicht aufgeführt, `amd64` |
| 0 — keine Festlegung vor dem ersten CI-Versuch | Keine Vorfestlegung der Testumgebung | Blockiert `SPEC-025` und `SPEC-032` |

## Konsequenzen

- **Positiv:** Kompatibilitätsaussagen stützen sich auf reproduzierbare Läufe mit dem echten Client; Erwartungen an den Server (Fehlernummern, NULL-Logik, Typen, Cancel-Verhalten) stammen aus einer echten Instanz.
- **Negativ / Risiko:** CI-Laufzeit, Plattenbedarf (rund 4 GB für beide Images) und die Pflege von Testmatrix, Schema und Ausnahmeliste verursachen laufenden Aufwand; der Lauf D bindet das Repo an die Verfügbarkeit von `mcr.microsoft.com` und an die EULA des Herstellers. Der TDS-8.0-Pfad ließ sich an SQL Server 2022 nicht abgleichen (`Encrypt=Strict` scheitert), bliebe also allein durch B belegt; B selbst ist für TLS und TDS 8.0 nicht gemessen.
- **Folgepflicht:** `SPEC-021`, `SPEC-025`, `SPEC-032`, `SPEC-045` und `SPEC-050` auf die gewählte Umgebung festlegen (erfolgt); die Gates in einem Slice der funktionalen Welle anlegen, mit Dockerfile-Stage für den Referenzclient, Lock-Datei, `nuget.config`, Digest-Pins und dem EULA-Zeiger im Make-Fragment. Die beobachteten Servermeldungen (18456 mit State 1, 208, 8134) stehen als Messwerte für die Simulation bereit und sind dort durch den Referenzabgleich zu belegen, nicht durch diese ADR.
- **Offen:** Häufigkeit der Läufe von D in der CI (B läuft mit `make gates`); NuGet-Audit und `trustedSigners` (siehe Entscheidung; Träger: Folge-Slice zur Reproduzierbarkeit, [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)); der SQL-Server-Tag für TDS 8.0; Laufzeit und Speicherbedarf auf CI-Runnern; Mitschnitt-Lauf; rechtliche Prüfung der EULA-Nutzung; für den Fall „Fehler nach Zeilen“ gibt es keine Server-Referenz, nur die Annahme des Entwurfs ([TDS-QA-07](../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)).

## Fitness Function (falls maschinell prüfbar)

| Tooling                                     | Regel                                                                                                                                 | Make-Target                         |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------- |
| JVM-/Protokolltests und .NET-Referenzclient | Die Testmatrix (B) läuft containerisiert ohne lokale .NET-Installation und ohne Mount auf dem Host und wird bei einem fehlgeschlagenen Szenario rot. Der Referenzabgleich (D) läuft gegen den SQL-Server-Container und wird bei einer Abweichung außerhalb der Ausnahmeliste rot. Noch kein bestandenes Gate. | — (Gate noch einzurichten) <!-- d-check:ignore (geplantes Target) --> |

## Re-Evaluierungs-Trigger

Neue SqlClient-Hauptversion, nicht reproduzierbarer Kompatibilitätsfehler, geänderte EULA oder Bezugsbedingungen des SQL-Server-Images, oder ein SQL-Server-Image mit TDS 8.0 (dann ist der Abgleich des TDS-8.0-Pfads neu zu bewerten).

## Geschichte

| Datum      | Ereignis                      | Verweis |
| ---------- | ----------------------------- | ------- |
| 2026-10-29 | Proposed – Entscheidung offen | —       |
| 2026-10-29 | Option D (Referenzabgleich) und Bezug auf `SPEC-045`, `SPEC-050` ergänzt; Entscheidung weiter offen | [SPEC-045](../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) |
| 2026-10-30 | Accepted – Option B (Pflicht-Matrix) zusätzlich Option D (Vergleichslauf); Spike mit Stub-Server und echtem SQL-Server-Container gemessen; EULA-Zustimmung des Repo-Inhabers festgehalten; NuGet-Audit und `trustedSigners` offen | [SPEC-025](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) |
| 2026-10-30 | Zurück auf Proposed – Review fordert Korrekturen vor erneutem Accepted | [Review](../../reviews/2026-10-30-7fe5583-052712d-adr-0008.md) |
| 2026-10-30 | Korrektur nach Review (F-1 bis F-9) im Status Proposed: D als eigenes Make-Target, Messaussagen an die Messung gebunden, EULA auf Entscheidung beschränkt; Ausnahme von `AGENTS.md` §3.5, weil die ADR nicht Accepted blieb | [Review](../../reviews/2026-10-30-7fe5583-052712d-adr-0008.md) |
