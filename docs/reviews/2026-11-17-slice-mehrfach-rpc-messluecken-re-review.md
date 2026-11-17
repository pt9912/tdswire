# Re-Review: slice-mehrfach-rpc-messluecken — 2026-11-17

**Review-Art:** Re-Review der Nachbesserung `e934b9af` gegen den Report [2026-11-17-slice-mehrfach-rpc-messluecken](2026-11-17-slice-mehrfach-rpc-messluecken.md), gegen Plan, ADR und Hard Rules; nicht gegen die DoD. Gelesen ist nur `git diff a32c8618..HEAD` (Code: `referenztest-dotnet/Mitschnitt/MessungBelegTests.cs`, 12 Zeilen; Doku: `spec/spezifikation.md`, `docs/plan/kompatibilitaetsmatrix.md`, `docs/messungen/referenzabgleich.md`, der Vorgänger-Report).

**Skill:** `.harness/skills/reviewer.md` @ `e934b9af` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-17

**Eingangs-Kontext:** Vorgänger-Report mit F-1 bis F-6, [ADR-0051](../plan/adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) (im Diff unverändert), [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), `AGENTS.md` §3.7 und §3.8.

**Selbst gefahren** (Last 6 beim Start, 16 GiB verfügbar; keine künstliche Last; kein `make vergleichslauf-d`, kein `clean-build`, kein `e2e`):

- `make docs-check`, `make flaeche`, `make kopplung-zahlen`, `make gate-zuordnung`: je Exit 0.
- `git diff --stat a32c8618..HEAD -- referenztest-dotnet/Mitschnitt/messung`: leer, die Ablagen sind unverändert.
- `docker build --target test` am Stand: Exit 0 (aus dem Layer-Cache, kein Testzähler im Log). Drei Mutationen an Kopien von `referenztest-dotnet/` im Scratchpad, der Arbeitsbaum blieb unverändert:

| Mutation | Wirkung (gemessen) |
|---|---|
| Schwelle `== 24` auf `== 23` | rot: `Der_Referenzclient_trennt_in_keiner_Messung_mit_dem_Byte_0x80` (2022 und 2025), sonst 197 grün |
| Ablage 2022 `rpc-batch-fehler-weiter/03`: Parametername `@Id` im ersten Aufruf auf `@Jd` | rot: `ContinueUpdateOnError_aendert_den_Draht_nicht_und_der_Client_wirft_nicht(2022)` und der Gleichheitstest der Instanzen |
| dieselbe Ablage-Mutation, die neue Regex-Assertion aus dem Test entfernt | nur noch der Gleichheitstest der Instanzen rot, `ContinueUpdateOnError_…` grün: die neue Assertion trägt die Anfrage-Aussage allein |

Eine erste Fassung der zweiten Mutation (Buchstabe im SQL-Text) blieb grün, weil die Auswertung `anfrage` nur Name, Parameternamen, Typen und Längen führt, nicht den SQL-Text; das ist die Zusage des Tests („gleiche Folge der Aufrufe und Parameter“), kein Befund.

## Stand der Findings des Vorgängers

