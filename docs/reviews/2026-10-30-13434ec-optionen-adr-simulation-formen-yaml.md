# Nachtrag zu den Optionen für die ADR zu [slice-adr-simulation-formen](../plan/planning/done/slice-adr-simulation-formen.md): Beschreibungsform als Datei im neutralen Schema-Format von d-migrate

* Rolle und Stand: Architect, Stand `main` bei Commit 13434ec (Worktree). Es ist **keine Entscheidung gesetzt** und keine ADR geschrieben. Dieser Nachtrag ergänzt den [Optionsbericht](2026-10-30-e6bfd2d-optionen-adr-simulation-formen.md); wo beide sich widersprechen, gilt dieser Nachtrag (Abschnitt 11 nennt die Stellen).
* Bezug: [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-08](../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0010](../plan/adr/0010-detekt-pre-release.md), [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends), [SPEC-036](../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), [SPEC-045](../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig), [SPEC-002](../../spec/spezifikation.md#spec-002--interner-query-vertrag), [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), [ARC-016](../../spec/architecture.md#2-schichten-und-constraints).
* **Entscheidungen des Nutzers vom 2026-10-30, hier übernommen und nicht erneut gefragt:** Beschreibungsform ist eine Datei; Dateiformat ist das neutrale Schema-Format von d-migrate (YAML, `schema_format: "1.0"`), nicht das eigene Zeilenformat F1 des Optionsberichts; `IS [NOT] NULL` ist eine Setzung des Nutzers; die Überführung von Literal und Parameter in den Spaltentyp ist streng und verlustfrei. Die Voreinstellungen des Optionsberichts für die übrigen Fragen bleiben als „Setzung (Voreinstellung 2026-10-30, nicht gemessen)“: Schlüssel nur beschreibend, Textvergleich ohne Beachtung der Schreibweise, Form C leer, keine Vertragsdatei, Verdrahtung über Kotlin-API plus Umgebungsvariable mit Dateipfad, die Datei beschreibt nur das Schema.
* **Präzisierungen des Nutzers im Lauf:** d-migrate ist ein **Werkzeug** (es erzeugt aus dem Schema SQL-Server-DDL), nicht eine Bibliothek im Server; das Schema soll die gemeinsame Quelle von Referenzinstanz und Simulations-Backend sein. Die Kosten einer YAML-Parser-Abhängigkeit sind kein Ausschlussgrund, die Bibliothek von d-migrate ist die naheliegende Voreinstellung. Dazu kamen die JSON-Fassung des Modells (`spec/schema.json`) als Option E4.
* Gelesen: von d-migrate das Benutzerhandbuch §3.1, §3.2, §3.5, §4.1, Anhang A.1 bis A.6, Anhang C und Anhang F (der Rest des Handbuchs nur per Suche nach `mssql` und den Codes `W136` bis `W139`), `spec/neutral-model-spec.md` (§2, §3, §4.1, §4.2, §13), `spec/schema-reference.md`, `spec/type-mapping.md` §6, `spec/schema.json`, `gradle.properties`, die Build-Dateien von `hexagon/core`, `adapters/driven/formats` und das Wurzel-`build.gradle.kts`, `THIRD-PARTY-NOTICES.md`, `YamlSchemaCodec.kt`, `JsonSchemaCodec.kt`, `SchemaFileResolver.kt`, `SchemaNodeParser.kt` und `SchemaNodeParserSupport.kt`; vom Repo `SqlType`, [ARC-016](../../spec/architecture.md#2-schichten-und-constraints) samt Tabelle, `Dockerfile`, `build.gradle.kts`, `gradle.lockfile`, `gradle/verification-metadata.xml`. Nicht abgerufen: `adapters/driven/connection-config/build.gradle.kts` (der Pfad steht im Dateibaum von d-migrate, der Abruf kam nicht zustande) und die übrigen d-migrate-Module.

## 1. Messobjekt und Grenzen (vorweg)

* **Messobjekte:** (a) d-migrate 1.12.0 als Docker-Image `ghcr.io/pt9912/d-migrate:1.12.0` mit dem Digest `sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491` (Tag `latest` zeigte bei der Abfrage auf dieselbe Version), 752 378 155 Byte; Läufe über `docker build` mit `COPY` der Eingaben, nichts gemountet, Ausgaben über `docker build --output`. Die Läufe heißen M-D1 (Fixture und `alltypes`), M-D2 (Extras mit Sequenz, Sicht, Trigger, Index), M-D3 (`--split pre-post`, `--inline-foreign-keys never`), M-D4 (Tabellenschlüssel `dbo.person`), M-D5 (Fehlerfälle) und M-D6 (Oktalzahl, `yes`, Alias-Kaskade). (b) Ein Spike aus Kotlin 2.4.10, JDK 21, Gradle-Image des Repos per Digest (`docker build`, ohne Mount), mit Adaptern für fünf YAML-Bibliotheksstände, einem eigenen JSON-Leser und einem DDL-Leser. Der Spike liegt nur im Scratchpad (`/tmp/claude-1000/-Development-tdswire/8562f454-84ba-456b-9970-945e69c035f4/scratchpad/sim-yaml`) und nicht im Repo. (c) Ein Lock-Versuch auf einer Kopie des Repos im Scratchpad (Abschnitt 7).
* **Eigene Erwartungen:** Die Erwartungen der Fehlerfälle, die Regeln des Lesers und die Mutationen der Eingaben hat derselbe Autor geschrieben wie der Spike. „Erkannt“ heißt: der Spike tut, was der Autor erwartet, nicht: er tut, was ein echter Server täte.
* **Kein SQL Server gemessen.** Die von d-migrate erzeugte DDL wurde **nicht** gegen einen SQL Server ausgeführt. `schema migrate --target db:… --execute` (Anhang A.6 des Handbuchs) wurde nicht ausgeführt; das Handbuch widerspricht sich, ob Migrate gegen SQL Server geht (§4.1: „Migrate/Profiling folgen“ bei der `mssql://`-URL; Anhang F.6: „Volltext bei SQL Server richtet `schema migrate` ein“). Was die Referenzinstanz mit dem Skript tut, ist hier Annahme.
* **Zeiten:** Je ein Prozess, Median aus 300 Läufen nach 300 Aufwärmläufen, auf einem Rechner, auf dem andere Builds liefen. Die Grenzfälle (Abschnitt 7) liefen für vier Bibliotheken gleichzeitig; ihre Zeiten sind Größenordnungen, keine Vergleichswerte. Der Fixture-Median wurde danach je Bibliothek einzeln wiederholt.
* **Beschränkter Umfang der Typmessung:** Die Typtabelle (Abschnitt 5) stützt sich auf eine Datei mit 25 Spalten (`alltypes`); `geometry` und `fulltext` sind nicht erzeugt worden, sondern stehen nach `spec/type-mapping.md` §6.1 und Anhang C.
* **Uneinheitliche Beschriftung:** Der Lauf der Bibliothek snakeyaml-engine 3.2 trägt in seiner Rohausgabe die Überschrift „2.10“; der Spike verwendete 3.2 (belegt durch die aufgelöste Klassenpfad-Liste).

## 2. Herkunft, Belege und Lizenz von d-migrate (Auftrag e)

**Was d-migrate ist.** Ein Kommandozeilenwerkzeug, das das neutrale Schema liest, prüft (`schema validate`), als DDL für fünf Dialekte ausgibt (`schema generate --target postgresql|mysql|sqlite|mssql|oracle`), zurückliest (`schema reverse`) und Änderungen ausrollt (`schema migrate`). Beleg: Handbuch §3.1, §3.2, Anhang A.

**Belegstand.** Alle Aussagen über d-migrate stehen gegen den Stand `main` bei Commit `a33984910bc3c760ad2f7c9733f5da0f12e1abf7` (letzter Push laut GitHub-API 2026-10-27, Release `v1.12.0` vom 2026-10-27), abgerufen am 2026-10-30. Aussagen über das Laufzeitverhalten stehen gegen das Image 1.12.0 (Abschnitt 1).

| Beleg | Ort (Permalink auf den Commit) |
|---|---|
| Formales Schema des Formats (JSON Schema 2020-12) | [spec/schema.json](https://github.com/pt9912/d-migrate/blob/a33984910bc3c760ad2f7c9733f5da0f12e1abf7/spec/schema.json) |
| Modellspezifikation, Typtabelle, Validierungsregeln | [spec/neutral-model-spec.md](https://github.com/pt9912/d-migrate/blob/a33984910bc3c760ad2f7c9733f5da0f12e1abf7/spec/neutral-model-spec.md) §3.1, §13 |
| Kurzreferenz (`required`, `ordinal`, `primary_key`) | [spec/schema-reference.md](https://github.com/pt9912/d-migrate/blob/a33984910bc3c760ad2f7c9733f5da0f12e1abf7/spec/schema-reference.md) |
| Abbildung auf SQL Server | [spec/type-mapping.md](https://github.com/pt9912/d-migrate/blob/a33984910bc3c760ad2f7c9733f5da0f12e1abf7/spec/type-mapping.md) §6.1 |
| Benutzerhandbuch | [docs/user/anwenderhandbuch.md](https://github.com/pt9912/d-migrate/blob/a33984910bc3c760ad2f7c9733f5da0f12e1abf7/docs/user/anwenderhandbuch.md) §3.1, §3.2, Anhang A.4, Anhang C, Anhang F |
| Leser für YAML und JSON | [YamlSchemaCodec.kt](https://github.com/pt9912/d-migrate/blob/a33984910bc3c760ad2f7c9733f5da0f12e1abf7/adapters/driven/formats/src/main/kotlin/dev/dmigrate/format/yaml/YamlSchemaCodec.kt), [JsonSchemaCodec.kt](https://github.com/pt9912/d-migrate/blob/a33984910bc3c760ad2f7c9733f5da0f12e1abf7/adapters/driven/formats/src/main/kotlin/dev/dmigrate/format/json/JsonSchemaCodec.kt), [SchemaFileResolver.kt](https://github.com/pt9912/d-migrate/blob/a33984910bc3c760ad2f7c9733f5da0f12e1abf7/adapters/driven/formats/src/main/kotlin/dev/dmigrate/format/SchemaFileResolver.kt) |
| Bibliotheken | [adapters/driven/formats/build.gradle.kts](https://github.com/pt9912/d-migrate/blob/a33984910bc3c760ad2f7c9733f5da0f12e1abf7/adapters/driven/formats/build.gradle.kts), [gradle.properties](https://github.com/pt9912/d-migrate/blob/a33984910bc3c760ad2f7c9733f5da0f12e1abf7/gradle.properties) |

**Welche YAML-Bibliothek d-migrate nutzt (belegt).**
* Den Leser des Schemas bildet `jackson-dataformat-yaml` in der Version `jacksonVersion=2.21.7` (`formats/build.gradle.kts`, `gradle.properties`); `YamlSchemaCodec` liest mit `ObjectMapper(YAMLFactory())` und schaltet `FAIL_ON_READING_DUP_TREE_KEY` ein. Diese Version bringt `org.yaml:snakeyaml` 2.5 mit (aufgelöst im Spike, Abschnitt 7).
* `snakeyaml-engine` (`snakeyamlEngineVersion=2.7`) steht im selben Modul, die Quelle kommentiert ihn als „Internal SnakeYAML adapter“ für die Datenschreiber (`StreamDataWriterAdapter`); den Schema-Leser stellt er nicht.
* Für JSON liest `JsonSchemaCodec` mit Jacksons `JsonFactory`; beide Codecs reichen den Baum an denselben `SchemaNodeParser` (Quelltext-Kommentar: „Shared between YamlSchemaCodec and JsonSchemaCodec“). `SchemaFileResolver` wählt nach der Dateiendung (`.yaml`, `.yml`, `.json`). **Gelesen wird also für YAML und JSON gleich;** JSON ist dort ausdrücklich ein dokumentiertes Dateiformat.
* **Gemessen (Image 1.12.0):** Dieselbe Fixture als YAML und als JSON ergibt mit `schema generate --target mssql --deterministic` **byte-gleiche** DDL (`cmp`).

**Veröffentlicht d-migrate ein Modul als Maven-Artefakt? Nein, soweit prüfbar.** Auf Maven Central liefert die Suche nach der Gruppe `dev.dmigrate` 0 Treffer, das Verzeichnis `dev/dmigrate/` existiert dort nicht (HTTP 404); in den GitHub Packages des Nutzers `pt9912` steht nur das Maven-Paket `io.github.pt9912.pgchangefeed-kotlin` (andere Pakete: Container-Images, darunter `d-migrate`). Im gelesenen Wurzel-`build.gradle.kts` und in den Build-Dateien von `hexagon/core` und `formats` steht kein Veröffentlichungsblock. Grenze: andere Module, andere Gruppenkennungen und andere Registries sind nicht abgesucht. Folge: Das Schema-Modell (`SchemaDefinition`) ist **nicht** als Bibliothek beziehbar; der Server kann es nur selbst lesen oder durch das Werkzeug erzeugte DDL lesen.

**Lizenz (belegt).** d-migrate steht unter der MIT-Lizenz (GitHub-API: `MIT`; `LICENSE`: „Copyright (c) 2025 pt9912“, derselbe Rechteinhaber wie dieses Repo, dessen `LICENSE` „Copyright (c) 2026 pt9912“ trägt). `THIRD-PARTY-NOTICES.md` von d-migrate führt als Hinweispflicht nur die Oracle-JDBC-Treiber und nennt keine YAML-Bibliothek.

**Wie dieses Repo die Spezifikation von d-migrate referenziert, ohne Text zu kopieren (Setzung).** Die ADR und die Spezifikation nennen das Format **nur** mit dem Permalink-Muster der obigen Tabelle (Commit, Pfad, Abschnitt) und mit den Feldnamen selbst, die sie lesen; kein Absatz von d-migrate wird übernommen. Jede Aussage über das Format trägt den Belegstand („geprüft gegen Commit a339849, 2026-10-30“, AGENTS.md §3.8); ändert d-migrate das Format (`schema_format` ist heute die Konstante `"1.0"`), ist der Belegstand zu erneuern, nicht der Text anzupassen. Die Fixture-Datei im Repo ist eine eigene Datei dieses Repos in diesem Format und keine Kopie einer d-migrate-Datei. Die Aussage „MIT, daher ohne Hinweispflicht“ ist die Lesart dieses Berichts, keine Rechtsberatung; ob ein Lizenznachweis nötig wird, hängt am Artefakt ([slice-release-artefakt-lizenz](../plan/planning/open/slice-release-artefakt-lizenz.md)): die Stage `build` des `Dockerfile` kopiert nur `build/libs/*.jar`, und `build.gradle.kts` führt kein Shadow-Plugin (gelesen: Plugins `kotlin-jvm` und `detekt`); ein Jar mit eingebetteten Bibliotheken ist damit heute nicht der Fall.

## 3. Was d-migrate für `mssql` tatsächlich erzeugt (Auftrag 1)

**Aufruf (Handbuch Anhang A.4):** `d-migrate schema generate --source <schema.yaml|json> --target mssql --output <datei.sql> [--deterministic] [--split single|pre-post] [--inline-foreign-keys auto|always|never]`. Dazu entsteht ein Bericht `<datei>.report.yaml`. `--deterministic` lässt Laufzeit-Zeitstempel weg. Es gibt zusätzlich `--partition-storage` und `--mssql-hash-partitions` (nur für Partitionierung). **Direkt anlegen** würde `schema migrate --source <datei> --target db:<url> --execute --report <bericht>` (Anhang A.6; nicht ausgeführt, siehe Abschnitt 1).

**Ausgabe an der Fixture** (drei Tabellen, `person`, `orders`, `tag`; Messung M-D1; vollständig im Scratchpad):

```
SET ANSI_NULLS ON;  SET ANSI_PADDING ON;  SET ANSI_WARNINGS ON;  SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;  SET NUMERIC_ROUNDABORT OFF;  SET QUOTED_IDENTIFIER ON;
GO
CREATE TABLE [person] ( [id] INT NOT NULL, [name] NVARCHAR(100), [salary] DECIMAL(10,2), [born] DATE,
    CONSTRAINT [pk_person] PRIMARY KEY ([id]) );
GO
CREATE TABLE [tag] ( [person_id] INT NOT NULL, [tag] NVARCHAR(50) NOT NULL,
    CONSTRAINT [pk_tag] PRIMARY KEY ([person_id], [tag]) );
GO
CREATE TABLE [orders] ( ..., CONSTRAINT [fk_orders_person_id] FOREIGN KEY ([person_id]) REFERENCES [person] ([id]),
    CONSTRAINT [pk_orders] PRIMARY KEY ([order_id]) );
GO
CREATE INDEX [idx_orders_person] ON [orders] ([person_id]);
GO
```

* **Anweisungsarten**, die in den Läufen vorkamen: `SET …;`, `GO` (Stapeltrenner auf eigener Zeile), `CREATE TABLE`, `CREATE [UNIQUE] INDEX`, `CREATE SEQUENCE`, `CREATE OR ALTER VIEW`, `CREATE OR ALTER TRIGGER` und mit `--split pre-post` oder `--inline-foreign-keys never` `ALTER TABLE … ADD CONSTRAINT … FOREIGN KEY` (Messungen M-D2, M-D3). Kommentare `-- …` erscheinen im Kopf und vor Tabellen mit Warnung.
* **Reihenfolge:** Die Tabellen stehen in der DDL als `person`, `tag`, `orders`, in der Datei als `person`, `orders`, `tag`; die Tabelle mit dem Fremdschlüssel rückt hinter ihr Ziel (Beobachtung, die Regel dahinter ist nicht gelesen).
* **Bezeichner:** immer `[…]`; Namen ohne Schema; **Schemas kennt das Modell nicht.** In `spec/schema.json` hat der Schlüssel unter `tables` kein Muster und kein Schemafeld. Gemessen (M-D4): ein Tabellenschlüssel `dbo.person` ergibt `CREATE TABLE [dbo.person]`, also eine Tabelle, deren **Name** einen Punkt enthält, im Standardschema des Servers; Fremdschlüssel und Index verweisen auf `[dbo.person]` und `[dbo.orders]`. `sales.tag` lässt sich damit nicht als Tabelle `tag` im Schema `sales` erzeugen.
* **Nullbarkeit:** `required: true` ergibt `NOT NULL`; `identifier` ergibt `INT IDENTITY(1,1) NOT NULL` (beides gemessen). Dass eine Schlüsselspalte auch ohne `required` nicht nullbar ist, steht in der Kurzreferenz („`required` an einer Schlüsselspalte ist redundant, nie falsch“) und ist an d-migrate **nicht gemessen**: alle Schlüsselspalten der Fixture tragen `required: true`.
* **Keine Zeilen:** Das Schema enthält keine Daten (`spec/schema.json` kennt kein Feld dafür). Zeilen kommen aus keinem der beiden Wege.

**Verhalten bei den Fehlerfällen der Fixture (M-D5; `schema validate`, `schema generate`):**

| Fall | d-migrate 1.12.0 |
|---|---|
| E1 Tabelle doppelt (gleicher Schlüssel) | Fehler, Zeile 28 Spalte 5 (`Duplicate field 'person' … FAIL_ON_READING_DUP_TREE_KEY`) |
| E1b Tabelle doppelt, andere Schreibweise (`PERSON`) | **kein Fehler**, „Validation passed“, 4 Tabellen |
| E2 unbekannter Typ (`money`) | Fehler `Unknown type: money`, ohne Ort |
| E3 Spalte doppelt, andere Schreibweise (`ID`) | **kein Fehler**, 11 Spalten |
| E3b Spalte doppelt, gleicher Schlüssel | Fehler, Zeile 12 Spalte 15 |
| E4 Tabelle ohne Spalten (`columns: {}`) | `validate` meldet `E001 Table has no columns` mit Pfad `tables.person` (und `E003` am Fremdschlüssel) |
| E5 Schlüssel auf unbekannte Spalte | **kein Fehler**, „Validation passed“; `generate` schreibt DDL |
| E6 abgeschnitten | Fehler `while scanning a simple key`, Zeile 26 Spalte 5 |

Zwei Folgen: Ein Schlüssel auf eine nicht vorhandene Spalte und Duplikate ohne Beachtung der Schreibweise gehen durch d-migrate **ohne Befund**; auf einem SQL Server mit der üblichen Server-Collation würde das `CREATE TABLE` sie abweisen (Annahme, nicht gemessen). Der Leser des Simulations-Backends darf sich daher nicht darauf verlassen, dass ein Schema, das d-migrate akzeptiert, in sich stimmig ist.

**Weitere gemessene Eigenheiten des Lesers von d-migrate (M-D6):** `precision: 010` ergibt `DECIMAL(8,2)` (YAML-1.1-Oktalzahl), `max_length: 0x10` ergibt `NVARCHAR(16)`, `required: yes` ergibt `NOT NULL`. d-migrate liest also YAML-1.1-Zahlen und -Wahrheitswerte. Eine Alias-Kaskade (neun Ebenen zu je neun Verweisen) im Feld `tables` ergab „Validation passed“, 0 Tabellen, in 2,1 s (dieselbe Zeit wie die Fixture, 1,85 s, also JVM-Start); eine Liste statt einer Abbildung unter `tables` ergab keinen Fehler. d-migrate ist an diesen Stellen **toleranter als sein eigenes `spec/schema.json`** (das auf oberster Ebene und in Tabelle und Spalte `additionalProperties: false` setzt).

## 4. Teilmenge des neutralen Modells, die das Simulations-Backend liest (Auftrag b)

Beleg: `spec/schema.json` (Feldnamen, Pflichtfelder, `additionalProperties`) und Handbuch Anhang F. „Abweisen“ heißt: Ladefehler mit Pfad, Zeile und Spalte; „ignorieren“: das Feld ist nach `spec/schema.json` bekannt, hat aber im Backend keine Wirkung (Setzung des Architects, Voreinstellung).

| Ebene | Feld | Umgang | Grund |
|---|---|---|---|
| Datei | `schema_format` | Pflicht, muss `1.0` sein | `const "1.0"` im Schema |
| Datei | `name`, `version` | Pflicht (Zeichenkette), nicht ausgewertet | Pflicht im Schema |
| Datei | `tables` | lesen | Gegenstand |
| Datei | `description`, `encoding`, `locale`, `custom_types`, `procedures`, `functions`, `views`, `triggers`, `sequences` | ignorieren | im Schema bekannt, ohne Wirkung auf Tabellen, Spalten, Schlüssel |
| Datei | jedes andere Feld | abweisen | `additionalProperties: false` (auch `aggregates`, das der Codec von d-migrate liest, das aber **nicht** in `spec/schema.json` steht) |
| Tabelle | `columns` | Pflicht, mindestens eine Spalte | Schema: `minProperties: 1` |
| Tabelle | `primary_key` | Pflicht, mindestens ein Eintrag, jeder Eintrag eine vorhandene Spalte | Schema: `minItems: 1`; die Prüfung auf vorhandene Spalten macht d-migrate nicht (M-D5, E5) |
| Tabelle | `description`, `indices`, `constraints`, `partitioning`, `metadata` | ignorieren | keine Wirkung; das Backend speichert nichts und prüft keine Eindeutigkeit |
| Tabelle | anderes Feld | abweisen | `additionalProperties: false` |
| Spalte | `type` | Pflicht, abbilden (Abschnitt 5) | |
| Spalte | `required` | lesen (`true`/`false`) | ergibt `nullable = false` |
| Spalte | `max_length`, `precision`, `scale`, `timezone` | lesen und prüfen (Abschnitt 5) | Teil der Typabbildung |
| Spalte | `ordinal` | lesen: bei allen Spalten gesetzt, danach sortieren; nur bei einem Teil, abweisen | Kurzreferenz: Serialisierung und DDL emittieren Spalten in `ordinal`-Reihenfolge (nicht an d-migrate gemessen) |
| Spalte | `unique`, `default`, `auto_increment`, `generation`, `references`, `length`, `float_precision`, `values`, `ref_type`, `element_type`, `geometry_type`, `srid` | ignorieren, **soweit der Typ abbildbar ist**; die Felder der Lückentypen (`length`, `float_precision`, `values`, `ref_type`, `element_type`, `geometry_type`, `srid`) erscheinen nur an abgewiesenen Typen | keine Wirkung auf Resultset oder Schlüsselbeschreibung |
| Spalte | anderes Feld | abweisen | `additionalProperties: false` |

Weitere Setzungen des Lesers (Abschnitt 10): Namen von Tabellen und Spalten werden ohne Beachtung der Schreibweise auf Doppelte geprüft; Tabellennamen werden wörtlich übernommen (Abschnitt 8).

## 5. Abbildung der neutralen Typen auf `SqlType`, mit Lückenliste (Auftrag c)

Spalten: der Typ im Schema (`column.type`-Enumeration in `spec/schema.json`, 21 Werte), was d-migrate 1.12.0 daraus für `mssql` erzeugt (Messung M-D1, Datei `alltypes`; Anhang C des Handbuchs und `spec/type-mapping.md` §6.1 stimmen darin überein), der [`SqlType`](../../src/main/kotlin/io/github/pt9912/tdswire/domain/SqlType.kt) des Servers, und der Umgang.

| Neutraler Typ | T-SQL bei d-migrate | `SqlType` | Umgang |
|---|---|---|---|
| `identifier` | `INT IDENTITY(1,1) NOT NULL` | `Int32`, nicht nullbar | abbilden; das Identitätsmerkmal geht verloren (Abschnitt 8) |
| `integer` | `INT` | `Int32` | abbilden |
| `biginteger` | `BIGINT` | `Int64` | abbilden |
| `boolean` | `BIT` | `Bool` | abbilden |
| `text` (`max_length`) | `NVARCHAR(n)`, ohne Angabe `NVARCHAR(MAX)` | `UnicodeText` | abbilden; die Länge geht verloren (Frage N4) |
| `email` | `NVARCHAR(254)` | `UnicodeText` | abbilden; Länge geht verloren |
| `decimal` | `DECIMAL(p,s)`; über 38 gekappt auf 38 (`W139`) | `Decimal(p,s)` | abbilden bis `p` 38; `p` über 38 **abweisen** (`SqlType` und [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog) lassen sie in der Typabbildung mit `TDS_UNSUPPORTED_TYPE` scheitern); d-migrate dagegen kappt |
| `date` | `DATE` | `Date` | abbilden |
| `time` | `TIME` | `Time` | abbilden |
| `datetime` | `DATETIME2` | `DateTime` | abbilden |
| `datetime` mit `timezone: true` | `DATETIMEOFFSET` | – (kein Typ mit Zone) | **Lücke** |
| `smallint` | `SMALLINT` | – (Breiten unter 32 Bit sind nicht im Katalog) | **Lücke** |
| `char` | `NCHAR(n)` | – | **Lücke** |
| `float` (`single`/`double`) | `REAL` / `FLOAT` | – (kein Gleitkomma) | **Lücke** |
| `uuid` | `UNIQUEIDENTIFIER` | – | **Lücke** |
| `binary` | `VARBINARY(MAX)` | – | **Lücke** |
| `xml` | `XML` | – | **Lücke** |
| `json` | `NVARCHAR(MAX)` (`W137`) | `UnicodeText` nur über den DDL-Weg | **Lücke** im YAML-Weg (nichts erfinden: ein Text ist nicht JSON) |
| `array` | `NVARCHAR(MAX)` (`W137`) | `UnicodeText` nur über den DDL-Weg | **Lücke** im YAML-Weg |
| `enum` | `NVARCHAR(n)` mit `CHECK` | `UnicodeText` nur über den DDL-Weg | **Lücke** im YAML-Weg |
| `geometry`, `fulltext` | nicht gemessen; nach `type-mapping.md` §6.1 `geometry`/`geography` bzw. `NVARCHAR(MAX)` (`W132`) | – | **Lücke** |

**Ergebnis:** 10 von 21 Typen sind abbildbar (`identifier`, `integer`, `biginteger`, `boolean`, `text`, `email`, `decimal` bis 38, `date`, `time`, `datetime` ohne Zone); 11 sind eine Lücke: acht Typen, für die es auch in der erzeugten DDL keinen Katalogtyp gibt (`smallint`, `char`, `float`, `uuid`, `binary`, `xml`, `geometry`, `fulltext`), und drei, die d-migrate in Text verwandelt (`json`, `array`, `enum`); dazu der Teilfall `datetime` mit Zone. Von den 25 Spalten der Messdatei sind 8 echte Lücken (`NCHAR`, `SMALLINT`, `REAL`, `FLOAT`, `DATETIMEOFFSET`, `UNIQUEIDENTIFIER`, `XML`, `VARBINARY`); mit diesen entfernt liest der DDL-Leser die übrigen 17 (D1b). Der **definierte Fehler** für eine Lücke ist ein Ladefehler beim Start mit Pfad, Zeile, Spalte und dem Typnamen (kein TDS-Fehlercode, weil beim Laden noch kein Client verbunden ist); die Frage, ob stattdessen die Tabelle geladen und nur der Zugriff auf die Spalte mit `TDS_UNSUPPORTED_TYPE` ([SPEC-014](../../spec/spezifikation.md#4-fehler-codes-und-logging-felder)) beantwortet wird, ist Frage N3.

## 6. Lesewege im Spike: E1 YAML, E2 erzeugte DDL, E4 JSON (Auftrag d, 2)

Drei Dateien tragen dieselbe Fixture: `fixture.yaml` und `fixture.json` (von Hand geschrieben, Strukturgleichheit bei d-migrate gemessen, Abschnitt 2) und `fixture-dmigrate.sql` (**unverändert** die Ausgabe von d-migrate 1.12.0). Der Leser des Spike (rund 120 Zeilen) macht aus einem Baum aus Abbildungen, Listen und Skalaren mit Zeile und Spalte die Tabellenbeschreibung (Abschnitt 4 und 5); er hängt an keiner Bibliothek. Alle Lesewege liefern auf der Fixture **dieselbe** Beschreibung (YAML gleich JSON gleich DDL, nach Tabellenname sortiert; die DDL ordnet nach Fremdschlüsseln).

| Weg | Aufwand im Spike (Zeilen ohne Leer- und Kommentarzeilen) | Abhängigkeit | Laden der Fixture (µs, Median) |
|---|---|---|---|
| E1 YAML, Jackson-Strom-API | Adapter 47 (zugleich JSON) | `jackson-dataformat-yaml` 2.21.7 | 205 |
| E1 YAML, snakeyaml-engine 2.10 | Adapter 43 | `snakeyaml-engine` 2.10 | 202 |
| E1 YAML, snakeyaml-engine 3.2 | Adapter 43 | `snakeyaml-engine` 3.2 | 266 |
| E1 YAML, snakeyaml 2.7 | Adapter 42 | `snakeyaml` 2.7 | 276 |
| E1 YAML, kaml 0.104.0 | Adapter 27 | `kaml` mit vier weiteren Jars | 320 |
| E4 JSON, Jackson-Strom-API | Adapter (derselbe) | `jackson-core` | 85 |
| E4 JSON, eigener Leser (RFC 8259) | 137 | keine | 38 |
| E2 erzeugte DDL, eigener Leser | 180 (Tokenizer und Leser) | keine | 67 bis 70 |

**Fehlerfälle (die sieben des Optionsberichts E1, E1b, E2, E3, E4, E5, E6 und zwei zusätzliche, E3b und E6b), Fehlerort in Zeile und Spalte (1-basiert):**

| Fall | E1 YAML (alle vier Bibliotheken) | E4 JSON (Jackson / eigener Leser) | E2 DDL |
|---|---|---|---|
| E1 Tabelle doppelt | erkannt, Z 27 S 3 (kaml meldet selbst und nennt die frühere Stelle) | erkannt, Z 7 S 5 | erkannt, ohne Ort (im Spike nicht gesetzt) |
| E1b Tabelle doppelt, andere Schreibweise | erkannt, Z 27 S 3 | erkannt, Z 7 S 5 | erkannt, ohne Ort |
| E2 unbekannter Typ | erkannt, Pfad `tables.person.columns.born.type`, Z 11 S 23 | erkannt, Z 11 S 27 | erkannt, Z 18 S 5 (MONEY als Lücke) |
| E3 Spalte doppelt, andere Schreibweise | erkannt, Z 12 S 7 | erkannt, Z 12 S 9 | erkannt, Z 19 S 5 |
| E3b Spalte doppelt, gleicher Schlüssel | erkannt, Z 12 S 7 | erkannt, Z 12 S 9 | erkannt, Z 19 S 5 |
| E4 Tabelle ohne Spalten | erkannt, Z 7 S 14 | erkannt, Z 6 S 28 | erkannt, Z 14 S 24 |
| E5 Schlüssel auf unbekannte Spalte | erkannt, Z 12 S 19 | erkannt, Z 13 S 23 | erkannt, ohne Ort |
| E6 abgeschnitten (letzte 25 Zeichen fehlen) | erkannt, Z 26 S 5 (Jackson) bzw. S 10 (engine, snakeyaml, kaml) | erkannt, Z 30 S 30 (beide) | **nicht erkannt** |
| E6b abgeschnitten im letzten `CREATE TABLE` | – | – | erkannt, Z 31 S 31 |

Der DDL-Weg erkennt E6 nicht: die letzten 25 Zeichen liegen in einem `CREATE INDEX`, und jeder Stapel, der kein `CREATE TABLE` ist, wird ungelesen übergangen. Eine gekappte Datei, die in einem übergangenen Stapel endet, lädt also fehlerfrei. Bei YAML und JSON gibt es das nicht, weil das Dokument strukturell geschlossen sein muss.

**Was ein DDL-Leser lesen, übergehen und abweisen muss (Auftrag 2, E2).** Der Leser des Spike teilt am Stapeltrenner `GO` (eigene Zeile) und entscheidet am Anfang des Stapels:
* **lesen:** `CREATE TABLE [name] (…)` mit Spaltenzeilen `[spalte] TYP[(…)] [IDENTITY(1,1)] [NOT NULL|NULL] [CONSTRAINT [n] DEFAULT|UNIQUE|CHECK …]` und Tabellen-Constraints `CONSTRAINT [n] PRIMARY KEY|FOREIGN KEY|UNIQUE|CHECK …`; Ausdrücke in `DEFAULT` und `CHECK` werden bei Klammertiefe 0 bis zum nächsten Komma, zur schließenden Klammer oder zu einem Schlüsselwort übersprungen (`DEFAULT NEXT VALUE FOR [seq]`, `DEFAULT CURRENT_TIMESTAMP`, `CHECK (len(name) > 1)` gelesen, D2 und D3);
* **übergehen** (ungelesen, bis zum nächsten `GO`): `SET …`, `CREATE SEQUENCE`, `CREATE [UNIQUE|CLUSTERED|NONCLUSTERED] INDEX`, `CREATE OR ALTER VIEW`, `CREATE OR ALTER TRIGGER`, `ALTER TABLE`; Sichten und Trigger tragen beliebiges T-SQL im Rumpf, das nur der Stapeltrenner begrenzt;
* **abweisen:** jede andere Anweisung (D6 `INSERT`), jeden Spaltentyp ohne `SqlType` (Abschnitt 5).
* Nicht gemessen und nicht gelesen: Partitionsfunktionen und -schemata, Volltextkataloge (`W144`, `W132` im Handbuch), Spatial. Das sind Anweisungen, die d-migrate laut Handbuch erzeugen kann; der Leser würde sie abweisen. `ALTER TABLE … ADD CONSTRAINT … PRIMARY KEY` wurde nie ausgegeben (Primärschlüssel stehen inline), würde aber übergangen und den Schlüssel verlieren.
* **Grenze der Lesart:** Die Form des Textes ist ein **Zwischenprodukt** von d-migrate 1.12.0, kein dokumentierter Vertrag; die gelesene Teilmenge ist eine Beobachtung an drei Eingabedateien (Fixture, `alltypes`, Extras), kein Beleg für künftige Versionen.

**Robustheit und Grenzen (Messungen S1 bis S9; ein Prozess je Bibliothek, parallel, Größenordnungen):**

| Fall | Jackson YAML | snakeyaml-engine 2.10 / 3.2 | snakeyaml 2.7 | kaml |
|---|---|---|---|---|
| Alias-Kaskade 9 hoch 9 | Strom-API expandiert nicht (1 ms) | Fehler „aliases … exceeds max=50“ (1 ms) | Fehler, max=50 | Fehler „anchors and aliases is disabled“ |
| Alias auf Skalar | gelesen | Spike weist ab (Bibliothek erlaubt ihn) | wie engine | Bibliothek weist ab |
| Tag `!!java.net.URL` als Wert | vom Spike abgewiesen (Tag sichtbar) | vom Spike abgewiesen | Bibliothek weist ab („Global tag is not allowed“) | vom Spike abgewiesen |
| Tag auf einer Sequenz (`!!javax.script.ScriptEngineManager []`) | gelesen, ignoriert | gelesen, ignoriert | Bibliothek weist ab | gelesen, ignoriert |
| Schachtelung Flow 100 / 1000 / 100000 | ok / ok (34 ms) / Fehler Grenze 1000 | ok / ok / **StackOverflowError** | Fehler ab 100 (Grenze 50) | ok / **StackOverflowError** / StackOverflowError |
| Schachtelung Block 2000 | Fehler Grenze 1000 | **StackOverflowError** | Fehler Grenze 50 | **StackOverflowError** |
| Dokument 7 MB (200 000 Spalten) | Fehler „exceeds the limit: 3145728 code points“, 0,3 bis 1 s | dasselbe | dasselbe | dasselbe (als `YamlEngineException`) |
| Dokument 0,7 MB (20 000 Spalten) | ok, 59 ms | ok, 167 bis 253 ms | ok, 179 ms | ok, 186 ms |
| Skalar 1 / 2,5 / 4 Mio Zeichen | 0,5 s / 3,5 s / Fehler nach 10 s | 0,4 bis 0,5 s / 2,8 bis 3,3 s / Fehler nach 7 bis 9 s | 0,5 s / 2,9 s / Fehler nach 7 s | 1,0 s / 10 s / Fehler nach 18 s |
| Skalar 30 Mio Zeichen | kein Ergebnis nach 20 s | kein Ergebnis nach 20 s | kein Ergebnis nach 20 s | kein Ergebnis nach 20 s |
| Byte Order Mark am Anfang | ok | ok | ok | **Fehler** |
| Tabulator als Einrückung | Fehler mit Ort | Fehler mit Ort | Fehler mit Ort | Fehler mit Ort |
| zwei Dokumente | Fehler (vom Spike) | Fehler (vom Spike) | Fehler (vom Spike) | Fehler (Bibliothek) |
| Merge-Key `<<`, komplexer Schlüssel | abgewiesen | abgewiesen | abgewiesen | abgewiesen |

Befunde: (1) Alle YAML-Bibliotheken scannen einen langen Skalar **überlinear** (grob vervierfacht beim 2,5-fachen); die 3-MiB-Grenze der Bibliotheken begrenzt das nur nach oben (Fehler erst nach 7 bis 18 s). Eine Dateigrößengrenze **vor** dem Parsen ist die wirksame Abwehr und hängt an keiner Bibliothek (Setzung in Abschnitt 10). (2) Die Tiefe begrenzen nur Jackson (1000) und snakeyaml (50) von sich aus; snakeyaml-engine und kaml enden bei großer Tiefe in einem `StackOverflowError` (ein `Error`, kein `Exception`; kaml schon bei Tiefe 1000). (3) In keinem Weg wird über die Knoten- oder Strom-API ein Objekt aus einem Tag konstruiert; Konstruktor-Schnittstellen (`load`) wurden weder verwendet noch gemessen. (4) Die Tiefe und Größe der JSON-Wege: Jackson-JSON begrenzt Tiefe 1000, Zeichenkette 20 Mio, Zahl 1000 Ziffern (Meldungen der Bibliothek); der eigene Leser begrenzt, was der Spike setzte (Tiefe 64, Zahl 1000 Zeichen) und ließ eine Zeichenkette von 30 Mio Zeichen zu (151 ms). (5) Der DDL-Weg liest 200 000 Spalten (8 MB) in 0,85 s und eine Klammerschachtelung von 100 000 in 66 ms ohne Stapelüberlauf (der Leser überspringt iterativ).

**E4 JSON im Vergleich (Auftrag 2).** Jackson-JSON und der eigene RFC-8259-Leser verhalten sich auf allen Fehlerfällen gleich bis auf: Byte Order Mark (Jackson lehnt ab, der eigene Leser ignoriert sie), unpaares Surrogat `\ud83d` (Jackson nimmt an, der eigene Leser lehnt ab), Kommentare (beide lehnen ab), führende Null `010` (beide lehnen ab; Jackson mit der genaueren Meldung „Leading zeroes not allowed“), Zahl `1e1` und Zahl über `Int` (Ladefehler des Lesers, nicht des Parsers). JSON hat keine Anker, keine Tags, keine Wahrheitswert-Mehrdeutigkeit (`yes`, `on`) und keine Einrückungsfehler; der eigene Leser hat 137 Zeilen und keine Abhängigkeit. Der Preis gegenüber YAML: Kommentare in der Datei fehlen, und der Nutzer hat YAML gewählt.

## 7. YAML-Bibliothek und Kosten (Aufträge 1 bis 3 des zweiten Nachtrags)

**Kandidaten, je Version am Maven-Katalog (`repo1.maven.org`, `maven-metadata.xml`) nachgeprüft am 2026-10-30:**

| Kandidat | Neueste Version im Katalog | Im Spike | Jars der Laufzeit außer `kotlin-stdlib` und `annotations` | Größe (Byte) | Lizenz (POM) |
|---|---|---|---|---|---|
| Jackson YAML (Bibliothek von d-migrate) | 2.22.4 | 2.21.7 (Version von d-migrate) | `jackson-dataformat-yaml` 2.21.7, `jackson-databind` 2.21.7, `jackson-core` 2.21.7, `jackson-annotations` 2.21, `snakeyaml` 2.5 (5 Jars) | 2 788 997 | Apache-2.0 (POMs von databind, core, snakeyaml; das POM von `jackson-dataformat-yaml` nennt keine eigene Lizenz) |
| snakeyaml-engine | 3.2 | 2.10 und 3.2 | 1 Jar, keine Abhängigkeit | 300 020 (2.10), 309 167 (3.2) | Apache-2.0 |
| snakeyaml | 2.7 | 2.7 | 1 Jar, keine Abhängigkeit | 332 460 | Apache-2.0 |
| kaml | 0.104.0 | 0.104.0 | `kaml-jvm`, `kotlinx-serialization-core-jvm`, `snakeyaml-engine-kmp-jvm` 4.0.1 (Gruppe `it.krzeminski`), `okio-jvm`, `urlencoder-lib-jvm` (5 Jars) | 1 447 852 | Apache-2.0 (POM von kaml) |

`kotlin-stdlib` (1 841 933 Byte) und `annotations` stehen bereits in der Sperre des Repos. Welche YAML-Fassung (1.1 oder 1.2) eine Bibliothek typisiert, ist nicht gemessen: der Spike liest den Text der Skalare, nicht ihre Typen; gemessen ist nur, dass die Kette von d-migrate Oktalzahlen und `yes` typisiert (Abschnitt 3). Die Jackson-Version 2.21.7 ist die von d-migrate (`gradle.properties`), nicht die neueste (2.22.4); „gleiche Bibliothek“ heißt hier gleiche Hauptlinie, und die Version bestimmt der Nutzer (Frage N2).

**Lock-Versuch auf einer Kopie des Repos** (Stage `lock-export` des `Dockerfile`, dieselben Gradle-Aufgaben wie `make deps-lock` ohne `test`; die Ausgabe ohne Änderung stimmt mit `gradle.lockfile` und `verification-metadata.xml` des Repos überein):

| Änderung | `gradle.lockfile` | `gradle/verification-metadata.xml` | Stage `deps` im `Dockerfile` |
|---|---|---|---|
| snakeyaml-engine 2.10 als `implementation` | **1 Zeile geändert** (die Zeile `snakeyaml-engine:2.10=detekt` führt zusätzlich `compileClasspath`, `runtimeClasspath`, `testCompileClasspath`, `testRuntimeClasspath`) | **unverändert** (Jar und POM mit Prüfsumme sind für detekt schon verzeichnet) | unverändert |
| `jackson-dataformat-yaml` 2.21.7 als `implementation` | **6 Zeilen neu** (`jackson-annotations`, `jackson-core`, `jackson-databind`, `jackson-dataformat-yaml`, `jackson-bom`, `snakeyaml` 2.5) | **11 Komponenten neu, 73 Zeilen, rund 4,5 KB** (122 auf 133; dazu die Eltern-POMs `oss-parent`, `jackson-base`, `jackson-parent`, `jackson-dataformats-text` und ein `junit-bom` 5.14.1) | unverändert |

In beiden Fällen ändert sich außerdem `gradle/libs.versions.toml` (eine Zeile) und `build.gradle.kts` (eine Zeile); die Stage `deps` kopiert genau diese Dateien und lädt danach die zusätzlichen Artefakte, ihr Text bleibt gleich. Die Prüfsummen schreibt `make deps-lock` (braucht Netz, [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)); `verify-signatures` steht im Repo auf `false`, die neuen Prüfsummen stammen also vom ersten Abruf und nicht aus einer geprüften Signatur (derselbe Zustand wie für alle bisherigen Einträge). Ein neuer Import in `simulation` (statt in `runtime`) bräuchte weder eine Regel in `config/detekt/detekt.yml` (die Regel `ForbiddenImport/simulation` sperrt nur Pakete des Repos) noch einen `@Suppress`; er widerspräche aber der Tabelle in [ARC-016](../../spec/architecture.md#2-schichten-und-constraints) (Frage N3). [ADR-0010](../plan/adr/0010-detekt-pre-release.md) wird nicht berührt.

**Lizenz-Folge.** Alle Kandidaten stehen unter Apache-2.0 und sind mit MIT als Lizenz dieses Repos vereinbar. Heute enthält das ausgelieferte Jar nur eigene Klassen, die Abhängigkeiten werden aus der Sperre aufgelöst; ein Lizenz- oder Hinweisdokument für Abhängigkeiten gibt es im Repo nicht (und für `kotlinx-coroutines` ebenso wenig). Wird später ein Jar mit eingebetteten Bibliotheken ausgeliefert, trägt die Pflicht [slice-release-artefakt-lizenz](../plan/planning/open/slice-release-artefakt-lizenz.md); sie ist keine neue Pflicht dieser Entscheidung.

**Warum die Empfehlung von der kleinsten Lösung abweicht (Abschnitt 9, N2).** Die kleinste ist snakeyaml-engine 2.10 (kein neues Artefakt, eine Zeile Sperre, keine neue Prüfsumme). Dagegen steht: (1) d-migrate liest mit Jackson, und die gemessenen Eigenheiten seines Lesers (Oktalzahl, `yes`) stammen aus dieser Kette; wer dieselbe Eingabe wie das Werkzeug sehen soll, nimmt dieselbe Kette. (2) Jackson begrenzt die Tiefe (1000) selbst, snakeyaml-engine nicht (`StackOverflowError`); die Grenze von 3 MiB Dokumentgröße haben alle snakeyaml-Bibliotheken. (3) Jackson liefert JSON im selben Artefakt (E4 ohne Zusatzaufwand). Der Preis ist real: sechs Sperrzeilen, elf Komponenten, rund 2,8 MB Jars und ein Abhängigkeitsbaum (`databind` wird mitgezogen, obwohl der Spike nur die Strom-API nutzt). kaml bietet nichts, was die Strom-API nicht ebenso leistet (vier zusätzliche Bibliotheken, Fehler bei Byte Order Mark, Stapelüberlauf schon bei Tiefe 1000, langsamster Skalarscan).

## 8. Rückweg: Vergleich mit der Referenzinstanz (Auftrag 3)

Gemeinsame Quelle heißt: dieselbe YAML-Datei geht (a) durch `d-migrate schema generate --target mssql` an die Referenzinstanz und (b) durch den Leser an das Simulations-Backend ([SPEC-045](../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig)). Gemessen ist, was d-migrate erzeugt und was der Leser daraus macht; **was der Referenzserver mit dem Skript tut, ist nicht gemessen.**

**Was gleich ist (Fixture, gemessen):** dieselben Tabellen und Spaltennamen, dieselbe Spaltenreihenfolge (bei fehlendem `ordinal`: Reihenfolge im Dokument), dieselbe Nullbarkeit (`required`, Schlüssel), dieselben Schlüsselspalten, und die Typfamilien `INT`/`BIGINT`/`BIT`/`DATE`/`TIME`/`DATETIME2`/`DECIMAL(p,s)` als `Int32`/`Int64`/`Bool`/`Date`/`Time`/`DateTime`/`Decimal(p,s)`. Dass die Skale von `TIME` und `DATETIME2` (Standard 7) mit der Setzung von [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog) (Scale 7) zusammenfällt, ist hier nur abgeleitet.

**Wo sie auseinanderlaufen (je ein Eintrag für die Ausnahmeliste oder eine Folge-Entscheidung):**

1. **Länge von Text.** Die Referenz meldet `NVARCHAR(100)`; das Backend meldet `UnicodeText` als `NVARCHAR(4000)` ([SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), Setzung). Der Resultset-Metadaten-Vergleich weicht für jede `text`-Spalte ab (Frage N4); ohne `max_length` erzeugt d-migrate `NVARCHAR(MAX)`, auch das ist nicht `NVARCHAR(4000)`.
2. **Identität.** `identifier` ergibt `INT IDENTITY(1,1)`; das Backend kennt kein Identitätsmerkmal. Das ist bei `KeyInfo` und `SchemaOnly` sichtbar, nicht gemessen.
3. **Dezimalzahl über 38.** d-migrate kappt auf 38 (`W139`) und erzeugt eine gültige Tabelle; der Leser weist die Spalte ab. Die Datei darf also kein `precision` über 38 tragen, sonst sind beide Wege verschieden schweigsam.
4. **Text für `json`, `array`, `enum`.** Der DDL-Weg liest sie als `UnicodeText` (die Referenzinstanz hat tatsächlich Text mit `CHECK`), der YAML-Weg weist sie ab (Abschnitt 5). Hier ist der DDL-Weg **näher an der Referenzinstanz**.
5. **Schemas.** d-migrate legt nur Tabellen im Standardschema an; `dbo.person` als Schlüssel ergibt eine Tabelle mit Punkt im Namen (Abschnitt 3). Der Leser übernimmt den Namen wörtlich ins Schema `dbo` (Setzung, Abschnitt 10). Dann stimmen beide überein: `SELECT * FROM [dbo.person]` trifft, `SELECT * FROM dbo.person` nicht (abgeleitet, nicht gemessen). Die Mehr-Schema-Fixture des Optionsberichts (`sales.tag`) lässt sich über d-migrate nicht herstellen; mit dem Kotlin-Weg (`SimTable.schema`) bleibt sie möglich.
6. **Nicht abgebildete Eigenschaften.** `UNIQUE`, `DEFAULT`, `CHECK`, Fremdschlüssel und Indizes erzwingt die Referenzinstanz, das Backend nicht (es speichert nichts, [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends)). Das berührt Lesezugriffe nicht, aber Vergleichsläufe mit Schreibzugriff.
7. **Collation.** Die Spalten tragen in der DDL kein `COLLATE`; die Textsemantik hängt an der Standard-Collation der Referenzdatenbank, das Backend nimmt `SQL_Latin1_General_CP1_CI_AS` ([SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), Setzung). Ob beide gleich vergleichen, ist unter der Frage 6 des Optionsberichts offen und bleibt ein Fund des Referenzabgleichs.
8. **Sitzungsoptionen.** Das Skript beginnt mit `SET ANSI_NULLS ON` und `SET QUOTED_IDENTIFIER ON` (und fünf weiteren). Sie gelten für die Sitzung, die das Skript ausführt; sie sagen **nichts** über die Sitzung des Referenzclients. Die Annahme `ANSI_NULLS ON` in [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) bleibt damit eine Annahme des Referenzabgleichs.
9. **Zeilen.** Weder die YAML-Datei noch die DDL trägt Zeilen. Zeilen für den Vergleich müssen aus einer dritten Quelle auf beide Seiten kommen ([slice-referenzabgleich-live](../plan/planning/open/slice-referenzabgleich-live.md)).

**Folgerung für die Frage, was die Referenzinstanz bekommt:** Die Datei ist die Quelle, das Skript ein erzeugtes Zwischenprodukt (`--deterministic` macht es diff-bar). Die Prüfungen, die d-migrate auslässt (Schlüssel auf unbekannte Spalte, Namen ohne Beachtung der Schreibweise), läuft der Leser des Backends selbst; ein Schema, das d-migrate annimmt und der Leser abweist, ist ein Befund und kein Widerspruch.

## 9. Fragen an den Nutzer (Optionsliste, Empfehlung des Architect)

Die Fragen N1 bis N5 ersetzen oder präzisieren die Fragen 1, 2, 9 und 10 des Optionsberichts (Abschnitt 11). Alles Triviale steht in Abschnitt 10 als Setzung.

### Frage N1 — Lesewege

* **A — YAML direkt (E1):** Der Server liest die Schema-Datei, die auch d-migrate liest. Eine Datei, eine Quelle, Fehlerort in der Datei, die der Nutzer pflegt. Kosten: eine Bibliothek (Frage N2).
* **B — erzeugte DDL (E2):** Der Server liest das Skript, das auch an die Referenzinstanz geht. Keine Bibliothek, rund 180 Zeilen; bei `json`, `array`, `enum` und `decimal` über 38 liest er, was die Referenzinstanz tatsächlich hat (Text, `DECIMAL(38,…)`), wo der YAML-Weg die Spalte abweist (Abschnitt 8, Punkt 3 und 4). Aber: die Datei im Repo wäre ein erzeugtes Zwischenprodukt (zwei Dateien, ein Erzeugungsschritt mit dem 752-MB-Image von d-migrate), ihr Format ist kein dokumentierter Vertrag, die Fehler zeigen auf die erzeugte Datei, und das Abschneiden in einem übergangenen Stapel bleibt unerkannt (E6).
* **C — beides:** doppelte Pflege, zwei Wege, dieselbe Beschreibung zu prüfen; der Gleichheitstest (Abschnitt 6) wäre die Absicherung.
* **D — zusätzlich JSON (E4):** dieselbe Datei als `.json`, gleicher Baum, gleiche Semantik bei d-migrate (belegt). Kostet mit Jackson nichts (`JsonFactory` ist im selben Artefakt), ohne Jackson 137 Zeilen.
* **Empfehlung: A.** E2 ist nicht messbar besser: es gewinnt bei der Typnähe zur Referenzinstanz (vier Fälle, in denen der YAML-Weg abweist) und bei den Abhängigkeiten, und verliert bei Quelle, Fehlerort, Zwischenprodukt und Erkennung gekappter Dateien. D bleibt als spätere Zugabe möglich und ist keine Entscheidung dieser ADR.

### Frage N2 — YAML-Bibliothek (bei N1 = A oder C)

* **A — Jackson (`jackson-dataformat-yaml` 2.21.7 mit snakeyaml 2.5):** dieselbe Kette wie d-migrate. 6 Sperrzeilen, 11 Prüfsummen-Komponenten (rund 4,5 KB), 5 Jars (2,8 MB). Tiefe 1000 von sich aus begrenzt (Dokumentgröße 3 MiB wie bei allen snakeyaml-Bibliotheken); JSON im Artefakt.
* **B — snakeyaml-engine 2.10:** 1 Sperrzeile, keine neue Prüfsumme, 1 Jar (0,3 MB), schon im Netz für detekt geladen. Keine Tiefengrenze (`StackOverflowError`); nicht die Kette von d-migrate, ob sie Oktalzahlen und `yes` ebenso typisiert, ist nicht gemessen.
* **C — snakeyaml 2.7:** 1 Jar (0,33 MB), eigene Sperrzeile und neue Prüfsummen; Tiefe 50 und Tag-Sperre eingebaut; die Bibliothek unter Jackson, aber nicht dieselbe Hauptlinie (2.7 statt 2.5).
* **Empfehlung: A.** Auf Wunsch des Nutzers (Kosten kein Ausschlussgrund, Bibliothek von d-migrate als Voreinstellung) und weil Jackson die Eingabegrenzen schon mitbringt, die bei B ein eigener Schutz sein müssten. Die Version (2.21.7 gleich d-migrate oder 2.22.4 neueste) ist Teil der Wahl; Empfehlung 2.21.7, bis ein Grund für die neuere Linie vorliegt. **Konsequenz:** `gradle/libs.versions.toml`, `build.gradle.kts`, `gradle.lockfile` und `gradle/verification-metadata.xml` ändern sich über `make deps-lock`; kein Gate wird gelockert.

### Frage N3 — Ort der Bibliothek (Architektur)

* **A — Adapter in `runtime`, reiner Leser in `simulation`:** `runtime` (Composition Root, darf „alle benötigten Module“ nach [ARC-016](../../spec/architecture.md#2-schichten-und-constraints)) liest die Datei und erzeugt mit der Bibliothek einen einfachen Baum (Abbildung, Liste, Skalar, Zeile und Spalte; im Spike je Bibliothek 27 bis 47 Zeilen); `simulation` macht daraus die Tabellenbeschreibung (rund 120 Zeilen, ohne Bibliothek). `simulation` bleibt bei Domain, Port und Stream-Bibliothek; **keine Änderung der Spezifikation und von [ARC-016](../../spec/architecture.md#2-schichten-und-constraints) nötig.**
* **B — Bibliothek direkt in `simulation`:** ein Import mehr in `simulation`; dazu müsste die Zeile von [ARC-016](../../spec/architecture.md#2-schichten-und-constraints) (Rang 3, „Darf importieren“) geändert werden, eine Spezifikationsänderung außerhalb einer ADR (eine ADR schärft die Spezifikation, `SPEC-041` nennt die Erlaubnisliste nur für den Kern, der Detekt-Lauf hielte es nicht auf).
* **Empfehlung: A.** Der Baum ist die Naht; die Bibliothek ist austauschbar, und der DDL- oder JSON-Weg könnte denselben Leser nutzen (JSON ohne Bibliothek liegt in `simulation`).

### Frage N4 — Längenangaben (`max_length`) und Typlücken

N4a — Umgang mit `max_length`/`email`:
* **A — lesen und prüfen (positive Ganzzahl), nicht durchsetzen, nicht an den Client melden:** der Client sieht `NVARCHAR(4000)`; die Abweichung zur Referenz (`NVARCHAR(n)`) kommt auf die Ausnahmeliste von [SPEC-045](../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig).
* **B — `UnicodeText` um eine Länge erweitern** (Änderung von `SqlType` und Typabbildung, also Port und Protokoll, [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md) und [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog)): meldet `NVARCHAR(n)` und könnte Überlänge beim Schreiben abweisen. Eigene Folge-Entscheidung, nicht in dieser ADR.
* **C — Dateien mit `max_length` abweisen:** unbrauchbar, weil die Beispiele von d-migrate sie tragen.
* **Empfehlung: A**, B als benannter Folgeschritt mit Träger (offener Punkt der ADR als Datei).

N4b — Umgang mit Typlücken (Abschnitt 5):
* **A — abweisen beim Laden:** ein Ladefehler mit Pfad, Zeile, Spalte und Typnamen; kein Teilladen. Die gemeinsame Datei darf nur die zehn abbildbaren Typen tragen.
* **B — Tabelle laden, Spalte sperren:** Zugriff auf die Spalte endet mit `TDS_UNSUPPORTED_TYPE`; `SELECT *` auf die Tabelle scheitert. Mehr Verhalten zu definieren und zu testen, und ein Schema, das „lädt“ und beim ersten `SELECT *` scheitert.
* **C — den Typkatalog erweitern** (`smallint`, `char`, `float`, GUID, binär, Zonenzeit): Folge-Entscheidung zu [SPEC-003](../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), `SqlType` und Codec. Der nächste vorhandene Anschluss ist [slice-codec-datentypen-erweiterung](../plan/planning/open/slice-codec-datentypen-erweiterung.md); sein Ziel nennt aber nur Codec-Formen (PLP, NBCROW) und nicht den Typkatalog des Backends.
* **Empfehlung: A jetzt, C als Folge mit einem noch zu benennenden Träger** (die ADR führt es als offenen Punkt; ob der genannte Slice den Katalog aufnimmt oder eine neue Datei entsteht, ist eine Entscheidung des Nutzers). A ist die einzige Option, die nichts erfindet und den Fehler an der Stelle zeigt, die der Nutzer pflegt.

### Frage N5 — Reihenfolge der Quellen für das, was die Referenzinstanz bekommt

Das ist **keine** Entscheidung dieser ADR, sondern eine des Referenzabgleichs; sie ist hier genannt, weil sie am Format hängt.
* **A — `d-migrate schema generate` aus derselben Datei** (`--deterministic`), das Skript wird nicht eingecheckt, sondern im Lauf erzeugt.
* **B — das Skript im Repo, erzeugt und eingecheckt** (diff-bar).
* **C — `schema migrate --execute` gegen die Referenzinstanz:** laut Handbuch für SQL Server widersprüchlich beschrieben (Abschnitt 1), nicht gemessen.
* **Empfehlung: A, Entscheidung beim Träger** [slice-referenzabgleich-live](../plan/planning/open/slice-referenzabgleich-live.md); diese ADR legt nur fest, dass die Datei das Format von d-migrate trägt.

## 10. Setzungen ohne Frage (Voreinstellung 2026-10-30, nicht gemessen, soweit nicht anders vermerkt)

1. **Nur die Felder von `spec/schema.json`:** Unbekanntes wird abgewiesen, Bekanntes ohne Wirkung ignoriert (Abschnitt 4). Gegenmodell: d-migrate selbst liest tolerant (Abschnitt 3); die strengere Lesart fängt Tippfehler, die d-migrate durchlässt.
2. **Nur kanonische Skalare:** `required` nur `true`/`false`; Ganzzahlen nur nach `0|[1-9][0-9]*`. `010`, `0x10`, `yes` werden abgewiesen, obwohl d-migrate sie liest (`DECIMAL(8,2)`, `NVARCHAR(16)`, `NOT NULL`, M-D6); eine Abweisung ist hörbar, ein stilles Anderslesen nicht. Der Spike liest Text, nicht typisierte Werte; ein Wert `"true"` in Anführungszeichen wird deshalb ebenfalls als wahr gelesen (J3), die Lesart ist nachsichtiger als `schema.json`.
3. **Tabellennamen wörtlich:** der Schlüssel unter `tables` ist der Name, das Schema ist `dbo`; ein Punkt im Namen bleibt Teil des Namens (wie die erzeugte DDL).
4. **Nullbarkeit:** nicht nullbar sind Spalten mit `required: true`, Spalten im `primary_key` und `identifier`.
5. **Eigene Prüfungen, die d-migrate nicht macht:** Duplikate von Tabellen und Spalten ohne Beachtung der Schreibweise, Schlüsselspalten vorhanden, Tabelle mit mindestens einer Spalte und einem Schlüssel.
6. **`ordinal`:** bei allen Spalten gesetzt, danach sortieren; nur teilweise gesetzt, abweisen (nach der Kurzreferenz von d-migrate, nicht an d-migrate gemessen).
7. **Ein Dokument, UTF-8, keine Anker, Aliase, Tags und Merge-Keys, nur Skalar-Schlüssel;** Byte Order Mark wird mit YAML gelesen.
8. **Dateigröße höchstens 1 MiB, vor dem Parsen geprüft** (Messung: Skalar mit 1 Mio Zeichen rund 0,5 s, mit 2,5 Mio mehrere Sekunden; 20 000 Spalten sind 0,7 MB und laden in 59 bis 250 ms). Die Zahl ist eine Voreinstellung, nicht aus einem Bedarf abgeleitet.
9. **Ein Ladefehler bricht den Start ab,** vor dem Binden des Ports und ohne Teilladen; die Meldung nennt den Pfad im Dokument, Zeile und Spalte und nie den Inhalt (wie [ADR-0011](../plan/adr/0011-pruefwertformat.md) für Dateien mit Geheimnissen, hier ohne Geheimnis).
10. **Verdrahtung:** Kotlin-API und eine Umgebungsvariable mit dem Dateipfad (Name nicht festgelegt, Schreibweise wie die Variable in [ADR-0011](../plan/adr/0011-pruefwertformat.md)); `runtime` liest die Datei.
11. **Schlüssel nur beschreibend** (kein Einfluss auf Resultset und Callbacks; `SimTable` bekommt dafür ein additives Feld, [SPEC-035](../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends) ist fortzuschreiben), **Textvergleich ohne Beachtung der Schreibweise, Form C leer, keine Vertragsdatei, die Datei beschreibt nur das Schema** (Voreinstellungen des Optionsberichts).
12. **Nicht durchgesetzt:** `UNIQUE`, `DEFAULT`, `CHECK`, Fremdschlüssel, Indizes, `auto_increment`/`generation`.

## 11. Änderungen gegenüber dem Optionsbericht

* **Frage 1 (Beschreibungsform):** entschieden, Datei. Die Option B des Optionsberichts („10 bis 70 Zeilen Parser ohne Fremdbibliothek“) gilt nicht mehr: ein YAML-Parser ohne Bibliothek wurde nicht gebaut und nicht gemessen; die Ausweichwege E2 (180 Zeilen) und E4 (137 Zeilen) sind gemessen.
* **Frage 2 (Dateiformat):** entschieden, das neutrale Schema-Format von d-migrate; F1 bis F3 des Optionsberichts entfallen.
* **Frage 9 (Verdrahtung) und 10 (Zeilen ohne Integrator-Code):** Voreinstellungen bleiben. Die Datei kann keine Zeilen tragen (kein Feld im Schema): Frage 10 B ist nicht mehr möglich.
* **Messtabelle M3 des Optionsberichts:** das Zeilenformat F1 liest die Fixture in rund 27 Zeilen; der Leser für das neutrale Format braucht rund 120 Zeilen plus einen Adapter von 27 bis 47 Zeilen je Bibliothek. Die Mehrkosten gegenüber F1 sind: Bibliothek (Frage N2), Adapter, 3 bis 5 Mal so viel Leser.
* **Abschnitt 6 des Optionsberichts („Nicht gemessen, Träger zu benennen“):** hinzu kommen Träger für (a) die Typlücken-Erweiterung (Träger zu benennen; nächster Anschluss [slice-codec-datentypen-erweiterung](../plan/planning/open/slice-codec-datentypen-erweiterung.md), dessen Ziel nur Codec-Formen nennt), (b) die Längenangabe (Frage N4a, Variante B, Träger zu benennen), (c) die Quelle des Skripts für die Referenzinstanz ([slice-referenzabgleich-live](../plan/planning/open/slice-referenzabgleich-live.md)) und (d) die Erneuerung des Belegstandes von d-migrate bei einem neuen Release (Vorschlag: Hinweis in [slice-sim-erkennung](../plan/planning/in-progress/slice-sim-erkennung.md) §6).

## 12. Akzeptierte Negative, nicht Gemessenes und Träger

* **Akzeptiertes Negativ:** Der Spike ist von einem Autor mit eigenen Erwartungen; er belegt Machbarkeit, Aufwand und das Verhalten der Bibliotheken, nicht die Übereinstimmung mit einem SQL Server. Das ist einmalig und harmlos genug, um keine eigene Pflicht zu bekommen; die ADR führt es als „Grenze der Aussage“.
* **Akzeptiertes Negativ:** Die Grenzfälle liefen parallel; die Zeiten über 1 s sind Größenordnungen. Die Aussage „überlinear“ stützt sich auf drei Messpunkte (1, 2,5, 4 Mio Zeichen) je Bibliothek und ist keine Komplexitätsaussage.
* **Nicht gemessen, Träger vorhanden:** Verhalten der Referenzinstanz mit dem Skript, Collation, Sitzungsoptionen, Identitätsmerkmal in `KeyInfo` → [slice-referenzabgleich-live](../plan/planning/open/slice-referenzabgleich-live.md) (`BEO-TDS/messung-am-echten-sql-server-fehlt`), [slice-qa-kompatibilitaet](../plan/planning/open/slice-qa-kompatibilitaet.md).
* **Nicht gemessen, Träger zu benennen:** Konstruktor-Schnittstellen der YAML-Bibliotheken (nicht verwendet); `schema migrate --execute` gegen SQL Server; die Typen `geometry` und `fulltext` in der erzeugten DDL; die Wirkung der Version Jackson 2.22.4 gegenüber 2.21.7; `ordinal` an d-migrate.
* **Grenze der d-migrate-Belege:** Stand Commit a339849 und Image 1.12.0 am 2026-10-30; der Handbuchtext widerspricht sich an einer Stelle (Migrate gegen SQL Server). Wo Handbuch, Spezifikation und Messung abweichen, gilt in diesem Bericht die Messung.
