# Slice slice-katalog-und-struktur-an-messung-angleichen: Katalogspalten, Strukturabweisung und Änderungszähler der Strukturänderung an die Messung am echten SQL Server angleichen (M-16, M-19, M-20)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md), [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)

**Berührte Spec-Stellen:** SPEC-035 (Katalog der Spalten: Typen von `max_length`, `precision`, `scale`, `is_nullable`; Strukturabweisung mit Fehlernummern; Änderungszähler 0, Setzung) · SPEC-036 (Form D: `CREATE TABLE` ohne Schlüssel abgewiesen) · Abschnitt Fehlercodes (`TDS_UNSUPPORTED_SQL`, `TDS_SIM_UNKNOWN_OBJECT` bei der Strukturabweisung). **Die Spezifikation ändert dieser Slice** (Spezifikation ist Zielbild: der Wortlaut folgt dem Messwert, die „bekannte Abweichung“ und die Messvermerke entfallen oder werden zur Zusage).

**Verantwortlich:** — bis zur Priorisierung.

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-07.

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Der Server dieses Repos antwortet auf die Katalogabfragen von d-migrate und auf `CREATE TABLE` und `ALTER TABLE … ADD` so, wie der echte SQL Server (2022 und 2025) es nach den Zeilen M-16, M-19 und M-20 des [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07) tut: Katalogspalten `max_length` als `smallint`, `precision` und `scale` als `tinyint`, `is_nullable` als `bit` nullbar; Strukturabweisung mit den Fehlernummern 2714 (`CREATE TABLE` einer vorhandenen Tabelle, State 6), 2705 (`ADD` einer vorhandenen Spalte, State 4) und 4902 (`ALTER TABLE` einer unbekannten Tabelle), Class 16; `CREATE TABLE` ohne Schlüssel erlaubt; Abschluss von `CREATE TABLE` und `ALTER TABLE … ADD` als `DONE` ohne `COUNT`, Befehl `0x00c6` bzw. `0x00d8`, `set implicit_transactions on` mit `ENVCHANGE` Typ 8 und das `COMMIT` mit Typ 9.

