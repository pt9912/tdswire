# Verifikationsbericht: slice-cancel-zusatztext-ursache-untersuchen — 2026-11-22

**Rolle:** Verifier (Modul 11), frischer Kontext; DoD- und Entscheidungs-Konformität, Plan-vs-Code-Diff. Kein Review, kein Fix, keine DoD-Haken, kein Schließen, nichts nach außen.

**Stand:** Branch `slice-cancel-zusatztext-ursache-untersuchen`, Spitze `61931878` (Nachbesserung `46de3417` ohne zweiten Review, siehe unten); Arbeitsbaum vor und nach allen Läufen sauber. Alle Läufe gelten für `61931878`.

**Gegenstand:** Slice [slice-cancel-zusatztext-ursache-untersuchen](../plan/planning/done/slice-cancel-zusatztext-ursache-untersuchen.md); [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-FA-07](../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe); [ADR-0054](../plan/adr/0054-antwort-auf-attention-in-zwei-nachrichten.md), [ADR-0057](../plan/adr/0057-abbruch-beim-beenden-endet-im-paar.md), [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md); Messprotokoll [referenzabgleich.md](../messungen/referenzabgleich.md) (A-7, Abschnitt „Zusatz der Antwort auf `Cancel()`“), Rohzeilen [cancel-zusatz-2026-11-22-rohzeilen.txt](../messungen/cancel-zusatz-2026-11-22-rohzeilen.txt); Reviews [Review](2026-11-22-slice-cancel-zusatztext-ursache-untersuchen.md) (0 HIGH, 5 MEDIUM, 3 LOW, 5 INFO) und [Re-Review](2026-11-22-slice-cancel-zusatztext-ursache-untersuchen-re-review.md) (0/1/1/5, R-1 in `46de3417` als Test nachgebessert, ohne zweiten Review). Nutzerentscheidung 2026-11-22 zur Live-Regel A-7: „Regel bleibt“.

## Urteil

**DoD erfüllt, soweit sie Sache des Verifiers ist; der Slice ist aus Sicht der Verifikation schließbar.** Alle gefahrenen Sensoren sind Exit 0 (`make clean-build`, `make e2e`, `make kompatibilitaet`, `make gates FORCE=1`, `make docs-check`, `make vergleichslauf-d LAUF=mitschnitt`), `src/` und `testserver/` sind gegen `main` unverändert, kein Gate ist gelockert, die Zahlen des Messprotokolls tragen bei der Stichprobe, das Ursachenurteil ist an seinen drei Bedingungen festgemacht und breiter nicht. `make vergleichslauf-d LAUF=live` ist in beiden Läufen **rot, und zwar genau aus der Ursache der Entscheidung „Regel bleibt“** (die Referenz 2025 zeigt den Zusatz, die Zeile `cancel-mit-folgeabfrage` meldet „die Ausnahme dazu entfaellt“; alle anderen 24 Schritte `ok`): kein Befund des Slice. Offen bleiben nur Closure-Arbeiten des Planners (Notiz §7, Haken, Register, Ausgänge der Risiken in §6) und zwei Hinweise (F-1 LOW, F-2 bis F-4 INFO).

## Selbst gefahrene Sensoren (End-Stand `61931878`, Docker, unter der Messsperre, keine künstliche Last)

Messsperre `/tmp/claude-1000/-Development-tdswire/messlock` von mir angelegt (Kennung im `owner`, frei vorgefunden), nach dem letzten Lauf von mir freigegeben; `available` vor den Läufen 8,7 bis 15,2 GiB, nie unter 4000 MB; Lastmittel zu Beginn 22, später 4,7 bis 7 (fremde Prozesse, nicht meine).

