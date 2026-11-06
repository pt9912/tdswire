# Verifikations-Bericht: slice-adr-handle-grenze-ersatz — 2026-11-06

**Rolle:** Verifier (Modul 11), frischer Kontext. Frage: Bauen wir es richtig, gegen Plan und DoD.

**Gegenstand:** Zweig `slice-adr-handle-grenze-ersatz`, HEAD `b75f58c3`, Diff `cd0e2368..HEAD` (Commits 1a1b4683, 54977d36 Review, b928e120 Nachbesserung, b75f58c3 Accepted). Plan: [slice-adr-handle-grenze-ersatz](../plan/planning/in-progress/slice-adr-handle-grenze-ersatz.md), Ergebnis: [ADR-0036](../plan/adr/0036-handle-freigabe-und-grenze-je-sitzung.md), [Messprotokoll](../messungen/handle-grenze-optionen-2026-11-06.md). Bezug: [ADR-0036](../plan/adr/0036-handle-freigabe-und-grenze-je-sitzung.md), [ADR-0017](../plan/adr/0017-prepared-handles-grenzwert.md), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate).

**Urteil: bestanden mit zwei Nacharbeiten (V-1 MEDIUM, V-2 LOW).** Kein Befund gegen Entscheidung, Belege oder Supersedes.

## Sensor-Belege (selbst gefahren)

- `make docs-check`: 845 Dateien, 0 Befunde (Exit 0).
- `make gates`: Exit 0. Klassen `werkzeug`, `tests`, `stempel` mit Beleg übersprungen (Stempel passt, weil der Diff weder Code noch Spec noch Werkzeug berührt: `git diff --stat cd0e2368..HEAD -- spec src tools config harness .claude` leer); `immer`-Gates (docs-check, gate-zuordnung 15 Gates/33 Fälle, stempel-eingaenge) gelaufen. Kein `FORCE=1`-Lauf; kein Code im Diff, der ihn verlangte.

## DoD (Slice §2)

| Punkt | Befund |
|---|---|
| Optionen mit Messung oder Setzung | Erfüllt in der Sache: Optionen A bis G mit Pro/Contra, jede Zeile Messzeile oder „Setzung/nicht gemessen“; Messprotokoll mit Reihen h, t, u, Kopf „mssql-jdbc 13.4.0 gegen Testserver, nicht d-migrate, nicht echter SQL Server“. Häkchen im Slice **nicht gesetzt** (V-1). |
| ADR mit Entscheidung des Nutzers | Erfüllt, Häkchen gesetzt. Status `Accepted`, Index-Zeile `Accepted (vom Nutzer entschieden)`. Entscheid zitiert („deine empfehlungen“, 2026-11-06). Original **nicht einsehbar**: als vom Auftraggeber berichtet zu werten; die ADR gibt ihn als Zitat mit Datum wieder, ohne Fundort. Option D mit B, Freigabe nur als Batch, RPC-Teil gestrichen: im Text konsistent. |
| Liste der Spec-Änderungen und Anschluss | Erfüllt in der Sache: ADR-Tabelle „Liste der Spec-Änderungen“, Slice-§3-Absatz, Anschluss und Risiko im MERGE-Slice, Eintrag im Flächen-Slice. Häkchen **nicht gesetzt** (V-1). |
| Nachweis-Pflichten (gates, Review, Closure usw.) | `make gates` jetzt grün (hier belegt); Review-Report liegt vor (`docs/reviews/2026-11-06-1a1b468-…`); die Häkchen stehen noch offen, Closure-Notiz/Register/Risiko-Ausgänge offen, solange der Slice in `in-progress/` liegt. |

## Entscheidungs-Konformität

- **Supersedes wörtlich:** Zählsatz aus Entscheidung 1 und Entscheidung 4 vollständig sind in [ADR-0036](../plan/adr/0036-handle-freigabe-und-grenze-je-sitzung.md) wörtlich genannt; `git diff cd0e2368..HEAD -- docs/plan/adr/0017-prepared-handles-grenzwert.md` und `…0005-…md`: leer, beide unverändert. Kein `Supersedes`/`Schärft` zu [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md) (Kopf und Offen 8 sagen es ausdrücklich).
- **Review-Findings am Text:** F-1 behoben (Zählerpunkt „−1 beim Erstaufruf“, Absatz „Ohne RETURNSTATUS …“, Nicht-Ziel 6 „keine Änderung“, SPEC-059, Kompatibilitätsmatrix, `src/`-Kommentare und Tests in der Liste); F-2 durch Streichung des RPC-Teils aufgelöst; F-3 Konsequenz mit Bedingung und Folgepflicht 3; F-4 Offen 7 und Eintrag im Risiko des Flächen-Slice; F-5 Kopfzeile des Slice nennt SPEC-001/058/059, `SPEC-057` nur als „unverändert“ in `Schärft` (stimmig); F-6 Negativfälle in Entscheidung 2 und Fitness Function; F-7 „Treiber allein“, Reihe h3 benannt; F-8 Absatz „Beleglage der Optionen (ungleich)“ (A als kürzester belegter Weg genannt). F-9/F-10 (INFO) im Text berücksichtigt.
- **Aussagen ≤ Beleg:** nur mssql-jdbc 13.4.0 gegen den Testserver gemessen; d-migrate, echter SQL Server, Speicher je Handle, Pooling und .NET stehen als „nicht gemessen“ (Kontext, Konsequenzen, Offen 2 bis 6). Keine Zusage über d-migrate ohne Bedingung gefunden.
- **Keine Code-/Spec-Änderung im Diff:** bestätigt (7 Dateien, nur `docs/`).

