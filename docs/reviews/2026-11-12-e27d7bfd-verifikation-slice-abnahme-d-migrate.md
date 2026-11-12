# Verifikationsbericht: slice-abnahme-d-migrate (HEAD e27d7bfd)

* Rolle: Verifier (Modul 11), frischer Kontext. Gegenstand: [slice-abnahme-d-migrate](../plan/planning/done/slice-abnahme-d-migrate.md). Maßstab: DoD §2, Plan §3, [TDS-FA-12](../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0025](../plan/adr/0025-produktschnitt-bibliothek-und-testserver.md), `SPEC-040`, Hard Rules `AGENTS.md` §3.1 bis §3.8.
* Stand: Zweig `worktree-agent-a21526b2fddb5451e`, HEAD `e27d7bfd`, Basis `main` `d348c1f2`; `git merge-tree --write-tree $(git rev-parse main) HEAD` liefert einen Baum (`d9200c8f`), konfliktfrei. Am Produktivcode nichts geändert. Haken in der DoD: keine gesetzt, und ich setze keine.
* Eingang: Review [2026-11-12-3a276fae-review-slice-abnahme-d-migrate.md](2026-11-12-3a276fae-review-slice-abnahme-d-migrate.md) (0 HIGH, 2 MEDIUM, 2 LOW, 3 INFO); die Nachbesserung F-1 bis F-5 im Commit `e27d7bfd` habe ich gegen den Text geprüft (alle fünf umgesetzt, siehe unten).

## Urteil

**Konform, mit Hinweisen.** Alle drei Liefer-Punkte sind belegt; ich habe jede Funktion des Solls selbst gefahren, die Exit-Codes weichen an keiner Stelle von den Protokollzeilen ab. Keine HIGH-, keine MEDIUM-Abweichung. Zwei LOW und zwei INFO (V-1 bis V-4), keiner blockiert die Closure. Offen sind die Planner-Aufgaben (Haken, Closure-Notiz, Ausgänge §6, Register Punkt 16, Paarungen).

## Eigene Läufe (Belege)

| Lauf | Ergebnis |
|---|---|
| `git diff --stat d348c1f2..HEAD` | 45 Dateien, nur `docs/`; kein `src/`, `testserver/`, `spec/`, `docs/plan/adr/`, `config/`, `tools/`, `Makefile`, `harness/`, `AGENTS.md`; kein `@Suppress`; keine Gate-Änderung |
| `make docs-check`, `make flaeche`, `make kopplung-zahlen` | Exit 0, 4 s (Cache) |
| `make gates` | Exit 0, 9 s (Stempel/Cache; Doku-Änderung, kein Frischbeleg nötig) |
| `make clean-build`, `make e2e`, Mutationen | nicht gefahren (Sensor-Zuschnitt, kein Code im Diff) |
| Anleitung nachgespielt (siehe unten) | alle Funktionen des Solls gelaufen, Exit-Codes gleich den Protokollzeilen |

## (2) Anleitung nachgespielt

Rig: Testserver-Image aus dem Arbeitsbaum (`tdswire-testserver:ver-abn`), Container `ver-abn1` (Port 16101), `ver-abn2` (16102), `ver-abn3` (16103, `REQUIRED`); d-migrate 1.12.0 per Digest `sha256:308f2775…6491`, `--network host`, `--user "$(id -u):$(id -g)"`; `probe` im Scratchpad statt im Repo. Rig-Fehler meinerseits (ein `make testserver-down` im falschen Verzeichnis) kein Befund. Alles nach dem Lauf entfernt (Container, mein Image); fremde Images und Volumes nicht angefasst.

