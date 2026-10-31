# Slice slice-antwort-mehrteilig-output: Mehrteilige Antworten, Rückgabestatus und OUTPUT-Parameter

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-anfrageverarbeitung

**Bezug:** [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)

**Berührte Spec-Stellen:** SPEC-002 · SPEC-030 · SPEC-031 · ARC-005 · ARC-007 · ARC-015 · [TDS-FA-10.a](../../../../spec/spezifikation.md#tds-fa-10a--mehrteilige-antworten-und-rpc-ausgabewerte)

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

**Ziel:** Der Server beantwortet eine Anfrage mit mehreren Ergebnissen, Update-Zählern, Rückgabestatus und OUTPUT-Parametern als eine geordnete Antwort: die Ereignisse des Backends erscheinen in der Reihenfolge des Stroms, eine Ergebnisgrenze (auch ein leeres Ergebnis zwischen zwei Ergebnissen) ist für den Client eindeutig, ein RPC liefert RETURNSTATUS, je OUTPUT-Parameter ein RETURNVALUE und DONEPROC, und eine nicht unterstützte Antwortform ergibt einen definierten Fehler statt eines unvollständigen Erfolgsstroms ([TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte)).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Der Kern des Sequenzierers (Ereignis → Token für `ResultStart`, `Row`, `ResultEnd`, Vorschau für `DONE_MORE`, Backpressure) und die Kodierung von COLMETADATA und ROW — die Welle `welle-backend-streaming` ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)); dieser Slice ergänzt, was der Dispatch für RPC und Mehrfach-Ergebnisse braucht, und baut den Kern nicht neu.
- Fehlerkodierung (ERROR, DONE mit Fehlerstatus), die Unterscheidung statement- und batch-abbrechender Fehler im Strom und das Schicksal eines zurückgehaltenen Abschlusses bei Cancel oder Disconnect — die Welle `welle-fehler-abbruch`; offen in [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) und [ADR-0007](../../adr/0007-fehler-und-attention.md) mit dieser Zuständigkeit.
- Parameterbindung und Dispatch des RPC — `slice-rpc-executesql-parameter`; das Prepare-Handle als RETURNVALUE — `slice-rpc-prepared-handles` (es nutzt die RPC-Antwortform dieses Slice).
- Ordinale Zählung der RETURNVALUE, Tokenfolge und DONE-Statusbits eines echten SQL Servers — nicht gemessen, Träger `slice-qa-reproduzierbarkeit` (`BEO-TDS/messung-am-echten-sql-server-fehlt`); dieser Slice setzt die Festlegung aus MS-TDS und dem Spike-Testserver.
- Transaktionen, MARS, Table-Valued-Parameter und weitere Antwortformen außerhalb des Clientprofils — dokumentiert als nicht unterstützt (siehe DoD), nicht gebaut ([TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte) Out-of-Scope).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Mehrere Ergebnisse in Reihenfolge** ([TDS-FA-10.a](../../../../spec/spezifikation.md#tds-fa-10a--mehrteilige-antworten-und-rpc-ausgabewerte), SPEC-002, SPEC-031): Ein Test-Backend mit zwei Ergebnissen und einem Update-Zähler führt zu einer Antwort in der Reihenfolge des Stroms; `DONE_MORE` ist an jedem Abschluss außer dem letzten gesetzt, `DONE_COUNT` nur bei gemeldetem Zähler; ein leeres Ergebnis zwischen zwei Ergebnissen erzeugt Metadaten und Abschluss, sodass die Grenze eindeutig ist (Byte-Vektor je Fall; die Messzeile `DONE MORE|COUNT` mit anschließendem Abschluss aus [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) ist ein Spike-Befund am Testserver, kein Wert eines echten Servers).
- [ ] **RPC-Antwortform und OUTPUT** ([TDS-FA-10.a](../../../../spec/spezifikation.md#tds-fa-10a--mehrteilige-antworten-und-rpc-ausgabewerte), SPEC-030): ein RPC mit OUTPUT-Parameter und mit Rückgabestatus erzeugt RETURNSTATUS, je OUTPUT- und InputOutput-Parameter ein RETURNVALUE (Name, Typ, Wert, ordinale Zählung als Festlegung) und zum Schluss DONEPROC bzw. DONEINPROC passend zum Kontext; ein NULL-Wert im OUTPUT bleibt NULL; ohne OUTPUT-Parameter entsteht kein RETURNVALUE.
- [ ] **Nicht unterstützte Antwortform** ([TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte) Negative, SPEC-031): Liefert das Test-Backend eine Form, die das Profil nicht kennt (Zeile ohne vorheriges `ResultStart`, doppeltes `ResultEnd`, `OutputParameter` bei einem Batch, Typ außerhalb der Abbildung), meldet der Server einen definierten Fehler und beginnt vor dem ersten gesendeten Byte keine Erfolgsantwort; nach dem ersten gesendeten Byte folgt ERROR und Abschluss mit Fehlerstatus, solange der Strom synchron ist, sonst schließt der Server (Setzung aus [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) und [ADR-0007](../../adr/0007-fehler-und-attention.md), nicht gemessen); ein Test prüft, dass kein vollständiger Erfolgsabschluss auf eine Teilantwort folgt.

Nachweis-Pflichten für alle Punkte:

- [ ] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>` (Konvention nach `slice-qa-spec-abdeckung`); die Tests laufen in `make test` und sind grün.
- [ ] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet (KDoc des Elements). Die Zuordnung *Test | Festlegung* je Aussage steht in der Closure-Notiz. Die Tests laufen gegen ein Test-Backend, das den Backend-Port implementiert, nicht gegen einen echten SQL Server und nicht gegen den Referenzclient; Aussagen über deren Verhalten trägt dieser Slice nicht.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist.
- [ ] Closure-Notiz mit Steering-Loop-Lerneintrag.
- [ ] Beobachtungs-Register (`../observations/`) fortgeschrieben — neues Verzeichnis `BEO-<KUERZEL>/<slug>/` oder eine weitere Datei in dessen `evidence/`; **kein Zaehler wird gesetzt**, er folgt aus den Dateien. Keine Beobachtung angefallen ist ebenfalls eine Antwort und wird in §7 notiert.
- [ ] Jedes Risiko aus §6 trägt einen Ausgang (eingetreten / entfallen / weiter offen).
- [ ] Die drei Paarungen (Anker · Folge-Slice · Register) sind getragen — im Repo mit Wellen von der Welle-Closure geprüft.

## 3. Plan (vor Code)

Regeln dieser Sektion: Baseline-Regelwerk `grundlagen-bootstrap.md`
§Was ist eine Sub-Area? — diese Liste liefert die **Pfad-Kandidaten** für §8,
nicht die Antwort: Pfad-Berührung ist nicht hinreichend, und eine
Aussagen-Berührung steht hier gar nicht.

| Datei / Komponente | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/` (Unterpackage `dispatch`) <!-- d-check:ignore (geplant: neues Package) --> | update | Anbindung der Antwort an den Sequenzierer für mehrere Ergebnisse und für RPC (RETURNSTATUS, RETURNVALUE, DONEPROC bzw. DONEINPROC) (ARC-005, ARC-007, ARC-015); Prüfung der Ereignisreihenfolge, soweit die Nachbarwelle sie nicht liefert |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/` (Sequenzierer aus `welle-backend-streaming`) | update, nur falls nötig | RPC-spezifische Ereignisse ergänzen, falls der Kern sie nicht trägt; die Änderung stimmt die Welle `welle-backend-streaming` ab |
| `spec/spezifikation.md` `SPEC-021` | update | Antwortformen, die der Server liefert, und die ausdrücklich nicht unterstützten ([TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte) verlangt die Dokumentation nicht benötigter Varianten) |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/` (Unterpackage `dispatch`) <!-- d-check:ignore (geplant: neues Package) --> | neu | Byte-Vektoren der Antworten, Ergebnisgrenzen, OUTPUT und RETURNSTATUS, nicht unterstützte Form; nach [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte); `@spec`-Tag je Test |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

**Ist-Zustand gegen den Plan (Implementer, vor dem Code):** Der Sequenzierer-Kern liegt auf `main` (`slice-backend-sequenzierer` in `done/`) und trägt schon die Vorschau für `DONE_MORE`, `DONE_COUNT` nur bei gemeldeter Zahl, die Ereignisreihenfolge-Prüfung (Verstoß ist ein Fall von `SPEC-048`) und die Ablehnung von `ReturnStatus` und `OutputParameter`; die Unterpackages `response` und `dispatch` existieren (nicht `dispatch` neu). Offen waren daher nur die RPC-Antwortform und die Dispatch-Tests für Mehrfach-Ergebnisse und Negativformen. Der dritte Liefer-Punkt (Reihenfolge) ist im Kern vorhanden; dieser Slice belegt ihn an der Leitung des Dispatch. Die Liste der Dateien:

| Datei | Änderungs-Art | Begründung |
|---|---|---|
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/response/ResponseForm.kt` | neu | Antwortform BATCH oder RPC, von der Anfrageschleife an den Sequenzierer gegeben |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/response/ResponseRun.kt` | update | DONEINPROC und DONEPROC, RETURNSTATUS, RETURNVALUE in der Form RPC |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/response/ResponseSequencer.kt` | update | Parameter `form`, Festlegungen im KDoc |
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RequestLoop.kt` | update | RPC ruft den Sequenzierer mit der Form RPC |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/response/ResponseRpcTest.kt` | neu | Sequenzierer in der Form RPC |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/AnswerFormsDispatchTest.kt` | neu | Byte-Vektoren der Antworten an der Anfrageschleife |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RpcDispatchTest.kt` | update | Erwartung der RPC-Antwort: DONEINPROC und DONEPROC statt DONE |

- Reihenfolge: Mehrfach-Ergebnisse, dann RPC-Antwortform, dann der Negativfall.
- Die Byte-Vektoren stammen aus MS-TDS und aus den Spike-Antworten in [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md); Abschnittsnummern externer Standards nur mit belegbarer Quelle (`AGENTS.md` §3.8).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `slice-sql-batch-dispatch` und `slice-rpc-executesql-parameter` liegen in `done/`, der Sequenzierer-Kern der Welle `welle-backend-streaming` liegt auf `main` (Kennung des Slice wird nachgetragen), und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2; Schnittvorschlag: die Prüfung der Ereignisreihenfolge (dritter Punkt) wird ein eigener Slice.
- `in-progress` → `open` (blockiert — Carveout?): der Sequenzierer-Kern der Nachbarwelle trägt die DONE-Familie nicht in der hier gebrauchten Form und ihr Planer kann sie nicht ergänzen.

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

- Wem gehört die Prüfung der Ereignisreihenfolge im Sequenzierer ([ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) §Offen: "im Spike nicht gebaut", ohne Slice-Zuständigkeit)? Dieser Plan legt sie hierher, weil [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte) Negative sie verlangt; widerspricht ein Nachbarplan, wandert sie dorthin und der dritte Liefer-Punkt entfällt. — **Ausgang:** offen bis zur Closure; Frage an den Nutzer in der Welle-Eröffnung.
- Die ordinale Zählung der RETURNVALUE, die Tokenreihenfolge und die DONE-Statusbits eines echten SQL Servers sind nicht gemessen (`BEO-TDS/messung-am-echten-sql-server-fehlt`); [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) nennt sie ausdrücklich als gegen MS-TDS zu prüfen. Die Vektoren sind von Hand abgeleitet. — **Ausgang:** offen bis zur Closure.
- Ob ein `Failure` als "nächstes Ereignis" der `DONE_MORE`-Vorschau zählt, ist in [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md) offen (Zuständigkeit Backend-/Mapping-Slice, nach Messung); ein Test mit Fehler nach einem Ergebnis hängt davon ab. — **Ausgang:** offen bis zur Closure.
- Der Rückgabestatus eines RPC per Prozedurname kommt aus dem Backend (`ReturnStatus`); ein Backend, das ihn nicht liefert, ergibt keinen RETURNSTATUS, und ob der Client daran scheitert, ist nicht gemessen. — **Ausgang:** offen bis zur Closure.
- Größe: drei Liefer-Punkte, der dritte hängt an der Nachbarwelle. — **Ausgang:** offen bis zur Closure. Rückführung und Schnitt in §4.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Das Package `protocol` (Dispatch, Antwortabbildung) ist ein Pfad-Cluster (Achse 3), trägt aber keine eigene Strukturregel (Achse 1) und keine eigene Inventur-Linie (Achse 2): eine von drei Achsen, unter der Schwelle. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register durchgegangen (`../observations/BEO-TDS/`, acht Beobachtungen, Stand des gemergten `main`); alle tragen die Sub-Area `*`. Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `BEO-TDS/aussagen-ueber-messung-hinaus` — 7×, verkörpert (`AGENTS.md` §3.8, Anker `seit welle-protokollcodec`). Treffer: Die Regel gilt für diesen Slice; die Zuordnung *Test | Festlegung* (§2) und die Messgrenze im KDoc setzen sie um.
- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 3×, geplant (Träger `slice-qa-reproduzierbarkeit`, Vergleichslauf D). Treffer: ordinale Zählung der RETURNVALUE, Tokenfolge, DONE-Statusbits (§6). Die Schwelle ist erreicht und der Träger benannt; ein weiterer Beleg aus der Closure dieses Slice hebt die Beobachtung nicht in einen eigenen Slice, er wird als Beleg ergänzt.
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Treffer: `ADR-*`- und `TDS-*`-Kennungen stehen in diesem Plan als Link; `make docs-check` belegt es.
- Ohne Treffer: `BEO-TDS/adapter-festlegungen-ohne-slice`, `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`, `BEO-TDS/vergleichslauf-d-ungeklaert`.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
