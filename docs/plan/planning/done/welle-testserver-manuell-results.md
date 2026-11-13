# Welle welle-testserver-manuell — Testserver von Hand starten und mit d-migrate als fremdem Client benutzen — Closure-Notiz

> **Zitier-Form** *(bleibt stehen — Norm, kein Ausfüll-Hinweis).* Dieses
> Artefakt friert ein; was es zitiert, bewegt sich weiter. Deshalb: **Kennung,
> nicht Adresse** — `slice-<Kennung>` statt seines Lifecycle-Pfads, `make <target>`
> statt eines Links auf die Sensor-Datei, eine Baseline-Stelle als
> `v<X.Y.Z>` · `regelwerk/<datei>.md` §<Abschnitt> statt als Link
> (Baseline-Regelwerk `grundlagen-harness-dateien.md` §harness/README.md als
> Einstiegspunkt — beim Ausfüllen mit dem adoptierten Tag schreiben).

**Welle:** welle-testserver-manuell
**Abschluss:** 2026-11-13
**Verantwortlich:** pt9912

## Was wurde geliefert?

Regeln dieser Sektion: Baseline-Regelwerk `v6.18.1` · `regelwerk/modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — *was gelernt wurde*: geliefert · was
funktionierte · was anders lief. Mit ID-Bezug, wo es einen gibt.

Alle 33 Slices mit `Welle: welle-testserver-manuell` liegen in `done/` (am Dateisystem geprüft: kein Slice der Welle in `open/`, `next/` oder `in-progress/`; jeder in §4 und §5 der Welle-Datei genannte Slice der Welle besteht dort). Das *Mehr* gegenüber den Slice-DoDs, Closure-Trigger 3 der Welle-Datei, trägt `slice-abnahme-d-migrate`: ein Validator in frischem Kontext ohne Planwissen fuhr die Funktionen des Solls nach der Anleitung gegen den Testserver (Messprotokoll `abnahme-d-migrate-2026-11-12`).

- **Start und Stop von Hand:** `slice-testserver-up-down` (Targets `testserver-up`, `-down`, `-logs`, `-status`, Test-Image, Selbsttest) und `slice-testserver-manuell-doku` (Anleitung `docs/user/testserver-manuell.md`); [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md).
- **Verbindung, Anmeldung und Export (Hürden 1 bis 5 und 8):** `slice-prelogin-serverversion`, `slice-d-migrate-verbindung-und-export`, `slice-tls-d-migrate`; `encrypt=true` mit dem Treiber-Standard.
- **Verteilung Bibliothek und Testserver:** `slice-testserver-gradle-modul` ([ADR-0034](../../adr/0034-testserver-als-gradle-modul.md)), `slice-adr-testserver-bestand-und-katalog`, `slice-katalog-und-ddl-zustand`: Katalog aus den mitgeteilten Tabellen, Weitergabe von Strukturänderungen an den Integrator ([ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)); [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate).
- **Schreibpfad:** `slice-schreibpfad-bulk-load`, `slice-schreibpfad-merge-output`, `slice-bulk-load-feste-typen-und-referenzclient`, `slice-massenladen-atomar-bauen` (mit `slice-adr-massenladen-atomar-an-messung`, [ADR-0044](../../adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md)), `slice-bulkcopy-tabellenname-mit-schema-und-sonderzeichen`.
- **Handles:** `slice-adr-handle-grenze-ersatz`, `slice-handle-freigabe-und-grenze-bauen` ([ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md)), `slice-sp-unprepare-ohne-obergrenze`, `slice-adr-sp-execute-unbekanntes-handle` und `slice-sp-execute-unbekanntes-handle-bauen` ([ADR-0037](../../adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md), [ADR-0045](../../adr/0045-sp-execute-unbekanntes-handle-ergibt-fehler-8179-state-4.md)).
- **Angleichung an den echten SQL Server (Nutzermaßstab: die Spezifikation ist Zielbild, der echte Server die Referenz):** `slice-qa-vergleichslauf-d-messungen-bulk-merge` (Messung), `slice-katalog-und-struktur-an-messung-angleichen`, `slice-katalogspalten-smallint-tinyint-angleichen`, `slice-antwort-tokens-zielbild-luecken-bauen`, `slice-ddl-abschluss-und-transaktionszustand-angleichen`, `slice-katalog-indizes-defaults-fremdschluessel`, `slice-merge-antwort-an-messung-angleichen`, `slice-unbekannte-tabelle-class-16-angleichen`, die Strukturformen D5, D6 und D7 (`slice-drop-constraint-form-d5`, `slice-drop-index-if-exists`, `slice-declare-df-standardwert-vorhandene-spalte`), `slice-statementkind-index-mitglieder` und `slice-zweites-error-folgefehler-angleichen` (1781, 1750 und das zweite 3727 am Draht gemessen); [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität).
- **Abnahme:** `slice-abnahme-d-migrate`. Belegt sind, jede mit Exit-Code und Zustand danach: Anmeldung mit und ohne `encrypt=true`, falsches Kennwort (Exit 4), `schema reverse`, `schema compare`, `schema migrate` mit `--execute`, `data export`, `data import` (Standardpfad, doppelter Schlüssel, `--on-conflict skip` und `update`), `data transfer` (Standardpfad, `skip`, `update`, `DECIMAL`), `data profile` als Negativfall (Exit 5, nicht im Soll), Neustart setzt zurück, `testserver-up`, `-status`, `-logs`, `-down`. Bedingungen aus dem Protokoll: Rechner `legion7i16` (Intel Core i9-13900H, 20 Kerne, 31 GiB, Linux 6.8.0-147, Docker 29.9.3), natürliche Last 3 bis 4,5 (Load Average 1 Minute), Testserver-Image lokal gebaut `sha256:8906932bcf88d86563e5cb01f39802e00b7b34cebbd7aa9d0761b7c328b3ffc4` (Arbeitsbaum auf `acd70a20`), Client d-migrate 1.12.0 `ghcr.io/pt9912/d-migrate@sha256:308f27753036c9cc423f716b9fc4c9e68016543a8003ecd2f8282e05a2ee6491` mit mssql-jdbc 13.4.0.

**Grenzen dieser Aussage (nicht breiter als der Beleg, `AGENTS.md` §3.8).**

1. Der Validator-Lauf stand auf `acd70a20`. Danach wurde noch Code geändert (14 Dateien: das zweite ERROR 1781, 1750, 3727, `ResponseSequencer`, `ErrorCode`, `StatementPlanner`, Tests). Den Validator-Lauf mit allen Funktionen des Solls hat niemand auf dem End-Stand wiederholt; auf `c84d5579` (End-Stand des Codes) fuhr der Verifier von `slice-zweites-error-folgefehler-angleichen` `make clean-build` (Exit 0, 572 s), `make e2e` (Exit 0, acht Stufen, 392 s) und fünf d-migrate-Schritte (Exit 0, `IDENTICAL`). Seitdem ist `src/`, `testserver/`, `tools/` und das `Dockerfile` unverändert (Diff gegen `c84d5579` leer; geprüft in dieser Closure).
2. Das Kriterium nennt einen „frischen Klon“; der Lauf stand in einem Worktree auf `acd70a20` (Arbeitsbaum, Image lokal gebaut). Dass nach `make testserver-down` „weder Container noch Netzwerk noch Port belegt“ sind, belegt das Protokoll mit `testserver-status` (Exit 2, „nicht gestartet“) und dem zweiten `down` (kein Container, nichts zu tun); eine eigene Prüfung des Netzwerks und des Ports steht nicht im Protokoll. Nicht gelaufen sind laut Protokoll: belegter Port, Host `0.0.0.0`, Datei über 1 MiB, `LOG_LEVEL=DEBUG`, Zugriff von einem anderen Rechner, Microsoft.Data.SqlClient am Handlauf.
3. Der Verfügbarkeit des d-migrate-Images (beweglicher Tag) ist nicht gesichert; der Digest steht im Protokoll (Risiko 2 von `slice-abnahme-d-migrate`, weiter offen).
4. Das Soll gilt für d-migrate 1.12.0 mit mssql-jdbc 13.4.0; andere Versionen und Clients sind keine Zusage ([TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) Out-of-Scope, §6 der Welle-Datei).

**Zielbild-Entscheide des Nutzers in dieser Welle (die Spezifikation ändert sich dafür, nicht still):** 2026-11-12 die Antwortfolge der Form D7 und `ENVCHANGE` Typ 8 unter `IMPLICIT_TRANSACTIONS` als Grenzen der Zusage (`SPEC-036` Form D7 (a), `SPEC-031`); 2026-11-13 die Namen von Standardwert und Schlüssel als Grenze der Zusage (Weg 2, `SPEC-036` Form D5 und Form D6 (c), Änderungsprotokoll; die Bibliothek führt die Namen nicht, [ADR-0046](../../adr/0046-oeffentliche-flaeche-der-katalog-elemente.md) bleibt unverändert, Option A). Das Lastenheft ist durch keinen der Entscheide berührt.

## Was hat funktioniert?

Regeln dieser Sektion: Baseline-Regelwerk `v6.18.1` · `regelwerk/modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3.

