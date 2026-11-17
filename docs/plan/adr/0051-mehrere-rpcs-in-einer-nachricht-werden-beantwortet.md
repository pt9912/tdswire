# ADR-0051: Mehrere RPCs in einer Nachricht werden beantwortet

**Status:** Proposed

**Datum:** 2026-11-17

**Autor:** Architect (Entwurf); Entscheidung: pt9912 (Nutzer), steht aus

**Bezug:** [TDS-FA-04](../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-10](../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-FA-06](../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0005](0005-rpc-und-prepared-statements.md), [ADR-0006](0006-backend-vertrag-und-streaming.md), [ADR-0038](0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md), [ADR-0039](0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md), [ADR-0036](0036-handle-freigabe-und-grenze-je-sitzung.md)

**Schärft:** [SPEC-030](../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten) (Abschnitt „Mehrere RPCs in einer Nachricht“: Trennbyte, Grenzen, Antwortform, Fehler im Aufruf k, Ablehnung von `sp_prepexec` und `sp_execute`, Aufrufe am Backend-Port); [SPEC-011](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) und [SPEC-013](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) stehen als Zeilen in §4 der Spezifikation (der Mehrfach-RPC ist kein Parserfehler mehr; die Ablehnung eines Aufrufs ist `SPEC-013`).

**Supersedes (teilweise), nur wörtlich benannt; die Datei des Vorgängers bleibt unverändert und `Accepted` ([AGENTS.md](../../../AGENTS.md) §3.5):** [ADR-0039](0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md) **Entscheidung 3** („Mehrfach-RPC wird der Seite ERROR und DONE zugeordnet“) vollständig und den Satz in **Entscheidung 5**, der Test `Mehrere_RPCs_in_einer_Nachricht_enden_mit_Protokollfehler_und_geschlossener_Verbindung` bleibe grün. Es bleiben: Entscheidung 1 (Struktur- und Kodierfehler enden auf ERROR und DONE), Entscheidung 2 (Fehler nach dem Erkennen der Prozedur enden auf DONEPROC), Entscheidung 4 (kein Messauftrag zur ungültig kodierten RPC-Nachricht) und der Rest von Entscheidung 5 (der Code folgt der Linie von Entscheidung 1 und 2). Ebenso unberührt bleiben [ADR-0038](0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) und [ADR-0005](0005-rpc-und-prepared-statements.md) (der Katalog der Prozeduren).

---

## Kontext

**Anlass.** Der Review des Bau-Slice zur Unterstützung mehrerer RPCs in einer Nachricht (Befund F-1, MEDIUM; Quelle: der Review-Report unter `docs/reviews/` zum Stand `b898101e`) fand: [ADR-0039](0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md) ist `Accepted` und ordnet in Entscheidung 3 den Mehrfach-RPC der Seite ERROR und DONE zu; in Entscheidung 5 verlangt sie, der Test zur Ablehnung bleibe grün. Spezifikation und Code sagen seit dem Bau das Gegenteil (die Nachricht wird angenommen und beantwortet), und der Diff trägt keine Folge-ADR. Die ADR selbst benannte den Auslöser: Unterstützung mehrerer RPCs entfällt als Grund der Zuordnung (Abschnitte „Offen“ und „Re-Evaluierungs-Trigger“ von ADR-0039). Der Nutzer entschied am 2026-11-17: Folge-ADR als `Proposed`.

