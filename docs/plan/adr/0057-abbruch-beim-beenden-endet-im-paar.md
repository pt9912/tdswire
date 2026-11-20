# ADR-0057: Der Abbruch beim Beenden des Servers endet im Paar aus `DONE[ERROR]` und `DONE[ATTN]` (ersetzt teilweise ADR-0027)

**Status:** Accepted (vom Nutzer entschieden, 2026-11-20)

**Datum:** 2026-11-20

**Autor:** pt9912

**Bezug:** [TDS-FA-07](../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0054](0054-antwort-auf-attention-in-zwei-nachrichten.md), [ADR-0027](0027-abbruch-und-zuruecksetzen.md), [ADR-0007](0007-fehler-und-attention.md)

**Schärft:** [SPEC-044](../../../spec/spezifikation.md#spec-044--geordnetes-beenden) (Nr. 3 und 4, Form des Abbruchs nach Ablauf von `SHUTDOWN_GRACE`), [SPEC-029](../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung) (Antwort auf ATTENTION, Herkunft). Diese ADR ändert `spec/` nicht; Spezifikation, Code und Nutzer-Doku stehen im Branch bereits so (siehe Konsequenzen).

**Supersedes (teilweise), nur wörtlich benannt, wirksam erst mit dem Status `Accepted`; die Datei von [ADR-0027](0027-abbruch-und-zuruecksetzen.md) bleibt unverändert und `Accepted` ([AGENTS.md](../../../AGENTS.md) §3.5):** [ADR-0027](0027-abbruch-und-zuruecksetzen.md), **nur** die folgenden Aussagen, jeweils nur soweit sie die Form der Antwort auf ATTENTION im Stream oder beim Beenden betreffen:

- Entscheidung 2, erster Punkt (*Cancel*): „kein ERROR-Token, nur `DONE[ATTN]` nach Entscheidung 1“; ersetzt wird „nur `DONE[ATTN]` nach Entscheidung 1“ durch die Form nach [ADR-0054](0054-antwort-auf-attention-in-zwei-nachrichten.md) (das Paar). „Kein ERROR-Token“, „Cancel ist kein Fehler“ und „die Verbindung bleibt offen“ bleiben.
- Entscheidung 2, Ausnahme (c): „beim geordneten Beenden endet die Verbindung auch nach `DONE[ATTN]`“; ersetzt wird „nach `DONE[ATTN]`“ durch „nach dem Paar aus `DONE[ERROR]` und `DONE[ATTN]`“.
- Fitness Function: „Nach `Cancel()` kommt genau ein `DONE[ATTN]`“; ersetzt wird „genau ein `DONE[ATTN]`“ durch „das Paar aus `DONE[ERROR]` (ohne ERROR-Token) und `DONE[ATTN]`“, für `Cancel()` im Stream wie für den Abbruch beim Beenden.

Es bleiben: die Bestätigung `DONE[ATTN]` als einzelne Nachricht für ATTENTION **nach** Ende der Antwort und im Leerlauf, Entscheidung 2 im Übrigen (`QUERY_TIMEOUT` als Fehler, Ausnahmen (a) und (b)), 3, 4 (Schließen nach `TDS_SHUTDOWN` nach ERROR und DONE) und 5 sowie die von [ADR-0054](0054-antwort-auf-attention-in-zwei-nachrichten.md) ersetzten Aussagen in der dort genannten Reichweite.

---

## Kontext

[ADR-0054](0054-antwort-auf-attention-in-zwei-nachrichten.md) (Accepted, 2026-11-19) lässt den Server auf ATTENTION in `RUNNING` und `STREAMING` in zwei Nachrichten antworten, wie der echte Server es am Draht tut. Es ersetzt dafür [ADR-0027](0027-abbruch-und-zuruecksetzen.md) Entscheidung 1, aber nicht die Stellen von ADR-0027, die dieselbe Form an anderer Stelle erneut nennen: den ersten Punkt von Entscheidung 2, die Ausnahme (c) (Beenden) und die Fitness Function. ADR-0054 sagt zum Beenden nichts.

Das geordnete Beenden ([SPEC-044](../../../spec/spezifikation.md#spec-044--geordnetes-beenden)) bricht nach Ablauf von `SHUTDOWN_GRACE` jede noch laufende Anfrage „wie bei ATTENTION“ ab (Cancel an das Backend, [SPEC-029](../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung)). Mit ADR-0054 ist „wie bei ATTENTION“ das Paar; die Aussage von ADR-0027, die Verbindung ende „nach `DONE[ATTN]`“, und die Zeile der Fitness Function nennen weiter die Ein-Nachricht-Form. Der Nutzer hat am 2026-11-20 entschieden: **Der Abbruch beim Beenden endet ebenfalls als Paar `DONE[ERROR]` + `DONE[ATTN]`, danach endet die Verbindung.** Maßstab ist derselbe Grundsatz wie bei ADR-0054 („Spezifikation ist Zielbild“) und die Einheitlichkeit: eine Form für den Abbruch einer laufenden Antwort, gleich ob der Client (`Cancel()`) oder der Server (Beenden) ihn auslöst.

**Beleg:** Für die Form beim Beenden **keiner**. Gemessen ist nur die Antwort des echten Servers auf ein ATTENTION des Clients (Messzeilen M1, M2, M5 in ADR-0054); ein Herunterfahren des echten Servers mit laufender Anfrage ist nicht gemessen ([ADR-0027](0027-abbruch-und-zuruecksetzen.md) Entscheidung 4 führt das Schließen beim Beenden ebenfalls als Setzung). Die Aussage dieser ADR ist deshalb eine Setzung des Nutzers und trägt nicht weiter ([AGENTS.md](../../../AGENTS.md) §3.8).

## Entscheidung

Wir wählen **Option A**: Wird eine laufende Anfrage nach Ablauf von `SHUTDOWN_GRACE` abgebrochen, antwortet der Server mit der Form von [ADR-0054](0054-antwort-auf-attention-in-zwei-nachrichten.md) Nr. 1 und 2: das Ende der abgebrochenen Antwort als `DONE` mit Status `ERROR` ohne ERROR-Token, danach `DONE` mit Status `ATTN`, beide unmittelbar nacheinander; danach endet die Verbindung. Setzung des Nutzers vom 2026-11-20, am echten Server nicht gemessen.

1. **Form.** Gleich der Cancel-Antwort im Stream (ADR-0054 Nr. 1): kein ERROR-Token, kein SESSIONSTATE. Es gibt keine eigene Beenden-Form.
2. **Verbindungsende.** Nach dem vollständig geschriebenen Paar endet die Verbindung. „Endet nach `DONE[ATTN]`“ bleibt zutreffend, weil `DONE[ATTN]` die letzte Nachricht des Paares ist. Das Ende führt in einem Übergang nach `CLOSED` ([SPEC-001](../../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung)); geschlossen wird nach dem Schreiben.
3. **Unverändert:** Beachtet das Backend den Cancel nicht innerhalb `CANCEL_TIMEOUT`, wird die Verbindung ohne weitere Bytes getrennt, auch wenn die erste Nachricht noch nicht geschrieben ist ([SPEC-044](../../../spec/spezifikation.md#spec-044--geordnetes-beenden) Nr. 4, ADR-0054 Nr. 4); eine nach dem Beginn eingetroffene Anfrage wird mit `TDS_SHUTDOWN` beantwortet (ERROR und `DONE`, dann Ende der Verbindung, [ADR-0027](0027-abbruch-und-zuruecksetzen.md) Entscheidung 4); ein ATTENTION des Clients im Beenden folgt [SPEC-029](../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung) und ADR-0054, der erste Übergang gewinnt.

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| **A — das Paar auch beim Beenden (gewählt, Nutzerentscheid 2026-11-20)** | eine Form für jeden Abbruch einer laufenden Antwort; konsistent mit ADR-0054 und der Spezifikation als Zielbild; kein zweiter Pfad im Code | beim Beenden nicht am echten Server gemessen (Setzung) |
| B — beim Beenden weiter ein einzelnes `DONE[ATTN]` | entspricht dem Wortlaut von ADR-0027 | vom Nutzer verworfen: zwei Formen für denselben Abbruch; der Beenden-Pfad bliebe als einziger bei der Ein-Nachricht-Form, die ADR-0054 für den Abbruch einer laufenden Antwort ablöst |
| C — Verbindung beim Beenden nach der Frist ohne Abschluss trennen | einfach; keine Form ohne Messung zu setzen | der Client sieht einen Transportfehler statt einer abgeschlossenen Antwort; widerspricht [SPEC-044](../../../spec/spezifikation.md#spec-044--geordnetes-beenden) Nr. 3 („nie ein halber Tokenstrom“) |
| D — nichts festlegen | keine Vorfestlegung | ADR-0027 nennt weiter „nur `DONE[ATTN]`“; Spezifikation und Code widersprächen einer angenommenen ADR ohne tragende Entscheidung |

## Konsequenzen

- Positiv: Spezifikation, Code und angenommene Entscheidungen nennen für den Abbruch einer laufenden Antwort dieselbe Form; keine ADR-Aussage verneint mehr, was gebaut ist.
- Negativ: Die Form beim Beenden ist eine Setzung ohne Messung; ein Client, der beim Beenden die Ein-Nachricht-Form erwartet, ist nicht gemessen (der Referenzclient nimmt beide Formen an, ADR-0054 M6).
- **Folgepflicht, Spec-Nachzug:** keine offene. [SPEC-044](../../../spec/spezifikation.md#spec-044--geordnetes-beenden) Nr. 3 und 4 (Form, Herkunft „Setzung des Nutzers vom 2026-11-20“), Code und Nutzer-Doku stehen im Branch bereits so; diese ADR trägt die Entscheidung nach, die die Spezifikation schon führt.
- **Akzeptiertes Negativ (einmalig, mit Grund):** Der echte Server verhält sich beim Beenden mit laufender Anfrage möglicherweise anders (etwa mit einem ERROR-Token oder ohne zweite Nachricht). Das ist ungemessen und bleibt es: die Form ist als Setzung gekennzeichnet und behauptet nichts über den echten Server; ein Herunterfahren des echten SQL Servers mit laufender Anfrage ist kein Szenario, das ein Client des Produkts auslöst. Kein eigener Folgeauftrag; der Re-Evaluierungs-Trigger fängt eine spätere Messung.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| JVM-/Protokolltests des Testservers | Eine beim Beenden nach `SHUTDOWN_GRACE` abgebrochene Anfrage endet mit `DONE[ERROR]` ohne ERROR-Token und `DONE[ATTN]`, danach endet die Verbindung; bleibt der Cancel ohne Wirkung, wird nach `CANCEL_TIMEOUT` ohne weitere Bytes getrennt. Belegt durch die Tests des Branches, nicht durch eine Messung am echten Server. | `make test` |

## Re-Evaluierungs-Trigger

Eine Messung am echten Server, die der Form beim Beenden widerspricht, oder ein Client, der an der Zwei-Nachrichten-Form im Beenden scheitert.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-20 | Accepted; Entscheidung des Nutzers: Option A (Paar auch beim Beenden), Setzung ohne Messung | [ADR-0054](0054-antwort-auf-attention-in-zwei-nachrichten.md), [ADR-0027](0027-abbruch-und-zuruecksetzen.md) |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
