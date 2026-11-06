# ADR-0037: `sp_unprepare` im SQL-Batch mit unbekanntem Handle ergibt Fehler 8179 (ersetzt teilweise ADR-0036)

**Status:** Accepted (vom Nutzer entschieden, 2026-11-06: „ADR-0037 ja“; Begleitpunkte: „deine empfehlungen“)

**Datum:** 2026-11-06 (Entwurf und Annahme)

**Autor:** pt9912

**Bezug:** [TDS-FA-12](../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-FA-04](../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [ADR-0036](0036-handle-freigabe-und-grenze-je-sitzung.md), [ADR-0008](0008-dotnet-kompatibilitaetstests.md), [ADR-0014](0014-fehlerfolge-rpc-antwort.md), [ADR-0017](0017-prepared-handles-grenzwert.md)

**Schärft:** [SPEC-030](../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten) (Antwort der Batch-Freigabe auf ein unbekanntes Handle) und [§4 der Spezifikation](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder) (ein Code mit der Nummer 8179 und State 8; der Satz „State ist für jeden Code 1“ braucht dafür eine Ausnahme). `SPEC-057` (`sp_execute` mit unbekanntem Handle, Nummer 50009), `SPEC-013` und Nicht-Ziel 6 bleiben unverändert.

**Supersedes (teilweise), nur wörtlich benannt; die Datei des Vorgängers bleibt unverändert und `Accepted` ([AGENTS.md](../../../AGENTS.md) §3.5):** [ADR-0036](0036-handle-freigabe-und-grenze-je-sitzung.md) Entscheidung 2, **nur** der Halbsatz „ein unbekanntes Handle ist kein Fehler (idempotent), weil ein Batch mehrere nennt und `RESETCONNECTION` die Tabelle leert.“ Es bleiben: der Satz „Die Antwort ist ein Erfolg ohne Zeilen“ für Handles, die die Sitzung hält, die Form des Batches, die Erkennung nur bei ganzem Text dieser Form, der Satz „Eine freigegebene Nummer wird nicht wieder vergeben“, der Absatz zum RPC `sp_unprepare` (bleibt abgelehnt) und die Entscheidungen 1, 3, 4 und 5. Die Fitness-Function-Zeile von ADR-0036 „unbekanntes Handle im Batch ohne Fehler“ und der Eintrag „unbekanntes Handle bei `sp_unprepare` ist Erfolg (Setzung)“ in ihrer Geschichte gelten damit nicht mehr; [ADR-0036](0036-handle-freigabe-und-grenze-je-sitzung.md) selbst wird nicht geändert.

**Regeln:** Baseline-Regelwerk `modul-04-adrs.md` §Ziel-Form: ADR (MADR).

---

## Kontext

[ADR-0036](0036-handle-freigabe-und-grenze-je-sitzung.md) setzt für `EXEC sp_unprepare <n>` mit einem Handle, das die Sitzung nicht hält, einen Erfolg ohne Zeilen und nennt das ausdrücklich „Setzung, nicht gemessen“ (Offen 3, Re-Evaluierungs-Trigger: „verhält sich bei `sp_unprepare` anders“). Der Nutzergrundsatz lautet: die Spezifikation ist das Zielbild, der echte SQL Server die Referenz.

Die Gegenmessung (Messprotokoll `docs/messungen/referenzabgleich.md`, Zeile M-4, Abschnitt „Gegenmessungen zu den Befunden der Spikes“; Läufe `make vergleichslauf-d LAUF=messung-2022` und `LAUF=messung-2025`, Developer-Image 2022 und 2025 per Digest gepinnt, `Microsoft.Data.SqlClient` 7.1.1, TDS 7.4, Einzelmessung 2026-11-06, ein Host) zeigt:

| Aussage | Beleg (M-4) |
|---|---|
| `sp_unprepare` mit dem vergebenen Handle gelingt, Rückgabestatus 0 | M-4, erster Teil |
| dasselbe Handle ein zweites Mal: `Number 8179, Class 16, State 8`, Text „Could not find prepared statement with handle 1.“ | M-4, zweiter Teil |
| unbekanntes Handle 999999, als Prozedur und als SQL-Batch `EXEC sp_unprepare 999999`: dieselbe Nummer 8179 mit dem Handle im Text | M-4, dritter Teil |
| die Verbindung bleibt nach dem Fehler offen | M-4 |

Die Review-Findung F-4 (Review vom 2026-11-06 zur Lieferung 7cb411b2 des Live-Laufs, noch nicht auf `main`) hält den Widerspruch zu ADR-0036 fest und verlangt eine Folge-ADR.

**Nicht gemessen** (und deshalb unten Setzung oder offen): was der echte Server bei einem Batch mit mehreren Handles tut, von denen eines unbekannt ist (bricht 8179 den Batch ab? kommen die übrigen Freigaben an?); die weiteren Felder des ERROR-Tokens (Servername, Prozedurname, Zeilennummer; am Client sichtbar nur Nummer, Class, State, Text); der Wortlaut für 999999 einzeln (belegt ist „Handle im Text“; der Wortlaut für Handle 1 ist wörtlich belegt); das Verhalten nach `RESETCONNECTION`; die Antwort auf `sp_execute` mit unbekanntem Handle; die Tokenfolge auf dem Draht (kein Mitschnitt). Die Messung ist am Client gemacht, nicht auf dem Draht.

## Entscheidung

Wir wählen **Option B: Fehler 8179**, wie der echte Server.

1. **Antwort (Messung M-4).** Ein Handle der Batch-Form `EXEC sp_unprepare <n>`, das die Sitzung nicht hält, ergibt einen ERROR mit Nummer **8179**, Class **16**, State **8** und dem Text `Could not find prepared statement with handle <n>.` mit der genannten Nummer, gefolgt von einem DONE mit Fehlerstatus; die Verbindung bleibt offen, das Backend wird nicht gerufen. Nummer, Class, State, Verbindung offen und der Wortlaut für Handle 1 sind gemessen; der Wortlaut für andere Nummern ist als gleich gesetzt (M-4 nennt für 999999 nur „Handle im Text“); die weiteren Felder des ERROR-Tokens legt die Spezifikation fest (Setzung, nicht gemessen).
2. **Was „unbekannt“ heißt.** Nie vergeben, bereits freigegeben (doppelt freigegeben: gemessen, M-4) und jedes Handle aus der Zeit vor `RESETCONNECTION` (Setzung: die Tabelle wird geleert, [ADR-0036](0036-handle-freigabe-und-grenze-je-sitzung.md), nicht am echten Server gemessen). Ein unbekanntes Handle ändert die Zählung nicht.
3. **Batch mit mehreren Handles (Setzung, nicht gemessen).** Die Anweisungen werden der Reihe nach bearbeitet, jedes gehaltene Handle wird freigegeben, jedes unbekannte ergibt einen ERROR 8179; das DONE am Ende trägt den Fehlerstatus. Grund der Setzung: dann bleibt der Fehler eines Handles ohne Folge für die Freigabe der übrigen, und die Tabelle der Sitzung wächst nicht durch einen Fehler. Ob der echte Server den Batch bei 8179 abbricht, ist offen (Offen 1).
4. **Unberührt.** `sp_execute` mit unbekanntem Handle bleibt `SPEC-057` (50009), weil M-4 nur `sp_unprepare` misst (Offen 2); der RPC `sp_unprepare` bleibt abgelehnt ([ADR-0036](0036-handle-freigabe-und-grenze-je-sitzung.md) Entscheidung 2, Entscheid des Nutzers; Offen 3).

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| A — Erfolg beibehalten ([ADR-0036](0036-handle-freigabe-und-grenze-je-sitzung.md)) | idempotent; ein Batch mit einem veralteten Handle schlägt nicht fehl; keine Änderung | widerspricht der Referenz (M-4, beide Instanzen) und dem Grundsatz „Spec ist Zielbild“; ein Client, der doppelt freigibt, merkt den Fehler am Testserver nicht und scheitert am echten Server (verdeckt Clientfehler, [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität)) |
| B — Fehler 8179, Class 16, State 8 (gewählt) | gleich dem echten Server in Nummer, Class, State, Text und offener Verbindung (M-4); der Testserver zeigt dem Client den Fehler, den er später am echten Server sähe | der Treiber bekommt auf einen Batch mit freigegebenen Handles einen Fehler; gemessen ist nur, dass mssql-jdbc 13.4.0 auf den Fehler „Unsupported SQL.“ weitermacht (t6 in [handle-grenze-optionen-2026-11-06](../../messungen/handle-grenze-optionen-2026-11-06.md)), nicht auf 8179; neuer Code in §4 mit State 8 statt 1 |
| C — repo-eigene Nummer, `TDS_UNKNOWN_HANDLE` (50009) wie bei `sp_execute` | kein neuer Code, ein Fehler für „Handle unbekannt“ in beiden Pfaden | Nummer weicht vom echten Server ab (M-4: 8179); der Grundsatz „Zielbild“ spricht dagegen, wo die Referenz gemessen ist; `SPEC-057` ist für `sp_execute` formuliert und würde mitgezogen |

**Beleglage.** Option B ist die einzige, die M-4 trägt; A und C sind Setzungen ohne Messung, A ist durch M-4 widerlegt.

## Konsequenzen

- Positiv: Der Testserver weicht in diesem Punkt nicht mehr von der Referenz ab; ein Client, der ein Handle doppelt oder ein nie vergebenes freigibt, sieht denselben Fehler wie am echten Server.
- Negativ: Ein Batch mit mehreren Freigaben kann einen oder mehrere Fehler enthalten; ein Treiber, der sie nicht erwartet, meldet sie (bei mssql-jdbc für 8179 nicht gemessen; ob der Treiber je ein freigegebenes Handle erneut nennt, ist nicht gemessen). Der Zähler und die Tabelle bleiben davon unberührt.
- **Folgepflicht (nach `Accepted`):** (1) Spezifikation: Liste unten, Träger ist der Bau-Slice zu ADR-0036 (der Planner zieht seinen Start-Trigger und Liefer-Punkt 1 nach; dieser Entwurf ändert keinen Slice). (2) Quelltext und Tests im selben Slice. (3) Die Ausnahme aus dem Live-Lauf entfällt nicht von selbst: der Live-Lauf führt `sp_unprepare` nicht aus (M-4), ein Vergleichsschritt dafür ist eine Entscheidung des Planners, kein Teil dieser ADR (akzeptiertes Negativ: M-4 hält den Wert, die Fitness Function unten hält den Testserver).

### Liste der Spec-Änderungen (schreibt der Träger nach `Accepted`; ergänzt die Liste von [ADR-0036](0036-handle-freigabe-und-grenze-je-sitzung.md), ersetzt keine Zeile außer der genannten)

| Stelle | Änderung |
|---|---|
| `SPEC-030`, Handle-Verwaltung (Zeile „die Batch-Form `EXEC sp_unprepare …` gibt frei“ der Liste von ADR-0036) | ergänzen: ein Handle, das die Sitzung nicht hält (nie vergeben, schon freigegeben, vor `RESETCONNECTION`), ergibt ERROR 8179 (Class 16, State 8), die Verbindung bleibt offen, das Backend wird nicht gerufen; Beleg M-4; Batch mit mehreren Handles: Setzung nach Entscheidung 3 |
| §4, Tabelle der Codes | neue Zeile (Name nach der Konvention der Tabelle, Vorschlag `TDS_SIM_UNKNOWN_PREPARED_HANDLE`) mit Nummer 8179, Class 16, Folge offen, Herkunft: Nummer, Class und State gemessen (M-4); Log-Marke legt die Spezifikation fest |
| §4, Satz „State ist für jeden Code 1“ | Ausnahme für den neuen Code (State 8, gemessen); sonst 1 |
| §4, Zeile `TDS_UNKNOWN_HANDLE` (50009) / `SPEC-057` | keine Änderung; Hinweis, dass der echte Server für `sp_execute` vermutlich ebenfalls 8179 sendet, nicht gemessen (Offen 2) |
| Kompatibilitätsmatrix (`docs/plan/kompatibilitaetsmatrix.md`), Zeile „Handle-Verwaltung je Sitzung“ und Lücke 13 | beim Nachziehen aus ADR-0036 die Antwort auf ein unbekanntes Handle als 8179 führen |
| `SPEC-045`/Ausnahmeliste | keine Ausnahme nötig, sobald gebaut |

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| JVM-Tests (`kotlin.test`) | Batch `EXEC sp_unprepare <n>` mit nie vergebener Nummer, mit bereits freigegebener Nummer und nach `RESETCONNECTION`: ERROR mit Nummer 8179, Class 16, State 8, Text mit der Nummer, DONE mit Fehlerstatus, Verbindung offen, Zähler unverändert, Backend nicht gerufen (Handvektor); gehaltenes Handle: Erfolg ohne Zeilen, Zähler sinkt; Batch `[gehalten, unbekannt, gehalten]`: beide gehaltenen frei, genau ein 8179 (Setzung); gemischter Batch geht weiter an das Backend; der RPC `sp_unprepare` bleibt `TDS_UNSUPPORTED_RPC`. Gegenprobe: antwortet der Server auf ein unbekanntes Handle mit Erfolg, fällt der Test | `make test` |
| Referenzclient gegen die Referenzinstanz (Messung, kein Gate, [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md)) | Zeile M-4 bleibt der Beleg der Nummer, des State und der offenen Verbindung | `make vergleichslauf-d` |

## Offen

1. **Batch mit mehreren Handles, davon eines unbekannt** (Entscheidung 3; bleibt Setzung, ungemessen): Abbruch des Batches oder Weitermachen am echten Server nicht gemessen. Messauftrag: eine Messzeile im Live-Lauf (Ort und Slice legt der Planner fest); weicht sie von der Setzung ab, gilt der Re-Evaluierungs-Trigger.
2. **`sp_execute` mit unbekanntem Handle** am echten Server: nicht gemessen; vermutlich ebenfalls 8179, dann wäre `SPEC-057` (50009) eine weitere Abweichung. Bleibt Setzung (50009); Messauftrag: Messung im Live-Lauf, ausstehend; weicht sie ab, entscheidet eine neue ADR.
3. **RPC `sp_unprepare`:** M-4 ruft ihn „als Prozedur“ auf (am Client `CommandType.StoredProcedure`, nach Name; ob dabei ProcID 15 oder ein Name auf dem Draht steht, ist nicht gemessen) und der echte Server nimmt ihn an. Das ist eine Abweichung des Katalogs (Nicht-Ziel 6, [ADR-0036](0036-handle-freigabe-und-grenze-je-sitzung.md) Entscheidung 2, vom Nutzer so entschieden); sie wird hier nicht geändert. Entscheid des Nutzers („deine empfehlungen“, 2026-11-06): der RPC bleibt abgelehnt, kein Supersedes von [ADR-0005](0005-rpc-und-prepared-statements.md); die Abweichung steht als offene Schuld im Beobachtungs-Register (`BEO-TDS/messung-am-echten-sql-server-fehlt`).
4. **Wortlaut und Felder des ERROR-Tokens** außer Nummer, Class, State, Text: Setzung der Spezifikation.
5. **Entscheidungsnummer:** der Auftrag nannte „Entscheidung 5“ von ADR-0036; die Setzung steht dort in Entscheidung 2 (Entscheidung 5 ist die Überschreitung). Diese ADR ersetzt den Halbsatz in Entscheidung 2 wörtlich; der Vermerk bleibt als Abweichung vom Auftragstext stehen.

## Re-Evaluierungs-Trigger

Eine Messung am echten Server (andere Version oder Image-Digest) liefert für `sp_unprepare` mit unbekanntem Handle eine andere Nummer, Class oder einen anderen State; die Messung zu Offen 1 oder 2 weicht von Setzung oder Spec ab; mssql-jdbc oder ein anderer Client scheitert an 8179 in der Freigabe ohne Zutun des Nutzers (Option A oder eine Entschärfung neu).

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-06 | Accepted – der Nutzer antwortet „ADR-0037 ja“ (Option B) und „deine empfehlungen“ für die Begleitpunkte: RPC `sp_unprepare` bleibt abgelehnt (offene Schuld im Register), `sp_execute` mit unbekanntem Handle und gemischter Batch bleiben Setzung mit Messauftrag | Entscheid des Nutzers |
| 2026-11-06 | Proposed – Entwurf: Option B (Fehler 8179) ersetzt den Halbsatz „ein unbekanntes Handle ist kein Fehler“ aus Entscheidung 2 von ADR-0036 | Messprotokoll `docs/messungen/referenzabgleich.md` M-4; Review vom 2026-11-06 zur Lieferung 7cb411b2 des Live-Laufs, F-4 |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
