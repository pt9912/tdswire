# ADR-0004: Session-Zustandsautomat und Protokollverarbeitung

**Status:** Proposed

**Datum:** 2026-10-29

**Autor:** pt9912

**Bezug:** [TDS-FA-01](../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-FA-02](../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [TDS-FA-07](../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe)

**Schärft:** [SPEC-001](../../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung), [SPEC-028](../../../spec/spezifikation.md#spec-028--prelogin-tls-und-login7-zustands--und-antwortvertrag), [SPEC-029](../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung), [ARC-004](../../../spec/architecture.md#1-komponenten-übersicht), [ARC-015](../../../spec/architecture.md#1-komponenten-übersicht), [TDS-FA-02.a](../../../spec/spezifikation.md#tds-fa-02a--login7-und-sitzung), [TDS-FA-07.a](../../../spec/spezifikation.md#tds-fa-07a--attention-und-cleanup)

---

## Kontext

PRELOGIN, LOGIN7, Requests und ATTENTION haben zustandsabhängige Regeln. Unerlaubte Übergänge dürfen keinen inkonsistenten Antwortstrom erzeugen.

## Messung (Spike, Stand 2026-10-30)

Gemessen in einem Spike per Dockerfile ohne Mount: JDK 21, Kotlin 2.1.20, `kotlinx-coroutines-core` 1.10.1, `-Xmx512m` für die Prozessläufe. Der Transport ist der aus [ADR-0003](0003-transportframework.md) (`AsynchronousSocketChannel` mit eigener `SSLEngine`-Schleife), für alle Optionen derselbe Code. Referenzclient `Microsoft.Data.SqlClient` 7.1.1 auf .NET 10 (SDK 10.0.403) im selben Container über `localhost`, `Pooling=false`. Der Spike-Code liegt nur im Scratchpad des Spike-Agenten und ist nicht im Repo.

**Grenzen des Messobjekts, vorweg:** Alle drei Optionen und die Referenztabelle schrieb derselbe Autor. Die Antworten stammen von einem Testserver ohne Authentifizierungsprüfung, ohne ENVCHANGE außer der Collation, ohne MARS und ohne Transaktionen; ein RPC wird wie ein Batch behandelt. Es lief kein echter SQL Server und kein Server dieses Repos. Die Ergebnisse der Prozessläufe sind Einzelläufe (zwei Läufe) auf einem Rechner, auf dem andere Builds liefen.

Gebaut sind drei Sitzungen hinter derselben Schnittstelle (Ereignisse `Start`, `Msg(Typ, Nutzlast)`, `Attn`, `TlsDone`, `Disc`; Ausgaben Schreiben, TLS-Start, Schließen) und demselben fingierten Backend:

- **A**: `enum`-Zustände (`CONNECTED`, `PRELOGIN`, `TLS_NEG`, `LOGIN7`, `IDLE`, `RUNNING`, `STREAMING`, `CANCELLING`, `COMPLETED`, `CLOSED`) und eine Übergangsfunktion `step(Zustand, Ereignisart) → (Folgezustand, Aktion)`; jeder Übergang samt Schreiben läuft unter einem `Mutex` je Sitzung.
- **B**: eine Kette von sieben Handlern ohne Zustandsaufzählung; der Zustand steckt in Flags (`started`, `preDone`, `tlsOk`, `loggedIn`, `active`, `attn`). **B0** ohne Sperre, **B** dieselbe Kette mit `Mutex` um jeden Handler und um jedes Schreiben des Anfragepfads.
- **C**: eine Sitzungs-Coroutine mit Postfach (`Channel`), die als Einzige schreibt und deren Zustand die Position im Code ist (Handshake-Folge, dann Anfrageschleife); der Anfragepfad meldet Zeilen und Abschluss als Nachrichten an das Postfach.

Alle drei bilden denselben Anfragezustand ab (`IDLE → RUNNING → STREAMING → COMPLETED → IDLE`, ATTENTION über `CANCELLING`), beantworten ATTENTION in `IDLE` und `COMPLETED` nur mit dem Abschluss mit ATTENTION-Status, lassen bei gleichzeitigem Abschluss und ATTENTION den ersten Übergang gewinnen und schließen die Verbindung ohne Antwort bei jedem unzulässigen Ereignis. **Diese Antwort auf einen unzulässigen Übergang ist eine Setzung des Spike**, die Spezifikation legt sie nicht fest.

### Messzeilen Prozesslauf (Fake-Io, vier Threads, Referenztabelle des Spike)

| Messung | A expliziter Automat | B0 Kette ohne Sperre | B Kette mit Sperre | C Akteur |
|---|---|---|---|---|
| Nicht leere Zeilen der Sitzungsdatei (A davon 48 in der Übergangsfunktion) | 117 | 74 | 74 | 82 |
| Matrix: 10 Zustände × 14 Ereignisse × 5 Folgeproben = 700 Läufe gegen die Referenztabelle, Abweichungen | 0 | 0 | 0 | 0 |
| Zufallsfolgen (3000, Länge 1 bis 12, 70 % zulässige Ereignisse) gegen die Referenztabelle, Abweichungen / Ressourcenbefunde | 0 / 0 | 0 / 0 | 0 / 0 | 0 / 0 |
| Wettlauf Abschluss gegen ATTENTION, 20 000 Läufe, davon unzulässige Antwortfolgen | 0 | 0 | 0 | 0 |
| Wettlauf: gleichzeitige Schreibvorgänge im Io, Summe über 20 000 Läufe (zwei Läufe) | 0 / 0 | 4560 / 6132 | 0 / 0 | 0 / 0 |
| Wettlauf: Folgeanfrage ohne Antwort, Sitzung geschlossen, Backend nicht oder doppelt geschlossen | 0 | 0 | 0 | 0 |
| Durchsatz, 1 Verbindung, Folgeanfragen nacheinander, letzte Runde, Anfragen/s (zwei Läufe) | 140 408 / 114 660 | Stillstand (siehe Befunde) | 156 080 / 107 809 | 120 903 / 83 719 |
| Heap je angemeldeter Leerlauf-Sitzung (20 000 Sitzungen, ohne Transportpuffer, Byte) | 342 | 647 | 644 | 1136 |
| Mutation: Zustandszweig `RUNNING/STREAMING` entfernt | Kompilierfehler | – | – | – |
| Mutation: neuer Zustand ohne Zweig | Kompilierfehler | – | – | – |
| Mutation: Behandlung von ATTENTION entfernt (A: Zweig in `IDLE`; B: Handler `attnH`; C: Zweig `Attn`) | kompiliert; Matrix 16 Abweichungen | – | kompiliert; Matrix 26 Abweichungen | kompiliert; Matrix 26 Abweichungen |

Die Matrix umfasst 140 Zellen (Zustand × Ereignis): 18 zulässige Übergänge, 85 unzulässige (Verbindung wird geschlossen, nichts gesendet), 9 `Disc`, 14 Zellen ohne Wirkung (`RELEASE`/`FAIL` des Backends außerhalb einer Anfrage) und 14 im Zustand `CLOSED`. Die Zustände `CANCELLING` und `COMPLETED` sind nur über den Wettlauf, nicht als Matrixzeile geprüft. Die Tabelle nennt den zweiten Lauf. Der erste Lauf, während andere Builds liefen, zeigte je eine Abweichung bei A in Matrix und Zufallsfolgen (die erwartete Abschlussantwort fehlte beim Auswerten); das ist vermutlich auf zu kurzes Warten der Testhilfe zurückzuführen und nicht weiter untersucht; nach Verlängerung des Wartens ergab der zweite Lauf überall 0. Der Durchsatz derselben Option schwankt zwischen den beiden Läufen um bis zu etwa 30 %.

### Messzeilen Referenzclient (je Option A, B mit Sperre, C)

Drei Verbindungsarten: TDS 7.4 ohne TLS (`Encrypt=false`), TDS 7.4 mit TLS (`Encrypt=true;TrustServerCertificate=true`) und TDS 8.0 (`Encrypt=Strict;HostNameInCertificate=localhost`). Alle drei Optionen ergaben dieselben Zählungen, außer den zeitabhängigen Anteilen im Szenario mit dem zweiten Thread.

| Szenario (je Verbindungsart) | Ergebnis (A, B, C gleich) |
|---|---|
| `Open`, `SELECT 1`, `Close` | gelingt in allen drei Arten |
| 200 Befehle auf einer Verbindung | 200/200 |
| `ExecuteReader` auf einem Strom von 100 Mio. Zeilen, nach 1000 Zeilen `SqlCommand.Cancel()`, danach Lesen bis zum Ende, danach `SELECT 1` auf derselben Verbindung, 100 Läufe | 100/100 mit Abbruchmeldung des Clients, 100/100 Folgebefehl gelingt, 0 defekte Verbindungen |
| `Cancel()` aus einem zweiten Thread gegen einen schnellen Befehl (Zufallsverzug bis etwa 1 ms), 1000 Läufe | 1000/1000 Folgebefehl gelingt, 0 defekte Verbindungen; Wert geliefert 90 bis 418, abgebrochen 581 bis 910, 0 bis 6 als `InvalidOperationException` „Operation cancelled by user“ (je nach Option und Art) |
| `Cancel()` während `RUNNING` (Backend hängt), 20 Läufe | 20/20 abgebrochen, 20/20 Folgebefehl gelingt |
| 30 parallele Verbindungen (10 Stromabbruch, 10 `SELECT 1` × 300, 10 Abbruch bei `RUNNING` × 5) | 0 Verbindungen mit Abweichung |
| Rohe Pakete (TDS 7.4 ohne TLS): SQL Batch vor Login, LOGIN7 zuerst, ATTENTION zuerst und vor Login, zweites PRELOGIN, zweites LOGIN7, PRELOGIN nach Login, Pakettyp `0x7F`, zweiter Batch während der erste läuft, Nachricht über 64 KiB vor Login (11 Fälle) | 11/11: Verbindung geschlossen, 0 Antwortbytes, Server danach gesund |
| 50 harte Verbindungsabbrüche (RST) während `RUNNING` und `STREAMING` | Backend geöffnet = geschlossen (18 138 / 18 224 / 18 245 über den ganzen Lauf), 0 doppelt geschlossen |

### Befunde

- Die Optionen A, B (mit Sperre) und C bestehen alle geprüften Zustandsfolgen, Wettläufe und Clientszenarien; die Messung trennt sie darin nicht. Was sie trennt: **wer einen vergessenen Fall findet** und **was Nebenläufigkeit kostet**. Fehlt in A ein Zustand, bricht das Kompilieren ab (die Zustände sind ein `enum` in einem `when`-Ausdruck; gemessen nur für A, für B und C sind die Mutationen nicht gelaufen). Fehlt ein Ereigniszweig innerhalb eines Zustands (A) oder in B und C eine Behandlung, kompiliert der Code, und nur die Matrix findet es (16 bis 26 Abweichungen in den drei Mutationen, die kompilieren). Der Schutz in A reicht damit nur für Zustände, nicht für Ereignisse je Zustand.
- B0 (ohne Sperre) erzeugte im Wettlauf 4560 bzw. 6132 gleichzeitige Schreibvorgänge (Summe über 20 000 Läufe; Abschlussantwort und ATTENTION-Abschluss), ohne dass die gesendete Abfolge unzulässig wurde. Auf dem echten Transport würde ein zweites gleichzeitiges `write` auf demselben `AsynchronousSocketChannel` scheitern oder TLS-Records vermischen; das ist **nicht gemessen**, B0 lief nur im Fake-Io. Im Durchsatzlauf lehnte B0 eine Folgeanfrage ab, die unmittelbar nach dem Schreiben der Antwort eintraf (Stillstand bei Anfrage 126 bzw. 8273 in den zwei Läufen), weil `active` erst nach dem Schreiben auf `null` gesetzt wird; die Folgeanfrage kam im Test schneller als im Netz möglich. Dass ein echter Client diesen Zeitpunkt trifft, ist nicht gemessen.
- Die ATTENTION-Antwort in `IDLE`/`COMPLETED` (nur der Abschluss mit ATTENTION-Status, vorgesehen für den Fall, dass `Cancel()` und Abschluss sich kreuzen) wird in den Referenzclient-Läufen nicht einzeln gezählt. Dass der Client in 1000/1000 Läufen je Art den Folgebefehl ausführen konnte, ist nur mittelbar ein Beleg. Am echten SQL Server wurde der Fall „Antwort vollständig gesendet, dann `Cancel()`“ gemessen: ATTENTION kommt an, der Server antwortet mit einer Nachricht DONE[ATTN] ([ADR-0007](0007-fehler-und-attention.md), R13); ATTENTION im Leerlauf ohne laufende Anfrage ist nicht gemessen (der Client sendet keine, R14).
- C braucht mit 1136 Byte rund das Dreifache an Sitzungsspeicher gegenüber A (342 Byte) und lag im Durchsatzlauf in beiden Läufen unter A und B (83 719 bis 120 903 gegen 107 809 bis 156 080 bei B und 114 660 bis 140 408 bei A). A und B mit Sperre liegen im Durchsatz innerhalb der Schwankung zwischen den Läufen (B lag im ersten Lauf darüber, A im zweiten). Gemessen ist nur der Fake-Io ohne Netz; ob die Unterschiede gegenüber dem Netzdurchsatz zählen, ist nicht gemessen. Dafür schreibt in C nur der Akteur, die Wettläufe brauchen keine Sperre, und `on()` muss eine Bestätigung abwarten, damit der Transport nach PRELOGIN weiß, ob er den TLS-Handshake beginnt (zusätzliche Verdrahtung, in A und B entfällt sie).
- Zustandsmodell und Nebenläufigkeitsmodell sind in der Optionstabelle in einer Achse zusammengefasst, im Spike aber getrennt tragend: A ist ein explizites Zustandsmodell, serialisiert über einen `Mutex`; B ein implizites Zustandsmodell in Flags (B0 ungeschützt, B mit `Mutex`); C ein implizites Modell in der Codeposition, serialisiert über ein Postfach. Ein expliziter Automat im Akteur (A-Tabelle, C-Serialisierung) ist **nicht gebaut**.
- Der Spike-Transport schreibt jedes Paket unabhängig von der Abbruchanforderung (`NonCancellable`), damit ein abgebrochener Strom kein halbes Paket hinterlässt. Ein Lauf ohne diese Maßgabe wurde nicht gebaut; ob sie nötig ist, ist nicht gemessen.
- In allen drei Läufen traten bei den 50 harten Abbrüchen im Serverprozess 24 (A), 24 (B) und 22 (C) nicht abgefangene `IOException: Broken pipe` in den Threads der Kanalgruppe auf (zusätzlich je 10 `ClosedChannelException` bzw. `AsynchronousCloseException` im Verbindungscode); die Ursache liegt im Spike-Adapter (Fortsetzung eines abgebrochenen Schreibens), gleich für alle Optionen, nicht analysiert.

### Nicht gemessen

- Verhalten eines echten SQL Servers bei unzulässigen Übergängen und bei ATTENTION im Leerlauf (die Messung zu ATTENTION nach vollständiger Antwort steht oben, [ADR-0007](0007-fehler-und-attention.md) R13).
- MARS, Transaktionen, Pooling (`sp_reset_connection`, `RESETCONNECTION`), `Command Timeout` und Login-Zeitgrenze ([SPEC-008](../../../spec/spezifikation.md#3-defaults-und-konstanten)), die `CANCEL_TIMEOUT`-Frist ([SPEC-033](../../../spec/spezifikation.md#3-defaults-und-konstanten); der Spike setzt sie nicht), RPC-Zustände (ein RPC wurde wie ein Batch behandelt).
- Last über 30 Verbindungen mit dem Referenzclient (die 20 000 Wettläufe laufen im Fake-Io mit vier Threads), Latenz-Perzentile, Langzeitlauf, andere Clients.
- Ob eine andere Autorin oder ein anderer Autor die Optionen anders schriebe. Die Referenztabelle und die drei Sitzungen stammen vom selben Autor; die Nullen in der Matrix belegen die Übereinstimmung mit dieser Tabelle, nicht ihre Richtigkeit gegen [SPEC-001](../../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung).

## Entscheidung

Wir wählen **Option A** als Zustandsmodell und den **`Mutex` je Sitzung** als Nebenläufigkeitsmodell (Entscheidung des Nutzers, 2026-10-30; die Wahl ist eine Setzung, die Messung entscheidet sie nicht, siehe Befunde).

- **Zustandsmodell:** expliziter Automat. Die Zustände sind ein `enum`; die Übergänge sind eine reine Funktion `step(Zustand, Ereignisart) → (Folgezustand, Aktion)` ohne Netz und ohne Schreiben, die der Sitzungsrahmen ausführt. Der Spike hat diese Form mit zehn Zuständen und fünf Ereignisarten gebaut; die endgültige Aufzählung ist noch festzulegen (siehe unten).
- **Nebenläufigkeitsmodell:** Jeder Übergang samt dem zugehörigen Schreiben läuft unter einem `Mutex` je Sitzung. Der Anfragezustand wird je Sitzung atomar entschieden, wie `SPEC-001` es verlangt. Bei gleichzeitigem Abschluss und ATTENTION gewinnt der erste Übergang.
- **Verworfen nach Messung:** Eine Handler-Kette ohne Sperre (B0). Sie erzeugte im Fake-Io gleichzeitige Schreibvorgänge (4560 bzw. 6132 in 20 000 Läufen) und einen Stillstand im Durchsatzlauf; die Auswirkung auf dem echten Transport ist nicht gemessen.
- **Was die Messung für die Wahl trägt, und was nicht:** Sie trägt, dass A und der `Mutex` die geprüften Szenarien bestehen (Matrix, Zufallsfolgen, Wettläufe, Referenzclient in drei Verbindungsarten) und dass ein fehlender Zustand in A ein Kompilierfehler ist (zwei Mutationen). Dass er in B und C „nur ein Testfund“ wäre, ist eine Struktur-Folgerung (kein `enum` mit erschöpfendem `when`) und nicht gemessen: die Mutationszeilen „Zustandszweig entfernt“ und „neuer Zustand ohne Zweig“ sind für B und C nicht gelaufen. Sie trägt nicht, dass A bei den Ereignissen je Zustand sicherer ist (Mutation: kompiliert, 16 Abweichungen), nicht, dass A einer Option mit Sperre im Durchsatz überlegen ist (A und B im Rahmen der Schwankung), und nicht die Bedeutung im Betrieb.
- **Nachweis:** Ein Spike mit drei Sitzungen, einer Referenztabelle und dem Referenzclient (siehe Messung). Der Spike-Code liegt nicht im Repo.

**Nicht entschieden und in dieser ADR als offen geführt:**

- die Antwort auf einen unzulässigen Übergang (Spike: Verbindung schließen ohne Antwort, eine Setzung des Spike);
- die Reichweite von ATTENTION im Handshake (PRELOGIN, TLS, LOGIN7);
- der Zuschnitt der Zustände (der Spike hat eine flache Aufzählung, `SPEC-001` beschreibt Verbindungszustand und Anfragezustand in `READY` getrennt; die Spezifikation legt den Zuschnitt nicht fest);
- **ATTENTION in jedem Anfragezustand:** diese ADR ist der Eigentümer der Frage ([ADR-0007](0007-fehler-und-attention.md) delegiert sie hierher). `SPEC-001` trägt für `IDLE` und `COMPLETED` die Abschlussbestätigung als Setzung; am echten SQL Server ist nur ATTENTION nach vollständig gesendeter Antwort gemessen (R13), im Leerlauf nicht;
- das Ende des Handshakes nach einem abgelehnten Login (Verbindung offen oder geschlossen; die Frage führt [ADR-0007](0007-fehler-und-attention.md));
- Sitzungszustand nach einem Fehler (Transaktionen), `RESETCONNECTION` bei Pooling und der Zeitpunkt des Schließens nach `TDS_SHUTDOWN` ([ADR-0007](0007-fehler-und-attention.md) legt „nach ERROR und DONE“ fest; offen bleibt der Zustandsübergang).

## Verglichene Alternativen

Kennzeichnung: **(M)** am Spike gemessen.

| Option                                   | Pro                               | Contra                                   |
| ---------------------------------------- | --------------------------------- | ---------------------------------------- |
| **A — expliziter endlicher Zustandsautomat, `Mutex` je Sitzung** | Fehlender Zustand ist ein Kompilierfehler (M, 2 Mutationen; für B und C nicht gelaufen, dort nur eine Struktur-Folgerung); Übergangsfunktion ist rein und ohne Netz prüfbar (Struktur; die Matrix mit 0 Abweichungen und der Wettlauf ohne Schreibwettlauf trennen die Varianten nicht, siehe Befunde); 342 Byte je Leerlauf-Sitzung (M) | 117 Zeilen gegen 74 (B) und 82 (C) (M); fehlt ein Ereigniszweig in einem Zustand, kompiliert der Code (M: Matrix 16 Abweichungen) |
| B — Handler-Kette mit implizitem Zustand (B0 ohne, B mit Sperre) | Wenigste Zeilen (74, M); B mit Sperre ohne Schreibwettlauf und mit Durchsatz im Rahmen von A (M); 644 Byte je Sitzung (M) | Fehlende Behandlung kompiliert und wird nur von der Matrix gefunden (M: 26 Abweichungen); B0: 4560 bzw. 6132 gleichzeitige Schreibvorgänge im Fake-Io und Stillstand im Durchsatzlauf (M; echter Transport nicht gemessen) |
| C — Sitzungs-Coroutine mit Postfach | Schreibwettlauf ohne Disziplin je Schreibstelle ausgeschlossen, weil nur der Akteur schreibt (M: 0 / 0, keine Sperre); 82 Zeilen (M) | Durchsatz in beiden Läufen unter A und B (M, Fake-Io); 1136 Byte je Sitzung (M); fehlende Behandlung kompiliert (M: 26 Abweichungen); zusätzliche Verdrahtung für die TLS-Bestätigung (M) |
| 0 — keine Festlegung vor der Zustandsmatrix | Keine Vorfestlegung der Session-Architektur | Blockiert `SPEC-001`, `SPEC-028` und `SPEC-029`; die Matrix liegt jetzt als Messung vor |

Ein expliziter Automat im Akteur (Tabelle aus A, Serialisierung aus C) ist nicht gebaut und nicht gemessen.

## Konsequenzen

- **Positiv:** Zulässige und unzulässige Zustandsübergänge sind als Funktion prüfbar; ein fehlender Zustand fällt beim Kompilieren auf. Schreiben und Übergang sind je Sitzung serialisiert.
- **Negativ / Risiko:** Die Zustandsmodellierung kostet Zeilen und Aufwand vor dem ersten lauffähigen Login (117 gegen 74 Zeilen im Spike). Der Compiler-Schutz deckt nur fehlende Zustände; fehlende Ereigniszweige in einem Zustand finden nur Matrix-Tests. Die Sperre ist eine Disziplin am Sitzungsrahmen: Ein Schreiben außerhalb der Sperre ist nicht ausgeschlossen (B0 zeigt die Folge im Fake-Io).
- **Folgepflicht:** `SPEC-001` auf die Struktur festlegen (Aufzählung, reine Übergangsfunktion, Sperre je Sitzung; geschehen, der Zuschnitt der Aufzählung bleibt offen); `SPEC-028` und `SPEC-029` an der Matrix und den offenen Punkten ausrichten, sobald diese entschieden sind; Matrix-Test (alle Zustände × Ereignisse) und Wettlauf-Test im Produktivcode ergänzen.
- **Offen:** die Antwort auf einen unzulässigen Übergang (Spike: Schließen ohne Antwort; am echten SQL Server nicht gemessen); die Reichweite von ATTENTION im Handshake; ATTENTION in jedem Anfragezustand (Eigentümer diese ADR, siehe „Nicht entschieden“); das Ende des Handshakes nach abgelehntem Login; der Zuschnitt der Zustände (flach oder getrennt nach Verbindungs- und Anfragezustand); Sitzungszustand nach einem Fehler (Transaktionen), `RESETCONNECTION` bei Pooling, Zeitpunkt des Schließens nach `TDS_SHUTDOWN` (dieselben Punkte führt [ADR-0007](0007-fehler-und-attention.md)); ob `NonCancellable` beim Schreiben eines Pakets nötig ist; die Ursache der `Broken pipe`-Meldungen bei harten Abbrüchen (Spike-Adapter, nicht analysiert).
- **Grenze der Aussage:** Referenztabelle und Sitzungen stammen vom selben Autor, und es lief kein echter SQL Server; die Wahl gilt für den Server dieses Repos nach seiner Spezifikation, nicht als Nachweis des Verhaltens eines echten Servers.

## Fitness Function (falls maschinell prüfbar)

| Tooling                                     | Regel                                                                                                                                 | Make-Target                         |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------- |
| JVM-/Protokolltests und .NET-Referenzclient | Jeder in der Zustandsmatrix ungültige Übergang wird abgelehnt; zwei parallele Sitzungen beeinflussen sich nicht; kein gleichzeitiges Schreiben je Sitzung. Noch kein bestandenes Gate. | — (Gate noch einzurichten) |

## Re-Evaluierungs-Trigger

Neue TDS-Nachrichtentypen oder nicht deterministische Session-Races; ein Schreibwettlauf auf dem echten Transport; eine Messung am echten SQL Server, die der Setzung bei unzulässigen Übergängen widerspricht.

## Geschichte

| Datum      | Ereignis                      | Verweis |
| ---------- | ----------------------------- | ------- |
| 2026-10-29 | Proposed – Entscheidung offen | —       |
| 2026-10-30 | Messung ergänzt (Spike mit drei Sitzungen und dem Referenzclient); Option A mit `Mutex` je Sitzung gewählt (Entscheidung des Nutzers, 2026-10-30); Status weiter Proposed bis Review und Verifikation; Antwort auf unzulässige Übergänge, ATTENTION im Handshake, Zustandszuschnitt und Sitzungszustand nach Fehler offen | [SPEC-001](../../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung) |
| 2026-10-30 | Nach Review nachgeschärft (Status weiter Proposed): Aussage zu B und C als Struktur-Folgerung gekennzeichnet, Gleichstand aus der Pro-Spalte von A genommen, Zuschnitt in `SPEC-001` als offen geführt, Eigentümer von „ATTENTION in jedem Anfragezustand“ benannt | [SPEC-001](../../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung) |
