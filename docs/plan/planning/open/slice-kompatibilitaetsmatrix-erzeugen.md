# Slice slice-kompatibilitaetsmatrix-erzeugen: Kompatibilitätsmatrix als erzeugte Sicht auf die Spezifikation

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage (Ziel der Welle: jede Festlegung durch Tests belegt; die Sicht liest dieselben Belege wie [slice-qa-spec-abdeckung](../done/slice-qa-spec-abdeckung.md); die Welle ist noch flach in `planning/`, ihre Slice-Tabelle trägt die Zeilen).

**Bezug:** [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit) (Messmethode: „dokumentierte Kompatibilitätsmatrix“), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität) (Referenzclient), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) (E2E-Läufe stehen nicht in den Gates).

**Berührte Spec-Stellen:** `SPEC-001`, `SPEC-003`, `SPEC-028`, `SPEC-029`, `SPEC-030`, `SPEC-031`, `SPEC-032`, `SPEC-036` (die Festlegungen, aus denen die Sicht ihre Zeilen liest; dieser Slice ändert sie nicht, der Spec-Nachzug liegt in [slice-kompatibilitaetsmatrix-migration](slice-kompatibilitaetsmatrix-migration.md)).

**Verantwortlich:** — bis zur Priorisierung.
**Autor:** pt9912 (Planner-Rolle). **Datum:** 2026-11-01.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** `docs/plan/kompatibilitaetsmatrix.md` (Ort wie `AGENTS.md` §5 Regel 6 zum Zeitpunkt der Umsetzung) wird von keinem Menschen geschrieben, auch nicht in einer Quelldatei mit Aussagetexten. Sie ist die **erzeugte Sicht** auf die Spezifikation: Zeilen sind die `SPEC-*`-Festlegungen und Tabellenzeilen von `spec/spezifikation.md`, gruppiert nach der Gliederung der Spec; die Aussage (Setzung, nicht gemessen, Zielzustand, Abweichung) ist der Marker der Spec-Zeile; Belege sind die `@spec`-Tags der Kotlin-Tests mit den JUnit-XML-Berichten, für den Referenzclient die `Spec`-Traits der .NET-Tests mit einer **E2E-Ergebnisdatei**, die `make e2e` schreibt; der Kopf ist fester Text im Erzeuger, der nur die Spalten erklärt. Der Erzeuger ist **kein dritter**, sondern eine zweite Ausgabe des vorhandenen (`tools/spec-abdeckung.sh`, geteilter Leser). Er verweigert (Exit 1) eine Festlegung der Kategorie „Nachricht/Datentyp“ ohne Beleg und ohne erklärten Marker, ein Tag auf unbekannter Kennung und eine Ergebnis-Zeile ohne Tag; zwei Läufe sind byte-gleich.

**Bestandsaufnahme der heutigen Matrix (Stand 2026-11-01, Worktree auf `a9e7abf`; gezählt):** 270 Zeilen, 169 Tabellenzeilen = 14 Kopf-, 14 Trenn- und **141 Datenzeilen** in 14 Tabellen; 24 Commits im Verlauf der Datei (der Anlass nannte 20). 282 Zitate `Klasse > Testfall` (237 verschiedene, 58 Testklassen; keines mit `(` oder `[`). Von 83 822 Byte stehen 21 267 (25 %) in Zitaten; rund 75 % sind Handtext. **Keine Zeile ist reine Zitat-Zeile.**

| Klasse | Abschnitte (Datenzeilen) | Zeilen | Zitate |
|---|---|---|---|
| A. Zeile mit Zitat | 1 (9), 2 (15 von 17), 2a (9 von 10), 3a (10 von 12), 3b (16), 4 (9), 6b (3 von 4) | 71 | 279 |
| B. Zeile ohne Zitat in diesen Tabellen | NBCROW, „weitere Tokens“, nicht unterstützte Typen (2a), Batch-Flag, Table-Valued-Parameter, „Kernregeln gelten für Testquellen“ (6b) | 6 | 0 |
| C. Aussage ohne Test | 5 Bekannte Lücken (16), 6a Ist-Abgleich (10), „Herkunft der Aussage“ (7) | 33 | 1 |
| D. Referenzclient | 7a (12), 7b (10), 7c (5), 7d (4) | 31 | 0 |

