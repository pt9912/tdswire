# Slice slice-codec-rpc-parser: RPC-Anfrage-Parser für den Katalog von ADR-0005 ohne Netzwerk

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle — Folge-Slice von `welle-protokollcodec`; die funktionale Welle für SQL_BATCH und RPC ist noch nicht geplant und nimmt ihn auf, sobald sie eröffnet wird.

**Bezug:** [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen) (Parametertypen der TYPE_INFO), [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md)

**Berührte Spec-Stellen:** SPEC-003 · SPEC-024 · SPEC-027 · SPEC-028 · SPEC-030 · ARC-007

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

**Ziel:** Ein reiner Parser ohne Netzwerk liest die RPC-Anfrage (Pakettyp RPC) in die Formen, die der Katalog in [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) und `contracts/client-profile.md` (§RPC-Katalog) nennt: `sp_executesql` (ProcID 10), `sp_prepexec` (ProcID 13), `sp_execute` (ProcID 12) und RPC per Prozedurname; Parameter mit Namen, Statusflags (OUTPUT), TYPE_INFO, NULL-Sentinels und Kollation; Fehler sind Ergebniswerte, keine ungefangene Ausnahme.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Ausführung, Prepare-Handle-Verwaltung je Sitzung und Fehlerantwort bei unbekanntem Handle — das ist Ablauf, nicht Parser; der Zustand gehört zum Zustandsautomaten ([ADR-0004](../../adr/0004-session-zustandsautomat.md), `Proposed`) und zum Backend-Vertrag ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), `Proposed`). Der Parser liefert die Handle-Zahl, entscheidet nichts.
- Unbekannte Katalogformen (`sp_prepare`, `sp_unprepare`, `sp_cursor*`, `SET FMTONLY`-Text, Transaktions-Pakettyp `0x0E`) — die Behandlung ist im Katalog offen; der Parser meldet eine unbekannte ProcID als eigenen Ergebniswert und legt keine Antwort fest ([ADR-0007](../../adr/0007-fehler-und-attention.md), `Proposed`).
- NBCROW, PLP und Typen außerhalb des Mindestkatalogs (Parameter-TYPE_INFO über die Typen von SPEC-003 hinaus) — dafür steht `slice-codec-datentypen-erweiterung`; dieser Slice lehnt solche Parameter als Ergebniswert ab.
  **Abweichung von [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) und SPEC-003:** die ADR (Folgen) verlangt vom RPC-Parser, PLP (MAX-Typen) zu dekodieren, und ihre Messmatrix sowie SPEC-003 führen `MAX` in `sp_executesql`-Parametern. Dieser Slice liefert das nicht; `spec/` und die ADR bleiben unberührt, der Träger der Lücke ist `slice-codec-datentypen-erweiterung`. Verhalten (im Test `PLP lehnt die ganze Nachricht ab, auch als SQL-Text und Deklaration`): ein PLP-Parameter lehnt die **ganze Nachricht** als `INVALID_TYPE` ab (kein Teilergebnis, der Parameter wird nicht übersprungen), auch hinter gültigen Parametern. SQL-Text und Deklarationsliste von `sp_executesql` laufen durch dieselbe TYPE_INFO-Lesung und werden als MAX ebenso abgelehnt; ob ein Client einen langen SQL-Text als MAX sendet, ist nicht gemessen.
