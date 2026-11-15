# Review-Report: slice-release-entscheidungen — 2026-11-15

**Review-Art:** Code (Doku) gegen Plan §1 bis §3, [ADR-0010](../plan/adr/0010-detekt-pre-release.md), [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md) und Hard Rules

**Gegenstand:** Commit 8f7a75f0, Basis `main` (`git diff main...8f7a75f0`): nur `docs/maintainer/releasing.md` (6 Zeilen neu, 8 entfernt).

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-15

**Eingangs-Kontext:** Slice `slice-release-entscheidungen` (§1 bis §3, §6); [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz); [ADR-0010](../plan/adr/0010-detekt-pre-release.md), [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md); `AGENTS.md` §3.5, §3.7, §3.8; `harness/README.md` §Sensors.

---

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Die Zahlen 103 s und 339 s stehen jetzt in `releasing.md` und in `harness/README.md` (Zelle `make e2e`); kein Test, kein Gate und kein Kopplungskommentar hält beide gleich. Wird die Zelle neu gemessen, bleibt die Anleitung bei den alten Zahlen (`make kopplung-zahlen` deckt nur die Zahlen von `gross`). | Reviewer-Skill (Zahl an zweitem Ort ohne Sensor); `AGENTS.md` §3.7 Klasse Kopplung | `docs/maintainer/releasing.md` · „103 s warm und 339 s unter hoher Last“ | ja — Grep auf beide Orte; kein bestehendes Gate | Zahl an zweitem Ort ohne Sensor |
| F-2 | LOW | Der Satz „für Stufe 8 und für andere Hosts ist die Dauer nicht gemessen“ steht neben einer vorhandenen Einzelmessung mit acht Stufen (`e2e: gruen (8 Stufen, 415 s)`, `welle-produktschnitt-results`, dort als veraltete Zelle in `harness/README.md` vermerkt). Die genannten Zahlen gelten für Stufen 1 bis 7, der Schritt verlangt `make e2e` mit acht Stufen. | `AGENTS.md` §3.8 | `docs/maintainer/releasing.md` · „für Stufe 8 und für andere Hosts ist die Dauer nicht gemessen“ | ja — Abgleich mit Closure-Notiz der Welle | Aussage enger oder älter als vorhandener Beleg |
| F-3 | LOW | Schritt 2 sagt „Er läuft auf dem Release-Stand“. Entschieden ist „Schritt der Anleitung, kein Gate“ (Slice §1 a); der Stand ist eine Zugabe, und der Release-Commit entsteht erst in Schritt 5, nach Schritt 2. Schritt 8 (unverändert) nennt als Vermerk nur „Ergebnis von Schritt 1 bis 4“, Schritt 2 nennt „Ergebnis und Dauer“. | `AGENTS.md` §3.8; Slice §6 (Setzung statt Entscheidung) | `docs/maintainer/releasing.md` · „Er läuft auf dem Release-Stand“ | nein | Setzung ohne Kennzeichnung neben Entscheidung |
| F-4 | LOW | Der Wortlaut zu (c) ist an den drei Stellen nicht identisch, obwohl Slice §2 „denselben Wortlaut“ verlangt: Schritt 6 „nicht jetzt entschieden und nach der ersten Veröffentlichung neu zu bewerten“, „Nicht Teil“ „nicht gebaut; nicht jetzt entschieden, nach der ersten Veröffentlichung neu zu bewerten“ (ohne „und“, mit „nicht gebaut“), Offene Entscheidungen wie Schritt 6 im Plural. Der Sinn ist gleich. | Slice §2 | `docs/maintainer/releasing.md` · „nicht gebaut; nicht jetzt entschieden, nach der ersten Veröffentlichung“ | ja — Grep | Wortlaut-Drift zwischen Fundstellen |
| F-5 | INFO | [ADR-0047](../plan/adr/0047-veroeffentlichung-ueber-github-packages.md) nennt die Software-Stückliste „bleibt offen in `releasing.md`“; das stimmt nach dem Diff nur noch als „zurückgestellt“. Die ADR ist `Accepted` und bleibt unverändert (`AGENTS.md` §3.5); der Slice hat sie nicht berührt. Gleiches gilt für Verweise in `done/`-Slices (Zeitdokumente). | `AGENTS.md` §3.5 | `docs/plan/adr/0047-veroeffentlichung-ueber-github-packages.md` · „bleibt offen in“ | nein | — |
| F-6 | INFO | Der Absatz „Zurückgestellt, nicht offen im Sinn von ungeprüft“ steht unter „Offene Entscheidungen“, dessen Einleitung „Diese Punkte hat der Nutzer nicht entschieden“ lautet. Der Plan verlangt, die Sektion führe nur wirklich Offenes; der Absatz ist als Zurückstellung gekennzeichnet und folgt dem Plan („Zurückstellung und Trigger festhalten“). Zur Kenntnis. | Slice §2 | `docs/maintainer/releasing.md` · „Zurückgestellt, nicht offen im Sinn von ungeprüft“ | nein | — |

