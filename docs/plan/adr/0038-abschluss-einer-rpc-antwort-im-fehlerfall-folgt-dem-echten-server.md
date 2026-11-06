# ADR-0038: Abschluss einer RPC-Antwort im Fehlerfall folgt dem echten Server

**Status:** Accepted

**Datum:** 2026-11-06

**Autor:** pt9912

**Bezug:** [TDS-FA-10](../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-FA-06](../../../spec/lastenheft.md#tds-fa-06--fehler-und-abschlusszustände), [TDS-FA-04](../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0005](0005-rpc-und-prepared-statements.md), [ADR-0006](0006-backend-vertrag-und-streaming.md), [ADR-0007](0007-fehler-und-attention.md), [ADR-0014](0014-fehlerfolge-rpc-antwort.md) (zurückgezogen, siehe „Verhältnis zu ADR-0014“), [ADR-0015](0015-spannungen-adr-gegen-spec.md), [ADR-0017](0017-prepared-handles-grenzwert.md), [ADR-0036](0036-handle-freigabe-und-grenze-je-sitzung.md)

**Schärft:** [SPEC-030](../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten) (Abschluss der RPC-Antwort im Fehlerfall), [SPEC-031](../../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität) (Festlegungen des Response Sequencers zu `DONE_MORE` und zum Weiterlaufen), [TDS-FA-10.a](../../../spec/spezifikation.md#tds-fa-10a--mehrteilige-antworten-und-rpc-ausgabewerte), [SPEC-059](../../../spec/spezifikation.md#einstieg-des-integrators-vertrag-zu-spec-059) (Fläche: `Failure` mit Klasse, nur Option A). Kein `Supersedes`: [ADR-0014](0014-fehlerfolge-rpc-antwort.md) war nie `Accepted` und ist zurückgezogen; kein Satz einer angenommenen ADR wird überstimmt (siehe „Verhältnis zu angenommenen ADRs“).

---

## Kontext

Die Frage aus [ADR-0014](0014-fehlerfolge-rpc-antwort.md) bleibt: wie endet die Antwort auf eine RPC-Nachricht, die mit einem Fehler endet. [ADR-0014](0014-fehlerfolge-rpc-antwort.md) setzte sie ohne Messung (Option A dort: DONEPROC mit Fehlerstatus, ohne `MORE`); der Nutzer hat die ADR am 2026-11-06 nach der Messung zurückgezogen. Maßstab ist der Nutzergrundsatz vom 2026-11-06: **Spezifikation ist Zielbild, der echte SQL Server ist die Referenz** (die Messung ist die Referenz, wo sie etwas zeigt).

**Beleg.** Messreihe am Draht vom 2026-11-06, Messpunkte W-1 bis W-3 des Messprotokolls `docs/messungen/referenzabgleich.md` (Abschnitt „Messreihe am Draht: Messungen vom 2026-11-06“; dieser Abschnitt steht zum Zeitpunkt dieser ADR nur im Zweig des Slice für die Messungen, nicht auf `main`; der Link wird nach dem Merge nachgetragen, solange die ADR `Proposed` ist). Instanzen `2022-latest` (16.0.4305.5, Digest `sha256:cf8f0ab2…a291718`) und `2025-latest` (Digest-Pin laut Protokoll), Referenzclient `Microsoft.Data.SqlClient` 7.1.1 auf .NET 10.0.14, TLS-beendender Proxy, Aufnahme entschlüsselt, Rohbytes je Szenario als Dateien `referenztest-dotnet/Mitschnitt/messung/{2022,2025}/<Szenario>.messung`. Jede Zeile unten ist eine **Einzelmessung je Instanz** (ein Fall je Szenario, beide Instanzen gleich), keine Verteilung.

| Nr | Messzeile (Szenario) | Befund |
|---|---|---|
| M1 | W-1, `rpc-fehler-vor-zeilen`, `rpc-fehler-batch-abbrechend` (`sp_executesql`, Fehler 208) | `ERROR RETURNSTATUS DONEPROC`; DONEPROC mit `ERROR`, ohne `MORE`, ohne `COUNT`; RETURNSTATUS = **208** (Fehlernummer); die Anweisung nach dem Fehler läuft nicht; kein DONE |
| M2 | W-2, `rpc-fehler-nach-zeilen` (Fehler 8134 nach drei Zeilen) | `COLMETADATA ROW+ ERROR DONEINPROC RETURNSTATUS DONEPROC`; DONEINPROC `MORE\|ERROR`; **DONEPROC ohne Fehlerbit**; RETURNSTATUS **8134** |
| M3 | W-2, `rpc-fehler-statement-abbrechend` (`SELECT 1/0; SELECT 2`) | `COLMETADATA ERROR DONEINPROC COLMETADATA ROW+ DONEINPROC RETURNSTATUS DONEPROC`; erstes DONEINPROC `MORE\|ERROR`; die **zweite Anweisung läuft**; DONEPROC ohne Bits; RETURNSTATUS **0** |
| M4 | W-3, `rpc-fehler-ausgabewert` | `DONEINPROC COLMETADATA ERROR DONEINPROC RETURNSTATUS RETURNVALUE DONEPROC`: **RETURNVALUE auch nach dem Fehler** (Ordinal 2, Wert 1), RETURNSTATUS 8134, DONEPROC ohne Bits |
| M5 | W-3, `proc-fehler-*` (benannte Prozeduren) | ERROR trägt den Prozedurnamen; RETURNSTATUS **-6**; DONEPROC ohne Bits; `RAISERROR` Stufe 16 zwischen zwei Ergebnissen: DONEINPROC `MORE\|ERROR`, das zweite Ergebnis folgt |
| M6 | W-1 bis W-3, alle `rpc-fehler-*`, `proc-fehler-*` | in **keinem** Fall endet die RPC-Antwort mit DONE; Referenzclient: Ausnahme am Ort des Fehlers (`ExecuteReader`, `Read`, `NextResult`) mit gleicher Nummer, Klasse, State; Verbindung offen; Folgeabfrage gelingt |

**Gegen den Bestand.** [ADR-0014](0014-fehlerfolge-rpc-antwort.md) Option A passt nur zu M1 (Fehler vor dem ersten Ergebnis). Der Bau (`failedDone()`, `ResponseRpcTest`) schreibt DONE mit Fehlerstatus und weicht von M1 bis M6 ab; die Ausnahmeliste des Mitschnitt-Laufs führt die Folgen als A-10 (`curCmd`), A-16 (RETURNSTATUS/RETURNVALUE) und A-17 (Division durch null, `MORE`), jeweils als „Lücke“. Die Spezifikation setzt in `SPEC-031` für statement-abbrechende Fehler die Variante ohne `DONE_MORE` und „ein Fehler beendet den Lauf“; M3 widerspricht beidem. Die Nutzerentscheidung vom 2026-11-06 im Bau-Slice zur Angleichung der Antwort-Tokens an die Messung (`Failure` trägt die Klasse; Kante zu `SPEC-059`) liegt vor dieser ADR; sie ist dort als Bauauftrag entschieden, die Ausprägung im Fehlerfall entscheidet diese ADR.

**Nicht gemessen** (bleibt in allen Optionen Setzung): Fehler in `sp_prepexec`, `sp_execute`, `sp_prepare` und in einer unbekannten ProcID; ungültig kodierte RPC-Nachrichten; Fehler anderer Typen als 208 und 8134; Fehler in einer Prozedur in einer Prozedur; die Reaktion des Referenzclients auf DONE gegen DONEPROC (nicht messbar, weil der Server DONE im RPC nicht sendet, M6).

## Entscheidung

Wir wählen **Option A** (Nutzerentscheid vom 2026-11-06, „deine Empfehlungen“, bestätigt die Empfehlung des Architects; bestätigt sind auch die Setzungen in Entscheidung 3 und 4): Die Antwort auf eine RPC-Nachricht endet im Fehlerfall **wie am echten Server gemessen**, und der Backend-Vertrag trägt dafür die Klasse des Fehlers.

1. **Abschluss ist DONEPROC, nie DONE** (M1 bis M6; Setzung nur für die nicht gemessenen RPC-Formen: `sp_prepexec`, `sp_execute`, `sp_prepare` folgen derselben Regel). Das gilt für ein `Failure` im Strom, für eine Ablehnung vor dem Backend (`SPEC-013`), für `TDS_INTERNAL_ERROR` und `TDS_SHUTDOWN`, soweit überhaupt ERROR und Abschluss geschrieben werden. Die Antwort auf einen SQL_BATCH bleibt ERROR und DONE (gemessen in [ADR-0007](0007-fehler-und-attention.md), R01, R05 bis R07).
2. **Statusbit nach Lage des Fehlers** (M1 bis M3). Tritt der Fehler **vor** dem ersten Ergebnis der RPC-Antwort auf: `ERROR`, `RETURNSTATUS`, `DONEPROC[ERROR]` (ohne `MORE`, ohne `COUNT`). Tritt er **nach** Beginn eines Ergebnisses auf: `ERROR`, `DONEINPROC[MORE|ERROR]` für die betroffene Anweisung, `RETURNSTATUS`, `DONEPROC` **ohne** Fehlerbit. Die Zuordnung „Fehlerbit am Abschluss der Anweisung, nicht am Abschluss der Prozedur, sobald eine Anweisung abgeschlossen oder begonnen ist“ ist die am Draht gemessene Regel für `sp_executesql` mit einer bzw. zwei Anweisungen; die Verallgemeinerung auf andere Verschachtelungen ist **Setzung**.
3. **Statement- gegen batch-abbrechend** (M1, M3). `Failure` trägt eine **Klasse**. Bei einem statement-abbrechenden Fehler schreibt der Sequenzierer `DONEINPROC[MORE|ERROR]`, **liest weitere Ereignisse und führt die folgende Anweisung aus**; bei einem batch-abbrechenden Fehler beendet er die Antwort und liest nichts mehr. Welche Fehler welcher Klasse angehören, bestimmt das Backend (es liefert die Klasse im `Failure`); der Server dieser Bibliothek setzt für seine eigenen Fehler (`TDS_BACKEND_ERROR`, `TDS_UNSUPPORTED_SQL`, Verstöße gegen die Ereignisreihenfolge) die Klasse batch-abbrechend (**Setzung**, kein Messbefund).
4. **RETURNSTATUS und RETURNVALUE im Fehlerfall** (M1, M2, M4). Die RPC-Antwort trägt im Fehlerfall RETURNSTATUS: die **Nummer des letzten ERROR-Tokens**, wenn der Fehler die Ausführung beendet hat (M1, M2, M4), und 0, wenn die Ausführung nach dem Fehler weiterlief (M3). RETURNVALUE bleibt auch nach einem Fehler in der Antwort, wenn der Ausgabewert vor dem Fehler gesetzt war (M4). Der RETURNSTATUS-Wert -6 benannter Prozeduren (M5) ist **nicht Gegenstand**: das Simulations-Backend hat keine Prozeduren, und die Zahl hängt an der Art des Fehlers in der Prozedur, die nicht gemessen ist. Für Fehler ohne SQL-Server-Entsprechung (Ablehnung vor dem Backend mit `TDS_UNSUPPORTED_RPC`, interne Fehler) gilt als **Setzung** `ERROR`, `DONEPROC[ERROR]` ohne RETURNSTATUS; die Nummern der eigenen Fehlerkategorien sind keine Fehlernummern des echten Servers.
5. **Ungültig kodierte RPC-Nachricht** bleibt, was die Spezifikation setzt (`SPEC-011`: Struktur- und Kodierfehler sind `TDS_PROTOCOL_ERROR`, Class 20, Verbindung schließt; `SPEC-013`: gültig kodierte, nicht unterstützte Form ist `TDS_UNSUPPORTED_RPC`, Class 16, Verbindung offen; Nutzerentscheidung vom 2026-10-31, nicht gemessen). Diese ADR ändert die Trennlinie nicht; sie legt nur den Abschluss fest (Entscheidung 1, 4 letzter Satz).

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| **A — Zielbild gleich echter Server, voll (gewählt, Empfehlung)**: DONEPROC, Statusbit nach Lage, `Failure` mit Klasse, RETURNSTATUS und RETURNVALUE im Fehlerfall | folgt dem Grundsatz „Spec ist Zielbild, der echte Server ist die Referenz“; schließt A-10, A-16, A-17 und Konflikte 3 und 4 der Messung; ein Treiber, der Tokens nach Fehlern auswertet (Ausgabewerte, RETURNSTATUS), sieht die gemessene Folge; kein Sonderfall nach Pakettyp | berührt die öffentliche Fläche (`Failure` mit Klasse, `SPEC-059`, Sensor `make flaeche`); Sequenzierer liest nach einem Fehler weiter (größerer Zustandsraum, mehr Tests); Verallgemeinerung über die gemessenen Fälle hinaus bleibt Setzung |
| B — wie [ADR-0014](0014-fehlerfolge-rpc-antwort.md) Option A: DONEPROC mit Fehlerstatus, ohne `MORE`, ohne RETURNSTATUS/RETURNVALUE, `Failure` beendet die Antwort | kein Eingriff in den Backend-Vertrag; kleinster Bau; stimmt für Fehler vor dem ersten Ergebnis (M1) | falsch für Fehler nach Zeilen (M2, M3, M4): Fehlerbit am falschen Token, Ausgabewert und RETURNSTATUS fehlen, statement-abbrechende Fehler laufen nicht weiter; A-16 und A-17 bleiben dauerhaft Ausnahmen; widerspricht dem Grundsatz |
| C — Mischform: Tokenfolge nach M1 bis M4 (DONEPROC, `DONEINPROC[MORE\|ERROR]`, RETURNSTATUS, RETURNVALUE), aber **ohne** Klasse: jedes `Failure` beendet die Antwort | echte Form für den Fall „Fehler beendet die Ausführung“ (M1, M2, M4); keine Änderung der öffentlichen Fläche; M3 bleibt als einzige Ausnahme | statement-abbrechendes Weiterlaufen (M3) fehlt dauerhaft, bis ein Backend es braucht; A-17 teils offen; zwei Stände derselben Regel, später Fläche und Sequenzierer doch ändern |
| D — DONE belassen (heutiger Bau, nichts tun) | kein Code, kein Test | widerspricht M6 (DONE im RPC kommt am echten Server nicht vor) und allen Tokenfolgen; kein Maßstab |

## Konsequenzen

- Positiv: Fehlerabschluss im RPC hat Messzeilen statt Setzung; die Ausnahmen A-10 (soweit DONEPROC/`curCmd`), A-16 und A-17 und die Konflikte 3 und 4 der Messung haben einen Träger; die Grundsatzfrage „Spec oder Server“ ist für diesen Gegenstand beantwortet.
- Negativ: `Failure` bekommt ein Mitglied (Klasse); der Sequenzierer unterscheidet zwei Verläufe nach einem Fehler; die gemessene Regel gilt an je einem Fall je Form (Einzelmessungen), alles darüber ist Setzung.
- **Backend-Vertrag und öffentliche Fläche (nur Option A).** `QueryEvent.Failure` (derzeit `code`, `message`) erhält eine Klasse; `Failure` ist Teil von `SPEC-059`. Ein neuer Typ oder ein neues Mitglied braucht im selben Bau-Commit-Satz einen Eintrag in `SPEC-059`, sonst meldet `make flaeche` den Unterschied (der Sensor vergleicht Typnamen, keine Mitglieder; das Mitglied muss die Spezifikation trotzdem nennen). Ein bestehendes Backend ohne Klasse bleibt lauffähig, wenn die Klasse einen Vorgabewert hat (batch-abbrechend, Entscheidung 3); das ist Bauentscheidung des Slice, nicht dieser ADR.
- **Folgepflicht (nach `Accepted`).** (1) Spezifikation: `SPEC-031` („Festlegungen des Response Sequencers“: `DONE_MORE` bei statement-abbrechendem Fehler, „ein Fehler beendet den Lauf“, Absatz zur Befehlskennung), `SPEC-030` (Abschluss im Fehlerfall, RETURNSTATUS im Fehlerfall), `TDS-FA-10.a` (Antwortformen im Fehlerfall; der Wortlaut des Lastenhefts `TDS-FA-10` wird gefüllt, nicht geändert), `SPEC-059` (Liste). (2) Quelltext und Tests: `failedDone()` und `ResponseRpcTest` (Pin DONE auf DONEPROC); Handvektoren aus den Messdateien für M1 bis M4. (3) Ausnahmeliste des Mitschnitt-Laufs: A-16, A-17 werden Ausnahmen mit Träger oder entfallen; A-10 entfällt für `curCmd` im Fehlerfall zusammen mit dem Slice. Träger für (1) bis (3): der Bau-Slice zur Angleichung der Antwort-Tokens an die Messung (Konflikte 2 bis 4); Fehlerbit und Klasse sind dort noch nicht als eigene Liefer-Punkte benannt, der Planner zieht nach.
- **Verhältnis zu ADR-0014.** Die zurückgezogene ADR bleibt als Verlauf stehen. Ihre Entscheidung 3 (ungültig kodierte RPC) lebt als Spec-Stand weiter (Entscheidung 5 oben); ihre Entscheidung 4 (statement-abbrechend „nicht Gegenstand“) ist hier entschieden (Entscheidung 3).
- **Verhältnis zu angenommenen ADRs.** [ADR-0017](0017-prepared-handles-grenzwert.md) (Ablehnung des 1001. Handles: „ERROR … und DONEPROC mit Fehlerstatus nach ADR-0014“) und [ADR-0036](0036-handle-freigabe-und-grenze-je-sitzung.md) nennen [ADR-0014](0014-fehlerfolge-rpc-antwort.md) im Bezug. Der Satz der ADR-0017 beschreibt eine Ablehnung vor dem Backend und vor jedem Ergebnis; das ist Fall M1 und widerspricht Entscheidung 2 nicht (Entscheidung 4 letzter Satz: dort ohne RETURNSTATUS, weil die Fehlerkategorie keine Nummer des echten Servers ist). Beide Texte bleiben unverändert ([AGENTS.md](../../../AGENTS.md) §3.5); der Verweis löst auf die zurückgezogene Datei auf. [ADR-0015](0015-spannungen-adr-gegen-spec.md) (Reihenfolge RETURNVALUE/RETURNSTATUS, Ordinal der RETURNVALUE) wird nicht berührt: das Ordinal ist ein anderer Messpunkt (W-5, Konflikt 1 der Messung). [ADR-0027](0027-abbruch-und-zuruecksetzen.md) (ENVCHANGE bei `RESETCONNECTION`, Cancel-Antwort) wird nicht berührt.
- **Akzeptiertes Negativ (einmalig, mit Grund).** Fehler in `sp_prepexec`, `sp_execute`, `sp_prepare`, in unbekannter ProcID und in verschachtelten Prozeduren sind nicht gemessen; die Verallgemeinerung (Entscheidung 1, 2) ist Setzung, weil das Simulations-Backend nur `sp_executesql` und die Prepare-Familie bedient und ein weiterer Messlauf keinen Verbraucher hat. Wer diese Fälle braucht, löst den Trigger unten aus.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| JVM-Tests (`kotlin.test`) | Handvektoren aus den Messdateien M1 bis M4: Fehler vor Zeilen, nach Zeilen, statement-abbrechend, mit Ausgabewert; keine RPC-Antwort endet mit DONE | `make test` (Gate noch einzurichten; Träger: der Slice oben) |
| Mitschnitt-Vergleich (Referenzabgleich) | Ausnahmen A-16, A-17 entfallen oder sind begründet | — (kein Gate, `make vergleichslauf-d`) |
| Flächen-Sensor | `Failure` mit Klasse steht in `SPEC-059` (nur Typnamen) | `make flaeche` |

## Offen

- Entschieden am 2026-11-06 (Quelle: Nutzerentscheid): Option A, mit den Setzungen „eigene Server-Fehler sind batch-abbrechend“ (Entscheidung 3) und „kein RETURNSTATUS bei Fehlern ohne SQL-Server-Nummer“ (Entscheidung 4).
- Nicht gemessen: siehe Kontext, „Nicht gemessen“.
- Der Messabschnitt liegt auf `main`: [Messreihe am Draht](../../messungen/referenzabgleich.md#messreihe-am-draht-messungen-vom-2026-11-06).

## Re-Evaluierungs-Trigger

Eine Messung, nach der einer der Fälle M1 bis M5 auf einer weiteren Serverversion oder einem weiteren Client anders endet; eine Messung eines nicht gemessenen RPC-Fehlerfalls (`sp_prepexec`, `sp_execute`, unbekannte ProcID, verschachtelte Prozedur), die von Entscheidung 1 oder 2 abweicht; ein Backend, das Fehler in einer anderen Klasse liefert als die Setzung in Entscheidung 3; ein Client, der ein fehlendes RETURNSTATUS bei Fehlern ohne SQL-Server-Nummer als Fehler zeigt.

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-06 | Proposed – Folge-Entscheidung zur zurückgezogenen [ADR-0014](0014-fehlerfolge-rpc-antwort.md) (Nutzerentscheid „ADR-0014 wie von dir empfohlen“); Messbeleg W-1 bis W-3 vom 2026-11-06; Annahme steht aus | [ADR-0014](0014-fehlerfolge-rpc-antwort.md) |
| 2026-11-06 | Accepted – Option A mit den Setzungen zu Entscheidung 3 und 4; Quelle: Nutzerentscheid („deine Empfehlungen“) | — |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
