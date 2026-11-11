# Verifikationsbericht: slice-bulk-load-feste-typen-und-referenzclient (HEAD 6c0f7f62)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: Slice `slice-bulk-load-feste-typen-und-referenzclient` (`in-progress/`, Zweig `worktree-agent-a8329d681f4d810e9`, HEAD `6c0f7f62`, Basis `0b3e990c`). Verlauf: Review `2026-11-08-dcb5196b-review-slice-bulk-load-feste-typen-und-referenzclient.md` (12 Findings), vom Implementer behandelt in `c9769b75`, `e10b802b`, `6c0f7f62`; F-12 liegt beim Planner. `main` steht auf `6d75692a` und ist **nicht** in diesem Zweig (letzter Merge `c1591a6d`; fehlend sind die jüngsten Merges von `main` zu zwei ADR-Entwürfen, laut Auftrag erwartet).
* Maßstab: DoD §2 und Plan §3 des Slice, [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), `SPEC-001`, `SPEC-035`, `SPEC-036`, `SPEC-040`, `SPEC-059`, `AGENTS.md` §3.7 und §3.8. Grundsatz: Spec ist Zielbild, der echte SQL Server die Referenz.
* Verifiziert wird die Zusage, nicht der Diff. Nichts am Produktivcode geändert; der Arbeitsbaum war vor diesem Bericht sauber. Alle Mutationen liefen in Kopien unter dem Scratchpad (`git archive HEAD`). Haken habe ich keine gesetzt (der Slice sieht sie für den Planner vor).

## Eigene Läufe (Belege)

Host: Lastmittel 17 bis 29 durch fremde Läufe (kein ruhiger Host), alles Einzelläufe.

| Lauf | Ergebnis |
|---|---|
| `make clean-build` (Frischbeleg, Stages `build-jar`, `test`, `detekt` ohne Cache) | Exit 0; 1247 `PASSED`, kein Test rot (`:test` 4 min 11 s und 5 min 36 s in zwei Stufen, Jar 1 min, detekt grün) |
| `make gates FORCE=1` | Exit 0, 34 s: Stages aus dem Layer-Cache des vorigen Laufs (Verdrahtung, kein Frischbeleg; den Frischbeleg trägt die Zeile darüber) |
| `make flaeche` | Exit 0 |
| `make docs-check` | Exit 0 (vor und mit diesem Bericht, 1256 Dateien, 0 Befunde) |
| `make matrix-zitate` | Exit 0; 1247 bestandene Testfälle, 318 Zitate, 0 ohne PASSED (frischer Bau, 269 s) |
| `make simulations-integration` | Exit 0; „Alle 8 Tests des Referenzclients bestanden“, darunter die drei `SqlBulkCopyTests` (`Massenladen_mit_SqlBulkCopy_laedt_die_Zeilen_in_den_Bestand_und_zaehlt_sie`, `Mehrere_Nachrichten_je_Batch_laden_alle_Zeilen`, `Schluesselverstoss_endet_mit_2627_…`), 31 s |
| `make e2e` | **erster Lauf Exit 2**: Stufe 7 rot nach 39 s, `SynchronitaetTests.Cancel_und_Command_Timeout_kurz_hintereinander_lassen_die_Folgeabfrage_nur_ihre_Antwort_erhalten` (`Expected: 2, Actual: 0`, Last 27). Der Test ist Gegenstand von `BEO-TDS/wanduhr-tests-flackern-unter-last` (Beleg dort) und berührt das Massenladen nicht. **Wiederholung Exit 0**, 8 Stufen grün, 235 s |
| `d-migrate` 1.12.0 (Digest `sha256:308f2775…`) gegen zwei Testserver aus dem Arbeitsbaum (`make testserver-up` mit `TESTSERVER_NAME=ver-ts-a`/`ver-ts-b`, Ports 24893/24894, danach beide entfernt, `docker ps -a` ohne Rest) | `data import` (Standardpfad `INSERT BULK`) Exit 0, 2 eingefügt; `data export` 2 Zeilen; zweiter `data import` Exit 5 (`Violation of PRIMARY KEY constraint. Cannot insert duplicate key.`), Export danach byte-gleich (`cmp`); `data transfer` A nach B Exit 0, Export von B byte-gleich dem von A |
| Echter SQL Server (`make vergleichslauf-d`) | **nicht gefahren**: der Slice verlangt keinen neuen Lauf am echten Server (Messreihe M-8 bis M-12 liegt vor, der Slice liest sie nur). Die Messung für Namen mit Schema und Sonderzeichen verlangt der neue Träger-Slice |

