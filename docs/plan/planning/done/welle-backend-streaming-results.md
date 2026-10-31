# Welle welle-backend-streaming — Backend-Port, Typabbildung und Resultset-Streaming ohne Vollpufferung — Closure-Notiz

> **Zitier-Form** *(bleibt stehen — Norm, kein Ausfüll-Hinweis).* Dieses
> Artefakt friert ein; was es zitiert, bewegt sich weiter. Deshalb: **Kennung,
> nicht Adresse** — `slice-<Kennung>` statt seines Lifecycle-Pfads, `make <target>`
> statt eines Links auf die Sensor-Datei, eine Baseline-Stelle als
> `v<X.Y.Z>` · `regelwerk/<datei>.md` §<Abschnitt> statt als Link
> (Baseline-Regelwerk `grundlagen-harness-dateien.md` §harness/README.md als
> Einstiegspunkt — beim Ausfüllen mit dem adoptierten Tag schreiben).

**Welle:** welle-backend-streaming
**Abschluss:** 2026-10-31
**Verantwortlich:** pt9912

## Was wurde geliefert?

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — *was gelernt wurde*: geliefert · was
funktionierte · was anders lief. Mit ID-Bezug, wo es einen gibt.

- Ein Backend hinter dem Port ([TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung)) liefert ein Ergebnis als `Flow` von Ereignissen; ein Sequenzierer macht daraus die Antwort-Tokens des Codecs ([TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen)): Metadaten vor der ersten Zeile, Zeilen, Abschlüsse mit `DONE_MORE` aus der Vorschau, ERROR plus Abschluss bei einem Fehler im synchronen Strom, definierter Fehler für nicht abbildbare Typen. Ein Ergebnis oberhalb des gesetzten Heaps eines Testlaufs wird ohne Vollpufferung an eine langsame Senke weitergereicht ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)). Die Aussage steht an einem Test-Backend (Fake), einer Testsenke und den Handvektoren des Codecs; kein Referenzclient, kein Socket, kein echtes Backend (Welle-Datei §6).
- `slice-backend-port-vertrag`: Port als Kotlin-Schnittstelle (`QueryBackend`, `QueryExecution`, sieben `QueryEvent`, `QueryRequest`, Backend-Typmodell `SqlType`), Modelltypen in `domain`, Schnittstellen in `port`; Test-Backend `ScriptedBackend` und abstrakte Vertragssuite `QueryBackendContract`.
- `slice-backend-typabbildung`: Abbildung Backendtyp → TDS-Typ und -Wert (`ColumnMapping`, `ValueMapping`, `DecimalValues`), Validierung der Spaltenliste vor COLMETADATA, `TDS_UNSUPPORTED_TYPE` für nicht abbildbare Typen, Zeitzonen- und Genauigkeitsregeln nach Entscheidung vom 2026-10-30.
- `slice-backend-sequenzierer`: `ResponseSequencer` mit COLMETADATA, ROW, DONE-Familie, `DONE_MORE`-Vorschau, ERROR plus Abschluss, Prüfung der Ereignisreihenfolge (Verstoß endet in `TDS_INTERNAL_ERROR`), `close()` genau einmal; Schnittstelle zum Fehler-Mapping (`ErrorMapping`, `MappedError`, `fateOf`).
- `slice-backend-streaming-nachweis`: eine suspendierende Senke (`ReplyStream`, `MessageStream`), Packet Size der Sitzung am Schreiber, `StreamedResponse` statt `BufferedResponse`, Cancel und Senkenfehler schließen die Ausführung, `OutOfMemoryError` aus Backend und Senke endet in ERROR plus DONE oder schließt ohne Antwort ([ADR-0013](../../adr/0013-backend-grenzen.md)), Fehlerflag im Strom vor dem Paketbau (Review F-1).

**Das Mehr gegenüber den Slice-DoDs (Welle-Datei §3): der Nachweis über die ganze Kette in einem Lauf.** `StreamingSmokeTest` fährt Test-Backend, Port, Typabbildung, Sequenzierer, `TokenEncoder`, Paket-Schreiber und eine langsame Senke zusammen: `Ein Ergebnis ueber dem Heap des Testlaufs wird vollstaendig uebertragen` (1 200 000 Zeilen zu 128 Zeichen bei gesetztem Heap von 128 MiB, zur Laufzeit gegen `Runtime.maxMemory()` geprüft), `Zwei Ergebnisse ergeben Metadaten, Zeilen und die DONE_MORE-Folge` und `Ein Fehler nach Teilzeilen ergibt ERROR und DONE mit Fehlerstatus` (die Senkenausgabe wird vom Test-Decoder des Codecs zurückgelesen). Die Zähler-Schranke (`StreamingDispatchTest`) ist der deterministische Beleg ohne GC-Abhängigkeit: ein haltender Draht begrenzt die vorausgelieferten Ereignisse auf exakt 409 bei Packet Size 520. Alle drei Smoke-Tests liefen im frischen Lauf dieser Closure (Verifikation unten).