71 + 6 + 33 + 31 = 141. Die Spezifikation führt dagegen **56 Festlegungen** (Zählung der Überschriften `### SPEC-` und der Tabellenzeilen `SPEC-` in `spec/spezifikation.md`, Nummern bis `SPEC-059`; `make spec-abdeckung` liest dieselben).

**Was bleibt, wenn die Matrix nur eine Sicht ist — ehrlich gemessen.** (1) Ist die Matrix dann mehr als `spec-abdeckung` mit anderer Gruppierung und einer Referenzclient-Spalte? **Nein.** Drei Unterschiede bleiben: die Gruppierung nach Thema, die Spalte für den Referenzclient und die Aussage „unterstützt / nicht unterstützt“, und alle drei sind aus Spec-Zeilen und Tests ableitbar, wenn die Spec die Zeile trägt. Der Erzeuger ist darum eine zweite Ausgabe des vorhandenen, nicht ein neuer. (2) **Granularität:** 56 Festlegungen tragen nicht die 71 + 6 Zeilen der Abschnitte 1 bis 4, in denen jedes Token, jeder Pakettyp und jeder Parametertyp eine Zeile hat. Heuristik an den 70 Zeilen dieser Abschnitte mit Schlüsselwort in der ersten Zelle (Skript, einmalig; ein Treffer heißt nur, dass die Spec das Wort nennt, nicht dass sie die Aussage festlegt): 53 Zeilen mit Treffer, 4 ohne (`ErrorMapper`, „Abbildung Backend nach TDS“, Paketheader, TCP-Listener), 13 ohne auswertbares Schlüsselwort (zu lesen von Hand). Gezielt gesucht: „Batch-Flag“/„mehrere RPC“ 0 Treffer, „Transaction Manager“ 0, „Pfadwahl“/„erste Byte“ 0, `ErrorMapper` 0; dagegen `NBCROW` 1, `FLTN` 1, `RESETCONNECTION` 1, `SSPI` 4, `FMTONLY` 3, `PLP` 6, Table-Valued 2. **Das ist ein Befund für die Spec, nicht für die Matrix:** Es fehlen mindestens die Festlegungen Batch-Flag, Transaktions-Pakettyp und Pfadwahl je Verbindung, und die Spec hat für Nachrichten, Tokens und Parametertypen keine Tabelle mit einer Zeile je Fall. Ohne sie ist die Sicht gröber als die heutige Matrix (rund 56 statt 141 Zeilen); mit ihr (Spec-Nachzug in der Migration) bleibt die Granularität erhalten, und die Aussage steht dort, wo sie wahr wird.

**Entscheidung je Aussage der Klassen B, C, D (Zahlen, keine Pauschale):**

