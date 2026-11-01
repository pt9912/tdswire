# ADR-0029: Log und Telemetrie als Parameter des Einstiegs: Ablösung der Sätze zum Log in ADR-0021 und ADR-0025

**Status:** Accepted

**Datum:** 2026-11-01

**Autor:** pt9912

**Bezug:** [ADR-0020](0020-logformat-und-observability-schnittstelle.md), [ADR-0021](0021-oeffentlicher-integrations-einstieg.md), [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md), [ADR-0009](0009-authentifizierungsmodus.md), [TDS-FA-09](../../../spec/lastenheft.md#tds-fa-09--konfiguration-und-diagnose)

**Schärft:** nichts Neues in der Spezifikation. Die Spezifikations-Nachzüge stehen in der Folgepflicht von [ADR-0020](0020-logformat-und-observability-schnittstelle.md) und sind nicht Gegenstand.

**Supersedes (teilweise), jeweils nur wörtlich benannt; die Dateien der Vorgänger bleiben unverändert und `Accepted` ([AGENTS.md](../../../AGENTS.md) §3.5):**

- [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) Entscheidung 1, **nur** den Halbsatz „noch Log oder Telemetrie umleiten“ der ausdrücklichen Folge („ein Embedder kann über den Einstieg weder den Authentifizierungsadapter tauschen … noch Log oder Telemetrie umleiten“), und in Entscheidung 2 **nur** den Satz „Ändert [ADR-0020](0020-logformat-und-observability-schnittstelle.md) den Log-Schreiber auf den Generator von `jackson-core`, hängt auch das Log an Jackson, und Option b2 (Kern ohne Jackson) entfällt, solange sie gewählt ist“ (der Schreiber ist nach [ADR-0020](0020-logformat-und-observability-schnittstelle.md) Frage 1 ein Adapter im Testserver, nicht Teil der Bibliothek).
- [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) Entscheidung 1, **nur** die Aussage „`ServerLog` als Schnittstelle mit `NONE`“ als Teil der Bibliothek, und im Absatz „Wirkung der Bindung des Testservers“ **nur** die Klammer „(der Aufrufer des Einstiegs tauscht weder Authentifizierungsadapter noch Log)“, soweit sie das Log betrifft; für den Testserver bestätigt die Klammer jetzt nur noch den Authentifizierungsadapter. Entscheidung 7 wird nicht ersetzt, sondern von [ADR-0020](0020-logformat-und-observability-schnittstelle.md) Frage 1 geschärft (siehe Entscheidung 3).

Nicht ersetzt, sondern unverändert in Kraft: alle übrigen Entscheidungen von [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) und [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md). Der Status beider bleibt `Accepted`, weil nur die genannten Sätze abgelöst werden.

---

## Kontext

[ADR-0020](0020-logformat-und-observability-schnittstelle.md) (`Accepted`, 2026-11-01) entscheidet in Frage 1 Punkt 3: ein Parameter von `TdsServer.start` nimmt eine Senke für Ereignisse entgegen (Voreinstellung „keine Senke“); er wirkt auf Log und Telemetrie, nicht auf den Authentifizierungsadapter. Die Senke löst `ServerLog` samt `NONE` ab. [ADR-0020](0020-logformat-und-observability-schnittstelle.md) wählt dafür die Form „Schärfung, kein `Supersedes`“ (Folgepflicht 5) und legt dem Nutzer als Offen (b) die Frage vor, ob das genügt oder eine Folge-ADR die Sätze ausdrücklich ablösen soll.

Der Review der Annahme (Befund M-1) hält fest: [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) und [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) sagen weiter das Gegenteil. Wer sie zuerst liest, baut „kein Log-Parameter“. Für denselben Fall nutzt [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) ein teilweises `Supersedes`; Schärfung ist die schwächere Form und lässt den widersprechenden Wortlaut ohne Zeiger stehen.

Diese ADR führt keine neue Sachentscheidung ein. Sie formalisiert, was [ADR-0020](0020-logformat-und-observability-schnittstelle.md) angenommen hat, in der Form, die die Repo-Konvention für abgelöste Sätze vorsieht.

## Entscheidung

Offen (b) aus [ADR-0020](0020-logformat-und-observability-schnittstelle.md) wird so beantwortet: Die Sätze werden ausdrücklich abgelöst.

1. **Ersetzt wird** der Teil der Folge in [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) Entscheidung 1, der dem Embedder Log und Telemetrie abspricht. Es gilt [ADR-0020](0020-logformat-und-observability-schnittstelle.md) Frage 1 Punkt 3: ein Embedder kann über einen Parameter von `start` eine Senke übergeben und so Log und Telemetrie umleiten. Die Senke und das Ereignismodell stehen nach [ADR-0020](0020-logformat-und-observability-schnittstelle.md) in der öffentlichen Fläche (Folgepflicht 5 dort zu [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) Entscheidung 3).
2. **Ersetzt wird** die Andeutung in [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) Entscheidung 2, das Log hänge an `jackson-core`. Die Bibliothek trägt keinen Schreiber; das Format ist ein Adapter im Testserver ([ADR-0020](0020-logformat-und-observability-schnittstelle.md) Frage 1). Ob Option b2 (Kern ohne Jackson) wegen des Logs entfällt, entscheidet damit nicht das Log.
3. **Ersetzt wird** in [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) Entscheidung 1 die Festschreibung von `ServerLog` mit `NONE` als Teil der Bibliothek: an ihre Stelle tritt die Senke aus [ADR-0020](0020-logformat-und-observability-schnittstelle.md) mit Voreinstellung „keine Senke“. Die Klammer zum Testserver verliert den Teil „noch Log“: der Testserver übergibt die Senke seines JSON-Adapters wie jeder Embedder. Entscheidung 7 von [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) (Bereitschaftszeile schreibt der Testserver selbst, kein Log aus der Bibliothek „bis dahin“) gilt bis zum Bau der Senke weiter.
4. **Bleibt unverändert:** der Authentifizierungsadapter ist für den Embedder über den Einstieg weiter nicht tauschbar ([ADR-0021](0021-oeffentlicher-integrations-einstieg.md) Entscheidung 1, [ADR-0009](0009-authentifizierungsmodus.md)); `Wiring` und `Bootstrap` bleiben `internal`; der Einstieg ohne Senke schreibt nichts; alle übrigen Entscheidungen beider ADRs (Fassade, `BackendSource`, `StartOutcome`, Artefakt, Stabilitätszusage, Source-Set `integrator`, Produktschnitt, Testserver, E2E-Zuordnung).
5. **Kennzeichnung:** Form, Name und Ort der Senke und des Parameters sind Arbeitsnamen aus [ADR-0020](0020-logformat-und-observability-schnittstelle.md); diese ADR legt sie nicht fest.

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| **A — Teil-Supersedes nach dem Muster von [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) (gewählt)** | der abgelöste Wortlaut ist über das Feld `Supersedes` und den Index-Titel auffindbar; Verifier und Implementer lesen eine Linie; kein Eingriff in angenommene Dateien | eine ADR mehr |
| B — bei der Schärfung in [ADR-0020](0020-logformat-und-observability-schnittstelle.md) bleiben | kein Dokument | zwei angenommene ADRs sagen das Gegenteil, ohne dass ein Feld es anzeigt (Befund M-1) |
| C — die Sätze in [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) und [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) klarstellend ändern | Wortlaut stimmt | verletzt [AGENTS.md](../../../AGENTS.md) §3.5; auch „nur klarstellend“ ist eine Änderung einer angenommenen ADR |

## Konsequenzen

- Positiv: Die Entscheidungslage zum Log hat eine Linie; Offen (b) in [ADR-0020](0020-logformat-und-observability-schnittstelle.md) ist beantwortet.
- Negativ: Die abgelösten Sätze bleiben in [ADR-0021](0021-oeffentlicher-integrations-einstieg.md) und [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) im Text stehen und sind nur über diese ADR und den Index-Titel als abgelöst erkennbar (wie bei [ADR-0015](0015-spannungen-adr-gegen-spec.md)); die Index-Zeilen der Vorgänger tragen keinen Zeiger.
- **Folgepflicht (nach `Accepted`):** keine eigene. Bau und Spezifikations-Nachzug tragen die Folgearbeiten aus Folgepflicht 1, 2 und 5 von [ADR-0020](0020-logformat-und-observability-schnittstelle.md); dort ist dieser Punkt schon benannt. Die Lesart (a) aus Offen von [ADR-0020](0020-logformat-und-observability-schnittstelle.md) ist nicht Gegenstand.
- **Akzeptiertes Negativ (einmalig):** Die Befunde M-2 bis M-4 des Reviews (Abbruchgrund gegen Spezifikation, Importregel, Plan-Stand) sind nicht Gegenstand; ihre Träger stehen im Review.

## Fitness Function (falls maschinell prüfbar)

Keine eigene: die ADR ändert keinen Code. Die Prüfung dessen, was sie festhält, steht in der Fitness Function von [ADR-0020](0020-logformat-und-observability-schnittstelle.md); `make docs-check` prüft die Verweise dieser Datei.

## Offen

Nichts. Die Annahme liegt beim Nutzer; sie bestätigt eine in [ADR-0020](0020-logformat-und-observability-schnittstelle.md) bereits angenommene Sachentscheidung.

## Re-Evaluierungs-Trigger

Eine Änderung von [ADR-0020](0020-logformat-und-observability-schnittstelle.md) Frage 1 Punkt 3 (Parameter von `start`) durch eine Folge-ADR; dann ist auch diese ADR zu lesen.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-01 | Proposed – Formalisierung von Offen (b) aus [ADR-0020](0020-logformat-und-observability-schnittstelle.md) nach Befund M-1 des Reviews der Annahme; Status bleibt Proposed bis zur Annahme durch den Nutzer | [ADR-0020](0020-logformat-und-observability-schnittstelle.md), [ADR-0021](0021-oeffentlicher-integrations-einstieg.md), [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) |
| 2026-11-01 | Accepted – Annahme durch den Nutzer; beantwortet Offen (b) aus [ADR-0020](0020-logformat-und-observability-schnittstelle.md) | [ADR-0020](0020-logformat-und-observability-schnittstelle.md), [ADR-0021](0021-oeffentlicher-integrations-einstieg.md), [ADR-0025](0025-produktschnitt-bibliothek-und-testserver.md) |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