Die Aussagen der Welle, in zwei Spalten. *Gemessen* heißt: ein bestandener Test belegt es, am Fake und an einer Testsenke (nie am Referenzclient, nie an einem echten SQL Server, nie am Produkt unter Last); *Festlegung* heißt Entscheidung oder Lesart ohne Messung. Die Tabelle führt die Tabellen der vier Slice-Closures zusammen.

| Spalte | Aussage | Beleg im Repo |
|---|---|---|
| gemessen (Fake, Testsenke) | Ein haltender Draht begrenzt die vorausgelieferten Ereignisse auf exakt 409; nach Freigabe kommen alle Ereignisse und Bytes an | `StreamingDispatchTest`; Mutation M2 rot |
| gemessen (Fake, Testsenke) | Ein Ergebnis über dem gesetzten Heap (128 MiB) wird vollständig übertragen | `StreamingSmokeTest` (Heap-Test); Mutation Mheap rot; Messbedingung `gradle/test-jvm.init.gradle.kts` |
| gemessen (Fake, Testsenke) | Cancel des Collectors und Senkenfehler rufen `close()` und `cancel()` genau einmal; die Sperre der Sitzung bleibt während eines haltenden Schreibvorgangs gehalten; die Packet Size der Sitzung gilt am Schreiber | `StreamingDispatchTest`, `StreamingSmokeTest`; Mutationen M1, M5, M7 rot |
| gemessen (Fake, Testsenke) | `OutOfMemoryError` aus Backend und Senke: ERROR plus DONE bei synchronem Strom, sonst Schließen ohne Antwort (Token-Grenze, mitten im Token, abschließendes DONE); andere `Error` und die Abbruchausnahme werden nicht gefangen | `StreamingDispatchTest`, `MessageStreamTest`; Mutationen M12a, M12c rot, M12b nur laut Implementer |
| gemessen (Fake) | Ereignisreihenfolge: Verstöße enden in `TDS_INTERNAL_ERROR`; ein Abbruch verwirft den zurückgehaltenen Abschluss; `close()` genau einmal in jedem Ausgang, auch bei suspendierendem `close()`; Zufallsfolgen (4000 Läufe) gegen ein getrenntes Modell | `ResponseOrderTest`, `ResponseFailureTest`, `ResponseErrorMapperTest`; Verifikation 20 von 20 Mutanten tot |
| gemessen (Fake) | Die Abbildung weist Typen ohne Abbildung, Precision über 38, Werte mit Zone, zu genaue Werte und NULL in nicht nullbarer Spalte ab, atomar je Spaltenliste und Zeile; Rechenzeit bei extremem Exponent und großer Mantisse begrenzt | `ColumnMappingTest`, `ValueMappingTest`, `DecimalValuesTest`, `MappingFuzzTest`; Mutanten der Nachverifikation 18 von 21 rot, die drei überlebenden laut Fix-Runde inzwischen rot (nicht nachgeprüft) |
| gemessen (Compiler, Gate) | Die Untertyp-Menge von `QueryEvent` ist erschöpfend; `domain` und `port` importieren nur Erlaubtes | `BackendModelTest` (Übersetzungszeit), `make detekt` (`ForbiddenImport/port`) |
| Handvektor, Selbstkonsistenz | Token-Größen COLMETADATA 13, ROW 5, DONE 13 Byte und 13 + 407 · 5 = 2048; Bytes der Antworten und der Werte; Roundtrip gegen den Test-Decoder (belegt Übereinstimmung mit dem Encoder, keine Protokolltreue) | `ValueMappingTest`, `ResponseMappingTest`, `ResponseDecoderTest`, `StreamingSmokeTest` |
| Festlegung (Nutzerentscheidung 2026-10-30) | Ort der Typen (`domain` / `port`, im Slice als „Aufrufer“ geführt); `Failure` im Strom statt Ausnahme bei `execute()`; `Failure` zählt als nächstes Ereignis der Vorschau; Abschluss bei Cancel oder Disconnect verworfen; Reihenfolgeverstoß ist ein Fall von `SPEC-048`; `ReturnStatus` und `OutputParameter` abgelehnt; Zonen- und Genauigkeitsregeln; Precision über 38 in der Abbildung | `slice-backend-port-vertrag` §6, `slice-backend-sequenzierer` §6, `slice-backend-typabbildung` §6; KDoc, `SPEC-002`, `SPEC-003`, `SPEC-031` |
| Festlegung (vom Nutzer nicht bestätigt) | Lesart der Decimal-Entscheidung (nachlaufende Nullen zählen nicht; Mantisse über 1000 Stellen abgewiesen); 100-ns-Raster; „synchron“ heißt, der Transport hat jedes bisher erzeugte Token angenommen; Puffer ein Paket plus Paket im Schreiben plus ein Token, keine Byte-Zahl; Zähler-Schranke 409 als Schranke des Slice; Grenze für „kleine“ Ergebnisse (keine Grenze, Setzung der ADR) | KDoc, `SPEC-003`, `SPEC-022`, `SPEC-031`, [ADR-0013](../../adr/0013-backend-grenzen.md) Entscheidung 5; Register `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` |
| nicht gemessen | Annahme der Tokenfolge, der `DONE_MORE`-Bits und der Spaltenmetadaten durch `Microsoft.Data.SqlClient` und das Verhalten eines echten SQL Servers; Rückstau und Heap im Produktbetrieb unter Last; Verzögerung durch den zurückgehaltenen Abschluss; Socket und Kanal; ein blockierendes Backend und die Durchsetzung der Frist; ATTENTION während des Streamens; Hängen von Coroutinen nach dem Fang des `OutOfMemoryError`; die Obergrenze eines einzelnen Tokens; Kollation am Produkt; `curCmd` | Träger in der Tabelle unten; Register |

