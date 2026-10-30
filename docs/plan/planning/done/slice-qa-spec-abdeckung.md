# Slice slice-qa-spec-abdeckung: Spezifikations-Abdeckungsmatrix: jede Festlegung durch Tests belegt

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)

**Berührte Spec-Stellen:** SPEC-024 · SPEC-025 · SPEC-032

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

**Ziel:** Jede Festlegung der Spezifikation (`SPEC-*`) ist durch Modul-Tests, E2E-Tests oder ein Gate nachgewiesen, und eine Abdeckungsmatrix unter `docs/plan/` zeigt je `SPEC-*` ihre Nachweise und die Festlegungen ohne Nachweis. Ein Nachweis ist ein bestandener Lauf; die bloße Nennung der `SPEC-*` in einem Test belegt nichts.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Requirements Traceability Matrix der Anforderungen (`TDS-*`) — sie entsteht per `make doc-trace` und wird nicht abgespeichert (`AGENTS.md` §5).
- Die Tests selbst — sie entstehen mit den Slices der Anforderungen; dieser Slice macht ihre Abdeckung sichtbar.

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [x] Konvention festgelegt: Ein Test nennt die `SPEC-*`, die er nachweist, im KDoc über der Testfunktion mit dem Block-Tag `@spec SPEC-<NNN>` (mehrere Kennungen durch Leerzeichen getrennt); kein JUnit-`@Tag`, weil die Kernregeln in Tests nur `kotlin.test` erlauben. Der Selbsttest belegt, dass ein Test ohne Namensvetter im Bericht und ein Tag auf eine unbekannte Kennung auffallen. Beleg: Kopf von `tools/spec-abdeckung.sh`, Kopfzeile der Matrix, Zeile in `harness/README.md`; Verifikation (1), Selbsttest-Fälle `SPEC-004` und `SPEC-099`.
- [x] Erzeuger schreibt die Abdeckungsmatrix nach `docs/plan/` (je `SPEC-*`: Nachweise nach Art, Festlegungen ohne Nachweis sichtbar), analog zu `make e2e-abdeckung`. Er liest den `@spec`-Tag aus dem Quelltext und gleicht Klasse und Methodenname mit den Berichten der Testläufe ab: nur ein bestandener Test zählt, ein nicht gelaufener oder roter nicht.
- [x] Entscheidung, ob Waisen das Gate brechen, getroffen; bei einem Gate steht die Bindung in `harness/README.md`. Beleg: Nutzer 2026-10-30, Option D: nur berichten, kein Gate; `SPEC_ABDECKUNG_STRENG=1` bleibt optionaler Schalter, nichts in `GATE_CHECKS`.
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
| `tools/mk/` | neu | Make-Fragment für den Erzeuger |
| `harness/README.md` | update | Zeile für das neue Target |
| Abdeckungsmatrix, Datei unter docs/plan/ | neu | erzeugte Sicht über alle `SPEC-*` |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `slice-qa-wartbarkeit` liegt in `done/` (das Unit-Test-Gate steht).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): Es gibt noch keine Tests, deren Abdeckung sich messen ließe.

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

- Die Testberichte liegen nach dem Lauf in Docker nicht auf dem Host; der Erzeuger braucht sie als Ausgabe der Test-Stage. — **Ausgang:** entfallen: Der Export läuft über eine Stage mit `--output` in ein temporäres Verzeichnis außerhalb des Repos (`mktemp -d`, `trap`); Matrix und Waisenzahl kommen so auf den Host, gemessen in Review und Verifikation (Verifikation (2), (4)).
- Ein Gate auf Festlegungen ohne Nachweis färbt `make gates` rot, solange Tests fehlen (wie bei der E2E-Sicht, die ausdrücklich kein Gate ist). — **Ausgang:** entfallen: Entscheidung gefallen (Nutzer 2026-10-30, Option D: nur berichten, kein Gate).

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

