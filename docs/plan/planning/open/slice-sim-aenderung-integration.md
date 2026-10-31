# Slice slice-sim-aenderung-integration: Simulations-Backend, Teil 3: Änderungsweitergabe, Verdrahtung und Integrationstest mit dem Referenzclient

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-simulations-backend

**Bezug:** [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-08](../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-QA-05](../../../../spec/lastenheft.md#tds-qa-05--wartbarkeit), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md)

**Berührte Spec-Stellen:** SPEC-025 · SPEC-035 · SPEC-036 · SPEC-040 · ARC-008 · ARC-016 · [TDS-FA-11.a](../../../../spec/spezifikation.md#tds-fa-11a--simulations-backend)

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

**Ziel:** Einfüge-, Änderungs- und Löschanweisungen werden an die Callbacks des Integrators weitergegeben und mit dem Zähler beantwortet; `runtime` kann den Server mit dem Simulations-Backend starten; das Test-Gate nach SPEC-040 belegt mit dem Referenzclient Auswahl, leeres Ergebnis, Zeilenquelle oberhalb der Speichergrenze, Fehler der Zeilenquelle und Cancel.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Vergleich mit einer SQL-Server-Referenzinstanz — [slice-referenzabgleich-live](slice-referenzabgleich-live.md); dieser Slice belegt das Verhalten gegen den Referenzclient, nicht gegen einen echten Server.
- Die Kompatibilitäts-Szenarien S1 bis S18 — [slice-qa-kompatibilitaet](slice-qa-kompatibilitaet.md) schreibt sie; dieser Slice schreibt nur die fünf Szenarien von SPEC-040 und nutzt Gerüst und Make-Mechanik von [slice-qa-dotnet-referenztest-geruest](../done/slice-qa-dotnet-referenztest-geruest.md). Welches Backend die Szenarien von [slice-qa-kompatibilitaet](slice-qa-kompatibilitaet.md) bedient, ist offen (Frage 4 in §6).
- Persistenz und Transaktionssemantik über die Weitergabe hinaus, Auswertung beliebigen SQLs — nach [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) ausgeschlossen.
- Lastmessung gegen das Simulations-Backend — [slice-qa-performance](slice-qa-performance.md).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Änderungsweitergabe** ([TDS-FA-11.a](../../../../spec/spezifikation.md#tds-fa-11a--simulations-backend) Schritt 6): W1, W2 und W3 prüfen die Werte, rufen den Callback der Tabelle (`where = null` für eine Anweisung ohne `WHERE`) und senden dessen Zahl als `UpdateCount`; ohne Callback endet die Anweisung mit `TDS_UNSUPPORTED_SQL`, ein Fehler im Callback ist ein `Failure` mit `TDS_BACKEND_ERROR`; bei einem Fehler der Wertprüfung wird kein Callback gerufen (zählende Attrappe mit positivem Kontrollfall); jede Anweisung ruft ihren Callback genau einmal.
- [ ] **Verdrahtung:** `runtime` (ARC-008) verdrahtet das Simulations-Backend optional, den Weg legt [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) aus [slice-adr-simulation-formen](../done/slice-adr-simulation-formen.md) fest — Kotlin-Schnittstelle und eine Umgebungsvariable mit dem Pfad der Beschreibungsdatei (Name legt der Slice fest und trägt ihn in die Konfiguration ein). Hierher gehört die neue Abhängigkeit: `jackson-dataformat-yaml` 2.21.7 als `implementation` (`make deps-lock` schreibt `gradle.lockfile` und `gradle/verification-metadata.xml`; Größenordnung 6 Sperrzeilen und 11 Komponenten nach der ADR), der Adapter in `runtime`, der die Datei mit der Strom-API zu einem Baum aus Abbildung, Liste und Skalar mit Zeile und Spalte liest und an den Leser aus [slice-sim-erkennung](../done/slice-sim-erkennung.md) übergibt, die Größengrenze von 1 MiB vor dem Parsen und der Startabbruch bei einem Ladefehler vor dem Binden des Ports (Test); geladene Tabellen haben eine leere Zeilenquelle und keine Callbacks; kein anderes Package importiert `simulation` (die Importregel in `config/detekt/detekt.yml` bleibt grün); **Wächter für die neue Abhängigkeit:** `ForbiddenImport/simulation` sperrt bisher nur Pakete des Repos, ein Import der YAML-Bibliothek in `simulation` bliebe grün ([ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) §Fitness Function). Der Slice ergänzt in `config/detekt/detekt.yml` eine `ForbiddenImport`-Zeile für die YAML-Bibliothek in `simulation` (Paketpräfixe aus den gelockten Jars, nicht geraten) und einen **Selbsttest-Fall dafür, der Pflicht ist** (`config/detekt/selbsttest`, `tools/detekt-selbsttest.sh`; ohne ihn ist der Punkt nicht erfüllt): ein Import der Bibliothek in einer Testdatei im Package `simulation` macht `make detekt-selbsttest` rot, solange die Zeile fehlt. Das ist eine Verschärfung, keine Lockerung (`AGENTS.md` §3.6). Und der Server startet und beantwortet den Login auch ohne das Simulations-Backend (Test).
- [ ] **Test-Gate nach SPEC-040:** Ein Integrationstest mit dem Referenzclient gegen den Serverprozess mit Simulations-Backend prüft Auswahl mit Bedingung, leeres Ergebnis, eine Zeilenquelle oberhalb der Speichergrenze (Prozess mit gesetztem Heap), den Fehler der Zeilenquelle und Cancel mit Folgeabfrage; das Make-Target steht in `harness/README.md` §Sensors, und ob es in `make gates` aufgenommen wird, entscheidet die gemessene Laufzeit (Frage in §6).

Nachweis-Pflichten für alle drei Punkte:

- [ ] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>` (Konvention nach [slice-qa-spec-abdeckung](../done/slice-qa-spec-abdeckung.md)); die Tests laufen in `make test` und sind grün.
- [ ] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet; die Zuordnung *Test | Festlegung* je Aussage steht in der Closure-Notiz (`BEO-TDS/aussagen-ueber-messung-hinaus`, siehe §8).
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
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/` <!-- d-check:ignore (geplant: neue Datei) --> | update | Weitergabe an Callbacks |
| `src/main/kotlin/io/github/pt9912/tdswire/runtime/` <!-- d-check:ignore (geplant: neue Datei) --> | update | optionale Verdrahtung (Composition Root) |
| `referenztest-dotnet/` | update | Szenarien nach SPEC-040 im Testprojekt des Gerüsts |
| `tools/mk/` | update | Make-Fragment für das Gate nach SPEC-040; `GATE_CHECKS +=` nach Entscheidung |
| `src/test/kotlin/io/github/pt9912/tdswire/simulation/` <!-- d-check:ignore (geplant: neue Datei) --> | update | Callback-Tests Happy/Boundary/Negative; `@spec`-Tag SPEC-035 |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) steht auf `Accepted`, [slice-sim-erkennung](../done/slice-sim-erkennung.md) (der Leser vom Baum zur Beschreibung) und [slice-sim-auswahl-streaming](slice-sim-auswahl-streaming.md), [slice-qa-dotnet-referenztest-geruest](../done/slice-qa-dotnet-referenztest-geruest.md), [slice-bootstrap-konfiguration](../done/welle-handshake-login/slice-bootstrap-konfiguration.md) und [slice-sql-batch-dispatch](../done/slice-sql-batch-dispatch.md) liegen in `done/` (der Serverprozess startet und beantwortet SQL_BATCH), und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2 oder der Integrationstest sprengt den Rahmen; Schnittvorschlag: Änderungsweitergabe als eigener Slice.
- `in-progress` → `open` (blockiert — Carveout?): der Serverprozess beantwortet SQL_BATCH noch nicht, oder [ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) wird durch eine Folge-ADR abgelöst (Weg der Verdrahtung, Bibliothek oder Format).

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün, der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung *Test | Festlegung* mit der Heap-Grenze des Integrationslaufs sowie den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Neue Abhängigkeit im Lock: `jackson-dataformat-yaml` zieht `databind`, `core`, `annotations` und `snakeyaml` mit (5 Jars, 2,8 MB; die Prüfsummen stammen vom ersten Abruf, `verify-signatures` steht auf `false`). Die Zahlen der ADR sind ein Lock-Versuch auf einer Kopie, kein Lauf in diesem Repo: Die Closure nennt die tatsächlichen Änderungen von `make deps-lock`. Ob ein Lizenznachweis nötig wird, trägt [slice-release-artefakt-lizenz](slice-release-artefakt-lizenz.md); der Slice fügt kein Hinweisdokument hinzu. — **Ausgang:** offen bis zur Closure.
- Lock-Werkzeug: `make deps-lock` ist repariert und läuft mit der vollen Aufgabenliste (Stage `lock` trägt die Testeingänge; Beleg: [Verifikation 2](../../../reviews/2026-10-30-cc81155-verifikation-2-adr-0012-simulations-backend.md)). Folgepflicht: Die Closure beschreibt, wie Lock und Prüfsummen geschrieben wurden; kein Gate wird dafür gelockert ([ADR-0012](../../adr/0012-simulations-backend-beschreibung-und-formen.md) §Konsequenzen). — **Ausgang:** offen bis zur Closure.
- Größe: Die Verdrahtung trägt jetzt Bibliothek, Adapter, Größengrenze, Startabbruch und Umgebungsvariable und ist der größte der drei Liefer-Punkte; wächst sie über den Rahmen, ist der Schnitt „Änderungsweitergabe“ und „Datei laden und verdrahten“ als zwei Slices (siehe §4). — **Ausgang:** offen bis zur Closure.
- Callbacks schreiben beim Integrator; eine Wiederholung nach einem Teilerfolg würde sie doppelt rufen ([ADR-0007](../../adr/0007-fehler-und-attention.md), Option C ist nicht gewählt). Der Test belegt genau einen Aufruf je Anweisung; die Aussage gilt nur, solange der Server nicht wiederholt. — **Ausgang:** offen bis zur Closure.
- Die Aussage „Zeilenquelle oberhalb der Speichergrenze“ gilt für den Heap des Serverprozesses im Test, nicht für den Speicher des Clients und nicht für das Produkt unter Last (`BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`); die Closure nennt den gesetzten Heap und die Zeilenzahl. — **Ausgang:** offen bis zur Closure.
- Laufzeit des Integrationstests im Gate: ungemessen; ein langsames Gate verleitet zum Herausnehmen — die Messung steht in der Closure, die Aufnahme in `make gates` folgt ihr. — **Ausgang:** offen bis zur Closure.
- Doppelung mit [slice-qa-kompatibilitaet](slice-qa-kompatibilitaet.md) (Frage 4): Laufen deren Szenarien S3 bis S10 gegen das Simulations-Backend, ist der Start-Trigger jenes Slice zu ergänzen; die Szenarien dürfen nicht zweimal geschrieben werden. — **Ausgang:** offen bis zur Closure.
- Der Integrationslauf belegt das Verhalten des Referenzclients gegen den Server dieses Repos, nicht gegen einen echten SQL Server (`BEO-TDS/messung-am-echten-sql-server-fehlt`). — **Ausgang:** offen bis zur Closure.
- Die Tabellenbeschreibung `SimTable` behält die übergebenen Listen `columns` und `primaryKey` unkopiert (acht Konstruktorparameter, `LongParameterList` des Gates erlaubt sechs); Index und Validierung gelten nur zum Zeitpunkt der Übergabe, ändert der Integrator die Liste danach, stehen sie auseinander (`BEO-TDS/listen-ohne-kopie-wegen-parameterzahl-gate`, aus [slice-sim-erkennung](../done/slice-sim-erkennung.md)). Wo dieser Slice `SimTable` aus Listen des Integrators erzeugt (Schnittstelle und Adapter), kopiert er sie dort (Fabrikaufruf oder sekundärer Konstruktor, ohne die Schwelle zu lockern) oder weist den Weg ab; ein Zuschnitt von `SimTable` auf weniger Parameter oder das Lockern von `LongParameterList` (ADR, `AGENTS.md` §3.6) ist Entscheidung des Nutzers. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Das Package `simulation` ist ein eigener Pfad-Cluster (Achse 3) mit Importgrenze im Gate (`ForbiddenImport/simulation` in `config/detekt/detekt.yml`; Achse 1 plausibel) und mit einer Doku-Aussage (SPEC-036), die gegen den Code abgleichbar ist (Achse 2 plausibel). Die Schwelle wäre damit erreicht; weil der Modus Greenfield derselbe bliebe und eine eigene Zeile in `harness/conventions.md` keine andere Regel auslöste, bleibt `*`. Eine Ausdifferenzierung ist Sache des Nutzers und hier nicht vorgenommen.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, 19 Beobachtungen, Stand des Worktrees auf `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/aussagen-ueber-messung-hinaus` — 16×, Stand *verkörpert* (`AGENTS.md` §3.8, Reviewer-Skill). Betrifft diesen Slice: Jede Aussage über den Integrationslauf nennt Heap, Zeilenzahl und Client-Version.
- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 9×, Stand *geplant* (Träger [slice-qa-reproduzierbarkeit](../done/slice-qa-reproduzierbarkeit.md)). Der Lauf ist gegen den Referenzclient, nicht gegen einen echten SQL Server; Vergleich: [slice-referenzabgleich-live](slice-referenzabgleich-live.md).
- `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` — 7×, Stand *geplant* (Träger [slice-qa-kompatibilitaet](slice-qa-kompatibilitaet.md)). Der Integrationslauf läuft in TDS 7.4 ohne Pool; TDS 8.0 und Pooling sind hier nicht gemessen.
- `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen` — 1×, offen. Betrifft die Aussage zur Speichergrenze (Testlauf, §6).
- `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler` — 1×, offen. Betrifft die zählende Attrappe der Callbacks (Untergrenze, §2).
- `BEO-TDS/test-stage-cache-verdeckt-wiederholung` — 2×, offen. Betrifft das Test-Gate in einer Docker-Stage: gecachte grüne Läufe belegen Wiederholung nicht.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Slice und Closure-Notiz nennen `ADR-*`- und `TDS-*`-Kennungen als Link.
- Ohne Treffer: `BEO-TDS/vergleichslauf-d-ungeklaert`, `BEO-TDS/offene-punkte-ohne-traeger`, `BEO-TDS/spec-einzelfaelle-ohne-lastenheft-deckung`, `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`, `BEO-TDS/geheimnis-kopien-im-speicher-nicht-gemessen`, `BEO-TDS/strukturtest-ohne-variation-der-eingabe`, `BEO-TDS/abdeckungsmatrix-veraltet-ohne-sensor`, `BEO-TDS/ablage-im-repo-bricht-docs-check`, `BEO-TDS/katalog-und-lock-ohne-gegenpruefung`, `BEO-TDS/java-quelle-umgeht-kotlin-regel`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/adapter-festlegungen-ohne-slice`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
