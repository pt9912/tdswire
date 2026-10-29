# ADR-0004: Session-Zustandsautomat und Protokollverarbeitung

**Status:** Proposed

**Datum:** 2026-10-29

**Autor:** pt9912

**Bezug:** [TDS-FA-01](../../../spec/lastenheft.md#tds-fa-01--verbindungsaufbau-und-protokollverhandlung), [TDS-FA-02](../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [TDS-FA-07](../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe)

**Schärft:** [SPEC-001](../../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung), [SPEC-028](../../../spec/spezifikation.md#spec-028--prelogin-tls-und-login7-zustands--und-antwortvertrag), [SPEC-029](../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung), [ARC-004](../../../spec/architecture.md#1-komponenten-übersicht), [ARC-015](../../../spec/architecture.md#1-komponenten-übersicht), [TDS-FA-02.a](../../../spec/spezifikation.md#tds-fa-02a--login7-und-sitzung), [TDS-FA-07.a](../../../spec/spezifikation.md#tds-fa-07a--attention-und-cleanup)

---

## Kontext

PRELOGIN, LOGIN7, Requests und ATTENTION haben zustandsabhängige Regeln. Unerlaubte Übergänge dürfen keinen inkonsistenten Antwortstrom erzeugen.

## Entscheidung

**Offen — noch keine Option gewählt.** Diese ADR dokumentiert die zu treffende Entscheidung, nicht bereits einen Beschluss. Vor `Accepted` sind eine Zustands- und Ereignismatrix mit negativen Übergängen sowie Connection-Reuse-Tests zu erstellen; dann ist zu entscheiden.

## Verglichene Alternativen

| Option                                   | Pro                               | Contra                                   |
| ---------------------------------------- | --------------------------------- | ---------------------------------------- |
| A — expliziter endlicher Zustandsautomat | Übergänge prüfbar und fuzzbar     | Mehr Zustandsmodellierung                |
| B — Handler-Kette mit implizitem Zustand | Einfacher Start                   | Fehlerfälle schwer vollständig zu prüfen |
| C — actorbasierte Session                | Sequenzielle Ereignisverarbeitung | Zusätzliche Runtime-Komplexität          |
| 0 — keine Festlegung vor der Zustandsmatrix | Keine Vorfestlegung der Session-Architektur | Blockiert `SPEC-001`, `SPEC-028` und `SPEC-029` |

## Konsequenzen

- **Positiv nach Entscheidung:** Zulässige und unzulässige Zustandsübergänge sind vollständig prüfbar.
- **Negativ / Risiko:** Die Zustandsmodellierung kostet Aufwand vor dem ersten lauffähigen Login.
- **Folgepflicht:** `SPEC-001`, `SPEC-028` und `SPEC-029` auf die gewählte Struktur festlegen; Matrix-Tests ergänzen.
- **Offen:** Zustandsmodell (explizit oder implizit) und Nebenläufigkeitsmodell.

## Fitness Function (falls maschinell prüfbar)

| Tooling                                     | Regel                                                                                                                                 | Make-Target                         |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------- |
| JVM-/Protokolltests und .NET-Referenzclient | Jeder in der Zustandsmatrix ungültige Übergang wird abgelehnt; zwei parallele Sitzungen beeinflussen sich nicht. Noch kein bestandenes Gate. | — (Gate noch einzurichten) |

## Re-Evaluierungs-Trigger

Neue TDS-Nachrichtentypen oder nicht deterministische Session-Races.

## Geschichte

| Datum      | Ereignis                      | Verweis |
| ---------- | ----------------------------- | ------- |
| 2026-10-29 | Proposed – Entscheidung offen | —       |
