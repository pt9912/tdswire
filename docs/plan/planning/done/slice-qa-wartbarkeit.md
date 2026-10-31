# Slice slice-qa-wartbarkeit: Wartbarkeit: Modulgrenzen und Kompatibilitätsmatrix

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0010](../../adr/0010-detekt-pre-release.md)

**Berührte Spec-Stellen:** SPEC-041

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

**Ziel:** Protokollverarbeitung und Backendintegration sind getrennt testbar, die Modulgrenzen sind maschinell geprüft, und die unterstützten Nachrichten und Datentypen stehen in einer Kompatibilitätsmatrix.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Gerüst-Gates (detekt, Build) — sie bestehen bereits und werden hier nur um Tests erweitert, nicht neu gebaut.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- Das Unit-Test-Gate (`SPEC-024`, `make test`) liefert `slice-codec-tds-paket` und ist hier kein Liefer-Punkt mehr; dieser Slice baut darauf auf und erweitert es nur um Tests, nicht um ein zweites Gate.
- [x] Kompatibilitätsmatrix der Nachrichten und Datentypen dokumentiert, als Abdeckungs-Dokument unter docs/plan/ (`AGENTS.md` §5). Stand `68598df`, 212 verschiedene Zitate, alle im frischen Bericht `PASSED`; die Herkunftsstufen der Bestandszeilen aus `bda6cd6` sind nicht neu gemessen, die 775 Testfälle der Stand-Zeile abgeleitet, das Rot-Sehen der Importregeln einmalig am Stand `bda6cd6`, keine Zeile am Referenzclient gemessen.
- [x] `make gates` grün (vor jedem Commit der Closure und nach dem Move).
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor (Einschränkung: der Review der Neuaufnahme gilt für `754743d`, die Fix-Runde `75533b0` hat keinen eigenen Review, nur die zweite Verifikation; der erste Review gilt dem alten Stand `71122af`)
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist (`harness/README.md`, `SPEC-052`).
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
| Kompatibilitätsmatrix, Datei unter docs/plan/ | neu | nach [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit) |
| `docs/plan/kompatibilitaetsmatrix.md` | neu | die Matrix als Abdeckungs-Dokument (`AGENTS.md` §5 Regel 6), von Hand gepflegt; jede „unterstützt“-Zeile nennt einen Test, der im frischen Lauf von `make test` bestanden steht; zweiter Teil: Ist-Abgleich der Importregeln in `config/detekt/detekt.yml` gegen `spec/architecture.md` §2 |
| `tools/matrix-zitate.sh`, `tools/mk/matrix-zitate.mk` | neu | Wächter der Zitate (`make matrix-zitate`), kein Gate; bei Planung nicht genannt, beim Bau entstanden |
| `harness/README.md` | geändert | Zeile `make matrix-zitate` im Index der Werkzeuge, Bindung [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit) |
| `spec/spezifikation.md` | geändert | `SPEC-052` (Laufzeit-Image) in der Fix-Runde mit dem Anker [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) nachgezogen |
| `docs/plan/planning/done/slice-qa-wartbarkeit.md` | geändert | diese Tabelle |

**Erster Lauf — Ist-Zustand gegen den Plan** (gemessen, nicht angenommen):

- Das Unit-Test-Gate `make test` liegt (`tools/mk/test.mk`, in `GATE_CHECKS`); der frische Lauf (`docker build --target test --no-cache-filter test`) zeigt am Stand `9ff2913` 738 Testfälle in 98 Klassen, alle bestanden. Die Dockerfile-Stage `test` ist damit der Beleg für die Spalte „Test“ der Matrix.
- „Modulgrenzen“ meint nach [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit) (Messmethode „Review der Modulgrenzen, automatisierte Tests“) und [SPEC-041](../../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) die Importregeln der Schichten in `config/detekt/detekt.yml`; deren Quelle ist die Tabelle in `spec/architecture.md` §2. Das Gerüst-Gate steht (`make detekt`, `make detekt-selbsttest`) und ist nicht Gegenstand (§1); der Abgleich Tabelle gegen Konfiguration ist es.
- Entscheidung Erzeuger oder Handpflege: Handpflege. Ein Erzeuger bräuchte ein neues Tag in jedem Testkopf unter `src/test` oder eine zweite Zuordnungsdatei; `make spec-abdeckung` führt die Abdeckung der Festlegungen und ist an Nachrichten und Typen nicht gebunden. Die Zitate prüft `make matrix-zitate` (jeder zitierte Testfall steht im Bericht eines frischen Testlaufs als `PASSED`), kein Gate.

