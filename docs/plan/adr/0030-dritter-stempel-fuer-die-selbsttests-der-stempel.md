# ADR-0030: Dritter Stempel `stempel` für die Selbsttests der Stempel: Ablösung von „genau zwei Stempel“ und „im Werkzeug-Gate“ in ADR-0024

**Status:** Accepted

**Datum:** 2026-11-01

**Autor:** pt9912

**Bezug:** [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0024](0024-lokale-stempel-zwei-klassen.md), [ADR-0023](0023-gate-klassen-und-stempel.md), [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md), [AGENTS.md](../../../AGENTS.md) §3.5, §3.6, §3.8

**Schärft:** — (Prozess-ADR; keine Spec-Stelle legt die Gate-Struktur fest, die Spec wird nicht angefasst).

**Supersedes (teilweise), jeweils nur wörtlich benannt; die Dateien der Vorgänger bleiben unverändert und `Accepted` ([AGENTS.md](../../../AGENTS.md) §3.5):**

- [ADR-0024](0024-lokale-stempel-zwei-klassen.md) Entscheidung 1, **nur** die Zahl: „**Genau zwei Stempel:** `werkzeug` und `tests`. Die **Zahl der Stempel bleibt zwei**, der **Mechanismus ist einer** (ein Skript). Ein neues Gate bekommt keinen eigenen Stempel, sondern gehört zu einem der beiden.“ Es gilt: genau **drei** Stempel, `werkzeug`, `tests` und `stempel`; der Mechanismus bleibt einer. Ebenso die Zahl im Titel („genau zwei Stempel“) und im Abschnitt „Zahl der Stempel“ der Alternativen (gewählt war dort „Zwei Stempel“).
- [ADR-0024](0024-lokale-stempel-zwei-klassen.md) Entscheidung 3, **nur** den Wortlaut „sonst zu `werkzeug`“ der Zuordnungsregel: ein Gate, das den Stempel-Mechanismus selbst prüft (Skript, Eingangslisten, Zuordnung), gehört zu `stempel`; die übrigen Teile der Regel gelten.
- [ADR-0024](0024-lokale-stempel-zwei-klassen.md) Entscheidung 10, **nur** die Ortsangabe „Pflicht-Sensor, **im Werkzeug-Gate**“ beim Selbsttest der Eingangslisten und in Folgepflicht 0 „**im Werkzeug-Gate**“ sowie in der Fitness Function die Zelle „(Pflicht, **im Werkzeug-Gate**; nach Umsetzung)“: der Selbsttest mit Mutation ist Pflicht-Sensor im Gate der Klasse `stempel`. Das Verbot „ohne Selbsttest wird kein Stempel geschrieben“ und die Mutations-Regel bleiben.
- [ADR-0024](0024-lokale-stempel-zwei-klassen.md) Re-Evaluierungs-Trigger 2 („Aufteilung in weitere Stempel ist eine neue Entscheidung“) ist mit dieser ADR die neue Entscheidung für genau einen weiteren Stempel; weitere Stempel bleiben es nicht.
- [ADR-0023](0023-gate-klassen-und-stempel.md) Entscheidung 1, **nur** das Wort „Zwei“ in „Zwei Gate-Klassen, dazu eine eigene Einheit“, soweit es die Zahl der Klassen der Stempel festlegt (ADR-0024 hatte es schon geschärft).

Nicht ersetzt, sondern unverändert in Kraft: alle übrigen Entscheidungen von [ADR-0024](0024-lokale-stempel-zwei-klassen.md) (Format, Speicherort, Skript-Schnittstelle, Aufnahmebedingung, Migration, Bindung an `record-gates`) und von [ADR-0023](0023-gate-klassen-und-stempel.md). Der Status beider bleibt `Accepted`.

---

## Kontext

**Befund (Review zur Migration der Gate-Kette auf zwei Stempel, HIGH-2):** [ADR-0024](0024-lokale-stempel-zwei-klassen.md) Entscheidung 10 verlangt den Selbsttest der Eingangslisten mit Mutation als Pflicht-Sensor im Werkzeug-Gate („ein Selbsttest, der das nicht zeigt, gilt nicht als gelaufen“, Folgepflicht 0). Die Migration nimmt vier Selbsttests nicht in die Gate-Kette auf: `stempel-selbsttest`, `stempel-eingaenge`, `stempel-abbild`, `stempel-gruppe`. Als Gates der Klasse `werkzeug` liefen sie bei jeder Änderung unter `docs/` mit. Es gibt dafür weder eine ADR noch einen Carveout; auch eine Nutzerentscheidung ändert eine angenommene ADR nur über eine neue ADR ([AGENTS.md](../../../AGENTS.md) §3.5, §3.6). Folge: ein Fehler in `tools/stempel*`, den Listen oder dem Dockerfile-Bezug röten nur diese vier Targets, `make gates` bleibt grün.

