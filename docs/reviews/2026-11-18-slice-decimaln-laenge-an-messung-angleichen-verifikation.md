# Verifikationsbericht: slice-decimaln-laenge-an-messung-angleichen — 2026-11-18

**Rolle:** Verifier (Modul 11), frischer Kontext; DoD- und Entscheidungs-Konformität, Plan-vs-Code-Diff. Kein Review, kein Fix, keine DoD-Haken, kein Schließen, keine Änderung an `roadmap.md` oder am Lifecycle.

**Stand:** Branch `slice-decimaln-laenge-an-messung-angleichen`, Spitze `7dcd69df` (Commits `42d9e32c`, `09560bff`, `36529248`, `7dcd69df`); Arbeitsbaum sauber vor diesem Bericht.

**Gegenstand:** [slice-decimaln-laenge-an-messung-angleichen](../plan/planning/done/slice-decimaln-laenge-an-messung-angleichen.md) (A-11, Option A, Nutzerentscheidung 2026-11-18); [TDS-FA-05](../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md). Reviews: [Review](2026-11-18-slice-decimaln-laenge-an-messung-angleichen.md), [Re-Review](2026-11-18-slice-decimaln-laenge-an-messung-angleichen-re-review.md).

## Selbst gefahrene Sensoren (End-Stand `7dcd69df`, unter der Messsperre, Docker)

| Sensor | Ergebnis | Dauer |
|---|---|---|
| `make clean-build` | Exit 0 (Jar, Tests, detekt, `--no-cache`); 1495 `PASSED`-Zeilen im Log, kein Test `FAILED` | 795 s |
| `make e2e` | Exit 0, „gruen (9 Stufen, 723 s)“; Stufen 1 bis 9: 195, 172, 58, 89, 43, 32, 37, 15, 82 s | 723 s |
| `make gates` (ohne `FORCE`) | Exit 0, **12 s**: die drei Gruppenschritte `werkzeug`, `tests`, `stempel` sind „uebersprungen, Beleg“ (Stempel am Baum-Hash); gelaufen sind die Klasse `immer` (`docs-check`, `flaeche`, `gate-zuordnung` mit 33 Fällen, `stempel-eingaenge`). Der Frischbeleg für Tests und detekt ist `make clean-build`; ein Lauf der Klassen `werkzeug`/`stempel` am End-Stand ist hier nicht gefahren (Beleg des Implementers gilt über den Stempel) | 11 s |
| Nachprüfung der Messdateien | siehe unten | — |

Gesamt rund 25 Minuten; größte Posten `make clean-build` (795 s) und `make e2e` (723 s, Stufe 1 mit 195 s und Stufe 2 mit 172 s). Messsperre `/tmp/claude-1000/-Development-tdswire/messlock` frei übernommen und freigegeben; `available` vor jedem Lauf 16 bis 18 GB (Grenze 4000 MB nie berührt). Keine künstliche Last. `make e2e` belegt die Ausgabeseite gegen den Referenzclient (Stufen 3 bis 8 mit Simulation und `DECIMAL`-Spalten des Bestands); mssql-jdbc ist nicht Gegenstand von `make e2e` (siehe Risiko 1).

## Plan-vs-Code-Diff (`git diff main...HEAD`, 27 Dateien)

