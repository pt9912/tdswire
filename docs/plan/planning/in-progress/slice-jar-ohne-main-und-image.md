# Slice slice-jar-ohne-main-und-image: Bibliotheks-Jar ohne `Main-Class`, ohne Produkt-Image, Container-Lauf nur noch mit dem Testserver

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-produktschnitt

**Bezug:** [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-RB-05](../../../../spec/lastenheft.md#tds-rb-05--lizenz) (Lizenz im Jar bleibt); [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) (`Accepted`, Entscheidungen 3 und 4, Folgepflicht 2 (b)), [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) (Option M1, Begriff E2E-Lauf), [ADR-0018](../../adr/0018-laufzeit-artefakt.md) (Entscheidungen 1 bis 4 teilweise ersetzt), [ADR-0019](../../adr/0019-kompatibilitaetstests-zuschnitt.md) (Container-Modell bleibt).

**Berührte Spec-Stellen:** SPEC-052 · SPEC-042 (Build-Gate, Digest-Pin der Basis-Images) · SPEC-040 (die Aussagen stehen nach `slice-spec-nachzug-produktschnitt`; dieser Slice setzt sie im Bau um und nimmt die Zielzustand-Vermerke heraus, die er erfüllt, Nachweis-Pflicht in der DoD).

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-11-01.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** `tdswire.jar` ist ein Bibliotheks-Jar ohne `Main-Class` (Lizenz in `META-INF` und `Class-Path` auf `lib/` bleiben), die Manifest-Prüfung im Bau und ihr Selbsttest sagen „kein `Main-Class`“, das Dockerfile führt weder die Stage `runtime` noch die Distroless-Stage `build`, und `make e2e` läuft in allen sieben Stufen gegen den Testserver ([ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) Entscheidungen 3 und 4).

**Befund, der den Slice begründet** (am 2026-11-01 im Quelltext und in den Skripten gelesen, nichts ausgeführt; Stand vor `slice-testserver-herausloesen`): `build.gradle.kts` setzt im `jar`-Task `Main-Class` auf `…runtime.MainKt` und einen `Class-Path` aus `lib/`; `tools/manifest-pruefung.sh` vergleicht `Main-Class` mit der Startklasse und verlangt einen nicht leeren `Class-Path`; `tools/manifest-selbsttest.sh` prüft die Fehlerfälle an synthetischen Jars; das Dockerfile führt die Stages `runtime` (Distroless Java 21 `nonroot`, per Digest gepinnt) und `build` (Distroless `static`, per Digest gepinnt, **letzte Stage und damit Vorgabe von `docker build .`**); `make build` baut diese Vorgabe (`tools/mk/build.mk`), und `docker build -t tdswire:latest` trägt das Ergebnis. `BEO-TDS/zwei-artefakt-stages-nebeneinander` hält fest, dass `runtime` und `build` zwei Definitionen desselben Artefakts sind.

**Voraussetzung:** `slice-testserver-herausloesen` liegt in `done/`; dort ist die `Main-Class` der Bibliothek bereits ohne Klasse (das Jar nennt `MainKt`, die Zeichenkette prüft die Manifest-Prüfung noch) und der Server-Container von `make laufzeittest` kommt aus der Stage des Testservers.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Das Herauslösen des Testservers, die Umstellung der Test-Einstiege und die Logprüfungen der Skripte — `slice-testserver-herausloesen`; dieser Slice setzt voraus, dass sie stehen.
- Die Spezifikation — sie steht nach `slice-spec-nachzug-produktschnitt`; dieser Slice nimmt nur die Zielzustand-Vermerke heraus, die er erfüllt (DoD, Nachweis-Pflichten), und ändert sie sonst nur bei einem Befund.
- Lizenzhinweise der Bibliotheken in `lib/` — offener Punkt von [ADR-0018](../../adr/0018-laufzeit-artefakt.md), nach [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) Sache des Release-Prozesses (`slice-release-doku-prozess`, Register `BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage`); der Slice entscheidet ihn nicht.
- Ein Alterssensor für die Digests der entfallenden Basis-Images — er entfällt mit dem Image ([ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md): akzeptiertes Negativ); ein Ersatz wird nicht gebaut.
- Der Wortlaut der Nutzer-Doku zum Start im Container: sie ist Inhalt von `slice-release-doku-prozess`; dieser Slice schreibt nur den Plan von [welle-referenzabgleich-release](../welle-referenzabgleich-release.md) um (Liefer-Punkt 3), nicht die Doku.
- Ein Produkt-`main`, ein Betriebs-Image oder die Veröffentlichung in einem Artefakt-Repository — Re-Evaluierungs-Trigger der ADR.

**Setzungen des Planners** (gelesen und nicht gemessen):

- `make build` baut die Stage `build-jar` (`--target build-jar`) und lädt kein Image mehr; die Variablen `IMAGE` und `TAG` in `tools/mk/build.mk` entfallen oder bleiben nur, soweit ein anderes Target sie braucht. Der `Class-Path` auf `lib/` bleibt als Komfort für Embedder ([ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) Entscheidung 3).
- Der Name des Targets `make laufzeittest` bleibt (Stufe 2 von `make e2e`, [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) und `tools/mk/e2e.mk` nennen ihn); der Gegenstand heißt in Kopf, Hilfetext und Spezifikation Testserver-Container. Ob der Name zu ändern ist, entscheidet der Nutzer, nicht dieser Slice.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Wortlaut und Zuschnitt folgen [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) Folgepflicht 2 (b); drei Liefer-Punkte.

Liefer-Punkte (drei):

- [ ] **Jar ohne `Main-Class` und seine Prüfung** ([TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-RB-05](../../../../spec/lastenheft.md#tds-rb-05--lizenz), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)): Das Manifest von `tdswire.jar` führt keinen `Main-Class`-Eintrag; die Lizenz liegt unverändert in `META-INF` (die Prüfung in `build-jar` bleibt), der `Class-Path` besteht aus `lib/<Name>`-Einträgen mit vorhandener Datei, der Jar-Name ist `tdswire.jar`. `tools/manifest-pruefung.sh` meldet ein vorhandenes `Main-Class` als Befund; `tools/manifest-selbsttest.sh` (in `make skript-selbsttest`) prüft: Jar ohne `Main-Class` besteht, Jar mit `Main-Class` ist rot, die bisherigen Fehlerfälle von `Class-Path` bleiben rot, und der Aufruf der Prüfung in der Stage `build-jar` steht genau einmal. **Belege:** (1) die Mutation „`Main-Class` im `jar`-Task wieder gesetzt“ macht `make build` rot; (2) `make skript-selbsttest` ist grün; (3) `build/libs/lib` in der Stage `build-jar` führt kein Jackson (Teil von Closure-Kriterium (2) der Welle).
- [ ] **Stages `runtime` und `build` entfernt, Container-Lauf nur mit dem Testserver** ([TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)): Das Dockerfile führt weder `runtime` noch die Distroless-Stage `build`; die Digest-Pins der beiden Basis-Images sind weg (ein Pin bleibt nur für Basis-Images, die noch gebaut werden, `SPEC-042`); `make build` baut `build-jar` und liefert Compile, Jar, Lizenz- und Manifest-Prüfung. `tools/laufzeittest.sh` und `tools/mk/laufzeittest.mk` enthalten keine Prüfung und keinen Kommentar mehr, die ein Produkt-Image voraussetzen (Image-Inhalt, Nutzer 65532, Lizenz in `/app`, `JAVA_TOOL_OPTIONS`, Stage `runtime`); der Lauf prüft den Testserver-Container: Start, Login mit `TEST_UNENCRYPTED` aus dem zweiten Container, `docker stop` mit Exit-Code 143. **Belege:** `grep -rn "MainKt\|AS runtime\|AS build$" Dockerfile tools build.gradle.kts` findet nichts außer in Aussagen, die den Wegfall nennen; `make build` und `make gates` sind grün. **E2E-Stufen:** Stufe 1 (`make smoke`) und Stufe 2 (`make laufzeittest`) sind grün gegen den Testserver und in `tools/e2e.sh` so deklariert ([slice-e2e-geruest](../in-progress/slice-e2e-geruest.md), Kennungen unverändert: [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)); `make e2e-abdeckung` zeigt sie danach in `docs/plan/e2e-abdeckung.md`; die Läufe gehen über die Prozessgrenze (Container, Rohbytes-Client im zweiten Container), nicht über `internal`-Zugriff.
- [ ] **Kopplung und Index** ([TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)): `make kopplung-zahlen` ist grün (acht Mutationen; seine Orte in Gradle, Kotlin, .NET, `SPEC-040` und `harness/README.md` stimmen nach dem Umbau); `harness/README.md`, die Wurzel-`README.md` und die Kopfkommentare der Make-Fragmente (`tools/mk/build.mk`, `laufzeittest.mk`, `smoke.mk`) sagen, was nach dem Umbau gilt; `make e2e` ist in allen sieben Stufen grün (Messung in der Closure-Notiz), und die Suche nach der Kernaussage (`Startklasse`, `MainKt`, `Laufzeit-Image`, `Distroless`, `runtime`-Stage) über `harness/`, `README.md`, `tools/`, `Dockerfile` und `docs/plan/` (ohne `adr/`, `planning/done/` und `reviews/`) findet keine zweite Fundstelle (`BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`); die Trefferliste steht in der Closure-Notiz. **Umschreiben der berührten Welle (Nutzerentscheidung 2026-11-01: erst in diesem Slice):** der Vermerk „berührt durch [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)“ in [welle-referenzabgleich-release](../welle-referenzabgleich-release.md) ist aufgelöst, das heißt Welle-Ziel (§1), das Closure-Kriterium „Läufe gegen das Laufzeit-Image“ (§3) und der Satz zur Nutzer-Doku „Start im Container“ (§3, §4) sagen, was nach der ADR gilt (Läufe gegen den Testserver-Container beziehungsweise gegen die Bibliothek; kein Produkt-Image), der Vermerk ist entfernt, und der Wortlaut ist vom Nutzer bestätigt, weil er ein Closure-Kriterium ändert (bestätigt am 2026-11-01: Vorschlag des Koordinators vom Nutzer angenommen); die Beschreibung von `slice-release-artefakt-lizenz` in §4 jener Welle bleibt als Bericht eines abgeschlossenen Slice stehen und wird um den Hinweis auf die ADR ergänzt.

Nachweis-Pflichten für alle Punkte:

- [ ] `make gates` grün.
- [ ] **Zielzustand-Vermerke der Spezifikation entfernt** (Nachweis, kein Liefer-Punkt; Zusage aus `slice-spec-nachzug-produktschnitt` §3): Die Zielzustand-Vermerke in `spec/spezifikation.md`, die dieser Slice erfüllt, sind an den Stellen entfernt (Stand per `grep -n Zielzustand spec/spezifikation.md` im Nachweis geprüft, mit Treffern vor und nach dem Umbau); die Stellen, die der jeweils andere Slice trägt, bleiben stehen. Ein stehengebliebener Vermerk ist ein Befund (`BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`), ein entfernter Satz ohne tragenden Code ebenso. Die Zeilennummern gelten für den Stand nach `slice-spec-nachzug-produktschnitt` und verschieben sich mit jeder Änderung der Datei; maßgeblich ist der Text der Stelle.

  | Zeile | Stelle |
  |---|---|
  | 1213 | `SPEC-059` Zeile Abhängigkeiten: `lib/` führt noch die YAML-Bibliothek (fällt mit dem Entfernen von Jackson durch `slice-testserver-herausloesen`; dieser Slice belegt den Inhalt von `lib/` in Beleg (3) des ersten Liefer-Punkts) |
  | 1228 | `SPEC-052`: Startklasse im Manifest, Laufzeit-Image, Konfiguration im Container, `make laufzeittest` gegen das Image |

  Die Stellen 607, 685, 823, 830, 1085, 1150, 1151, 1155, 1180, 1207, 1209 und 1225 trägt `slice-testserver-herausloesen`.

- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update: `harness/README.md` — die Zeilen `make build` (Jar ohne Startklasse, Lizenz und Manifest im Bau; keine Basis-Images mehr für ein Produkt-Image), `make skript-selbsttest` (Fall „kein `Main-Class`“), `make kopplung-zahlen`, `make laufzeittest` und `make e2e` stimmen mit dem Bestand; die Wurzel-`README.md` nennt keinen Container-Start des Produkts.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo mit Wellen von der Welle-Closure geprüft.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `build.gradle.kts` | update | `jar`-Task ohne `Main-Class`; Lizenz und `Class-Path` bleiben |
| `tools/manifest-pruefung.sh` | update | Prüfung „kein `Main-Class`“ statt Vergleich mit der Startklasse; Kopfkommentar |
| `tools/manifest-selbsttest.sh` | update | Fälle: mit `Main-Class` rot, ohne bestanden; Fehlerfälle von `Class-Path` unverändert |
| `Dockerfile` | update | Stages `runtime` und `build` (Distroless) mit ihren Digest-Pins entfernt; Kommentar „letzte Stage = Vorgabe“ nachgezogen |
| `tools/mk/build.mk` | update | `make build` mit `--target build-jar`, ohne `-t $(IMAGE):$(TAG)`; Hilfetext |
| `tools/laufzeittest.sh`, `tools/mk/laufzeittest.mk` | update | Reste des Produkt-Images entfernt; Kopf und Hilfetext sagen Testserver-Container |
| `tools/e2e.sh`, `tools/mk/e2e.mk` | update | Texte der Stufen 1 und 2 |
| `tools/integrationszahlen-pruefung.sh` und `harness/README.md` (Zeile `simulations-integration`) | prüfen | die Orte von `kopplung-zahlen` stimmen nach dem Umbau |
| `harness/README.md`, `README.md` | update | Zeilen `build`, `skript-selbsttest`, `kopplung-zahlen`, `laufzeittest`, `e2e` |
| `docs/plan/e2e-abdeckung.md` | update (erzeugt) | `make e2e-abdeckung` |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf (etwa CI-Dateien, die `docker build .` oder das Image `tdswire:latest` nennen; `.github/workflows/`).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`), alle Bedingungen beobachtbar:

1. `slice-testserver-herausloesen` liegt in `done/` (Voraussetzung (b) setzt (a) voraus, [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md) Folgepflicht 2).
2. Der Start-Trigger von [welle-produktschnitt](../welle-produktschnitt.md) ist eingetreten.
3. Ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte, etwa weil CI-Dateien, ein Release-Workflow oder Skripte außerhalb der Liste das Image `tdswire:latest` verlangen; Zerlegung: Jar und Prüfung | Stages und Container-Lauf | Kopplung und Index.
- `in-progress` → `open` (blockiert — Carveout?): ein Embedder-Szenario oder ein Lauf braucht das Image oder die Startklasse (Re-Evaluierungs-Trigger der ADR: dann Produkt-`main` mit eigener Entscheidung); oder `slice-testserver-herausloesen` fällt in `open/` zurück; oder ein Gate müsste gelockert werden (`AGENTS.md` §3.6).

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün, `make e2e` ist in allen sieben Stufen grün (Messung in der Closure-Notiz, nicht aus dem Gedächtnis), der Review-Report liegt vor, und die Closure-Notiz trägt den Steering-Loop-Lerneintrag. Gemessen sind die Manifest-Prüfung mit der Rückfall-Mutation, der Selbsttest und die Läufe der Stufen 1 und 2 über die Prozessgrenze; nicht gemessen ist ein Betriebs-Image (es gibt keines) und die Verträglichkeit der Lizenzen in `lib/` ([ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md), offener Punkt).

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Eine CI-Datei, ein Skript oder eine Doku außerhalb der Liste ruft `docker build .` ohne `--target` oder das Image `tdswire:latest` und bricht, wenn die letzte Stage wechselt. — **Ausgang:** offen bis zur Closure.
- Mit dem Digest-Pin entfällt auch jeder Sensor, der sein Alter hätte prüfen können; ein Pin für ein Image, das nicht mehr gebaut wird, bliebe als totes Muster stehen. — **Ausgang:** offen bis zur Closure.
- `laufzeittest` heißt weiter nach einem Container-Artefakt, das es nicht mehr gibt; der Name trägt in Hilfetext, `tools/mk/e2e.mk` und `harness/README.md` etwas Falsches, wenn der Gegenstand nicht mitgenannt wird. — **Ausgang:** offen bis zur Closure.
- Der Wortlaut von [welle-referenzabgleich-release](../welle-referenzabgleich-release.md) (Läufe gegen das Laufzeit-Image, Nutzer-Doku zum Start im Container) und `slice-release-artefakt-lizenz` in `done/` beschreiben ein Image, das dieser Slice entfernt; jene Welle trägt bis dahin den Vermerk „berührt durch [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)“. Das Umschreiben ist Liefer-Punkt 3; die Nutzer-Doku selbst gehört `slice-release-doku-prozess`, und ein Closure-Kriterium der Welle zu ändern braucht die Bestätigung des Nutzers. — **Ausgang:** offen bis zur Closure (erwartet: eingetreten und aufgelöst im Slice; Register `BEO-TDS/zwei-artefakt-stages-nebeneinander`).
- Der `Class-Path` auf `lib/` bleibt als Komfort für Embedder und führt Bibliotheken ohne Lizenzprüfung ({ADR25}: offen). — **Ausgang:** offen bis zur Closure (erwartet: weiter offen → `BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage`).
- Der Kommentar der Stage `runtime` im `Dockerfile` nennt „(ConfigKeys)“ als Quelle der Umgebung des Containers; `ConfigKeys` ist `internal` und der Testserver liest die Umgebung (`EnvKeys`). Beim Umschreiben der Stage nachziehen (Befund des Reviews zu `slice-explicit-api-umbau`). — **Ausgang:** offen bis zur Closure.
- Die Manifest-Prüfung wird gelockert: „kein `Main-Class`“ ist schwächer als der Vergleich mit einer Zeichenkette, wenn `Class-Path` mit entfällt; die Schwelle bleibt (`AGENTS.md` §3.6), der Selbsttest hält beide Fälle. — **Ausgang:** offen bis zur Closure.

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

Wird bei der Closure gefüllt.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration kennt nur `*` (Greenfield, Kürzel `TDS`). Der Slice berührt `build.gradle.kts`, `Dockerfile`, `tools/` und die Indizes: ein Pfad-Cluster (Achse 3) ohne eigene Strukturregel oder Inventur-Linie — eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`). Treffer: `BEO-TDS/zwei-artefakt-stages-nebeneinander` (1×, offen; der Slice entfernt beide Stages und kann die Beobachtung schließen), `BEO-TDS/jackson-abhaengigkeit-fuer-embedder-nicht-gemessen` (1×, offen; Teilbeleg `lib/` ohne Jackson), `BEO-TDS/zahl-an-mehreren-orten-ohne-sensor` (1×, offen; `kopplung-zahlen`), `BEO-TDS/lizenzhinweise-der-bibliotheken-rechtsfrage` (1×, offen; Träger `slice-release-doku-prozess`, Grenze), `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (3×, offen; Suche in §2 Liefer-Punkt 3), `BEO-TDS/zusage-ohne-sensor-in-der-ci-kette` (2×, offen; die Manifest-Prüfung bleibt im Gate, der Selbsttest hält sie), `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert, `AGENTS.md` §3.8). Nachgesehen am 2026-11-01 (Umschreiben der Welle `welle-referenzabgleich-release` aufgenommen): `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` (10×, offen; die Umschreibung eines Closure-Kriteriums gilt erst nach Bestätigung des Nutzers, Liefer-Punkt 3), `BEO-TDS/dod-haengt-an-offenem-slice-ohne-rueckfuehrung` (3×, offen; Kante zu `slice-release-doku-prozess` ist keine DoD-Abhängigkeit). Sonst keine Treffer.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
