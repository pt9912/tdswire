# Review: slice-decimaln-laenge-an-messung-angleichen (A-11, Option A)

* Datum: 2026-11-18 · Rolle: Reviewer (frischer Kontext) · Diff: `git diff main...HEAD` (42d9e32c Messung, 09560bff Angleichung)
* Geprüft gegen: Slice-Plan (§1, §3, §6), `AGENTS.md` §3.1 bis 3.8, [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md), `.harness/skills/reviewer.md`. Nicht gegen die DoD (Verifier).
* Sensor: `make test` auf dem Stand (Exit 0, aus dem Cache: der Stand war schon gebaut, kein frischer Lauf). Mutationen im Kopf gelesen, nicht gefahren: Der Default 17 im `TypeInfoWriter` wird zu `decimalBytes(precision)`. Das macht `TokenEncoderTest > COLMETADATA Dezimalzahlen tragen die Laenge 17 je Precision`, `ColumnMappingTest` und `BulkCopyProbeTest` rot. Kein Messlauf, kein `make clean-build`, kein `make e2e`.

## Antworten auf die Schwerpunkte

1. **„Länge 17“ gegen die Messung.** Die Zusage trägt bis zu den gemessenen Precisions 1, 9, 10, 19, 20, 28, 29 und 38, je `decimal` und `numeric`, an 2022 und 2025. Die Messdateien zeigen `6a 11` bzw. `6c 11` in allen 16 Spalten. Die Grenze „Precisions dazwischen nicht einzeln gemessen“ steht im selben Satz (SPEC-003, W-23, KDoc). Der Code schreibt 17 für alle Precisions 1 bis 38 (Interpolation innerhalb der Klassengrenzen). Das ist plausibel und deklariert. Der Satz „Ein Wert im ROW ist höchstens so lang wie die TYPE_INFO“ steht in SPEC-003 unter „gemessen“. Gemessen sind aber nur die Wertlängen 5 und 9 (W-25). Siehe F-3.
2. **ColumnMapping.kt unverändert.** Das ist sachlich richtig: Der Default 17 im `TypeInfoWriter` deckt `ColumnMapping` ab, `DecimalType.bytes` bleibt für explizite Größen. Die Abweichung vom Plan-Wortlaut („ColumnMapping.kt und TypeInfoWriter.kt“) ist im Bericht benannt und ohne Wirkung. Der Plan-Tabelleneintrag war „update, falls Option A“. Befund nur insofern, als `BulkCopyProbe.kt` (siehe F-2) nicht im Plan stand.
3. **W-25 und Encoder-Korrektheit.** Der Encoder ist nicht falsch. Die Wertbytes nach Precision (5, 9, 13, 17) sind immer höchstens 17, also nie länger als die TYPE_INFO; ein Client liest das Längenbyte je Wert (MS-TDS erlaubt 5, 9, 13, 17). Es bleibt eine gemessene Abweichung der Draht-Bytes vom echten Server (z. B. `decimal(10,2)`, Wert 1,5: der Server sendet 5 Byte, der Encoder 9). Der Vergleich der Mitschnitte sieht sie nicht, weil er Werte vergleicht. Sie hat keine Ausnahme-Nummer, keinen Slice und keinen Registereintrag. Siehe F-1.
4. **Entfernte Ausnahmeregeln, Ablagezahl, Spec, Matrix.** Beide TSV-Regeln und die Zeile A-11 sind weg. Die Ablage ist je Instanz 38 Dateien (36 + 2), der Test `MessungBelegTests` ist auf 38 nachgezogen. SPEC-003 und SPEC-036 sind nachgezogen. Das Matrix-Zitat zeigt auf den umbenannten Test, der existiert. Die Quellen `slice-simulation-schemaonly-keyinfo.md` (Zeilen 33, 44, 104, „Vorbedingung A-11“) und die geschlossenen Slice-Notizen nennen A-11 weiter als offen. `done/` ist Zeitdokument und bleibt. Siehe F-4.
5. **Tests.** Siehe F-5. Die Länge 17 ist durch Handvektoren und Mutationen getragen. Die ROW-Eigenschaft ist schwächer getragen.

## Findings

