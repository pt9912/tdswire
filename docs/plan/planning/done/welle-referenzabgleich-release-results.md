# Welle welle-referenzabgleich-release — Fertiger Server: Referenzabgleich mit einer SQL-Server-Instanz, lauffähiges Artefakt, Dokumentation und Release-Schritte — Closure-Notiz

> **Zitier-Form** *(bleibt stehen — Norm, kein Ausfüll-Hinweis).* Dieses
> Artefakt friert ein; was es zitiert, bewegt sich weiter. Deshalb: **Kennung,
> nicht Adresse** — `slice-<Kennung>` statt seines Lifecycle-Pfads, `make <target>`
> statt eines Links auf die Sensor-Datei, eine Baseline-Stelle als
> `v<X.Y.Z>` · `regelwerk/<datei>.md` §<Abschnitt> statt als Link
> (Baseline-Regelwerk `grundlagen-harness-dateien.md` §harness/README.md als
> Einstiegspunkt — beim Ausfüllen mit dem adoptierten Tag schreiben).

**Welle:** welle-referenzabgleich-release
**Abschluss:** 2026-11-17
**Verantwortlich:** pt9912

Auftrag zur Closure: Nutzer, 2026-11-17 („ja, welle-referenzabgleich-release schließen“).

## Was wurde geliefert?

Regeln dieser Sektion: Baseline-Regelwerk `v6.18.1` · `regelwerk/modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — *was gelernt wurde*: geliefert · was
funktionierte · was anders lief. Mit ID-Bezug, wo es einen gibt.

Die Welle-Datei nennt fünf Slices (§4); am Dateisystem tragen sieben Slices in `done/` das Feld `Welle: welle-referenzabgleich-release` (die zwei später angelegten sind `slice-referenzabgleich-adr-0028-nachzug` und `slice-release-entscheidungen`). Keiner liegt in `open/`, `next/` oder `in-progress/` mit diesem Feld.

- **Live-Lauf** (`slice-referenzabgleich-live`, [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)): derselbe Referenzclient (25 Schritte) gegen `2022-latest`, `2025-latest` und den Server dieses Repos mit Simulations-Backend; `make vergleichslauf-d LAUF=live`; Ausnahmeliste `referenztest-dotnet/Vergleich/live-ausnahmen.tsv`; Gegenmessungen M-1 bis M-5, darunter M-4, die [ADR-0037](../../adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md) auslöste.
- **Mitschnitt-Lauf** (`slice-referenzabgleich-mitschnitt`, [TDS-RB-03](../../../../spec/lastenheft.md#tds-rb-03--protokollstandard), [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md)): mitschneidender Proxy mit Redaktion (kein Kennwort, kein Prüfwert, kein Servername in der Ablage), 17 Szenarien je Instanz, feldweiser Vergleich beim Abspielen gegen den frisch gestarteten Server (`LAUF=aufnahme`, `LAUF=mitschnitt`); Ausnahmeliste `mitschnitt-ausnahmen.tsv`; die Wire-Messung von Tokenreihenfolge, DONE-Statusbits und RETURNVALUE-Zählung.
- **Antwort-Tokens an die Messung angeglichen** (`slice-antwort-tokens-an-messung-angleichen`, [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte)): Ordinal der RETURNVALUE, `curCmd`, `MORE` bei statement-abbrechendem Fehler, RETURNSTATUS, ENVCHANGE bei `RESETCONNECTION`; Spezifikation dem echten Server angeglichen (Nutzerentscheid 2026-11-06); [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) und [ADR-0039](../../adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md) `Accepted`.
- **Artefakt und Lizenz** (`slice-release-artefakt-lizenz`, [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-RB-05](../../../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)): Laufzeittest und Lizenznachweis; das Produkt-Image entfiel mit [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md), der Laufzeittest läuft heute gegen den Testserver-Container (`make laufzeittest`).
- **Doku und Prozess** (`slice-release-doku-prozess`, [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0010](../../adr/0010-detekt-pre-release.md)): Nutzer- und Maintainer-Doku, Release-Schritte; die Entscheidungen des Nutzers zu Wächter-Form, Stückliste und Registry liefert `slice-release-entscheidungen` (`releasing.md`: `make e2e` als Schritt, Wächter für [ADR-0010](../../adr/0010-detekt-pre-release.md) als Schritt ohne Target, Stückliste zurückgestellt).
- **Nachzug TDS 8.0** (`slice-referenzabgleich-adr-0028-nachzug`, [ADR-0028](../../adr/0028-referenzinstanz-fuer-tds-8-0.md) `Accepted`): `2025-latest` ist als Setzung Referenzinstanz des TDS-8.0-Pfads; `SPEC-045`, `SPEC-050` und zwei Pläne nachgezogen.

## Was hat funktioniert?

Regeln dieser Sektion: Baseline-Regelwerk `v6.18.1` · `regelwerk/modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3.

