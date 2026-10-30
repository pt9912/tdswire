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

- NuGet-Audit: Entscheidung, ob das Repo die Schwachstellenprüfung anderweitig führt oder bewusst verzichtet; wird `NuGetAudit=false` nicht beendet, braucht es einen Carveout mit Trigger (`AGENTS.md` §3.6). Bewertet gegen [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit).
- `trustedSigners`: eine funktionierende Liste für `signatureValidationMode=require` unter Linux, oder die begründete Entscheidung dagegen.
- Der Vergleichslauf D (Referenzabgleich nach [SPEC-045](../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig)) als eigenes Make-Target: Ausnahmeliste für das gemessene Schema, Laufzeit sowie Netz-, Platten- und Speicherbedarf auf dem Zielsystem gemessen, EULA-Rechtsprüfung, SQL-Server-Tag für TDS 8.0. In `make gates` kommt D erst nach dieser Messung.
- State und Meldung eines echten SQL Servers ([ADR-0009](../../adr/0009-authentifizierungsmodus.md) §Offen): das Anmelde-Szenario in D (falsches Kennwort, unbekannter Benutzer; einmal gemessen 18456, State 1, Class 14, Meldung mit Benutzernamen) und ein Eintrag der Ausnahmeliste *so gewollt* für die feste Meldung ohne Kennung (`SPEC-012`). Der interne State ist am Client nicht sichtbar. Dieser Slice ist der einzige Träger; [slice-qa-kompatibilitaet](../open/slice-qa-kompatibilitaet.md) führt keinen Referenzabgleich.
- Die Antwortseite des Codecs am echten SQL Server (Träger der Beobachtung `BEO-TDS/messung-am-echten-sql-server-fehlt`, Ausgang aus `welle-protokollcodec`): Reihenfolge der Antwort-Tokens im Strom, Statusbits der DONE-Familie und ordinale Zählung der RETURNVALUE, die der Codec bisher nur als Handvektor und als Festlegung trägt (Closure-Notiz [welle-protokollcodec-results](../done/welle-protokollcodec-results.md), Tabelle *gemessen | Festlegung*). Der Vergleichslauf D deckt sie mit je einem Szenario mit Mehrfach-Resultset und RPC mit Ausgabewert, oder der Slice benennt je Punkt, warum die Messung nicht lieferbar ist.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Wahl des Referenzclients ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)) — sie bestimmt, was die Integrationstests sind; der Slice bindet sie in die CI ein, entscheidet sie nicht.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [ ] CI-Workflow führt `make gates` in Docker aus und ist grün.
- [ ] Clean-Build ohne Host-Toolchain nachgewiesen (Host: Docker und `make`), einschließlich des .NET-Restore: NuGet-Audit und `trustedSigners` entschieden und umgesetzt, oder ein Carveout mit Trigger.
- [ ] Vergleichslauf D als eigenes Make-Target, nicht in `make gates`; Ausnahmeliste (darunter die feste Login-Meldung ohne Kennung) sowie Laufzeit, Netz-, Platten- und Speicherbedarf gemessen, Anmelde-Szenario gegen den echten Server enthalten, Antwort-Token-Reihenfolge, DONE-Statusbits und RETURNVALUE-Zählung des Codecs gemessen (oder je Punkt als nicht lieferbar begründet), Aufnahme in `make gates` erst danach.
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
| CI-Workflow | neu | Clean-Build und Gates nach [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) |
| `harness/README.md` | update | CI-Badge als Lauf-Status |
| `nuget.config`, Restore-Konfiguration des Referenzclient-Tests | neu | NuGet-Audit und `trustedSigners` ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)) |
| Make-Target für den Vergleichslauf D | neu | Referenzabgleich gegen den SQL-Server-Container, außerhalb von `make gates` ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)) |

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

- Die Integrationstests ([ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), Status `Proposed`, wird `Accepted`) sind nicht gebaut; die CI deckt zunächst nur die vorhandenen Gates. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` steht noch als Vorlage; der Slice berührt das ganze Repo (`*`), eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/` trägt nur die `README.md`): keine Treffer.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
