# Verifikation: slice-referenzabgleich-mitschnitt — 2026-11-06

**Rolle:** Verifier (Modul 11), "Bauen wir es richtig?" gegen DoD (§2 des Slice), [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0028](../plan/adr/0028-referenzinstanz-fuer-tds-8-0.md) (`Proposed`), SPEC-045, SPEC-050 und [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität). Kein Review, keine Reparatur, kein Haken gesetzt, keine Entscheidung des Nutzers gesetzt. Maßstab des Nutzers (2026-11-06): die Spezifikation ist Zielbild, der echte SQL Server die Referenz, eine Abweichung des Servers ist Schuld.

**Gegenstand:** Zweig `worktree-agent-ac2ab6a0ad4085453`, HEAD `c712427a` (Basis `6b28cd69`; Implementierung `f409ce20`, `a0d7d82c`, `01b5c7eb`; Review `40874003`, Nachbesserung `c712427a`). Review davor: [2026-11-06-01b5c7eb-slice-referenzabgleich-mitschnitt.md](2026-11-06-01b5c7eb-slice-referenzabgleich-mitschnitt.md). Die EULA des gepinnten Developer-Images ist vom Nutzer pt9912 bestätigt (2026-11-01, 2026-11-06); die Läufe unten sind lokal, kein Gate.

## Eigene Sensor-Läufe (am Stand `c712427a`, Arbeitsbaum sauber)

| Lauf | Ergebnis |
|---|---|
| `make referenztest-dotnet` | Exit 0, aber `CACHED` (Stage `test` aus dem Cache): kein Frischbeleg. Daher `docker build --no-cache-filter test --target test referenztest-dotnet`: `Passed! Failed: 0, Passed: 28, Skipped: 0, Total: 28` (frisch) |
| `make vergleichslauf-d LAUF=mitschnitt` | Exit 0, "gruen", 35 s; zwei Teilläufe, je "Server mit Simulations-Backend (Klartext) meldet 'gestartet, Port 1433'" (frisch gestartet), "live: gruen (324 Schritte)". Nachgezählt aus der Ausgabe: 2022 69, 2025 64 Zeilen "Ausnahme (…)", zusammen 515 "gleich" + 133 Ausnahmen = 648 = 2 x 324 |
| `make vergleichslauf-d LAUF=aufnahme-2025` (der Reviewer fuhr nur 2022) | Exit 0, "gruen", 37 s; 17 Mitschnitte, Test grün, "das Kennwort steht in keiner Datei der Ablage". Die Neuaufnahme ist bis auf **eine** Datei byte-gleich zur abgelegten (`2025/cancel-streaming.mitschnitt`, eine Zeile `antwort`, Länge der gelesenen Zeilen, zeitabhängig); der Servername bleibt redigiert. Abgelegte Aufnahmen danach mit `git checkout` wiederhergestellt, `git status` sauber |
| `make docs-check` | 946 Dateien, 0 Befunde, Exit 0 |
| `make gates` | Exit 0. Kein Stempel für `werkzeug` und `tests` an einem Vorfahren, also **voller** Lauf aller Gruppen; Gradle-/Kotlin-Stages `CACHED` (der Diff berührt keine Kotlin-Quelle), die Stage `test` des .NET-Projekts fuhr ich vorher frisch (siehe oben). Cache-Falle G1: Der Lauf belegt die Gates am HEAD, für Kotlin keine frische Gradle-Arbeit |
| Redaktion am Dateiinhalt (siehe unten) | kein Treffer |

Nicht gefahren: `make e2e`, `make matrix-zitate` (der Reviewer meldete einen roten Erstlauf der Kotlin-Stage `test`, Ursache offen, Review F-9; der Diff seit dem Review berührt keine Kotlin-Quelle), `LAUF=aufnahme-2022` (vom Reviewer gefahren), Mutationen am Werkzeug.

## Redaktion (an den Dateien erneut geprüft)

