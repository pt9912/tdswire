# Verifikationsbericht: 7c2e7e4 (slice-codec-rpc-parser) — 2026-10-30

**Review-Art:** Verifikation (DoD- und Entscheidungs-Konformität, Plan-vs-Code), Modul 11

**Gegenstand:** Merge `7c2e7e4` auf `main`, Slice `docs/plan/planning/done/slice-codec-rpc-parser.md`. Diff `4cddbc3..7c2e7e4` (15 Dateien): `protocol/rpc` (sechs Dateien), `protocol/wire/AllHeaders`, `ProtocolError`, `SqlBatchParser`, vier Testklassen unter `protocol/rpc`, `testsupport/RpcVectors`, der Slice-Plan. Zwischen `7c2e7e4` und dem Stand des Working Trees während der Läufe (`95c5c56`, am Ende `7c9624c`) ist unter `src/main/**/protocol` und `src/test/**/protocol` nichts geändert; hinzugekommen ist nur `testsupport/TransportSupport.kt` (Transport-Slice). Die Gate-Läufe liefen auf einem frischen Klon, ausgecheckt auf `7c2e7e4`.

**Bezug:** [TDS-FA-04](../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-05](../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-FA-10](../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität); [ADR-0004](../plan/adr/README.md), [ADR-0005](../plan/adr/README.md), [ADR-0006](../plan/adr/README.md), [ADR-0007](../plan/adr/README.md).

**Modell:** claude-sonnet-5-5 · **Datum:** 2026-10-30

**Methode:** Behauptung des Implementers ≠ Bestätigung. Alle Läufe in Docker (`make`, `docker build --target test`) auf Kopien im Scratchpad (`tar` des Working Trees ohne `.git` für die Mutationen, `git clone` + `checkout 7c2e7e4` für die Gate-Läufe). Das Repo selbst blieb unverändert; `git status --short` zeigt am Ende `?? .claude/worktrees/` (Zustand vor Beginn) und die Datei dieses Berichts. Der Review-Report `docs/reviews/2026-10-30-7c2e7e4-slice-codec-rpc-parser.md` lag während der Läufe untracked vor; ich habe ihn nicht als Beleg verwendet.

---

## 1. Selbst gefahrene Sensoren

| Sensor | Ergebnis |
|---|---|
| `docker build --target test --no-cache` (Working Tree) | `BUILD SUCCESSFUL`, 120 Tests PASSED, 0 FAILED; davon 22 unter `protocol/rpc` (7 + 8 + 1 + 6) |
| `make gates` auf Klon bei `7c2e7e4` | Exit 0: `baseline-verify` OK (54 Dateien), d-check 141 Dateien, 0 Befunde, `detekt`, `build`, `detekt-selbsttest`, `test`; Stempel `.harness/state/gates-passed.{head,diffsha}` geschrieben |
| Roter Gate-Lauf: im Klon bei `7c2e7e4` `STATUS_OUTPUT = 0x02` gesetzt, dann `make gates` | Exit 2; vier RPC-Tests `FAILED`; **kein** Stempel (`.harness/state/` enthält nur `detekt-selbsttest.ok`). Der rote Lauf wirkt weiterhin. |
| Mutationsstichprobe | 24 Mutationen, siehe §3: 22 rot, 2 Überlebende |

## 2. DoD-Punkte einzeln

