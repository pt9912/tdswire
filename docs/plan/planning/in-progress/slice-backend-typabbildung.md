# Slice slice-backend-typabbildung: Typabbildung Backendtyp → TDS-Typ und -Wert, Validierung der Metadaten vor COLMETADATA

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-backend-streaming

**Bezug:** [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilit%C3%A4t), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)

**Berührte Spec-Stellen:** SPEC-003 · SPEC-014 · SPEC-031 · ARC-001 · ARC-007 (Typabbildung ist zentral und deterministisch, `spec/architecture.md` §2)

**Verantwortlich:** pt9912

**Autor:** pt9912. **Datum:** 2026-10-30.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Eine zentrale, deterministische Abbildung überführt `ResultColumn`/`SqlType` in die `ColumnDef` des Codecs (`TdsType`, Nullbarkeit, Name) und einen Backendwert in einen `TdsValue`; die gesamte Spaltenliste ist vor der Ausgabe von COLMETADATA validiert, und ein nicht abbildbarer Typ oder Wert endet als definierter Fehlerwert (`TDS_UNSUPPORTED_TYPE`, `SPEC-014`) statt als fehlerhaftes Token ([TDS-FA-05.a](../../../../spec/spezifikation.md#tds-fa-05a--ergebniskodierung), [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen) Negative).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Sequenzierer, Ereignisfolge, `DONE_MORE`, Fehlertokens — `slice-backend-sequenzierer`; die Abbildung liefert Ergebniswerte, schreibt aber keine Tokens in einen Strom.
- Typen außerhalb des Mindestkatalogs (tinyint/smallint-Breiten, Gleitkomma, Geld, GUID, Binär, MAX/PLP, NBCROW) — `slice-codec-datentypen-erweiterung` und der Katalog von `SPEC-003`; ein solcher Backendtyp ist nicht abbildbar und endet im definierten Fehler ([TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), Out-of-Scope: vollständiger SQL-Server-Typkatalog).
- Das Backend-Typmodell selbst (`SqlType`) — `slice-backend-port-vertrag`; dieser Slice setzt es voraus.
- Fehlernummer, Severity und State des Fehlerwerts — `welle-fehler-abbruch` ([TDS-FA-06.a](../../../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung)); der Fehlerwert trägt die interne Kategorie, nicht die TDS-Nummer.
- Messung der Typkodierung am Referenzclient und am echten SQL Server — `slice-qa-reproduzierbarkeit`, `slice-qa-kompatibilitaet` (`BEO-TDS/messung-am-echten-sql-server-fehlt`); die Abbildung belegt Handvektoren und Selbstkonsistenz mit dem Test-Decoder.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Metadatenabbildung** ([TDS-FA-05.a](../../../../spec/spezifikation.md#tds-fa-05a--ergebniskodierung) Schritt 1, 2; `SPEC-003`): `SqlType` → `TdsType` für den Mindestkatalog mit Precision/Scale-Grenzen (Decimal 1 bis 38), Unicode-Text bis 4000 UTF-16-Einheiten (Kollation als gekennzeichnete Festlegung), Scale 0 bis 7 für Zeit und Datum mit Zeit, nullable → N-Form; die komplette Spaltenliste wird vor jeder Ausgabe validiert (`SPEC-031`, erster Satz); leere Spaltenliste und leeres Resultset mit Metadaten bleiben gültig.
- [ ] **Wertabbildung** ([TDS-FA-05.a](../../../../spec/spezifikation.md#tds-fa-05a--ergebniskodierung) Schritt 3, 5; `SPEC-003`): Backendwert → `TdsValue` mit NULL, Min/Max der Integer, leerem String (nicht NULL), maximaler Textlänge, Unicode einschließlich Surrogatpaaren, Decimal-Skalierung und -Überlauf, Datum und Zeit ohne Zeitzone (Zeitzonenannahme als Festlegung, §6); ein Wert, der nicht zur Spaltenart passt, ist ein Fehlerwert vor Ausgabe der Zeile.
- [ ] **Fehlerverhalten** ([TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen) Negative, `SPEC-014`, [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilit%C3%A4t)): nicht abbildbarer Backendtyp, ungültiger Unicode (unpaarer Surrogat), Überlauf und Typkonflikt enden in einem Ergebniswert mit interner Kategorie `TDS_UNSUPPORTED_TYPE`; Zufalls- und Grenzeingaben mit festem Startwert enden stets in `Ok` oder dem Fehlerwert, nie in einer Ausnahme; die Textdarstellung des Fehlerwerts enthält keine Zeilenwerte (Nutzerdaten).

Nachweis-Pflichten für alle drei Punkte:

- [ ] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>`; die Tests laufen in `make test` und sind grün.
- [ ] Jede Aussage im Quelltext (KDoc) und in Spec oder Vertrag steht an einem Test oder ist als Festlegung gekennzeichnet; die Closure-Notiz trennt *gemessen*, *Handvektor*, *Selbstkonsistenz* und *Festlegung* wie `slice-codec-tds-paket` (`BEO-TDS/aussagen-ueber-messung-hinaus`). Die Messung in [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) gilt für den Spike, nicht für den Quelltext dieses Slice.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/result/` | neu | Typabbildung (`ColumnMapping`, `ValueMapping`); liegt in `protocol`, weil `protocol` laut Importregeln `domain` und `protocol/token` zugleich nutzen darf (`domain` und `port` dürfen `protocol` nicht importieren); Festlegung dieses Slice. Nutzt `ColumnDef`, `TdsType`, `TdsValue` aus `protocol/token` und `SqlType`/`ResultColumn` aus `domain`; keine Imports aus `transport`, `simulation`, `runtime` |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/result/` | neu | Handvektoren je Typ (Happy, Boundary, Negative nach [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen)), Roundtrip mit `TokenDecoder`, Fuzz mit festem Startwert; `@spec`-Tag je Test |
| `spec/spezifikation.md` | update, falls nötig | `SPEC-003` nennt „zu verifizieren“ und Grenzfälle ohne Wert (Zeitzone, Kollation); ändert die Umsetzung eine Zeile, wird sie im selben Slice nachgezogen |

- Reihenfolge: Metadaten, dann Werte, dann Fehlerverhalten.
- Der Implementer liest `TdsType`, `ColumnDef`, `TdsValue` und die Writer-Grenzen (`TypeInfoWriter`, `ValueWriter`) und passt diese Tabelle an.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `slice-backend-port-vertrag` liegt in `done/` (erfüllt; `SqlType`, `ResultColumn`), `make gates` ist auf `main` grün, die Nutzerentscheidung zur Zeitzonenannahme und zum Kollationswert (§6) liegt vor, und ein Verantwortlicher ist im Kopf eingetragen. Der Slice braucht den Sequenzierer nicht.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2; Schnittvorschlag: Slice A = Metadatenabbildung, Slice B = Wertabbildung und Fehlerverhalten.
- `in-progress` → `open` (blockiert — Carveout?): `SqlType` trägt den Mindestkatalog nicht (Zeit-Scale, Länge von `UnicodeText`; der Port-Slice liegt in `done/`) und die Änderung am Modell ist nicht Teil dieses Slice, sondern braucht einen eigenen Folge-Slice.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün, der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung je Aussage der Abbildung (*gemessen* · *Handvektor* · *Selbstkonsistenz* · *Festlegung*) sowie den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Zeitzonen- und Genauigkeitsannahme — Entscheidungsbedarf (Nutzer):** `SPEC-003` nennt „Zeitzonenannahmen“ und „nach Profil“ ohne Wert; Backendwerte mit Zone (`Instant`, `OffsetDateTime`) sind weder im Mindestkatalog noch abgebildet, und die Rundung bei Scale kleiner als die Genauigkeit des Werts ist offen. — **Entschieden (Nutzer, 2026-10-30):** Werte ohne Zone (`LocalDate`, `LocalTime`, `LocalDateTime`) werden wie geliefert abgebildet, ohne Zeitzonenumrechnung; Werte mit Zone (`Instant`, `OffsetDateTime`) sind nicht im Mindestkatalog und enden in `TDS_UNSUPPORTED_TYPE`; ist die Genauigkeit des Werts größer als die Scale der Spalte, wird abgewiesen statt gerundet (der Encoder rundet nie). Die JVM-Typen je `SqlType` sind eine Festlegung dieses Slice; die endgültige Zeilendarstellung entscheidet `slice-adr-backend-grenzen`. — **Ausgang:** entschieden; Belege in der Closure-Notiz.
- **Kollation von Unicode-Text** (Wert in `Collation`): der Codec legt `SQL_Latin1_General_CP1_CI_AS` als Konstante fest, ob sie zum Clientprofil passt, ist am Testserver gemessen, nicht am Produkt. — **Ausgang:** weiter offen: → `BEO-TDS/messung-am-echten-sql-server-fehlt` (`slice-qa-reproduzierbarkeit`).
- **Modellgrenzen von `SqlType` gegen die Abbildung (aus `slice-backend-port-vertrag`):** (a) `SqlType.Decimal` nimmt jede Precision ab 1 an, der Codec (`TypeInfoWriter.MAX_PRECISION`) 1 bis 38; wie der Widerspruch aufgelöst wird (Obergrenze im Modell oder Fehlerwert in der Abbildung), ist eine Nutzerentscheidung — **Entschieden (Nutzer, 2026-10-30):** das Modell (`domain/SqlType.Decimal`) bleibt unverändert; die Typabbildung validiert die Spaltenliste vor COLMETADATA und meldet `TDS_UNSUPPORTED_TYPE` bei Precision über 38. (b) `SqlType` trägt weder die Scale von `Time` und `DateTime` noch ein Längenlimit von `UnicodeText`; die Abbildung setzt beide fest (Scale 0 bis 7, Länge bis 4000) oder verlangt ein erweitertes Modell. (c) Die JVM-Typen der Werte in `Row`, `QueryParameter` und `OutputParameter` je `SqlType` sind im Port offen ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)); die Wertabbildung legt sie als Festlegung fest. — **Ausgang:** offen bis zur Closure.
- **Nullbarkeit gegen Wert:** ein `null`-Wert in einer als nicht nullbar beschriebenen Spalte, doppelte oder leere Spaltennamen: die Spezifikation schweigt (benannte Spec-Lücke); der Slice legt sie als Festlegung fest oder nennt sie offen. — **Ausgang:** offen bis zur Closure.
- **Ohne Messung am Referenzclient:** Die Abbildung belegt Handvektoren und Selbstkonsistenz; die Annahme der Spaltenmetadaten durch den Client ist nicht gemessen (`BEO-TDS/aussagen-ueber-messung-hinaus`, `BEO-TDS/messung-am-echten-sql-server-fehlt`). — **Ausgang:** weiter offen: → `slice-qa-kompatibilitaet`, `slice-qa-reproduzierbarkeit`.
- **Doppelung mit `slice-codec-datentypen-erweiterung`:** wächst der Katalog hier, wird der Folge-Slice überflüssig oder doppelt. — **Ausgang:** offen bis zur Closure; Grenze in §1.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren** statt neu
formulieren — sonst zählt das Register zwei Namen getrennt) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln (das
Feld `liegt in` steht **nur**, wenn mit diesem Slice wirklich etwas verkörpert
wurde; Feld und Zielort auf **einer** Zeile, Sektionsangabe innerhalb der
Backticks). Ging der Gegenstand an einen anderen Slice oder entfiel er, trägt
diese Sektion die Zeile `Gegenstand:` mit Kennung oder Grund und jedes Risiko
aus §6 seinen Ausgang; die Liefer-Punkte der DoD bleiben leer
(`modul-05-planning-harness.md` §Ein Slice, dessen Gegenstand ein anderer
übernimmt).

Wird bei der Closure gefüllt.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung — dort die **zwei vorgelagerten
Schritte** (sie stehen in jedem Slice-Plan, unabhängig von Modus und
Slice-Typ) und die **vier Pflichtkriterien** (Konventionen-Dichte ·
Phase-Reife · Evidenz-/Diskrepanz-Risiko · Reconciliation-Aufwand), vier und
nicht mehr.

**Der Abschnitt selbst entfällt nie.** Die zwei vorgelagerten Prüfungen laufen
in **jedem** Slice-Plan — sie hängen weder am Modus noch am Slice-Typ. Bedingt
ist allein der Modus-Begründungsblock am Ende; deshalb nennt der Titel beide
Hälften.

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); `port`, `protocol` und `domain` sind keine eigenen Pfad-Cluster mit eigener Strukturregel (die Schichten-Importregeln in `config/detekt/detekt.yml` sind Architektur-, keine Modus-Grenzen). Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, acht Beobachtungen, alle Sub-Area `*`). Treffer: `BEO-TDS/aussagen-ueber-messung-hinaus` (7×, die Regel steht in `AGENTS.md` §3.8; sie bindet KDoc, Vertrag und Spec-Nachführung dieses Slice, Gegenmittel in §2) und `BEO-TDS/messung-am-echten-sql-server-fehlt` (3×; der Mindestkatalog ist nur als Handvektor und am Spike-Testserver belegt, §6). Ohne Treffer: die übrigen sechs.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
