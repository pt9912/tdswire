# Slice slice-adr-massenladen-atomar-an-messung: ADR-Entwurf zur Atomarität des Massenladens: „Zeilen vor dem Fehler bleiben“ ([ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 5 (iii) und Nr. 6) gegen die Messung M-12 (2627, keine Zeile der Nachricht bleibt)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-testserver-manuell

**Bezug:** [TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)

**Berührte Spec-Stellen:** [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) (Nr. 5 (iii) und Nr. 6, Folgepflicht 3) · SPEC-035 (Zeilenquelle, Stapel-Callback) · SPEC-036 (Massenladen). Die Spezifikation ändert der Bau-Slice [slice-schreibpfad-bulk-load](../done/slice-schreibpfad-bulk-load.md) nach `Accepted`, nicht dieser Slice.

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

**Ziel:** Eine ADR im Status `Proposed` (Architect, `Supersedes` teilweise [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md), Nr. 5 (iii) und Nr. 6 wörtlich benannt) legt dem Nutzer die Frage vor, ob eine Nachricht des Massenladens eine Anweisung mit atomarer Übernahme ist (der echte Server nimmt bei 2627 keine Zeile der Nachricht auf, M-12), auch ohne Stapel-Callback beim Integrator. Der Slice entscheidet nichts; entscheiden tut der Nutzer über die ADR. Die Allaussage der ADR trägt nur so weit wie ihr Beleg: die Messung M-12 ist ein Fall (Schlüsselverstoß, 3 Zeilen, ein Paket, Verifikation V-4); der Slice erweitert sie.