| # | DoD-Punkt | Urteil | Beleg der Verifikation |
|---|---|---|---|
| 1 | **RPC-Kopf und Katalog-Erkennung** (SPEC-028, SPEC-030): ALL_HEADERS, ProcID oder Name (`NameLenProcID` als Zeichenzahl), Optionsflags, vier Formen, andere ProcID als Ergebniswert | **bestätigt** | `RpcParser.read`: `AllHeaders.read`, `lead == 0xFFFF` → ProcID, sonst Name mit `utf16(length)` (Zeichen); Länge 0 → `INVALID_LENGTH`; `RpcCatalog.bind` für 10/13/12, sonst `UnknownProcedure(procId)` ohne Parameterlesung. Tests: `sp_executesql…`, `sp_prepexec…`, `sp_execute…`, `RPC per Prozedurname zaehlt Zeichen…`, `RPC per Name ohne Parameter und mit Optionsflags` (`0x0002`), `Unbekannte ProcID…`. Mutationen M01, M07, M08, M09 rot (zu Katalogform und Handle: M10, M11, M18, M20) (§3). Die ALL_HEADERS-Herauslösung aus `SqlBatchParser` ist verhaltensgleich (Diff gelesen; `SqlBatchParserTest > Fehlerhafter ALL_HEADERS-Block…` fiel unter M08 rot). |
| 2 | **Parameter-Leser** (SPEC-003, SPEC-030): Name, Statusbyte (OUTPUT), TYPE_INFO samt Kollation und Precision/Scale, Längenpräfix, NULL-Sentinel; Text und Deklarationsliste getrennt vom Wertetransport | **bestätigt, mit Einschränkung F-1 (PLP) und F-3** | OUTPUT-Bit `0x01` (M03 rot, 5 Tests), NULL-Sentinel `0xFFFF` (M04 rot) und Länge 0 (M05 rot), Vorzeichen von `INTN` (M19 rot), Kollation (M23 rot), Scale/Precision (M16 rot, Test `NUMERICN und DECIMALN…`). `ExecuteSql`/`PrepExec` tragen `statement` und `declarations` als eigene Felder, Wertparameter in `parameters`; Vertauschung ist rot (M10, M11). Einschränkung: PLP/`MAX` und `XML` werden abgelehnt (F-1). `fDefaultValue`/`useDefault` hat keinen Test (M22 überlebt, F-3). |
| 3 | **Fehlerverhalten** (SPEC-027, [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität)): zerstückelte, überlange, abgeschnittene, zufällige Eingaben → Ergebniswert mit Kategorie nach SPEC-011; Textdarstellung ohne Parameterwerte | **bestätigt, mit Befund F-3** | `guarded` fängt `ProtocolViolation`; Kategorien: `INVALID_TYPE_INFO`, `INVALID_RPC_FORM` → `TDS_PROTOCOL_ERROR` (`ProtocolError.kt`), `INVALID_TYPE` → `TDS_UNSUPPORTED_TYPE`. *Abgeschnitten:* Test `Abgeschnittener letzter Parameter…` prüft jede Länge innerhalb des letzten Parameters, Fuzz kürzt jeden Vektor auf alle Längen `0..n`. *Überlang:* `Ueberzaehlige Bytes hinter den Parametern…` (Anhang von 1 bis 3 Byte, vier Vektoren). *Zufall:* `RpcFuzzTest`, Startwert `20261030`, 6000 Läufe mit Rauschen, mutierten Vektoren und aus Katalog-TYPE_INFO zusammengesetzten Nachrichten; er prüft zusätzlich, dass Wertart und TYPE_INFO zusammenpassen (M07 und M14 färben den Fuzz-Test rot). *Zerstückelt:* der Parser nimmt die zusammengesetzte Nachricht; „zerstückelt“ ist als Verkürzung belegt, nicht als Zerlegung in Pakete (die gehört dem `MessageAssembler`; ein RPC-Lauf durch ihn fehlt, F-5). Textdarstellung: §5; ein Überlebender (M21, `BitValue`) zeigt eine Lücke im Test (F-3). |
| 4 | Jeder Test trägt `@spec SPEC-<NNN>`; Tests laufen in `make test`, grün | **bestätigt** | Je Datei gleich viele `@Test` und `@spec SPEC`-Blöcke (7/7, 8/8, 1/1, 6/6 = 22/22, `grep -c`). Verwendete IDs: SPEC-003 (9×), SPEC-027 (2×), SPEC-030 (11×); alle drei existieren in `spec/spezifikation.md` (Z. 275, 353; SPEC-027 in der Tabelle Z. 603). Zuordnung passend; SPEC-028 (ALL_HEADERS) trägt kein RPC-Test, obwohl der Slice sie nennt (F-6). |
| 5 | Jede Aussage an einem Test oder als Festlegung gekennzeichnet; Closure-Notiz trennt *gemessen · Handvektor · Selbstkonsistenz · Festlegung* | **nicht bestätigt (Closure-Teil offen); KDoc-Teil siehe §6** | Haken steht zu Recht leer, §7 des Slice ist „Wird bei der Closure gefüllt“. Im Code sind die Festlegungen in den KDoc-Köpfen von `RpcParser`, `RpcCatalog`, `RpcParameterReader`, `RpcTypeInfoReader` ausdrücklich als „Festlegungen (ohne Orakel)“ gekennzeichnet; die Testklassen sagen „Handvektoren, von Hand nach MS-TDS gerechnet“; der Gegenrechnungs-Test sagt „Selbstkonsistenz“. Bewertung in §6. |
| 6 | `make gates` grün | **bestätigt** | Exit 0, selbst gefahren (§1). |
| 7 | Review durchgeführt, Report unter `docs/reviews/` | **Report liegt vor, Haken offen** | `docs/reviews/2026-10-30-7c2e7e4-slice-codec-rpc-parser.md` existiert, ist zum Zeitpunkt der Läufe noch nicht committed. Das Setzen des Hakens ist Sache des Planners. |
| 8 | Doku-Update bei berührtem öffentlichem Vertrag | **bestätigt (kein Update nötig), mit F-1** | `spec/spezifikation.md`, `contracts/` und `docs/plan/adr/` zwischen `4cddbc3` und `7c2e7e4` unverändert (`git diff --stat`). Der Plan nennt das als Option („unverändert, falls die Tests keine Zeile von SPEC-030 widerlegen“); die PLP-Ablehnung widerlegt keine Zeile der Tabelle, ist aber enger als der Text von SPEC-003 (F-1). |
| 9 | Closure-Notiz mit Steering-Loop-Lerneintrag | **offen, nicht fällig** | §7 leer. |
| 10 | Beobachtungs-Register fortgeschrieben | **offen, nicht fällig** | Kein Eintrag durch diesen Slice unter `docs/plan/planning/observations/`; `aussagen-ueber-messung-hinaus` ist als Treffer im Plan §8 genannt. Das Muster tritt in kleiner Form wieder auf (F-4). |
| 11 | Risiken aus §6 tragen einen Ausgang | **offen, nicht fällig** | Alle drei stehen auf „offen bis zur Closure“. Das Risiko „Parameterwerte in Textdarstellung“ lässt sich nach §5 mit Einschränkung F-3 als „nicht eingetreten“ schließen; Beleg: Tests und M06/M12/M24. |
| 12 | Drei Paarungen (Anker · Folge-Slice · Register) | **offen, nicht fällig** | Folge-Slice `slice-codec-datentypen-erweiterung` liegt vor und nennt den RPC-Parser (§3, Zeile `protocol/rpc`: „update, nur falls nötig“). |

