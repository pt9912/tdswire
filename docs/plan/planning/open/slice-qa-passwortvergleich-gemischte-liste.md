# Slice slice-qa-passwortvergleich-gemischte-liste: Passwortvergleich in konstanter Zeit bei gemischten Benutzerlisten angleichen und messen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-FA-02](../../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [ADR-0009](../../adr/0009-authentifizierungsmodus.md)

**Berührte Spec-Stellen:** SPEC-051 · Abschnitt „Kotlin-Signatur des Authentifizierungsadapters“ (Bauart des Vergleichs)

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912. **Datum:** 2026-11-06.

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Der Passwortvergleich in konstanter Zeit ([TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)) gilt auch für gemischte Benutzerlisten (niedrigere Iterationszahl oder `plain` neben `pbkdf2-sha256`): Die Zeit trennt dort bekannte nicht mehr von unbekannten Benutzernamen, belegt durch eine Messung für gemischte Listen; der Satz „das ist eine Grenze, keine Zusage“ in `spec/spezifikation.md` entfällt oder wird auf den gemessenen Rest eingeschränkt.

**Herkunft.** Beobachtung `BEO-TDS/passwortvergleich-gemischte-liste-ohne-traeger` (Review `docs/reviews/2026-11-06-f6ce18c7-spec-zielbild-abweichungen.md`, F-4). Nutzerentscheid vom 2026-11-06: der Träger-Vorschlag des Planners (ein Folge-Slice) ist übernommen; Grundsatz des Nutzers: die Spezifikation ist das Zielbild, der Code folgt ihr. Gemessen ist bisher nur die Trennung selbst (Evidence von `slice-qa-sicherheit`: Attrappe 420000 Iterationen rund 102 ms, bekannt mit 210000 rund 48 ms, `plain` rund 0,05 ms; Docker-Image des Laufs, Host des Laufs, Werte gelten nur dort). Zeitgleichheit bei gleicher Iterationszahl ist gemessen (`BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Drosselung wiederholter Anmeldeversuche — `slice-qa-anmeldedrosselung` (geschlossen, `CO-003` aufgelöst); dieser Slice ändert die Rechenarbeit je Versuch, nicht die Zahl der Versuche.
- Dateirechte und Übergabe der Benutzerliste im Betrieb — Risiko von `slice-release-artefakt-lizenz`, Entscheidung des Nutzers steht aus.
- Ein Adapter mit eigener Benutzerverwaltung — der Vertrag kann die Anforderung nicht erzwingen (Spezifikation, SPEC-051); der Slice betrifft die Einstiegsimplementierung.
- Zeitgleichheit gegen einen entfernten Angreifer über ein Netz — die Messung läuft im Prozess am Adapter; was ein Netz an Rauschen addiert, ist nicht gemessen und wird nicht behauptet (`AGENTS.md` §3.8).

**Keine Mindestzahl.** Die vier Ausschlüsse stehen mit Grund da; keiner ist erfunden.

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Angleichung des Vergleichs** ([TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [TDS-FA-02](../../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung)): Der Adapter leistet bei gemischten Listen für bekannte und unbekannte Benutzernamen dieselbe Rechenarbeit; zwei Wege stehen offen — (a) Attrappe je Eintragsart (der Scheinvergleich läuft mit der Eintragsart und Iterationszahl, die der bekannte Eintrag hätte) oder (b) gleiche Arbeit für `plain` und für niedrigere Iterationszahlen (Zusatzableitung bis zur Arbeit der Attrappe). Die Wahl und ihre Kosten (Rechenzeit je Anmeldung) trägt die Closure-Notiz; sie ist eine Setzung des Planners, bis der Nutzer sie bestätigt.
- [ ] **Messung für gemischte Listen** ([TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)): ein Test mit echter Ableitung, Median von 15 Aufrufen, im Docker-Image des Repos (Maß wie `AuthTimingTest`): bekannt mit falschem Passwort, unbekannt und bekannt mit richtigem Passwort brauchen in einer Liste mit niedrigerer Iterationszahl und in einer Liste mit `plain`-Eintrag innerhalb derselben Toleranz gleich lang (die Toleranz nennt der Test, nicht dieser Plan). **Beleg:** die Mutation „Angleichung entfernt“ macht mindestens einen Test rot; die Messwerte mit Host und Bedingung stehen in der Closure-Notiz, nicht als Zusage über andere Hosts.
- [ ] **Spec-Nachzug** ([TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)): die Sätze „das ist eine Grenze, keine Zusage“ und „Verbleibende Grenze bei gemischten Listen“ in `spec/spezifikation.md` (Abschnitt zu SPEC-051 und Bauart des Vergleichs) tragen den gemessenen Stand: Zusage nur so weit, wie die Messung des zweiten Punkts trägt; ein nicht angeglichener Rest bleibt als Grenze benannt.

Nachweis-Pflichten für alle drei Punkte:

- [ ] Jeder neue Test trägt im KDoc den Block-Tag `@spec SPEC-051`; die Tests laufen in `make test` und sind grün.
- [ ] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet (`BEO-TDS/aussagen-ueber-messung-hinaus`).
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist (Spezifikation: dritter Liefer-Punkt).
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
| `src/main/kotlin/io/github/pt9912/tdswire/config/auth/UserListAuthAdapter.kt` | update | Scheinvergleich und Vergleich bei gemischten Listen angleichen ([TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)) |
| `src/main/kotlin/io/github/pt9912/tdswire/config/auth/UserList.kt` | update | Wahl der Attrappe je Eintragsart, falls Weg (a) |
| `src/test/kotlin/io/github/pt9912/tdswire/config/auth/AuthTimingTest.kt` | update | Messung für gemischte Listen (Happy, Boundary, Negative) statt „messbare Zeitgrenze“ |
| `spec/spezifikation.md` Abschnitt Authentifizierungsadapter | update | Nachzug der Grenze auf den gemessenen Stand |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Der Nutzer hat den Weg (a) oder (b) bestätigt oder dem Vorschlag des Implementers zugestimmt (Kosten je Anmeldung sind Teil der Entscheidung), und ein Verantwortlicher ist eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte; Schnittvorschlag: Angleichung mit Messung | Spec-Nachzug.
- `in-progress` → `open` (blockiert): der Nutzer entscheidet, dass die Grenze das Zielbild ist; dann geht der Slice mit der Zeile `Gegenstand:` nach `done/`, der Satz in der Spezifikation wird als Nutzerentscheidung gekennzeichnet.

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün (einschließlich `make test`), der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung *Messung | Festlegung* mit Host und Bedingung der Messwerte und den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Gleiche Arbeit kostet Rechenzeit: Läuft für `plain`-Einträge dieselbe Ableitung wie für die Attrappe, verliert der `plain`-Testmodus seine Schnelligkeit; ob das für Tests tragbar ist, ist ungeklärt. — **Ausgang:** offen bis zur Closure.
- Zeitgleichheit im Prozess ist nicht Zeitgleichheit am Draht; die Messung trägt nur den Adapter ([TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)). — **Ausgang:** offen bis zur Closure.
- Die Wahl zwischen (a) und (b) ist eine Setzung des Planners, nicht vom Nutzer bestätigt (`BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`). — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); der Slice berührt den Authentifizierungsadapter und die Spezifikation, eine eigene Sub-Area besteht nicht.

**Vorgelagert — offene Beobachtungen sichten:** `BEO-TDS/passwortvergleich-gemischte-liste-ohne-traeger` (Gegenstand dieses Slice), `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` (Zeitgleichheit bei gleicher Iterationszahl), `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert), `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`, `BEO-TDS/strukturtest-ohne-variation-der-eingabe`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
