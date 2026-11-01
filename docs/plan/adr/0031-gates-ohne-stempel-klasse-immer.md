# ADR-0031: Gates ohne Stempel: Klasse `immer` für docs-check, gate-zuordnung und stempel-eingaenge-pruefung

**Status:** Accepted

**Datum:** 2026-11-01

**Autor:** pt9912

**Bezug:** [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0024](0024-lokale-stempel-zwei-klassen.md), [ADR-0030](0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md), [ADR-0023](0023-gate-klassen-und-stempel.md), [AGENTS.md](../../../AGENTS.md) §3.5, §3.6, §3.8

**Schärft:** — (Prozess-ADR; keine Spec-Stelle legt die Gate-Struktur fest, die Spec wird nicht angefasst).

**Supersedes (teilweise), jeweils nur wörtlich benannt; die Dateien der Vorgänger bleiben unverändert und `Accepted` ([AGENTS.md](../../../AGENTS.md) §3.5), gilt erst mit `Accepted` dieser ADR:**

- [ADR-0024](0024-lokale-stempel-zwei-klassen.md) Entscheidung 1, im Punkt „**`werkzeug`:** `docs-check`, `baseline-verify`, `detekt-selbsttest`, `skript-selbsttest` und …“ **nur** das Wort `docs-check`: es gehört zur Klasse `immer`.
- [ADR-0024](0024-lokale-stempel-zwei-klassen.md) Entscheidung 3, **nur** den Wortlaut „sonst zu `werkzeug`“ der Zuordnungsregel, soweit er `docs-check` betrifft: ein Gate, dessen Eingang keine Eingangsliste vollständig deckt, gehört zu `immer`.
- [ADR-0030](0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md) Entscheidung 2, **nur** den Satz „`gate-zuordnung` und `stempel-eingaenge-pruefung` bleiben in `werkzeug`: sie sind schnell (0,13 s und 0,3 s, Einzelmessung) und prüfen die Listen gegen den echten Baum bei jeder Änderung.“: beide gehören zur Klasse `immer`.

Nicht ersetzt, sondern unverändert in Kraft: alle übrigen Entscheidungen von [ADR-0024](0024-lokale-stempel-zwei-klassen.md) und [ADR-0030](0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md); die Zahl der Stempel bleibt drei. `immer` ist kein Stempel, sondern die Aussage „läuft bei jedem `make gates`“.

---

## Kontext

**Befund (Review zur Migration der Gate-Kette, HIGH-1 der ersten Runde, belegt; MEDIUM-2 der zweiten Runde):** `docs-check` liest über `scan.roots` jede Markdown-Datei des Repos, `stempel-eingaenge-pruefung` alle Make-Fragmente und das Dockerfile, `gate-zuordnung` die Make-Fragmente. Standen sie in `werkzeug`, übersprang ein Beleg den Lauf, sobald nur eine Datei außerhalb der Eingangsliste geändert war: eine Datei `src/NOTIZ.md` mit totem Link und unbekannter ID ließ `make gates` mit Exit 0 enden und den Nachweis schreiben. Die Prüfung der Listen ist selbst nicht stempelbar, denn sie müsste laufen, um zu zeigen, dass die Liste vollständig ist, und läge sie in der Klasse, wäre sie bei vollständig gedeckter Liste übersprungen, bei unvollständiger ebenso.

**Ist-Zustand:** Die Umsetzung führt die Klasse `immer` (ohne Stempel) mit genau diesen drei Gates; sie weicht damit von den beiden `Accepted`-ADRs ab. Die Begründung steht bisher nur im Kopf von `tools/mk/stempel.mk` und in der Planung der Migration. Der Verifikationsbericht der Migration fand keine Falsch-grün-Lücke und hält die Klasse für technisch sicher; er verlangt die Entscheidung über die ADR-Lage (Bedingung 1).

**Kosten (Einzelmessungen 2026-11-01, warmer Cache, hoher Host-Last; Quelle: Kopf von `tools/mk/stempel.mk` und Verifikationsbericht):** `docs-check` 1,2 s, `stempel-eingaenge-pruefung` 0,3 s, `gate-zuordnung` 0,13 s; zusammen rund 1,7 s bei jedem Lauf, auch bei „nichts geändert“.

**Regel:** [AGENTS.md](../../../AGENTS.md) §3.6; [ADR-0024](0024-lokale-stempel-zwei-klassen.md) Entscheidung 6: ein Gate mit nicht in einer Liste stehendem Eingang trägt im Stempel nicht und läuft immer.

## Entscheidung

Vorgeschlagen ist Option A.