- **Die Ausnahmeliste als Sensor.** Eine kleinere oder größere Abweichung als die gelistete macht den Lauf rot (Selbsttest in `make referenztest-dotnet` und `make skript-selbsttest`); jede Lücke, die ein Slice baute, verschwand aus der Liste und der Lauf blieb grün: A-10 (`curCmd`), A-12 (NBCROW) und A-19/A-20 (Flag-Feld) sind entfallen, A-3 ist von Class 20 auf Class 16 gewandert (`slice-unbekannte-tabelle-class-16-angleichen`).
- **Review und Verifikation fanden, was der Bau nicht hielt:** *so gewollt* ohne Beleg (A-3, A-4/A-5, A-9 wurden *Lücke*, F-1, F-2, F-6 des Live-Reviews; A-13, A-14 im Mitschnitt-Review), die abweichende Messbedingung `ConnectRetryCount=0` neben dem Standard 1 (F-1, SESSIONSTATE), den Servernamen im ERROR-Token (Redaktion), den Widerspruch zum Wortlaut von [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) (F-1 des Token-Slice, Folge [ADR-0039](../../adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md)).
- **Entscheidungen als eigener, kleiner Slice** (`slice-release-entscheidungen`): trägt nur ein, was der Nutzer entschied, und benennt Zurückgestelltes mit Träger.

## Was ging anders als geplant?

