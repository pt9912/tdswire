# Slice slice-antwortform-nbcrow: Antwortform: Sequenzierer schreibt NBCROW wie der echte Server (A-12)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle (keine Closure-Bedingung außerhalb der DoD dieses Slice; Folge-Slice von `slice-codec-datentypen-erweiterung` nach Entscheidung des Nutzers 2026-11-16).

**Bezug:** [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)

**Berührte Spec-Stellen:** SPEC-003 (NBCROW) · SPEC-045 (Ausnahmeliste des Referenzabgleichs, Regel A-12)

**Verantwortlich:** pt9912

**Autor:** pt9912. **Datum:** 2026-11-16.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Der Sequenzierer wählt je Zeile zwischen `ROW` und `NBCROW` so, wie es der echte SQL Server in der Antwort tut (gemessen: `NBCROW` genau dann, wenn er kürzer ist als der `ROW` derselben Werte, bei Gleichstand `ROW`; eine NULL in `INTN` bleibt `ROW`), belegt an einer Messung gegen den echten Server und am Kompatibilitätstest; die Ausnahme A-12 des Referenzabgleichs entfällt. Der Encoder kann `NBCROW` seit `slice-codec-datentypen-erweiterung` auf Anweisung des Aufrufers schreiben ([Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md) Abschnitt 2, Lücke 6); hier entscheidet der Sequenzierer, wann er es anweist.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- A-11 (`DECIMALN` mit Länge 17 statt 9 im TYPE_INFO von `decimal(10,2)`) — es ist die Längenwahl eines Typs, nicht die Form der Zeile; die Ursache ist nicht untersucht, eine Belegstelle fehlt (`AGENTS.md` §3.8). Der Träger ist offen und beim Nutzer (Register `BEO-TDS/mitschnitt-luecken-ohne-traeger`).
- Weitere Basistypen von `sql_variant`, `text`, `ntext`, `image` — kein Mitschnitt belegt sie; ohne Beleg gibt es kein Orakel (Entscheidung des Nutzers 2026-11-16, benannte Lücke ohne eigenen Slice).
- Antwortseite eines `nvarchar(max)`-OUTPUT-Werts über 4000 Zeichen — die Wertabbildung der Antwort ist nicht Teil der Zeilenform und ungemessen; sie bleibt als Lücke im Register (`BEO-TDS/messung-am-echten-sql-server-fehlt`).
- Änderung des Encoders oder der Wertcodierung — `NullBitmapRowToken` bleibt, wie es ist; der Slice ändert nur, wann der Sequenzierer ihn verwendet.
- Zeilenform im Massenladen und in Antworten außerhalb des Ergebnisstroms (RETURNVALUE) — dort gibt es kein ROW und kein NBCROW.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Messung der Wahl zwischen ROW und NBCROW** am echten SQL Server (beide Referenzinstanzen, Einzelmessung je Instanz, Messweg des Referenzabgleichs, `docs/messungen/referenzabgleich.md`): Zeile ohne NULL bei nullbaren Spalten, mit einer NULL, mit nur NULL, NULL erst in einer Spalte hinter der achten (Bitmap von zwei Byte) und eine Zeile ohne nullbare Spalte. Das Messprotokoll in `docs/messungen/` trägt die Bytes der Zeilen; was nicht gemessen wird, ist Setzung und so gekennzeichnet.
- [x] **Sequenzierer schreibt die gemessene Regel** (`ResponseRun`, `NullBitmapRowToken`, SPEC-003): ein Test je gemessenem Fall mit den Bytes der Messung als Orakel; die Ausnahme A-12 entfällt aus `referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv`, und der Mitschnitt-Lauf der Referenz ist ohne sie grün.
- [x] **Zielbild-Prüfung** (SPEC-003, [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen)): Spezifikation, Kompatibilitätsmatrix (Zeile NBCROW, Lücke 6) und `referenzabgleich.md` (Zeile A-12) sagen den gebauten Stand; keine Aussage steht breiter da als die Messung. *Erfüllt mit Vorbehalt V-1 der Verifikation: der Client mssql-jdbc ist ungemessen und als Setzung geführt (§6, §7).*