- 34 Dateien (17 je Instanz), 72 KB. Alle `anfrage`/`antwort`-Hexfolgen zurückgewandelt, alle UTF-16LE-Zeichenketten ab 5 Zeichen gelistet (Zählung): nur SQL-Texte der Szenarien, Spaltennamen (`zeile`, `Score`, `Gross`, `Preis`), Parameterdeklarationen, die Fehlertexte von ERROR 208 und 8134 und `xxxxxxxxxxxx` (12 Zeichen, Servername des ERROR-Tokens, in 8 Dateien). Kein Treffer für `pass|pwd|secret|token|bearer|localhost|user|alice|referenz|mssql|host`, kein `Tdswire-Ref` (Kennwort entsteht je Lauf zufällig, `tools/vergleichslauf-d.sh` Z. 238). Klartext-Grep über die Dateien nach `kennwort|passwort|password|pwd|secret|token|bearer|alice|localhost|container`: nur das Wort "Container" im Kopf (`# methode: Proxy im Container des Clients …`), kein Wert.
- Container-ID: in der Ablage nicht mehr vorhanden (F-5 behoben); die Neuaufnahme 2025 erzeugt dieselben Bytes, die Redaktion greift also stabil.
- Grenze (eigene Feststellung, kein Befund): die Anmeldung steht bauartbedingt nicht in der Ablage (`Encrypt=false`, Vorspann und LOGIN7 verschlüsselt, Pakettypen der Ablage nur Batch/RPC/ATTENTION).

## DoD (§2)

Die Haken im Slice setze ich nicht; der Stand der Haken ist unten genannt.

| # | Punkt | Befund |
|---|---|---|
| 1 | **Aufnahme** | Weitgehend erfüllt, mit zwei Wortlaut-Befunden (P-1, P-2). Proxy (`Relais.cs`, Klasse `Aufnahme`), Redaktion (`Redaktion.cs`; Kennwort, Prüfwert, Servername), 17 Szenarien je Instanz (Batch mit Zeilen/NULL, leer, alle Typen; RPC mit Parameter und `InputOutput`; `Prepare()`; mehrteilig; Fehler vor/nach Zeilen; statement- und batch-abbrechend; fünf Cancel/Frist-Szenarien (`cancel-streaming`, `-leerlauf`, `-wartend`, `-kurz-vor-timeout`, `-kurz-nach-timeout`) plus `timeout-wartend`), Kopf mit Instanz, Image-Digest, Client-Version, Schema-Hash, Methode. Der Test zur Redaktion (`Der_Mitschnitt_enthaelt_weder_Kennwort_noch_Pruefwert_obwohl_der_Rohstrom_beides_traegt`) speist Eingaben ein, die beides enthalten, und belegt die Findbarkeit im Rohstrom (so vom Reviewer gelesen; die 28 Tests sind frisch grün). Das Szenario **Anmeldung fehlt** (P-1) |
| 2 | **Abspielen und Vergleich** | Erfüllt: frisch gestarteter Server je Teillauf (Dockerfile-Stage `kompatibilitaetsserver`, Klartext), 324 Felder je Teillauf (27 Austausche x 12 Felder: Nachrichtenzahl, ATTENTION gesendet/quittiert, Tokenfolge, COLMETADATA mit Typ, Länge, Precision, Scale, Nullbarkeit, Zeilenwerte und -zahl, DONE-Familie, ERROR, INFO, ENVCHANGE, RETURNSTATUS/RETURNVALUE, Abspielen); Servername, Handle und ENVCHANGE-Werte nur als vorhanden/leer. Der Lauf ist rot bei kleinerer **und** größerer Abweichung als die Liste (im Kopf der TSV: Ergebnis der Referenz und des Servers wortgleich); das sah ich nicht per Mutation, sondern nur als Zusage im Kopf (nicht gefahren) |
| 3 | **Ausnahmeliste und Messaussage** | Erfüllt mit Wortlaut-Befund (P-3). Liste: 74 Regeln nachgezählt (`mitschnitt-ausnahmen.tsv`: 77 Zeilen minus 3 Kopfzeilen; 10 mit `@2022`, 5 mit `@2025`, 59 für beide), davon 73 *Luecke* und 1 *so gewollt* (A-6). Messaussagen 1 bis 6 stehen im Messprotokoll mit Belegzeilen; Tokenreihenfolge, DONE-Bits, RETURNSTATUS (immer 0, vor RETURNVALUE und DONEPROC), Ordinal (ein Fall je Instanz), ATTENTION; Abweichungen von der Spec sind als offene Konflikte geführt, nicht entschieden |
| N1 | `@spec SPEC-<NNN>` im KDoc, Tests in `make test` | **Nicht im Wortlaut erfüllbar** (P-2): die 22 C#-Tests tragen `// @spec SPEC-045` als Kommentar, `make spec-abdeckung` liest nur Kotlin-KDoc, `SPEC-045` bleibt dort ohne Nachweis; die Tests laufen in `make referenztest-dotnet` (Klasse `tests` in `make gates`), nicht in `make test` |
| N2 | Jede Aussage an einem Test oder als Festlegung; Zuordnung in der Closure-Notiz | Offen, wie im Slice (§7 "Wird bei der Closure gefüllt") |
| N3 | `make gates` grün | Am HEAD belegt (Exit 0, siehe oben). **Der Haken `[x]` steht seit `01b5c7eb`**, also vor Review und Nachbesserung `c712427a`; er trägt jetzt, war aber ein Vorab-Haken (V-2) |
| N4 | Review, Doku, Closure-Notiz, Register, Risiko-Ausgänge, Paarungen | Review-Report liegt vor (Haken im Slice `[ ]`, richtig: er ist Sache des Planners); Closure-Notiz, Risiko-Ausgänge (alle "offen bis zur Closure") und Paarungen offen, `[ ]` zu Recht; Register fortgeschrieben (`mitschnitt-luecken-ohne-traeger`, zwei `state.md`, ein `evidence`) |

