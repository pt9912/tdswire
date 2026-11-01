# Slice slice-resetconnection-zuruecksetzen: Zurücksetzen: RESETCONNECTION leert die Handle-Tabelle, Messung mit Pooling am Referenzclient

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-fehler-abbruch

**Bezug:** [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md), [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md), [ADR-0004](../../adr/0004-session-zustandsautomat.md), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)

**Berührte Spec-Stellen:** SPEC-001 · SPEC-030 · SPEC-057 · SPEC-058 · SPEC-004

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-11-01.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Das Statusbit `RESETCONNECTION` (`0x08`) im ersten Paket nach der Wiederverwendung einer gepoolten Verbindung setzt den Sitzungszustand zurück, mindestens die Handle-Tabelle der Prepared-Handles ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Entscheidung 5): unter der Sitzungssperre, vor der Verarbeitung der Anfrage, die das Bit trägt; ein früher vergebenes Handle ist danach unbekannt (`TDS_UNKNOWN_HANDLE`, 50009), Zähler und Grenze von 1000 ([ADR-0017](../../adr/0017-prepared-handles-grenzwert.md)) beginnen neu. Am Referenzclient mit Pooling wird gemessen, ob das Bit ankommt und ob die gemessene Erschöpfung (Kompatibilitätsmatrix, Zeile 13 der bekannten Lücken: ohne `sp_unprepare` endet das 1001. Prepare mit 50010) damit entfällt; die Messung kehrt diese Aussage um, wenn sie so ausfällt, und bleibt sonst als Negativbefund stehen.

