# Review-Report (Re-Review): slice-handle-freigabe-und-grenze-bauen — 2026-11-06

**Review-Art:** Code und Doku, Nachbesserung (Re-Review von F-1 bis F-8 und V-1 bis V-6)

**Gegenstand:** Commits `81a9c47c` (Code, Tests), `89dff65b` (Spec, Doku, Register), `0d6d7e44` (`Messung.java`), Verifikationsbericht `101ecc7e`; Zweig `slice-handle-freigabe-und-grenze-bauen`

**Vorgänger:** [Erster Review dd4600eb](2026-11-06-dd4600eb-review-slice-handle-freigabe-und-grenze-bauen.md), [Verifikation 0d6d7e44](2026-11-06-0d6d7e44-verifikation-slice-handle-freigabe-und-grenze-bauen.md)

**Skill:** `.harness/skills/reviewer.md` · **Modell:** claude-sonnet-5-5 · **Datum:** 2026-11-06

**Eingangs-Kontext:** [ADR-0036](../plan/adr/0036-handle-freigabe-und-grenze-je-sitzung.md), [ADR-0037](../plan/adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md), [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-02](../../spec/lastenheft.md#tds-qa-02--stabilität); `AGENTS.md` §3.7, §3.8; SPEC-030/045/060/061.

## Lauf-Belege (selbst gefahren)

- `make docs-check`: 1022 Dateien, 0 Befunde. `make flaeche`: Exit 0.
- Mutation `.take(MAX_REPORTED)` -> `.takeLast(MAX_REPORTED)` in `UnprepareBatch.answer`, `make test`: rot, ausschließlich `UnprepareDispatchTest > Sehr viele unbekannte Nummern ergeben eine begrenzte Antwort und ein begrenztes Log` (erwartete die Handles 2.. mit der Reihenfolge des Textes). Danach zurückgesetzt, Arbeitsbaum sauber. Gelesen, nicht gefahren: `numbers.take(100).filterNot {…}` ließe das gehaltene Handle 1 am Textende gehalten; der Test prüft `errors(2).single()` und wäre rot.

## Prüfung der Behebungen

| Finding | Urteil | Begründung |
|---|---|---|
| F-1 Grenze 100 | hält | `filterNot { release }` läuft über alle Nummern (eager `List`), erst danach `take`: Verarbeitung vollständig, Reihenfolge des Textes, Log und Antwort gleich lang (`handles = unknown`). DONE-Status `ERROR` und `limit + 1` Token der ANTWORT werden im Test geprüft (`session.tokens(1)`), nicht nur das Log. Mischung gehalten/unbekannt: das gehaltene Handle am Ende ist frei. Der bestehende Mischtest bleibt. |
| F-2 M-6-Träger | teilweise (R-3) | Register-Eintrag mit Beobachtung, Evidenz, Stand; `SPEC-057` unverändert. |
| F-3 Plan-Tabelle | hält | Tabelle und Kopf nennen die im Erst-Review aufgeführten Dateien und Stellen (Rest: V-4, `docs/plan/spec-abdeckung.md`, noch offen, LOW). |
| F-4 `@handle` im KDoc | hält | Grep: keine Fundstelle mehr. |
| F-5 Flag-Feld | hält, Träger tragfähig | A-19 im Messprotokoll, A-16 berichtigt, `SPEC-030` verweist auf A-19, Folge-Slice `slice-antwort-tokens-an-messung-angleichen` (in `open/`, überlebt die Closure) führt den Punkt im Liefer-Punkt 2. Kein Sensor (V-5, INFO, offen ausgesprochen). |
| F-6 `@spec SPEC-061` | hält | Tags an `HandleLimitEmbeddingTest` (zwei Tests); `ConfigLoaderTest` +1 Zeile. |
| F-7 Kopplung | hält | Kommentar in `Messung.java` nennt Tabelle, Spalte, Quelle (`sim.yaml` existiert), Exit 1 (`System.exit(ok ? 0 : 1)` belegt), Nicht-Gate. |

## Findings

| # | Kat. | Quelle | Pfad · Anker | Befund | verifizierbar | Klasse |
|---|---|---|---|---|---|---|
| R-1 | MEDIUM | AGENTS.md §3.8, Reviewer-Skill (zweite Fundstelle) | `docs/user/einbettung-und-betrieb.md` · "ergibt bei der Freigabe den Fehler 8179 wie am echten SQL Server (`SPEC-060`)" und `docs/plan/kompatibilitaetsmatrix.md` · "jede Nummer, die die Sitzung nicht hält (nie vergeben, doppelt freigegeben, vor `RESETCONNECTION`), ergibt ERROR 8179" | Die Obergrenze 100 steht in `SPEC-030`, `SPEC-060`, Slice §6 und KDoc, aber nicht in der Nutzer-Doku und nicht in der Matrix-Zeile "Freigabe der Handles". Ein Nutzer liest "wie am echten SQL Server" und "jede Nummer"; ab der 101. unbekannten Nummer eines Batches fehlt der Fehler (V-2, zweiter Teil, nach dem Verifikationsbericht nicht behoben: `89dff65b` berührt beide Dateien nicht). Die Matrix zitiert den neuen Test nicht. | ja (Grep "100" in beiden Dateien) | Fortschreibung lässt die zweite Fundstelle stehen |
| R-2 | MEDIUM | AGENTS.md §3.6/§3.8 ("Spec ist Zielbild") | `docs/plan/planning/done/slice-handle-freigabe-und-grenze-bauen.md` · "weiter offen bis zur Closure (Messung am echten Server nicht vorgesehen)" | Die Abweichung "höchstens 100 gemeldete Nummern" gegen den echten Server (M-7: jede Nummer ein Fehler) hat als einzigen Träger Slice §6, das mit der Closure nach `done/` wandert und dort archiviert wird. `SPEC-060` bleibt als Setzung, aber ohne Auflösungs-Trigger: weder Register-Eintrag noch Ausnahmezeile (A-xx) noch Folge-Slice führen die Abweichung als Schuld. Nach der Closure ist sie sichtbar nur als Setzung im Text, nicht als zu schließende Abweichung. Offen wie V-2 (erster Teil). | ja (Grep im Register und in `referenzabgleich.md`) | Zielbild-Abweichung ohne Träger über die Closure hinaus |
| R-3 | LOW | Maintainability / `BEO-TDS/offene-punkte-ohne-traeger` | `docs/plan/planning/observations/BEO-TDS/sp-execute-unbekanntes-handle-spec-057-widerspricht-messung/state.md` · "Träger der Entscheidung: [slice-handle-freigabe-und-grenze-bauen]" | Der Register-Eintrag überlebt die Closure, sein Stand nennt aber den Slice §6 (wandert nach `done/`) und einen Folge-Slice "noch ohne Kennung" als Träger. Bis zur Closure muss der Stand einen dann gültigen Ort nennen (V-3, offen). Der Eintrag selbst und `SPEC-057` mit Messvermerk sind sichtbar; nichts ist still gelöst. | nein | Träger zirkulär |
| R-4 | INFO | Reviewer-Skill (Haken vor Verifikation) | `docs/plan/planning/done/slice-handle-freigabe-und-grenze-bauen.md` · §2 DoD | Haken stehen seit `f2a38e70` (V-1). Gehört dem Verifier/Planner; in diesem Diff keine neuen Haken (kein `- [x]` in `81a9c47c`, `89dff65b`, `0d6d7e44`). | ja | Haken vor der Verifikation (Instanz bereits im Register geführt) |
| R-5 | INFO | AGENTS.md §3.8 | `src/main/kotlin/.../dispatch/UnprepareBatch.kt` · "`SPEC-060`, Setzung, nicht gemessen" | Geprüft: KDoc, SPEC-060, SPEC-030 und Test nennen die Grenze einheitlich als Setzung, ohne Messbehauptung; Kommentare tragen Zusage/Kopplung (§3.7), keine Chronik. Test belegt Antwort (ERROR-Liste, Tokenzahl, DONE-Status), Log und Freigabe. Kein Finding. | nein | — |

Hinweis zur Bewertung des Trägers (Auftrag): Flag-Feld A-19 und M-6 haben einen Träger, der die Closure überlebt (Folge-Slice in `open/`, Register-Eintrag; M-6 mit der Schwäche R-3). Die Obergrenze 100 hat ihn nicht (R-2).

## Negativbefund (geprüft, ohne weiteren Befund)

- `src/main/kotlin/.../protocol/dispatch/`: geprüft, ohne Befund über die Behebung F-1 hinaus (keine Suppression, Schichten unverändert).
- `src/test/`, `src/integrator/`, `testserver/`: geprüft, ohne Befund (Tags, KDoc ohne `@handle`).
- `tools/treiber-handles/`: geprüft, ohne Befund.
- `spec/spezifikation.md` (SPEC-030, 045-Verweis, 060, 061): geprüft, ohne Befund (Aussage <= Beleg, Setzung gekennzeichnet).
- `docs/messungen/referenzabgleich.md` (A-16, A-19): geprüft, ohne Befund.
- `docs/plan/planning/` (Register, `in-progress/slice-antwort-tokens-an-messung-angleichen.md`): geprüft, Befund R-2, R-3.
- `docs/user/`, `docs/plan/kompatibilitaetsmatrix.md`: Befund R-1.
- `harness/`, ADRs: unverändert, geprüft, ohne Befund.
