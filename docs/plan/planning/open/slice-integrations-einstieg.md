# Slice slice-integrations-einstieg: Öffentlicher Einstieg zum Einbetten des Servers mit eigenem Backend und eigenen Zeilenquellen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-anfrageverarbeitung

**Bezug:** [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) (Zeilenquelle des Integrators); die ADR zum öffentlichen Integrations-Einstieg ist im Bezug nachzutragen, sobald sie auf `main` liegt (§4, §6).

**Berührte Spec-Stellen:** [TDS-FA-08.a](../../../../spec/spezifikation.md#tds-fa-08a--backend-vertrag) · SPEC-002 · SPEC-035 · ARC-006 · ARC-008 · ARC-016

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912. **Datum:** 2026-10-31.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Ein Integrator kann den Server mit einem eigenen `QueryBackend` und eigenen Zeilenquellen aus seinem Programm starten und beenden, ohne dass er `internal`-Typen des Moduls berührt; der Nachweis läuft ausschließlich über die öffentliche API gegen ein In-Memory-Backend ([TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)).

**Befund, der den Slice begründet** (Stand des Repos am Tag der Anlage, nicht an einem Integrator gemessen): `QueryBackend` (Paket `port`) ist `public`; `Bootstrap`, die Verdrahtung, `RequestSource` und `HandlerSetup` im Paket `runtime` sind `internal`; `main()` startet den Server nur ohne Backend oder mit `SIMULATION_DESCRIPTION_FILE`. Ein Weg, auf dem ein Integrator ein eigenes Backend übergibt, ist nicht öffentlich; kein Slice trägt ihn. Der Befund fiel in der Übergabe des Implementers von `slice-sim-aenderung-integration` auf (Review `2026-10-31-a7e6afa-slice-sim-aenderung-integration`, mit der Archivierung von welle-simulations-backend aus `docs/reviews/` entfernt, im Archiv der Welle), nicht in einem Test.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Entscheidung über Form, Zuschnitt und Stabilitätszusage des Einstiegs — sie gehört in die ADR zum öffentlichen Integrations-Einstieg (in Arbeit, `Proposed`); der Slice setzt sie um und entscheidet sie nicht. Bis zu ihrem `Accepted` startet der Slice nicht (§4).
- Änderungen am Backend-Port (`QueryBackend`, `QueryEvent`, `QueryRequest`) — der Port-Vertrag ist [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md); eine Änderung wäre eine Folge-ADR.
- Das Simulations-Backend und seine Beschreibung — `welle-simulations-backend`; der Einstieg nimmt eine Zeilenquelle des Integrators entgegen, er baut sie nicht.
- Ein veröffentlichtes Artefakt in einem Paket-Repository und eine Versionierungs-Zusage — das Release-Artefakt ist `slice-release-artefakt-lizenz` (geschlossen), die Prozess-Doku `slice-release-doku-prozess`; dieser Slice liefert die Schnittstelle im Quelltext, keine Veröffentlichung.
- Das Trennen in ein eigenes Gradle-Modul, falls die ADR es nicht verlangt — Modulgrenzen sind der Gegenstand von `slice-qa-wartbarkeit`; ob der Nachweis nur außerhalb des Moduls läuft, entscheidet die ADR (§6).
- Backend-Betrieb (Verbindungspools, Authentifizierung gegen ein echtes Backend) — kein Träger im Repo; der Einstieg übergibt, er betreibt nicht.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Entwurf; Wortlaut und Zuschnitt der Punkte folgen der ADR zum öffentlichen Integrations-Einstieg und werden beim Übergang `open` → `next` gegen sie geprüft.

Liefer-Punkte (drei):

- [ ] **Öffentlicher Einstieg und Konfigurationstyp** ([TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend)): ein `public` Einstieg und ein `public` Konfigurationstyp, mit dem der Integrator ein eigenes `QueryBackend` und eigene Zeilenquellen ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)) übergibt; die `internal`-Typen von `runtime` bleiben `internal`. Das Verhältnis zu `main()` (gleicher Weg oder getrennt) folgt der ADR.
- [ ] **Lebenszyklus** (start/stop): Der Einstieg startet den Server und gibt dem Integrator ein Handle, mit dem er ihn mit Frist beendet; ein zweites Beenden und ein Start mit ungültiger Konfiguration enden definiert, ohne offene Ressourcen (Test). Die Einzelheiten (Form des Handles, Blockierung, Fehlerabbildung) folgen der ADR.
- [ ] **Nachweis ohne `internal`-Zugriff** ([TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend), Nachweis „Integration mit einem In-Memory-Testbackend“): ein Test startet den Server über den öffentlichen Einstieg mit einem In-Memory-Backend und eigener Zeilenquelle und beantwortet mit Rohbytes eine Anfrage; der Test liegt dort, wo er keinen `internal`-Typ des Moduls sehen kann (eigenes Source-Set, eigenes Modul oder der Referenzclient: das entscheidet die ADR), und eine Mutation, die den Einstieg `internal` macht, macht ihn rot (Beleg in der Closure-Notiz).

Nachweis-Pflichten für alle Punkte:

- [ ] Doku: KDoc der öffentlichen Typen (Zusage, Lebenszyklus, Threading, Zeilenquelle schließt mit der Anfrage) und die Festlegung in `spec/spezifikation.md` (Abschnitt zum Backend-Vertrag), soweit die ADR sie verlangt; Aussagen stehen an einem Test oder sind als Festlegung gekennzeichnet (`AGENTS.md` §3.8).
- [ ] Jeder Test trägt im KDoc den Block-Tag `@spec SPEC-<NNN>`; die Tests laufen in `make test` und sind grün.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
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
| `src/main/kotlin/io/github/pt9912/tdswire/runtime/` | update | öffentlicher Einstieg und Konfigurationstyp über `Bootstrap` (ARC-008); `internal` bleibt, was nicht Teil der Zusage ist |
| `src/main/kotlin/io/github/pt9912/tdswire/runtime/Main.kt` | update | Verhältnis zum Einstieg laut ADR; Umgebungsvariablen bleiben der Weg des Prozesses |
| `src/test/` (Ort laut ADR) | neu | Einbettungstest nur über die öffentliche API mit In-Memory-Backend und eigener Zeilenquelle, nach [TDS-RB-04](../../../../spec/lastenheft.md#tds-rb-04--externes-backend); `@spec`-Tag je Test |
| `spec/spezifikation.md` | update | Festlegung zum Einstieg im Abschnitt Backend-Vertrag |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

- Reihenfolge: Konfigurationstyp und Einstieg, dann Lebenszyklus, dann der Test von außen; die Mutation (Einstieg `internal`) vor der Closure.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Die ADR zum öffentlichen Integrations-Einstieg steht im ADR-Index auf `Accepted`, `slice-sim-aenderung-integration` liegt in `done/` (die Verdrahtung der Zeilenquelle, die der Einstieg übergibt), und ein Verantwortlicher ist im Kopf eingetragen. Kennung und Link der ADR werden im Bezug und hier nachgezogen, sobald ihre Datei auf `main` liegt.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2, etwa weil die ADR ein eigenes Modul verlangt.
- `in-progress` → `open` (blockiert — Carveout?): die ADR wird `Superseded` oder ihre Form ändert sich während der Umsetzung.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün, der Review-Report liegt vor, der Einbettungstest läuft ohne Zugriff auf `internal`-Typen und die Mutation „Einstieg `internal`“ macht ihn rot, und die Closure-Notiz trägt den Steering-Loop-Lerneintrag (geschärfte Regel, neuer Sensor oder benannte Spec-Lücke). Gemessen ist nur, was der Test gegen ein In-Memory-Backend belegt; ein echtes Backend und der Referenzclient sind nicht Gegenstand.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Stabilitätszusage: Was `public` wird, ist eine Zusage an Dritte; welche Typen dazugehören, wie lange sie halten und wie Änderungen angekündigt werden, ist nicht entschieden. Die Entscheidung ist die ADR; ohne sie liefert der Slice nichts. — **Ausgang:** offen bis zur Closure.
- Jackson-Abhängigkeit für Embedder: Die YAML-Bibliothek (`libs.jackson.dataformat.yaml`) hängt als `implementation` am Projekt; ob ein Embedder sie auf den Klassenpfad bekommt oder von ihr frei bleiben muss, ist nicht gemessen. Der Einstieg darf keinen Jackson-Typ in seiner öffentlichen Signatur tragen. — **Ausgang:** offen bis zur Closure.
- Verhältnis zu `Main.kt`: zwei Wege zum Start (Prozess und Einbettung) können auseinanderlaufen; die Konfigurationsprüfung (Konflikt Backend und Simulation, Flags, Port) soll einmal stehen. Ob `main()` den öffentlichen Einstieg ruft, entscheidet die ADR. — **Ausgang:** offen bis zur Closure.
- Nachweis von der falschen Seite der Grenze: Tests im Modul sehen `internal`; ein Test, der den Einstieg von innen ruft, bestünde auch bei fehlender Öffentlichkeit. Der Test muss dort laufen, wo `internal` nicht sichtbar ist, und die Mutation beweist es (`BEO-TDS/nachweis-von-der-falschen-seite-der-grenze`). — **Ausgang:** offen bis zur Closure.
- Lebenszyklus in einem fremden Prozess: Der Integrator ruft `stop` aus beliebigem Thread, hält Zeilenquellen offen oder wirft aus dem Backend; [ADR-0013](../../adr/0013-backend-grenzen.md) regelt die Grenzen für den Protokollkern, das Beenden aus dem Embedder ist dort nicht festgelegt. — **Ausgang:** offen bis zur Closure.
- Der Ort des Einbettungstests hängt an den Modulgrenzen von `slice-qa-wartbarkeit` (in Arbeit); ein dort beschlossenes Modul-Layout verändert den Zuschnitt dieses Slice. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Das Package `runtime` (Composition Root) ist ein Pfad-Cluster (Achse 3), trägt aber keine eigene Strukturregel (Achse 1) und keine eigene Inventur-Linie (Achse 2): eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`). Treffer:

- `BEO-TDS/oeffentlicher-einstieg-ohne-traeger` — 1×, offen (Beleg `evidence/slice-sim-aenderung-integration.md`). Treffer: der Befund, den dieser Slice trägt.
- `BEO-TDS/nachweis-von-der-falschen-seite-der-grenze` — 1×, offen. Treffer: der Test von außen (§2 Punkt 3, §6).
- `BEO-TDS/test-belegt-nur-den-fake` — verkörpert (`.harness/skills/reviewer.md`, Anker `seit welle-backend-streaming`). Treffer: der In-Memory-Test darf nicht nur sein Skript ausliefern; die Eingaben variieren.
- `BEO-TDS/aussagen-ueber-messung-hinaus` — verkörpert (`AGENTS.md` §3.8). Treffer: Aussagen zur Stabilität des Einstiegs stehen an einem Test oder sind als Festlegung gekennzeichnet.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
