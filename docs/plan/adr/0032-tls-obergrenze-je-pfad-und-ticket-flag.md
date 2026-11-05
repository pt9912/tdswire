# ADR-0032: TLS-Obergrenze je Pfad und Wegfall des Ticket-Flags

**Status:** Accepted (Entscheidung des Nutzers 2026-11-05)

**Datum:** 2026-11-05

**Autor:** pt9912

**Bezug:** [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-FA-12](../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0002](0002-tls-und-verschluesselung.md), [ADR-0003](0003-transportframework.md), [Messprotokoll](../../messungen/tls-d-migrate-2026-11-05.md)

**Schärft:** [SPEC-023](../../../spec/spezifikation.md#6-externe-verträge) (TLS-Versionen je Pfad), [SPEC-043](../../../spec/spezifikation.md#spec-043--startprüfung-tls-konfiguration-und-port) (Startprobe verträgt das Ticket), [Sicherheitsprofil](../../../spec/spezifikation.md#sicherheitsprofil-vertrag-zu-spec-023) (Abschnitte Protokollversionen, JVM-Flag, Gemessen).

**Supersedes (teilweise), nur wörtlich benannt; die Dateien der Vorgänger bleiben unverändert und `Accepted` ([AGENTS.md](../../../AGENTS.md) §3.5):**

- [ADR-0003](0003-transportframework.md), Entscheidung, Punkt „TLS“: **nur** der Satz „Der Serverprozess läuft mit `-Djdk.tls.server.newSessionTicketCount=0`: Ohne das Flag sendet … und der Client bricht ab.“; und in „Konsequenzen“ der Halbsatz der Folgepflicht „das JVM-Flag steht im Sicherheitsprofil und ist im Start des Serverprozesses zu setzen“. Der Rest (Option C, Umschaltung am Ergebnis `FINISHED`, TDS-8.0 ab dem ersten Byte) gilt weiter.
- [ADR-0018](0018-laufzeit-artefakt.md), Entscheidung, Punkt „Startzeile“: **nur** das Flag in `java -Djdk.tls.server.newSessionTicketCount=0 -jar /app/tdswire.jar` (die Startzeile hat kein Flag mehr; soweit [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) den Punkt nicht ohnehin ersetzt hat).
- [ADR-0021](0021-oeffentlicher-integrations-einstieg.md), Entscheidung 3: **nur** der Satz „Der Einstieg prüft das JVM-Flag `-Djdk.tls.server.newSessionTicketCount=0` wie der Prozess und setzt es nicht selbst; der Embedder setzt es …“. Die Prüfung entfällt, der Embedder setzt nichts.

Geschärft, nicht ersetzt: [ADR-0002](0002-tls-und-verschluesselung.md) (dort steht „Mindest-Protokollversion und zugelassene Suiten sind offen“; die Obergrenze je Pfad ist eine Festlegung darin, kein Widerspruch). Der gemessene Kontext von ADR-0002 („TLS 1.3 im 7.4-Pfad gelingt“, gemessen mit `Microsoft.Data.SqlClient`) bleibt als Messung wahr, ist aber nicht mehr, was der Server im 7.4-Pfad anbietet.

---

## Kontext

**Nutzerentscheidungen vom 2026-11-05.** Ausgangspunkt ist der Slice zur TLS-Behebung für d-migrate: d-migrate 1.12.0 mit dem Treiber-Standard (`encrypt=true`) meldet sich am Testserver (`REQUIRED`) nicht an. Die Messungen M1 bis M8 und die Matrix V0 bis V3 stehen im [Messprotokoll](../../messungen/tls-d-migrate-2026-11-05.md); hier nur, was die Entscheidung trägt.

1. **Obergrenze je Pfad.** `mssql-jdbc` 13.4.0 (JDK 21) sendet im TDS-7.4-Pfad unter TLS 1.3 sein `Finished` nicht als Paket `0x12` und schickt LOGIN7 gleich als rohen TLS-Record (M2, Rohbytes-Mitschnitt, ein Host, ein Lauf); der Server trennt mit `PROTOCOL_VIOLATION`. Unter TLS 1.2 trägt der Treiber das `Finished` im Paket `0x12`, die Anmeldung gelingt (M3, M7). Der Server nimmt ein regelgerecht gerahmtes TLS 1.3 an (M4); der Fehler liegt im Treiber. Zur Erklärung gibt es einen Quelltext-Befund, der **nicht belegt** ist: laut Koordinator puffert `SSLHandshakeOutputStream` in `IOBuffer.java` (Zweig `main`, nicht der Tag 13.4.0, nicht gelesen am gemessenen Stand, nicht gemessen) die Client-Handshake-Bytes bis zum nächsten Lesen. Zur Rechtfertigung der Obergrenze, nicht zur Erklärung des Fehlers: Nach Microsoft Learn unterstützt TDS 7.x Verschlüsselung bis TLS 1.2 (laut Suchergebnis, nicht selbst am Server gemessen).
2. **Ticket-Flag.** Das Flag `-Djdk.tls.server.newSessionTicketCount=0` entstand im Spike von [ADR-0003](0003-transportframework.md) für den 7.4-Pfad **mit TLS 1.3** (NewSessionTicket landet im Paket `0x12`, der Client liest danach rohes TLS; Zahlen aus dem Bericht eines Spike-Agenten, nicht wiederholt). Mit der Obergrenze TLS 1.2 tritt das dort nicht mehr auf. Im Gegenteil: V0 (Flag gesetzt) ist für den .NET-Referenzclient im 7.4-Pfad **rot** (2 von 19 grün; „unexpected message“, M8), weil der Server die Session-Ticket-Erweiterung ankündigt und keines sendet. V1 (zusätzlich `enableSessionTicketExtension=false`), V2 (kein Flag) und V3 sind 19 von 19 grün; der 8.0-Pfad ist in allen vier Varianten 2 von 2 grün, d-migrate meldet sich in allen vier an. Im 8.0-Pfad ist ein Ticket ein TLS-Record wie jeder andere (Zusatzmessung mit `openssl s_client`, Verbindung blieb offen). V2 und V3 liefen nur mit lokal ausgeschalteter Prüfung des Flags und ausgeschalteter Startprobe (Wegwerfzweig); ohne diese Lockerungen startet der Server nicht: die Startprobe (Handschlag im Speicher im 8.0-Pfad, TLS 1.3) scheitert mit `PAIR (Handschlag CONNECTION_ABORTED / Gegenstelle null)`. Ursache nicht untersucht; naheliegend ist das NewSessionTicket nach dem Handschlag, das die Gegenstelle der Probe nicht liest (nicht belegt).

**Annahmen, die kippen:** (a) dass ein Treiber im 7.4-Pfad mit TLS 1.3 gebraucht wird; (b) dass kein Client im 7.4-Pfad ein Ticket im 1.2-Handschlag ablehnt; (c) dass TLS-Tickets im 8.0-Pfad für die gemessenen Clients unschädlich bleiben (gemessen nur mit dem .NET-Referenzclient und `openssl`).

## Entscheidung

1. **Obergrenze je Pfad.** Der TDS-7.4-Pfad (Handschlag in Paketen `0x12`) bietet **höchstens TLS 1.2** an. Der TDS-8.0-Pfad (`Encrypt=Strict`) bleibt bei TLS 1.3 (Mindestversion des Profils bleibt TLS 1.2). Die Obergrenze gilt je Pfad und ändert **keinen Standard der Konfiguration** (`TlsParameters`, `SPEC-010`); kein Konfigurationsschlüssel kommt hinzu. Der Server nimmt keine Anmeldung ohne das `Finished` des Clients an.
2. **Das Flag `-Djdk.tls.server.newSessionTicketCount=0` entfällt als Pflicht des Einbettenden** (Weg V2). Die Bibliothek prüft es nicht mehr, der Einbettende und der Testserver setzen es nicht, und der Start bricht nicht ab, wenn es fehlt. Voraussetzung ist, dass der Server **ohne** das Flag startet:
   - Die **Startprobe** (`SPEC-043`) verträgt das NewSessionTicket des 8.0-Pfads: Sie darf an einem Ticket nach dem Handschlag nicht scheitern.
   - Der **Bootstrap-Check** auf den Wert des Flags entfällt, weil seine Voraussetzung (Flag ist Pflicht) entfällt.

   **Wie** die Probe das Ticket verträgt, ist eine Implementierungsfrage und wird hier nicht gewählt. Zwei zulässige Wege: (i) nach dem Handschlag auf der Gegenstelle weiterlesen, bis das Ticket verarbeitet ist; (ii) die Probe endet mit dem Ergebnis `FINISHED` beider Seiten. Kriterium der Wahl: Die Probe behält ihren Vertrag aus `SPEC-043` (ein vollständiger Handschlag mit den konfigurierten Versionen und Suiten, Diagnose `CERTIFICATE`, `PRIVATE_KEY`, `PAIR` unverändert) **und** ihr Urteil hängt nicht von Zeit, Wartezeiten oder Reihenfolge des Tickets ab (deterministisch, ohne Schlaf und ohne Zeitgrenze). Wer (i) wählt, muss die Terminierung ohne Zeitgrenze belegen. Vor der Wahl ist die Ursache des gemessenen `PAIR`-Abbruchs zu bestätigen, weil sie hier nicht belegt ist.

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| Server nimmt LOGIN7 als rohen Record ohne `Finished` an (Behandlung der Treiber-Eigenheit am Server) | `mssql-jdbc` liefe im Treiber-Standard auch mit TLS 1.3 im 7.4-Pfad | Der Server nähme eine Verbindung an, deren Handschlag der Client nicht bestätigt hat; das schwächt den Handschlag und senkt [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit); eine Gate-Senkung nach `AGENTS.md` §3.6; verworfen |
| Nur am Client: `sslProtocol=TLSv1.2` in der Verbindungszeichenfolge (Workaround) | Keine Änderung am Server; gemessen funktional (M3) | Der Treiber-Standard (`encrypt=true`) bleibt am Testserver gebrochen; das Soll von [TDS-FA-12](../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) ist „d-migrate verbindet sich mit dem Standard“; jeder Nutzer muss den Workaround kennen; verworfen |
| V0 — Stand halten: Flag Pflicht, TLS-Obergrenze 1.2 | Keine Änderung der Probe | Rot: der .NET-Referenzclient scheitert im 7.4-Pfad (Ticket angekündigt, keines gesendet); **unzulässig** |
| V1 — zwei Flags: `newSessionTicketCount=0` und `enableSessionTicketExtension=false` | 19 von 19 grün gemessen; Probe bleibt unverändert | Zwei Pflicht-Flags für jeden Einbettenden, dazu das Wissen um ihren Grund; das Flag-Problem bleibt bestehen und wird größer; **verworfene Alternative** |
| V3 — nur `enableSessionTicketExtension=false` | Ein Flag, grün gemessen | Eine Pflicht des Einbettenden bleibt; der Server startet ohne Änderung der Probe nicht (nicht gelaufen als Bibliothek); verworfen zugunsten von V2 |
| **V2 — kein Flag; Probe verträgt das Ticket (gewählt)** | Keine Pflicht für den Einbettenden; 7.4-Pfad 19 von 19, 8.0-Pfad 2 von 2, d-migrate meldet sich an (Matrix, ein Host, Einzelmessung je Zelle) | Die Probe ändert sich; im 8.0-Pfad fließen Tickets, ihr Verhalten bei Wiederaufnahme ist nicht gemessen |

## Konsequenzen

- Positiv: Der Treiber-Standard von d-migrate funktioniert; der Einbettende hat keine JVM-Pflicht mehr; der 7.4-Pfad entspricht dem, was ein echter SQL Server nach Microsoft Learn verhandelt.
- Negativ: TLS 1.3 gibt es nur im 8.0-Pfad; ein Client, der im 7.4-Pfad TLS 1.3 verlangt, scheitert mit `HANDSHAKE_FAILED`. **Wer das Flag weiter setzt, bricht den 7.4-Pfad für den .NET-Client** (V0: Erweiterung angekündigt, kein Ticket); die Bibliothek erkennt das nicht (siehe Folgepflicht).
- **§3.6 (keine Gate-Lockerung):** Keine Schwelle sinkt, keine Linter-Strenge, keine Architekturregel. Die Obergrenze verengt das Angebot (der 7.4-Pfad bietet weniger an), er schwächt nichts: Mindestversion, Suiten und Standard `REQUIRED` bleiben, und der Handschlag wird nicht geschwächt (Entscheidung 1, letzter Satz). Der Bootstrap-Check verschwindet nicht als Lockerung, sondern weil seine Voraussetzung entfällt: Er schützte vor einem Abbruch („Unrecognized record version“), der nur unter TLS 1.3 im 7.4-Pfad auftrat, und dieser Pfad bietet kein TLS 1.3 mehr; im 8.0-Pfad belegt die Zusatzmessung kein Rahmenproblem durch das Ticket. Der Ersatz ist ein Test, der das neue Soll hält (siehe Folgepflicht), kein Wegfall des Belegs.
- **Folgepflicht Spezifikation (Nachzug nach `Accepted`, durch den Implementer im Slice):** `SPEC-023` und der Abschnitt Protokollversionen (TLS 1.3 gilt nur im 8.0-Pfad; 7.4-Pfad höchstens TLS 1.2; das Sicherheitsprofil trägt die Obergrenze bereits, der Abschnitt „JVM-Flag“ wird gegenstandslos); der Absatz „Offen, gemessen am 2026-11-05“ im Abschnitt „Gemessen“ entfällt zugunsten der Entscheidung; `SPEC-043` nennt, dass die Probe das Ticket verträgt; die Zeile „JVM-Flag“ der Tabelle der Festlegungen des Einstiegs und die Ereignisse zum Abbruchgrund „JVM-Flag fehlt“ (`StartRefused`) entfallen. Die Spec verweist dabei nicht auf diese ADR (Richtung).
- **Folgepflicht Umsetzung:** `Bootstrap.kt` (Check entfällt), `TdsServer.kt` (Aufruf der Prüfung), `TlsStartProbe` (verträgt das Ticket), `src/testserver/.../Main.kt`, `Dockerfile` (Startzeile ohne Flag), `build.gradle.kts` (`jvmArgs` der Tasks, u. a. `kompatibilitaetsServer`), `gradle/test-jvm.init.gradle.kts`, Tests: `BootstrapTest` (Flag-Test umkehren: der Start gelingt ohne Flag), `ServerSmokeTest`, `TlsTicketFlagTest` und `testsupport/TicketProbe.kt` (halten den 8.0-Pfad künftig **ohne** Flag und mit Ticket fest, sonst entfällt der Beleg), ein Test der Obergrenze (reiner TLS-1.3-Client scheitert im 7.4-Pfad, der 8.0-Pfad handelt TLS 1.3 aus), der Start ohne Flag mit der Probe. Die Kompatibilitätsmatrix ([kompatibilitaetsmatrix](../kompatibilitaetsmatrix.md)) und die Nutzer-Doku (`docs/user/testserver-manuell.md` und weitere Nennungen des Flags) werden nachgezogen; nach `make gates` bleibt `grep newSessionTicketCount` außerhalb von Historie (Messprotokolle, Reviews, abgeschlossene Slices, Vorgänger-ADRs) leer.
- **Akzeptiertes Negativ (kein eigener Träger):** Ein Einbettender, der das Flag aus Gewohnheit weiter setzt, bricht den 7.4-Pfad für .NET-Clients still (V0). Die Nutzer-Doku und `SPEC-023` sagen „nicht setzen“; ein Wächter gegen das Flag wäre eine neue Pflicht und entspräche dem Gegenteil der Entscheidung. Kippt das (ein Einbettender meldet es), ist ein Hinweis-Ereignis an der Senke die Abhilfe.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| Unit-Tests der Bibliothek (JDK-`SSLEngine`-Gegenstelle) | 7.4-Pfad: ein reiner TLS-1.3-Client scheitert, TLS 1.2 gelingt; 8.0-Pfad: TLS 1.3 gelingt ohne Flag, auch mit Ticket; Start (Probe) ohne Flag gelingt | `make test` |
| .NET-Referenzclient | `Anmeldung` im 7.4-Pfad (TLS 1.2) und `Tds80_Strict_*` grün ohne Flag | `make kompatibilitaet KATEGORIE=Anmeldung` (kein Gate, [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md)) |

## Nicht gemessen

- Wiederaufnahme einer Sitzung mit dem Ticket (Ticket benutzt), in beiden Pfaden.
- Andere Treiber und Treiberversionen; `mssql-jdbc` im 8.0-Pfad; der Tag 13.4.0 im Quelltext (der Befund zu `IOBuffer.java` ist Zweig `main`, nicht gelesen am Tag).
- ECDSA-Zertifikate und `TLS_CHACHA20_POLY1305_SHA256`.
- Ein echter SQL Server (nach Microsoft Learn TLS 1.2 im 7.x-Pfad; laut Suchergebnis, nicht selbst gemessen).
- Die Bibliothek mit geänderter Probe: V2 und V3 liefen mit lokal ausgeschalteter Probe und Prüfung; `make test` und `make gates` darüber sind nicht gelaufen. Einzelmessungen, ein Host, Last 9 bis 28.

## Re-Evaluierungs-Trigger

Ein Client, der im 7.4-Pfad TLS 1.3 verlangt, oder ein Treiber, der den 7.4-Pfad unter TLS 1.3 regelgerecht rahmt (dann ist eine Anhebung der Obergrenze zu prüfen); ein Client, der an Tickets im 8.0-Pfad scheitert (dann ist die Ticket-Zahl zu entscheiden); eine Messung gegen einen echten SQL Server, die die Annahme zu Microsoft Learn widerlegt.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-05 | Accepted – Entscheidung des Nutzers: Obergrenze TLS 1.2 im 7.4-Pfad; Flag entfällt (Weg V2) | [Messprotokoll](../../messungen/tls-d-migrate-2026-11-05.md) |
