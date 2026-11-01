# Slice slice-kompatibilitaetsmatrix-erzeugen: Kompatibilitätsmatrix aus Tags und Testbericht erzeugen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage (Ziel der Welle: jede Festlegung durch Tests belegt; die Matrix ist die zweite handgepflegte Abdeckungs-Sicht neben der, die [slice-qa-spec-abdeckung](../done/slice-qa-spec-abdeckung.md) erzeugbar machte; die Welle ist noch flach in `planning/`, ihre Slice-Tabelle bekommt eine Zeile).

**Bezug:** [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit) (Messmethode: „dokumentierte Kompatibilitätsmatrix“), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) (Reproduzierbarkeit des Erzeugers), [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) (E2E-Läufe stehen nicht in den Gates), [ADR-0023](../../adr/0023-gate-klassen-und-stempel.md) und [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md) (Gate-Klassen; nur für den Gate-Schritt der Option B).

**Berührte Spec-Stellen:** — (keine Änderung an `spec/`; die Matrix ist ein Abdeckungs-Dokument nach `AGENTS.md` §5 Regel 6, die Spec nennt sie nur in der Messmethode von [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit)).

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

**Ziel:** `docs/plan/kompatibilitaetsmatrix.md` (Ort wie `AGENTS.md` §5 Regel 6 zum Zeitpunkt der Umsetzung) ist **reine Ausgabe** eines Erzeugers und wird nie von Hand geändert. Drei Eingänge: (1) eine **Quelldatei** mit einem Datensatz je Fall (Schlüssel, Abschnitt, Gegenstand, Aussage, Herkunft, erwarteter Beleg, Verweis auf Messung oder Ausnahme) und die Kopf-, Lese- und Pflegetexte als Felder — der einzige Ort für Handtext, als Daten; (2) das Tag `@matrix <Schlüssel>` an den Kotlin-Tests (KDoc wie `@spec`) und `[Trait("Matrix", "<Schlüssel>")]` an den .NET-Referenztests; (3) die JUnit-XML-Berichte der Stage `test`. Abgeleitet, nie von Hand: die Zitate `Klasse > Testfall`, der Status je Fall, Zählungen, Waisen. Eine Zeile ohne Tag, ein Tag ohne Zeile, ein doppelter Schlüssel und ein Fall, dessen erklärter Beleg nicht zum Befund passt, sind Exit 1 bzw. 2; nicht bestandene, nicht gelaufene und übersprungene Tests zählen nicht. Zwei Läufe liefern byte-gleiche Ausgabe. Die Aufnahme als Gate (Ausgabe gleich committeter Stand) ist das Ziel des Folge-Slice [slice-kompatibilitaetsmatrix-gate](slice-kompatibilitaetsmatrix-gate.md).

**Bestandsaufnahme (Stand 2026-11-01, Worktree auf `a9e7abf`; gezählt, nicht geschätzt):** `docs/plan/kompatibilitaetsmatrix.md` hat 270 Zeilen, davon 169 Tabellenzeilen = 14 Kopf-, 14 Trennzeilen und **141 Datenzeilen** in 14 Tabellen; 24 Commits im Verlauf der Datei (der Anlass nannte 20). 282 Zitate `Klasse > Testfall` (237 verschiedene, 58 Testklassen; keines mit `(` oder `[`, kein parametrisierter Test im Quelltext). Zerlegung der 141 Datenzeilen:

