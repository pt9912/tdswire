# ADR-0053: Lesart zu TDS 8.0 in ADR-0007 und ADR-0008 folgt ADR-0028 (ersetzt teilweise ADR-0007 und ADR-0008)

**Status:** Proposed

**Datum:** 2026-11-17

**Autor:** pt9912

**Bezug:** [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0007](0007-fehler-und-attention.md), [ADR-0008](0008-dotnet-kompatibilitaetstests.md), [ADR-0028](0028-referenzinstanz-fuer-tds-8-0.md), [Messprotokoll](../../messungen/referenzabgleich.md)

**Schärft:** [SPEC-045](../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig), [SPEC-050](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) (keine neue Aussage; die Lesart steht dort seit [ADR-0028](0028-referenzinstanz-fuer-tds-8-0.md)).

**Supersedes (teilweise), jeweils nur wörtlich benannt, wirksam erst mit dem Status `Accepted`; die Dateien von [ADR-0007](0007-fehler-und-attention.md) und [ADR-0008](0008-dotnet-kompatibilitaetstests.md) bleiben unverändert und `Accepted` ([AGENTS.md](../../../AGENTS.md) §3.5):**

- [ADR-0007](0007-fehler-und-attention.md), Abschnitt „Nicht gemessen“, Punkt „TDS 8.0 und TLS“: **nur** der Teilsatz „der Server R bietet TDS 8.0 nicht an ([ADR-0008](0008-dotnet-kompatibilitaetstests.md))“. Der Rest des Punktes („Alle Läufe mit `Encrypt=false`“, „T hat keine TLS-Schicht“, „Dass Fehler- und ATTENTION-Framing unter TDS 8.0 gleich sind, ist **abgeleitet, nicht gemessen**“) bleibt wahr und gilt weiter.
- [ADR-0008](0008-dotnet-kompatibilitaetstests.md), Pins: **nur** der Teilsatz „der Grund, TDS 8.0 werde nicht angeboten, ist gefolgert“. Die Folge „mit `2022-latest` scheitert `Encrypt=Strict`“ bleibt wahr.
- [ADR-0008](0008-dotnet-kompatibilitaetstests.md), Konsequenzen, Negativ / Risiko: **nur** der Teilsatz „bliebe also allein durch B belegt“ (der Satz davor, „Der TDS-8.0-Pfad ließ sich an SQL Server 2022 nicht abgleichen (`Encrypt=Strict` scheitert)“, bleibt wahr, und „B selbst ist für TLS und TDS 8.0 nicht gemessen“ ebenso).
- [ADR-0008](0008-dotnet-kompatibilitaetstests.md), Aufzählung „Offen:“: **nur** die Nennung „der SQL-Server-Tag für TDS 8.0“.

Die drei Aussagen derselben Gruppe, die [ADR-0028](0028-referenzinstanz-fuer-tds-8-0.md) schon wörtlich ersetzt (Messung D: „bietet TDS 8.0 nicht an“, „Image-Tag … wurde nicht ermittelt“, „TLS 1.3 also kein Maßstab“), wiederholt diese ADR nicht.

**Regeln:** Baseline-Regelwerk `modul-04-adrs.md` §Ziel-Form: ADR (MADR).

---

## Kontext

[ADR-0028](0028-referenzinstanz-fuer-tds-8-0.md) (`Accepted`, 2026-11-13) ersetzt drei Sätze von [ADR-0008](0008-dotnet-kompatibilitaetstests.md) wörtlich. Die Prüfung der Entscheidungs-Trigger bei der Closure der Welle zum Referenzabgleich und zur Veröffentlichung fand vier weitere Stellen mit derselben alten Lesart („2022 bietet TDS 8.0 nicht an“, „Tag nicht ermittelt“), eine in [ADR-0007](0007-fehler-und-attention.md), drei in [ADR-0008](0008-dotnet-kompatibilitaetstests.md); sie stehen nicht in der `Supersedes`-Liste von [ADR-0028](0028-referenzinstanz-fuer-tds-8-0.md). Eine `Accepted`-ADR wird nicht nachgebessert ([AGENTS.md](../../../AGENTS.md) §3.5); der Nutzer beauftragte die Folge-ADR am 2026-11-17 („ja folge adr (7 8)“).

**Was die alte Lesart sagt.** „SQL Server 2022 bietet TDS 8.0 nicht an“, dazu gefolgert, der Tag für TDS 8.0 sei unbekannt und der Pfad allein durch den Stub-Lauf B belegbar.