Nachweis-Pflichten für alle drei Punkte:

- [x] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>`; die Tests laufen in `make test` und sind grün.
- [x] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet; die Closure-Notiz trennt *gemessen*, *Handvektor*, *Selbstkonsistenz* und *Festlegung*.
- [x] Zerstückelte, überlange und zufällige Eingaben enden in einem Ergebniswert (SPEC-027, [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)) — für die Zeilenzahl und Spaltenzahl der Zeilen des Sequenzierers.
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist (die Zeilen zu NBCROW in `SPEC-003`).
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/response/ResponseRun.kt` | update | der Sequenzierer wählt zwischen `RowToken` und `NullBitmapRowToken` nach der gemessenen Regel (ruft `RowForm`) |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/response/RowForm.kt` | neu | die Regel „NBCROW, wenn kürzer als ROW, sonst ROW“ an einer Stelle |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/token/ValueWriter.kt`, `.../token/Token.kt` | update | `ValueWriter.nullSize` (Bytes eines NULL im ROW, aus `writeNull`); KDoc von `NullBitmapRowToken` nennt den Sequenzierer als Ort der Wahl |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/response/ResponseRowFormTest.kt`, `src/test/kotlin/io/github/pt9912/tdswire/protocol/token/ValueWriterNullSizeTest.kt` | neu | Tests je gemessenem Fall mit den Bytes der Messung als Orakel; Zufall, überlange und falsche Zeilen; Größe eines NULL je Typ |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/response/ResponseOrderTest.kt`, `src/test/kotlin/io/github/pt9912/tdswire/runtime/KatalogSpaltenWireTest.kt`, `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RequestSequenceTest.kt` | update | zählten Zeilen nur als ROW (`ResponseOrderTest`, `KatalogSpaltenWireTest`: rot nach der Änderung); KDoc von `RequestSequenceTest` |
| `referenztest-dotnet/Vergleich/MessungBulkTests.cs` | update | Szenario `zeilenform-null` (Datenbank `tdswire_zeilen`, eigene Hash-Gruppe), 17 Austausche |
| `referenztest-dotnet/Mitschnitt/TokenLeser.cs`, `referenztest-dotnet/Mitschnitt/MessAuswertung.cs`, `referenztest-dotnet/Mitschnitt/MessungBulkBelegTests.cs` | update | Bytes je Zeile in der Auswertung (`mitZeilenform`), Belegtests der Ablage (Hexfolgen, Regel an den Zeilen), Ablage 23 |
| `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/{2022,2025}/zeilenform-null.messung` | neu | Ablage der Messung je Instanz |
| `referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv` | update | die Regel A-12 entfällt |
| `tools/mk/vergleichslauf-d.mk` | update | Kopfzeile: 23 Szenarien der Messreihe |
| `docs/messungen/zeilenform-nbcrow-2026-11-17.md` | neu | Messprotokoll der Zeilenform am echten Server (M-37 bis M-41) |
| `docs/user/referenz-konfiguration.md` | update | ein Absatz zur Zeilenform unter den Antwortformen |
| `spec/spezifikation.md`, `docs/plan/kompatibilitaetsmatrix.md`, `docs/messungen/referenzabgleich.md`, `docs/plan/spec-abdeckung.md`, Register `BEO-TDS/mitschnitt-luecken-ohne-traeger` (`state.md`) | update | Zielbild-Prüfung; Zeile NBCROW und Lücke 6 der Matrix; Zeile A-12 entfällt, Verweis auf das Messprotokoll; Abdeckungsmatrix nach geänderten Tags; Stand zu A-12 |