## 3. Mutationsliste

Jede Mutation lief in einer frischen Kopie des Repos (`docker build --target test --progress=plain .`), nach genau einer Änderung per `perl -0pi`; geprüft wurde, dass die Änderung angewendet wurde und kein Kompilierfehler entstand. „rot“ heißt: Exit ≠ 0 und mindestens ein Test `FAILED`.

| ID | Datei · Änderung | Ergebnis |
|---|---|---|
| M01 | `RpcParser.readName`: `utf16(length)` → `utf16(length / 2)` (Namenslänge als Bytes statt Zeichen) | rot (14 Tests: Prozedurname, Fuzz-Kopf, Datums-Typen, …) |
| M02 | `RpcParameterReader`: Parametername `utf16(u8())` → `utf16(u8() / 2)` | rot (6) |
| M03 | `STATUS_OUTPUT = 0x01` → `0x02` | rot (4: `sp_executesql…`, `sp_prepexec…`, `Textdarstellung…`, `Abgeschnittener…`) |
| M04 | `NULL_COUNTED = 0xFFFF` → `0x7FFF` | rot (4) |
| M05 | `NULL_PREFIXED = 0` → `0xFF` | rot (5) |
| M06 | `TextValue.toString` nennt den Text | rot (`Textdarstellung enthaelt weder Werte noch SQL-Text`) |
| M07 | `PREPEXEC_FIXED = 3` → `2` | rot (3, darunter der Fuzz-Test) |
| M08 | `AllHeaders`: Obergrenze `total > messageSize - start` entfernt | rot (`Fehlerhafter Kopf…`, `SqlBatchParserTest > Fehlerhafter ALL_HEADERS-Block…`) |
| M09 | `RpcParser`: Prüfung Namenslänge 0 entfernt | rot (`Fehlerhafter Kopf…`) |
| M10 | `RpcCatalog.executeSql`: Text und Deklaration vertauscht | rot (`sp_executesql trennt Text, Deklaration…`) |
| M11 | `RpcCatalog.prepExec`: Deklaration und Text vertauscht | rot (`sp_prepexec liefert Handle, Deklaration, Text…`) |
| M12 | `ExecuteSql.toString` nennt den SQL-Text | rot (`Textdarstellung…`) |
| M13 | `countedLength`: Obergrenze gegen TYPE_INFO-Länge entfernt | rot (`Wertlaenge gegen TYPE_INFO…`) |
| M14 | `readPrefixed`: Prüfung `length != expected` entfernt | rot (`Wertlaenge…`, Fuzz-Test) |
| M15 | `readText`: Prüfung auf gerade Byte-Zahl entfernt | rot (`Wertlaenge…`) |
| M16 | `RpcTypeInfoReader.decimal`: `scale > precision` nicht mehr geprüft | rot (`TYPE_INFO ausserhalb der Grenzen…`) |
| M17 | PLP-Ablehnung entfernt (`length == PLP_MARKER && false`) | rot (`PLP, XML und unbekannte Typen…`) |
| M18 | `RpcCatalog.handle`: Größe 4 des Handles nicht mehr geprüft | rot (`Verletzte Katalogform ist INVALID_RPC_FORM`) |
| M19 | `RpcParameterReader.integer`: Vorzeichenerweiterung entfernt | rot (`INTN liest 1 Byte vorzeichenlos…`) |
| M20 | `RpcCatalog.handle`: OUTPUT-Pflicht von `sp_prepexec` entfernt | rot (`Verletzte Katalogform…`) |
| M21 | `RpcValue.BitValue.toString` nennt den Wert | **überlebt** (rc=0, 0 FAILED) |
| M22 | `STATUS_DEFAULT = 0x02` → `0x04` (`useDefault`) | **überlebt** (rc=0, 0 FAILED) |
| M23 | `RpcTypeInfoReader.collation`: Kollations-Info auf 16 Bit maskiert | rot (`sp_execute…`, `Zeichen- und Binaertypen…`) |
| M24 | `RpcValue.IntegerValue.toString` nennt den Wert | rot (`Textdarstellung…`) |