| Funktion | Befehl (Kurzform) | Exit (meiner) | Protokoll | Zustand danach |
|---|---|---|---|---|
| Anmeldung unverschlüsselt | `schema reverse` | 0 | 0 | `ist.yaml`, `ist.report.yaml` |
| Anmeldung `encrypt=true` | `REQUIRED`, `bob`, `encrypt=true&trustServerCertificate=true` | 0 | 0 | Schema geschrieben; Log `tds-7.4:required`; ohne `trustServerCertificate` Exit 4, Log `tls_handshake_failed` |
| falsches Kennwort | `alice:falsch` | 4 | 4 | Log `login_rejected` |
| `schema compare` | Soll gegen Server | 1, dann 0 | 1, 0 | `DIFFERENT` (`produkte`, `column Email`), danach `IDENTICAL` |
| `schema migrate` ohne `--execute` | `soll-elemente.yaml` | 0 | nicht als eigene Zeile | gibt das DDL aus, ändert nichts |
| `schema migrate --execute` | `soll.yaml`, `soll-elemente.yaml` | 0 | 0 | `ok`, 2 beziehungsweise 3 Anweisungen, `transactionRolledBack` false |
| `data export` | `--tables kunden` | 0 | 0 | `[]`, danach Zeilen |
| `data import` Standardpfad | mit `Email`-Spalte ohne Neustart | 5 | 5 | `Source and destination schemas do not match.` |
| `data import` nach Neustart | | 0 | 0 | `2 inserted`, Export beide Zeilen |
| `data import` doppelter Schlüssel | | 5 | 5 | `Violation of PRIMARY KEY constraint…`, Export unverändert |
| `--on-conflict skip` | Id 2 geändert, Id 3 neu | 0 | 0 | `1 inserted`; Id 2 unverändert, Id 3 neu (Wiederholung: `0 inserted, 2 skipped`) |
| `--on-conflict update` | dieselbe Datei | 0 | 0 | `0 inserted, 2 updated`, Id 2 geändert |
| `data transfer` ohne gleiche Struktur | `--tables kunden` | 3 | 3 | `Preflight: Column 'kunden.Email' missing in target` |
| **Zusatz:** `schema migrate --source /work/soll.yaml --target 'db:…/manuell?encrypt=false' --execute` auf dem Ziel | Befehl aus dem neuen Absatz | 0 | (nicht gelaufen im Protokoll) | `ok`, 2 Anweisungen |
| `data transfer` Standardpfad | nach migrate | 0 | 0 | `Transferred: kunden`, `Transfer complete`, Ziel 3 Zeilen |
| `data transfer --on-conflict skip` / `update` | Id 3 geändert, Id 4 neu | 0 / 0 | 0 / 0 | skip: Id 4 neu, Id 3 unverändert; update: Id 3 geändert |
| `data transfer` `DECIMAL` | `bestellungen`, Standard und `update` | 0 / 0 | 0 / 0 | `"12.50"`, `"3.25"` |
| `data profile` (Negativ) | | 5 | 5 | `Profiling failed: Failed to list tables: Unsupported SQL.` |
| Aufgabe 7 Schritte 2 bis 5 | `compare` / `migrate --execute` / `compare` / `reverse` | 1, 0, 0, 0 | 1, 0, 0, 0 | `Tables removed: 2`, 3 Anweisungen, `IDENTICAL`; `default: 1`, `default: neu`, Index, `on_delete: cascade` als `foreign_key`-Eintrag, `expression: '"Menge">(0)'`, `unique_constraint` |
| F3: `restrict`, `no_action` (von `cascade`) | `migrate --execute`, `compare` | 5, 1 | 5, 1 | `POST_EXECUTE_DRIFT`; `compare`: `_fk_LieferantId (… on_delete=restrict|no_action) -> _fk_LieferantId (…)` |
| F3: `on_delete` weggelassen, `cascade` | | 0, 0 | 0, 0 | `IDENTICAL` |
| `make testserver-status` / `-logs` / `-down` | | 0 / 0 / 0; nach `down` Exit 2 | 0 / 0; 2 | Ereignisse wie im Protokoll |

Nicht von mir gefahren (wie im Protokoll „nicht gelaufen“ oder ohne Wirkung auf das Urteil): die Zusatzfälle der Tabelle D außer F3, die Rohbytes-Clients, Microsoft.Data.SqlClient, Host `0.0.0.0`, belegter Port, Datei über 1 MiB. Der Satz „Im Anfangsstand beider Container ist kein Migrationsschritt nötig“ ist nicht gefahren (ich habe nach dem Neustart zuerst `Email` angelegt).

