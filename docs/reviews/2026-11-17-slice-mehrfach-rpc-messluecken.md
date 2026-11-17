# Review-Report: slice-mehrfach-rpc-messluecken — 2026-11-17

**Review-Art:** Code, Ablagen und Messprotokoll, gegen Plan, ADR und Hard Rules; nicht gegen die DoD.

**Gegenstand:** Branch `slice-mehrfach-rpc-messluecken`, `git diff $(git merge-base HEAD main)..HEAD` (ein Commit `a32c8618`, 29 Dateien: zehn neue `.messung`-Ablagen je Instanz 2022 und 2025, `MessungWireTests.cs`, `MessAuswertung.cs`, `MessungBelegTests.cs`, Messprotokoll, `SPEC-030`, Kompatibilitätsmatrix, Slice-Plan, `spec-abdeckung.md`, Kopf von `vergleichslauf-d.mk`).

**Skill:** `.harness/skills/reviewer.md` @ `a32c8618` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-17

**Eingangs-Kontext:** Slice `slice-mehrfach-rpc-messluecken` (§1 bis §6), [ADR-0051](../plan/adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) (`Accepted`, im Diff unverändert), [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md), [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md), [SPEC-030](../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten), [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), [SPEC-045](../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig), [TDS-FA-04](../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-10](../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), `AGENTS.md` §3.5, §3.7, §3.8.

**Selbst gefahren** (Last 4,6 beim Start, 17 GiB verfügbar; keine künstliche Last; kein `make vergleichslauf-d`, kein `clean-build`, kein `e2e`):

- `make docs-check`, `make flaeche`, `make kopplung-zahlen`, `make gate-zuordnung`: je Exit 0 (vor Anlegen dieses Reports; `make docs-check` danach erneut, siehe Summary).
- `make referenztest-dotnet` liest nur die Ablage (Stage `test` kopiert `Smoke` und `Mitschnitt`, läuft ohne Netz und ohne Server); ich habe den Stand und vier Mutationen als `docker build --target test` an Kopien des Verzeichnisses `referenztest-dotnet/` im Scratchpad gefahren, der Arbeitsbaum blieb unverändert. Der Stand ist grün (aus dem Layer-Cache, kein eigener Testzähler im Log).
- Nachrechnung der Bytes der Ablage 2022 von Hand mit einem Wegwerf-Zerleger im Scratchpad (nicht im Repo, nur für diese Prüfung; die Aussagen im Report stehen auch ohne ihn in den Tests):

