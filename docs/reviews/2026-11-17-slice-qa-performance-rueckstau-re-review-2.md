# Review-Report (zweiter Re-Review): slice-qa-performance-rueckstau — 2026-11-17

**Review-Art:** zweiter Re-Review der Nachbesserung nach dem [Re-Review](2026-11-17-slice-qa-performance-rueckstau-re-review.md) (N-1 bis N-4), nur der Diff gegen dessen Stand, gegen Plan, ADR und Hard Rules; nicht gegen die DoD. Frischer Kontext.

**Gegenstand:** Branch `slice-qa-performance-rueckstau`, `git diff 1ed39fee..770cfb79`: `tools/rueckstau.sh` (+3 Zeilen Kommentar), `tools/rueckstau-selbsttest.sh`, `tools/mk/skript-selbsttest.mk` (Hilfetext), Plan-Tabelle im Slice, Re-Review-Report. Commits `23ed4e8f` (Code), `770cfb79` (Doku).

**Skill:** `.harness/skills/reviewer.md` @ `770cfb79` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-17

**Eingangs-Kontext:** der Re-Review (N-1 bis N-4), [TDS-QA-04](../../spec/lastenheft.md#tds-qa-04--performance), [ADR-0006](../plan/adr/0006-backend-vertrag-und-streaming.md), [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), `AGENTS.md` 3.7, 3.8.

**Selbst gefahren** (Speicher 13 GiB verfügbar, ein weiterer Implementer parallel; keine künstliche Last, kein `make rueckstau`, kein `clean-build`, kein `e2e`; alle Mutationen an Kopien im Scratchpad):

- Frischer `docker build --no-cache --target skript-selbsttest .`: Exit 0, 90 s. `rueckstau-selbsttest: gruen` (0 Abweichungen, elf Mutationen je rot), `rueckstau-tabellen-selbsttest: gruen`. Dasselbe Skript auf dem Host (`bash`, ohne Docker): grün, 61 s.
- `make docs-check` Exit 0 (1595 Dateien, 0 Befunde, vor Anlegen dieses Reports), `make gate-zuordnung` Exit 0 (33 Fälle), `make stempel-eingaenge-pruefung` Exit 0.
- **N-1 selbst gefahren:** die Mutationen des Selbsttests streichen die zwei eingerückten `speicherwacht`-Aufrufe in `zelle()` (beide, nur den ersten, nur den zweiten). Gemessene Wirkung: beide gestrichen färbt 5 Prüfungen rot (je Fall `vor-server`/`vor-client` Exit, Meldung, bei `vor-server` zusätzlich der Aufruf `run -d`), nur der erste gestrichen 1 Prüfung (der Server startet), nur der zweite gestrichen 2 Prüfungen (Exit 1 statt 3, Meldung fehlt). Die Röte kommt aus den neuen Prüfungen: die älteren Fälle (Wächter vor dem Bau) laufen vor `zelle()` und sind von der Streichung nicht berührt. `vor-client` unterscheidet Exit 3 vom Exit 1 des Client-`run`, den der Stub für diesen Fall liefert.

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| M-1 | MEDIUM | Der Kommentar am Muster sagt, der Selbsttest belege, „dass jeder Name des Musters einen Zweig hat“. Der Selbsttest liest das Muster nicht: er fährt seine eigene Liste `alle`. Gemessen: ein Name im Muster ohne Zweig (Kopie mit `\|imp-neu` im Muster, ohne Zweig, ohne Eintrag in `alle`) lässt ihn grün (0 Abweichungen). Genau der Fall, den der Kommentar abdeckt (Name im Muster ergänzt, Zweig vergessen, Abbruch erst nach dem Bau der Images), wird nicht gefangen. Auf der Seite des Selbsttests steht an `alle` kein Kopplungskommentar. | `AGENTS.md` 3.8 (Aussage breiter als Beleg), Skill MEDIUM „Zahl, Feldmenge oder Zuordnung steht an einem zweiten Ort ohne Sensor“ | `tools/rueckstau.sh` · „belegt, dass jeder Name des Musters einen Zweig hat“; `tools/rueckstau-selbsttest.sh` · `alle="leser-eigen-1` | ja (Mutation, Kopie im Scratchpad) | Kopplungssensor liest eine Kopie statt der Quelle |
| M-2 | INFO | Die Grenze im Kopf des Selbsttests („belegt den Anlauf des Skripts, keinen Lauf gegen Docker“) ist enger als der Stand: der Selbsttest belegt jetzt auch die zwei Wächter in `zelle()` bis zum Start von Server und Client. Die Aussage ist zu schwach, nicht zu stark; keine Wirkung. | Maintainability | `tools/rueckstau-selbsttest.sh` · „Grenze: belegt den Anlauf des Skripts“ | nein | Grenze bleibt hinter dem Beleg zurück |

(Die Kennung M-2 ist wegen der Zählung dieses Reports so vergeben; Kategorie INFO.)

## Antworten auf die Prüfpunkte

1. **N-1 behoben.** Beide Wächter in `zelle()` sind von Tests getragen (Mutationen oben). Der Satz im Skriptkopf „unter dieser verfuegbaren Menge bricht der Lauf vor jedem Container ab, Exit 3“ stimmt: der Wächter steht vor dem Bau, vor `docker run` des Servers und vor dem Start des Clients; beim zweiten läuft der Server bereits, den die Aufräumfalle `aufraeumen` entfernt, und der Client startet nicht. Der Satz ist vom Container-Begriff her gesagt (Images und Netz sind keine Container).
2. **N-3 zur Hälfte behoben.** Kopplung steht jetzt als Kommentar am Muster und am `case`; der Selbsttest fährt alle Namen von `alle` und meldet „unbekannte Zelle“ zur Laufzeit als Abweichung; die Mutationen `zweig-blk-fehlt` und `zweig-imp-fehlt` färben je 1 Prüfung rot (gemessen). Offen: M-1 (die Liste im Test ist die Kopie, nicht das Muster).
3. **N-2 behoben.** Die Plan-Tabelle nennt `tools/mk/skript-selbsttest.mk`; der Diff berührt keine Datei außerhalb der Tabelle (Abgleich gegen `git diff --stat 1ed39fee..HEAD`: die fünf Dateien sind Tabelle, Report, Hilfetext, Selbsttest, Skript). Der Satz „Der Implementer-Agent erweitert die Liste in seinem ersten Lauf“ ist entfernt; zurück bleibt eine doppelte Leerzeile (kein Befund).
4. **N-4 behoben.** Der Hilfetext trägt die fehlenden Kommas.
5. **Neue Fehler, Maskierung:** im Stub steht `[ ... ] && senke` als letzte Anweisung einer `case`-Arm; der Stub endet mit `exit 0`, die Arme können den Exit nicht maskieren oder verfälschen, und `build` ohne `fall` liefert gewollt Exit 1. Kein `&&`-Befund. Die Datei `fall` bleibt für den Fall `ohne-abbruch` nicht leer, der Bau gelingt, `run -d` scheitert, alle Zweige des `case` werden erreicht.
6. **Hard Rules in neuen Kommentaren (`AGENTS.md` 3.7, 3.8):** Stub-Kommentar, Kopplungskommentare und Selbsttest-Kopf stehen im Indikativ über den geltenden Zustand (Zusage, Kopplung, Grenze). Eine Ausnahme: der Satz am Muster behauptet mehr als belegt (M-1). Der Satz „(ein Name ohne Zweig endete sonst erst nach dem Bau der Images in ‚unbekannte Zelle‘)“ beschreibt die abwesende Prüfung im Konjunktiv; er erklärt den Zweck der Prüfung und ist kein Befund (keine verworfene Alternative, kein abwesender Text). Kein `@Suppress`, `noqa`, `nolint`.

## Negativbefund (geprüft, ohne Befund)

- `tools/rueckstau.sh`: ohne Befund außer M-1 (Kommentar).
- `tools/rueckstau-selbsttest.sh`: ohne Befund außer M-1 und M-2.
- `tools/mk/skript-selbsttest.mk`: ohne Befund.
- `docs/plan/` (Slice): ohne Befund; keine `- [x]` im Diff gesetzt.
- `docs/reviews/`: Re-Review-Datei unverändert gegenüber dem Stand des Re-Reviews bis auf den Commit `770cfb79`; ohne Befund.
- `testserver/`, `referenztest-dotnet/`, `src/`, `spec/`, `harness/`, `Dockerfile`, `docs/user/`, `docs/messungen/`: im Diff `1ed39fee..HEAD` unberührt.

## Re-Review-Pflicht

Behebt eine Nachbesserung M-1 an `tools/rueckstau.sh` oder `tools/rueckstau-selbsttest.sh`, ist das Code (Skript): ein Re-Review in frischem Kontext oder in der Closure-Notiz §7 der Vermerk „Nachbesserung ohne zweiten Review“ mit Commits, Grund und der Prüfung. Eine Korrektur allein des Kommentars (Aussage auf „jeder Name von `alle`“ einschränken) ist eine Kommentar-Korrektur ohne Pflicht.