| Gruppe | Zeilen | Vorschlag | Folge |
|---|---|---|---|
| Lücken 2, 3, 5, 6, 7, 8, 9, 10, 12, 13, 14, 15 (Spec nennt den Gegenstand: `PLP`, `xml`, Table-Valued, `NBCROW`, `FLTN`, Referenzabgleich, `RESETCONNECTION`, Precision 38, ATTENTION, Handles, `SSPI`/Kennwortwechsel, Typen des Parsers) | 12 | **SPEC-Festlegung ist da**; die Sicht übernimmt Kennung und Marker. Fehlt der Marker („nicht unterstützt“, „Abweichung“), wird er in der Spec ergänzt | Spec-Nachzug nur der Marker |
| Lücken 4 (Batch-Flag) und 11 (Transaction Manager) | 2 | **neue SPEC-Festlegung** (Wortlaut ohne Slice-, Wellen- oder ADR-Wörter) | Spec-Nachzug |
| Lücke 16 (mehrteilige Antworten, [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte)) | 1 | bleibt als Anforderung ohne Beleg **sichtbar** (Waise der Sicht); ob die Spec sie trägt, ist offen | Spec-Entscheidung des Nutzers |
| Lücke 1 (Referenzclient-Abdeckung insgesamt) | 1 | **entfällt**: Summenaussage, die Spalte „Referenzclient“ zeigt sie | — |
| Spalte „Träger“ der Lücken (Slice-Namen) | 16 | **entfällt**: Planungsstand, steht in `open/` und `done/`; die Spec nennt keine Slices | — |
| Ist-Abgleich 6a (8 Package-Zeilen, 2 Constraints: `architecture.md` §2 gegen `detekt.yml`) | 10 | **entfällt als Tabelle**: eine Gegenüberstellung zweier Dateien ist eine Prüfung, keine Aussage; die Modulgrenzen belegt `make detekt` samt `make detekt-selbsttest` (`SPEC-041`), die Sicht führt eine Gate-Zeile (Grenze: der Erzeuger kennt Gate-Nachweise nicht, die Zeile sagt „Gate“, nicht „bestanden“). Verlust: die Tabelle der Abgleichs-Spalten | Entscheidung des Nutzers; Alternative: ein Test, der beide Dateien liest (neuer Sensor) |
| „Herkunft der Aussage“ (7 Stufen) | 7 | **fester Text im Erzeuger** (Legende der Marker der Spec: Setzung, nicht gemessen, Handvektor, Festlegung …); der Erzeuger prüft, dass jeder Marker der Spec in der Legende steht | — |
| Zeilen ohne Zitat: NBCROW, nicht unterstützte Typen (2a), Table-Valued | 3 | Spec trägt (`NBCROW` 1, `SPEC-003` „gehören nicht dazu“, Table-Valued 2); Beleg „keiner“ ist ein erklärter Marker | — |
| Zeilen ohne Zitat: „weitere Tokens“, Batch-Flag | 2 | **neue SPEC-Festlegung** | Spec-Nachzug |
| Zeile ohne Zitat: „Kernregeln gelten für Testquellen“ (6b) | 1 | entfällt (Konfigurationsdetail von `detekt.yml`, Gate `SPEC-041`) | — |
| Referenzclient 7a (12), 7b (10), 7c (5), 7d (4) | 31 | je .NET-Test ein `Spec`-Trait (33 `[Fact]` für 31 Zeilen: 11 + 13 + 5 + 4); die Messwerte (`ServerVersion 01.00.0000`, `Number 50004`) stehen als Assertion im Test und als Zeile `messung:` im Lauf, **nicht** in der Sicht — Verlust benannt. Hat ein Szenario keine passende Festlegung, ist das ein Spec-Befund | Traits setzen, Zuordnung je Test |

Summe: 12 + 2 + 1 + 1 = 16 Lücken; 3 + 2 + 1 = 6 Zeilen ohne Zitat; 10 + 7 + 31 = 48 weitere; die Lücken-Spalte „Träger“ ist keine Zeile.

**E2E-Ergebnisdatei (Entscheidung des Nutzers: ob der Test bestanden hat, steht erst nach `make e2e` fest).** Heute schreibt `make e2e` ([`tools/e2e.sh`](../../../../tools/e2e.sh), Stufen rufen `make`-Targets) nur Deklarationen (`e2e_abdeckung`, Quelle der Sicht `docs/plan/e2e-abdeckung.md`: „Deklaration, kein Lauf-Beleg“) und gibt die Ergebnisse auf die Konsole aus (`tools/kompatibilitaetstest.sh` ruft `dotnet test` mit dem Logger `console;verbosity=detailed`); eine Ergebnisdatei gibt es nicht. Vorschlag: `make e2e` schreibt am Ende eine kleine Datei (Zeile je .NET-Test: Klasse, Testfall, `PASSED`/`FAILED`, dazu eine Kopfzeile mit dem Eingangs-Hash der Stempelklasse `tests` ([ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md), `tools/stempel.sh`) und dem Datum); der Erzeuger liest sie wie die JUnit-Berichte. Ort: `docs/messungen/` (Regel 7 von `AGENTS.md` §5: Einzelmessungen mit Datum, Host und Bedingung), weil sie eine Einzelmessung ist und kein Bericht der Stage `test`; sie liegt nicht in `tools/stempel/tests.eingaenge`, sonst änderte ihr Commit den Hash, den sie nennt. Ablauf: `make e2e`, danach `make kompatibilitaetsmatrix`, beide Dateien in einem Commit. **Grenze, die den ersten Vorschlag ändert:** ein Abgleich „Stand der Ergebnisdatei gleich aktueller Eingangs-Hash“ kann nicht in der committeten Sicht stehen. Der Hash ändert sich mit jeder Änderung unter `src/`, die Sicht wäre mit jeder Änderung veraltet, und ein Gate „committet gleich erzeugt“ verlangte bei jeder Quelltext-Änderung eine Neuerzeugung. Darum nennt die Sicht den **Stand und das Datum des Laufs aus der Datei** (Daten, nicht Urteil), und die Prüfung „Lauf veraltet?“ ist ein eigenes Werkzeug-Target (kein Gate, [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)), das den aktuellen Hash gegen die Datei hält und die Abweichung meldet; ein stilles „bestanden“ gibt es nicht, weil die Spalte immer „Lauf bei Stand X, Datum“ trägt. Offen für den Nutzer: ob das genügt oder ob die Sicht bei jedem Stand-Wechsel absichtlich neu erzeugt werden muss.

