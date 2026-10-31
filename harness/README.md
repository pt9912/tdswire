# Harness

---

## Purpose

Dieser Harness verbindet bestehende Spezifikationen, ADRs,
Planning-Dokumente und Gates. Er ist **kein Ersatz** für `spec/` oder
`docs/`, sondern ein **Einstiegspunkt** für Menschen und AI-Code-Agenten.

Wenn diese Datei einer kanonischen Quelle widerspricht, **gewinnt die
kanonische Quelle**, und diese Datei wird angepasst.

Strukturregeln (Verzeichniskonvention, ID-Schemata, Modus-Deklarationen
pro Sub-Area, Zusatzklassen für Sensors-Bindung) sowie Adaptionen ggü.
der adoptierten Baseline leben in [`conventions.md`](conventions.md).
Diese Datei dupliziert sie nicht.

## Source precedence

| Rang | Datei | Charakter |
|---|---|---|
| 1 | [`spec/lastenheft.md`](../spec/lastenheft.md) | vertraglich abnahmebindend |
| 2 | [`spec/spezifikation.md`](../spec/spezifikation.md) | technisch fortschreibbar |
| 3 | [`spec/architecture.md`](../spec/architecture.md) | Komponenten/Sequenzen, meilensteinfrei |
| 4 | [`docs/plan/adr/`](../docs/plan/adr/) | Architekturentscheidungen |
| 5 | [`docs/plan/planning/in-progress/roadmap.md`](../docs/plan/planning/in-progress/roadmap.md) | Wellen-Sequenz |
| 6 | `docs/user/*` und `docs/maintainer/*` *(falls vorhanden)* | Nutzer-Doku (Betrieb, Qualität); Maintainer-Doku (z. B. `releasing.md`) | <!-- d-check:ignore (Verzeichnisse optional; entlinkt, da im frischen Repo selten vorhanden) -->
| 7 | [`README.md`](../README.md) | Projekt-Überblick |
| 8 | [`AGENTS.md`](../AGENTS.md) | Agent-Briefing |
| 9 | diese Datei | Harness-Einstieg |

> Die Ränge 1–3 sind die **drei Spec-Straten** — Vertrag, Technik, Sicht —,
> und alle drei sind obligatorisch (Baseline-Regelwerk
> `grundlagen-referenz-richtung.md` §Spec-Straten). **Adaption ist die
> Zwei-Straten-Form, nicht die Drei-Straten-Form**: Wer Rang 2 streicht,
> deklariert das als `MR-<NNN>` in [`conventions.md`](conventions.md) und
> nummeriert neu (dann acht Ränge).

## Guides (Feedforward-Quellen)



| Quelle | Inhalt |
|---|---|
| [`spec/lastenheft.md`](../spec/lastenheft.md) | Anforderungen, IDs, Akzeptanzkriterien |
| [`spec/spezifikation.md`](../spec/spezifikation.md) | technische Details, Defaults |
| [`spec/architecture.md`](../spec/architecture.md) | Komponenten, Schichten, Constraints |
| [`docs/plan/adr/`](../docs/plan/adr/) | Architekturentscheidungen |
| [`docs/plan/planning/`](../docs/plan/planning/) | Slice-Pläne und Roadmap |
| [`AGENTS.md`](../AGENTS.md) | Hard Rules, Source Precedence, Workflow |
| [`conventions.md`](conventions.md) | repo-lokale Strukturregeln, Adaptions-Block (`MR-*`), Modus-Deklarationen |
| `.harness/skills/reviewer.md` | Reviewer-Skill: HIGH-Liste, Kategorien-Regeln, Negativbefund-Pflicht, Output-Schema (Modul 10) — nächste Rolle nach Schritt 8 des Minimal Agent Workflow, nicht Teil der Implementer-Eingabe |
| `.harness/baseline/<tag>/regelwerk/` (vendored; `README.md` = Index) | adoptiertes Betriebsregelwerk in Agenten-Kurzform — **präsente nachschlagbare Vertiefung**, pro Entscheidung abschnittsweise (siehe [`AGENTS.md`](../AGENTS.md) §1); derivativ, Stand/Tag siehe [`conventions.md`](conventions.md) §Baseline |
| `.harness/baseline/<tag>/templates/` (vendored, parallel) | Referenz-Form der Skelette, auf die das Regelwerk mit `../templates/…` als „Ziel-Form" verweist (netzlos, weil parallel zu `regelwerk/`); Vorlagen zum Kopieren-und-Ausfüllen |

## Sensors (Feedback-Gates)