| Klasse | Abschnitte (Datenzeilen) | Zeilen | Zitate | Beispiel |
|---|---|---|---|---|
| **A. ableitbar** — Zeile mit mindestens einem Zitat in der Spalte „Test“: Zuordnung Gegenstand → bestandene Tests; Status und Herkunft stehen daneben | 1 (9 von 9), 2 (15 von 17), 2a (9 von 10), 3a (10 von 12), 3b (16 von 16), 4 (9 von 9), 6b (3 von 4) | 71 | 279 in diesen Tabellen (58 + 24 + 60 + 41 + 39 + 52 + 5) | „LOGIN7 (Parser)“: Spalte Test mit mehreren `Login7ParserTest > …` |
| **B. Zeile in diesen Tabellen ohne Zitat** (Sonderfall von A: muss „nicht belegt“ tragen, sonst ist die Zeile falsch) | 2 (2), 2a (1), 3a (2), 6b (1) | 6 | 0 | Regel des Abschnitts „Pflege“: eine Zeile „unterstützt“ ohne Testfall ist „nicht belegt“; ob alle sechs das tragen, prüft die erste Handlung der Umsetzung |
| **C. handgeschriebene Aussage ohne Test** — Grenze, Setzung, Lücke, Ist-Abgleich, Stufen-Vokabular | 5 Bekannte Lücken (16), 6a Ist-Abgleich `architecture.md` gegen `detekt.yml` (10), „Herkunft der Aussage“ (7) | 33 | 1 (Lücke 10 nennt `ColumnMappingTest > …` in der Zelle „Träger“) | Lücke 12 „Wirkung von ATTENTION“: „am Referenzclient und an einem echten Server nicht gemessen“ |
| **D. Referenzclient-Beleg** (E2E-Stufen 4 bis 7, Klassen `SzenarienTests`, `AnmeldungTests`, `LueckenTests`, `SynchronitaetTests`) | 7a (12), 7b (10), 7c (5), 7d (4) | 31 | 0 | „Anmeldung mit TLS: `Open` gelingt; `ServerVersion` `01.00.0000`“; der Wächter liest `…Tests >` nicht (er erkennt nur Klassennamen auf `Test`) |

Summe 71 + 6 + 33 + 31 = 141 Datenzeilen; 279 + 1 Zitate in Tabellen, 2 im Fließtext = 282. Also: **keine einzige Zeile ist reine Zitat-Zeile.** Auch die 71 Zeilen der Klasse A tragen in „Nachricht/Token/Typ“, „Status“ und „Herkunft“ Handtext; abgeleitet ist allein die Spalte „Test“. Gemessen an den Bytes: von 83 822 Byte der Datei stehen 21 267 Byte in Zitaten (25 %); die übrigen rund 75 % sind Handtext, der **in die Quelle als Daten** zieht, nicht wegfällt. Die Spalte „Herkunft“ hat ein festes Vokabular von 7 Stufen (Tabelle „Herkunft der Aussage“), wird aber in rund 30 Zellen der Abschnitte 1 bis 4 durch Freitext ergänzt (z. B. „Handvektor; Größen nicht gemessen“): Feld `herkunft` (Stufe, vom Erzeuger gegen die Stufen-Tabelle geprüft) plus Feld `herkunft-text`. Die SPEC kommt nur in 17 der 155 Tabellenzeilen vor (10 verschiedene `SPEC-*`): die Matrix gliedert nach Nachricht, Token und Datentyp, die Spezifikation nach Festlegung. **Zeilenschlüssel = SPEC-Kennung trägt daher nicht** (eine Festlegung deckt viele Zeilen, viele Zeilen keine Festlegung); die Quelle führt `spec:` als optionales Feld, das der Erzeuger gegen `spec/spezifikation.md` prüft (Leser der Festlegungen aus `tools/spec-abdeckung.sh`), und der Rest wird nicht aus der Spezifikation gewonnen.

Fließtext vor Abschnitt 1 und nach Abschnitt 7 ist nicht tabellarisch: Gegenstand, „Stand des Belegs“ (775 Testfälle in 106 Klassen, Basis `68598df`), „Herkunft der Bestandszeilen“, „Referenzclient“, „Pflege“, außerdem ein Absatz „Rot gesehen“ in 7d. Diese Teile sind Handtext mit Zahlen, die der Bericht kennt (Testfälle, Klassen) und ein Commit-Stand, den er nicht kennt.

**Wie die Schwestern es lösen (Vorlage der Mechanik):**

