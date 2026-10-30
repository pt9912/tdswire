# Verifikation slice-qa-spec-abdeckung (Modul 11)

**Gegenstand:** `slice-qa-spec-abdeckung` (Welle welle-qualitaet-grundlage), gemergt mit 36e1ed0. Bezug: [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit).
**Rolle:** Verifier (gegen DoD und Spec, nicht Diff-Review).
**Stand der Läufe:** Working Tree auf 8ad3ef0 (Beginn), Mutationskopien aus `git archive 8ad3ef0`; `make gates` am Ende auf d2feffb (HEAD wanderte während der Verifikation durch parallele Commits; `tools/`, `Dockerfile`, `tools/mk/`, Lock-Dateien sind zwischen 8ad3ef0 und HEAD unverändert).
**Methode:** alle Läufe Docker-only über `make`; Mutationen je in frischer Kopie im Scratchpad; Repo am Ende sauber (`git status`: nur `.claude/worktrees/` und diese Datei).

## Urteil

Der Slice ist **funktional bestätigt, aber nicht schließbar**: Der Erzeuger arbeitet wie zugesagt, 14 von 15 Mutationen werden gefangen, die Matrix ist deterministisch. Offen sind die Dokumentation der Entscheidung (Option D) im Slice, die Festlegung der Konvention an einem Ort außerhalb des Erzeugers, die committete Matrix (veraltet) und zwei Härtungsbefunde.

## DoD Punkt für Punkt

| # | DoD-Punkt | Urteil | Beleg |
|---|---|---|---|
| 1 | Konvention `@spec SPEC-<NNN>` festgelegt; Selbsttest belegt, dass umbenannter/verwaister Test auffällt | **teilweise bestätigt** | Erzeuger kann sie, Selbsttest belegt Umbenennung (stale Bericht) und Tag auf unbekannte Kennung (SPEC-099). Festgelegt ist sie aber **nur** im Kopf von `tools/spec-abdeckung.sh` und im Kopf der erzeugten Matrix (siehe F-2). |
| 2 | Erzeuger schreibt Matrix nach `docs/plan/`, nur bestandene Tests zählen | **bestätigt** | Lauf siehe unten; Mutationen M1, M3, S3, S5, S7 fangen rot / übersprungen / nicht gelaufen. |
| 3 | Entscheidung, ob Waisen das Gate brechen, getroffen; bei Gate Bindung in `harness/README.md` | **in der Sache bestätigt, im Slice nicht nachgezogen** | Nutzer-Entscheidung 2026-10-30 = Option D (berichten, kein Gate, `SPEC_ABDECKUNG_STRENG=1` optional). Umsetzung stimmt: kein Eintrag in `GATE_CHECKS`, README-Zeilen 82/83 tragen „kein Gate“ (siehe F-1 zum Slice-Text). |
| 4 | `make gates` grün | **bestätigt** | `make gates` Exit 0 (durch Cache 6 s). |
| 5 | Review durchgeführt, Report unter `docs/reviews/` | **bestätigt (mit Vorbehalt)** | `docs/reviews/2026-10-30-36e1ed0-slice-qa-spec-abdeckung.md` liegt vor, zum Prüfzeitpunkt untracked/neu (Commit d2feffb). |
| 6 | Doku-Update bei öffentlichem Vertrag | **bestätigt** | README-Zeilen für beide Targets; Nutzerkonvention fehlt in zentraler Doku (F-2). |
| 7 | Closure-Notiz mit Lerneintrag | **nicht bestätigt** | §7 „Wird bei der Closure gefüllt.“ |
| 8 | Beobachtungs-Register fortgeschrieben | **nicht bestätigt** | Nicht erledigt, nichts in §7 notiert. |
| 9 | Jedes Risiko aus §6 trägt Ausgang | **nicht bestätigt** | Beide Risiken stehen auf „offen bis zur Closure“. |
| 10 | Drei Paarungen getragen | **nicht prüfbar** | hängt an Closure-Notiz (7). |

