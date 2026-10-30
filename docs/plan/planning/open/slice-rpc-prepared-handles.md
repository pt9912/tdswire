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

**Verantwortlich:** — bis zur Priorisierung.

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

- [ ] **Handle-Tabelle je Sitzung** ([TDS-FA-04.a](../../../../spec/spezifikation.md#tds-fa-04a--rpc-und-parameter), SPEC-030, [ADR-0004](../../adr/0004-session-zustandsautomat.md)): Vergabe, Nachschlagen und Ende mit der Sitzung; der Zugriff läuft unter dem Sitzungsschutz des Zustandsmodells von [ADR-0004](../../adr/0004-session-zustandsautomat.md); zwei Sitzungen sehen die Handles der jeweils anderen nicht (Test); die Anzahl je Sitzung ist begrenzt, das Überschreiten ergibt den definierten Fehler und lässt die Verbindung offen. Der Grenzwert und die Vergabe der Handle-Werte sind Festlegungen des Nutzers (§6) und nicht gemessen.
- [ ] **`sp_prepexec` und `sp_execute`** ([TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc) Happy Path, [TDS-FA-10.a](../../../../spec/spezifikation.md#tds-fa-10a--mehrteilige-antworten-und-rpc-ausgabewerte)): `sp_prepexec` (Parameter 0 OUTPUT-Handle mit −1 beim Erstaufruf, Deklaration, Text) führt über das Test-Backend aus und liefert das Handle als RETURNVALUE mit Ordinal 0 und `INTN` 4 (so beantwortete es der Spike-Testserver; für einen echten Server nicht gemessen); `sp_execute` (Parameter 0 Handle) ruft das Test-Backend mit dem gemerkten Text und den neuen Werten, ein zweiter und dritter Aufruf mit anderen Werten liefern je das Ergebnis; NULL-Parameter und OUTPUT folgen den Regeln aus `slice-rpc-executesql-parameter`.
- [ ] **Definierte Fehler** ([TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc) Negative, [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md)): ein unbekanntes Handle, ein bereits wieder entferntes Handle (nach Sitzungsende nicht erreichbar), `sp_unprepare`, `sp_prepare` und das Überschreiten der Handle-Grenze ergeben je einen definierten Fehler ohne Verbindungsabbruch; ein Folge-RPC auf derselben Verbindung wird beantwortet (Setzung, nicht gemessen).

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
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/` (Unterpackage `dispatch`) <!-- d-check:ignore (geplant: neues Package) --> | update | Handle-Tabelle der Sitzung, `sp_prepexec`- und `sp_execute`-Pfad, Fehlerfälle (ARC-004, ARC-005); importiert Domain, Backend-Port, Parser-Typen |
| `contracts/client-profile.md` | update | Festlegung: Handle-Grenze, Verhalten bei `sp_unprepare` und unbekanntem Handle, ohne Zusage über Pooling |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/` (Unterpackage `dispatch`) <!-- d-check:ignore (geplant: neues Package) --> | neu | Handle-Lebenszyklus, Isolation zweier Sitzungen, Grenze, Folge-RPC nach Fehler, Parallelzugriff auf die Tabelle einer Sitzung; nach [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc); `@spec`-Tag je Test |

Der Implementer-Agent erweitert die Liste in seinem ersten Lauf.

- Reihenfolge: Tabelle mit Grenze, dann `sp_prepexec`/`sp_execute`, dann die Fehlerfälle.
- Der Nachweis, dass der Referenzclient `Prepare()` mit diesem Server besteht, gehört nicht hierher; er ist `slice-qa-kompatibilitaet`.

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `slice-rpc-executesql-parameter` und `slice-antwort-mehrteilig-output` liegen in `done/`, [ADR-0004](../../adr/0004-session-zustandsautomat.md) steht im ADR-Index auf `Accepted` (Sitzungsschutz und Sitzungsende), der Grenzwert für Handles je Sitzung ist vom Nutzer entschieden (§6), und ein Verantwortlicher ist im Kopf eingetragen.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2.
- `in-progress` → `open` (blockiert — Carveout?): [ADR-0004](../../adr/0004-session-zustandsautomat.md) bleibt `Proposed`, oder der Grenzwert bleibt unentschieden.

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

- Der Grenzwert der Handles je Sitzung und die Vergabe der Handle-Werte (Zähler je Sitzung ab 1, Wiederverwendung) sind weder gemessen noch in [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md) festgelegt ("zu begrenzen", Festlegung ohne Zahl). Die Zahl ist eine Entscheidung des Nutzers und wird hier nicht gesetzt. — **Ausgang:** offen bis zur Closure; Frage an den Nutzer in der Welle-Eröffnung.
- Unter Pooling kann ein Client `sp_unprepare` oder `sp_reset_connection` senden; der Katalog beantwortet `sp_unprepare` mit einem Fehler, was ein gepoolter Client daraus macht, ist nicht gemessen (`BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`). Handles wachsen ohne `sp_unprepare` bis zur Grenze. — **Ausgang:** offen bis zur Closure.
- Das Handle als RETURNVALUE mit Ordinal 0 ist vom Testserver beantwortet und vom Client akzeptiert worden; die Zählung eines echten Servers ist nicht gemessen (`BEO-TDS/messung-am-echten-sql-server-fehlt`). — **Ausgang:** offen bis zur Closure.
- Der Zugriff auf die Handle-Tabelle hängt am Nebenläufigkeitsmodell von [ADR-0004](../../adr/0004-session-zustandsautomat.md) (`Mutex` je Sitzung, Setzung des Nutzers, `Proposed`); gleichzeitige Anfragen einer Sitzung ohne MARS sind nicht erwartet, aber ein ATTENTION während `sp_prepexec` ist Gegenstand der Welle `welle-fehler-abbruch`. — **Ausgang:** offen bis zur Closure.
- Hält der Server den SQL-Text der Handles, kann ein Handle viel Speicher binden (MAX-Text); die Größe je Handle ist nicht begrenzt in [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md). — **Ausgang:** offen bis zur Closure.

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
- `BEO-TDS/messung-am-echten-sql-server-fehlt` — 3×, geplant (Träger `slice-qa-reproduzierbarkeit`, Vergleichslauf D). Treffer: ordinale Zählung des Handle-RETURNVALUE (§6). Die Schwelle ist erreicht und der Träger benannt; ein weiterer Beleg aus der Closure dieses Slice hebt die Beobachtung nicht in einen eigenen Slice, er wird als Beleg ergänzt.
- `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen` — 3×, geplant (Träger `slice-qa-kompatibilitaet`). Treffer: `sp_unprepare` und `sp_reset_connection` unter Pooling (§6).
- `BEO-TDS/kennungen-ohne-link-in-neuen-dokumenten` — 1×, offen. Treffer: `ADR-*`- und `TDS-*`-Kennungen stehen in diesem Plan als Link; `make docs-check` belegt es.
- Ohne Treffer: `BEO-TDS/adapter-festlegungen-ohne-slice`, `BEO-TDS/anmeldepfad-sicherheit-nicht-gebaut`, `BEO-TDS/betreiberanforderung-benutzerverwaltung-fehlt`, `BEO-TDS/vergleichslauf-d-ungeklaert`.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