Der Implementer liest den Stand von `ResponseRun` und passt diese Tabelle an; eine beim Bau entstandene Datei steht vor dem Handoff darin (`BEO-TDS/plan-tabelle-nennt-neue-datei-nicht`).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `slice-codec-datentypen-erweiterung` liegt in `done/` (der Encoder schreibt NBCROW), `make gates` ist auf `main` grün, ein Verantwortlicher ist im Kopf eingetragen, und der Nutzer bestätigt den Lauf gegen die Referenzinstanzen (EULA des Images liegt beim Nutzer, `make vergleichslauf-d`); ohne die Bestätigung bleibt der Slice in `open/`, weil Liefer-Punkt 1 den echten Server braucht.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2; Schnittvorschlag: Messung und Bau trennen.
- `in-progress` → `open` (blockiert): Der Lauf gegen den echten Server ist nicht freigegeben oder gelingt nicht; ohne Messung bleibt A-12 eine Lücke, und der Sequenzierer schreibt ROW weiter.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün, der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung je Aussage sowie den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Die Regel des echten Servers (wann NBCROW, wann ROW) war vor dem Slice aus einer Zeile des Mitschnitts `batch-typen` bekannt (Zeile nur aus NULL als NBCROW); ob jede Zeile mit einer NULL NBCROW ist, war offen und ist gemessen: nein, die Regel ist „kürzer als ROW“. — **Ausgang:** entfallen — gemessen (Messprotokoll M-37 bis M-41): NBCROW genau dann, wenn kürzer als ROW; die Regel steht in `RowForm`.
- Die Referenz (`SqlClient`) liest ROW und NBCROW; ob ein anderer Client NBCROW ablehnt, ist nicht gemessen. — **Ausgang:** weiter offen, Träger Nutzer (siehe §7: Folge-Messauftrag mssql-jdbc); die Verifikation (V-1) hat gezeigt, dass `make e2e` keinen Lauf mit mssql-jdbc enthält, die Aussage des Reviews (F-5) trug nicht.
- Größe: drei Liefer-Punkte. — **Ausgang:** entfallen — drei Liefer-Punkte geliefert, keine Rückführung nach §4.

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

Geschlossen 2026-11-17 (Planner, Slice-Closure ohne Welle). Geprüfter Stand: Zweig `slice-antwortform-nbcrow`, Spitze `0d4867a0` (Basis `fbac7a7f`; sieben Commits, 27 Dateien, +1334 −34); danach nur Doku (diese Closure). Prüfung: Review [2026-11-17-a40b50c3](../../../reviews/2026-11-17-a40b50c3-slice-antwortform-nbcrow.md) (F-1 und F-2 MEDIUM, F-3 LOW, F-4 und F-5 INFO; F-1 bis F-3 in `853239d5` nachgebessert), Verifikation [2026-11-17-853239d5](../../../reviews/2026-11-17-853239d5-verifikation-slice-antwortform-nbcrow.md) (V-1 MEDIUM, V-2 und V-3 INFO).

**Gegenstand.** Der Sequenzierer (`ResponseRun`, `RowForm`) wählt je Zeile `ROW` oder `NBCROW` wie der echte SQL Server: NBCROW genau dann, wenn die ausgelassenen NULL mehr Bytes sparen als die Bitmap (`ceil(n / 8)`) kostet, bei Gleichstand ROW; die Größe eines NULL im ROW kommt aus `ValueWriter.nullSize` (aus `writeNull`, keine zweite Tabelle). Die Ausnahme A-12 des Referenzabgleichs entfällt, der Mitschnitt-Lauf ist ohne sie grün.

**Beleg je DoD-Punkt (Bestätigung durch den Verifier).**

