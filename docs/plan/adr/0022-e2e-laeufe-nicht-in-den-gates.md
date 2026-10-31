# ADR-0022: E2E-Läufe gehören nicht in die Gates

**Status:** Accepted

**Datum:** 2026-10-31 (Accepted, vom Nutzer entschieden)

**Autor:** pt9912

**Bezug:** [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-RB-01](../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [ADR-0008](0008-dotnet-kompatibilitaetstests.md), [ADR-0012](0012-simulations-backend-beschreibung-und-formen.md), [ADR-0018](0018-laufzeit-artefakt.md), [ADR-0019](0019-kompatibilitaetstests-zuschnitt.md)

**Schärft:** [SPEC-052](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) und [SPEC-040](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) (beide nennen heute ein Gate; nach Annahme dieser ADR ziehen sie nach, siehe Konsequenzen). Kein angenommener ADR-Satz wird überstimmt; kein `Supersedes`. Berührt: [ADR-0018](0018-laufzeit-artefakt.md) Entscheidung 4 und Titel (Proposed, siehe Konsequenzen).

**Regeln:** Baseline-Regelwerk `modul-04-adrs.md`
§Ziel-Form: ADR (MADR).

---

## Kontext

**Nutzerentscheidung vom 2026-10-31:** „e2e gehören nicht in die gates“, präzisiert: „eigener Durchgang — und eigener Workflow beim Pushen“. Die Selbstprüfung des Harness (`tools/harness/selbstpruefung.sh`) bleibt Werkzeug ohne Gate, „nur wenn es unseren Prozess nicht ausbremst“.

**Ist-Zustand** (aus `GATE_CHECKS` gelesen): `make gates` führt `test`, `detekt`, `build`, `detekt-selbsttest`, `docs-check`, `baseline-verify`, `referenztest-dotnet` und zwei Läufe, die den Serverprozess über seine Außengrenze ansprechen:

- `laufzeittest` (Container-Laufzeittest: Image-Inhalt, Start, Rohbytes-Login aus einem zweiten Container, `docker stop`). Aufnahme nach einem Maßstab (Setzung im Kopf von `tools/mk/laufzeittest.mk`): Warmlauf unter der Laufzeit der Stage `test` ohne Cache. [SPEC-052](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) nennt es Gate.
- `simulations-integration` (Referenzclient `Microsoft.Data.SqlClient` gegen den Serverprozess mit Simulations-Backend). [SPEC-040](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) und `harness/README.md` nennen es Gate.

`make smoke` (Kind-JVM startet `MainKt`, Rohbytes-Client über die Prozessgrenze) läuft in der Stage `test` und hängt damit an `make test`, ohne eigenen Eintrag. `referenztest-dotnet` baut und prüft den Referenzclient ohne TDS-Server; es spricht keinen Serverprozess an. Die CI-Datei `.github/workflows/gates.yml` führt allein `make gates`.

**Messwerte** (Einzelmessungen, je ein Lauf, keine Streuung; Quelle: Kopf der beiden Fragmente und `harness/README.md`, gemessen am 2026-10-31): `laufzeittest` warm 24 bis 31 s, kalt 88 bis 171 s; `simulations-integration` warm 19 bis 25 s, kalt 131 s; Stage `test` ohne Cache 89 bis 102 s. CI-Lauf 41193283726: 8 min 58 s gegen rund 7 min 43 s davor (ein Lauf; die Differenz enthält nur das Gate `laufzeittest`, die CI-Laufzeit mit `simulations-integration` ist nicht gemessen). Der Aufschlag beider Gates auf `make gates` kalt (rund 100 bis 120 s) und warm (rund 40 s) ist eine Addition dieser Einzelwerte, keine eigene Messung.

**Warum `laufzeittest` ins Gate kam:** Der Review zum Laufzeit-Artefakt (Befund F-1, Report vom 2026-10-31 unter `docs/reviews/`, Commit `1c5a26a`) fand: Ohne einen Lauf über das Jar bleibt ein defekter `Class-Path` oder Jar-Name bei `make gates` grün, weil `make smoke` die Klasse `MainKt` startet, nicht das Jar. Das Gate war die Antwort auf diesen Befund; der eigene Workflow beantwortet ihn jetzt ebenfalls (der Push löst ihn aus), M1 unten zusätzlich billig im Gate.

**Regel:** [AGENTS.md](../../../AGENTS.md) §3.6 — ein Gate wird nur per ADR gelockert. Der Auszug zweier Läufe aus `GATE_CHECKS` ist eine Lockerung der Gate-Kette und braucht diese ADR; die Nutzerentscheidung ist der Anlass, nicht der Träger.

**Annahme, die kippt:** Dass E2E-Läufe teuer genug bleiben, um den Prozess zu bremsen. Werden sie billig, entfällt der Grund für die Trennung vom Gate (nicht der für den eigenen Workflow).

## Entscheidung

Wir wählen **Option B**.

1. **Begriff:** Ein E2E-Lauf ist ein Lauf, der den Serverprozess über seine Außengrenze anspricht — als Container oder als Kindprozess, mit Referenzclient oder Rohbytes-Client über die Prozessgrenze. Ein Test im Modul mit Zugriff auf `internal` ist kein E2E-Lauf.
2. **Eigener Durchgang:** E2E steht nicht in `GATE_CHECKS`. `laufzeittest` und `simulations-integration` verlassen die Gate-Kette und bleiben als Targets bestehen. Sie laufen gebündelt über `make e2e` (Produkt-E2E, eigene Laufzeit und eigene Messung; die Sicht darauf ist `docs/plan/e2e-abdeckung.md`, erzeugt von `make e2e-abdeckung`).
3. **Eigener Workflow beim Push:** Ein CI-Workflow `.github/workflows/e2e.yml` (`name: e2e`) läuft bei `push` und `pull_request` wie `gates.yml`, mit eigenem Job, eigenem Zeitlimit, `permissions: contents: read` und SHA-gepinnten Actions wie in `gates.yml`; er ruft `make e2e`. `gates.yml` ruft weiter nur `make gates`. Die Datei `e2e.yml` legt diese ADR nicht an; sie ist Folgepflicht nach `Accepted`. `make workflow-lint` (actionlint) gilt für die neue Datei.
4. **Was in den Gates bleibt:** Unit- und Integrationstests im Modul, `make smoke` in der Stage `test`, die billigen Prüfungen im Bau (Lizenz in `build-jar`, Digest-Pins), `referenztest-dotnet` (kein Serverprozess), `detekt`, `detekt-selbsttest`, `docs-check`, `baseline-verify`. Die Liste der Gates steht einmal, in `harness/README.md`.
5. **Ersatz für F-1:** Eine Manifest-Prüfung im Bau (Option M1, siehe unten), ohne Container.
6. **Harness-Selbstprüfung:** bleibt Werkzeug ohne Gate. Der Vorbehalt des Nutzers („nur wenn es unseren Prozess nicht ausbremst“) ist Bedingung dieser ADR; eine Aufnahme in ein Gate bräuchte eine neue Entscheidung mit Messung.
7. **Maßstab:** Der Maßstab „Warmlauf unter der Stage `test` ohne Cache“ aus dem Kopf von `laufzeittest.mk` gilt für E2E-Läufe nicht mehr; er bleibt für Prüfschritte, die keine E2E-Läufe sind.

Die Details des Workflows (Pflicht-Check, Cache, Logs, `workflow_dispatch`, Release-Bezug) sind unten als Optionen mit Empfehlung geführt.

## Verglichene Alternativen

**Zur Gate-Zugehörigkeit der E2E-Läufe:**

| Option | Pro | Contra |
|---|---|---|
| A — nichts tun: beide Läufe bleiben in `make gates` | Jede Regression im Jar, Image und Referenzclient macht `make gates` rot; kein Beobachtungsloch | Widerspricht der Nutzerentscheidung; kalt rund 100 bis 120 s und warm rund 40 s mehr je Gate-Lauf (Einzelmessungen); jedes weitere E2E (Cancel, TLS am Prozess, Last) wüchse in die Gates hinein |
| **B — E2E aus `GATE_CHECKS`, eigener Durchgang `make e2e`, eigener Workflow beim Push** | Folgt der Nutzerentscheidung; `make gates` bleibt der schnelle Lauf vor jedem Commit; neue E2E-Stufen kosten das Gate nichts; der Push löst E2E selbst aus, das Beobachtungsloch ist nur lokal, nicht in der CI; die Laufzeit von `gates.yml` fällt auf den Stand davor zurück (nicht gemessen) | Lokal sieht `make gates` ein defektes Jar oder Image nicht; ein zweiter Workflow ist zu pflegen (Pins, Zeitlimit); Spec-Zeilen und Doku ziehen nach |
| C — E2E nur vor dem Release, kein eigener Workflow | Geringste laufende Kosten | Widerspricht der Präzisierung; die Lücke bleibt Tage bis Wochen offen |
| D — E2E nach Änderungs-Eingängen in `make gates` (wie `detekt-selbsttest`) | Lücke F-1 bleibt zu | Widerspricht der Nutzerentscheidung; die Eingangsliste ist eine zweite Wahrheit, die driftet |

**Zu den Details des Workflows** (jeweils Empfehlung fett; keine ist Voraussetzung der Entscheidung 3):

| Detail | Optionen | Empfehlung und Grund |
|---|---|---|
| Wirkung | **P1 — Pflicht-Check (Merge blockiert bei Rot)** · P2 — sichtbar rot, ohne Sperre | P1, wenn die Laufzeit des Workflows tragbar ist: ein rotes E2E, das kein Merge aufhält, wird gelesen oder nicht. Ob das Repo Pflicht-Checks erzwingt, ist Einstellung der Plattform und hier nicht geprüft; ohne Erzwingung gilt faktisch P2 |
| Parallelität | **eigener Job parallel zu `gates.yml`** · nach `gates` in Folge | Parallel: die Gesamtzeit ist das Maximum, nicht die Summe; der Preis ist doppelter Bau der Images (Cache unten) |
| Cache | **kein Cache zunächst, Kaltlauf messen** · Docker-Layer-Cache der Plattform | Der Kaltlauf liegt bei 88 bis 171 s bzw. 131 s je Lauf (Einzelmessungen); ein Cache ist ein eigener Eingang für Drift und gehört erst bei einer gemessenen Notwendigkeit eingeführt |
| Logs bei Fehlschlag | **Konsolenausgabe des Laufs genügt** · Artefakt-Upload | Die Läufe schreiben auf die Konsole; ein Upload ist eine zusätzliche Action mit Pin, erst bei einem Fall, in dem die Konsole nicht reicht |
| `workflow_dispatch` zusätzlich | **ja** · nein | Ein Handstart ohne Push (z. B. auf einem Release-Stand) kostet eine Zeile; `gates.yml` zeigt das Muster nicht, das ist beim Anlegen zu prüfen |
| Release-Bezug | **grüner E2E auf dem Release-Stand ist Bedingung** · nur Hinweis | Der Release-Prozess entscheidet; die ADR schlägt es vor |

**Zum Ersatz für F-1 (Jar-Manifest):**

| Option | Pro | Contra |
|---|---|---|
| M0 — kein Ersatz | Kein Aufwand | F-1 bleibt lokal offen; ein defekter `Class-Path` fiele nur im E2E-Workflow auf |
| **M1 — Manifest-Prüfung im Bau (`build-jar`, ohne Container)** | Fängt den Fall des Befunds (Main-Class falsch, Class-Path-Eintrag ohne Datei in `lib/`, Jar-Name) schon in `make gates`; die Lizenzprüfung im selben Schritt zeigt, dass die Bauart trägt | Prüft das Manifest, nicht den Start: Entrypoint der Stage `runtime`, Nutzerrechte und Basis-Image bleiben bis zum E2E unbeobachtet |
| M2 — `java -jar tdswire.jar` kurz im Bau starten (Kindprozess ohne Container) | Fängt auch eine falsche Klasse oder fehlende Bibliothek zur Ladezeit | Braucht einen Startmodus, den das Produkt heute nicht hat; Sekunden Laufzeit im Gate; mehr Aufwand als M1 |

**Aufwandsschätzung M1** (Schätzung, nicht gemessen): ein Prüfskript von rund 20 Zeilen `bash` im Schritt `build-jar` (`unzip -p` auf `META-INF/MANIFEST.MF`, `Main-Class` gegen die Konstante, jeder `Class-Path`-Eintrag gegen `lib/`), plus ein Negativtest (Mutation am Class-Path, Bau rot). Der Lauf kostet im Bau Sekunden, kein Docker-Start. Ein kleiner Folge-Schritt, kein eigenes Dokument.

## Konsequenzen

- Positiv: `make gates` ist der schnelle Lauf; neue E2E-Stufen kosten das Gate nichts; eine Folge-ADR je neuem E2E-Lauf (Gate ja/nein) entfällt, weil der Begriff in Entscheidung 1 die Zuordnung trägt.
- Negativ: siehe „Akzeptierte Negative“.
- Folgepflicht (nach `Accepted`, durch einen Implementer, nicht durch diese ADR):
  - `.github/workflows/e2e.yml` anlegen (Entscheidung 3), `make workflow-lint` fahren; `gates.yml` bleibt unverändert.
  - `tools/mk/laufzeittest.mk` und `tools/mk/simulationstest.mk`: `GATE_CHECKS +=` entfernen, Kopfkommentar auf „kein Gate“ nachziehen (der Maßstab-Absatz entfällt, die Messwerte bleiben als Zusage).
  - `make e2e` und das Produkt-E2E-Skript einführen (Entscheidung 2); `make e2e` ist heute nicht vorhanden.
  - `harness/README.md`: beide Zeilen wandern aus der Tabelle der Gates in die der Werkzeuge; `make e2e` bekommt dort seine Zeile mit Bindung auf diese ADR; die Gate-Liste bleibt einmal geführt, der Workflow `e2e.yml` wird dort nur verlinkt, nicht als Gate gelistet. Der Abschnitt „Aktueller Lauf-Status“ nennt dann zwei Workflows.
  - Spec-Nachzug: [SPEC-052](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) (Titel „Gate: `make laufzeittest`“ und der Satz „Das Ziel steht in `make gates`“) und [SPEC-040](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) („Teil von `make gates`“); die Spec fasst diese ADR nicht an.
  - [ADR-0018](0018-laufzeit-artefakt.md) (Proposed): Titel „Laufzeittest als Gate“ und Entscheidung 4 widersprechen dieser ADR; sie wird vor ihrer Annahme angepasst (Verweis auf diese ADR) oder mit dem Vermerk angenommen, dass Entscheidung 4 durch diese ADR ersetzt ist. Welcher Weg, entscheidet die Reihenfolge der Annahme.
  - [ADR-0019](0019-kompatibilitaetstests-zuschnitt.md) (Proposed) ist zu prüfen: `referenztest-dotnet` bleibt Gate und ist nicht betroffen; die dort als noch einzurichtend genannte Pflicht-Matrix der Referenzclient-Läufe gegen den Serverprozess fiele unter den Begriff E2E und gehörte nicht in `GATE_CHECKS`.
  - M1 einbauen (Entscheidung 5).

## Akzeptierte Negative

1. **Lokal kein Fänger für Regressionen im E2E-Bereich.** Eine Regression im Jar-Start, im Image (Stage `runtime`, Entrypoint, Nutzerrechte), im Referenzclient-Pfad oder im Serverprozess über die Grenze zeigt `make gates` nicht mehr; sie zeigt der eigene Workflow beim Push (bzw. `make e2e` von Hand). Das Negativ ist damit kleiner als ohne Workflow: es betrifft den Weg bis zum Push, nicht die CI. Gemildert durch `make smoke` in der Stage `test` (über `MainKt`, nicht über das Jar) und durch M1.
2. **Der Entrypoint der Stage `runtime` bleibt im Gate ohne Fänger.** M1 prüft das Manifest, nicht die Startzeile des Images. Ein Fehler dort fiele im E2E-Workflow auf.
3. **Ein roter E2E-Workflow, den niemand liest oder der nicht sperrt, schützt nicht.** Gebunden durch die Wirkung P1 und den Trigger unten.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| `make` | Kein E2E-Lauf steht in `GATE_CHECKS`: die Fragmente unter `tools/mk/` zeigen nach Umsetzung kein `GATE_CHECKS += laufzeittest` und kein `GATE_CHECKS += simulations-integration` | heute kein Sensor; nicht behauptet |
| actionlint | `.github/workflows/e2e.yml` ist syntaktisch gültig | `make workflow-lint` (prüft weder SHA-Pins noch, dass der Workflow `make e2e` aufruft) |
| CI | Der Workflow `e2e` führt `make e2e` | `make e2e` (Ziel entsteht mit der Umsetzung) |

## Offen

Gesetzt am 2026-10-31 (Setzung der Koordination nach der Nutzerentscheidung, mit den Empfehlungen der Tabellen oben; nicht gemessen):

- **Wirkung P1:** der Workflow `e2e` ist Pflicht-Check für `main`. Die Erzwingung ist eine Einstellung der Plattform und im Repo nicht abbildbar; sie ist vom Nutzer einzurichten. Ohne sie gilt faktisch P2.
- **Release-Bezug:** ein grüner E2E auf dem Release-Stand ist Bedingung des Releases.
- **Manifest-Prüfung:** M1 im Bau (`build-jar`, ohne Container).
- **Waisen-Schalter der E2E-Sicht:** später; `make e2e-abdeckung` bleibt ein Bericht ohne Urteil, und ein Schalter in `GATE_CHECKS` wäre eine eigene Entscheidung (Anforderung ohne E2E-Stufe ist nicht E2E-Lauf rot).
- **Cache, Logs, `workflow_dispatch`:** wie in der Tabelle empfohlen (kein Cache zunächst, Konsolenausgabe, `workflow_dispatch` ja).

## Re-Evaluierungs-Trigger

- `make e2e` wird warm so billig, dass es nicht mehr ins Gewicht fällt (Vorschlag: unter einem Zehntel der Stage `test` ohne Cache): die Aufnahme in `make gates` ist neu zu prüfen.
- Eine Regression, die `make gates` grün durchließ und erst der E2E-Workflow fand, oder ein Release mit defektem Artefakt: die Entscheidung wird neu bewertet; M2 oder Option D sind dann die ersten Kandidaten.
- Der E2E-Workflow war über vier Wochen (Vorschlag) nie rot und nie gelesen, oder er ist wiederholt aus Infrastrukturgründen rot: Wirkung (P1/P2) und Zuschnitt neu bewerten.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-10-31 | Nutzerentscheidung „e2e gehören nicht in die gates“, präzisiert „eigener Durchgang — und eigener Workflow beim Pushen“ | Sitzung |
| 2026-10-31 | Proposed | — |
| 2026-10-31 | Accepted – vom Nutzer entschieden („das wird uns ausbremsen, wenn die tests im gate bleiben“); offene Fragen mit den Empfehlungen gesetzt | Sitzung |