| Sensor | Ergebnis | Dauer |
|---|---|---|
| `make clean-build` | **Exit 0** (Jar, Tests, detekt, `--no-cache`); 1558 Zeilen `PASSED`, keine `FAILED`-Zeile eines Tests (die einzigen `SKIPPED` sind `checkKotlinGradlePluginConfigurationErrors`), 8 mal `BUILD SUCCESSFUL`, 0 mal `BUILD FAILED`. | 696 s |
| `make e2e` | **Exit 0**, „gruen (9 Stufen, 194 s)“; Stufen 1 bis 9: 1, 17, 22, 17, 18, 17, 37, 18, 47 s, alle grün | 194 s |
| `make kompatibilitaet` (Standard `KATEGORIE=Szenario`) | **Exit 0**, „gruen“ | 16 s |
| `make gates FORCE=1` | **Exit 0**; `baseline-verify`, `detekt-selbsttest`, `kopplung-zahlen`, `skript-selbsttest`, `detekt`, `build-jar`, `referenztest-dotnet` (Stage `test`, **aus dem Layer-Cache**, siehe unten), `test`, vier Selbsttests der Stempel (67 Fälle im Gruppenschritt), `docs-check` (1757 Dateien, 0 Befunde), `flaeche`, `gate-zuordnung` (16 Gates, Selbsttest 33 Fälle), `stempel-eingaenge` grün. Ein erster `make gates` ohne `FORCE=1` übersprang die drei Stempelklassen („Beleg für …“); ich habe ihn abgebrochen und mit `FORCE=1` wiederholt | 34 s |
| `make docs-check` (einzeln) | **Exit 0**, `1757 Datei(en) geprüft, 0 Befund(e)` | 3 s |
| `make vergleichslauf-d LAUF=mitschnitt` | **Exit 0**, grün, beide Teilläufe (2022, 2025) | 38 s |
| `make vergleichslauf-d LAUF=live` (Lauf 1) | **Exit 2, rot**: Teillauf 2022 grün, Teillauf 2025 rot, einzige Abweichung `cancel-mit-folgeabfrage` („die Ausnahme dazu entfaellt“), 25 Schritte, 24 davon `ok` | 95 s |
| `make vergleichslauf-d LAUF=live` (Lauf 2, die eine Wiederholung) | **Exit 2, rot**: dasselbe Bild, Teillauf 2022 grün, 2025 rot aus derselben Ursache | 84 s |
| `RecordRelaisTests` im Wegwerf-Kontext (Scratchpad, Kopie von `referenztest-dotnet/`, Stage `test` mit Filter `FullyQualifiedName~RecordRelaisTests`, `--no-cache-filter test`, `--network=none`) | **dreimal in Folge 12 von 12 grün** (Exit 0, je rund 8 s); kein Flackern | 3 × 8 s |
| Mutation M20 (`var antwort = _wirkt && …` ohne `_attention`, in der Kopie) | **rot, genau ein Fall**: `Eingriff_Verzoegern_laesst_den_Record_der_Antwortlaenge_vor_dem_ATTENTION_des_Clients_unverzoegert` (Lesezeit 5 s: „Unable to read data from the transport connection: Connection timed out“), 11 von 12 grün; die Mutation ist nur in der Wegwerf-Kopie, nicht im Repo | 11 s |

Nicht gefahren: `LAUF=cancel` (Auftrag: das Messwerkzeug ist reviewt, die Messung muss nicht wiederholt werden), `make spec-abdeckung` (Sache des Implementers, siehe F-3), `make flaeche` und `make kopplung-zahlen` einzeln (beide in `make gates FORCE=1`), `make matrix-zitate` (die Zelle der Matrix ist ein Zitat der Messung, kein Zitat `Klasse > Testfall`). Alle Läufe im Vordergrund und nacheinander (zur Überwachung durch das Werkzeug in den Hintergrund verlagert, nie zwei Läufe zugleich).

**Zu „Stage `test` aus dem Cache“:** in `make gates FORCE=1` zeigt `[test 1/1] RUN --network=none sh test-lauf.sh test` den Status `CACHED` (der Inhalt der Stage ist der des Implementer-Laufs mit derselben Eingabe). Deshalb der eigene Frischlauf von `RecordRelaisTests` im Wegwerf-Kontext (oben, dreimal). Die Stage `test` des `referenztest-dotnet/Dockerfile` kopiert nur `Smoke` und `Mitschnitt`: **`CancelZusatzMessungTests` (Ordner `Vergleich`) läuft weder in `make clean-build` noch in `make gates`**, sondern nur im Client-Image der Stufe `vergleich` (Bau in `LAUF=live`, `LAUF=mitschnitt`; der Test selbst nur in `LAUF=cancel`). Dass er **kompiliert**, belegen die beiden grünen beziehungsweise (aus anderer Ursache) roten, aber gebauten Läufe von `LAUF=live` und der grüne `LAUF=mitschnitt`; dass er **läuft**, belegt allein die Messung vom 2026-11-22 (nicht wiederholt).