Regeln dieser Sektion: Baseline-Regelwerk `v6.18.1` · `regelwerk/modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — jede Zeile möglichst mit der Konsequenz,
die daraus schon gezogen wurde (Folge-Slice, Spec-Version).

- **Das Produkt-Image entfiel** ([ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)); der Closure-Trigger „Laufzeittest in `make gates`“ der Welle-Datei ist durch [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) überholt: `make laufzeittest` ist kein Gate mehr, sondern Stufe 2 von `make e2e` (siehe Verifikation, Kriterium 2).
- **Die Läufe laufen nicht gegen die Stage `testserver`**, wie der Closure-Trigger 3 es nennt, sondern gegen die Stage `kompatibilitaetsserver` des Dockerfile (Container, Gradle-Task `kompatibilitaetsServer` des Moduls `:testserver`, Klartext beim Mitschnitt, `REQUIRED` beim Live-Lauf); die Stage `testserver` fährt der Laufzeittest.
- **Anmeldung nicht im Mitschnitt** (bauartbedingt, verschlüsselt); sie ist später am Draht gemessen (W-8, W-9, `slice-qa-vergleichslauf-d-messungen`); der Mitschnitt-Lauf hat zwei Ausnahmelisten-Dateien statt einer und `make spec-abdeckung` las C# erst nach `slice-spec-abdeckung-liest-csharp`.
- **DoD-Wortlaut über der Bauart** (zwei Punkte des Mitschnitt-Slice nicht erfüllbar, ein dritter beschrieb eine Struktur, die der Schlüsselraum nicht trägt) und **Entscheidungen des Nutzers als Lieferinhalt** (DoD 2 und 3 von `slice-release-doku-prozess`): beides fiel bei Review und Verifikation auf, nicht bei der Planung; verkörpert als Planner-Schritt 6b (siehe Steering-Loop-Einträge).
- **Nachbesserung ohne zweiten Review** bei `slice-antwort-tokens-an-messung-angleichen` (V-4, B-4 nicht nachgeholt): als Grenze angenommen.

## Zwei Spalten: gemessen und Festlegung

Grenzen für alle Zeilen *gemessen*: Einzelmessungen, je Szenario und Instanz eine Aufnahme oder ein Lauf, ein Host (20 CPUs, 31 GiB Speicher, Docker 29.9.2), `Microsoft.Data.SqlClient` 7.1.1, `2022-latest` und `2025-latest` mit den Digests des Messprotokolls `docs/messungen/referenzabgleich.md`, TDS 7.4. Verglichen wird die Ausgabe des Clients (Live-Lauf) und die Tokenfelder der Antworten (Mitschnitt-Lauf), nicht der Client selbst. „Grün“ heißt: jede Abweichung stimmt mit der Liste überein; es heißt nicht, dass der Server dem Zielbild entspricht.

| Aussage | Spalte | Beleg und Grenze |
|---|---|---|
| Live-Lauf: 25 Schritte, 12 gleich, 13 gleich der gelisteten Abweichung, beide Instanzen | gemessen | Closure von `slice-referenzabgleich-live` (2026-11-06, `live-2022` 80 s bei Last 35 bis 40); seither nicht auf `main` wiederholt (siehe Verifikation, Kriterium 2) |
| Mitschnitt-Lauf: 17 Szenarien je Instanz, 324 Felder je Teillauf | gemessen | zuletzt `make vergleichslauf-d LAUF=mitschnitt` Exit 0, 36 s, Verifikation von `slice-antwortform-nbcrow` auf `853239d5` (2026-11-17); `LAUF=mitschnitt-2022` Exit 0, 22 s (2026-11-11); Abspielen gegen den Server dieses Repos, keine Instanz nötig |
| Tokenfolge, DONE-Statusbits, Ordinal, RETURNSTATUS, ENVCHANGE, Fehler im RPC | gemessen | Messreihe am Draht W-1 bis W-9 und Mitschnitt-Ergebnisse 1 bis 6; ein Fall je Instanz bei Ordinal und Bits bei statement-abbrechendem Fehler |
| Server weicht in 50 Regeln der beiden Listen als *Lücke* ab | gemessen | Schuld des Servers am Zielbild; Träger siehe Tabelle unten |
| Kein Kennwort, kein Prüfwert, kein Servername in der Ablage | gemessen | Test und Prüfung am Dateiinhalt; die Redaktion sucht den bekannten Wert, keine anderen Kennungen |
| Laufzeittest: Start, Rohbytes-Login aus zweitem Container, Stop, Exit 143 | gemessen | `make laufzeittest`, elf Prüfzeilen, 24 s warm (Verifikation von `slice-release-artefakt-lizenz`, Einzelmessung, Image-Stand vor [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)) |
| Lizenz: `LICENSE` ist die MIT-Lizenz | gemessen | `LizenzTest` in `make test`; Lizenz und Manifest im Bau von `make build` |
| 11 Konfigurationsschlüssel, Voreinstellungen, Bereiche der Nutzer-Doku | gemessen (Lesen) | Review und Verifikation unabhängig; kein Test hält die Tabelle gegen `ServerConfig` |
| Latest des Kurs-Repos ist `v6.18.1`, gepinnt ist `v6.18.1` | gemessen (ein Abruf) | 2026-11-06, `gh release list`; nicht wiederholt |
| Ausnahmeliste: A-1, A-6 *so gewollt* | Festlegung | `SPEC-012`, [ADR-0009](../../adr/0009-authentifizierungsmodus.md); `TDS_BACKEND_ERROR`, [ADR-0007](../../adr/0007-fehler-und-attention.md) |
| Aufnahme-Methode: mitschneidender Proxy | Festlegung | Setzung des Planners, vom Nutzer bestätigt (Start-Trigger von `slice-qa-vergleichslauf-d-messungen`); eine Veränderung der Pakete durch den Proxy ist nicht gegen eine zweite Methode gemessen |
| Schema des Live-Laufs bleibt im Code des Integrators (`testschema.sql` getrennt gepflegt) | Festlegung | Nutzerentscheidung 2026-11-06 (N5); kein Sensor gegen Auseinanderlaufen |
| `ConnectRetryCount=1` ist Messbedingung | Festlegung | Standard des Clients (Matrix, Setzung des Nutzers), nicht vom Lauf erhoben |
| Live- und Mitschnitt-Lauf nicht in `make gates` und nicht in der CI | Festlegung | Nutzerentscheidung 2026-11-06; EULA-Entscheidung 2026-11-01 (lokal, nie in Gates, E2E, CI) |
| Fehlerabschluss im RPC; Mehrfach-RPC auf der ERROR-und-DONE-Seite | Festlegung | [ADR-0039](../../adr/0039-abschluss-der-antwort-auf-eine-rpc-nachricht-die-der-parser-abweist.md) Option B, Setzung ohne Messbefund; Mehrfach-RPC danach als [ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) |
| `2025-latest` ist Referenzinstanz des TDS-8.0-Pfads | Festlegung | [ADR-0028](../../adr/0028-referenzinstanz-fuer-tds-8-0.md); `2022-latest` nimmt `Encrypt=Strict` nicht an, Ursache nicht geklärt |
| Wächter für [ADR-0010](../../adr/0010-detekt-pre-release.md): Schritt in `releasing.md`, kein Target | Festlegung | Nutzerentscheidung 2026-11-14; wer den Schritt vergisst, bemerkt den Trigger nur zufällig |
| Nicht abgeglichen: TDS 8.0 am Live-Lauf, Pooling, andere Clients, `sp_execute` mit Handle am Mitschnitt, Transaktion | nicht gemessen | Grenzen des Messprotokolls; `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` |

### Ausnahmeliste: Art je Regel und die offene Arbeit

Stand dieser Closure, gezählt aus den beiden `.tsv`-Dateien: **Live-Liste 14 Regeln** (3 *so gewollt*: A-1 für zwei Schritte, A-6; 11 *Lücke*), **Mitschnitt-Liste 40 Regeln** (1 *so gewollt*: A-6; 39 *Lücke*). Jede Regel trägt Art und Verweis in der Spalte `Verweis`. **Keine der Lücken hat einen Slice als Träger der Behebung**: der Verweis zeigt je Nummer auf einen Register-Eintrag, dessen Träger-Vorschlag des Planners nicht angelegt ist. Das ist die Lage zur Entscheidung des Nutzers, nicht eine Schließung.

| Nr | Art | Verweis auf die offene Arbeit | Stand der Arbeit |
|---|---|---|---|
| A-1, A-6 | so gewollt | `SPEC-012` und [ADR-0009](../../adr/0009-authentifizierungsmodus.md); `TDS_BACKEND_ERROR` und [ADR-0007](../../adr/0007-fehler-und-attention.md) | keine offene Arbeit |
| A-2 (mehrteilige Antwort, Ausgabewert des Backends, A-16 Rest) | Lücke | `BEO-TDS/fa-10-mehrteilige-antwort-ohne-backend-traeger` | offen, kein Träger-Slice, Vorschlag nicht angelegt |
| A-3 (Text „Unknown object.“; Class ist angeglichen) | Lücke | `BEO-TDS/unbekannte-tabelle-class-20-statt-16-am-server` | Class 16 gebaut (`slice-unbekannte-tabelle-class-16-angleichen`); der feste Text bleibt, kein Träger |
| A-4, A-5, A-15, A-17 (Anweisungsformen: `SELECT 2`, `OR`, `WAITFOR`, zwei Anweisungen) | Lücke | `BEO-TDS/anweisungsformen-des-simulations-backends-enger-als-der-server`, `SPEC-036` | offen, hängt an der Lesart-Entscheidung `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`; kein Träger-Slice |
| A-7, A-13 (Antwort auf Cancel: Text, Zwei-Nachrichten-Form) | Lücke | `BEO-TDS/cancel-antwort-text-weicht-vom-echten-server-ab`, [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) | Messung am Draht geliefert (`slice-qa-vergleichslauf-d-messungen`); der Bau hat keinen Träger |
| A-8 (`SchemaOnly`, `KeyInfo`, `FMTONLY`) | Lücke | `BEO-TDS/schemaonly-keyinfo-fmtonly-ohne-traeger` | offen, kein Träger |
| A-9 (Zeilenzahl von `UPDATE` und `DELETE`) | Lücke | `BEO-TDS/callback-zeilenzahl-ist-fixture-konstante` | offen, kein Träger |
| A-11 (TYPE_INFO `decimal(10,2)`, Länge 17 statt 9) | Lücke | `BEO-TDS/mitschnitt-luecken-ohne-traeger` | ohne Träger; `slice-codec-datentypen-erweiterung` bearbeitete sie nicht; Ursache nicht untersucht |
| A-14 (Spaltenbeschreibung `laufend`, `kaputt`) | Lücke | `SPEC-045` (Pflege von `testschema.sql` und Tabellen gemeinsam) | Pflegerückstand, kein Register-Eintrag eigens; hängt an `BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht` |
| A-18 (SESSIONSTATE, nur 2022 bei `ConnectRetryCount=1`) | Lücke | `BEO-TDS/mitschnitt-luecken-ohne-traeger` | Nutzerentscheid 2026-11-06: kein Bau, dokumentierte Lücke; Re-Evaluierungs-Trigger: ein Treiber oder Pool zeigt Bedarf |

Erledigt und aus den Listen entfallen: A-10 (`curCmd`), A-12 (NBCROW, `slice-antwortform-nbcrow`), A-19 (Flag-Feld des Handles), A-20 (`slice-antwort-tokens-zielbild-luecken-bauen`).

## Steering-Loop-Einträge

Regeln dieser Sektion: Baseline-Regelwerk `v6.18.1` · `regelwerk/modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 (hier stehen **nur** Beobachtungen, die im
Register 3× erreicht haben; jeder Eintrag nennt seine `BEO-<KUERZEL>/<slug>`) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