- Punkt 1, Messung: erfüllt. [zeilenform-nbcrow-2026-11-17](../../../messungen/zeilenform-nbcrow-2026-11-17.md) M-37 bis M-41 mit den Bytes je Zeile, beide Instanzen (2022, 2025), Einzelmessung je Instanz; Ablagen `zeilenform-null.messung` je Instanz; nicht Gemessenes unter „Grenzen“. Die Läufe gegen die Instanzen hat die Verifikation nicht wiederholt, sie prüfte am Beleg.
- Punkt 2, Sequenzierer: erfüllt. `ResponseRowFormTest` (je gemessenem Fall die Bytes der Messung als Orakel, `@spec SPEC-003`), `ValueWriterNullSizeTest`; `make clean-build` ohne roten Test; `make vergleichslauf-d LAUF=mitschnitt` grün ohne A-12 (Verifikation).
- Punkt 3, Zielbild-Prüfung: erfüllt **mit Vorbehalt V-1**. [SPEC-003](../../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog), Matrix (Zeile NBCROW, Lücke 6 geschlossen), `referenzabgleich.md` und Nutzer-Doku nennen die Bedingungen (ein Client, ein Paket, bis 17 Spalten) und die Setzungen; der Vorbehalt: der Client mssql-jdbc ist ungemessen (siehe „Weiter offen“).
- Nachweise: `@spec` an jeder Testfunktion der Klasse; SPEC-027 ([TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)) für Zeilen- und Spaltenzahl in `zufällige Zeilen …` und `überlange Zeilen und Ergebnisse …`; „zerstückelt“ ist nicht anwendbar, die Wahl liegt auf fertigen Werten ohne Eingabestrom (Verifikation). Review, Doku, Register, Risiken, Paarungen: unten.

**Zuordnung je Aussage.**

- *Gemessen* (echter Server, 2022 und 2025, Einzelmessungen, `Microsoft.Data.SqlClient` 7.1.1): ROW oder NBCROW je Zeile, nicht je Ergebnis (M-37); Bitmap bis drei Byte (17 Spalten) mit NULL in der zehnten Spalte (M-38); die NULL-Größe im ROW für `INTN`, `BITN`, `DECIMALN`, `DATE`, `TIME`, `DATETIME2`, `uniqueidentifier` (ein Byte), `NVARCHAR` und `varbinary` ohne MAX (zwei), `nvarchar(max)` (acht), jeweils als Zeilenform, die die Alternativen trennt (M-39); keine nullbare Spalte: ROW (M-40); dieselbe Wahl im RPC (M-41). Die Regel ist an den Zeilen der Austausche 01 bis 12 gemessen.
- *Handvektor:* keiner; die Orakel der Tests sind die Bytes der Messung.
- *Selbstkonsistenz:* `ValueWriterNullSizeTest` (die Größe aus `nullSize` gleicht dem, was `writeNull` schreibt); die Zufalls- und Überlängentests der Zeilen und Spalten; die NULL-Größe von PLP und `sql_variant` am Server nicht gemessen (Review F-4).
- *Festlegung (Setzung ohne Messung):* die Rechnung `ceil(n / 8)` für mehr als 17 Spalten; die Anwendung der Regel auf Ergebnisse mit mehreren Dezimalspalten und auf Ergebnisse über mehrere Pakete; die Geltung für andere Clients als den Referenzclient; ein Typ, den `writeNull` nicht kennt, zählt ein Byte (der Sequenzierer erreicht ihn nicht).

**Was ging anders als geplant.** (a) Der Plan (§1, §6) trug die Annahme „eine Zeile mit NULL ist NBCROW“, bekannt aus einer Zeile des Mitschnitts (`batch-typen`, Zeile nur aus NULL); die Messung widerlegte sie: eine NULL in `INTN` bleibt ROW, entscheidend ist die Länge. Das Ziel ist auf den Befund korrigiert (Review F-3). (b) Review F-1: die Bedingungen der Messung standen nur im Messprotokoll, nicht in Spec, Matrix und Nutzer-Doku; nachgebessert. (c) Review F-2: `docs/user/referenz-konfiguration.md` fehlte in der Plan-Tabelle; nachgetragen. (d) Drei bestehende Tests setzten „Zeile = ROW“ voraus (`ResponseOrderTest`, `KatalogSpaltenWireTest` rot nach der Änderung, KDoc von `RequestSequenceTest`); die Plan-Tabelle nennt sie, nachgezogen. (e) Review F-5 behauptete, `make e2e` mit der d-migrate-Abnahme belege mssql-jdbc; die Verifikation widerlegte es (V-1), siehe unten.