Gesamt rund 32 Minuten Wanduhr (Lesen ab 09:46; Messsperre 09:51 bis 10:15; Bericht danach), davon rund 24 Minuten aktive Docker-Läufe. Größte Posten: `make clean-build` 696 s, `make e2e` 194 s, `LAUF=live` 95 s und 84 s, Mutations- und Wiederholungsläufe zusammen rund 40 s.

## (1) Messaussagen, nachgerechnet aus den Rohzeilen (`grep`/`awk`)

Die Datei hat 1277 Zeilen: 4 Kommentarzeilen, 920 Zeilen `durchlauf`, die übrigen `summe` und `muster`. Es sind 920 Durchläufe (Plan §3, Kopf der Datei), nicht 920 Zeilen. Teil A steht in den Zeilen 1 bis 1036 (800 Durchläufe, 4 Ziele × 5 Modi × 40), Teil B ab Zeile 1037 (120 Durchläufe, 4 Ziele × 30 Prozesse).

| Aussage des Protokolls | Nachgerechnet | Stand |
|---|---|---|
| Referenz, Teil A: `direkt` 28 und 29, `relais` 25 und 26, `relais-einzeln` 23 und 21 von 40 (2022, 2025) | genau so | trägt |
| Spanne „21 bis 29 von 40“ (Teil A) | Minimum 21 (`relais-einzeln`, 2025), Maximum 29 (`direkt`, 2025) | trägt |
| Teil B, Referenz: 2022 8 von 30, 2025 9 von 30; Server 30 von 30 und 30 von 30 | genau so | trägt |
| „27 bis 73 %“ | 8/30 = 26,7 %, 29/40 = 72,5 % | trägt (gerundet) |
| `relais-buendeln`: 0 von 40 je Ziel, „0 von 160“ | 0/40 an Referenz 2022, 2025 und Server 2022, 2025; 4 × 40 = 160 | trägt |
| `relais-verzoegert`: Referenz 40 von 40 je Instanz, „80 von 80“ | 40/40 und 40/40 an der Referenz (der Server auch 40/40) | trägt; Grundmenge verschieden, im Absatz „Ergebnis“ benannt (Re-Review R-5) |
| Server „in jedem der 380 Durchläufe ohne den Eingriff Bündeln (320 in Teil A, 60 in Teil B)“ | Server Teil A: 2 Ziele × 4 Modi × 40 = 320 mit Zusatz, Bündeln 0; Teil B 60 von 60; gesamt 380 | trägt; **die Matrix nennt „380 von 380 Durchläufen“ ohne den Zusatz „ohne den Eingriff Bündeln“** (F-4) |
| 640 Durchläufe mit Relais, in allen drei Datenrecords nach dem ATTENTION | `server-records=3` in 640 Zeilen, `relais` in 640 Zeilen von `durchlauf` | trägt |
| Abstand am Relais, Server: „38 bis 44 ms in allen 320 Durchläufen mit Relais“ | Minimum 38056 µs (2025, `relais`), Maximum 44185 µs (2025, `relais-einzeln`) über alle acht Zellen mit Relais, auch in `relais-buendeln` (am Relais gemessene Lesezeit, die Weitergabe war gebündelt) | trägt |
| Abstand am Relais, Referenz (`relais`): 0/57/237 µs (2022), 0/59/523 µs (2025) | Zeilen `summe` genau so | trägt |

Weitere Einzelzahlen (Mediane je Zelle) hat der Re-Review aus denselben Zeilen nachgerechnet; ich habe sie nicht wiederholt.

## (2) Messwerkzeug: Tests und Mutation