**Nutzerentscheidungen aus den Slice-Risiken (Welle-Datei §3), gegen die Slice-Notizen geprüft, benannt und nicht ergänzt:**

- *Ort der Typen in `domain` oder `port`:* festgehalten in `slice-backend-port-vertrag` §6 als „Entschieden (Aufrufer, 2026-10-30; Setzung, nicht gemessen)“ — Modelltypen in `domain`, `QueryBackend` und `QueryExecution` in `port`, Herleitung aus `ARC-001`, `ARC-006` und der Importregel. Der Slice nennt den Entscheider „Aufrufer“, nicht „Nutzer“; ob beide dieselbe Person sind, steht in keiner Notiz. Umgesetzt, vom Schichtwächter (`ForbiddenImport`) gehalten.
- *Abbildung des `Failure`-Ereignisses auf Fehlernummer und Severity:* **nicht als Nutzerentscheidung dieser Welle festgehalten.** `slice-backend-sequenzierer` §6 führt „Herkunft des Fehler-Mappings“ als Planungs-Lesart („keine Entscheidung“); der Ausgang ist „eingetreten“ (die Schnittstelle mit Stellvertreter trug nicht, umgebaut in der Fix-Runde). Die Zuordnung selbst liegt bei `slice-fehler-mapping` der Welle `welle-fehler-abbruch` (`done/`): dort ist die Gruppeneinteilung (Class bis 19 offen; `TDS_PROTOCOL_ERROR` und `TDS_INTERNAL_ERROR` mit Class 20) Nutzerentscheidung vom 2026-10-30, die Einzelzuordnung (Class 20 für `TDS_UNSUPPORTED_TYPE`, `TDS_SIM_UNKNOWN_OBJECT`, `TDS_TIMEOUT`; Nummern 50001 bis 50008; Texte) „Festlegung des Slice, vom Nutzer nicht bestätigt“. Die Statement-gegen-batch-Unterscheidung ist weder entschieden noch gebaut (unten).
- *Grenze für „kleine“ Ergebnisse:* festgehalten in `slice-backend-streaming-nachweis` §6: entschieden in [ADR-0013](../../adr/0013-backend-grenzen.md) als Setzung („Voreinstellung, vom Nutzer nicht ausdrücklich gewählt“): keine Grenze im Vertrag, Pflichttext für materialisierende Backends. Der Nachweis misst sie nicht.

Das Kriterium der Welle-Datei („getroffen und in den Notizen der Slices festgehalten“) ist damit für den Ort der Typen und, als Setzung, für die Grenze erfüllt; für die Abbildung des `Failure` ist es nur für die Gruppen erfüllt, die Einzelzuordnung bleibt unbestätigt (Register `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`, `SPEC-011` bis `SPEC-016` als „Vorschlag“).

**Jeder offene Punkt aus dem Abschnitt „Offen“ von [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) hat einen Slice und einen Ausgang** (Prüfung am 2026-10-31 gegen die Dateien im Planning-Lifecycle und die ADRs):

