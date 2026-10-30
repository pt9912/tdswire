# Welle welle-protokollcodec — TDS-Protokollcodec ohne Netzwerk — Closure-Notiz

> **Zitier-Form** *(bleibt stehen — Norm, kein Ausfüll-Hinweis).* Dieses
> Artefakt friert ein; was es zitiert, bewegt sich weiter. Deshalb: **Kennung,
> nicht Adresse** — `slice-<Kennung>` statt seines Lifecycle-Pfads, `make <target>`
> statt eines Links auf die Sensor-Datei, eine Baseline-Stelle als
> `v<X.Y.Z>` · `regelwerk/<datei>.md` §<Abschnitt> statt als Link
> (Baseline-Regelwerk `grundlagen-harness-dateien.md` §harness/README.md als
> Einstiegspunkt — beim Ausfüllen mit dem adoptierten Tag schreiben).

**Welle:** welle-protokollcodec
**Abschluss:** 2026-10-30
**Verantwortlich:** pt9912

## Was wurde geliefert?

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — *was gelernt wurde*: geliefert · was
funktionierte · was anders lief. Mit ID-Bezug, wo es einen gibt.

- Ein byteweise zerstückelter TDS-Nachrichtenstrom wird ohne Netzwerk zu Nachrichten zusammengesetzt und in die Antwort-Tokens zerlegt (und umgekehrt): Paket-Codec (Header, Zusammensetzen, Zerlegen in Pakete der Packet Size) und Nachrichten-Codec (PRELOGIN, LOGIN7-Parser-Grundlage, SQL_BATCH-Text, Encoder der Antwort-Tokens) in `slice-codec-tds-paket` ([TDS-FA-01](../../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-FA-02](../../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte)).
- Das Test-Gate `make test` ([TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)) hängt in `make gates`: 98 Tests (`kotlin.test`), darunter der Fuzz-Anteil zu [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität).
- Der gemeinsame Smoke (`CodecSmokeTest`) lässt PRELOGIN-Anfrage und -Antwort, LOGIN7 mit Login-Antwort und SQL_BATCH mit Resultset und RETURNSTATUS durch beide Codecs laufen, byteweise zerstückelt.
- Die Aussage gilt für den Codec, nicht für die Kompatibilität mit dem Referenzclient (Welle-Datei §6); die Zuordnung unten macht sichtbar, wie weit jede Aussage belegt ist.

**Das Mehr gegenüber der DoD des Slice (Welle-Datei §3): die Zuordnung *gemessen | Festlegung*.** Sie führt die Tabelle der Slice-Closure verdichtet weiter; vier Stärken statt zwei, weil ein Handvektor keine Messung ist. *Gemessen* heißt am Spike-Testserver in TDS 7.4 ohne TLS und Pool beobachtet; *Handvektor* heißt von Hand aus der Lesart von MS-TDS abgeleitet. Der Text von MS-TDS liegt nicht im Repo, Abschnittsnummern daraus nennt diese Notiz nicht.

