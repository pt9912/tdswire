# Review-Report: slice-qa-stabilitaet (Merge cb4c853) — 2026-10-30

**Review-Art:** Code (Diff gegen Plan, ADR, Spezifikation und Hard Rules aus `AGENTS.md`; nicht gegen die DoD)

**Gegenstand:** Merge `cb4c853`, `git diff cb4c853^1 cb4c853` (9 Dateien, 1273 Zeilen): acht neue Testdateien unter `src/test/kotlin/io/github/pt9912/tdswire/stability/` und der Slice-Plan `slice-qa-stabilitaet`. Produktionsdateien sind im Diff unverändert.

**Skill:** `.harness/skills/reviewer.md` @ ae624d2
**Modell:** claude-sonnet-5-5 · **Datum:** 2026-10-30

**Eingangs-Kontext:**

- `slice-qa-stabilitaet` (Plan, DoD wurde nicht geprüft)
- [ADR-0003](../plan/adr/0003-transportframework.md)
- [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität)
- [SPEC-005](../../spec/spezifikation.md#3-defaults-und-konstanten), [SPEC-006](../../spec/spezifikation.md#3-defaults-und-konstanten), [SPEC-007](../../spec/spezifikation.md#3-defaults-und-konstanten), [SPEC-008](../../spec/spezifikation.md#3-defaults-und-konstanten), [SPEC-034](../../spec/spezifikation.md#3-defaults-und-konstanten), [SPEC-046](../../spec/spezifikation.md#3-defaults-und-konstanten), [SPEC-027](../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge), [SPEC-028](../../spec/spezifikation.md#spec-028--prelogin-tls-und-login7-zustands--und-antwortvertrag)
- `AGENTS.md` §3.1, §3.2, §3.7, §3.8
- vorherige Reviews am Modul: der Review von `slice-transport-listener-lebenszyklus` (Stand d9bab24, archiviert mit der Welle), `2026-10-30-bba672b-codec-tds-paket.md`
- Beobachtungen `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler`, `BEO-TDS/strukturtest-ohne-variation-der-eingabe`, `BEO-TDS/flake-fix-ohne-reproduzierten-fehler`

**Ausgeführt (alles in Docker, Kopie des Stands im Scratchpad, das Repo blieb unverändert):** Grundlinie der neun Stabilitätstests (grün, 2 m 6 s unter einer Last von über 100); 23 Produktions- und Testmutationen einzeln (Tabelle unten); Instrumentierungsläufe für Deskriptorzahl, gleichzeitige Handler und Zähler; Flakiness: 12 Wiederholungen des Verbindungssturms bei 60 Busy-Loops (eigener Container), dazu zwei gleichzeitige Volläufe der Stabilitätstests in weiteren Docker-Builds. `make detekt` (Exit 0) und `make docs-check` (0 Befunde, mit diesem Report) liefen im Repo-Wurzelverzeichnis; `make gates` nicht (es schreibt den Nachweis). Die Mutationsnummern M1 bis M14 des Implementers lagen mir nicht vor; M2, M3 und M6 habe ich nach Beschreibung nachgebaut, für M9, M12 und M13 habe ich stattdessen die Stellen Treiber, Register und Deskriptoren mutiert (Tabelle). Meine Kürzel sind eigene.

---

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | HIGH | Der Kommentar beschreibt eine verworfene Alternative im Konjunktiv: „Eine Referenz, die alles annähme, würde den Fuzz-Test wertlos machen.“ Er trägt keine der fünf Klassen (Zusage, Kopplung, Abgrenzung, Rang-Zeiger, Grenze). Die Einordnung HIGH folgt der Liste des Skills; die Korrektur ist ein Satz, die geltende Zusage der Handvektoren steht im Satz davor. | `AGENTS.md` §3.7, Skill-Liste „Kommentar trägt keine der Kommentar-Klassen“ | `FramingFuzzTest.kt` · `würde den Fuzz-Test wertlos machen` | nein (kein Gate fängt Kommentarklassen) | Kommentar beschreibt verworfene Alternative |
| F-2 | MEDIUM | „105 gleichzeitige Verbindungen“ steht im KDoc des Sturmtests und im Slice (§2, §6), gemessen ist es nicht. Mit einem Zähler im Handler (Wegwerfcode) lag die Spitze gleichzeitig lebender Handler in drei Läufen bei 48, 48 und 49; die 105 sind die Zahl der Fälle insgesamt, die meisten Clients schließen früh (12 Client-Threads, 10 der 105 bleiben bis zur Frist offen). Der Test belegt die Last nicht, die der Text nennt, und misst die Spitze selbst nicht. | `AGENTS.md` §3.8, [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität) | `ConnectionStormTest.kt` · `105 gleichzeitige Verbindungen aus 21 Fällen` | ja (Zähler der gleichzeitigen Handler im Test) | Gleichzeitigkeit behauptet, Spitze nicht gemessen |
| F-3 | MEDIUM | Die Typmenge der Referenz (`KNOWN_TYPES`: 1, 2, 3, 4, 6, 7, 8, 14, 16, 17, 18) wird als „Katalog nach MS-TDS 2.2.3.1.1“ und die Referenz als „unabhängig vom Paket-Codec“ ausgewiesen. Die Menge ist elementgleich mit `PacketType.entries` (elf Codes, nachgeglichen); das Repo führt keinen MS-TDS-Text, die Abschnittsnummer hat keine belegbare Quelle, und `PacketType` nennt den Katalog selbst eine Festlegung („Typen, die der Server liest oder schreibt“). Gemessen: Zusätzliche Codes in `PacketType` (0x13, 0x00, 0xFF, 0x09) sind rot (T1 bis T4), der Fuzz erkennt also Abweichungen zwischen Code und Menge, nicht Abweichungen beider vom Standard. Mein eigener Abgleich der elf Codes mit meiner Kenntnis von MS-TDS stimmt, ist aber keine Messung. Dieselbe Klasse stand beim Review von `slice-backend-typabbildung` (F-5) als MEDIUM. | `AGENTS.md` §3.8 | `FramingOracle.kt` · `Pakettypen nach MS-TDS 2.2.3.1.1: 1, 2, 3, 4, 6, 7, 8, 14, 16, 17, 18` und `Typ im Katalog nach` | nein | Abschnittsnummer ohne Quelle, Orakel desselben Gedächtnisses |
| F-4 | MEDIUM | Die Zerstückelung der Fuzz-Ströme hat keinen Zähler und keine Prüfung. Mutation G3 (`WireGenerator.chunks` liefert immer den ganzen Strom) lässt alle neun Stabilitätstests grün. Mutation A6 (der Assembler prüft die Nachrichtengrenze nicht, wenn der Header über zwei Lesevorgänge ankommt) ist mit der Zerstückelung des Fuzz-Tests rot, mit G3 zusätzlich nur noch durch den Sturmtest rot, und dort hängt es an der Aufteilung durch TCP, die der Test nicht steuert. Beide KDocs (`FramingFuzzTest`, `HandshakeFuzzTest`) nennen die Zerstückelung als belegte Eingabe; der Zähler der Fehlerarten (Referenz-seitig, in G1 rot gesehen) deckt die Stückelung nicht ab. | `BEO-TDS/strukturtest-ohne-variation-der-eingabe`, [SPEC-028](../../spec/spezifikation.md#spec-028--prelogin-tls-und-login7-zustands--und-antwortvertrag), [SPEC-027](../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) | `WireGenerator.kt` · `fun chunks(wire: ByteArray): List<ByteArray>` | ja (Mutation G3 und A6 zusammen) | Eingabevariation (Stückelung) ohne Zähler |
| F-5 | MEDIUM | Die Aussage „Zahl offener Dateideskriptoren ist nach der zweiten Runde nicht gewachsen“ ist breiter als der Zähler. Die Prüfung ist `<= baseline + DESCRIPTOR_SLACK` mit Schlupf 3, ohne Begründung am Eintrag. Mutation L2a (jede 40. Verbindung, Ids 40, 80 und 120 der Hauptrunde, schließt den Kanal nie) bleibt grün; L2b (jede 10. Verbindung) wird rot erkannt, das Leck ist also real messbar und der Zähler trägt ab vier Lecks je Runde, nicht darunter. Lecks der Aufwärmrunde (21 Verbindungen) stecken in der Grundlinie und sind unsichtbar. Gemessen im Lauf mit Ausgabe: Grundlinie 65, nach der Runde 65, am Ende 65; der Schlupf war dort nicht gebraucht. Die Zahl ist die des ganzen Prozesses (Clients und Server liegen in derselben JVM) und gilt nur auf Linux (`UnixOperatingSystemMXBean`). | `AGENTS.md` §3.8, `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler` | `ConnectionStormTest.kt` · `openDescriptors() <= baseline + DESCRIPTOR_SLACK` und `nicht gewachsen` | ja (Mutation L2a und L2b) | Zähler trägt weniger als die Aussage, Schlupf ohne Messung |
| F-6 | LOW | `ScriptedChannel.closed` und `ScriptedChannel.bytesWritten` werden von keinem Test gelesen; das KDoc sagt „Schreiben wird angenommen und gezählt“. Mutation D1 (der Treiber schließt den Kanal bei Ausnahme oder Abbruch nicht mehr) bleibt in den Stabilitätstests grün; ob frühere Tests es decken, habe ich nicht geprüft, der Listener schließt in `finish` ohnehin selbst. | `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler`, Maintainability | `StabilityChannels.kt` · `var bytesWritten = 0L` | ja (Mutation D1) | Zähler ohne Leser |
| F-7 | LOW | Der Zähler des Anmelde-Fuzz trennt Rahmenfehler nicht von Kanalfehlern: der Schlüssel `Closed:READ_FAILED` (mindestens 30) wird auch von den Läufen mit `IO_FAILURE` bedient. Gemessen kommen von 1896 Lesefehlern 806 aus `IO_FAILURE` und 1090 aus sauberem Ende mit Referenzfehler; die Aussage im KDoc „Rahmenfehler vorkamen“ stimmt also, der Zähler trägt sie nicht. | `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler` | `HandshakeFuzzTest.kt` · `"Closed:READ_FAILED"` in der Liste der Mindestzähler | ja (Instrumentierung) | Zählschlüssel vermengt zwei Ursachen |
| F-8 | INFO | Offsets: die Fuzz-Ströme des Anmeldepfads verändern Offsets und Längen innerhalb von PRELOGIN und LOGIN7 nur durch zufälliges Überschreiben; gezielte Variation der Offsettabelle gibt es nicht (Art 6 ändert gezielt nur das Längenfeld des ersten Paketkopfs). Die DoD nennt „Offsets und Längen innerhalb der Nachricht“ ausdrücklich; Verweis an den Verifier. | [SPEC-027](../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) | `HandshakeFuzzTest.kt` · `private fun lengthByte` | nein | – |
| F-9 | INFO | Toleranz im Sturmtest: `handshakeOutcomes` lässt bis zu so viele zusätzliche Ausgänge `Closed:READ_FAILED` zu, wie hängende Verbindungen existieren, nicht nur für diese. Ein überzähliger Lesefehler einer nicht hängenden Verbindung wäre in dieser Menge versteckt. Beobachtet wurde das nicht. | Maintainability | `ConnectionStormTest.kt` · `extra in 0..hung` | nein | – |
| F-10 | INFO | Umfang: `StormSupport.kt` hat 321 Zeilen und trägt Handler, Client, RST-Client, Erwartungsmodell und den Fall-Katalog (fünf Typen). Detekt und die Dateigrößenregeln sind grün; die Datei ist die größte der Testdateien des Pakets (übrige 53 bis 185 Zeilen). | Maintainability | `StormSupport.kt` · `enum class Hostile` | nein | – |
| F-11 | INFO | Abgrenzung (Punkt 6): der Slice benennt `MAX_CONNECTIONS` (der Code enthält die Zeichenfolge nur im KDoc des Tests; `grep` über `src/main` leer), den fehlenden SQL_BATCH- und RPC-Pfad, den JDK-Socket statt `Microsoft.Data.SqlClient`, TLS ohne Anmeldung und die Messgrenze der Ressourcen, jeweils mit Träger oder Ausgang „offen“. Der Test „bis MAX_CONNECTIONS“ ist in der DoD offen geblieben. Die Fuzz-Tests der Parser `SqlBatchParser` und `RpcParser`, auf die der Slice verweist, existieren (`SqlBatchParserTest`, `RpcFuzzTest`). Die Planzeile `tools/mk/` wurde von „neu“ auf „entfällt“ geändert, mit Begründung (`make test` führt die Tests aus). Keine Aussage gefunden, die über das Gemessene hinausgeht, außer F-2 und F-5. | `AGENTS.md` §3.8 | `slice-qa-stabilitaet` · §6 | nein | – |
| F-12 | INFO | Flakiness (Punkt 4): 12 von 12 Läufen des Sturmtests bei 60 Busy-Loops grün (25 bis 30 s je Lauf, Last des Hosts zusätzlich über 100), zwei gleichzeitige Volläufe der neun Tests grün (3 m 8 s und 3 m 21 s). Kein reproduzierter Fehlschlag. Die Wartepunkte (`eventually`, fünf Sekunden; Zeit über `ManualDelayer` gesteuert; Handshake-Frist 60 s) tragen nach dieser Messung unter dieser Last; Aussage nur für diese Last und diese Läufe. Der Zähler ungefangener Ausnahmen kann ausschlagen (U1: Ausnahme im Abschlussbehandler des Kanals, rot mit Threadnamen). | `BEO-TDS/flake-fix-ohne-reproduzierten-fehler` | `ConnectionStormTest.kt` · `eventually("alle Fälle angenommen")` | ja (Wiederholung) | – |

## Mutationen (Punkt 1 und 3)

Jede einzeln gegen die neun Stabilitätstests, ein Build je Mutation (23 Läufe). „rot“ nennt die Tests, die ausschlugen. Kürzel sind meine eigenen; M2, M3 und M6 sind nach der Beschreibung des Auftrags nachgebaut.

| Kürzel | Mutation | Ergebnis |
|---|---|---|
| M2 | `PacketHeader`: Länge `< 4` statt `< 8` | rot: Sturm, FramingFuzz, HandshakeFuzz, StreamCutoff (Handvektor Länge 7) |
| M3 | `PacketHeader`: `>=` statt `>` an `maxPacketBytes` | rot: nur FramingFuzz (Länge genau auf der Paketgrenze kommt in Konfiguration 100/256 zufällig vor; es gibt keinen Handvektor wie bei der Nachrichtengrenze) |
| M3b | `MessageAssembler`: `>=` statt `>` an `maxMessageBytes` | rot: FramingFuzz (Grenztest und Referenz-Fuzz) |
| M6 | `MessageReader`: fertige Nachrichten vor dem Fehler verwerfen | rot: HandshakeFuzz, StreamCutoff |
| T1 bis T4 | Rahmenfehler-Typmenge: `PacketType` um 0x13, 0x00, 0xFF, 0x09 erweitert | je rot: FramingFuzz |
| A1 | `MessageAssembler`: `headerFill > 0` aus `insideMessage` | rot: Sturm, FramingFuzz, HandshakeFuzz, StreamCutoff (zwei Tests) |
| A2 | `MessageAssembler`: `messageType != null` aus `insideMessage` | rot: FramingFuzz, HandshakeFuzz, StreamCutoff |
| A3 | `MessageAssembler`: Eingabe nach dem Fehler weiter verarbeiten | rot: FramingFuzz, HandshakeFuzz |
| A4 | `MessageAssembler`: Puffer beim Verstoß leeren | rot: FramingFuzz |
| A5 | `MessageAssembler`: geteilter Header an falscher Stelle zusammengesetzt | rot: Sturm, FramingFuzz, HandshakeFuzz, StreamCutoff (zwei Tests) |
| A6 | `MessageAssembler`: Nachrichtengrenze bei geteiltem Header nicht geprüft | rot: Sturm, FramingFuzz |
| A6 mit G3 | A6 zusammen mit G3 | rot: nur Sturm (F-4) |
| Q1 | `MessageReader`: Kanalfehler wie Ende des Stroms behandeln | rot: Sturm, HandshakeFuzz, StreamCutoff |
| L1 | `ManagedConnection.finish`: `registry.release` entfernt | rot: Sturm |
| D1 | `HandshakeDriver`: Kanal bei Ausnahme oder Abbruch nicht schließen | überlebt (F-6) |
| L2a | `ManagedConnection`: Kanal nie schließen für Ids mit `id % 40 == 0` (3 Lecks) | überlebt (F-5) |
| L2b | wie L2a mit `id % 10` (10 Lecks) | rot: Sturm |
| U1 | `SocketChannelAdapter`: Ausnahme nach `resume` (Probe für den Zähler ungefangener Ausnahmen) | rot: Sturm, Meldung nennt die Threads |
| G1 | `WireGenerator`: keine Randlängen (`BOUNDARY_ONE_IN` sehr groß) | rot: FramingFuzz (Zähler der Fehlerarten trägt) |
| G3 | `WireGenerator.chunks` liefert immer den ganzen Strom | überlebt (F-4) |

Zuordnung zu M1 bis M14 des Implementers (außer M2, M3, M6) kann ich nicht belegen.

## Zu den übrigen Prüfpunkten

- **Punkt 2 (Orakel-Unabhängigkeit):** der Aufbau (Ganz-Strom-Leser ohne Zustandsautomat) ist unabhängig von der Struktur des Assemblers; die Regeln und die Typmenge sind es nicht (F-3). Das KDoc nennt die Festlegungen des Codecs, die das Orakel übernimmt, offen („die Festlegungen des Codecs, hier einzeln ausgeschrieben“); „unabhängig“ trägt für den Aufbau, nicht für den Inhalt der Typmenge. Die Handvektoren des Orakels (`Referenz entscheidet Handvektoren wie die Spezifikation`) prüfen jede Regel einzeln; die Regeln stammen aus derselben Quelle wie der Code.
- **Punkt 3 (Zähler und Variation):** der Zähler der Fehlerarten im Fuzz trägt (G1 rot; gemessen je Konfiguration hunderte Treffer je Art bei Mindestzahl 25). Nicht tragen: Stückelung (F-4), Deskriptoren unter vier Lecks (F-5), Schlüssel im Anmelde-Fuzz (F-7), Zähler am Kanal (F-6).
- **Punkt 5 (Ressourcenaussagen):** die Aussagen im Slice sind in §6 ehrlich begrenzt (Deskriptoren und Listener-Threads, nicht Heap und gemeinsame Pools). „Keine Leckage“ darf aus dem Test nicht gelesen werden: nur Deskriptoren, nur ab vier Lecks je Runde, nur Linux, ohne Aufwärmrunde, im ganzen Prozess; Threads nur mit Namensanfang des Listeners.
- **Punkt 7 (Hard Rules):** kein `@Suppress` oder `@SuppressWarnings` im Diff (`grep` null Treffer); alle Läufe nur in Docker (§3.1); §3.7 siehe F-1, sonst Indikativ; §3.8 siehe F-2, F-3, F-5.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `stability/FramingOracle.kt` | geprüft; Befund F-3 (Typmenge), sonst ohne Befund (Regeln, Reihenfolge der Prüfungen, gepufferte Bytes stimmen mit dem Assembler überein) |
| `stability/WireGenerator.kt` | geprüft; Befund F-4 |
| `stability/FramingFuzzTest.kt` | geprüft; Befund F-1, F-4 |
| `stability/StreamCutoffTest.kt` | geprüft, ohne Befund (Grenzen von Hand gezählt, alle Positionen, drei Puffer, beide Enden; M2, M6, A1, A2, A5, Q1 rot) |
| `stability/HandshakeFuzzTest.kt` | geprüft; Befund F-7, F-8 |
| `stability/StabilityChannels.kt` | geprüft; Befund F-6 |
| `stability/ConnectionStormTest.kt`, `stability/StormSupport.kt` | geprüft; Befund F-2, F-5, F-9, F-10 |
| `slice-qa-stabilitaet` | geprüft gegen den Code (Ist-Zustand und Abgrenzung), Befund nur über F-2 |
| Produktionsverzeichnisse (`src/main`) | im Diff unverändert, nur als Mutationsziel berührt |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 1 |
| MEDIUM | 4 |
| LOW | 2 |
| INFO | 5 |

**Finding-Klassen dieses Laufs:** Kommentar beschreibt verworfene Alternative · Gleichzeitigkeit behauptet, Spitze nicht gemessen · Abschnittsnummer ohne Quelle, Orakel desselben Gedächtnisses · Eingabevariation (Stückelung) ohne Zähler · Zähler trägt weniger als die Aussage · Zähler ohne Leser · Zählschlüssel vermengt zwei Ursachen

## Verdikt

**Merge-blockierend:** ja nach der Regel des Skills (HIGH F-1 und vier MEDIUM F-2 bis F-5); der Merge liegt vor, F-1 bis F-5 sind vor der Closure einzuarbeiten oder mit Begründung anzunehmen. F-1 ist eine Satzänderung. F-2, F-3 und F-5 lassen sich durch Einschränken der Aussage oder durch Nachmessen beheben, F-4 durch einen Zähler oder eine Prüfung der Stückelung. Die Produktions- und Testmutationen schlagen bis auf D1, L2a und G3 rot aus; die Läufe unter Last (12 von 12, zwei parallele Volläufe) zeigen keinen Fehlschlag.

**Übergabe:** Findings gehen an den Implementer; die Finding-Klassen gehen in die Closure-Notiz (§7) des Slice. DoD- und Spec-Konformität prüft der Verifier.