- **Messung vor Bau, Ablage der Antwort im Repo.** Die Angleichungs-Slices lasen Nummern, States, Classes und Tokenfolgen aus den Ablagen der Messung am echten Server (2022 und 2025) statt aus dem Gedächtnis; der Verifier dekodierte die Rohbytes mit eigenem Dekoder und fand sie gleich den Handvektoren. Das ließ die Aussagen an der Messung hängen und machte `AGENTS.md` §3.8 prüfbar.
- **Ein Validator ohne Planwissen.** Er fand vier Funde, die weder die Slices noch der Review sahen (Neustart nach Aufgabe 3, `data transfer` ohne Schritte, fester Port in den Zeichenfolgen, Fremdschlüssel mit `restrict`); kein einziger war ein Fehler des Servers. Die Anleitung ist danach eine, die ein Fremder nachspielen kann.
- **Ein Fund der Abnahme wurde vor der Behebung am echten Server gemessen.** F3 (Exit 5 bei `on_delete: restrict`) sah wie eine Abweichung des Testservers aus und war Verhalten des Clients, am echten SQL Server 2022 gleich; die Messung ersparte einen Folge-Slice (Beobachtung `BEO-TDS/fund-der-abnahme-vor-der-behebung-am-echten-server-messen`, 1×).
- **Grenzen werden entschieden und mit Trigger geführt statt als „nicht gebaut“ stehen gelassen.** Die Punkte 9, 10, 13 bis 15 und 11 des Registers `BEO-TDS/katalog-elemente-grenzen-der-angleichung` haben je Träger oder Trigger.