`RecordRelaisTests`: 12 Fälle, dreimal in Folge 12/12 grün (Frischlauf, nicht aus dem Cache); alle Fälle tragen `// @spec SPEC-045`. Der neue Fall zu R-1 (`46de3417`, 14 Zeilen Testcode, `RecordRelais.cs` unverändert) hat keine Zeitschwelle: die Verzögerung (8 s) liegt über der Lesezeit des Clients (5 s), der Fall besteht oder wirft; er flackert nicht. **M20 ist rot, in genau diesem Fall**, die übrigen elf bleiben grün: R-1 des Re-Reviews ist behoben und belegt. Die Nachbesserung ändert nur Testcode; ihr erneuter Review fehlt (R-7 des Re-Reviews: Vermerk „Nachbesserung ohne zweiten Review“ in §7 mit Commits, Grund und Prüfung ist Sache des Planners; mein Lauf ist die Prüfung dazu).

## (3) Ursachenurteil gegen AGENTS.md §3.8

Das Urteil im Protokoll ist an drei Bedingungen festgemacht und sagt es im Satz: Quelle ist der GitHub-Tag `v7.1.1`, nicht das NuGet-Paket; die Zahl der TLS-Records je `Read` ist nicht gemessen; der Ablauf gilt unter der Prämisse `ReturnImmediately` im Lesepfad (im Review am 2026-11-22 bestätigt; die Fundstellen stehen im Protokoll nicht, siehe F-2). Die Eingriffe am Relais (Bündeln 0 von 160, Verzögern 80 von 80 an der Referenz) tragen „das `DONE[ATTN]` im Puffer oder nicht“ als Bedingung, ohne die Zwischenstufe der Records je `Read`. Als Vermutung gekennzeichnet und nicht belegt sind die 40 ms am Server (Nagle und verzögerte Bestätigung, ohne `TCP_NODELAY`-Lauf) und der Grund der Rate an der Referenz (Überschrift (c) „Vermutung, nicht belegt“). Der Unterschied zu den Live-Läufen (1 von 21) steht als „nicht erklärt“ da, und „selten“ ist ausdrücklich nicht auf die Messmethode übertragen. Die Grenzen (ein Host, wechselnde Last, ein Client in einer Version, Länge 50 als `DONE[ATTN]` aus Länge und Reihenfolge, nicht aus entschlüsselten Bytes) stehen im Absatz „Grenzen“. Keine Aussage ist breiter als ihr Beleg; **ein Befund dazu: keiner**.

## Entscheidungs-Konformität

| Entscheidung | Befund |
|---|---|
| [ADR-0054](../plan/adr/0054-antwort-auf-attention-in-zwei-nachrichten.md), [ADR-0057](../plan/adr/0057-abbruch-beim-beenden-endet-im-paar.md) (Form der Antwort) | **Erfüllt, unberührt.** `git diff main...HEAD --name-only` enthält weder `docs/plan/adr/` noch `spec/`; der Server ist unverändert. Die Option „Server angleichen“ trennt im Protokoll sauber den Zeitpunkt der ersten Nachricht (dem Bau überlassen) von der zweiten („unmittelbar danach unter derselben Sperre“, Setzung) und nennt den Abstand von rund 40 ms als gemessen, seine Ursache als nicht festgestellt |
| AGENTS.md §3.6 (Sensor nicht gelockert) | **Erfüllt.** `live-ausnahmen.tsv`: Spalten 1 bis 4 aller Zeilen gegen `main` byte-gleich (`md5sum` der Spalten 1 bis 4 verglichen), nur Spalte 5 (Grund) der Zeile `cancel-mit-folgeabfrage` verändert, sechs Spalten wie zuvor; `tools/vergleichslauf-d.sh` ändert nur `cancel-*` (neuer Lauf, kein Eingriff in `vergleichen`, Ausnahmeregeln, Selbsttest); `Makefile`, `Dockerfile`, `config/`, `GATE_CHECKS`, detekt unberührt. Der Live-Lauf bleibt scharf und rot, wenn die Referenz den Zusatz zeigt (meine zwei Läufe, siehe unten) |
| Nutzerentscheidung 2026-11-22 „Regel bleibt“ | **Umgesetzt wie entschieden.** Slice §1 und §6, Protokoll (Abschnitt Optionen, Zeile „Regel bleibt“, Zeile A-7), Grund in der Liste und Matrix nennen sie; die Optionen „Regel präziser fassen“, „Server angleichen“, „nichts“ stehen als Dokumentation mit Wirkung und Grenze |
| AGENTS.md §3.1, §3.2, §3.5 | kein Host-Aufruf von `gradle` oder `java`; im Diff kein `@Suppress` (Treffer nur im Text von Reviews); keine `Accepted`-ADR geändert |