Die Dateinamen der Läufe liegen im Scratchpad (`mut/m1a…m19`, `batch2.out`); die ID-Zählung oben ist die des Berichts. Der Repo-Working-Tree wurde nicht angefasst.

## 4. Plan-vs-Code-Diff

| Plan (§3, „Angepasster Plan“) | Code | Abweichung |
|---|---|---|
| `protocol/wire/AllHeaders.kt` neu, aus `SqlBatchParser` herausgelöst | vorhanden, `internal object`, `SqlBatchParser` ruft es | keine; Verhalten gleich, M08 trifft beide Tests |
| `ProtocolError.kt`: `INVALID_TYPE_INFO`, `INVALID_RPC_FORM` | vorhanden, Kategorie `TDS_PROTOCOL_ERROR` | keine |
| `protocol/rpc/`: `RpcParser`, `RpcCatalog`, `RpcParameterReader`, `RpcTypeInfoReader`, `RpcRequest.kt`; 18 Typen; PLP und `XML` als `INVALID_TYPE` | vorhanden, zusätzlich `RpcTypeCode.kt` (Typkennungen); Typenliste stimmt | keine inhaltliche; eine Datei mehr als genannt |
| Test `RpcParserTest.kt` (eine Datei) | vier Dateien (`RpcCatalogFormsTest`, `RpcTypesTest`, `RpcFailureTest`, `RpcFuzzTest`) plus `testsupport/RpcVectors.kt` | Namens- und Schnitt-Abweichung, im Plan nicht nachgezogen (LOW, F-7) |
| `spec/spezifikation.md`: unverändert | unverändert | in Ordnung; siehe F-1 |
| Deutung des Werts nur für `INTN`, `BITN`, Unicode-Text | so umgesetzt; übrige Typen als `RawValue` | keine |
| Layering: keine Imports aus `transport`, `port`, `simulation`, `runtime` | Imports nur aus `protocol.token` (`Collation`, `TypeInfoWriter`) und `protocol.wire`; `detekt` grün | keine; Hinweis: der Parser verwendet `TypeInfoWriter.timeBytes` des Encoders (Kopplung innerhalb `protocol`, F-7) |