**Herkunft.** Aus der Zuordnung der Befunde im [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07) (M-16, M-19, M-20 widersprechen der Setzung der Spezifikation und dem Bau von [slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md); Verifikation V-1 dieses Slice). Träger-Register: [`BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab`](../observations/BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab/state.md). Nutzerentscheid 2026-11-07 (per Rückfrage an den Nutzer, Beleg im Register-Eintrag dort). Nutzerentscheid: Bau-Slice anlegen; Katalogspalten `max_length` smallint, `precision` und `scale` tinyint, `is_nullable` bit nullbar; Strukturabweisung mit 2714, 2705, 4902; `CREATE TABLE` ohne Schlüssel erlaubt; Änderungszähler: DONE ohne COUNT, `implicit_transactions` mit ENVCHANGE Typ 8 und 9. Die Spezifikation ist Zielbild, der echte SQL Server die Referenz (Nutzergrundsatz 2026-11-06). Die Messung [slice-qa-vergleichslauf-d-messungen-bulk-merge](../done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md) liegt vor (Einzelmessungen je Instanz 2022 und 2025, Referenzclient `Microsoft.Data.SqlClient`, nicht mssql-jdbc von d-migrate).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Die Sichten für Indizes, Standardwerte, Prüfbedingungen und Fremdschlüssel — [slice-katalog-indizes-defaults-fremdschluessel](../open/slice-katalog-indizes-defaults-fremdschluessel.md); sie bauen auf den Typen dieses Slice auf, haben aber ihre eigene Messung (M-21).
- Die Fehlerabbildung von `sp_executesql` und der Abschluss im RPC — [slice-antwort-tokens-zielbild-luecken-bauen](../open/slice-antwort-tokens-zielbild-luecken-bauen.md); dieser Slice berührt den Abschluss von SQL_BATCH-Strukturänderungen (M-20), nicht den der RPC-Antwort (`DONEPROC`).
- Weitere DDL-Formen (Fremdschlüssel, Indizes, Standardwerte in `CREATE TABLE`) — Folge-Slice [slice-katalog-indizes-defaults-fremdschluessel](../open/slice-katalog-indizes-defaults-fremdschluessel.md); gemessen ist nur, was die Messreihe trägt.
- Eine Aussage über andere Clients als den Referenzclient: Die Texte sind der Wortlaut von d-migrate (`DMigrateTexte.cs`), gemessen ist die Antwort des Servers auf den Text, nicht das Verhalten von d-migrate oder mssql-jdbc (`AGENTS.md` §3.8).

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Katalogspalten** ([TDS-FA-11](../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): In der Antwort der Katalogabfragen von d-migrate 1.12.0 sind `max_length` `smallint`, `precision` und `scale` `tinyint` und `is_nullable` `bit` nullbar (`BITN`) (M-16); `SPEC-035` nennt den Typ der Spalte statt der „bekannten Abweichung `Int32`“; je Spalte ein Test gegen das COLMETADATA (Typ, Länge, Nullbarkeit), Maße von `bigint`, `bit`, `time`, `datetime2` und Sonderwert -1 (M-17, M-18) bleiben unverändert (Messung stützt sie).
- [ ] **Strukturabweisung** ([TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): `CREATE TABLE` einer vorhandenen Tabelle ergibt 2714 (Class 16, State 6), `ADD` einer vorhandenen Spalte 2705 (State 4), `ALTER TABLE` einer unbekannten Tabelle 4902 (State 1); `CREATE TABLE` ohne Schlüssel ist erlaubt (die Abweisung nach Form D entfällt); `SPEC-035`, `SPEC-036` und die Fehlertabelle sind angeglichen, die Verbindung bleibt nach dem Fehler offen (M-19); Tests je Fall.
- [ ] **Änderungszähler und Transaktionstexte** ([TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität)): `CREATE TABLE` schließt mit `DONE` ohne `COUNT`, Befehl `0x00c6`, `ALTER TABLE … ADD` mit Befehl `0x00d8` (M-20); `set implicit_transactions on` antwortet mit Befehl `0x00b9` und, im Zusammenhang mit `CREATE TABLE`, `ENVCHANGE` Typ 8, das `COMMIT` mit `ENVCHANGE` Typ 9 und den Befehlen der Zeile M-20; Tests je Text. **Prüfung der Fläche:** Braucht eine der drei Änderungen einen neuen öffentlichen Namen (Fehlerkennung, Callback-Rückgabe), ist die Rückführung `in-progress` → `next` benannt (Entscheidung über die Fläche nach `SPEC-059` und [ADR-0021](../../adr/0021-oeffentlicher-integrations-einstieg.md), `make flaeche`).

Nachweis-Pflichten für alle Punkte:

- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

## 3. Plan (vor Code)


Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/` (Katalog der Spalten, Strukturformen, Abschluss der Strukturänderung) | update | Typen der Katalogspalten, Fehlernummern, Befehlskennungen |
| `testserver/kotlin/` (Strukturänderung über den Integrator, Fehlerabbildung) | update (falls betroffen) | Integrator-Anteil der Strukturabweisung |
| `spec/spezifikation.md` (`SPEC-035`, `SPEC-036`, Fehlertabelle) | update | Zielbild: Wortlaut folgt dem Messwert; Messvermerke werden Zusage |
| `docs/messungen/katalog-und-ddl-zustand-2026-11-06.md`, `docs/user/` (Grenzen) | update | Nachziehen der Aussagen über Typen und Fehler des Katalogs |
| Tests (Kotlin, `src/test`) | neu / update | Happy/Boundary/Negative nach [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate) |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-qa-vergleichslauf-d-messungen-bulk-merge](../done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md) liegt in `done/` (erfüllt mit der Closure dieses Slice) und [slice-katalog-und-ddl-zustand](../done/slice-katalog-und-ddl-zustand.md) liegt in `done/` (erfüllt). Beide Slices berühren die Katalogdateien; [slice-katalog-indizes-defaults-fremdschluessel](../open/slice-katalog-indizes-defaults-fremdschluessel.md) startet nach diesem Slice (Setzung des Planners: die Typen der Spalten wirken in jede Sichtantwort); ein gleichzeitiger Lauf beider erzeugt Konflikte am Katalog.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte oder die Fläche wächst; Schnittvorschlag: Katalogspalten | Strukturabweisung | Änderungszähler.
- `in-progress` → `open` (blockiert): die Änderung des Spaltentyps bricht einen Beleg am fremden Client (d-migrate liest `max_length` über JDBC); der Nutzer entscheidet dann über den Weg.

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle drei Liefer-Punkte der DoD erledigt, `make gates` grün, der Review-Report liegt vor, die Zeilen M-16, M-19, M-20 sind in der Zuordnung des [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07) von „widerspricht“ auf „angeglichen“ gezogen, die Closure-Notiz trägt den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- **Auswirkung auf den Beleg am fremden Client.** d-migrate liest `max_length` und `precision` über JDBC; ob `smallint` und `tinyint` statt `int` etwas ändert, ist nicht gemessen. Die Abnahme ([slice-abnahme-d-migrate](../open/slice-abnahme-d-migrate.md)) prüft es am Ende (Grenze, kein Soll der Sonde). — **Ausgang:** offen bis zur Closure.
- Die Reihenfolge zu [slice-katalog-indizes-defaults-fremdschluessel](../open/slice-katalog-indizes-defaults-fremdschluessel.md) ist eine Setzung des Planners. — **Ausgang:** offen bis zur Closure.
- Die Messung trägt den Referenzclient, nicht mssql-jdbc; die Antworten auf die Texte von d-migrate sind gemessen, ihre Wirkung im Treiber nicht (`AGENTS.md` §3.8). — **Ausgang:** offen bis zur Closure.

## 7. Closure-Notiz


Regeln dieser Sektion: Baseline-Regelwerk `modul-06-roadmap.md`
§Das Beobachtungs-Register (vorhandene `BEO-<KUERZEL>/<slug>` **zitieren** statt neu
formulieren — sonst zählt das Register zwei Namen getrennt) ·
`grundlagen-traceability.md` §Herkunfts-Anker für Steering-Loop-Regeln (das
Feld `liegt in` steht **nur**, wenn mit diesem Slice wirklich etwas verkörpert
wurde; Feld und Zielort auf **einer** Zeile, Sektionsangabe innerhalb der
Backticks). Ging der Gegenstand an einen anderen Slice oder entfiel er, trägt
diese Sektion die Zeile `Gegenstand:` mit Kennung oder Grund und jedes Risiko
aus §6 seinen Ausgang; die Liefer-Punkte der DoD bleiben leer
(`modul-05-planning-harness.md` §Ein Slice, dessen Gegenstand ein anderer
übernimmt).

Wird bei der Closure gefüllt.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung — dort die **zwei vorgelagerten
Schritte** (sie stehen in jedem Slice-Plan, unabhängig von Modus und
Slice-Typ) und die **vier Pflichtkriterien** (Konventionen-Dichte ·
Phase-Reife · Evidenz-/Diskrepanz-Risiko · Reconciliation-Aufwand), vier und
nicht mehr.

**Der Abschnitt selbst entfällt nie.** Die zwei vorgelagerten Prüfungen laufen
in **jedem** Slice-Plan — sie hängen weder am Modus noch am Slice-Typ. Bedingt
ist allein der Modus-Begründungsblock am Ende; deshalb nennt der Titel beide
Hälften.

**Vorgelagert — Sub-Area-Wahl prüfen:** `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); eine eigene Sub-Area besteht nicht.

**Vorgelagert — offene Beobachtungen sichten:** [`BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab`](../observations/BEO-TDS/katalog-strukturabweisung-und-aenderungszaehler-weichen-vom-echten-server-ab/state.md) (Gegenstand dieses Slice), [`BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server`](../observations/BEO-TDS/spec-setzung-widerspricht-messung-am-echten-server/state.md), [`BEO-TDS/messung-am-echten-sql-server-fehlt`](../observations/BEO-TDS/messung-am-echten-sql-server-fehlt/state.md).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
