# ADR-0006: Backend-Schnittstelle und Resultset-Streaming

**Status:** Proposed

**Datum:** 2026-10-29

**Autor:** pt9912

**Bezug:** [TDS-FA-05](../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-FA-08](../../../spec/lastenheft.md#tds-fa-08--backend-anbindung), [TDS-FA-10](../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-FA-11](../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend)

**Schärft:** [SPEC-002](../../../spec/spezifikation.md#spec-002--interner-query-vertrag), [SPEC-035](../../../spec/spezifikation.md#spec-035--tabellenbeschreibung-und-zeilenquelle-des-simulations-backends), [TDS-FA-11.a](../../../spec/spezifikation.md#tds-fa-11a--simulations-backend), [SPEC-031](../../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität), [ARC-006](../../../spec/architecture.md#1-komponenten-übersicht), [ARC-007](../../../spec/architecture.md#1-komponenten-übersicht), [ARC-015](../../../spec/architecture.md#1-komponenten-übersicht), [TDS-FA-05.a](../../../spec/spezifikation.md#tds-fa-05a--ergebniskodierung), [TDS-FA-08.a](../../../spec/spezifikation.md#tds-fa-08a--backend-vertrag), [TDS-FA-10.a](../../../spec/spezifikation.md#tds-fa-10a--mehrteilige-antworten-und-rpc-ausgabewerte)

---

## Kontext

Die eigene Query-Engine soll unabhängig vom TDS-Codec bleiben. Mehrere Resultsets, Update-Counts und Fehler während des Streamings müssen korrekt sequenziert werden.

`SPEC-002` skizziert den Backend-Vertrag mit einem `Flow<QueryEvent>` und `TDS-FA-08.a` verlangt Backpressure bis zum Transport. Die Zeilenquelle des Simulations-Backends (`SPEC-035`, `TDS-FA-11.a`) ist ein suspendierendes Pull-Interface; `TDS-FA-11` verlangt, dass eine Zeilenquelle über der Speichergrenze schrittweise übertragen wird. Beide Skizzen sprechen gegen Option A für große Ergebnisse. Sie entscheiden nichts: Die Wahl des Ergebnismodells bleibt offen.

## Entscheidung

**Offen — noch keine Option gewählt.** Diese ADR dokumentiert die zu treffende Entscheidung, nicht bereits einen Beschluss. Vor `Accepted` sind Prototypen für große Resultsets, mehrere Ergebnisse und Fehler nach der ersten Zeile gegeneinander zu testen.

## Verglichene Alternativen

| Option                                     | Pro                                    | Contra                                           |
| ------------------------------------------ | -------------------------------------- | ------------------------------------------------ |
| A — vollständig materialisierte Ergebnisse | Einfache Serialisierung                | Speicherbedarf und Latenz bei großen Ergebnissen |
| B — asynchroner Event-Stream               | Backpressure und mehrere Ergebnisteile | Komplexere Fehler- und Cancel-Semantik           |
| C — synchroner Iterator                    | Einfaches Backend-Interface            | Blockierungsrisiko und schwieriger Abbruch       |
| 0 — keine Festlegung vor den Prototypen | Keine Vorfestlegung der Backend-Schnittstelle | Blockiert `SPEC-002` und `SPEC-031` |

## Konsequenzen

- **Positiv nach Entscheidung:** Der Backend-Vertrag ist ohne TDS-Bytes beschrieben und für große Ergebnisse tragfähig.
- **Negativ / Risiko:** Backpressure und Abbruch machen den Vertrag komplexer als eine einfache Rückgabe.
- **Folgepflicht:** `SPEC-002` und `SPEC-031` auf die gewählte Schnittstelle festlegen; Backend-Vertrag als Datei anlegen.
- **Offen:** Ergebnismodell (Materialisierung, Event-Stream, Iterator) und Fehlersemantik im Strom.

## Fitness Function (falls maschinell prüfbar)

| Tooling                                     | Regel                                                                                                                                 | Make-Target                         |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------- |
| JVM-/Protokolltests und .NET-Referenzclient | Ein Resultset oberhalb der Speichergrenze wird ohne vollständige Materialisierung übertragen; ein Fehler nach der ersten Zeile erzeugt ERROR mit Abschluss oder ein Verbindungsende. Noch kein bestandenes Gate. | — (Gate noch einzurichten) |

## Re-Evaluierungs-Trigger

Backpressure-Probleme oder zusätzliche Backend-Ergebnisarten.

## Geschichte

| Datum      | Ereignis                      | Verweis |
| ---------- | ----------------------------- | ------- |
| 2026-10-29 | Proposed – Entscheidung offen | —       |
| 2026-10-29 | Bezug auf `TDS-FA-11` und `SPEC-035` ergänzt; Entscheidung weiter offen | [TDS-FA-11](../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) |
