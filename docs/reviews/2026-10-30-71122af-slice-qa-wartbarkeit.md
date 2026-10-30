# Review slice-qa-wartbarkeit (Merge 71122af)

* Gegenstand: `docs/plan/kompatibilitaetsmatrix.md` (neu) und `docs/plan/planning/in-progress/slice-qa-wartbarkeit.md` §3 (Diff `cff52a9..71122af`: 2 Dateien)
* Grundlage: [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0010](../plan/adr/0010-detekt-pre-release.md), [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md), [ADR-0009](../plan/adr/0009-authentifizierungsmodus.md), [ADR-0002](../plan/adr/0002-tls-und-verschluesselung.md), [ADR-0004](../plan/adr/0004-session-zustandsautomat.md), [ADR-0007](../plan/adr/0007-fehler-und-attention.md), `spec/architecture.md` §2, `AGENTS.md` §3.1 bis 3.8 und §5 Regel 6
* Gegen DoD wird nicht geprüft (Verifier).

## Läufe

| Lauf | Ergebnis |
|---|---|
| `docker build --target test --no-cache-filter test --progress=plain .` | Exit 0; 280 Zeilen `PASSED` in 37 Klassen, kein `FAILED`, kein übersprungener Testfall |
| alle 161 Zitate (132 verschiedene) `Klasse > Testfall` | jeder verschiedene Wert steht im frischen Bericht als `PASSED`; jeder existiert als `fun` im Quelltext (ein Fall `ScriptedBackendContractTest > Ergebnis kommt vollstaendig und in Reihenfolge an` liegt in der geerbten abstrakten Klasse `QueryBackendContract`, 8 Fälle, wie behauptet) |
| `make docs-check` | 253 Dateien, 0 Befunde |
| `make gates` | Exit 0 |
| Diff gegen `src/`, `build.gradle.kts`, Lock, `tools/`, Makefile-Fragmente seit `cff52a9` | leer; `GATE_CHECKS` unverändert |
| `src/` seit `bda6cd6` (Stand des Belegs) | unverändert |

## Findings

### MEDIUM

**M-1**
* kategorie: MEDIUM
* quelle: `AGENTS.md` §3.8, §5 Regel 6
* pfad: `docs/plan/kompatibilitaetsmatrix.md` · `| 7 | Typgrößen `FLTN`, `MONEYN`, `DATETIMN`, `GUID`, Zuschlag bei `DATETIMEOFFSETN` | nur Handvektor und Selbstkonsistenz (3b) | `slice-qa-kompatibilitaet`, `slice-qa-reproduzierbarkeit` |`
* befund: Die Matrix führt zwei Slices als Träger, aber weder `open/slice-qa-kompatibilitaet.md` noch `in-progress/slice-qa-reproduzierbarkeit.md` enthält `FLTN`, `MONEYN`, `GUID` oder die Typgrößen (grep: 0 Treffer); der Kopf des Abschnitts definiert „kein Träger“ als „keine Slice-Datei führt den Punkt in DoD oder Risiko“, also müsste die Zeile „kein Träger“ lauten. Szenario: Beide Slices schließen, die Typgrößen bleiben ungemessen, und die Matrix hat den Punkt als getragen ausgewiesen.
* verifizierbar: ja (grep über die beiden Slice-Dateien)
* klasse: Träger-Angabe ohne tragenden Slice-Text

### LOW

**L-1**
* kategorie: LOW
* quelle: `AGENTS.md` §3.8
* pfad: `docs/plan/kompatibilitaetsmatrix.md` · `| 10 | `SqlType.Decimal` gegen Precision-Grenze des Codecs | ... | im KDoc von `SqlType.Decimal` als offen geführt, kein Träger |`
* befund: `in-progress/slice-backend-typabbildung.md` führt den Punkt im Risiko (§6, „Modellgrenzen von `SqlType` gegen die Abbildung“ (a), Ausgang „offen bis zur Closure“) und in der DoD (Decimal 1 bis 38); die Zeile „kein Träger“ ist falsch, der Träger fehlt.
* verifizierbar: ja
* klasse: Träger-Angabe ohne tragenden Slice-Text