Alle Haken in §2 stehen noch auf `[ ]`; der Slice liegt in `in-progress/`. Punkte 7–10 sind Closure-Pflichten, keine Mängel der Umsetzung, aber vor `done/` zu erledigen.

## Prüfungen im Einzelnen

### (1) Konvention `@spec`

`grep -rn "@spec"` über `*.md`, `*.yml`, `*.mk`, `Dockerfile`, `*.sh` (ohne Baseline, Reviews, Worktrees): Treffer nur in `tools/spec-abdeckung.sh` (Kopf, Zeile 6, und Parser), in `tools/spec-abdeckung-selbsttest.sh`, im Target-Kommentar `tools/mk/spec-abdeckung.mk`, in `harness/README.md` Zeile 82 (nur Erwähnung), im Dockerfile-Kommentar und in der erzeugten Matrix-Kopfzeile. Nicht festgelegt in `spec/spezifikation.md`, `AGENTS.md`, `harness/conventions.md` oder `docs/user/`. Offene Slices zitieren „Konvention nach `slice-qa-spec-abdeckung`“ (z. B. `slice-transport-listener-lebenszyklus`, `slice-antwort-mehrteilig-output`); die Quelle der Konvention ist damit ein Skript-Kopf. Zusätzlich im Parser unterstützt, aber nirgends gesagt: mehrere `@spec`-Zeilen untereinander (so benutzt in `UserListAuthAdapterTest`). Befund F-2.

### (2) Läufe, Zahlen, Determinismus

- `make spec-abdeckung`, Exit 0, `31 Festlegung(en) ohne Nachweis`, schrieb die Matrix neu.
- Zweiter Lauf: `unveraendert`; `cmp` der beiden Ergebnisse identisch (deterministisch). Der Quelltext-Bestand ändert sich zwischen den Läufen nicht, Reihenfolge ist durch `sort` und Spec-Reihenfolge fest.
- `make spec-abdeckung-selbsttest`: Exit 0 (Docker-Cache, kein Neubau). Die Selbsttest-Fixture ist in den Mutationen M1 bis M9 jeweils neu gebaut worden und hat dort greifen müssen (siehe unten).
- **Differenz committete zu frischer Matrix (Bestand 8ad3ef0):**

| Kennzahl | committet (1714dc4) | frisch |
|---|---|---|
| mit bestandenem Test | 10 von 51 | 14 von 51 |
| Gate-Festlegungen ungemessen | 8 | 6 |
| ohne Nachweis | 33 | 31 |

Geändert: SPEC-003 16 → 26 Modul-Tests; SPEC-028 46 → 53; SPEC-030 2 → 14; SPEC-007 2 → 3; SPEC-046 2 → 5; SPEC-027 9 → 21; SPEC-012 und SPEC-051 neu nachgewiesen (Auth-Tests); SPEC-024 (Unit-Test-Gate) und SPEC-042 (Build-Gate) nun durch Modul-Tests `MemoryChannelTest`/`CoroutinesPinTest` „nachgewiesen“ statt „Gate-Festlegung, ungemessen“. Die committete Matrix ist veraltet. Das ist erwartetes Verhalten eines nicht gegateten Berichts, aber die Datei im Repo behauptet einen Stand, der nicht mehr gilt (F-3). Der frische Lauf wurde nicht committet; `git checkout docs/plan/spec-abdeckung.md` stellte den Stand wieder her.
- Hinweis zu SPEC-024/042: Die Festlegungen der Klasse „gate“ (Make-Target) gelten nach einem getaggten Modul-Test als „nachgewiesen“. Die Grenze „Tag sagt, was der Autor meint“ steht im Matrix-Kopf; ob `CoroutinesPinTest` das Build-Gate belegt, urteilt der Leser (F-6).

### (3) Mutationen

Je frische Kopie aus `git archive 8ad3ef0`; „gefangen“ = Selbsttest schlägt fehl bzw. Matrix/Exit zeigen die erwartete Änderung.

