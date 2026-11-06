# Welle welle-fehler-abbruch — Fehler und Abschlusszustände, Abbruch und Ressourcenfreigabe, Protokoll-Synchronität — Closure-Notiz

> **Zitier-Form** *(bleibt stehen — Norm, kein Ausfüll-Hinweis).* Dieses
> Artefakt friert ein; was es zitiert, bewegt sich weiter. Deshalb: **Kennung,
> nicht Adresse** — `slice-<Kennung>` statt seines Lifecycle-Pfads, `make <target>`
> statt eines Links auf die Sensor-Datei, eine Baseline-Stelle als
> `v<X.Y.Z>` · `regelwerk/<datei>.md` §<Abschnitt> statt als Link
> (Baseline-Regelwerk `grundlagen-harness-dateien.md` §harness/README.md als
> Einstiegspunkt — beim Ausfüllen mit dem adoptierten Tag schreiben).

**Welle:** welle-fehler-abbruch
**Abschluss:** 2026-11-06
**Verantwortlich:** pt9912

## Was wurde geliefert?

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — *was gelernt wurde*: geliefert · was
funktionierte · was anders lief. Mit ID-Bezug, wo es einen gibt.

Alle sechs Slices der Welle liegen in `done/`. Die Aussage der Welle steht an Rohbytes-Tests gegen Fake-Backends, an einem Serverprozess (Kind-JVM über `main`) und an zwei Slices, die am Referenzclient `Microsoft.Data.SqlClient` messen. Sie gilt für den Server dieses Repos, nicht für einen echten SQL Server.

- `slice-fehler-mapping`: Zuordnung jedes Fehlercodes zu ERROR-Nummer, Class, State, Text und Verbindungsfolge (`ErrorMapper`, Tabelle in Abschnitt 4 der Spezifikation); [TDS-FA-06](../../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände).
- `slice-fehler-zustandsuebergang`: die Folge nach einem Fehler (Verbindung offen oder geschlossen, Folgeanfrage mit nur ihrer eigenen Antwort) als Beleg in `DispatchFateTest`, kein neuer Produktivcode; [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) auf der Rohbytes-Ebene.
- `slice-attention-cancel-timeout`: ATTENTION als eigenes Paket, genau ein Cancel, `DONE[ATTN]` allein, `CANCEL_TIMEOUT`, `QUERY_TIMEOUT` mit `TDS_TIMEOUT` (Class 16, Verbindung offen), ATTENTION im Handshake schließt ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Entscheidungen 1 bis 3); [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe).
- `slice-abbruch-ressourcen-shutdown`: Ressourcenfreigabe bei Disconnect, `MAX_CONNECTIONS` samt Test bis zur Grenze, geordnetes Beenden nach `SPEC-044` mit `TDS_SHUTDOWN` und Rückkehr nach höchstens `SHUTDOWN_GRACE` plus `CANCEL_TIMEOUT`; [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität).
- `slice-cancel-referenzclient-szenario`: das Cancel-Szenario von `SPEC-040` am Referenzclient (`Cancel()`, `CancellationToken`, Command-Timeout, `TDS_TIMEOUT` als `SqlException`); [CO-004](../../carveouts/done/CO-004-cancel-szenario-spec-040-fehlt.md) aufgelöst; [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität). Der DoD-Punkt 2 ist auf das Belegte eingeschränkt (Nutzerentscheid 2026-11-06).
- `slice-resetconnection-zuruecksetzen`: `RESETCONNECTION` leert die Handle-Tabelle unter der Sitzungssperre ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Entscheidung 5); Messung mit Pooling am Referenzclient (Stufe 6 von `make e2e`: 1200 Befehle in drei Open-Phasen beantwortet).

**Aussagen der Welle in zwei Spalten** (Closure-Kriterium §3). *Gemessen* heißt: ein bestandener Test oder Lauf belegt es, am Produkt mit Rohbytes-Client, Fake-Backend oder Referenzclient — nie an einem echten SQL Server. *Festlegung* heißt: Setzung des Nutzers oder Lesart ohne Messung am Referenzclient.

