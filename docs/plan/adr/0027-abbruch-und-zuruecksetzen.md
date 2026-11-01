# ADR-0027: Abbruch und Zurücksetzen: Inhalt der Cancel-Antwort, Cancel und Timeout als Fehler, ATTENTION im Handshake, Schließen nach `TDS_SHUTDOWN`, `RESETCONNECTION` (schärft ADR-0004 und ADR-0007)

**Status:** Accepted (vom Nutzer entschieden)

**Datum:** 2026-11-01

**Autor:** pt9912

**Bezug:** [ADR-0004](0004-session-zustandsautomat.md), [ADR-0007](0007-fehler-und-attention.md), [ADR-0013](0013-backend-grenzen.md), [ADR-0015](0015-spannungen-adr-gegen-spec.md), [ADR-0017](0017-prepared-handles-grenzwert.md), [TDS-FA-07](../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-07](../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)

**Schärft:** [SPEC-001](../../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung) (ATTENTION im Handshake, Zurücksetzen), [SPEC-029](../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung) (Inhalt der Cancel-Antwort), [SPEC-016](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) (`TDS_TIMEOUT`), [SPEC-044](../../../spec/spezifikation.md#spec-044--geordnetes-beenden) und [SPEC-049](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) (Zeitpunkt des Schließens), [SPEC-030](../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten) (Handle-Tabelle beim Zurücksetzen), [TDS-FA-06.a](../../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung), [TDS-FA-07.a](../../../spec/spezifikation.md#tds-fa-07a--attention-und-cleanup).

**Supersedes:** keines. Diese ADR **schließt offene Punkte** von [ADR-0004](0004-session-zustandsautomat.md) („Nicht entschieden“, „Offen“) und [ADR-0007](0007-fehler-und-attention.md) („Nicht entschieden“, „Offen“) nach dem Muster von [ADR-0015](0015-spannungen-adr-gegen-spec.md). Beide bleiben `Accepted` und unverändert ([AGENTS.md](../../../AGENTS.md) §3.5); ihre Offen-Listen sind für die fünf Punkte unten beantwortet. Die Klausel in ADR-0007 „`TDS_TIMEOUT` bleibt außerhalb der Gruppen, bis entschieden ist, ob Cancel und Timeout als Fehler gelten“ ist eine Bis-Klausel; ihre Bedingung tritt mit Entscheidung 2 ein, sie wird nicht überschrieben.

---

## Kontext

ADR-0004 und ADR-0007 führen Punkte zu Abbruch und Zurücksetzen offen. Messbezeichner R01 bis R19 und S01 bis S22 sind die der Messung in [ADR-0007](0007-fehler-und-attention.md); „Setzung“ heißt: festgelegt, nicht gemessen. Der Bau des Abbruchpfads, des Cancel-Szenarios am Referenzclient ([CO-004](../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md)) und des Disconnect- und Beendens-Pfads wartet auf diese Antworten; die Spezifikation führt die Fragen als „noch festzulegen“ ([SPEC-001](../../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung), [SPEC-016](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [TDS-FA-06.a](../../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung)). Der Nutzer hat am 2026-11-01 die Empfehlungen des Architects für alle fünf Punkte übernommen. Eine neue Messung liegt nicht zugrunde; die Entscheidung stützt sich auf R und S aus ADR-0007 und auf die Messung am Referenzclient zu den Prepared-Handles ([Kompatibilitätsmatrix](../kompatibilitaetsmatrix.md), Zeile 13 der bekannten Lücken: `Microsoft.Data.SqlClient` 7.1.1 sendet kein `sp_unprepare`, nach 1000 Prepare endet das 1001. mit `TDS_PREPARE_LIMIT`, Code 50010).

## Entscheidung

Der Nutzer wählt für jeden der fünf Punkte die unten als gewählt markierte Option. Alle fünf sind **Setzungen des Nutzers (2026-11-01)**; wo ein Messbezug besteht, steht er dabei.

1. **Inhalt der Cancel-Antwort: nur DONE mit ATTENTION-Status (`DONE[ATTN]`), keine zweite Nachricht.** Auf ein ATTENTION in `RUNNING` oder `STREAMING` stoppt der Server die regulären Zeilen und sendet genau eine Bestätigung `DONE[ATTN]`. Die Zwei-Nachrichten-Form des echten Servers (erst SESSIONSTATE und `DONE[ERR]`, dann `DONE[ATTN]`, R11, R12) wird nicht gebaut, bevor das Cancel-Szenario am Referenzclient sie braucht. Messbezug: am Testserver genügte `DONE[ATTN]` dem Referenzclient (S11, S15, S17: offen, Folgeabfrage richtig); ohne die Bestätigung hängt der Client bis zum Timeout (S12, S14, S16, S18). Nicht gemessen: ein Server dieses Repos, andere Clients. Dieselbe Bestätigung ist die Form der Antwort auf ATTENTION nach Ende der Antwort; ob und wann der Server im Leerlauf antwortet, bleibt wie in `SPEC-001` Setzung (R13 gilt nur für „Antwort vollständig gesendet“).

2. **Cancel ist kein Fehler; `QUERY_TIMEOUT` ist ein Fehler der Anfragefehler-Gruppe.**
   - *Cancel* (ATTENTION des Clients, auch ausgelöst durch dessen `CommandTimeout`, R15) ist ein Abbruch durch den Client: kein ERROR-Token, nur `DONE[ATTN]` nach Entscheidung 1; die Verbindung bleibt offen (ADR-0007: „nach Cancel oder Timeout, wenn DONE[ATTN] gesendet wurde“; gemessen am echten Server R11 bis R16).
   - *Ablauf der Frist einer Anfrage* (`QUERY_TIMEOUT`, [SPEC-009](../../../spec/spezifikation.md#3-defaults-und-konstanten), vom Protokollkern durchgesetzt, [ADR-0013](0013-backend-grenzen.md) Entscheidung 4) geht den Weg des Cancel zum Backend und endet in `TDS_TIMEOUT` als **Fehler der Anfragefehler-Gruppe**: ERROR (Class 16) und DONE mit Fehlerstatus; die Verbindung **bleibt offen**, wenn dieser Abschluss vollständig gesendet wurde. Die Spezifikation führt `TDS_TIMEOUT` heute mit Class 20 und „schließt“ (Zeile in [§4](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), dort als Festlegung und Folge der Grundregel gekennzeichnet); das ist mit dieser ADR überholt und wird nachgezogen (Folgepflicht).
   - *Ausnahmen, die schließen:* (a) ist der Strom nicht mehr sicher abzuschließen, schließt die Verbindung ohne ERROR und DONE ([SPEC-031](../../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität)); (b) beachtet das Backend den Cancel nicht, schließt die Verbindung nach `CANCEL_TIMEOUT` ([SPEC-033](../../../spec/spezifikation.md#3-defaults-und-konstanten)); nur dieser vom Backend ignorierte Cancel schließt, nicht der Cancel selbst; (c) beim geordneten Beenden endet die Verbindung auch nach `DONE[ATTN]` (ADR-0007, unverändert).
   - Kreuzen sich Server-Frist und Client-ATTENTION, gewinnt der erste Übergang ([SPEC-029](../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung)); ein ATTENTION nach dem vollständig gesendeten Timeout-Abschluss ist der Fall „ATTENTION nach Ende der Antwort“ (Entscheidung 1).
   - Messbezug: der Client reagiert auf einen Serverfehler der Class 16 mit `SqlException` und offener Verbindung (S01, S20). `TDS_TIMEOUT` als Serverfehler ist am Referenzclient nicht gemessen; das echte-Server-Verhalten bei Cancel und Client-Timeout (R11 bis R16) kennt keine Server-Frist.

3. **ATTENTION im Handshake (PRELOGIN, TLS, LOGIN7): Verbindung schließen, ohne Antwort** (Grundregel A aus ADR-0007). Das Paket wird weder beantwortet noch ignoriert. Messbezug: „ATTENTION zuerst“ und „vor Login“ gehören zu den rohen Paketen, auf die der Spike des Zustandsautomaten mit „Verbindung geschlossen, 0 Antwortbytes“ reagierte (ADR-0004, Messzeile „Rohe Pakete“, 11 von 11); das ist Verhalten des Testservers des Spike (dort Setzung), nicht eines echten Servers, der hier nicht gemessen ist.

4. **Zeitpunkt des Schließens nach `TDS_SHUTDOWN`: nach ERROR und DONE** (Antwort senden, dann schließen). Das ist die Lesart, die ADR-0007 und [SPEC-049](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) führen; offen war der Zustandsübergang: das Ende der Antwort führt in **einem** Übergang nach `CLOSED` ([SPEC-001](../../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung), Verbindungsfolge des Ausgangs), geschlossen wird nach dem Schreiben. Messbezug: „ERROR, DONE, dann Schließen“ wird am Referenzclient richtig gelesen, erst die nächste Abfrage scheitert (S21, S22). Ein Herunterfahren am echten Server ist nicht gemessen; die Reihenfolge ist Setzung.

5. **`RESETCONNECTION` (Statusbit `0x08` im ersten Paket nach Wiederverwendung der gepoolten Verbindung, R18, R19): der Server setzt den Sitzungszustand zurück, mindestens die Handle-Tabelle der Prepared-Handles.**
   - Das Zurücksetzen geschieht unter der Sitzungssperre, vor der Verarbeitung der Anfrage, die das Bit trägt. Handles, die vorher vergeben waren, sind danach unbekannt (`TDS_UNKNOWN_HANDLE`, [SPEC-057](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder)); Zähler und Grenze von 1000 ([ADR-0017](0017-prepared-handles-grenzwert.md)) beginnen neu.
   - Grund und Messbezug: der Referenzclient sendet kein `sp_unprepare`; ohne Zurücksetzen erschöpfen gepoolte Sitzungen die Handles (gemessen am Referenzclient 7.1.1: das 1001. Prepare endet mit 50010, Kompatibilitätsmatrix, Zeile 13 der bekannten Lücken). Dass ein Pool beim Wiederverwenden `RESETCONNECTION` setzt, ist am echten Server gemessen (R18, R19: `0x08` im ersten Paket nach `Open()`, dieselbe TCP-Verbindung). Dass der Referenzclient mit Pooling gegen einen Server dieses Repos das Bit sendet, ist **nicht** gemessen.
   - Weiteren Sitzungszustand kennt der Server heute nicht (Transaktionen sind im Katalog nicht enthalten, [SPEC-030](../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten)). „Mindestens die Handle-Tabelle“ heißt: wer weiteren Sitzungszustand einführt, setzt ihn hier zurück, ohne neue Entscheidung.
   - **Offen, nicht gemessen, nicht geraten:** ob und wie die Antwort ENVCHANGE trägt. R18 zeigt nur, dass die Antwort des echten Servers mit ENVCHANGE beginnt; Typ und Inhalt sind nicht ausgewertet, und ob der Referenzclient ein ENVCHANGE verlangt, ist nicht gemessen. Messweg im Abschnitt „Messungen“.

**Wirkung auf den Zustandsautomaten** ([ADR-0004](0004-session-zustandsautomat.md) flach, [ADR-0015](0015-spannungen-adr-gegen-spec.md)). Die Aufzählung bleibt flach. (1) Entscheidung 1 braucht für „Cancel signalisiert, Bestätigung noch nicht gesendet“ einen Zwischenschritt zwischen `RUNNING`/`STREAMING` und `IDLE`: das ist der in `SPEC-001` vorgesehene `CANCELLING`, den ADR-0015 nicht verworfen hat; ob als Zustand der Aufzählung oder als Übergang mit Aktion, entscheidet der Bau; ein Wechsel des Zuschnitts ist es nicht (der Trigger von ADR-0015 gilt weiter). (2) Die Handshake-Zustände gehen bei ATTENTION nach `CLOSED` (Entscheidung 3), wie schon beim abgelehnten Login. (3) Entscheidung 4 verlangt keinen neuen Zustand. (4) `RESETCONNECTION` ist eine Aktion am Übergang, der die Anfrage annimmt, kein Zustand; das Bit steht im Paketkopf, der Transport muss es dem Sitzungsrahmen melden (ob der Codec es heute weitergibt, ist hier nicht geprüft). Das Bit in einem anderen Zustand als `IDLE` ist **nicht entschieden** (bis dahin Grundregel A).

**Wirkung auf den Backend-Vertrag** ([ADR-0006](0006-backend-vertrag-und-streaming.md), [ADR-0013](0013-backend-grenzen.md)). Keine Änderung der Schnittstelle: `cancel(sessionId, requestId)` und `close()` werden weiter höchstens einmal je Anfrage gerufen, gleich ob ATTENTION, Frist, Disconnect oder Beenden der Anlass ist. `deadlineEpochMillis` bleibt Hinweis, die Durchsetzung bleibt beim Protokollkern. Das Zurücksetzen betrifft nur den Sitzungszustand im Server; das Backend wird dafür nicht gerufen.

**Wirkung auf die Observability** (nur Hinweis, keine Entscheidung; [ADR-0020](0020-logformat-und-observability-schnittstelle.md) ist nicht angenommen). Cancel (ATTENTION angenommen, `DONE[ATTN]` gesendet), Timeout (`TDS_TIMEOUT` mit `error_code`), Schließen nach ignoriertem Cancel (`CANCEL_TIMEOUT`) und Zurücksetzen (Zahl verworfener Handles) sind unterscheidbare Ereignisse; Namen und Felder legt die Observability-Entscheidung fest. Für sie gilt: Cancel erzeugt keinen Fehler-Eintrag, Timeout schon.

## Verglichene Alternativen

| Punkt | Option | Pro | Contra |
|---|---|---|---|
| 1 | **A — nur `DONE[ATTN]` (gewählt)** | am Referenzclient ausreichend (S11, S15, S17); eine Nachricht | weicht vom echten Server ab (R11, R12); andere Clients nicht gemessen |
| 1 | B — zwei Nachrichten wie der echte Server | gemessenes Vorbild (R11, R12) | mehr Token und Zustand; kein Beleg, dass der Referenzclient sie braucht |
| 1 | C — nichts festlegen | keine Vorfestlegung | blockiert den Bau und das Cancel-Szenario |
| 2 | **A — Cancel kein Fehler, Timeout Anfragefehler, Verbindung offen (gewählt)** | folgt dem echten Server bei Cancel (R11 bis R16) und der Gruppenregel für Class ≤ 19; Verbindung wiederverwendbar | Class 16 für `TDS_TIMEOUT` ist Setzung; der Client sieht Number 50005 statt −2 (nicht gemessen) |
| 2 | B — beide als Fehler, Grundregel A (schließen) | einfach, bisheriger Wortlaut der Spezifikation | Pool verliert die Verbindung bei jedem Cancel; weicht von R11 bis R16 ab |
| 2 | C — beide als Fehler, offen | eine Regel für beide | ein Fehler-Token neben `DONE[ATTN]` widerspricht Entscheidung 1 |
| 3 | **A — schließen ohne Antwort (gewählt)** | Grundregel A; im Spike 11 von 11 sauber | am echten Server nicht gemessen |
| 3 | B — ignorieren und weiterlesen | Client bleibt verbunden | Desynchronisation des Handshakes möglich, kein Beleg |
| 3 | C — mit `DONE[ATTN]` beantworten | wie nach dem Login | kein Anfragekontext im Handshake |
| 4 | **A — nach ERROR und DONE schließen (gewählt)** | Client liest die Antwort (S21, S22); folgt ADR-0007 | Reihenfolge nicht am echten Server gemessen |
| 4 | B — vor der Antwort schließen | einfach | Client sieht Transportfehler statt `TDS_SHUTDOWN` (vgl. S08, S09) |
| 4 | C — nach einer Schonfrist | Antwort sicher zugestellt | neue Frist ohne Anlass |
| 5 | **A — Sitzungszustand zurücksetzen, mindestens Handles (gewählt)** | verhindert die gemessene Erschöpfung bei Pooling; Bit am echten Server gemessen | ENVCHANGE-Antwort offen |
| 5 | B — Bit ignorieren | nichts zu bauen | gepoolte Sitzungen erschöpfen die 1000 Handles (gemessen am Client ohne `sp_unprepare`) |
| 5 | C — Verbindung beim Bit schließen | einfach | der Pool verliert seine Wiederverwendung, Gegenteil des Zwecks |

## Konsequenzen

- Positiv: Der Bau von Abbruchpfad, Cancel-Szenario und Beenden ist entscheidbar; fünf offene Punkte in ADR-0004 und ADR-0007 sind beantwortet, ohne dass diese angefasst werden; das Pooling-Risiko aus ADR-0017 hat einen Träger.
- Negativ: Die Offen-Listen von ADR-0004 und ADR-0007 stehen im Text weiter und sind nur über diese ADR als beantwortet erkennbar; den Hinweis trägt der Titel des Index-Eintrags (der Index hat keine „Schärft“-Spalte). `TDS_TIMEOUT` mit Class 16 widerspricht dem Wortlaut der Spezifikation, bis sie nachgezogen ist.
- **Folgepflicht, Spec-Nachzug** (Träger: der Planner legt ihn in den Zuschnitt „Abbruchpfad mit Fristen“; diese ADR ändert `spec/` nicht): (a) [SPEC-016](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), Zeile `TDS_TIMEOUT`: Aktion „ERROR und DONE mit Fehlerstatus, Verbindung offen“ statt „Fehler/Disconnect“; (b) Zuordnungstabelle in §4: `TDS_TIMEOUT` Class 16, offen, Herkunft „Setzung des Nutzers 2026-11-01“; die Sätze zur offenen Einzelzuordnung von `TDS_TIMEOUT` und „Offen: ... ob Cancel und Timeout als Fehler zählen“ entfallen; (c) [TDS-FA-06.a](../../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung) Nr. 6: `TDS_TIMEOUT` gehört zur Anfragefehler-Gruppe, „Noch festzulegen: ob Cancel und Timeout als Fehler zählen“ entfällt; (d) [SPEC-029](../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung): die Cancel-Antwort ist `DONE[ATTN]` allein, Cancel ist kein Fehler; (e) [SPEC-001](../../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung): ATTENTION im Handshake schließt ohne Antwort, `RESETCONNECTION` setzt zurück; (f) [SPEC-030](../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten): die Handle-Tabelle wird bei `RESETCONNECTION` geleert; (g) [SPEC-044](../../../spec/spezifikation.md#spec-044--geordnetes-beenden) und [SPEC-049](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder): Herkunft kennzeichnen (Schließen nach ERROR und DONE); (h) der Absatz „Offene Punkte des Backend-Vertrags“, letzter Punkt (Class von `TDS_TIMEOUT`); (i) Zeile 13 der bekannten Lücken in der Kompatibilitätsmatrix, soweit sie `RESETCONNECTION` offen führt. Die Architektursicht bleibt ohne ADR-Bezug ([AGENTS.md](../../../AGENTS.md) §3.4).
- **Folgepflicht, Zuschnitt (Vorschlag an den Planner):** (1) **Abbruchpfad mit Fristen**: ATTENTION erkennen, genau ein Cancel, `DONE[ATTN]`, `CANCELLING` und Race, `CANCEL_TIMEOUT`, Frist `QUERY_TIMEOUT` mit `TDS_TIMEOUT` als Anfragefehler, ATTENTION im Handshake, Schließen nach `TDS_SHUTDOWN` nach ERROR und DONE; (2) **Referenzclient-Szenario**: das Cancel-Szenario von `SPEC-040` am Referenzclient (`Cancel()`, `CancellationToken`, Command-Timeout, Folgeabfrage), löst [CO-004](../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md) auf; (3) **Zuruecksetzen**: `RESETCONNECTION` im Sitzungsrahmen, Handle-Tabelle leeren, Messung mit Pooling. Wie die bereits geplanten offenen Zuschnitte zu Cancel und Timeout und zu Disconnect und Beenden darin aufgehen, entscheidet der Planner, nicht diese ADR.
- **Folgepflicht, Messungen:** Abschnitt „Messungen“.
- **Akzeptierte Negative (einmalig, mit Grund):** (1) Class 16 und Number 50005 für `TDS_TIMEOUT` sind am Referenzclient nicht gemessen; das Referenzclient-Szenario misst sie mit, ein Fehlverhalten ist ein Re-Evaluierungs-Fall. (2) `RESETCONNECTION` in einem anderen Zustand als `IDLE` bleibt unentschieden: der Referenzclient setzt das Bit nur im ersten Paket nach `Open()` (R18, R19), ein Fall ohne Anlass.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| JVM-/Protokolltests, .NET-Referenzclient | Nach `Cancel()` kommt genau ein `DONE[ATTN]`, die Folgeabfrage auf derselben Verbindung erhält nur ihre Antwort; Ablauf von `QUERY_TIMEOUT` endet in ERROR 50005 (Class 16) und DONE mit Fehlerstatus bei offener Verbindung; ATTENTION vor dem Login schließt ohne Antwortbytes; nach `TDS_SHUTDOWN` kommen ERROR und DONE vor dem Schließen; nach `RESETCONNECTION` ist ein früheres Handle unbekannt und der Zähler beginnt neu. Noch keine bestandenen Tests. | — (Tests mit dem jeweiligen Bau); Cancel-Szenario über `make simulations-integration` ([CO-004](../carveouts/CO-004-cancel-szenario-spec-040-fehlt.md)) |

## Messungen

Folgepflicht, nicht Teil der Entscheidung:

- **ENVCHANGE bei `RESETCONNECTION`:** (a) den Mitschnitt von R18 am echten Server wiederholen und Typ und Inhalt des ENVCHANGE im ersten Antwortpaket auswerten (der Proxy-Parser aus ADR-0007 kennt es nicht); (b) den Referenzclient mit `Pooling=true` gegen den Server dieses Repos fahren, zuerst ohne ENVCHANGE in der Antwort: gelingt die Folgeabfrage, ist es nicht nötig; sonst mit dem Typ aus (a). Das Ergebnis ist dann eine Messung, keine Setzung.
- **Zwei-Nachrichten-Form des Cancel:** nur wenn das Referenzclient-Szenario mit der Ein-Nachricht-Form scheitert (`Cancel()`, `CancellationToken`, Command-Timeout, ATTENTION während des Streamings).
- **`TDS_TIMEOUT` als Serverfehler:** Referenzclient gegen den Server mit kurzem `QUERY_TIMEOUT` und längerem Command-Timeout des Clients; Folgeabfrage auf derselben Verbindung.

## Offen

Bleibt aus ADR-0004 und ADR-0007 offen, weil diese ADR es nicht entscheidet: die Antwort auf unzulässige Übergänge außerhalb des Handshakes (die Spezifikation führt „Verbindung schließen“ als Festlegung des Baus), ATTENTION in jedem Anfragezustand über Entscheidung 1 hinaus (insbesondere im Leerlauf), Sitzungszustand nach einem Fehler (Transaktionen), die ENVCHANGE-Antwort auf `RESETCONNECTION`, `RESETCONNECTION` außerhalb von `IDLE`, `NonCancellable` beim Schreiben eines Pakets, Abbildung statement- und batch-abbrechender Fehler, Zuordnung der übrigen eigenen Codes.

## Re-Evaluierungs-Trigger

Das Referenzclient-Szenario scheitert mit der Ein-Nachricht-Form (Entscheidung 1) oder mit `TDS_TIMEOUT` Class 16 (Entscheidung 2); ein Client mit Pooling verlangt ein ENVCHANGE oder setzt das Bit in einem anderen Zustand als `IDLE`; eine Messung am echten Server, die einer der fünf Setzungen widerspricht; Sitzungszustand jenseits der Handles (Transaktionen).

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-01 | Accepted (vom Nutzer entschieden) – Empfehlungen des Architects für die fünf Punkte übernommen; schließt offene Punkte von ADR-0004 und ADR-0007, ohne sie zu ändern | [ADR-0004](0004-session-zustandsautomat.md), [ADR-0007](0007-fehler-und-attention.md) |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
