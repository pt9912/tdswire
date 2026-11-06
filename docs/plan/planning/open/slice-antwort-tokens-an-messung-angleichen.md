# Slice slice-antwort-tokens-an-messung-angleichen: Antwort-Tokens an die Messung am echten SQL Server angleichen (`curCmd`, `MORE` bei statement-abbrechendem Fehler, RETURNSTATUS, Ordinal der RETURNVALUE)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-referenzabgleich-release

**Bezug:** [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0015](../../adr/0015-spannungen-adr-gegen-spec.md); berührt, ohne sie zu ändern: [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) (`Withdrawn`), [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) (`Accepted`), [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) (`Accepted`)

**Berührte Spec-Stellen:** [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte).a Schritt 2 und 3 (Statusbits, RETURNSTATUS, Ordinal) · SPEC-030 (RETURNSTATUS-Regel, Ordinal der RETURNVALUE, Handle) · SPEC-031 (`DONE_MORE` bei statement-abbrechendem Fehler, `curCmd`) · Abschnitt „Query-Backend (Vertrag zu `SPEC-022`)“ (Festlegungen des Response Sequencers, Herkunft der Befehlskennung, RPC im Backend-Vertrag) · SPEC-045 (Ausnahmeliste: Zeilen A-10 und A-16). Die Spezifikation ändert dieser Slice bei der Umsetzung, nicht der Plan.

**Verantwortlich:** — bis zur Priorisierung (pt9912 trägt sich beim Übergang `open` → `next` ein).

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-06.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Vier Stellen, an denen die Spezifikation und der gebaute Sequenzierer dem Verhalten des echten SQL Servers widersprechen, sind angeglichen: die Befehlskennung `curCmd` der DONE-Familie, `MORE` am `DONE` eines statement-abbrechenden Fehlers im Batch, RETURNSTATUS in jeder RPC-Antwort und das Ordinal der RETURNVALUE; die Spezifikation führt die gemessene Lesart, der Bau folgt ihr, und der Mitschnitt-Lauf belegt es, soweit er es sehen kann.

**Herkunft und Nutzerentscheidung.** Der Mitschnitt-Lauf ([slice-referenzabgleich-mitschnitt](../done/slice-referenzabgleich-mitschnitt.md), Messprotokoll `docs/messungen/referenzabgleich.md`, Abschnitt „Mitschnitt-Lauf“, Ergebnis 1 bis 3 und Ausnahmen A-10, A-16, A-17) hat vier Konflikte der Spezifikation mit der Messung gefunden (Register `BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server`). **Nutzerentscheidung vom 2026-11-06** („deine empfehlungen“; Grundsätze des Nutzers: „Spec ist Zielbild“, der echte SQL Server ist die Referenz): in allen vier Konflikten wird die **Spezifikation dem echten Server angeglichen**, nicht der Server dieses Repos der bisherigen Setzung. Die Empfehlung zum Ordinal lautet zusätzlich, vorher einen zweiten Messfall mit mehreren Ausgabewerten zu messen (bisher ein Fall je Instanz). Lesart der pauschalen Bestätigung: sie bestätigt die Empfehlungen des Planners im Register, nicht mehr (`BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`).

| Nr | Konflikt | Spezifikation heute | gemessen (Einzelmessung je Instanz, 2022 und 2025) | Angleichung (Nutzerentscheidung) |
|---|---|---|---|---|
| 1 | Ordinal der RETURNVALUE | ab 0 in der Reihenfolge der Ereignisse, das Handle von `sp_prepexec` Ordinal 0, ein Ausgabewert des Backends ab 1 | Position in der Aufrufliste: 2 für den dritten Parameter von `sp_executesql`, 0 für das Handle von `sp_prepexec` | Spezifikation: Ordinal = Position des Parameters in der Aufrufliste; **erst nach dem zweiten Messfall** (Messauftrag unten) |
| 2 | `curCmd` der DONE-Familie | 0, Herkunft nicht festgelegt (Spec-Lücke) | `0x00c1` (Anweisung mit Ergebnis), `0x00fd` (Batch ohne Ergebnis, Fehler, ATTN), `0x00e0` (DONEPROC), nie 0 | Spezifikation: der Sequenzierer schreibt die gemessene Kennung je Abschlussart; Herkunft nach der Messung unten |
| 3 | `MORE` bei fehlerhaftem Abschluss | statement-abbrechender Fehler: Variante ohne `MORE` (`Failure` trägt keine Klasse) | statement-abbrechend (Division durch null, danach eine zweite Anweisung): `DONE` mit `MORE\|ERROR`, die zweite Anweisung läuft; batch-abbrechend: `DONE` mit `ERROR` ohne `MORE` | Spezifikation und Bau: `Failure` trägt die Klasse (statement- oder batch-abbrechend), der Sequenzierer schreibt `MORE\|ERROR` bzw. `ERROR` |
| 4 | RETURNSTATUS | ohne `ReturnStatus` des Backends entsteht keiner | RETURNSTATUS 0 bei jedem gemessenen RPC (`sp_executesql` mit und ohne Ausgabewert, `sp_prepexec`) | Spezifikation und Bau: jede RPC-Antwort trägt RETURNSTATUS, Wert 0, wenn das Backend keinen liefert |