| Gemessen (Produkt, nicht echter Server) | Festlegung |
|---|---|
| Fehler nach gesendeten Zeilen: ERROR und DONE mit Fehlerstatus, die Folgeanfrage erhält nur ihre Antwort (`DispatchFateTest`, `SynchronitaetWiringTest`, Referenzclient Stufe 7: `Synchronitaet` 10 von 10) | Class-Werte und Verbindungsfolge je Code (Gruppen 16 offen, 20 schließt); Einzelzuordnung von `TDS_UNSUPPORTED_TYPE`, `TDS_SIM_UNKNOWN_OBJECT`, `TDS_TIMEOUT`, Number 208 mit Class 20 (Spezifikation: „Einzelzuordnung offen“, der echte Server sendet 208 mit Class 16) |
| Fehler einer schließenden Folge (Class 20): Verbindung geschlossen, Folgeanfrage ohne Antwort und ohne Backend | „Nicht abschließbar“ heißt: die Senke nimmt ein Token nicht an; unzulässiger Übergang schließt ohne Antwort |
| ATTENTION im Streaming: `DONE[ATTN]`, genau ein Cancel, Verbindung offen, eine Folgeanfrage (`AbbruchWiringTest`, Referenzclient `Cancel()` mit 50 gelesenen Zeilen, Quelle einmal geschlossen) | Inhalt der Cancel-Antwort als Ein-Nachricht-Form `DONE[ATTN]` allein (Entscheidung 1); jedes weitere ATTENTION wird einzeln bestätigt (`SPEC-029`, ungemessen) |
| `QUERY_TIMEOUT`: `TDS_TIMEOUT` bei offener Verbindung (Wiring-Test, Referenzclient Command-Timeout) | Class 16 und Number 50005 für `TDS_TIMEOUT`; Voreinstellungen 30 s und 5 s |
| Backend ignoriert den Abbruch: Schließen nach `CANCEL_TIMEOUT`, Stop kehrt nach Frist plus `CANCEL_TIMEOUT` zurück (`ShutdownWiringTest`, `ListenerAbortTest`, gesteuerte Uhr; Prozess: `ServerSmokeTest`) | Reale Dauer unter Last ist nicht gemessen (`BEO-TDS/beenden-unter-last-nicht-gemessen`) |
| Abrupter Abbruch und beschädigtes PRELOGIN: Backend-Strom und Sitzung frei, Prozess erreichbar (`ConnectionLeakTest`, `ServerSmokeTest`) | Das Ende der Gegenstelle gilt auch bei halb geschlossener Verbindung als Disconnect (nicht bestätigt) |
| Stop mit laufender Anfrage: Rückkehr innerhalb der Frist, `TDS_SHUTDOWN` für eine spätere Anfrage (`ShutdownWiringTest`; Prozess-Stop in `ServerSmokeTest`) | `DONE[ATTN]` ohne ERROR im Beenden (nicht bestätigt); Abweisung ohne Antwortbytes über `MAX_CONNECTIONS` und Wertebereiche (bestätigt 2026-11-06) |
| `RESETCONNECTION` im ersten Paket: früher vergebene Handles unbekannt, Anfrage beantwortet, Zähler beginnt bei 1 (`ResetConnectionDispatchTest`, Referenzclient mit Pooling, Stufe 6) | Das Bit wirkt nur in `READY`; `0x10` allein setzt nichts zurück (kein Test); der Zähler beginnt nach dem Zurücksetzen bei 1 (Folge: Handle-Aliasing) |

**Das Mehr gegenüber den Slice-DoDs (Closure-Kriterium §3, Wellen-Smoke) — nur zum Teil erfüllt, und zwar so:** Das Kriterium verlangt *einen* Smoke, der in *einem* Lauf über *eine* Verbindung des Rohbytes-Clients gegen den über den Bootstrap-Pfad gestarteten Serverprozess alle acht Fälle fährt. Diesen einen Lauf gibt es nicht. Es gibt die acht Fälle, verteilt: Prozess-Ebene (Kind-JVM über `main`, Rohbytes) tragen nur Stop mit laufender Anfrage, abrupter Abbruch und beschädigtes PRELOGIN (`ServerSmokeTest`); Fehler mit Folgeanfrage, schließende Folge, ATTENTION, ignorierter Cancel und `TDS_TIMEOUT` laufen über echte Sockets gegen einen im Test gestarteten `TdsServer` (`Bootstrap.launch`, Fake-Backend), also ohne eigenen Prozess; `RESETCONNECTION` läuft auf Dispatch-Ebene und mit dem Referenzclient in Stufe 6 gegen den Testserver-Prozess. Die Verteilung ist die Lesart „Vereinigung der Tests“; ob sie das Mehr erfüllt, ist eine Frage an den Nutzer (siehe Frage 1). Diese Closure hat das Kriterium nicht abgeschwächt und keinen Smoke nachgebaut.

## Was hat funktioniert?

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3.

- Der Zuschnitt der Reihenfolge trug: Mapping vor Zustandsübergang vor ATTENTION vor Disconnect und Beenden; `slice-fehler-zustandsuebergang` wendete die vorab genannte Rückführung bewusst nicht an (Begründung in seiner Closure-Notiz). Die Zerlegung von `slice-attention-cancel-timeout` am 2026-11-01 (Drift-Log der Roadmap) hielt jeden Slice bei höchstens drei Liefer-Punkten.
- Die Entscheidung vor dem Bau ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md), fünf Punkte vom Nutzer) machte die Slices baubar; die Nutzerfragen wanderten in die Slices als Risiken mit Ausgang statt als Blocker.
- Mutationen je Slice (Review und Verifikation fuhren sie einzeln) fanden überlebende Mutanten, die ein grüner Lauf nicht zeigte (`slice-fehler-zustandsuebergang`: M3c; `slice-attention-cancel-timeout`: Voreinstellung 5 s gegen 6 s; `slice-abbruch-ressourcen-shutdown`: M1 bis M3 diskriminieren).
- Der Maßstab „Spezifikation ist Zielbild“ (Nutzerentscheid 2026-11-06) entschied die Kollision zwischen Code und Spezifikation in `slice-abbruch-ressourcen-shutdown` ohne Umbau der Spezifikation: der Code folgt, die Spannung bleibt als offene Frage benannt.
- Bei drei Slices (`slice-fehler-zustandsuebergang`, `slice-abbruch-ressourcen-shutdown`, `slice-resetconnection-zuruecksetzen`) stand der Frischbeleg ohne Cache (`make clean-build` bzw. `--no-cache-filter test`) als eigene Zeile im Verifikationsbericht, nicht nur ein gecachter `make gates`.

## Was ging anders als geplant?

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — jede Zeile moeglichst mit der Konsequenz,
die daraus schon gezogen wurde (Folge-Slice, Spec-Version).