- `make spec-abdeckung` ([`tools/mk/spec-abdeckung.mk`](../../../../tools/mk/spec-abdeckung.mk), `tools/spec-abdeckung.sh`, Dockerfile-Stufen `spec-abdeckung`, `spec-abdeckung-export`, `spec-abdeckung-selbsttest`): **Eingänge** `SPEC_DATEI` (Festlegungen `SPEC-<NNN>` als Überschrift oder Tabellenzeile), `TEST_QUELLE` (KDoc-Tag `@spec SPEC-<NNN> [...]` an der nächsten `fun`), `BERICHTE` (JUnit-XML des Gradle-Laufs), `ZIEL`. **Urteil:** mindestens ein bestandener getaggter Test; roter, übersprungener, nicht gelaufener (umbenannter) Test zählt nicht, steht in eigenem Abschnitt; unbekannte Kennung im Tag wird gemeldet; `STRENG=1` macht Waisen zu Exit 1. **Ablage:** die Docker-Stufe schreibt Matrix und Waisenzahl per `--output type=local` in ein `mktemp -d` außerhalb des Repos, das Make-Fragment kopiert nur bei Abweichung (`cmp`). **Selbsttest** `tools/spec-abdeckung-selbsttest.sh`: erzeugter Mini-Bestand (Spezifikation, Mini-Tests, Mini-Berichte) mit je einem Fall für rot (failure und error), übersprungen, nicht gelaufen, unbekannte Kennung, mehrere `@spec`-Zeilen, Einzeilen-KDoc, `suspend fun`, verworfenen Tag, doppelten Bericht und Gate-Klasse. Kein Gate (Entscheidung des Nutzers vom 2026-10-30, Option D).
- `make e2e-abdeckung` (`harness/mk/e2e-abdeckung.mk`, `tools/harness/e2e-abdeckung.sh`, Werkzeug-Fragment, konvergent): liest **Deklarationen im Quelltext** von `tools/e2e.sh` (Stufen-Kopfzeilen), führt nichts aus, schreibt `docs/plan/e2e-abdeckung.md` nur bei Abweichung; „stabile Abdeckungs-Deklaration, kein Lauf-Beleg“; Kennungen als Code-Span; ein Waisen-Urteil fällt dort nicht. Kein Gate, mit der Begründung im Kopf des Fragments (ein Gate wäre rot, weil jemand eine Deklaration noch nicht schrieb).
- Beide haben die Form „Quelle der Wahrheit im Quelltext + Tag/Deklaration, Erzeuger schreibt das Dokument“. `make matrix-zitate` ([`tools/mk/matrix-zitate.mk`](../../../../tools/mk/matrix-zitate.mk), `tools/matrix-zitate.sh`) ist die umgekehrte Richtung (Handtext zitiert, Wächter prüft): `Klasse > Testfall` aus Inline-Code gegen `PASSED`-Zeilen der Stage `test` (`--no-cache-filter test`, also ein voller Testlauf), Fremdformen und fehlende Zitate Exit 1, ohne Selbsttest, kein Gate; Zitate brachen durch Umbenennungen zweimal (Testserver-Umbau).

**Mechanik (abgeleitet aus den Schwestern, gleiche Form):**