**Herkunft.** Zielbild-Konflikt aus der Zuordnung der Befunde (M-11, M-12) des [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07). Träger-Register: [`BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand`](../observations/BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand/state.md). Nutzerentscheid 2026-11-07 (per Rückfrage an den Nutzer, Beleg im Register-Eintrag dort). Nutzerentscheid: Folge-ADR schreiben lassen (Architect, `Proposed`, `Supersedes` teilweise [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md)); der Bau richtet sich nach `Accepted`. Die Spezifikation ist Zielbild, der echte SQL Server die Referenz (Nutzergrundsatz 2026-11-06). Die Messung [slice-qa-vergleichslauf-d-messungen-bulk-merge](../done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md) liegt vor (Einzelmessungen je Instanz 2022 und 2025, Referenzclient `Microsoft.Data.SqlClient`, nicht mssql-jdbc von d-migrate). Eine angenommene ADR wird nicht überschrieben (`AGENTS.md` §3.5), deshalb die Folge-ADR.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Der Bau der Atomarität und der Weitergabe der Zeilen — [slice-schreibpfad-bulk-load](../done/slice-schreibpfad-bulk-load.md); er richtet sich nach `Accepted` (Setzung des Planners: der Bau beginnt nicht vor dem Entscheid, sonst baut er gegen den Wortlaut, den die Messung widerlegt).
- Eine Änderung von [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) selbst — sie ist `Accepted` und unveränderlich; die neue ADR schärft über `Supersedes (teilweise)`.
- Messungen mit mssql-jdbc (Treiber von d-migrate) — Folgepflicht 2 der ADR hat ihren Träger in [slice-schreibpfad-merge-output](slice-schreibpfad-merge-output.md) (Messauftrag).

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Messung der Atomarität** ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)): Das [Messprotokoll](../../../messungen/referenzabgleich.md#messreihe-zu-massenladen-merge-katalog-und-strukturänderung-messungen-vom-2026-11-07) führt Zeilen mit der nächsten freien Nummer für den Schlüsselverstoß an erster, mittlerer und letzter Zeile einer Nachricht, in der zweiten von drei Nachrichten (mehrere Pakete, mehrere `BatchSize`) und für eine Quelle mit mehreren Verstößen: Zustand der Tabelle danach (Zeilen), Tokenfolge, Zähler, Verbindung; Einzelmessung je Instanz 2022 und 2025 (lokal, EULA für das gepinnte Image: Nutzer, 2026-11-06), Grenze „Referenzclient, nicht mssql-jdbc“ im selben Satz. Beleg: `make vergleichslauf-d`, Messprotokoll, `make docs-check`.
- [ ] **ADR `Proposed`** ([TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)): Der Architect schreibt die ADR aus der ADR-Vorlage (`.harness/baseline/v6.18.1/templates`): Optionen mindestens A (die Nachricht ist eine Anweisung mit atomarer Übernahme, wie der echte Server) und B (Teilbestand bleibt wie in [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 5 (iii) und Nr. 6, Abweichung als Setzung), dazu die Frage, was der Stapel-Callback des Integrators bei Fehler zurückgibt, und die Zuordnung der Fehlercodes je Fehlerursache (Schlüsselverstoß, Typfehler, NULL in nicht nullbarer Spalte, volle Zellgrenze des Bestands, Fehler im Code des Integrators): der Bau von [slice-schreibpfad-bulk-load](../done/slice-schreibpfad-bulk-load.md) meldet heute jede Ausnahme des Callbacks als 2627 mit dem Text eines Schlüsselverstoßes (Setzung, Verifikation V-4), und diese Zuordnung hat über die Closure dieses Slice hinaus keinen anderen Träger; wo der echte Server messbar andere Nummern nennt, tragen die Zeilen des ersten Liefer-Punkts sie, sonst bleibt die Zuordnung eine benannte Setzung der ADR; Empfehlung auf Grundlage der Messung; `Supersedes (teilweise)`: [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md); `Schärft:`-Feld; Folgepflicht-Liste; Eintrag im ADR-Index. Beleg: `make docs-check`.
- [ ] **Entscheid und Nachzug** ([TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)): Der Nutzer entscheidet; mit `Accepted` zieht der Planner [slice-schreibpfad-bulk-load](../done/slice-schreibpfad-bulk-load.md) nach (Wortlaut, Teilbestand, Tests) und trägt das Ergebnis in das Register [`BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand`](../observations/BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand/state.md) ein. Beleg: ADR-Status, Stand des Registers.

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
| `referenztest-dotnet/Vergleich/MessungBulkTests.cs`, `referenztest-dotnet/Mitschnitt/messung/bulk-merge-katalog/{2022,2025}/` | update | Szenarien der Atomarität |
| `docs/messungen/referenzabgleich.md` | update | Zeilen M-25 ff. (nächste freie Nummer) |
| `docs/plan/adr/` (neue ADR) und `docs/plan/adr/README.md` | neu / update | Entscheidung zur Atomarität, Index |
| `docs/plan/planning/observations/BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand/` | update | Stand, Verweis auf die ADR |
| `src/main/kotlin/io/github/pt9912/tdswire/simulation/BulkLoads.kt`, `spec/spezifikation.md` (`SPEC-036`, Fehlercode-Tabelle) | update (nach `Accepted`, durch den Bau-Slice) | Code je Fehlerursache statt Sammelcode 2627 |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): [slice-qa-vergleichslauf-d-messungen-bulk-merge](../done/slice-qa-vergleichslauf-d-messungen-bulk-merge.md) liegt in `done/` (erfüllt mit der Closure dieses Slice) und ein Verantwortlicher ist eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): die ADR trägt mehr als die Atomarität (zum Beispiel Transaktionen um das Massenladen oder das IGNORE-Bit); Schnittvorschlag: Messung | ADR | Nachzug.
- `in-progress` → `open` (blockiert): das gepinnte Image startet nicht oder die EULA-Bestätigung entfällt; der Nutzer entscheidet, ob die ADR auf M-12 als Setzung mit der Grenze „ein Fall“ entsteht.

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Alle drei Liefer-Punkte der DoD erledigt, die ADR steht auf `Accepted` (Entscheidung des Nutzers), [slice-schreibpfad-bulk-load](../done/slice-schreibpfad-bulk-load.md) ist nachgezogen, `make gates` grün, der Review-Report liegt vor, und die Closure-Notiz trägt den Steering-Loop-Lerneintrag sowie die Zuordnung *Messung | Festlegung* für die Atomarität.

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Die Erweiterung der Messung kann M-12 nicht bestätigen (etwa Teilbestand bei Fehler in der Mitte einer Nachricht mit mehreren Paketen); dann ändert sich die Option, nicht der Slice. — **Ausgang:** offen bis zur Closure.
- Die Messung trägt den Referenzclient (`SqlBulkCopy`), nicht mssql-jdbc; der Treiber von d-migrate kann eine andere Nachrichtenfolge senden ([callback-loesung-spike](../../../messungen/callback-loesung-spike-2026-11-05.md)); die ADR trägt die Grenze im selben Satz. — **Ausgang:** offen bis zur Closure.
- [slice-schreibpfad-bulk-load](../done/slice-schreibpfad-bulk-load.md) kann vor dem Entscheid in `in-progress/` stehen (anderer Zweig); dann gilt Setzung des Planners: das Verhalten bei Fehler wird zurückgestellt oder als Abweichung gekennzeichnet. — **Ausgang:** offen bis zur Closure.

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

**Vorgelagert — offene Beobachtungen sichten:** [`BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand`](../observations/BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand/state.md) (Gegenstand), [`BEO-TDS/messung-am-echten-sql-server-fehlt`](../observations/BEO-TDS/messung-am-echten-sql-server-fehlt/state.md), [`BEO-TDS/aussagen-ueber-messung-hinaus`](../observations/BEO-TDS/aussagen-ueber-messung-hinaus/state.md) (ein Fall, Allaussage).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
