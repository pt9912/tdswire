# ADR-0045: `sp_execute` mit unbekanntem Handle ergibt Fehler 8179, State 4, wie am echten Server (Option A: die Spezifikation folgt dem echten Server; ersetzt teilweise ADR-0036 und ADR-0037)

**Status:** Accepted (vom Nutzer entschieden)

**Datum:** 2026-11-08

**Autor:** Architect (Entwurf); Entscheidung: pt9912 (Nutzer), 2026-11-08, per Auswahl „Spec folgt dem echten Server“

**Bezug:** [TDS-FA-04](../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0036](0036-handle-freigabe-und-grenze-je-sitzung.md), [ADR-0037](0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md), [ADR-0005](0005-rpc-und-prepared-statements.md), [ADR-0014](0014-fehlerfolge-rpc-antwort.md), [ADR-0017](0017-prepared-handles-grenzwert.md), [ADR-0008](0008-dotnet-kompatibilitaetstests.md)

**Schärft (nach `Accepted`):** `SPEC-057` (Fehler von `sp_execute` mit unbekanntem Handle: Nummer, State, Text, Name des Codes) und `SPEC-060` (gemeinsamer Code, State je Aufrufform), beide Zeilen in [§4 der Spezifikation](../../../spec/spezifikation.md#4-fehler-codes-und-logging-felder), dort außerdem die Zeile `TDS_UNKNOWN_HANDLE` (Nummer 50009), die Zeile `TDS_SIM_UNKNOWN_PREPARED_HANDLE` und der Satz zu den State-Ausnahmen; sowie [SPEC-030](../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten) (Absatz zu `sp_execute` und Absatz zu `RESETCONNECTION`: die Folge für ein nicht gehaltenes Handle).

**Supersedes (teilweise), nur wörtlich benannt; die Dateien der Vorgänger bleiben unverändert und `Accepted` ([AGENTS.md](../../../AGENTS.md) §3.5):**

- [ADR-0037](0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md) Entscheidung 4, **nur** der Halbsatz „`sp_execute` mit unbekanntem Handle bleibt `SPEC-057` (50009), weil M-4 nur `sp_unprepare` misst (Offen 2)“, und dessen Offen 2 („Bleibt Setzung (50009)“). Die Messung, auf die Offen 2 wartete, liegt vor (M-6); der Re-Evaluierungs-Trigger von ADR-0037 ist damit ausgelöst. Es bleiben: der Rest von Entscheidung 4 (der RPC `sp_unprepare` bleibt abgelehnt), die Entscheidungen 1 bis 3 und Offen 1, 3 bis 5.
- [ADR-0036](0036-handle-freigabe-und-grenze-je-sitzung.md) und [ADR-0017](0017-prepared-handles-grenzwert.md): **nur**, soweit sie für `sp_execute` mit einem Handle, das die Sitzung nicht hält, einen Fehler mit der Nummer 50009 (`TDS_UNKNOWN_HANDLE`) festlegen oder voraussetzen. Handle-Verwaltung, Grenze, Freigabe und alles Übrige beider ADRs bleiben unberührt.

**Regeln:** Baseline-Regelwerk `modul-04-adrs.md` §Ziel-Form: ADR (MADR).

---

## Kontext

`SPEC-057` setzt für `sp_execute` mit einem Handle, das die Sitzung nicht hält, einen eigenen Code `TDS_UNKNOWN_HANDLE` mit der Nummer 50009 (Festlegung, „nicht gemessen“, Class 16, State 1). [ADR-0037](0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md) hat für die Batch-Freigabe schon die Nummer des echten Servers (8179) übernommen und für `sp_execute` die Messung als offen geführt. Der Nutzergrundsatz lautet: die Spezifikation ist das Zielbild, der echte SQL Server die Referenz.

Die Messung M-6 (`docs/messungen/referenzabgleich.md`; Test `Execute_mit_unbekanntem_Handle_und_gemischter_Unprepare_Batch`, `Microsoft.Data.SqlClient` am Referenzclient, 2022 und 2025 mit demselben Ergebnis, Einzelmessung 2026-11-06) zeigt:

| Aussage | Beleg |
|---|---|
| `sp_execute` als Prozedur (`CommandType.StoredProcedure`, `@handle` = 999999) und als SQL-Batch `EXEC sp_execute 999999`: `Number 8179, Class 16, State 4` | M-6 |
| Text „Could not find prepared statement with handle 999999.“, ein Fehler | M-6 |
| die Verbindung bleibt offen | M-6 |
| ein vergebenes und danach freigegebenes Handle ergibt bei `sp_execute` ebenfalls 8179, State 4 | M-7 |
| zum Vergleich `sp_unprepare` mit unbekanntem Handle: dieselbe Nummer 8179, aber State 8 | M-4 |

**Nicht gemessen** (und deshalb unten Setzung): die Tokenfolge auf dem Draht für `sp_execute` mit unbekanntem Handle (M-6 ist am Client gemessen; unter `referenztest-dotnet/Mitschnitt/messung/` liegt kein Mitschnitt dieses Falls, die Dateien dort tragen 8179 nur für `sp_unprepare`, M-22 und M-23); die weiteren Felder des ERROR-Tokens (Servername, Name der Prozedur, Zeilennummer) für `sp_execute`; ob ein RETURNSTATUS die Antwort begleitet; der Fall `sp_execute` nach `RESETCONNECTION` am echten Server; der Wortlaut für andere Nummern als 999999. Für `sp_unprepare` ist am Draht gemessen: ERROR (Prozedur `sp_unprepare`), RETURNSTATUS 8179, DONEPROC, kein DONE (M-22, M-23); das gilt für `sp_execute` nicht als gemessen.

## Entscheidung

Wir wählen **Option A: `sp_execute` mit unbekanntem Handle antwortet wie der echte Server**; die Spezifikation folgt der Messung.

1. **Fehler (Messung M-6).** `sp_execute` mit einem Handle, das die Sitzung nicht hält, ergibt einen ERROR mit Nummer **8179**, Class **16**, State **4** und dem Text `Could not find prepared statement with handle <n>.` (mit der genannten Nummer), gefolgt vom Abschluss der Antwort auf einen RPC mit Fehlerstatus; die Verbindung bleibt offen, das Backend wird nicht gerufen, die Zählung ändert sich nicht. Nummer, Class, State, ein Fehler, offene Verbindung und der Text für Handle 999999 sind am Client gemessen (beide Aufrufformen, 2022 und 2025). Der Text für andere Nummern ist als gleich gesetzt.
2. **Was „unbekannt“ heißt.** Wie bisher in `SPEC-057` und `SPEC-030`: nie vergeben (auch −1 und 0), bereits freigegeben und jedes Handle aus der Zeit vor `RESETCONNECTION`. Gemessen sind „nie vergeben“ (M-6) und „vergeben und freigegeben“ (M-7); `RESETCONNECTION` bleibt Setzung.
3. **Ein Code, zwei States.** Der Code `TDS_SIM_UNKNOWN_PREPARED_HANDLE` (Nummer 8179) trägt den State je Aufrufform: 8 bei der Batch-Freigabe, 4 bei `sp_execute`. Der Code `TDS_UNKNOWN_HANDLE` (50009) wird für diesen Fall nicht mehr gesendet. Ob der Aufzählungswert im Quelltext entfällt oder nur unbenutzt bleibt und wie der State übergeben wird, entscheidet der Bau nach den Regeln der Schichten; die Wirkung am Draht ist hier festgelegt, die Form im Quelltext nicht.
4. **Weitere Felder und Tokenfolge (Setzung, nicht gemessen).** Der Abschluss bleibt die bisherige Form der Antwort auf einen RPC (ERROR und DONEPROC mit Fehlerstatus, Befehl `0x00e0`, kein DONE); Servername, Name der Prozedur und Zeilennummer legt die Spezifikation fest (Setzung; der Name `sp_execute` liegt nahe, weil der echte Server bei `sp_unprepare` seinen Prozedurnamen setzt, ist für `sp_execute` aber nicht gemessen); ob der echte Server einen RETURNSTATUS 8179 wie bei `sp_unprepare` sendet, ist offen (Offen 1).
5. **Log.** Die feste Marke `unknown-handle` bleibt (Festlegung ohne Wirkung am Draht); eine Vereinheitlichung mit `unknown-prepared-handle` ist Sache des Baus.

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| A — 8179, State 4, wie der echte Server (gewählt) | gleich dem echten Server in Nummer, Class, State, Text und offener Verbindung (M-6); ein Client, der auf die Nummer 8179 prüft, verhält sich an beiden gleich ([TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität)); ein eigener Code weniger | Änderung an Spezifikation, Quelltext und Tests; ein Client, der gegen den Testserver auf 50009 prüfte, ändert die Prüfung; State ist nicht mehr für jeden Code 1, eine weitere Ausnahme in §4 |
| B — bei 50009 bleiben (Setzung beibehalten) | keine Änderung; eigener, unterscheidbarer Code des Testservers | widerspricht der Referenz (M-6, beide Instanzen) und dem Grundsatz „Spec ist Zielbild“, wo die Referenz gemessen ist; ein Client, der auf 8179 reagiert, verhält sich am Testserver anders als am echten Server (verdeckt Clientfehler, [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität)); die Abweichung bliebe als offene Schuld bestehen, obwohl sie gemessen ist. Verworfen vom Nutzer am 2026-11-08 |

**Beleglage.** A ist die einzige Option, die M-6 trägt; B ist eine Setzung ohne Messung und durch M-6 für Nummer und State widerlegt.

## Konsequenzen

- Positiv: Der Testserver weicht für beide Wege, ein unbekanntes Handle zu nennen (`sp_execute` und Batch-Freigabe), nicht mehr in der Nummer von der Referenz ab; die Unterscheidung nach State (4 und 8) ist wie am echten Server.
- Negativ: Der Testserver unterscheidet „Handle unbekannt“ nicht mehr an einer eigenen Nummer des Benutzerbereichs; `SPEC-057` verliert ihren eigenen Code; §4 braucht eine weitere State-Ausnahme.
- **Folgepflicht (nach `Accepted`), Träger ist ein Bau-Slice, den der Planner anlegt** (diese Entscheidung ändert weder Spezifikation noch Quelltext noch Planung):
  1. Spezifikation nach der Liste unten.
  2. Quelltext: der Zweig von `sp_execute` mit nicht gehaltenem Handle in der Schleife der Anfragen (`protocol/dispatch/RequestLoop.kt`, bisher `ErrorCode.TDS_UNKNOWN_HANDLE`) und die Regeln im Katalog der Codes (`protocol/error/ErrorCode.kt`, `ErrorMapper`); State je Aufrufform.
  3. Tests: die Tests, die 50009 und `TDS_UNKNOWN_HANDLE` behaupten (`PreparedHandlesDispatchTest`, `ResetConnectionDispatchTest`, `RequestSequenceTest`, `ErrorMapperTest`), und ein Handvektor für 8179/State 4; Referenzclient-Tests gegen den Serverprozess, soweit einer 50009 erwartet.
  4. Kompatibilitätsmatrix und deren Zitate (`make matrix-zitate`), soweit sie 50009 nennen.

### Liste der Spec-Änderungen (schreibt der Träger nach `Accepted`)

| Stelle | Änderung |
|---|---|
| `SPEC-057` (Zeile in §4) | Nummer 8179 (Class 16, State 4) statt 50009; Text „Could not find prepared statement with handle <n>.“; der Satz „… nicht mit 50009; die Abweichung ist gemessen, ihre Behandlung nicht entschieden“ entfällt zugunsten der Aussage, dass der Testserver dem echten Server folgt (Beleg M-6); Tokenfolge und weitere Felder als Setzung kennzeichnen (Entscheidung 4) |
| `SPEC-030`, Absatz `sp_execute` und Absatz `RESETCONNECTION` | der Verweis auf `SPEC-057` bleibt; die Folge nennt 8179/State 4 |
| `SPEC-060` und Zeile `TDS_SIM_UNKNOWN_PREPARED_HANDLE` | State je Aufrufform (4 bei `sp_execute`, 8 bei der Batch-Freigabe); Herkunft: beides gemessen (M-4, M-6) |
| §4, Zeile `TDS_UNKNOWN_HANDLE` (50009) | entfällt oder wird als nicht mehr gesendet geführt (der Träger wählt die Form, die zu Entscheidung 3 passt) |
| §4, Satz „State ist für jeden Code 1 (Ausnahmen, gemessen: …)“ | Ausnahme `TDS_SIM_UNKNOWN_PREPARED_HANDLE`: State 8 und 4 |
| Kompatibilitätsmatrix (`docs/plan/kompatibilitaetsmatrix.md`), Zeilen `sp_execute` und „Handle-Verwaltung je Sitzung“ | Antwort auf ein unbekanntes Handle als 8179/State 4 führen |
| Messprotokoll, Zeile M-6 | der Hinweis „`SPEC-057` und `TDS_UNKNOWN_HANDLE` bleiben bis dahin unverändert“ ist mit dem Nachzug eingelöst; der Träger zieht die Zeile nach |

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| JVM-Tests (`kotlin.test`) | `sp_execute` mit nie vergebenem (auch −1, 0), freigegebenem und nach `RESETCONNECTION` nicht mehr gehaltenem Handle: ERROR mit Nummer 8179, Class 16, State 4, Text mit der Nummer, Abschluss mit Fehlerstatus, Verbindung offen, Backend nicht gerufen, Zähler unverändert (Handvektor); die Batch-Freigabe behält State 8. Gegenprobe: antwortet der Server mit 50009 oder State 8, fällt der Test | `make test` |
| Referenzclient gegen die Referenzinstanz (Messung, kein Gate, [ADR-0022](0022-e2e-laeufe-nicht-in-den-gates.md)) | Zeile M-6 bleibt der Beleg von Nummer, State und offener Verbindung | `make vergleichslauf-d` |

## Offen

1. **Tokenfolge und Felder auf dem Draht** für `sp_execute` mit unbekanntem Handle (RETURNSTATUS 8179? Name der Prozedur im ERROR? Zeilennummer?): nicht gemessen; bleibt Setzung (Entscheidung 4). Akzeptiertes Negativ: der Client sieht Nummer, Class, State und Text richtig, nur die Bytes dahinter sind Setzung; ein Messauftrag ist keine Bedingung dieser Entscheidung. Weicht eine spätere Messung ab, gilt der Re-Evaluierungs-Trigger.
2. **Der Fall nach `RESETCONNECTION`** am echten Server: nicht gemessen; bleibt Setzung (Entscheidung 2), dieselbe Lage wie bei der Batch-Freigabe.

## Re-Evaluierungs-Trigger

Eine Messung am echten Server (andere Version oder anderer Image-Digest) liefert für `sp_execute` mit unbekanntem Handle eine andere Nummer, Class oder einen anderen State; eine Messung auf dem Draht weicht von der Setzung in Entscheidung 4 ab; ein Client scheitert am Testserver wegen der geänderten Nummer, ohne dass der echte Server ihn träfe.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-08 | Accepted – der Nutzer wählt „Spec folgt dem echten Server“ (Option A); Option B (bei 50009 bleiben) verworfen | Entscheid des Nutzers |
| 2026-11-08 | Proposed – Entwurf nach Messung M-6 (Fehler 8179, State 4 für `sp_execute` mit unbekanntem Handle); löst den Re-Evaluierungs-Trigger von ADR-0037 (Offen 2) aus | Messprotokoll `docs/messungen/referenzabgleich.md` M-6, M-7 |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
