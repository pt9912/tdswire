# Review-Report: slice-fehler-mapping — 2026-10-30

**Review-Art:** Code (gegen Plan, Entscheidungen und Hard Rules; nicht gegen die DoD, das ist die Frage des Verifiers).

**Gegenstand:** Merge `a8072b5` (Umsetzung von slice-fehler-mapping), Diff gegen den ersten Parent: `git diff a8072b5^1 a8072b5`, fünf Dateien, 551 Zeilen hinzu, 3 entfernt. Der Slice-Plan liegt unter `docs/plan/planning/in-progress/slice-fehler-mapping.md`. Neue Dateien: `ErrorCode.kt`, `ErrorMapper.kt`, `ErrorMapperTest.kt` (Package `protocol.error`); geändert: `spec/spezifikation.md` (§4 und `TDS-FA-06.a`), der Slice-Plan.

**Skill:** `.harness/skills/reviewer.md` (Stand des Arbeitsbaums) · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-10-30

**Eingangs-Kontext:**

- slice-fehler-mapping (Plan, §3 Änderungsliste, §6 Risiken)
- [ADR-0007](../plan/adr/0007-fehler-und-attention.md) (Entscheidung, Class der eigenen Codes, Grundregel A), [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) für die Schnittstelle
- [SPEC-011](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [SPEC-012](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [SPEC-013](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [SPEC-014](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [SPEC-015](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [SPEC-016](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [SPEC-048](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [SPEC-049](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [TDS-FA-06.a](../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung)
- [TDS-FA-06](../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit)
- `AGENTS.md` §3.1, §3.2, §3.7, §3.8
- Stand der parallelen Arbeit: ungetrackter Worktree `.claude/worktrees/agent-a04a04397623cd066` (Pakete `protocol/response`, nur gelesen)

**Selbst gefahrene Läufe.** Alles in Kopien des Baums von `a8072b5` (Docker-Build-Kontext, nichts gemountet, Wegwerfcode im Scratchpad), je `docker build --target test`. Jede der zehn Mutationen wurde **einzeln** gefahren; jede färbt mindestens einen Test rot (Ausgangslage grün wurde nicht neu gemessen, das ist Sache des Verifiers).

| Mutation (einzeln) | Rote Tests |
|---|---|
| DONE-Status `ERROR` statt `FINAL` | 9 Handvektor-Tests, Selbstkonsistenztest, Login-Gleichheitstest |
| Fate von `TDS_BACKEND_ERROR` auf schließt (Class 16) | Gruppentest, Schwellentest, Handvektor |
| Severity von `TDS_PROTOCOL_ERROR` 19 statt 20 | Gruppentest, Schwellentest, Handvektor |
| `TDS_SIM_INVALID_VALUE` auf Class 20 und schließt | Gruppentest, Handvektor |
| Text des Logins `Login failed` ohne Punkt | Login-Gleichheitstest, Handvektor |
| Log trägt `exception.message` | Test „Text einer Ausnahme steht weder in der Antwort noch im Log“ |
| Unbekannter Failure-Code auf `TDS_BACKEND_ERROR` statt `TDS_INTERNAL_ERROR` | Failure-Test |
| `streamSynchronous` ignoriert | Test „nicht synchroner Strom“ |
| `log.record` entfernt | drei Tests (Ausnahme/Log, Failure-Log, nicht synchroner Strom) |
| `TDS_UNSUPPORTED_TYPE`-Kategorie auf `TDS_PROTOCOL_ERROR` | Codec-Kategorie-Test |

Ergebnis: Mutationsfestigkeit der acht genannten Punkte bestätigt, einzeln und nicht nur gebündelt. Nicht mutiert und nicht abgedeckt: siehe F-4 und F-6.

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Die Schnittstelle des Mappers passt nicht zu der des parallelen Sequenzierers. `ErrorMapper.failure` und `protocol` liefern eine fertige `TdsMessage` (ERROR und DONE, eigener `TokenEncoder`); der Sequenzierer im Worktree erwartet `(Failure) -> ServerMessage` und `(ProtocolError) -> ServerMessage`, schreibt ERROR und DONE selbst in die laufende Nachricht und entscheidet das Verbindungsende aus der Severity. Szenario: Zeilen, dann Fehler in **einer** Nachricht ([ADR-0007](../plan/adr/0007-fehler-und-attention.md), Messung R03): die Einzelnachricht des Mappers lässt sich nicht an die Zeilen anhängen; bleibt nur, `ErrorCode.rule` am Mapper vorbei zu lesen, dann gibt es zwei Quellen für die Verbindungsfolge (`ConnectionFate` und Severity-Regel). | [ADR-0007](../plan/adr/0007-fehler-und-attention.md), [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), Plan §3 („Schnittstelle für den Sequenzierer“) | `ErrorMapper.kt` · `fun failure(failure: QueryEvent.Failure, streamSynchronous: Boolean = true): ErrorResponse`; Gegenseite `ResponseSequencer.kt` · `val backend: (QueryEvent.Failure) -> ServerMessage` | nein (die Gegenseite ist ungemerged; ein Integrationstest fehlt) | Schnittstelle zum Nachbar-Slice nicht abgeglichen |
| F-2 | MEDIUM | Der „Vollständigkeitstest gegen die Spezifikation“ vergleicht die Enum mit einer im Test ausgeschriebenen Namensliste, nicht mit der Tabelle in §4. Name und `@spec`-Tags (`SPEC-011` bis `SPEC-049`) behaupten mehr, als er prüft. Szenario: ein neuer Code oder eine geänderte Nummer in der Spezifikation (oder umgekehrt in der Enum, sofern der Handvektor mitgezogen wird) lässt alles grün, und die Abdeckungsmatrix zählt `SPEC-013` bis `SPEC-016` als bestanden belegt. Die KDoc benennt die Lücke ehrlich („läuft nicht: die Testumgebung enthält die Spezifikation nicht“); der Testname tut es nicht. Dass der Abgleich in Stage `test` nicht läuft, stimmt (die Stage kopiert nur `src`); die Stage `spec-abdeckung` kopiert die Spezifikation, führt aber keinen Abgleich aus. | `AGENTS.md` §3.8 | `ErrorMapperTest.kt` · `Jeder Code der Spezifikation hat genau eine Zuordnung mit eigener Nummer` | ja (Nummer in §4 ändern, `make test` bleibt grün) | Test benennt Spec-Abgleich, prüft eine Kopie |
| F-3 | MEDIUM | Die Tabelle in §4 nennt als Herkunft von Class **und** Folge „Grundregel“ für `TDS_UNSUPPORTED_TYPE`, `TDS_SIM_UNKNOWN_OBJECT` und `TDS_TIMEOUT`. [ADR-0007](../plan/adr/0007-fehler-und-attention.md) begründet mit der Grundregel A nur die Folge (der Server schließt); die Class 20 ist die Wahl des Slice. Beim Code 208 ist sie zudem nicht neutral: die Nummer ist als Vorschlag aus dem echten Server abgeleitet, der sie in Messung R05 mit Class 16 sendet; mit Class 20 sieht ein Client dieselbe Nummer in einer Klasse, bei der er die Verbindung selbst trennt (S03/S04). Die Kennzeichnung „Einzelzuordnung offen“ steht da und mildert; die Zeile sagt mehr, als der Beleg trägt. | `AGENTS.md` §3.8, [ADR-0007](../plan/adr/0007-fehler-und-attention.md) | `spec/spezifikation.md` · `Class und Folge: Grundregel, Einzelzuordnung offen` (Zeile `TDS_SIM_UNKNOWN_OBJECT`) | ja (Textabgleich ADR gegen §4) | Herkunftsstufe „Grundregel“ breiter als der Beleg |
| F-4 | LOW | `failure` bildet jeden Failure-Code ab, dessen Text ein Enum-Name ist, auch `TDS_LOGIN_FAILED`, `TDS_SHUTDOWN`, `TDS_PROTOCOL_ERROR`, `TDS_TIMEOUT`. Ein Backend, das `TDS_LOGIN_FAILED` meldet, löst ERROR 18456 mit Class 14 und Verbindungsende mitten in der Sitzung aus. Weder KDoc noch Test legen fest, ob das gewollt ist (Plan und `TDS-FA-06.a` Nr. 5 sprechen von „erwarteter Ablehnung des Backends“). | Maintainability · [TDS-FA-06.a](../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung) | `ErrorMapper.kt` · `ErrorCode.entries.firstOrNull { it.name == failure.code }` | ja (Sonde `Failure("TDS_LOGIN_FAILED", …)`; nicht gefahren, aus dem Code gelesen) | Abbildung des Backend-Codes ohne Positivliste |
| F-5 | LOW | Die Herkunftsstufen sind in Spezifikation und KDoc verschieden benannt. §4 trennt „Setzung des Nutzers“ von „Festlegung“; die KDoc der Enum nennt `TDS_PROTOCOL_ERROR` und `TDS_INTERNAL_ERROR` mit Class 20 „Festlegung“ und fasst „die Severity und die Verbindungsfolge nach den Gruppen der [ADR-0007](../plan/adr/0007-fehler-und-attention.md)“ unter Festlegung. Das ist enger als der Beleg (Setzung des Nutzers, 2026-10-30), und wer die Stufe lesen will, bekommt zwei Taxonomien. | Maintainability · `AGENTS.md` §3.8 | `ErrorCode.kt` · `Festlegung: Severity 20 nach` (KDoc von `TDS_PROTOCOL_ERROR`) | nein | Herkunftsstufen zweifach benannt |
| F-6 | INFO | Nicht getestet: `protocol` und `failure` mit `streamSynchronous = false`, und `failure` mit Code, der ein bekannter Code ist, auf `fate`. Die Mutation „ignoriere `streamSynchronous` nur in `protocol`“ wurde nicht gefahren; sie würde den Test nicht färben. Zuständig: Implementer, kein Einzelrisiko, weil alle Wege durch `response` laufen. | Maintainability | `ErrorMapperTest.kt` · `Bei nicht synchronem Strom schliesst die Verbindung ohne Antwort` | ja | Negativpfad nur über einen Einstieg |
| F-7 | INFO | `streamSynchronous` hat den Default `true`. Ein Aufrufer, der den Parameter vergisst, sendet ERROR und DONE in einen Strom, der es nicht mehr verträgt (`[TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)`). Kein Szenario im Diff; die Entscheidung liegt beim Sequenzierer-Slice (siehe F-1). | [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) | `ErrorMapper.kt` · `streamSynchronous: Boolean = true` | nein | Sicherheitsrelevanter Parameter mit Default |
| F-8 | INFO | Gemessen ([ADR-0007](../plan/adr/0007-fehler-und-attention.md), R10) ist für Class 20 und 25 ein DONE mit `ERROR` **und** `SERVER_ERROR`; der Mapper sendet für jeden Code nur `ERROR`. §4 benennt das als „nicht am Produkt gemessen“, aber nicht die Abweichung vom gemessenen Muster bei Class 20. Zuständig: Messung (`BEO-TDS/messung-am-echten-sql-server-fehlt`). | [ADR-0007](../plan/adr/0007-fehler-und-attention.md) | `spec/spezifikation.md` · `Das DONE trägt für jeden Code das Statusbit` | nein | Abweichung vom gemessenen DONE-Muster ungenannt |
| F-9 | INFO | Der Log trägt von einer Ausnahme nur den Typnamen, von einem unbekannten Failure-Code nur die Konstante `unknown-failure-code`; Stack und Code des Backends sind nicht auffindbar. §4 benennt die Einengung gegenüber `SPEC-048`. Das ist Geheimnisfreiheit auf Kosten der Diagnose; Pflicht-Logfelder (`connection_id` u. a.) trägt der Eintrag nicht, das gehört dem Aufrufer. | [SPEC-048](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit) | `ErrorMapper.kt` · `const val UNKNOWN_FAILURE_CODE = "unknown-failure-code"` | nein | Diagnose gegen Geheimnisfreiheit abgewogen |

## Einzelfragen des Auftrags

- **Herkunftsstufen.** Nummern 208/245 sind in Tabelle und KDoc als Vorschlag, 50001 als Vorschlag (Benutzerbereich), 50002 bis 50008 und alle Texte als Festlegung gekennzeichnet; die Aussage „kein Wert gemessen“ steht über der Tabelle. Das trägt. Zwei Abweichungen: F-3 (Grundregel überdehnt) und F-5 (zweifache Benennung).
- **Mutationsfestigkeit.** Siehe Tabelle oben; alle Mutanten tot, einzeln gefahren.
- **Vollständigkeitstest.** Siehe F-2: ehrlich in der KDoc, aber nicht gegen die Spezifikation und im Namen irreführend. Der Abgleich ist in Stage `test` nicht machbar, ein Ort dafür wäre die Stage mit Kopie der Spezifikation; Entscheidung beim Implementer.
- **Geheimnisfreiheit.** Antwort und Log tragen weder Ausnahmetext noch Failure-Text noch fremden Code (Test deckt ASCII und UTF-16LE; Mutation „Log mit Ausnahmetext“ ist rot). Kein Befund.
- **Class-20-Wahl.** [ADR-0007](../plan/adr/0007-fehler-und-attention.md) und `TDS-FA-06.a` setzen nur Gruppen und die Folge; Class 20 für Codes ohne Gruppe ist Wahl des Slice, in §4 als offene Einzelzuordnung geführt, die Entscheidung des Nutzers steht aus. Siehe F-3 für das Etikett.
- **§4 der Spezifikation.** Keine ADR-Kennung, keine ARC-Kennung, keine Links nach unten im Zugefügten (geprüft am Diff). Die untere Schicht präzisiert (Nummern, Texte) und führt keine Zusage ein, die im Lastenheft ohne Anker steht.
- **Hard Rules.** §3.1: nur Docker genutzt. §3.2: kein `@Suppress` im Diff. §3.7: die Kommentare tragen Zusage, Kopplung oder Grenze; kein Konjunktiv über Verworfenes. Im Slice-Plan beschreibt die Zeile „Die Entscheidung lag zum Start nicht vor“ eine Chronik im Ausgang-Feld einer Risiko-Zeile; kein Gate fängt es, die Zeile bleibt unter der Schwelle für ein Finding.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/error` (Layer: Imports `domain`, `protocol`, `transport`) | geprüft, ohne Befund außer F-1, F-4, F-7 |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/error` | geprüft, Befunde F-2, F-6 |
| `spec/spezifikation.md` (§4, `TDS-FA-06.a`, Geschichte) | geprüft, Befunde F-3, F-8; keine Abwärtslinks, keine ADR-Kennung |
| `docs/plan/planning/in-progress/slice-fehler-mapping.md` | geprüft, ohne Befund |
| `config/`, `build.gradle.kts`, `Dockerfile`, `tools/` | nicht im Diff, ohne Befund |

## Summary

Kein HIGH. Drei MEDIUM: die Mapper-Schnittstelle passt nicht zu dem, was der Sequenzierer erwartet (F-1); der Vollständigkeitstest belegt weniger, als sein Name sagt (F-2); die Herkunft „Grundregel“ in §4 deckt mehr als die Folge (F-3). Zwei LOW, vier INFO. Die geforderten Mutationen sind einzeln alle gefangen. Die Findings nehmen den Implementer-Bericht nicht als gegeben: F-1 stützt sich auf den gelesenen Stand des Worktrees, der sich noch ändern kann.
