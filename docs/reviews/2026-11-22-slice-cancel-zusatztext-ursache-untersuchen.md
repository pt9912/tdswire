# Review-Report: slice-cancel-zusatztext-ursache-untersuchen — 2026-11-22

**Review-Art:** Code (Messwerkzeug, Messprotokoll, Nachzug-Stellen) gegen Plan, ADR und Hard Rules; keine Prüfung gegen die DoD (das ist die Frage des Verifiers).

**Gegenstand:** Diff `main...HEAD` im Zweig `slice-cancel-zusatztext-ursache-untersuchen`, Commits `20fd2274` (Messwerkzeug), `a3a8160c` (Messprotokoll, Rohzeilen, Matrix, Grund der Regel), `cacaca4d` (Nachtrag Stand der Werkzeuge). `git diff main...HEAD -- src testserver` ist leer: Produktionscode unverändert.

**Skill:** `.harness/skills/reviewer.md` (Stand des Zweigs) · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-22

**Eingangs-Kontext:**

- Slice `slice-cancel-zusatztext-ursache-untersuchen` (§1 bis §6)
- [ADR-0054](../plan/adr/0054-antwort-auf-attention-in-zwei-nachrichten.md) (Zwei-Nachrichten-Form, Entscheidung 2), [ADR-0057](../plan/adr/0057-abbruch-beim-beenden-endet-im-paar.md), [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md)
- `TDS-FA-07`, `TDS-QA-01`
- `AGENTS.md` §3.1 bis §3.8, `harness/README.md` §Minimal agent workflow
- Messprotokoll des Referenzabgleichs (Abschnitt „Zusatz der Antwort auf `Cancel()`“), `docs/messungen/cancel-zusatz-2026-11-22-rohzeilen.txt`

**Ausgeführte Sensoren** (Docker beziehungsweise Host; Wegwerf-Kontexte im Scratchpad, nichts davon im Repo; `free -m` available 12 bis 14 GB; keine Messsperre angelegt, keine Referenzinstanz gestartet):

1. Nachrechnen der Zahlen des Messprotokolls aus den Rohzeilen mit `grep` und `awk` (Zählung je Ziel und Modus, Teil A und B, Relais-Abstände, Muster der Records, Dauer, 920 Zeilen `durchlauf`): alle Zahlen im Messprotokoll tragen (Ergebnis unten).
2. Fundstellen der Quelle des Clients: `TdsParser.cs` und `SqlDataReader.cs` im Tag `v7.1.1` von `dotnet/SqlClient` von GitHub geholt (nur gelesen, im Scratchpad) und die Zeilen 3070 bis 3076, 3100 bis 3118, 3646 bis 3651, 3676 bis 3687, 3715 bis 3722 gegen den Text gelesen.
3. `RecordRelaisTests` (sieben Fälle) in einem Wegwerf-Kontext (Kopie von `referenztest-dotnet/` im Scratchpad, `Dockerfile` mit Filter `RecordRelaisTests`, Stage `test`, Restore aus dem Layer-Cache): Ausgangslauf 7 von 7 grün. Zehn Mutationen von `RecordRelais.cs`, je ein Lauf (Ergebnis unten).
4. `docker build --target vergleich referenztest-dotnet` (Bau von `CancelZusatzMessungTests`): Build succeeded, 0 Warnungen. `bash tools/vergleichslauf-d-selbsttest.sh`: grün. `bash -n tools/vergleichslauf-d.sh`: ohne Befund.
5. `make docs-check` (0 Befunde), `make kopplung-zahlen`, `make flaeche` (je ohne Fehler).
6. Grep nach der alten Lesart („Operation cancelled by user“, „Zusatz“, „nicht untersucht“) über `spec/`, `docs/`, `harness/`, `tools/`, `referenztest-dotnet/`; Abgleich der Plan-Tabelle §3 gegen `git diff --name-only main...HEAD`.

Nicht gefahren (Tabelle `harness/README.md` §Minimal agent workflow und Auftrag): `make gates`, `make clean-build`, `make e2e`, `make kompatibilitaet`, `make vergleichslauf-d`, `make spec-abdeckung`.

## Nachgerechnete Messaussagen (Schwerpunkt 1)