**Messwerte (Einzelmessungen vom 2026-11-01, warmer Docker-Layer-Cache, Lastmittel 1,7 bis 5, keine Streuung behauptet; Quelle: Kopf von `tools/mk/stempel.mk` und Review im Zweig der Migration, hier nicht neu gemessen):** `stempel-selbsttest` 1,3 s, `stempel-eingaenge` 3,5 s, `stempel-abbild` 3,0 s, `stempel-gruppe` 2,3 s, zusammen 10,1 s. `make gates` bei nur geänderter Doku 6,8 s (Review: 7,3 s), beide Klassen mit Beleg 2,2 s. Das Nutzerziel ist, dass `make gates` schneller wird; ein Gate, das bei jeder Doku-Änderung 10 s zulegt, widerspricht ihm.

**Was die vier Selbsttests lesen (gelesen, nicht gemessen):** das Stempel-Skript und seine Hilfsskripte, die Gruppenschritte, die Zuordnungsprüfung, die Eingangslisten, das Dockerfile als Vorlage der Mutation und die Make-Verdrahtung. Der Gegenstand eines Selbsttests ist der Mechanismus, nicht die Doku. Seine Eingänge sind eine kleine Teilmenge von `werkzeug`.

**Regel:** [AGENTS.md](../../../AGENTS.md) §3.6 — ein Gate, das bei gleichem Eingang nicht läuft, ist keine Senkung der Schwelle, solange der Beleg für genau diesen Eingang vorliegt. Ein Gate gar nicht in die Kette zu nehmen, ist eine Senkung.

**Annahme, die kippt:** Dass das Ergebnis der Selbsttests eine Funktion der unten genannten Eingänge ist. Liest einer von ihnen etwas, das in der Liste fehlt, kann er fälschlich übersprungen werden (siehe Akzeptierte Negative 1).

## Entscheidung

Wir wählen **Option B**: ein dritter Stempel `stempel` mit enger Eingangsliste. Die vier Selbsttests sind Gates dieser Klasse und stehen in `make gates`; sie laufen nur, wenn sich ihre Eingänge ändern.

1. **Dritte Klasse `stempel`.** Eine Datei `tools/stempel/stempel.eingaenge` im Format der anderen (ein Pfad je Zeile, Verzeichnisse rekursiv, `#` Kommentar). Gleicher Mechanismus, gleiche Skript-Schnittstelle, gleicher Beleg als Note ([ADR-0024](0024-lokale-stempel-zwei-klassen.md) Entscheidung 4 und 5). Der Gruppenschritt `gruppe-stempel` steht in `GATE_CHECKS` neben `gruppe-werkzeug` und `gruppe-tests`.
2. **Umfang der Klasse:** `stempel-selbsttest`, `stempel-eingaenge`, `stempel-abbild`, `stempel-gruppe`. `gate-zuordnung` und `stempel-eingaenge-pruefung` bleiben in `werkzeug`: sie sind schnell (0,13 s und 0,3 s, Einzelmessung) und prüfen die Listen gegen den echten Baum bei jeder Änderung.
3. **Eingänge der Klasse (Anfangsliste, Setzung):** `tools/stempel.sh`, die Hilfsskripte und Selbsttests des Mechanismus unter `tools/` (`tools/stempel-*`), `tools/stempel/` (alle Listen), `tools/gate-zuordnung.sh` und `tools/gate-zuordnung-selbsttest.sh`, `tools/mk/stempel.mk`, `repo.mk`, `Makefile`, `Dockerfile` (Vorlage der Mutation), und was ein Selbsttest sonst liest. **Der Implementer bildet die endgültige Liste aus den Lese-Pfaden der vier Selbsttests** (jedes `bash`, `source`, `cat`, `git`-Argument auf eine Repo-Datei) und führt sie in der Liste auf. Nicht in der Liste stehen `docs/`, `spec/`, `harness/`, `src/` und `referenztest-dotnet/`; eine Änderung dort löst die Klasse nicht aus.
4. **Selbstbezug:** die Liste `stempel.eingaenge` und `tools/stempel.sh` stehen in den Listen **aller drei** Stempel (Fortschreibung von [ADR-0024](0024-lokale-stempel-zwei-klassen.md) Entscheidung 10, zweiter Punkt). Die Liste ist dort unter `tools/` bereits durch `werkzeug` gedeckt; in `tests` steht sie ausdrücklich.
5. **Zuordnung:** Gates, die den Stempel-Mechanismus, seine Listen oder die Zuordnung der Gates prüfen, gehören zu `stempel`; sonst gilt die Regel aus [ADR-0024](0024-lokale-stempel-zwei-klassen.md) Entscheidung 3. Die Zuordnungsprüfung (`gate-zuordnung`) kennt drei Klassen.
6. **Pflichtmenge in der Zuordnungsprüfung:** `gate-zuordnung` verlangt, dass die vier genannten Selbsttests in `GATE_CHECKS` stehen und der Klasse `stempel` angehören; fehlt einer oder steht er in keiner Klasse, ist `gate-zuordnung` rot. Ihr Selbsttest enthält diesen Fall als Mutation. Das ist die Fitness Function dieser ADR.
7. **CI:** keine Änderung. Die CI sieht keine Noten ([ADR-0024](0024-lokale-stempel-zwei-klassen.md) Entscheidung 4) und fährt alle drei Klassen; die Selbsttests laufen dort bei jedem Push als Teil von `make gates` (Kosten dort rund 10 s, Einzelmessung lokal). Das ist der Vollauf, der falsch grüne Überspringer (Negativ 1) fängt.
8. **Nicht Gegenstand:** die Befunde HIGH-1 (Dateien außerhalb der Listen können `docs-check` überspringen lassen) und MEDIUM-1 aus demselben Review. Ein dritter Stempel ändert daran nichts; sie gehören in die Nachbesserung der Listen von `werkzeug` und `tests`.

