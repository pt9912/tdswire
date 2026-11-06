# Verifikationsbericht: slice-qa-vergleichslauf-d-messungen (HEAD 073ad171)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: [slice-qa-vergleichslauf-d-messungen](../plan/planning/in-progress/slice-qa-vergleichslauf-d-messungen.md) (Zweig `slice-qa-vergleichslauf-d-messungen`, Basis `cbe4fb06`, HEAD `073ad171`; Commits `d50ffd6c` Umsetzung, `27a3af4e` Wiederholungslauf, `b1a3b658` Review, `7d845031` Merge `main`, `073ad171` Nachbesserung) gegen DoD §2, Plan §3, [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md), `SPEC-045`, `SPEC-050`, [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität) und `AGENTS.md` §3. Eingang: Review-Report [2026-11-06-d50ffd6c-slice-qa-vergleichslauf-d-messungen](2026-11-06-d50ffd6c-slice-qa-vergleichslauf-d-messungen.md) (0 HIGH, 5 MEDIUM, 2 LOW, 5 INFO), Nachbesserung `073ad171`.
* Selbst gefahren (Arbeitsbaum sauber, HEAD `073ad171`, `main` `802bdfbb` ist Vorfahre von HEAD):
  * `docker build --no-cache-filter test --target test referenztest-dotnet`: `Passed! Failed: 0, Passed: 79, Total: 79`.
  * `make vergleichslauf-d LAUF=wire` (beide Instanzen, EULA vom Nutzer erteilt): Exit 0, `gruen`, Laufzeit gesamt 66 s (Last 7 bis 10; das Messprotokoll nennt 70 s). Neue Ablage gegen abgelegte: 44 von 44 Dateien weichen ab, jeweils in der PRELOGIN-Zeile des Clients (Zufallskennungen, siehe V-9), 2 zusätzlich in einer Serverzeile des abgebrochenen Stroms; 46 Zeilen geändert, sonst byte-gleich. Danach `git checkout -- referenztest-dotnet/Mitschnitt/messung`, `git status` leer.
  * `make docs-check`: `1006 Datei(en) geprüft, 0 Befund(e)` (vor Anlage dieses Berichts).
  * `make stempel-eingaenge-pruefung`: ok.
  * `make gates`: Exit 0 (Stempel der Klasse `werkzeug` ohne Beleg an einem Vorfahren, daher voll gelaufen; darin `d-check` 0 Befunde, `gate-zuordnung` 16 Gates, `stempel-eingaenge` ok).
  * Mutationen an Kopien im Scratchpad (Arbeitsbaum unberührt; Maß `docker build --target test`): Feld 78 (SSPI) aus der Redaktionsliste gestrichen, Feld 86 (Kennwortwechsel) gestrichen, Feld 82 (Datenbankdatei) gestrichen: jeweils **1 rot von 79**, und es ist jedes Mal `MessungEinheitTests.Login7_redigiert_SSPI_Kennwortwechsel_und_Datenbankdatei`. Das Review (F-1) sah dieselben Mutationen mit 0 rot; behoben.
* Redaktion an den Dateien (44 Messdateien `Mitschnitt/messung/{2022,2025}/`, eigenes Skript im Scratchpad, an den **abgelegten** Dateien und an der neu aufgenommenen Ablage): siehe DoD-Punkt (3).
* Nicht selbst gefahren: die Mutation am Eigencode `WeitereAbweisen` (der Test `Der_TlsProxy_schliesst_weitere_Verbindungen_sofort_und_zaehlt_sie` ist gelesen, `WeitereVerbindungen` wird dort mit Assert gezählt; die „rot gesehen“-Aussage des Implementers ist unbelegt von mir), `make matrix-zitate`, die sechs Wiederholungsläufe der Pooling-Szenarien (Zahlen aus dem Messprotokoll gelesen, nicht wiederholt), der Nutzerwortlaut vom 2026-11-06 („deine empfehlungen“; ich behandle ihn als vom Auftraggeber berichtet).

## Gesamturteil

