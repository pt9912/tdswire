# Verifikationsbericht: slice-einstellungen-in-typen-buendeln (HEAD b6ed24bb)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: [slice-einstellungen-in-typen-buendeln](../plan/planning/in-progress/slice-einstellungen-in-typen-buendeln.md). Maßstab: DoD §2, Plan §3, [TDS-QA-05](../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-FA-09](../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [ADR-0021](../plan/adr/0021-oeffentlicher-integrations-einstieg.md), [ADR-0040](../plan/adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md).
* Stand: Zweig `worktree-agent-a2bd77169a720681a`, HEAD `b6ed24bb`, Basis `main` `013d2954`; Arbeitsbaum sauber. Am Produktivcode nichts geändert (die Mutationen sind zurückgesetzt, `git status` leer). Haken in der DoD: keine gesetzt, und ich setze keine.
* Eingang: Review [2026-11-13-7582e1ef-review-slice-einstellungen-in-typen-buendeln.md](2026-11-13-7582e1ef-review-slice-einstellungen-in-typen-buendeln.md); Nachbesserung `b6ed24bb`.

## Urteil

**Konform, ohne HIGH und MEDIUM.** Alle drei Liefer-Punkte sind belegt, F-1 bis F-5 des Reviews sind behoben oder ausdrücklich als ohne Aktion belassen. Zwei LOW/INFO-Punkte für den Planner stehen unten (V-1, V-2). Offen bleiben die Aufgaben des Planners: Haken, Closure-Notiz (Zuordnung *Test | Festlegung*, Benennung der inkompatiblen Änderung, Steering-Loop), Ausgänge der Risiken in §6, Register.

## Eigene Läufe (Belege)

| Lauf | Ergebnis |
|---|---|
| `git diff 78ef2f58..b6ed24bb --stat` | 4 Dateien: Plan, `docs/user/einbettung-und-betrieb.md`, `spec/spezifikation.md`, `EinstellungenEmbeddingTest.kt` (nur KDoc, 2 Zeilen); kein Code, kein Testcode |
| `make gates` | Exit 0, 18 s: Docker-Cache (88 Schichten `CACHED`, gleicher Baum wie beim Implementer), `stempel-gruppe` über den Stempel übersprungen; `flaeche` mit Selbsttest grün |
| `make flaeche` einzeln | Exit 0 |
| `make clean-build` | Exit 0, 571 s (`--no-cache`); 1397 Testfälle `PASSED`, 0 `FAILED` |
| `make e2e` | Exit 0, 8 Stufen grün, 328 s (Stufe 1 70 s, 2 87 s, 3 47 s, 4 42 s, 5 15 s, 6 18 s, 7 35 s, 8 14 s) |
| Mutationen (c), eine Docker-Stage mit Filter auf drei Testklassen und `--continue`, alle drei Mutationen zugleich | 69 s; je Mutation die erwartete Klasse rot, siehe (c) |
| `make docs-check` | 1395 Dateien, 0 Befunde (vor dem Commit) |

Gesamtlaufzeit rund 25 Minuten; größte Posten: `make clean-build` 571 s, `make e2e` 328 s, Lesen von Diff, Spezifikation und `docs/user/`. Keine künstliche Last; die Läufe liefen nacheinander (Sensoren als ein Hintergrundskript, Ergebnis aus den Logs gelesen).

## DoD je Liefer-Punkt

