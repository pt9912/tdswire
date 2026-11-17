# Slice slice-codec-datentypen-erweiterung: NBCROW, PLP und Typen außerhalb des Mindestkatalogs im Codec

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** keine (Vorrat; war `welle-anfrageverarbeitung` zugeordnet (Entscheidung des Nutzers 2026-10-31, Folge-Slice von `welle-protokollcodec`), seit deren Closure 2026-11-07 ohne Welle; Start-Trigger unverändert; ob und wann er nötig wird, entscheidet die Messung des Referenzclients (Risiko in §6).)

**Bezug:** [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)

**Berührte Spec-Stellen:** SPEC-003 · SPEC-007 · SPEC-013 · SPEC-014 · SPEC-027 · [TDS-FA-03](../../../../spec/lastenheft.md#tds-fa-03--einfache-sql-batches) (Ablauf `TDS-FA-03.a`, Schritt 4)

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-10-30.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Der Token-Encoder und der RPC-Parser (`slice-codec-rpc-parser`) beherrschen über den Mindestkatalog von SPEC-003 hinaus die Formen, die ein Client für längere Werte und weitere Typen braucht: PLP (MAX-Typen) in ROW und RETURNVALUE, NBCROW und `sql_variant` mit Basistyp `int` (Wortlaut nach Entscheidung des Nutzers 2026-11-16; `text`, `ntext`, `image` und weitere Basistypen sind nicht belegt und bleiben unsupported); was der Codec nicht kann, bleibt ein Ergebniswert mit Kategorie `TDS_UNSUPPORTED_TYPE` (SPEC-014).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- UDT, `json` und Table-Valued-Parameter — das Clientprofil nennt sie nicht als gemessen; ohne Messung gäbe es kein Orakel, und ein Handvektor allein wäre ein weiteres Vorkommen von `BEO-TDS/aussagen-ueber-messung-hinaus`.
- Abbildung zwischen Backendtypen und TDS-Typen — das ist der Backend-Vertrag ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), `Proposed`); der Codec nimmt TDS-Typen entgegen und kennt `SqlType` nicht.
- Netzwerk, Coroutinen, Kanäle und Streaming mit Rückstau — sie liegen in der Welle `welle-transport-tls`; hier arbeitet der Codec auf Bytes im Speicher.
- Nachweis gegen den Referenzclient und gegen einen echten SQL Server — er gehört in das Kompatibilitäts- und Referenzabgleich-Gate (SPEC-032, SPEC-050).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **PLP** (SPEC-003): MAX-Typen (`nvarchar(max)`, `varchar(max)`, `varbinary(max)`) in COLMETADATA, ROW und RETURNVALUE als Stück-Folge mit Terminator; Längenprüfung vor dem Puffern. **Auch als RPC-Parameter** im Parser aus `slice-codec-rpc-parser`, samt SQL-Text und Deklarationsliste von `sp_executesql` als MAX: das ist die Abweichung von [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) und SPEC-003, die jener Slice benennt (er lehnt heute die ganze Nachricht als `INVALID_TYPE` ab); der Test `PLP lehnt die ganze Nachricht ab, auch als SQL-Text und Deklaration` wechselt mit diesem Punkt von „abgelehnt“ zu „gelesen“.
- [x] **NBCROW** (SPEC-003): Null-Bitmap-Zeile neben ROW; Encoder wählt nicht selbst, sondern schreibt, was man ihm gibt.
- [x] **Weitere Typen** (SPEC-003, SPEC-014): `sql_variant` mit Basistyp `int` (gemessen am Echtserver-Mitschnitt); `text`, `ntext`, `image` und weitere `sql_variant`-Basistypen sind nicht belegt und bleiben `TDS_UNSUPPORTED_TYPE` als benannte Lücke in Matrix und Closure, ohne eigenen Slice (Entscheidung des Nutzers 2026-11-16, Wortlaut angepasst; vorher: „`text`, `ntext`, `image`, `sql_variant` nur, soweit `SPEC-021` sie als gesendet führt“, ein Beleg, den `SPEC-021` für keinen der vier trägt).

Nachweis-Pflichten für alle drei Punkte:

