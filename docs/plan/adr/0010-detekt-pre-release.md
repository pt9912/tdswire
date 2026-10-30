# ADR-0010: detekt 2.0 als Gate-Werkzeug (Pre-Release)

**Status:** Accepted

**Datum:** 2026-10-29

**Autor:** pt9912

**Bezug:** [TDS-QA-05](../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)

**Schärft:** [SPEC-041](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge)

**Regeln:** Baseline-Regelwerk `modul-04-adrs.md`
§Ziel-Form: ADR (MADR).

---

## Kontext

Das Static-Analysis-Gate (`SPEC-041`) prüft die Schichtgrenzen der Architektur und das Format des Quelltexts. Es stützt sich auf drei Fähigkeiten von detekt: Importregeln je Package (`ForbiddenImport`), Aufrufregeln mit Typauflösung (`ForbiddenMethodCall`, nur über `detektMain` und `detektTest`) und den ktlint-Regelsatz als Wrapper.

Gepinnt ist `2.0.0-alpha.6` (`gradle/libs.versions.toml`), ein Pre-Release. Die Version wurde mit dem Gradle-Gerüst übernommen (Commit `5d2236d`); die Wahl steht in einem Kommentar in `build.gradle.kts`, der auf diese ADR verweist.

Gemessen (2026-10-30, detekt `2.0.0-alpha.6`, Docker-Stage `detekt`, je eine Variante von `config/detekt/detekt.yml`): Rot wird der Lauf durch die **Konfigurationsvalidierung** (`config: validation: true`, Standard), nicht durch `warningsAsErrors`. Die Validierung meldet `Property '<pfad>' is misspelled or does not exist. Allowed properties: [...]`, danach `Run failed with 1 invalid config property.`, Exit-Code 1. Sie erfasst einen unbekannten oder umbenannten Top-Level-Schlüssel, eine unbekannte oder umbenannte Regel in den Regelsätzen `style` und `complexity`, eine unbekannte oder umbenannte Option an einer Regel mit festem Namen (`ForbiddenMethodCall`: `methods`; `CognitiveComplexMethod`) und jeden unbekannten oder umbenannten Schlüssel im `config:`-Block. Sie erfasst **nicht**: Optionen an den Regeln mit Namenszusatz (`ForbiddenImport/<name>`: eine unbekannte Option und eine umbenannte `forbiddenImports` blieben bei Exit-Code 0 ohne Ausgabe) und Regeln und Optionen im Regelsatz `ktlint` (eine unbekannte Regel und eine unbekannte Option blieben bei Exit-Code 0). Mit `warningsAsErrors: false` blieb die Validierung rot, mit `validation: false` wurde derselbe Fehler grün. Eine umbenannte Option einer `ForbiddenImport/<name>`-Regel fängt erst `make detekt-selbsttest` (Befund `Regel ForbiddenImport/config meldet 0 von 6 verbotenen Importen`, Exit-Code 1); für den `ktlint`-Block prüft der Selbsttest nichts.

Annahmen: Der Pin ist exakt, `gradle.lockfile` und `gradle/verification-metadata.xml` fixieren Version und Prüfsummen. Die Konfiguration kennt keine Baseline und senkt keinen Standard. Fällt eine dieser Annahmen, ist die Entscheidung neu zu prüfen.

## Entscheidung

Wir wählen **Option B**: detekt `2.0.0-alpha.6` bleibt das Gate-Werkzeug, mit exaktem Pin, Lock und Prüfsummen und `warningsAsErrors: true`. Der Regelumfang ist der der Standardkonfiguration dieser Version, abweichend davon `CognitiveComplexMethod`, `ComplexInterface` und `NotImplementedDeclaration` aktiv (`config/detekt/detekt.yml`), ergänzt um den ktlint-Wrapper gleicher Version, die Importregeln und Technik-Regeln der Schichten und das Suppression-Verbot (`SPEC-041`).

Ein Pre-Release trägt das Risiko, dass sich Optionen zwischen Alphas umbenennen oder entfallen. Gemessen ist: die Konfigurationsvalidierung von detekt (Standard `validation: true`) macht eine umbenannte oder entfernte Option an einer Regel mit festem Namen, eine unbekannte Regel in `style` und `complexity` und einen unbekannten Top-Level- oder `config:`-Schlüssel zu einem roten Lauf; `warningsAsErrors: false` ändert an dieser Validierung nichts (der Lauf blieb rot), ein Einfluss von `warningsAsErrors` auf diese Fälle ist nicht gemessen. Ungeprüft von der Validierung bleiben Optionen der Regeln `ForbiddenImport/<name>` und der gesamte `ktlint`-Block; bei den Importregeln fängt der Selbsttest eine umbenannte Option, beim `ktlint`-Block fängt sie nichts (siehe Kontext).

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| A — detekt 1.x (Stabil-Release) | Keine Pre-Release-Risiken, breit eingesetzt | Gemessen (detekt `1.23.8` mit Kotlin `2.4.10` in Docker; der Messbeleg liegt nicht im Repo, die Messung ist hier nicht nachprüfbar): `detekt` und `detektMain` laufen ohne Fehler. Offen bleibt, ob `ForbiddenMethodCall` unter Kotlin 2.4 meldet: auch die Kontrolle mit Kotlin 2.0.21 meldete den Testaufruf nicht, die Messung ist damit unentschieden. Die Gradle-API unterscheidet sich nach Kenntnis der 1.x-API (Plugin-ID `io.gitlab.arturbosch.detekt`, Berichtsformat `checkstyle` entfällt in 1.x; abgeleitet, nicht gemessen), `build.gradle.kts` und `detekt.yml` wären umzuschreiben (abgeleitet) |
| **B — detekt `2.0.0-alpha.6`, exakt gepinnt** | Läuft nachweislich mit dem Gerüst; Selbsttest belegt die Importregeln, die Aufrufregeln und das Suppression-Verbot | Pre-Release: Optionen und Regelsätze können sich zwischen Alphas ändern; ein Wechsel kostet Nacharbeit an `detekt.yml` |
| C — kein detekt, Schichtgrenzen nur per Review | Keine Werkzeugabhängigkeit | Importgrenzen und das Suppression-Verbot hängen an Aufmerksamkeit; widerspricht `TDS-QA-05` als maschinell prüfbarer Eigenschaft |