- **Das Mehr (Wellen-Smoke) wurde nie als eigener Slice geschnitten.** Die Welle-Datei nennt in §3 einen Smoke über den Bootstrap-Pfad, §4 führt aber keinen Slice, der ihn baut (anders als `slice-anfragefolge-gesamttest` in `welle-anfrageverarbeitung`). Konsequenz: siehe Frage 1; ein Träger ist nicht angelegt.
- **Die Messlücken am echten SQL Server wurden nicht kleiner, sondern sichtbarer.** Die Welle liefert Belege für `BEO-TDS/messung-am-echten-sql-server-fehlt` und `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, keine Messung (so war es in §5 angelegt). Der Stand von `tds-8-0-und-pooling-nicht-gemessen` lautet inzwischen *offen*, nicht *geplant*, wie §3 sagte: kein Träger-Slice für den Rest (siehe Frage 9).
- **Eine Messung am Draht (Mitschnitt, Referenzabgleich) widerspricht einer Setzung dieser Welle.** Der echte Server antwortet auf ATTENTION im Stream mit zwei Nachrichten (`DONE[ERROR]`, dann `DONE[ATTN]`), die Welle baut die Ein-Nachricht-Form `DONE[ATTN]` allein (Ausnahme A-13 in `docs/messungen/referenzabgleich.md`; der Referenzclient nimmt beide Formen an). Das berührt einen Re-Evaluierungs-Trigger von [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) (siehe Trigger-Audit und Frage 3).
- **Der Code folgte in zwei Slices der Spezifikation statt umgekehrt** (Nutzerentscheid 2026-11-06 „Spec ist Zielbild“): `slice-abbruch-ressourcen-shutdown` (Obergrenze, `TDS_SHUTDOWN`) und `slice-cancel-referenzclient-szenario` (DoD-Punkt 2 eingeschränkt). Konsequenz: die Spannung in `SPEC-044` und die Folgepflichten (a), (c) von [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) stehen als offen mit Träger, nicht als geliefert.
- **Die Restrunde von `slice-fehler-mapping` hat kein Verifier nachgeprüft**, das Delta `a7a0a0a..5cc30f8` von `slice-attention-cancel-timeout` ebenso nicht. Beides steht in den Closure-Notizen der Slices; diese Closure prüft es nicht nach.
- **Die Aussage war in fünf Slices breiter als der Beleg** (`AGENTS.md` §3.8 galt, der Review fand es trotzdem; Klasse `BEO-TDS/aussagen-ueber-messung-hinaus`, bereits verkörpert).

## Steering-Loop-Einträge

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 (hier stehen **nur** Beobachtungen, die im
Register 3× erreicht haben; jeder Eintrag nennt seine `BEO-<KUERZEL>/<slug>`) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln (Feld
und Zielort auf **einer** Zeile, Sektionsangabe innerhalb der Backticks; die
**Spec-Lücke** trägt statt `liegt in` ihre `LH-*`-ID — das ist kein Versehen).

**Lese-Schritt des Beobachtungs-Registers.** Gelesen wurden alle Verzeichnisse mit mindestens drei Evidence-Dateien (`BEO-TDS`, Stand 2026-11-06). **Verkörpert wurde mit dieser Closure nichts:** keine Regel, kein Sensor, keine Gate-Zeile, und deshalb trägt kein Eintrag ein Feld `liegt in` (die Anker-Paarung entfällt). Der Grund ist kein Auslassen. Jede neue Verkörperung ändert eine Datei, die diese Closure nicht ändern darf oder soll (`.harness/skills/`, `.claude/commands/`, `AGENTS.md`, `Makefile`-Gates) und verlangt eine Entscheidung des Nutzers (`AGENTS.md` §3.6 für Gates); die Entscheidungen stehen als Fragen unten.

Drei Gruppen:

1. **Schon verkörpert, kein Ausgang nötig** (Anker vorhanden, ein weiterer Beleg ändert nichts): `aussagen-ueber-messung-hinaus` (66 Dateien), `zeile-oder-zweig-ohne-unterscheidenden-test` (20), `offene-punkte-ohne-traeger` (18), `fortschreibung-laesst-zweite-fundstelle-stehen` (18), `test-stage-cache-verdeckt-wiederholung` (10), `abdeckungsmatrix-veraltet-ohne-sensor` (9), `strukturtest-ohne-variation-der-eingabe` (8), `test-belegt-nur-den-fake` (7), `dod-satz-verlangt-ablage-die-der-slice-ersetzt` (7), `risikozeile-nennt-behobenen-zustand` (5), `slice-plan-veraltet-nach-gelieferter-adr` (3); `kennungen-ohne-link-in-neuen-dokumenten` (5) ist gestrichen, weil `make docs-check` die Klasse deckt.
2. **Geplant, mit Träger** (Ausgang steht in `state.md`): `messung-am-echten-sql-server-fehlt` (33; Träger `slice-qa-vergleichslauf-d-messungen`), `rueckstau-im-produktivbetrieb-nicht-gemessen` (9; Träger `slice-qa-performance`), `anmeldepfad-sicherheit-nicht-gebaut` (7).
3. **Über der Schwelle und offen — gelesen, nicht verkörpert, Entscheidung beim Nutzer.** Der Lese-Schritt ist mit dieser Closure vollzogen und in jeder `state.md` vermerkt:

| Klasse (`BEO-TDS/…`) | Evidence | Beitrag dieser Welle | Empfehlung |
|---|---|---|---|
| `tds-8-0-und-pooling-nicht-gemessen` | 20 | `slice-fehler-zustandsuebergang`, `slice-abbruch-ressourcen-shutdown`, `slice-resetconnection-zuruecksetzen` (der geforderte weitere Beleg) | Träger-Slice für den Rest anlegen (siehe Frage 9) |
| `lesart-einer-nutzerentscheidung-unbestaetigt` | 16 | `slice-fehler-mapping`, `slice-abbruch-ressourcen-shutdown` | Nutzerentscheid 2026-11-06 gilt: bei der Welle-Closure lesen, ohne Regel, Sensor oder Träger-Slice; hier gelesen, unverändert |
| `kommentar-nennt-abwesenden-oder-unwirklichen-zustand` | 6 | `slice-abbruch-ressourcen-shutdown` (V-2) | siehe Frage 8 |
| `zusage-ohne-sensor-in-der-ci-kette` | 6 | keiner | kein Eingriff dieser Welle; Entscheidung bei der Welle, die die CI-Kette ändert |
| `zahl-an-mehreren-orten-ohne-sensor` | 5 (Stand-Text sagt 1×) | `slice-attention-cancel-timeout`, `slice-cancel-referenzclient-szenario`, `slice-resetconnection-zuruecksetzen` | Test, der die Orte gegeneinander hält (Vorschlag aus `slice-attention-cancel-timeout`), als Sensor in `make kopplung-zahlen` prüfen |
| `spec-einzelfaelle-ohne-lastenheft-deckung` | 4 | keiner | unverändert beim Nutzer |
| `plan-tabelle-nennt-neue-datei-nicht` | 4 | `slice-cancel-referenzclient-szenario` | Prüfpunkt „Plan-Tabelle gegen Diff“ im Reviewer-Skill (Frage 7) |
| `geheimnis-kopien-im-speicher-nicht-gemessen` | 4 | keiner | unverändert beim Nutzer |
| `spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar` | 3 (Stand-Text sagt 2×) | keiner | Zähler in `state.md` nachgeführt |
| `schranken-test-mit-ungeprueftem-zaehler` | 3 | keiner | unverändert beim Nutzer |
| `festlegung-gilt-nur-fuer-das-clientprofil` | 3 | keiner | unverändert beim Nutzer |
| `eingangsliste-je-stage-von-hand` | 3 (Stand-Text sagt 2×) | keiner | Zähler in `state.md` nachgeführt |
| `dod-haengt-an-offenem-slice-ohne-rueckfuehrung` | 3 | keiner | unverändert beim Nutzer |
| `aussage-ueber-schichtgrenze-ohne-test` | 3 | keiner | Prüfpunkt im Reviewer-Skill oder Sensor (Frage 7) |

**Kein Eintrag über der Schwelle wurde in dieser Closure zur Regel.** Diese Feststellung ist das Ergebnis des Lese-Schritts, nicht sein Auslassen.

Neue Beobachtungen dieser Welle (je 1×, unter der Schwelle, vom Sichtungs-Schritt von `/plan-welle` zu lesen): `BEO-TDS/gebaut-satz-verengt-zusage-der-spezifikation`, `BEO-TDS/shutdown-ruhende-verbindung-endet-vor-tds-shutdown`, `BEO-TDS/batch-anweisungsgrenzen-dem-server-unbekannt`, `BEO-TDS/test-gegenstelle-haelt-ende-bis-zustand-ohne-zeitgrenze`, `BEO-TDS/zustand-nach-abweisung-in-zweitem-sperrfenster-erhoben`, `BEO-TDS/beenden-unter-last-nicht-gemessen`, `BEO-TDS/folgepflicht-in-dod-ohne-messweg`, `BEO-TDS/zeitgrenze-des-tests-nahe-an-ungeklaerter-schwelle`, `BEO-TDS/mutant-faellt-nur-an-der-zeitueberschreitung`, `BEO-TDS/spec-tag-nennt-lastenheft-kennung`, `BEO-TDS/zustandspunkte-nach-fehler-ohne-entscheidung`.

**Benannte Spec-Lücken** (verkörpert in der Spezifikation, kein `liegt in`): (1) `SPEC-044` Nr. 2 gegen Nr. 3 für eine ruhende Verbindung und der Handshake im Beenden, als „Offene Frage an die Spezifikation“ geführt; (2) „Einzelzuordnung offen“ in Abschnitt 4 (Class von `TDS_UNSUPPORTED_TYPE`, `TDS_SIM_UNKNOWN_OBJECT`, `TDS_TIMEOUT`, Number 208); (3) „Jedes weitere ATTENTION wird einzeln bestätigt“ (`SPEC-029`) ohne Client-Messung; (4) das Handle-Aliasing nach dem Zurücksetzen (`SPEC-030`, „Setzung, nicht gemessen“). Nicht in einer versionierten Spezifikation als offen geführt, nur im Register: die drei Zustandspunkte (Sitzungszustand nach Fehler, Kriterium „nicht abschließbar“, Antwort auf unzulässigen Übergang) in `BEO-TDS/zustandspunkte-nach-fehler-ohne-entscheidung`.

## Beobachtungs-Register (Zeiger)

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register — der Zähler wird **nicht** hier gepflegt; diese
Sektion ist ein Zeiger und trägt keine Daten.

Der Zähler steht in [`../observations/`](../observations/).
Was in dieser Welle **3×** erreicht hat und gelesen wurde, steht oben unter
*Steering-Loop-Einträge*.

**Die zwei geforderten Belege (Closure-Kriterium §3):** `BEO-TDS/messung-am-echten-sql-server-fehlt` trägt `evidence/slice-fehler-mapping.md`, `evidence/slice-fehler-zustandsuebergang.md`, `evidence/slice-abbruch-ressourcen-shutdown.md` und `evidence/slice-resetconnection-zuruecksetzen.md`; `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` trägt `evidence/slice-fehler-zustandsuebergang.md`, `evidence/slice-abbruch-ressourcen-shutdown.md` und `evidence/slice-resetconnection-zuruecksetzen.md` (gezählt am Dateisystem, Stand dieser Closure). Beide stehen damit mit mindestens einem weiteren Beleg dieser Welle da; das Kriterium ist erfüllt. Die Aussage „beide stehen mit Stand 3× auf *geplant*“ in §3 der Welle-Datei trifft für `tds-8-0-und-pooling-nicht-gemessen` zum Zeitpunkt der Closure nicht mehr zu (20 Dateien, Stand *offen*); der Zähler folgt aus den Dateien.

## Folge-Slices

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — **derivativ**: Diese Liste zeigt nur,
das Original ist die Slice-Datei. Jeder genannte Folge-Slice muss als Datei im
Planning-Lifecycle existieren; genannt ohne angelegt ist dieselbe Klasse wie
ein halluziniertes Gate.

- `slice-qa-vergleichslauf-d-messungen` (`open/`) — misst am echten Server: Cancel-Antwort (A-13), ATTENTION im Leerlauf, Statusbits bei statement- und batch-abbrechenden Fehlern, Class 21 bis 24, ENVCHANGE bei `RESETCONNECTION`, Herunterfahren; trägt damit die Folgepflichten (a) und (c) von [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) nach dem Mitschnitt.
- `slice-betrieb-zustandsereignisse` (`open/`) — Start-Trigger (`slice-abbruch-ressourcen-shutdown` in `done/`) erfüllt; sein Risiko zu `MAX_CONNECTIONS` führt den gebauten Stand.
- `slice-kompatibilitaetsmatrix-erzeugen` (`open/`) — trägt die Pflege der Stand-Zeile der Kompatibilitätsmatrix (L-2 aus `slice-resetconnection-zuruecksetzen`).
- `slice-qa-performance` (`open/`) — trägt `BEO-TDS/rueckstau-im-produktivbetrieb-nicht-gemessen`; ob er auch das Beenden unter Last (`BEO-TDS/beenden-unter-last-nicht-gemessen`) aufnimmt, ist offen (Frage 6).

Ohne Datei und deshalb **nicht** als Folge-Slice geführt (Vorschläge, jeweils nach einer Entscheidung des Nutzers): Entscheidung zu `SPEC-044` Nr. 2/Nr. 3 und Handshake, Backend-Vertrag mit Anweisungsgrenze, Zähler nach dem Zurücksetzen, Messung des Beenden unter Last.

## Offene Fragen an den Nutzer

Diese Closure trifft keine davon. Jede trägt einen Träger (Register oder Spezifikation) und eine Empfehlung.

1. **Wellen-Smoke (Mehr, §3 der Welle-Datei).** Erfüllt die Vereinigung der Tests (siehe „Was wurde geliefert?“) das Kriterium „ein Lauf, eine Verbindung, Bootstrap-Prozess“, oder wird ein Slice für den einen Smoke verlangt? Träger: diese Notiz. *Empfehlung:* die Lesart „Vereinigung“ bestätigen und §3 der Welle-Datei in einem Nachtrag (Planner) auf die Testnamen je Fall umschreiben (Vorbild: die Anpassung von `welle-simulations-backend` §3 nach [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)); ein Gesamt-Smoke über eine Verbindung wäre ein Slice mit eigenem Fake-Backend und brächte gegenüber `ShutdownWiringTest` und `ServerSmokeTest` nur die Reihenfolge, nicht neue Fälle.
2. **`SPEC-044` Nr. 2 gegen Nr. 3 und Handshake** (`BEO-TDS/shutdown-ruhende-verbindung-endet-vor-tds-shutdown`). Gebaut ist: eine ruhende Verbindung endet beim Beginn sofort, `TDS_SHUTDOWN` erreicht nur Verbindungen mit laufender Anfrage oder halber Nachricht. *Empfehlung:* Nr. 2 behalten, Nr. 3 auf „laufende Anfrage oder halbe Nachricht“ einschränken (die Zusage der Spezifikation wird enger, nicht der Code breiter), Handshake im Beenden: schließen. Folge: ein kleiner Nachzug in der Spezifikation durch den Architect.
3. **Cancel-Antwort: zwei Nachrichten am echten Server (Ausnahme A-13).** Der Re-Evaluierungs-Trigger von [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) („Messung am echten Server, die einer der Setzungen widerspricht“) ist formal eingetreten; die andere Trigger-Bedingung (Referenzclient scheitert) nicht. *Empfehlung:* [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) bleibt unverändert, A-13 bleibt als offene Schuld mit Träger `slice-qa-vergleichslauf-d-messungen`; erst wenn ein Client die Ein-Nachricht-Form nicht annimmt, folgt eine Folge-ADR mit `Supersedes`.
4. **Batch-Anweisungsgrenzen** (`BEO-TDS/batch-anweisungsgrenzen-dem-server-unbekannt`): „von den übrigen Anweisungen eines Batches läuft nach dem Beginn nichts mehr“ (`SPEC-044` Nr. 3) ist nicht gebaut, weil der Server die Anweisungsgrenzen nicht kennt. *Empfehlung:* zuerst als Entscheidung (Backend-Vertrag um eine Anweisungsgrenze, zusammen mit der Klasse des `Failure` aus [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) Entscheidung 4), nicht als Bau.
5. **Zwei Festlegungen im Beenden ohne Bestätigung:** `DONE[ATTN]` ohne ERROR im Beenden und das Ende der Gegenstelle als Disconnect auch bei halb geschlossener Verbindung. *Empfehlung:* beide bestätigen; beide sind an Rohbytes-Tests belegt und widersprechen keiner Messung.
6. **Beenden unter Last** (`BEO-TDS/beenden-unter-last-nicht-gemessen`): die Fristen sind mit gesteuerter Uhr belegt, die reale Dauer nicht. *Empfehlung:* als Messauftrag in `slice-qa-performance` aufnehmen (Rückstau und Beenden sind dieselbe Lastklasse).
7. **Reviewer-Prüfpunkte** für `plan-tabelle-nennt-neue-datei-nicht` (4×) und `aussage-ueber-schichtgrenze-ohne-test` (3×): beide haben die Schwelle erreicht und sind nicht verkörpert. *Empfehlung:* je ein Prüfpunkt in der MEDIUM-Liste von `.harness/skills/reviewer.md` mit Anker `seit welle-fehler-abbruch`; das ändert einen Skill und ist hier nicht getan.
8. **`kommentar-nennt-abwesenden-oder-unwirklichen-zustand` (6×):** der Lese-Schritt war laut `state.md` der Closure von `welle-testserver-manuell` zugewiesen. Diese Closure hat ihn trotzdem gelesen, weil `modul-06-roadmap.md` jede Welle-Closure zum Lesen verpflichtet und `welle-fehler-abbruch` zuerst schließt; ein weiteres Evidence (`slice-abbruch-ressourcen-shutdown`, V-2) liegt in dieser Welle. Verkörpert ist die Regel schon (`AGENTS.md` §3.7); offen ist ein Reviewer-Punkt oder Sensor. *Empfehlung:* kein Sensor (die Klasse ist Urteil, nicht Muster); ein Reviewer-Punkt „Kommentar nennt Abwesendes“ genügt, Entscheidung bei der Closure von `welle-testserver-manuell` belassen, damit der Lese-Schritt nicht zweimal als Ausgang gilt.
9. **Messlücke TDS 8.0 und Pooling:** kein Träger-Slice für den Rest (`BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, 20×, Stand *offen*). *Empfehlung:* den Rest (mehrpaketiges LOGIN7, `RESETCONNECTION` am Draht, TLS 1.2 und andere Suiten, `sp_unprepare`, Paketierung der Antworten) in `slice-qa-vergleichslauf-d-messungen` aufnehmen, soweit der Mitschnitt es nicht schon misst.
10. **Handle-Aliasing nach dem Zurücksetzen:** der Zähler beginnt bei 1 ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Entscheidung 5); ein gehaltenes Handle kann danach auf ein neues zeigen. Ob langlebige vorbereitete `SqlCommand`s das Pooling überstehen, ist ungemessen. *Empfehlung:* bei Entscheidung 5 bleiben, bis eine Messung ein Scheitern zeigt; ein Weiterlaufen des Zählers wäre eine Abweichung und braucht eine neue ADR mit `Supersedes`.
11. **Offene Rechenstände ohne Verifier:** die Restrunde von `slice-fehler-mapping` und das Delta `a7a0a0a..5cc30f8` von `slice-attention-cancel-timeout` sind nie unabhängig verifiziert worden. *Empfehlung:* hinnehmen; die späteren Läufe (`make gates`, `make e2e`) decken das Verhalten, die Mutationen des Deltas fehlen.

