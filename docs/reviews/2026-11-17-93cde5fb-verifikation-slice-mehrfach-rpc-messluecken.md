# Verifikationsbericht: slice-mehrfach-rpc-messluecken — 2026-11-17

**Rolle:** Verifier (Modul 11): DoD- und Entscheidungs-Konformität, Plan-vs-Code-Diff. Nicht Review, nicht Validierung. Ich berichte, ich repariere nichts, ich hake keine DoD-Punkte ab und schließe den Slice nicht.

**Gegenstand:** Zweig `slice-mehrfach-rpc-messluecken`, Spitze `93cde5fb`, Basis `main` (Merge-Basis). Plan: [slice-mehrfach-rpc-messluecken](../plan/planning/done/slice-mehrfach-rpc-messluecken.md). Entscheidungen: [ADR-0051](../plan/adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) (`Accepted`), [SPEC-030](../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten), [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog). Messprotokoll: [Messlücken zu mehreren RPCs in einer Nachricht](../messungen/referenzabgleich.md#messlücken-zu-mehreren-rpcs-in-einer-nachricht-messungen-vom-2026-11-17) (W-14 bis W-22). Anforderungen: [TDS-FA-04](../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität).

**Reviews im Zweig:** [Review](2026-11-17-slice-mehrfach-rpc-messluecken.md), [Re-Review](2026-11-17-slice-mehrfach-rpc-messluecken-re-review.md). Beide gelesen; ihre Aussagen sind nicht übernommen, sondern an Stand, Bytes und Lauf geprüft.

## Eigener Lauf (End-Stand `93cde5fb`, sauberer Baum, keine künstliche Last)

| Sensor | Ergebnis | Dauer |
|---|---|---|
| `make clean-build` | Exit 0; Jar, Tests, detekt (`--no-cache`); 1474 `PASSED`-Zeilen aus Haupt- und Testserver-Modul, kein roter Testfall; „übersprungen“ sind nur die Gradle-Aufgaben `checkKotlinGradlePluginConfigurationErrors` | 569 s |
| `make e2e` | Exit 0, „gruen (9 Stufen, 149 s)“; Stufen: 1 Smoke 1 s, 2 Laufzeittest 12 s, 3 Simulation 18 s, 4 Szenario 11 s, 5 Anmeldung 14 s, 6 Lücken 13 s, 7 Synchronität 34 s, 8 eigenes Backend 11 s, 9 Lasttest (Probengröße) 35 s | 149 s |
| `make gates FORCE=1` | Exit 0; alle Klassen gelaufen, Stempelzeilen „liegt schon an 93cde5fb“, `gate-zuordnung` 33 Fälle, `stempel-*` 118/95/102/67 Fälle wie erwartet | 32 s (Layer-Cache) |
| `make docs-check` | Exit 0, 1611 Dateien, 0 Befunde (vor Anlegen dieses Berichts) | 2 s |
| Belegtests: `docker build --target test --no-cache-filter test referenztest-dotnet` (die Stage von `make referenztest-dotnet`, hier ohne Layer-Cache, damit der Lauf Testzahlen zeigt) | Exit 0, `Passed! Failed: 0, Passed: 199, Skipped: 0, Total: 199` | 5 s |
| `docker build --target vergleich referenztest-dotnet` (Übersetzung von `Vergleich/MessungWireTests.cs`, die kein Gate baut) | Exit 0, „Build succeeded“ | 4 s |

Nicht gefahren: `make vergleichslauf-d` (keine Ablage trägt ihre Aussage nicht, siehe unten; EULA, echter Server), Selbsttest der Skripte (`tools/` ist nur durch einen Kommentar in `tools/mk/vergleichslauf-d.mk` berührt; `make gates FORCE=1` fuhr die Klasse `werkzeug` trotzdem), Mutationen (der Re-Review fuhr drei).

**Last und Sperre.** `clean-build` und `e2e` liefen unter der Messsperre `/tmp/claude-1000/-Development-tdswire/messlock` (genommen 15:17:01 UTC, `owner` nannte mich, frei 15:28:59 UTC, nur wenn `owner` mich nannte). Die Sperre war bis dahin belegt: ich wartete ab 13:21 UTC rund 116 Minuten (bis auf 15 s, dann 1 s Takt, weil die Reihe des anderen Slice die Sperre zwischen den Läufen nur kurz freigab); die zwei Stunden waren nicht ganz ausgeschöpft. Die leichten Prüfungen (Nachrechnen, Diff, `docs-check`, Belegtests) liefen währenddessen. Lastmittel danach 3,8; Speicher 13 GiB benutzt von 31, 17 GiB verfügbar. Größte Zeitposten: Wartezeit auf die Sperre (116 min), `clean-build` (Gradle-Test-Stage, je Stage rund 3 min), das Nachrechnen der Ablagen.

**Was `clean-build` und `e2e` für diese Änderung belegen.** Der Diff berührt weder `src/` noch `testserver/` noch `docs/plan/adr/` (`git diff main...HEAD --stat -- src testserver docs/plan/adr`: leer; die 31 Dateien liegen unter `referenztest-dotnet/`, `docs/`, `spec/` und `tools/mk/vergleichslauf-d.mk`). Beide Läufe belegen daher nur, dass der Produktstand unverändert grün ist und keine Zusage des Servers bewegt wurde; sie sagen nichts über die Messung, die am echten SQL Server liegt. Was die Messaussagen trägt, sind die Ablagen (Bytes, unten nachgerechnet) und die 199 grünen Belegtests der Stage `test`.

---

## Nachgerechnet an den Bytes

Gelesen sind die `.messung`-Dateien beider Instanzen (2022, 2025) mit einem eigenen Zerleger im Scratchpad (`dec.py` und `rpc.py` unter `/tmp/claude-1000/-Development-tdswire/3768ddc4-cff6-4152-b0be-815e9ed5f5cb/scratchpad/`; nicht Teil des Repos). Das Nummerierungsmaß `/NN` des Protokolls (00 ist die Anmeldung) deckt sich mit der Reihenfolge der Antworten.

| # | Aussage des Protokolls | Befund am Draht (beide Instanzen) |
|---|---|---|
| 1 | W-14 Fehler im ersten Aufruf: `ERROR INFO DONEINPROC RETURNSTATUS DONEPROC` …; DONEINPROC `MORE\|ERROR`, RETURNSTATUS 2627, DONEPROC `MORE\|0x80` ohne Fehlerbit | bestätigt: `rpc-batch-fehler-erster`: ERROR 2627/Class 14, INFO 3621, DONEINPROC `0x0003` (Befehl `0x00c3`, Zähler 0), RETURNSTATUS 2627, DONEPROC `0x0081`; danach zwei Aufrufe mit DONEINPROC `0x0011` (Zähler 1), RETURNSTATUS 0, DONEPROC `0x0081` und `0x0000` |
| 2 | W-14 Fehler im letzten Aufruf: DONEPROC ohne Bits | bestätigt: `rpc-batch-fehler-letzter`: zwei Aufrufe `0x0081`, dann ERROR 2627, INFO 3621, DONEINPROC `0x0003`, RETURNSTATUS 2627, DONEPROC **`0x0000`** |
| 3 | W-14 Bestand: Zeilen 301, 302 und 300 beziehungsweise 310, 311, 320 | bestätigt an den ROW-Werten der Folgeabfrage (`0x12d`, `0x12e`, `0x12c`; `0x136`, `0x137`, `0x140`) |
| 4 | W-15: Draht gleich W-13 | bestätigt: `rpc-batch-fehler-weiter/03` und `rpc-batch-fehler/03` haben dieselbe Tokenfolge, dieselben Statusbits (`0x0081` am Fehleraufruf) und gleiche Anfrage (drei `sp_executesql`, je vier Parameter); Zeilen 330, 331, 340 (`0x14a`, `0x14b`, `0x154`) |
| 5 | W-16: Übersetzungsfehler 208 im ersten Aufruf: `ERROR RETURNSTATUS DONEPROC` ohne DONEINPROC, DONEPROC `MORE\|ERROR\|0x80` (`0x0083`) | bestätigt (`/01`): ERROR 208/Class 16, RETURNSTATUS 208, DONEPROC `0x0083`, Befehl `0x00e0`; der zweite Aufruf läuft, DONEPROC `0x0000` |
| 6 | W-16: derselbe Fehler im mittleren von drei Aufrufen nach einem Ergebnis | bestätigt (`/03`): erster Aufruf mit COLMETADATA, ROW, DONEINPROC `0x0011`, DONEPROC `0x0081`; zweiter `0x0083`; dritter läuft |
| 7 | W-16: 8134 trägt das Fehlerbit am DONEINPROC, DONEPROC ohne Fehlerbit | bestätigt (`/02`): `COLMETADATA ERROR DONEINPROC RETURNSTATUS DONEPROC` mit DONEINPROC `0x0003`, RETURNSTATUS 8134, DONEPROC `0x0081`; zweiter Aufruf läuft |
| 8 | W-17 Ergebnismengen eine/zwei/eine | bestätigt (`/02`): 4 COLMETADATA und 4 ROW; je Menge DONEINPROC `0x0011`/`0x00c1`, Zähler 1; RETURNSTATUS 0 je Aufruf; DONEPROC `0x0081`, `0x0081`, `0x0000` |
| 9 | W-17 DML und Ergebnis gemischt | bestätigt (`/03`): DONEINPROC Befehl `0x00c3`, `0x00c1`, `0x00c5`, je Zähler 2; DONEPROC `0x0081`, `0x0081`, `0x0000` |
| 10 | W-18 Ordinal 2 in beiden Aufrufen, RETURNVALUE hinter den Ergebnissen, vor DONEPROC; Prozedur per Name: Ordinal 0 | bestätigt (`/04`): `… DONEINPROC RETURNSTATUS RETURNVALUE DONEPROC`, Ordinal 2 mit Werten 5 und 6; `/05`: Ordinal 0, Wert 5, der zweite Aufruf ohne RETURNVALUE |
| 11 | W-19: Referenzclient sendet in den drei Formen nur `sp_executesql` | bestätigt an den Anfragen von `rpc-batch-vorbereitet/01` bis `/03`: alle sechs Aufrufe ProcID 10 (`0xffff 0x000a`), kein `sp_prepexec`, `sp_prepare` oder `sp_execute`; Antwort wie W-11 (`0x0081`, `0x0000`) |
| 12 | W-20: `sp_prepexec`, `sp_execute` als Prozedur per Name werden angenommen, Handles 1, 2, 3, danach 4 | bestätigt: Anfragen `sp_prepexec` (zweimal), `sp_prepexec`, `sp_execute` (zweimal, `@handle` 3), `sp_prepexec` mit nachfolgendem `sp_execute` (`@handle` = 4, Wert 4 in der Anfrage nachgelesen); RETURNVALUE 1, 2 (`/05`), 3 (`/06`), 4 (`/08`), kein ERROR, `sp_execute` DONEINPROC Zähler 1, RETURNSTATUS 0. Das Handle 4 war vor der Nachricht nicht vergeben; der Client trägt den Wert als Erwartung ein, nicht als Verweis auf den Ausgabewert (das Protokoll sagt es so) |
| 13 | W-21: 1024 und 1025 Aufrufe angenommen | bestätigt: Anfrage mit 1024 und 1025 Aufrufen (alle ProcID 10, je ein Parameter), Antwort in einer Nachricht mit 1024 und 1025 RETURNSTATUS 0, DONEPROC `0x0081` an 1023 und 1024 Aufrufen, `0x0000` am letzten, kein ERROR |
| 14 | W-22: 4086 und 4206 Parameter | bestätigt: drei Aufrufe mit 1362 und 1402 Einträgen (Text, Deklarationsliste als `ntext`, dann 1360 und 1400 `int`), zusammen 4086 und 4206; Antwort: drei Ergebnismengen, DONEPROC `0x0081`, `0x0081`, `0x0000`, kein ERROR |
| 15 | Zahlen der Ablage: 36 Dateien je Instanz, 992949 und 993149 Bytes für die zehn neuen; vier große Dateien (137 bis 339 KB) | bestätigt (`ls`, `wc -c`: 36 Dateien je Verzeichnis; 992949 und 993149; `aufrufe-1024` 136993 Bytes, `parameter-4206` 339133 Bytes) |

Beide Instanzen zeigen in allen genannten Austauschen dieselben Tokenfolgen, Statusbits, Zähler und Nummern. Das Nachrechnen beschränkt sich auf die Antwortform und die Zahl der Aufrufe und Parameter; Zeilenwerte habe ich nur dort gelesen, wo das Protokoll sie nennt (Punkt 3 und 4).

---

## Befund je DoD-Punkt

Kein Punkt ist abgehakt; Haken setzt der Planner bei der Closure.

| DoD-Punkt | Befund | Beleg |
|---|---|---|
| Messreihe Antwortform (Fehler erster/letzter, Fehler vor dem ersten Ergebnis, Ergebnismengen; je Instanz, Ablage, Messzeilen und Grenzen) | **erfüllt** | je fünf Szenarien (`fehler-erster`, `-letzter`, `-vor-ergebnis`, `ergebnismengen`) je Instanz unter `referenztest-dotnet/Mitschnitt/messung/2022/` und `2025/`; W-14, W-16, W-17, W-18 stehen mit Messzeilen und Grenzen im Protokoll; Punkte 1 bis 10 der Tabelle oben |
| Messreihe Clientform und Grenzen (`sp_prepexec`/`sp_execute`, `ContinueUpdateOnError`, 4096 und 1024; je Messzeile oder „nicht lieferbar“) | **erfüllt, mit Einschränkung** | W-15, W-19, W-20, W-21, W-22 mit Messzeile je Instanz; „nicht lieferbar“ für `sp_prepexec` von sich aus, `0x80`, Abbruch/Frist und die Grenze oberhalb der Messung steht mit Begründung im Protokoll. Einschränkung: die Grenze ist nur nach unten begrenzt (angenommen sind 1025 und 4206, eine Grenze des echten Servers ist nicht gefunden); das Protokoll sagt das selbst |
| Abgleich der Setzungen (je Setzung belegt/widerlegt/ungemessen; Spec und Matrix führen den Beleg; bei Widerspruch Frage mit Messzeilen in §7 und beim Nutzer) | **teilweise erfüllt, Rest ist Entscheidung des Nutzers** | `SPEC-030` und Matrix führen den Beleg und lassen den Wortlaut der Setzungen stehen; der Widerspruch ist in beiden benannt, die Entscheidung steht aus (siehe Entscheidungs-Konformität). Die Frage „Code angleichen / Zielbild anpassen“ mit den Messzeilen in §7 des Slice fehlt noch: §7 ist leer („Wird bei der Closure gefüllt“); das gehört in die Closure-Notiz, ist also noch nicht fällig, aber nicht erledigt |
| `make docs-check`, `make flaeche`, `make kopplung-zahlen`, `make gates` Exit 0; `make spec-abdeckung` einmal | **erfüllt für `docs-check` und `gates`; `flaeche`, `kopplung-zahlen`, `spec-abdeckung` nicht von mir gefahren** | eigene Läufe oben; `make flaeche` und `make kopplung-zahlen` sind Klasse `immer` beziehungsweise Teil von `werkzeug` und liefen in `make gates FORCE=1` mit (Exit 0); der Re-Review meldet beide mit Exit 0; `docs/plan/spec-abdeckung.md` trägt die Zeile `SPEC-045` mit 112 bestandenen Tests (vorher 100), ich habe das Werkzeug nicht neu gefahren und die Zahl nicht nachgerechnet. `make matrix-zitate` ist nicht gefahren und nicht verlangt: die Matrix änderte in diesem Slice Text von drei Zeilen, aber kein Zitat `Klasse > Testfall` ist neu (Vergleich der Zitate in den `+`- und `-`-Zeilen des Diffs: keine neuen) |
| Review durchgeführt, Report unter `docs/reviews/` | **erfüllt** | Review und Re-Review im Zweig (Pfade oben); der Re-Review nennt N-1 (MEDIUM, Name breiter als Prüfung) und N-2 (LOW); beide sind in `93cde5fb` behoben (siehe Findings) |
| Doku-Update (Protokoll, `SPEC-030`, Matrix, Kopf von `vergleichslauf-d.mk`) | **erfüllt** | Diff: Protokoll (Abschnitt „Messlücken“, W-14 bis W-22; „vierzehn weitere“, 36 Szenarien), `SPEC-030`, `SPEC-003` (nur die Begründung, siehe unten), Matrix (zwei Zeilen und die Drift-Zeile), `tools/mk/vergleichslauf-d.mk` (36 Szenarien, 98 s); die Zahlen 14/36 und 22 + 14 stimmen mit der Ablage überein |
| Closure-Notiz mit Steering-Loop-Lerneintrag | **offen** (nicht fällig) | §7 des Slice: „Wird bei der Closure gefüllt“ |
| Beobachtungs-Register fortgeschrieben | **offen** (nicht fällig) | `docs/plan/planning/observations/` im Diff nicht berührt; die Aufgabe steht in der Closure |
| Jedes Risiko aus §6 trägt einen Ausgang | **offen** (nicht fällig) | §6 trägt weiter „offen bis zur Closure“; der Ausgang des ersten Risikos (Referenzclient erzeugt die Form nicht) ist inzwischen messbar: `sp_prepexec` von sich aus nicht lieferbar (W-19), alle anderen Formen herstellbar |
| Die drei Paarungen (Anker · Folge-Slice · Register) | **offen** (nicht fällig) | Closure |

Die drei Liefer-Punkte der DoD (≤ 3) sind eingehalten.

## Entscheidungs-Konformität

- **[ADR-0051](../plan/adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) unverändert.** `git diff main...HEAD -- docs/plan/adr` ist leer. AGENTS.md §3.5 ist eingehalten.
- **Zwei Abweichungen zwischen Messung und Setzung sind als Widerspruch geführt, nicht still aufgelöst:** Entscheidung 7 (`sp_prepexec` und `sp_execute` in einer Nachricht ergeben `SPEC-013`) gegen W-20; Entscheidung 5 (1024 Aufrufe, 4096 Parameter) gegen W-21 und W-22. `SPEC-030` lässt den Wortlaut der Setzungen stehen („Setzung, nicht gebaut“, „Festlegungen“) und nennt daneben „die Setzung widerspricht der Messung, die Entscheidung des Nutzers dazu steht aus“ beziehungsweise „strenger als der echte Server, die Entscheidung des Nutzers dazu steht aus“; die Matrix trägt dieselbe Aussage („Widerspruch“, Drift-Zeile „Entscheidung des Nutzers steht aus“). Weder `src/` noch `testserver/` ist berührt, kein Folge-Slice angelegt: das ist richtig, solange der Nutzer nicht entschieden hat.
- **Entscheidung 3 (Fehler vor dem ersten Ergebnis, `MORE|0x80`)** war Setzung und ist nun für den Übersetzungsfehler (208) gemessen; für den Laufzeitfehler vor der ersten Zeile (8134) gilt das Fehlerbit am DONEINPROC und DONEPROC ohne Fehlerbit, wie im einzelnen RPC. Beide Fälle stehen im Protokoll und in `SPEC-030`; die Setzung bleibt für den verbindungsschließenden Fehler (nicht gemessen, so benannt).
- **`ntext` ([SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog)).** Der Referenzclient sendet bei 1360 und 1400 Parametern die Deklarationsliste als `ntext` (Bytes: Parameter 2 jedes Aufrufs, Typ `0x63`, Punkt 14). Die Setzung `TDS_UNSUPPORTED_TYPE` für `text`, `ntext`, `image` steht unverändert da (nur „(Setzung)“ ist ergänzt); geändert ist die Begründung: für `text` und `image` weiter „kein gemessener Client sendet sie“, für `ntext` die Messung und „ob der Server dieser Bibliothek eine solche Nachricht annimmt, ist nicht gemessen“. Die Frage „Code angleichen (`ntext` als RPC-Parameter lesen) oder Zielbild anpassen“ steht als „Offen, Entscheidung des Nutzers“ in `SPEC-003`, nicht als Änderung. **Hinweis an den Planner:** der Diff berührt SPEC-003 im Wortlaut der Begründung (ein Satz ersetzt, ein Satz neu); das ist keine Änderung der Setzung, aber doch ein Eingriff in die Spezifikation, den der Auftrag („Setzungen nicht geändert“) nicht ausdrücklich nannte. Ich halte ihn für zulässig, weil er eine falsch gewordene Messaussage zurücknimmt (AGENTS §3.8), nenne ihn aber, damit der Nutzer ihn kennt.
- **Hard Rules.** Keine `@Suppress`-Änderung (kein Kotlin im Diff), Docker-only (alle Läufe über `make` und `docker build`), kein Gate gelockert, keine Datei verschoben (§3.3), `spec/architecture.md` nicht berührt (§3.4), kein Konjunktiv-Kommentar in den geänderten Kommentaren gefunden (§3.7; Stichprobe `vergleichslauf-d.mk`, Testkommentare).

## Plan-vs-Code-Diff

| Plan (§3 des Slice) | Diff |
|---|---|
| `referenztest-dotnet/Vergleich/MessungWireTests.cs` update | geändert (+220 Zeilen), Übersetzung mit Stage `vergleich` geprüft; zehn neue Szenarien mit eigener Tabelle je Szenario (`BEO-TDS/messreihe-szenarien-teilen-den-bestand`) |
| `Mitschnitt/messung/2022/`, `2025/` neu | zehn Dateien je Instanz neu; die 26 älteren Dateien unverändert (`git diff --stat` zeigt 20 neue Dateien, 496 Einfügungen im Ablageordner, keine Löschung) |
| `MessAuswertung.cs`, `MessungBelegTests.cs` update „falls nötig“ | geändert (`ntext` im Zerleger; Belegtests +266 Zeilen); die 199 Tests grün |
| `referenzabgleich.md` update | geändert, Abschnitt „Messlücken“ (W-14 bis W-22) |
| `spec/spezifikation.md` (`SPEC-030`) update | geändert; zusätzlich `SPEC-003` (nicht im Plan; siehe oben) |
| `kompatibilitaetsmatrix.md` update | geändert (zwei Zeilen und die Drift-Zeile) |
| `tools/mk/vergleichslauf-d.mk` update | Kopfkommentar geändert, kein Target |
| `docs/plan/spec-abdeckung.md` update (erzeugt) | eine Zeile (`SPEC-045`: 100 auf 112) |
| `tools/vergleichslauf-d.sh` unverändert | unverändert |
| `docs/plan/adr/0051-…` nicht ändern | nicht geändert |
| nicht im Plan, im Diff | zwei Report-Dateien in `docs/reviews/` (Review, Re-Review); im Slice zwei Tabellenzeilen in §3 (die Zeilen für `spec-abdeckung.md` und `vergleichslauf-d.sh`); kein DoD-Haken, keine Closure-Notiz |

Kein `src/`, kein `testserver/`, kein Skript: der Plan ist eingehalten, die einzige Abweichung ist `SPEC-003`.

## Findings

Keine HIGH, keine MEDIUM.

| ID | Klasse | Befund | Ort |
|---|---|---|---|
| V-1 | LOW | W-15 schließt: „die Option wirkt nur im Client“. Gemessen ist ein Szenario (`INSERT` mit Fehler im mittleren von drei Aufrufen), ein Client in einer Version, und dass Anfrage und Antwort gleich sind. „nur im Client“ sagt mehr, als die Ablage trägt: Sie belegt „gleicher Draht in diesem Szenario“, nicht, dass die Option in anderen Formen (andere Fehlerorte, Ergebnismengen) keinen Draht ändert (AGENTS §3.8). Das Protokoll nennt die Grenze (Szenario, ein Client) im Abschnitt „Grenzen“; der Satz selbst ist breiter. | `docs/messungen/referenzabgleich.md`, Zeile W-15, letzter Satz |
| V-2 | LOW | Die Bezeichnung „zehn Dateien je Instanz, zusammen 992949 und 993149 Bytes“ und „36 Ablagen“ stimmen (nachgerechnet). Die Zahl 36 steht an mehreren Orten (Kommentar im Test, Protokoll, `vergleichslauf-d.mk`); der Test `Der_Referenzclient_trennt_…` prüft die Zahl 24 der Mehrfach-Nachrichten exakt (`== 24`), die 36 steht nur in Kommentar und Dokumenten. Wer eine Ablage hinzufügt, muss sie an mehreren Stellen nachziehen; ein Sensor dafür liegt nur bei der 24. Wartungshinweis (schon als INFO F-10 und N-3 der Reviews geführt), keine Abweichung. | `referenztest-dotnet/Mitschnitt/MessungBelegTests.cs`, Zeile 685; Protokoll; `tools/mk/vergleichslauf-d.mk` |
| V-3 | INFO | Die Reviews-Findings F-1 bis F-6, N-1 und N-2 sind am Stand geprüft behoben: F-1 und F-2 (Wortlaut `SPEC-003`, `SPEC-030`), F-3 bis F-6 (Konstante entfernt, Schwelle `== 24`, Testname nennt „in der ersten Antwortnachricht“, Anfrage-Assertion mit Regex), N-1 (Name nennt jetzt die Grenze „erste Antwortnachricht“; die Prüfung liest bis zum ersten `\|\|`, das ist die erste Antwortnachricht), N-2 (Matrix und Protokoll sagen „Referenzclient“ beziehungsweise „eine Version“). Die Mutationen des Re-Reviews habe ich nicht wiederholt. | `MessungBelegTests.cs`, Matrix, Protokoll |
| V-4 | INFO | Kein Gate baut `Vergleich/MessungWireTests.cs` (Review F-9); ich habe die Stage `vergleich` gebaut („Build succeeded“). Die Szenarien selbst laufen nur im Messlauf gegen den echten Server (EULA, nur lokal); ihr Beleg sind die Ablagen. Der Beleg der Aussagen ist damit „Ablage plus Belegtests ohne Referenzinstanz“, nicht ein Lauf am echten Server in dieser Verifikation. | — |

## Ungemessen (was der Slice nicht trägt)

Aus den Grenzen des Protokolls und aus dem Nachrechnen; jede Aussage trägt nur so weit wie die Ablage (AGENTS §3.8):

- **Fehler außerhalb der gemessenen Formen:** gemessen sind Schlüsselverstoß (2627) im ersten, mittleren und letzten Aufruf, ein Übersetzungsfehler (208) im ersten und mittleren Aufruf, ein Laufzeitfehler (8134) im ersten Aufruf; nicht gemessen sind Fehler in `sp_prepexec` und `sp_execute`, in einer unbekannten ProcID, in verschachtelten Prozeduren und der verbindungsschließende Fehler. Der Übersetzungsfehler im letzten Aufruf ist nicht gemessen; für 208 liegt nur der Fall mit weiterem Aufruf vor.
- **Andere Clients und Versionen:** nur `Microsoft.Data.SqlClient` 7.1.1 auf .NET 10.0.14 (`SqlBatch`, `SqlDataAdapter`); mssql-jdbc, ODBC, FreeTDS, `sqlcmd` sind nicht gemessen.
- **Grenzen oberhalb der Messung:** angenommen sind 1025 Aufrufe und 4206 Parameter; ob und wo der echte Server eine Grenze hat, ist nicht gemessen. Die Schwelle, ab der der Client die Deklaration als `ntext` sendet, ist nicht gemessen.
- **`ntext` am Server dieser Bibliothek:** nicht gemessen; eine Nachricht mit `ntext`-Deklarationsliste an den Testserver ist weder gesendet noch beantwortet worden (SPEC-003, Nutzerentscheidung).
- **Abbruch und Frist mitten in der Nachricht** am echten Server: nicht gemessen (der Referenzclient trifft den Zeitpunkt nicht gezielt); gedeckt nur am Skript-Backend durch Tests dieses Repos.
- **Trennbyte `0x80`:** in keiner der 36 Ablagen je Instanz gesendet; keine Messung herstellbar.
- **`sp_prepexec` und `sp_execute` von sich aus:** der Referenzclient sendet sie in den drei geprüften Formen nicht; andere Formen (mehrere Parameter, andere Typen, `SqlBatch` ohne `Prepare` bei längeren Texten) sind nicht geprüft.
- **Befehle, Typen, Prozeduren:** `INSERT`, `SELECT`, `UPDATE`, `DECLARE` und die Prozedur `wire_proc_ergebnis`; die Parametertypen `int`, `nvarchar`, `ntext` im Zerleger.
- **Wiederholbarkeit:** je Szenario eine Aufnahme je Instanz am 2026-11-17, ein Host, ein Lastzustand (Lastmittel 7 bis 9 laut Protokoll); „beide Instanzen gleich“ heißt gleich in je einer Aufnahme. Streuung zwischen Läufen ist nicht gemessen, die Laufzeitangaben (98 s) sind Einzelwerte.

## Zusammenfassung

Die Messaussagen W-14 bis W-22 sind an den abgelegten Bytes nachgerechnet und stimmen in 15 geprüften Punkten (DONEPROC-Bits bei Fehlern im ersten und letzten Aufruf, `0x0083` bei 208 gegen Fehlerbit am DONEINPROC bei 8134, Ergebnismengen 1/2/1, Ordinal 2 und 0, `sp_prepexec` ohne Eigenversand, Handle 4 in derselben Nachricht, 1024/1025 Aufrufe, 4086/4206 Parameter, Zahl und Größe der Ablagen). `clean-build`, `e2e`, `gates FORCE=1`, `docs-check` und die 199 Belegtests sind grün; `src/` und `testserver/` sind nicht berührt, die Läufe belegen also nur den unveränderten Produktstand. [ADR-0051](../plan/adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) ist unverändert; die zwei Abweichungen (Entscheidung 7 gegen W-20, Entscheidung 5 gegen W-21/W-22) und die `ntext`-Frage stehen als offene Entscheidung des Nutzers in `SPEC-030`, `SPEC-003` und der Matrix, ohne dass eine Setzung im Wortlaut geändert wäre; `SPEC-003` ist in der Begründung angepasst (siehe oben). Ein LOW (V-1: „nur im Client“) und zwei INFO. Offen sind nur die Closure-Punkte (Notiz mit Lerneintrag und der Frage an den Nutzer in §7, Register, Risiko-Ausgänge, Paarungen) und die Nutzerentscheidung; kein Haken gesetzt, nicht geschlossen.