**Die Messdaten, die Redaktion und die Sensor-Belege tragen; kein Geheimnis in der Ablage, kein Gate gelockert, keine Accepted-ADR berührt, die Zielbild-Konflikte sind benannt und nicht entschieden.** Der Slice ist nicht closure-fähig, weil (a) die Closure-Pflichten offen sind (erwartet) und (b) der **Slice-Text selbst dem Stand nicht mehr entspricht**: DoD-Wortlaut gegen umgeschriebenen Plan (V-1, V-2), ein DoD-Haken ohne gemessenes oder als Abweichung geführtes Ergebnis (V-4), fehlende Träger-Einträge (V-5) und zweite Fundstellen (V-3, V-6). Kein HIGH, 5 MEDIUM (V-1 bis V-5), 2 LOW, 3 INFO. Alle MEDIUM sind Entscheidungen des Planners oder Nutzers, keine Mängel an Code oder Messwerten.

## DoD-Punkte (§2)

### Liefer-Punkt 1, Antwortseite im RPC und Zweitfälle (Haken gesetzt): Messung bestätigt, Haken nur teilweise getragen

* (a) Fehler in `sp_executesql`, vor und nach Zeilen: **bestätigt.** Selbst gelesen `rpc-fehler-vor-zeilen` 2022 (Nachricht 14: ERROR, RETURNSTATUS 208 `79 d0000000`, DONEPROC `fe 0200 e000` = Status ERROR, Befehl `0x00e0`, kein DONE); die Belegtests `MessungBelegTests` (Zeilen 111 bis 157) prüfen W-1 bis W-3 für beide Instanzen. Die Frage „behandelt der Referenzclient beide Formen verschieden“ ist laut Messprotokoll **nicht messbar**, weil der echte Server DONE im RPC nicht sendet; das steht ehrlich im Protokoll (Antwort zu [ADR-0014](../plan/adr/0014-fehlerfolge-rpc-antwort.md), Punkt d), die DoD verlangt aber die Aussage. Zur Entscheidung: V-4.
* (b) COLMETADATA hinter RETURNVALUE: Test `Kein_Szenario_stellt_COLMETADATA_hinter_RETURNVALUE_...`; das Messprotokoll sagt „in diesen Szenarien nicht eingetreten, ein erzwingendes Szenario nicht gefunden“. Die Spec-Lücke 3 aus [ADR-0015](../plan/adr/0015-spannungen-adr-gegen-spec.md) bleibt damit offen; die DoD verlangt „gemessen oder Abweichung“. V-4.
* (c) Ordinal mit mehreren Ausgabewerten (zehn Zweitfälle: sechs `sp_executesql`, `sp_prepexec` und `sp_execute`, zwei Prozeduren) und `curCmd` (W-6, 15 Szenarien `dml-*`): **bestätigt**, Test `Das_Ordinal_...` und `Die_Befehlskennung_...` (Zeile 221, einschließlich „nie 0 im Betrieb“). Nutzerentscheidung (1) vom 2026-11-06 (`curCmd` ohne Aussage, bis gemessen) wird von der Messung eingelöst; der Bau bleibt im Bau-Slice.
* „Je Punkt steht *gemessen* oder *Abweichung* in der Ausnahmeliste“: **nicht erfüllt nach Wortlaut.** Die Liste `mitschnitt-ausnahmen.tsv` gilt für abgespielte Mitschnitte; die Messungen werden nicht abgespielt, die Befunde stehen als W-1 bis W-9 im Messprotokoll, die Matrix trägt den Stand (sieben Zellen geändert, Verweise auf W-n). Die Abweichung vom Wortlaut ist im Plan §3 begründet (V-1).

### Liefer-Punkt 2, Zurücksetzen: bestätigt

W-7 an den Dateien nachgelesen: `reset-ohne-zustand` beider Instanzen, Nachricht 2 des Clients mit Status `09` (EOM und `RESETCONNECTION`), die Antwort beginnt mit `e3 0300 12 0000` (ENVCHANGE, Länge 3, Typ 18, beide Werte leer); die erste Nutzung trägt `01`. Tests Zeilen 251 und 272. „Ein Befund ‚Token unbekannt‘ ist kein Ergebnis“: der Zerleger (`TokenLeser`) kennt Typ 18 jetzt. Auch hier steht das Ergebnis im Messprotokoll, nicht in der Ausnahmeliste (V-1). [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md) (Accepted) unverändert (Messweg (a) ist damit gefahren, der Befund wird dort nicht nachgetragen).

