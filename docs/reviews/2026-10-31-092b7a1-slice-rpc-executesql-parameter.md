# Review-Report: slice-rpc-executesql-parameter — 2026-10-31

**Review-Art:** Code (Diff gegen Plan, Entscheidungen und Hard Rules). Gegen die DoD wird nicht geprüft; das ist die Frage des Verifiers.

**Gegenstand:** Merge-Commit 092b7a1 („Merge slice-rpc-executesql-parameter Umsetzung“), Diff gegen den ersten Parent 81d41d5 (Stand nach dem Streaming-Slice): 15 Dateien, davon `RpcBinder`, `RpcParameters`, `RpcProcedureName`, `RpcWireValues`, `RequestLoop`, `HandshakeAutomaton`, `QueryRequest`, `spec/spezifikation.md` (SPEC-013-Zeile und Absatz „RPC im Backend-Vertrag“, Historie) und die Tests `RpcBinderTest`, `RpcDispatchTest`, `RpcWire`, `DispatchFailureTest`, `HandshakeMatrixTest`, `BackendModelTest`. Gelesen wurde zusätzlich der Gesamtdiff gegen 7aa0763.

**Skill:** `.harness/skills/reviewer.md` @ 092b7a1
**Modell:** claude-sonnet-5-5 · **Datum:** 2026-10-31

**Eingangs-Kontext:**

