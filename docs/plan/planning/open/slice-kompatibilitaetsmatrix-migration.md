# Slice slice-kompatibilitaetsmatrix-migration: Spec-Nachzug, Tags und Umstellung auf die erzeugte Sicht

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage (wie [slice-kompatibilitaetsmatrix-erzeugen](slice-kompatibilitaetsmatrix-erzeugen.md)).

**Bezug:** [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md).

**Berührte Spec-Stellen:** `spezifikation.md` §1 und §2 (Festlegungen für Batch-Flag, Transaktions-Pakettyp, Pfadwahl je Verbindung, „weitere Tokens“; Feld `Kategorie`; Marker), `SPEC-003`, `SPEC-028`, `SPEC-030`.

**Verantwortlich:** — bis zur Priorisierung.
**Autor:** pt9912 (Planner-Rolle). **Datum:** 2026-11-01.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Die handgeschriebene Matrix wird abgelöst: die Spezifikation trägt, was die Matrix heute allein sagt (Kategorie, Marker, Festlegungen für die gezählten Lücken), die Tests tragen die Tags (`@spec` in Kotlin, `Spec`-Trait in den 33 .NET-Tests), und `docs/plan/kompatibilitaetsmatrix.md` ist die Ausgabe des Erzeugers aus [slice-kompatibilitaetsmatrix-erzeugen](slice-kompatibilitaetsmatrix-erzeugen.md). Kein Satz der alten Matrix wird in eine Datei übernommen, die ein Mensch weiterpflegt, außer als Festlegung der Spec.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Erzeuger und Selbsttest — [slice-kompatibilitaetsmatrix-erzeugen](slice-kompatibilitaetsmatrix-erzeugen.md); hier wird er benutzt.
- Gate — [slice-kompatibilitaetsmatrix-gate](slice-kompatibilitaetsmatrix-gate.md).
- Den Ist-Abgleich 6a (10 Zeilen) als neuen Sensor nachzubauen — ein Test, der `architecture.md` §2 gegen `detekt.yml` liest, wäre ein neuer Sensor; Entscheidung des Nutzers, bis dahin entfällt die Tabelle (Begründung im Plan des Erzeugers).
- Die Messwerte der 31 Referenzclient-Zeilen als Prosa zu erhalten — sie stehen als Assertion im Test und als Messzeile im Lauf.

**Keine Mindestzahl.** Ein Slice mit *einem* echten Ausschluss ist besser als
einer mit vier erfundenen; die vier Klassen sind ein Suchraster, keine
Ausfüll-Liste. Suchreihenfolge: Was übernimmt ein **Folge-Slice** (mit
Kennung — und die Kennung muss den Punkt auch annehmen)? Was bleibt als
**Bestand** bewusst stehen (mit Begründung)? Was wäre ein **anderer Vorgang**?
Welche **Schicht** rührt der Slice nicht an?

Was hier steht, ist die Grenze, an der ein wachsender Slice sich messen lässt:
Wer später etwas mitnimmt, das hier ausgeschlossen war, hat den Plan
**geändert**, nicht nur ergänzt.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [ ] **Spec-Nachzug** (nach den Entscheidungen des Nutzers; Wortlaut ohne Slice-, Wellen- und ADR-Wörter, `spec-straten` des Doku-Gates hält die Richtung): Feld `Kategorie` an den Spec-Zeilen, neue Festlegungen für Batch-Flag, Transaktions-Pakettyp, Pfadwahl je Verbindung und „weitere Tokens“, Marker dort, wo die Zeile ihn nicht trägt (Lücken 2, 3, 5 bis 10, 12 bis 15), und die Tabellen je Fall für Nachrichten, Tokens und Parametertypen, falls der Nutzer die Granularität behalten will.
- [ ] **Tags:** jede Festlegung der Kategorie „Nachricht/Datentyp“ trägt einen bestandenen getaggten Test oder einen erklärten Marker; die 33 .NET-Tests tragen `Spec`-Trait (Zuordnung je Test, Szenario ohne Festlegung ist ein Spec-Befund und steht im Diff-Protokoll); nur Kommentare und Attribute ändern sich, `make test` bleibt unverändert grün.
- [ ] **Umstellung mit Diff-Protokoll:** die Sicht ersetzt die Handdatei; §7 listet alle 141 Zeilen der alten Matrix mit ihrem Verbleib (Festlegung `SPEC-NNN`, Test, entfallen mit Grund) und zählt gegen die Tabelle im Plan des Erzeugers; `make matrix-zitate`, `tools/matrix-zitate.sh` und `tools/mk/matrix-zitate.mk` sind entfernt, die vier offenen Slices, `docs/messungen/referenzabgleich.md`, die Welle und die Zeile in `harness/README.md` sind nachgezogen (Zeitdokumente bleiben unverändert).
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update: `harness/README.md`, `AGENTS.md` §5 Regel 6 und 7 (nach Entscheidung des Nutzers).
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `spec/spezifikation.md` | update | Feld `Kategorie`, Festlegungen, Marker, Tabellen je Fall |
| Kotlin-Tests in `src/test`, `src/integrator`, `src/testserver` | update (Tag) | `@spec` für die neuen Festlegungen |
| `referenztest-dotnet/**/*.cs` | update (Trait) | `Spec`-Trait neben `Kategorie` |
| `docs/plan/kompatibilitaetsmatrix.md` | wird erzeugt | ersetzt die Handdatei |
| `tools/matrix-zitate.sh`, `tools/mk/matrix-zitate.mk`, `harness/README.md` | entfernt / update | Zitate gibt es nicht mehr |

