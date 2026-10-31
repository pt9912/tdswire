# ADR-0014: Fehlerfolge der RPC-Antwort: DONEPROC im Fehlerfall, ungültig kodierte RPC-Nachricht, statement- gegen batch-abbrechende Fehler

**Status:** Proposed

**Datum:** 2026-10-31

**Autor:** pt9912

**Bezug:** [ADR-0005](0005-rpc-und-prepared-statements.md), [ADR-0006](0006-backend-vertrag-und-streaming.md), [ADR-0007](0007-fehler-und-attention.md), [TDS-FA-04](../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-06](../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), [TDS-FA-10](../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte)

**Schärft:** [SPEC-030](../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten), [SPEC-013](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [SPEC-011](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [SPEC-031](../../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität), [TDS-FA-04.a](../../../spec/spezifikation.md#tds-fa-04a--rpc-und-parameter), [TDS-FA-06.a](../../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung). Verhältnis zu den angenommenen ADRs: [ADR-0005](0005-rpc-und-prepared-statements.md) (Antwortform der RPC-Antwort im Fehlerfall) und [ADR-0007](0007-fehler-und-attention.md) (Fehlerfolge einer ungültig kodierten RPC-Nachricht) werden **geschärft**, nicht ersetzt: beide sagen dazu nichts, kein Satz von ihnen wird überstimmt (siehe „Verhältnis zu angenommenen ADRs“). Kein `Supersedes`.

---

## Kontext

Drei Fragen betreffen dieselbe Sache: wie eine **Fehlerantwort auf eine RPC-Nachricht** endet. Alle drei haben heute keinen Träger; der Nutzer hat am 2026-10-31 die Empfehlung übernommen.

**Was gemessen ist.** [ADR-0007](0007-fehler-und-attention.md) misst am echten SQL Server (Messreihe R) Fehlerantworten auf SQL_BATCH: ERROR und `DONE[ERR]` (R01, R05 bis R07), bei statement-abbrechenden Fehlern `DONE[MORE|ERR]` mit weiterlaufenden Statements (R04). **Eine einzige** Zeile berührt die Form eines Fehlerabschlusses mit Prozedurbezug: R02, `SELEC 1` als Batch gesendet, der Server las den Batch als Prozedurname und antwortete ERROR 2812 (Class 16, State 62) und `DONEPROC[ERR]`. R02 ist **kein** Fehler in einer RPC-Nachricht: der Auslöser war ein SQL_BATCH, der Server hat ihn als Prozedurnamen ausgeführt. Sie zeigt daher nur, dass der Server den Abschluss nach der Art der ausgeführten Sache wählt (hier Prozedur) und nicht nach dem Pakettyp. Ein Fehler in `sp_executesql`, eine unbekannte ProcID oder eine ungültig kodierte RPC-Nachricht ist am echten Server **nicht** gemessen.

**Was gebaut ist.** [ADR-0005](0005-rpc-und-prepared-statements.md) belegt am Testserver (Messreihe T, nur Erfolgsfall): ein DONE (Batch) bzw. DONEPROC (RPC) genügt, damit der Client ohne Fehler abschließt. Der Server schreibt im Erfolgsfall DONEINPROC je Abschluss und DONEPROC zum Schluss ([SPEC-030](../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten)). Im Fehlerfall schreibt der Quelltext immer ein `DONE` mit Fehlerstatus (`failedDone()` in `ResponseRun.kt`), auch bei einer RPC-Antwort; `ResponseRpcTest` pinnt DONE mit Verweis auf `SPEC-031`; dass die Setzung gegen R02 steht, sagt die KDoc des `ResponseSequencer` (Closure des Slice für mehrteilige Antworten und Ausgabewerte, Spec-Lücke 4: „kein Träger im Repo“).

**Ungültig kodierte RPC-Nachricht.** [ADR-0007](0007-fehler-und-attention.md) sagt zur Fehlerfolge nichts. Die Spezifikation setzt (Nutzerentscheidung vom 2026-10-31, nicht gemessen): ein Struktur- oder Kodierfehler (Format, Länge, Typkennung außerhalb der Typliste, TYPE_INFO, ALL_HEADERS) ist `TDS_PROTOCOL_ERROR` (`SPEC-011`, Class 20, nach ERROR schließt der Server); eine gültig kodierte, aber nicht unterstützte Form (ProcID außerhalb des Katalogs, PLP-Typ, Wert außerhalb des Typbereichs) ist `TDS_UNSUPPORTED_RPC` (`SPEC-013`, Class 16, Verbindung offen). Der Review des Slice für `sp_executesql` und Parameter fand die frühere Zwischenfassung widersprüchlich (RPC-Strukturfehler offen, Batch-Strukturfehler schließend); die Spezifikation wurde danach auf die Trennung nach Ursache gebracht, die ADR fehlt.

**Statement- gegen batch-abbrechende Fehler.** [ADR-0006](0006-backend-vertrag-und-streaming.md) führt als offen: das `Failure`-Ereignis trägt keine Klasse, der Sequenzierer schreibt deshalb immer die Variante ohne `MORE` (SPEC „Festlegungen des Response Sequencers“), während der echte Server bei statement-abbrechenden Fehlern `DONE[MORE|ERR]` sendet und die folgenden Statements ausführt (R04); bei batch-abbrechenden Fehlern endet die Antwort mit `DONE[ERR]` ohne `MORE` und ohne die folgenden Statements (R05 bis R07).

## Entscheidung

Wir wählen **Option A**: Eine Fehlerantwort auf eine RPC-Nachricht endet mit **DONEPROC** (Status Fehler), eine ungültig kodierte RPC-Nachricht folgt derselben Fehlerklassen-Regel wie ein ungültiger SQL_BATCH, und die Unterscheidung statement- gegen batch-abbrechend wird hier **nicht** getroffen.

1. **Abschlusstoken (Setzung, nicht am echten Server gemessen).** Die Antwort auf eine RPC-Nachricht (Pakettyp `0x03`), die mit einem Fehler endet, besteht aus ERROR und einem **DONEPROC** mit Fehlerstatus (`ERR`, ohne `MORE`, ohne `COUNT`); die Antwort auf einen SQL_BATCH (Pakettyp `0x01`) bleibt ERROR und DONE mit Fehlerstatus. Die Form folgt hier dem **Pakettyp der Anfrage**, nicht dem Erfolg des Parsens. Das ist eine **Setzung ohne Messung**: R02 zeigt den Abschluss nach Art der ausgeführten Sache (ein als Prozedurname gelesener Batch endete mit `DONEPROC[ERR]`), nicht nach Pakettyp; die Regel „Batch immer DONE“ ist nur für Batch-Fälle gemessen, die nichts als Prozedur ausführten (R01, R05 bis R07), und widerspricht R02 für den Sonderfall des Batch als Prozedurname. Die Form gilt für Ablehnungen vor dem Backend (`SPEC-013`), für ein `Failure` im Strom, für `TDS_INTERNAL_ERROR` und `TDS_SHUTDOWN` (`SPEC-048`, `SPEC-049`) und für `TDS_PROTOCOL_ERROR` (3.), soweit überhaupt ERROR und DONE geschrieben werden. Ein vor dem Fehler abgeschlossenes Ergebnis oder ein abgeschlossener Zähler behält sein DONEINPROC wie im Erfolgsfall.
2. **Beleg und seine Grenze.** Die Wahl stützt sich auf R02 (Server schließt eine Prozedur-Ausführung mit `DONEPROC[ERR]` ab) und auf die Folgerung, dass der Abschluss einer Prozedur-Antwort im Erfolgsfall DONEPROC ist ([ADR-0005](0005-rpc-und-prepared-statements.md)). Dass der echte Server **dasselbe für den Fehler in `sp_executesql`** sendet, ist nicht gemessen; ebenso nicht, ob der Referenzclient DONE und DONEPROC mit Fehlerstatus in der Wirkung unterscheidet. Beides ist Setzung.
3. **Ungültig kodierte RPC-Nachricht (Bestätigung des Spec-Stands, Setzung).** Ein Struktur- oder Kodierfehler einer RPC-Nachricht ergibt die Kategorie des Parsers: `TDS_PROTOCOL_ERROR` (Class 20) oder, wo der Parser sie setzt, `TDS_UNSUPPORTED_TYPE` (Wert 50003; ebenfalls schließend, Class 20, `SPEC-030`: „`SPEC-011` beziehungsweise die Kategorie des Parsers“), ERROR und DONEPROC mit Fehlerstatus, danach schließt der Server; **dieselbe Regel wie beim SQL_BATCH**, die Wahl hängt an der Ursache, nicht am Pakettyp. Eine gültig kodierte, nicht unterstützte Form bleibt `TDS_UNSUPPORTED_RPC` (Class 16, Verbindung offen, Backend nicht gerufen). Die Trennlinie ist die der Spezifikation; diese ADR ändert sie nicht, sie macht sie zur Entscheidung.
4. **Statement- gegen batch-abbrechend: nicht Gegenstand dieser ADR.** Für die RPC-Antwort gilt unverändert die heutige Setzung des Sequenzierers: jedes `Failure` beendet die Antwort, ohne `MORE`, folgende Statements laufen nicht. Die Entscheidung, ob `Failure` eine Klasse bekommt, ändert den Backend-Vertrag ([ADR-0006](0006-backend-vertrag-und-streaming.md), `QueryEvent`) und ist von der Wahl des Abschlusstokens unabhängig: DONEPROC ist in beiden Klassen der Abschluss, nur Statusbit und Weiterlaufen unterscheiden sich. Gründe, sie hier nicht zu treffen: (i) gemessen sind nur Batch-Fälle (R04 bis R07), kein RPC-Fall; (ii) keiner der Verbraucher (Simulations-Backend, Test-Backend) ist als Erzeuger statement-abbrechender Fehler benannt; (iii) die Entscheidung bräuchte eine Messung und eine Vertragsänderung, die nur bei einem Verbraucher lohnt. Das **akzeptierte Negativ**: bei einem statement-abbrechenden Fehler weicht Statusbit und Folge von R04 ab, solange kein Backend ihn liefern kann; der Punkt bleibt in [ADR-0006](0006-backend-vertrag-und-streaming.md) „Offen“ und im Register (`BEO-TDS/offene-punkte-ohne-traeger`) geführt, mit dem Trigger unten.

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| **A — DONEPROC mit Fehlerstatus bei RPC-Antworten; ungültig kodierte RPC wie ungültiger Batch (gewählt)** | die einzige Messzeile mit Prozedurbezug (R02) endet mit DONEPROC; Erfolgs- und Fehlerabschluss einer RPC-Antwort haben dieselbe Art; eine Regel nach Ursache für beide Pakettypen (kein Sonderfall nach Pakettyp) | R02 ist kein RPC-Fehler (siehe Kontext); Quelltext (`failedDone()`) und der Test `ResponseRpcTest` ändern sich; am echten Server und am Client nicht gemessen |
| B — DONE belassen (heutiger Bau) | kein Code, kein Test ändert sich; für den Batch gemessen (R01, R05 bis R07) | widerspricht der einzigen Messzeile mit Prozedurbezug; Erfolg endet mit DONEPROC, Fehler mit DONE: zwei Arten für dieselbe Antwort; ein Client, der DONEPROC erwartet, könnte anders reagieren (nicht gemessen) |
| C — erst im Slice für den Vergleichslauf D messen, dann entscheiden | ersetzt Setzung durch Messung | blockiert die Spec-Lücke (Closure des Slice für mehrteilige Antworten und Ausgabewerte, Lücke 4) bis zu einem Lauf, der noch nicht gebaut ist; keine Rückfallregel in der Zwischenzeit |
| D — Strukturfehler einer RPC-Nachricht offen halten (`TDS_UNSUPPORTED_RPC`, Class 16) | Strom bleibt synchron, weil die Nachricht vollständig gerahmt ist (Review des Slice für `sp_executesql` und Parameter); ein defekter Client verliert die Verbindung nicht | zwei Regeln für denselben Defekt je nach Pakettyp (Review des Slice für `sp_executesql` und Parameter); Spec-Stand (`SPEC-011`) ist bereits anders; Fehlerzustand eines Parsers, der die Nachricht nicht verstanden hat, ist kein Beleg für sichere Weiterverwendung der Verbindung |

## Konsequenzen

- Positiv: Spec-Lücke 4 der Closure des Slice für mehrteilige Antworten und Ausgabewerte und die Abgrenzung `SPEC-011`/`SPEC-013` (Review des Slice für `sp_executesql` und Parameter) haben eine Entscheidung statt zweier Setzungen; die Fehlerfolge der RPC-Antwort ist ein Satz.
- Negativ: Eine Setzung ersetzt eine Setzung; kein Beleg ist stärker als R02. Der Quelltext weicht heute ab und ändert sich.
- **Verhältnis zu angenommenen ADRs.** [ADR-0005](0005-rpc-und-prepared-statements.md) nennt DONEPROC für die RPC-Antwort im Erfolgsfall und schweigt zum Fehlerfall; [ADR-0007](0007-fehler-und-attention.md) misst R02 und schweigt zur Fehlerfolge einer ungültig kodierten RPC-Nachricht. Diese ADR füllt beide Lücken und widerspricht keinem Satz von ihnen; ihre Texte bleiben unverändert ([AGENTS.md](../../../AGENTS.md) §3.5).
- **Folgepflicht (nach `Accepted`):** (1) Spezifikation nachführen: `SPEC-030` (Abschlusstoken im Fehlerfall), `SPEC-013` und `SPEC-011` (Zeile „ERROR und DONE“ nennt für RPC DONEPROC), `SPEC-031`. Träger: der Slice, den der Planner dafür in der Welle für Fehler und Abbruch anlegt. (2) Quelltext: `failedDone()` wählt den Abschluss nach dem Pakettyp der Anfrage; `ResponseRpcTest` ändert den Pin von DONE auf DONEPROC, ein Test deckt die Ablehnung vor dem Backend und das `Failure` im Strom. Träger: derselbe Slice. (3) Optional und kein Muss: eine Zeile „Fehler in einer RPC-Antwort“ in Slice für den Vergleichslauf D, die R02 für RPC-Nachrichten misst; ob sie aufgenommen wird, entscheidet der Planner, die ADR verlangt sie nicht.
- **Akzeptiertes Negativ (einmalig, mit Grund):** Der Blindfleck „statement-abbrechender Fehler im RPC“ bleibt offen, weil es keinen benannten Verbraucher und keine Messung gibt (Entscheidung 4); er erscheint in keinem Slice, nur im Register und im Trigger dieser ADR.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| JVM-Tests (`kotlin.test`) | Die Fehlerantwort auf eine RPC-Nachricht endet mit ERROR und DONEPROC (Fehlerstatus), die auf einen SQL_BATCH mit ERROR und DONE; eine ungültig kodierte RPC-Nachricht ergibt `TDS_PROTOCOL_ERROR` und schließt, eine nicht unterstützte Form `TDS_UNSUPPORTED_RPC` bei offener Verbindung. Noch kein bestandenes Gate. | — (Gate noch einzurichten; Träger: Slice in der Welle für Fehler und Abbruch) |

## Offen

- Für den Nutzer: (a) die Setzung DONEPROC trotz fehlender Messung am Fehler einer RPC-Nachricht (Option A) oder erst Messung (Option C); (b) Entscheidung 4 als Verzicht bis zum Trigger oder jetzt ein Slice für die Failure-Klasse.
- Nicht gemessen: Antwort des echten SQL Servers auf einen Fehler in `sp_executesql`, eine unbekannte ProcID, eine ungültig kodierte RPC-Nachricht; die Reaktion des Referenzclients auf DONE gegen DONEPROC mit Fehlerstatus.
- Fehlerfolge statement-abbrechender Fehler im Strom (siehe Entscheidung 4).

## Re-Evaluierungs-Trigger

Eine Messung (im Slice für den Vergleichslauf D oder ein Lauf gegen den echten SQL Server), nach der der Abschluss eines Fehlers in einer RPC-Antwort ein DONE ist oder der Referenzclient beide Formen verschieden behandelt; ein Backend oder Treiber, das statement-abbrechende Fehler liefert (Entscheidung 4); ein Client, der eine ungültig kodierte RPC-Nachricht nach dem Schließen als Fehler der Anwendung statt der Verbindung zeigt.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-10-31 | Proposed – Folge-ADR (Entscheidung des Nutzers 2026-10-31, Empfehlung übernommen); Setzung, nicht gemessen; Status bleibt Proposed bis Review und Annahme durch den Nutzer | [ADR-0005](0005-rpc-und-prepared-statements.md), [ADR-0007](0007-fehler-und-attention.md) |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