| ID | Vorher | Stand | Beleg |
|---|---|---|---|
| F-1 | MEDIUM | **behoben** | `SPEC-003`: die Setzung „`text`, `ntext` und `image` bleiben `TDS_UNSUPPORTED_TYPE`“ steht unverändert, das Wort „(Setzung)“ ist ergänzt. Der falsche Messsatz „kein gemessener Client sendet sie“ gilt nur noch für `text` und `image`; für `ntext` steht die Messung (Referenzclient 7.1.1, 1360 und 1400 Parameter, W-22) und „ob der Server dieser Bibliothek eine solche Nachricht annimmt, ist nicht gemessen“. Die Nutzerfrage steht als „Offen, Entscheidung des Nutzers“ mit beiden Wegen (Code angleichen oder Zielbild anpassen, `ntext` bleibt `TDS_UNSUPPORTED_TYPE`). Matrix (Zeile „mehrere RPCs in einer Nachricht“), `SPEC-030` und Protokoll („Grenzen“) sagen „sendet“ und „nicht gemessen“ und treffen keine Aussage über den Server. Das Zielbild ist nicht still geändert; die Weiterführung in `SPEC-003` Zeile 539 („Nicht gemessen: `text`, `ntext`, …“) und Matrix Zeilen 83 und 128 bleibt wahr. |
| F-2 | MEDIUM | **behoben** | `SPEC-030` und Protokoll nennen „drei gemessene Formen“, Client, Version und `INSERT` mit einem Parameter im selben Satz; „andere Formen sind nicht geprüft“ steht dabei. |
| F-3 | LOW | **behoben** | `KeinFehlerAmAnfang` ist entfernt; `grep` findet nur noch den Vorgänger-Report. |
| F-4 | LOW | **behoben** | Die Schwelle ist `== 24` mit dem Kommentar „24 Mehrfach-Nachrichten je Instanz … (Bestand der Ablage)“; die Mutation auf 23 ist rot (siehe oben). |
| F-5 | LOW | **teilweise**, siehe N-1 | Der Name sagt nun Einzel-RPCs und Mehrfach-Nachrichten getrennt, der Mehrfach-Teil ist breiter als die Prüfung. |
| F-6 | LOW | **behoben** | Die Anfrage wird mit Regex (`\d+` auf `#`) verglichen; Ablage-Mutation rot, ohne die Assertion grün (siehe oben); das Protokoll (W-15) sagt „Anfrage gleich gebaut (… Test), Antwort gleich“. |

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| N-1 | MEDIUM | Der umbenannte Test sagt „in Mehrfach-Nachrichten folgt jeder RETURNVALUE RETURNVALUE oder DONEPROC“, prüft aber nur `folge.Split("||")[0]`, den ersten Aufruf. Ein späterer Aufruf mit COLMETADATA oder einem anderen Token hinter RETURNVALUE bliebe grün, während der Name es ausschließt. (Aus dem Code gelesen, nicht per Mutation gefahren; die Zeile der Prüfung ist im Diff unverändert, der Name neu.) | AGENTS.md §3.8, Skill „Aussage breiter als ihr Beleg“ | `referenztest-dotnet/Mitschnitt/MessungBelegTests.cs` · `Assert.DoesNotMatch(@"RETURNVALUE(?!\s+(RETURNVALUE\|DONEPROC))", folge.Split("\|\|")[0]);` und `In_Einzel_RPCs_steht_nichts_hinter_RETURNVALUE_ausser_DONEPROC_und_in_Mehrfach_Nachrichten_folgt_jeder_RETURNVALUE_RETURNVALUE_oder_DONEPROC` | ja — Mutation in einer Ablage hinter dem ersten Aufruf | Testname breiter als die Prüfung |
| N-2 | LOW | Matrix („bei sehr vielen Parametern sendet der Client die Deklarationsliste als `ntext`“) und Protokoll („ein Client mit sehr vielen Parametern sendet eine `ntext`-Deklarationsliste“) sagen „der Client“ und „ein Client“; gemessen ist der Referenzclient in einer Version bei 1360 und 1400 Parametern, die Schwelle ist laut Protokoll nicht gemessen. `SPEC-030` und `SPEC-003` sagen „Referenzclient“. | AGENTS.md §3.8 | `docs/plan/kompatibilitaetsmatrix.md` · „bei sehr vielen Parametern sendet der Client die Deklarationsliste als `ntext`“; `docs/messungen/referenzabgleich.md` · „ein Client mit sehr vielen Parametern sendet eine `ntext`-Deklarationsliste“ | nein | Subjekt breiter als der gemessene Client |
| N-3 | INFO | `== 24` ist eine exakte Zahl: eine neue Ablage mit Trennbyte `0xFF` macht den Test rot, bis die Zahl nachgezogen ist. Die Zahl steht nur im Test (Kommentar und Prüfung auf derselben Seite), nirgends in Doku oder Spec; kein zweiter Ort. Kein Befund, nur Wartungshinweis an den, der die Ablage erweitert. | Maintainability | `MessungBelegTests.cs` · `24 Mehrfach-Nachrichten je Instanz` | ja | — |

## Negativbefund

| Verzeichnis | Ergebnis |
|---|---|
| `referenztest-dotnet/Mitschnitt/messung` | unverändert (leerer `git diff --stat`) |
| `referenztest-dotnet/Mitschnitt/MessungBelegTests.cs` | N-1; sonst geprüft, ohne Befund (Konstante entfernt, Schwelle und Anfrage-Assertion durch Mutation rot, kein Konjunktiv-Kommentar im Diff) |
| `spec/spezifikation.md` | geprüft, ohne Befund: Setzung zu `ntext` unverändert, Nutzerfrage offen geführt; keine neue Zusage ohne Lastenheft-Anker (kein Eintrag in `spec/lastenheft.md` nötig, die Stelle präzisiert `TDS-FA-04`/`TDS-FA-05` nur durch einen Messbefund) |
| `docs/plan/kompatibilitaetsmatrix.md`, `docs/messungen/referenzabgleich.md` | N-2; sonst geprüft, ohne Befund |
| `docs/plan/adr/` | im Diff nicht berührt; [ADR-0051](../plan/adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) unverändert |
| `src/`, `testserver/`, `tools/`, `harness/`, `docs/user/` | im Diff nicht berührt |

## Summary

Keine HIGH-Findings. Fünf der sechs Vorgänger-Findings sind behoben, F-1 ohne stille Änderung des Zielbilds; F-5 ist nur teilweise behoben, weil der neue Testname („jeder RETURNVALUE“ in Mehrfach-Nachrichten) mehr sagt, als die Prüfung (erster Aufruf) trägt (N-1, MEDIUM). Dazu ein LOW zum Subjekt „Client“ in Matrix und Protokoll (N-2) und ein INFO. Die Mutationen zu F-4 und F-6 sind rot; die Ablagen sind unverändert. Eine Nachbesserung von N-1 ändert Testcode oder den Namen; ändert sie nur den Namen, braucht sie keinen dritten Review, ändert sie die Prüfung oder eine Ablage, schon (Skill MEDIUM „Nachbesserung mit Code ohne zweiten Review“).