| # | Mutation | Erwartung | Ergebnis |
|---|---|---|---|
| M1 | Erzeuger: `failure|error` im Bericht ignoriert (rote Tests als bestanden) | Selbsttest rot | **gefangen** (FEHLT: SPEC-001 …) |
| M2 | Erzeuger: Tabellenzeilen-Erkennung entfernt | Selbsttest rot | **gefangen** (FEHLT: Zählung trennt Waisen/Gate) |
| M3 | Erzeuger: `<skipped>` ignoriert | Selbsttest rot | **gefangen** |
| M4 | Erzeuger: Tag auf unbekannte Kennung wird nicht gemeldet | Selbsttest rot | **gefangen** (FEHLT: SPEC-099) |
| M5 | Erzeuger: STRENG-Schalter wirkungslos | Selbsttest rot | **gefangen** („STRENG=1 hätte Exit 1 liefern müssen“) |
| M6 | Erzeuger: `fehler()` liefert Exit 0 statt 2 | Selbsttest rot | **gefangen** („ohne Berichte muss Exit 2 liefern“) |
| M7 | Erzeuger: Gate-Klasse nie vergeben | Selbsttest rot | **gefangen** |
| M8 | Erzeuger: ein neuer KDoc-Block verwirft einen offenen Tag nicht mehr | Selbsttest rot | **ÜBERLEBT** (Exit 0), Selbsttest deckt diese zugesagte Parser-Eigenschaft nicht (F-4) |
| M9 | Erzeuger: Methodenname beim Abgleich ignoriert | Selbsttest rot | **gefangen** |
| S1 | Testbestand: `@spec SPEC-012` eines bestandenen Tests entfernt | Waisen 31 → 32 | **bestätigt** (32; SPEC-012 „OHNE NACHWEIS“) |
| S3 | Testbestand: `@Test` von einem getaggten Test (SPEC-024) entfernt | „nicht gelaufen“, zählt nicht | **bestätigt** (Nicht bestanden 1, Zeile „nicht gelaufen (kein Ergebnis im Bericht)“) |
| S4 | Testbestand: unbekannte Kennung im Tag (SPEC-012 → SPEC-999) | Abschnitt „Tags auf unbekannte Festlegungen“, SPEC-012 Waise | **bestätigt** (32) |
| S5 | Testbestand: Assertion gedreht, getaggter Test rot | „rot“, zählt nicht, Lauf bricht Matrix nicht ab | **bestätigt** (SPEC-012 und SPEC-051 als „rot“, SPEC-012 Waise) |
| S6 | `SPEC_ABDECKUNG_STRENG=1` bei 31 Waisen | Exit 1, Matrix vorher geschrieben | **bestätigt** (Make-Exit 2 als Folge von Rezept-Exit 1; Meldung „STRENG=1 und Festlegungen ohne Nachweis“) |
| S7 | Testbestand: `@kotlin.test.Ignore` auf getaggtem Test | „übersprungen“, zählt nicht | **bestätigt** |
| S8 | leere Berichte (Erzeuger direkt mit leerem `BERICHTE`) | Exit 2, keine Matrix | **bestätigt** (Exit 2, `z.md` nicht angelegt; Docker-Anteil entfällt, Skript ist reines `sh`/`awk`) |

15 Mutationen (M1 bis M9, S1, S3 bis S8; S2 nicht vergeben): 14 gefangen bzw. bestätigt, 1 überlebt (M8). Hinweis zu S3: SPEC-024 bleibt „nachgewiesen“, weil zwei weitere Tests bestehen; nur die Spalte „Nicht bestanden“ und der Abschnitt zeigen den Verlust. Eine Umbenennung im Quelltext (alter Name im Bericht) ist in der Live-Pipeline nicht herstellbar, weil der Testlauf die Berichte frisch erzeugt; die Zusage „umbenannter Test verliert Nachweis“ gilt nur für veraltete Berichte (F-5).

### (4) d-check und Ablage