## Konsequenzen

- **Positiv:** Das Gate läuft heute, die Schichtregeln und Technik-Regeln sind belegt. Die Wahl ist an einer Stelle begründet und auffindbar.
- **Negativ / Risiko:** Jeder Versionswechsel kann `detekt.yml` brechen; der ktlint-Block ist gleich dem Standard der gepinnten Version und gehört nach einem Pin-Wechsel neu erzeugt (Kommentar in `config/detekt/detekt.yml`).
- **Folgepflicht:** `SPEC-041` bleibt unverändert; die Entscheidung schärft sie nur. `build.gradle.kts` nennt diese ADR bereits.
- **Autor-Bestätigung (2026-10-30):** Der Autor bestätigte die ADR mit „adr-0010 bestätigt“. Ein über die Übernahme des Gerüsts hinausgehendes Motiv (Funktionsumfang, Kotlin-Verträglichkeit) ist weder genannt noch belegt.
- **Offen, Grenzen der Messung:** Nur `2.0.0-alpha.6` ist gemessen, keine andere Alpha. Nicht gemessen sind die Wirkung von `warningsAsErrors: true` auf andere Warnungen (etwa veraltete Properties) und die Abdeckung des `ktlint`-Blocks nach einem Pin-Wechsel. Gegenmaßnahmen für die ungedeckte Lücke, nicht umgesetzt und keine Entscheidung: (1) ein Selbsttest-Fall, der die Schlüssel des `ktlint`-Blocks gegen die Regelliste des Wrapper-Jars hält; (2) ein Selbsttest-Fall je `ForbiddenImport/<name>`-Option über die vorhandene Prüfung hinaus; (3) nach jedem Pin-Wechsel den `ktlint`-Block neu erzeugen, wie der Kommentar in `config/detekt/detekt.yml` es schon verlangt, und diese Pflicht an den Re-Evaluierungs-Trigger binden.
- **Offen:** Die Messung von Option A ist unvollständig (`ForbiddenMethodCall` unter Typauflösung); sie begründet nicht, dass Option A scheitert.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| detekt mit Selbsttest | Jede Regel meldet ihre verbotenen Fälle, die erlaubten Testdaten nicht; eine umbenannte oder entfernte Option macht den Lauf rot, soweit die Konfigurationsvalidierung sie erfasst (`make detekt`); Importregeln `ForbiddenImport/<name>` fängt `make detekt-selbsttest`; der `ktlint`-Block ist nicht abgedeckt | `make detekt`, `make detekt-selbsttest` |

## Re-Evaluierungs-Trigger

Erscheint ein Stabil-Release von detekt 2.x, oder bricht ein Versionswechsel innerhalb der Alpha-Reihe `detekt.yml`.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-10-29 | Proposed | — |
| 2026-10-30 | Autor bestätigt Option B ("adr-0010 bestätigt"); Review ohne HIGH, MEDIUM eingearbeitet | [Review](../../reviews/2026-10-30-af645e1-adr-0010.md) |
| 2026-10-30 | Aussage zu `warningsAsErrors` durch Messung ersetzt: Konfigurationsvalidierung statt `warningsAsErrors`, Lücken bei `ForbiddenImport/<name>` und `ktlint`; Status unverändert Proposed | — |
| 2026-10-30 | Nach Verifikation nachgeführt (Status weiter Proposed): Entscheidungsabschnitt zu `warningsAsErrors` an den Kontext angeglichen (`warningsAsErrors: false` ändert die Validierung nicht), Aussage zur 1.x-API als abgeleitet gekennzeichnet | [Verifikation](../../reviews/2026-10-30-46be900-verifikation-adr-0004-0006-0007-0010-0011.md) |
| 2026-10-30 | Accepted – nach Review und Verifikation (Option B bestätigt: Autor, 2026-10-30); Probelauf und Verifikation bestätigen die Aussagen zur Konfigurationsvalidierung | [Verifikation](../../reviews/2026-10-30-46be900-verifikation-adr-0004-0006-0007-0010-0011.md) |
