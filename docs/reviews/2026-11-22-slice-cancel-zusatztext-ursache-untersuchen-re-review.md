# Re-Review-Report: slice-cancel-zusatztext-ursache-untersuchen — 2026-11-22

**Review-Art:** Re-Review der Nachbesserung (Code und Doku) gegen Plan, ADR und Hard Rules; keine Prüfung gegen die DoD (das ist die Frage des Verifiers). Frischer Kontext; gelesen wurden nur die Commits nach dem Report-Stand, nicht der ganze Slice.

**Gegenstand:** Zweig `slice-cancel-zusatztext-ursache-untersuchen`, Commits nach `95f5169a` (Erstreview: [2026-11-22-slice-cancel-zusatztext-ursache-untersuchen.md](2026-11-22-slice-cancel-zusatztext-ursache-untersuchen.md), 0 HIGH, 5 MEDIUM, 3 LOW, 5 INFO):

- `1aee629e` (Code): `referenztest-dotnet/Mitschnitt/RecordRelais.cs` (neuer optionaler Konstruktorparameter `halteGrenzeMs`, Kommentar zur Grenze des Zerlegers), `RecordRelaisTests.cs` (vier Fälle, Hilfsklasse `Strecke`, Methode `Schreiben`).
- `e4e594ad` (Doku): `docs/messungen/referenzabgleich.md`, `docs/plan/kompatibilitaetsmatrix.md`, Slice, `docs/plan/spec-abdeckung.md`, `referenztest-dotnet/Vergleich/live-ausnahmen.tsv` (nur Grund).

`git diff 20fd2274 HEAD -- src testserver tools harness Makefile Dockerfile config` ist leer (Produktionscode, Skripte, Gates unverändert). Nutzerentscheidung 2026-11-22 zur Live-Regel: „Regel bleibt“.

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-22

**Eingangs-Kontext:** Erstreview-Report, Diff der beiden Commits, [ADR-0054](../plan/adr/0054-antwort-auf-attention-in-zwei-nachrichten.md) Entscheidung 2, `SPEC-001`, `SPEC-029`, `SPEC-045`, `TDS-FA-07`, `TDS-QA-01`, `AGENTS.md` §3.6 bis §3.8, `harness/README.md` §Minimal agent workflow, `docs/messungen/cancel-zusatz-2026-11-22-rohzeilen.txt`.

**Ausgeführte Sensoren** (`free -m` available zu Beginn 15,8 GB, am Ende 6,5 GB; keine Messsperre angelegt, keine Referenzinstanz gestartet, keine künstliche Last; Lastmittel des Hosts 10 bis 15):

1. `RecordRelaisTests` in einem Wegwerf-Kontext im Scratchpad (Kopie von `referenztest-dotnet/`, Stage `test` mit Filter `RecordRelaisTests`, Restore aus dem Layer-Cache, `--network=none`): Ausgangslauf 11 von 11 grün, **9 Läufe in Folge** (3 + 6) und ein weiterer Lauf von 8 Wiederholungen mit Zeitausgabe (instrumentierte Kopie, nicht im Repo): kein Flackern.
2. Mutationen von `RecordRelais.cs`, je ein Lauf der Klasse (Ergebnis unten): M4, M5, M6, M8, M9 des Erstreviews und eigene M11, M12, M13, M20, M21, M22, M24, M5+M12.
3. Zahlen der Rohzeilen nachgerechnet (`grep`, `awk`): Teil A und B je Ziel und Modus, Spanne, Mediane des Abstands je Zelle (Minimum, Median, Maximum), 640 Durchläufe mit Relais.
4. `bash tools/vergleichslauf-d-selbsttest.sh`: grün. Zusätzlich die echte Liste `live-ausnahmen.tsv` mit `bash tools/vergleichslauf-d.sh --vergleich` gegen Paare aus ihren eigenen Spalten 2 und 3 gefahren (der Selbsttest liest die echte Liste nicht): Zeile `cancel-mit-folgeabfrage` wird gelesen („ok Ausnahme (Luecke)“), bei gleichen Ergebnissen „die Ausnahme dazu entfaellt“ (rot), Spaltenzahl 6.
5. `make docs-check` (1756 Dateien, 0 Befunde), `make flaeche`, `make kopplung-zahlen` (je Exit 0).
6. Grep nach der alten Lesart („27 bis 73“, „je nach Aufbau“, „1 von 21“, „selten“, „nicht untersucht“) über `spec/`, `docs/`, `harness/`, `tools/`, `referenztest-dotnet/`; Abgleich der Plan-Tabelle §3 gegen `git diff --name-only main...HEAD`.