- `make docs-check` mit der frisch erzeugten Matrix: `176 Datei(en) geprüft, 0 Befund(e)`.
- `.harness/state/spec-abdeckung` nach `make spec-abdeckung` weggeräumt (bei Erfolg und bei STRENG-Abbruch: der `rm` steht vor dem `exit 1`).
- **Befund F-7:** Liegt die Ablage, Modus 700, einmal da (selbst gemessen: `drwx------` für das Verzeichnis), bricht `make docs-check` fail-closed mit `permission denied` (DC-FA-TGT-001). Das Räumen hängt am Durchlaufen des Rezepts. Bricht das Rezept zwischen `docker build` und `rm -rf` ab (STRG-C, `cp` scheitert, `cat waisen` scheitert) oder läuft `make docs-check` zeitgleich in einem anderen Terminal, rotet `docs-check`, bis jemand die Ablage per Hand räumt (der Folgelauf `make spec-abdeckung` räumt sie mit seinem ersten `rm -rf`). Ich habe die Ablage im Test selbst angelegt und danach entfernt.

### (5) Out-of-Scope

- `GATE_CHECKS`: kein Treffer für `spec-abdeckung` in `repo.mk`, `harness/mk/*.mk`, `tools/mk/*.mk`; das Fragment setzt `GATE_CHECKS` nicht. Bestätigt.
- `harness/README.md` Zeilen 82/83: Bindung „kein Gate“. Bestätigt.
- Merge 36e1ed0 ändert nur Dockerfile, Matrix, README, `tools/mk/spec-abdeckung.mk`, zwei Skripte (475 Zeilen, keine Löschung). `gradle.lockfile`, `settings-gradle.lockfile`, `gradle/verification-metadata.xml`: keine Änderung. Bestätigt.
- Die TDS-Requirements-Traceability-Matrix wird nicht abgespeichert; `docs/plan/spec-abdeckung.md` ist die SPEC-Sicht. Bestätigt.

### (6) Aussagen über Messung hinaus (AGENTS.md §3.8)

Matrix-Kopf, Skript-Kopf und Slice-Ziel nennen die Grenze („Tag nennt die Festlegung, die der Autor meint; Assertions urteilt der Leser“). Nicht gedeckt:
- Matrix-Kopf und Selbsttest-Kopf: „Ein umbenannter Test verliert seinen Nachweis“: gilt nur gegen veraltete Berichte (F-5).
- Slice-Ziel: „Jede Festlegung der Spezifikation ist … nachgewiesen“: Ist-Stand 14 von 51; die Zeile ist als Ziel formuliert, kein Befund.
- `tools/mk/spec-abdeckung.mk` Kopf: „ob … entscheidet der Nutzer, nicht dieses Fragment“: Kommentar beschreibt eine Entscheidung als offen, die getroffen ist (F-1).
- `harness/README.md` Zeile 82: „je `SPEC-*` bestandene `@spec`-Tests nach Art“: korrekt.
- Dockerfile: „rote Tests brechen die Stage nicht“: durch S5 bestätigt.

### (7) Laufzeit

Erster Lauf nach Teständerung (Stage `spec-abdeckung`, Gradle-Layer teilweise warm): **56 s** Wanduhr. Wiederholung ohne Änderung: **0,75 s** (reiner Docker-Cache). Mutationsläufe im Bereich 1 bis 3 Minuten (Gradle-Neubau der geänderten Datei). Der Wert „25 s“ des Implementers ist weder bestätigt noch widerlegt: er hängt vom Cache-Zustand ab und liegt in diesem Lauf bei etwa dem Doppelten.

## Befunde

### HIGH

Keine.

### MEDIUM

