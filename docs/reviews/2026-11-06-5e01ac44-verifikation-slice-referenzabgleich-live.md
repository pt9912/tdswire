# Verifikation: slice-referenzabgleich-live — 2026-11-06

**Rolle:** Verifier (Modul 11), "Bauen wir es richtig?" gegen DoD (§2 des Slice) und Entscheidungen ([ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), `SPEC-045`/`SPEC-050`/`SPEC-032`/`SPEC-035`). Kein Review, keine Reparatur. Maßstab des Nutzers (2026-11-06): Spec ist Zielbild, der echte SQL Server ist die Referenz, Abweichungen des Servers sind Schuld.

**Gegenstand:** Zweig `slice-referenzabgleich-live`, HEAD `5e01ac44` (Implementierung `7cb411b2`, Review `28d89330`, Merge `6ed3278e`, Nachbesserung `5e01ac44`). Bezug: [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md). EULA: Zustimmung des Nutzers pt9912 für das gepinnte Developer-Image (2026-11-01, 2026-11-06); Lauf nur lokal.

## Eigene Sensor-Läufe (am Stand `5e01ac44`)

| Lauf | Ergebnis |
|---|---|
| `make vergleichslauf-d LAUF=live-2022` | Exit 0, "live: gruen (25 Schritte)", "Live-Vergleich: gleich oder genau die gelistete Abweichung", Lauf 35 s (davon Bau und Start 16 s; Einzelmessung, andere Last als im Protokoll). Meine Zählung der Ausgabe: 12 Zeilen `ok   gleich`, 13 Zeilen `ok   Ausnahme` (3 so gewollt: `anmeldung-falsches-kennwort`, `anmeldung-unbekannter-benutzer`, `fehler-nach-zeilen`; 10 Lücke: `mehrteilig`, `fehler-vor-zeilen`, `anweisung-ohne-tabelle`, `bedingung-mit-or`, `cancel-mit-folgeabfrage`, `schema-person`, `schema-typen`, `schema-person-schluessel`, `aendern`, `loeschen`); 25 Zeilen `live: referenz`, 25 Zeilen `live: server`. 12 + 13 = 25, stimmt mit Slice und Protokoll. |
| Cancel-Text dieses Laufs | Referenz 2022 diesmal die kurze Variante ("A severe error ... should be discarded."), also die erste der zwei Cancel-Regeln. Die zweite Regel (lange Variante) habe ich nicht selbst gesehen; die zwei Regeln sind in `live-ausnahmen.tsv` wortgleich geführt. |
| `make skript-selbsttest` | Exit 0 (enthält den Live-Vergleich des Selbsttests von `vergleichslauf-d.sh`). |
| `make docs-check` | 874 Dateien, 0 Befunde, Exit 0. |
| `make gates` | Exit 0. **Grenze:** die Gruppen `werkzeug`, `tests`, `stempel` wurden "übersprungen, Beleg für <Hash>" (Stempel, [ADR-0024](../plan/adr/0024-lokale-stempel-zwei-klassen.md)); ich habe `FORCE=1` nicht gefahren. Der Beleg ist der Stempel des Implementers am selben Arbeitsbaum-Hash, nicht mein Frischlauf. Selbst gefahren und grün: `skript-selbsttest`, `docs-check`, `gate-zuordnung` und `stempel-eingaenge` (im Lauf). |
| `live-2025`, `LAUF=live` (beide Instanzen), `messung-*` | **nicht gefahren.** Die Aussagen des Protokolls zu 2025 und zu den Gegenmessungen M-1 bis M-5 stütze ich auf das Protokoll und den Review-Report (der Reviewer wiederholte M-4 an 2022), nicht auf eigene Läufe. |

## DoD (§2)