## Entscheidungs-Konformität

| Entscheidung | Befund |
|---|---|
| [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md) (.NET-Referenztests, Vergleichslauf D) | konform: Werkzeug im .NET-Gerüst (Eigencode im Test-Stage, keine neue Abhängigkeit, kein NuGet-Paket, `--network=none` in `test` bleibt), Referenzinstanz nur lokal; Grenze "eine Instanz, TDS 7.4 unverschlüsselt" im Messprotokoll |
| [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md) (E2E nicht in den Gates) | konform: `tools/mk/vergleichslauf-d.mk` ergänzt nur `LAUF=aufnahme\|mitschnitt\|…`; `GATE_CHECKS` und `tools/stempel/*.eingaenge` nicht geändert (`git diff --name-only` zeigt weder `Makefile` noch `stempel` noch `config/`); Aufnahme ersetzt die Ablage im Arbeitsbaum und committet nichts |
| [ADR-0028](../plan/adr/0028-referenzinstanz-fuer-tds-8-0.md) (`Proposed`) | vom Diff nicht berührt; TDS 8.0 als nicht aufgenommen deklariert |
| SPEC-045 | konform bis auf P-3 (eigene Liste statt "dieselbe"); Schema-Stand in der Aufnahme als Hash im Kopf; Art *so gewollt*/*Lücke* geführt |
| SPEC-050 | Gate-Klasse unverändert (kein Gate hinzu, keine Schwelle gesenkt) |
| [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität) | Wire-Messung gegen 2022 und 2025 für 17 Szenarien; die Aussage "gleich wie die Referenz" gilt nur dafür (so im Messprotokoll geführt) |
| Hard Rules | §3.2: kein `@Suppress`/`SuppressMessage` im Diff (grep); §3.5/§3.6: keine Accepted-ADR geändert (`git diff --name-only` ohne `docs/plan/adr/`), kein Gate gelockert; §3.8: die Aussagen des Messprotokolls tragen höchstens so weit wie ihr Beleg (stichprobenartig: ATTENTION, SESSIONSTATE, Ordinal) |

## Messaussage ATTENTION / SESSIONSTATE / A-18

- **A-18 `ConnectRetryCount=1`**: `MitschnittLaufTests.cs` Z. 42 setzt `ConnectRetryCount = 1` für Aufnahme und Abspielen; das Messprotokoll nennt es "Standard des Clients" (Methode, Grenzen, Ergebnis 5). Der Standardwert 1 ist hier **nicht** vom Lauf erhoben, sondern aus der Matrix ("Standardeinstellung `ConnectRetryCount=1`") übernommen; für die Aussage genügt, dass die Einstellung gesetzt und genannt ist.
- **Belegt gegen die Git-Historie** (meine Nachprüfung): in `a0d7d82c` (`ConnectRetryCount=0`) ist die Antwort auf `cancel-wartend` 13 Byte (ein `DONE`) in beiden Instanzen; in `c712427a` auf 2022 29 Byte (SESSIONSTATE 16 Byte + DONE 13), auf 2025 weiter 13 Byte. Das stützt "mit 0 kein SESSIONSTATE, mit 1 auf 2022 ja, auf 2025 nein". Ein Lauf je Einstellung und Instanz, so geführt.
- **Zwei-Nachrichten-Form** (`DONE[ERROR]`, dann `DONE[ATTN]`) und ein ATTENTION bei Frist/`Cancel()` im Abstand von 100 ms: am Draht belegt (vier Aufnahmen); die Aussage ist im Text auf diese Läufe beschränkt, "einzeln" für mehrere ATTENTION ist weg (F-3), "Mehrere ATTENTION je Anfrage sind am Server dieses Repos nicht abgespielt" steht ausdrücklich da.

## Review-Findings F-1 bis F-7 und F-12 am Text

| ID | Stand am Text |
|---|---|
| F-1 | Behoben: `ConnectRetryCount=1` in Aufnahme und Abspielen, `TokenLeser` kennt SESSIONSTATE (0xE4), A-18 je Instanz, Messbedingung in Methode und Grenzen, Gegenmessung mit 0 benannt und in der Historie belegbar |
| F-2 | Behoben: A-13 und A-14 jetzt *Lücke*, A-13 nennt "bedingter Aufschub, kein Ziel", A-14 "Pflegerückstand"; 73 *Lücke*, 1 *so gewollt* (A-6) |
| F-3 | Behoben: "bestätigt … einzeln" ersetzt durch den Beleg (ein ATTENTION je `WAITFOR`-Szenario, A-15, nicht abgespielt für wartend) |
| F-4 | **Teilweise** behoben: Messprotokoll, Matrix Zeile 8/12 und die Zeilen DONE/RETURNSTATUS/RETURNVALUE/Form RPC, Spec Zeilen 171, 484, 503, 1162, 1222 und beide Register-`state.md` sind nachgezogen. Zwei Vermerke "nicht gemessen" stehen noch (V-1) |
| F-5 | Behoben: Servername im ERROR-Token wird beim Ablegen durch `xxxxxxxxxxxx` ersetzt, Einheitstest dazu (+25 Zeilen in `MitschnittEinheitTests.cs`), Container-ID in keiner Ablage; selbst geprüft (siehe Redaktion) |
| F-6 | Behoben: Kopplungs-Kommentar in `tools/vergleichslauf-d.sh` (ENV-Namen, Gradle-Task `kompatibilitaetsServer`, "Versagen ist laut") |
| F-7 | Behoben als Verweis: Register `BEO-TDS/mitschnitt-luecken-ohne-traeger` (A-10, A-11, A-15, A-18); Stand "offen — Bau ohne Träger" (Träger weiter nicht benannt, das ist Sache des Planners) |
| F-12 | Behoben als Benennung: "Offene Konflikte mit dem Zielbild (nicht entschieden)" im Messprotokoll (Ordinal, `curCmd`, DONE-Bits bei statement-abbrechendem Fehler, RETURNSTATUS), A-16 begründet weiter nur mit dem Backend; die Entscheidung liegt beim Planner |

## Zweite Fundstellen "nicht gemessen" (Grep über `spec/`, Matrix, Register, offene/laufende Slices)

- **Spec-Zusagen nicht abgeschwächt**: `git diff 6b28cd69..HEAD -- spec` (Wortdiff) fügt nur Messvermerke hinzu und ersetzt "nicht gemessen" durch den gemessenen Fall mit Grenze; keine Zusage (Form RPC, `curCmd` 0, "Variante ohne `MORE`", "kein `ReturnStatus` ohne Backend") ist umgeschrieben oder gestrichen. Dass diese Zusagen jetzt der Messung widersprechen, steht im Messprotokoll als offen (F-12).
- Stehen geblieben (V-1): (a) `docs/plan/kompatibilitaetsmatrix.md` Z. 275 "Nicht gemessen: ATTENTION im Leerlauf am Referenzclient (…), ein Server mit zwei ATTENTION einer Anfrage am Draht, …": der Satz stimmt für Server und Wege außer `Cancel()` weiter, ist aber für `Cancel()` im Leerlauf am Referenzclient überholt (Zeile 12 sagt gemessen). (b) `spec/spezifikation.md` Z. 1224 "Die Antwort auf einen erfolgreichen RPC hat die Form RPC des Response Sequencers (Setzung des Implementers, nicht gemessen; Tokenfolge, Statusbits und Ordinale sind Handvektoren)": der Satz ist ein Vermerk zur Herkunft der Form, im selben Absatz steht jetzt der gemessene Ordinal-Fall; für die 17 Szenarien ist "nicht gemessen" überholt. [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md) ("Nicht gemessen: ordinale Zählung der RETURNVALUE, DONE-Statusbits") und die Register-Evidenz älterer Slices (`slice-adr-rpc`, `slice-codec-tds-paket`, `slice-sql-batch-dispatch`, …) sind `Accepted` bzw. Zeitdokumente und bleiben (§3.5); `docs/plan/planning/open/slice-handle-freigabe-und-grenze-bauen.md` Z. 50 nennt RETURNSTATUS "(Handvektor)" in einem offenen Slice.

## Plan-Befunde für den Planner (nicht gelöst)

- **P-1 (DoD-Wortlaut Szenarien):** §2 Punkt 1 nennt als Szenario "Anmeldung"; es fehlt bauartbedingt (Anmeldung verschlüsselt/Vorspann, `Encrypt=false` nimmt sie nicht in die Ablage, [ADR-0028](../plan/adr/0028-referenzinstanz-fuer-tds-8-0.md) `Proposed` für TDS 8.0). ENVCHANGE nach der Anmeldung ist deshalb in keinem Szenario enthalten (Messaussage 4). Die Lücke ist im Messprotokoll benannt, im Slice-Text steht der Ausgang noch aus. Entscheidung: DoD-Wortlaut anpassen oder Folge-Slice (TDS 8.0/Anmeldung am Draht).
- **P-2 (DoD-Wortlaut `@spec`):** Der KDoc-Block-Tag ist in C# nicht erfüllbar; `// @spec SPEC-045` ist Kommentar, `make spec-abdeckung` führt SPEC-045 ohne Nachweis. Auch "Tests laufen in `make test`" trifft nicht: sie laufen in `make referenztest-dotnet`. Entscheidung: Werkzeug (`tools/spec-abdeckung.sh` für C#) oder Wortlaut.
- **P-3 (Plan: gemeinsame Ausnahmeliste):** Plan §2 Punkt 3 und §3 verlangen "dieselbe Ausnahmeliste wie im Live-Lauf"/"gemeinsame Liste"; gebaut ist eine zweite maschinenlesbare Datei `mitschnitt-ausnahmen.tsv` neben `live-ausnahmen.tsv` (gleiches Format, gemeinsame Nummern A-2/A-3/A-6 im Messprotokoll, das ist die eine Prosa-Liste). Zwei Dateien können für denselben Befund auseinanderlaufen; ein Sensor dafür fehlt. Entscheidung: Hinnahme oder Zusammenführung.
- **P-4 (Folgeschuld):** Die 73 *Lücke*-Regeln hängen für A-10, A-11, A-15, A-18 an einem Register ohne Träger-Slice (`mitschnitt-luecken-ohne-traeger`), A-13 an `cancel-antwort-text-weicht-vom-echten-server-ab` ohne Bau-Slice; unter dem Maßstab "Abweichung des Servers ist Schuld" fehlt für diese Schuld jeder Slice. Planner.

## Verbleibende Befunde

- **V-1 (LOW):** zwei überholte Vermerke "nicht gemessen" (Matrix Z. 275, Spec Z. 1224, siehe oben). Es sind Vermerke, keine Zusagen; die Spec-Zusage ist nicht abgeschwächt.
- **V-2 (INFO):** Haken `[x] make gates grün` im Slice steht seit `01b5c7eb` vor der Verifikation und vor der Nachbesserung. Jetzt am HEAD gedeckt (Exit 0); die Regel "keine Haken vorab" gilt für die übrigen Punkte (alle `[ ]`, richtig).
- **V-3 (INFO):** `make referenztest-dotnet` zeigt wegen `CACHED` einen früheren Stand; der Frischbeleg ist der `--no-cache-filter`-Lauf (28 Tests).
- **V-4 (INFO):** Messprotokoll, Ergebnis 5: "(A-13) ;" mit Leerzeichen vor dem Semikolon (Tippfehler, ohne Wirkung).
- **V-5 (INFO):** Die Messung ist ein Lauf je Instanz und Szenario, ein Host; der Fehler-/Boundary-Charakter der Vergleichstests (Abweichungen in verschiedenen Feldern, [`BEO-TDS/strukturtest-ohne-variation-der-eingabe`](../plan/planning/observations/BEO-TDS/strukturtest-ohne-variation-der-eingabe/observation.md)) ist nicht per Mutation am Vergleich gefahren.
- Keine DoD-Verletzung unter den beanspruchten Punkten, keine Aussage über dem Beleg gefunden, kein Gate gelockert, keine Suppression, keine Accepted-ADR geändert, nichts in `make gates` aufgenommen.

## Verdikt

**DoD-konform** für die drei Liefer-Punkte, soweit der Wortlaut erfüllbar ist, mit den Plan-Befunden **P-1 bis P-4** für den Planner (zwei davon DoD-Wortlaut, nicht gelöst) und den Restvermerken V-1 bis V-5. `make gates`, `make docs-check`, `make referenztest-dotnet` (28 Tests, frisch), `LAUF=mitschnitt` (2 x 324 Felder, 69/64 Ausnahmen) und `LAUF=aufnahme-2025` selbst grün; Redaktion an den Dateien erneut geprüft, ohne Treffer. Die Review-Findings F-1 bis F-3, F-5 bis F-7, F-12 sind am Text behoben, F-4 bis auf V-1. Für Closure fehlen: Closure-Notiz mit Zuordnung *Test | Festlegung*, Risiko-Ausgänge, Steering-Loop-Lerneintrag, Paarungen und die Haken; ich habe nichts außer diesem Report geändert.