### Liefer-Punkt 3, Anmeldung am Draht und SESSIONSTATE (A-18): bestätigt, mit Eingrenzung der Antwort

* TLS-beendende Betriebsart (`TlsAufnahme`, Zertifikat im Prozess, nicht abgelegt), PRELOGIN, LOGIN7 und Anmeldeantwort in der Ablage; Antwort `ENVCHANGE(1) INFO ENVCHANGE(7) ENVCHANGE(2) INFO LOGINACK ENVCHANGE(4) FEATUREEXTACK DONE` (W-8, Test Zeile 288). Gegenprobe `ConnectRetryCount=0` und `1`: FEATUREEXT `0x01` nur mit 1 (Dateien `anmeldung-crc0/1` unterscheiden sich im LOGIN7).
* A-18: Die DoD fragt, ob die Verhandlung der Grund dafür sei, dass 2022 SESSIONSTATE sendet und 2025 nicht. Gemessen (W-9): die Verhandlung ist **Voraussetzung** (ohne sie nie), nach `SET` senden **beide** Instanzen; nur nach dem Abbruch (Zustandskennung 254) sendet allein 2022. Die Ursache des Unterschieds zwischen den Instanzen ist **nicht** geklärt, und das Protokoll sagt es (Fortschreibung von A-18, Grenze „Bedeutung der Zustandskennung 254 nicht festgestellt“). Die Eingrenzung ist ehrlich; ob sie die DoD erfüllt, ist Lesart (V-4).
* „Ob der Server dieses Repos SESSIONSTATE senden soll, entscheidet der Nutzer“: nicht entschieden, nicht gebaut. Gewahrt.

### Nachweis-Pflichten

* *Jede Aussage an Test, Mitschnitt oder als Festlegung gekennzeichnet; Zuordnung Messung | Festlegung je Aussage in der Closure-Notiz:* **offen** (§7 „Wird bei der Closure gefüllt“). Im Messprotokoll sind Setzungen gekennzeichnet (Methode, TLS 1.2, `TrustServerCertificate=true` nur auf der Proxystrecke, „Hypothese“ zur Ursache des Abbruchs, „Einzelmessung“); die Zuordnung je Aussage steht noch nicht. V-7.
* `make gates` grün: **bestätigt** (Exit 0, selbst gefahren auf HEAD). Haken `[x]` zu Recht.
* Review: Report liegt vor; die Nachbesserung `073ad171` ist nicht erneut gereviewt (V-8). Haken `[ ]`, korrekt.
* Doku-Update (`harness/README.md`, Matrix): Haken `[x]`, getragen; Zelle der Zeile `make vergleichslauf-d` gelesen, Target-Liste im Kopf von `vergleichslauf-d.mk` führt `wire|wire-2022|wire-2025`.
* Closure-Notiz, Register, Risiken in §6, Paarungen: alle `[ ]` und §6 „offen bis zur Closure“: keine Haken vorab. Gefunden: **keine vorab gesetzten Haken** außer den drei oben (Liefer-Punkte, `make gates`, Doku-Update); die Liefer-Punkte sind Implementer-Haken und stehen unter dem Vorbehalt V-4.

## Review-Findings F-1 bis F-7, F-11 am Text