- **Quelle:** zeilenorientierte Datensätze (`Feld: Wert`, ein Fall bis zur Leerzeile), lesbar mit `sh`/`awk` wie die Eingänge von `tools/spec-abdeckung.sh`; kein neues Werkzeug im Image `basis`. Datensatz-Arten: `kopf` (Absätze vor Abschnitt 1, „Stand“-Satz ohne Commit), `stufe` (Herkunft-Vokabular), `abschnitt`, `fall`, `luecke` (Abschnitt 5), `abgleich` (6a), `messung` (Abschnitt 7), `pflege`. Schlüssel: lesbare Kebab-Kennung je Fall, stabil gegen Einfügungen (z. B. `login7-parser`).
- **Tags:** `@matrix <Schlüssel> [<Schlüssel> ...]` im KDoc-Block der Testfunktion, Haftung wie `@spec` (gemeinsamer Leser). .NET: `[Trait("Matrix", "<Schlüssel>")]` neben dem vorhandenen `[Trait("Kategorie", ...)]`; der Erzeuger liest die Trait-Zeilen aus `referenztest-dotnet/**/*.cs`.
- **Urteil je Fall** (Feld `erwartet`): `test` — mindestens ein bestandener getaggter Kotlin-Test, sonst Exit 1; `referenzclient` — mindestens ein .NET-Tag, **das Bestehen steht nicht im Eingang** und wird nicht behauptet; `keiner` — Setzung, Grenze oder Lücke, ausdrücklich ohne Test; ein getaggter bestandener Test an einem Fall mit `keiner` ist Exit 1 (die Erklärung ist veraltet). Das ersetzt die Handsätze „nicht belegt“ der sechs B-Zeilen durch eine Deklaration, die der Erzeuger gegen den Befund prüft; das Dokument zeigt den abgeleiteten Status (`belegt: n Testfälle` · `nicht belegt (erklärt)` · `Referenzclient: Tag vorhanden, Bestehen nur nach make e2e`).
- **Ehrliche Grenze der Referenzclient-Spalte:** Messzeilen (`messung: ...`) kommen aus dem E2E-Durchgang, den [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) aus den Gates hält und der nicht reproduzierbar ist. Der Wortlaut der Messung (z. B. `ServerVersion 01.00.0000`) bleibt Datum der Quelle mit `gemessen: 2026-10-31`; der Erzeuger bindet ihn nur an den Namen des .NET-Tests. Das Dokument sagt es im Kopf und in der Spalte. Die 31 Zeilen des Abschnitts 7 stehen 33 `[Fact]` in vier Klassen gegenüber (`AnmeldungTests` 11, `SzenarienTests` 13, `LueckenTests` 5, `SynchronitaetTests` 4); ob jeder Test genau einer Zeile entspricht, ist bei der Migration zu prüfen.
- **Ausgabe:** Markdown mit denselben Abschnitten und Tabellen wie heute (Spalte „Test“ erzeugt, sortiert nach Klasse und Testfall, damit zwei Läufe byte-gleich sind), Kopfzeile „erzeugt von `make kompatibilitaetsmatrix`, nicht von Hand ändern“, Stand-Zeile mit Testzahl und Klassenzahl des Berichts ohne Commit, Waisen-Zusammenfassung. `make matrix-zitate` entfällt als Prüfung: Zitate gibt es in der Quelle nicht mehr, der Erzeuger prüft Tag gegen Bericht; eine Zitat-Form in einem Handtext der Quelle (`Klasse > Testfall`) ist Exit 1.

**Optionen (Kosten und Risiko in §6):**

- **A — Matrix als Ausgabe, Quelle + Tags + Erzeuger** (Empfehlung; mit Zwischenstand „erzeugter Teil neben handgeschriebenem Aussagenteil“ als verworfener Vorläufer). Kosten: Quelle mit 141 Fällen und rund 13 Textabsätzen überführen; Tags an rund 237 Kotlin-Testfällen in rund 58 Klassen und an den .NET-Tests; Erzeuger, Selbsttest, Fragment. Risiko: Überführung ist mechanisch, aber groß; Diff gegen den Bestand fängt sie.
- **B — nur Zitat-Prüfung als Gate der Klasse `tests`, Matrix bleibt Handarbeit** ([ADR-0023](../../adr/0023-gate-klassen-und-stempel.md), [ADR-0024](../../adr/0024-lokale-stempel-zwei-klassen.md)): löst die Handpflege nicht (Präzisierung des Nutzers), kostet einen Cache-Bruch der Stage `test` je Lauf (`--no-cache-filter test`) oder ein neues Lesen der Berichte. Verworfen.
- **C — nur Gate-Aufnahme von `matrix-zitate` ohne Erzeuger:** ausgeschieden (Präzisierung des Nutzers).
- **A + Gate** (Ziel): Erzeuger-Ausgabe gleich committeter Stand als Gate der Klasse `tests`, damit die Datei nicht von Hand zu ändern ist; Folge-Slice, weil an [slice-stempel-migration](slice-stempel-migration.md) gebunden.

