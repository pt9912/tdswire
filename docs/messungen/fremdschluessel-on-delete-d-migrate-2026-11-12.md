# Fremdschlüssel mit `on_delete: restrict` oder `no_action` bei d-migrate: Messprotokoll vom 2026-11-12

Messprotokoll zu [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität) und [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md) (`AGENTS.md` §5 Regel 7). Es klärt Fund F3 des Protokolls [abnahme-d-migrate-2026-11-12](abnahme-d-migrate-2026-11-12.md): ob der Exit 5 `POST_EXECUTE_DRIFT` bei einem Fremdschlüssel mit `restrict` oder `no_action` aus dem Verhalten von d-migrate oder aus einer Abweichung des Testservers folgt. Es hält fest, was gemessen wurde, nicht, ob eine Anforderung belegt ist.

## Ergebnis

**Art (d) des Abnahmeprotokolls, Verhalten des Clients d-migrate, keine Abweichung des Testservers.** Gemessen mit d-migrate 1.12.0, nur `on_delete`, SQL Server 2022, je Fall ein Lauf: der echte SQL Server 2022 und der Testserver liefern in allen sieben Läufen je Seite denselben Exit-Code, dieselbe Katalogzeile und dieselbe Ausgabe von `schema compare`. Der Katalog meldet für `ON DELETE NO ACTION` am echten Server `delete_referential_action_desc = NO_ACTION` (`delete_referential_action = 0`); d-migrate liest `NO_ACTION` als „kein `on_delete`“ und vergleicht das mit dem Soll `no_action` oder `restrict`, das es zugleich als `ON DELETE NO ACTION` sendet.

## Bedingungen

| Angabe | Wert |
|---|---|
| Datum, Rechner | 2026-11-12, `legion7i16`, 20 Kerne, 31 GiB, Linux 6.8.0-147, Docker 29.9.3; Lauf 11:37:59 bis 11:40:32 (153 s für alle 14 Läufe), natürliche Last, keine künstliche; Speicher beobachtet (12,5 bis 14,5 GiB verfügbar, Swap vorher belegt, nicht durch den Lauf) |
| Echter Server | `mcr.microsoft.com/mssql/server:2022-latest@sha256:cf8f0ab2ebe61feec2eed2629061a07d247f791c2a8ddda74a4ac9f06a291718` (Pin aus `referenztest-dotnet/Dockerfile`), `Microsoft SQL Server 2022 (RTM-CU27-GDR) (KB5126934) - 16.0.4305.5 (X64)`, `ACCEPT_EULA=Y` und `MSSQL_PID=Developer` (Zustimmung des Nutzers pt9912, [vergleichslauf-d.mk](../../tools/mk/vergleichslauf-d.mk)), nur lokal; ein Container, frische Datenbank `manuell` je Lauf (`DROP`/`CREATE`) mit den Tabellen `kunden` und `bestellungen` (Typen wie in der Beschreibung des Testservers) |
| Testserver | Arbeitsbaum des Worktrees auf `25e733e5` (Basis `main` `d348c1f2`), Image lokal gebaut (`make testserver-up`, Voreinstellungen, Benutzer `alice`), Neustart (`down`, `up`) je Lauf |
| Client | d-migrate 1.12.0, `ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491`, `--network host`, `--user "$(id -u):$(id -g)"`, `encrypt=false` |
| Mitschnitt | der mitschneidende Durchlauf `tools/jdbc-messung/Aufnahme.java` (ändert keine Bytes) zwischen d-migrate und dem jeweiligen Server; die Zeile der Katalogabfrage `FOREIGN_KEYS` und ihre Antwort stehen unten wörtlich im Auszug |
| Soll | `soll-elemente.yaml` der Anleitung ([Aufgabe 7](../user/testserver-manuell.md)), nur `auftraege.LieferantId.references.on_delete` wechselt: `cascade`, `no_action`, `restrict`, ohne Zeile |
| Ablage | Befehle, Ausgaben, Aufnahmen und Schlüsselwerte liegen im Scratchpad des Laufs (nicht im Repo); der Kennwort-Wert des echten Servers ist in den Aufnahmen durch `***` ersetzt |

Läufe: Reihe 1 (`s1`) legt `auftraege` mit dem Soll neu an (`schema migrate --execute`, danach `schema compare`). Reihe 2 (`s2`) legt es zuerst mit `cascade` an und ändert dann `on_delete` im Soll (migrate, compare, wie die Zusatzfälle des Abnahmeprotokolls).

## Tabelle

Spalten: Exit von `migrate` / `compare`; gelesene Zeile = Antwort auf die Abfrage `FOREIGN_KEYS` (Name, Spalte, Zieltabelle, Zielspalte, `delete_referential_action_desc`, `update_referential_action_desc`) nach dem letzten `migrate`. Beide Seiten stimmen in jeder Zeile überein.