| Finding | Behoben? | Beleg (selbst gesehen) |
|---|---|---|
| F-1 MEDIUM, Redaktion SSPI/Kennwortwechsel ohne Test | ja | Test `Login7_redigiert_SSPI_Kennwortwechsel_und_Datenbankdatei` füllt die Felder 78, 82, 86 und prüft jedes; drei Mutationen je einzeln **rot** (siehe oben) |
| F-2 MEDIUM, Plan-Tabelle gegen Diff | Tabelle ja; Folge offen | §3 nennt jetzt `Messung.cs`, `MessAuswertung.cs`, `messung/`, `MessungWireTests.cs`, Beleg- und Einheitstests, csproj, `harness/README.md`, `spec/spezifikation.md`; Dateien im Diff stimmen mit der Tabelle überein. **Aber** der Plan wurde dem Code angepasst, der DoD-Text blieb (V-1, V-2) |
| F-3 MEDIUM, zweite Fundstelle zu W-7 und A-18 | teilweise | Spec: Messvermerk zu `RESETCONNECTION` auf W-7 gezogen, Zusage unberührt (Diff gelesen: nur der Vermerk, der Satz „trägt kein ENVCHANGE: der Referenzclient nahm sie so an“ bleibt als Zusage stehen); TSV: der Grund der fünf Regeln A-18 auf W-8/W-9 gezogen (kein „nicht untersucht“ mehr in der Liste, Grep). **Weitere Fundstellen in der Spec bleiben (V-3)** |
| F-4 MEDIUM, RETURNSTATUS-Bedingung | ja | Messprotokoll W-2 und Konflikt (3) und Matrix nennen die Bedingung („Fehlernummer, wenn danach keine Anweisung des Aufrufs mehr läuft; 0, wenn danach eine läuft; derselbe Fehler 8134 ergibt beide Werte; -6 in drei Prozeduraufrufen“); gegen Datei `rpc-fehler-vor-zeilen` (208) gelesen |
| F-5 MEDIUM, `WeitereAbweisen` ohne Test | ja (Test vorhanden) | `MessungEinheitTests` Zeilen 399 bis 407 prüfen `WeitereVerbindungen` 0 und `i`; die Mutation selbst nicht gefahren |
| F-6 LOW, Kommentare `TrustServerCertificate` | ja | Diff `ReferenzZiel.cs` und `Kompatibilitaet/Ziel.cs` je eine Zeile |
| F-7 LOW, Prüfzeile des Skripts | ja | Diff `tools/vergleichslauf-d.sh` eine Zeile; Lauf grün |
| F-11 INFO, Träger-Zuordnung ohne Eintrag | benannt, nicht gelöst | Messprotokoll führt „Träger-Eintrag im Bau-Slice fehlt (Planner)“ für Konflikte (4) bis (6) und die ADR-Frage; **bestätigt**: Grep nach `ENVCHANGE`, `SESSIONSTATE`, `RESETCONNECTION` im Bau-Slice ergibt 0 Treffer (V-5) |

F-8 bis F-10, F-12 (INFO) sind keine Nachbesserungsgegenstände. F-9 (Zufallskennungen in PRELOGIN) bestätigt (V-9).

## Redaktion erneut an den Dateien

* **Hexfolgen zurückgewandelt** (LOGIN7 aller 44 Dateien nach den Offsets zerlegt, Kennwort entschleiert: XOR `0xA5`, dann Nibble-Tausch): Rechnername 12 Zeichen, Benutzer 2, Server 19, Kennwort 30 — jeweils nur `x`; Datenbankdatei und Kennwortwechsel leer (Länge 0), SSPI-Länge 0; ClientPID und Verbindungskennung Nullen; ClientID Nullen. **Kein Befund**, an den abgelegten Dateien und an der neu aufgenommenen Ablage.
* **UTF-16-Strings aller Nachrichten gelistet** (alle Folgen ab 3 Zeichen) und ASCII-Folgen ab 5 Zeichen: nur Datenbanknamen `master`/`tdswire_live`, `us_english`/`Deutsch` und die Meldungstexte, Bibliotheksname `Core Microsoft SqlClient Data Provider`, das Merkmal `|MS-MDS|7.1.1|X64|Linux|Ubuntu 24.04.5 LTS|.NET 10.0.14|0|0`, Paketgrößen `4096`/`8000`, SQL-Text und Objektnamen (`wire_*`), Servernamen in INFO/ERROR als `x`. **Nicht gefunden:** `sa`, `Tdswire-Ref`, `tdswire-vgl`, `Zufallswert`, ein Name aus zwölf Hex-Zeichen (Container-Kennung), ein Rechnername; die einzigen ASCII-Treffer sind drei Zufallsfolgen im Hex der Zufallskennungen. Kopfzeilen: Instanz, Image-Digest, Client, Hash, Methode, Datum; kein Host.
* **Mutationen der Redaktion rot:** SSPI (Feld 78), Kennwortwechsel (Feld 86), Datenbankdatei (Feld 82): je rot, genau der eine Test (siehe oben). Das Review hatte 0 rot gesehen.
* Grenze (im Protokoll genannt): Länge des Kennworts (30) und der Namen bleibt in den Längenfeldern sichtbar; Anwendungs- und Bibliotheksname, Sprache, Merkmalsdaten und die Zufallskennungen des Clients bleiben stehen.