### Mutationen (selbst gefahren, vollständiger Lauf der Stage `test`)

| Mutation | Wirkung gemessen |
|---|---|
| A1 `BulkLoadReader.metadata`: `reader.u16Le() != 0` → `< 0` (Befund F-6 des Reviews, dort grün) | **rot**: `BulkLoadReaderReferenceClientTest > zwei Bytes ungleich 0 hinter der Spaltenzahl sind ein Fehler des Lesers` (neuer Test); F-6 ist geschlossen |
| A2 `BulkValueReader.integer`: Vorzeichenerweiterung `size > TINY` → `>= TINY` (TINYINT mit Vorzeichen) | rot: `BulkLoadReaderTest > die Ganzzahlen folgen Groesse und Vorzeichen des Typs`, dazu der Handvektor der festen Typen |
| A3 `BulkFixedType.INT4` (0x38) entfernt | rot: `… ein unbekannter fester Typ und ein abgeschnittener fester Wert sind Fehler des Lesers`, `… die festen Typen TINYINT, BIT, SMALLINT, INT und BIGINT werden nach der Beschreibung gelesen` |
| B `BulkFixedType.of` liefert immer `null` (alle festen Typen entfernt) | rot, 6 Tests: alle vier Messnachrichten-Tests (`bulk-typen-null`, `bulk-verstoss-schluessel`, `bulk-mehrere-nachrichten`, Nutzlast am Testserver), Handvektor der festen Typen, unbekannter fester Typ |

A1 bis A3 liefen in einem Bau; die roten Tests sind je Mutation verschieden und zuordenbar. Im selben Lauf fiel einmal `ListenerAbortTest > der Listener ruft den Abbruch nach der Frist und wartet CANCEL_TIMEOUT ab` (Last 22 bis 27; Klasse `wanduhr-tests-flackern-unter-last`, im Frischbeleg `make clean-build` bestanden). Die Mutationen M1 bis M7 des Reviews habe ich nicht wiederholt (nicht Gegenstand der Nachbesserung außer F-6).

## Behandlung der 12 Findings des Reviews (am Stand `6c0f7f62` nachgelesen)

| Finding | Stand | Beleg |
|---|---|---|
| F-1 HIGH Kommentar verworfene Alternative (`BulkCopyProbe`) | behoben | KDoc nennt nur Aufzeichnung (`DECIMALN(10,2)`, `0x11`) und Test; Test-KDoc `BulkCopyProbeTest` bereinigt |
| F-2 MEDIUM `SPEC-001` zwei Absätze | behoben | ein Absatz; Typenliste, Messdeckung (BIGINT fest, `INTN` 2/4, `BITN`, `NUMERICN`, `NVARCHAR`, `DATEN`, `TIMEN`, `DATETIME2N`), Handvektoren, DONE 8/12, gekürzte Nachricht und Zurücksetzen von „Massenladen erwartet“ nach IGNORE stehen darin; Kopf des Slice nennt `SPEC-001`. Ich habe die Typliste gegen die Hex-Dateien geprüft (`typen-null`: `0x7f`, `INTN` 4/2, `BITN`, `NUMERICN`, `NVARCHAR`, `DATEN`, `TIMEN`, `DATETIME2N`; DONE je 8 Byte; zwei Nullbyte hinter der Spaltenzahl): stimmt |
| F-3 MEDIUM Plan-Tabelle §3 und Kopf | behoben | §3 nennt `IntegrationServer.kt`, `simulationstest.mk`, `BulkCopyProbeSets.kt`, `TdsType.kt`/`TypeInfoWriter.kt`, `simulation/SimulationBackend.kt`, `harness/README.md`, `spec-abdeckung.md`; jede Datei des Diffs liegt unter einer Zeile (Abgleich von `git diff --stat main...HEAD` gegen §3: keine Datei außerhalb, `welle-testserver-manuell.md` und `roadmap.md` sind Planner-Dateien) |
| F-4 MEDIUM falsche Datei für `bulk_ziel` | behoben | Klassenkommentar nennt `IntegrationTables.bulkTarget()` in `IntegrationServer.kt` |
| F-5 MEDIUM Zusage „ruft keinen Callback“ | behoben | `BulkLoading.kt`: „fragt höchstens den Katalog-Anbieter und ruft keinen Callback für Zeilen“ |
| F-6 MEDIUM Prüfung der Nullbyte ohne Test | behoben | neuer Test, Mutation A1 rot |
| F-7 LOW Fuzz-Basis | behoben | `BulkLoadFuzzTest` hat fünf Ausgangsnutzlasten (feste Typen, `typen-null`, `verstoss-schluessel`); Test im Frischbeleg grün |
| F-8 LOW „zwei Tabellen“ | behoben | „sechs Tabellen ohne Schema“ in KDoc, `SPEC-036`; Grep nach „zwei Tabellen“ in `spec/` und `docs/user/` ohne Treffer |
| F-9 LOW Messzeile ohne Bedingung | behoben, knapp | Zelle nennt „Einzelmessung 2026-11-07, Last unbekannt“; Länge der Zelle „Tut was“ 199 Zeichen (Grenze 200) |
| F-10 INFO Abschneide-Erkennung | zur Kenntnis | in `SPEC-001`, KDoc `done`, Test benannt |
| F-11 INFO Namen mit Schema ohne Träger | behoben im Entwurf | neuer Slice `next/slice-bulkcopy-tabellenname-mit-schema-und-sonderzeichen.md`; §6 des Slice verweist darauf (Formprüfung unten) |
| F-12 INFO Zustandsfelder, Merge mit `main` | **offen beim Planner** | siehe V-3 und V-4 |

