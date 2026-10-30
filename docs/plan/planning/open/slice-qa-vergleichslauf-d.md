# Slice slice-qa-vergleichslauf-d: Vergleichslauf D: Referenzabgleich gegen einen echten SQL Server

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0009](../../adr/0009-authentifizierungsmodus.md)

**Berührte Spec-Stellen:** SPEC-045 · SPEC-050

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912. **Datum:** 2026-10-30.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Der Vergleichslauf D ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) Option D, Referenzabgleich nach [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig)) läuft als eigenes Make-Target gegen einen SQL-Server-Container, ist gemessen und steht mit seiner Ausnahmeliste im Repo; in `make gates` kommt er erst nach dieser Messung.

**Übernimmt:** den Gegenstand des DoD-Punkts 3 von [slice-qa-reproduzierbarkeit](../done/slice-qa-reproduzierbarkeit.md). Der Schnitt ist eine Setzung der Closure jenes Slice: der CI-Teil war lieferbar, D hängt an den Szenarien des Referenzclients aus [slice-qa-kompatibilitaet](slice-qa-kompatibilitaet.md) und hatte in der Datei des alten Slice keine Rückführung dafür.

**Umfang** (aus [slice-qa-reproduzierbarkeit](../done/slice-qa-reproduzierbarkeit.md) §1 übernommen):

- Make-Target für D, Container der Referenzinstanz, Referenz-Seite der Läufe, außerhalb von `make gates`.
- Ausnahmeliste für das gemessene Schema, darunter der Eintrag *so gewollt* für die feste Meldung ohne Kennung (`SPEC-012`); Laufzeit sowie Netz-, Platten- und Speicherbedarf auf dem Zielsystem gemessen.
- EULA-Rechtsprüfung für `ACCEPT_EULA=Y` ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md): als Entscheidung festgehalten, rechtlich ungeprüft).
- Anmelde-Szenario in D (falsches Kennwort, unbekannter Benutzer; einmal gemessen 18456, State 1, Class 14, Meldung mit Benutzernamen, [ADR-0009](../../adr/0009-authentifizierungsmodus.md) §Offen). Der interne State ist am Client nicht sichtbar.
- Antwortseite des Codecs am echten SQL Server, soweit der Client sie beobachtet (Träger der Beobachtung `BEO-TDS/messung-am-echten-sql-server-fehlt`): Reihenfolge der Antwort-Tokens, Statusbits der DONE-Familie, ordinale Zählung der RETURNVALUE, je ein Szenario mit Mehrfach-Resultset und RPC mit Ausgabewert, oder je Punkt die Begründung, warum die Messung nicht lieferbar ist. Die Client-API zeigt Tokenfolge und Statusbits nach ihrer Gestalt nicht (Annahme, nicht gemessen); die Messung am Wire trägt [slice-referenzabgleich-mitschnitt](slice-referenzabgleich-mitschnitt.md).
- SQL-Server-Image-Tag für TDS 8.0, siehe offene Frage in §6.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Szenarien des Referenzclients (S1 bis S18) — sie schreibt [slice-qa-kompatibilitaet](slice-qa-kompatibilitaet.md); D führt sie gegen den echten Server aus und braucht sie als Eingang (Start-Trigger).
- Die Server-Seite des Live-Laufs, die Ausnahmeliste je Szenario und der Mitschnitt-Lauf — [slice-referenzabgleich-live](slice-referenzabgleich-live.md) und [slice-referenzabgleich-mitschnitt](slice-referenzabgleich-mitschnitt.md); dieser Slice liefert Target, Container und Rahmen, jene füllen sie mit dem Server des Repos.
- NuGet-Audit und `trustedSigners` des Referenztests — [slice-qa-dotnet-lieferkette-haerten](slice-qa-dotnet-lieferkette-haerten.md) ([CO-001](../../carveouts/CO-001-nuget-audit-aus.md), [CO-002](../../carveouts/CO-002-nuget-signaturpruefung-fehlt.md)).
- Die Wahl des Referenzclients — durch [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) entschieden.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [ ] Vergleichslauf D als eigenes Make-Target, nicht in `make gates`; Eintrag in `harness/README.md` §Sensors oder §Werkzeuge; Ausnahmeliste (darunter die feste Login-Meldung ohne Kennung); Laufzeit, Netz-, Platten- und Speicherbedarf auf dem Zielsystem gemessen; EULA-Prüfung dokumentiert; Aufnahme in `make gates` erst nach der Messung entschieden.
- [ ] Anmelde-Szenario gegen den echten Server (falsches Kennwort, unbekannter Benutzer) in D; der vom Client beobachtbare Teil von Antwort-Token-Reihenfolge, DONE-Statusbits und RETURNVALUE-Zählung des Codecs gemessen oder je Punkt als nicht lieferbar begründet (Wire-Messung bei [slice-referenzabgleich-mitschnitt](slice-referenzabgleich-mitschnitt.md)).
- [ ] SQL-Server-Image-Tag für TDS 8.0 ermittelt und gemessen, oder als nicht verfügbar begründet; der Befund steht in der Ausnahmeliste; ändert er eine Aussage von [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), entsteht eine Folge-ADR (die `Accepted`-ADR bleibt unverändert).
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
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
| `tools/mk/` (Fragment für D) | neu | Make-Target des Vergleichslaufs, Container der Referenzinstanz im eigenen Netzwerk ohne Mount ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [SPEC-050](../../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge)) |
| `harness/README.md` | update | Zeile des Targets in §Sensors oder §Werkzeuge; Bindung |
| Ausnahmeliste | neu | Abweichungen mit Pfad, Szenario, Grund und Art (*so gewollt* oder *Lücke*), nach [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) |
| `referenztest-dotnet/` | update | Anmelde-Szenario und Szenarien für Mehrfach-Resultset und RPC mit Ausgabewert, soweit [slice-qa-kompatibilitaet](slice-qa-kompatibilitaet.md) sie nicht liefert |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-qa-kompatibilitaet](slice-qa-kompatibilitaet.md) liegt in `done/` (Szenarien des Referenzclients), und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): `mcr.microsoft.com` oder das Image der Referenzinstanz ist nicht erreichbar, die EULA-Zustimmung ist rechtlich nicht geklärt, oder die Szenarien des Referenzclients fehlen.

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