## Zweite Fundstellen (Grep nach Messung, nur Messvermerke, Zusagen unberührt)

Gesucht: „nicht gemessen“, „nicht untersucht“, „ohne Messung“, „Handvektor“, „offen“ zu ENVCHANGE, RESETCONNECTION, SESSIONSTATE, DONEPROC, `curCmd`, Ordinal, A-18 in `spec/`, Matrix, Messprotokoll, TSV, offenen und laufenden Slices, Register, `src/`.

| Ort | Aussage heute | Stand der Messung | Bewertung |
|---|---|---|---|
| `spec/spezifikation.md` (Pooling/`RESETCONNECTION`) | Messvermerk auf W-7 gezogen | gemessen | behoben (F-3) |
| `spec/spezifikation.md` Zeile 1158 (Login-Antwort) | „Festlegung ohne Messung am Produkt“ | Anmeldeantwort des echten Servers gemessen (W-8) | zweite Fundstelle, **kein Vermerk** (V-3) |
| `spec/spezifikation.md` Zeile 1175 | „an einem echten SQL Server … für 17 aufgenommene Szenarien gemessen … sonst Handvektoren“ | 22 weitere Szenarien je Instanz | zweite Fundstelle (V-3) |
| `spec/spezifikation.md` Zeile 506 | „Tokenfolge, Ordinal und Name des Handle-RETURNVALUE sind Handvektoren“ | Ordinal von `sp_prepexec`/`sp_execute` gemessen (W-5, A-16) | zweite Fundstelle (V-3) |
| `spec/spezifikation.md` Zeile 568 | „`curCmd` der DONE-Tokens ist 0; woher sie kommt, ist nicht festgelegt“ | `curCmd` je Anweisungsart gemessen (W-6) | Zusage (Konflikt 2, Bau-Slice); nur ein Messvermerk wäre zulässig (V-3) |
| `spec/spezifikation.md` Zeile 1481 (Änderungsverlauf 2026-11-01) | „nicht gemessen“ | Verlaufszeile | bleibt (Zeitdokument) |
| Matrix (`kompatibilitaetsmatrix.md`) Zeilen ENVCHANGE, DONE, RETURNSTATUS, RETURNVALUE, FEATUREEXTACK/SESSIONSTATE, 8, 9 | gemessener Stand mit Verweis auf W-n | | geprüft, ohne Befund (Zeile „Antwort auf RPC“ nennt als Ist „Ordinal ab 0 in der Reihenfolge der Ereignisse“, richtig als Ist) |
| TSV `mitschnitt-ausnahmen.tsv` | fünf Regeln A-18 | | behoben; Grep nach „nicht untersucht“ in `referenztest-dotnet/Vergleich/`: kein Treffer |
| Register `tds-8-0-und-pooling-nicht-gemessen/state.md` | „nicht gemessen bleiben mehrpaketiges LOGIN7, das `RESETCONNECTION`-Bit, TLS 1.2 …“ | `RESETCONNECTION`-Bit am Draht gesehen (W-7), TLS 1.2 im Proxy gesetzt | veraltet; Register ist Closure-Pflicht (DoD `[ ]`), hier benannt |
| Register `anmeldung-am-draht-nicht-aufgenommen/state.md`, `messung-am-echten-sql-server-fehlt` | „Träger entschieden“ / „geplant“ | Anmeldung am Draht gemessen | wie oben, Closure-Pflicht |
| Slices `open/` und `in-progress/` | `slice-antwort-tokens-an-messung-angleichen`: Setzungen offen bis Closure (Zeile 156) | | kein Messvermerk nötig; Träger-Lücke siehe V-5 |
| **Stand von [ADR-0014](../plan/adr/0014-fehlerfolge-rpc-antwort.md) im Messprotokoll** | Konflikt (3), „Antwort auf die offene Frage“: „zurückgezogen; Frage führt [ADR-0038](../plan/adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) (`Proposed`), Option A empfohlen, Nutzer noch nicht entschieden“ | gegen `main`: [ADR-0014](../plan/adr/0014-fehlerfolge-rpc-antwort.md) `Status: Withdrawn`, Index `Withdrawn`; [ADR-0038](../plan/adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) `Proposed` im Index | **stimmt mit `main` überein** |
| Slice-Datei selbst: Bezug, Herkunft, DoD Liefer-Punkt 1, §6 | [ADR-0014](../plan/adr/0014-fehlerfolge-rpc-antwort.md) `Proposed`, „Annahme von Option A oder Zurückziehen“ | [ADR-0014](../plan/adr/0014-fehlerfolge-rpc-antwort.md) ist `Withdrawn` | **veraltet** (V-6) |