| # | Punkt | Befund | Haken im Slice |
|---|---|---|---|
| 1 | Server-Seite (umformuliert) | Erfüllt in der neuen Fassung. Der Lauf startet den Server als eigenen Container, eigenes Netzwerk, ohne Mount (`vergleichslauf-d.sh`), Stage `kompatibilitaetsserver` mit Basis per Digest; Tabellen in `testserver/.../KompatibilitaetsServer.kt` (Datei vorhanden), Schema der Referenzinstanz ist `referenztest-dotnet/Vergleich/testschema.sql`. **Plan-Änderung:** die Alt-Fassung ("Tabellenbeschreibung des Simulations-Backends bildet das Testschema ab ... Weg des Ladens folgt der ADR") ist ersetzt; der Text nennt "Entscheidung des Nutzers 2026-11-06" und "Entscheidung N5". Nachvollziehbar über Datum und `git diff 28d89330 HEAD`; eine ausdrückliche Kennzeichnung als Plan-Änderung (was vorher stand) steht bewusst nicht im Text (`AGENTS.md` §3.7: keine Chronik). Das ist akzeptabel, aber siehe V-1. | `[x]` (belegt) |
| 2 | Live-Lauf | Erfüllt: 25 Schritte, je Schritt eine Zeile je Ziel, Wertverglichen nur auf vorhanden/nicht vorhanden bei Serververgebenem (Versionstext), Dauern nicht verglichen (Protokoll "Was der Lauf vergleicht"). Eigener Lauf `live-2022` grün. | `[x]` (belegt für 2022) |
| 3 | Ausnahmeliste und Urteil | Erfüllt: jede der 13 Abweichungen steht mit Schritt, wortgleichem Ergebnispaar, Grund, Art und Verweis in `live-ausnahmen.tsv` (14 Zeilen, Cancel hat zwei Regeln); `--vergleich` urteilt gleich oder genau die Abweichung, sonst rot; der Selbsttest deckt Gleichheit, gelistete, kleinere, größere, nicht gelistete Abweichung, doppelte Regel und Regel ohne Abweichung (`skript-selbsttest` grün). Gate-Aufnahme: nicht aufgenommen (ADR-0022). | `[x]` |
| N1 | `make gates` grün | Exit 0, mit der oben genannten Stempel-Grenze | `[x]` |
| N2 | Doku-Update | `docs/messungen/referenzabgleich.md`, `harness/README.md` (Sensors) nachgezogen | `[x]` |
| N3 | Review durchgeführt, Report liegt vor | Report `2026-11-06-7cb411b2-slice-referenzabgleich-live.md` liegt vor; Haken steht offen. Die Nachbesserung `5e01ac44` ist nicht erneut gereviewt. | `[ ]` (keine Haken vorab; Entscheid beim Planner, siehe V-4) |
| N4 | Closure-Notiz, Register, Risiko-Ausgänge, Paarungen | offen, wie vorgesehen (§7 "Wird bei der Closure gefüllt"; §6 Ausgänge "offen bis zur Closure"/"weiter offen") | `[ ]` (richtig) |

## Review-Findings F-1 bis F-11 am Text

| F | Befund | Stand in `5e01ac44` |
|---|---|---|
| F-1 (A-4, A-5 "so gewollt" auf unbestätigter Setzung) | behoben: Art **Lücke** in `live-ausnahmen.tsv` und Protokoll; Grund nennt "Lesart, die der Nutzer nicht bestätigt hat", Zielbild echter Server, Lastenheft schließt `OR` nicht aus | erledigt |
| F-2 (A-3 Class 20) | behoben: Art **Lücke**, "Abweichung des Servers, bis die Zuordnung entschieden ist"; Register `BEO-TDS/client-schliesst-bei-class-20-selbst` (Verzeichnis vorhanden) | erledigt |
| F-3 (Lücke ohne Träger, falsche Herkunft) | A-2: Register `BEO-TDS/fa-10-mehrteilige-antwort-ohne-backend-traeger` (vorhanden) zitiert, Codec-Slice in `done/` genannt, "Träger offen"; A-7: "misst nur, baut nicht, Träger des Baus offen"; A-8: "nennt SchemaOnly/KeyInfo/FMTONLY nicht, Träger nicht benannt", Träger-Vorschlag in §6. Alle drei sind **als offene Schuld geführt** (§6, Ausgang "weiter offen"), nicht mit tragendem Träger versehen: das ist ehrlich, die Entscheidung liegt beim Planner. Die Register-Einträge für A-7, A-8, A-3/A-4/A-5/A-9 sind "zur Closure" zugesagt, noch nicht angelegt. | benannt, Träger offen (V-2) |
| F-4 (M-4 gegen ADR-0036) | behoben: M-4 benennt "Widerspruch zu einer Setzung" mit [ADR-0036](../plan/adr/0036-handle-freigabe-und-grenze-je-sitzung.md) `Accepted`, "Folge-ADR mit `Supersedes` legt der Architect an"; Slice §6 trägt es als eigenes Risiko mit Ausgang "weiter offen (Folge-ADR nötig)". ADR-0036 unverändert (`git diff 28d89330 HEAD` berührt `docs/plan/adr/` nicht), §3.5 gewahrt | erledigt |
| F-5 (Schema ohne Sensor, N5 offen) | N5 entschieden (Nutzer, 2026-11-06), in DoD 1, Protokoll "Server-Seite (Schema)" und §6 festgehalten; fehlender Sensor als Risiko "Schema-Gleichlauf ohne Sensor" benannt | erledigt, Rest siehe V-1 |
| F-6 (A-9 Fixture-Konstante) | behoben: Art **Lücke**, Grund "feste Konstante ... keine Auswertung", zwei von 25 Schritten prüfen die Zählung nicht | erledigt |
| F-7 (zweite Fundstellen "eine Instanz Version 2022") | Slice §5 berichtigt (2022 und 2025, Nachzug-Slice verlinkt). `SPEC-045` Absatz "Grenzen" (`spec/spezifikation.md`, "Gemessen ist nur eine Instanz der Version 2022") steht weiter; Träger `slice-referenzabgleich-adr-0028-nachzug` (open) und der Slice §5 nennen das | erledigt (Spec-Stelle mit Träger) |
| F-8 (Zweig `leck`, Wartebedingung `gestartet` ohne Test) | nicht angefasst, `5e01ac44` nennt es nicht; LOW, Auswirkung klein (Wegwerf-Kennwort) | offen, unbenannt (V-3) |
| F-9 (Cancel-Text beide Instanzen) | behoben im Text: Protokoll "beide Instanzen zeigten beide Varianten"; die Regeln der `tsv` tragen "(2022 und 2025)". "sechs Läufe" ist entfernt. Es bleibt "in zwei früheren Läufen grün/rot" (Zeile "Ergebnis"): nicht im Repo abgelegt, nur Berichtsaussage | erledigt, Rest V-3 |
| F-10 (Zählung) | meine Nachrechnung bestätigt 12 + 13 = 25; die Zahl "19 Vergleichsfälle" des Selbsttests habe ich nicht neu gezählt | bestätigt für den Lauf |
| F-11 (Kopplung Stage) | als Risiko "Kopplung des Live-Laufs an die Dockerfile-Stage `kompatibilitaetsserver` und `/home/gradle/server.pem`" in §6 aufgenommen, Ausgang "weiter offen (Register)" | benannt |

