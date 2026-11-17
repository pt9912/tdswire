# Review-Report: slice-mehrfach-rpc-unterstuetzen, Re-Review der Nachbesserung — 2026-11-17

**Review-Art:** Code, Re-Review (gegen Plan, Spec, ADR und Hard Rules; nicht gegen die DoD)

**Gegenstand:** `git diff b898101e..13ef2e4e` auf dem Zweig `slice-mehrfach-rpc-unterstuetzen`: `5cb52047` (Code, Tests, Skripte), `5cad1c4b` (Doku, Plan, erster Report), `13ef2e4e` (KDoc, Testkommentar). Der Commit `09aab294` ([ADR-0051](../plan/adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md), `Proposed`) liegt danach und ist nicht Gegenstand.

**Vorgänger:** [2026-11-17-b898101e-slice-mehrfach-rpc-unterstuetzen.md](2026-11-17-b898101e-slice-mehrfach-rpc-unterstuetzen.md) (kein HIGH, F-1 bis F-5 MEDIUM, F-6 bis F-9 LOW, F-10 bis F-13 INFO).

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-17

**Eingangs-Kontext:** [TDS-FA-04](../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0039](../plan/adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md), `SPEC-009`, `SPEC-029`, `SPEC-030`, `AGENTS.md` §3.7, §3.8; der erste Report; die Diffs der drei Commits selbst gelesen, nicht aus dem Bericht des Implementers übernommen.

**Selbst gefahren (Vordergrund, keine künstliche Last, Last 6 bis 7, 12 GB verfügbar):**

- `make docs-check`: Exit 0 (1555 Dateien, 0 Befunde)
- `docker build --no-cache --target skript-selbsttest .`: Exit 0, 28 s; `log-muster-selbsttest: gruen`, die drei neuen Mutationen je rot (3, 1, 1 Fälle)
- Mutation zu F-3 (Stage `test` an einer Kopie des Standes, 1267 Tests, 202 s): siehe F-3

Nicht gefahren: `make gates`, `clean-build`, `e2e`, `make kompatibilitaet KATEGORIE=Luecken`, eine Mutation zu F-5 (Begründung dort).

---

## Je Finding des ersten Reports