## (1) DoD-Zeilen gegen Beleg

| DoD-Zeile | Beleg | Urteil |
|---|---|---|
| Lauf durch den Validator | [abnahme-d-migrate-2026-11-12](../messungen/abnahme-d-migrate-2026-11-12.md): Rechner, Last, Image-ID und Digest, Befehle, Exit-Codes, Auszüge, Funde F1 bis F4 | belegt; dass ein Agent des Typs `validator` lief, steht im Protokoll („Validator mit frischem Kontext“); ich kann es nicht an einer Erfassung prüfen |
| Beleg je Funktion | Tabelle A: jede Funktion des Solls mit Exit und Zustand danach; „nicht gelaufen“ steht ehrlich (Tabelle C, Grenzen) | belegt, durch meinen Lauf bestätigt |
| Anleitung nachgezogen | Neustart-Hinweis (F1), `data transfer`-Absatz mit Befehl (F2), Port-Hinweis (F4), F3 in Grenzen und Fehlertabelle; Bestand im Arbeitsspeicher, „ein Neustart leert den Bestand“ steht in Aufgabe 1, 6 und der Referenz | belegt; zu „Grenze zu JDBC“ siehe V-4 |

Register-Zuordnung (3): getroffen 9, 14, 15(a); nicht getroffen 10 bis 13 (13(a) nur in der Wirkung), 15(b), 15(c). Gegen die Punkte im Register gelesen: Punkt 9 (`DROP INDEX IF EXISTS`, Tabelle D Index ändern/umbenennen/entfernen Exit 0), 14 (Entfernen eines eindeutigen Index, Exit 5), 15(a) (Folge der Anweisungen bei Form D7, Standardwert neu/geändert/weggenommen Exit 0), 10 und 11 (d-migrate sendet nur `IF EXISTS` und den `DECLARE @df`-Text), 12 (kein Schlüssel einer referenzierten Tabelle entfernt), 15(b) (zweiter Standardwert an derselben Spalte nicht gefahren; „Standardwert geändert“ ist D7 mit `DROP`/`ADD`, kein zweiter Standardwert), 15(c) (kein `IMPLICIT_TRANSACTIONS`). Stimmig. Die Zuordnung „15(a) berührt“ ist eine Lesart (Tabelle D belegt Exit 0 und `IDENTICAL`, nicht die Zwischenabschlüsse); sie ist im Protokoll so begrenzt.

F3 in der Begrenzung (Art (d); 14 Läufe, nur SQL Server 2022, nur `on_delete`, je Fall ein Lauf):

* Messprotokoll [fremdschluessel-on-delete-d-migrate-2026-11-12](../messungen/fremdschluessel-on-delete-d-migrate-2026-11-12.md): vollständig (Ergebnis, Bedingungen, Grenzen). Die 14 Läufe sind „allen sieben Läufen je Seite“, Tabelle 7 Zeilen; Zeitraum 153 s stimmt mit 11:37:59 bis 11:40:32.
* Anleitung: „Der echte SQL Server 2022 … (gemessen mit d-migrate 1.12.0, nur `on_delete`, je Fall ein Lauf; … aus dem Verhalten geschlossen, nicht aus seinem Quelltext)“ im selben Satz.
* Abnahmeprotokoll Zeile F3: nennt „SQL Server 2022 liefert dasselbe“ und verweist auf das Messprotokoll, trägt aber „nur `on_delete`, je Fall ein Lauf, aus dem Verhalten geschlossen“ nicht in der Zeile, siehe V-1.

## (4) Plan-Tabelle gegen Diff