Nicht gefahren (Auftrag und Tabelle `harness/README.md` §Minimal agent workflow): `make gates`, `make clean-build`, `make e2e`, `make kompatibilitaet`, `make vergleichslauf-d`, `make spec-abdeckung`.

Laufzeit gesamt: rund 11 Minuten (größte Posten: neun Mutationsläufe je 10 bis 15 s im Docker-Build; Wiederholungsläufe 16 bis 40 s; Lesen der Diffs und Nachrechnen der Rohzeilen den größten Teil).

## Stand der Findings des Erstreviews

| Finding | Stand | Beleg dieses Laufs |
|---|---|---|
| F-1 (Bedingung im Satz) | behoben im Protokoll (A-7 und Absatz „Ursache, Belegstärke“); in `live-ausnahmen.tsv` nur teilweise (siehe R-5, INFO) | A-7: „für `Microsoft.Data.SqlClient` 7.1.1, belegt durch Eingriffe am Relais (…) und durch die Quelle des GitHub-Tags `v7.1.1` (nicht das NuGet-Paket; unter der Prämisse `ReturnImmediately` im Lesepfad; wie viele TLS-Records ein `Read` liefert, ist nicht gemessen)“ in **einem** Satz. Absatz (a): „am Relais gemessen … und in der Quelle gelesen; ‚belegt‘ gilt nur mit den drei Bedingungen“ mit allen drei. Überschrift und Text von (c) tragen „Vermutung, nicht belegt“. Matrix nennt die Rate mit „ohne Eingriff in den Zeitpunkt“ und den Zeiger auf das Protokoll. |
| F-2 (Rate an mehreren Orten) | behoben | Matrix, A-7, Ausnahmeliste und Optionen nennen dieselben Zahlen: Teil A 21 bis 29 von 40, Teil B 8 bis 9 von 30, Live 1 von 21; das Protokoll führt die Spanne „27 bis 73 %“ einmal und nennt die Fundstellen („gilt an allen Fundstellen dieser Messung (Matrix, A-7, Ausnahmeliste)“); Matrix und Liste zeigen auf das Protokoll. Nachgerechnet aus den Rohzeilen: `direkt` 28 und 29, `relais` 25 und 26, `relais-einzeln` 23 und 21 von 40; Teil B 8 und 9 von 30; 8/30 = 26,7 %, 29/40 = 72,5 %; `relais-buendeln` 0 von 160, `relais-verzoegert` Referenz 80 von 80. Alte Spannen per Grep nicht mehr vorhanden. |
| F-3 (ADR-Wortlaut) | behoben | Option „Server angleichen“ trennt jetzt: [ADR-0054](../plan/adr/0054-antwort-auf-attention-in-zwei-nachrichten.md) Entscheidung 2 überlässt dem Bau den Zeitpunkt der **ersten** Nachricht; für die zweite gilt „folgt unmittelbar danach unter derselben Sperre der Sitzung“ (Setzung; gegen die ADR Zeile 51, `SPEC-001` und `SPEC-029` gelesen: der Wortlaut stimmt); der Abstand von rund 40 ms ist gemessen, seine Ursache nicht festgestellt, ob er „unmittelbar“ genügt, steht als offen. |
| F-4 (Eigencode ohne Test) | für M4, M5, M6, M8, M9 behoben; ein verwandter Fall bleibt grün (R-1) | Alle fünf Mutationen nachgefahren, je rot im erwarteten Fall (Tabelle unten). |
| F-5 (Plan-Tabelle) | behoben | Plan-Tabelle §3 nennt jetzt `RecordRelais.cs`, `RecordRelaisTests.cs`, `tools/mk/vergleichslauf-d.mk`, `harness/README.md`, `docs/plan/spec-abdeckung.md`, die Rohzeilen-Datei; Matrix steht in Abschnitt 7d; jede Datei von `git diff --name-only main...HEAD` (außer den Review-Reports) steht in der Tabelle. Kopf nennt `SPEC-045` für die neuen Tests. |
| F-6 (Modusangabe der Mediane) | behoben | Median je Zelle aus den Rohzeilen nachgerechnet: `relais` 2022 ohne 41 µs (15), mit 72 µs (25); 2025 ohne 43 µs (14), mit 65,5 µs (26); `relais-einzeln` 2022 ohne 46 µs (17), mit 58 µs (23); 2025 ohne 47 µs (19), mit 70 µs (21); Minimum und Maximum wie im Protokoll (z. B. `relais` 2025: 0 bis 172 ohne, 33 bis 523 mit); die Überlappung in allen vier Zellen stimmt. |
| F-7 (Grenze einer Option) | behoben | Wortlaut stimmt mit Kopf und Verhalten von `vergleichen` überein; „die Ausnahme dazu entfaellt“ (rot bei gleichen Ergebnissen) selbst gefahren. |
| F-8 (Überschrift) | behoben | Überschrift von (c) lautet „nur in einem Teil der Durchläufe zeigt (Vermutung, nicht belegt)“; der Text sagt, „selten“ sei auf diese Messmethode nicht übertragbar. |