| Aussage | Nachgerechnet an | Ergebnis |
|---|---|---|
| W-14 erster Aufruf: DONEINPROC `MORE\|ERROR` (`0x03`), RETURNSTATUS 2627, DONEPROC `0x81`, danach zweimal DONEINPROC `0x11`/RS 0, DONEPROC `0x81` und `0x00` | `fehler-erster` | stimmt |
| W-14 letzter Aufruf: DONEPROC `0x00` nach Fehler, vorher zweimal `0x81` | `fehler-letzter` | stimmt |
| W-16 Übersetzungsfehler 208: kein DONEINPROC davor, DONEPROC `0x83` (Befehl `0x00e0`), zweiter Aufruf DONEPROC `0x00` | `fehler-vor-ergebnis/01` | stimmt |
| W-16 Laufzeitfehler 8134: DONEINPROC `0x03`, DONEPROC `0x81` ohne Fehlerbit, RETURNSTATUS 8134 | `/02` | stimmt |
| W-16 mittlerer von drei: DONEPROC `0x81`, dann `0x83`, dann `0x00` | `/03` | stimmt |
| W-17 Tokenfolge 1, 2, 1 Mengen, DONEPROC `0x81`, `0x81`, `0x00`; DML-gemischt Befehle `0xc3`, `0xc1`, `0xc5` je 2 Zeilen | `ergebnismengen/02`, `/03` | stimmt |
| W-18 Ordinal 2 und 2 (Werte 5 und 6); per Name 0 (Wert 5), zweiter Aufruf ohne RETURNVALUE | `ergebnismengen/04`, `/05` | stimmt |
| W-19 drei `c 03`-Nachrichten tragen nur ProcID 10, Trennbyte `0xFF`, weder `sp_prepexec` noch `sp_execute` | `vorbereitet/02` bis `/04` | stimmt |
| W-20 Namen `sp_prepexec` zweimal, `sp_prepexec` allein, `sp_execute` zweimal (`@handle` 3), `sp_prepexec` dann `sp_execute` mit `@handle` 4; Antworten ohne ERROR, DONEPROC `0x81`/`0x00` | `vorbereitet/05`, `/07`, `/08` | stimmt |
| W-21 1024 und 1025 Aufrufe in der Anfrage (ProcID-10-Zählung), Antwort 18432 und 18450 Bytes (je Aufruf 18 Bytes), DONEPROC `0x81` 1023 und 1024 mal, einmal `0x00`, kein ERROR | `aufrufe-1024`, `-1025` | stimmt |
| W-22 drei Aufrufe, Antwort 153 Bytes, DONEPROC `0x81` zweimal, `0x00` einmal, kein ERROR; 3 mal (1360 + 2) = 4086 und 3 mal (1400 + 2) = 4206 | `parameter-4086`, `-4206` | stimmt |
| Ablage 2025 trägt dieselben Zählungen wie 2022 | `aufrufe-*`, `parameter-*` | stimmt |
| Bytesummen 992949 (2022) und 993149 (2025); 137, 137, 329, 339 KB für die vier Grenz-Dateien | `wc -c` | stimmt |
| `ParameterBudget` zählt jeden von `readAll` gelesenen Parameter, `sp_executesql` und `sp_prepexec` nutzen `readAll` | `RpcParameterReader.kt`, `RpcCatalog.kt` | Zählweise wie in W-22 beschrieben |
| Die Requests von `fehler` und `fehler-weiter` unterscheiden sich nur im Tabellennamen (12 Byte Längenunterschied = 3 mal 2 Zeichen) | `fehler`, `fehler-weiter` | „die Option ändert den Draht nicht“ gilt auch für die Anfrage, getestet ist nur die Antwort (F-6) |

- Mutationen (je ein Lauf an einer Kopie, Wirkung gemessen):

| Mutation | Wirkung (gemessen) |
|---|---|
| 2022 `fehler-letzter`: DONEPROC des letzten Aufrufs `0x0000` auf `0x0003` | rot: `Ein_Fehler_im_letzten_von_drei_Aufrufen_endet_mit_DONEPROC_ohne_Bits(2022)` und der Gleichheitstest der Instanzen |
| 2025 `fehler-vor-ergebnis`: DONEPROC `0x0083` auf `0x0003` | rot: `Ein_Uebersetzungsfehler_im_nicht_letzten_Aufruf_…(2025)` und der Gleichheitstest |
| `MessAuswertung`: `typ == 0x63` (ntext) auf `0x62` | rot: `Der_echte_Server_beantwortet_Nachrichten_mit_4086_und_4206_Parametern_…` (beide Instanzen); `…ohne_Zerlegungsproblem` bleibt grün |
| 2022 `ergebnismengen`: Ordinal der ersten RETURNVALUE `0x0002` auf `0x0000` | rot: `Das_Ordinal_der_RETURNVALUE_zaehlt_…(2022)` und der Gleichheitstest |

Die von Implementer genannten ohne rote Mutation (Zahlen 1024 und 1025, Szenario `parameter-4086`) habe ich im Kopf bewertet: die Zahl `n` kommt aus der Schleife und wird gegen die Ablage gezählt, jede Änderung der Ablage röten den Test; `parameter-4086` ist der Vergleichsfall unter der Grenze und trägt die Aussage „4086 ist ebenfalls angenommen“, dieselbe Zählung wie 4206. Kein Befund. Die Schwelle `>= 17` ist F-4.

