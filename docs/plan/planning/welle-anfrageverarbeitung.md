# Welle welle-anfrageverarbeitung: Verarbeitung von SQL_BATCH und RPC nach dem Login (Dispatch, Parameter, Prepared-Statements, mehrteilige Antworten)

**Lifecycle:** Diese Datei entsteht bei der **Eröffnung** der Welle und liegt
flach unter `docs/plan/planning/`; bei Closure wandert sie per `git mv` nach
`done/` (neben ihre `welle-<Kennung>-results.md`). Der Zustand ist die
Verzeichnis-Position — kein Status-Feld. **Geplante Wellen bekommen noch keine
Datei:** Sie stehen in der Roadmap unter *Nächste Wellen* und nirgends sonst —
zwei Positionen, nicht drei.

**Zielmeilenstein:** kein Meilenstein-Bezug.

**Verantwortlich:** pt9912. **Datum:** 2026-10-30.

---

## 1. Welle-Ziel

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wann Arbeit eine Welle braucht.

Eine Sitzung im Zustand `READY` beantwortet ein SQL_BATCH, einen parametrisierten RPC (`sp_executesql`, `sp_prepexec`, `sp_execute`, RPC per Prozedurname) und mehrteilige Antworten mit Rückgabestatus und OUTPUT-Parametern, indem sie die Anfrage an den Backend-Port gibt und die Ereignisse als geordnete Antwort zurückführt, ohne SQL-Text und Parameterwerte zu verketten; unbekannte RPC-Formen und ungültige Parameterkodierung ergeben einen definierten Fehler, ohne die Verbindung zu beenden ([TDS-FA-03](../../../spec/lastenheft.md#tds-fa-03--einfache-sql-batches), [TDS-FA-04](../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-10](../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte)). Die Aussage steht an einer Rohbytes-Folge auf **einer** Sitzung gegen ein Test-Backend und gilt für diesen Pfad und diesen Rohbytes-Client, nicht für die Annahme durch den Referenzclient `Microsoft.Data.SqlClient` und nicht für einen echten SQL Server (siehe §6). Hinzu kommt der Codec-Teil des Slice `slice-codec-datentypen-erweiterung` (PLP, NBCROW, weitere Typen; [TDS-FA-05](../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen)), der Bytes im Speicher kodiert und dekodiert und keine Sitzung braucht.

## 2. Trigger (Welle startet)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Regeln — ein Trigger ist **beobachtbar** dann, wenn ein *anderer*
Mensch ohne Rückfrage sagen kann, ob er eingetreten ist; ein Datum darf erwähnt
werden, aber nie Trigger sein. Und der **Start**-Trigger ist **kein Ergebnis
dieser Welle**: Steht er in der Slice-Liste unten, ist er falsch platziert.

- `make gates` ist auf `main` grün.
- Der Slice `slice-handshake-login` der Welle [welle-handshake-login](done/welle-handshake-login/welle-handshake-login.md) liegt in `done/`: Die Sitzung erreicht den Zustand `READY`.
- Der Slice `slice-codec-rpc-parser` liegt in `done/`: Der Parser für den RPC-Katalog von [ADR-0005](../adr/0005-rpc-und-prepared-statements.md) steht auf `main`.
- [ADR-0004](../adr/0004-session-zustandsautomat.md), [ADR-0005](../adr/0005-rpc-und-prepared-statements.md), [ADR-0006](../adr/0006-backend-vertrag-und-streaming.md) und [ADR-0007](../adr/0007-fehler-und-attention.md) stehen im ADR-Index auf `Accepted` (erfüllt: alle vier tragen den Status). Ihre offenen Punkte schließen sie nur per Folge-ADR; die Träger stehen in den Closure-Notizen von `slice-adr-session-zustand`, `slice-adr-backend-streaming` und `slice-adr-fehler-attention` und in den Slices der Wellen [welle-backend-streaming](welle-backend-streaming.md) und [welle-fehler-abbruch](welle-fehler-abbruch.md).

Weitere Voraussetzungen gelten je Slice und stehen in dessen §4 (Backend-Port und Sequenzierer-Kern aus der Welle `welle-backend-streaming`, Fehler-Mapping aus der Welle `welle-fehler-abbruch`, Typkatalog, Grenzwert der Handles): Sie verzögern einen Slice, nicht den Start der Welle. Der Start-Trigger von `slice-codec-datentypen-erweiterung` steht in dessen §4 (`slice-codec-tds-paket` in `done/`, `SPEC-021` oder der Referenzabgleich nennt einen Gegenstand als gesendet); er ist kein Start-Trigger der Welle.

## 3. Closure-Trigger (Welle schließt)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wann Arbeit eine Welle braucht — der Trigger muss das *Mehr* gegenüber den
einzelnen Slice-DoDs benennen; kann er das nicht, liegt keine Welle vor.

- Alle Slices in `done/`. Für `slice-codec-datentypen-erweiterung` gilt dabei: bleibt der Beleg aus, dass der Referenzclient die Formen sendet (§4 und §6 des Slice), geht er nach der Regel des Slice-Kopfs mit der Zeile `Gegenstand:` in §7 nach `done/`.
- `make gates` grün, einschließlich `make test`.
- Das Mehr gegenüber den Slice-DoDs: Ein Rohbytes-Lauf auf **einer** Sitzung ab `READY` gegen ein Test-Backend, das den Backend-Port implementiert, belegt in einer Folge: ein Batch mit Ergebnis, ein Batch mit leerem Ergebnis, ein Batch mit zwei Ergebnissen, `sp_executesql` mit NULL- und OUTPUT-Parameter, `sp_prepexec` und danach `sp_execute` mit dem gelieferten Handle, ein unbekannter RPC und ein unbekanntes Handle (je ein definierter Fehler), und danach noch eine erfolgreiche Anfrage auf derselben Verbindung. Der Lauf enthält keine PLP- und keine NBCROW-Form; deren Belege liegen in `slice-codec-datentypen-erweiterung`. Er belegt, was die Slices einzeln nicht belegen: der Anfragezustand kehrt nach jeder Anfrageart nach `IDLE` zurück, die Handle-Tabelle überlebt Anfragen derselben Sitzung, und ein Fehler beendet die Sitzung nicht.
- Die Closure-Notiz führt die Aussagen der Welle in zwei Spalten, *gemessen* (Test, der besteht) und *Festlegung* (MS-TDS-Lesart, Setzung des Nutzers oder Entscheidung eines Slice ohne Messung am Referenzclient oder echten Server); jeder in §6 ausgeschlossene Gegenstand ist dort einem Träger zugeordnet, der als Datei existiert, oder trägt den Grund, warum nicht.
- Closure-Notiz in `welle-anfrageverarbeitung-results.md`.

## 4. Slices in dieser Welle

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Lifecycle als State Machine — der Zustand eines Slice ist sein
Lifecycle-Verzeichnis und wird hier **nicht** gespiegelt.

| Slice | Titel | Bezug |
|---|---|---|
| slice-sql-batch-dispatch | SQL_BATCH-Dispatch in `READY` auf den Backend-Port: Dekodierung, `QueryRequest`, Ergebnis, leeres Ergebnis, Fehlerfall | [TDS-FA-03](../../../spec/lastenheft.md#tds-fa-03--einfache-sql-batches), [TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0004](../adr/0004-session-zustandsautomat.md), [ADR-0006](../adr/0006-backend-vertrag-und-streaming.md), [ADR-0007](../adr/0007-fehler-und-attention.md) |
| slice-rpc-executesql-parameter | RPC-Dispatch: `sp_executesql` und Prozedurname mit typisierten Parametern, NULL, Richtung, definierte Fehler | [TDS-FA-04](../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0005](../adr/0005-rpc-und-prepared-statements.md) |
| slice-antwort-mehrteilig-output | Mehrere Ergebnisse, Update-Zähler, RETURNSTATUS, RETURNVALUE, DONEPROC, nicht unterstützte Antwortform | [TDS-FA-10](../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0005](../adr/0005-rpc-und-prepared-statements.md), [ADR-0006](../adr/0006-backend-vertrag-und-streaming.md) |
| slice-rpc-prepared-handles | `sp_prepexec`, `sp_execute`, Handle-Tabelle je Sitzung mit Grenze, definierte Fehler | [TDS-FA-04](../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-10](../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0004](../adr/0004-session-zustandsautomat.md), [ADR-0005](../adr/0005-rpc-und-prepared-statements.md) |
| slice-codec-datentypen-erweiterung | NBCROW, PLP (auch als RPC-Parameter) und Typen außerhalb des Mindestkatalogs im Codec | [TDS-FA-05](../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0006](../adr/0006-backend-vertrag-und-streaming.md) |

Reihenfolge: Der Batch-Dispatch legt den Rahmen (Annahme in `READY`, Anfragezustand, Anschluss an den Sequenzierer) und steht zuerst. Der RPC-Dispatch folgt und braucht den Parser und den Typkatalog. Die Antwortform (Mehrfach-Ergebnisse, RETURNSTATUS und RETURNVALUE) steht vor den Prepared-Statements, weil das Handle als RETURNVALUE über sie läuft. Der Handle-Slice schließt die Welle, weil er Dispatch, Parameterbindung und Antwortform voraussetzt und die Sitzung um Zustand erweitert. `slice-codec-datentypen-erweiterung` steht nach `slice-rpc-executesql-parameter`: Dessen §1 nennt ihn als Träger für PLP und Typen außerhalb des Mindestkatalogs und lehnt solche Parameter bis dahin ab; der Test des Parser-Slice `PLP lehnt die ganze Nachricht ab, auch als SQL-Text und Deklaration` wechselt mit dem Codec-Slice zu „gelesen“. Die Reihenfolge zu den übrigen Slices dieser Welle ist nicht festgelegt, weil keiner von ihnen ihn voraussetzt und er keinen von ihnen.

## 5. Abhängigkeiten

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Struktur: fünf Abschnitte.

- Blockiert: `slice-qa-kompatibilitaet` (Szenarien mit dem Referenzclient für Batch, `sp_executesql`, `Prepare()`, OUTPUT und mehrere Resultsets brauchen diesen Server), `slice-qa-reproduzierbarkeit` (Vergleichslauf D braucht Antworten, die verglichen werden können), `slice-qa-performance` und `slice-qa-stabilitaet` (Last und Fuzz brauchen Anfragen).
- Wird blockiert von: [welle-handshake-login](done/welle-handshake-login/welle-handshake-login.md) (`READY` entsteht dort); `slice-codec-rpc-parser` (Folge-Slice von `welle-protokollcodec`, `in-progress/`, ohne Welle — er bleibt dort, diese Welle hängt von ihm ab und nimmt ihn nicht auf); [welle-entscheidungen-grundlage](done/welle-entscheidungen-grundlage/welle-entscheidungen-grundlage.md) nicht mehr ([ADR-0004](../adr/0004-session-zustandsautomat.md), [ADR-0006](../adr/0006-backend-vertrag-und-streaming.md) und [ADR-0007](../adr/0007-fehler-und-attention.md) sind `Accepted`).
- Parallel geplant, nicht gedoppelt: die Wellen [welle-backend-streaming](welle-backend-streaming.md) und [welle-fehler-abbruch](welle-fehler-abbruch.md) (geplant von anderer Stelle). Die Grenze: **diese Welle** schneidet den Dispatch von SQL_BATCH und RPC auf den Backend-Port (ARC-005), die Parameterbindung, die Prepared-Handle-Verwaltung nach [ADR-0005](../adr/0005-rpc-und-prepared-statements.md) und die Reihenfolge mehrteiliger Antworten und OUTPUT-Werte; **`welle-backend-streaming`** schneidet Backend-Port, `Flow<QueryEvent>`, Typkatalog und die Kodierung der Resultsets (COLMETADATA, ROW) einschließlich des Sequenzierer-Kerns; **`welle-fehler-abbruch`** schneidet Fehlerabbildung, ERROR- und DONE-Kodierung, ATTENTION, Cancel, Timeout und Protokoll-Synchronität ([TDS-FA-06](../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), [TDS-FA-07](../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-07](../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)). Die Slices dieser Welle nennen die Nachbarslices als Start-Trigger (Backend-Port und Sequenzierer: [slice-backend-port-vertrag](done/slice-backend-port-vertrag.md), [slice-backend-sequenzierer](done/slice-backend-sequenzierer.md); Fehler-Mapping und ERROR-/DONE-Kodierung: [slice-fehler-mapping](done/slice-fehler-mapping.md)). Wer den Sequenzierer (ARC-015) schneidet, ist unter den drei Planern abzustimmen (offene Frage, §6 der Slices `slice-sql-batch-dispatch` und `slice-antwort-mehrteilig-output`).
- Wird nachgezogen von `slice-codec-datentypen-erweiterung` an `slice-rpc-executesql-parameter` (Abhängigkeit des Codec-Slice, nicht des Dispatch-Slice): was der Parser aus `slice-codec-rpc-parser` heute ablehnt (PLP, Typen außerhalb des Mindestkatalogs), liest der Codec-Slice künftig; ob der Dispatch dann die gelesenen Parameter bindet, gehört in keinen dieser beiden Zuschnitte und ist offen. Die Fehlerfolge für abgewiesene Parser-Nachrichten steht in `slice-rpc-executesql-parameter` §6.
- Berührt, blockiert aber nicht: [welle-transport-tls](done/welle-transport-tls/welle-transport-tls.md). Diese Welle arbeitet an der Nachrichten-Schnittstelle der Sitzung, nicht am Socket; die Anbindung an den Listener liegt im Bootstrap-Slice von [welle-handshake-login](done/welle-handshake-login/welle-handshake-login.md).
- Berührt, blockiert aber nicht: [welle-qualitaet-grundlage](welle-qualitaet-grundlage.md). Die Liefer-Punkte der Slices `slice-qa-kompatibilitaet`, `slice-qa-reproduzierbarkeit`, `slice-qa-performance` und `slice-qa-stabilitaet` zu Anfragen bleiben dort; diese Welle ändert sie nicht.

## 6. Out-of-Scope für diese Welle

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Eröffnung Schritt 1 — Out-of-Scope gehört zur
Zielsetzung: Was nicht ausdrücklich ausgeschlossen ist, dehnt die Welle, bis
der Closure-Trigger unerreichbar wird.

Träger der ausgeschlossenen Gegenstände (existierende Datei, sonst Zeiger auf die Closure):

- Backend-Port, `Flow<QueryEvent>`, Typkatalog, Zeilendarstellung, Resultset-Kodierung (COLMETADATA, ROW; NBCROW und PLP als Codec-Formen liegen in `slice-codec-datentypen-erweiterung` dieser Welle), Sequenzierer-Kern, Backpressure, Frist-Semantik — die Welle `welle-backend-streaming` ([ADR-0006](../adr/0006-backend-vertrag-und-streaming.md), [TDS-FA-05](../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-FA-08](../../../spec/lastenheft.md#tds-fa-08--backend-anbindung)); Welle-Datei: [welle-backend-streaming](welle-backend-streaming.md). Diese Welle ruft den Port und baut ihn nicht.
- Fehlerabbildung, `TDS_*`-Codes und Classes, ERROR- und DONE-Kodierung, ATTENTION, Cancel, Timeout, Ressourcenfreigabe bei Abbruch, Protokoll-Synchronität — die Welle `welle-fehler-abbruch` ([ADR-0007](../adr/0007-fehler-und-attention.md), [TDS-FA-06](../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), [TDS-FA-07](../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-07](../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)); Welle-Datei: [welle-fehler-abbruch](welle-fehler-abbruch.md). Diese Welle entscheidet, *dass* ein Fehler entsteht und ob die Verbindung bleibt, nicht wie er kodiert wird.
- Handshake, Login, Zustand `READY` herstellen, Bootstrap und Verdrahtung — [welle-handshake-login](done/welle-handshake-login/welle-handshake-login.md).
- Sockets, TLS, Listener, geordnetes Beenden mit Frist — [welle-transport-tls](done/welle-transport-tls/welle-transport-tls.md) und für das Beenden `welle-fehler-abbruch`; ein Lauf gegen einen Socket ist nicht Teil dieser Welle.
- Simulations-Backend (ARC-016): Welle [welle-simulations-backend](welle-simulations-backend.md); jedes echte Backend: kein Träger im Repo. Die Tests dieser Welle nutzen ein Test-Backend als Fixture.
- RPC-Formen außerhalb des Katalogs von [ADR-0005](../adr/0005-rpc-und-prepared-statements.md) (`sp_prepare`, `sp_unprepare`, `sp_cursor*`, Table-Valued-Parameter, MARS), `SET`-Text bei `SchemaOnly` und `KeyInfo`, Transaktionen (Pakettyp `0x0E`), `sp_reset_connection` — offen in [ADR-0005](../adr/0005-rpc-und-prepared-statements.md) und [ADR-0004](../adr/0004-session-zustandsautomat.md); Träger: der Re-Evaluierungs-Trigger der ADR und `slice-qa-kompatibilitaet` (Messung). Ein neuer Slice entsteht erst nach Nachweis des Bedarfs im Clientprofil.
- Typen außerhalb des Mindestkatalogs im Backend (`SqlType`, Länge von `UnicodeText`; offene Punkte 1 und 2 von [ADR-0012](../adr/0012-simulations-backend-beschreibung-und-formen.md)) — Zuschnitt offen; `slice-codec-datentypen-erweiterung` führt sie in §6 als Punkt, den der Nutzer entscheidet. UDT, `json` und Table-Valued-Parameter bleiben auch im Codec ausgeschlossen (§1 des Slice).
- Nachweis gegen den Referenzclient und einen echten SQL Server: Kompatibilitäts-Läufe, Mitschnitte, Tokenfolge und DONE-Statusbits, ordinale Zählung der RETURNVALUE, TDS 8.0, Pooling — `slice-qa-kompatibilitaet`, `slice-qa-reproduzierbarkeit`. Diese Welle belegt Dispatch und Antwortbytes mit Rohbytes gegen ein Test-Backend.
- Last und Durchsatz — `slice-qa-performance`. Fuzz über ungültige Anfragen in einer angemeldeten Sitzung: `slice-qa-stabilitaet` (geschlossen) hat nur den Anmeldepfad und die Parser ohne Transport gefuzzt; ein Träger für den Anfragepfad besteht nicht (Register `BEO-TDS/fuzz-nach-verdrahtung-ohne-traeger`).
- Observability-Adapter (ARC-009, SPEC-017): nur ein minimaler Log-Anschluss, der keine Parameterwerte und keinen Anfragetext ausgibt. Träger: Welle [welle-betrieb-diagnose](welle-betrieb-diagnose.md).
- Neue Anforderungen oder Änderungen am Lastenheft.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-traceability.md`
§Herkunfts-Anker für Steering-Loop-Regeln — dort die **Ruheort-Regel**: Die
beiden Zeiger unten sind so zu schreiben, wie sie vom Ruheort `done/` auflösen,
nicht vom Schreibort.

Ergebnis: `welle-anfrageverarbeitung-results.md` <!-- d-check:ignore (Ruheort done/: der Zeiger löst erst nach dem Verschieben auf) -->
Zähler: `../observations/` <!-- d-check:ignore (Ruheort done/: der Zeiger löst erst nach dem Verschieben auf) -->