**Lese-Schritt des Registers.** `docs/plan/planning/observations/BEO-TDS/` führt 179 Verzeichnisse; 45 haben mindestens 3 Dateien in `evidence/` (der Zähler ist die Zahl der Dateien; manche `Stand`-Zeilen zählen anders). Davon tragen 32 einen Ausgang (24 `verkörpert`, je 1 `gebaut`, `gestrichen`, `geliefert`, `geplant`, 2 `Träger entschieden`, 2 mit `teilweise gemessen` oder `teils gemessen`); 13 stehen `offen`. **Diese Closure verkörpert nichts und ändert weder Regel noch Gate noch Command noch Sensor:** Regeln, Commands und Sensoren zu schreiben ist Entscheidung des Nutzers. Es entsteht darum kein Eintrag mit `liegt in`; der Anker-Paarung fehlt damit kein Zielort. Keine Closure-Aussage über die Wirkung der verkörperten Regeln ([AGENTS.md](../../../../AGENTS.md) §3.8).

*Schon verkörpert, mit Beitrag aus Slices dieser Welle (Regel bleibt, keine Lockerung; die Anker stammen von früheren Closures):*

| Beobachtung | Dateien | davon aus dieser Welle | Zielort und Anker (unverändert) |
|---|---|---|---|
| `BEO-TDS/aussagen-ueber-messung-hinaus` | 96 | 6 (`referenzabgleich-live`, `-mitschnitt`, `antwort-tokens-an-messung-angleichen`, `release-artefakt-lizenz`, `release-doku-prozess`, `referenzabgleich-adr-0028-nachzug`) | `AGENTS.md` §3.8 und `.harness/skills/reviewer.md` |
| `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` | 51 | 4 (`live`, `mitschnitt`, `antwort-tokens`, `adr-0028-nachzug`) | `.harness/skills/reviewer.md`, `.claude/commands/implement-slice.md` Schritt 20c |
| `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht` | 28 | 2 (`antwort-tokens`, `adr-0028-nachzug`) | `.harness/skills/reviewer.md`; Sensor-Frage beim Nutzer |
| `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` | 28 | 1 (`live`) | `.harness/skills/reviewer.md` |
| `BEO-TDS/nachbesserung-ohne-zweiten-review` | 17 | 1 (`antwort-tokens`) | Regel nach Nutzerentscheid 2026-11-07 |
| `BEO-TDS/test-stage-cache-verdeckt-wiederholung` | 12 | 2 (`mitschnitt`, `release-artefakt-lizenz`) | `make clean-build`, `harness/README.md` §Sensors |
| `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`, `kommentar-nennt-abwesenden-oder-unwirklichen-zustand` | 12, 12 | je 1 (`release-doku-prozess`) | `implement-slice` Schritt 24, `.harness/skills/reviewer.md` |
| `BEO-TDS/dod-wortlaut-ueber-der-bauart` | 10 | 1 (`mitschnitt`) | `.claude/commands/plan-welle.md` Schritt 6b |
| `BEO-TDS/zahl-an-mehreren-orten-ohne-sensor` | 10 | 1 (`release-entscheidungen`) | `.harness/skills/reviewer.md` |
| `BEO-TDS/haken-vor-verifikation-gesetzt`, `strukturtest-ohne-variation-der-eingabe`, `dod-satz-verlangt-ablage-die-der-slice-ersetzt`, `spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar`, `redaktion-sucht-nur-den-bekannten-wert` | 7, 8, 7, 6, 3 | je 1 oder 2 | je ihr Zielort mit Anker einer früheren Welle |

