# Verifikationsbericht: slice-bulkcopy-tabellenname-mit-schema-und-sonderzeichen (HEAD 82f62eae)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: Slice `slice-bulkcopy-tabellenname-mit-schema-und-sonderzeichen` (`in-progress/`, Zweig `worktree-agent-ac37abe6cc0d45031`, HEAD `82f62eae`, Basis `main` `7412984a`; `main` steht inzwischen auf `f2db5649`, nur Planungsdokumente).
* Maßstab: DoD §2 und Plan §3 des Slice, [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [ADR-0044](../plan/adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md), `SPEC-001`, `SPEC-036` Form B, `SPEC-040`; Grundsatz „Spec ist Zielbild“ und `AGENTS.md` §3.8.
* Verifiziert wird die Zusage, nicht der Diff; dazu ein Kurzreview des Parsers der Nachbesserung (`82f62eae`; kein zweiter Review lief). Nichts am Produktivcode geändert, kein `git stash`. Rig und Mutationen liefen an einer Kopie (`git archive HEAD`) im Scratchpad (`.../scratchpad/vb/`), Original unverändert.

## Eigene Läufe (Belege)

Host: Lastmittel 3 bis 10 (der Reviewer von `slice-drop-constraint-form-d5` lief nebenher), keine künstliche Last, alle Läufe im Vordergrund oder auf den Exit-Code gewartet.

| Lauf | Ergebnis |
|---|---|
| `make clean-build` (einmal, End-Stand, `--no-cache`) | Exit 0, 560 s; 1349 `PASSED`, kein `FAILED`; `BulkCopyFormsTest` alle 9 Fälle `PASSED` |
| `sh tools/matrix-zitate.sh docs/plan/kompatibilitaetsmatrix.md <Log von clean-build>` | Exit 0; 349 verschiedene Zitate, 0 ohne `PASSED`, 0 Fremdformen (die Zitate der Matrix sind gegenüber `7412984a` unverändert: Mengenvergleich; deshalb kein eigener `make matrix-zitate`) |
| `make e2e` | Exit 0, „gruen (8 Stufen, 294 s)“, kein Last-Flake (Stufe 1: 69 s, 2: 85 s, 3: 19 s, 8: 12 s); Stufe 3 = `make simulations-integration`: 7 Tabellen im Log, **13 von 13** Tests, darunter die vier Fälle der Theorie (`dbo.bulk_ziel`, `[dbo].[bulk_ziel]`, `s1.[bulk]]ziel'x]`, `[s1].[bulk]]ziel'x]`) und `Eine_Tabelle_in_einem_unbekannten_Schema_…` |
| `make referenztest-dotnet` | Exit 0, aber vollständig aus dem Layer-Cache (38 s, kein Testlog). Deshalb zusätzlich `docker build --no-cache-filter test --target test referenztest-dotnet`: Exit 0, **136 bestanden, 0 rot, 0 übersprungen** (7 s) |
| `make gates FORCE=1` | Exit 0, 29 s (Stages aus dem Layer-Cache von `clean-build`/Vorläufen: Verdrahtungsbeleg, der Frischbeleg ist die `clean-build`-Zeile) |
| `make flaeche` | Exit 0 (keine neue öffentliche Fläche; `recognize` und `TableRef` sind `internal`) |
| `make docs-check` | Exit 0, 1346 Dateien, 0 Befunde (vor Anlage dieses Berichts) |
| `git merge-tree --write-tree HEAD <main>` mit `main` = `f2db5649` | Exit 0, kein Konflikt (Baum `222e49ef`); `main` brachte nur `slice-drop-index-if-exists` (Roadmap, Welle, `slice-abnahme-d-migrate`) |
| d-migrate 1.12.0 (Digest `sha256:308f2775…`) | siehe Abschnitt Referenzclients |
| Echter SQL Server (`make vergleichslauf-d`) | **nicht gefahren** (Messung liegt als Ablage vor; Freigabe des Nutzers für das gepinnte Image besteht; die Ablage trägt in allen 38 Dateien die Digests `2022-latest@sha256:cf8f0ab2…` und `2025-latest@sha256:245f6b1c…`) |

## Kurzreview des Parsers (`BulkCopyForms.kt`, Konstante `PART`)

Fassung: `\[((?:[^\]']++|\]\]|'')++)\]`, dazu `unescape` (`]]` zu `]`, danach `''` zu `'`) und Rückbezüge `\1`, `\2` an den Stellen `FROM` und Kollation.

**Absturz und Laufzeit** (Rig `impl-bulkname-deps:lokal`, Wegwerf-Test, nicht übernommen; Zeit je `recognize` ohne Aufwärmen, 10 000, 100 000 und 1 000 000 Zeichen je Name):

| Eingabe | Ergebnis |
|---|---|
| Name `a…a`, gemischt `a]b'c. …` (mit `]]` und `''`), `]]]…` (nur `]]`-Paare), `'''…` (nur `''`-Paare); ohne Schema; mit Schema, beide Namen lang; nur die Tabelle lang | erkannt, Namenslänge richtig (bei `]]]…` und `'''…` die halbe Zahl der Zeichen), kein Fehler; 1 Mio Zeichen: 5 bis 83 ms (größter Text 12 MB, Schema+Name mit `]]`-Paaren) |
| einzelnes `]` oder `'` am Ende, in der Mitte (auch hinter 1 Mio `]]`-Paaren), Name ohne Verdopplung | nicht erkannt (`null`), 0 bis 59 ms |
| Ende fehlt (abgeschnitten), zweiter Name der drei Stellen abweichend und lang, Name an einer der drei Stellen um ein Zeichen verlängert | `null`, 0 bis 7 ms (Rückbezug ohne Zeitexplosion) |
| 1 Mio Leerzeichen vor und hinter dem Text, 1 Mio Leerzeichen in einer Lücke des Wortlauts | erkannt, 2 bis 14 ms; 1 Mio `;` am Ende: nicht erkannt, 0 ms |

Kein `StackOverflowError`, kein Fall über 100 ms: die Laufzeit ist linear. **Mutation R** (`PART` wieder `(?:[^\]']|\]\]|'')+`, enge Klasse `BulkCopyFormsTest`): rot, 1 von 9 (`lange Namen … ohne Fehler` mit `StackOverflowError`), also trägt der Test den Befund F-1 des Reviews.

**Erkennung unverändert gegenüber der Vorfassung.** Differentialtest alte gegen neue Wiederholung (Rig, nur Namen bis 7 Zeichen, damit die alte Fassung nicht überläuft): 40 000 Zufallsfälle über sechs Muster (verdoppelt ausgezeichnet, mit Schema, roh unausgezeichnet mit freiem `]` und `'`, an den drei Stellen abweichend, Name mit Punkt in einer Klammer), davon 13 073 erkannt: Ergebnis (`TableRef` oder `null`) in jedem Fall gleich. Gegenüber Z1 bis Z8 des Reviews: `BulkCopyFormsTest` 9 von 9 grün im Frischbau; die Texte der Ablage (alle zehn `probe-*.sql`) ergeben dieselben `TableRef` wie vor der Nachbesserung (Tests unverändert grün). Die Begründung, warum die besitzende Wiederholung die Menge der erkannten Texte nicht ändert: nach dem Namensteil folgt in der Vorlage nie ein `]`, nur `'`, `.` oder `)`; eine `]]`-Folge ungerader Länge (`]]]`) schließt der Teil mit dem letzten `]`, die Zerlegung ist eindeutig.

**Mutationen der Nachbesserung nachgefahren** (enge Klassen):

| ID | Mutation | Klasse | Ergebnis |
|---|---|---|---|
| R | `PART` wieder rekursiv (ohne `++`) | `BulkCopyFormsTest` | rot (1 von 9, `StackOverflowError`) |
| MC | Trimmen und `removeSuffix(";")` in `recognize` entfernt (`val text = sql`) | `BulkCopyFormsTest` | rot (1 von 9: `Leerraum und Semikolon am Ende …`); F-4 des Reviews behoben |
| MD | Schema im Katalogschlüssel exakt (`SimModel.kt:161`, `schema` statt `SimNames.fold(schema)`) | `BulkCopyFormsTest` | rot (1 von 9: `die Probe sucht Schema und Namen ohne Beachtung der Schreibweise`); F-9 behoben |
| LF | Datei `probe-klammer.sql` von CR LF auf LF umgestellt (Bindung) | `BulkCopyFormsTest` | rot nur durch den Summentest (1 von 9); die Erkennung bleibt grün: der Summentest ist der einzige Träger der Bindung |

## Referenzclients

* **Referenzclient `SqlBulkCopy`:** `make e2e` Stufe 3 grün (13 von 13), s. o. Die vier Namensformen mit Schema und Sonderzeichen und das unbekannte Schema (`InvalidOperationException`, Verbindung bleibt offen, Folgeabfrage liefert) sind belegt.
* **d-migrate 1.12.0 (mssql-jdbc 13.4.0), Regression der Namen ohne Schema.** Zwei Testserver aus dem Arbeitsbaum (`TESTSERVER_NAME=vb-ts-a`/`vb-ts-b`, Ports 14377/14378, Image `vb-testserver:lokal`), Standardtabelle `kunden`: `data import` (Standardpfad, `INSERT BULK`) Exit 0, „2 inserted“; `data export` zeigt `Ada` und `Bob` (`Eintritt` `null`); `data transfer` A nach B Exit 0, Export B gleich; erneutes `data import` endet mit `Violation of PRIMARY KEY constraint. Cannot insert duplicate key.`, Exit 5 (wie in der Nutzer-Doku). Danach beide Container gestoppt und entfernt, Image `vb-testserver:lokal` entfernt. Namen mit Schema oder Sonderzeichen in `--table` wurden **nicht** gefahren und sind am echten Server nicht gemessen.
* **Grenze „mssql-jdbc mit Schema und Sonderzeichen nicht gemessen“ je Aussagesatz:** steht im Satz der Aussage in `SPEC-036` Form B (Absatz der Probe), `docs/user/einbettung-und-betrieb.md` (Punkt zum Massenladen), `docs/user/referenz-konfiguration.md` (Zeile „Probe der Zieltabelle“) und `docs/user/testserver-manuell.md` (letzter Satz des Punktes „Der Referenzclient lädt anders …“) sowie im Messprotokoll („Grenzen“). Die Sätze, die nur den Befund „gemessen an `SqlBulkCopy` (M-33 bis M-36)“ tragen, nennen den Client im Satz: `SPEC-001` (Absatz der festen Typen), `SPEC-036` (Absatz „Gemessen ist die Form an …“), `SPEC-040`, der erste Satz in `referenz-konfiguration.md` (Zeile 69) und `kompatibilitaetsmatrix.md` (Zeile BULK_LOAD). Siehe V-5.

## Rohbytes der Ablage

Eigener Dekoder (Python, `-I`, Scratchpad), alle 3 neuen Dateien je Instanz vollständig gelesen (52, 52 beziehungsweise 12 Nachrichten), nicht nur die Auswertung der Tests:

| Prüfung | Ergebnis |
|---|---|
| Größen der Ablage | je Instanz 19 Dateien, 757 797 Bytes (2022) und 758 177 Bytes (2025) wie im Messprotokoll |
| Köpfe | alle 38 Dateien der Ablage tragen den Digest ihrer Instanz; die sechs neuen nennen `Microsoft.Data.SqlClient 7.1.1 auf .NET 10.0.14`, den TLS-beendenden Proxy und die Aufnahme 2026-11-11 |
| `bulk-name-schema`: Probetexte (7 je Instanz) | `[dbo].[bulk_ns]` (3 Läufe: `dbo.bulk_ns`, `[dbo].[bulk_ns]`, `"dbo"."bulk_ns"` ergeben denselben Text), `OBJECT_ID('[dbo].[bulk_ns]')`, `N' FROM [dbo].[bulk_ns]'`, `EXEC ..sp_tablecollations_100 N'[dbo].[bulk_ns]'` (ohne Punkt vor der Klammer); drei Teile: `OBJECT_ID('[tdswire_namen].[dbo].[bulk_ns]')`, `FROM [tdswire_namen].[dbo].[bulk_ns]`, `EXEC [tdswire_namen]..sp_tablecollations_100 N'[dbo].[bulk_ns]'`, `insert bulk [tdswire_namen].[dbo].[bulk_ns]`; `[s1].[bulk_s1]`; unbekannt `[dbo].[gibtesnicht]` und `[s2].[bulk_ns]` (die Probe geht auf den Draht, danach kein `insert bulk`) |
| `bulk-name-sonderzeichen` (7 je Instanz) | `[bulk]]x]` an `OBJECT_ID` und `FROM`, `N'.[bulk]]x]'`, `insert bulk [bulk]]x]`; `[bulk''x]` an allen drei Stellen, `N'.[bulk''x]'`, `insert bulk [bulk'x]` (einfach); Leerzeichen, Punkt in der Klammer (ein Teil, `N'.[bulk.x]'`), `dbo.[bulk x]` (`N'[dbo].[bulk x]'`), zwei Leerzeichen unverändert (`[bulk  y]`) |
| Texte beider Instanzen | die Probetexte (Zeilen `OBJECT_ID`, `EXEC(N'SELECT … FROM …')`, `sp_tablecollations_100`) sind in 2022 und 2025 gleich, in allen drei Dateien |
| Probetexte gegen die zehn `probe-*.sql` | SHA-256 des UTF-8-Textes der Ablage (Instanz 2022) gleicht der Summe jeder Datei, für neun Dateien in `bulk-name-*` und für `probe-bulk_typen` (Datei `bulk-typen-null`); der Text mit zwei Leerzeichen und der mit `bulk x` sind in der Ablage, `bulk x` (Probe 3 von `bulk-name-sonderzeichen`) hat keine Datei |
| Nachricht 0x07 von `bulk-name-feste-typen` (72 Byte) | in beiden Instanzen byte-gleich und gleich `typen-fest.hex` und dem Hexliteral in `BulkLoadReaderReferenceClientTest` und `MessungBulkBelegTests`; `0x38` INT, `0x34` SMALLINT, `0x30` TINYINT, `0x32` BIT je ohne Längenbyte; Zeile 2: `ffffffff`, `feff`, `ff` (255, ohne Vorzeichen), `00`; Zähler 2; die `DONE`-Antwort (`fd 1000 f000 0200000000000000`) mit Befehl `0x00f0`, Zähler 2; Server-Antworten beider Instanzen gleich |
| Grenzen des Messprotokolls | in M-33 bis M-36 benannt: vier Teile, Länge 128, Zeichen außerhalb der Basic Multilingual Plane, Zeilenumbruch, Schreibweise des Schemas, mssql-jdbc, andere Spaltentypen |

## Bindungstest (SHA-256)

* **Kotlin `BulkCopyFormsTest.die Dateien der Probetexte tragen die Summen der aufgezeichneten Proben`** hält die Bytes der zehn Dateien gegen eine Tabelle. **.NET `MessungBulkBelegTests.Die_Probetexte_der_Tests_des_Kotlin_Moduls_sind_Zeichen_fuer_Zeichen_die_aufgezeichneten_Proben`** hält dieselbe Tabelle (je Datei, Szenario, Index der Probe) gegen den dekodierten Text der Ablage in **beiden** Instanzen (Theorie `Stufen`). Beide Tabellen enthalten dieselben zehn Summen (je einmal in beiden Dateien gefunden und gegen `sha256sum` der Dateien geprüft).
* **Er trägt.** Mutation A: eine Ziffer der Summe in der .NET-Tabelle geändert, frische Stage `test` rot (2 von 136: Instanz 2022 und 2025, mit gefundener Summe im Text). Mutation LF (oben): die Kotlin-Seite wird durch eine Zeilenenden-Änderung rot, und zwar nur durch den Summentest.
* **Grenze (benannt, unverändert):** wer beide Seiten (Tabelle in beiden Dateien) und die Datei zugleich ändert, wird nicht gefangen; die Ablage selbst ist durch die Tests ebenfalls nur mit der Summe verankert, nicht durch eine Quelle außerhalb des Repos. Die Texte von `bulk x` (Fall 3 der Sonderzeichen) sind nicht gebunden, weil keine Datei von ihnen besteht.

## DoD, Zeile für Zeile

| DoD-Zeile | Beleg | Urteil |
|---|---|---|
| **Liefer-Punkt 1, Messung** (`dbo.t`, `[dbo].[t]`, Name mit `]`, 2022 und 2025, Messprotokoll; Tabelle mit INT, SMALLINT, TINYINT, BIT und Nachricht 0x07) | Ablage `bulk-name-schema` (`dbo.bulk_ns`, `[dbo].[bulk_ns]`, `"dbo"."bulk_ns"`), `bulk-name-sonderzeichen` (`bulk]x` als `[bulk]]x]`), `bulk-name-feste-typen`; je Instanz identische Probetexte, Rohbytes selbst gelesen; Messprotokoll M-33 bis M-36 mit Bedingungen, Grenzen, drei Läufen der Instanz 2022 benannt (Ablage = letzter Lauf) | erfüllt |
| **Liefer-Punkt 2, Erkennung und Antwort** (`BulkCopyForms` erkennt die gemessenen Formen; Test mit dem aufgezeichneten Text; `SqlBulkCopy`-Szenario mit Schema im Integrationslauf) | `BulkCopyForms.recognize` (ein oder zwei Teile, `]]`, `''`, Suche im genannten Schema); 9 Fälle `BulkCopyFormsTest` mit den zehn aufgezeichneten Texten; `make e2e` Stufe 3: 13 von 13, vier Fälle mit Schema und mit `s1.bulk]ziel'x`, unbekanntes Schema; Leser der festen Typen gegen die Nachricht aus M-36 (`BulkLoadReaderReferenceClientTest`, Z8 des Reviews rot) | erfüllt |
| **Liefer-Punkt 3, Nachzug** (`SPEC-036` und Nutzer-Doku nennen die Grenze nicht mehr) | die alte Grenze („Tabelle ohne Schema, Name ohne `]` und `'`“) steht nirgends mehr (Suche in `spec/`, `docs/user/`, `docs/plan/kompatibilitaetsmatrix.md`, `README.md`); fünf Dateien in `docs/user/` gelesen, drei geändert; `SPEC-036` Form B nennt die Setzungen und die neuen Grenzen (drei Teile, 208-Stelle, mssql-jdbc). Die DoD-Zeile spricht nur von der alten Grenze; die neuen Grenzen sind benannt und tragen (drei Teile) einen Register-Eintrag | erfüllt, mit V-1 |
| `make gates` grün | `make gates FORCE=1` Exit 0 (29 s, aus dem Cache) und `make clean-build` Exit 0 (Frischbeleg, 1349 Tests); `make e2e` und die .NET-Stage grün | erfüllt |
| Review durchgeführt, Report unter `docs/reviews/` | `2026-11-11-d4331dc4-review-…` (0 HIGH, 3 MEDIUM, 4 LOW, 3 INFO); alle zehn Findings in `82f62eae` behoben (nachgeprüft: F-1 durch R/Parser, F-2 `SPEC-036` nennt 208 mitten im Batch, F-3 Plan-Tabelle und Kopf, F-4 MC, F-5 Bindung, F-6 Register, F-7 Klassen-KDoc, F-8 Grenze im Satz, F-9 MD, F-10 Zustandssatz) | erfüllt |
| Haken der Liefer-Punkte setzt der Planner | keine `[x]` im Slice (0 Treffer); die Zeile selbst bleibt offen, bis der Planner sie nach diesem Bericht setzt | richtig offen, nicht von der Verifikation zu setzen |
| Doku-Update, falls ein öffentlicher Vertrag berührt | `SPEC-001`, `SPEC-036`, `SPEC-040`, drei Nutzer-Dokumente, Messprotokoll, Matrix; `make flaeche` grün (keine neue öffentliche Fläche); `harness/README.md` unverändert (Zellen-Grenze 200 Zeichen berührt keine Zelle) | erfüllt |
| Closure-Notiz mit Steering-Loop-Lerneintrag | §7 „Wird bei der Closure gefüllt“ | offen, Planner |
| Beobachtungs-Register fortgeschrieben (oder Antwort in §7) | neuer Eintrag `bulkcopy-drei-namensteile-gemessen-nicht-gebaut` (Dateien `observation.md`, `state.md`, `evidence/review-2026-11-11-d4331dc4-bulkcopy-tabellenname.md`), Zeiger in zwei bestehenden Einträgen nachgezogen; Antwort in §7 steht aus | teilweise, Planner (V-2) |
| Jedes Risiko aus §6 trägt einen Ausgang | §6: „offen bis zur Closure“ | offen, Planner (V-3) |
| Die drei Paarungen (Anker · Folge-Slice · Register) | kein Anker (nach Stand keine `liegt in`-Regel), kein Folge-Slice, ein Register-Eintrag (Pfad aus `done/` löst, `evidence/` nicht leer); wird nach dem `git mv` vom Planner geprüft | offen, Planner |

## Entscheidungs-Konformität

* [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (Stapel-Callback): `SimTable.onInsertBatch` und der Weg der Nachricht 0x07 sind im Diff unberührt (in `src/main` nur `BulkCopyForms.kt`, KDoc in `BulkColumnType.kt` und `BulkLoadReader.kt`).
* [ADR-0044](../plan/adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md) (Atomarität): unberührt; das zweite Ziel `s1.bulk]ziel'x` im Integrationsserver hat einen eigenen Speicher je Tabelle, der Schlüsselverstoß-Fall läuft grün.
* Hard Rules `AGENTS.md` §3.1 bis §3.8: kein Host-Aufruf, keine Suppression (detekt grün), kein `Accepted`-ADR geändert, `spec/architecture.md` unberührt, git-mv-Regel eingehalten (`slice-mv` als eigene Commits), §3.8 siehe V-4. `SPEC-001` wurde im Kopf des Slice als berührt genannt und im Absatz zu den festen Typen fortgeschrieben.

## Plan-Tabelle §3 gegen Diff

Alle 45 Dateien des Diffs `7412984a..82f62eae` sind in der Tabelle gedeckt: Quelle (3 Dateien), Tests und Ressourcen (zwei Klassen, 9 Probetexte, `typen-fest.hex`), Integration (`SqlBulkCopyTests.cs`, `IntegrationServer.kt`, `simulationstest.sh`), Messung (`MessungBulkTests.cs`, `MessungBulkBelegTests.cs`, sechs Ablage-Dateien, `vergleichslauf-d.mk`), Planungsdokumente (Roadmap, Welle, zwei Register, Eltern-Slice, älterer Report), Register-Verzeichnis, `spec-abdeckung.md`, Spezifikation, `docs/user/` (drei Dateien), Matrix, Messprotokoll. Nicht in der Tabelle, aber im Diff: der Review-Report selbst (Artefakt der Review-Rolle) und `docs/reviews/…6c0f7f62` ist genannt. `SPEC-040` steht im Kopf. Zahlen im Plan stimmen mit dem Diff (neun `probe-*.sql`, sechs Ablage-Dateien, 13 Tests, 7 Tabellen). Zweite Fundstellen: `tools/mk/vergleichslauf-d.mk` (19 Szenarien), `MessungBulkBelegTests` (19 Dateien), Messprotokoll (19 Dateien, Bytes), Matrix (12 Nachrichten, 6 verschiedene), `SqlBulkCopyTests`-KDoc (M-33 bis M-36): konsistent; Suche nach „sechs Tabellen“, „ohne Schema“, „nicht erkannt“ in `spec/`, `docs/user/`, `README.md`: nur die beabsichtigten Treffer.

## Findings der Verifikation

| ID | Klasse | Befund | Wer |
|---|---|---|---|
| V-1 | LOW | Start-Trigger des Slice (§4) verlangt „ein Verantwortlicher ist eingetragen“; der Kopf trägt weiter `**Verantwortlich:** — bis zur Priorisierung.`, der Slice liegt aber in `in-progress/` (die Roadmap nennt ihn „in Arbeit“). Die DoD-Zeile „Nachzug“ spricht nur von der alten Grenze, die neuen Grenzen (drei Teile, 208-Position, mssql-jdbc) stehen in Spezifikation und Register, nicht in der DoD-Zeile. | Planner: Verantwortlichen eintragen (oder Begründung in §7), DoD-Wortlaut belassen |
| V-2 | LOW | Register `bulkcopy-drei-namensteile-gemessen-nicht-gebaut`: `observation.md` trägt die Überschrift „Benannt, nicht gezählt“ und `state.md` „offen“, zugleich liegt `evidence/review-2026-11-11-d4331dc4-bulkcopy-tabellenname.md` mit `**Vorgang:**` und `**Fund:**` — das Register zählt die Zahl der gültigen Evidence-Dateien (Zähler = 1). Einordnung: Ein Review-Report ist nach `modul-06-roadmap.md` ein abgeschlossener Vorgang und taugt als Beleg, die Form stimmt (Dateiname = Kennung des Vorgangs); der Beleg zählt **einmal**. Die Lage-Prüfung („dort, wo seine Klasse abgeschlossen wird“) ist erfüllt: der Report liegt in `docs/reviews/`. Der Widerspruch ist der Satz „benannt, nicht gezählt“ in der Beobachtung selbst. Der Slice ist noch nicht geschlossen; die Closure des Slice ist **derselbe** Vorgang (derselbe Fund), eine zweite Evidence-Datei mit der Slice-Kennung würde dasselbe Auftreten ein zweites Mal zählen. | Planner: Satz „Benannt, nicht gezählt“ in `observation.md` auf „1×, Vorgang Review d4331dc4“ berichtigen, keine zweite Evidence-Datei für denselben Fund bei der Closure anlegen; Stand bleibt `offen` mit dem Re-Evaluierungs-Trigger |
| V-3 | INFO | Risiko §6 („Der Referenzclient ändert den Text der Probe bei anderen Namen …“) ist eingetreten (Kollationsform mit/ohne Schema, `]]`, `''`, Form mit drei Teilen) und durch die Erkennung gedeckt, mit einer Grenze (drei Teile, Register). | Planner: Ausgang „eingetreten“ mit Zeiger auf M-33 bis M-35 und das Register in §6 und §7 |
| V-4 | LOW | `AGENTS.md` §3.8 (Aussage ≤ Beleg): die KDoc der Konstante `PART` in `BulkCopyForms.kt` sagt „ein Name von einer Million Zeichen wird ohne `StackOverflowError` erkannt (`BulkCopyFormsTest`)“; der Test geht bis 100 000 Zeichen (Plan §3 und KDoc des Tests: 10 000 und 100 000). Die Aussage ist wahr (gemessen in dieser Verifikation: 1 Mio Zeichen, 5 bis 83 ms), der genannte Beleg trägt sie nicht. | Implementer (kleinste Änderung: „einhunderttausend“ oder Test auf 1 Mio erweitern) |
| V-5 | INFO | Zwei Aussagesätze nennen die Gegenseite nicht, tragen aber den Client im Satz: `docs/user/referenz-konfiguration.md` Zeile 69 („an `SqlBulkCopy` … gemessen, … auch mit Schema und Sonderzeichen“) und die Matrix-Zeile BULK_LOAD („der Katalog-Batch von `SqlBulkCopy` ist beantwortet …“). `referenz-konfiguration.md` (Zeile „Probe der Zieltabelle“) nennt `ERROR` 208 ohne die Stelle (dort steht nicht, dass dieser Server den Fehler vor den Ergebnissen sendet; `einbettung-und-betrieb.md` und `SPEC-036` tragen es). Keine Falschaussage. | — (vermerkt) |

Keine HIGH, keine MEDIUM. Kein Befund am Parser außer V-4.

## Zusage gegen Befund

Die Zusage des Slice (Namen mit Schema und mit `]` oder `'` werden von der Probe der Zieltabelle angenommen, am echten Server gemessen) hält: die Messung ist echt (Rohbytes in beiden Instanzen gleich und selbst gelesen), die Erkennung deckt die gemessenen Formen (zehn aufgezeichnete Texte, Differentialtest, Mutationen R, MC, MD, LF rot), der Referenzclient lädt im Integrationslauf (13 von 13) und `d-migrate` bleibt für Namen ohne Schema unverändert (Import, Transfer, Export, Schlüsselverstoß). Nicht belegt, aber benannt: `mssql-jdbc` mit Schema und Sonderzeichen, drei Teile (gemessen, nicht gebaut), 208 mitten im Batch (Abweichung dieses Servers).

## Bedingungen für die Planner-Closure

1. **Haken** der drei Liefer-Punkte setzen (Verifikation: erfüllt) sowie `make gates`, Review und Doku-Update; die Zeilen Closure-Notiz, Register-Antwort, Risiken und Paarungen schließt der Planner selbst.
2. **V-4** vor der Closure beheben (Implementer, ein Satz oder eine Testzahl) oder als Setzung mit Begründung in der Closure-Notiz führen.
3. **V-1** und **V-2** (Verantwortlicher; „benannt, nicht gezählt“ berichtigen, keine zweite Evidence-Datei für denselben Fund); **V-3** (Ausgang des Risikos in §6 und §7).
4. **Closure-Notiz §7** mit Steering-Loop-Lerneintrag; Kandidaten, die der Slice selbst zeigt: (a) eine Alternation unter einem unbegrenzten `+` in einem Regex rekursiert je Zeichen (`StackOverflowError`) — besitzende Wiederholung und ein Test mit langem Namen; (b) Testtexte aus der Aufzeichnung sind erst durch eine Summe auf beiden Seiten an den Beleg gebunden. Der Zähler läuft über Vorgänge; kein Eintrag ohne `liegt in`-Zielort ist „verkörpert“.
5. **Reihenfolge nach `AGENTS.md` §3.3:** erst der Inhalt (Haken, §7), dann der reine `git mv` nach `done/`, dann Roadmap, Welle und Zeiger nachziehen (`make slice-mv` zieht die Verweise), dann die Paarungen (Register-Pfad aus `done/`).
6. **Zusammenführung mit `main`:** `git merge-tree --write-tree HEAD main` ist gegen `f2db5649` konfliktfrei. Der Slice `slice-drop-constraint-form-d5` berührt Spezifikation, Roadmap, Welle und Messprotokolle: vor der Zusammenführung die Probe wiederholen; Zeilen von `SPEC-036` (Form B und die Katalog-Formen), `SPEC-040` und `docs/messungen/referenzabgleich.md` (Messnummern: dieser Slice belegt M-33 bis M-36, ein paralleler Slice darf sie nicht ebenfalls vergeben), `docs/plan/spec-abdeckung.md` (neu erzeugen mit `make spec-abdeckung` nach der Zusammenführung, wenn beide Zweige Tests geändert haben) und die Roadmap-Zeile „in `in-progress/`“ von Hand abgleichen.
7. Die Messung am echten Server darf nicht als Nachweis für `mssql-jdbc` gelesen werden; die Grenze steht in jedem Aussagesatz, den der Slice berührt (außer den zwei Sätzen in V-5, die den Client nennen).

## Laufzeit

Gesamt rund 30 Minuten (17:15 bis 17:45), davon `make clean-build` 560 s (größter Posten), `make e2e` 302 s (Stufen 1 und 2 mit 69 s und 85 s), Rig-Läufe (Parser, Mutationen R, MC, MD, LF, Differentialtest) rund 6 Minuten, d-migrate mit Bau des Test-Images rund 1 Minute, `make gates FORCE=1` 29 s (Cache), .NET-Stage 38 s (Cache) und 7 s (frisch), Ablage-Dekodierung und Lesen der Dokumente der Rest.
