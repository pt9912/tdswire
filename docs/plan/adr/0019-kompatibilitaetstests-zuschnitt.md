# ADR-0019: Kompatibilitätstests: Zuschnitt, Container-Modell, SSPI/NTLM

**Status:** Accepted (vom Nutzer entschieden)

**Datum:** 2026-10-31

**Autor:** pt9912

**Bezug:** [ADR-0008](0008-dotnet-kompatibilitaetstests.md), [ADR-0009](0009-authentifizierungsmodus.md), [ADR-0018](0018-laufzeit-artefakt.md), [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit)

**Schärft:** [SPEC-025](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge) (Integrationstest-Gate), [SPEC-032](../../../spec/spezifikation.md#7-festlegungen-der-harness-werkzeuge), [SPEC-021](../../../spec/spezifikation.md#6-externe-verträge) (Clientprofil).

**Supersedes (teilweise):** [ADR-0008](0008-dotnet-kompatibilitaetstests.md) „Entscheidung“, **nur** (1) im Aufzählungspunkt „B ist die Pflicht-Matrix“ die Sätze zum Container-Modell („Ein Container: Server des Repos … im selben Container, ohne Netzwerk, ohne Bereitschaftsschleife“ und „Zwei Container … bleiben zulässig, sobald ein Szenario getrennte Prozesse braucht“) und (2) im Aufzählungspunkt „D ist der Vergleichslauf, nicht der Ersatz“ den Satz „Die Pflicht-Matrix B ist Teil von `make gates`.“ (wörtlich); an seine Stelle tritt [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md): die Matrix B ist ein E2E-Lauf und nicht Teil von `make gates`. Dasselbe gilt für den Halbsatz „B läuft mit `make gates`“ im Abschnitt „Offen“ von ADR-0008. [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md) ersetzt diesen Satz selbst nicht ausdrücklich; diese ADR führt den Teil-Supersede nach. Der Pflichtumfang des Punktes „B ist die Pflicht-Matrix“, die Wahl B zusätzlich D, die Pins, die EULA und alle anderen Entscheidungen von ADR-0008 gelten unverändert. Der Status von ADR-0008 bleibt `Accepted`; die Datei wird nicht angefasst ([AGENTS.md](../../../AGENTS.md) §3.5).

---

## Kontext

Der Slice für die Kompatibilitätstests (Entscheidung des Nutzers 2026-10-31, Empfehlung übernommen) nennt in §4 offen, wann und wie er startet, in §6 das Container-Modell, und führt unter den Anmeldemessungen der DoD Windows-SSPI (NTLM). Drei Fragen, ein Gegenstand: die Kompatibilitätsmatrix B von [ADR-0008](0008-dotnet-kompatibilitaetstests.md).

**Zuschnitt.** Der Start-Trigger „ein lauffähiger Server beantwortet die Anmeldung“ ist erfüllt. Die DoD verlangt Szenarien SELECT, parametrisierte Abfrage, NULL, Fehler, Abbruch und TDS-8.0-Messungen. Stand im Quelltext (bei Abfassung gelesen): `runtime/Bootstrap.kt` verdrahtet das Simulations-Backend (`SimulationResult.Ready` führt zu `bind` mit dem Backend; Quelle sind die Beschreibungsdatei der Konfiguration oder die Tabellen des Integrators, ohne beides läuft der Prozess ohne Anfragepfad), und die KDoc von `runtime/ConnectionWiring.kt` beschreibt den Pfad ab dem ersten Byte (TDS 8.0, Erkennung am Byte `0x16`) als verdrahtet, belegt gegen eine JDK-Gegenstelle und Rohbytes (`Tds8WiringTest`), nicht gegen den Referenzclient. Die Träger-Slices beider Verdrahtungen liegen in `done/`. Die DoD zeigt damit auf Gebautes; offen bleibt, ob der Slice auf den vollen Umfang oder auf die Anmeldung zugeschnitten wird.

**Container-Modell.** [ADR-0008](0008-dotnet-kompatibilitaetstests.md) gemessen (Spike mit einem Minimal-Stub, ein Lauf je Zeile): ein Container 2,9 s, zwei Container im eigenen Netzwerk ohne Mount 3,6 s (rund 0,7 s mehr); bei vier gleichzeitigen Läufen 4,8 s gegen 10,5 s; bei `SIGINT`/`SIGTERM` räumen beide Modelle mit `trap` vollständig auf, bei `SIGKILL` bleiben Container (bei zwei Containern auch Netzwerk) stehen. Die Entscheidung von ADR-0008 war „ein Container, zwei zulässig, sobald ein Szenario getrennte Prozesse braucht“. Seitdem ist ein zweites Muster **gebaut**: `make laufzeittest` startet den Server-Container aus der Stage `runtime` ([ADR-0018](0018-laufzeit-artefakt.md): Distroless Java, ohne weitere Laufzeit) und einen zweiten Container als Rohbytes-Client im selben Netzwerk. Der .NET-Referenzclient liegt in einem Image auf dem .NET-SDK; Server und Client in einem Container hieße, JVM und .NET-SDK in einem Image zu vereinen (abgeleitet, nicht gebaut).

**SSPI/NTLM.** [ADR-0009](0009-authentifizierungsmodus.md): der Server lehnt `fIntSecurity` (SSPI/Kerberos) und Entra-ID ab; gemessen ist nur ein MIT-Kerberos-Token, nicht Windows-SSPI (NTLM); „nicht gemessen: Windows-SSPI (NTLM) und die Erkennung von `fIntSecurity` mit einem anderen Token als MIT-Kerberos“.

## Entscheidung

Mit der Annahme am 2026-10-31 bestätigt der Nutzer **Zuschnitt A** (voller Umfang der DoD, Start nach `Accepted`), **zwei Container** und **SSPI/NTLM nicht lieferbar** (Einschätzung, nicht gemessen) sowie den Teil-Supersede von [ADR-0008](0008-dotnet-kompatibilitaetstests.md) für die Container-Sätze und den Satz „Die Pflicht-Matrix B ist Teil von `make gates`.“ (Nutzerentscheidung 2026-10-31: „ADRs 0016 bis 0019 nach Korrektur annehmen“). Die Punkte unter „Offen“ bleiben offen.

Wir wählen **Option A (Zuschnitt: voller Umfang, Start nach `Accepted`)**, **zwei Container (Container-Modell)** und **„nicht lieferbar“ für SSPI/NTLM**.

1. **Zuschnitt A:** Der Slice für die Kompatibilitätstests behält den **vollen Umfang** der DoD (Anmeldung, SELECT, parametrisierte Abfrage, NULL, Fehler, Abbruch, TDS 8.0) und kann nach `Accepted` dieser ADR starten; eine Voraussetzung, auf die er warten müsste, besteht im Quelltext nicht (Ausgangslage oben). Die Szenarien laufen gegen das Simulations-Backend mit einer Beschreibung, die der Slice mitbringt. Der Planner schreibt Start-Trigger und DoD des Slice entsprechend (kein Eingriff in diese ADR).
2. **Container-Modell, Setzung:** Die Pflicht-Matrix B läuft mit **zwei Containern** (Server und Client getrennt, eigenes Docker-Netzwerk ohne Mount, kein veröffentlichter Port), mit Netzwerkname je Lauf (Namen mit PID), Bereitschaftsschleife auf das Server-Log und `trap` im Rezept, wie `make laufzeittest` es bereits baut. Das **ersetzt** die Sätze zum Container-Modell in [ADR-0008](0008-dotnet-kompatibilitaetstests.md). Grund: das gebaute Muster, die getrennten Images von Server (JVM) und Client (.NET) und Szenarien, die getrennte Prozesse brauchen (Verbindungsabbruch, Cancel, Pooling). Die Mehrkosten (rund 0,7 s je Lauf, bei vier gleichzeitigen Läufen 10,5 s gegen 4,8 s) sind am Minimal-Stub gemessen, nicht an der Pflicht-Matrix.
3. **SSPI/NTLM nicht lieferbar (Einschätzung, nicht gemessen):** Windows-SSPI (NTLM) mit einem Windows-Token wird im Slice nicht gemessen und als **nicht lieferbar** benannt, mit dem Grund: die Umgebung des Repos ist ein Linux-Docker-Host ohne Windows-Domäne ([TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) verlangt Reproduktion in Docker); ein Windows-Token zu erzeugen, wäre ein Aufbau, den das Repo nicht trägt (Einschätzung). Das ist Einschätzung des Architect/Koordinators und im Repo **nicht** durch einen Versuch belegt. Es bleibt bei der Ablehnung nach [ADR-0009](0009-authentifizierungsmodus.md); dort steht der gemessene Stand (MIT-Kerberos-Token, Ablehnung).
4. Die Pflichtumfänge und die Wahl B zusätzlich D von [ADR-0008](0008-dotnet-kompatibilitaetstests.md) bleiben unverändert.

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| **A — voller Umfang, Start nach `Accepted` (gewählt)** | die DoD zeigt nur auf Gebautes; die Kompatibilitätsaussage ([TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität)) deckt Anfragen und TDS 8.0 in einem Slice ab | großer Slice; die Messungen laufen gegen das Simulations-Backend mit der Beschreibung des Slice, nicht gegen Beschreibungen anderer Nutzer |
| B — auf die Anmeldung zuschneiden | kleiner Slice, erste Kompatibilitätsaussage früher | SELECT, RPC, Fehler, Abbruch und TDS 8.0 fallen aus der DoD heraus und brauchen einen weiteren Slice; zwei Gates für ein Thema |
| C — zurückstellen bis zu einer Voraussetzung | wäre sinnvoll, wenn Simulations-Backend oder TDS-8.0-Verdrahtung fehlten | beide Voraussetzungen sind im Quelltext erfüllt: C hätte nichts, worauf es wartet |

| Container-Modell | Pro | Contra |
|---|---|---|
| ein Container (ADR-0008, bisher) | kein Netzwerk, keine Bereitschaftsschleife, kein Aufräumen von Hand; 2,9 s (Stub) | JVM und .NET-SDK in einem Image (abgeleitet); Server und Client nicht als getrennte Prozesse wie später in Betrieb; Szenarien mit getrennten Prozessen brauchen ohnehin zwei |
| **zwei Container (gewählt)** | gebautes Muster (`laufzeittest`); getrennte Images; entspricht dem späteren Betrieb | rund 0,7 s mehr je Lauf (Stub); Netzwerk, Bereitschaftsschleife, `trap`; bei `SIGKILL` bleiben Container und Netzwerk stehen (gemessen) |

| SSPI/NTLM | Pro | Contra |
|---|---|---|
| messen | schließt die Lücke in [ADR-0009](0009-authentifizierungsmodus.md) | Windows-Token ohne Windows-Umgebung nicht erzeugbar (Einschätzung); ein Windows-Aufbau neben dem Linux-Docker-Host, den [TDS-QA-06](../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit) als Umgebung der Reproduktion nennt (Einschätzung; [AGENTS.md](../../../AGENTS.md) §3.1 verbietet nur JDK, Gradle und Kotlin auf dem Host und trägt das nicht) |
| **als nicht lieferbar benennen (gewählt)** | ehrliche DoD; der Server lehnt den Modus ohnehin ab | die Lücke bleibt benannt statt geschlossen |

## Konsequenzen

- Positiv: Der Slice kann mit voller DoD starten, ohne auf Verdrahtung zu warten; der Widerspruch zu ADR-0008 über das Gate ist ausdrücklich aufgelöst; ein Container-Modell ist festgelegt, das schon im E2E-Durchgang läuft.
- Negativ: Der Slice ist groß; die Matrix B kostet pro Lauf etwas mehr als im Spike.
- **Folgepflicht (nach `Accepted`):** (1) Der Planner passt Start-Trigger und DoD des Slice für die Kompatibilitätstests an (Zuschnitt A, zwei Container, SSPI/NTLM als nicht lieferbar mit Grund) und verweist auf diese ADR; § 6 des Slice („Container-Modell offen“) bekommt einen Ausgang. (2) Spezifikation: `SPEC-025`/`SPEC-032` nennen das Container-Modell zwei Container; Träger: der Spec-Nachzug nach `Accepted`. (3) Spezifikation: der Satz, die Matrix B sei Teil von `make gates`, falls er in der Spezifikation steht, folgt [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md). (4) Kein Quelltext ändert sich durch diese ADR.
- **Akzeptiertes Negativ (einmalig):** SSPI/NTLM bleibt ungemessen; der Server lehnt den Modus ab, ein Client mit Windows-Token sieht die Ablehnung nur, wenn jemand es dort ausprobiert. Es bleibt bei `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut` bzw. dem Register-Eintrag zu ADR-0009 §Offen.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| .NET-Referenztests im Container | Die Matrix B läuft mit zwei Containern ohne Mount; sie prüft den Serverprozess über die Prozessgrenze und ist ein E2E-Lauf (Stufe von `make e2e`, nicht in `make gates`, [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md)); ein roter Test macht `make e2e` rot; Aufräumen bei `SIGINT`/`SIGTERM`. Noch kein bestandener Lauf der Pflicht-Matrix. | — (Lauf noch einzurichten; Träger: der Slice für die Kompatibilitätstests; Gerüst: `make referenztest-dotnet`) |

## Offen

- Für den Nutzer: Zuschnitt A (voller Umfang) statt B; zwei Container; SSPI/NTLM als nicht lieferbar.
- Nicht entschieden: die Laufzeit der Pflicht-Matrix mit zwei Containern (nicht gemessen); Pooling mit `ResetConnection`, mehrpaketiges LOGIN7 und TDS 8.0 mit SQL-Authentifizierung als Messungen der DoD (bleiben beim Slice).

## Re-Evaluierungs-Trigger

Ein Szenario, das ohne getrennte Prozesse auskommt und dessen Laufzeit zwei Container spürbar teurer macht; eine Windows-Umgebung im CI, die ein SSPI-Token erzeugen kann; eine Betreiberanforderung an NTLM; der Slice stellt fest, dass ein Teil der DoD am Simulations-Backend oder am TDS-8.0-Pfad scheitert (dann Zuschnitt B für den Rest).

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-10-31 | Proposed – Zuschnitt A, zwei Container, SSPI/NTLM nicht lieferbar (Entscheidung des Nutzers 2026-10-31, Empfehlung übernommen); Teil-Supersede der Container-Sätze und des Satzes „Matrix B ist Teil von `make gates`“ von ADR-0008; Ausgangslage des Zuschnitts nach Prüfung des Quelltexts auf voller Umfang, Start nach `Accepted` umgestellt; Einschätzung zu SSPI/NTLM nicht gemessen; Status bleibt Proposed bis Review und Annahme durch den Nutzer | [ADR-0008](0008-dotnet-kompatibilitaetstests.md), [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md) |
| 2026-10-31 | Accepted – angenommen nach Review und Korrekturrunde (Review docs/reviews/2026-10-31-33ffaab-adr-0014-bis-0019.md) | [Review](../../reviews/2026-10-31-33ffaab-adr-0014-bis-0019.md) |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