*Über der Schwelle, ohne Ausgang oder mit offenem Rest — Entscheidung beim Nutzer (Regel, Sensor, Träger-Slice oder Streichung), Stand `offen` bleibt, die Zähler sind nicht geändert:*

- `BEO-TDS/skript-pruefung-ohne-rot-gegenprobe` (3): `slice-betrieb-log-adapter-testserver`, `slice-release-job-im-workflow-bauen`, `slice-qa-performance-rueckstau`; im Slice behoben, nichts verkörpert.
- `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` (16; 1 aus `release-artefakt-lizenz`); `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` (21; 1 aus `adr-0028-nachzug`); `BEO-TDS/live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht` (3; `live`, `mitschnitt`, `qa-vergleichslauf-d-messungen-bulk-merge`; Gate-Aufnahme abgelehnt, ein Sensor nur für die Kopplungen denkbar, nicht vorgeschlagen).
- `BEO-TDS/zusage-ohne-sensor-in-der-ci-kette` (9), `katalog-elemente-grenzen-der-angleichung` (5), `dod-haengt-an-offenem-slice-ohne-rueckfuehrung` (4), `festlegung-gilt-nur-fuer-das-clientprofil` (4), `geheimnis-kopien-im-speicher-nicht-gemessen` (4), `spec-einzelfaelle-ohne-lastenheft-deckung` (4), `eingangsliste-je-stage-von-hand` (3), `parametertyp-des-clients-passt-nicht-zur-spalte` (3), `schranken-test-mit-ungeprueftem-zaehler` (3): kein Beitrag aus dieser Welle.
- `BEO-TDS/messung-am-echten-sql-server-fehlt` (37; 3 aus dieser Welle: `live`, `mitschnitt`, `release-artefakt-lizenz`), `rueckstau-im-produktivbetrieb-nicht-gemessen` (12; 1 aus `release-artefakt-lizenz`), `mitschnitt-luecken-ohne-traeger` (4), `spec-setzung-widerspricht-messung-am-echten-server` (3), `anmeldepfad-sicherheit-nicht-gebaut` (7, `geplant`), `katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab` (5, `geliefert`), `zielbild-luecken-der-antwort-tokens` (4, `gebaut`): Stand je in der `state.md`, nachgeführt wo diese Welle beitrug.

**Spec-Lücken, benannt und offen (kein Zielort, keine `LH-*`-Zeile):** (1) `SPEC-050` führt den Referenzabgleich als „Lauf des E2E-Durchgangs, noch einzurichten“; Live- und Mitschnitt-Lauf sind gewollt nicht in `make e2e` und nicht in `make gates` (Nutzerentscheidung 2026-11-06); die Zeile sagt das nicht. (2) Das Lastenheft nennt den Referenzabgleich „ergänzend“ und „vorläufig“; ob [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität) ohne ihn als belegt gilt, ist die offene Frage der Welle-Datei §5 (Frage an den Nutzer). Beide ändert diese Closure nicht.

## Beobachtungs-Register (Zeiger)

Regeln dieser Sektion: Baseline-Regelwerk `v6.18.1` · `regelwerk/modul-06-roadmap.md`
§Das Beobachtungs-Register — der Zähler wird **nicht** hier gepflegt; diese
Sektion ist ein Zeiger und trägt keine Daten.

Der Zähler steht in [`../observations/`](../observations/).
Was in dieser Welle **3×** erreicht hat, steht oben unter
*Steering-Loop-Einträge*.

Ausgänge der in der Welle-Datei genannten Beobachtungen, nachgeführt in ihrer `state.md`:

- `BEO-TDS/messung-am-echten-sql-server-fehlt`: Stand bleibt *teilweise gemessen*; diese Welle lieferte die Antwortseite im RPC und den Abgleich am Client und am Draht (Live-Lauf, Mitschnitt, W-1 bis W-9, M-1 bis M-7); der Rest steht in der `state.md`.
- `BEO-TDS/vergleichslauf-d-ungeklaert` (2 Dateien, unter der Schwelle): Stand bleibt `offen`; Laufzeiten sind gemessen (siehe Verifikation), die CI bleibt für diesen Lauf ausgeschlossen (EULA, Entscheidung 2026-11-01 und 2026-11-06), die Ursache des Fehlers von `2022-latest` bei `Encrypt=Strict` ist nicht geklärt.
- `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`: Stand `offen`; die Lage zu TDS 8.0 steht in `SPEC-045` und `SPEC-050`, `RESETCONNECTION` ist am Draht gemessen (W-7).

## Folge-Slices

Regeln dieser Sektion: Baseline-Regelwerk `v6.18.1` · `regelwerk/modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — **derivativ**: Diese Liste zeigt nur,
das Original ist die Slice-Datei. Jeder genannte Folge-Slice muss als Datei im
Planning-Lifecycle existieren.

- `slice-release-im-tag-lauf-messen` (`open/`; Träger der Ungemessenen der [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md)-Kette; nicht ohne den Nutzer startbar: Version, Push und Tag-Schutz, Lizenzbeleg gelesen, Verantwortlicher) und danach `slice-release-nachtraeglich-bestandstags` (`open/`).
- `slice-mehrfach-rpc-messluecken` (`open/`; Messung am echten SQL Server zu [ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md); nicht ohne den Nutzer startbar) und `slice-mssql-jdbc-gegen-testserver` (`open/`; mssql-jdbc gegen NBCROW und Mehrfach-RPC, Nicht-Gate-Target neben `make e2e`): berühren den Referenzabgleich (A-11 bis A-12, NBCROW, Mehrfach-RPC), tragen aber keine Regel der Ausnahmeliste.
- **Nicht angelegt, kein Slice als Datei:** die Träger-Vorschläge zu A-2, A-4/A-5/A-15/A-17, A-7/A-13, A-8, A-9, A-11, A-14 (Tabelle oben); sie stehen in den Register-Einträgen und warten auf die Entscheidung des Nutzers.

## Verifikation

Regeln dieser Sektion: Baseline-Regelwerk `v6.18.1` · `regelwerk/modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 1 — keine Behauptung ohne nachprüfbaren
Anker (Hash, Lauf, Zahl).

Geprüft am Stand `0ab6be71` (`main`) im Worktree dieser Closure, 2026-11-17.

1. **Alle Slices in `done/`:** erfüllt. Die fünf Slices aus §4 der Welle-Datei und die zwei später angelegten (`slice-referenzabgleich-adr-0028-nachzug`, `slice-release-entscheidungen`) liegen als Dateien in `done/`; `grep` auf `^**Welle:** welle-referenzabgleich-release` findet sieben Dateien, alle in `done/`, keine in `open/`, `next/`, `in-progress/`. (Drei Dateien nennen die Kennung in einem `Welle:`-Feld „ohne Welle“ im Fließtext, siehe Archivierung.)
2. **`make gates`, Laufzeittest, Lizenzprüfung, Live- und Mitschnitt-Lauf:**
   - *Gates:* `make gates` Exit 0 am Stand dieser Closure (11 s; die Gruppen `werkzeug`, `tests` und `stempel` übersprungen bei passendem Stempel, `docs-check` 1610 Dateien 0 Befunde, `gate-zuordnung` 16 Gates und 33 Fälle, `stempel-eingaenge`, `flaeche`). Der Lauf belegt für `tests` den Stempel, keine frische Gradle-Arbeit; `make gates FORCE=1` ist in dieser Closure **nicht gefahren**: ein anderer Lauf von `make gates FORCE=1` und die Messung eines Slice belegten den Host, und seit dem Stempel änderte die Closure nur Dokumente (Diff ohne `src/`, `testserver/`, Gate, Konfiguration). Frischbeleg der Tests bleibt `make clean-build` der Verifikationen der Slices (zuletzt `853239d5`, 2026-11-17).
   - *Lizenzprüfung:* in `make gates` über `make test` (`LizenzTest`) und `make build` (Lizenz und Manifest im Bau der Stage `build-jar`), beide stehen in `GATE_CHECKS`.
   - *Laufzeittest:* **nicht in `make gates`**. `make laufzeittest` verließ die Gate-Kette mit [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) und ist Stufe 2 von `make e2e` (kein Gate, `harness/README.md` §Sensors). Das Kriterium in der Fassung der Welle-Datei („einschließlich des Laufzeittests“) ist durch diese `Accepted`-ADR überholt; die Closure trifft keine Entscheidung darüber, ob der Laufzeittest zurückkehrt.
   - *Live-Lauf und Mitschnitt-Lauf grün gegen ihre Ausnahmeliste, oder Begründung der Nicht-Aufnahme in `make gates` mit gemessener Laufzeit:* die Alternative „Begründung“ gilt. Beide Läufe sind nicht in `make gates` und nicht in der CI: Nutzerentscheidung 2026-11-06 (Gate-Aufnahme: nein) und die EULA-Entscheidung vom 2026-11-01 (das Developer-Image: lokal, nie in Gates, E2E oder CI). Gemessene Laufzeit (Einzelmessungen, Last unterschiedlich): Live-Lauf 35 bis 46 s je Teillauf (2026-11-06), `live-2022` 80 s bei Last 35 bis 40 (Closure von `slice-referenzabgleich-live`); Mitschnitt-Lauf 14 bis 17 s je Teillauf (2026-11-06), 22 s (`mitschnitt-2022`, 2026-11-11), 36 s (beide Instanzen, 2026-11-17). Die Selbsttests des Vergleichs und der Redaktion laufen dagegen in `make referenztest-dotnet` und `make skript-selbsttest` (beide Gates). **Grün-Beleg auf diesem Stand:** der Mitschnitt-Lauf ist am 2026-11-17 von einer Verifikation grün gefahren (Exit 0, `853239d5`); der Live-Lauf ist zuletzt am 2026-11-06 grün gefahren und **in dieser Closure nicht wiederholt**: er braucht die Referenzinstanz (EULA, Nutzer), und der Host misst für einen anderen Slice. Seit dem 2026-11-06 änderten sich Server und Listen (A-3 Class 16, A-10, A-12, A-19, A-20 entfallen, Mehrfach-RPC); das ist eine **Lücke im Beleg**, keine Aussage „rot“.
