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
- Messungen mit mssql-jdbc (Treiber von d-migrate) — Folgepflicht 2 der ADR hat ihren Träger in [slice-schreibpfad-merge-output](../done/slice-schreibpfad-merge-output.md) (Messauftrag).
- Die Fehlernummern 2628, 2627 und 208 des `MERGE` (M-15) — [slice-merge-antwort-an-messung-angleichen](../open/slice-merge-antwort-an-messung-angleichen.md); dieser Slice trägt die Zuordnung der Codes je Fehlerursache **des Massenladens** (ein Punkt gehört genau einem Slice).

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Messung der Atomarität — Wortlaut angepasst, nicht als Messung geliefert** ([TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0008](../../adr/0008-dotnet-kompatibilitaetstests.md)): Entscheid des Nutzers mit der Annahme von [ADR-0044](../../adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md) am 2026-11-08: die Zusage „ganz oder gar nicht“ gilt als Setzung über den gemessenen Fall M-12 hinaus (Schlüsselverstoß, drei Zeilen, ein Paket), die erweiterte Messung (erste, mittlere und letzte Zeile, zweite von drei Nachrichten, mehrere Pakete, mehrere Verstöße; Einzelmessung je Instanz 2022 und 2025, Referenzclient, nicht mssql-jdbc) ist kein Liefer-Punkt mehr, sondern Auslöser von Re-Evaluierungs-Trigger 1 der ADR. **Nicht geliefert:** keine Zeile im Messprotokoll über M-12 hinaus. **Beleg:** [ADR-0044](../../adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md) (Abschnitte „Akzeptiertes Negativ“, „Re-Evaluierungs-Trigger“ 1, Geschichte 2026-11-08: „erweiterte Messung folgt als Trigger“); die Messung bleibt offen im Register.
- [x] **ADR `Proposed`** ([TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)): Der Architect schreibt die ADR aus der ADR-Vorlage (`.harness/baseline/v6.18.1/templates`): Optionen mindestens A (die Nachricht ist eine Anweisung mit atomarer Übernahme, wie der echte Server) und B (Teilbestand bleibt wie in [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 5 (iii) und Nr. 6, Abweichung als Setzung), dazu die Frage, was der Stapel-Callback des Integrators bei Fehler zurückgibt, und die Zuordnung der Fehlercodes je Fehlerursache (Schlüsselverstoß, Typfehler, NULL in nicht nullbarer Spalte, volle Zellgrenze des Bestands, Fehler im Code des Integrators): der Bau von [slice-schreibpfad-bulk-load](../done/slice-schreibpfad-bulk-load.md) meldet heute jede Ausnahme des Callbacks als 2627 mit dem Text eines Schlüsselverstoßes (Setzung, Verifikation V-4), und diese Zuordnung hat über die Closure dieses Slice hinaus keinen anderen Träger; wo der echte Server messbar andere Nummern nennt, tragen die Zeilen des ersten Liefer-Punkts sie, sonst bleibt die Zuordnung eine benannte Setzung der ADR; Empfehlung auf Grundlage der Messung; `Supersedes (teilweise)`: [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md); `Schärft:`-Feld; Folgepflicht-Liste; Eintrag im ADR-Index. Beleg: `make docs-check`. — **Beleg:** [ADR-0044](../../adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md) (Optionen A bis D und K-a bis K-c, `Supersedes (teilweise)` [ADR-0033](../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) Nr. 5 (iii) und Nr. 6 wörtlich, `Schärft:`, Folgepflichten), Eintrag im ADR-Index. Die Frage nach dem Rückgabewert des Stapel-Callbacks beantwortet Entscheidung 1 (er wirft bei Fehler, ohne eine Zeile übernommen zu haben), die Zuordnung der Fehlercodes Entscheidung 3 (Sammelcode 2627 als benannte Grenze, Setzung).
- [x] **Entscheid und Nachzug** ([TDS-FA-12](../../../../spec/lastenheft.md#tds-fa-12--testserver-mit-veränderlichem-arbeitsspeicher-bestand-und-prüfclient-d-migrate)): Der Nutzer entschied am 2026-11-08 (Option A, Sammelcode als Grenze). Der Nachzug des Baus geschieht nicht an der geschlossenen Datei [slice-schreibpfad-bulk-load](../done/slice-schreibpfad-bulk-load.md) (sie liegt in `done/`), sondern im Bau-Slice [slice-massenladen-atomar-bauen](../open/slice-massenladen-atomar-bauen.md) (Wortlaut in `SPEC-035` und `SPEC-036`, Entfernen des Einzelzeilen-Rückfalls, Tests; Start nach [slice-ddl-abschluss-und-transaktionszustand-angleichen](../done/slice-ddl-abschluss-und-transaktionszustand-angleichen.md)), wie [ADR-0044](../../adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md) Entscheidung 4 es vorsieht; der Stand des Registers ist fortgeschrieben. **Beleg:** ADR-Status `Accepted`, Datei des Bau-Slice in `open/`, `state.md` des Registers.

Nachweis-Pflichten für alle Punkte:

- [x] `make gates` grün. — *reine Dokumentation: nach der Tabelle der Änderungsart gilt `make docs-check` (grün); ein Lauf von `make gates` fand nicht statt.*
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Haken der Liefer-Punkte setzt der Planner nach der Verifikation, nicht der Implementer (`BEO-TDS/haken-vor-verifikation-gesetzt`).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist. — kein öffentlicher Vertrag berührt (ADR und Planung).
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert. — Stand von `BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand` fortgeschrieben; keine neue Beobachtung angefallen.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo **ohne** Wellen-Betrieb hier geprüft, im Repo **mit** Wellen von der nächsten Welle-Closure (auch für Slices ohne Wellen-Zugehörigkeit).

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

- Die Erweiterung der Messung kann M-12 nicht bestätigen (etwa Teilbestand bei Fehler in der Mitte einer Nachricht mit mehreren Paketen); dann ändert sich die Option, nicht der Slice. — **Ausgang:** weiter offen: → `BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand` im Register; die Messung ist nicht gemacht, die ADR führt die Zusage als Setzung mit Re-Evaluierungs-Trigger 1.
- Die Messung trägt den Referenzclient (`SqlBulkCopy`), nicht mssql-jdbc; der Treiber von d-migrate kann eine andere Nachrichtenfolge senden ([callback-loesung-spike](../../../messungen/callback-loesung-spike-2026-11-05.md)); die ADR trägt die Grenze im selben Satz. — **Ausgang:** weiter offen: [ADR-0044](../../adr/0044-massenladen-nachricht-ist-atomar-wie-am-echten-server.md) trägt die Grenze „Referenzclient, nicht mssql-jdbc“ (Re-Evaluierungs-Trigger 2); der Träger ist [slice-abnahme-d-migrate](../open/slice-abnahme-d-migrate.md), der Lauf von d-migrate gegen den Testserver.
- [slice-schreibpfad-bulk-load](../done/slice-schreibpfad-bulk-load.md) kann vor dem Entscheid in `in-progress/` stehen (anderer Zweig); dann gilt Setzung des Planners: das Verhalten bei Fehler wird zurückgestellt oder als Abweichung gekennzeichnet. — **Ausgang:** entfallen: [slice-schreibpfad-bulk-load](../done/slice-schreibpfad-bulk-load.md) liegt in `done/` und hat das Verhalten bei Fehler dem Entscheid überlassen; der Nachzug liegt im Bau-Slice [slice-massenladen-atomar-bauen](../open/slice-massenladen-atomar-bauen.md).

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

- **Was hat funktioniert:** Die ADR trennt scharf, was gemessen ist (ein Fall, M-12) und was Setzung (Atomarität darüber hinaus, Fehlercode für andere Ursachen als den Schlüsselverstoß), und nimmt den Preis als „akzeptiertes Negativ“ in den Text; der Entscheid des Nutzers (Option A, Sammelcode als Grenze) deckt sich mit der Empfehlung. Die Option „Bibliothek erzwingt die Atomarität“ ist mit Grund verworfen statt übergangen.
- **Was ging anders als geplant:** (a) **Liefer-Punkt 1, die erweiterte Messung, wurde nicht gemacht.** Die ADR wurde mit der Messung als Trigger angenommen (Geschichte 2026-11-08, Entscheid des Nutzers); der Wortlaut von Punkt 1 ist in §2 auf diesen Entscheid angepasst und trägt „nicht geliefert“. (b) Der Nachzug von [slice-schreibpfad-bulk-load](../done/slice-schreibpfad-bulk-load.md) ist ein eigener Bau-Slice, nicht eine Änderung an der geschlossenen Datei. (c) Kein Review-Report: der Review-Haken der DoD bleibt offen, `Accepted` liegt durch Nutzerentscheid vor; ein Review des ADR-Textes ist nicht durchgeführt worden. (d) Der Start-Trigger in §4 („erfüllt mit der Closure dieses Slice“) bezog sich auf die Messung des Vorgängers und ist mit der Closure gegenstandslos.
- **Gegenstand:** teilweise: die erweiterte Messung entfällt als Bedingung und bleibt als offener Punkt im Register (Trigger 1 der ADR), der Nachzug geht an [slice-massenladen-atomar-bauen](../open/slice-massenladen-atomar-bauen.md).
- **Steering-Loop-Eintrag:** Nichts verkörpert; kein `liegt in`, gezählt wird. Benannt: Ein ADR-Slice mit einem Messpunkt in der DoD, den der Nutzerentscheid überholt, braucht beim Schließen den angepassten Wortlaut mit Beleg des Entscheids statt eines stillen Hakens; der Fall ist hier einmal aufgetreten, eine Regel entsteht daraus nicht.
- **Beobachtungs-Register (`../observations/`):** `state.md` von `BEO-TDS/folge-adr-zu-adr-0033-massenladen-verstoss-und-teilbestand` fortgeschrieben (ADR `Accepted`, Bau-Slice, Messung offen mit Trigger 1); keine neue Beobachtung angefallen.
- **Folge-Slices:** [slice-massenladen-atomar-bauen](../open/slice-massenladen-atomar-bauen.md) (Zusage am Stapel-Callback, Spezifikation, Tests, Doku) — ist eine Datei in `open/`.
- **Risiken aus §6:** zwei weiter offen mit Träger im Register beziehungsweise in [slice-abnahme-d-migrate](../open/slice-abnahme-d-migrate.md), eines entfallen; Ausgänge in §6.
- **Drei Paarungen:** Anker: kein `liegt in` in diesem Slice, nichts zu paaren; Folge-Slice: Datei in `open/` vorhanden; Register: Stand fortgeschrieben. Prüfung der Welle-Closure vorbehalten.

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