**Ergänzung aus der Messreihe am Draht** ([slice-qa-vergleichslauf-d-messungen](../done/slice-qa-vergleichslauf-d-messungen.md), Messprotokoll `docs/messungen/referenzabgleich.md`, Abschnitt „Messreihe am Draht“, die Konflikte dort; Nutzerentscheid 2026-11-06: alle sechs Konflikte des Messprotokolls gehen zu diesem Slice). Die Nummern 1 bis 4 oben bleiben; die Zählung des Messprotokolls (1 Ordinal, 2 `curCmd`, 3 Fehlerabschluss im RPC, 4 RETURNSTATUS und RETURNVALUE im Fehlerfall, 5 ENVCHANGE bei `RESETCONNECTION`, 6 SESSIONSTATE) ist eine eigene; die folgenden vier Zeilen sind neu und nennen die Nummer des Messprotokolls in Klammern:

| Nr | Konflikt | Spezifikation und Bau heute | gemessen (Einzelmessung je Instanz, 2022 und 2025; Messprotokoll) | Angleichung / Träger |
|---|---|---|---|---|
| 5 | ENVCHANGE bei `RESETCONNECTION` (Messprotokoll Konflikt 5) | Antwort ohne ENVCHANGE; die Spezifikation sagt „trägt kein ENVCHANGE“ (Zusage), [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) führt es als offen | W-7: erstes Token der Antwort ist ENVCHANGE Typ 18 mit leerem neuem und leerem altem Wert, bei jeder Wiederverwendung, auch nach `USE`, `SET LANGUAGE`, `DATEFORMAT` | Spezifikation und Bau: Typ 18 leer als erstes Token der Antwort auf eine Anfrage mit `RESETCONNECTION`; [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) bleibt unverändert (`Accepted`, Hard Rule 3.5) |
| 6 | RETURNSTATUS und RETURNVALUE im Fehlerfall (Konflikt 4) | im Fehlerfall ERROR und DONE mit Fehlerstatus, keines von beiden | W-3: RETURNVALUE trotz Fehler (Ordinal 2, Wert 1), RETURNSTATUS vorhanden (Fehlernummer, 0 oder -6 je nach Folge); Ergebnis nach Ausgabewert-Zuweisung steht vor RETURNSTATUS | folgt [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) (`Accepted`, Start-Trigger §4 erfüllt); Spezifikation und Bau folgen der Entscheidung |
| 7 | Fehlerabschluss im RPC (Konflikt 3) | DONE bzw. DONEPROC mit Fehlerstatus, `Failure` beendet die Antwort | W-1 bis W-3: nie DONE im RPC; vor dem ersten Ergebnis `ERROR RETURNSTATUS DONEPROC` (DONEPROC mit `ERROR`), nach einem Ergebnis `DONEINPROC MORE\|ERROR` und DONEPROC ohne Fehlerbit; statement-abbrechend: die Antwort läuft weiter | [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) (`Accepted`, Option A, Nutzerentscheid 2026-11-06, mit den Setzungen „eigene Server-Fehler sind batch-abbrechend“ und „kein RETURNSTATUS bei Fehlern ohne SQL-Server-Nummer“); Spezifikation und Bau folgen |
| 8 | SESSIONSTATE (Konflikt 6) | der Server sendet keines (A-18) | W-8, W-9: Voraussetzung ist die Verhandlung (`ConnectRetryCount=1`, FEATUREEXT `0x01`); nach `SET DATEFORMAT` und `SET LANGUAGE` senden beide Instanzen, nach dem Abbruch (Zustandskennung 254) nur 2022; Ursache des Unterschieds nicht geklärt | **Kein Bau, dokumentierte Lücke** (Nutzerentscheid 2026-11-06, „deine Empfehlungen“: vorerst nicht senden): der Server dieses Repos sendet kein SESSIONSTATE; die Spezifikation führt den Vermerk der Messung und die Grenze. **Re-Evaluierungs-Trigger:** ein Treiber oder Pool zeigt Bedarf (Sitzungswiederaufnahme scheitert oder wird gebraucht). Die Ursache des Instanzunterschieds nach dem Abbruch (nur 2022, Zustandskennung 254) bleibt ohne Träger |