| Plan §3 | Diff | |
|---|---|---|
| `abnahme-d-migrate-2026-11-12.md`, neu | 127 Zeilen, neu | ok |
| `testserver-manuell.md`, update | 12 Zeilen | ok |
| `fremdschluessel-on-delete-d-migrate-2026-11-12.md`, neu | 59 Zeilen, neu | ok (Nachbesserung F-2 des Reviews) |
| `docs/plan/planning/` (Moves, Roadmap, Verweise), `docs/reviews/` | Moves `open`/`next` nach `in-progress`, Verweis-Nachzüge in `done/`, `observations/`, `welle-testserver-manuell.md`, `done/slice-zweites-error-folgefehler-angleichen.md` (nur der Pfad), Roadmap, Reviews, Review-Report | ok |
| optional `tools/` | nicht berührt | ok |

Darüber hinaus keine Datei. Der Slice-Kopf trägt `Verantwortlich: pt9912` (Commit `acd70a20`) und die Plan-Tabelle; §7 ist leer, Haken ungesetzt (Planner).

## (5) Anleitung, Hard Rules

* §5 Regel 5 / §3.7: die neuen Sätze nennen Zustand und Messung („gefahren am 2026-11-12“, dem bestehenden Muster der Datei folgend), keine Chronik der Entscheidung; Grep auf „bisher / früher / noch nicht / seit Slice“ im Diff ohne Treffer. `harness/README.md`, `AGENTS.md`, `spec/` nicht berührt.
* §3.8: Aussagen tragen ihre Bedingung (Version, Server, je ein Lauf), bis auf V-1.
* §3.1, §3.2, §3.3 (Moves rein, Inhalt in eigenen Commits), §3.4 (Architektur unberührt), §3.5 (keine ADR berührt), §3.6: ohne Befund.

## (6) Folge-Slice

`slice-zweites-error-folgefehler-angleichen` liegt in `open/`; im Diff nur der Pfad im Satz „Start“ (`../done/slice-abnahme-d-migrate.md`). Der Trigger (Abnahme trifft den zweiten Standardwert an derselben Spalte oder das zweite ERROR 3727) ist nicht ausgelöst: Register 10 und 15(b) stehen im Protokoll als „nein“; ich habe weder Standardwert zweimal an einer Spalte noch `DROP CONSTRAINT` ohne Treffer gesehen (d-migrate sendet `IF EXISTS`). Er bleibt offen und spätestens bei der Wellen-Closure fällig.

## Befunde

| ID | Kategorie | Befund | Pfad |
|---|---|---|---|
| V-1 | LOW | Fortschreibung lässt die zweite Fundstelle stehen (BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen): die Zeile F3 des Abnahmeprotokolls sagt „Der echte SQL Server 2022 liefert dasselbe (Exit 5): d-migrate liest `NO_ACTION` ohne `on_delete`“ ohne die Begrenzung (nur `on_delete`, je Fall ein Lauf, aus dem Verhalten geschlossen), die Messprotokoll und Anleitung im selben Satz tragen. Der Verweis auf das Messprotokoll steht in der Zeile, die Grenze nicht. | `docs/messungen/abnahme-d-migrate-2026-11-12.md` · Tabelle E, Zeile F3 |
| V-2 | LOW | Das Messprotokoll zu F3 führt unter „Vorschlag für den Planner“ Punkte, die der Diff schon erledigt hat: Vorschlag 2 (Anleitung, Aufgabe 7) und Vorschlag 4 (Abnahmeprotokoll F3 von „(b), (c) offen“ auf (d), „Grenzen dort“) sind umgesetzt; Vorschlag 1 (Register Punkt 16) ist es nicht. Ein Leser kann nicht sehen, welcher Vorschlag offen ist. Der Planner schließt das mit Punkt 16 und kann die Vorschläge 2 und 4 dann streichen oder als erledigt kennzeichnen. | `docs/messungen/fremdschluessel-on-delete-d-migrate-2026-11-12.md` · „Vorschlag für den Planner“ |
| V-3 | INFO | Der Absatz `data transfer` sagt „Im Anfangsstand beider Container ist kein Migrationsschritt nötig“; gefahren ist nur der Fall mit `Email` an der Quelle (Preflight Exit 3, nach `migrate` Exit 0), nicht der Anfangsstand allein. Die Aussage folgt aus dem Vorabtest (gleiche Spalten), ist aber nicht als Lauf belegt. | `docs/user/testserver-manuell.md` · „Im Anfangsstand beider Container“ |
| V-4 | INFO | DoD-Punkt 3 nennt „die Grenze zu JDBC“; die Anleitung trägt sie nur indirekt (mssql-jdbc als Treiber, `INSERT`/`DELETE` als SQL-Text abgewiesen, Massenladen). Ob das der Formulierung der DoD genügt, entscheidet der Planner; ich habe die Zeile als belegt gelesen, weil Bestand, Abweisung und Neustart-Verhalten in Aufgabe 6 und der Referenz stehen. | `docs/user/testserver-manuell.md` · Aufgabe 6 „Eigenschaften“, Referenz |