## Findings

* **V-1 (MEDIUM) DoD-Wortlaut und Plan §3 laufen auseinander.** §3 wurde in diesem Zweig auf den Code umgeschrieben (`Messung.cs`, `messung/` statt `aufnahmen/`, `MessungWireTests.cs` statt `MitschnittLaufTests.cs`, Befunde im Messprotokoll statt in der Ausnahmeliste). Der DoD-Text von Liefer-Punkt 1 nennt weiter `Vergleich/MitschnittLaufTests.cs` und „in der Ausnahmeliste“; Liefer-Punkt 2 sagt „Das Ergebnis steht in der Ausnahmeliste“. Haken sind gesetzt gegen einen Text, den die Lieferung nicht erfüllt. Zu entscheiden (Planner): DoD-Wortlaut an die Lieferung angleichen oder die Abweichung in der Closure-Notiz ausdrücklich als solche führen. Nicht lösen hier.
* **V-2 (MEDIUM) Spezifikations-Änderung gegen Ausschluss im Slice.** §1 schließt „das Ändern von Code, Spezifikation oder `Accepted`-ADRs aufgrund eines Befunds“ aus, die Nachweis-Pflicht sagt „Nachträge in der Spezifikation und in ADRs führt dieser Slice nicht aus“. Der Zweig ändert `spec/spezifikation.md` (nur ein Messvermerk, Zusage unberührt, am Diff geprüft), und die §3-Zeile dazu ist erst mit `073ad171` entstanden. Die Reichweite („nur Messvermerke“) ist für W-7 eingehalten; für die anderen Fundstellen (V-3) nicht entschieden. Planner: Ausschluss und §3 vereinbaren, und die Frage, ob Messvermerke an den übrigen Stellen jetzt oder im Bau-Slice folgen.
* **V-3 (MEDIUM) Zweite Fundstellen in der Spezifikation ohne Messvermerk:** Zeilen 506 (Handle-RETURNVALUE: Ordinal und Name als Handvektor), 568 (`curCmd` ist 0), 1158 (Login-Antwort „ohne Messung am Produkt“) und 1175 („17 aufgenommene Szenarien“). Sie sind Setzungen oder Zusagen, die gegen die Messung stehen (Konflikte 1, 2 bzw. Anmeldeantwort); nur der Messvermerk, nicht die Zusage, wäre Gegenstand dieses Slice. Zu entscheiden: welche davon jetzt einen Vermerk bekommen. Zuordnung Messung | Festlegung je Aussage: V-7.
* **V-4 (MEDIUM) Liefer-Punkt 1 ist abgehakt, zwei Teile sind weder „gemessen“ noch „Abweichung“ im Sinne der DoD:** (i) ob der Referenzclient DONE und DONEPROC mit Fehlerstatus verschieden behandelt, ist nicht messbar (gesendete Formen gemessen, Wirkung gleich: Ausnahme am Ort, Verbindung offen); (ii) COLMETADATA hinter RETURNVALUE ist in keinem der Szenarien eingetreten, Spec-Lücke 3 bleibt offen. Beides ist im Protokoll ehrlich genannt; ob das den Haken trägt (oder als „nicht lieferbar mit Grund“ in der Closure-Notiz stehen muss, §5 lässt das zu), entscheidet der Planner. Gleiches gilt für A-18: Voraussetzung gemessen, Ursache des Instanz-Unterschieds nicht.
* **V-5 (MEDIUM) Träger-Einträge im Bau-Slice fehlen:** [slice-antwort-tokens-an-messung-angleichen](../plan/planning/open/slice-antwort-tokens-an-messung-angleichen.md) enthält keine Zeile zu ENVCHANGE bei `RESETCONNECTION` (Konflikt 5), SESSIONSTATE (6) und RETURNSTATUS/RETURNVALUE im Fehlerfall (4) (0 Treffer für `ENVCHANGE`, `SESSIONSTATE`, `RESETCONNECTION`), und kein Eintrag zu [ADR-0038](../plan/adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) (`Proposed`, 0 Treffer); die Verweise auf [ADR-0014](../plan/adr/0014-fehlerfolge-rpc-antwort.md) in Zeilen 14, 54, 60 sind auf den zurückgezogenen Stand zu prüfen. Das Messprotokoll sagt „alle sechs gehen zum Bau-Slice“, der Bau-Slice führt nur Ordinal, `curCmd`, `MORE` und RETURNSTATUS; Konflikt (5) nennt ihn als Träger, ohne dass er dort steht; außerdem widerspricht sich der Satz bei (6): „ob er [SESSIONSTATE] senden soll, ist nicht entschieden“. Das Messprotokoll benennt die Lücke selbst („Planner“). Planner: Einträge anlegen oder die Aussage zurücknehmen. Nicht lösen hier.
* **V-6 (LOW) Slice-Datei trägt den Stand von [ADR-0014](../plan/adr/0014-fehlerfolge-rpc-antwort.md) vor dem Zurückziehen:** Bezug, Herkunft (`Proposed`), DoD Liefer-Punkt 1 („Entscheidungs-Konsequenz für [ADR-0014](../plan/adr/0014-fehlerfolge-rpc-antwort.md) (`Proposed`) … Option A anzunehmen oder die ADR zurückzuziehen“) und §6 („[ADR-0014](../plan/adr/0014-fehlerfolge-rpc-antwort.md) ist `Proposed`“) stimmen nicht mit `main` (`Withdrawn`, [ADR-0038](../plan/adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) `Proposed`) überein; das Messprotokoll und der Index stimmen. Die Risiko-Ausgänge in §6 sind „offen bis zur Closure“ (zulässig bis dahin).
* **V-7 (LOW) Zuordnung *Messung | Festlegung* je Aussage steht aus** (Closure-Notiz leer). Im Protokoll tragen Methode, TLS 1.2, `TrustServerCertificate=true`, die Hypothese zur Ursache des Abbruchs von `reset-ohne-zustand` und „Einzelmessung“ ihre Kennzeichnung; die Zuordnung je Aussage ist Closure-Pflicht (`BEO-TDS/aussagen-ueber-messung-hinaus`).
* **V-8 (INFO) Review-Abdeckung:** Die Nachbesserung `073ad171` (Test, Spezifikationsvermerk, TSV, Matrix, Protokoll) ist nicht erneut gereviewt; Re-Review der Fix-Stellen wie bei früheren Slices.
* **V-9 (INFO) Zufallskennungen in PRELOGIN:** jede Neuaufnahme ändert alle 44 Dateien (selbst gefahren, 46 Zeilen); „wiederholbar“ heißt inhaltsgleich, nicht byte-gleich. Im Protokoll als Grenze genannt, der Kopf der Dateien nennt nur LOGIN7 als redigiert. Review F-9, unverändert.
* **V-10 (INFO) Stempel:** `make gates` lief voll (kein Beleg der Klasse `werkzeug` an einem Vorfahren); es ist kein Gate gelockert, keine Schwelle gesenkt, `harness/README.md` nur um die Messreihe in der Zeile des Targets erweitert.

