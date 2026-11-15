# Verifikation: slice-release-entscheidungen — 2026-11-15

**Rolle:** Verifier (Frage: bauen wir es richtig, gegen Plan und DoD). **Gegenstand:** Stand 2c6dcfc9, Basis `main` (`git diff main...2c6dcfc9`), Branch `worktree-agent-a231c5f47ad1cd322`. **Eingang:** Slice `docs/plan/planning/in-progress/slice-release-entscheidungen.md` (§1 bis §3, §6), Review-Report `2026-11-15-8f7a75f0-slice-release-entscheidungen-review.md`, [ADR-0010](../plan/adr/0010-detekt-pre-release.md), [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md), `AGENTS.md` §3.8, `docs/maintainer/releasing.md`. **Bezug:** [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz). **Modell:** claude-sonnet-5-5.

## Urteil

**Konform für beide Liefer-Punkte der DoD; F-1 bis F-4 des Reviews behoben, F-5 und F-6 sinnvoll belassen.** Keine HIGH, keine MEDIUM. 0 LOW, 3 INFO. Die Nachbesserung (f145be91, 2c6dcfc9) wurde nicht erneut reviewt (Register `nachbesserung-ohne-zweiten-review`); sie ist hier Zeile für Zeile gegen den Report gelesen.

Geändert sind nur `docs/maintainer/releasing.md` und der Review-Report (`git diff --stat main...2c6dcfc9`: 2 Dateien). Kein `src/`, kein `testserver/`: nach `harness/README.md` §Minimal agent workflow kein `make clean-build`, kein `make e2e`; beides nicht gefahren.

## Sensoren (selbst gefahren auf 2c6dcfc9, Arbeitsbaum sauber)

| Sensor | Exit | Laufzeit | Beleg |
|---|---|---|---|
| `make docs-check` | 0 | 2 s | 1465 Dateien, 0 Befunde (direkter Exit-Code, nicht über Pipe) |
| `make flaeche` | 0 | 2 s | |
| `make kopplung-zahlen` | 0 | 1 s | |
| `make gates` | 0 | 10 s | Gruppen `werkzeug`, `tests`, `stempel` wegen Beleg übersprungen (Hashes `2bcb62fb…`, `955888fe…`, `5c5b7912…`); Grün der Gruppen ist Beleg-Grün, kein Frischlauf, `FORCE=1` nicht gefahren |

Gesamtlaufzeit etwa 15 s, größter Posten `make gates` (10 s). Nach Anlegen dieses Berichts lief `make docs-check` erneut (siehe Schluss).

## DoD (Slice §2)

| Punkt | Urteil | Beleg |
|---|---|---|
| 1 E2E im Release und Wächter | **konform** | Schritt 2: `make e2e` ist ein Schritt, kein Gate, Ergebnis und Dauer im Release-Vermerk (Schritt 8), Nutzerentscheidung 2026-11-14 genannt, statt „nicht entschieden“. Abschnitt Wächter, „Grenze“: Entscheidung, dass es beim Schritt bleibt, Werkzeug-Target „besteht nicht und ist nicht vorgesehen“; der Satz „ob es entsteht, entscheidet der Nutzer“ ist entfallen. Die Zeilen „Ob `make e2e` Teil eines Releases ist“ und zum Wächter-Target stehen nicht mehr unter „Offene Entscheidungen“. Kein neues Target, `harness/README.md` nicht im Diff (keine Zeile in §Sensors). |
| 2 Stückliste und Jar ohne Registry zurückgestellt | **konform** | Schritt 6, „Nicht Teil dieses Ablaufs“ und „Offene Entscheidungen“ tragen denselben Kernwortlaut „nicht jetzt entschieden, nach der ersten Veröffentlichung neu zu bewerten“, je mit „(Nutzer, 2026-11-14)“; die Einbettung (Schritt 6 „er ist …“, Klammer mit „nicht gebaut;“, Absatz „Zurückgestellt …“) unterscheidet sich, der Wortlaut zu (c) nicht. „Offene Entscheidungen“ führt als Liste nur Lizenzhinweise zu `lib/` und Dateirechte der Benutzerliste. |

Nachweis-Pflichten des Slice (Gates, Review-Report) erfüllt; Closure-Notiz (§7), Register-Fortschreibung und der Ausgang der Risiken §6 sind laut Slice „bei der Closure“ fällig und hier nicht Gegenstand.

## Entscheidungen des Nutzers (nur Entschiedenes)

| Entscheidung 2026-11-14 | Eintrag | Urteil |
|---|---|---|
| (a) `make e2e` Schritt der Anleitung, kein Gate | Schritt 2, Schritt 8 (Dauer nach Nutzerentscheidung 2026-11-15, Code angleichen an „Ergebnis und Dauer“) | konform; „Release-Stand“ nicht mehr eingetragen |
| (b) Wächter ohne neues Target | Abschnitt Wächter, Grenze | konform |
| (c) nicht jetzt, nach erster Veröffentlichung neu bewerten | Schritt 6, „Nicht Teil“, „Offene Entscheidungen“ | konform; Zusatz „die Neubewertung hat keinen Sensor“ ist Tatsache nach Slice §6, keine Setzung |

Die Dauer im Vermerk (Schritt 8: „dazu die Dauer von `make e2e`“) ist durch die Nutzerentscheidung 2026-11-15 getragen und dem Slice-Wortlaut „Ergebnis und Dauer“ der DoD gleich.

## Diff gegen den Plan (Plan §3: nur `releasing.md`)

