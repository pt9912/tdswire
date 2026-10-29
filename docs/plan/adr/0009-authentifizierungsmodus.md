# ADR-0009: Authentifizierungsmodus und Authentifizierungsadapter

**Status:** Proposed

**Datum:** 2026-10-29

**Autor:** pt9912

**Bezug:** [TDS-FA-02](../../../spec/lastenheft.md#tds-fa-02--anmeldung-und-sitzungsverwaltung), [TDS-QA-03](../../../spec/lastenheft.md#tds-qa-03--sicherheit)

**Schärft:** [SPEC-004](../../../spec/spezifikation.md#spec-004--testprofil), [SPEC-012](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), [ARC-002](../../../spec/architecture.md#1-komponenten-übersicht), [ARC-004](../../../spec/architecture.md#1-komponenten-übersicht), [TDS-FA-02.a](../../../spec/spezifikation.md#tds-fa-02a--login7-und-sitzung)

---

## Kontext

Das Lastenheft verlangt, den initial unterstützten Authentifizierungsmodus vor der Implementierung der Anmeldung festzulegen. Die Wahl bestimmt den Inhalt von LOGIN7, die Schnittstelle zum Authentifizierungsadapter und die Sicherheitsaussagen. Windows Integrated Authentication, Kerberos und Entra-ID sind im Lastenheft für die erste Version ausgeschlossen.

## Entscheidung

**Offen — noch keine Option gewählt.** Diese ADR dokumentiert die zu treffende Entscheidung, nicht bereits einen Beschluss. Vor `Accepted` sind die LOGIN7-Felder des Referenzclients im festgelegten Profil zu beobachten und die Anforderungen des Betreibers an Benutzerverwaltung und Geheimnisschutz zu dokumentieren.

## Verglichene Alternativen

| Option                                                  | Pro                                          | Contra                                                  |
| ------------------------------------------------------- | -------------------------------------------- | ------------------------------------------------------- |
| A — SQL-Authentifizierung über austauschbaren Adapter   | Betreiber bindet eigene Benutzerverwaltung an | Adapter-Vertrag und Geheimnisbehandlung zu definieren   |
| B — fest konfigurierte Benutzerliste                    | Einfach und ohne externe Abhängigkeit         | Nur für Test und kleine Betriebe geeignet               |
| C — Weiterreichen der Zugangsdaten an das Backend       | Backend entscheidet allein                    | Backend sieht Geheimnisse; Vertrag nicht mehr TDS-frei  |
| 0 — keine Festlegung vor dem ersten LOGIN7-Mitschnitt   | Keine Vorfestlegung                           | Blockiert `SPEC-012` und die Anmeldung nach `TDS-FA-02` |

## Konsequenzen

- **Positiv nach Entscheidung:** Der Anmeldepfad und seine Fehlerfälle sind festgelegt und prüfbar.
- **Negativ / Risiko:** Der gewählte Modus begrenzt die unterstützten Clientkonfigurationen.
- **Folgepflicht:** `SPEC-004` und `SPEC-012` auf den gewählten Modus festlegen; Adapter-Vertrag in `ARC-002` beschreiben.
- **Offen:** Modus, Adapter-Schnittstelle und Umgang mit Geheimnissen.

## Fitness Function (falls maschinell prüfbar)

| Tooling                                     | Regel                                                                                                                                                | Make-Target                    |
| ------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------ |
| JVM-/Protokolltests und .NET-Referenzclient | Gültige Zugangsdaten führen zu einer nutzbaren Sitzung, ungültige zu einem definierten Anmeldefehler ohne Sitzung; Zugangsdaten erscheinen in keinem Log. Noch kein bestandenes Gate. | — (Gate noch einzurichten)     |

## Re-Evaluierungs-Trigger

Ein neuer unterstützter Authentifizierungsmodus im Clientprofil oder eine neue Sicherheitsrichtlinie des Betreibers.

## Geschichte

| Datum      | Ereignis                      | Verweis |
| ---------- | ----------------------------- | ------- |
| 2026-10-29 | Proposed – Entscheidung offen | —       |