**Was gemessen ist** (Bedingung für alle Zeilen laut [ADR-0028](0028-referenzinstanz-fuer-tds-8-0.md) und [Messprotokoll](../../messungen/referenzabgleich.md), B-3 bis B-6: Einzelmessungen vom 2026-11-01, ein Host, `Microsoft.Data.SqlClient` 7.1.1, selbst erzeugtes Zertifikat über `tlscert` und `tlskey`, beim Client Vertrauensanker):

- `2022-latest`: `Encrypt=Strict` gelingt unter vier selbst erzeugten Zertifikatsvarianten nicht; das Log nennt eine fehlende gültige Zertifikatskonfiguration für Strict, der Server erkennt den Versuch also und lehnt ihn ab. „Bietet nicht an“ ist damit nicht belegt; ob eine andere Konfiguration unter Linux `Strict` auf 2022 erlaubt, ist **nicht geklärt**. Die Herstellerseite zu TDS 8.0 nennt SQL Server 2022 als unterstützt, ohne Aussage für Linux oder Container.
- `2025-latest`: `Encrypt=Strict` gelingt mit diesem Zertifikat ohne Zusatzeinstellung; das Log nennt TLS 1.0 bis 1.3. Nicht gemessen: Verhalten mit dem Standardzertifikat ohne `tlscert`.
- Dass `2025-latest` die Referenzinstanz des TDS-8.0-Pfads ist, ist eine **Setzung** von [ADR-0028](0028-referenzinstanz-fuer-tds-8-0.md) (vom Nutzer am 2026-11-13 angenommen), keine Messung.

**Was nicht gemessen ist und hier nicht behauptet wird:** Fehler- und ATTENTION-Framing unter TDS 8.0 (weder an 2022 noch an 2025; das Messprotokoll nennt TDS 8.0 am Live-Lauf als nicht abgeglichen), Pooling unter TDS 8.0, andere Clients. Diese ADR misst nichts.

## Entscheidung

1. **Geltende Lesart.** Wo [ADR-0007](0007-fehler-und-attention.md) oder [ADR-0008](0008-dotnet-kompatibilitaetstests.md) zu TDS 8.0 am Referenzserver sprechen, gilt die Lage aus [ADR-0028](0028-referenzinstanz-fuer-tds-8-0.md) und dem Kontext oben: `2022-latest` nimmt `Encrypt=Strict` unter den gemessenen Bedingungen nicht an, Ursache nicht geklärt; `2025-latest` nimmt es an und ist (Setzung) die Referenzinstanz des TDS-8.0-Pfads.
2. **Ersetzt werden genau die vier Teilsätze der `Supersedes`-Liste:**
   - [ADR-0007](0007-fehler-und-attention.md) „der Server R bietet TDS 8.0 nicht an“ wird gelesen als: R (`2022-latest`) nimmt `Encrypt=Strict` in der Messung vom 2026-11-01 nicht an, Ursache nicht geklärt. Die Läufe von R bleiben Läufe mit `Encrypt=false`; die Aussage über das Framing unter TDS 8.0 bleibt „abgeleitet, nicht gemessen“.
   - [ADR-0008](0008-dotnet-kompatibilitaetstests.md) „der Grund, TDS 8.0 werde nicht angeboten, ist gefolgert“ wird gelesen als: Der Grund ist nicht geklärt (nicht „gefolgert“); gemessen ist nur das Scheitern von `Strict` an `2022-latest` unter vier Zertifikatsvarianten.
   - [ADR-0008](0008-dotnet-kompatibilitaetstests.md) „bliebe also allein durch B belegt“ wird gelesen als: An `2022-latest` bleibt der Pfad ohne Abgleich; an `2025-latest` gelingt `Strict` (Messung), und die Instanz ist die Referenz des Pfads (Setzung). Ob und wie weit der Pfad dort inhaltlich abgeglichen ist, ist nicht gemessen.
   - [ADR-0008](0008-dotnet-kompatibilitaetstests.md) „der SQL-Server-Tag für TDS 8.0“ als offener Punkt: beantwortet für die Annahme von `Strict` durch `2025-latest` ([ADR-0028](0028-referenzinstanz-fuer-tds-8-0.md)); offen bleibt allein die Ursache an 2022.