## DoD-Prüfung, Zeile für Zeile (Haken setzt der Planner)

**Liefer-Punkt 1, Leser, feste Typen:** erfüllt.
* Die fünf verschiedenen Messnachrichten (`typen-null`, `verstoss-schluessel`, `mehrere-1` bis `-3`; 327, 108, 28 835, 31 049 und 15 549 Byte) stehen als Hex-Dateien unter `src/test/resources/messung/bulk-referenzclient/` und ergeben im Leser Zeilen (`BulkLoadReaderReferenceClientTest`, vier Tests plus „jede Teilnachricht ist Fehler“); Mutation B macht sie rot. Dass die fünf Dateien byte-gleich den 10 Nachrichten beider Instanzen sind, hat der Review geprüft (in diesem Lauf nicht wiederholt, Unterschied zur eigenen Prüfung benannt); ich habe die Spalten- und DONE-Struktur der drei geprüften Dateien selbst gelesen.
* Nur BIGINT (`0x7f`) ist gemessen. INT `0x38`, SMALLINT `0x34`, TINYINT `0x30`, BIT `0x32` stehen als Handvektor nach der Beschreibung von `MS-TDS`, ohne Mitschnitt, in KDoc von `BulkFixedType` und `BulkLoadReader`, in `SPEC-001` und im Testnamen („werden nach der Beschreibung gelesen“) gekennzeichnet; die Aussage trägt nicht weiter als der Beleg (`AGENTS.md` §3.8). Ein Lauf des Referenzclients mit nicht nullbarer INT-Spalte fehlt (`bulk_ziel.Zahl` ist nullbar); das Risiko „weitere feste Typen“ in §6 bleibt „offen bis zur Closure“ und braucht dort seinen Ausgang.
* Die Deutung der zwei Nullbyte hinter der Spaltenzahl ist als **nicht belegt** benannt (`SPEC-001`, `SPEC-036`, KDoc von `BulkLoadReader` und `BulkCopyProbe`); der Leser verlangt in der Form mit Lücke beide Bytes gleich 0 (Test, Mutation A1) und nimmt die Form ohne Lücke an, weil die Nachricht des Referenzclients an den Testserver sie nicht trägt. Das ist eine Setzung, keine Messung: ob die zwei Bytes bei anderen Clients oder Werten etwas anderes zählen, ist nicht belegt.
* Negativfälle: abgeschnittene Nachricht (jede Teilnachricht außer der um vier Byte gekürzten, die einer vollständigen mit DONE von 8 Byte gleicht und angenommen ist, benannt in `SPEC-001`), unbekannter fester Typ, abgeschnittener fester Wert enden als `TRUNCATED`/`INVALID_TYPE`. Bewertung der Ausnahme: kein Inhalt wird verkürzt angenommen, aber die oberen vier Byte der DONE-Zeilenzahl fehlen; [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) bleibt unberührt (eine Antwort je Anfrage, `RequestLoop`).

**Liefer-Punkt 2, Katalog-Batch:** erfüllt.
* `BulkCopyForms` erkennt den Wortlaut der Probe (aufgezeichnet in `probe-bulk_typen.sql`, laut Review in allen sechs Aufzeichnungen beider Instanzen bis auf den Namen gleich); `BulkCopyProbe` baut die Antwort aus vier Ergebnissen; für `bulk_schluessel` ist sie bis auf die zwei Nullbyte byte-gleich der Aufzeichnung (`BulkCopyProbeTest`, Hex `antwort-probe-verstoss-schluessel.hex`). Die Form steht in `SPEC-036` (Form B). Der Test läuft im Frischbeleg grün.
* Die Grenze (Name mit Schema, Name mit `]` oder `'`: `TDS_UNSUPPORTED_SQL`) ist in `SPEC-036`, KDoc und Nutzer-Doku ehrlich gesagt und hat den Träger-Slice.

