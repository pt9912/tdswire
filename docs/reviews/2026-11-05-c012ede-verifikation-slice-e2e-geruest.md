# Verifikationsbericht: slice-e2e-geruest (HEAD c012ede)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: [slice-e2e-geruest](../plan/planning/done/slice-e2e-geruest.md) (Zweig `slice-e2e-geruest`, Basis `main` 3cfdd01, Commits `b27e292`, `4ac95e6`, `08ff794`, `c012ede`) gegen DoD §2, Plan §3, [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-FA-08](../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-RB-04](../../spec/lastenheft.md#tds-rb-04--externes-backend) und `AGENTS.md` §3.7, §3.8. Eingang: Review-Report [2026-11-05-08ff794-slice-e2e-geruest](2026-11-05-08ff794-slice-e2e-geruest.md) (0 HIGH, 3 MEDIUM, 3 LOW, 3 INFO).
* Selbst gefahren (Arbeitsbaum sauber, HEAD `c012ede`): `make e2e-abdeckung` (Ausgabe: „unveraendert … 7 Stufen, 7 Deklarationen, 0 ohne Kennung“, Exit 0), `make docs-check` (724 Dateien, 0 Befunde), `make skript-selbsttest` (Exit 0, aber alle Docker-Schichten `CACHED`, der Lauf hat also nichts neu geprüft; deshalb zusätzlich `bash tools/e2e-selbsttest.sh` direkt: „ok (Abbruch an Stufe 1, 2, 5 und 7, alle gruen, Teil Nicht belegt in 7 Stufen)“). Gelesen gegen: `tools/e2e.sh`, `tools/mk/e2e.mk`, `repo.mk`, `.github/workflows/e2e.yml`, `harness/README.md` (Zeilen `make e2e`, `make kompatibilitaet`, `make laufzeittest`), `build.gradle.kts`, `KompatibilitaetsServer.kt`, `IntegrationServer.kt`, `Main.kt`, `ErrorCode.kt`, `docs/plan/e2e-abdeckung.md`, `spec/lastenheft.md` (FA-08, RB-04).
* Nicht selbst gefahren: `make e2e` (ca. 6 Min), `make gates`. Die Messung des Planners habe ich rechnerisch und gegen das Skript geprüft, nicht reproduziert. Es ist keine Produktionsdatei geändert (der Diff 3cfdd01..HEAD betrifft nur Markdown).

## Gesamturteil

**Die Planungsänderungen sind konform und die Befunde F-1 bis F-6 sind behoben, mit einer Einschränkung bei F-1 (V-1). Der Slice ist aber noch nicht closure-fähig: DoD 2 (Laufzeit warm und kalt) ist nur benannt, nicht erfüllt, und DoD 3 (Übergabe der Lücken) ist für FA-08/RB-04 getragen, für den Rest der Folge-Stufen-Tabelle nicht belegt.** Kein HIGH, 2 MEDIUM, 1 LOW, 3 INFO.

Bedingungen für die Closure:

* B-1: DoD 2 messen oder entscheiden. Entweder Warm-Lauf, Kalt-Lauf (frischer buildx-Builder) und die Einzelläufe der sieben Stufen messen und in §7 mit Datum, Last und Cache-Zustand eintragen; oder den Wortlaut von DoD 2 mit Begründung ändern (Entscheidung des Planners oder Nutzers, keine stille Aufweichung nach `AGENTS.md` §3.8). Ohne eines von beiden darf das Häkchen nicht stehen.
* B-2: DoD 3 vervollständigen (V-1, V-2): die Folge-Stufen-Tabelle in §3 gegen die erzeugte Sicht aktualisieren oder ihr Alter am Kopf der Tabelle ausdrücklich zur Lesehilfe erklären; für [TDS-FA-10](../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte) und [TDS-FA-07](../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) (Träger in `done/`, keine Stufe) in §7 einen Befund mit Ausgang nennen.
* B-3: Die Aussage zum Backend-Fehlpfad (V-2) auf den Bestand einschränken, in §3 und im Folge-Slice §1.
* B-4: Die offenen Pflichten, die der Auftrag ausdrücklich als „kein Befund“ führt (Closure-Notiz, Register, Risiko-Ausgänge 1, 2, 4 in §6, DoD-Haken), bleiben beim Planner.

