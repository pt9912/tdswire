# Welle welle-simulations-backend — Mitgeliefertes Simulations-Backend: Beschreibung, Erkennung, Auswahl mit Streaming und Änderungsweitergabe — Closure-Notiz

> **Zitier-Form** *(bleibt stehen — Norm, kein Ausfüll-Hinweis).* Dieses
> Artefakt friert ein; was es zitiert, bewegt sich weiter. Deshalb: **Kennung,
> nicht Adresse** — `slice-<Kennung>` statt seines Lifecycle-Pfads, `make <target>`
> statt eines Links auf die Sensor-Datei, eine Baseline-Stelle als
> `v<X.Y.Z>` · `regelwerk/<datei>.md` §<Abschnitt> statt als Link
> (Baseline-Regelwerk `grundlagen-harness-dateien.md` §harness/README.md als
> Einstiegspunkt — beim Ausfüllen mit dem adoptierten Tag schreiben).

**Welle:** welle-simulations-backend
**Abschluss:** 2026-10-31
**Verantwortlich:** pt9912

## Was wurde geliefert?

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — *was gelernt wurde*: geliefert · was
funktionierte · was anders lief. Mit ID-Bezug, wo es einen gibt.

- Ein mitgeliefertes Simulations-Backend ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)) beantwortet über den Port des Backend-Vertrags ([TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung)) die erkannten Anweisungsformen von SPEC-036 aus einer deklarativ beschriebenen Tabelle (SPEC-035), ohne SQL auszuwerten und ohne Daten zu speichern. Die Beschreibung liegt als Kotlin-Schnittstelle und als YAML-Datei vor (Umgebungsvariable `SIMULATION_DESCRIPTION_FILE`); eine Zeilenquelle oberhalb der Speichergrenze wird schrittweise übertragen; Änderungsanweisungen gehen an Callbacks des Integrators. Der Protokollkern läuft unverändert ohne das Backend.
- `slice-adr-simulation-formen`: die Entscheidung als [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) (`Accepted`), SPEC-035 und SPEC-036 fortgeschrieben, Lastenheft 0.5.0 um `IS [NOT] NULL` ergänzt (Entscheidung des Nutzers); Zeichenscanner statt Regex, YAML mit Jackson 2.21.7, neutrales Schema-Modell von d-migrate.
- `slice-sim-erkennung`: Tabellenbeschreibung und Leser, Zeichenscanner und Zuordnung der Formen S1, W1 bis W3, Objektprüfung vor dem Lesen (`TDS_UNSUPPORTED_SQL`, `TDS_SIM_UNKNOWN_OBJECT`).
- `slice-sim-auswahl-streaming`: Auswahl mit Bedingung und Parametern (Überführung vor dem Öffnen der Quelle, `TDS_SIM_INVALID_VALUE`), Lesen der Zeilenquelle auf Abnahme mit genau einem `close()`, die Suite `QueryBackendContract` besteht am Simulations-Backend wie am Test-Backend, Heap-Nachweis `SimulationMemoryTest`.
- `slice-sim-aenderung-integration`: Änderungsweitergabe (W1 bis W3) an Callbacks, Verdrahtung in `runtime` (Datei, Größengrenze 1 MiB, Startabbruch vor dem Binden, Wächter `ForbiddenImport/simulation` mit Selbsttest-Fall), Integrationslauf mit dem Referenzclient (SPEC-040) als Stufe 3 von `make e2e` ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)).

**Das Mehr gegenüber den Slice-DoDs (Welle-Datei §3).** Zwei Aussagen, die kein einzelner Slice trägt:

1. *Der Referenzclient liest eine Zeilenquelle oberhalb der Speichergrenze vollständig.* `make e2e` Stufe 3 (`make simulations-integration`): Microsoft.Data.SqlClient 7.1.1, TDS 7.4 ohne Verschlüsselung und ohne Pool, Serverprozess mit `maxHeapSize` 48 MiB, Tabelle `gross` mit 500 000 Zeilen zu 128 Zeichen (64 000 000 Zeichen Text, mindestens 27 % über dem Heap); der Lauf endet mit „Alle 4 Tests des Referenzclients bestanden“. Ein Messpunkt, eine Maschine; der Beleg trägt nicht den Speicher des Clients, nicht den Betrieb unter Last und nicht das Verhalten gegen einen echten SQL Server.
2. *Derselbe Server startet und beantwortet den Login ohne Simulations-Backend.* `make e2e` Stufe 1 (`make smoke`, `main` ohne Beschreibungsdatei, Rohbytes-Login) und der Kotlin-Test des Starts ohne Simulation in `SimulationWiringTest`.

**Carveout.** Die Welle schließt mit dem dokumentierten, aktiven Carveout [CO-004](../../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md): Der Cancel-Teil von SPEC-040 (Cancel mit Folgeabfrage am Referenzclient) hat keinen Test, der Lauf besteht mit vier der fünf Szenarien. Der Server beantwortet ein ATTENTION-Paket während einer Anfrage nicht (SPEC-001 nennt den Pfad Zielbild), das Szenario ist gegen den heutigen Server nicht baubar. Entscheidung des Nutzers 2026-10-31: Option A, befristeter Carveout; Auflösungs-Trigger `slice-attention-cancel-timeout` in `done/` und das Szenario besteht in `make simulations-integration`; Folge-Slice `slice-attention-cancel-timeout` in `welle-fehler-abbruch`. Kein Gate ist rot: `make gates` und `make e2e` sind grün (Verifikation unten); der Carveout nimmt keine Schwelle und keine Regel aus.