**Empfehlung:** A in diesem Slice, das Gate im Folge-Slice. A beseitigt die Ursache (Handpflege; eine Umbenennung macht den Fall beim nächsten Erzeugen laut statt still falsch), kostet für sich keine Gate-Zeit (kein Eintrag in `GATE_CHECKS`; Nutzerziel „`make gates` soll performanter werden“) und folgt der Mechanik von `spec-abdeckung`. Das Gate liest die vorhandenen Berichte der Stage `test` und vergleicht mit `cmp`; die Kosten sind nicht gemessen (Folge-Slice), die Aufnahme geschieht nur über die Stempelklasse `tests`, ohne Lockerung eines Gates ([AGENTS.md](../../../../AGENTS.md) §3.6).

**Entscheidungen außerhalb des Slice (benannt, nicht geschrieben):** (1) eine **ADR** „Die Kompatibilitätsmatrix ist erzeugte Ausgabe“ (Quelle, Tag, Urteil je Fall, Referenzclient-Spalte nur als Tag; die Nummer vergibt der Schreibende, der Index in `docs/plan/adr/README.md` wird dabei fortgeschrieben); (2) eine Änderung von `AGENTS.md` §5 Regel 6 (Abdeckungs-Dokumente: die Matrix ist erzeugt, die Quelle liegt als Daten dort, Regel 7 bleibt unberührt, weil `docs/messungen/referenzabgleich.md` nur von Messzeilen verlinkt wird); (3) die Struktur-Entscheidung des Nutzers über den Ort (ein Verzeichnis für Abdeckungs-Dokumente ist vorgeschlagen, nicht entschieden). Das Lastenheft ändert sich nicht: „dokumentierte Kompatibilitätsmatrix“ in der Messmethode von [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit) lässt eine erzeugte Datei zu; die Belegform („ein Beleg ist ein bestandener Testfall“) steht in der Matrix und in Regel 6, nicht im Lastenheft.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- **Gate-Aufnahme** — [slice-kompatibilitaetsmatrix-gate](slice-kompatibilitaetsmatrix-gate.md): braucht die verdrahtete Klasse `tests` aus [slice-stempel-migration](slice-stempel-migration.md) und eine Messung der Gate-Zeit; dieser Slice hängt davon nicht ab.
- **Prüfung, dass der Referenzclient die Aussagen annimmt** — Bestehen der .NET-Tests ist Sache von `make kompatibilitaet` ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)); der Erzeuger liest Tags, keine Ergebnisse des E2E-Durchgangs.
- **Cancel-Szenario und Messungen am echten SQL Server** — [slice-cancel-referenzclient-szenario](slice-cancel-referenzclient-szenario.md) bzw. [slice-qa-vergleichslauf-d-messungen](slice-qa-vergleichslauf-d-messungen.md); dieser Slice ändert keine Aussage der Matrix, nur woher sie kommt.
- **Verhaltensänderung von `tools/spec-abdeckung.sh`** über das Herauslösen des gemeinsamen Tag-Lesers hinaus — dessen Selbsttest ist der Wächter.
- **Schreiben der ADR und der Änderung von `AGENTS.md` §5** — Entscheidungen des Nutzers (siehe Ziel); der Slice setzt sie voraus.

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