## Zweite Fundstellen nach Streichung des RPC-Teils (Grep über Repo, ohne `.harness/`)

Gesucht: `sp_unprepare`, [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md), `Offen 8`, `RPC`, `Proposed`, `Entwurf`.

- [ADR-0036](../plan/adr/0036-handle-freigabe-und-grenze-je-sitzung.md): `sp_unprepare` durchgängig als Batch-Form, der RPC bleibt abgelehnt (Kopf, Entscheidung 2, Option-D-Zeile, Fitness Function, Liste, Offen 5, `SPEC-013` „keine Änderung“). Kein Rest einer RPC-Aufnahme in den Katalog. Offen 8 aufgelöst, keine zweite Fundstelle „offen“. Geschichte-Zeile `Proposed` ist Chronik, zulässig.
- Slice (Risiko-Zeile, Spec-Absatz, Bezug auf [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md) als „unberührt“): konsistent.
- MERGE-Slice und Flächen-Slice: Anschluss, Start-Trigger ([ADR-0036](../plan/adr/0036-handle-freigabe-und-grenze-je-sitzung.md) `Accepted`), Risiko zu d-migrate-Verhalten und Möglicher neuer Name: konsistent mit Batch-Fassung; kein „Proposed“, kein RPC-Bezug.
- ADR-Index: Zeile [ADR-0036](../plan/adr/0036-handle-freigabe-und-grenze-je-sitzung.md) `Accepted`, Titel „ersetzt teilweise [ADR-0017](../plan/adr/0017-prepared-handles-grenzwert.md)“, ohne [ADR-0005](../plan/adr/0005-rpc-und-prepared-statements.md): konsistent.
- Messprotokoll: nennt `sp_unprepare` als gemessene Treiberform (RPC/Batch unterschieden, t5), keine Empfehlung, die dem Entscheid widerspräche.
- `spec/`, `src/`: kein Treffer im Diff; die Alt-Lesart dort bleibt bis zum Träger bestehen und steht in der ADR-Liste.

## Findings

| ID | Kategorie | Befund | Pfad | Klasse |
|---|---|---|---|---|
| V-1 | MEDIUM | Stale zweite Fundstelle des Status: Slice-§3-Tabelle führt [ADR-0036](../plan/adr/0036-handle-freigabe-und-grenze-je-sitzung.md) weiter als „neu (`Proposed`)“, die ADR ist `Accepted`. Zudem sind die DoD-Häkchen „Optionen mit Messung oder Setzung“ und „Liste der Spec-Änderungen und Anschluss“ nicht gesetzt, obwohl die Belege vorliegen; ebenso „Review durchgeführt“ und „`make gates` grün“ (Letzteres jetzt belegt). Der Slice behauptet damit weniger als gebaut ist, die DoD-Bestätigung des Implementers ist nicht im Artefakt nachvollziehbar. | `docs/plan/planning/in-progress/slice-adr-handle-grenze-ersatz.md` Zeile 73 und DoD-Zeilen 50, 52, 56, 57 | Planner/Implementer: nachziehen |
| V-2 | LOW | Entscheid des Nutzers ist in der ADR als Zitat ohne Fundort geführt; im Verifier-Kontext nicht gegen das Original prüfbar. Kein Widerspruch im Text, aber die Quelle (Sitzung/Nachricht) fehlt. | [ADR-0036](../plan/adr/0036-handle-freigabe-und-grenze-je-sitzung.md) Kopf und Geschichte | Hinweis |

Negativbefund: keine Verletzung von AGENTS.md §3.5 ([ADR-0017](../plan/adr/0017-prepared-handles-grenzwert.md)/0005 unverändert), §3.6 (kein Gate gelockert), §3.8 (siehe oben), §3.7 in den geänderten Zeilen (Indikativ). Roadmap führt den Slice unter `in-progress/` (Zustand = Verzeichnis, stimmt).

## Ergebnis

Gegen Plan und Entscheidung konform; die Sensoren `make docs-check` und `make gates` sind grün. Nachzuziehen vor der Closure: V-1 (Status in §3, DoD-Häkchen) durch den Planner; danach Closure-Notiz.