| Reihe | Soll `on_delete` | gesendet (Auszug) | Exit echter Server | Exit Testserver | gelesene Zeile (echt = Testserver) | `compare` (echt = Testserver) |
|---|---|---|---|---|---|---|
| s1 | `cascade` | `CONSTRAINT [fk_auftraege_LieferantId] … ON DELETE CASCADE` | 0 / 0 | 0 / 0 | `fk_auftraege_LieferantId`, …, `CASCADE`, `NO_ACTION` | `IDENTICAL` |
| s1 | `no_action` | `… ON DELETE NO ACTION` | **5 / 1** | **5 / 1** | `fk_auftraege_LieferantId`, …, `NO_ACTION`, `NO_ACTION` | `DIFFERENT`: `_fk_LieferantId (… on_delete=no_action) -> fk_auftraege_LieferantId (…)` |
| s1 | `restrict` | `… ON DELETE NO ACTION` | **5 / 1** | **5 / 1** | wie `no_action` | `DIFFERENT`: `… on_delete=restrict) -> fk_auftraege_LieferantId (…)` |
| s1 | ohne | ohne `ON DELETE` | 0 / 0 | 0 / 0 | `NO_ACTION`, `NO_ACTION` | `IDENTICAL` |
| s2 | `cascade`, dann `no_action` | `DROP CONSTRAINT IF EXISTS [fk_auftraege_LieferantId]`, `ADD CONSTRAINT [_fk_LieferantId] … ON DELETE NO ACTION` | 0 / 0, dann **5 / 1** | 0 / 0, dann **5 / 1** | `_fk_LieferantId`, …, `NO_ACTION`, `NO_ACTION` | `DIFFERENT`: `_fk_LieferantId (… on_delete=no_action) -> _fk_LieferantId (…)` |
| s2 | `cascade`, dann `restrict` | wie zuvor | 0 / 0, dann **5 / 1** | 0 / 0, dann **5 / 1** | wie zuvor | `DIFFERENT`: `… on_delete=restrict) -> _fk_LieferantId (…)` |
| s2 | `cascade`, dann ohne | `ADD CONSTRAINT [_fk_LieferantId] … REFERENCES [lieferanten]([Id])` | 0 / 0, dann 0 / 0 | 0 / 0, dann 0 / 0 | `_fk_LieferantId`, …, `NO_ACTION`, `NO_ACTION` | `IDENTICAL` |

Der Katalog direkt (`sqlcmd`, am echten Server, nach jedem Lauf): `name|del|del_desc|upd|upd_desc` = `fk_auftraege_LieferantId|1|CASCADE|0|NO_ACTION` (Soll `cascade`), `…|0|NO_ACTION|0|NO_ACTION` (Soll `no_action`, `restrict`, ohne), in Reihe 2 mit dem Namen `_fk_LieferantId`. Die Spalte `delete_referential_action` kennt `restrict` nicht: SQL Server hat nur `NO ACTION`, `CASCADE`, `SET NULL`, `SET DEFAULT`; `restrict` ist im Soll von d-migrate ein eigener Wert, der am Server `NO ACTION` wird.

## Auszug der Aufnahme (Antwort auf `FOREIGN_KEYS`, Tabelle `auftraege`, Lauf s1 `no_action`)

Echter Server, wörtlich: `COLMETADATA[constraint_name:NVARCHAR(128)/flags=0x8, column_name:NVARCHAR(128)/flags=0x9, referenced_table:NVARCHAR(128)/flags=0x8, referenced_column:NVARCHAR(128)/flags=0x9, delete_referential_action_desc:NVARCHAR(60)/flags=0x9, update_referential_action_desc:NVARCHAR(60)/flags=0x9, constraint_column_id:INT4/flags=0x8] ORDER ROW*1 [('fk_auftraege_LieferantId', 'LieferantId', 'lieferanten', 'Id', 'NO_ACTION', 'NO_ACTION', 1)]`. Die Zeile des Testservers lautet in diesem Lauf `ROW*1 [('fk_auftraege_LieferantId', 'LieferantId', 'lieferanten', 'Id', 'NO_ACTION', 'NO_ACTION', 1)]` und in allen Läufen gleich der des echten Servers (Wert, Namen und Reihenfolge der Spalten; die Spaltenmetadaten wurden nur am echten Server im Wortlaut gelesen, ihre Gleichheit mit dem Testserver steht in den Tests der Bibliothek, nicht in dieser Messung).

## Einordnung

