# ADR-0019: Kompatibilitätstests: Zuschnitt, Container-Modell, SSPI/NTLM

**Status:** Proposed

**Datum:** 2026-10-31

**Autor:** pt9912

**Bezug:** [ADR-0008](0008-dotnet-kompatibilitaetstests.md), [ADR-0009](0009-authentifizierungsmodus.md), [ADR-0018](0018-laufzeit-artefakt.md), [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)

**Schärft:** [SPEC-025](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) (Integrationstest-Gate), [SPEC-032](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge), [SPEC-021](../../../spec/spezifikation.md#6-externe-verträge) (Clientprofil).

**Supersedes (teilweise):** [ADR-0008](0008-dotnet-kompatibilitaetstests.md) „Entscheidung“, Aufzählungspunkt „B ist die Pflicht-Matrix“, **nur** die Sätze zum Container-Modell („Ein Container: Server des Repos … im selben Container, ohne Netzwerk, ohne Bereitschaftsschleife“ und „Zwei Container … bleiben zulässig, sobald ein Szenario getrennte Prozesse braucht“). Der Pflichtumfang desselben Punktes, die Wahl B zusätzlich D, die Pins, die EULA und alle anderen Entscheidungen von ADR-0008 gelten unverändert. Der Status von ADR-0008 bleibt `Accepted`; die Datei wird nicht angefasst ([AGENTS.md](../../../AGENTS.md) §3.5).

---

## Kontext

Der Slice für die Kompatibilitätstests (Entscheidungsblatt [Punkt 3](../entscheidungsblatt.md)) nennt in §4 offen, wann und wie er startet, in §6 das Container-Modell, und führt unter den Anmeldemessungen der DoD Windows-SSPI (NTLM). Drei Fragen, ein Gegenstand: die Kompatibilitätsmatrix B von [ADR-0008](0008-dotnet-kompatibilitaetstests.md).

**Zuschnitt.** Der Start-Trigger „ein lauffähiger Server beantwortet die Anmeldung“ ist erfüllt, trägt die DoD aber nicht: laut Plan (§4) verdrahtet `Bootstrap.kt` den Handler ohne `QueryBackend` (außer in Tests existiert keine Implementierung in `src/main`), und der Pfad ab dem ersten Byte (TDS 8.0) ist nicht verdrahtet (KDoc von `runtime/ConnectionWiring.kt`). Die DoD verlangt Szenarien SELECT, parametrisierte Abfrage, NULL, Fehler, Abbruch und TDS-8.0-Messungen; ohne Anfragepfad und ohne TDS-8.0-Verdrahtung zeigt die DoD auf Ungebautes. Der Stand ist aus dem Plan zitiert und für diese ADR nicht erneut geprüft.

**Container-Modell.** [ADR-0008](0008-dotnet-kompatibilitaetstests.md) gemessen (Spike mit einem Minimal-Stub, ein Lauf je Zeile): ein Container 2,9 s, zwei Container im eigenen Netzwerk ohne Mount 3,6 s (rund 0,7 s mehr); bei vier gleichzeitigen Läufen 4,8 s gegen 10,5 s; bei `SIGINT`/`SIGTERM` räumen beide Modelle mit `trap` vollständig auf, bei `SIGKILL` bleiben Container (bei zwei Containern auch Netzwerk) stehen. Die Entscheidung von ADR-0008 war „ein Container, zwei zulässig, sobald ein Szenario getrennte Prozesse braucht“. Seitdem ist ein zweites Muster **gebaut**: `make laufzeittest` startet den Server-Container aus der Stage `runtime` ([ADR-0018](0018-laufzeit-artefakt.md): Distroless Java, ohne weitere Laufzeit) und einen zweiten Container als Rohbytes-Client im selben Netzwerk. Der .NET-Referenzclient liegt in einem Image auf dem .NET-SDK; Server und Client in einem Container hieße, JVM und .NET-SDK in einem Image zu vereinen (abgeleitet, nicht gebaut).

**SSPI/NTLM.** [ADR-0009](0009-authentifizierungsmodus.md): der Server lehnt `fIntSecurity` (SSPI/Kerberos) und Entra-ID ab; gemessen ist nur ein MIT-Kerberos-Token, nicht Windows-SSPI (NTLM); „nicht gemessen: Windows-SSPI (NTLM) und die Erkennung von `fIntSecurity` mit einem anderen Token als MIT-Kerberos“.

## Entscheidung

Wir wählen **Option A (Zuschnitt)**, **zwei Container (Container-Modell)** und **„nicht lieferbar“ für SSPI/NTLM**.

1. **Zuschnitt A:** Der Slice für die Kompatibilitätstests wird **zurückgestellt**, bis (a) der Slice für Änderung und Integration des Simulations-Backends in `done/` liegt (das Simulations-Backend ist in `main` verdrahtet) und (b) die Verdrahtung des TDS-8.0-Pfads ab dem ersten Byte in `main` einen Träger-Slice in `done/` hat. Gemessene Aussagen über Szenarien, die einen dieser Pfade brauchen, entstehen erst danach. Der Planner schreibt Start-Trigger und DoD des Slice entsprechend (kein Eingriff in diese ADR).
2. **Container-Modell, Setzung:** Die Pflicht-Matrix B läuft mit **zwei Containern** (Server und Client getrennt, eigenes Docker-Netzwerk ohne Mount, kein veröffentlichter Port), mit Netzwerkname je Lauf (Namen mit PID), Bereitschaftsschleife auf das Server-Log und `trap` im Rezept, wie `make laufzeittest` es bereits baut. Das **ersetzt** die Sätze zum Container-Modell in [ADR-0008](0008-dotnet-kompatibilitaetstests.md). Grund: das gebaute Muster, die getrennten Images von Server (JVM) und Client (.NET) und Szenarien, die getrennte Prozesse brauchen (Verbindungsabbruch, Cancel, Pooling). Die Mehrkosten (rund 0,7 s je Lauf, bei vier gleichzeitigen Läufen 10,5 s gegen 4,8 s) sind am Minimal-Stub gemessen, nicht an der Pflicht-Matrix.
3. **SSPI/NTLM nicht lieferbar (Einschätzung, nicht gemessen):** Windows-SSPI (NTLM) mit einem Windows-Token wird im Slice nicht gemessen und als **nicht lieferbar** benannt, mit dem Grund: die Umgebung des Repos ist ein Linux-Docker-Host ohne Windows-Domäne ([TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) verlangt Reproduktion in Docker); ein Windows-Token zu erzeugen, wäre ein Aufbau, den das Repo nicht trägt. Das ist Einschätzung des Architect/Koordinators und im Repo **nicht** durch einen Versuch belegt. Es bleibt bei der Ablehnung nach [ADR-0009](0009-authentifizierungsmodus.md); dort steht der gemessene Stand (MIT-Kerberos-Token, Ablehnung).
4. Die Pflichtumfänge und die Wahl B zusätzlich D von [ADR-0008](0008-dotnet-kompatibilitaetstests.md) bleiben unverändert.

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| **A — zurückstellen bis Simulations-Backend und TDS-8.0-Verdrahtung in `main` (gewählt)** | die DoD zeigt nur auf Gebautes; Messungen gelten dem Produkt, nicht einem Stub | der Slice startet später; die Aussagen zu Kompatibilität ([TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität)) kommen erst danach |
| B — auf die Anmeldung zuschneiden | startet jetzt; Anmeldung ist gebaut | SELECT, RPC, Fehler, Abbruch und TDS 8.0 fallen aus der DoD heraus und brauchen einen weiteren Slice; zwei Gates für ein Thema |
| C — Voraussetzungen vorziehen (Integration und TDS-8.0-Verdrahtung zuerst) | Reihenfolge ohne Warten | ist mit A dasselbe, nur als Auftrag formuliert; die Reihenfolge der Slices ist Sache des Planners |

| Container-Modell | Pro | Contra |
|---|---|---|
| ein Container (ADR-0008, bisher) | kein Netzwerk, keine Bereitschaftsschleife, kein Aufräumen von Hand; 2,9 s (Stub) | JVM und .NET-SDK in einem Image (abgeleitet); Server und Client nicht als getrennte Prozesse wie später in Betrieb; Szenarien mit getrennten Prozessen brauchen ohnehin zwei |
| **zwei Container (gewählt)** | gebautes Muster (`laufzeittest`); getrennte Images; entspricht dem späteren Betrieb | rund 0,7 s mehr je Lauf (Stub); Netzwerk, Bereitschaftsschleife, `trap`; bei `SIGKILL` bleiben Container und Netzwerk stehen (gemessen) |

| SSPI/NTLM | Pro | Contra |
|---|---|---|
| messen | schließt die Lücke in [ADR-0009](0009-authentifizierungsmodus.md) | Windows-Token ohne Windows-Umgebung nicht erzeugbar (Einschätzung); Aufbau außerhalb von Docker-only ([AGENTS.md](../../../AGENTS.md) §3.1) |
| **als nicht lieferbar benennen (gewählt)** | ehrliche DoD; der Server lehnt den Modus ohnehin ab | die Lücke bleibt benannt statt geschlossen |

## Konsequenzen

- Positiv: Die DoD des Slice zeigt nicht mehr auf Ungebautes; ein Container-Modell ist festgelegt, das schon als Gate läuft.
- Negativ: Die Kompatibilitätsaussage für Anfragen und TDS 8.0 kommt später; die Matrix B kostet pro Lauf etwas mehr als im Spike.
- **Folgepflicht (nach `Accepted`):** (1) Der Planner passt Start-Trigger und DoD des Slice für die Kompatibilitätstests an (Zuschnitt A, zwei Container, SSPI/NTLM als nicht lieferbar mit Grund) und verweist auf diese ADR; § 6 des Slice („Container-Modell offen“) bekommt einen Ausgang. (2) Spezifikation: `SPEC-025`/`SPEC-032` nennen das Container-Modell zwei Container; Träger: der Spec-Nachzug nach `Accepted`. (3) Der Träger-Slice für die TDS-8.0-Verdrahtung ist [Punkt 4](../entscheidungsblatt.md) des Entscheidungsblatts und nicht Gegenstand dieser ADR. (4) Kein Quelltext ändert sich durch diese ADR.
- **Akzeptiertes Negativ (einmalig):** SSPI/NTLM bleibt ungemessen; der Server lehnt den Modus ab, ein Client mit Windows-Token sieht die Ablehnung nur, wenn jemand es dort ausprobiert. Es bleibt bei `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` bzw. dem Register-Eintrag zu ADR-0009 §Offen.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| .NET-Referenztests im Container | Die Matrix B läuft mit zwei Containern ohne Mount; ein roter Test macht `make gates` rot; Aufräumen bei `SIGINT`/`SIGTERM`. Noch kein bestandenes Gate für die Pflicht-Matrix. | — (Gate noch einzurichten; Träger: der Slice für die Kompatibilitätstests; Gerüst: `make referenztest-dotnet`) |

## Offen

- Für den Nutzer: Zuschnitt A statt B oder C; zwei Container; SSPI/NTLM als nicht lieferbar.
- Nicht entschieden: wie der Träger der TDS-8.0-Verdrahtung zugeschnitten wird (Punkt 4 des Entscheidungsblatts); die Laufzeit der Pflicht-Matrix mit zwei Containern (nicht gemessen); Pooling mit `ResetConnection`, mehrpaketiges LOGIN7 und TDS 8.0 mit SQL-Authentifizierung als Messungen der DoD (bleiben beim Slice).

## Re-Evaluierungs-Trigger

Ein Szenario, das ohne getrennte Prozesse auskommt und dessen Laufzeit zwei Container spürbar teurer macht; eine Windows-Umgebung im CI, die ein SSPI-Token erzeugen kann; eine Betreiberanforderung an NTLM; die Verdrahtung des Simulations-Backends und des TDS-8.0-Pfads in `main` (dann startet der Slice).

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-10-31 | Proposed – Zuschnitt A, zwei Container, SSPI/NTLM nicht lieferbar zu Punkt 3 des Entscheidungsblatts (Nutzer: „ok deine empfehlungen“); Teil-Supersede der Container-Sätze von ADR-0008; Einschätzung zu SSPI/NTLM nicht gemessen; Status bleibt Proposed bis Review und Annahme durch den Nutzer | [Entscheidungsblatt](../entscheidungsblatt.md) |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
