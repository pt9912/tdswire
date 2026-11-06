# ADR-0036: Freigabe von Prepared-Handles, Antwortform von `sp_prepexec` und Grenze je Sitzung (ersetzt teilweise ADR-0017)

**Status:** Accepted (vom Nutzer entschieden, 2026-11-06: „deine empfehlungen“)

**Datum:** 2026-11-06 (Entwurf und Annahme)

**Autor:** pt9912

**Bezug:** [TDS-FA-12](../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-FA-04](../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0004](0004-session-zustandsautomat.md), [ADR-0005](0005-rpc-und-prepared-statements.md), [ADR-0014](0014-fehlerfolge-rpc-antwort.md), [ADR-0017](0017-prepared-handles-grenzwert.md), [ADR-0021](0021-oeffentlicher-integrations-einstieg.md), [ADR-0026](0026-oeffentlichkeit-simulations-backend-und-katalog.md), [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)

**Schärft:** [SPEC-030](../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten) (Antwort auf `sp_prepexec`, Freigabe, Zählung, Grenze), [SPEC-001](../../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung) (Batch-Form der Freigabe ohne Backend im Sitzungsrahmen), Zeilen `SPEC-013` und `SPEC-058` in [§4 der Spezifikation](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) und `SPEC-034` in [§3 der Spezifikation](../../../spec/spezifikation.md#3-defaults-und-konstanten) (Speicher je Sitzung, Eingang der Grenze), `SPEC-059` (falls die Grenze ein öffentlicher Name wird, Offen 7); `SPEC-057`, `SPEC-013` und Nicht-Ziel 6 der Spezifikation bleiben unverändert (der RPC `sp_unprepare` bleibt abgelehnt).

**Supersedes (teilweise), nur wörtlich benannt; die Datei des Vorgängers bleibt unverändert und `Accepted` ([AGENTS.md](../../../AGENTS.md) §3.5):** [ADR-0017](0017-prepared-handles-grenzwert.md) (1) Entscheidung 1, **nur** der Satz „Gezählt werden die gleichzeitig gehaltenen Handles; weil `sp_unprepare` nicht unterstützt wird, heißt das heute: die in der Sitzung bisher erfolgreich vergebenen.“; (2) Entscheidung 4 vollständig: „Konfigurierbarkeit. Die Zahl ist fest; sie ist nicht per Umgebung einstellbar (Option c nicht gewählt, siehe Alternativen). Wird sie änderbar, ist das eine neue Entscheidung (Trigger).“ Es bleiben: die Zahl 1000 und der Satz „Eine Sitzung hält höchstens **1000** Prepared-Handles gleichzeitig“ als Voreinstellung (Setzung), die Entscheidungen 2 und 3 und alle Alternativen. [ADR-0005](0005-rpc-und-prepared-statements.md) bleibt unberührt: der RPC `sp_unprepare` bleibt außerhalb des Katalogs, die Freigabe läuft nur über die Batch-Form.

**Regeln:** Baseline-Regelwerk `modul-04-adrs.md` §Ziel-Form: ADR (MADR).

---

## Kontext

[ADR-0017](0017-prepared-handles-grenzwert.md) setzt die Grenze auf 1000 Handles je Sitzung, fest, und lehnt `sp_unprepare` ab, weil der Client „in den gemessenen Szenarien (ohne Pool)“ keines sende. Der Spike zu [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) maß mit d-migrate 1.12.0 (mssql-jdbc 13.4.0) gegen einen Prototyp: `--on-conflict skip` mit 20 000 Zeilen endet mit Exit 5 „Prepared handle limit reached“ nach 1001 `MERGE` je Sitzung, und in 2002 Anweisungen kam kein `sp_unprepare` an (a6 im [Messprotokoll des Spikes](../../messungen/callback-loesung-spike-2026-11-05.md)). Der Nutzer entschied am 2026-11-06, die Ablösung sei eine eigene ADR und ein eigener Slice vor dem `MERGE`-Slice.

Die Messung dieser ADR ([Messprotokoll](../../messungen/handle-grenze-optionen-2026-11-06.md), Zeilen h, t und u; Treiber allein gegen den gebauten Testserver, nicht gegen einen echten SQL Server, nicht d-migrate) ändert das Bild:

1. **Das Muster des Spikes zeigt sich beim Treiber allein, gegen die Antwort des Testservers (mssql-jdbc 13.4.0; ob es d-migrate trifft, ist nicht gemessen).** Mit Erstes-Mal-Prepare schickt mssql-jdbc ein `sp_prepexec` je Ausführung und nach 1001 Ausführungen endet die Sitzung an der Grenze; auch ein einzelnes, wiederholt ausgeführtes Anweisungsobjekt bekommt je Ausführung ein neues Handle und nie ein `sp_execute` (h2, h3). In der Reihe h3 (Voreinstellung, ein Anweisungsobjekt) sendet der Treiber den Wert 0 im OUTPUT-Parameter (h6); `sp_unprepare` sendet er in h2 bis h5 nie.
2. **Mit einem RETURNSTATUS vor dem RETURNVALUE `@handle` ändert sich das Verhalten des Treibers**, obwohl der Testserver sonst unverändert bleibt: ein Anweisungsobjekt benutzt sein Handle mit `sp_execute` (198 von 200, t1), und der Treiber gibt geschlossene Anweisungen frei (t3, t4) — **als SQL-Batch** `EXEC sp_unprepare 1;EXEC sp_unprepare 2;…` mit elf Handles je Batch, nicht als RPC (t5). Der Testserver beantwortet diesen Batch heute mit „Unsupported SQL.“, der Treiber setzt fort (t6); der Server gibt nichts frei, die Grenze bleibt erreichbar (t4).
3. **Der Treiber verträgt eine Antwort ohne Handle** und bereitet beim nächsten Mal neu vor (u1, u2).

Gemessen ist das am Treiber; **nicht gemessen** ist: ob d-migrate seine Anweisungsobjekte schließt oder wiederverwendet (die Reihen `neu` und `eine` sind die Grenzfälle), ob ein echter SQL Server RETURNSTATUS auf `sp_prepexec` sendet, wie er `sp_unprepare` mit unbekanntem Handle beantwortet, wie viele Handles er je Sitzung hält, und der Speicher je Handle (`AGENTS.md` §3.8).

## Entscheidung

*Der Nutzer hat am 2026-11-06 mit „deine empfehlungen“ die Empfehlung des Entwurfs übernommen, mit einer Änderung: der RPC `sp_unprepare` entfällt, nur die Batch-Form bleibt. Jede Nummer ist eine Setzung (Voreinstellung, nicht am echten SQL Server gemessen), soweit nichts anderes dabeisteht.*

Wir wählen **Option D (Freigabe, mit Antwortform) zusammen mit Option B (Grenze einstellbar)**.

1. **Antwortform (Setzung; Belege t1, h6).** Die Antwort auf `sp_prepexec` trägt ein RETURNSTATUS (Wert 0) vor dem RETURNVALUE `@handle`. Das schärft die Tokenfolge in [SPEC-030](../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten), die dort als Handvektor gilt; die Wirkung ist am Treiber gemessen, ein echter SQL Server nicht.
2. **Freigabe (Setzung).** Ein Eingang gibt die genannten Handles frei, ohne das Backend zu rufen: der SQL-Batch der Form `EXEC sp_unprepare <n>` mit weiteren `;EXEC sp_unprepare <n>` (die Form, die mssql-jdbc 13.4.0 sendet, t5; das Ende des Textes ist nicht im Mitschnitt, die Grammatik ist aus zwei Batches gelesen). Erkannt wird der Batch nur, wenn sein gesamter Text diese Form hat; ein gemischter Batch (etwa `EXEC sp_unprepare 1; SELECT …`) geht unverändert an das Backend. Schreibweise (`exec`, `EXECUTE`, Klammern, Zeilenumbrüche), ein nichtnumerisches oder negatives Argument und eine Länge über der Grenze der Nachricht legt die Spezifikation fest und sind Negativfälle der Prüfung (Setzung, nicht gemessen; der Treiber sendet nur die gemessene Form). Die Antwort ist ein Erfolg ohne Zeilen; ein unbekanntes Handle ist kein Fehler (idempotent), weil ein Batch mehrere nennt und `RESETCONNECTION` die Tabelle leert. Eine freigegebene Nummer wird nicht wieder vergeben (der Zähler bleibt wie in [SPEC-030](../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten)). Der RPC `sp_unprepare` (ProcID 15) bleibt abgelehnt (`SPEC-013`): kein Treiber der Messung sendet ihn, der Katalog wächst nach [ADR-0005](0005-rpc-und-prepared-statements.md) nur bei nachgewiesener Form.
3. **Zählung.** Gezählt werden die gleichzeitig gehaltenen Handles: vergebene minus freigegebene (ersetzt den Zählsatz in Entscheidung 1 von [ADR-0017](0017-prepared-handles-grenzwert.md)).
4. **Grenze einstellbar.** Die Voreinstellung bleibt **1000**; sie ist ein Eingang der Bibliothek (Name, Ort, Bereich und Umgebungsvariable des Testservers legt die Spezifikation fest, abgestimmt mit dem Slice zur Verkleinerung der öffentlichen Fläche; hier nicht festgelegt). Das ersetzt Entscheidung 4 von [ADR-0017](0017-prepared-handles-grenzwert.md). Grund: Ob d-migrate seine Anweisungen schließt, ist nicht gemessen; schließt es sie nicht, hilft Nr. 2 nicht, und der Betreiber braucht einen Stellwert. Offen ist, ob der Eingang ein neuer öffentlicher Name ist oder in eine bestehende Konfiguration der Bibliothek gehört ([ADR-0021](0021-oeffentlicher-integrations-einstieg.md), [ADR-0026](0026-oeffentlichkeit-simulations-backend-und-katalog.md), `SPEC-059`); der Slice zur Verkleinerung der öffentlichen Fläche legt die Liste der öffentlichen Namen fest und kennt den Namen nicht (Offen 7).
5. **Überschreitung** bleibt Entscheidung 2 von [ADR-0017](0017-prepared-handles-grenzwert.md) (definierter Fehler, Verbindung offen) — unberührt.

## Verglichene Alternativen

Die Zeilen nennen die Messzeile des Protokolls oder die Kennzeichnung „Setzung“/„nicht gemessen“.

| Option | Pro | Contra |
|---|---|---|
| A — Zahl fest anheben (z. B. 100 000; der Spike stellte den Prototyp für a4 auf 100 000, ohne Speichermessung) | kleinster Eingriff, eine Zahl; löst a6 für 20 000 Zeilen | verschiebt die Grenze, ohne die Ursache anzufassen (h2, h3: ein Handle je Ausführung); Speicher bis zu Grenze × `MAX_CONNECTIONS` (1000) × Textgröße, nichts davon gemessen; Texte sind nicht begrenzt (`SPEC-030`) |
| B — Zahl einstellbar (Voreinstellung 1000) | Betreiber kann die Zahl an seinen Client anpassen; ändert nichts am Protokoll | neuer Eingang und neue öffentliche Fläche; löst die Ursache nicht; wer die Zahl erhöht, trägt den Speicher |
| C — Wiederverwendung: gleicher Text und gleiche Deklarationsliste liefern dasselbe Handle | zählt verschiedene Anweisungen statt Ausführungen; kein neues Protokoll | **nicht gemessen** (kein Server-Prototyp); weicht von einem Server ab, der je `sp_prepexec` ein Handle vergibt (nicht gemessen); verlangt Zählung der Nutzer eines Handles, sobald es freigegeben wird; der Treiber gibt bei h3 ohne RETURNSTATUS gar kein Handle frei |
| D — Freigabe: Antwortform mit RETURNSTATUS, `sp_unprepare` als Batch (der RPC-Teil des Entwurfs ist gestrichen) | wirkt an der Ursache: ein Handle je Anweisungsobjekt (t1), Freigabe geschlossener (t3); die Tabelle bleibt klein, solange der Client schließt; entspricht dem, was der Treiber sendet | ändert das Batch-Verhalten (Sitzungsrahmen erkennt eine Form vor dem Backend); wirkt nur, wenn d-migrate Anweisungen schließt oder wiederverwendet (nicht gemessen); Wirkung des RETURNSTATUS und Antwort eines echten Servers nicht gemessen; unbekanntes Handle bei `sp_unprepare` ist Setzung |
| E — Verdrängung des ältesten (oder am längsten ungenutzten) Handles | die Sitzung erreicht die Grenze nie | in [ADR-0017](0017-prepared-handles-grenzwert.md) als Option c verworfen: ein verdrängtes Handle ergibt bei `sp_execute` einen Fehler mit falscher Ursache; bei t1 hält ein Anweisungsobjekt sein Handle lange und wäre das erste Opfer; die Reaktion des Treibers auf den Fehler ist nicht gemessen |
| F — Überlauf ohne Handle: die Anweisung läuft, die Antwort trägt kein Handle | keine Fehlerantwort; der Treiber verträgt das (u1, u2) und bereitet neu vor; Speicher begrenzt | ändert Entscheidung 2 von [ADR-0017](0017-prepared-handles-grenzwert.md) (definierter Fehler); der Client merkt die Verschlechterung nicht; am Server nicht gebaut und nicht gemessen; andere Clients (.NET) nicht gemessen |
| G — nichts tun | keine Änderung | der Import bricht bei 1001 `MERGE` je Sitzung (a6); ob sich d-migrate so steuern lässt, dass es die Grenze nicht berührt (es nutzte zwei Sitzungen), ist nicht gemessen; die Zerlegung läge beim Nutzer des Clients, nicht beim Server |

**Beleglage der Optionen (ungleich).** Nur Option A ist gegen d-migrate gefahren (a4 des Spikes: Grenze 100 000, 20 000 Zeilen laufen durch; a6 mit 1000: Abbruch). D und F sind nur am Treiber gemessen (Reihen t und u), B, C und E gar nicht. D mit B löst a6 für d-migrate nur über B, solange nicht gemessen ist, dass d-migrate Anweisungen schließt oder wiederverwendet; wer den kürzesten belegten Weg will, wählt A.

## Konsequenzen

- Positiv (Setzung, soweit sie über den Treiber hinausgeht): Der Treiber nutzt mit RETURNSTATUS ein Handle je Anweisungsobjekt (t1) und sendet für geschlossene Anweisungen `sp_unprepare` (t3), in Batches zu elf Handles; bis zu zehn geschlossene Handles bleiben dabei unfreigegeben. Dass die Handle-Zahl dann den lebenden Anweisungen statt den Ausführungen folgt, setzt einen Server voraus, der freigibt; ein solcher Lauf liegt nicht vor (Folgepflicht 3), und ob der Treiber nach einer Erfolgsantwort auf den Batch anders vorgeht als nach t6, ist nicht gemessen. Die Grenze bleibt als Schutz ([TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilität)), einstellbar.
- Negativ: Der RPC-Katalog bleibt; der Sitzungsrahmen liest eine Batch-Form, bevor ein Backend gerufen wird; zwei neue Zusagen (Antwortform, Freigabe) gelten bisher nur am Treiber. Ob d-migrate seine Anweisungen schließt, entscheidet, ob die Wirkung für den `MERGE`-Pfad eintritt; bis dahin ist die Wirkung nur am Treiber belegt.
- **Folgepflicht (nach `Accepted`):** (1) Spezifikation, siehe Liste unten. (2) Quelltext und Tests: Antwortform, Freigabe (Batch), Zählung, einstellbare Grenze; Träger ist ein eigener Bau-Slice vor dem `MERGE`-Slice, den der Planner anlegt (Offen 1); der `MERGE`-Slice trägt die Kante als Start-Trigger. (3) Messung: ein Lauf von d-migrate mit `--on-conflict skip` über mehr als 1001 Zeilen je Sitzung gegen den Testserver, mit Zählung der Handles; Träger der `MERGE`-Slice.

### Liste der Spec-Änderungen (schreibt der Träger nach `Accepted`; dieser Slice ändert die Spezifikation nicht)

| Stelle | Änderung |
|---|---|
| `SPEC-030`, Antwort auf `sp_prepexec` | RETURNSTATUS (Wert 0) vor RETURNVALUE `@handle`, als Setzung gekennzeichnet; Beleg Messzeilen h6 und t1 |
| `SPEC-030`, Handle-Verwaltung | Satz „`sp_unprepare` und `sp_prepare` sind nicht im Katalog … und geben kein Handle frei“ ersetzen: der RPC `sp_unprepare` und `sp_prepare` bleiben außerhalb des Katalogs und geben kein Handle frei, die Batch-Form `EXEC sp_unprepare …` gibt frei; Zählsatz „gleichzeitig gehalten“; Zahl 1000 als Voreinstellung, Klammer „nicht konfigurierbar“ streichen |
| `SPEC-030`, RPC-Tabelle und Absatz darunter | keine Zeile `sp_unprepare`; Absatz „Prepare-Handles gehören zur Sitzung …“: den Hinweis ergänzen, dass die Batch-Form (nicht der RPC) frei gibt und dass der Referenzclient (.NET) ohne Pool kein `sp_unprepare` sendete; die Aufzählung „auch `sp_prepare`, `sp_unprepare` und `sp_cursor*`“ gilt für den RPC unverändert |
| `SPEC-030`, Parameter 0 von `sp_prepexec` | „bei Erstaufruf −1“ ergänzen: mssql-jdbc 13.4.0 sendet in Reihe h3 den Wert 0 (h6); beide Werte sind „kein Handle“. **Zweite Fundstelle** im Zählerpunkt der Handle-Verwaltung („−1 beim Erstaufruf, ein altes Handle bei einem erneuten Prepare“) gleich nachziehen |
| `SPEC-030`, Absatz „Ohne RETURNSTATUS und RETURNVALUE bleiben … Prepare-Handle … leer bzw. −1; die Tokens sind für diese Formen Pflicht“ | zur neuen Antwortform ins Verhältnis setzen: die Zusage verlangt die Tokens schon; der gebaute `sp_prepexec` sendet kein RETURNSTATUS (h6), die Änderung ist damit auch eine Angleichung an diese Zusage; ob `sp_execute` und `sp_executesql` ein RETURNSTATUS brauchen, ist nicht gemessen |
| Spezifikation, Nicht-Ziel 6 | keine Änderung: Nicht-Ziel 6 betrifft RPC-Formen, der RPC `sp_unprepare` bleibt Nicht-Ziel |
| `SPEC-059` | nur falls die Grenze ein öffentlicher Name wird (Offen 7) |
| Kompatibilitätsmatrix (`docs/plan/kompatibilitaetsmatrix.md`; hängt an `make matrix-zitate`) | Zeilen `sp_prepexec` („−1 beim Erstaufruf“), „Handle-Verwaltung … höchstens 1000 Handles“ und Lücke 13 („`sp_unprepare` ist abgelehnt“: gilt für den RPC, die Batch-Form ergänzen) nachziehen; die Zeile „ProcID außerhalb von 10, 12, 13 (darunter … `sp_unprepare`)“ bleibt; jedes Zitat `Klasse > Testfall` bleibt PASSED, sonst bricht `make matrix-zitate` |
| Quelltext-Kommentare und Tests mit der alten Lesart (Grep nach `sp_unprepare`, „nicht konfigurierbar“, „−1 beim Erstaufruf“ über `src/`) | `PreparedHandles.kt` („Voreinstellung, nicht konfigurierbar“), `RequestLoop.kt` (Kommentar zu den Handles: Batch-Freigabe ergänzen; „`sp_unprepare` … nicht im Katalog“ gilt für den RPC weiter); Test `PreparedHandlesDispatchTest` („−1 beim Erstaufruf“ ergänzen, der RPC-Test zu `sp_unprepare` bleibt gültig und braucht einen Nachbarn für den Batch). `RpcParameters.kt`, `RpcProcedureName.kt`, `RpcDispatchTest`, `RpcBinderTest`, `RpcCatalogFormsTest` und `RequestSequenceTest` betreffen den RPC und bleiben; je Stelle prüfen |
| `SPEC-013` | keine Änderung: der RPC `sp_unprepare` bleibt `TDS_UNSUPPORTED_RPC`; die Batch-Form ist kein RPC |
| `SPEC-057`, `SPEC-058` | `SPEC-058`: „bei erreichter Grenze von 1000“ wird „bei erreichter Grenze (Voreinstellung 1000)“; `SPEC-057` unverändert |
| `SPEC-034` und §3 | Zeile für den Eingang der Handle-Grenze (Name und Ort mit dem Slice zur öffentlichen Fläche abgestimmt, Bereich, Voreinstellung 1000, Testserver-Umgebungsvariable); Speicher je Sitzung: Grenze × Textgröße, nicht gemessen |
| `SPEC-001` | die Batch-Form der Freigabe wird im Sitzungsrahmen beantwortet, ohne Backend, der Ort entspricht dem von `RESETCONNECTION`, die Art nicht (dort ein Statusbit im Paketkopf, hier ein Text) |
| RPC-Katalog des Referenzclients (Abschnitt „RPC-Katalog des Referenzclients“) | mssql-jdbc 13.4.0: `sp_unprepare` als SQL-Batch, Form und Schwelle (Messzeile t5); Messgrenzen wie dort |
| `SPEC-036` | keine Änderung, solange die Freigabe im Sitzungsrahmen liegt und nicht im Backend |

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| JVM-Tests (`kotlin.test`) | `sp_prepexec` antwortet mit RETURNSTATUS vor `@handle` (Handvektor); `sp_unprepare` als Batch gibt frei, die Zählung sinkt, der RPC `sp_unprepare` bleibt `TDS_UNSUPPORTED_RPC` und gibt nichts frei, eine freigegebene Nummer wird nicht vergeben; unbekanntes Handle im Batch ohne Fehler und die übrigen Handles des Batches werden freigegeben; Negativfälle: gemischter Batch geht ans Backend, andere Schreibweise, nichtnumerisches und negatives Argument; die Anfrage, die über die eingestellte Grenze vergäbe, ergibt weiter `SPEC-058`; zwei Sitzungen zählen getrennt. Gegenprobe: ohne das Entfernen aus der Tabelle bleibt die Zählung stehen und der Test an der Grenze fällt | `make test` |
| Lauf mit mssql-jdbc 13.4.0 (kein Gate; der Träger legt Wiederholbarkeit fest) | ein Anweisungsobjekt, 200 Ausführungen: 1 `sp_prepexec` und 198 `sp_execute` (t1); 1100 neue Anweisungen mit Erstes-Mal-Prepare: kein Abbruch an der Grenze (Gegenstück zu t4) | der Slice des `MERGE`-Pfads |

## Offen

1. **Bau-Slice** (entschieden: ja). Antwortform, Freigabe und Eingang der Grenze baut ein eigener Slice vor dem `MERGE`-Slice; der Planner legt ihn an, nicht der Implementer dieser ADR.
2. **Verhalten von d-migrate** beim `MERGE`: Schließen oder Wiederverwenden der Anweisungsobjekte (nicht gemessen); entscheidet, ob Nr. 2 ausreicht oder Nr. 4 greift. Träger: der Beleg am fremden Client im `MERGE`-Slice.
3. **Echter SQL Server:** RETURNSTATUS auf `sp_prepexec`, Antwort auf `sp_unprepare` mit unbekanntem Handle, Grenze der Handles je Sitzung, Speicher je Handle. Träger: der Slice der Vergleichsmessungen am echten SQL Server (der Slice der Vergleichsmessungen am echten SQL Server; EULA-Entscheidung des Nutzers), `BEO-TDS/messung-am-echten-sql-server-fehlt`.
4. **Pooling** (`RESETCONNECTION`, `sp_unprepare` im Pool, TDS 8.0): nicht gemessen; `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`.
5. **Andere Treiberversionen und Clients** (.NET sendet möglicherweise `sp_unprepare` als RPC; nicht gemessen; ein Nachweis wäre der Anlass, den RPC in den Katalog aufzunehmen, Re-Evaluierung).
6. **Reaktion des Treibers** auf `sp_execute` mit unbekanntem Handle (Option E): nicht gemessen, weil der Testserver den Fehler in der Messreihe nicht auslöst.
7. **Öffentliche Fläche und Name des Eingangs der Grenze** (Nr. 4): ob ein neuer öffentlicher Name entsteht oder eine bestehende Konfiguration trägt, dazu Bereich und Umgebungsvariable des Testservers. Name und Ort werden in der Spezifikation mit dem Slice zur Verkleinerung der öffentlichen Fläche und `SPEC-059` abgestimmt (Entscheid des Nutzers); der Träger legt keinen Namen vorher an.
8. **[ADR-0005](0005-rpc-und-prepared-statements.md) (Katalog):** aufgelöst. Der Nutzer hat den RPC-Teil gestrichen; es gibt kein `Supersedes` und kein `Schärft` zu ADR-0005.

## Re-Evaluierungs-Trigger

Ein Lauf von d-migrate zeigt, dass es seine Anweisungen weder schließt noch wiederverwendet (dann Option A, C oder F neu); ein echter SQL Server sendet auf `sp_prepexec` kein RETURNSTATUS oder verhält sich bei `sp_unprepare` anders; eine andere Version von mssql-jdbc sendet `sp_unprepare` anders; eine Messung des Speichers je Handle widerlegt die Grenze; ein gepoolter Referenzclient erreicht die Grenze im Normalbetrieb.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-06 | Proposed – Entwurf mit Optionen A bis G, Messzeilen h, t, u und Empfehlung D mit B | [Messprotokoll](../../messungen/handle-grenze-optionen-2026-11-06.md), [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Folgepflicht 1 |
| 2026-11-06 | Accepted – der Nutzer antwortet „deine empfehlungen“: Option D mit B; der RPC-Teil von `sp_unprepare` entfällt (nur die Batch-Form), damit entfällt das Supersedes zu ADR-0005; Antwortform bleibt in dieser ADR; unbekanntes Handle bei `sp_unprepare` ist Erfolg (Setzung); Name und Ort des Eingangs der Grenze werden in der Spezifikation abgestimmt; der Bau-Slice vor dem `MERGE`-Slice wird vom Planner angelegt | Entscheid des Nutzers |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
