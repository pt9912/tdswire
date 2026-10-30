# Welle welle-fehler-abbruch: Fehler und Abschlusszustände, Abbruch und Ressourcenfreigabe, Protokoll-Synchronität

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

Ein Rohbytes-Client durchläuft gegen den Server mit einem Fake-Backend hinter dem Port Fehler, Abbruch und Beenden, ohne dass je die Antwort einer früheren Anfrage einer späteren zugeordnet wird: Fehler werden nach der Zuordnungstabelle des Fehler-Mappings als ERROR und DONE mit Fehlerstatus gesendet, die Verbindung bleibt genau in den Fällen offen, die [ADR-0007](../adr/0007-fehler-und-attention.md) festlegt (Class ≤ 19 mit vollständigem Abschluss; nach Cancel oder Timeout mit gesendetem DONE mit ATTENTION-Status) und schließt sonst, ATTENTION während des Streamings wird mit der Abschlussbestätigung beantwortet und löst genau einen Cancel aus, ein Verbindungsabbruch gibt Backend-Strom und Sitzungsressourcen frei, und das geordnete Beenden nach SPEC-044 kehrt nach spätestens `SHUTDOWN_GRACE` plus `CANCEL_TIMEOUT` zurück ([TDS-FA-06](../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), [TDS-FA-07](../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-07](../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilität)). Die Aussage steht an Rohbytes-Tests und einem Smoke mit Fake-Backend und gilt für diesen Client und dieses Backend, nicht für die Annahme durch den Referenzclient `Microsoft.Data.SqlClient` und nicht für einen echten SQL Server (siehe §6).

## 2. Trigger (Welle startet)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Regeln — ein Trigger ist **beobachtbar** dann, wenn ein *anderer*
Mensch ohne Rückfrage sagen kann, ob er eingetreten ist; ein Datum darf erwähnt
werden, aber nie Trigger sein. Und der **Start**-Trigger ist **kein Ergebnis
dieser Welle**: Steht er in der Slice-Liste unten, ist er falsch platziert.

- `make gates` ist auf `main` grün.
- [ADR-0004](../adr/0004-session-zustandsautomat.md) und [ADR-0007](../adr/0007-fehler-und-attention.md) stehen im ADR-Index auf `Accepted` (erfüllt). Ihre offenen Punkte schließen sie nur per Folge-ADR; die Träger dieser Welle stehen in den Closure-Notizen von `slice-adr-session-zustand` und `slice-adr-fehler-attention` und in den DoD und Risiken der Slices unten.
- Der Slice `slice-handshake-login` der Welle [welle-handshake-login](welle-handshake-login.md) liegt in `done/`: Handshake-Zustandsautomat, Sitzungsrahmen und das Schließen nach der festen Login-Ablehnung stehen auf `main`.

Weitere Voraussetzungen gelten je Slice und stehen in dessen §4 (der Anfragezustand `IDLE → RUNNING → STREAMING → COMPLETED` der Welle `welle-anfrageverarbeitung`, der Port samt Strom der Welle `welle-backend-streaming` mit [ADR-0006](../adr/0006-backend-vertrag-und-streaming.md) auf `Accepted`, der Listener und das Verbindungsende-Ereignis der Welle [welle-transport-tls](welle-transport-tls.md), der Stop-Pfad von `slice-bootstrap-konfiguration`, und die Nutzerentscheidungen aus den Risiken der Slices): Sie verzögern einen Slice, nicht den Start der Welle.

## 3. Closure-Trigger (Welle schließt)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wann Arbeit eine Welle braucht — der Trigger muss das *Mehr* gegenüber den
einzelnen Slice-DoDs benennen; kann er das nicht, liegt keine Welle vor.

