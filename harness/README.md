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
| 6 | [`docs/user/`](../docs/user/README.md), [`docs/maintainer/`](../docs/maintainer/releasing.md) | Nutzer-Doku (Betrieb, Qualität); Maintainer-Doku (`releasing.md`) |
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
| `make docs-check` | Doku-Referenzen (d-check); das Gate, das die Vorlage mitbringt | Klasse `immer`, [ADR-0024](../docs/plan/adr/0024-lokale-stempel-zwei-klassen.md) |
| `make skript-selbsttest` | Abbruch von `e2e.sh` an roter Stufe; Fehler von `manifest-pruefung.sh` (`Main-Class`, Testserver, Jackson), Aufruf in `build-jar`; `vergleichslauf-d.sh`: Abbruch ohne EULA, in der CI, Live-Vergleich | Klasse `werkzeug`, [ADR-0024](../docs/plan/adr/0024-lokale-stempel-zwei-klassen.md), [ADR-0022](../docs/plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md) |
| `make detekt` | Suppression-Verbot, Jackson-Sperre (`src/main`), Modulgrenze-Sperre, detekt (ktlint, `warningsAsErrors`), Schicht- und Ausgabe-Regeln, auch `integrator`, `testserver`; keine Baseline | Klasse `tests`, [ADR-0024](../docs/plan/adr/0024-lokale-stempel-zwei-klassen.md) |
| `make detekt-selbsttest` | Regeln der Schichten, Ausgabe-, Suppression-, Jackson- und Modulgrenze-Sperre gegen Testdaten prüfen; übersprungen bei Stempel, `src/main` ist Eingang (jede Änderung dort macht ihn ungültig) | Klasse `werkzeug`, [ADR-0024](../docs/plan/adr/0024-lokale-stempel-zwei-klassen.md) |
| `make kopplung-zahlen` | Heap und Text von `gross` des Integrationslaufs in Gradle, Kotlin, .NET, `SPEC-040` und README gleich; Selbsttest mit acht Mutationen (Stage `kopplung-zahlen`, ohne Gradle) | Klasse `werkzeug`, [ADR-0024](../docs/plan/adr/0024-lokale-stempel-zwei-klassen.md), [ADR-0023](../docs/plan/adr/0023-gate-klassen-und-stempel.md) |
| `make flaeche` | Typnamen der Liste in `SPEC-059` gegen die öffentlichen Typdeklarationen und ihre Verwendung in `src/integrator` und `testserver/kotlin`; vergleicht keine Mitglieder; Selbsttest mit 14 Fällen | Klasse `immer`, [ADR-0024](../docs/plan/adr/0024-lokale-stempel-zwei-klassen.md), [TDS-QA-05](../spec/lastenheft.md#tds-qa-05--wartbarkeit) |
| `make test` | Unit-Tests in Docker, Stage `test`; Einbettungstest `integrator` (nur öffentliche Fläche, ohne Jackson) und die Tests des Moduls `testserver`; ein roter Test macht `make gates` rot | Klasse `tests`, [ADR-0024](../docs/plan/adr/0024-lokale-stempel-zwei-klassen.md), [ADR-0021](../docs/plan/adr/0021-oeffentlicher-integrations-einstieg.md) |
| `make build` | Bibliotheks-Jar ohne `Main-Class` in Docker (Stage `build-jar`), ohne Tests und Image; Lizenz und Manifest (keine `Main-Class`, `Class-Path` gegen `lib/`, kein Jackson) im Bau geprüft | Klasse `tests`, [ADR-0024](../docs/plan/adr/0024-lokale-stempel-zwei-klassen.md), [ADR-0022](../docs/plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md) |
| `make smoke` | Rohbytes-Smoke in Docker (Stage `smoke`): Kind-JVM startet das `main` des Testservers, Rohbytes-Client spricht PRELOGIN und LOGIN7; Start mit TLS ohne JVM-Option gelingt; läuft auch in `make test` | — |
| `make referenztest-dotnet` | .NET-Referenztests in Docker, Stage `test`, auch Mitschnitt; in `make gates` übersprungen bei passendem Stempel (`make gates FORCE=1` erzwingt); roter Test macht `make gates` rot; Restore: Signaturen | Klasse `tests`, [ADR-0024](../docs/plan/adr/0024-lokale-stempel-zwei-klassen.md), [ADR-0008](../docs/plan/adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0035](../docs/plan/adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md) |
| `<make-target>` | <…> | ADR-<NNNN> |
| `<make-target>` | <…>, bootstrap-aware | Schwelle X %, M<n> → Y % |
| `<make-target>` | <…> | bootstrap via `CO-<NNN>` bis <Slice/Welle> |
| `make gates` | alle inneren Gates; Klassen `werkzeug`, `tests`, `stempel` mit Stempel (bei Beleg übersprungen, `FORCE=1` erzwingt), `immer` ohne | [ADR-0024](../docs/plan/adr/0024-lokale-stempel-zwei-klassen.md), [ADR-0030](../docs/plan/adr/0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md) |
| `make gruppe-werkzeug` | Gruppenschritt der Klasse `werkzeug` allein; schreibt den Stempel nach grünem Lauf aller Gates der Klasse; Teil von `make gates` | [ADR-0024](../docs/plan/adr/0024-lokale-stempel-zwei-klassen.md) |
| `make gruppe-tests` | Gruppenschritt der Klasse `tests` allein; schreibt den Stempel nach grünem Lauf aller Gates der Klasse; Teil von `make gates` | [ADR-0024](../docs/plan/adr/0024-lokale-stempel-zwei-klassen.md) |
| `make gruppe-stempel` | Gruppenschritt der Klasse `stempel` allein (die vier Selbsttests); schreibt den Stempel nach grünem Lauf; Teil von `make gates` | [ADR-0030](../docs/plan/adr/0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md) |
| `make gate-zuordnung` | jedes Gate in genau einer Klasse, seine Stage zur Klasse, die vier Selbsttests der Stempel in `stempel`; Selbsttest mit 33 Fällen; Klasse `immer` | [ADR-0024](../docs/plan/adr/0024-lokale-stempel-zwei-klassen.md), [ADR-0030](../docs/plan/adr/0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md) |
| `make stempel-eingaenge-pruefung` | `COPY`-Quellen und Make-Fragmente der Klassen stehen in `tools/stempel/*.eingaenge`, ohne den Selbsttest mit Mutationen; Klasse `immer` | [ADR-0024](../docs/plan/adr/0024-lokale-stempel-zwei-klassen.md) |
| `make stempel-selbsttest` | jeder Fehlerpfad von `tools/stempel.sh` in Wegwerf-Repos (flacher Klon, Hash, Note, Sperre, Amend, Rebase, Squash); Host, bash, git; Klasse `stempel` | [ADR-0030](../docs/plan/adr/0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md) |
| `make stempel-eingaenge` | Selbsttest der Prüfung der Listen mit Mutationen an Dockerfile, Listen und Fragmenten; Host, bash, git; Klasse `stempel` | [ADR-0030](../docs/plan/adr/0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md) |
| `make stempel-abbild` | jeder Eintrag der echten Listen ändert den Hash seiner Klasse, an einem Abbild des Arbeitsbaums; Klasse `stempel` | [ADR-0030](../docs/plan/adr/0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md) |
| `make stempel-gruppe` | Zusammenspiel der Gruppenschritte und der Flags von make mit den echten Listen am Abbild; Klasse `stempel` | [ADR-0030](../docs/plan/adr/0030-dritter-stempel-fuer-die-selbsttests-der-stempel.md) |
| `<make-target>` | gates + extras | — |
| `<make-target>` | volle Closure | Image-Hash `sha256:…` (Modul 14) |
| `make <gate-mit-grenze>` | <…>; Grenze und Ausgänge in der verlinkten Datei | ADR-<NNNN> |

**Werkzeuge — genannt, weil der Lauf sie braucht, aber kein Gate:**

| Target | Tut was | Bindung |
|---|---|---|
| `make deps-lock` | schreibt `gradle.lockfile`, `testserver/gradle.lockfile`, `settings-gradle.lockfile` und `gradle/verification-metadata.xml` neu (braucht Netz), prüft nichts | kein Gate |
| `make spec-abdeckung` | führt Kotlin- und C#-Tests aus, schreibt `docs/plan/spec-abdeckung.md` (je `SPEC-*` bestandene Tests, Waisen); `SPEC_ABDECKUNG_STRENG=1` bricht bei Waisen; Tags: Kopf von `tools/spec-abdeckung.sh` | kein Gate |
| `make spec-abdeckung-selbsttest` | prüft den Erzeuger der Abdeckungsmatrix an einem Mini-Bestand (Kotlin, C#): roter, nicht gelaufener, übersprungener, unbekannt getaggter Test zählt nicht; fünf Mutationen des Erzeugers machen ihn rot | kein Gate |
| `make matrix-zitate` | baut die Stage `test` frisch; jedes Zitat `Klasse > Testfall` der Kompatibilitätsmatrix muss als PASSED stehen, sonst Exit 1; bei rotem Bau die roten Testfälle (`tools/matrix-zitate-rot.sh`) | kein Gate, [TDS-QA-05](../spec/lastenheft.md#tds-qa-05--wartbarkeit) |
| `make e2e` | E2E-Durchgang aus 8 Stufen, Abbruch bei Rot; Stufen 1 bis 7: 103 s warm (Last 3 bis 8), 339 s (hohe Last), Einzelmessungen 2026-11-01; `tools/mk/e2e.mk` | kein Gate, [ADR-0022](../docs/plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), [TDS-QA-01](../spec/lastenheft.md#tds-qa-01--kompatibilität) |
| `make kompatibilitaet` | Referenzclient gegen den Serverprozess; Stufen 4 bis 7 von `make e2e` (`KATEGORIE=Szenario`, `Anmeldung`, `Luecken`, `Synchronitaet`); Matrix 7; Stufe 7 mit Cancel; `tools/mk/kompatibilitaet.mk` | kein Gate, [ADR-0019](../docs/plan/adr/0019-kompatibilitaetstests-zuschnitt.md), [ADR-0022](../docs/plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), [TDS-QA-01](../spec/lastenheft.md#tds-qa-01--kompatibilität) |
| `make eigenes-backend` | Referenzclient gegen den Serverprozess mit eigenem In-Memory-Backend (nicht die Simulation): Ergebnis gefüllt und leer, Backend-Fehler, `execute` wirft, Server bleibt verfügbar; Stufe 8 | kein Gate, [ADR-0022](../docs/plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0034](../docs/plan/adr/0034-testserver-als-gradle-modul.md), [TDS-FA-08](../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-RB-04](../spec/lastenheft.md#tds-rb-04--externes-backend) |
| `make laufzeittest` | Testserver-Container (kein Produkt-Image): Start, Rohbytes-Login aus 2. Container, Stop; warm 14 s, nach Quelländerung 86 s (Einzelmessung 2026-11-01, Last 9 bis 11); Stufe 2 von `make e2e` | kein Gate, [ADR-0022](../docs/plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), [TDS-RB-01](../spec/lastenheft.md#tds-rb-01--implementierungssprache) |
| `make testserver-up` | startet den Testserver als Container im Hintergrund auf `127.0.0.1:1433` (Test-Image `tdswire-testserver:lokal`); Anleitung: [testserver-manuell](../docs/user/testserver-manuell.md) | kein Gate, [ADR-0022](../docs/plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), [ADR-0025](../docs/plan/adr/0025-produktschnitt-bibliothek-und-testserver.md), [TDS-RB-01](../spec/lastenheft.md#tds-rb-01--implementierungssprache) |
| `make testserver-down` | stoppt und entfernt den Testserver-Container; ohne Container ein Erfolg ohne Wirkung | kein Gate, [ADR-0022](../docs/plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), [TDS-RB-01](../spec/lastenheft.md#tds-rb-01--implementierungssprache) |
| `make testserver-logs` / `make testserver-status` | Log des Handlaufs (`TESTSERVER_FOLGEN=1` verfolgt es) / Exit 0, wenn der Container läuft, sonst Fehler | kein Gate, [ADR-0022](../docs/plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md) |
| `make testserver-selbsttest` | prüft up, Loopback, Login vom Host-Netz, down, Eingabefehler, Warnung, Port belegt; 9 Mutationen rot; 59 s (Cache), 159 s erster Lauf; Last 12 bis 15, 2026-11-05 | kein Gate, [ADR-0022](../docs/plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), [TDS-QA-06](../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) |
| `make simulations-integration` | Referenzclient gegen den Testserver: Quelle über dem Heap (48 MiB), Fehler der Quelle, Cancel; Stufe 3; warm 19 s, nach Quelländerung 49 s (Einzelmessung 2026-11-01, Last 9), kalt nicht gemessen | kein Gate, [ADR-0022](../docs/plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md) |
| `make vergleichslauf-d` | Lauf D: Referenzclient gegen echten SQL Server (2022, 2025; Digest): Anmeldung, TDS 8.0, Live, Mitschnitt, Messreihe `LAUF=wire`; nur lokal, EULA: Nutzer; `docs/messungen/referenzabgleich.md` | kein Gate, [ADR-0008](../docs/plan/adr/0008-dotnet-kompatibilitaetstests.md), [TDS-QA-01](../spec/lastenheft.md#tds-qa-01--kompatibilität) |
| `make messung-treiber-handles` | Beleg am Treiber: mssql-jdbc 13.4.0 gegen den gebauten Testserver (Handle je Anweisungsobjekt, Freigabe als Batch, keine Abweisung an der Grenze); Docker, Kopf von `tools/treiber-handles.sh` | kein Gate, [ADR-0036](../docs/plan/adr/0036-handle-freigabe-und-grenze-je-sitzung.md), [ADR-0022](../docs/plan/adr/0022-e2e-laeufe-nicht-in-den-gates.md), [TDS-FA-04](../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc) |
| `make doc-manual` | liest Handbuch und Spezifikation von d-check; `MANUAL=<begriff>`, hermetisch | kein Gate |
| `make workflow-lint` | prüft Syntax und Ausdrücke von `.github/workflows/*.yml` mit actionlint (Image per Digest, über stdin); prüft weder die SHA-Pins der Actions noch, dass der Workflow `make gates` aufruft | kein Gate, [TDS-QA-06](../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) |
| `make clean-build` | baut Jar, Tests und detekt (Gradle-Stages) mit `--no-cache` in Docker, ohne .NET-Restore; Frischbeleg für Tests mit Wanduhr oder Last, die `make test` aus dem Cache zeigt (seit slice-handshake-login) | kein Gate, [TDS-QA-06](../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) |
| `make referenztest-dotnet-lock` | schreibt `referenztest-dotnet/packages.lock.json` neu (braucht Netz; das erste Lock vertraut nuget.org beim Schreiben), prüft nichts | kein Gate, [ADR-0035](../docs/plan/adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md) |
| `make referenztest-dotnet-audit` | meldet bekannte Schwachstellen der .NET-Pakete am Tag des Laufs; Ergebnis hängt am Abfragetag, nicht reproduzierbar, Exit 0 auch bei Fund | kein Gate, [ADR-0035](../docs/plan/adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md) |
| `make builder-create` | legt mit `BUILDER=<name>` einen buildx-Builder mit CPU-/RAM-Limits an (Setzung `BUILDER_CPUS=8`, `BUILDER_MEMORY=24g`); belegte Optionen und Kaltstart-Grenze: Kopf von `tools/mk/builder.mk` | kein Gate, [TDS-QA-06](../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) |
| `make builder-rm` | entfernt den Builder `BUILDER=<name>` samt seinem Cache | kein Gate, [TDS-QA-06](../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) |
| `make <messung>` | misst <…> gegen <Schwelle> | kein Gate, ADR-<NNNN> |
| `make <vorschau>` | sagt, was <schreibender Lauf> täte; Ausgänge und Sperren in der verlinkten Datei | kein Gate |

**Aktueller Lauf-Status:** lokal `make help` / `make gates`; die lokalen Stempel (Notes) kennt die CI nicht, sie läuft voll, ein Beleg-Weg für die CI ist offen ([ADR-0023](../docs/plan/adr/0023-gate-klassen-und-stempel.md) Entscheidung 7). Die CI-Datei `.github/workflows/gates.yml` führt `make gates`, `.github/workflows/e2e.yml` führt `make e2e` (kein Gate; noch ohne Lauf auf `main`); sieben Läufe auf `main` sind `success` (jüngster abgeschlossener: 41053892627 auf `cbdbd80`, ältester: 41023977695; Abfrage `gh run list --workflow gates`). Ein Badge steht nicht hier; der Stand ist die Abfrage.
**Rote Gates:** Begründung im verlinkten `CO-<NNN>` (siehe Bindung-Spalte), Modul 7.
**Nicht behauptet:** ein Target für den Vergleichslauf D (kein Referenzclient gebaut), ein Frischbeleg der Signaturprüfung durch `make referenztest-dotnet` (der Layer-Cache wiederholt den Restore nicht; Frischbeleg: Kopf von `tools/mk/referenztest-dotnet.mk`), ein Selbsttest der Signaturprüfung des .NET-Restore (`signatureValidationMode=require` mit `trustedSigners` in `referenztest-dotnet/nuget.config` greift im Restore; der rote Fall ist einmal von Hand gemessen, [Messprotokoll](../docs/messungen/dotnet-signaturpruefung-2026-11-06.md)) und ein Gate für die Schwachstellenprüfung ([ADR-0035](../docs/plan/adr/0035-dotnet-restore-signaturliste-und-audit-verzicht.md); `make referenztest-dotnet-audit` meldet nur).

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