**Was gemessen ist.** Das Messprotokoll ([Mehrere RPCs in einer Nachricht, 2026-11-17](../../messungen/referenzabgleich.md#mehrere-rpcs-in-einer-nachricht-messungen-vom-2026-11-17)) enthält vier Szenarien mit dem Referenzclient (`SqlDataAdapter`, `UpdateBatchSize` 2 und 3, `sp_executesql` mit `INSERT`), je eine Einzelmessung an SQL Server 2022 und 2025, beide Instanzen gleich:

| Nr | Befund | Quelle |
|---|---|---|
| W-10 | Eine Nachricht trägt alle Aufrufe einer Gruppe; zwischen den Aufrufen steht das Byte `0xFF`, danach der nächste Aufruf mit eigener ProcID, eigenen Optionsbits und eigenen Parametern, ohne zweites ALL_HEADERS. Bei drei Zeilen und `UpdateBatchSize` 2 gehen zwei Nachrichten (zwei Aufrufe, dann einer). Das Byte `0x80` kam in keiner Nachricht vor. Je Nachricht eine Antwortnachricht | Messprotokoll W-10 |
| W-11 | Tokenfolge `DONEINPROC RETURNSTATUS DONEPROC` je Aufruf, hintereinander; DONEPROC des ersten Aufrufs `MORE\|0x80` (Statusbits `0x0081`, Befehl `0x00e0`), DONEPROC des letzten ohne Bits | Messprotokoll W-11 |
| W-12 | Mit Ausgabewert je Aufruf `DONEINPROC DONEINPROC RETURNSTATUS RETURNVALUE DONEPROC`; das Ordinal der RETURNVALUE ist in beiden Aufrufen 4, zählt also je Aufruf von vorn (die Stelle von `@N` in der Aufrufliste von `sp_executesql`) | Messprotokoll W-12 |
| W-13 | Schlüsselverstoß (2627, Class 14) im zweiten von drei Aufrufen: ERROR, INFO 3621, DONEINPROC `MORE\|ERROR`, RETURNSTATUS 2627, DONEPROC `MORE\|0x80` ohne Fehlerbit; der dritte Aufruf läuft und ist im Bestand; die Verbindung bleibt offen | Messprotokoll W-13 (Serverteil aus den abgelegten Bytes; der Clientteil „`SqlException` 2627“ stammt aus der Beobachtungszeile des Laufs, nicht aus der Ablage) |

**Grenzen der Messung** (Messprotokoll, Absatz „Grenzen“): Einzelmessungen, ein Host, ein Client in einer Version; nur `sp_executesql` mit `INSERT`, höchstens drei Aufrufe je Nachricht; **nicht gemessen** sind das Trennbyte `0x80`, Aufrufe mit Ergebnismengen, `sp_prepexec` und `sp_execute` in einer Nachricht, der RPC per Prozedurname in einer Folge, ein Fehler im ersten oder letzten Aufruf, ein Fehler vor dem ersten Ergebnis eines Aufrufs, der nicht der letzte ist, Abbruch und Frist mitten in der Nachricht, `ContinueUpdateOnError`. Die Zahl der Parameter oder Aufrufe je Nachricht, bei der der echte Server abweist, ist nicht gemessen.

**Stand von Spezifikation und Code** (als Ist-Stand gelesen, nicht neu geprüft): Die Spezifikation (`SPEC-030`, Zeile „Mehrere RPCs in einer Nachricht“) führt die Punkte der Entscheidung unten, die Messung und Setzung getrennt nennen. Der Port (`QueryBackend.execute`) trägt weiter einen `QueryRequest` je Aufruf.

**Befunde beim Abgleich von Spezifikation und Messung** (nicht geglättet, kein Widerspruch zur Entscheidung):

- Die Spezifikation schreibt je Aufruf die Folge „DONEINPROC, RETURNSTATUS, die RETURNVALUE und DONEPROC“. Gemessen ist das für W-11 (ohne Ausgabewert); in W-12 steht je Aufruf ein zweites DONEINPROC vor RETURNSTATUS. Die Form je Aufruf ist die der Antwort auf einen einzelnen RPC ([ADR-0038](0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md)); diese ADR legt keine eigene Tokenfolge je Aufruf fest, sondern nur, was zwischen den Aufrufen gilt (Entscheidung 2).
- „Gemessen“ ist das Ordinal je Aufruf nur an einer Stelle (4 in beiden Aufrufen); die Regel „von vorn“ ist der einfachste Schluss daraus, an anderen Positionen nicht gesondert gezeigt.
- Hinter einer ProcID außerhalb des Katalogs endet das Lesen; ein Strukturfehler in einem späteren Aufruf derselben Nachricht ergibt dann `SPEC-013` statt `SPEC-011` (Spezifikation: ausdrücklich genannt; Review F-13). Wie der echte Server das tut, ist nicht gemessen.

**Randbedingung.** Grundsatz des Nutzers (2026-11-06): die Spezifikation ist Zielbild, der echte SQL Server die Referenz, wo gemessen. Eine Setzung ist zulässig, wenn sie als Setzung steht ([AGENTS.md](../../../AGENTS.md) §3.8).

## Entscheidung

Wir wählen **Option A** (Empfehlung des Architects; Annahme durch den Nutzer steht aus). Messung und Setzung sind getrennt benannt.

1. **Annahme und Antwort (gemessen: W-10, W-11).** Eine RPC-Nachricht mit mehreren RPCs wird angenommen und mit **einer** Antwortnachricht beantwortet. Auf die Parameter eines Aufrufs folgt das Trennbyte **`0xFF`**, danach der nächste Aufruf mit eigener Prozedur, eigenen Optionsbits und eigenen Parametern, ohne zweites ALL_HEADERS.
2. **Antwortform (gemessen: W-11, W-12).** Je Aufruf die Antwort der Form RPC ([ADR-0038](0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md)), nacheinander in der Reihenfolge der Aufrufe. Das DONEPROC jedes Aufrufs außer des letzten trägt `MORE` und das Bit `0x0080` (`MORE|0x80`), das des letzten keine Bits. Das Ordinal der RETURNVALUE zählt je Aufruf von vorn.
3. **Fehler im Aufruf k, Verbindung bleibt offen (gemessen für den Fehler nach dem Beginn einer Anweisung in der Mitte, W-13).** Der Fehler beendet nur seinen Aufruf (Abschluss nach [ADR-0038](0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) mit `MORE|0x80` am DONEPROC); die folgenden Aufrufe laufen. Für den Fehler vor dem ersten Ergebnis eines Aufrufs, der nicht der letzte ist (DONEPROC mit Fehlerbit und `MORE|0x80`), gilt dieselbe Linie als **Setzung, nicht gemessen**.
4. **Fehler, der die Verbindung schließt (Setzung, nicht gemessen).** Er beendet die Nachricht: kein weiterer Aufruf erreicht das Backend, das DONEPROC trägt kein `MORE`.
5. **Grenzen (Festlegungen ohne Messung).** Der Parser liest höchstens **4096 Parameter je Nachricht** (über alle Aufrufe; Nutzerbestätigung 2026-11-17: die Grenze gilt je Nachricht, nicht je Aufruf) und höchstens **1024 Aufrufe**. Ein Überschreiten ist ein Strukturfehler (`INVALID_RPC_FORM`, `SPEC-011`) mit geschlossener Verbindung. Ein Trennbyte ohne folgenden Aufruf ist ebenfalls `INVALID_RPC_FORM`.
6. **Nur `0xFF` trennt (Festlegung, Nutzerbestätigung 2026-11-17).** Das Byte `0x80` (in MS-TDS als `BatchFlag` bezeichnet; die Bezeichnung steht ohne Quelle im Repo) ist nicht gemessen und trennt nicht: es bleibt eine Namenslänge von 128 Zeichen, mit `0xFF` ist umgekehrt eine Namenslänge von 255 nicht lesbar. Ein Client, der `0x80` als Trenner sendet, bekommt einen Strukturfehler oder falsch gelesene Parameter (Zustand wie vor der Unterstützung).
7. **`sp_prepexec` und `sp_execute` in einer Nachricht mit mehreren RPCs (Setzung, nicht gemessen und nicht gebaut).** Die ganze Nachricht ergibt `TDS_UNSUPPORTED_RPC` (`SPEC-013`, Verbindung offen); das Backend wird nicht gerufen. Dasselbe gilt für jede andere Ablehnung eines Aufrufs (ProcID oder Name außerhalb des Katalogs). Der Grund ist die Handle-Verwaltung ([ADR-0036](0036-handle-freigabe-und-grenze-je-sitzung.md)): ein Handle aus Aufruf 1 als Eingang von Aufruf 2 in derselben Nachricht ist am echten Server nicht gemessen.
8. **Form am Backend-Port (Setzung, nicht gemessen).** Das Backend sieht **einen `QueryRequest` je Aufruf**, nacheinander, nie gleichzeitig, mit **derselben `requestId`** und derselben Frist der Nachricht. Der Vertrag des Ports ([ADR-0006](0006-backend-vertrag-und-streaming.md)) bleibt in seiner Form; neu ist nur, dass dieselbe `requestId` mehrfach hintereinander auftritt; `cancel(sessionId, requestId)` trifft den laufenden Aufruf.
9. **Abbruch und Frist mitten in der Nachricht (Setzung, am Server dieser Bibliothek durch Tests belegt, nicht am echten Server gemessen).** Die Antwort der fertigen Aufrufe bleibt stehen und wird mit `DONE[ATTN]` beziehungsweise ERROR 50005 und DONEPROC mit `ERROR` ohne `MORE` beendet.
10. **Abgrenzung.** Die Trennlinie für Struktur- und Kodierfehler ([ADR-0039](0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md) Entscheidung 1) und die Linie nach dem Erkennen der Prozedur (Entscheidung 2) bleiben. Der Test mit `UpdateBatchSize` 2 ersetzt den Test der Ablehnung; ein Test, der die Ablehnung fordert, steht nicht mehr im Bestand.

## Verglichene Alternativen

| Option | Inhalt | Pro | Contra |
|---|---|---|---|
| A — wie gemessen, Festlegungen benannt (gewählt) | Entscheidung oben: `0xFF` trennt, 4096 Parameter je Nachricht und 1024 Aufrufe als Festlegungen, `sp_prepexec` und `sp_execute` abgelehnt, ein `QueryRequest` je Aufruf mit gleicher `requestId` | trägt die gemessene Form (W-10 bis W-13); kleinste Änderung am Port (keine neue Methode, keine neue Typform); jede Setzung ist benannt und hat einen Trigger; vom Nutzer für `0xFF` und 4096 bestätigt | die Lesegrenzen sind ungemessen (der echte Server kann andere Grenzen haben); `requestId` kommt mehrfach vor (ein Backend, das je Kennung genau eine Ausführung erwartet, sieht sie wiederholt); `sp_prepexec` in einer Nachricht wird abgelehnt, obwohl der echte Server sie vermutlich annimmt (nicht gemessen) |
| B — `0x80` zusätzlich als Trenner | Das Byte `0x80` (`BatchFlag` in MS-TDS) trennt ebenfalls, wo es als Trennbyte plausibel ist | ein Client, der `0x80` sendet, wird gelesen; näher an einem Protokoll, das zwei Werte kennt | keine Messung (der Referenzclient sendet `0xFF`); die Namenslänge 128 ist ein gültiger Parametername, die Lesart wird an der Stelle mehrdeutig (zwei Parameternamen der Länge 128 und 255 sind dann nicht mehr lesbar); die Quelle der Bezeichnung steht nicht im Repo; Nutzer hat am 2026-11-17 `0xFF` allein bestätigt |
| C — Grenze je Aufruf, Obergrenzen für Aufrufe und Bytes | 4096 Parameter je Aufruf statt je Nachricht; dazu Obergrenzen für die Zahl der Aufrufe und die Gesamtgröße der Nachricht | folgt der bisherigen Grenze eines Aufrufs unverändert; Bytegrenze schützt den Speicher unmittelbar | zusätzliche Festlegungen (Bytegrenze, Aufrufgrenze) ohne Messung; ein Batch mit sehr vielen Zeilen ließe die Parameterzahl der Nachricht unbegrenzt wachsen, nur durch die Aufrufgrenze und die Bytegrenze gehalten; der Nutzer hat am 2026-11-17 „4096 je Nachricht“ bestätigt |
| D — Aufrufreihe als Einheit am Port | Das Backend bekommt die ganze Reihe der Aufrufe einer Nachricht in einem Aufruf (neuer Typ, neue Methode oder Liste im `QueryRequest`) | das Backend sieht die Nachricht als Einheit (Transaktion über die Reihe, eine Ausführung je `requestId`); keine wiederholte `requestId` | ändert die öffentliche Fläche des Ports ([ADR-0006](0006-backend-vertrag-und-streaming.md), `SPEC-059`) und alle Einbettungen; die Antwort der Reihe müsste das Backend je Aufruf trennen (Streaming mit Grenzen) oder der Server müsste sie zerlegen; kein Verbraucher verlangt es; ein Aufwand ohne Messung als Grund |
| E — nichts tun, Ablehnung stehen lassen | Der Mehrfach-RPC bleibt Protokollfehler (Class 20, Verbindung geschlossen) | kein Aufwand; [ADR-0039](0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md) bliebe wahr | am echten Server ist `UpdateBatchSize` über 1 gültig (W-10); der Referenzclient scheitert am Server dieser Bibliothek; die Fähigkeitsgrenze war nur als Zwischenstand gesetzt; Spezifikation und Code stehen bereits auf A, so dass E eine Rücknahme wäre |

## Empfehlung des Architects

**Option A.**

1. **Aussage ≤ Beleg.** A trägt, was W-10 bis W-13 zeigen (Trennbyte, Antwortform, Ordinal, Fehler im mittleren Aufruf), und kennzeichnet jede Festlegung und Setzung (Grenzen, `0x80`, `sp_prepexec`, Port-Form, Verbindungsfehler, Abbruch).
2. **Geringster Zusatzaufwand, der real trägt.** B und C fügen Festlegungen ohne Messung hinzu; D ändert die öffentliche Fläche für einen Fall, den kein Verbraucher verlangt. A braucht keinen neuen Port-Typ.
3. **Nutzerbestätigung.** Zu `0xFF` allein und zu 4096 je Nachricht liegt die Bestätigung vom 2026-11-17 vor; sie ist der Grund, B und C nicht zu empfehlen.
4. **Konflikt-Pfad als Sequenz.** Verdikt: die Entscheidung wird per Folge-Entscheidung für einen Teilbereich abgelöst (Entscheidung 3 und ein Satz von Entscheidung 5 von ADR-0039). Bis zur Annahme bleibt Befund F-1 offen; das Dokument des Vorgängers wird nicht angefasst.

## Konsequenzen

- Mit der Annahme gilt `Supersedes (teilweise) ADR-0039` für Entscheidung 3 und den genannten Satz von Entscheidung 5. Bis dahin steht ADR-0039 unverändert, und der Stand von Spezifikation und Code weicht von ihr ab (Befund F-1 bleibt offen).
- `SPEC-030` führt die Entscheidung; die Zuordnung des Mehrfach-RPC zur Seite ERROR und DONE entfällt dort schon. Die Spezifikation trägt keinen Verweis auf diese ADR ([AGENTS.md](../../../AGENTS.md) §3.4); die ADR deklariert ihr `Schärft`-Feld.
- Ein Backend sieht dieselbe `requestId` mehrfach hintereinander (Entscheidung 8). Das steht in der Nutzer-Doku; **Befund:** die KDoc von `QueryRequest` und `QueryBackend.execute` nennt es laut Review nicht (Einschätzung der Port-Frage im Review). Ein Nachzug dort ist Sache des Slice, nicht dieser ADR.
- Die Festlegungen (4096 je Nachricht, 1024 Aufrufe, `0xFF` allein, Ablehnung von `sp_prepexec` und `sp_execute`) sind **akzeptierte Negative ohne Messauftrag**: der Referenzclient sendet sie nicht, ein Messauftrag stünde in keinem Verhältnis zum Nutzen. Sie fallen mit dem ersten Verbraucher oder der ersten Messung (Trigger unten).
- Der Fehler im ersten oder letzten Aufruf, Aufrufe mit Ergebnismengen und mehr als drei Aufrufe je Nachricht haben keine Messung; die Form folgt der Linie von Entscheidung 2 und 3 ohne eigenen Beleg.
- Offen und nicht Gegenstand dieser ADR: der Clientteil von W-13 („zwei Fehler“) hat keinen abgelegten Beleg (Review F-7); er trägt keine Entscheidung.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| JVM-Tests (`kotlin.test`) | Parser und Dispatch: `0xFF` trennt, `0x80` nicht; Grenzen 4096 und 1024 (an der Grenze gelingt, eins darüber nicht); `sp_prepexec` und `sp_execute` in der Nachricht ergeben `SPEC-013` ohne Backend-Aufruf; Antwortform (`MORE\|0x80` außer am letzten, Ordinal je Aufruf); Fehler im Aufruf k beendet nur diesen | `make test` |
| Belege der Messung | die abgelegten Messungen W-10 bis W-13 beider Instanzen tragen die Aussagen der Entscheidungen 1 bis 3 | `make vergleichslauf-d LAUF=wire` (kein Gate, nur lokal, EULA: Nutzer), Belegtests ohne Instanz in `make referenztest-dotnet` |
| e2e (Referenzclient) | `UpdateBatchSize` 2 gelingt gegen den Server dieser Bibliothek | `make kompatibilitaet KATEGORIE=Luecken` (kein Gate, [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md)) |

## Re-Evaluierungs-Trigger

Eine Messung, die eine Festlegung widerlegt oder ergänzt (Trennbyte `0x80`, Grenzen für Parameter und Aufrufe, `sp_prepexec` in einer Nachricht, Ergebnismengen im Aufruf, Fehler im ersten oder letzten Aufruf, Abbruch und Frist mitten in der Nachricht); ein Client, der `0x80` als Trenner sendet; ein Backend, das je `requestId` genau eine Ausführung braucht oder die Reihe als Einheit sehen will (Entscheidung 8, Option D); eine Änderung der Trennlinie `SPEC-011`/`SPEC-013`.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-17 | Proposed – Entwurf zum Review-Befund F-1 des Bau-Slice; Entscheidung des Nutzers „Folge-ADR als Proposed“; Annahme steht aus | [ADR-0039](0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md) |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
