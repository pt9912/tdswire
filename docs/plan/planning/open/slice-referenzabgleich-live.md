# Slice slice-referenzabgleich-live: Referenzabgleich, Live-Lauf: derselbe Referenzclient gegen die Referenzinstanz und gegen den Server mit Simulations-Backend

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-referenzabgleich-release

**Bezug:** [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)

**Berührte Spec-Stellen:** SPEC-025 · SPEC-032 · SPEC-035 · SPEC-045 · SPEC-050 · [TDS-FA-11.a](../../../../spec/spezifikation.md#tds-fa-11a--simulations-backend)

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

**Ziel:** Der Referenzclient führt dieselben Szenarien nacheinander gegen eine SQL-Server-Referenzinstanz und gegen den Server mit Simulations-Backend aus, das dasselbe Testschema beschreibt; der Lauf ist grün, wenn beide Ausgaben gleich sind oder genau der dokumentierten Abweichung entsprechen (SPEC-045).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Make-Target des Vergleichslaufs D, Container der Referenzinstanz, die Referenz-Seite der Läufe, EULA-Prüfung, Messung von Laufzeit und Ressourcen und der Image-Tag für TDS 8.0 — [slice-qa-vergleichslauf-d](slice-qa-vergleichslauf-d.md); dieser Slice nutzt sie und ergänzt die Server-Seite. Fehlt dort etwas, wenn dieser Slice startet, ist das ein Rückführungsgrund, kein stilles Nachbauen.
- Die Szenarien des Referenzclients selbst (S1 bis S18) — [slice-qa-kompatibilitaet](slice-qa-kompatibilitaet.md); dieser Slice führt dieselben Szenarien gegen zwei Ziele aus (das Ziel ist ein Parameter) und schreibt keine zweite Fassung.
- Der Mitschnitt-Lauf nach SPEC-045 — [slice-referenzabgleich-mitschnitt](slice-referenzabgleich-mitschnitt.md); die Tokenfolge sieht der Live-Lauf nicht, er vergleicht, was der Client ausgibt.
- Der TDS-8.0-Pfad — an der Referenzinstanz Version 2022 scheitert `Encrypt=Strict`; dass sie TDS 8.0 nicht anbietet, ist gefolgert, nicht gemessen ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), SPEC-045; offene Frage in [slice-qa-vergleichslauf-d](slice-qa-vergleichslauf-d.md) §6); der Abgleich entfällt dort und steht als Grenze in der Ausnahmeliste.
- Das Beheben gefundener Abweichungen im Server — je Befund ein Slice nach der Messung; dieser Slice dokumentiert sie als *Lücke* mit Verweis auf die offene Arbeit.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Server-Seite:** Die Tabellenbeschreibung des Simulations-Backends bildet das Testschema der Referenzinstanz ab (beide getrennt gepflegt und gemeinsam geändert, SPEC-045; der Weg des Ladens folgt der ADR aus [slice-adr-simulation-formen](slice-adr-simulation-formen.md)); der Server mit Simulations-Backend läuft für den Lauf in Docker (eigener Container im eigenen Netzwerk, ohne Mount, Basis-Image per Digest).
- [ ] **Live-Lauf:** Das Szenario (Anmeldung, SELECT mit und ohne Parameter, NULL, Unicode, Decimal, Datum, leere und mehrteilige Ergebnisse, Fehler vor und nach Zeilen, OUTPUT und RETURNSTATUS soweit freigegeben, Cancel mit Folgeabfrage) läuft nacheinander gegen beide Ziele, je Schritt eine Ausgabezeile; vom Server selbst vergebene Werte (Objekt-IDs, Sitzungs-IDs, Zeitstempel, Versionstext) werden nur auf vorhanden oder nicht vorhanden verglichen, Dauern werden nicht verglichen.
- [ ] **Ausnahmeliste und Urteil:** Jede Abweichung steht mit Pfad, Anweisung oder Szenario, Grund und Art (*so gewollt* oder *Lücke* mit Verweis auf die offene Arbeit) in der Liste; eine kleinere oder größere Abweichung als die dokumentierte macht den Lauf rot (Test mit verschiedenen Abweichungen); ob der Lauf in `make gates` kommt, entscheidet die in [slice-qa-vergleichslauf-d](slice-qa-vergleichslauf-d.md) gemessene Laufzeit.