Die Aussagen der Welle, in zwei Spalten (die Tabellen der vier Slice-Closures zusammengeführt). *Gemessen* heißt: ein bestandener Lauf am Code der Welle, mit handgebauten Eingaben, Attrappen, dem Referenzclient oder im Spike der ADR; **nie an einem echten SQL Server**. *Festlegung* heißt Entscheidung oder Lesart ohne Messung.

| Spalte | Aussage | Beleg im Repo |
|---|---|---|
| gemessen (Referenzclient SqlClient 7.1.1, TDS 7.4) | Auswahl mit Bedingung liefert Metadaten und die passenden Zeilen; leeres Ergebnis hat Metadaten und keine Zeile; eine Zeilenquelle oberhalb des Heaps (48 MiB) kommt vollständig an; der Fehler der Zeilenquelle kommt als `SqlException` nach den Zeilen, die Verbindung bleibt nutzbar | `referenztest-dotnet/Integration/SimulationBackendTests.cs` (vier Tests), `make e2e` Stufe 3 |
| gemessen (Eigencode, Attrappe) | Jede Zeile der Liste „Nicht erkannt“ endet als nicht erkannt, ihr Zwilling bleibt erkannt; Literale, Bezeichner, Kommentare werden an ihren Grenzen getrennt; Zuordnung und Objektprüfung rufen weder Zeilenquelle noch Callback; die Fehlercodes sind Namen von `ErrorCode` | `StatementFormsTest`, `StatementScannerTest`, `StatementOracleTest`, `StatementPlannerTest`, `SimulationCodesTest`; Mutationen rot (24 von 25 in der Verifikation, die 25. nach der Mini-Runde) |
| gemessen (Eigencode, handgebauter Baum) | Der Leser liest die Teilmenge samt Ignorierliste, bildet zehn Typen ab, weist elf ab; jeder Ladefehler trägt Pfad, Zeile, Spalte der richtigen Stelle und nie Inhalt; Validierung der Beschreibung | `SimDescriptionReaderTest` (26), `SimModelTest` (8) |
| gemessen (Eigencode, Jackson 2.21.7) | Der Adapter liefert Abbildung, Liste und Skalar mit Zeile und Spalte; Anker an Sammlungen, Aliase, Tags und Merge-Schlüssel sind Ladefehler; Größengrenze 1 MiB greift vor dem Parsen; Startabbruch vor dem Binden bei Widerspruch | `YamlTreeReaderTest`, `SimulationWiringTest`; Mutationen beider Verifikationen und des Reviews rot |
| gemessen (Eigencode, Zähler) | Auswahl: Spalten, Zeilenzahl, Bedingung auf jeder Zeile, Überführung vor dem Öffnen; `next()` nur auf Abnahme, `close()` genau einmal nach Ende, Fehler, Cancel und Disconnect; die Suite `QueryBackendContract` gilt am Simulations-Backend | `SimulationBackendSelectTest`, `SimValuesTest`, `SimulationBackendSourceTest`, `SimulationBackendContractTest`; Mutationen M1 bis M6, M8 rot, M7 roter Lauf ohne roten Test |
| gemessen (Eigencode, zählende Attrappe) | W1 bis W3 rufen den Callback genau einmal, ohne `WHERE` mit `where = null`; ohne Callback `TDS_UNSUPPORTED_SQL`; Fehler des Callbacks `TDS_BACKEND_ERROR` ohne Ausnahmetext; bei ungültigem Wert kein Callback | `SimulationBackendChangeTest`; Mutationen rot |
| gemessen (ein Messpunkt) | Ein Ergebnis über dem Heap des Testlaufs (1 200 000 Zeilen zu 128 Zeichen, Heap 128 MiB) wird an einer wartenden Senke gelesen und nicht vollständig gehalten | `SimulationMemoryTest`; frischer Lauf PASSED; M1, M8 rot |
| gemessen (Wächter) | Ein Import von `com.fasterxml.jackson` oder `org.yaml` in `simulation` macht `make detekt` rot; Heap-Zahl und Zeichenzahl des Integrationslaufs stehen an vier Orten gleich | `make detekt-selbsttest`, `IntegrationZahlenTest` |
| gemessen (Spike der ADR, Autor gleich Erwartender) | Zeichenscanner 60 von 60 Eingaben, die Regex-Variante 45 von 54 mit einem still veränderten Wert; Lesewege und Abhängigkeitsgröße der YAML-Bibliotheken; Verhalten von d-migrate 1.12.0 (zehn von 21 Typen abbildbar) | [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) §Messzeilen M1 bis MV |
| Handvektor | Ignorierliste und 21 Typnamen nachgezählt gegen das `schema.json` von d-migrate (gelesen, nicht gebaut); 153,6 MB Text gegen 134,2 MB Heap (Heap-Test); 64 000 000 Zeichen gegen 50 331 648 Bytes (Integrationslauf) | Reviews der Slices; Rechnung in den KDoc und in `tools/simulationstest.sh` |
| Selbstkonsistenz | 600 erzeugte Anweisungen, 64 Grenzfälle und 7 Anhänge: Parser und Erzeuger stammen aus demselben Slice; die Zahlen von Server und Client stehen in Kotlin und C# gleich | `StatementOracleTest`, `IntegrationServer.kt`, `SimulationBackendTests.cs` |
| Festlegung (Entscheidung des Nutzers, 2026-10-30) | Kotlin-Schnittstelle und Datei, neutrales Schema-Modell von d-migrate als YAML, Jackson 2.21.7, `IS [NOT] NULL`, strenge verlustfreie Überführung, `max_length` lesen und nicht durchsetzen, Typlücken beim Laden abweisen; Cancel-Szenario als Carveout (2026-10-31) | [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) §Entscheidungen des Nutzers 1 bis 8; [CO-004](../../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md) |
| Festlegung (Setzung, nicht gemessen) | Gelesene Teilmenge, Textvergleich ohne Beachtung der Schreibweise (`s` gleich `ſ`), Dezimalvergleich über `compareTo`, Überführungsregel und ihre Einzelfälle (`0` und `1` in einer Bool-Spalte), dreiwertige Logik, Fehlerabbildung der Weitergabe, Größengrenze, Symlinks, „genau einmal“ bei einem Sammler, Form C leer | [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) §Setzungen 1 bis 14, SPEC-035, SPEC-036 |
| Festlegung (vom Nutzer nicht bestätigt) | Die Liste „Nicht erkannt“ in SPEC-036 ist abschließend (Lesart); `tables` ist Pflicht (strenger als das Schema von d-migrate); `precision` und `scale` bei `decimal` Pflicht, Zeittypen ohne Genauigkeit, keine Obergrenze für die Länge des Anweisungstextes | [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) Offen 12 (a), Offen 7 und 8; Register `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` |
| nicht gemessen | Alles an einem echten SQL Server (Überführung, Textvergleich, Kollation, Kommentarschachtelung, Rundung, dreiwertige Logik); Cancel mit Folgeabfrage am Referenzclient (CO-004); Einfüge-, Änderungs- und Löschanweisungen und Abfragen mit Parametern am Referenzclient; TDS 8.0, Verschlüsselung, Pooling; der Rückstau mit einem langsamen Client und der Heap unter Last; die Frist `deadlineEpochMillis`; eine FIFO am Konfigurationspfad; die Signaturen der Jackson-Jars | Träger in der Tabelle unten; Register |