## Prüfung der Review-Befunde F-1 bis F-6

| ID | Behauptung | Bestand | Urteil |
|---|---|---|---|
| F-1 | Lückenaussage zu FA-08/RB-04 auf den Bestand zugeschnitten | Zeile in §3 und Befund in §7 nennen jetzt Stufe 4 bis 7 gegen `kompatibilitaetsserver`; das trifft zu: `KompatibilitaetsServer.kt` übergibt `BackendSource.Custom(RecordingBackend(SimulationBackend(...)))` an `runTestserver`, das `TdsServer.start` ruft (`Main.kt:77`); `build.gradle.kts` (`testserverOhneAssoziation`, Zeilen 103 bis 111) bricht bei Assoziation mit `main`; die Grenze „`internal` ist im Bytecode öffentlich“ steht wörtlich im Kopf des Einbettungstests (`build.gradle.kts` Zeile 45 bis 47). Die Sicht `docs/plan/e2e-abdeckung.md` deklariert FA-08 und RB-04 in keiner Stufe (geprüft: kein Treffer). Die Stufe-3-Zeile (`TdsServer.start`, nicht mehr `runServer(Wiring(...))`) stimmt mit `Main.kt` und `IntegrationServer.kt` (`runTestserver { BackendSource.Simulation(...) }`). Offen: der Halbsatz zum Fehlpfad ist breiter als der Bestand, siehe V-2. | **behoben, mit Einschränkung (V-2)** |
| F-2 | „Warm-Wert“ und „96 Schritte CACHED“ entfernt | §7 sagt: „ein Wert unter hoher Last mit nicht kontrolliertem Cache, weder ein Warm- noch ein Kaltwert“; die CACHED-Zahl ist als nicht abgelegt und nicht nachprüfbar gekennzeichnet. | behoben |
| F-3 | Altmessung als „Altstand 2026-10-31, vier Stufen“ gekennzeichnet | Der Absatz trägt jetzt Stand und Geltungsbereich in der Überschrift („gilt nicht für den heutigen Bestand mit sieben Stufen“). | behoben |
| F-4 | Streuung bewertet, Last-Angabe des Implementers als unbelegt gekennzeichnet | Summen: 58+113+57+60+24+24+24 = 360 und 76+23+99+60+17+20+22 = 317, beide richtig und gleich den Gesamtwerten. Differenzen: 317−360 = −43 s (−11,9 %, genannt „−12 %“), Stufe 1 +18, Stufe 2 −90, Stufe 3 +42, Stufe 5 −7: alle richtig (Stufe 6 mit −4 s ist nicht genannt, ohne Folge). Die Folgerung „Streuung aus zwei Einzelläufen nicht zu beziffern“ trägt höchstens so weit wie der Beleg. | behoben |
| F-5 | Folge-Slice in die Welle-Tabelle eingetragen | `welle-qualitaet-grundlage.md` Zeile 69 führt `slice-e2e-backend-ausserhalb-modul` mit FA-08 und RB-04. | behoben |
| F-6 | Zweigname aus `Verantwortlich:` entfernt | Feld lautet „pt9912.“. | behoben |

## DoD-Punkte (§2)

### DoD 1, Skript und Marker: bestätigt

* `tools/e2e.sh` (Präfix `e2e`) definiert `e2e_abdeckung` vor der ersten Stufe und führt sieben Stufen nacheinander aus; ein Docker-Lauf gleichzeitig (jede Stufe ein `make`-Aufruf), Abbruch an der ersten roten Stufe (`lauf`, Exit 1; vom Selbsttest gehalten, selbst gefahren).
* `repo.mk` setzt `E2E_ABDECKUNG_QUELLE = tools/e2e.sh`, `E2E_ABDECKUNG_PRAEFIX = e2e`, `E2E_ABDECKUNG_SPEC = spec/lastenheft.md` (Zeilen 35 bis 37), einfaches `=`.
* `make e2e-abdeckung`: „unveraendert“, sieben Deklarationen, null ohne Kennung. Jede Kurzbeschreibung trägt „Nicht belegt: …“ (vom Selbsttest gehalten).
* Abweichung Plan gegen Code: V-3.

### DoD 2, Aufruf und Laufzeit: Aufruf bestätigt, Laufzeit nur benannt

Erfüllt:

* `make e2e` (`tools/mk/e2e.mk`) ruft das Skript; Zeile in `harness/README.md` §Sensors vorhanden (Zeile 97, „7 Stufen“, Bindung [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md)).
* [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md): `e2e` steht in keiner `GATE_CHECKS`-Anweisung (geprüft: `grep` auf `GATE_CHECKS += e2e|laufzeittest|simulations|kompat` ohne Treffer); `laufzeittest` und `simulations-integration` haben keine Gate-Zeile mehr; `.github/workflows/e2e.yml` ruft `make e2e`.

**Nicht erfüllt, nur benannt: „Die Laufzeit warm und kalt ist gemessen und mit der Summe der einzelnen Läufe der sieben Stufen verglichen.“**

* Warm: für sieben Stufen nicht gemessen. Die beiden Werte vom 2026-11-05 (360 s bei angegebener, unbelegter Last 15; 317 s bei Last 20, Cache nicht kontrolliert) sind laut §7 selbst weder Warm- noch Kaltwert.
* Kalt: für sieben Stufen nicht gemessen. Der Kaltwert 323 s stammt vom Altstand mit vier Stufen (2026-10-31).
* Vergleich mit der Summe der Einzelläufe: für sieben Stufen nicht gemessen. Der Vergleich des Altstands (Differenz 1 s) gilt für vier Stufen.
* §7 sagt das ehrlich, in jeder Messzeile. Das ist Transparenz, kein Beleg. Die Messung für sieben Stufen fehlt vollständig; das Häkchen darf mit dem vorliegenden Stand nicht gesetzt werden. Das Messen allein ist Sache von Last und Zeit (ca. 6 Min je Durchgang und ein Kaltlauf), nicht eine Frage des Zuschnitts.
* Gegenprobe zur Plausibilität: 317 s und 360 s liegen bei der Hochlast-Angabe in `harness/README.md` („339 s (hohe Last)“), nicht beim Warm-Wert („103 s, Last 3 bis 8“). Ein Widerspruch besteht nicht; dass die README-Zahlen „Einzelmessungen 2026-11-01“ sind, steht dort.

### DoD 3, Übergabe der Lücken: für FA-08/RB-04 getragen, im Ganzen nur teilweise