## Antworten auf die gezielten Punkte

1. **Nur Entschiedenes eingetragen:** (a) Schritt 2 und (b) Wächter-Grenze stimmen mit den Entscheidungen vom 2026-11-14 überein; (c) ist als „nicht jetzt entschieden, nach der ersten Veröffentlichung neu zu bewerten“ geführt. Hinzugefügt sind „auf dem Release-Stand“ (F-3) und „Neubewertung hat keinen Sensor“ (Tatsache, Slice §6). Kein neues Target, keine Zeile in `harness/README.md`.
2. **Wortlaut (c):** gleicher Sinn, nicht identisch (F-4).
3. **Laufzeiten:** 103 s warm, 339 s hohe Last, Einzelmessungen 2026-11-01, Stufen 1 bis 7 stimmen mit `harness/README.md` §Sensors überein und sind als Einzelmessung gekennzeichnet; Kopplung ohne Sensor (F-1), Stufe-8-Satz (F-2).
4. **Verweis auf Schritt 8:** zeigt auf „Tag und Vermerk“; stimmt. Der Vermerk-Text dort nennt Schritt 2 nicht ausdrücklich als „Ergebnis und Dauer“ (F-3), liegt aber in „Schritt 1 bis 4“.
5. **Unverändert:** Kopf, Schritte 1, 3, 4, 5, 7, 8, 9, Tag-Schutz, Freshness-Audit: keine Hunks (Hunks nur bei Zeile 10, 14, 19, 65 und im Abschnitt „Offene Entscheidungen“).
6. **Offene Entscheidungen / Querverweise:** führt nur noch Lizenzhinweise und Dateirechte (F-6 zum Zusatzabsatz). Der Kopfsatz („Wo etwas fehlt, steht es unter Offene Entscheidungen“) bleibt tragfähig. Verweise im Diff sind unverändert gültig; Gegenstellen siehe F-5.
7. **Kennungen als Links:** alle neuen Kennungen im Diff ([ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), `harness/README.md`) sind Links; `make docs-check` siehe unten.

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `docs/maintainer/releasing.md` Schritte 1, 3 bis 5, 7 bis 9, Kopf, Tag-Schutz, Freshness-Audit | geprüft, ohne Befund (unverändert) |
| `docs/maintainer/releasing.md` Abschnitt Wächter [ADR-0010](../plan/adr/0010-detekt-pre-release.md) | geprüft, ohne Befund (Entscheidung (b) korrekt, Satz „ob es entsteht“ entfällt) |
| Dateien außer `releasing.md` (`harness/README.md`, `spec/`, `src/`, `tools/`) | geprüft, ohne Befund (nicht im Diff; Plan §3 nennt nur `releasing.md`) |
| Suppression, Host-statt-Docker, Schichtregel | geprüft, ohne Befund (kein Code im Diff) |

## Gates

`make docs-check` (Exit 0, 1464 Dateien, 0 Befunde, vor Anlegen des Reports), `make flaeche` (Exit 0), `make kopplung-zahlen` (Exit 0). `make gates` und `make e2e` fuhr der Reviewer nicht (Tabelle `harness/README.md` §Minimal agent workflow, Art „Doku oder Spec“).

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 1 |
| LOW | 3 |
| INFO | 2 |

**Finding-Klassen dieses Laufs:** Zahl an zweitem Ort ohne Sensor · Aussage enger oder älter als vorhandener Beleg · Setzung ohne Kennzeichnung neben Entscheidung · Wortlaut-Drift zwischen Fundstellen

## Verdikt

**Merge-blockierend:** ja, wegen F-1 (MEDIUM; Ausgang: Sensor, Kopplungskommentar auf beiden Seiten oder Zahl entfernen und nur verweisen, entscheidet der Implementer). F-2 bis F-4 sind LOW, F-5 und F-6 INFO.

**Übergabe:** Findings an den Implementer; Finding-Klassen in die Slice-Closure §7. Der Report ersetzt keine Verifikation ([TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz), [ADR-0010](../plan/adr/0010-detekt-pre-release.md), [ADR-0022](../plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md)).