| Offener Punkt | Slice | Ausgang |
|---|---|---|
| `Failure` als „nächstes Ereignis“ der `DONE_MORE`-Vorschau | `slice-backend-sequenzierer` | entschieden mit Beleg: Nutzerentscheidung 1 vom 2026-10-30; Test „Failure nach ResultEnd gibt dem zurueckgehaltenen Abschluss DONE_MORE“, Mutant V14 tot; am Produkt nicht gemessen (Register `BEO-TDS/messung-am-echten-sql-server-fehlt`, Träger `slice-qa-vergleichslauf-d`) |
| Schicksal des zurückgehaltenen Abschlusses bei Cancel oder Disconnect | `slice-backend-sequenzierer` | entschieden mit Beleg: verworfen (Nutzerentscheidung 2); Test „Abbruch verwirft den zurueckgehaltenen Abschluss“, Mutant V01 tot. Die Cancel-Seite (Bestätigung) übergeben an `slice-attention-cancel-timeout`, der Disconnect an `slice-abbruch-ressourcen-shutdown` (beide `open/`, Welle `welle-fehler-abbruch`) |
| Prüfung der Ereignisreihenfolge | `slice-backend-sequenzierer` | entschieden mit Beleg: Fall von `SPEC-048` (Nutzerentscheidung 3); Test „Reihenfolgeverstoss endet in TDS_INTERNAL_ERROR“ (sieben Verstöße), Mutant V10 tot |
| statement-abbrechende (R04) gegen batch-abbrechende Fehler (R05 bis R07, [ADR-0007](../../adr/0007-fehler-und-attention.md)), von `Failure` nicht getragen | `slice-backend-sequenzierer` | **ausdrücklich weiter offen, ohne Slice als Träger:** der Sequenzierer schreibt für jeden Fehler eine Variante und beendet den Lauf; die Lücke steht als benannte Spec-Lücke in der Spezifikation (§4 „Offen“, Backend-Vertrag), die Entscheidung liegt beim Nutzer; `slice-fehler-zustandsuebergang` (`done/`) hat sie aufgenommen und mit „weiter offen“ geschlossen |
| Grenze für „kleine“ Ergebnisse | `slice-adr-backend-grenzen` (ohne Welle), umgesetzt im Pflichttext des Nachweis-Slice | entschieden mit Beleg als Setzung: [ADR-0013](../../adr/0013-backend-grenzen.md) Entscheidung 5, `Accepted`; KDoc von `QueryExecution` nachgezogen; der Nachweis misst sie nicht |
| Zeilendarstellung und Heap-Grenze eines materialisierenden Backends | `slice-adr-backend-grenzen`, `slice-backend-typabbildung` | entschieden mit Beleg: [ADR-0013](../../adr/0013-backend-grenzen.md) Entscheidung 1 (`List<Any?>` mit festgeschriebenen Klassen je Spaltenart); `ValueMapping` nimmt nur diese Klassen an, jede andere ist Typkonflikt, `ValueMappingTest`; die Heap-Grenze löst der Strom mit Rückstau (`StreamingSmokeTest`) |
| Behandlung von `OutOfMemoryError` | `slice-adr-backend-grenzen` (Entscheidung), `slice-backend-streaming-nachweis` (Bau) | entschieden mit Beleg: [ADR-0013](../../adr/0013-backend-grenzen.md) Entscheidung 2; Fang gebaut und mit einem Testdouble belegt (Token-Grenze, mitten im Token, abschließendes DONE; V-1 behoben); das akzeptierte Negativ der ADR (Hänger nach dem Fang im Spike) ist nicht widerlegt und nicht reproduziert |
| Abbruch und Thread-Verbrauch eines blockierenden Backends | `slice-adr-backend-grenzen` | entschieden: Vertragspflicht „suspendierend“ ([ADR-0013](../../adr/0013-backend-grenzen.md) Entscheidung 3); **kein Wächter** (ein blockierender Aufruf ist nicht maschinell prüfbar); der Hilfsadapter ist ein benannter Folgeschritt ohne Träger-Datei, ausgelöst durch den Re-Evaluierungs-Trigger „echtes Backend, dessen Treiber blockiert“ |
| Semantik der Frist `deadlineEpochMillis` | `slice-adr-backend-grenzen` (Entscheidung), `slice-attention-cancel-timeout` (Bau) | an eine andere Welle übergeben: [ADR-0013](../../adr/0013-backend-grenzen.md) Entscheidung 4 (der Kern setzt durch, Ablauf endet in `TDS_TIMEOUT`); Träger `slice-attention-cancel-timeout` (`open/`, Welle `welle-fehler-abbruch`), dessen Tabelle den `RequestLoop`-Umbau nennt. Diese Welle übernimmt das Feld unverändert und setzt nichts durch |
| Verzögerung durch den zurückgehaltenen Abschluss | `slice-backend-streaming-nachweis` (§6) | **weiter offen, Träger unvollständig:** am Fake nur als Ereigniszahl messbar, nicht als Latenz. Als Träger ist `slice-qa-performance` (`open/`) benannt; sie führt Rückstau, Heap und den zurückgehaltenen Abschluss weder in der DoD noch im Risiko (Prüfung am 2026-10-31). Geführt in `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` (5×, `offen`, Entscheidung beim Nutzer) |

