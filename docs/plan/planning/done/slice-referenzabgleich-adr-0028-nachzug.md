# Slice slice-referenzabgleich-adr-0028-nachzug: Nachzug der Aussagen über TDS 8.0 an der Referenzinstanz nach Annahme von ADR-0028

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.

**Welle:** welle-referenzabgleich-release

**Bezug:** [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0028](../../adr/0028-referenzinstanz-fuer-tds-8-0.md)

**Berührte Spec-Stellen:** SPEC-045, SPEC-050

**Verantwortlich:** pt9912 (Zweig slice-referenzabgleich-adr-0028-nachzug)

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

- [x] **Spezifikation:** `SPEC-045` in `spec/spezifikation.md` (Absatz „Grenzen“, Satz „Gemessen ist nur eine Instanz der Version 2022 … der Abgleich beider Pfade entfällt dort“) und die Tabellenzeile `SPEC-050` (Referenzinstanzen) nennen die gemessene Lage mit Bedingung im Satz (Instanz, Digest, Einzelmessung, Zertifikatsvarianten) und die zweite Referenzinstanz; die Spezifikation nennt dabei weder ADR noch Slice noch Datum einer Messreihe (`AGENTS.md` §3.4 gilt dem Sinn nach für die Stratumsgrenze).
- [x] **Pläne:** [slice-referenzabgleich-live](../done/slice-referenzabgleich-live.md) §1 (Punkt „Der TDS-8.0-Pfad“: „gefolgert, nicht gemessen“, „der Abgleich entfällt dort“) und [slice-referenzabgleich-mitschnitt](../done/slice-referenzabgleich-mitschnitt.md) §1 (Punkt „Verschlüsselte Mitschnitte und der TDS-8.0-Pfad“: „bietet TDS 8.0 nicht an“) tragen die gemessene Lage statt der Folgerung.
- [x] **Messwerkzeug wird Entscheidung:** Kommentar von `referenztest-dotnet/Dockerfile` (Stufe `referenzinstanz-2025`) und Kopf von `tools/mk/vergleichslauf-d.mk` nennen die Stufe als angenommene Referenzinstanz; Befund B-6 in [Ausnahmeliste](../../../messungen/referenzabgleich.md) ist mit Verweis auf die Annahme geschlossen.

Nachweis-Pflichten für alle drei Punkte:

- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben; **kein Zaehler wird gesetzt**.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `spec/spezifikation.md` (`SPEC-045`, Tabellenzeile `SPEC-050`) | update | Grenzen-Absatz von `SPEC-045` und Referenzinstanzen in `SPEC-050`, siehe Liefer-Punkt Spezifikation |
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