| Stärke | Aussagen | Beleg im Repo |
|---|---|---|
| gemessen | TDS-Version im LOGINACK ist `74 00 00 04`; die Collation-ENVCHANGE (Typ 7) ist im Login nötig ([ADR-0005](../../adr/0005-rpc-und-prepared-statements.md), Messung) | `TokenEncoderTest` pinnt die Bytes als Regressionsanker; der Test misst nichts |
| Handvektor | Paketheader, PRELOGIN, LOGIN7 samt Entschleierung, SQL_BATCH, Token-Bytes (DONE-Familie, ERROR, INFO, ENVCHANGE, COLMETADATA, ROW, RETURNVALUE) | `PacketHeaderTest`, `PreloginCodecTest`, `Login7ParserTest`, `SqlBatchParserTest`, `TokenEncoderTest` |
| Selbstkonsistenz | Roundtrip Encoder und Test-Decoder, Writer und Assembler, der gemeinsame Smoke, Fuzz mit festem Startwert endet in Ergebniswerten mit Fehlerkategorie | `TokenRoundtripTest`, `PacketWriterTest`, `CodecSmokeTest`, Fuzz in fünf Testklassen; sie belegen keine Protokolltreue |
| Festlegung | Byteordnung von Programmversion (LOGINACK) und Unter-Build (PRELOGIN-VERSION), Packet-ID je Nachricht ab 1 und ungeprüft beim Lesen, Nachrichtengrenze, Packet Size 9 bis 65535, bekannte PRELOGIN-Optionen mit fester Länge, LOGIN7-Längenregeln, ENVCHANGE-Katalog Typ 1, 4, 7, Zeilenzahl ohne gesetztes Bit | KDoc der Elemente als *Festlegung*, je ein Test pinnt den Wert; ohne Orakel oder ohne Messung am Referenzclient |
| nicht gemessen | Reihenfolge der Tokens im Strom, DONE-Statusbits und ordinale Zählung der RETURNVALUE eines echten SQL Servers; Verhalten unter TDS 8.0 und Pooling | nur Handvektor (siehe Steering-Loop-Einträge, Ausgang `geplant`) |

**Jeder in Welle-Datei §6 ausgeschlossene Gegenstand hat einen Träger als Datei** (Closure-Trigger, Prüfung am 2026-10-30):

| Ausgeschlossen | Träger |
|---|---|
| Netzwerk jeder Art | `slice-transport-coroutines-geruest`, `slice-transport-listener-lebenszyklus`, `slice-transport-tls-schleife` (`welle-transport-tls`, alle in `open/`) |
| Handshake und Session | `slice-handshake-login` (`open/`); Zustandsautomat [ADR-0004](../../adr/0004-session-zustandsautomat.md) in `slice-adr-session-zustand` (`in-progress/`) |
| Backend | [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) in `slice-adr-backend-streaming` (`in-progress/`) |
| Anfrageseite von SQL_BATCH und RPC | `slice-codec-rpc-parser` und `slice-codec-datentypen-erweiterung` (`open/`, ohne Welle) |
| Fehler- und ATTENTION-Semantik | [ADR-0007](../../adr/0007-fehler-und-attention.md) in `slice-adr-fehler-attention` (`open/`) |
| Authentifizierung | `slice-auth-adapter-benutzerliste` und `slice-adr-pruefwertformat` (`open/`) |
| Referenzclient und echter SQL Server | `slice-qa-kompatibilitaet` und `slice-qa-reproduzierbarkeit` (`welle-qualitaet-grundlage`, `open/`) |
| Neue Anforderungen oder Änderungen am Lastenheft | kein Träger nötig: `spec/lastenheft.md` ist seit der Planung der Welle unverändert (`git log` über den Bereich leer) |

## Was hat funktioniert?

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3.

- Die Trennung nach Beleg-Stärke machte Review und Verifikation prüfbar: Beide fanden Aussagen über MS-TDS ohne Beleg im KDoc, und die Findings ließen sich durch Kennzeichnung als *Festlegung* beheben statt durch Codeänderung.
- Der Schnitt trug: Test-Gate, Paket-Codec und Nachrichten-Codec in einem Slice, ohne dass die Rückführung nach Welle-Datei §4 nötig wurde.
- Unabhängiger Test-Quelltext für die Verschleierung (`Login7Builder` im Test) hält den Parser vom eigenen Encoder unabhängig.

## Was ging anders als geplant?

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — jede Zeile moeglichst mit der Konsequenz,
die daraus schon gezogen wurde (Folge-Slice, Spec-Version).