## 5. Prüfpunkte der Anfrage

**(1) Kopf, Katalog, Leser, Fehlerverhalten:** bestätigt, siehe DoD 1–3. Kopf, ProcID-Marker, Name in Zeichen, Optionsflags, `UnknownProcedure`; OUTPUT, NULL-Sentinels beider Arten (`0xFFFF` gezählt, `0` mit Längenbyte), Kollation (5 Byte), Precision/Scale (Dezimal mit Größe 5/9/13/17, Zeittypen Scale ≤ 7); die Fehler sind Ergebniswerte.

**(2) Mutationen:** 24 Läufe, 22 rot, 2 Überlebende (M21, M22). Alle geforderten Klassen sind abgedeckt: Namenslänge in Zeichen statt Bytes (M01, M02), OUTPUT-Bit (M03), NULL-Sentinel (M04, M05), Text/Deklaration-Vertauschung (M10, M11), PLP-Ablehnung (M17), `toString`-Leck (M06, M12, M24 rot; M21 überlebt), `PREPEXEC_FIXED` (M07), Längenprüfungen (M13, M14, M15, M08).

**(3) Roter Gate-Lauf:** wirkt weiterhin (§1): Exit 2 und kein Stempel nach einem roten Test.

**(4) `@spec`-Tags:** 22 von 22, IDs vorhanden und passend (DoD 4); Lücke SPEC-028 (F-6).

**(5) SQL-Text/Werte nie in `toString`/Fehlerwerten:** Quelltext durchgesehen. `TextValue` nennt nur `chars=<n>`, `RawValue` nur `bytes=<n>`, `IntegerValue` und `BitValue` „verborgen“, `ExecuteSql`/`PrepExec` nur Zeichenzahlen von Text und Deklaration. Alle `violation(…)`-Texte nennen Typ, Größe, Position, nie Inhalt (kein Wert, kein SQL-Text). Es erscheinen Prozedurname (`ByName`) und Parameternamen (`RpcParameter.toString`); das sind Bezeichner, keine Nutzerwerte (F-4). Die Tests sichern das für `TextValue`, `RawValue`, `IntegerValue`, SQL-Text und Deklaration (M06, M12, M24 rot); für `BitValue` nicht (M21).