**L-2**
* kategorie: LOW
* quelle: Maintainability (Konsistenz der eigenen Definition in Abschnitt 5)
* pfad: `docs/plan/kompatibilitaetsmatrix.md` · Zeile 5 (Table-Valued-Parameter: „slice-rpc-executesql-parameter führt es als nicht im Katalog“); ebenso Zeile 14 („Messung der Ablehnung am Referenzclient in slice-qa-kompatibilitaet“) und Zeile 9 (`Pooling` in `slice-qa-reproduzierbarkeit`)
* befund: Zeile 5 nennt einen Slice, der den Punkt ausschließt (Out-of-Scope, `slice-rpc-executesql-parameter` §1) und nach der eigenen Definition „kein Träger“ wäre. Zeile 14: `slice-qa-kompatibilitaet` führt die Messung von Windows-SSPI (NTLM) als Anmeldung, nicht die Messung der Ablehnung; Kennwortwechsel und FeatureExt `0x02` stehen dort nicht. Zeile 9: `slice-qa-reproduzierbarkeit` nennt weder Pooling noch RESETCONNECTION (Träger dafür ist allein `slice-qa-kompatibilitaet`).
* verifizierbar: ja
* klasse: Träger-Angabe ohne tragenden Slice-Text

**L-3**
* kategorie: LOW
* quelle: `AGENTS.md` §3.8
* pfad: `docs/plan/kompatibilitaetsmatrix.md` · `| `tinyint`, `smallint`, `int`, `bigint` | `INTN` 1/2/4/8 | ... | Typwahl des Clients gemessen (Spike); Lesart der Bytes Handvektor |`
* befund: Die Messmatrix in [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md) nennt als gemessene Parametertypen `int`, `bigint`, `nvarchar`, `varchar`, `decimal`, `datetime2`, `bit`, `uniqueidentifier`, `varbinary`; `tinyint` und `smallint` (INTN 1 und 2) stehen dort nicht. Die Matrix folgt `SPEC-003` („vom Referenzclient beobachtet“), das ADR und Spec in diesem Punkt nicht deckungsgleich führen; die Stufe „gemessen (Spike)“ reicht für 1 und 2 Byte nur so weit wie diese Spec-Zeile. Die Zeilen `float`, `money`, `datetime`, `date`, `time`, `binary`, `char`, `nchar` führen die Typwahl dagegen sauber als Handvektor/nicht gemessen.
* verifizierbar: nein (Messung liegt außerhalb des Repos)
* klasse: Herkunftsstufe breiter als Messmatrix

**L-4**
* kategorie: LOW
* quelle: `AGENTS.md` §5 Regel 6 (Beleg ist ein bestandener Test, nicht ein Verweis)
* pfad: `docs/plan/kompatibilitaetsmatrix.md` · `| Transaction Manager (`0x0E`) | nicht unterstützt: der Pakettyp ist bekannt und gilt in `READY` als Anfrage (wird weitergegeben) ...` mit Test `PacketHeaderTest > Pakettyp-Codes haben die Werte aus MS-TDS`
* befund: Der Test pinnt nur den Typcode; die Aussage „gilt in `READY` als Anfrage, wird weitergegeben“ (`HandshakeEvent.kt`, `TRANSACTION_MANAGER -> REQUEST_MESSAGE`) hat keinen Testfall (`TRANSACTION_MANAGER` kommt in `src/test` nur in `PacketHeaderTest` vor). Die Zeile benennt „nur der Typcode“; das Verhalten bleibt unbelegt.
* verifizierbar: ja
* klasse: Verhaltensaussage ohne Testfall