- [x] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>`; die Tests laufen in `make test` und sind grün.
- [x] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet (KDoc des Elements); die Closure-Notiz trennt *gemessen*, *Handvektor*, *Selbstkonsistenz* und *Festlegung* wie `slice-codec-tds-paket`.
- [x] Zerstückelte, überlange und zufällige Eingaben enden in einem Ergebniswert (SPEC-027, [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)).
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist (die Zeilen zu den Typen in `SPEC-003`).
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
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/token/` | update | `TdsType` (`PlpType` mit drei MAX-Typen), `TdsValue.BytesValue`, `NullBitmapRowToken`, neu `PlpValues` (TYPE_INFO, Wert, Längenprüfung) und `VariantValues` (`sql_variant`, nur Basistyp `int`), `TypeInfoWriter`, `ValueWriter`, `TokenWriters`; die Zerlegung hält die `when`-Blöcke unter der Grenze von detekt |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/rpc/` | update | `RpcType.plp`, `RpcTypeInfoReader` (PLP für `NVARCHAR`, `BIGVARCHAR`, `BIGVARBINARY`), neu `PlpReader` (Körper, Text), `RpcParameterReader`; `RpcCatalog` bleibt unverändert und liest SQL-Text und Deklaration über denselben Weg |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RpcParameters.kt`, `.../result/ValueMapping.kt`, `.../bulk/BulkLoadReader.kt`, `.../wire/ByteReader.kt`, `.../wire/ProtocolError.kt`, `.../dispatch/RpcBinder.kt`, `.../dispatch/RequestLoop.kt` | update | der Dispatch bildet `nvarchar(max)` als Wertparameter auf `UnicodeText` ab und lehnt `varchar(max)` und `varbinary(max)` ab; die Wertabbildung weist MAX-Spalten ab; das Massenladen lehnt PLP weiter ab; `ByteReader.u64Le` |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/token/`, `.../rpc/`, `.../dispatch/`, `.../testsupport/` | update | Handvektoren (Bytes eines echten SQL Servers aus dem Mitschnitt), `TokenPlpNbcRowTest`, `RpcPlpTest`, Test-Decoder (`TokenDecoder`), Roundtrip und Fuzz erweitert; die Tests der Ablehnung von PLP wechseln auf „gelesen“ |
| `spec/spezifikation.md`, `docs/plan/kompatibilitaetsmatrix.md`, `docs/plan/spec-abdeckung.md`, `docs/messungen/referenzabgleich.md`, Register `BEO-TDS/mitschnitt-luecken-ohne-traeger` | update | `SPEC-003` (MAX-Typen und NBCROW im Codec), `SPEC-013` (MAX-Typ als Wertparameter), Änderungsliste; Zeilen und Lücken 2 und 6 der Matrix |

Der Implementer liest den Stand des Token-Packages aus `done/` (`slice-codec-tds-paket`) und passt diese Tabelle an.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `slice-codec-tds-paket` liegt in `done/`, `make gates` ist auf `main` grün, und `spec/spezifikation.md` `SPEC-021` oder der Referenzabgleich nennt mindestens einen der Gegenstände als gesendet oder verlangt; ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2; Schnittvorschlag: PLP und NBCROW in einem Slice, die weiteren Typen in einem zweiten.
- `in-progress` → `open` (blockiert — Carveout?): Es gibt keinen Beleg, dass der Referenzclient die Typen sendet oder erwartet; der Slice bleibt dann `open`, und `TDS_UNSUPPORTED_TYPE` genügt.

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

- Offene Punkte aus [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) (Simulations-Backend), die diese Datei als Träger nennt: (1) der Typkatalog von `SqlType` und [SPEC-003](../../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog) umfasst `smallint`, `char`, `float`, GUID, binär, Zonenzeit und `xml` nicht, die das neutrale Schema von d-migrate kennt, und das Simulations-Backend weist solche Spalten beim Laden ab; (2) `UnicodeText` trägt keine Länge, das Backend meldet `NVARCHAR(4000)` statt `NVARCHAR(n)`. Setzung (Entscheidung des Nutzers 2026-10-31, nicht gemessen): Der Mindestkatalog von SPEC-003 und die bisherige Länge von `UnicodeText` bleiben unverändert; die Erweiterung des Typkatalogs und der `UnicodeText`-Länge geschieht ausschließlich in diesem Slice, gemessen am Referenzclient, nicht vorab; bis dahin ist ein Typ oder eine Länge außerhalb davon ein definierter Fehler. Die Aufnahme in die Liefer-Punkte von §2 folgt erst, wenn der Referenzclient-Beleg vorliegt. Folge für den Dispatch, die der Zuschnitt noch nicht trägt: `RpcParameters.sqlType` bildet nur `INTN` 4/8, `BITN`, `NVARCHAR`/`NCHAR`, `DECIMALN`/`NUMERICN`, `DATEN`, `TIMEN` und `DATETIME2N` ab und lehnt jeden anderen Parametertyp mit ERROR 50002 ab (Abweichung von `SPEC-003`, geführt als Lücke 15 der [Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md)); wer einen dieser Typen in `SqlType` aufnimmt, hebt diese Ablehnung für ihn auf und ändert damit das Verhalten der RPC-Parameter, nicht nur den Typkatalog des Ergebnisstroms (die Tests `RpcBinderTest > Typen ausserhalb des Mindestkatalogs sind abgelehnt` und `RpcDispatchTest > Eine Ablehnung beantwortet mit ERROR und DONE …` wechseln dann für ihn von „abgelehnt“ zu „abgebildet“). Das ist eine Aussage über den Ist-Zustand und eine Folge, keine Zusage; der Slice bleibt Vorrat. — **Ausgang:** teils eingetreten, Rest weiter offen. Eingetreten: `nvarchar(max)` als Wertparameter ist `UnicodeText` (Entscheidung des Nutzers 2026-11-16); die Tests `RpcBinderTest` und `RpcDispatchTest` wechseln für diesen Typ von „abgelehnt“ zu „abgebildet“. Weiter offen, Träger: Nutzer (§7): der Typkatalog von `SqlType` (`smallint`, `char`, `float`, GUID, binär, Zonenzeit, `xml`) und die Länge von `UnicodeText` sind unverändert, Matrix Lücke 15 „kein Träger“.
- **A-11 aus dem Mitschnitt-Lauf (Zuordnung vom 2026-11-06):** der echte Server sendet im TYPE_INFO von `decimal(10,2)` `DECIMALN` mit Länge 17, der Server dieses Repos mit Länge 9 (Precision, Scale und Werte gleich; `docs/messungen/referenzabgleich.md`, Ausnahme A-11, Register `BEO-TDS/mitschnitt-luecken-ohne-traeger`). Die Ursache ist vor einer Änderung zu untersuchen (Längenwahl je Precision nach MS-TDS, Belegstelle zu klären); gehört sie zur Typabbildung des Encoders, trägt sie dieser Slice; A-12 (NBCROW im Sequenzierer) trägt ein Folge-Slice (Nutzerentscheidung 2026-11-16), sonst wird sie ein eigener Slice. Ohne Belegstelle bleibt es eine Messung, keine Regel (`AGENTS.md` §3.8). — **Ausgang:** A-12 eingetreten als Folge-Slice: [slice-antwortform-nbcrow](../in-progress/slice-antwortform-nbcrow.md); A-11 weiter offen und **ohne Träger**: der Bau berührte `DECIMALN` nicht, die Ursache ist nicht untersucht (Register `BEO-TDS/mitschnitt-luecken-ohne-traeger` mit Träger-Vorschlag, Entscheidung beim Nutzer, §7).
- Es ist offen, ob der Referenzclient diese Formen in den gemessenen Szenarien sendet oder erwartet: das Clientprofil nennt sie als nicht gesendet oder ungemessen. Ohne Beleg ist der Slice Vorrat. — **Ausgang:** entfallen (beantwortet): im Mitschnitt steht `E7 FF FF` (`nvarchar(max)`) 16-mal in Anfragen, `A7 FF FF` und `A5 FF FF` kein Mal; `sql_variant` (Basistyp `int`) und NBCROW stehen in der Antwort des echten Servers (M-16); `text`, `ntext`, `image`, UDT, `json` und Table-Valued-Parameter kommen nicht vor.
- Handvektoren für PLP und NBCROW sind eine Lesart von MS-TDS, keine Messung (`BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/aussagen-ueber-messung-hinaus`). — **Ausgang:** eingetreten, benannt: die Matrix trennt je Zeile gemessen, Handvektor und Selbstkonsistenz; was ein Handvektor bleibt, steht in §7 und im Register `BEO-TDS/messung-am-echten-sql-server-fehlt` (Beleg `evidence/slice-codec-datentypen-erweiterung.md`).
- Strenge der statischen Analyse: weitere Typen vergrößern die `when`-Blöcke des Encoders; die Abhilfe ist Zerlegung im Code. — **Ausgang:** entfallen: `PlpValues` und `VariantValues` zerlegen die `when`-Blöcke; `detektMain` und `detektTest` sind in `make clean-build` des Verifiers grün, ohne Suppression.
- Zielbild-Anpassung nach Nutzerentscheidung 2026-11-16: als in Anfragen des Referenzclients beobachtet führen Spec (`SPEC-003`, Absatz „RPC-Parametertypen“) und Matrix nur `nvarchar(max)` (TYPE_INFO `E7 FF FF`, `zusatz-unprepare-curcmd`); `varchar(max)`, `varbinary(max)` und `xml` sind Handvektor. — **Ausgang:** entfallen (entschieden und nachgezogen).
- Größe: drei Liefer-Punkte. — **Ausgang:** entfallen: drei Liefer-Punkte, keine Rückführung nach §4.
- Aus dem Bau und den Berichten (nach der Planung ergänzt), je mit Ausgang:
  - NBCROW im Sequenzierer (A-12): der Encoder schreibt NBCROW auf Anweisung, der Sequenzierer ROW mit NULL (Entscheidung des Nutzers 2026-11-16). — **Ausgang:** eingetreten als Folge-Slice [slice-antwortform-nbcrow](../in-progress/slice-antwortform-nbcrow.md).
  - Obergrenze der Länge von `nvarchar(max)` als Wertparameter: keine eigene, die Nachricht begrenzt `SPEC-007` (Setzung, nicht gemessen). — **Ausgang:** weiter offen, Träger: Nutzer (§7).
  - Der Wertkörper der MAX-Typen in Anfragerichtung ist nicht gemessen (aufgezeichnet ist nur die TYPE_INFO `E7 FF FF`); `varchar(max)`, `varbinary(max)` und `xml` sind Handvektor. — **Ausgang:** weiter offen → `BEO-TDS/messung-am-echten-sql-server-fehlt`; Träger einer Messung: ein Mitschnitt des Referenzclients mit einem Wert über 4000 Zeichen, Entscheidung beim Nutzer.
  - Weitere Basistypen von `sql_variant`, NULL von `sql_variant` im ROW (Länge 0), `text`, `ntext`, `image`: kein Beleg. — **Ausgang:** weiter offen als benannte Lücke ohne eigenen Slice (Entscheidung des Nutzers 2026-11-16) → `BEO-TDS/messung-am-echten-sql-server-fehlt`; ein Bau braucht vorher einen Mitschnitt.
  - `nchar`, `char` und `binary` mit Länge `0xFFFF` bleiben `UNSUPPORTED_PLP` (Festlegung des Implementers, kein MAX-Typ dieser Spezifikation, nicht gemessen). — **Ausgang:** weiter offen, Träger: Nutzer (§7).
  - Die Antwortseite eines `nvarchar(max)`-OUTPUT-Werts über 4000 Zeichen (RETURNVALUE) ist nicht getestet (`ColumnMapping` bildet `UnicodeText` mit `maxLength` ab, Vorgabe 4000; Review F-11). — **Ausgang:** weiter offen → `BEO-TDS/messung-am-echten-sql-server-fehlt`; die Matrix Zeile 126 sagt es.

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

Geschlossen 2026-11-16 (Planner, Slice-Closure ohne Welle). Geprüfter Stand: Zweig `slice-codec-datentypen-erweiterung`, HEAD `5ef12076` (Basis `b028ca04`, 11 Commits, 36 Dateien, +1456 −132); danach nur Doku (Verifikationsbericht, Überschrift der RPC-Parametertypen, Folge-Slice, Register). Prüfung: Review [2026-11-16-246f3f8c](../../../reviews/2026-11-16-246f3f8c-slice-codec-datentypen-erweiterung-review.md) (fünf MEDIUM, drei LOW, vier INFO), Re-Review [2026-11-16-f131a2c3](../../../reviews/2026-11-16-f131a2c3-slice-codec-datentypen-erweiterung-re-review.md) (ein MEDIUM N-1, ein LOW N-2), Verifikation [2026-11-16-5ef12076](../../../reviews/2026-11-16-5ef12076-slice-codec-datentypen-erweiterung-verifikation.md) (kein HIGH, kein MEDIUM; F-1 und F-2 LOW, F-3 und F-4 INFO).

**Entscheidungen des Nutzers, 2026-11-16 (echte Antworten, hier als solche vermerkt).** (1) `sql_variant` wird lesend aufgenommen, nur Basistyp `int` (gemessen). (2) `nvarchar(max)` als Wertparameter ist `UnicodeText`. (3) NBCROW im Sequenzierer ist ein eigener Folge-Slice; A-12 bleibt bis dahin Ausnahme. (4) Die ältere Aussage von Spec und Matrix zu beobachteten MAX-Typen ist auf das Aufgezeichnete eingeschränkt (Zielbild an den Beleg). (5) DoD Punkt 3 hat den Wortlaut des Gelieferten; `text`, `ntext`, `image` und weitere `sql_variant`-Basistypen sind nicht belegt und bleiben `TDS_UNSUPPORTED_TYPE` als benannte Lücke in Matrix und dieser Closure, ohne eigenen Slice.

**Was hat funktioniert.** (a) PLP: `PlpValues` (TYPE_INFO, Wert, Längenprüfung vor dem Puffern) und `PlpReader` (Körper, Text) lesen und schreiben `nvarchar(max)`, `varchar(max)` und `varbinary(max)` in COLMETADATA, ROW, NBCROW und RETURNVALUE und als RPC-Parameter, SQL-Text und Deklaration von `sp_executesql` und `sp_prepexec` als MAX; der Test der Ablehnung wechselte auf „gelesen“ (`RpcFailureTest > PLP wird gelesen, auch als SQL-Text und Deklaration`). Im Dispatch ist nur `nvarchar(max)` Wertparameter (`UnicodeText`); `varchar(max)` und `varbinary(max)` werden gelesen und dann mit `SPEC-013` abgelehnt. (b) NBCROW: `NullBitmapRowToken` auf Anweisung des Aufrufers (Encoder; der Sequenzierer schreibt ROW). (c) `sql_variant` mit Basistyp `int` in COLMETADATA, ROW, NBCROW und RETURNVALUE (`VariantValues`). (d) Die Gegenproben des Verifiers: vier Mutationen (NULL-Zweig von PLP, `requireNullable` im NBCROW, Terminator, Basistyp von `sql_variant`) je rot und zurückgenommen.

**Beleg je DoD-Punkt.**

- Punkt 1, PLP: erfüllt. `TokenPlpNbcRowTest`, `RpcPlpTest`, `RpcFailureTest`; `make clean-build` und `make test` des Verifiers (1249 Tests).
- Punkt 2, NBCROW: erfüllt im Encoder (`TokenWriters.writeNullBitmapRow`, `TokenPlpNbcRowTest`); der Sequenzierer ist nach Entscheidung 3 nicht Teil, Träger [slice-antwortform-nbcrow](../in-progress/slice-antwortform-nbcrow.md).
- Punkt 3, `sql_variant` mit Basistyp `int`: erfüllt im neu gefassten Wortlaut (Entscheidung 5); Beleg `TokenPlpNbcRowTest > sql_variant mit Basistyp int gibt die Bytes des echten Servers wieder`. Die übrigen Typen sind nicht gebaut und benannte Lücke (siehe unten).
- Nachweis 1 (`@spec`, Tests grün): `make clean-build` Exit 0 und `make spec-abdeckung` Exit 0, die Abdeckungsmatrix danach unverändert (Verifikation §1, §7); Review F-3 (falsches `SPEC-028`) in `dc91934f` behoben.
- Nachweis 2 (Trennung der Aussagen): unten in dieser Notiz und in Matrix, Spec und KDoc.
- Nachweis 3 (zerstückelte, überlange, zufällige Eingaben, `SPEC-027`, [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)): `RpcPlpTest` (Stücke, Verkürzung jeder Nachricht, überlange Längen, zufällige Körper), `RpcFuzzTest`, `TokenRoundtripTest` (Verifikation §3). Grenze: der Encoder-Grenzfall von `limit` ist nur über die Prüf-Naht mit kleiner Grenze belegt (KDoc von `PlpValues`).
- Nachweis 4 (`make gates`): Exit 0, siehe „Gates“ unten.
- Nachweis 5 (Review): Review, Re-Review und Verifikation liegen vor.
- Nachweis 6 (Doku): Spec (`SPEC-003`, `SPEC-013`, Änderungsliste), Matrix, `referenzabgleich.md`; `make docs-check` Exit 0.
- Nachweise 7 bis 10: diese Notiz, das Register (unten), die Ausgänge in §6, die Paarungen (unten).

**Zuordnung je Aussage.**

- *Gemessen:* `E7 FF FF` (`nvarchar(max)`, Kollation `09 04 D0 00 34`) in Anfragen, 16-mal, ausschließlich in `zusatz-unprepare-curcmd`, Instanzen 2022 und 2025 (vom Verifier am Mitschnitt nachgezählt; `A7 FF FF` und `A5 FF FF` stehen in keiner Anfrage); die Bytes einer NBCROW-Antwort des echten Servers mit 14 Spalten (Mitschnitt `katalog-abfragen`, Zeile 64) mit den Bitmaps `00 3C` (symmetrisch, trennt die Bitreihenfolge nicht) und `00 32` (trennt sie); `sql_variant` mit Basistyp `int` im NBCROW (M-16, Einzelmessung je Instanz). `00 3E` steht im Mitschnitt und ist nicht getestet.
- *Handvektor nach `MS-TDS`:* der PLP-Wertkörper (Stücke, Terminator, unbekannte Gesamtlänge), TYPE_INFO von `varchar(max)` und `varbinary(max)`, NULL als acht Byte `0xFF`, NULL von `sql_variant` im ROW (Länge 0), die Bitmap-Breite.
- *Selbstkonsistenz:* Roundtrip und Fuzz (`TokenRoundtripTest`, `RpcFuzzTest`, zufällige Zerlegungen in `RpcPlpTest`).
- *Festlegung:* `nvarchar(max)` als Wertparameter ohne eigene Obergrenze (die Nachricht begrenzt `SPEC-007`); `nchar`, `char` und `binary` mit Länge `0xFFFF` bleiben `UNSUPPORTED_PLP`; die Grenze `TOO_LONG` von `PlpValues` ist die Array-Grenze, keine Heap-Grenze (Review F-9); andere Basistypen von `sql_variant` sind `TYPE_MISMATCH` beziehungsweise `TDS_UNSUPPORTED_TYPE`.

**Was ging anders als geplant.** (a) Der Plan sah NBCROW im Codec und `text`, `ntext`, `image`, `sql_variant` „soweit gesendet“ vor; gebaut sind PLP, NBCROW im Encoder und `sql_variant` mit Basistyp `int`; NBCROW im Sequenzierer ist ein eigener Slice, `text`, `ntext` und `image` sind nicht gebaut (kein Beleg). Der Wortlaut „soweit `SPEC-021` sie als gesendet führt“ trug für `sql_variant` nicht (Verifikation F-2): `SPEC-021` führt keinen der vier Typen als gesendet, der Beleg ist die Antwort des echten Servers und Entscheidung 1. (b) Die Nachbesserung zum Review (`dc91934f`) schrieb „am Referenzclient beobachtet“ für vier MAX-Typen, ohne den Mitschnitt zu lesen; der Re-Review (N-1) und die Zählung am Mitschnitt zeigten `nvarchar(max)` allein; Entscheidung 4 brachte das Zielbild an den Beleg (`5ef12076`, Überschrift in `ff9ada71`). (c) Die Dispatch-Folge, die der Plan offen ließ, ist entschieden (Entscheidung 2). (d) `RpcBinder.kt`, `RequestLoop.kt`, `ProtocolError.kt` und `spec-abdeckung.md` standen nicht in §3 (Review F-4, Re-Review N-2), nachgetragen.

**Nachbesserung ohne zweiten Review: kein Fall.** Nach dem Review `246f3f8c` folgten `dc91934f`, `5974fa68`, `f131a2c3` (vom Re-Review geprüft), danach `3514f32a` und `5ef12076`. Die Verifikation hat per Diff-Filter belegt: `git diff 246f3f8c..HEAD -U0 -- src`, jede geänderte Zeile gefiltert auf „beginnt nicht mit `/**`, `*`, `*/`, `//`“, ergibt keinen Treffer (11 Dateien, +38 −21: KDoc, Umbrüche darin und `@spec`-Tags); der Diff `f131a2c3..HEAD` berührt `src` nicht (Verifikation §5). Die Regel (`BEO-TDS/nachbesserung-ohne-zweiten-review`) verlangt den Re-Review für Quelltext, Testcode, Skripte und Konfiguration, nicht für Kommentar und Doku; sie greift hier nicht.