**Nutzerentscheidungen der Welle (Welle-Datei §3), gegen die Slice-Notizen geprüft:** Beschreibungsform, Schlüssel (beschreibend), Form C (leer) und Verdrahtung (Kotlin-API plus Umgebungsvariable) stehen als Entscheidungen des Nutzers vom 2026-10-30 in [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) (§Entscheidungen des Nutzers 1 bis 8) und in den Slice-Notizen. Die Entscheidung zum Cancel-Szenario (Option A) vom 2026-10-31 steht in CO-004. Offen beim Nutzer bleiben Offen 12 (a) und (c) der ADR.

**Jeder offene Punkt von [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) hat einen Träger und einen Ausgang** (Prüfung am 2026-10-31 gegen die Dateien im Planning-Lifecycle: jeder Träger existiert und führt, wo „führt [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md)“ steht, den Punkt in DoD, Risiko oder Verweis auf die ADR):

| Offener Punkt | Träger | Ausgang |
|---|---|---|
| Offen 1, 2: Typkatalog und Länge von `UnicodeText` | `slice-codec-datentypen-erweiterung` (`open/`, führt [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md)) | weiter offen, Zuschnitt beim Nutzer |
| Offen 3: Quelle des Skripts der Referenzinstanz, Nachführung von SPEC-045 | `slice-referenzabgleich-live` (`open/`, führt [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md)) | weiter offen |
| Offen 4, 5: Form C, Schlüssel als Metadatum, Identitätsmerkmal | `slice-qa-kompatibilitaet` (`open/`, führt [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md)) | weiter offen; die Form C ist leer (Setzung) |
| Offen 6: Servergleiche (Überführung, Textvergleich, Kommentarschachtelung, Rundung, Collation, Sitzungsoptionen, erzeugtes Skript) | `slice-referenzabgleich-live` (`open/`) | weiter offen (`BEO-TDS/messung-am-echten-sql-server-fehlt`) |
| Offen 7, 8: `precision`/`scale` bei `decimal`, Genauigkeit der Zeittypen, Länge des Anweisungstextes | `slice-sim-erkennung` (`done/`) | **als Festlegung gebaut, am Server nicht gemessen; weiter offen mit unvollständigem Träger:** die Messung am echten Server trägt `slice-qa-vergleichslauf-d` (`open/`), dessen DoD diese Punkte nicht führt (Prüfung am 2026-10-31: kein Verweis auf [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) in der Datei); geführt in `BEO-TDS/messung-am-echten-sql-server-fehlt` (Entscheidung beim Nutzer, ob die DoD ergänzt wird) |
| Offen 9: Name der Umgebungsvariable; Abhängigkeit und Lock; Wächter `ForbiddenImport` mit Selbsttest | `slice-sim-aenderung-integration` (`done/`) | erledigt mit Beleg: `SIMULATION_DESCRIPTION_FILE`; Jackson 2.21.7 in Lock und Prüfsummen; Wächter `ForbiddenImport/simulation` mit Selbsttest-Fall (`make detekt-selbsttest`, Mutation „Zeile entfernt“ rot). Die Prüfsummen stammen vom ersten Abruf, keine Signaturprüfung (`BEO-TDS/gradle-pruefsummen-ohne-signaturpruefung`, 1×) |
| Offen 10: JVM-Typen der Werte je `SqlType` | `slice-adr-backend-grenzen` (`done/`) | erledigt: [ADR-0013](../../adr/0013-backend-grenzen.md) Entscheidung 1; das Simulations-Backend liefert diese Klassen (Eine Zelle anderer JVM-Klasse ist ungleich, SPEC-035) |
| Lizenz der Abhängigkeiten im Artefakt | `slice-release-artefakt-lizenz` (`done/`) | erledigt im Slice; Rechtsfrage in `BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage` |
| Offen 11 | kein Träger | akzeptiert von der ADR (einmalig und folgenlos); der Re-Evaluierungs-Trigger fängt jede Änderung |
| Offen 12 (a) Lesart „die positive Aufzählung ist abschließend“, (c) Zeile unter „Offene Entscheidungen“ zu [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) | Nutzer; SPEC-036 und die Lastenheft-Zeile | **weiter offen beim Nutzer** (Register `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`, `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung`); (b) erledigt (Lastenheft 0.5.0) |