1. **Vierte Klasse `immer` ohne Stempel:** `docs-check`, `gate-zuordnung` und `stempel-eingaenge-pruefung` laufen bei jedem `make gates`. Es gibt keine Eingangsliste und keinen Beleg für sie.
2. **Zuordnungsregel:** ein Gate, dessen Eingang eine Eingangsliste nicht vollständig deckt (jede Markdown-Datei, alle Make-Fragmente), gehört zu `immer`; sonst gilt die Regel aus [ADR-0024](0024-lokale-stempel-zwei-klassen.md) Entscheidung 3 und [ADR-0030](0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md) Entscheidung 5.
3. **Die Zahl der Stempel bleibt drei;** der Mechanismus bleibt einer.

## Verglichene Alternativen

Zeiten sind Einzelmessungen (Quelle im Kontext).

| Option | Pro | Contra |
|---|---|---|
| **A — Klasse `immer` ohne Stempel (gewählt vorgeschlagen)** | kein Falsch-grün-Weg über Dateien außerhalb einer Liste; die Listenprüfung ist nicht von sich selbst abhängig; Ist-Zustand, keine Umbauarbeit | rund 1,7 s bei jedem Lauf, auch bei unveränderter Doku; ein Zuschnitt mehr (vier Klassen) |
| B — alle drei zurück nach `werkzeug`, Liste deckt alle Markdown-Dateien (Scan-Wurzeln) | folgt den beiden `Accepted`-ADRs; bei „nichts geändert“ entfallen die 1,7 s | die Liste müsste das ganze Repo decken, jede Änderung irgendwo ließe `werkzeug` laufen und der Gewinn bei Doku-Änderungen entfiele; eine neue Datei außerhalb der Wurzeln bleibt Falsch-grün-Risiko; die Prüfung der Liste bliebe von sich selbst abhängig |
| C — `docs-check` ohne Stempel, Prüfung und `gate-zuordnung` in `werkzeug` | spart 0,4 s | die Listenprüfung ist die Stelle, die ein Überspringen verhindern soll; in `werkzeug` ist sie bei gedeckter Liste übersprungen, die Lücke von A bleibt dort offen; zwei Zuordnungen statt einer |

## Konsequenzen

- Die Köpfe von `tools/mk/stempel.mk` und die Tabelle in `harness/README.md` verweisen auf diese ADR statt auf einen offenen Punkt.
- `werkzeug.eingaenge` kann um die Pfade verkürzt werden, die nur `docs-check` las; das ist eine Listenänderung, keine Entscheidung dieser ADR.

## Akzeptierte Negative

1. **Kosten bei jedem Lauf:** rund 1,7 s (Einzelmessung), auch wenn nichts geändert ist. Einmaliges, benanntes Negativ ohne Folge-Dokument.
2. **Ein Zuschnitt mehr:** vier Klassen, drei davon mit Stempel. Einmaliges, benanntes Negativ.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| `tools/gate-zuordnung.sh` mit Selbsttest (bestehender Sensor) | `docs-check`, `gate-zuordnung` und `stempel-eingaenge-pruefung` stehen in `GATE_CHECKS` und in der Klasse `immer`; die Mutation „`docs-check` nach `werkzeug`“ wird rot (zu ergänzen, falls der Selbsttest sie nicht schon führt) | `make gate-zuordnung` (Gate in `immer`) |
| Vollauf in der CI | alle Klassen laufen ohne Noten | `make gates` |

## Offen

Zur Entscheidung des Nutzers:

- (a) Option A wie vorgeschlagen, oder B, C.
- (b) Ob die Pflichtmenge der Klasse `immer` in `gate-zuordnung` (die drei Gates verlangt, wie bei `stempel` nach [ADR-0030](0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md) Entscheidung 6) hier oder erst in der Umsetzung fixiert wird; der Vorschlag ist: Pflichtmenge, damit ein Zurücksetzen nach `werkzeug` rot ist.

## Re-Evaluierungs-Trigger

- Die 1,7 s je Lauf werden spürbar (Zahl bei Nachmessung deutlich höher): Zuschnitt neu bewerten.
- Eine Eingangsliste kann ohne Schleife über das ganze Repo vollständig geprüft werden: Option B neu bewerten.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-01 | Proposed – Review MEDIUM-2 und Verifikation zur Migration der Gate-Kette; Optionen A bis C, Empfehlung A; Status bleibt Proposed bis zur Entscheidung des Nutzers | [ADR-0024](0024-lokale-stempel-zwei-klassen.md), [ADR-0030](0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md) |
| 2026-11-01 | Accepted – Annahme durch den Nutzer; gewählt ist Option A (Klasse `immer` ohne Stempel); die Frage (b) zur Pflichtmenge in `gate-zuordnung` ist nicht beantwortet und bleibt offen | [ADR-0024](0024-lokale-stempel-zwei-klassen.md), [ADR-0030](0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md) |
