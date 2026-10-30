# ADR-0010: detekt 2.0 als Gate-Werkzeug (Pre-Release)

**Status:** Proposed

**Datum:** 2026-10-29

**Autor:** pt9912

**Bezug:** [TDS-QA-05](../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)

**Schärft:** [SPEC-041](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge)

**Regeln:** Baseline-Regelwerk `modul-04-adrs.md`
§Ziel-Form: ADR (MADR).

---

## Kontext

Das Static-Analysis-Gate (`SPEC-041`) prüft die Schichtgrenzen der Architektur und das Format des Quelltexts. Es stützt sich auf drei Fähigkeiten von detekt: Importregeln je Package (`ForbiddenImport`), Aufrufregeln mit Typauflösung (`ForbiddenMethodCall`, nur über `detektMain` und `detektTest`) und den ktlint-Regelsatz als Wrapper.

Gepinnt ist `2.0.0-alpha.6` (`gradle/libs.versions.toml`), ein Pre-Release. Die Version wurde mit dem Gradle-Gerüst aus dem Schwesterprojekt pgwire übernommen (Commit-Nachricht von `5d2236d`: „Gradle/Kotlin/detekt wie in pgwire“); die Wahl stand bisher nur in einem Kommentar in `build.gradle.kts`.

Annahmen: Der Pin ist exakt, `gradle.lockfile` und `gradle/verification-metadata.xml` fixieren Version und Prüfsummen. Die Konfiguration kennt keine Baseline und senkt keinen Standard. Fällt eine dieser Annahmen, ist die Entscheidung neu zu prüfen.

## Entscheidung

Wir wählen **Option B**: detekt `2.0.0-alpha.6` bleibt das Gate-Werkzeug, mit exaktem Pin, Lock und Prüfsummen und `warningsAsErrors: true`. Der Regelumfang ist der der Standardkonfiguration dieser Version, ergänzt um den ktlint-Wrapper gleicher Version, die Importregeln und Technik-Regeln der Schichten und das Suppression-Verbot (`SPEC-041`).

Ein Pre-Release trägt das Risiko, dass sich Optionen zwischen Alphas umbenennen oder entfallen. `warningsAsErrors` macht das zu einem roten Lauf statt zu einer still wirkungslosen Regel; der Selbsttest belegt, dass jede Regel meldet.

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| A — detekt 1.x (Stabil-Release) | Keine Pre-Release-Risiken, breit eingesetzt | Gemessen (detekt `1.23.8` mit Kotlin `2.4.10` in Docker): `detekt` und `detektMain` laufen ohne Fehler. Offen bleibt, ob `ForbiddenMethodCall` unter Kotlin 2.4 meldet: auch die Kontrolle mit Kotlin 2.0.21 meldete den Testaufruf nicht, die Messung ist damit unentschieden. Die Gradle-API unterscheidet sich (Plugin-ID `io.gitlab.arturbosch.detekt`, Berichtsformat `checkstyle` entfällt in 1.x), `build.gradle.kts` und `detekt.yml` sind umzuschreiben |
| **B — detekt `2.0.0-alpha.6`, exakt gepinnt** | Läuft nachweislich mit dem Gerüst; Selbsttest belegt alle Regeln | Pre-Release: Optionen und Regelsätze können sich zwischen Alphas ändern; ein Wechsel kostet Nacharbeit an `detekt.yml` |
| C — kein detekt, Schichtgrenzen nur per Review | Keine Werkzeugabhängigkeit | Importgrenzen und das Suppression-Verbot hängen an Aufmerksamkeit; widerspricht `TDS-QA-05` als maschinell prüfbarer Eigenschaft |

## Konsequenzen

- **Positiv:** Das Gate läuft heute, die Schichtregeln und Technik-Regeln sind belegt. Die Wahl ist an einer Stelle begründet und auffindbar.
- **Negativ / Risiko:** Jeder Versionswechsel kann `detekt.yml` brechen; der ktlint-Block ist gleich dem Standard der gepinnten Version und gehört nach einem Pin-Wechsel neu erzeugt (Kommentar in `config/detekt/detekt.yml`).
- **Folgepflicht:** `build.gradle.kts` verweist auf diese ADR statt auf „die zugehörige ADR". Bei Annahme `SPEC-041` unverändert lassen; die Entscheidung schärft sie nur.
- **Bestätigt durch den Autor (2026-10-30):** Option B ist die gewollte Wahl. Ein über die Übernahme des Gerüsts hinausgehendes Motiv von pgwire (Funktionsumfang, Kotlin-Verträglichkeit) ist weder genannt noch belegt.
- **Offen:** Die Messung von Option A ist unvollständig (`ForbiddenMethodCall` unter Typauflösung); sie begründet nicht, dass Option A scheitert.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| detekt mit Selbsttest | Jede Regel meldet ihre verbotenen Fälle, die erlaubten Testdaten nicht; eine umbenannte oder entfernte Option macht den Lauf rot (`warningsAsErrors`) | `make detekt`, `make detekt-selbsttest` |

## Re-Evaluierungs-Trigger

Erscheint ein Stabil-Release von detekt 2.x, oder bricht ein Versionswechsel innerhalb der Alpha-Reihe `detekt.yml`.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-10-29 | Proposed | — |
