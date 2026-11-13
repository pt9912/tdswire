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

**Verantwortlich:** Implementer-Agent, Auftrag des Nutzers 2026-11-08.

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

- [x] **Annahmeschleife bei erschöpftem Limit** ([TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose)): der Test der Kind-JVM läuft **ohne** das Vorladen der Klassen und liest `ACCEPT_FAILED` im Sammler; nach dem Schließen der Verbindungen nimmt der Server wieder an. **Beleg:** die Rücknahme der Änderung am Produktcode macht den Test rot (Mutation: der `failed`-Callback behandelt den `NoClassDefFoundError` wie vorher). Der Weg (Klassen vor dem ersten Annehmen laden, den Callback ohne neue Klassen abschließen, den `Error` fangen oder Beenden mit Meldung) bleibt dem Architect; welcher gewählt wird, steht mit Grund in der Closure-Notiz.
- [x] **Spezifikation und Test-Naht** ([TDS-FA-09](../../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose)): der Satz zu `ACCEPT_FAILED` in Abschnitt 4 der Spezifikation nennt die gemessene Bedingung (Kind-JVM, Limit, ohne Vorladen); trägt `AcceptChild` das Vorladen weiter, steht im KDoc, warum und was der Test dann nicht belegt.

Nachweis-Pflichten für beide Punkte:

- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist (Abschnitt 4 der Spezifikation, siehe Liefer-Punkt 2).
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

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

`AcceptChild.kt` und `TelemetryWiringTest.kt` liegen auf `main` (aus [slice-betrieb-telemetrie-verdrahtung-belegen](../done/slice-betrieb-telemetrie-verdrahtung-belegen.md)).

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

- Der Test hängt an `sh`, `ulimit -n` und Zeitschranken von 40 s und 60 s; unter Last ist er nicht gemessen (Review F-9). — **Ausgang:** weiter offen: am Kind-JVM-Test nicht eingetreten (Verifikation: grün bei Lastmittel 8 bis 277, Frischlauf, drei Mutationsbauten mit Baseline); rot wurden zwei andere Wanduhr-Tests, beide in `BEO-TDS/wanduhr-tests-flackern-unter-last` geführt. Die Beobachtung bleibt offen.
- Der Weg, der die Klasse ohne Dateizugriff bereitstellt, kann eine Naht im Produktcode verlangen (`BEO-TDS/test-naht-im-produktivcode`). — **Ausgang:** eingetreten: `prepareExhaustion` (`TdsListener`) und `prepareAcceptFailure` (`TelemetryEvents`, Aufruf in `Bootstrap`) stehen im Produktcode; [ADR-0043](../../adr/0043-annahmeschleife-bei-erschoepftem-dateilimit.md) benennt das als akzeptiertes Negativ; Produktnutzen ist die Verfügbarkeit nach Ressourcenmangel, die Fläche wächst nicht (beide Funktionen `internal`, `make flaeche` grün).

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