**Zuordnung zur Welle (Setzung des Planners).** Der Slice liegt in `welle-fehler-abbruch`, nicht in `welle-anfrageverarbeitung`: Die Handle-Verwaltung ist dort geliefert, alle Closure-Slices ihrer Tabelle liegen in `done/`, und ein neuer Slice dort verzögerte ihren Abschluss; das Zurücksetzen ist dagegen Teil des Beschlusses „Abbruch und Zurücksetzen“ ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md)), hängt am selben Sitzungsrahmen und an derselben Sitzungssperre wie der Abbruchpfad ([slice-attention-cancel-timeout](../done/slice-attention-cancel-timeout.md)) und gehört zur Ressourcenfreigabe von [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe). `TDS-FA-07` und die Welle tragen damit auch das Zurücksetzen; die Welle-Datei führt den Slice in ihrer Tabelle.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Der Abbruchpfad (ATTENTION, Cancel, Fristen) — [slice-attention-cancel-timeout](../done/slice-attention-cancel-timeout.md); anderer Auslöser, anderer Übergang. Beide Slices ändern den Sitzungsrahmen (Risiko in §6).
- Das Cancel-Szenario am Referenzclient — [slice-cancel-referenzclient-szenario](../open/slice-cancel-referenzclient-szenario.md); dort läuft der Lauf ohne Pool, hier mit.
- Die Messung des ENVCHANGE im ersten Antwortpaket am echten SQL Server (Teil (a) der Messung in [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md)) — braucht einen echten SQL Server mit Mitschnitt; Träger ist `slice-qa-vergleichslauf-d-messungen` (Liefer-Punkt 3, Planung 2026-11-01), der Container kommt aus `slice-qa-vergleichslauf-d`. Die Messung hängt nicht an Teil (b): sie läuft dort unabhängig davon, ob die Folgeabfrage nach dem Zurücksetzen ohne ENVCHANGE am Referenzclient gelingt (Teil (b), in Punkt 2 dieses Slice). Bis ihr Ergebnis vorliegt, ist die Antwort ohne ENVCHANGE eine Setzung der Entscheidung 5, nicht gemessen.
- `RESETCONNECTION` in einem anderen Zustand als `IDLE` — von [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) nicht entschieden (akzeptiertes Negativ: der Referenzclient setzt das Bit nur im ersten Paket nach `Open()`, R18, R19); der Slice baut dafür nichts und benennt im KDoc, was der Code tut.
- Weiterer Sitzungszustand jenseits der Handle-Tabelle (Transaktionen sind nicht im Katalog, `SPEC-030`) — wer ihn einführt, setzt ihn an dieser Stelle zurück, ohne neue Entscheidung ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md)).
- Das Freigeben einzelner Handles durch `sp_unprepare` — nicht im Katalog von [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md); `sp_unprepare` ergibt 50002 (`slice-rpc-prepared-handles`).
- Ein Ereignis oder eine Metrik für das Zurücksetzen (Zahl verworfener Handles) — Namen und Felder legt die Observability-Entscheidung ([ADR-0020](../../adr/0020-logformat-und-observability-schnittstelle.md)) und die Welle `welle-betrieb-diagnose` fest.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [x] **`RESETCONNECTION` im Sitzungsrahmen.** Trägt die erste Nachricht in `IDLE` das Bit `0x08` (`TdsMessage.resetConnection` trägt es im Quelltext schon), leert der Rahmen unter der Sitzungssperre und vor der Verarbeitung der Anfrage die Handle-Tabelle; die Anfrage mit dem Bit wird danach wie jede andere beantwortet. Rohbytes-Tests mit Fake-Backend: ein früher vergebenes Handle ergibt nach dem Zurücksetzen 50009 bei offener Verbindung; der Zähler beginnt bei 1; die Grenze von 1000 gilt neu (nach 1000 gehaltenen Handles und dem Zurücksetzen gelingen wieder 1000); ohne das Bit bleiben die Handles gültig; eine Anfrage mit dem Bit und einem `sp_prepexec` liefert das neue Handle. Eine Wächter-Mutation (das Leeren entfernt) macht mindestens einen Test rot. ([TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) Ressourcenfreigabe, [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), `SPEC-030`, `SPEC-057`) Wie das Bit vom Transport in den Sitzungsrahmen kommt, ist im Bau zu prüfen und im KDoc festzuhalten ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md): „ob der Codec es heute weitergibt, ist hier nicht geprüft“).
- [x] **Messung mit Pooling am Referenzclient** (Stufe 6 des Produkt-E2E, `make kompatibilitaet`, Kategorie `Luecken`, Testserver `KompatibilitaetsServer.kt`): `Microsoft.Data.SqlClient` mit `Pooling=true` und einer physischen Verbindung (`Max Pool Size=1`, ein einziges LOGIN7 im Log des Testservers). Gemessen werden (a) ob das Bit im ersten Paket nach `Open()` ankommt, beobachtet über seine Wirkung (über mehrere `Close()`/`Open()`-Zyklen derselben physischen Verbindung gelingen mehr als 1000 vorbereitete Befehle ohne 50010) und, falls der Testserver einen Zähler führt, über diesen; (b) die Gegenprobe: innerhalb **einer** Open-Phase endet das 1001. Prepare weiter mit 50010; (c) dass die Folgeabfrage nach dem Zurücksetzen ohne ENVCHANGE in der Antwort gelingt (Teil (b) der Messung in [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md)). Die Ergebnisse stehen in der Kompatibilitätsmatrix: Zeile 13 der bekannten Lücken und die Zeile `Prepare` werden berichtigt, die Aussage „nach 1000 Prepare endet das 1001. mit 50010“ erhält ihre Grenze („innerhalb einer Open-Phase“) oder bleibt, wenn das Bit nicht ankommt. Eine Messung, die das Bit nicht zeigt, ist ein Ergebnis (Negativbefund) und wird als solches eingetragen. ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)) **E2E-Stufe:** Stufe 6 trägt die Kennungen [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität) in `tools/e2e.sh`; `make e2e-abdeckung` zeigt sie danach in `docs/plan/e2e-abdeckung.md`.
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist. Der Spec-Nachzug nach [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) (Folgepflicht, Spec-Änderung durch den Implementer dieses Slice, nicht durch den Planner) gehört dazu und zählt nicht als Liefer-Punkt (Setzung des Planners: er folgt wortgleich der Entscheidung): (e) `SPEC-001`, nur der Teil „`RESETCONNECTION` setzt zurück“ (der Teil „ATTENTION im Handshake schließt“ liegt in [slice-attention-cancel-timeout](../done/slice-attention-cancel-timeout.md)); (f) `SPEC-030`: die Handle-Tabelle wird bei `RESETCONNECTION` geleert, Zähler und Grenze beginnen neu; (i) Zeile 13 der bekannten Lücken in der Kompatibilitätsmatrix, soweit sie `RESETCONNECTION` offen führt; dazu die Stelle zu Pooling und `RESETCONNECTION` unter den offenen Freigabepunkten des Clientprofils (`SPEC-004`), die erst nach der Messung geändert wird (Register `BEO-TDS/spec-nachzug-nach-messung-offen`), und der KDoc von `PreparedHandles` (Ende eines Handles).
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
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RequestLoop.kt` | update | Bit lesen, unter der Sitzungssperre vor der Anfrage zurücksetzen (Aktion am Übergang, kein Zustand, [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md)) |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/PreparedHandles.kt` | update | Zurücksetzen der Tabelle samt Zähler (`clear` besteht schon, der Zähler beginnt neu) |
| `src/main/kotlin/io/github/pt9912/tdswire/transport/framing/` | prüfen, ggf. update | `TdsMessage.resetConnection` ist gesetzt (`MessageAssembler`); die Weitergabe an den Sitzungsrahmen ist zu prüfen |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/` | neu | Rohbytes-Tests: frühes Handle 50009, Zähler neu, Grenze neu, ohne Bit unverändert ([TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)); Wächter-Mutation |
| `referenztest-dotnet/Kompatibilitaet/LueckenTests.cs` (oder eine neue Datei zu Pooling) | update | Referenzclient mit `Pooling=true`: Open/Close-Zyklen, mehr als 1000 Prepare, Gegenprobe in einer Open-Phase |
| `src/test/kotlin/io/github/pt9912/tdswire/runtime/KompatibilitaetsServer.kt` | update | eine physische Verbindung belegen (ein LOGIN7 im Log), ggf. ein Zähler für das Bit (Test-Quelltext, kein Produkt) |
| `tools/e2e.sh`, `tools/kompatibilitaetstest.sh` | update | Kennungen und Text der Stufe 6 |
| `spec/spezifikation.md`, `docs/plan/kompatibilitaetsmatrix.md` | update | Doku-Update in §2 |

Ergänzt im ersten Lauf des Implementers (Ist-Zustand gegen den Plan gemessen: `TdsMessage.resetConnection` kommt über `MessageAssembler` und `TransportHandshakeChannel` unverändert beim Sitzungsrahmen an, am Transport ist nichts zu ändern; der Rahmen wertet das Bit nicht in `RequestLoop` aus, sondern im Automaten, weil dort der Übergang liegt, der die Anfrage annimmt):

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/session/HandshakeAction.kt`, `HandshakeAutomaton.kt` | update | neue Aktion `ResetSession` vor `Forward`, wenn die Anfrage in `READY` das Bit trägt ([TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe)) |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/session/HandshakeSession.kt` | update | führt die Aktion unter der Sitzungssperre aus (`onSessionReset`) |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/session/HandshakeMatrix.kt`, `HandshakeEvent.kt` | update | KDoc: das Bit wird an der Anfrage in `READY` ausgewertet |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/ResetConnectionDispatchTest.kt` | neu | Rohbytes-Tests mit Fake-Backend und über den Transport: früheres Handle 50009, Zähler ab 1, Grenze neu, ohne Bit unverändert, Sperre ([TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc)) |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/session/SessionSupport.kt` | update | Beschriftung der neuen Aktion |
| `referenztest-dotnet/Kompatibilitaet/LueckenTests.cs`, `AnmeldungTests.cs` | update | zwei Pooling-Messungen am Referenzclient (Kopf von `AnmeldungTests`: Nicht-belegt-Satz) ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)) |
| `tools/kompatibilitaetstest.sh`, `tools/e2e.sh`, `docs/plan/e2e-abdeckung.md` | update | Prüfung „eine Sitzung“ im Log, Text und Kennungen der Stufen 5 und 6, erzeugte Sicht |
| `src/testserver/kotlin/.../KompatibilitaetsServer.kt` | unverändert | der Aufzeichner genügt (Sitzungskennung und Marker im Log); er sieht Anfragen am Backend-Port, nicht die Statusbits der Pakete, einen Zähler des Bits führt er nicht |

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) steht im ADR-Index auf `Accepted` (erfüllt), `slice-rpc-prepared-handles` und `slice-qa-kompatibilitaet` liegen in `done/` (erfüllt: Handle-Tabelle, Stufe 6 mit dem Referenzclient), die Start-Bedingung der Welle `welle-fehler-abbruch` ist eingetreten, und ein Verantwortlicher ist im Kopf eingetragen. Der Slice braucht den Abbruchpfad nicht; ist [slice-attention-cancel-timeout](../done/slice-attention-cancel-timeout.md) `in-progress`, sind beide abzustimmen (Risiko in §6).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2, oder der Messaufbau von Punkt 2 (eine physische Verbindung, Zähler im Testserver) sprengt den Rahmen; Schnittvorschlag: die Messung als eigener Slice hinter dem Zurücksetzen (Punkt 1 ist ohne Messung lieferbar). Auslöser, am Slice-Plan beobachtbar: das Häkchen von Punkt 1 ist gesetzt und für Punkt 2 gibt es noch keinen Test.
- `in-progress` → `open` (blockiert — Carveout?): Der Transport gibt das Bit nicht an den Sitzungsrahmen und die Weitergabe verlangt einen Umbau am Transport außerhalb dieses Slice, oder die Messung zeigt, dass der Referenzclient ein ENVCHANGE verlangt oder das Bit in einem anderen Zustand als `IDLE` setzt (Re-Evaluierungs-Trigger von [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md)). Dann wartet der Slice auf die Folgeentscheidung.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün (einschließlich `make test`), die Messung am Referenzclient ist in der Kompatibilitätsmatrix eingetragen (auch als Negativbefund), und die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Zähler beginnt neu, nicht weiter:** [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) setzt „Zähler und Grenze von 1000 beginnen neu“. Dann kann ein früher vergebenes Handle mit kleinem Wert (z. B. 1) nach einem neuen `sp_prepexec` wieder gültig sein, mit anderem Text; ein Client, der ein Handle aus der Zeit vor dem Zurücksetzen hält, trifft dann nicht 50009, sondern den falschen Text. Der Referenzclient verwirft seine Handles beim Zurücksetzen selbst (nicht gemessen). Der Slice setzt wie die ADR, weist die Folge im Test und im KDoc aus und stellt die Frage dem Nutzer (ein weiterlaufender Zähler wäre eine Abweichung von der ADR und braucht eine Folge-Entscheidung). — **Ausgang:** eingetreten: der Zähler beginnt nach `RESETCONNECTION` bei 1 (Mutation V3 rot); die Folge ist in SPEC-030, KDoc und Matrix benannt, die Frage steht in der nächsten Zeile.
- **Handle-Aliasing nach dem Zurücksetzen (vom Review gefunden, offen, nicht entschieden):** Ein Client, der ein Handle aus der Zeit vor dem Zurücksetzen hält, bekommt nach einem neuen `sp_prepexec` mit gleichem Wert still den Text des neuen Handles statt 50009. Die Messung übt kein gehaltenes Handle über das Zurücksetzen hinweg (je Befehl neu vorbereitet, einmal ausgeführt); ob langlebige vorbereitete `SqlCommand`s das Pooling sicher überstehen, ist nicht gemessen. Entscheidung beim Nutzer oder Architect (Folge-ADR nach [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) §Re-Evaluierung). — **Ausgang:** weiter offen: → `BEO-TDS/zustandspunkte-nach-fehler-ohne-entscheidung`
- Der Sitzungsrahmen und die Sitzungssperre sind dieselben wie in [slice-attention-cancel-timeout](../done/slice-attention-cancel-timeout.md); zwei Slices in `in-progress` an `RequestLoop` erzeugen Konflikte und eine Reihenfolge-Abhängigkeit im Zustandsautomaten. — **Ausgang:** entfallen: beide Slices liegen im Stand von `main`, alle Tests laufen grün, ein Konflikt ist nicht aufgetreten.
- Dass der Referenzclient mit Pooling gegen einen Server dieses Repos das Bit sendet, ist nicht gemessen (R18, R19 stammen vom echten Server); fällt die Messung negativ aus, bleibt die Handle-Erschöpfung unter Pooling bestehen und Zeile 13 der Lücken offen. — **Ausgang:** weiter offen: → `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` (über die Wirkung unter TDS 7.4 positiv gemessen, das Bit am Draht nicht gesehen, TDS 8.0 offen).
- ENVCHANGE in der Antwort auf `RESETCONNECTION`: Typ und Inhalt am echten Server nicht ausgewertet (R18); ob der Referenzclient eines verlangt, misst Punkt 2 (c). — **Ausgang:** weiter offen: → `BEO-TDS/messung-am-echten-sql-server-fehlt`
- `RESETCONNECTION` außerhalb von `IDLE` ist unentschieden ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) „Offen“); der Code verhält sich dort, wie der Rahmen ohne Auswertung des Bits, und das steht als Festlegung im KDoc. — **Ausgang:** weiter offen: → `BEO-TDS/zustandspunkte-nach-fehler-ohne-entscheidung`
- Die Wirkung im Test gegen ein Fake-Backend belegt den Rahmen, nicht den Pool des Clients; Pooling unter TDS 8.0 ist nicht Teil der Messung. — **Ausgang:** weiter offen: → `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`

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