**L-5**
* kategorie: LOW
* quelle: `AGENTS.md` §5 Regel 6
* pfad: `docs/plan/kompatibilitaetsmatrix.md` · `| TLS: Mindestversion, Zertifikatsfehler, abgebrochener Handshake | nicht unterstützt (Ergebniswert, kein Klartext auf der Leitung) ...` mit `TlsLoopbackTest > payload never appears in plaintext on the wire`
* befund: Der zitierte Test prüft den Erfolgsfall (nach gelungenem Handshake steht die Nutzlast nicht im Klartext auf dem Draht), nicht die genannten Fehlerfälle. „Kein Klartext“ nach Versions-, Zertifikats- und Abbruchfehler belegt er nicht; `TlsNegativeTest > plaintext instead of TLS in the eight zero path ...` belegt es nur für diesen Fall (0 geschriebene Bytes).
* verifizierbar: ja
* klasse: Test belegt anderen Fall als die Zeile

**L-6**
* kategorie: LOW
* quelle: `AGENTS.md` §5 Regel 6
* pfad: `docs/plan/kompatibilitaetsmatrix.md` · Abschnitt „Pflege“ · `Wer sie ändert, lässt `make test` mit frischem Testlauf ... laufen`
* befund: Die Aktualisierungsregel greift nur, wenn jemand die Matrix ändert. Der Stand ist als Merge-Stand `bda6cd6` benannt (gut: `src/` ist seitdem unverändert), aber es gibt kein Datum und keinen Auslöser bei Änderung am Codec oder an einem zitierten Testfall; ein umbenannter Testfall lässt eine „unterstützt“-Zeile ohne Beleg stehen, ohne dass etwas rot wird. Die Wegwerf-Prüfung der Zitate (Plan §3) liegt nicht im Repo; die Handpflege selbst ist im Plan §3 begründet entschieden.
* verifizierbar: nein
* klasse: Handgepflegtes Abdeckungs-Dokument ohne Auslöser

### INFO

**I-1** (Abschnitt 6a, Zählung „sieben der acht Package-Zeilen decken sich“) · Die Zeile `transport` ist als „deckt“ geführt, hat aber dieselbe Lücke wie `protocol`: `ForbiddenImport/transport` verbietet nur `port`, `simulation`, `runtime`; §2 erlaubt dem Transport nur „Protocol-Schnittstellen“ und „Observability-Schnittstellen“, und der Kommentar in `config/detekt/detekt.yml` (Zeilen 161 bis 163) nennt „transport und observability“ als noch ohne Schnitt zwischen Schnittstelle und Implementierung. Ist-Stand: `transport` importiert `protocol.session.HandshakeChannel` und `Inbound` (Schnittstelle bzw. versiegelte Schnittstelle, unverletzt). Die Abweichung ist damit für `transport`/`protocol` symmetrisch, die Matrix nennt sie nur für `protocol`. Zudem bilden `protocol → transport.framing` (13 Importe) und `transport → protocol.session` (2) eine Paketabhängigkeit in beide Richtungen, die §2 für diese Paare vorsieht und die Matrix nicht erwähnt. Zuständig: Architect.

**I-2** (Abschnitt 6a) · Importzählungen nachgeprüft und stimmig: `port` 2 (beide `domain`), `config` 2 (`domain`), `protocol` 17 `config.auth`, 2 `domain`, 13 `transport.framing` (`FramingLimits`, `PacketHeader`, `PacketType`, `TdsMessage`), `transport` 2 `protocol.session`; `observability`, `simulation`, `runtime` ohne Verzeichnis. Die Aussage „kein Sensor hält `detekt.yml` gegen `architecture.md` §2“ stimmt (das Gate prüft nur Konfiguration gegen Testdaten aus ihren eigenen Werten). Abschnitt 6b (Testimporte `protocol`: nur `config`, `domain`, `protocol`, `transport`, `testsupport`; `port`: 14 Importe, alle `domain`) stimmt.

**I-3** (Abschnitt 3a, Zeile „mehrere RPCs“ gegen Lücke 4) · Zeile 3a zitiert [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md) als „kam nicht vor“, Lücke 4 schreibt „[ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md) kennt es nicht“; die ADR führt es unter „Nicht gemessen“. Redaktionell widersprüchlich.