3. **Nichts anderes ändert sich.** Insbesondere bleibt jede Messaussage der beiden ADRs über `2022-latest` (Class 20, TLS 1.0 bis 1.2 im Log, Cancel, Fehlernummern) bestehen; sie war richtig und ist nur zu eng gelesen worden. Die Dateien von [ADR-0007](0007-fehler-und-attention.md) und [ADR-0008](0008-dotnet-kompatibilitaetstests.md) bleiben unverändert; die Indexzeilen führen nach Annahme den Vermerk „teilweise ersetzt durch ADR-0053“.

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| A — nichts tun | kein Aufwand | zwei `Accepted`-ADRs behaupten weiter, 2022 „biete TDS 8.0 nicht an“; wer nur diese liest, nimmt eine Ursache als gemessen, die es nicht ist (§3.8); der Befund der Closure bliebe offen |
| B — [ADR-0028](0028-referenzinstanz-fuer-tds-8-0.md) im Index als allein maßgeblich erklären, ohne `Supersedes` | billig, kein neues Dokument | eine Vermerk-Zeile im Index ist keine Entscheidung mit Wirkung auf die Vorgänger; ein Leser der Datei sieht ihn nicht; die Vorgänger-Dateien stehen ohne Zeiger da |
| **C — diese Folge-ADR mit wörtlich benanntem `Supersedes (teilweise)`** | der Konfliktpfad des Repos ([AGENTS.md](../../../AGENTS.md) §3.5): die Ablösung ist benannt, die Vorgänger bleiben `Accepted` und unverändert; der Index zeigt den Zeiger | ein Dokument mehr, vier Teilsätze Wortlaut zu pflegen |
| D — die Dateien von [ADR-0007](0007-fehler-und-attention.md) und [ADR-0008](0008-dotnet-kompatibilitaetstests.md) „klarstellend“ ändern | ein Leser findet die richtige Lesart an der Stelle | verletzt [AGENTS.md](../../../AGENTS.md) §3.5; nicht zulässig |

Empfehlung: **Option C**. Die Wahl liegt beim Nutzer.

## Konsequenzen

- Positiv: die vier Stellen sind abgelöst, statt als Messaussage weiterzuleben; der Rest der Messaussagen bleibt unberührt.
- Negativ: [ADR-0007](0007-fehler-und-attention.md) und [ADR-0008](0008-dotnet-kompatibilitaetstests.md) lesen sich ohne den Index weiter mit der alten Lesart; Träger des Zeigers ist der Indexvermerk nach Annahme.
- Folgepflicht nach Annahme (Planner oder Implementer, Doku): der Vermerk „teilweise ersetzt durch ADR-0053“ in den Indexzeilen von [ADR-0007](0007-fehler-und-attention.md) und [ADR-0008](0008-dotnet-kompatibilitaetstests.md); die Zeile in der Geschichte unten.
- Zweite Fundstellen, die diese ADR nicht ändert (Spec ändert der Nutzer-Entscheidungsweg): keine mit alter Lesart gefunden. `SPEC-045`, `SPEC-050` und `docs/messungen/referenzabgleich.md` (B-6) tragen die Lesart von [ADR-0028](0028-referenzinstanz-fuer-tds-8-0.md) bereits; `docs/user/` nennt keine Lesart zu TDS 8.0 an 2022.
- Akzeptiertes Negativ: Die Lesart von [ADR-0028](0028-referenzinstanz-fuer-tds-8-0.md) selbst ist unverändert eine Einzelmessung eines Hosts mit einer Setzung; diese ADR macht sie nicht belastbarer.

## Fitness Function (falls maschinell prüfbar)

Keine; die Aussage ist ein Leseverweis. Das Doku-Gate (`make docs-check`) prüft nur, dass die Links dieser ADR auflösen.

## Re-Evaluierungs-Trigger

Die Trigger von [ADR-0028](0028-referenzinstanz-fuer-tds-8-0.md) (neuer Digest von `2025-latest` oder `2022-latest` mit anderem Verhalten bei `Encrypt=Strict`; belegte Konfiguration, mit der `2022-latest` `Strict` annimmt); eine Messung des Fehler- und ATTENTION-Framings unter TDS 8.0, die einer Aussage von [ADR-0007](0007-fehler-und-attention.md) widerspricht.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-17 | Proposed (Empfehlung Option C, Entscheidung beim Nutzer) | Closure der Welle zum Referenzabgleich und zur Veröffentlichung; Auftrag des Nutzers vom 2026-11-17 |