**Optionen (Kosten und Risiko in §6):**

- **A — zweite Ausgabe des vorhandenen Erzeugers** (Empfehlung): `tools/spec-abdeckung.sh` wird in einen geteilten Leser (Festlegungen, Tags, Berichte) und zwei Ausgaben getrennt; die Sicht gruppiert nach einem Feld `Kategorie` der Spec-Zeile (Nachricht, Datentyp, Fehler, Modulgrenze), liest den Marker der Zeile und die Ergebnisdatei. Kosten M (Erzeuger, Selbsttest, Fragment), die Migration hängt an der Spec.
- **B — dritter, eigener Erzeuger mit eigener Quelle:** verworfen (Nutzer: Quelle mit Aussagetexten ist nur verschobener Handtext).
- **C — die Matrix ganz ersetzen durch eine Gruppierung der Datei `docs/plan/spec-abdeckung.md`** (kein neuer Ausgang, die Datei `kompatibilitaetsmatrix.md` entfällt): am kleinsten. Folge für [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit): die Messmethode nennt die „dokumentierte Kompatibilitätsmatrix“; das Lastenheft ist Rang 1 und vertraglich, eine Streichung wäre eine Vertragsänderung des Nutzers. Mit A bleibt die Datei unter dem Namen bestehen und das Lastenheft unverändert.
- **D — nur Gate-Aufnahme von `matrix-zitate`:** ausgeschieden.

**Empfehlung:** A. Sie nutzt die vorhandene Mechanik und ihren Selbsttest, schreibt keine Aussage von Hand und lässt Lastenheft und Dateinamen stehen. C ist die Rückfallstufe, wenn der Nutzer die Granularität der Spec nicht erhöhen will: dann ist die Matrix tatsächlich nur eine Gruppierung und wird nicht eigens geführt.