12. **Archivierung (Schritt 4).** Wann und von wem läuft sie? *Empfehlung:* nach dem Zusammenführen der parallel bearbeiteten Zweige, im Haupt-Arbeitsbaum, mit dem Auflösen der 16 Verweise auf Review-Reports als eigenem Commit davor; bis dahin gilt die Welle als inhaltlich geschlossen, die Auditierbarkeit steht aus.

## Verifikation

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 1 — keine Behauptung ohne nachprüfbaren
Anker (Hash, Lauf, Zahl).

Läufe dieser Closure, 2026-11-06, Stand `5d1be057` plus die Closure-Dateien, Einzelmessungen auf einem Host mit Last durch andere Läufe:

- `make clean-build` (Jar, Tests, detekt ohne Layer-Cache; Frischbeleg gegen die Cache-Falle der Stage `test`, die `make test` und `make gates` aus dem Cache zeigen): Exit 0 in 496 s, 974 Zeilen `PASSED`, keine rote Zeile, `BUILD SUCCESSFUL` in allen Gradle-Stages.
- `make e2e`: Exit 0, acht Stufen grün, 158 s (Docker-Schichten der Stufen zum Teil aus dem Cache, weil unmittelbar nach `make clean-build` gefahren; Stufe 1 `make smoke` 1 s, Stufe 3 `make simulations-integration` 26 s mit dem Cancel einer laufenden Anfrage, Stufe 6 `Luecken` 19 s mit dem Pooling-Fall zu `RESETCONNECTION`, Stufe 7 `Synchronitaet` 37 s). Die Welle-Datei und die Roadmap sprechen von sieben Stufen; `tools/e2e.sh` führt seit `slice-e2e-backend-ausserhalb-modul` acht.
- `make kompatibilitaet`: Exit 0, Referenzclient gegen den Serverprozess, 12 s (Bau 2 s, Tests 10 s; Cache); die Kategorien `Szenario`, `Anmeldung`, `Luecken`, `Synchronitaet` liefen außerdem als Stufen 4 bis 7 von `make e2e`.
- Der Wellen-Smoke aus §3 wurde damit **nicht als ein Lauf gefahren**, weil es ihn nicht gibt (siehe „Was wurde geliefert?“ und Frage 1); gefahren sind die Tests, die seine acht Fälle tragen, im `make clean-build` (`ServerSmokeTest`, `ShutdownWiringTest`, `AbbruchWiringTest`, `SynchronitaetWiringTest`, `ConnectionLeakTest`, `ListenerAbortTest`, `DispatchFateTest`, `ResetConnectionDispatchTest`) und die Referenzclient-Stufen in `make e2e`.
- Nicht gemessen: Kaltlauf der Docker-Schichten für `make e2e`; die Läufe sind Einzelmessungen auf einem Host mit Last durch andere Läufe, kein Beleg für andere Hosts.
- `make docs-check` und `make gates`: siehe „Archivierung und Roadmap“ (gefahren nach dem Self-Close-Commit und nach dem Move).