## Was ging anders als geplant?

Regeln dieser Sektion: Baseline-Regelwerk `v6.18.1` · `regelwerk/modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — jede Zeile möglichst mit der Konsequenz,
die daraus schon gezogen wurde (Folge-Slice, Spec-Version).

- **Die Welle wuchs von zwei Slices (Start und Stop von Hand) auf 33.** Der Umschnitt vom 2026-11-05 („Die Welle können wir erst schließen, wenn alles funktioniert“) machte d-migrate als fremden Client zum Beleg; die Messungen am echten Server (2026-11-06 bis 2026-11-12) lieferten Folge-Slices, die vorher nicht planbar waren (zwölf Slices der Angleichung, drei Strukturformen). Konsequenz: das Drift-Log der Roadmap trägt die Aufnahmen (*Historische Trigger-Verschiebungen*).
- **Die Sonde sah erfundene Katalogantworten laufen.** „Läuft“ der Sonde vom 2026-11-05 war kein Beleg für einen Server; erst die Abnahme gegen den Testserver selbst ist einer (so in §1 der Welle-Datei vorbehalten). Konsequenz: `slice-abnahme-d-migrate` als Bedingung des *Mehrs*.
- **Der Punkt „Fortschreibung lässt die zweite Fundstelle stehen“ trat in dieser Welle 16-mal auf (Dateien in `evidence/`, deren Slice `Welle: welle-testserver-manuell` trägt), obwohl die Prüfung im Reviewer-Skill seit 11 Dateien steht.** Der Reviewer fing sie jedes Mal; die Seite des Implementers hatte keinen Suchschritt. Konsequenz: Steering-Loop-Eintrag 1.
- **Der Punkt 11 des Registers (Namen von Standardwert und Schlüssel) blieb nach der Abnahme offen** und brauchte eine Entscheidung des Nutzers; entschieden am 2026-11-13 (Weg 2). Konsequenz: Spezifikation, Nutzer-Doku und Register nachgezogen (Commit vor dieser Closure).
- **Der Validator-Lauf steht nicht auf dem End-Stand des Codes** (Grenze 1 oben): `slice-zweites-error-folgefehler-angleichen` war laut Planung keine Voraussetzung der Abnahme (Trigger: die Abnahme trifft den Fall) und schloss danach. Er hat die Abnahme nicht ausgelöst, sie trat nicht ein.

## Trigger-Audit der Welle (Schritt 2)

Regeln dieser Sektion: `close-welle` Schritt 2 — je Klasse eine belegte Feststellung.

- **Carveouts:** 0 offen. `docs/plan/carveouts/` führt nur `done/` (CO-001 bis CO-004); kein Slice der Welle hat einen Carveout eröffnet oder verlängert. Kein Gate steht rot in `done/`.
- **Bootstrap-aware Gates:** 0 offen. Die Sensors-Tabelle in `harness/README.md` führt kein Gate mit Reifestufe; die Zeilen mit `bootstrap-aware` sind Vorlagenreste.
- **Entscheidungen (Re-Evaluierungs-Trigger der ADRs, die die Slices der Welle tragen).** Maßstab: Kenntnisstand dieser Closure aus den Closure-Notizen der Slices und dem Messprotokoll der Abnahme; kein Trigger wurde eigens neu gemessen.

| ADR | Trigger | Befund |
|---|---|---|
| [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) | Lauf von d-migrate weicht von b3, b5 bis b7, c5 ab; Atomarität des Massenladens; `execute` als Engpass; FLOAT im Soll; öffentlicher Name über die Liste hinaus | nicht eingetreten: die Abnahme fand keine Abweichung des Servers von einer Zusage, FLOAT kam in keiner Funktion des Soll vor (Risiko 3 der Abnahme entfallen); `CREATE_INDEX` und `DROP_INDEX` sind Mitglieder, die der Nutzer am 2026-11-12 ohne Folge-ADR entschied; Atomarität des Massenladens ist durch [ADR-0044](../../adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md) bearbeitet |
| [ADR-0034](../../adr/0034-testserver-als-gradle-modul.md) | M1 rot; M4 Zyklus; Gate-Zeit wächst; Veröffentlichung; Auseinanderlaufen von Embedder- und Testserver-Pfad | M1: Modulgrenze-Sperre in `make detekt` grün; M4 nicht neu gefahren; Gate-Zeit: `make gates` mit Stempel 12 s, das Maß des Nutzers ist nicht genannt; Veröffentlichung: nicht eingetreten |
| [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md) | d-migrate schließt oder verwendet nichts wieder; echter Server weicht bei `sp_prepexec` oder `sp_unprepare` ab; andere mssql-jdbc-Version | nicht eingetreten: kein Fund der Abnahme betrifft Handles; mssql-jdbc 13.4.0 |
| [ADR-0037](../../adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md) und [ADR-0045](../../adr/0045-sp-execute-unbekanntes-handle-ergibt-fehler-8179-state-4.md) | Messung mit anderer Version oder anderem Image-Digest weicht ab; ein Client scheitert an 8179 | nicht eingetreten (Kenntnisstand) |
| [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) | `smallint`/`tinyint` als Eingang; FLOAT; Sichten brauchen `nvarchar(max)` oder `sql_variant` | nicht eingetreten (Kenntnisstand) |
| [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md) | Messung zeigt `ENVCHANGE` 8 bei weiteren Anweisungen | **eingetreten, bearbeitet:** die Messung zur Form D7 zeigt `ENVCHANGE` Typ 8 bei dem Text mit `DECLARE @df`; Entscheid des Nutzers 2026-11-12: Grenze der Zusage (`SPEC-031`), Trigger ein Client mit `IMPLICIT_TRANSACTIONS` und Strukturanweisungen; kein neues Mitglied von `StatementKind` |
| [ADR-0044](../../adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md) | Teilbestand nach Fehler gemessen; d-migrate sendet andere Nachrichtenfolge; Integrator braucht den Schlüsselverstoß unterschieden | nicht eingetreten: `data import` und `data transfer` der Abnahme liefen mit dem Standardpfad ohne Fund |
| [ADR-0046](../../adr/0046-oeffentliche-flaeche-der-katalog-elemente.md) | Setzung weicht am echten Server ab und ändert die Form der Elemente; Namen je Schema gemessen; Integrator meldet Bruch von `onStructure`; erste Veröffentlichung; Fläche wächst | nicht eingetreten; Punkt 11 des Registers (Namen von Standardwert und Schlüssel) führte nicht zur Erweiterung der Fläche, Entscheid des Nutzers 2026-11-13 (Option A bleibt); Namen je Schema bleiben ungemessen (Punkt 5 des Registers, Setzung) |
| [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md) | (Aufnahme von d-migrate und JDBC ins Clientprofil) | offen, Entscheidung des Nutzers: Register `BEO-TDS/festlegung-gilt-nur-fuer-das-clientprofil` (3×, Frage 1 unten) |

Keine Entscheidung steht mit eingetretener, unbearbeiteter Bedingung da.

## Steering-Loop-Einträge

Regeln dieser Sektion: Baseline-Regelwerk `v6.18.1` · `regelwerk/modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 (hier stehen **nur** Beobachtungen, die im
Register 3× erreicht haben; jeder Eintrag nennt seine `BEO-<KUERZEL>/<slug>`) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