- [ ] **Erzeuger mit Selbsttest:** `tools/kompatibilitaetsmatrix.sh` liest Quelle, Tags (Kotlin-KDoc und .NET-Trait) und JUnit-XML und schreibt die Matrix (Eingänge als Umgebungsvariablen wie bei `tools/spec-abdeckung.sh`, Vorgabe streng); Make-Fragment `tools/mk/kompatibilitaetsmatrix.mk` (`kompatibilitaetsmatrix`, `kompatibilitaetsmatrix-selbsttest`, Zeilen in `harness/README.md`, kein `GATE_CHECKS`), Dockerfile-Stufen im Muster `spec-abdeckung` (erben von `test`, Export per `--output` in `mktemp -d`, Kopie nur bei Abweichung). Zwei Läufe auf demselben Bericht liefern byte-gleiche Ausgabe (`cmp`). Der Selbsttest `tools/kompatibilitaetsmatrix-selbsttest.sh` arbeitet an einem erzeugten Mini-Bestand und belegt je Mutation Exit-Code und Meldung: umbenannter Test (Tag ohne gleichnamigen Testfall), fehlendes Tag (Fall mit `erwartet: test` ohne Test), Tag auf unbekanntem Schlüssel, doppelter Schlüssel, roter Test (failure und error), nicht gelaufener, übersprungener Test, doppelter Bericht (schlechtester zählt), `@matrix` neben `@spec` im selben KDoc-Block, `erwartet: keiner` mit bestandenem Tag, .NET-Trait ohne Fall, unbekannte Herkunfts-Stufe, Zitat-Form im Handtext; dazu der Gegenfall: Mini-Bestand ohne Mutation ist grün und die Ausgabe bei zweimaligem Lauf byte-gleich. `make spec-abdeckung-selbsttest` bleibt grün (der Tag-Leser ist geteilt).
- [ ] **Migration der Matrix ohne Informationsverlust:** (1) ein einmaliges Überführungswerkzeug (nicht im Repo behalten, wenn es nach der Überführung keinen Leser hat) liest die heutige Matrix und schreibt Quelle und Zuordnung Testfall → Schlüssel; (2) die Tags stehen an rund 237 Kotlin-Testfällen und den .NET-Tests (reine Kommentar- bzw. Attribut-Änderung, kein Testfall wird umbenannt, `make test` unverändert grün); (3) der **Diff** der Ausgabe gegen die heutige Matrix ist leer bis auf die benannten Unterschiede (Kopfzeile, Stand-Zeile ohne Commit, Spaltenreihenfolge der Zitate, Status-Ableitung) — jede weitere Abweichung ist eine Zeile im Diff-Protokoll von §7; (4) die erzeugte Datei ersetzt die Handdatei, `make matrix-zitate` und `tools/matrix-zitate.sh` entfallen, Verweise auf den alten Pfad und die Handpflege sind nachgezogen. Ort wie `AGENTS.md` §5 Regel 6 zum Zeitpunkt der Umsetzung (die Struktur-Entscheidung des Nutzers steht aus).
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update: `harness/README.md` (Sensors-Tabelle: neue Zeilen, Zeile von `make matrix-zitate` entfällt), `AGENTS.md` §5 Regel 6 (nach der Entscheidung des Nutzers), Dateien, die den Pfad der Matrix nennen.
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
| `tools/kompatibilitaetsmatrix.sh` | neu | Erzeuger; Quelle + Tags + Berichte → Matrix; Muster `tools/spec-abdeckung.sh` |
| gemeinsamer Tag-Leser (z. B. `tools/abdeckung-tags.sh`) | neu / refactor | `spec-abdeckung.sh` und der Erzeuger lesen KDoc-Tags mit derselben Haftungsregel (nächste `fun`, Einzeilen-KDoc ungelesen); eine zweite Kopie der Regel wäre selbst die Drift, die dieser Slice beseitigt |
| `tools/kompatibilitaetsmatrix-selbsttest.sh` | neu | Mutationen laut DoD, Stil von `tools/spec-abdeckung-selbsttest.sh` |
| `tools/mk/kompatibilitaetsmatrix.mk`, `Dockerfile` | neu / update | Targets und Stufen im Muster von `tools/mk/spec-abdeckung.mk`; kein Eintrag in `GATE_CHECKS` |
| Quelle der Matrix (Ort wie `AGENTS.md` §5 Regel 6 zum Zeitpunkt der Umsetzung) | neu | Datensätze: 141 Fälle, 16 Lücken, 10 Abgleich-Zeilen, 7 Stufen, 31 Messungen, Kopf- und Pflegetexte |
| `docs/plan/kompatibilitaetsmatrix.md` | wird erzeugt | reine Ausgabe |
| Tests in `src/test`, `src/integrator`, `src/testserver`; `referenztest-dotnet/**/*.cs` | update (Tag, Trait) | `@matrix <Schlüssel>` neben `@spec`; Trait neben `Kategorie`; nur Kommentar bzw. Attribut |
| `tools/matrix-zitate.sh`, `tools/mk/matrix-zitate.mk` | entfernt | Zitate gibt es nicht mehr als Eingabe |
| `harness/README.md`, `AGENTS.md` §5 | update | Zeilen der Targets; Regel 6 nach Nutzerentscheidung |