**Nutzerentscheidungen vom 2026-11-06** (alle mit „deine Empfehlungen“; Lesart wie oben: sie bestätigt die Empfehlungen des Planners, nicht mehr):

1. Die Welle dieses Slice bleibt `welle-referenzabgleich-release`.
2. Name des Handle-RETURNVALUE von `sp_prepexec` (gemessen ohne Namen, Spezifikation und Server dieses Repos führen `@handle`; „fünfter Widerspruch“): gehört zur Antwortform und liegt in [slice-handle-freigabe-und-grenze-bauen](../done/slice-handle-freigabe-und-grenze-bauen.md) (Liefer-Punkt 1). Dieser Slice führt dazu nur die Abgrenzung.
3. `curCmd` für INSERT, UPDATE, DELETE und DONEINPROC: keine Aussage, bis gemessen; der Messauftrag liegt in [slice-qa-vergleichslauf-d-messungen](../done/slice-qa-vergleichslauf-d-messungen.md) (Liefer-Punkt 1).
4. Herkunft von `curCmd` im Sequenzierer, aus der Form des Ereignisses abgeleitet und ohne Änderung des Backend-Ports, und `Failure` mit Klasse sind bestätigt. **Kante zur öffentlichen Fläche:** `Failure` ist Teil der Fläche von `SPEC-059`; ein neuer Name oder ein neues Mitglied braucht im selben Bau-Commit-Satz einen Eintrag in `SPEC-059`, sonst meldet der Flächen-Sensor aus [slice-oeffentliche-flaeche-verkleinern-und-belegen](../done/slice-oeffentliche-flaeche-verkleinern-und-belegen.md) den Unterschied.
5. Grenze des Mitschnitt-Laufs bestätigt: am Server sichtbar sind nur `curCmd` und RETURNSTATUS; Ordinal und `MORE` belegen Tests mit Handvektor aus dem Mitschnitt.

**Kante zu [slice-handle-freigabe-und-grenze-bauen](../done/slice-handle-freigabe-und-grenze-bauen.md) (nicht doppelt belegen).** Der RETURNSTATUS auf `sp_prepexec` (Wert 0 vor dem RETURNVALUE `@handle`) gehört **dorthin** (Liefer-Punkt 1, Antwortform von `sp_prepexec`, [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md)). Dieser Slice baut die allgemeine Regel für die übrigen RPC-Antworten (gemessen: `sp_executesql`); er schreibt den Pfad von `sp_prepexec` nicht und prüft ihn nicht mit einem eigenen Test. Liegt der Handle-Slice zuerst in `done/`, trägt er die Regel dort schon für `sp_prepexec`, und die Spezifikationsänderung zu Konflikt 4 übernimmt sie als Sonderfall; liegt dieser Slice zuerst in `done/`, bleibt `sp_prepexec` in der Regel ausgenommen, bis der Handle-Slice sie baut. Die Reihenfolge wird nicht erzwungen, nur die Zuständigkeit. Dasselbe gilt für Name und Ordinal des Handle-RETURNVALUE: der Name liegt dort (Nutzerentscheidung 2), das Ordinal 0 des Handles nennt Konflikt 1 nur als unverändert.