## Entscheidungs-Konformität

| Quelle | Befund |
|---|---|
| [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md) / `SPEC-045` | Live-Lauf, Ausnahmeliste mit zwei Arten (so gewollt, Lücke), Regeln eng (Schritt plus wortgleiches Paar), kleinere/größere Abweichung rot: konform. TDS 8.0 am Live-Lauf nicht abgeglichen, als Grenze genannt. |
| [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md) | konform: `GATE_CHECKS` ohne `vergleichslauf-d`; `git diff main...HEAD` auf `Makefile`, `config`, `repo.mk`, `harness/mk` leer; `tools/mk/vergleichslauf-d.mk` nur +6/-1 (Läufe, Hilfetext "KEIN Gate"). Die Diffs an `Dockerfile`, `config/detekt/detekt.yml`, `tools/mk/build.mk` stammen aus dem Merge `6ed3278e` von `main`, nicht aus diesem Slice. Keine Lockerung (§3.6). |
| `SPEC-050`, `SPEC-032`, `SPEC-035` | `SPEC-050` (Live-Lauf als Teil des E2E-Durchgangs "noch einzurichten") ist nicht in den E2E-Durchgang aufgenommen: gewollt (ADR-0022), Spec nicht nachgezogen (V-5). `SPEC-035` (Anzahl betroffener Zeilen) wird in A-9 als Lücke gegen die Spec geführt; die Beschreibungsdatei trägt keine Zeilen (ADR-0012), das Schema bleibt im Code des Integrators: konform mit dem Nutzerentscheid. `SPEC-032` (Kompatibilitätslauf) unberührt. |
| `AGENTS.md` §3.8 (Aussage ≤ Beleg) | Protokoll sagt ausdrücklich: verglichen ist, was der Client ausgibt; keine Aussage über Tokenfolge, Statusbits, Draht; Einzelmessungen mit Last. Cancel-Textherkunft "nicht festgestellt". Konform. Rest: "zwei frühere Läufe" (V-3). |
| `AGENTS.md` §3.5 | ADRs unverändert. |

## Zweite Fundstellen (Grep)