**Konsequenzen (benannt, nicht geschrieben):** (1) **ADR** „Die Kompatibilitätsmatrix ist eine erzeugte Sicht auf die Spezifikation“ (Erzeuger, Kategorie-Feld, E2E-Ergebnisdatei, Stand-Grenze); (2) **Spec-Änderung**: Feld `Kategorie` an den Spec-Zeilen, neue Festlegungen für Batch-Flag, Transaktions-Pakettyp, Pfadwahl je Verbindung, „weitere Tokens“, Tabellen je Fall für Nachrichten, Tokens und Parametertypen, Marker an Zeilen, die ihn nicht tragen; (3) `AGENTS.md` §5 Regel 6 (die Matrix ist erzeugt) und Regel 7 (E2E-Ergebnisdatei als Einzelmessung); (4) `harness/README.md`: Zeile von `make matrix-zitate` entfällt, Zeile des Zielnamens kommt; (5) `tools/mk/matrix-zitate.mk` und `tools/matrix-zitate.sh` entfallen mit der Migration (Zitate gibt es nicht mehr, der Erzeuger prüft Tag gegen Bericht); (6) die 24 Dateien mit Verweis auf die Matrix: der Pfad bleibt (A), die Abschnittsverweise („7b“, „Lücke 12“) verlieren ihre Gegenstelle. Zeitdokumente bleiben unverändert (10 Reviews, 5 Slices in `done/`, 1 Beobachtungs-Beleg, [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) im Status `Accepted`: 17 Dateien); nachzuziehen sind 4 offene Slices, `docs/messungen/referenzabgleich.md`, die Welle und `tools/mk/matrix-zitate.mk` (7 Dateien).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- **Spec-Nachzug, Tags an Tests, Löschen der Handmatrix** — [slice-kompatibilitaetsmatrix-migration](slice-kompatibilitaetsmatrix-migration.md): hängt an den Entscheidungen des Nutzers über die Spec; dieser Slice baut den Erzeuger und prüft ihn am Mini-Bestand.
- **Gate-Aufnahme** — [slice-kompatibilitaetsmatrix-gate](slice-kompatibilitaetsmatrix-gate.md): braucht die verdrahtete Klasse `tests` aus [slice-stempel-migration](../done/slice-stempel-migration.md) und eine Messung der Gate-Zeit.
- **Bestehen der Referenzclient-Tests im Gate** — [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md): `make e2e` bleibt kein Gate; die Sicht trägt dessen letzten Lauf als Datum.
- **Cancel-Szenario und Messungen am echten SQL Server** — [slice-cancel-referenzclient-szenario](../done/slice-cancel-referenzclient-szenario.md) bzw. [slice-qa-vergleichslauf-d-messungen](../done/slice-qa-vergleichslauf-d-messungen.md) (Messreihe am Draht W-1 bis W-9, geschlossen); weitere Messungen haben keinen Träger (`BEO-TDS/messung-am-echten-sql-server-fehlt`).
- **Schreiben der ADR und der Änderungen an Spec und `AGENTS.md`** — Entscheidungen des Nutzers.

**Keine Mindestzahl.** Ein Slice mit *einem* echten Ausschluss ist besser als
einer mit vier erfundenen; die vier Klassen sind ein Suchraster, keine
Ausfüll-Liste. Suchreihenfolge: Was übernimmt ein **Folge-Slice** (mit
Kennung — und die Kennung muss den Punkt auch annehmen)? Was bleibt als
**Bestand** bewusst stehen (mit Begründung)? Was wäre ein **anderer Vorgang**?
Welche **Schicht** rührt der Slice nicht an?

Was hier steht, ist die Grenze, an der ein wachsender Slice sich messen lässt:
Wer später etwas mitnimmt, das hier ausgeschlossen war, hat den Plan
**geändert**, nicht nur ergänzt.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [ ] **Erzeuger mit Selbsttest:** der Leser von `tools/spec-abdeckung.sh` (Festlegungen, `@spec`-Tags, JUnit-XML) ist herausgelöst und geteilt; eine zweite Ausgabe schreibt die Sicht (Ziel wie `AGENTS.md` §5 Regel 6 zum Zeitpunkt der Umsetzung) mit festem Kopf, Gruppen nach `Kategorie`, Marker je Zeile, Beleg-Spalten „Tests (bestanden)“ und „Referenzclient (Lauf bei Stand X, Datum)“ aus der E2E-Ergebnisdatei. Make-Fragment im Muster von `tools/mk/spec-abdeckung.mk` (Zeile je Target in `harness/README.md`, kein `GATE_CHECKS`), Dockerfile-Stufen im Muster `spec-abdeckung`. Zwei Läufe auf denselben Eingängen sind byte-gleich (`cmp`); `make spec-abdeckung-selbsttest` bleibt grün.
- [ ] **Selbsttest mit Mutationen** an einem erzeugten Mini-Bestand (Mini-Spec, Mini-Tests, Mini-Berichte, Mini-Ergebnisdatei): umbenannter Test, fehlendes Tag (Festlegung der Kategorie „Nachricht/Datentyp“ ohne Beleg und ohne erklärten Marker → Exit 1), Tag auf unbekannter Kennung, doppelte Kennung, roter (failure und error), nicht gelaufener und übersprungener Test, doppelter Bericht; für die Ergebnisdatei: Zeile `FAILED` (zählt nicht), Tag ohne Ergebnis, Ergebnis ohne Tag (Exit 1), Datei ohne Kopfzeile oder ohne Stand (Exit 2); Marker der Spec außerhalb der Legende; der Gegenfall: Mini-Bestand ohne Mutation ist grün. Dazu `make e2e` schreibt die Ergebnisdatei (Stufe am Ende von `tools/e2e.sh`; `make skript-selbsttest` bleibt grün und prüft den Abbruch an der ersten roten Stufe weiter) und das Target „Lauf veraltet?“ meldet die Abweichung des Standes (kein Gate).
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update: `harness/README.md` (Sensors- und Werkzeug-Tabelle: neue Zeilen), `AGENTS.md` §5 Regel 6 und 7 nach Entscheidung des Nutzers.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `tools/spec-abdeckung.sh` | refactor | Leser (Festlegungen, Tags, Berichte) herauslösen, Ausgabe der Abdeckungsmatrix unverändert (Selbsttest ist Wächter) |
| `tools/kompatibilitaetsmatrix.sh` (Ausgabe der Sicht) und Selbsttest | neu | zweite Ausgabe über dem geteilten Leser; Mutationen laut DoD |
| `tools/mk/kompatibilitaetsmatrix.mk`, `Dockerfile` | neu / update | Targets und Stufen im Muster von `spec-abdeckung`; Ablage per `--output` in `mktemp -d` |
| `tools/e2e.sh`, `tools/kompatibilitaetstest.sh` | update | schreibt die Ergebnisdatei unter `docs/messungen/` |
| Target „Lauf veraltet?“ | neu | Eingangs-Hash der Klasse `tests` gegen Stand der Ergebnisdatei, Meldung, kein Gate |
| `harness/README.md` | update | Zeilen der Targets |