**Fix-Runde nach dem Review** ([Bericht](../../../reviews/2026-10-30-71122af-slice-qa-wartbarkeit.md)): nur `docs/plan/kompatibilitaetsmatrix.md` und die DoD von `slice-qa-kompatibilitaet` (Träger für Lücke 7); kein Eingriff in `src/`. Träger-Angaben der Lücken 1, 5, 7, 8, 9, 10, 14 gegen den Text der Slice-Dateien neu gesetzt; Zeilen zu Transaction Manager, TLS-Fehlerfällen und `tinyint`/`smallint` auf das beschränkt, was ihr Test belegt; Auslöser und Stand-Datum im Abschnitt „Pflege“. Die Matrix ist seit der Fix-Runde `75533b0` auf den Stand `68598df` aufgenommen (775 Testfälle in 106 Klassen); maßgeblich ist die Stand-Zeile der Matrix, nicht dieser Text. Die 775 sind abgeleitet (766 `@Test` plus 9 vererbte Fälle von `QueryBackendContract`), nicht in einem Lauf an `68598df` gemessen. Die 738 Testfälle in 98 Klassen oben sind die Messung am Stand `9ff2913` und bleiben als „Erster Lauf“ richtig. Die Bestandszeilen aus `bda6cd6` stehen in der Matrix als nicht neu gemessen ausgewiesen.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `make gates` ist auf `main` grün (Gerüst mit detekt und Build steht).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Die Matrix ist ohne Clientprofil nicht ausfüllbar.

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

- Die Testbibliothek für den Kern muss mit den Kernregeln (`kotlin.test`, keine Fremdbibliothek) vereinbar sein. — **Ausgang:** entfallen: der Slice liefert nur Dokument, Skript und Make-Fragment; der Wächter `tools/matrix-zitate.sh` ist ein Shell-Skript, das den Bericht des Gradle-Laufs liest, und dieser Slice änderte weder `build.gradle.kts` noch das Lock (die Änderungen dort stammen aus anderen Slices) und führte keine Testbibliothek ein.

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

- **Was hat funktioniert:** Der Schnitt trug, die Rückführung nach §4 war nicht nötig; der Slice liefert ein Dokument (`docs/plan/kompatibilitaetsmatrix.md`), den Wächter `tools/matrix-zitate.sh` (`make matrix-zitate`, kein Gate) und keine Zeile Produktivcode. Der Wächter hielt jeder Mutation der zweiten Verifikation stand (M1 bis M6, Exit 1 mit Zeilennummer); der Ist-Abgleich Importregeln gegen `spec/architecture.md` §2 fand zwei Abweichungen (`protocol` und `transport`) und führt sie, ohne die Regel zu schärfen (Entscheidung der Architektur). Die Kette Review, Verifikation, Review der Neuaufnahme und zweite Verifikation fand, was die Selbstprüfung nicht fand: die Matrix aus `71122af` stützte sich auf einen Stand mit 280 Testfällen, die Neuaufnahme auf 738, dann 775.
- **Was ging anders als geplant:** (a) Der erste Review fand einen MEDIUM (M-1: Träger-Angaben ohne tragenden Slice-Text); die Matrix wurde auf `9ff2913` neu aufgenommen (Verifikation 1: M-1 Träger der Lücken 3, 4, 14; M-2 Neuaufnahme ohne Review). (b) Der Review der Neuaufnahme am Stand `754743d` fand zwei MEDIUM: F-1 (die Zeilen der Abschnitte 3b nannten Typen „unterstützt“, die der Dispatch ablehnt) und F-2 (`SPEC-052` ohne Lastenheft-Anker); Fix-Runde `75533b0` (Lücke 15, Anker [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), Wächter erkennt Fremdformen). (c) Die zweite Verifikation (0 HIGH, 0 MEDIUM, L-1 bis L-5) ist der einzige Beleg für `75533b0`. (d) Plan-Text und Matrix nannten verschiedene Stände (L-1), `docs/plan/spec-abdeckung.md` war veraltet (L-4); beides in dieser Closure nachgezogen. (e) Die Datei-Tabelle von §3 nannte `tools/matrix-zitate.sh`, das Make-Fragment, `harness/README.md` und `SPEC-052` nicht; ergänzt.
- **Reports:** [Review](../../../reviews/2026-10-30-71122af-slice-qa-wartbarkeit.md) (alter Stand `71122af`), [Verifikation 1](../../../reviews/2026-10-31-fa6c4a4-verifikation-slice-qa-wartbarkeit.md), [Review der Neuaufnahme](../../../reviews/2026-10-31-754743d-neuaufnahme-kompatibilitaetsmatrix.md) (gilt für `754743d`: 0 HIGH, 2 MEDIUM, 3 LOW, 4 INFO) und [Verifikation 2](../../../reviews/2026-10-31-48ed775-verifikation-2-slice-qa-wartbarkeit.md) (0 HIGH, 0 MEDIUM, L-1 bis L-5). Die Fix-Runde `75533b0` hat keinen eigenen Review. Ein Validator-Lauf entfällt: der Slice liefert ein Dokument, keinen Nutzerwert am Server.
- **Frischer Testlauf in dieser Closure** (`docker build --no-cache-filter test --target test .` am Stand nach dem Merge von `main`, vor den Inhalts-Commits): Exit 0, `BUILD SUCCESSFUL`, 779 `PASSED`, 106 verschiedene Klassen, 0 `FAILED`. Die 779 liegen vier über den 775 der Matrix (vier Fälle der Mini-Runde von `slice-sim-aenderung-integration`, nicht im Gegenstand der Matrix).