## Was hat funktioniert?

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3.

- Die Reihenfolge Entscheidung, Erkennung, Auswahl, Integration trug: Kein Slice brauchte die Rückführung nach der Welle-Datei, jeder startete auf dem Stand des vorigen. Die Entscheidung vor dem Code ließ die Lastenheft-Lücke (`IS [NOT] NULL`) sichtbar werden, bevor Code darauf stand; die Tabelle „Deckung“ der ADR war der Auslöser.
- Das Prüfen durch Mutation fand, was Lesen und grüne Tests nicht fanden, in allen drei Code-Slices (Erkennung 25 Mutationen der Verifikation, Auswahl M1 bis M8, Integration fünf Änderungen im Review und sechs in der zweiten Verifikation): eine Festlegung ohne Test (`s` gleich `ſ`), ein vorauslesendes Backend, das die Suite durchließ, ein `IOException`-Zweig ohne Test.
- Die abstrakte Suite `QueryBackendContract` aus `welle-backend-streaming` gab dem Simulations-Backend ein Prüfwerkzeug: Es erbt die Suite und besteht sie wie das Test-Backend; der Review von `slice-sim-auswahl-streaming` schärfte sie um den Test der Abnahme.
- Die Zählung der Zeilen und Zeichen an vier Orten hält jetzt ein Test (`IntegrationZahlenTest`), nicht die Disziplin der Beteiligten.
- Der Integrationslauf mit dem Referenzclient ist die erste Messung mit einem echten Client am Server dieses Repos; sie fand vor der Closure eine Lock-Stage, die `LizenzTest` brach.

## Was ging anders als geplant?

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — jede Zeile moeglichst mit der Konsequenz,
die daraus schon gezogen wurde (Folge-Slice, Spec-Version).

- Der Cancel-Teil von SPEC-040 war nicht baubar: Der Server beantwortet ATTENTION während einer Anfrage nicht, und die Welle-Datei §5 nannte die Abhängigkeit nicht. Der Review fand es (F-1), die Spec war zuerst ohne Carveout gekürzt worden. Konsequenz: [CO-004](../../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md) mit Folge-Slice `slice-attention-cancel-timeout`. Register: `BEO-TDS/dod-haengt-an-offenem-slice-ohne-rueckfuehrung` (3×).
- Der Integrationslauf war als Test-Gate in `make gates` geplant (Welle-Datei §3). [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) nahm ihn aus den Gates und machte ihn zur Stufe 3 von `make e2e` mit eigenem Workflow; §3 der Welle-Datei ist vor dem Move angepasst (Inhalts-Commit).
- Mehrere Fix-Runden haben weder Review noch Verifier nachgeprüft (Erkennung: 188c4f3 nur durch die Verifikation, ea338ef und 8eef26b nur Implementer und Planner; Integration: 76985bd erst von der Verifikation, 1f1c8dd nur von der zweiten Verifikation, 6ace873 und cbaca96 nur Implementer und Planner). Die Angaben stehen in den Slice-Notizen. Die Fix-Runde der Integration erzeugte eine neue Lücke derselben Klasse (M-1).
- Spec, KDoc und ADR waren nach Entscheidungen mehrfach breiter als der Beleg ([ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) F-1 bis F-13; KDoc „ohne Leserecht“; Etikett der Heap-Tests). Das ist `BEO-TDS/aussagen-ueber-messung-hinaus` (40×), verkörpert in `AGENTS.md` §3.8 und im Reviewer-Skill; die Wiederkehr nach der Verkörperung ist belegt und in jedem Fall behoben.
- Die Träger der Offen-Liste von [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) standen nach zwei Fix-Runden noch nicht vollständig (Offen 6 nur halb getragen); der Träger von Offen 7 und 8 für die Messung am Server ist es bis heute nicht (siehe Tabelle).
- Die Heap-Mutation „jede Zeile behalten“ endet als abgebrochener Test-Worker statt als roter Test (`BEO-TDS/oom-bricht-testlauf-statt-test`, 2×); der Review ließ sie 23 Minuten laufen, die Ursache ist nicht geklärt.
- `SimTable` kopiert seine Listen nicht (das Gate `LongParameterList` lässt nur die `data class` zu); der Katalog kopiert, die Zusage am Konstruktor trägt nur KDoc und SPEC-035. Register: `BEO-TDS/listen-ohne-kopie-wegen-parameterzahl-gate` (1×, `offen`).