**Optional: Ansatz als Liste, wenn eine Zeile pro Datei nicht trägt** — z. B.
eine Schnittstellenänderung über viele gleichrangige Dateien mit derselben
Begründung, oder ein Ansatz, der sich nicht auf eine Datei herunterbrechen
lässt. Ergänzt die Tabelle, ersetzt sie nicht:

- Reihenfolge der Umsetzung: Erzeuger und Selbsttest zuerst am Mini-Bestand (Liefer-Punkt 1), danach Überführung, Tags, Diff (Liefer-Punkt 2). Ist Punkt 2 zu groß (270 Tag-Stellen plus 141 Fälle), Zerlegung in `…-erzeugen` (Punkt 1) und `…-migration`; das ist eine Rückführung nach `next` (§4).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `make gates` ist auf `main` grün, und die Struktur-Entscheidung des Nutzers über den Ort der Abdeckungs-Dokumente (`AGENTS.md` §5 Regel 6) sowie die ADR „Matrix ist Ausgabe“ sind getroffen oder ausdrücklich zurückgestellt. Eine Abhängigkeit von [slice-stempel-migration](slice-stempel-migration.md) besteht nicht; sie gilt erst für [slice-kompatibilitaetsmatrix-gate](slice-kompatibilitaetsmatrix-gate.md).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): Liefer-Punkt 2 (Überführung, 270 Tag-Stellen, Diff) ist größer als der Erzeuger; Zerlegung in Erzeuger mit Selbsttest und Migration.
- `in-progress` → `open` (blockiert — Carveout?): die Ortsentscheidung wird nach Start geändert, oder der gemeinsame Tag-Leser lässt `make spec-abdeckung-selbsttest` rot werden und der Grund liegt im Selbsttest der Schwester.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