## `src/` und `testserver/` unverändert (belegt)

`git diff main...HEAD --name-only -- src testserver` liefert 0 Zeilen, ebenso `git diff main HEAD --name-only | grep -E '^(src|testserver)/'` (0 Treffer); die Dateien des Diffs sind genau: `docs/messungen/` (2), `docs/plan/` (3: Matrix, Slice, Spec-Abdeckung), `harness/README.md`, `referenztest-dotnet/` (4: `RecordRelais.cs`, `RecordRelaisTests.cs`, `CancelZusatzMessungTests.cs`, `live-ausnahmen.tsv`), `tools/` (2) und zwei Reports. Kein Produktionscode des Servers.

## DoD Punkt für Punkt (§2, §5)

Keine Haken gesetzt; `[x]` und Closure sind Sache des Planners.

| DoD-Punkt (§2) | Befund |
|---|---|
| **Messung der Bedingung** (Läufe je Instanz mit/ohne Zusatz, Datum, Host, Last, Rohausgabe abgelegt, was am Draht unterscheidet) | **Erfüllt.** Zahlen je Instanz und Modus (1), Datum 2026-11-22, Host (20 CPUs, 31 GiB), Lastmittel zu Beginn je Teil, Rohzeilen im Repo (920 Durchläufe), am Draht: Aufteilung in drei Records gleich, Unterschied ist der Abstand der Lesezeiten (Server rund 40 ms, Referenz µs) und der Eingriff Bündeln/Verzögern; Grenze: der Abstand allein trennt die Fälle an der Referenz nicht |
| **Ursache** (benannt und belegt oder „nicht festgestellt“ mit Grenzen; Optionen mit Wirkung; Wahl ist die des Nutzers) | **Erfüllt**, mit den Grenzen aus (3); Optionstabelle mit vier Optionen, Entscheidung „Regel bleibt“ benannt |
| `make gates` Exit 0, `docs-check`, `flaeche`, `kopplung-zahlen`; `make spec-abdeckung` bei Änderung von Tests | **Erfüllt**: `make gates FORCE=1` Exit 0, `make docs-check` Exit 0; `flaeche` und `kopplung-zahlen` in `make gates` grün. `make spec-abdeckung`: vom Implementer, nicht von mir gefahren (F-3). `make vergleichslauf-d` ist Nachweis, kein Gate ([ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md)) |
| Review durchgeführt, Report unter `docs/reviews/` | **Erfüllt:** Review und Re-Review liegen vor (F-1 bis F-8 des Reviews laut Re-Review behoben; R-1 durch `46de3417` und M20 rot von mir nachgemessen; R-2 bis R-4 laut Commit `61931878` nachgezogen, nicht erneut reviewt, siehe oben) |
| Doku-Update: Messprotokoll, `live-ausnahmen.tsv` (Grund), Matrix 7d | **Erfüllt** und gegen den Diff gelesen; Matrix Zeile `Cancel()` nach 50 Zeilen trägt Rate und Zeiger; Abschnitt 5 Nr. 12 unverändert (nicht im Diff) |
| Closure-Notiz mit Steering-Loop-Lerneintrag | **Offen:** §7 „Wird bei der Closure gefüllt.“ (erwartet) |
| Beobachtungs-Register fortgeschrieben (`BEO-TDS/cancel-antwort-text-weicht-vom-echten-server-ab`, Ausgang nachführen) | **Offen:** kein Pfad unter `observations/` im Diff; `state.md` führt noch „er ist eine Datei in `open/`, nicht gestartet“ und den Zähler 3× (erwartet, Closure Schritt 25 laut Slice §6) |
| Jedes Risiko aus §6 trägt einen Ausgang | **Erfüllt als Vorschlag:** alle fünf Risiken tragen einen Ausgang (entfallen, entfallen/weiter offen, eingetreten und entschieden, weiter offen, entfallen); „setzen und bestätigen tut ihn der Planner“ (Wortlaut §6). Nach meinen Läufen: das dritte Risiko („`LAUF=live` gelegentlich rot ohne Befund des Servers“) ist **eingetreten** und bestätigt (zwei von zwei Läufen mit rotem Teillauf 2025, siehe F-1) |
| Die drei Paarungen (Anker · Folge-Slice · Register) | **Offen bis zur Closure:** keine `liegt in`-Zeile (§7 leer), Register nicht fortgeschrieben; der Folge-Slice für „Server angleichen“ ist nicht angelegt (die Entscheidung des Nutzers vertagt den Bau; Träger ist das Register, Sache des Planners) |