## Mutationen am Messwerkzeug (selbst gefahren)

Engste Testklasse `RecordRelaisTests`, Ausgangslage 11 von 11 grün, je Mutation ein Lauf (nur `RecordRelais.cs` geändert):

| Nr. | Mutation | Ergebnis |
|---|---|---|
| M4 | Zeitgeber der Haltegrenze nicht angelegt | rot: `Eingriff_Buendeln_gibt_gehaltene_Records_nach_der_Haltegrenze_frei_auch_ohne_Record_der_Antwortlaenge` (Lesezeit 5 s) |
| M5 | `Buendeln when _wirkt` statt `when _attention && _wirkt` | rot: `Eingriff_Buendeln_laesst_Bytes_vor_dem_ATTENTION_des_Clients_unberuehrt` (Lesezeit 5 s) |
| M6 | `_wirkt = false` im Zweig `Verzoegern` gestrichen | rot: `Eingriff_Verzoegern_verzoegert_nur_den_ersten_Record_der_Antwortlaenge`, Meldung „zweiter Record der Antwortlänge nach 1500.1895 ms“ (das Protokoll nennt „ohne die Einmaligkeit 1500 ms“: stimmt) |
| M8 | Tor `_scharf` im Rückruf für den Client aufgehoben (`(_scharf \|\| record != null) && …`) | rot: `Eingriff_Buendeln_laesst_Bytes_vor_dem_ATTENTION_des_Clients_unberuehrt` |
| M9 | Weitergabe des Rests (`BeiRecord?.Invoke(… , null)`) gestrichen | rot: `Eingriff_Einzeln_gibt_den_Rest_nach_einem_unbekannten_Byte_weiter` |
| M11 (eigene) | `_wirkt = false` in `FreigebenUnterSperre` gestrichen | rot: `Eingriff_Buendeln_haelt_den_ersten_Record_bis_der_der_Antwortlaenge_da_ist` |
| M13 (eigene) | `Halten(roh, false)` statt `Halten(roh, antwort)` | rot: `Eingriff_Buendeln_laesst_Bytes_vor_dem_ATTENTION…` |
| M21 (eigene) | `if (freigeben) FreigebenUnterSperre();` gestrichen | rot: `Eingriff_Buendeln_laesst_Bytes_vor_dem_ATTENTION…` |
| M20 (eigene) | `antwort` ohne `_attention` (`var antwort = _wirkt && …`) | **grün** (R-1) |
| M22 (eigene) | Prüfung `Typ: 0x17` im Rückruf für den Client durch `record != null` ersetzt | grün (im Protokoll als „nicht mutiert und nicht gesehen“ genannt; siehe R-4) |
| M12 (eigene) | Konstruktor ignoriert `halteGrenzeMs`, fester Wert 500 | grün (R-3) |
| M24 (eigene) | Standardwert von `halteGrenzeMs` 5000 statt 500 | grün (R-3) |
| M5+M12 | beide zusammen | grün (R-3) |