**Liefer-Punkt 3, Beleg am Referenzclient:** erfüllt.
* `SqlBulkCopyTests` (3 Fälle) laufen gegen den Testserver, `make simulations-integration` 8 von 8 (zuvor 5); Zähler (`SqlRowsCopied` 5), Lesen danach und 2627 beim Schlüsselverstoß sind belegt; Mutation 6 des Reviews (Duplikatprüfung entfernt) war rot.
* `make referenztest-dotnet` ist Teil von `make gates FORCE=1` (Exit 0, aus dem Layer-Cache des Frischlaufs von `clean-build` nicht erfasst: dieser fährt `.NET` nicht). Der .NET-Frischbeleg fehlt in diesem Lauf; die Tests des Referenzclients selbst liefen frisch in `make simulations-integration` und `make e2e`.
* Die Grenze „die Nutzlast des Referenzclients wird nicht gelesen“ ist in `spec/`, `docs/user/` und der Matrix entfernt (Grep ohne Treffer); die Matrix-Zitate bestehen (`make matrix-zitate`).
* `SPEC-001` und `SPEC-036`, **Form B und Form M beide erhalten** und widerspruchsfrei: Form B (Zeile in der Formtabelle, Probe von d-migrate und von `SqlBulkCopy`, Nutzlast) und Form M (`MERGE … OUTPUT $action`, Messung M-25 bis M-27) stehen unverändert nebeneinander; der Diff berührt Form M nicht. Die Aussage „Dezimalzahlen mit Größe 17 für jede Precision“ siehe V-1.

**Nachweis-Pflichten:**

| Pflicht | Stand |
|---|---|
| `make gates` grün | ja (`FORCE=1`, Exit 0; Frischbeleg `make clean-build` Exit 0) |
| Review-Report liegt vor | ja (`docs/reviews/2026-11-08-dcb5196b-review-…`; kein Re-Review der Nachbesserung, die Nachbesserung ist hier gegen die Findings geprüft) |
| Haken nicht vom Implementer gesetzt (`BEO-TDS/haken-vor-verifikation-gesetzt`) | bestätigt: im Slice kein `[x]` (Grep) |
| Doku-Update | ja (`spec/`, Matrix, `docs/user/*`, `harness/README.md`) |
| Closure-Notiz, Register, Risiko-Ausgänge, Paarungen | offen, Sache des Planners: §7 steht auf „—“, das erste Risiko aus §6 trägt „offen bis zur Closure“ |

## Lifecycle, Träger-Slice, Plan-Tabelle

* Lifecycle: der Slice liegt in `in-progress/`, `roadmap.md` führt ihn dort, `welle-testserver-manuell.md` zeigt auf den neuen Pfad; die Moves sind eigene reine Commits (`027c8221`, `77f7894f`).
* Neuer Slice `next/slice-bulkcopy-tabellenname-mit-schema-und-sonderzeichen.md` (**Formprüfung, Entwurf; der Planner bestätigt**): Lifecycle `open/`, alle acht Abschnitte, drei Liefer-Punkte (Messung, Erkennung und Antwort, Nachzug), Nachweis-Pflichten, Trigger mit Rückführungen, Closure-Trigger, Risiko mit Ausgang offen, §7 leer („Wird bei der Closure gefüllt“), §8 mit Beobachtungs-Zeiger. Er sagt, die Messung am echten Server verlange die Freigabe des Nutzers (EULA), und legt sie nicht selbst fest. Der Autor ist als Entwurf des Implementers gekennzeichnet. Es fehlt: Verantwortlicher (ausdrücklich „bis zur Priorisierung“), Eintrag in die Slice-Tabelle von `welle-testserver-manuell.md` und in die Kantenliste (V-3).
* Plan-Tabelle §3 gegen Diff: stimmt (F-3).
* `harness/README.md`: nur die Zeile von `make simulations-integration` geändert; die Zelle „Tut was“ hat 199 Zeichen; Verweis-/Bindungsspalte unverändert.

## Findings der Verifikation

### V-1 LOW — „Größe 17 für jede Precision“ trägt weiter als der Beleg

