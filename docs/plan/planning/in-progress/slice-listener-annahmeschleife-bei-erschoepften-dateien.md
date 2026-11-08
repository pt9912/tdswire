# Slice slice-listener-annahmeschleife-bei-erschoepften-dateien: Listener: Annahmeschleife läuft nach erschöpftem Limit offener Dateien weiter oder meldet

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-betrieb-diagnose

**Bezug:** [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md) (`Accepted`)

**Berührte Spec-Stellen:** SPEC-048 · [TDS-FA-09.a](../../../../spec/spezifikation.md#tds-fa-09a--konfiguration-und-diagnose) (Abschnitt 4: der Satz zu `ACCEPT_FAILED`) · SPEC-044 (nur gelesen: die Annahmeschleife endet mit dem geordneten Beenden)

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912. **Datum:** 2026-11-06.

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Ist das Limit offener Dateien erschöpft, läuft die Annahmeschleife von `TdsListener` weiter (sie nimmt nach dem Freiwerden wieder an) oder meldet den Ausfall als `ACCEPT_FAILED`; sie steht nicht mehr still und ohne Meldung.

**Herkunft (Befund):** Aus dem Review von [slice-betrieb-telemetrie-verdrahtung-belegen](../done/slice-betrieb-telemetrie-verdrahtung-belegen.md), Finding F-2 (Review-Report [2026-11-06-84edd7e-review-slice-betrieb-telemetrie-verdrahtung-belegen](../../../reviews/2026-11-06-84edd7e-review-slice-betrieb-telemetrie-verdrahtung-belegen.md); Test `AcceptChild.kt` unter `src/test/kotlin/io/github/pt9912/tdswire/runtime/`). Der Befund, gemessen mit einer Kind-JVM unter `ulimit -n` und ohne das Vorladen der Klassen: Bei erschöpftem Limit offener Dateien endet der `failed`-Callback von `TdsListener.acceptOne` mit einem `NoClassDefFoundError` (Thread `tdswire-listener-io-1`), weil die JVM die Klasse `Accept.Failed` nicht mehr laden kann. Die Continuation von `acceptOne` wird nicht fortgesetzt, die Annahmeschleife steht, es kommt kein `ACCEPT_FAILED` und kein Wiederannehmen. Gemessen ist das in einer Kind-JVM mit einem Limit von 128 bis 160 Dateien; ob es in anderen Umgebungen gleich verläuft, ist nicht belegt. Mit vorgeladenen Klassen entsteht das Ereignis, und der Server nimmt nach dem Schließen der Verbindungen wieder an.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Der Test über den Server für `ACCEPT_FAILED` mit vorgeladenen Klassen, `CONNECTION_FAILED` und die Rot-Fälle der Ausgabe-Sperre — [slice-betrieb-telemetrie-verdrahtung-belegen](../done/slice-betrieb-telemetrie-verdrahtung-belegen.md); dieser Slice setzt dessen Test voraus und ändert das Produktverhalten, das jener nur belegt.
- Weitere `Error` aus Callbacks der NIO-Gruppe (etwa beim Lesen oder Schreiben einer Verbindung) — nicht gemessen; der Slice misst die Annahme, und ein Befund zu anderen Callbacks bekäme einen eigenen Eintrag im Register.
- Eine Zusage zum Verhalten bei erschöpftem Limit über die Annahme hinaus (Grenze offener Dateien je Prozess, Betriebsanleitung) — braucht eine Entscheidung des Nutzers; der Slice liefert Verhalten und Beleg.

**Keine Mindestzahl.** Ein Slice mit *einem* echten Ausschluss ist besser als
einer mit vier erfundenen; die vier Klassen sind ein Suchraster, keine
Ausfüll-Liste. Suchreihenfolge: Was übernimmt ein **Folge-Slice** (mit
Kennung — und die Kennung muss den Punkt auch annehmen)? Was bleibt als
**Bestand** bewusst stehen (mit Begründung)? Was wäre ein **anderer Vorgang**?
Welche **Schicht** rührt der Slice nicht an?

Was hier steht, ist die Grenze, an der ein wachsender Slice sich messen lässt:
Wer später etwas mitnimmt, das hier ausgeschlossen war, hat den Plan
**geändert**, nicht nur ergänzt.

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (zwei):

- [ ] **Annahmeschleife bei erschöpftem Limit** ([TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose)): der Test der Kind-JVM läuft **ohne** das Vorladen der Klassen und liest `ACCEPT_FAILED` im Sammler; nach dem Schließen der Verbindungen nimmt der Server wieder an. **Beleg:** die Rücknahme der Änderung am Produktcode macht den Test rot (Mutation: der `failed`-Callback behandelt den `NoClassDefFoundError` wie vorher). Der Weg (Klassen vor dem ersten Annehmen laden, den Callback ohne neue Klassen abschließen, den `Error` fangen oder Beenden mit Meldung) bleibt dem Architect; welcher gewählt wird, steht mit Grund in der Closure-Notiz.
- [ ] **Spezifikation und Test-Naht** ([TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose)): der Satz zu `ACCEPT_FAILED` in Abschnitt 4 der Spezifikation nennt die gemessene Bedingung (Kind-JVM, Limit, ohne Vorladen); trägt `AcceptChild` das Vorladen weiter, steht im KDoc, warum und was der Test dann nicht belegt.

Nachweis-Pflichten für beide Punkte:

- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist (Abschnitt 4 der Spezifikation, siehe Liefer-Punkt 2).
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
| `src/main/kotlin/io/github/pt9912/tdswire/transport/listener/TdsListener.kt` | update | `acceptOne` und der `failed`-Callback: Annahmeschleife übersteht erschöpftes Limit: `prepareExhaustion` baut Werte der Klassen des `failed`-Pfads und des Lesepfads (`ChannelResult`) vor dem ersten Annehmen |
| `src/main/kotlin/io/github/pt9912/tdswire/runtime/TelemetryEvents.kt` · `src/main/kotlin/io/github/pt9912/tdswire/runtime/Bootstrap.kt` | update | Weg A nach [ADR-0043](../../adr/0043-annahmeschleife-bei-erschoepftem-dateilimit.md): `prepareAcceptFailure` baut die Ereignisse des Annahmefehlers einmal (Klassen des Weges bis zum Ereignis), `Bootstrap` ruft es vor dem Start des Listeners |
| `src/test/kotlin/io/github/pt9912/tdswire/runtime/AcceptChild.kt` | update | Kind-JVM ohne Vorladen oder mit begründetem Vorladen, nach [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität) (Negative: Limit erschöpft) |
| `src/test/kotlin/io/github/pt9912/tdswire/runtime/TelemetryWiringTest.kt` | update | Test des Wiederannehmens ohne Vorladen |
| `spec/spezifikation.md` Abschnitt 4 | update | Satz zu `ACCEPT_FAILED` mit gemessener Bedingung |

Die Dateien `AcceptChild.kt` und `TelemetryWiringTest.kt` entstehen mit dem Zweig `worktree-agent-a735f10cebdeaf861` und liegen erst nach dessen Merge auf `main`; die Tabelle gilt für den Stand nach dem Merge.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-betrieb-telemetrie-verdrahtung-belegen](../done/slice-betrieb-telemetrie-verdrahtung-belegen.md) liegt in `done/` (der Befund und der Test `AcceptChild` stehen damit auf `main`); ein Verantwortlicher ist eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): die Behebung verlangt eine Änderung der Fehlerbehandlung aller Callbacks der NIO-Gruppe; Schnittvorschlag: Annahme | übrige Callbacks.
- `in-progress` → `open` (blockiert): der Befund lässt sich in der Umgebung des Gates nicht wiederholen (das Limit der Kind-JVM greift nicht); dann bleibt er Register-Eintrag mit der Grenze „nur in einer Umgebung gemessen“.

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün (einschließlich `make test`), der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung *Test | Festlegung* und den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Der Test hängt an `sh`, `ulimit -n` und Zeitschranken von 40 s und 60 s; unter Last ist er nicht gemessen (Review F-9). — **Ausgang:** offen bis zur Closure.
- Der Weg, der die Klasse ohne Dateizugriff bereitstellt, kann eine Naht im Produktcode verlangen (`BEO-TDS/test-naht-im-produktivcode`). — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); der Slice berührt Listener, Tests und Spezifikation, eine eigene Sub-Area besteht nicht.

**Vorgelagert — offene Beobachtungen sichten:** `BEO-TDS/test-naht-im-produktivcode` (offen, 2×), `BEO-TDS/lebenszyklus-im-fremden-prozess-nicht-gemessen` (offen, 1×), `BEO-TDS/beenden-unter-last-nicht-gemessen`, `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert: der Befund gilt nur in der gemessenen Umgebung). Der Befund selbst hat noch keinen Eintrag im Register; die Closure legt ihn an.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