---

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | `SPEC-003` sagt weiter, `text`, `ntext` und `image` blieben `TDS_UNSUPPORTED_TYPE`, weil „kein gemessener Client sendet sie als RPC-Parameter“. W-22 zeigt, dass der Referenzclient in der Ablage die Deklarationsliste langer Parameterlisten als `ntext` sendet (`[1]- ntext`, in den Tests belegt). Die Folge steht nirgends: eine solche Nachricht (Client 7.1.1, 1360 Parameter je Aufruf) erreicht diesen Server nur, wenn der Typ vor der Zählgrenze 4096 nicht schon abgelehnt wird; Matrix, `SPEC-030` und Protokoll führen als Abweichung nur 1024/4096, nicht den Typ. Wer „Zielbild anpassen“ auf 4096 entscheidet, bekommt die Entscheidungsgrundlage unvollständig. | AGENTS.md §3.8 (Aussage trägt höchstens so weit wie der Beleg), Skill „Fortschreibung lässt die zweite Fundstelle stehen“, [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog) | `spec/spezifikation.md` · „kein gemessener Client sendet sie als RPC-Parameter“ | ja — `grep ntext spec/spezifikation.md` gegen W-22 in `docs/messungen/referenzabgleich.md` | Messung widerlegt Aussage an zweiter Fundstelle |
| F-2 | MEDIUM | `SPEC-030` sagt „der Referenzclient sendet sie von sich aus in keiner Mehrfach-Nachricht, W-19“ ohne Bedingung im Satz. Gemessen sind drei Formen (`SqlBatch.Prepare` zweimal, `SqlCommand.Prepare` vor `adapter.Update`), ein Client in einer Version, `INSERT` mit `@i`. Das Protokoll formuliert „in den drei Formen“, aber auch dort heißt es bei „Nicht lieferbar (1)“ „in den Formen, die er bietet“, was über die drei hinaus zählt. | AGENTS.md §3.8, Skill „Zusage ohne ihre gemessene Bedingung im selben Satz“ | `spec/spezifikation.md` · „der Referenzclient sendet sie von sich aus in keiner Mehrfach-Nachricht“; `docs/messungen/referenzabgleich.md` · „in den Formen, die er bietet“ | nein — Aussage über die Menge der Wege des Clients, nicht durch einen Lauf belegbar | Aussage ohne Bedingung im selben Satz |
| F-3 | LOW | `KeinFehlerAmAnfang` in `MessungBelegTests.cs` ist als `private const` angelegt und nirgends benutzt. | Maintainability | `referenztest-dotnet/Mitschnitt/MessungBelegTests.cs` · `private const string KeinFehlerAmAnfang` | ja — Compiler-Hinweis oder `grep` | nicht benutzte Konstante |
| F-4 | LOW | Der Test `Der_Referenzclient_trennt_in_keiner_Messung_mit_dem_Byte_0x80` verlangt `>= 17` Anfragen mit `Batchflag 0xff`; die Ablage hat 27 Mehrfach-Nachrichten je Instanz (nachgezählt). Zehn Nachrichten dürfen verschwinden, ohne dass der Test rot wird. Der Teil „kein `0x80`“ ist nicht leer: der Zerleger führt jedes Trennbyte als `Batchflag 0x..`. | Skill „Auswahl- oder Gleichheitseigenschaft …“ (Untergrenze lockerer als der Bestand) | `referenztest-dotnet/Mitschnitt/MessungBelegTests.cs` · `Count(z => z.Value.Contains("Batchflag 0xff"` | ja — Mutation: Ablage um bis zu zehn Mehrfach-Nachrichten kürzen | Schwelle unter dem Bestand |
| F-5 | LOW | Der Test heißt weiter `Kein_Szenario_stellt_COLMETADATA_hinter_RETURNVALUE_und_RETURNVALUE_steht_vor_DONEPROC`, gilt aber nur für Einzel-Nachrichten; in Mehrfach-Nachrichten steht COLMETADATA des nächsten Aufrufs hinter einer RETURNVALUE, und geprüft wird dort nur, dass auf jede RETURNVALUE eine weitere RETURNVALUE oder ein DONEPROC folgt. Die Unterscheidung ist begründet und nicht aufgeweicht (Einzel-Zweig unverändert, der Mehrfach-Zweig prüft jede RETURNVALUE statt nur der letzten); der Name sagt die Ausnahme nicht. | AGENTS.md §3.7 (Test-KDoc und Namen beschreiben den geltenden Zustand) | `MessungBelegTests.cs` · `Kein_Szenario_stellt_COLMETADATA_hinter_RETURNVALUE` | nein | Testname breiter als Assertion |
| F-6 | LOW | W-15 sagt „Die Option ändert den Draht nicht“; der Test vergleicht nur Tokenfolge, DONE-Zeilen und RETURNSTATUS der Antwort. Die Anfrage stimmt (von mir nachgerechnet: nur der Tabellenname unterscheidet sich), steht aber in keinem Test. | AGENTS.md §3.8 | `docs/messungen/referenzabgleich.md` · „Die Option ändert den Draht nicht; sie wirkt nur im Client“ | ja — Vergleich der Anfragen nach Normierung des Tabellennamens | Aussage über Anfrage und Antwort, Test nur Antwort |
| F-7 | INFO | Die beiden Abweichungen von [ADR-0051](../plan/adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) (Entscheidung 7 gegen W-20; Entscheidung 5 gegen W-21 und W-22) sind ehrlich geführt: `SPEC-030` lässt den Wortlaut der Setzungen stehen, nennt „Widerspruch“ und „die Entscheidung des Nutzers steht aus“, die Matrix nennt beide Zeilen als „Widerspruch, Entscheidung des Nutzers offen“, das Änderungsprotokoll der Spezifikation trägt die Zeile, das Protokoll zeigt die Bytes und die Grenze nach oben („oberhalb von 1025 und 4206 nichts gemessen“). [ADR-0051](../plan/adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) ist im Diff nicht berührt, keine stille Absenkung. Die Frage in §7 des Slice steht noch nicht (Closure-Notiz folgt durch den Planner). | AGENTS.md §3.5, §3.8 | `spec/spezifikation.md` · „die Setzung widerspricht der Messung, die Entscheidung des Nutzers dazu steht aus“ | nein | — |
| F-8 | INFO | `docs/user/einbettung-und-betrieb.md` (Zeile „`sp_prepexec` und `sp_execute` in einer solchen Nachricht ergeben `TDS_UNSUPPORTED_RPC`“), `referenz-konfiguration.md` (Zeile „mehrere RPCs in einer Nachricht“) und die KDoc von `RpcParser` und `RequestLoop` sagen den Ist-Zustand des Codes und stimmen weiter; sie nennen die Abweichung zum echten Server nicht und führen die Grenzen 1024 und 4096 nicht. Das ist mit `AGENTS.md` §5 Regel 5 vereinbar (Ist-Zustand), bis der Nutzer entschieden hat; nach einer Entscheidung „Code angleichen“ oder „Zielbild anpassen“ braucht jede dieser Stellen eine Änderung (Skill „Fortschreibung lässt die zweite Fundstelle stehen“). | `AGENTS.md` §5 Regel 5 | `docs/user/einbettung-und-betrieb.md` · „ergeben `TDS_UNSUPPORTED_RPC`“ | nein | zweite Fundstellen bei Folgeentscheidung |
| F-9 | INFO | `referenztest-dotnet/Vergleich/MessungWireTests.cs` wird von keinem Gate gebaut (Stage `test` kopiert `Smoke` und `Mitschnitt`, die Stage `vergleich` nur der Messlauf). Ein Übersetzungsfehler dort fiele erst im nächsten Messlauf auf; die Szenarien liefen in diesem Slice am 2026-11-17. Gilt für die ganze Reihe seit ihrer Einführung, kein neuer Befund. | Maintainability | `referenztest-dotnet/Dockerfile` · `COPY Smoke Smoke` / `COPY Mitschnitt Mitschnitt` | nein | Erzeuger ohne Gate-Bau |
| F-10 | INFO | Ablage im Repo: 10 Dateien je Instanz, zusammen rund 1 MB, davon vier Dateien mit 137 bis 339 KB (Grenzmessungen); passt zur Konvention (die 26 älteren Dateien liegen ebenfalls im Repo), das Protokoll nennt die Größen. Die Zahl 36 steht an vier Orten (Test, Protokoll, `vergleichslauf-d.mk`, Messprotokoll-Kopf); der Test `Die_Ablage_hat_36_Messungen_…` ist der Sensor für die Ablage, Protokoll und `.mk` sind Kommentar-Kopplung ohne Sensor. | `AGENTS.md` §3.7 (Kopplung) | `tools/mk/vergleichslauf-d.mk` · „36 Szenarien je Instanz“ | ja — `make referenztest-dotnet` | — |