Alle Zahlen des Messprotokolls stimmen mit den Rohzeilen überein:

| Aussage | Rohzeilen |
|---|---|
| Teil A, `direkt`: Referenz 28 und 29 von 40, Server 40 von 40 (je Lauf) | gleich |
| `relais` 25 und 26, `relais-einzeln` 23 und 21, `relais-verzoegert` 40 und 40 (Referenz), `relais-buendeln` 0 von 40 (Referenz 2022, 2025, Server 2022, 2025) | gleich |
| Teil B: Referenz 8 und 9 von 30, Server 30 und 30 von 30 | gleich |
| 380 Durchläufe des Servers mit Zusatz ohne Bündeln (320 in Teil A, 60 in Teil B) | gleich |
| 920 Zeilen `durchlauf`, 629 mit Zusatz (232 Referenz Teil A, 320 Server Teil A, 77 Teil B), `zusatz=1` genau bei `fehler=2` | gleich |
| 640 Durchläufe mit Relais: je ein Record der Länge 37 vom Client, genau drei Datenrecords vom Server; Server 53 Byte in 319 von 320, einmal 71; Referenz Länge 68, Server 66 | gleich |
| Abstand am Relais (`relais`): Referenz 0 / 57 / 237 µs und 0 / 59 / 523 µs, Server 40130 / 40644 / 42061 µs und 38056 / 41266 / 44009 µs; über alle Server-Modi 38,1 bis 44,2 ms | gleich |
| `dauer-ms` `direkt`: Server 41,6 / 42,7 / 60,9 und 41,4 / 42,5 / 57,5; Referenz 0,4 / 0,8 / 36,2 und 0,4 / 0,8 / 25,6; Maximum je erster Durchlauf | gleich |
| Median des Abstands je Durchlauf an der Referenz ohne und mit Zusatz | weicht in der Zuordnung ab (F-6) |

Die Fundstellen der Quelle des Clients (Zeilenbereiche) stimmen mit dem Tag `v7.1.1` überein. Die Ablaufbeschreibung von `TryRun` ist richtig; sie hängt an der Prämisse, dass der Lesepfad `TryRun` mit `RunBehavior.ReturnImmediately` ruft (stimmt: `SqlDataReader.cs`, mehrere Aufrufe), die das Protokoll nicht nennt (F-1).

## Mutationen am Messwerkzeug (selbst gefahren)

Engste Testklasse `RecordRelaisTests`, sieben Fälle, je Mutation ein Lauf:

| Nr. | Mutation | Ergebnis |
|---|---|---|
| M1 | Länge eines TLS-Records ohne die 5 Byte Kopf | rot (6 von 7) |
| M2 | `Buendeln` ohne `Halten` (sofort schreiben) | rot (nur der Fall `Eingriff_Buendeln…`) |
| M3 | `Verzoegern` ohne `Thread.Sleep` | rot (nur der Fall `Eingriff_Verzoegern…`) |
| M4 | Haltezeit-Timer (`HalteGrenze`, 500 ms) nicht angelegt | grün |
| M5 | `Buendeln when _wirkt` statt `when _attention && _wirkt` | grün |
| M6 | `_wirkt = false` im Zweig `Verzoegern` gestrichen | grün |
| M7 | Verzögern und Bündeln ohne den Vergleich mit der Antwortlänge | rot (2 Fälle) |
| M8 | Tor `_scharf` im Client-Callback aufgehoben (`_scharf \|\| record != null`) | grün |
| M9 | Weitergabe des nicht gedeuteten Rests (`BeiRecord(…, null)`) gestrichen | grün |
| M10 | Prüfung `_laenge < kopfLaenge` im Zerleger entfernt | rot |