**Zeitfälle und Flackern.** Zwei Fälle messen eine Zeit. Verzögern: Untergrenze fehlt, Obergrenze „zweiter Record unter 1000 ms bei 1500 ms Verzögerung“; gemessen in acht Wiederholungen 0,014 bis 0,061 ms (Last 10 bis 13): Abstand zur Schwelle rund fünf Größenordnungen. Haltegrenze: Untergrenze „nicht vor 250 ms bei 300 ms“; gemessen 300,5 bis 303,0 ms; die Untergrenze wächst unter Last nicht (die Uhr läuft vor dem Zeitgeber an). Alle übrigen Fälle haben als obere Grenze nur die Lesezeit des Clients von 5 s; siebzehn Läufe der Klasse (3 + 6 + 8, zuletzt mit Zeitausgabe) ohne einen roten Fall. Kein knappes Wanduhr-Fenster.

**Konstruktoränderung.** `CancelZusatzMessungTests.cs` ruft `new RecordRelais(host, port, eingriff, antwortLaenge, verzoegerungMs)` (Zeile 104) ohne `halteGrenzeMs`, also mit dem Standard 500 ms; vorher stand `HalteGrenze = TimeSpan.FromMilliseconds(500)` als Konstante. Gleicher Wert, gleiche Verwendung (`new Timer(…, _halteGrenze, Timeout.InfiniteTimeSpan)` an derselben Stelle): die Messaussagen sind unberührt. `git diff 20fd2274 HEAD` für `CancelZusatzMessungTests.cs`, `tools/` und `harness/` ist leer; die Aussage „Skript, Make-Datei und `CancelZusatzMessungTests` bleiben gleich“ stimmt. `live-ausnahmen.tsv`: Spalten 1 bis 4 aller Regeln gegen `main` unverändert (gleicher Hash), nur der Grund ist umformuliert.

