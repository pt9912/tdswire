# Slice slice-abnahme-d-migrate: Abnahme: Validator-Lauf mit d-migrate über alle Funktionen des Solls gegen den Testserver

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [TDS-QA-06](../../../../spec/lastenheft.md#tds-qa-06--reproduzierbarkeit), [TDS-RB-01](../../../../spec/lastenheft.md#tds-rb-01--implementierungssprache), [ADR-0025](../../adr/0025-produktschnitt-bibliothek-und-testserver.md)

**Berührte Spec-Stellen:** — (Lauf und Beleg; Spec-Lücken, die der Lauf findet, gehen als benannte Lücke in die Closure-Notiz)

**Verantwortlich:** pt9912

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-05.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert; jeder Ausschluss mit Begründung.

**Ziel:** Ein Validator-Agent in frischem Kontext ohne Planwissen führt nach der Anleitung `docs/user/testserver-manuell.md` alle Funktionen des Solls mit d-migrate 1.12.0 gegen den Testserver aus und belegt jede mit der Ausgabe: Anmeldung (unverschlüsselt und mit `encrypt=true`), `data export`, `schema reverse`, `schema compare`, `schema migrate` einschließlich `--execute`, `data import` und `data transfer` im Standardpfad und mit `--on-conflict skip|update`. Das ist das „Mehr“ der Welle [welle-testserver-manuell](../welle-testserver-manuell.md) gegenüber den Slice-DoDs.

**Quelle und Vorbehalt.** Auslöser ist die Sonde vom 2026-11-05 ([d-migrate-sonde-2026-11-05](../../../messungen/d-migrate-sonde-2026-11-05.md)). Ihr Vorbehalt gilt hier: alles, was dort „lief“, lief gegen erfundene Katalogantworten; belegt ist, was d-migrate sendet und dass Treiber und d-migrate die TDS-Antworten der Bibliothek verarbeiten. Aufwandsgrößen (S/M/L) sind Schätzungen der Sonde, nicht gemessen. Dieser Slice ist der Beleg, den die Sonde nicht liefern konnte: gegen die Bibliothek mit dem Testserver als Integrator (Katalog aus den mitgeteilten Tabellen, Bestand im Arbeitsspeicher des Testservers), nicht gegen vorbereitete Antworten.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- `data profile`: Aggregate, `CAST`, `TOP`, `GROUP BY` schließt [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) aus; der Validator führt es höchstens als Negativfall aus (Erwartung: definierter SQL-Fehler, Exit ungleich 0), nicht als Soll-Funktion.
- Behebung gefundener Fehler im Slice: Der Validator meldet; jede Behebung ist ein Folge-Slice oder ein Rücklauf in den betroffenen Slice (der Rest der Welle bleibt offen, bis alles funktioniert).
- Der Vergleich gegen einen echten SQL Server: bleibt der Vergleichslauf D (EULA-Entscheidung des Nutzers).
- Aufnahme des Laufs in `make gates`: ein Lauf mit fremdem Image gehört nach [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)-Muster nicht in die Gates; ob ein `make`-Ziel entsteht, ist eine Entscheidung dieses Slice, kein Zwang.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

Liefer-Punkte (3):

- [x] **Lauf durch den Validator:** Ein Agent des Typs `validator` in frischem Kontext führt die Funktionen des Solls aus (Rechner, Last, Image-Digest von d-migrate und Testserver, Befehle, Exit-Codes, Auszüge der Ausgabe); jede Abweichung von der Anleitung ist ein Fund.
- [x] **Beleg je Funktion:** eine Zeile je Funktion in einem Messprotokoll unter `docs/messungen/` mit Exit-Code und dem Zustand danach (Export nach Import, Struktur nach `--execute`); nicht gelaufene Funktionen stehen als nicht gelaufen, nicht als grün.
- [x] **Anleitung nachgezogen:** `docs/user/testserver-manuell.md` beschreibt den Ist-Zustand (keine Chronik): die Grenze zu JDBC und die Aussage „Tabellen aus der Datei sind leer, Einfügen wird abgewiesen“ entsprechen dem Stand nach der Welle; sie nennt, wie der Bestand des Testservers zustande kommt und was er nicht tut (keine Dauerhaftigkeit).

Nachweis-Pflichten für alle Punkte:

- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben oder „keine Beobachtung angefallen“ in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `docs/messungen/abnahme-d-migrate-2026-11-12.md` — Messprotokoll des Validator-Laufs | neu | Beleg je Funktion |
| `docs/user/testserver-manuell.md` | update | Ist-Zustand nach der Welle |
| `docs/messungen/fremdschluessel-on-delete-d-migrate-2026-11-12.md` | neu | Nachmessung zu Fund F3 des Abnahmeprotokolls (Fremdschlüssel `restrict`/`no_action`, echter SQL Server 2022) |
| `docs/plan/planning/observations/BEO-TDS/` (Register, Evidence) | update | Punkt 16 und Zeiger im Register `katalog-elemente-grenzen-der-angleichung`, Evidence zu den Beobachtungen der Closure |
| `docs/plan/planning/` (Moves, Roadmap, Verweise) und `docs/reviews/` | update | Lifecycle-Moves nach `in-progress/`, Verweis-Nachzüge, Roadmap-Zeilen, Review-Bericht |
| optional `tools/` und `tools/mk/` — Skript und Ziel für den Lauf | neu | nur wenn der Lauf wiederholbar sein soll; nicht in `make gates` |

**Anschluss an die Entscheidung** ([ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), `Accepted` am 2026-11-06, Messungen: [Messprotokoll des Spikes](../../../messungen/callback-loesung-spike-2026-11-05.md); Liste in §3 von [slice-adr-testserver-bestand-und-katalog](../done/slice-adr-testserver-bestand-und-katalog.md)). Spec-Änderungen dieses Slice: `SPEC-040` (alle Läufe mit d-migrate, Exit 0), in der Tabelle „Login-Antwort des Servers (Festlegung)“ der Ersatz des Satzes „Welche Hauptversion mssql-jdbc mindestens verlangt, ist nicht gemessen“ durch die Messung (Hauptversionen 9 bis 17 ohne 14 angenommen, 0 und 8 abgelehnt, [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 10; der Wert `16.0.1000.6` bleibt; Übernahme aus dem Entscheidungs-Slice, der die Spezifikation nicht geändert hat) und die Festlegung von Version und Treiber des Prüfclients (d-migrate 1.12.0, mssql-jdbc 13.4.0, Image-Digest), die [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) der Spezifikation überlässt; ein Eintrag im Clientprofil braucht keine ADR (Entscheid 2026-11-06). Aus dem Spike: Der Standardpfad von `data import` ist für Tabellen mit INT-Spalte nicht zu belegen (b3); mit `--on-conflict skip` lief INT nur mit Verengung Int64 auf Int32, angepasstem RPC-Parser und unter der Handle-Grenze (a3, a6, a7); die Abnahme sagt das aus, statt es zu überspielen (Entscheid 2026-11-06: BIGINT-Schlüssel in den Beispieltabellen, Gegenmessung am echten SQL Server bleibt offen).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): Alle anderen Slices der Welle [welle-testserver-manuell](../welle-testserver-manuell.md) für das Soll liegen in `done/`: [slice-prelogin-serverversion](../done/slice-prelogin-serverversion.md), [slice-d-migrate-verbindung-und-export](../done/slice-d-migrate-verbindung-und-export.md), [slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md), [slice-schreibpfad-bulk-load](../done/slice-schreibpfad-bulk-load.md), [slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md), [slice-tls-d-migrate](../done/slice-tls-d-migrate.md).

**Weitere Slices der Welle, Nutzerentscheid 2026-11-07 (Zuordnung des Planners, geprüft gegen das Soll):** [slice-katalog-und-struktur-an-messung-angleichen](../done/slice-katalog-und-struktur-an-messung-angleichen.md), [slice-katalogspalten-smallint-tinyint-angleichen](../done/slice-katalogspalten-smallint-tinyint-angleichen.md), [slice-ddl-abschluss-und-transaktionszustand-angleichen](../done/slice-ddl-abschluss-und-transaktionszustand-angleichen.md) (die drei Teile des früheren Slice `slice-katalog-und-struktur-an-messung-angleichen`, Zuschnitt 2026-11-08 nach [ADR-0041](../../adr/0041-spaltentypen-der-katalogantworten-ueber-internen-weg.md) und [ADR-0042](../../adr/0042-anweisungsart-und-zustandswechsel-nur-durch-die-bibliothek.md)), [slice-sp-unprepare-ohne-obergrenze](../done/slice-sp-unprepare-ohne-obergrenze.md), [slice-antwort-tokens-zielbild-luecken-bauen](../done/slice-antwort-tokens-zielbild-luecken-bauen.md) und [slice-statementkind-index-mitglieder](../done/slice-statementkind-index-mitglieder.md) (Abschluss von `CREATE INDEX` und `DROP INDEX`, Entscheid des Nutzers 2026-11-12) gleichen den Server dem echten SQL Server an (Zielbild); keine Funktion des Solls hängt an ihnen, sie sind **Grenze, keine Voraussetzung**; liegen sie vor der Abnahme, misst der Lauf sie mit (Regress der Funktionen `schema reverse`, `schema migrate --execute`), sonst gelten die Messvermerke der Spezifikation. [slice-adr-massenladen-atomar-an-messung](../done/slice-adr-massenladen-atomar-an-messung.md) ist **Eingang** von [slice-schreibpfad-bulk-load](../done/slice-schreibpfad-bulk-load.md) (Verhalten bei Fehler im Massenladen) und damit mittelbar Voraussetzung dieser Abnahme für `data import` im Standardpfad.

**Voraussetzung aus dem Register [katalog-elemente-grenzen-der-angleichung](../observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/state.md) (Setzung des Planners nach der Closure von [slice-katalog-indizes-defaults-fremdschluessel](../done/slice-katalog-indizes-defaults-fremdschluessel.md); Standardantwort auf die Empfehlung des Verifiers, umkehrbar):** [slice-drop-constraint-form-d5](../done/slice-drop-constraint-form-d5.md) liegt vor diesem Slice, weil ein zweites `schema migrate --execute` mit einer Prüfbedingung im Soll sonst an `DROP CONSTRAINT` mit Exit 5 endet und die Punkte 1 und 3 des Registers (Standardwert an vorhandener Spalte, Index mit `unique: true`) maskiert. [slice-declare-df-standardwert-vorhandene-spalte](../done/slice-declare-df-standardwert-vorhandene-spalte.md) liegt ebenfalls vor diesem Slice (Form D7 gebaut, Entscheid des Nutzers 2026-11-12); [slice-zweites-error-folgefehler-angleichen](../open/slice-zweites-error-folgefehler-angleichen.md) ist keine Voraussetzung und wird ausgelöst, wenn die Abnahme den zweiten Standardwert an derselben Spalte oder das zweite ERROR 3727 trifft. Ebenso liegt [slice-drop-index-if-exists](../done/slice-drop-index-if-exists.md) vor diesem Slice (Entscheid des Nutzers 2026-11-11, Punkt 9 des Registers): ändert das Soll einen vorhandenen Index, sendet d-migrate zuerst `DROP INDEX IF EXISTS`, und der Testserver weist die Form heute ab. Mit dem mitgelieferten Soll (`sim.yaml`, keine Elemente) trifft den Lauf keine dieser Grenzen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): Der Lauf findet Fehler in mehreren Funktionen; die Behebungen wandern in die betroffenen Slices, die Abnahme beginnt von vorn.
- `in-progress` → `open` (blockiert): Ein Fund verlangt eine Entscheidung des Nutzers (zum Beispiel eine Funktion, die sich nicht belegen lässt).

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln.