- **Offene Frage — Tag für TDS 8.0.** Die Aussagen widersprechen sich. [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) misst, dass `Encrypt=Strict` gegen `2022-latest` mit `Class=20, Number=0` scheitert, nennt den Grund (SQL Server 2022 biete TDS 8.0 nicht an) aber nur als gefolgert und den Tag für TDS 8.0 als nicht ermittelt. [slice-referenzabgleich-live](slice-referenzabgleich-live.md) schreibt dagegen, die Referenzinstanz 2022 biete den TDS-8.0-Pfad nicht an, und lässt den Abgleich dort entfallen. Der Vorgänger-Slice wies „SQL-Server-Tag für TDS 8.0“ dem Vergleichslauf D zu (Review F-12). Offen: ob dieser Slice einen Tag sucht oder die Grenze setzt, und ob „bietet nicht an“ gemessen werden muss, bevor live es als Ausschluss führt. — **Ausgang:** offen bis zur Closure.
- Die Szenarien des Referenzclients ([slice-qa-kompatibilitaet](slice-qa-kompatibilitaet.md)) sind der Eingang von D; fehlen sie, bleibt D ohne Anmelde- und Antwort-Szenarien. — **Ausgang:** offen bis zur Closure.
- Laufzeit, Platten- und Speicherbedarf sind nur an einem Host gemessen ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md): ein Lauf, `2022-latest` beweglich, bis auf den Digest); ob D in `make gates` kommt, hängt an der Messung auf dem Zielsystem. — **Ausgang:** offen bis zur Closure.
- Die EULA-Zustimmung (`ACCEPT_EULA=Y`) ist rechtlich ungeprüft. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` führt `*` als einzige Sub-Area; der Slice berührt das ganze Repo, eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen. Treffer: `BEO-TDS/messung-am-echten-sql-server-fehlt` (Stand *geplant*, dieser Slice ist der Träger für den Teil, den der Client beobachtet) und `BEO-TDS/vergleichslauf-d-ungeklaert` (Stand *offen*, Träger dieser Slice); beide aus `slice-qa-reproduzierbarkeit` übertragen.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
