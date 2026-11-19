# Review-Report: slice-simulation-mehrere-anweisungen-und-ausgabewert — 2026-11-19

**Review-Art:** Code — gegen Plan, [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md) und die Hard Rules (`AGENTS.md` §3.1 bis §3.8).

**Gegenstand:** Diff `4cd1eed6...HEAD` des Zweigs `slice-simulation-mehrere-anweisungen-und-ausgabewert`, ein Commit `d9292a35`, 21 Dateien (Slice [slice-simulation-mehrere-anweisungen-und-ausgabewert](../plan/planning/in-progress/slice-simulation-mehrere-anweisungen-und-ausgabewert.md)).

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-19

**Eingangs-Kontext:** Slice (§1, §2, §3, §4, §6), [TDS-FA-10](../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) (Entscheidung 14), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (Nr. 11), `AGENTS.md` §3, `SPEC-030`, `SPEC-036`, `TDS-FA-11.a`, Messprotokoll des Referenzabgleichs (A-2, A-16, A-17, M-24), vorherige Reviews am Modul ([fehlertext-unbekannte-tabelle](2026-11-18-slice-fehlertext-unbekannte-tabelle-angleichen.md)), Diff.

**Ausgeführte Sensoren:** keine neu gefahren. Auf `HEAD` liegen die Stempel `werkzeug` und `tests` als grün (`git notes --ref=refs/notes/gates show HEAD`); `make gates` ist damit nicht wiederholt. Nicht gefahren, weil nicht Teil der Reviewer-Pflicht für `src/`: `make clean-build`, `make e2e`, Messläufe, der Live-Lauf. Die Mutationen unten sind im Kopf an Code und Testdaten geprüft, nicht gefahren; die drei Mutationen des Implementers stehen nirgends im Repo (Closure-Notiz §7 ist leer), ich habe sie nicht nachvollzogen. Für den Report: `make docs-check`.

## Findings

