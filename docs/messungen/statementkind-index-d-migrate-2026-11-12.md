# `CREATE INDEX` und `DROP INDEX` mit eigener Anweisungsart: Messprotokoll vom 2026-11-12

Messprotokoll zu [TDS-FA-11](../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) und [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) (`AGENTS.md` §5 Regel 7), Träger: [slice-statementkind-index-mitglieder](../plan/planning/done/slice-statementkind-index-mitglieder.md). Es hält fest, was gemessen wurde, nicht, ob eine Anforderung belegt ist. Es setzt das [Messprotokoll zu `DROP INDEX`](drop-index-d-migrate-2026-11-11.md) fort: dort antwortet der Testserver auf `CREATE INDEX` und `DROP INDEX` mit `DONE` mit `COUNT` 0 und der Kennung 0; mit der Aufzählung `StatementKind` um `CREATE_INDEX` und `DROP_INDEX` antwortet er mit `DONE` ohne `COUNT` und den Kennungen `0x00c8` und `0x00c9`. Gemessen wird hier, ob d-migrate die Folge gegen den Testserver mit dem geänderten Abschluss ebenso fährt wie vorher.

## Bedingungen

| Angabe | Wert |
|---|---|
| Datum | 2026-11-12 |
| Host | Linux 6.8.0-147, 20 Kerne, 31 GiB, Docker 29.9.3; Load Average (1 Minute) laut `uptime` 3,8 vor und 6,9 nach den Läufen (Entwicklungsläufe anderer) |
| Client | d-migrate 1.12.0, Image `ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491` (mssql-jdbc 13.4.0), `--network host`, Verbindung `mssql://alice:secret@127.0.0.1:24971/manuell?encrypt=false` |
| Testserver | Arbeitsbaum des Slice `slice-statementkind-index-mitglieder` vor dem Commit der Quellen, Image lokal gebaut (`make testserver-up` mit `TESTSERVER_NAME=tdswire-skm`, `TESTSERVER_PORT=24971`, `TESTSERVER_IMAGE=tdswire-testserver:skm`, danach Container und Image entfernt), `TEST_UNENCRYPTED`, Benutzer `alice`, Beschreibung `testserver/resources/manuell/sim.yaml` (`kunden`, `bestellungen`) |

**Erzeuger der Zahlen.** Die Läufe sind von Hand mit `docker run` gefahren; die Soll-Dateien und das Skript, das die Befehle bündelte, liegen nicht im Repo (Scratchpad des Laufs). Die Soll-Dateien führen die Tabellen `kunden` und `bestellungen` (wie `sim.yaml`) sowie `lieferanten` (`Id` als Schlüssel, `Name` als `text` mit `max_length` 100) und `auftraege` (`Id` als Schlüssel, `Status` als `text` mit `max_length` 20 und `required`, `Menge` als `biginteger` und `required`), ohne Fremdschlüssel, Standardwert und Prüfbedingung. Sie unterscheiden sich im Index von `auftraege`: `a` hat `ix_auftraege_status` auf `Status`; `h` denselben Namen auf `Status, Menge`; `i` den Namen `ix_auftraege_neu` auf `Status, Menge`; `k` keinen Index. Befehle wie in [Aufgabe 7 der Anleitung](../user/testserver-manuell.md#7-fremdschlüssel-indizes-standardwerte-und-prüfbedingungen-anlegen-und-lesen). Die Bytes des Abschlusses prüft kein d-migrate-Lauf, sondern der Einbettungstest `StatementKindEmbeddingTest` und `ResponseStatementKindTest` (Handvektoren aus der Ablage `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/<stufe>/struktur-drop-index.messung`, Zeilen 03 bis 07). Zeiten wurden nicht erhoben.

## Läufe gegen den frisch gestarteten Testserver

| Schritt | Soll, Befehl | Exit | Ergebnis |
|---|---|---|---|
| t01 | `a`, `schema migrate --execute` | 0 | `statementsAttempted` 3: `CREATE TABLE` für `auftraege` und `lieferanten`, `CREATE INDEX [ix_auftraege_status] ON [auftraege] ([Status]);`; `schema compare`: `IDENTICAL` |
| t03 | `a`, zweiter Lauf | 0 | `statementsAttempted` 0 |
| t05 | `h`, Index mit anderen Spalten | 0 | `DROP INDEX IF EXISTS [ix_auftraege_status] ON [auftraege];` und `CREATE INDEX [ix_auftraege_status] ON [auftraege] ([Status], [Menge]);`, `statementsAttempted` 2; `schema compare`: `IDENTICAL` |
| t07 | `i`, Index umbenannt | 0 | `CREATE INDEX [ix_auftraege_neu] …`, dann `DROP INDEX IF EXISTS [ix_auftraege_status] ON [auftraege];`, `statementsAttempted` 2; `schema compare`: `IDENTICAL` |
| t09 | `k`, Index aus dem Soll entfernt (der Index ist nicht eindeutig) | 0 | `DROP INDEX IF EXISTS [ix_auftraege_neu] ON [auftraege];`, `statementsAttempted` 1; `schema compare`: `IDENTICAL` |

Das Log des Testservers enthielt am Ende keine Zeile `request_failed`.

## Befunde

- d-migrate 1.12.0 fährt `CREATE INDEX` und `DROP INDEX IF EXISTS` gegen den Testserver mit dem Abschluss ohne `COUNT` und den Kennungen `0x00c8` und `0x00c9` wie vorher mit dem Abschluss mit `COUNT` 0: jeder Lauf endet mit Exit 0 und `IDENTICAL`, kein Schritt meldet einen Fehler (Tabelle). Dass d-migrate die Befehlskennung dieser Abschlüsse nicht auswertet, ist daraus nicht belegt, nur dass die gefahrenen Schritte unverändert gelingen.
- Der Draht selbst ist durch diese Läufe nicht gemessen; er ist als Handvektor aus der Messung am echten Server (Ablage `struktur-drop-index.messung`) im Test belegt. „Zeile“ bezeichnet in den Messprotokollen zu dieser Ablage die Nummer der Anweisung in der Ablage (Anweisungen 3 bis 6: `CREATE INDEX`, Antwort `0x00c8`; Anweisung 7: `DROP INDEX IF EXISTS`, Antwort `0x00c9`), nicht eine Textzeile der Datei.
- **Nicht gemessen:** `CREATE INDEX` mit `INCLUDE`, Filter und absteigender Spalte (keine Form, die d-migrate hier sendet; `INCLUDE` meldet die Bibliothek mit derselben Art als Setzung); ENVCHANGE Typ 8 und 9 bei diesen Arten nach `set implicit_transactions on` (weder am echten Server gemessen noch gebaut); der Schritt mit einem eindeutigen Index (`unique: true`, Exit 5 wie im [Messprotokoll zu `DROP INDEX`](drop-index-d-migrate-2026-11-11.md), Schritte t10 bis t14) wurde nicht wiederholt; d-migrate gegen den echten Server mit diesen Schritten.
