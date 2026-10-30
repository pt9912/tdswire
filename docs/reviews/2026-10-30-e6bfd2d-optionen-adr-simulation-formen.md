# Optionen für die ADR zu [slice-adr-simulation-formen](../plan/planning/in-progress/slice-adr-simulation-formen.md): Anweisungsformen, Typen und Beschreibungsform des Simulations-Backends

* Rolle und Stand: Architect, Stand `main` bei Commit e6bfd2d (Worktree). Es ist **keine Entscheidung gesetzt** und keine ADR geschrieben: dieser Bericht ist die Grundlage, aus der der Nutzer jede Option wählt. Eine ADR entsteht erst nach der Wahl (Status zuerst Proposed, `Accepted` nach Review und Verifikation).
* Bezug: [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-08](../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md), Spec-Stellen [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends), [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), [TDS-FA-11.a](../../spec/spezifikation.md#tds-fa-11a--simulations-backend), [SPEC-037 bis SPEC-039](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder).
* Gelesen: Lastenheft [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) und „Offene Entscheidungen“, die drei Spec-Stellen, [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) (Entscheidung, Offen-Liste), `contracts/query-backend.md`, `SqlType`, `QueryEvent`, `QueryRequest`, Importregeln in `config/detekt/detekt.yml`, [ADR-0011](../plan/adr/0011-pruefwertformat.md) (Vorbild für ein eigenes Dateiformat), der Slice (§2, §3, §6) und die Folge-Slices [slice-sim-erkennung](../plan/planning/open/slice-sim-erkennung.md), [slice-sim-auswahl-streaming](../plan/planning/open/slice-sim-auswahl-streaming.md), [slice-sim-aenderung-integration](../plan/planning/open/slice-sim-aenderung-integration.md) sowie [slice-referenzabgleich-live](../plan/planning/open/slice-referenzabgleich-live.md).

## 1. Messobjekt und Grenzen (vorweg)

* **Messobjekt:** ein Spike aus rund 680 Zeilen Kotlin (Kotlin 2.4.10, JDK 21, Gradle-Image des Repos per Digest, `docker build` ohne Mount). Der Code liegt nur im Scratchpad (`/tmp/claude-1000/-Development-tdswire/8562f454-84ba-456b-9970-945e69c035f4/scratchpad/sim-formen`) und nicht im Repo.
* **Eigene Erwartungen:** Die erwarteten Ergebnisse der 60 Scanner-Eingaben und die Regeln beider Überführungs-Varianten hat derselbe Autor geschrieben wie der Spike. „60 von 60“ heißt: der Scanner tut, was der Autor erwartet, nicht: er tut, was ein echter Server täte.
* **Kein SQL Server gemessen.** Alles, was das Verhalten eines echten Servers betrifft (Überführungsregeln, Textvergleich, Kommentarschachtelung), ist hier Annahme und steht als solche da. Die Breite trägt [slice-referenzabgleich-live](../plan/planning/open/slice-referenzabgleich-live.md); die Stichprobe von [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md) deckt nur dreiwertige Logik, Groß-/Kleinschreibung von Spaltennamen und `"…"`-Bezeichner (`BEO-TDS/aussagen-ueber-messung-hinaus`, `BEO-TDS/messung-am-echten-sql-server-fehlt`).
* **Wertetypen sind angenommen:** Der Spike nimmt `Int`, `Long`, `Boolean`, `String`, `BigDecimal`, `LocalDate`, `LocalTime`, `LocalDateTime` als JVM-Typen je `SqlType`. Das ist eine Annahme; die Zeilendarstellung ist in [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) offen und liegt bei [slice-adr-backend-grenzen](../plan/planning/in-progress/slice-adr-backend-grenzen.md).
* Zeitwerte sind Einzel- bis Zweifachläufe auf einem Rechner, auf dem andere Builds liefen.

## 2. Messungen

### M1 Zeichenscanner gegenüber Regex auf Rohtext (60 Eingaben)

Die 60 Eingaben mischen `;`, `--`, `/* */`, `[…]`, `"…"`, `N'…'` mit verdoppeltem Hochkomma, Schlüsselwörter in Literalen und Bezeichnern, offene Literale und Kommentare, Leerraum-Varianten, Zahlformen, mehrteilige Namen und alle vier Formen.

| Variante | Aufbau | Ergebnis wie erwartet | Zeilen (Spike, mit Leerzeilen) |
|---|---|---|---|
| A — Zeichenscanner mit Erkennung (SPEC-036 Schritt 2) | Tokenizer (Literale, Bezeichner, Kommentare) plus Zuordnung S1, W1, W2, W3 | 60 von 60 | rund 150 |
| B — Regex auf dem Rohtext, Kommentare per Regex entfernt, nur Form S1 | drei Regexe | 45 von 54 S1-nahen Eingaben | 47 |

B scheitert an neun Eingaben: fünf werden zu Unrecht abgelehnt (`--` im Literal, `AND` im Literal, `--` in `"…"`, Komma im Literal einer `IN`-Liste, geschachtelter Kommentar), drei zu Unrecht angenommen (unquotiertes Schlüsselwort als Spaltenname, zwei aufeinanderfolgende Literale `'a' 'b'`, `*, a` in der Liste), und eine liefert einen **still veränderten Wert** (`/* */` im Literal ergibt `'a c'`). Das trägt die Setzung in SPEC-036, einen Zeichenscanner zu bauen; eine echte Entscheidung ist hier nicht offen.

Laufzeit von A: `IN`-Liste mit 200 000 Werten (1,29 Mio Zeichen) Median 79 ms und 75 ms in zwei Läufen; ein Blockkommentar mit 1 Mio Schachtelungstiefe (4 Mio Zeichen) 7 und 4 ms ohne Stapelüberlauf; ein Literal mit 5 Mio Zeichen 40 und 43 ms. Ob der Transport die Größe eines SQL-Textes begrenzt, ist nicht geprüft.

### M2 Überführung eines Literals oder Parameters in den Spaltentyp (40 Eingaben, zwei Regelsätze)

Streng: nur gleiche Typfamilie und nur verlustfrei; Nachsichtig: dazu Zahl↔Text, Text→Bool, Rundung auf die Skala, Zeitpunkt mit Leerzeichen, Datum→Zeitpunkt. 28 Eingaben ergeben in beiden dasselbe (unter anderem `2147483648` in Int32 und `9223372036854775808` in Int64 als Überlauf, `1.5` in Int32 abgelehnt, `2026-02-30` und `30.10.2026` als Datum abgelehnt, Zahl `2` in Bool abgelehnt, `123456789.12` in Dezimal(10,2) abgelehnt, Parametertyp Int64 in Int32 abgelehnt). Es unterscheiden sich 12, davon zwei nur im Wortlaut der Ablehnung:

| Eingabe | Streng | Nachsichtig |
|---|---|---|
| Zahl 42 → Text | abgelehnt | `'42'` |
| Zahl 1.239 → Dezimal(10,2) | abgelehnt (Skala 3 > 2) | 1.24 (gerundet, half-up) |
| Text `'42'` und `' 42 '` → Int32 | abgelehnt | 42 |
| Text `'true'` und `'1'` → Bool | abgelehnt | wahr |
| Text `'2026-10-30 12:30:00'` → DateTime | abgelehnt (nur ISO mit `T`) | angenommen |
| Parameter Text `'42'` → Int32 | abgelehnt | 42 |
| Parameter Int32 → Text | abgelehnt | `'42'` |
| Parameter Date → DateTime | abgelehnt | Mitternacht |
| Text `'abc'` und `''` → Int32 | abgelehnt | abgelehnt (anderer Wortlaut) |

Zwei Befunde für den Vergleich in der Bedingung: `BigDecimal("1.0") == BigDecimal("1.00")` ist **falsch**, `compareTo` ergibt 0; der Gleichheitsvergleich einer Dezimalspalte muss also über `compareTo` laufen. Der JDK-Textvergleich: `"abc"` gegen `"ABC"` ordinal ungleich, ohne Beachtung der Schreibweise gleich; `"a"` gegen `"a "` ordinal ungleich; `"ss"` gegen `"ß"` auch ohne Beachtung der Schreibweise ungleich; `"i"` gegen `"İ"` ohne Beachtung der Schreibweise **gleich**. Wie ein echter Server diese Fälle behandelt, ist nicht gemessen.

### M3 Beschreibungsform als Datei (Fixture mit drei Tabellen, zwei Schemas, 10 Spalten, einem zusammengesetzten Schlüssel)

Alle vier Parser lesen die Fixture zurück (Rückweg gleich). Die Validierung (doppelte Tabellen und Spalten ohne Beachtung der Schreibweise, unbekannter Typ, leere Spaltenliste, Schlüsselspalte unbekannt oder nullbar) ist für alle gleich und nicht mitgezählt. Fehlerfälle: E1 Tabelle doppelt, E1b doppelt bei anderer Schreibweise, E2 unbekannter Typ, E3 Spalte doppelt, E4 Tabelle ohne Spalten, E5 Schlüssel auf unbekannte Spalte, E6 Datei am Ende abgeschnitten.

| Format | Parser ohne Validierung (Zeilen) | Laden, Median (µs, warm, zwei Läufe) | Fehlerort | E1 bis E5 | E6 abgeschnitten |
|---|---|---|---|---|---|
| F1 eigenes Zeilenformat (`table`, `column`, `key`, wie das Zeilenformat der Benutzerliste in [ADR-0011](../plan/adr/0011-pruefwertformat.md)) | rund 27 | 41 / 32 | Zeilennummer | alle erkannt | erkannt (Zeile 15) |
| F2 Teilmenge von `CREATE TABLE` mit dem Tokenizer aus M1 | rund 43 (Tokenizer, 74 Zeilen, ohnehin für die Erkennung nötig) | 34 / 27 | nur Tokenindex | alle erkannt | erkannt (Tokenindex 90) |
| F3 XML über das JDK (`java.xml`, Doctype gesperrt) | rund 19 | 306 / 236 | Zeile und Spalte | alle erkannt | erkannt; **der JDK-Parser schreibt „[Fatal Error]“ auf stderr**, solange kein eigener Fehlerhandler gesetzt ist |
| F4 `java.util.Properties` | rund 10 | 26 / 26 | keiner | E1 **nicht erkannt** (gleicher Schlüssel, der letzte gewinnt still) | **nicht erkannt** (die abgeschnittene Zeile wird still ein leerer Schlüssel) |

Weitere Messbefunde: Properties verliert die Reihenfolge der Tabellen und kollidiert mit dem Komma in `decimal(10,2)`, sobald man Spalten mit Komma trennt (der erste Lauf des Spike brach daran). Das JDK 21 des Spike enthält kein Modul mit „json“ im Namen: ein JSON-Format braucht einen eigenen Parser oder eine Fremdbibliothek; die Importregeln für `simulation` sperren nur die Packages des Repos, nicht `javax.xml`, die Architektursicht nennt für `simulation` aber nur Domain, Port und die Laufzeitbibliothek (ARC-016). Properties ist damit gemessen kein tragfähiger Kandidat, JSON ebenfalls nicht (kein JDK-Parser); beide stehen nicht in den Fragen.

## 3. Deckung der Liste „Nicht erkannt“ durch das Lastenheft

Das Lastenheft nennt als Ausschluss Verbund, Aggregat, Ausdruck, Unterabfrage und Sortierung und beschreibt als Gegenstück eine **abgegrenzte Menge** einfacher Anweisungsformen mit „einfachen Gleichheits- und Mengenbedingungen, verknüpft durch UND“.

| Einzelfall in SPEC-036 | Deckung durch das Lastenheft |
|---|---|
| `JOIN` und weitere Relationen | wörtlich: Verbund |
| Unterabfragen | wörtlich: Unterabfrage |
| Aggregate und `GROUP BY` | wörtlich: Aggregat |
| `ORDER BY` | wörtlich: Sortierung |
| Ausdrücke und Funktionen in Listen und Bedingungen | wörtlich: Ausdruck |
| `WITH`, `DISTINCT`, `TOP`, `OFFSET`/`FETCH`, Aliase, `OUTPUT`-Klauseln, mehrere Anweisungen in einem Text | nicht wörtlich; gedeckt durch die abgegrenzte positive Liste (Lesart) |
| `OR`, `NOT`, `<`, `>`, `LIKE`, `BETWEEN` | nicht wörtlich; gedeckt dadurch, dass nur Gleichheit und Menge, verknüpft durch UND, genannt sind (Lesart) |

Die Liste erweitert das Lastenheft damit nicht, solange man die positive Aufzählung als abschließend liest; fünf Einzelfälle sind wörtlich gedeckt, der Rest hängt an dieser Lesart. In der **Gegenrichtung** nennt SPEC-036 `IS [NOT] NULL` als Term, das Lastenheft nennt es nicht (Frage 4).

## 4. Fragen an den Nutzer

Reihenfolge nach Abhängigkeit; Frage 2 und 10 entfallen, wenn Frage 1 die Datei nicht wählt. Alle Empfehlungen sind die des Architect, nicht gesetzt.

### Frage 1 — Beschreibungsform

* **A — nur Kotlin-Schnittstelle** (Skizze in SPEC-035): Ein Server mit Simulations-Backend startet nur mit Code des Integrators. Kein Parser. Der Referenzabgleich ([slice-referenzabgleich-live](../plan/planning/open/slice-referenzabgleich-live.md)) braucht ein Schema ohne Integrator-Code (SPEC-045) und bekäme dazu eigenen Testcode.
* **B — Kotlin-Schnittstelle und zusätzlich eine deklarative Datei für die Tabellen** (Spalten, Typen, Schlüssel). Zeilenquelle und Callbacks bleiben Code (eine Datei kann sie nicht tragen); die Datei bindet über Schema und Name an sie. Aufwand: ein Parser ohne Fremdbibliothek, 10 bis 70 Zeilen je nach Format (M3).
* **Empfehlung: B.** Nur B erfüllt „deklarativ beschriebene Tabellen“ für einen mitgelieferten Serverprozess und trägt den Referenzabgleich ohne Sondercode. Kosten: der Parser und seine Fehlerfälle als Teil von [slice-sim-erkennung](../plan/planning/open/slice-sim-erkennung.md).

### Frage 2 — Dateiformat (nur bei B)

* **F1 — eigenes Zeilenformat** (`table dbo.person`, `column id int32 notnull`, `key id`). Rund 27 Zeilen, Zeilennummer im Fehler, alle Fehlerfälle gemessen erkannt, Vorbild in [ADR-0011](../plan/adr/0011-pruefwertformat.md). Nachteil: ein weiteres Format, das nur dieses Repo kennt.
* **F2 — Teilmenge von `CREATE TABLE`** mit dem Tokenizer der Erkennung. Rund 43 Zeilen, der Tokenizer ist ohnehin da. Das Schema-Skript der Referenzinstanz ([SPEC-045](../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig)) könnte dieselbe Datei sein (abgeleitet, nicht gemessen; dann entfiele das getrennte Pflegen). Nachteile: nur Tokenindex als Fehlerort; die Datei sieht aus wie SQL, das Backend akzeptiert aber nur eine Teilmenge (Längen von `NVARCHAR` fallen weg, `DEFAULT` und `IDENTITY` sind nicht erkannt); und das Lastenheft sagt „wertet kein SQL aus“ (Lesart: eine Beschreibung ist keine Auswertung, die aber der Nutzer bestätigen sollte).
* **F3 — XML über das JDK.** Rund 19 Zeilen, Zeile und Spalte im Fehler, aber das Laden dauert ein Vielfaches (236 und 306 µs gegen 27 bis 41 µs bei F1 und F2; für ein einmaliges Laden beim Start ohne Folge), der Doctype muss gesperrt und der Fehlerhandler gesetzt werden (sonst schreibt der JDK-Parser auf stderr), und `javax.xml` ist ein neuer Import in `simulation`.
* **Empfehlung: F1.** Geringster Aufwand mit voller Fehlererkennung und ohne Lesartfrage zum Lastenheft; F2 ist die Alternative, wenn das gemeinsame Skript mit der Referenzinstanz zählt.

### Frage 3 — Schlüssel

Das Lastenheft nennt „Spalten, Typen, Schlüssel“, SPEC-035 kennt keine Schlüssel, `ResultColumn` und der Codec tragen kein Schlüsselmerkmal.

* **A — Schlüssel nur als Teil der Beschreibung** (ein oder mehrere Spalten; die Validierung prüft, dass sie existieren und nicht nullbar sind), ohne Wirkung auf Ergebnisse und Callbacks. Das Backend speichert nichts und prüft keine Eindeutigkeit.
* **B — Schlüssel zusätzlich als Metadatum zum Client** (Schlüsselkennzeichen im Spaltenvertrag und in COLMETADATA). Ändert `ResultColumn` und den Codec, also Port und Protokoll; verlangt zuvor die Messung, was der Referenzclient bei `KeyInfo` und `SchemaOnly` erwartet ([slice-qa-kompatibilitaet](../plan/planning/open/slice-qa-kompatibilitaet.md)). Das gehört nicht in diesen Entscheidungs-Slice, sondern in einen Folge-Slice.
* **Empfehlung: A** jetzt; B nur als benannter Folgeschritt nach der Messung. „Kein Schlüssel“ wäre eine Abweichung vom Lastenheft und ist deshalb keine Option.

### Frage 4 — `IS [NOT] NULL` (Lesart des Lastenhefts)

`= NULL` trifft nie (SPEC-035, dreiwertige Logik); `IS NULL` ist der einzige Weg zu Zeilen mit NULL. Das Lastenheft nennt es nicht wörtlich (`BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung`).

* **A — behalten, als Setzung des Nutzers gekennzeichnet** (Lesart: Teil der Gleichheitsbedingungen).
* **B — streichen** (nur `=` und `IN`): NULL-Zeilen sind dann über keine Bedingung erreichbar; die Aussage „Zeilen mit NULL-Werten“ im Happy Path bliebe über `SELECT` ohne `WHERE` erfüllt.
* **C — behalten und der Nutzer führt die Ergänzung im Lastenheft selbst nach** (Rang 1; nicht Sache einer ADR).
* **Empfehlung: A.** Kein Zusatzaufwand, im Erkennungsteil ohnehin gebaut (M1), und die Setzung steht ehrlich als solche da.

### Frage 5 — Überführung von Literal und Parameter in den Spaltentyp

Das Ergebnis `TDS_SIM_INVALID_VALUE` ([SPEC-039](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder)) braucht eine Regel, was „nicht überführbar“ heißt. Gemessen ist M2, nicht das Verhalten des Servers.

* **A — streng und verlustfrei:** gleiche Typfamilie; ISO-Formate für Datum, Zeit und Zeitpunkt (Zeitpunkt nur mit `T`); keine Rundung, keine Zahl↔Text-Überführung; Parameter nur verlustfrei erweitert (Int32→Int64→Dezimal).
* **B — streng plus drei Lockerungen:** Rundung auf die Skala der Spalte (half-up), Zeitpunkt auch mit Leerzeichen statt `T`, Date-Parameter in eine DateTime-Spalte.
* **C — nachsichtig:** B plus Zahl→Text, Text→Zahl und Text→Bool, Parameter Text→Zahl und Zahl→Text (volle Nachsicht des Spike).
* **Empfehlung: A.** Eine strengere Regel später zu lockern bricht keinen Aufrufer, eine nachsichtigere später zu verschärfen schon; die Breite der Servermessung folgt im Referenzabgleich, und Abweichungen laufen dann über die Ausnahmeliste.
* **Setzung ohne Frage** (Voreinstellung, nicht gemessen): Dezimalvergleich über `compareTo`; Überlauf (Int32, Int64, Stellen der Dezimalzahl) ist immer `TDS_SIM_INVALID_VALUE`; Werte der Spalte `UnicodeText` haben kein Längenlimit (wie `SqlType`).

### Frage 6 — Vergleich von Textwerten in Bedingungen

SPEC-036 legt nur Bezeichner fest (ohne Beachtung der Schreibweise, „wie die übliche Server-Collation“), nicht die Werte.

* **A — ordinal** (`=` auf den Zeichen, `'a'` ≠ `'A'` ≠ `'a '`). Deterministisch, JDK-Vergleich.
* **B — ohne Beachtung der Schreibweise** wie bei den Bezeichnern; nachgestellte Leerzeichen zählen. Nutzt den JDK-Vergleich, dessen Randfälle gemessen sind (`i`/`İ` gleich, `ss`/`ß` ungleich).
* **C — je Spalte in der Beschreibung wählbar** (Schreibweise beachten oder nicht); Voreinstellung wie A oder B.
* **Empfehlung: B** aus Gründen der Folgerichtigkeit mit den Bezeichnern; ob der Server Werte und Bezeichner gleich behandelt, ist nicht gemessen und bleibt ein Fund für den Referenzabgleich. C ist der Weg, falls dieser Fund eine Abweichung zeigt.

### Frage 7 — Form C (Texte, die der Referenzclient selbst stellt)

* **A — leer lassen**, bis [slice-qa-kompatibilitaet](../plan/planning/open/slice-qa-kompatibilitaet.md) die Texte misst (SPEC-036: „bis dahin ist sie leer“); die Erkennung legt nur den Platz an.
* **B — leer als Standard, aber vom Integrator um eigene Texte erweiterbar** (Text → festes Ergebnis): schafft eine Schnittstelle, deren Form nicht gemessen ist.
* **C — jetzt eine Liste festlegen** (zum Beispiel die `SET`-Texte): wäre eine Annahme, die erst die Messung stützen könnte.
* **Empfehlung: A.** Kein Aufwand, keine ungestützte Aussage; Träger der Füllung bleibt [slice-qa-kompatibilitaet](../plan/planning/open/slice-qa-kompatibilitaet.md) (offener Punkt der ADR mit Träger als Datei).

### Frage 8 — Vertragsdatei `contracts/simulation-backend.md`

* **A — keine:** die ADR und die fortgeschriebene SPEC-035 tragen die Beschreibung (Beschreibungsform, Zeilenquelle, Callbacks).
* **B — jetzt als Entwurf** analog `contracts/query-backend.md` (Integrator-Vertrag).
* **C — mit dem Code** in [slice-sim-aenderung-integration](../plan/planning/open/slice-sim-aenderung-integration.md), wenn die Typen existieren.
* **Empfehlung: A.** B und C schreiben dieselbe Aussage ein zweites Mal neben SPEC-035 und ADR (Drift-Risiko); die Datei bleibt später möglich, wenn ein Integrator sie braucht.

### Frage 9 — Verdrahtung in `runtime`

Der Weg steht laut [slice-sim-aenderung-integration](../plan/planning/open/slice-sim-aenderung-integration.md) in der ADR; `runtime` existiert noch nicht.

* **A — nur Kotlin-API:** `runtime` bietet eine Funktion, der Integrator ruft sie aus eigenem Startcode mit seinen Tabellen auf. Das mitgelieferte Programm startet ohne Simulations-Backend.
* **B — Konfigurationsschalter im mitgelieferten Serverprozess:** eine Umgebungsvariable mit dem Pfad der Beschreibungsdatei (wie `AUTH_USERS_FILE` in [ADR-0011](../plan/adr/0011-pruefwertformat.md)); `runtime` liest die Datei und gibt den Text an den Parser in `simulation` (die Importregel verbietet `simulation` den Zugriff auf `config`). Setzt Frage 1 = B voraus.
* **C — beides.**
* **Empfehlung: C bei Frage 1 = B, sonst A.** Der Integrationstest und der Referenzabgleich starten den Prozess ohne Code, der Integrator nutzt weiter die API.

### Frage 10 — Zeilen ohne Integrator-Code (nur bei Frage 1 = B)

Eine Datei ohne Zeilenquelle liefert für jede Tabelle ein leeres Ergebnis.

* **A — Datei beschreibt nur das Schema;** ohne Integrator-Code sind alle Tabellen leer. Die Zeilenquelle oberhalb der Heap-Grenze (Welle-Closure) kommt aus Testcode.
* **B — die Datei darf feste Zeilen je Tabelle tragen** (Werte in der Form der Literale): Testdaten wohnen im Repo, nur lesbar; Einfügen, Ändern und Löschen bleiben ohne Callback `TDS_UNSUPPORTED_SQL`.
* **Empfehlung: A** für diese ADR; die Frage, ob [slice-referenzabgleich-live](../plan/planning/open/slice-referenzabgleich-live.md) Zeilen braucht (SPEC-045 nennt Unicode, Dezimal, Datum), ist dort mit dem Szenario zu klären und im Zweifel eine Folge-Entscheidung (benannt, Träger: dieser Slice).

## 5. Setzungen ohne Frage (Voreinstellung, nicht gemessen)

Alle als „Setzung (Voreinstellung 2026-10-30, nicht gemessen)“ in die ADR zu übernehmen, außer der Nutzer widerspricht; M1 zeigt jeweils das Verhalten des Spike.

* Leerraum sind Leerzeichen, Tab, CR und LF; ein geschütztes Leerzeichen (NBSP) ist nicht erkannt.
* Blockkommentare dürfen sich schachteln (geschachtelt wie in T-SQL; Annahme, nicht am Server gemessen); ein offenes Literal oder ein offener Kommentar ist nicht erkannt.
* `N'…'` gilt nur, wenn `N` unmittelbar vor dem Hochkomma steht (`N 'x'` ist nicht erkannt); ein verdoppeltes Hochkomma steht für ein Hochkomma; `]]` in `[…]` und `""` in `"…"` ebenso.
* Zahlen: Ziffern mit optionalem Dezimalpunkt und ein führendes `-`; Exponent, Hexzahl und `+` sind nicht erkannt; eine Zahl, an die sich Buchstaben anschließen (`1AND`), ist nicht erkannt.
* Bezeichner unquotiert aus Buchstaben, Ziffern, `_`, `#`, `$`; fünfzehn Schlüsselwörter (`SELECT`, `FROM`, `WHERE`, `AND`, `OR`, `NOT`, `IN`, `IS`, `NULL`, `INSERT`, `INTO`, `VALUES`, `UPDATE`, `SET`, `DELETE`) nur quotiert als Bezeichner; dreiteilige Namen sind nicht erkannt.
* `INSERT` ohne Spaltenliste und `DELETE` ohne `FROM` sind nicht erkannt; eine Spalte darf in Liste und Bedingungen mehrfach stehen; `<>`, `!=` und `NOT IN` sind nicht erkannt (Lesart aus Abschnitt 3).
* Datum, Zeit und Zeitpunkt als Literal nur im ISO-Format als `'…'`; Parameternamen werden ohne Beachtung der Schreibweise verglichen.
* Eine Obergrenze für die Länge des Anweisungstextes setzt diese Entscheidung nicht (M1: 1,29 Mio Zeichen in rund 79 ms); ob der Transport sie begrenzt, ist nicht geprüft.

## 6. Akzeptierte Negative, nicht Gemessenes und Träger

* **Akzeptiertes Negativ:** Der Spike ist von einem Autor mit eigenen Erwartungen; er belegt Machbarkeit, Aufwand und Randfälle des JDK, nicht die Übereinstimmung mit einem Server. Das ist einmalig und harmlos genug, um keine eigene Pflicht zu bekommen; die ADR führt es als „Grenze der Aussage“.
* **Nicht gemessen, Träger vorhanden:** Servergleiche zu Überführung, Textvergleich, Kommentarschachtelung, Rundung → [slice-referenzabgleich-live](../plan/planning/open/slice-referenzabgleich-live.md) (`BEO-TDS/messung-am-echten-sql-server-fehlt`); Form C und Schlüssel-Metadaten → [slice-qa-kompatibilitaet](../plan/planning/open/slice-qa-kompatibilitaet.md).
* **Nicht gemessen, Träger zu benennen:** Obergrenze der Textlänge am Transport (Vorschlag: Risiko in [slice-sim-erkennung](../plan/planning/open/slice-sim-erkennung.md) §6, wenn der Nutzer es so will).
* **Abhängigkeit von anderen Entscheidungen:** die JVM-Typen der Werte je `SqlType` ([slice-adr-backend-grenzen](../plan/planning/in-progress/slice-adr-backend-grenzen.md)); die Überführungsregel aus Frage 5 gilt für sie und ist bei einer anderen Zeilendarstellung neu zu prüfen.

## 7. Ablauf nach der Wahl

1. Der Nutzer wählt je Frage; der Architect schreibt die neue ADR als Proposed (nächste freie Nummer laut ADR-Index, `Bezug` auf [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), ohne `Supersedes`) mit den Messzeilen aus Abschnitt 2 und den Setzungen aus Abschnitt 5, mit Offen-Liste und Träger je Punkt, und trägt sie in den Index ein.
2. SPEC-035 und SPEC-036 werden fortgeschrieben (das Wort „vorläufig“ entfällt nur dort, wo die Entscheidung es trägt); die Messtabellen kommen in den Slice (DoD, Punkt 1).
3. Start-Trigger und Risiken der drei Folge-Slices werden an die Entscheidung angeschlossen; danach Review und Verifikation in frischem Kontext.