**I-4** (Abschnitt 3b, Zeile `text`, `ntext`, `image`, `sql_variant`, UDT, `json`) · Der Test `PLP, XML und unbekannte Typen ...` übergibt unter anderem `62`; das ist die Kennung von `sql_variant`. Die Zeile nennt „genannten Typen nicht einzeln“; für `sql_variant` stimmt das nicht ganz, für `text`, `ntext`, `image`, UDT, `json` ja.

**I-5** (Abschnitt 2, Zeile DONE) · Die Statusbit-Werte stehen als „Handvektor“; `Token.kt` zitiert „MS-TDS 2.2.7.6“ als Abschnitt. Nicht Teil des Diffs; Hinweis für den Verifier, ob die Abschnittsnummer (`AGENTS.md` §3.8) belegbar ist.

## Prüffragen des Auftrags

1. **Stichprobe.** Alle 132 verschiedenen Zitate gegen Quelltext und frischen Lauf: existent und `PASSED`. Inhaltlich gegengelesen (rund 35): u. a. `PreloginNegotiationTest > REQUIRED nimmt OFF, ON und REQ ...`, `HandshakeDriverTest > Unvereinbare Verschluesselung ...`, `LoginAdmissionTest > Eine andere FeatureExt-Option ...`, `HandshakeMatrixTest` (beide), `PacketHeaderTest > Pakettyp-Codes ...`, `RpcCatalogFormsTest > sp_prepexec ...` und `Unbekannte ProcID ...` (11, 15, 0, wie in der Zeile genannt), `RpcTypesTest > Statusbyte ...` und `Datums- und Zeittypen ...` (Länge aus Scale, wie als Selbstkonsistenz geführt), `RpcFailureTest > PLP lehnt ...`, `PLP, XML und unbekannte Typen ...`, `Mehr Parameter als die Obergrenze ...`, `TokenEncoderTest > DONE-Familie ...`, `DONE traegt 64-Bit-Zeilenzahl`, `TlsLoopbackTest > encapsulated path ... every suite`, `payload never appears ...`, `TlsNegativeTest > plaintext instead of TLS ...`. Test belegt etwas anderes bzw. weniger: L-4, L-5; der ATTENTION-Zeile genügt `HandshakeMatrixTest > Jede Zelle ...` (alle Zustände und Ereignisarten), der Test „SQL_BATCH vor READY ...“ deckt ATTENTION nur vor `READY`.
2. **Herkunftsstufen.** Gegen [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md) (Messmatrix, „Nicht gemessen“), `contracts/client-profile.md` (Login-Antwort: nur LOGINACK mit DONE, Collation-ENVCHANGE, genau ein ERROR gemessen), [ADR-0002](../plan/adr/0002-tls-und-verschluesselung.md)/Closure `slice-transport-tls-schleife` (JDK-Gegenstelle), [ADR-0009](../plan/adr/0009-authentifizierungsmodus.md), [ADR-0004](../plan/adr/0004-session-zustandsautomat.md), [ADR-0007](../plan/adr/0007-fehler-und-attention.md): stimmig, bis auf L-3. Tiefe der Prüfung: [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md) und Clientprofil Zeile für Zeile; [ADR-0002](../plan/adr/0002-tls-und-verschluesselung.md), [ADR-0004](../plan/adr/0004-session-zustandsautomat.md), [ADR-0007](../plan/adr/0007-fehler-und-attention.md), [ADR-0009](../plan/adr/0009-authentifizierungsmodus.md) und die Closure-Notizen nur stichprobenartig (Aussagen „Festlegung“ und „JDK-Gegenstelle“ gegen die Testkommentare, nicht gegen jeden ADR-Messabschnitt). Nichts, was nur Handvektor oder Selbstkonsistenz ist, steht als „gemessen“. Der Referenzclient ist durchgehend „nicht gemessen“ (Kopf, TLS-Zeilen, Lücke 1, 9); die Matrix wiederholt dabei, dass `referenztest-dotnet` nur `KeinServerSmokeTests` enthält.
3. **Lücken.** Träger vorhanden und tragend: 2, 6 (`slice-codec-datentypen-erweiterung` führt PLP auch als RPC-Parameter und NBCROW), 8, 12, 13 (`slice-attention-cancel-timeout`, `slice-sql-batch-dispatch`, `slice-fehler-mapping`, `slice-rpc-prepared-handles`), 11 (Spec/ADR-Offen). Nicht getragen oder falsch: 7 (M-1), 10 (L-1), 5/9/14 (L-2). **`xml` als RPC-Parameter bestätigt:** `SPEC-003` führt `xml` als `XML` und unter den PLP-Typen; weder DoD noch Risiko eines Slice in `open/`, `in-progress/` oder `done/` führt `xml` (`slice-codec-datentypen-erweiterung` nennt `xml` nicht); „kein Träger“ in Lücke 3 stimmt. Batch-Flag (Lücke 4): `slice-codec-rpc-parser` nennt es nur als „nicht erkannt“, „kein Träger“ stimmt.
4. **Modulgrenzen.** Siehe I-1 und I-2. Die ausdrücklich benannte Abweichung („`protocol` darf `transport` ganz importieren, §2 verbietet die Implementierung“) stimmt gegen `detekt.yml` (`ForbiddenImport/protocol` verbietet nur `simulation`, `runtime`) und `grep`; Ist-Stand verletzt §2 nicht. Selbsttest-Angaben (Abschnitt 6a „Belege“) nicht neu gelaufen; `make gates` (enthält `detekt-selbsttest`) ist grün.
5. **Veraltungsrisiko.** Stand ist als Merge-Stand `bda6cd6` benannt, kein Datum, Regel nur änderungsgetrieben: L-6.
6. **Hard Rules.** 3.1: nur `make`/`docker`-Läufe zitiert, kein Host-Gradle. 3.2: kein `@Suppress`. 3.7: keine Code-/Konfigurationskommentare im Diff; `Stand`-Zeile nennt Zustand und Beleg als Anker. Der Absatz „Rot gesehen (einmalig, nicht dauerhaft) ... Wiederherstellung ... Exit-Code 0“ in 6a erzählt den Messvorgang; er ist ein Beleg, keine Chronik eines Zustandsfeldes, daher kein Finding. 3.8: siehe M-1, L-3. d-check: 0 Befunde, Links der Matrix lösen auf. Kein neues Gate, keine Änderung an `src/`, `build.gradle.kts`, Lock.