**Zu [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) (`Withdrawn`), [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) (`Accepted`) und [ADR-0028](../../adr/0028-referenzinstanz-fuer-tds-8-0.md) (`Proposed`).** Konflikt 3 berührt den dritten Teil der zurückgezogenen [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) (statement- gegen batch-abbrechende Fehler). Die Frage nach dem Abschluss einer RPC-Antwort im Fehlerfall führt jetzt [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) (`Accepted`, Option A, Nutzerentscheid vom 2026-11-06). Dieser Slice ändert keine ADR und stützt sich für die Konflikte 3 und 4 auf die Messung und die Nutzerentscheidung vom 2026-11-06, für die Konflikte 6 und 7 (Fehlerabschluss im RPC) erst auf die angenommene [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) (Start-Trigger in §4). [ADR-0028](../../adr/0028-referenzinstanz-fuer-tds-8-0.md) (zweite Instanz `2025-latest`) bleibt unberührt: die Messung stammt von beiden Instanzen, der Nachzug der ADR liegt bei [slice-referenzabgleich-adr-0028-nachzug](slice-referenzabgleich-adr-0028-nachzug.md).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- RETURNSTATUS auf `sp_prepexec` und die Antwortform des Handles (Name, Ordinal) — [slice-handle-freigabe-und-grenze-bauen](../done/slice-handle-freigabe-und-grenze-bauen.md); doppelte Belegung wäre ein Konflikt zweier Slices an derselben Stelle des Sequenzierers (Kante oben).
- Ausgabewerte und mehrere Anweisungen je Text im Simulations-Backend (A-16 zweiter Teil, A-17 Grund „außerhalb der erkannten Formen“) — der Bau der Backend-Seite hat keinen Träger (`BEO-TDS/fa-10-mehrteilige-antwort-ohne-backend-traeger`, Matrix Lücke 16; Folge-Slice „Backend liefert mehrere Anweisungen je Text und Ausgabewerte“ ohne Kennung); deshalb kann der Mitschnitt-Lauf Konflikt 3 und 1 nicht am Server abspielen, sondern nur Konflikt 2 und 4 (Sensor-Grenze unter §2).
- Die Entscheidung über [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) (Abschluss einer RPC-Antwort im Fehlerfall) — der Nutzer; die Messung dazu liegt vor (W-1 bis W-3 in `docs/messungen/referenzabgleich.md`, geliefert von [slice-qa-vergleichslauf-d-messungen](../done/slice-qa-vergleichslauf-d-messungen.md)). Der Bau der Konflikte 6 und 7 folgt der Entscheidung, nicht der Messung allein.
- Der Bau der Cancel-Zwei-Nachrichten-Form (A-13) und die Typlängen (A-11, A-12) — [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) bzw. [slice-codec-datentypen-erweiterung](slice-codec-datentypen-erweiterung.md); andere Gegenstände der Ausnahmeliste.
- Änderungen an `spec/lastenheft.md`: [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte) verlangt Antwortformen „in korrekter Reihenfolge einschließlich Abschluss- und Rückgabewerten“; die Angleichung füllt diese Zusage, ändert sie nicht.

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

Liefer-Punkte (drei):