## Nachbesserung des Reviews (F-1 bis F-5) im Commit e27d7bfd

| Review | Stand |
|---|---|
| F-1 (Anleitung nennt „nicht gemessen“) | behoben: Satz mit Messung und Bedingung, Fehlertabelle ergänzt |
| F-2 (Plan-Tabelle ohne zweite Messdatei) | behoben: Zeile steht in §3 |
| F-3 (Begriffs-Kollision der Fund-Arten) | behoben: Art (d) mit Legende in Protokoll 1, Verweis auf (d) |
| F-4 (Schritt ohne Befehl) | behoben: Befehl mit `db:`-Präfix steht im Absatz; **von mir ausgeführt**, Exit 0 |
| F-5 (Bedingung im selben Satz) | behoben in der Anleitung; im Abnahmeprotokoll siehe V-1 |

## Negativbefunde

| Bereich | Ergebnis |
|---|---|
| Produktcode, Spec, ADR, `@Suppress`, Gate-Schwellen, `harness/README.md` | nicht im Diff |
| Exit-Codes der Protokolle gegen meine Läufe | keine Abweichung |
| Register-Zuordnung, Zahlen der Messprotokolle (7 Zeilen, 14 Läufe, 153 s) | ohne Befund |
| `slice-zweites-error-folgefehler-angleichen` | nicht ausgelöst |

## Summary

| Kategorie | Anzahl |
|---|---|
| HIGH | 0 |
| MEDIUM | 0 |
| LOW | 2 |
| INFO | 2 |

## Bedingungen für die Planner-Closure

1. Haken der drei Liefer-Punkte und der Nachweis-Pflichten setzen (`make gates` grün, Review, Verifikation liegen vor).
2. Register `katalog-elemente-grenzen-der-angleichung`: Punkt 16 (F3, Träger Hinweis in der Anleitung), Zeiger für 9, 14 und 15(a) fortschreiben (Stand: gebaut und mit d-migrate gemessen), 11 bleibt offen; der Link vom Ruheort aus schreiben.
3. Closure-Notiz §7 mit Steering-Loop-Eintrag, Ausgang je Risiko §6 (Validator-Lücken: F1, F2, F4 eingetreten; Image-Digest: Verfügbarkeit nicht gesichert; FLOAT: nicht getroffen), Folge-Slices (`slice-zweites-error-folgefehler-angleichen` bleibt offen).
4. V-1 und V-2 in die Closure-Nachführung nehmen (Begrenzung in die Zeile F3, erledigte Vorschläge kennzeichnen).
5. Die Welle `welle-testserver-manuell` schließt erst mit `slice-zweites-error-folgefehler-angleichen`; die Lifecycle-Bewegung nach `done/` erst nach der Notiz (`AGENTS.md` §3.3).

Gesamtlaufzeit rund 35 Minuten; größte Posten: Lesen von Slice, Protokollen, Register und Anleitung (etwa 12 Minuten), Nachspielen der Anleitung mit drei Containern (Läufe zusammen rund 3 Minuten, Schlüsselspeicher und Prüfwert im JDK-Image etwa 20 s), `make docs-check`/`flaeche`/`kopplung-zahlen`/`gates` zusammen 13 s.
