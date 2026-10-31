# Slice slice-rpc-executesql-parameter: RPC-Dispatch: sp_executesql und Prozedurname mit typisierten Parametern

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** welle-anfrageverarbeitung

**Bezug:** [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit), [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md)

**Berührte Spec-Stellen:** SPEC-002 · SPEC-003 · SPEC-030 · ARC-005 · [TDS-FA-04.a](../../../../spec/spezifikation.md#tds-fa-04a--rpc-und-parameter)

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

**Ziel:** Eine Sitzung im Zustand `READY` nimmt einen RPC an, den der Parser aus `slice-codec-rpc-parser` zerlegt hat, bildet für `sp_executesql` und für den RPC per Prozedurname einen `QueryRequest` mit typisierten Parametern (INPUT, NULL, OUTPUT, InputOutput) ohne Verkettung von SQL-Text und Werten, und beantwortet eine unbekannte RPC-Form oder eine ungültige Parameterkodierung mit einem definierten Fehler, ohne die Verbindung zu beenden ([TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc)).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- `sp_prepexec`, `sp_execute` und die Handle-Verwaltung — `slice-rpc-prepared-handles`; sie brauchen die RPC-Antwortform (RETURNVALUE) aus `slice-antwort-mehrteilig-output`.
- Antwort-Tokens für OUTPUT-Parameter und Rückgabestatus (RETURNSTATUS, RETURNVALUE, DONEPROC) — `slice-antwort-mehrteilig-output`; hier wird die Richtung (OUTPUT, InputOutput) in den `QueryRequest` übernommen, die Antwort darauf bildet jener Slice.
- RPC-Parser, TYPE_INFO-Dekodierung, NULL-Sentinels — `slice-codec-rpc-parser` (Voraussetzung, nicht Teil dieser Welle); PLP, NBCROW und Typen außerhalb des Mindestkatalogs — `slice-codec-datentypen-erweiterung`; ein Parameter dieser Art ergibt hier den definierten Fehler.
- Typkatalog (`SqlType`), Werteabbildung und Prüfung der Typabbildbarkeit gegen das Backend — die Welle `welle-backend-streaming` ([TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen)); dieser Slice bildet vom Parser-Ergebnis auf den dortigen Typ ab und führt keinen zweiten Katalog.
- Fehlerkodierung, Class- und Code-Zuordnung von `TDS_UNSUPPORTED_RPC` und `TDS_PROTOCOL_ERROR` — die Welle `welle-fehler-abbruch` ([ADR-0007](../../adr/0007-fehler-und-attention.md)); dieser Slice entscheidet, *dass* ein Fehler entsteht und ob die Verbindung bleibt, nicht wie er kodiert wird.
- `sp_prepare`, `sp_unprepare`, `sp_cursor*`, Table-Valued-Parameter, MARS — nicht im Katalog von [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md); sie ergeben den definierten Fehler; eine Aufnahme braucht den Nachweis des Bedarfs im Clientprofil (Re-Evaluierungs-Trigger der ADR).

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **`sp_executesql` ohne Verkettung** ([TDS-FA-04.a](../../../../spec/spezifikation.md#tds-fa-04a--rpc-und-parameter), SPEC-030, SPEC-002): Das Test-Backend erhält den SQL-Text unverändert als `sql`, die Deklarationsliste ist aufgelöst, und die Werte stehen als typisierte `QueryParameter` mit Namen, Typ und Richtung in der Parameterliste; ein Parameterwert, der SQL-Syntax enthält, kommt nur als Wert an (Test: der Text ist byte-gleich zu dem im RPC, der Wert steht in der Liste). Ein Test belegt, dass der Dispatch keinen Text aus Wert und SQL zusammensetzt.
- [ ] **NULL und Typgrenzen** ([TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc) Boundary, SPEC-003): ein NULL-Parameter und je ein Wert an der dokumentierten Grenze der Typen des Mindestkatalogs behalten ihre Bedeutung auf dem Weg zum Backend (NULL bleibt `null` mit Typ, Integer-Grenzen, Unicode-Text mit Nicht-BMP-Zeichen, Dezimalzahl mit Precision und Scale); die Richtung OUTPUT und InputOutput wird in die Parameterliste übernommen.
- [ ] **RPC per Prozedurname und definierte Fehler** ([TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc) Negative, SPEC-030): ein RPC per Prozedurname erreicht das Test-Backend in der Form, die die offene Frage zum Backend-Vertrag (§6) festlegt; ein unbekannter RPC (`sp_prepare`, `sp_unprepare`, `sp_cursor*`, unbekannte ProcID, unbekannter Name nach der Antwort des Backends) und eine ungültige Parameterkodierung ergeben je einen definierten Fehler, die Verbindung bleibt offen, ein Folge-RPC wird beantwortet (Setzung aus [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) und [ADR-0007](../../adr/0007-fehler-und-attention.md), nicht gemessen).

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
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/` (Unterpackage `dispatch`) <!-- d-check:ignore (geplant: neues Package) --> | update | RPC-Dispatch in `READY` neben dem Batch-Dispatch aus `slice-sql-batch-dispatch`: Parser-Ergebnis → `QueryRequest`, Bindung der Parameter, Abbildung der Parser-Fehler (ARC-005) |
| `spec/spezifikation.md` `SPEC-022` | update, nur falls die Frage zum Prozedurnamen (§6) so entschieden wird | Form, in der ein RPC per Prozedurname das Backend erreicht; die Änderung des Vertrags stimmt die Welle `welle-backend-streaming` ab |
| `spec/spezifikation.md` `SPEC-021` | update | Festlegung der Fehlerantwort auf nicht unterstützte RPC-Formen, ohne Zusage über das Verhalten des Referenzclients |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/` (Unterpackage `dispatch`) <!-- d-check:ignore (geplant: neues Package) --> | neu | Bindungstests (Text und Werte getrennt), NULL und Grenzwerte, OUTPUT-Richtung, unbekannte Form, ungültige Kodierung, Folge-RPC auf offener Verbindung; nach [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc); `@spec`-Tag je Test |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

- Reihenfolge: `sp_executesql` mit Bindung, dann NULL und Grenzen, dann Prozedurname und Fehlerfälle.
- Die Typabbildung hängt am Datentypen-Katalog der Welle `welle-backend-streaming`; fehlt ein Typ dort, ergibt der Parameter den definierten Fehler, es entsteht hier kein Ersatzkatalog.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `slice-sql-batch-dispatch` liegt in `done/` (Dispatch-Rahmen und Anfragezustand stehen), `slice-codec-rpc-parser` liegt in `done/` (Parser für den Katalog von [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md)), der Typkatalog (`SqlType`) der Welle `welle-backend-streaming` liegt auf `main`, die Frage nach der Form des RPC per Prozedurname (§6) ist beantwortet, und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2; Schnittvorschlag: der RPC per Prozedurname wird ein eigener Slice.
- `in-progress` → `open` (blockiert — Carveout?): der Typkatalog der Nachbarwelle deckt die Typen des Mindestkatalogs nicht, oder die Form des Prozedurnamen-RPC ist nicht entschieden.

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

- Wie erreicht ein RPC per Prozedurname das Backend? `QueryRequest` kennt nur `sql` und Parameter (SPEC-002). Entweder baut der Dispatch einen `EXEC`-Text aus dem Namen (nahe an einer SQL-Verkettung, der Name ist nutzerkontrolliert) oder der Vertrag bekommt ein eigenes Feld; die Wahl ist eine Entscheidung zum Backend-Vertrag und nicht Teil dieses Plans. — **Ausgang:** offen bis zur Closure; Frage an den Nutzer in der Welle-Eröffnung.
- Die Messung des RPC-Verhaltens stammt vom Spike-Testserver (Referenzclient, TDS 7.4 unverschlüsselt, `Pooling=false`, ein Lauf); ein echter SQL Server, TDS 8.0 und Pooling sind nicht gemessen (`BEO-TDS/messung-am-echten-sql-server-fehlt`, `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`). Die Freigabe der RPCs im Clientprofil steht aus. — **Ausgang:** offen bis zur Closure.
- Die Fehlerantwort auf eine unbekannte RPC-Form ist nicht gemessen; die Class des Fehlers `TDS_UNSUPPORTED_RPC` ist eine Voreinstellung des Nutzers (Class 16, Verbindung bleibt offen, [ADR-0007](../../adr/0007-fehler-und-attention.md)). — **Ausgang:** offen bis zur Closure.
- Der Parser kann Parameter mit Typen außerhalb des Mindestkatalogs nur ablehnen (`slice-codec-datentypen-erweiterung` fehlt); ein Client, der solche Typen sendet, erhält den definierten Fehler. — **Ausgang:** offen bis zur Closure.
- Die Parameter-Werte können Geheimnisse sein (Kennung, Passwort der Anwendung); keine automatisch erzeugte Textdarstellung von `QueryRequest` darf sie ausgeben ([TDS-QA-03](../../../../spec/lastenheft.md#tds-qa-03--sicherheit)). — **Ausgang:** offen bis zur Closure.

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
- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 3×, geplant (Träger `slice-qa-reproduzierbarkeit`, Vergleichslauf D). Treffer: Messgrenze der RPC-Formen (Testserver, kein echter SQL Server). Die Schwelle ist erreicht und der Träger benannt; ein weiterer Beleg aus der Closure dieses Slice hebt die Beobachtung nicht in einen eigenen Slice, er wird als Beleg ergänzt.
- `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` — 3×, geplant (Träger `slice-qa-kompatibilitaet`). Treffer: Pooling und TDS 8.0 für `sp_executesql` ungemessen (§6).
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Treffer: `ADR-*`- und `TDS-*`-Kennungen stehen in diesem Plan als Link; `make docs-check` belegt es.
- Ohne Treffer: `BEO-TDS/adapter-festlegungen-ohne-slice`, `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/vergleichslauf-d-ungeklaert`.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