**Lese-Schritt des Registers.** `docs/plan/planning/observations/BEO-TDS/` führt 166 Verzeichnisse; 43 haben mindestens 3 Dateien in `evidence/` (der Zähler ist die Zahl der Dateien; die `Stand`-Zeilen mancher Einträge zählen anders, siehe Frage 3 unten). Davon tragen 24 einen Ausgang (17 `verkörpert`, 2 `geplant`, 2 `Träger entschieden`, je 1 `gestrichen`, `gebaut`, `geliefert`) aus früheren Closures oder Slice-Entscheiden; 19 stehen `offen` (18) oder `teilweise gemessen` (1) über der Schwelle oder als Sammeleintrag (Liste unten). Diese Closure verkörpert **einen** Eintrag.

- **Implementer-Anweisung** geschärft: vor dem Handoff jede berichtigte, abgelöste oder neu entschiedene Aussage per Grep über `spec/`, `docs/user/` (alle Seiten), `docs/plan/`, `harness/`, `tools/` und Kommentare suchen, die Suchbegriffe im Bericht nennen, jeden Treffer in die Änderungsliste nehmen oder begründen (neuer Schritt 20c; die Reviewer-Prüfung gleichen Namens bleibt als zweite Seite) — liegt in `.claude/commands/implement-slice.md`.
  Auslöser: `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (38 Dateien in `evidence/`; 11 bei der ersten Verkörperung im Reviewer-Skill, 27 danach, davon 16 in Slices dieser Welle, zuletzt `slice-abnahme-d-migrate` (V-1), `slice-zweites-error-folgefehler-angleichen` (F-1), `slice-statementkind-index-mitglieder` und `slice-declare-df-standardwert-vorhandene-spalte`). Der Befund: die Prüfung auf der Seite des Reviewers fängt, der Implementer erzeugt weiter; `slice-zweites-error-folgefehler-angleichen` §7 benannte „es fehlt weiter der Wächter auf der Seite des Implementers (Suchschritt)“ ausdrücklich. Zielort besteht, Schwelle weit überschritten. Grenze: eine Regel, kein Sensor; ob der Schritt die Zahl der Funde senkt, zeigt das Register an den folgenden Slices (Retirement-Check: „seit welle-testserver-manuell — ist die Beobachtung wieder aufgetreten?“).

**Gezählt, nicht verkörpert (kein `liegt in`).**

- `BEO-TDS/aussagen-ueber-messung-hinaus` (81 Dateien, 13 aus dieser Welle) ist verkörpert (`AGENTS.md` §3.8, Reviewer-Skill, seit welle-protokollcodec); die Regel wird in jeder Welle wieder gefunden und nicht gesenkt. Keine weitere Schärfung beschlossen; der neue Schritt 20c schneidet die Klasse „zweite Fundstelle“ ab, nicht die Klasse „breiter als die Messung“.
- `BEO-TDS/fund-der-abnahme-vor-der-behebung-am-echten-server-messen`: 1×, unter der Schwelle.
- Folgende Einträge hatten nach Dateizähler bereits vor dieser Welle 3× oder mehr, stehen `offen` und warten laut ihrem `Stand` auf eine Entscheidung des Nutzers (Regel, Sensor, Träger-Slice oder Streichung); diese Closure liest sie, ändert weder Regel noch Gate und lässt den Stand `offen`: `kommentar-nennt-abwesenden-oder-unwirklichen-zustand` (10 Dateien), `festlegung-gilt-nur-fuer-das-clientprofil` (3), `oeffentliche-flaeche-mitglieder-ohne-sensor` (3), `lesart-einer-nutzerentscheidung-unbestaetigt` (16), `wanduhr-tests-flackern-unter-last` (12), `tds-8-0-und-pooling-nicht-gemessen` (20, Träger offen), `zusage-ohne-sensor-in-der-ci-kette` (6), `spec-einzelfaelle-ohne-lastenheft-deckung` (4), `geheimnis-kopien-im-speicher-nicht-gemessen` (3), `test-naht-im-produktivcode` (3), `schranken-test-mit-ungeprueftem-zaehler` (3), `live-lauf-ausserhalb-der-gates-haelt-kopplungen-nicht` (3), `flake-fix-ohne-reproduzierten-fehler` (3), `eingangsliste-je-stage-von-hand` (3), `dod-haengt-an-offenem-slice-ohne-rueckfuehrung` (3), `zahl-an-mehreren-orten-ohne-sensor` (6). Weitere offene Einträge über der Schwelle mit Träger oder Teilausgang: `messung-am-echten-sql-server-fehlt` (36, teilweise gemessen), `katalog-elemente-grenzen-der-angleichung` (5, Sammeleintrag, Träger je Punkt; Punkt 11 entschieden, die Punkte 4 bis 8 Setzung, 12 und 13 bis 15 mit Trigger) und `parametertyp-des-clients-passt-nicht-zur-spalte` (3, `Stand` zählt 1×, Träger genannt).
- Drei dieser Einträge nennen diese Closure als ihren Lese-Schritt (`kommentar-nennt-abwesenden-oder-unwirklichen-zustand`, `festlegung-gilt-nur-fuer-das-clientprofil`, `oeffentliche-flaeche-mitglieder-ohne-sensor`). Ihnen fehlt der Ausgang, den nur der Nutzer setzt (Fragen 1 und 2 unten); nichts wurde geraten.
- **Nachtrag zu einer früheren Verkörperung (Zeiger):** `BEO-TDS/spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar` (4 Dateien, davon drei aus dieser Welle: `slice-adr-handle-grenze-ersatz`, `slice-adr-testserver-bestand-und-katalog`, `slice-katalog-und-ddl-zustand`) ist verkörpert in `.harness/skills/reviewer.md`, `.claude/commands/plan-welle.md` und `.claude/commands/implement-slice.md` (Nutzerentscheid 2026-11-06); dieser Zeiger erfüllt den Auftrag, den die Beobachtung der Closure dieser Welle zuweist (ihr Stand nennt, der Eintrag mit `liegt in` gehöre in die `-results.md` der Wellen, die den Lese-Schritt tragen). Geprüft in dieser Closure: die drei Ziele (`.harness/skills/reviewer.md`, `.claude/commands/plan-welle.md` Schritt 6d, `.claude/commands/implement-slice.md` Schritt 20a) bestehen und tragen den Anker `seit slice-katalog-und-ddl-zustand`; ein neues Feld `liegt in` entsteht hier nicht.

**Benannte Spec-Lücke:** keine neue. Die Lücke „Namen von Standardwert und Schlüssel“ ist als Grenze der Zusage aufgelöst (`SPEC-036`, Änderungsprotokoll 2026-11-13, Entscheid des Nutzers).

## Beobachtungs-Register (Zeiger)

Regeln dieser Sektion: Baseline-Regelwerk `v6.18.1` · `regelwerk/modul-06-roadmap.md`
§Das Beobachtungs-Register — der Zähler wird **nicht** hier gepflegt; diese
Sektion ist ein Zeiger und trägt keine Daten.

Der Zähler steht in [`../observations/`](../observations/).
Was in dieser Welle **3×** erreicht hat, steht oben unter
*Steering-Loop-Einträge*. Fortgeschrieben bei dieser Closure: `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (Ausgang um den Schritt 20c ergänzt), `BEO-TDS/katalog-elemente-grenzen-der-angleichung` (Punkt 11 entschieden), `BEO-TDS/kommentar-nennt-abwesenden-oder-unwirklichen-zustand`, `BEO-TDS/festlegung-gilt-nur-fuer-das-clientprofil`, `BEO-TDS/oeffentliche-flaeche-mitglieder-ohne-sensor` (Lese-Schritt dieser Closure gelesen, Stand `offen`).