| ID | Stand | Beleg |
|---|---|---|
| F-1 | offen, nicht im Umfang der Nachbesserung | [ADR-0039](../plan/adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md) ist im Diff unberührt (`git diff --stat` auf `docs/plan/adr/`: leer). Die Folge-ADR ist als [ADR-0051](../plan/adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) (`Proposed`, `09aab294`) nachgereicht und hier nicht bewertet; bis zur Annahme sagt [ADR-0039](../plan/adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md) weiter das Gegenteil von Spec und Code. |
| F-2 | behoben | Plan §3 trägt die Tabelle „Weitere berührte Dateien“ mit allen im ersten Report genannten Dateien und die zusätzlichen `SPEC-*`; gegen den Diff abgeglichen (Rest: N-3). |
| F-3 | behoben | Test `Alle Aufrufe einer Nachricht tragen dieselbe Frist und Sitzung`. Mutation gefahren: die Frist im Schleifenrumpf von `carryOut` je Aufruf neu berechnet (`times.now() + …` in `QueryRequest(...)`): genau ein Test rot, dieser (`1267 tests completed, 1 failed`). Lastunabhängig: die Uhr ist `now = { tick.also { tick += 1000 } }`, kein Wanduhr-Wert; die Frist 600 s läuft real nicht ab, der Test endet in Millisekunden. Sitzungsgleichheit trägt der Test nicht (alle Aufrufe haben ohnehin `s1`), die Zusage der Frist trägt er. |
| F-4 | behoben dem Wortlaut nach, neuer Rest | `docs/plan/e2e-abdeckung.md` und `tools/e2e.sh` nennen die Gelingen-Aussage jetzt als „Erwartung der Stufe“, Lauf „steht aus“. Der Zusatz ist selbst ein Zustand, der nach dem Lauf falsch wird (N-1). |
| F-5 | behoben | `RpcSequenceAbortTest`: beide Tests fragen `doneProcs(session)` mit Gleichheit auf der ganzen Liste: Frist `[MORE|RPC_IN_BATCH, ERROR]` (das erste DONEPROC trägt `MORE|0x80`, das schließende kein `MORE`), ATTENTION `[MORE|RPC_IN_BATCH]` plus `DONE[ATTN]` am Ende ohne ERROR; je genau ein `cancel` für `("s1","r1")`, Folgeanfrage beantwortet (3 Anfragen, 2 Antworten). Die Konstruktion ist deterministisch: `collecting` meldet erst der zweite Ablauf (`suspendedAfter` nur im zweiten Skript), die Zeit steuert `SteeredPause`, `awaitWaiting(30.seconds)` ist nur die Wartegrenze eines hängenden Tests. Eine Mutation habe ich hier nicht gefahren; der Schluss „rot, wenn die Antwort des ersten Aufrufs verworfen wird oder der Abschluss `MORE` trägt“ folgt aus der Gleichheit auf der Liste, nicht aus einem Lauf. |
| F-6 | behoben mit Rest | Test `Ein Fehler nach dem Beginn einer Anweisung traegt die gemessene Form mit MORE und 0x80` prüft RETURNSTATUS `[0, 2627, 0]`, DONEPROC `[0x0081, 0x0081, 0x0000]`, DONEINPROC `[MORE|COUNT, MORE|ERROR, MORE|COUNT]`. Er baut W-13 nicht nach (N-2). |
| F-7 | behoben | W-13 nennt die Clientseite jetzt mit ihrer Quelle (Beobachtungszeile des Laufs, nicht die Ablage) und sagt, dass die Ablage nur Bytes hält. Die Aussage ist auf ihren Beleg zurückgenommen. |
| F-8 | nicht angefasst | Die Zuordnung der zwei gemeinsam gefahrenen Mutationen steht im Diff nicht; die Wirkung der ersten habe ich im ersten Lauf einzeln gemessen. Als LOW bestehen lassen. |
| F-9 | behoben | `log_ohne_fehlercode` liest `"event":"request_failed"` und `"error_code":"<Code>"` literal; die Feldnamen stimmen mit dem Adapter überein (`EventProperty("errorCode", "error_code")` in `TelemetryJson.kt`) und mit der Positivprobe derselben Stufe (`grund request_failed error_code TDS_UNSUPPORTED_TYPE` nutzt dieselben Namen). Selbsttest: Fälle gut / Protokollfehler rot / Code in anderem Ereignis zählt nicht, drei Mutationen (Negation, Feldname, Ereignisfilter) je rot, gefahren. Auf einem leeren Log wahr (`! grep … | grep -q` negiert die ganze Pipeline); die Positivprobe `login_accepted` steht in `kompatibilitaetstest.sh`. Nicht gelaufen gegen einen Serverprozess, das nennt der Kommentar. |
| F-10 | als Zustand vermerkt | Die Bestätigung des Nutzers (nur `0xFF` trennt, 4096 je Nachricht, 2026-11-17) steht in Spec, Messprotokoll-Grenzen und Plan als bestätigte Festlegung, weiter ohne Messung und mit dem Failure-Szenario („Batch darüber endet in `INVALID_RPC_FORM`“). Ich kann die Bestätigung selbst nicht prüfen; sie ist hier die genannte Quelle. |
| F-11, F-12 | unverändert | kein Auftrag der Nachbesserung |
| F-13 | behoben | Spec: „ein späterer Aufruf, auch ein Strukturfehler darin, wird nicht gelesen, die Nachricht ergibt `SPEC-013`“. Der Test `Hinter einer unbekannten ProcID endet das Lesen` hängt Bytes eines abgeschnittenen Aufrufs an und erwartet genau einen Folgeaufruf `UnknownProcedure(11)`; er trägt die Aussage. |

