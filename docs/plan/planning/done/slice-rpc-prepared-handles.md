# Slice slice-rpc-prepared-handles: Prepared-Statements: sp_prepexec, sp_execute und Handle-Tabelle je Sitzung

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-anfrageverarbeitung

**Bezug:** [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0004](../../adr/0004-session-zustandsautomat.md), [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md)

**Berührte Spec-Stellen:** SPEC-030 · SPEC-001 · ARC-004 · ARC-005 · [TDS-FA-04.a](../../../../spec/spezifikation.md#tds-fa-04a--rpc-und-parameter) · [TDS-FA-10.a](../../../../spec/spezifikation.md#tds-fa-10a--mehrteilige-antworten-und-rpc-ausgabewerte)

**Verantwortlich:** pt9912.

**Autor:** pt9912. **Datum:** 2026-10-30.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Die Sitzung verwaltet Prepared-Statements nach [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md): `sp_prepexec` führt aus und liefert ein sitzungsbezogenes Handle als RETURNVALUE, `sp_execute` führt das gemerkte Statement mit neuen Parameterwerten aus, die Zahl der Handles je Sitzung ist begrenzt, ein unbekanntes Handle und `sp_unprepare` ergeben einen definierten Fehler, und die Handles enden mit der Sitzung ([TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc)).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- `sp_executesql` und der RPC per Prozedurname — `slice-rpc-executesql-parameter`; dieser Slice nutzt dessen Parameterbindung.
- Die RPC-Antwortform (RETURNSTATUS, RETURNVALUE, DONEPROC) — `slice-antwort-mehrteilig-output`; das Handle ist dort ein OUTPUT-artiges RETURNVALUE, das der Dispatch einspeist, nicht das Backend.
- `sp_unprepare` unterstützen, `sp_prepare`, `sp_cursor*` — nicht im Katalog von [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md); ein gepoolter Client kann `sp_unprepare` senden, der Katalog beantwortet es mit einem Fehler. Ob das unter Pooling genügt, ist nicht gemessen (Träger `slice-qa-kompatibilitaet`, `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`).
- `sp_reset_connection` und das Verhalten der Handle-Tabelle beim Zurücksetzen der Sitzung — offen in [ADR-0004](../../adr/0004-session-zustandsautomat.md) und [ADR-0007](../../adr/0007-fehler-und-attention.md) (`RESETCONNECTION`); der Slice legt nichts fest, was beide offen lassen.
- Übersetzung eines Prepared-Statements in eine Backend-spezifische Vorbereitung (serverseitiges Prepare im Backend) — der Server hält Text und Deklaration und ruft das Backend bei jedem `sp_execute` wie bei einer Anfrage; ein Backend-Prepare wäre eine Änderung des Port-Vertrags ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [x] **Handle-Tabelle je Sitzung** ([TDS-FA-04.a](../../../../spec/spezifikation.md#tds-fa-04a--rpc-und-parameter), SPEC-030, [ADR-0004](../../adr/0004-session-zustandsautomat.md)): Vergabe, Nachschlagen und Ende mit der Sitzung; der Zugriff läuft unter dem Sitzungsschutz des Zustandsmodells von [ADR-0004](../../adr/0004-session-zustandsautomat.md); zwei Sitzungen sehen die Handles der jeweils anderen nicht (Test); die Anzahl je Sitzung ist begrenzt, das Überschreiten ergibt den definierten Fehler und lässt die Verbindung offen. Der Grenzwert (1000 je Sitzung) ist eine Setzung laut [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md) (`Accepted`) und nicht gemessen; die Vergabe der Handle-Werte ist eine Festlegung des Nutzers (§6) und nicht gemessen.
- [x] **`sp_prepexec` und `sp_execute`** ([TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc) Happy Path, [TDS-FA-10.a](../../../../spec/spezifikation.md#tds-fa-10a--mehrteilige-antworten-und-rpc-ausgabewerte)): `sp_prepexec` (Parameter 0 OUTPUT-Handle mit −1 beim Erstaufruf, Deklaration, Text) führt über das Test-Backend aus und liefert das Handle als RETURNVALUE mit Ordinal 0 und `INTN` 4 (so beantwortete es der Spike-Testserver; für einen echten Server nicht gemessen); `sp_execute` (Parameter 0 Handle) ruft das Test-Backend mit dem gemerkten Text und den neuen Werten, ein zweiter und dritter Aufruf mit anderen Werten liefern je das Ergebnis; NULL-Parameter und OUTPUT folgen den Regeln aus `slice-rpc-executesql-parameter`.
- [x] **Definierte Fehler** ([TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc) Negative, [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md)): ein unbekanntes Handle, ein bereits wieder entferntes Handle (nach Sitzungsende nicht erreichbar), `sp_unprepare`, `sp_prepare` und das Überschreiten der Handle-Grenze ergeben je einen definierten Fehler ohne Verbindungsabbruch; ein Folge-RPC auf derselben Verbindung wird beantwortet (Setzung, nicht gemessen).

Nachweis-Pflichten für alle Punkte:

- [x] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>` (Konvention nach `slice-qa-spec-abdeckung`); die Tests laufen in `make test` und sind grün.
- [x] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet (KDoc des Elements). Die Zuordnung *Test | Festlegung* je Aussage steht in der Closure-Notiz. Die Tests laufen gegen ein Test-Backend, das den Backend-Port implementiert, nicht gegen einen echten SQL Server und nicht gegen den Referenzclient; Aussagen über deren Verhalten trägt dieser Slice nicht.
- [x] `make gates` grün.
- [x] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [x] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [x] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [x] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [x] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [x] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo mit Wellen von der Welle-Closure geprüft.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/` (Unterpackage `dispatch`) <!-- d-check:ignore (geplant: neues Package) --> | update | Handle-Tabelle der Sitzung, `sp_prepexec`- und `sp_execute`-Pfad, Fehlerfälle (ARC-004, ARC-005); importiert Domain, Backend-Port, Parser-Typen |
| `spec/spezifikation.md` `SPEC-021` | update | Festlegung: Handle-Grenze, Verhalten bei `sp_unprepare` und unbekanntem Handle, ohne Zusage über Pooling |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/` (Unterpackage `dispatch`) <!-- d-check:ignore (geplant: neues Package) --> | neu | Handle-Lebenszyklus, Isolation zweier Sitzungen, Grenze, Folge-RPC nach Fehler, Parallelzugriff auf die Tabelle einer Sitzung; nach [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc); `@spec`-Tag je Test |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

Erweiterung des Implementers (Ist-Zustand gegen den Plan gemessen):

- Drift: die Festlegung steht in `SPEC-030` (so schärft es [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md)), nicht in `SPEC-021`; die zwei neuen Fehlercodes stehen in Abschnitt 4 der Spezifikation als `SPEC-057` und `SPEC-058` (die Nummer 053 vergibt der parallele Zweig der Anmeldedrosselung, 054 bis 056 bleiben bewusst frei für dessen weitere Zeilen; die Lücke ist eine Setzung gegen eine Kollision beim Zusammenführen, kein Zählfehler).
- Drift: [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md) steht auf `Accepted` (2026-10-31); DoD und Risiken nennen in der Closure den Zustand `Accepted`.
- Neu im Quelltext: `PreparedHandles` (Tabelle mit Mutex je Sitzung), `HandleReturningBackend` (speist das Handle ein), `RpcBinding.Prepare` und `RpcBinding.ExecutePrepared` in `RpcBinder`, Pfade `prepare` und `executePrepared` in `RequestLoop`, `ErrorCode.TDS_UNKNOWN_HANDLE` und `ErrorCode.TDS_PREPARE_LIMIT`; `RpcRejection.NOT_BUILT` entfällt.
- Berührt außerdem: `docs/plan/kompatibilitaetsmatrix.md` (Zeilen `sp_prepexec`, `sp_execute`, Handle-Verwaltung, Lücke 13).

- Reihenfolge: Tabelle mit Grenze, dann `sp_prepexec`/`sp_execute`, dann die Fehlerfälle.
- Der Nachweis, dass der Referenzclient `Prepare()` mit diesem Server besteht, gehört nicht hierher; er ist `slice-qa-kompatibilitaet`.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `slice-rpc-executesql-parameter` und `slice-antwort-mehrteilig-output` liegen in `done/`, [ADR-0004](../../adr/0004-session-zustandsautomat.md) steht im ADR-Index auf `Accepted` (Sitzungsschutz und Sitzungsende), [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md) (Grenzwert der Handles je Sitzung) steht im ADR-Index auf `Accepted`, und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): [ADR-0004](../../adr/0004-session-zustandsautomat.md) bleibt `Proposed`, oder [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md) bleibt `Proposed`.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün (einschließlich `make test`), der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung *Test | Festlegung* je Aussage sowie den Steering-Loop-Lerneintrag (geschärfte Regel, neuer Sensor oder benannte Spec-Lücke; eine Closure ohne Lerneintrag ist keine). Gemessen ist in diesem Slice nur, was ein Test gegen das Test-Backend belegt; was der Referenzclient oder ein echter SQL Server sendet oder annimmt, ist nicht gemessen und bleibt bei `slice-qa-kompatibilitaet` und `slice-qa-reproduzierbarkeit`.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Der Grenzwert der Handles je Sitzung und die Vergabe der Handle-Werte (Zähler je Sitzung ab 1, Wiederverwendung) sind weder gemessen noch in [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) festgelegt ("zu begrenzen", Festlegung ohne Zahl). Der Grenzwert ist entschieden in [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md) (1000 je Sitzung, Setzung, nicht gemessen; Status `Accepted`). Die Vergabe der Handle-Werte bleibt Festlegung des Nutzers. — **Ausgang:** entfallen. Der Grenzwert ist per [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md) (`Accepted`) entschieden und per Test gepinnt (Grenze 1000, 1001. Handle abgelehnt); die Vergabe (Zähler je Sitzung ab 1, keine Wiederverwendung, der Wert des Clients bleibt unberücksichtigt) ist als Festlegung gekennzeichnet. Beides bleibt Setzung, nicht gemessen.
- Unter Pooling kann ein Client `sp_unprepare` oder `sp_reset_connection` senden; der Katalog beantwortet `sp_unprepare` mit einem Fehler, was ein gepoolter Client daraus macht, ist nicht gemessen (`BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`). Handles wachsen ohne `sp_unprepare` bis zur Grenze. — **Ausgang:** weiter offen, Träger `slice-qa-kompatibilitaet` (`BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, Evidence dieses Slice ergänzt). `sp_unprepare` ergibt 50002 und lässt das Handle gültig; die Handles wachsen bis zur Grenze.
- Das Handle als RETURNVALUE mit Ordinal 0 ist vom Testserver beantwortet und vom Client akzeptiert worden; die Zählung eines echten Servers ist nicht gemessen (`BEO-TDS/messung-am-echten-sql-server-fehlt`). — **Ausgang:** weiter offen, Träger `slice-qa-reproduzierbarkeit` (Vergleichslauf D, `BEO-TDS/messung-am-echten-sql-server-fehlt`, Evidence dieses Slice ergänzt).
- Der Zugriff auf die Handle-Tabelle hängt am Nebenläufigkeitsmodell von [ADR-0004](../../adr/0004-session-zustandsautomat.md) (`Mutex` je Sitzung, Setzung des Nutzers, `Accepted`); gleichzeitige Anfragen einer Sitzung ohne MARS sind nicht erwartet, aber ein ATTENTION während `sp_prepexec` ist Gegenstand der Welle `welle-fehler-abbruch`. — **Ausgang:** weiter offen. Der Zugriff läuft über einen eigenen `Mutex` je Tabelle (siehe §7, Lesart zu F-3); der Parallelzugriff ist getestet. Ein ATTENTION während `sp_prepexec` ist ungetestet und Gegenstand von `welle-fehler-abbruch`.
- Wiederholtes `sp_prepexec` mit demselben Text (und mit altem Handle) vergibt je Aufruf ein neues Handle und belegt Plätze bis zur Grenze von 1000, `sp_unprepare` gibt nichts frei ([ADR-0017](../../adr/0017-prepared-handles-grenzwert.md) „Negativ“, in SPEC-030 benannt). — **Ausgang:** eingetreten als Setzung (die Spezifikation benennt es, Verifikation F-4); ob ein gepoolter Client daran stößt, ist nicht gemessen (`BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`).
- Hält der Server den SQL-Text der Handles, kann ein Handle viel Speicher binden (MAX-Text); die Größe je Handle ist nicht begrenzt in [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md). — **Ausgang:** weiter offen. Die Textgröße je Handle bleibt ungrenzt; dazu tritt das Re-Prepare (siehe unten) als Hebel auf die Zahl der Plätze.

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

- **Was hat funktioniert:** Der Schnitt trug, die Rückführung nach §4 war nicht nötig; alle drei Liefer-Punkte sind im Code bestätigt. Der Review (b19b914) fand drei MEDIUM (Spec-Absatz mitten im Satz, `NonCancellable` ohne Test, „kein Handle bei Fehler“ nur vor dem ersten Ausgabewert), die Fix-Runde 6e481cd behob sie; der Verifier (6e481cd) fuhr `make gates` (799 Tests PASSED) und drei Wächter-Mutationen (M1 „gesendet heißt gültig“, M2 Grenze 1001, M3 `clear()` am Sitzungsende), alle rot, und fand kein HIGH und kein MEDIUM; sein LOW F-2 (`returned` am Stromende) behob 63c3e50.
- **Was ging anders als geplant:** (a) Die Festlegung steht in SPEC-030 statt SPEC-021, die Fehlercodes 50009/50010 als SPEC-057/SPEC-058 (§3). (b) Die SPEC-ID-Lücke 054 bis 056 (F-5) ist eine Setzung gegen eine Merge-Kollision: SPEC-053 vergibt der parallele Zweig der Anmeldedrosselung, SPEC-059 der Einstieg; die Lücke bleibt, sie ist kein Zählfehler und wird nicht gefüllt. (c) Der Plan nannte `Proposed` für zwei inzwischen angenommene ADRs (F-1); der Wortlaut ist in dieser Closure auf `Accepted` gezogen. (d) Die Fix-Runde stammt vom Implementer; die Mutationen M1 bis M3 fuhr der Verifier, die Mutationen des Reviews der Reviewer. Ob der Parallel-Test mit Startschranke (6e481cd) die Mutation „Mutex entfernt“ stabil fängt, ist nicht gefahren (F-6).
- **Reports:** [Review](../../../reviews/2026-10-31-b19b914-slice-rpc-prepared-handles.md) gegen `b19b914` (0 HIGH, 3 MEDIUM, 3 LOW, 5 INFO) und [Verifikation](../../../reviews/2026-11-01-6e481cd-verifikation-slice-rpc-prepared-handles.md) am Stand `6e481cd` (0 HIGH, 0 MEDIUM, 6 LOW; Urteil: closure-fähig unter Bedingungen). Ein Validator-Lauf entfällt: der Slice liefert keinen Betriebsweg.

**Lesart zu F-3 (Sitzungsschutz), vom Planner entschieden, vom Nutzer unbestätigt.** [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md) Entscheidung 3 verlangt den Zugriff auf die Tabelle „unter dem Sitzungsschutz“ von [ADR-0004](../../adr/0004-session-zustandsautomat.md). Gebaut ist ein eigener `Mutex` in `PreparedHandles`, nicht der von `HandshakeSession`. Der Planner liest das als zulässig: Die Tabelle besteht je Sitzung (je `RequestLoop`), der Mutex schützt nur ihren Zustand und wird nie zusammen mit einem zweiten Sitzungsmutex gehalten, also ist weder Verschachtelung noch Deadlock möglich; Vergabe und Grenzprüfung laufen atomar im selben `withLock`. Die ADR bleibt unverändert (`Accepted`, AGENTS.md 3.5); eine Klarstellung per Folge-ADR ist möglich, wenn der Nutzer die Lesart nicht teilt. Die Lesart ist Setzung, nicht gemessen; sie steht als Evidence in `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt`.

**Zuordnung gemessen | Handvektor | Selbstkonsistenz | Festlegung | nicht gemessen je Aussage.** *Gemessen* heißt beobachtet in einem Lauf dieses Repos gegen das Test-Backend (eine Fixture des Backend-Ports) und den Sitzungsrahmen; **gegen den Referenzclient und einen echten SQL Server wurde nichts gemessen.** *Handvektor* heißt ein von Hand gezählter Vektor ohne externe Quelle. *Selbstkonsistenz* heißt Code gegen Code. *Festlegung* heißt Setzung ohne Messung.

| Stärke | Aussage | Beleg im Repo |
|---|---|---|
| gemessen (gegen das Test-Backend) | Handle-Tabelle je Sitzung: Vergabe, Nachschlagen, Ende mit der Sitzung; zwei offene Sitzungen sehen die Handles der anderen nicht; Grenze 1000 (das 1001. Handle abgelehnt, 1000 Aufrufe ohne Fehler, danach laufen `sp_execute(1)` und `sp_execute(1000)`); Vergabe und Grenzprüfung atomar unter paralleler Last | `PreparedHandlesTest`, `PreparedHandlesDispatchTest`; M2 und M3 des Verifiers rot |
| gemessen | `sp_prepexec` führt aus und liefert das Handle als RETURNVALUE; `sp_execute` ruft das Backend mit gemerktem Text und neuen Werten, mehrere Aufrufe mit anderen Werten; NULL und OUTPUT wie bei `sp_executesql`; `sp_executesql` vergibt kein Handle | `PreparedHandlesDispatchTest`, `HandleReturningBackendTest` |
| gemessen | Unbekanntes Handle (2, −1, 0): 50009, Class 16, Verbindung offen, Backend nicht gerufen; 1001. Handle: 50010, Class 16, vorhandene Handles bleiben gültig; `sp_unprepare` und `sp_prepare`: 50002, Handle bleibt gültig; ein Folge-RPC wird beantwortet | `PreparedHandlesDispatchTest` |
| gemessen | Ein Fehler nach dem gesendeten Handle lässt es gültig; ein Fehler vor dem Handle vergibt keines und verbraucht keinen Zählerwert | `PreparedHandlesDispatchTest`, `HandleReturningBackendTest`; M1 des Verifiers rot |
| Handvektor | RETURNVALUE des Handles mit Ordinal 0, `INTN` 4, Name `@handle` (Bytes `26 04 04`) | `HandleReturningBackendTest`; belegt die Bytes dieses Servers, nicht die eines echten |
| Selbstkonsistenz | Code, Class und Log-Marke je Ablehnung kommen aus `ErrorMapper` und `ErrorCode`; der Dispatch wird gegen diese Quelle geprüft, nicht gegen eine zweite | `PreparedHandlesDispatchTest` |
| Festlegung | Fehlercodes 50009 und 50010 (SPEC-057, SPEC-058); Name `@handle`; „gesendet heißt gültig“ (nach dem Handle-Ereignis bleibt das Handle auch bei Fehler gültig); Zähler je Sitzung ab 1, keine Wiederverwendung, der Wert des Clients (−1 oder altes Handle) bleibt unberücksichtigt; Grenze 1000 ohne Konfiguration ([ADR-0017](../../adr/0017-prepared-handles-grenzwert.md)); Re-Prepare belegt weitere Plätze; `sp_unprepare` gibt nichts frei; ein Folge-RPC nach einem Fehler wird beantwortet | KDoc von `PreparedHandles`, `HandleReturningBackend`; SPEC-030, SPEC-057, SPEC-058 |
| nicht gemessen | Das Handle-RETURNVALUE (Ordinal, Typ, Name) an einem echten SQL Server und am Referenzclient; Pooling, `sp_unprepare` und `sp_reset_connection` am Client; ATTENTION, Cancel oder Timeout während `sp_prepexec` (kein Test für den Abbruchpfad); die Mutation „Mutex entfernt“ am Parallel-Test (F-6); der Abschluss DONEPROC statt DONE ([ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) `Proposed`) | Register und Träger in der Tabelle unten |

**Offene Punkte der Closure mit Träger:**

| Offener Punkt | Träger | Stand |
|---|---|---|
| Handle-RETURNVALUE, Fehlerantwort und Abschluss am Referenzclient und an einem echten Server | Register `BEO-TDS/messung-am-echten-sql-server-fehlt` (*geplant*, `slice-qa-vergleichslauf-d`) | Evidence dieses Slice ergänzt |
| `sp_unprepare`, `sp_reset_connection` und TDS 8.0 unter Pooling | Register `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` (*geplant*, `slice-qa-kompatibilitaet`) | Evidence dieses Slice ergänzt |
| ATTENTION oder Abbruch während `sp_prepexec` | `welle-fehler-abbruch` | benannt in §6; kein Test in diesem Slice |
| Abschluss-Token DONE statt DONEPROC bei Fehler in der RPC-Antwort | [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) (`Proposed`), [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md) Folgepflicht 3 | Folgepunkt nach Entscheidung über [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md), nicht Teil dieser Closure |
| Textgröße je Handle ungrenzt | kein Träger; Hinweis für den Architect, bei einem Messbefund eine Grenze zu setzen | §6 |

- **Gegenstand:** entfällt (der Slice lieferte).
- **Steering-Loop-Eintrag:** Kein `liegt in`: mit diesem Slice ist keine Regel, kein Sensor und keine Gate-Zeile an einem Zielort verkörpert worden; gezählt wird, nicht verkörpert. Drei Feststellungen. (1) **Eine ADR nennt einen Mechanismus („unter dem Sitzungsschutz“), den der Slice anders einlöst, und der Wortlaut bleibt stehen:** die Lesart des Planners (siehe oben) ist Setzung; `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` (jetzt 11×, über der Schwelle, nicht verkörpert, Entscheidung beim Nutzer). (2) **Der Plan nannte den Status zweier ADRs, der sich vor dem Start geändert hatte:** `BEO-TDS/risikozeile-nennt-behobenen-zustand` (jetzt 5×, über der Schwelle, nicht verkörpert, Entscheidung beim Nutzer). (3) **Ein Eigencode-Zusatz (`NonCancellable`) trug keinen Test, und eine KDoc-Aussage war breiter als der Test:** `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (jetzt 13×, verkörpert) und `BEO-TDS/aussagen-ueber-messung-hinaus` (jetzt 41×, verkörpert) bleiben getragen; hier nur Evidence. **Benannte Spec-Lücke:** [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md) Folgepflicht 3 nennt die Fehlerfolge der RPC-Antwort nach DONEPROC, gebaut ist ERROR plus DONE mit Fehlerstatus; [ADR-0014](../../adr/0014-fehlerfolge-rpc-antwort.md) steht auf `Proposed`, der Nachzug ist ein Folgepunkt. Einzelfall ohne Klasse bisher: `BEO-TDS/test-tempdir-aufraeumen-race-unter-last` (neu, 1×, offen).
- **Beobachtungs-Register (`../observations/`):** `evidence/slice-rpc-prepared-handles.md` ergänzt in `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` (jetzt 16×, *geplant*), `BEO-TDS/messung-am-echten-sql-server-fehlt` (jetzt 24×, *geplant*), `BEO-TDS/aussagen-ueber-messung-hinaus` (41×), `BEO-TDS/lesart-einer-nutzerentscheidung-unbestaetigt` (11×), `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (13×) und `BEO-TDS/risikozeile-nennt-behobenen-zustand` (5×). Eine neue Beobachtung mit dieser Evidence-Datei: `BEO-TDS/test-tempdir-aufraeumen-race-unter-last` (1×, offen; der Flake `SimulationWiringTest > ein Symlink …`, einmal in zehn Läufen). **Über der Schwelle ohne Ausgang, Entscheidung beim Nutzer:** `lesart-einer-nutzerentscheidung-unbestaetigt` und `risikozeile-nennt-behobenen-zustand`; beide bleiben unverkörpert. Die Zähler folgen aus den Dateien.
- **Risiken aus §6:** alle sechs tragen genau einen Ausgang (siehe §6): einer entfallen (Grenzwert und Vergabe, per [ADR-0017](../../adr/0017-prepared-handles-grenzwert.md) entschieden), einer eingetreten als Setzung (Re-Prepare belegt Plätze, F-4), vier weiter offen (Pooling, echter Server, ATTENTION während `sp_prepexec`, Textgröße je Handle). Kein Risiko verlangt einen Carveout.
- **Drei Paarungen:** Anker: kein Eintrag trägt `liegt in`, die Paarung entfällt. Folge-Slice: `slice-qa-vergleichslauf-d` und `slice-qa-kompatibilitaet` führen die beiden Messpunkte über das Register, `welle-fehler-abbruch` den Abbruchpfad. Register: eine Evidence-Datei dieses Slice in sechs bestehenden Klassen und eine neue Beobachtung; die Welle-Closure prüft die Paarungen ein zweites Mal.
- **Für die Welle-Closure von `welle-anfrageverarbeitung`:** mit diesem Slice liegen `slice-rpc-executesql-parameter`, `slice-antwort-mehrteilig-output` und `slice-rpc-prepared-handles` in `done/`; ob weitere Slices der Welle offen sind, prüft die Welle-Closure am Wellenplan.

## 8. Sub-Area-Prüfungen und Modus-Begründung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Sub-Area-Modus-Begründung — dort die **zwei vorgelagerten
Schritte** (sie stehen in jedem Slice-Plan, unabhängig von Modus und
Slice-Typ) und die **vier Pflichtkriterien** (Konventionen-Dichte ·
Phase-Reife · Evidenz-/Diskrepanz-Risiko · Reconciliation-Aufwand), vier und
nicht mehr.

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Das Package `protocol` (Dispatch, Antwortabbildung) ist ein Pfad-Cluster (Achse 3), trägt aber keine eigene Strukturregel (Achse 1) und keine eigene Inventur-Linie (Achse 2): eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, acht Beobachtungen, Stand des gemergten `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/aussagen-ueber-messung-hinaus` — 7×, verkörpert (`AGENTS.md` §3.8, Anker `seit welle-protokollcodec`). Treffer: Die Regel gilt für diesen Slice; die Zuordnung *Test | Festlegung* (§2) und die Messgrenze im KDoc setzen sie um.
- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 3×, geplant (Träger `slice-qa-reproduzierbarkeit`, Vergleichslauf D). Treffer: ordinale Zählung des Handle-RETURNVALUE (§6). Die Schwelle ist erreicht und der Träger benannt; ein weiterer Beleg aus der Closure dieses Slice hebt die Beobachtung nicht in einen eigenen Slice, er wird als Beleg ergänzt.
- `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` — 3×, geplant (Träger `slice-qa-kompatibilitaet`). Treffer: `sp_unprepare` und `sp_reset_connection` unter Pooling (§6).
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Treffer: `ADR-*`- und `TDS-*`-Kennungen stehen in diesem Plan als Link; `make docs-check` belegt es.
- Ohne Treffer: `BEO-TDS/adapter-festlegungen-ohne-slice`, `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/vergleichslauf-d-ungeklaert`.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