- **`src/main` (3 Dateien):** `TypeInfoWriter.writeDecimal` schreibt ohne Angabe (`bytes == 0`) die Länge 17 statt `decimalBytes(precision)`; die Prüfung `bytes >= decimalBytes(precision)` und `<= 17` bleibt. `BulkCopyProbe` verliert `DECIMAL_BYTES` und die Sonderbehandlung (der Default trägt die 17, Probe unverändert 17). `TdsType.DecimalType`-KDoc nennt Messung und Grenze. `ColumnMapping.kt` ist laut Plan-Tabelle unverändert (Default im Writer genügt); die DoD nannte `ColumnMapping.kt` mit, die Plan-Tabelle stellt die Abweichung ausdrücklich fest: **zulässig**.
- **Tests:** `TokenEncoderTest` (Handvektor je Precision 1, 9, 10, 19, 20, 28, 29, 38, `decimal` und `numeric`, nullbar), `DecimalValuesTest` (Wertlänge höchstens TYPE_INFO, NULL als `D1 00` je Precision), `ColumnMappingTest`, `ResponseRowFormTest`, `BulkCopyProbeTest` (Probe 17), `MessungBelegTests.cs` (Beleg der Ablage: 38 Messungen, Länge 17 je Precision aus beiden Instanzen); `MessungWireTests.cs` zwei neue Szenarien `decimal-laenge-tabelle`, `decimal-laenge-ausdruck`.
- **Ausnahmeliste:** die zwei Regeln `batch-select/01/colmetadata` und `batch-typen/01/colmetadata` fehlen in `mitschnitt-ausnahmen.tsv`; A-11 fehlt in der Tabelle von `docs/messungen/referenzabgleich.md`. `grep -w "A-11"` außerhalb von `docs/reviews` und Altdokumenten (`done/`) findet nur Verweise mit Auflösungsvermerk oder Slice-Text.
- **Nicht im Plan, aber im Diff und begründet:** Verweis-Nachzug in `docs/messungen/zeilenform-nbcrow-…`, `…unterschiede-2022-2025-…`, `kompatibilitaetsmatrix.md`, `spec-abdeckung.md`, `tools/mk/vergleichslauf-d.mk`, `done/slice-simulation-schemaonly-keyinfo.md` (alle in der Plan-Tabelle aufgeführt). Keine Datei außerhalb der Plan-Tabelle geändert.

## DoD Punkt für Punkt (Haken im Slice sind noch leer, das ist Sache des Planners)

1. **Messung am echten Server: erfüllt.** Die Ablagen `referenztest-dotnet/Mitschnitt/messung/2022/` und `2025/` (`decimal-laenge-tabelle.messung`, `decimal-laenge-ausdruck.messung`) tragen Kopf (Instanz 2022: 16.0.4305.5, 2025 mit Digest, Client 7.1.1) und Aufnahmedatum; ich habe die COLMETADATA-Bytes der Tabellenantwort je Instanz ausgelesen: je 8 `DECIMALN` (`6a`) und 8 `NUMERICN` (`6c`) mit Länge `0x11` = 17, Precisions 1, 9, 10, 19, 20, 28, 29, 38. Die Hypothese „immer 17“ ist an diesen acht Precisions je Instanz belegt (W-23, W-24); der Beleg trägt `MessungBelegTests` in `make clean-build` (grün). Ich habe `make vergleichslauf-d LAUF=wire` nicht wiederholt (EULA-Lauf, kein Sensor der Tabelle); die Rohbytes der Ablage sind der Beleg.
2. **Angleichung: erfüllt.** Länge 17 im Writer, Handvektoren aus der Messung, NULL-Wert, Probe von `SqlBulkCopy` bleibt 17, beide Mitschnitt-Regeln entfallen. Tests grün in `clean-build`.
3. **Nachzug: erfüllt.** SPEC-003 (neuer Absatz mit Messdatum, Precisions, Grenze „ein Client, Einzelmessung je Instanz“; der Satz „nicht Teil dieser Aussage (A-11)“ entfällt), SPEC-036 (Probe), Messprotokoll (neuer Abschnitt W-23 bis W-25 mit Bedingungen und Grenzen, A-11 aus der Tabelle entfernt), KDoc von `DecimalType`. SPEC-045 nur über Protokoll und Regeldatei, wie in „Tatsächlich geändert“ des Slice angegeben.
4. **Gates und Sensoren der Art: erfüllt, mit der Einschränkung der Zeile `make gates` oben.** `clean-build` und `e2e` Exit 0. `docs-check` und `flaeche` liefen in `make gates` (Klasse `immer`) grün. `kopplung-zahlen` ist Klasse `werkzeug` und lief über den Stempel-Beleg, nicht frisch. Mutation je Zusage und `make spec-abdeckung` sind Implementer-Pflicht; ich habe sie nicht wiederholt (nicht in der Verifier-Zeile der Tabelle).
5. **Review: erfüllt.** Zwei Berichte unter `docs/reviews/`, nach dem Re-Review kein HIGH und kein MEDIUM offen; LOW N-1/N-2 betreffen nur Text.
6. **Doku-Update öffentlicher Vertrag: erfüllt** (SPEC-003, SPEC-036, Messprotokoll, TSV, KDoc).
7. **Closure-Notiz, Register, Risiko-Ausgänge, Paarungen: offen, Closure-Handlung.** §7 trägt nur den Vermerk „Offen, Träger Nutzer“; Closure-Notiz, Steering-Loop-Eintrag und Risiko-Ausgänge in §6 („offen bis zur Closure“) stehen noch aus. Das Register `BEO-TDS/mitschnitt-luecken-ohne-traeger` hat die Beleg-Datei `evidence/slice-decimaln-laenge-an-messung-angleichen.md` für W-25; `state.md` und `BEO-TDS/aussagen-ueber-messung-hinaus` habe ich nicht auf Fortschreibung geprüft (Planner/Closure).

