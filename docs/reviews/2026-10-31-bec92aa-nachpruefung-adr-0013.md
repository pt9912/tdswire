# Nachprüfung: [ADR-0013](../plan/adr/0013-backend-grenzen.md) vor Accepted — 2026-10-31

**Rolle:** Verifier (Modul 11), frischer Kontext. Gegenstand: die Vorbedingungen V1 bis V3 und V6 aus der [Verifikation](2026-10-31-c2fea13-verifikation-adr-0013-backend-grenzen.md), umgesetzt in 76da7a5 und e8a340c (Stand `main` bei bec92aa). Nichts repariert, nichts committet. Kein Docker-Lauf außer `make docs-check`; Quelle und Texte gelesen und per Suche abgeglichen.

## Ergebnis in einem Satz

V1, V2, V3 und V6 sind erledigt; es gibt keinen Blocker. Zwei LOW-Hinweise (unten) sind kein Hindernis für Accepted.

## Vorbedingungen

| V | Stand | Beleg |
|---|---|---|
| V1 | erledigt | Entscheidung 2 trägt die „Abnahme-Entscheidung des Nutzers (2026-10-31)“ zu [TDS-FA-06](../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände) bei unverändertem Lastenheft und nennt sie ausdrücklich „keine gemessene Erfüllung“ (M6, Verbindung kann ohne Antwort schließen). Abschnitt „Offen“ lautet „Nichts.“ mit Verweis auf Entscheidung 2. Contra-Zelle von Option B und Negativ (a) sind angepasst, die Geschichtszeile steht. Die Spezifikation trägt den Halbsatz in SPEC-022 („nicht gemessen“). |
| V2 | erledigt | Entscheidung 2 definiert „synchron“ wie [SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität) und nennt den strengeren Bau als Festlegung. Gegen `MessageStream` geprüft: `flush` setzt `broken = true`, bevor das Paket gebaut wird, danach `broken` nur nach Kanalergebnis zurück; `write` und `finish` liefern nach dem Fehler `false`. Deckt sich mit KDoc und ResponseSequencer. Fitness-Function-Zeile angeglichen. |
| V3 | erledigt | (a) detekt-Risiko ist durch den Beleg ersetzt: Review F-11 nennt Befund je Fangform (Throwable meldet TooGenericExceptionCaught, `OutOfMemoryError` nicht); `config/detekt/detekt.yml` im Diff unverändert. (b) Suche in `src/main` nach dem Verweis auf die Vorgänger-ADR: genau fünf Dateien (ValueMapping, QueryExecution, QueryBackend, QueryEvent, QueryRequest), identisch mit der Liste in ADR und Slice; `BufferedResponse` gibt es nicht mehr, die KDoc des ResponseSequencer trägt keinen „offen“-Satz zu [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md). Die Zuordnung der Träger je Satz stimmt mit dem Slice überein. |
| V6 | erledigt, mit Einschränkung | Kopf „Berührte Spec-Stellen“ nennt SPEC-002, 009, 016, 022, 031, 035, 048 (geändert) und SPEC-033 (nur Verweis). Die zwei Liefer-Punkte in slice-release-artefakt-lizenz stehen auf getrennten Zeilen. DoD-Punkt 1 von slice-adr-backend-grenzen behält den Wortlaut „in diesem Slice“ und trägt einen Vorschlag-Vermerk; das ist vom Planner so gewählt, siehe Hinweis 2. |

## Prüfung der neuen Texte

| Prüfung | Ergebnis |
|---|---|
| Aussage breiter als Beleg (§3.8) | Keine gefunden. Die Abnahme-Entscheidung ist als solche und als „keine Messung“ gekennzeichnet; „gelingt das Schreiben nicht …“ bleibt „nicht gemessen“. |
| Slice- oder Welle-Kennung in der ADR | Keine ausser `BEO-TDS/…` in der Grenze der Aussage (unverändert aus der Verifikation, kein slice-/welle-Name); Träger stehen in Worten. Die Geschichtszeile verlinkt nur die Verifikation. |
| Links | Neue Links ([TDS-FA-06](../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), SPEC-031, Review, Verifikation) relativ und per `make docs-check` aufgelöst, siehe Abschluss. |
| Spezifikation | Wort „ADR“: 0 Treffer; der Zusatz in SPEC-022 ist ein Satz ohne Pfad ausserhalb `spec/`. Einzige Änderung seit 76da7a5~1: diese eine Zeile. |
| Unverändert | `spec/lastenheft.md`, `spec/architecture.md`, [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) und `src/` im Diff seit der Verifikation leer. |
| Konsistenz mit dem gebauten Stand (Merge c2fea13) | ADR-Wortlaut (Fang um Backend jedes Throwable ausser Abbruch, Fang nur OutOfMemoryError in Encoder und Senke, Schließen ohne Antwort nach Ausnahme im Schreiben) stimmt mit ResponseSequencer, ResponseRun und MessageStream überein. |

## Hinweise (LOW, nicht blockierend)

1. **Bedingung in der ADR, keine in der Spec.** Die ADR lässt die Zusage als erfüllt gelten, „wenn der Fehler gefangen wird und der Betreiberschalter als Abhilfe im Betriebstext steht“; der Betriebstext ist noch nicht geliefert (Träger: slice-release-doku-prozess). SPEC-022 sagt „gilt hierfür als erfüllt“ ohne diese Bedingung. Kein Widerspruch im Gebauten, aber bis zur Lieferung des Betriebstexts ist die Bedingung offen; die ADR nennt den Träger der Lieferung bereits. Die Zusatzklausel in der Spec ist optional.
2. **Wortlaut DoD-Punkt 1** („Tabelle in diesem Slice“) steht noch mit Vermerk statt korrigiert; gehört zur Closure des Planners, nicht zu Accepted.

Nicht nachgemessen: Der Spike und die Zahlen M1 bis M8 sind unverändert gegenüber der Verifikation und nicht wiederholt. Die Setzungen 5 bis 10 hat der Nutzer weiter nicht ausdrücklich gewählt (V4 der Verifikation, Rückfrage ist Sache des Nutzers); die Geschichtszeile kennzeichnet sie weiter als Setzung.

## Kann Accepted gesetzt werden?

**Ja.** Kein Blocker. Beim Setzen mitzuziehen: Status in der ADR, Indexzeile, Geschichtszeile; danach die zurückgestellten Nachzüge (KDoc-Sätze in den fünf Dateien, Liefer-Punkte, Start-Trigger von slice-sim-auswahl-streaming). Ab Accepted gilt [AGENTS.md](../../AGENTS.md) §3.5.

## Abschluss

`make docs-check` nach Anlage dieses Reports: Ergebnis in der Rückmeldung.