**Gates.** `make gates` Exit 0 auf dem End-Stand der Closure (Planner, 2026-11-16, Lauf 14 s): die Klassen `werkzeug`, `tests` und `stempel` sind übersprungen, weil ihre Eingaben seit dem grünen Lauf des Implementers unverändert sind (Stempel nach [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md), [ADR-0030](../../adr/0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md); `src/` ist seit `5ef12076` unberührt); die Klasse `immer` (`docs-check`, `flaeche`, `gate-zuordnung` und weitere) lief frisch. Den frischen Lauf der Tests und von detekt belegt `make clean-build` des Verifiers (Exit 0) auf demselben `src/`-Stand, `make e2e` Exit 0 (Verifikation §7); die Stempel kennt die CI nicht, sie läuft voll ([ADR-0023](../../adr/0023-gate-klassen-und-stempel.md)).

**Entscheidungen offen, Träger: Nutzer (Empfehlung des Planners ist ein Vorschlag, keine Setzung).**

1. Obergrenze der Länge von `nvarchar(max)` als Wertparameter (Festlegung ohne Beleg, die Nachricht begrenzt `SPEC-007`). Vorschlag: als Setzung belassen; eine Messung am echten Server nur, wenn ein Verbraucher eine engere Grenze braucht.
2. Weitere Basistypen von `sql_variant`, NULL im ROW, `text`, `ntext`, `image`: benannte Lücke ohne eigenen Slice (Entscheidung 5). Vorschlag: nur mit einem Mitschnitt, der sie zeigt (ein Lauf von d-migrate oder des Referenzclients), dann ein Bau-Slice.
3. `nchar`, `char`, `binary` mit Länge `0xFFFF` als `UNSUPPORTED_PLP` (Festlegung des Implementers, nicht gemessen). Vorschlag: beibehalten, bis ein Mitschnitt oder eine Belegstelle von `MS-TDS` das Gegenteil zeigt.
4. A-11 (`DECIMALN` mit Länge 17 statt 9) hat keinen Träger. Vorschlag: ein eigener Slice „Längenwahl `DECIMALN`“ mit Messung am echten Server und Belegstelle, oder nach Zerlegung ein Liefer-Punkt von [slice-antwortform-nbcrow](../in-progress/slice-antwortform-nbcrow.md); ohne Belegstelle bleibt es eine Messung, keine Regel (`AGENTS.md` §3.8).
5. Typkatalog von `SqlType` und Länge von `UnicodeText` ([ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) offene Punkte, Matrix Lücke 15, 14 RPC-Parametertypen, die der Parser liest und der Dispatch ablehnt): kein Träger. Vorschlag: erst, wenn ein Verbraucher (d-migrate) einen dieser Typen am Draht braucht, dann mit Messung.
6. Wertkörper der MAX-Typen in Anfragerichtung (Wert über 4000 Zeichen) und die Antwortseite eines `nvarchar(max)`-OUTPUT-Werts über 4000 Zeichen sind nicht gemessen. Vorschlag: ein Mitschnitt des Referenzclients mit einem langen Parameterwert, wenn die Messreihe `wire` das nächste Mal erweitert wird.