## AGENTS.md §3.8 (Aussage nicht breiter als Beleg)

- SPEC-003 sagt „Länge 17 für jedes `decimal` und `numeric`, unabhängig von der Precision“, nennt aber im selben Absatz die gemessenen Precisions und dass die dazwischen „nicht einzeln gemessen“ sind, sowie die Grenze (ein Client, Einzelmessung je Instanz): **innerhalb des Belegs**. Der Satz „Wert im ROW höchstens so lang wie die TYPE_INFO“ ist als Folgerung gekennzeichnet (gemessen 5 und 9 Byte, W-25): ehrlich.
- Die KDoc von `DecimalType` nennt dieselbe Grenze. Der Test `Wertbytes sind hoechstens so lang wie die TYPE_INFO` belegt die Ausgabeseite des Encoders, nicht den echten Server.
- **W-25 bleibt offen, Träger Nutzer:** der echte Server schreibt die Wertlänge nach dem Wert (5 Byte bis 4 Byte Betrag, 9 darüber; drei Werte gemessen), der Encoder nach der Precision. Der Vergleich der Mitschnitte sieht das nicht (er vergleicht Werte, nicht die Länge). Weder Slice noch Regel der Ausnahmeliste tragen die Lücke; das Protokoll, die Slice-§7 und die Beleg-Datei führen sie als offen. Nirgends als erledigt geführt (geprüft in SPEC-003, Protokoll, §7, KDoc, Testkommentaren). **Befund-frei.**

## Befunde

- **V-1 (offen, kein Mangel des Slice, Träger Nutzer):** W-25 (Länge der Dezimalwerte im ROW) hat keinen Träger: Entscheidung Nutzer, ob im Slice, als Folge-Slice oder als Ausnahme der Liste.
- **V-2 (Hinweis, Closure):** Risiko 1 des Slice (Wirkung der Länge 17 auf mssql-jdbc) ist ungemessen. Ich habe `make messung-treiber-handles` nicht gefahren (nicht in der Verifier-Zeile der Tabelle, der Slice nennt es im Risikotext); `make e2e` ist grün, benutzt aber den Referenzclient. Der Ausgang bei Closure muss „weiter offen“ oder gemessen sein, nicht „entfallen“. Die Länge 17 im COLMETADATA entspricht dem Vektor der RPC-Eingabeseite von mssql-jdbc (Größe 17, SPEC-003, M-31), was keine Messung der Ausgabeseite ist.
- **V-3 (Hinweis):** `make gates` lief am End-Stand mit Stempel-Belegen (12 s); der Frischbeleg der Klassen `tests` ist `clean-build`, der der Klassen `werkzeug` und `stempel` der Stempel des Implementers am Baum-Hash. Wer Frisch will, fährt `make gates FORCE=1` (hier nicht gefahren).
- Keine DoD-Verletzung, keine Entscheidungs-Verletzung (Option A, Code angleichen, wie am 2026-11-18 entschieden; Option B nicht stillschweigend übernommen), keine Aussage breiter als der Beleg gefunden.

## Urteil

**DoD erfüllt für die Liefer-Punkte 1 bis 3 und die Sensoren der Änderungsart (Punkte 4 bis 6): ja.** Offen sind nur die Closure-Handlungen (Closure-Notiz mit Steering-Loop-Eintrag, Fortschreibung des Registers, Ausgänge der Risiken §6, Paarungen) beim Planner, und die Entscheidung zu W-25 beim **Nutzer**. Der Slice darf nach `done/`, sobald der Planner die Closure-Notiz geschrieben hat und W-25 ausdrücklich als offene Schuld mit Träger Nutzer geführt bleibt (nicht als erledigt).
