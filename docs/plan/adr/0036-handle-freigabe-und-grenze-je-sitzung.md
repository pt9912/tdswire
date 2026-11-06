# ADR-0036: Freigabe von Prepared-Handles, Antwortform von `sp_prepexec` und Grenze je Sitzung (ersetzt teilweise ADR-0017)

**Status:** Proposed (Entwurf des Implementers; `Accepted` entscheidet der Nutzer)

**Datum:** 2026-11-06

**Autor:** pt9912

**Bezug:** [TDS-FA-12](../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-FA-04](../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0004](0004-session-zustandsautomat.md), [ADR-0005](0005-rpc-und-prepared-statements.md), [ADR-0014](0014-fehlerfolge-rpc-antwort.md), [ADR-0017](0017-prepared-handles-grenzwert.md), [ADR-0021](0021-oeffentlicher-integrations-einstieg.md), [ADR-0026](0026-oeffentlichkeit-simulations-backend-und-katalog.md), [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)

**Schärft (nach `Accepted`):** [SPEC-030](../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten) (Antwort auf `sp_prepexec`, Freigabe, Zählung, Grenze), [SPEC-001](../../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung) (Batch-Form der Freigabe ohne Backend im Sitzungsrahmen), Zeilen `SPEC-013`, `SPEC-057`, `SPEC-058` in [§4 der Spezifikation](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) und `SPEC-034` in [§3 der Spezifikation](../../../spec/spezifikation.md#3-defaults-und-konstanten) (Speicher je Sitzung, Eingang der Grenze).

**Supersedes (teilweise), nur wörtlich benannt; die Datei des Vorgängers bleibt unverändert und `Accepted` ([AGENTS.md](../../../AGENTS.md) §3.5):** [ADR-0017](0017-prepared-handles-grenzwert.md) (1) Entscheidung 1, **nur** der Satz „Gezählt werden die gleichzeitig gehaltenen Handles; weil `sp_unprepare` nicht unterstützt wird, heißt das heute: die in der Sitzung bisher erfolgreich vergebenen.“; (2) Entscheidung 4 vollständig: „Konfigurierbarkeit. Die Zahl ist fest; sie ist nicht per Umgebung einstellbar (Option c nicht gewählt, siehe Alternativen). Wird sie änderbar, ist das eine neue Entscheidung (Trigger).“ Es bleiben: die Zahl 1000 und der Satz „Eine Sitzung hält höchstens **1000** Prepared-Handles gleichzeitig“ als Voreinstellung (Setzung), die Entscheidungen 2 und 3 und alle Alternativen. *Gilt für die Fassung der Entscheidung unten (Option D mit B). Wählt der Nutzer eine andere Option, ändert sich der Umfang: bei A zusätzlich die Zahl in Entscheidung 1, bei F zusätzlich Entscheidung 2, bei D allein entfällt Punkt (2).*

**Regeln:** Baseline-Regelwerk `modul-04-adrs.md` §Ziel-Form: ADR (MADR).

---

## Kontext

[ADR-0017](0017-prepared-handles-grenzwert.md) setzt die Grenze auf 1000 Handles je Sitzung, fest, und lehnt `sp_unprepare` ab, weil der Client „in den gemessenen Szenarien (ohne Pool)“ keines sende. Der Spike zu [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) maß mit d-migrate 1.12.0 (mssql-jdbc 13.4.0) gegen einen Prototyp: `--on-conflict skip` mit 20 000 Zeilen endet mit Exit 5 „Prepared handle limit reached“ nach 1001 `MERGE` je Sitzung, und in 2002 Anweisungen kam kein `sp_unprepare` an (a6 im [Messprotokoll des Spikes](../../messungen/callback-loesung-spike-2026-11-05.md)). Der Nutzer entschied am 2026-11-06, die Ablösung sei eine eigene ADR und ein eigener Slice vor dem `MERGE`-Slice.

Die Messung dieser ADR ([Messprotokoll](../../messungen/handle-grenze-optionen-2026-11-06.md), Zeilen h, t und u; Treiber allein gegen den gebauten Testserver, nicht gegen einen echten SQL Server, nicht d-migrate) ändert das Bild:

1. **Das Muster des Spikes entsteht schon im Treiber und dort an der Antwort des Testservers.** Mit Erstes-Mal-Prepare schickt mssql-jdbc ein `sp_prepexec` je Ausführung und nach 1001 Ausführungen endet die Sitzung an der Grenze; auch ein einzelnes, wiederholt ausgeführtes Anweisungsobjekt bekommt je Ausführung ein neues Handle und nie ein `sp_execute` (h2, h3). Der Treiber sendet das Handle 0 im OUTPUT-Parameter und sendet nie `sp_unprepare` (h6, h2).
2. **Mit einem RETURNSTATUS vor dem RETURNVALUE `@handle` ändert sich das Verhalten des Treibers**, obwohl der Testserver sonst unverändert bleibt: ein Anweisungsobjekt benutzt sein Handle mit `sp_execute` (198 von 200, t1), und der Treiber gibt geschlossene Anweisungen frei (t3, t4) — **als SQL-Batch** `EXEC sp_unprepare 1;EXEC sp_unprepare 2;…` mit elf Handles je Batch, nicht als RPC (t5). Der Testserver beantwortet diesen Batch heute mit „Unsupported SQL.“, der Treiber setzt fort (t6); der Server gibt nichts frei, die Grenze bleibt erreichbar (t4).
3. **Der Treiber verträgt eine Antwort ohne Handle** und bereitet beim nächsten Mal neu vor (u1, u2).

Gemessen ist das am Treiber; **nicht gemessen** ist: ob d-migrate seine Anweisungsobjekte schließt oder wiederverwendet (die Reihen `neu` und `eine` sind die Grenzfälle), ob ein echter SQL Server RETURNSTATUS auf `sp_prepexec` sendet, wie er `sp_unprepare` mit unbekanntem Handle beantwortet, wie viele Handles er je Sitzung hält, und der Speicher je Handle (`AGENTS.md` §3.8).

## Entscheidung

*Vorschlag des Implementers, nicht bindend, bis der Nutzer die ADR annimmt; jede Nummer ist eine Setzung (Voreinstellung, nicht am echten SQL Server gemessen), soweit nichts anderes dabeisteht.*

Wir wählen **Option D (Freigabe, mit Antwortform) zusammen mit Option B (Grenze einstellbar)**.

1. **Antwortform (Setzung; Belege t1, h6).** Die Antwort auf `sp_prepexec` trägt ein RETURNSTATUS (Wert 0) vor dem RETURNVALUE `@handle`. Das schärft die Tokenfolge in [SPEC-030](../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten), die dort als Handvektor gilt; die Wirkung ist am Treiber gemessen, ein echter SQL Server nicht.
2. **Freigabe (Setzung).** Zwei Eingänge geben die genannten Handles frei, ohne das Backend zu rufen: der RPC `sp_unprepare` (ein Handle; ProcID 15 laut MS-TDS, der Beleg ist beim Bau zu beschaffen, kein Treiber der Messung sendet ihn) und der SQL-Batch der Form `EXEC sp_unprepare <n>` mit weiteren `;EXEC sp_unprepare <n>` (die Form, die mssql-jdbc 13.4.0 sendet, t5; das Ende des Textes ist nicht im Mitschnitt, die Grammatik ist aus zwei Batches gelesen). Die Antwort ist ein Erfolg ohne Zeilen; ein unbekanntes Handle ist kein Fehler (idempotent), weil ein Batch mehrere nennt und `RESETCONNECTION` die Tabelle leert. Eine freigegebene Nummer wird nicht wieder vergeben (der Zähler bleibt wie in [SPEC-030](../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten)).
3. **Zählung.** Gezählt werden die gleichzeitig gehaltenen Handles: vergebene minus freigegebene (ersetzt den Zählsatz in Entscheidung 1 von [ADR-0017](0017-prepared-handles-grenzwert.md)).
4. **Grenze einstellbar.** Die Voreinstellung bleibt **1000**; sie ist ein Eingang der Bibliothek (Name, Bereich und Umgebungsvariable des Testservers legt die Spezifikation fest). Das ersetzt Entscheidung 4 von [ADR-0017](0017-prepared-handles-grenzwert.md). Grund: Ob d-migrate seine Anweisungen schließt, ist nicht gemessen; schließt es sie nicht, hilft Nr. 2 nicht, und der Betreiber braucht einen Stellwert. Der Eingang erweitert die öffentliche Fläche ([ADR-0021](0021-oeffentlicher-integrations-einstieg.md), [ADR-0026](0026-oeffentlichkeit-simulations-backend-und-katalog.md)); der Slice, der sie verkleinert, ist davon berührt.
5. **Überschreitung** bleibt Entscheidung 2 von [ADR-0017](0017-prepared-handles-grenzwert.md) (definierter Fehler, Verbindung offen) — unberührt.

## Verglichene Alternativen

Die Zeilen nennen die Messzeile des Protokolls oder die Kennzeichnung „Setzung“/„nicht gemessen“.

| Option | Pro | Contra |
|---|---|---|
| A — Zahl fest anheben (z. B. 100 000; der Spike stellte den Prototyp für a4 auf 100 000, ohne Speichermessung) | kleinster Eingriff, eine Zahl; löst a6 für 20 000 Zeilen | verschiebt die Grenze, ohne die Ursache anzufassen (h2, h3: ein Handle je Ausführung); Speicher bis zu Grenze × `MAX_CONNECTIONS` (1000) × Textgröße, nichts davon gemessen; Texte sind nicht begrenzt (`SPEC-030`) |
| B — Zahl einstellbar (Voreinstellung 1000) | Betreiber kann die Zahl an seinen Client anpassen; ändert nichts am Protokoll | neuer Eingang und neue öffentliche Fläche; löst die Ursache nicht; wer die Zahl erhöht, trägt den Speicher |
| C — Wiederverwendung: gleicher Text und gleiche Deklarationsliste liefern dasselbe Handle | zählt verschiedene Anweisungen statt Ausführungen; kein neues Protokoll | **nicht gemessen** (kein Server-Prototyp); weicht von einem Server ab, der je `sp_prepexec` ein Handle vergibt (nicht gemessen); verlangt Zählung der Nutzer eines Handles, sobald es freigegeben wird; der Treiber gibt bei h3 ohne RETURNSTATUS gar kein Handle frei |
| D — Freigabe: Antwortform mit RETURNSTATUS, `sp_unprepare` als RPC und als Batch | wirkt an der Ursache: ein Handle je Anweisungsobjekt (t1), Freigabe geschlossener (t3); die Tabelle bleibt klein, solange der Client schließt; entspricht dem, was der Treiber sendet | ändert den RPC-Katalog (`SPEC-013`, `SPEC-030`) und das Batch-Verhalten (Sitzungsrahmen erkennt eine Form vor dem Backend); wirkt nur, wenn d-migrate Anweisungen schließt oder wiederverwendet (nicht gemessen); Wirkung des RETURNSTATUS und Antwort eines echten Servers nicht gemessen; unbekanntes Handle bei `sp_unprepare` ist Setzung |
| E — Verdrängung des ältesten (oder am längsten ungenutzten) Handles | die Sitzung erreicht die Grenze nie | in [ADR-0017](0017-prepared-handles-grenzwert.md) als Option c verworfen: ein verdrängtes Handle ergibt bei `sp_execute` einen Fehler mit falscher Ursache; bei t1 hält ein Anweisungsobjekt sein Handle lange und wäre das erste Opfer; die Reaktion des Treibers auf den Fehler ist nicht gemessen |
| F — Überlauf ohne Handle: die Anweisung läuft, die Antwort trägt kein Handle | keine Fehlerantwort; der Treiber verträgt das (u1, u2) und bereitet neu vor; Speicher begrenzt | ändert Entscheidung 2 von [ADR-0017](0017-prepared-handles-grenzwert.md) (definierter Fehler); der Client merkt die Verschlechterung nicht; am Server nicht gebaut und nicht gemessen; andere Clients (.NET) nicht gemessen |
| G — nichts tun | keine Änderung | der Import bricht bei 1001 `MERGE` je Sitzung (a6); ob sich d-migrate so steuern lässt, dass es die Grenze nicht berührt (es nutzte zwei Sitzungen), ist nicht gemessen; die Zerlegung läge beim Nutzer des Clients, nicht beim Server |

## Konsequenzen

- Positiv: Der Treiber nutzt ein Handle je Anweisungsobjekt (t1) und gibt geschlossene Anweisungen frei (t3); die Handle-Zahl folgt dann den lebenden Anweisungen statt den Ausführungen. Die Grenze bleibt als Schutz ([TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilität)), einstellbar.
- Negativ: Der RPC-Katalog wächst um `sp_unprepare`; der Sitzungsrahmen liest eine Batch-Form, bevor ein Backend gerufen wird; zwei neue Zusagen (Antwortform, Freigabe) gelten bisher nur am Treiber. Ob d-migrate seine Anweisungen schließt, entscheidet, ob die Wirkung für den `MERGE`-Pfad eintritt; bis dahin ist die Wirkung nur am Treiber belegt.
- **Folgepflicht (nach `Accepted`):** (1) Spezifikation, siehe Liste unten. (2) Quelltext und Tests: Antwortform, Freigabe (RPC und Batch), Zählung, einstellbare Grenze; Träger ist der Slice des `MERGE`-Pfads, solange der Nutzer keinen eigenen Bau-Slice schneidet (siehe Offen 1). (3) Messung: ein Lauf von d-migrate mit `--on-conflict skip` über mehr als 1001 Zeilen je Sitzung gegen den Testserver, mit Zählung der Handles; Träger der `MERGE`-Slice.

### Liste der Spec-Änderungen (schreibt der Träger nach `Accepted`; dieser Slice ändert die Spezifikation nicht)

| Stelle | Änderung |
|---|---|
| `SPEC-030`, Antwort auf `sp_prepexec` | RETURNSTATUS (Wert 0) vor RETURNVALUE `@handle`, als Setzung gekennzeichnet; Beleg Messzeilen h6 und t1 |
| `SPEC-030`, Handle-Verwaltung | Satz „`sp_unprepare` und `sp_prepare` sind nicht im Katalog … und geben kein Handle frei“ ersetzen: `sp_unprepare` (RPC und Batch-Form) gibt frei, `sp_prepare` bleibt außerhalb; Zählsatz „gleichzeitig gehalten“; Zahl 1000 als Voreinstellung, Klammer „nicht konfigurierbar“ streichen |
| `SPEC-030`, RPC-Tabelle und Absatz darunter | Zeile `sp_unprepare`; „auch `sp_prepare`, `sp_unprepare` und `sp_cursor*`“ ohne `sp_unprepare`; Satz zu Pooling („ein gepoolter Client kann `sp_unprepare` senden, das der Katalog mit einem Fehler beantwortet“) ersetzen |
| `SPEC-030`, Parameter 0 von `sp_prepexec` | „bei Erstaufruf −1“ ergänzen: mssql-jdbc 13.4.0 sendet 0 (h6); beide Werte sind „kein Handle“ |
| `SPEC-013` | „darunter `sp_prepare` und `sp_unprepare`“ nur `sp_prepare` |
| `SPEC-057`, `SPEC-058` | `SPEC-058`: „bei erreichter Grenze von 1000“ wird „bei erreichter Grenze (Voreinstellung 1000)“; `SPEC-057` unverändert |
| `SPEC-034` und §3 | Zeile für den Eingang der Handle-Grenze (Name, Bereich, Voreinstellung 1000, Testserver-Umgebungsvariable); Speicher je Sitzung: Grenze × Textgröße, nicht gemessen |
| `SPEC-001` | die Batch-Form der Freigabe wird im Sitzungsrahmen beantwortet, ohne Backend, wie `RESETCONNECTION` an derselben Stelle behandelt |
| RPC-Katalog des Referenzclients (Abschnitt „RPC-Katalog des Referenzclients“) | mssql-jdbc 13.4.0: `sp_unprepare` als SQL-Batch, Form und Schwelle (Messzeile t5); Messgrenzen wie dort |
| `SPEC-036` | keine Änderung, solange die Freigabe im Sitzungsrahmen liegt und nicht im Backend |

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| JVM-Tests (`kotlin.test`) | `sp_prepexec` antwortet mit RETURNSTATUS vor `@handle` (Handvektor); `sp_unprepare` als RPC und als Batch gibt frei, die Zählung sinkt, eine freigegebene Nummer wird nicht vergeben; unbekanntes Handle im Batch ohne Fehler und die übrigen Handles des Batches werden freigegeben; die Anfrage, die über die eingestellte Grenze vergäbe, ergibt weiter `SPEC-058`; zwei Sitzungen zählen getrennt. Gegenprobe: ohne das Entfernen aus der Tabelle bleibt die Zählung stehen und der Test an der Grenze fällt | `make test` |
| Lauf mit mssql-jdbc 13.4.0 (kein Gate; der Träger legt Wiederholbarkeit fest) | ein Anweisungsobjekt, 200 Ausführungen: 1 `sp_prepexec` und 198 `sp_execute` (t1); 1100 neue Anweisungen mit Erstes-Mal-Prepare: kein Abbruch an der Grenze (Gegenstück zu t4) | der Slice des `MERGE`-Pfads |

## Offen

1. **Bau-Slice.** Der `MERGE`-Slice hat drei Liefer-Punkte und trägt nach diesem Vorschlag zusätzlich Antwortform, Freigabe und Eingang der Grenze; eine eigene Zerlegung (Bau-Slice vor dem `MERGE`-Slice) ist eine Entscheidung des Nutzers.
2. **Verhalten von d-migrate** beim `MERGE`: Schließen oder Wiederverwenden der Anweisungsobjekte (nicht gemessen); entscheidet, ob Nr. 2 ausreicht. Träger: der Beleg am fremden Client im `MERGE`-Slice.
3. **Echter SQL Server:** RETURNSTATUS auf `sp_prepexec`, Antwort auf `sp_unprepare` mit unbekanntem Handle, Grenze der Handles je Sitzung, Speicher je Handle. Träger: der Slice der Vergleichsmessungen am echten SQL Server (der Slice der Vergleichsmessungen am echten SQL Server; EULA-Entscheidung des Nutzers), `BEO-TDS/messung-am-echten-sql-server-fehlt`.
4. **Pooling** (`RESETCONNECTION`, `sp_unprepare` im Pool, TDS 8.0): nicht gemessen; `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`.
5. **Andere Treiberversionen und Clients** (.NET sendet möglicherweise `sp_unprepare` als RPC; nicht gemessen).
6. **Reaktion des Treibers** auf `sp_execute` mit unbekanntem Handle (Option E): nicht gemessen, weil der Testserver den Fehler in der Messreihe nicht auslöst.
7. **Name, Bereich und Umgebungsvariable** des Eingangs der Grenze (Nr. 4): offen für die Spezifikation.

## Re-Evaluierungs-Trigger

Ein Lauf von d-migrate zeigt, dass es seine Anweisungen weder schließt noch wiederverwendet (dann Option A, C oder F neu); ein echter SQL Server sendet auf `sp_prepexec` kein RETURNSTATUS oder verhält sich bei `sp_unprepare` anders; eine andere Version von mssql-jdbc sendet `sp_unprepare` anders; eine Messung des Speichers je Handle widerlegt die Grenze; ein gepoolter Referenzclient erreicht die Grenze im Normalbetrieb.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-06 | Proposed – Entwurf mit Optionen A bis G, Messzeilen h, t, u und Empfehlung D mit B; die Entscheidung trifft der Nutzer | [Messprotokoll](../../messungen/handle-grenze-optionen-2026-11-06.md), [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Folgepflicht 1 |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
