# ADR-0015: Spannungen zwischen angenommener ADR und Spezifikation: OutputParameter im RPC, Zuschnitt der Zustandsaufzählung

**Status:** Proposed

**Datum:** 2026-10-31

**Autor:** pt9912

**Bezug:** [ADR-0004](0004-session-zustandsautomat.md), [ADR-0005](0005-rpc-und-prepared-statements.md), [ADR-0013](0013-backend-grenzen.md), [TDS-FA-10](../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-FA-01](../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-FA-07](../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe)

**Schärft:** [SPEC-031](../../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität) (Ablehnung von `OutputParameter` nur in der Antwort auf einen Batch), [SPEC-001](../../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung) (flacher Zuschnitt der Aufzählung), [TDS-FA-10.a](../../../spec/spezifikation.md#tds-fa-10a--mehrteilige-antworten-und-rpc-ausgabewerte), [ARC-004](../../../spec/architecture.md#1-komponenten-übersicht).

**Supersedes (teilweise):** [ADR-0013](0013-backend-grenzen.md) Entscheidung 6, **nur** den Halbsatz „Letzteres bleibt bis zur Abbildung abgelehnt“ (zu `OutputParameter.value`). Der Rest von Entscheidung 6 und alle anderen Entscheidungen von ADR-0013 gelten unverändert. Der Status von ADR-0013 bleibt `Accepted`, weil nur ein Halbsatz abgelöst wird; ADR-0013 wird nicht angefasst ([AGENTS.md](../../../AGENTS.md) §3.5). Die zweite Spannung (ADR-0004) ist **kein** `Supersedes`, sondern das Schließen eines dort ausdrücklich offen geführten Punktes („Schärft“).

---

## Kontext

Zwei angenommene ADRs und die Spezifikation (Rang 2, höher als jede ADR) stehen nebeneinander, ohne dass eine Entscheidung es sagt. Beide Fälle stehen in den Closure-Notizen als „Folge-ADR nötig“ ohne Träger; der Nutzer hat am 2026-10-31 die Empfehlung übernommen.

**(a) `OutputParameter`.** [ADR-0013](0013-backend-grenzen.md) Entscheidung 6 sagt: die Klassen der Werte gelten auch für `QueryParameter.value` und `OutputParameter.value`, „Letzteres bleibt bis zur Abbildung abgelehnt (SPEC-031)“. Die Spezifikation sagt jetzt: `OutputParameter` lehnt nur die Antwort auf einen Batch ab, in der Antwort auf einen RPC wird er abgebildet (RETURNVALUE; `SPEC-002`-Absatz zu Klassen, `SPEC-031`). Gebaut ist die Abbildung im RPC (Closure des Slice für mehrteilige Antworten und Ausgabewerte, Tests `ResponseRpcTest`, `AnswerFormsDispatchTest`; Handvektoren, nicht am echten Server gemessen). Der Halbsatz kann als zeitlich bedingt gelesen werden („bis zur Abbildung“), sagt aber ohne Einschränkung „Letzteres“ für beide Antwortformen; die Closure hat ihn als Spannung geführt.

**(b) Zuschnitt der Zustandsaufzählung.** [ADR-0004](0004-session-zustandsautomat.md) führt in „Nicht entschieden“ und „Offen“ den Zuschnitt der Zustände offen („flach oder getrennt nach Verbindungs- und Anfragezustand“). [SPEC-001](../../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung) legt „flach“ fest (Setzung des Nutzers, 2026-10-30, für den Anfragezustand nicht gemessen): eine Aufzählung führt Verbindungs- und Anfragezustände, `READY` ist der Anfragezustand `IDLE`, der Anfragezustand läuft `IDLE → RUNNING → STREAMING` und endet mit dem Ende der Antwort in einem Übergang nach `IDLE` oder `CLOSED`; `COMPLETED` ist kein Zustand der Aufzählung. Der Matrixtest des Baus (Closure des Slice für den SQL_BATCH-Dispatch) führt 126 Zellen: 32 zulässig, 6 ignoriert, 88 unzulässig. Der Spike von ADR-0004 maß eine flache Aufzählung (Option A); getrennte Aufzählungen sind dort nicht gebaut.

## Entscheidung

Wir wählen **Option A** für beide Punkte.

1. **`OutputParameter` im RPC (zu 11a).** Die Antwort auf einen RPC bildet `OutputParameter` ab (RETURNVALUE mit Ordinal nach [ADR-0005](0005-rpc-und-prepared-statements.md) und `SPEC-030`); die Antwort auf einen Batch lehnt `OutputParameter` und `ReturnStatus` weiter ab (`SPEC-031`, Typabbildungsfehler mit der dort geführten Verbindungsfolge). Diese ADR **ersetzt** damit den Halbsatz in [ADR-0013](0013-backend-grenzen.md) Entscheidung 6. Grund: [TDS-FA-10.a](../../../spec/spezifikation.md#tds-fa-10a--mehrteilige-antworten-und-rpc-ausgabewerte) ist auf RPC-Ausgabewerte zugeschnitten (Überschrift), die Abbildung ist gebaut und getestet, und ein Batch hat im Clientprofil keine OUTPUT-Parameter (ADR-0005: Ausgabewerte kommen aus RPC; für Batch ist keine Messung geführt).
2. **Zuschnitt (zu 11b).** Die Zustandsaufzählung ist **flach**, wie `SPEC-001` sie festlegt. Der Sitzungsrahmen hält den Zustand und entscheidet jeden Übergang atomar je Sitzung ([ADR-0004](0004-session-zustandsautomat.md) Option A mit Sperre je Sitzung). Diese ADR schließt damit den offenen Punkt „Zuschnitt“ in ADR-0004; sie widerspricht keiner Entscheidung dort. Damit ist auch die dort offene „endgültige Aufzählung“ geschlossen, soweit sie den Zuschnitt betrifft: die Aufzählung ist die gebaute (`ConnectionState`: `CONNECTED`, `PRELOGIN`, `TLS_NEGOTIATION`, `LOGIN7`, `AUTHENTICATING`, `READY`, `RUNNING`, `STREAMING`, `CLOSED`), ohne `COMPLETED` und ohne `CANCELLING`, mit `AUTHENTICATING`. Ein eigener Zustand `CANCELLING` ist nicht verworfen, sondern gehört zum offenen Punkt ATTENTION (siehe unten und Re-Evaluierungs-Trigger). Die übrigen offenen Punkte von ADR-0004 (unzulässiger Übergang, ATTENTION im Handshake und in jedem Anfragezustand, Sitzungszustand nach Fehler, `RESETCONNECTION`, Zeitpunkt des Schließens nach `TDS_SHUTDOWN`) bleiben offen und sind nicht Gegenstand.
3. **Kennzeichnung.** Beide Festlegungen sind **Setzungen**, nicht gemessen: Die Abbildung von `OutputParameter` ist ohne Messung am echten Server (Reihenfolge RETURNVALUE/RETURNSTATUS und COLMETADATA hinter RETURNVALUE sind Spec-Lücken 2 und 3, Träger: Slice für den Vergleichslauf D); der flache Zuschnitt ist für den Anfragezustand nicht gemessen.

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| **A — Teil-Supersede für 11a, Schärfung für 11b (gewählt)** | Spec, Code und Entscheidung sagen dasselbe; ADR-0013 und ADR-0004 bleiben unverändert; die Ablösung ist auf einen Halbsatz begrenzt | eine ADR mehr; Leser von ADR-0013 müssen den Hinweis in dieser ADR und im Titel ihres Index-Eintrags finden (ADR-0013 trägt keinen Zeiger, weil sie unverändert bleibt) |
| B — Spec an die ADRs angleichen (`OutputParameter` auch im RPC ablehnen; Zuschnitt „offen“) | ADR-Text bleibt wörtlich wahr | nimmt eine gebaute, getestete Abbildung zurück und widerspricht [ADR-0005](0005-rpc-und-prepared-statements.md) (RETURNVALUE für OUTPUT) und [TDS-FA-10](../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte); die Spec rangiert über der ADR |
| C — Spannung als Setzung stehen lassen | kein Dokument | jeder Lauf liest zwei Aussagen und muss selbst entscheiden; die Closures führen sie schon zweimal als „Folge-ADR nötig“ |
| D — getrennte Aufzählungen (Verbindungszustand, Anfragezustand) | Anfragezustände nur nach dem Login im Typ ausgedrückt (abgeleitet) | Umbau des gebauten, getesteten Automaten; zwei Automaten mit Zusammenspiel; kein Beleg, dass der flache Zuschnitt scheitert |

## Konsequenzen

- Positiv: Zwei Spec-Lücken der Closures haben einen Träger; Spec und ADR-Bestand widersprechen sich an diesen Stellen nicht mehr.
- Negativ: Der Halbsatz in ADR-0013 bleibt im Text stehen und ist nur über diese ADR als abgelöst erkennbar; der Index hat keine Supersedes-Spalte (ID, Titel, Status, Bezug); den Hinweis trägt der Titel des Index-Eintrags dieser ADR („ersetzt teilweise“), und ADR-0013 trägt keinen Zeiger.
- **Folgepflicht (nach `Accepted`):** Die Spezifikation trägt beide Aussagen bereits (`SPEC-031`, `SPEC-001`); der Spec-Nachzug beschränkt sich auf Kennzeichnung der Herkunft („Nutzerentscheidung“ durch den Verweis auf diese ADR) und auf die Zeile in den Sichten (`ARC-004` bleibt ohne ADR-Bezug, [AGENTS.md](../../../AGENTS.md) §3.4). Kein Code, kein Slice nötig. Träger: der Planner schließt die Spec-Lücke 1 der Closure des Slice für mehrteilige Antworten und Ausgabewerte und den Eintrag „Folge-ADR nötig“ des Slice für den SQL_BATCH-Dispatch mit Verweis auf diese ADR.
- **Akzeptiertes Negativ (einmalig):** Ob ein echter SQL Server `OutputParameter` in der Antwort auf einen Batch tatsächlich nie liefert, ist nicht gemessen; die Ablehnung im Batch ist Setzung.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| JVM-Tests (`kotlin.test`) | Der RPC bildet `OutputParameter` als RETURNVALUE ab, der Batch lehnt ihn ab; der Matrixtest des Automaten deckt alle Zustände × Ereignisarten der flachen Aufzählung. | `make test` (gebaut: `ResponseRpcTest`, `AnswerFormsDispatchTest`, Matrixtest; der Nachweis der Zellenzahl 126 steht in der Closure des Slice für den SQL_BATCH-Dispatch, hier nicht erneut geprüft) |

## Offen

Nichts zu den zwei Punkten. Nicht Gegenstand und weiter offen: Reihenfolge der Tokens und COLMETADATA nach RETURNVALUE am echten Server (Spec-Lücken 2 und 3), die in „Entscheidung“ 2 genannten offenen Punkte von ADR-0004.

## Re-Evaluierungs-Trigger

Eine Messung am echten Server, nach der der Batch `OutputParameter` kennt oder der RPC ihn anders abbildet; ein Anfragezustand, der die flache Aufzählung nicht mehr lesbar hält (etwa der Bau von `CANCELLING` für ATTENTION in jedem Anfragezustand) oder ein Messergebnis zu unzulässigen Übergängen, das den Zuschnitt widerlegt.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-10-31 | Proposed – Folge-ADR (Entscheidung des Nutzers 2026-10-31, Empfehlung übernommen); Teil-Supersede von ADR-0013 Entscheidung 6, Schließen eines offenen Punktes von ADR-0004; Status bleibt Proposed bis Review und Annahme durch den Nutzer | [ADR-0004](0004-session-zustandsautomat.md), [ADR-0013](0013-backend-grenzen.md) |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