## Konformität der Entscheidungen

* [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md) (Referenzclient, Docker-only): Messcode im Repo, Ablage außerhalb des Repos gesammelt (`mktemp -d`), nur Redigiertes wandert hinein; Kennwort je Lauf zufällig. Gewahrt.
* [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md): `make vergleichslauf-d` bleibt Werkzeug, kein Gate, nicht in `make e2e`; `LAUF=wire` additiv. Gewahrt.
* [ADR-0027](../plan/adr/0027-abbruch-und-zuruecksetzen.md) (Accepted): unverändert; Messweg (a) gefahren. Gewahrt (§3.5).
* `SPEC-045`, `SPEC-050`: Redaktion, Herkunft und Test mit Tag `@spec SPEC-045`; Messungen werden nicht abgespielt, daher nicht in der Ausnahmeliste (V-1).
* [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität): Messung an zwei Instanzen mit Digest, Einzelmessungen als solche benannt.
* `AGENTS.md` §3.1 (Docker-only), §3.2 (kein Suppress im Diff), §3.5, §3.6 (keine Schwelle gesenkt), §3.8 (Aussagen tragen so weit wie die Zeilen; Folgerungen und Hypothesen markiert): kein Verstoß gefunden.
* Zielbild-Konflikte (Ordinal, `curCmd`, Fehlerabschluss im RPC, RETURNSTATUS/RETURNVALUE im Fehlerfall, ENVCHANGE bei `RESETCONNECTION`, SESSIONSTATE) sind **benannt und nicht entschieden**; [ADR-0038](../plan/adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) bleibt beim Nutzer.