- [ ] **Spezifikation angleichen** (Nutzerentscheidung vom 2026-11-06; erst nach dem zweiten Messfall, Start-Trigger): Die Spezifikation führt die gemessene Lesart der vier Konflikte, jeweils mit Messzeile (Instanz, Digest, Szenario) und der Grenze „ein Fall je Instanz“, wo sie gilt. **Spec-Änderungsliste:** (a) [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte).a Schritt 3 und SPEC-030 („Handle … vor dem ersten Ausgabewert“, Zählung der Ausgabewerte): Ordinal = Position in der Aufrufliste, mit dem zweiten Messfall belegt, Handle-Ordinal unverändert 0; (b) SPEC-030 und Backend-Vertrag („RPC im Backend-Vertrag“, „Ohne RETURNSTATUS und RETURNVALUE …“): RETURNSTATUS 0 in jeder RPC-Antwort ohne Rückgabestatus des Backends; (c) SPEC-031 und „Festlegungen des Response Sequencers“: `DONE_MORE|DONE_ERROR` bei statement-abbrechendem Fehler, `Failure` mit Klasse, Streichung des Satzes „schreibt der Sequenzierer die Variante ohne `DONE_MORE`“; (d) SPEC-031 und „Herkunft der Befehlskennung“: Wert je Abschlussart, Herkunft im Sequenzierer (Setzung des Planners: aus der Form des Ereignisses abgeleitet, ohne Änderung des Backend-Ports; wo nur ein Wert gemessen ist, nur dieser, sonst bleibt die Lücke benannt); (e) SPEC-045: Zeilen A-10 und A-16 (Teil RETURNSTATUS und Ordinal) in der Beschreibung des Messprotokolls nachgezogen; (f) Nr 5 bis 8 (ENVCHANGE bei `RESETCONNECTION`, RETURNSTATUS und RETURNVALUE im Fehlerfall, Fehlerabschluss im RPC, SESSIONSTATE): Nr 5 sofort, Nr 6 und 7 nach [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) `Accepted`, Nr 8 ist kein Bau (Lücke, Entscheid des Nutzers 2026-11-06), nur Messvermerk und Grenze in der Spezifikation. Der Messauftrag liegt in [slice-qa-vergleichslauf-d-messungen](../done/slice-qa-vergleichslauf-d-messungen.md) (Liefer-Punkt 1).
- [ ] **Sequenzierer baut die gemessene Lesart** *(Bibliothek)*: `curCmd` je Abschlussart (`0x00c1`, `0x00fd`, `0x00e0`, weitere nur, wenn gemessen); `Failure` trägt die Klasse (statement- oder batch-abbrechend), `MORE|ERROR` bzw. `ERROR` am fehlerhaften Abschluss, die Folgeanweisung läuft nach einem statement-abbrechenden Fehler; RETURNSTATUS 0 in der Antwort auf jeden RPC außer `sp_prepexec` (Kante oben); RETURNVALUE mit dem Ordinal der Position in der Aufrufliste; das Flag-Feld des Handle-RETURNVALUE von `sp_prepexec` `0x0000` wie am echten Server (Regel A-19 des Messprotokolls; der Bau von [slice-handle-freigabe-und-grenze-bauen](../done/slice-handle-freigabe-und-grenze-bauen.md) sendet `0x0001`; der Lauf liest das Feld nicht, der Beleg ist ein Handvektor-Test); ENVCHANGE Typ 18 leer als erstes Token nach `RESETCONNECTION` (Nr 5); Nr 6 und 7 nach der Entscheidung über [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md); SESSIONSTATE (Nr 8) wird nicht gebaut (Lücke; Re-Evaluierungs-Trigger in Nr 8). Tests nach Happy/Boundary/Negative mit Tag `@spec`, Handvektoren aus den Mitschnitten (Herkunft: Datei, Instanz, Digest), Variation der Eingabe (mehrere Parameterpositionen, mehrere Ausgabewerte, Fehler vor und nach Zeilen); bestehende Tests, die die alte Setzung festschreiben, werden mit Begründung geändert, nicht gelöscht.
- [ ] **Nachweis am Mitschnitt-Lauf und in der Matrix:** `make vergleichslauf-d LAUF=mitschnitt` ist grün, und die Regel A-10 (`curCmd`) ist aus `referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv` und aus der Ausnahmeliste des Messprotokolls entfernt (das Abspielen sieht `curCmd` jedes Austauschs); die Regel A-16 ist in ihrem Teil RETURNSTATUS gekürzt, soweit der Server ihn nun schreibt, und behält als Lücke den Ausgabewert des Backends (Grund: Backend). Konflikt 3 und das Ordinal sieht der Lauf am Server nicht (A-17 und A-16 bleiben aus dem Grund „außerhalb der erkannten Formen“ bzw. „Backend liefert keinen Ausgabewert“); ihr Beleg sind die Tests des zweiten Punkts mit Handvektor aus dem Mitschnitt, und das Messprotokoll nennt diese Grenze. Kompatibilitätsmatrix (`docs/plan/kompatibilitaetsmatrix.md`): Zeilen zu RETURNSTATUS, RETURNVALUE, DONE-Familie auf den neuen Stand, `make matrix-zitate` grün.

Nachweis-Pflichten für alle drei Punkte:

- [ ] Jede Aussage steht an einem Test, einem Mitschnitt oder ist als Festlegung gekennzeichnet; die Zuordnung *Messung | Handvektor | Festlegung* je Aussage steht in der Closure-Notiz (`BEO-TDS/aussagen-ueber-messung-hinaus`, `AGENTS.md` §3.8).
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist (die Antwortform der Bibliothek ist Teil des Vertrags mit dem Client; Nutzer-Doku und Matrix).
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
| `spec/spezifikation.md` ([TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte).a, SPEC-030, SPEC-031, Query-Backend-Vertrag, SPEC-045) | update | Spec-Änderungsliste (a) bis (e) in §2 |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/response/` (`ResponseSequencer.kt`, `ResponseRun.kt`, `RpcOutputs.kt`) und `protocol/token/` (`Token.kt`, `TokenWriters.kt`) | update | `curCmd` je Abschlussart, `MORE`/`ERROR` je Klasse, RETURNSTATUS-Regel, Ordinal der RETURNVALUE |
| Backend-Port (`QueryEvent.Failure`) | update | `Failure` trägt die Klasse des Fehlers; die Fläche ist öffentlich (`SPEC-059`: neuer Name oder neues Mitglied mit Eintrag dort, Nutzerentscheidung 4), die Änderung ist rückwärtsverträglich zu halten (Voreinstellung = batch-abbrechend) oder als inkompatible Änderung auszuweisen (Entscheidung beim Implementer, im Review geprüft) |
| `src/test/` (Sequenzierer) und `testserver/` | update / neu | Happy/Boundary/Negative je Konflikt nach [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität) und [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), Tag `@spec` |
| `referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv`, `docs/messungen/referenzabgleich.md` | update | A-10 entfällt, A-16 gekürzt, Grenze des Abspielens benannt |
| `docs/plan/kompatibilitaetsmatrix.md` | update | Zeilen zu RETURNSTATUS, RETURNVALUE, DONE-Familie; `make matrix-zitate` |

**Optional: Ansatz als Liste, wenn eine Zeile pro Datei nicht trägt** — z. B.
eine Schnittstellenänderung über viele gleichrangige Dateien mit derselben
Begründung, oder ein Ansatz, der sich nicht auf eine Datei herunterbrechen
lässt. Ergänzt die Tabelle, ersetzt sie nicht:

- Reihenfolge: erst Spezifikation (Liefer-Punkt 1), dann Bau (2), dann Nachweis (3); die Spezifikation wird in einem eigenen Commit geändert, der Bau folgt ihr.
- Der Sensor ist der Mitschnitt-Lauf (`make vergleichslauf-d LAUF=mitschnitt`, nur lokal, EULA vom Nutzer bestätigt am 2026-11-06): vor dem Bau rot an A-10 gemessen, nach dem Bau grün ohne die Regel.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-qa-vergleichslauf-d-messungen](../done/slice-qa-vergleichslauf-d-messungen.md) hat den Messauftrag aus Liefer-Punkt 1 erledigt (Ordinal der RETURNVALUE mit mehreren Ausgabewerten gemessen, `curCmd` für Abschlüsse mit Zeilenzahl gemessen; ohne diese Messung bliebe das Ordinal im Slice eine Setzung, und der Bau von Punkt 2 stützte sich auf einen Fall je Instanz), die Nutzerentscheidung vom 2026-11-06 („Spec dem echten Server angleichen“) ist erteilt, und ein Verantwortlicher ist im Kopf eingetragen. Der Ordinal-Messfall liegt vor (W-5). **Zusätzlicher Start-Trigger für Nr 6 und 7:** [ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) ist `Accepted` (Entscheidung des Nutzers; bis dahin ist der Teil nicht startbar, die übrigen Nummern sind es). Nr 8 (SESSIONSTATE) hat keinen Bau (Lücke; Entscheid des Nutzers 2026-11-06). Konflikt 2, 3 und 4 sind unabhängig vom Ordinal gemessen; der Implementer darf Punkt 1 und 2 für sie vorziehen und das Ordinal zuletzt bauen, wenn der Messauftrag noch läuft.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): `Failure` mit Klasse zwingt zu einer Änderung der Fläche des Backend-Ports, die mehr als den Sequenzierer berührt; Schnittvorschlag: Konflikt 3 als eigener Slice, Konflikte 1, 2 und 4 als dieser.
- `in-progress` → `open` (blockiert — Carveout?): der Messauftrag liefert für das Ordinal ein Ergebnis, das der Bauart des Ports widerspricht (der Port trägt keine Position in der Aufrufliste), oder die Instanzen weichen voneinander ab; der Nutzer entscheidet dann über die Lesart.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün, `make vergleichslauf-d LAUF=mitschnitt` grün ohne die Regel A-10, der Review-Report liegt vor, und die Closure-Notiz trägt je Konflikt (Ordinal, `curCmd`, `MORE`, RETURNSTATUS) die Zuordnung *Messung | Handvektor | Festlegung*, die Grenzen (zwei Instanzen, Digests, Client, TDS 7.4 mit `Encrypt=false`) und den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **`curCmd` jenseits der gemessenen Werte.** Gemessen sind drei Werte (Anweisung mit Ergebnis, Batch ohne Ergebnis oder Fehler oder ATTN, DONEPROC). Für INSERT, UPDATE, DELETE und DONEINPROC gilt keine Aussage, bis gemessen (Nutzerentscheidung 3, `AGENTS.md` §3.8); der Messauftrag liegt im Messslice. — **Ausgang:** entfallen als Frage (entschieden); der Bau schreibt für diese Abschlüsse keinen Wert, den die Messung nicht trägt.
- **Name des Handle-RETURNVALUE.** Entschieden (Nutzerentscheidung 2): Gegenstand von [slice-handle-freigabe-und-grenze-bauen](../done/slice-handle-freigabe-und-grenze-bauen.md). — **Ausgang:** entfallen (anderer Träger).
- **Der Mitschnitt-Lauf sieht nur zwei der vier Konflikte am Server.** Konflikt 1 und 3 sind am Server dieses Repos nicht abspielbar (Backend, erkannte Anweisungsformen); ihr Beleg sind Tests mit Handvektor, nicht der Vergleich. Die Aussage „gleich wie die Referenz“ gilt dafür nicht (Nutzerentscheidung 5). — **Ausgang:** offen bis zur Closure.
- **`Failure` mit Klasse ändert die öffentliche Fläche** (`SPEC-059`; Nutzerentscheidung 4; `BEO-TDS/oeffentliche-flaeche-der-telemetrie-ohne-entscheidung` zeigt die Familie; die Fläche schneidet [slice-oeffentliche-flaeche-verkleinern-und-belegen](../done/slice-oeffentliche-flaeche-verkleinern-und-belegen.md)). Der Eintrag in `SPEC-059` gehört zum Bau; fehlt er, meldet der Flächen-Sensor. — **Ausgang:** offen bis zur Closure.
- **Nr 6 und 7 folgen einer Entscheidung des Nutzers** ([ADR-0038](../../adr/0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) `Accepted`, 2026-11-06); Nr 8 (SESSIONSTATE) ist entschieden als „vorerst nicht senden“, dokumentierte Lücke mit Re-Evaluierungs-Trigger „Treiber oder Pool zeigt Bedarf“. — **Ausgang:** entfallen als Frage (entschieden).
- **Setzung über die Entscheidungen hinaus.** Die Herkunft von `curCmd` und `Failure` mit Klasse sind bestätigt (Nutzerentscheidung 4); als Setzung bleibt die Rückwärtsverträglichkeit von `Failure` (Voreinstellung batch-abbrechend oder ausgewiesene inkompatible Änderung). — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` führt `*` als einzige Sub-Area; der Slice berührt Bibliothek (Sequenzierer), Spezifikation, Testwerkzeug und Dokumentation, keine dieser Strukturen trägt eine eigene Strukturregel neben dem Repo insgesamt. `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, Stand des Worktrees auf `main` `5d1be057`, 2026-11-06; Namensliste, Texte der Treffer gelesen). Treffer:

- `BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server` — der Gegenstand; Stand wird mit diesem Slice von *offen* auf *Träger entschieden* fortgeschrieben.
- `BEO-TDS/mitschnitt-luecken-ohne-traeger` — A-10 und der RETURNSTATUS-Teil von A-16 sind hier getragen; A-11, A-15, A-18 haben andere Träger.
- `BEO-TDS/done-more-statement-abbrechender-fehler-weicht-ab` — Konflikt 3; Bau-Träger ist dieser Slice.
- `BEO-TDS/fa-10-mehrteilige-antwort-ohne-backend-traeger` — Grenze des Nachweises am Server (Backend-Seite ohne Träger).
- `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` — die pauschale Bestätigung vom 2026-11-06 und die Lesart „Spec angleichen“.
- `BEO-TDS/aussagen-ueber-messung-hinaus` — verkörpert (`AGENTS.md` §3.8): ein Fall je Instanz trägt das Ordinal nicht; deshalb der zweite Messfall.
- `BEO-TDS/gebaut-satz-verengt-zusage-der-spezifikation` — die Änderung der Spezifikation darf die Zusage nicht enger oder weiter fassen als die Messung.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — Kennungen in Slice und Closure-Notiz als Link.

**Modus-Begründungsblock.** Alle berührten Sub-Areas GF.