- **Zuordnung Test | Festlegung:** *Test* (`TelemetryWiringTest` „Ein gescheitertes Annehmen ergibt ACCEPT_FAILED und der Server nimmt danach wieder an“; `TelemetryRedactionTest` „Der Text der Ausnahme des Betriebssystems erreicht ACCEPT_FAILED nicht“, Limit 128 und 160): Kind-JVM `AcceptChild` aus Klassenverzeichnissen **ohne** Vorladen; das Ereignis `ACCEPT_FAILED` mit `exceptionType` `java.io.IOException` kommt an, nach dem Schließen der gehaltenen Verbindungen nimmt der Server an (128 Dateien; bei 160 nur das Ereignis). Rotbelege der Verifikation: M1 (Aufruf `prepareAcceptFailure()` in `Bootstrap.events()` entfernt) und M2 (Prüfung der `failed`-Klassen aus `prepareExhaustion` entfernt) röten beide Klassen mit `kein ACCEPT_FAILED`, M3 (`ChannelResult`/`ChannelError` entfernt) röten nur den Test des Wiederannehmens; Review M4a (nur `ChannelResult.Ok`) und M4b (nur `Accept.Failed`) rot. *Festlegung* (nur Text, kein Test): die Auswahl der Klassen als Bestimmung durch den Test („Welche Klassen genügen, bestimmt der Test“); die Aussage zu `ChannelFailure` (nur im Verbund mit `ChannelResult` belegt, V-2); die Grenzen im Satz zu `ACCEPT_FAILED` in Abschnitt 4 der Spezifikation (andere Umgebungen, Jar, andere Callbacks der NIO-Gruppe, Senke des Embedders nicht gemessen).
- **Weg nach [ADR-0043](../../adr/0043-annahmeschleife-bei-erschoepftem-dateilimit.md):** Weg A (Vorbereitung des Fehlerpfads beim Start), kein Rückfall auf B. Grund: der einzige Weg, der gemessen trägt, ist „Klassen vorhanden“; A stellt das im Produktcode her, mit dem kleinsten Eingriff, ohne neue Fläche und ohne neues Verhalten bei Erfolg. Die Vorbereitung ging über den Wortlaut der ADR hinaus: das Domänenereignis `AcceptFailed` im Mapping der Runtime (`prepareAcceptFailure`) und `ChannelResult$Ok` im Lesepfad gehaltener Verbindungen (`prepareExhaustion`). Das deckt die ADR mit „welche Klassen genügen, bestimmt der Test der Kind-JVM“ (Review F-4); eine Schärfung oder Folge-ADR ist nicht nötig. Zu eng ist der Beleg, nicht der Umfang: er trägt gehaltene, idle Verbindungen, keine Anfrage, kein Schreiben und kein Framing unter erschöpftem Limit.
- **Was hat funktioniert:** (1) Die Mutation je Teil der Vorbereitung (Review M4a bis M4c, Verifikation M1 bis M3) ordnet jedem Teil einen Träger zu oder findet den Teil ohne Träger (F-2: doppelt gebautes `ListenerEvent.AcceptFailed`, behoben). (2) Das Entfernen der Test-Hilfe `preloadClasses` machte den Produktfehler sichtbar, den das Vorladen im Test verdeckt hatte. (3) Die Meldung `fehlt: [...]` mit der fehlenden Klasse im Kopf der Assertion (Nachzug F-1, F-2) macht den Rotfall lesbar.
- **Was ging anders als geplant:** (1) Der Plan sah `TdsListener.kt` und den Test vor; der Weg verlangte zusätzlich `TelemetryEvents.kt` und `Bootstrap.kt` (Plan-Tabelle vom Implementer ergänzt). (2) Die erste Fassung des Satzes in Abschnitt 4 war breiter als die vorbereiteten Klassen („den Lesepfad der Verbindungen“, F-1) und ist auf die Ergebniswerte des Lesens eingeschränkt. (3) Der Verifier sah in Stufe 7 von `make e2e` einmal rot (Last 182), nicht im Diff.
- **Review und Verifikation:** Review `5400b4e3` (0 HIGH/MEDIUM, F-1 und F-2 LOW, F-3 bis F-7 INFO): F-1 und F-2 behoben in `07cf015b` ohne zweiten Review (der Verifier las und mutierte sie), F-3 und F-4 in dieser Closure, F-5 und F-6 als Grenzen, F-7 (Schlusssatz §3) in dieser Closure. Verifikation `07cf015b` (0 HIGH/MEDIUM/LOW; V-1 bis V-4 INFO): V-3 (Verantwortlicher) in dieser Closure, V-4 (ADR im Zweig `Proposed`) erledigt mit dem Merge von `main`. Grenzen, nicht behoben: **V-1** — die Meldung „der Server nimmt nach dem Schließen nicht mehr an“ hängt `child.output` ungekürzt an (im roten Lauf M3 rund 400 KB in einer Zeile), anders als die erste Meldung, die auf `DIAGNOSTIC_CHARS` kürzt; die fehlende Klasse steht dort nur durch Suchen. **V-2** — `ChannelFailure` ist nur im Verbund mit `ChannelResult.Failed` und `ChannelError` mutiert; die Aussage der Spezifikation trägt als Gruppe („Ergebniswerte des Lesens“), nicht als Einzelname. Die Nachzüge hatten keinen Re-Review.
- **Gemessen** (Einzelmessungen 2026-11-08, ein Host mit Lastmittel 8 bis 277 durch fremde Läufe, kein ruhiger Host; Quelle: Verifikationsbericht): `make clean-build` Exit 0 in 13 min 57 s, 1230 Testfälle `PASSED`, 0 `FAILED`; `make matrix-zitate` Exit 0, 309 Zitate; `make gates FORCE=1` Exit 0 aus dem Layer-Cache (kein Frischbeleg der Tests); `make e2e` Lauf 1 Stufe 7 rot, Lauf 2 grün; drei Mutationen rot.
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-listener-annahmeschleife-bei-erschoepften-dateien.md` ergänzt in [BEO-TDS/test-naht-im-produktivcode](../observations/BEO-TDS/test-naht-im-produktivcode/observation.md) (F-3; **3 Dateien, Schwelle erreicht**; der Lese-Schritt gehört in die Closure von [welle-betrieb-diagnose](welle-betrieb-diagnose.md), hier nicht entschieden) und [BEO-TDS/wanduhr-tests-flackern-unter-last](../observations/BEO-TDS/wanduhr-tests-flackern-unter-last/observation.md) (`ListenerAbortTest` mit `CANCEL_TIMEOUT` unter Last 19 bis 24 rot beim Implementer, `SynchronitaetTests.Cancel_und_Command_Timeout_kurz_hintereinander…` in Stufe 7 bei Last 182 rot beim Verifier, Wiederholung grün); fortgeschrieben [BEO-TDS/listener-annahmeschleife-steht-bei-erschoepftem-dateilimit-still](../observations/BEO-TDS/listener-annahmeschleife-steht-bei-erschoepftem-dateilimit-still/observation.md) (Stand: Folge-Slice geschlossen).
- **Steering-Loop-Eintrag:** Kein Eintrag in dieser Closure verkörpert, daher kein `liegt in`. Gelernt: (1) *Eine Test-Hilfe, die eine Vorbedingung des Betriebs herstellt, kann einen Produktfehler verdecken*: das Vorladen in `AcceptChild` machte den Test grün, während die Annahmeschleife im Betrieb stillstand; die Mutation „Hilfe entfernen“ fand es im Review von [slice-betrieb-telemetrie-verdrahtung-belegen](../done/slice-betrieb-telemetrie-verdrahtung-belegen.md). Der Fall ist mit der Beobachtung `BEO-TDS/test-naht-im-produktivcode` verwandt, aber nicht gleich (hier steht Produktcode, den heute nur der Test begründet, und er hat Produktnutzen; Verifikation); er wird dort als dritte Evidence geführt, die Zuordnung ist eine Setzung, und ob eine Regel daraus wird, entscheidet die Welle-Closure. (2) *Eine Vorbereitung im Produktcode hat nur so viele Teile, wie Tests sie halten*: die Mutation je Teil fand einen Teil ohne Träger (F-2). Gezählt, nicht verkörpert. **Benannte Spec-Lücke:** keine; die Lücke der Spezifikation (Bedingung des Satzes zu `ACCEPT_FAILED`) ist mit diesem Slice in Abschnitt 4 geschlossen.
- **Risiken aus §6:** beide mit Ausgang eingetragen (Last-Flake weiter offen, am Kind-JVM-Test nicht eingetreten; Naht im Produktcode eingetreten und in [ADR-0043](../../adr/0043-annahmeschleife-bei-erschoepftem-dateilimit.md) als akzeptiertes Negativ benannt).
- **Folge-Slices:** keiner. Offene Grenzen ohne Slice: andere Callbacks der NIO-Gruppe unter erschöpftem Limit (Schreiben, Framing, Anfrage), der Betrieb aus einem Jar und andere Umgebungen — nicht gemessen, laut Abschnitt 4 der Spezifikation benannt; ein Befund dazu bekäme einen Eintrag im Register.
- **Drei Paarungen:** Das Repo führt Wellen; die Paarungen prüft die Closure von [welle-betrieb-diagnose](welle-betrieb-diagnose.md). Anker ↔ Folge-Slice: kein Anker gesetzt. Register ↔ Closure-Notiz: alle genannten Kennungen existieren als Verzeichnis mit nicht leerem `evidence/` (am Dateisystem geprüft). Folge-Slices: keine genannt.

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

**Vorgelagert — offene Beobachtungen sichten:** `BEO-TDS/test-naht-im-produktivcode` (offen, 2×), `BEO-TDS/lebenszyklus-im-fremden-prozess-nicht-gemessen` (offen, 1×), `BEO-TDS/beenden-unter-last-nicht-gemessen`, `BEO-TDS/aussagen-ueber-messung-hinaus` (verkörpert: der Befund gilt nur in der gemessenen Umgebung). Der Befund steht als `BEO-TDS/listener-annahmeschleife-steht-bei-erschoepftem-dateilimit-still` im Register.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