Trigger-Audit der Welle (Schritt 2), je Artefaktklasse:

- **Carveouts: 0 offen.** `docs/plan/carveouts/` führt nur `done/CO-001` bis `done/CO-004`; [CO-004](../../carveouts/done/CO-004-cancel-szenario-spec-040-fehlt.md) ist aufgelöst (Trigger a: `slice-attention-cancel-timeout` und `slice-cancel-referenzclient-szenario` in `done/`; Trigger b: das Cancel-Szenario besteht in `make simulations-integration`). Kein Gate schließt mit Carveout.
- **Bootstrap-aware Gates: 0 offen.** Die Sensors-Tabelle in `harness/README.md` führt keine Zeile mit Reifestufe oder `bootstrap via CO-*` außer den Platzhalterzeilen der Vorlage; keine Schwelle ist in dieser Welle gesenkt oder hochgeschaltet worden.
- **Entscheidungen: eine mit eingetretenem Trigger, die übrigen unberührt.** [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md): Trigger „eine Messung am echten Server, die einer der fünf Setzungen widerspricht“ ist durch A-13 (zwei Nachrichten am Draht) formal eingetreten; die Trigger „Referenzclient scheitert“, „ENVCHANGE verlangt“ und „Bit außerhalb von `IDLE`“ sind nicht eingetreten (Stufen 6 und 7 grün). Bestätigt statt Folge-ADR, Frage 3. [ADR-0004](../../adr/0004-session-zustandsautomat.md) und [ADR-0007](../../adr/0007-fehler-und-attention.md): kein Trigger eingetreten (keine neue Recovery-Lücke, keine Desynchronisation in den Läufen); ihre Offen-Listen haben je Punkt einen Ausgang, siehe unten. [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) steht auf `Proposed`; seine Trigger-Bedingung (Messung der RPC-Fehlerform) ist durch den Mitschnitt berührt (A-17, am Server nicht abgespielt), die Annahme ist eine Sache des Nutzers.

