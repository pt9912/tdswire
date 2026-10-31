# Welle welle-backend-streaming: Backend-Port, Typabbildung und Resultset-Streaming ohne Vollpufferung

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

Ein Backend hinter dem Port ([TDS-FA-08](../../../spec/lastenheft.md#tds-fa-08--backend-anbindung)) liefert ein Ergebnis als `Flow` von Ereignissen, und ein Sequenzierer macht daraus die Antwort-Tokens des Codecs ([TDS-FA-05](../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen)): Spaltenmetadaten vor der ersten Zeile, Zeilen, Abschlüsse mit `DONE_MORE`-Bit aus der Vorschau, ERROR plus Abschluss bei einem Fehler im synchronen Strom, Typabbildung vom Backendtyp auf die TDS-Typen mit definiertem Fehler für nicht abbildbare Typen. Ein Ergebnis oberhalb der Heap-Grenze eines Testlaufs wird dabei ohne Vollpufferung an eine langsame Senke weitergereicht. Die Aussage steht an einem Test-Backend (minimaler Fake, kein Simulations-Backend), einer Senke im Test und den Hand-Vektoren des Codecs, nicht am Referenzclient, nicht an einem Socket und nicht an einem echten Backend (siehe §6). Die Speichergrenze ist die eines Testlaufs mit gesetztem Heap, keine Grenze des Produkts ([ADR-0006](../adr/0006-backend-vertrag-und-streaming.md), Grenze der Aussage).

**Zuordnung der Anforderung zur Streaming-Aussage.** Die Aussage „Resultset wird nicht vollständig gehalten“ steht im Lastenheft als Boundary-Kriterium von [TDS-FA-11](../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) und in der Spezifikation bei der Backpressure-Pflicht von [TDS-FA-08](../../../spec/lastenheft.md#tds-fa-08--backend-anbindung) (`SPEC-031`, `SPEC-002`); [TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilit%C3%A4t) (Stabilität) deckt in dieser Welle nur die Seite „ein abgebrochener oder unzulässiger Strom beendet den Prozess nicht“ (Reihenfolgeprüfung, Cancel und Close), [TDS-QA-04](../../../spec/lastenheft.md#tds-qa-04--performance) (Ressourcenverbrauch messbar) die Messung. Der Auftrag der Welle nannte [TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilit%C3%A4t) für das Streaming; die Welle führt es unter [TDS-FA-08](../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-FA-11](../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) und [TDS-QA-04](../../../spec/lastenheft.md#tds-qa-04--performance), bis der Nutzer die Zuordnung anders festlegt (offene Frage 1 in der Übergabe des Planers).

## 2. Trigger (Welle startet)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Regeln — ein Trigger ist **beobachtbar** dann, wenn ein *anderer*
Mensch ohne Rückfrage sagen kann, ob er eingetreten ist; ein Datum darf erwähnt
werden, aber nie Trigger sein. Und der **Start**-Trigger ist **kein Ergebnis
dieser Welle**: Steht er in der Slice-Liste unten, ist er falsch platziert.

- `make gates` ist auf `main` grün.
- [ADR-0006](../adr/0006-backend-vertrag-und-streaming.md) steht im ADR-Index auf `Accepted` (erfüllt). Ihre offenen Punkte führen die Slices dieser Welle ([slice-backend-sequenzierer](done/slice-backend-sequenzierer.md), [slice-backend-port-vertrag](done/slice-backend-port-vertrag.md), [slice-backend-streaming-nachweis](in-progress/slice-backend-streaming-nachweis.md)) und, ohne Welle, [slice-adr-backend-grenzen](done/slice-adr-backend-grenzen.md).
- Der Slice `slice-codec-tds-paket` der Welle [welle-protokollcodec](done/welle-protokollcodec/welle-protokollcodec.md) liegt in `done/`: der Encoder der Antwort-Tokens, der Typkatalog des Codecs und das Test-Gate `make test` stehen auf `main`.

Weitere Voraussetzungen gelten je Slice und stehen in dessen §4 (der Pin von `kotlinx-coroutines-core` aus `slice-transport-coroutines-geruest` für den Port-Slice): sie verzögern einen Slice, nicht den Start der Welle. [ADR-0004](../adr/0004-session-zustandsautomat.md) ist keine Voraussetzung (siehe §5).

## 3. Closure-Trigger (Welle schließt)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wann Arbeit eine Welle braucht — der Trigger muss das *Mehr* gegenüber den
einzelnen Slice-DoDs benennen; kann er das nicht, liegt keine Welle vor.

- Alle Slices in `done/`.
- `make gates` grün, einschließlich `make test`.
- Das Mehr gegenüber den Slice-DoDs: Der Nachweis in `slice-backend-streaming-nachweis` läuft über die ganze Kette in einem Lauf (Test-Backend, Port, Typabbildung, Sequenzierer, `TokenEncoder`, Paket-Schreiber, langsame Senke) mit einem Ergebnis, dessen Materialisierung im Testlauf den gesetzten Heap überschritte, und belegt dort: der Heap bleibt unter der Grenze, die Senke erhält eine Tokenfolge, die der Test-Decoder des Codecs liest, und ein Fehler nach teilweise gesendeten Zeilen endet bei synchronem Strom in ERROR plus Abschluss. Einzelne Slices belegen jeweils nur ihren Teil.
- Die Closure-Notiz führt die Aussagen der Welle in zwei Spalten, *gemessen* (Test oder Lauf, der besteht) und *Festlegung* (Entscheidung oder Lesart ohne Messung am Referenzclient oder echten SQL Server), und ordnet jeden offenen Punkt aus [ADR-0006](../adr/0006-backend-vertrag-und-streaming.md) (§Offen) einem Slice mit Ausgang zu: entschieden mit Beleg, ausdrücklich weiter offen mit Träger als Datei, oder an eine andere Welle übergeben mit Träger.
- Die Nutzerentscheidungen aus den Slice-Risiken (Ort der Typen in `domain` oder `port`, Abbildung des `Failure`-Ereignisses auf Fehlernummer und Severity, Grenze für „kleine“ Ergebnisse) sind getroffen und in den Notizen der Slices festgehalten.
- Closure-Notiz in `welle-backend-streaming-results.md`.

## 4. Slices in dieser Welle

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Lifecycle als State Machine — der Zustand eines Slice ist sein
Lifecycle-Verzeichnis und wird hier **nicht** gespiegelt.

| Slice | Titel | Bezug |
|---|---|---|
| slice-backend-port-vertrag | Backend-Port als Kotlin-Schnittstelle (`QueryBackend`, `QueryRequest`, `QueryExecution`, `QueryEvent`, `ResultColumn`, Backend-Typmodell) und minimales Test-Backend; Vertragstest | [TDS-FA-08](../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-QA-05](../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0006](../adr/0006-backend-vertrag-und-streaming.md) |
| slice-backend-typabbildung | Typabbildung Backendtyp → TDS-Typ und -Wert (zentral, deterministisch), Validierung der Metadaten vor COLMETADATA, definierter Fehler für nicht abbildbare Typen | [TDS-FA-05](../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [ADR-0006](../adr/0006-backend-vertrag-und-streaming.md) |
| slice-backend-sequenzierer | Sequenzierer `QueryEvent` → Token-Folge: COLMETADATA, ROW, DONE-Familie mit `DONE_MORE`-Vorschau, ERROR plus Abschluss bei Fehler im synchronen Strom, Prüfung der Ereignisreihenfolge | [TDS-FA-05](../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-FA-08](../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilit%C3%A4t), [ADR-0006](../adr/0006-backend-vertrag-und-streaming.md) |
| slice-backend-streaming-nachweis | Backpressure und Speichergrenze: Sequenzierer an einer suspendierenden Senke, Cancel und Close schließen die Ausführung, Nachweis ohne Vollpufferung über die ganze Kette | [TDS-FA-08](../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-FA-11](../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilit%C3%A4t), [TDS-QA-04](../../../spec/lastenheft.md#tds-qa-04--performance), [ADR-0006](../adr/0006-backend-vertrag-und-streaming.md) |

Reihenfolge: Port-Vertrag vor Typabbildung und Sequenzierer (beide nutzen die Ereignis- und Spaltentypen des Ports); die Typabbildung vor dem Sequenzierer (er ruft sie für COLMETADATA und ROW); der Nachweis schließt die Welle, weil er alle drei nutzt.

## 5. Abhängigkeiten

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Struktur: fünf Abschnitte.

- Blockiert: [welle-anfrageverarbeitung](welle-anfrageverarbeitung.md) (Dispatch von SQL_BATCH und RPC, Prepared-Handles, mehrteilige Antworten; von anderer Stelle geplant): sie ruft den Port und den Sequenzierer dieser Welle. Blockiert das Simulations-Backend ([TDS-FA-11](../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), Welle [welle-simulations-backend](welle-simulations-backend.md)): es implementiert den Port dieser Welle. Blockiert mittelbar [welle-fehler-abbruch](welle-fehler-abbruch.md) ([TDS-FA-06](../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszust%C3%A4nde), [TDS-FA-07](../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe); von anderer Stelle geplant): sie verfeinert Fehlerabbildung und Cancel auf Port und Sequenzierer.
- Wird blockiert von: [ADR-0006](../adr/0006-backend-vertrag-und-streaming.md), das auf `Accepted` stehen muss (Start-Trigger); `slice-transport-coroutines-geruest` (Pin von `kotlinx-coroutines-core`; nur der Port-Slice, in dessen §4) in der Welle [welle-transport-tls](done/welle-transport-tls/welle-transport-tls.md); erfüllt, der Slice liegt in `done/`.
- Berührt, blockiert aber nicht: [welle-transport-tls](done/welle-transport-tls/welle-transport-tls.md). Der Sequenzierer schreibt Tokens in eine Senke; diese Welle nimmt vom Transport nur eine suspendierende Senken-Schnittstelle (Bytes hinein, Rückstau durch Suspendieren) und belegt sie im Test mit einer langsamen Testsenke. Wer diese Schnittstelle schneidet, ist mit `slice-transport-coroutines-geruest` abzustimmen, dessen Nachrichtenschreiber rückstaufest sein soll; zwei Definitionen derselben Schnittstelle sind zu vermeiden (offene Frage in `slice-backend-streaming-nachweis` §6).
- Berührt, blockiert aber nicht: `slice-codec-rpc-parser` (in `in-progress/`; Anfrageseite, liefert die Parameter für `QueryRequest`) und `slice-codec-datentypen-erweiterung` (`open/`; NBCROW, PLP, Typen außerhalb des Mindestkatalogs). Diese Welle bildet nur den Mindestkatalog des Codecs ab; ein Backendtyp außerhalb davon endet im definierten Fehler.
- Berührt, blockiert aber nicht: [welle-handshake-login](done/welle-handshake-login/welle-handshake-login.md) (kein Backend, keine Anfragen) und [welle-qualitaet-grundlage](welle-qualitaet-grundlage.md): `slice-qa-stabilitaet` (parallele Verbindungen, Fuzz) und `slice-qa-performance` (Lasttest mit getrennter Messung von Protokollschicht und Backend) bauen auf diesem Pfad auf und messen mehr als diese Welle; sie bleiben unverändert.
- [ADR-0004](../adr/0004-session-zustandsautomat.md) (Zustandsautomat) blockiert nicht: Der Sequenzierer hält den Zustand einer Antwort, nicht den der Verbindung. Wo `SPEC-001` und `SPEC-029` die Atomarität von Übergang und Schreiben unter einer Sperre je Sitzung verlangen, ist die Anbindung an den Anfragezustand Sache der Anfrageverarbeitung (§6).

## 6. Out-of-Scope für diese Welle

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Eröffnung Schritt 1 — Out-of-Scope gehört zur
Zielsetzung: Was nicht ausdrücklich ausgeschlossen ist, dehnt die Welle, bis
der Closure-Trigger unerreichbar wird.

Träger der ausgeschlossenen Gegenstände (existierende Datei, sonst Zeiger auf die Closure):

- Dispatch von SQL_BATCH und RPC, Prepared-Handles und Handle-Verwaltung je Sitzung, mehrteilige RPC-Antworten mit `RETURNSTATUS`, `RETURNVALUE` und `DONEPROC`/`DONEINPROC` ([TDS-FA-10](../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [ADR-0005](../adr/0005-rpc-und-prepared-statements.md)): [welle-anfrageverarbeitung](welle-anfrageverarbeitung.md) (anderer Planer). Der Port definiert die Ereignistypen `ReturnStatus` und `OutputParameter`, weil `SPEC-002` sie trägt; ihre Abbildung auf Tokens ist dort. Ob der Sequenzierer dieser Welle sie bis dahin als nicht unterstützte Antwortform mit definiertem Fehler ablehnt ([TDS-FA-10](../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), Negative), ist Entscheidungsbedarf (`slice-backend-sequenzierer` §6). Träger der Übergabe: [welle-anfrageverarbeitung](welle-anfrageverarbeitung.md) (Slice [slice-antwort-mehrteilig-output](open/slice-antwort-mehrteilig-output.md)).
- Fehler- und ATTENTION-Semantik ([TDS-FA-06](../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszust%C3%A4nde), [TDS-FA-07](../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [ADR-0007](../adr/0007-fehler-und-attention.md)): [welle-fehler-abbruch](welle-fehler-abbruch.md) (anderer Planer; [ADR-0007](../adr/0007-fehler-und-attention.md) ist `Accepted`). Diese Welle bildet nur ab, was [ADR-0006](../adr/0006-backend-vertrag-und-streaming.md) festlegt (Fehler im synchronen Strom: ERROR plus Abschluss; nicht abschließbarer Strom: Verbindung ohne Abschluss schließen); woher Fehlernummer, Severity und State des `Failure`-Ereignisses kommen, ist Entscheidungsbedarf (`slice-backend-sequenzierer` §6). Katalog eigener Codes, Severity-Schwellen, ATTENTION-Bestätigung und `CANCEL_TIMEOUT` gehören dorthin.
- Verbindungs-Zustandsautomat, Anfragezustand (`SPEC-001`, `SPEC-029`), Sperre je Sitzung ([ADR-0004](../adr/0004-session-zustandsautomat.md), `Accepted`): nicht in dieser Welle. Der Sequenzierer ist ohne Sitzung testbar.
- Simulations-Backend ([TDS-FA-11](../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), `SPEC-035` bis `SPEC-040`, ARC-016): Welle [welle-simulations-backend](welle-simulations-backend.md). Hier nur der minimale Test-Fake im Test-Quelltext, ohne Anweisungserkennung, ohne Tabellenbeschreibung, ohne Zeilenquelle nach `SPEC-035`.
- Transport, Sockets, TLS, Listener, Nachrichtenschreiber über einem Kanal: `welle-transport-tls`. Diese Welle läuft gegen eine Test-Senke.
- Antworten eines echten SQL Servers und des Referenzclients auf die gebauten Szenarien (auch zum Bit `DONE_MORE`, zur Reihenfolge der Tokens und zu Statusbits), MARS, Transaktionen, `QUERY_TIMEOUT`: `slice-qa-reproduzierbarkeit`, `slice-qa-kompatibilitaet` (`BEO-TDS/messung-am-echten-sql-server-fehlt`).
- Typen außerhalb des Mindestkatalogs, NBCROW, PLP, LOBs: `slice-codec-datentypen-erweiterung` ([TDS-FA-05](../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), Out-of-Scope des Lastenhefts: vollständiger SQL-Server-Typkatalog).
- Blockierendes Backend hinter dem `Flow` (Thread-Verbrauch, Interrupt-Verhalten, Abbruch eines nicht unterbrechbaren Aufrufs): Ein echtes Backend gibt es nicht; die Welle prüft nur ein suspendierendes Test-Backend und führt den Punkt als offen ([ADR-0006](../adr/0006-backend-vertrag-und-streaming.md), §Offen und Re-Evaluierungs-Trigger). Träger der Entscheidung: [slice-adr-backend-grenzen](done/slice-adr-backend-grenzen.md) (ohne Welle).
- Durchsetzung der Frist `deadlineEpochMillis`: Die Semantik ist in [ADR-0006](../adr/0006-backend-vertrag-und-streaming.md) offen; die Welle übernimmt das Feld unverändert in den Port und setzt nichts durch. Träger der Entscheidung: [slice-adr-backend-grenzen](done/slice-adr-backend-grenzen.md) (ohne Welle), das auch die Grenze für „kleine“ Ergebnisse, die Zeilendarstellung eines materialisierenden Backends und die Behandlung von `OutOfMemoryError` entscheidet.
- Neue Anforderungen oder Änderungen am Lastenheft.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-traceability.md`
§Herkunfts-Anker für Steering-Loop-Regeln — dort die **Ruheort-Regel**: Die
beiden Zeiger unten sind so zu schreiben, wie sie vom Ruheort `done/` auflösen,
nicht vom Schreibort.

Ergebnis: `welle-backend-streaming-results.md`
Zähler: `../observations/` <!-- d-check:ignore (Ruheort done/: der Zeiger löst erst nach dem Verschieben auf) -->