Jede Funktion des Solls hat einen Beleg mit Ausgabe oder einen benannten, vom Nutzer entschiedenen Ausgang; `make gates` grün; Closure-Notiz mit Steering-Loop-Lerneintrag geschrieben. Dieser Slice ist die Bedingung des Mehrs im Closure-Trigger der Welle.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — jedes Risiko bekommt genau einen Ausgang.

- Ein Validator ohne Planwissen findet Lücken der Anleitung, die der Planner nicht sieht; das ist die Absicht, kostet aber Läufe (Aufwand nicht gemessen; Setzung M). — **Ausgang:** eingetreten: F1, F2 und F4 sind Lücken der Anleitung, behoben und die Anleitung nachgezogen (Neustart-Hinweis, `data transfer` mit Befehl, Port in den Zeichenfolgen); F3 ist Art (d), keine Lücke.
- Lauf hängt an einem Image mit beweglichem Tag (`ghcr.io/pt9912/d-migrate:latest`): der Digest wird im Protokoll genannt, die Verfügbarkeit des Images nicht gesichert. — **Ausgang:** weiter offen: das Image blieb im Lauf verfügbar und der Digest steht im Messprotokoll; dass es verfügbar bleibt, ist nicht gesichert.
- FLOAT (Hürde 12): Braucht eine Funktion des Solls den Typ und hat die Entscheidung ihn nicht aufgenommen, scheitert die Abnahme an dieser Stelle. — **Ausgang:** entfallen: keine Funktion des Solls brauchte den Typ.