## Folge-Slices

Regeln dieser Sektion: Baseline-Regelwerk `v6.18.1` · `regelwerk/modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 3 — **derivativ**: Diese Liste zeigt nur,
das Original ist die Slice-Datei. Jeder genannte Folge-Slice muss als Datei im
Planning-Lifecycle existieren.

- `slice-qa-performance` (Last, Durchsatz und Dauerbetrieb; §6 der Welle-Datei schloss es aus, Datei in `open/`) — Träger der Out-of-Scope-Grenze „Last“.
- Kein neuer Slice aus dieser Welle: die offenen Grenzen des Katalogs (Register `BEO-TDS/katalog-elemente-grenzen-der-angleichung`, Punkte 4 bis 8 Setzung, 12 sowie 13 bis 15 mit Trigger) tragen einen Trigger statt eines Slice; ein Vorrats-Slice entstand nicht.

## Ausgeschlossene Gegenstände (§6 der Welle-Datei) und ihr Träger

Regeln dieser Sektion: Baseline-Regelwerk `v6.18.1` · `regelwerk/modul-06-roadmap.md`
§Wellen-Closure-Prozedur — Out-of-Scope gehört zur Zielsetzung; jeder Ausschluss nennt, wo er weiterlebt.

| Gegenstand | Träger | Stand |
|---|---|---|
| `data profile` von d-migrate | [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) Out-of-Scope; Negativfall der Abnahme (Exit 5, `Unsupported SQL.`) | unverändert ausgeschlossen |
| Produkt-Image, Registry, Signierung, Produktbetrieb | [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md); `slice-release-entscheidungen` (`open/`) | unverändert |
| Dauerhaftigkeit, Transaktionssemantik über mehrere Anweisungen | [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) Out-of-Scope; Grenze der Transaktionszusage in `SPEC-031` | unverändert |
| Last, Durchsatz, Dauerbetrieb | `slice-qa-performance` (`open/`) | offen |
| Benutzerverwaltung im Betrieb, Dateirechte der Benutzerliste | `BEO-TDS/benutzerliste-dateirechte-im-betrieb-offen` | offen |
| Vergleichslauf D gegen den echten Server | eigenes Target `make vergleichslauf-d` mit EULA-Entscheidung des Nutzers; Messprotokolle in `docs/messungen/` | Messungen dieser Welle am echten Server mit Freigabe des Nutzers (2026-11-06, 2026-11-11, 2026-11-12) |
| Andere Clients und Versionen als d-migrate 1.12.0 mit mssql-jdbc 13.4.0 | keine Zusage; Aufnahme ins Clientprofil [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md) ist eigene Entscheidung | offen, Frage 1 |

## Offene Fragen an den Nutzer

Diese Closure trifft keine davon; sie ändert für sie weder Regel noch Gate.

1. **Clientprofil.** Gehören JDBC (mssql-jdbc 13.4.0) und d-migrate in das Clientprofil von [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md)? Die Abnahme belegt die Funktionen für genau diese Versionen; `BEO-TDS/festlegung-gilt-nur-fuer-das-clientprofil` (3×) wartet darauf. *Empfehlung:* Folge-ADR zu [ADR-0001](../../adr/0001-tds-version-und-clientprofil.md), die d-migrate 1.12.0 mit mssql-jdbc 13.4.0 als zweites Clientprofil nennt und die Festlegungen am Profil festhält.
2. **Reviewer-Punkt zu Kommentaren und Sensor für Mitglieder der Fläche.** `BEO-TDS/kommentar-nennt-abwesenden-oder-unwirklichen-zustand` (10 Dateien; `AGENTS.md` §3.7 trägt die Regel, der Reviewer-Skill hat den Baseline-Punkt ohne Anker) und `BEO-TDS/oeffentliche-flaeche-mitglieder-ohne-sensor` (3×; `make flaeche` vergleicht Typnamen, nicht Mitglieder; Träger Architect): Regel, Sensor oder Streichung? *Empfehlung:* für den Kommentar wie bei `welle-fehler-abbruch` und `welle-anfrageverarbeitung` empfohlen: kein Sensor, die Klasse ist Urteil, den Baseline-Punkt im Reviewer-Skill mit Anker versehen; für die Mitglieder der Fläche eine Entscheidung des Architects, ob `make flaeche` um Mitglieder wächst.
3. **Zähler der `Stand`-Zeilen.** Mehrere Einträge zählen in ihrem `Stand` anders als die Dateien in `evidence/` (`eingangsliste-je-stage-von-hand` und `schranken-test-mit-ungeprueftem-zaehler` „2×“ gegenüber 3 Dateien, `zahl-an-mehreren-orten-ohne-sensor` „1×“ gegenüber 6, `parametertyp-des-clients-passt-nicht-zur-spalte` „1×“ gegenüber 3). Maßgeblich ist die Zahl der Dateien (README des Registers). *Empfehlung:* die `Stand`-Zeilen bei der nächsten Sichtung angleichen; die Einträge als über der Schwelle behandeln und je einen Ausgang setzen.
4. **Der Validator-Lauf auf dem End-Stand.** Grenze 1 der Lieferung: den Lauf mit allen Funktionen des Solls auf dem Stand nach `slice-zweites-error-folgefehler-angleichen` nicht wiederholt. *Empfehlung:* nicht wiederholen; der Verifier fuhr `make e2e` und d-migrate-Schritte auf `c84d5579`, und seitdem änderte sich kein Code. Wer es streng liest, startet einen Validator-Lauf gegen den End-Stand vor dem Merge.
5. **Archivierung (Schritt 4).** Siehe „Archivierung und Roadmap“ unten.

## Verifikation

Regeln dieser Sektion: Baseline-Regelwerk `v6.18.1` · `regelwerk/modul-06-roadmap.md`
§Wellen-Closure-Prozedur, Schritt 1 — keine Behauptung ohne nachprüfbaren
Anker (Hash, Lauf, Zahl).

- Alle Slices der Welle in `done/`: 33 Dateien mit `Welle: welle-testserver-manuell` in `done/`, 0 in `open/`, `next/` und `in-progress/` (am Dateisystem, 2026-11-13).
- `make gates` Exit 0 auf dem End-Stand dieser Closure (Wert und Dauer im Abschnitt „Archivierung und Roadmap“); Stufen mit Stempel und Docker-Cache, keine neue Test-Ausführung (nur Dokumente, Register und eine Command-Datei geändert).
- Frischbelege des Codes auf `c84d5579`: `make clean-build` Exit 0 (572 s, 1391 `PASSED`), `make e2e` Exit 0 (acht Stufen, 392 s), d-migrate 1.12.0 Exit 0 und `IDENTICAL`; Verifikationsbericht von `slice-zweites-error-folgefehler-angleichen` (`docs/reviews/2026-11-12-c84d5579-verifikation-slice-zweites-error-folgefehler-angleichen.md`). Diff gegen `c84d5579` in `src/`, `testserver/`, `tools/`, `Dockerfile`, `Makefile`: leer. Diese Closure fuhr weder `make clean-build` noch `make e2e` neu.
- Abnahme: `docs/messungen/abnahme-d-migrate-2026-11-12.md` (Rechner, Last, Digests oben), Verifikationsbericht `docs/reviews/2026-11-12-e27d7bfd-verifikation-slice-abnahme-d-migrate.md`: konform.
- Doku-Gates dieser Closure: `make docs-check`, `make flaeche`, `make kopplung-zahlen`: Werte im Abschnitt „Archivierung und Roadmap“.

## Drei Paarungen (Schritt 3, zum Schluss geprüft)

- *Anker:* ein Eintrag mit `liegt in`: `.claude/commands/implement-slice.md` besteht und trägt `seit welle-testserver-manuell` (Schritt 20c). Der Zeiger auf `spike-prototyp-nicht-im-repo-messung-nicht-wiederholbar` trägt kein neues `liegt in`.
- *Folge-Slice:* `slice-qa-performance` besteht als Datei in `open/`.
- *Register:* jede genannte Kennung `BEO-TDS/<slug>` besteht als Verzeichnis mit nicht leerem `evidence/` (Lauf in dieser Closure über alle genannten Kennungen).

## Archivierung und Roadmap

(Wird im Abschluss dieser Closure ausgefüllt.)