**Kommentar zur Grenze des Zerlegers.** „ein unbekanntes erstes Byte am Ende eines Lesevorgangs wird erst mit dem nächsten Lesevorgang herausgegeben“ ist wahr: nach `_beendet = true; continue;` endet die Schleife bei `i == bytes.Length`, `_roh` hält das Byte bis zum nächsten `Fuettern`. Die Aussage im Protokoll „in den 640 Durchläufen mit Relais … genau drei Datenrecords“ stimmt (640 von 640 Zeilen `server-records=3`).

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| R-1 | MEDIUM | Die Bedingung `_attention` im Zweig `Verzoegern` (`var antwort = _attention && _wirkt && …`) trägt kein Test: M20 (`_attention` gestrichen) lässt alle elf Fälle grün. Der Bruder der Bedingung beim Bündeln (M5) ist jetzt belegt, der Fall für `Verzoegern` fehlt (kein Fall schickt vor dem ATTENTION des Clients einen Record der Antwortlänge durch ein Relais im Modus `Verzoegern`). Failure-Szenario: ein Umbau lässt `Verzoegern` den ersten Record der Antwortlänge 50 schon vor dem Cancel verzögern (Anmeldung, Vorabfrage); der Messlauf `relais-verzoegert` misst dann nicht mehr die Verzögerung des `DONE[ATTN]`, und `RecordRelaisTests` bleibt grün. | Skill „Eigencode, dessen Entfernung keinen Test rot macht“ | `referenztest-dotnet/Mitschnitt/RecordRelais.cs` · „var antwort = _attention && _wirkt && record != null && record.Laenge == _antwortLaenge && record.Typ == 0x17;“ | ja — Mutation M20 in `RecordRelaisTests` | Eigencode ohne Test, Mutation bleibt grün |
| R-2 | LOW | Die Zeile „Regel bleibt“ der Optionen nennt in der Spalte „Änderung“ „keine (`live-ausnahmen.tsv` und `SPEC-045` unverändert)“; dieselbe Nachbesserung ändert den Grund der Zeile in `live-ausnahmen.tsv` (Diff gegen `main`), und das Protokoll sagt es im Absatz „Bedingungen“ selbst („nur der Grund ist umformuliert“). Die Ergebnispaare sind unverändert (nachgerechnet), die Datei nicht. | `AGENTS.md` §3.8 | `docs/messungen/referenzabgleich.md` · „keine (`live-ausnahmen.tsv` und `SPEC-045` unverändert)“ | ja — `git diff main...HEAD -- referenztest-dotnet/Vergleich/live-ausnahmen.tsv` | Aussage ungenauer als der Diff |
| R-3 | INFO | Der neue Parameter `halteGrenzeMs` ist durch keinen Fall gebunden: M12 (fester Wert 500) und M24 (Standard 5000) bleiben grün; M5 zusammen mit M12 bleibt ebenfalls grün, der Fall zu `_attention` verliert dann seine Kraft (die 30 s Haltegrenze trägt ihn). Die Wirkung eines einzelnen Fehlers ist nicht beobachtbar, deshalb INFO. Der Standard 500 ms (der Wert der Messläufe) ist nur durch Lesen des Diffs belegt, nicht durch einen Test. | Maintainability | `referenztest-dotnet/Mitschnitt/RecordRelais.cs` · „_halteGrenze = TimeSpan.FromMilliseconds(halteGrenzeMs);“ | ja — Mutationen M12, M24, M5+M12 | — |
| R-4 | INFO | Das Protokoll nennt „nicht mutiert und nicht gesehen: die Typprüfung `Typ: 0x17` im Rückruf für den Client und `_wirkt = false` in der Freigabe von `Buendeln`“. Selbst gefahren: M22 (Typprüfung) bleibt grün, wie genannt; M11 (`_wirkt = false` in der Freigabe) ist rot (`Eingriff_Buendeln_haelt_den_ersten_Record_bis_der_der_Antwortlaenge_da_ist`), die Grenze ist an dieser Stelle enger als genannt. | Maintainability | `docs/messungen/referenzabgleich.md` · „**Nicht mutiert und nicht gesehen:**“ | ja — Mutation M11 | — |
| R-5 | INFO | Der Grund der Zeile in `live-ausnahmen.tsv` nennt Client-Version, Quelle (GitHub-Tag, nicht das NuGet-Paket) und die Art des Belegs (Eingriffe am Relais, Bytes unverändert), aber nicht die Prämisse `ReturnImmediately` und nicht, dass die Zahl der Records je `Read` nicht gemessen ist; das Protokoll sagt, die Interventionen stützten die Aussage ohne diese Zwischenstufe, der Grund zeigt auf das Protokoll. In Zelle A-7 steht „Bündeln 0 von 160, Verzögern 80 von 80 Durchläufen“ mit verschiedener Grundmenge (160 enthält den Server, 80 nur die Referenz; der Server liefert den Zusatz mit Verzögern ebenfalls in 80 von 80), im Absatz „Ergebnis“ ist der Bezug genannt. | `AGENTS.md` §3.8 | `referenztest-dotnet/Vergleich/live-ausnahmen.tsv` · „Bedingung am 2026-11-22 fuer Microsoft.Data.SqlClient 7.1.1“; `docs/messungen/referenzabgleich.md` · „(Bündeln 0 von 160, Verzögern 80 von 80 Durchläufen“ | nein — Lesen | — |
| R-6 | INFO | `vergleichslauf-d-selbsttest.sh` liest die echte `live-ausnahmen.tsv` nicht (eigene Listen im Test); dass die Liste nach der Änderung des Grundes liest, ist nur durch den eigenen Lauf von `--vergleich` gegen die echte Liste belegt (siehe Sensor 4: grün). Die Zahl 133 in `docs/plan/spec-abdeckung.md` (129 + 4 Fälle, `RecordRelaisTests`) stammt aus einem Lauf, den dieser Review nicht fährt (`make spec-abdeckung` ausgeschlossen); sie ist mit vier neuen Fällen stimmig. Das Protokoll stützt „ReturnImmediately im Review bestätigt“ auf den Erstreview ohne Fundstelle („die Fundstellen stehen hier nicht“), und im Slice §6 stehen Risiken im Wortlaut vor der Entscheidung („Bis zur Entscheidung des Nutzers bleibt `LAUF=live` gelegentlich rot“, „selten (1 von 21)“); beides ist Gegenstand von Planner und Verifier. | Maintainability | `docs/plan/spec-abdeckung.md` · „RecordRelaisTests“; Slice §6 · „Bis zur Entscheidung des Nutzers bleibt“ | nein | — |
| R-7 | INFO | Die Nachbesserung ändert Testcode und eine Konstruktorsignatur nach dem Review-Report; dieser Report ist der zweite Review dieser Fläche (Skill „Nachbesserung mit Code ohne zweiten Review“). Kommt für R-1 ein weiterer Fall hinzu, ist dieselbe Fläche wieder Nachbesserung: ein weiterer Re-Review oder der Vermerk „Nachbesserung ohne zweiten Review“ in der Closure-Notiz §7 mit Commits, Grund und der Prüfung. | Maintainability | `docs/plan/planning/done/slice-cancel-zusatztext-ursache-untersuchen.md` · „7. Closure-Notiz“ | nein | — |

Hard Rules: **§3.6** nicht berührt (`live-ausnahmen.tsv`: Kennungen, Ergebnispaare und Art unverändert; „Regel bleibt“ lockert den Sensor nicht; `GATE_CHECKS`, `detekt.yml`, Stages unverändert). **§3.7**: die neuen Kommentare (Grenze des Zerlegers, `halteGrenzeMs` im KDoc, Test-Kommentare, KDoc von `Strecke`) tragen Zusage, Grenze oder Kopplung im Indikativ, keine verworfene Alternative. **§3.8**: Befunde R-2, R-5 oben; alle Zahlen des Protokolls, die dieser Lauf nachgerechnet hat, tragen. **§3.1**: alle Läufe in Docker; kein Host-Toolchain-Aufruf.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/`, `testserver/` (Produktionscode) | geprüft, ohne Befund (Diff leer) |
| `tools/`, `harness/`, `Makefile`, `Dockerfile`, `config/` | geprüft, ohne Befund (kein Diff seit `20fd2274`) |
| `docs/user/`, `spec/`, `docs/plan/adr/` | geprüft, ohne Befund (keine Änderung; Grep nach der alten Lesart ohne Treffer in lebenden Dokumenten) |
| `docs/plan/kompatibilitaetsmatrix.md` (Zeile `Cancel()` nach 50 gelesenen Zeilen) | geprüft, ohne Befund |
| `docs/messungen/referenzabgleich.md` (A-7, Absätze Ergebnis 1 und 2, Quelle, Ursache, Optionen) | geprüft, Befunde R-2, R-4, R-5 |
| `referenztest-dotnet/Mitschnitt/` (`RecordRelais.cs`, `RecordRelaisTests.cs`) | geprüft, Befunde R-1, R-3 |
| `referenztest-dotnet/Vergleich/live-ausnahmen.tsv` | geprüft, Befund R-5 (INFO); Liste liest (Sensor 4) |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 1 |
| LOW | 1 |
| INFO | 5 |

**Finding-Klassen dieses Laufs:** Eigencode ohne Test, Mutation bleibt grün (zweiter Lauf desselben Slice; Wiederholung der Klasse F-4) · Aussage ungenauer als der Diff · Parameter eines Werkzeugs ohne Bindung durch einen Fall.

## Verdikt

**Merge-blockierend:** ja, wegen R-1 (ein MEDIUM, kein HIGH). F-1 bis F-8 sind behoben, nicht nur umformuliert (Beleg je Finding oben); F-4 ist für M4, M5, M6, M8, M9 mit je einem roten Fall belegt, die Fälle flackern in 17 Läufen nicht.

**Empfehlung:** Nachbesserung, klein (ein Fall zu R-1, eine Zeile Protokoll zu R-2), danach weiter an den Verifier. Für den Fall zu R-1 gilt R-7: erneuter Re-Review dieser Fläche oder Vermerk in §7. R-3 bis R-7 verlangen keine Aktion.

**Übergabe:** R-1 und R-2 an den Implementer; R-6 (Slice §6) an den Planner; die Finding-Klasse „Eigencode ohne Test, Mutation bleibt grün“ in die Closure-Notiz §7 und von dort in den Zähler. Die Prüfung gegen die DoD liegt beim Verifier.