- Der Test-Gate-Haken stand vor dem Beleg des absichtlich roten Laufs; der Beleg steht in der Slice-Closure nachgetragen. Konsequenz: keine, der Beleg ist erbracht (Verifier-Lauf, `make gates` Exit 2).
- Der Mutationsstand ist teils Angabe des Implementers ohne Ablage im Repo (59 Läufe); der Verifier-Anteil (38 Mutationen, 36 rot, 2 überlebt) ist nachvollziehbar. Konsequenz: keine Regel, nur die Benennung der Grenze.
- Die Review-Reports dieses Slice (Dateinamen `…-bba672b-codec-tds-paket.md` und `…-verifikation-codec-tds-paket.md`) tragen den Basisnamen `slice-codec-tds-paket` nicht; das Archivierungs-Werkzeug ordnet sie deshalb keinem Slice zu (siehe Verifikation, Schritt 4).

## Steering-Loop-Einträge

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 (hier stehen **nur** Beobachtungen, die im
Register 3× erreicht haben; jeder Eintrag nennt seine `BEO-<KUERZEL>/<slug>`) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln (Feld
und Zielort auf **einer** Zeile, Sektionsangabe innerhalb der Backticks).

Drei Einträge haben die Schwelle erreicht (`aussagen-ueber-messung-hinaus` 7×, `messung-am-echten-sql-server-fehlt` 3×, `tds-8-0-und-pooling-nicht-gemessen` 3×); die übrigen fünf Beobachtungen stehen mit 1× darunter und werden hier nicht gelesen.

- **Hard Rule** ergänzt: Eine Aussage in ADR, Spec, Vertrag, Slice, KDoc oder Test trägt höchstens so weit wie ihr Beleg (Messzeile, Handvektor mit Quelle oder gekennzeichnete Setzung); Abschnittsnummern externer Standards nur mit belegbarer Quelle; die Regel gilt auch für Code-KDoc.
  — liegt in `AGENTS.md §3.8`.
  Auslöser: `BEO-TDS/aussagen-ueber-messung-hinaus` (`slice-adr-authentifizierung`, `slice-adr-clientprofil`, `slice-adr-kompatibilitaetstests`, `slice-adr-rpc`, `slice-adr-tls`, `slice-adr-transport`, `slice-codec-tds-paket` — 7×).
- **Reviewer-Skill** geschärft: dieselbe Regel als MEDIUM-Anker, damit der Review sie als Finding mit Konventions-Anker führt.
  — liegt in `.harness/skills/reviewer.md`.
  Auslöser: `BEO-TDS/aussagen-ueber-messung-hinaus` (7×, wie oben).
- **Messlücke** mit Träger statt Regel: Tokenreihenfolge, DONE-Statusbits und RETURNVALUE-Zählung des Codecs liegen nur als Handvektor vor. Ausgang `geplant`, Träger `slice-qa-reproduzierbarkeit` (Vergleichslauf D gegen einen echten SQL Server); der DoD-Punkt 3 des Slice nennt die Messung seit dieser Closure. Das ist eine Setzung dieser Closure: Die Alternative, die Messung in `slice-qa-kompatibilitaet` zu führen, entfällt, weil jener Slice laut seinem §1 keinen Referenzabgleich gegen einen SQL Server führt.
  Auslöser: `BEO-TDS/messung-am-echten-sql-server-fehlt` (`slice-adr-authentifizierung`, `slice-adr-rpc`, `slice-codec-tds-paket` — 3×).
- **Messlücke** mit Träger statt Regel: Der Codec ist für TDS 8.0 und Pooling nur nach MS-TDS festgelegt. Ausgang `geplant`, Träger `slice-qa-kompatibilitaet`; dessen DoD-Punkt 3 nannte TDS 8.0 und Pooling schon und nennt seit dieser Closure auch die Codec-Aussage. Auch das ist eine Setzung dieser Closure; der Träger ist der Slice, der den Referenzclient gegen den Server dieses Repos fährt.
  Auslöser: `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` (`slice-adr-authentifizierung`, `slice-adr-rpc`, `slice-codec-tds-paket` — 3×).