* Getragen: Die Zeile zu [TDS-FA-08](../../spec/lastenheft.md#tds-fa-08--backend-anbindung)/[TDS-RB-04](../../spec/lastenheft.md#tds-rb-04--externes-backend) nennt den Folge-Slice [slice-e2e-backend-ausserhalb-modul](../plan/planning/done/slice-e2e-backend-ausserhalb-modul.md); dessen DoD 2 verlangt die Stufe mit den beiden Kennungen. Er liegt in `open/`, steht in der Welle-Tabelle, hat Link-Verweise, die auflösen (`make docs-check` grün), und grenzt sich gegen Stufe 4 ab. Der Vorgänger-Träger [slice-integrations-einstieg](../plan/planning/done/slice-integrations-einstieg.md) in `done/` führt keinen E2E-Punkt (geprüft: 0 Treffer); §7 hält das als Befund fest.
* Eingegrenzte Übereinstimmung mit den Trägern: Geprüft habe ich die DoDs von `slice-cancel-referenzclient-szenario`, `slice-abbruch-ressourcen-shutdown`, `slice-betrieb-zustandsereignisse`, `slice-qa-performance` und `slice-e2e-backend-ausserhalb-modul`: jede nennt einen Lauf oder eine Stufe. Nicht verglichen habe ich den Text der DoD-Zeilen Wort für Wort gegen die Lücken-Beschreibung.
* Nicht getragen: V-1 (Tabelle ist gegenüber der Sicht überholt) und die Träger in `done/` ohne Stufe (FA-10, FA-07 Beenden, FA-11 Negativfall: der Träger `slice-sim-aenderung-integration` hat 0 E2E-Treffer). DoD 3 sagt: „ein Träger in `done/` ohne seine Stufe ist ein Befund dieses Slice, kein stilles Auslassen“. §7 benennt den Befund nur für `slice-integrations-einstieg`.
* Das Urteil des Slice selbst („benannt und getragen, nicht geschlossen“) ist ehrlich und trifft für FA-08/RB-04 zu.

## Findings

| ID | Kategorie | Befund | Pfad |
|---|---|---|---|
| V-1 | MEDIUM | **Folge-Stufen-Tabelle in §3 ist gegenüber der erzeugten Sicht überholt.** Die Tabelle ist mit „Stand der Bestandsaufnahme 2026-10-31“ überschrieben, steht aber im Slice, der bei der Closure gilt, und DoD 3 verlangt Übereinstimmung. Widersprüche zu `docs/plan/e2e-abdeckung.md` (Stufen 4 bis 7): „[TDS-FA-04](../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-05](../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen) … kein Lauf über die Prozessgrenze“ (Stufe 4 deklariert FA-04 und FA-05, Stufe 5 und 6 FA-04); „[TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-RB-02](../../spec/lastenheft.md#tds-rb-02--externer-client): Stufe 3 trägt nur das Simulations-Backend“ (Stufe 4 deklariert beide); „[TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit) (TLS-Modus): am Prozess nur Rohbytes ohne TLS“ (Stufe 5 deklariert QA-03 mit TDS 8.0); „[TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) Negativfall fehlt in Stufe 3“ (Stufe 4: „außerhalb der Formen“); „[TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität) … trägt kein offener Slice“ (Stufe 6 deklariert QA-02). Ohne Stufe in der Sicht bleiben dagegen [TDS-FA-10](../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte) („Nicht belegt: mehrteilige Antworten“ in Stufe 4) und [TDS-FA-07](../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe); ihr Träger [slice-qa-kompatibilitaet](../plan/planning/done/slice-qa-kompatibilitaet.md) liegt in `done/`. Der Review (F-1) hat nur die FA-08-Zeile gelesen; die übrigen Zeilen stehen nicht im Diff und wurden nicht beanstandet. Failure-Szenario: Der Leser der Closure nimmt die Tabelle als Stand der Lücken und plant Stufen, die es schon gibt, oder übersieht, dass FA-10 keinen Träger mehr hat. | `slice-e2e-geruest.md` §3 Folge-Stufen; `docs/plan/e2e-abdeckung.md` |
| V-2 | MEDIUM | **Die neue Lückenaussage ist beim Fehlpfad breiter als der Bestand.** Die Zeile in §3 sagt: „kein Lauf belegt den Fehlpfad eines Backends (Backend fehlerhaft oder nicht erreichbar, Server bleibt verfügbar)“; der Folge-Slice §1 sagt, Stufe 4 „belegt keinen Backend-Fehlpfad“. Der Bestand führt Läufe, in denen ein Fehler aus dem Backend-Pfad den Client erreicht und die Verbindung nutzbar bleibt: Stufe 3 („Fehler der Zeilenquelle als `SqlException` mit nutzbarer Verbindung“), Stufe 4 („Fehler vor Zeilen“, „nach Zeilen (Verbindung nutzbar)“), Stufe 7 („sechs Runden Fehler nach drei Zeilen (50004, Class 16) und Folgeabfrage“). `ErrorCode.kt` bildet 50004 als `TDS_BACKEND_ERROR` mit `ConnectionFate.OPEN` ab. Ich habe nicht verfolgt, aus welcher Stelle der Testserver-Tabellen der Fehler in Stufe 7 stammt; sicher ist, dass er über die Simulation läuft und nicht über eine eigene `QueryBackend`-Implementierung. Richtig und belegbar ist die engere Aussage: kein Lauf bindet ein eigenes Backend an, dessen `execute` fehlschlägt oder das nicht erreichbar ist, und prüft, dass der Server für weitere Verbindungen verfügbar bleibt ([TDS-FA-08](../../spec/lastenheft.md#tds-fa-08--backend-anbindung) Negative). Dieselbe Klasse wie F-1 (Lückenaussage breiter als der Bestand), diesmal in umgekehrter Richtung (Lücke größer behauptet als sie ist). | `slice-e2e-geruest.md` §3 Zeile FA-08/RB-04; `slice-e2e-backend-ausserhalb-modul.md` §1 „Bestand und Abgrenzung“; `tools/e2e.sh` Stufen 3, 4, 7; `ErrorCode.kt` |
| V-3 | LOW | **Plan §3 und DoD 1 beschreiben nicht den Code.** Die Dateizeile sagt „Stufen 1 bis 4 und die Funktion `e2e_abdeckung`“; die Stufen-Tabelle führt drei Stufen und „4 entfällt“; DoD 1 sagt „die Stufen aus §3 (Stufe 4 entfällt)“. Der Code führt sieben Stufen; die Stufen 4 bis 7 (Referenzclient, `make kompatibilitaet`) stehen in keiner Plan-Tabelle, nur in DoD 2 und §7. Der Zusatz in der Stufe-4-Zeile („heute sieben Stufen“) macht es lesbar, die Tabelle ist aber nicht der Plan, den der Code erfüllt. Keine Verletzung der DoD, aber ein offener Plan-vs-Code-Unterschied. | `slice-e2e-geruest.md` §3 (Dateitabelle, Stufentabelle), §2 DoD 1 |
| V-4 | INFO | Der Review-Report prüft den Stand `08ff794`; die Einarbeitung in `c012ede` ist von diesem Bericht erstmals gegen den Bestand gelesen. Der Report ist im selben Commit abgelegt, in dem F-1 bis F-6 behoben werden; ein zweiter Review-Lauf auf `c012ede` liegt nicht vor (Planungstext, kein Code, deshalb hier nicht verlangt). | `docs/reviews/2026-11-05-08ff794-slice-e2e-geruest.md` |
| V-5 | INFO | `make skript-selbsttest` meldet Exit 0, aber der Schritt `RUN bash tools/e2e-selbsttest.sh && …` kam aus dem Docker-Cache. Als Beleg für `c012ede` trägt der Docker-Lauf deshalb nur „Eingaben unverändert gegenüber einem früheren grünen Lauf“. Der direkte Lauf von `tools/e2e-selbsttest.sh` ist grün. | `tools/mk/skript-selbsttest.mk` |
| V-6 | INFO | `AGENTS.md` §3.7 und §3.8 im Diff: die neuen Texte beschreiben Zustand und Beleg („nicht nachprüfbar“, „nicht gemessen“), keine Chronik und keine verworfenen Alternativen; `Verantwortlich:` trägt keinen vergänglichen Zusatz mehr. Der Abschnitt „Altstand“ in §7 ist eine Messchronik mit Standangabe und gehört dorthin (Closure-Notiz). Kein Befund. | `slice-e2e-geruest.md` §7 |

## Plan gegen Code (Diff)

| Plan (§3) | Code | Urteil |
|---|---|---|
| `tools/e2e.sh` neu, Stufen 1 bis 4 | existiert; sieben Stufen, Stufe 4 des Plans entfällt (Gate), Stufen 4 bis 7 sind `make kompatibilitaet`-Kategorien | Abweichung in der Stufenzahl dokumentiert (DoD 2, §7), Plan-Tabelle nicht nachgezogen (V-3) |
| `repo.mk` update, drei Variablen | vorhanden, einfaches `=`, `E2E_ABDECKUNG_ZIEL` unverändert | konform |
| `tools/mk/e2e.mk` neu, Zeile in `harness/README.md` | vorhanden, Zeile vorhanden | konform |
| `docs/plan/e2e-abdeckung.md` erzeugt | `make e2e-abdeckung`: unverändert | konform |
| nicht in `GATE_CHECKS` ([ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md)) | kein Eintrag; Workflow `e2e.yml` getrennt von `gates.yml` | konform |
| Erzeuger `tools/harness/e2e-abdeckung.sh` und `harness/mk/e2e-abdeckung.mk` nicht geändert | im Diff 3cfdd01..HEAD unberührt (nur vier Markdown-Dateien) | konform |

## Was bestätigt und was nur behauptet ist

* Bestätigt (selbst gefahren oder am Code gelesen): sieben Stufen im Skript, Abbruch an roter Stufe, „Nicht belegt“ je Deklaration, Sicht unverändert, kein Gate, Workflow, `harness/README.md`-Zeile, Source-Set `testserver` ohne Assoziation, Rechnung der beiden Messungen.
* Nur behauptet, nicht reproduziert: die Gesamtzeiten 317 s und 360 s, die Last 20 und 15, der Grün-Lauf `make e2e` auf `c012ede`, `make gates`. Dazu liegt im Repo kein Beleg (die Messungen stehen als Text in §7).
* Offen, kein Befund (Auftrag): Closure-Notiz, Register, Risiko-Ausgänge 1, 2, 4 in §6, DoD-Haken.
