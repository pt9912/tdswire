# Slice slice-mssql-jdbc-gegen-testserver: mssql-jdbc gegen den Testserver — NBCROW und Mehrfach-RPC messen

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle (keine Closure-Bedingung außerhalb der DoD dieses Slice; Folge-Messauftrag aus [slice-antwortform-nbcrow](../done/slice-antwortform-nbcrow.md) und [slice-mehrfach-rpc-unterstuetzen](../done/slice-mehrfach-rpc-unterstuetzen.md) nach Nutzerentscheidung vom 2026-11-17: „ok 2 slices“).

**Bezug:** [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) (Zeilenform), [ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md) (Mehrfach-RPC), [ADR-0036](../../adr/0036-handle-freigabe-und-grenze-je-sitzung.md) (Vorlage der Treibermessung), [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) (E2E nicht in den Gates)

**Berührte Spec-Stellen:** [SPEC-003](../../../../spec/spezifikation.md#spec-003--typmapping-initialer-mindestkatalog) (Absatz Zeilenform, NBCROW) · [SPEC-030](../../../../spec/spezifikation.md#spec-030--rpc-varianten-und-parameter-metadaten) (Abschnitt „Mehrere RPCs in einer Nachricht“). Die Spezifikation ändert dieser Slice nur dort, wo die Messung eine Setzung belegt; bei Widerspruch gilt die Frage „Code angleichen / Zielbild anpassen“ an den Nutzer.

**Verantwortlich:** —

**Autor:** pt9912 (Entwurf in der Planner-Rolle). **Datum:** 2026-11-17.

---

## 1. Ziel und Abgrenzung

**Ziel:** mssql-jdbc 13.4.0 (der Treiber, den d-migrate 1.12.0 mitbringt, siehe `tools/treiber-handles.sh`) ist gegen den **gebauten Testserver** dieses Repos **und gegen die gepinnten Instanzen des echten SQL Server (2022, 2025)** für zwei Aussagen gemessen, die bisher nur für den Referenzclient `Microsoft.Data.SqlClient` 7.1.1 gelten: (a) **NBCROW**: liest der Treiber Zeilen mit NULL in der Form, die der Sequenzierer wählt ([slice-antwortform-nbcrow](../done/slice-antwortform-nbcrow.md)), und ist das Ergebnis gleich dem Wert am echten Server; (b) **Mehrfach-RPC**: gelingt `addBatch`/`executeBatch` gegen den Testserver, und welche Wirkung und Antwortform ergeben sich im Vergleich zum echten Server ([slice-mehrfach-rpc-unterstuetzen](../done/slice-mehrfach-rpc-unterstuetzen.md)). Gemessen werden Wirkung und Antwortform, nicht Durchsatz.

**Entscheidung des Nutzers (2026-11-17):** Der JDBC-Lauf ist ein **eigenes Nicht-Gate-Target neben `make e2e`** (Vorlage `make messung-treiber-handles`), keine Stufe von `make e2e`. Er steht in keinem Fall in `GATE_CHECKS` ([ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)). Der Lauf gegen die Instanzen des echten Servers ist ein zweiter Teil mit Mechanik wie `make vergleichslauf-d` (nur lokal, EULA des Nutzers vom 2026-11-01 für das gepinnte Developer-Image, nie in der CI).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Durchsatz, Latenz, Last — andere Gegenstände (`make lasttest`, Welle `welle-qualitaet-grundlage`); hier zählt, was der Treiber liest und was er sendet.
- Andere Clients als mssql-jdbc 13.4.0 — in diesem Plan sind **keine** weiteren benannt (ODBC, FreeTDS, `sqlcmd` und andere Versionen von mssql-jdbc haben keinen Träger); die Liste wird nicht erfunden, ein weiterer Client ist ein eigener Slice.
- d-migrate selbst (Lauf der Anwendung) — [slice-abnahme-d-migrate](../done/slice-abnahme-d-migrate.md) und die Messprotokolle zu d-migrate sind der Ort; dieser Slice misst den Treiber allein, wie `make messung-treiber-handles` (ob d-migrate dieselben Aufrufe sendet, ist damit nicht belegt).
- Ein Lauf des Treibers gegen andere Instanzen als die gepinnten (2022, 2025) und gegen TLS-/TDS-8.0-Pfade — der Plan misst die Referenzinstanzen der Reihe `wire`; weitere Instanzen und Pfade haben keinen Träger.
- Änderungen an `src/` und `testserver/` ohne Messbefund — Code nur, wenn die Messung eine Zusage widerlegt und der Nutzer „Code angleichen“ entschieden hat; das wäre ein eigener Slice.
- Die Messlücken am echten Server zum Mehrfach-RPC (Fehler an den Rändern, Ergebnismengen, Grenzen) — [slice-mehrfach-rpc-messluecken](../done/slice-mehrfach-rpc-messluecken.md).
- Der Flake `ServerSmokeTest` — Register (`BEO-TDS/flake-ohne-namen-des-roten-testfalls`), kein Bezug zu einem Treiber.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; Gate-Läufe und Closure-Pflichten zählen nicht mit.

- [ ] **NBCROW mit mssql-jdbc:** Der Treiber liest Ergebnisse mit NULL-Zeilen in beiden Formen (ROW und NBCROW, auch mit neun und mehr Spalten, wo die Bitmap mehr als ein Byte trägt) **am Testserver und an den Instanzen 2022 und 2025** und vergleicht die gelesenen Werte (`getObject`, `wasNull`) mit der erwarteten Zeile; die Messzeile nennt je Fall und je Server die Zeilenform (am Draht gezählt wie in `tools/treiber-handles/Messung.java`) und das Ergebnis; Unterschiede zwischen Testserver und echtem Server stehen als Befund.
- [ ] **Mehrfach-RPC mit mssql-jdbc:** `addBatch`/`executeBatch` eines `PreparedStatement` am Testserver **und an den Instanzen 2022 und 2025**: gemessen sind Update-Zählung, Verbindung danach, ob der Treiber mehrere RPCs in **einer** Nachricht sendet (am Draht gezählt) und die Antwortform; die Aussage trägt für den Treiber allein, nicht für d-migrate.
- [ ] **Träger der Läufe:** zwei Werkzeuge mit je Skript und Make-Fragment, beide Nicht-Gate, Zeile in `harness/README.md`, Bindung auf [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md): ein **eigenes Target neben `make e2e`** für den Lauf am Testserver (Entscheidung des Nutzers 2026-11-17, Vorlage `make messung-treiber-handles`) und ein Lauf an den gepinnten Instanzen mit der Mechanik von `make vergleichslauf-d` (nur lokal, EULA-Schalter, bricht in der CI ab). Kein Eintrag in `GATE_CHECKS`, keine Stufe von `make e2e`; Selbsttest des Werkzeugs nach dem Muster der Nachbarn, soweit `make skript-selbsttest` es für diese Art Werkzeug vorsieht.
- [ ] `make docs-check`, `make flaeche`, `make kopplung-zahlen`, `make skript-selbsttest` und `make gates` Exit 0 (Doku/Spec, Werkzeug; `harness/README.md` §Minimal agent workflow). Der neue Lauf selbst: Messung, kein Gate.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor (`.harness/skills/reviewer.md`), kein Self-Review.
- [ ] Doku-Update: Messprotokoll unter `docs/messungen/`, `SPEC-003` und `SPEC-030` (Client-Einschränkung belegt oder weiter Setzung), Kompatibilitätsmatrix (Zeile NBCROW, Mehrfach-RPC), `harness/README.md` (Zeile des Laufs).
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben, u. a. `BEO-TDS/festlegung-gilt-nur-fuer-das-clientprofil`; keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — Pfad-Kandidaten für §8; geprüft ist das Vorhandensein der Dateien am 2026-11-17.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `tools/treiber-handles.sh`, `tools/treiber-handles/Messung.java`, `tools/mk/treiber-handles.mk` | Vorlage, lesen | Mechanik: Image des Testservers bauen und starten, Treiber und JRE aus dem Image von d-migrate (Digest im Skript), `Messung.java` mit dem JDK des Gradle-Images übersetzen, TCP-Durchlauf zählt die Nachrichten des Clients; Exit 1 bei verfehlter Erwartung |
| neues Skript und Messprogramm für die JDBC-Messung (Pfad: Vorschlag `tools/jdbc-zeilen-batch.sh` mit `tools/jdbc-zeilen-batch/Messung.java`; Name im ersten Lauf zu bestätigen) | neu | Reihen (a) NBCROW, (b) Mehrfach-RPC; der Durchlauf liest hier auch die Antwort des Servers (Zeilenform), nicht nur die Nachrichten des Clients |
| `tools/mk/<neu>.mk` (eigenes Fragment) | neu | Targets für beide Läufe; `tools/e2e.sh` und `tools/mk/e2e.mk` bleiben unberührt (keine Stufe); `tools/mk/vergleichslauf-d.mk` und `tools/vergleichslauf-d.sh` sind Vorlage für den EULA-Schalter, den Abbruch in der CI und den Start der Instanzen (Dockerfile-Stages `referenzinstanz-2022`, `referenzinstanz-2025`) |
| `harness/README.md` | update | Zeile des Laufs in der Tabelle der Werkzeuge (Bindung [ADR-0022](../../adr/0022-e2e-laeufe-nicht-in-den-gates.md)) |
| `docs/messungen/<name>-<datum>.md` | neu | Messprotokoll, Einzelmessung mit Datum, Host, Bedingung (`AGENTS.md` §5 Regel 7) |
| `spec/spezifikation.md` (`SPEC-003`, `SPEC-030`), `docs/plan/kompatibilitaetsmatrix.md` | update | die Aussage „anderer Client ungemessen“ trägt höchstens so weit wie die Messzeile (AGENTS §3.8) |
| `docs/plan/e2e-abdeckung.md` | nicht ändern | keine Stufe; der Lauf steht nicht in `make e2e` |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf (Test- und Skript-Selbsttestdateien mit Kennung).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`open` → `next` → `in-progress`): Nutzerzeichen und ein Verantwortlicher (`Verantwortlich:` ist bis dahin `—`). Voraussetzung am Host: Docker mit Netz für den Pull des Images von d-migrate, falls es lokal fehlt (wie `make messung-treiber-handles`), und für den zweiten Teil die gepinnten Instanzen 2022 und 2025 (Pull, Platz; EULA vom Nutzer bestätigt 2026-11-17, Mechanik und Schalter wie im Kopf von `tools/mk/vergleichslauf-d.mk`). Der zweite Teil läuft **nicht parallel** zu `make lasttest` und zu Messslices der Welle `welle-qualitaet-grundlage`.

