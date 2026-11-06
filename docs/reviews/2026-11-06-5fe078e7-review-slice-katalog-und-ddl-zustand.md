# Review-Report: slice-katalog-und-ddl-zustand — 2026-11-06

**Review-Art:** Code — gegen Plan, ADR und Hard Rules (nicht gegen die DoD; das ist die Frage des Verifiers).

**Gegenstand:** Zweig `slice-katalog-und-ddl-zustand`, Commit `5fe078e7` auf `fd6d5286` (`git diff fd6d5286..HEAD`, 27 Dateien).

**Skill:** `.harness/skills/reviewer.md` @ `5fe078e7` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-06

**Eingangs-Kontext:**

- `slice-katalog-und-ddl-zustand` (Plan §3, DoD, Risiken §6)
- [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (Accepted: Katalog-Anbieter, Strukturänderungs-Callback, Fassung F, Z3, `maxLength`), [ADR-0026](../plan/adr/0026-oeffentlichkeit-simulations-backend-und-katalog.md), [ADR-0034](../plan/adr/0034-testserver-als-gradle-modul.md)
- `SPEC-035`, `SPEC-036`, `SPEC-040`, `SPEC-059`, `TDS-FA-11.a`; `TDS-FA-11`, `TDS-FA-12`, `TDS-QA-07`
- `AGENTS.md` §3.1 bis §3.8; Nutzergrundsatz 2026-11-06 „Spec ist Zielbild“ (echter SQL Server ist die Referenz)

**Eigene Läufe (Worktree, Stand `5fe078e7`):**

| Lauf | Ergebnis |
|---|---|
| `make gates` | Exit 0 (Klassen `werkzeug` und `tests` liefen, kein Beleg vorhanden; `stempel-eingaenge` ok, `gate-zuordnung` 33 Fälle) |
| `make flaeche` | Exit 0 |
| `make e2e` | Exit 0, 8 Stufen, 147 s |
| `make matrix-zitate` | 1009 bestandene Testfälle, 0 Zitate ohne PASSED |
| Mutation „Planer liest nur einmal“ (`StatementPlanner` hält den Katalog aus dem Bau) | `make test` rot: `FlaecheEmbeddingTest > einIntegratorLegtTabellenUeberAnbieterUndStrukturCallbackAn` (Gradle brach danach ab, die Unit-Tests liefen in diesem Lauf nicht; dass `SimulationBackendCatalogTest` allein ebenfalls rot wird, ist nicht gefahren) |
| Mutation `nvarchar`-Länge in Zeichen statt Byte | `make test` rot: `jeder Typ von SqlType hat einen Typnamen im Katalog`, `die Spalten einer Tabelle sind der Katalog des Servers` |
| Mutation Transaktionsregex ohne Anker (`containsMatchIn` statt `matches`) | `make test` rot: `CatalogFormsTest > Transaktionstexte sind Form C und nur sie` |
| Mutation `@Synchronized` an `TableStore.catalog`/`replace` entfernt | `make test` **grün** (Exit 0), siehe F-1 |
| d-migrate 1.12.0 (Image `sha256:308f2775…`) gegen `make testserver-up` aus dem Arbeitsbaum (Port 24991, `TEST_UNENCRYPTED`) | `schema reverse` Exit 0, `--include-all` Exit 0, `compare` gegen die Rückgabe und gegen `sim.yaml` Exit 0 (`IDENTICAL`); `schema migrate --execute` mit neuer Tabelle, neuer Spalte `Email`, `NVARCHAR(MAX)` (`notizen`): Exit 0, `status: ok`, 3 Anweisungen, `transactionRolledBack: false`; `compare` danach Exit 0; zweiter `migrate` `no_op`; `reverse` danach zeigt `notizen.Text` ohne `max_length` und `Email` mit 200; Soll mit `identifier` Exit 1 (`identifier -> biginteger`). Die Statementzahlen wurden nicht gezählt (siehe F-5) |

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Wird `@Synchronized` von `catalog()` und `replace()` entfernt, bleibt `make test` grün (gefahren, Exit 0): kein Test greift nebenläufig zu. Zugleich ist die Folge „prüfen im Planer, ändern im Callback“ nicht atomar: zwei Sitzungen, die `CREATE TABLE` derselben neuen Tabelle oder `ALTER TABLE … ADD` an derselben Tabelle gleichzeitig senden, passieren beide die Prüfung gegen denselben Stand, und `replace` überschreibt still (zweites `CREATE TABLE` ersetzt die erste Tabelle statt abgewiesen zu werden, bei `ADD` geht eine der beiden Spalten verloren). Der Kopf von `TableStore` sagt „Der Zugriff ist synchronisiert“, das trägt für den einzelnen Aufruf, nicht für die Folge. | `AGENTS.md` §3.8, Reviewer-Skill „Eigencode, dessen Entfernung keinen Test rot macht“ | `testserver/kotlin/io/github/pt9912/tdswire/testserver/TableStore.kt` · `Der Zugriff ist synchronisiert; eine Strukturänderung ersetzt die Tabelle als Ganzes` | ja — Mutation (gefahren); der Verlust bei zwei Verbindungen ist nicht gefahren | Synchronisation ohne Test, Prüfung und Änderung nicht atomar |
| F-2 | MEDIUM | Die Plan-Tabelle in Slice §3 nennt zwei berührte Testdateien nicht: `StatementFormsTest.kt` (Zeile `CREATE TABLE t (a int)` durch `CREATE INDEX i ON t (a)` ersetzt, weil die Anweisung jetzt erkannt ist) und `SimDescriptionReaderTest.kt` (Erwartung `maxLength`, neuer Test `liest max_length als Laenge des Textes`). Der Kopf „Berührte Spec-Stellen“ stimmt mit dem Diff überein (`TDS-FA-11.a`, `SPEC-035`, `SPEC-036`, `SPEC-040`, `SPEC-059`). | Reviewer-Skill „Plan-Tabelle nennt die berührten Dateien nicht“ | `docs/plan/planning/done/slice-katalog-und-ddl-zustand.md` · `testserver/test/kotlin/.../TableStoreTest.kt` (Zeile der Testdateien, ohne die beiden) | ja — Abgleich `git diff --stat` gegen die Tabelle | Plan-Tabelle unvollständig gegen den Diff |
| F-3 | LOW | `SimColumn.maxLength = -1` für `NVARCHAR(MAX)` geht über den Wortlaut von [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) hinaus, der „Vorgabewert 4000“ und eine Länge nennt, aber keinen Wertebereich und keinen Sonderwert. `SPEC-035` nennt den Sonderwert und kennzeichnet ihn als Setzung; belegt ist er durch den Lauf mit d-migrate (ohne -1 Exit 5, mit -1 Exit 0; ich habe `notizen` mit `Text` ohne `max_length` gefahren: Exit 0, `IDENTICAL`). Die Fundstelle im Änderungsprotokoll von `SPEC-059` sagt weiter „1 bis 4000, Vorgabe 4000“ ohne -1. Einen Widerspruch zur ADR sehe ich nicht; eine Folge-ADR ist nicht erzwungen, der Sonderwert ist öffentliche Fläche (Wert, kein Name) und steht nur in Spec und KDoc. | [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 2, `AGENTS.md` §3.5 | `spec/spezifikation.md` · `SimColumn.maxLength` (1 bis 4000, Vorgabe 4000) im Änderungsprotokoll vom 2026-11-06 | ja — Grep | Fortschreibung lässt die zweite Fundstelle stehen |
| F-4 | LOW | Das mitgelieferte `main` des Testservers hat jetzt immer ein Backend (`BackendSource.Custom(TableStore(it).backend())`); ohne Beschreibungsdatei antwortete es vorher nicht (Verbindung schloss bei der ersten Anfrage), jetzt antwortet es mit „Tabelle nicht beschrieben“ und nimmt `CREATE TABLE` an. `SPEC-035` „Ohne Tabellen läuft der Server ohne Simulations-Backend, beantwortet den Login und lässt eine Anfrage unbeantwortet“ bleibt für `BackendSource.Simulation` der Bibliothek wahr, gilt aber nicht mehr für die Hauptklasse; weder Spec noch Slice noch `docs/user` nennen die Verhaltensänderung des Testservers. `make e2e` Stufe 1 (Login ohne Beschreibungsdatei) bleibt grün. | `AGENTS.md` §3.7 (Zustand beschreiben), Maintainability | `spec/spezifikation.md` · `Ohne Tabellen läuft der Server ohne Simulations-Backend` und `testserver/kotlin/.../Main.kt` · `fun main() = runTestserver(choose = { BackendSource.Custom(TableStore(it).backend()) })` | ja — Grep, `make e2e` | Verhaltensänderung nicht benannt |
| F-5 | LOW | Die Zahlen des Messprotokolls (26/27 Anfragen, 22/24 `sys.`, 69/70/85 in k5 bis k10) stammen aus einem Wegwerf-Wrapper, der nicht im Repo steht, und aus einem Stand, der zur Messung nicht committet war; das Protokoll sagt beides offen. Meine Wiederholung am Commit bestätigt Exit-Codes und Berichtsfelder (siehe Tabelle oben), die Zahlen nicht. Die Aussage, die im Repo trägt, ist `DMigrateWire.kt` (Handvektoren, der Test prüft 17 von 20 Formen). | `AGENTS.md` §3.8 | `docs/messungen/katalog-und-ddl-zustand-2026-11-06.md` · `er steht nicht im Repo` | nein — Wrapper nicht im Repo | Messzahl ohne wiederholbaren Erzeuger |
| F-6 | INFO | Typen der Katalogspalten `max_length`, `precision`, `scale` als `Int32` statt `smallint`/`tinyint`: weicht vom echten Server ab (Referenzabgleich M-1), ist in `SPEC-035` und im Messprotokoll als Setzung und Abweichung benannt, d-migrate liest die Antworten. Ob ein streng typisierender Client sie anders liest, ist nicht gemessen; der Träger der Gegenmessung ist `slice-qa-vergleichslauf-d-messungen`. | Zielbild „echter Server ist Referenz“ | `spec/spezifikation.md` · `hier sind es \`Int32\`` | nein — Gegenmessung am echten Server | Abweichung vom Server benannt |
| F-7 | INFO | Typnamen und Maße von `bigint`, `bit`, `time`, `datetime2` sind als Setzung gekennzeichnet (Spec, ADR-Anschluss, KDoc von `CatalogAnswers`); Byte- und Precision-Werte entsprechen meiner Kenntnis des Servers (date 3/10, time 5/16/7, datetime2 8/27/7, decimal 5/9/13/17), sind aber hier nicht am Server nachgemessen. `make vergleichslauf-d` (EULA bestätigt) könnte sie belegen. | `AGENTS.md` §3.8 | `src/main/kotlin/io/github/pt9912/tdswire/simulation/CatalogAnswers.kt` · `sind Setzung nach dem Handbuch des Servers` | ja — `make vergleichslauf-d` | Setzung ohne Servermessung |
| F-8 | INFO | Die Fehlerarten der Strukturabweisung (`TDS_UNSUPPORTED_SQL` für vorhandene Tabelle, vorhandene Spalte, fehlenden Schlüssel; `TDS_SIM_UNKNOWN_OBJECT` für unbekannte Tabelle oder Schlüsselspalte) haben keinen eigenen Code und sind als Setzung benannt; der echte Server antwortet mit eigenen Nummern. Ein Client, der Nummer 2714 erwartet, sieht den Unterschied. | [ADR-0033](../plan/adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 11 | `spec/spezifikation.md` · `der echte Server antwortet mit eigenen Nummern` | nein | Fehlernummer weicht vom Server ab |
| F-9 | INFO | `SimTable.onInsertBatch` wird nicht gerufen; KDoc von `SimTable` und `SPEC-035` sagen es, der Einbettungstest belegt nur, dass der Name von außen übersetzt. Das Massenladen bleibt Gegenstand von `slice-schreibpfad-bulk-load`. Die Beispieltabelle `bestellungen` hat jetzt `KundenId` als `BIGINT`; die Messung `SELECT * FROM bestellungen WHERE KundenId = ?` mit `setInt` im Zweig `slice-handle-freigabe-und-grenze-bauen` bleibt tragfähig, weil die Bibliothek einen `Int`-Parameter für eine `Int64`-Spalte wandelt (`SimValues`); gefahren habe ich diese Messung nicht. | Maintainability | `src/main/kotlin/io/github/pt9912/tdswire/simulation/SimModel.kt` · `dieser Stand der Bibliothek ruft ihn nicht` | ja — Grep | Mitglied ohne Aufrufer benannt |

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| Anbieter bei jeder Katalogabfrage (`StatementPlanner`, `SimulationBackend`) | geprüft, ohne Befund: kein Zustand in Planer oder Backend, der Anbieter wird je `plan` gerufen, ein werfender Anbieter endet in `TDS_BACKEND_ERROR` mit fester Meldung; Mutation „liest einmal“ rot (Einbettungstest) |
| Normalisierung (`CatalogForms.normalize`, Parameterstelle ` @P0 ` zu `?`) | geprüft, ohne Befund: die Normalisierung ist symmetrisch auf Wortlaut und Anfrage angewandt; die normalisierten Wortlaute der 20 Formen sind paarweise verschieden (Test `ein Text je Form`); Verwechslung zwischen Formen nicht gefunden |
| Transaktionstexte (`TransactionForms`) | geprüft, ohne Befund: `matches` ist ganzer Text; Mutation ohne Anker rot |
| `CREATE TABLE`/`ALTER TABLE … ADD` (`StructureParts`, Planer) | geprüft, ohne Befund: Namen, Quoting, Mehrfachspalten, doppelte Spalten, Länge 1 bis 4000 oder `MAX`, `DECIMAL(p, s)` mit p ≤ 38 und s ≤ p, Schlüssel mit unbekannter Spalte; nicht erkannte Typen und Klauseln enden in `TDS_UNSUPPORTED_SQL`; die Parserhärte habe ich gelesen und über die Tests geprüft, nicht mit eigenen Eingaben gefahren |
| `OBJECT_ID`-Name (`QualifiedName.parse`) | geprüft, ohne Befund: ein oder zwei Teile, offene Klammer, leerer Teil und drei Teile ergeben ein leeres Ergebnis |
| Typabbildung `CatalogAnswers.typeOf` | geprüft, ohne Befund bis auf F-6 und F-7; Länge in Byte (2 × Zeichen, -1 bleibt -1), Mutation rot |
| Öffentliche Fläche (`make flaeche`, zweiter Konstruktor, `maxLength`, `onInsertBatch`) | geprüft, ohne Befund: kein neuer Typname, `make flaeche` grün, die drei Namen stehen in `SPEC-059` und werden im Einbettungstest von außen übersetzt |
| Protokoll-Synchronität (`TDS-QA-07`) | geprüft, ohne Befund: Form K, C und D liefern je Anfrage genau eine Antwort (`events`, `ChangeExecution` mit Registrierung und Abmeldung); `make e2e` Stufe 7 (Synchronität) grün |
| Schichten und Hard Rules (`AGENTS.md` §3.1, §3.2, §3.6) | geprüft, ohne Befund: kein `@Suppress`, `make detekt` im Lauf von `make gates` grün, nur `make`/Docker benutzt, keine Gate-Lockerung im Diff |
| Kommentar-Klassen (`AGENTS.md` §3.7) | geprüft, ohne Befund: im Quelltext des Diffs keine Konjunktive über verworfene Alternativen (Grep) |
| Zweite Fundstellen (Grep über `spec/`, `docs/user/`, `docs/plan/`, Kommentare) | geprüft; Treffer „Ohne Tabellen läuft der Server ohne Simulations-Backend“ siehe F-4; `FixedAnswers` nennt jetzt SET und Transaktionstext; `SimCatalog fest` nur im Spike-Protokoll (Zeitdokument); `onInsertBatch` und `maxLength` stehen in Spec, ADR, KDoc und Tests ohne Widerspruch |
| Stempel-Eingänge, README | geprüft, ohne Befund: `stempel-eingaenge` und `gate-zuordnung` grün; `harness/README.md` und `README.md` stehen nicht im Diff, die Zellengrenze von 200 Zeichen ist damit nicht berührt |
| `docs/user/testserver-manuell.md`, `testserver/resources/manuell/sim.yaml` | geprüft, ohne Befund: Tabelle der Beispieltypen stimmt mit `sim.yaml` überein (`biginteger`, `required`); `schema compare --source file:sim.yaml` ist `IDENTICAL` (gefahren) |
| `make e2e`, `make matrix-zitate` | geprüft, ohne Befund |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 2 |
| LOW | 3 |
| INFO | 4 |

**Finding-Klassen dieses Laufs:** Synchronisation ohne Test, Prüfung und Änderung nicht atomar · Plan-Tabelle unvollständig gegen den Diff · Fortschreibung lässt die zweite Fundstelle stehen · Verhaltensänderung nicht benannt · Messzahl ohne wiederholbaren Erzeuger · Setzung ohne Servermessung

## Verdikt

**Merge-blockierend:** nein für HIGH (keines); die beiden MEDIUM (F-1, F-2) sind vor der Closure zu behandeln, F-1 als Test oder als eingeschränkte Aussage im Kopf von `TableStore`, F-2 als Ergänzung der Tabelle. Das Urteil über die DoD liegt beim Verifier.

**Übergabe:** Findings an den Implementer; die Finding-Klassen gehen in die Closure-Notiz §7 und von dort in den Zähler. Die Gegenmessung der Setzungen (F-6, F-7, F-8) am echten SQL Server ist der Träger `slice-qa-vergleichslauf-d-messungen`; `make vergleichslauf-d` ist laut Nutzer lokal erlaubt, ich habe es nicht gefahren.
