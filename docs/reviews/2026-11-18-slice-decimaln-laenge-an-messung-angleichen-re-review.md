# Review-Report: slice-decimaln-laenge-an-messung-angleichen, Re-Review der Nachbesserung — 2026-11-18

**Review-Art:** Re-Review nach dem Skill-Punkt „Nachbesserung mit Code ohne zweiten Review“; nur Commit `36529248`, gegen Plan, ADR und Hard Rules; nicht gegen die DoD.

**Gegenstand:** `git show 36529248` (6 Dateien): `DecimalValuesTest.kt`, `spec/spezifikation.md` (SPEC-003), Slice-Plan (Kopf, §3, §7), `slice-simulation-schemaonly-keyinfo.md`, Register-Evidenz `BEO-TDS/mitschnitt-luecken-ohne-traeger/evidence/slice-decimaln-laenge-an-messung-angleichen.md`, Erstreview.

**Eingangs-Kontext:** [Erstreview](2026-11-18-slice-decimaln-laenge-an-messung-angleichen.md) (kein HIGH, F-1 und F-2 MEDIUM, F-3 bis F-5 LOW), [TDS-FA-05](../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md) (`Accepted`, unverändert), `AGENTS.md` 3.7, 3.8.

**Selbst gefahren:** nichts. Der freie Speicher (Spalte `free`) lag bei 3896 MB, `available` bei 16312 MB; es lief deshalb kein Gradle- und kein Docker-Lauf, nur Lesen. Mutation und Testlauf sind gelesen, nicht gefahren.

## Prüfung je Finding

- **F-5 (Code, behoben).** Der Test schreibt das COLMETADATA in denselben Encoder und liest die Länge der TYPE_INFO aus Index 10 (Token 1, Anzahl 2, Benutzertyp 4, Flags 2, Typ 1, dann das Längenbyte). Der Index stimmt mit dem Aufbau überein. Die Zeile wird ab `metadata.size` gelesen. Verglichen wird `length <= typeInfoLength`, nicht mehr gegen `decimalBytes(38)`. Mutation Default 13 (`LENGTH_17` in `writeDecimal`, `TypeInfoWriter.kt` Zeile 112): Der größte Wert der Precision 29 bis 38 ist 17 Byte lang; entweder weist die Prüfung `bytes < decimalBytes(precision)` das COLMETADATA ab (dann ist `assertIs<Ok>` rot), oder das Längenbyte 13 liegt unter 17 (dann ist `assertTrue` rot). Rot in beiden Fällen, gelesen. `TYPE_INFO_LENGTH_INDEX` ist eine benannte Konstante, der Kommentar nennt die Kopplung (§3.7: Kopplung, Indikativ). Behoben.
- **F-1 (Träger W-25, beantwortet).** Die Evidenz liegt im Register unter dem richtigen Eintrag (`evidence/`, Form wie die Nachbardateien), nennt Beleg W-25, Grenze („drei Werte, 13 und 17 Byte nicht gemessen“) und lässt die Entscheidung offen („Träger: Entscheidung des Nutzers offen“). Der Slice §7 führt dieselbe Aussage als „Offen, Träger Nutzer“ mit drei Optionen, ohne eine zu wählen. Es wird nichts Neues entschieden. Beantwortet.
- **F-2 (Plan-Tabelle, beantwortet).** Kopf und Tabelle stimmen mit dem Diff überein (BulkCopyProbe, Tests, Messprotokolle, Matrix, `vergleichslauf-d.mk`, schemaonly-Slice, Evidenz). Der Kopf trennt „Berührt“ und „Tatsächlich geändert“ (SPEC-003 und SPEC-036; SPEC-045 nur über Messprotokoll und TSV). Beantwortet.
- **F-3 (SPEC-003, beantwortet).** Der Satz lautet jetzt „… ist Folgerung (gemessen sind Wertlängen von 5 und 9 Byte, W-25), nicht gemessen“. Das trägt nur so weit wie der Beleg (§3.8).
- **F-4 (schemaonly-keyinfo, beantwortet).** Die vier Stellen (Vorbedingung, Empfehlung, „NICHT in diesem Slice“, Risiko) sind auf „aufgelöst“ gesetzt. Die Aussage „nicht mehr durch A-11 verschieden“ bleibt als „Folgerung … nicht gemessen“ gekennzeichnet. Stellen in `done/` unverändert, richtig.

## Neue Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| N-1 | LOW | Kopf des Slice, Zeile „Berührte Spec-Stellen“: „(Ausnahmeliste). · [SPEC-036]“ trägt einen Punkt vor dem Trenner; die Zeile ist sehr lang und mischt die Aufzählung mit dem Satz „Tatsächlich geändert“. Nur Form. | Maintainability | `docs/plan/planning/in-progress/slice-decimaln-laenge-an-messung-angleichen.md` Zeile 16 | ja | Formfehler |
| N-2 | LOW | §3.7 nahe der Grenze: „(nicht im ersten Plan)“ in der Plan-Tabelle und „War Vorbedingung … ist aufgelöst“ im Risiko des schemaonly-Slice beschreiben einen früheren Zustand, nicht den geltenden. Die Zusage steht jeweils daneben; die Herkunft hält `git`. Kein Gate rot, daher kein höherer Rang. | AGENTS.md 3.7 | Slice §3 Zeile „BulkCopyProbe.kt“; `slice-simulation-schemaonly-keyinfo.md` Risiko-Zeile 104 | ja | Chronik im Plan |
| N-3 | INFO | `state.md` des Registers führt W-25 nicht; die Evidenz-Datei trägt sie. Das genügt dem Register-Schnitt (Evidenz ist der Eingang, `state.md` wird beim Lese-Schritt der Closure fortgeschrieben). Kein Handlungsbedarf vor der Closure. | Register-Konvention | `BEO-TDS/mitschnitt-luecken-ohne-traeger/state.md` | ja | Hinweis |
| N-4 | INFO | `make docs-check` wurde in diesem Re-Review nicht gefahren (Speicher). Der Verifier fährt es nach der Tabelle für Doku-Änderungen (`make docs-check`, `make flaeche`, `make kopplung-zahlen`). | harness/README.md | — | ja (Verifier) | Beleg im Folgeschritt |

## Urteil

Kein HIGH, kein MEDIUM. F-1 bis F-5 sind beantwortet, ohne etwas Neues zu entscheiden; die Entscheidung über W-25 bleibt beim Nutzer und ist so geführt. Zwei LOW (N-1, N-2), die nicht blockieren. **Freigabe an den Verifier: ja.** Der offene Punkt W-25 (Träger Nutzer) gehört in die Closure-Frage, nicht in diese Freigabe.
