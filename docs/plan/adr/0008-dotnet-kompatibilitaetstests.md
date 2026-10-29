# ADR-0008: Referenzclient und .NET-Kompatibilitätstests

**Status:** Proposed

**Datum:** 2026-10-29

**Autor:** pt9912

**Bezug:** [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose)

**Schärft:** [SPEC-021](../../../spec/spezifikation.md#6-externe-verträge), [SPEC-025](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge), [SPEC-032](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge), [ARC-010](../../../spec/architecture.md#3-externe-abhängigkeiten)

---

## Kontext

Unit-Tests der TDS-Kodierung allein belegen nicht, dass SqlConnection.Open, ExecuteReader, Parameter und Cancel funktionieren.

Diese ADR legt fest, **wie** die Kompatibilität nachgewiesen wird (Testmatrix und Ausführung in der CI); den Inhalt des Clientprofils entscheidet ADR-0001.

## Entscheidung

**Offen — noch keine Option gewählt.** Diese ADR dokumentiert die zu treffende Entscheidung, nicht bereits einen Beschluss. Vor `Accepted` sind CI-Machbarkeit, Laufzeit und Abdeckung der kritischen Client-Operationen zu vergleichen; danach ist die verpflichtende Testmatrix zu beschließen.

## Verglichene Alternativen

| Option                                  | Pro                                | Contra                                    |
| --------------------------------------- | ---------------------------------- | ----------------------------------------- |
| A — ausschließlich JVM-Protokolltests   | Schnell und deterministisch        | Keine echte Client-Kompatibilitätsaussage |
| B — containerisierte .NET-Referenztests | Reproduzierbare End-to-End-Evidenz | CI-Laufzeit und Pflege                    |
| C — manuelle Tests gegen .NET-Anwendung | Realitätsnah                       | Nicht zuverlässig reproduzierbar          |
| 0 — keine Festlegung vor dem ersten CI-Versuch | Keine Vorfestlegung der Testumgebung | Blockiert `SPEC-025` und `SPEC-032` |

## Konsequenzen

- **Positiv nach Entscheidung:** Kompatibilitätsaussagen stützen sich auf reproduzierbare Läufe mit dem echten Client.
- **Negativ / Risiko:** CI-Laufzeit und die Pflege der Testmatrix verursachen laufenden Aufwand.
- **Folgepflicht:** `SPEC-021`, `SPEC-025` und `SPEC-032` auf die gewählte Umgebung festlegen; das Gate dazu anlegen.
- **Offen:** Pflichtumfang der Testmatrix und Häufigkeit der Läufe.

## Fitness Function (falls maschinell prüfbar)

| Tooling                                     | Regel                                                                                                                                 | Make-Target                         |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------- |
| JVM-/Protokolltests und .NET-Referenzclient | Die Testmatrix läuft containerisiert ohne lokale .NET-Installation auf dem Host und wird bei einem fehlgeschlagenen Szenario rot. Noch kein bestandenes Gate. | — (Gate noch einzurichten) |

## Re-Evaluierungs-Trigger

Neue SqlClient-Hauptversion oder nicht reproduzierbarer Kompatibilitätsfehler.

## Geschichte

| Datum      | Ereignis                      | Verweis |
| ---------- | ----------------------------- | ------- |
| 2026-10-29 | Proposed – Entscheidung offen | —       |