## Steering-Loop-Einträge

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 (hier stehen **nur** Beobachtungen, die im
Register 3× erreicht haben; jeder Eintrag nennt seine `BEO-<KUERZEL>/<slug>`) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln (Feld
und Zielort auf **einer** Zeile, Sektionsangabe innerhalb der Backticks; die
**Spec-Lücke** trägt statt `liegt in` ihre `LH-*`-ID — das ist kein Versehen).

**Lese-Schritt des Registers (2026-10-31).** Das Register führt 59 Beobachtungen, jede mit nicht leerem `evidence/` (gegen das Dateisystem geprüft); 19 haben mindestens drei Evidence-Dateien. 16 davon tragen Evidence aus den vier Slices dieser Welle; 12 dieser 16 haben schon einen Ausgang, vier stehen über der Schwelle ohne Ausgang. Die übrigen drei Beobachtungen über der Schwelle haben keine Evidence aus dieser Welle und bleiben unverändert: `BEO-TDS/strukturtest-ohne-variation-der-eingabe` (6×, verkörpert), `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` (6×, geplant), `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen` (4×, offen, Entscheidung beim Nutzer).

*Schon mit Ausgang* (der Lese-Schritt bestätigt den Stand, ohne ihn zu ändern):

- `BEO-TDS/aussagen-ueber-messung-hinaus` (40×, alle vier Slices): `verkörpert` in `AGENTS.md` §3.8 und im Reviewer-Skill, Anker `seit welle-protokollcodec`. Wiederkehr nach der Verkörperung in allen vier Slices, jeweils als Finding behoben; keine Schärfung.
- `BEO-TDS/offene-punkte-ohne-traeger` (17×, `slice-adr-simulation-formen`): `verkörpert` in `.claude/commands/implement-slice.md` Schritt 24. Die Träger-Prüfung des Entscheidungs-Slice fand Offen 6 halb getragen; behoben vor `Accepted`.
- `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (12×, drei Slices): `verkörpert` im Reviewer-Skill, Anker `seit welle-transport-tls`. Die Wiederkehr in `slice-sim-aenderung-integration` (M-1: eine Fix-Runde entfernt einen Fehlerzweig und lässt den Geschwister-Zweig ungeprüft) ist ein Fall, den die Regel nicht erfasst; **ob sie um „Fix-Runden prüfen die Geschwister-Zweige der berührten Funktion“ geschärft wird, entscheidet der Nutzer** (kein `liegt in`, keine Änderung durch diese Closure).
- `BEO-TDS/test-belegt-nur-den-fake` (4×, `slice-sim-auswahl-streaming`), `BEO-TDS/risikozeile-nennt-behobenen-zustand` (4×, `slice-adr-simulation-formen`), `BEO-TDS/dod-satz-verlangt-ablage-die-der-slice-ersetzt` (4×, `slice-adr-simulation-formen`), `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor` (7×, `slice-sim-auswahl-streaming`), `BEO-TDS/test-stage-cache-verdeckt-wiederholung` (5×, `slice-sim-auswahl-streaming`): `verkörpert`, je Ausgang im `state.md`; keine Schärfung durch diese Welle.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` (4×, `slice-adr-simulation-formen`): `gestrichen`, vom Doku-Gate gedeckt.
- `BEO-TDS/messung-am-echten-sql-server-fehlt` (23×, alle vier Slices): `geplant`, Träger `slice-qa-vergleichslauf-d` (`open/`); für Offen 7 und 8 der ADR unvollständig (siehe Tabelle oben).
- `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` (15×, `slice-sim-aenderung-integration`): `geplant`, Träger `slice-qa-kompatibilitaet` (`open/`); der Integrationslauf fuhr TDS 7.4 ohne Pool, Parameter und Änderungsanweisungen nicht.
- `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` (8×, `slice-sim-auswahl-streaming` und `slice-sim-aenderung-integration`): `geplant`, Träger `slice-qa-performance` (`open/`).