- **Spec-Lücke** benannt, offen: SPEC-003 nennt „Token-/Typkennungen … zu verifizieren“; die Handvektoren sind eine Lesart von MS-TDS, keine Messung. Sie schließt sich mit den beiden Messlücken oben (`slice-qa-reproduzierbarkeit`, `slice-qa-kompatibilitaet`); kein `liegt in`, weil in einer Spec und nicht an einem Zielort verkörpert.

## Beobachtungs-Register (Zeiger)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register — der Zähler wird **nicht** hier gepflegt; diese
Sektion ist ein Zeiger und trägt keine Daten.

Der Zähler steht in [`../observations/`](../observations/).
Was in dieser Welle **3×** erreicht hat, steht oben unter
*Steering-Loop-Einträge*.

## Folge-Slices

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — **derivativ**: Diese Liste zeigt nur,
das Original ist die Slice-Datei. Jeder genannte Folge-Slice muss als Datei im
Planning-Lifecycle existieren; genannt ohne angelegt ist dieselbe Klasse wie
ein halluziniertes Gate.

- `slice-codec-rpc-parser` (RPC-Anfrage-Parser nach [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md)) — ohne Welle, die funktionale Welle für SQL_BATCH und RPC nimmt ihn auf.
- `slice-codec-datentypen-erweiterung` (NBCROW, PLP, Typen außerhalb des Mindestkatalogs) — ohne Welle.
- `slice-qa-reproduzierbarkeit` (Träger der Messung am echten SQL Server, DoD angepasst) und `slice-qa-kompatibilitaet` (Träger der TDS-8.0- und Pooling-Messung, DoD angepasst) — beide in `welle-qualitaet-grundlage`, schon vor dieser Closure angelegt.

## Verifikation

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 1 — keine Behauptung ohne nachprüfbaren
Anker (Hash, Lauf, Zahl).

- Alle Slices der Welle in `done/`: `slice-codec-tds-paket` (Merge `debc067`, Review und Verifikation gegen `bba672b` ohne HIGH).
- `make gates` grün am 2026-10-30 auf dem Stand `debc067` plus Closure (Exit 0), `baseline-verify: v6.18.1 OK — 54 Dateien`.
- `make test` in einem Lauf ohne Cache (`--no-cache-filter test`): `BUILD SUCCESSFUL in 36s`, 98 Tests `PASSED`, 0 `FAILED`; der Smoke gemeinsam durch beide Codecs: `CodecSmokeTest > PRELOGIN-Anfrage und -Antwort() PASSED`, `CodecSmokeTest > SQL_BATCH und Resultset mit RETURNSTATUS() PASSED`, `CodecSmokeTest > LOGIN7 und Login-Antwort() PASSED`.
- Zuordnung *gemessen | Festlegung* und Träger je ausgeschlossenem Gegenstand: siehe oben (Closure-Trigger der Welle-Datei).
- Trigger-Audit: **Carveouts** 0 offen (`docs/plan/carveouts/` trägt nur `.gitkeep`; keine Kennung `CO-` im Bestand). **Bootstrap-aware Gates** 0 offen (kein Target trägt eine Schwelle oder Reifestufe; die Bindungsspalte von `harness/README.md` führt keine). **ADR-Re-Evaluierungs-Trigger** der von der Welle berührten, angenommenen ADRs: [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md) (fehlgeschlagener Handshake im Profil: kein Handshake gebaut), [ADR-0002](../../adr/0002-tls-und-verschluesselung.md) (neuer Verschlüsselungsdefault: nein), [ADR-0003](../../adr/0003-transportframework.md) (Leaks, Backpressure: kein Netzwerk gebaut), [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) (neuer RPC-Aufruftyp oder abweichende Antwort eines echten SQL Servers: nicht gemessen, nicht eingetreten), [ADR-0009](../../adr/0009-authentifizierungsmodus.md) (neuer Modus: nein) — 0 fällig. Der Wächter für den Trigger von [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) ist die Messlücke oben.
- Archivierung (Schritt 4): siehe Abschnitt *Archivierung* unten.
