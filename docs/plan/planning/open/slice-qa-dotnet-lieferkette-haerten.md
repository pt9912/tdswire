# Slice slice-qa-dotnet-lieferkette-haerten: .NET-Restore: Signaturprüfung und Schwachstellenprüfung entscheiden

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-qualitaet-grundlage

**Bezug:** [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)

**Berührte Spec-Stellen:** SPEC-025

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

**Ziel:** Die beiden befristeten Ausstiege des .NET-Restore aus [slice-qa-dotnet-referenztest-geruest](../done/slice-qa-dotnet-referenztest-geruest.md) werden beendet oder dauerhaft entschieden: [CO-001](../../carveouts/CO-001-nuget-audit-aus.md) (`NuGetAudit=false`) und [CO-002](../../carveouts/CO-002-nuget-signaturpruefung-fehlt.md) (keine Signaturprüfung der Pakete).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Szenarien gegen den Server dieses Repos — Träger ist [slice-qa-kompatibilitaet](slice-qa-kompatibilitaet.md); dieser Slice berührt die Lieferkette des Restore, keine Testinhalte.
- Vergleichslauf D, EULA-Prüfung — Träger ist [slice-qa-vergleichslauf-d](slice-qa-vergleichslauf-d.md).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [ ] **Liefer-Punkt 1 — Signaturprüfung:** Eine funktionierende `trustedSigners`-Liste (Autorenzertifikate der Pakete im Lock, Repository-Zertifikat von nuget.org) und `signatureValidationMode=require` in `referenztest-dotnet/nuget.config`, belegt durch einen grünen Lauf von `make referenztest-dotnet` und einen roten bei einem Paket, dessen Signierer nicht in der Liste steht; [CO-002](../../carveouts/CO-002-nuget-signaturpruefung-fehlt.md) ist aufgelöst. Oder: der Nutzer entscheidet den dauerhaften Verzicht, dann gehört die Entscheidung in eine ADR (Gate-Lockerung, `AGENTS.md` §3.6) und der Carveout wird mit Verweis darauf aufgelöst. Eine Zeitbox für den Versuch setzt der Nutzer.
- [ ] **Liefer-Punkt 2 — Schwachstellenprüfung:** Der Nutzer entscheidet, ob `NuGetAudit=false` dauerhaft bleibt. Bleibt es, braucht der Verzicht eine ADR (offen, ob eine ADR nötig ist: Begründung dort), und `make referenztest-dotnet-audit` bleibt das manuelle, nicht reproduzierbare Werkzeug; wird der Audit wieder angeschaltet, ist zu entscheiden, wie ein vom Abfragetag abhängiges Ergebnis mit `make gates` vereinbar ist. [CO-001](../../carveouts/CO-001-nuget-audit-aus.md) ist danach aufgelöst.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update: `harness/README.md` §Sensors, Zeile „Nicht behauptet".
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
| `referenztest-dotnet/nuget.config` | update | `trustedSigners`, `signatureValidationMode` ([TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)) |
| `referenztest-dotnet/TdsWire.Referenztests.csproj` | update | `NuGetAudit` nach Entscheidung |
| `docs/plan/carveouts/` | update | beide Carveouts nach `done/` |
| ADR | neu, falls der Nutzer einen dauerhaften Verzicht entscheidet | Gate-Lockerung braucht eine ADR |

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-qa-dotnet-referenztest-geruest](../done/slice-qa-dotnet-referenztest-geruest.md) liegt in `done/`; der Nutzer hat eine Zeitbox für den `trustedSigners`-Versuch gesetzt.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): der `trustedSigners`-Versuch sprengt die Zeitbox; dann eigener Slice je Liefer-Punkt.
- `in-progress` → `open` (blockiert — Carveout?): die Entscheidung des Nutzers zum Audit fehlt.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün, beide Carveouts nach `done/` verschoben und die Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Der aus [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) bekannte Befund: mit dem Repository-Zertifikat als einzigem Eintrag scheiterten Pakete („signed but not by a trusted signer“); der Aufwand einer vollständigen Liste ist nicht gemessen. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` deklariert nur `*` (Default, GF); der Slice berührt `referenztest-dotnet/`, eine feinere Sub-Area ist nicht deklariert.

**Vorgelagert — offene Beobachtungen sichten:** `../observations/BEO-TDS/` durchgegangen; kein Eintrag betrifft die Lieferkette des .NET-Restore.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