### F-1 · MEDIUM · Abweichung W-25 gemessen, ohne Träger
* quelle: AGENTS.md §3 (Spec ist Zielbild, Abweichung ist Schuld), SPEC-045 (Ausnahmeliste), Slice §2 (Register)
* pfad: `docs/messungen/referenzabgleich.md` · „der Server dieses Repos ist daran nicht angeglichen, der Vergleich der Mitschnitte zeigt keinen Unterschied“; `spec/spezifikation.md` · „Die Länge der Dezimalwerte im ROW ist nicht Teil dieser Aussage“
* befund: Der Slice entfernt A-11 und führt dafür eine neue gemessene Abweichung ein (ROW-Wertlänge nach Wert statt nach Precision). Sie ist nur als Prosa-Ausnahme in SPEC-003 und W-25 vermerkt. Es gibt keine Nummer in der Ausnahmeliste, keinen Folge-Slice und keinen Registereintrag in der Diff-Liste (`BEO-TDS/mitschnitt-luecken-ohne-traeger` ist nicht berührt). Failure-Szenario: Die Lücke ist in drei Wochen nirgends auffindbar außer in einem Messprotokoll, und der nächste Gleichlauf-Slice (z. B. SchemaOnly/KeyInfo) hält sie für erledigt, weil „A-11 aufgelöst“ steht.
* verifizierbar: nein
* klasse: Gemessene Abweichung ohne Träger

### F-2 · MEDIUM · Plan-Tabelle nennt berührte Dateien nicht
* quelle: Reviewer-Skill (Plan-Tabelle gegen Diff), Slice §3
* pfad: `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/BulkCopyProbe.kt` · „private const val DECIMAL_BYTES = 17“ (entfernt); Slice §3 Tabelle
* befund: Im Diff berührt, aber nicht in Slice §3 genannt: `BulkCopyProbe.kt` (Code, Override entfernt), `MessungBelegTests.cs`, `tools/mk/vergleichslauf-d.mk`, `docs/messungen/unterschiede-2022-2025-bestand-2026-11-17.md`, `zeilenform-nbcrow-2026-11-17.md`, `docs/plan/kompatibilitaetsmatrix.md`, `ResponseRowFormTest`, `BulkCopyProbeTest`, `DecimalValuesTest`, SPEC-036. Der Kopf „Berührte Spec-Stellen“ nennt nur SPEC-003 und SPEC-045; der Diff berührt SPEC-003 und SPEC-036 (SPEC-045 steht nur im Änderungsprotokoll und im `@spec`-Tag). Die Planner-Tabelle sagt außerdem „ColumnMapping.kt … update“, geändert wurde sie nicht.
* verifizierbar: ja (Abgleich Diff und Tabelle)
* klasse: Plan-Tabelle unvollständig gegenüber dem Diff

