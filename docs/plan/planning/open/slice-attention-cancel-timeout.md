# Slice slice-attention-cancel-timeout: ATTENTION, Cancel und Timeout: Abschlussbestätigung, genau ein Cancel, CANCEL_TIMEOUT

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-fehler-abbruch

**Bezug:** [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0004](../../adr/0004-session-zustandsautomat.md), [ADR-0007](../../adr/0007-fehler-und-attention.md), [ADR-0013](../../adr/0013-backend-grenzen.md)

**Berührte Spec-Stellen:** SPEC-001 · SPEC-002 · SPEC-009 · SPEC-016 · SPEC-022 · SPEC-029 · SPEC-031 · SPEC-033 · ARC-015

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

**Ziel:** Ein ATTENTION-Paket während `RUNNING` oder `STREAMING` löst genau einen Cancel an die Backendoperation aus, stoppt die regulären Zeilen und wird mit der Abschlussbestätigung beantwortet; die Verbindung bleibt offen, die folgende Anfrage erhält nur ihre eigene Antwort, und ein Backend, das den Cancel nicht beachtet, führt nach `CANCEL_TIMEOUT` zum Schließen. Der Slice ist außerdem Träger der Frist einer Anfrage ([ADR-0013](../../adr/0013-backend-grenzen.md)): der Dispatch setzt `deadlineEpochMillis` auf Beginn plus `QUERY_TIMEOUT` und umschließt die Sammlung des Stroms mit einem Zeitlimit; der Ablauf endet in `TDS_TIMEOUT`.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- ATTENTION im Handshake (PRELOGIN, TLS, LOGIN7) und die Antwort auf unzulässige Übergänge — von [ADR-0004](../../adr/0004-session-zustandsautomat.md) und [ADR-0007](../../adr/0007-fehler-und-attention.md) als offen geführt; der Slice setzt die Entscheidung des Nutzers um und entscheidet sie nicht. Fehlt sie, behandelt er nur ATTENTION in `READY`.
- Fehler-Mapping und Verbindungsfolge nach Fehlern — `slice-fehler-mapping`, `slice-fehler-zustandsuebergang`.
- Cancel beim Disconnect und beim geordneten Beenden — `slice-abbruch-ressourcen-shutdown`; dort wird derselbe Abbruchpfad aufgerufen, hier wird er gebaut.
- Garantierte Unterbrechbarkeit beliebiger externer Backendoperationen — Out-of-Scope von [TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe); der Slice belegt nur die Frist `CANCEL_TIMEOUT` und das Schließen.
- Verhalten des Referenzclients (`SqlCommand.Cancel()`, `CancellationToken`, Command-Timeout, Pooling) — `slice-qa-synchronitaet` und `slice-qa-kompatibilitaet`; hier Rohbytes-Tests mit einem Fake-Backend. Der Referenzclient hängt nach der ADR-Messung bei fehlender Bestätigung bis zu seinem Timeout (T, S10, S12, S14, S16, S18); das belegt der Slice nicht am Client.
- MARS und mehrere gleichzeitige Anfragen je Sitzung (`SPEC-004` lässt das offen).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