## Plan-Abgleich Slice-Plan §3

Die Tabelle in §3 führt die Matrix doppelt (Zeile 1 ohne Pfad „Kompatibilitätsmatrix, Datei unter docs/plan/“, Zeile 2 mit Pfad); das stammt aus dem Slice-Gerüst und ist keine Änderung dieses Diffs. Der „Erste Lauf“ (280 Testfälle, alle bestanden) stimmt mit dem frischen Lauf überein. Zur Entscheidung „Handpflege statt Erzeuger“ siehe L-6.

## Negativbefund (geprüft, ohne Befund)

* `docs/plan/planning/in-progress/slice-qa-wartbarkeit.md`: ohne Befund (Änderung beschränkt auf §3)
* `docs/plan/adr/` (0002, 0004, 0005, 0007, 0009, 0010): unverändert im Diff, ohne Befund
* `config/detekt/detekt.yml` und `spec/architecture.md` §2: unverändert, Abgleich siehe I-1, I-2
* `src/`, `build.gradle.kts`, `gradle.lockfile`, `tools/`, `harness/mk/`: unverändert, ohne Befund
* `src/test/` (Quelltext der zitierten Testfälle): ohne Befund außer L-4, L-5

## Zusammenfassung

0 HIGH, 1 MEDIUM, 6 LOW, 5 INFO. Alle 132 verschiedenen Testzitate (161 Vorkommen) existieren und stehen im frischen Lauf als `PASSED`; die Herkunftsstufen sind bis auf L-3 durch die ADR-Messabschnitte gedeckt; die Schwäche liegt in Abschnitt 5 (Träger-Angaben).
