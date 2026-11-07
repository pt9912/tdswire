# ADR-0039: Abschluss der Antwort auf eine RPC-Nachricht, die der Parser abweist

**Status:** Accepted

**Datum:** 2026-11-06

**Autor:** Architect (Entwurf); Entscheidung: pt9912 (Nutzer)

**Bezug:** [TDS-FA-10](../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-FA-06](../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0007](0007-fehler-und-attention.md), [ADR-0038](0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md)

**Schärft:** [ADR-0038](0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) Entscheidung 1 und 5 (Abschluss der Antwort auf eine ungültig kodierte RPC-Nachricht); über sie [SPEC-030](../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten) (Abschluss der RPC-Antwort im Fehlerfall). `SPEC-011` und `SPEC-013` stehen als Zeilen in [§4 der Spezifikation](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder). **Supersedes (teilweise), nur wörtlich benannt; die Datei des Vorgängers bleibt unverändert und `Accepted` ([AGENTS.md](../../../AGENTS.md) §3.5):** [ADR-0038](0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) Entscheidung 1, **nur** für Fehler, die entstehen, bevor die Nachricht als RPC mit bekannter Prozedur gelesen ist (Struktur- und Kodierfehler, `SPEC-011`): für sie gilt nicht „Abschluss ist DONEPROC, nie DONE“, sondern ERROR und DONE wie beim SQL_BATCH. Es bleiben: DONEPROC für jeden Fehler nach dem Erkennen der Prozedur und alle übrigen Entscheidungen von ADR-0038 (wie [ADR-0037](0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md) es für [ADR-0036](0036-handle-freigabe-und-grenze-je-sitzung.md) tut).

---

## Kontext

**Anlass.** Der Review des Bau-Slice zur Angleichung der Antwort-Tokens an die Messung (Befund F-1, HIGH; der Report liegt zum Zeitpunkt dieser ADR nur im Zweig `worktree-agent-ab976abaf054dbe74`, nicht auf `main`) fand: [ADR-0038](0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) Entscheidung 1 legt den Abschluss **jeder** RPC-Antwort im Fehlerfall auf DONEPROC; der Bau schreibt die Antwort auf eine Nachricht, die der Parser abweist (`SPEC-011`, `RpcBinding.Rejected` mit `error`), weiter als ERROR und DONE. Die Spezifikation hat das als „nicht gemessen; Setzung“ nachgezogen, ohne Folge-ADR. Der Bau weicht damit vom Wortlaut einer angenommenen ADR ab und darf nicht ohne tragende Entscheidung stehen bleiben (Konflikt-Pfad: entweder gilt die ADR und der Bau ändert sich, oder eine Folge-ADR löst Entscheidung 1 für diesen Bereich ab).

**Textlage.** Entscheidung 1 nennt als Geltung „ein `Failure` im Strom, eine Ablehnung vor dem Backend (`SPEC-013`), `TDS_INTERNAL_ERROR`, `TDS_SHUTDOWN`, soweit überhaupt ERROR und Abschluss geschrieben werden“ und sagt, die Antwort auf einen SQL_BATCH bleibe ERROR und DONE. Der Parserfehler im RPC steht nicht in dieser Aufzählung. Entscheidung 5 sagt: die ADR ändere die Trennlinie zwischen `SPEC-011` und `SPEC-013` nicht, „sie legt nur den Abschluss fest“. Beides lässt sich so lesen, dass der Abschluss auch für `SPEC-011` DONEPROC ist (Wortlaut „Abschluss ist DONEPROC, nie DONE“) oder dass `SPEC-011` unberührt bleibt (Aufzählung). Diese ADR beseitigt die Zweideutigkeit; sie ändert ADR-0038 nicht.

