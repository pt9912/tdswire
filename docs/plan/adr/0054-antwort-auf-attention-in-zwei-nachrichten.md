# ADR-0054: Antwort auf ATTENTION in zwei Nachrichten wie am echten Server (ersetzt teilweise ADR-0027 Entscheidung 1)

**Status:** Accepted (vom Nutzer entschieden, 2026-11-19)

**Datum:** 2026-11-18

**Autor:** pt9912

**Bezug:** [TDS-FA-07](../../../spec/lastenheft.md#tds-fa-07--abbruch-und-ressourcenfreigabe), [TDS-QA-01](../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0027](0027-abbruch-und-zuruecksetzen.md), [ADR-0007](0007-fehler-und-attention.md), [ADR-0008](0008-dotnet-kompatibilitaetstests.md), [Messprotokoll des Referenzabgleichs](../../messungen/referenzabgleich.md)

**Schärft:** [SPEC-029](../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung) (Antwort auf ATTENTION), [SPEC-001](../../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung) (`CANCELLING`, Regel „doppelte DONE-Tokens sind verboten“), [SPEC-045](../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) (Ausnahmeliste: A-7, A-13). Diese ADR ändert `spec/` nicht; der Nachzug steht unter Konsequenzen.

**Supersedes (teilweise), nur wörtlich benannt, wirksam erst mit dem Status `Accepted`; die Datei von [ADR-0027](0027-abbruch-und-zuruecksetzen.md) bleibt unverändert und `Accepted` ([AGENTS.md](../../../AGENTS.md) §3.5):** [ADR-0027](0027-abbruch-und-zuruecksetzen.md) Entscheidung 1, **nur** die Aussagen

- „nur DONE mit ATTENTION-Status (`DONE[ATTN]`), keine zweite Nachricht“ und „sendet genau eine Bestätigung `DONE[ATTN]`“ für ATTENTION in `RUNNING` oder `STREAMING`;
- „Die Zwei-Nachrichten-Form des echten Servers … wird nicht gebaut, bevor das Cancel-Szenario am Referenzclient sie braucht“;
- im Abschnitt „Messungen“ der Punkt „Zwei-Nachrichten-Form des Cancel: nur wenn das Referenzclient-Szenario mit der Ein-Nachricht-Form scheitert“ und der erste Teil des Re-Evaluierungs-Triggers („Das Referenzclient-Szenario scheitert mit der Ein-Nachricht-Form“), soweit er die Bedingung des Aufschubs trägt.

Es bleiben: die Bestätigung `DONE[ATTN]` als einzelne Nachricht für ATTENTION **nach** Ende der Antwort und im Leerlauf (zweiter Teil von Entscheidung 1), Entscheidung 2 (Cancel ist kein Fehler, `QUERY_TIMEOUT`), 3, 4, 5 und alle weiteren Entscheidungen von ADR-0027.

---

## Kontext

[ADR-0027](0027-abbruch-und-zuruecksetzen.md) Entscheidung 1 (Setzung des Nutzers, 2026-11-01) lässt den Server auf ATTENTION im Stream mit einer Nachricht `DONE[ATTN]` antworten und schiebt die Zwei-Nachrichten-Form des echten Servers auf, bis ein Referenzclient-Szenario sie braucht. Dieser Aufschub ist **bedingt, kein Ziel**. Die Bedingung ist nicht eingetreten: der Referenzclient nimmt beide Formen an (Live-Lauf, A-7: Verbindung bleibt offen, Folgeabfrage gelingt).

Seither ist die Form des echten Servers am Draht gemessen (Messprotokoll, Abschnitt Mitschnitt-Lauf, Ergebnis 5, und Regel A-13; Messreihe W-9). Der Nutzer hat am 2026-11-18 entschieden: **Code angleichen**. Maßstab ist der Nutzergrundsatz „Spezifikation ist Zielbild, der echte Server ist die Referenz“, wie er schon für [ADR-0038](0038-abschluss-einer-rpc-antwort-im-fehlerfall-folgt-dem-echten-server.md) galt. [ADR-0027](0027-abbruch-und-zuruecksetzen.md) ist `Accepted` und nicht änderbar; die Korrektur ist diese Folge-ADR. Ohne sie bräuchte der Bau einen Wortlaut, den eine angenommene ADR ausdrücklich verneint.

**Beleg (gemessen, Einzelmessungen: je Szenario und Instanz eine Aufnahme, Client `Microsoft.Data.SqlClient` 7.1.1, Instanzen 2022 und 2025, Standard `ConnectRetryCount=1`):**

| Nr | Messzeile | Befund |
|---|---|---|
| M1 | Mitschnitt `cancel-streaming/01`, A-13 | `Cancel()` nach einem Antwortpaket: zwei Nachrichten. Erste: `COLMETADATA ROW+ DONE`, `DONE` mit Status `ERROR`, Befehl `0x00c1`, ohne Zeilenzahl. Zweite: `DONE` mit Status `ATTN`, Befehl `0x00fd`, ohne Zeilenzahl. Kein ERROR-Token. |
| M2 | Mitschnitt, Ergebnis 5, A-15 (`WAITFOR`: Frist und `Cancel()`) | wartender Befehl: zwei Nachrichten, `DONE` mit `ERROR` **ohne ERROR-Token**, dann `DONE` mit `ATTN` |
| M3 | Mitschnitt, Ergebnis 5 (a) | Frist 2 s und `Cancel()` nach 1,9 s und nach 2,1 s: der Client sendet **ein** ATTENTION (je ein Lauf je Instanz) |
| M4 | Mitschnitt, Ergebnis 5 (c) | `Cancel()` an einem beendeten oder nicht gestarteten Befehl sendet kein ATTENTION (je ein Lauf je Instanz) |
| M5 | W-9, A-18 | beide Instanzen: zwei Nachrichten; SESSIONSTATE vor dem `DONE[ERROR]` nur bei 2022 mit `ConnectRetryCount=1`, bei `ConnectRetryCount=0` nie; die Zwei-Nachrichten-Form war mit beiden Einstellungen gleich |
| M6 | Live-Lauf A-7 | Referenz: `SqlException` Number 0, Class 11, State 0, Verbindung offen, Folgeabfrage gelingt; Text der Referenz in einem Teil der Läufe mit, in anderen ohne den Zusatz „ Operation cancelled by user.“ |

**Nicht gemessen** (bleibt in allen Optionen Setzung oder offen): ATTENTION im Leerlauf über einen anderen Weg als `Cancel()`; Abstände zwischen `Cancel()` und Frist unter 100 ms; ein zweites ATTENTION je Anfrage (M3: am Client nicht beobachtet); die Reaktion anderer Clients; der Befehl `curCmd` der ersten Nachricht beim wartenden Befehl (M2 nennt ihn nicht); die Folgerung, dass der vom Client erzeugte Text (A-7) mit der Zwei-Nachrichten-Form gleich wird. Der Text entsteht nach Messung und Code im Client (weder `src/` noch `testserver/` enthalten ihn, der Server sendet keinen ERROR-Token); das ist eine Folgerung, kein Beleg.

## Entscheidung

Wir wählen **Option A**: Der Server antwortet auf ATTENTION in `RUNNING` und `STREAMING` in **zwei Nachrichten** wie der echte Server (Nutzerentscheid 2026-11-18: Code angleichen). Angenommen am 2026-11-19 vom Nutzer, einschließlich der Bestätigung eines ATTENTION während `CANCELLING` als einzelnes `DONE[ATTN]` (Nr. 4, dritter Punkt).

1. **Wire-Form** (M1, M2, M5).
   - *Im Stream* (mindestens ein Antwortpaket ist gesendet): erste Nachricht = das Ende der abgebrochenen Antwort als `DONE` mit Status `ERROR` (ohne `MORE`, ohne `COUNT`), Befehl nach der Form des Ereignisses wie in [SPEC-031](../../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität); gemessen ist `0x00c1`. Zweite Nachricht = `DONE` mit Status `ATTN`, Befehl `0x00fd`.
   - *Bei wartendem Befehl* (die Anfrage hat noch nichts geschrieben, M2): erste Nachricht = `DONE` mit `ERROR`, zweite = `DONE` mit `ATTN`, jeweils ohne ERROR-Token. Der Befehl der ersten Nachricht ist in M2 nicht genannt; der Bau entnimmt ihn den Messdateien (Handvektor) und trägt ihn als gemessen oder als Setzung ein.
   - **Kein ERROR-Token in keinem der beiden Fälle.** Cancel bleibt im Sinne von [ADR-0027](0027-abbruch-und-zuruecksetzen.md) Entscheidung 2 kein Fehler: kein ERROR-Token, kein Fehler-Eintrag in der Observability; das Fehlerbit im `DONE` ist die Form des Drahtes, kein Fehlerereignis des Servers (Setzung der Lesart).
   - Es wird kein SESSIONSTATE geschrieben (A-18 bleibt, Nutzerentscheid 2026-11-06 „kein Bau“).
2. **Zustand `CANCELLING`** ([SPEC-001](../../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung), Zustand der Aufzählung, unverändert). Ablauf: ATTENTION gewinnt den Übergang `RUNNING`/`STREAMING` → `CANCELLING`; genau ein Cancel an das Backend; die erste Nachricht ersetzt die reguläre Abschlussnachricht (diese wird nicht geschrieben); die zweite folgt unmittelbar danach unter derselben Sperre der Sitzung, ohne dass dazwischen eine andere Nachricht geschrieben wird (Setzung); danach `CANCELLING` → `IDLE`. Wann die erste Nachricht geschrieben wird, relativ zum Abschluss des Backends, legt der Bau fest; die Form ist hier festgelegt, nicht der Zeitpunkt.
3. **Lesart von „doppelte DONE-Tokens sind verboten“** ([SPEC-001](../../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung)). Verboten bleibt ein zweiter Abschluss **derselben Antwort** durch den Gewinn zweier Übergänge. Das `DONE[ERROR]` der Cancel-Form ist der Abschluss der abgebrochenen Antwort und ersetzt das reguläre `DONE`; das `DONE[ATTN]` ist die Bestätigung des ATTENTION, eine eigene Nachricht. Je Anfrage steht damit entweder der reguläre Abschluss oder das Paar (Setzung der Lesart; der Wortlaut in der Spezifikation wird mit dem Bau angepasst).
4. **Race-Fälle** (mit Ausnahme von M3 alle Setzungen, nicht gemessen).
   - *DONE gegen ATTENTION:* der erste Übergang gewinnt ([SPEC-029](../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung), unverändert). Gewinnt das Ende der Antwort, ist die Antwort vollständig gesendet; das ATTENTION wird als eigene Nachricht mit einem einzelnen `DONE[ATTN]` bestätigt, ohne Cancel (zweiter Teil von [ADR-0027](0027-abbruch-und-zuruecksetzen.md) Entscheidung 1, unverändert). Gewinnt ATTENTION, gilt Nr. 1 und 2; die reguläre Antwort schreibt nicht mehr.
   - *Frist gegen ATTENTION:* gewinnt die Frist, endet die Anfrage mit ERROR 50005 und `DONE[ERROR]` ([ADR-0027](0027-abbruch-und-zuruecksetzen.md) Entscheidung 2, unverändert), ein folgendes ATTENTION ist „ATTENTION nach Ende der Antwort“ (ein einzelnes `DONE[ATTN]`); gewinnt ATTENTION, gilt die Zwei-Nachrichten-Form und es gibt keinen ERROR 50005.
   - *Wiederholtes ATTENTION:* löst keinen zweiten Cancel aus. Ein ATTENTION, das nach dem vollständig geschriebenen Paar gelesen wird, wird einzeln mit einem `DONE[ATTN]` bestätigt. Eines, das während `CANCELLING` eintrifft, wird erst nach dem Übergang nach `IDLE` verarbeitet und dann ebenso einzeln bestätigt; das Paar wird nicht verdoppelt.
   - *`CANCEL_TIMEOUT`:* beachtet das Backend den Cancel nicht, schließt die Verbindung ohne weitere Bytes ([SPEC-029](../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung), unverändert); ist die erste Nachricht noch nicht geschrieben, wird sie nicht mehr geschrieben.
   - *Disconnect* in `CANCELLING`: schreibt nichts mehr, auch nicht das Paar (unverändert).
5. **Folgen für die Regeln des Referenzabgleichs** (Erwartung, kein Beleg). Die vier Regeln A-13 und die zwei Regeln A-7 entfallen oder verkleinern sich, die Regeln `cancel-streaming/01/tokenfolge@2022` und `…/02/tokenfolge@2022` nur auf den Grund A-18. Ob A-7 mit A-13 entfällt, zeigt erst der Lauf gegen den gebauten Server; die Liste wird dann nach Messung angepasst, nicht vorher.

## Verglichene Alternativen

| Option | Pro | Contra |
|---|---|---|
| A — zwei Nachrichten wie der echte Server (gewählt, Nutzerentscheid 2026-11-18) | folgt der Messung (M1, M2); Spezifikation als Zielbild gleich dem echten Server; möglicherweise entfallen A-7 und A-13 | ändert eine Setzung des Nutzers vom 2026-11-01; mehr Zustand und Race-Fälle; Race-Setzungen nicht gemessen |
| B — Ein-Nachricht-Form bleibt, A-7 und A-13 sind „so gewollt“ | nichts zu bauen; für den Referenzclient tragfähig (A-7) | Abweichung vom echten Server bleibt dauerhaft in der Liste; andere Clients nicht gemessen |
| C — Zwei Nachrichten nur im Stream, bei wartendem Befehl eine | kleinerer Bau | der Fall des wartenden Befehls ist gemessen und weicht dann bewusst ab, ohne Grund |
| D — nichts festlegen | keine Vorfestlegung | der Bau wäre ohne tragende Entscheidung gegen den Wortlaut von ADR-0027 Entscheidung 1 |

## Konsequenzen

- Positiv: Der Bau hat eine Entscheidung, die der angenommenen ADR-0027 nicht widerspricht; die Form folgt der Messung; die Zwei-Nachrichten-Form der Ausnahmeliste hat ein Ziel statt eines „bedingten Aufschubs“.
- Negativ: Die Lesart von „doppelte DONE-Tokens“ (Nr. 3) und die Race-Fälle (Nr. 4) sind Setzungen; ein Client, der die Ein-Nachricht-Form erwartet, ist nicht gemessen (nur der Referenzclient nimmt beide Formen an, A-7).
- **Folgepflicht, Spec-Nachzug** (Träger: der Bau, der diese ADR umsetzt; diese ADR ändert `spec/` nicht): [SPEC-029](../../../spec/spezifikation.md#spec-029--attention-und-wiederverwendung-der-verbindung) (Cancel-Antwort ist das Paar, Kennzeichnung der Herkunft), [SPEC-001](../../../spec/spezifikation.md#spec-001--zustandsautomat-einer-verbindung) (Satz zu `CANCELLING` und zur Lesart der doppelten DONE-Tokens), [SPEC-045](../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) und das Messprotokoll (A-7, A-13), Kompatibilitätsmatrix Abschnitt 5 Nr. 12.
- **Akzeptierte Negative (einmalig, mit Grund):** (1) Der Befehl der ersten Nachricht beim wartenden Befehl ist in der Auswertung nicht genannt; der Bau liest ihn aus den Messdateien, ein Fehlgriff fällt im Abspielen des Mitschnitts auf (der Vergleich nennt jedes abweichende Feld). (2) Die Setzung zu einem ATTENTION während `CANCELLING` hat keinen Anlass am Referenzclient (M3: ein ATTENTION); ein Client, der es sendet, ist ein Re-Evaluierungs-Fall.

## Fitness Function (falls maschinell prüfbar)

| Tooling | Regel | Make-Target |
|---|---|---|
| JVM-/Protokolltests, Mitschnitt-Abspielen, .NET-Referenzclient | ATTENTION im Stream und bei wartendem Befehl ergibt zwei Nachrichten (`DONE[ERROR]` ohne ERROR-Token, `DONE[ATTN]`); Race DONE gegen ATTENTION und Frist gegen ATTENTION schreibt nie zwei Abschlüsse derselben Antwort; wiederholtes ATTENTION löst keinen zweiten Cancel aus. Noch keine bestandenen Tests. | — (Tests mit dem Bau); Abspielen: `make vergleichslauf-d` (kein Gate) |

## Offen (nicht entschieden)

- Andere Clients als der Referenzclient bleiben ohne Beleg für die Zwei-Nachrichten-Form; mssql-jdbc ist nicht gemessen.

## Re-Evaluierungs-Trigger

Ein Client mit der Ein-Nachricht-Erwartung scheitert an der Zwei-Nachrichten-Form; eine Messung am echten Server, die einer Setzung in Nr. 2 bis 4 widerspricht (insbesondere ein zweites ATTENTION je Anfrage oder ATTENTION im Leerlauf über einen anderen Weg als `Cancel()`).

## Geschichte

| Datum | Ereignis | Verweis |
|---|---|---|
| 2026-11-18 | Proposed; Option A auf Nutzerentscheid vom selben Tag (Code angleichen) | [ADR-0027](0027-abbruch-und-zuruecksetzen.md) |
| 2026-11-19 | Accepted; ausdrückliche Annahme durch den Nutzer, einschließlich der einzelnen Bestätigung eines wiederholten ATTENTION | — |

Nach `Accepted` wird diese Datei **nicht mehr inhaltlich überschrieben**.
Spätere Korrekturen oder Schärfungen entstehen als neue ADR mit
`Supersedes ADR-NNNN` (Baseline-Regelwerk `modul-04-adrs.md`
§Hard Rule für Accepted-ADRs).