- Alle Slices in `done/`.
- `make gates` grün, einschließlich `make test`.
- Das Mehr gegenüber den Slice-DoDs: Ein Smoke fährt in einem Lauf über eine Verbindung des Rohbytes-Clients gegen den über den Bootstrap-Pfad gestarteten Serverprozess mit Fake-Backend: Fehler nach bereits gesendeten Zeilen (ERROR und DONE mit Fehlerstatus) und unmittelbar folgende Anfrage mit nur ihrer eigenen Antwort; Fehler einer schließenden Folge (Verbindung geschlossen); nicht abschließbarer Strom (geschlossen ohne ERROR und DONE); ATTENTION während des Streamings (Abschlussbestätigung, eine Folgeanfrage); ein Backend, das den Cancel ignoriert (Schließen nach `CANCEL_TIMEOUT`); abrupter Abbruch (Backend-Strom und Sitzung freigegeben, Prozess läuft weiter); Stop mit laufender Anfrage (Rückkehr innerhalb der Frist, `TDS_SHUTDOWN` für eine spätere Anfrage). Einzelne Slices belegen jeweils nur ihren Teil, der Zusammenhang über Fehler, Abbruch und Beenden in einem Lauf ist das Mehr.
- Die Closure-Notiz führt die Aussagen der Welle in zwei Spalten, *gemessen* (Test oder Smoke, der besteht) und *Festlegung* (Setzung des Nutzers oder Lesart ohne Referenzclient-Messung; dazu gehören Class-Werte, Verbindungsfolge und die Zuordnung der Codes). Jeder offene Punkt aus [ADR-0004](../adr/0004-session-zustandsautomat.md) und [ADR-0007](../adr/0007-fehler-und-attention.md) hat dort genau einen Ausgang: vom Nutzer entschieden und umgesetzt (mit Slice-Beleg), oder weiter offen mit Träger als Datei oder Grund. Dasselbe gilt für jeden in §6 ausgeschlossenen Gegenstand.
- Beleg im Beobachtungs-Register: `BEO-TDS/messung-am-echten-sql-server-fehlt` und `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` tragen je einen weiteren Beleg dieser Welle (beide stehen mit Stand 3× auf *geplant*; der Träger bleibt bei den Slices der Qualitätswelle).
- Closure-Notiz in `welle-fehler-abbruch-results.md`.

## 4. Slices in dieser Welle

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Lifecycle als State Machine — der Zustand eines Slice ist sein
Lifecycle-Verzeichnis und wird hier **nicht** gespiegelt.

