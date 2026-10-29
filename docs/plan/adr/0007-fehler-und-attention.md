# ADR-0007: Fehlerbehandlung und ATTENTION-Semantik

**Status:** Proposed

**Datum:** 2026-10-29

**Autor:** pt9912

**Bezug:** [TDS-FA-06](../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), [TDS-FA-07](../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-07](../../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität)

**Schärft:** [SPEC-044](../../../spec/spezifikation.md#spec-044--geordnetes-beenden), [SPEC-047](../../../spec/spezifikation.md#3-defaults-und-konstanten), [SPEC-048](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [SPEC-049](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [SPEC-029](../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung), [SPEC-031](../../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität), [SPEC-011](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [SPEC-016](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [ARC-015](../../../spec/architecture.md#1-komponenten-übersicht), [TDS-FA-06.a](../../../spec/spezifikation.md#tds-fa-06a--fehlerabbildung), [TDS-FA-07.a](../../../spec/spezifikation.md#tds-fa-07a--attention-und-cleanup)

---

## Kontext

Ein Cancel oder Streaming-Fehler darf den TDS-Antwortstrom nicht desynchronisieren. ERROR, DONE und ATTENTION-Bestätigung müssen zum Sessionzustand passen.

Dasselbe gilt für zwei Fälle, die keinen Client-Abbruch voraussetzen: das geordnete Beenden des Servers (`SPEC-044`, Frist `SPEC-047`, Fehler `TDS_SHUTDOWN` nach `SPEC-049`) und interne Fehler (`SPEC-048`). Auch dort endet eine laufende Antwort regulär oder die Verbindung wird getrennt, nie mit einem halben Tokenstrom, und ein Fehler nach Beginn des Beendens führt nie zur Weiterverwendung der Verbindung.

## Entscheidung

**Offen — noch keine Option gewählt.** Diese ADR dokumentiert die zu treffende Entscheidung, nicht bereits einen Beschluss. Vor `Accepted` ist eine Fehler-/Cancel-Matrix gegen einen echten .NET-Client zu prüfen, einschließlich der Wiederverwendung derselben Verbindung.

## Verglichene Alternativen

| Option                                    | Pro                                       | Contra                                            |
| ----------------------------------------- | ----------------------------------------- | ------------------------------------------------- |
| A — Verbindung bei jedem Fehler schließen | Einfache Isolation                        | Schlechte Client-Wiederverwendung                 |
| B — zustandsabhängige Recovery            | Wiederverwendung nach definierten Fehlern | Komplexe Token- und Cancel-Sequenz                |
| C — generisches Retry im Server           | Kann temporäre Fehler abfedern            | Gefahr doppelter Ausführung und falscher Semantik |
| 0 — keine Festlegung vor der Fehler-/Cancel-Matrix | Keine Vorfestlegung der Recovery-Regeln | Blockiert `SPEC-029`, `SPEC-031` und `SPEC-016` |

## Konsequenzen

- **Positiv nach Entscheidung:** Nach jedem Fehlerfall ist definiert, ob die Verbindung weiterverwendet werden darf.
- **Negativ / Risiko:** Zustandsabhängige Recovery erhöht die Zahl der zu testenden Token- und Cancel-Sequenzen.
- **Folgepflicht:** `SPEC-029`, `SPEC-031`, `SPEC-011` und `SPEC-016` auf die gewählte Recovery-Regel festlegen.
- **Offen:** Fehlerklassen, die eine Wiederverwendung der Verbindung erlauben; ob nach `TDS_SHUTDOWN` und nach einem internen Fehler (`SPEC-048`) die Verbindung stets endet, wie `SPEC-044` und `SPEC-049` es vorsehen, oder die Recovery-Regel Ausnahmen kennt.

## Fitness Function (falls maschinell prüfbar)

| Tooling                                     | Regel                                                                                                                                 | Make-Target                         |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------- |
| JVM-/Protokolltests und .NET-Referenzclient | Nach Fehler und ATTENTION liefert die unmittelbar folgende Abfrage auf derselben Verbindung nur ihre eigene Antwort; ist das nicht sicher, ist die Verbindung geschlossen. Noch kein bestandenes Gate. | — (Gate noch einzurichten) |

## Re-Evaluierungs-Trigger

Neue Recovery-Lücke oder reproduzierbare Desynchronisation.

## Geschichte

| Datum      | Ereignis                      | Verweis |
| ---------- | ----------------------------- | ------- |
| 2026-10-29 | Proposed – Entscheidung offen | —       |
| 2026-10-29 | Bezug auf `SPEC-044`, `SPEC-047`, `SPEC-048`, `SPEC-049` (geordnetes Beenden, interne Fehler) ergänzt; Entscheidung weiter offen | [SPEC-044](../../../spec/spezifikation.md#spec-044--geordnetes-beenden) |