**Zuordnung gemessen | Handvektor | Selbstkonsistenz | Festlegung | nicht gemessen je Aussage.** *Gemessen* heißt beobachtet in einem Lauf dieses Repos; **gegen den Referenzclient und gegen einen echten SQL Server wurde in diesem Slice nichts gemessen.**

| Stärke | Aussage | Beleg im Repo |
|---|---|---|
| gemessen | 212 verschiedene Zitate `Klasse > Testfall` der Matrix stehen im Bericht eines frischen Testlaufs als `PASSED`; jede Fremdform meldet der Wächter mit Zeilennummer (nur bei einem Klassennamen auf `Test`, siehe L-3 unten) | `make matrix-zitate` (Verifikation 2: Exit 0, 779 bestandene Fälle, 212 Zitate); Mutationen M1 bis M6 rot (Verifikation 2) |
| gemessen | Importzahlen je Package (Abschnitt 6a) und die Zahlen des Selbsttests: 9 Importregeln, 65 verbotene Importe, 12 Aufruf-Fälle, 10 Formen von `@Suppress` | `grep ^import` (Verifikation 2); `make detekt-selbsttest` |
| Handvektor | Aussagen der Matrix zu Rahmen, Tokens und RPC-Formen, soweit sie an Handvektoren der Tests hängen (z. B. `26 01 01 07`, `6D 08 00`) | die zitierten Fälle in `RpcBinderTest`, `RpcDispatchTest`; Quelle der Vektoren: Lesart von MS-TDS und Messungen des Spikes, kein Standardbeleg |
| Selbstkonsistenz | Die Spalte „Status“ „unterstützt“ gilt gegen Parser und Dispatch desselben Repos; die Abbildung Backend nach TDS gegen sich selbst | Abschnitte 3b, 2a der Matrix; kein Referenzclient |
| Festlegung | Der Ist-Abgleich 6a (Wertung „deckt“, „teilweise“); die Stufen der Spalte „Herkunft“ der Bestandszeilen; Handpflege statt Erzeuger; `SPEC-052` (Image, Nutzer 65532, Port 1433) | Kopf und Abschnitt „Pflege“ der Matrix; `spec/spezifikation.md` |
| nicht gemessen | Alles am Referenzclient (Träger `slice-qa-kompatibilitaet`); 775 Testfälle an `68598df` (abgeleitet: 766 `@Test` plus 9 vererbte Fälle); Herkunftsstufen der Bestandszeilen aus `bda6cd6`; Rot-Sehen der Importregeln über den Stand `bda6cd6` hinaus; Lücke 15 (14 abgelehnte RPC-Parametertypen, Abweichung von `SPEC-003`) am Client | Matrix Abschnitt 5, Träger `slice-codec-datentypen-erweiterung` (nur Risiko, Vorrat) und `slice-qa-kompatibilitaet` |

**Offene Befunde der zweiten Verifikation, die diese Closure nicht ändert (nur Code oder Code-Kommentar):**

- **L-3 (INFO):** Der Wächter `tools/matrix-zitate.sh` erkennt eine Fremdform nur bei einem Klassennamen auf `Test`; der Skript-Kopf sagt „Fremdform“ ohne diese Einschränkung. Kommentar-Fix im Skript, Gegenstand eines Implementer-Laufs; Register: `BEO-TDS/aussagen-ueber-messung-hinaus`.
- **F-7 (INFO):** Das KDoc in `src/main/.../protocol/dispatch/RequestLoop.kt` (Zeile 44) nennt `TDS_PROTOCOL_ERROR` für einen abgewiesenen RPC; das Verhalten ist `TDS_UNSUPPORTED_TYPE` (50003) bzw. 50002. Kommentar-Fix, eigener Implementer-Lauf. Er berührt `git diff --stat 68598df..HEAD -- src/main` und damit die Stand-Zeile der Matrix: der Lauf prüft, ob der Auslöser im Abschnitt „Pflege“ greift (Kommentar ohne Verhaltensänderung: die Matrix bleibt wahr) und nennt es dort.
- **L-5 (INFO):** Rot-Sehen der Importregeln einmalig am Stand `bda6cd6`, nicht wiederholt; in der Matrix ausgewiesen.