* quelle: `AGENTS.md` §3.8
* pfad: `spec/spezifikation.md` · „Dezimalzahlen mit Größe 17 für jede Precision, so die Aufzeichnung“ (Form B, ebenso das KDoc von `BulkCopyProbe` „für jede Precision“)
* befund: Belegt ist die Größe 17 für `DECIMALN(10,2)` in der Antwort des echten Servers und `NUMERICN` mit Größe 17 in der Nachricht `typen-null`; dass der Server für jede Precision 17 meldet, ist nicht aufgezeichnet. Die Formulierung „so die Aufzeichnung“ nennt eine Messung für mehr, als sie zeigt; eine Setzung ist nicht gekennzeichnet. Die Größe ist durch `DecimalType.bytes` zudem an einen Test gebunden (`BulkCopyProbeTest`), der die Setzung festschreibt.
* verifizierbar: ja (Lesen der Aufzeichnung: eine Dezimalspalte)

### V-2 INFO — Frischbeleg des .NET-Referenztests fehlt in `clean-build`

* befund: `make clean-build` fährt Jar, Tests und detekt, nicht `referenztest-dotnet` (siehe `harness/README.md`). Der Frischbeleg für `SqlBulkCopyTests` sind `make simulations-integration` und `make e2e` (beide grün, frische Läufe des Containers); `make gates FORCE=1` hat den .NET-Test aus dem Cache gezeigt. Kein Widerspruch zur DoD, nur die Grenze dieses Berichts.

### V-3 INFO (Planner) — neuer Slice nicht in der Welle eingetragen

* pfad: `docs/plan/planning/welle-testserver-manuell.md` und Drift-Log in `docs/plan/planning/in-progress/roadmap.md`
* befund: `slice-bulkcopy-tabellenname-mit-schema-und-sonderzeichen` steht in beiden nicht (Grep); sein Start-Trigger nennt `done/` des Elternslice. Eintrag und Kante gehören dem Planner.

### V-4 INFO (Planner) — F-12 des Reviews unverändert offen

* pfad: `docs/plan/planning/in-progress/roadmap.md`, `docs/plan/planning/observations/BEO-TDS/messung-am-echten-sql-server-fehlt/state.md`, `…/mitschnitt-luecken-ohne-traeger/state.md`
* befund: Die Zustandszellen, die „die Nutzlast des Referenzclients liest der Testserver nicht“ behaupten, sind nicht nachgezogen; das gehört zur Closure des Planners. Der Zweig enthält `main` bis zum Merge `c1591a6d`, nicht den jüngsten Stand `6d75692a`; nach dem Merge sind `make gates`, `make simulations-integration` und die Abdeckungsmatrix (`docs/plan/spec-abdeckung.md`, erzeugt) neu zu fahren.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| Öffentliche Fläche (`make flaeche`, `SPEC-059`) | geprüft, ohne Befund: kein neuer öffentlicher Name; `bulkProbe` intern |
| Hard Rules (Suppression, Docker-only, Schicht) | geprüft, ohne Befund (`detekt` im Frischbeleg grün) |
| Haken vor Verifikation | geprüft, ohne Befund |
| Messnachrichten byte-genau | Struktur selbst gelesen, Gleichheit mit den Mitschnitten beider Instanzen vom Review übernommen |

## Gesamturteil

**Closure-fähig aus Sicht der Verifikation: ja, unter den Bedingungen B1 bis B3 des Planners.** Alle drei Liefer-Punkte sind belegt: Leser und Probe bestehen die Messnachrichten und die Aufzeichnung, `SqlBulkCopy` lädt, zählt und endet mit 2627 am Testserver, `d-migrate` (Import, Export, doppelter Schlüssel, Transfer) läuft im Standardpfad, die Mutationen sind rot (auch die Lücke F-6 des Reviews ist geschlossen), und der Frischbeleg ist grün. Die Grenzen sind benannt (feste Typen INT/SMALLINT/TINYINT/BIT ohne Mitschnitt, Zwei-Nullbyte-Deutung unbelegt, Namen mit Schema ohne Messung mit Träger-Slice).

* B1: Closure-Notiz §7 mit Steering-Loop-Eintrag, Ausgang für beide Risiken aus §6 (das erste: „weitere feste Typen“ bleibt ohne Messung offen oder erhält einen Träger).
* B2: V-3 und V-4 (Welle-Tabelle, Roadmap-Zeilen, Zustandsfelder der Register) und Bestätigung des Entwurfs des Träger-Slice.
* B3: nach dem Merge mit `main` die Gates und den Integrationslauf wiederholen; V-1 einschränken oder als Setzung kennzeichnen.

Finding-Klassen: Aussage breiter als der Beleg (V-1) · Zustandsfeld nennt überholte Grenze (V-4).
