# Verifikation: slice-release-doku-prozess — 2026-11-06

**Rolle:** Verifier (Modul 11), "Bauen wir es richtig?" gegen DoD (gekürzter Wortlaut, `3258e26e`) und Entscheidungen. Kein Review, keine Reparatur.

**Gegenstand:** `git diff 1c9a85c9..d3d2e63d`, Zweig `slice-release-doku-prozess`. Bezug: [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-QA-06](../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-RB-05](../../spec/lastenheft.md#tds-rb-05--lizenz), [ADR-0010](../plan/adr/0010-detekt-pre-release.md), [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0013](../plan/adr/0013-backend-grenzen.md).

## Eigene Sensor-Läufe

| Lauf | Ergebnis |
|---|---|
| `make docs-check` | 773 Dateien, 0 Befunde, Exit 0 |
| `make gates` | Exit 0. Klassen `werkzeug`, `tests`, `stempel` übersprungen bei passendem Stempel (Diff berührt nur Doku); `docs-check`, `gate-zuordnung`, `stempel-eingaenge` liefen. Der Beleg der Klasse `tests` stammt damit aus früherem Lauf; der Diff enthält keinen Quelltext, kein Gate-Fragment, keine Konfiguration |
| `git diff` auf `src/`, `config/`, `Dockerfile`, `build.gradle.kts`, `tools/`, `spec/`, ADRs | leer; keine Suppression, kein Gate gelockert, keine ADR geändert |
| Konfigurationstabelle gegen `ServerConfig.kt`, `ConfigValidation.kt` | alle 11 Schlüssel, Feldzuordnung, Voreinstellungen (0.0.0.0, 1433, 15 s, 65536, REQUIRED, 30 s, 5 s) und Bereiche (0..65535, 1..3600, 512..65536, Host nicht leer) gleich; `auth` ohne Voreinstellung wie im Code |
| Nicht Gebautes (`MAX_CONNECTIONS`, `request_id`, `METRICS_INTERVAL`, `duration_ms`, `SHUTDOWN_GRACE` als Eingang) | im Review per grep gegen `src/main` belegt; `ServerConfig` führt keines der Felder |

## DoD-Punkte (§2, gekürzter Wortlaut)

| # | Punkt | Befund | Haken |
|---|---|---|---|
| 1 | Nutzer-Doku | Erfüllt. Einbettung über `TdsServer.start`, Testserver als Nicht-Betriebsweg ([ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md)), Konfigurationstabelle, Log/Startdiagnose, Metriken/Spans ausdrücklich als nicht gebaut, Backend-Anbindung (SPEC-022), Simulations-Backend mit Verweis auf SPEC-036 statt Kopie, [ADR-0013](../plan/adr/0013-backend-grenzen.md)-Betriebstext (Schalter, Pflicht für Integratoren, Zahlen als Spike-Messung mit Bedingungen), Grenzen mit Verweis auf Matrix. Rang-6-Links in `AGENTS.md`, `harness/README.md`, `README.md` gesetzt, alle `d-check:ignore`-Marker dazu entfernt (grep: keiner mehr in `AGENTS.md`, `harness/README.md`, Slice-Plan, neuen Docs). Ist-Zustand: keine Register-Namen, kein Datum "2026-10-31", keine Planung | erfüllt |
| 2 | Maintainer-Doku | Erfüllt. Schritte nur für Entschiedenes/Gebautes (Gates, Artefakt, Lizenz); Freshness-Audit als Schritt; Versionsschema, Tag, Registry, Signierung, Stückliste, `make e2e` im Release unter "Offene Entscheidungen" und in den Schritten als offen; die Gate-Bedingung `success` ist als Setzung der Anleitung gekennzeichnet; Rang-6-Pfad gesetzt. Folge-Slice `open/slice-release-entscheidungen.md` liegt vor | erfüllt |
| 3 | Wächter [ADR-0010](../plan/adr/0010-detekt-pre-release.md) | Erfüllt als Schritt 4 plus Abschnitt mit Abfrage (curl auf Maven-Metadaten, grep auf `2.x.y` ohne Zusatz), Gegenprobe über `gh`, Ausgang "neuer Slice mit Supersedes, die ADR bleibt unverändert", Grenze ("kein Auslöser, kein Gate") benannt. Werkzeug-Target als Nutzerentscheidung ausgeklammert | erfüllt |

Haken: In `d3d2e63d` sind die drei Liefer-Punkte unabgehakt (`- [ ]`); vorab gesetzte Haken waren die der Nutzer-Doku (`cc912f6a`), sie sind zurückgenommen. Offen für den Planner/Implementer: Die Haken der drei Liefer-Punkte setzt nach diesem Bericht der Planner; ich habe nichts geändert. Der Haken "make gates grün" steht bereits (`[x]`, Lauf auf `4b7132d0`); ich bestätige ihn durch eigenen Lauf, mit dem Vorbehalt der übersprungenen Stempelklassen (Diff berührt nur Doku). Review-, Closure-, Register- und Risiko-Haken sind offen und gehören dem Planner.

## Review-Findings

| ID | Stand am Text |
|---|---|
| F-1 | Berücksichtigt: DoD 2/3 gekürzt, Entscheidungen im Folge-Slice `slice-release-entscheidungen` (open/), Trigger "in-progress → open" als entfallen begründet. Der Plan-Defekt ist durch Kürzung behoben; Prüfung durch den Architect bleibt dessen Sache |
| F-2 | Behoben: Bedingung ist als "Setzung dieser Anleitung (nicht entschieden)" gekennzeichnet |
| F-3 | Behoben: kein `BEO-TDS`, kein Datum der Nutzerentscheidung in der Nutzer-Doku (grep 0 Treffer) |
| F-4 | Behoben: "Stand dieser Anleitung v6.18.1" entfernt; die Messung steht als "Belegt ist (einmal, 2026-11-06 ...)" mit Bedingung und Grenze (Aussage <= Beleg) |
| F-5 | Behoben: Marker im Slice-Plan entfernt, docs-check grün |
| F-6 | Behoben: Rollen-Text aus dem DoD entfernt, Haken zurückgenommen |
| F-7 | Behoben: Rückgabetyp `UserListResult` (`Valid.list`, `Invalid`) im Text beschrieben |
| F-8, F-9 | INFO, unverändert: Tabelle ohne Sensor gegen Code (in der Doku und §6 selbst als Grenze benannt); Host-Werkzeug `curl`/`gh` in einer Handlung, kein Gate |

## Verbleibende Befunde

- V-1 (INFO): Der Beleg der Klassen `tests`/`stempel` ist ein Stempel, kein frischer Lauf (Doku-Diff). Wer einen Frischbeleg will: `make gates FORCE=1`. Nicht gefahren, weil kein Code im Diff.
- V-2 (INFO): Die Tabelle der Nutzer-Doku und `ServerConfig` halten sich nur durch Lesen (benannte Grenze, kein Mangel dieses Slice). Abdeckung des Satzes "Einbettungstest EmbeddingTest" und der Test-Namen in der Doku habe ich nicht einzeln neu ausgeführt; `make test` lag im übersprungenen Stempel.
- Keine DoD-Verletzung, keine Entscheidung des Nutzers gesetzt, keine Aussage über dem Beleg gefunden, kein Gate gelockert.

## Verdikt

**DoD-konform** (gekürzter Wortlaut): alle drei Liefer-Punkte erfüllt, `make docs-check` und `make gates` grün. Übergabe an den Planner: Haken der Punkte 1 bis 3 setzen, Closure/Register/Review-Haken schließen, Slice nach `done/` (Folge-Slice `slice-release-entscheidungen` bleibt `open/`).