### F-1 — MEDIUM
- `kategorie`: MEDIUM
- `quelle`: [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 11, [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 14, Slice §4 (Start) und §6 (Risiko 4), `AGENTS.md` §3.5
- `pfad`: `spec/spezifikation.md` · „| S2 | zwei Auswahlen der Form S1 in einem Text“ gegen `docs/plan/planning/in-progress/slice-simulation-mehrere-anweisungen-und-ausgabewert.md` · „Folge-ADR und Spec-Änderung gehen dem Bau voraus (Start-Bedingung)“
- `befund`: [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 11 (vom Nutzer am 2026-11-06 bestätigt) lässt die Formenliste „nur um Formen wachsen, die das Lastenheft nennt“; `TDS-FA-11` nennt zwei Auswahlen in einem Text nicht, `TDS-FA-10` nennt nur „mehrere Ergebnisse“ der Antwort. Die Entscheidung des Nutzers vom 2026-11-18 sagt „begrenzt auf Formen, die das Backend ohnehin erkennt“; S2 ist eine neue Kombination bekannter Formen, ihre Lesart ist im Slice als unbestätigte Setzung geführt. §4 und §6 verlangen die Folge-ADR vor dem Bau und sind im Diff nicht angepasst, §1 sagt dagegen „nicht geschrieben, entscheidet der Architect“: der Slice widerspricht sich, und die Start-Bedingung ist nicht erfüllt.
- `verifizierbar`: nein (Entscheidung, kein Gate)
- `klasse`: Formenliste wächst ohne ADR-Träger / Start-Bedingung des Slice nicht erfüllt

### F-2 — MEDIUM
- `kategorie`: MEDIUM
- `quelle`: `AGENTS.md` §3.8 (Aussage trägt höchstens so weit wie ihr Beleg), Skill „Aussage über eine Gegenseite ruht auf Zeilen, die nur die eigene Seite schreibt“
- `pfad`: `docs/plan/kompatibilitaetsmatrix.md` · „der Wert des Parameters bleibt der Eingabewert, den die Antwort als RETURNVALUE trägt | gemessen (Lücke 16)“ gegen `referenztest-dotnet/Kompatibilitaet/SzenarienTests.cs` · „ohne ihn bliebe der Eingabewert stehen“
- `befund`: Die Zeile in Abschnitt 7a steht als „gemessen“; der Test sieht nur `ausgabe.Value == 7`, das mit und ohne RETURNVALUE gilt, und sagt das im Kommentar selbst („das RETURNVALUE belegen der Mitschnitt … und die Tests des Backends“). Die Messung am Referenzclient trägt „keine Ausnahme, Wert bleibt 7“, nicht „die Antwort trägt ein RETURNVALUE“; Ändern des Servers auf „kein RETURNVALUE“ lässt den Test grün.
- `verifizierbar`: ja (Mutation: `SelectSeries.echo` leer lassen, `SzenarienTests` bleibt grün; der Mitschnitt-Lauf wird rot)
- `klasse`: Messzeile belegt weniger als die Zeile sagt

### F-3 — MEDIUM
- `kategorie`: MEDIUM
- `quelle`: `AGENTS.md` §3.8, Skill „Zusage ohne ihre gemessene Bedingung im selben Satz“, DoD des Slice („Live-Lauf nicht gefahren“ steht in §7)
- `pfad`: `docs/plan/kompatibilitaetsmatrix.md` · „zwei `SELECT` in einem Text liefern zwei Ergebnismengen (Mitschnitt `mehrteilig`, Live-Schritt `mehrteilig`)“
- `befund`: Der Satz nennt den Live-Schritt als Beleg dafür, dass zwei `SELECT` zwei Ergebnismengen liefern; der Live-Lauf ist in diesem Slice nicht gefahren, die Regel des Live-Schritts in `live-ausnahmen.tsv` ist ohne Lauf gestrichen. Gemessen ist der Live-Schritt nur an der Referenz (2022, 2025) und an der alten Abweisung des Servers; die neue Antwort des Servers im Live-Schritt ist nicht beobachtet. Die Bedingung steht im Messprotokoll (neuer Abschnitt), nicht im Satz der Matrix.
- `verifizierbar`: ja (Verifier: Live-Lauf `LAUF=live-2022`, `live-2025`, falls die Referenzinstanz zur Verfügung steht; sonst Vermerk in §7)
- `klasse`: Beleg zitiert, der nicht gefahren wurde

### F-4 — MEDIUM
- `kategorie`: MEDIUM
- `quelle`: `AGENTS.md` §3.8, Skill „Zusage ohne ihre gemessene Bedingung im selben Satz“, DoD („Aussage zum reinen `OUTPUT`-Parameter als gemessen oder als ‚nicht gemessen‘“)
- `pfad`: `spec/spezifikation.md` · „für einen reinen `OUTPUT`-Parameter und für jede andere Form ist kein Ausgabewert gemessen“ und `docs/user/referenz-konfiguration.md` · „RPC mit `InputOutput`-Parameter an einer einfachen `SELECT`-Anweisung (Simulations-Backend)“
- `befund`: (a) M-24 des Messprotokolls nennt „einen nicht zugewiesenen Ausgabewert (`int`, `nvarchar`)“ und einen `InputOutput` mit NULL ohne Zuweisung, je Instanz gemessen; „kein Ausgabewert gemessen“ ist für den reinen `OUTPUT`-Parameter in dieser Allgemeinheit nicht gedeckt (gemeint ist: nicht an der Auswahl S1, nicht mit dem Wert gelesen; das steht nicht da). (b) Die Zeile der Nutzer-Doku und der Satz der Spezifikation nennen die gemessene Bedingung (`sp_executesql`, ein Parameter, `INTN` 4, kein Treffer der Auswahl) nur teilweise: `echo` läuft für jede RPC-Form, die eine Form S1 mit `InputOutput`-Parameter an das Backend gibt (auch `sp_execute`, `sp_prepexec`, benannte Prozedur), gemessen ist nur `sp_executesql`.
- `verifizierbar`: nein (Doku; Gegenprobe im Messprotokoll M-24 lesen)
- `klasse`: Zusage ohne gemessene Bedingung / „nicht gemessen“ neben vorhandener Messung

### F-5 — MEDIUM
- `kategorie`: MEDIUM
- `quelle`: Skill „Fortschreibung lässt die zweite Fundstelle stehen“ und „Nutzer-Doku nicht komplett gegen den Slice gelesen“, `AGENTS.md` §5 Regel 5
- `pfad`: `spec/spezifikation.md` · „Mehrere Anweisungen in einem Text gelten als nicht erkannt, solange das Clientprofil sie nicht verlangt; die Texte, die d-migrate verlangt“ (`TDS-FA-11.a` Schritt 1) und `docs/user/einbettung-und-betrieb.md` · „Alles andere endet mit `TDS_UNSUPPORTED_SQL`; dass die Liste abschließend ist, ist eine Setzung.“
- `befund`: Der Diff schreibt die Ausnahme „Form S2“ nur in `SPEC-036` und in `testserver-manuell.md` nach. `TDS-FA-11.a` Schritt 1 nennt als einzige Ausnahme Form C (Form D7 fehlt dort schon vorher) und widerspricht S2; die Aufzählung „Was das Backend erkennt“ der Einbettungs-Anleitung nennt zwei Auswahlen nicht und sagt, alles andere ende mit `TDS_UNSUPPORTED_SQL`. Die Schritte 5 und 7 von `TDS-FA-11.a` beschreiben nur ein Resultset je Auswahl.
- `verifizierbar`: ja (Grep „Mehrere Anweisungen in einem Text“, „Alles andere endet“)
- `klasse`: Fortschreibung lässt zweite Fundstelle stehen

### F-6 — MEDIUM
- `kategorie`: MEDIUM
- `quelle`: Skill „Plan-Tabelle nennt die berührten Dateien nicht“
- `pfad`: `docs/plan/planning/in-progress/slice-simulation-mehrere-anweisungen-und-ausgabewert.md` · „Der Implementer-Agent erweitert die Liste in seinem ersten Lauf“
- `befund`: §3 ist im Diff unverändert. Nicht in der Tabelle stehen die neuen `SelectExecution.kt` und `SeriesForms.kt` (Zerlegung beim Bau), `StatementOracleTest.kt`, `StatementPlannerTest.kt`, `docs/user/referenz-konfiguration.md`, `docs/user/testserver-manuell.md`, `docs/plan/spec-abdeckung.md`; die Tabelle nennt `KompatibilitaetsServer.kt` und `ResponseRpcTest.kt`, die der Diff nicht berührt (ohne Vermerk, warum). Im Kopf „Berührte Spec-Stellen“ steht `SPEC-045`, deren Text der Diff nicht ändert (die Ausnahmeliste liegt im Messprotokoll).
- `verifizierbar`: ja (Abgleich Tabelle gegen `git diff --stat`)
- `klasse`: Plan-Tabelle gegen Diff nicht abgeglichen

### F-7 — MEDIUM
- `kategorie`: MEDIUM
- `quelle`: Skill „Eigencode, dessen Entfernung keinen Test rot macht“
- `pfad`: `src/main/kotlin/io/github/pt9912/tdswire/simulation/SelectExecution.kt` · „invalid != null -> PreparedSeries.Rejected(SimCodes.INVALID_VALUE, invalid.reason)“ und „if (!read(part)) return“
- `befund`: Zwei neue Zweige tragen keinen Test. (1) Der `Invalid`-Zweig von `SelectSeries.prepare`: kein Test gibt eine Folge mit einem Wert, den die Spalte nicht hält; ohne den Zweig wirft `ready as PreparedCondition.Ready` eine `ClassCastException` außerhalb von `guarded`. (2) Die Weitergabe des Endes zwischen den Teilen und vor `echo`: scheitert die erste Quelle, oder bricht ein Cancel ab, läuft die zweite Auswahl und ein `OutputParameter` nach dem `Failure` los, ohne dass ein Test rot wird (der Test „Fehler in der zweiten Auswahl“ hat keinen `InputOutput`-Parameter und keine zweite Auswahl nach dem Fehler; ein Fehler der ersten Quelle und ein `InputOutput`-Parameter mit Fehler der Quelle fehlen).
- `verifizierbar`: ja (Mutation je Zweig: entfernen beziehungsweise `read(part)` ohne `return`; alle Tests bleiben nach meiner Lesung grün, nicht gefahren)
- `klasse`: Fehlerzweig ohne Test / Auswahl und Abbruch nur mit einer Eingabe geprüft

### F-8 — LOW
- `kategorie`: LOW
- `quelle`: `TDS-FA-11.a` Schritt 2 („Ohne Zuordnung endet die Anfrage mit `TDS_UNSUPPORTED_SQL`, bevor …“), `SPEC-036` („zwei Auswahlen mit Parametern bleiben nicht erkannt“)
- `pfad`: `src/main/kotlin/io/github/pt9912/tdswire/simulation/SelectExecution.kt` · „parameters.isNotEmpty() ->“ und `src/main/kotlin/io/github/pt9912/tdswire/simulation/StatementPlanner.kt` · „private fun series(selects: List<Statement.Select>, catalog: SimCatalog)“
- `befund`: Zwei Auswahlen mit Parametern sind im Parser erkannt und werden erst im Backend mit `TDS_UNSUPPORTED_SQL` abgewiesen, nachdem der Planer die Objekte geprüft hat: mit einer unbekannten Tabelle in einer der beiden Auswahlen antwortet der Server `TDS_SIM_UNKNOWN_OBJECT` statt `TDS_UNSUPPORTED_SQL`. Der Test `StatementPlannerTest` hält für nicht erkannte Anweisungen den Vorrang der Zuordnung vor der Objektprüfung; die Aussage „nicht erkannt“ in der Spezifikation gilt für diesen Fall nicht.
- `verifizierbar`: ja (Test: Folge mit unbekannter Tabelle und einem Parameter)
- `klasse`: Abweisung an anderer Stelle als die Spezifikation sagt / Reihenfolge der Prüfungen

### F-9 — LOW
- `kategorie`: LOW
- `quelle`: `AGENTS.md` §3.8, Slice §1 („Fehler der zweiten Auswahl bei der Prüfung gegen die Beschreibung … nicht gemessen“)
- `pfad`: `src/test/kotlin/io/github/pt9912/tdswire/simulation/SimulationBackendSelectTest.kt` · „ein Fehler der Zeilenquelle der zweiten Auswahl ist das letzte Ereignis nach dem vollständigen ersten Ergebnis“ und `spec/spezifikation.md` · „(Festlegung, nicht gemessen)“ in der Zeile S2
- `befund`: Die Kennzeichnung „Festlegung, nicht gemessen“ steht für die Abweisung bei unbekannter Tabelle oder Spalte. Das Verhalten bei einem Fehler der Zeilenquelle der zweiten Auswahl nach dem vollständigen ersten Ergebnis und bei einem nicht überführbaren Wert (`TDS_SIM_INVALID_VALUE`, ebenfalls vor dem Lesen) ist ebenfalls nicht gemessen, steht aber weder in der Spezifikation noch im Slice als Setzung; die Wirkung auf dem Draht (`DONE` mit `MORE` der ersten Auswahl, dann ERROR) hat keinen Test am Sequenzierer.
- `verifizierbar`: nein
- `klasse`: Setzung nicht als Setzung gekennzeichnet

### F-10 — LOW
- `kategorie`: LOW
- `quelle`: `AGENTS.md` §3.7 (Kommentar beschreibt, was da ist), Skill-Punkt „Test-KDoc … Konjunktiv“
- `pfad`: `referenztest-dotnet/Kompatibilitaet/SzenarienTests.cs` · „ohne ihn bliebe der Eingabewert stehen: das RETURNVALUE belegen der Mitschnitt“
- `befund`: Der Kommentar trägt eine Grenze (richtig: der Test sieht das RETURNVALUE nicht), formuliert sie aber im Konjunktiv über den abwesenden Zustand. Es ist ein Kommentar in Testcode, nicht KDoc; ich stufe ihn nach dem LOW-Punkt des Skills ein, die Lesart „Kommentar in Code“ (HIGH) wäre dem Wortlaut nach möglich und verlangt keinen Lösungsvorschlag von mir.
- `verifizierbar`: nein
- `klasse`: Konjunktiv über abwesenden Zustand im Kommentar

### F-11 — LOW
- `kategorie`: LOW
- `quelle`: Maintainability (Historie `spec/spezifikation.md` Abschnitt 8; vorherige Slices tragen je eine Zeile)
- `pfad`: `spec/spezifikation.md` · „## 8. Historie“
- `befund`: Das Verhalten von `SPEC-036` (Form S2) und `SPEC-030` (Ausgabewert) ändert sich; die Historie bekommt keine Zeile, anders als bei den Slices vom 2026-11-17 und 2026-11-18. Eine öffentliche Fläche (`SPEC-059`) ist nicht berührt, deshalb ist es kein Verstoß gegen die dort genannte Pflicht.
- `verifizierbar`: ja (Diff)
- `klasse`: Änderung ohne Historienzeile

### F-12 — INFO
- `kategorie`: INFO
- `quelle`: Maintainability
- `pfad`: `src/main/kotlin/io/github/pt9912/tdswire/simulation/SeriesForms.kt` · „if (it.isEmpty()) Recognition.NotRecognized(Unrecognized.UNEXPECTED_TOKEN) else single(it)“
- `befund`: Ein leeres Stück scheiterte auch ohne diesen Zweig (`TokenStream.next()` wirft `UNEXPECTED_END`); der Zweig legt nur den Grund `UNEXPECTED_TOKEN` fest, den kein Test prüft (die Tests nutzen `assertOnlyExtensionFails` ohne Grund). Verhalten sonst gleich.
- `verifizierbar`: nein
- `klasse`: Zweig ohne Wirkung auf den Ausgang

### F-13 — INFO
- `kategorie`: INFO
- `quelle`: `harness/README.md` §Minimal agent workflow (Konflikt in `docs/plan/spec-abdeckung.md`)
- `pfad`: `docs/plan/spec-abdeckung.md` · „| `SPEC-036` | Erkannte Anweisungsformen des Simulations-Backends (vorläufig) | 257 |“
- `befund`: `main` hat sich seit der Basis `4cd1eed6` bewegt (Slice `slice-kompatibilitaetsserver-an-referenz-angleichen`, ändert auch `KompatibilitaetsServer.kt`, `SzenarienTests.cs`, beide Ausnahmelisten); ein Probemerge (`git merge-tree`) ist bis auf `docs/plan/spec-abdeckung.md` sauber. Zuständig: Planner beim Merge (Fassung von `main`, danach neu erzeugen, weil dieser Slice Tests und Tags ändert); der Verifier fährt auf dem End-Stand nach dem Merge.
- `verifizierbar`: ja (`git merge-tree --write-tree HEAD main`)
- `klasse`: Konflikt in erzeugter Datei

## Antworten auf die Schwerpunkte

1. **Zerlegung.** Das `;`-Token kommt aus dem Scanner: ein `;` in Zeichenkette, quotiertem Bezeichner oder Kommentar trennt nicht (Test `erkennt zwei Auswahlen in einem Text`: `'x;y'`, `[b;c]`, `-- ;`, `/* ; */`). Genau ein abschließendes `;` fällt vorher weg (`parse`); ein zweites, ein führendes oder mittleres leeres Stück macht den Text nicht erkannt (getestet: `;;`, `; ;`, `S;S;;`). Grenze zwei: die dritte Auswahl ist `TOO_MANY_STATEMENTS`, ein Fehler eines früheren Stücks gewinnt vor dem Grenzgrund; Form C und D7 laufen vor der Zerlegung und bleiben unberührt (`SELECT 1; SELECT 1` bleibt nicht erkannt, `FixedFormsTest`). Das Orakel (`StatementOracleTest`) zählt die erwarteten Folgen mit `series > 0`. Ohne Befund an der Zerlegung selbst.
2. **Fehlerverhalten der zweiten Auswahl.** Die Abweisung bei unbekannter Tabelle oder Spalte vor dem Lesen ist im Plan, in der Spezifikation (Zeile S2) und im Messprotokoll als „Festlegung, nicht gemessen“ gekennzeichnet. Nicht gekennzeichnet sind der Fehler der Zeilenquelle nach dem ersten Ergebnis und der ungültige Wert (F-9); der `Invalid`-Zweig ist ohne Test (F-7); die Reihenfolge der Prüfungen mit Parametern weicht von der Spezifikation ab (F-8).
3. **Verschiebung ohne Verhaltensänderung.** `SelectExecution` ist Zeile für Zeile gegen die alte Klasse gelesen: gleiche Ereignisfolge für eine Auswahl, `never` ohne Öffnen der Quelle, Schließen höchstens einmal in `finally`, `stopped` vor jedem Teil und vor jeder Zeile, Fehler der Quelle vor und nach `ResultStart`; die Projektion wird je Teil statt im Konstruktor berechnet, ohne Wirkung. Neu und gewollt ist nur `echo` nach dem letzten Ergebnis, und nur wenn alle Teile vollständig endeten. Kein Befund; die Gegenprobe (Mutation des Abbruchzweigs) ist F-7.
4. **Aussagen nicht breiter als der Beleg.** Gemessen sind der Mitschnitt `mehrteilig` und `rpc-ausgabewert`, je Instanz ein Fall; die Zeile S2 der Spezifikation hält „je ein Fall“, den Satz „Setzung der Grenze“ und die Statusbits (`MORE|COUNT`, `COUNT`, Befehl `0x00c1`), die mit der Mitschnitt-Regel übereinstimmen. Zu weit sind die Matrix-Zeilen (F-2, F-3) und der Satz zum reinen `OUTPUT`-Parameter (F-4). Das Messprotokoll sagt „Neue Messungen gibt es nicht … Live-Lauf in diesem Slice nicht gefahren“: richtig.
5. **SPEC-036, SPEC-030, Matrix, Nutzer-Doku.** `SPEC-036` (Zeile S2, Liste „Nicht erkannt“), `SPEC-030`, Matrix Nr. 1, Nr. 16 und Abschnitt 7a, `testserver-manuell.md` und `referenz-konfiguration.md` sind gegen den Code gelesen und stimmen in Form und Umfang (zwei Auswahlen, ohne Parameter, `InputOutput` als Eingabewert), mit den Befunden F-2 bis F-5. Treffer auf die Wörter des Skills in `docs/user/` im Diff: keine; alte Treffer außerhalb des Diffs (`vorläufig` nur als Teil des SPEC-036-Ankers, „bisher“ in `referenz-konfiguration.md` Zeile 113, „nicht gebaut“ in `einbettung-und-betrieb.md` Zeile 388) sind nicht Gegenstand dieses Slice.
6. **Kommentare (§3.7).** Die neuen KDocs in `SeriesForms.kt`, `SelectExecution.kt`, `Statement.kt`, `StatementPlanner.kt`, `SimulationBackend.kt` tragen Zusage, Grenze oder Rang-Zeiger im Indikativ; ein Treffer im C#-Test (F-10). Test-KDocs nennen den gemessenen Fall und kennzeichnen handgebaute Zeilen.
7. **Mutationen (im Kopf).** Rot: `SERIES_LIMIT` auf 3 (Grenztest und `StatementFormsTest`), Zerlegung an Rohzeichen statt Token (Test mit `'x;y'`), `selects.size != recognized.size` entfernt (gemischte Folgen), Parameter-Abweisung entfernt, Echo für alle Richtungen (Test mit `INPUT`, `INPUT_OUTPUT`, `OUTPUT`), Echo vor dem letzten Ergebnis (Reihenfolge im Test), dritte Auswahl durchgelassen. Grün (nicht gefangen): `Invalid`-Zweig entfernt, `if (!read(part)) return` ohne `return`, der leere-Stück-Zweig (F-7, F-12). Der Test des Echos prüft nur einen `InputOutput`-Parameter; die Reihenfolge mehrerer ist nicht gemessen und im Test nicht festgelegt.
8. **Hard Rules.** §3.1 (keine Host-Aufrufe), §3.2 (kein `@Suppress`; Grep über den Diff: kein Treffer), §3.5 (keine Accepted-ADR geändert), §3.6 (kein Gate gelockert, `config/detekt` und `Dockerfile` unberührt): ohne Befund. §3.8 und §3.7: F-2 bis F-4, F-10. Keine `- [x]` im Diff des Slice (Haken sind nicht vorweggenommen).
9. **A-17 nicht berührt.** `SELECT 1/0; SELECT 2` (Mitschnitt `batch-statement-abbrechend`) scheitert schon im Scanner am Zeichen `/`, `SELECT Id FROM gibtesnicht; SELECT 2` (`batch-batch-abbrechend`) im zweiten Stück `SELECT 2` (keine Spalte, keine Tabelle); beide bleiben `TDS_UNSUPPORTED_SQL`, bevor der Planer die Tabelle prüft; die A-17-Regeln in `mitschnitt-ausnahmen.tsv` sind unverändert. `OR`, Konstanten-`SELECT 2`, `WAITFOR` sind nicht angefasst (Test `lehnt andere Folgen als zwei Auswahlen ab` hält `SELECT a FROM t; -- c` und `SELECT 1` in der nächsten Zeile nicht erkannt).
10. **Folge-ADR zu [ADR-0012](../plan/adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 14: nötig, nach meiner Einschätzung.** Entscheidung 14 ist durch [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 11 teilweise ersetzt (nur der erste Satz, die Formenliste). Nr. 11 setzt die Regel, nach der die Liste wächst: Änderung der Spezifikation und nur um Formen, die das Lastenheft nennt. Form S2 ist per Spezifikation geändert (erste Hälfte erfüllt), aber im Lastenheft nur indirekt über `TDS-FA-10` („mehrere Ergebnisse“) verankert; ob das „nennt“ erfüllt, entscheidet nicht der Reviewer und nicht der Implementer. Dass der Bau keine öffentlichen Typen oder Ereignisse hinzufügt, ändert daran nichts, weil es die Regel zur Lesart der Liste betrifft, nicht die Fläche. Eine Folge-ADR (ersetzt oder ergänzt [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 11 um die Form, mit dem Anker `TDS-FA-10` und der Grenze „zwei Auswahlen, ohne Parameter“) oder eine ausdrückliche Bestätigung des Nutzers mit Eintrag in einer ADR schließt F-1; ohne sie bleibt der Slice gegen seine eigene Start-Bedingung (§4) gebaut. Der Reviewer schreibt sie nicht.

## Geprüft, ohne Befund

- `src/main/kotlin/io/github/pt9912/tdswire/simulation/` (`SeriesForms.kt`, `StatementParser.kt`, `SqlScanner.kt`, `Statement.kt`, `StatementPlanner.kt`, `SimulationBackend.kt`, `SelectExecution.kt`): Zerlegung, Verschiebung und Ereignisfolge ohne Befund außer F-7, F-8, F-12; keine Schicht- oder Importregel berührt (nur Pakete `domain`, `port`, `kotlinx.coroutines`).
- `src/test/kotlin/io/github/pt9912/tdswire/simulation/` (vier geänderte Testklassen): Tests tragen die Zusagen der Zerlegung und des Echos; Lücken F-7. Der Test der Grenze zwei (`die Grenze der Anweisungszahl ist zwei`) hat mit `WHERE 1 = 0` als erster Auswahl zwei verschiedene Eingaben (keine einheitliche Eingabe).
- `docs/messungen/referenzabgleich.md` (A-2 und A-16 entfallen, neuer Abschnitt): Stand benennt Zustand und Beleg, keine Chronik; Befunde F-3, F-4.
- `referenztest-dotnet/Vergleich/` (`live-ausnahmen.tsv`, `mitschnitt-ausnahmen.tsv`): entfernt sind genau A-2 (Live eine Regel, Mitschnitt fünf) und A-16 (zwei Regeln); A-17 und übrige Regeln unverändert; die Mitschnitt-Regeln sind gegen die Stempel `tests` grün, der Live-Lauf nicht gefahren (F-3).
- `docs/plan/kompatibilitaetsmatrix.md`, `docs/user/`: gegen Code und Spezifikation gelesen, Befunde F-2 bis F-5.
- `docs/plan/spec-abdeckung.md`: SPEC-030 und SPEC-036 führen die neuen Tests (135 beziehungsweise 257 Treffer); Konflikt mit `main` siehe F-13.
- Hard Rules §3.1 bis §3.6: ohne Befund.

## Urteil

Kein HIGH. Sieben MEDIUM (F-1 bis F-7; F-1 ist die Frage der Folge-ADR, die Architect und Nutzer beantworten), vier LOW (F-8 bis F-11), zwei INFO (F-12, F-13). Der Code bildet die gemessene Antwort ab, die Zerlegung ist an Literalen, Kommentaren und Grenzfällen richtig, und die Verschiebung von `SelectExecution` ändert kein Verhalten. Die Befunde liegen bei der Reichweite der Aussagen (Matrix, Spezifikation, Nutzer-Doku), den nicht nachgezogenen Fundstellen, zwei ungetesteten Zweigen und der offenen ADR-Frage. Zurück an den Implementer (F-2 bis F-11) und an den Architect (F-1), danach Verifier; ein Re-Review ist nötig, wenn die Nachbesserung Quelltext oder Testcode ändert (F-7, F-8).