Der Slice hat zwei Liefer-Punkte (Messung, Ursache); die Rückführung „mehr als drei“ ist nicht ausgelöst.

## Plan-vs-Code-Diff (§3, `git diff main...HEAD`, 14 Dateien, 2535 Einfügungen, 24 Löschungen)

Dateiweiser Abgleich der Plan-Tabelle §3 gegen `git diff main...HEAD --name-only` (ohne die beiden Reports): jede Datei der Tabelle steht im Diff (`referenzabgleich.md`, Rohzeilen, `CancelZusatzMessungTests.cs`, `vergleichslauf-d.sh`, `vergleichslauf-d.mk`, `RecordRelais.cs`, `RecordRelaisTests.cs`, `spec-abdeckung.md`, `harness/README.md`, Matrix, `live-ausnahmen.tsv`), und jede Datei des Diffs steht in der Tabelle, außer dem Slice selbst. Berührte Spec-Stellen: `spec/` ist nicht im Diff, wie der Kopf des Slice es zusagt („nur, wenn die Regel ihre Art ändert“; sie ändert sie nicht). `SPEC-045`: `RecordRelaisTests` 12 von 12 getaggt, `docs/plan/spec-abdeckung.md` führt SPEC-045 mit 134 Tests (122 auf `main` plus 12), stimmig mit zwölf neuen Fällen (F-3: nicht von mir erzeugt). `CancelZusatzMessungTests.cs` trägt `SPEC-045` im KDoc, aber kein `// @spec` am Fall (F-1). Die Zeile von `make vergleichslauf-d` in `harness/README.md` und die Hilfezeile in `tools/mk/vergleichslauf-d.mk` nennen `cancel`; die Stage `vergleich` baut den Test mit (belegt durch den Bau in `LAUF=live` und `LAUF=mitschnitt`).

## Findings