DoD vollständig, `make gates` grün, `make kompatibilitaetsmatrix-selbsttest` und `make spec-abdeckung-selbsttest` grün, zweiter Lauf von `make kompatibilitaetsmatrix` auf dem committeten Stand meldet „unverändert“, Diff-Protokoll liegt in §7, Review-Report liegt vor, Closure-Notiz mit Lerneintrag geschrieben (Sensor geschärft oder ergänzt, oder benannte Spec-Lücke). Ohne Lerneintrag ist der Slice nur abgelegt.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Tag-Haftung und geteilter Leser:** `@matrix` neben `@spec` im selben KDoc-Block; die Haftungsregel („Folgezeile ohne eigenes `@spec` gehört nicht zum Tag“) kann das neue Tag verschlucken oder das alte brechen. Gegenmaßnahme: Mutationsfall im Selbsttest beider Erzeuger. — **Ausgang:** weiter offen bis zur Umsetzung; der Ausgang steht bei der Closure.
- **Der Beleg trägt nur bis zum Tag** (`AGENTS.md` §3.8): ein bestandener getaggter Test belegt die Zeile nur, wenn seine Assertions die Aussage tragen; der Erzeuger sieht den Namen, nicht den Inhalt. Dasselbe gilt für die heutige Handmatrix und für `spec-abdeckung`. Die Ausgabe sagt es in einem Satz. — **Ausgang:** weiter offen: → BEO-TDS/test-belegt-nur-den-fake im Register (5×, verkörpert im Reviewer-Skill; die Prüfung bleibt Sache des Reviews).
- **Quelle und Test driften auseinander** — die Kernfrage des Anlasses; Gegenmaßnahme sind die Sperren (Zeile ohne Tag, Tag ohne Zeile, doppelter Schlüssel, `erwartet` gegen Befund). Nicht gesperrt bleibt, dass ein Handsatz in der Quelle (z. B. Lücke 3 „Abweichung von `SPEC-003`“) nach einer Änderung der Spec veraltet; das optionale Feld `spec:` fängt nur eine entfallene Kennung. — **Ausgang:** weiter offen: → BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen im Register (8 Dateien unter `evidence/`, über der Schwelle, Entscheidung beim Nutzer; dieser Slice verkörpert dazu keine Regel).
- **Stand-Angaben:** die Zeile „Stand des Belegs“ nennt heute einen Commit (`68598df`) und eine Prüfung per Diff über `src/main`; ein Erzeuger kennt den Commit nicht und darf ihn nicht einführen (die Ausgabe wäre nicht byte-gleich). Die erzeugte Datei nennt Testzahl und Klassenzahl des Berichts; der Quelltext-Stand ist Handsatz im Pflege-Abschnitt oder entfällt. Der Plan nennt keinen zweiten Stand im Fließtext. — **Ausgang:** weiter offen: → BEO-TDS/stand-angaben-in-matrix-und-slice-plan-driften im Register (1×).
- **Kein Gate, also kein Sensor gegen die von Hand bearbeitete Ausgabe** — bis zum Folge-Slice kann jemand die erzeugte Datei ändern; Evidenz 7× für die Spec-Matrix, 4× für CI-Zusagen. Die Kosten des Gates sind **nicht gemessen** und stehen dort. — **Ausgang:** eingetreten als Folge-Slice slice-kompatibilitaetsmatrix-gate; weiter offen: → BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor im Register (7×) und BEO-TDS/zusage-ohne-sensor-in-der-ci-kette (4×).
- **Zahl an mehreren Orten:** die Matrix nennt „775 Testfälle in 106 Klassen“; nach der Umstellung steht die Zahl nur in der erzeugten Datei; Handanteile wiederholen keine Zahl des Berichts. — **Ausgang:** weiter offen: → BEO-TDS/zahl-an-mehreren-orten-ohne-sensor im Register (2 Dateien unter `evidence/`).
- **Referenzclient-Spalte trägt weniger als sie aussieht:** der Tag belegt, dass ein .NET-Test für den Fall existiert, nicht dass er besteht; die Messwerte bleiben Daten der Quelle (31 Zeilen, ein Lauf je Zeile). — **Ausgang:** eingetreten als bekannte Grenze, im erzeugten Dokument in Kopf und Spalte benannt; geprüft wird nur beim nächsten `make kompatibilitaet`.
- **Aufwand der Überführung:** 141 Fälle in Schlüssel, rund 237 Kotlin-Testfälle und die .NET-Tests in Tags, Handtext in Daten; Größenordnung L für die Migration, M für den Erzeuger (Schätzung, nicht gemessen). — **Ausgang:** weiter offen bis zur Umsetzung; der Ausgang steht bei der Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Die berührten Pfade (`tools/`, `tools/mk`, `Dockerfile`, KDoc in `src/*/kotlin`, `docs/plan/`) liegen in dieser einen Sub-Area; eine Ausdifferenzierung ist nicht nötig.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, Stand des Worktrees auf `a9e7abf` am 2026-11-01); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor` — 7×, offen, über der Schwelle, nicht verkörpert; Treffer für die committete Spec-Matrix; Risiko „Kein Gate“.
- `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` — 8×, offen, über der Schwelle, Entscheidung beim Nutzer; Risiko „Erzeugter Teil und Handtext driften“.
- `BEO-TDS/zusage-ohne-sensor-in-der-ci-kette` — 4×, offen; Risiko „Kein Gate“.
- `BEO-TDS/test-belegt-nur-den-fake` — 5×, verkörpert (Reviewer-Skill); Risiko „Der Beleg trägt nur bis zum Tag“.
- `BEO-TDS/zahl-an-mehreren-orten-ohne-sensor` — 2×, offen; Risiko „Zahl an mehreren Orten“.
- `BEO-TDS/stand-angaben-in-matrix-und-slice-plan-driften` — 1×, offen; Risiko „Stand-Angaben“.
- Weitere Einträge mit „Matrix“ im Text, ohne Bezug zu diesem Schnitt: `spec-tag-nennt-lastenheft-kennung` (1×), `spec-nachzug-nach-messung-offen`, `zustandspunkte-nach-fehler-ohne-entscheidung`; kein Eintrag trägt „Zitat“ oder „matrix-zitate“ im Namen.
- Ohne Treffer: die übrigen Einträge des Registers. Ob die Umsetzung eine weitere Evidenz liefert, steht in §7.

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