## Prüfpunkte der Anfrage

1. **Aussagen gegen Ablage** — 14 Zeilen nachgerechnet (Tabelle oben, mehr als die verlangten zehn): alle stimmen mit den Bytes überein. Bedingungen (ein Host, ein Client in einer Version, `sp_executesql`/`INSERT`/`SELECT`/`DECLARE`, 2022 und 2025 je eine Aufnahme) stehen im Protokoll in den Zeilen „Bedingungen“ und „Grenzen“ und in den Tabellenzellen (`Einzelmessung; beide Instanzen gleich`, „in den drei Formen“, „oberhalb von 1025 … nicht gemessen“); in `SPEC-030` stehen sie im einleitenden Klammertext, bei W-19 fehlt die Bedingung im Satz (F-2). W-16 ist sauber nach Übersetzungsfehler (208, `0x83`) und Laufzeitfehler (8134, Bit am DONEINPROC, DONEPROC `0x81`) getrennt; die Satzeinleitung in `SPEC-030` („DONEPROC mit `ERROR` eines Fehlers vor dem ersten Ergebnis“) nennt den zweiten Fall erst in der Klammer, trägt ihn aber richtig.
2. **Abweichungen von [ADR-0051](../plan/adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md)** — siehe F-7; keine Änderung an der ADR, kein stilles Anpassen. Dass `SPEC-030` den Wortlaut „abgelehnt mit `SPEC-013`“ stehen lässt und den Widerspruch daneben nennt, ist hier die ehrliche Form. Offen bleibt die Lücke des Typs `ntext` (F-1).
3. **Code** — `Stapel` und `Einfuegen` (mit `weiter` und `vorbereiten`) lesen die Mengen und Zeilen in der Reihenfolge des Lesens, fangen `SqlException` mit allen Nummern, die Beobachtungszeilen tragen die Ausnahme mit Ort. `MessAuswertung` zerlegt `ntext` (Höchstlänge 4, Kollation 5, Länge 4, Daten); die Mutation macht den 4086/4206-Test rot. Vier Mutationen rot (Tabelle oben). Der Unterschied Einzel/Mehrfach im RETURNVALUE-Test ist begründet (F-5).
4. **Plan-Tabelle §3 gegen den Diff** — jede berührte Datei steht in der Tabelle (die Zeilen für `spec-abdeckung.md` und `tools/vergleichslauf-d.sh` sind im Diff ergänzt); `tools/vergleichslauf-d.sh` ist wie angegeben unberührt. Berührte Spec-Stelle ist `SPEC-030`; `SPEC-045` steht im Kopf des Plans und ist über die `@spec`-Tags berührt, nicht im Text. Keine Haken in der DoD gesetzt. Kein Befund.
5. **`ntext`** — als beobachtete Tatsache geführt (Zeile „gesehen“, Test `[1]- ntext`), die Schwelle ausdrücklich „nicht gemessen“. Eine Behauptung über den Server steht nicht. Die Lücke ist die Folge für `SPEC-003` (F-1), nicht die Formulierung des Hinweises.
6. **Harness** — `harness/README.md` nennt keine Szenario-Zahl der Reihe `wire`; `vergleichslauf-d.mk` ist nachgeführt; `tools/stempel` unverändert, `make gate-zuordnung` Exit 0; `docs/plan/spec-abdeckung.md` geändert (`SPEC-045` von 100 auf 112 Tests, zwölf neue Testmethoden), gehört zur Plan-Tabelle.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `referenztest-dotnet/Mitschnitt/messung/2022/` und `2025/` (20 neue Dateien) | geprüft, ohne Befund (Bytes nachgerechnet, 2025 gegen 2022; Redaktion durch die vorhandenen Belegtests, kein Kennwort, kein Containername in den neuen Dateien — Test `Jede_Datei_…` unverändert grün) |
| `referenztest-dotnet/Mitschnitt/MessAuswertung.cs` | geprüft, ohne Befund (ntext-Zweig, vier Mutationen siehe oben) |
| `referenztest-dotnet/Mitschnitt/MessungBelegTests.cs` | geprüft, Befunde F-3 bis F-5 |
| `referenztest-dotnet/Vergleich/MessungWireTests.cs` | geprüft, ohne Befund an der Logik (jede Tabelle des Szenarios eigen, `Stapel` zählt Mengen und Zeilen, keine Suppression); Hinweis F-9 |
| `docs/messungen/referenzabgleich.md` | geprüft, Befund F-2, F-6 |
| `spec/spezifikation.md` (`SPEC-030`, Änderungsprotokoll) | geprüft, Befunde F-1, F-2 |
| `docs/plan/kompatibilitaetsmatrix.md` | geprüft, ohne Befund (Widerspruch benannt, Zitate unverändert, `make matrix-zitate` nicht verlangt, weil keine Zitate geändert) |
| `docs/plan/planning/in-progress/slice-mehrfach-rpc-messluecken.md` | geprüft, ohne Befund |
| `docs/plan/adr/` | geprüft, ohne Befund (im Diff unberührt, `AGENTS.md` §3.5) |
| `docs/user/`, KDoc `RpcParser`, `RequestLoop` | geprüft, Hinweis F-8 |
| `tools/mk/vergleichslauf-d.mk`, `harness/README.md`, `tools/stempel`, `docs/plan/spec-abdeckung.md` | geprüft, ohne Befund |
| `src/`, `testserver/` | im Diff nicht berührt |

