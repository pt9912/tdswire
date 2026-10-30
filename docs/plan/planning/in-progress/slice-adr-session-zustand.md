# Slice slice-adr-session-zustand: ADR-0004 entscheiden: Session-Zustandsautomat und Protokollverarbeitung

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-entscheidungen-grundlage

**Bezug:** [TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-FA-02](../../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [ADR-0004](../../adr/0004-session-zustandsautomat.md)

**Berührte Spec-Stellen:** SPEC-001 · SPEC-028 · SPEC-029 · ARC-004 · ARC-015

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

**Ziel:** [ADR-0004](../../adr/0004-session-zustandsautomat.md) ist mit einem Nachweis entschieden und `Accepted`; die offene Frage lautet: Zustandsmodell (explizit oder implizit) und Nebenläufigkeitsmodell.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Produktionscode der gewählten Option — er entsteht in einem Slice der funktionalen Welle, weil dieser Slice entscheidet und nicht baut.
- Änderungen am Lastenheft — eine ADR schärft höchstens die Spezifikation; ein Widerspruch zum Lastenheft ist ein eigener Vorgang.

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [ ] Spike durchgeführt; seine Ergebnisse (Messwerte, Matrix, Befund) stehen in der ADR. Der Spike-Code ist temporär und nach der Entscheidung gelöscht.
- [ ] [ADR-0004](../../adr/0004-session-zustandsautomat.md) auf `Accepted`, Option gewählt, Geschichte und ADR-Index nachgezogen.
- [ ] Spec-Stellen aus dem `Schärft:`-Feld der ADR an die Entscheidung angepasst.
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
| `docs/plan/adr/0004-session-zustandsautomat.md` | update | Entscheidung, Status, Geschichte |
| `docs/plan/adr/README.md` | update | Indexzeile |
| Spike (temporär, nicht im Repo behalten) | neu, danach löschen | Ergebnisse gehen in die ADR |
| `spec/spezifikation.md` | update | Stellen aus `Schärft:` |
| `docs/plan/planning/in-progress/roadmap.md` | update | „In Arbeit“ nennt diesen Slice |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

**Zwischenstand der Messung** für [ADR-0004](../../adr/0004-session-zustandsautomat.md) (Spike per Dockerfile ohne Mount; JDK 21, Kotlin 2.1.20, `kotlinx-coroutines-core` 1.10.1, `-Xmx512m` für die Prozessläufe; der Transport ist der aus [ADR-0003](../../adr/0003-transportframework.md) (`AsynchronousSocketChannel` mit eigener `SSLEngine`-Schleife), für alle Optionen derselbe Code; Referenzclient `Microsoft.Data.SqlClient` 7.1.1 auf .NET 10 (SDK 10.0.403) im selben Container über `localhost`, `Pooling=false`. Alle drei Optionen und die Referenztabelle schrieb derselbe Autor; die Antworten stammen von einem Testserver ohne Auth-Prüfung, ohne ENVCHANGE außer der Collation, ohne MARS und ohne Transaktionen; ein RPC wird wie ein Batch behandelt. Die Ergebnisse der Prozessläufe sind Einzelläufe auf einem Rechner, auf dem andere Builds liefen.)

Gebaut sind drei Sitzungen hinter derselben Schnittstelle (Ereignisse `Start`, `Msg(Typ, Nutzlast)`, `Attn`, `TlsDone`, `Disc`; Ausgaben Schreiben, TLS-Start, Schließen) und demselben fingierten Backend:

- **A**: `enum`-Zustände (`CONNECTED`, `PRELOGIN`, `TLS_NEG`, `LOGIN7`, `IDLE`, `RUNNING`, `STREAMING`, `CANCELLING`, `COMPLETED`, `CLOSED`) und eine Übergangsfunktion `step(Zustand, Ereignisart) → (Folgezustand, Aktion)`; jeder Übergang samt Schreiben läuft unter einem `Mutex` je Sitzung.
- **B**: eine Kette von sieben Handlern ohne Zustandsaufzählung; der Zustand steckt in Flags (`started`, `preDone`, `tlsOk`, `loggedIn`, `active`, `attn`). **B0** ohne Sperre, **B** dieselbe Kette mit `Mutex` um jeden Handler und um jedes Schreiben des Anfragepfads.
- **C**: eine Sitzungs-Coroutine mit Postfach (`Channel`), die als Einzige schreibt und deren Zustand die Position im Code ist (Handshake-Folge, dann Anfrageschleife); der Anfragepfad meldet Zeilen und Abschluss als Nachrichten an das Postfach.

Alle drei bilden denselben Anfragezustand ab (`IDLE → RUNNING → STREAMING → COMPLETED → IDLE`, ATTENTION über `CANCELLING`), beantworten ATTENTION in `IDLE` und `COMPLETED` nur mit dem Abschluss mit ATTENTION-Status, lassen bei gleichzeitigem Abschluss und ATTENTION den ersten Übergang gewinnen und schließen die Verbindung ohne Antwort bei jedem unzulässigen Ereignis. **Diese Antwort auf einen unzulässigen Übergang ist eine Setzung des Spike**, die Spezifikation legt sie nicht fest.

| Messung | A expliziter Automat | B0 Kette ohne Sperre | B Kette mit Sperre | C Akteur |
|---|---|---|---|---|
| Nicht leere Zeilen der Sitzungsdatei (A davon 48 in der Übergangsfunktion) | 117 | 74 | 74 | 82 |
| Matrix: 10 Zustände × 14 Ereignisse × 5 Folgeproben = 700 Läufe gegen die Referenztabelle, Abweichungen | 0 | 0 | 0 | 0 |
| Zufallsfolgen (3000, Länge 1 bis 12, 70 % zulässige Ereignisse) gegen die Referenztabelle, Abweichungen / Ressourcenbefunde | 0 / 0 | 0 / 0 | 0 / 0 | 0 / 0 |
| Wettlauf Abschluss gegen ATTENTION, 20 000 Läufe, davon unzulässige Antwortfolgen | 0 | 0 | 0 | 0 |
| Wettlauf: gleichzeitige Schreibvorgänge im Io, Summe über 20 000 Läufe (zwei Läufe) | 0 / 0 | 4560 / 6132 | 0 / 0 | 0 / 0 |
| Wettlauf: Folgeanfrage ohne Antwort, Sitzung geschlossen, Backend nicht oder doppelt geschlossen | 0 | 0 | 0 | 0 |
| Durchsatz, 1 Verbindung, Folgeanfragen nacheinander, Fake-Io, letzte Runde, Anfragen/s (zwei Läufe) | 140 408 / 114 660 | Stillstand (siehe Befunde) | 156 080 / 107 809 | 120 903 / 83 719 |
| Heap je angemeldeter Leerlauf-Sitzung (20 000 Sitzungen, Sitzungsobjekte ohne Transportpuffer, Byte) | 342 | 647 | 644 | 1136 |
| Mutation: Zustandszweig `RUNNING/STREAMING` entfernt | Kompilierfehler | – | – | – |
| Mutation: neuer Zustand ohne Zweig | Kompilierfehler | – | – | – |
| Mutation: Behandlung von ATTENTION entfernt (A: Zweig in `IDLE`; B: Handler `attnH`; C: Zweig `Attn`) | kompiliert; Matrix 16 Abweichungen | – | kompiliert; Matrix 26 Abweichungen | kompiliert; Matrix 26 Abweichungen |

Die Matrix umfasst 140 Zellen (Zustand × Ereignis): 18 zulässige Übergänge, 85 unzulässige (Verbindung wird geschlossen, nichts gesendet), 9 `Disc`, 14 Zellen ohne Wirkung (`RELEASE`/`FAIL` des Backends außerhalb einer Anfrage) und 14 im Zustand `CLOSED`. Die Zustände `CANCELLING` und `COMPLETED` sind nur über den Wettlauf, nicht als Matrixzeile geprüft. Die Tabelle nennt den zweiten Lauf. Der erste Lauf, während andere Builds liefen, zeigte je eine Abweichung bei A in Matrix und Zufallsfolgen (die erwartete Abschlussantwort fehlte beim Auswerten); das ist vermutlich auf zu kurzes Warten der Testhilfe auf Ruhe zurückzuführen und nicht weiter untersucht; nach Verlängerung des Wartens ergab der zweite Lauf überall 0. Der Durchsatz derselben Option schwankt zwischen den beiden Läufen um bis zu etwa 30 %.

Referenzclient gegen den Server, je Option (A, B mit Sperre, C) in den drei Verbindungsarten TDS 7.4 ohne TLS (`Encrypt=false`), TDS 7.4 mit TLS (`Encrypt=true;TrustServerCertificate=true`) und TDS 8.0 (`Encrypt=Strict;HostNameInCertificate=localhost`); alle drei Optionen ergaben dieselben Zählungen, außer den zeitabhängigen Anteilen im Szenario mit dem zweiten Thread:

| Szenario (je Verbindungsart) | Ergebnis (A, B, C gleich) |
|---|---|
| `Open`, `SELECT 1`, `Close` | gelingt in allen drei Arten |
| 200 Befehle auf einer Verbindung | 200/200 |
| `ExecuteReader` auf einem Strom von 100 Mio. Zeilen, nach 1000 Zeilen `SqlCommand.Cancel()`, danach Lesen bis zum Ende, danach `SELECT 1` auf derselben Verbindung, 100 Läufe | 100/100 mit Abbruchmeldung des Clients, 100/100 Folgebefehl gelingt, 0 defekte Verbindungen |
| `Cancel()` aus einem zweiten Thread gegen einen schnellen Befehl (Zufallsverzug bis etwa 1 ms), 1000 Läufe | 1000/1000 Folgebefehl gelingt, 0 defekte Verbindungen; Wert geliefert 90 bis 418, abgebrochen 581 bis 910, 0 bis 6 als `InvalidOperationException` „Operation cancelled by user“ (je nach Option und Art) |
| `Cancel()` während `RUNNING` (Backend hängt), 20 Läufe | 20/20 abgebrochen, 20/20 Folgebefehl gelingt |
| 30 parallele Verbindungen (10 Stromabbruch, 10 `SELECT 1` × 300, 10 Abbruch bei `RUNNING` × 5) | 0 Verbindungen mit Abweichung |
| Rohe Pakete (TDS 7.4 ohne TLS): SQL Batch vor Login, LOGIN7 zuerst, ATTENTION zuerst und vor Login, zweites PRELOGIN, zweites LOGIN7, PRELOGIN nach Login, Pakettyp `0x7F`, zweiter Batch während der ersten läuft, Nachricht über 64 KiB vor Login (11 Fälle) | 11/11: Verbindung geschlossen, 0 Antwortbytes, Server danach gesund |
| 50 harte Verbindungsabbrüche (RST) während `RUNNING` und `STREAMING` | Backend geöffnet = geschlossen (18 138 / 18 224 / 18 245 über den ganzen Lauf), 0 doppelt geschlossen |

Befunde:

- Nach den Messwerten trennen die Optionen die geprüften Zustandsfolgen, Wettläufe und Clientszenarien nicht: A, B (mit Sperre) und C bestehen alle. Was sie trennt, ist, **wer einen vergessenen Fall findet** und **was Nebenläufigkeit kostet**: Fehlt in A ein Zustand, bricht das Kompilieren ab (die Zustände sind ein `enum` in einem `when`-Ausdruck); fehlt ein Ereigniszweig innerhalb eines Zustands oder in B und C eine Behandlung, kompiliert der Code, und nur die Matrix findet es (16 bis 26 Abweichungen in den drei Mutationen, die kompilieren und eine Behandlung entfernen). Der Schutz in A reicht damit nur für Zustände, nicht für Ereignisse je Zustand.
- B0 (ohne Sperre) erzeugte im Wettlauf 4560 bzw. 6132 gleichzeitige Schreibvorgänge (Summe über 20 000 Läufe) (Abschlussantwort und ATTENTION-Abschluss), ohne dass die gesendete Abfolge unzulässig wurde. Auf dem echten Transport würde ein zweites gleichzeitiges `write` auf demselben `AsynchronousSocketChannel` scheitern oder TLS-Records vermischen; das ist **nicht gemessen**, B0 lief nur im Fake-Io. Im Durchsatzlauf lehnte B0 eine Folgeanfrage ab, die unmittelbar nach dem Schreiben der Antwort eintraf (Stillstand bei Anfrage 126 und 8273 in den zwei Läufen), weil `active` erst nach dem Schreiben auf `null` gesetzt wird; die Folgeanfrage kam im Test schneller als im Netz möglich. Dass ein echter Client diesen Zeitpunkt trifft, ist nicht gemessen.
- Die ATTENTION-Antwort in `IDLE`/`COMPLETED` (nur der Abschluss mit ATTENTION-Status, vorgesehen für den Fall, dass `Cancel()` und Abschluss sich kreuzen) wird in den Referenzclient-Läufen nicht einzeln gezählt. Dass der Client in 1000/1000 Läufen je Art den Folgebefehl ausführen konnte, ist nur mittelbar ein Beleg.
- C braucht mit 1136 Byte rund das Dreifache an Sitzungsspeicher gegenüber A und lag im Durchsatzlauf in beiden Läufen unter A und B (83 719 bis 120 903 gegen 107 809 bis 156 080 bei B und 114 660 bis 140 408 bei A). Gemessen ist nur der Fake-Io ohne Netz; ob der Unterschied gegenüber dem Netzdurchsatz zählt, ist nicht gemessen. Dafür schreibt nur der Akteur, die Wettläufe brauchen keine Sperre, und `on()` muss eine Bestätigung abwarten, damit der Transport nach PRELOGIN weiß, ob er den TLS-Handshake beginnt (Ack-Plumbing, in A und B entfällt es).
- Zustandsmodell und Nebenläufigkeitsmodell sind in der ADR-Tabelle in einer Achse zusammengefasst, im Spike aber getrennt tragend: A ist ein explizites Zustandsmodell, serialisiert über einen `Mutex`; B ein implizites Zustandsmodell in Flags (B0 ungeschützt, B mit `Mutex`); C ein implizites Modell in der Codeposition, serialisiert über ein Postfach. Ein expliziter Automat im Akteur (A-Tabelle, C-Serialisierung) ist **nicht gebaut**.
- Der Spike-Transport schreibt jedes Paket unabhängig von der Abbruchanforderung (`NonCancellable`), damit ein abgebrochener Strom kein halbes Paket hinterlässt. Ein Lauf ohne diese Maßgabe wurde nicht gebaut; ob sie nötig ist, ist nicht gemessen.
- In allen drei Läufen traten bei den 50 harten Abbrüchen im Serverprozess 24 (A), 24 (B) und 22 (C) nicht abgefangene `IOException: Broken pipe` in den Threads der Kanalgruppe auf (zusätzlich je 10 `ClosedChannelException` bzw. `AsynchronousCloseException` im Verbindungscode); die Ursache liegt im Spike-Adapter (Fortsetzung eines abgebrochenen Schreibens), gleich für alle Optionen, nicht analysiert.

Empfehlung des Agenten (**Empfehlung, keine Entscheidung**; die Optionen sind in der Messung nicht auf Korrektheit getrennt): Option A als Zustandsmodell, weil der Compiler fehlende Zustände meldet und die Übergangsfunktion als reine Funktion ohne Netz die Matrixprüfung trägt; die Kosten sind etwa 40 Zeilen mehr als B und C (117 gegen 74 und 82). Für das Nebenläufigkeitsmodell zeigt die Messung `Mutex` (A, B mit Sperre) und Postfach (C) als gleichwertig in der Korrektheit; die Sperre war in beiden Läufen günstiger in Speicher und Durchsatz, das Postfach vermeidet Schreibwettläufe ohne Disziplin je Schreibstelle. B ohne Sperre scheidet nach der Messung aus. Warum ich A wählen würde: ein Defekt aus einem vergessenen Zustand ist in A ein Kompilierfehler, in B und C nur ein Testfund. Diese Begründung ist eine Setzung des Agenten; die Messung belegt die Mutationsbefunde, nicht ihre Bedeutung im Betrieb.

Die Messung trägt **nicht**: Aussagen über das Verhalten eines echten SQL Servers (Antwort auf unzulässige Übergänge, Verhalten nach ATTENTION in `IDLE`), MARS, Transaktionen, Pooling (`sp_reset_connection`), `Command Timeout` und Login-Zeitgrenze ([SPEC-008](../../../../spec/spezifikation.md#3-defaults-und-konstanten)), die `CANCEL_TIMEOUT`-Frist ([SPEC-033](../../../../spec/spezifikation.md#3-defaults-und-konstanten); der Spike setzt sie nicht), Last über 30 Verbindungen mit dem Referenzclient (die 20 000 Wettläufe laufen im Fake-Io mit 4 Threads), Latenz-Perzentile, Langzeitlauf, RPC-Zustände (ein RPC wurde wie ein Batch behandelt) und ob eine andere Autorin oder ein anderer Autor die Optionen anders schriebe. Die Referenztabelle und die drei Sitzungen stammen vom selben Autor; die Nullen in der Matrix belegen die Übereinstimmung mit dieser Tabelle, nicht ihre Richtigkeit gegen [SPEC-001](../../../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung).

**Entscheidung des Nutzers (2026-10-30):** Zustandsmodell A (expliziter Automat) mit `Mutex` je Sitzung; B0 (ohne Sperre) scheidet nach der Messung aus. [ADR-0004](../../adr/0004-session-zustandsautomat.md) trägt Messung, Entscheidung und die offenen Punkte (Antwort auf unzulässige Übergänge, ATTENTION im Handshake und in jedem Anfragezustand, Ende des Handshakes nach abgelehntem Login, Zustandszuschnitt, Sitzungszustand nach Fehler) und bleibt `Proposed` bis Review und Verifikation; `SPEC-001` ist nachgezogen (der Zuschnitt der Aufzählung bleibt dort offen). Dass ein fehlender Zustand in B und C nur ein Testfund wäre, ist eine Struktur-Folgerung, nicht gemessen.

**Nachführung (Entscheidung des Nutzers, 2026-10-30):** Das Ende des Handshakes nach abgelehntem Login ist entschieden und liegt bei [ADR-0007](../../adr/0007-fehler-und-attention.md): der Server schließt, ein weiteres LOGIN7 wird nicht angenommen (Setzung, nicht gemessen). [ADR-0004](../../adr/0004-session-zustandsautomat.md) hat das übernommen; offen bleiben hier unverändert die Reichweite von ATTENTION im Handshake, ATTENTION in jedem Anfragezustand, Sitzungszustand nach Fehler, `RESETCONNECTION`, der Zeitpunkt des Schließens nach `TDS_SHUTDOWN` und der Zuschnitt der Zustände.

Wiederholung: die Spike-Dateien (`Dockerfile`, `kotlin/`, `dotnet/`, `run.sh`, `mutate.sh`) liegen nur im Scratchpad des Spike-Agenten und gehören nicht ins Repo; `docker build --progress=plain --target inproc .` führt Matrix, Zufallsfolgen, Wettlauf, Durchsatz und Speicher aus, das Standardziel den Referenzclient.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `slice-adr-transport` liegt in `done/`.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Die Zustands- und Ereignismatrix ist ohne die Pufferstrategie des Transports nicht abschließbar.

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

- Die Zustands- und Ereignismatrix ist ohne die Pufferstrategie des Transports nicht abschließbar. — **Ausgang:** offen bis zur Closure.

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