| Slice | Titel | Bezug |
|---|---|---|
| slice-fehler-mapping | Fehler-Mapping: Zuordnung jedes Fehlercodes zu ERROR-Nummer, Class, State, Text und Verbindungsfolge; Fortschreibung der Zuordnungstabelle in der Spezifikation | [TDS-FA-06](../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), [TDS-QA-07](../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [ADR-0007](../adr/0007-fehler-und-attention.md) |
| slice-fehler-zustandsuebergang | Zustandsübergänge nach Fehler: Verbindung offen, nach ERROR und DONE geschlossen oder ohne Antwort geschlossen; Folgeanfrage nur mit eigener Antwort; offene Zustandspunkte | [TDS-FA-06](../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), [TDS-QA-07](../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0004](../adr/0004-session-zustandsautomat.md), [ADR-0007](../adr/0007-fehler-und-attention.md) |
| slice-attention-cancel-timeout | ATTENTION, Cancel und Timeout: Abschlussbestätigung, genau ein Cancel, Race DONE gegen ATTENTION, `CANCEL_TIMEOUT` | [TDS-FA-07](../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-07](../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0004](../adr/0004-session-zustandsautomat.md), [ADR-0007](../adr/0007-fehler-und-attention.md) |
| slice-abbruch-ressourcen-shutdown | Ressourcenfreigabe bei Disconnect und geordnetes Beenden (SPEC-044) mit `TDS_SHUTDOWN`; Stop-Pfad des Bootstrap | [TDS-FA-07](../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-07](../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [ADR-0004](../adr/0004-session-zustandsautomat.md), [ADR-0007](../adr/0007-fehler-und-attention.md) |

Reihenfolge: Das Mapping steht vor dem Zustandsübergang (dieser führt die Folge aus, die jenes festlegt), der Zustandsübergang vor ATTENTION (der Abbruchpfad hängt am selben Sitzungsrahmen und Mutex), ATTENTION vor Disconnect und Beenden (beide rufen den Abbruchpfad). Das Mapping ist als einziger Slice unabhängig von Anfragezustand und Transport und kann beginnen, sobald die Welle startet.

Abgrenzung zu den Nachbarwellen (Vorschlag, mit deren Planern abzustimmen): Die Welle `welle-anfrageverarbeitung` liefert Dispatch von SQL_BATCH und RPC und den Anfragezustand im Happy Path; diese Welle liefert den Fehler- und Abbruchpfad am selben Zustand (`CANCELLING`, Fehlerübergänge). Die Welle `welle-backend-streaming` liefert Port, Strom und Typabbildung; diese Welle konsumiert `Failure`, Stromende und `cancel`. Wer den Response Sequencer (ARC-015) schreibt, ist nicht entschieden (Risiko in `slice-fehler-zustandsuebergang`).

## 5. Abhängigkeiten

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Struktur: fünf Abschnitte.

- Blockiert: `slice-qa-synchronitaet` und der Stabilitäts-Teil von `slice-qa-stabilitaet` der Welle [welle-qualitaet-grundlage](welle-qualitaet-grundlage.md) (sie brauchen das Verhalten, das diese Welle baut); die Fehler- und Cancel-Szenarien von `slice-qa-kompatibilitaet`. Diese Welle ändert jene Slices nicht; die Messungen am Referenzclient, die Fuzz-Tests und der Test mit parallelen Verbindungen bis `MAX_CONNECTIONS` bleiben dort ([TDS-QA-07](../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilität) sind hier nur auf der Protokollebene mit Fake-Backend belegt). Der Slice `slice-qa-synchronitaet` nennt die Wahl der Recovery-Regel noch als offen in [ADR-0007](../adr/0007-fehler-und-attention.md); den Nachzug übernimmt dessen Planung.
- Wird blockiert von: [ADR-0004](../adr/0004-session-zustandsautomat.md) und [ADR-0007](../adr/0007-fehler-und-attention.md) nicht mehr (beide `Accepted`); [welle-handshake-login](welle-handshake-login.md) (Slice `slice-handshake-login`; für den Stop-Pfad auch `slice-bootstrap-konfiguration`); der Welle [welle-anfrageverarbeitung](welle-anfrageverarbeitung.md) (Anfragezustand aus [slice-sql-batch-dispatch](next/slice-sql-batch-dispatch.md), für die Slices ab `slice-fehler-zustandsuebergang`); der Welle [welle-backend-streaming](welle-backend-streaming.md) (Port, Strom, `Failure`, `cancel`: [slice-backend-port-vertrag](done/slice-backend-port-vertrag.md), [slice-backend-sequenzierer](done/slice-backend-sequenzierer.md); [ADR-0006](../adr/0006-backend-vertrag-und-streaming.md) auf `Accepted`); [welle-transport-tls](welle-transport-tls.md) (Verbindungsende-Ereignis und Listener-Stop, nur der Slice `slice-abbruch-ressourcen-shutdown`). Die Wellen `welle-anfrageverarbeitung` und `welle-backend-streaming` werden von anderer Stelle geplant; ihre Slice-Kennungen stehen in den Start-Triggern der Slices dieser Welle, soweit die Datei existiert.
- Berührt, blockiert aber nicht: [welle-handshake-login](welle-handshake-login.md). Die feste Login-Ablehnung (ERROR 18456, Class 14) bleibt dort; das Fehler-Mapping nimmt sie lesend auf, ein Test belegt die Byte-Gleichheit. Der Stop ohne Frist-Logik, den jene Welle in §6 ausschließt, wird hier durch das geordnete Beenden ersetzt.
- Beobachtungs-Register: Die Messlücken am echten SQL Server und zu TDS 8.0 und Pooling (`BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`) haben ihre Träger in `slice-qa-reproduzierbarkeit` und `slice-qa-kompatibilitaet`; diese Welle liefert Belege, keine Messung.

## 6. Out-of-Scope für diese Welle

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Eröffnung Schritt 1 — Out-of-Scope gehört zur
Zielsetzung: Was nicht ausdrücklich ausgeschlossen ist, dehnt die Welle, bis
der Closure-Trigger unerreichbar wird.

Träger der ausgeschlossenen Gegenstände (existierende Datei, sonst Zeiger auf die Closure oder der Grund):

- SQL_BATCH- und RPC-Dispatch, Batch-Zerlegung, Anfragezustand im Happy Path — Welle [welle-anfrageverarbeitung](welle-anfrageverarbeitung.md) (wird von anderer Stelle geplant). Diese Welle hängt Fehler- und Abbruchpfad an den Zustand, sie baut den Dispatch nicht.
- Backend-Port, Flow, Typabbildung, Simulations-Backend — Welle [welle-backend-streaming](welle-backend-streaming.md) (andere Planung), [ADR-0006](../adr/0006-backend-vertrag-und-streaming.md). Diese Welle konsumiert `Failure`, Strom und `cancel`.
- Transport, TLS, Listener, Erkennen des Verbindungsendes — [welle-transport-tls](welle-transport-tls.md).
- Handshake und feste Login-Ablehnung (ERROR 18456) — [welle-handshake-login](welle-handshake-login.md); hier nur lesend im Mapping.
- Nachweis gegen den Referenzclient und einen echten SQL Server: Fehler-, Cancel- und Pooling-Szenarien am .NET-Client, Messung der Fehlernummern, States, Texte und DONE-Statusbits, TDS 8.0 — `slice-qa-kompatibilitaet`, `slice-qa-synchronitaet` und `slice-qa-reproduzierbarkeit` (Vergleichslauf D). Diese Welle belegt Verhalten mit Rohbytes und Fake-Backend ([TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität) bleibt dort).
- Fuzz- und Fragmentierungs-Gate sowie Test mit parallelen Verbindungen bis `MAX_CONNECTIONS` als Gate — `slice-qa-stabilitaet`.
- Observability-Adapter (strukturierte Logs, Metriken, Traces, `SPEC-017`): nur der minimale Log-Anschluss. Träger: Welle [welle-betrieb-diagnose](welle-betrieb-diagnose.md).
- Retry im Server (Option C von [ADR-0007](../adr/0007-fehler-und-attention.md)), Mehrfach-Anfragen je Sitzung (MARS, `SPEC-004`), garantierte Unterbrechbarkeit externer Backendoperationen und die Nachbildung aller SQL-Server-Fehlernummern — ausdrücklich Out-of-Scope von [TDS-FA-06](../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände) und [TDS-FA-07](../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) bzw. nicht gewählt.
- Entscheidungen, die den Nutzer verlangen (siehe die Risiken der Slices): Sie werden hier nicht getroffen; jede Entscheidung läuft als Nutzerfrage und wird im Slice festgehalten.
- Neue Anforderungen oder Änderungen am Lastenheft.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-traceability.md`
§Herkunfts-Anker für Steering-Loop-Regeln — dort die **Ruheort-Regel**: Die
beiden Zeiger unten sind so zu schreiben, wie sie vom Ruheort `done/` auflösen,
nicht vom Schreibort.

Ergebnis: `welle-fehler-abbruch-results.md`
Zähler: `../observations/` <!-- d-check:ignore (Ruheort done/: der Zeiger löst erst nach dem Verschieben auf) -->