## 7. Closure-Notiz

Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register; `grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln.

- **Was hat funktioniert:** Der Validator in frischem Kontext fuhr alle Funktionen des Solls nach der Anleitung ([Messprotokoll](../../../messungen/abnahme-d-migrate-2026-11-12.md), d-migrate 1.12.0, Image-Digest und Rechner im Protokoll): Anmeldung mit und ohne `encrypt=true`, `data export`, `schema reverse`, `schema compare`, `schema migrate` mit und ohne `--execute`, `data import` und `data transfer` im Standardpfad und mit `--on-conflict skip|update`, `data profile` als Negativfall (Exit 5); Exit-Codes und Zustand danach stehen je Funktion. Der Verifier spielte die Anleitung mit drei Containern nach und fand keinen Exit-Code abweichend. Der Server verfehlte in keinem Lauf eine Zusage der Anleitung (Funde der Art (a): keine); die Wellen-Slices haben das Soll getragen.
- **Was ging anders als geplant:** (1) Die Abnahme fand vier Funde: F1, F2 und F4 sind Lücken der Anleitung (Neustart nach Aufgabe 3, `data transfer` ohne Schritte, fester Port in den Zeichenfolgen), in dieser Arbeit behoben; F3 (Fremdschlüssel mit `restrict` oder `no_action`, Exit 5) ist Verhalten des Clients, am echten SQL Server 2022 gleich. (2) F3 verlangte eine Nachmessung am echten Server, die der Slice nicht plante ([Messprotokoll](../../../messungen/fremdschluessel-on-delete-d-migrate-2026-11-12.md), 14 Läufe; Plan-Tabelle §3 nachgetragen, Review F-2). (3) Der Review (0 HIGH, 2 MEDIUM, 2 LOW, 3 INFO) fand die Anleitung zu F3 noch mit „nicht gemessen“ und eine Fund-Art ohne Definition; F-1 bis F-5 sind im Commit `e27d7bfd` nachgebessert, der Verifier prüfte sie gegen den Text. (4) Die Verifikation (konform, 0 HIGH, 0 MEDIUM, 2 LOW, 2 INFO) fand V-1 (Begrenzung von F3 fehlte in der Zeile F3 des Abnahmeprotokolls) und V-2 (erledigte Vorschläge im Messprotokoll als offen geführt): in der Closure behoben. V-3 (der Satz zum Anfangsstand beider Container ist aus dem Vorabtest gefolgert, nicht gefahren): in der Anleitung als Folgerung benannt. (5) V-4 („die Grenze zu JDBC“ der DoD): Entscheid des Planners, die Formulierung genügt; die Anleitung nennt in Aufgabe 6 und der Referenz, was mssql-jdbc kann (Massenladen, `MERGE`) und was der Server abweist (`INSERT` und `DELETE` als SQL-Text), mit Messung und Testbeleg; ein eigener Satz zur „Grenze zu JDBC“ würde nichts hinzufügen, was dort nicht steht.
- **Spec-Lücken und offene Entscheidung:** keine neue Spec-Lücke. Offen, Entscheid des Nutzers: Punkt 11 des Registers (Namen von Standardwert und Schlüssel): Namen führen (Weg 1) oder die Grenze in die Spezifikation aufnehmen (Weg 2); die Abnahme hat die Namen nicht gebraucht, die Spezifikation ist nicht geändert.
- **Steering-Loop-Eintrag:** Der Validator fand keinen Fehler im Server, aber vier Funde bei Anleitung und Client: die Abnahme war als Beleg wirksam, ein Lauf ohne Planwissen deckte, was die Wellen-Slices und die Anleitung nicht sahen. Kein Eintrag verkörpert, daher kein `liegt in`; gezählt, nicht verkörpert, je Beleg 1× oder mit Zähler im Register: (1) *Ein Fund einer Abnahme wird vor der Behebung am echten Server gemessen:* F3 sah aus wie eine Abweichung des Testservers und war Verhalten des Clients; erst die Messung ordnete es ein und ersparte einen Folge-Slice für den Testserver. Neu geführt als Beobachtung `BEO-TDS/fund-der-abnahme-vor-der-behebung-am-echten-server-messen` (Evidence dieses Slice). (2) *Eine Begrenzung steht an jeder Fundstelle der Aussage im selben Satz:* V-1 ist ein weiteres Auftreten von `BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen` (Evidence dieses Slice; die Beobachtung ist bereits verkörpert, ihr Prüfpunkt im Reviewer-Skill hat die Stelle im Abnahmeprotokoll nicht erfasst, weil die Nachbesserung der Anleitung die Zeile nicht über die Liste der Fundstellen suchte). Zur Setzung der Haken: erst nach Review und Verifikation gesetzt (`BEO-TDS/haken-vor-verifikation-gesetzt`, verkörpert). Keine benannte Spec-Lücke.
- **Beobachtungs-Register (`../observations/`):** neu: [BEO-TDS/fund-der-abnahme-vor-der-behebung-am-echten-server-messen](../observations/BEO-TDS/fund-der-abnahme-vor-der-behebung-am-echten-server-messen/state.md). Fortgeschrieben mit `evidence/slice-abnahme-d-migrate.md`: [BEO-TDS/katalog-elemente-grenzen-der-angleichung](../observations/BEO-TDS/katalog-elemente-grenzen-der-angleichung/state.md) (neuer Punkt 16; getroffen 9, 14, 15 (a); nicht getroffen 10 bis 13, 15 (b), 15 (c), ihre Trigger bleiben; Punkt 11 offen, Entscheid des Nutzers) und [BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen](../observations/BEO-TDS/fortschreibung-laesst-zweite-fundstelle-stehen/state.md).
- **Folge-Slices:** keiner angelegt. [slice-zweites-error-folgefehler-angleichen](../open/slice-zweites-error-folgefehler-angleichen.md) bleibt in `open/`: der Trigger (die Abnahme trifft den zweiten Standardwert an derselben Spalte oder das zweite ERROR 3727) ist nicht ausgelöst; der Slice schließt die Welle mit und ist Bedingung des „Mehrs“ der Welle, spätestens bei der Wellen-Closure fällig.
- **Risiken aus §6:** alle 3 mit Ausgang eingetragen, in der Reihenfolge von §6 (eingetreten: 1; weiter offen: 2 (Verfügbarkeit des Images); entfallen: 3).
- **Verifikation:** [Verifikationsbericht](../../../reviews/2026-11-12-e27d7bfd-verifikation-slice-abnahme-d-migrate.md) auf `e27d7bfd`: konform; Anleitung nachgespielt, Exit-Codes gleich den Protokollzeilen; `make docs-check`, `make flaeche`, `make kopplung-zahlen` Exit 0, `make gates` Exit 0 (9 s, Stempel und Cache; Änderung nur in `docs/`, kein Frischbeleg nötig); `make clean-build`, `make e2e` und Mutationen nicht gefahren (kein Code im Diff). Review: [Reviewbericht](../../../reviews/2026-11-12-3a276fae-review-slice-abnahme-d-migrate.md) mit Nachbesserung in `e27d7bfd`. Läufe des Planners auf dem End-Stand (nur Dokumente und Register geändert): `make docs-check` (0 Befunde, 1389 Dateien), `make flaeche` und `make kopplung-zahlen` Exit 0; `make gates` Exit 0 in 9 s (Stempel und Docker-Cache, keine neue Test-Ausführung).
- **Drei Paarungen:** Das Repo führt Wellen; die Paarungen prüft die Closure von [welle-testserver-manuell](../welle-testserver-manuell.md). Hier geprüft: *Anker*: kein Anker gesetzt (kein `liegt in`). *Folge-Slice*: [slice-zweites-error-folgefehler-angleichen](../open/slice-zweites-error-folgefehler-angleichen.md) besteht als Datei in `open/`. *Register*: alle genannten Kennungen bestehen als Verzeichnis mit nicht leerem `evidence/` (die neue Beobachtung mit der Evidence dieses Slice).

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung.

**Vorgelagert — Sub-Area-Wahl prüfen:** Eine Sub-Area (`*`, Kürzel `TDS`); keine Aufteilung nötig.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (Namensliste der Eröffnung, Texte nicht einzeln gelesen); kein Treffer ausgemacht. Der Implementer sichtet das Register bei Start erneut.

Alle berührten Sub-Areas GF.
