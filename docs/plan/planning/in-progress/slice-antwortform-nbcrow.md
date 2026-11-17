# Slice slice-antwortform-nbcrow: Antwortform: Sequenzierer schreibt NBCROW wie der echte Server (A-12)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** ohne Welle (keine Closure-Bedingung außerhalb der DoD dieses Slice; Folge-Slice von `slice-codec-datentypen-erweiterung` nach Entscheidung des Nutzers 2026-11-16).

**Bezug:** [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen), [TDS-QA-01](../../../../spec/lastenheft.md#tds-qa-01--kompatibilität), [ADR-0006](../../adr/0006-backend-vertrag-und-streaming.md)

**Berührte Spec-Stellen:** SPEC-003 (NBCROW) · SPEC-045 (Ausnahmeliste des Referenzabgleichs, Regel A-12)

**Verantwortlich:** pt9912

**Autor:** pt9912. **Datum:** 2026-11-16.

---

## 1. Ziel und Abgrenzung

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Der Sequenzierer schreibt eine Zeile mit NULL-Wert so als `NBCROW`, wie es der echte SQL Server in der Antwort tut, belegt an einer Messung gegen den echten Server und am Kompatibilitätstest; die Ausnahme A-12 des Referenzabgleichs entfällt. Der Encoder kann `NBCROW` seit `slice-codec-datentypen-erweiterung` auf Anweisung des Aufrufers schreiben ([Kompatibilitätsmatrix](../../kompatibilitaetsmatrix.md) Abschnitt 2, Lücke 6); hier entscheidet der Sequenzierer, wann er es anweist.

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- A-11 (`DECIMALN` mit Länge 17 statt 9 im TYPE_INFO von `decimal(10,2)`) — es ist die Längenwahl eines Typs, nicht die Form der Zeile; die Ursache ist nicht untersucht, eine Belegstelle fehlt (`AGENTS.md` §3.8). Der Träger ist offen und beim Nutzer (Register `BEO-TDS/mitschnitt-luecken-ohne-traeger`).
- Weitere Basistypen von `sql_variant`, `text`, `ntext`, `image` — kein Mitschnitt belegt sie; ohne Beleg gibt es kein Orakel (Entscheidung des Nutzers 2026-11-16, benannte Lücke ohne eigenen Slice).
- Antwortseite eines `nvarchar(max)`-OUTPUT-Werts über 4000 Zeichen — die Wertabbildung der Antwort ist nicht Teil der Zeilenform und ungemessen; sie bleibt als Lücke im Register (`BEO-TDS/messung-am-echten-sql-server-fehlt`).
- Änderung des Encoders oder der Wertcodierung — `NullBitmapRowToken` bleibt, wie es ist; der Slice ändert nur, wann der Sequenzierer ihn verwendet.
- Zeilenform im Massenladen und in Antworten außerhalb des Ergebnisstroms (RETURNVALUE) — dort gibt es kein ROW und kein NBCROW.

## 2. Definition of Done

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (drei):

- [ ] **Messung der Wahl zwischen ROW und NBCROW** am echten SQL Server (beide Referenzinstanzen, Einzelmessung je Instanz, Messweg des Referenzabgleichs, `docs/messungen/referenzabgleich.md`): Zeile ohne NULL bei nullbaren Spalten, mit einer NULL, mit nur NULL, NULL erst in einer Spalte hinter der achten (Bitmap von zwei Byte) und eine Zeile ohne nullbare Spalte. Das Messprotokoll in `docs/messungen/` trägt die Bytes der Zeilen; was nicht gemessen wird, ist Setzung und so gekennzeichnet.
- [ ] **Sequenzierer schreibt die gemessene Regel** (`ResponseRun`, `NullBitmapRowToken`, SPEC-003): ein Test je gemessenem Fall mit den Bytes der Messung als Orakel; die Ausnahme A-12 entfällt aus `referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv`, und der Mitschnitt-Lauf der Referenz ist ohne sie grün.
- [ ] **Zielbild-Prüfung** (SPEC-003, [TDS-FA-05](../../../../spec/lastenheft.md#tds-fa-05--resultsets-und-datentypen)): Spezifikation, Kompatibilitätsmatrix (Zeile NBCROW, Lücke 6) und `referenzabgleich.md` (Zeile A-12) sagen den gebauten Stand; keine Aussage steht breiter da als die Messung.

Nachweis-Pflichten für alle drei Punkte:

- [ ] Jeder Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>`; die Tests laufen in `make test` und sind grün.
- [ ] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet; die Closure-Notiz trennt *gemessen*, *Handvektor*, *Selbstkonsistenz* und *Festlegung*.
- [ ] Zerstückelte, überlange und zufällige Eingaben enden in einem Ergebniswert (SPEC-027, [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)) — für die Zeilenzahl und Spaltenzahl der Zeilen des Sequenzierers.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist (die Zeilen zu NBCROW in `SPEC-003`).
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
| `src/main/kotlin/io/github/pt9912/tdswire/protocol/response/ResponseRun.kt` | update | der Sequenzierer wählt zwischen `RowToken` und `NullBitmapRowToken` nach der gemessenen Regel |
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/response/` | update | Tests je gemessenem Fall; der Test-Decoder liest NBCROW bereits (`TokenDecoder`) |
| `referenztest-dotnet/Vergleich/mitschnitt-ausnahmen.tsv` | update | die Regel A-12 entfällt |
| `docs/messungen/` | neu | Messprotokoll der Zeilenform am echten Server |
| `spec/spezifikation.md`, `docs/plan/kompatibilitaetsmatrix.md`, `docs/messungen/referenzabgleich.md`, `docs/plan/spec-abdeckung.md`, Register `BEO-TDS/mitschnitt-luecken-ohne-traeger` | update | Zielbild-Prüfung; Zeile NBCROW und Lücke 6 der Matrix; Zeile A-12; Abdeckungsmatrix nach geänderten Tags |

Der Implementer liest den Stand von `ResponseRun` und passt diese Tabelle an; eine beim Bau entstandene Datei steht vor dem Handoff darin (`BEO-TDS/plan-tabelle-nennt-neue-datei-nicht`).

## 4. Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `slice-codec-datentypen-erweiterung` liegt in `done/` (der Encoder schreibt NBCROW), `make gates` ist auf `main` grün, ein Verantwortlicher ist im Kopf eingetragen, und der Nutzer bestätigt den Lauf gegen die Referenzinstanzen (EULA des Images liegt beim Nutzer, `make vergleichslauf-d`); ohne die Bestätigung bleibt der Slice in `open/`, weil Liefer-Punkt 1 den echten Server braucht.

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): mehr als drei Liefer-Punkte in §2; Schnittvorschlag: Messung und Bau trennen.
- `in-progress` → `open` (blockiert): Der Lauf gegen den echten Server ist nicht freigegeben oder gelingt nicht; ohne Messung bleibt A-12 eine Lücke, und der Sequenzierer schreibt ROW weiter.

## 5. Closure-Trigger

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Die DoD ist vollständig, `make gates` ist grün, der Review-Report liegt vor, und die Closure-Notiz trägt die Zuordnung je Aussage sowie den Steering-Loop-Lerneintrag.

## 6. Risiken und offene Punkte

Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

- Die Regel des echten Servers (wann NBCROW, wann ROW) ist aus einer Zeile des Mitschnitts `batch-typen` bekannt (Zeile mit NULL als NBCROW); ob sie für jede Zeile mit einer NULL gilt, ist nicht gemessen. — **Ausgang:** offen bis zur Closure; Liefer-Punkt 1 misst sie.
- Die Referenz (`SqlClient`) liest ROW und NBCROW; ob ein anderer Client NBCROW ablehnt, ist nicht gemessen. — **Ausgang:** offen bis zur Closure.
- Größe: drei Liefer-Punkte. — **Ausgang:** offen bis zur Closure; Rückführung und Schnitt in §4.

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`); keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register (`../observations/BEO-TDS/`) gegen die Verzeichnisliste und die Stichworte NBCROW, Mitschnitt, Messung, Antwort, Zielbild, Plan-Tabelle gelesen; Treffer: `mitschnitt-luecken-ohne-traeger` (A-12 trägt dieser Slice), `messung-am-echten-sql-server-fehlt` und `aussagen-ueber-messung-hinaus` (beide über der Schwelle und verkörpert; Liefer-Punkt 1 und 3 sind die Gegenmaßnahme), `plan-tabelle-nennt-neue-datei-nicht` (verkörpert; §3 nennt die Pflicht).

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
