# Abschluss von Strukturänderungen und ENVCHANGE: d-migrate gegen den Testserver vor und nach dem Transaktionszustand, Messprotokoll vom 2026-11-08

Messprotokoll zu [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität) und [TDS-QA-07](../../spec/lastenheft.md#tds-qa-07--protokoll-synchronität) (`AGENTS.md` §5 Regel 7), Träger: [slice-ddl-abschluss-und-transaktionszustand-angleichen](../plan/planning/in-progress/slice-ddl-abschluss-und-transaktionszustand-angleichen.md), Bedingung aus [ADR-0042](../plan/adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) Entscheidung 8. Es hält fest, was gemessen wurde, nicht, ob eine Anforderung belegt ist. Es setzt das [Messprotokoll Katalog und DDL-Zustand](katalog-und-ddl-zustand-2026-11-06.md) fort: dieselbe Folge von Befehlen, dieselben Soll-Dateien, derselbe Client.

**Frage.** Ändert sich das Ergebnis von `schema migrate --execute`, `schema compare` und `schema reverse` gegen den Testserver, wenn der Server `ENVCHANGE` Typ 8 und 9 sendet ([SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität), Absatz zum Transaktionszustand)? Der Lauf **A** ist der Stand unmittelbar vor dem Bau von `ENVCHANGE`, der Lauf **B** der Stand mit `ENVCHANGE`; beide haben bereits den Abschluss ohne `COUNT` für `CREATE TABLE`, `ALTER TABLE … ADD` und die Transaktionstexte (Messprotokoll M-20 in [referenzabgleich](referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07)).

## Bedingungen

| Angabe | Wert |
|---|---|
| Datum | 2026-11-08, Läufe zwischen 17:10 und 17:45 |
| Host | Linux 6.8.0-147, 20 Kerne, 31 GiB, Docker 29.9.2; Load Average (1 Minute) laut `uptime` 9,0 um 17:43 (Entwicklungsläufe anderer) |
| Client | d-migrate 1.12.0, Image `ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491`, mssql-jdbc 13.4.0 (im Image) |
| Testserver A | Commit `7b478a2e` des Zweigs (Arbeitsbaum sauber), Image lokal gebaut mit `make testserver-up` (`TESTSERVER_IMAGE=tdswire-testserver:ddl-A`), Abbild `sha256:76157e5e8f57fd37cdf7ef5cf0339d4dd71e061261e724ce7fc58e5119d376c1`, `TEST_UNENCRYPTED`, Benutzer `alice`, Port 14341, Beschreibung `testserver/resources/manuell/sim.yaml` |
| Testserver B | Commit `279289f7` des Zweigs (Arbeitsbaum sauber), Abbild `sha256:e494c6e210b143302d292128fc668279f498ccf7ed9079d75eb2bfdec67a3e34`, sonst wie A, Port 14342 |
| Befehle | wie k1, k3, k4, k5, k6, k7, k9, k10, k11 des Protokolls vom 2026-11-06; frischer Server je Lauf (der Bestand wächst durch `schema migrate`) |
| Soll-Dateien | `sim.yaml` (die Beschreibung des Testservers), `soll2.yaml` (neue Tabelle `produkte`, neue Spalte `Email` an `kunden`), `soll3.yaml` (zusätzlich `notizen`) |

## Läufe

| Nr | Befehl (gekürzt) | Exit A | Exit B | Ausgabe A gleich B |
|---|---|---|---|---|
| k1 | `schema reverse` | 0 | 0 | ja |
| k3 | `schema compare` gegen das Ergebnis von k1 | 0 | 0 | ja (`IDENTICAL`) |
| k4 | `schema compare` gegen `sim.yaml` | 0 | 0 | ja (`IDENTICAL`) |
| k5 | `schema migrate soll2.yaml --execute` | 0 | 0 | ja (Bericht `status: ok`, `operationsTotal` 2, `statementsAttempted` 2, `transactionRolledBack` false) |
| k6 | `schema compare` gegen `soll2.yaml` | 0 | 0 | ja (`IDENTICAL`) |
| k7 | `schema reverse` nach der Änderung | 0 | 0 | ja |
| k9 | derselbe `schema migrate` noch einmal | 0 | 0 | ja (`status: no_op`) |
| k10 | `schema migrate soll3.yaml --execute` | 0 | 0 | ja (`status: ok`, `statementsAttempted` 1) |
| k11 | `schema compare` gegen `soll3.yaml` | 0 | 0 | ja (`IDENTICAL`) |

Verglichen sind die Ausgabe jedes Befehls, die beiden Schema-Dateien von `schema reverse` und die drei Berichte (Zeitstempel, Dauern, Kennungen und der Port der URL ersetzt; 14 Dateien, alle gleich). Die Dateien `up*.sql` aus `--output` entstanden in keinem der beiden Läufe und gehen nicht in den Vergleich ein.

**Mitschnitt der Antwort des Servers.** Ein Wegwerf-Proxy zwischen d-migrate und dem Server legte die Bytes Server nach Client ab; Lauf `schema migrate soll2.yaml --execute` (Exit 0 beide Male), gezählt sind Byte-Folgen im Strom:

| Folge | A | B |
|---|---|---|
| `DONE` `0x00b9` (`set implicit_transactions on`) | 1 | 1 |
| `DONE` `0x00c6` ohne `COUNT` (`CREATE TABLE`) | 1 | 1 |
| `DONE` `0x00d8` ohne `COUNT` (`ALTER TABLE … ADD`) | 1 | 1 |
| `DONE MORE` `0x00ba` (`set implicit_transactions off IF …`) | 1 | 1 |
| `ENVCHANGE` Typ 8 (`e3 0b 00 08 08 …`) | 0 | 1 |
| `ENVCHANGE` Typ 9 (`e3 0b 00 09 00 08 …`) | 0 | 1 |
| `DONE` `0x00d5` | 0 | 1 |

Der Treiber hat die Antwort von B mit `ENVCHANGE` angenommen; die Ergebnisse oben ändern sich nicht. Ob er den Deskriptor in den folgenden Anfragen zurücksendet, zeichnet dieser Lauf nicht auf (nur die Richtung Server nach Client); der Referenzclient sendet ihn zurück (M-20), und der Einbettungstest bedient eine Anfrage mit zurückgesendetem Deskriptor ([SPEC-031](../../spec/spezifikation.md#spec-031--streaming-fehler-und-atomarität)).

## Grenzen der Messung

- Einzelmessungen: ein Lauf je Stand, ein Host unter wechselnder Last, ein Digest des Clients, eine Treiberversion. Gemessen ist das Verhalten von d-migrate 1.12.0 und mssql-jdbc 13.4.0 gegen den **Testserver dieses Repos**, nicht gegen einen echten SQL Server; die Bytes des Servers selbst sind in M-20 am Referenzclient gemessen. Eine Aussage über andere Clients trägt dieses Protokoll nicht ([AGENTS.md](../../AGENTS.md) §3.8).
- Nicht gemessen: der Fehlerpfad (`IF @@TRANCOUNT > 0 ROLLBACK TRAN` bei offener Transaktion; die Bibliothek schreibt dazu kein `ENVCHANGE` Typ 10), `data import`, `data export` und `data transfer` mit offener Transaktion, mehr als zwei Verbindungen, TLS.
- Der Erzeuger der Läufe (zwei Shell-Skripte und der Proxy) liegt nicht im Repo; die Exit-Codes, die Berichtsfelder und die Zählungen sind die Aussage, die Dateien der Läufe liegen nicht ab. Die Wiederholung folgt dem Protokoll vom 2026-11-06 (Befehle und Soll-Dateien dort, Anleitung in [testserver-manuell](../user/testserver-manuell.md)).
- Die Läufe stehen nicht in `make gates` und nicht in `make e2e`.