## Summary

Keine HIGH-Findings. Zwei MEDIUM: `SPEC-003` bleibt bei „kein gemessener Client sendet `ntext`“, obwohl W-22 das Gegenteil an der Ablage zeigt, und die Folge für die Entscheidung des Nutzers zu 4096 Parametern fehlt (F-1); `SPEC-030` führt W-19 ohne die gemessene Bedingung (F-2). Vier LOW/INFO an Test und Doku. Alle nachgerechneten Messaussagen stimmen; vier Mutationen sind rot. Die beiden Abweichungen von [ADR-0051](../plan/adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) sind offen und ehrlich benannt, die Entscheidung bleibt beim Nutzer.

Kein HIGH heißt: nach Behebung von F-1 und F-2 (Doku, `spec/`) ist ein zweiter Review nur nötig, wenn sich dabei Quelltext, Testcode oder Ablagen ändern.

## Re-Review-Pflicht

Eine Nachbesserung, die `MessungBelegTests.cs` (F-3, F-4, F-5, F-6), `MessAuswertung.cs`, `MessungWireTests.cs` oder eine `.messung`-Ablage ändert, braucht einen Re-Review in frischem Kontext (Skill MEDIUM „Nachbesserung mit Code ohne zweiten Review“); reine Doku-Änderungen an `spec/spezifikation.md`, Protokoll und Matrix (F-1, F-2) nicht. Ein Nutzerentscheid „Code angleichen“ (`src/`) ist ein eigener Slice mit eigenem Review.