- **F-1 Entscheidung Option D steht nicht im Slice; zwei Texte widersprechen ihr.** DoD-Haken „Entscheidung … getroffen“ ist offen; §6 Risiko 2 trägt „Ausgang: offen“ und beschreibt ein rotes Gate als Möglichkeit; der Kopfkommentar von `tools/mk/spec-abdeckung.mk` sagt „entscheidet der Nutzer“. Nachzuziehen: Haken setzen mit dem Beleg „Nutzer, 2026-10-30, Option D“; Risiko 2 mit Ausgang „entfallen (Option D: kein Gate)“; §7 nennt die Entscheidung. Kommentar im Fragment auf den Ist-Zustand umschreiben (§3.7: beschreibt, was da ist). Die Umsetzung selbst entspricht Option D.
- **F-2 Konvention nur im Erzeugerkopf und in der Matrix-Kopfzeile festgelegt.** Der Slice fordert „Konvention festgelegt“; Folge-Slices und `AGENTS.md`-Leser finden die Regel nicht. Einen kanonischen Ort nennen (Spezifikation oder `AGENTS.md`/`harness/conventions.md`), samt Form (Block-Tag im KDoc direkt über der Funktion, mehrere Kennungen durch Leerzeichen oder mehrere Zeilen, kein `@Tag`).
- **F-3 Committete Matrix veraltet.** `docs/plan/spec-abdeckung.md` zeigt 10/8/33, frisch sind 14/6/31 (siehe oben). Wer sie liest, bekommt einen älteren Stand als der Bestand. Neu erzeugen und committen; ein Gate gibt es nicht (Option D), also bleibt es eine geschuldete Handlung beim Schließen jedes Slice mit Tests (in `slice-*`-DoDs der Folge-Slices als Punkt zu verankern, falls gewollt).
- **F-7 Ablage `.harness/state/spec-abdeckung` bricht `docs-check` fail-closed, bis sie weggeräumt ist** (siehe (4)). Härtung wäre ein `trap`/`rm -rf` auch im Fehlerpfad oder ein Ablageort außerhalb des Scan-Bereichs; das Kommentar-Versprechen „das Target räumt sie nach dem Lesen weg“ gilt nur im Erfolgs- und STRENG-Pfad.

### LOW

- **F-4 Mutation M8 überlebt:** „Ein neuer KDoc-Block verwirft einen offenen Tag“ ist im Skript zugesagt, aber im Selbsttest nicht belegt. Fixture-Fall ergänzen (Tag, neuer Block ohne Tag, dann Funktion).
- **F-5 Zusage „umbenannter Test verliert seinen Nachweis“ (Matrix-Kopf, DoD-Wortlaut „umbenannt“) ist nur gegen einen veralteten Bericht belegbar;** in der Live-Pipeline sind Berichte frisch. Aussage auf den Fall einschränken (Tag, dessen Test im Bericht keinen Namensvetter hat) oder streichen (§3.8).
- **F-6 Gate-Festlegung durch Modul-Test „nachgewiesen“:** SPEC-024 und SPEC-042 stehen auf „nachgewiesen“, weil ein Modul-Test sie nennt. Das ist von der Grenze im Kopf gedeckt, aber die Klasse „Gate-Festlegung, ungemessen“ verschwindet damit still, sobald irgendein Test die Kennung nennt.
- **F-8 Make-Exit 2 bei STRENG:** `make` meldet bei Rezept-Exit 1 den Exit 2. Verhalten gemäß GNU make; Hinweis im README („bricht bei Waisen“) trifft zu, nur der Code ist nicht 1.
- **F-9 Review-Report zum Prüfzeitpunkt untracked** (Commit d2feffb brachte ihn kurz danach).

## Bestätigungsliste Eingaben

Beleg vs. Behauptung: Selbsttest, Matrix-Erzeugung, Determinismus, Ablage-Räumung (Erfolgspfad), `docs-check`, `make gates`, Out-of-Scope: selbst gefahren. Laufzeit „25 s“: nicht bestätigt (56 s kalt/teilgewärmt, 0,75 s warm).

## Zustand des Repos

`git status`: nur `.claude/worktrees/` (vorbestehend) und diese Berichtsdatei. `docs/plan/spec-abdeckung.md` per `git checkout` auf den committeten Stand zurückgesetzt; Ablage `.harness/state/spec-abdeckung` entfernt; Mutationskopien liegen im Scratchpad `/tmp/claude-1000/-Development-tdswire/8562f454-84ba-456b-9970-945e69c035f4/scratchpad/`.