**Optional: Ansatz als Liste, wenn eine Zeile pro Datei nicht trägt** — z. B.
eine Schnittstellenänderung über viele gleichrangige Dateien mit derselben
Begründung, oder ein Ansatz, der sich nicht auf eine Datei herunterbrechen
lässt. Ergänzt die Tabelle, ersetzt sie nicht:

- Größenordnung L (Schätzung, nicht gemessen): rund 56 bis 77 Festlegungs-Zeilen, 33 Traits, mindestens 4 neue Festlegungen. Ist der Spec-Nachzug groß, ist er ein eigener Slice vor der Umstellung (Rückführung nach `next`).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-kompatibilitaetsmatrix-erzeugen](slice-kompatibilitaetsmatrix-erzeugen.md) liegt in `done/`, und die Entscheidungen des Nutzers über Spec-Feld, Granularität und den Ist-Abgleich 6a sind getroffen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): der Spec-Nachzug allein trägt den Slice; dann Zerlegung in Spec-Nachzug und Umstellung.
- `in-progress` → `open` (blockiert — Carveout?): der Nutzer ändert die Entscheidung über die Granularität.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

DoD vollständig, `make gates` grün, die Sicht ist erzeugt und unverändert beim zweiten Lauf, das Diff-Protokoll in §7 ist vollständig, Review-Report liegt vor, Closure-Notiz mit Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Informationsverlust** (Messwerte der 31 Referenzclient-Zeilen, Tabelle 6a, Spalte „Träger“): beabsichtigt, im Diff-Protokoll je Zeile benannt. — **Ausgang:** weiter offen bis zur Umsetzung.
- **Spec-Nachzug nennt Planungswörter** (Slice, Welle, ADR in der Festlegung): die Spec ist Rang 2 und nennt keine Planung. — **Ausgang:** weiter offen: → BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen im Register (8 Dateien unter `evidence/`; die Fortschreibung betrifft Spec und ADR an mehreren Stellen).
- **Verweise auf Abschnitte der alten Matrix** in Zeitdokumenten laufen ins Leere (17 Dateien): bleiben stehen. — **Ausgang:** entfallen: Zeitdokumente zeigen ihren Stand.
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

**Vorgelagert — Sub-Area-Wahl prüfen:** nur `*` (Greenfield, Kürzel `TDS`); berührt sind `spec/`, `src/*/kotlin` (Kommentare), `referenztest-dotnet/`, `tools/`, `docs/plan/`.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, 2026-11-01): `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (8×, über der Schwelle, Entscheidung beim Nutzer), `BEO-TDS/spec-nachzug-nach-messung-offen` (Spec-Nachzug nach Messung; Zähler im Register nachzusehen), `BEO-TDS/spec-tag-nennt-lastenheft-kennung` (1×); sonst keine Treffer.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

<!-- Block für jede berührte Sub-Area duplizieren. Format identisch
mit dem im Baseline-Regelwerk §Ziel-Form: Sub-Area-Modus-Begründung
abgedruckten Block. -->

### Sub-Area: <Name>

- **Modus:** GF | BF | Hybrid
- **Konventionen-Dichte:** <Beleg aus `harness/conventions.md`,
  Adaptions-Block oder Code>
- **Phase-Reife:** Phase 0–5 <Begründung gegen die Phase × Modus-Matrix>
- **Evidenz-/Diskrepanz-Risiko:** <bei BF/Hybrid: was kann die
  Inventur sichtbar machen? bei GF: meist niedrig>
- **Reconciliation-Aufwand:** <Slice-Schätzung;
  Graduation-/Folge-Slice-Trigger>