1. **Typen und Schnitt: erfüllt.** `RequestSettings(timeouts, limits, observation)` mit `RequestTimeouts` (3 Fristen), `RequestLimits` (2 Grenzen), `ObservationSettings` (1 Feld) entspricht Plan §3; jede Gruppe und jedes Feld hat die Voreinstellung aus `ConfigDefaults`; die Aufrufe in Haupt-, Test-, Testserver- und Integrator-Code sind benannt; `ConfigLoader` und `EnvironmentConfig` sind angeglichen, Umgebungsnamen unverändert. Die drei neuen Namen stehen in der Liste von `SPEC-059` (Zeile 2152), `make flaeche` ist grün.
2. **Java-Lesart: erfüllt, in ihrer Grenze.** `@JvmOverloads` an allen vier Typen (Wahl in Plan §3 begründet); `EinstellungenEmbeddingTest` belegt Kotlin-Aufruf und die Java-Konstruktoren per Reflection (`getConstructor`), nur über öffentliche Namen. Die inkompatible Änderung ist in Plan §1 und jetzt auch in Spezifikation (Zeile Prüfung, Änderungsprotokoll 2026-11-13) und `docs/user` benannt.
3. **Belege und Nachzug: erfüllt im Verifier-Umfang.** Alle Tests grün (1397). Mutationen: siehe (c). Spezifikation und `docs/user/` gelesen: (e). Die Zuordnung *Test | Festlegung* in der Closure-Notiz ist noch nicht geschrieben (§7 „Wird bei der Closure gefüllt“); das ist Aufgabe des Planners.

## (a) Typschnitt und öffentliche Fläche

Der Diff `013d2954..HEAD` gegen `src/main` fügt genau vier öffentliche Deklarationen hinzu (`RequestTimeouts`, `RequestLimits`, `ObservationSettings`, `RequestSettings` mit `@JvmOverloads`); drei Namen sind neu, `RequestSettings` besteht fort. Weitere öffentliche Namen entstehen nicht. Die Mitglieder (Reihenfolge der Felder) hält `make flaeche` nicht; sie hält `RequestSettingsTest` mit sechs verschiedenen Werten (BEO `oeffentliche-flaeche-mitglieder-ohne-sensor`, Plan §6 bleibt bis zur Closure offen).

## (b) Voreinstellungen, Schlüssel, ADR-0040

`effectiveSettings()` bildet die zwölf Schlüssel unverändert ab (nur die Quelle der sechs Felder wechselt auf `requests.timeouts`, `.limits`, `.observation`; Diff gelesen). Bereiche in `ConfigValidation` unverändert, nur der Zugriff. Voreinstellungen 30 s, 5 s, 10 s, 1000, 16 MiB, 60 s stehen gleich in Spezifikation §3 (`SPEC-007`, `-009`, `-033`, `-047`, `-061`, `-062`), Prüfzeile `SPEC-059`, `docs/user/referenz-konfiguration.md` und `ConfigDefaults`. `EffectiveSettings.kt` ist nicht im Diff.

## (c) Mutationen (selbst gefahren)

Alle drei gleichzeitig angewandt, Lauf mit `--tests` auf `RequestSettingsTest`, `AbbruchWiringTest` (Task `test`) und `EinstellungenEmbeddingTest` (Task `integratorTest`), `--continue`:

| Mutation | Ergebnis |
|---|---|
| Tausch `maxMessageBytes` und `metricsIntervalSeconds` in `effectiveSettings()` | rot: `RequestSettingsTest > Jeder Wert der Gruppen steht unter seinem Schluessel` |
| Tausch `queryTimeout` und `cancelTimeout` in `ConnectionWiring` | rot: `AbbruchWiringTest > QUERY_TIMEOUT aus der Umgebung endet in TDS_TIMEOUT bei offener Verbindung` |
| `@JvmOverloads` an `RequestTimeouts` entfernt | rot: `EinstellungenEmbeddingTest > ausJavaSindDieKonstruktorenMitWenigerArgumentenAufrufbar` und `> eineAusJavaGebauteGruppeWirdBeimStartGeprueft` |

Grenze: Die drei Mutationen liefen zusammen, nicht einzeln; die roten Tests gehören je zu genau einer Mutation (verschiedene Klassen), eine Querwirkung ist nicht aufgetreten. Der Task `:testserver:test` fiel im Lauf ebenfalls aus, weil der Filter dort keinen Test fand (Artefakt des Filters, keine Wirkung der Mutation). Die Quelle ist mit `git checkout -- src` zurückgesetzt; `git status` leer. Nicht gefahren: die übrigen Mutationen des Implementers (Pflicht des Implementers).

## (d) Java-Lesart