**Gegenstand:** geliefert (`RESETCONNECTION` im Sitzungsrahmen: `HandshakeAutomaton` gibt in `READY` bei gesetztem Bit `[ResetSession, Forward]` aus, `HandshakeSession.execute` führt `sessionReset()` unter der Sitzungssperre vor der Verarbeitung aus, `RequestLoop` leert die Handle-Tabelle über `onSessionReset`; Messung mit Pooling am Referenzclient in Stufe 6; Spec-Nachzug). Commits: Sitzungsrahmen `a7e9c61`, Messung und Spec `eba9d15`, Review-Report `79dbc61`, Fix-Runde `3456eb0`, Nachzug nach dem Merge von `main` `467f1fc`, Verifikationsbericht `8362089`.

**Gemessen** (Einzelmessungen am 2026-11-01 auf einem Host mit Last 11 bis 16 durch fremde Prozesse, kein Beleg für andere Hosts), Verifikation auf `467f1fc` (eigene Läufe): `make clean-build` Exit 0 in 7 min 37 s, 888 Tests PASSED, 0 FAILED, 0 SKIPPED, darunter alle 7 Tests von `ResetConnectionDispatchTest`; `make e2e` Exit 0 in 296 s, sieben Stufen grün (Stufe 6 mit 7 Tests in 25 s); `make matrix-zitate` 888 bestandene Testfälle, 244 Zitate, 0 ohne PASSED; `make gates` der Verifikation zu großen Teilen aus dem Cache (9,3 s), kein Frischbeleg. Mutationen: neun eigene der Verifikation, acht rot (V1 Reset auch in RUNNING, V2 außerhalb der Sperre, V3 ohne Zähler-Reset, V4 Bit nur an SQL_BATCH, V5 bei ATTENTION, V6 Transaction Manager ohne Reset, V8 Haken nicht verdrahtet, V9 ohne `statements.clear()`), **V7 grün** (`0x10` wie `0x08`, siehe L-1). Gemessen am Referenzclient (`Microsoft.Data.SqlClient`, TDS 7.4, `Pooling=true`): drei Open-Phasen zu je 400 Befehlen, 1200 beantwortet; in einer Phase endet die Anfrage nach 1000 mit 50010 bei offener Verbindung; die Folgeabfrage nach dem Zurücksetzen wird ohne ENVCHANGE angenommen; eine Sitzung im Log.