## Was hat funktioniert?

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3.

- Der Schnitt trug in allen vier Slices; keiner brauchte die Rückführung nach Welle-Datei §4 für seinen Zuschnitt. Port-Vertrag vor Typabbildung und Sequenzierer, der Nachweis zuletzt: jeder Slice konnte auf dem Stand des vorigen starten.
- Die Prüfung durch Mutation fand, was Lesen und grüne Tests nicht fanden, in allen vier Slices (15, 21 plus 21 und 20 Mutationen der Verifikationen des Port-, Abbildungs- und Sequenzierer-Slice, beim Nachweis M1 bis M12c, Mheap und Packet-ID): Rechenzeit vor der Grenzprüfung, ein Abbruchschutz ohne suspendierendes Gegenstück, eine Fehlerantwort hinter einem halben Token.
- Der Sequenzierer ist ohne Sitzung und ohne Transport testbar (die Schichtregel hält `make detekt`); die `DONE_MORE`-Vorschau ist über eine Steuerung ohne Wanduhr geprüft.
- Die Zähler-Schranke (exakt 409) ist ein deterministischer Beleg für „keine Vollpufferung“; der Heap-Test ist nur der Zusatz und flackerte in keinem Lauf.
- Die abstrakte Vertragssuite `QueryBackendContract` (das Backend ist Parameter) gibt dem Simulations-Backend ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)) ein Prüfwerkzeug: ein künftiges Backend erbt sie.
- Der Entscheidungs-Slice `slice-adr-backend-grenzen` lief ohne Welle neben der Welle und schloss fünf offene Punkte der ADR, bevor der Nachweis-Slice Code darauf baute; der KDoc-Nachzug folgte der Annahme als eigener Commit.

## Was ging anders als geplant?

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — jede Zeile moeglichst mit der Konsequenz,
die daraus schon gezogen wurde (Folge-Slice, Spec-Version).