## Verglichene Alternativen

Zeiten sind Einzelmessungen vom 2026-11-01 bei warmem Cache (Quelle im Kontext); „gerechnet“ heißt Summe, nicht gemessen.

| Option | Pro | Contra |
|---|---|---|
| A — die vier Selbsttests als Gates der Klasse `werkzeug` | folgt [ADR-0024](0024-lokale-stempel-zwei-klassen.md) Entscheidung 10 wörtlich; keine neue Entscheidung | jede Doku-Änderung zahlt rund 10 s dazu (gerechnet: 6,8 s plus 10,1 s ergeben 16,9 s statt 6,8 s); widerspricht dem Nutzerziel; Fehlersuche im Mechanismus ist selten, Doku-Änderungen sind häufig |
| **B — dritter Stempel `stempel`, enge Eingangsliste (gewählt)** | der Pflicht-Sensor steht im Gate und trägt; Gate-Zeit bei Doku-, Quell- und Dockerfile-nahen Änderungen praktisch unverändert (nicht gemessen: der Aufwand von `pruefe`/`schreibe` für eine dritte Klasse, geschätzt Bruchteile einer Sekunde); der Beleg gilt für alle Worktrees; derselbe Mechanismus | [ADR-0024](0024-lokale-stempel-zwei-klassen.md) Entscheidung 1 wird teilweise abgelöst; eine dritte Liste ist eine weitere Setzung, die driften kann (Negativ 1) |
| C — Selbsttests als eigener CI-Schritt, lokal ein befristeter Carveout mit Trigger | `make gates` lokal unverändert schnell; CI-Schritt einfach | Entscheidung 10 gilt lokal nicht, der Carveout hat keinen natürlichen Auflösungs-Trigger (die Ursache, Laufzeit, löst sich nicht von selbst); ein Fehler in `tools/stempel*` bleibt lokal grün und wird erst im Push rot; der befristete Charakter ist erfunden |
| D — Selbsttests bleiben Nicht-Gate, per ADR festgeschrieben | kein Aufwand, `make gates` unverändert | nimmt den Pflicht-Sensor weg, den der Nutzer ausdrücklich verlangt hat („die Eingangsliste für den Test brauchen wir auch“); Fehler im Mechanismus, der Gates überspringt, bleiben dauerhaft unbemerkt; die gefährlichste Fehlerrichtung (falsch grün) hat keinen Sensor |

## Konsequenzen