| Target | Vertrag | Bindung |
|---|---|---|
| `make docs-check` | Doku-Referenzen (d-check); das Gate, das die Vorlage mitbringt | — |
| `make detekt` | Suppression-Verbot (`@Suppress`) und detekt mit ktlint, `warningsAsErrors`, Import- und Technik-Regeln der Schichten (`ForbiddenMethodCall` über `detektMain`/`detektTest`), in Docker; keine Baseline | — |
| `make detekt-selbsttest` | Importregeln der Schichten und Suppression-Verbot gegen Testdaten prüfen; läuft nur bei geänderten Eingängen | — |
| `make test` | Unit-Tests (`kotlin.test` auf der JUnit-Plattform) in Docker, Stage `test`; ein roter Test macht `make gates` rot; Lock und Prüfsummen der Testlaufzeit über `make deps-lock` | — |
| `make build` | Compile und Jar in Docker, ohne Tests; Basis-Images per Digest gepinnt | — |
| `make smoke` | Rohbytes-Smoke in Docker (Stage `smoke`): Kind-JVM startet `main`, Rohbytes-Client spricht PRELOGIN und LOGIN7; Start ohne Flag bricht ab; läuft auch in `make test`, kein Referenzclient | — |
| `make referenztest-dotnet` | .NET-Referenztests in Docker ohne Mount, Stage `test`: gesperrter Restore, dann Build und Smoke-Test ohne Netz und ohne TDS-Server; ein roter Test macht `make gates` rot | [ADR-0008](../docs/plan/adr/0008-dotnet-kompatibilitaetstests.md), [CO-001](../docs/plan/carveouts/CO-001-nuget-audit-aus.md), [CO-002](../docs/plan/carveouts/CO-002-nuget-signaturpruefung-fehlt.md) |
| `<make-target>` | <…> | ADR-<NNNN> |
| `<make-target>` | <…>, bootstrap-aware | Schwelle X %, M<n> → Y % |
| `<make-target>` | <…> | bootstrap via `CO-<NNN>` bis <Slice/Welle> |
| `make gates` | alle inneren Gates | — |
| `<make-target>` | gates + extras | — |
| `<make-target>` | volle Closure | Image-Hash `sha256:…` (Modul 14) |
| `make <gate-mit-grenze>` | <…>; Grenze und Ausgänge in der verlinkten Datei | ADR-<NNNN> |

**Werkzeuge — genannt, weil der Lauf sie braucht, aber kein Gate:**

| Target | Tut was | Bindung |
|---|---|---|
| `make deps-lock` | schreibt `gradle.lockfile` und `gradle/verification-metadata.xml` neu (braucht Netz), prüft nichts | kein Gate |
| `make spec-abdeckung` | führt die Tests aus, schreibt `docs/plan/spec-abdeckung.md` (je `SPEC-*` bestandene Tests, Waisen); `SPEC_ABDECKUNG_STRENG=1` bricht bei Waisen; Tag `@spec`: Kopf von `tools/spec-abdeckung.sh` | kein Gate |
| `make spec-abdeckung-selbsttest` | prüft den Erzeuger der Abdeckungsmatrix an einem erzeugten Mini-Bestand: roter, nicht gelaufener, übersprungener und unbekannt getaggter Test zählen nicht | kein Gate |
| `make doc-manual` | liest Handbuch und Spezifikation von d-check; `MANUAL=<begriff>`, hermetisch | kein Gate |
| `make workflow-lint` | prüft Syntax und Ausdrücke von `.github/workflows/*.yml` mit actionlint (Image per Digest, über stdin); prüft weder die SHA-Pins der Actions noch, dass der Workflow `make gates` aufruft | kein Gate, [TDS-QA-06](../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) |
| `make clean-build` | baut Jar, Tests und detekt (Gradle-Stages) mit `--no-cache` in Docker, ohne .NET-Restore; Frischbeleg für Tests mit Wanduhr oder Last, die `make test` aus dem Cache zeigt (seit slice-handshake-login) | kein Gate, [TDS-QA-06](../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) |
| `make referenztest-dotnet-lock` | schreibt `referenztest-dotnet/packages.lock.json` neu (braucht Netz; das erste Lock vertraut nuget.org beim Schreiben), prüft nichts | kein Gate, [CO-002](../docs/plan/carveouts/CO-002-nuget-signaturpruefung-fehlt.md) |
| `make referenztest-dotnet-audit` | meldet bekannte Schwachstellen der .NET-Pakete am Tag des Laufs; Ergebnis hängt am Abfragetag, nicht reproduzierbar, Exit 0 auch bei Fund | kein Gate, [CO-001](../docs/plan/carveouts/CO-001-nuget-audit-aus.md) |
| `make builder-create` | legt mit `BUILDER=<name>` einen buildx-Builder mit CPU-/RAM-Limits an (Setzung `BUILDER_CPUS=8`, `BUILDER_MEMORY=24g`); belegte Optionen und Kaltstart-Grenze: Kopf von `tools/mk/builder.mk` | kein Gate, [TDS-QA-06](../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) |
| `make builder-rm` | entfernt den Builder `BUILDER=<name>` samt seinem Cache | kein Gate, [TDS-QA-06](../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) |
| `make <messung>` | misst <…> gegen <Schwelle> | kein Gate, ADR-<NNNN> |
| `make <vorschau>` | sagt, was <schreibender Lauf> täte; Ausgänge und Sperren in der verlinkten Datei | kein Gate |

