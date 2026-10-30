# Slice slice-qa-reproduzierbarkeit: Reproduzierbarkeit: Clean-Build und Tests in CI

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0010](../../adr/0010-detekt-pre-release.md)

**Berührte Spec-Stellen:** SPEC-026 · SPEC-042 · SPEC-045 · SPEC-050

**Verantwortlich:** pt9912

**Autor:** pt9912. **Datum:** 2026-10-29.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Ein Clean-Build und die Integrationstests laufen in CI ohne lokale JVM auf dem Host.

**Zusätzlicher Umfang aus [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)** (dieser Slice ist der Träger der dort offenen Punkte):

- NuGet-Audit: übernommen von [slice-qa-dotnet-referenztest-geruest](../done/slice-qa-dotnet-referenztest-geruest.md) (Entscheidung F1: `NuGetAudit=false` mit Carveout [CO-001](../../carveouts/CO-001-nuget-audit-aus.md)); die Beendigung des Carveouts führt [slice-qa-dotnet-lieferkette-haerten](../open/slice-qa-dotnet-lieferkette-haerten.md). Bewertet gegen [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit).
- `trustedSigners`: übernommen von [slice-qa-dotnet-referenztest-geruest](../done/slice-qa-dotnet-referenztest-geruest.md) (Entscheidung F2: jetzt nur Lock und `contentHash`, Carveout [CO-002](../../carveouts/CO-002-nuget-signaturpruefung-fehlt.md)); die Signaturliste führt [slice-qa-dotnet-lieferkette-haerten](../open/slice-qa-dotnet-lieferkette-haerten.md).
- Der Vergleichslauf D (Referenzabgleich nach [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig)) mit Ausnahmeliste, Messung, EULA-Prüfung, Anmelde-Szenario, Antwortseite des Codecs und SQL-Server-Tag für TDS 8.0 liegt bei [slice-qa-vergleichslauf-d](../open/slice-qa-vergleichslauf-d.md). Das ist eine Setzung der Closure dieses Slice: D braucht die Szenarien des Referenzclients aus [slice-qa-kompatibilitaet](../open/slice-qa-kompatibilitaet.md) und war dem Slice zugewiesen, ohne dass §4 diese Abhängigkeit nannte (Verifikation H-1).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Wahl des Referenzclients ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)) — sie bestimmt, was die Integrationstests sind; der Slice bindet sie in die CI ein, entscheidet sie nicht.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [x] CI-Workflow führt `make gates` in Docker aus und ist grün.
- [x] Clean-Build ohne Host-Toolchain nachgewiesen (Host: Docker und `make`): `make clean-build` für die Gradle-Stages, der gesperrte Restore des Referenztest-Gerüsts ([slice-qa-dotnet-referenztest-geruest](../done/slice-qa-dotnet-referenztest-geruest.md)) auf dem frischen Runner durch `make gates` im CI-Lauf; NuGet-Audit und `trustedSigners` tragen Carveouts mit Trigger, ihre Auflösung liegt bei [slice-qa-dotnet-lieferkette-haerten](../open/slice-qa-dotnet-lieferkette-haerten.md).
- [x] Vergleichslauf D als eigenes Make-Target mit Ausnahmeliste, Messungen, Anmelde-Szenario und Codec-Antwortseite — **verschoben, nicht geliefert**, nach [slice-qa-vergleichslauf-d](../open/slice-qa-vergleichslauf-d.md) (Setzung der Closure, §7).
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| CI-Workflow | neu | Clean-Build und Gates nach [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) |
| `harness/README.md` | update | Lauf-Status mit Run-IDs (ein Badge ist nicht geliefert); Zeilen von `workflow-lint` und `clean-build` |
| `nuget.config`, Restore-Konfiguration des Referenzclient-Tests | entfällt hier | liegt im Gerüst ([slice-qa-dotnet-referenztest-geruest](../done/slice-qa-dotnet-referenztest-geruest.md)); NuGet-Audit und `trustedSigners` führt [slice-qa-dotnet-lieferkette-haerten](../open/slice-qa-dotnet-lieferkette-haerten.md) ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)) |
| `tools/mk/ci.mk` | neu | Make-Targets `workflow-lint` (actionlint, Docker) und `clean-build` (`--no-cache`), beide kein Gate ([TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)) |
| Make-Target für den Vergleichslauf D | entfällt hier | verschoben nach [slice-qa-vergleichslauf-d](../open/slice-qa-vergleichslauf-d.md) ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)) |
| `Dockerfile` (Stage `deps`), `build.gradle.kts` (Task `abhaengigkeitenLaden`) | update | `deps` löst die Konfigurationen von compile, test, detekt und assemble auf und lädt sie (Sperre und Verifikation aktiv); eine Quelltextänderung löst keinen Download aus ([TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)) |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `make gates` ist auf `main` grün.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): CI-Runner ohne Docker.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün und die Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Die Integrationstests ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)) sind nicht gebaut, das Referenztest-Gerüst liegt; die CI deckt zunächst nur die vorhandenen Gates. — **Ausgang:** entfallen: Der Workflow führt `make gates` ohne Auswahl; was [slice-qa-kompatibilitaet](../open/slice-qa-kompatibilitaet.md) in `make gates` hängt, läuft dort. Das Gerüst-Gate lief im Log von Lauf 41048175295 (1 von 1 bestanden, Restore frisch).
- Das Versprechen „eine Quelltextänderung löst keinen Download aus“ (Stage `deps`) hat keinen Sensor; `abhaengigkeitenLaden` überspringt unauflösbare Konfigurationen (Review F-2). — **Ausgang:** weiter offen: → `BEO-TDS/zusage-ohne-sensor-in-der-ci-kette`.
- Die SHA-Pins der Actions und der Aufruf `make gates` im Workflow hat kein Sensor (Review F-8, Verifikation M3 und M4: `make workflow-lint` bleibt grün). — **Ausgang:** weiter offen: → `BEO-TDS/zusage-ohne-sensor-in-der-ci-kette`.
- Vergleichslauf D hing an den Szenarien eines Slice in `open/` und hatte keine Rückführung dafür (Verifikation H-1). — **Ausgang:** eingetreten: → `slice-qa-vergleichslauf-d`.
- Laufzeit, Netz- und Platzbedarf von `make clean-build` auf einem frischen Runner sind nicht gemessen (Review F-11). — **Ausgang:** entfallen: Das Target trägt keine Zeit- oder Platzzusage; auf dem Runner ist `make gates` der Beleg (Lauf 41048175295: 4 min 56 s im Schritt), die lokale Zahl steht in §7 als Messung eines Hosts.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren** statt neu
formulieren — sonst zählt das Register zwei Namen getrennt) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln (das
Feld `liegt in` steht **nur**, wenn mit diesem Slice wirklich etwas verkörpert
wurde; Feld und Zielort auf **einer** Zeile, Sektionsangabe innerhalb der
Backticks). Ging der Gegenstand an einen anderen Slice oder entfiel er, trägt
diese Sektion die Zeile `Gegenstand:` mit Kennung oder Grund und jedes Risiko
aus §6 seinen Ausgang; die Liefer-Punkte der DoD bleiben leer
(`modul-05-planning-harness.md` §Ein Slice, dessen Gegenstand ein anderer
übernimmt).

