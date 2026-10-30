# Verifikationsbericht: bba672b (slice-codec-tds-paket) — 2026-10-30

**Review-Art:** Verifikation (DoD- und Entscheidungs-Konformität, Plan-vs-Code), Modul 11

**Gegenstand:** Merge `bba672b` auf `main`, Slice `docs/plan/planning/in-progress/slice-codec-tds-paket.md` (Welle `welle-protokollcodec`). `src/`, `Dockerfile`, `build.gradle.kts`, `gradle/`, `tools/mk/` und `harness/README.md` sind zwischen `bba672b` und dem geprüften `HEAD` unverändert (`git diff --stat bba672b HEAD` über diese Pfade ist leer); spätere Commits betreffen nur [ADR-0010](../plan/adr/README.md), Pläne und den Review-Report.

**Bezug:** [TDS-FA-01](../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-FA-02](../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [TDS-FA-05](../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-03](../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit); [ADR-0003](../plan/adr/README.md), [ADR-0005](../plan/adr/README.md), [ADR-0009](../plan/adr/README.md).

**Modell:** claude-sonnet-5-5 · **Datum:** 2026-10-30

**Methode:** Behauptung des Implementers ≠ Bestätigung. Alle Läufe in Docker über `make` bzw. `docker build` auf einer Kopie außerhalb des Repos (`git archive HEAD` in das Scratchpad); der Working Tree des Repos wurde nicht verändert. Am Ende zeigt `git status --short` nur `?? .claude/worktrees/` (Zustand vor Beginn).

---

## 1. Selbst gefahrene Sensoren

| Sensor | Ergebnis |
|---|---|
| `make gates` im Repo (zweimal, vor und nach den Mutationsläufen) | Exit 0 |
| `docker build --target test --no-cache-filter test` (Kopie, Stage ohne Cache, damit das Protokoll sichtbar ist) | `BUILD SUCCESSFUL`, 96 Tests PASSED, 0 FAILED, 0 SKIPPED |
| `make docs-check` | 113 Dateien, 0 Befunde |
| Roter Test (Kopie mit eigenem `git init`): Erwartungswert in `TokenEncoderTest` `RETURNSTATUS` auf `79 00 00 00 01` gekippt, dann `make gates` | Exit 2; `Task :test FAILED`, `TokenEncoderTest > RETURNSTATUS() FAILED expected: <79 00 00 00 01> but was: <79 00 00 00 00>`, `BUILD FAILED` |
| Mutationsstichprobe | 38 Mutationen, siehe §3 |

## 2. DoD-Punkte einzeln

| # | DoD-Punkt | Urteil | Beleg der Verifikation |
|---|---|---|---|
| 1 | **Test-Gate** `make test`: Stage `test`, `tools/mk/test.mk`, nur `kotlin.test`, Teil von `make gates`, Zeile in `harness/README.md` §Sensors, Katalog/Lock/Prüfsummen, roter Test macht `make gates` rot | **bestätigt** | `Dockerfile`: Stage `test` auf `deps`, `COPY src`, `RUN gradle --no-daemon test`. `tools/mk/test.mk`: Target `test`, `GATE_CHECKS += test`. `tools/mk/build.mk`: `DEPS_TASKS` enthält `test`. `harness/README.md` Zeile 66 trägt `make test`; das Doku-Gate ist grün. `gradle/libs.versions.toml` führt `junit` 5.13.4, `kotlin-test`, `junit-bom`, `junit-jupiter-engine`, `junit-platform-launcher`; `gradle.lockfile` (+13 Zeilen) und `gradle/verification-metadata.xml` (+13 Komponenten) führen sie; alle `import`s in `src/test` stammen aus `kotlin.test` und dem JDK (`java.io`, `java.math`, `java.time`, `java.util.Locale`, `kotlin.random`), keines aus JUnit. Roter Lauf: selbst nachgestellt, Exit 2 (§1). Hinweis: der Plan verlangt den Beleg „im Closure-Bericht“; §7 des Slice ist noch leer. Der Beleg liegt hiermit vor, steht aber nicht in der Closure-Notiz. |
| 2 | **Paket-Codec** (`transport`): 8-Byte-Header, Länge Big-Endian, Packet-ID mit Überlauf, Längenprüfung unten/oben, Zusammensetzen zerstückelter Eingabe, Abbruch vor dem Puffern, Zerlegen bis Packet-Size, Fehler als Ergebniswerte mit Kategorie | **bestätigt** | Mutationen M01 bis M11 (alle rot, §3). `FramingError.category` liefert `TDS_PROTOCOL_ERROR` (`SPEC-011`). Der Test „Abbruch bevor die Nachricht gepuffert ist“ fiel unter M03 und M05 rot. |
| 3 | **Nachrichten-Codec** (`protocol`): Encoder der Antwort-Tokens, PRELOGIN, LOGIN7-Parser, SQL_BATCH-Text, Passwort in keiner Textdarstellung | **bestätigt, mit Befund F-3 und F-4** | Mutationen M12 bis M38 (35 von 36 anwendbaren rot, 1 Überlebender, §3). Passwort: §5. Befunde zur Kennzeichnung als Festlegung: F-1, F-4. |
| 4 | Jeder Test trägt `@spec SPEC-<NNN>` im KDoc; Tests laufen in `make test` und sind grün | **bestätigt** | 96 `@Test`, 96 `@spec`-Blöcke, je Datei gleich viele (`grep -c`). Verwendete IDs: SPEC-003 (16×), -006 (10×), -007 (2×), -011 (4×), -012 (1×), -014 (2×), -027 (9×), -028 (46×), -029 (1×), -030 (3×), -046 (2×); alle existieren in `spec/spezifikation.md`. Inhaltlich passend bis auf F-5. |
| 5 | Jede Aussage an einem Test oder als Festlegung gekennzeichnet; Zuordnung *Test \| Festlegung* in der Closure-Notiz | **nicht bestätigt** | Haken steht zu Recht leer; §7 des Slice ist „Wird bei der Closure gefüllt“. Stichprobe der Kennzeichnung: F-1 (LOGINACK), F-4 (PRELOGIN-VERSION, Aussage über MS-TDS ohne Beleg). |
| 6 | Gemeinsamer Smoke (PRELOGIN, LOGIN7 mit Login-Antwort, Resultset mit RETURNSTATUS) durch beide Codecs, byteweise zerstückelt | **bestätigt** | `CodecSmokeTest` (3 Tests). Fielen rot unter M01, M07, M12, M13, M21, M22 bis M28. |
| 7 | Zerstückelte, überlange, abgeschnittene, zufällige Eingaben enden in einem Ergebniswert (`SPEC-027`, [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität)) | **bestätigt** | Fuzz mit festem Startwert in `MessageAssemblerTest`, `PreloginCodecTest`, `Login7ParserTest`, `SqlBatchParserTest`, `TokenRoundtripTest`. M36 (Leser prüft eine Stelle zu großzügig, es entstünde `IndexOutOfBounds`) machte sechs Tests rot, darunter die beiden Fuzz-Tests und „Tabelle ohne Terminator ist ein Fehler“. `guarded` fängt nur `ProtocolViolation`; die Fuzz-Läufe belegen, dass keine andere Ausnahme entweicht. |
| 8 | `make gates` grün | **bestätigt** | Exit 0, selbst gefahren (§1). |
| 9 | Review durchgeführt, Report unter `docs/reviews/` | **Report liegt vor, Haken offen** | `docs/reviews/2026-10-30-bba672b-codec-tds-paket.md` (Commit 808ac2b) existiert. Der Haken im Slice steht auf `[ ]`; das Setzen ist Sache des Planners. Der Report vermerkt selbst, dass der rote Lauf nicht ausgeführt wurde (F-5 dort); das ist hiermit nachgeholt. |
| 10 | Doku-Update bei berührtem öffentlichem Vertrag | **bestätigt, mit Befund F-2** | `harness/README.md`-Zeile vorhanden; `contracts/` im Merge unverändert. `SPEC-024` ist im Slice als berührte Spec-Stelle genannt und nicht angepasst (F-2). |
| 11 | Closure-Notiz mit Steering-Loop-Lerneintrag | **offen, nicht fällig** | §7 leer. |
| 12 | Beobachtungs-Register fortgeschrieben | **offen, nicht fällig** | Kein Eintrag unter `docs/plan/planning/observations/` durch diesen Slice; `aussagen-ueber-messung-hinaus` steht auf 6 Dateien. Das Muster ist in kleiner Form wieder aufgetreten (F-1, F-4). |
| 13 | Risiken aus §6 tragen einen Ausgang | **offen, nicht fällig** | Alle neun Risiken stehen auf „offen bis zur Closure“. |
| 14 | Drei Paarungen (Anker · Folge-Slice · Register) | **offen, nicht fällig** | Folge-Slices für RPC-Parser, NBCROW/PLP, Backend-Abbildung sind laut §1 noch nicht angelegt. |

## 3. Mutationsstichprobe (Paket-Codec, LOGIN7, Token-Encoder, PRELOGIN, SQL_BATCH)

Jede Mutation lief in einer frischen Kopie (`docker build --target test`), das Repo blieb unberührt. „rot“ heißt: Exit ≠ 0, mindestens ein Test `FAILED`, kein Kompilierfehler.

| ID | Datei · Änderung | Ergebnis (Auswahl der roten Tests) |
|---|---|---|
| M01 | `PacketHeader.toBytes`: Länge Little-Endian | rot (9; Smoke, Header-Tests) |
| M02 | `parse`: Untergrenze 8 auf 7 | rot (`Laenge 8 ist zulaessig und 7 nicht`, Fuzz) |
| M03 | `parse`: Obergrenze `>` auf `>=` | rot (4; „Nachrichtengrenze gilt einschliesslich“, „Abbruch bevor …“) |
| M04 | `MessageAssembler`: Nachrichtengrenze `>` auf `>=` | rot (3) |
| M05 | Summe zählt nur Nutzdaten statt Paketlänge | rot (3; `SPEC-007`) |
| M06 | `PacketWriter`: Packet-ID-Überlauf bei 255 statt 256 | rot (`Packet-ID laeuft nach 255 auf 0 ueber`) |
| M07 | `PacketWriter`: EOM am ersten statt letzten Paket | rot (6; Smoke, Writer) |
| M08 | `PacketWriter`: Packet Size 8 zulässig | rot |
| M09 | Typwechsel in der Nachricht nicht mehr geprüft | rot (`Typwechsel innerhalb einer Nachricht ist ein Fehler`) |
| M10 | `endOfInput` meldet halbe Nachricht nicht | rot (`Abgeschnittene Eingabe wird am Ende gemeldet`) |
| M11 | RESETCONNECTION aus letztem statt erstem Paket | rot (`Reset-Connection und Ignore werden gemeldet`) |
| M12 | LOGIN7-Entschleierung: Maske `0xA4` | rot (8; Smoke, Handvektor) |
| M13 | Entschleierung: Reihenfolge XOR/Nibble-Tausch vertauscht | rot (8) |
| M14 | `fIntSecurity` auf Bit `0x40` | rot (`fIntSecurity und SSPI-Block werden erkannt`) |
| M15 | LOGIN7-Längenfeld nicht mehr gegen Nachrichtenlänge geprüft | rot (`Laengenfeld muss zur Nachricht passen`) |
| M16 | Feld-Offset darf in den festen Kopf zeigen | rot (`Felder liegen hinter dem Kopf …`) |
| M17 | FeatureExt-Option `0x02` auf `0x03` | rot (`FeatureExt-Option 0x02 wird erkannt`) |
| M18 | `SecretChars.toString` gibt den Inhalt aus | rot (`Keine Textdarstellung enthaelt Passwort oder Kennung`, 2) |
| M19 | `Login7Fields.toString` gibt die Kennung aus | rot (derselbe Test) |
| M20 | `fChangePassword` auf Bit `0x02` | rot (`fChangePassword und neues Passwort`) |
| M21 | DONE: Status und CurCmd vertauscht | rot (5) |
| M22 | RETURNSTATUS Big-Endian | rot (`RETURNSTATUS`) |
| M23 | LOGINACK: TDS-Version Little-Endian | rot (4; `LOGINACK`, Smoke) |
| M24 | ENVCHANGE-Typ Packet Size 3 statt 4 | rot (3) |
| M25 | ERROR/INFO: State und Severity vertauscht | rot (4) |
| M26 | RETURNVALUE: Statusbyte entfällt | rot (3) |
| M27 | ENVCHANGE-Typ Collation 8 statt 7 | rot (3) |
| M28 | PRELOGIN-Offset Little-Endian | rot (4) |
| M29 | PRELOGIN-Offset darf in die Tabelle zeigen | rot (`Offsets liegen hinter der Tabelle und in der Nachricht`) |
| M30 | SQL_BATCH: ALL_HEADERS-Länge nicht gegen Nachricht geprüft | rot |
| M31 | Typkennung INT8 `0x7E` statt `0x7F` | rot (3) |
| M32 | PRELOGIN-Tabelle: Eingabeende direkt nach einem Nicht-Terminator-Byte gilt als Tabellenende | **überlebt (grün)** |
| M33 | SQL_BATCH: ungerade Textlänge nicht geprüft | rot |
| M34 | DONE-Statusbereich nicht geprüft (Write-Seite) | rot (`Feldgrenzen der Tokens`) |
| M35 | FeatureExt-Tabellenoffset nur unten geprüft | rot |
| M36 | `ByteReader.need` eine Stelle zu großzügig | rot (6, darunter Fuzz) |
| M37 | FeatureExt-Tabellenoffset `>= total` nicht geprüft | überlebt (grün); nahezu äquivalent: der Folgezugriff `u8()` meldet dann `TRUNCATED` statt `INVALID_OFFSET`, beide mit Kategorie `TDS_PROTOCOL_ERROR`. Kein Befund. |
| M38 | SQL_BATCH: Untergrenze der ALL_HEADERS-Länge entfällt | rot |

Ergebnis: 38 Mutationen, 36 rot, 2 überlebt (M32 echte Lücke, M37 ohne Folge). Keine Mutation scheiterte am Kompilieren, keine blieb unangewandt.

## 4. Prüfpunkte des Auftrags

**(3) `@spec`-Tags.** Siehe DoD 4. Die Zuordnung ist stimmig, mit einer Ausnahme (F-5).

**(4) Passwort nie in `toString`/Log.** Bestätigt. `SecretChars.toString()` ist fest `"SecretChars(verborgen)"` (weder Inhalt noch Länge), `Login7Fields.toString()` ist fest, `Login7Request.toString()` nennt nur Version, Packet Size und drei Merkmale; `Login7Header` trägt kein Geheimnis. `ProtocolError.detail` nennt Feldnamen, Offsets und Längen, nie Inhalte. M18 und M19 machten den Test „Keine Textdarstellung enthaelt Passwort oder Kennung“ rot. `reveal()` gibt eine Kopie, `wipe()` überschreibt. Es gibt in `src/main` weder Logging noch Ausgabe.

**(5) Schichtgrenzen.** Bestätigt. In `src/main` importiert weder `protocol` aus `transport` noch umgekehrt; `transport` importiert nur `java.io.ByteArrayOutputStream`, `protocol` nur `java.io`, `java.math`, `java.time`. Kein Import aus `domain`, `port`, `simulation`, `runtime`. Die Importregeln in `config/detekt/detekt.yml` (Zeilen 266 bis 280) und `make detekt` im `make gates` sind grün. Die `protocol`-Tests importieren `transport` nicht; nur `smoke` bringt beide zusammen. Grenze des Sensors (laut `SPEC-041`): er prüft nur `import`-Zeilen; ein voll qualifizierter Name würde ihn umgehen. Dafür gibt es in `src/main` keinen Treffer.

**(6) Out-of-Scope.** Eingehalten. Kein `java.net`, `java.nio.channels`, `javax.net`, `Socket` (Suche in `src`, Build-Dateien und Katalog ohne Treffer). `kotlinx-coroutines-core` steht nicht im Katalog; die Coroutines-Einträge im Lock gehören der Konfiguration `detekt` und der Kotlin-Toolchain und sind älter als dieser Slice. Kein Handshake, kein Zustandsautomat, kein RPC-Parser (nur RETURNSTATUS/RETURNVALUE als Encoder), keine Abbildung von `QueryEvent`/`SqlType`, kein NBCROW/PLP, `domain` enthält nur das bereits vorhandene `Placeholder.kt`, `port` bleibt leer. ATTN-Behandlung: nur der Pakettyp. Hinweis aus dem Review (dort F-1), von mir bestätigt als offen: [ADR-0003](../plan/adr/README.md) sagt „sobald der erste Transport-Code entsteht“; die Auslegung „sobald Code mit I/O entsteht“ steht nur im Plan §6, das Risiko ist ohne Ausgang.

## 5. Findings

| ID | Schwere | Befund | Fundort |
|---|---|---|---|
| F-1 | MEDIUM | **LOGINACK-Byteordnung: das Gate trägt sie nur halb als Festlegung.** Das Gate pinnt `AD 18 00 01 74 00 00 04 …` als Handvektor (`TokenEncoderTest.LOGINACK`); M23 färbt ihn rot. Als Festlegung gekennzeichnet ist die Ordnung in `Token.kt` („Festlegung nach MS-TDS 2.2.7.13“) und im Test („Lesart von MS-TDS, nicht am Referenzclient gemessen“). Das trägt drei Schwächen. (a) Zwei Aussagen mit verschiedenem Status stehen unter einem Wort: die Bytefolge `74 00 00 04` der TDS-Version ist in [ADR-0005](../plan/adr/README.md) (`Accepted`, Abschnitt „Weitere Befunde der Messung“) als gemessen belegt, der Test sagt aber „nicht am Referenzclient gemessen“ und zitiert die ADR nicht; für die Programmversion (`progVersion`, hier `01 00 00 00` Big-Endian) liegt weder Messung noch unabhängiges Orakel vor, Vektor und Code stammen aus derselben Quelle. (b) Die Quellenangabe „MS-TDS 2.2.7.13“ ist im Repo nicht auflösbar (SPEC-020 führt MS-TDS als extern, der Text liegt nicht bei); die Abschnittsnummer ist offline nicht prüfbar, meine Erinnerung nennt für LOGINACK 2.2.7.14. Eine konkrete Nummer, die nicht geprüft werden kann, behauptet mehr, als der Implementer geprüft hat — dies ist die Form der Beobachtung `aussagen-ueber-messung-hinaus`. (c) Das Gate ist ein Regressionsanker für den gewählten Wert, kein Beleg der Protokolltreue; das sagt der Test-KDoc zutreffend, der Produktions-KDoc nicht. Einordnung: die Aussage ist als Festlegung erkennbar und nicht als Messung ausgegeben (kein „Client akzeptiert“), also kein Verstoß gegen §6 des Plans, aber die Zuordnung *Test \| Festlegung* in der Closure-Notiz muss die beiden Bestandteile trennen und die Abschnittsnummer streichen oder gegen den Text belegen. | `src/main/.../protocol/token/Token.kt` Z. 7–9; `src/test/.../protocol/token/TokenEncoderTest.kt` Z. 172–185; [ADR-0005](../plan/adr/README.md) |
| F-2 | MEDIUM | **`SPEC-024` sagt „Gate noch einzurichten“, obwohl `make test` besteht.** Die Zeile in `spec/spezifikation.md` (Rang 2) ist durch den Merge falsch; der Slice nennt `SPEC-024` unter „Berührte Spec-Stellen“ und der Plan-Tabelle ist keine Änderung an der Spezifikation zu entnehmen. Nicht geändert (Auftrag), nur gemeldet. Beim Nachziehen die Beschreibung zugleich an den Ist-Stand binden (`kotlin.test`, Stage `test`, Bestandteil von `make gates`), und `slice-qa-wartbarkeit` Z. 46 (erster Liefer-Punkt „Unit-Test-Gate (`SPEC-024`) läuft in Docker …“) auf den Rest kürzen, wie §6 des Plans vorsieht. | `spec/spezifikation.md` Z. 578; `docs/plan/planning/*/slice-qa-wartbarkeit.md` Z. 46 |
| F-3 | LOW | **Testlücke M32.** Endet eine PRELOGIN-Nachricht unmittelbar nach einem Nicht-Terminator-Byte der Tabelle (z. B. Nutzdaten `00`), meldet der Parser heute `TRUNCATED`; die Mutation, die dort „Tabellenende“ annimmt und `Ok` liefert, bleibt grün. Der DoD-Satz „Offset-/Längen-Tabelle samt Terminator geprüft“ trägt diese Grenze nicht; das Fuzz-Verhalten schützt sie nicht, weil es nur „kein Absturz“ verlangt. Ein Test mit Eingabe `00` und eins mit `00 00` genügt. | `src/test/.../protocol/prelogin/PreloginCodecTest.kt` |
| F-4 | LOW | **Aussagen über MS-TDS ohne Beleg im Repo** (Review F-3, F-4 dort, von mir ohne MS-TDS-Text nicht entscheidbar): Byteordnung des Unter-Builds der PRELOGIN-Option VERSION (`PreloginMessage.version`, „zwei Bytes Big-Endian“, Vektor selbst gewählt) und die Begründung, MS-TDS beschreibe die Packet-ID als vom Empfänger ignoriert (`PacketHeader`, KDoc; es gibt keinen Test, der nicht fortlaufende Packet-IDs annimmt). Beide gehören in die Zuordnung der Closure-Notiz als *Festlegung ohne Messung*, nicht als Zitat. | `PreloginMessage.kt` Z. 38; `PacketHeader.kt` Z. 5–8 |
| F-5 | LOW | **`@spec SPEC-012` am Test `ERROR`.** `SPEC-012` ist die feste Ablehnungsantwort (Nummer 18456, einheitlicher State); die feste Ablehnung ist laut Plan §1 ausdrücklich nicht Teil des Slice. Der Test prüft die Byte-Form eines ERROR-Tokens mit diesen Beispielwerten; passender wäre `SPEC-028` oder `SPEC-011`. Wer später aus den Tags ein Abdeckungs-Dokument erzeugt (`slice-qa-spec-abdeckung`), liest sonst `SPEC-012` als belegt. | `TokenEncoderTest.kt` Z. 103 |
| F-6 | LOW | **Der Haken „Test-Gate“ stand vor dem Beleg.** Der Plan verlangt den absichtlich roten Lauf „im Closure-Bericht“; bei Merge war nichts belegt (Review F-5 dort). Die Aussage trifft zu (§1), der Beleg steht nur in diesem Bericht. Die Closure-Notiz muss ihn tragen oder auf diesen Bericht zeigen. | `slice-codec-tds-paket.md` §2, §7 |
| F-7 | LOW | **Fuzz belegt nur „kein Absturz“.** Die Fuzz-Tests prüfen keine Fehlerkategorie; ein Parser, der nach Rauschen irrtümlich `Ok` liefert, bliebe unentdeckt (Ursache von F-3). Für das Codec-Teil des Fuzz-Gates ausreichend (Plan §2 Zeile 5 verlangt nur Ergebniswert, nicht Ablehnung). | `*Test.kt` mit `Random(SEED)` |
| F-8 | INFO | `junit-jupiter-api` und `junit-platform-commons` stehen für `testCompileClasspath` in 5.10.1 und für `testRuntimeClasspath` in 5.13.4 im Lock (Kotlin-Test-Plugin bringt 5.10.1 mit). Kein Fehler, nur zwei Versionen in der Lock-Datei. | `gradle.lockfile` |

HIGH: keine.

## 6. Plan-vs-Code-Diff

| Plan-Zeile (§3) | Ist |
|---|---|
| `Dockerfile` update | wie geplant; Kopf nennt `make test` |
| `build.gradle.kts`, `libs.versions.toml` | wie geplant; `tasks.test` mit `useJUnitPlatform()` und Logging |
| `gradle.lockfile`, `verification-metadata.xml` | geschrieben (`make deps-lock`), 13 neue Komponenten |
| `tools/mk/test.mk` neu; `DEPS_TASKS` | wie geplant |
| `harness/README.md` Zeile | vorhanden |
| `transport/framing/` neu | alle genannten Typen vorhanden (`PacketHeader`, `PacketType`, `PacketStatus`, `FramingLimits`, `MessageAssembler`, `PacketWriter`, `FramingError` samt `FramingResult`, `TdsMessage`); keine verbotenen Importe |
| `protocol/` (`wire`, `prelogin`, `login7`, `sqlbatch`, `token`) | vorhanden; kein Import aus `transport` |
| Tests `transport/framing`, `protocol`, `smoke`, `testsupport` | vorhanden (9 Testklassen, `TokenDecoder`, `Login7Builder`, `Hex` nur im Test-Quelltext) |
| nicht in der Tabelle | `config/detekt/detekt.yml`: im Merge nur ein Kommentar, keine Lockerung |

Abweichung: keine außerhalb der Plan-Tabelle liegende Datei im Produktionscode. Spec-Änderung zu `SPEC-024`: fehlt (F-2).