- Die Schnittstelle zum Fehler-Mapping mit Stellvertreter trug nicht (Review F-1 von `slice-backend-sequenzierer`); sie wurde in der Fix-Runde umgebaut. Der Rest, ein Leser von `ErrorMapper.sequencerMapping()` und `fateOf` in `src/main`, ging an `slice-sql-batch-dispatch` und `slice-fehler-zustandsuebergang` (beide `done/`). Register: `BEO-TDS/folge-einer-festlegung-im-nachbar-slice-unbenannt` (2×).
- Der Review von `slice-backend-streaming-nachweis` fand eine Fehlerantwort hinter einem halben Token (F-1); die Lösung (Fehlerflag im Strom vor dem Paketbau) stand nicht im Plan. Register: `BEO-TDS/fehlerantwort-hinter-halbem-token` (1×).
- Der Encoder skalierte vor der Grenzprüfung (`setScale`, `stripTrailingZeros`); die Rechenzeit wuchs mit dem Wert (1330 ms bis über 20 s). Behoben in der Fix-Runde von `slice-backend-typabbildung`. Register: `BEO-TDS/normalisierung-vor-grenzpruefung-bindet-rechenzeit` (1×).
- Der Vertragstest des Port-Slice belegte zunächst nur das Test-Backend (vier Tests gaben das Skript des Fakes zurück); behoben durch die abstrakte Suite. Register: `BEO-TDS/test-belegt-nur-den-fake` (3×, auch im Nachweis-Slice).
- Mehrere Fix-Runden haben kein Verifier nachgeprüft: die zweite Runde `48e7154` der Typabbildung, die Restrunde (`e9ce7af`, `55278d5`, `d827919`) des Sequenzierers und die Mini-Runde `0af01b5` des Nachweises (Mutante M12b nur laut Implementer). Die Angaben stehen in den Slice-Notizen als solche.
- Spec, Vertrag und KDoc waren nach Entscheidungen mehrfach falsch oder zu breit (Port-Vertrag M1, V1; Typabbildung F-4, V-1; Nachweis F-2, F-3). Das ist `BEO-TDS/aussagen-ueber-messung-hinaus`, verkörpert in `AGENTS.md` §3.8; die Regel war der Maßstab der Befunde, die Wiederkehr nach der Verkörperung ist belegt (keine Schärfung).
- Der Planungsstand der Welle-Datei: die Zuordnung der Streaming-Aussage zu [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) und [TDS-QA-04](../../../../spec/lastenheft.md#tds-qa-04--performance) statt [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilit%C3%A4t) (Welle-Datei §1, „offene Frage 1“) hat der Nutzer in keiner Notiz bestätigt. Die Slices `slice-backend-sequenzierer` und `slice-backend-streaming-nachweis` führen [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilit%C3%A4t) im Bezug für die Seite „abgebrochener oder unzulässiger Strom beendet den Prozess nicht“; die Zuordnung bleibt eine Lesart des Planers.

## Steering-Loop-Einträge

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 (hier stehen **nur** Beobachtungen, die im
Register 3× erreicht haben; jeder Eintrag nennt seine `BEO-<KUERZEL>/<slug>`) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln (Feld
und Zielort auf **einer** Zeile, Sektionsangabe innerhalb der Backticks).

**Lese-Schritt des Registers (2026-10-31).** Das Register führt 45 Beobachtungen, jede mit nicht leerem `evidence/` (gegen das Dateisystem geprüft); 18 haben mindestens drei Evidence-Dateien. Neun davon tragen Evidence aus den vier Slices dieser Welle. Drei dieser neun haben schon einen Ausgang, sechs stehen über der Schwelle ohne Ausgang. Die übrigen neun Beobachtungen über der Schwelle haben keine Evidence aus dieser Welle und bleiben unverändert (`BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` und `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` mit Ausgang `geplant`, `BEO-TDS/test-stage-cache-verdeckt-wiederholung` `verkörpert`; ohne Ausgang `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung`, `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten`, `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler`, `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`, `BEO-TDS/dod-satz-verlangt-ablage-die-der-slice-ersetzt`, `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`).

*Schon mit Ausgang* (der Lese-Schritt bestätigt den Stand):

- `BEO-TDS/aussagen-ueber-messung-hinaus` (33×, alle vier Slices): `verkörpert` in `AGENTS.md` §3.8 und im Reviewer-Skill, Anker `seit welle-protokollcodec`. In allen vier Slices als Finding wieder aufgetreten und behoben (Abschnittsnummern ohne Quelle, Spec nach Entscheidung nicht nachgeführt, KDoc und Spec breiter als der Test, Etikett Handvektor bei Encoder-Herkunft); keine Schärfung.
- `BEO-TDS/offene-punkte-ohne-traeger` (16×, `slice-backend-port-vertrag` in dieser Welle): `verkörpert` in `.claude/commands/implement-slice.md` Schritt 24 für Entscheidungs-Slices. Im Port-Slice, einem Implementierungs-Slice, fand die Träger-Prüfung drei Punkte ohne Träger (Decimal-Obergrenze, Zeit-Scale und Textlänge, JVM-Typen je `SqlType`) und führte sie in `slice-backend-typabbildung` §6. Ob der Absatz auf Implementierungs-Slices ausgedehnt wird, ist seit der Closure von `slice-auth-adapter-benutzerliste` eine offene Frage an den Nutzer, die diese Closure nicht entscheidet.
- `BEO-TDS/messung-am-echten-sql-server-fehlt` (19×, drei Slices dieser Welle): `geplant`, Träger `slice-qa-vergleichslauf-d` (`open/`).

*Über der Schwelle, nicht verkörpert, Entscheidung beim Nutzer.* Diese Closure verkörpert keine dieser Regeln und ändert weder `AGENTS.md` noch ein Gate, einen Skill oder eine Adaption: eine neue Regel oder ein neuer Sensor ist eine Entscheidung des Nutzers. Jeder Eintrag steht mit Vorschlag im Bericht der Closure; sein `state.md` trägt den Stand `offen` mit dem Vermerk dieser Closure. Es gibt kein `liegt in`, die Paarung (a) entfällt.

- `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` — 9×, aus dieser Welle `slice-backend-typabbildung` (Decimal-Lesart, 100-ns-Raster) und `slice-backend-sequenzierer` (Definition von „synchron“ als Festlegung des Implementers). Die Slice-Closure des Sequenzierers hatte den Lese-Schritt für diese Welle angekündigt.
- `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` — 8×, aus dieser Welle `slice-backend-sequenzierer` (`NonCancellable` um `close()` nur mit nicht suspendierendem Gegenstück belegt) und `slice-backend-streaming-nachweis` (V-1 Fang beim abschließenden DONE, F-6 Bereich statt exakter Zahl, F-7).
- `BEO-TDS/strukturtest-ohne-variation-der-eingabe` — 6×, aus dieser Welle `slice-backend-typabbildung` (Fehlertexte an vier statt allen Pfaden, Zonentest unter der Zone des Containers).
- `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` — 5×, aus dieser Welle `slice-backend-streaming-nachweis`. Der Träger `slice-qa-performance` führt Rückstau, Heap und den zurückgehaltenen Abschluss nicht (siehe Tabelle zu [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)); Entscheidung beim Nutzer, ob die DoD dieses Slice ergänzt wird.
- `BEO-TDS/risikozeile-nennt-behobenen-zustand` — 4×, aus dieser Welle `slice-backend-streaming-nachweis` (F-9).
- `BEO-TDS/test-belegt-nur-den-fake` — 3×, aus dieser Welle `slice-backend-port-vertrag` und `slice-backend-streaming-nachweis` (das dritte Auftreten liegt in `slice-transport-listener-lebenszyklus`). Verwandt, anderer Mechanismus: `BEO-TDS/strukturtest-ohne-variation-der-eingabe`.

**Spec-Lücken, benannt und offen** (kein `liegt in`, weil in einer Spec und nicht an einem Zielort verkörpert):

- Der Speicher des Schreibers hat keine Zahl, weil das Stück des Aufrufers ein ganzes Token ist (`SPEC-022`: „ein Token“, „eine Byte-Zahl ist nicht gemessen“); die Zähler-Schranke belegt nur Tokens der Größe einer Int-Zeile. Verankert wird sie, wenn Spec oder ADR eine Obergrenze je Token setzen oder `slice-codec-datentypen-erweiterung` (lange Werte) sie trägt.
- `SPEC-002` und `SPEC-003` lassen die Genauigkeit der Zeittypen, die Zeitzonenannahme und die Länge von `UnicodeText` offen; der Quelltext setzt Scale 7 und Länge bis 4000 als Festlegung. `SPEC-003` schweigt zu NULL in nicht nullbarer Spalte und zu leeren oder doppelten Spaltennamen; der Slice legt sie fest.
- Die Statement-gegen-batch-Unterscheidung und `curCmd` der DONE-Tokens (`SPEC-031`: der Sequenzierer schreibt 0, der Port trägt keine Kennung) stehen als offene Punkte des Backend-Vertrags in der Spezifikation; kein Slice trägt sie.

## Beobachtungs-Register (Zeiger)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register — der Zähler wird **nicht** hier gepflegt; diese
Sektion ist ein Zeiger und trägt keine Daten.

Der Zähler steht in [`../observations/`](../observations/).
Was in dieser Welle **3×** erreicht hat, steht oben unter
*Steering-Loop-Einträge*.

## Folge-Slices

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — **derivativ**: Diese Liste zeigt nur,
das Original ist die Slice-Datei. Jeder genannte Folge-Slice muss als Datei im
Planning-Lifecycle existieren; genannt ohne angelegt ist dieselbe Klasse wie
ein halluziniertes Gate.

Keine neuen Slices durch diese Closure. Die Träger der offenen Punkte existieren als Dateien:

- `slice-attention-cancel-timeout` (Cancel-Seite des zurückgehaltenen Abschlusses, Frist `deadlineEpochMillis`, Class von `TDS_TIMEOUT`), `slice-abbruch-ressourcen-shutdown` (Disconnect während der Anfrage, Dauer eines haltenden Schreibvorgangs) — `open/`.
- `slice-qa-vergleichslauf-d`, `slice-qa-kompatibilitaet` (Referenzclient, echter SQL Server, Token-Folge, Kollation) — `open/`.
- `slice-qa-performance` (Rückstau und Heap unter Last; Träger unvollständig, siehe oben) — `open/`.
- `slice-codec-datentypen-erweiterung` (NBCROW, PLP, Typen außerhalb des Mindestkatalogs), `slice-antwort-mehrteilig-output` (Abbildung von `ReturnStatus` und `OutputParameter`) — `open/`.
- `welle-simulations-backend` mit `slice-sim-auswahl-streaming` (implementiert den Port, Boundary-Kriterium von [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)) — flach bzw. `open/`.
- `slice-sql-batch-dispatch`, `slice-fehler-zustandsuebergang`, `slice-adr-backend-grenzen` — `done/`.

## Verifikation

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 1 — keine Behauptung ohne nachprüfbaren
Anker (Hash, Lauf, Zahl).

- Alle Slices der Welle in `done/`: `slice-backend-port-vertrag`, `slice-backend-typabbildung`, `slice-backend-sequenzierer`, `slice-backend-streaming-nachweis` (Dateien in `done/` gegen das Dateisystem geprüft; in allen vier ist kein DoD-Punkt offen, `grep -c "- \[ \]"` ergibt je 0; `Welle:` nennt in allen vier `welle-backend-streaming`).
- Welle-Smoke `StreamingSmokeTest`: im frischen Lauf unten drei Tests `PASSED` (Fehler nach Teilzeilen, zwei Ergebnisse mit `DONE_MORE`-Folge, Ergebnis über dem Heap des Testlaufs).
- Frischer Testlauf ohne Cache (`docker build --no-cache-filter test --target test --progress=plain .` auf dem Stand `ab4b000`, einmal, Arbeitsbaum bis auf diese Notiz sauber): Exit 0, `BUILD SUCCESSFUL in 1m 19s`, 654 Tests `PASSED`, kein Test `FAILED`; die Stage `test` lief, kein Cache-Treffer.
- `make gates` Exit 0 am 2026-10-31 auf dem Stand des Self-Close-Commits (`baseline-verify: v6.18.1 OK — 54 Dateien`; `make docs-check` ohne Befund).
- Trigger-Audit, je Klasse:
  - **Carveouts:** zwei aktiv, 0 fällig. [CO-001](../../carveouts/CO-001-nuget-audit-aus.md) und [CO-002](../../carveouts/CO-002-nuget-signaturpruefung-fehlt.md) betreffen die .NET-Referenztests; ihr Auflösungs-Trigger (Folge-Slice `slice-qa-dotnet-lieferkette-haerten` in `done/`) ist nicht eingetreten, der Folge-Slice liegt in `open/`, die Welle berührt die .NET-Referenztests nicht. Diese Welle hat keinen Carveout eröffnet, und sie schließt mit keinem stillen roten Gate.
  - **Bootstrap-aware Gates:** 0 offen. Die Tabelle *Sensors* in `harness/README.md` führt kein Target mit Schwelle oder Reifestufe; `make test`, `make detekt` und `make smoke` tragen keine Stufe.
  - **ADR-Re-Evaluierungs-Trigger** der von der Welle berührten, angenommenen ADRs — 0 fällig. [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md): Backpressure-Probleme oder zusätzliche Ergebnisarten: nein (die Zähler-Schranke und der Heap-Test bestehen; der Port führt die sieben Ereignisse der Spezifikation, keine weitere Art); ein echtes Backend mit nicht unterbrechbarem Treiber: es gibt keines; eine Messung am echten SQL Server, die der Vorschau für `DONE_MORE` widerspricht: nicht gefahren, Wächter `slice-qa-vergleichslauf-d`. [ADR-0013](../../adr/0013-backend-grenzen.md): ein echtes Backend mit blockierendem Treiber: nein (der Hilfsadapter hat damit keinen Auslöser); ein Lauf, der die Hänger nach dem Fang des `OutOfMemoryError` erklärt, widerlegt oder häufiger zeigt: nein, der Test löst den Fehler mit einem Testdouble aus und ist dafür nicht gebaut; Zeilen als Spaltenblöcke als Heap-Bestimmer: nein (der Strom bestimmt die Grenze, `StreamingSmokeTest`); ein Client mit Timeout ohne ATTENTION: nicht gemessen; Nutzerentscheidung, das Fangen aufzugeben: nein (keine Notiz nennt sie). [ADR-0007](../../adr/0007-fehler-und-attention.md) dient der Welle als Messquelle (Reihen R04 bis R10), ihre Trigger (Recovery-Lücke, Pool-Client, Messung am Server dieses Repos oder mit TDS 8.0) sind nicht eingetreten und gehören `welle-fehler-abbruch`.
- Zuordnung *gemessen | Festlegung* und Träger je offenem Punkt von [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md): siehe oben (Closure-Trigger der Welle-Datei).
- Drei Paarungen: (a) Anker — kein Eintrag dieser Closure trägt `liegt in`, die Paarung entfällt. (b) Folge-Slice — alle unter *Folge-Slices* genannten Dateien existieren im Planning-Lifecycle (`open/`, `done/` bzw. flach), geprüft gegen das Dateisystem am 2026-10-31; die Ausnahme ist benannt: `slice-qa-performance` führt den Punkt „zurückgehaltener Abschluss“ nicht, und der Hilfsadapter und die Statement-gegen-batch-Unterscheidung haben keinen Slice. (c) Register — alle genannten `BEO-TDS/<slug>` existieren als Verzeichnis mit nicht leerem `evidence/` (gegen das Dateisystem geprüft).