3. **Das Mehr der Welle:** (a) Läufe gegen einen Container statt gegen einen Testprozess: erfüllt, Dockerfile-Stage `kompatibilitaetsserver` (nicht die Stage `testserver`, siehe „Was ging anders“); (b) Schritte der Nutzer-Doku zur Einbettung (`TdsServer.start`) an der Bibliothek nachvollzogen: `EmbeddingTest` im Modul `integrator` (nur öffentliche Fläche, ohne Jackson) in `make test`, Doku `docs/user/einbettung-und-betrieb.md`; ein Start des Produkts im Container gibt es nicht ([ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)).
4. **Zwei Spalten, Arten der Ausnahmeliste:** oben. Die Art steht in jeder Regel; „Lücke mit Verweis auf die offene Arbeit“ ist im Wortlaut erfüllt (jede Lücke verweist auf einen Register-Eintrag oder eine Spezifikation), ein Slice als Träger der Behebung fehlt bei allen vierzehn Nummern (A-2 bis A-5, A-7 bis A-9, A-11, A-13 bis A-18) (Entscheidung des Nutzers).
5. **Ausgänge der Beobachtungen** `messung-am-echten-sql-server-fehlt` und `vergleichslauf-d-ungeklaert`: nachgeführt (Zeiger oben und `state.md`); beide bleiben ohne endgültigen Ausgang.
6. **Nutzerentscheidungen** (Aufnahme-Methode, Ablage, Versionsschema, Form des Wächters): Aufnahme-Methode Proxy bestätigt (`slice-qa-vergleichslauf-d-messungen` §Start), Ablage im Repo (34 Dateien, höchstens 256 KiB je Datei, `slice-referenzabgleich-mitschnitt`), Versionsschema und Registry in [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md) und [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md) (`slice-release-entscheidungen` §Schon entschieden), Form des Wächters am 2026-11-14 (Schritt in `releasing.md`, kein Target). In den Slice-Notizen festgehalten. Offen bleiben nach `releasing.md`: Lizenzhinweise zu den Bibliotheken in `lib/` (Rechtsfrage) und Dateirechte der Benutzerliste; beides ist nicht Gegenstand dieser Welle.
7. **Trigger-Audit (Schritt 2), je Klasse belegt:**
   - *Carveouts:* `docs/plan/carveouts/` führt keine offene Datei; [CO-001](../../carveouts/done/CO-001-nuget-audit-aus.md) bis [CO-004](../../carveouts/done/CO-004-cancel-szenario-spec-040-fehlt.md) liegen in `done/`. 0 offen.
   - *Bootstrap-aware Gates:* kein Gate der Sensors-Tabelle trägt Bootstrap-Bindung (die Vorlagenzeile `<make-target> … bootstrap-aware` ist ein Platzhalter; `grep` nach `bootstrap-aware` in `tools/mk`, `Makefile`, `repo.mk` ohne Treffer). 0 offen.
   - *Entscheidungen mit Re-Evaluierungs-Trigger, die diese Welle berührt:* [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) (Trigger „SQL-Server-Image mit TDS 8.0“: eingetreten und beantwortet durch [ADR-0028](../../adr/0028-referenzinstanz-fuer-tds-8-0.md); neue SqlClient-Hauptversion: nicht geprüft, kein Wächter, Pin 7.1.1); [ADR-0010](../../adr/0010-detekt-pre-release.md) (Stabil-Release von detekt 2.x: Wächter ist ein Schritt ohne Auslöser, einmal am 2026-11-06 belegt, in dieser Closure nicht wiederholt, **ein Trigger ohne Auslöser**, so entschieden am 2026-11-14); [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) (Veröffentlichung in einem Artefakt-Repository: eingetreten, behandelt mit [ADR-0047](../../adr/0047-veroeffentlichung-ueber-github-packages.md), [ADR-0049](../../adr/0049-cloudsmith-als-zweites-veroeffentlichungsziel.md), [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md)); [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) (Trigger: warmer `make e2e` unter einem Zehntel der Stage `test` oder Regression, die nur der E2E-Workflow fand: nicht gemessen und nicht beobachtet, `e2e.yml` hat noch keinen Lauf auf `main`); [ADR-0050](../../adr/0050-github-release-im-tag-lauf.md) (Mess-Pflichten: Träger `slice-release-im-tag-lauf-messen` in `open/`); [ADR-0028](../../adr/0028-referenzinstanz-fuer-tds-8-0.md) (neuer Digest von `2025-latest` oder `2022-latest`: nicht geprüft, die Digests sind gepinnt). Keine Entscheidung dieser Welle hat einen eingetretenen, unbeantworteten Trigger. Offener Rest des Audits: [ADR-0007](../../adr/0007-fehler-und-attention.md) und [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) tragen eine Zeile mit der alten Lesart zu TDS 8.0, die nicht in der `Supersedes`-Liste von [ADR-0028](../../adr/0028-referenzinstanz-fuer-tds-8-0.md) steht; behebbar nur über eine Folge-ADR (Entscheidung des Nutzers).

