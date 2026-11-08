# ADR-0044: Eine Nachricht des Massenladens wird ganz oder gar nicht übernommen (Option A: Zusage des Stapel-Callbacks, der Einzelzeilen-Rückfall entfällt); Fehlercode des Callbacks bleibt Sammelcode 2627 als benannte Grenze

**Status:** Proposed (Entscheid des Nutzers steht aus)

**Datum:** 2026-11-08

**Autor:** Architect (Entwurf); Entscheidung: pt9912 (Nutzer), offen

**Bezug:** [TDS-FA-11](../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [ADR-0021](0021-oeffentlicher-integrations-einstieg.md), [ADR-0008](0008-dotnet-kompatibilitaetstests.md)

**Schärft (nach `Accepted`):** [SPEC-035](../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) (`onInsertBatch`, Rückfall auf `onInsert`, Zusage „keine Transaktion“), [SPEC-036](../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) (Form B, Fehler des Callbacks), die Zeile `TDS_SIM_KEY_VIOLATION` der Fehlertabelle und die Antwort auf einen Fehler des Callbacks in `SPEC-001`.

**Supersedes (teilweise), nur wörtlich benannt; die Datei des Vorgängers bleibt unverändert und `Accepted` ([AGENTS.md](../../../AGENTS.md) §3.5):** [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)
(1) Nr. 5 Festlegung (iii), **nur** der Satz „Fehler eines Callbacks beenden das Massenladen mit dem Fehler des Callbacks (b5); die Zeilen davor bleiben.“;
(2) Nr. 5 Tabelle Weitergaben, Zeile Z3, **nur** der Halbsatz „hat die Tabelle keinen, ruft sie `onInsert` je Zeile (Setzung, dann Teilbestand bei Fehler)“;
(3) Nr. 6, **nur** der Satz „Die Bibliothek nimmt nichts zurück: ein Fehler in der Mitte hinterlässt den Teilbestand beim Integrator (b5, d1), und `ROLLBACK TRAN` beantwortet sie mit dem Änderungszähler 0, ohne Wirkung (d1).“ **für das Massenladen** (die Aussage zu `ROLLBACK TRAN` und zu Anweisungen mit mehreren Callbacks außerhalb des Massenladens, etwa `MERGE`, bleibt) sowie der Schlusssatz „ohne ihn gilt es nicht“ im Absatz „Folge, die der Wortlaut nicht deckt“;
(4) Nr. 7, **nur** soweit sie einen Fehler des Callbacks einer Nachricht `0x07` als `TDS_BACKEND_ERROR` (50004) festlegt.
Es bleiben: alle übrigen Nummern und Sätze von ADR-0033, insbesondere Nr. 5 (i), (ii), (iv), die Zusage „keine Transaktion“ außerhalb des Massenladens, die Folgepflichten 1, 2, 4 bis 6. Folgepflicht 3 von ADR-0033 („Atomarität … Träger: der Slice für das Massenladen“) wird mit dieser ADR zur Zusage in Entscheidung 1.

**Regeln:** Baseline-Regelwerk `modul-04-adrs.md` §Ziel-Form: ADR (MADR).

---

## Kontext

**Zielbild.** Die Spezifikation ist Zielbild, der echte SQL Server die Referenz (Nutzergrundsatz 2026-11-06, [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität)); eine Abweichung ist Schuld, keine Gleichberechtigung (`AGENTS.md` §3.8: eine Aussage trägt höchstens so weit wie ihr Beleg).

**Konflikt.** [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 5 (iii) und Nr. 6 halten fest: ein Fehler in der Mitte hinterlässt den Teilbestand, „die Zeilen davor bleiben“; atomar ist nur, was der Integrator im Stapel-Callback atomar macht. Die Messung am echten Server sagt für den gemessenen Fall das Gegenteil.

**Messung** ([Zeile M-12](../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07), Referenzclient `Microsoft.Data.SqlClient` mit `SqlBulkCopy`, Einzelmessung je Instanz SQL Server 2022 und 2025, nicht mssql-jdbc von d-migrate): Schlüssel 2 vorhanden, Quelle mit den Schlüsseln 1, 2, 3 in einem Paket. Die Nachricht `0x07` endet mit `ERROR` 2627 (Class 14, State 1), `INFO` (3621) und `DONE` (Status `ERROR`, Befehl `0x00f0`, ohne Zähler), danach ein `DONE` mit `ATTN`. Die Tabelle enthält danach nur die vorhandene Zeile: **die Zeile 1 vor dem Fehler bleibt nicht**. Die Verbindung bleibt offen. M-11: Fehler gegen Typ, Länge und NULL erkennt der **Client** vor dem Senden (ATTENTION, keine Nachricht `0x07`); ein serverseitiger Verstoß dieser Art wurde nicht hergestellt.

**Grenze des Belegs (kein Teil der Entscheidung, sondern ihres Gewichts).** Gemessen ist **ein** Fall: Schlüsselverstoß, drei Zeilen, ein Paket, Verstoß in der Mitte. Nicht gemessen: Verstoß an erster und letzter Zeile, Verstoß in der zweiten von mehreren Nachrichten (`BatchSize`), Nachricht über mehrere Pakete, mehrere Verstöße in einer Quelle, andere serverseitige Ursachen (Typ, NULL, Länge). Diese Messungen sind der erste Liefer-Punkt des Planungsauftrags zu dieser ADR und stehen aus. Dass eine Nachricht eine Anweisung ist, ist außerdem eine Festlegung des Nutzers (2026-11-06) in ADR-0033; die Messung stützt sie in dem einen Fall, ohne sie zu beweisen. Dass SQL Server jede Anweisung atomar behandelt und der gemessene Fall deshalb kein Sonderfall ist, ist eine **Annahme**, kein Beleg.

**Ist im Bau** (Bau des Massenladens, Stand `main`): Die Bibliothek ruft `onInsertBatch` einmal je Nachricht, hat die Tabelle ihn nicht, `onInsert` je Zeile. Der Bestand des Testservers (`TableStore`, Modul `testserver`, nur öffentliche Fläche) übernimmt den Stapel atomar und erfüllt das Zielbild bereits. **Jede** Ausnahme des Callbacks meldet die Bibliothek als 2627 mit dem Text eines Schlüsselverstoßes (Setzung, Verifikation V-4); der Bestand des Testservers wirft eine gewöhnliche Ausnahme, kein Signal einer Ursache: die Bibliothek unterscheidet Schlüsselverstoß, NULL, Typ, volle Zellgrenze und Fehler im Code des Integrators nicht, und ohne neuen öffentlichen Namen kann sie es nicht.

## Entscheidung

**Vorschlag (Empfehlung des Architect; entschieden wird durch den Nutzer):**

1. **Eine Nachricht `0x07` ist eine Anweisung mit Übernahme ganz oder gar nicht (Option A).** Der Stapel-Callback `onInsertBatch` hat die Zusage: er gibt bei Erfolg die Zahl der übernommenen Zeilen zurück (der Änderungszähler, unverändert) und **wirft bei Fehler, ohne eine Zeile der Nachricht übernommen zu haben**. Die Bibliothek macht nichts zurück (keine Transaktion, keine Kompensation); die Zusage ist eine Pflicht des Integrators, wie bisher die Atomarität „im Stapel-Callback“ eine war, nur jetzt Teil des Vertrags statt Option. Was die Bibliothek sicherstellt, ist, dass sie selbst keinen Teilbestand erzeugt: **der Rückfall auf `onInsert` je Zeile entfällt**. Eine Tabelle ohne `onInsertBatch` lehnt `INSERT BULK` mit `TDS_UNSUPPORTED_SQL` ab (der Pfad „Tabelle ohne Callback“ besteht schon). Meldet der Callback weniger Zeilen, als die Nachricht trägt, bleibt seine Zahl der Änderungszähler (nicht gemessen; keine neue Aussage).
2. **Das gilt als Setzung über den gemessenen Fall hinaus.** Die Spezifikation führt es als Zielbild mit der Grenze „gemessen: Schlüsselverstoß, drei Zeilen, ein Paket (M-12)“; die Messungen des ersten Liefer-Punkts ergänzen oder korrigieren sie. Zeigt eine davon Teilbestand (etwa bei einer Nachricht über mehrere Pakete oder in der zweiten von mehreren Nachrichten), gilt Re-Evaluierungs-Trigger 1; die Zusage bleibt bis dahin das Zielbild.
3. **Fehlercode des Callbacks: der Sammelcode 2627 bleibt als benannte Grenze.** Jeder Fehler des Stapel-Callbacks bleibt `TDS_SIM_KEY_VIOLATION` (2627, Class 14, `INFO` 3621, `DONE` `0x00f0`). Gemessen ist 2627 **nur** für den Schlüsselverstoß; für Typ, Länge und NULL liegt keine serverseitige Messung vor (M-11: der Client weist sie ab), für Zellgrenze und Fehler im Code des Integrators gibt es am echten Server keine Entsprechung, die man messen könnte. Die Spezifikation sagt das bei der Zeile `TDS_SIM_KEY_VIOLATION` ausdrücklich („Nummer für andere Ursachen als den Schlüsselverstoß: Setzung des Sammelcodes“). Eine ursachenscharfe Zuordnung braucht ein Signal vom Integrator und damit einen **neuen öffentlichen Namen** (Befund nach ADR-0033 Nr. 4, Entscheidung des Nutzers); sie ist die Option K-c unten und hat ihren Trigger (Re-Evaluierungs-Trigger 3), keinen Folge-Slice.
4. **Nachzug.** Nach `Accepted` zieht der Planner den Bau des Massenladens nach (Wortlaut in `SPEC-035` und `SPEC-036`, Entfernen des Einzelzeilen-Rückfalls samt Tests, Test „Tabelle nur mit `onInsert` lehnt `INSERT BULK` ab“). Diese ADR ändert `spec/` und Code nicht.

## Verglichene Alternativen

**Frage 1: Atomarität der Nachricht**

| Option | Pro | Contra |
|---|---|---|
| **A — Zusage des Stapel-Callbacks, Einzelzeilen-Rückfall entfällt (empfohlen)** | Die Bibliothek kann den Zustand, den der echte Server im gemessenen Fall nie erzeugt, nicht mehr erzeugen: das Zielbild gilt konstruktiv für ihren Teil. Der Bestand des Testservers erfüllt es schon. Kleinster Eingriff: Code wird entfernt, kein neuer Name, keine Transaktion (der Ausschluss von Transaktionssemantik in den Anforderungen zum Simulations-Backend bleibt unberührt). | Ein Integrator mit nur `onInsert` bekommt `TDS_UNSUPPORTED_SQL` für `INSERT BULK` und muss einen Stapel-Callback schreiben (einfach: Zeilen sammeln, atomar übernehmen). Die Bibliothek kann die Zusage nicht erzwingen; ein Integrator, der sie bricht, erzeugt Teilbestand. Gemessen ist ein Fall (Grenze oben). |
| B — Teilbestand bleibt wie ADR-0033 Nr. 5 (iii) und Nr. 6; Abweichung als Setzung | Keine Änderung; der Rückfall auf `onInsert` bleibt. Kein Nachzug. | Verhalten, das der echte Server im gemessenen Fall nie zeigt, bleibt dauerhaft Teil der Spezifikation; widerspricht dem Zielbild (Schuld ohne Frist). Ein Test eines Integrators gegen den Testserver könnte ein Verhalten sehen, das gegen den echten Server nicht auftritt (der Zweck der Kompatibilitätsanforderung ist das Gegenteil). |
| C — Zusage wie A, Rückfall auf `onInsert` bleibt als benannte Abweichung | Kein Bruch für Integratoren mit nur `onInsert`; Zusage und Grenze stehen im Text. | Die Abweichung bleibt im Bau, mit Pflege (Test, Text) und ohne Trigger zur Auflösung; der Rückfall wäre die einzige Stelle, an der die Bibliothek selbst Teilbestand erzeugt. Kein Gewinn außer Bestandsschutz einer Fläche, die seit 2026-11-07 existiert und vor `1.0` nur durch Änderung der Spezifikation fest ist. |
| D — Die Bibliothek erzwingt die Atomarität selbst (Rücknahme der Zeilen über `onDelete` oder Transaktionsrahmen) | Atomar auch ohne Mitwirkung des Integrators. | Braucht einen Schlüssel und `onDelete` je Tabelle, eine Transaktionssemantik mit Rücknahme (in den Anforderungen zum Simulations-Backend ausdrücklich ausgeschlossen) und Aufwand weit über dem Nutzen; nicht tragfähig bei Callbacks mit Nebenwirkungen. Verworfen. |

**Frage 2: Fehlercode des Callbacks** (Entscheidung 3)

| Option | Pro | Contra |
|---|---|---|
| **K-a — Sammelcode 2627 bleibt, Grenze benannt (empfohlen)** | Deckt den einzigen gemessenen Fall (Schlüsselverstoß, der Fall des Testservers). Kein neuer Name, kein Folge-Slice. | Für Ursachen außer dem Schlüsselverstoß ist die Nummer ungenau (Setzung). Die Meldung sagt „Violation of PRIMARY KEY“ auch bei einem Fehler im Code des Integrators. Wirkung auf d-migrate: nicht gemessen. |
| K-b — Fehler des Callbacks als `TDS_BACKEND_ERROR` (50004), wie ADR-0033 Nr. 7 | Ehrlich für Fehler im Code des Integrators; ohne neuen Namen. | Der Schlüsselverstoß des Testservers, der gemessene Fall, liefe dann als 50004 statt 2627 und widerspräche der Referenz. Verworfen. |
| K-c — neuer öffentlicher Name für den Schlüsselverstoß (z. B. ein Ausnahme-Typ); sonst 50004 | Ursachenscharf: 2627 für den Schlüsselverstoß, sonst 50004; Zielbild für den gemessenen Fall und ehrlich für den Rest. | Neue öffentliche Fläche (Befund nach ADR-0033 Nr. 4, Nutzerentscheid, Änderung von `SPEC-059`), dazu Nachzug der Nummern für Typ und NULL, die am Server nicht gemessen sind. Aufwand ohne belegten Bedarf. Als Trigger vorgemerkt, nicht gebaut. |

## Konsequenzen

- Positiv: Die Spezifikation trägt für das Massenladen einen Satz weniger, der dem echten Server in dem einen gemessenen Fall widerspricht; die Bibliothek erzeugt keinen Teilbestand mehr; es entsteht keine neue öffentliche Fläche.
- Negativ: Die Zusage der Atomarität gilt als Setzung über einen Fall hinaus. Integratoren mit nur `onInsert` müssen für `INSERT BULK` einen Stapel-Callback schreiben (Bruch einer Fläche, die seit einem Tag besteht; vor `1.0` nur durch Änderung der Spezifikation fest, `SPEC-059`). Der Sammelcode bleibt ungenau.
- **Akzeptiertes Negativ:** Die Antworten auf Verstöße an erster und letzter Zeile, in der zweiten Nachricht, über mehrere Pakete und mit mehreren Verstößen sind nicht gemessen. Ein Fehlschlag dieser Annahme ist harmlos für den Bau (das Entfernen eines Rückfalls lässt sich zurücknehmen) und wird vom Trigger gefangen; ein eigener Träger entsteht nicht außer dem ersten Liefer-Punkt des Slice, der ohnehin geplant ist.
- Folgepflicht Spezifikation: Wortlaut in `SPEC-035` (Absatz `onInsertBatch`, Rückfall, Zusage), `SPEC-036` (Form B, Fehler des Callbacks, Satz zu „Teilbestand“), `SPEC-001` (Antwort bei Fehler) und der Zeile `TDS_SIM_KEY_VIOLATION`; Träger: Nachzug des Baus des Massenladens durch den Planner nach `Accepted` (Liefer-Punkt 3 des Planungsauftrags zu dieser ADR). Der Verweis auf ADR-0033 Nr. 5 (iii) und Nr. 6 in `spec/` und im Messprotokoll (Zeile „widerspricht“) folgt dem Nachzug.
- Folgepflicht Messung: Liefer-Punkt 1 des Planungsauftrags (erste, mittlere, letzte Zeile; zweite von drei Nachrichten; mehrere Verstöße) bleibt; sein Ergebnis kann diese ADR nicht ändern, sondern löst Trigger 1 aus.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| Tests des Moduls `testserver` (Einbettungstest) und der Bibliothek | Nachricht mit Verstoß in der Mitte lässt den Bestand unverändert; Tabelle nur mit `onInsert` lehnt `INSERT BULK` ab (nach dem Nachzug) | `make test` |

## Re-Evaluierungs-Trigger

1. Eine Messung des ersten Liefer-Punkts (oder jede spätere am echten Server) zeigt Teilbestand nach einem Fehler in einer Nachricht: Folge-ADR zur Zusage (Zuschnitt: Zusage je Fall).
2. Ein Lauf von d-migrate (mssql-jdbc) sendet eine Nachrichtenfolge, bei der die Annahme „eine Nachricht ist eine Anweisung“ nicht trägt.
3. Ein Integrator braucht den Schlüsselverstoß von anderen Fehlern zu unterscheiden, oder eine serverseitige Messung nennt für Typ, Länge oder NULL eine andere Nummer: Folge-ADR mit Option K-c (neuer öffentlicher Name).

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-07 | Zielbild-Konflikt aus den Messungen M-11, M-12; Nutzerentscheid: Folge-ADR schreiben lassen | [Register-Eintrag](../planning/observations/BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand/state.md) |
| 2026-11-08 | Proposed (Option A empfohlen, Entscheid offen) | Planungsauftrag zur Atomarität des Massenladens |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**. Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit `Supersedes ADR-0044`.