### F-3 · LOW · „Wert im ROW höchstens so lang wie die TYPE_INFO“ steht unter „gemessen“
* quelle: AGENTS.md §3.8
* pfad: `spec/spezifikation.md` · „Ein Wert im ROW ist höchstens so lang wie die TYPE_INFO.“ (Absatz „Länge von `DECIMALN` und `NUMERICN` im TYPE_INFO (gemessen am 2026-11-18“)
* befund: Gemessen sind Wertlängen 5 und 9 Byte (W-25; 13 und 17 sind laut W-25 nicht gemessen). Der Satz steht ohne Kennzeichnung als Setzung oder Folgerung (aus der Länge 17 als Obergrenze nach MS-TDS) in einem Absatz, der mit „gemessen“ überschrieben ist.
* verifizierbar: nein
* klasse: Aussage breiter als Beleg (Absatz-Überschrift trägt Folgerung mit)

### F-4 · LOW · Veraltete Gegenstelle in offenem Slice
* quelle: Reviewer-Skill (Fortschreibung lässt die zweite Fundstelle stehen)
* pfad: `docs/plan/planning/in-progress/slice-simulation-schemaonly-keyinfo.md` · „Vorbedingung A-11“
* befund: Der Slice nennt A-11 als offen (Zeilen 33, 44, 104, Risiko „Ohne A-11 bleibt der Gleichlauf … verfehlt“). A-11 ist durch diesen Diff aufgelöst. Die Änderungsliste nennt die Stelle nicht und gibt keinen Grund zum Stehenlassen. Die Stellen in `done/` bleiben zu Recht.
* verifizierbar: ja (`grep -rnw A-11`)
* klasse: Zweite Fundstelle nach Fortschreibung

### F-5 · LOW · Test der ROW-Länge vergleicht gegen eine Konstante statt gegen die TYPE_INFO
* quelle: Slice §6 (Risiko 3: „Tests der Ausgabeseite prüfen, dass kein Wert länger als die TYPE_INFO ist“); Maintainability
* pfad: `src/test/kotlin/io/github/pt9912/tdswire/protocol/token/DecimalValuesTest.kt` · „assertTrue(length <= TypeInfoWriter.decimalBytes(38)“
* befund: Der Test liest die TYPE_INFO-Länge nicht aus dem geschriebenen COLMETADATA, sondern nimmt `decimalBytes(38)` (= 17). Wird der Default im `writeDecimal` auf 13 gesenkt, bleibt der Test grün; ein Längenbyte über der TYPE_INFO würde nur `TokenEncoderTest` rot machen. Die Zusage „Wert höchstens TYPE_INFO“ ist damit nur über den Umweg des Handvektor-Tests getragen. Der Fall `expectedLength` wird für den größten Wert nicht gesetzt (null).
* verifizierbar: ja (Mutation Default 13)
* klasse: Eigenschaftstest gegen Konstante statt gegen die Gegenseite

### F-6 · INFO · Entfernte Mitschnitt-Regeln sind in diesem Review nicht live belegt
* quelle: Slice §2 DoD 2 (Verifier)
* pfad: `referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv` · (zwei entfernte Zeilen `batch-select/01/colmetadata`, `batch-typen/01/colmetadata`)
* befund: Dass der Vergleichslauf ohne die zwei Regeln grün ist, lässt sich nur im Live-Lauf (`LAUF=mitschnitt`) belegen. Reviewer fährt ihn nicht. Zuständig: Verifier (`make e2e` und der Vergleichslauf).
* verifizierbar: ja (Verifier)
* klasse: Belegt nur im Folgeschritt

### F-7 · INFO · Kommentare nach §3.7
Die geänderten KDocs (`TdsType.DecimalType`, `BulkCopyProbe`, `TypeInfoWriter.decimalBytes`, Tests) sind im Indikativ und tragen Zusage, Kopplung oder Grenze. Die neuen KDocs nennen die abgelösten Zustände nicht. Die Formulierung „Wächter: die Größe 9 aus der Precision“ im KDoc von `BulkCopyProbeTest` und „Ein Wächter gegen die Länge nach Precision (5, 9, 13)“ benennt die Mutation, die der Test fängt (vorhandenes Muster), und ist kein Befund.

## Geprüft, ohne Befund
* `src/main/kotlin/.../protocol/token/` (`TypeInfoWriter`, `TdsType`): keine Schichtverletzung, kein Suppress. Die Prüfung `bytes < decimalBytes(precision) || bytes > 17` bleibt.
* `src/main/kotlin/.../protocol/dispatch/` (`BulkCopyProbe`): Die Probe schreibt 17 über den Default; Verhalten der Probe unverändert (`6a 11 0a 02`).
* `src/test/kotlin/.../token`, `result`, `response`, `dispatch`: Handvektoren aus den Messdateien; Mutation „Länge nach Precision“ rot (siehe oben).
* `referenztest-dotnet/Vergleich`, `Mitschnitt`, `messung/2022|2025`: Szenarien und Messdateien konsistent (Erzeuger im Repo, Zahlen 38/17 stimmen). Instanzen, Digest und Client stehen in den Köpfen der Messdateien.
* `docs/messungen/*`, `docs/plan/kompatibilitaetsmatrix.md`, `docs/plan/spec-abdeckung.md`: Zitate lösen auf; Haken der DoD sind im Diff nicht gesetzt (Slice-Datei ändert nur die Entscheidungszeile).
* `docs/user/`: nicht im Diff; kein Treffer auf „Länge 9“ oder Precision-abhängige Länge.
* `tools/mk/vergleichslauf-d.mk`: nur Kommentar; kein Host-Aufruf (AGENTS.md §3.1).
* Suppression, ADR-Änderung, Gate-Lockerung: nichts im Diff.

## Urteil
Kein HIGH. Der Kern (Länge 17 im COLMETADATA je gemessener Precision) trägt, ist handvektor-belegt und gegen Mutationen geschützt. Offen sind zwei MEDIUM (F-1 Träger für W-25, F-2 Plan-Tabelle) und drei LOW. Freigabe an den Verifier, sobald der Planner/Implementer F-1 und F-2 beantwortet hat. Keine Nachbesserung mit Code ist nötig; falls F-5 nachgebessert wird, braucht diese Fläche einen Re-Review.
