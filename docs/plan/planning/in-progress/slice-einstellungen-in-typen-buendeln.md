# Slice slice-einstellungen-in-typen-buendeln: Einstellungen der ServerConfig in benannten Typen bündeln

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-betrieb-diagnose

**Bezug:** [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) (`Accepted`, Zusage vor 1.0 eng, Liste der Namen in `SPEC-059`), [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4 (jeder neue öffentliche Name steht in einer Liste), [ADR-0040](../../adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md) (`EffectiveSettings`, zwölf Schlüssel)

**Berührte Spec-Stellen:** SPEC-059 · SPEC-062 · SPEC-007 · SPEC-009 · SPEC-033 · SPEC-047 · SPEC-052 · spezifikation.md §3 (Defaults und Konstanten) und §5 (Abstand der Schnappschüsse)

**Verantwortlich:** pt9912 (Start am 2026-11-13 angeordnet).

**Autor:** Planner-Agent, Auftrag des Nutzers 2026-11-07. **Datum:** 2026-11-07.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar.

**Ziel:** Die Einstellungsgruppen von `ServerConfig` sind benannte, gruppierte Typen mit Voreinstellungen statt langer Listen gleichartiger, nach Position vertauschbarer Parameter. Anlass: `RequestSettings` hat sechs Ganzzahl- und Dauer-Parameter (`queryTimeoutSeconds`, `cancelTimeoutSeconds`, `shutdownGraceSeconds`, `maxPreparedHandles`, `maxMessageBytes`, `metricsIntervalSeconds`); beim Zusammenführen zweier Zweige war das Vertauschen von `maxMessageBytes` und `metricsIntervalSeconds` eine reale Gefahr (Nutzerentscheid 2026-11-07: Einstellungen in einem Typ bündeln, Register `BEO-TDS/sechster-requestsettings-parameter-ohne-entscheidung`). Der Schnitt der Gruppen (zum Beispiel Limits, Zeiten, Beobachtung) und die Namen legt der Implementer im Plan fest und begründet sie.

**Inkompatible Änderung der öffentlichen Fläche.** Der Slice ändert Konstruktoren, Parameter und gegebenenfalls Typnamen der öffentlichen Fläche (aus Java entfällt der Konstruktor `RequestSettings(int, int, int, int)` schon durch den sechsten Parameter; `copy` und `componentN` ändern sich). Das ist vor der ersten Veröffentlichung zulässig ([ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md): Zusage vor 1.0 eng; gemeint ist die erste Veröffentlichung, die Versionsnummer ist Gegenstand von [slice-release-entscheidungen](../open/slice-release-entscheidungen.md)) und wird hier benannt, damit niemand sie still liest.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Verhaltensänderung: nur die Form ändert sich; Bereiche, Voreinstellungen und Prüfungen in `config` bleiben (Test: alle bisherigen Tests grün, `ConfigValidationTest` unverändert in den Erwartungen).
- Neue Einstellungen: eine neue Einstellung gehört in ihren eigenen Slice.
- Der Zeitpunkt der Veröffentlichung und die Versionsnummer — [slice-release-entscheidungen](../open/slice-release-entscheidungen.md).
- Die Schlüssel der wirksamen Konfiguration (`EffectiveSettings`, zwölf Schlüssel, [ADR-0040](../../adr/0040-wirksame-konfiguration-im-ereignis-aus-typisiertem-wertobjekt.md)) und die Umgebungsnamen des Testservers: sie bleiben gleich; angeglichen wird nur der Aufbau in `ConfigLoader` und `EnvironmentConfig`.
- Bis zur Closure dieses Slice bleibt `metricsIntervalSeconds` der sechste Parameter von `RequestSettings`, Aufrufe benannt ([slice-betrieb-metriken-tracing](../done/slice-betrieb-metriken-tracing.md)).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**.

Liefer-Punkte (drei):

- [ ] **Typen und Schnitt:** `RequestSettings` und gegebenenfalls weitere Einstellungsgruppen von `ServerConfig` sind in benannte, gruppierte Typen überführt; jeder hat Voreinstellungen, Aufrufe sind überall benannt (Haupt-, Test-, Testserver-, Integrator-Code), `ConfigLoader` des Testservers und `EnvironmentConfig` sind angeglichen; der Schnitt und die Namen stehen im Plan (§3) mit Begründung; die neuen Typnamen stehen vor dem Bau in der Liste von `SPEC-059` (Ergänzung der Liste durch den Slice selbst), `make flaeche` ist grün.
- [ ] **Java-Lesart:** aus Java sind die Typen mit Voreinstellungen aufrufbar (`@JvmOverloads` oder Erbauer, Wahl begründet); ein Einbettungstest über die öffentliche Fläche (ohne Jackson, nur öffentliche Namen) belegt Kotlin- und Java-Aufruf; die inkompatible Änderung der Fläche ist im Slice benannt.
- [ ] **Belege und Nachzug:** alle bisherigen Tests sind grün; Mutationen belegen die Unterscheidbarkeit (zwei vertauschte Werte machen mindestens einen Test rot; eine verlorene Voreinstellung ebenso); Spezifikation (Tabelle der Defaults, `SPEC-062`) und alle Dateien unter `docs/user/` (`referenz-konfiguration.md`, Aufgabe 10, Einbettungstest) sind komplett gegen den Stand nach dem Slice gelesen und auf den Ist-Zustand gebracht (`AGENTS.md` §5 Regel 5).

Nachweis-Pflichten für alle drei Punkte:

- [ ] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>`; die Tests laufen in `make test` und sind grün.
- [ ] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet; die Zuordnung *Test | Festlegung* steht in der Closure-Notiz.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/config/ServerConfig.kt`, `ConfigValidation.kt` | update | Gruppen als Typen; Prüfung folgt den Gruppen, Bereiche unverändert |
| `src/main/kotlin/io/github/pt9912/tdswire/domain/telemetry/EffectiveSettings.kt`, `runtime/TelemetryEvents.kt` | update | Zuordnung `ServerConfig.effectiveSettings()` auf die neue Form; zwölf Schlüssel bleiben |
| `testserver/kotlin/io/github/pt9912/tdswire/testserver/ConfigLoader.kt` | update | Aufbau der Gruppen aus den Umgebungsnamen, Namen unverändert |
| `src/test/kotlin/` (`ConfigValidationTest`, `EnvironmentConfig`, `ZustandsereignisseTest`), `src/integrator/` (`HandleLimitEmbeddingTest`, `TelemetryFelderEmbeddingTest`, `AnleitungTest`), `testserver/test/` (`ConfigLoaderTest`) | update | Aufrufe benannt; neuer Test mit Vertauschung als Mutation; Java-Lesart im Einbettungstest |
| `spec/spezifikation.md` (`SPEC-059` Liste, §3, §5), `docs/user/` (alle Dateien), `docs/plan/spec-abdeckung.md` | update | Namen in der Liste vor dem Bau; Nutzer-Doku komplett gelesen; Matrix mit `make spec-abdeckung` |

Der Implementer erweitert die Liste in seinem ersten Lauf; Schnitt und Namen der Gruppen schlägt er im Plan vor und begründet sie.

**Schnitt und Namen (Implementer, vor dem Bau).**

Ist-Stand am 2026-11-13: `ServerConfig` führt schon `ListenSettings` (Host, Port, Verbindungen), `LoginSettings` (Frist, PRELOGIN-Größe), `EncryptionSetting`, `TlsKeyMaterial`, `AuthSettings` und `RequestSettings`; nur `RequestSettings` hat sechs gleichartige Ganzzahlen. Die übrigen Gruppen haben zwei bis drei Felder und bleiben als Typen unverändert; ihre Aufrufe werden benannt. Neu ist die Gliederung von `RequestSettings` in drei Gruppen, `ServerConfig.requests` und die Umgebungsnamen bleiben:

| Typ | Felder | Grund des Schnitts |
|---|---|---|
| `RequestTimeouts` | `queryTimeoutSeconds`, `cancelTimeoutSeconds`, `shutdownGraceSeconds` | alle Fristen in ganzen Sekunden, gleiche Art des Wertes, ein Bereich 0 oder 1 bis 3600 |
| `RequestLimits` | `maxPreparedHandles`, `maxMessageBytes` | Zählgrenzen und Größen je Sitzung und Nachricht |
| `ObservationSettings` | `metricsIntervalSeconds` | die Einstellung der Beobachtung; eine Gruppe mit einem Feld, damit ein Wert der Fristen oder Grenzen nicht an ihrer Stelle übersetzt (der Anlass: `maxMessageBytes` und `metricsIntervalSeconds` vertauscht) |
| `RequestSettings` | `timeouts`, `limits`, `observation` | drei Parameter dreier verschiedener Typen: positional nicht vertauschbar |

Java-Lesart: `@JvmOverloads` an allen vier Typen, kein Erbauer. Begründung: jede Gruppe hat höchstens drei Felder, die Überladungen decken „nur die ersten Felder setzen“ ab, und ein Erbauer fügte drei weitere öffentliche Typnamen hinzu. Grenze: aus Java lässt sich ein späteres Feld nur setzen, wenn die früheren mitgegeben werden. Der Einbettungstest ruft die Konstruktoren per Reflection auf (derselbe Weg wie bei `UpdateCount` und `ResultColumn`; das Source-Set `integrator` hat keinen Java-Quelltext).

Neue Namen in `SPEC-059`: `RequestTimeouts`, `RequestLimits`, `ObservationSettings` (Package `config`). Der Schnitt liegt im Rahmen von Abschnitt 1 (Beispiel „Limits, Zeiten, Beobachtung“), eine Architect-Entscheidung ist nicht nötig. Aufrufe der übrigen Gruppen (`ListenSettings`, `LoginSettings`) sind im Haupt-, Test-, Testserver- und Integrator-Code ebenfalls benannt.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-betrieb-metriken-tracing](../done/slice-betrieb-metriken-tracing.md) liegt in `done/` (der sechste Parameter besteht), und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2; Schnittvorschlag: `RequestSettings` zuerst, weitere Gruppen in einem Folge-Slice.
- `in-progress` → `open` (Entscheid des Architects nötig): die Ergänzung der Liste der Namen in `SPEC-059` verlangt nach [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md) oder [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 4 eine Entscheidung, die der Slice nicht selbst treffen darf (dann Architect, gegebenenfalls Folge-ADR mit `Supersedes`, weil `Accepted` unveränderlich ist, `AGENTS.md` §3.5).

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün (einschließlich `make test` und `make flaeche`), der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung *Test | Festlegung*, die Benennung der inkompatiblen Änderung und den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang.

- Neue Typnamen weiten die Liste von `SPEC-059`; `make flaeche` vergleicht Typnamen, keine Mitglieder (`BEO-TDS/oeffentliche-flaeche-mitglieder-ohne-sensor`), eine vertauschte Reihenfolge der Mitglieder sieht er nicht. — **Ausgang:** offen bis zur Closure.
- Der Umbau berührt viele Aufrufe (Haupt-, Test-, Testserver-, Integrator-Code, Beispielcode der Anleitung); ein positionaler Aufruf, der still kompiliert, ist die Gefahr, die der Slice beseitigen soll. — **Ausgang:** offen bis zur Closure (Sensor: Mutation mit vertauschten Werten).
- Konflikte mit parallelen Zweigen, die `ServerConfig` oder `RequestSettings` ändern (frühere Merges: `RequestSettings` fünfter und sechster Parameter). — **Ausgang:** offen bis zur Closure.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register · `grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

Wird bei der Closure gefüllt.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); keine eigene Sub-Area.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`), nachgesehen am 2026-11-07:

- `BEO-TDS/sechster-requestsettings-parameter-ohne-entscheidung` — Auslöser; Entscheid des Nutzers 2026-11-07, dieser Slice löst ihn auf.
- `BEO-TDS/oeffentliche-flaeche-mitglieder-ohne-sensor` — 2×, offen: die Mitglieder der Fläche hält kein Sensor (Risiko in §6).
- `BEO-TDS/plan-tabelle-nennt-neue-datei-nicht` — verkörpert: Tabelle in §3 nennt jede berührte Datei, der Implementer ergänzt sie.
- `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` — verkörpert: Spezifikation, Nutzer-Doku und Beispielcode gemeinsam nachziehen.
- Ohne Treffer: die übrigen Beobachtungen.

Alle berührten Sub-Areas GF.