Die im Messprotokoll genannten drei Mutationen (Zerlegung ohne Kopflänge, `Buendeln` ohne Halten, `Verzoegern` ohne Warten) sind rot, wie beschrieben.

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Die Bedingung des Zusatzes steht als „belegt durch Quelle und Intervention“, ohne im Satz zu nennen: Quelle ist der GitHub-Tag, nicht das NuGet-Paket (steht nur im Absatz „Erzeuger“, „angenommen, nicht verglichen“); die Zahl der TLS-Records je `stream.Read` ist weder gelesen noch gemessen; die Aussage „liegt die zweite Nachricht schon im Puffer, endet der Aufruf“ gilt nur, weil der Lesepfad `ReturnImmediately` ruft (nicht genannt). Die Erklärung der Rate an der Referenz (c) „oft schon im Puffer, oft nicht“ trägt keine Kennzeichnung als Vermutung, obwohl der Abstand am Relais die Fälle „nicht trennt“. Dieselbe Aussage steht in der Zelle A-7 und im Grund der Zeile in `live-ausnahmen.tsv` („der Client ergaenzt den Zusatz, wenn das DONE mit ATTN spaeter bei ihm ankommt …“) ohne Client-Version und ohne die Art des Belegs (Eingriffe am Relais an 40 Durchläufen je Ziel). Failure-Szenario: wer bei einem roten `LAUF=live` die Ausnahmeliste liest, nimmt die Bedingung als gemessen am Client, nicht als am Relais erzeugt. | `AGENTS.md` §3.8, Skill „Zusage ohne ihre gemessene Bedingung im selben Satz“ | `docs/messungen/referenzabgleich.md` · „(a) **Bedingung des Zusatzes**: belegt durch Quelle und Intervention“ und „(c) **Warum die Referenz selten den Zusatz zeigt**: bei Abständen im Mikrosekundenbereich liegt die zweite Nachricht oft schon im Puffer, oft nicht“; `referenztest-dotnet/Vergleich/live-ausnahmen.tsv` · „der Client ergaenzt den Zusatz, wenn das DONE mit ATTN spaeter bei ihm ankommt“ | nein — Lesen | Aussage breiter als Beleg, Bedingung nicht im Satz |
| F-2 | MEDIUM | Dieselbe Rate der Referenz steht an drei Orten mit verschiedenen Spannen: Matrix „8 von 30 bis 40 von 40 mit“ (schließt den Eingriff Verzögern ein, lässt den Eingriff Bündeln mit 0 von 40 aus), Zelle A-7 und Grund der Zeile „27 bis 73 %“, Optionen „27 bis 73 %“ (diese Spanne schließt den Eingriff `relais-einzeln` ein und Verzögern/Bündeln aus). Kein Sensor und kein Kopplungskommentar hält die Orte gleich; die Closure-Notiz nennt sie nicht. | `AGENTS.md` §3.7 (Kopplung), Skill „Zahl … steht an einem zweiten Ort ohne Sensor“ | `docs/plan/kompatibilitaetsmatrix.md` · „(8 von 30 bis 40 von 40 mit; Bedingung:“; `referenztest-dotnet/Vergleich/live-ausnahmen.tsv` · „in 27 bis 73 % der Durchlaeufe“ | nein — Lesen | Zahl an mehreren Orten, Spannen verschieden |
| F-3 | MEDIUM | Die Option „Server angleichen“ begründet den Spielraum mit „der Zeitpunkt ist dort ausdrücklich dem Bau überlassen“. [ADR-0054](../plan/adr/0054-antwort-auf-attention-in-zwei-nachrichten.md) Entscheidung 2 überlässt dem Bau den Zeitpunkt der ersten Nachricht relativ zum Abschluss des Backends; für die zweite Nachricht sagt sie „folgt unmittelbar danach unter derselben Sperre (Setzung)“, ebenso `SPEC-029`. Der gemessene Abstand von rund 40 ms zwischen beiden Nachrichten wird nirgends zu diesem Wortlaut ins Verhältnis gesetzt (er kann im Kernel liegen, die Ursache ist „nicht festgestellt“). Failure-Szenario: der Nutzer entscheidet die Optionen im Glauben, der Abstand sei vom ADR freigegeben; ob der Server der „unmittelbar“-Setzung genügt, bleibt ungesagt. | `AGENTS.md` §3.8, [ADR-0054](../plan/adr/0054-antwort-auf-attention-in-zwei-nachrichten.md) Entscheidung 2 | `docs/messungen/referenzabgleich.md` · „der Zeitpunkt ist dort ausdrücklich dem Bau überlassen“ | nein — Lesen | Aussage über eine ADR breiter als ihr Wortlaut |
| F-4 | MEDIUM | Neuer Eigencode des Relais, dessen Streichung `RecordRelaisTests` grün lässt (selbst gefahren): Haltezeit-Timer von `Buendeln` (M4), die Bedingung `_attention` des Bündelns (M5), die Einmaligkeit des Verzögerns (M6), das Tor `Scharf()` (M8), die Weitergabe des Rests nach einem unbekannten ersten Byte im Eingriffsmodus (M9). Die Tests führen den Client erst nach `Scharf()` und schicken keine Bytes vor dem ATTENTION und keine unbekannte Art durch das Relais mit Eingriff. Failure-Szenario: ein Umbau lässt `Buendeln` die Antworten der Anmeldung halten oder hält Bytes ohne Freigabe zurück, und der Messtest, der nur Zeilen zählt, bleibt grün. Die Messung selbst zeigt das Verhalten in den 920 Durchläufen (drei Records, Muster gleich), nicht die Tests. | Skill „Eigencode, dessen Entfernung keinen Test rot macht“ | `referenztest-dotnet/Mitschnitt/RecordRelais.cs` · „case Eingriff.Buendeln when _attention && _wirkt:“ (und `new Timer(_ => Freigeben(), null, HalteGrenze`, `if (_scharf && record is { Typ: 0x17 })`, `BeiRecord?.Invoke(_roh.Concat(bytes[i..]`) | ja — Mutationen M4, M5, M6, M8, M9 in `RecordRelaisTests` | Eigencode ohne Test, Mutation bleibt grün |
| F-5 | MEDIUM | Die Plan-Tabelle in §3 nennt nicht alle berührten Dateien: `referenztest-dotnet/Mitschnitt/RecordRelais.cs` und `RecordRelaisTests.cs` (Tabelle nennt `referenztest-dotnet/Vergleich/`), `tools/mk/vergleichslauf-d.mk`, `harness/README.md`, `docs/plan/spec-abdeckung.md`, `docs/messungen/cancel-zusatz-2026-11-22-rohzeilen.txt` fehlen; `SPEC-045` (die neuen Tests tragen `@spec SPEC-045`) steht im Kopf nur als „nur, wenn die Regel ihre Art ändert“. Matrix und `live-ausnahmen.tsv` stehen in §2 und §3 „nur nach Entscheidung des Nutzers“ und sind im Diff geändert (Grund der Zeile, Werte gleich; die Entscheidung steht aus). Die Matrix ist in §7d (Zeile `Cancel()` nach 50 gelesenen Zeilen) geändert, nicht in §5 Nr. 12. Der Slice liegt nicht im Diff; der Abgleich bleibt dem Planner. | Skill „Plan-Tabelle nennt die berührten Dateien nicht“ | `docs/plan/planning/done/slice-cancel-zusatztext-ursache-untersuchen.md` · „update (nur nach Entscheidung des Nutzers)“ | ja — `git diff --name-only main...HEAD` gegen die Tabelle | Plan-Tabelle gegen Diff unvollständig |
| F-6 | LOW | „im Modus `relais` Median 38 bis 47 µs ohne, 58 bis 72 µs mit Zusatz“: im Modus `relais` allein stehen 38 und 41 µs (ohne) und 65 und 72 µs (mit); 46 und 47 µs (ohne) und 58 und 70 µs (mit) gehören zu `relais-einzeln`. Die Mediane stammen aus einem Wegwerf-`awk` (im Protokoll als Größenordnung benannt); der Befund „die Bereiche überlappen“ trägt. | `AGENTS.md` §3.8 | `docs/messungen/referenzabgleich.md` · „im Modus `relais` Median 38 bis 47 µs ohne, 58 bis 72 µs mit Zusatz“ | ja — `awk` über die Rohzeilen (Paare `letzte=<Länge>@<µs>,50@<µs>`) | Modusangabe breiter als die Zahlen |
| F-7 | LOW | Die Option „Regel präziser fassen“ nennt als Grenze, `vergleichen` kenne „je Regel genau ein Ergebnispaar“, zwei Referenztexte brauchten eine Änderung des Skripts. Der Kopf des Skripts sagt, ein Schritt dürfe mehrere Regeln mit verschiedenen Ergebnispaaren haben; das Hindernis ist der Fall „Referenz gleich Server“ bei vorhandener Regel („die Ausnahme dazu entfaellt“, rot). | `AGENTS.md` §3.8 | `docs/messungen/referenzabgleich.md` · „`vergleichen` in `tools/vergleichslauf-d.sh` kennt je Regel genau ein Ergebnispaar“ | ja — `bash tools/vergleichslauf-d.sh --vergleich` mit zwei Zeilen derselben Kennung | Grenze einer Option ungenau beschrieben |
| F-8 | LOW | Die Überschrift „(c) Warum die Referenz selten den Zusatz zeigt“ trägt die Lesart der Live-Läufe (1 von 21); der Absatz selbst misst 27 bis 73 % und sagt, „selten“ sei auf diese Messmethode nicht übertragbar. | `AGENTS.md` §3.8 | `docs/messungen/referenzabgleich.md` · „(c) **Warum die Referenz selten den Zusatz zeigt**“ | nein — Lesen | Überschrift breiter als der Absatz |
| F-9 | INFO | Die Messläufe liefen am Arbeitsbaum vor Commit `20fd2274`; „Test, Relais und Skript sind seither unverändert“ ist eine Aussage des Implementers, am Diff nicht prüfbar (das Protokoll benennt das). Verifier und Nachlauf können die Messung wiederholen (`LAUF=cancel`). | Maintainability | `docs/messungen/referenzabgleich.md` · „die Läufe liefen am Arbeitsbaum vor diesem Commit“ | nein | — |
| F-10 | INFO | `CancelZusatzMessungTests` urteilt nicht (Assert zählt nur Zeilen), liegt in `Vergleich/` (Stage `test` kopiert nur `Smoke` und `Mitschnitt`) und trägt die Kategorie `MessungCancelZusatz`; `harness/README.md` führt `make vergleichslauf-d` als „kein Gate“. Als Messwerkzeug mit Trait-Kategorie zulässig und nicht als Wächter ausgewiesen; `vergleichslauf-d-selbsttest.sh` kennt die neuen Lauf-Namen nicht (grün). | [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md) | `referenztest-dotnet/Vergleich/CancelZusatzMessungTests.cs` · „Der Test urteilt nicht über die Häufigkeit“ | ja — `make referenztest-dotnet` (Stage ohne den Test) | — |
| F-11 | INFO | In der Zelle von `make vergleichslauf-d` in `harness/README.md` entfällt das Präfix „Lauf D:“ neben dem neuen Eintrag `cancel`; der Commit nennt es nicht. Der Name „Lauf D“ steht in `tools/mk/vergleichslauf-d.mk` und im Abschnitt „Nicht behauptet“ weiter. | Maintainability | `harness/README.md` · „Referenzclient (Anmeldung, TDS 8.0, Live, Mitschnitt, `wire`, `bulk`, `cancel`)“ | nein | — |
| F-12 | INFO | Die Zeitschwellen in `RecordRelaisTests` (100, 140, 500 ms bei Verzögerungen von 150 und 400 ms) laufen in `make gates` (Klasse `tests`) unter wechselnder Last; im Lauf unter Last 10 grün (152 bis 558 ms). Kein Befund ohne roten Lauf. | Maintainability | `referenztest-dotnet/Mitschnitt/RecordRelaisTests.cs` · „Assert.True(erstes < 100“ | nein | — |
| F-13 | INFO | Register `BEO-TDS/cancel-antwort-text-weicht-vom-echten-server-ab` (`state.md`: „Die Herkunft der zwei Textvarianten ist offen“) und die Closure-Notiz stehen nicht im Diff; das ist DoD-Gegenstand der Closure (Planner, Verifier). | Maintainability | `docs/plan/planning/observations/BEO-TDS/cancel-antwort-text-weicht-vom-echten-server-ab/state.md` | nein | — |

## Antworten auf die Schwerpunkte

1. **Zahlen:** jede nachgerechnete Zahl trägt (Tabelle oben); eine Zuordnung (F-6) ist ungenau.
2. **Ursachenurteil:** die Bedingung an den Extremen (Bündeln 0 von 160, Verzögern 80 von 80) und die Fundstellen der Quelle sind belegt; „belegt“ gilt am Relais, für Quelle als GitHub-Tag und ohne die Zahl der Records je `Read`. Die Vermutung „Nagle und verzögerte Bestätigung“ ist als „Vermutung ohne Beleg“ gekennzeichnet, `src/main` setzt tatsächlich kein `TCP_NODELAY` (gelesen). Die frühere Rate 1 von 21 wird als nicht erklärt geführt, ohne die früheren Protokolle umzudeuten (A-7, „Nicht deterministisch“ und die Läufe vom 2026-11-19 und 2026-11-20 unverändert). Beanstandet ist die Verdichtung in F-1 und F-8.
3. **Werkzeug:** Zerleger und Relais lesen sich richtig (Randfälle Lesegrenze im Kopf, zwei Records in einem Read, unbekanntes erstes Byte, Länge unter Kopf); drei Mutationen rot wie beschrieben, fünf weitere grün (F-4). Änderung in `tools/vergleichslauf-d.sh` und Make-Datei ist ein neuer Lauf ohne Urteil, kein Gate, kein Gate gelockert (`GATE_CHECKS` unverändert).
4. **Entscheidungsvorlage:** vier Optionen mit Wirkung und Grenze, keine gewählt; die Einordnung der Lockerung nach §3.6 („Entscheidung des Nutzers, gegebenenfalls ADR“) deckt sich mit dem Slice. Beanstandet: F-3, F-7.
5. **Plan-Tabelle und Nachzug:** F-5; die Zeile der Zelle A-7 und der Grund der Regel beschreiben Zustand statt Chronik, mit Ausnahme älterer, unveränderter Datumsangaben.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `src/`, `testserver/` (Produktionscode) | geprüft, ohne Befund (Diff leer) |
| `docs/user/` (Grep nach „Operation cancelled“, „Zusatz“, `Cancel()`) | geprüft, ohne Befund (keine Treffer) |
| `docs/plan/adr/` ([ADR-0054](../plan/adr/0054-antwort-auf-attention-in-zwei-nachrichten.md), -0057 unverändert) | geprüft, ohne Befund |
| `spec/` (`SPEC-029`, `SPEC-045` unverändert, Wortlaut „unmittelbar“ siehe F-3) | geprüft, ohne Befund außer F-3 |
| `docs/messungen/cancel-zusatz-2026-11-22-rohzeilen.txt` | geprüft, ohne Befund (920 Zeilen, keine Kennwörter, keine Hostnamen, Kürzung im Kopf genannt) |
| `tools/vergleichslauf-d.sh`, `tools/mk/vergleichslauf-d.mk` (Host statt Docker, Kommentare, Kennwort-Prüfung, Selbsttest) | geprüft, ohne Befund außer F-10 |
| `docs/plan/spec-abdeckung.md` (eine Zeile, 122 auf 129, `RecordRelaisTests`) | geprüft, ohne Befund |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 5 |
| LOW | 3 |
| INFO | 5 |

**Finding-Klassen dieses Laufs:** Aussage breiter als Beleg, Bedingung nicht im Satz · Zahl an mehreren Orten, Spannen verschieden · Aussage über eine ADR breiter als ihr Wortlaut · Eigencode ohne Test, Mutation bleibt grün · Plan-Tabelle gegen Diff unvollständig · Modusangabe breiter als die Zahlen · Grenze einer Option ungenau beschrieben · Überschrift breiter als der Absatz

## Verdikt

**Merge-blockierend:** ja (fünf MEDIUM, kein HIGH). Alle Zahlen des Messprotokolls tragen; die Nachbesserung betrifft Formulierungen, die Plan-Tabelle und fehlende Tests des Relais. F-4 ändert Testcode: nach der Nachbesserung braucht diese Fläche einen Re-Review in frischem Kontext (Skill „Nachbesserung mit Code ohne zweiten Review“) oder den Vermerk in der Closure-Notiz.

**Übergabe:** Findings gehen an den Implementer (F-5 an den Planner); die Finding-Klassen gehen zusätzlich in die Slice-Closure §7 und von dort in den Zähler. Die Prüfung gegen die DoD liegt beim Verifier.