**Gates.** `make gates` Exit 0 auf `853239d5` (Verifier, 12 s; die Klasse `immer` lief frisch, die Stempel der Klassen `werkzeug`, `tests`, `stempel` hatten einen Beleg und wurden übersprungen); der Lauf auf dem Stand dieser Closure (nur Doku, `src/` seit `853239d5` unberührt) steht in der Übergabe des Planners, nicht in dieser Notiz. Stempel nach [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md), [ADR-0030](../../adr/0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md); den frischen Lauf der Tests und von detekt belegt `make clean-build` des Verifiers (Exit 0, 11 min 15 s unter Last 11 bis 14), `make e2e` Exit 0 (9 von 9 Stufen, 7 min 43 s). Die Stempel kennt die CI nicht, sie läuft voll ([ADR-0023](../../adr/0023-gate-klassen-und-stempel.md)).

**Weiter offen (Träger: Nutzer).**

1. **Client mssql-jdbc (d-migrate) gegen NBCROW dieses Servers: nicht gemessen.** `make e2e` enthält keinen JDBC-Lauf (alle neun Stufen `Microsoft.Data.SqlClient` oder Rohbytes; `grep -ci jdbc` im Log: 0); `make messung-treiber-handles` ist das einzige Ziel mit mssql-jdbc gegen diesen Server und liest keine Zeilen mit NULL. Mildernd, kein Beleg: der echte Server schickt mssql-jdbc dasselbe NBCROW (Katalogabfrage von d-migrate, `SPEC-003`). **Folge-Messauftrag:** mssql-jdbc gegen den Testserver mit Ergebnissen, die NULL-Zeilen in beiden Formen tragen (auch 9 und mehr Spalten). Vorschlag des Planners (Setzung): ein eigener kleiner Slice; **nicht angelegt, ohne Kennung**, die Entscheidung liegt beim Nutzer. Spec, Matrix und Messprotokoll führen den Client als Setzung.
2. A-11 (`DECIMALN` mit Länge 17 statt 9 im TYPE_INFO von `decimal(10,2)`): weiter ohne Träger (Register `BEO-TDS/mitschnitt-luecken-ohne-traeger`), nicht Gegenstand dieses Slice.

**Ungemessen (benannte Lücken, kein eigener Slice).** Bitmap über 17 Spalten (vier und mehr Byte); Ergebnisse mit mehreren Dezimalspalten (`decimal(10,2)` nur mit einer NULL in sechs Spalten gemessen; A-11 bleibt); Ergebnisse über mehrere Pakete und mehrere Ergebnisse je Antwort; die Größe eines NULL im ROW für `varchar`, `char`, `binary`, `float`, `money`, `datetime`, `smalldatetime`, `sql_variant`, `xml` und PLP am Server (der Sequenzierer erreicht diese Typen nicht); mssql-jdbc (siehe oben).

**Risiken (Ausgänge in §6).** Entfallen: die Regel des Servers (gemessen), Größe. Weiter offen mit Träger Nutzer: anderer Client (Folge-Messauftrag oben).