- **Belege:** Review [2026-10-30-cff52a9-slice-qa-reproduzierbarkeit](../../../reviews/2026-10-30-cff52a9-slice-qa-reproduzierbarkeit.md) (0 HIGH, 3 MEDIUM, 4 LOW, 5 INFO), Verifikation [2026-10-30-cff52a9-verifikation-slice-qa-reproduzierbarkeit](../../../reviews/2026-10-30-cff52a9-verifikation-slice-qa-reproduzierbarkeit.md) (DoD 1 und 2 bestätigt, DoD 3 offen, H-1). Bezug: [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0010](../../adr/0010-detekt-pre-release.md).
- **Was hat funktioniert:** Der Workflow `.github/workflows/gates.yml` führt `make gates` in Docker auf `ubuntu-24.04`; die Stage `deps` (`abhaengigkeitenLaden`) hält den Download von der Quelltextänderung getrennt. Review und Verifikation färbten den Wächter mit Mutationen rot, wo er greift (siehe Tabelle).
- **Was ging anders als geplant:** Vergleichslauf D (DoD-Punkt 3) ist nicht geliefert, sondern per Setzung der Closure in [slice-qa-vergleichslauf-d](../open/slice-qa-vergleichslauf-d.md) geschnitten (Option a der Verifikation H-1; Option b, Rückführung nach `open/`, hätte den fertigen CI-Teil liegen lassen). Verweise in `slice-qa-kompatibilitaet`, `slice-referenzabgleich-live`, `slice-referenzabgleich-mitschnitt`, `welle-referenzabgleich-release`, `welle-qualitaet-grundlage` und `slice-qa-dotnet-lieferkette-haerten` zeigen dorthin; die Registerstände von `BEO-TDS/messung-am-echten-sql-server-fehlt` und `BEO-TDS/vergleichslauf-d-ungeklaert` nennen den neuen Träger. Weitere Dateien, die `slice-qa-reproduzierbarkeit` als Träger von D nennen (offene Slices und Wellen-Pläne der Anfrage-, Backend-, Handshake- und Fehler-Wellen, `done/`-Dokumente), sind nicht umgeschrieben: `done/`-Dokumente sind Zeitdokumente, die übrigen löst der Leser über §7 dieser Datei in einem Hop auf. Der Widerspruch zum Tag für TDS 8.0 (Review F-12) steht im neuen Slice als offene Frage.
- **Zuordnung, Stand der Closure:**

  | Aussage | Zuordnung | Wert und Grenze |
  |---|---|---|
  | CI-Läufe von `make gates` auf `main` | gemessen (`gh run list --workflow gates`) | sieben Läufe `success`, zuletzt 41053892627 auf `cbdbd80`; auf `cff52a9` (Stand des Reviews) Lauf 41053210377 grün; Laufzeit 3 min 46 s bis 5 min 15 s je Lauf (Verifikation, fünf Läufe); ein Lauf je Commit, keine Aussage über Stabilität |
  | Workflow-Datei auf dem geprüften Stand | gemessen | byte-gleich auf `bda6cd6` und `cff52a9` (Verifikation) |
  | `deps` nach Quelltextänderung | gemessen (Verifikation 3.3) | `compile`, `detekt`, `build-jar`: `deps` CACHED, kein Download; `test` nicht belastbar (Layer lag im Cache); Eingangsdatei `libs.versions.toml` lässt `deps` neu laufen |
  | Mutationen am Workflow | gemessen | `runs-on` → `runs`, `permissions` → `permission`, leere Liste: rot; nicht existente Version und geänderte Prüfsumme in `deps`: rot |
  | Mutationen, die grün bleiben | gemessen (Lücke) | Action-Pin gegen `@v6` und `run: make gates` gegen `run: make docs-check`: `make workflow-lint` Exit 0; Register `BEO-TDS/zusage-ohne-sensor-in-der-ci-kette` |
  | `make clean-build` | gemessen, ein Host | Verifikation 469,9 s unter Last, Implementer 356 s; die Zahl ist ein Lauf und steht in keiner Zusage |
  | Clean-Build einschließlich .NET-Restore | gemessen (Runner) | Restore frisch im Log von Lauf 41048175295; `make clean-build` selbst deckt ihn nicht |
  | Workflow gegen actionlint 1.7.7 (Digest) | Selbstkonsistenz | prüft die Datei gegen die Regeln von actionlint, nicht das Verhalten auf dem Runner |
  | Runner `ubuntu-24.04` | Festlegung | Label, das Image dahinter rollt fort (Image-Version 20261025.353.1 im Lauf 41048175295); Docker- und make-Version nicht festgelegt |
  | Keine Sensoren für SHA-Pin, `make gates`-Aufruf und `abhaengigkeitenLaden`-Konfigurationen | Festlegung | Setzung der Closure: nicht bauen, als Beobachtung führen; keine Gate-Lockerung (`AGENTS.md` §3.6) |
  | Frischer Runner für `make clean-build`; Netz- und Platzbedarf; `detekt-selbsttest`, `spec-abdeckung`, `lock` ohne Netz | nicht gemessen | Lücke, kein Träger außer dem Register |
  | Vergleichslauf D und alles darin | nicht gemessen | Träger [slice-qa-vergleichslauf-d](../open/slice-qa-vergleichslauf-d.md) |