## Offen für den Planner (nicht gelöst)

1. Träger-Einträge im Bau-Slice [slice-antwort-tokens-an-messung-angleichen](../plan/planning/open/slice-antwort-tokens-an-messung-angleichen.md) für die Konflikte 4 bis 6 und die ADR-Frage ([ADR-0038](../plan/adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md)); Stand der [ADR-0014](../plan/adr/0014-fehlerfolge-rpc-antwort.md)-Verweise dort (V-5).
2. Wortlaut des Plans: DoD-Text gegen umgeschriebenen §3 (V-1), Ausschluss „keine Spezifikations-Nachträge“ gegen den Messvermerk und die übrigen Fundstellen (V-2, V-3), Haken von Liefer-Punkt 1 (V-4), veralteter [ADR-0014](../plan/adr/0014-fehlerfolge-rpc-antwort.md)-Stand in der Slice-Datei (V-6).
3. Zuordnung *Messung | Festlegung* je Aussage in der Closure-Notiz (V-7).

## Bedingungen für die Closure

* B-1: Entscheidungen des Planners zu V-1 bis V-6 in der Closure-Notiz festhalten.
* B-2: Re-Review der Nachbesserung `073ad171` (V-8).
* B-3: Closure-Notiz mit je Messpunkt Ergebnis, Grenzen und Steering-Loop-Lerneintrag; Ausgänge der acht Risiken in §6 (derzeit alle „offen bis zur Closure“); Register fortschreiben (u. a. `tds-8-0-und-pooling-nicht-gemessen`, `anmeldung-am-draht-nicht-aufgenommen`, `messung-am-echten-sql-server-fehlt`); die drei Paarungen.
* B-4: `make gates` erneut auf dem Merge-Stand belegen.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| Geheimnisse in `Mitschnitt/messung/**` | geprüft (44 Dateien, abgelegt und neu aufgenommen), ohne Befund |
| Redaktionsmutationen 78, 82, 86 | rot, ohne Befund |
| `make vergleichslauf-d LAUF=wire` | grün, Ablage danach zurückgesetzt, Arbeitsbaum sauber |
| `make gates`, `make docs-check`, `make stempel-eingaenge-pruefung`, Stage `test` (79) | grün |
| Accepted-ADRs, Gates, Schwellen | unverändert |
| [ADR-0014](../plan/adr/0014-fehlerfolge-rpc-antwort.md)/0038-Stand im Messprotokoll gegen `main` | stimmt |
