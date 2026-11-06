# Slice slice-referenzabgleich-adr-0028-nachzug: Nachzug der Aussagen über TDS 8.0 an der Referenzinstanz nach Annahme von ADR-0028

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.

**Welle:** welle-referenzabgleich-release

**Bezug:** [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0028](../../adr/0028-referenzinstanz-fuer-tds-8-0.md)

**Berührte Spec-Stellen:** SPEC-045

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912. **Datum:** 2026-11-01.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar.

**Ziel:** Die Aussagen über den Abgleich des TDS-8.0-Pfads an der Referenzinstanz, die [ADR-0028](../../adr/0028-referenzinstanz-fuer-tds-8-0.md) benennt, sind nach deren Annahme durch den Nutzer in Spezifikation und Plan nachgezogen. **Vor der Annahme (Status `Proposed`) wird nichts davon ausgeführt**: eine Empfehlung ist keine Entscheidung. Wird die Option A oder eine andere gewählt, bestimmt die Annahme den Inhalt dieses Slice neu; die DoD unten gilt für die Empfehlung (Option B).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Eine Änderung von [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) — die ADR ist `Accepted` und bleibt unverändert (`AGENTS.md` §3.5); die drei wörtlich benannten Sätze ersetzt [ADR-0028](../../adr/0028-referenzinstanz-fuer-tds-8-0.md) selbst.
- Die Annahme von [ADR-0028](../../adr/0028-referenzinstanz-fuer-tds-8-0.md) — Entscheidung des Nutzers, kein Schritt eines Slice.
- Die Klärung, ob `2022-latest` unter einer anderen Konfiguration `Encrypt=Strict` annimmt — Messung, nicht Nachzug; der Re-Evaluierungs-Trigger der ADR nennt sie.
- Der Live-Lauf selbst — [slice-referenzabgleich-live](../done/slice-referenzabgleich-live.md); hier wird nur seine Aussage über die Referenzinstanz angepasst.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**.

Liefer-Punkte (drei):

- [ ] **Spezifikation:** `SPEC-045` in `spec/spezifikation.md` (Absatz „Grenzen“, Satz „Gemessen ist nur eine Instanz der Version 2022 … der Abgleich beider Pfade entfällt dort“) nennt die gemessene Lage mit Bedingung im Satz (Instanz, Digest, Einzelmessung, Zertifikatsvarianten) und die zweite Referenzinstanz; die Spezifikation nennt dabei weder ADR noch Slice noch Datum einer Messreihe (`AGENTS.md` §3.4 gilt dem Sinn nach für die Stratumsgrenze).
- [ ] **Pläne:** [slice-referenzabgleich-live](../done/slice-referenzabgleich-live.md) §1 (Punkt „Der TDS-8.0-Pfad“: „gefolgert, nicht gemessen“, „der Abgleich entfällt dort“) und [slice-referenzabgleich-mitschnitt](slice-referenzabgleich-mitschnitt.md) §1 (Punkt „Verschlüsselte Mitschnitte und der TDS-8.0-Pfad“: „bietet TDS 8.0 nicht an“) tragen die gemessene Lage statt der Folgerung.
- [ ] **Messwerkzeug wird Entscheidung:** Kommentar von `referenztest-dotnet/Dockerfile` (Stufe `referenzinstanz-2025`) und Kopf von `tools/mk/vergleichslauf-d.mk` nennen die Stufe als angenommene Referenzinstanz; Befund B-6 in [Ausnahmeliste](../../../messungen/referenzabgleich.md) ist mit Verweis auf die Annahme geschlossen.

Nachweis-Pflichten für alle drei Punkte:

- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben; **kein Zaehler wird gesetzt**.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `spec/spezifikation.md` (`SPEC-045`) | update | Grenzen-Absatz, siehe Liefer-Punkt Spezifikation |
| `docs/plan/planning/done/slice-referenzabgleich-live.md`, `…/slice-referenzabgleich-mitschnitt.md` | update | Aussage über die Referenzinstanz |
| `referenztest-dotnet/Dockerfile`, `tools/mk/vergleichslauf-d.mk`, `docs/messungen/referenzabgleich.md` | update | Status der Stufe `referenzinstanz-2025`, Befund B-6 |

## 4. Trigger

**Start** (`open` → `next`): [ADR-0028](../../adr/0028-referenzinstanz-fuer-tds-8-0.md) hat den Status `Accepted` (Entscheidung des Nutzers); ein Verantwortlicher ist eingetragen.

**Rückführungen:**

- `in-progress` → `next` (zu groß): mehr als drei Liefer-Punkte.
- `in-progress` → `open` (blockiert): die ADR wird verworfen oder durch eine andere ersetzt; dann entfällt dieser Slice mit Begründung in §7 (`Gegenstand:`).

## 5. Closure-Trigger

Alle Punkte der DoD erledigt, `make gates` grün, Closure-Notiz mit Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

- Die Annahme wird Option A oder C wählen oder ausbleiben; der Nachzug hätte dann einen anderen Inhalt. — **Ausgang:** offen bis zur Annahme.
- Ein neuer Digest von `2022-latest` oder `2025-latest` ändert das Verhalten bei `Encrypt=Strict` vor dem Nachzug (Re-Evaluierungs-Trigger der ADR). — **Ausgang:** offen bis zum Start.

## 7. Closure-Notiz

Wird bei der Closure gefüllt.

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration führt `*` als einzige Sub-Area.

**Vorgelagert — offene Beobachtungen sichten:** `BEO-TDS/spec-nachzug-nach-messung-offen` (Träger dieses Slice für den Teil `SPEC-045`), `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`.

Alle berührten Sub-Areas GF.