`git diff main...2c6dcfc9 -- docs/maintainer/releasing.md` trägt Hunks nur an: Schritt 2, Schritt 6 (letzter Satz), Schritt 8 (ein Einschub in der Vermerk-Klammer), „Nicht Teil dieses Ablaufs“, Wächter-„Grenze“, „Offene Entscheidungen“. Kopf, Schritte 1, 3, 4, 5, 7, 9, Tag-Schutz und Freshness-Audit: keine Hunks. Schritt 8 ist bis auf die Dauer-Ergänzung unverändert (der Slice §1 weist Schritt 8 dem Vorgänger-Slice zu; die Ergänzung ist durch die Nutzerentscheidung 2026-11-15 gedeckt, siehe V-1). Außerhalb von `releasing.md` ist nur der Review-Report neu.

## Review-Findings

| Finding | Behoben? | Beleg |
|---|---|---|
| F-1 MEDIUM Zahlen an zweitem Ort | **ja** | Die Zahlen 103 s und 339 s stehen nicht mehr in `releasing.md`; Schritt 2 verweist auf die Zeile `make e2e` in `harness/README.md` §Sensors (einzige Stelle, dort mit Datum der Einzelmessung). `grep` auf `103 s`/`339 s` in `releasing.md`: kein Treffer. |
| F-2 LOW Stufe-8-Satz enger als Beleg | **ja** | Der Satz „für Stufe 8 und für andere Hosts ist die Dauer nicht gemessen“ ist entfallen; die Aussage über Umfang und Dauer liegt allein bei der Zeile in `harness/README.md`. |
| F-3 LOW „Release-Stand“ als Zugabe | **ja** | „Er läuft auf dem Release-Stand“ steht nicht mehr; Schritt 8 nennt die Dauer. |
| F-4 LOW Wortlaut (c) | **ja** | siehe DoD-Punkt 2: Kernsatz an drei Stellen wörtlich gleich. |
| F-5 INFO ADR-0047 | **sinnvoll belassen** | [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md) ist `Accepted`, `AGENTS.md` §3.5; ihre Nachzugs-Zeilen (Z. 39, 162) beschreiben den Stand vor dem Nachzug und sind als „Nachzug im Folge-Slice“ geführt. Nicht geändert, richtig. |
| F-6 INFO Absatz „Zurückgestellt“ | **sinnvoll belassen** | Der Absatz ist als Zurückstellung gekennzeichnet, die Liste unter der Einleitung führt nur Offenes; folgt Plan §2. |

## Querverweise (Suche `Offene Entscheidungen`, `Nicht Teil dieses Ablaufs`, `Wächter` in `docs/`, `harness/`, `spec/`)

- Trägt die neue Lage: Kopf von `releasing.md` („die Entscheidungen, die der Nutzer noch nicht getroffen hat“, Anker `#offene-entscheidungen`, löst auf, `docs-check` grün), Schritt 4 (Anker Wächter, unverändert gültig). `spec/lastenheft.md` und `spec/spezifikation.md` führen eigene Abschnitte „Offene Entscheidungen“, unabhängig von `releasing.md`. `harness/` und `AGENTS.md` verweisen nicht auf die geänderten Abschnitte.
- Benannt, nicht geändert (Accepted-ADR): [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md) Z. 39 und 162 (Stand vor dem Nachzug, F-5).
- Benannt, nicht geändert (Zeitdokumente): `done/slice-release-doku-prozess.md` (Z. 41, 149, 155, 157, benennt `slice-release-entscheidungen` als Träger der Entscheidungen, stimmt), `done/slice-maven-veroeffentlichung.md` (Z. 45, 119: Wächter-Form „offen“, Träger dieser Slice), `welle-referenzabgleich-release.md` (Z. 63). Alle sind Zeitdokumente oder Plan der laufenden Welle; kein Nachzug nötig.

## Findings

| ID | Kategorie | Befund | Pfad |
|---|---|---|---|
| V-1 | INFO | Slice §1 grenzt „Änderungen an Schritt 1, 8 und 9“ aus; die Dauer-Ergänzung in Schritt 8 weicht davon ab. Sie folgt der Nutzerentscheidung 2026-11-15 (Entscheidung (a) „Ergebnis und Dauer“) und der Anweisung des Auftrags; der Slice-Text nennt sie nicht. Bei der Closure in §7 vermerken oder §1 anpassen. | `docs/plan/planning/in-progress/slice-release-entscheidungen.md` §1 |
| V-2 | INFO | Die Gruppen von `make gates` liefen per Stempel-Beleg, nicht frisch; für eine reine Doku-Änderung nach Tabelle ausreichend (die Stage `test` kopiert weder `spec/` noch `docs/`). | `make gates`-Ausgabe |
| V-3 | INFO | Die Neubewertung „nach der ersten Veröffentlichung“ hat keinen Sensor und keinen Auslöser außer der Closure von `slice-maven-erste-veroeffentlichung` (Slice §6, offen bis zur Closure); die Anleitung sagt das selbst. | `releasing.md` „Offene Entscheidungen“ |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 0 |
| LOW | 0 |
| INFO | 3 |

## Verdikt

**Konform.** Beide DoD-Punkte wörtlich erfüllt, nur Entschiedenes eingetragen, Plan-vs-Diff ohne Abweichung außer V-1 (durch Nutzerentscheidung gedeckt). Kein Befund blockiert die Closure. Bezug: [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz), [ADR-0010](../plan/adr/0010-detekt-pre-release.md), [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md).