| ID | Schwere | Befund | Quelle | Pfad |
|---|---|---|---|---|
| F-1 | LOW | `CancelZusatzMessungTests.cs` trägt kein `// @spec SPEC-045` am einzigen `[Fact]` (Zeile 180), nur den Verweis im KDoc (Zeile 13); der Kopf des Slice sagt „Die neuen Tests tragen `@spec SPEC-045`“. Folge für Gates: keine (der Test liegt im Ordner `Vergleich`, den die Stage `test` nicht kopiert; er trägt `[Trait("Kategorie", "MessungCancelZusatz")]` und urteilt nicht), `RecordRelaisTests` sind vollständig getaggt. Entweder Tag nachtragen oder den Satz des Slice auf `RecordRelaisTests` einschränken. | Slice Kopf „Berührte Spec-Stellen“; Kopf von `tools/spec-abdeckung.sh` | `referenztest-dotnet/Vergleich/CancelZusatzMessungTests.cs` Zeile 180 |
| F-2 | INFO | Die Prämisse `ReturnImmediately` trägt im Protokoll den Satz „im Review am 2026-11-22 in `SqlDataReader.cs` desselben Tags bestätigt; die Fundstellen stehen hier nicht“. Das ist ehrlich, aber für den Retirement-Check dünn: ohne Zeilenangabe (wie bei `TdsParser.cs`) kann ein späterer Leser die Prämisse nicht an der Quelle nachprüfen. Keine Aktion nötig, solange niemand die Ablaufbeschreibung ohne die Prämisse zitiert. | AGENTS.md §3.8 | `docs/messungen/referenzabgleich.md`, Absatz „Quelle des Clients (Fundstelle)“ |
| F-3 | INFO | `make spec-abdeckung` habe ich nicht gefahren (Auftrag, Sache des Implementers); die Zahl 134 in `docs/plan/spec-abdeckung.md` ist rechnerisch stimmig (122 + 12 Fälle), aber nicht frisch bestätigt. Bei einer Konfliktauflösung der Datei gilt die Regel der Tabelle in `harness/README.md` (Fassung von `main`). | `harness/README.md` §Minimal agent workflow | `docs/plan/spec-abdeckung.md` |
| F-4 | INFO | Die Matrix-Zelle (Abschnitt 7d, Zeile `Cancel()` nach 50 Zeilen) nennt „380 von 380 Durchläufen“ am Server ohne den im Protokoll genannten Zusatz „ohne den Eingriff Bündeln“ (am Server 0 von 80 mit Bündeln). Nachgerechnet trägt die Zahl nur mit diesem Zusatz (320 + 60); die Zelle trägt den Zeiger auf das Protokoll, der Satz steht „ohne Eingriff in den Zeitpunkt“ nur bei der Referenz. Keine Aktion nötig; wer die Zelle allein liest, könnte „380 von 380“ als alle Durchläufe des Servers lesen (460). | AGENTS.md §3.8 | `docs/plan/kompatibilitaetsmatrix.md` Abschnitt 7d, Zeile `Cancel()` nach 50 gelesenen Zeilen |
| F-5 | INFO | Beobachtung zum Live-Lauf (kein Befund des Slice): in meinen beiden Läufen von `LAUF=live` zeigte die Referenz 2022 den Zusatz nicht, die Referenz 2025 **beide Male** (Rohzeilen: Lauf 1 Zeile 469, Lauf 2 Zeile 469 der Ausgabe; der Server in allen vier Teilläufen). Das rote Ergebnis hat genau die Ursache aus Slice §1 und §6 („die Ausnahme dazu entfaellt“, sonst 24 von 25 Schritten `ok`). Zusammen mit den früheren Läufen steht die Referenz mit Zusatz jetzt bei 3 von 25 Live-Teilläufen (1 von 21 plus 2 von 4); das liegt näher an den Raten der Messreihe (8 bis 29 von 30/40) als an „1 von 21“, ist aber vier Läufe auf einem Host und keine Rate. Der Wert „1 von 21“ in Matrix, Ausnahmeliste und Protokoll ist ein datierter Stand und bleibt wahr; der Zähler der Beobachtung kann bei der Closure um diese zwei Teilläufe wachsen. | Slice §1, §6 (drittes Risiko) | Rohausgabe: Scratchpad des Verifiers (`live1.log`, `live2.log`), nicht im Repo |

| Schwere | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 0 |
| LOW | 1 (F-1) |
| INFO | 4 (F-2, F-3, F-4, F-5) |

## Offene Fragen (knapp)

1. F-1: Tag `// @spec SPEC-045` am Messtest nachtragen oder den Satz im Kopf des Slice auf `RecordRelaisTests` beschränken? (Planner/Implementer; kein Gate hängt daran.)
2. Closure: Vermerk „Nachbesserung ohne zweiten Review“ (`46de3417`, `61931878`) in §7 mit Prüfung (dieser Bericht: 3 × 12 von 12, M20 rot) und Zähler der Finding-Klasse „Eigencode ohne Test, Mutation bleibt grün“ (zwei Läufe desselben Slice).
3. Der Bau am Server („Server angleichen“, Ursache der 40 ms) ist vertagt; Träger ist laut Slice §6 das Register. Ein Folge-Slice entsteht erst mit der Entscheidung des Nutzers.

## Empfehlung

**Closure: ja.** Die Zusagen des Slice sind an Rohzeilen, Quelle und zwei Referenzinstanzen belegt, kein Produktionscode wurde geändert, kein Sensor gelockert, und alle Sensoren außer dem nach Entscheidung „Regel bleibt“ bekanntermaßen zufallsabhängig roten `LAUF=live` sind Exit 0. Ein rotes `LAUF=live` wegen des Zusatzes an der Referenz ist kein Befund; belegt ist es oben mit den Zeilen `cancel-mit-folgeabfrage` beider Läufe.

Rohausgaben der Läufe liegen im Scratchpad des Verifiers (`clean-build.log`, `e2e.log`, `kompat.log`, `gates.log`, `mitschnitt.log`, `live1.log`, `live2.log`, `rr/run1.log` bis `rr/run3.log`, `rr/m20.log`), nicht im Repo.