- **Gegenstand:** entfällt (der Slice lieferte; die Lücken der Matrix haben Träger oder sind als Abweichung benannt).
- **Steering-Loop-Eintrag:** Kein `liegt in`: mit diesem Slice ist keine Regel, kein Sensor und keine Spec-Zeile verkörpert worden; gezählt wird, nicht verkörpert. Drei Feststellungen. (1) **Eine Matrix-Aussage „unterstützt“ gilt gegen die Dispatch-Schicht, nicht gegen den Parser:** der Parser liest oder reicht Wertbytes, der Dispatch (`RpcParameters.sqlType`) entscheidet, ob ein Typ den Weg zum Backend findet; eine Matrix, die den Parser zitiert, behauptet mehr als der Server tut (Review F-1). Register: `BEO-TDS/aussage-ueber-schichtgrenze-ohne-test` (jetzt 2×) und `BEO-TDS/aussagen-ueber-messung-hinaus` (weiteres Auftreten, Stand bleibt verkörpert). (2) **Matrix und Slice-Plan veralten gegenseitig:** jede Fix-Runde setzt die Matrix neu auf und lässt im Plan-Text den alten Stand stehen (Verifikation 1 L-1, Verifikation 2 L-1); der Plan sollte die Stand-Zeile der Matrix nennen statt sie zu wiederholen. Neue Beobachtung `BEO-TDS/stand-angaben-in-matrix-und-slice-plan-driften`, 1×. (3) **Benannte Spec-Lücke:** `SPEC-003` führt `tinyint`/`smallint`, `float`/`real`, `money`, `datetime`, GUID, `char`/`varchar` und `binary` unter den beobachteten RPC-Parametertypen, der Dispatch lehnt sie ab (Lücke 15 der Matrix); in der Spezifikation steht die Abweichung nicht, die Matrix führt sie. Träger `slice-codec-datentypen-erweiterung` (nur Risiko §6, Vorrat) und `slice-qa-kompatibilitaet`; die Spezifikation ist hier nicht geändert. Kein unverkörperter Eintrag erreicht mit diesem Slice 3× neu; `BEO-TDS/strukturtest-ohne-variation-der-eingabe` (5×) steht weiter über der Schwelle, nicht verkörpert, Entscheidung beim Nutzer.
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-qa-wartbarkeit.md` ergänzt in `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor` (Verifikation 2 L-4; verkörpert als Handlung, Stand unverändert), `BEO-TDS/aussagen-ueber-messung-hinaus` (Review F-1, F-3; Verifikation 2 L-3, F-7; verkörpert), `BEO-TDS/aussage-ueber-schichtgrenze-ohne-test` (Review F-1; jetzt 2×), `BEO-TDS/offene-punkte-ohne-traeger` (Review M-1, Verifikation 2 L-2; verkörpert) und `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht` (jetzt 2×); eine neue Beobachtung `BEO-TDS/stand-angaben-in-matrix-und-slice-plan-driften` mit dieser Evidence-Datei. Die Zähler folgen aus den Dateien. Nicht über der Schwelle und nicht verkörpert: `BEO-TDS/aussage-ueber-schichtgrenze-ohne-test`, `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht` und die neue Beobachtung (unter 3×).
- **Risiken aus §6:** das eine Risiko trägt genau einen Ausgang (entfallen).
- **Drei Paarungen:** Anker: kein Eintrag trägt `liegt in`, die Paarung entfällt. Folge-Slice: `slice-codec-datentypen-erweiterung` und `slice-qa-kompatibilitaet` existieren in `open/` und führen die Punkte (Lücke 15 bzw. Lücken 3, 4, 14; gegen die Dateien geprüft). Register: eine Evidence-Datei dieses Slice in fünf bestehenden Klassen und eine neue Beobachtung.
- **Für die Welle-Closure von `welle-qualitaet-grundlage`:** die Welle ist mit diesem Slice nicht vollständig; kein Slice der Welle liegt mehr in `in-progress/`, die übrigen liegen in `open/`.

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

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/` trägt nur die `README.md`): keine Treffer.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