**Aktueller Lauf-Status:** lokal `make help` / `make gates`. Die CI-Datei `.github/workflows/gates.yml` führt `make gates`; sieben Läufe auf `main` sind `success` (jüngster abgeschlossener: 41053892627 auf `cbdbd80`, ältester: 41023977695; Abfrage `gh run list --workflow gates`). Ein Badge steht nicht hier; der Stand ist die Abfrage.
**Rote Gates:** Begründung im verlinkten `CO-<NNN>` (siehe Bindung-Spalte), Modul 7.
**Nicht behauptet:** ein Target für den Vergleichslauf D (kein Referenzclient gebaut), die Signaturprüfung der .NET-Pakete (`CO-002`) und ein Gate für die Schwachstellenprüfung (`CO-001`; `make referenztest-dotnet-audit` meldet nur).

**Targets der Werkzeug-Fragmente:** [`mk/ai-harness-init.md`](mk/ai-harness-init.md) — der Teil dieses Index, den `ai-harness-init` bei jedem Lauf neu schreibt; was das Repo über eines dieser Targets entscheidet, steht in dieser Zeile.

**Targets des Repos:** repo-eigene Make-Fragmente liegen in `tools/mk/`. `repo.mk` bindet sie mit `-include tools/mk/*.mk` ein, und `.d-check.yml` führt den Pfad unter `makefiles:`. Jedes ihrer Targets hat seine Zeile in den Tabellen oben; ein Gate hängt sich mit `GATE_CHECKS += <target>` in `make gates`. `harness/mk/` gehört dem Werkzeug und enthält nur dessen Fragmente.

## Traceability rules

- PRs/Commits **müssen** mindestens eine `TDS-*`- oder `ADR-*`-ID nennen.
- Neue oder geänderte Anforderungen brauchen einen Beleg: Test, Gate, Demo oder ADR.
- Neue ADRs müssen im ADR-Index ergänzt werden.
- Änderungen an Planning-Dokumenten müssen die Lifecycle-Regeln beachten (open → next → in-progress → done; reine `git mv`-Commits siehe AGENTS.md §3.3).

## Safety and scope boundaries



- <…>
- <…>

## Minimal agent workflow

1. Diese Datei lesen.
2. Relevante kanonische Quelle lesen.
3. Betroffene IDs identifizieren.
4. Kleinste Änderung planen.
5. Engsten nützlichen Sensor laufen lassen.
6. Repo-weiten Gate-Lauf vor Handoff (`make gates`).
7. Doku/Indizes aktualisieren, falls ein öffentlicher Vertrag berührt.
8. Ausgeführte Sensors und verbleibende Risiken berichten.

Dieser Workflow deckt ausschließlich die Implementer-Rolle ab. Schritt 8
ist der Rollenwechsel, kein Abschluss: Bericht → Handoff an Reviewer
(`.harness/skills/reviewer.md`, siehe §Guides) → Verifier. Kein
Self-Review — anderer Kontext findet andere Findings, derselbe Kontext
dieselben blinden Flecken (Baseline-Regelwerk `modul-08-agentenrollen.md`).

## Leseordnung

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-harness-dateien.md`
§harness/README.md als Einstiegspunkt — die Menschen-Hälfte des Einstiegs:
drei bis fünf **geordnete** Zeiger, was ein neuer Mensch zuerst liest und was
bei Bedarf; eine Leseordnung, die alles nennt, ist keine.

1. <zuerst — z. B. `AGENTS.md` §Hard Rules>
2. <dann — z. B. `spec/lastenheft.md`>
3. <bei Bedarf — z. B. `harness/conventions.md`>