Nachweis-Pflichten für alle drei Punkte:

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
| Referenztestprojekt (`referenztest-dotnet/`) | update | Ziel als Parameter der vorhandenen Szenarien; Ausgabe je Schritt eine Zeile |
| Tabellenbeschreibung des Testschemas <!-- d-check:ignore (geplant: Ort nach ADR) --> | neu | Gegenstück zum Schema-Skript der Referenzinstanz |
| Ausnahmeliste <!-- d-check:ignore (geplant: neue Datei) --> | neu | Ablage nach dem Rahmen aus [slice-qa-vergleichslauf-d](slice-qa-vergleichslauf-d.md) |
| `tools/mk/` | update | Make-Target des Vergleichslaufs aus [slice-qa-vergleichslauf-d](slice-qa-vergleichslauf-d.md) erweitern; Eintrag in `harness/README.md` §Sensors |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [welle-simulations-backend](../welle-simulations-backend.md) liegt in `done/`, und [slice-qa-vergleichslauf-d](slice-qa-vergleichslauf-d.md) und [slice-qa-kompatibilitaet](slice-qa-kompatibilitaet.md) liegen in `done/` (Vergleichslauf D, Referenzinstanz, Szenarien), und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2 oder die Server-Seite sprengt den Rahmen; Schnittvorschlag: Schema-Gleichlauf als eigener Slice.
- `in-progress` → `open` (blockiert — Carveout?): das Make-Target des Vergleichslaufs oder die Referenzinstanz aus [slice-qa-vergleichslauf-d](slice-qa-vergleichslauf-d.md) steht nicht zur Verfügung, oder die EULA-Zustimmung ist rechtlich nicht geklärt.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün, der Review-Report liegt vor, und die Closure-Notiz trägt die Ausnahmeliste mit Art und Verweis je Regel, die Grenzen des Laufs (eine Instanz Version 2022, TDS 7.4) und den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Das Schema der Referenzinstanz und die Beschreibung des Simulations-Backends werden getrennt gepflegt und können auseinanderlaufen; ob ein Test beide gegeneinander hält (Spalten und Typen aus dem Katalog der Instanz gegen die Beschreibung), ist offen (Frage in §6). — **Ausgang:** offen bis zur Closure.
- Gemessen ist nur eine Instanz der Version 2022; `Encrypt=Strict` scheitert dort und das Server-Log nennt TLS 1.0 bis 1.2 ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)): TDS 8.0 und TLS 1.3 sind an ihr nicht abgleichbar (`BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`). — **Ausgang:** offen bis zur Closure.
- Der Fall „Fehler nach Zeilen“ hat bisher keine Server-Referenz; der Lauf liefert sie erstmals und kann Festlegungen aus der Spezifikation und aus [ADR-0007](../../adr/0007-fehler-und-attention.md) widerlegen. Ein Widerspruch führt zu einer benannten Spec-Lücke oder einer neuen ADR, nicht zu einer stillen Anpassung der Ausnahmeliste. — **Ausgang:** offen bis zur Closure.
- Verglichen wird, was der Lauf ausgibt; was nicht ausgegeben wird, ist nicht verglichen. Die Closure nennt die Grenze und leitet aus „grün“ keine Aussage über Tokenfolge oder Statusbits ab. — **Ausgang:** offen bis zur Closure.
- Der Lauf braucht `ACCEPT_EULA=Y` für das Image der Referenzinstanz; die Zustimmung ist in der ADR als Entscheidung festgehalten und rechtlich ungeprüft ([slice-qa-vergleichslauf-d](slice-qa-vergleichslauf-d.md) trägt die Prüfung). — **Ausgang:** offen bis zur Closure.
- Ein Vergleich mit Ausnahmeliste kann durch zu weite Regeln alles grün machen; Regeln sind so eng wie möglich (Pfad, Anweisung oder Szenario), und der Test belegt, dass eine nicht gelistete Abweichung rot macht. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Der Slice berührt Testwerkzeug, Docker-Stages und Dokumentation; keine dieser Strukturen trägt eine eigene Strukturregel (Achse 1) oder eine eigene Inventur-Linie (Achse 2) neben dem Repo insgesamt: eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, 19 Beobachtungen, Stand des Worktrees auf `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 9×, Stand *geplant* (Träger [slice-qa-vergleichslauf-d](slice-qa-vergleichslauf-d.md)). Träger der Server-Seite für den Teil, den der Client beobachtet; die Wire-Messung trägt [slice-referenzabgleich-mitschnitt](slice-referenzabgleich-mitschnitt.md).
- `BEO-TDS/vergleichslauf-d-ungeklaert` — 1×, offen (Träger [slice-qa-vergleichslauf-d](slice-qa-vergleichslauf-d.md)). Betrifft CI-Machbarkeit, EULA und Häufigkeit des Laufs: Träger bleibt [slice-qa-vergleichslauf-d](slice-qa-vergleichslauf-d.md), dieser Slice wartet auf deren Ergebnis.
- `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` — 7×, Stand *geplant* (Träger [slice-qa-kompatibilitaet](slice-qa-kompatibilitaet.md)). Betrifft die Grenze des Laufs: TDS 8.0 ist an der Referenzinstanz nicht abgleichbar.
- `BEO-TDS/aussagen-ueber-messung-hinaus` — 16×, Stand *verkörpert* (`AGENTS.md` §3.8, Reviewer-Skill). Betrifft diesen Slice: Jede Aussage über „gleich wie die Referenz“ nennt Instanz, Version und Szenario (§6).
- `BEO-TDS/strukturtest-ohne-variation-der-eingabe` — 1×, offen. Betrifft den Test der Ausnahmeliste: verschiedene Abweichungen statt einer (§2).
- `BEO-TDS/test-stage-cache-verdeckt-wiederholung` — 2×, offen. Betrifft Tests in der Docker-Stage: gecachte grüne Läufe belegen Wiederholung nicht.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Slice und Closure-Notiz nennen `ADR-*`- und `TDS-*`-Kennungen als Link.
- Ohne Treffer: `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`, `BEO-TDS/offene-punkte-ohne-traeger`, `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung`, `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`, `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`, `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler`, `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`, `BEO-TDS/ablage-im-repo-bricht-docs-check`, `BEO-TDS/katalog-und-lock-ohne-gegenpruefung`, `BEO-TDS/java-quelle-umgeht-kotlin-regel`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/adapter-festlegungen-ohne-slice`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