**Rückführungen — vorab benennen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert): das Image von d-migrate ist nicht mehr beziehbar (Digest im Skript), oder die gepinnten Instanzen sind nicht beziehbar (Pull, Platz, geänderter EULA-Text).

## 5. Closure-Trigger

Alle Punkte der DoD in §2 erledigt, `make gates` und `make docs-check` Exit 0, das Messprotokoll liegt unter `docs/messungen/`, und die Closure-Notiz mit Steering-Loop-Lerneintrag ist geschrieben.

## 6. Risiken und offene Punkte

- Der Treiber allein ist nicht d-migrate: d-migrate kann andere Aufrufe senden (andere Optionen, andere Anweisungsformen). — **Ausgang:** offen bis zur Closure; Grenze steht im Messprotokoll und in Spec und Matrix (`BEO-TDS/festlegung-gilt-nur-fuer-das-clientprofil`).
- Der Treiber sendet am echten Server andere Aufrufe als am Testserver (Server-Prepare, Optionen je Version) oder bekommt andere Antworten; der Vergleich ist dann ein Befund, kein Fehler des Laufs. — **Ausgang:** offen bis zur Closure; Behandlung eines Widerspruchs zur Spec nur nach Nutzerentscheid (Code angleichen / Zielbild anpassen).
- Der Treiber sendet bei `executeBatch` möglicherweise nicht mehrere RPCs in einer Nachricht (andere Optionen, `useBulkCopyForBatchInsert`, Server-Prepare); dann ist (b) mit der gemessenen Form des Treibers beantwortet, nicht mit der erwarteten. — **Ausgang:** offen bis zur Closure; die Messzeile nennt, was gesendet wurde.
- Ein Befund widerlegt eine Zusage ([TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [ADR-0051](../../adr/0051-mehrere-rpcs-in-einer-nachricht-werden-beantwortet.md)). — **Ausgang:** offen bis zur Closure; Behandlung nur nach Nutzerentscheid (Code angleichen: Folge-Slice; Zielbild anpassen: Folge-ADR).
- Der Lauf am echten Server ist Einzelmessung je Instanz, ein Host, ein Treiber in einer Version, nur lokal (nicht in der CI, nicht reproduzierbar durch Dritte ohne EULA). — **Ausgang:** offen bis zur Closure; Grenzen stehen im Messprotokoll.
- Größe: drei Liefer-Punkte. — **Ausgang:** offen bis zur Closure; Rückführung nach §4.

## 7. Closure-Notiz

Wird bei der Closure gefüllt.

**Herkunft:** [slice-antwortform-nbcrow](../done/slice-antwortform-nbcrow.md) §7 „Weiter offen“ Punkt 1 (mssql-jdbc gegen NBCROW dieses Servers ungemessen) und Verifikation [2026-11-17-853239d5](../../../reviews/2026-11-17-853239d5-verifikation-slice-antwortform-nbcrow.md) V-1 (`make e2e` enthält keinen JDBC-Lauf); [slice-mehrfach-rpc-unterstuetzen](../done/slice-mehrfach-rpc-unterstuetzen.md) §7 „Weiter offen“ Punkt 2 (anderer Client, mssql-jdbc) und Messprotokoll [zeilenform-nbcrow-2026-11-17](../../../messungen/zeilenform-nbcrow-2026-11-17.md). Nutzerentscheidung 2026-11-17: „ok 2 slices“.

## 8. Sub-Area-Prüfungen und Modus-Begründung

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`) gegen die Verzeichnisliste gelesen; Treffer: `festlegung-gilt-nur-fuer-das-clientprofil` (beim Nutzer entschieden offen; dieser Slice liefert die Messung für einen zweiten Client), `aussagen-ueber-messung-hinaus` (verkörpert, AGENTS §3.8), `messung-am-echten-sql-server-fehlt`. Zähler-Stände stehen in den Dateien des Registers, nicht hier.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