Belegt ist die Signatur der erzeugten Konstruktoren per Reflection, kein Java-Compiler (das Source-Set hat keinen Java-Quelltext, Plan §3). Spezifikation: „aus Java sind die Konstruktoren mit weniger Argumenten aufrufbar, Test: `EinstellungenEmbeddingTest`“; `docs/user`: `new RequestTimeouts(10)` als Beispiel, Beleg genannt. Die Aussagen gehen nicht über die Signaturen hinaus; der Quelltext-Beispiel-Aufruf selbst ist nicht übersetzt (INFO V-2).

## (e) `docs/user/` komplett auf alte Aussagen gelesen

Alle fünf Dateien auf `RequestSettings`, `requests.`, die sechs Feldnamen, Konstruktor, `copy`, `componentN`, „positional“, „nach Position“, „sechs“ und Voreinstellungen durchsucht und die betroffenen Abschnitte (Aufgabe 6 und 10 in `einbettung-und-betrieb.md`, Fehlertabelle, Konfigurationstabelle in `referenz-konfiguration.md`) gelesen. Keine alte Lesart (kein Zugriff `requests.queryTimeoutSeconds` usw., kein Ganzzahl-Konstruktor, nur die Satzformen „Ein Konstruktor von `RequestSettings` mit Ganzzahlen besteht nicht“). `referenz-ereignisse.md` nennt `metricsIntervalSeconds` als Feld des Ereignisses (Schlüssel von `EffectiveSettings`, unverändert) und `testserver-manuell.md` nur Umgebungsnamen; beide stimmen. Kein Befund.

## Review-Findings F-1 bis F-5

| ID | Stand | Beleg |
|---|---|---|
| F-1 (Plan-Tabelle, Spec-Stellen) | behoben | Plan §3 nennt `Bootstrap.kt`, `ConnectionWiring.kt`, `PreparedHandles.kt`, `RequestLoop.kt`, `AbbruchWiringTest`, `TdsServerLifecycleTest` u. a.; „Berührte Spec-Stellen“ nennt `SPEC-058` und `SPEC-061` |
| F-2 (Wächter-KDoc breiter als der Test) | behoben | KDoc nennt jetzt die Gruppe `limits`, die `timeouts`-Prüfung hält `ConfigValidationTest`; nur KDoc geändert |
| F-3 (Änderungsprotokoll) | behoben | Zeile 2026-11-13 in der Spezifikation; die beiden älteren Zeilen (2026-11-06, 2026-11-07) tragen wieder den Namen des damaligen Standes (`RequestSettings.maxPreparedHandles`, `RequestSettings.metricsIntervalSeconds`) |
| F-4 (Flächenänderung nur im Plan) | behoben | Spezifikation (Zeile Prüfung, Protokoll) und `docs/user/einbettung-und-betrieb.md` nennen den entfallenen Ganzzahl-Konstruktor sowie `copy`/`componentN` |
| F-5 (Aufzählung Einbettungstests) | INFO ohne Aktion | Aufzählung ist mit „Dazu gehören“ eingeleitet; `SPEC-059` Zeile 2153 und `docs/user` nennen den Test |

## Findings des Verifiers

| ID | Kategorie | Befund | Pfad |
|---|---|---|---|
| V-1 | LOW | Die Zuordnung *Test | Festlegung*, die Benennung der inkompatiblen Änderung und der Steering-Loop-Lerneintrag fehlen noch in §7; §6 trägt noch keine Ausgänge. Closure-Aufgabe des Planners, kein Mangel des Stands. | `docs/plan/planning/in-progress/slice-einstellungen-in-typen-buendeln.md` §6, §7 |
| V-2 | INFO | Die Java-Aufrufbeispiele (`new RequestTimeouts(10)`) sind nicht übersetzt; belegt ist die Konstruktor-Signatur per Reflection. Aussage nicht breiter als der Beleg; ohne Aktion. | `docs/user/einbettung-und-betrieb.md` Aufgabe 6 |

Eine Spec-Lücke oder DoD-Verletzung, die Tests und Review nicht sähen, fand ich nicht.