- Netzwerk, Coroutinen, Kanäle — sie liegen in der Welle `welle-transport-tls`.
- Nachweis gegen den Referenzclient oder einen echten SQL Server — er gehört in das Kompatibilitäts- und Referenzabgleich-Gate (SPEC-032, SPEC-050); der Katalog ist nur am Testserver in TDS 7.4 ohne TLS und Pool gemessen (`BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **RPC-Kopf und Katalog-Erkennung** (SPEC-028, SPEC-030): ALL_HEADERS, Prozedur per ProcID oder Name (`NameLenProcID` als Zeichenzahl), Optionsflags; die vier Katalogformen werden erkannt, eine andere ProcID ist ein Ergebniswert.
- [x] **Parameter-Leser** (SPEC-003, SPEC-030): Name, Statusbyte (OUTPUT), TYPE_INFO des Mindestkatalogs samt Kollation und Precision/Scale, Wert mit Längenpräfix und NULL-Sentinel; für `sp_executesql` bleiben SQL-Text und Deklarationsliste getrennt vom Wertetransport. Ohne PLP/MAX: die Abweichung von [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) und SPEC-003 steht in §1 und §7, der Träger ist `slice-codec-datentypen-erweiterung`.
- [x] **Fehlerverhalten** (SPEC-027, [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)): zerstückelte, überlange, abgeschnittene und zufällige Eingaben (fester Startwert) enden in einem Ergebniswert mit Kategorie nach SPEC-011; die Textdarstellung des Parse-Ergebnisses enthält keine Parameterwerte.

Nachweis-Pflichten für alle drei Punkte:

- [x] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>`; die Tests laufen in `make test` und sind grün.
- [x] Jede Aussage des Parsers steht an einem Test oder ist als Festlegung gekennzeichnet (KDoc des Elements); die Orakel sind Handvektoren, und die Closure-Notiz trennt *gemessen*, *Handvektor*, *Selbstkonsistenz* und *Festlegung* wie `slice-codec-tds-paket` (`BEO-TDS/aussagen-ueber-messung-hinaus`).
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist. — nicht nötig: `spec/`, `contracts/` und `docs/plan/adr/` sind unverändert; die Lücke zu PLP/MAX steht als benannte Spec-Lücke in §7.
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
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/rpc/` | neu | RPC-Parser mit eigenem Ergebnismodell; nutzt `ByteReader` aus `protocol/wire`; keine Imports aus `transport`, `port`, `simulation`, `runtime` |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/rpc/` | neu | Handvektoren je Katalogform, Negativfälle, Fuzz mit festem Startwert; `@spec`-Tag je Test |
| `spec/spezifikation.md` | update, falls nötig | `SPEC-030` ist „Entwurf“; ändert die Umsetzung eine Zeile des Katalogs, wird die Spec im selben Slice nachgezogen (Lehre aus `slice-codec-tds-paket`: eine berührte Spec-Stelle steht in dieser Tabelle) |

- Reihenfolge: Kopf und Erkennung, dann Parameter-Leser, dann Fehlerverhalten.
- Der Implementer liest die API von `protocol/wire` und `protocol/token` aus `done/` (`slice-codec-tds-paket`) und passt diese Tabelle an.

**Angepasster Plan (Implementer, gegen den Ist-Zustand gemessen):**

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/wire/AllHeaders.kt` | neu | der ALL_HEADERS-Leser, den `SqlBatchParser` und der RPC-Parser teilen (aus `SqlBatchParser` herausgelöst, Verhalten unverändert) |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/wire/ProtocolError.kt` | update | zwei Fehlergründe der Kategorie `TDS_PROTOCOL_ERROR`: `INVALID_TYPE_INFO` (TYPE_INFO malformed) und `INVALID_RPC_FORM` (Katalogform verletzt) |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/rpc/` | neu | `RpcParser` (Kopf, Name oder ProcID, Optionsflags), `RpcCatalog` (Bindung nach Position), `RpcParameterReader`, `RpcTypeInfoReader`, Modell in `RpcRequest.kt`; Typen: `INTN`, `BITN`, `FLTN`, `MONEYN`, `DATETIMN`, `GUID`, `DECIMALN`/`NUMERICN`, `DATEN`, `TIMEN`, `DATETIME2N`, `DATETIMEOFFSETN`, `NCHAR`/`NVARCHAR`, `BIGCHAR`/`BIGVARCHAR`, `BIGBINARY`/`BIGVARBINARY`; PLP und `XML` werden als `INVALID_TYPE` abgelehnt (PLP: Abweichung von [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md), siehe §1); `RpcTypeCode.kt` hält die Typkennungen; `RpcParameterReader` begrenzt die Parameterzahl auf `MAX_PARAMETERS` (Festlegung ohne Messung, Verstoß ist `INVALID_RPC_FORM`) |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/rpc/` | neu | vier Testklassen statt einer `RpcParserTest.kt`: `RpcCatalogFormsTest` (Katalogformen), `RpcTypesTest` (TYPE_INFO und Werte je Typ), `RpcFailureTest` (Negativfälle, Obergrenze, Textdarstellung), `RpcFuzzTest` (Fuzz mit festem Startwert) |
| `src/test/kotlin/io/github/pt9912/tdswire/testsupport/RpcVectors.kt` | neu | gemeinsame Handvektoren |
| `src/test/kotlin/io/github/pt9912/tdswire/transport/framing/RpcAssemblyTest.kt` | neu | zerstückelte RPC-Nachrichten durch den `MessageAssembler` gegen den Parser; im Paket `transport`, weil `protocol` den Transport nicht importiert |
| `spec/spezifikation.md` | unverändert, falls die Tests keine Zeile von `SPEC-030` widerlegen | Prüfung nach den Tests |

Kopplung: der Parser nimmt die Länge der Zeittypen von `TypeInfoWriter.timeBytes` des Token-Encoders (zwei Richtungen innerhalb `protocol`, vom Gate erlaubt); die Klassengrenzen sind im Test an den Skalen 0 bis 7 belegt, die Zuordnung selbst ist dem Encoder entnommen (Selbstkonsistenz).

Eingegrenzt gegenüber dem Eingangsplan: der Wert wird nur für `INTN`, `BITN` und die Unicode-Texttypen gedeutet; alle anderen Typen liefern ihre Wertbytes unverändert (Deutung gehört der Datentypen-Erweiterung).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `slice-codec-tds-paket` liegt in `done/`, `make gates` ist auf `main` grün, [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) steht auf `Accepted`, und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2; Schnittvorschlag: Slice A = Kopf und `sp_executesql`, Slice B = `sp_prepexec`, `sp_execute` und Prozedurname.
- `in-progress` → `open` (blockiert — Carveout?): `SPEC-030` ändert sich so, dass der Katalog neu entschieden werden muss.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün, der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung je Aussage des Parsers sowie den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Der Katalog ist nur am Testserver gemessen (Mindestkatalog; unter Pooling und für `sp_prepare`, `sp_unprepare`, `sp_cursor*` ungemessen): der Parser belegt Handvektoren, nicht die Annahme durch den Client. — **Ausgang:** weiter offen, im Register (`BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, `BEO-TDS/messung-am-echten-sql-server-fehlt`); Träger `slice-qa-kompatibilitaet` und `slice-qa-reproduzierbarkeit`.
- Parameterwerte sind Nutzerdaten (SQL-Parameter): eine automatisch erzeugte Textdarstellung brächte sie in ein Log. — **Ausgang:** entfallen. Die Textdarstellungen nennen von Text, SQL-Text, Deklaration, Wertbytes, Namen und Zahlen nur Längen oder verbergen sie; der Test `Textdarstellung enthaelt weder Werte noch SQL-Text` sichert das, die Mutationen am `toString` von Text, Zahl und SQL-Text wurden rot gesehen (Verifikation M06, M12, M24; die überlebende M21 zu `BITN` ist durch einen ergänzten Test gedeckt).
- Größe: drei Liefer-Punkte, vier Katalogformen. — **Ausgang:** entfallen: die Rückführung nach §4 war nicht nötig, der Slice blieb bei drei Liefer-Punkten.
- Bei der Closure ergänzt, im Plan nicht vorhergesehen: der Parser lehnt PLP/MAX ab, obwohl [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) und SPEC-003 sie im RPC führen (Review F-1, Verifikation F-1). — **Ausgang:** eingetreten; Folge-Slice `slice-codec-datentypen-erweiterung` (nennt den RPC-Parameter seit dieser Closure in seiner DoD).
- Bei der Closure ergänzt: Parameterzahl und Speicher je Nachricht waren nur durch die Nachrichtengröße begrenzt (Review F-2). — **Ausgang:** eingetreten und im Slice behoben: `MAX_PARAMETERS` 4096, Verstoß ist `INVALID_RPC_FORM`; die Zahl ist eine Festlegung ohne Messung (§7). Kein Carveout, kein Folge-Slice.

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

- **Was hat funktioniert:** Der Schnitt trug: Kopf und Katalog, Parameter-Leser und Fehlerverhalten liegen in einem Slice, ohne dass die Rückführung nach §4 nötig wurde. Die Trennung nach Beleg-Stärke (Tabelle unten) machte Review und Verifikation prüfbar: Die Kennzeichnung *Festlegung ohne Orakel* stand schon im KDoc, und die Findings zu unmarkierten Aussagen ließen sich durch Kennzeichnen statt durch Umbau beheben. Die Verifikation fuhr 24 Mutationen (22 rot, 2 überlebt); beide Überlebenden sind seitdem durch Tests gedeckt.
- **Was ging anders als geplant:** (a) Der Parser lehnt PLP/MAX ab, obwohl [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) und SPEC-003 sie im RPC führen (siehe Abweichung unten); der Plan nannte es als Scope-Ausschluss, nicht als Abweichung. (b) Die Parameterzahl war nur durch die Nachrichtengröße begrenzt (Review F-2); `MAX_PARAMETERS` 4096 ist seitdem eine Festlegung ohne Messung. (c) Unmarkierte Aussagen über MS-TDS im KDoc (Bitnamen der Statusbits und Optionsflags, ProcIDs 11 und 15 als „frei gewählt“; Review F-1, F-3, F-9, Verifikation F-4) sind gekennzeichnet oder berichtigt. (d) Namen von Prozedur und Parameter stehen in der Textdarstellung nur noch als Länge. (e) Die Plan-Tabelle nannte eine Testdatei, geliefert sind vier; der Plan ist nachgezogen. Die Befunde sind eingearbeitet (Merge `8ad3ef0`).
- **Reports:** [Review](../../../reviews/2026-10-30-7c2e7e4-slice-codec-rpc-parser.md) (kein HIGH; F-1 und F-2 MEDIUM, F-3 bis F-5 LOW, F-6 bis F-9 INFO) und [Verifikation](../../../reviews/2026-10-30-7c2e7e4-verifikation-slice-codec-rpc-parser.md) (kein HIGH; F-1 bis F-3 MEDIUM, F-4 bis F-7 LOW), beide gegen den Merge `7c2e7e4`. Ein Validator-Lauf entfällt: der Slice liefert keinen End-Nutzer-Wert, der Parser hat noch keinen Aufrufer. `SPEC-028` trägt in den RPC-Tests bewusst kein Tag: die Kopf-Tests tragen `SPEC-030` (Verifikation F-6).

**Abweichung von [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) und SPEC-003 (PLP/MAX):** Die ADR (Folgen) verlangt vom RPC-Parser, PLP (MAX-Typen) zu dekodieren; ihre Messmatrix und SPEC-003 führen `MAX` in `sp_executesql`-Parametern. Dieser Slice liefert das nicht. Ist-Verhalten: Ein PLP-Parameter lehnt die **ganze Nachricht** als `INVALID_TYPE` ab (Kategorie `TDS_UNSUPPORTED_TYPE`), ohne Teilergebnis und auch hinter gültigen Parametern; ebenso SQL-Text und Deklarationsliste von `sp_executesql`, wenn sie als MAX gesendet werden (Test `PLP lehnt die ganze Nachricht ab, auch als SQL-Text und Deklaration`). Ob ein Client einen langen SQL-Text als MAX sendet, ist nicht gemessen. Folge: Bis `slice-codec-datentypen-erweiterung` (`open/`, seit dieser Closure mit dem RPC-Parameter in der DoD) geliefert ist, kann ein Client mit MAX-Parameter keinen RPC absetzen. `spec/` und die ADR sind in diesem Slice unberührt.

**Beleg des absichtlich roten Gate-Laufs:** Verifikation §1: `STATUS_OUTPUT = 0x02` im Klon bei `7c2e7e4`, `make gates` endet mit Exit 2, vier RPC-Tests `FAILED`, kein Stempel.

**Mutationsstand** (Maß dafür, ob die Tests Abweichungen sehen, nicht für Protokolltreue): Verifier 24 Mutationen, 22 rot, 2 überlebt (M21 `BITN` in der Textdarstellung, M22 `useDefault`); beide sind nach den Befunden durch Tests gedeckt, ohne erneute Mutation durch den Verifier. Implementer nach den Befunden: 7 Mutationen rot gesehen (Angabe des Implementers im Handoff; die Liste liegt nicht im Repo).

**Zuordnung Test | Festlegung je Aussage des Parsers.** Vier Stärken: *gemessen* heißt am Spike-Testserver in TDS 7.4 ohne TLS und Pool beobachtet ([ADR-0005](../../adr/0005-rpc-und-prepared-statements.md), Messmatrix); *Handvektor* heißt von Hand aus der Lesart von MS-TDS gerechnet, ohne Text von MS-TDS im Repo (SPEC-020: extern), also eine Lesart und keine Messung; *Selbstkonsistenz* heißt Code gegen Code; *Festlegung ohne Orakel* heißt Setzung ohne Messung und ohne unabhängigen Vektor. Diese Notiz nennt keine Abschnittsnummer von MS-TDS.

| Stärke | Aussage | Beleg im Repo |
|---|---|---|
| gemessen | Die vier Katalogformen `sp_executesql` (ProcID 10), `sp_prepexec` (13), `sp_execute` (12) und RPC per Prozedurname; `OptionFlags` `0x0002` bei `sp_executesql`, `0x0000` bei Prozedurname; Handle als `INTN` 4 mit OUTPUT (−1 beim Erstaufruf von `sp_prepexec`); Maxlen eines Unicode-Parameters ist das Doppelte der Zeichenzahl | Handvektoren in `RpcCatalogFormsTest` je Form, dem Wert nach aus der Messmatrix; gemessen am Testserver, nicht an einem Microsoft-SQL-Server und nicht unter TDS 8.0 oder Pooling |
| Handvektor | ALL_HEADERS (Gesamtlänge, Header aus Länge, Typ, Inhalt); `NameLenProcID` als Zeichenzahl, `0xFFFF` leitet eine ProcID ein; Parameterkopf (Namenslänge in Zeichen, Statusbyte mit OUTPUT in Bit 0); Typkennungen der 18 Typen; Größenklassen von `INTN`, `FLTN`, `MONEYN`, `DATETIMN`, `GUID`; Dezimalgrößen 5/9/13/17 samt Vorzeichenbyte; Kollation mit 5 Byte nur bei Zeichentypen; Zählwerte `u16` mit `0xFFFF` als NULL; Längenbyte-Typen mit Länge 0 als NULL | `RpcCatalogFormsTest`, `RpcTypesTest`, `RpcFailureTest`; Verifikation M01 bis M05, M16, M19, M23 rot. Die Vektoren teilen die Lesart von MS-TDS mit dem Parser (Review F-9) |
| Selbstkonsistenz | Länge der Zeittypen (`DATEN`, `TIMEN`, `DATETIME2N`, `DATETIMEOFFSETN`) aus `TypeInfoWriter.timeBytes` des Token-Encoders; Hex-Vektor einer TYPE_INFO gegen die vom `TypeInfoWriter` gebaute Bytefolge | `RpcTypesTest` „Datums- und Zeittypen lesen die Laenge aus dem Scale“, `RpcCatalogFormsTest` „Hex-Vektor stimmt mit dem im Code gebauten Vektor ueberein“; der Hex-Vektor steht zweimal im Code (Quelltext und Test), nicht unabhängig vom Encoder |
| Selbstkonsistenz | Zerstückelte, überlange, abgeschnittene und zufällige Eingaben enden in einem Ergebniswert mit Kategorie (Startwert `20261030`, 6000 Läufe, jede Verkürzung der Vektoren); eine zerstückelte RPC-Nachricht ergibt durch den `MessageAssembler` dasselbe Parse-Ergebnis | `RpcFuzzTest`, `RpcFailureTest` „Abgeschnittener letzter Parameter…“ und „Ueberzaehlige Bytes…“, `RpcAssemblyTest` |
| Festlegung ohne Orakel | Positionale Deutung der Parameter von `sp_prepexec` und `sp_execute` (Namen ignoriert); `sp_executesql` verlangt zwei Textparameter (SQL-Text, Deklaration), `sp_prepexec` drei feste Parameter | KDoc von `RpcParser`, `RpcCatalog`; `RpcCatalogFormsTest`, Verifikation M07, M10, M11 rot. Die Positionen führt SPEC-030; als gemessen ausgewiesen ist nur `sp_executesql` ohne Namen |
| Festlegung ohne Orakel | NULL-Regeln (Länge 0 bei Längenbyte-Typen, `0xFFFF` bei Zählwert-Typen); Wertlänge höchstens so lang wie die höchste Länge der TYPE_INFO; Unicode-Text mit gerader Byte-Zahl; Dezimalgrößen, Precision 1 bis 38, Scale höchstens Precision (bei Zeittypen höchstens 7) | KDoc von `RpcParameterReader`, `RpcTypeInfoReader`; `RpcFailureTest` „Wertlaenge gegen TYPE_INFO…“, „TYPE_INFO ausserhalb der Grenzen…“; Verifikation M13 bis M16 rot |
| Festlegung ohne Orakel | `INTN` ist bei 1 Byte vorzeichenlos; `BITN` liest jeden Wert ungleich 0 als wahr; das Statusbyte wird unverändert übernommen, reservierte Bits werden nicht geprüft; Bit 1 als Standardwert ist eine Lesart von MS-TDS | `RpcTypesTest` „INTN liest 1 Byte…“, „Laenge 0 ist NULL und BITN liest wahr und falsch“, „Statusbyte setzt output und useDefault unabhaengig“; Verifikation M19 rot |
| Festlegung ohne Orakel | Eine unbekannte ProcID ist `RpcCall.UnknownProcedure` (ein Ok-Ergebnis, die Parameter werden nicht gelesen); der Parser legt keine Antwort fest | `RpcCatalogFormsTest` „Unbekannte ProcID…“; die Antwort gehört [ADR-0007](../../adr/0007-fehler-und-attention.md) (`Proposed`) |
| Festlegung ohne Orakel | Keine Batch-Flags: die Parameter reichen bis zum Ende der Nachricht, eine zweite RPC in derselben Nachricht wird nicht erkannt; Table-Valued-Parameter werden nicht erkannt | KDoc von `RpcParameterReader`; kein eigener Test (siehe offene Punkte unten) |
| Festlegung ohne Orakel | `MAX_PARAMETERS` 4096 je Nachricht, Verstoß ist `INVALID_RPC_FORM`; der Wert ist gesetzt, nicht gemessen | `RpcFailureTest` „Mehr Parameter als die Obergrenze…“ (4096 gelesen, 4097 und 100000 abgelehnt) |
| Festlegung ohne Orakel | Zuordnung der Fehlergründe zu Kategorien: `INVALID_TYPE_INFO` und `INVALID_RPC_FORM` ergeben `TDS_PROTOCOL_ERROR`, `INVALID_TYPE` ergibt `TDS_UNSUPPORTED_TYPE`; die Textdarstellung enthält keine Werte, keinen SQL-Text und Namen nur als Länge | `RpcFailureTest` „Textdarstellung enthaelt weder Werte noch SQL-Text“; `ProtocolError.kt`; gemessen ist keine Fehlerantwort |
| nicht gemessen | Typgrößen von `FLTN`, `MONEYN`, `DATETIMN` und `GUID`; der Zuschlag von 2 Byte bei `DATETIMEOFFSETN`; TDS 8.0 und Pooling; ein echter SQL Server | nur Handvektor und Selbstkonsistenz (Register unten); Träger `slice-qa-kompatibilitaet` und `slice-qa-reproduzierbarkeit` |
| nicht erkannt | Mehrere RPCs in einer Nachricht (Batch-Flag), Table-Valued-Parameter, PLP/MAX und `XML` | PLP/MAX: Abweichung oben, Träger `slice-codec-datentypen-erweiterung`; `XML` und Typen außerhalb der 18: `INVALID_TYPE`, Test `PLP, XML und unbekannte Typen…` |

**Offene Punkte der Closure mit Träger** (Paarung Folge-Slice, gegen den Inhalt der Dateien geprüft):

| Offener Punkt | Träger | Stand |
|---|---|---|
| PLP/MAX in RPC-Parametern, auch als SQL-Text und Deklarationsliste | `slice-codec-datentypen-erweiterung` (`open/`) | DoD-Punkt „PLP“ nennt die RPC-Parameter seit dieser Closure |
| Table-Valued-Parameter | `slice-rpc-executesql-parameter` (`open/`, §1: nicht im Katalog, ergibt den definierten Fehler); im Parser ein unbekannter Typ, also `INVALID_TYPE` | bewusst nicht unterstützt, Fehlerwert |
| Mehrere RPCs in einer Nachricht (Batch-Flag) | kein Träger-Slice: der Katalog von [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) kennt es nicht | bewusst nicht unterstützt; die Folgebytes werden als Parameter gelesen, ob das einen Fehlerwert ergibt, hängt von den Bytes ab und ist nicht getestet |
| Messung des Katalogs am Referenzclient, unter TDS 8.0 und Pooling | `slice-qa-kompatibilitaet` (DoD-Punkt 3), `slice-qa-reproduzierbarkeit` | laufende Beobachtungen im Register |

- **Gegenstand:** entfällt (der Slice lieferte, mit der benannten Einschränkung PLP/MAX).
- **Steering-Loop-Eintrag:** **Benannte Spec-Lücke, offen:** SPEC-003 und SPEC-030 beschreiben das Ziel (MAX-Parameter in `sp_executesql`, PLP im RPC-Parser), und [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) verlangt es; die Abweichung des Parsers (PLP/MAX lehnt die ganze Nachricht ab) ist dort nicht vermerkt. Das ist eine Lücke in `spec/`, die dieser Slice nicht schließt; sie schließt sich mit `slice-codec-datentypen-erweiterung` oder durch einen Vermerk in SPEC-030 durch die dafür zuständige Rolle. Kein `liegt in`, weil in einer Spec und nicht an einem Zielort verkörpert. Gezählt, nicht verkörpert: eine Folge, die eine ADR verlangt, und der Slice, der sie trägt, können auseinanderlaufen, ohne dass ein Sensor es sieht; die Abweichung wurde erst im Review gefunden (Review F-1, Verifikation F-1). Eine neue Beobachtung ist das nicht, weil `BEO-TDS/offene-punkte-ohne-traeger` offene Punkte einer ADR meint und nicht diese Folge; sie wird hier nur benannt. Die Regel aus `AGENTS.md` §3.8 ist schon verkörpert; dieser Slice ist ein weiteres Auftreten von `BEO-TDS/aussagen-ueber-messung-hinaus` trotz der Regel und keine Schärfung.
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-codec-rpc-parser.md` ergänzt in drei vorhandenen Beobachtungen, keine neue angelegt. `BEO-TDS/aussagen-ueber-messung-hinaus`: unmarkierte Aussagen über MS-TDS im KDoc (Review F-1, F-9, Verifikation F-4). `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`: der RPC-Katalog ist nur unter TDS 7.4 ohne Pool gemessen. `BEO-TDS/messung-am-echten-sql-server-fehlt`: der Parser ist nur gegen Handvektoren und den Spike-Testserver belegt. Kein neues Auftreten für `BEO-TDS/offene-punkte-ohne-traeger` (die offenen Punkte oben haben Träger als Datei) und für die übrigen sechs Beobachtungen. Die Zähler folgen aus den Dateien; alle drei standen schon vor diesem Slice über der Schwelle, ihre `state.md` bleiben unverändert.
- **Risiken aus §6:** alle fünf tragen genau einen Ausgang (siehe §6): zwei entfallen, zwei eingetreten (eines mit Folge-Slice, eines im Slice behoben), eines weiter offen im Register.
- **Drei Paarungen:** Anker: kein Eintrag trägt `liegt in`, die Paarung entfällt. Folge-Slice: `slice-codec-datentypen-erweiterung` und `slice-rpc-executesql-parameter` existieren in `open/` und führen die Punkte in ihrem Inhalt. Register: drei Evidence-Dateien, siehe oben.

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

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`), alle Sub-Area `*`. Treffer: `BEO-TDS/aussagen-ueber-messung-hinaus` (7×, Muster in KDoc; Gegenmittel in §2), `BEO-TDS/messung-am-echten-sql-server-fehlt` (3×) und `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` (3×; Katalog nur am Testserver gemessen, §6). Ohne Treffer: die übrigen fünf.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