- Positiv: Entscheidung 10 aus [ADR-0024](0024-lokale-stempel-zwei-klassen.md) ist erfüllt, ohne die Gate-Zeit bei Doku-Änderungen zu belasten; ein Fehler in `tools/stempel*` rötet `make gates` lokal; das Fehlen der Selbsttests in der Kette ist selbst ein Rot.
- Negativ: siehe „Akzeptierte Negative“.
- **Folgepflicht (nach `Accepted`, durch einen Implementer, im Zuge der laufenden Migration der Gate-Kette oder unmittelbar danach):**
  1. Die Datei `tools/stempel/stempel.eingaenge` (Entscheidung 3), das Fragment `tools/mk/stempel.mk` (Gruppenschritt `gruppe-stempel`, Zuordnung der vier Selbsttests), `repo.mk` (`GATE_CHECKS` um die dritte Gruppe), `tools/gate-zuordnung.sh` (drei Klassen, Pflichtmenge, Mutation im Selbsttest), `tools/stempel-gruppe.sh` und die Selbsttests, die „zwei Klassen“ fest einbauen.
  2. `harness/README.md` §Sensors: die vier Zeilen der Selbsttests als Gate mit Bindung auf diese ADR; die Aussage „kein Gate“ entfällt dort und im Kopf von `tools/mk/stempel.mk`.
  3. Nachmessung (Einzelmessung, warm): `make gates` bei nur geänderter Doku (Erwartung: wie ohne die Selbsttests), bei geändertem `tools/stempel.sh` (Erwartung: rund 10 s mehr), mit Beleg aller drei Klassen. Ohne Messung trägt keine Laufzeitaussage.
  4. Die Liste aus den Lese-Pfaden der vier Selbsttests ableiten und im Kopf der Liste die Herleitung nennen ([AGENTS.md](../../../AGENTS.md) §3.8).

## Akzeptierte Negative

1. **Falsch grün bei fehlendem Eingang:** liest ein Selbsttest eine Datei, die nicht in `stempel.eingaenge` steht, wird er bei deren Änderung fälschlich übersprungen. Das Risiko ist kleiner als bei den anderen Klassen, weil die Lese-Pfade dreier Skripte und Listen im Repo überschaubar sind und der Vollauf in der CI jeden Push prüft; es bleibt ein Rest. Ein Selbsttest, der die Lese-Pfade mechanisch prüft, wird nicht gebaut: die Kosten (ein Ablaufverfolger über Bash) stehen nicht im Verhältnis zum Rest. Bei erstem Auftreten gilt der Re-Evaluierungs-Trigger.
2. **Ein Zuschnitt mehr:** drei Listen statt zwei. Einmaliges, benanntes Negativ ohne Folge-Dokument; der Mechanismus bleibt einer.
3. **Die Messwerte sind Einzelmessungen** bei Lastmittel 1,7 bis 5; der Gewinn gegenüber Option A (gerechnet 10 s bei Doku-Änderungen) hängt am Zustand des Hosts.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| `tools/gate-zuordnung.sh` mit Selbsttest (Entscheidung 6, nach Umsetzung) | die vier Selbsttests stehen in `GATE_CHECKS` und gehören zur Klasse `stempel`; die Mutation „einen aus der Kette nehmen“ wird rot | `make gate-zuordnung` (Gate in `werkzeug`) |
| Vollauf in der CI (Entscheidung 7) | alle drei Klassen laufen ohne Noten; ein roter Selbsttest rötet den Push | `make gates` |

Wer die Pflichtmenge in `tools/gate-zuordnung.sh` selbst ändert, ändert einen Eingang von `werkzeug` und löst dessen Lauf aus; die Änderung steht im Diff und im Review.

## Offen

Zur Entscheidung des Nutzers:

- (a) Option B wie vorgeschlagen, oder A, C, D.
- (b) Ob [ADR-0023](0023-gate-klassen-und-stempel.md) Entscheidung 1 mit dem Wort „Zwei“ in das Supersedes gehört (hier ja, weil sonst zwei angenommene ADRs eine Zahl nennen, die nicht mehr gilt).
- (c) HIGH-1 und MEDIUM-1 des Reviews (Listen von `werkzeug` und `tests` zu schmal) sind nicht Gegenstand dieser ADR; sie brauchen Nachbesserung der Listen (Entscheidung 8), eine Entscheidung dazu ist nicht nötig, solange die Zahl der Stempel gilt.

## Re-Evaluierungs-Trigger

- Ein Selbsttest wurde aufgrund eines Belegs übersprungen und hätte rot sein müssen: die Liste der Klasse `stempel` neu bewerten; Rückfall auf die Eingangsliste `tools` als Ganzes ist die einfachste Verschärfung.
- Die Nachmessung zeigt, dass die dritte Klasse bei Doku- oder Quell-Änderungen spürbar mitläuft: Zuschnitt der Liste prüfen.
- Ein vierter Stempel wird erwogen: neue Entscheidung, nicht Anhang an diese.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-01 | Proposed – Review-Befund HIGH-2 zur Migration der Gate-Kette; Optionen A bis D, Empfehlung B; Status bleibt Proposed bis zur Entscheidung des Nutzers | [ADR-0024](0024-lokale-stempel-zwei-klassen.md), [ADR-0023](0023-gate-klassen-und-stempel.md) |
| 2026-11-01 | Accepted – Annahme durch den Nutzer; gewählt ist Option B (dritter Stempel `stempel`) | [ADR-0024](0024-lokale-stempel-zwei-klassen.md), [ADR-0023](0023-gate-klassen-und-stempel.md) |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