## Drei Paarungen (Schritt 3, zum Schluss geprüft)

- *Anker:* kein Eintrag dieser Notiz trägt `liegt in`; es gibt nichts zu paaren. Die verkörperten Regeln der Tabelle oben tragen ihren Anker aus früheren Closures.
- *Folge-Slice:* `slice-release-im-tag-lauf-messen`, `slice-release-nachtraeglich-bestandstags`, `slice-mehrfach-rpc-messluecken`, `slice-mssql-jdbc-gegen-testserver` bestehen als Dateien in `open/`.
- *Register:* jede in dieser Notiz genannte Kennung `BEO-TDS/<slug>` besteht als Verzeichnis mit nicht leerem `evidence/` (Lauf in dieser Closure über alle genannten Kennungen).

## Archivierung und Roadmap

Regeln dieser Sektion: `close-welle` Schritte 4 und 6.

**Archivierung (Schritt 4): nicht ausgeführt, Freigabe des Koordinators steht aus.** `make archive-welle WELLE=welle-referenzabgleich-release` ist in diesem Arbeitsbaum nicht gefahren; der Träger liegt im gitignorierten Bereich des Haupt-Arbeitsbaums. Es gibt bereits sechs `done/*/archiv.zip`, die Sperre `[untergrenze]` besteht nicht. Die Vorschau (`ai-harness-init archive-welle --vorschau welle-referenzabgleich-release`, aus dem Haupt-Arbeitsbaum gegen diesen Arbeitsbaum gefahren, nichts geschrieben, Exit 3) nennt nach der Ergebnisnotiz und dem Move des Plans: 12 Mitglieder (Feld `Welle:`), 6 wellenlos seit der letzten Closure, 2 „bleibt liegen (nach der Grenze)“, 84 fremd, 43 Review-Reports ohne Stub, 137 Dateien mit Verweisen auf Bewegtes. Eine Sperre: `[haenger]`, 55 Verweise aus 31 Dateien (49 im Beobachtungs-Register, vier in der Roadmap, je einer in `slice-mssql-jdbc-gegen-testserver` und in einem Review) auf 24 der 43 Review-Reports. **Die Zahl der Mitglieder (12) ist größer als die der Slices mit dem Feld `Welle: welle-referenzabgleich-release` (7):** drei Dateien in `open/` und `in-progress/` (`slice-release-im-tag-lauf-messen`, `slice-release-nachtraeglich-bestandstags`, `slice-bulk-fuer-eigene-backends`) nennen die Kennung der Welle in ihrer Zeile `Welle: ohne Welle — …` im Fließtext; ob das Werkzeug sie einsammelt, ist vor einem schreibenden Lauf zu klären. Nichts erzwungen, nichts von Hand bewegt. Bis zur Freigabe gilt die Welle als inhaltlich geschlossen; die Auditierbarkeit nach Schritt 4 steht aus.

**Gates dieser Closure (Pflicht für Doku und Spec nach `harness/README.md` §Minimal agent workflow):** `make docs-check` Exit 0 (1610 Dateien, 0 Befunde, auch nach dem Move und der Link-Reconciliation), `make flaeche` Exit 0, `make kopplung-zahlen` Exit 0, `make gates` Exit 0 (11 s, Stempel). Geändert wurden nur Dokumente und Register; `src/` und `testserver/` blieben unberührt.

**Roadmap (Schritt 6):** die Welle verließ *Offene Wellen* (samt dem Block „In Arbeit (Welle welle-referenzabgleich-release)“), *Abgeschlossene Wellen* trägt ihre Zeile mit Zeiger auf diese Notiz. Meilenstein: keiner (die Welle hat „kein Meilenstein-Bezug“). Keine Umplanung, daher kein Eintrag in *Historische Trigger-Verschiebungen*. Der Welle-Plan liegt per reinem Move in `done/`; die Verweise darauf sind nachgezogen.