- [ ] ATTENTION wird als eigenes Paket erkannt, auch während Streaming; in `RUNNING` und `STREAMING` wird genau einmal Cancel an das Backend gegeben, es folgt keine reguläre Zeile mehr, die Abschlussbestätigung wird nach der Entscheidung des Nutzers zum Inhalt der Cancel-Antwort gesendet, und die Sitzung nimmt erst danach wieder eine Anfrage an; Rohbytes-Tests mit suspendierendem und mit blockierendem Fake-Backend, ein Test mit unmittelbar folgender Anfrage. ([TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) Happy Path, [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität), `SPEC-029`) Dazu gehört der offene Punkt aus [ADR-0004](../../adr/0004-session-zustandsautomat.md), ob das Schreiben eines Pakets von der Abbruchanforderung unabhängig laufen muss (der Spike schrieb jedes Paket als `NonCancellable`, ein Lauf ohne diese Maßgabe wurde nicht gebaut): ein Test bricht mitten in einem Paket ab und prüft, dass kein halbes Paket auf der Leitung bleibt; die gewählte Form steht als Festlegung im KDoc.
- [ ] Wettlauf DONE gegen ATTENTION und ATTENTION in `IDLE` oder `COMPLETED`: der erste Übergang gewinnt, es entsteht kein doppeltes DONE und keine zweite Antwort, wiederholtes ATTENTION löst keinen zweiten Cancel aus; der Wettlauftest läuft mit gesteuerter Reihenfolge statt Wiederholung bis zum Zufallsfund. ATTENTION im Handshake (PRELOGIN, TLS, LOGIN7) ist in [ADR-0004](../../adr/0004-session-zustandsautomat.md) und [ADR-0007](../../adr/0007-fehler-und-attention.md) offen und hat sonst keinen Träger: der Nutzer entscheidet sie vor dem Start, dieser Slice setzt sie mit einem Test je Handshake-Zustand um (`slice-handshake-login` liefert die Zustände) oder führt sie in der Closure als weiter offen mit Grund. ([TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe) Boundary, `SPEC-001`)
- [ ] **Zeitlimits** `CANCEL_TIMEOUT` und Frist einer Anfrage, beide mit gesteuerter Uhr. (a) `CANCEL_TIMEOUT`: Ein Backend, das den Cancel nicht beachtet, führt nach der Frist zum Schließen der Verbindung mit geschlossenem Backend-Strom und freigegebener Sitzung. (b) Frist ([ADR-0013](../../adr/0013-backend-grenzen.md)): der Dispatch setzt `deadlineEpochMillis` auf Beginn plus `QUERY_TIMEOUT` (heute `null`; der Test des SQL_BATCH-Dispatch hält das fest und ändert sich hier) und umschließt die Sammlung mit einem Zeitlimit; bei Ablauf geht der Lauf den Weg des Cancel (Strom abbrechen, `close()`, zurückgehaltenen Abschluss verwerfen) und endet in `TDS_TIMEOUT`; Tests: Ablauf, Frist in der Vergangenheit, `null` ohne Zeitlimit, Backend ignoriert den Hinweis. Class von `TDS_TIMEOUT` und Verbindungsfolge folgen der Nutzerentscheidung, ob Cancel und Timeout als Fehler gelten. (`SPEC-033`, `SPEC-009`, `SPEC-016`, `SPEC-022`)
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
| Anfragezustand (Response Sequencer, ARC-015) | update | Zustand `CANCELLING`, Übergänge `RUNNING`/`STREAMING` → `CANCELLING` → `COMPLETED`, Race-Regel |
| `src/main/kotlin/io/github/pt9912/tdswire/transport/` (Nachrichtenleser) | update | ATTENTION als eigenes Paket erkennen, auch bei laufendem Schreiben; an den Sequencer melden |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/session/` (Package-Name ein Vorschlag) | update | Fristüberwachung `CANCEL_TIMEOUT`, Schließen der Verbindung |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/` (`RequestLoop`) | update | Frist setzen (Beginn plus `QUERY_TIMEOUT`) und Zeitlimit um die Sammlung des Stroms ([ADR-0013](../../adr/0013-backend-grenzen.md)); Ablauf in `TDS_TIMEOUT` |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/session/` | neu | Rohbytes-Tests mit Fake-Backend: Cancel einmal, Race, Frist ([TDS-FA-07](../../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-07](../../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)) |
| `spec/spezifikation.md` | update | `SPEC-029` und `SPEC-016` nach den Nutzerentscheidungen (Inhalt der Cancel-Antwort, Cancel/Timeout als Fehler) |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf, inklusive einer Testdatei-Zeile mit der Akzeptanzkriterien-ID in `Begründung`.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Der Start-Trigger der Welle `welle-fehler-abbruch` ist eingetreten, `slice-fehler-zustandsuebergang` liegt in `done/`, die Nutzerentscheidungen zum Inhalt der Cancel-Antwort, dazu, ob Cancel und Timeout als Fehler gelten, und zu ATTENTION im Handshake liegen vor, und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2, oder der dritte Punkt (Zeitlimits) sprengt den Rahmen; Schnittvorschlag: Frist und Zeitlimit (`QUERY_TIMEOUT`, Teil (b)) als eigener Slice hinter diesem, der den Abbruchpfad des Cancel nutzt und startet, wenn DoD-Punkt 1 in `done/` liegt.
- `in-progress` → `open` (blockiert — Carveout?): Der Port bietet kein `cancel`, das ein Fake-Backend beobachtbar macht (`spec/spezifikation.md` `SPEC-022` führt `cancel(sessionId, requestId)` als Entwurf), oder der Anfragezustand der Welle `welle-anfrageverarbeitung` lässt `CANCELLING` nicht zu.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle Punkte der DoD in §2 erledigt, `make gates` grün (einschließlich `make test`) und die Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Entscheidungsbedarf (Nutzer): Inhalt der Cancel-Antwort — zwei Nachrichten wie am echten Server (R11, R12) oder nur DONE mit ATTENTION-Status (am Testserver genügte das dem Referenzclient, T, S11). Beides ist nicht an einem Server dieses Repos gemessen. — **Ausgang:** offen bis zur Closure
- Entscheidungsbedarf (Nutzer): Gelten Cancel und Timeout als Fehler im Sinne der Regel „Fehler“ (`SPEC-016` nennt „Fehler/Disconnect“)? Davon hängt die Class von `TDS_TIMEOUT` und die Verbindungsfolge ab; Grundregel A (schließen) gilt bis dahin. — **Ausgang:** offen bis zur Closure
- Spec-Lücke „welche Frist `TDS_TIMEOUT` auslöst“: beantwortet in [ADR-0013](../../adr/0013-backend-grenzen.md) (Status Proposed): `QUERY_TIMEOUT` (`SPEC-009`), vom Protokollkern durchgesetzt; der Client-Timeout kommt weiter als ATTENTION. Offen bleiben Class und Verbindungsfolge (Entscheidungsbedarf des Nutzers oben). — **Ausgang:** offen bis zur Closure
- Entscheidungsbedarf (Nutzer; offene Punkte von [ADR-0004](../../adr/0004-session-zustandsautomat.md) und [ADR-0007](../../adr/0007-fehler-und-attention.md), Träger ist dieser Slice, DoD-Punkt 2): ATTENTION im Handshake und in `IDLE`; am echten Server ist nur ATTENTION nach vollständig gesendeter Antwort gemessen (R13), im Leerlauf nicht. — **Ausgang:** weiter offen: → `BEO-TDS/messung-am-echten-sql-server-fehlt`
- Das Verhalten bei fehlender oder falscher Bestätigung (Client hängt bis zum Timeout) ist am Testserver gemessen, die Wirkung unter Pooling und TDS 8.0 nicht. — **Ausgang:** weiter offen: → `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`
- Wettlauftests, die nur durch Wiederholung Treffer erzeugen, sind Zufallsbelege; die DoD verlangt gesteuerte Reihenfolge. — **Ausgang:** offen bis zur Closure
- Ein Backend ist nach dem Port-Vertrag suspendierend, ein blockierender Treiber liegt hinter der Umhüllung des Integrators ([ADR-0013](../../adr/0013-backend-grenzen.md)); der Fake-Backend-Test belegt `CANCEL_TIMEOUT` und die Frist, nicht das Verhalten eines echten blockierenden Treibers. Die Messungen der Frist (Überschreitung 0 bis 3 ms, bei nicht unterbrechbarem Aufruf bis zu dessen Ende) stammen aus einem Spike mit anderen Versionen und gelten nicht für diesen Quelltext. — **Ausgang:** offen bis zur Closure
- Zwei Auslöser für denselben Abbruch (ATTENTION des Clients, Frist des Kerns) gehen denselben Weg des Cancel; ohne gemeinsamen Pfad entstünde ein doppeltes DONE oder ein doppelter Cancel. Der Wettlauftest des zweiten Punkts nimmt die Frist als zweiten Auslöser auf (gesteuerte Reihenfolge). — **Ausgang:** offen bis zur Closure

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Die berührten Packages (`protocol`, `transport`, `runtime`) sind Pfad-Cluster (Achse 3), tragen aber keine eigene Strukturregel (Achse 1) und keine eigene Inventur-Linie (Achse 2): eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, acht Beobachtungen, Stand des gemergten `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 3×, Stand *geplant*. Treffer: ATTENTION im Leerlauf, Cancel-Antwort am Server dieses Repos (§6).
- `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` — 3×, Stand *geplant*. Treffer: Pooling und TDS 8.0 für Cancel und Timeout (§6).
- `BEO-TDS/aussagen-ueber-messung-hinaus` — 7×, Stand *verkörpert*. Treffer: Rohbytes-Test mit Fake-Backend belegt nicht den Referenzclient.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Treffer: Kennungen stehen als Link.
- Ohne Treffer: `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`, `BEO-TDS/adapter-festlegungen-ohne-slice`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/vergleichslauf-d-ungeklaert`.

**Modus-Begründungsblock — Umfang.** Pflicht, sobald mindestens eine berührte
Sub-Area BF oder Hybrid ist — einer pro Sub-Area. Bei reinem GF genügt der
Hinweis *"alle berührten Sub-Areas GF"*; bei reinem Refactor ohne neue
Sub-Area-Berührung entfällt **er** — nicht der Abschnitt.

Alle berührten Sub-Areas GF.
