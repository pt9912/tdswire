# ADR-0055: Zwei Auswahlen ohne Parameter in einem Text sind eine erkannte Form des Simulations-Backends (ersetzt teilweise ADR-0033 Nr. 11 und ADR-0012 Entscheidung 14)

**Status:** Proposed

**Datum:** 2026-11-19

**Autor:** pt9912

**Bezug:** [TDS-FA-10](../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-FA-11](../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0008](0008-dotnet-kompatibilitaetstests.md), [ADR-0012](0012-simulations-backend-beschreibung-und-formen.md), [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [Messprotokoll des Referenzabgleichs](../../messungen/referenzabgleich.md), [Review-Report vom 2026-11-19](../../reviews/2026-11-19-slice-simulation-mehrere-anweisungen-und-ausgabewert.md) (Befund F-1)

**Schärft:** [SPEC-036](../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) (Tabelle der Formen: Zeile S2; Liste „Nicht erkannt“: mehrere Anweisungen in einem Text), [TDS-FA-11.a](../../../spec/spezifikation.md#tds-fa-11a--simulations-backend) (Schritt 1, Ausnahmen der Regel „mehrere Anweisungen gelten als nicht erkannt“). Diese ADR ändert `spec/` nicht; der Nachzug steht unter Konsequenzen.

**Supersedes (teilweise), nur wörtlich benannt, wirksam erst mit dem Status `Accepted`; die Dateien von [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) und [ADR-0012](0012-simulations-backend-beschreibung-und-formen.md) bleiben unverändert und `Accepted` ([AGENTS.md](../../../AGENTS.md) §3.5):**

- [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 11, **nur** der Halbsatz „und nur um Formen, die das Lastenheft nennt: Katalogabfragen, Strukturänderungen, Massenladen und die Abfragen, die der Prüfclient für Verbindung und Metadaten stellt“, **soweit** er die Liste der Formen auf diese Gruppen begrenzt; es gilt zusätzlich die Form S2 dieser ADR. Es bleiben: „abschließend“ (alles Fremde endet mit `TDS_UNSUPPORTED_SQL`), die Deckung von `MERGE` und alles Übrige von Nr. 11.
- [ADR-0012](0012-simulations-backend-beschreibung-und-formen.md) Entscheidung 14, **nur** der erste Satz „Gelesene Anweisungsformen bleiben die Tabelle S1, W1, W2, W3 und C aus SPEC-036“, soweit er die erkannten Formen begrenzt (von [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) schon teilweise ersetzt); es gilt zusätzlich die Form S2. Es bleiben die Scanner-Einzelheiten und alles Übrige von Entscheidung 14.
- [ADR-0012](0012-simulations-backend-beschreibung-und-formen.md), Abschnitt „Deckung der Liste ‚Nicht erkannt‘ durch das Lastenheft“, Tabellenzeile „… mehrere Anweisungen in einem Text“: **nur** für den Text aus zwei Auswahlen ohne Parameter (Form S2). Für jeden anderen Text aus mehreren Anweisungen gilt die Zeile weiter.

**Regeln:** Baseline-Regelwerk `modul-04-adrs.md` §Ziel-Form: ADR (MADR).

---

## Kontext

[ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 11 (vom Nutzer am 2026-11-06 bestätigt) lässt die Liste der erkannten Formen nur um Formen wachsen, die das Lastenheft nennt. [TDS-FA-11](../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) nennt keinen Text aus zwei Anweisungen; [TDS-FA-10](../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte) nennt „mehrere Ergebnisse“ und im Boundary-Kriterium die Grenzen zwischen zwei Ergebnissen bei `NextResult()`, aber nicht, dass ein Text aus zwei Anweisungen sie erzeugt. Die Liste „Nicht erkannt“ in [SPEC-036](../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) führt „mehrere Anweisungen in einem Text“; [ADR-0012](0012-simulations-backend-beschreibung-und-formen.md) deckt diese Zeile nur über die Lesart „die positive Liste ist abschließend“ (Tabelle der Deckung).

Der Nutzer hat am 2026-11-18 entschieden: **Code angleichen, begrenzt auf Formen, die das Backend ohnehin erkennt** (Referenzabgleich, Ausnahme A-2: der Referenzclient bekommt vom echten Server zwei Ergebnismengen, vom Server dieses Repos `TDS_UNSUPPORTED_SQL`). Zwei Auswahlen der Form S1 in einem Text sind eine **neue Kombination bekannter Formen**, keine bisher erkannte Form; ob die Entscheidung sie deckt, sagt weder das Lastenheft noch [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md). Der Review (Befund F-1) hat das festgestellt: Bau und Spezifikationszeile S2 liegen vor einer Entscheidung, die Nr. 11 dafür verlangt. Eine `Accepted`-ADR wird nicht nachgebessert ([AGENTS.md](../../../AGENTS.md) §3.5); die Korrektur ist diese Folge-ADR.

**Was gemessen ist** (Einzelmessungen: je Instanz eine Aufnahme, ein Host, `Microsoft.Data.SqlClient` 7.1.1, Instanzen 2022 und 2025; [Messprotokoll](../../messungen/referenzabgleich.md)): Mitschnitt `mehrteilig`, der Text `SELECT Id FROM person WHERE Id = 1; SELECT Id FROM person WHERE Id = 2` als `SQL_BATCH` ohne Parameter. Die Referenz liefert zwei Ergebnismengen mit je einer Zeile; die Zeile S2 in [SPEC-036](../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig) gibt die Abschlüsse an (erstes `DONE` mit `MORE` und `COUNT`, zweites `DONE` mit `COUNT`).

**Was nicht gemessen ist und hier nicht behauptet wird:** eine dritte Auswahl; Schreibformen oder gemischte Folgen; zwei Auswahlen mit Parametern (`sp_executesql`); zwei Auswahlen aus verschiedenen Tabellen oder mit anderen Bedingungen als im Messtext; das Verhalten des echten Servers bei einem Fehler der zweiten Auswahl. Die Antwort des Servers dieses Repos auf zwei Auswahlen ist im Live-Schritt `mehrteilig` nicht beobachtet; gemessen ist dort die Referenz.

## Entscheidung

1. **Deckung (Lesart des Lastenhefts, vom Nutzer zu bestätigen).** Für das Simulations-Backend gilt [TDS-FA-10](../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte) („mehrere Ergebnisse“, Grenze zwischen zwei Ergebnissen) als Nennung, soweit ein Text aus **zwei bereits erkannten Auswahlen** zwei Ergebnismengen ergibt. Das ist dasselbe Muster, mit dem [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 11 `MERGE` über ein Akzeptanzkriterium deckt: eine Lesart des Nutzers, keine wörtliche Nennung. Das Lastenheft ändert diese ADR nicht.
2. **Form S2.** Erkannt sind **genau zwei Auswahlen der Form S1, ohne Parameter, in einem Text** ([SPEC-036](../../../spec/spezifikation.md#spec-036--erkannte-anweisungsformen-des-simulations-backends-vorläufig), Zeile S2). Die Antwort sind zwei Ergebnismengen in der Reihenfolge des Textes.
3. **Die Grenze ist die Messgrenze.** „Zwei, ohne Parameter“ ist gemessen nur am Mitschnitt `mehrteilig`, je 2022 und 2025, je ein Fall. Die Zulassung jeder Auswahl der Form S1 (andere Tabelle, Spalten oder Bedingung als im Messtext) ist eine **Setzung**, weil die Antwortform je Auswahl dieselbe ist wie bei einer einzelnen Form S1; am echten Server ist sie dafür nicht gemessen. Das Verhalten bei unbekannter Tabelle oder Spalte in einer der beiden Auswahlen (der ganze Text wird abgewiesen, bevor etwas gelesen wird) ist ebenfalls eine **Festlegung, nicht gemessen**.
4. **Alles andere bleibt nicht erkannt** und endet mit `TDS_UNSUPPORTED_SQL`: eine dritte Auswahl, eine Folge mit einer anderen Anweisung als S1, ein leeres Stück zwischen zwei `;`, zwei Auswahlen mit Parametern. Die Obergrenze zwei ist die Grenze der Messung, keine Eigenschaft, die der Server begründet; sie wächst nur durch eine Folge-ADR mit Messung (Alternative D).
5. **„Abschließend“ gilt weiter.** Die Entscheidung öffnet keine Tür für beliebige Texte aus mehreren Anweisungen. Form C und der Text der Form D7 bleiben, wie sie sind.

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| A — nichts tun | kein neues Dokument | Zeile S2 und Bau stünden ohne ADR-Träger gegen Nr. 11; A-2 bliebe Abweichung vom Zielbild |
| B — Zielbild anpassen: A-2 als „so gewollt“ | kein Bau, keine neue Form | widerspricht der Entscheidung des Nutzers vom 2026-11-18 (Code angleichen); Lücke zum Referenzclient bleibt |
| **C — genau zwei Auswahlen S1 ohne Parameter, Lesart von [TDS-FA-10](../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte)** | deckt den gemessenen Fall; Grenze trägt die Messung; kleinster Schritt; Nr. 11 bleibt im Kern | Lesart des Nutzers statt Wortlaut; Setzung für andere S1-Paare |
| D — beliebig viele Auswahlen S1 | kein Sonderfall „zwei“ | nur zwei gemessen; die Lesart wird weiter überdehnt (§3.8); neue Zusage ohne Beleg |
| E — Folgen mit Schreibformen oder gemischt | näher am echten Server | nicht gemessen; braucht Ausdrücke und Zählerlogik; anderes Lieferziel |
| F — Lastenheft nennt die Form wörtlich, Nr. 11 unverändert | sauberste Herleitung | Lastenheft ist vertraglich; Änderung nur durch den Nutzer; dauert, Bau stünde bis dahin still |

Empfehlung: **Option C**. Die Wahl liegt beim Nutzer; F schließt C nicht aus (eine Klarstellung im Lastenheft kann nachfolgen und macht die Lesart zur Nennung).

## Konsequenzen

- Positiv: die Ausnahme A-2 lässt sich durch Bau schließen, ohne eine Lastenheft-Änderung zu erzwingen; die Grenze ist an der Messung festgemacht; Nr. 11 bleibt für alles andere in Kraft.
- Negativ: die Deckung hängt an einer Lesart des Nutzers; die Zulassung beliebiger Paare S1 ist eine Setzung (Entscheidung 3).
- Folgepflicht nach Annahme (Planner oder Implementer, Doku): Vermerk „teilweise ersetzt durch ADR-0055“ in den Indexzeilen von [ADR-0012](0012-simulations-backend-beschreibung-und-formen.md) und [ADR-0033](0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md); Geschichte unten.
- Zweite Fundstellen, die diese ADR nicht ändert (Änderung von `spec/` und `docs/user/` geht über den Nutzer-Weg): [TDS-FA-11.a](../../../spec/spezifikation.md#tds-fa-11a--simulations-backend) Schritt 1 nennt als Ausnahme der Regel „mehrere Anweisungen in einem Text“ nur Form C und widerspricht Form S2, die Schritte 5 und 7 beschreiben ein Resultset je Auswahl; die Aufzählung „Was das Backend erkennt“ in `docs/user/einbettung-und-betrieb.md` nennt zwei Auswahlen nicht und sagt, alles andere ende mit `TDS_UNSUPPORTED_SQL`. Beides ist nach Annahme nachzuziehen (Review F-5).
- Akzeptiertes Negativ: Die Antwort des Servers dieses Repos auf zwei Auswahlen ist am echten Referenzclient nur über den Mitschnitt-Lauf belegt, nicht über den Live-Lauf; ein Frischbeleg gehört zur Verifikation, nicht zu dieser Entscheidung.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| Tests am Simulations-Backend | S2 wird erkannt; dritte Auswahl, Folge mit anderer Anweisung, leeres Stück, zwei Auswahlen mit Parametern enden mit `TDS_UNSUPPORTED_SQL` | `make test` |
| Mitschnitt-Lauf (kein Gate) | die Regeln `mehrteilig/01/*` entfallen, der Lauf ist gegen die verkleinerte Liste grün | `make referenztest-dotnet` |

## Re-Evaluierungs-Trigger

Der Nutzer verwirft die Lesart von [TDS-FA-10](../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte) oder ergänzt das Lastenheft um die Form (Alternative F); ein Messlauf mit mehr als zwei Auswahlen, mit Parametern oder mit Schreibformen liegt vor; der Live-Lauf der Referenz zeigt eine andere Antwortform oder einen anderen Abschluss als der Mitschnitt; ein Embedder oder Clientprofil verlangt längere Folgen.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-19 | Proposed (Empfehlung Option C, Entscheidung beim Nutzer) | [Review-Report vom 2026-11-19](../../reviews/2026-11-19-slice-simulation-mehrere-anweisungen-und-ausgabewert.md), Befund F-1 |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