**Handvektor:** keiner. **Selbstkonsistenz:** die Rohbytes-Tests laufen gegen Fake-Backends mit demselben Codec, der die Antwort erzeugt; das Bit am Draht wird nur über seine Wirkung am Referenzclient beobachtet.

**Festlegung (Setzungen):** (1) Der Zähler beginnt nach dem Zurücksetzen bei 1 und die Grenze von 1000 gilt neu ([ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Entscheidung 5). (2) Das Bit wird nur in `IDLE` (`READY`) ausgewertet; außerhalb schließt die Verbindung ohne Zurücksetzen. (3) `0x10` allein setzt nichts zurück (nicht gemessen, kein Test, L-1). (4) Der Transaction Manager mit Bit setzt zurück, der Zustand bleibt `READY`, die Schleife schließt danach unbeantwortet. (5) Das Zurücksetzen ist eine Aktion, kein Zustand.

**Abweichungen von Plan und DoD-Wortlaut:** keine Abweichung von [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md). Der Plan ließ offen, wie das Bit vom Transport in den Rahmen kommt: `MessageAssembler` liest es aus `firstStatus and RESET_CONNECTION`, der Codec reicht es durch (im KDoc festgehalten). Ergänzt in der Fix-Runde: Transaction-Manager-Fall (siebter Test).

**Nicht gemessen:** das Bit am Draht; ENVCHANGE in der Antwort am echten SQL Server; TDS 8.0 mit Pooling; ein gehaltenes Handle über das Zurücksetzen hinweg (Handle-Aliasing); ein eigener Mutationslauf am Referenzclient (die Docker-Tests ohne Reset zeigen V8 und V9); Kaltlauf von `make gates`.

**Review und Verifikation:** Review `eba9d15` (L-1 bis L-5, I-1 bis I-5; behoben in `3456eb0`, I-1 nur gemeldet), Verifikation `467f1fc`: closure-fähig mit Bedingungen, kein HIGH, kein MEDIUM. Berichte: `docs/reviews/2026-11-01-eba9d15-slice-resetconnection-zuruecksetzen.md`, `docs/reviews/2026-11-01-467f1fc-verifikation-slice-resetconnection-zuruecksetzen.md`. Offen aus der Verifikation: L-1 (kein Test für `0x10`, V7 grün), L-2 (Stand-Zeile der Matrix veraltet, Pflege außerhalb), INFO-3 (Handle-Aliasing). Die Bedingungen der Verifikation (DoD-Haken, diese Notiz, Ausgänge, Register) sind mit dieser Closure erfüllt.

**Ausgang der Risiken in §6:** in §6 je Zeile eingetragen: Zähler beginnt neu: eingetreten; Rahmen und Sperre gemeinsam mit `slice-attention-cancel-timeout`: entfallen; Handle-Aliasing, Bit vom Referenzclient (TDS 8.0), ENVCHANGE am echten Server, Bit außerhalb von `IDLE`, Fake gegen Pool: weiter offen.

**Steering-Loop-Eintrag:** Kein Eintrag verkörpert, daher kein `liegt in`; nichts als Regel gesetzt, Zähler nicht gesetzt. Gelernt: (1) Der Koordinator empfahl vor dem Bau „der Zähler läuft weiter“, ohne die `Accepted` [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Entscheidung 5 zu lesen („Zähler und Grenze beginnen neu“); der Implementer folgte richtig der ADR. Eine Empfehlung an den Implementer, die eine Entscheidung berührt, ist vor der Weitergabe gegen die `Accepted`-ADR zu lesen; steht sie quer, ist sie eine Frage an den Nutzer, keine Anweisung. Einmal beobachtet, gezählt, nicht verkörpert. (2) Benannte Spec-Lücke: Das Handle-Aliasing nach dem Zurücksetzen ist die Folge der Setzung in SPEC-030 (dort als „Setzung, nicht gemessen“ benannt); ob langlebige vorbereitete `SqlCommand`s das Pooling überstehen, ist ungemessen (§6). (3) Aussagen zu `0x10` und der Matrix-Stand-Zeile trugen weiter als ihr Beleg ([AGENTS.md](../../../../AGENTS.md) §3.8): L-1 und L-2.

**Offene Punkte für den Nutzer:** (a) Handle-Aliasing: soll der Zähler nach dem Zurücksetzen weiterlaufen (Abweichung von [ADR-0027](../../adr/0027-abbruch-und-zuruecksetzen.md) Entscheidung 5, braucht eine neue ADR mit `Supersedes`) oder bleibt es bei 1 mit der benannten Folge? Entscheidung beim Nutzer oder Architect. (b) L-1: optional ein Test, der `0x10` allein als wirkungslos festhält, oder die Aussage abschwächen. (c) L-2: die Stand-Zeile der Kompatibilitätsmatrix ist veraltet (775 statt 888 Testfälle, „nur KDoc“ falsch); Pflege ausserhalb dieses Slice, als Aufgabe in §6 von [slice-kompatibilitaetsmatrix-erzeugen](../open/slice-kompatibilitaetsmatrix-erzeugen.md) eingetragen.

**Register:** Evidenz `slice-resetconnection-zuruecksetzen.md` ergänzt zu [BEO-TDS/tds-8-0-und-pooling-nicht-gemessen](../observations/BEO-TDS/tds-8-0-und-pooling-nicht-gemessen/observation.md), [BEO-TDS/messung-am-echten-sql-server-fehlt](../observations/BEO-TDS/messung-am-echten-sql-server-fehlt/observation.md), [BEO-TDS/zustandspunkte-nach-fehler-ohne-entscheidung](../observations/BEO-TDS/zustandspunkte-nach-fehler-ohne-entscheidung/observation.md), [BEO-TDS/aussagen-ueber-messung-hinaus](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/observation.md), [BEO-TDS/zahl-an-mehreren-orten-ohne-sensor](../observations/BEO-TDS/zahl-an-mehreren-orten-ohne-sensor/observation.md). Zähler nicht gesetzt.

**Folge-Slice:** keiner neu; die Aufgabe L-2 liegt in [slice-kompatibilitaetsmatrix-erzeugen](../open/slice-kompatibilitaetsmatrix-erzeugen.md) (§6), die Messung des Cancel am Referenzclient in [slice-cancel-referenzclient-szenario](../open/slice-cancel-referenzclient-szenario.md). Das Handle-Aliasing wartet auf die Entscheidung des Nutzers.

**Drei Paarungen:** Das Repo führt Wellen; die Paarungen prüft die Welle-Closure. Anker ↔ Folge-Slice: kein Anker gesetzt. Register ↔ Closure-Notiz: getragen (Pfade oben). Folge-Slice: genannt.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Die berührten Packages (`protocol`, `transport`) und Testpfade tragen keine eigene Strukturregel (Achse 1) und keine eigene Inventur-Linie (Achse 2): eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, Stand des gemergten `main` am 2026-11-01); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` — 17×, Stand *offen* (über der Schwelle, ohne Ausgang, Entscheidung beim Nutzer). Treffer: Pooling, `RESETCONNECTION` und `sp_unprepare` am Referenzclient (§6); dieser Slice liefert einen weiteren Beleg.
- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 25×, Stand *geplant*. Treffer: ENVCHANGE bei `RESETCONNECTION` am echten Server (§6).
- `BEO-TDS/spec-nachzug-nach-messung-offen` — 1×, offen. Treffer: `SPEC-004` führt Pooling und `RESETCONNECTION` als offenen Freigabepunkt (§2, Doku-Update).
- `BEO-TDS/stand-angaben-in-matrix-und-slice-plan-driften` — 1×, offen. Treffer: Zeile 13 der Lücken und die Zeile `Prepare` der Kompatibilitätsmatrix nennen den Stand vor der Messung (§2).
- `BEO-TDS/zustandspunkte-nach-fehler-ohne-entscheidung` — 1×, offen. Treffer: `RESETCONNECTION` außerhalb von `IDLE` bleibt unentschieden (§6).
- `BEO-TDS/aussagen-ueber-messung-hinaus` — 45×, Stand *verkörpert*. Treffer: ein Rohbytes-Test mit Fake-Backend belegt nicht den Pool des Referenzclients (§6).
- Ohne Treffer: die übrigen Einträge des Registers.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