**Optional: Ansatz als Liste, wenn eine Zeile pro Datei nicht trägt** — z. B.
eine Schnittstellenänderung über viele gleichrangige Dateien mit derselben
Begründung, oder ein Ansatz, der sich nicht auf eine Datei herunterbrechen
lässt. Ergänzt die Tabelle, ersetzt sie nicht:

- Reihenfolge: geteilter Leser mit grünem `spec-abdeckung-selbsttest` zuerst, dann Sicht und Selbsttest, dann die Ergebnisdatei. Keine Quelle mit Aussagetexten entsteht an irgendeiner Stelle; Handtext in der Pipeline ist ein Fehler des Plans.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `make gates` ist auf `main` grün, und der Nutzer hat entschieden, ob die Matrix als Sicht (Option A) oder gar nicht mehr geführt wird (Option C) und ob die Spec ein Feld `Kategorie` und Tabellen je Fall bekommt. Keine Abhängigkeit von [slice-stempel-migration](../done/slice-stempel-migration.md).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): die Herauslösung des Lesers lässt sich nicht ohne Änderung des Verhaltens von `spec-abdeckung` zeigen; dann Zerlegung in Refactor und Sicht.
- `in-progress` → `open` (blockiert — Carveout?): die Entscheidung des Nutzers über `Kategorie` ist nach Start geändert.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

