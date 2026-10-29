# ADR-0003: Transportframework und Netzwerk-Laufzeit

**Status:** Proposed

**Datum:** 2026-10-29

**Autor:** pt9912

**Bezug:** [TDS-FA-01](../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-QA-02](../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-RB-01](../../../spec/lastenheft.md#tds-rb-01--implementierungssprache)

**Schärft:** [ARC-003](../../../spec/architecture.md#1-komponenten-übersicht), [ARC-008](../../../spec/architecture.md#1-komponenten-übersicht), [ARC-013](../../../spec/architecture.md#3-externe-abhängigkeiten), [SPEC-027](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge), [TDS-FA-01.a](../../../spec/spezifikation.md#tds-fa-01a--tcp-verbindung-und-prelogin)

---

## Kontext

Framing, Fragmentierung, Backpressure und TLS-Integration benötigen eine robuste JVM-I/O-Laufzeit. Die Wahl soll Protokollsemantik nicht mit Transport koppeln.

## Entscheidung

**Offen — noch keine Option gewählt.** Diese ADR dokumentiert die zu treffende Entscheidung, nicht bereits einen Beschluss. Vor `Accepted` sind ein Spike mit fragmentierten TDS-Paketen, TLS und ein Lasttest durchzuführen sowie Wartungsaufwand und Speicherverhalten zu vergleichen.

## Verglichene Alternativen

| Option                                       | Pro                                | Contra                                             |
| -------------------------------------------- | ---------------------------------- | -------------------------------------------------- |
| A — Netty                                    | Bewährte Pipeline und ByteBuf-I/O  | Komplexität bei Buffer-Lebensdauer                 |
| B — Java NIO                                 | Keine zusätzliche Frameworkbindung | Mehr eigener Netzwerkcode                          |
| C — Kotlin-Coroutines mit Socket-Abstraktion | Idiomatic Kotlin API               | Framing und Backpressure weiterhin selbst zu lösen |
| 0 — keine Festlegung vor dem ersten Transport-Spike | Keine Frameworkbindung vor dem Nachweis | Blockiert `ARC-003` und `ARC-013` |

## Konsequenzen

- **Positiv nach Entscheidung:** Framing, Backpressure und Pufferverwaltung sind an einer Stelle gelöst und prüfbar.
- **Negativ / Risiko:** Die Wahl bindet Buffer-Lebensdauer und Wartungsaufwand an das gewählte Framework.
- **Folgepflicht:** `ARC-003`, `ARC-008` und `ARC-013` auf die gewählte Laufzeit festlegen; Fuzz-Tests nach `SPEC-027` ergänzen.
- **Offen:** Framework und Pufferstrategie.

## Fitness Function (falls maschinell prüfbar)

| Tooling                                     | Regel                                                                                                                                 | Make-Target                         |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------- |
| JVM-/Protokolltests und .NET-Referenzclient | Fragmentierungs- und Fuzz-Tests mit beliebigen Paketgrenzen verursachen keinen Prozessabsturz; der Speicherbedarf bleibt unter Last begrenzt. Noch kein bestandenes Gate. | — (Gate noch einzurichten) |

## Re-Evaluierungs-Trigger

Nachweisbare Leaks, unzureichende Backpressure oder neue JVM-Runtime-Anforderungen.

## Geschichte

| Datum      | Ereignis                      | Verweis |
| ---------- | ----------------------------- | ------- |
| 2026-10-29 | Proposed – Entscheidung offen | —       |