*Über der Schwelle, nicht verkörpert, Entscheidung beim Nutzer.* Diese Closure verkörpert keine Regel und ändert weder `AGENTS.md` noch ein Gate, einen Skill oder eine Adaption: eine neue Regel oder ein neuer Sensor ist eine Entscheidung des Nutzers. Der `state.md` jedes der vier Einträge trägt den Vermerk dieses Lese-Schritts. Es gibt kein `liegt in`, die Paarung (a) entfällt.

- `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` — 10×, aus dieser Welle `slice-adr-simulation-formen` (Offen 12 (a)) und `slice-sim-erkennung` (Lesart „abschließend“, Festlegungen des Lesers). Zugleich Offen 12 (a) und (c) der ADR, die der Nutzer beantworten muss.
- `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung` — 4×, aus dieser Welle `slice-adr-simulation-formen` (F-2, N-2) und `slice-sim-erkennung`. Die Deckungstabelle als Pflichtform vor dem Eintrag einer Einzelfall-Liste in die Spec ist ein Vorschlag, noch keine Regel; die Prüffrage steht im Reviewer-Skill (Kategorie „Die untere Spec-Schicht erweitert die obere“).
- `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler` — 3×, aus dieser Welle `slice-sim-erkennung` (F-4). Die Zählung ist uneinheitlich geführt (siehe `state.md`); kein belegbarer Schwellen-Übertritt, daher kein Anker.
- `BEO-TDS/dod-haengt-an-offenem-slice-ohne-rueckfuehrung` — 3×, aus dieser Welle `slice-sim-aenderung-integration` (F-1). Die dritte Instanz ist als [CO-004](../../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md) mit Folge-Slice getragen.

**Neue Sensoren dieser Welle** (ohne `liegt in`, weil mit ihr keine Regel verkörpert wurde): `IntegrationZahlenTest`, der Wächter `ForbiddenImport/simulation` für Jackson und snakeyaml samt Selbsttest-Fall, der Test der Abnahme in `QueryBackendContract`, der Test über `/proc/self/mem` (Lesefehler an einer regulären Datei).

**Spec-Lücken, benannt und offen** (kein `liegt in`, weil in einer Spec und nicht an einem Zielort verkörpert):

