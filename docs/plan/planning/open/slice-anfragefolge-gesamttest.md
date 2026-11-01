# Slice slice-anfragefolge-gesamttest: Gesamtfolge der Anfragearten auf einer Sitzung (Mehr-Kriterium von welle-anfrageverarbeitung)

**Lifecycle:** Der Zustand dieses Slice ist das Verzeichnis, in dem diese
Datei liegt — eines von `open/`, `next/`, `in-progress/`, `done/`. Er
wechselt nur durch `git mv`, siehe
Baseline-Regelwerk `modul-05-planning-harness.md` §Lifecycle als State Machine.
Übernimmt ein anderer Slice den Gegenstand oder entfällt er, geht diese Datei
aus `open/` oder `next/` nach `done/` — §7 nennt in der Zeile `Gegenstand:`
Kennung oder Grund, die Liefer-Punkte der DoD bleiben leer
(§Ein Slice, dessen Gegenstand ein anderer übernimmt).

**Welle:** [welle-anfrageverarbeitung](../welle-anfrageverarbeitung.md) — der Slice trägt das *Mehr* des Closure-Triggers (§3 der Welle, dritter Punkt), das keiner der Slice-DoDs der Welle allein belegt.

**Bezug:** [TDS-FA-03](../../../../spec/lastenheft.md#tds-fa-03--einfache-sql-batches), [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität), [ADR-0004](../../adr/0004-session-zustandsautomat.md), [ADR-0005](../../adr/0005-rpc-und-prepared-statements.md)

**Berührte Spec-Stellen:** SPEC-001 · SPEC-002 · SPEC-030 · SPEC-057 · SPEC-013 · ARC-004 · ARC-005

**Verantwortlich:** pt9912.

**Autor:** pt9912 (Planner). **Datum:** 2026-11-01. **Basis:** `main` 3c35058.

---

## 1. Ziel und Abgrenzung


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — Schnitt nach Lieferwert, nicht nach Schichten; jeder Slice
ist einzeln lieferbar. **§1 nennt Ziel und Abgrenzung** (Out-of-Scope-Disziplin
des Lastenhefts, auf den Slice-Plan angewandt); die vier Klassen des
Ausschlusses stehen in **eben diesem Abschnitt** des Baseline-Regelwerks,
zusammen mit der Begründungs-Pflicht je Punkt.

**Ziel:** Ein Gesamttest belegt auf **einer** Sitzung ab `READY` gegen ein Test-Backend, das den Backend-Port implementiert, die Folge des Closure-Triggers von [welle-anfrageverarbeitung](../welle-anfrageverarbeitung.md) (§3, dritter Punkt) und damit das *Mehr* gegenüber den Slice-DoDs: der Anfragezustand kehrt nach jeder Anfrageart nach `IDLE` zurück, die Handle-Tabelle überlebt Anfragen derselben Sitzung, und ein Fehler beendet die Sitzung nicht ([TDS-FA-03](../../../../spec/lastenheft.md#tds-fa-03--einfache-sql-batches), [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte)).

**Ausdrücklich NICHT in diesem Slice** — je Punkt mit Begründung:

- Eine Änderung am Produktivcode — der Slice ist ein Beleg, kein Umbau; findet der Test eine Abweichung, ist das ein Befund, den der Implementer als Folge-Slice oder Beobachtung meldet (§6), nicht im selben Slice behebt.
- PLP, NBCROW und Typen außerhalb des Mindestkatalogs — `slice-codec-datentypen-erweiterung` (Vorrat); die Folge enthält laut Closure-Trigger keine dieser Formen.
- Paket-Framing, Socket, TLS und Login — gehören den Wellen `welle-transport-tls` und `welle-handshake-login` (beide `done/`); der Socket-Pfad mit zwei Batches steht in `RequestWiringTest`. Die Aussage des Gesamttests gilt für Nachrichten (Pakettyp und Nutzlast) einer Sitzung, nicht für Pakete auf einem Socket.
- Der Referenzclient und ein echter SQL Server — `slice-qa-kompatibilitaet` (`done/`) und `slice-qa-reproduzierbarkeit`; der Test trägt keine Aussage über deren Verhalten ([welle-anfrageverarbeitung](../welle-anfrageverarbeitung.md) §6).
- ATTENTION, Cancel, Timeout und Backend-Fehler im Strom — `welle-fehler-abbruch`; die Folge enthält nur Fehler, die der Dispatch selbst erzeugt (unbekannter RPC, unbekanntes Handle).
- Die Grenze der Handle-Tabelle (1000) — `PreparedHandlesDispatchTest`; die Folge belegt Überleben, nicht die Grenze.

## 2. Definition of Done


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Ziel-Form: Slice — **≤ 3 Liefer-Punkte**; mehr heißt: der Slice ist zu groß und
gehört zurück zur Zerlegung. Gezählt wird nur, was mit dem Umfang wächst — die
Gate-Läufe und die fünf Closure-Pflichten darunter zählen nicht mit.

Liefer-Punkte (zwei):

- [ ] **Gesamtfolge-Test** ([TDS-FA-03](../../../../spec/lastenheft.md#tds-fa-03--einfache-sql-batches), [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), SPEC-001, SPEC-002, SPEC-030, SPEC-057, SPEC-013): **ein** Test mit den `@spec`-Tags dieser Kennungen im Source-Set `test`. Er läuft als Rohbytes-Folge (Handvektoren der Nachrichten, Antworten als Hex der Nutzlast) auf **einer** Sitzung ab `READY` gegen ein Test-Backend, das den Backend-Port implementiert, und belegt in einer Folge: ein Batch mit Ergebnis, ein Batch mit leerem Ergebnis, ein Batch mit zwei Ergebnissen, `sp_executesql` mit NULL- und OUTPUT-Parameter, `sp_prepexec` und danach `sp_execute` mit dem gelieferten Handle, ein unbekannter RPC und ein unbekanntes Handle (je ein definierter Fehler), und danach noch eine erfolgreiche Anfrage auf derselben Verbindung. Der Lauf enthält keine PLP- und keine NBCROW-Form; deren Belege liegen in `slice-codec-datentypen-erweiterung`. Er belegt, was die Slices einzeln nicht belegen: der Anfragezustand kehrt nach jeder Anfrageart nach `IDLE` zurück, die Handle-Tabelle überlebt Anfragen derselben Sitzung, und ein Fehler beendet die Sitzung nicht. *(Wortlaut aus §3 der Welle, dritter Punkt, unverändert.)*
- [ ] **Wächter der Folge** ([TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität)): Mutationen am Produktivcode, je im Arbeitsbaum gesetzt, gesehen rot und zurückgenommen (Liste in §3, Messzeilen in der Closure-Notiz): der Test wird rot, wenn der Anfragezustand nach einer Anfrageart nicht nach `READY` zurückkehrt, wenn die Handle-Tabelle bei einer Anfrageart (Batch, `sp_executesql`, definierter Fehler) geleert wird oder wenn ein definierter Fehler die Sitzung beendet.

Nachweis-Pflichten für alle Punkte:

- [ ] Der Test trägt im KDoc über der Testfunktion den Block-Tag `@spec SPEC-<NNN>` je Kennung (Konvention nach `slice-qa-spec-abdeckung`); er läuft in `make test` und ist grün.
- [ ] Jede Aussage steht an einem Test oder ist als Festlegung gekennzeichnet (AGENTS.md §3.8). Die Antwort-Bytes sind Handvektoren mit Quelle (die Vorläufer-Tests, §3); Tokenfolge, Fehlercodes, Handle-Werte und das Ende der Folge sind Festlegungen und weder am Referenzclient noch an einem SQL Server gemessen. Die Zuordnung *gemessen | Festlegung* steht in der Closure-Notiz.
- [ ] Gate-Zeit: Der Test braucht weder Socket noch Wartezeit noch `yield()`; sein Lauf steht im Gradle-Testbericht unter 2 s, und die Dauer von `make gates` (Einzelmessung) steigt durch ihn nicht über die Streuung des Vorlaufs auf `main` hinaus. Beide Zahlen stehen in der Closure-Notiz.
- [ ] `make gates` grün.
- [ ] Review durchgeführt, Report unter `docs/reviews/` liegt vor
      (`.harness/skills/reviewer.md`) — Rollenwechsel nach Schritt 8 des
      Minimal Agent Workflow (`AGENTS.md` §6), kein Self-Review (Modul 8).
- [ ] Doku-Update, falls ein öffentlicher Vertrag berührt ist (erwartet: keiner).
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
| `src/test/kotlin/io/github/pt9912/tdswire/protocol/dispatch/RequestSequenceTest.kt` | neu | Der Gesamtfolge-Test (Dateiname ist ein Vorschlag; eine Klasse, eine Testfunktion) nach [TDS-FA-03](../../../../spec/lastenheft.md#tds-fa-03--einfache-sql-batches), [TDS-FA-04](../../../../spec/lastenheft.md#tds-fa-04--parametrisierte-abfragen-über-rpc), [TDS-FA-10](../../../../spec/lastenheft.md#tds-fa-10--mehrteilige-antworten-und-ausgabewerte), [TDS-QA-02](../../../../spec/lastenheft.md#tds-qa-02--stabilität); `@spec` SPEC-001, SPEC-002, SPEC-030, SPEC-057, SPEC-013 |

Ansatz:

- **Wahl der Sitzungshilfe: `Session` aus `DispatchSupport.kt`, nicht `WireSession`.** `Session.login(vararg Inbound)` nimmt jede Nachricht als Rohbytes (`BatchVectors.raw`, `RpcWire.*`), `session.hex(i)` liefert die Nutzlast jeder Antwort einzeln, und `session.channel.exhausted` sagt, ob die Folge ganz gelesen ist. `WireSession.login` nimmt nur SQL_BATCH-Texte; für RPC bräuchte es einen Überlader im geteilten Test-Support und einen Zusammenbau der Antwort-Pakete zu Nachrichten, und es würde das Paket-Framing prüfen, das `StreamingDispatchTest`, `AttentionWireTest` und `RequestWiringTest` schon tragen und das nicht zum *Mehr* gehört. Die Aussage heißt deshalb „Nachrichten einer Sitzung“, nicht „Pakete“. Verlangt der Nutzer den echten Transport, ist die Alternative `WireSession` mit Überlader; sie ändert den Schnitt nicht, nur die Hilfe.
- **Folge, 1 bis 8 im Wortlaut der Welle, 9 und 10 als Erweiterung:** (1) Batch mit Ergebnis, (2) Batch mit leerem Ergebnis, (3) Batch mit zwei Ergebnissen, (4) `sp_executesql` mit NULL- und OUTPUT-Parameter (`RpcWire.executeSql`), (5) `sp_prepexec` (`RpcWire.prepExec`) und danach `sp_execute` mit dem gelieferten Handle (`RpcWire.execute`), (6) ein unbekannter RPC (`RpcWire.procId` mit einer ProcID außerhalb des Katalogs, z. B. 15 = `sp_unprepare`, `TDS_UNSUPPORTED_RPC`, SPEC-013), (7) `sp_execute` mit einem unbekannten Handle (`TDS_UNKNOWN_HANDLE`, SPEC-057), (8) eine erfolgreiche Anfrage auf derselben Verbindung (ein Batch). **Zusatz** (9) `sp_executesql` und (10) `sp_execute` mit dem Handle aus (5): Ohne sie bliebe die Aussage „die Handle-Tabelle überlebt Anfragen“ auf die Fehler (6), (7) und den Batch (8) beschränkt, und eine Leerung bei `sp_executesql` wäre unsichtbar. Der Zusatz erweitert den Wortlaut, ersetzt ihn nicht.
- **Backend:** `ScriptedBackend.inSequence(run1, …)` mit einer Ereignisliste je **Backend-Aufruf**; (6) und (7) rufen das Backend nicht, der Index zählt nur die Aufrufe. Der Test prüft `backend.requests` genau (Anzahl, SQL-Text, Parameter mit NULL und Richtung OUTPUT), nicht nur eine Obergrenze (§6).
- **Anfragezustand beobachten:** Ein Wrapper um das Backend (`QueryBackend` delegierend, im Test) liest bei jedem `execute` den Zustand der Sitzung (`session.ready.live.context.state`) und hält ihn fest; erwartet `RUNNING` bei jedem Aufruf. Dass die Anfrage davor nach `READY` zurückkehrte, belegt die Annahme der nächsten Nachricht selbst (außerhalb von `READY` schließt die Schleife: `RequestLoop.step`); darum folgt **jeder** Anfrageart (Batch, `sp_executesql`, `sp_prepexec`, `sp_execute`, definierter Fehler) mindestens eine weitere Anfrage. Der Test nennt im KDoc die Lesart: `READY` ist der Anfragezustand `IDLE` (SPEC-001); `COMPLETED` ist nicht abgebildet.
- **Antworten:** Hex der Nutzlast je Antwort für die Erfolgsfälle (Handvektoren, die in `SqlBatchDispatchTest`, `AnswerFormsDispatchTest` und `PreparedHandlesDispatchTest` stehen und dort ihre Quelle tragen; der Implementer kopiert sie mit Verweis in die neue Datei, statt die privaten Konstanten der Vorläufer anzufassen); für die Fehler `session.tokens(i).errorMessage()` und `lastDone().status == DoneStatus.ERROR` mit den Codes 50002 und 50009 wie in `PreparedHandlesDispatchTest`. Fehlt für einen Fall ein Vektor mit Quelle, wird er von Hand nach MS-TDS gebaut und als Handvektor gekennzeichnet, oder die Aussage entfällt (AGENTS.md §3.8).
- **Deterministisch:** Der Eingang ist das Drehbuch des `FakeHandshakeChannel`; `fixedTimes()` fixiert die Uhr; kein `yield()`, kein `delay`, kein Thread, kein Socket.
- **Mutationen (je im Arbeitsbaum, rot sehen, zurücknehmen; den Ort in `RequestLoop` und den Dispatch-Klassen sucht der Implementer):** M1 der Zustand kehrt nach einem Batch nicht nach `READY` zurück; M2 dasselbe nach `sp_executesql`; M3 dasselbe nach `sp_prepexec`/`sp_execute`; M4 dasselbe nach einem definierten Fehler (Zustand bleibt `RUNNING`); M5 `handles.clear()` nach einem Batch; M6 `handles.clear()` nach `sp_executesql`; M7 `handles.clear()` im Fehlerpfad (unbekannter RPC, unbekanntes Handle); M8 ein definierter Fehler schließt die Verbindung. Zu jeder Mutation steht in der Closure-Notiz der erste rote Schritt der Folge. Überlebt eine, fehlt ein Schritt der Folge, und der Plan ist zu ergänzen, nicht die Mutation zu streichen.

## 4. Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Trigger je Lifecycle-Übergang und WIP-Limit.

**Start** (`next` → `in-progress`): `make gates` ist auf `main` grün, und die Slices `slice-sql-batch-dispatch`, `slice-rpc-executesql-parameter`, `slice-antwort-mehrteilig-output` und `slice-rpc-prepared-handles` liegen in `done/` (erfüllt auf `main` 3c35058: alle vier liegen dort).

**Rückführungen — vorab benennen, nicht erst im Nachhinein begründen:**

- `in-progress` → `next` (zu groß, zurück zur Zerlegung): Die Folge verlangt Änderungen am Produktivcode oder am geteilten Test-Support (`DispatchSupport`, `WireSession`), um beobachtbar zu werden; die Änderung gehört dann in einen eigenen Slice davor.
- `in-progress` → `open` (blockiert — Carveout?): Eine Mutation bleibt rot nicht, weil der Dispatch den Zustand nicht trägt (Befund am Produktivcode); der Befund wird Beobachtung oder Slice, bevor dieser Slice schließt.

## 5. Closure-Trigger


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Closure- und Lerneintrag-Regeln — zwei beobachtbare Kriterien **und** ein
Lerneintrag; ohne ihn ist der Slice nur abgelegt.

Zwei beobachtbare Kriterien: (1) DoD vollständig, der Test steht auf `main`, `make gates` ist grün (einschließlich `make test`); (2) die Closure-Notiz führt die Aussagen des Tests in zwei Spalten, *gemessen* (Test, der besteht) und *Festlegung*, und die Mutationen M1 bis M8 mit ihrem ersten roten Schritt. Lerneintrag: geschärfte Regel, neuer Sensor oder benannte Spec-Lücke (Normalfall hier: Beobachtungs-Register fortgeschrieben, §7).

## 6. Risiken und offene Punkte


Regeln dieser Sektion: Baseline-Regelwerk `modul-05-planning-harness.md`
§Offene Risiken werden bei Closure aufgelöst — **jedes** Risiko bekommt genau
**einen** Ausgang, und kein Slice geht nach `done/`, während eines ohne Ausgang
dasteht.

Die Ausgänge stehen im Planungsstand; der Implementer setzt sie bei der Closure endgültig.

- Der Test belegt nur den Fake: Das Backend liefert, was das Drehbuch sagt, also bestätigt jede Antwort das Skript. — **Ausgang:** weiter offen → `BEO-TDS/test-belegt-nur-den-fake` (verkörpert im Reviewer-Skill). Der Wächter ist die Mutation am Produktivcode (M1 bis M8), nicht am Fake; der Test sagt „Dispatch gegen ein Test-Backend“, nicht „Backend-Verhalten“. Wird eine Mutation nur durch eine Änderung am Drehbuch rot, ist der Befund eine weitere `evidence/`-Datei.
- Ein Zähler prüft nur eine Obergrenze (`backend.requests`, Anzahl der Antworten). — **Ausgang:** weiter offen → `BEO-TDS/schranken-test-mit-ungeprueftem-zaehler` (3 Evidence-Dateien, offen). Gegenmaßnahme im Plan: Gleichheit statt Obergrenze. Nach dem Plan sind es 9 Backend-Aufrufe (Schritte 1, 2, 3, 4, 5 beide, 8, 9, 10) und 11 Antworten (10 Schritte, Schritt 5 hat zwei Nachrichten); der Implementer prüft die Zahlen am Bau und schreibt sie fest, dazu `channel.exhausted`.
- Eine Mutation hängt an einer Reihenfolge, die der Test nicht steuert. — **Ausgang:** weiter offen → `BEO-TDS/mutation-haengt-an-ungesteuerter-reihenfolge` (2 Evidence-Dateien, offen). Die Folge läuft sequenziell über ein Drehbuch ohne `yield()`, `delay` und Wanduhr; jede Mutation hängt an der Anfrageart, nicht an einer Nummer; `ScriptedBackend.inSequence` indiziert nach Aufrufzahl, die im sequenziellen Lauf eindeutig ist.
- Lesart: Die Welle sagt `IDLE`, der Code kennt `READY` (SPEC-001: `READY` ist der Anfragezustand `IDLE`; `COMPLETED` ist nicht abgebildet), und „Rohbytes“ meint hier Nachrichten, nicht Pakete oder Socket. — **Ausgang:** weiter offen → Rückfrage an den Nutzer bei der Priorisierung; kein Register-Eintrag, weil die Lesart den Wortlaut des Closure-Triggers nicht ändert, sondern nur in KDoc und §1 benannt ist.
- Der Test findet eine echte Abweichung (z. B. die Handle-Tabelle wird bei einer Anfrageart geleert). — **Ausgang:** weiter offen → Befund als Beobachtung oder Folge-Slice; die Welle schließt erst, wenn der Befund behoben oder als Setzung der Welle benannt ist. Dass die Handle-Tabelle über mehrere Anfragearten hält, ist in keinem Vorläufer-Test belegt (Befund der Planung).
- Gate-Zeit: Ein langer Test verlangsamt `make gates`. — **Ausgang:** entfallen, wenn die Messung (Testbericht unter 2 s, `make gates` innerhalb der Streuung) in der Closure-Notiz steht; sonst eingetreten → Test kürzen, nicht das Gate lockern (AGENTS.md §3.6).

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

- **Was hat funktioniert:** <…>
- **Was ging anders als geplant:** <…>
- **Gegenstand:** <übernommen von `slice-<Kennung>` | entfallen: <Grund>>
  *(nur beim Ausgang ohne Arbeit; sonst Zeile löschen)*
- **Steering-Loop-Eintrag:** <Guide oder Sensor> <geschärft/ergänzt>: <was genau>
  — liegt in `<AGENTS.md §X | Makefile:<target> | .harness/skills/…>`.
  Auslöser: `BEO-<KUERZEL>/<slug>` (<slice-kennung-a>, <slice-kennung-b>, <slice-kennung-c> — 3×).
  *(Wurde mit diesem Slice nichts verkörpert — der Normalfall —, entfällt die
  Teil-Zeile `— liegt in …` ersatzlos. Der Eintrag ist dann gezählt, nicht
  verkörpert.)*
- **Beobachtungs-Register (`../observations/`):** <`BEO-<KUERZEL>/<slug>/` neu angelegt, Beleg `evidence/slice-<Kennung>.md` | `evidence/slice-<Kennung>.md` in `BEO-<KUERZEL>/<slug>/` ergaenzt — Zaehler steht damit bei <N>x | keine Beobachtung angefallen>
- **Folge-Slices:** <slice-<Kennung> (<Titel>) — ist eine Datei in `open/`>
- **Risiken aus §6:** <jedes mit genau einem Ausgang — siehe §6>
- **Drei Paarungen:** <nur im Repo ohne Wellen-Betrieb — Anker · Folge-Slice · Register, Ergebnis>

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

**Vorgelagert — Sub-Area-Wahl prüfen:** Die Modus-Deklaration in `harness/conventions.md` kennt nur `*` (Greenfield, Kürzel `TDS`). Berührt ist `src/test` (Dispatch-Tests); keine eigene Strukturregel, keine eigene Inventur-Linie: unter der Schwelle von zwei Achsen. Keine feinere Sub-Area; `*` bleibt.

**Vorgelagert — offene Beobachtungen sichten:** Register `../observations/BEO-TDS/` durchgegangen (Stand `main` 3c35058, Sub-Area bei allen `*`). Treffer mit Zähler-Stand (Zahl der Dateien unter `evidence/`):

- `test-belegt-nur-den-fake` — 5×, verkörpert (Reviewer-Skill, Anker `seit welle-backend-streaming`); Risiko in §6.
- `schranken-test-mit-ungeprueftem-zaehler` — 3×, offen, nicht verkörpert (Entscheidung beim Nutzer); Risiko in §6.
- `mutation-haengt-an-ungesteuerter-reihenfolge` — 2×, offen; Risiko in §6. Ein weiterer Beleg stellt ihn auf 3× und braucht dann eine Entscheidung im Lese-Schritt der Welle-Closure.

Weitere Beobachtungen berühren den Gegenstand nicht.

**Modus-Begründungsblock — Umfang.** Alle berührten Sub-Areas GF.