- **Steering-Loop-Eintrag:** Kein Eintrag verkörpert, daher kein `liegt in`. Festgestellt: (1) Ein Liefer-Punkt, der Eingänge eines Slice in `open/` braucht, ohne dass §4 es nennt, endet in einer Schleife und fällt erst in der Verifikation auf; der Planer prüft den Schnitt auf Vorwärts-Abhängigkeiten ([BEO-TDS/dod-haengt-an-offenem-slice-ohne-rueckfuehrung](../observations/BEO-TDS/dod-haengt-an-offenem-slice-ohne-rueckfuehrung/observation.md), 1×, offen). (2) Zusagen der CI-Kette ohne Sensor, der ihren Bruch meldet ([BEO-TDS/zusage-ohne-sensor-in-der-ci-kette](../observations/BEO-TDS/zusage-ohne-sensor-in-der-ci-kette/observation.md), 1×, offen). (3) Aussagen breiter als der Beleg wurden im Review wieder gefunden ([BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md), Stand bleibt `verkörpert`, `AGENTS.md` §3.8). Unter der 3×-Schwelle wird nichts in Command, Gate oder Skill verkörpert.
- **Beobachtungs-Register (`../observations/`):** neu [BEO-TDS/zusage-ohne-sensor-in-der-ci-kette](../observations/BEO-TDS/zusage-ohne-sensor-in-der-ci-kette/observation.md) und [BEO-TDS/dod-haengt-an-offenem-slice-ohne-rueckfuehrung](../observations/BEO-TDS/dod-haengt-an-offenem-slice-ohne-rueckfuehrung/observation.md), beide `offen`, je `evidence/slice-qa-reproduzierbarkeit.md`; `evidence/slice-qa-reproduzierbarkeit.md` ergänzt in `BEO-TDS/aussagen-ueber-messung-hinaus`. Die Zähler folgen aus den Dateien. `BEO-TDS/messung-am-echten-sql-server-fehlt` und `BEO-TDS/vergleichslauf-d-ungeklaert`: Träger gewechselt, keine neue Evidenz.
- **Folge-Slices:** [slice-qa-vergleichslauf-d](../open/slice-qa-vergleichslauf-d.md) (Vergleichslauf D) — ist eine Datei in `open/`; [slice-qa-dotnet-lieferkette-haerten](../open/slice-qa-dotnet-lieferkette-haerten.md) trägt NuGet-Audit und `trustedSigners` ([CO-001](../../carveouts/CO-001-nuget-audit-aus.md), [CO-002](../../carveouts/CO-002-nuget-signaturpruefung-fehlt.md)).
- **Risiken aus §6:** je ein Ausgang, siehe §6.
- **Drei Paarungen:** Das Repo führt Wellen; die Paarungen prüft die Closure von [welle-qualitaet-grundlage](../welle-qualitaet-grundlage.md). Anker ↔ Folge-Slice: kein Anker gesetzt; der Folge-Slice steht in der Slice-Tabelle der Welle. Register ↔ Closure-Notiz: getragen (drei Pfade oben).

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung — dort die **zwei vorgelagerten
Schritte** (sie stehen in jedem Slice-Plan, unabhängig von Modus und
Slice-Typ) und die **vier Pflichtkriterien** (Konventionen-Dichte ·
Phase-Reife · Evidenz-/Diskrepanz-Risiko · Reconciliation-Aufwand), vier und
nicht mehr.

**Der Abschnitt selbst entfällt nie.** Die zwei vorgelagerten Prüfungen laufen
in **jedem** Slice-Plan — sie hängen weder am Modus noch am Slice-Typ. Bedingt
ist allein der Modus-Begründungsblock am Ende; deshalb nennt der Titel beide
Hälften.

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` steht noch als Vorlage; der Slice berührt das ganze Repo (`*`), eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen. Treffer: `BEO-TDS/messung-am-echten-sql-server-fehlt` (*geplant*) und `BEO-TDS/vergleichslauf-d-ungeklaert` (*offen*), beide mit D als Gegenstand und seit der Closure bei `slice-qa-vergleichslauf-d`; `BEO-TDS/aussagen-ueber-messung-hinaus` (*verkörpert*, `AGENTS.md` §3.8) für die Aussagen des Slice.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