- **Belege:** Review [2026-10-30-36e1ed0-slice-qa-spec-abdeckung](../../../reviews/2026-10-30-36e1ed0-slice-qa-spec-abdeckung.md), Verifikation [2026-10-30-36e1ed0-verifikation-slice-qa-spec-abdeckung](../../../reviews/2026-10-30-36e1ed0-verifikation-slice-qa-spec-abdeckung.md). Die Befunde sind in den Commits `9e2df83` und `d086f59` eingearbeitet, Merge `edf60b0`. Bezug: [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit).
- **Was funktioniert hat:** `make spec-abdeckung` liest die `@spec`-Tags aus dem Quelltext, gleicht sie mit den Berichten des Testlaufs ab und schreibt die Matrix deterministisch (byte-gleich bei Wiederholung). Rote, nicht gelaufene und übersprungene Tests zählen nicht. Das Rezept legt Matrix und Waisenzahl nach `mktemp -d` außerhalb des Repos ab und räumt per `trap`; ohne gültige Waisenzahl endet der Lauf nicht grün.
- **Zuordnung, Stand der Closure:**

  | Aussage | Zuordnung | Wert und Grenze |
  |---|---|---|
  | Matrix des Laufs bei der Closure | gemessen | 12 von 51 Festlegungen mit mindestens einem bestandenen getaggten Test, 7 Gate-Festlegungen ungemessen, 32 ohne Nachweis; ein einziges Feld wich vom Stand im Merge `edf60b0` ab (Zählung bei `SPEC-051`) und ist neu erzeugt. Die Zahlen sind der Stand jenes Testlaufs, nicht eines Commits |
  | Laufzeit `make spec-abdeckung` | gemessen (Verifikation (7)) | 56 s nach einer Teständerung, 0,75 s bei Cache-Treffer; ein Wert für einen Host, der 25-s-Wert des Implementers ist weder bestätigt noch widerlegt |
  | Mutationsstand | gemessen | Implementer 9 + 3 gefangen; Verifier 15 Mutationen, 14 gefangen, M8 („ein neuer KDoc-Block verwirft einen offenen Tag nicht mehr“) überlebte und ist seither mit einem Fixture-Fall im Selbsttest gedeckt |
  | Option D: nur berichten, kein Gate, `SPEC_ABDECKUNG_STRENG=1` optional, nichts in `GATE_CHECKS` | Setzung | Nutzerentscheidung 2026-10-30 |
  | Klasse „Gate-Festlegung“ (Titelzelle nennt ein `make`-Target oder „Gate noch einzurichten“) | Setzung | zählt weder als Nachweis noch als Waise; ein Gate als Nachweis kennt der Erzeuger nicht |
  | Urteilswort „mindestens ein bestandener getaggter Test“ | Setzung | statt „nachgewiesen“ (Review F-4); der Tag nennt, was der Autor meint |
  | Qualität der Assertions hinter einem Tag | nicht gemessen | der Erzeuger prüft Bestehen, nicht Aussagekraft |
  | Entity-Dekodierung (`&lt;` …) und Titel mit `\|` im Selbsttest | nicht gemessen | ein Fall bleibt ungeprüft |
  | Verhalten bei geändertem Zeilenformat der Spezifikation | nicht gemessen | der Erzeuger meldet dann zu wenige Festlegungen und bricht nur bei null |
  | Selbsttest in `make gates` | nicht der Fall | `make spec-abdeckung-selbsttest` läuft nur auf Aufruf |

- **Was ging anders als geplant:** Die Entscheidung zur Gate-Frage fiel erst während der Verifikation; der Slice-Text trug sie zunächst nicht (Review F-1, nachgezogen). Eine Aussage war breiter als ihr Beleg: „ein umbenannter Test verliert seinen Nachweis“ gilt in der Live-Pipeline nur gegen einen Bericht ohne Namensvetter, weil der Testlauf die Berichte frisch erzeugt (Verifikation F-5; [AGENTS.md](../../../../AGENTS.md) §3.8). Das Rezept legte zunächst unter dem Repo ab und brach `docs-check` beim Abbruch (Verifikation F-7).
- **Steering-Loop-Eintrag:** Kein Eintrag verkörpert, daher kein `liegt in`. Festgestellt: (1) Die Handlung „die Matrix bei jeder Slice-Closure mit Tests neu erzeugen und mit dem Closure-Commit committen“ ist als Regel vorgeschlagen; ohne Gate bleibt sie eine geschuldete Handlung, und die Matrix veraltete bereits dreimal im Lauf dieses Slice (Review F-1, Verifikation F-3, Closure), allerdings in einem Vorgang, also gezählt 1× ([BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor](../observations/BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor/observation.md)). Unter der 3×-Schwelle wird sie nicht in einen Command oder ein Gate verkörpert; die Frage, ob sie früher als DoD-Punkt der Folge-Slices gelten soll, geht an den Nutzer. (2) Die verkörperte Regel [BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md) trat erneut auf (Review F-4, Verifikation F-5); der Reviewer fing es. (3) Eine benannte Spec-Lücke fiel nicht an.
- **Beobachtungs-Register (`../observations/`):** neu [BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor](../observations/BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor/observation.md) und [BEO-TDS/ablage-im-repo-bricht-docs-check](../observations/BEO-TDS/ablage-im-repo-bricht-docs-check/observation.md), beide `offen`; `evidence/slice-qa-spec-abdeckung.md` ergänzt in [BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md) (Stand bleibt `verkörpert`) und [BEO-TDS/test-stage-cache-verdeckt-wiederholung](../observations/BEO-TDS/test-stage-cache-verdeckt-wiederholung/observation.md) (Stand bleibt `offen`). Die Zähler folgen aus den Dateien; kein Eintrag erreicht mit diesem Slice 3× ohne Stand.
- **Risiken aus §6:** Zwei, je ein Ausgang: Testberichte auf dem Host entfallen durch den Export der Stage; Gate auf Waisen entfallen durch die Entscheidung (Option D).
- **Drei Paarungen:** Das Repo führt Wellen; die Paarungen prüft die Closure von [welle-qualitaet-grundlage](../welle-qualitaet-grundlage.md). Anker ↔ Folge-Slice: kein Anker gesetzt, kein Folge-Slice nötig. Register ↔ Closure-Notiz ist getragen (alle vier Pfade stehen oben).

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield); der Slice berührt das ganze Repo, eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/` trägt nur die `README.md`): keine Treffer.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