**Register.** Fortgeschrieben, kein Zähler gesetzt: je `evidence/slice-antwortform-nbcrow.md` in `BEO-TDS/aussagen-ueber-messung-hinaus` (93 Dateien; Review F-1, Verifikation V-1), `BEO-TDS/festlegung-gilt-nur-fuer-das-clientprofil` (4; Verifikation V-1, im `state.md` vermerkt), `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (48; Review F-1, F-3) und `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht` (25; Review F-2). `BEO-TDS/mitschnitt-luecken-ohne-traeger` nachgeführt (A-12 gebaut). Gesichtet, nicht angelegt: die Tests, die „Zeile = ROW“ voraussetzten, fand der rote Lauf und die Plan-Tabelle nannte sie; es gibt keine Beobachtung dieser Bauart, 1×, der Sensor (roter Test) hat gegriffen, daher kein neues Verzeichnis.

**Lese-Schritt (Repo ohne Wellen-Betrieb, diese Closure löst ihn aus).** Gelesen, was in der Ablage 3× erreicht hat und von diesem Slice berührt wurde: `aussagen-ueber-messung-hinaus` (93, verkörpert in `AGENTS.md` §3.8 und `.harness/skills/reviewer.md`), `fortschreibung-laesst-zweite-fundstelle-stehen` (48, verkörpert), `plan-tabelle-nennt-neue-datei-nicht` (25, verkörpert im Reviewer-Skill), `festlegung-gilt-nur-fuer-das-clientprofil` (4, offen, Entscheidung beim Nutzer), `mitschnitt-luecken-ohne-traeger` (Träger entschieden). Feststellung: keine erreicht mit diesem Slice die Schwelle neu; die Klasse „Festlegung gilt nur für das Clientprofil“ ist weiter weder verkörpert noch geplant und hier zum vierten Mal aufgetreten; ihre Entscheidung (Regel, Sensor, Träger oder Streichung) liegt beim Nutzer, die Closure ändert weder Regel noch Gate. Retirement-Check der verankerten Klassen: wiedergekehrt, keine Lockerung.

**Drei Paarungen** (Repo ohne Wellen-Betrieb, hier geprüft nach dem Move). *Anker* — diese Notiz verkörpert nichts und trägt kein Feld `liegt in`; nichts zu paaren. *Folge-Slice* — der Folge-Messauftrag mssql-jdbc ist ausdrücklich nicht angelegt; er steht als offener Punkt mit Träger Nutzer (kein Folge-Slice, den die Paarung verlangen könnte). *Register* — vier Dateien `evidence/slice-antwortform-nbcrow.md`, jede mit Zeiger auf diesen Slice in `done/`; `make docs-check` Exit 0.

**Lerneintrag (Steering Loop).**

- **Geschärft, keine neue Regel:** (i) Eine Aussage über den Belegumfang eines Sensors („`make e2e` belegt den Client X“) wird am Lauf oder am Target geprüft, nicht aus dem Namen des Sensors abgeleitet: der Review (F-5) irrte, der Verifier fand es mit einem Suchlauf über das Log (`AGENTS.md` §3.8, `BEO-TDS/aussagen-ueber-messung-hinaus`, verkörpert; Retirement-Check: wiedergekehrt, die Regel bleibt). (ii) Eine Messung zur Regel trennt die Alternativen durch die Wahl der Zeilen, nicht durch die Zahl: die Hypothese „NBCROW bei jeder NULL“ stammte aus einer einzigen Zeile des Mitschnitts und war falsch; erst Zeilen mit einer NULL in `INTN`, `NVARCHAR` allein und an Spaltenzahlen 8 gegen 9 trennten die vier Lesarten (Messprotokoll „Die Regel, die die Zeilen tragen“).
- **Für künftige Pläne (Planner):** Ziel und Risiko-Text eines Plans, der eine Regel erst messen lässt, nennen die Regel als offen („Wahl nach gemessener Regel“), nicht als Vermutung im Indikativ (Review F-3; `AGENTS.md` §3.7, §3.8).
- **Benannte Spec-Lücken** (kein `liegt in`, nichts verkörpert): mssql-jdbc gegen NBCROW dieses Servers (oben), mehr als 17 Spalten, mehrere Dezimalspalten, Mehrpaket-Ergebnisse, NULL-Größen weiterer Typen (`SPEC-003` Absatz Zeilenform, Matrix Zeile NBCROW).

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`) gegen die Verzeichnisliste und die Stichworte NBCROW, Mitschnitt, Messung, Antwort, Zielbild, Plan-Tabelle gelesen; Treffer: `mitschnitt-luecken-ohne-traeger` (A-12 trägt dieser Slice), `messung-am-echten-sql-server-fehlt` und `aussagen-ueber-messung-hinaus` (beide über der Schwelle und verkörpert; Liefer-Punkt 1 und 3 sind die Gegenmaßnahme), `plan-tabelle-nennt-neue-datei-nicht` (verkörpert; §3 nennt die Pflicht).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