**(6) Out-of-Scope:** eingehalten. Keine Imports aus `java.net`, `java.nio.channels`, `javax.net`, `kotlinx` im Paket `protocol/rpc` (`grep`); kein Handshake, kein Zustand, kein Backend, keine Handle-Verwaltung (der Parser liefert die Handle-Zahl). Abhängigkeiten: `gradle.lockfile`, `settings-gradle.lockfile`, `gradle/verification-metadata.xml`, `gradle/libs.versions.toml`, `build.gradle.kts` sind zwischen `4cddbc3` und `7c2e7e4` unverändert (`git diff --stat` leer). Hinweis: gegen den späteren `HEAD` ist ein Unterschied zu sehen (Pin der Coroutines-Bibliothek, Commit `40f8eb1`), der aus dem Transport-Slice stammt und nicht aus diesem Slice; gegen `4cddbc3` allein zu vergleichen, würde dem Slice das fälschlich zurechnen.

## 6. Aussagen über Messung hinaus (AGENTS.md 3.8)

Maßstab: *gemessen* ([ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md) am Spike-Testserver, TDS 7.4 unverschlüsselt, `Pooling=false`), *Handvektor* (von Hand nach MS-TDS), *Selbstkonsistenz* (Code gegen Code), *Festlegung* (Setzung ohne Orakel).

| Aussage | Einstufung im Code | Bewertung |
|---|---|---|
| Katalog „nur am Testserver gemessen, kein Microsoft-SQL-Server; Tests belegen Handvektoren, nicht die Annahme durch einen Client“ (`RpcParser`, `RpcCatalogFormsTest`) | gemessen/Handvektor sauber getrennt | **trägt**. |
| `OptionFlags=0x0002` für `sp_executesql`, Text/Deklaration ohne Namen, `OptionFlags=0x0000` bei Prozedurname | verweist auf die Messmatrix in [ADR-0005](../plan/adr/README.md) | **trägt**, die ADR führt beide Werte. |
| `NameLenProcID` = Zeichenzahl | Festlegung nach [SPEC-030](../../spec/spezifikation.md) (Tabellenzeile steht dort) | **trägt**; M01 rot. |
| Parameter von `sp_prepexec`/`sp_execute` „nach Position gedeutet (Namen ignoriert), wie sie der Spike beobachtet hat“ (`RpcParser`) | als Festlegung geführt, mit Spike-Verweis | **teilweise**: [ADR-0005](../plan/adr/README.md) belegt „ohne Namen“ für `sp_executesql`; für `sp_prepexec`/`sp_execute` führt nur SPEC-030 die Positionen, die Namen sind nicht als gemessen ausgewiesen. Der Halbsatz „wie sie der Spike beobachtet hat“ trägt für die beiden weiter, als die ADR-Zeile reicht (F-4). |
| Statusbit-Namen `fByRefValue`, `fDefaultValue`; Optionsflag-Namen `fWithRecomp`, `fNoMetaData`, `fReuseMetaData` (`RpcRequest.kt`) | ohne Kennzeichnung, ohne Quelle | Nur `fNoMetaData` (`0x0002`) steht in der ADR. Die übrigen Zuordnungen sind unmarkierte Aussagen über MS-TDS; `useDefault` hat keinen Test (M22). **Trägt nicht** im Sinne der Regel (F-4). |
| TYPE_INFO-Grenzen (Größen, Precision 1–38, Scale ≤ 7, Dezimalgrößen 5/9/13/17) | „Festlegungen (ohne Orakel)“ im KDoc; Tests prüfen die Grenzen | **trägt**. |
| `INTN` 1 Byte vorzeichenlos, 2/4/8 vorzeichenbehaftet | Handvektor | **trägt**; M19 rot. |
| Hex-Vektor gegen im Code gebauten Vektor | ausdrücklich „Selbstkonsistenz mit `TypeInfoWriter`, nicht unabhängig“ | **trägt**; sauber als Selbstkonsistenz gekennzeichnet. |
| Fehlergründe/Kategorien der Negativfälle | „Festlegung dieses Parsers; gemessen ist keine Fehlerantwort“ (`RpcFailureTest`) | **trägt**. |
| `TDS 7.2 und höher`, ALL_HEADERS Pflicht (`RpcParser`) | Festlegung | **trägt**; kein Test für Version (der Parser kennt keine). |