**Risiken (Ausgänge in §6).** Eingetreten: Dispatch-Folge (Typ `nvarchar(max)` abgebildet), Handvektoren benannt, A-12 als Folge-Slice. Entfallen: Frage der gesendeten Formen (beantwortet), Strenge der statischen Analyse, Zielbild-Anpassung, Größe. Weiter offen mit Träger: A-11 (ohne Träger, Nutzer), Obergrenze, Wertkörper in Anfragerichtung, weitere Basistypen, `UNSUPPORTED_PLP`, RETURNVALUE über 4000 Zeichen, Typkatalog.

**Register.** Fortgeschrieben, kein Zähler gesetzt: je `evidence/slice-codec-datentypen-erweiterung.md` in `BEO-TDS/aussagen-ueber-messung-hinaus` (89 Dateien; Re-Review N-1, Review F-6 und F-7, Verifikation F-1), `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (45; Review F-2 und F-5, Verifikation F-1), `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht` (23; Review F-4, Re-Review N-2), `BEO-TDS/test-naht-im-produktivcode` (4; Review F-1), `BEO-TDS/kommentar-nennt-abwesenden-oder-unwirklichen-zustand` (11; Review F-8), `BEO-TDS/dod-wortlaut-ueber-der-bauart` (10; Verifikation F-2; verwandt, nicht dieselbe Bauart: die Bedingung hing an einem Beleg, nicht an einem Werkzeug) und `BEO-TDS/messung-am-echten-sql-server-fehlt` (37; Handvektoren und Lücken oben); neu `BEO-TDS/plan-nennt-falsche-spec-kennung-fuer-tests` (1×; Review F-3: `SPEC-028` statt `SPEC-003` aus dem Plan in die Tags übernommen). `BEO-TDS/mitschnitt-luecken-ohne-traeger` nachgeführt (A-12 Träger [slice-antwortform-nbcrow](../in-progress/slice-antwortform-nbcrow.md), A-11 ohne Träger). Gesichtet, kein neues Auftreten: `BEO-TDS/nachbesserung-ohne-zweiten-review` (siehe oben, kein Fall), `BEO-TDS/spec-tag-nennt-lastenheft-kennung` (anderer Mechanismus, das Tag war gültig und falsch).

**Lese-Schritt (Repo ohne Wellen-Betrieb, diese Closure löst ihn aus).** Gelesen, was in der Ablage 3× erreicht hat und von diesem Slice berührt wurde: `aussagen-ueber-messung-hinaus` (89), `fortschreibung-laesst-zweite-fundstelle-stehen` (45), `plan-tabelle-nennt-neue-datei-nicht` (23), `messung-am-echten-sql-server-fehlt` (37), `kommentar-nennt-abwesenden-oder-unwirklichen-zustand` (11), `dod-wortlaut-ueber-der-bauart` (10), `test-naht-im-produktivcode` (4), `mitschnitt-luecken-ohne-traeger` (4). Feststellung: alle acht tragen bereits einen Ausgang (verkörpert oder Träger entschieden, je in ihrem `state.md`); keine erreicht mit diesem Slice die Schwelle neu, und keine Zeile ist neu zuzuweisen. Retirement-Check der Klassen mit Herkunfts-Anker: die Klasse ist in diesem Slice wiedergekehrt, keine Lockerung. Kein Zähler außer 3× erreicht; `plan-nennt-falsche-spec-kennung-fuer-tests` steht bei 1×.

**Folge-Slice:** [slice-antwortform-nbcrow](../in-progress/slice-antwortform-nbcrow.md) (Sequenzierer schreibt NBCROW, A-12; Messung gegen den echten Server und Kompatibilitätstest als Beleg; ohne Welle, Verantwortlich —, ist eine Datei in `open/`).

**Drei Paarungen** (Repo ohne Wellen-Betrieb, hier geprüft nach dem Move): *Anker* — diese Notiz verkörpert nichts und trägt kein Feld `liegt in`; nichts zu paaren. *Folge-Slice* — [slice-antwortform-nbcrow](../in-progress/slice-antwortform-nbcrow.md) liegt als Datei in `open/`. *Register* — acht Dateien `evidence/slice-codec-datentypen-erweiterung.md` (sieben fortgeschrieben, eine in der neuen Beobachtung `plan-nennt-falsche-spec-kennung-fuer-tests`), jede mit auflösendem Zeiger auf diese Datei in `done/`; `make docs-check` Exit 0.

**Lerneintrag (Steering Loop).**

- **Geschärft, keine neue Regel:** (i) Eine Herkunftsaussage („am Referenzclient beobachtet“, „gemessen“) nennt Quelle und Zahl, und der, der sie schreibt, liest die Quelle selbst: die Nachbesserung zu einem Befund über Belegumfang wiederholte die Fehlerklasse (`AGENTS.md` §3.8, `BEO-TDS/aussagen-ueber-messung-hinaus`, verkörpert in `AGENTS.md` und `.harness/skills/reviewer.md`; Retirement-Check: die Klasse ist wieder aufgetreten, die Regel bleibt). Die Zählung am Mitschnitt (16-mal, eine Datei, zwei Instanzen) hat den Streit entschieden, nicht eine zweite Lesart. (ii) Eine Fortschreibung sucht die Kernaussage im ganzen Repo, auch die Überschrift eines Absatzes, dessen Text sie einschränkt (`BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`, verkörpert; die Verifikation fand sie).
- **Neu benannt, Beobachtung 1×, nichts verkörpert:** Eine `SPEC-<NNN>` im Plan, die den Gegenstand nicht trägt, wandert in die `@spec`-Tags und in die Abdeckungsmatrix; kein Gate sieht sie (`BEO-TDS/plan-nennt-falsche-spec-kennung-fuer-tests`).
- **Für künftige Pläne (Planner):** Ein Liefer-Punkt mit „nur, soweit `<Quelle>` es als gesendet führt“ ist beim Schnitt gegen die Quelle zu lesen; führt sie keinen der Gegenstände, ist der Wortlaut nicht erfüllbar (hier: `SPEC-021`), und die Frage geht vor dem Start an den Nutzer, nicht an den Verifier.
- **Benannte Spec-Lücken** (kein `liegt in`, nichts verkörpert): weitere Basistypen von `sql_variant`, `text`, `ntext`, `image` (Matrix Zeile 128), Typkatalog von `SqlType` (Matrix Lücke 15), A-11 (`referenzabgleich.md`), Wertkörper der MAX-Typen in Anfragerichtung (Spec `SPEC-003`, Matrix Zeile 126).

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); `protocol` ist kein eigener Pfad-Cluster mit eigener Strukturregel. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`), alle Sub-Area `*`. Treffer: `BEO-TDS/aussagen-ueber-messung-hinaus` (7×) und `BEO-TDS/messung-am-echten-sql-server-fehlt` (3×), beide in §6. Ohne Treffer: die übrigen sechs.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