- Die Annahme wird Option A oder C wählen oder ausbleiben; der Nachzug hätte dann einen anderen Inhalt. — **Ausgang:** entfallen: [ADR-0028](../../adr/0028-referenzinstanz-fuer-tds-8-0.md) ist am 2026-11-13 mit Option B `Accepted` (Entscheidung des Nutzers; Geschichtszeile der ADR), der Inhalt des Slice ist die Empfehlung, für die die DoD geschrieben war. Kein Folge-Slice nötig.
- Ein neuer Digest von `2022-latest` oder `2025-latest` ändert das Verhalten bei `Encrypt=Strict` vor dem Nachzug (Re-Evaluierungs-Trigger der ADR). — **Ausgang:** entfallen: nicht eingetreten; die Digests in `referenztest-dotnet/Dockerfile` (`sha256:cf8f0ab2…`, `sha256:245f6b1c…`) sind im Diff des Slice unverändert (am Dockerfile ändert sich nur ein Kommentar), und `SPEC-045` nennt dieselben Digests (Verifikation, DoD-Punkt 1). Die Messung gilt weiter als Einzelmessung vom 2026-11-01 auf einem Host.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register · `grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

Closure am 2026-11-13 (Planner).

**Belege der Rollen.** Umsetzung `0acbaf39`. Review: [2026-11-13-0acbaf39-review-slice-referenzabgleich-adr-0028-nachzug](../../../reviews/2026-11-13-0acbaf39-review-slice-referenzabgleich-adr-0028-nachzug.md) (0 HIGH, 1 MEDIUM F-1, 3 LOW F-2 bis F-4, 3 INFO F-5 bis F-7). Nacharbeit F-1 bis F-4: `0a518989` (ohne zweiten Review; die Verifikation hat den Commit gezielt gelesen, V-3 des Berichts). Verifikation: [2026-11-13-0a518989-verifikation-slice-referenzabgleich-adr-0028-nachzug](../../../reviews/2026-11-13-0a518989-verifikation-slice-referenzabgleich-adr-0028-nachzug.md) (konform, ohne HIGH und MEDIUM; 3 LOW V-1 bis V-3, 2 INFO V-4, V-5): Liefer-Punkte 1 bis 3 am End-Stand belegt, F-1 bis F-4 aufgehoben. V-1 bis V-3 behoben in `6f2c32bd` (Zeile `SPEC-050` sagt „Rollen der Instanzen nach `SPEC-045`“, DoD-Zeile 1 ordnet Klammersatz und Tabellenzeile getrennt zu, Historie der Spezifikation führt eine Zeile); V-4 und V-5 bleiben stehen (Zeitdokumente, Messprotokoll hält den Befund des Tages). Sensoren: `make docs-check`, `make flaeche`, `make kopplung-zahlen`, `make gates` je Exit 0; `make gates` 40 s, 32 s und 9 s in den drei Läufen (Cache und Stempel; die Änderungen liegen außerhalb der Stage-Eingänge). Kein Gradle-, `clean-build`-, `e2e`- oder `vergleichslauf-d`-Lauf: die Änderung ist Doku und Spec, Dockerfile und Make nur Kommentare (Tabelle in `harness/README.md` §Minimal agent workflow).

**Zuordnung Test | Festlegung.** Nichts an diesem Slice ist „getestet“: kein Test, kein Gate hält die Aussagen über die Referenzinstanzen; `make docs-check` prüft nur die Verweise.

| Aussage | Art | Stelle |
|---|---|---|
| `Encrypt=Strict` scheitert an `2022-latest` unter vier selbst erzeugten Zertifikatsvarianten (Log nennt TLS 1.0 bis 1.2) und gelingt an `2025-latest` mit demselben Zertifikat ohne Zusatzeinstellung (Log nennt TLS 1.0 bis 1.3) | **gemessen**, Einzelmessung 2026-11-01, ein Host, selbst erzeugtes Zertifikat über `tlscert`/`tlskey`, Vertrauensanker beim Client; nicht von diesem Slice wiederholt | `docs/messungen/referenzabgleich.md` B-3, B-4; `SPEC-045` |
| `2025-latest` ist die Referenzinstanz des TDS-8.0-Pfads; beide Instanzen führen alle Läufe | **Festlegung** (Setzung, [ADR-0028](../../adr/0028-referenzinstanz-fuer-tds-8-0.md) Option B), nicht gemessen | `SPEC-045`, `SPEC-050`, Kopf von `tools/mk/vergleichslauf-d.mk` |
| Ob eine andere Konfiguration `Strict` an 2022 erlaubt; Verhalten von 2025 mit dem Standardzertifikat des Servers | **nicht gemessen**, im Wortlaut als offen geführt | `SPEC-045`, B-6 |

**Fortschreibung von Zeitdokumenten.** Über den DoD-Wortlaut (§1 beider Pläne) hinaus liegen vier Änderungen in Dateien unter `done/` (F-6): `slice-referenzabgleich-live` an den Zeilen 112, 123, 164 und 194 (Register-Zeile, F-4) sowie `slice-referenzabgleich-mitschnitt` an Zeile 159. Sie schreiben den **Stand** fort (die alte Lesart „gefolgert, nicht gemessen“ und „Proposed“ ist durch die Messung und die Annahme abgelöst), nicht die Chronik; die Zusätze tragen einen auflösbaren Anker. Andere Zeitdokumente mit der alten Lesart bleiben bewusst stehen (V-5: `done/slice-qa-vergleichslauf-d.md`, `done/slice-abnahme-d-migrate.md`, `done/slice-antwort-tokens-an-messung-angleichen.md`): sie beschreiben den Stand ihrer Zeit.

**Offener Punkt für den Nutzer (Accepted-ADR, nicht angefasst).** [ADR-0007](../../adr/0007-fehler-und-attention.md) (`Accepted`, Zeile ~105) sagt „der Server R bietet TDS 8.0 nicht an“; diese Stelle steht nicht in der `Supersedes`-Liste von [ADR-0028](../../adr/0028-referenzinstanz-fuer-tds-8-0.md). [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) Zeile ~83 („der Grund, TDS 8.0 werde nicht angeboten, ist gefolgert“) steht ebenfalls nicht wörtlich in ihr (ersetzt ist der Satz in Zeile ~69). Beheben ließe sich das nur über eine Folge-ADR mit `Supersedes` (`AGENTS.md` §3.5); in diesem Slice ist keine ADR angelegt oder geändert. Entscheidung beim Nutzer: Folge-ADR oder diese Lesart als überholt stehen lassen. Gezählt in `BEO-TDS/accepted-adr-aussage-ausserhalb-der-supersedes-liste` (1×).

**Restrisiken (benannt).**

- *Die Rolle von `2025-latest` ruht auf einer Einzelmessung* (ein Host, ein selbst erzeugtes Zertifikat, ein Tag); Standardzertifikat und andere Konfiguration an 2022 sind ungemessen.
- *Digests an drei Stellen* (Dockerfile, `SPEC-045`, Messprotokoll; F-5): ein neuer Digest braucht drei Pflegestellen, ein Gate hält sie nicht gleich.
- *Nacharbeit `0a518989` ohne zweiten Review*: gezielt von der Verifikation gelesen (V-3), Doku-Änderung ohne Code.
- *Alte Lesart in Accepted-ADR* (siehe oben).

**Gelernt (Lerneintrag).**

1. *Der Slice-Kopf nennt eine berührte Spec-Stelle nicht* (F-1: `SPEC-050` im Diff, im Kopf nur `SPEC-045`). Die Regel gilt (Reviewer-Skill, „Plan-Tabelle nennt die berührten Dateien nicht“) und griff im Review; gezählt als Evidenz in `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht`, keine neue Beobachtung.
2. *Eine Setzung steht im Wortlaut als Messung* (F-2: „Gemessen ist das …“ für eine Rollenverteilung, die [ADR-0028](../../adr/0028-referenzinstanz-fuer-tds-8-0.md) setzt; die Rollenverteilung war außerdem enger als der Lauf). Die Regel gilt (`AGENTS.md` §3.8); Evidenz in `BEO-TDS/aussagen-ueber-messung-hinaus`.
3. *Die zweite Fundstelle bleibt stehen* (F-4: §8 von `slice-referenzabgleich-live` neben dem nachgezogenen §1 und §7). Die Regel gilt (Reviewer-Skill, Schritt 20c); Evidenz in `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`. Die Verifikation V-5 trennte die bewusst stehen gelassenen Zeitdokumente von den Fundstellen.
4. *Eine Folge-ADR ersetzt Sätze wörtlich, und eine Accepted-ADR nennt dieselbe Aussage an einer Stelle, die in der Liste fehlt* ([ADR-0007](../../adr/0007-fehler-und-attention.md), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md) Zeile ~83): neue Beobachtung, 1×, nichts verkörpert. Die Frage beim Schreiben der ADR wäre: welche Accepted-ADR sagt dieselbe Aussage, nicht nur die, aus der zitiert wird.
5. *Die Historie der Spezifikation fehlte beim Nachzug* (V-3, LOW): eine Praxis der Datei, kein Gate; einmal gesehen, nicht gezählt.

- **Steering-Loop-Eintrag:** keine Regel verkörpert, kein Feld `liegt in` gesetzt; die Klassen 1 bis 3 sind gezählt und ihre Regeln gelten bereits, Klasse 4 ist unterhalb der Schwelle. Eine benannte Spec-Lücke entsteht nicht.
- **Beobachtungs-Register (`../observations/`):** neu `BEO-TDS/accepted-adr-aussage-ausserhalb-der-supersedes-liste` (1×); Evidenz `slice-referenzabgleich-adr-0028-nachzug.md` ergänzt in `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` (Stand bleibt offen), `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen`, `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht` und `BEO-TDS/aussagen-ueber-messung-hinaus`; `BEO-TDS/spec-nachzug-nach-messung-offen`: Stand fortgeschrieben (`SPEC-045` und `SPEC-050`, Zeile Referenzinstanzen, umgesetzt; der Rest bleibt ohne Träger offen), **kein Zähler gesetzt** (keine Evidenz-Datei, wie der Plan es vorsah).
- **Folge-Slices:** keine neuen. [slice-release-entscheidungen](../open/slice-release-entscheidungen.md) bleibt in `open/` und wartet auf Entscheidungen des Nutzers.
- **Risiken aus §6:** beide tragen einen Ausgang (entfallen).
- **Gates:** `make docs-check`, `make flaeche`, `make kopplung-zahlen`, `make gates` Exit 0 (siehe Commit-Bericht der Closure).

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration führt `*` als einzige Sub-Area.

**Vorgelagert — offene Beobachtungen sichten:** `BEO-TDS/spec-nachzug-nach-messung-offen` (Träger dieses Slice für den Teil `SPEC-045`), `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`.

Alle berührten Sub-Areas GF.