**PLP-Ablehnung gegenüber SPEC-003/[ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md).** Der Text von [ADR-0005](../plan/adr/README.md) („Der RPC-Parser muss … PLP (MAX-Typen) … dekodieren“) und SPEC-003 („MAX-Typen … tragen Länge `0xFFFF` und PLP“) ist breiter als die Umsetzung; zudem hat der Spike MAX-Parameter an `sp_executesql` gemessen (Messmatrix: „MAX mit Länge `0xFFFF` und PLP“). Der Parser lehnt jeden dieser Parameter als `INVALID_TYPE` ab, und zwar die ganze Nachricht, auch wenn nur ein späterer Parameter MAX ist. Ist die Lücke im Slice dokumentiert? **Als Scope-Ausschluss ja, als Abweichung von ADR/SPEC nur halb.** Dokumentiert: Slice §1 („NBCROW, PLP und Typen außerhalb des Mindestkatalogs … dafür steht `slice-codec-datentypen-erweiterung`; dieser Slice lehnt solche Parameter als Ergebniswert ab“), §3 (PLP und XML als `INVALID_TYPE`), das KDoc von `RpcTypeInfoReader`, der Test `PLP, XML und unbekannte Typen…` und der Folge-Slice (Zeile `protocol/rpc` „Parameter-Leser für PLP“). Nicht dokumentiert: (a) dass damit die in der ADR geforderte und im Spike gemessene Form nicht gelesen wird, also ein Client mit langem Text oder `nvarchar(max)`-Parameter heute ein Ergebnis `TDS_UNSUPPORTED_TYPE` bekäme; (b) dass dies auch den SQL-Text und die Deklarationsliste von `sp_executesql` trifft, wenn der Client sie als MAX sendet (nicht gemessen, in der Messmatrix nur für Parameter genannt); (c) dass SPEC-030/SPEC-003 ohne Hinweis stehen, der Parser also hinter dem Wortlaut der Spec bleibt. Das ist F-1.

## 7. Findings

