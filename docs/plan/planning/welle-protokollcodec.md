# Welle welle-protokollcodec: TDS-Protokollcodec ohne Netzwerk

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

Ein byteweise zerstückelter TDS-Nachrichtenstrom wird ohne Netzwerk zu Nachrichten zusammengesetzt und in die Tokens zerlegt, die Verbindungsaufbau, Login, SQL_BATCH und RPC auf der Antwortseite brauchen; umgekehrt serialisiert der Codec diese Tokens und zerlegt sie in Pakete der ausgehandelten Größe. Die Aussage steht an einem Test, der Paket-Codec und Token-Codec gemeinsam durchläuft ([TDS-FA-01](../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-FA-05](../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilität)); sie gilt für den Codec, nicht für die Kompatibilität mit dem Referenzclient (siehe §6).

## 2. Trigger (Welle startet)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Regeln — ein Trigger ist **beobachtbar** dann, wenn ein *anderer*
Mensch ohne Rückfrage sagen kann, ob er eingetreten ist; ein Datum darf erwähnt
werden, aber nie Trigger sein. Und der **Start**-Trigger ist **kein Ergebnis
dieser Welle**: Steht er in der Slice-Liste unten, ist er falsch platziert.

- `make gates` ist auf `main` grün (Gerüst mit Docker, detekt und Build steht).
- [ADR-0001](../adr/0001-tds-version-und-clientprofil.md), [ADR-0002](../adr/0002-tls-und-verschluesselung.md), [ADR-0003](../adr/0003-transportframework.md), [ADR-0005](../adr/0005-rpc-und-prepared-statements.md) und [ADR-0009](../adr/0009-authentifizierungsmodus.md) stehen im ADR-Index auf `Accepted`. Die Welle [welle-entscheidungen-grundlage](welle-entscheidungen-grundlage.md) muss dafür nicht geschlossen sein: ihre offenen ADRs ([ADR-0004](../adr/0004-session-zustandsautomat.md), [ADR-0006](../adr/0006-backend-vertrag-und-streaming.md), [ADR-0007](../adr/0007-fehler-und-attention.md)) betreffen Zustandsautomat, Backend-Vertrag und Fehlersemantik, die dieser Codec nicht berührt (siehe §6).

## 3. Closure-Trigger (Welle schließt)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wann Arbeit eine Welle braucht — der Trigger muss das *Mehr* gegenüber den
einzelnen Slice-DoDs benennen; kann er das nicht, liegt keine Welle vor.

- Alle Slices in `done/`.
- `make gates` grün, einschließlich des neuen Test-Gates `make test`.
- Das Mehr gegenüber der DoD des Slice: Die Closure-Notiz führt die Aussagen des Codecs in zwei Spalten, *gemessen* (Test, der besteht) und *Festlegung* (MS-TDS-Lesart oder Entscheidung des Slice, ohne Referenzclient-Messung), und jeder in §6 ausgeschlossene Gegenstand hat einen Folge-Slice als Datei in `open/` oder in der Notiz den Grund, warum nicht. Ohne diese Zuordnung wäre die Welle ein einzelner Slice.
- Closure-Notiz in `welle-protokollcodec-results.md`.

## 4. Slices in dieser Welle

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Lifecycle als State Machine — der Zustand eines Slice ist sein
Lifecycle-Verzeichnis und wird hier **nicht** gespiegelt.