- **Zeile für Zeile am echten Server:** Exit 5 `Post-execute compare detected drift`, danach `compare` Exit 1 `DIFFERENT` in den vier Läufen mit `no_action` oder `restrict` (s1 und s2); Exit 0 und `IDENTICAL` in den Läufen mit `cascade` und ohne `on_delete`. Das ist das Ergebnis des Testservers, ohne Unterschied.
- **Ursache im Client, gemessen:** d-migrate sendet `ON DELETE NO ACTION` (auch für `restrict`), der Server meldet `NO_ACTION`, und der Reverse-Teil von d-migrate macht daraus kein `on_delete` (Soll ohne `on_delete` bleibt `IDENTICAL`, das Soll mit `no_action` nicht). Der Name ist nicht die Ursache: in Reihe 2 tragen Soll und Ziel denselben Namen `_fk_LieferantId` und der Unterschied bleibt allein `on_delete=…` gegen keinen Wert. Der Namenswechsel `fk_auftraege_LieferantId` zu `_fk_LieferantId` entsteht, weil d-migrate beim Ändern `DROP CONSTRAINT IF EXISTS [<bisheriger Name>]` und `ADD CONSTRAINT [_fk_<Spalte>]` sendet; das ist am echten Server gleich.
- **Quelltext von d-migrate nicht gelesen:** die Aussage „liest `NO_ACTION` als kein `on_delete`“ ist aus dem Verhalten (Eingabe gegen Ausgabe) geschlossen, nicht aus dem Quelltext.
- **Testserver:** `SimElementFields.kt` bildet `restrict` und `no_action` auf `NO_ACTION` ab und ohne Angabe ebenso (`testserver/kotlin/io/github/pt9912/tdswire/testserver/SimElementFields.kt` Zeilen 13 bis 17 und 56); die Bibliothek meldet es mit dem Katalog (`src/main/kotlin/io/github/pt9912/tdswire/simulation/CatalogForms.kt` Zeilen 128 bis 150). Das entspricht dem Katalog des echten Servers; eine Abweichung ist nicht gefunden, ein Folge-Slice für den Testserver nicht nötig.

## Vorschlag für den Planner (setzt der Planner, nicht dieser Lauf)

1. **Register** `BEO-TDS/katalog-elemente-grenzen-der-angleichung`, neuer Punkt 16: „**Fremdschlüssel mit `on_delete: restrict` oder `no_action` bei d-migrate:** keine Abweichung des Testservers. d-migrate sendet `ON DELETE NO ACTION`, der Katalog meldet `NO_ACTION` (echter Server und Testserver gleich, Messprotokoll `docs/messungen/fremdschluessel-on-delete-d-migrate-2026-11-12.md`, im Register als Link vom Ruheort zu schreiben), d-migrate liest es ohne `on_delete`; `schema migrate --execute` endet mit `POST_EXECUTE_DRIFT` und Exit 5, `compare` mit `DIFFERENT`. Dieselbe Art wie Punkt 3 (eindeutiger Index). Träger: Hinweis in der Anleitung.“ Alternativ eigene Beobachtung; sie hat keinen Träger im Code.
2. **Anleitung** `docs/user/testserver-manuell.md`, Aufgabe 7: als Grenze neben dem eindeutigen Index nennen: „Ein Fremdschlüssel mit `on_delete: restrict` oder `no_action` im Soll endet bei `schema migrate --execute` mit Exit 5 und `compare` mit `DIFFERENT`, am echten SQL Server 2022 ebenso (d-migrate 1.12.0, nur `on_delete`, je Fall ein Lauf): d-migrate liest `NO ACTION` ohne `on_delete`. Lösung im Soll: `on_delete` weglassen (Standard ist `NO ACTION`).“ Beleg dafür: Lauf s1 und s2 „ohne“ (`IDENTICAL`).
3. **Spezifikation:** keine neue Setzung nötig; die Angleichung an den Katalog stimmt (`NO_ACTION` für `restrict`, `no_action` und ohne Angabe). Falls die Spezifikation `restrict` als `NO_ACTION` führt, ist sie durch diese Messung am echten Server (nur d-migrate 1.12.0, SQL Server 2022 `16.0.4305.5`) gestützt.
4. **Abnahmeprotokoll:** Fund F3 von „(b), (c) offen“ auf Art „(d) Verhalten des Clients, am echten Server gleich“ stellen und auf dieses Protokoll verweisen (Abschnitt „Grenzen“ dort, „kein Lauf gegen einen echten SQL Server“, trifft für F3 nicht mehr zu).

## Grenzen dieses Protokolls

Einzelläufe auf einem Host, je Fall ein Lauf (keine Wiederholung); nur SQL Server 2022 (RTM-CU27-GDR 16.0.4305.5), nicht 2025; nur d-migrate 1.12.0 und mssql-jdbc aus diesem Image; nur `on_delete` (nicht `on_update`, `set_null`, `set_default`); nur die Tabelle `auftraege` mit einer Spalte im Schlüssel; Last natürlich (Load Average nicht gemessen). Die Spaltenmetadaten der Antwort (Typ, Länge) sind nur am echten Server im Wortlaut gelesen.
