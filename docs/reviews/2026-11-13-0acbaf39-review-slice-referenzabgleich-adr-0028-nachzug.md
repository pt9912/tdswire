# Review-Report: slice-referenzabgleich-adr-0028-nachzug — 2026-11-13

**Review-Art:** Code (Doku und Kommentare) gegen Plan und Verträge.

**Gegenstand:** Diff `603e7486..0acbaf39` (ein Commit, 6 Dateien).

**Skill:** `.harness/skills/reviewer.md` @ 603e7486 · <!-- d-check:ignore (Skill-Pfad als Zitat) -->
**Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-13

**Eingangs-Kontext:**

- Slice `slice-referenzabgleich-adr-0028-nachzug` (§1, §2, §3)
- [ADR-0028](../plan/adr/0028-referenzinstanz-fuer-tds-8-0.md) (Accepted), [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md) (unverändert)
- `docs/messungen/referenzabgleich.md` (B-3 bis B-6, Digests, Bedingungen), `SPEC-045`, `SPEC-050`
- [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität)
- `AGENTS.md` §3.3, §3.4, §3.5, §3.7, §3.8

---

## Findings

| ID | Kategorie | Befund | Quelle | Pfad | Verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| F-1 | MEDIUM | Der Diff setzt `SPEC-050` um (Tabellenzeile nennt jetzt `2022-latest` und `2025-latest`), der Slice-Kopf „Berührte Spec-Stellen“ nennt nur `SPEC-045`. Failure-Szenario: wer Spec-Stellen eines Slice aus dem Kopf liest (Abdeckung, Folge-Review), übersieht die Änderung an `SPEC-050`. | Reviewer-Skill (Plan-Tabelle nennt die berührten Dateien nicht) | `slice-referenzabgleich-adr-0028-nachzug.md` · „**Berührte Spec-Stellen:** SPEC-045“; Diff in `spec/spezifikation.md` · „`SPEC-050` … SQL-Server-Referenzinstanzen in Docker“ | nein — kein Gate gleicht Kopf und Diff ab | Berührte Spec-Stelle fehlt im Slice-Kopf |
| F-2 | LOW | Die Aufteilung „`2022-latest` für alle Schritte außer TDS 8.0, `2025-latest` für den TDS-8.0-Pfad“ (SPEC-045, Kopf von `vergleichslauf-d.mk`) ist enger als der Lauf: `live-2025`, `wire-2025`, `bulk-2025`, `jdbc-2025`, `aufnahme-2025` messen alle Schritte auch an 2025 (`tools/vergleichslauf-d.sh`, Messprotokoll Abschnitt Bedingungen). Der Satz „Gemessen ist das als Einzelmessung …“ bezieht sich außerdem auf eine Rollenverteilung, die eine Setzung der ADR ist, nicht eine Messung. | [ADR-0028](../plan/adr/0028-referenzinstanz-fuer-tds-8-0.md), `AGENTS.md` §3.8 | `spec/spezifikation.md` · „für alle Schritte außer TDS 8.0“; `tools/mk/vergleichslauf-d.mk` · „fuer alle Schritte ausser TDS 8.0“ | ja — Grep auf `LAUF=live-2025` in `tools/mk/vergleichslauf-d.mk` | Rollenverteilung der Instanzen enger als der Lauf |
| F-3 | LOW | B-6 sagt in der Gegenwart, der Plan des Live-Laufs „schreibt wörtlich“ die zwei Sätze (Zeile 40); der Diff ersetzt genau diese Sätze, B-6 zitiert nun abwesenden Text. „seither“ im selben Eintrag erzählt Chronik. | `AGENTS.md` §3.7 | `docs/messungen/referenzabgleich.md` · „schreibt wörtlich: „dass sie TDS 8.0 nicht anbietet““ | nein | Zitat auf geänderte Stelle im Befundtext |
| F-4 | LOW | Zweite Fundstelle: `slice-referenzabgleich-live.md` §8 sagt weiter „TDS 8.0 ist an der Referenzinstanz nicht abgleichbar“, während §7 (Zeile ~123, im Diff) und §1 dieselbe Datei die Lesart ablösen. Es ist der Registerstand zur Planungszeit; ein Grund, warum die Stelle bleibt, steht nirgends. | Reviewer-Skill (Fortschreibung lässt die zweite Fundstelle stehen) | `docs/plan/planning/done/slice-referenzabgleich-live.md` · „Betrifft die Grenze des Laufs: TDS 8.0 ist an der Referenzinstanz nicht abgleichbar“ | ja — Grep „nicht abgleichbar“ | Alte Lesart in zweiter Fundstelle nicht benannt |
| F-5 | INFO | `SPEC-045` trägt die gekürzten Digests `sha256:cf8f0ab2…` und `sha256:245f6b1c…`; ein neuer Digest im Dockerfile braucht dann zwei Pflegestellen mehr (Spezifikation, Messprotokoll). Der Plan verlangt „Digest“ im Satz, daher keine Abweichung. | Maintainability | `spec/spezifikation.md` · „Digest `sha256:245f6b1c…`“ | nein | Digest in zwei Dokumenten |
| F-6 | INFO | Die vier Änderungen in `done/` (Live §1, §7 zweimal, Zeile ~164; Mitschnitt §1 und Zeile ~159) liegen über dem DoD-Wortlaut (§2 nennt nur §1 beider Pläne). Plan-Tabelle §3 deckt beide Dateien als „update, Aussage über die Referenzinstanz“. Je Stelle würde die alte Aussage falsch: „[ADR-0028](../plan/adr/0028-referenzinstanz-fuer-tds-8-0.md) ist Proposed“ (~112, ~159), „Nachzug von … `Proposed`“ (~123), „an 2022 nicht erreichbar“ (~164). Es sind Fortschreibungen des Stands, keine Umschreibung der Chronik; der Zusatz „seit 2026-11-13“ in ~112 und ~159 ist ein Datum im Zustandsfeld, aber mit auflösbarem Anker. Keine Aktion. | `AGENTS.md` §3.3, §3.7 | `slice-referenzabgleich-live.md` · „war beim Lauf Proposed und ist seit 2026-11-13 Accepted“ | nein | Fortschreibung über DoD-Wortlaut |
| F-7 | INFO | Registerdateien, `done/slice-qa-vergleichslauf-d.md` und `done/slice-abnahme-d-migrate.md` behalten die alte Lesart; sie sind Zeitdokumente (Fund, offene Frage) und beschreiben den Stand der Messung, das trägt. [ADR-0007](../plan/adr/0007-fehler-und-attention.md) (Accepted, „Server R bietet TDS 8.0 nicht an“) und [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md) bleiben nach `AGENTS.md` §3.5 unverändert; [ADR-0028](../plan/adr/0028-referenzinstanz-fuer-tds-8-0.md) benennt [ADR-0007](../plan/adr/0007-fehler-und-attention.md) nicht. | `AGENTS.md` §3.5 | `docs/plan/adr/0007-fehler-und-attention.md` · „der Server R bietet TDS 8.0 nicht an“ | nein | Immutable ADR mit alter Lesart |

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| `spec/spezifikation.md` SPEC-045: „TLS 1.0 bis 1.2“ (2022), „TLS 1.0 bis 1.3“ (2025), „vier Zertifikatsvarianten“, Digests, „nicht geklärt“, Standardzertifikat „nicht gemessen“ | geprüft gegen B-3, B-4, [ADR-0028](../plan/adr/0028-referenzinstanz-fuer-tds-8-0.md) Kontext; Zahlen, Digest-Kürzel und Bedingung (Einzelmessung, ein Host, `tlscert`/`tlskey`, Vertrauensanker) im Satz gedeckt, ohne Befund |
| `spec/spezifikation.md` §3.4: Slice, ADR, Welle, Datum in SPEC-045/SPEC-050 | geprüft, ohne Befund (kein Slice, keine ADR, kein Datum) |
| `docs/plan/planning/done/` Live §1 und Mitschnitt §1 (Punkte TDS-8.0) | geprüft, Aussagen durch B-3/B-4 und [ADR-0028](../plan/adr/0028-referenzinstanz-fuer-tds-8-0.md) gedeckt, ohne Befund |
| `referenztest-dotnet/Dockerfile` Kommentar | geprüft, Klasse Zusage/Rang-Zeiger, Indikativ, ADR-ID auflösbar, ohne Befund |
| `tools/mk/vergleichslauf-d.mk` Kopf | geprüft, Indikativ, keine Chronik, Stage-Name existiert; Aussage zur Aufteilung siehe F-2 |
| `docs/plan/adr/` | geprüft, keine Änderung an [ADR-0028](../plan/adr/0028-referenzinstanz-fuer-tds-8-0.md) oder [ADR-0008](../plan/adr/0008-dotnet-kompatibilitaetstests.md) im Diff, ohne Befund |
| `docs/user/`, `harness/`, `README.md`, `referenztest-dotnet/` (Grep nach alten Formulierungen) | geprüft, ohne Treffer |
| DoD-Haken im Slice | geprüft, kein `- [x]` im Diff, ohne Befund |
| Sensoren | `make docs-check` (0 Befunde, 2 s), `make flaeche`, `make kopplung-zahlen` (je ~1 s) grün |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 1 |
| LOW | 3 |
| INFO | 3 |

**Finding-Klassen dieses Laufs:** Berührte Spec-Stelle fehlt im Slice-Kopf · Rollenverteilung der Instanzen enger als der Lauf · Zitat auf geänderte Stelle im Befundtext · Alte Lesart in zweiter Fundstelle nicht benannt

## Verdikt

**Merge-blockierend:** ja, wegen F-1 (MEDIUM), das der Planner im Slice-Kopf schließt; F-2 bis F-4 sind LOW und blockieren nicht.

**Übergabe:** Findings an den Implementer bzw. Planner; Finding-Klassen in die Slice-Closure §7. Der Report ersetzt keine Verifikation.