| Slice | Titel | Bezug |
|---|---|---|
| slice-codec-tds-paket | TDS-Paketheader, Paket-Codec und Token-Grundstock ohne Netzwerk | [TDS-FA-01](../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-FA-02](../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [TDS-FA-05](../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-FA-06](../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), [TDS-FA-10](../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilität) |

## 5. Abhängigkeiten

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Roadmap-Struktur: fünf Abschnitte.

- Blockiert: die funktionalen Wellen für Verbindungsaufbau, Login, SQL_BATCH und RPC (wegen der Nachrichtenformen, die sie über diesen Codec lesen und schreiben); sie sind noch nicht geplant und stehen in keiner Roadmap-Zeile.
- Folge-Slices aus der Closure von `slice-codec-tds-paket`, ohne Welle und nicht Teil des Closure-Triggers dieser Welle: [slice-codec-rpc-parser](open/slice-codec-rpc-parser.md) (RPC-Anfrage-Parser nach [ADR-0005](../adr/0005-rpc-und-prepared-statements.md)) und [slice-codec-datentypen-erweiterung](open/slice-codec-datentypen-erweiterung.md) (NBCROW, PLP, weitere Typen); die funktionale Welle für SQL_BATCH und RPC nimmt sie auf.
- Berührt, blockiert aber nicht: [welle-qualitaet-grundlage](welle-qualitaet-grundlage.md). Das Test-Gate dieser Welle überschneidet sich mit dem ersten Liefer-Punkt von `slice-qa-wartbarkeit` (Unit-Test-Gate); wer zuerst schließt, gibt dem anderen das Gate (siehe Slice §6).
- Wird blockiert von: keiner Welle. [welle-entscheidungen-grundlage](welle-entscheidungen-grundlage.md) bleibt offen und läuft weiter (`slice-adr-session-zustand`, `slice-adr-backend-streaming`, `slice-adr-fehler-attention`).

## 6. Out-of-Scope für diese Welle

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Eröffnung Schritt 1 — Out-of-Scope gehört zur
Zielsetzung: Was nicht ausdrücklich ausgeschlossen ist, dehnt die Welle, bis
der Closure-Trigger unerreichbar wird.

- Netzwerk jeder Art: keine Sockets, kein `AsynchronousSocketChannel`, keine `SSLEngine`, keine Coroutinen-I/O. Der Codec arbeitet auf Bytes im Speicher; die Netzwerk-Laufzeit entscheidet [ADR-0003](../adr/0003-transportframework.md) und ihr Code kommt mit dem Transport.
- Handshake und Session: kein Verbindungs-Zustandsautomat, keine PRELOGIN-/LOGIN7-Verhandlung als Ablauf, keine TLS-Kapselung in Paketen vom Typ `0x12` (der Codec kennt `0x12` nur als Pakettyp). Der Zustandsautomat ist [ADR-0004](../adr/0004-session-zustandsautomat.md) (`Proposed`).
- Backend: keine Abbildung zwischen Backend-Ereignissen und Tokens, kein Backend-Port, kein Simulations-Backend. Der Backend-Vertrag ist [ADR-0006](../adr/0006-backend-vertrag-und-streaming.md) (`Proposed`).
- Anfrageseite von SQL_BATCH und RPC: kein Parser für SQL_BATCH-Text, ALL_HEADERS oder RPC-Nachrichten (`sp_executesql` und folgende); die Antwort-Tokens `RETURNSTATUS` und `RETURNVALUE` sind im Codec, der RPC-Parser nicht.
- Fehler- und ATTENTION-Semantik: der Codec serialisiert Nummer, Severity, State und Meldung, die man ihm gibt, und setzt das ATTN-Bit des DONE, wenn man es ihm sagt; welche Fehler welche Nummer tragen und wann abgebrochen wird, entscheidet [ADR-0007](../adr/0007-fehler-und-attention.md) (`Proposed`).
- Authentifizierung: kein Adapter, keine feste Ablehnungsantwort, keine Prüfung von Kennung und Passwort. Der LOGIN7-Parser liefert die Felder, entscheidet aber nichts ([ADR-0009](../adr/0009-authentifizierungsmodus.md)).
- Nachweis gegen den Referenzclient und gegen einen echten SQL Server: keine Kompatibilitäts-Läufe, keine Mitschnitte. Sie gehören in `welle-qualitaet-grundlage` (Kompatibilitäts- und Referenzabgleich-Gate).
- Neue Anforderungen oder Änderungen am Lastenheft.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-traceability.md`
§Herkunfts-Anker für Steering-Loop-Regeln — dort die **Ruheort-Regel**: Die
beiden Zeiger unten sind so zu schreiben, wie sie vom Ruheort `done/` auflösen,
nicht vom Schreibort.

Ergebnis: [welle-protokollcodec-results](welle-protokollcodec-results.md)
Zähler: [observations](../observations/)