**Offene Punkte aus [ADR-0004](../../adr/0004-session-zustandsautomat.md) und [ADR-0007](../../adr/0007-fehler-und-attention.md), je ein Ausgang** (Closure-Kriterium §3):

| Punkt | Ausgang | Beleg |
|---|---|---|
| Cancel und Timeout als Fehler | vom Nutzer entschieden und umgesetzt | [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Entscheidung 2; `slice-attention-cancel-timeout`, `slice-cancel-referenzclient-szenario` |
| Statement- gegen batch-abbrechende Fehler im Strom | weiter offen, Träger als Datei | `slice-qa-vergleichslauf-d-messungen` (Messung), [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) Entscheidung 4, Frage 4 |
| Zuordnung weiterer Codes zu einer Class | teils umgesetzt (Gruppen, `slice-fehler-mapping`), Einzelzuordnung weiter offen | Spezifikation Abschnitt 4 „Einzelzuordnung offen“ |
| ATTENTION im Handshake | vom Nutzer entschieden und umgesetzt | [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Entscheidung 3; `HandshakeMatrix` in `slice-attention-cancel-timeout` |
| Antwort auf unzulässige Übergänge | weiter offen, Träger als Register-Datei | `BEO-TDS/zustandspunkte-nach-fehler-ohne-entscheidung` |
| Inhalt der Cancel-Antwort | vom Nutzer entschieden und umgesetzt; der Draht zeigt die andere Form | [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Entscheidung 1; A-13, Frage 3 |
| `RESETCONNECTION` | vom Nutzer entschieden und umgesetzt (Bit in `READY`) | [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Entscheidung 5; `slice-resetconnection-zuruecksetzen` |
| Sitzungszustand nach Fehler (Transaktionen) | weiter offen, Träger als Register-Datei | `BEO-TDS/zustandspunkte-nach-fehler-ohne-entscheidung` |
| Zeitpunkt des Schließens nach `TDS_SHUTDOWN` | umgesetzt für Verbindungen mit laufender Anfrage; Spannung zu Nr. 2 weiter offen | `slice-abbruch-ressourcen-shutdown`, Frage 2 |
| ATTENTION in jedem Anfragezustand | umgesetzt (Matrix, 150 Zellen) | `slice-attention-cancel-timeout` |

**Ausgeschlossene Gegenstände (§6 der Welle-Datei), je ein Ausgang:** Dispatch und Anfragezustand (`welle-anfrageverarbeitung`, Datei in `planning/`); Backend-Port und Simulations-Backend (Wellen in `done/`); Transport und TLS (Welle in `done/`); Handshake und feste Login-Ablehnung (Welle in `done/`, Byte-Gleichheit im Mapping-Test belegt); Nachweis am echten SQL Server (`slice-qa-vergleichslauf-d-messungen`); Fuzz mit SQL_BATCH in angemeldeter Sitzung (`BEO-TDS/fuzz-nach-verdrahtung-ohne-traeger`, kein Träger-Slice, unverändert); Observability-Adapter (`welle-betrieb-diagnose`); Retry im Server, MARS, garantierte Unterbrechbarkeit: Out-of-Scope der Anforderungen, nicht gewählt.

**Archivierung (Schritt 4)** und **Roadmap (Schritt 6)** stehen im Abschnitt „Archivierung und Roadmap“ unten.

## Archivierung und Roadmap

**Roadmap (Schritt 6).** Die Welle steht in *Abgeschlossene Wellen* mit Zeiger auf diese Notiz; ihr Zeiger und der Block „In Arbeit (Welle welle-fehler-abbruch)“ sind aus *Offene Wellen* entfernt, befördert wurde niemand. Die Welle erfüllt keinen Meilenstein (ihre Datei nennt „kein Meilenstein-Bezug“). Das Drift-Log bekommt keinen Eintrag: eine Schließung ist keine Umplanung.

**Verweise nach dem Move (Schritt 3).** Die Welle-Datei liegt in `done/`; ihre eigenen relativen Pfade und die Verweise aus drei Wellen-Dateien, der Roadmap, zwei Slices in `open/` und drei Slices in `done/` sind nachgezogen, `make docs-check` zeigt 0 Befunde. Der Abschnitt 7 der Welle-Datei trägt den Zeiger auf diese Notiz und auf das Register in der Ruheort-Form (`../observations/`).

**Paarungen (Schritt 3).** (a) Anker: kein Eintrag trägt `liegt in`, die Paarung entfällt. (b) Folge-Slice: `slice-qa-vergleichslauf-d-messungen`, `slice-betrieb-zustandsereignisse`, `slice-kompatibilitaetsmatrix-erzeugen` und `slice-qa-performance` liegen als Dateien in `open/`. (c) Register: jede genannte Kennung `BEO-TDS/<slug>` ist ein Verzeichnis im Register, und jedes Verzeichnis trägt ein nicht leeres `evidence/` (gegen das Dateisystem geprüft).

**Archivierung (Schritt 4): nicht ausgeführt, Bedingung in diesem Arbeitsbaum nicht eingetreten.** `make archive-welle WELLE=welle-fehler-abbruch` meldet in diesem Arbeitsbaum, dass der Träger (`.harness/state/bin/ai-harness-init`, gitignoriert) nicht liegt und das Repo „seine Wellen nicht archiviert“; er liegt im Haupt-Arbeitsbaum. Die vom dortigen Träger gefahrene Vorschau (`ai-harness-init archive-welle --vorschau welle-fehler-abbruch`, schreibt nichts) ergibt: 6 Mitglieder, 2 wellenlose Slices seit der letzten Closure, 0 nach der Grenze, 17 Review-Reports (ohne Stub), 49 Dateien mit Verweisen (darunter vier in `open/` und Evidence-Dateien des Registers), und **eine Sperre `[haenger]`**: 16 Verweise aus 12 Evidence-Dateien zeigen auf Review-Reports, die verschwinden sollen. Der schreibende Lauf bräche ab. Das ist der Zustand, den die Archivierung von `welle-simulations-backend` mit einem eigenen Commit „Links auf Review-Reports gelöst, Vorbedingung der Archivierung“ vor dem Lauf auflöste. Die Archivierung ist **nicht Teil dieser Closure-Commits**, weil sie neben der Welle fremde Dateien in `open/`, `docs/reviews/` und drei Wellen-Dateien umschreibt, die parallel bearbeitet werden; sie läuft als eigener Zug auf dem zusammengeführten Stand: erst die 16 Verweise lösen (Commit), dann `make archive-welle WELLE=welle-fehler-abbruch` im Haupt-Arbeitsbaum (zwei Commits durch den Träger), dann die Feststellungen in dieser Sektion nachtragen. **Bis dahin ist die Welle inhaltlich geschlossen, aber nicht auditierbar geschlossen** im Sinn von Schritt 4 (Frage 12).

**Gate-Läufe nach dem Move und den Link-Nachzügen** (Stand: Commit der Link-Nachzüge `ed78a61b`, 2026-11-06). `make docs-check`: 983 Dateien, 0 Befunde. `make gates`: Exit 0; die Klassen `werkzeug`, `tests` und `stempel` waren bei passendem Beleg übersprungen, weil seit dem Quelltext-Stand nur Dokumente geändert sind, `docs-check` und die Klasse `immer` liefen. `make gates FORCE=1`: Exit 0 in 33 s (68 Docker-Schichten `CACHED`); das belegt Stempel, Host-Selbsttests und Doku, **nicht** frische Gradle-Arbeit — die steht im `make clean-build` oben. Der Quelltext ist gegenüber dem Stand `5d1be057` unverändert.