- Slice-Plan `slice-rpc-executesql-parameter` (Ziel, Abgrenzung, Risiken, Nutzerentscheidung vom 2026-10-31: RPC per Prozedurname als EXEC-Text, Backend-Vertrag unverändert)
- [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md), [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), [ADR-0007](../plan/adr/0007-fehler-und-attention.md)
- [TDS-FA-04](../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit)
- [SPEC-002](../../spec/spezifikation.md#spec-002--interner-query-vertrag), [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), SPEC-011, SPEC-013, SPEC-014 und SPEC-048 (Tabelle in [Abschnitt 4](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder)), [SPEC-029](../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung), [SPEC-030](../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten), [SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität)
- [AGENTS.md](../../AGENTS.md) §3.1, §3.2, §3.6, §3.7, §3.8

**Eigene Läufe (Scratchpad, Kopie des Stands 092b7a1 per git archive, je ein Docker-Build gleichzeitig, nichts gemountet, keine Last-Schleifen):**

- Handvektoren nachgerechnet mit Python (Skript im Scratchpad): 10^38−1 und 10^38 in Little-Endian, Tag 3652058 gleich 9999-12-31 (Tag 3652059 danach), 2025-01-15 gleich Tag 739265, Ticks des letzten Tages bei Scale 7 (863999999999) und Scale 0 (86399), Scale 3 (45296789), Scale 7 (37230000001), 99999 und 100000. Alle Vektoren der Tests stimmen mit der Rechnung überein. Das belegt die Arithmetik, nicht das Layout des Drahtformats (siehe F-4).
- Ausgangsstand: Stage test grün (633 Tests).
- Mutationen, jeweils einzeln und mit Rückbau der Datei. Die Nummern M1 bis M21 des Implementers liegen dem Reviewer nicht vor; es sind eigene Mutationen aus den genannten Schwerpunkten (R1 bis R12). Alle zwölf wurden von mindestens einem Test erkannt:

| Nr. | Mutation | Erkannt durch |
|---|---|---|
| R1 | Prozedurname: Semikolon in regulären Bezeichnerzeichen erlaubt | RpcBinderTest, Prozedurnamen folgen der Grammatik |
| R2 | Teil in Klammern: leerer Teil `[]` erlaubt | RpcBinderTest, Prozedurnamen folgen der Grammatik |
| R3 | Richtung: Bit 0 mit NULL ergibt INPUT_OUTPUT statt OUTPUT | RpcBinderTest, Richtung; RpcDispatchTest, sp_executesql |
| R4 | Dezimal: Betrag gleich 10^p angenommen (Vergleich > statt >=) | RpcBinderTest, Dezimalwert außerhalb des Bereichs |
| R5 | Datum: ein Tag über 9999-12-31 angenommen | RpcBinderTest, Datum und Zeit außerhalb des Bereichs |
| R6 | Zeit: 24:00 angenommen (Vergleich > statt >=) | RpcBinderTest, Datum und Zeit außerhalb des Bereichs |
| R7 | Statusbyte: Prüfung auf fremde Bits entfernt | RpcBinderTest, Statusbit |
| R8 | HandshakeAutomaton: RPC setzt nicht RUNNING | sechs Tests in RpcDispatchTest |
| R9 | Katalogname per Name: Präfix sp_cursor nicht mehr abgelehnt | RpcBinderTest, Katalognamen; RpcDispatchTest, Ablehnung |
| R10 | Aufruftext enthält den Parameterwert statt des Namens rechts | RpcBinderTest, Aufruftext; RpcDispatchTest, Name |
| R11 | Parameternamen: Eindeutigkeit mit Beachtung der Schreibweise | RpcBinderTest, Parameternamen |
| R12 | Ablehnung: Verbindung schließt nach der Antwort | RpcDispatchTest, Ablehnung und mehrere Ablehnungen |

  Zusätzlich R13: QueryParameter.toString gibt den Wert aus; erkannt durch BackendModelTest und RpcBinderTest.
- `make docs-check` nach Anlage dieses Reports: siehe Summary.

---

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Ein Strukturfehler wird je nach Pakettyp verschieden beantwortet: eine RPC-Nachricht, die der Parser abweist (abgeschnittene TYPE_INFO, Restbytes, leer), ergibt `TDS_UNSUPPORTED_RPC` (Class 16, Verbindung offen); ein SQL_BATCH, den der Parser abweist, ergibt `TDS_PROTOCOL_ERROR` (Class 20, schließt). Die SPEC-013-Zeile nennt die RPC-Zuordnung als Setzung, die SPEC-011-Zeile „Paket/Token ungültig“ bleibt unverändert und deckt dieselbe Lage; die Spezifikation sagt nicht, welche Zeile für eine ungültig kodierte RPC-Nachricht gilt. Die Wahl stimmt mit dem Wortlaut der DoD überein; offen bleibt die Abgrenzung der beiden Zeilen. Das Log trägt für beide Ursachen denselben Eintrag (Code, ohne Grund), so dass ein defekter Client und eine nicht gebaute Form im Betrieb nicht unterscheidbar sind. Der Strom bleibt synchron, weil die Nachricht vollständig gerahmt ist: kein Synchronitätsfehler. | SPEC-011 · SPEC-013 · [ADR-0007](../plan/adr/0007-fehler-und-attention.md) | src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RpcBinder.kt · „is ProtocolResult.Failed -> RpcBinding.Rejected(RpcRejection.ENCODING)“; spec/spezifikation.md · „oder eine RPC-Nachricht, die der Parser abweist (Setzung, nicht gemessen)“; RequestLoop.kt · „is ProtocolResult.Failed -> errors.protocol(parsed.error)“ | ja — ein Test mit gleichem Defekt in Batch und RPC zeigt die zwei Folgen | Strukturfehler je Pakettyp verschieden beantwortet, Zuständigkeit der Zeilen offen |
| F-2 | LOW | Die Obergrenzen der Namen sind im KDoc und im Spezifikationsabsatz verschieden gezählt: beim Prozedurnamen zählen höchstens 128 Zeichen je Teil ohne die Klammern, beim regulären Teil mit allen Zeichen, beim Parameternamen 128 Zeichen einschließlich `@`. Der Spezifikationsabsatz nennt „höchstens 128 Zeichen je Teil“ und „höchstens 128 Zeichen“ ohne diese Zählregel; die Tests belegen die Grenzen (127 und 128 gültig, 129 abgelehnt), die Zählweise ist nur im Code lesbar. | Maintainability (Plan §6: Eingrenzung steht in der Spezifikation) | spec/spezifikation.md · „höchstens 128 Zeichen je Teil“; RpcProcedureName.kt · „höchstens [MAX_PART_CHARS] Zeichen je Teil“ | nein — kein Gate vergleicht Spezifikation und Zählweise | Obergrenze ohne Zählregel in der Spezifikation |
| F-3 | INFO | Die Ablehnung eines Wertes außerhalb seines Bereichs (Dezimalbetrag ab 10^Precision, Vorzeichenbyte außer 0 und 1, Tag nach 9999-12-31, Zeit ab 24 Uhr; `RpcRejection.VALUE`) steht im KDoc von `RpcWireValues` als Festlegung und ergibt `TDS_UNSUPPORTED_RPC`. Die SPEC-013-Zeile und der Absatz im Backend-Vertrag zählen sie nicht auf; sie nennen Typ, Statusbit, Namen und Parserablehnung. Zuständig für einen Nachtrag ist der Implementer. | AGENTS.md §3.8 · SPEC-013 | src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RpcWireValues.kt · „Die Grenzen sind Festlegungen ohne Messung“ | nein | Festlegung nur im KDoc |
| F-4 | INFO | Das Layout der Dezimal-, Datums- und Zeitwerte steht als „Layout nach MS-TDS“ im KDoc, ohne Abschnittsnummer; die Handvektoren der Tests hat derselbe Lauf geschrieben, der den Decoder schrieb. Sie belegen die Selbstkonsistenz (vom Reviewer nachgerechnet), nicht ein am Referenzclient gemessenes Drahtformat. Das KDoc kennzeichnet die Grenzen als Festlegung, das Layout selbst nicht; keine Abschnittsnummer ohne Quelle steht da, daher kein Verstoß gegen §3.8. Der Vergleichslauf mit dem Referenzclient (Beobachtung messung-am-echten-sql-server-fehlt) trägt die Messung. | AGENTS.md §3.8 | RpcWireValues.kt · „(Layout nach MS-TDS)“ | nein — erst der Referenzclient-Lauf | Layout als Quelle genannt, nicht gemessen |
| F-5 | INFO | Die Namensgrammatik ist weiter als die regulären Bezeichner von T-SQL: Unicode-Buchstaben und -Ziffern aller Schriften, im Klammerteil jedes Zeichen außer ISO-Steuerzeichen (Semikolon, Hochkomma, `--`, U+2028). Das ist kein Bruch des Textes: ein Teil in Klammern endet nur am einfachen `]`, danach muss ein Punkt oder das Ende folgen, geprüft mit Mutation R1 und R2; der Scanner des Simulations-Backends behandelt `[ ]` als Quotierung. Offen bleibt, dass ein späteres Backend die Quotierung selbst beachten muss; im Backend-Vertrag steht das nicht. Ein Parametername darf mit `@@` beginnen (`@@x`), der Text enthält ihn links und rechts von `=`; kein Test belegt den Fall. | [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit) · SPEC-030 | RpcProcedureName.kt · „name[index].isLetterOrDigit() \|\| name[index] in“; RpcParameters.kt · „name.drop(1).all“ | ja — ein Test mit `@@x` und mit U+2028 im Klammerteil | Grammatik weiter als der Zielbezeichner, Randfälle ohne Test |
| F-6 | INFO | Die Antwort auf einen erfolgreichen RPC besteht nur aus DONE; der Absatz im Backend-Vertrag nennt RETURNSTATUS, RETURNVALUE und DONEPROC „noch nicht festgelegt“, SPEC-030 nennt RETURNSTATUS und RETURNVALUE für die Formen Pflicht. Ein OUTPUT- oder InputOutput-Parameter erreicht das Backend (Richtung übernommen); ein OutputParameter-Ereignis des Backends schließt nach SPEC-031 die Verbindung. Nichts davon ist vorweggenommen: Handles, Cancel und sp_prepexec gehören den Folge-Slices. Zuständig ist `slice-antwort-mehrteilig-output`. | SPEC-030 · SPEC-031 | spec/spezifikation.md · „RETURNSTATUS, RETURNVALUE und DONEPROC sind noch nicht festgelegt“ | nein | Zwischenstand der Antwortform |

## Prüfung nach Schwerpunkten

1. **Injektionsfreiheit.** `sp_executesql`: `RpcBinder` übernimmt den Text der Nachricht unverändert als `sql`; die Deklarationsliste fließt nicht ein, Werte stehen nur in der Liste (Test mit Injektionswert, Prüfung auf Wortgleichheit und Nichtenthaltensein). RPC per Name: `execText` setzt aus Name und Parameternamen zusammen, nie aus Werten (Mutation R10 erkannt). Gedankenfuzz am Namen: eine Zeichenfolge, die den Text bricht, müsste außerhalb von `[ ]` Leer-, Trenn- oder Anführungszeichen tragen (abgelehnt, reguläre Teile lassen nur Buchstaben, Ziffern, `_@#$` zu) oder im Klammerteil ein einfaches `]` setzen (beendet den Teil, danach nur Punkt oder Ende zulässig, sonst Ablehnung); fünf Teile, leere Teile, 129 Zeichen und Steuerzeichen sind abgelehnt. Das Zerlegen endet nach vier Teilen. Ein Weg zum Brechen des Textes wurde nicht gefunden. Parameternamen gehen in den Text nur nach der engen Grammatik (kein Leerzeichen, kein `-`, kein Semikolon, keine Klammer).
2. **Typbindung.** INTN 4 und 8, BITN, NVARCHAR/NCHAR, DECIMALN/NUMERICN, DATEN, TIMEN, DATETIME2N entsprechen der im KDoc genannten Abbildung; DATETIME2 liest zuerst die Zeit, dann das Datum. Grenzwerte: Dezimal ab 10^p, Datum bis Tag 3652058, Zeit unter 24 Uhr; Überlauf von Ticks mal Einheit ist für Scale 0 bis 7 ausgeschlossen (größter Wert 4·10^15). Richtung: Bit 0 mit Wert INPUT_OUTPUT, mit NULL OUTPUT; ein InputOutput-Parameter mit NULL erscheint als OUTPUT, im KDoc als Festlegung benannt. Parser begrenzt Precision auf 1 bis 38 und Scale auf Precision beziehungsweise 7; die Kombination von Größe und Precision prüft der Parser nicht, der Decoder lehnt über die Betragsgrenze ab.
3. **Fehlerverhalten.** Ablehnungen ergeben `TDS_UNSUPPORTED_RPC` mit Class 16 und offener Verbindung (Mutation R12 erkannt); das Backend wird nicht gerufen (Test). `QueryRequest`, `QueryParameter` und `RpcBinding.Query` verbergen Werte und Text (R13 erkannt). Zur Inkonsistenz zwischen RPC- und Batch-Strukturfehlern siehe F-1.
4. **Wirkung auf Bestehendes.** `HandshakeAutomaton.forward` setzt RUNNING für RPC; Transaction Manager und ATTENTION bleiben in READY und schließen (Test bleibt, für beide Typen). Die Änderung in `DispatchFailureTest` nimmt RPC aus der Liste „bleibt unbeantwortet“ und stellt in `RpcDispatchTest` eine Gegenprobe für den RPC bereit; `HandshakeMatrixTest` ersetzt die Erwartung für RPC (RUNNING und Forward) und behält die Erwartung für die beiden anderen Typen: kein Verlust von Schutz. Die Merge-Stelle in `RequestLoop` ist sauber: `answerRpc` bindet, die Ablehnung geht als ganze Nachricht über den Sitzungsrahmen (wie der Strukturfehler eines Batch), die Anfrage geht durch `execute` mit dem Strom des Streaming-Slice (Senke, Rückstau, EOM-Paket unverändert); der Parameter `parameters` ersetzt nur die leere Liste. KDoc der Schleife beschreibt den Zustand (RPC beantwortet, Transaction Manager und ATTENTION nicht).
5. **Nicht vorweggenommen.** Weder RETURNSTATUS noch RETURNVALUE noch DONEPROC, keine Handles, kein Cancel; `sp_prepexec` und `sp_execute` ergeben Ablehnung (NOT_BUILT), `sp_prepare`, `sp_unprepare` und `sp_cursor*` (auch per Name) UNKNOWN_PROCEDURE.
6. **Herkunftsstufen und §3.8.** Alle Aussagen zu Grenzen, Namen, Richtung und Antwortform tragen das Wort Festlegung oder „nicht gemessen“ im KDoc und im Spezifikationsabsatz; es steht keine Abschnittsnummer eines externen Standards. Die Spezifikationsänderungen (SPEC-013-Zeile, Absatz „RPC im Backend-Vertrag“, Historie) enthalten weder das Wort ADR noch einen Pfad außerhalb von spec; die untere Schicht erweitert die obere nicht (Anker: [TDS-FA-04](../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc) und [SPEC-030](../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten)). Hard Rules §3.1 (nur Docker), §3.2 (kein `@Suppress` im Diff), §3.6 (keine Änderung an `detekt.yml` oder Gates), §3.7 (Kommentare Zusage, Kopplung, Abgrenzung; keine Chronik): kein Verstoß.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| src/main protocol/dispatch (RpcBinder, RpcParameters, RpcProcedureName, RpcWireValues, RequestLoop) | geprüft, Befunde F-1, F-2, F-3, F-4, F-5 |
| src/main protocol/session (HandshakeAutomaton, HandshakeEvent) | geprüft, ohne Befund |
| src/main domain (QueryRequest, QueryParameter) | geprüft, ohne Befund |
| src/test protocol/dispatch und protocol/session | geprüft, ohne Befund außer F-5 (Testlücke) |
| spec/spezifikation.md (SPEC-013, Absatz Backend-Vertrag, Historie) | geprüft, Befunde F-1, F-2, F-3, F-6 |
| config/detekt, Dockerfile, Makefile, build.gradle.kts | im Diff unverändert, geprüft, ohne Befund |
| Suppression (`@Suppress`, `@SuppressWarnings`) im Diff | geprüft, ohne Befund |

## Summary

Kein HIGH. Ein MEDIUM (F-1: Strukturfehler eines RPC bleiben offen und tragen `TDS_UNSUPPORTED_RPC`, die eines Batch schließen mit `TDS_PROTOCOL_ERROR`; die Abgrenzung von SPEC-011 und SPEC-013 und der Grund im Log fehlen), ein LOW (F-2), vier INFO (F-3 bis F-6). Die Injektionsfreiheit hielt dem Gedankenfuzz am Namen stand; alle zwölf Mutationen der Schwerpunkte und die Zusatzmutation zur Textdarstellung wurden von den Tests erkannt; die nachgerechneten Handvektoren stimmen. Dieser Report wird nicht herabgestuft, wenn der Implementer widerspricht; ein Widerspruch zu F-1 geht als Konflikt über den Architect.