## Neue Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| N-1 | LOW | Zwei Zustandsaussagen über einen noch ausstehenden Lauf: der Kommentar „der Lauf gegen den Serverprozess steht aus“ und der Zusatz „Lauf der Stufe 6 mit diesem Test steht aus“ in `tools/e2e.sh` und `docs/plan/e2e-abdeckung.md` (beide Texte sind per Prüfung gleich zu halten). Nach dem Lauf des Verifiers sind sie falsch und müssen in beiden Dateien nachgezogen werden; der Kommentar im Skript trägt dann keine der Klassen mehr. | `AGENTS.md` §3.7 (Zustand statt Zusage) | `tools/kompatibilitaetstest.sh` · „der Lauf gegen den Serverprozess steht aus“ | ja: Grep nach „steht aus“ | Zustand mit Verfallsdatum in Skript und Abdeckungssicht |
| N-2 | LOW | Das KDoc des F-6-Tests nennt „die gemessene Form … (W-2 und W-13)“. Die Eingabe ist ein offenes Ergebnis mit Zeile (W-2), nicht die Anweisung ohne Ergebnis aus W-13; ein INFO 3621 prüft der Test nicht. Das Backend-Skript sagt das nicht. Die Matrix führt den Test ohne Einschränkung unter „gemessen“. | `AGENTS.md` §3.8 | `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RpcSequenceDispatchTest.kt` · „Die gemessene Form eines Fehlers nach dem Beginn einer Anweisung (W-2 und W-13)“ | nein | Test breiter benannt als seine Eingabe |
| N-3 | LOW | `docs/plan/spec-abdeckung.md` ist nach den neuen Tests (`RpcSequenceAbortTest`, zwei Fälle in `RpcSequenceDispatchTest`, mit `SPEC-009`, `SPEC-029`, `SPEC-030`) nicht neu erzeugt (letzter Stand `b898101e`, kein Treffer auf die Namen). Der Plan nennt die Datei als berührt; die Tabelle der Änderungsart „Tests oder `@spec`-Tags“ verlangt `make spec-abdeckung` einmal. | `harness/README.md` §Minimal agent workflow | `docs/plan/spec-abdeckung.md` · „`SPEC-029` | ATTENTION und Wiederverwendung der Verbindung“ | ja: `make spec-abdeckung` | Abdeckungssicht hinter dem Teststand |
| N-4 | INFO | Der Lauf `ServerSmokeTest` war im Lauf des Implementers einmal rot („Flake“); der rote Testfall steht nicht mit Namen im Plan. Der Diff berührt weder `runtime/` noch den Serverprozess. In meinem Lauf (Stage `test`, natürliche Last 6 bis 7) liefen alle sechs Fälle von `ServerSmokeTest` grün. Damit ist der Flake weder reproduziert noch widerlegt; als Befund gilt „nicht belegt“, ein Fix ist nicht im Diff. | Reviewer-Skill „Roter Lauf eines Wanduhr-Tests außerhalb des Diffs“ | `src/test/kotlin/io/github/pt9912/tdswire/runtime/ServerSmokeTest.kt` · „Der Prozess-Stop beendet eine ruhende Verbindung und bricht die laufende Anfrage nach der Frist ab“ | nein | Roter Lauf außerhalb des Diffs ohne benannten Fall |
| N-5 | INFO | Der Selbsttest `log_ohne_fehlercode` nutzt die Fixture `leer` nicht; „wahr auf leerem Log“ steht im Kommentar, ist aber kein Fall. Die Aussage folgt aus der Bauart (Negation der Pipeline). | Maintainability | `tools/log-muster-selbsttest.sh` · „Log ohne Protokollfehler“ | ja | Zusage des Kommentars ohne eigenen Fall |

## Prüfung der Aussagen (`AGENTS.md` §3.8)

- Spec, „Setzung, am Server dieser Bibliothek durch Tests belegt“ für Abbruch und Frist mitten in der Nachricht: die zwei neuen Tests tragen es; der Satz sagt „nicht am echten Server gemessen“ im selben Satz.
- W-13 Clientseite: Quelle und Grenze benannt (F-7).
- KDoc `QueryRequest` und `QueryBackend.execute`: „ruft … je Aufruf nacheinander mit derselben `requestId` und derselben `deadlineEpochMillis`“ stimmt mit `RequestLoop.carryOut` (Frist einmal je Nachricht) und dem Test aus F-3 überein; „`cancel` nennt die `requestId` des laufenden Aufrufs“ trägt der Abbruchtest. Nur KDoc, kein Mitglied neu: kein Eintrag im Änderungsprotokoll der `SPEC-059` nötig.
- Die „Nutzerbestätigung“ steht als Zustand, nicht als Chronik.

## Negativbefunde

Geprüft, ohne Befund (außer wie oben):

- `src/main/kotlin/` (`domain/QueryRequest.kt`, `port/QueryBackend.kt`): nur KDoc, keine Signaturänderung; Zeilenlänge im dritten Commit.
- `src/test/kotlin/.../protocol/dispatch/`: kein `@Suppress`, kein Wanduhr-Wert (Uhr und Pause gesteuert); Mutation zu F-3 rot.
- `tools/` (`log-muster.sh`, `log-muster-selbsttest.sh`, `kompatibilitaetstest.sh`, `e2e.sh`): Host-Aufrufe nur `docker`; Selbsttest frisch grün.
- `config/`, `build.gradle.kts`, `Dockerfile`, `tools/suppression-check.sh`: nicht im Diff.
- `docs/plan/adr/0039-…`: unverändert.
- `docs/messungen/referenzabgleich.md`, `docs/plan/kompatibilitaetsmatrix.md`, `spec/spezifikation.md`: Setzungen und Festlegungen benannt; die neuen Zitate der Matrix (drei Testfälle) stehen als Test im Bestand (Namen gegen den Quelltext verglichen); `make matrix-zitate` nicht gefahren.
- `docs/user/`: im Nachbesserungs-Diff nicht berührt.

## Re-Review-Pflicht

Eine weitere Nachbesserung an Quelltext, Testcode oder Skripten (N-1, N-2 sind Kommentar und Doku; N-3 erzeugt nur eine Datei) braucht nur dann einen weiteren Re-Review, wenn sie Logik ändert.