DoD vollständig, `make gates` grün, `make spec-abdeckung-selbsttest` und der neue Selbsttest grün, zweiter Lauf der Sicht auf denselben Eingängen meldet „unverändert“, Review-Report liegt vor, Closure-Notiz mit Lerneintrag (Sensor ergänzt oder benannte Spec-Lücke).

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Granularität:** ohne Tabellen je Fall in der Spec hat die Sicht rund 56 statt 141 Zeilen; ob der Nutzer das will, ist offen. — **Ausgang:** weiter offen bis zur Entscheidung; Spec-Lücke benannt (Batch-Flag, Transaktions-Pakettyp, Pfadwahl je Verbindung, „weitere Tokens“), kein Eintrag im Register, weil die Entscheidung beim Nutzer liegt.
- **Der Beleg trägt nur bis zum Tag** (`AGENTS.md` §3.8): der Erzeuger sieht Namen und Ergebnis, nicht den Inhalt der Assertions. — **Ausgang:** weiter offen: → BEO-TDS/test-belegt-nur-den-fake im Register (5 Dateien unter `evidence/`, verkörpert im Reviewer-Skill).
- **Stand-Vergleich der E2E-Datei steht nicht in der Sicht** (Reproduzierbarkeit, §1); wer `make e2e` nicht fährt, hat eine Sicht mit altem Datum, die „veraltet?“ nur meldet. — **Ausgang:** eingetreten als bekannte Grenze, im Kopf der Sicht benannt; Stand-Angaben nicht an zwei Orten: BEO-TDS/stand-angaben-in-matrix-und-slice-plan-driften (1×).
- **Marker der Spec fehlen oder sind uneinheitlich** (Setzung, nicht gemessen, Zielzustand, Festlegung: 112/87/20/64 Vorkommen in der Spec, nicht als Feld): die Sicht liest sie als Wort der Zeile. — **Ausgang:** weiter offen bis zur Umsetzung; der Erzeuger prüft die Legende.
- **Kein Gate bis zum Folge-Slice:** die Sicht kann bis dahin von Hand geändert werden (Evidenz 7× für die Spec-Matrix, 4× für CI-Zusagen). — **Ausgang:** weiter offen: → BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor im Register und BEO-TDS/zusage-ohne-sensor-in-der-ci-kette.
- **Die Stand-Zeile der Kompatibilitätsmatrix ist veraltet** (Verifikation `slice-resetconnection-zuruecksetzen`, L-2): der Satz „Seit `68598df` ändern nur KDoc, kein Verhalten“ stimmt nicht mehr (`git diff --stat 68598df..HEAD -- src/main` zeigt 101 Dateien), die Zahl „775 Testfälle in 106 Klassen“ steht bei 888; die Zeilen 9, 13, 3a, 4, 7b und 7c sind von Hand für `RESETCONNECTION` nachgezogen und gehören in die Quelle der Erzeugung. — **Ausgang:** weiter offen bis zur Umsetzung; der Erzeuger schreibt den Stand aus dem Lauf statt von Hand: → BEO-TDS/zahl-an-mehreren-orten-ohne-sensor im Register.
- **Ein Szenario des Referenzclients ohne Festlegung** erzeugt eine Spec-Lücke statt einer Zeile. — **Ausgang:** weiter offen bis zur Umsetzung.

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

Wird bei der Closure gefüllt.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Die berührten Pfade (`tools/`, `tools/mk`, `Dockerfile`, `docs/messungen/`, `referenztest-dotnet/`) liegen in dieser Sub-Area.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, Worktree auf `a9e7abf`, 2026-11-01); Zähler = Dateien unter `evidence/`:

- `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor` — 7×, offen, über der Schwelle; Risiko „Kein Gate“.
- `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` — 8×, offen, über der Schwelle, Entscheidung beim Nutzer; hier entfällt die zweite Fundstelle durch die Erzeugung der Sicht.
- `BEO-TDS/zusage-ohne-sensor-in-der-ci-kette` — 4×, offen.
- `BEO-TDS/test-belegt-nur-den-fake` — 5×, verkörpert.
- `BEO-TDS/zahl-an-mehreren-orten-ohne-sensor` — 2×, offen; die Zahl der Testfälle steht nur in der Sicht.
- `BEO-TDS/stand-angaben-in-matrix-und-slice-plan-driften` — 1×, offen.
- Kein Eintrag zu „Zitat“ oder „matrix-zitate“; sonst keine Treffer.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

<!-- Block für jede berührte Sub-Area duplizieren. Format identisch
mit dem im Baseline-Regelwerk §Ziel-Form: Sub-Area-Modus-Begründung
abgedruckten Block. -->

### Sub-Area: <Name>

- **Modus:** GF | BF | Hybrid
- **Konventionen-Dichte:** <Beleg aus `harness/conventions.md`,
  Adaptions-Block oder Code>
- **Phase-Reife:** Phase 0–5 <Begründung gegen die Phase × Modus-Matrix>
- **Evidenz-/Diskrepanz-Risiko:** <bei BF/Hybrid: was kann die
  Inventur sichtbar machen? bei GF: meist niedrig>
- **Reconciliation-Aufwand:** <Slice-Schätzung;
  Graduation-/Folge-Slice-Trigger>