- "so gewollt": nur noch in den drei verbliebenen Regeln (A-1, A-6 samt Pfad `fehler-nach-zeilen`) und in Definitionen/Skriptcode; die Aussagen zu A-3, A-4, A-5, A-9 in `tsv` und Protokoll sind Lücke. Die Spezifikation (`SPEC-045`) und die Welle-/Mitschnitt-Pläne nennen die zwei Arten unverändert (korrekt).
- "nur eine Instanz" / "eine Instanz Version 2022": `spec/spezifikation.md` SPEC-045 Absatz "Grenzen" (Träger: `slice-referenzabgleich-adr-0028-nachzug`, dort ausdrücklich genannt) und Beobachtung `spec-nachzug-nach-messung-offen`. Im Slice selbst keine Fundstelle mehr.
- "2022 und 2025": Slice §5/§6, Protokoll, `harness/README.md` ("2022, 2025; Digest-Pin"): einheitlich.
- SPEC-045-Verweise: Slice, Protokoll, `tools/vergleichslauf-d*.sh`, `LiveSzenarioTests.cs`, `testschema.sql`, `tsv`; die Aussage im Absatz "Grenzen" der Spec "gemeinsame Quelle, die Datei nach SPEC-035; wie das Skript entsteht, ist nicht festgelegt" steht neben dem Nutzerentscheid (Skript = `testschema.sql`, Schema im Code); die Spec trägt den Fall "nur im Code des Integrators" bereits. Nachzug der Spec zu N5 ist nicht benannt (V-5).
- README-Zellen: `make vergleichslauf-d` Vertrag-Zelle 200 Zeichen, `make skript-selbsttest` 193 (mit Leerzeichen gezählt): ≤ 200, am Rand.
- Kopplung: Dockerfile-Stage `kompatibilitaetsserver` (`FROM deps AS kompatibilitaetsserver`), `/home/gradle/server.pem` und `testserver/kotlin/.../KompatibilitaetsServer.kt` sowie Gradle-Task `kompatibilitaetsServer` in `testserver/build.gradle.kts` bestehen; `vergleichslauf-d.sh` baut `--target kompatibilitaetsserver`; mein Lauf `live-2022` ist nach dem Modul-Umbau grün. Kein Sensor hält die Kopplung (§6 benennt es).

## Abweichungen und offene Punkte (für den Planner)

- **V-1 (MEDIUM, Text):** Der Slice widerspricht sich selbst nach der Plan-Änderung. §3 Plan-Tabelle führt noch "Tabellenbeschreibung des Testschemas, neu" (Alt-Gegenstand von DoD 1); §6 erstes Risiko beschreibt die Beschreibungsdatei als gemeinsame Quelle und N5 als noch zu entscheidend ("ist nach ... zu entscheiden", "ob ein Test beide gegeneinander hält, ist offen (Frage in §6)"), obwohl N5 in DoD 1 und im zweiten Risiko entschieden steht. Ausgang "offen bis zur Closure" ist für den entschiedenen Teil überholt. Das ist keine DoD-Verletzung, aber eine Fortschreibungslücke gleicher Klasse wie F-7.
- **V-2:** Die Lücken A-2, A-3, A-4, A-5, A-7, A-8, A-9 haben keinen **bauenden** Träger; sie sind als offene Schuld geführt, die Register-Einträge sind zur Closure zugesagt, nicht angelegt (bis auf zwei vorhandene). Der Lauf ist wegen der Liste grün, nicht weil der Server dem Zielbild entspricht: ein "grün" belegt Übereinstimmung mit der Liste, 10 von 25 Schritten weichen als Lücke ab, 3 als gewollt ab. Die Closure muss das so sagen.
- **V-3 (LOW):** F-8 offen und nicht benannt; im Protokoll bleibt "in zwei früheren Läufen grün/rot" ohne abgelegten Beleg (Aussage ≤ Beleg: es ist Berichtsaussage). Die Textvariante der Cancel-Antwort schwankt; mein Lauf sah nur die kurze Variante.
- **V-4:** Die Nachbesserung `5e01ac44` ist nicht erneut gereviewt; ob der Review-Haken (N3) vor der Closure gesetzt wird oder ein Re-Review nötig ist, entscheidet der Planner.
- **V-5:** `SPEC-045` Absatz "Grenzen" (eine Instanz 2022; gemeinsame Quelle als Datei nach SPEC-035) und `SPEC-050` (E2E-Durchgang "noch einzurichten") bleiben unverändert; Träger für den ersten Teil: `slice-referenzabgleich-adr-0028-nachzug`; für N5 nicht benannt.
- **V-6:** Folge-ADR zu ADR-0036 (Architect) ist offen; M-4 hat keinen Sensor im Live-Lauf.

## Urteil

0 DoD-Verletzungen. Die drei Liefer-Punkte und `make gates` sind belegt (mit den genannten Läufen: Live-Lauf 2022, Selbsttest, docs-check; 2025 und Gegenmessungen nicht selbst gefahren; die Gate-Gruppen per Stempel übersprungen, nicht erzwungen). Die Review-Findings F-1 bis F-7, F-9, F-11 sind am Text behoben oder als offene Schuld ehrlich geführt; F-8 offen. Kein Gate gelockert, nichts in `make gates` aufgenommen, keine Haken vorab. Offen für den Planner: V-1 (Slice-Text konsistent machen), V-2 (Träger der Lücken), V-4, V-5, V-6.