| # | Schwere | Befund |
|---|---|---|
| F-1 | **MEDIUM** | **PLP/MAX-Ablehnung ist enger als [ADR-0005](../plan/adr/README.md) und SPEC-003, und die Abweichung ist nicht als solche vermerkt.** Siehe §6. Der Scope-Ausschluss in Slice §1 und der Folge-Slice tragen die Entscheidung, aber die Closure-Notiz muss sagen: (a) die ADR fordert MAX im RPC-Parser, der Slice liefert es nicht, Folge-Slice `slice-codec-datentypen-erweiterung`; (b) das Verhalten ist „ganze Nachricht abgelehnt“ (nicht „Parameter übersprungen“); (c) SQL-Text und Deklaration als MAX sind ungeprüft. Bis dahin liest ein Mitleser in SPEC-003/ADR „muss PLP dekodieren“ und in `RpcTypeInfoReader` das Gegenteil. Kein Fehler im Code, ein Dokumentationsbefund; Handlung: Eintrag in §7 des Slice und ein Hinweis in SPEC-030 oder dem Folge-Slice-Risiko. |
| F-2 | **MEDIUM** | **Closure-Pflichten sind offen (nicht verletzt, aber Voraussetzung für `done/`).** §7 leer; Haken aller DoD-Punkte leer; Register nicht fortgeschrieben; Risiken ohne Ausgang; Review-Report nicht committed. Die Zuordnung *gemessen · Handvektor · Selbstkonsistenz · Festlegung* je Aussage ist in den KDoc-Köpfen weitgehend vorhanden (§6) und kann daraus in die Closure-Notiz übernommen werden. |
| F-3 | **MEDIUM** | **Textdarstellung ist nur teilweise gesichert; `useDefault` hat keinen Test.** Überlebende M21: `BitValue.toString` darf den Wert nennen, ohne dass ein Test rot wird; die Textdarstellungs-Vektoren enthalten keinen `BITN`-Wert, und die verbotenen Zeichenfolgen (`"123456789"`, `"DE AD"`, `"geheim"`, `"select"`, `"int"`) würden `true`/`false` ohnehin nicht fangen. Die DoD-Zusage „Textdarstellung enthält keine Parameterwerte“ trägt für `BITN` damit nur durch den Quelltext, nicht durch den Test. Überlebende M22: `STATUS_DEFAULT`/`useDefault` ist öffentliche API ohne Test. Handlung: `EXECUTE` (mit `BITN` wahr) in die Vektorenliste von `Textdarstellung enthaelt weder Werte noch SQL-Text` aufnehmen und `"true"` verbieten; `useDefault` testen oder streichen. |
| F-4 | LOW | **Unmarkierte Aussagen über MS-TDS (AGENTS.md 3.8).** Bitnamen `fByRefValue`/`fDefaultValue`/`fWithRecomp`/`fReuseMetaData` in `RpcRequest.kt` ohne Kennzeichnung als Lesart; „wie sie der Spike beobachtet hat“ trägt für `sp_prepexec` und `sp_execute` nur über SPEC-030, nicht über eine Messzeile der ADR. Dazu `RpcParameter.toString`/`ByName.toString` mit Parameter- und Prozedurnamen: Bezeichner des Clients, keine Werte, aber im Slice nicht als zulässig begründet. Das Muster ist `aussagen-ueber-messung-hinaus` in kleiner Form; Handlung: je ein Halbsatz „Lesart von MS-TDS, nicht gemessen“, Name in der Closure-Notiz als zulässig begründen. |
| F-5 | LOW | **„Zerstückelt“ ist als Verkürzung belegt, nicht als Zerlegung in Pakete.** Der RPC-Parser sieht die zusammengesetzte Nachricht; die Fragmentierung trägt der `MessageAssembler` aus `slice-codec-tds-paket`. Ein Test, der eine RPC-Nachricht byteweise durch den Assembler in den Parser schickt, fehlt; er ist nicht verlangt, würde aber die DoD-Formulierung wörtlich belegen. |
| F-6 | LOW | **SPEC-028 ohne Test-Tag im RPC-Paket.** Die Kopf-Tests (ALL_HEADERS) tragen SPEC-030; der Slice nennt SPEC-028 als berührte Spec-Stelle für den Kopf. Die Tests sind tragfähig (M08 rot), das Tag zeigt aber nur auf eine der beiden Stellen. |
| F-7 | LOW | **Plan-Abweichungen ohne Nachzug.** Der angepasste Plan nennt eine Testdatei `RpcParserTest.kt`, geliefert sind vier; `RpcTypeCode.kt` fehlt in der Liste. Der Slice-Kopf nennt [TDS-FA-04](../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc) und [TDS-FA-10](../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), die Commits [TDS-FA-05](../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen); die beiden Mengen sind nicht aufeinander abgebildet (die Commit-Nachricht-Prüfung verlangt nur eine ID). Der Parser hängt für die Zeitlänge an `TypeInfoWriter.timeBytes` des Encoders: eine Kopplung zweier Richtungen innerhalb `protocol`, vom Gate erlaubt. |

**HIGH: keine.** Kein DoD-Punkt der Liefer-Gruppe ist verletzt; die offenen Punkte betreffen die Closure und die Dokumentation der PLP-Lücke.

## 8. Verbleibende Risiken

- Der Katalog ist nur am Spike-Testserver gemessen; die Tests belegen Handvektoren nach MS-TDS, nicht die Annahme durch einen Client (Träger `slice-qa-kompatibilitaet`, `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`).
- Die Vektoren sind von Hand gerechnet und teilen sich mit dem Parser dieselbe Lesart von MS-TDS; unabhängig davon prüft sie erst der Referenzabgleich (`BEO-TDS/messung-am-echten-sql-server-fehlt`).
- Bis `slice-codec-datentypen-erweiterung` liegt, kann ein realer Client mit MAX-Parameter (F-1) keinen RPC absetzen.