- [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) führt unter „Offene Entscheidungen“ weiter die Zeile zu den erkannten Anweisungsformen, obwohl die Festlegung als `Accepted` vorliegt (Offen 12 (c)); und die Lesart „die Liste der positiven Formen ist abschließend“, auf der die Deckung der Liste „Nicht erkannt“ in SPEC-036 beruht, ist nicht bestätigt (Offen 12 (a)). Beides entscheidet der Nutzer.
- SPEC-040 nennt „Cancel mit Folgeabfrage gehört nicht zum Lauf, die Ausnahme ist befristet“ und kann den Carveout nicht nennen (die Spec verweist nicht abwärts); wer die Spec liest, findet Trigger und Folge-Slice über `harness/README.md` und CO-004.
- Die Matrix `docs/plan/spec-abdeckung.md` kennt nur Kotlin-Tests und führt SPEC-040 mit 0 Nachweisen, obwohl der Referenzclient-Lauf besteht; der Beleg steht in `make e2e` und `docs/plan/e2e-abdeckung.md`.
- SPEC-035 und SPEC-036 setzen Einzelfälle der Überführung und der Bezeichnerregeln, die [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 6 nur als Familie trägt; kein Server hat sie gemessen.

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

Keine neuen Slices durch diese Closure. Die Träger der offenen Punkte existieren als Dateien in `open/`:

- `slice-attention-cancel-timeout` (löst [CO-004](../../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md) auf, Frist `deadlineEpochMillis`, hängendes `next()` einer Quelle; die Zerlegung vor dem Start ist L-4 der zweiten Verifikation von `slice-sim-aenderung-integration`).
- `slice-qa-vergleichslauf-d` (Messung am echten Server; Offen 7 und 8 unvollständig getragen), `slice-referenzabgleich-live` (Offen 3 und 6), `slice-qa-kompatibilitaet` (Offen 4 und 5, Pool, TDS 8.0, Parameter), `slice-qa-performance` (Rückstau und Heap unter Last).
- `slice-codec-datentypen-erweiterung` (Offen 1 und 2: Typkatalog, Länge von `UnicodeText`), `slice-integrations-einstieg` (öffentlicher Einstieg für Integratoren, `BEO-TDS/oeffentlicher-einstieg-ohne-traeger`).

## Verifikation

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 1 — keine Behauptung ohne nachprüfbaren
Anker (Hash, Lauf, Zahl).

- Alle vier Slices der Welle in `done/`: `slice-adr-simulation-formen`, `slice-sim-erkennung`, `slice-sim-auswahl-streaming`, `slice-sim-aenderung-integration` (Dateien gegen das Dateisystem geprüft; `grep -c -- '- \[ \]'` ergibt je 0; `Welle:` nennt in allen vier `welle-simulations-backend`). Der DoD-Punkt 3 von `slice-sim-aenderung-integration` ist nur teilweise erfüllt (vier von fünf Szenarien), der Haken trägt die Einschränkung im Wortlaut; der Slice geht mit [CO-004](../../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md) nach `done/`.
- Stand der Läufe: `33c89da` (nach `git merge main` auf `363a90a`; die Commits dieser Closure ändern nur Dokumente).
- `make gates`, einmal, Exit 0, 8 s (Cache der Gradle-Stages warm; `baseline-verify: v6.18.1 OK — 54 Dateien`, `make docs-check` ohne Befund).
- `make e2e`, einmal, Exit 0, vier Stufen grün in 37 s (Stufe 1 `make smoke` 1 s, Stufe 2 `make laufzeittest` 17 s, Stufe 3 `make simulations-integration` 17 s mit „Alle 4 Tests des Referenzclients bestanden“, Stufe 4 `make referenztest-dotnet` 2 s); warm, nicht der kalte Wert (`harness/README.md` nennt kalt 323 s für den ersten Lauf).
- Frischer Testlauf ohne Cache: `docker build --no-cache-filter test --target test --progress=plain .`, einmal, Exit 0, `BUILD SUCCESSFUL in 1m 20s` (86 s Wanduhr), 779 Tests `PASSED`, kein Test `FAILED` (der Treffer von `grep FAILED` ist der Testname `Eine schliessende Abbildung ergibt FAILED_CLOSE_CONNECTION` und steht `PASSED`); die Stage `test` lief, kein Cache-Treffer. Darin `SimulationMemoryTest` und die drei `StreamingSmokeTest` `PASSED`.
- Trigger-Audit, je Klasse:
  - **Carveouts:** vier aktiv, 0 aufgelöst, 0 fällig. [CO-001](../../carveouts/done/CO-001-nuget-audit-aus.md) und [CO-002](../../carveouts/done/CO-002-nuget-signaturpruefung-fehlt.md) (.NET-Referenztests): Trigger (Folge-Slice `slice-qa-dotnet-lieferkette-haerten` in `done/`) nicht eingetreten, der Folge-Slice liegt in `open/`; die Welle berührt die Lieferkette der .NET-Pakete nicht (der Integrationslauf nutzt die bestehende Lock-Datei). [CO-003](../../carveouts/done/CO-003-anmeldedrosselung-fehlt.md) (Anmeldedrosselung): Trigger `slice-qa-anmeldedrosselung` in `done/` nicht eingetreten, Folge-Slice in `open/`; die Welle berührt den Anmeldepfad nicht. [CO-004](../../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md): **in dieser Welle eröffnet, Option A vom Nutzer bestätigt (2026-10-31), bleibt aktiv, befristet durch den Auflösungs-Trigger** (`slice-attention-cancel-timeout` in `done/` und das Szenario besteht in `make simulations-integration`); Folge-Slice existiert in `open/` und nennt CO-004 in seiner DoD und seinem §4, CO-004 nennt ihn zurück. Die Welle schließt mit diesem Carveout, nicht mit einem stillen roten Gate.
  - **Bootstrap-aware Gates:** 0 offen. Die Tabelle *Sensors* in `harness/README.md` führt kein Target mit Schwelle oder Reifestufe; `make test`, `make detekt`, `make smoke` tragen keine Stufe, und `make e2e` und `make simulations-integration` sind nach [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) keine Gates.
  - **ADR-Re-Evaluierungs-Trigger** der von der Welle berührten, angenommenen ADRs: [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md): ein neues Release von d-migrate mit geändertem `schema_format`: nicht beobachtet (der Belegstand ist der des 2026-10-30; kein Sensor prüft das, der Trigger hat keinen Wächter); eine Messung am echten SQL Server, die Überführung, Textvergleich oder Typabbildung widerlegt: nicht gefahren, Wächter `slice-qa-vergleichslauf-d`; Offen 7 und 8 (`precision`/`scale`, Länge des Anweisungstextes) bleiben ohne vollständigen Träger, **kein eingetretener Trigger, aber eine benannte Lücke**; eine Entscheidung zu Typkatalog oder Länge von `UnicodeText`: nein (der Träger liegt in `open/`); ein Sicherheitsbefund zu `jackson-dataformat-yaml` oder `snakeyaml`: kein Befund bekannt, kein Sensor dafür; die Entscheidung, ein Jar mit eingebetteten Bibliotheken auszuliefern: nein. **Offen 12 (a), (c)** bleiben beim Nutzer (Lesart „abschließend“ und Lastenheft-Zeile unter „Offene Entscheidungen“). [ADR-0013](../../adr/0013-backend-grenzen.md): ein echtes Backend mit blockierendem Treiber: nein (die Zeilenquelle des Simulations-Backends ist suspendierend); Zeilen als Spaltenblöcke als Heap-Bestimmer: nein (`SimulationMemoryTest` und der Integrationslauf zeigen den Strom als Bestimmer, ein Messpunkt); ein Client mit Timeout ohne ATTENTION: nicht gemessen. [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md): Backpressure-Probleme oder zusätzliche Ergebnisarten: nein; eine Messung am echten Server gegen die Vorschau für `DONE_MORE`: nicht gefahren. [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md): neue SqlClient-Hauptversion (7.1.1 im Integrationslauf, gepinnt), nicht reproduzierbarer Kompatibilitätsfehler: nein. [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) (in dieser Welle angenommen): `make e2e` warm 37 s, die Schwelle der ADR für die Aufnahme in `make gates` (unter einem Zehntel der Stage `test` ohne Cache, hier 86 s) ist nicht erreicht, der Trigger ist nicht eingetreten; eine Regression, die `make gates` durchließ und erst der E2E-Workflow fand: nicht beobachtet; der Workflow `e2e.yml` hat noch keinen Lauf auf `main` (`harness/README.md`), der Vier-Wochen-Trigger läuft damit nicht.
  - **0-offen-Feststellungen sind belegt**, nicht behauptet: Quelle jeder Zeile sind die Dateien der Carveouts und ADRs vom 2026-10-31.
- Zuordnung *gemessen | Festlegung* und Träger je offenem Punkt von [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md): siehe oben (Closure-Trigger der Welle-Datei §3).
- Drei Paarungen: (a) Anker — kein Eintrag dieser Closure trägt `liegt in`, die Paarung entfällt. (b) Folge-Slice — alle unter *Folge-Slices* genannten Dateien existieren in `open/` (gegen das Dateisystem geprüft am 2026-10-31); die Ausnahme ist benannt: `slice-qa-vergleichslauf-d` führt Offen 7 und 8 nicht. (c) Register — alle genannten `BEO-TDS/<slug>` existieren als Verzeichnis mit nicht leerem `evidence/` (gegen das Dateisystem geprüft).
- Archivierung (Schritt 4): `archive-welle welle-simulations-backend` lief mit Kennung [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) in zwei Commits (`a4b292d` reiner Move, `f42c403` Archiv, Stubs und Verweis-Nachzug): vier Slices, der Welle-Plan und sieben Review-Reports nach `done/welle-simulations-backend/archiv.zip` (110370 Bytes, zwölf Dateien, mit `unzip -l` gegen die Vorschau geprüft), fünf Stubs, sieben Reports aus `docs/reviews/` entfernt, Verweis-Nachzug in 21 Dateien. Der Träger `.harness/state/bin/ai-harness-init` liegt im gitignorierten Zustands-Bereich des Hauptcheckouts, nicht im Worktree dieses Laufs; der Lauf rief ihn von dort mit dem Arbeitsverzeichnis des Worktrees auf (derselbe Aufruf wie das Ziel `archive-welle`, plus `--kennung`). Befunde:
  - Vorher und nachher geprüft: Das Archiv enthält genau die vier Slices dieser Welle (`slice-adr-simulation-formen` gehört real zu ihr), den Plan und die sieben Reports, deren Name eine dieser Slice-Kennungen trägt; die Vorschau zählte 4 Mitglieder, 0 wellenlose, 13 fremde (Slices anderer Wellen und wellenlose der Folgezeit bleiben flach liegen). Die Prüfsummen der fünf früheren Archive (`welle-protokollcodec`, `welle-entscheidungen-grundlage`, `welle-transport-tls`, `welle-handshake-login`, `welle-backend-streaming`) sind vor und nach dem Lauf gleich (`sha256sum -c`), und das Archiv von `welle-backend-streaming` enthält nichts zu `sim` oder `0012`.
  - Die Vorprüfung sperrte zunächst mit `[haenger]`: Verweise aus [CO-004](../../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md), die Register-Evidence-Dateien und `slice-integrations-einstieg` zeigten auf sieben Reports, die verschwinden. Die Links sind durch die Kennung des Reports ersetzt (Commit `61e73e4`), danach lief die Vorprüfung ohne Sperre.
  - Nicht archiviert, weil ihr Name keine Slice-Kennung der Welle trägt: die Reports zur Entscheidung (`2026-10-30-69c32e6-adr-0012-simulations-backend`, die zugehörige Verifikation und Verifikation 2, `2026-10-30-1565700-nachreview-adr-0012`) und die Optionsberichte (`2026-10-30-e6bfd2d-optionen-adr-simulation-formen`, `2026-10-30-13434ec-optionen-adr-simulation-formen-yaml`). Sie bleiben in `docs/reviews/`; ihre Verweise auf die Slices lösen über die Stubs auf.
  - Der Verweis-Nachzug des Werkzeugs erreichte die Verweise auf den Welle-Plan selbst nicht (zwölf Fundstellen in Slices, Wellen und einem Review; nach dem Move 67 Befunde in `make docs-check`). Von Hand nachgezogen im Commit `f7b272d`, vor der Archivierung; danach 0 Befunde. Dazu die eigenen `../`-Pfade des Plans (eine Ebene tiefer).
  - Geltungsbereich der Sensoren: wie bei den früheren Archivierungen geprüft (`.d-check.yml` und die Werkzeug-Skripte keilen nicht auf `done/*.md`); `make docs-check` nach dem Lauf: 532 Dateien, 0 Befunde.
  - Zwischenstand: zwischen dem Move `03270b1` und dem Commit `f7b272d` war `make docs-check` rot (Verweise auf den bewegten Welle-Plan); die Commits gehören in denselben Push.
- `make gates` Exit 0 nach der Archivierung (siehe den Bericht der Closure für Lauf und Laufzeit).