**Heutiger Stand (Code, aus dem Review-Zweig gelesen).** `RequestLoop.answerRpc`: eine `Rejected`-Bindung mit Parserfehler wird über `errors.protocol(it)` beantwortet (Form BATCH: ERROR, DONE mit Fehlerstatus); eine Ablehnung ohne Parserfehler (`SPEC-013`) über `errors.code(TDS_UNSUPPORTED_RPC, form = ResponseForm.RPC)` (ERROR, DONEPROC). `TDS_PROTOCOL_ERROR` hat Class 20 und schließt die Verbindung (`SPEC-011`; Fehler-Mapping: Nummer 50006 Festlegung, Class und Folge Setzung des Nutzers).

**Beleg und Gegenprobe (Reviewer, Einzelläufe 2026-11-06).**

| Nr | Befund | Quelle |
|---|---|---|
| P1 | Mit `form = RPC` für Parserfehler wird in `make kompatibilitaet KATEGORIE=Luecken` genau ein Test rot: `Mehrere_RPCs_in_einer_Nachricht_enden_mit_Protokollfehler_und_geschlossener_Verbindung` (`SqlDataAdapter`, `UpdateBatchSize` 2); der Referenzclient liefert `InvalidOperationException` statt `SqlException` 50006 | Review F-1, Gegenprobe, ein Lauf |
| P2 | Der `xml`-Parameter-Fall (ein RPC, `TDS_UNSUPPORTED_TYPE` 50003, Class 20) bleibt mit `form = RPC` grün (`SqlException` 50003, Class 20, Verbindung `Closed`) | Review F-1, Gegenprobe, ein Lauf |
| P3 | Am echten SQL Server tritt der Mehrfach-RPC **nicht** als Parserfehler auf; `UpdateBatchSize` 2 ist dort ein gültiges Verfahren des Referenzclients. Der Test pinnt das Verhalten dieses Servers, keinen Messbefund | Review F-1; Kompatibilitätsmatrix, Lücke 4 (das Batch-Flag der Nachricht ist „aus der Wirkung geschlossen, am Draht nicht gesehen“) |
| P4 | Eine am echten Server **gemessene** Antwort auf eine ungültig kodierte RPC-Nachricht gibt es nicht; die Szenarien W-1 bis W-9 der [Messreihe am Draht](../../messungen/referenzabgleich.md#messreihe-am-draht-messungen-vom-2026-11-06) enthalten keinen Parserfehler (der Referenzclient kodiert keine ungültigen Nachrichten) | Messprotokoll, Messreihe vom 2026-11-06 |
| P5 | Der Mechanismus hinter P1 ist **nicht untersucht**: Die Verbindung wird bei Class 20 schon heute geschlossen (`SPEC-011`); warum der Client bei DONEPROC statt ERROR und DONE eine `InvalidOperationException` statt der `SqlException` wirft, ist nicht gemessen. Insbesondere ist **nicht gezeigt**, dass ein Verbindungsschluss nach der Antwort die `SqlException` zurückbringt | keine Messung |

**Randbedingungen.** Grundsatz des Nutzers (2026-11-06): die Spezifikation ist Zielbild, der echte SQL Server die Referenz, wo gemessen. Für Parserfehler im RPC gibt es keine Referenz (P4); für SQL_BATCH misst [ADR-0007](0007-fehler-und-attention.md) R01 und R05 bis R07 ERROR und DONE, R02 ein DONEPROC mit Fehlerstatus bei einem als Prozedurname gelesenen Batch (anderer Fall: Fehler nach dem Erkennen als Prozedur). Eine Setzung ist zulässig, wenn sie als Setzung steht (AGENTS.md §3.8).

## Entscheidung

Wir wählen **Option B** (Nutzerentscheid vom 2026-11-07, bestätigt die Empfehlung des Architects): Die Trennlinie wird festgeschrieben, als **Setzung, nicht als Messbefund** (P4: eine Antwort des echten Servers auf eine ungültig kodierte RPC-Nachricht ist nicht gemessen; `AGENTS.md` §3.8).

1. **Struktur- und Kodierfehler** (Fehler, die entstehen, bevor die Nachricht als RPC mit bekannter Prozedur gelesen ist, `SPEC-011`) enden wie die Antwort auf einen SQL_BATCH: ERROR und DONE mit Fehlerstatus.
2. **Fehler nach dem Erkennen der Prozedur** (`SPEC-013`, Fehler des Backends, interne Fehler) enden auf DONEPROC ([ADR-0038](0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) Entscheidung 1 bis 4 unverändert).
3. **Mehrfach-RPC** (mehrere RPCs in einer Nachricht) wird der Seite ERROR und DONE zugeordnet: der Server liest die Nachricht als Ganzes nicht (erste Prozedur erkannt, Rest nicht lesbar). Am echten Server ist der Mehrfach-RPC gültig (P3); die Ablehnung ist eine Fähigkeitsgrenze dieses Servers. Die Unterstützung mehrerer RPCs ist ein eigenes Planungsthema ([slice-mehrfach-rpc-unterstuetzen](../planning/open/slice-mehrfach-rpc-unterstuetzen.md)).
4. **Kein Messauftrag.** Der optionale Messauftrag zu B entfällt (Nutzerentscheid 2026-11-07); die Linie bleibt Setzung, das Negativ ist akzeptiert, solange kein Verbraucher die Form braucht.
5. **Kein Bau, kein Test:** der Code (`RequestLoop.answerRpc`) folgt der Linie schon; der Test `Mehrere_RPCs_in_einer_Nachricht_enden_mit_Protokollfehler_und_geschlossener_Verbindung` bleibt grün.

## Verglichene Alternativen

| Option | Inhalt | Pro | Contra |
|---|---|---|---|
| A — DONEPROC für jeden Abschluss durchsetzen | `RequestLoop.answerRpc` schreibt auch den Parserfehler mit `form = RPC`. Der e2e-Test bleibt auf `SqlException` 50006; der Fehlerweg wird so gebaut, dass der Client sie wieder bekommt (zu prüfen: Verbindungsschluss unmittelbar nach der Antwort, Reihenfolge von Schreiben und Schließen) | folgt dem Wortlaut von ADR-0038 Entscheidung 1; **eine** Regel („RPC-Antwort endet auf DONEPROC“) ohne Sonderfall nach Fehlerursache; keine Ablösung von ADR-0038, nur Bau und Spec-Rückbau von „endet dagegen in ERROR und DONE“ | Wirksamkeit **nicht gezeigt** (P5): kann sein, dass der Client bei DONEPROC im Batch-Modus nie eine `SqlException` liefert, dann bleibt der Test rot oder wird abgeschwächt (Gate-Lockerung braucht eine ADR, AGENTS.md §3.6); die Regel stützt sich auf keine Messung (P4), sie verallgemeinert M1 bis M6 aus ADR-0038 auf einen Fall, den kein Messpunkt zeigt; der Mehrfach-RPC ist am echten Server gültig (P3), der Test pinnt eine Setzung und löst dafür Bauaufwand aus |
| B — Trennlinie festschreiben | Fehler, die entstehen, **bevor** die Nachricht als RPC mit bekannter Prozedur gelesen ist (Struktur- und Kodierfehler, `SPEC-011`), enden wie die Antwort auf einen SQL_BATCH: ERROR und DONE; Fehler **nach** dem Erkennen der Prozedur (`SPEC-013`, Backend-Fehler, interne Fehler) enden auf DONEPROC. Messauftrag am echten Server (siehe Konsequenzen). Ersetzt ADR-0038 Entscheidung 1 für die erste Klasse | **kein Bau**: der Code folgt der Linie schon; der grüne Test bleibt; die Linie ist eine Aussage über den Ort des Fehlers beim Lesen der Nachricht und fällt mit der Trennung `SPEC-011`/`SPEC-013` zusammen; als Setzung gekennzeichnet; der gemessene Fall R02 (Fehler nach dem Erkennen als Prozedur: DONEPROC) passt zur Linie | Zwei Abschlussformen für RPC-Nachrichten; die Linie ist nicht gemessen (P4), ohne Messauftrag bleibt sie Setzung; der Mehrfach-RPC liegt an der Linie (erste Prozedur erkannt, Rest nicht lesbar) und braucht eine ausdrückliche Zuordnung (Vorschlag: ERROR und DONE, weil der Server die Nachricht als Ganzes nicht lesen kann); Teil-Ablösung von ADR-0038 Entscheidung 1 |
| C — Mehrfach-RPC als eigener Fall | Alle Parserfehler im RPC folgen A (DONEPROC). Nur die Ablehnung einer Nachricht mit mehreren RPCs wird als **eigene, benannte Ausnahme** geführt (ERROR und DONE, Class 20, Verbindung schließt), mit der Begründung P3: der echte Server nimmt die Nachricht an, die Ablehnung ist eine Fähigkeitsgrenze dieses Servers, kein Kodierfehler; sie läuft aus, sobald mehrere RPCs unterstützt werden | A bleibt für den gemessenen Kern (Parserfehler eines RPC, P2 grün) gültig; die einzige betroffene Stelle trägt einen sichtbaren Grund; verschwindet mit der Unterstützung des Batch-Flags | Eine Ausnahme mit Einzelfall-Beleg (P1 ist ein Lauf); Parserfehler der übrigen Klassen sind weiter ungemessen, „DONEPROC für den Rest“ ist Setzung ohne Messung (P4) mit dem Wagnis aus P5; die Ausnahme braucht einen Träger für das Auslaufen (Folge-Slice „mehrere RPCs unterstützen“ oder Register-Eintrag), sonst bleibt sie dauerhaft; das Auslaufen setzt eine Fähigkeit voraus, die kein Slice plant |
| D — nichts tun, Abweichung stehen lassen | Bau und Spec bleiben, ADR-0038 unverändert | kein Aufwand | widerspricht dem Wortlaut einer angenommenen ADR ohne Entscheidung (F-1); der Konflikt-Pfad kennt dieses Verdikt nicht; **nicht wählbar** |

## Empfehlung des Architects

**Option B**, mit der ausdrücklichen Zuordnung des Mehrfach-RPC zur Seite ERROR und DONE.

1. **Aussage ≤ Beleg.** Für die Klasse „Parserfehler im RPC“ gibt es keine Messung (P4). Option A verallgemeinert M1 bis M6 auf diese Klasse und trägt zusätzlich ein ungeklärtes Bauwagnis (P5); Option C trägt dieselbe Verallgemeinerung für den Rest. Option B behauptet nur die Linie, die der Bau schon hat und die zur Trennung der Spezifikation passt, und kennzeichnet sie als Setzung.
2. **Geringster Zusatzaufwand, der real trägt.** B ändert keinen Quelltext und keinen Test; A verlangt Bau mit offenem Ausgang, C zusätzlich einen Träger für das Auslaufen der Ausnahme.
3. **Der auslösende Fall hat keine Referenz.** Am echten Server ist der Mehrfach-RPC kein Fehler (P3). Der Test pinnt unser Verhalten; die ehrliche Lösung des Mehrfach-RPC ist seine Unterstützung, nicht die Feinform seiner Ablehnung. Wird er unterstützt, entfällt der Fall (Re-Evaluierungs-Trigger).
4. **Der Konflikt-Pfad bleibt eine Sequenz.** Verdikt: „die Entscheidung wird per Folge-Entscheidung für einen Teilbereich abgelöst“ (B). Der Befund F-1 bleibt bis zur Annahme offen.

Gewichtet der Nutzer „eine Regel ohne Sonderfall“ höher als den Aufwand, ist **A** die zweite Wahl, aber erst nach einem Vorversuch, der P5 klärt (siehe Konsequenzen). C empfehle ich nicht: es verbindet die Verallgemeinerung von A mit einer Sonderregel und einem Träger.

## Konsequenzen

Gemeinsam, unabhängig von der Wahl:

- `SPEC-030` nennt die Linie als Festlegung (Setzung, nicht gemessen) und ordnet den Mehrfach-RPC der Seite ERROR und DONE zu; die Spezifikation trägt keinen Verweis auf diese ADR (Sicht- und Vertragsstraten nennen keine ADR, `AGENTS.md` §3.4 für die Sicht, die ADR deklariert ihr `Schärft`-Feld).
- Die angenommene [ADR-0038](0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) bleibt unverändert (AGENTS.md §3.5).
- Betroffen sind nur Nachrichten, die der Parser abweist; `SPEC-013`, Backend-Fehler und interne Fehler bleiben DONEPROC (ADR-0038 Entscheidung 1).

Je Option:

- **A.** Bau: `answerRpc` mit `form = RPC` für Parserfehler; Fehlerweg so, dass der Client eine `SqlException` bekommt. **Prüfauftrag vor Annahme (Vorversuch, kein Bau):** `make kompatibilitaet KATEGORIE=Luecken` mit der Änderung, Meldungstext der `InvalidOperationException` lesen, Verbindungsschluss vor und nach dem Schreiben ausprobieren; Ergebnis als Messzeile in die ADR. Gelingt es nicht, fällt A weg oder verlangt eine ADR zur Test-Abschwächung. Spec: „endet dagegen in ERROR und DONE“ entfällt.
- **B (gewählt).** Die Annahme trägt `Supersedes (teilweise) ADR-0038 Entscheidung 1` für Struktur- und Kodierfehler. Spec: die Zeile in `SPEC-030` nennt die Linie und die Zuordnung des Mehrfach-RPC. Ein Messauftrag entfällt (Nutzerentscheid 2026-11-07): eine ungültig kodierte RPC-Nachricht per Rohbytes-Client gegen den echten Server zu senden, stünde in keinem Verhältnis zum Nutzen (Parserfehler kommen von Clients, die ungültige Nachrichten senden, nicht von den Referenzclients); akzeptiertes Negativ, solange kein Verbraucher die Form braucht. Kein Bau, kein Test.
- **C.** Wie A, plus: eine benannte Ausnahme im Code und in der Spezifikation für den Mehrfach-RPC, ein Träger für ihr Auslaufen, der Prüfauftrag aus A für den Rest.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| e2e (Referenzclient) | `Mehrere_RPCs_in_einer_Nachricht_enden_mit_Protokollfehler_und_geschlossener_Verbindung` und der `xml`-Parameter-Fall bleiben grün: `SqlException` 50006 bzw. 50003, Class 20, Verbindung `Closed` | `make kompatibilitaet KATEGORIE=Luecken` (kein Gate, ADR-0022) |
| JVM-Tests (`kotlin.test`) | je Klasse der gewählten Linie ein Test, der die Form des Abschlusses (DONE oder DONEPROC) der Antwort auf eine abgewiesene Nachricht festlegt | `make test` |

## Offen

- Soll der Mehrfach-RPC (am echten Server gültig) unterstützt werden? Das löst die Frage für diesen Fall auf; Träger: [slice-mehrfach-rpc-unterstuetzen](../planning/open/slice-mehrfach-rpc-unterstuetzen.md) (`open/`, Eingang: am echten Server ist `UpdateBatchSize 2` gültig).
- Nicht gemessen: jede Antwort des echten Servers auf eine ungültig kodierte RPC-Nachricht (P4); der Mechanismus hinter P1 (P5).

## Re-Evaluierungs-Trigger

Eine Messung einer ungültig kodierten RPC-Nachricht am echten SQL Server; die Unterstützung mehrerer RPCs in einer Nachricht (der Auslöser entfällt); ein Referenzclient, der bei der gewählten Form des Abschlusses die Verbindung oder die Ausnahme anders zeigt als in P1 und P2; eine Änderung der Trennlinie `SPEC-011`/`SPEC-013`.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-06 | Proposed – Entwurf zum Review-Befund F-1 des Bau-Slice zur Angleichung der Antwort-Tokens an die Messung; Wahl steht aus | [ADR-0038](0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) |
| 2026-11-07 | Accepted – Option B (Trennlinie als Setzung, Mehrfach-RPC auf der Seite ERROR und DONE, kein Messauftrag); Quelle: Nutzerentscheid | — |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
